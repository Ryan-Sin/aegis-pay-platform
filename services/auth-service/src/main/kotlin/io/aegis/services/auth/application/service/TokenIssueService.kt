package io.aegis.services.auth.application.service

import com.nimbusds.jwt.JWTClaimsSet
import io.aegis.platform.core.context.TerminalContext
import io.aegis.platform.core.exception.BusinessException
import io.aegis.services.auth.application.exception.AuthErrorCode
import io.aegis.services.auth.application.exception.AuthErrorMessage
import io.aegis.services.auth.application.port.`in`.IssueTokenCommand
import io.aegis.services.auth.application.port.`in`.IssueTokenUseCase
import io.aegis.services.auth.application.port.out.ChallengeStorePort
import io.aegis.services.auth.application.port.out.JwtSignerPort
import io.aegis.services.auth.application.port.out.LoadTerminalPort
import io.aegis.services.auth.config.AuthTokenProperties
import io.aegis.services.auth.domain.auth.IssuedToken
import io.aegis.services.auth.domain.auth.TerminalChallenge
import io.aegis.services.auth.domain.terminal.Terminal
import io.aegis.services.auth.domain.terminal.TerminalPublicKeys
import io.aegis.services.auth.domain.terminal.TerminalSignatures
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID

@Service
class TokenIssueService(
    private val challengeStorePort: ChallengeStorePort,
    private val loadTerminalPort: LoadTerminalPort,
    private val jwtSignerPort: JwtSignerPort,
    private val properties: AuthTokenProperties,
    private val clock: Clock,
) : IssueTokenUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun issueToken(command: IssueTokenCommand): IssuedToken {
        consumeChallenge(command)
        val terminal = authenticate(command)

        val issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS)
        val expiresAt = issuedAt.plus(properties.ttl)
        val jti = UUID.randomUUID().toString()
        val claims = JWTClaimsSet.Builder()
            .issuer(properties.issuer)
            .audience(properties.audience)
            .subject(terminal.terminalId)
            .claim(TerminalContext.CLAIM_STORE_ID, terminal.storeId)
            .claim(TerminalContext.CLAIM_SCOPES, terminal.scopes.sorted())
            .jwtID(jti)
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt))
            .build()

        val accessToken = jwtSignerPort.sign(claims).serialize()
        log.info("Issued terminal token: terminalId={}, jti={}", terminal.terminalId, jti)
        return IssuedToken(accessToken = accessToken, issuedAt = issuedAt, expiresAt = expiresAt)
    }

    private fun consumeChallenge(command: IssueTokenCommand) {
        val challengedTerminalId = challengeStorePort.consume(command.nonce)
            ?: reject(AuthErrorCode.INVALID_CHALLENGE, AuthErrorMessage.REASON_CHALLENGE_NOT_FOUND, command)
        if (challengedTerminalId != command.terminalId) {
            reject(AuthErrorCode.INVALID_CHALLENGE, AuthErrorMessage.REASON_CHALLENGE_TERMINAL_MISMATCH, command)
        }
    }

    private fun authenticate(command: IssueTokenCommand): Terminal {
        val terminal = loadTerminalPort.findByTerminalId(command.terminalId)
            ?: reject(AuthErrorCode.TERMINAL_AUTHENTICATION_FAILED, AuthErrorMessage.REASON_TERMINAL_NOT_FOUND, command)
        if (!terminal.isActive) {
            reject(
                AuthErrorCode.TERMINAL_AUTHENTICATION_FAILED,
                "${AuthErrorMessage.REASON_TERMINAL_NOT_ACTIVE}(${terminal.status})",
                command,
            )
        }

        val publicKey = runCatching { TerminalPublicKeys.decode(terminal.keyAlgorithm, terminal.publicKey) }
            .getOrElse {
                log.error("Stored terminal public key is unreadable: terminalId={}", terminal.terminalId, it)
                reject(AuthErrorCode.TERMINAL_AUTHENTICATION_FAILED, AuthErrorMessage.REASON_INVALID_PUBLIC_KEY, command)
            }
        val message = TerminalChallenge.signingMessage(command.terminalId, command.nonce)
        if (!TerminalSignatures.verify(terminal.keyAlgorithm, publicKey, message, command.signature)) {
            reject(AuthErrorCode.TERMINAL_AUTHENTICATION_FAILED, AuthErrorMessage.REASON_INVALID_SIGNATURE, command)
        }
        return terminal
    }

    private fun reject(errorCode: AuthErrorCode, reason: String, command: IssueTokenCommand): Nothing {
        throw BusinessException(errorCode, detail = "$reason (terminalId=${command.terminalId})")
    }
}
