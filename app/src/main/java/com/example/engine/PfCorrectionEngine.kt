package com.example.engine

import com.example.data.PfGlobalEntity
import com.example.data.PfTransformerEntity
import com.example.data.TransformerEntity
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.abs

data class PfCorrectionResult(
    val transformerId: String,
    val existingKW: Double,
    val existingPF: Double,
    val targetPf: Double,
    val existingKvar: Double,
    val targetKvar: Double,
    val requiredKvar: Double,
    val bankSteps: Double,
    val bankCapacity: Double,
    val bankCurrent: Double,
    val correctedReactive: Double,
    val correctedPF: Double,
    val correctedKVA: Double = 0.0,
    val pfState: String,
    val status: String,
    val messages: List<String>
)

object PfCorrectionEngine {
    fun calculate(
        globalSettings: PfGlobalEntity,
        transformerSettings: List<PfTransformerEntity>,
        transformers: List<TransformerEntity>,
        transformerResults: Map<String, TransformerResult>
    ): List<PfCorrectionResult> {
        return transformers.map { tx ->
            val result = transformerResults[tx.transformerId]
            val activePowerKW = result?.demandLoad ?: 0.0
            val existingPF = tx.pf
            val targetPF = globalSettings.targetPf

            if (tx.status != "Active") {
                return@map buildNullResult(tx.transformerId, activePowerKW, existingPF, targetPF, "INACTIVE")
            }

            if (activePowerKW < 0.0 || 
                existingPF <= 0.0 || existingPF > 1.0 || 
                targetPF <= 0.0 || targetPF > 1.0 ||
                globalSettings.kvarStepSize <= 0.0 ||
                activePowerKW.isNaN() || activePowerKW.isInfinite() ||
                existingPF.isNaN() || existingPF.isInfinite() ||
                targetPF.isNaN() || targetPF.isInfinite()
            ) {
                return@map buildNullResult(tx.transformerId, activePowerKW, existingPF, targetPF, "INVALID INPUT")
            }

            if (targetPF <= existingPF) {
                val existingKvar = activePowerKW * tan(acos(existingPF))
                val existingKVA = if (existingPF > 0.0) activePowerKW / existingPF else 0.0
                return@map PfCorrectionResult(
                    transformerId = tx.transformerId,
                    existingKW = activePowerKW,
                    existingPF = existingPF,
                    targetPf = targetPF,
                    existingKvar = existingKvar,
                    targetKvar = existingKvar,
                    requiredKvar = 0.0,
                    bankSteps = 0.0,
                    bankCapacity = 0.0,
                    bankCurrent = 0.0,
                    correctedReactive = existingKvar,
                    correctedPF = existingPF,
                    correctedKVA = Math.round(existingKVA * 100.0) / 100.0,
                    pfState = when {
                        abs(existingKvar) <= globalSettings.unityToleranceKvar -> "UNITY"
                        existingKvar < 0.0 -> "LEADING"
                        else -> "LAGGING"
                    },
                    status = "NO CORRECTION REQUIRED",
                    messages = listOf("Existing power factor ($existingPF) already meets or exceeds target ($targetPF).")
                )
            }

            val existingKvar = activePowerKW * tan(acos(existingPF))
            val targetKvar = activePowerKW * tan(acos(targetPF))
            val requiredKvar = activePowerKW * (tan(acos(existingPF)) - tan(acos(targetPF)))

            val bankSteps = ceil(requiredKvar / globalSettings.kvarStepSize)
            val bankCapacity = bankSteps * globalSettings.kvarStepSize

            val txSettings = transformerSettings.firstOrNull { it.transformerId == tx.transformerId }
            val hasHarmonicFilter = txSettings?.harmonicFilter == "Yes"
            val hasDetuningReactor = txSettings?.detuningReactor == "Yes"
            val missingHarmonicInputs = !hasHarmonicFilter && !hasDetuningReactor

            val detuningBoost = if (hasDetuningReactor) 1.075 else 1.0
            val bankCurrent = if (globalSettings.systemVoltage > 0.0) {
                (bankCapacity * 1000.0 / (sqrt(3.0) * globalSettings.systemVoltage)) * detuningBoost
            } else 0.0

            val correctedReactive = existingKvar - bankCapacity
            val correctedKVA_calc = sqrt(activePowerKW * activePowerKW + correctedReactive * correctedReactive)
            val correctedPF = if (correctedKVA_calc > 0.0) activePowerKW / correctedKVA_calc else 1.0

            val pfState = when {
                abs(correctedReactive) <= globalSettings.unityToleranceKvar -> "UNITY"
                correctedReactive < 0.0 -> "LEADING"
                else -> "LAGGING"
            }

            val status = when {
                bankCapacity < requiredKvar -> "FAIL — UNDERSIZED"
                correctedPF > 0.98 -> "REVIEW — BANK CAPACITY EXCEEDS PEAK REQUIREMENT${if (missingHarmonicInputs) " / HARMONIC INPUTS OPEN" else ""}"
                correctedPF < targetPF -> "FAIL — TARGET PF NOT MET"
                missingHarmonicInputs -> "REVIEW — HARMONIC/DETUNING INPUTS"
                else -> "COMPLIANT"
            }

            PfCorrectionResult(
                transformerId = tx.transformerId,
                existingKW = activePowerKW,
                existingPF = existingPF,
                targetPf = targetPF,
                existingKvar = existingKvar,
                targetKvar = targetKvar,
                requiredKvar = requiredKvar,
                bankSteps = bankSteps,
                bankCapacity = bankCapacity,
                bankCurrent = bankCurrent,
                correctedReactive = correctedReactive,
                correctedPF = correctedPF,
                correctedKVA = Math.round(correctedKVA_calc * 100.0) / 100.0,
                pfState = pfState,
                status = status,
                messages = emptyList()
            )
        }
    }

    private fun buildNullResult(transformerId: String, existingKW: Double, existingPF: Double, targetPf: Double, status: String): PfCorrectionResult {
        val existingKvar = if (existingPF > 0.0 && existingPF <= 1.0 && existingKW >= 0.0 && !existingPF.isNaN() && !existingKW.isNaN()) {
            existingKW * tan(acos(existingPF))
        } else 0.0
        val existingKVA = if (existingPF > 0.0) existingKW / existingPF else 0.0
        return PfCorrectionResult(
            transformerId = transformerId,
            existingKW = existingKW,
            existingPF = existingPF,
            targetPf = targetPf,
            existingKvar = existingKvar,
            targetKvar = 0.0,
            requiredKvar = 0.0,
            bankSteps = 0.0,
            bankCapacity = 0.0,
            bankCurrent = 0.0,
            correctedReactive = existingKvar,
            correctedPF = existingPF,
            correctedKVA = Math.round(existingKVA * 100.0) / 100.0,
            pfState = if (existingPF >= 1.0) "UNITY" else "LAGGING",
            status = status,
            messages = emptyList()
        )
    }
}
