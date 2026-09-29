package com.example.camera.nameplate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * On-device OCR processor for equipment nameplates using ML Kit Text Recognition (Latin).
 * Converts camera bitmaps or imported file URIs into structured NameplateScanResult.
 */
object NameplateOcrProcessor {

    fun processBitmap(
        bitmap: Bitmap,
        onSuccess: (result: NameplateScanResult, rawText: String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        try {
            // Hardware bitmaps must be converted to software bitmaps for ML Kit processing
            val softwareBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
            } else {
                bitmap
            }

            val image = InputImage.fromBitmap(softwareBitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val rawText = visionText.text
                    val result = NameplateParser.parse(rawText)
                    onSuccess(result, rawText)
                }
                .addOnFailureListener { error ->
                    onError(error)
                }
        } catch (e: Throwable) {
            onError(e)
        }
    }

    fun processUri(
        context: Context,
        uri: Uri,
        onSuccess: (result: NameplateScanResult, rawText: String, bitmap: Bitmap?) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        try {
            val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }

            processBitmap(
                bitmap = bitmap,
                onSuccess = { result, rawText ->
                    onSuccess(result, rawText, bitmap)
                },
                onError = onError
            )
        } catch (e: Throwable) {
            onError(e)
        }
    }
}
