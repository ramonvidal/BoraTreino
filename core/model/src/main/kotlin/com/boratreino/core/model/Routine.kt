package com.boratreino.core.model

import java.time.DayOfWeek

/** One workout in a plan, such as "Legs", with the weekdays it is planned for. */
data class Routine(
    val id: Long,
    val planId: Long,
    val name: String,
    val shortName: String,
    val position: Int,
    val weekdays: Set<DayOfWeek>,
) {
    init {
        require(name.isNotBlank()) { "Routine name must not be blank" }
        require(shortName.isNotBlank()) { "Routine shortName must not be blank" }
    }
}
