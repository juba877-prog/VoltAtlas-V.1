package com.example.data.catalog

import org.json.JSONArray
import org.json.JSONObject

class CatalogRepository {

    var cableProducts: List<CableProduct> = emptyList()
        private set
    var cableTypesData: List<CableTypeData> = emptyList()
        private set
    var breakers: List<Breaker> = emptyList()
        private set
    var accessories: List<Accessory> = emptyList()
        private set
    var deratingRows: List<DeratingTableRow> = emptyList()
        private set
    var transformerSeeds: List<TransformerSeed> = emptyList()
        private set
    var buswayEntries: List<BuswayCatalogEntry> = emptyList()
        private set

    var diagnostics: CatalogLoadDiagnostics = CatalogLoadDiagnostics(
        cableCount = 0,
        cableTypesCount = 0,
        breakerCount = 0,
        accessoryCount = 0,
        deratingCount = 0,
        transformerSeedCount = 0,
        buswayCount = 0,
        status = "UNLOADED"
    )
        private set

    fun loadAllFromStrings(
        cableCatalogJson: String,
        cableTypesDataJson: String,
        breakerCatalogJson: String,
        accessoryCatalogJson: String,
        deratingDataJson: String,
        transformersSeedJson: String,
        buswayCatalogJson: String? = null
    ): CatalogLoadDiagnostics {
        val warnings = mutableListOf<String>()

        cableProducts = parseCableProducts(cableCatalogJson, warnings)
        cableTypesData = parseCableTypesData(cableTypesDataJson, warnings)
        breakers = parseBreakers(breakerCatalogJson, warnings)
        accessories = parseAccessories(accessoryCatalogJson, warnings)
        deratingRows = parseDeratingData(deratingDataJson, warnings)
        transformerSeeds = parseTransformerSeeds(transformersSeedJson, warnings)
        if (buswayCatalogJson != null) {
            buswayEntries = parseBusways(buswayCatalogJson, warnings)
        }

        try {
            com.example.data.Catalog.deratingData = org.json.JSONObject(deratingDataJson)
        } catch (e: Exception) {
            warnings.add("Failed mapping legacy Catalog.deratingData: ${e.message}")
        }

        val status = if (
            cableProducts.isNotEmpty() &&
            cableTypesData.isNotEmpty() &&
            breakers.isNotEmpty() &&
            accessories.isNotEmpty() &&
            deratingRows.isNotEmpty() &&
            transformerSeeds.isNotEmpty()
        ) {
            "COMPLETE"
        } else {
            "DATA_MISSING"
        }

        diagnostics = CatalogLoadDiagnostics(
            cableCount = cableProducts.size,
            cableTypesCount = cableTypesData.size,
            breakerCount = breakers.size,
            accessoryCount = accessories.size,
            deratingCount = deratingRows.size,
            transformerSeedCount = transformerSeeds.size,
            buswayCount = buswayEntries.size,
            warnings = warnings,
            status = status
        )

        return diagnostics
    }

    private fun parseCableProducts(jsonStr: String, warnings: MutableList<String>): List<CableProduct> {
        val list = mutableListOf<CableProduct>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val mfg = safeString(obj, "mfg", "Manufacturer", defaultValue = "Generic")
                val typeCode = safeInt(obj, "typeCode", "TypeCode", "Code", defaultValue = 0)
                val size = safeDouble(obj, "size", "SZ (mm²)", "Size", defaultValue = 0.0)
                val model = safeString(obj, "model", "Model", defaultValue = "")
                val gnd = safeDouble(obj, "gnd", "GND", defaultValue = 0.0)
                val duct = safeDouble(obj, "duct", "DUCT", defaultValue = 0.0)
                val air = safeDouble(obj, "air", "AIR", defaultValue = 0.0)
                val od = safeDouble(obj, "od", "OD", defaultValue = 0.0)
                val wt = safeDouble(obj, "wt", "WT", defaultValue = 0.0)

                list.add(CableProduct(mfg, typeCode, size, model, gnd, duct, air, od, wt))
            }
        } catch (e: Exception) {
            warnings.add("Failed parsing cable_catalog.json: ${e.message}")
        }
        return list
    }

    private fun parseCableTypesData(jsonStr: String, warnings: MutableList<String>): List<CableTypeData> {
        val list = mutableListOf<CableTypeData>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val typeCode = safeInt(obj, "typeCode", "TypeCode", "TC", "code", defaultValue = i + 1)
                val cableType = safeString(obj, "Cable Type (active from _CATALOG)", "cableType", "TypeStr", defaultValue = "Standard")
                val sizeMm2 = safeDouble(obj, "SZ (mm²)", "col0", "size", defaultValue = 0.0)
                val rCu70 = safeDouble(obj, "RCu70", "col1", defaultValue = 0.0)
                val x = safeDouble(obj, "X", "col2", defaultValue = 0.0)
                val rAl70 = safeDouble(obj, "RAl70", "col3", defaultValue = 0.0)
                val rCu90 = safeDouble(obj, "RCu90", "col4", defaultValue = 0.0)
                val rAl90 = safeDouble(obj, "RAl90", defaultValue = 0.0)
                val conductor = safeString(obj, "conductor", defaultValue = "Cu")
                val insulation = safeString(obj, "insulation", defaultValue = "XLPE")
                val armour = safeString(obj, "armour", defaultValue = "Unarmoured")
                val cores = safeString(obj, "cores", defaultValue = "3C")

                list.add(CableTypeData(typeCode, cableType, sizeMm2, rCu70, x, rAl70, rCu90, rAl90, conductor, insulation, armour, cores))
            }
        } catch (e: Exception) {
            warnings.add("Failed parsing cable_types_data.json: ${e.message}")
        }
        return list
    }

    private fun parseBreakers(jsonStr: String, warnings: MutableList<String>): List<Breaker> {
        val list = mutableListOf<Breaker>()
        try {
            val cleanJson = jsonStr.trim().removePrefix("\uFEFF")
            val array = JSONArray(cleanJson)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val mfg = safeString(obj, "mfg", "Source", defaultValue = "Generic")
                val type = safeString(obj, "type", defaultValue = "MCCB")
                val series = safeString(obj, "series", "Series", defaultValue = "")
                val frame = safeString(obj, "frameA", "frame", "Frame", defaultValue = "")
                val version = safeString(obj, "version", "Version", defaultValue = "")

                val rawRating = if (obj.has("ratingA")) obj.get("ratingA") else if (obj.has("rating_A")) obj.get("rating_A") else if (obj.has("In (A)")) obj.get("In (A)") else null
                val ratingA = BreakerParser.parseRating(rawRating)

                val rawPoles = if (obj.has("poles")) obj.get("poles") else if (obj.has("Poles")) obj.get("Poles") else null
                val poles = BreakerParser.parsePoles(rawPoles)

                val rawIcu = if (obj.has("icuKa")) obj.get("icuKa") else if (obj.has("icu_kA")) obj.get("icu_kA") else if (obj.has("Icu@415V (kA)")) obj.get("Icu@415V (kA)") else null
                val icuKa = BreakerParser.parseDouble(rawIcu)

                val rawIcs = if (obj.has("icsKa")) obj.get("icsKa") else if (obj.has("ics_kA")) obj.get("ics_kA") else if (obj.has("Ics@415V (kA)")) obj.get("Ics@415V (kA)") else null
                val icsKa = BreakerParser.parseDouble(rawIcs)

                val rawIcm = if (obj.has("icmKa")) obj.get("icmKa") else if (obj.has("icm_kA")) obj.get("icm_kA") else if (obj.has("Icm@415V (kA)")) obj.get("Icm@415V (kA)") else null
                val icmKa = BreakerParser.parseDouble(rawIcm)

                val icwKa = BreakerParser.parseDouble(obj.opt("icwKa"))
                val cat = safeString(obj, "cat", defaultValue = "A")
                val tripUnit = safeString(obj, "tripUnit", "Trip Unit", defaultValue = "Thermal-Magnetic")
                val tripType = safeString(obj, "tripType", defaultValue = "Thermal-Magnetic")
                val orderingCode = safeString(obj, "orderingCode", "Ordering Code", defaultValue = "")

                list.add(Breaker(mfg, type, series, frame, version, ratingA, poles, icuKa, icsKa, icmKa, icwKa, cat, tripUnit, tripType, orderingCode))
            }
        } catch (e: Exception) {
            println("ERROR PARSING BREAKERS: ${e.message}")
            e.printStackTrace()
            warnings.add("Failed parsing breaker_catalog.json: ${e.message}")
        }
        return list
    }

    private fun parseAccessories(jsonStr: String, warnings: MutableList<String>): List<Accessory> {
        val list = mutableListOf<Accessory>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val mfg = safeString(obj, "mfg", "Manufacturer", defaultValue = "Generic")
                val category = safeString(obj, "category", "Category", defaultValue = "")
                val subCategory = safeString(obj, "subCategory", "Sub-Category", defaultValue = "")
                val partNumber = safeString(obj, "partNumber", "Part Number", defaultValue = "")
                val desc = safeString(obj, "desc", "Description", defaultValue = "")
                val sizeMin = safeDouble(obj, "sizeMin", "Size Min (mm²)", defaultValue = 0.0)
                val sizeMax = safeDouble(obj, "sizeMax", "Size Max (mm²)", defaultValue = 0.0)
                val odMin = safeDouble(obj, "odMin", "OD Min (mm)", defaultValue = 0.0)
                val odMax = safeDouble(obj, "odMax", "OD Max (mm)", defaultValue = 0.0)
                val cores = safeString(obj, "cores", "Cores", defaultValue = "Any")
                val conductor = if (obj.has("conductor") && !obj.isNull("conductor")) obj.optString("conductor") else null
                val armourType = if (obj.has("armourType") && !obj.isNull("armourType")) obj.optString("armourType") else null
                val threadSize = if (obj.has("threadSize") && !obj.isNull("threadSize")) obj.optString("threadSize") else null
                val boltSize = if (obj.has("boltSize") && !obj.isNull("boltSize")) obj.optString("boltSize") else null

                list.add(Accessory(mfg, category, subCategory, partNumber, desc, sizeMin, sizeMax, odMin, odMax, cores, conductor, armourType, threadSize, boltSize))
            }
        } catch (e: Exception) {
            warnings.add("Failed parsing accessory_catalog.json: ${e.message}")
        }
        return list
    }

    private fun parseDeratingData(jsonStr: String, warnings: MutableList<String>): List<DeratingTableRow> {
        val list = mutableListOf<DeratingTableRow>()
        try {
            val trimmed = jsonStr.trim()
            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val airTemp = safeDouble(obj, "AIR_TEMP", defaultValue = 40.0)
                    val pvc = safeDouble(obj, "PVC", defaultValue = 1.0)
                    val xlpe = safeDouble(obj, "XLPE", defaultValue = 1.0)
                    val gndTemp = safeDouble(obj, "GND_TEMP", defaultValue = 35.0)
                    val soilRes = safeDouble(obj, "SOIL_RES", defaultValue = 1.0)
                    val soilFactor = safeDouble(obj, "FACTOR", defaultValue = 1.0)
                    val grpKey = safeString(obj, "GRP_KEY", defaultValue = "Air_Trefoil_Touching")
                    list.add(DeratingTableRow(airTemp, pvc, xlpe, gndTemp, soilRes, soilFactor, grpKey))
                }
            } else if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                val grouping = root.optJSONObject("grouping")
                if (grouping != null) {
                    val factors = grouping.optJSONObject("factors")
                    if (factors != null) {
                        val keys = factors.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            list.add(DeratingTableRow(
                                airTemp = 0.0,
                                pvcFactor = 0.0,
                                xlpeFactor = 0.0,
                                gndTemp = 0.0,
                                soilRes = 0.0,
                                soilFactor = 0.0,
                                grpKey = key
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            warnings.add("Failed parsing derating_data.json: ${e.message}")
        }
        return list
    }

    private fun parseTransformerSeeds(jsonStr: String, warnings: MutableList<String>): List<TransformerSeed> {
        val list = mutableListOf<TransformerSeed>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = safeString(obj, "transformerId", defaultValue = "TX-$i")
                val kva = safeDouble(obj, "kVA", defaultValue = 0.0)
                val v1 = safeDouble(obj, "primaryVoltageV", defaultValue = 22000.0)
                val v2 = safeDouble(obj, "secondaryVoltageV", defaultValue = 400.0)
                val z = safeDouble(obj, "percentZ", defaultValue = 4.0)
                val xr = safeDouble(obj, "xrRatio", defaultValue = 3.0)
                val rt = safeDouble(obj, "rtMOhm", defaultValue = 0.0)
                val xt = safeDouble(obj, "xtMOhm", defaultValue = 0.0)
                val isc = safeDouble(obj, "iscKA", defaultValue = 0.0)

                list.add(TransformerSeed(id, kva, v1, v2, z, xr, rt, xt, isc))
            }
        } catch (e: Exception) {
            warnings.add("Failed parsing transformers_seed.json: ${e.message}")
        }
        return list
    }

    fun parseBusways(jsonStr: String, warnings: MutableList<String>): List<BuswayCatalogEntry> {
        val list = mutableListOf<BuswayCatalogEntry>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val mfg = safeString(obj, "mfg", "mfg", defaultValue = "Elsewedy")
                val model = safeString(obj, "model", defaultValue = "Power Link")
                val conductor = safeString(obj, "conductor", defaultValue = "Cu")
                val ratingA = safeDouble(obj, "ratingA", "rating_A", defaultValue = 0.0)
                val ipkKA = safeDouble(obj, "ipkKA", "ipk_kA", defaultValue = 0.0)
                val icwKA = safeDouble(obj, "icwKA", "icw_kA", defaultValue = 0.0)
                val rMOhmPerM = safeDouble(obj, "rMOhmPerM", "rOhmPerKm", defaultValue = 0.0)
                val xMOhmPerM = safeDouble(obj, "xMOhmPerM", "xOhmPerKm", defaultValue = 0.0)
                val weightKgPerM = safeDouble(obj, "weightKgPerM", defaultValue = 0.0)
                val ipRating = safeString(obj, "ipRating", defaultValue = "IP55")

                list.add(BuswayCatalogEntry(mfg, model, conductor, ratingA, ipkKA, icwKA, rMOhmPerM, xMOhmPerM, weightKgPerM, ipRating))
            }
        } catch (e: Exception) {
            warnings.add("Failed parsing busway_catalog.json: ${e.message}")
        }
        return list
    }

    private fun safeString(obj: JSONObject, vararg keys: String, defaultValue: String): String {
        for (key in keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                val valStr = obj.optString(key, "").trim()
                if (valStr.isNotEmpty()) return valStr
            }
        }
        return defaultValue
    }

    private fun safeDouble(obj: JSONObject, vararg keys: String, defaultValue: Double): Double {
        for (key in keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                val raw = obj.opt(key)
                if (raw is Number) return raw.toDouble()
                val str = raw?.toString()?.trim() ?: ""
                val d = str.toDoubleOrNull()
                if (d != null) return d
            }
        }
        return defaultValue
    }

    private fun safeInt(obj: JSONObject, vararg keys: String, defaultValue: Int): Int {
        for (key in keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                val raw = obj.opt(key)
                if (raw is Number) return raw.toInt()
                val str = raw?.toString()?.trim() ?: ""
                val i = str.toIntOrNull()
                if (i != null) return i
                val d = str.toDoubleOrNull()
                if (d != null) return d.toInt()
            }
        }
        return defaultValue
    }
}
