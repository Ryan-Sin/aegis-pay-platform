package io.aegis.services.auth.adapter.`in`.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant

data class ChallengeRequest(
    @field:NotBlank
    @field:Pattern(regexp = TERMINAL_ID_PATTERN)
    val terminalId: String,
)

data class ChallengeResponse(
    val nonce: String,
    val expiresAt: Instant,
)

data class TokenRequest(
    @field:NotBlank
    @field:Pattern(regexp = TERMINAL_ID_PATTERN)
    val terminalId: String,

    @field:NotBlank
    @field:Size(max = 64)
    val nonce: String,

    @field:NotBlank
    @field:Size(max = 256)
    val signature: String,
)

data class TokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val expiresAt: Instant,
)

private const val TERMINAL_ID_PATTERN = "[A-Za-z0-9_-]{1,64}"
