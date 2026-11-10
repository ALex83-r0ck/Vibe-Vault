package com.alexander.vibevault

import android.content.ActivityNotFoundException
import android.net.Uri
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
                    ScanFeedback(scanState, cameraMissing)
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
            Button(
                onClick = {
                    triedToSave = true
                    if (viewModel.save()) onDone()
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

@Composable
private fun ScanFeedback(state: ScanState, cameraMissing: Boolean) {
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
                r.brand != null -> "Marke „${r.brand}“ erkannt, aber keine mg-Angabe pro Pouch. Bitte Stärke eintragen."
                else -> "Kein passender Text gefunden. Achte darauf, dass die mg-Angabe gut lesbar ist."
            }
            Text(message, style = MaterialTheme.typography.bodySmall, color = if (r.strengthMg != null) textColor else Color.Unspecified)
        }
    }
}
