package com.boratreino.core.health

import kotlinx.coroutines.CoroutineScope

/** Keeps Health Connect in step with the finished trainings while the setting is on. */
interface HealthSync {
    /** Starts watching; safe to call more than once. */
    fun start(scope: CoroutineScope)
}
