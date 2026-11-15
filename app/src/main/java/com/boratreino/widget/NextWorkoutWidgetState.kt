package com.boratreino.widget

import com.boratreino.core.model.OpenSession
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.Sex
import com.boratreino.core.model.TodayPlan

/** What the second line of the widget says; the renderer turns each kind into a string resource. */
enum class WidgetDetail {
    /** "N exercises": [NextWorkoutWidgetState.exerciseCount]. */
    EXERCISES,

    /** "Continue: <exercise>": [NextWorkoutWidgetState.continueExercise]. */
    CONTINUE,

    /** "Rest day". */
    REST_DAY,

    /** "No routine yet". */
    NO_ROUTINE,

    /** Nothing to say (a free workout, or a rest day with no routine to show). */
    NONE,
}

/**
 * Everything the "Next workout" widget shows, with no Android types so it is testable on the JVM.
 * [routineName] null means the renderer shows its own fallback title.
 */
data class NextWorkoutWidgetState(
    val routineName: String?,
    val detail: WidgetDetail,
    val exerciseCount: Int = 0,
    val continueExercise: String? = null,
    /** Picks the mascot: the woman for [Sex.FEMALE], the man otherwise (also when unknown). */
    val sex: Sex? = null,
)

/**
 * Maps the Today plan, the open session and the owner's sex to the widget state.
 *
 * 1. A session with logged sets: the routine's name (or none for a free workout) and "Continue: <exercise last logged>".
 * 2. No routines at all: "No routine yet".
 * 3. Rest day: the upcoming routine's name (when known) and "Rest day".
 * 4. Otherwise the routine of the plan ([TodayPlan.active], else [TodayPlan.suggested]) and its exercise count,
 *    not counting the ones skipped today. A free workout has no routine, so no detail.
 */
fun widgetStateOf(plan: TodayPlan, open: OpenSession?, sex: Sex?): NextWorkoutWidgetState {
    val active = plan.active
    val last = open?.machines?.firstOrNull()?.machine?.name
    if (last != null) {
        return NextWorkoutWidgetState(
            routineName = active?.routine?.name,
            detail = WidgetDetail.CONTINUE,
            continueExercise = last,
            sex = sex,
        )
    }
    if (plan.routines.isEmpty()) {
        return NextWorkoutWidgetState(routineName = null, detail = WidgetDetail.NO_ROUTINE, sex = sex)
    }
    if (plan.restDay && open == null) {
        val upcoming = plan.upcoming?.routine?.name
        return NextWorkoutWidgetState(
            routineName = upcoming,
            detail = if (upcoming != null) WidgetDetail.REST_DAY else WidgetDetail.NONE,
            sex = sex,
        )
    }
    val name = active?.routine?.name ?: plan.suggested?.name.takeIf { plan.choice != RoutineChoice.Free }
    val count = active?.items?.count { it.machineId !in plan.skipped } ?: 0
    return NextWorkoutWidgetState(
        routineName = name,
        detail = if (active != null) WidgetDetail.EXERCISES else WidgetDetail.NONE,
        exerciseCount = count,
        sex = sex,
    )
}
