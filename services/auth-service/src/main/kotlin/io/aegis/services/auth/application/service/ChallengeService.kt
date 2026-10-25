package io.aegis.services.auth.application.service

import io.aegis.services.auth.application.port.`in`.IssueChallengeUseCase
import io.aegis.services.auth.application.port.out.ChallengeStorePort
import io.aegis.services.auth.config.AuthTokenProperties
import io.aegis.services.auth.domain.auth.IssuedChallenge
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64

@Service
class ChallengeService(
    private val challengeStorePort: ChallengeStorePort,
    private val properties: AuthTokenProperties,
    private val clock: Clock,
) : IssueChallengeUseCase {

    private val secureRandom = SecureRandom()

    override fun issueChallenge(terminalId: String): IssuedChallenge {
        val nonce = newNonce()
        challengeStorePort.save(nonce, terminalId, properties.challengeTtl)
        return IssuedChallenge(
            nonce = nonce,
            expiresAt = clock.instant().plus(properties.challengeTtl),
        )
    }

    private fun newNonce(): String {
        val bytes = ByteArray(NONCE_BYTES).also(secureRandom::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        const val NONCE_BYTES = 32
    }
}
