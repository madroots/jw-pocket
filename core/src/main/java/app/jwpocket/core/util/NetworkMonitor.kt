package app.jwpocket.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the device is online, so screens can point to downloads when it isn't. */
class NetworkMonitor(context: Context) {

    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val _online = MutableStateFlow(isOnlineNow())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        runCatching {
            val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { _online.value = true }
                override fun onLost(network: Network) { _online.value = isOnlineNow() }
                override fun onUnavailable() { _online.value = false }
            })
        }
    }

    /** True on mobile data, hotspots and other connections the person pays by use for. */
    fun isMetered(): Boolean = runCatching { cm.isActiveNetworkMetered }.getOrDefault(true)

    private fun isOnlineNow(): Boolean = runCatching {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)
}
