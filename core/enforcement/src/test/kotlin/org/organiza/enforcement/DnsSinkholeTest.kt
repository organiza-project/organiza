package org.organiza.enforcement

import java.nio.ByteBuffer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.organiza.testing.FakeDnsUpstream

class DnsSinkholeTest {
    private val upstream = FakeDnsUpstream()
    private var list = BlockList(emptySet(), setOf(domain("reddit.com")))
    private val sinkhole = DnsSinkhole(upstream) { list }

    private val v4Src = byteArrayOf(10, 111, 0, 1)
    private val v4Dst = byteArrayOf(10, 111, 0, 2)
    private val v6Src = ByteArray(16) { 0xfd.toByte() }
    private val v6Dst = ByteArray(16) { 0xfe.toByte() }

    private fun dnsQuery(name: String, id: Int = 0x1234): ByteArray {
        val labels = name.split('.').flatMap { listOf(it.length.toByte()) + it.toByteArray().toList() }
        return ByteBuffer.allocate(12 + labels.size + 1 + 4).apply {
            putShort(id.toShort()); putShort(0x0100); putShort(1); putShort(0); putShort(0); putShort(0)
            put(labels.toByteArray()); put(0); putShort(1); putShort(1)
        }.array()
    }

    private fun udpV4(dns: ByteArray, dstPort: Int = 53): ByteArray =
        ByteBuffer.allocate(20 + 8 + dns.size).apply {
            put(0x45); put(0); putShort((20 + 8 + dns.size).toShort()); putInt(0); put(64); put(17); putShort(0)
            put(v4Src); put(v4Dst)
            putShort(40000.toShort()); putShort(dstPort.toShort()); putShort((8 + dns.size).toShort()); putShort(0)
            put(dns)
        }.array()

    private fun udpV6(dns: ByteArray): ByteArray =
        ByteBuffer.allocate(40 + 8 + dns.size).apply {
            putInt(0x60000000); putShort((8 + dns.size).toShort()); put(17); put(64)
            put(v6Src); put(v6Dst)
            putShort(40000.toShort()); putShort(53); putShort((8 + dns.size).toShort()); putShort(0)
            put(dns)
        }.array()

    private fun tcpV4(dstPort: Int = 53): ByteArray =
        ByteBuffer.allocate(40).apply {
            put(0x45); put(0); putShort(40); putInt(0); put(64); put(6); putShort(0)
            put(v4Src); put(v4Dst)
            putShort(40000.toShort()); putShort(dstPort.toShort()); putInt(1000); putInt(0)
            put(0x50); put(0x02); putShort(1024); putShort(0); putShort(0)
        }.array()

    private fun u16(packet: ByteArray, at: Int) =
        ((packet[at].toInt() and 0xff) shl 8) or (packet[at + 1].toInt() and 0xff)

    private fun checksum(bytes: ByteArray): Int {
        var sum = 0L
        var i = 0
        while (i + 1 < bytes.size) { sum += u16(bytes, i); i += 2 }
        if (i < bytes.size) sum += (bytes[i].toInt() and 0xff) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xffff) + (sum shr 16)
        return sum.toInt()
    }

    private fun assertValidV4Udp(reply: ByteArray) {
        assertEquals(0xffff, checksum(reply.copyOfRange(0, 20)), "ip checksum")
        val udp = reply.copyOfRange(20, reply.size)
        val pseudo = ByteBuffer.allocate(12 + udp.size).apply {
            put(reply, 12, 8); put(0); put(17); putShort(udp.size.toShort()); put(udp)
        }.array()
        assertEquals(0xffff, checksum(pseudo), "udp checksum")
    }

    @Test
    fun `blocked name gets NXDOMAIN without touching upstream`() {
        val reply = assertNotNull(sinkhole.handle(udpV4(dnsQuery("old.reddit.com"))))
        val dns = reply.copyOfRange(28, reply.size)
        assertEquals(0x1234, u16(dns, 0))
        assertEquals(0x80, dns[2].toInt() and 0x80, "QR set")
        assertEquals(3, dns[3].toInt() and 0x0f, "NXDOMAIN")
        assertEquals(0, upstream.forwarded.size)
    }

    @Test
    fun `reply reverses addresses and ports with valid checksums`() {
        val reply = assertNotNull(sinkhole.handle(udpV4(dnsQuery("reddit.com"))))
        assertContentEquals(v4Dst, reply.copyOfRange(12, 16))
        assertContentEquals(v4Src, reply.copyOfRange(16, 20))
        assertEquals(53, u16(reply, 20))
        assertEquals(40000, u16(reply, 22))
        assertEquals(reply.size, u16(reply, 2))
        assertValidV4Udp(reply)
    }

    @Test
    fun `allowed names are forwarded and the answer is wrapped`() {
        val query = dnsQuery("example.com")
        upstream.response = byteArrayOf(0x12, 0x34, 0x81.toByte(), 0x80.toByte(), 9, 9, 9, 9, 9, 9, 9, 9, 7)
        val reply = assertNotNull(sinkhole.handle(udpV4(query)))
        assertContentEquals(query, upstream.forwarded.single())
        assertContentEquals(upstream.response, reply.copyOfRange(28, reply.size))
        assertValidV4Udp(reply)
    }

    @Test
    fun `an unreachable upstream gives SERVFAIL rather than silence`() {
        upstream.response = null
        val reply = assertNotNull(sinkhole.handle(udpV4(dnsQuery("example.com"))))
        assertEquals(2, reply[28 + 3].toInt() and 0x0f)
    }

    @Test
    fun `ipv6 queries are answered with a valid udp checksum`() {
        val reply = assertNotNull(sinkhole.handle(udpV6(dnsQuery("reddit.com"))))
        assertEquals(0x60, reply[0].toInt() and 0xf0)
        assertContentEquals(v6Dst, reply.copyOfRange(8, 24))
        assertEquals(3, reply[48 + 3].toInt() and 0x0f)
        val udp = reply.copyOfRange(40, reply.size)
        val pseudo = ByteBuffer.allocate(40 + udp.size).apply {
            put(reply, 8, 32); putInt(udp.size); put(0); put(0); put(0); put(17); put(udp)
        }.array()
        assertEquals(0xffff, checksum(pseudo))
    }

    @Test
    fun `block list changes apply to the next query`() {
        list = BlockList.EMPTY
        upstream.response = dnsQuery("reddit.com")
        sinkhole.handle(udpV4(dnsQuery("reddit.com")))
        assertEquals(1, upstream.forwarded.size)
    }

    @Test
    fun `tcp to port 53 is reset so it cannot carry blocked lookups`() {
        val reply = assertNotNull(sinkhole.handle(tcpV4()))
        assertEquals(6, reply[9].toInt())
        assertEquals(0x14, reply[33].toInt() and 0xff, "RST+ACK")
        assertEquals(1001L, ByteBuffer.wrap(reply, 28, 4).int.toLong(), "ack covers the SYN")
        assertEquals(0xffff, checksum(reply.copyOfRange(0, 20)))
        val tcp = reply.copyOfRange(20, reply.size)
        val pseudo = ByteBuffer.allocate(12 + tcp.size).apply {
            put(reply, 12, 8); put(0); put(6); putShort(tcp.size.toShort()); put(tcp)
        }.array()
        assertEquals(0xffff, checksum(pseudo))
    }

    @Test
    fun `an incoming tcp reset is never answered`() {
        val rst = tcpV4().also { it[33] = 0x04 }
        assertNull(sinkhole.handle(rst))
    }

    @Test
    fun `other traffic is dropped`() {
        assertNull(sinkhole.handle(udpV4(dnsQuery("example.com"), dstPort = 443)))
        assertNull(sinkhole.handle(tcpV4(dstPort = 443)))
    }

    @Test
    fun `dns responses and compressed questions are dropped`() {
        val response = dnsQuery("reddit.com").also { it[2] = 0x81.toByte() }
        assertNull(sinkhole.handle(udpV4(response)))
        val pointer = dnsQuery("reddit.com").also { it[12] = 0xc0.toByte() }
        assertNull(sinkhole.handle(udpV4(pointer)))
    }

    @Test
    fun `names that do not parse as domains are forwarded not blocked`() {
        upstream.response = dnsQuery("localhost")
        assertNotNull(sinkhole.handle(udpV4(dnsQuery("localhost"))))
        assertEquals(1, upstream.forwarded.size)
    }

    @Test
    fun `malformed packets never throw`() {
        val random = Random(42)
        repeat(20_000) {
            sinkhole.handle(random.nextBytes(random.nextInt(0, 120)))
        }
        val valid = udpV4(dnsQuery("reddit.com"))
        repeat(20_000) {
            val mutated = valid.copyOf(random.nextInt(0, valid.size + 1))
            if (mutated.isNotEmpty()) mutated[random.nextInt(mutated.size)] = random.nextInt().toByte()
            sinkhole.handle(mutated)
        }
    }
}
