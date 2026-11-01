package org.organiza.enforcement

import java.nio.ByteBuffer
import org.organiza.domain.DomainName
import org.organiza.domain.Parsed
import org.organiza.domain.port.DnsUpstream

/**
 * Decides the fate of each raw IP packet read from the VPN interface.
 *
 * Blocked names get NXDOMAIN locally. Allowed names go to [upstream] (ADR-0005). TCP to
 * port 53 is reset, so a client cannot use it to reach a blocked name. Anything else,
 * including malformed input, is dropped by returning null.
 */
public class DnsSinkhole(
    private val upstream: DnsUpstream,
    private val blockList: () -> BlockList,
) {
    /** Returns the packet to write back to the interface, or null to send nothing. */
    public fun handle(packet: ByteArray): ByteArray? {
        val ip = IpHeader.parse(packet) ?: return null
        return when (ip.protocol) {
            PROTOCOL_UDP -> handleUdp(packet, ip)
            PROTOCOL_TCP -> handleTcp(packet, ip)
            else -> null
        }
    }

    private fun handleUdp(packet: ByteArray, ip: IpHeader): ByteArray? {
        if (ip.end - ip.payload < UDP_HEADER) return null
        if (u16(packet, ip.payload + 2) != DNS_PORT) return null
        val length = u16(packet, ip.payload + 4)
        if (length < UDP_HEADER || ip.payload + length > ip.end) return null
        val query = packet.copyOfRange(ip.payload + UDP_HEADER, ip.payload + length)
        val question = DnsQuestion.parse(query) ?: return null
        val blocked = question.name?.let(blockList()::blocks) == true
        val answer = if (blocked) {
            question.reply(query, RCODE_NXDOMAIN)
        } else {
            upstream.forward(query) ?: question.reply(query, RCODE_SERVFAIL)
        }
        val udp = ByteBuffer.allocate(UDP_HEADER + answer.size).apply {
            putShort(DNS_PORT.toShort())
            putShort(u16(packet, ip.payload).toShort())
            putShort((UDP_HEADER + answer.size).toShort())
            putShort(0)
            put(answer)
        }.array()
        return ip.reply(packet, udp, checksumOffset = 6)
    }

    private fun handleTcp(packet: ByteArray, ip: IpHeader): ByteArray? {
        if (ip.end - ip.payload < TCP_HEADER) return null
        if (u16(packet, ip.payload + 2) != DNS_PORT) return null
        val flags = packet[ip.payload + 13].toInt()
        if (flags and TCP_RST != 0) return null
        val headerLength = (packet[ip.payload + 12].toInt() and 0xf0) shr 2
        if (headerLength < TCP_HEADER || ip.payload + headerLength > ip.end) return null
        val data = ip.end - ip.payload - headerLength
        val consumed = data + (if (flags and TCP_SYN != 0) 1 else 0) + (if (flags and TCP_FIN != 0) 1 else 0)
        val incoming = ByteBuffer.wrap(packet, ip.payload + 4, 8)
        val seq = incoming.int
        val ack = incoming.int
        val tcp = ByteBuffer.allocate(TCP_HEADER).apply {
            putShort(DNS_PORT.toShort())
            putShort(u16(packet, ip.payload).toShort())
            putInt(if (flags and TCP_ACK != 0) ack else 0)
            putInt(seq + consumed)
            put((TCP_HEADER shl 2).toByte())
            put((TCP_RST or TCP_ACK).toByte())
        }.array()
        return ip.reply(packet, tcp, checksumOffset = 16)
    }

    private class IpHeader(
        val version: Int,
        val protocol: Int,
        val payload: Int,
        val end: Int,
    ) {
        /** Builds a reply to [request] carrying [body], with addresses swapped and checksummed. */
        fun reply(request: ByteArray, body: ByteArray, checksumOffset: Int): ByteArray =
            if (version == 4) replyV4(request, body, checksumOffset) else replyV6(request, body, checksumOffset)

        private fun replyV4(request: ByteArray, body: ByteArray, checksumOffset: Int): ByteArray {
            val out = ByteBuffer.allocate(IPV4_HEADER + body.size).apply {
                put(0x45)
                put(0)
                putShort((IPV4_HEADER + body.size).toShort())
                putInt(0)
                put(REPLY_TTL)
                put(protocol.toByte())
                putShort(0)
                put(request, 16, 4)
                put(request, 12, 4)
                put(body)
            }.array()
            writeU16(out, 10, checksum(out, 0, IPV4_HEADER, 0L))
            val pseudo = sum(out, 12, 8, 0L) + protocol + body.size
            writeTransportChecksum(out, IPV4_HEADER, checksumOffset, pseudo)
            return out
        }

        private fun replyV6(request: ByteArray, body: ByteArray, checksumOffset: Int): ByteArray {
            val out = ByteBuffer.allocate(IPV6_HEADER + body.size).apply {
                putInt(0x60000000)
                putShort(body.size.toShort())
                put(protocol.toByte())
                put(REPLY_TTL)
                put(request, 24, 16)
                put(request, 8, 16)
                put(body)
            }.array()
            val pseudo = sum(out, 8, 32, 0L) + protocol + body.size
            writeTransportChecksum(out, IPV6_HEADER, checksumOffset, pseudo)
            return out
        }

        private fun writeTransportChecksum(out: ByteArray, start: Int, offset: Int, pseudo: Long) {
            val value = checksum(out, start, out.size - start, pseudo)
            // In UDP a zero checksum means "none", so a computed zero is sent as all ones.
            writeU16(out, start + offset, if (value == 0 && protocol == PROTOCOL_UDP) 0xffff else value)
        }

        companion object {
            fun parse(packet: ByteArray): IpHeader? {
                if (packet.isEmpty()) return null
                return when ((packet[0].toInt() and 0xf0) shr 4) {
                    4 -> parseV4(packet)
                    6 -> parseV6(packet)
                    else -> null
                }
            }

            private fun parseV4(packet: ByteArray): IpHeader? {
                if (packet.size < IPV4_HEADER) return null
                val headerLength = (packet[0].toInt() and 0x0f) * 4
                val total = u16(packet, 2)
                if (headerLength < IPV4_HEADER || total < headerLength || total > packet.size) return null
                val fragmented = u16(packet, 6) and 0x3fff != 0
                if (fragmented) return null
                return IpHeader(4, packet[9].toInt() and 0xff, headerLength, total)
            }

            private fun parseV6(packet: ByteArray): IpHeader? {
                if (packet.size < IPV6_HEADER) return null
                val end = IPV6_HEADER + u16(packet, 4)
                if (end > packet.size) return null
                return IpHeader(6, packet[6].toInt() and 0xff, IPV6_HEADER, end)
            }
        }
    }

    private class DnsQuestion(val name: DomainName?, val end: Int) {
        fun reply(query: ByteArray, rcode: Int): ByteArray =
            ByteBuffer.allocate(end).apply {
                put(query, 0, 2)
                put((query[2].toInt() and FLAG_RD or FLAG_QR).toByte())
                put((FLAG_RA or rcode).toByte())
                putShort(1)
                putShort(0)
                putShort(0)
                putShort(0)
                put(query, DNS_HEADER, end - DNS_HEADER)
            }.array()

        companion object {
            /** Returns null unless [query] is a standard query with a readable first question. */
            fun parse(query: ByteArray): DnsQuestion? {
                if (query.size < DNS_HEADER) return null
                if (query[2].toInt() and FLAG_QR != 0) return null
                if (u16(query, 4) == 0) return null
                val labels = mutableListOf<String>()
                var at = DNS_HEADER
                while (true) {
                    if (at >= query.size) return null
                    val length = query[at].toInt() and 0xff
                    at++
                    if (length == 0) break
                    if (length and 0xc0 != 0 || at + length > query.size) return null
                    labels += String(query, at, length, Charsets.ISO_8859_1)
                    at += length
                }
                if (at + 4 > query.size) return null
                val name = DomainName.parse(labels.joinToString("."))
                return DnsQuestion((name as? Parsed.Valid)?.value, at + 4)
            }
        }
    }

    private companion object {
        const val PROTOCOL_TCP = 6
        const val PROTOCOL_UDP = 17
        const val DNS_PORT = 53
        const val IPV4_HEADER = 20
        const val IPV6_HEADER = 40
        const val UDP_HEADER = 8
        const val TCP_HEADER = 20
        const val DNS_HEADER = 12
        const val REPLY_TTL: Byte = 64
        const val TCP_FIN = 0x01
        const val TCP_SYN = 0x02
        const val TCP_RST = 0x04
        const val TCP_ACK = 0x10
        const val FLAG_QR = 0x80
        const val FLAG_RD = 0x01
        const val FLAG_RA = 0x80
        const val RCODE_SERVFAIL = 2
        const val RCODE_NXDOMAIN = 3

        fun u16(bytes: ByteArray, at: Int): Int =
            ((bytes[at].toInt() and 0xff) shl 8) or (bytes[at + 1].toInt() and 0xff)

        fun writeU16(bytes: ByteArray, at: Int, value: Int) {
            bytes[at] = (value shr 8).toByte()
            bytes[at + 1] = value.toByte()
        }

        fun sum(bytes: ByteArray, start: Int, length: Int, initial: Long): Long {
            var total = initial
            var at = start
            val end = start + length
            while (at + 1 < end) {
                total += u16(bytes, at)
                at += 2
            }
            if (at < end) total += (bytes[at].toInt() and 0xff) shl 8
            return total
        }

        fun checksum(bytes: ByteArray, start: Int, length: Int, initial: Long): Int {
            var total = sum(bytes, start, length, initial)
            while (total shr 16 != 0L) total = (total and 0xffff) + (total shr 16)
            return total.toInt().inv() and 0xffff
        }
    }
}
