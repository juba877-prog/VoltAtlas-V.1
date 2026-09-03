package com.example.sld

import android.net.Uri
import androidx.core.content.FileProvider

/**
 * Custom FileProvider ensuring correct MIME type resolution for CAD files (.dxf, .dwg),
 * which are absent from the standard Android framework MimeTypeMap.
 */
class CadFileProvider : FileProvider() {
    override fun getType(uri: Uri): String? {
        val path = uri.path.orEmpty()
        return when {
            path.endsWith(".dxf", ignoreCase = true) -> "application/dxf"
            path.endsWith(".dwg", ignoreCase = true) -> "application/acad"
            path.endsWith(".png", ignoreCase = true) -> "image/png"
            path.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            else -> super.getType(uri)
        }
    }
}
