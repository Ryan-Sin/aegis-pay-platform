package io.aegis.services.auth.adapter.`in`.web

import io.aegis.platform.core.response.SuccessResponse
import io.aegis.services.auth.adapter.`in`.web.dto.ChallengeRequest
import io.aegis.services.auth.adapter.`in`.web.dto.ChallengeResponse
import io.aegis.services.auth.adapter.`in`.web.dto.TokenRequest
import io.aegis.services.auth.adapter.`in`.web.dto.TokenResponse
import io.aegis.services.auth.application.port.`in`.IssueChallengeUseCase
import io.aegis.services.auth.application.port.`in`.IssueTokenCommand
import io.aegis.services.auth.application.port.`in`.IssueTokenUseCase
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val issueChallengeUseCase: IssueChallengeUseCase,
    private val issueTokenUseCase: IssueTokenUseCase,
) {

    @PostMapping("/challenge")
    fun challenge(@Valid @RequestBody request: ChallengeRequest): SuccessResponse<ChallengeResponse> {
        val challenge = issueChallengeUseCase.issueChallenge(request.terminalId)
        return SuccessResponse.of(ChallengeResponse(nonce = challenge.nonce, expiresAt = challenge.expiresAt))
    }

    @PostMapping("/token")
    fun token(@Valid @RequestBody request: TokenRequest): SuccessResponse<TokenResponse> {
        val token = issueTokenUseCase.issueToken(
            IssueTokenCommand(terminalId = request.terminalId, nonce = request.nonce, signature = request.signature),
        )
        return SuccessResponse.of(
            TokenResponse(
                accessToken = token.accessToken,
                tokenType = TOKEN_TYPE,
                expiresIn = Duration.between(token.issuedAt, token.expiresAt).seconds,
                expiresAt = token.expiresAt,
            ),
        )
    }

    private companion object {
        const val TOKEN_TYPE = "Bearer"
    }
}
