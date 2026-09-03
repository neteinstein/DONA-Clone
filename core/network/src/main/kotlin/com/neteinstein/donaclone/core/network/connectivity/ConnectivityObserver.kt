package com.neteinstein.donaclone.core.network.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Push-based (not polled) "does this device have any usable network at all" signal, backed by
 * [ConnectivityManager.registerDefaultNetworkCallback]. This is deliberately generic OS-level
 * connectivity, not "can we reach the DPU" — the hub may be LAN-only with no internet route at
 * all, so DPU reachability is tracked separately (see `AuthRepository.sessionState`).
 */
class ConnectivityObserver(
    context: Context,
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val _isOnline = MutableStateFlow(hasActiveInternetCapableNetwork())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        val request =
            NetworkRequest
                .Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
        connectivityManager?.registerNetworkCallback(
            request,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isOnline.value = true
                }

                override fun onLost(network: Network) {
                    _isOnline.value = hasActiveInternetCapableNetwork()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    _isOnline.value = hasActiveInternetCapableNetwork()
                }
            },
        )
    }

    private fun hasActiveInternetCapableNetwork(): Boolean {
        val network = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
