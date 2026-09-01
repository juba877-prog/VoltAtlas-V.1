package com.example.data

import android.content.Context
import org.json.JSONArray
import java.io.InputStream

data class CableType(
    val code: Int,
    val cableType: String,
    val conductor: String,
    val insulation: String,
    val armour: String,
    val cores: String,
    val voltage: String,
    val standard: String,
    val rColumn: String
)

data class DataSheetEntry(
    val size: Double,
    val R_Cu20: Double,
    val X: Double,
    val R_Al20: Double,
    val R_Cu_70_est: Double,
    val R_Al_70_est: Double,
    val provenance: String = "IEC_60228_TABLE_2"
) {
    // Backward-compatibility property aliases
    val R_Cu70: Double get() = R_Cu20
    val R_Al70: Double get() = R_Al20
    val R_Cu90: Double get() = R_Cu_70_est
    val R_Al90: Double get() = R_Al_70_est

    // Secondary constructor supporting legacy positional/named argument order
    constructor(
        size: Double,
        R_Cu70: Double,
        X: Double,
        R_Al70: Double,
        R_Cu90: Double,
        R_Al90: Double
    ) : this(
        size = size,
        R_Cu20 = R_Cu70,
        X = X,
        R_Al20 = R_Al70,
        R_Cu_70_est = R_Cu90,
        R_Al_70_est = R_Al90,
        provenance = "IEC_60228_TABLE_2"
    )
}

data class CableCatalogEntry(
    val mfg: String,
    val typeCode: Int,
    val size: Double,
    val model: String,
    val gnd: Double,
    val duct: Double,
    val air: Double,
    val od: Double,
    val wt: Double,
    val provenance: String = "ELSEWEDY_TECHNICAL_CATALOG_IEC_60502"
)

data class BreakerEntry(
    val mfg: String,
    val type: String = "MCCB",
    val series: String,
    val frame: String,
    val version: String,
    val rating_A: Double,
    val poles: Int,
    val icu_kA: Double,
    val ics_kA: Double,
    val icm_kA: Double,
    val tripUnit: String
) {
    val ratingA: Double get() = rating_A
    val icuKa: Double get() = icu_kA
}

data class AccessoryEntry(
    val mfg: String,
    val category: String,
    val subCategory: String,
    val partNumber: String,
    val desc: String,
    val sizeMin: Double,
    val sizeMax: Double,
    val odMin: Double,
    val odMax: Double,
    val cores: String = "Any",
    val conductor: String? = null,
    val armourType: String? = null,
    val threadSize: String? = null,
    val boltSize: String? = null
)

data class ConduitCatalogEntry(
    val id: String,
    val conduitType: String,
    val tradeSize: String,
    val internalDiameterMm: Double,
    val areaTotalMm2: Double = Math.PI * internalDiameterMm * internalDiameterMm / 4.0,
    val maxFill40PctMm2: Double = areaTotalMm2 * 0.40,
    val provenance: String = "EXCEL_REFERENCE",
    val status: String = "APPROVED"
) {
    val internalAreaMm2: Double get() = areaTotalMm2
}

data class CatalogDiagnostics(
    val isCableCatalogLoaded: Boolean,
    val isBreakerCatalogLoaded: Boolean,
    val isAccessoryCatalogLoaded: Boolean,
    val isDataSheetLoaded: Boolean,
    val isDeratingDataLoaded: Boolean,
    val isConduitCatalogLoaded: Boolean = false,
    val lastErrorMessage: String?,
    val status: String,
    val definedFamilies: Int,
    val supportedFamilies: Int,
    val unsupportedFamilies: Int,
    val cableRecords: Int,
    val missingRx: Int,
    val duplicateKeys: Int,
    val orphanTypeCodes: Int
)

object Catalog {
    var cableTypes: List<CableType> = emptyList()
    var dataSheet: List<DataSheetEntry> = emptyList()
    var cableCatalog: List<CableCatalogEntry> = emptyList()
    var breakers: List<BreakerEntry> = emptyList()
    var accessories: List<AccessoryEntry> = emptyList()
    var conduits: List<ConduitCatalogEntry> = emptyList()
    var busways: List<com.example.data.catalog.BuswayCatalogEntry> = emptyList()
    var deratingData: org.json.JSONObject? = null

    @Volatile
    private var isLoaded = false

    val isCatalogLoaded: Boolean
        get() = isLoaded

    @Volatile
    private var cachedDiagnostics: CatalogDiagnostics? = null

    @Volatile
    private var _supportedTypeCodes: Set<Int>? = null

    val supportedTypeCodes: Set<Int>
        get() {
            _supportedTypeCodes?.let { return it }
            val set = cableCatalog.map { it.typeCode }.toSet()
            if (isLoaded) {
                _supportedTypeCodes = set
            }
            return set
        }

    @Volatile
    private var _cableTypesByName: Map<String, CableType>? = null

    val cableTypesByName: Map<String, CableType>
        get() {
            _cableTypesByName?.let { return it }
            val map = cableTypes.associateBy { it.cableType.trim().lowercase() }
            if (isLoaded) {
                _cableTypesByName = map
            }
            return map
        }

    @Volatile
    var lastError: Throwable? = null

    fun invalidateIndexes() {
        cachedDiagnostics = null
        _supportedTypeCodes = null
        _cableTypesByName = null
    }

    fun invalidateDiagnostics() {
        invalidateIndexes()
    }

    fun findCableType(name: String?): CableType? {
        if (name.isNullOrBlank()) return null
        val normalized = name.trim().lowercase()
        return cableTypesByName[normalized]
            ?: cableTypes.firstOrNull { it.cableType.trim().equals(name.trim(), ignoreCase = true) }
    }

    fun isTypeCodeSupported(typeCode: Int): Boolean {
        return supportedTypeCodes.contains(typeCode)
    }

    fun isCableTypeUnsupported(cableTypeName: String?): Boolean {
        val type = findCableType(cableTypeName) ?: return false
        return !isTypeCodeSupported(type.code)
    }

    fun getDiagnostics(): CatalogDiagnostics {
        cachedDiagnostics?.let { return it }

        val defined = cableTypes.size
        val typeCodesInCatalog = cableCatalog.map { it.typeCode }.toSet()
        val supported = cableTypes.count { it.code in typeCodesInCatalog }
        val unsupported = defined - supported
        val records = cableCatalog.size
        
        val datasheetSizes = dataSheet.map { it.size }.toSet()
        val missingRxVal = cableCatalog.count { it.size !in datasheetSizes }
        
        val modelsList = cableCatalog.map { it.model }
        val duplicateKeysVal = modelsList.size - modelsList.distinct().size
        
        val definedTypeCodes = cableTypes.map { it.code }.toSet()
        val orphanTypeCodesVal = cableCatalog.count { it.typeCode !in definedTypeCodes }
        
        val stat = when {
            lastError != null || cableTypes.isEmpty() || dataSheet.isEmpty() || cableCatalog.isEmpty() || breakers.isEmpty() || accessories.isEmpty() -> "INVALID"
            unsupported > 0 -> "PARTIAL"
            else -> "COMPLETE"
        }
        
        val result = CatalogDiagnostics(
            isCableCatalogLoaded = cableCatalog.isNotEmpty(),
            isBreakerCatalogLoaded = breakers.isNotEmpty(),
            isAccessoryCatalogLoaded = accessories.isNotEmpty(),
            isDataSheetLoaded = dataSheet.isNotEmpty(),
            isDeratingDataLoaded = deratingData != null,
            isConduitCatalogLoaded = conduits.isNotEmpty(),
            lastErrorMessage = lastError?.localizedMessage ?: lastError?.message,
            status = stat,
            definedFamilies = defined,
            supportedFamilies = supported,
            unsupportedFamilies = unsupported,
            cableRecords = records,
            missingRx = missingRxVal,
            duplicateKeys = duplicateKeysVal,
            orphanTypeCodes = orphanTypeCodesVal
        )
        if (isLoaded || lastError != null) {
            cachedDiagnostics = result
        }
        return result
    }

    fun readAsset(context: Context, filename: String): String {
        try {
            return context.assets.open(filename).use { stream ->
                val size = stream.available()
                val buffer = ByteArray(size)
                val readBytes = stream.read(buffer)
                if (readBytes <= 0 && size > 0) {
                    throw java.io.IOException("File is empty or could not be read: $filename")
                }
                String(buffer, 0, readBytes.coerceAtLeast(0), Charsets.UTF_8)
            }
        } catch (e: Exception) {
            throw java.io.IOException("Failed to read asset file '$filename': ${e.message}", e)
        }
    }

    @Synchronized
    fun loadAll(context: Context) {
        if (isLoaded) return
        try {
            lastError = null

            val deratingString = readAsset(context, "derating_data.json")
            val parsedDerating = org.json.JSONObject(deratingString)

            val parsedCableTypes = loadJson(context, "cable_types.json") { obj ->
                CableType(
                    code = obj.getInt("code"),
                    cableType = obj.getString("cableType"),
                    conductor = obj.getString("conductor"),
                    insulation = obj.getString("insulation"),
                    armour = obj.getString("armour"),
                    cores = obj.getString("cores"),
                    voltage = obj.getString("voltage"),
                    standard = obj.getString("standard"),
                    rColumn = obj.getString("rColumn")
                )
            }

            val parsedDataSheet = loadJson(context, "data_sheet.json") { obj ->
                DataSheetEntry(
                    size = obj.getDouble("size"),
                    R_Cu20 = if (obj.has("R_Cu20")) obj.getDouble("R_Cu20") else obj.getDouble("R_Cu70"),
                    X = obj.getDouble("X"),
                    R_Al20 = if (obj.has("R_Al20")) obj.getDouble("R_Al20") else obj.getDouble("R_Al70"),
                    R_Cu_70_est = if (obj.has("R_Cu_70_est")) obj.getDouble("R_Cu_70_est") else obj.getDouble("R_Cu90"),
                    R_Al_70_est = if (obj.has("R_Al_70_est")) obj.getDouble("R_Al_70_est") else obj.getDouble("R_Al90"),
                    provenance = obj.optString("provenance", "IEC_60228_TABLE_2")
                )
            }

            val parsedCableCatalog = loadJson(context, "cable_catalog.json") { obj ->
                CableCatalogEntry(
                    mfg = obj.getString("mfg"),
                    typeCode = obj.getInt("typeCode"),
                    size = obj.getDouble("size"),
                    model = obj.optString("model", "IEC-Generic-${obj.optDouble("size")}"),
                    gnd = obj.getDouble("gnd"),
                    duct = obj.getDouble("duct"),
                    air = obj.getDouble("air"),
                    od = obj.optDouble("od", 0.0),
                    wt = obj.optDouble("wt", 0.0),
                    provenance = obj.optString("provenance", "ELSEWEDY_TECHNICAL_CATALOG_IEC_60502")
                )
            }

            val parsedBreakers = loadJson(context, "breaker_catalog.json") { obj ->
                BreakerEntry(
                    mfg = obj.optString("mfg", obj.optString("Source", "Generic")),
                    type = obj.optString("type", "MCCB"),
                    series = obj.optString("series", obj.optString("Series", "")),
                    frame = if (obj.has("frameA")) obj.optString("frameA", "") else obj.optString("frame", obj.optString("Frame", "")),
                    version = obj.optString("version", obj.optString("Version", "")),
                    rating_A = com.example.data.catalog.BreakerParser.parseRating(obj.opt("ratingA") ?: obj.opt("rating_A") ?: obj.opt("In (A)")),
                    poles = com.example.data.catalog.BreakerParser.parsePoles(obj.opt("poles") ?: obj.opt("Poles")),
                    icu_kA = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("icuKa") ?: obj.opt("icu_kA") ?: obj.opt("Icu@415V (kA)")),
                    ics_kA = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("icsKa") ?: obj.opt("ics_kA") ?: obj.opt("Ics@415V (kA)")),
                    icm_kA = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("icmKa") ?: obj.opt("icm_kA") ?: obj.opt("Icm@415V (kA)")),
                    tripUnit = obj.optString("tripUnit", obj.optString("Trip Unit", "Thermal-Magnetic"))
                )
            }

            val parsedAccessories = loadJson(context, "accessory_catalog.json") { obj ->
                AccessoryEntry(
                    mfg = obj.optString("mfg", obj.optString("Manufacturer", "Generic")),
                    category = obj.optString("category", obj.optString("Category", "")),
                    subCategory = obj.optString("subCategory", obj.optString("Sub-Category", "")),
                    partNumber = obj.optString("partNumber", obj.optString("Part Number", "")),
                    desc = obj.optString("desc", obj.optString("Description", "")),
                    sizeMin = obj.optDouble("sizeMin", obj.optDouble("Size Min (mm²)", 0.0)),
                    sizeMax = obj.optDouble("sizeMax", obj.optDouble("Size Max (mm²)", 0.0)),
                    odMin = obj.optDouble("odMin", obj.optDouble("OD Min (mm)", 0.0)),
                    odMax = obj.optDouble("odMax", obj.optDouble("OD Max (mm)", 0.0)),
                    cores = obj.optString("cores", obj.optString("Cores", "Any")),
                    conductor = if (obj.has("conductor") && !obj.isNull("conductor")) obj.optString("conductor") else null,
                    armourType = if (obj.has("armourType") && !obj.isNull("armourType")) obj.optString("armourType") else null,
                    threadSize = if (obj.has("threadSize") && !obj.isNull("threadSize")) obj.optString("threadSize") else null,
                    boltSize = if (obj.has("boltSize") && !obj.isNull("boltSize")) obj.optString("boltSize") else null
                )
            }

            val parsedConduits = loadJson(context, "conduit_catalog.json") { obj ->
                val d = obj.getDouble("internalDiameterMm")
                val computedArea = Math.PI * d * d / 4.0
                val totalArea = obj.optDouble("areaTotalMm2", computedArea)
                val fill40 = obj.optDouble("maxFill40PctMm2", totalArea * 0.40)
                ConduitCatalogEntry(
                    id = obj.getString("id"),
                    conduitType = obj.optString("conduitType", "Rigid PVC"),
                    tradeSize = obj.getString("tradeSize"),
                    internalDiameterMm = d,
                    areaTotalMm2 = totalArea,
                    maxFill40PctMm2 = fill40,
                    provenance = obj.optString("provenance", "EXCEL_REFERENCE"),
                    status = obj.optString("status", "APPROVED")
                )
            }

            val parsedBusways = try {
                loadJson(context, "busway_catalog.json") { obj ->
                    com.example.data.catalog.BuswayCatalogEntry(
                        mfg = obj.optString("mfg", "Elsewedy"),
                        model = obj.optString("model", "Power Link"),
                        conductor = obj.optString("conductor", "Cu"),
                        ratingA = obj.optDouble("ratingA", 0.0),
                        ipkKA = obj.optDouble("ipkKA", 0.0),
                        icwKA = obj.optDouble("icwKA", 0.0),
                        rMOhmPerM = obj.optDouble("rMOhmPerM", 0.0),
                        xMOhmPerM = obj.optDouble("xMOhmPerM", 0.0),
                        weightKgPerM = obj.optDouble("weightKgPerM", 0.0),
                        ipRating = obj.optString("ipRating", "IP55")
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }

            if (parsedCableTypes.isEmpty()) {
                throw IllegalStateException("cable_types.json loaded but contains zero records.")
            }
            if (parsedDataSheet.isEmpty()) {
                throw IllegalStateException("data_sheet.json loaded but contains zero records.")
            }
            if (parsedCableCatalog.isEmpty()) {
                throw IllegalStateException("cable_catalog.json loaded but contains zero records.")
            }
            if (parsedBreakers.isEmpty()) {
                throw IllegalStateException("breaker_catalog.json loaded but contains zero records.")
            }
            if (parsedAccessories.isEmpty()) {
                throw IllegalStateException("accessory_catalog.json loaded but contains zero records.")
            }

            // Atomic publication of parsed catalog collections
            deratingData = parsedDerating
            cableTypes = parsedCableTypes
            dataSheet = parsedDataSheet
            cableCatalog = parsedCableCatalog
            breakers = parsedBreakers
            accessories = parsedAccessories
            conduits = parsedConduits
            busways = parsedBusways

            _supportedTypeCodes = parsedCableCatalog.map { it.typeCode }.toSet()
            _cableTypesByName = parsedCableTypes.associateBy { it.cableType.trim().lowercase() }
            cachedDiagnostics = null
            isLoaded = true
        } catch (e: Exception) {
            lastError = e
            isLoaded = false
            invalidateIndexes()
            throw RuntimeException("Catalog initialization failed: ${e.message}", e)
        }
    }

    @Synchronized
    fun resetForTesting() {
        cableTypes = emptyList()
        dataSheet = emptyList()
        cableCatalog = emptyList()
        breakers = emptyList()
        accessories = emptyList()
        conduits = emptyList()
        busways = emptyList()
        deratingData = null
        isLoaded = false
        lastError = null
        invalidateIndexes()
    }

    private fun <T> loadJson(context: Context, filename: String, mapper: (org.json.JSONObject) -> T): List<T> {
        val jsonString = readAsset(context, filename)
        val list = mutableListOf<T>()
        val array = JSONArray(jsonString)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            try {
                list.add(mapper(obj))
            } catch (e: Exception) {
                throw IllegalStateException("Failed to parse record at index $i in asset '$filename': ${e.message}", e)
            }
        }
        return list
    }
}

fun resolveCableType(
    rawCableType: String,
    defaultCore: String = "4C",
    defaultCond: String = "Cu",
    defaultIns: String = "XLPE"
): String {
    val trimmed = rawCableType.trim()
    if (trimmed.isNotBlank()) {
        val exactMatch = Catalog.cableTypes.firstOrNull { it.cableType.trim().equals(trimmed, ignoreCase = true) }
        if (exactMatch != null) return exactMatch.cableType

        val isAl = trimmed.contains("Al", ignoreCase = true)
        val condTarget = if (isAl) "Al" else "Cu"
        val isXLPE = trimmed.contains("XLPE", ignoreCase = true)
        val insTarget = if (isXLPE) "XLPE" else "PVC"

        val detectedCore = when {
            trimmed.contains("1C", ignoreCase = true) || trimmed.contains("Single", ignoreCase = true) -> "1C"
            trimmed.contains("2C", ignoreCase = true) -> "2C"
            trimmed.contains("3.5C", ignoreCase = true) -> "3.5C"
            trimmed.contains("3C", ignoreCase = true) -> "3C"
            trimmed.contains("4C", ignoreCase = true) -> "4C"
            else -> defaultCore.ifBlank { "4C" }
        }

        val matchedType = Catalog.cableTypes.firstOrNull { type ->
            val cMatch = type.conductor.contains(condTarget, ignoreCase = true) || type.cableType.contains(condTarget, ignoreCase = true)
            val iMatch = type.insulation.contains(insTarget, ignoreCase = true) || type.cableType.contains(insTarget, ignoreCase = true)
            val coreMatch = type.cores.equals(detectedCore, ignoreCase = true) || type.cableType.contains(detectedCore, ignoreCase = true)
            cMatch && iMatch && coreMatch
        }
        if (matchedType != null) return matchedType.cableType

        val matchedCondCore = Catalog.cableTypes.firstOrNull { type ->
            (type.conductor.contains(condTarget, ignoreCase = true) || type.cableType.contains(condTarget, ignoreCase = true)) &&
            (type.cores.equals(detectedCore, ignoreCase = true) || type.cableType.contains(detectedCore, ignoreCase = true))
        }
        if (matchedCondCore != null) return matchedCondCore.cableType
    }

    return Catalog.cableTypes.firstOrNull { it.cores.equals(defaultCore, ignoreCase = true) }?.cableType
        ?: Catalog.cableTypes.firstOrNull()?.cableType
        ?: "${if (defaultCond.equals("Al", true)) "Al" else "Cu"}/${if (defaultIns.equals("PVC", true)) "PVC" else "XLPE"}/SWA/PVC - $defaultCore"
}

data class LoadCategoryOption(
    val code: String,
    val label: String,
    val defaultPf: Double,
    val defaultDf: Double,
    val description: String
)

object LoadCategories {
    val OPTIONS = listOf(
        LoadCategoryOption("LTG", "LTG — Lighting", defaultPf = 0.92, defaultDf = 0.90, description = "Lighting circuits (typically PF 0.90-0.95, DF 0.90-1.0)"),
        LoadCategoryOption("SO", "SO — Socket Outlets", defaultPf = 0.85, defaultDf = 0.60, description = "Small power / socket outlets (typically PF 0.85, DF 0.50-0.70)"),
        LoadCategoryOption("MTR", "MTR — Motor", defaultPf = 0.85, defaultDf = 0.80, description = "Motor loads (typically PF 0.80-0.85, DF 0.75-1.0)"),
        LoadCategoryOption("HVAC", "HVAC — Heating/Vent/AC", defaultPf = 0.85, defaultDf = 0.90, description = "HVAC equipment (typically PF 0.85, DF 0.80-1.0)"),
        LoadCategoryOption("PWR", "PWR — General Power", defaultPf = 0.85, defaultDf = 0.80, description = "General power circuits (typically PF 0.85, DF 0.70-0.80)"),
        LoadCategoryOption("MISC", "MISC — Miscellaneous", defaultPf = 0.85, defaultDf = 1.00, description = "Miscellaneous loads (typically PF 0.85, DF 1.0)"),
        LoadCategoryOption("SPARE", "SPARE — Spare Circuit", defaultPf = 0.85, defaultDf = 0.00, description = "Spare breaker/circuit (no connected load)"),
        LoadCategoryOption("SPACE", "SPACE — Space Only", defaultPf = 0.85, defaultDf = 0.00, description = "Future space allocation (no connected load)")
    )

    fun findByCode(code: String?): LoadCategoryOption? {
        if (code.isNullOrBlank()) return null
        return OPTIONS.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }
    }
}

data class LoadTypeBreakdownRow(
    val categoryCode: String,
    val categoryLabel: String,
    val circuitCount: Int,
    val connectedKva: Double,
    val demandKva: Double,
    val averagePf: Double,
    val effectiveDemandFactor: Double
)

data class LoadTypeBreakdown(
    val rows: List<LoadTypeBreakdownRow>,
    val totalConnectedKva: Double,
    val totalDemandKva: Double,
    val overallDemandFactor: Double
)

fun calculateLoadTypeBreakdown(circuits: List<CircuitEntity>): LoadTypeBreakdown {
    val activeCircuits = circuits.filter { it.active }
    val grouped = activeCircuits.groupBy { it.loadCategory?.trim()?.uppercase()?.takeIf { cat -> cat.isNotEmpty() } }

    val rows = mutableListOf<LoadTypeBreakdownRow>()
    var sumConnectedKva = 0.0
    var sumDemandKva = 0.0

    val knownCodes = LoadCategories.OPTIONS.map { it.code }
    val presentKnownCodes = knownCodes.filter { grouped.containsKey(it) }
    val presentUnknownCodes = grouped.keys.filterNotNull() - knownCodes.toSet()
    val presentCodesInOrder = presentKnownCodes + presentUnknownCodes
    val categoriesInOrder = if (grouped.containsKey(null)) presentCodesInOrder + listOf(null) else presentCodesInOrder

    for (catCode in categoriesInOrder) {
        val list = grouped[catCode] ?: continue
        if (list.isEmpty()) continue

        val codeLabel = if (catCode == null) {
            "Uncategorized"
        } else {
            LoadCategories.findByCode(catCode)?.label ?: catCode
        }

        val count = list.size
        val connectedKva = list.sumOf { it.loadKVA }
        val demandKva = list.sumOf { it.loadKVA * it.demandFactor }

        val weightedPfSum = list.sumOf { it.loadKVA * it.pf }
        val avgPf = if (connectedKva > 0) weightedPfSum / connectedKva else if (list.isNotEmpty()) list.map { it.pf }.average() else 0.85
        val effDf = if (connectedKva > 0) demandKva / connectedKva else if (list.isNotEmpty()) list.map { it.demandFactor }.average() else 1.0

        rows.add(
            LoadTypeBreakdownRow(
                categoryCode = catCode ?: "UNCATEGORIZED",
                categoryLabel = codeLabel,
                circuitCount = count,
                connectedKva = Math.round(connectedKva * 100.0) / 100.0,
                demandKva = Math.round(demandKva * 100.0) / 100.0,
                averagePf = Math.round(avgPf * 100.0) / 100.0,
                effectiveDemandFactor = Math.round(effDf * 1000.0) / 1000.0
            )
        )

        sumConnectedKva += connectedKva
        sumDemandKva += demandKva
    }

    val overallDf = if (sumConnectedKva > 0) sumDemandKva / sumConnectedKva else 1.0

    return LoadTypeBreakdown(
        rows = rows,
        totalConnectedKva = Math.round(sumConnectedKva * 100.0) / 100.0,
        totalDemandKva = Math.round(sumDemandKva * 100.0) / 100.0,
        overallDemandFactor = Math.round(overallDf * 1000.0) / 1000.0
    )
}
