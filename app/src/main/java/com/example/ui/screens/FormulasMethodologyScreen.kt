package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import com.example.ui.theme.VoltAtlasMotion
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.LatexFormula

data class FormulaTopic(
    val id: String,
    val title: String,
    val standard: String,
    val latexFormula: String,
    val secondaryLatex: String? = null,
    val variables: List<String>,
    val workedExample: String
)

val FORMULA_TOPICS = listOf(
    FormulaTopic(
        id = "design_current",
        title = "1. Design Current (Ib)",
        standard = "IEC 60364-5-52",
        latexFormula = """I_b = \dfrac{P \times 1000}{\sqrt{3}\; V_L \cos\phi}""",
        secondaryLatex = """\text{Single Phase (1}\phi\text{): } I_b = \dfrac{P \times 1000}{V_{ph} \cos\phi}""",
        variables = listOf(
            "Ib = Design current in Amperes (A)",
            "P = Active load power in Kilowatts (kW)",
            "VL = Line-to-line AC voltage (400V)",
            "Vph = Phase-to-neutral voltage (230V)",
            "cosφ = Load operating power factor (0.80 – 1.00)"
        ),
        workedExample = """3-Phase Load = 160 kW, VL = 400V, PF = 0.80
Ib = (160 × 1000) / (√3 × 400 × 0.80) = 288.68 A"""
    ),
    FormulaTopic(
        id = "voltage_drop",
        title = "2. Cable Voltage Drop (ΔV)",
        standard = "IEC 60364-5-52 / BS 7671",
        latexFormula = """\Delta V = \dfrac{\sqrt{3}\; I_b\, L\,(R\cos\phi + X\sin\phi)}{1000}""",
        secondaryLatex = """\text{Single Phase (1}\phi\text{): } \Delta V = \dfrac{2\, I_b\, L\,(R\cos\phi + X\sin\phi)}{1000}""",
        variables = listOf(
            "ΔV = Voltage drop in Volts (V)",
            "Ib = Circuit design current (A)",
            "L = One-way circuit route length in meters (m)",
            "R = AC conductor resistance at operating temp (Ω/km)",
            "X = Cable reactance at system frequency (Ω/km)",
            "sinφ = Reactive power factor (sinφ = √(1 − cos²φ))"
        ),
        workedExample = """Ib = 231 A, Length L = 50 m, 150 mm² Cu Cable (R = 0.1581 Ω/km, X = 0.077 Ω/km, PF = 0.80)
sin(φ) = √(1 − 0.80²) = 0.60
ΔV = [√3 × 231 × 50 × (0.1581 × 0.80 + 0.077 × 0.60)] / 1000 = 3.45 V
%VD = (3.45 / 400) × 100 = 0.863% (Compliant)"""
    ),
    FormulaTopic(
        id = "thermal_withstand",
        title = "3. Short-Circuit Thermal Withstand (S)",
        standard = "IEC 60364-5-54",
        latexFormula = """S \ge \dfrac{I_{sc}\sqrt{t}}{k}""",
        variables = listOf(
            "S = Minimum conductor cross-sectional area (mm²)",
            "Isc = Symmetrical RMS short-circuit current (A)",
            "t = Fault duration / protection operating time (seconds)",
            "k = Conductor material factor (Cu/XLPE = 143, Cu/PVC = 115, Al/XLPE = 94)"
        ),
        workedExample = """Fault Current Isc = 25 kA (25,000 A), Trip Time t = 0.1 s, Cable = Cu/XLPE (k = 143)
S ≥ (25,000 × √0.1) / 143 = (25,000 × 0.3162) / 143 = 55.28 mm²
Selected Standard Conductor = 70 mm²"""
    ),
    FormulaTopic(
        id = "transformer_short_circuit",
        title = "4. Transformer Short-Circuit Current",
        standard = "IEC 60076 / IEC 60909",
        latexFormula = """I_{sc\,3ph} = \dfrac{S_{kVA}\times 1000}{\sqrt{3}\; V_L\,(\%Z/100)}""",
        variables = listOf(
            "Isc_3ph = 3-phase symmetrical fault current at secondary (A)",
            "SkVA = Transformer rated capacity (kVA)",
            "VL = Secondary nominal line voltage (400V)",
            "%Z = Percentage short-circuit impedance (e.g., 5.0%)"
        ),
        workedExample = """Rating = 1000 kVA, Secondary Voltage = 400V, %Z = 5.0%
Full Load Current I_flc = (1000 × 1000) / (√3 × 400) = 1443.38 A
Isc_3ph = 1443.38 / (5.0 / 100) = 28,867 A = 28.87 kA"""
    ),
    FormulaTopic(
        id = "peak_short_circuit",
        title = "5. Peak Short-Circuit Current (κ, ip)",
        standard = "IEC 60909",
        latexFormula = """\kappa = 1.02 + 0.98\,e^{-3R/X}\qquad i_p = \kappa\sqrt{2}\,I_{sc}""",
        variables = listOf(
            "κ = Peak factor multiplier (1.02 ≤ κ ≤ 2.0)",
            "R/X = Resistance-to-reactance ratio at fault location",
            "ip = Peak short-circuit current (kA)",
            "Isc = Symmetrical RMS fault current (kA)"
        ),
        workedExample = """Isc = 25 kA RMS, System R/X Ratio = 0.1
κ = 1.02 + 0.98 × exp(−3 × 0.1) = 1.02 + 0.98 × 0.7408 = 1.745
ip = 1.745 × √2 × 25 kA = 1.745 × 1.4142 × 25 = 61.7 kA Peak"""
    ),
    FormulaTopic(
        id = "power_factor_correction",
        title = "6. Power Factor Correction (Qc)",
        standard = "IEC 60831 / IEEE 141",
        latexFormula = """Q_c = P\,(\tan\phi_1 - \tan\phi_2)""",
        variables = listOf(
            "Qc = Required capacitor bank rating in Kilovars (kVAR)",
            "P = Total active power load (kW)",
            "φ1 = Initial phase angle (arccos(PF1))",
            "φ2 = Target phase angle (arccos(PF2))"
        ),
        workedExample = """Active Power P = 500 kW, Initial PF1 = 0.80, Target PF2 = 0.95
tan(φ1) = tan(arccos(0.80)) = 0.750
tan(φ2) = tan(arccos(0.95)) = 0.329
Qc = 500 × (0.750 − 0.329) = 210.5 kVAR
Selected Standard Commercial Bank = 225 kVAR"""
    ),
    FormulaTopic(
        id = "generator_sizing",
        title = "7. Generator Sizing (Sgen)",
        standard = "ISO 8528",
        latexFormula = """S_{gen} = \dfrac{\max(P_{run},\,P_{step})}{PF}""",
        variables = listOf(
            "Sgen = Minimum generator rated capacity (kVA)",
            "Prun = Continuous running active power demand (kW)",
            "Pstep = Maximum transient motor starting kW step",
            "PF = Generator rated power factor (0.80)"
        ),
        workedExample = """Continuous Running Load Prun = 200 kW, Motor Starting Peak Pstep = 280 kW
Max Active Demand = max(200, 280) = 280 kW
Sgen = 280 / 0.80 = 350 kVA Rating"""
    ),
    FormulaTopic(
        id = "ups_battery",
        title = "8. UPS Battery Capacity (Ah)",
        standard = "IEC 62040",
        latexFormula = """Ah = \dfrac{kWh \times 1000}{V_{dc}\; DoD\; \eta}""",
        variables = listOf(
            "Ah = Required battery bank capacity (Ampere-hours)",
            "kWh = Required backup energy (kW × autonomy hours)",
            "Vdc = Nominal DC bus voltage (V)",
            "DoD = Maximum depth of discharge (0.80)",
            "η = Inverter conversion efficiency (0.92)"
        ),
        workedExample = """Backup Energy = 30 kWh, DC Voltage = 384 V DC, DoD = 0.80, Efficiency η = 0.92
Ah = (30 × 1000) / (384 × 0.80 × 0.92) = 30,000 / 282.624 = 106.15 Ah
Selected Standard Battery Bank = 120 Ah"""
    ),
    FormulaTopic(
        id = "earthing_rod",
        title = "9. Earthing Rod Resistance (R)",
        standard = "BS 7430",
        latexFormula = """R = \dfrac{\rho}{2\pi L}\left(\ln\dfrac{8L}{d} - 1\right)""",
        variables = listOf(
            "R = Resistance of single driven rod in Ohms (Ω)",
            "ρ = Uniform soil resistivity (Ω·m)",
            "L = Driven rod length in meters (m)",
            "d = Rod outer diameter in meters (m)"
        ),
        workedExample = """Soil Resistivity ρ = 100 Ω·m, Rod Length L = 3.0 m, Diameter d = 0.016 m (16 mm)
8L / d = (8 × 3.0) / 0.016 = 1500
ln(1500) − 1 = 7.313 − 1 = 6.313
R = [100 / (2 × π × 3.0)] × 6.313 = 5.305 × 6.313 = 33.5 Ω"""
    ),
    FormulaTopic(
        id = "conduit_fill",
        title = "10. Conduit Fill Percentage",
        standard = "NEC Ch. 9 / BS 7671",
        latexFormula = """\%fill = \dfrac{\sum A_{cables}}{A_{conduit}} \times 100""",
        variables = listOf(
            "%fill = Percentage of internal conduit area occupied",
            "Acables = Sum of cross-sectional areas of all enclosed cables (π/4 × ∑OD²)",
            "Aconduit = Internal cross-sectional area of conduit (π/4 × D_int²)",
            "NEC Fill Limits: 1 Cable = 53%, 2 Cables = 31%, 3+ Cables = 40%"
        ),
        workedExample = """4 Cables of Outer Diameter OD = 14 mm (Total Area = 4 × π/4 × 14² = 615.75 mm²)
Conduit Internal Diameter D = 48 mm (Internal Area = π/4 × 48² = 1809.56 mm²)
Fill % = (615.75 / 1809.56) × 100 = 34.0% ≤ 40% Limit -> PASS"""
    ),
    FormulaTopic(
        id = "ampacity_derating",
        title = "11. Ampacity Derating Factors (Iz)",
        standard = "IEC 60364-5-52",
        latexFormula = """I_z = I_{table}\times(C_{temp}\times C_{group}\times C_{soil})""",
        variables = listOf(
            "Iz = Derated continuous current-carrying capacity (A)",
            "Itable = Base un-derated ampacity from IEC catalog tables (A)",
            "Ctemp = Ambient temperature correction factor (0.87 for 45°C in XLPE)",
            "Cgroup = Cable grouping/bundling reduction factor (0.80 for 4 circuits)",
            "Csoil = Ground/soil thermal resistivity correction factor (if buried)"
        ),
        workedExample = """240 mm² Cu/XLPE Cable, Base Catalog Itable = 430 A
Ambient Temp = 45°C (Ctemp = 0.87), 4 Grouped Circuits (Cgroup = 0.80)
Iz = 430 × 0.87 × 0.80 = 299.28 A Derated Capacity"""
    ),
    FormulaTopic(
        id = "ats_sizing",
        title = "12. ATS Frame Sizing (Igov)",
        standard = "IEC 60947-6-1",
        latexFormula = """I_{gov} = \max(I_{design},\, I_{genFLC})""",
        variables = listOf(
            "Igov = Governing current rating for ATS transfer switch frame selection (A)",
            "Idesign = Normal grid utility supply design load current (A)",
            "IgenFLC = Emergency standby generator rated full-load current (A)"
        ),
        workedExample = """Normal Utility Grid Load Current = 250 A, Generator FLC = 289 A
Igov = max(250, 289) = 289 A Governing Current
Selected Standard Commercial ATS Frame = 315 A"""
    ),
    FormulaTopic(
        id = "protection_coordination",
        title = "13. Protection Coordination & ADS",
        standard = "IEC 60364-4-43 / IEC 60947-2",
        latexFormula = """I_b \le I_n \le I_z \quad;\quad I_{cu} \ge I_{sc} \quad;\quad Z_s \le Z_{s,max}=\dfrac{U_0}{I_a}""",
        variables = listOf(
            "Ib = Circuit design operating current (A)",
            "In = Breaker nominal trip rating (A)",
            "Iz = Cable derated thermal capacity (A)",
            "Icu = Breaker rated ultimate short-circuit breaking capacity (kA)",
            "Isc = Maximum prospective short-circuit fault current (kA)",
            "Zs = Earth fault loop impedance (Ω)",
            "U0 = Nominal phase-to-earth voltage (230V)",
            "Ia = Disconnection current for required trip time (t ≤ 0.4s / 5s)"
        ),
        workedExample = """Ib = 289 A ≤ In = 315 A ≤ Iz = 299 A? -> (315A > 299A: FAIL -> Cable Up-size Required)
Icu = 36 kA ≥ Isc = 28.87 kA -> (PASS)
Earth Loop Zs = 0.35 Ω ≤ Zs,max = (230 / 315) = 0.73 Ω -> (PASS)"""
    ),
    FormulaTopic(
        id = "transformer_components",
        title = "14. Transformer Impedance Components (IFLC, Zt, Rt, Xt)",
        standard = "IEC 60076 / IEC 60909",
        latexFormula = """I_{FLC}=\dfrac{S_{kVA}\times 1000}{\sqrt{3}\,V_L}\,,\; Z_t=\dfrac{V_L^2}{S_{kVA}\times 1000}\cdot\dfrac{\%Z}{100}\,,\; R_t=\dfrac{Z_t}{\sqrt{1+(X/R)^2}}\,,\; X_t=R_t\cdot(X/R)""",
        variables = listOf(
            "IFLC = Secondary full-load current (A)",
            "SkVA = Transformer rated power (kVA)",
            "VL = Secondary line voltage (V)",
            "%Z = Percent short-circuit impedance",
            "Zt = Transformer total impedance per phase (Ω)",
            "Rt = Transformer internal resistance per phase (Ω)",
            "Xt = Transformer internal reactance per phase (Ω)",
            "X/R = Transformer reactance-to-resistance ratio (e.g., 6.0)"
        ),
        workedExample = """1000 kVA, 400V Secondary, %Z = 5.0%, X/R Ratio = 6.0
IFLC = (1000 × 1000) / (√3 × 400) = 1443.4 A
Zt = (400² / 1,000,000) × (5.0 / 100) = 0.0080 Ω = 8.00 mΩ
Rt = 8.00 / √(1 + 6.0²) = 8.00 / 6.0828 = 1.315 mΩ
Xt = 1.315 × 6.0 = 7.891 mΩ"""
    ),
    FormulaTopic(
        id = "power_conversions_summary",
        title = "15. Power Triangle & Conversions Note",
        standard = "IEEE 141 / IEC 60038",
        latexFormula = """S = \sqrt{P^2 + Q^2} \qquad P = S \cos\phi \qquad Q = S \sin\phi""",
        variables = listOf(
            "S = Apparent power in Kilovolt-amperes (kVA)",
            "P = Active power in Kilowatts (kW)",
            "Q = Reactive power in Kilovars (kVAR)",
            "cosφ = Operating power factor",
            "Note: For 20 interactive unit conversions (AWG ↔ mm², µF ↔ kVAR, HP ↔ kW, Freq ↔ RPM), see the Conversions tab"
        ),
        workedExample = """Active Power P = 400 kW, Reactive Power Q = 300 kVAR
Apparent Power S = √(400² + 300²) = √(160,000 + 90,000) = 500 kVA
Power Factor = P / S = 400 / 500 = 0.80 Lagging"""
    )
)

@Composable
fun FormulasMethodologyScreen() {
    var searchQuery by remember { mutableStateOf("") }
    val expandedState = remember { mutableStateMapOf<String, Boolean>() }

    val filteredTopics = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            FORMULA_TOPICS
        } else {
            val q = searchQuery.trim().lowercase()
            FORMULA_TOPICS.filter { topic ->
                topic.title.lowercase().contains(q) ||
                topic.standard.lowercase().contains(q) ||
                topic.variables.any { it.lowercase().contains(q) }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Functions,
                            contentDescription = "Methodology Icon",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "Engineering Methodology",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "IEC & BS Standard Formulas & Worked Calculation Examples",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search formulas, standards, or variables...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }

        // Accordion Cards
        items(filteredTopics, key = { it.id }) { topic ->
            val isExpanded = expandedState[topic.id] ?: false

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    1.dp,
                    if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Accordion Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedState[topic.id] = !isExpanded }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = topic.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = topic.standard,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Expanded Content Body
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn(animationSpec = VoltAtlasMotion.fadeEnterSpec) + expandVertically(animationSpec = spring(stiffness = VoltAtlasMotion.SpringStiffnessLow)),
                        exit = fadeOut(animationSpec = VoltAtlasMotion.fadeExitSpec) + shrinkVertically(animationSpec = spring(stiffness = VoltAtlasMotion.SpringStiffnessLow))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        ) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // 1. Primary Formula Box (Rendered via jLaTeXMath)
                            Text(
                                text = "Mathematical Formula:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LatexFormula(
                                latex = topic.latexFormula,
                                textColor = MaterialTheme.colorScheme.onSurface
                            )

                            // Secondary Formula (if present, e.g. Single Phase)
                            if (topic.secondaryLatex != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                LatexFormula(
                                    latex = topic.secondaryLatex,
                                    textColor = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 2. Variables Legend
                            Text(
                                text = "Variables Definition:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            topic.variables.forEach { variable ->
                                Text(
                                    text = "• $variable",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 3. Worked Example Card
                            Text(
                                text = "Worked Calculation Example:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = topic.workedExample,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
