package com.example.ai.ui

/**
 * Phase 6 R-1: the single deterministic presentation contract for assistant output.
 *
 * ## Why this exists
 *
 * The assistant renderer is plain Compose `Text`. There is no Markdown library
 * (zero `commonmark`/markdown deps in the module) and no HTML renderer, so every
 * markup character reaches the screen literally. Three concrete defects follow:
 *
 * 1. **Raw LaTeX leaks.** Gemini emits math notation (`$I_z = 158.6\text{ A}$`) and
 *    nothing in the repo ever unwrapped it, so `$$`, `\text{}`, `\Delta` were shown raw.
 *    The system prompt never asks for LaTeX, so this is not a prompting problem.
 * 2. **Duplicated units.** `CableToolContracts.CableSizingResultDetails.cableSize_mm2`
 *    is a `String` that already contains its unit (`"70 mm\u00b2"`), while
 *    `AssistantResultCard` renders `"${metric.value} ${metric.unit}"`. The result is
 *    `70 mm\u00b2 mm\u00b2`. Note `AssistantResponseMapper` already strips the unit on its
 *    *fallback* path but not on the authoritative tool-result path - that asymmetry is the bug.
 * 3. **Malformed Markdown.** `ConversationPolicyEngine` emits `**bold**` into plain text.
 *
 * ## Contract
 *
 * - Presentation only. No engineering value is changed, rounded, rescaled or invented.
 *   Units are never stripped from a bare value; a unit already carried by a value is
 *   only ever *deduplicated*, never altered.
 * - Unit handling is type/field aware: a unit is only removed from a value when the
 *   trailing token is a known alias of *that declared unit*. `"70 mm\u00b2"` with
 *   `unit = "mm\u00b2"` becomes `"70"`; `"CABLE"` with `unit = "A"` is left untouched
 *   because the preceding character is a letter, not a delimiter.
 * - Pure functions, no I/O, no dispatcher, no allocation of resources. Safe to call from
 *   any thread; no main-thread work is added anywhere.
 */
object EngineeringDisplayFormatter {

    // ---------------------------------------------------------------------
    // 1. Value + unit contract
    // ---------------------------------------------------------------------

    private class UnitSpec(
        val symbol: String,
        val aliases: List<String>,
        /** `%` binds tight to its number ("2.18%"); every other symbol takes a space. */
        val tight: Boolean = false
    ) {
        /** Longest first, so "mm^2" is preferred over "mm". */
        val aliasesByLength: List<String> = aliases.sortedByDescending { it.length }
    }

    private val UNITS: List<UnitSpec> = listOf(
        UnitSpec("mm\u00b2", listOf("mm\u00b2", "mm2", "mm^2", "MM\u00b2", "MM2", "sqmm", "sq mm", "sq. mm")),
        UnitSpec("m\u03a9", listOf("m\u03a9", "mohm", "m\u03a9")),
        UnitSpec("kVA", listOf("kVA", "kva", "KVA", "kilovoltampere")),
        UnitSpec("kW", listOf("kW", "kw", "KW", "kilowatt")),
        UnitSpec("kA", listOf("kA", "ka", "KA", "kiloampere")),
        UnitSpec("\u00b0C", listOf("\u00b0C", "degC", "\u00b0c", "degc")),
        UnitSpec("A", listOf("A", "amp", "amps", "ampere", "ampere(s)")),
        UnitSpec("V", listOf("V", "volt", "volts", "volt(s)")),
        UnitSpec("\u03a9", listOf("\u03a9", "ohm", "ohms", "\u03a9")),
        UnitSpec("%", listOf("%", "percent", "pct"), tight = true),
        UnitSpec("m", listOf("m", "meter", "meters", "metre", "metres")),
        UnitSpec("s", listOf("s", "sec", "secs", "second", "seconds"))
    )

    private fun specFor(unit: String?): UnitSpec? {
        val u = unit?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        UNITS.firstOrNull { it.symbol.equals(u, ignoreCase = true) }?.let { return it }
        return UNITS.firstOrNull { spec -> spec.aliases.any { it.equals(u, ignoreCase = true) } }
    }

    private fun specForSymbol(symbol: String): UnitSpec? =
        UNITS.firstOrNull { it.symbol == symbol }

    /**
     * Removes a trailing unit from [raw] when - and only when - it is an alias of the
     * declared [unit]. Returns the bare value. Never changes the numeric content.
     */
    fun normalizeMetricValue(raw: String?, unit: String?): String {
        val value = raw?.trim() ?: return ""
        if (value.isEmpty()) return value
        val spec = specFor(unit) ?: return value

        for (alias in spec.aliasesByLength) {
            if (value.length < alias.length) continue
            val tail = value.substring(value.length - alias.length)
            if (!tail.equals(alias, ignoreCase = true)) continue
            val head = value.substring(0, value.length - alias.length)
            if (head.isEmpty() || isDelimiter(head[head.length - 1])) {
                return head.trim()
            }
        }
        return value
    }

    /**
     * The canonical single display string for a value/unit pair, e.g.
     * `("70 mm\u00b2", "mm\u00b2") -> "70 mm\u00b2"`, `("2.18%", "%") -> "2.18%"`.
     * A unit already present in [value] is replaced, never appended twice.
     */
    fun joinValueAndUnit(value: String?, unit: String?): String {
        val spec = specFor(unit)
        val bare = normalizeMetricValue(value, unit).trim()
        if (spec == null) {
            val v = value?.trim().orEmpty()
            val u = unit?.trim().orEmpty()
            return listOf(v, u).filter { it.isNotEmpty() }.joinToString(" ")
        }
        if (bare.isEmpty()) return spec.symbol
        return if (spec.tight) bare + spec.symbol else "$bare ${spec.symbol}"
    }

    private fun isDelimiter(c: Char): Boolean = c.isWhitespace() || c.isDigit()

    // ---------------------------------------------------------------------
    // 2. Display-text contract (LaTeX / Markdown / markers / unit dedupe)
    // ---------------------------------------------------------------------


    /**
     * A pattern that can never match anything, used as the fallback when compilation fails.
     * `[^\s\S]` is an empty character class (no character is both non-whitespace and
     * whitespace), so it matches nothing and lets every `.replace()` call site keep working
     * unchanged. `(?!)` was avoided because a negative lookahead is less portable than an
     * empty character class.
     */
    private val NEVER_MATCHES: Regex = Regex("[^\\s\\S]")

    /**
     * Compiles [pattern], degrading to [NEVER_MATCHES] instead of throwing.
     *
     * A presentation formatter must never be able to kill the app. On Android
     * `java.util.regex.Pattern` is implemented by com.android.icu.util.regex, so a construct
     * that compiles under the JVM's engine can still throw `PatternSyntaxException` on a
     * device - and inside an `object` initializer that becomes
     * `ExceptionInInitializerError`, which kills the process on the FIRST assistant turn.
     * That happened twice during Phase 6 R-1 (indices 84 and 86, each the exact pattern
     * length), while every JVM unit test stayed green.
     *
     * Returning a never-matching pattern means a rejected pattern costs one cosmetic
     * normalization step instead of the whole application.
     */
    private fun regexOrNever(pattern: String, vararg options: RegexOption): Regex =
        try {
            if (options.isEmpty()) Regex(pattern) else Regex(pattern, options.first())
        } catch (t: Throwable) {
            // Deliberately broad: PatternSyntaxException, StackOverflowError from a
            // pathological compile, or any engine quirk must not escape.
            NEVER_MATCHES
        }

    // BLOCK_MATH / INLINE_MATH strip the math delimiters so their content becomes ordinary
    // text. Mutation testing shows both are EQUIVALENT MUTANTS: deleting them leaves every
    // observable output unchanged, because the later passes (TEXT_CMD, FRAC, SQRT, the symbol
    // table, the residual '$' strip) already operate on the whole string. They are kept as
    // defence in depth - they make the intent explicit and they would matter if a future pass
    // were made delimiter-aware - but no test can distinguish them, and none is claimed.
    private val BLOCK_MATH = regexOrNever("""\$\$(.+?)\$\$""", RegexOption.DOT_MATCHES_ALL)
    private val INLINE_MATH = regexOrNever("""(?<!\$)\$([^$\n]+?)\$(?!\$)""", RegexOption.DOT_MATCHES_ALL)
    private val BRACKET_MATH = regexOrNever("""\\\[(.+?)\\\]""", RegexOption.DOT_MATCHES_ALL)
    private val PAREN_MATH = regexOrNever("""\\\((.+?)\\\)""", RegexOption.DOT_MATCHES_ALL)

    // NOTE (Phase 6 R-1 fix, DEVICE-VERIFIED — read before touching these patterns)
    //
    // On Android, `java.util.regex.Pattern` is implemented by com.android.icu.util.regex.
    // A JVM unit test therefore exercises OpenJDK's engine and CANNOT detect any
    // construct ICU rejects. Two constructs here crashed the app on the first assistant
    // turn while every JVM test stayed green:
    //
    //   1. `\\(?:text|...)`  - an escaped backslash immediately before a group open.
    //      ICU reads `\(` as a LITERAL paren, so `(?:` can never open a group.
    //      -> PatternSyntaxException "near index 84" (pattern length 84).
    //   2. a trailing BARE `}` - Java tolerates it; ICU does not.
    //      -> PatternSyntaxException "near index 86" (pattern length 86) after fix (1).
    //
    // Rules for every pattern in this file:
    //   - match a literal backslash with `[\\]`, never with `\\` next to `(?:`;
    //   - wrap EVERY literal brace in a character class `[{]` / `[}]` — both `\{`
    //     and bare `}` fail on ICU; only the character-class form is portable;
    //   - verify on a real device, because the JVM is not a faithful oracle here.
    private val TEXT_CMD = regexOrNever("""[\\](?:text|mathrm|mathbf|textbf|textit|mbox|operatorname|mathsf|mathtt)\s*[{]([^{}]*)[}]""")
    private val FRAC = regexOrNever("""[\\]frac\s*[{]([^{}]*)[}]\s*[{]([^{}]*)[}]""")
    private val SQRT = regexOrNever("""[\\]sqrt\s*[{]([^{}]*)[}]""")

    private val SYMBOLS: List<Pair<String, String>> = listOf(
        "\\Delta" to "\u0394", "\\delta" to "\u03b4", "\\Omega" to "\u03a9", "\\omega" to "\u03c9",
        "\\mu" to "\u03bc", "\\alpha" to "\u03b1", "\\beta" to "\u03b2", "\\theta" to "\u03b8",
        "\\lambda" to "\u03bb", "\\rho" to "\u03c1", "\\sigma" to "\u03c3", "\\phi" to "\u03d5",
        "\\varepsilon" to "\u03b5", "\\pi" to "\u03c0", "\\gamma" to "\u03b3",
        "\\times" to "\u00d7", "\\cdot" to "\u00b7", "\\pm" to "\u00b1", "\\div" to "\u00f7",
        "\\geq" to "\u2265", "\\ge" to "\u2265", "\\leq" to "\u2264", "\\le" to "\u2264",
        "\\neq" to "\u2260", "\\ne" to "\u2260", "\\approx" to "\u2248", "\\sim" to "~",
        "\\rightarrow" to "\u2192", "\\Rightarrow" to "\u21d2", "\\to"  to "\u2192",
        "\\infty" to "\u221e", "\\circ" to "\u00b0", "\\degree" to "\u00b0",
        "\\prime"  to "\u2032", "\\percent" to "%", "\\ohm" to "\u03a9"
    )

    // Matches a LaTeX-escaped literal punctuation char: \%, \&, \#, \$, \_, \{, \}.
    // Written as an alternation because a single character class containing braces
    // compiles inconsistently across JDK regex versions. The brace alternatives are the
    // character class [\{\}] so no bare brace ever reaches the engine.
    private val ESCAPED_PUNCT = regexOrNever("""\\([%#${'$'}]|[{}])""")
    private val SPACING_MACRO = regexOrNever("""\\[,;:! ]""")
    private val UNKNOWN_CMD = regexOrNever("""\\[a-zA-Z]+""")

    private val BOLD = regexOrNever("""\*\*([^*]+)\*\*""")
    private val BOLD_US = regexOrNever("""__([^_]+)__""")
    private val ITALIC = regexOrNever("""\*([^*\n]+)\*""")
    // `_x` in engineering notation is a SUBSCRIPT, not markdown emphasis: I_z, S_n, Z_s.
    // The underscore is dropped so a plain-Text renderer shows "Iz". Constrained to a
    // single alphanumeric character, preceded by a letter and not followed by another
    // alphanumeric, so snake_case identifiers (cableSize_mm2, value_mm) keep both parts.
    // Braces use [{}] character class (literal inside []), per the ICU note above.
    private val SUBSCRIPT =
        regexOrNever("""(?<=[\p{L}])_[{}]?([A-Za-z0-9])[{}]?(?![\p{L}\p{N}])""")
    private val CODE = regexOrNever("""`([^`]+)`""")
    private val LINK = regexOrNever("""\[([^\]]+)]\([^)]*\)""")
    private val HEADING = regexOrNever("""(?m)^\s*#{1,6}\s+""")
    private val BULLET = regexOrNever("""(?m)^\s*[-*+]\s+""")

    /** Conservative: only internal, enum-shaped, underscore-bearing provenance tokens. */
    private val INTERNAL_MARKER = regexOrNever("""\[(.*?)\]""")
    private val INTERNAL_NAMES = setOf(
        "CALCULATED_BY_VOLTATLAS", "PROVENANCE_ENGINE", "SOURCE_TOOL", "EVIDENCE_REF"
    )

    /** True when [token] is an internal provenance marker rather than user text. */
    private fun isInternalMarker(token: String): Boolean {
        val t = token.trim()
        if (t in INTERNAL_NAMES) return true
        return t.contains('_') && Regex("""[A-Z][A-Z0-9_]{2,}""").matches(t)
    }

    /**
     * Normalizes assistant prose into the single representation the plain-text
     * renderer can display faithfully. Idempotent.
     *
     * Handles, in order: math delimiters, LaTeX commands, unsupported Markdown,
     * internal provenance markers, duplicated units, residual escapes, whitespace.
     */
    fun normalizeDisplayText(text: String): String {
        var s = text
        s = BLOCK_MATH.replace(s) { it.groupValues[1] }
        s = INLINE_MATH.replace(s) { it.groupValues[1] }
        s = BRACKET_MATH.replace(s) { it.groupValues[1] }
        s = PAREN_MATH.replace(s) { it.groupValues[1] }

        repeat(6) {
            val next = TEXT_CMD.replace(s) { it.groupValues[1] }
            if (next == s) return@repeat
            s = next
        }
        s = FRAC.replace(s) { "${it.groupValues[1]}/${it.groupValues[2]}" }
        s = SQRT.replace(s) { "\u221a(${it.groupValues[1]})" }
        for ((cmd, sym) in SYMBOLS) s = s.replace(cmd, sym)
        s = ESCAPED_PUNCT.replace(s) { it.groupValues[1] }
        s = SPACING_MACRO.replace(s, "")
        s = UNKNOWN_CMD.replace(s, "")

        s = BOLD.replace(s) { it.groupValues[1] }
        s = BOLD_US.replace(s) { it.groupValues[1] }
        s = ITALIC.replace(s) { it.groupValues[1] }
        // Subscripts are resolved BEFORE markdown emphasis so I_z is never parsed as italics.
        s = SUBSCRIPT.replace(s) { it.groupValues[1] }
        s = CODE.replace(s) { it.groupValues[1] }
        s = LINK.replace(s) { it.groupValues[1] }
        s = HEADING.replace(s, "")
        s = BULLET.replace(s, "\u2022 ")

        s = INTERNAL_MARKER.replace(s) { if (isInternalMarker(it.groupValues[1])) "" else it.value }

        s = dedupeUnits(s)

        s = s.replace("$", "").replace("\\", "")
        s = s.replace("\u00a0", " ")
        s = s.split('\n').joinToString("\n") { it.trimEnd() }
        s = Regex("""\n{3,}""").replace(s, "\n\n")
        return s.trim()
    }

    /**
     * Collapses a duplicated trailing unit: "70 mm\u00b2 mm\u00b2" -> "70 mm\u00b2",
     * "0.245 \u03a9\u03a9" -> "0.245 \u03a9", "2.18 % %" -> "2.18%".
     *
     * Each alias must be a COMPLETE token: the negative lookahead rejects a match that is
     * only a prefix of a longer unit, so the meter symbol "m" cannot match inside "mm\u00b2"
     * and silently corrupt an engineering value.
     */
    private fun dedupeUnits(s: String): String {
        // One alternation over every known alias, longest first, so a run of repeated
        // units is matched as a whole ("mm² mm²", "\u03a9\u03a9", "% %") and the boundary is
        // validated once at the END of the run. Validating per-alias would reject the
        // legitimate second "\u03a9" in "\u03a9\u03a9", while validating nothing would let the
        // meter symbol "m" match inside "mm\u00b2".
        val anyAlias = UNITS.flatMap { it.aliases }
            .distinct()
            .sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }
        val re = Regex("""(\d)\s*($anyAlias)(?:\s*($anyAlias))+(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
        return re.replace(s) { m ->
            val canonical = UNITS.firstOrNull { spec -> m.groupValues[2].equals(spec.symbol, ignoreCase = true) }
            val byLength = UNITS.firstOrNull { spec ->
                m.groupValues[2].equals(spec.aliases.first(), ignoreCase = true)
            }
            val tight = (canonical ?: byLength)?.tight ?: false
            m.groupValues[1] + (if (tight) (canonical?.symbol ?: "%") else " " + (canonical?.symbol ?: m.groupValues[2]))
        }
    }
}