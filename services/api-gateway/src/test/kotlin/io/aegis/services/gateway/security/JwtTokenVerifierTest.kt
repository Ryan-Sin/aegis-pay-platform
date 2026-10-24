package io.aegis.services.gateway.security

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.PlainHeader
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import io.aegis.platform.core.context.TerminalContext
import io.aegis.platform.core.exception.BusinessException
import io.aegis.services.gateway.config.GatewayAuthProperties
import io.aegis.services.gateway.exception.GatewayErrorCode
import io.aegis.services.gateway.exception.GatewayErrorMessage
import io.aegis.services.gateway.support.FakeJwksAsyncHttpClient
import io.aegis.services.gateway.support.JwtTestFixtures.FIXED_NOW
import io.aegis.services.gateway.support.JwtTestFixtures.KEY_1
import io.aegis.services.gateway.support.JwtTestFixtures.createHs256Token
import io.aegis.services.gateway.support.JwtTestFixtures.createRs256Token
import io.aegis.services.gateway.support.JwtTestFixtures.generateRsaKey
import io.aegis.services.gateway.support.JwtTestFixtures.toJwksJson
import io.aegis.services.gateway.support.MutableClock
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Duration
import java.util.Base64
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JwtTokenVerifierTest {

    private val clock = MutableClock()
    private val http = FakeJwksAsyncHttpClient().apply { willReturn(toJwksJson(KEY_1)) }
    private val jwksKeyProvider = JwksKeyProvider(GatewayAuthProperties(), http.asFactory(), clock)
    private val verifier = JwtTokenVerifier(jwksKeyProvider, clock)

    @AfterEach
    fun tearDown() {
        jwksKeyProvider.close()
    }

    @Test
    @DisplayName("정상 토큰이면 sub, storeId, jti, scopes를 단말 컨텍스트로 변환한다.")
    fun extractsTerminalContextFromValidToken() = runBlocking {
        val claims = verifier.verify(createRs256Token(KEY_1))

        assertEquals("TERM-001", claims.terminalContext.terminalId)
        assertEquals("STORE-001", claims.terminalContext.storeId)
        assertEquals(setOf("payment:write", "payment:read"), claims.terminalContext.scopes)
        assertEquals("jti-001", claims.jti)
    }

    @Test
    @DisplayName("scopes가 문자열이면 쉼표와 공백 기준으로 나누고 빈 값은 버린다.")
    fun splitsStringScopes() = runBlocking {
        val claims = verifier.verify(createRs256Token(KEY_1, scopes = "payment:write, payment:read  refund"))

        assertEquals(setOf("payment:write", "payment:read", "refund"), claims.terminalContext.scopes)
    }

    @Test
    @DisplayName("scopes 클레임이 없으면 빈 집합이다.")
    fun returnsEmptyScopesWhenAbsent() = runBlocking {
        val claims = verifier.verify(createRs256Token(KEY_1, scopes = null))

        assertTrue(claims.terminalContext.scopes.isEmpty())
    }

    @Test
    @DisplayName("exp가 지났어도 30초 leeway 안이면 통과하고, 넘으면 EXPIRED_AUTH_TOKEN이다.")
    fun appliesClockSkewLeewayAtBoundary() = runBlocking {
        val exp = FIXED_NOW.plus(Duration.ofMinutes(10))
        val token = createRs256Token(KEY_1, expirationTime = exp)

        // exp + 30초: leeway 경계 → 통과
        clock.advance(Duration.ofMinutes(10).plusSeconds(30))
        verifier.verify(token)

        // exp + 31초: 만료
        clock.advance(Duration.ofSeconds(1))
        val ex = assertFailsWith<BusinessException> { verifier.verify(token) }
        assertEquals(GatewayErrorCode.EXPIRED_AUTH_TOKEN, ex.errorCode)
    }

    @Test
    @DisplayName("exp 클레임이 없는 토큰은 만료 토큰으로 거부한다.")
    fun rejectsTokenWithoutExp() = runBlocking {
        val ex = assertFailsWith<BusinessException> {
            verifier.verify(createRs256Token(KEY_1, expirationTime = null))
        }

        assertEquals(GatewayErrorCode.EXPIRED_AUTH_TOKEN, ex.errorCode)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "not-a-jwt", "a.b.c"])
    @DisplayName("JWT 형식이 아니면 INVALID_AUTH_TOKEN이고 JWKS를 조회하지 않는다.")
    fun rejectsMalformedToken(token: String) = runBlocking {
        assertInvalid(GatewayErrorMessage.REASON_JWT_PARSE_FAILED) { verifier.verify(token) }
        assertEquals(0, http.fetchCount)
    }

    @Test
    @DisplayName("HS256 토큰(alg confusion)은 JWKS 조회 전에 거부한다.")
    fun rejectsHs256BeforeKeyLookup() = runBlocking {
        assertInvalid(GatewayErrorMessage.REASON_UNSUPPORTED_ALGORITHM) { verifier.verify(createHs256Token()) }
        assertEquals(0, http.fetchCount)
    }

    @Test
    @DisplayName("alg=none 토큰은 서명 토큰으로 파싱되지 않아 거부한다.")
    fun rejectsUnsecuredToken() = runBlocking {
        val claims = JWTClaimsSet.Builder()
            .subject("TERM-001")
            .claim(TerminalContext.CLAIM_STORE_ID, "STORE-001")
            .jwtID("jti-001")
            .expirationTime(Date.from(FIXED_NOW.plus(Duration.ofMinutes(10))))
            .build()
        val unsecuredToken = PlainJWT(PlainHeader.Builder().type(JOSEObjectType.JWT).build(), claims).serialize()

        assertInvalid(GatewayErrorMessage.REASON_JWT_PARSE_FAILED) { verifier.verify(unsecuredToken) }
        assertEquals(0, http.fetchCount)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   "])
    @DisplayName("kid 헤더가 비어 있으면 거부한다.")
    fun rejectsBlankKid(kid: String) = runBlocking {
        assertInvalid(GatewayErrorMessage.REASON_MISSING_KID_HEADER) {
            verifier.verify(createRs256Token(KEY_1, kid = kid))
        }
    }

    @Test
    @DisplayName("kid 헤더가 없으면 거부한다.")
    fun rejectsMissingKid() = runBlocking {
        assertInvalid(GatewayErrorMessage.REASON_MISSING_KID_HEADER) {
            verifier.verify(createRs256Token(KEY_1, kid = null))
        }
    }

    @Test
    @DisplayName("게시된 kid를 사칭해 다른 키로 서명한 토큰은 서명 불일치로 거부한다.")
    fun rejectsTokenSignedByAttackerKeyWithKnownKid() = runBlocking {
        val attackerKey = generateRsaKey(kid = KEY_1.keyID)

        assertInvalid(GatewayErrorMessage.REASON_INVALID_SIGNATURE) {
            verifier.verify(createRs256Token(attackerKey))
        }
    }

    @Test
    @DisplayName("서명 후 payload를 바꾼 토큰은 서명 불일치로 거부한다.")
    fun rejectsTamperedPayload() = runBlocking {
        val (header, _, signature) = createRs256Token(KEY_1).split(".")
        val forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"sub":"TERM-999","storeId":"STORE-999","jti":"jti-001","exp":${FIXED_NOW.epochSecond + 600}}""".toByteArray(),
        )

        assertInvalid(GatewayErrorMessage.REASON_INVALID_SIGNATURE) {
            verifier.verify("$header.$forgedPayload.$signature")
        }
    }

    @Test
    @DisplayName("sub가 없거나 공백이면 거부한다.")
    fun rejectsMissingOrBlankSubject() = runBlocking {
        assertInvalid(GatewayErrorMessage.REASON_MISSING_SUB_CLAIM) { verifier.verify(createRs256Token(KEY_1, subject = null)) }
        assertInvalid(GatewayErrorMessage.REASON_MISSING_SUB_CLAIM) { verifier.verify(createRs256Token(KEY_1, subject = " ")) }
    }

    @Test
    @DisplayName("storeId가 없거나 공백이면 거부한다.")
    fun rejectsInvalidStoreId() = runBlocking {
        assertInvalid(GatewayErrorMessage.REASON_MISSING_STORE_ID_CLAIM) { verifier.verify(createRs256Token(KEY_1, storeId = null)) }
        assertInvalid(GatewayErrorMessage.REASON_MISSING_STORE_ID_CLAIM) { verifier.verify(createRs256Token(KEY_1, storeId = " ")) }
    }

    @Test
    @DisplayName("jti가 없거나 공백이면 거부한다(블랙리스트 조회 키가 없으므로).")
    fun rejectsMissingOrBlankJti() = runBlocking {
        assertInvalid(GatewayErrorMessage.REASON_MISSING_JTI_CLAIM) { verifier.verify(createRs256Token(KEY_1, jti = null)) }
        assertInvalid(GatewayErrorMessage.REASON_MISSING_JTI_CLAIM) { verifier.verify(createRs256Token(KEY_1, jti = " ")) }
    }

    @Test
    @DisplayName("거부 원인은 detail에만 담기고 클라이언트 message에는 섞이지 않는다(ADR-0001).")
    fun keepsReasonOutOfClientMessage() = runBlocking {
        val ex = assertFailsWith<BusinessException> { verifier.verify(createHs256Token()) }

        assertEquals(GatewayErrorMessage.REASON_UNSUPPORTED_ALGORITHM, ex.detail)
        assertFalse(ex.message.contains(GatewayErrorMessage.REASON_UNSUPPORTED_ALGORITHM))
    }

    private suspend fun assertInvalid(expectedReason: String, block: suspend () -> Unit) {
        val ex = runCatching { block() }.exceptionOrNull()
        assertTrue(ex is BusinessException, "BusinessException이 발생해야 합니다: $ex")
        assertEquals(GatewayErrorCode.INVALID_AUTH_TOKEN, ex.errorCode)
        assertEquals(expectedReason, ex.detail)
    }
}
