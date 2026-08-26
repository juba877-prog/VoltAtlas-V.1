package com.example.ui.screens.circuits

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.CircuitEntity
import com.example.engine.SizingResult
import com.example.engine.SizingResultStatus
import com.example.ui.EngineeringViewModel
import com.example.ui.UiState
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
}

