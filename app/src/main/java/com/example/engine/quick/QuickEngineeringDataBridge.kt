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
 * Outcome of a base-ampacity (Iz) lookup: either the catalog value, or the reason no value exists.
 *
 * An [Unresolved] result must never be replaced by a value taken from a different installation
 * basis (N1).
 */
sealed interface BaseAmpacityResolution {

    /** Why no base ampacity could be resolved. */
    enum class Reason {
        /** Installation method is unsupported, blank, or unknown to this catalog. */
        UNSUPPORTED_INSTALLATION,

        /** No catalog row matched size / cable type / manufacturer, or it carries no ampacity. */
        CATALOG_MISS
    }

    /** Catalog ampacity (Iz) in amperes for the resolved installation family. */
    data class Resolved(val ampacityA: Double) : BaseAmpacityResolution

    /** No ampacity value exists; [reason] says why and [detail] is the user-facing explanation. */
    data class Unresolved(
        val reason: Reason,
        val detail: String
    ) : BaseAmpacityResolution
}

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
     *
     * Returns null when no value exists, including when [installation] is unsupported, blank or
     * unknown: the free-air column is never substituted for an unrecognized method. Use
     * [resolveBaseAmpacityDetailed] to learn why no value could be resolved.
     */
    fun resolveBaseAmpacity(
        sizeMm2: Double,
        cableType: String,
        manufacturer: String,
        installation: String
    ): Double? = when (
        val resolution = resolveBaseAmpacityDetailed(sizeMm2, cableType, manufacturer, installation)
    ) {
        is BaseAmpacityResolution.Resolved -> resolution.ampacityA
        is BaseAmpacityResolution.Unresolved -> null
    }

    /**
     * Resolves base catalog ampacity (Iz), reporting WHY no value could be resolved (N1).
     *
     * Installation handling is delegated to the existing A1-R resolver in [CableSizingEngine]:
     * [CableSizingEngine.unsupportedInstallationMessage] supplies the supported-method deny list
     * (blank, In Conduit, In Conduit / Trunking, A1/A2/B1/B2/C, In Ground) and
     * [CableSizingEngine.canonicalInstallation] supplies the owner-approved D1 -> Duct and
     * D2 -> Buried aliases, so this path cannot disagree with cable sizing.
     *
     * The family selection below mirrors `CableSizingEngine.getGroupingDerating` exactly (Buried,
     * then Duct, then Air/free-air/tray) evaluated on the canonical form. The only difference is
     * the outcome for an unrecognized method: [BaseAmpacityResolution.Unresolved] instead of a
     * silent fall-through to the free-air column.
     *
     * No synonym and no second mapping table are defined here.
     */
    fun resolveBaseAmpacityDetailed(
        sizeMm2: Double,
        cableType: String,
        manufacturer: String,
        installation: String
    ): BaseAmpacityResolution {
        val unsupportedMessage = CableSizingEngine.unsupportedInstallationMessage(installation)
        if (unsupportedMessage != null) {
            return BaseAmpacityResolution.Unresolved(
                BaseAmpacityResolution.Reason.UNSUPPORTED_INSTALLATION,
                unsupportedMessage
            )
        }

        val family = resolveAmpacityFamily(installation)
        if (family == null) {
            return BaseAmpacityResolution.Unresolved(
                BaseAmpacityResolution.Reason.UNSUPPORTED_INSTALLATION,
                "Installation method '${installation.trim()}' is not supported by the current ampacity catalog " +
                    "(supported: Duct, Buried, Air/Tray)."
            )
        }

        if (sizeMm2 <= 0.0) return catalogMiss(sizeMm2, cableType, manufacturer)

        val typeCode = Catalog.cableTypes.firstOrNull {
            it.cableType.trim().equals(cableType.trim(), ignoreCase = true)
        }?.code ?: return catalogMiss(sizeMm2, cableType, manufacturer)

        val entry = Catalog.cableCatalog.firstOrNull {
            it.typeCode == typeCode &&
            it.mfg.trim().equals(manufacturer.trim(), ignoreCase = true) &&
            abs(it.size - sizeMm2) < 0.001
        } ?: return catalogMiss(sizeMm2, cableType, manufacturer)

        val ampacity = when (family) {
            AmpacityFamily.AIR -> entry.air
            AmpacityFamily.DUCT -> entry.duct
            AmpacityFamily.BURIED -> entry.gnd
        }

        return if (ampacity > 0.0) {
            BaseAmpacityResolution.Resolved(ampacity)
        } else {
            catalogMiss(sizeMm2, cableType, manufacturer)
        }
    }

    /** Catalog ampacity family a resolved installation method maps onto. */
    private enum class AmpacityFamily { AIR, DUCT, BURIED }

    /**
     * Maps [installation] to its catalog ampacity family, or null when the method is unsupported,
     * blank, or unknown to this catalog.
     *
     * The rules are the ones `CableSizingEngine.getGroupingDerating` applies (Buried, then Duct,
     * then Air/free-air/tray), evaluated on the A1-R canonical form so the owner-approved D1/D2
     * aliases resolve here as well. Unlike that path, an unrecognized method returns null rather
     * than defaulting to free air.
     */
    private fun resolveAmpacityFamily(installation: String): AmpacityFamily? {
        val canonical = CableSizingEngine.canonicalInstallation(installation).trim().lowercase()
        return when {
            canonical == "buried" ||
                (canonical.contains("direct") &&
                    (canonical.contains("ground") || canonical.contains("buried"))) -> AmpacityFamily.BURIED
            canonical.contains("duct") -> AmpacityFamily.DUCT
            canonical.contains("free air") ||
                canonical.contains("tray") ||
                canonical == "air" -> AmpacityFamily.AIR
            else -> null
        }
    }

    private fun catalogMiss(sizeMm2: Double, cableType: String, manufacturer: String) =
        BaseAmpacityResolution.Unresolved(
            BaseAmpacityResolution.Reason.CATALOG_MISS,
            "No catalog ampacity row for size=$sizeMm2 cableType=$cableType manufacturer=$manufacturer."
        )

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
