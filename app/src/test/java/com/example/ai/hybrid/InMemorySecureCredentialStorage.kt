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

    override fun hasCredential(): Boolean = !storedCredential.isNullOrBlank()

    override fun getMaskedCredential(): String = storedCredential?.let { AndroidKeystoreSecureCredentialStorage.mask(it) } ?: ""
}
