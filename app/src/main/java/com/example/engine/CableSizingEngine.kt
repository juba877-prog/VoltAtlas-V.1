package com.example.engine

import com.example.data.Catalog
import com.example.data.CableCatalogEntry
import com.example.data.CircuitEntity
import kotlin.math.sqrt

enum class SizingResultStatus {
    PASS,
    WARNING,
    FAIL,
    INCOMPLETE,
    ERROR
}

enum class CableFailureCode {
    AMPACITY_FAIL,
    VOLTAGE_DROP_FAIL,
    CUMULATIVE_VOLTAGE_DROP_FAIL,
    CATALOG_MISSING,
    INCOMPLETE_INPUT,
    DERATING_INVALID,
    MULTIPLE_CONSTRAINTS,
    INDETERMINATE
}

data class SizingResult(
    val Ib_A: Double,
    val deviceSize_A: Double,
    val deviceType: String,
    val cTotalDerating: Double,
    val iRequired_perCable_A: Double,
    val productCode: String,
    val cableSize_mm2: String,
    val ratedI_table_A: Double,
    val capacity_derated_Iz_A: Double,
    val utilizationPct: Double,
    val neutral_mm2: String,
    val earthCPC_mm2: String,
    val voltDrop_V: Double,
    val vdPct: Double,
    val cumulativeVDpct: Double,
    val status: String,
    val failReason: String = "",
    val cableMaterial: String = "Cu",
    val insulation: String = "XLPE",
    val kFactor: Double = 143.0,
    val deviceBreakingKA: Double = 36.0,
    val deviceMakingKA: Double = 0.0,
    val cableOD_mm: Double = 0.0,
    val earthOD_mm: Double = 0.0,
    val rUsed: Double = 0.0,
    val xUsed: Double = 0.0,
    val clearingTime_s: Double = 0.1,
    val odSourceMfg: String = "Elsewedy",
    val isODApproximated: Boolean = false,
    val warnings: List<String> = emptyList(),
    val resultStatus: SizingResultStatus,
    val isAmpacityOk: Boolean,
    val isVoltageDropOk: Boolean,
    val primaryFailureCode: CableFailureCode?,
    val isDeviceFromCatalog: Boolean = false,
    val breakerOrderingCode: String? = null,
    val conductorAreaMm2: Double? = null,
    val cpcAreaMm2: Double? = null,
    val r20: Double? = null
) {
    val phaseConductorAreaMm2: Double? get() = conductorAreaMm2
}

object CableSizingEngine {

    val STANDARD_RATINGS = listOf(
        16.0, 20.0, 25.0, 32.0, 40.0, 50.0, 63.0, 80.0, 100.0, 125.0,
        160.0, 200.0, 250.0, 315.0, 400.0, 500.0, 630.0, 800.0,
        1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3200.0, 4000.0, 5000.0, 6300.0
    )

    /**
     * Temperature-correction factors for conductor operating temperature
     * per IEC 60364-5-52 Annex G and IEC 60228:
     * 1 + α₂₀·(θ − 20), where α₂₀ = 0.00393 /K for Cu and 0.00403 /K for Al.
     *  - Cu/PVC  (70 °C): 1 + 0.00393 × 50 = 1.1965
     *  - Cu/XLPE (90 °C): 1 + 0.00393 × 70 = 1.2751
     *  - Al/PVC  (70 °C): 1 + 0.00403 × 50 = 1.2015
     *  - Al/XLPE (90 °C): 1 + 0.00403 × 70 = 1.2821
     */
    const val TEMP_FACTOR_CU_PVC = 1.1965
    const val TEMP_FACTOR_CU_XLPE = 1.2751
    const val TEMP_FACTOR_AL_PVC = 1.2015
    const val TEMP_FACTOR_AL_XLPE = 1.2821

    private val STANDARD_SIZES = listOf(
        1.5, 2.5, 4.0, 6.0, 10.0, 16.0, 25.0, 35.0, 50.0, 70.0,
        95.0, 120.0, 150.0, 185.0, 240.0, 300.0, 400.0, 500.0,
        630.0, 800.0, 1000.0
    )

    /**
     * Reduced-neutral sizing used by the workbook.
     * This is intentionally independent from CPC sizing.
     */
    fun getHalfSize(size: Double): Double = when (size) {
        in 0.0..16.0 -> size
        25.0 -> 16.0
        35.0 -> 16.0
        50.0 -> 25.0
        70.0 -> 35.0
        95.0 -> 50.0
        120.0 -> 70.0
        150.0 -> 70.0
        185.0 -> 95.0
        240.0 -> 120.0
        300.0 -> 150.0
        400.0 -> 185.0
        500.0 -> 240.0
        630.0 -> 300.0
        800.0 -> 400.0
        1000.0 -> 500.0
        else -> nextStandardSize(size / 2.0)
    }

    /**
     * IEC 60364-5-54 Table 54.7:
     * S <= 16       -> Sp = S
     * 16 < S <= 35  -> Sp = 16
     * S > 35       -> S/2, rounded UP to the next standard size.
     *
     * The displayed CPC is Cu. Final verification by the adiabatic equation
     * must remain a separate protection/short-circuit check.
     */
    fun getCPCSize(phaseSize: Double): Double = when (phaseSize) {
        in 0.0..16.0 -> phaseSize
        25.0 -> 16.0
        35.0 -> 16.0
        50.0 -> 25.0
        70.0 -> 35.0
        95.0 -> 50.0
        120.0 -> 70.0
        150.0 -> 70.0
        185.0 -> 95.0
        240.0 -> 120.0
        300.0 -> 150.0
        400.0 -> 185.0
        500.0 -> 240.0
        630.0 -> 300.0
        800.0 -> 400.0
        1000.0 -> 500.0
        else -> nextStandardSize(phaseSize / 2.0)
    }

    private fun nextStandardSize(required: Double): Double =
        STANDARD_SIZES.firstOrNull { it + EPSILON >= required }
            ?: STANDARD_SIZES.last()

    // ---------------------------------------------------------------------
    // JSON derating lookups
    // ---------------------------------------------------------------------

    fun getTempDerating(
        installation: String,
        isXLPE: Boolean,
        temp: Int
    ): Double {
        val data = Catalog.deratingData ?: return 1.0
        val normalizedInstallation = installation.trim().lowercase()

        val category = if (
            normalizedInstallation.contains("free air") ||
            normalizedInstallation.contains("on tray") ||
            normalizedInstallation == "air"
        ) {
            "air_temperature"
        } else {
            // Buried ducts and direct-buried cables use ground-temperature data.
            "ground_temperature"
        }

        val categoryObject = data.optJSONObject(category) ?: return 1.0
        val insulationKey = if (isXLPE) "XLPE" else "PVC"
        val keys = categoryObject.keys()
            .asSequence()
            .mapNotNull { it.toIntOrNull() }
            .sorted()
            .toList()

        if (keys.isEmpty()) return 1.0

        val bucket = keys.firstOrNull { it >= temp } ?: keys.last()
        return categoryObject
            .optJSONObject(bucket.toString())
            ?.optDouble(insulationKey, 1.0)
            ?: 1.0
    }

    fun getDepthDerating(installation: String, depth: Double): Double {
        // Gate on the canonical FAMILY (the same boundary the ampacity column and grouping use),
        // not on the raw 'direct buried' phrase, so canonical 'D2'/'Buried' derives the same
        // depth factor as 'Laid Direct in Ground' instead of silently skipping it.
        if (canonicalInstallationFamily(installation) != "Buried") return 1.0

        val data = Catalog.deratingData ?: return 1.0
        val factorsObject = data
            .optJSONObject("burial_depth")
            ?.optJSONObject("factors")
            ?: return 1.0

        val keys = factorsObject.keys()
            .asSequence()
            .mapNotNull { it.toDoubleOrNull() }
            .sorted()
            .toList()

        if (keys.isEmpty()) return 1.0

        val bucket = keys.firstOrNull { it + EPSILON >= depth } ?: keys.last()
        return factorsObject.optDouble(numberKey(bucket), 1.0)
    }

    fun getSoilDerating(installation: String, soilRes: Int): Double {
        // Same canonical-family gate as getDepthDerating, so depth and soil can never disagree
        // about whether an installation is buried.
        if (canonicalInstallationFamily(installation) != "Buried") return 1.0

        val data = Catalog.deratingData ?: return 1.0
        val factorsObject = data
            .optJSONObject("soil_resistivity")
            ?.optJSONObject("factors")
            ?: return 1.0

        val valueToCompare = if (soilRes <= 10) soilRes * 100 else soilRes
        val keys = factorsObject.keys()
            .asSequence()
            .mapNotNull { it.toIntOrNull() }
            .sorted()
            .toList()

        if (keys.isEmpty()) return 1.0

        val bucket = keys.firstOrNull { it >= valueToCompare } ?: keys.last()
        return factorsObject.optDouble(bucket.toString(), 1.0)
    }

    fun getGroupingDerating(
        installation: String,
        formation: String,
        spacing: String,
        noCircuits: Int
    ): Double? {
        if (noCircuits <= 0) return null

        val data = Catalog.deratingData ?: return null
        val factorsObject = data
            .optJSONObject("grouping")
            ?.optJSONObject("factors")
            ?: return null

        // Exact-match aliases first (D1/D2 -> Duct/Buried), then the existing substring rules.
        // The alias target is lowercased so it participates in the same case-sensitive substring
        // checks used by every other branch below.
        val normalizedInstallation =
            INSTALLATION_ALIASES[installation.trim().lowercase()]?.lowercase()
                ?: installation.trim().lowercase()
        val prefix = when {
            isDirectBuried(installation) || normalizedInstallation == "buried" -> "Buried"
            normalizedInstallation.contains("duct") -> "Duct"
            normalizedInstallation.contains("free air") ||
                normalizedInstallation.contains("tray") ||
                normalizedInstallation == "air" -> "Air"
            else -> return null
        }

        val normalizedFormation = formation.trim().lowercase()
        val form = when {
            normalizedFormation.contains("flat") -> "Flat"
            normalizedFormation.contains("trefoil") || normalizedFormation.isBlank() -> "Trefoil"
            else -> return null
        }

        val normalizedSpacing = spacing.trim().lowercase()
        val space = when {
            normalizedSpacing.contains("0.30") ||
                normalizedSpacing.contains("30 cm") -> "0.30m"
            normalizedSpacing.contains("0.15") ||
                normalizedSpacing.contains("15 cm") -> "0.15m"
            normalizedSpacing.contains("touching") || normalizedSpacing.isBlank() -> "Touching"
            else -> return null
        }

        val groupingKey = "${prefix}_${form}_${space}"
        val factorsArray = factorsObject.optJSONArray(groupingKey) ?: return null
        if (noCircuits == 1) return 1.0
        val index = noCircuits.coerceIn(1, 6) - 1
        val factor = factorsArray.optDouble(index, -1.0)
        return if (factor > 0.0) factor else null
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Exact-match aliases for IEC reference-method codes that map cleanly onto one of the three
     * ampacity installation families this catalog provides (Duct, Buried, Air).
     *
     * Matched case-insensitively against the TRIMMED whole input — never by substring, so a value
     * that merely contains "d1" is never silently reinterpreted as a reference-method code.
     */
    private val INSTALLATION_ALIASES: Map<String, String> = mapOf(
        "d1" to "Duct",    // cables in ducts in ground
        "d2" to "Buried"   // cables direct in ground
    )

    /**
     * Installation methods with NO valid mapping onto this catalog's families, matched exactly
     * (case-insensitively) against the trimmed input.
     *
     * Conduit, trunking and the wall-mounted / clipped / in-insulated-wall reference methods are
     * deliberately excluded rather than coerced: mapping them to Air would overstate ampacity, and
     * mapping them to Duct or Buried would apply ground-installation derating to a cable that is not
     * in the ground. Both are wrong, in opposite directions.
     */
    private val UNSUPPORTED_INSTALLATION_METHODS: Set<String> = setOf(
        "in conduit",
        "in conduit / trunking",
        "a1", "a2",
        "b1", "b2",
        "c",
        "in ground"
    )

    /**
     * Resolves the canonical installation method for [installation].
     *
     * An exact-match alias (case-insensitive, trimmed) is rewritten to its catalog family so all
     * downstream derating lookups agree; anything else is returned unchanged. This is deliberately
     * NOT a substring rule: a value that merely contains "d1" is never reinterpreted.
     */
    fun canonicalInstallation(installation: String): String =
        INSTALLATION_ALIASES[installation.trim().lowercase()] ?: installation

    /**
     * Resolves the single canonical installation family — `Buried`, `Duct` or `Air` — for
     * [installation], which is expected to be the output of [canonicalInstallation] (the call is
     * repeated here because it is idempotent, so the single alias table stays the only
     * normalization boundary).
     *
     * This is the one place an installation becomes an ampacity column selection. It mirrors the
     * family rules the derating lookups use, so the base rating and the derating can never read
     * different installation semantics — which is what let `D1`/`D2` reach the free-air column.
     *
     * Returns null for anything the catalog has no family for, so an unrecognised installation
     * yields no numeric ampacity instead of a silently substituted one.
     */
    private fun canonicalInstallationFamily(installation: String): String? {
        val canonical = canonicalInstallation(installation)
        val normalized = canonical.trim().lowercase()
        return when {
            isDirectBuried(canonical) || normalized == "buried" -> "Buried"
            normalized.contains("duct") -> "Duct"
            normalized.contains("free air") ||
                normalized.contains("tray") ||
                normalized == "air" -> "Air"
            else -> null
        }
    }

    /**
     * Returns the specific, user-actionable failure message for an installation method this catalog
     * cannot compute, or null when the method is supported (or is merely unknown and will fall
     * through to the generic grouping-derating failure).
     *
     * A blank value is reported separately: it means the source record carried no installation at
     * all (for example an imported file with the key absent) and must never be defaulted silently.
     */
    fun unsupportedInstallationMessage(installation: String): String? {
        val trimmed = installation.trim()
        if (trimmed.isEmpty()) return "Installation method not specified in imported file"
        if (trimmed.lowercase() in UNSUPPORTED_INSTALLATION_METHODS) {
            return "Installation method '$trimmed' is not supported by the current ampacity catalog " +
                "(supported: Duct, Buried, Air/Tray)."
        }
        return null
    }

    private fun isDirectBuried(installation: String): Boolean {
        val value = installation.trim().lowercase()
        return value.contains("direct") &&
            (value.contains("ground") || value.contains("buried"))
    }

    /**
     * Base ampacity for [entry] from the canonical family resolved by
     * [canonicalInstallationFamily] — never from the raw installation string.
     *
     * The families are explicit and exhaustive: `Buried` -> gnd, `Duct` -> duct, `Air` -> air.
     * There is deliberately no catch-all branch, so an unknown family yields no numeric ampacity
     * (null) rather than being reported as a free-air rating. `CableCatalogEntry` already stores
     * these as `Double`, so no conversion is needed.
     */
    private fun ampacityOf(entry: CableCatalogEntry, installationFamily: String?): Double? =
        when (installationFamily) {
            "Air" -> entry.air
            "Duct" -> entry.duct
            "Buried" -> entry.gnd
            else -> null
        }

    private fun rxValueOf(size: Double, rColumn: String): Pair<Double, Double>? {
        val sheet = Catalog.dataSheet.firstOrNull {
            kotlin.math.abs(it.size - size) <= EPSILON
        } ?: return null

        val resistance = when (rColumn) {
            "R_Cu20", "R_Cu70" -> sheet.R_Cu20
            "R_Al20", "R_Al70" -> sheet.R_Al20
            "R_Cu_70_est", "R_Cu90" -> sheet.R_Cu_70_est
            "R_Al_70_est", "R_Al90" -> sheet.R_Al_70_est
            else -> return null
        }

        return Pair(resistance, sheet.X)
    }

    private fun formatSize(size: Double, includeCopper: Boolean = false): String {
        val numeric = if (size % 1.0 == 0.0) {
            size.toInt().toString()
        } else {
            size.toString()
        }
        return if (includeCopper) "$numeric mm\u00B2 (Cu)" else "$numeric mm\u00B2"
    }

    private fun parseCableSize(value: String?): Double? {
        val match = Regex("""^\s*([0-9]+(?:\.[0-9]+)?)""")
            .find(value.orEmpty())

        return match
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
    }

    fun failedResult(
        reason: String,
        primaryFailureCode: CableFailureCode? = null,
        resultStatus: SizingResultStatus = when (primaryFailureCode) {
            CableFailureCode.INCOMPLETE_INPUT -> SizingResultStatus.INCOMPLETE
            null -> SizingResultStatus.FAIL
            else -> SizingResultStatus.FAIL
        }
    ): SizingResult = SizingResult(
        Ib_A = 0.0,
        deviceSize_A = 0.0,
        deviceType = "N/A",
        cTotalDerating = 0.0,
        iRequired_perCable_A = 0.0,
        productCode = "N/A",
        cableSize_mm2 = "N/A",
        ratedI_table_A = 0.0,
        capacity_derated_Iz_A = 0.0,
        utilizationPct = 0.0,
        neutral_mm2 = "N/A",
        earthCPC_mm2 = "N/A",
        voltDrop_V = 0.0,
        vdPct = 0.0,
        cumulativeVDpct = 0.0,
        status = "✗ FAIL",
        failReason = reason,
        resultStatus = resultStatus,
        isAmpacityOk = false,
        isVoltageDropOk = false,
        primaryFailureCode = primaryFailureCode
    )

    // ---------------------------------------------------------------------
    // Main sizing engine
    // ---------------------------------------------------------------------

    fun sizeCircuit(
        circuit: CircuitEntity,
        parentCumulativeVD: Double = 0.0,
        catalog: com.example.data.catalog.CatalogRepository
    ): SizingResult {
        return sizeCircuitInternal(circuit, parentCumulativeVD, catalog = catalog)
    }

    fun sizeCircuit(
        circuit: CircuitEntity,
        parentCumulativeVD: Double = 0.0,
        allCircuits: List<CircuitEntity> = emptyList()
    ): SizingResult {
        return sizeCircuitInternal(circuit, parentCumulativeVD, allCircuits = allCircuits)
    }

    private fun sizeCircuitInternal(
        circuit: CircuitEntity,
        parentCumulativeVD: Double = 0.0,
        allCircuits: List<CircuitEntity> = emptyList(),
        catalog: com.example.data.catalog.CatalogRepository? = null
    ): SizingResult {
        // Kept for API compatibility. Parent/child load aggregation is a
        // separate engine concern and is not silently applied here.
        @Suppress("UNUSED_VARIABLE")
        val contextCircuits = allCircuits

        val cat = catalog
        val isLegacy = cat == null
        if (isLegacy && (Catalog.cableCatalog.isEmpty() || Catalog.cableTypes.isEmpty() || Catalog.dataSheet.isEmpty())) {
            return failedResult("Catalog is not loaded.", CableFailureCode.CATALOG_MISSING)
        } else if (cat != null && (cat.cableProducts.isEmpty() || cat.cableTypesData.isEmpty() || cat.deratingRows.isEmpty())) {
            return failedResult("Catalog is not loaded.", CableFailureCode.CATALOG_MISSING)
        }

        if (circuit.voltageV <= 0) {
            return failedResult("Voltage must be greater than zero.", CableFailureCode.INCOMPLETE_INPUT)
        }
        if (circuit.loadKVA < 0.0) {
            return failedResult("Load kVA cannot be negative.", CableFailureCode.INCOMPLETE_INPUT)
        }
        if (circuit.parallelRuns <= 0) {
            return failedResult("Parallel runs must be at least 1.", CableFailureCode.INCOMPLETE_INPUT)
        }
        if (circuit.pf <= 0.0 || circuit.pf > 1.0) {
            return failedResult("Power factor must be greater than 0 and not more than 1.0.", CableFailureCode.INCOMPLETE_INPUT)
        }
        if (circuit.maxVDpct <= 0.0) {
            return failedResult("Maximum voltage-drop limit must be greater than zero.", CableFailureCode.INCOMPLETE_INPUT)
        }

        if (circuit.length_m <= 0.0) {
            return failedResult("Cable length must be greater than zero.", CableFailureCode.INCOMPLETE_INPUT)
        }

        val isThreePhase = circuit.phases.trim().uppercase().contains("3P")
        val ibA = if (isThreePhase) {
            circuit.loadKVA * 1000.0 / (sqrt(3.0) * circuit.voltageV)
        } else {
            circuit.loadKVA * 1000.0 / circuit.voltageV
        }

        val requiredRating = ibA * circuit.safetyFactor
        val initialDeviceSize = STANDARD_RATINGS.firstOrNull {
            it + EPSILON >= requiredRating
        } ?: STANDARD_RATINGS.last()

        val autoDeviceType = when {
            initialDeviceSize <= 125.0 -> "MCB"
            initialDeviceSize <= 1600.0 -> "MCCB"
            else -> "ACB"
        }

        val baseDeviceType = circuit.overrideDeviceType ?: autoDeviceType

        val autoDeviceSize = if (baseDeviceType.contains("MCCB", ignoreCase = true) && initialDeviceSize < 63.0) {
            63.0 // MCCBs start at 63A typically (IEC standard minimum for most frames)
        } else {
            initialDeviceSize
        }

        val deviceSizeA = circuit.overrideDeviceSize ?: autoDeviceSize
        val deviceType = baseDeviceType

        val isXLPE = circuit.cableType.contains("XLPE", ignoreCase = true)
        // Canonical installation method: exact-match aliases (D1 -> Duct, D2 -> Buried) resolve once
        // here so EVERY derating lookup below sees the same, resolvable method. Applying the alias
        // in only one derating helper would leave the others reading an unresolvable code.
        val installation = canonicalInstallation(circuit.installation)
        // One canonical family drives the ampacity column too, so the base rating can never be read
        // from a different installation interpretation than the derating computed from `installation`.
        val installationFamily = canonicalInstallationFamily(installation)
        val normalizedInstallation = installation.trim().lowercase()
        val relevantTemperature = if (
            normalizedInstallation.contains("free air") ||
            normalizedInstallation.contains("tray")
        ) {
            circuit.airTempC
        } else {
            circuit.gndTempC
        }

        // Installation methods this catalog cannot model must fail with a specific, actionable
        // message rather than the generic grouping failure — and a blank value must never be
        // silently defaulted, since it means the source record carried no installation at all.
        unsupportedInstallationMessage(circuit.installation)?.let { message ->
            return failedResult(message, CableFailureCode.DERATING_INVALID)
        }

        val cTemp = getTempDerating(
            installation,
            isXLPE,
            relevantTemperature
        )
        val cDepth = getDepthDerating(
            installation,
            circuit.burialDepth_m
        )
        val cSoil = getSoilDerating(
            installation,
            circuit.soilRes
        )
        val cGroup = getGroupingDerating(
            installation,
            circuit.formation,
            circuit.spacing,
            circuit.noCircuits
        ) ?: return failedResult(
            "Grouping derating factor not found for installation '${circuit.installation}', formation '${circuit.formation}', spacing '${circuit.spacing}'.",
            CableFailureCode.DERATING_INVALID
        )

        val cTotalDerating = cTemp * cDepth * cSoil * cGroup
        if (cTotalDerating <= 0.0) {
            return failedResult("Total derating factor is zero or invalid.", CableFailureCode.DERATING_INVALID)
        }

        // IEC 60364-5-52 Annex E — third-harmonic derating of the PHASE conductors.
        // For 15–33 % third-harmonic content the rating factor is 0.86 (the neutral counts
        // as the 4th current-carrying conductor), applied IN ADDITION to the full-size
        // neutral. The circuit flag is Boolean — no harmonic-% input exists — so the
        // 15–33 % band factor (0.86) is applied and explicitly flagged; for > 33 % content
        // the design must shift to the neutral-current basis (0.86 up to 45 %, then 1.0)
        // and must be verified manually (see warning emitted in the result).
        val harmonicPhaseFactor = if (circuit.isHarmonic && circuit.phases.trim().startsWith("3")) 0.86 else 1.0

        val iRequiredPerCableA =
            ibA / (circuit.parallelRuns * cTotalDerating)

        val isCu = circuit.cableType.contains("Cu", ignoreCase = true)
        val isAl = circuit.cableType.contains("Al", ignoreCase = true)
        val cableMaterial = if (isAl && !isCu) "Al" else "Cu"

        // IMPORTANT: no fallback to typeCode = 1.
        var selectedTypeCode = 0
        
        if (isLegacy) {
            val selectedType = Catalog.cableTypes.firstOrNull {
                it.cableType.trim().equals(circuit.cableType.trim(), ignoreCase = true)
            } ?: return failedResult("Cable type '${circuit.cableType}' was not found in cable_types.json.", CableFailureCode.CATALOG_MISSING)
            selectedTypeCode = selectedType.code
            
            val hasNoCatalogEntries = Catalog.cableCatalog.none { it.typeCode == selectedTypeCode }
            if (hasNoCatalogEntries) {
                return failedResult("Catalog data is not available for this cable family.", CableFailureCode.CATALOG_MISSING)
            }
        } else {
            val selectedTypeRepo = catalog?.cableTypesData?.firstOrNull {
                it.cableType.trim().equals(circuit.cableType.trim(), ignoreCase = true)
            }
            val selectedTypeLegacy = if (selectedTypeRepo == null) {
                Catalog.cableTypes.firstOrNull {
                    it.cableType.trim().equals(circuit.cableType.trim(), ignoreCase = true)
                }
            } else null

            if (selectedTypeRepo != null) {
                selectedTypeCode = selectedTypeRepo.typeCode
            } else if (selectedTypeLegacy != null) {
                selectedTypeCode = selectedTypeLegacy.code
            } else {
                return failedResult("Cable type '${circuit.cableType}' was not found in catalog.", CableFailureCode.CATALOG_MISSING)
            }
            
            val hasNoCatalogEntries = catalog?.cableProducts?.none { it.typeCode == selectedTypeCode } == true
            if (hasNoCatalogEntries) {
                return failedResult("Catalog data is not available for this cable family.", CableFailureCode.CATALOG_MISSING)
            }
        }

        val allManufacturerFamilyEntries = if (isLegacy) {
            Catalog.cableCatalog
                .filter {
                    it.mfg.trim().equals(circuit.manufacturer.trim(), ignoreCase = true) && it.typeCode == selectedTypeCode
                }.sortedBy { it.size }
        } else {
            catalog?.cableProducts
                ?.filter {
                    it.mfg.trim().equals(circuit.manufacturer.trim(), ignoreCase = true) && it.typeCode == selectedTypeCode
                }?.map { 
                    CableCatalogEntry(it.mfg, it.typeCode, it.size, it.model, it.gnd, it.duct, it.air, it.od, it.wt)
                }?.sortedBy { it.size } ?: emptyList()
        }

        val candidates = allManufacturerFamilyEntries
            // Pure IEC selection (smallest product with rated_I >= I_required), removing the arbitrary 16mm filter

        if (allManufacturerFamilyEntries.isEmpty()) {
            return failedResult(
                "Catalog data is not available for this cable family.",
                CableFailureCode.CATALOG_MISSING
            )
        }

        val cosPhi = circuit.pf
        val sinPhi = sqrt((1.0 - cosPhi * cosPhi).coerceAtLeast(0.0))
        val limitPct = circuit.maxVDpct * 100.0

        data class CandidateCheck(
            val entry: CableCatalogEntry,
            val ratedCurrentA: Double,
            val izDeratedA: Double,
            val voltageDropV: Double,
            val voltageDropPct: Double,
            val cumulativeVoltageDropPct: Double,
            val ampacityOk: Boolean,
            val voltageDropOk: Boolean,
            val resistance: Double,
            val reactance: Double,
            val r20: Double?
        )

        fun checkCandidate(entry: CableCatalogEntry): CandidateCheck? {
            // The upstream grouping-derating lookup already failed the circuit for any family that
            // does not resolve, so this null is defence in depth: reject the candidate rather than
            // size it against a substituted column.
            val ratedCurrentA = ampacityOf(entry, installationFamily) ?: return null
            val izDeratedA =
                ratedCurrentA * circuit.parallelRuns * cTotalDerating * harmonicPhaseFactor

            val rxValuesAndR20 = if (isLegacy) {
                val sheet = Catalog.dataSheet.firstOrNull {
                    kotlin.math.abs(it.size - entry.size) <= EPSILON
                } ?: return null

                val r20Raw = if (cableMaterial == "Al") sheet.R_Al20 else sheet.R_Cu20
                val r20 = if (r20Raw > 0.0) r20Raw else null
                if (r20 == null || sheet.X <= 0.0) return null
                Pair(r20, sheet.X)
            } else {
                val sheet = catalog?.cableTypesData?.firstOrNull {
                    it.typeCode == selectedTypeCode && kotlin.math.abs(it.sizeMm2 - entry.size) <= EPSILON
                } ?: catalog?.cableTypesData?.firstOrNull {
                    kotlin.math.abs(it.sizeMm2 - entry.size) <= EPSILON
                } ?: return null

                val r20Raw = if (cableMaterial == "Al") sheet.rAl20 else sheet.rCu20
                val r20 = if (r20Raw > 0.0) r20Raw else null
                if (r20 == null || sheet.x <= 0.0) return null
                Pair(r20, sheet.x)
            }
            val (candidateR20, reactance) = rxValuesAndR20

            // IEC 60364-5-52 Annex G / IEC 60228: Conductor resistance at maximum
            // operating temperature (70 °C for PVC, 90 °C for XLPE).
            val operatingTempFactor = when {
                cableMaterial == "Al" && isXLPE -> TEMP_FACTOR_AL_XLPE
                cableMaterial == "Al" -> TEMP_FACTOR_AL_PVC
                isXLPE -> TEMP_FACTOR_CU_XLPE
                else -> TEMP_FACTOR_CU_PVC
            }
            val rOperating = candidateR20 * operatingTempFactor

            val impedanceTerm =
                rOperating * cosPhi + reactance * sinPhi

            val voltageDropV = if (isThreePhase) {
                sqrt(3.0) * ibA * impedanceTerm * circuit.length_m /
                    (1000.0 * circuit.parallelRuns)
            } else {
                2.0 * ibA * impedanceTerm * circuit.length_m /
                    (1000.0 * circuit.parallelRuns)
            }

            val voltageDropPct =
                voltageDropV / circuit.voltageV * 100.0
            val cumulativeVoltageDropPct =
                parentCumulativeVD + voltageDropPct

            val ampacityOk =
                ibA <= deviceSizeA + EPSILON &&
                    deviceSizeA <= izDeratedA + EPSILON
            val voltageDropOk =
                cumulativeVoltageDropPct <= limitPct + EPSILON

            return CandidateCheck(
                entry = entry,
                ratedCurrentA = ratedCurrentA,
                izDeratedA = izDeratedA,
                voltageDropV = voltageDropV,
                voltageDropPct = voltageDropPct,
                cumulativeVoltageDropPct = cumulativeVoltageDropPct,
                ampacityOk = ampacityOk,
                voltageDropOk = voltageDropOk,
                resistance = rOperating,
                reactance = reactance,
                r20 = candidateR20
            )
        }

        val selectedCheck: CandidateCheck
        var failReason = ""

        val overrideText = circuit.overrideCableSize?.trim()
        if (!overrideText.isNullOrEmpty() &&
            !overrideText.equals("AUTO", ignoreCase = true)
        ) {
            val overrideSize = parseCableSize(overrideText)
                ?: return failedResult(
                    "Manual cable size '$overrideText' is invalid.",
                    CableFailureCode.INCOMPLETE_INPUT
                )

            val overrideEntry = allManufacturerFamilyEntries.firstOrNull {
                kotlin.math.abs(it.size - overrideSize) <= EPSILON
            } ?: return failedResult(
                "Manual cable size ${formatSize(overrideSize)} does not exist " +
                    "for the selected manufacturer and cable type.",
                CableFailureCode.CATALOG_MISSING
            )

            selectedCheck = checkCandidate(overrideEntry)
                ?: return failedResult(
                    "R/X data is missing for manual cable size " +
                        formatSize(overrideSize) + ".",
                    CableFailureCode.CATALOG_MISSING
                )

            failReason = buildFailureReason(
                selectedCheck.ampacityOk,
                selectedCheck.voltageDropOk,
                isManualOverride = true
            )
        } else {
            val checks = candidates.mapNotNull(::checkCandidate)
            if (checks.isEmpty()) {
                return failedResult(
                    "No cable candidate has valid R/X data in data_sheet.json.",
                    CableFailureCode.CATALOG_MISSING
                )
            }

            selectedCheck = checks.firstOrNull {
                it.ampacityOk && it.voltageDropOk
            } ?: checks.last()

            failReason = buildFailureReason(
                selectedCheck.ampacityOk,
                selectedCheck.voltageDropOk,
                isManualOverride = false
            )
        }

        val selectedEntry = selectedCheck.entry
        val cableSizeDouble = selectedEntry.size
        val cableSizeMm2 = formatSize(cableSizeDouble)
        val neutralSize = if (isThreePhase) {
            if (circuit.isHarmonic) {
                cableSizeDouble
            } else {
                getHalfSize(cableSizeDouble)
            }
        } else {
            cableSizeDouble
        }
        val cpcSize = getCPCSize(cableSizeDouble)

        val utilizationPct = if (selectedCheck.izDeratedA > 0.0) {
            ibA / selectedCheck.izDeratedA * 100.0
        } else {
            0.0
        }

        val isAmpacityPass = selectedCheck.ampacityOk
        val isVdPass = selectedCheck.voltageDropOk
        val isPass = isAmpacityPass && isVdPass

        val status = if (isPass) {
            "✓ PASS"
        } else {
            "✗ FAIL"
        }

        val resStatus = if (isPass) SizingResultStatus.PASS else SizingResultStatus.FAIL
        val failureCode = when {
            isPass -> null
            !isAmpacityPass && !isVdPass -> CableFailureCode.MULTIPLE_CONSTRAINTS
            !isAmpacityPass -> CableFailureCode.AMPACITY_FAIL
            !isVdPass -> if (parentCumulativeVD > 0.0) CableFailureCode.CUMULATIVE_VOLTAGE_DROP_FAIL else CableFailureCode.VOLTAGE_DROP_FAIL
            else -> CableFailureCode.INDETERMINATE
        }

        val insulation = if (isXLPE) "XLPE" else "PVC"
        val kFactor = when {
            cableMaterial == "Cu" && insulation == "XLPE" -> 143.0
            cableMaterial == "Al" && insulation == "XLPE" -> 94.0
            cableMaterial == "Cu" && insulation == "PVC" -> 115.0
            cableMaterial == "Al" && insulation == "PVC" -> 76.0
            else -> 143.0
        }

        val targetType = when {
            deviceType.equals("MCB", ignoreCase = true) -> "MCB"
            deviceType.equals("ACB", ignoreCase = true) -> "ACB"
            deviceType.equals("MCCB", ignoreCase = true) -> "MCCB"
            deviceSizeA <= 63.0 -> "MCB"
            deviceSizeA > 630.0 -> "ACB"
            else -> "MCCB"
        }

        val breaker = Catalog.breakers
            .filter { b -> b.rating_A >= deviceSizeA && (b.type.equals(targetType, ignoreCase = true) || targetType.isEmpty()) }
            .minByOrNull { it.rating_A }
            ?: Catalog.breakers
                .filter { b -> b.rating_A >= deviceSizeA }
                .minByOrNull { it.rating_A }

        val isDeviceFromCatalog = breaker != null
        val breakerOrderingCode = breaker?.orderingCode
        val resolvedDeviceType = breaker?.type?.ifEmpty { targetType } ?: targetType
        val deviceBreakingKA = breaker?.icu_kA ?: when (resolvedDeviceType) {
            "MCB" -> 10.0
            "ACB" -> 65.0
            else -> 36.0
        }
        val clearingTime_s = when (resolvedDeviceType) {
            "MCB" -> 0.01
            "MCCB" -> 0.1
            "ACB" -> 0.3
            else -> 0.1
        }
        val allCatalogProducts = if (isLegacy) {
            Catalog.cableCatalog.map { com.example.data.catalog.CableProduct(it.mfg, it.typeCode, it.size, it.model, it.gnd, it.duct, it.air, it.od, it.wt) }
        } else {
            catalog?.cableProducts ?: emptyList()
        }

        val dimInfo = resolveCableDimensions(
            mfg = selectedEntry.mfg,
            typeCode = selectedEntry.typeCode,
            size = selectedEntry.size,
            rawOD = selectedEntry.od,
            rawWT = selectedEntry.wt,
            catalogProducts = allCatalogProducts
        )

        val cableOD_mm = dimInfo.od_mm

        val rawEarthOD = if (isLegacy) {
            Catalog.cableCatalog.firstOrNull {
                it.size == cpcSize && it.typeCode == selectedTypeCode
            }?.od ?: 0.0
        } else {
            catalog?.cableProducts?.firstOrNull {
                it.size == cpcSize && it.typeCode == selectedTypeCode
            }?.od ?: 0.0
        }

        val earthOD_mm = if (rawEarthOD > 0.0) {
            rawEarthOD
        } else {
            resolveCableDimensions(
                mfg = selectedEntry.mfg,
                typeCode = selectedTypeCode,
                size = cpcSize,
                rawOD = rawEarthOD,
                rawWT = 0.0,
                catalogProducts = allCatalogProducts
            ).od_mm
        }

        return SizingResult(
            Ib_A = round(ibA, 2),
            deviceSize_A = deviceSizeA,
            deviceType = deviceType,
            cTotalDerating = round(cTotalDerating, 4),
            iRequired_perCable_A = round(iRequiredPerCableA, 2),
            productCode = selectedEntry.model,
            cableSize_mm2 = cableSizeMm2,
            ratedI_table_A = selectedCheck.ratedCurrentA,
            capacity_derated_Iz_A = round(selectedCheck.izDeratedA, 1),
            utilizationPct = round(utilizationPct, 2),
            neutral_mm2 = formatSize(neutralSize),
            earthCPC_mm2 = formatSize(cpcSize),
            voltDrop_V = round(selectedCheck.voltageDropV, 3),
            vdPct = round(selectedCheck.voltageDropPct, 4),
            cumulativeVDpct = round(
                selectedCheck.cumulativeVoltageDropPct,
                4
            ),
            status = status,
            failReason = failReason,
            cableMaterial = cableMaterial,
            insulation = insulation,
            kFactor = kFactor,
            deviceBreakingKA = deviceBreakingKA,
            deviceMakingKA = breaker?.icm_kA ?: 0.0,
            cableOD_mm = cableOD_mm,
            earthOD_mm = earthOD_mm,
            rUsed = selectedCheck.resistance,
            xUsed = selectedCheck.reactance,
            r20 = selectedCheck.r20,
            clearingTime_s = clearingTime_s,
            odSourceMfg = dimInfo.sourceMfg,
            isODApproximated = dimInfo.isApproximated,
            warnings = if (circuit.isHarmonic) {
                listOf(
                    "Harmonic circuit (3rd-harmonic content assumed in the 15–33 % band): phase-conductor ampacity derated ×0.86 per IEC 60364-5-52 Annex E, full-size neutral applied. If content exceeds 33 %, sizing must shift to the neutral-current basis — verify."
                )
            } else emptyList(),
            resultStatus = resStatus,
            isAmpacityOk = isAmpacityPass,
            isVoltageDropOk = isVdPass,
            primaryFailureCode = failureCode,
            isDeviceFromCatalog = isDeviceFromCatalog,
            breakerOrderingCode = breakerOrderingCode,
            conductorAreaMm2 = selectedEntry.size,
            cpcAreaMm2 = cpcSize
        )
    }

    private fun buildFailureReason(
        ampacityOk: Boolean,
        voltageDropOk: Boolean,
        isManualOverride: Boolean
    ): String {
        if (ampacityOk && voltageDropOk) return ""

        val reasons = mutableListOf<String>()
        if (!ampacityOk) {
            reasons += "Ampacity constraint Ib ≤ In ≤ Iz is not satisfied"
        }
        if (!voltageDropOk) {
            reasons += "Cumulative voltage drop exceeds the allowed limit"
        }

        val prefix = if (isManualOverride) {
            "Manual cable override failed validation: "
        } else {
            "No catalog size satisfies all constraints; largest checked size: "
        }

        return prefix + reasons.joinToString("; ") + "."
    }

    private fun numberKey(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString()
        else value.toString()

    private fun round(value: Double, decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10.0 }
        return kotlin.math.round(value * multiplier) / multiplier
    }

    private const val EPSILON = 1e-9

    fun resolveCableDimensions(
        mfg: String,
        typeCode: Int,
        size: Double,
        rawOD: Double,
        rawWT: Double,
        catalogProducts: List<com.example.data.catalog.CableProduct>
    ): CableDimensionInfo {
        val hasOD = rawOD > 0.0
        val hasWT = rawWT > 0.0

        if (hasOD && hasWT) {
            return CableDimensionInfo(
                od_mm = rawOD,
                wt_kg_km = rawWT,
                sourceMfg = mfg,
                isApproximated = false
            )
        }

        val candidates = catalogProducts.filter {
            it.typeCode == typeCode && kotlin.math.abs(it.size - size) < 0.001 && (it.od > 0.0 || it.wt > 0.0)
        }

        val match = candidates.firstOrNull { it.mfg.equals("Elsewedy", ignoreCase = true) }
            ?: candidates.firstOrNull()

        val fallbackOD = if (hasOD) rawOD else (match?.od ?: estimateSensibleOD(size, typeCode))
        val fallbackWT = if (hasWT) rawWT else (match?.wt ?: estimateSensibleWT(size, typeCode))
        val sourceMfg = if (hasOD) mfg else (match?.mfg ?: "Standard Est.")

        return CableDimensionInfo(
            od_mm = fallbackOD,
            wt_kg_km = fallbackWT,
            sourceMfg = sourceMfg,
            isApproximated = !hasOD || !hasWT
        )
    }

    fun estimateSensibleOD(size: Double, typeCode: Int): Double {
        val sqrtS = sqrt(size.coerceAtLeast(1.5))
        return when {
            typeCode in listOf(11, 12, 13, 14, 90) -> round(1.8 * sqrtS + 6.0, 1)
            typeCode in listOf(1, 3, 5, 7, 9, 10, 66, 68, 70, 72, 92) -> round(3.0 * sqrtS + 9.0, 1)
            else -> round(3.2 * sqrtS + 10.0, 1)
        }
    }

    fun estimateSensibleWT(size: Double, typeCode: Int): Double {
        val factor = when {
            typeCode in listOf(11, 12, 13, 14, 90) -> 12.0
            typeCode in listOf(1, 3, 5, 7, 9, 10, 66, 68, 70, 72, 92) -> 32.0
            else -> 42.0
        }
        return round(factor * size + 150.0, 1)
    }
}

data class CableDimensionInfo(
    val od_mm: Double,
    val wt_kg_km: Double,
    val sourceMfg: String,
    val isApproximated: Boolean
)
