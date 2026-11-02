package com.boratreino.core.data

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock the test moves by hand; [zone] is the zone the repositories read dates in. */
internal class TestClock(var now: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = TestClock(now, zone)

    override fun instant(): Instant = now

    fun advance(by: Duration) {
        now = now.plus(by)
    }
}
