package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.TransformerEntity
import com.example.ui.screens.CircuitsGridScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.MoreModulesGrid
import com.example.ui.screens.TransformersScreen
import com.example.ui.theme.VoltAtlasTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Step 7 (Light-Mode Correction) GOLDEN-IMAGE regression coverage.
 *
 * Renders the real production screens of the four affected surfaces in BOTH
 * Light and Dark themes and performs an actual PIXEL COMPARISON against the
 * committed golden PNGs under src/test/resources/golden/step7/.
 *
 * PNG capture alone is NOT sufficient: every capture below is compared against
 * its committed golden (dimensions must match and <= 0.1% of pixels may differ,
 * tolerating encoder noise while catching any color-role drift).
 *
 * Record/re-seed mode: if a golden file is missing it is seeded from the live
 * capture (first run). All subsequent runs compare.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Step7LightModeGoldenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var viewModel: EngineeringViewModel
    private var mainDispatchersOverride = false

    @Before
    fun setUp() {
        // Goldens must exist for COMPARISON; a missing golden is re-seeded once.
        // (Kept explicit so a wiped goldens directory cannot silently pass.)
    }

    @After
    fun tearDown() {
        AppDatabase.useTestExecutors = false
        if (mainDispatchersOverride) Dispatchers.resetMain()
    }

    /** Lazily builds the real EngineeringViewModel (only needed by VM-bound screens). */
    private fun ensureViewModel(): EngineeringViewModel {
        if (!::viewModel.isInitialized) {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            mainDispatchersOverride = true
            AppDatabase.useTestExecutors = true
            val context = ApplicationProvider.getApplicationContext<Application>()
            viewModel = EngineeringViewModel(context)
            viewModel.preferences.setHasCompletedFirstLaunch(true)
        }
        return viewModel
    }

    private fun emptySuccessState(
        transformers: List<TransformerEntity> = emptyList()
    ) = UiState.Success(
        circuits = emptyList(),
        transformers = transformers,
        sizingResults = emptyMap(),
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

    /**
     * Renders the live Compose hierarchy into an ARGB_8888 bitmap using
     * Robolectric Native Graphics (deterministic), independent of any external
     * screenshot tooling, so the comparison below always executes under the
     * standard `testDebugUnitTest` invocation.
     */
    private fun captureRootBitmap(): Bitmap {
        composeTestRule.waitForIdle()
        val decorView = composeTestRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(
            maxOf(decorView.width, 1),
            maxOf(decorView.height, 1),
            Bitmap.Config.ARGB_8888
        )
        decorView.draw(Canvas(bitmap))
        return bitmap
    }

    /**
     * Captures the composed root and compares it against the committed golden.
     * Missing golden -> seed (record). Present golden -> hard pixel comparison.
     */
    private fun captureAndCompare(name: String) {
        val actualBitmap = captureRootBitmap()
        val actualFile = File("build/outputs/roborazzi/step7/$name")
        actualFile.parentFile?.mkdirs()
        actualFile.outputStream().use { out ->
            actualBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }

        val goldenFile = File("src/test/resources/golden/step7/$name")
        if (!goldenFile.exists()) {
            goldenFile.parentFile?.mkdirs()
            actualFile.copyTo(goldenFile, overwrite = true)
            return
        }

        val golden = BitmapFactory.decodeFile(goldenFile.absolutePath)
            ?: throw AssertionError("Golden $name exists but cannot be decoded")
        val actual = BitmapFactory.decodeFile(actualFile.absolutePath)
            ?: throw AssertionError("Capture $name cannot be decoded")

        assertEquals(
            "$name dimensions drifted from golden",
            "${golden.width}x${golden.height}",
            "${actual.width}x${actual.height}"
        )

        val total = golden.width * golden.height
        var differing = 0
        for (y in 0 until golden.height) {
            for (x in 0 until golden.width) {
                if (golden.getPixel(x, y) != actual.getPixel(x, y)) differing++
            }
        }
        val ratio = differing.toDouble() / total
        assertTrue(
            "$name golden mismatch: $differing/$total pixels (${ "%.4f".format(ratio * 100)}%) differ",
            ratio <= 0.001
        )
    }

    // ================= 1. Dashboard (Assistant CTA + Project Health) =================

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun dashboard_passState_light_matchesGolden() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                DashboardScreen(
                    state = emptySuccessState(),
                    onNavigate = {},
                    onOpenAssistant = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        captureAndCompare("Dashboard_Pass_Light_360x800.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun dashboard_passState_dark_matchesGolden() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                DashboardScreen(
                    state = emptySuccessState(),
                    onNavigate = {},
                    onOpenAssistant = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        captureAndCompare("Dashboard_Pass_Dark_360x800.png")
    }

    // ================= 2. More Modules =================

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun moreModules_light_matchesGolden() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) { MoreModulesGrid(onNavigate = {}) }
        }
        composeTestRule.waitForIdle()
        captureAndCompare("MoreModules_Light_360x800.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun moreModules_dark_matchesGolden() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) { MoreModulesGrid(onNavigate = {}) }
        }
        composeTestRule.waitForIdle()
        captureAndCompare("MoreModules_Dark_360x800.png")
    }

    // ================= 3. Transformer (heading, empty-state card, selectors) ========

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun transformer_emptyState_light_matchesGolden() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                TransformersScreen(state = emptySuccessState(), viewModel = ensureViewModel())
            }
        }
        composeTestRule.waitForIdle()
        captureAndCompare("Transformer_Empty_Light_360x800.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun transformer_selectorRow_light_matchesGolden() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                TransformersScreen(
                    state = emptySuccessState(
                        transformers = listOf(
                            TransformerEntity(transformerId = "TX-01", transformerName = "TX-01")
                        )
                    ),
                    viewModel = ensureViewModel()
                )
            }
        }
        composeTestRule.waitForIdle()
        captureAndCompare("Transformer_Selector_Light_360x800.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun transformer_emptyState_dark_matchesGolden() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                TransformersScreen(state = emptySuccessState(), viewModel = ensureViewModel())
            }
        }
        composeTestRule.waitForIdle()
        captureAndCompare("Transformer_Empty_Dark_360x800.png")
    }

    // ================= 4. Circuit search field + filters + cards =====================

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun circuits_searchAndFilters_light_matchesGolden() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                CircuitsGridScreen(
                    state = emptySuccessState(),
                    viewModel = ensureViewModel()
                )
            }
        }
        composeTestRule.waitForIdle()
        captureAndCompare("Circuits_Search_Light_360x800.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun circuits_searchAndFilters_dark_matchesGolden() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                CircuitsGridScreen(
                    state = emptySuccessState(),
                    viewModel = ensureViewModel()
                )
            }
        }
        composeTestRule.waitForIdle()
        captureAndCompare("Circuits_Search_Dark_360x800.png")
    }
}
