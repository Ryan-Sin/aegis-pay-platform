package io.aegis.services.auth.application.port.`in`

import io.aegis.services.auth.domain.auth.IssuedToken

interface IssueTokenUseCase {
    fun issueToken(command: IssueTokenCommand): IssuedToken
}

data class IssueTokenCommand(
    val terminalId: String,
    val nonce: String,
    val signature: String,
)
