package com.example.camera.nameplate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Listener callback interface for receiving real-time equipment nameplate detections.
 */
fun interface NameplateScanListener {
    fun onNameplateScanned(result: NameplateScanResult)
}

/**
 * High-level nameplate analyzer managing text frame stabilization, debouncing,
 * and electrical specification accumulation.
 */
class NameplateAnalyzer(
    parentScope: CoroutineScope? = null,
    private val minConfidenceThreshold: Float = 0.4f,
    private val listener: NameplateScanListener
) : AutoCloseable {
    private val job = SupervisorJob(parentScope?.coroutineContext?.get(kotlinx.coroutines.Job))
    private val scope = if (parentScope != null) {
        CoroutineScope(parentScope.coroutineContext + job)
    } else {
        CoroutineScope(job + Dispatchers.Default)
    }
    private var lastScannedResult: NameplateScanResult? = null
    private var consecutiveStableFrames: Int = 0

    /**
     * Process raw text recognized from an optical frame (CameraX or ML Kit Text Recognition).
     */
    fun processTextFrame(recognizedText: String) {
        if (recognizedText.isBlank()) return

        scope.launch {
            val result = NameplateParser.parse(recognizedText)
            if (result.confidenceScore >= minConfidenceThreshold) {
                // Check stability with prior frame
                val isSimilarToLast = lastScannedResult?.let { last ->
                    last.equipmentType == result.equipmentType &&
                            (last.powerKw == result.powerKw || last.kvaRating == result.kvaRating)
                } ?: false

                if (isSimilarToLast) {
                    consecutiveStableFrames++
                } else {
                    consecutiveStableFrames = 1
                }
                lastScannedResult = result

                // Listener notified on the analysis coroutine scope.
                // Callers requiring UI-thread dispatch (e.g. Compose state updates)
                // perform their own main-thread dispatching.
                listener.onNameplateScanned(result)
            }
        }
    }

    fun reset() {
        lastScannedResult = null
        consecutiveStableFrames = 0
    }

    override fun close() {
        cancel()
    }

    fun cancel() {
        job.cancel()
    }
}
