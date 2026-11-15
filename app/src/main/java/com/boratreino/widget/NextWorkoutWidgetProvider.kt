package com.boratreino.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The "Next workout" home-screen widget: shows the next routine and opens the app. No buttons change data. */
class NextWorkoutWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun updater(): NextWorkoutWidgetUpdater
    }

    override fun onEnabled(context: Context) = refresh(context)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) =
        refresh(context)

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) = refresh(context)

    private fun refresh(context: Context) {
        val pending = goAsync()
        val updater = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        ).updater()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                updater.refreshOnce()
            } finally {
                pending.finish()
            }
        }
    }
}
