package io.aegis.services.auth

import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.util.JSONObjectUtils
import com.nimbusds.jwt.SignedJWT
import io.aegis.platform.core.exception.CommonErrorCode
import io.aegis.platform.core.response.SuccessResponse
import io.aegis.services.auth.application.exception.AuthErrorCode
import io.aegis.services.auth.application.port.out.SaveTerminalPort
import io.aegis.services.auth.support.LocalStackKmsConfiguration
import io.aegis.services.auth.support.MySqlContainerConfiguration
import io.aegis.services.auth.support.TerminalFixtures
import io.aegis.services.auth.support.ValkeyContainerConfiguration
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyPair
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlContainerConfiguration::class, LocalStackKmsConfiguration::class, ValkeyContainerConfiguration::class)
class AuthServiceApplicationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var saveTerminalPort: SaveTerminalPort

    @Test
    @DisplayName("JWKS 엔드포인트는 서명 키의 공개키를 표준 JWK Set 형식(감싸지 않음)으로 내보낸다.")
    fun jwksEndpointPublishesSigningPublicKey() {
        val response = get("/api/v1/auth/jwks")

        assertEquals(200, response.statusCode())
        assertFalse(response.body().contains("\"d\""))
        assertEquals(1, JWKSet.parse(response.body()).keys.filterIsInstance<RSAKey>().size)
    }

    @Test
    @DisplayName("challenge 발급 → 단말 서명 → 토큰 발급까지 성공하고, 토큰은 JWKS 공개키로 검증되며 gateway 필수 클레임을 가진다.")
    fun issuesTokenThroughChallengeFlow() {
        val (terminalId, keyPair) = registerTerminal()

        val nonce = requestChallenge(terminalId)
        val response = post(
            "/api/v1/auth/token",
            tokenRequest(terminalId, nonce, TerminalFixtures.signChallenge(keyPair.private, terminalId, nonce)),
        )

        assertEquals(200, response.statusCode())
        val body = JSONObjectUtils.parse(response.body())
        assertEquals(SuccessResponse.SUCCESS_CODE, body["code"])
        val data = JSONObjectUtils.getJSONObject(body, "data")
        assertEquals("Bearer", data["tokenType"])
        assertEquals(43_200L, (data["expiresIn"] as Number).toLong())

        val jwt = SignedJWT.parse(data["accessToken"] as String)
        val publicKey = JWKSet.parse(get("/api/v1/auth/jwks").body()).getKeyByKeyId(jwt.header.keyID) as RSAKey
        assertTrue(jwt.verify(RSASSAVerifier(publicKey)))
        assertEquals(terminalId, jwt.jwtClaimsSet.subject)
        assertEquals("STORE-001", jwt.jwtClaimsSet.getStringClaim("storeId"))
        assertEquals("aegis-auth-service", jwt.jwtClaimsSet.issuer)
        assertEquals(listOf("aegis-api"), jwt.jwtClaimsSet.audience)
    }

    @Test
    @DisplayName("같은 요청을 다시 보내면(재전송 공격) 401 INVALID_CHALLENGE이고 응답에 내부 사유가 없다.")
    fun rejectsReplayedTokenRequest() {
        val (terminalId, keyPair) = registerTerminal()
        val nonce = requestChallenge(terminalId)
        val request = tokenRequest(terminalId, nonce, TerminalFixtures.signChallenge(keyPair.private, terminalId, nonce))
        assertEquals(200, post("/api/v1/auth/token", request).statusCode())

        val replay = post("/api/v1/auth/token", request)

        assertEquals(401, replay.statusCode())
        assertEquals(AuthErrorCode.INVALID_CHALLENGE.code, JSONObjectUtils.parse(replay.body())["code"])
        assertFalse(replay.body().contains("nonce 없음"))
    }

    @Test
    @DisplayName("잘못된 단말 ID 형식은 400, 깨진 JSON은 500이 아니라 400 BAD_REQUEST다.")
    fun rejectsInvalidRequests() {
        val invalidId = post("/api/v1/auth/challenge", """{"terminalId":"TERM 001;drop"}""")
        val brokenJson = post("/api/v1/auth/challenge", """{"terminalId":""")

        assertEquals(400, invalidId.statusCode())
        assertEquals(CommonErrorCode.INVALID_REQUEST_BODY.code, JSONObjectUtils.parse(invalidId.body())["code"])
        assertEquals(400, brokenJson.statusCode())
        assertEquals(CommonErrorCode.BAD_REQUEST.code, JSONObjectUtils.parse(brokenJson.body())["code"])
    }

    private fun registerTerminal(): Pair<String, KeyPair> {
        val terminalId = "TERM-${UUID.randomUUID().toString().take(8)}"
        val keyPair = TerminalFixtures.ecKeyPair()
        saveTerminalPort.save(TerminalFixtures.terminal(terminalId = terminalId, keyPair = keyPair))
        return terminalId to keyPair
    }

    private fun requestChallenge(terminalId: String): String {
        val response = post("/api/v1/auth/challenge", """{"terminalId":"$terminalId"}""")
        assertEquals(200, response.statusCode())
        return JSONObjectUtils.getJSONObject(JSONObjectUtils.parse(response.body()), "data")["nonce"] as String
    }

    private fun tokenRequest(terminalId: String, nonce: String, signature: String): String =
        """{"terminalId":"$terminalId","nonce":"$nonce","signature":"$signature"}"""

    private fun get(path: String): HttpResponse<String> =
        send(HttpRequest.newBuilder(uri(path)).GET().build())

    private fun post(path: String, json: String): HttpResponse<String> =
        send(
            HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build(),
        )

    private fun uri(path: String): URI = URI.create("http://localhost:$port$path")

    private fun send(request: HttpRequest): HttpResponse<String> =
        HttpClient.newHttpClient().use { it.send(request, HttpResponse.BodyHandlers.ofString()) }
}
