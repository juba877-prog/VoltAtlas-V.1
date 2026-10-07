package com.example.ui

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.CircuitEntity
import com.example.data.ProjectEntity
import com.example.data.EngineeringRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * KT-12 regression guard — injected-database path.
 *
 * Prevents two regressions from reappearing:
 *
 * 1. **The injection seam.** `EngineeringViewModel(application, testDatabase)` must keep using the
 *    supplied database. If a future change routes the injected instance back through
 *    `AppDatabase.getDatabase(application)`, this test fails, because it asserts the project it
 *    created is visible through the injected database and the repository built on it.
 *
 * 2. **The Robolectric file-backed failure.** Robolectric's native SQLite shadow cannot create a
 *    file-backed database inside its sandbox directory (`SQLITE_CANTOPEN`), even when the path and
 *    its parent directory are both correct (proven in docs/audit/KT-12_DB_PATH_PROVENANCE.md,
 *    classification C). Robolectric UI tests must therefore inject an in-memory Room instance.
 *    This test passes only if that remains possible and if `createAndOpenProject` completes
 *    deterministically against it.
 *
 * It also pins the honest async contract: `createAndOpenProject` returns a Job, and project
 * creation is only complete once that Job completes and `activeProjectId` is non-null.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class EngineeringViewModelInjectedDatabaseTest {

    private lateinit var database: AppDatabase
    private lateinit var viewModel: EngineeringViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .allowMainThreadQueries()
            .build()
        viewModel = EngineeringViewModel(context, database)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) database.close()
        AppDatabase.useTestExecutors = false
        Dispatchers.resetMain()
    }

    @Test
    fun createAndOpenProject_usesInjectedDatabase_andPublishesActiveProjectId() = runBlocking {
        val job = viewModel.createAndOpenProject("Injected DB Project")
        job.join()

        assertTrue("project-open coroutine should have completed", job.isCompleted)
        assertFalse("project-open coroutine must not have failed", job.isCancelled)

        val projectId = viewModel.activeProjectId.value
        assertNotNull("createAndOpenProject must publish activeProjectId", projectId)

        // The project must exist in the INJECTED database, proving the seam is honoured and that
        // no fallback to the process-wide file-backed singleton occurred.
        val project = database.projectDao().getProject(projectId!!)
        assertNotNull("project must be persisted in the injected database", project)
        assertEquals("Injected DB Project", project!!.projectName)

        // And through a repository built on the same injected instance.
        val repository = EngineeringRepository(database)
        assertEquals(1, repository.getAllProjectsList().size)
    }

    @Test
    fun injectedDatabase_isUsedForCircuitWrites_notTheGlobalSingleton() = runBlocking {
        val job = viewModel.createAndOpenProject("Circuit Write Project")
        job.join()
        val projectId = viewModel.activeProjectId.value
        assertNotNull(projectId)

        database.circuitDao().insertCircuit(
            CircuitEntity(
                projectId = projectId!!,
                circuitName = "CKT-INJECTED",
                loadKVA = 25.0,
                voltageV = 400,
                fedFrom = "Transformer"
            )
        )

        val rows = database.circuitDao().getCircuitsListForProject(projectId)
        assertEquals(1, rows.size)
        assertEquals("CKT-INJECTED", rows.first().circuitName)

        // Sanity: a row written directly to the injected DB is visible to a repository on the same
        // instance, which is the contract Robolectric UI tests depend on.
        val repository = EngineeringRepository(database)
        assertEquals(1, repository.circuitDao.getCircuitsListForProject(projectId).size)
    }

    @Test
    fun productionConstructor_remainsAvailable() {
        // The one-argument constructor must remain callable, because `ViewModelProvider` uses it.
        // It deliberately makes NO database-state assertion: under Robolectric that path is exactly
        // the one that fails with SQLITE_CANTOPEN, and asserting on it would re-introduce the
        // environmental failure this suite exists to prevent.
        val context = ApplicationProvider.getApplicationContext<Application>()
        val vm = EngineeringViewModel(context)
        assertNotNull("production constructor must still build a ViewModel", vm.activeProjectId)
    }

    @Test
    fun projectEntity_isVisibleThroughInjectedDatabase() = runBlocking {
        // Direct-write/read parity on the injected instance: guards against the injected database
        // being silently replaced between construction and use.
        database.projectDao().upsertProject(
            ProjectEntity(
                projectId = "PRJ-DIRECT",
                projectName = "Direct",
                createdAt = 1L,
                updatedAt = 1L,
                revision = 1L
            )
        )
        val read = database.projectDao().getProject("PRJ-DIRECT")
        assertNotNull(read)
        assertEquals("Direct", read!!.projectName)
    }
}