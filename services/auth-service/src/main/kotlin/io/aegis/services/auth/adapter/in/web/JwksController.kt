package io.aegis.services.auth.adapter.`in`.web

import io.aegis.services.auth.application.port.`in`.GetJwksUseCase
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@RestController
class JwksController(
    private val getJwksUseCase: GetJwksUseCase,
) {

    @GetMapping("/api/v1/auth/jwks")
    fun jwks(): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
            .body(getJwksUseCase.getPublicJwks())
}
