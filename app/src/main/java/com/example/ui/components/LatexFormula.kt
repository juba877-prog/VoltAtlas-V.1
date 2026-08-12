package com.example.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ru.noties.jlatexmath.JLatexMathView

@Composable
fun LatexFormula(
    latex: String,
    modifier: Modifier = Modifier,
    textSizePx: Int = 46,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val argbColor = textColor.toArgb()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        AndroidView(
            factory = { context ->
                JLatexMathView(context).apply {
                    textSize(textSizePx)
                    textColor(argbColor)
                    align(JLatexMathView.ALIGN_CENTER, JLatexMathView.ALIGN_CENTER)
                    setLatex(latex)
                }
            },
            update = { view ->
                view.textSize(textSizePx)
                view.textColor(argbColor)
                view.align(JLatexMathView.ALIGN_CENTER, JLatexMathView.ALIGN_CENTER)
                view.setLatex(latex)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        )
    }
}
