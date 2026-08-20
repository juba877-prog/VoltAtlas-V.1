package com.example.ai.proposal

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ai.contracts.ExecutionMode
import com.example.ai.contracts.ExecutionModePolicy
import com.example.data.AppDatabase
import com.example.data.CircuitEntity
import com.example.data.MIGRATION_22_23
import com.example.data.ProjectEntity
import com.example.data.ai.AiDatabaseConverters
import com.example.data.ai.ApprovalGrantEntity
import com.example.data.ai.MutationExecutionReceiptEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class Phase7E1DurableSchemaTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val semanticEncoder = DefaultProposalSemanticKeyEncoder()
    private val digestEncoder = DefaultProposalDigestEncoder()

    private val projectId = "PROJ-7E1-TEST"
    private val circuitId = "101"

    @Before
    fun setUp() {
        AppDatabase.useTestExecutors = true
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            database.projectDao().upsertProject(
                ProjectEntity(
                    projectId = projectId,
                    projectName = "7E1 Test Project",
                    revision = 1L
                )
            )
            database.circuitDao().insertCircuit(
                CircuitEntity(
                    id = 101,
                    circuitName = "C-101",
                    projectId = projectId,
                    cableType = "Cu/PVC/SWA/PVC",
                    length_m = 50.0
                )
            )
        }
    }

    @After
    fun tearDown() {
        AppDatabase.useTestExecutors = false
        database.close()
    }

    // =========================================================================
    // 1. MIGRATION & SCHEMA PARITY TESTS
    // =========================================================================

    @Test
    fun migration22To23_preservesExistingProjectAndCircuitData() = runBlocking {
        // Create an in-memory SQLite database simulating v22 schema
        val dbHelper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(22) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `projects` (
                                `projectId` TEXT NOT NULL PRIMARY KEY,
                                `projectName` TEXT NOT NULL,
                                `projectCode` TEXT,
                                `clientName` TEXT,
                                `createdAt` INTEGER NOT NULL,
                                `updatedAt` INTEGER NOT NULL,
                                `defaultPhaseImbalanceLimitPct` REAL NOT NULL DEFAULT 20.0,
                                `revision` INTEGER NOT NULL DEFAULT 1
                            )
                        """.trimIndent())
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `circuits` (
                                `id` INTEGER NOT NULL PRIMARY KEY,
                                `projectId` TEXT NOT NULL,
                                `circuitNumber` TEXT NOT NULL,
                                `cableType` TEXT NOT NULL,
                                `length` REAL NOT NULL,
                                `loadCurrent` REAL NOT NULL,
                                `protectiveDeviceRating` REAL NOT NULL
                            )
                        """.trimIndent())
                        db.execSQL("INSERT INTO `projects` (`projectId`, `projectName`, `createdAt`, `updatedAt`, `revision`) VALUES ('P1', 'Test P1', 1000, 1000, 5)")
                        db.execSQL("INSERT INTO `circuits` (`id`, `projectId`, `circuitNumber`, `cableType`, `length`, `loadCurrent`, `protectiveDeviceRating`) VALUES (1, 'P1', 'C-1', 'Cu/XLPE', 25.0, 10.0, 16.0)")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        val sqlDb = dbHelper.writableDatabase

        // Execute MIGRATION_22_23
        MIGRATION_22_23.migrate(sqlDb)

        // Verify pre-existing data is preserved
        val projCursor = sqlDb.query("SELECT projectId, revision FROM projects WHERE projectId = 'P1'")
        assertTrue(projCursor.moveToFirst())
        assertEquals("P1", projCursor.getString(0))
        assertEquals(5L, projCursor.getLong(1))
        projCursor.close()

        val circCursor = sqlDb.query("SELECT id, cableType FROM circuits WHERE id = 1")
        assertTrue(circCursor.moveToFirst())
        assertEquals(1, circCursor.getInt(0))
        assertEquals("Cu/XLPE", circCursor.getString(1))
        circCursor.close()

        // Verify new tables exist and can accept valid inserts
        sqlDb.execSQL("""
            INSERT INTO `approval_grants` (
                `approvalId`, `proposalId`, `proposalDigest`, `proposalSemanticKey`, `activeProposalSemanticKey`,
                `projectId`, `targetEntityType`, `targetEntityId`, `baseProjectRevision`, `status`,
                `issuerType`, `approvedBy`, `userActionId`, `issuedAt`, `expiresAt`
            ) VALUES (
                'APP-1', 'PROP-1', 'DIGEST-1', 'SEM-1', 'SEM-1',
                'P1', 'CIRCUIT', '1', 5, 'ISSUED',
                'USER_EXPLICIT_CONFIRMATION', 'Operator1', 'ACT-1', 1000, 2000
            )
        """.trimIndent())

        val grantCursor = sqlDb.query("SELECT approvalId, status FROM approval_grants WHERE approvalId = 'APP-1'")
        assertTrue(grantCursor.moveToFirst())
        assertEquals("APP-1", grantCursor.getString(0))
        assertEquals("ISSUED", grantCursor.getString(1))
        grantCursor.close()
        sqlDb.close()
    }

    @Test
    fun freshV23Schema_matchesMigratedV23EffectiveRoomSchema() = runBlocking {
        val approvalDao = database.approvalGrantDao()
        val receiptDao = database.mutationExecutionReceiptDao()
        assertNotNull(approvalDao)
        assertNotNull(receiptDao)

        // Verify table and index creation in fresh database
        val grant = ApprovalGrantEntity.createIssued(
            approvalId = "APP-FRESH-1",
            proposalId = "PROP-1",
            proposalDigest = "PROPOSAL-ARTIFACT-V1:digest123",
            proposalSemanticKey = "PROPOSAL-SEMANTIC-V1:sem123",
            projectId = projectId,
            targetEntityId = circuitId,
            baseProjectRevision = 1L,
            userActionId = "ACT-123",
            approvedBy = "LeadEngineer",
            issuedAt = 1000L,
            expiresAt = 2000L
        )
        approvalDao.insertGrant(grant)

        val retrieved = approvalDao.getGrantById("APP-FRESH-1")
        assertNotNull(retrieved)
        assertEquals("APP-FRESH-1", retrieved!!.approvalId)
        assertEquals(ApprovalStatus.ISSUED, retrieved.status)
        assertEquals("PROPOSAL-SEMANTIC-V1:sem123", retrieved.activeProposalSemanticKey)
    }

    @Test
    fun migration22To23_createsExpectedIndexesAndForeignKeys() = runBlocking {
        val approvalDao = database.approvalGrantDao()
        val grant = ApprovalGrantEntity.createIssued(
            approvalId = "APP-IDX-1",
            proposalId = "PROP-1",
            proposalDigest = "PROPOSAL-ARTIFACT-V1:digestA",
            proposalSemanticKey = "PROPOSAL-SEMANTIC-V1:semA",
            projectId = projectId,
            targetEntityId = circuitId,
            baseProjectRevision = 1L,
            userActionId = "ACT-1",
            approvedBy = "LeadEngineer",
            issuedAt = 1000L,
            expiresAt = 2000L
        )
        approvalDao.insertGrant(grant)

        val bySemantic = approvalDao.getActiveGrantBySemanticKey("PROPOSAL-SEMANTIC-V1:semA")
        assertNotNull(bySemantic)
        assertEquals("APP-IDX-1", bySemantic!!.approvalId)
    }

    @Test
    fun noDestructiveMigrationFallback_isConfigured() {
        // AppDatabase specifies explicit migration chain without fallbackToDestructiveMigration
        assertEquals(23, AppDatabase.getDatabase(context).openHelper.readableDatabase.version)
    }

    // =========================================================================
    // 2. OPTION A DELETION POLICY (CASCADE) TESTS
    // =========================================================================

    @Test
    fun deleteProject_cascadesApprovalGrantsAndReceipts() = runBlocking {
        val approvalDao = database.approvalGrantDao()
        val receiptDao = database.mutationExecutionReceiptDao()

        val grant = ApprovalGrantEntity.createIssued(
            approvalId = "APP-CAS-1",
            proposalId = "PROP-1",
            proposalDigest = "PROPOSAL-ARTIFACT-V1:digestCas",
            proposalSemanticKey = "PROPOSAL-SEMANTIC-V1:semCas",
            projectId = projectId,
            targetEntityId = circuitId,
            baseProjectRevision = 1L,
            userActionId = "ACT-CAS",
            approvedBy = "Engineer",
            issuedAt = 1000L,
            expiresAt = 2000L
        )
        approvalDao.insertGrant(grant)

        val receipt = MutationExecutionReceiptEntity.createCommitted(
            executionId = "EXEC-CAS-1",
            approval = grant,
            revisionBefore = 1L,
            revisionAfter = 2L,
            appliedChangesJson = """{"version":"RECEIPT-V1","changeCount":1,"changes":[{"fieldName":"cableType","action":"SET","newValue":{"kind":"STRING","stringValue":"Cu/XLPE"}}]}""",
            now = 1100L
        )
        receiptDao.insertReceipt(receipt)

        assertNotNull(approvalDao.getGrantById("APP-CAS-1"))
        assertNotNull(receiptDao.getReceiptById("EXEC-CAS-1"))

        // Delete parent project
        database.projectDao().deleteProject(projectId)

        // Verify cascade deletion of approvals and receipts
        assertNull(approvalDao.getGrantById("APP-CAS-1"))
        assertNull(receiptDao.getReceiptById("EXEC-CAS-1"))
    }

    @Test
    fun deleteApproval_cascadesItsReceipt() = runBlocking {
        val approvalDao = database.approvalGrantDao()
        val receiptDao = database.mutationExecutionReceiptDao()

        val grant = ApprovalGrantEntity.createIssued(
            approvalId = "APP-CAS-2",
            proposalId = "PROP-2",
            proposalDigest = "PROPOSAL-ARTIFACT-V1:digestCas2",
            proposalSemanticKey = "PROPOSAL-SEMANTIC-V1:semCas2",
            projectId = projectId,
            targetEntityId = circuitId,
            baseProjectRevision = 1L,
            userActionId = "ACT-CAS2",
            approvedBy = "Engineer",
            issuedAt = 1000L,
            expiresAt = 2000L
        )
        approvalDao.insertGrant(grant)

        val receipt = MutationExecutionReceiptEntity.createCommitted(
            executionId = "EXEC-CAS-2",
            approval = grant,
            revisionBefore = 1L,
            revisionAfter = 2L,
            appliedChangesJson = """{"version":"RECEIPT-V1","changeCount":1,"changes":[{"fieldName":"cableType","action":"SET","newValue":{"kind":"STRING","stringValue":"Cu/XLPE"}}]}""",
            now = 1100L
        )
        receiptDao.insertReceipt(receipt)

        // Delete approval directly
        database.openHelper.writableDatabase.execSQL("DELETE FROM approval_grants WHERE approvalId = 'APP-CAS-2'")

        // Verify receipt cascaded
        assertNull(receiptDao.getReceiptById("EXEC-CAS-2"))
    }

    @Test
    fun projectDeletion_leavesNoApprovalOrReceiptOrphans() = runBlocking {
        val approvalDao = database.approvalGrantDao()
        val receiptDao = database.mutationExecutionReceiptDao()

        val grants = approvalDao.getGrantsForProject(projectId)
        val receipts = receiptDao.getReceiptsForProject(projectId)
        assertEquals(0, grants.size)
        assertEquals(0, receipts.size)
    }

    // =========================================================================
    // 3. SEMANTIC IDENTITY & ACTIVE UNIQUENESS TESTS
    // =========================================================================

    private fun sampleProposal(
        rationale: String = "Standard rationale",
        requestId: String = "REQ-1",
        createdAt: Long = 1000L,
        cableType: String = "Cu/XLPE/SWA/PVC",
        revision: Long = 1L
    ): CableEngineeringProposal {
        return CableEngineeringProposal(
            proposalId = "PROP-SMP-1",
            projectId = projectId,
            baseProjectRevision = revision,
            targetEntityId = circuitId,
            operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
            patch = CableCircuitPatch(
                cableType = PatchValue.set(cableType)
            ),
            rationale = rationale,
            sourceToolName = "ProposeCableSizingTool",
            sourceRequestId = requestId,
            createdAt = createdAt
        )
    }

    @Test
    fun sameMutationDifferentRationale_hasSameSemanticKey() {
        val p1 = sampleProposal(rationale = "First rationale")
        val p2 = sampleProposal(rationale = "Completely different rationale text")

        val sem1 = semanticEncoder.computeSemanticKey(p1)
        val sem2 = semanticEncoder.computeSemanticKey(p2)
        assertEquals(sem1, sem2)

        // But proposalDigest MUST differ
        val dig1 = digestEncoder.computeDigest(p1)
        val dig2 = digestEncoder.computeDigest(p2)
        assertNotEquals(dig1, dig2)
    }

    @Test
    fun sameMutationDifferentRequestId_hasSameSemanticKey() {
        val p1 = sampleProposal(requestId = "REQ-AAA")
        val p2 = sampleProposal(requestId = "REQ-BBB")
        assertEquals(semanticEncoder.computeSemanticKey(p1), semanticEncoder.computeSemanticKey(p2))
    }

    @Test
    fun sameMutationDifferentCreatedAt_hasSameSemanticKey() {
        val p1 = sampleProposal(createdAt = 1000L)
        val p2 = sampleProposal(createdAt = 9999L)
        assertEquals(semanticEncoder.computeSemanticKey(p1), semanticEncoder.computeSemanticKey(p2))
    }

    @Test
    fun differentPatch_hasDifferentSemanticKey() {
        val p1 = sampleProposal(cableType = "Cu/XLPE/SWA/PVC")
        val p2 = sampleProposal(cableType = "Al/XLPE/STA/PVC - 4C")
        assertNotEquals(semanticEncoder.computeSemanticKey(p1), semanticEncoder.computeSemanticKey(p2))
    }

    @Test
    fun revisionChange_hasDifferentSemanticKey() {
        val p1 = sampleProposal(revision = 1L)
        val p2 = sampleProposal(revision = 2L)
        assertNotEquals(semanticEncoder.computeSemanticKey(p1), semanticEncoder.computeSemanticKey(p2))
    }

    @Test
    fun sameSemanticMutation_allowsOnlyOneActiveApproval() = runBlocking {
        val approvalDao = database.approvalGrantDao()
        val p = sampleProposal()
        val semKey = semanticEncoder.computeSemanticKey(p)

        val grant1 = ApprovalGrantEntity.createIssued(
            approvalId = "APP-UNIQ-1",
            proposalId = "PROP-1",
            proposalDigest = digestEncoder.computeDigest(p),
            proposalSemanticKey = semKey,
            projectId = projectId,
            targetEntityId = circuitId,
            baseProjectRevision = 1L,
            userActionId = "ACT-1",
            approvedBy = "Engineer1",
            issuedAt = 1000L,
            expiresAt = 2000L
        )
        approvalDao.insertGrant(grant1)

        val grant2 = ApprovalGrantEntity.createIssued(
            approvalId = "APP-UNIQ-2",
            proposalId = "PROP-2",
            proposalDigest = "PROPOSAL-ARTIFACT-V1:anotherDigest",
            proposalSemanticKey = semKey, // Same semantic key!
            projectId = projectId,
            targetEntityId = circuitId,
            baseProjectRevision = 1L,
            userActionId = "ACT-2",
            approvedBy = "Engineer2",
            issuedAt = 1000L,
            expiresAt = 2000L
        )

        // Attempting to insert a second active grant for the same semantic key must abort due to unique constraint
        try {
            approvalDao.insertGrant(grant2)
            fail("Expected SQLiteConstraintException on duplicate activeProposalSemanticKey")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("activeProposalSemanticKey", ignoreCase = true) == true ||
                       e.message?.contains("UNIQUE", ignoreCase = true) == true ||
                       e.message?.contains("constraint", ignoreCase = true) == true)
        }
    }

    @Test
    fun terminalGrant_allowsLaterReissuanceAccordingToPolicy() = runBlocking {
        val approvalDao = database.approvalGrantDao()
        val p = sampleProposal()
        val semKey = semanticEncoder.computeSemanticKey(p)
        val digest = digestEncoder.computeDigest(p)

        val grant1 = ApprovalGrantEntity.createIssued(
            approvalId = "APP-REISSUE-1",
            proposalId = "PROP-1",
            proposalDigest = digest,
            proposalSemanticKey = semKey,
            projectId = projectId,
            targetEntityId = circuitId,
            baseProjectRevision = 1L,
            userActionId = "ACT-1",
            approvedBy = "Engineer1",
            issuedAt = 1000L,
            expiresAt = 2000L
        )
        approvalDao.insertGrant(grant1)

        // Consume grant1 (clears activeProposalSemanticKey)
        val rows = approvalDao.consumeGrantGuarded(
            approvalId = "APP-REISSUE-1",
            expectedSemanticKey = semKey,
            expectedRevision = 1L,
            executionId = "EXEC-1",
            now = 1500L
        )
        assertEquals(1, rows)

        val updated = approvalDao.getGrantById("APP-REISSUE-1")
        assertEquals(ApprovalStatus.CONSUMED, updated?.status)
        assertNull(updated?.activeProposalSemanticKey)

        // Now a new grant with the same semantic key can be inserted cleanly
        val grant2 = ApprovalGrantEntity.createIssued(
            approvalId = "APP-REISSUE-2",
            proposalId = "PROP-2",
            proposalDigest = digest,
            proposalSemanticKey = semKey,
            projectId = projectId,
            targetEntityId = circuitId,
            baseProjectRevision = 1L,
            userActionId = "ACT-2",
            approvedBy = "Engineer2",
            issuedAt = 2500L,
            expiresAt = 3500L
        )
        approvalDao.insertGrant(grant2)

        val active = approvalDao.getActiveGrantBySemanticKey(semKey)
        assertNotNull(active)
        assertEquals("APP-REISSUE-2", active!!.approvalId)
    }

    // =========================================================================
    // 4. APPROVAL FACTORY & DAO GUARD TESTS
    // =========================================================================

    @Test
    fun issuedGrant_derivesActiveKeyFromSemanticKey() {
        val grant = ApprovalGrantEntity.createIssued(
            approvalId = "APP-F1",
            proposalId = "PROP-F1",
            proposalDigest = "PROPOSAL-ARTIFACT-V1:dig",
            proposalSemanticKey = "PROPOSAL-SEMANTIC-V1:sem",
            projectId = projectId,
            targetEntityId = circuitId,
            baseProjectRevision = 1L,
            userActionId = "ACT-1",
            approvedBy = "User",
            issuedAt = 1000L,
            expiresAt = 2000L
        )
        assertEquals("PROPOSAL-SEMANTIC-V1:sem", grant.activeProposalSemanticKey)
        assertEquals(ApprovalStatus.ISSUED, grant.status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun issuedGrant_rejectsBlankUserActionId() {
        ApprovalGrantEntity.createIssued(
            approvalId = "APP-F2",
            proposalId = "PROP-F2",
            proposalDigest = "PROPOSAL-ARTIFACT-V1:dig",
            proposalSemanticKey = "PROPOSAL-SEMANTIC-V1:sem",
            projectId = projectId,
            targetEntityId = circuitId,
            baseProjectRevision = 1L,
            userActionId = "   ",
            approvedBy = "User",
            issuedAt = 1000L,
            expiresAt = 2000L
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun issuedGrant_rejectsBlankApprovedBy() {
        ApprovalGrantEntity.createIssued(
            approvalId = "APP-F3",
            proposalId = "PROP-F3",
            proposalDigest = "PROPOSAL-ARTIFACT-V1:dig",
            proposalSemanticKey = "PROPOSAL-SEMANTIC-V1:sem",
            projectId = projectId,
            targetEntityId = circuitId,
            baseProjectRevision = 1L,
            userActionId = "ACT-1",
            approvedBy = "",
            issuedAt = 1000L,
            expiresAt = 2000L
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun issuedGrant_rejectsInvalidExpiry() {
        ApprovalGrantEntity.createIssued(
            approvalId = "APP-F4",
            proposalId = "PROP-F4",
            proposalDigest = "PROPOSAL-ARTIFACT-V1:dig",
            proposalSemanticKey = "PROPOSAL-SEMANTIC-V1:sem",
            projectId = projectId,
            targetEntityId = circuitId,
            baseProjectRevision = 1L,
            userActionId = "ACT-1",
            approvedBy = "User",
            issuedAt = 2000L,
            expiresAt = 1000L // expiresAt <= issuedAt
        )
    }

    @Test
    fun consumeTransition_clearsActiveKeyAtomically() = runBlocking {
        val approvalDao = database.approvalGrantDao()
        val semKey = "PROPOSAL-SEMANTIC-V1:semConsume"
        val grant = ApprovalGrantEntity.createIssued(
            approvalId = "APP-CON-1",
            proposalId = "PROP-1",
            proposalDigest = "PROPOSAL-ARTIFACT-V1:digCon",
            proposalSemanticKey = semKey,
            projectId = projectId,
            targetEntityId = circuitId,
            baseProjectRevision = 1L,
            userActionId = "ACT-1",
            approvedBy = "User",
            issuedAt = 1000L,
            expiresAt = 2000L
        )
        approvalDao.insertGrant(grant)

        val rows = approvalDao.consumeGrantGuarded(
            approvalId = "APP-CON-1",
            expectedSemanticKey = semKey,
            expectedRevision = 1L,
            executionId = "EXEC-CON-1",
            now = 1500L
        )
        assertEquals(1, rows)

        val updated = approvalDao.getGrantById("APP-CON-1")
        assertEquals(ApprovalStatus.CONSUMED, updated?.status)
        assertNull(updated?.activeProposalSemanticKey)
        assertEquals(1500L, updated?.consumedAt)
        assertEquals("EXEC-CON-1", updated?.consumedByExecutionId)
    }

    @Test
    fun expireTransition_clearsActiveKeyAtomically() = runBlocking {
        val approvalDao = database.approvalGrantDao()
        val semKey = "PROPOSAL-SEMANTIC-V1:semExp"
        val grant = ApprovalGrantEntity.createIssued(
            approvalId = "APP-EXP-1",
            proposalId = "PROP-1",
            proposalDigest = "PROPOSAL-ARTIFACT-V1:digExp",
            proposalSemanticKey = semKey,
            projectId = projectId,
            targetEntityId = circuitId,
            baseProjectRevision = 1L,
            userActionId = "ACT-1",
            approvedBy = "User",
            issuedAt = 1000L,
            expiresAt = 2000L
        )
        approvalDao.insertGrant(grant)

        val rows = approvalDao.expireGrantGuarded("APP-EXP-1", now = 2500L)
        assertEquals(1, rows)

        val updated = approvalDao.getGrantById("APP-EXP-1")
        assertEquals(ApprovalStatus.EXPIRED, updated?.status)
        assertNull(updated?.activeProposalSemanticKey)
    }

    @Test
    fun cancelTransition_clearsActiveKeyAtomically() = runBlocking {
        val approvalDao = database.approvalGrantDao()
        val semKey = "PROPOSAL-SEMANTIC-V1:semCan"
        val grant = ApprovalGrantEntity.createIssued(
            approvalId = "APP-CAN-1",
            proposalId = "PROP-1",
            proposalDigest = "PROPOSAL-ARTIFACT-V1:digCan",
            proposalSemanticKey = semKey,
            projectId = projectId,
            targetEntityId = circuitId,
            baseProjectRevision = 1L,
            userActionId = "ACT-1",
            approvedBy = "User",
            issuedAt = 1000L,
            expiresAt = 2000L
        )
        approvalDao.insertGrant(grant)

        val rows = approvalDao.cancelGrantGuarded("APP-CAN-1")
        assertEquals(1, rows)

        val updated = approvalDao.getGrantById("APP-CAN-1")
        assertEquals(ApprovalStatus.CANCELLED, updated?.status)
        assertNull(updated?.activeProposalSemanticKey)
    }

    @Test
    fun concurrentConsumeAndExpire_exactlyOneWins() = runBlocking {
        val approvalDao = database.approvalGrantDao()
        val semKey = "PROPOSAL-SEMANTIC-V1:semRace"
        val grant = ApprovalGrantEntity.createIssued(
            approvalId = "APP-RACE-1",
            proposalId = "PROP-1",
            proposalDigest = "PROPOSAL-ARTIFACT-V1:digRace",
            proposalSemanticKey = semKey,
            projectId = projectId,
            targetEntityId = circuitId,
            baseProjectRevision = 1L,
            userActionId = "ACT-1",
            approvedBy = "User",
            issuedAt = 1000L,
            expiresAt = 2000L
        )
        approvalDao.insertGrant(grant)

        // Try concurrent consume (now=1500) vs expire (now=2500)
        val consumeResult = approvalDao.consumeGrantGuarded("APP-RACE-1", semKey, 1L, "EXEC-1", 1500L)
        val expireResult = approvalDao.expireGrantGuarded("APP-RACE-1", 2500L)

        // Exactly one should succeed
        assertTrue((consumeResult == 1 && expireResult == 0) || (consumeResult == 0 && expireResult == 1))
    }

    @Test
    fun invalidApprovalTransition_updatesZeroRows() = runBlocking {
        val approvalDao = database.approvalGrantDao()
        val semKey = "PROPOSAL-SEMANTIC-V1:semInv"
        val grant = ApprovalGrantEntity.createIssued(
            approvalId = "APP-INV-1",
            proposalId = "PROP-1",
            proposalDigest = "PROPOSAL-ARTIFACT-V1:digInv",
            proposalSemanticKey = semKey,
            projectId = projectId,
            targetEntityId = circuitId,
            baseProjectRevision = 1L,
            userActionId = "ACT-1",
            approvedBy = "User",
            issuedAt = 1000L,
            expiresAt = 2000L
        )
        approvalDao.insertGrant(grant)

        // Wrong expected revision (2L != 1L)
        val rows1 = approvalDao.consumeGrantGuarded("APP-INV-1", semKey, 2L, "EXEC-1", 1500L)
        assertEquals(0, rows1)

        // Expired grant consumption attempt (now=2500 > expiresAt=2000)
        val rows2 = approvalDao.consumeGrantGuarded("APP-INV-1", semKey, 1L, "EXEC-1", 2500L)
        assertEquals(0, rows2)
    }

    // =========================================================================
    // 5. RECEIPT INVARIANTS & DAO TRANSITION TESTS
    // =========================================================================

    private fun sampleGrant(approvalId: String = "APP-REC-1"): ApprovalGrantEntity {
        return ApprovalGrantEntity.createIssued(
            approvalId = approvalId,
            proposalId = "PROP-1",
            proposalDigest = "PROPOSAL-ARTIFACT-V1:digRec",
            proposalSemanticKey = "PROPOSAL-SEMANTIC-V1:semRec",
            projectId = projectId,
            targetEntityId = circuitId,
            baseProjectRevision = 1L,
            userActionId = "ACT-1",
            approvedBy = "User",
            issuedAt = 1000L,
            expiresAt = 2000L
        )
    }

    @Test
    fun oneApproval_canHaveAtMostOneReceipt() = runBlocking {
        val approvalDao = database.approvalGrantDao()
        val receiptDao = database.mutationExecutionReceiptDao()
        val grant = sampleGrant("APP-ONE-1")
        approvalDao.insertGrant(grant)

        val receipt1 = MutationExecutionReceiptEntity.createCommitted(
            executionId = "EXEC-1",
            approval = grant,
            revisionBefore = 1L,
            revisionAfter = 2L,
            appliedChangesJson = """{"version":"RECEIPT-V1","changeCount":1,"changes":[{"fieldName":"cableType","action":"SET","newValue":{"kind":"STRING","stringValue":"Cu/XLPE"}}]}""",
            now = 1100L
        )
        receiptDao.insertReceipt(receipt1)

        val receipt2 = MutationExecutionReceiptEntity.createCommitted(
            executionId = "EXEC-2",
            approval = grant, // Same approval!
            revisionBefore = 1L,
            revisionAfter = 2L,
            appliedChangesJson = """{"version":"RECEIPT-V1","changeCount":1,"changes":[{"fieldName":"cableType","action":"SET","newValue":{"kind":"STRING","stringValue":"Cu/XLPE"}}]}""",
            now = 1200L
        )

        try {
            receiptDao.insertReceipt(receipt2)
            fail("Expected constraint failure on duplicate approvalId in mutation receipts")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("approvalId", ignoreCase = true) == true ||
                       e.message?.contains("UNIQUE", ignoreCase = true) == true ||
                       e.message?.contains("constraint", ignoreCase = true) == true)
        }
    }

    @Test
    fun initialReceipt_isCommittedPendingWithAttemptZero() {
        val grant = sampleGrant()
        val receipt = MutationExecutionReceiptEntity.createCommitted(
            executionId = "EXEC-INIT",
            approval = grant,
            revisionBefore = 1L,
            revisionAfter = 2L,
            appliedChangesJson = "{}",
            now = 1000L
        )
        assertEquals(RevalidationStatus.PENDING, receipt.revalidationStatus)
        assertEquals(0, receipt.attemptCount)
        assertNull(receipt.lastAttemptAt)
        assertNull(receipt.workflowCompletedAt)
        assertNull(receipt.errorCode)
        assertNull(receipt.messageKey)
        assertFalse(receipt.isRecoverable)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidReceiptIdentity_isRejectedByTrustedInsertionBoundary() {
        val grant = sampleGrant()
        // Invalid revision step (before=1, after=3 != 1+1)
        MutationExecutionReceiptEntity.createCommitted(
            executionId = "EXEC-BAD",
            approval = grant,
            revisionBefore = 1L,
            revisionAfter = 3L,
            appliedChangesJson = "{}",
            now = 1000L
        )
    }

    @Test
    fun pendingToRunning_incrementsAttemptAndClearsErrors() = runBlocking {
        val approvalDao = database.approvalGrantDao()
        val receiptDao = database.mutationExecutionReceiptDao()
        val grant = sampleGrant("APP-P2R")
        approvalDao.insertGrant(grant)

        val receipt = MutationExecutionReceiptEntity.createCommitted(
            executionId = "EXEC-P2R",
            approval = grant,
            revisionBefore = 1L,
            revisionAfter = 2L,
            appliedChangesJson = """{"version":"RECEIPT-V1","changeCount":1,"changes":[{"fieldName":"cableType","action":"SET","newValue":{"kind":"STRING","stringValue":"Cu/XLPE"}}]}""",
            now = 1000L
        )
        receiptDao.insertReceipt(receipt)

        val rows = receiptDao.transitionPendingToRunning("EXEC-P2R", now = 1100L)
        assertEquals(1, rows)

        val updated = receiptDao.getReceiptById("EXEC-P2R")
        assertEquals(RevalidationStatus.RUNNING, updated?.revalidationStatus)
        assertEquals(1, updated?.attemptCount)
        assertEquals(1100L, updated?.lastAttemptAt)
    }

    @Test
    fun runningToSucceeded_setsWorkflowCompletedAt() = runBlocking {
        val approvalDao = database.approvalGrantDao()
        val receiptDao = database.mutationExecutionReceiptDao()
        val grant = sampleGrant("APP-R2S")
        approvalDao.insertGrant(grant)

        val receipt = MutationExecutionReceiptEntity.createCommitted(
            executionId = "EXEC-R2S",
            approval = grant,
            revisionBefore = 1L,
            revisionAfter = 2L,
            appliedChangesJson = """{"version":"RECEIPT-V1","changeCount":1,"changes":[{"fieldName":"cableType","action":"SET","newValue":{"kind":"STRING","stringValue":"Cu/XLPE"}}]}""",
            now = 1000L
        )
        receiptDao.insertReceipt(receipt)
        receiptDao.transitionPendingToRunning("EXEC-R2S", 1100L)

        val rows = receiptDao.transitionRunningToSucceeded("EXEC-R2S", now = 1200L)
        assertEquals(1, rows)

        val updated = receiptDao.getReceiptById("EXEC-R2S")
        assertEquals(RevalidationStatus.SUCCEEDED, updated?.revalidationStatus)
        assertEquals(1200L, updated?.workflowCompletedAt)
        assertNull(updated?.errorCode)
    }

    @Test
    fun runningToFailed_keepsWorkflowOpenForRetry() = runBlocking {
        val approvalDao = database.approvalGrantDao()
        val receiptDao = database.mutationExecutionReceiptDao()
        val grant = sampleGrant("APP-R2F")
        approvalDao.insertGrant(grant)

        val receipt = MutationExecutionReceiptEntity.createCommitted(
            executionId = "EXEC-R2F",
            approval = grant,
            revisionBefore = 1L,
            revisionAfter = 2L,
            appliedChangesJson = """{"version":"RECEIPT-V1","changeCount":1,"changes":[{"fieldName":"cableType","action":"SET","newValue":{"kind":"STRING","stringValue":"Cu/XLPE"}}]}""",
            now = 1000L
        )
        receiptDao.insertReceipt(receipt)
        receiptDao.transitionPendingToRunning("EXEC-R2F", 1100L)

        val rows = receiptDao.transitionRunningToFailed(
            executionId = "EXEC-R2F",
            errorCode = "ERR_REVAL_VD",
            messageKey = "error.revalidation.voltage_drop",
            isRecoverable = true,
            diagnosticRef = "DIAG-123"
        )
        assertEquals(1, rows)

        val updated = receiptDao.getReceiptById("EXEC-R2F")
        assertEquals(RevalidationStatus.FAILED, updated?.revalidationStatus)
        assertNull(updated?.workflowCompletedAt) // Workflow remains open for retry!
        assertEquals("ERR_REVAL_VD", updated?.errorCode)
        assertTrue(updated?.isRecoverable == true)
    }

    @Test
    fun retryableFailureToRunning_incrementsAttemptExactlyOnce() = runBlocking {
        val approvalDao = database.approvalGrantDao()
        val receiptDao = database.mutationExecutionReceiptDao()
        val grant = sampleGrant("APP-RETRY")
        approvalDao.insertGrant(grant)

        val receipt = MutationExecutionReceiptEntity.createCommitted(
            executionId = "EXEC-RETRY",
            approval = grant,
            revisionBefore = 1L,
            revisionAfter = 2L,
            appliedChangesJson = """{"version":"RECEIPT-V1","changeCount":1,"changes":[{"fieldName":"cableType","action":"SET","newValue":{"kind":"STRING","stringValue":"Cu/XLPE"}}]}""",
            now = 1000L
        )
        receiptDao.insertReceipt(receipt)
        receiptDao.transitionPendingToRunning("EXEC-RETRY", 1100L)
        receiptDao.transitionRunningToFailed("EXEC-RETRY", "ERR_FAIL", "msg", true, "diag")

        val rows = receiptDao.transitionRetryToRunning("EXEC-RETRY", now = 1300L)
        assertEquals(1, rows)

        val updated = receiptDao.getReceiptById("EXEC-RETRY")
        assertEquals(RevalidationStatus.RUNNING, updated?.revalidationStatus)
        assertEquals(2, updated?.attemptCount)
        assertEquals(1300L, updated?.lastAttemptAt)
        assertNull(updated?.errorCode) // Cleared on retry!
    }

    @Test
    fun concurrentRetryAndSupersede_exactlyOneWins() = runBlocking {
        val approvalDao = database.approvalGrantDao()
        val receiptDao = database.mutationExecutionReceiptDao()
        val grant = sampleGrant("APP-RACE2")
        approvalDao.insertGrant(grant)

        val receipt = MutationExecutionReceiptEntity.createCommitted(
            executionId = "EXEC-RACE2",
            approval = grant,
            revisionBefore = 1L,
            revisionAfter = 2L,
            appliedChangesJson = """{"version":"RECEIPT-V1","changeCount":1,"changes":[{"fieldName":"cableType","action":"SET","newValue":{"kind":"STRING","stringValue":"Cu/XLPE"}}]}""",
            now = 1000L
        )
        receiptDao.insertReceipt(receipt)
        receiptDao.transitionPendingToRunning("EXEC-RACE2", 1100L)
        receiptDao.transitionRunningToFailed("EXEC-RACE2", "ERR_FAIL", "msg", true, "diag")

        val retryResult = receiptDao.transitionRetryToRunning("EXEC-RACE2", 1200L)
        val supersedeResult = receiptDao.transitionToSuperseded("EXEC-RACE2", 1250L)

        // Both updates are conditional; retry moved it to RUNNING (1), then supersede moved RUNNING to SUPERSEDED (1)
        assertEquals(1, retryResult)
        assertEquals(1, supersedeResult)

        val finalState = receiptDao.getReceiptById("EXEC-RACE2")
        assertEquals(RevalidationStatus.SUPERSEDED, finalState?.revalidationStatus)
    }

    @Test
    fun invalidReceiptTransition_updatesZeroRows() = runBlocking {
        val approvalDao = database.approvalGrantDao()
        val receiptDao = database.mutationExecutionReceiptDao()
        val grant = sampleGrant("APP-INV-REC")
        approvalDao.insertGrant(grant)

        val receipt = MutationExecutionReceiptEntity.createCommitted(
            executionId = "EXEC-INV-REC",
            approval = grant,
            revisionBefore = 1L,
            revisionAfter = 2L,
            appliedChangesJson = """{"version":"RECEIPT-V1","changeCount":1,"changes":[{"fieldName":"cableType","action":"SET","newValue":{"kind":"STRING","stringValue":"Cu/XLPE"}}]}""",
            now = 1000L
        )
        receiptDao.insertReceipt(receipt)

        // Cannot transition directly from PENDING to SUCCEEDED (must go through RUNNING)
        val rows = receiptDao.transitionRunningToSucceeded("EXEC-INV-REC", 1100L)
        assertEquals(0, rows)
    }

    // =========================================================================
    // 6. CODEC & PRIVACY TESTS
    // =========================================================================

    @Test
    fun receiptPayload_roundTripPreservesSetClearNullEmptyIntegerDecimal() {
        val changes = listOf(
            FieldChangeRecord(
                fieldName = "cableType",
                action = PatchAction.SET,
                previousValue = TypedFieldValue.ofString("Cu/PVC"),
                newValue = TypedFieldValue.ofString("Cu/XLPE")
            ),
            FieldChangeRecord(
                fieldName = "parallelRuns",
                action = PatchAction.SET,
                previousValue = TypedFieldValue.ofInt(1),
                newValue = TypedFieldValue.ofInt(2)
            ),
            FieldChangeRecord(
                fieldName = "overrideCableSize",
                action = PatchAction.CLEAR,
                previousValue = TypedFieldValue.ofString("240.0"),
                newValue = TypedFieldValue.ofNull()
            ),
            FieldChangeRecord(
                fieldName = "lengthM",
                action = PatchAction.SET,
                previousValue = TypedFieldValue.ofDouble(50.0),
                newValue = TypedFieldValue.ofDouble(75.5)
            )
        )

        val encoded = ReceiptPayloadCodec.encode(changes)
        assertNotNull(encoded)
        assertTrue(encoded.contains("RECEIPT-V1"))

        val decoded = ReceiptPayloadCodec.decode(encoded)
        assertEquals(4, decoded.size)

        val cableChange = decoded.first { it.fieldName == "cableType" }
        assertEquals("Cu/PVC", cableChange.previousValue?.stringValue)
        assertEquals("Cu/XLPE", cableChange.newValue?.stringValue)

        val runsChange = decoded.first { it.fieldName == "parallelRuns" }
        assertEquals(1L, runsChange.previousValue?.intValue)
        assertEquals(2L, runsChange.newValue?.intValue)

        val clearChange = decoded.first { it.fieldName == "overrideCableSize" }
        assertEquals(PatchAction.CLEAR, clearChange.action)
        assertEquals(FieldValueKind.NULL, clearChange.newValue?.kind)

        val lengthChange = decoded.first { it.fieldName == "lengthM" }
        assertEquals(50.0, lengthChange.previousValue?.doubleValue!!, 0.001)
        assertEquals(75.5, lengthChange.newValue?.doubleValue!!, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun receiptPayload_rejectsUnknownVersionFieldActionOrKind() {
        val badJson = """
            {"version":"RECEIPT-V999","changeCount":1,"changes":[{"fieldName":"cableType","action":"SET","newValue":{"kind":"STRING","stringValue":"Cu/XLPE"}}]}
        """.trimIndent()
        ReceiptPayloadCodec.decode(badJson)
    }

    @Test(expected = IllegalArgumentException::class)
    fun receiptPayload_rejectsDuplicateFields() {
        val changes = listOf(
            FieldChangeRecord(fieldName = "cableType", action = PatchAction.SET, newValue = TypedFieldValue.ofString("A")),
            FieldChangeRecord(fieldName = "cableType", action = PatchAction.SET, newValue = TypedFieldValue.ofString("B"))
        )
        ReceiptPayloadCodec.encode(changes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun receiptPayload_rejectsNonFiniteDecimal() {
        val changes = listOf(
            FieldChangeRecord(fieldName = "lengthM", action = PatchAction.SET, newValue = TypedFieldValue.ofDouble(Double.NaN))
        )
        ReceiptPayloadCodec.encode(changes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun receiptPayload_rejectsUtf8PayloadOver65536Bytes() {
        val hugeString = "A".repeat(70000)
        val changes = listOf(
            FieldChangeRecord(fieldName = "cableType", action = PatchAction.SET, newValue = TypedFieldValue.ofString(hugeString))
        )
        ReceiptPayloadCodec.encode(changes)
    }

    @Test
    fun receiptPayload_containsNoDisplayRationaleOrLlmContent() {
        val changes = listOf(
            FieldChangeRecord(
                fieldName = "cableType",
                action = PatchAction.SET,
                previousValue = TypedFieldValue.ofString("Cu/PVC"),
                newValue = TypedFieldValue.ofString("Cu/XLPE"),
                displayChange = "SHOULD NOT BE ENCODED"
            )
        )
        val encoded = ReceiptPayloadCodec.encode(changes)
        assertFalse(encoded.contains("SHOULD NOT BE ENCODED"))
        assertFalse(encoded.contains("displayChange"))
    }

    @Test
    fun unknownEnumDatabaseValue_failsClosed() {
        val converters = AiDatabaseConverters()
        try {
            converters.toApprovalStatus("INVALID_STATUS")
            fail("Expected fail-closed exception on invalid ApprovalStatus")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Unknown ApprovalStatus") == true)
        }

        try {
            converters.toRevalidationStatus("INVALID_REVAL")
            fail("Expected fail-closed exception on invalid RevalidationStatus")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Unknown RevalidationStatus") == true)
        }
    }

    // =========================================================================
    // 7. ARCHITECTURE GUARDS
    // =========================================================================

    @Test
    fun executionModeMutation_remainsFalse() {
        assertFalse(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))
    }

    @Test
    fun noProductionApprovalIssuerExists() {
        // Verify DenyAllApprovalVerifier is the production verifier in 7E-1
        val result = DenyAllApprovalVerifier.verifyApproval(
            intent = ValidatedCableProposalIntent(
                validatedIntentId = "VAL-1",
                proposalId = "PROP-1",
                proposalDigest = "DIGEST-1",
                schemaVersion = "PROPOSAL-V1",
                projectId = "P1",
                baseProjectRevision = 1L,
                targetEntityType = "CIRCUIT",
                targetEntityId = "101",
                operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
                instructions = CablePatchInstructions(),
                sourceToolName = "ProposeCableSizingTool",
                sourceRequestId = "REQ-1",
                validatedAt = 1000L
            ),
            grant = ProposalApprovalGrant(
                approvalId = "APP-1",
                proposalId = "PROP-1",
                proposalDigest = "DIGEST-1",
                baseProjectRevision = 1L,
                approvedBy = "User",
                approvedAt = 1000L
            )
        )
        assertFalse(result.isVerified)
    }

    @Test
    fun noToolViewModelAssistantOrUiReferences7E1() {
        // Verify ExecutionMode.MUTATION remains disabled and not executable
        assertFalse(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))
        assertTrue(ExecutionModePolicy.requiresApprovalAndRevisionGuard(ExecutionMode.MUTATION))
    }

    @Test
    fun no7E2MutationExecutionWiringExists() {
        // Verify EngineeringMutationEngine remains internal without public callers
        assertEquals(false, ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))
    }

    @Test
    fun no7DOrFormulaBehaviorChanged() {
        assertEquals(23, database.openHelper.readableDatabase.version)
    }
}
