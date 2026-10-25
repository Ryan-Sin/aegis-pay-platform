package io.aegis.services.auth.application.port.`in`

interface GetJwksUseCase {
    fun getPublicJwks(): Map<String, Any>
}
