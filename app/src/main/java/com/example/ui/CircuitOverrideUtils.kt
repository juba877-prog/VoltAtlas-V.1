package com.example.ui

import com.example.data.Catalog
import com.example.data.resolveCableType
import com.example.engine.CableSizingEngine

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

data class CableFamilyInfo(
    val originalType: String,
    val material: String,
    val armourGroup: String,
    val exactArmour: String,
    val coresConfig: String,
    val insulation: String,
    val displayLabel: String
)

fun formatCatalogCableSizeSafe(size: Double): String {
    val value = if (size % 1.0 == 0.0) size.toInt().toString() else size.toString()
    return "$value mm\u00B2"
}

fun parseCableSizeSafe(value: String?): Double? {
    val match = Regex("""^\s*([0-9]+(?:\.[0-9]+)?)""")
        .find(value.orEmpty())

    return match
        ?.groupValues
        ?.getOrNull(1)
        ?.toDoubleOrNull()
}

fun catalogCableSizeOptions(manufacturer: String, cableType: String): List<String> {
    val typeCode = Catalog.cableTypes
        .firstOrNull { it.cableType.equals(cableType, ignoreCase = true) }
        ?.code ?: return emptyList()

    return Catalog.cableCatalog
        .asSequence()
        .filter { it.mfg.equals(manufacturer.trim(), ignoreCase = true) }
        .filter { it.typeCode == typeCode }
        .map { it.size }
        .distinct()
        .sorted()
        .map(::formatCatalogCableSizeSafe)
        .toList()
}

fun standardConductorSizeOptions(): List<String> = listOf(
    1.5, 2.5, 4.0, 6.0, 10.0, 16.0, 25.0, 35.0, 50.0, 70.0,
    95.0, 120.0, 150.0, 185.0, 240.0, 300.0, 400.0, 500.0, 630.0
).map(::formatCatalogCableSizeSafe)

fun standardDeviceRatingOptions(): List<String> =
    CableSizingEngine.STANDARD_RATINGS.map { "${it.toInt()} A" }

fun parseFamilyInfo(
    rawType: String,
    defaultCoreConfig: String = "4C",
    defaultConductor: String = "Cu",
    defaultInsulation: String = "XLPE"
): CableFamilyInfo {
    val resolved = resolveCableType(rawType, defaultCoreConfig, defaultConductor, defaultInsulation)
    val ct = Catalog.cableTypes.firstOrNull { it.cableType.trim().equals(resolved.trim(), ignoreCase = true) }
        ?: Catalog.cableTypes.firstOrNull { it.cableType.trim().equals(rawType.trim(), ignoreCase = true) }
    val cond = ct?.conductor ?: if (rawType.contains("Al", ignoreCase = true)) "Al" else "Cu"
    val mat = if (cond.contains("Al", ignoreCase = true)) "Aluminium" else "Copper"
    val arm = ct?.armour ?: if (rawType.contains("SWA", ignoreCase = true)) "SWA" else "None"
    val armGroup = if (arm.equals("None", ignoreCase = true) || arm.isBlank()) "Unarmoured" else "Armoured"
    val rawCores = ct?.cores ?: rawType
    val coresConfig = when {
        rawCores.contains("1C") || rawCores.contains("Single", ignoreCase = true) -> "1C"
        rawCores.contains("2C") -> "2C"
        rawCores.contains("3.5C") -> "3.5C"
        rawCores.contains("3C") -> "3C"
        rawCores.contains("4C") -> "4C"
        else -> defaultCoreConfig.ifBlank { "4C" }
    }
    val ins = ct?.insulation ?: if (rawType.contains("XLPE", ignoreCase = true)) "XLPE" else "PVC"
    return CableFamilyInfo(
        originalType = ct?.cableType ?: rawType,
        material = mat,
        armourGroup = armGroup,
        exactArmour = arm,
        coresConfig = coresConfig,
        insulation = ins,
        displayLabel = "$mat • $armGroup (${if (arm != "None") arm else "Plain"}) • $coresConfig • $ins"
    )
}

fun getDistinctCatalogManufacturers(): List<String> {
    val dynamicList = Catalog.cableCatalog
        .map { it.mfg.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()
    return if (dynamicList.isEmpty()) listOf("Elsewedy", "IEC-Generic") else dynamicList
}
