package com.alexander.vibevault

import android.Manifest
import android.content.ActivityNotFoundException
import android.net.Uri
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.alexander.vibevault.di.AppModule
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.alexander.vibevault.data.PouchTimer
import com.alexander.vibevault.domain.StrengthSource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alexander.vibevault.ui.VibeScaffold
import com.alexander.vibevault.ui.formatMg
import com.alexander.vibevault.ui.formatMgValue
import com.alexander.vibevault.ui.parseMg

@Composable
fun AddEntryScreen(viewModel: AddEntryViewModel, onDone: () -> Unit) {
    val suggestions by viewModel.suggestions.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    var triedToSave by rememberSaveable { mutableStateOf(false) }
    var pendingPhoto by rememberSaveable { mutableStateOf<Uri?>(null) }
    var cameraMissing by rememberSaveable { mutableStateOf(false) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) pendingPhoto?.let(viewModel::scan) else viewModel.discardCapture()
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::scan)
    }

    val targetMinutes by viewModel.pouchTargetMinutes.collectAsState()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (viewModel.save()) onDone()
    }

    val showStrengthError = triedToSave && parseMg(viewModel.strengthInput) == null

    VibeScaffold(title = "Neuer Eintrag", onBack = onDone) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("📷 Dose scannen", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Fotografiere das Etikett – Marke und mg werden automatisch erkannt. " +
                            "Die Erkennung läuft offline auf deinem Gerät, das Foto wird danach gelöscht.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val uri = viewModel.createCaptureUri() ?: return@Button
                                pendingPhoto = uri
                                try {
                                    takePicture.launch(uri)
                                    cameraMissing = false
                                } catch (e: ActivityNotFoundException) {
                                    cameraMissing = true
                                }
                            },
                            enabled = scanState != ScanState.Scanning,
                            modifier = Modifier.weight(1f)
                        ) { Text("Kamera") }
                        OutlinedButton(
                            onClick = {
                                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            enabled = scanState != ScanState.Scanning,
                            modifier = Modifier.weight(1f)
                        ) { Text("Galerie") }
                    }
                    ScanFeedback(
                        scanState,
                        cameraMissing,
                        onPickStrength = { viewModel.strengthInput = formatMgValue(it) },
                        onReset = viewModel::resetScan
                    )
                }
            }

            if (suggestions.isNotEmpty()) {
                Text("Zuletzt verwendet", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions) { suggestion ->
                        SuggestionChip(
                            onClick = { viewModel.applySuggestion(suggestion) },
                            label = { Text("${suggestion.brand} · ${formatMg(suggestion.nicotineStrength)}") }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = viewModel.strengthInput,
                onValueChange = { viewModel.strengthInput = it },
                label = { Text("Stärke (mg pro Pouch)") },
                placeholder = { Text("z. B. 6 oder 1,5") },
                isError = showStrengthError,
                supportingText = if (showStrengthError) {
                    { Text("Bitte eine gültige Zahl eingeben") }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = viewModel.brandInput,
                onValueChange = { viewModel.brandInput = it },
                label = { Text("Marke (optional)") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TimerSection(
                enabled = viewModel.timerEnabled,
                onEnabledChange = { viewModel.timerEnabled = it },
                targetMinutes = targetMinutes,
                onTargetChange = viewModel::setPouchTarget
            )
            Button(
                onClick = {
                    triedToSave = true
                    if (parseMg(viewModel.strengthInput) == null) return@Button
                    if (viewModel.timerEnabled && !PouchTimer.canPostNotifications(context) &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ) {
                        // Ohne Erlaubnis läuft der Timer trotzdem in der App, nur ohne Benachrichtigung
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else if (viewModel.save()) {
                        onDone()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(52.dp)
            ) {
                Text("Speichern", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private val targetOptions = listOf(15, 20, 30, 45, 60)

@Composable
private fun TimerSection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    targetMinutes: Int,
    onTargetChange: (Int) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("⏱️ Timer starten", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Erinnert dich nach $targetMinutes min, den Pouch rauszunehmen. " +
                            "Kürzer drin = weniger Nikotin.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            if (enabled) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(targetOptions) { minutes ->
                        FilterChip(
                            selected = minutes == targetMinutes,
                            onClick = { onTargetChange(minutes) },
                            label = { Text("$minutes min") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                            )
                        )
                    }
                }
                ExactAlarmHint()
            }
        }
    }
}

/** Hinweis + Knopf, falls Android die Erinnerung sonst verspätet zustellen darf. */
@Composable
private fun ExactAlarmHint() {
    val context = LocalContext.current
    val timer = remember { AppModule.providePouchTimer() }
    var onTime by remember { mutableStateOf(timer.canRemindOnTime()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onTime = timer.canRemindOnTime()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (onTime || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            "⚠️ Ohne „Wecker & Erinnerungen“ darf Android die Erinnerung bis zu 10 min später schicken.",
            style = MaterialTheme.typography.bodySmall
        )
        TextButton(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                )
            },
            contentPadding = PaddingValues(0.dp)
        ) { Text("Pünktliche Erinnerung erlauben") }
    }
}

@Composable
private fun ScanFeedback(
    state: ScanState,
    cameraMissing: Boolean,
    onPickStrength: (Float) -> Unit,
    onReset: () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onPrimaryContainer
    when {
        cameraMissing -> Text("Keine Kamera-App gefunden – nutze „Galerie“.", color = MaterialTheme.colorScheme.error)
        state == ScanState.Scanning -> Column {
            Text("Lese Etikett …", style = MaterialTheme.typography.bodySmall, color = textColor)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
        state == ScanState.Failed -> Text(
            "Bild konnte nicht gelesen werden. Versuch es mit mehr Licht oder näher am Etikett.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        state is ScanState.Done -> {
            val r = state.result
            val message = when {
                r.strengthMg != null && r.brand != null -> "✅ Erkannt: ${r.brand}, ${formatMg(r.strengthMg)} – bitte kurz prüfen."
                r.strengthMg != null -> "✅ ${formatMg(r.strengthMg)} erkannt. Marke bitte selbst eintragen."
                r.perGramMg != null && r.brand != null -> "Marke „${r.brand}“ erkannt."
                r.perGramMg != null -> "Marke bitte selbst eintragen."
                r.brand != null -> "Marke „${r.brand}“ erkannt, aber keine mg-Angabe pro Pouch. Bitte Stärke eintragen."
                else -> "Kein passender Text gefunden. Achte darauf, dass die mg-Angabe gut lesbar ist."
            }
            val hint = when (r.strengthSource) {
                StrengthSource.COMPUTED -> "Berechnet aus ${r.computation}."
                StrengthSource.UNQUALIFIED -> "Unsicher: mg-Angabe ohne „pro Pouch“ gefunden."
                else -> null
            }
            var showRaw by rememberSaveable { mutableStateOf(false) }
            Column {
                Text(message, style = MaterialTheme.typography.bodySmall, color = if (r.strengthMg != null) textColor else Color.Unspecified)
                if (hint != null) Text(hint, style = MaterialTheme.typography.labelSmall, color = textColor)
                if (r.perGramMg != null && r.strengthSource != StrengthSource.COMPUTED) {
                    PerGramHelper(r.perGramMg, onPickStrength)
                }
                val sure = r.strengthSource == StrengthSource.PER_POUCH || r.strengthSource == StrengthSource.COMPUTED
                if (!sure) {
                    Text(
                        "💡 Tipp: Scanne auch die Rückseite/Unterseite – dort steht oft Nettogewicht und Anzahl. " +
                            "Die Fotos werden zusammen ausgewertet.",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (state.pages > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "📄 ${state.pages} Fotos zusammen ausgewertet",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onReset) { Text("Neu anfangen") }
                    }
                }
                TextButton(onClick = { showRaw = !showRaw }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (showRaw) "Erkannten Text ausblenden" else "Erkannten Text anzeigen")
                }
                if (showRaw) {
                    Text(
                        text = state.lines.joinToString("\n") { it.text }.ifBlank { "(kein Text)" },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = textColor
                    )
                }
            }
        }
    }
}

/** Typische Pouch-Gewichte: slim ≈ 0,5–0,7 g, normal ≈ 0,8–1 g. */
private val TYPICAL_POUCH_WEIGHTS = listOf(0.5f, 0.6f, 0.7f, 0.8f, 1.0f)

/**
 * Auf der Dose steht nur "X mg/g": Das ist der Gehalt pro Gramm, nicht pro Pouch.
 * Pouch-Gewicht antippen → mg pro Pouch wird eingetragen.
 */
@Composable
private fun PerGramHelper(perGramMg: Float, onPickStrength: (Float) -> Unit) {
    val textColor = MaterialTheme.colorScheme.onPrimaryContainer
    Column(modifier = Modifier.padding(top = 6.dp)) {
        Text(
            "⚠️ Auf der Dose steht ${formatMgValue(perGramMg)} mg/g – das ist pro Gramm, nicht pro Pouch.",
            style = MaterialTheme.typography.bodySmall,
            color = textColor
        )
        Text(
            "Wie schwer ist ein Pouch? (steht oft klein auf der Dose, sonst: slim ≈ 0,6 g, normal ≈ 0,8–1 g)",
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            items(TYPICAL_POUCH_WEIGHTS) { weight ->
                val mg = Math.round(perGramMg * weight * 10) / 10f
                SuggestionChip(
                    onClick = { onPickStrength(mg) },
                    label = { Text("${formatMgValue(weight)} g → ${formatMg(mg)}") }
                )
            }
        }
    }
}
