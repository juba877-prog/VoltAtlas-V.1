package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TransformerEntity
import com.example.engine.LightingCalculationInput
import com.example.engine.LightingEngine
import com.example.engine.LightingResult
import com.example.engine.TransformerEngine
import com.example.engine.TransformerResult
import com.example.engine.converters.*
import com.example.ui.theme.*
import java.util.Locale

// =============================================================================
// SHARED REUSABLE QUICK CALCULATOR SCAFFOLD / CONTAINER
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCalculatorContainer(
    title: String,
    subtitle: String,
    errorMessage: String? = null,
    onBack: () -> Unit,
    onCopySummary: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // Intercept back button to return to Quick Hub
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ThemeTextPrimary
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = ThemePrimary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("quick_calc_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Quick Engineering",
                            tint = ThemeTextPrimary
                        )
                    }
                },
                actions = {
                    if (onCopySummary != null) {
                        IconButton(onClick = onCopySummary, modifier = Modifier.testTag("copy_summary_btn")) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Summary",
                                tint = ThemePrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ThemeSurface)
            )
        },
        containerColor = ThemeBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (errorMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SleekFail.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, SleekFail),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = "Error", tint = SleekFail)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = errorMessage, color = SleekFail, fontSize = 13.sp)
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun QuickResultCard(
    title: String = "Calculation Results",
    accentColor: Color = SleekPass,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ThemeSurfaceElevated),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, accentColor),
        modifier = Modifier.fillMaxWidth().testTag("result_card")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, fontWeight = FontWeight.Bold, color = ThemeTextPrimary, fontSize = 16.sp)
            }
            HorizontalDivider(color = ThemeBorderSubtle)
            content()
        }
    }
}

@Composable
fun QuickMetricRow(
    title: String,
    value: String,
    subtitle: String? = null,
    highlight: Boolean = false,
    valueColor: Color = ThemeTextPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = ThemeTextSecondary, fontSize = 13.sp)
            if (subtitle != null) {
                Text(subtitle, color = ThemeTextSecondary.copy(alpha = 0.7f), fontSize = 11.sp)
            }
        }
        Text(
            text = value,
            color = valueColor,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = if (highlight) 17.sp else 14.sp
        )
    }
}

@Composable
fun QuickFormulaNote(note: String) {
    Surface(
        color = ThemeSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ThemeBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, tint = ThemePrimary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(note, color = ThemeTextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
fun QuickCalculateButton(
    text: String = "Calculate",
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("calculate_btn"),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(colors = listOf(SleekPrimary, Color(0xFF2563EB)))),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Calculate, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

// =============================================================================
// 2. VOLTAGE DROP CALCULATOR SCREEN
// =============================================================================

@Composable
fun QuickVoltageDropScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var rText by rememberSaveable { mutableStateOf("0.727") }
    var xText by rememberSaveable { mutableStateOf("0.080") }
    var ampsText by rememberSaveable { mutableStateOf("100.0") }
    var lengthText by rememberSaveable { mutableStateOf("50.0") }
    var cosPhiText by rememberSaveable { mutableStateOf("0.80") }
    var voltageText by rememberSaveable { mutableStateOf("400") }
    var isThreePhase by rememberSaveable { mutableStateOf(true) }

    var result by remember { mutableStateOf<VdropResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun calculate() {
        val r = rText.toDoubleOrNull()
        val x = xText.toDoubleOrNull()
        val amps = ampsText.toDoubleOrNull()
        val len = lengthText.toDoubleOrNull()
        val cosPhi = cosPhiText.toDoubleOrNull()
        val volt = voltageText.toDoubleOrNull()

        if (r == null || r < 0.0) { errorMessage = "Resistance R must be >= 0 Ω/km."; return }
        if (x == null || x < 0.0) { errorMessage = "Reactance X must be >= 0 Ω/km."; return }
        if (amps == null || amps <= 0.0) { errorMessage = "Current must be > 0 A."; return }
        if (len == null || len <= 0.0) { errorMessage = "Cable length must be > 0 m."; return }
        if (cosPhi == null || cosPhi <= 0.0 || cosPhi > 1.0) { errorMessage = "Power Factor must be between 0.01 and 1.00."; return }
        if (volt == null || volt <= 0.0) { errorMessage = "Voltage must be > 0 V."; return }

        errorMessage = null
        result = ElectricalConverters.cableVoltageDrop(
            rOhmPerKm = r,
            xOhmPerKm = x,
            amps = amps,
            lengthM = len,
            cosPhi = cosPhi,
            isThreePhase = isThreePhase,
            voltageV = volt
        )
    }

    QuickCalculatorContainer(
        title = "Voltage Drop Calculator",
        subtitle = "Unit mV/A/m, Total ΔV & Percentage Drop",
        errorMessage = errorMessage,
        onBack = onBack,
        onCopySummary = result?.let { res ->
            {
                val text = "Voltage Drop Summary: ${String.format(Locale.US, "%.3f", res.mVperAm)} mV/A/m | ΔV = ${String.format(Locale.US, "%.2f", res.vDrop)} V (${String.format(Locale.US, "%.2f", res.vDropPct)}%)"
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Voltage Drop", text))
                Toast.makeText(context, "Summary copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ThemeSurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, ThemeBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Input Parameters", fontWeight = FontWeight.Bold, color = ThemeTextPrimary, fontSize = 15.sp)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickDropdown(
                        label = "System Phase",
                        selected = if (isThreePhase) "3-Phase (400V)" else "1-Phase (230V)",
                        options = listOf("3-Phase (400V)", "1-Phase (230V)"),
                        onSelect = { 
                            isThreePhase = it.startsWith("3")
                            voltageText = if (isThreePhase) "400" else "230"
                        },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = voltageText,
                        onValueChange = { voltageText = it },
                        label = { Text("Voltage (V)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = ampsText,
                        onValueChange = { ampsText = it },
                        label = { Text("Current (A)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("input_current")
                    )
                    OutlinedTextField(
                        value = lengthText,
                        onValueChange = { lengthText = it },
                        label = { Text("Length (m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("input_length")
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = rText,
                        onValueChange = { rText = it },
                        label = { Text("R (Ω/km)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = xText,
                        onValueChange = { xText = it },
                        label = { Text("X (Ω/km)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = cosPhiText,
                    onValueChange = { cosPhiText = it },
                    label = { Text("Power Factor (cosφ)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        QuickCalculateButton("Calculate Voltage Drop") { calculate() }

        result?.let { res ->
            QuickResultCard {
                QuickMetricRow(title = "Unit Voltage Drop", value = "${String.format(Locale.US, "%.3f", res.mVperAm)} mV/A/m", highlight = true, valueColor = SleekPrimary)
                HorizontalDivider(color = BorderColor)
                QuickMetricRow(title = "Total Voltage Drop (ΔV)", value = "${String.format(Locale.US, "%.2f", res.vDrop)} V", highlight = true)
                QuickMetricRow(
                    title = "Percentage Drop (ΔV%)",
                    value = "${String.format(Locale.US, "%.2f", res.vDropPct)}%",
                    highlight = true,
                    valueColor = if (res.vDropPct <= 3.0) SleekPass else if (res.vDropPct <= 5.0) SleekWarning else SleekFail
                )
                QuickFormulaNote(
                    note = if (isThreePhase) "IEC / BS Formula: mV/A/m = √3 × (R cosφ + X sinφ) | ΔV = (mV/A/m × I × L) / 1000"
                           else "1-Phase Formula: mV/A/m = 2 × (R cosφ + X sinφ) | ΔV = (mV/A/m × I × L) / 1000"
                )
            }
        }
    }
}

// =============================================================================
// 3. LOAD CURRENT CALCULATOR SCREEN (Amps ↔ kW / kVA)
// =============================================================================

@Composable
fun QuickLoadCurrentScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf("PowerToCurrent") } // "PowerToCurrent" or "CurrentToPower"
    var powerInputText by rememberSaveable { mutableStateOf("100.0") }
    var currentInputText by rememberSaveable { mutableStateOf("144.3") }
    var voltageText by rememberSaveable { mutableStateOf("400") }
    var cosPhiText by rememberSaveable { mutableStateOf("0.80") }
    var isThreePhase by rememberSaveable { mutableStateOf(true) }

    var result by remember { mutableStateOf<CurrentPowerResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun calculate() {
        val volt = voltageText.toDoubleOrNull()
        val cosPhi = cosPhiText.toDoubleOrNull()

        if (volt == null || volt <= 0.0) { errorMessage = "Voltage must be > 0 V."; return }
        if (cosPhi == null || cosPhi <= 0.0 || cosPhi > 1.0) { errorMessage = "Power Factor must be between 0.01 and 1.00."; return }

        if (mode == "PowerToCurrent") {
            val pKw = powerInputText.toDoubleOrNull()
            if (pKw == null || pKw <= 0.0) { errorMessage = "Power (kW) must be > 0."; return }
            errorMessage = null
            result = ElectricalConverters.powerToCurrent(pKw, volt, cosPhi, isThreePhase)
        } else {
            val amps = currentInputText.toDoubleOrNull()
            if (amps == null || amps <= 0.0) { errorMessage = "Current (A) must be > 0."; return }
            errorMessage = null
            result = ElectricalConverters.currentToPower(amps, volt, cosPhi, isThreePhase)
        }
    }

    QuickCalculatorContainer(
        title = "Load Current Calculator",
        subtitle = "Two-way Amps ↔ kW / kVA Conversion",
        errorMessage = errorMessage,
        onBack = onBack,
        onCopySummary = result?.let { res ->
            {
                val text = "Load Current: ${String.format(Locale.US, "%.2f", res.amps)} A | Active: ${String.format(Locale.US, "%.2f", res.pKw)} kW | Apparent: ${String.format(Locale.US, "%.2f", res.sKva)} kVA"
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Load Current", text))
                Toast.makeText(context, "Summary copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ThemeSurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, ThemeBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Calculation Mode", fontWeight = FontWeight.Bold, color = ThemeTextPrimary, fontSize = 15.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == "PowerToCurrent",
                        onClick = { mode = "PowerToCurrent"; result = null },
                        label = { Text("kW → Amps") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = mode == "CurrentToPower",
                        onClick = { mode = "CurrentToPower"; result = null },
                        label = { Text("Amps → kW / kVA") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickDropdown(
                        label = "System Phase",
                        selected = if (isThreePhase) "3-Phase (400V)" else "1-Phase (230V)",
                        options = listOf("3-Phase (400V)", "1-Phase (230V)"),
                        onSelect = {
                            isThreePhase = it.startsWith("3")
                            voltageText = if (isThreePhase) "400" else "230"
                        },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = voltageText,
                        onValueChange = { voltageText = it },
                        label = { Text("Voltage (V)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (mode == "PowerToCurrent") {
                    OutlinedTextField(
                        value = powerInputText,
                        onValueChange = { powerInputText = it },
                        label = { Text("Active Power P (kW)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("input_power_kw")
                    )
                } else {
                    OutlinedTextField(
                        value = currentInputText,
                        onValueChange = { currentInputText = it },
                        label = { Text("Operating Current I (Amps)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("input_current_amps")
                    )
                }

                OutlinedTextField(
                    value = cosPhiText,
                    onValueChange = { cosPhiText = it },
                    label = { Text("Power Factor (cosφ)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        QuickCalculateButton("Calculate Current & Power") { calculate() }

        result?.let { res ->
            QuickResultCard {
                QuickMetricRow(title = "Operating Current (I)", value = "${String.format(Locale.US, "%.2f", res.amps)} A", highlight = true, valueColor = SleekPrimary)
                HorizontalDivider(color = BorderColor)
                QuickMetricRow(title = "Active Power (P)", value = "${String.format(Locale.US, "%.2f", res.pKw)} kW")
                QuickMetricRow(title = "Apparent Power (S)", value = "${String.format(Locale.US, "%.2f", res.sKva)} kVA")
                QuickFormulaNote(
                    note = if (isThreePhase) "3-Phase: I = (P × 1000) / (√3 × V × cosφ) | S = √3 × V × I / 1000"
                           else "1-Phase: I = (P × 1000) / (V × cosφ) | S = V × I / 1000"
                )
            }
        }
    }
}

// =============================================================================
// 4. POWER TRIANGLE CALCULATOR SCREEN
// =============================================================================

@Composable
fun QuickPowerTriangleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var inputMode by rememberSaveable { mutableStateOf("FromS") } // "FromS" or "FromPQ"
    var sKvaText by rememberSaveable { mutableStateOf("100.0") }
    var cosPhiText by rememberSaveable { mutableStateOf("0.80") }
    var pKwText by rememberSaveable { mutableStateOf("80.0") }
    var qKvarText by rememberSaveable { mutableStateOf("60.0") }

    var result by remember { mutableStateOf<PowerTriangleResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun calculate() {
        if (inputMode == "FromS") {
            val s = sKvaText.toDoubleOrNull()
            val cosPhi = cosPhiText.toDoubleOrNull()
            if (s == null || s <= 0.0) { errorMessage = "Apparent Power S (kVA) must be > 0."; return }
            if (cosPhi == null || cosPhi < 0.1 || cosPhi > 1.0) { errorMessage = "Power factor cosφ must be between 0.10 and 1.00."; return }
            errorMessage = null
            result = ElectricalConverters.powerTriangleFromS(s, cosPhi)
        } else {
            val p = pKwText.toDoubleOrNull()
            val q = qKvarText.toDoubleOrNull()
            if (p == null || p < 0.0) { errorMessage = "Active Power P (kW) must be >= 0."; return }
            if (q == null || q < 0.0) { errorMessage = "Reactive Power Q (kVAR) must be >= 0."; return }
            if (p == 0.0 && q == 0.0) { errorMessage = "Both P and Q cannot be zero."; return }
            errorMessage = null
            result = ElectricalConverters.powerTriangleFromPQ(p, q)
        }
    }

    QuickCalculatorContainer(
        title = "Power Triangle Calculator",
        subtitle = "Active (kW), Reactive (kVAR) & Apparent (kVA)",
        errorMessage = errorMessage,
        onBack = onBack,
        onCopySummary = result?.let { res ->
            {
                val text = "Power Triangle: P = ${String.format(Locale.US, "%.2f", res.pKw)} kW | Q = ${String.format(Locale.US, "%.2f", res.qKvar)} kVAR | S = ${String.format(Locale.US, "%.2f", res.sKva)} kVA (cosφ = ${String.format(Locale.US, "%.2f", res.cosPhi)})"
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Power Triangle", text))
                Toast.makeText(context, "Summary copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ThemeSurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, ThemeBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Input Mode", fontWeight = FontWeight.Bold, color = ThemeTextPrimary, fontSize = 15.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = inputMode == "FromS",
                        onClick = { inputMode = "FromS"; result = null },
                        label = { Text("Given S (kVA) & cosφ") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = inputMode == "FromPQ",
                        onClick = { inputMode = "FromPQ"; result = null },
                        label = { Text("Given P (kW) & Q (kVAR)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (inputMode == "FromS") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = sKvaText,
                            onValueChange = { sKvaText = it },
                            label = { Text("Apparent Power S (kVA)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = cosPhiText,
                            onValueChange = { cosPhiText = it },
                            label = { Text("Power Factor (cosφ)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = pKwText,
                            onValueChange = { pKwText = it },
                            label = { Text("Active Power P (kW)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = qKvarText,
                            onValueChange = { qKvarText = it },
                            label = { Text("Reactive Power Q (kVAR)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        QuickCalculateButton("Calculate Power Triangle") { calculate() }

        result?.let { res ->
            QuickResultCard {
                QuickMetricRow(title = "Active Power (P)", value = "${String.format(Locale.US, "%.2f", res.pKw)} kW", highlight = true, valueColor = SleekPrimary)
                QuickMetricRow(title = "Reactive Power (Q)", value = "${String.format(Locale.US, "%.2f", res.qKvar)} kVAR", highlight = true, valueColor = Color(0xFFF59E0B))
                QuickMetricRow(title = "Apparent Power (S)", value = "${String.format(Locale.US, "%.2f", res.sKva)} kVA", highlight = true)
                HorizontalDivider(color = BorderColor)
                QuickMetricRow(title = "Power Factor (cosφ)", value = String.format(Locale.US, "%.3f", res.cosPhi))
                QuickMetricRow(title = "Reactive Factor (sinφ)", value = String.format(Locale.US, "%.3f", res.sinPhi))
                QuickFormulaNote(note = "Formulas: P = S × cosφ | Q = S × sinφ | S = √(P² + Q²)")
            }
        }
    }
}

// =============================================================================
// 5. POWER FACTOR CORRECTION SCREEN
// =============================================================================

@Composable
fun QuickPfcScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var pKwText by rememberSaveable { mutableStateOf("100.0") }
    var cosPhi1Text by rememberSaveable { mutableStateOf("0.80") }
    var cosPhi2Text by rememberSaveable { mutableStateOf("0.95") }
    var voltageText by rememberSaveable { mutableStateOf("400") }
    var freqText by rememberSaveable { mutableStateOf("50") }

    var pfcResult by remember { mutableStateOf<PfcResult?>(null) }
    var ufResult by remember { mutableStateOf<Double?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun calculate() {
        val p = pKwText.toDoubleOrNull()
        val c1 = cosPhi1Text.toDoubleOrNull()
        val c2 = cosPhi2Text.toDoubleOrNull()
        val v = voltageText.toDoubleOrNull()
        val f = freqText.toDoubleOrNull() ?: 50.0

        if (p == null || p <= 0.0) { errorMessage = "Active Power P (kW) must be > 0."; return }
        if (c1 == null || c1 <= 0.1 || c1 >= 1.0) { errorMessage = "Initial cosφ1 must be between 0.10 and 0.999."; return }
        if (c2 == null || c2 <= c1 || c2 > 1.0) { errorMessage = "Target cosφ2 must be greater than cosφ1 and <= 1.00."; return }
        if (v == null || v <= 0.0) { errorMessage = "Voltage must be > 0 V."; return }

        errorMessage = null
        val res = ElectricalConverters.powerFactorCorrection(p, c1, c2, v)
        pfcResult = res
        ufResult = ElectricalConverters.kvarToMicroFarad(res.qcKvar, v, f, CapacitorConnectionType.DELTA)
    }

    QuickCalculatorContainer(
        title = "Power Factor Correction",
        subtitle = "Capacitor kVAR & Line Current Reduction",
        errorMessage = errorMessage,
        onBack = onBack,
        onCopySummary = pfcResult?.let { res ->
            {
                val text = "PFC Summary: Required Qc = ${String.format(Locale.US, "%.2f", res.qcKvar)} kVAR | ΔI = ${String.format(Locale.US, "%.2f", res.deltaAmps)} A reduction"
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("PFC Calculation", text))
                Toast.makeText(context, "Summary copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ThemeSurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, ThemeBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Load & Power Factor Inputs", fontWeight = FontWeight.Bold, color = ThemeTextPrimary, fontSize = 15.sp)

                OutlinedTextField(
                    value = pKwText,
                    onValueChange = { pKwText = it },
                    label = { Text("Active Power P (kW)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = cosPhi1Text,
                        onValueChange = { cosPhi1Text = it },
                        label = { Text("Initial PF (cosφ1)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cosPhi2Text,
                        onValueChange = { cosPhi2Text = it },
                        label = { Text("Target PF (cosφ2)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = voltageText,
                        onValueChange = { voltageText = it },
                        label = { Text("System Voltage (V)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = freqText,
                        onValueChange = { freqText = it },
                        label = { Text("Frequency (Hz)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        QuickCalculateButton("Calculate Required Capacitor kVAR") { calculate() }

        pfcResult?.let { res ->
            QuickResultCard {
                QuickMetricRow(title = "Required Capacitor Bank (Qc)", value = "${String.format(Locale.US, "%.2f", res.qcKvar)} kVAR", highlight = true, valueColor = SleekPrimary)
                QuickMetricRow(title = "Line Current Reduction (ΔI)", value = "${String.format(Locale.US, "%.2f", res.deltaAmps)} A", highlight = true, valueColor = SleekPass)
                ufResult?.let { uf ->
                    HorizontalDivider(color = BorderColor)
                    QuickMetricRow(title = "Delta Equivalent Capacitance", value = "${String.format(Locale.US, "%.1f", uf)} µF", subtitle = "3-Phase Delta Bank @ ${voltageText}V")
                }
                QuickFormulaNote(note = "Formula: Qc = P × (tanφ1 - tanφ2) | ΔI = (P × 1000 / (√3 × V)) × (1/cosφ1 - 1/cosφ2)")
            }
        }
    }
}

// =============================================================================
// 6. TRANSFORMER QUICK SIZING SCREEN
// =============================================================================

@Composable
fun QuickTransformerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var loadKvaText by rememberSaveable { mutableStateOf("800.0") }
    var txType by rememberSaveable { mutableStateOf("Dry Type (Cast Resin)") }
    var secondaryVoltageText by rememberSaveable { mutableStateOf("400") }
    var pfText by rememberSaveable { mutableStateOf("0.80") }

    var result by remember { mutableStateOf<TransformerResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun calculate() {
        val loadKva = loadKvaText.toDoubleOrNull()
        val volt = secondaryVoltageText.toIntOrNull()
        val pf = pfText.toDoubleOrNull()

        if (loadKva == null || loadKva <= 0.0) { errorMessage = "Load (kVA) must be > 0."; return }
        if (volt == null || volt <= 0) { errorMessage = "Secondary voltage must be > 0 V."; return }
        if (pf == null || pf <= 0.0 || pf > 1.0) { errorMessage = "Power factor must be between 0.01 and 1.00."; return }

        // Recommended size selection: minSize = loadKVA / 0.85
        val minSize = loadKva / 0.85
        val selectedKva = TransformerEngine.STANDARD_TX_SIZES.firstOrNull { it >= minSize } ?: TransformerEngine.STANDARD_TX_SIZES.last()
        val percentZ = TransformerEngine.getDefaultPercentZ(txType, selectedKva)

        val transientTx = TransformerEntity(
            transformerId = "TX-QUICK",
            manualKVA = selectedKva,
            sizeMode = "Manual",
            voltage = volt,
            percentZ = percentZ,
            xr = 6.0,
            status = "Active",
            vectorGroup = "Dyn11"
        )

        errorMessage = null
        result = TransformerEngine.calculate(transientTx, emptyList())
    }

    QuickCalculatorContainer(
        title = "Transformer Quick Sizing",
        subtitle = "Standard Sizing, FLC, Loading % & Impedance",
        errorMessage = errorMessage,
        onBack = onBack,
        onCopySummary = result?.let { res ->
            {
                val text = "Transformer Sizing: ${res.selectedSize.toInt()} kVA (${txType}) | FLC = ${String.format(Locale.US, "%.1f", res.flc)} A | Zt = ${String.format(Locale.US, "%.2f", res.zt)} mΩ"
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Transformer Sizing", text))
                Toast.makeText(context, "Summary copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ThemeSurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, ThemeBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Design Load Inputs", fontWeight = FontWeight.Bold, color = ThemeTextPrimary, fontSize = 15.sp)

                OutlinedTextField(
                    value = loadKvaText,
                    onValueChange = { loadKvaText = it },
                    label = { Text("Required Design Load (kVA)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                QuickDropdown(
                    label = "Transformer Technology",
                    selected = txType,
                    options = listOf("Dry Type (Cast Resin)", "Oil Immersed (ONAN)"),
                    onSelect = { txType = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = secondaryVoltageText,
                        onValueChange = { secondaryVoltageText = it },
                        label = { Text("Secondary Voltage (V)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = pfText,
                        onValueChange = { pfText = it },
                        label = { Text("Power Factor (cosφ)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        QuickCalculateButton("Size Transformer") { calculate() }

        result?.let { res ->
            val loadKva = loadKvaText.toDoubleOrNull() ?: 0.0
            val loadingPct = if (res.selectedSize > 0.0) (loadKva / res.selectedSize) * 100.0 else 0.0

            QuickResultCard {
                QuickMetricRow(title = "Recommended Standard Size", value = "${res.selectedSize.toInt()} kVA", highlight = true, valueColor = SleekPrimary)
                QuickMetricRow(title = "Secondary Full Load Current (FLC)", value = "${String.format(Locale.US, "%.1f", res.flc)} A", highlight = true)
                QuickMetricRow(
                    title = "Design Loading Percentage",
                    value = "${String.format(Locale.US, "%.1f", loadingPct)}%",
                    valueColor = if (loadingPct <= 85.0) SleekPass else SleekWarning
                )
                HorizontalDivider(color = BorderColor)
                QuickMetricRow(title = "Transformer Impedance (Zt)", value = "${String.format(Locale.US, "%.3f", res.zt)} mΩ")
                QuickMetricRow(title = "Resistance Rt / Reactance Xt", value = "${String.format(Locale.US, "%.3f", res.rt)} / ${String.format(Locale.US, "%.3f", res.xt)} mΩ")
                QuickFormulaNote(note = "IEC Standard Sizing Rule: Selected size satisfies continuous loading <= 85% | FLC = kVA × 1000 / (√3 × V)")
            }
        }
    }
}

// =============================================================================
// 7. TRANSFORMER SHORT-CIRCUIT SCREEN (Dual Presentation)
// =============================================================================

@Composable
fun QuickTransformerScScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var kvaText by rememberSaveable { mutableStateOf("1000") }
    var zPctText by rememberSaveable { mutableStateOf("5.0") }
    var voltageText by rememberSaveable { mutableStateOf("400") }

    var quickScResult by remember { mutableStateOf<TxShortCircuitResult?>(null) }
    var iecResult by remember { mutableStateOf<TransformerResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun calculate() {
        val kva = kvaText.toDoubleOrNull()
        val zPct = zPctText.toDoubleOrNull()
        val volt = voltageText.toDoubleOrNull()

        if (kva == null || kva <= 0.0) { errorMessage = "Transformer rating (kVA) must be > 0."; return }
        if (zPct == null || zPct <= 0.0) { errorMessage = "Impedance %Z must be > 0%."; return }
        if (volt == null || volt <= 0.0) { errorMessage = "Secondary voltage must be > 0 V."; return }

        errorMessage = null

        // 1. Nominal / Fast estimate (c = 1.00)
        quickScResult = ElectricalConverters.txQuickShortCircuit(kva, zPct, volt)

        // 2. Rigorous IEC 60909 (c = 1.05 factor + Dyn11 earth fault)
        val transientTx = TransformerEntity(
            transformerId = "TX-QUICK-SC",
            manualKVA = kva,
            sizeMode = "Manual",
            voltage = volt.toInt(),
            percentZ = zPct,
            xr = 6.0,
            status = "Active",
            vectorGroup = "Dyn11"
        )
        iecResult = TransformerEngine.calculate(transientTx, emptyList())
    }

    QuickCalculatorContainer(
        title = "Transformer Short-Circuit",
        subtitle = "Nominal & IEC 60909 Symmetrical Fault Currents",
        errorMessage = errorMessage,
        onBack = onBack,
        onCopySummary = iecResult?.let { res ->
            {
                val text = "Transformer Short Circuit: ${kvaText} kVA @ ${voltageText}V (%Z=${zPctText}%) | Nominal Isc = ${String.format(Locale.US, "%.2f", quickScResult?.iScKa ?: 0.0)} kA | IEC 60909 I\"k = ${String.format(Locale.US, "%.2f", res.isc3Phase)} kA"
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Transformer Short Circuit", text))
                Toast.makeText(context, "Summary copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ThemeSurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, ThemeBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Transformer Nameplate Data", fontWeight = FontWeight.Bold, color = ThemeTextPrimary, fontSize = 15.sp)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = kvaText,
                        onValueChange = { kvaText = it },
                        label = { Text("Rating (kVA)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("input_tx_kva")
                    )
                    OutlinedTextField(
                        value = zPctText,
                        onValueChange = { zPctText = it },
                        label = { Text("Impedance (%Z)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("input_tx_zpct")
                    )
                }

                OutlinedTextField(
                    value = voltageText,
                    onValueChange = { voltageText = it },
                    label = { Text("Secondary Line Voltage (V)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        QuickCalculateButton("Calculate Fault Currents") { calculate() }

        if (quickScResult != null && iecResult != null) {
            val qRes = quickScResult!!
            val iRes = iecResult!!

            QuickResultCard(title = "Dual Short-Circuit Assessment") {
                QuickMetricRow(title = "Full Load Current (IFLC)", value = "${String.format(Locale.US, "%.1f", qRes.iFlcAmps)} A")
                HorizontalDivider(color = BorderColor)

                // 1. Nominal Estimate
                QuickMetricRow(
                    title = "Nominal Short-Circuit Current",
                    value = "${String.format(Locale.US, "%.2f", qRes.iScKa)} kA",
                    subtitle = "Traditional industrial estimate (c = 1.00)",
                    highlight = true,
                    valueColor = Color(0xFFF59E0B)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 2. IEC 60909 Maximum Fault Current
                QuickMetricRow(
                    title = "IEC 60909 Maximum Fault Current (I\"k)",
                    value = "${String.format(Locale.US, "%.2f", iRes.isc3Phase)} kA",
                    subtitle = "Rigorous maximum symmetrical current (c = 1.05)",
                    highlight = true,
                    valueColor = SleekFail
                )

                QuickMetricRow(
                    title = "Single-Phase Earth Fault (I\"k1)",
                    value = "${String.format(Locale.US, "%.2f", iRes.isc1Phase)} kA",
                    subtitle = "Dyn11 Vector Group (Z0 = 0.9 × Z1)"
                )

                HorizontalDivider(color = BorderColor)
                QuickMetricRow(title = "Transformer Impedance (Zt)", value = "${String.format(Locale.US, "%.3f", iRes.zt)} mΩ")

                QuickFormulaNote(note = "Nominal: Isc = IFLC / (%Z/100) | IEC 60909: I\"k = (1.05 × V) / (√3 × Zt) with Zt = (V² × %Z) / (kVA × 100)")
            }
        }
    }
}

// =============================================================================
// 8. LIGHTING / LUX CALCULATOR SCREEN
// =============================================================================

@Composable
fun QuickLightingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var lengthText by rememberSaveable { mutableStateOf("10.0") }
    var widthText by rememberSaveable { mutableStateOf("6.0") }
    var heightText by rememberSaveable { mutableStateOf("3.0") }
    var targetLuxText by rememberSaveable { mutableStateOf("500.0") }
    var mfText by rememberSaveable { mutableStateOf("0.80") }
    var ufText by rememberSaveable { mutableStateOf("0.65") }
    var lumensText by rememberSaveable { mutableStateOf("3200.0") }
    var wattsText by rememberSaveable { mutableStateOf("28.0") }

    var result by remember { mutableStateOf<LightingResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun calculate() {
        val len = lengthText.toDoubleOrNull()
        val wid = widthText.toDoubleOrNull()
        val hgt = heightText.toDoubleOrNull() ?: 3.0
        val targetLux = targetLuxText.toDoubleOrNull()
        val mf = mfText.toDoubleOrNull()
        val uf = ufText.toDoubleOrNull()
        val lm = lumensText.toDoubleOrNull()
        val watts = wattsText.toDoubleOrNull() ?: 0.0

        if (len == null || len <= 0.0) { errorMessage = "Room length must be > 0 m."; return }
        if (wid == null || wid <= 0.0) { errorMessage = "Room width must be > 0 m."; return }
        if (targetLux == null || targetLux <= 0.0) { errorMessage = "Target Lux must be > 0 lx."; return }
        if (mf == null || mf <= 0.0 || mf > 1.0) { errorMessage = "Maintenance factor (MF) must be between 0.01 and 1.00."; return }
        if (uf == null || uf <= 0.0 || uf > 1.0) { errorMessage = "Utilization factor (UF) must be between 0.01 and 1.00."; return }
        if (lm == null || lm <= 0.0) { errorMessage = "Lumens per fixture must be > 0 lm."; return }
        if (watts < 0.0) { errorMessage = "Watts per fixture cannot be negative."; return }

        errorMessage = null
        result = LightingEngine.calculate(
            LightingCalculationInput(
                length = len,
                width = wid,
                height = hgt,
                targetLux = targetLux,
                maintenanceFactor = mf,
                utilizationFactor = uf,
                lumensPerFixture = lm,
                wattsPerFixture = watts
            )
        )
    }

    QuickCalculatorContainer(
        title = "Lighting / Lux Calculator",
        subtitle = "Lumen Method Fixture Count & Power Density",
        errorMessage = errorMessage,
        onBack = onBack,
        onCopySummary = result?.let { res ->
            {
                val text = "Lighting Calculation: ${res.fixturesCount} fixtures (${lumensText} lm, ${wattsText} W) -> Actual Lux: ${res.actualLux} lx | Power Density: ${res.powerDensity_W_m2} W/m²"
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Lighting Calculation", text))
                Toast.makeText(context, "Summary copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ThemeSurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, ThemeBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Room Dimensions & Target Lux", fontWeight = FontWeight.Bold, color = ThemeTextPrimary, fontSize = 15.sp)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = lengthText,
                        onValueChange = { lengthText = it },
                        label = { Text("Length (m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("input_room_length")
                    )
                    OutlinedTextField(
                        value = widthText,
                        onValueChange = { widthText = it },
                        label = { Text("Width (m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("input_room_width")
                    )
                    OutlinedTextField(
                        value = heightText,
                        onValueChange = { heightText = it },
                        label = { Text("Height (m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = targetLuxText,
                        onValueChange = { targetLuxText = it },
                        label = { Text("Target Lux (lx)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("input_target_lux")
                    )
                    OutlinedTextField(
                        value = mfText,
                        onValueChange = { mfText = it },
                        label = { Text("Maintenance (MF)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = ufText,
                        onValueChange = { ufText = it },
                        label = { Text("Utilization (UF)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(color = ThemeBorderSubtle)
                Text("Luminaire Specification", fontWeight = FontWeight.Bold, color = ThemeTextPrimary, fontSize = 15.sp)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = lumensText,
                        onValueChange = { lumensText = it },
                        label = { Text("Lumens / Fixture (lm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = wattsText,
                        onValueChange = { wattsText = it },
                        label = { Text("Watts / Fixture (W)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        QuickCalculateButton("Calculate Fixtures & Lux") { calculate() }

        result?.let { res ->
            QuickResultCard {
                QuickMetricRow(title = "Required Fixtures Count", value = "${res.fixturesCount} Units", highlight = true, valueColor = SleekPrimary)
                QuickMetricRow(title = "Actual Maintained Illuminance", value = "${res.actualLux} Lux", highlight = true, valueColor = SleekPass)
                HorizontalDivider(color = BorderColor)
                QuickMetricRow(title = "Total Luminous Flux Needed", value = "${res.totalLumensNeeded} lm")
                QuickMetricRow(title = "Total Lighting Power", value = "${res.totalPower_W} W")
                QuickMetricRow(title = "Lighting Power Density (LPD)", value = "${res.powerDensity_W_m2} W/m²")
                QuickMetricRow(title = "Room Floor Area", value = "${res.area_m2} m²")
                QuickFormulaNote(note = "Lumen Method: Lumens = (Target Lux × Area) / (UF × MF) | Fixtures = ⌈Lumens / Lumens_Fixture⌉ | Lux = (Fixtures × Lumens_Fixture × UF × MF) / Area")
            }
        }
    }
}
