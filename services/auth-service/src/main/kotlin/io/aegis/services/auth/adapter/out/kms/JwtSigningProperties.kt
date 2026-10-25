package io.aegis.services.auth.adapter.out.kms

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "aegis.auth.signing")
data class JwtSigningProperties(
    val signingKey: String,
    val verificationKeys: List<String> = emptyList(),
)
