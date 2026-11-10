package com.alexander.vibevault.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Taktgeber für zeitabhängige Anzeigen. Ohne ihn bliebe "heute" nach Mitternacht auf dem Vortag
 * stehen, bis sich zufällig ein Eintrag ändert.
 */
object TimeTicker {
    /** Emittiert sofort und danach zu jedem Minutenwechsel. */
    fun minutes(): Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            val millisToNextMinute = 60_000L - System.currentTimeMillis() % 60_000L
            delay(millisToNextMinute + 50L)
        }
    }

    /** Emittiert das aktuelle Datum, neu nur beim Tageswechsel. */
    fun days(): Flow<LocalDate> = minutes().map { it.toLocalDate() }.distinctUntilChanged()
}
