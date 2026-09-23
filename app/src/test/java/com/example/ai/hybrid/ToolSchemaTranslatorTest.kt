package com.example.ai.hybrid

import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.tools.EngineeringToolCatalog
import com.example.ai.tools.EngineeringToolRegistry
import org.junit.Assert.*
import org.junit.Test

class ToolSchemaTranslatorTest {

    private val circuitContext = BoundedProjectContext(
        projectId = "PROJ-1",
        projectName = "Test Project",
        liveRevision = 1L,
        activeScreen = "CircuitsGridScreen",
        activeEntityType = "CIRCUIT",
        activeEntityId = "C-101",
        activeEntityName = "Feeder 101"
    )

    private val genericProjectContext = BoundedProjectContext(
        projectId = "PROJ-1",
        projectName = "Test Project",
        liveRevision = 1L,
        activeScreen = "ProjectDashboardScreen",
        activeEntityType = null,
        activeEntityId = null
    )

    private val allDescriptors = EngineeringToolCatalog.READ_TOOLS + EngineeringToolCatalog.CABLE_TOOLS

    private fun scoped(context: BoundedProjectContext?) =
        ToolSchemaTranslator.getScopedDeclarations(EngineeringToolRegistry.scopeTo(allDescriptors, context))

    // 1. Scoped tools: circuit context returns cable tools plus relevant read tools
    @Test
    fun cloudEngine_translatesOnlyScopedToolsNotFullRegistry() {
        val circuitToolNames = scoped(circuitContext).map { it.name }

        assertTrue(circuitToolNames.contains("calculate_cable_sizing"))
        assertTrue(circuitToolNames.contains("evaluate_cable_candidate"))
        assertTrue(circuitToolNames.contains("compare_cable_candidates"))
        assertTrue(circuitToolNames.contains("run_what_if"))
        assertTrue(circuitToolNames.contains("get_entity"))

        val genericToolNames = scoped(genericProjectContext).map { it.name }

        assertTrue(genericToolNames.contains("get_current_project_context"))
        assertFalse(
            "Generic context must not expose cable sizing calculation tools",
            genericToolNames.contains("calculate_cable_sizing")
        )
    }

    // 2. Security invariant: zero mutation tools in schema
    @Test
    fun cloudEngine_neverIncludesMutationToolsInSchema() {
        val allDeclarations = ToolSchemaTranslator.getAllDeclarations(allDescriptors)
        val forbiddenTerms = listOf(
            "mutation", "approve", "approval", "grant", "receipt", "workflow", "gateway", "commit"
        )

        for (decl in allDeclarations) {
            val lowerName = decl.name.lowercase()
            val lowerDesc = decl.description.lowercase()
            for (term in forbiddenTerms) {
                assertFalse(
                    "Tool '${decl.name}' must not contain forbidden term '$term'",
                    lowerName.contains(term) || lowerDesc.contains(term)
                )
            }
        }
    }

    // 3. Every advertised name is canonical snake_case (must match the executable registry key)
    @Test
    fun allDeclarationNames_areCanonicalSnakeCase() {
        val declarations = ToolSchemaTranslator.getAllDeclarations(allDescriptors)
        for (decl in declarations) {
            assertTrue(
                "Tool name '${decl.name}' must be canonical snake_case",
                decl.name.matches(Regex("^[a-z][a-z0-9_]*$"))
            )
        }
    }

    // 4. Parameter types must be provider-interpretable; no pseudo-type tokens
    @Test
    fun allParameterTypes_areValidProviderTokens() {
        val allowed = setOf("STRING", "INTEGER", "NUMBER", "BOOLEAN", "ARRAY<STRING>", "OBJECT", "ARRAY<OBJECT>")
        val declarations = ToolSchemaTranslator.getAllDeclarations(allDescriptors)

        fun assertValid(params: List<CloudFunctionParameter>, tool: String) {
            for (p in params) {
                assertTrue(
                    "Tool '$tool' parameter '${p.name}' has invalid type '${p.type}'",
                    allowed.contains(p.type)
                )
                assertValid(p.fields, tool)
            }
        }

        for (decl in declarations) {
            assertValid(decl.parameters, decl.name)
        }
    }
}
