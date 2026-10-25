package io.aegis.services.auth.domain.auth

import java.nio.charset.StandardCharsets

object TerminalChallenge {

    const val PROTOCOL_VERSION = "aegis-auth-v1"

    fun signingMessage(terminalId: String, nonce: String): ByteArray =
        "$PROTOCOL_VERSION\n$terminalId\n$nonce".toByteArray(StandardCharsets.UTF_8)
}
