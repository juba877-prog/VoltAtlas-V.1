package com.example.sld

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SymbolGalleryItem(
    val title: String,
    val iecCode: String,
    val spec: String,
    val symbolType: SldSymbols.IecSymbolType
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SldSymbolGallery(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    val currentThemeIsDark = com.example.ui.theme.LocalThemeIsDark.current
    var isDark by remember(currentThemeIsDark) { mutableStateOf(currentThemeIsDark) }
    val palette = SldTheme.palette(isDark)

    val symbols = remember {
        listOf(
            SymbolGalleryItem("Utility Grid Source", "IEC 60617-6", "22.0 kV Incomer", SldSymbols.IecSymbolType.UTILITY_SOURCE),
            SymbolGalleryItem("Power Transformer", "IEC 60617-6", "1000 kVA 22/0.4kV", SldSymbols.IecSymbolType.TRANSFORMER),
            SymbolGalleryItem("Diesel Generator", "IEC 60617-6", "500 kVA Standby", SldSymbols.IecSymbolType.GENERATOR),
            SymbolGalleryItem("Auto Transfer Switch", "IEC 60617-7", "ATS 800A 4P", SldSymbols.IecSymbolType.ATS),
            SymbolGalleryItem("Air Circuit Breaker", "IEC 60617-7", "ACB 2500A 50kA", SldSymbols.IecSymbolType.AIR_CIRCUIT_BREAKER_ACB),
            SymbolGalleryItem("Molded Case Breaker", "IEC 60617-7", "MCCB 250A 3P", SldSymbols.IecSymbolType.MOLDED_CASE_BREAKER_MCCB),
            SymbolGalleryItem("Switch Disconnector", "IEC 60617-7", "Isolator 800A 3P", SldSymbols.IecSymbolType.SWITCH_DISCONNECTOR),
            SymbolGalleryItem("Busway Trunking", "IEC 60617-6", "Cu 2500A 3L+N", SldSymbols.IecSymbolType.BUSWAY),
            SymbolGalleryItem("Uninterruptible Power", "IEC 60617-6", "UPS 100 kVA", SldSymbols.IecSymbolType.UPS),
            SymbolGalleryItem("Capacitor Bank", "IEC 60617-6", "APFC 150 kVAR", SldSymbols.IecSymbolType.CAPACITOR_BANK),
            SymbolGalleryItem("Digital Multi Meter", "IEC 60617-8", "DMM V/A/Hz/PF", SldSymbols.IecSymbolType.DIGITAL_MULTI_METER_DMM),
            SymbolGalleryItem("Indication Lamp", "IEC 60617-8", "Pilot Light 230V", SldSymbols.IecSymbolType.INDICATION_LAMP),
            SymbolGalleryItem("Digital kWh Meter", "IEC 60617-8", "Energy Meter RS485", SldSymbols.IecSymbolType.DIGITAL_KWH_METER),
            SymbolGalleryItem("Final Load Arrow", "IEC 60617-2", "Feeder Load Drop", SldSymbols.IecSymbolType.LOAD_ARROW)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(16.dp)
            .testTag("symbol_gallery_container")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "IEC 60617 Symbol Library",
                    color = palette.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Gate C.1 — Parameterized Theme-Aware Symbols",
                    color = palette.textSecondary,
                    fontSize = 12.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = !isDark,
                    onClick = { isDark = false },
                    label = { Text("Light Theme") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.enclosureBusTaps,
                        selectedLabelColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = isDark,
                    onClick = { isDark = true },
                    label = { Text("Dark Theme") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.enclosureBusTaps,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(symbols) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .border(1.dp, palette.tierDatum, RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = palette.surface),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = item.title,
                            color = palette.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )

                        Canvas(
                            modifier = Modifier
                                .size(64.dp)
                                .padding(4.dp)
                        ) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val sizePx = size.width
                            when (item.symbolType) {
                                SldSymbols.IecSymbolType.UTILITY_SOURCE -> SldSymbols.drawUtilitySource(this, center, sizePx, palette, item.spec)
                                SldSymbols.IecSymbolType.TRANSFORMER -> SldSymbols.drawTransformer(this, center, sizePx, palette, item.spec)
                                SldSymbols.IecSymbolType.GENERATOR -> SldSymbols.drawGenerator(this, center, sizePx, palette, item.spec)
                                SldSymbols.IecSymbolType.ATS -> SldSymbols.drawAts(this, center, sizePx, palette, item.spec)
                                SldSymbols.IecSymbolType.CAPACITOR_BANK -> SldSymbols.drawCapacitorBank(this, center, sizePx, palette)
                                SldSymbols.IecSymbolType.BUSWAY -> SldSymbols.drawBusway(this, center, sizePx, palette, item.spec)
                                SldSymbols.IecSymbolType.AIR_CIRCUIT_BREAKER_ACB -> SldSymbols.drawAcb(this, center, sizePx, palette, item.spec)
                                SldSymbols.IecSymbolType.MOLDED_CASE_BREAKER_MCCB -> SldSymbols.drawMccb(this, center, sizePx, palette, item.spec)
                                SldSymbols.IecSymbolType.DIGITAL_MULTI_METER_DMM -> SldSymbols.drawDmm(this, center, sizePx, palette)
                                SldSymbols.IecSymbolType.INDICATION_LAMP -> SldSymbols.drawIndicationLamp(this, center, sizePx, palette)
                                SldSymbols.IecSymbolType.DIGITAL_KWH_METER -> SldSymbols.drawKwhMeter(this, center, sizePx, palette)
                                SldSymbols.IecSymbolType.SWITCH_DISCONNECTOR -> SldSymbols.drawSwitchDisconnector(this, center, sizePx, palette, item.spec)
                                SldSymbols.IecSymbolType.UPS -> SldSymbols.drawUps(this, center, sizePx, palette)
                                SldSymbols.IecSymbolType.LOAD_ARROW -> SldSymbols.drawLoadArrow(this, center, sizePx, palette)
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = item.spec,
                                color = palette.feeders,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = item.iecCode,
                                color = palette.textSecondary,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
