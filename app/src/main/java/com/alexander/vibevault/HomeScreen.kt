package com.alexander.vibevault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alexander.vibevault.ui.EntryCard
import com.alexander.vibevault.ui.SectionHeader
import com.alexander.vibevault.ui.StatTile
import com.alexander.vibevault.ui.SwipeToDelete
import com.alexander.vibevault.ui.VibeScaffold
import com.alexander.vibevault.ui.formatBodyMg
import com.alexander.vibevault.ui.formatMg
import com.alexander.vibevault.ui.relativeTo
import com.alexander.vibevault.ui.formatMgValue
import com.alexander.vibevault.ui.parseMg
import com.alexander.vibevault.ui.pluralDays
import com.alexander.vibevault.ui.showUndoDelete
import com.alexander.vibevault.ui.theme.HeroGradient
import com.alexander.vibevault.ui.theme.StatusGood
import com.alexander.vibevault.ui.theme.StatusOver
import com.alexander.vibevault.ui.theme.StatusWarning

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAddEntry: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenBody: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showLimitDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // Neuer Timer wird oberhalb eingefügt – ohne Scrollen bliebe er außerhalb des Bildes
    LaunchedEffect(state.activePouch?.entryId) {
        if (state.activePouch != null) listState.animateScrollToItem(0)
    }

    VibeScaffold(
        title = "VibeVault",
        snackbarHostState = snackbarHostState,
        actions = {
            IconButton(onClick = onOpenAchievements) {
                Icon(Icons.Filled.Star, contentDescription = "Erfolge")
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddEntry,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Eintrag") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            // unten extra Platz, damit der FAB die letzte Card nicht verdeckt
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            state.activePouch?.let { active ->
                item(key = "active_pouch") {
                    ActivePouchCard(
                        startedAt = active.startedAt,
                        targetMinutes = state.pouchTargetMinutes,
                        label = state.activeEntry?.let { e ->
                            listOfNotNull(e.brand.ifBlank { null }, formatMg(e.nicotineStrength)).joinToString(" · ")
                        },
                        onRemoveNow = viewModel::removePouchNow,
                        onRemoveAtTarget = viewModel::removePouchAtTarget
                    )
                }
            }
            item {
                TodayHeroCard(
                    todayMg = state.todayMg,
                    limitMg = state.dailyLimitMg,
                    onEditLimit = { showLimitDialog = true }
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        emoji = "🔥",
                        value = pluralDays(state.currentStreak),
                        label = "Serie im Limit",
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = onOpenAchievements
                    )
                    StatTile(
                        emoji = "🏆",
                        value = "${state.unlockedAchievements}/${state.totalAchievements}",
                        label = "Erfolge",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenAchievements
                    )
                }
            }
            item {
                StatTile(
                    emoji = "🧬",
                    value = "${formatBodyMg(state.body.nicotineMg)} im Körper",
                    label = state.body.nicotineFreeAt
                        ?.let { "Nikotinfrei ab ca. ${it.relativeTo(state.now)} · Details zum Abbau" }
                        ?: "Praktisch nikotinfrei 🌿 · Details zum Abbau",
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onOpenBody
                )
            }
            item {
                SectionHeader("Letzte Einträge") {
                    TextButton(onClick = onOpenStats) { Text("Alle & Statistik") }
                }
            }
            if (!state.isLoading && state.recentEntries.isEmpty()) {
                item {
                    Text(
                        text = "Noch keine Einträge. Tippe auf „Eintrag“, um den ersten anzulegen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.recentEntries, key = { it.id }) { entry ->
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
                item {
                    Text(
                        text = "Tipp: Zum Löschen nach links wischen",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }

    if (showLimitDialog) {
        DailyLimitDialog(
            currentLimit = state.dailyLimitMg,
            onDismiss = { showLimitDialog = false },
            onConfirm = {
                viewModel.setDailyLimit(it)
                showLimitDialog = false
            }
        )
    }
}

/** Läuft sekundengenau, solange ein Pouch im Mund ist. */
@Composable
private fun ActivePouchCard(
    startedAt: LocalDateTime,
    targetMinutes: Int,
    label: String?,
    onRemoveNow: () -> Unit,
    onRemoveAtTarget: () -> Unit
) {
    val elapsedSeconds by produceState(initialValue = secondsSince(startedAt), startedAt) {
        while (true) {
            value = secondsSince(startedAt)
            delay(1_000L - System.currentTimeMillis() % 1_000L)
        }
    }
    val targetSeconds = targetMinutes * 60L
    val over = elapsedSeconds > targetSeconds
    val forgotten = elapsedSeconds > targetSeconds + 30 * 60 // deutlich drüber: vermutlich vergessen zu stoppen
    val container = if (over) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (over) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = if (over) "⏰ Pouch ist über der Zeit" else "⏱️ Pouch drin",
                style = MaterialTheme.typography.titleMedium
            )
            if (label != null) Text(label, style = MaterialTheme.typography.bodySmall)
            Text(
                text = formatClock(elapsedSeconds),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = if (over) {
                    "${formatClock(elapsedSeconds - targetSeconds)} über deinem Ziel von $targetMinutes min"
                } else {
                    "Noch ${formatClock(targetSeconds - elapsedSeconds)} bis zu deinem Ziel ($targetMinutes min)"
                },
                style = MaterialTheme.typography.bodyMedium
            )
            LinearProgressIndicator(
                progress = { (elapsedSeconds.toFloat() / targetSeconds).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .height(8.dp),
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                trackColor = content.copy(alpha = 0.15f),
                strokeCap = StrokeCap.Round
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRemoveNow,
                    colors = if (over) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    }
                ) { Text("Raus genommen") }
                if (forgotten) {
                    TextButton(onClick = onRemoveAtTarget) { Text("War nach $targetMinutes min raus") }
                }
            }
        }
    }
}

private fun secondsSince(start: LocalDateTime): Long =
    java.time.Duration.between(start, LocalDateTime.now()).seconds.coerceAtLeast(0)

/** 754 → "12:34", 4000 → "1:06:40" */
private fun formatClock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
private fun TodayHeroCard(todayMg: Float, limitMg: Float, onEditLimit: () -> Unit) {
    val fraction = if (limitMg > 0f) todayMg / limitMg else 1f
    val statusColor = when {
        fraction > 1f -> StatusOver
        fraction >= 0.8f -> StatusWarning
        else -> StatusGood
    }
    val statusText = when {
        todayMg == 0f -> "Noch nichts heute – stark! 🌿"
        fraction > 1f -> "${formatMg(todayMg - limitMg)} über dem Limit"
        else -> "Noch ${formatMg(limitMg - todayMg)} im Limit"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(HeroGradient))
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(116.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    color = statusColor,
                    trackColor = Color.White.copy(alpha = 0.22f),
                    strokeWidth = 11.dp,
                    strokeCap = StrokeCap.Round
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatMgValue(todayMg),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Text(
                        text = "von ${formatMg(limitMg)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Heute", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )
                FilledTonalButton(
                    onClick = onEditLimit,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Limit ändern", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun DailyLimitDialog(currentLimit: Float, onDismiss: () -> Unit, onConfirm: (Float) -> Unit) {
    var input by remember { mutableStateOf(formatMgValue(currentLimit)) }
    val parsed = parseMg(input)?.takeIf { it > 0f }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tageslimit") },
        text = {
            Column {
                Text(
                    "Bleibst du an einem Tag bei höchstens diesem Wert, wächst deine Serie. 🔥",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Limit (mg pro Tag)") },
                    isError = parsed == null,
                    supportingText = if (parsed == null) {
                        { Text("Bitte eine Zahl größer 0 eingeben") }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
