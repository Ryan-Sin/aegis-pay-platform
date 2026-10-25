package io.aegis.services.auth.adapter.out.kms

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.aegis.platform.core.exception.BusinessException
import io.aegis.services.auth.application.exception.AuthErrorCode
import io.aegis.services.auth.application.exception.AuthErrorMessage
import io.aegis.services.auth.application.port.out.JwtSignerPort
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.KeyUsageType
import software.amazon.awssdk.services.kms.model.MessageType
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec

class KmsJwtSigner(
    private val kmsClient: KmsClient,
    properties: JwtSigningProperties,
) : JwtSignerPort {
    private val signingKey: KmsPublicKey = loadPublicKey(properties.signingKey)

    private val publicJwkSet: JWKSet = JWKSet(
        (listOf(signingKey) + properties.verificationKeys.map(::loadPublicKey))
            .distinctBy { it.jwk.keyID }
            .map { it.jwk },
    )

    init {
        log.info(
            "Loaded JWT signing key from KMS: kid={}, publishedKids={}",
            signingKey.jwk.keyID,
            publicJwkSet.keys.map { it.keyID },
        )
    }

    override fun sign(claims: JWTClaimsSet): SignedJWT {
        val header = JWSHeader.Builder(JWSAlgorithm.RS256)
            .type(JOSEObjectType.JWT)
            .keyID(signingKey.jwk.keyID)
            .build()
        val unsigned = SignedJWT(header, claims)
        val signature = signDigest(sha256(unsigned.signingInput))

        return SignedJWT(
            unsigned.header.toBase64URL(),
            unsigned.payload.toBase64URL(),
            Base64URL.encode(signature),
        )
    }

    override fun publicJwkSet(): JWKSet = publicJwkSet

    private fun signDigest(digest: ByteArray): ByteArray =
        try {
            kmsClient.sign {
                it.keyId(signingKey.kmsKeyId)
                    .message(SdkBytes.fromByteArray(digest))
                    .messageType(MessageType.DIGEST)
                    .signingAlgorithm(SIGNING_ALGORITHM)
            }.signature().asByteArray()
        } catch (ex: SdkException) {
            throw BusinessException(
                AuthErrorCode.TOKEN_SIGNING_UNAVAILABLE,
                detail = "${AuthErrorMessage.REASON_KMS_SIGN_FAILED} (kid=${signingKey.jwk.keyID})",
                cause = ex,
            )
        }

    private fun loadPublicKey(keyRef: String): KmsPublicKey {
        val response = kmsClient.getPublicKey { it.keyId(keyRef) }

        check(response.keyUsage() == KeyUsageType.SIGN_VERIFY) {
            "KMS key is not a signing key: keyRef=$keyRef, keyUsage=${response.keyUsage()}"
        }
        check(SIGNING_ALGORITHM in response.signingAlgorithms()) {
            "KMS key does not support $SIGNING_ALGORITHM: keyRef=$keyRef, keySpec=${response.keySpec()}"
        }

        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(response.publicKey().asByteArray())) as RSAPublicKey
        val jwk = RSAKey.Builder(publicKey)
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .keyIDFromThumbprint()
            .build()

        return KmsPublicKey(kmsKeyId = response.keyId(), jwk = jwk)
    }

    private fun sha256(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)

    private class KmsPublicKey(
        val kmsKeyId: String,
        val jwk: RSAKey,
    )

    private companion object {
        val log = LoggerFactory.getLogger(KmsJwtSigner::class.java)
        val SIGNING_ALGORITHM: SigningAlgorithmSpec = SigningAlgorithmSpec.RSASSA_PKCS1_V1_5_SHA_256
    }
}
