package com.alexander.vibevault

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.alexander.vibevault.data.Habits
import com.alexander.vibevault.data.Reminders
import com.alexander.vibevault.data.SettingsRepository
import com.alexander.vibevault.ui.VibeScaffold
import com.alexander.vibevault.ui.formatMgValue
import com.alexander.vibevault.ui.parseMg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val reminders: Reminders,
    private val appScope: CoroutineScope
) : ViewModel() {
    val habits: StateFlow<Habits> = settings.habits

    fun update(change: (Habits) -> Habits) {
        settings.setHabits(change(settings.habits.value))
        appScope.launch { reminders.refresh() }
    }
}

private val pauseOptions = listOf(0, 30, 45, 60, 90, 120)
private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val habits by viewModel.habits.collectAsState()

    VibeScaffold(title = "Einstellungen", onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsCard("🧘 Pause nach dem Rausnehmen") {
                Hint("Zeigt dir, wann der nächste Pouch frühestens dran wäre. Nichts wird gesperrt.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pauseOptions.forEach { minutes ->
                        FilterChip(
                            selected = habits.pauseMinutes == minutes,
                            onClick = { viewModel.update { it.copy(pauseMinutes = minutes) } },
                            label = { Text(if (minutes == 0) "Aus" else "$minutes min") }
                        )
                    }
                }
            }

            SettingsCard("🌙 Schlaf & Morgen") {
                TimeRow("Schlafenszeit", habits.bedtime) { t -> viewModel.update { it.copy(bedtime = t) } }
                TimeRow("Aufstehen", habits.wakeTime) { t -> viewModel.update { it.copy(wakeTime = t) } }
                SwitchRow(
                    title = "Abends erinnern",
                    subtitle = "30 min bevor der letzte Pouch für guten Schlaf fällig wäre",
                    checked = habits.eveningReminder
                ) { on -> viewModel.update { it.copy(eveningReminder = on) } }
            }

            SettingsCard("💶 Kosten") {
                var price by rememberSaveable { mutableStateOf(habits.pricePerCan?.let(::formatMgValue) ?: "") }
                var perCan by rememberSaveable { mutableStateOf(habits.pouchesPerCan.toString()) }
                OutlinedTextField(
                    value = price,
                    onValueChange = { input ->
                        price = input
                        val value = parseMg(input)
                        if (input.isBlank() || value != null) viewModel.update { it.copy(pricePerCan = value?.takeIf { v -> v > 0f }) }
                    },
                    label = { Text("Preis pro Dose (€)") },
                    placeholder = { Text("z. B. 5,50") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = perCan,
                    onValueChange = { input ->
                        perCan = input.filter { it.isDigit() }.take(3)
                        perCan.toIntOrNull()?.takeIf { it > 0 }?.let { n -> viewModel.update { it.copy(pouchesPerCan = n) } }
                    },
                    label = { Text("Pouches pro Dose") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SettingsCard("🗓️ Wochenrückblick") {
                SwitchRow(
                    title = "Sonntags um 19 Uhr",
                    subtitle = "Deine Woche in Zahlen – ohne Vorwürfe",
                    checked = habits.weeklyReview
                ) { on -> viewModel.update { it.copy(weeklyReview = on) } }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Hint(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun TimeRow(label: String, time: LocalTime, onChange: (LocalTime) -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = {
            TimePickerDialog(context, { _, h, m -> onChange(LocalTime.of(h, m)) }, time.hour, time.minute, true).show()
        }) { Text(time.format(hhmm)) }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Hint(subtitle)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
