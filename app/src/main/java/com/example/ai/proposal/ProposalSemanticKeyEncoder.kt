package com.example.ai.proposal

import com.example.ai.context.freshness.internal.CanonicalFingerprintEncoder
import java.security.MessageDigest
import java.util.Locale

const val PROPOSAL_SEMANTIC_VERSION_V1 = "PROPOSAL-SEMANTIC-V1"

/**
 * Interface responsible for computing canonical deterministic byte encodings
 * and SHA-256 semantic keys for [EngineeringProposal] envelopes.
 *
 * Excludes presentation rationale, timestamps, source request IDs, and engineering evidence.
 */
interface ProposalSemanticKeyEncoder {
    /**
     * Computes the raw canonical bytes representing the authoritative mutation semantics.
     */
    fun canonicalSemanticBytes(
        schemaVersion: String,
        projectId: String,
        baseProjectRevision: Long,
        targetEntityType: String,
        targetEntityId: String,
        operation: ProposalOperation,
        patch: Any?
    ): ByteArray

    /**
     * Computes the authoritative, version-prefixed SHA-256 semantic key.
     * Format: `PROPOSAL-SEMANTIC-V1:<64 lowercase hex characters>`
     */
    fun computeSemanticKey(
        schemaVersion: String,
        projectId: String,
        baseProjectRevision: Long,
        targetEntityType: String,
        targetEntityId: String,
        operation: ProposalOperation,
        patch: Any?
    ): String

    /**
     * Computes the semantic key directly from an [EngineeringProposal] envelope.
     */
    fun computeSemanticKey(proposal: EngineeringProposal<*>): String
}

/**
 * Authoritative default implementation of [ProposalSemanticKeyEncoder].
 *
 * Framing Invariants:
 * - Version Prefix: `PROPOSAL-SEMANTIC-V1:`
 * - Unicode NFC normalization for string fields.
 * - Decimal numbers stripped of trailing zeros in Locale.ROOT (locale-neutral machine encoding).
 * - Key-sorted nested framing for deterministic serialization.
 * - Excludes non-authoritative presentation rationale, timestamps, source request IDs, and evidence.
 */
class DefaultProposalSemanticKeyEncoder : ProposalSemanticKeyEncoder {

    companion object {
        const val VERSION = PROPOSAL_SEMANTIC_VERSION_V1
    }

    override fun canonicalSemanticBytes(
        schemaVersion: String,
        projectId: String,
        baseProjectRevision: Long,
        targetEntityType: String,
        targetEntityId: String,
        operation: ProposalOperation,
        patch: Any?
    ): ByteArray {
        val encoder = CanonicalFingerprintEncoder()
        encoder.putString("schemaVersion", schemaVersion)
        encoder.putString("projectId", projectId)
        encoder.putLong("baseProjectRevision", baseProjectRevision)
        encoder.putString("targetEntityType", targetEntityType)
        encoder.putString("targetEntityId", targetEntityId)
        encoder.putString("operation", operation.name)

        encoder.putNested("patch") { p ->
            encodePatch(p, patch)
        }

        return encoder.encodeCanonicalBytes()
    }

    override fun computeSemanticKey(
        schemaVersion: String,
        projectId: String,
        baseProjectRevision: Long,
        targetEntityType: String,
        targetEntityId: String,
        operation: ProposalOperation,
        patch: Any?
    ): String {
        return try {
            val bytes = canonicalSemanticBytes(
                schemaVersion = schemaVersion,
                projectId = projectId,
                baseProjectRevision = baseProjectRevision,
                targetEntityType = targetEntityType,
                targetEntityId = targetEntityId,
                operation = operation,
                patch = patch
            )
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            val hex = digest.joinToString("") { "%02x".format(Locale.ROOT, it) }
            "$VERSION:$hex"
        } catch (t: Throwable) {
            throw IllegalStateException("Failed to compute canonical proposal semantic key: ${t.message}", t)
        }
    }

    override fun computeSemanticKey(proposal: EngineeringProposal<*>): String {
        return computeSemanticKey(
            schemaVersion = proposal.schemaVersion,
            projectId = proposal.projectId,
            baseProjectRevision = proposal.baseProjectRevision,
            targetEntityType = proposal.targetEntityType,
            targetEntityId = proposal.targetEntityId,
            operation = proposal.operation,
            patch = proposal.patch
        )
    }

    private fun encodePatch(encoder: CanonicalFingerprintEncoder, patch: Any?) {
        when (patch) {
            null -> {
                encoder.putString("__type__", "NULL")
            }
            is CableCircuitPatch -> {
                encoder.putString("__type__", "CableCircuitPatch")
                patch.cableType?.let { pv ->
                    encoder.putNested("cableType") {
                        it.putString("action", pv.action.name)
                        it.putString("value", pv.value)
                    }
                }
                patch.installation?.let { pv ->
                    encoder.putNested("installation") {
                        it.putString("action", pv.action.name)
                        it.putString("value", pv.value)
                    }
                }
                patch.lengthM?.let { pv ->
                    encoder.putNested("lengthM") {
                        it.putString("action", pv.action.name)
                        it.putDouble("value", pv.value)
                    }
                }
                patch.manufacturer?.let { pv ->
                    encoder.putNested("manufacturer") {
                        it.putString("action", pv.action.name)
                        it.putString("value", pv.value)
                    }
                }
                patch.overrideCableSize?.let { pv ->
                    encoder.putNested("overrideCableSize") {
                        it.putString("action", pv.action.name)
                        it.putString("value", pv.value)
                    }
                }
                patch.overrideDeviceSize?.let { pv ->
                    encoder.putNested("overrideDeviceSize") {
                        it.putString("action", pv.action.name)
                        it.putDouble("value", pv.value)
                    }
                }
                patch.overrideDeviceType?.let { pv ->
                    encoder.putNested("overrideDeviceType") {
                        it.putString("action", pv.action.name)
                        it.putString("value", pv.value)
                    }
                }
                patch.parallelRuns?.let { pv ->
                    encoder.putNested("parallelRuns") {
                        it.putString("action", pv.action.name)
                        it.putInt("value", pv.value)
                    }
                }
            }
            else -> {
                encoder.putString("__type__", patch.javaClass.name)
                encoder.putString("value", patch.toString())
            }
        }
    }
}
