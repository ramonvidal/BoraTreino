package com.boratreino.core.data

import com.boratreino.core.model.BodyMeasurement
import com.boratreino.core.model.MeasurementType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class DefaultMeasurementRepositoryTest {
    private lateinit var store: FakeWorkoutStore
    private lateinit var repo: MeasurementRepository

    private val oct1 = LocalDate.of(2026, 10, 1)
    private val oct20 = LocalDate.of(2026, 10, 20)
    private val nov1 = LocalDate.of(2026, 11, 1)
    private val nov10 = LocalDate.of(2026, 11, 10)

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        repo = DefaultMeasurementRepository(store)
    }

    // add

    @Test
    fun `add saves the value rounded to the nearest half centimetre`() = runTest {
        repo.add(nov1, MeasurementType.WAIST, 82.25)

        val saved = repo.observeAll().first().single()
        assertEquals(nov1, saved.date)
        assertEquals(MeasurementType.WAIST, saved.type)
        assertEquals(82.5, saved.valueCm, 0.0)
    }

    @Test
    fun `add keeps a value that is already a half step`() = runTest {
        repo.add(nov1, MeasurementType.HIPS, 95.5)

        assertEquals(95.5, repo.observeAll().first().single().valueCm, 0.0)
    }

    @Test
    fun `add raises a value below the minimum to the minimum`() = runTest {
        repo.add(nov1, MeasurementType.NECK, 3.0)

        assertEquals(10.0, repo.observeAll().first().single().valueCm, 0.0)
    }

    @Test
    fun `add lowers a value above the maximum to the maximum`() = runTest {
        repo.add(nov1, MeasurementType.THIGH, 999.0)

        assertEquals(300.0, repo.observeAll().first().single().valueCm, 0.0)
    }

    @Test
    fun `add saves the minimum for a value that is not a number`() = runTest {
        repo.add(nov1, MeasurementType.ARM, Double.NaN)

        assertEquals(10.0, repo.observeAll().first().single().valueCm, 0.0)
    }

    @Test
    fun `add returns the id of the row it saved`() = runTest {
        val id = repo.add(nov1, MeasurementType.WAIST, 82.5)

        assertEquals(id, repo.observeAll().first().single().id)
    }

    @Test
    fun `each add gets a new id`() = runTest {
        val first = repo.add(oct1, MeasurementType.WAIST, 84.0)
        val second = repo.add(oct1, MeasurementType.WAIST, 84.0)

        assertTrue(second != first)
        assertEquals(2, repo.observeAll().first().size)
    }

    // observeAll

    @Test
    fun `observeAll is empty when nothing was saved`() = runTest {
        assertEquals(emptyList<BodyMeasurement>(), repo.observeAll().first())
    }

    @Test
    fun `observeAll lists the newest date first`() = runTest {
        repo.add(oct1, MeasurementType.WAIST, 84.0)
        repo.add(nov10, MeasurementType.WAIST, 82.0)
        repo.add(oct20, MeasurementType.ARM, 33.0)

        assertEquals(
            listOf(nov10, oct20, oct1),
            repo.observeAll().first().map { it.date },
        )
    }

    @Test
    fun `on the same date the row saved last comes first`() = runTest {
        val earlier = repo.add(nov1, MeasurementType.WAIST, 82.0)
        val later = repo.add(nov1, MeasurementType.ARM, 33.0)

        assertEquals(listOf(later, earlier), repo.observeAll().first().map { it.id })
    }

    @Test
    fun `observeAll reflects adds and deletes that happen after it was read`() = runTest {
        assertTrue(repo.observeAll().first().isEmpty())

        val id = repo.add(nov1, MeasurementType.WAIST, 82.5)
        assertEquals(listOf(id), repo.observeAll().first().map { it.id })

        repo.delete(id)
        assertTrue(repo.observeAll().first().isEmpty())
    }

    // delete

    @Test
    fun `delete returns the row it removed and removes it`() = runTest {
        val id = repo.add(nov1, MeasurementType.WAIST, 82.5)

        val deleted = repo.delete(id)

        assertEquals(BodyMeasurement(id, nov1, MeasurementType.WAIST, 82.5), deleted)
        assertTrue(repo.observeAll().first().isEmpty())
    }

    @Test
    fun `delete of a missing id returns null and changes nothing`() = runTest {
        val keep = repo.add(nov1, MeasurementType.WAIST, 82.5)

        assertNull(repo.delete(keep + 100))

        assertEquals(listOf(keep), repo.observeAll().first().map { it.id })
    }

    @Test
    fun `a second delete of the same id returns null`() = runTest {
        val id = repo.add(nov1, MeasurementType.WAIST, 82.5)
        assertTrue(repo.delete(id) != null)

        assertNull(repo.delete(id))
    }

    @Test
    fun `delete leaves the other measurements in place`() = runTest {
        repo.add(oct1, MeasurementType.WAIST, 84.0)
        val middle = repo.add(oct20, MeasurementType.ARM, 33.0)
        repo.add(nov1, MeasurementType.NECK, 38.0)

        repo.delete(middle)

        assertEquals(listOf(nov1, oct1), repo.observeAll().first().map { it.date })
    }

    // restore and undo

    @Test
    fun `restore puts a deleted row back with the same id`() = runTest {
        val id = repo.add(nov1, MeasurementType.WAIST, 82.5)
        val deleted = repo.delete(id)!!

        repo.restore(deleted)

        assertEquals(listOf(deleted), repo.observeAll().first())
        assertEquals(id, repo.observeAll().first().single().id)
    }

    @Test
    fun `undo after a delete brings back the same list in the same order`() = runTest {
        repo.add(oct1, MeasurementType.WAIST, 84.0)
        val middle = repo.add(oct20, MeasurementType.ARM, 33.0)
        repo.add(nov1, MeasurementType.NECK, 38.0)
        val before = repo.observeAll().first()

        val deleted = repo.delete(middle)!!
        repo.restore(deleted)

        assertEquals(before, repo.observeAll().first())
    }

    @Test
    fun `a restored row can be deleted again by its id`() = runTest {
        repo.restore(BodyMeasurement(42, oct1, MeasurementType.CHEST, 100.0))

        assertEquals(BodyMeasurement(42, oct1, MeasurementType.CHEST, 100.0), repo.delete(42))
        assertNull(repo.delete(42))
    }

    @Test
    fun `an add after a restore does not reuse the restored id`() = runTest {
        val id = repo.add(nov1, MeasurementType.WAIST, 82.5)
        repo.restore(repo.delete(id)!!)

        val next = repo.add(nov10, MeasurementType.WAIST, 82.0)

        assertTrue(next != id)
        assertEquals(2, repo.observeAll().first().size)
    }
}
