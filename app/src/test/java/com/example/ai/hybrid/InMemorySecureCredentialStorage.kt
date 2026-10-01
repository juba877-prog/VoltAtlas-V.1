package com.example.ai.hybrid

/**
 * In-memory test fake implementation of [SecureCredentialStorage] for unit tests.
 * Compile-time separated from production sources.
 */
class InMemorySecureCredentialStorage(
    initialCredential: String? = null
) : SecureCredentialStorage {
    private var storedCredential: String? = initialCredential?.trim()?.takeIf { it.isNotEmpty() }

    override fun saveCredential(credential: String) {
        storedCredential = credential.trim().takeIf { it.isNotEmpty() }
    }

    override fun getCredential(): String? = storedCredential

    override fun clearCredential() {
        storedCredential = null
    }

    /** Phase 6 Batch 4 / G-5: mirrors production — only [CredentialReadResult.Available] counts. */
    override fun hasCredential(): Boolean = readCredential() is CredentialReadResult.Available

    override fun getMaskedCredential(): String = storedCredential?.let { AndroidKeystoreSecureCredentialStorage.mask(it) } ?: ""

    /**
     * Phase 6 Batch 4 / G-5: mirrors production semantics — a stored-but-unreadable credential
     * is NOT "configured". [unavailableReason] lets a test simulate KEY_DECRYPT_FAILED /
     * KEY_READ_FAILED / KEY_INVALID while a value is technically present.
     */
    var unavailableReason: KeyReadFailure? = null

    override fun readCredential(): CredentialReadResult {
        unavailableReason?.let { return CredentialReadResult.Unavailable(it) }
        val value = storedCredential
        return if (value.isNullOrBlank()) {
            CredentialReadResult.Unavailable(KeyReadFailure.KEY_NOT_CONFIGURED)
        } else {
            CredentialReadResult.Available(value)
        }
    }
}
