package io.aegis.services.gateway.support

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference

class MutableClock private constructor(
    private val current: AtomicReference<Instant>,
    private val zone: ZoneId,
) : Clock() {

    constructor(
        start: Instant = JwtTestFixtures.FIXED_NOW,
        zone: ZoneId = ZoneOffset.UTC,
    ) : this(AtomicReference(start), zone)

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock =
        if (zone == this.zone) this else MutableClock(current, zone)

    override fun instant(): Instant = current.get()

    fun advance(duration: Duration) {
        current.updateAndGet { it.plus(duration) }
    }
}