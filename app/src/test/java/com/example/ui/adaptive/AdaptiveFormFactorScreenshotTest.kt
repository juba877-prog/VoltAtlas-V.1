package com.example.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.CircuitEntity
import com.example.engine.SizingResult
import com.example.engine.SizingResultStatus
import com.example.ui.EngineeringViewModel
import com.example.ui.ProjectValidationSummary
import com.example.ui.UiState
import com.example.ui.screens.CircuitsGridScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.QuickEngineeringHubScreen
import com.example.ui.theme.VoltAtlasTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Step 2: Multi-Form-Factor Screenshot Test Harness.
 *
 * Verifies visual layout stability, grid reflow, and responsiveness across
 * the four canonical Android form factors defined in the adaptive specification:
 *  1. Phone (Compact < 600dp: w360dp-h800dp-xhdpi)
 *  2. Foldable Unfolded (Medium 600–839dp: w673dp-h841dp-xhdpi)
 *  3. Tablet (Expanded 840–1199dp: w1024dp-h768dp-mdpi)
 *  4. Desktop (Expanded Large >= 1200dp: w1280dp-h800dp-mdpi)
 *
 * Outputs golden visual artifacts to `build/outputs/roborazzi/adaptive/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdaptiveFormFactorScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: EngineeringViewModel

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        viewModel = EngineeringViewModel(app)
    }

    private fun createSampleCircuit(
        id: Int,
        name: String,
        loadKVA: Double,
        fedFrom: String,
        parentId: Int? = null,
        panelType: String? = null
    ) = CircuitEntity(
        id = id,
        projectId = "DEFAULT_PROJECT",
        circuitName = name,
        loadKVA = loadKVA,
        fedFrom = fedFrom,
        parentId = parentId,
        panelType = panelType,
        cableType = "Cu/XLPE/PVC - 4C",
        length_m = 35.0,
        maxVDpct = 0.03,
        loadType = "NORMAL"
    )

    private fun createSampleSizing(status: SizingResultStatus, vdPct: Double) = SizingResult(
        Ib_A = 220.0,
        deviceSize_A = 250.0,
        deviceType = "MCCB",
        cTotalDerating = 0.85,
        iRequired_perCable_A = 220.0,
        productCode = "CU/XLPE/PVC-120",
        cableSize_mm2 = "120 mm²",
        ratedI_table_A = 299.0,
        capacity_derated_Iz_A = 254.15,
        utilizationPct = 86.5,
        neutral_mm2 = "70 mm²",
        earthCPC_mm2 = "70 mm²",
        voltDrop_V = 3.2,
        vdPct = vdPct,
        cumulativeVDpct = vdPct,
        status = if (status == SizingResultStatus.PASS) "✓ PASS" else "✗ FAIL",
        resultStatus = status,
        isAmpacityOk = status == SizingResultStatus.PASS,
        isVoltageDropOk = status == SizingResultStatus.PASS,
        primaryFailureCode = null
    )

    private fun sampleCircuitsState(): UiState.Success {
        val c1 = createSampleCircuit(1, "MDB-Main", 300.0, "Transformer", panelType = "MDB")
        val c2 = createSampleCircuit(2, "SMDB-HVAC", 120.0, "MDB-Main", parentId = 1, panelType = "SMDB")
        val c3 = createSampleCircuit(3, "DB-Lighting", 45.0, "SMDB-HVAC", parentId = 2, panelType = "DB")
        val c4 = createSampleCircuit(4, "DB-Power", 60.0, "SMDB-HVAC", parentId = 2, panelType = "DB")

        return UiState.Success(
            circuits = listOf(c1, c2, c3, c4),
            transformers = emptyList(),
            sizingResults = mapOf(
                1 to createSampleSizing(SizingResultStatus.PASS, 0.8),
                2 to createSampleSizing(SizingResultStatus.PASS, 1.4),
                3 to createSampleSizing(SizingResultStatus.PASS, 2.1),
                4 to createSampleSizing(SizingResultStatus.PASS, 2.6)
            ),
            transformerResults = emptyMap(),
            boq = emptyList(),
            drums = emptyList(),
            procurement = emptyList(),
            panels = emptyList(),
            pfCorrectionResults = emptyList(),
            validationSummary = ProjectValidationSummary(
                overallStatus = "100% COMPLIANT / PASS",
                totalFailures = 0,
                totalReviewsRequired = 0,
                totalWarnings = 0,
                openIssues = emptyList()
            )
        )
    }

    // =========================================================================
    // CIRCUITS ADAPTIVE GRID — 4 FORM FACTORS
    // =========================================================================

    @After
    fun tearDown() {
        // KT-12: this class reaches AppDatabase.getDatabase() (directly or via
        // EngineeringViewModel), which caches the instance process-wide together with an
        // absolute DB file path inside the current Robolectric sandbox. Robolectric rotates that
        // sandbox per @Config while Gradle reuses one JVM, so a surviving instance points at a
        // path that no longer exists and later suites fail with SQLITE_CANTOPEN (code 14).
        AppDatabase.resetInstanceForTesting()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun captureCircuits_Phone_360dp() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                CircuitsGridScreen(state = sampleCircuitsState(), viewModel = viewModel)
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/adaptive/circuits_phone_360dp.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w673dp-h841dp-xhdpi")
    fun captureCircuits_Foldable_673dp() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                CircuitsGridScreen(state = sampleCircuitsState(), viewModel = viewModel)
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/adaptive/circuits_foldable_673dp.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w1024dp-h768dp-mdpi")
    fun captureCircuits_Tablet_1024dp() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                CircuitsGridScreen(state = sampleCircuitsState(), viewModel = viewModel)
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/adaptive/circuits_tablet_1024dp.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w1280dp-h800dp-mdpi")
    fun captureCircuits_Desktop_1280dp() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                CircuitsGridScreen(state = sampleCircuitsState(), viewModel = viewModel)
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/adaptive/circuits_desktop_1280dp.png")
    }

    // =========================================================================
    // DASHBOARD METRICS ADAPTIVE REFLOW — PHONE VS TABLET
    // =========================================================================

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun captureDashboard_Phone_360dp() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                DashboardScreen(state = sampleCircuitsState(), onNavigate = {})
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/adaptive/dashboard_phone_360dp.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w1024dp-h768dp-mdpi")
    fun captureDashboard_Tablet_1024dp() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                DashboardScreen(state = sampleCircuitsState(), onNavigate = {})
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/adaptive/dashboard_tablet_1024dp.png")
    }

    // =========================================================================
    // QUICK ENGINEERING HUB ADAPTIVE GRID — PHONE VS TABLET
    // =========================================================================

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun captureQuickHub_Phone_360dp() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                QuickEngineeringHubScreen(onSelectTool = {}, onBack = {})
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/adaptive/quick_hub_phone_360dp.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w1024dp-h768dp-mdpi")
    fun captureQuickHub_Tablet_1024dp() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                QuickEngineeringHubScreen(onSelectTool = {}, onBack = {})
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/adaptive/quick_hub_tablet_1024dp.png")
    }

    // =========================================================================
    // QUICK ENGINEERING TWO-PANE LIST-DETAIL SCENE — TABLET & DESKTOP
    // =========================================================================

    @Test
    @Config(sdk = [33], qualifiers = "w1024dp-h768dp-mdpi")
    fun captureQuickTwoPane_Tablet_Placeholder_1024dp() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(0.38f).fillMaxHeight()) {
                            QuickEngineeringHubScreen(onSelectTool = {}, onBack = {})
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                        Box(modifier = Modifier.weight(0.62f).fillMaxHeight()) {
                            com.example.navigation.QuickToolPlaceholder()
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/adaptive/quick_two_pane_tablet_placeholder_1024dp.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w1024dp-h768dp-mdpi")
    fun captureQuickTwoPane_Tablet_ActiveTool_1024dp() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(0.38f).fillMaxHeight()) {
                            QuickEngineeringHubScreen(onSelectTool = {}, onBack = {})
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                        Box(modifier = Modifier.weight(0.62f).fillMaxHeight()) {
                            com.example.ui.screens.QuickCableCalcScreen(onBack = {})
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/adaptive/quick_two_pane_tablet_active_tool_1024dp.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w1280dp-h800dp-mdpi")
    fun captureQuickTwoPane_Desktop_ActiveTool_1280dp() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(0.38f).fillMaxHeight()) {
                            QuickEngineeringHubScreen(onSelectTool = {}, onBack = {})
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                        Box(modifier = Modifier.weight(0.62f).fillMaxHeight()) {
                            com.example.ui.screens.QuickCableCalcScreen(onBack = {})
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/adaptive/quick_two_pane_desktop_active_tool_1280dp.png")
    }

    // =========================================================================
    // PROJECT PICKER ADAPTIVE MULTI-COLUMN GRID — 4 FORM FACTORS
    // =========================================================================

    private fun sampleProjectPickerProjects() = listOf(
        com.example.data.ProjectEntity(
            projectId = "P1",
            projectName = "Hospital Main Substation",
            clientName = "National Health Authority",
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L
        ),
        com.example.data.ProjectEntity(
            projectId = "P2",
            projectName = "Commercial Tower Phase 2",
            clientName = "Emaar Properties",
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L
        ),
        com.example.data.ProjectEntity(
            projectId = "P3",
            projectName = "Industrial Plant Feeder",
            clientName = "Petrofac Engineering",
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L
        ),
        com.example.data.ProjectEntity(
            projectId = "P4",
            projectName = "Data Center Distribution",
            clientName = "Cloud Infrastructure Ltd",
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L
        )
    )

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun captureProjectPicker_Phone_360dp() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                com.example.ui.ProjectPickerScreen(
                    projects = sampleProjectPickerProjects(),
                    onCreateProject = {},
                    onOpenProject = {},
                    onRenameProject = { _, _ -> },
                    onDeleteProject = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/adaptive/project_picker_phone_360dp.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w673dp-h841dp-xhdpi")
    fun captureProjectPicker_Foldable_673dp() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                com.example.ui.ProjectPickerScreen(
                    projects = sampleProjectPickerProjects(),
                    onCreateProject = {},
                    onOpenProject = {},
                    onRenameProject = { _, _ -> },
                    onDeleteProject = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/adaptive/project_picker_foldable_673dp.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w1024dp-h768dp-mdpi")
    fun captureProjectPicker_Tablet_1024dp() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                com.example.ui.ProjectPickerScreen(
                    projects = sampleProjectPickerProjects(),
                    onCreateProject = {},
                    onOpenProject = {},
                    onRenameProject = { _, _ -> },
                    onDeleteProject = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/adaptive/project_picker_tablet_1024dp.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w1280dp-h800dp-mdpi")
    fun captureProjectPicker_Desktop_1280dp() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                com.example.ui.ProjectPickerScreen(
                    projects = sampleProjectPickerProjects(),
                    onCreateProject = {},
                    onOpenProject = {},
                    onRenameProject = { _, _ -> },
                    onDeleteProject = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/adaptive/project_picker_desktop_1280dp.png")
    }
}

