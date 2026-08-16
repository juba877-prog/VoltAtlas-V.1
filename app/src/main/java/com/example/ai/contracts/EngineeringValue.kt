package com.example.ai.contracts

import com.squareup.moshi.JsonClass

/**
 * Type discriminator for closed engineering values.
 */
enum class EngineeringValueKind {
    DECIMAL,
    INTEGER,
    TEXT,
    BOOLEAN,
    MISSING
}

/**
 * Closed, serialization-safe typed representation of an engineering quantity.
 * Preserves the canonical numerical or boolean value alongside a typed canonical unit
 * and human-readable formatting, avoiding lossy or unstructured string-only representation.
 *
 * Structurally validated in init: exactly one canonical value variant is active
 * unless the kind is MISSING.
 */
@JsonClass(generateAdapter = true)
data class EngineeringValue(
    val kind: EngineeringValueKind,
    val decimalValue: Double? = null,
    val intValue: Long? = null,
    val textValue: String? = null,
    val booleanValue: Boolean? = null,
    val unit: EngineeringUnit? = null,
    val formattedDisplay: String? = null
) {
    init {
        when (kind) {
            EngineeringValueKind.DECIMAL -> {
                require(decimalValue != null) { "EngineeringValue of kind DECIMAL requires non-null decimalValue" }
                require(intValue == null && textValue == null && booleanValue == null) {
                    "EngineeringValue of kind DECIMAL cannot have multiple active value fields"
                }
            }
            EngineeringValueKind.INTEGER -> {
                require(intValue != null) { "EngineeringValue of kind INTEGER requires non-null intValue" }
                require(decimalValue == null && textValue == null && booleanValue == null) {
                    "EngineeringValue of kind INTEGER cannot have multiple active value fields"
                }
            }
            EngineeringValueKind.TEXT -> {
                require(textValue != null) { "EngineeringValue of kind TEXT requires non-null textValue" }
                require(decimalValue == null && intValue == null && booleanValue == null) {
                    "EngineeringValue of kind TEXT cannot have multiple active value fields"
                }
            }
            EngineeringValueKind.BOOLEAN -> {
                require(booleanValue != null) { "EngineeringValue of kind BOOLEAN requires non-null booleanValue" }
                require(decimalValue == null && intValue == null && textValue == null) {
                    "EngineeringValue of kind BOOLEAN cannot have multiple active value fields"
                }
            }
            EngineeringValueKind.MISSING -> {
                require(decimalValue == null && intValue == null && textValue == null && booleanValue == null) {
                    "EngineeringValue of kind MISSING cannot have numeric, text, or boolean payloads"
                }
            }
        }
    }

    companion object {
        fun ofDecimal(value: Double, unit: EngineeringUnit? = null, formatted: String? = null): EngineeringValue {
            val fmt = formatted ?: if (unit != null && unit != EngineeringUnit.NONE) "$value ${unit.symbol}" else value.toString()
            return EngineeringValue(
                kind = EngineeringValueKind.DECIMAL,
                decimalValue = value,
                unit = unit,
                formattedDisplay = fmt
            )
        }

        fun ofInt(value: Long, unit: EngineeringUnit? = null, formatted: String? = null): EngineeringValue {
            val fmt = formatted ?: if (unit != null && unit != EngineeringUnit.NONE) "$value ${unit.symbol}" else value.toString()
            return EngineeringValue(
                kind = EngineeringValueKind.INTEGER,
                intValue = value,
                unit = unit,
                formattedDisplay = fmt
            )
        }

        fun ofText(value: String, formatted: String? = null): EngineeringValue {
            return EngineeringValue(
                kind = EngineeringValueKind.TEXT,
                textValue = value,
                unit = EngineeringUnit.NONE,
                formattedDisplay = formatted ?: value
            )
        }

        fun ofBoolean(value: Boolean, formatted: String? = null): EngineeringValue {
            return EngineeringValue(
                kind = EngineeringValueKind.BOOLEAN,
                booleanValue = value,
                unit = EngineeringUnit.NONE,
                formattedDisplay = formatted ?: value.toString()
            )
        }

        fun missing(reason: String? = null): EngineeringValue {
            return EngineeringValue(
                kind = EngineeringValueKind.MISSING,
                unit = null,
                formattedDisplay = reason ?: "N/A (Missing)"
            )
        }
    }
}
