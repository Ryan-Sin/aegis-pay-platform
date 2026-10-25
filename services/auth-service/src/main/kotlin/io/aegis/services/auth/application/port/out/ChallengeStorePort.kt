package io.aegis.services.auth.application.port.out

import java.time.Duration

interface ChallengeStorePort {
    fun save(nonce: String, terminalId: String, ttl: Duration)

    fun consume(nonce: String): String?
}
