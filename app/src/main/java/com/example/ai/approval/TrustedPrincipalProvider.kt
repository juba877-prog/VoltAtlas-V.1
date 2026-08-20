package com.example.ai.approval

/**
 * Interface providing authenticated engineer principal identity for trusted approval issuance.
 * Fails closed if principal identity cannot be authoritatively resolved.
 */
interface TrustedPrincipalProvider {
    fun getTrustedPrincipalId(): String?
}

/**
 * Authoritative principal provider that delegates to an injected or registered trusted authority.
 * In the absence of an explicitly configured authority, returns null to fail closed.
 *
 * Invariants:
 * - Never returns hardcoded strings in production default.
 * - Never falls back to chat text, user-entered text, or LLM output.
 * - Never provides an anonymous fallback.
 */
class DefaultTrustedPrincipalProvider(
    private val principalSupplier: () -> String? = { null }
) : TrustedPrincipalProvider {
    override fun getTrustedPrincipalId(): String? = principalSupplier()?.trim()?.ifBlank { null }
}
