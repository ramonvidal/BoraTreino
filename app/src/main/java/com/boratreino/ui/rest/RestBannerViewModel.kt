package com.boratreino.ui.rest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.model.RestTimerRules
import com.boratreino.core.notify.RestTimer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** What the global rest banner shows: whole seconds left out of [totalSeconds]. */
data class RestBannerState(val remainingSeconds: Int, val totalSeconds: Int)

/** Watches the rest timer for the banner at the top of every screen; null = no rest running. */
@HiltViewModel
class RestBannerViewModel @Inject constructor(
    private val restTimer: RestTimer,
    private val clock: Clock,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val ticking: Flow<RestBannerState?> = restTimer.state.flatMapLatest { timer ->
        val endsAt = timer.endsAt
        if (endsAt == null) {
            flowOf(null)
        } else {
            flow {
                while (true) {
                    val left = RestTimerRules.remainingSeconds(endsAt, clock.instant())
                    if (left <= 0) {
                        emit(null)
                        break
                    }
                    emit(RestBannerState(left, timer.durationSeconds))
                    delay(1_000)
                }
            }
        }
    }

    val state: StateFlow<RestBannerState?> =
        ticking.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun extend() = restTimer.extend()

    fun skip() = restTimer.skip()
}
