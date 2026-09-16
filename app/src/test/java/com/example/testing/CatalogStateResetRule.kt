package com.example.testing

import com.example.data.AccessoryEntry
import com.example.data.BreakerEntry
import com.example.data.CableCatalogEntry
import com.example.data.CableType
import com.example.data.ConduitCatalogEntry
import com.example.data.DataSheetEntry
import com.example.data.Catalog
import com.example.data.catalog.BuswayCatalogEntry
import org.junit.rules.ExternalResource
import org.json.JSONObject

/**
 * Immutable snapshot of every mutable, process-wide field of the [Catalog] singleton.
 *
 * [Catalog] is a Kotlin `object`, so its state is shared by every test class that runs in the
 * same JVM fork. Test classes that rewrite `Catalog.cableTypes` / `Catalog.cableCatalog` (or that
 * trigger the production loader) therefore leak state into every class that runs afterwards.
 *
 * Regression context (defect `VOLT-TESTISO-001`): `resolveCableType()` consults
 * `Catalog.cableTypes` at runtime, so a leaked catalogue changed the value observed by
 * `CircuitEditorInitializerSpecTest.test01_addMode_initialization`
 * (`expected:<[A]rmoured> but was:<[Un]armoured>`). The defect was order-dependent and became
 * visible when Kotlin 2.4.20 changed test-class execution order.
 */
data class CatalogStateSnapshot(
    val cableTypes: List<CableType>,
    val dataSheet: List<DataSheetEntry>,
    val cableCatalog: List<CableCatalogEntry>,
    val breakers: List<BreakerEntry>,
    val accessories: List<AccessoryEntry>,
    val conduits: List<ConduitCatalogEntry>,
    val busways: List<BuswayCatalogEntry>,
    val deratingData: JSONObject?,
    val lastError: Throwable?
)

/** Captures the current shared [Catalog] state. */
fun captureCatalogState(): CatalogStateSnapshot = CatalogStateSnapshot(
    cableTypes = Catalog.cableTypes,
    dataSheet = Catalog.dataSheet,
    cableCatalog = Catalog.cableCatalog,
    breakers = Catalog.breakers,
    accessories = Catalog.accessories,
    conduits = Catalog.conduits,
    busways = Catalog.busways,
    deratingData = Catalog.deratingData,
    lastError = Catalog.lastError
)

/**
 * Restores shared [Catalog] state captured by [captureCatalogState] and invalidates the derived
 * caches so subsequent lookups are recomputed from the restored lists.
 */
fun restoreCatalogState(snapshot: CatalogStateSnapshot) {
    Catalog.cableTypes = snapshot.cableTypes
    Catalog.dataSheet = snapshot.dataSheet
    Catalog.cableCatalog = snapshot.cableCatalog
    Catalog.breakers = snapshot.breakers
    Catalog.accessories = snapshot.accessories
    Catalog.conduits = snapshot.conduits
    Catalog.busways = snapshot.busways
    Catalog.deratingData = snapshot.deratingData
    Catalog.lastError = snapshot.lastError
    Catalog.invalidateIndexes()
}

/**
 * Test-only guard that makes shared [Catalog] state class-scoped instead of process-scoped.
 *
 * Semantics: snapshot on entry, restore on exit. Used as a JUnit4 class rule
 * (`@JvmField @org.junit.ClassRule`) it guarantees that a test class cannot leak catalogue
 * mutations into classes that run after it; used as a method rule (`@get:Rule`) it scopes the
 * mutation to a single test method.
 *
 * Restoring the entry state (rather than a hard-coded "empty" baseline) keeps classes that load
 * the catalogue in `@BeforeClass` / `@Before` working unchanged, while every class still starts
 * from the state produced by its predecessors and hands back exactly that state on exit.
 */
class CatalogStateResetRule : ExternalResource() {

    private var snapshot: CatalogStateSnapshot? = null

    override fun before() {
        snapshot = captureCatalogState()
    }

    override fun after() {
        snapshot?.let { restoreCatalogState(it) }
        snapshot = null
    }
}
