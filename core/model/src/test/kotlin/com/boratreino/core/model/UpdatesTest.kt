package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class UpdatesTest {
    private val update = AppUpdate(
        buildNumber = 41,
        versionName = "0.41",
        downloadUrl = "https://github.com/ramonvidal/BoraTreino/releases/download/v0.41/BoraTreino.apk",
        sizeBytes = 12_345_678L,
    )
    private val now = Instant.parse("2026-11-03T10:00:00Z")

    @Test
    fun `checks are every six hours`() {
        assertEquals(Duration.ofHours(6), Updates.CHECK_EVERY)
    }

    @Test
    fun `release tags give the build number`() {
        assertEquals(41, Updates.buildNumber("v0.41"))
        assertEquals(5, Updates.buildNumber("v0.5"))
        assertEquals(123, Updates.buildNumber("v0.123"))
    }

    @Test
    fun `whitespace around the tag is ignored`() {
        assertEquals(41, Updates.buildNumber("  v0.41 "))
        assertEquals(41, Updates.buildNumber("v0.41\n"))
    }

    @Test
    fun `tags in another shape give no build number`() {
        assertNull(Updates.buildNumber("v1.0"))
        assertNull(Updates.buildNumber("0.41"))
        assertNull(Updates.buildNumber("v0.41-beta"))
        assertNull(Updates.buildNumber(""))
        assertNull(Updates.buildNumber("v0.41.1"))
        assertNull(Updates.buildNumber("latest"))
    }

    @Test
    fun `only a higher build number is newer`() {
        assertTrue(Updates.isNewer(update, installedBuild = 40L))
        assertTrue(Updates.isNewer(update, installedBuild = 1L))
        assertFalse(Updates.isNewer(update, installedBuild = 41L))
        assertFalse(Updates.isNewer(update, installedBuild = 42L))
    }

    @Test
    fun `a check is due when there has never been one`() {
        assertTrue(Updates.isCheckDue(null, now))
    }

    @Test
    fun `a check is not due just under six hours after the last`() {
        assertFalse(Updates.isCheckDue(now, now))
        assertFalse(Updates.isCheckDue(now.minus(Duration.ofMinutes(359)), now))
        assertFalse(Updates.isCheckDue(now.minus(Duration.ofHours(6)).plusSeconds(1), now))
    }

    @Test
    fun `a check is due six hours or more after the last`() {
        assertTrue(Updates.isCheckDue(now.minus(Duration.ofHours(6)), now))
        assertTrue(Updates.isCheckDue(now.minus(Duration.ofHours(25)), now))
    }

    @Test
    fun `a newer build is offered`() {
        assertTrue(Updates.shouldOffer(update, installedBuild = 40L, dismissedBuild = null))
    }

    @Test
    fun `the installed build or an older one is never offered`() {
        assertFalse(Updates.shouldOffer(update, installedBuild = 41L, dismissedBuild = null))
        assertFalse(Updates.shouldOffer(update, installedBuild = 42L, dismissedBuild = null))
        assertFalse(Updates.shouldOffer(update, installedBuild = 42L, dismissedBuild = 41))
    }

    @Test
    fun `a dismissed build is not offered again`() {
        assertFalse(Updates.shouldOffer(update, installedBuild = 40L, dismissedBuild = 41))
    }

    @Test
    fun `a build newer than the dismissed one is offered`() {
        val next = update.copy(buildNumber = 42, versionName = "0.42")
        assertTrue(Updates.shouldOffer(next, installedBuild = 40L, dismissedBuild = 41))
    }
}
