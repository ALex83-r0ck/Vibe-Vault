package com.alexander.vibevault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alexander.vibevault.domain.Achievement
import com.alexander.vibevault.ui.StatTile
import com.alexander.vibevault.ui.VibeScaffold
import com.alexander.vibevault.ui.formatMg
import com.alexander.vibevault.ui.pluralDays

@Composable
fun AchievementsScreen(viewModel: AchievementsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val unlocked = state.achievements.count { it.unlocked }
    val total = state.achievements.size

    VibeScaffold(title = "Erfolge", onBack = onBack) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        emoji = "🔥",
                        value = pluralDays(state.currentStreak),
                        label = "Aktuelle Serie",
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    StatTile(
                        emoji = "⭐",
                        value = pluralDays(state.bestStreak),
                        label = "Beste Serie",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text("$unlocked von $total freigeschaltet", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else unlocked / total.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(8.dp),
                        strokeCap = StrokeCap.Round,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    Text(
                        text = "Ein Tag zählt, wenn du höchstens ${formatMg(state.dailyLimitMg)} konsumierst.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            items(state.achievements, key = { it.id }) { achievement ->
                AchievementCard(achievement)
            }
        }
    }
}

@Composable
private fun AchievementCard(achievement: Achievement) {
    val container = if (achievement.unlocked) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .alpha(if (achievement.unlocked) 1f else 0.55f),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Text(
                text = if (achievement.unlocked) achievement.emoji else "🔒",
                style = MaterialTheme.typography.displaySmall
            )
            Text(
                text = achievement.title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = achievement.description,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2
            )
            if (!achievement.unlocked && achievement.progress != null) {
                Text(
                    text = achievement.progress,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
