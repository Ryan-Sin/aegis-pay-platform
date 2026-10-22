package io.aegis.services.gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "aegis.gateway.auth")
data class GatewayAuthProperties(
    val authServiceBaseUrl: String = "http://localhost:8081",
    val jwksCacheTtl: Duration = Duration.ofHours(6),
    val jwksRefreshInterval: Duration = Duration.ofMinutes(30),
    val jwksMinRefreshCooldown: Duration = Duration.ofSeconds(30),
    val blacklistTimeout: Duration = Duration.ofMillis(200),
    val permitAllPaths: List<String> = listOf(
        "/api/v1/auth/token",
        "/.well-known/jwks.json",
    ),
)
