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
import com.alexander.vibevault.ui.formatEuro
import com.alexander.vibevault.ui.formatMgValue
import com.alexander.vibevault.ui.formatMinutes
import com.alexander.vibevault.domain.CostSummary
import com.alexander.vibevault.domain.FirstPouchTrend
import com.alexander.vibevault.domain.PauseStats
import com.alexander.vibevault.domain.StrengthTip
import com.alexander.vibevault.domain.Trigger
import com.alexander.vibevault.domain.WeeklyReview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import com.alexander.vibevault.ui.showUndoDelete

@Composable
fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit, onOpenSettings: () -> Unit) {
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
            state.review?.let { review -> item { WeeklyReviewCard(review, state.dailyLimitMg) } }
            item { SectionHeader("Deine Muster (30 Tage)") }
            item { TriggerCard(state.triggers) }
            item { PauseStatsCard(state.pauses, state.pauseMinutes) }
            item { FirstPouchCard(state.firstPouch) }
            item { StrengthCard(state.strengthMix, state.strengthTip) }
            item { CostCard(state.cost, onOpenSettings) }
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

@Composable
private fun InsightCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

/** Horizontaler Balken mit Beschriftung links und Wert rechts. */
@Composable
private fun BarRow(label: String, fraction: Float, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(130.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Text(value, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(44.dp))
    }
}

@Composable
private fun WeeklyReviewCard(review: WeeklyReview, limitMg: Float) {
    InsightCard("🗓️ Deine Woche") {
        Text(
            "${formatMg(review.totalMg)} · ${review.pouches} Pouches",
            style = MaterialTheme.typography.headlineSmall
        )
        review.changePercent?.let { change ->
            Text(
                when {
                    change < 0 -> "📉 ${-change} % weniger als in der Vorwoche – stark!"
                    change > 0 -> "📈 $change % mehr als in der Vorwoche."
                    else -> "Genauso viel wie in der Vorwoche."
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Hint("${review.daysInLimit} von ${review.daysTracked} Tagen im Limit (${formatMg(limitMg)})")
        if (review.pauses.total > 0) Hint("${review.pauses.kept} von ${review.pauses.total} Pausen eingehalten")
        review.topTrigger?.let {
            Hint("Häufigster Auslöser: ${it.emoji} ${it.label} (${(review.topTriggerShare * 100).toInt()} %)")
        }
    }
}

@Composable
private fun TriggerCard(triggers: List<Pair<Trigger, Int>>) {
    InsightCard("🎯 Auslöser") {
        if (triggers.isEmpty()) {
            Hint("Tippe beim Eintragen an, was gerade los ist – hier siehst du dann, wann du am häufigsten zum Pouch greifst.")
        } else {
            val total = triggers.sumOf { it.second }
            triggers.forEach { (trigger, count) ->
                BarRow("${trigger.emoji} ${trigger.label}", count.toFloat() / total, "${count * 100 / total} %")
            }
            val top = triggers.first().first
            Hint(
                when (top) {
                    Trigger.BORED -> "Langeweile ist dein Top-Auslöser – oft hilft schon, kurz etwas anderes zu tun."
                    Trigger.STRESS -> "Stress ist dein Top-Auslöser – ein paar tiefe Atemzüge können die erste Welle abfangen."
                    Trigger.COFFEE, Trigger.MEAL -> "Gewohnheits-Kopplung: Der Pouch hängt an einer festen Situation. Die lässt sich am leichtesten verschieben."
                    else -> "Wenn du deine Auslöser kennst, kannst du gezielt Alternativen ausprobieren."
                }
            )
        }
    }
}

@Composable
private fun PauseStatsCard(pauses: PauseStats, pauseMinutes: Int) {
    InsightCard("🧘 Pausen") {
        val avg = pauses.averageGapMinutes
        if (avg == null) {
            Hint("Sobald du mehrere Pouches an einem Tag einträgst, siehst du hier deinen Abstand dazwischen.")
        } else {
            Text("Ø ${formatMinutes(avg)} zwischen zwei Pouches", style = MaterialTheme.typography.titleSmall)
            if (pauseMinutes > 0) {
                BarRow("Gehalten", pauses.kept.toFloat() / pauses.total, "${pauses.kept}/${pauses.total}")
                Hint("Deine Pause: $pauseMinutes min nach dem Rausnehmen")
            }
        }
    }
}

@Composable
private fun FirstPouchCard(trend: FirstPouchTrend) {
    InsightCard("🌅 Erster Pouch am Morgen") {
        val now = trend.thisWeekMinutes
        if (now == null) {
            Hint("Noch keine Daten aus den letzten 7 Tagen.")
        } else {
            Text("Ø ${formatMinutes(now)} nach dem Aufstehen", style = MaterialTheme.typography.titleSmall)
            trend.previousWeekMinutes?.let { prev ->
                val diff = now - prev
                Hint(
                    when {
                        diff > 5 -> "⬆️ ${formatMinutes(diff)} später als in der Vorwoche – sehr gut."
                        diff < -5 -> "⬇️ ${formatMinutes(-diff)} früher als in der Vorwoche."
                        else -> "Ungefähr wie in der Vorwoche."
                    }
                )
            }
            Hint("Je länger du morgens ohne auskommst, desto weniger bestimmt das Nikotin deinen Tag.")
        }
    }
}

@Composable
private fun StrengthCard(mix: List<Pair<Float, Int>>, tip: StrengthTip?) {
    InsightCard("💪 Stärken") {
        if (mix.isEmpty()) {
            Hint("Noch keine Einträge in den letzten 30 Tagen.")
        } else {
            val max = mix.maxOf { it.second }
            mix.forEach { (mg, count) -> BarRow(formatMg(mg), count.toFloat() / max, "${count}×") }
        }
        tip?.let {
            Hint(
                "💡 Mit ${formatMg(it.toMg)} statt ${formatMg(it.fromMg)} wären es diese Woche " +
                    "${formatMgValue(it.savedMg)} mg weniger gewesen (${it.count} Pouches)."
            )
        }
    }
}

@Composable
private fun CostCard(cost: CostSummary?, onOpenSettings: () -> Unit) {
    InsightCard("💶 Kosten") {
        if (cost == null) {
            Hint("Trag den Preis einer Dose ein, dann siehst du, was du im Monat und im Jahr ausgibst.")
            TextButton(onClick = onOpenSettings, contentPadding = PaddingValues(0.dp)) { Text("Preis eintragen") }
        } else {
            Text("${formatEuro(cost.thisMonthEuro)} diesen Monat", style = MaterialTheme.typography.titleSmall)
            Hint("Hochgerechnet ≈ ${formatEuro(cost.perYearEuro)} im Jahr · ${formatEuro(cost.pricePerPouchEuro)} pro Pouch")
        }
    }
}
