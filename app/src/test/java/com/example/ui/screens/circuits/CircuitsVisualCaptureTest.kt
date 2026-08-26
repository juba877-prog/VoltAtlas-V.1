package com.example.ui.screens.circuits

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.CircuitEntity
import com.example.engine.ShortCircuitResult
import com.example.engine.SizingResult
import com.example.engine.SizingResultStatus
import com.example.ui.EngineeringViewModel
import com.example.ui.UiState
import com.example.ui.screens.CircuitChildLookup
import com.example.ui.screens.CircuitCardV3
import com.example.ui.screens.CircuitsGridScreen
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CircuitsVisualCaptureTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var viewModel: EngineeringViewModel

    private val outputDir = File("build/outputs/roborazzi/circuits_c2").apply { mkdirs() }

    private fun createCircuit(
        id: Int,
        name: String,
        loadKVA: Double,
        fedFrom: String,
        parentId: Int? = null,
        panelType: String? = null,
        cableType: String = "Cu/XLPE/PVC",
        lengthM: Double = 30.0,
        maxVDpct: Double = 0.03,
        loadType: String = "NORMAL"
    ) = CircuitEntity(
        id = id,
        projectId = "DEFAULT_PROJECT",
        circuitName = name,
        loadKVA = loadKVA,
        fedFrom = fedFrom,
        parentId = parentId,
        panelType = panelType,
        cableType = cableType,
        length_m = lengthM,
        maxVDpct = maxVDpct,
        loadType = loadType
    )

    private fun createSizing(
        status: SizingResultStatus,
        cableSize: String = "70",
        ibA: Double = 120.0,
        deviceSizeA: Double = 160.0,
        deviceType: String = "MCCB",
        vdPct: Double = 2.45,
        utilizationPct: Double = 65.0,
        izA: Double = 185.0,
        breakingKA: Double = 36.0
    ) = SizingResult(
        Ib_A = ibA,
        deviceSize_A = deviceSizeA,
        deviceType = deviceType,
        cTotalDerating = 0.8,
        iRequired_perCable_A = 150.0,
        productCode = "PROD-1",
        cableSize_mm2 = cableSize,
        ratedI_table_A = 200.0,
        capacity_derated_Iz_A = izA,
        utilizationPct = utilizationPct,
        neutral_mm2 = "35",
        earthCPC_mm2 = "35",
        voltDrop_V = 5.0,
        vdPct = vdPct,
        cumulativeVDpct = vdPct,
        status = if (status == SizingResultStatus.PASS) "✓ PASS" else "✗ FAIL",
        deviceBreakingKA = breakingKA,
        resultStatus = status,
        isAmpacityOk = status == SizingResultStatus.PASS,
        isVoltageDropOk = status == SizingResultStatus.PASS,
        primaryFailureCode = null
    )

    private fun populatedSampleState(): UiState.Success {
        val c1 = createCircuit(1, "MDB-Main", 250.0, "Transformer", panelType = "MDB", lengthM = 15.0)
        val c2 = createCircuit(2, "SMDB-1", 120.0, "MDB-Main", parentId = 1, panelType = "SMDB", lengthM = 25.0)
        val c3 = createCircuit(3, "DB-Lighting-L1", 45.0, "SMDB-1", parentId = 2, panelType = "DB", lengthM = 40.0)
        val c4 = createCircuit(4, "DB-Power-L1", 60.0, "SMDB-1", parentId = 2, panelType = "DB", lengthM = 50.0)
        val c5 = createCircuit(5, "AHU-Roof-01", 30.0, "MDB-Main", parentId = 1, panelType = null, lengthM = 65.0)

        val sizings = mapOf(
            1 to createSizing(SizingResultStatus.PASS, cableSize = "4x185", ibA = 360.0, deviceSizeA = 400.0, vdPct = 0.85),
            2 to createSizing(SizingResultStatus.PASS, cableSize = "4x95", ibA = 173.0, deviceSizeA = 200.0, vdPct = 1.42),
            3 to createSizing(SizingResultStatus.PASS, cableSize = "4x25", ibA = 65.0, deviceSizeA = 80.0, vdPct = 2.10),
            4 to createSizing(SizingResultStatus.FAIL, cableSize = "4x35", ibA = 86.6, deviceSizeA = 100.0, vdPct = 4.20)
            // c5 has null sizing -> STANDBY
        )

        return UiState.Success(
            circuits = listOf(c1, c2, c3, c4, c5),
            transformers = emptyList(),
            sizingResults = sizings,
            shortCircuitResults = emptyMap(),
            transformerResults = emptyMap(),
            boq = emptyList(),
            drums = emptyList(),
            procurement = emptyList(),
            panels = emptyList(),
            generatorResult = null,
            atsResult = null,
            upsResult = null,
            pfCorrectionResults = emptyList()
        )
    }

    private fun captureRootBitmap(): android.graphics.Bitmap {
        composeTestRule.waitForIdle()
        val decorView = composeTestRule.activity.window.decorView
        val bitmap = android.graphics.Bitmap.createBitmap(
            maxOf(decorView.width, 1),
            maxOf(decorView.height, 1),
            android.graphics.Bitmap.Config.ARGB_8888
        )
        decorView.draw(android.graphics.Canvas(bitmap))
        return bitmap
    }

    private fun saveCapture(name: String) {
        val bitmap = captureRootBitmap()
        val file = File(outputDir, name)
        file.parentFile?.mkdirs()
        file.outputStream().use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        viewModel = EngineeringViewModel(context)
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun capture_circuits_dark_360dp() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CircuitsGridScreen(
                        state = populatedSampleState(),
                        viewModel = viewModel
                    )
                }
            }
        }
        saveCapture("Circuits_Dark_360dp.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun capture_circuits_light_360dp() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CircuitsGridScreen(
                        state = populatedSampleState(),
                        viewModel = viewModel
                    )
                }
            }
        }
        saveCapture("Circuits_Light_360dp.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h640dp-mdpi")
    fun capture_circuits_dark_320dp_narrow() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CircuitsGridScreen(
                        state = populatedSampleState(),
                        viewModel = viewModel
                    )
                }
            }
        }
        saveCapture("Circuits_Dark_320dp_Narrow.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h640dp-mdpi")
    fun capture_circuits_light_320dp_narrow() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CircuitsGridScreen(
                        state = populatedSampleState(),
                        viewModel = viewModel
                    )
                }
            }
        }
        saveCapture("Circuits_Light_320dp_Narrow.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w412dp-h915dp-xxhdpi")
    fun capture_circuits_dark_412dp_wide() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CircuitsGridScreen(
                        state = populatedSampleState(),
                        viewModel = viewModel
                    )
                }
            }
        }
        saveCapture("Circuits_Dark_412dp_Wide.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w412dp-h915dp-xxhdpi")
    fun capture_circuits_light_412dp_wide() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CircuitsGridScreen(
                        state = populatedSampleState(),
                        viewModel = viewModel
                    )
                }
            }
        }
        saveCapture("Circuits_Light_412dp_Wide.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.3f)
    fun capture_circuits_dark_fontScale_1_3x() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CircuitsGridScreen(
                        state = populatedSampleState(),
                        viewModel = viewModel
                    )
                }
            }
        }
        saveCapture("Circuits_Dark_FontScale_1_3x.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.3f)
    fun capture_circuits_light_fontScale_1_3x() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CircuitsGridScreen(
                        state = populatedSampleState(),
                        viewModel = viewModel
                    )
                }
            }
        }
        saveCapture("Circuits_Light_FontScale_1_3x.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun capture_circuits_dark_fontScale_1_5x() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CircuitsGridScreen(
                        state = populatedSampleState(),
                        viewModel = viewModel
                    )
                }
            }
        }
        saveCapture("Circuits_Dark_FontScale_1_5x.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun capture_circuits_light_fontScale_1_5x() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CircuitsGridScreen(
                        state = populatedSampleState(),
                        viewModel = viewModel
                    )
                }
            }
        }
        saveCapture("Circuits_Light_FontScale_1_5x.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w800dp-h360dp-land-xhdpi")
    fun capture_circuits_landscape_dark() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CircuitsGridScreen(
                        state = populatedSampleState(),
                        viewModel = viewModel
                    )
                }
            }
        }
        saveCapture("Circuits_Landscape_Dark_800x360.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w800dp-h360dp-land-xhdpi")
    fun capture_circuits_landscape_light() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CircuitsGridScreen(
                        state = populatedSampleState(),
                        viewModel = viewModel
                    )
                }
            }
        }
        saveCapture("Circuits_Landscape_Light_800x360.png")
    }

    // =========================================================================
    // ISOLATED-CARD CHECKPOINT (C3 recovery - card-only renders on a neutral
    // theme-correct surface, no top shell, no list).
    // =========================================================================

    private fun createShortCircuit(circuitId: Int, iscKA: Double) = ShortCircuitResult(
        circuitId = circuitId,
        sourceName = "Transformer",
        cableMm2 = "185",
        iscKA = iscKA,
        iwKA = iscKA * 0.8,
        deviceKA = 36.0,
        deviceCheck = "OK",
        cableCheck = "OK",
        marginPct = 48.0,
        status = "CHECK OK",
        note = ""
    )

    private fun renderIsolatedCard(
        circuit: CircuitEntity,
        sizing: SizingResult?,
        isSelected: Boolean = false,
        darkTheme: Boolean = true
    ) {
        val lookup = CircuitChildLookup.build(listOf(circuit))
        val model = CircuitUiModelMapper.mapToCardUiModel(
            circuit = circuit,
            sizingResult = sizing,
            shortCircuitResult = if (sizing != null) createShortCircuit(circuit.id, 14.85) else null,
            allCircuits = listOf(circuit),
            hasChildren = lookup.hasChildren(circuit.circuitName)
        )
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        CircuitCardV3(
                            circ = circuit,
                            result = sizing,
                            model = model,
                            isSelected = isSelected,
                            childrenLookup = lookup,
                            viewModel = viewModel,
                            onNavigateToPanelSchedule = null,
                            onOpenAssistant = null,
                            onEdit = {},
                            onDelete = {}
                        )
                    }
                }
            }
        }
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun capture_isolated_card_pass_dark_360() {
        renderIsolatedCard(
            circuit = createCircuit(1, "MDB-Main", 250.0, "Transformer", panelType = "MDB", lengthM = 15.0),
            sizing = createSizing(SizingResultStatus.PASS, cableSize = "4x185", ibA = 360.0, deviceSizeA = 400.0, vdPct = 0.85),
            darkTheme = true
        )
        saveCapture("IsolatedCard_PASS_Dark_360dp.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun capture_isolated_card_pass_light_360() {
        renderIsolatedCard(
            circuit = createCircuit(1, "MDB-Main", 250.0, "Transformer", panelType = "MDB", lengthM = 15.0),
            sizing = createSizing(SizingResultStatus.PASS, cableSize = "4x185", ibA = 360.0, deviceSizeA = 400.0, vdPct = 0.85),
            darkTheme = false
        )
        saveCapture("IsolatedCard_PASS_Light_360dp.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun capture_isolated_card_fail_dark_360() {
        renderIsolatedCard(
            circuit = createCircuit(2, "SMDB-1", 120.0, "MDB-Main", lengthM = 25.0),
            sizing = createSizing(SizingResultStatus.FAIL, cableSize = "4x35", ibA = 86.6, deviceSizeA = 100.0, vdPct = 4.20),
            darkTheme = true
        )
        saveCapture("IsolatedCard_FAIL_Dark_360dp.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun capture_isolated_card_standby_dark_360() {
        renderIsolatedCard(
            circuit = createCircuit(3, "AHU-Roof-01", 30.0, "MDB-Main", lengthM = 65.0),
            sizing = null,
            darkTheme = true
        )
        saveCapture("IsolatedCard_STANDBY_Dark_360dp.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h640dp-mdpi")
    fun capture_isolated_card_long_name_320() {
        renderIsolatedCard(
            circuit = createCircuit(4, "AHU-RC-02-RECOVERY-AIR-HANDLING-UNIT-SUPPLY-FAN", 30.0, "MDB-Main", lengthM = 110.0),
            sizing = createSizing(SizingResultStatus.PASS, cableSize = "4x120", ibA = 44.0, deviceSizeA = 63.0, vdPct = 1.9),
            darkTheme = true
        )
        saveCapture("IsolatedCard_LongName_Dark_320dp.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun capture_isolated_card_fontscale_360() {
        renderIsolatedCard(
            circuit = createCircuit(5, "DB-Power-L1", 60.0, "SMDB-1", lengthM = 50.0),
            sizing = createSizing(SizingResultStatus.WARNING, cableSize = "4x25", ibA = 65.0, deviceSizeA = 80.0, vdPct = 2.10),
            darkTheme = true
        )
        saveCapture("IsolatedCard_FontScale1_5_Dark_360dp.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun capture_isolated_card_expanded_overrides_360() {
        renderIsolatedCard(
            circuit = createCircuit(6, "DB-Lighting-L1", 45.0, "SMDB-1", lengthM = 40.0),
            sizing = createSizing(SizingResultStatus.PASS, cableSize = "4x25", ibA = 65.0, deviceSizeA = 80.0, vdPct = 2.10),
            isSelected = true,
            darkTheme = true
        )
        saveCapture("IsolatedCard_ExpandedOverrides_Dark_360dp.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun capture_isolated_card_missing_result_overrides_360() {
        renderIsolatedCard(
            circuit = createCircuit(7, "PUMP-MAIN-01", 22.0, "MDB-Main", lengthM = 12.0),
            sizing = null,
            isSelected = true,
            darkTheme = true
        )
        saveCapture("IsolatedCard_MissingResultOverrides_Dark_360dp.png")
    }
}

