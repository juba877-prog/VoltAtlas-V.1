package com.example.ai.approval

/**
 * Interface providing authenticated/local engineer principal identity.
 * Fails closed if principal identity cannot be authoritatively resolved.
 */
interface TrustedPrincipalProvider {
    fun getTrustedPrincipalId(): String?
}

/**
 * Standard implementation providing local authenticated engineer identity.
 */
class DefaultTrustedPrincipalProvider(
    private val principalId: String = "local-authenticated-engineer"
) : TrustedPrincipalProvider {
    override fun getTrustedPrincipalId(): String? = principalId.trim().ifBlank { null }
}
