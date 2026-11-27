package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class KindConversionRulesTest {
    private val base = Instant.parse("2026-11-20T10:00:00Z")

    private fun at(minute: Long): Instant = base.plusSeconds(minute * 60)

    private fun reps(
        position: Int,
        weightKg: Double?,
        reps: Int?,
        minute: Long = position.toLong(),
        type: SetType = SetType.NORMAL,
    ) = WorkoutSet(
        id = position + 1L, sessionId = 1L, machineId = 1L, position = position,
        weightKg = weightKg, reps = reps, seconds = null, calories = null, distanceM = null,
        loggedAt = at(minute), type = type,
    )

    private fun timed(position: Int, weightKg: Double?, seconds: Int, minute: Long = position.toLong()) = WorkoutSet(
        id = position + 1L, sessionId = 1L, machineId = 1L, position = position,
        weightKg = weightKg, reps = null, seconds = seconds, calories = null, distanceM = null,
        loggedAt = at(minute),
    )

    private fun metres(position: Int, weightKg: Double?, distanceM: Int, minute: Long = position.toLong()) = WorkoutSet(
        id = position + 1L, sessionId = 1L, machineId = 1L, position = position,
        weightKg = weightKg, reps = null, seconds = null, calories = null, distanceM = distanceM,
        loggedAt = at(minute),
    )

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
        } catch (e: IllegalArgumentException) {
            return
        }
        fail("expected IllegalArgumentException")
    }

    // Free and Unchanged

    @Test
    fun `the same kind is free even with sets`() {
        val sets = listOf(reps(0, 40.0, 10))

        assertEquals(KindConversion.Free, KindConversionRules.conversion(MachineKind.WEIGHT_REPS, MachineKind.WEIGHT_REPS, sets))
    }

    @Test
    fun `a change with no logged sets is free`() {
        assertEquals(
            KindConversion.Free,
            KindConversionRules.conversion(MachineKind.WEIGHT_REPS, MachineKind.WEIGHT_TIME, emptyList()),
        )
    }

    @Test
    fun `bodyweight to weight and reps is unchanged and keeps the sets`() {
        val backExtension = listOf(reps(0, 10.0, 12))

        assertEquals(
            KindConversion.Unchanged(1),
            KindConversionRules.conversion(MachineKind.BODYWEIGHT_REPS, MachineKind.WEIGHT_REPS, backExtension),
        )
        assertEquals(
            backExtension,
            KindConversionRules.convert(backExtension, MachineKind.BODYWEIGHT_REPS, MachineKind.WEIGHT_REPS, null),
        )
    }

    @Test
    fun `weight and reps to bodyweight is unchanged`() {
        val sets = listOf(reps(0, 0.0, 8), reps(1, 5.0, 6))

        assertEquals(
            KindConversion.Unchanged(2),
            KindConversionRules.conversion(MachineKind.WEIGHT_REPS, MachineKind.BODYWEIGHT_REPS, sets),
        )
    }

    @Test
    fun `time only to weight and time is unchanged and keeps the seconds`() {
        val sets = listOf(timed(0, 0.0, 30), timed(1, 0.0, 45))

        assertEquals(
            KindConversion.Unchanged(2),
            KindConversionRules.conversion(MachineKind.TIME, MachineKind.WEIGHT_TIME, sets),
        )
        assertEquals(sets, KindConversionRules.convert(sets, MachineKind.TIME, MachineKind.WEIGHT_TIME, null))
    }

    @Test
    fun `weight and time to time only is unchanged when no set has a load`() {
        val sets = listOf(timed(0, 0.0, 30), timed(1, null, 45))

        assertEquals(
            KindConversion.Unchanged(2),
            KindConversionRules.conversion(MachineKind.WEIGHT_TIME, MachineKind.TIME, sets),
        )
    }

    @Test
    fun `converting to time only forces 0 kg on the converted sets`() {
        val sets = listOf(timed(0, 0.0, 30), timed(1, null, 45))

        val converted = KindConversionRules.convert(sets, MachineKind.WEIGHT_TIME, MachineKind.TIME, null)

        assertEquals(listOf(0.0, 0.0), converted.map { it.weightKg })
        assertEquals(listOf(30, 45), converted.map { it.seconds })
    }

    // Owner's case: Farmer's Walk

    @Test
    fun `farmers walk from weight and reps needs a factor of seconds per rep, offered at 30`() {
        val sets = listOf(reps(0, 45.0, 1), reps(1, 47.5, 1), reps(2, 48.0, 1))

        val conversion = KindConversionRules.conversion(MachineKind.WEIGHT_REPS, MachineKind.WEIGHT_TIME, sets)

        assertTrue(conversion is KindConversion.NeedsFactor)
        conversion as KindConversion.NeedsFactor
        assertEquals(3, conversion.setCount)
        assertEquals(ConversionFactor.SECONDS_PER_REP, conversion.factor)
        assertEquals(30, conversion.default)
        assertEquals(48.0, conversion.sample.weightKg!!, 0.0)
    }

    @Test
    fun `farmers walk with factor 30 turns each one rep set into 30 seconds at the same load`() {
        val sets = listOf(reps(0, 45.0, 1), reps(1, 47.5, 1), reps(2, 48.0, 1))

        val converted = KindConversionRules.convert(sets, MachineKind.WEIGHT_REPS, MachineKind.WEIGHT_TIME, 30)

        assertEquals(listOf(45.0, 47.5, 48.0), converted.map { it.weightKg })
        assertEquals(listOf(30, 30, 30), converted.map { it.seconds })
        assertTrue(converted.all { it.reps == null && it.distanceM == null })
    }

    @Test
    fun `reps times the factor gives the seconds, so 3 reps at factor 30 is 90 seconds`() {
        val set = reps(0, 20.0, 3)

        val converted = KindConversionRules.convertSet(set, MachineKind.WEIGHT_REPS, MachineKind.WEIGHT_TIME, 30)

        assertEquals(90, converted.seconds)
        assertEquals(20.0, converted.weightKg!!, 0.0)
        assertEquals(null, converted.reps)
    }

    @Test
    fun `converting back with the same factor restores the reps`() {
        val sets = listOf(reps(0, 45.0, 1), reps(1, 40.0, 8), reps(2, 40.0, 3))

        val timedSets = KindConversionRules.convert(sets, MachineKind.WEIGHT_REPS, MachineKind.WEIGHT_TIME, 30)
        val back = KindConversionRules.convert(timedSets, MachineKind.WEIGHT_TIME, MachineKind.WEIGHT_REPS, 30)

        assertEquals(listOf(1, 8, 3), back.map { it.reps })
        assertEquals(listOf(45.0, 40.0, 40.0), back.map { it.weightKg })
        assertEquals(sets, back)
    }

    @Test
    fun `seconds become reps by rounding half up, and a short set is at least one rep`() {
        val factor = 30
        fun repsFor(seconds: Int): Int? = KindConversionRules
            .convertSet(timed(0, 20.0, seconds), MachineKind.WEIGHT_TIME, MachineKind.WEIGHT_REPS, factor)
            .reps

        assertEquals(3, repsFor(90))
        assertEquals(3, repsFor(100))
        assertEquals(4, repsFor(105))
        assertEquals(1, repsFor(10))
    }

    // Metres

    @Test
    fun `reps to metres has no default factor and is offered as needing one`() {
        val sets = listOf(reps(0, 40.0, 4))

        val conversion = KindConversionRules.conversion(MachineKind.WEIGHT_REPS, MachineKind.WEIGHT_DISTANCE, sets)

        assertTrue(conversion is KindConversion.NeedsFactor)
        conversion as KindConversion.NeedsFactor
        assertEquals(ConversionFactor.METRES_PER_REP, conversion.factor)
        assertEquals(null, conversion.default)
    }

    @Test
    fun `metres to reps needs a metres factor too`() {
        val conversion = KindConversionRules.conversion(
            MachineKind.WEIGHT_DISTANCE, MachineKind.WEIGHT_REPS, listOf(metres(0, 40.0, 25)),
        )

        assertTrue(conversion is KindConversion.NeedsFactor)
        conversion as KindConversion.NeedsFactor
        assertEquals(ConversionFactor.METRES_PER_REP, conversion.factor)
        assertEquals(null, conversion.default)
    }

    @Test
    fun `reps to metres at factor 10 turns 4 reps into 40 metres`() {
        val converted = KindConversionRules.convert(listOf(reps(0, 40.0, 4)), MachineKind.WEIGHT_REPS, MachineKind.WEIGHT_DISTANCE, 10)

        assertEquals(40, converted.single().distanceM)
        assertEquals(40.0, converted.single().weightKg!!, 0.0)
        assertEquals(null, converted.single().reps)
    }

    @Test
    fun `metres to reps at factor 10 turns 25 metres into 3 reps`() {
        val converted = KindConversionRules.convert(
            listOf(metres(0, 40.0, 25)), MachineKind.WEIGHT_DISTANCE, MachineKind.WEIGHT_REPS, 10,
        )

        assertEquals(3, converted.single().reps)
        assertEquals(null, converted.single().distanceM)
    }

    @Test
    fun `converting to metres without a factor is refused`() {
        assertRejected {
            KindConversionRules.convert(listOf(reps(0, 40.0, 4)), MachineKind.WEIGHT_REPS, MachineKind.WEIGHT_DISTANCE, null)
        }
    }

    // Refused conversions

    @Test
    fun `cardio is not offered in either direction`() {
        val sets = listOf(reps(0, 40.0, 10))

        assertEquals(
            KindConversion.NotOffered(NotOfferedReason.CARDIO),
            KindConversionRules.conversion(MachineKind.WEIGHT_REPS, MachineKind.CARDIO, sets),
        )
        assertEquals(
            KindConversion.NotOffered(NotOfferedReason.CARDIO),
            KindConversionRules.conversion(MachineKind.CARDIO, MachineKind.WEIGHT_REPS, sets),
        )
        assertEquals(
            KindConversion.NotOffered(NotOfferedReason.CARDIO),
            KindConversionRules.conversion(MachineKind.CARDIO, MachineKind.TIME, listOf(timed(0, 0.0, 30))),
        )
    }

    @Test
    fun `seconds and metres are not offered in either direction`() {
        assertEquals(
            KindConversion.NotOffered(NotOfferedReason.NO_COMMON_MEASURE),
            KindConversionRules.conversion(MachineKind.WEIGHT_TIME, MachineKind.WEIGHT_DISTANCE, listOf(timed(0, 40.0, 30))),
        )
        assertEquals(
            KindConversion.NotOffered(NotOfferedReason.NO_COMMON_MEASURE),
            KindConversionRules.conversion(MachineKind.WEIGHT_DISTANCE, MachineKind.WEIGHT_TIME, listOf(metres(0, 40.0, 25))),
        )
        assertEquals(
            KindConversion.NotOffered(NotOfferedReason.NO_COMMON_MEASURE),
            KindConversionRules.conversion(MachineKind.TIME, MachineKind.WEIGHT_DISTANCE, listOf(timed(0, 0.0, 30))),
        )
    }

    @Test
    fun `time only with a load is not offered, because the load would be lost`() {
        val sets = listOf(reps(0, 20.0, 10))

        assertEquals(
            KindConversion.NotOffered(NotOfferedReason.LOAD_WOULD_BE_LOST),
            KindConversionRules.conversion(MachineKind.WEIGHT_REPS, MachineKind.TIME, sets),
        )
        assertEquals(
            KindConversion.NotOffered(NotOfferedReason.LOAD_WOULD_BE_LOST),
            KindConversionRules.conversion(MachineKind.WEIGHT_TIME, MachineKind.TIME, listOf(timed(0, 47.5, 30))),
        )
    }

    @Test
    fun `time only is refused when any one set has a load, even if the others have none`() {
        val sets = listOf(reps(0, 0.0, 10), reps(1, 20.0, 10))

        assertEquals(
            KindConversion.NotOffered(NotOfferedReason.LOAD_WOULD_BE_LOST),
            KindConversionRules.conversion(MachineKind.WEIGHT_REPS, MachineKind.TIME, sets),
        )
    }

    @Test
    fun `reps to time only is offered as seconds per rep when no set has a load`() {
        val sets = listOf(reps(0, 0.0, 10))

        val conversion = KindConversionRules.conversion(MachineKind.WEIGHT_REPS, MachineKind.TIME, sets)

        assertTrue(conversion is KindConversion.NeedsFactor)
        conversion as KindConversion.NeedsFactor
        assertEquals(ConversionFactor.SECONDS_PER_REP, conversion.factor)
        assertEquals(30, conversion.default)
    }

    @Test
    fun `reading the offers lists every kind except the one it starts from`() {
        val sets = listOf(reps(0, 40.0, 10))

        val offers = KindConversionRules.conversions(MachineKind.WEIGHT_REPS, sets)

        assertEquals(MachineKind.entries.filter { it != MachineKind.WEIGHT_REPS }.toSet(), offers.keys)
        assertEquals(KindConversion.NotOffered(NotOfferedReason.CARDIO), offers[MachineKind.CARDIO])
        assertEquals(KindConversion.Unchanged(1), offers[MachineKind.BODYWEIGHT_REPS])
        assertEquals(KindConversion.NotOffered(NotOfferedReason.LOAD_WOULD_BE_LOST), offers[MachineKind.TIME])
    }

    // Sets and samples

    @Test
    fun `warm-ups count as sets in the conversion`() {
        val sets = listOf(
            reps(0, 40.0, 10),
            reps(1, 45.0, 8),
            reps(2, 20.0, 15, type = SetType.WARMUP),
        )

        assertEquals(
            KindConversion.Unchanged(3),
            KindConversionRules.conversion(MachineKind.WEIGHT_REPS, MachineKind.BODYWEIGHT_REPS, sets),
        )
        assertEquals(3, KindConversionRules.convert(sets, MachineKind.WEIGHT_REPS, MachineKind.BODYWEIGHT_REPS, null).size)
    }

    @Test
    fun `the sample for the preview is the latest working set`() {
        val sets = listOf(
            reps(0, 40.0, 10, minute = 0),
            reps(1, 45.0, 8, minute = 5),
            reps(2, 20.0, 15, minute = 10, type = SetType.WARMUP),
        )

        val conversion = KindConversionRules.conversion(MachineKind.WEIGHT_REPS, MachineKind.WEIGHT_TIME, sets)

        conversion as KindConversion.NeedsFactor
        assertEquals(45.0, conversion.sample.weightKg!!, 0.0)
    }

    @Test
    fun `the sample is the latest set when every set is a warm-up`() {
        val sets = listOf(
            reps(0, 20.0, 10, minute = 0, type = SetType.WARMUP),
            reps(1, 25.0, 8, minute = 5, type = SetType.WARMUP),
        )

        val conversion = KindConversionRules.conversion(MachineKind.WEIGHT_REPS, MachineKind.WEIGHT_TIME, sets)

        conversion as KindConversion.NeedsFactor
        assertEquals(25.0, conversion.sample.weightKg!!, 0.0)
    }

    @Test
    fun `a converted set keeps its id, session, position, time and type`() {
        val set = reps(4, 40.0, 10, minute = 7, type = SetType.DROP)

        val converted = KindConversionRules.convertSet(set, MachineKind.WEIGHT_REPS, MachineKind.WEIGHT_TIME, 30)

        assertEquals(set.id, converted.id)
        assertEquals(set.sessionId, converted.sessionId)
        assertEquals(set.machineId, converted.machineId)
        assertEquals(set.position, converted.position)
        assertEquals(set.loggedAt, converted.loggedAt)
        assertEquals(SetType.DROP, converted.type)
    }

    @Test
    fun `a set with no weight converts with 0 kg`() {
        val converted = KindConversionRules.convertSet(reps(0, null, 10), MachineKind.WEIGHT_REPS, MachineKind.WEIGHT_TIME, 30)

        assertEquals(0.0, converted.weightKg!!, 0.0)
    }

    // Ranges and clamping

    @Test
    fun `a factor of 1 and the largest seconds factor are accepted, one above is refused`() {
        assertEquals(5, KindConversionRules.convertSet(reps(0, 0.0, 5), MachineKind.WEIGHT_REPS, MachineKind.TIME, 1).seconds)
        assertEquals(
            600 * 2,
            KindConversionRules.convertSet(reps(0, 0.0, 2), MachineKind.WEIGHT_REPS, MachineKind.TIME, 600).seconds,
        )
        assertRejected {
            KindConversionRules.convert(listOf(reps(0, 0.0, 5)), MachineKind.WEIGHT_REPS, MachineKind.TIME, 601)
        }
    }

    @Test
    fun `a factor of 0 or below is refused`() {
        assertRejected {
            KindConversionRules.convert(listOf(reps(0, 40.0, 5)), MachineKind.WEIGHT_REPS, MachineKind.WEIGHT_TIME, 0)
        }
        assertRejected {
            KindConversionRules.convert(listOf(reps(0, 40.0, 5)), MachineKind.WEIGHT_REPS, MachineKind.WEIGHT_TIME, -30)
        }
    }

    @Test
    fun `the metres factor is refused above 1000`() {
        assertEquals(
            1000 * 2,
            KindConversionRules.convertSet(reps(0, 0.0, 2), MachineKind.WEIGHT_REPS, MachineKind.WEIGHT_DISTANCE, 1000).distanceM,
        )
        assertRejected {
            KindConversionRules.convert(listOf(reps(0, 0.0, 2)), MachineKind.WEIGHT_REPS, MachineKind.WEIGHT_DISTANCE, 1001)
        }
    }

    @Test
    fun `a needed factor is refused when it is missing`() {
        assertRejected {
            KindConversionRules.convert(listOf(reps(0, 40.0, 5)), MachineKind.WEIGHT_REPS, MachineKind.WEIGHT_TIME, null)
        }
        assertRejected {
            KindConversionRules.convertSet(reps(0, 40.0, 5), MachineKind.WEIGHT_REPS, MachineKind.WEIGHT_TIME, null)
        }
    }

    @Test
    fun `a conversion that is not offered is refused`() {
        assertRejected {
            KindConversionRules.convert(listOf(reps(0, 40.0, 10)), MachineKind.WEIGHT_REPS, MachineKind.CARDIO, null)
        }
        assertRejected {
            KindConversionRules.convert(listOf(reps(0, 20.0, 10)), MachineKind.WEIGHT_REPS, MachineKind.TIME, 30)
        }
    }

    @Test
    fun `converted amounts are clamped to the logging maxima`() {
        val longHold = KindConversionRules.convertSet(reps(0, 0.0, 100), MachineKind.WEIGHT_REPS, MachineKind.TIME, 600)
        assertEquals(LoggingRules.MAX_SECONDS, longHold.seconds)

        val farCarry = KindConversionRules.convertSet(reps(0, 40.0, 100), MachineKind.WEIGHT_REPS, MachineKind.WEIGHT_DISTANCE, 100)
        assertEquals(LoggingRules.MAX_DISTANCE_M, farCarry.distanceM)

        val manyReps = KindConversionRules.convertSet(timed(0, 40.0, 3600), MachineKind.WEIGHT_TIME, MachineKind.WEIGHT_REPS, 1)
        assertEquals(LoggingRules.MAX_REPS, manyReps.reps)
    }
}
