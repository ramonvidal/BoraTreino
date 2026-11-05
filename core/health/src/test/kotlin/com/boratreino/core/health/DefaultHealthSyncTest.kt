package com.boratreino.core.health

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.data.HistoryRepository
import com.boratreino.core.model.HealthExerciseKind
import com.boratreino.core.model.HealthExport
import com.boratreino.core.model.RepeatMode
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.model.SessionInfo
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.settings.HealthSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** What the sync does with Health Connect, on fakes: no Health Connect, database or DataStore. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DefaultHealthSyncTest {
    private val start = Instant.parse("2026-11-02T18:00:00Z")

    private class FakeAccess : HealthConnectAccess {
        var availability: HealthAvailability = HealthAvailability.Ready
        var availabilityError: Exception? = null
        var failWrites = 0
        var failDeletes = 0
        val written = mutableListOf<List<HealthExport>>()
        val deleted = mutableListOf<Set<Long>>()

        override suspend fun availability(): HealthAvailability {
            availabilityError?.let { throw it }
            return availability
        }

        override val permissions: Set<String> = emptySet()

        override fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> = error("not used")

        override fun installIntent(): Intent = error("not used")

        override suspend fun write(exports: List<HealthExport>) {
            if (failWrites > 0) {
                failWrites--
                throw IllegalStateException("write failed")
            }
            written += exports
        }

        override suspend fun delete(sessionIds: Set<Long>) {
            if (failDeletes > 0) {
                failDeletes--
                throw IllegalStateException("delete failed")
            }
            deleted += sessionIds
        }
    }

    private class FakeSettings : HealthSettings {
        val enabledFlow = MutableStateFlow(false)
        val syncedFlow = MutableStateFlow<Set<Long>>(emptySet())
        val storedSyncedValues = mutableListOf<Set<Long>>()

        override val enabled: Flow<Boolean> = enabledFlow

        override suspend fun setEnabled(enabled: Boolean) {
            enabledFlow.value = enabled
        }

        override val syncedSessionIds: Flow<Set<Long>> = syncedFlow

        override suspend fun setSyncedSessionIds(ids: Set<Long>) {
            storedSyncedValues += ids
            syncedFlow.value = ids
        }
    }

    private class FakeHistory : HistoryRepository {
        val exports = MutableStateFlow<List<HealthExport>>(emptyList())
        val sessionIds = MutableStateFlow<Set<Long>>(emptySet())
        val requestedDays = mutableListOf<Int>()

        override fun observeClosedSessionExports(sinceDays: Int): Flow<List<HealthExport>> {
            requestedDays += sinceDays
            return exports
        }

        override fun observeSessionIds(): Flow<Set<Long>> = sessionIds

        override fun observeMonth(month: YearMonth): Flow<Map<LocalDate, List<SessionInfo>>> = error("not used")
        override fun observeDay(date: LocalDate): Flow<List<SessionDetail>> = error("not used")
        override fun observeSession(sessionId: Long): Flow<SessionDetail?> = error("not used")
        override fun observeTrainingDates(): Flow<Set<LocalDate>> = error("not used")
        override suspend fun addSession(date: LocalDate, routineId: Long?): Long = error("not used")
        override suspend fun setSessionRoutine(sessionId: Long, routineId: Long?): Unit = error("not used")
        override suspend fun addSet(sessionId: Long, machineId: Long, draft: SetDraft): WorkoutSet = error("not used")
        override suspend fun addCardioSet(sessionId: Long, machineId: Long, draft: CardioDraft): WorkoutSet = error("not used")
        override suspend fun lastTimeBefore(sessionId: Long, machineId: Long): List<WorkoutSet> = error("not used")
        override suspend fun deleteSession(sessionId: Long): SessionDetail? = error("not used")
        override suspend fun restoreSession(detail: SessionDetail): Unit = error("not used")
        override suspend fun repeatLastSession(sessionId: Long, mode: RepeatMode): List<WorkoutSet> = error("not used")
    }

    private lateinit var access: FakeAccess
    private lateinit var settings: FakeSettings
    private lateinit var history: FakeHistory
    private lateinit var sync: DefaultHealthSync

    @Before
    fun setUp() {
        access = FakeAccess()
        settings = FakeSettings()
        history = FakeHistory()
        sync = DefaultHealthSync(access, history, settings)
    }

    private fun export(sessionId: Long, version: Long = 1L) = HealthExport(
        sessionId = sessionId,
        clientRecordId = "boratreino-session-$sessionId",
        version = version,
        start = start,
        end = start.plusSeconds(3600),
        kind = HealthExerciseKind.STRENGTH_TRAINING,
        title = "Training",
        activeCalories = 0,
        distanceM = 0,
        strengthSets = 3,
        strengthReps = 30,
    )

    /** Starts the sync in the test's background and lets the debounce and the collectors run. */
    private fun TestScope.startSync() {
        sync.start(backgroundScope)
        settle()
    }

    /**
     * Lets the debounce (3 s of virtual time) and the collectors run. advanceUntilIdle() is not enough:
     * it ignores work that only lives in backgroundScope, and the sync runs there.
     */
    private fun TestScope.settle() {
        runCurrent()
        advanceTimeBy(4_000)
        runCurrent()
    }

    private val allWritten get() = access.written.flatten()

    // Off

    @Test
    fun `while the setting is off nothing is written, deleted or stored`() = runTest {
        history.exports.value = listOf(export(1))
        history.sessionIds.value = setOf(1L)
        settings.syncedFlow.value = setOf(2L)

        startSync()

        assertTrue(access.written.isEmpty())
        assertTrue(access.deleted.isEmpty())
        assertTrue(settings.storedSyncedValues.isEmpty())
        assertEquals(setOf(2L), settings.syncedFlow.value)
    }

    @Test
    fun `while the setting is off the exports are not even watched`() = runTest {
        startSync()

        assertTrue(history.requestedDays.isEmpty())
    }

    // On and ready

    @Test
    fun `on and ready writes the exports and remembers their sessions`() = runTest {
        history.exports.value = listOf(export(1), export(2))
        history.sessionIds.value = setOf(1L, 2L)
        settings.enabledFlow.value = true

        startSync()

        assertEquals(listOf(1L, 2L), allWritten.map { it.sessionId })
        assertEquals(setOf(1L, 2L), settings.syncedFlow.value)
        assertTrue(access.deleted.isEmpty())
    }

    @Test
    fun `it watches the last 30 days`() = runTest {
        settings.enabledFlow.value = true

        startSync()

        assertTrue(history.requestedDays.isNotEmpty())
        assertTrue(history.requestedDays.all { it == 30 })
    }

    @Test
    fun `a new export is written when it appears`() = runTest {
        settings.enabledFlow.value = true
        history.exports.value = listOf(export(1))
        history.sessionIds.value = setOf(1L)
        startSync()

        history.exports.value = listOf(export(1), export(2))
        history.sessionIds.value = setOf(1L, 2L)
        settle()

        assertTrue(allWritten.any { it.sessionId == 2L })
        assertEquals(setOf(1L, 2L), settings.syncedFlow.value)
    }

    @Test
    fun `a changed export is written again with its new version`() = runTest {
        settings.enabledFlow.value = true
        history.exports.value = listOf(export(1, version = 10))
        history.sessionIds.value = setOf(1L)
        startSync()

        history.exports.value = listOf(export(1, version = 11))
        settle()

        assertEquals(listOf(10L, 11L), allWritten.filter { it.sessionId == 1L }.map { it.version })
    }

    @Test
    fun `emissions in quick succession are written once, with the latest content`() = runTest {
        settings.enabledFlow.value = true
        history.sessionIds.value = setOf(1L)
        sync.start(backgroundScope)
        runCurrent()

        advanceTimeBy(500)
        history.exports.value = listOf(export(1, version = 1))
        advanceTimeBy(500)
        history.exports.value = listOf(export(1, version = 2))
        settle()

        assertEquals(listOf(2L), allWritten.map { it.version })
    }

    @Test
    fun `starting twice does not write twice`() = runTest {
        settings.enabledFlow.value = true
        history.exports.value = listOf(export(1))
        history.sessionIds.value = setOf(1L)

        sync.start(backgroundScope)
        sync.start(backgroundScope)
        settle()

        assertEquals(listOf(1L), allWritten.map { it.sessionId })
    }

    // Deleted sessions

    @Test
    fun `a synced session that no longer exists is deleted from Health Connect`() = runTest {
        settings.enabledFlow.value = true
        settings.syncedFlow.value = setOf(1L, 2L)
        history.exports.value = listOf(export(2))
        history.sessionIds.value = setOf(2L)

        startSync()

        assertEquals(listOf(setOf(1L)), access.deleted)
        assertEquals(setOf(2L), settings.syncedFlow.value)
    }

    @Test
    fun `several deleted sessions go in one delete`() = runTest {
        settings.enabledFlow.value = true
        settings.syncedFlow.value = setOf(1L, 2L, 3L)
        history.exports.value = emptyList()
        history.sessionIds.value = setOf(3L)

        startSync()

        assertEquals(listOf(setOf(1L, 2L)), access.deleted)
        assertEquals(setOf(3L), settings.syncedFlow.value)
    }

    @Test
    fun `nothing is deleted when every synced session still exists`() = runTest {
        settings.enabledFlow.value = true
        settings.syncedFlow.value = setOf(1L, 2L)
        history.exports.value = listOf(export(1), export(2))
        history.sessionIds.value = setOf(1L, 2L)

        startSync()

        assertTrue(access.deleted.isEmpty())
    }

    @Test
    fun `a synced session older than the window but still in the database is kept`() = runTest {
        settings.enabledFlow.value = true
        settings.syncedFlow.value = setOf(1L)
        history.exports.value = listOf(export(5))
        history.sessionIds.value = setOf(1L, 5L)

        startSync()

        assertTrue(access.deleted.isEmpty())
        assertEquals(setOf(1L, 5L), settings.syncedFlow.value)
    }

    @Test
    fun `deleting a session later removes its record on the next emission`() = runTest {
        settings.enabledFlow.value = true
        history.exports.value = listOf(export(1), export(2))
        history.sessionIds.value = setOf(1L, 2L)
        startSync()

        history.sessionIds.value = setOf(2L)
        history.exports.value = listOf(export(2))
        settle()

        assertEquals(listOf(setOf(1L)), access.deleted)
        assertEquals(setOf(2L), settings.syncedFlow.value)
    }

    // Not ready

    @Test
    fun `when Health Connect is not ready nothing is written, deleted or stored`() = runTest {
        val notReady = listOf(
            HealthAvailability.Unsupported,
            HealthAvailability.NotInstalled,
            HealthAvailability.NeedsUpdate,
            HealthAvailability.NeedsPermission,
        )
        for (state in notReady) {
            val a = FakeAccess().apply { availability = state }
            val s = FakeSettings().apply {
                enabledFlow.value = true
                syncedFlow.value = setOf(9L)
            }
            val h = FakeHistory().apply {
                exports.value = listOf(export(1))
                sessionIds.value = setOf(1L)
            }

            DefaultHealthSync(a, h, s).start(backgroundScope)
            settle()

            assertTrue("$state wrote", a.written.isEmpty())
            assertTrue("$state deleted", a.deleted.isEmpty())
            assertTrue("$state stored", s.storedSyncedValues.isEmpty())
        }
    }

    @Test
    fun `it starts writing once Health Connect becomes ready`() = runTest {
        access.availability = HealthAvailability.NeedsPermission
        settings.enabledFlow.value = true
        history.exports.value = listOf(export(1))
        history.sessionIds.value = setOf(1L)
        startSync()
        assertTrue(access.written.isEmpty())

        access.availability = HealthAvailability.Ready
        history.exports.value = listOf(export(1), export(2))
        history.sessionIds.value = setOf(1L, 2L)
        settle()

        assertEquals(setOf(1L, 2L), allWritten.map { it.sessionId }.toSet())
    }

    // Turning off and on

    @Test
    fun `turning it off stops writing and leaves the existing records alone`() = runTest {
        settings.enabledFlow.value = true
        history.exports.value = listOf(export(1))
        history.sessionIds.value = setOf(1L)
        startSync()
        val writesBefore = access.written.size

        settings.enabledFlow.value = false
        settle()
        history.exports.value = listOf(export(1), export(2))
        history.sessionIds.value = setOf(2L)
        settle()

        assertEquals(writesBefore, access.written.size)
        assertTrue(access.deleted.isEmpty())
    }

    @Test
    fun `turning it on later starts writing`() = runTest {
        history.exports.value = listOf(export(1))
        history.sessionIds.value = setOf(1L)
        startSync()
        assertTrue(access.written.isEmpty())

        settings.enabledFlow.value = true
        settle()

        assertEquals(listOf(1L), allWritten.map { it.sessionId })
    }

    // Errors

    @Test
    fun `a failing write is swallowed and the next emission tries again`() = runTest {
        settings.enabledFlow.value = true
        access.failWrites = 1
        history.exports.value = listOf(export(1))
        history.sessionIds.value = setOf(1L)
        startSync()
        assertTrue(access.written.isEmpty())
        assertTrue(settings.storedSyncedValues.isEmpty())

        history.exports.value = listOf(export(1), export(2))
        history.sessionIds.value = setOf(1L, 2L)
        settle()

        assertEquals(setOf(1L, 2L), allWritten.map { it.sessionId }.toSet())
        assertEquals(setOf(1L, 2L), settings.syncedFlow.value)
    }

    @Test
    fun `a failing delete is swallowed and the next emission tries again`() = runTest {
        settings.enabledFlow.value = true
        settings.syncedFlow.value = setOf(1L, 2L)
        access.failDeletes = 1
        history.exports.value = listOf(export(2))
        history.sessionIds.value = setOf(2L)
        startSync()
        assertTrue(access.deleted.isEmpty())

        history.exports.value = listOf(export(2, version = 2))
        settle()

        assertEquals(listOf(setOf(1L)), access.deleted)
        assertEquals(setOf(2L), settings.syncedFlow.value)
    }

    @Test
    fun `a failing availability check is swallowed and the next emission tries again`() = runTest {
        settings.enabledFlow.value = true
        access.availabilityError = IllegalStateException("health connect crashed")
        history.exports.value = listOf(export(1))
        history.sessionIds.value = setOf(1L)
        startSync()
        assertTrue(access.written.isEmpty())

        access.availabilityError = null
        history.exports.value = listOf(export(1, version = 2))
        settle()

        assertEquals(listOf(1L), allWritten.map { it.sessionId })
    }
}
