package com.boratreino.core.model

import java.time.Instant

/** One visit to the gym; open until it has an end time. */
data class Session(
    val id: Long,
    val startedAt: Instant,
    val endedAt: Instant?,
    val routineId: Long?,
    val note: String?,
    /** An extra workout: it does not advance the routine rotation. */
    val isExtra: Boolean = false,
) {
    init {
        require(endedAt == null || endedAt >= startedAt) { "endedAt must not be before startedAt" }
    }

    val isOpen: Boolean get() = endedAt == null
}
