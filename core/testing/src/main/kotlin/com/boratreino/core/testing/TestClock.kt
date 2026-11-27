package com.boratreino.core.testing

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A clock the test moves by hand: set [now] or call [advance]. [zone] (UTC by default) is the zone the code under
 * test reads dates in. [withZone] gives a clock in another zone that still follows this one's [now].
 */
class TestClock(var now: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
    /** Starts at [start], an ISO-8601 instant such as "2026-11-02T12:00:00Z". */
    constructor(start: String, zone: ZoneId = ZoneOffset.UTC) : this(Instant.parse(start), zone)

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = if (zone == this.zone) this else ZonedView(this, zone)

    override fun instant(): Instant = now

    fun advance(by: Duration) {
        now = now.plus(by)
    }

    private class ZonedView(private val source: TestClock, private val zone: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = source.withZone(zone)
        override fun instant(): Instant = source.now
    }
}
