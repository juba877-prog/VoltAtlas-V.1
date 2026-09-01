package com.example.data.catalog

data class CableProduct(
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

data class CableTypeData(
    val typeCode: Int,
    val cableType: String,
    val sizeMm2: Double,
    val rCu20: Double,
    val x: Double,
    val rAl20: Double,
    val rCu_70_est: Double,
    val rAl_70_est: Double,
    val conductor: String,
    val insulation: String,
    val armour: String,
    val cores: String,
    val provenance: String = "IEC_60228_TABLE_2"
) {
    // Backward-compatibility property aliases
    val rCu70: Double get() = rCu20
    val rAl70: Double get() = rAl20
    val rCu90: Double get() = rCu_70_est
    val rAl90: Double get() = rAl_70_est

    constructor(
        typeCode: Int,
        cableType: String,
        sizeMm2: Double,
        rCu70: Double,
        x: Double,
        rAl70: Double,
        rCu90: Double,
        rAl90: Double,
        conductor: String,
        insulation: String,
        armour: String,
        cores: String
    ) : this(
        typeCode = typeCode,
        cableType = cableType,
        sizeMm2 = sizeMm2,
        rCu20 = rCu70,
        x = x,
        rAl20 = rAl70,
        rCu_70_est = rCu90,
        rAl_70_est = rAl90,
        conductor = conductor,
        insulation = insulation,
        armour = armour,
        cores = cores,
        provenance = "IEC_60228_TABLE_2"
    )
}

data class Breaker(
    val mfg: String,
    val type: String = "MCCB",
    val series: String,
    val frame: String,
    val version: String,
    val ratingA: Double,
    val poles: Int,
    val icuKa: Double,
    val icsKa: Double,
    val icmKa: Double,
    val icwKa: Double = 0.0,
    val cat: String = "A",
    val tripUnit: String,
    val tripType: String = "Thermal-Magnetic",
    val orderingCode: String,
    val provenance: String = "BREAKER_CATALOG_JSON"
) {
    val rating_A: Double get() = ratingA
    val icu_kA: Double get() = icuKa
    val ics_kA: Double get() = icsKa
    val icm_kA: Double get() = icmKa
}

data class Accessory(
    val mfg: String,
    val category: String,
    val subCategory: String,
    val partNumber: String,
    val description: String,
    val sizeMin: Double,
    val sizeMax: Double,
    val odMin: Double,
    val odMax: Double,
    val cores: String = "Any",
    val conductor: String? = null,
    val armourType: String? = null,
    val threadSize: String? = null,
    val boltSize: String? = null,
    val provenance: String = "ACCESSORY_CATALOG_JSON"
)

data class DeratingTableRow(
    val airTemp: Double,
    val pvcFactor: Double,
    val xlpeFactor: Double,
    val gndTemp: Double,
    val soilRes: Double,
    val soilFactor: Double,
    val grpKey: String,
    val provenance: String = "DERATING_DATA_JSON"
)

data class TransformerSeed(
    val transformerId: String,
    val kVA: Double,
    val primaryVoltageV: Double,
    val secondaryVoltageV: Double,
    val percentZ: Double,
    val xrRatio: Double,
    val rtMOhm: Double,
    val xtMOhm: Double,
    val iscKA: Double,
    val provenance: String = "TRANSFORMERS_SEED_JSON"
)

data class BuswayCatalogEntry(
    val mfg: String,
    val model: String,
    val conductor: String,
    val ratingA: Double,
    val ipkKA: Double,
    val icwKA: Double,
    val rMOhmPerM: Double,
    val xMOhmPerM: Double,
    val weightKgPerM: Double = 0.0,
    val ipRating: String = "IP55",
    val provenance: String = "BUSWAY_CATALOG_JSON"
)

data class CatalogLoadDiagnostics(
    val cableCount: Int,
    val cableTypesCount: Int,
    val breakerCount: Int,
    val accessoryCount: Int,
    val deratingCount: Int,
    val transformerSeedCount: Int,
    val buswayCount: Int = 0,
    val warnings: List<String> = emptyList(),
    val status: String
)
