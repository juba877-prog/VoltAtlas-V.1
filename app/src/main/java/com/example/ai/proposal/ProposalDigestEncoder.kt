package com.example.ai.proposal

import com.example.ai.contracts.EngineeringValueKind
import com.example.ai.context.freshness.internal.CanonicalFingerprintEncoder
import java.security.MessageDigest
import java.util.Locale

/**
 * Interface responsible for computing canonical deterministic byte encodings
 * and SHA-256 digests for [EngineeringProposal] envelopes.
 */
interface ProposalDigestEncoder {
    /**
     * Serializes the proposal into canonical UTF-8 bytes with stable field framing and ordering.
     */
    fun canonicalBytes(proposal: EngineeringProposal<*>): ByteArray

    /**
     * Computes the authoritative, version-prefixed SHA-256 digest string.
     * Format: `PROPOSAL-V1:<64 lowercase hex characters>`
     */
    fun computeDigest(proposal: EngineeringProposal<*>): String
}

/**
 * Authoritative default implementation of [ProposalDigestEncoder].
 *
 * Framing Invariants:
 * - Version Prefix: `PROPOSAL-V1:`
 * - Unicode NFC normalization for all string fields.
 * - Decimal numbers stripped of trailing zeros in Locale.ROOT (no locale variance).
 * - Key-sorted nested framing for deterministic serialization.
 * - Digest-Bound Rationale Policy: Human-readable rationale is bound to the digest to prevent post-approval tampering.
 */
class DefaultProposalDigestEncoder : ProposalDigestEncoder {

    companion object {
        const val VERSION = PROPOSAL_SCHEMA_VERSION_V1
    }

    override fun canonicalBytes(proposal: EngineeringProposal<*>): ByteArray {
        val encoder = CanonicalFingerprintEncoder()
        encoder.putString("schemaVersion", proposal.schemaVersion)
        encoder.putString("projectId", proposal.projectId)
        encoder.putLong("baseProjectRevision", proposal.baseProjectRevision)
        encoder.putString("targetEntityType", proposal.targetEntityType)
        encoder.putString("targetEntityId", proposal.targetEntityId)
        encoder.putString("operation", proposal.operation.name)

        encoder.putNested("patch") { p ->
            encodePatch(p, proposal.patch)
        }

        encoder.putString("sourceToolName", proposal.sourceToolName)
        encoder.putString("sourceRequestId", proposal.sourceRequestId)
        encoder.putString("rationale", proposal.rationale)
        encoder.putLong("createdAt", proposal.createdAt)
        encoder.putLong("expiresAt", proposal.expiresAt)

        proposal.engineeringEvidence?.let { ev ->
            encoder.putNested("engineeringEvidence") { e ->
                e.putString("evidenceType", ev.evidenceType)
                e.putString("calculationReference", ev.calculationReference)
                e.putString("governingCriterion", ev.governingCriterion)
                e.putNested("values") { vMap ->
                    ev.values.toSortedMap().forEach { (k, v) ->
                        vMap.putNested("k:$k") { vChild ->
                            vChild.putString("kind", v.kind.name)
                            vChild.putString("unit", v.unit?.name)
                            when (v.kind) {
                                EngineeringValueKind.DECIMAL -> v.decimalValue?.let { vChild.putDouble("value", it) }
                                EngineeringValueKind.INTEGER -> v.intValue?.let { vChild.putLong("value", it) }
                                EngineeringValueKind.TEXT -> v.textValue?.let { vChild.putString("value", it) }
                                EngineeringValueKind.BOOLEAN -> v.booleanValue?.let { vChild.putBoolean("value", it) }
                                EngineeringValueKind.MISSING -> vChild.putString("value", "MISSING")
                            }
                        }
                    }
                }
            }
        }

        return encoder.encodeCanonicalBytes()
    }

    override fun computeDigest(proposal: EngineeringProposal<*>): String {
        return try {
            val bytes = canonicalBytes(proposal)
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            val hex = digest.joinToString("") { "%02x".format(Locale.ROOT, it) }
            "$VERSION:$hex"
        } catch (t: Throwable) {
            throw IllegalStateException("Failed to compute canonical proposal digest: ${t.message}", t)
        }
    }

    private fun encodePatch(encoder: CanonicalFingerprintEncoder, patch: Any?) {
        when (patch) {
            null -> {
                encoder.putString("__type__", "NULL")
            }
            is CableCircuitPatch -> {
                encoder.putString("__type__", "CableCircuitPatch")
                patch.overrideCableSize?.let { pv ->
                    encoder.putNested("overrideCableSize") {
                        it.putString("action", pv.action.name)
                        it.putString("value", pv.value)
                    }
                }
                patch.cableType?.let { pv ->
                    encoder.putNested("cableType") {
                        it.putString("action", pv.action.name)
                        it.putString("value", pv.value)
                    }
                }
                patch.manufacturer?.let { pv ->
                    encoder.putNested("manufacturer") {
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
                patch.parallelRuns?.let { pv ->
                    encoder.putNested("parallelRuns") {
                        it.putString("action", pv.action.name)
                        it.putInt("value", pv.value)
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
                patch.lengthM?.let { pv ->
                    encoder.putNested("lengthM") {
                        it.putString("action", pv.action.name)
                        it.putDouble("value", pv.value)
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
