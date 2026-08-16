package com.example.ai.contracts

import com.squareup.moshi.Json

/**
 * Canonical engineering physical units supported across VoltAtlas contracts.
 */
enum class EngineeringUnit(val symbol: String) {
    @Json(name = "A") AMPERE("A"),
    @Json(name = "V") VOLT("V"),
    @Json(name = "kVA") KILOVOLT_AMPERE("kVA"),
    @Json(name = "kW") KILOWATT("kW"),
    @Json(name = "m") METER("m"),
    @Json(name = "mm²") SQUARE_MILLIMETER("mm²"),
    @Json(name = "Ω") OHM("Ω"),
    @Json(name = "mΩ") MILLIOHM("mΩ"),
    @Json(name = "%") PERCENT("%"),
    @Json(name = "kA") KILOAMPERE("kA"),
    @Json(name = "s") SECOND("s"),
    @Json(name = "°C") CELSIUS("°C"),
    @Json(name = "NONE") NONE("NONE");
}
