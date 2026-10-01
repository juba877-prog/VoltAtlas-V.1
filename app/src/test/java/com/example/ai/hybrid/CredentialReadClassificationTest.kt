package com.example.ai.hybrid

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6 Batch 4 / G-5 — keystore failure classification + cancellation safety.
 *
 * Root cause: `getCredential()` ended in `catch (_: Throwable) { null }`. A decrypt failure, an
 * invalidated key, corrupted stored ciphertext and "no key was ever configured" all produced the
 * same `null`, so Settings reported "Not Configured" for a credential that actually existed —
 * and cancellation was silently swallowed.
 *
 * These tests pin the typed distinction and the cancellation guarantee.
 */
class CredentialReadClassificationTest {

    // ---------------- typed failure modes ----------------

    /** G-5 — nothing stored is the ONLY state that may be reported as KEY_NOT_CONFIGURED. */
    @Test
    fun noStoredCredentialIsKeyNotConfigured() {
        val storage = InMemorySecureCredentialStorage(null)
        val result = storage.readCredential()
        assertEquals(
            CredentialReadResult.Unavailable(KeyReadFailure.KEY_NOT_CONFIGURED),
            result
        )
        assertFalse("an unconfigured credential must not claim to be configured", storage.hasCredential())
    }

    /** G-5 — a healthy stored credential reads back as Available. */
    @Test
    fun validCredentialIsAvailable() {
        val storage = InMemorySecureCredentialStorage("AIzaValidTestKey")
        val result = storage.readCredential()
        assertTrue("valid credential must be Available", result is CredentialReadResult.Available)
        assertEquals("AIzaValidTestKey", (result as CredentialReadResult.Available).credential)
        assertTrue(storage.hasCredential())
    }

    /**
     * G-5 — DECRYPT FAILURE IS NOT "NOT CONFIGURED". This is the core regression: a key that
     * exists but cannot be decrypted must never be reported as absent.
     */
    @Test
    fun decryptFailureIsDistinctFromNotConfigured() {
        val storage = InMemorySecureCredentialStorage("AIzaPresentButUndecryptable")
        storage.unavailableReason = KeyReadFailure.KEY_DECRYPT_FAILED

        val result = storage.readCredential()
        assertEquals(
            CredentialReadResult.Unavailable(KeyReadFailure.KEY_DECRYPT_FAILED),
            result
        )
        assertFalse(
            "a stored-but-undecryptable credential must NOT report as configured",
            storage.hasCredential()
        )
    }

    /** G-5 — a corrupt stored value is KEY_READ_FAILED, distinct from both other states. */
    @Test
    fun corruptedStoredValueIsReadFailure() {
        val storage = InMemorySecureCredentialStorage("AIzaCorrupted")
        storage.unavailableReason = KeyReadFailure.KEY_READ_FAILED
        assertEquals(
            CredentialReadResult.Unavailable(KeyReadFailure.KEY_READ_FAILED),
            storage.readCredential()
        )
    }

    /** G-5 — unusable key material is KEY_INVALID. */
    @Test
    fun invalidKeyMaterialIsKeyInvalid() {
        val storage = InMemorySecureCredentialStorage("AIzaWrongShape")
        storage.unavailableReason = KeyReadFailure.KEY_INVALID
        assertEquals(
            CredentialReadResult.Unavailable(KeyReadFailure.KEY_INVALID),
            storage.readCredential()
        )
    }

    /**
     * G-5 — all four states are DISTINCT. If two were ever merged, Settings would again be able
     * to claim "Not Configured" for a credential that is present.
     */
    @Test
    fun allFourFailureStatesAreDistinct() {
        val reasons = KeyReadFailure.entries.toList()
        assertEquals(4, reasons.size)
        assertEquals(4, reasons.toSet().size)

        val distinctResults = reasons.map { r ->
            val s = InMemorySecureCredentialStorage("AIzaX")
            s.unavailableReason = r
            s.readCredential()
        }
        assertEquals(
            "each failure state must produce a distinct result",
            4,
            distinctResults.toSet().size
        )
    }

    // ---------------- cancellation safety ----------------

    /**
     * G-5 — CancellationException must NOT be converted into a credential failure.
     * Swallowing it would make a cancelled coroutine look like "no key configured".
     */
    @Test
    fun cancellationExceptionIsNeverSwallowedIntoCredentialFailure() {
        // Use a storage whose read path throws cancellation, to prove it propagates.
        val storage = object : SecureCredentialStorage {
            override fun saveCredential(credential: String) = Unit
            override fun clearCredential() = Unit
            override fun getCredential(): String? = throw CancellationException("cancelled")
            override fun hasCredential(): Boolean = true
            override fun getMaskedCredential(): String = ""
            override fun readCredential(): CredentialReadResult = throw CancellationException("cancelled")
        }

        var propagated = false
        try {
            storage.readCredential()
        } catch (e: CancellationException) {
            propagated = true
        }
        assertTrue("CancellationException must propagate out of readCredential()", propagated)
    }

    /**
     * G-5 — masking never leaks the secret (unchanged invariant, re-asserted so G-5 cannot have
     * weakened it).
     */
    @Test
    fun maskedCredentialNeverLeaksTheSecret() {
        val storage = InMemorySecureCredentialStorage("AIzaSySuperSecretKeyValue1234")
        val masked = storage.getMaskedCredential()
        assertFalse("masked value must not contain the secret", masked.contains("SuperSecretKeyValue"))
        assertTrue("masked value must still reveal the tail for recognition", masked.endsWith("1234"))
    }

    /**
     * G-5 — a typed failure must not leak any secret material through its representation.
     */
    @Test
    fun failureStatesCarryNoSecretMaterial() {
        val s = InMemorySecureCredentialStorage("AIzaSySuperSecretKeyValue1234")
        s.unavailableReason = KeyReadFailure.KEY_DECRYPT_FAILED
        val text = s.readCredential().toString()
        assertFalse("failure state must not embed the secret", text.contains("SuperSecretKeyValue"))
        assertFalse("failure state must not embed the secret", text.contains("AIzaSy"))
    }
}
