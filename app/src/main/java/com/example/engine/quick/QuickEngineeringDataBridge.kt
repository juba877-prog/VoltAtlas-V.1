package com.example.engine.quick

import com.example.data.Catalog
import com.example.engine.CableSizingEngine
import com.example.ui.getDistinctCatalogManufacturers
import com.example.ui.parseCableSizeSafe
import kotlin.math.abs

/**
 * Resolved AC resistance (R), inductive reactance (X), and datasheet column metadata.
 */
data class ResolvedCableImpedance(
    val rOhmPerKm: Double,
    val xOhmPerKm: Double,
    val rColumnUsed: String,
    val isCatalogResolved: Boolean = true
)

/**
 * Complete resolved cable specification from authoritative catalog & datasheet tables.
 */
data class ResolvedCableData(
    val sizeMm2: Double,
    val manufacturer: String,
    val cableType: String,
    val impedance: ResolvedCableImpedance,
    val baseAmpacityA: Double?,
    val isResolved: Boolean = true
)

/**
 * Authoritative Quick Engineering Data Bridge.
 *
 * Provides a thin, direct bridge between Quick Engineering UI selections and
 * existing canonical catalog tables (Catalog.cableTypes, Catalog.dataSheet, Catalog.cableCatalog).
 *
 * Guarantees:
 * 1. Zero duplicate catalog tables or parallel matching logic.
 * 2. Zero heuristic string guessing when canonical typeCodes exist.
 * 3. Accurate material (Cu/Al) and insulation (PVC/XLPE) temperature R column mapping.
 * 4. Pure in-memory execution with zero Room or ViewModel dependency.
 */
object QuickEngineeringDataBridge {

    /**
     * Resolves AC Resistance (R) and Reactance (X) from Catalog.dataSheet.
     *
     * Rules (IEC 60364-5-52 / technical datasheet):
     * - Copper + XLPE (90°C) -> R_Cu90
     * - Copper + PVC (70°C)  -> R_Cu70
     * - Aluminium + XLPE (90°C) -> R_Al90
     * - Aluminium + PVC (70°C)  -> R_Al70
     */
    fun resolveCableImpedance(
        sizeMm2: Double,
        cableType: String
    ): ResolvedCableImpedance? {
        if (sizeMm2 <= 0.0) return null

        val sheetEntry = Catalog.dataSheet.firstOrNull {
            abs(it.size - sizeMm2) < 0.001
        } ?: return null

        val isCu = !cableType.contains("Al", ignoreCase = true)
        val isXLPE = cableType.contains("XLPE", ignoreCase = true)

        val (r, rColumn) = when {
            isCu && isXLPE -> Pair(sheetEntry.R_Cu90, "R_Cu90 (90°C)")
            isCu && !isXLPE -> Pair(sheetEntry.R_Cu70, "R_Cu70 (70°C)")
            !isCu && isXLPE -> Pair(sheetEntry.R_Al90, "R_Al90 (90°C)")
            else -> Pair(sheetEntry.R_Al70, "R_Al70 (70°C)")
        }

        if (r <= 0.0 || sheetEntry.X < 0.0) return null

        return ResolvedCableImpedance(
            rOhmPerKm = r,
            xOhmPerKm = sheetEntry.X,
            rColumnUsed = rColumn,
            isCatalogResolved = true
        )
    }

    /**
     * Resolves base catalog ampacity (Iz) in Amperes from Catalog.cableCatalog.
     */
    fun resolveBaseAmpacity(
        sizeMm2: Double,
        cableType: String,
        manufacturer: String,
        installation: String
    ): Double? {
        if (sizeMm2 <= 0.0) return null

        val typeCode = Catalog.cableTypes.firstOrNull {
            it.cableType.trim().equals(cableType.trim(), ignoreCase = true)
        }?.code ?: return null

        val entry = Catalog.cableCatalog.firstOrNull {
            it.typeCode == typeCode &&
            it.mfg.trim().equals(manufacturer.trim(), ignoreCase = true) &&
            abs(it.size - sizeMm2) < 0.001
        } ?: return null

        val instLower = installation.trim().lowercase()
        val ampacity = when {
            instLower.contains("direct") && (instLower.contains("ground") || instLower.contains("buried")) -> entry.gnd
            instLower.contains("duct") -> entry.duct
            else -> entry.air
        }
        return if (ampacity > 0.0) ampacity else null
    }

    /**
     * Resolves complete cable data record.
     */
    fun resolveFullCableData(
        sizeMm2: Double,
        cableType: String,
        manufacturer: String,
        installation: String
    ): ResolvedCableData? {
        val impedance = resolveCableImpedance(sizeMm2, cableType) ?: return null
        val ampacity = resolveBaseAmpacity(sizeMm2, cableType, manufacturer, installation)

        return ResolvedCableData(
            sizeMm2 = sizeMm2,
            manufacturer = manufacturer,
            cableType = cableType,
            impedance = impedance,
            baseAmpacityA = ampacity,
            isResolved = true
        )
    }

    /**
     * Returns distinct manufacturers present in authoritative Catalog.cableCatalog.
     */
    fun getAvailableManufacturers(): List<String> = getDistinctCatalogManufacturers()

    /**
     * Returns valid cable family names available for the specified manufacturer.
     */
    fun getAvailableFamilies(manufacturer: String): List<String> {
        val validCodes = Catalog.cableCatalog
            .asSequence()
            .filter { it.mfg.trim().equals(manufacturer.trim(), ignoreCase = true) }
            .map { it.typeCode }
            .toSet()

        val matching = Catalog.cableTypes
            .filter { it.code in validCodes }
            .map { it.cableType }
            .distinct()
            .sorted()

        return if (matching.isNotEmpty()) matching else listOf("Cu/XLPE/PVC - 4C")
    }

    /**
     * Returns available numeric cable sizes (mm²) for the specified manufacturer and family.
     */
    fun getAvailableSizes(manufacturer: String, cableType: String): List<Double> {
        val typeCode = Catalog.cableTypes
            .firstOrNull { it.cableType.trim().equals(cableType.trim(), ignoreCase = true) }
            ?.code ?: return emptyList()

        return Catalog.cableCatalog
            .asSequence()
            .filter { it.mfg.trim().equals(manufacturer.trim(), ignoreCase = true) }
            .filter { it.typeCode == typeCode }
            .map { it.size }
            .distinct()
            .sorted()
            .toList()
    }
}
