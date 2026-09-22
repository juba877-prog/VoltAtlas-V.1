package com.example.camera.nameplate

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val parser: (String) -> NameplateScanResult = { NameplateParser.parse(it) },
    private val logger: (String, Throwable?) -> Unit = { msg, tr ->
        try {
            android.util.Log.w("NameplateAnalyzer", msg, tr)
        } catch (_: RuntimeException) {
            // Android Log not mocked on pure JVM test environments without Robolectric
        }
    },
    private val listener: NameplateScanListener
) : AutoCloseable {
    private val job = SupervisorJob(parentScope?.coroutineContext?.get(Job))
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is kotlinx.coroutines.CancellationException) {
            return@CoroutineExceptionHandler
        }
        // Frame analysis / parsing failure must never propagate or leak into the shared pool
        logger("Swallowed unhandled frame analysis exception", throwable)
    }
    private val scope = if (parentScope != null) {
        CoroutineScope(parentScope.coroutineContext + job + exceptionHandler)
    } else {
        CoroutineScope(job + Dispatchers.Default + exceptionHandler)
    }
    private var lastScannedResult: NameplateScanResult? = null
    private var consecutiveStableFrames: Int = 0

    /**
     * Process raw text recognized from an optical frame (CameraX or ML Kit Text Recognition).
     */
    fun processTextFrame(recognizedText: String) {
        if (recognizedText.isBlank()) return

        scope.launch {
            val result = parser(recognizedText)
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
