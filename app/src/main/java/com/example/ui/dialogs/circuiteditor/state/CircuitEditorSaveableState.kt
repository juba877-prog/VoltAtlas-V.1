package com.example.ui.dialogs.circuiteditor.state

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.State
import androidx.compose.runtime.MutableState
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * The complete restorable editor payload. Contains ONLY primitives, strings, booleans,
 * nullable strings, and step names — never ViewModel, Flow, Context, repository, or
 * snapshot objects. Derived values (applicable steps, source resolution) are recomputed
 * from the restored draft plus the current snapshots and are deliberately not saved.
 */
data class CircuitEditorSaveableState(
    val draft: CircuitEditorDraft,
    val currentStepName: String,
    val visitedStepNames: List<String>,
    val identityError: String?,
    val reviewError: String?
) {
    companion object {
        fun initial(draft: CircuitEditorDraft): CircuitEditorSaveableState = CircuitEditorSaveableState(
            draft = draft,
            currentStepName = CircuitWorkflowStep.IDENTITY.name,
            visitedStepNames = listOf(CircuitWorkflowStep.IDENTITY.name),
            identityError = null,
            reviewError = null
        )

        /** Deterministic null-safe encoding: a nullable string is stored as (hasValue, value). */
        internal fun writeNullable(list: MutableList<Any>, value: String?) {
            if (value == null) {
                list.add(false)
                list.add("")
            } else {
                list.add(true)
                list.add(value)
            }
        }

        internal fun readNullableString(cursor: List<Any>, hasValueIndex: Int): String? =
            if (cursor[hasValueIndex] as Boolean) cursor[hasValueIndex + 1] as String else null

        /**
         * Deterministic list-based Saver over primitives, strings, booleans, step names, and
         * (hasValue, value) pairs for nullable strings. No serialization libraries, reflection,
         * Parcelable generation, or new dependencies.
         */
        val Saver: Saver<CircuitEditorSaveableState, Any> = listSaver(
            save = { state ->
                buildList {
                    val d = state.draft
                    add(d.name)
                    add(d.panelType)
                    add(d.fedFrom)
                    add(d.transformerId)
                    writeNullable(this, d.loadCategory)
                    add(d.loadNature)
                    add(d.startingMethod)
                    add(d.startingCurrentMultiplier)
                    add(d.loadKVA)
                    add(d.pf)
                    add(d.demandFactor)
                    add(d.voltageV)
                    add(d.safetyFactor)
                    add(d.phases)
                    add(d.isHarmonic)
                    add(d.supplyCategory)
                    add(d.assignedAtsId)
                    add(d.assignedGeneratorId)
                    add(d.assignedUpsId)
                    add(d.feederType)
                    add(d.manufacturer)
                    add(d.cableType)
                    add(d.parallelRuns)
                    add(d.length_m)
                    add(d.maxVDpct)
                    add(d.buswayMfg)
                    add(d.buswayModel)
                    add(d.filterMat)
                    add(d.filterArmour)
                    add(d.filterCores)
                    add(d.filterQuery)
                    add(d.installation)
                    add(d.airTempC)
                    add(d.gndTempC)
                    add(d.burialDepth_m)
                    add(d.soilRes)
                    add(d.noCircuits)
                    add(d.formation)
                    add(d.spacing)
                    add(d.busbarRatingA)
                    add(d.mainIncomingType)
                    add(d.mainIncomingCount)
                    add(d.ipRating)
                    add(d.panelLocation)
                    add(d.cableEntry)
                    add(d.numberOfWays)
                    add(d.feederDescription)
                    add(state.currentStepName)
                    add(state.visitedStepNames.size)
                    addAll(state.visitedStepNames)
                    writeNullable(this, state.identityError)
                    writeNullable(this, state.reviewError)
                }
            },
            restore = { list -> restoreFromList(list) }
        )
    }
}

/**
 * Cursor-based deterministic restore. Field order is identical to the save side.
 */
private fun restoreFromList(list: List<Any>): CircuitEditorSaveableState {
    var i = 0
    fun nextString(): String {
        val v = list[i] as String
        i++
        return v
    }
    fun nextBoolean(): Boolean {
        val v = list[i] as Boolean
        i++
        return v
    }
    fun nextNullable(): String? {
        val v = CircuitEditorSaveableState.readNullableString(list, i)
        i += 2
        return v
    }
    val draft = CircuitEditorDraft(
        name = nextString(),
        panelType = nextString(),
        fedFrom = nextString(),
        transformerId = nextString(),
        loadCategory = nextNullable(),
        loadNature = nextString(),
        startingMethod = nextString(),
        startingCurrentMultiplier = nextString(),
        loadKVA = nextString(),
        pf = nextString(),
        demandFactor = nextString(),
        voltageV = nextString(),
        safetyFactor = nextString(),
        phases = nextString(),
        isHarmonic = nextBoolean(),
        supplyCategory = nextString(),
        assignedAtsId = nextString(),
        assignedGeneratorId = nextString(),
        assignedUpsId = nextString(),
        feederType = nextString(),
        manufacturer = nextString(),
        cableType = nextString(),
        parallelRuns = nextString(),
        length_m = nextString(),
        maxVDpct = nextString(),
        buswayMfg = nextString(),
        buswayModel = nextString(),
        filterMat = nextString(),
        filterArmour = nextString(),
        filterCores = nextString(),
        filterQuery = nextString(),
        installation = nextString(),
        airTempC = nextString(),
        gndTempC = nextString(),
        burialDepth_m = nextString(),
        soilRes = nextString(),
        noCircuits = nextString(),
        formation = nextString(),
        spacing = nextString(),
        busbarRatingA = nextString(),
        mainIncomingType = nextString(),
        mainIncomingCount = nextString(),
        ipRating = nextString(),
        panelLocation = nextString(),
        cableEntry = nextString(),
        numberOfWays = nextString(),
        feederDescription = nextString()
    )
    val currentStepName = nextString()
    val visitedCount = (list[i] as Number).toInt()
    i++
    val visitedNames = buildList {
        repeat(visitedCount) { add(nextString()) }
    }
    val identityError = nextNullable()
    val reviewError = nextNullable()
    return CircuitEditorSaveableState(
        draft = draft,
        currentStepName = currentStepName,
        visitedStepNames = visitedNames,
        identityError = identityError,
        reviewError = reviewError
    )
}

/**
 * Restores [currentStepName] to a [CircuitWorkflowStep]; unknown restored names fall back
 * to IDENTITY defensively and deterministically.
 */
fun String.toCircuitWorkflowStepOrDefault(): CircuitWorkflowStep =
    CircuitWorkflowStep.entries.firstOrNull { it.name == this } ?: CircuitWorkflowStep.IDENTITY

/**
 * Restores [visitedStepNames] to a step set; unknown names are dropped.
 */
fun List<String>.toCircuitWorkflowStepSet(): Set<CircuitWorkflowStep> =
    mapNotNull { name -> CircuitWorkflowStep.entries.firstOrNull { it.name == name } }.toSet()

/**
 * Local read/write view over one field of the single saveable holder, so that ordinary
 * assignments (`draft = newDraft`) persist into the saveable state. Every write routes
 * through the holder; no second source of truth is created.
 */
class CircuitEditorSaveableField<T>(
    private val holder: MutableState<CircuitEditorSaveableState>,
    private val get: (CircuitEditorSaveableState) -> T,
    private val set: (CircuitEditorSaveableState, T) -> CircuitEditorSaveableState
) : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = get(holder.value)
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        holder.value = set(holder.value, value)
    }
}