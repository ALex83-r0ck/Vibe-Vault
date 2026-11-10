package com.alexander.vibevault

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alexander.vibevault.domain.CurvePoint
import com.alexander.vibevault.domain.NicotineModel
import com.alexander.vibevault.domain.PkParameters
import com.alexander.vibevault.ui.VibeScaffold
import com.alexander.vibevault.ui.formatBodyMg
import com.alexander.vibevault.ui.formatKg
import com.alexander.vibevault.ui.formatMgValue
import com.alexander.vibevault.ui.formatNgPerMl
import com.alexander.vibevault.ui.formatPercent
import com.alexander.vibevault.ui.parseMg
import com.alexander.vibevault.ui.relativeTo
import com.alexander.vibevault.ui.theme.HeroGradient
import kotlin.math.max

@Composable
fun BodyScreen(viewModel: BodyViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var showModelDialog by remember { mutableStateOf(false) }
    val body = state.body

    VibeScaffold(title = "Abbau", onBack = onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.linearGradient(HeroGradient))
                            .padding(20.dp)
                    ) {
                        Text("Nikotin im Körper", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(
                            text = formatBodyMg(body.nicotineMg),
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White
                        )
                        Text(
                            text = "≈ ${formatNgPerMl(body.nicotineNgPerMl)} im Blut",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        Text(
                            text = body.nicotineFreeAt?.let { "Nikotinfrei ab ca. ${it.relativeTo(state.now)}" }
                                ?: "Praktisch nikotinfrei 🌿",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier
                                .padding(top = 14.dp)
                                .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            item {
                InfoCard(title = "Blutspiegel Nikotin") {
                    Text(
                        text = "Letzte 12 h und Prognose für die nächsten 12 h (gestrichelt). " +
                            "Grüne Linie: ${formatMgValue(NicotineModel.NICOTINE_FREE_NG_PER_ML.toFloat())} ng/ml – darunter gilt man als praktisch nikotinfrei.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    NicotineCurveChart(points = state.curve)
                }
            }
            item {
                InfoCard(title = "🧪 Cotinin (Abbauprodukt)") {
                    Text(formatNgPerMl(body.cotinineNgPerMl), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = body.cotinineBelowCutoffAt
                            ?.let { "Unter ${formatMgValue(NicotineModel.COTININE_CUTOFF_NG_PER_ML.toFloat())} ng/ml ab ca. ${it.relativeTo(state.now)}" }
                            ?: "Unter dem Nichtraucher-Grenzwert ✓",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )
                    Text(
                        text = "Etwa 75 % des Nikotins werden in der Leber zu Cotinin umgebaut. Cotinin hat eine " +
                            "Halbwertszeit von ≈ 16 h und ist deshalb noch Tage später nachweisbar – darauf beruhen " +
                            "die meisten Nikotintests. 3 ng/ml im Blut ist ein üblicher Grenzwert zwischen " +
                            "Nichtrauchern und Konsumenten.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CotinineExplainer()
                }
            }
            item {
                InfoCard(title = "⚙️ Dein Modell") {
                    ModelRow("Körpergewicht", formatKg(state.params.bodyWeightKg))
                    ModelRow("Aufgenommener Anteil", formatPercent(state.params.absorbedFraction))
                    ModelRow("Halbwertszeit Nikotin", "${formatMgValue(state.params.nicotineHalfLifeH.toFloat())} h")
                    ModelRow("Halbwertszeit Cotinin", "${formatMgValue(state.params.cotinineHalfLifeH.toFloat())} h")
                    TextButton(onClick = { showModelDialog = true }, contentPadding = PaddingValues(0.dp)) {
                        Text("Anpassen")
                    }
                }
            }
            item {
                InfoCard(title = "📚 So wird gerechnet") {
                    Text(
                        text = "Jeder Pouch wird als Dosis behandelt: Nikotingehalt × aufgenommener Anteil. " +
                            "Daraus berechnet ein pharmakokinetisches Ein-Kompartiment-Modell, wie das Nikotin " +
                            "über die Mundschleimhaut ins Blut gelangt (Maximum nach ≈ 50 min) und mit einer " +
                            "Halbwertszeit von ≈ 2 h abgebaut wird. Mit dem Timer zählt die echte Nutzungsdauer: " +
                            "Nach dem Herausnehmen wird nichts mehr aufgenommen (nach 20 min ≈ 63 %, 30 min ≈ 78 %, " +
                            "60 min ≈ 95 % des möglichen Nikotins). Mehrere Pouches werden aufaddiert. Den " +
                            "Blutspiegel liefert das Verteilungsvolumen (Nikotin 2,6 L/kg, Cotinin 0,85 L/kg).\n\n" +
                            "Aus Pouches werden je nach Produkt etwa 24–59 % des Nikotins herausgelöst; ein Teil " +
                            "davon wird verschluckt und in der Leber abgebaut, bevor es ins Blut kommt. Darum ist " +
                            "der aufgenommene Anteil einstellbar.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Quellen: Benowitz NL et al., Handb Exp Pharmacol 2009;192:29–60 · " +
                            "Lunell E et al., Nicotine Tob Res 2020;22(10):1757–1763 · " +
                            "Benowitz NL et al., Am J Epidemiol 2009;169(2):236–248",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    Text(
                        text = "⚠️ Schätzung, kein medizinischer Befund. Der Abbau ist individuell sehr verschieden " +
                            "(Gene, Schwangerschaft, Medikamente, Leberfunktion) und kann leicht doppelt so schnell " +
                            "oder halb so schnell sein.",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }
    }

    if (showModelDialog) {
        ModelDialog(
            params = state.params,
            onDismiss = { showModelDialog = false },
            onConfirm = { weight, fraction ->
                viewModel.updateModel(weight, fraction)
                showModelDialog = false
            }
        )
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
            content()
        }
    }
}

@Composable
private fun ModelRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Linie des Blutspiegels: Vergangenheit durchgezogen, Prognose gestrichelt, Jetzt-Markierung in der Mitte. */
@Composable
private fun NicotineCurveChart(points: List<CurvePoint>) {
    if (points.size < 2) return
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    val thresholdColor = MaterialTheme.colorScheme.secondary
    val nowColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxY = max(points.maxOf { it.nicotineNgPerMl }, 2.0) * 1.15
    val nowIndex = points.size / 2

    Column {
        Text(
            text = "max. ${formatNgPerMl(points.maxOf { it.nicotineNgPerMl })}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(top = 4.dp)
        ) {
            val w = size.width
            val h = size.height
            fun x(i: Int) = i * w / (points.size - 1)
            fun y(v: Double) = (h - v / maxY * h).toFloat()

            val past = Path().apply {
                moveTo(x(0), y(points[0].nicotineNgPerMl))
                for (i in 1..nowIndex) lineTo(x(i), y(points[i].nicotineNgPerMl))
            }
            val future = Path().apply {
                moveTo(x(nowIndex), y(points[nowIndex].nicotineNgPerMl))
                for (i in nowIndex + 1 until points.size) lineTo(x(i), y(points[i].nicotineNgPerMl))
            }
            val area = Path().apply {
                moveTo(0f, h)
                points.forEachIndexed { i, p -> lineTo(x(i), y(p.nicotineNgPerMl)) }
                lineTo(w, h)
                close()
            }
            drawPath(area, fillColor)

            val thresholdY = y(NicotineModel.NICOTINE_FREE_NG_PER_ML)
            drawLine(
                color = thresholdColor,
                start = Offset(0f, thresholdY),
                end = Offset(w, thresholdY),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
            )
            drawLine(
                color = nowColor.copy(alpha = 0.6f),
                start = Offset(x(nowIndex), 0f),
                end = Offset(x(nowIndex), h),
                strokeWidth = 1.5.dp.toPx()
            )
            drawPath(past, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            drawPath(
                future,
                lineColor.copy(alpha = 0.7f),
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                )
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("−12 h", "jetzt", "+12 h").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ModelDialog(params: PkParameters, onDismiss: () -> Unit, onConfirm: (Double, Double) -> Unit) {
    var weightInput by remember { mutableStateOf(formatMgValue(params.bodyWeightKg.toFloat())) }
    var percentInput by remember { mutableStateOf(formatMgValue((params.absorbedFraction * 100).toFloat())) }
    val weight = parseMg(weightInput)?.toDouble()?.takeIf { it in 30.0..250.0 }
    val percent = parseMg(percentInput)?.toDouble()?.takeIf { it in 5.0..100.0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modell anpassen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it },
                    label = { Text("Körpergewicht (kg)") },
                    isError = weight == null,
                    supportingText = if (weight == null) { { Text("30–250 kg") } } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = percentInput,
                    onValueChange = { percentInput = it },
                    label = { Text("Aufgenommener Anteil (%)") },
                    isError = percent == null,
                    supportingText = {
                        Text(if (percent == null) "5–100 %" else "Standard 30 %. Studien: 24–59 % werden herausgelöst.")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (weight != null && percent != null) onConfirm(weight, percent / 100.0) },
                enabled = weight != null && percent != null
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

private val cotinineFacts = listOf(
    "Was ist Cotinin?" to
        "Das wichtigste Abbauprodukt von Nikotin. Deine Leber (vor allem das Enzym CYP2A6) baut rund drei " +
        "Viertel des aufgenommenen Nikotins zu Cotinin um, das dann über die Nieren ausgeschieden wird.",
    "Merkt man das?" to
        "Kaum. Cotinin wirkt viel schwächer als Nikotin und ist nicht das, was den Kick oder das Verlangen " +
        "auslöst. Es ist vor allem eine Spur, die zeigt, wie viel Nikotin in letzter Zeit da war.",
    "Warum hält es so lange?" to
        "Nikotin ist nach ca. 2 h zur Hälfte abgebaut, Cotinin erst nach ca. 16 h (je nach Mensch etwa 10–20 h). " +
        "Deshalb steigt es über den Tag an, wenn du regelmäßig Pouches nimmst, und braucht nach dem letzten " +
        "Pouch mehrere Tage, bis es wieder unten ist.",
    "Wofür wird es gemessen?" to
        "Nikotintests (Speichel, Urin, Blut) suchen fast immer nach Cotinin, z. B. beim Arzt, bei manchen " +
        "Versicherungen oder Arbeitgebern. Im Blut gelten ca. 3 ng/ml als Grenze zwischen „nimmt kein Nikotin“ " +
        "und „nimmt Nikotin“. Urintests haben eigene, höhere Grenzwerte.",
    "Was sagt mir mein Wert?" to
        "Er ist dein Langzeit-Maß: Der Nikotinspiegel schwankt mit jedem Pouch, Cotinin zeigt eher den " +
        "Durchschnitt der letzten 2–3 Tage. Wenn du weniger nimmst, sinkt er langsam über Tage – ein guter " +
        "Beleg, dass sich deine Veränderung wirklich im Körper zeigt.",
    "Wie genau ist die Zahl?" to
        "Eine Schätzung aus einem einfachen Modell mit Durchschnittswerten. Echte Werte hängen u. a. von Genen, " +
        "Leber, Ernährung und Medikamenten ab und können deutlich abweichen. Kein Ersatz für einen Test."
)

/** Aufklappbare Erklärung: Was bedeutet Cotinin? */
@Composable
private fun CotinineExplainer() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
        Text(if (expanded) "Weniger anzeigen" else "Was bedeutet Cotinin? ›")
    }
    if (expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            cotinineFacts.forEach { (question, answer) ->
                Column {
                    Text(question, style = MaterialTheme.typography.titleSmall)
                    Text(
                        answer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
