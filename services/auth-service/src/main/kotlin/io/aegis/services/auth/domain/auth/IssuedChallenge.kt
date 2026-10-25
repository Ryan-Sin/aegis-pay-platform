package io.aegis.services.auth.domain.auth

import java.time.Instant

data class IssuedChallenge(
    val nonce: String,
    val expiresAt: Instant,
)
