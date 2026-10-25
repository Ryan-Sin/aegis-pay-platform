package io.aegis.services.gateway.adapter.out.jwks

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component

@Component
class JwksHealthIndicator(
    private val jwksKeyProvider: JwksKeyProvider,
) : HealthIndicator {

    override fun health(): Health =
        if (jwksKeyProvider.isKeysLoaded) Health.up().build() else Health.down().build()
}
