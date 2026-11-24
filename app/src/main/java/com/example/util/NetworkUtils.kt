package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

object NetworkUtils {

    /**
     * Checks whether any usable network (Cellular, Wi-Fi, Ethernet, VPN) with internet capability
     * is currently available. Scans [ConnectivityManager.allNetworks] as a fallback when
     * [ConnectivityManager.getActiveNetwork] briefly returns null during Wi-Fi <-> Mobile Data or
     * Dual-SIM cellular handoffs.
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        fun NetworkCapabilities.hasUsableInternet(): Boolean {
            if (!hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
            ) {
                return false
            }
            return hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)
        }

        val activeCaps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        if (activeCaps != null && activeCaps.hasUsableInternet()) {
            return true
        }

        @Suppress("DEPRECATION")
        return cm.allNetworks.any { network ->
            val caps = cm.getNetworkCapabilities(network)
            caps != null && caps.hasUsableInternet()
        }
    }

    /**
     * Returns true when the device's current primary transport is mobile cellular data
     * (and not Wi-Fi/Ethernet), allowing callers to pace large multi-image uploads cleanly.
     */
    fun isCellularConnection(context: Context): Boolean {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeCaps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        if (activeCaps != null) {
            return activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                !activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
        return false
    }

    /**
     * Prefers IPv4 addresses before IPv6 while retaining IPv6 as fallback.
     * Prevents 20–30s connect stalls on mobile carriers with broken or unroutable IPv6 setups.
     */
    private val ipv4FirstDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.size <= 1) return addresses
            val v4 = ArrayList<InetAddress>(addresses.size)
            val v6 = ArrayList<InetAddress>(addresses.size)
            for (addr in addresses) {
                if (addr is Inet4Address) v4.add(addr) else v6.add(addr)
            }
            return if (v4.isEmpty()) addresses else v4 + v6
        }
    }

    fun createMobileResilientOkHttpClient(
        connectTimeoutSec: Long = 35L,
        readTimeoutSec: Long = 120L,
        writeTimeoutSec: Long = 90L,
        callTimeoutSec: Long = 180L,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .dns(ipv4FirstDns)
            .retryOnConnectionFailure(true)
            // Shorter keep-alive & HTTP/2 ping prevents dead sockets after carrier NAT expiry or cell handoff
            .connectionPool(ConnectionPool(4, 25, TimeUnit.SECONDS))
            .pingInterval(20, TimeUnit.SECONDS)
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .writeTimeout(writeTimeoutSec, TimeUnit.SECONDS)
            .callTimeout(callTimeoutSec, TimeUnit.SECONDS)
            .build()
    }

    fun isTransientNetworkFailure(e: Throwable): Boolean {
        if (e is CancellationException && e !is TimeoutCancellationException) return false
        var cur: Throwable? = e
        var depth = 0
        while (cur != null && depth < 8) {
            if (cur is TimeoutCancellationException ||
                cur is SocketTimeoutException ||
                cur is ConnectException ||
                cur is UnknownHostException ||
                cur is SocketException ||
                cur is EOFException ||
                cur is SSLException ||
                cur is InterruptedIOException ||
                cur is IOException
            ) {
                return true
            }
            val msg = cur.message.orEmpty()
            if (msg.contains("timeout", ignoreCase = true) ||
                msg.contains("timed out", ignoreCase = true) ||
                msg.contains("connection reset", ignoreCase = true) ||
                msg.contains("unexpected end of stream", ignoreCase = true) ||
                msg.contains("broken pipe", ignoreCase = true) ||
                msg.contains("failed to connect", ignoreCase = true) ||
                msg.contains("unable to resolve host", ignoreCase = true)
            ) {
                return true
            }
            cur = cur.cause
            depth++
        }
        return false
    }

    suspend fun <T> withMobileDataRetry(
        maxAttempts: Int = 2,
        initialDelayMs: Long = 650L,
        block: suspend () -> T,
    ): T {
        var lastError: Throwable? = null
        for (attempt in 1..maxAttempts) {
            try {
                return block()
            } catch (e: Throwable) {
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                lastError = e
                if (attempt < maxAttempts && isTransientNetworkFailure(e)) {
                    delay(initialDelayMs * attempt)
                } else {
                    throw e
                }
            }
        }
        throw lastError ?: IllegalStateException("Network request failed")
    }
}
