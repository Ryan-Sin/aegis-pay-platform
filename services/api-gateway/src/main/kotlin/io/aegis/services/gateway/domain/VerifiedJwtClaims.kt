package io.aegis.services.gateway.security

import io.aegis.platform.core.context.TerminalContext

data class VerifiedJwtClaims(
    val terminalContext: TerminalContext,
    val jti: String,
)
