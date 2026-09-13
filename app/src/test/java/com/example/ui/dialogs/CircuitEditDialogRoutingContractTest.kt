package com.example.ui.dialogs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * R0 static/source routing contract for [CircuitEditDialog].
 *
 * The retained legacy route composes a Material 3 AlertDialog containing OutlinedTextField
 * which does not reach Compose idle under the current Robolectric harness (accepted B0
 * diagnostic evidence). This suite therefore deliberately does NOT compose either route at
 * runtime and verifies the default-routing contract at source level only.
 *
 * Evidence classification:
 *  - DEFAULT_LEGACY_ROUTING_SOURCE_VERIFIED
 *  - MOBILE_ROUTE_RETAINED_SOURCE_VERIFIED
 *  - LEGACY_RUNTIME_UNOBSERVABLE_IN_CURRENT_HARNESS
 */
class CircuitEditDialogRoutingContractTest {

    private fun repoRoot(): Path {
        var dir: Path? = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        repeat(6) {
            val d = dir ?: return@repeat
            if (Files.isRegularFile(
                    d.resolve("app/src/main/java/com/example/ui/dialogs/CircuitEditDialog.kt")
                )
            ) {
                return d
            }
            dir = d.parent
        }
        throw IllegalStateException(
            "Repository root containing app/src/main/java/com/example/ui/dialogs/CircuitEditDialog.kt " +
                "not found from user.dir=${System.getProperty("user.dir")}"
        )
    }

    private fun productionSource(): String = Files.readString(
        repoRoot().resolve("app/src/main/java/com/example/ui/dialogs/CircuitEditDialog.kt")
    )

    private fun mainSource(relative: String): String = Files.readString(repoRoot().resolve(relative))

    /** Whitespace-normalized substring of [source]. */
    private fun normalized(source: String): String = source.replace(Regex("\\s+"), " ").trim()

    /**
     * Extracts the body of a top-level function by marker string using brace matching,
     * starting from the first '{' after the marker. Safe for the routing function bodies,
     * which contain no brace characters inside string literals.
     */
    private fun functionBody(source: String, marker: String): String {
        val funIdx = source.indexOf(marker)
        assertTrue("declaration marker not found: $marker", funIdx >= 0)
        val bodyOpen = source.indexOf('{', funIdx + marker.length)
        assertTrue("function body not opened: $marker", bodyOpen > funIdx)
        var depth = 0
        var i = bodyOpen
        var bodyClose = -1
        while (i < source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        bodyClose = i
                        break
                    }
                }
            }
            i++
        }
        assertTrue("function body not closed: $marker", bodyClose > bodyOpen)
        return normalized(source.substring(bodyOpen + 1, bodyClose))
    }

    /** Extracts the paren-delimited parameter list of the public [CircuitEditDialog]. */
    private fun publicParameterList(source: String): String {
        val funIdx = source.indexOf("fun CircuitEditDialog(")
        assertTrue("public CircuitEditDialog declaration not found", funIdx >= 0)
        val paramOpen = source.indexOf('(', funIdx + "fun CircuitEditDialog".length)
        assertTrue("parameter list not opened", paramOpen > funIdx)
        var depth = 0
        var i = paramOpen
        var paramClose = -1
        while (i < source.length) {
            when (source[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) {
                        paramClose = i
                        break
                    }
                }
            }
            i++
        }
        assertTrue("parameter list not closed", paramClose > paramOpen)
        return normalized(source.substring(paramOpen + 1, paramClose)).trimEnd(',')
    }

    // 1. The containment constant exists as private const val with value true (mobile default).
    @Test
    fun test01_containmentConstantIsPrivateConstTrue() {
        val constLine = productionSource().lines()
            .firstOrNull { it.trim().startsWith("private const val USE_MOBILE_CIRCUIT_EDITOR") }
        assertTrue("containment constant declaration not found", constLine != null)
        assertEquals("private const val USE_MOBILE_CIRCUIT_EDITOR = true", constLine!!.trim())
    }

    // 2. The public CircuitEditDialog signature preserves the exact API shape.
    @Test
    fun test02_publicSignaturePreserved() {
        val params = publicParameterList(productionSource())
        assertEquals(
            "circuit: CircuitEntity?, existingCircuits: List<CircuitEntity>, onDismiss: () -> Unit, " +
                "onSave: (CircuitEntity) -> Unit, viewModel: EngineeringViewModel? = null",
            params
        )
    }

    // 3. The active (true) branch routes to the mobile editor; the retained legacy
    //    implementation remains as the fallback branch.
    @Test
    fun test03_activeBranchIsMobileAndLegacyRetainedAsFallback() {
        val body = functionBody(productionSource(), "fun CircuitEditDialog(")
        assertTrue("gate check missing from public routing body", body.contains("if (USE_MOBILE_CIRCUIT_EDITOR) {"))
        val elseIdx = body.indexOf("} else {")
        assertTrue("else branch missing from public routing body", elseIdx >= 0)
        val mobileIdx = body.indexOf("MobileCircuitEditorRoute(")
        val legacyIdx = body.indexOf("LegacyCircuitEditDialogInternal(")
        assertTrue("mobile route call missing from the active branch", mobileIdx in 0 until elseIdx)
        assertTrue("legacy route call missing from the fallback branch", legacyIdx > elseIdx)
    }

    // 4. The true branch references the retained mobile route and its editor entry path.
    @Test
    fun test04_trueBranchReferencesMobilePath() {
        val routeBody = functionBody(productionSource(), "private fun MobileCircuitEditorRoute(")
        assertTrue("mobile route must host the Dialog container", routeBody.contains("Dialog("))
        assertTrue("mobile route must wrap the theme", routeBody.contains("VoltAtlasTheme {"))
        assertTrue("mobile route must invoke CircuitEditorParallelEntry", routeBody.contains("CircuitEditorParallelEntry("))
    }

    // 5. Both branches forward all five public parameters.
    @Test
    fun test05_bothBranchesForwardAllPublicParams() {
        val body = functionBody(productionSource(), "fun CircuitEditDialog(")
        listOf(
            "circuit = circuit",
            "existingCircuits = existingCircuits",
            "onDismiss = onDismiss",
            "onSave = onSave",
            "viewModel = viewModel"
        ).forEach { forwarded ->
            assertEquals(
                "parameter '$forwarded' must be forwarded by both branches exactly once each",
                2,
                body.split(forwarded).size - 1
            )
        }
    }

    // 6. The retained legacy implementation remains declared and is now the default target.
    @Test
    fun test06_legacyImplementationRemainsDeclared() {
        val source = productionSource()
        assertTrue(
            "LegacyCircuitEditDialogInternal declaration missing",
            source.contains("private fun LegacyCircuitEditDialogInternal(")
        )
    }

    // 7. Call-site files still reference the public dialog and are not gate-aware.
    @Test
    fun test07_callerSitesReferenceDialogAndAreNotGateAware() {
        val grid = mainSource("app/src/main/java/com/example/ui/screens/CircuitsGridScreen.kt")
        val panel = mainSource("app/src/main/java/com/example/ui/screens/PanelScheduleScreen.kt")
        assertTrue("CircuitsGridScreen must still call CircuitEditDialog", grid.contains("CircuitEditDialog("))
        assertTrue("PanelScheduleScreen must still call CircuitEditDialog", panel.contains("CircuitEditDialog("))
        assertFalse("call sites must not reference the containment gate", grid.contains("USE_MOBILE_CIRCUIT_EDITOR"))
        assertFalse("call sites must not reference the containment gate", panel.contains("USE_MOBILE_CIRCUIT_EDITOR"))
    }

    // 8. No remote/build/config feature system is introduced; the gate stays file-local.
    @Test
    fun test08_noRemoteOrConfigFeatureSystemIntroduced() {
        // Token scan applies to code only: comment sentences may legitimately describe the
        // negation ("no BuildConfig field, no rollout infrastructure") without introducing one.
        val codeOnly = productionSource()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("//.*"), "")
        listOf(
            "BuildConfig",
            "RemoteConfig",
            "remoteConfig",
            "Firebase",
            "Analytics",
            "rollout"
        ).forEach { token ->
            assertFalse("forbidden feature-system token '$token' in production dialog code", codeOnly.contains(token))
        }
        val mainRoot = repoRoot().resolve("app/src/main/java")
        val referencing = Files.walk(mainRoot).use { stream ->
            stream.filter { it.toString().endsWith(".kt") }
                .filter { Files.readString(it).contains("USE_MOBILE_CIRCUIT_EDITOR") }
                .map { it.fileName.toString() }
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.toList())
        }
        assertEquals(
            "the containment constant must be referenced only by CircuitEditDialog.kt",
            listOf("CircuitEditDialog.kt"),
            referencing
        )
    }
}
