package com.example.ghalam.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Listens to connectivity events. "Online" means the system has validated the connection (real
 * internet, not just a modem or a Wi-Fi without internet). [start] reports every moment the
 * situation gets better: internet came back, or the connection became unmetered (Wi-Fi).
 */
class NetworkMonitor(context: Context) {
    private val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _online = MutableStateFlow(currentlyOnline())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private var lastOk = false
    private var lastUnmetered = false
    private var started = false

    fun start(onImproved: () -> Unit) {
        if (started) return
        started = true
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val ok = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    val unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    val improved = ok && (!lastOk || (unmetered && !lastUnmetered))
                    lastOk = ok
                    lastUnmetered = unmetered
                    _online.value = ok
                    if (improved) onImproved()
                }

                override fun onLost(network: Network) {
                    lastOk = false
                    lastUnmetered = false
                    _online.value = currentlyOnline()
                }
            })
        }
    }

    /** Mobile data with a limited plan, or a hotspot. */
    fun isMetered(): Boolean = cm.isActiveNetworkMetered

    private fun currentlyOnline(): Boolean {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
