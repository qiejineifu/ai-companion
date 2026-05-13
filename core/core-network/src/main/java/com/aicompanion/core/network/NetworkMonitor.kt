package com.aicompanion.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

enum class NetworkStatus {
    CONNECTED,      // Online, full features
    METERED,        // Mobile data, may throttle
    DISCONNECTED,   // Offline
    UNSTABLE        // Network available but high latency
}

data class NetworkState(
    val status: NetworkStatus = NetworkStatus.DISCONNECTED,
    val isWifi: Boolean = false,
    val isCellular: Boolean = false,
    val bandwidth: Int = 0  // Estimated kbps, 0 = unknown
)

class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val statusChannel = Channel<NetworkState>(Channel.CONFLATED)
    val networkState: Flow<NetworkState> = statusChannel.receiveAsFlow()

    private var currentState = NetworkState()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateState(network)
        }

        override fun onLost(network: Network) {
            // Check if any other network is still available
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork == null) {
                currentState = NetworkState(status = NetworkStatus.DISCONNECTED)
                statusChannel.trySend(currentState)
            } else {
                updateState(activeNetwork)
            }
        }

        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities
        ) {
            updateState(network)
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)

        // Get initial state
        connectivityManager.activeNetwork?.let { updateState(it) }
    }

    private fun updateState(network: Network) {
        val caps = connectivityManager.getNetworkCapabilities(network)
        if (caps == null) {
            currentState = NetworkState(status = NetworkStatus.DISCONNECTED)
            statusChannel.trySend(currentState)
            return
        }

        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val bandwidth = caps.linkDownstreamBandwidthKbps

        val status = when {
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ->
                NetworkStatus.DISCONNECTED
            isMetered && bandwidth in 1..500 ->
                NetworkStatus.UNSTABLE
            isMetered ->
                NetworkStatus.METERED
            else ->
                NetworkStatus.CONNECTED
        }

        currentState = NetworkState(
            status = status,
            isWifi = isWifi,
            isCellular = isCellular,
            bandwidth = bandwidth
        )
        statusChannel.trySend(currentState)
    }

    fun isOnline(): Boolean = currentState.status != NetworkStatus.DISCONNECTED

    fun shouldUseOfflineMode(): Boolean {
        return when (currentState.status) {
            NetworkStatus.DISCONNECTED -> true
            NetworkStatus.UNSTABLE -> true
            NetworkStatus.METERED -> true  // Prefer offline on mobile data
            NetworkStatus.CONNECTED -> false
        }
    }

    fun destroy() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
}
