package com.alexander.vibevault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alexander.vibevault.ui.EntryCard
import com.alexander.vibevault.ui.SummaryCard
import com.alexander.vibevault.ui.VibeScaffold

@Composable
fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()

    VibeScaffold(title = "Statistiken", onBack = onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryCard("Heute", state.today.count, state.today.totalMg, Modifier.weight(1f))
                    SummaryCard("Letzte 7 Tage", state.last7Days.count, state.last7Days.totalMg, Modifier.weight(1f))
                }
            }
            item {
                Text(
                    text = "Alle Einträge",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }
            if (state.entries.isEmpty()) {
                item { Text("Keine Einträge vorhanden") }
            } else {
                items(state.entries, key = { it.id }) { entry -> EntryCard(entry) }
            }
        }
    }
}
