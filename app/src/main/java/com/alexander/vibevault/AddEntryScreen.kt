package com.alexander.vibevault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alexander.vibevault.ui.VibeScaffold
import com.alexander.vibevault.ui.formatMg
import com.alexander.vibevault.ui.formatMgValue
import com.alexander.vibevault.ui.parseMg
import java.time.LocalDateTime

@Composable
fun AddEntryScreen(viewModel: AddEntryViewModel, onDone: () -> Unit) {
    val suggestions by viewModel.suggestions.collectAsState()
    var nicotineStrength by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var triedToSave by remember { mutableStateOf(false) }

    val parsedStrength = parseMg(nicotineStrength)
    val showStrengthError = triedToSave && parsedStrength == null

    VibeScaffold(title = "Neuer Eintrag", onBack = onDone) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (suggestions.isNotEmpty()) {
                Text("Zuletzt verwendet", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions) { suggestion ->
                        SuggestionChip(
                            onClick = {
                                brand = suggestion.brand
                                nicotineStrength = formatMgValue(suggestion.nicotineStrength)
                            },
                            label = { Text("${suggestion.brand} · ${formatMg(suggestion.nicotineStrength)}") }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = nicotineStrength,
                onValueChange = { nicotineStrength = it },
                label = { Text("Stärke (mg)") },
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
                value = brand,
                onValueChange = { brand = it },
                label = { Text("Marke (optional)") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    triedToSave = true
                    if (parsedStrength != null) {
                        viewModel.saveEntry(LocalDateTime.now(), parsedStrength, brand.trim())
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
