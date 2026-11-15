package com.boratreino.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.boratreino.core.data.TrainingRepository
import com.boratreino.core.settings.BodySettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Reads the plan and redraws the "Next workout" widgets: once on demand, and while the app process lives. */
@Singleton
class NextWorkoutWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val training: TrainingRepository,
    private val bodySettings: BodySettings,
) {
    private val renderer = NextWorkoutWidgetRenderer(context)

    private fun stateFlow(): Flow<NextWorkoutWidgetState> = combine(
        training.observeTodayPlan(),
        training.observeOpenSession(),
        bodySettings.profile,
    ) { plan, open, profile -> widgetStateOf(plan, open, profile?.sex) }

    private fun widgetIds(manager: AppWidgetManager): IntArray =
        manager.getAppWidgetIds(ComponentName(context, NextWorkoutWidgetProvider::class.java))

    /** Reads the plan once and redraws; for the provider's `onUpdate` and `onEnabled`. */
    suspend fun refreshOnce() {
        val manager = AppWidgetManager.getInstance(context)
        val ids = widgetIds(manager)
        if (ids.isEmpty()) return
        renderer.update(manager, ids, stateFlow().first())
    }

    /** Redraws whenever the plan changes (debounced), but only when a widget is placed. */
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        stateFlow()
            .distinctUntilChanged()
            .debounce(DEBOUNCE_MILLIS)
            .onEach {
                val manager = AppWidgetManager.getInstance(context)
                renderer.update(manager, widgetIds(manager), it)
            }
            .launchIn(scope)
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 500L
    }
}
