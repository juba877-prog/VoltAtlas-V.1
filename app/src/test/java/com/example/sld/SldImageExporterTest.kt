package com.example.sld

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class SldImageExporterTest {

    @Test
    fun renderSldBitmap_returnsNull_whenNodesEmpty() {
        val emptyResult = SldEngineResult(
            nodes = emptyList(),
            edges = emptyList(),
            bounds = Rect(0f, 0f, 1000f, 800f)
        )

        val bitmap = SldImageExporter.renderSldBitmap(emptyResult, "EMPTY_PROJECT")
        assertNull("Empty diagram must return null bitmap", bitmap)
    }

    @Test
    fun renderSldBitmap_returnsValidBitmap_whenNodesPresent() {
        val node1 = PositionedSldNode(
            id = "NODE_TX",
            label = "TX-01",
            panelType = "TRANSFORMER",
            ratingText = "1000 kVA",
            statusColorHex = "#10B981",
            x = 100f,
            y = 50f,
            width = 140f,
            height = 70f,
            isSource = true
        )
        val node2 = PositionedSldNode(
            id = "NODE_MDB",
            label = "MDB-01",
            panelType = "MDB",
            ratingText = "400 A",
            statusColorHex = "#10B981",
            x = 100f,
            y = 200f,
            width = 200f,
            height = 30f,
            isSource = false
        )
        val edge = PositionedSldEdge(
            fromId = "NODE_TX",
            toId = "NODE_MDB",
            label = "4x(1x240) Cu",
            pathPoints = listOf(Pair(170f, 85f), Pair(170f, 200f))
        )
        val engineResult = SldEngineResult(
            nodes = listOf(node1, node2),
            edges = listOf(edge),
            bounds = Rect(0f, 0f, 800f, 600f)
        )

        val bitmapDark = SldImageExporter.renderSldBitmap(engineResult, "TEST_PROJECT", isDarkTheme = true)
        assertNotNull("Bitmap must not be null when nodes exist", bitmapDark)
        assertTrue("Bitmap width must be >= 1400", bitmapDark!!.width >= 1400)
        assertTrue("Bitmap height must be >= 1000", bitmapDark.height >= 1000)
        assertEquals(Bitmap.Config.ARGB_8888, bitmapDark.config)

        val bitmapLight = SldImageExporter.renderSldBitmap(engineResult, "TEST_PROJECT", isDarkTheme = false)
        assertNotNull("Light theme bitmap must not be null", bitmapLight)
        assertEquals(bitmapDark.width, bitmapLight!!.width)
        assertEquals(bitmapDark.height, bitmapLight.height)
    }

    @Test
    fun writeBitmapToStream_writesValidPngStream() {
        val node = PositionedSldNode(
            id = "NODE_GEN",
            label = "GEN-01",
            panelType = "GENERATOR",
            ratingText = "500 kVA",
            statusColorHex = "#10B981",
            x = 50f,
            y = 50f,
            width = 140f,
            height = 70f,
            isSource = true
        )
        val engineResult = SldEngineResult(
            nodes = listOf(node),
            edges = emptyList(),
            bounds = Rect(0f, 0f, 500f, 500f)
        )

        val bitmap = SldImageExporter.renderSldBitmap(engineResult, "PNG_STREAM_TEST")
        assertNotNull(bitmap)

        val outputStream = ByteArrayOutputStream()
        SldImageExporter.writeBitmapToStream(bitmap!!, outputStream)
        val bytes = outputStream.toByteArray()

        assertTrue("PNG byte stream must have non-zero content", bytes.isNotEmpty())
        // Verify PNG magic header: 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals('P'.code.toByte(), bytes[1])
        assertEquals('N'.code.toByte(), bytes[2])
        assertEquals('G'.code.toByte(), bytes[3])
    }
}
