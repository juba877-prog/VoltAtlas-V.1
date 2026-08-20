package com.example.ai.hybrid

/**
 * Interface determining network reachability prior to initiating cloud LLM calls.
 */
interface NetworkConnectivityProbe {
    fun isLikelyOnline(): Boolean
}

/**
 * Default implementation of [NetworkConnectivityProbe] delegating to a supplier function.
 */
class DefaultNetworkConnectivityProbe(
    private val isOnlineSupplier: () -> Boolean = { true }
) : NetworkConnectivityProbe {
    override fun isLikelyOnline(): Boolean = isOnlineSupplier()
}
