package io.aegis.services.gateway.domain

import io.aegis.platform.core.context.TerminalContext

data class VerifiedJwtClaims(
    val terminalContext: TerminalContext,
    val jti: String,
)
