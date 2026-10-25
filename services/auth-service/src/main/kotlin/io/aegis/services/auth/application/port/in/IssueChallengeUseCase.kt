package io.aegis.services.auth.application.port.`in`

import io.aegis.services.auth.domain.auth.IssuedChallenge

interface IssueChallengeUseCase {
    fun issueChallenge(terminalId: String): IssuedChallenge
}
