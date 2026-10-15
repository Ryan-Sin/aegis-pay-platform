package io.aegis.platform.lock

import java.util.concurrent.TimeUnit

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DistributedLock (
    val key: String,
    val prefix: String = "aegis:lock:",
    val waitTime: Long = 3L,
    val leaseTime: Long = 5L,
    val timeUnit: TimeUnit = TimeUnit.SECONDS,
)