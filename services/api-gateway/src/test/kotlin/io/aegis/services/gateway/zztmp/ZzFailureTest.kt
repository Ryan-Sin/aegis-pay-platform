package io.aegis.services.gateway.zztmp

import io.aegis.platform.core.exception.BusinessException
import io.aegis.platform.httpclient.exception.HttpClientErrorCode
import io.aegis.services.gateway.config.GatewayAuthProperties
import io.aegis.services.gateway.exception.GatewayErrorCode
import io.aegis.services.gateway.security.JwksKeyProvider
import io.aegis.services.gateway.support.FakeJwksAsyncHttpClient
import io.aegis.services.gateway.support.MutableClock
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ZzFailureTest {
    companion object {
        @JvmStatic fun failures() = listOf(
            BusinessException(HttpClientErrorCode.EXTERNAL_API_TIMEOUT, "auth-service", "/.well-known/jwks.json"),
            BusinessException(HttpClientErrorCode.EXTERNAL_API_CIRCUIT_OPEN, "auth-service"),
            BusinessException(HttpClientErrorCode.EXTERNAL_API_SERVER_ERROR, "auth-service", 503, "UNKNOWN", "Service Unavailable"),
        )
    }
    @ParameterizedTest @MethodSource("failures")
    fun initialLoad(failure: BusinessException) = runBlocking<Unit> {
        val http = FakeJwksAsyncHttpClient().apply { willThrow(failure) }
        val p = JwksKeyProvider(GatewayAuthProperties(), http.asFactory(), MutableClock())
        try {
            val ex = assertFailsWith<BusinessException> { p.getPublicKey("k1") }
            assertEquals(GatewayErrorCode.JWKS_FETCH_FAILED, ex.errorCode)
            println("ZZ ${failure.errorCode} -> ${ex.errorCode}, cause=${ex.cause}")
        } finally { p.close() }
    }
    @org.junit.jupiter.api.Test fun malformedJwks() = runBlocking<Unit> {
        val http = FakeJwksAsyncHttpClient().apply { willReturn("<html>502 Bad Gateway</html>") }
        val p = JwksKeyProvider(GatewayAuthProperties(), http.asFactory(), MutableClock())
        try {
            assertEquals(GatewayErrorCode.JWKS_FETCH_FAILED, assertFailsWith<BusinessException> { p.getPublicKey("k1") }.errorCode)
            println("ZZ html -> JWKS_FETCH_FAILED")
        } finally { p.close() }
    }
}
