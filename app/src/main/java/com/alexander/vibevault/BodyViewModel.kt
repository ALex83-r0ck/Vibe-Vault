package com.alexander.vibevault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexander.vibevault.data.ConsumptionRepository
import com.alexander.vibevault.data.SettingsRepository
import com.alexander.vibevault.domain.BodySnapshot
import com.alexander.vibevault.domain.CurvePoint
import com.alexander.vibevault.domain.NicotineModel
import com.alexander.vibevault.domain.PkParameters
import com.alexander.vibevault.domain.TimeTicker
import com.alexander.vibevault.domain.curve
import com.alexander.vibevault.domain.snapshot
import com.alexander.vibevault.domain.toDoses
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime

data class BodyUiState(
    val isLoading: Boolean = true,
    val now: LocalDateTime = LocalDateTime.now(),
    val body: BodySnapshot = BodySnapshot(),
    val curve: List<CurvePoint> = emptyList(),
    val params: PkParameters = SettingsRepository.DEFAULT_PK
)

class BodyViewModel(
    repository: ConsumptionRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<BodyUiState> = combine(
        repository.getAllEntries(),
        settings.pkParameters,
        TimeTicker.minutes()
    ) { entries, params, now ->
        val model = NicotineModel(params)
        val doses = entries.toDoses(now)
        BodyUiState(
            isLoading = false,
            now = now,
            body = model.snapshot(doses, now),
            curve = model.curve(doses, now),
            params = params
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyUiState())

    fun updateModel(bodyWeightKg: Double, absorbedFraction: Double) =
        settings.setBodyModel(bodyWeightKg, absorbedFraction)
}
