package com.alexander.vibevault

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexander.vibevault.data.ActivePouch
import com.alexander.vibevault.data.ConsumptionRepository
import com.alexander.vibevault.data.Habits
import com.alexander.vibevault.domain.PkParameters
import com.alexander.vibevault.data.LabelScanner
import com.alexander.vibevault.data.PouchTimer
import com.alexander.vibevault.data.SettingsRepository
import com.alexander.vibevault.data.model.ConsumptionEntry
import com.alexander.vibevault.domain.Insights
import com.alexander.vibevault.domain.LabelParser
import com.alexander.vibevault.domain.LevelLight
import com.alexander.vibevault.domain.NicotineModel
import com.alexander.vibevault.domain.Side
import com.alexander.vibevault.domain.TimeTicker
import com.alexander.vibevault.domain.Trigger
import com.alexander.vibevault.domain.toDoses
import com.alexander.vibevault.domain.LabelScanResult
import com.alexander.vibevault.domain.OcrLine
import com.alexander.vibevault.ui.formatMgValue
import com.alexander.vibevault.ui.parseMg
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Zuletzt verwendete Kombination aus Marke und Stärke als Schnellauswahl. */
data class EntrySuggestion(val brand: String, val nicotineStrength: Float)

/** Der Moment vor dem Pouch: wie viel ist noch im Blut, läuft eine Pause? */
data class EntryContext(
    val now: LocalDateTime,
    val ngPerMlNow: Double,
    val ngPerMlIn30: Double,
    val light: LevelLight,
    val pauseEndsAt: LocalDateTime?,
    val pouchActive: Boolean,
    val suggestedSide: Side?,
    val entries: List<ConsumptionEntry>
)

private data class ModelSettings(val pk: PkParameters, val active: ActivePouch?, val target: Int, val habits: Habits)

sealed interface ScanState {
    data object Idle : ScanState
    data object Scanning : ScanState
    /** [pages] = Anzahl zusammengeführter Fotos (z. B. Deckel + Unterseite). */
    data class Done(val result: LabelScanResult, val lines: List<OcrLine>, val pages: Int = 1) : ScanState
    data object Failed : ScanState
}

class AddEntryViewModel(
    private val repository: ConsumptionRepository,
    private val settings: SettingsRepository,
    private val scanner: LabelScanner,
    private val pouchTimer: PouchTimer,
    private val reminders: com.alexander.vibevault.data.Reminders,
    private val appScope: CoroutineScope
) : ViewModel() {

    var strengthInput by mutableStateOf("")
    var brandInput by mutableStateOf("")
    var timerEnabled by mutableStateOf(true)
    var trigger by mutableStateOf<Trigger?>(null)
    var side by mutableStateOf<Side?>(null)

    val pouchTargetMinutes: StateFlow<Int> = settings.pouchTargetMinutes

    private val modelSettings = combine(
        settings.pkParameters, settings.activePouch, settings.pouchTargetMinutes, settings.habits
    ) { pk, active, target, habits -> ModelSettings(pk, active, target, habits) }

    val entryContext: StateFlow<EntryContext?> = combine(
        repository.getAllEntries(), modelSettings, TimeTicker.minutes()
    ) { entries, s, now ->
        val (pk, active, target, habits) = s
        val model = NicotineModel(pk)
        val doses = entries.toDoses(now, active?.entryId, target)
        val ngNow = model.nicotineNgPerMl(doses, now)
        EntryContext(
            now = now,
            ngPerMlNow = ngNow,
            ngPerMlIn30 = model.nicotineNgPerMl(doses, now.plusMinutes(30)),
            light = Insights.light(ngNow),
            pauseEndsAt = Insights.pauseEndsAt(entries, active?.entryId, habits.pauseMinutes, target, now),
            pouchActive = active != null,
            suggestedSide = Insights.suggestedSide(entries),
            entries = entries
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    val suggestions: StateFlow<List<EntrySuggestion>> = repository.getAllEntries()
        .map { entries ->
            entries.asSequence()
                .filter { it.brand.isNotBlank() }
                .map { EntrySuggestion(it.brand, it.nicotineStrength) }
                .distinct()
                .take(4)
                .toList()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun applySuggestion(suggestion: EntrySuggestion) {
        brandInput = suggestion.brand
        strengthInput = formatMgValue(suggestion.nicotineStrength)
    }

    fun setPouchTarget(minutes: Int) = settings.setPouchTarget(minutes)

    /** null, wenn kein Speicherplatz für das Foto angelegt werden konnte. */
    fun createCaptureUri(): Uri? = runCatching { scanner.createCaptureUri() }.getOrNull()

    /** Kamera abgebrochen: angelegte leere Fotodatei entfernen. */
    fun discardCapture() {
        viewModelScope.launch { scanner.clearCapturedPhotos() }
    }

    /** Text aller bisherigen Fotos dieser Eingabe – Vorder- und Rückseite ergänzen sich. */
    private var scannedLines: List<OcrLine> = emptyList()
    private var scannedPages = 0

    /** Bisherige Scans verwerfen und neu anfangen. */
    fun resetScan() {
        scannedLines = emptyList()
        scannedPages = 0
        _scanState.value = ScanState.Idle
    }

    fun scan(uri: Uri) {
        viewModelScope.launch {
            _scanState.value = ScanState.Scanning
            try {
                val newLines = scanner.readLines(uri)
                val lines = scannedLines + newLines
                scannedLines = lines
                scannedPages++
                // Rohtext fürs Debugging: adb logcat -s VibeVaultOCR
                Log.d(OCR_TAG, "---- Scan $scannedPages: ${newLines.size} Zeilen ----")
                newLines.forEach { Log.d(OCR_TAG, "h=${it.heightPx.toString().padStart(4)} | ${it.text}") }
                val knownBrands = repository.getAllEntries().first().map { it.brand }.distinct()
                val result = LabelParser.parse(lines, knownBrands)
                Log.d(OCR_TAG, "Ergebnis: $result")
                result.strengthMg?.let { strengthInput = formatMgValue(it) }
                result.brand?.let { brandInput = it }
                _scanState.value = ScanState.Done(result, lines, scannedPages)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(OCR_TAG, "Scan fehlgeschlagen", e)
                _scanState.value = ScanState.Failed
            } finally {
                scanner.clearCapturedPhotos()
            }
        }
    }

    /**
     * Speichert, wenn die Eingabe gültig ist, und startet ggf. den Timer.
     * Läuft im App-Scope, damit es auch nach dem Schließen des Screens fertig wird.
     */
    fun save(): Boolean {
        val strength = parseMg(strengthInput) ?: return false
        val startTimer = timerEnabled
        val entry = ConsumptionEntry(
            timestamp = LocalDateTime.now(),
            nicotineStrength = strength,
            brand = brandInput.trim(),
            trigger = trigger?.key,
            side = side?.key
        )
        appScope.launch {
            val id = repository.insertEntry(entry)
            if (startTimer) pouchTimer.start(id, entry.timestamp) else reminders.refresh()
        }
        return true
    }

    companion object {
        const val OCR_TAG = "VibeVaultOCR"
    }
}
