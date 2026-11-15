package com.boratreino.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.boratreino.R
import com.boratreino.core.model.Sex
import com.boratreino.core.notify.Notifications
import com.boratreino.core.ui.R as CoreUiR

/** Builds the widget's [RemoteViews] from a [NextWorkoutWidgetState]; holds no data logic. */
class NextWorkoutWidgetRenderer(private val context: Context) {

    fun render(state: NextWorkoutWidgetState): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_next_workout)
        views.setImageViewResource(
            R.id.widget_mascot,
            if (state.sex == Sex.FEMALE) CoreUiR.drawable.mascot_woman else CoreUiR.drawable.mascot_man,
        )
        views.setTextViewText(R.id.widget_title, title(state))
        val detail = detail(state)
        views.setViewVisibility(R.id.widget_detail, if (detail == null) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.widget_detail, detail.orEmpty())
        Notifications.openAppIntent(context)?.let { views.setOnClickPendingIntent(R.id.widget_root, it) }
        return views
    }

    /** Pushes [state] to every placed widget; does nothing when there is none. */
    fun update(manager: AppWidgetManager, ids: IntArray, state: NextWorkoutWidgetState) {
        if (ids.isEmpty()) return
        manager.updateAppWidget(ids, render(state))
    }

    private fun title(state: NextWorkoutWidgetState): String = state.routineName ?: when (state.detail) {
        WidgetDetail.NO_ROUTINE -> context.getString(R.string.app_name)
        WidgetDetail.REST_DAY, WidgetDetail.NONE -> context.getString(R.string.widget_rest_day)
        else -> context.getString(R.string.widget_free_workout)
    }

    private fun detail(state: NextWorkoutWidgetState): String? = when (state.detail) {
        WidgetDetail.EXERCISES ->
            context.resources.getQuantityString(R.plurals.widget_exercises, state.exerciseCount, state.exerciseCount)
        WidgetDetail.CONTINUE -> context.getString(R.string.widget_continue, state.continueExercise.orEmpty())
        WidgetDetail.REST_DAY -> context.getString(R.string.widget_rest_day)
        WidgetDetail.NO_ROUTINE -> context.getString(R.string.widget_no_routine)
        WidgetDetail.NONE -> null
    }
}
