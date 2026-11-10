package com.alexander.vibevault

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexander.vibevault.data.ConsumptionRepository
import com.alexander.vibevault.data.LabelScanner
import com.alexander.vibevault.data.model.ConsumptionEntry
import com.alexander.vibevault.domain.LabelParser
import com.alexander.vibevault.domain.LabelScanResult
import com.alexander.vibevault.ui.formatMgValue
import com.alexander.vibevault.ui.parseMg
import kotlinx.coroutines.CancellationException
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
    data class Done(val result: LabelScanResult) : ScanState
    data object Failed : ScanState
}

class AddEntryViewModel(
    private val repository: ConsumptionRepository,
    private val scanner: LabelScanner
) : ViewModel() {

    var strengthInput by mutableStateOf("")
    var brandInput by mutableStateOf("")

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

    /** null, wenn kein Speicherplatz für das Foto angelegt werden konnte. */
    fun createCaptureUri(): Uri? = runCatching { scanner.createCaptureUri() }.getOrNull()

    /** Kamera abgebrochen: angelegte leere Fotodatei entfernen. */
    fun discardCapture() {
        viewModelScope.launch { scanner.clearCapturedPhotos() }
    }

    fun scan(uri: Uri) {
        viewModelScope.launch {
            _scanState.value = ScanState.Scanning
            try {
                val lines = scanner.readLines(uri)
                val knownBrands = repository.getAllEntries().first().map { it.brand }.distinct()
                val result = LabelParser.parse(lines, knownBrands)
                result.strengthMg?.let { strengthInput = formatMgValue(it) }
                result.brand?.let { brandInput = it }
                _scanState.value = ScanState.Done(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _scanState.value = ScanState.Failed
            } finally {
                scanner.clearCapturedPhotos()
            }
        }
    }

    /** Speichert, wenn die Eingabe gültig ist. */
    fun save(): Boolean {
        val strength = parseMg(strengthInput) ?: return false
        viewModelScope.launch {
            repository.insertEntry(
                ConsumptionEntry(
                    timestamp = LocalDateTime.now(),
                    nicotineStrength = strength,
                    brand = brandInput.trim()
                )
            )
        }
        return true
    }
}
