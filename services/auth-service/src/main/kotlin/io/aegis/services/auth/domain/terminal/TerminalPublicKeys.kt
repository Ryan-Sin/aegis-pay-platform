package io.aegis.services.auth.domain.terminal

import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object TerminalPublicKeys {
    fun decode(algorithm: TerminalKeyAlgorithm, encoded: String): PublicKey {
        val der = runCatching { Base64.getDecoder().decode(encoded) }
            .getOrElse { throw IllegalArgumentException("Terminal public key is not valid base64", it) }

        val publicKey = runCatching {
            KeyFactory.getInstance(algorithm.jcaKeyAlgorithm).generatePublic(X509EncodedKeySpec(der))
        }.getOrElse { throw IllegalArgumentException("Terminal public key is not a valid $algorithm key", it) }

        require(publicKey is ECPublicKey && publicKey.params.isSameCurve(curveOf(algorithm))) {
            "Terminal public key curve does not match $algorithm"
        }
        return publicKey
    }

    fun encode(publicKey: PublicKey): String = Base64.getEncoder().encodeToString(publicKey.encoded)

    private fun curveOf(algorithm: TerminalKeyAlgorithm): ECParameterSpec =
        AlgorithmParameters.getInstance("EC")
            .apply { init(ECGenParameterSpec(algorithm.curveName)) }
            .getParameterSpec(ECParameterSpec::class.java)

    private fun ECParameterSpec.isSameCurve(other: ECParameterSpec): Boolean =
        curve == other.curve && generator == other.generator && order == other.order && cofactor == other.cofactor
}
