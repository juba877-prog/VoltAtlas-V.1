package com.example.ai.tools

import com.example.ai.contracts.ExecutionMode
import com.example.ai.runtime.PromptPermissionMode

/**
 * Authoritative descriptor catalog: the single declaration surface for every
 * deterministic engineering tool reachable from the assistant.
 *
 * There must be no second hand-maintained tool declaration list anywhere in the
 * codebase. Model-facing function schemas are generated from this catalog, and
 * [EngineeringToolRegistry] proves at construction time that it matches the
 * executable typed tool registries exactly.
 *
 * Description wording constraint: these strings are surfaced to the model and are
 * additionally scanned by the zero-mutation security guard, so mutation-related
 * vocabulary is intentionally absent.
 */
object EngineeringToolCatalog {

    private const val READ_ONLY_SIDE_EFFECTS =
        "None. Strictly read-only: never writes repository, database, or cache state."

    private const val CALC_SIDE_EFFECTS =
        "None on project data. Reads project entities and produces an in-memory result only; " +
            "no persisted value is changed and no proposal is created."

    val READ_TOOLS: List<EngineeringToolDescriptor> = listOf(
        EngineeringToolDescriptor(
            toolName = "get_current_project_context",
            domain = EngineeringToolDomain.READ,
            purpose = "Return a high-level summary of the active project: revision, engineering status, " +
                "entity counts, and the top outstanding validation findings.",
            whenToUse = "First call of almost every turn, and whenever the project revision or overall " +
                "compliance posture must be established before reasoning about a specific entity.",
            whenNotToUse = "Do not use to obtain the electrical attributes of one specific circuit or " +
                "transformer; use get_entity for that. Never use it as a substitute for a calculation result.",
            outputMeaning = "A project summary including the authoritative project revision and counts of " +
                "calculated, stale, and missing circuits. Absent counts are reported as missing, never guessed.",
            sideEffects = READ_ONLY_SIDE_EFFECTS,
            authorizationLevel = PromptPermissionMode.READ,
            requiresProjectContext = true,
            requiresEntityContext = false,
            requiredFreshness = ToolFreshnessRequirement.NONE,
            provenanceRule = "Persisted project state and calculation freshness metadata.",
            executionMode = ExecutionMode.READ_ONLY,
            supportsParallelExecution = true,
            parameters = listOf(
                EngineeringToolParameter(
                    name = "projectId",
                    type = EngineeringToolParameterType.STRING,
                    required = true,
                    description = "Authoritative project identifier resolved by the runtime, never invented."
                ),
                EngineeringToolParameter(
                    name = "includeValidation",
                    type = EngineeringToolParameterType.BOOLEAN,
                    required = false,
                    description = "When true, includes outstanding validation findings."
                )
            )
        ),
        EngineeringToolDescriptor(
            toolName = "get_entity",
            domain = EngineeringToolDomain.READ,
            purpose = "Return the electrical attributes and cached result snapshots of one circuit or " +
                "transformer within the active project.",
            whenToUse = "Whenever the user refers to a specific circuit or transformer and its stored " +
                "attributes or cached calculation snapshots are required.",
            whenNotToUse = "Do not use to obtain a fresh engineering result; cached snapshots may be stale. " +
                "Never infer an entity identifier - resolve it from project context first.",
            outputMeaning = "A typed entity snapshot with its project revision and cached domain results, " +
                "each carrying an explicit usability state. Uncalculated domains are reported as missing.",
            sideEffects = READ_ONLY_SIDE_EFFECTS,
            authorizationLevel = PromptPermissionMode.READ,
            requiresProjectContext = true,
            requiresEntityContext = true,
            requiredFreshness = ToolFreshnessRequirement.NONE,
            provenanceRule = "Stored entity values plus cached calculation snapshots, each freshness-labelled.",
            executionMode = ExecutionMode.READ_ONLY,
            parameters = listOf(
                EngineeringToolParameter("projectId", EngineeringToolParameterType.STRING, true, "Authoritative project identifier."),
                EngineeringToolParameter("entityType", EngineeringToolParameterType.STRING, true, "Entity kind, currently CIRCUIT or TRANSFORMER."),
                EngineeringToolParameter("entityId", EngineeringToolParameterType.STRING, true, "Authoritative entity identifier within the project.")
            )
        ),
        EngineeringToolDescriptor(
            toolName = "get_calculation_state",
            domain = EngineeringToolDomain.READ,
            purpose = "Report whether stored calculation results for an engineering domain are CURRENT, " +
                "STALE, or MISSING relative to the live project revision.",
            whenToUse = "Before presenting or reasoning about any stored engineering result, and whenever " +
                "freshness is in question.",
            whenNotToUse = "Do not use it to obtain the result values themselves. It reports freshness only.",
            outputMeaning = "A freshness snapshot for the requested domain with an explicit state and reason code.",
            sideEffects = READ_ONLY_SIDE_EFFECTS,
            authorizationLevel = PromptPermissionMode.READ,
            requiresProjectContext = true,
            requiresEntityContext = true,
            requiredFreshness = ToolFreshnessRequirement.NONE,
            provenanceRule = "Calculation freshness metadata keyed by project revision and domain fingerprint.",
            executionMode = ExecutionMode.READ_ONLY,
            supportsParallelExecution = true,
            parameters = listOf(
                EngineeringToolParameter("projectId", EngineeringToolParameterType.STRING, true, "Authoritative project identifier."),
                EngineeringToolParameter("entityType", EngineeringToolParameterType.STRING, true, "Entity kind the freshness scope applies to."),
                EngineeringToolParameter("entityId", EngineeringToolParameterType.STRING, true, "Authoritative entity identifier within the project."),
                EngineeringToolParameter("domain", EngineeringToolParameterType.STRING, false, "Engineering domain to inspect, e.g. CABLE_SIZING.")
            )
        ),
        EngineeringToolDescriptor(
            toolName = "get_dependencies",
            domain = EngineeringToolDomain.READ,
            purpose = "Return the upstream and downstream relationships of an electrical node.",
            whenToUse = "When reasoning requires knowing what feeds an entity or what it feeds.",
            whenNotToUse = "Do not use it to obtain engineering values.",
            outputMeaning = "Resolved dependency relationships with their entity identities.",
            sideEffects = READ_ONLY_SIDE_EFFECTS,
            authorizationLevel = PromptPermissionMode.READ,
            requiresProjectContext = true,
            requiresEntityContext = true,
            requiredFreshness = ToolFreshnessRequirement.NONE,
            provenanceRule = "Persisted network relationships.",
            executionMode = ExecutionMode.READ_ONLY,
            supportsParallelExecution = true,
            parameters = listOf(
                EngineeringToolParameter("projectId", EngineeringToolParameterType.STRING, true, "Authoritative project identifier."),
                EngineeringToolParameter("entityType", EngineeringToolParameterType.STRING, true, "Entity kind of the node."),
                EngineeringToolParameter("entityId", EngineeringToolParameterType.STRING, true, "Authoritative entity identifier.")
            )
        ),
        EngineeringToolDescriptor(
            toolName = "get_change_history",
            domain = EngineeringToolDomain.READ,
            purpose = "Return the persisted revision history of the project.",
            whenToUse = "When the user asks what changed, or when revision chronology must be explained.",
            whenNotToUse = "Do not use it to obtain engineering values.",
            outputMeaning = "A chronological list of persisted revisions.",
            sideEffects = READ_ONLY_SIDE_EFFECTS,
            authorizationLevel = PromptPermissionMode.READ,
            requiresProjectContext = true,
            requiresEntityContext = false,
            requiredFreshness = ToolFreshnessRequirement.NONE,
            provenanceRule = "Persisted revision records.",
            executionMode = ExecutionMode.READ_ONLY,
            supportsParallelExecution = true,
            parameters = listOf(
                EngineeringToolParameter("projectId", EngineeringToolParameterType.STRING, true, "Authoritative project identifier."),
                EngineeringToolParameter("entityId", EngineeringToolParameterType.STRING, false, "Optional entity scope for the history query."),
                EngineeringToolParameter("maxEntries", EngineeringToolParameterType.INTEGER, false, "Maximum number of entries to return.")
            )
        ),
        EngineeringToolDescriptor(
            toolName = "get_catalog_item",
            domain = EngineeringToolDomain.READ,
            purpose = "Search the authoritative equipment catalog for cable, breaker, transformer, " +
                "busway, or accessory entries.",
            whenToUse = "Whenever a valid catalogue-backed candidate or rating must be established.",
            whenNotToUse = "Never use it to justify a rating that was not returned by this tool, and never " +
                "invent a catalog identifier.",
            outputMeaning = "Matching catalog entries with their authoritative identifiers and ratings.",
            sideEffects = READ_ONLY_SIDE_EFFECTS,
            authorizationLevel = PromptPermissionMode.READ,
            requiresProjectContext = false,
            requiresEntityContext = false,
            requiredFreshness = ToolFreshnessRequirement.NONE,
            provenanceRule = "Shipped equipment catalog assets.",
            executionMode = ExecutionMode.READ_ONLY,
            supportsParallelExecution = true,
            parameters = listOf(
                EngineeringToolParameter("category", EngineeringToolParameterType.STRING, true, "Catalog family to search, e.g. cable, breaker, transformer."),
                EngineeringToolParameter("query", EngineeringToolParameterType.STRING, false, "Free-text search query."),
                EngineeringToolParameter("manufacturer", EngineeringToolParameterType.STRING, false, "Restrict matches to one manufacturer."),
                EngineeringToolParameter("sizeMm2", EngineeringToolParameterType.NUMBER, false, "Restrict matches to a conductor cross-section."),
                EngineeringToolParameter("ratingA", EngineeringToolParameterType.NUMBER, false, "Restrict matches to a device rating in amperes."),
                EngineeringToolParameter("limit", EngineeringToolParameterType.INTEGER, false, "Maximum number of matches to return.")
            )
        )
    )

    val CABLE_TOOLS: List<EngineeringToolDescriptor> = listOf(
        EngineeringToolDescriptor(
            toolName = "calculate_cable_sizing",
            domain = EngineeringToolDomain.CABLE,
            purpose = "Run the authoritative VoltAtlas cable-sizing engine for an existing circuit and " +
                "return the selected conductor size, protective device, and every governing check.",
            whenToUse = "Whenever a cable size, protective device rating, ampacity, or voltage-drop result " +
                "is required for a circuit. This is the only legitimate source of those values.",
            whenNotToUse = "Never use this tool to justify a value you produced yourself. Do not infer, " +
                "estimate, or interpolate a conductor size. If the circuit is uncalculated, call this tool " +
                "rather than assuming a result.",
            outputMeaning = "A typed sizing result: selected size, device rating, per-check outcomes, the " +
                "governing criterion, and the project revision it was computed against.",
            sideEffects = CALC_SIDE_EFFECTS,
            authorizationLevel = PromptPermissionMode.CALCULATE,
            requiresProjectContext = true,
            requiresEntityContext = true,
            requiredFreshness = ToolFreshnessRequirement.MUST_BE_CURRENT,
            provenanceRule = "Deterministic cable-sizing engine output over persisted circuit data and catalog.",
            executionMode = ExecutionMode.LIVE_CALCULATION,
            parameters = listOf(
                EngineeringToolParameter("projectId", EngineeringToolParameterType.STRING, true, "Authoritative project identifier."),
                EngineeringToolParameter("circuitId", EngineeringToolParameterType.STRING, true, "Authoritative circuit identifier."),
                EngineeringToolParameter(
                    "forceRecalculate",
                    EngineeringToolParameterType.BOOLEAN,
                    false,
                    "When true, recomputes even if a CURRENT stored result already exists."
                )
            )
        ),
        EngineeringToolDescriptor(
            toolName = "evaluate_cable_candidate",
            domain = EngineeringToolDomain.CABLE,
            purpose = "Evaluate whether one specific candidate conductor meets every governing cable " +
                "criterion for an existing circuit.",
            whenToUse = "When the user proposes a specific size or alternative and its compliance must be " +
                "established deterministically.",
            whenNotToUse = "Do not use it to decide what the correct size is; use calculate_cable_sizing " +
                "for selection. Never evaluate a candidate size that the user did not supply and that no " +
                "tool or catalog produced.",
            outputMeaning = "A typed evaluation with per-check outcomes, the governing criterion, and a " +
                "compliance status for exactly the candidate supplied.",
            sideEffects = CALC_SIDE_EFFECTS,
            authorizationLevel = PromptPermissionMode.CALCULATE,
            requiresProjectContext = true,
            requiresEntityContext = true,
            requiredFreshness = ToolFreshnessRequirement.MUST_BE_CURRENT,
            provenanceRule = "Deterministic candidate evaluation engine output over persisted circuit data.",
            executionMode = ExecutionMode.LIVE_CALCULATION,
            parameters = listOf(
                EngineeringToolParameter("projectId", EngineeringToolParameterType.STRING, true, "Authoritative project identifier."),
                EngineeringToolParameter("circuitId", EngineeringToolParameterType.STRING, true, "Authoritative circuit identifier."),
                EngineeringToolParameter("candidateSizeMm2", EngineeringToolParameterType.NUMBER, false, "Candidate cross-section in mm2."),
                EngineeringToolParameter("candidateSizeText", EngineeringToolParameterType.STRING, false, "Candidate size as text, e.g. '240 mm2'."),
                EngineeringToolParameter("candidateMaterial", EngineeringToolParameterType.STRING, false, "Candidate conductor material from the catalog."),
                EngineeringToolParameter("candidateParallelRuns", EngineeringToolParameterType.INTEGER, false, "Number of parallel runs per phase."),
                EngineeringToolParameter("candidateProductCode", EngineeringToolParameterType.STRING, false, "Catalog product code for the candidate."),
                EngineeringToolParameter("candidateDeviceSizeA", EngineeringToolParameterType.NUMBER, false, "Candidate protective device rating in amperes."),
                EngineeringToolParameter("candidateDeviceType", EngineeringToolParameterType.STRING, false, "Candidate protective device type.")
            )
        ),
        EngineeringToolDescriptor(
            toolName = "compare_cable_candidates",
            domain = EngineeringToolDomain.CABLE,
            purpose = "Evaluate several candidate conductors against an existing circuit and rank them by " +
                "the authoritative engine's own criteria.",
            whenToUse = "When the user asks to compare alternatives, or asks whether a smaller or larger " +
                "size would still satisfy every condition.",
            whenNotToUse = "Do not use it to compute deltas or rankings yourself; the tool returns them. " +
                "Never supply candidate sizes that neither the user nor a tool produced.",
            outputMeaning = "A typed comparison with per-candidate outcomes plus the tool's own smallest " +
                "passing and lowest-utilization selections. Absent recommendation is reported as a reason.",
            sideEffects = CALC_SIDE_EFFECTS,
            authorizationLevel = PromptPermissionMode.CALCULATE,
            requiresProjectContext = true,
            requiresEntityContext = true,
            requiredFreshness = ToolFreshnessRequirement.MUST_BE_CURRENT,
            provenanceRule = "Deterministic candidate comparison engine output over persisted circuit data.",
            executionMode = ExecutionMode.LIVE_CALCULATION,
            parameters = listOf(
                EngineeringToolParameter("projectId", EngineeringToolParameterType.STRING, true, "Authoritative project identifier."),
                EngineeringToolParameter("circuitId", EngineeringToolParameterType.STRING, true, "Authoritative circuit identifier."),
                EngineeringToolParameter(
                    name = "candidates",
                    type = EngineeringToolParameterType.OBJECT_LIST,
                    required = true,
                    description = "Candidate conductors to evaluate. Every candidate must originate from the user or a tool.",
                    fields = listOf(
                        EngineeringToolField("candidateId", EngineeringToolParameterType.STRING, false, "Optional caller label for the candidate."),
                        EngineeringToolField("sizeMm2", EngineeringToolParameterType.NUMBER, false, "Cross-section in mm2."),
                        EngineeringToolField("sizeText", EngineeringToolParameterType.STRING, false, "Size as text."),
                        EngineeringToolField("material", EngineeringToolParameterType.STRING, false, "Conductor material from the catalog."),
                        EngineeringToolField("parallelRuns", EngineeringToolParameterType.INTEGER, false, "Parallel runs per phase."),
                        EngineeringToolField("deviceSizeA", EngineeringToolParameterType.NUMBER, false, "Protective device rating in amperes."),
                        EngineeringToolField("deviceType", EngineeringToolParameterType.STRING, false, "Protective device type.")
                    )
                ),
                EngineeringToolParameter("sortPolicy", EngineeringToolParameterType.STRING, false, "Ordering policy for the returned comparison.")
            )
        ),
        EngineeringToolDescriptor(
            toolName = "run_what_if",
            domain = EngineeringToolDomain.CABLE,
            purpose = "Re-run cable sizing for a circuit under hypothetical parameter changes and report the " +
                "field-by-field difference against the unchanged baseline.",
            whenToUse = "When the user proposes an alternative scenario for a parameter that differs from " +
                "stored project truth, such as a different length or ambient temperature.",
            whenNotToUse = "Never use it to apply a change. It is strictly non-mutating and produces no " +
                "proposal. If the user wants a change to become project truth, a proposal path is required.",
            outputMeaning = "A typed scenario result containing the source revision, the exact changed " +
                "fields, the baseline outcome, and the scenario outcome.",
            sideEffects = "None. The scenario is evaluated hypothetically; no project data is changed.",
            authorizationLevel = PromptPermissionMode.CALCULATE,
            requiresProjectContext = true,
            requiresEntityContext = true,
            requiredFreshness = ToolFreshnessRequirement.MUST_BE_CURRENT,
            provenanceRule = "Deterministic what-if engine output over an immutable scenario copy of the circuit.",
            executionMode = ExecutionMode.WHAT_IF,
            parameters = listOf(
                EngineeringToolParameter("projectId", EngineeringToolParameterType.STRING, true, "Authoritative project identifier."),
                EngineeringToolParameter("circuitId", EngineeringToolParameterType.STRING, true, "Authoritative circuit identifier."),
                EngineeringToolParameter(
                    name = "overrides",
                    type = EngineeringToolParameterType.OBJECT,
                    required = true,
                    description = "Scenario overrides applied on top of stored circuit truth. Only the fields the user actually changed may be set.",
                    fields = listOf(
                        EngineeringToolField("length_m", EngineeringToolParameterType.NUMBER, false, "Scenario route length in metres."),
                        EngineeringToolField("loadKVA", EngineeringToolParameterType.NUMBER, false, "Scenario load in kVA."),
                        EngineeringToolField("pf", EngineeringToolParameterType.NUMBER, false, "Scenario power factor."),
                        EngineeringToolField("voltageV", EngineeringToolParameterType.INTEGER, false, "Scenario system voltage."),
                        EngineeringToolField("maxVDpct", EngineeringToolParameterType.NUMBER, false, "Scenario voltage-drop limit."),
                        EngineeringToolField("cableType", EngineeringToolParameterType.STRING, false, "Scenario cable type."),
                        EngineeringToolField("manufacturer", EngineeringToolParameterType.STRING, false, "Scenario manufacturer."),
                        EngineeringToolField("installation", EngineeringToolParameterType.STRING, false, "Scenario installation method."),
                        EngineeringToolField("parallelRuns", EngineeringToolParameterType.INTEGER, false, "Scenario parallel runs."),
                        EngineeringToolField("airTempC", EngineeringToolParameterType.INTEGER, false, "Scenario air temperature."),
                        EngineeringToolField("gndTempC", EngineeringToolParameterType.INTEGER, false, "Scenario ground temperature."),
                        EngineeringToolField("burialDepth_m", EngineeringToolParameterType.NUMBER, false, "Scenario burial depth."),
                        EngineeringToolField("soilRes", EngineeringToolParameterType.INTEGER, false, "Scenario soil resistivity."),
                        EngineeringToolField("formation", EngineeringToolParameterType.STRING, false, "Scenario cable formation."),
                        EngineeringToolField("spacing", EngineeringToolParameterType.STRING, false, "Scenario spacing."),
                        EngineeringToolField("noCircuits", EngineeringToolParameterType.INTEGER, false, "Scenario number of circuits."),
                        EngineeringToolField("overrideCableSize", EngineeringToolParameterType.STRING, false, "Scenario forced conductor size."),
                        EngineeringToolField("overrideDeviceSize", EngineeringToolParameterType.NUMBER, false, "Scenario forced device rating."),
                        EngineeringToolField("overrideDeviceType", EngineeringToolParameterType.STRING, false, "Scenario forced device type.")
                    )
                )
            )
        )
    )
}
