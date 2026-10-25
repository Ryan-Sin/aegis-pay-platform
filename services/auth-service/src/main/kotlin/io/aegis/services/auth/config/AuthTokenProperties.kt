package io.aegis.services.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "aegis.auth.token")
data class AuthTokenProperties(
    val issuer: String,
    val audience: String,
    val ttl: Duration = Duration.ofHours(12),
    val challengeTtl: Duration = Duration.ofSeconds(60),
)
