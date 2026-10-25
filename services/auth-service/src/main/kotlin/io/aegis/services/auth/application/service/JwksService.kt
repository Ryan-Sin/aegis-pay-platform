package io.aegis.services.auth.application.service

import io.aegis.services.auth.application.port.`in`.GetJwksUseCase
import io.aegis.services.auth.application.port.out.JwtSignerPort
import org.springframework.stereotype.Service

@Service
class JwksService(
    private val jwtSignerPort: JwtSignerPort,
) : GetJwksUseCase {

    override fun getPublicJwks(): Map<String, Any> = jwtSignerPort.publicJwkSet().toJSONObject(true)
}
