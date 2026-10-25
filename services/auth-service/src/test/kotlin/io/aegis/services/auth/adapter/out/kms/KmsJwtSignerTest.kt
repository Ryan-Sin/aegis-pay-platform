package io.aegis.services.auth.adapter.out.kms

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.aegis.platform.core.exception.BusinessException
import io.aegis.services.auth.application.exception.AuthErrorCode
import io.aegis.services.auth.support.LocalStackKms
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import software.amazon.awssdk.services.kms.model.KeySpec
import software.amazon.awssdk.services.kms.model.KeyUsageType
import software.amazon.awssdk.services.kms.model.NotFoundException
import java.security.Signature
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KmsJwtSignerTest {

    private val kmsClient = LocalStackKms.client()

    @AfterAll
    fun tearDown() {
        kmsClient.close()
    }

    @Test
    @DisplayName("KMS로 서명한 JWT는 JWKS에 게시한 공개키로 검증된다.")
    fun signedJwtVerifiesWithPublishedKey() {
        val signer = signer()

        val jwt = signer.sign(claims())

        val publishedKey = signer.publicJwkSet().getKeyByKeyId(jwt.header.keyID) as RSAKey
        assertTrue(SignedJWT.parse(jwt.serialize()).verify(RSASSAVerifier(publishedKey)))
        assertEquals("TERM-001", SignedJWT.parse(jwt.serialize()).jwtClaimsSet.subject)
    }

    @Test
    @DisplayName("DIGEST 모드 서명은 표준 RS256(SHA256withRSA)과 같아서 JCA로도 검증된다.")
    fun digestSignatureIsStandardRs256() {
        val signer = signer()

        val jwt = signer.sign(claims())

        val publicKey = (signer.publicJwkSet().keys.single() as RSAKey).toRSAPublicKey()
        val verified = Signature.getInstance("SHA256withRSA").run {
            initVerify(publicKey)
            update(jwt.signingInput)
            verify(jwt.signature.decode())
        }
        assertTrue(verified)
    }

    @Test
    @DisplayName("헤더는 RS256, typ=JWT이고 kid는 공개키의 JWK thumbprint다(KMS 키 ID를 노출하지 않는다).")
    fun headerUsesThumbprintKid() {
        val signer = signer()

        val header = signer.sign(claims()).header

        val publishedKey = signer.publicJwkSet().keys.single() as RSAKey
        assertEquals(JWSAlgorithm.RS256, header.algorithm)
        assertEquals(JOSEObjectType.JWT, header.type)
        assertEquals(publishedKey.computeThumbprint().toString(), header.keyID)
        assertFalse(header.keyID.contains(LocalStackKms.SIGNING_KEY_ID))
    }

    @Test
    @DisplayName("JWKS에는 공개키만 있고 용도(sig)와 알고리즘(RS256)이 표시된다.")
    fun publishesOnlyPublicKeyMaterial() {
        val jwks = signer().publicJwkSet().toJSONObject(true)

        @Suppress("UNCHECKED_CAST")
        val key = (jwks["keys"] as List<Map<String, Any>>).single()
        assertEquals("RSA", key["kty"])
        assertEquals(KeyUse.SIGNATURE.identifier(), key["use"])
        assertEquals(JWSAlgorithm.RS256.name, key["alg"])
        assertFalse(key.containsKey("d"))
    }

    @Test
    @DisplayName("교체 기간에는 서명 키와 검증 유지 키를 함께 게시하고, 서명은 서명 키로만 한다.")
    fun publishesVerificationKeysAlongsideSigningKey() {
        val nextKeyId = LocalStackKms.createKey(kmsClient)
        val signer = signer(verificationKeys = listOf(LocalStackKms.SIGNING_KEY_ALIAS, nextKeyId))

        val kids = signer.publicJwkSet().keys.map { it.keyID }
        val signingKid = signer.sign(claims()).header.keyID

        assertEquals(2, kids.size, "같은 키를 두 번 지정해도 한 번만 게시한다")
        assertEquals(signer().publicJwkSet().keys.single().keyID, signingKid)
        assertTrue(signingKid in kids)
    }

    @Test
    @DisplayName("서명 키가 KMS에 없으면 생성(기동)에 실패한다.")
    fun failsFastWhenKeyIsMissing() {
        assertFailsWith<NotFoundException> {
            KmsJwtSigner(kmsClient, JwtSigningProperties(signingKey = "alias/does-not-exist"))
        }
    }

    @Test
    @DisplayName("서명용이 아닌 키(ENCRYPT_DECRYPT)는 받지 않는다.")
    fun rejectsNonSigningKey() {
        val encryptionKeyId = LocalStackKms.createKey(
            kmsClient,
            keySpec = KeySpec.RSA_2048,
            keyUsage = KeyUsageType.ENCRYPT_DECRYPT,
        )

        assertFailsWith<IllegalStateException> {
            KmsJwtSigner(kmsClient, JwtSigningProperties(signingKey = encryptionKeyId))
        }
    }

    @Test
    @DisplayName("KMS 서명이 실패하면 503 BusinessException이 되고 응답 문구에 KMS 키 정보가 없다.")
    fun signFailureBecomesServiceUnavailable() {
        val keyId = LocalStackKms.createKey(kmsClient)
        val signer = KmsJwtSigner(kmsClient, JwtSigningProperties(signingKey = keyId))
        kmsClient.disableKey { it.keyId(keyId) }

        val exception = assertFailsWith<BusinessException> { signer.sign(claims()) }

        assertEquals(AuthErrorCode.TOKEN_SIGNING_UNAVAILABLE, exception.errorCode)
        assertFalse(exception.message.contains(keyId))
        assertTrue(exception.cause != null)
    }

    private fun signer(verificationKeys: List<String> = emptyList()): KmsJwtSigner =
        KmsJwtSigner(
            kmsClient,
            JwtSigningProperties(signingKey = LocalStackKms.SIGNING_KEY_ALIAS, verificationKeys = verificationKeys),
        )

    private fun claims(): JWTClaimsSet =
        JWTClaimsSet.Builder()
            .subject("TERM-001")
            .claim("storeId", "STORE-001")
            .jwtID("jti-1")
            .expirationTime(Date(System.currentTimeMillis() + 60_000))
            .build()
}
