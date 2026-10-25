package io.aegis.services.auth.domain.terminal

import java.security.PublicKey
import java.security.Signature
import java.util.Base64

object TerminalSignatures {

    fun verify(
        algorithm: TerminalKeyAlgorithm,
        publicKey: PublicKey,
        message: ByteArray,
        signatureBase64: String,
    ): Boolean {
        val signature = runCatching { Base64.getDecoder().decode(signatureBase64) }.getOrNull() ?: return false
        return runCatching {
            Signature.getInstance(algorithm.jcaSignatureAlgorithm).run {
                initVerify(publicKey)
                update(message)
                verify(signature)
            }
        }.getOrDefault(false)
    }
}
