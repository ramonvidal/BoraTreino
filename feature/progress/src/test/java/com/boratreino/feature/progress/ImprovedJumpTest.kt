package com.boratreino.feature.progress

import com.boratreino.core.model.MachineProgress
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.ProgressOverview
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** A flagged load jump is never a "most improved" row on the Exercises tab. */
class ImprovedJumpTest {
    private val today = ProgressSamples.today

    private fun progress(id: Long, name: String, rows: List<Pair<LocalDate, Double>>) = MachineProgress(
        ProgressSamples.machine(id, name, MuscleGroup.ARMS),
        ProgressMetric.WEIGHT,
        ProgressSamples.points(id * 100, rows),
    )

    private fun overview(vararg machines: MachineProgress) = ProgressOverview(machines.toList(), 0, 0)

    @Test
    fun `a machine with an unconfirmed load jump is left out of most improved`() {
        // 20 kg to 35 kg in 13 days, and no later session at 35 kg or near it: the jump is flagged.
        val jumped = progress(
            5,
            "Biceps curl",
            listOf(ProgressSamples.date(9, 1) to 20.0, ProgressSamples.date(10, 12) to 20.0, ProgressSamples.date(10, 25) to 35.0),
        )
        val steady = progress(1, "Press", listOf(ProgressSamples.date(9, 1) to 100.0, ProgressSamples.date(10, 20) to 110.0))

        val state = buildProgressState(overview(jumped, steady), today)

        assertEquals(listOf("Press"), state.improved.map { it.machine.name })
    }

    @Test
    fun `the same machine without the jump is most improved`() {
        // The same weeks with a smaller step, so nothing is flagged.
        val steady = progress(
            5,
            "Biceps curl",
            listOf(ProgressSamples.date(9, 1) to 20.0, ProgressSamples.date(10, 12) to 20.0, ProgressSamples.date(10, 25) to 24.0),
        )

        val state = buildProgressState(overview(steady), today)

        assertEquals(listOf("Biceps curl"), state.improved.map { it.machine.name })
    }
}
