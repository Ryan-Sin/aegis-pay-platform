package io.aegis.services.auth.application.service

import io.aegis.services.auth.application.port.out.ChallengeStorePort
import io.aegis.services.auth.config.AuthTokenProperties
import io.aegis.services.auth.support.TerminalFixtures
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ChallengeServiceTest {

    private val saved = mutableListOf<Triple<String, String, Duration>>()
    private val store = object : ChallengeStorePort {
        override fun save(nonce: String, terminalId: String, ttl: Duration) {
            saved += Triple(nonce, terminalId, ttl)
        }

        override fun consume(nonce: String): String? = null
    }
    private val service = ChallengeService(
        store,
        AuthTokenProperties(issuer = "iss", audience = "aud", challengeTtl = Duration.ofSeconds(60)),
        Clock.fixed(TerminalFixtures.NOW, ZoneOffset.UTC),
    )

    @Test
    @DisplayName("nonce는 32바이트 난수(base64url)이고 요청한 단말 ID와 TTL로 저장된다.")
    fun storesRandomNonceWithTerminalAndTtl() {
        val challenge = service.issueChallenge("TERM-001")

        assertEquals(43, challenge.nonce.length)
        assertTrue(challenge.nonce.matches(Regex("[A-Za-z0-9_-]+")))
        assertEquals(Triple(challenge.nonce, "TERM-001", Duration.ofSeconds(60)), saved.single())
        assertEquals(TerminalFixtures.NOW.plusSeconds(60), challenge.expiresAt)
    }

    @Test
    @DisplayName("요청마다 다른 nonce가 나온다.")
    fun issuesDifferentNonceEachTime() {
        assertNotEquals(service.issueChallenge("TERM-001").nonce, service.issueChallenge("TERM-001").nonce)
    }
}
