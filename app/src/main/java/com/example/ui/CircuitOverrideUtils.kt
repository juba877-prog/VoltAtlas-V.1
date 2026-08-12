package com.example.ui

/**
 * Room representation:
 * null = AUTO
 * non-null = explicit manual override
 */
fun normalizeCableOverride(value: String?): String? {
    val cleaned = value?.trim().orEmpty()
    return when {
        cleaned.isBlank() -> null
        cleaned.equals("AUTO", ignoreCase = true) -> null
        else -> cleaned
    }
}

fun cableOverrideForDisplay(value: String?): String =
    value?.takeIf { it.isNotBlank() } ?: "AUTO"

fun normalizeDeviceSizeOverride(value: String?): Double? {
    val cleaned = value?.trim().orEmpty()
    if (cleaned.isBlank() || cleaned.equals("AUTO", ignoreCase = true)) {
        return null
    }
    return cleaned
        .replace("A", "", ignoreCase = true)
        .trim()
        .toDoubleOrNull()
}

fun deviceSizeOverrideForDisplay(value: Double?): String =
    value?.let {
        if (it % 1.0 == 0.0) "${it.toInt()} A" else "$it A"
    } ?: "AUTO"

fun normalizeDeviceTypeOverride(value: String?): String? {
    val cleaned = value?.trim().orEmpty()
    return when {
        cleaned.isBlank() -> null
        cleaned.equals("AUTO", ignoreCase = true) -> null
        else -> cleaned
    }
}
