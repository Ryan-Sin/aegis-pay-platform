package io.aegis.services.auth.domain.auth

import java.time.Instant

data class IssuedToken(
    val accessToken: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
)
