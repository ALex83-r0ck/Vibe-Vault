package com.alexander.vibevault

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexander.vibevault.data.ConsumptionRepository
import com.alexander.vibevault.data.LabelScanner
import com.alexander.vibevault.data.PouchTimer
import com.alexander.vibevault.data.SettingsRepository
import com.alexander.vibevault.data.model.ConsumptionEntry
import com.alexander.vibevault.domain.LabelParser
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Zuletzt verwendete Kombination aus Marke und Stärke als Schnellauswahl. */
data class EntrySuggestion(val brand: String, val nicotineStrength: Float)

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
    private val appScope: CoroutineScope
) : ViewModel() {

    var strengthInput by mutableStateOf("")
    var brandInput by mutableStateOf("")
    var timerEnabled by mutableStateOf(true)

    val pouchTargetMinutes: StateFlow<Int> = settings.pouchTargetMinutes

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
            brand = brandInput.trim()
        )
        appScope.launch {
            val id = repository.insertEntry(entry)
            if (startTimer) pouchTimer.start(id, entry.timestamp)
        }
        return true
    }

    companion object {
        const val OCR_TAG = "VibeVaultOCR"
    }
}
