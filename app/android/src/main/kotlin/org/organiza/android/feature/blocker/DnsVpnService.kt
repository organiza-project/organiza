package org.organiza.android.feature.blocker

import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.organiza.android.OrganizaApp
import org.organiza.android.R
import org.organiza.domain.port.DnsUpstream
import org.organiza.enforcement.DnsSinkhole

/** Routes only the virtual DNS address through the app, so other traffic is untouched (ADR-0007). */
class DnsVpnService : VpnService(), DnsUpstream {
    private val app get() = OrganizaApp.from(this)
    private var tunnel: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private var workers: ExecutorService? = null

    @Volatile private var resolvers: List<InetAddress> = emptyList()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            resolvers = linkProperties.dnsServers
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutDown()
            stopSelf()
            return START_NOT_STICKY
        }
        if (tunnel == null) start()
        return START_STICKY
    }

    private fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        getSystemService(ConnectivityManager::class.java).registerNetworkCallback(request, networkCallback)
        val established = Builder()
            .setSession(getString(R.string.app_name))
            .setBlocking(true)
            .addAddress(TUNNEL_V4, 32)
            .addAddress(TUNNEL_V6, 128)
            .addDnsServer(DNS_V4)
            .addDnsServer(DNS_V6)
            .addRoute(DNS_V4, 32)
            .addRoute(DNS_V6, 128)
            .establish()
        if (established == null) {
            shutDown()
            stopSelf()
            return
        }
        tunnel = established
        app.vpnRunning = true
        val pool = Executors.newFixedThreadPool(WORKERS)
        workers = pool
        // A site pass lifts its entry here too, so an override reaches the page (ADR-0016).
        val sinkhole = DnsSinkhole(this) { app.passes.apply(app.session.current.blockList) }
        val output = FileOutputStream(established.fileDescriptor)
        reader = Thread({ readLoop(FileInputStream(established.fileDescriptor), output, sinkhole, pool) }, "dns-vpn")
            .apply { start() }
    }

    private fun readLoop(
        input: FileInputStream,
        output: FileOutputStream,
        sinkhole: DnsSinkhole,
        pool: ExecutorService,
    ) {
        val buffer = ByteArray(MAX_PACKET)
        try {
            while (!Thread.currentThread().isInterrupted) {
                val length = input.read(buffer)
                if (length <= 0) continue
                val packet = buffer.copyOf(length)
                pool.execute {
                    val reply = sinkhole.handle(packet) ?: return@execute
                    try {
                        synchronized(output) { output.write(reply) }
                    } catch (_: IOException) {
                        // The tunnel closed while this reply was in flight, so there is no one to tell.
                    }
                }
            }
        } catch (_: IOException) {
            // Closing the tunnel in shutDown ends the blocking read.
        }
    }

    override fun forward(query: ByteArray): ByteArray? {
        for (resolver in resolvers) {
            try {
                DatagramSocket().use { socket ->
                    protect(socket)
                    socket.soTimeout = UPSTREAM_TIMEOUT_MS
                    socket.send(DatagramPacket(query, query.size, resolver, DNS_PORT))
                    val response = DatagramPacket(ByteArray(MAX_PACKET), MAX_PACKET)
                    socket.receive(response)
                    return response.data.copyOf(response.length)
                }
            } catch (_: IOException) {
                // Try the next resolver. The caller answers SERVFAIL if none respond.
            }
        }
        return null
    }

    override fun onRevoke() {
        shutDown()
        app.checkTamper()
        stopSelf()
    }

    override fun onDestroy() {
        shutDown()
        super.onDestroy()
    }

    private fun shutDown() {
        val wasRunning = tunnel != null
        app.vpnRunning = false
        tunnel?.close()
        tunnel = null
        reader?.interrupt()
        reader = null
        workers?.shutdownNow()
        workers = null
        if (wasRunning) {
            getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback)
        }
    }

    companion object {
        const val ACTION_STOP = "org.organiza.android.STOP_VPN"
        private const val TUNNEL_V4 = "10.111.222.1"
        private const val DNS_V4 = "10.111.222.2"
        private const val TUNNEL_V6 = "fd6f:7267:6100::1"
        private const val DNS_V6 = "fd6f:7267:6100::2"
        private const val DNS_PORT = 53
        private const val MAX_PACKET = 32767
        private const val WORKERS = 4
        private const val UPSTREAM_TIMEOUT_MS = 3000
    }
}
