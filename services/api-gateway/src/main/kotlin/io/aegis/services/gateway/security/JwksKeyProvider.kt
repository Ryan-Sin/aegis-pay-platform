package io.aegis.services.gateway.security

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import io.aegis.platform.core.exception.BusinessException
import io.aegis.platform.httpclient.AsyncHttpClientFactory
import io.aegis.platform.httpclient.config.HttpClientSpec
import io.aegis.platform.httpclient.config.HttpTarget
import io.aegis.platform.httpclient.get
import io.aegis.services.gateway.config.GatewayAuthProperties
import io.aegis.services.gateway.exception.GatewayErrorCode
import io.aegis.services.gateway.exception.GatewayErrorMessage
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.util.concurrent.atomic.AtomicLong

@Component
class JwksKeyProvider(
    private val properties: GatewayAuthProperties,
    asyncHttpClientFactory: AsyncHttpClientFactory,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lastForcedRefreshAttemptMillis = AtomicLong(0L)

    private val authHttpClient = asyncHttpClientFactory.create(
        HttpClientSpec(
            targetName = AUTH_SERVICE_TARGET_NAME,
            baseUrl = properties.authServiceBaseUrl,
            target = HttpTarget.INTERNAL,
        ),
    )

    private val localPublicKeyCache: AsyncLoadingCache<String, Map<String, RSAPublicKey>> = Caffeine.newBuilder()
        .refreshAfterWrite(properties.jwksRefreshInterval)
        .expireAfterWrite(properties.jwksCacheTtl)
        .maximumSize(1)
        .buildAsync { _, _ ->
            refreshScope.future { fetchPublicKeys() }
        }

    suspend fun getPublicKey(kid: String): RSAPublicKey {
        val publicKeys = localPublicKeyCache[CACHE_KEY].await()
        publicKeys[kid]?.let { return it }

        if (tryAcquireRefreshSlot()) {
            val refreshedKeys = runCatching {
                localPublicKeyCache.synchronous().refresh(CACHE_KEY).await()
            }.getOrElse { ex ->
                if (ex is CancellationException) throw ex
                log.warn("Forced JWKS refresh failed, keeping stale keys: kid={}", kid, ex)
                null
            }
            refreshedKeys?.get(kid)?.let { return it }
        }

        throw BusinessException(
            GatewayErrorCode.INVALID_AUTH_TOKEN,
            detail = "${GatewayErrorMessage.REASON_PUBLIC_KEY_NOT_FOUND} (kid=$kid)",
        )
    }

    private fun tryAcquireRefreshSlot(): Boolean {
        val now = clock.millis()
        val last = lastForcedRefreshAttemptMillis.get()
        return now - last >= properties.jwksMinRefreshCooldown.toMillis() &&
            lastForcedRefreshAttemptMillis.compareAndSet(last, now)
    }

    private suspend fun fetchPublicKeys(): Map<String, RSAPublicKey> {
        return runCatching {
            val jwksJson = authHttpClient.get<String>(JWKS_URI)
            JWKSet.parse(jwksJson).keys
                .filterIsInstance<RSAKey>()
                .filter { !it.keyID.isNullOrBlank() }
                .associate { rsaKey -> rsaKey.keyID to rsaKey.toRSAPublicKey() }
        }.getOrElse { ex ->
            if (ex is CancellationException) throw ex
            log.error("Failed to fetch JWKS from auth-service", ex)
            throw BusinessException(GatewayErrorCode.JWKS_FETCH_FAILED)
        }
    }

    @PreDestroy
    fun close() {
        refreshScope.cancel()
    }

    companion object {
        const val JWKS_URI = "/.well-known/jwks.json"
        private const val AUTH_SERVICE_TARGET_NAME = "auth-service"
        private const val CACHE_KEY = "JWKS"
    }
}
