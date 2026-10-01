package com.example.ai.hybrid

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import kotlinx.coroutines.CancellationException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Phase 6 Batch 4 / G-5 — outcome of a credential read, distinguishing the failure modes that
 * were previously all collapsed into `null`.
 *
 * `getCredential()` returning null used to mean BOTH "the user never configured a key" AND
 * "a key exists but could not be decrypted". The UI then reported "Not Configured" for a key
 * that was actually present, which is actively misleading. These states are now explicit:
 *
 * - [KEY_NOT_CONFIGURED] — nothing stored. This is the ONLY state that may report "Not Configured".
 * - [KEY_READ_FAILED]     — stored value present but unreadable (e.g. corrupted base64/ciphertext).
 * - [KEY_DECRYPT_FAILED]  — ciphertext present, keystore key unavailable/rejected it (AEAD tag
 *                           failure, key invalidated by a device change, alias removed).
 * - [KEY_INVALID]         — key material exists but is unusable/of the wrong shape.
 *
 * No secret, ciphertext or key bytes are ever carried in this type.
 */
sealed interface CredentialReadResult {
    /** Secret recovered. The value is never logged. */
    data class Available(val credential: String) : CredentialReadResult

    data class Unavailable(val reason: KeyReadFailure) : CredentialReadResult
}

/** The distinct ways a stored credential can fail to be read. */
enum class KeyReadFailure {
    KEY_NOT_CONFIGURED,
    KEY_READ_FAILED,
    KEY_DECRYPT_FAILED,
    KEY_INVALID
}

/**
 * Contract for secure, encrypted-at-rest storage of user credentials.
 *
 * Security Requirements:
 * - Credentials must NEVER be stored in plaintext.
 * - Credentials must NEVER be synced to cloud, sent to VoltAtlas servers, or logged.
 * - Masked credential display must never leak the full secret.
 *
 * Phase 6 Batch 4 / G-5: [readCredential] exposes the typed failure mode. [getCredential] and
 * [hasCredential] are retained UNCHANGED so every existing consumer keeps its exact contract.
 */
interface SecureCredentialStorage {
    fun saveCredential(credential: String)
    fun getCredential(): String?
    fun clearCredential()
    fun hasCredential(): Boolean
    fun getMaskedCredential(): String

    /**
     * Typed read. Implementations MUST NOT swallow [kotlinx.coroutines.CancellationException]:
     * cancellation is rethrown so it can never be misreported as a credential problem.
     */
    fun readCredential(): CredentialReadResult
}

/**
 * Production implementation of [SecureCredentialStorage] utilizing the Android Keystore
 * with AES-256-GCM authenticated encryption.
 */
class AndroidKeystoreSecureCredentialStorage(
    private val context: Context,
    private val keyAlias: String = "voltatlas_ai_gemini_key"
) : SecureCredentialStorage {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val lock = Any()

    override fun saveCredential(credential: String) {
        val trimmed = credential.trim()
        if (trimmed.isEmpty()) {
            clearCredential()
            return
        }

        synchronized(lock) {
            val encryptedBytes = encrypt(trimmed.toByteArray(Charsets.UTF_8))
            val base64Encrypted = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            prefs.edit().putString(KEY_ENCRYPTED_CREDENTIAL, base64Encrypted).apply()
        }
    }

    override fun getCredential(): String? {
        synchronized(lock) {
            val base64Encrypted = prefs.getString(KEY_ENCRYPTED_CREDENTIAL, null) ?: return null
            return try {
                val encryptedBytes = Base64.decode(base64Encrypted, Base64.NO_WRAP)
                val decryptedBytes = decrypt(encryptedBytes)
                String(decryptedBytes, Charsets.UTF_8).takeIf { it.isNotBlank() }
            } catch (e: CancellationException) {
                // Phase 6 Batch 4 / G-5: cancellation must propagate; swallowing it would make a
                // cancelled coroutine look like "no key configured".
                throw e
            } catch (_: Throwable) {
                null
            }
        }
    }

    /**
     * Phase 6 Batch 4 / G-5 — typed read that separates "never configured" from "present but
     * unreadable/undecryptable", and never swallows cancellation.
     */
    override fun readCredential(): CredentialReadResult = synchronized(lock) {
        val base64Encrypted = prefs.getString(KEY_ENCRYPTED_CREDENTIAL, null)
            ?: return@synchronized CredentialReadResult.Unavailable(KeyReadFailure.KEY_NOT_CONFIGURED)

        // 1. Stored bytes must be readable at all.
        val encryptedBytes = try {
            Base64.decode(base64Encrypted, Base64.NO_WRAP)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return@synchronized CredentialReadResult.Unavailable(KeyReadFailure.KEY_READ_FAILED)
        }

        if (encryptedBytes.isEmpty()) {
            return@synchronized CredentialReadResult.Unavailable(KeyReadFailure.KEY_READ_FAILED)
        }

        // 2. Ciphertext must decrypt with the keystore key.
        val plaintext = try {
            decrypt(encryptedBytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: java.security.GeneralSecurityException) {
            // AEAD tag failure / key invalidated / alias removed — the stored value exists but
            // this device can no longer decrypt it. Distinct from "not configured".
            return@synchronized CredentialReadResult.Unavailable(KeyReadFailure.KEY_DECRYPT_FAILED)
        } catch (e: IllegalArgumentException) {
            // Corrupt wire framing (bad IV length header) — the payload itself is unusable.
            return@synchronized CredentialReadResult.Unavailable(KeyReadFailure.KEY_INVALID)
        } catch (_: Throwable) {
            return@synchronized CredentialReadResult.Unavailable(KeyReadFailure.KEY_READ_FAILED)
        }

        // 3. Decrypted material must be a usable credential.
        val credential = String(plaintext, Charsets.UTF_8)
        if (credential.isBlank()) {
            return@synchronized CredentialReadResult.Unavailable(KeyReadFailure.KEY_INVALID)
        }
        CredentialReadResult.Available(credential)
    }

    override fun clearCredential() {
        synchronized(lock) {
            prefs.edit().remove(KEY_ENCRYPTED_CREDENTIAL).apply()
            try {
                val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
                keyStore.load(null)
                if (keyStore.containsAlias(keyAlias)) {
                    keyStore.deleteEntry(keyAlias)
                }
            } catch (_: Throwable) {
                // Ignore keystore cleanup errors
            }
        }
    }

    override fun hasCredential(): Boolean {
        // Phase 6 Batch 4 / G-5: a stored-but-unreadable credential must NOT report "configured"
        // any more than an unconfigured one does. Available() is the only success state.
        return readCredential() is CredentialReadResult.Available
    }

    override fun getMaskedCredential(): String {
        val credential = getCredential() ?: return ""
        return mask(credential)
    }

    private fun encrypt(plaintext: ByteArray): ByteArray {
        val key = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)

        // Wire format: 1 byte IV length + IV bytes + Ciphertext
        val result = ByteArray(1 + iv.size + ciphertext.size)
        result[0] = iv.size.toByte()
        System.arraycopy(iv, 0, result, 1, iv.size)
        System.arraycopy(ciphertext, 0, result, 1 + iv.size, ciphertext.size)
        return result
    }

    private fun decrypt(encryptedData: ByteArray): ByteArray {
        require(encryptedData.isNotEmpty()) { "Encrypted payload cannot be empty" }
        val ivSize = encryptedData[0].toInt()
        require(encryptedData.size > 1 + ivSize) { "Corrupt encrypted payload" }

        val iv = ByteArray(ivSize)
        System.arraycopy(encryptedData, 1, iv, 0, ivSize)

        val ciphertextLength = encryptedData.size - 1 - ivSize
        val ciphertext = ByteArray(ciphertextLength)
        System.arraycopy(encryptedData, 1 + ivSize, ciphertext, 0, ciphertextLength)

        val key = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            if (keyStore.containsAlias(keyAlias)) {
                val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
                if (entry != null) {
                    return entry.secretKey
                }
            }

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val parameterSpec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(parameterSpec)
            keyGenerator.generateKey()
        } catch (e: Throwable) {
            throw SecurityException("AndroidKeyStore hardware encryption unavailable or key generation failed", e)
        }
    }

    companion object {
        private const val PREFS_NAME = "voltatlas_ai_secure_keystore"
        private const val KEY_ENCRYPTED_CREDENTIAL = "enc_gemini_api_key"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128

        fun mask(credential: String): String {
            val trimmed = credential.trim()
            if (trimmed.isEmpty()) return ""
            return if (trimmed.length >= 8) {
                "••••••••••••" + trimmed.takeLast(4)
            } else {
                "••••••••••••"
            }
        }
    }
}


/**
 * Global accessor / locator for [SecureCredentialStorage].
 */
object DefaultSecureCredentialStorage {
    private var testInstance: SecureCredentialStorage? = null
    private var defaultInstance: SecureCredentialStorage? = null

    fun setTestInstance(instance: SecureCredentialStorage?) {
        testInstance = instance
    }

    fun reset() {
        testInstance = null
        defaultInstance = null
    }

    fun getInstance(context: Context): SecureCredentialStorage {
        testInstance?.let { return it }
        return defaultInstance ?: synchronized(this) {
            defaultInstance ?: AndroidKeystoreSecureCredentialStorage(context.applicationContext).also {
                defaultInstance = it
            }
        }
    }
}
