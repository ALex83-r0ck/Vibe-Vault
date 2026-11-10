package com.alexander.vibevault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alexander.vibevault.ui.EntryCard
import com.alexander.vibevault.ui.VibeScaffold

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAddEntry: () -> Unit,
    onOpenStats: () -> Unit
) {
    val recentEntries by viewModel.recentEntries.collectAsState()

    VibeScaffold(
        title = "VibeVault",
        floatingActionButton = {
            FloatingActionButton(onClick = onAddEntry) {
                Icon(Icons.Filled.Add, contentDescription = "Neuen Eintrag hinzufügen")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            // unten extra Platz, damit der FAB die letzte Card nicht verdeckt
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Letzte Einträge", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onOpenStats) { Text("Statistiken") }
                }
            }
            if (recentEntries.isEmpty()) {
                item {
                    Text(
                        text = "Noch keine Einträge. Tippe auf + um den ersten anzulegen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(recentEntries, key = { it.id }) { entry -> EntryCard(entry) }
            }
        }
    }
}
