package io.aegis.services.auth.application.service

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.aegis.platform.core.exception.BusinessException
import io.aegis.services.auth.application.exception.AuthErrorCode
import io.aegis.services.auth.application.port.`in`.IssueTokenCommand
import io.aegis.services.auth.application.port.out.ChallengeStorePort
import io.aegis.services.auth.application.port.out.JwtSignerPort
import io.aegis.services.auth.application.port.out.LoadTerminalPort
import io.aegis.services.auth.config.AuthTokenProperties
import io.aegis.services.auth.domain.terminal.Terminal
import io.aegis.services.auth.domain.terminal.TerminalStatus
import io.aegis.services.auth.support.TerminalFixtures
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenIssueServiceTest {

    private val keyPair = TerminalFixtures.ecKeyPair()
    private val clock = Clock.fixed(TerminalFixtures.NOW, ZoneOffset.UTC)
    private val properties = AuthTokenProperties(issuer = "aegis-auth-service", audience = "aegis-api")
    private val challengeStore = InMemoryChallengeStore()
    private val terminals = InMemoryTerminals()
    private val signer = LocalRsaSigner()
    private val service = TokenIssueService(challengeStore, terminals, signer, properties, clock)

    @Test
    @DisplayName("올바른 challenge 서명이면 gateway가 요구하는 클레임을 모두 담은 RS256 JWT를 발급한다.")
    fun issuesTokenWithExpectedClaims() {
        terminals.register(TerminalFixtures.terminal(keyPair = keyPair, scopes = setOf("payment:cancel", "payment:approve")))
        challengeStore.save("nonce-1", "TERM-001", Duration.ofSeconds(60))

        val issued = service.issueToken(command(nonce = "nonce-1"))

        val jwt = SignedJWT.parse(issued.accessToken)
        assertTrue(jwt.verify(RSASSAVerifier(signer.key.toRSAPublicKey())))
        val claims = jwt.jwtClaimsSet
        assertEquals("aegis-auth-service", claims.issuer)
        assertEquals(listOf("aegis-api"), claims.audience)
        assertEquals("TERM-001", claims.subject)
        assertEquals("STORE-001", claims.getStringClaim("storeId"))
        assertEquals(listOf("payment:approve", "payment:cancel"), claims.getStringListClaim("scopes"))
        assertTrue(claims.jwtid.isNotBlank())
        val issuedAtSeconds = TerminalFixtures.NOW.truncatedTo(ChronoUnit.SECONDS)
        assertEquals(issuedAtSeconds, claims.issueTime.toInstant())
        assertEquals(issuedAtSeconds.plus(Duration.ofHours(12)), claims.expirationTime.toInstant())
        assertEquals(claims.expirationTime.toInstant(), issued.expiresAt, "응답의 만료 시각은 토큰 exp와 정확히 같아야 한다")
    }

    @Test
    @DisplayName("같은 nonce로 두 번 요청하면 두 번째는 INVALID_CHALLENGE다(재사용 방지).")
    fun rejectsReusedNonce() {
        terminals.register(TerminalFixtures.terminal(keyPair = keyPair))
        challengeStore.save("nonce-1", "TERM-001", Duration.ofSeconds(60))
        service.issueToken(command(nonce = "nonce-1"))

        val ex = assertFailsWith<BusinessException> { service.issueToken(command(nonce = "nonce-1")) }

        assertEquals(AuthErrorCode.INVALID_CHALLENGE, ex.errorCode)
    }

    @Test
    @DisplayName("다른 단말에게 발급된 nonce는 쓸 수 없다.")
    fun rejectsNonceIssuedToOtherTerminal() {
        terminals.register(TerminalFixtures.terminal(keyPair = keyPair))
        challengeStore.save("nonce-1", "TERM-999", Duration.ofSeconds(60))

        val ex = assertFailsWith<BusinessException> { service.issueToken(command(nonce = "nonce-1")) }

        assertEquals(AuthErrorCode.INVALID_CHALLENGE, ex.errorCode)
    }

    @Test
    @DisplayName("서명이 틀려도 nonce는 소모된다(같은 nonce로 서명을 반복 시도할 수 없다).")
    fun consumesNonceEvenWhenSignatureFails() {
        terminals.register(TerminalFixtures.terminal(keyPair = keyPair))
        challengeStore.save("nonce-1", "TERM-001", Duration.ofSeconds(60))
        val wrongSignature = TerminalFixtures.signChallenge(TerminalFixtures.ecKeyPair().private, "TERM-001", "nonce-1")

        val ex = assertFailsWith<BusinessException> {
            service.issueToken(command(nonce = "nonce-1", signature = wrongSignature))
        }

        assertEquals(AuthErrorCode.TERMINAL_AUTHENTICATION_FAILED, ex.errorCode)
        assertNull(challengeStore.consume("nonce-1"))
    }

    @Test
    @DisplayName("미등록·정지·폐기 단말은 같은 코드(TERMINAL_AUTHENTICATION_FAILED)로 거절해서 단말 존재 여부를 드러내지 않는다.")
    fun rejectsUnknownOrInactiveTerminalWithSameCode() {
        val unknown = rejectCode()
        terminals.register(TerminalFixtures.terminal(keyPair = keyPair, status = TerminalStatus.SUSPENDED))
        val suspended = rejectCode()
        terminals.register(TerminalFixtures.terminal(keyPair = keyPair, status = TerminalStatus.REVOKED))
        val revoked = rejectCode()

        assertEquals(setOf(AuthErrorCode.TERMINAL_AUTHENTICATION_FAILED), setOf(unknown, suspended, revoked))
    }

    @Test
    @DisplayName("응답 문구에는 거절 사유(미등록, 정지 등)가 없고 detail에만 남는다.")
    fun keepsReasonOutOfClientMessage() {
        terminals.register(TerminalFixtures.terminal(keyPair = keyPair, status = TerminalStatus.SUSPENDED))
        challengeStore.save("nonce-1", "TERM-001", Duration.ofSeconds(60))

        val ex = assertFailsWith<BusinessException> { service.issueToken(command(nonce = "nonce-1")) }

        assertTrue(ex.detail.orEmpty().contains("SUSPENDED"))
        assertTrue(!ex.message.contains("SUSPENDED"))
    }

    private fun rejectCode(): AuthErrorCode {
        challengeStore.save("nonce-x", "TERM-001", Duration.ofSeconds(60))
        return assertFailsWith<BusinessException> { service.issueToken(command(nonce = "nonce-x")) }
            .errorCode as AuthErrorCode
    }

    private fun command(
        nonce: String,
        signature: String = TerminalFixtures.signChallenge(keyPair.private, "TERM-001", nonce),
    ) = IssueTokenCommand(terminalId = "TERM-001", nonce = nonce, signature = signature)

    private class InMemoryChallengeStore : ChallengeStorePort {
        private val nonces = mutableMapOf<String, String>()

        override fun save(nonce: String, terminalId: String, ttl: Duration) {
            nonces[nonce] = terminalId
        }

        override fun consume(nonce: String): String? = nonces.remove(nonce)
    }

    private class InMemoryTerminals : LoadTerminalPort {
        private val terminals = mutableMapOf<String, Terminal>()

        fun register(terminal: Terminal) {
            terminals[terminal.terminalId] = terminal
        }

        override fun findByTerminalId(terminalId: String): Terminal? = terminals[terminalId]
    }

    private class LocalRsaSigner : JwtSignerPort {
        val key: RSAKey = RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate()

        override fun sign(claims: JWTClaimsSet): SignedJWT =
            SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(), claims)
                .apply { sign(RSASSASigner(key)) }

        override fun publicJwkSet(): JWKSet = JWKSet(key.toPublicJWK())
    }
}
