package com.boratreino.core.model

import java.time.Duration
import java.time.Instant

/** Rules for when a session ends by itself. */
object SessionRules {
    val AUTO_CLOSE_AFTER: Duration = Duration.ofHours(3)

    /** True when nothing was logged for AUTO_CLOSE_AFTER: (lastSetAt ?: startedAt) + 3 h <= now. */
    fun isStale(session: Session, lastSetAt: Instant?, now: Instant): Boolean =
        !(lastSetAt ?: session.startedAt).plus(AUTO_CLOSE_AFTER).isAfter(now)

    /** When a stale session is closed: its last set's time, or startedAt when it has none. */
    fun closeTime(session: Session, lastSetAt: Instant?): Instant = lastSetAt ?: session.startedAt
}
