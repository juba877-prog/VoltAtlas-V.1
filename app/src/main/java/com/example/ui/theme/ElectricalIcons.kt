package com.example.ui.theme

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object ElectricalIcons {
    val Cyan = Color(0xFF08B7E8)
    val Green = Color(0xFF19C979)

    private fun createDuotoneIcon(
        name: String,
        builder: ImageVector.Builder.() -> Unit
    ): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(builder).build()
    }

    // 1. TRANSFORMER (Two overlapping circles, IEC symbol)
    val Transformer: ImageVector = createDuotoneIcon("Transformer") {
        // Left circle: center (9.5, 12), radius 5.5 — CYAN outline + 22% cyan fill
        path(
            fill = SolidColor(Cyan),
            fillAlpha = 0.22f,
            stroke = SolidColor(Cyan),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(15f, 12f)
            arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 4f, 12f)
            arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 15f, 12f)
            close()
        }
        // Right circle: center (14.5, 12), radius 5.5 — GREEN outline + 22% green fill
        path(
            fill = SolidColor(Green),
            fillAlpha = 0.22f,
            stroke = SolidColor(Green),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(20f, 12f)
            arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 9f, 12f)
            arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 20f, 12f)
            close()
        }
    }

    // 2. HOME
    val Home: ImageVector = createDuotoneIcon("Home") {
        // House
        path(
            fill = SolidColor(Cyan),
            fillAlpha = 0.22f,
            stroke = SolidColor(Cyan),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 3f)
            lineTo(20f, 10f)
            lineTo(19f, 10f)
            lineTo(19f, 20f)
            lineTo(5f, 20f)
            lineTo(5f, 10f)
            lineTo(4f, 10f)
            close()
        }
        // Door
        path(
            stroke = SolidColor(Green),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(10f, 20f)
            lineTo(10f, 13f)
            lineTo(14f, 13f)
            lineTo(14f, 20f)
        }
    }

    // 3. CIRCUITS
    val Circuits: ImageVector = createDuotoneIcon("Circuits") {
        // Connecting line A -> B
        path(
            stroke = SolidColor(Cyan),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(7f, 8f)
            lineTo(17f, 16f)
        }
        // Terminal stubs
        path(
            stroke = SolidColor(Cyan),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(7f, 8f)
            lineTo(7f, 3f)
            moveTo(17f, 16f)
            lineTo(17f, 21f)
        }
        // Node A
        path(
            fill = SolidColor(Cyan),
            stroke = SolidColor(Cyan),
            strokeLineWidth = 2f
        ) {
            moveTo(9.5f, 8f)
            arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 4.5f, 8f)
            arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 9.5f, 8f)
            close()
        }
        // Node B
        path(
            fill = SolidColor(Green),
            stroke = SolidColor(Green),
            strokeLineWidth = 2f
        ) {
            moveTo(19.5f, 16f)
            arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 14.5f, 16f)
            arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 19.5f, 16f)
            close()
        }
    }

    // 4. POWER (Lightning bolt)
    val Power: ImageVector = createDuotoneIcon("Power") {
        path(
            fill = SolidColor(Cyan),
            fillAlpha = 0.22f,
            stroke = SolidColor(Cyan),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(13f, 3f)
            lineTo(6f, 13f)
            lineTo(11f, 13f)
            lineTo(9f, 21f)
            lineTo(17f, 10f)
            lineTo(12f, 10f)
            close()
        }
    }

    // 5. PROTECTION (Shield + Breaker Z)
    val Protection: ImageVector = createDuotoneIcon("Protection") {
        // Shield
        path(
            fill = SolidColor(Green),
            fillAlpha = 0.22f,
            stroke = SolidColor(Green),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 3f)
            lineTo(20f, 7f)
            quadTo(20f, 15f, 12f, 21f)
            quadTo(4f, 15f, 4f, 7f)
            close()
        }
        // Breaker toggle Z
        path(
            stroke = SolidColor(Cyan),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9f, 11f)
            lineTo(13f, 11f)
            lineTo(11f, 14f)
            lineTo(15f, 14f)
        }
    }

    // 6. SHORT CIRCUIT
    val ShortCircuit: ImageVector = createDuotoneIcon("ShortCircuit") {
        // Center dot
        path(
            fill = SolidColor(Cyan),
            stroke = SolidColor(Cyan),
            strokeLineWidth = 2f
        ) {
            moveTo(15f, 12f)
            arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 9f, 12f)
            arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 15f, 12f)
            close()
        }
        // 4 rays
        path(
            stroke = SolidColor(Green),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 7f); lineTo(12f, 2f)
            moveTo(12f, 17f); lineTo(12f, 22f)
            moveTo(7f, 12f); lineTo(2f, 12f)
            moveTo(17f, 12f); lineTo(22f, 12f)
        }
        // 4 diagonal sparks
        path(
            stroke = SolidColor(Cyan),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(15.5f, 8.5f); lineTo(19f, 5f)
            moveTo(15.5f, 15.5f); lineTo(19f, 19f)
            moveTo(8.5f, 15.5f); lineTo(5f, 19f)
            moveTo(8.5f, 8.5f); lineTo(5f, 5f)
        }
    }

    // 7. EARTHING
    val Earthing: ImageVector = createDuotoneIcon("Earthing") {
        // Vertical line
        path(
            stroke = SolidColor(Cyan),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 3f)
            lineTo(12f, 12f)
        }
        // Top dot
        path(
            fill = SolidColor(Cyan),
            stroke = SolidColor(Cyan),
            strokeLineWidth = 1.5f
        ) {
            moveTo(13.5f, 3f)
            arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 10.5f, 3f)
            arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 13.5f, 3f)
            close()
        }
        // 3 horizontal bars
        path(
            stroke = SolidColor(Cyan),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(6f, 12f); lineTo(18f, 12f)
        }
        path(
            stroke = SolidColor(Green),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(8f, 15f); lineTo(16f, 15f)
            moveTo(10f, 18f); lineTo(14f, 18f)
        }
    }

    // 8. SLD
    val Sld: ImageVector = createDuotoneIcon("Sld") {
        // Top node
        path(
            fill = SolidColor(Cyan),
            fillAlpha = 0.22f,
            stroke = SolidColor(Cyan),
            strokeLineWidth = 2f
        ) {
            moveTo(14.5f, 5f)
            arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 9.5f, 5f)
            arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 14.5f, 5f)
            close()
        }
        // Vertical & busbar & drops
        path(
            stroke = SolidColor(Cyan),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 7.5f); lineTo(12f, 12f)
            moveTo(5f, 12f); lineTo(19f, 12f)
            moveTo(7f, 12f); lineTo(7f, 15f)
            moveTo(17f, 12f); lineTo(17f, 15f)
        }
        // Two branch boxes
        path(
            fill = SolidColor(Green),
            fillAlpha = 0.22f,
            stroke = SolidColor(Green),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4f, 15f); lineTo(10f, 15f); lineTo(10f, 20f); lineTo(4f, 20f); close()
            moveTo(14f, 15f); lineTo(20f, 15f); lineTo(20f, 20f); lineTo(14f, 20f); close()
        }
    }

    // 9. CONDUIT
    val Conduit: ImageVector = createDuotoneIcon("Conduit") {
        // Rounded pipe
        path(
            fill = SolidColor(Cyan),
            fillAlpha = 0.22f,
            stroke = SolidColor(Cyan),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(8f, 8f)
            lineTo(16f, 8f)
            quadTo(20f, 8f, 20f, 12f)
            quadTo(20f, 16f, 16f, 16f)
            lineTo(8f, 16f)
            quadTo(4f, 16f, 4f, 12f)
            quadTo(4f, 8f, 8f, 8f)
            close()
        }
        // 3 conductor dots inside at y=12
        path(fill = SolidColor(Green)) {
            moveTo(10.8f, 12f)
            arcTo(1.8f, 1.8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 7.2f, 12f)
            arcTo(1.8f, 1.8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 10.8f, 12f)
            close()
        }
        path(fill = SolidColor(Cyan)) {
            moveTo(13.8f, 12f)
            arcTo(1.8f, 1.8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 10.2f, 12f)
            arcTo(1.8f, 1.8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 13.8f, 12f)
            close()
        }
        path(fill = SolidColor(Green)) {
            moveTo(16.8f, 12f)
            arcTo(1.8f, 1.8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 13.2f, 12f)
            arcTo(1.8f, 1.8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 16.8f, 12f)
            close()
        }
    }

    // 10. BOQ
    val Boq: ImageVector = createDuotoneIcon("Boq") {
        // Page
        path(
            fill = SolidColor(Green),
            fillAlpha = 0.22f,
            stroke = SolidColor(Green),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(8f, 3f)
            lineTo(16f, 3f)
            quadTo(18f, 3f, 18f, 5f)
            lineTo(18f, 19f)
            quadTo(18f, 21f, 16f, 21f)
            lineTo(8f, 21f)
            quadTo(6f, 21f, 6f, 19f)
            lineTo(6f, 5f)
            quadTo(6f, 3f, 8f, 3f)
            close()
        }
        // 3 list lines
        path(
            stroke = SolidColor(Cyan),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(8f, 8f); lineTo(16f, 8f)
            moveTo(8f, 12f); lineTo(16f, 12f)
            moveTo(8f, 16f); lineTo(13f, 16f)
        }
    }

    // 11. SETTINGS
    val Settings: ImageVector = createDuotoneIcon("Settings") {
        // Ring
        path(
            fill = SolidColor(Cyan),
            fillAlpha = 0.22f,
            stroke = SolidColor(Cyan),
            strokeLineWidth = 2f
        ) {
            moveTo(18f, 12f)
            arcTo(6f, 6f, 0f, isMoreThanHalf = true, isPositiveArc = true, 6f, 12f)
            arcTo(6f, 6f, 0f, isMoreThanHalf = true, isPositiveArc = true, 18f, 12f)
            close()
        }
        // Center hole
        path(
            stroke = SolidColor(Green),
            strokeLineWidth = 2f
        ) {
            moveTo(14.5f, 12f)
            arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 9.5f, 12f)
            arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 14.5f, 12f)
            close()
        }
        // 8 teeth ticks
        path(
            stroke = SolidColor(Cyan),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 6f); lineTo(12f, 3f)
            moveTo(12f, 18f); lineTo(12f, 21f)
            moveTo(6f, 12f); lineTo(3f, 12f)
            moveTo(18f, 12f); lineTo(21f, 12f)
            moveTo(16.2f, 7.8f); lineTo(18.3f, 5.7f)
            moveTo(7.8f, 7.8f); lineTo(5.7f, 5.7f)
            moveTo(16.2f, 16.2f); lineTo(18.3f, 18.3f)
            moveTo(7.8f, 16.2f); lineTo(5.7f, 18.3f)
        }
    }
}

@Composable
fun ElectricalIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = Color.Unspecified
    )
}
