package com.example.engine

import com.example.data.CircuitEntity
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Standard electrical load categories recognized in consulting drawings and
 * international installation standards (IEC 60364, BS 7671, NEC 220).
 */
enum class LoadCategory(val displayName: String, val defaultDemandFactor: Double) {
    LIGHTING("Lighting", 0.90),
    SMALL_POWER("Small Power / Sockets", 0.70),
    HVAC_MOTORS("HVAC & Motors", 0.80),
    ELEVATORS("Elevators & Lifts", 0.75),
    PUMPS_PLUMBING("Pumps & Plumbing", 0.75),
    FIRE_LIFE_SAFETY("Fire & Life Safety", 0.00), // Standby during normal operation
    DATA_IT_UPS("Data, IT & UPS", 1.00),
    KITCHEN_APPLIANCES("Kitchen & Appliances", 0.70),
    SPARE_SPACE("Spare / Space", 0.00),
    SUB_PANEL("Sub-Distribution Panel", 0.90),
    GENERAL("General / Unclassified", 1.00)
}

/**
 * Summary breakdown of load and demand for a specific load category.
 */
data class CategoryDiversityBreakdown(
    val category: LoadCategory,
    val circuitCount: Int,
    val connectedKva: Double,
    val demandKva: Double,
    val effectiveDiversityFactor: Double
)

/**
 * Full diversity calculation result for a switchboard or distribution board.
 */
data class DiversityCalculationResult(
    val totalConnectedKva: Double,
    val totalDemandKva: Double,
    val overallDiversityFactor: Double, // Coincidence factor (TDL / TCL <= 1.0)
    val categoryBreakdowns: List<CategoryDiversityBreakdown>,
    val circuitCount: Int,
    val boardDesignCurrentA: Double,
    val standardRdfTable10: Double // IEC 61439-1 Table 10 RDF for this circuit count
)

/**
 * Diversity & Demand Calculation Engine compliant with:
 * - **IEC 61439-1 Table 10**: Rated Diversity Factor (RDF) by number of circuits.
 * - **IEC 60364-5-52 & BS 7671 Appendix A / Table A1**: Category-based maximum demand & diversity.
 * - **Standard Consulting Engineering Practice**:
 *   - Small Power: First 10 kVA @ 100%, balance @ 50%.
 *   - Motors / HVAC: Cascading diversity (1st @ 100%, 2nd @ 80%, others @ 60%).
 *   - Elevators: Group coincidence factors (1: 100%, 2: 80%, 3: 75%, 4+: 70%).
 *   - Pumps: Duty / Standby exclusion.
 *   - Fire / Emergency: 0% active demand during normal operating mode.
 *   - Spare / Space: 0% active demand.
 */
object DiversityCalculationEngine {

    /**
     * Resolves the Rated Diversity Factor (RDF) from IEC 61439-1 Table 10
     * based purely on the number of outgoing circuits:
     * - 1 circuit: 1.0
     * - 2 to 3 circuits: 0.9
     * - 4 to 5 circuits: 0.8
     * - 6 to 9 circuits: 0.7
     * - 10 or more circuits: 0.6
     */
    fun resolveIecTable10Rdf(circuitCount: Int): Double {
        return when {
            circuitCount <= 1 -> 1.0
            circuitCount in 2..3 -> 0.9
            circuitCount in 4..5 -> 0.8
            circuitCount in 6..9 -> 0.7
            else -> 0.6
        }
    }

    /**
     * Classifies a circuit into an engineering [LoadCategory] using its stored
     * [CircuitEntity.loadCategory], [CircuitEntity.loadNature], [CircuitEntity.panelType],
     * and circuit name heuristics.
     */
    fun classifyCircuit(circuit: CircuitEntity, isSubPanel: Boolean = false): LoadCategory {
        if (isSubPanel) return LoadCategory.SUB_PANEL

        // 1. Explicit stored category
        val explicit = circuit.loadCategory?.trim()?.uppercase().orEmpty()
        if (explicit.isNotEmpty()) {
            when {
                explicit.contains("FIRE") || explicit.contains("SMOKE") || explicit.contains("EMERGENCY") -> return LoadCategory.FIRE_LIFE_SAFETY
                explicit.contains("LIGHT") || explicit.contains("LGT") -> return LoadCategory.LIGHTING
                explicit.contains("SOCKET") || explicit.contains("RECEPTACLE") || explicit.contains("POWER") || explicit.contains("SKT") -> return LoadCategory.SMALL_POWER
                explicit.contains("HVAC") || explicit.contains("AHU") || explicit.contains("FCU") || explicit.contains("CHILLER") || explicit.contains("MOTOR") -> return LoadCategory.HVAC_MOTORS
                explicit.contains("ELEVATOR") || explicit.contains("LIFT") || explicit.contains("ESCALATOR") -> return LoadCategory.ELEVATORS
                explicit.contains("PUMP") || explicit.contains("PLUMBING") -> return LoadCategory.PUMPS_PLUMBING
                explicit.contains("UPS") || explicit.contains("DATA") || explicit.contains("SERVER") || explicit.contains("IT") -> return LoadCategory.DATA_IT_UPS
                explicit.contains("KITCHEN") || explicit.contains("COOK") -> return LoadCategory.KITCHEN_APPLIANCES
                explicit.contains("SPARE") || explicit.contains("SPACE") -> return LoadCategory.SPARE_SPACE
            }
        }

        // 2. Name heuristics (Fire/smoke life-safety checked before pumps)
        val nameUpper = circuit.circuitName.uppercase()
        if (nameUpper.contains("FIRE") || nameUpper.contains("SMOKE")) {
            return LoadCategory.FIRE_LIFE_SAFETY
        }
        if (nameUpper.contains("SPARE") || nameUpper.contains("SPACE")) {
            return LoadCategory.SPARE_SPACE
        }

        // 3. Motor load nature
        if (circuit.loadNature.equals("Motor", ignoreCase = true)) {
            return LoadCategory.HVAC_MOTORS
        }

        // 4. Remaining name heuristics
        return when {
            nameUpper.contains("LGT") || nameUpper.contains("LIGHT") || nameUpper.contains("LP") -> LoadCategory.LIGHTING
            nameUpper.contains("SKT") || nameUpper.contains("SOCKET") || nameUpper.contains("RCP") || nameUpper.contains("PWR") -> LoadCategory.SMALL_POWER
            nameUpper.contains("AHU") || nameUpper.contains("FCU") || nameUpper.contains("HVAC") || nameUpper.contains("CHILLER") || nameUpper.contains("MTR") || nameUpper.contains("FAN") -> LoadCategory.HVAC_MOTORS
            nameUpper.contains("LIFT") || nameUpper.contains("ELEVATOR") -> LoadCategory.ELEVATORS
            nameUpper.contains("PUMP") || nameUpper.contains("PMP") -> LoadCategory.PUMPS_PLUMBING
            nameUpper.contains("UPS") || nameUpper.contains("DATA") || nameUpper.contains("SRV") -> LoadCategory.DATA_IT_UPS
            nameUpper.contains("KTCH") || nameUpper.contains("KITCHEN") || nameUpper.contains("OVEN") -> LoadCategory.KITCHEN_APPLIANCES
            else -> LoadCategory.GENERAL
        }
    }

    /**
     * Calculates the diversified demand load (TDL), total connected load (TCL),
     * and category breakdowns for a set of circuits fed from a switchboard or panel.
     *
     * @param circuits The outgoing circuits directly fed from this board.
     * @param subPanelLoads Pre-calculated recursive loads for sub-panels keyed by circuitId.
     * @param boardVoltageV Stored operating voltage of the board (e.g. 400V).
     * @param isThreePhase True if the board is 3-phase (400V), false if 1-phase (230V).
     */
    fun calculateBoardDiversity(
        circuits: List<CircuitEntity>,
        subPanelLoads: Map<Int, Double> = emptyMap(),
        boardVoltageV: Double = 400.0,
        isThreePhase: Boolean = true
    ): DiversityCalculationResult {
        if (circuits.isEmpty()) {
            return DiversityCalculationResult(
                totalConnectedKva = 0.0,
                totalDemandKva = 0.0,
                overallDiversityFactor = 1.0,
                categoryBreakdowns = emptyList(),
                circuitCount = 0,
                boardDesignCurrentA = 0.0,
                standardRdfTable10 = 1.0
            )
        }

        val table10Rdf = resolveIecTable10Rdf(circuits.size)

        // Group circuits by load category
        val categoryCircuits = mutableMapOf<LoadCategory, MutableList<Pair<CircuitEntity, Double>>>()

        for (c in circuits) {
            val isSub = subPanelLoads.containsKey(c.id) ||
                    (c.panelType != null && c.panelType in setOf("MDB", "SMDB", "DB", "MCC", "PANEL", "OTHER"))
            val cat = classifyCircuit(c, isSub)
            val load = if (isSub) (subPanelLoads[c.id] ?: c.loadKVA) else c.loadKVA
            categoryCircuits.getOrPut(cat) { mutableListOf() }.add(c to load)
        }

        var totalConnectedSum = 0.0
        var totalDemandSum = 0.0
        val breakdowns = mutableListOf<CategoryDiversityBreakdown>()

        for ((cat, list) in categoryCircuits) {
            val catConnectedKva = list.sumOf { it.second }
            totalConnectedSum += catConnectedKva

            val catDemandKva: Double = when (cat) {
                LoadCategory.LIGHTING -> {
                    // Standard lighting: 90% demand factor (unless custom non-default DF is set), 1.0 coincidence
                    list.sumOf { (c, load) ->
                        val df = if (c.demandFactor > 0.0 && c.demandFactor < 1.0) c.demandFactor else 0.90
                        load * df
                    }
                }
                LoadCategory.SMALL_POWER -> {
                    // BS 7671 Table A1 / NEC 220: First 10 kVA @ 100%, balance @ 50%
                    if (catConnectedKva <= 10.0) {
                        catConnectedKva
                    } else {
                        10.0 + (catConnectedKva - 10.0) * 0.50
                    }
                }
                LoadCategory.HVAC_MOTORS -> {
                    // Cascading motors: 1st (largest) @ 100%, 2nd @ 80%, all others @ 60%
                    val sortedLoads = list.map { it.second }.sortedDescending()
                    var motorDemand = 0.0
                    sortedLoads.forEachIndexed { idx, mLoad ->
                        val factor = when (idx) {
                            0 -> 1.00
                            1 -> 0.80
                            else -> 0.60
                        }
                        motorDemand += mLoad * factor
                    }
                    motorDemand
                }
                LoadCategory.ELEVATORS -> {
                    // Group coincidence factor based on lift count
                    val factor = when (list.size) {
                        1 -> 1.00
                        2 -> 0.80
                        3 -> 0.75
                        else -> 0.70
                    }
                    catConnectedKva * factor
                }
                LoadCategory.PUMPS_PLUMBING -> {
                    // Check for standby pump keywords
                    var dutySum = 0.0
                    for ((c, load) in list) {
                        val isStandby = c.circuitName.contains("STANDBY", ignoreCase = true) ||
                                c.circuitName.contains("STBY", ignoreCase = true)
                        if (!isStandby) {
                            dutySum += load
                        }
                    }
                    // If no explicit standby was marked but multiple pumps exist, apply 0.75 factor
                    if (dutySum == 0.0 || dutySum == catConnectedKva) {
                        catConnectedKva * (if (list.size > 1) 0.75 else 1.0)
                    } else {
                        dutySum
                    }
                }
                LoadCategory.FIRE_LIFE_SAFETY -> {
                    // Standby in normal operation mode (0 kVA normal demand)
                    0.0
                }
                LoadCategory.DATA_IT_UPS -> {
                    // 100% continuous demand
                    catConnectedKva
                }
                LoadCategory.KITCHEN_APPLIANCES -> {
                    // 1st @ 100%, 2nd @ 80%, others @ 60%
                    val sorted = list.map { it.second }.sortedDescending()
                    var kitchenDemand = 0.0
                    sorted.forEachIndexed { idx, kLoad ->
                        val factor = when (idx) {
                            0 -> 1.00
                            1 -> 0.80
                            else -> 0.60
                        }
                        kitchenDemand += kLoad * factor
                    }
                    kitchenDemand
                }
                LoadCategory.SPARE_SPACE -> {
                    // 0% active demand
                    0.0
                }
                LoadCategory.SUB_PANEL -> {
                    // Sub-panel carrying its own pre-calculated diversified load
                    // Applied with 0.90 switchboard-level coincidence
                    catConnectedKva * 0.90
                }
                LoadCategory.GENERAL -> {
                    // Uses circuit demandFactor and IEC Table 10 RDF
                    list.sumOf { (c, load) ->
                        val df = if (c.demandFactor > 0.0 && c.demandFactor < 1.0) c.demandFactor else table10Rdf
                        load * df
                    }
                }
            }

            totalDemandSum += catDemandKva

            val effectiveDf = if (catConnectedKva > 0.0) {
                round((catDemandKva / catConnectedKva) * 100.0) / 100.0
            } else 1.0

            breakdowns.add(
                CategoryDiversityBreakdown(
                    category = cat,
                    circuitCount = list.size,
                    connectedKva = round(catConnectedKva * 10.0) / 10.0,
                    demandKva = round(catDemandKva * 10.0) / 10.0,
                    effectiveDiversityFactor = effectiveDf
                )
            )
        }

        val roundedTcl = round(totalConnectedSum * 10.0) / 10.0
        val roundedTdl = round(totalDemandSum * 10.0) / 10.0
        val overallDf = if (roundedTcl > 0.0) {
            min(1.0, round((roundedTdl / roundedTcl) * 100.0) / 100.0)
        } else 1.0

        val v = if (boardVoltageV > 0.0) boardVoltageV else 400.0
        val boardCurrentA = if (roundedTdl > 0.0) {
            if (isThreePhase) (roundedTdl * 1000.0) / (sqrt(3.0) * v)
            else (roundedTdl * 1000.0) / v
        } else 0.0

        return DiversityCalculationResult(
            totalConnectedKva = roundedTcl,
            totalDemandKva = roundedTdl,
            overallDiversityFactor = overallDf,
            categoryBreakdowns = breakdowns,
            circuitCount = circuits.size,
            boardDesignCurrentA = round(boardCurrentA * 10.0) / 10.0,
            standardRdfTable10 = table10Rdf
        )
    }
}
