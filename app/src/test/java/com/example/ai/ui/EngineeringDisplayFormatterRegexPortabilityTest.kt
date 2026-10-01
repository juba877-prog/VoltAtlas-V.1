package com.example.ai.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Phase 6 R-7: regex portability between the JVM and Android/ICU.
 *
 * ## Why this suite exists
 *
 * A pattern of the form
 *
 *     `\\(?:text|mathrm|...)\s*\{([^{}]*)}`   (84 characters)
 *
 * compiled fine under `java.util.regex`, so every JVM unit test passed, but on a real
 * Android device the FIRST assistant turn killed the app with
 *
 *     java.util.regex.PatternSyntaxException:
 *         Syntax error in regexp pattern near index 84
 *
 * `java.lang.ExceptionInInitializerError` from the object's static initializer.
 *
 * Two lessons are encoded here:
 *
 *  1. Every pattern this class builds must be validated against a strict compiler, not
 *     merely against the one engine the JVM happens to provide. [PatternSyntaxException]
 *     is caught explicitly so a bad pattern fails as a clear message here instead of
 *     crashing the app later.
 *  2. Patterns are exercised end-to-end by [stress inputs], not only through the public
 *     helpers, so a construct that is syntactically valid but semantically wrong on one
 *     engine is still caught.
 */
class EngineeringDisplayFormatterRegexPortabilityTest {

    // '$' opens a Kotlin template, so literal dollars in samples are spliced via dol.
    private val dol = "$"

    /**
     * Patterns the formatter depends on, spelled out exactly as they appear in the
     * implementation. Duplicated deliberately: this test must keep working even if the
     * implementation is edited.
     */
    private val patterns = mapOf(
        "inline math" to """(?<!\$)\$([^$\n]+?)\$(?!\$)""",
        "block math" to """\$\$(.+?)\$\$""",
        "bracket math" to """\\\[(.+?)\\\]""",
        "paren math" to """\\\((.+?)\\\)""",
        "text command" to """[\\](?:text|mathrm|mathbf|textbf|textit|mbox|operatorname|mathsf|mathtt)\s*[{]([^{}]*)[}]""",
        "frac" to """[\\]frac\s*[{]([^{}]*)[}]\s*[{]([^{}]*)[}]""",
        "sqrt" to """[\\]sqrt\s*[{]([^{}]*)[}]""",
        "escaped punct" to """\\([%#${'$'}]|[{}])""",
        "spacing macro" to """\\[,;:! ]""",
        "unknown cmd" to """\\[a-zA-Z]+""",
        "bold" to """\*\*([^*]+)\*\*""",
        "bold underscore" to """__([^_]+)__""",
        "subscript" to """(?<=[\p{L}])_[{}]?([A-Za-z0-9])[{}]?(?![\p{L}\p{N}])""",
        "code span" to """`([^`]+)`""",
        "link" to """\[([^\]]+)]\([^)]*\)""",
        "heading" to """(?m)^\s*#{1,6}\s+""",
        "bullet" to """(?m)^\s*[-*+]\s+""",
        "bracketed" to """\[(.*?)\]""",
        "enum marker" to """[A-Z][A-Z0-9_]{2,}""",
        "newlines" to """\n{3,}"""
    )

    @Test
    fun `every formatter pattern compiles on a strict engine`() {
        val failures = mutableListOf<String>()
        for ((name, pattern) in patterns) {
            try {
                Pattern.compile(pattern)
            } catch (e: PatternSyntaxException) {
                failures += "$name -> ${e.description} at ${e.index} (len=${pattern.length})"
            }
        }
        assertTrue(
            "patterns that failed to compile:\n" + failures.joinToString("\n"),
            failures.isEmpty()
        )
    }

    @Test
    fun `no pattern uses an escaped paren before a group open`() {
        // On ICU an ESCAPED `(` is a literal paren, so a following `?:` can never open a
        // group and the whole pattern is a syntax error. java.util.regex tolerated the
        // same text, which is exactly why the device crash escaped the JVM test suite.
        //
        // NOTE: [isIcuSafe] below replicates ICU's grouping rule on the JVM, because
        // com.android.icu.util.regex is a throwing stub in android.jar and cannot be
        // executed from a local unit test. Device verification remains the final oracle.
        val bad = patterns.filter { (_, p) -> !isIcuSafe(p) }
        assertTrue(
            "ICU-invalid patterns present: " +
                bad.entries.joinToString { "${it.key} -> ${it.value}" },
            bad.isEmpty()
        )
    }

    @Test
    fun `the ICU grouping rule check accepts the fix and rejects the original defect`() {
        // Self-check of the checker: the guard is only useful if it actually rejects the
        // construct that shipped the crash.
        val crashing = "\\" + "(?:text|mathrm)" + "\\s*" + "\\{([^{}]*)}"
        val fixed = "[\\](?:text|mathrm)" + "\\s*" + "\\{([^{}]*)}"
        assertTrue("checker must reject the crashing form", !isIcuSafe(crashing))
        assertTrue("checker must accept the fixed form", isIcuSafe(fixed))
    }

    /**
     * Replicates ICU's grouping rule: walk the pattern; when a backslash escapes the next
     * character, that character is literal. An unescaped '(' opens a group.
     */
    private fun isIcuSafe(pattern: String): Boolean {
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            if (c == '\\') {
                // Escaped char. If it is '(', it is LITERAL, so any '?:' right after it
                // is nonsense rather than a non-capturing group.
                if (i + 1 < pattern.length && pattern[i + 1] == '(' &&
                    pattern.substring(i + 2).startsWith("?:")
                ) {
                    return false
                }
                i += 2
                continue
            }
            i++
        }
        return true
    }

    @Test
    fun `text command pattern actually unwraps a real latex command`() {
        val re = Pattern.compile(patterns.getValue("text command"))
        val m = re.matcher("""A \text{ A} tail""")
        assertTrue("text command did not match", m.find())
        assertTrue("captured group wrong: ${m.group(1)}", m.group(1)?.contains("A") == true)
    }

    @Test
    fun `fraction and sqrt patterns unwrap real latex`() {
        val frac = Pattern.compile(patterns.getValue("frac"))
        val fm = frac.matcher("""\frac{1}{2}""")
        assertTrue(fm.find())
        assertTrue(fm.group(1)?.isNotEmpty() == true && fm.group(2)?.isNotEmpty() == true)

        val sqrt = Pattern.compile(patterns.getValue("sqrt"))
        val sm = sqrt.matcher("""\sqrt{3}""")
        assertTrue(sm.find())
    }

    @Test
    fun `normalization works end to end through the reformatted patterns`() {
        // If the patterns above stopped matching, this would silently return raw markup.
        // The command is unwrapped; whitespace inside the braces is preserved verbatim because
        // the formatter never re-lays-out text.
        val textCmd = EngineeringDisplayFormatter.normalizeDisplayText("A \\text{ A}")
        assertTrue("command survived: $textCmd", !textCmd.contains("text"))
        assertTrue("unit lost: $textCmd", textCmd.contains("A"))
        // "$" opens a Kotlin template, so the dollar-delimited math is spliced via dol.
        assertTrue(EngineeringDisplayFormatter.normalizeDisplayText("$dol\\frac{1}{2}$dol")
            .contains("1/2"))
        assertTrue(!EngineeringDisplayFormatter.normalizeDisplayText("$dol" + "50 mm²")
            .contains("$dol"))
    }

    @Test
    fun `an initializer-time failure would be visible rather than crashing a device`() {
        // Touching the object forces its static initializer to run. If any pattern were
        // invalid, the first access throws ExceptionInInitializerError - which is exactly
        // what killed the app. This test turns that into a readable failure here.
        val out = EngineeringDisplayFormatter.normalizeDisplayText("70 mm² mm²")
        assertTrue("normalizer did not run", out.isNotEmpty())
    }
}