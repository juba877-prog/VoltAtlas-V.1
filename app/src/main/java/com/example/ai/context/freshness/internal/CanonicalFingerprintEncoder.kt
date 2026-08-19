package com.example.ai.context.freshness.internal

import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/**
 * Deterministic canonical encoder and SHA-256 fingerprint digest generator.
 *
 * Framing Invariants:
 * - Text: Normalized to Unicode Form.NFC, encoded as `T:<length_in_bytes>:<utf8_bytes>`
 * - Integer / Long: Encoded as `I:<long_value>`
 * - Floating point: Rejects NaN / Infinity, normalizes -0.0 to 0.0, encoded as `D:<plain_decimal_string>` (Locale.ROOT)
 * - Boolean: Encoded as `B:1` or `B:0`
 * - Null: Encoded as `N:`
 * - Quantity with Unit: Encoded as `Q:<canonical_decimal>:<canonical_unit>`
 * - Map / Object: Alphabetically sorted by key, encoded as `M:<count>:{<key_bytes><value_bytes>}...`
 * - Set: Order-independent; encoded elements are sorted lexicographically before framing: `S:<count>:{<elem_bytes>}...`
 * - List: Order-dependent sequence: `L:<count>:{<elem_bytes>}...`
 */
class CanonicalFingerprintEncoder {

    private val entries = mutableMapOf<String, Any?>()

    fun putString(key: String, value: String?): CanonicalFingerprintEncoder {
        entries[key] = value
        return this
    }

    fun putInt(key: String, value: Int?): CanonicalFingerprintEncoder {
        entries[key] = value?.toLong()
        return this
    }

    fun putLong(key: String, value: Long?): CanonicalFingerprintEncoder {
        entries[key] = value
        return this
    }

    fun putDouble(key: String, value: Double?): CanonicalFingerprintEncoder {
        entries[key] = value
        return this
    }

    fun putBoolean(key: String, value: Boolean?): CanonicalFingerprintEncoder {
        entries[key] = value
        return this
    }

    fun putQuantity(key: String, value: Double?, unit: String): CanonicalFingerprintEncoder {
        entries[key] = if (value != null) QuantityValue(value, unit) else null
        return this
    }

    fun putList(key: String, values: List<Any?>?): CanonicalFingerprintEncoder {
        entries[key] = values
        return this
    }

    fun putSet(key: String, values: Set<Any?>?): CanonicalFingerprintEncoder {
        entries[key] = values
        return this
    }

    fun putNested(key: String, block: (CanonicalFingerprintEncoder) -> Unit): CanonicalFingerprintEncoder {
        val child = CanonicalFingerprintEncoder()
        block(child)
        entries[key] = child.entries
        return this
    }

    fun toDebugMap(): Map<String, Any?> = entries.toMap()

    fun encodeCanonicalBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        encodeValue(out, entries)
        return out.toByteArray()
    }

    fun computeSha256Hex(): String {
        val bytes = encodeCanonicalBytes()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(Locale.ROOT, it) }
    }

    companion object {
        data class QuantityValue(val value: Double, val unit: String)

        private fun encodeValue(out: ByteArrayOutputStream, value: Any?) {
            when (value) {
                null -> {
                    out.write("N:;".toByteArray(StandardCharsets.UTF_8))
                }
                is String -> {
                    val normalized = Normalizer.normalize(value, Normalizer.Form.NFC)
                    val bytes = normalized.toByteArray(StandardCharsets.UTF_8)
                    out.write("T:${bytes.size}:".toByteArray(StandardCharsets.UTF_8))
                    out.write(bytes)
                    out.write(';'.code)
                }
                is Int -> {
                    encodeValue(out, value.toLong())
                }
                is Long -> {
                    out.write("I:$value;".toByteArray(StandardCharsets.UTF_8))
                }
                is Float -> {
                    encodeValue(out, value.toDouble())
                }
                is Double -> {
                    require(!value.isNaN()) { "Canonical encoder rejects NaN." }
                    require(!value.isInfinite()) { "Canonical encoder rejects Infinite values." }
                    val cleanVal = if (value == 0.0 || value == -0.0) 0.0 else value
                    val decStr = BigDecimal.valueOf(cleanVal).stripTrailingZeros().toPlainString()
                    out.write("D:$decStr;".toByteArray(StandardCharsets.UTF_8))
                }
                is Boolean -> {
                    val flag = if (value) "1" else "0"
                    out.write("B:$flag;".toByteArray(StandardCharsets.UTF_8))
                }
                is QuantityValue -> {
                    require(!value.value.isNaN()) { "Canonical encoder rejects NaN in quantity." }
                    require(!value.value.isInfinite()) { "Canonical encoder rejects Infinite in quantity." }
                    val cleanVal = if (value.value == 0.0 || value.value == -0.0) 0.0 else value.value
                    val decStr = BigDecimal.valueOf(cleanVal).stripTrailingZeros().toPlainString()
                    val normalizedUnit = Normalizer.normalize(value.unit.trim(), Normalizer.Form.NFC)
                    val unitBytes = normalizedUnit.toByteArray(StandardCharsets.UTF_8)
                    out.write("Q:$decStr:${unitBytes.size}:".toByteArray(StandardCharsets.UTF_8))
                    out.write(unitBytes)
                    out.write(';'.code)
                }
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val map = value as Map<String, Any?>
                    val sortedKeys = map.keys.sorted()
                    out.write("M:${sortedKeys.size}:{".toByteArray(StandardCharsets.UTF_8))
                    for (k in sortedKeys) {
                        encodeValue(out, k)
                        encodeValue(out, map[k])
                    }
                    out.write("};".toByteArray(StandardCharsets.UTF_8))
                }
                is Set<*> -> {
                    // Set elements are rendered into individual byte arrays and sorted lexicographically for order independence
                    val encodedElements = value.map { elem ->
                        val elemOut = ByteArrayOutputStream()
                        encodeValue(elemOut, elem)
                        elemOut.toByteArray()
                    }.sortedWith { a, b ->
                        compareByteArrays(a, b)
                    }

                    out.write("S:${encodedElements.size}:{".toByteArray(StandardCharsets.UTF_8))
                    for (elemBytes in encodedElements) {
                        out.write(elemBytes)
                    }
                    out.write("};".toByteArray(StandardCharsets.UTF_8))
                }
                is List<*> -> {
                    // List elements preserve sequence order
                    out.write("L:${value.size}:{".toByteArray(StandardCharsets.UTF_8))
                    for (elem in value) {
                        encodeValue(out, elem)
                    }
                    out.write("};".toByteArray(StandardCharsets.UTF_8))
                }
                else -> {
                    // Fallback to text representation
                    encodeValue(out, value.toString())
                }
            }
        }

        private fun compareByteArrays(a: ByteArray, b: ByteArray): Int {
            val minLen = minOf(a.size, b.size)
            for (i in 0 until minLen) {
                val byteA = a[i].toInt() and 0xFF
                val byteB = b[i].toInt() and 0xFF
                if (byteA != byteB) {
                    return byteA.compareTo(byteB)
                }
            }
            return a.size.compareTo(b.size)
        }
    }
}
