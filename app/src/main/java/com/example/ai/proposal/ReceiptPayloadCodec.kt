package com.example.ai.proposal

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@JsonClass(generateAdapter = true)
data class EncodedValueDto(
    val kind: String,
    val stringValue: String? = null,
    val intValue: Long? = null,
    val doubleValue: Double? = null,
    val unit: String? = null
)

@JsonClass(generateAdapter = true)
data class EncodedFieldChangeDto(
    val fieldName: String,
    val action: String,
    val previousValue: EncodedValueDto? = null,
    val newValue: EncodedValueDto? = null
)

@JsonClass(generateAdapter = true)
data class EncodedReceiptPayloadDto(
    val version: String,
    val changeCount: Int,
    val changes: List<EncodedFieldChangeDto>
)

/**
 * Deterministic application-owned codec for canonical `RECEIPT-V1` machine payloads.
 *
 * Invariants:
 * - Version: `RECEIPT-V1`
 * - Allowlisted fields only
 * - Deterministic alphabetical field sorting
 * - Locale-neutral decimal representation; finite numbers only
 * - Enforces maximum UTF-8 payload length <= 65,536 bytes (64 KB)
 * - Prohibits display text, human rationale, and LLM reasoning strings
 */
object ReceiptPayloadCodec {
    const val VERSION = DURABLE_RECEIPT_SCHEMA_VERSION_V1
    const val MAX_PAYLOAD_BYTES = 65536 // 64 KB

    private val ALLOWLISTED_FIELDS = setOf(
        "overrideCableSize",
        "cableType",
        "manufacturer",
        "installation",
        "parallelRuns",
        "overrideDeviceSize",
        "overrideDeviceType",
        "lengthM"
    )

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(EncodedReceiptPayloadDto::class.java)

    /**
     * Encodes a list of [FieldChangeRecord] into a canonical, bounded UTF-8 JSON payload.
     */
    fun encode(changes: List<FieldChangeRecord>): String {
        require(changes.isNotEmpty()) { "Field changes list cannot be empty" }
        val distinctFields = changes.map { it.fieldName }.toSet()
        require(distinctFields.size == changes.size) { "Duplicate field changes detected in receipt changes list" }

        for (change in changes) {
            require(change.fieldName in ALLOWLISTED_FIELDS) {
                "Field '${change.fieldName}' is not an allowlisted patch field"
            }
            validateTypedFieldValue(change.previousValue)
            validateTypedFieldValue(change.newValue)
        }

        val sortedChanges = changes.sortedBy { it.fieldName }.map { change ->
            EncodedFieldChangeDto(
                fieldName = change.fieldName,
                action = change.action.name,
                previousValue = change.previousValue?.let { toDto(it) },
                newValue = change.newValue?.let { toDto(it) }
            )
        }

        val payloadDto = EncodedReceiptPayloadDto(
            version = VERSION,
            changeCount = sortedChanges.size,
            changes = sortedChanges
        )

        val json = adapter.toJson(payloadDto)
        val byteLength = json.toByteArray(Charsets.UTF_8).size
        require(byteLength <= MAX_PAYLOAD_BYTES) {
            "Encoded receipt payload exceeds maximum allowed size: $byteLength bytes > $MAX_PAYLOAD_BYTES bytes"
        }
        return json
    }

    /**
     * Decodes and validates a canonical JSON payload string into a list of [FieldChangeRecord].
     */
    fun decode(json: String): List<FieldChangeRecord> {
        val byteLength = json.toByteArray(Charsets.UTF_8).size
        require(byteLength <= MAX_PAYLOAD_BYTES) {
            "Receipt payload exceeds maximum allowed size: $byteLength bytes > $MAX_PAYLOAD_BYTES bytes"
        }

        val payloadDto = adapter.fromJson(json)
            ?: throw IllegalArgumentException("Failed to parse receipt payload JSON")

        require(payloadDto.version == VERSION) {
            "Unsupported receipt payload version: ${payloadDto.version} (expected $VERSION)"
        }
        require(payloadDto.changeCount == payloadDto.changes.size) {
            "Change count mismatch: declared=${payloadDto.changeCount}, actual=${payloadDto.changes.size}"
        }

        val distinctFields = payloadDto.changes.map { it.fieldName }.toSet()
        require(distinctFields.size == payloadDto.changes.size) { "Duplicate field changes detected in decoded payload" }

        return payloadDto.changes.map { dto ->
            require(dto.fieldName in ALLOWLISTED_FIELDS) {
                "Field '${dto.fieldName}' in decoded payload is not an allowlisted patch field"
            }
            val action = try {
                PatchAction.valueOf(dto.action)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid patch action: ${dto.action}", e)
            }

            FieldChangeRecord(
                fieldName = dto.fieldName,
                action = action,
                previousValue = dto.previousValue?.let { fromDto(it) },
                newValue = dto.newValue?.let { fromDto(it) },
                displayChange = null // Machine-only receipt payload; display text is strictly excluded
            )
        }
    }

    private fun validateTypedFieldValue(value: TypedFieldValue?) {
        if (value == null) return
        value.doubleValue?.let { d ->
            require(!d.isNaN() && !d.isInfinite()) { "Decimals in receipt payload must be finite numbers" }
        }
    }

    private fun toDto(v: TypedFieldValue): EncodedValueDto {
        return EncodedValueDto(
            kind = v.kind.name,
            stringValue = v.stringValue,
            intValue = v.intValue,
            doubleValue = v.doubleValue,
            unit = v.unit
        )
    }

    private fun fromDto(dto: EncodedValueDto): TypedFieldValue {
        val kind = try {
            FieldValueKind.valueOf(dto.kind)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid field value kind: ${dto.kind}", e)
        }
        return TypedFieldValue(
            kind = kind,
            stringValue = dto.stringValue,
            intValue = dto.intValue,
            doubleValue = dto.doubleValue,
            unit = dto.unit
        )
    }
}
