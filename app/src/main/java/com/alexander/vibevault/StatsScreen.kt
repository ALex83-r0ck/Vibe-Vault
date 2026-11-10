package com.alexander.vibevault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alexander.vibevault.ui.EntryCard
import com.alexander.vibevault.ui.SectionHeader
import com.alexander.vibevault.ui.StatTile
import com.alexander.vibevault.ui.SwipeToDelete
import com.alexander.vibevault.ui.VibeScaffold
import com.alexander.vibevault.ui.WeekBarChart
import com.alexander.vibevault.ui.formatMg
import com.alexander.vibevault.ui.showUndoDelete

@Composable
fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    VibeScaffold(title = "Statistik", onBack = onBack, snackbarHostState = snackbarHostState) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        emoji = "☀️",
                        value = formatMg(state.today.totalMg),
                        label = "Heute · ${entriesLabel(state.today.count)}",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        emoji = "📊",
                        value = formatMg(state.last7Days.totalMg),
                        label = "7 Tage · ${entriesLabel(state.last7Days.count)}",
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Letzte 7 Tage", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Linie = Tageslimit (${formatMg(state.dailyLimitMg)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        WeekBarChart(bars = state.week, limitMg = state.dailyLimitMg, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            item { SectionHeader("Alle Einträge") }
            if (state.entries.isEmpty()) {
                item {
                    Text(
                        "Keine Einträge vorhanden",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.entries, key = { it.id }) { entry ->
                    SwipeToDelete(
                        onDelete = {
                            viewModel.deleteEntry(entry)
                            scope.showUndoDelete(snackbarHostState) { viewModel.restoreEntry(entry) }
                        },
                        modifier = Modifier.animateItem()
                    ) {
                        EntryCard(entry)
                    }
                }
            }
        }
    }
}

private fun entriesLabel(count: Int) = if (count == 1) "1 Eintrag" else "$count Einträge"
