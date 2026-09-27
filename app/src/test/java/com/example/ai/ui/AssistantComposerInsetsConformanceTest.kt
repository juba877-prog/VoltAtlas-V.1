package com.example.ai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * COMPOSER / INSETS conformance (source-level guarantees that cannot be expressed through
 * Robolectric's window model):
 *
 * 17. The composer is anchored through WindowInsets, never through a hardcoded keyboard height.
 * 18. IME and system/navigation bar insets are applied at the sheet root (no overlap).
 * 19. The composer is NOT part of the conversation LazyColumn, and there is exactly one
 *     authoritative conversation scroll state with no automatic focus request.
 */
class AssistantComposerInsetsConformanceTest {

    private val moduleRoot: File by lazy {
        listOf(File("src/main/java"), File("app/src/main/java"))
            .firstOrNull { it.exists() }
            ?: error("Could not locate src/main/java from ${System.getProperty("user.dir")}")
    }

    private val sheetSource: String by lazy {
        val file = File(moduleRoot, "com/example/ai/ui/EngineeringAssistantSheet.kt")
        assertTrue("EngineeringAssistantSheet.kt must exist", file.exists())
        file.readText()
    }

    @Test
    fun insetsAreAppliedThroughWindowInsets_notHardcodedKeyboardHeights() {
        assertTrue("IME insets must come from WindowInsets.ime", sheetSource.contains("WindowInsets.ime"))
        assertTrue(
            "System/navigation bars must come from WindowInsets.navigationBars",
            sheetSource.contains("WindowInsets.navigationBars")
        )
        assertTrue("Insets must be consumed by a padding modifier", sheetSource.contains("windowInsetsPadding"))

        listOf("keyboardHeight", "KEYBOARD_HEIGHT", "imeHeight", "IME_HEIGHT", "imeBottomOffset").forEach { forbidden ->
            assertFalse("Hardcoded keyboard dimension is not allowed: $forbidden", sheetSource.contains(forbidden))
        }
    }

    @Test
    fun composerAndSuggestionsAreOutsideTheConversationList() {
        val listIndex = sheetSource.indexOf("testTag(\"assistant_conversation_list\")")
        val suggestionsIndex = sheetSource.indexOf("AssistantSuggestionsBar(")
        val composerIndex = sheetSource.indexOf("testTag(\"assistant_input\")")

        assertTrue("conversation list must exist", listIndex > 0)
        assertTrue("SuggestionsBar must be declared after the conversation list", suggestionsIndex > listIndex)
        assertTrue("Composer must be declared after the SuggestionsBar", composerIndex > suggestionsIndex)
    }

    @Test
    fun singleAuthoritativeScrollState_withoutAutomaticFocusRequests() {
        val scrollStates = Regex("rememberLazyListState\\(\\)").findAll(sheetSource).count()
        assertEquals("Exactly one conversation scroll state is allowed", 1, scrollStates)

        assertFalse("No FocusRequester may be attached to the composer", sheetSource.contains("FocusRequester()"))
        assertFalse("Focus must never be requested automatically", sheetSource.contains("requestFocus()"))
        assertFalse("The keyboard must never be shown programmatically", sheetSource.contains("keyboardController?.show"))
    }

    @Test
    fun backHandlingIsKeyboardAware() {
        assertTrue("Back must be intercepted while the keyboard is visible", sheetSource.contains("BackHandler(enabled = keyboardOwnsBack)"))
        assertTrue("Keyboard visibility must come from IME insets", sheetSource.contains("WindowInsets.ime.getBottom(density) > 0"))
        assertTrue("The keyboard-first Back must hide the keyboard", sheetSource.contains("keyboardController?.hide()"))
        assertTrue("The keyboard-first Back must release composer focus", sheetSource.contains("focusManager.clearFocus()"))
        // The sheet lives in its own dialog window, so Back is also intercepted at key level
        // (before the dialog's dismiss callback can fire).
        assertTrue("Back must be intercepted at key level", sheetSource.contains("onPreviewKeyEvent"))
        assertTrue("The key interception must target the Back key", sheetSource.contains("event.key == Key.Back"))
    }
}
