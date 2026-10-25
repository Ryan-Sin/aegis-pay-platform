package io.aegis.services.auth.domain.terminal

import io.aegis.services.auth.support.TerminalFixtures
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class TerminalPublicKeysTest {

    @Test
    @DisplayName("P-256 공개키는 인코딩 후 디코딩하면 같은 키가 된다.")
    fun roundTripsP256Key() {
        val publicKey = TerminalFixtures.ecKeyPair().public

        val decoded = TerminalPublicKeys.decode(TerminalKeyAlgorithm.EC_P256, TerminalPublicKeys.encode(publicKey))

        assertEquals(publicKey, decoded)
    }

    @Test
    @DisplayName("base64가 아니면 거부한다.")
    fun rejectsInvalidBase64() {
        assertFailsWith<IllegalArgumentException> {
            TerminalPublicKeys.decode(TerminalKeyAlgorithm.EC_P256, "not base64!!")
        }
    }

    @Test
    @DisplayName("공개키 구조(SPKI)가 아니면 거부한다.")
    fun rejectsNonKeyBytes() {
        val garbage = Base64.getEncoder().encodeToString(ByteArray(91) { it.toByte() })

        assertFailsWith<IllegalArgumentException> {
            TerminalPublicKeys.decode(TerminalKeyAlgorithm.EC_P256, garbage)
        }
    }

    @Test
    @DisplayName("RSA 공개키는 EC_P256으로 받지 않는다.")
    fun rejectsRsaKey() {
        val rsaPublicKey = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public

        assertFailsWith<IllegalArgumentException> {
            TerminalPublicKeys.decode(TerminalKeyAlgorithm.EC_P256, TerminalPublicKeys.encode(rsaPublicKey))
        }
    }

    @Test
    @DisplayName("다른 곡선(P-384)의 EC 공개키는 거부한다.")
    fun rejectsOtherCurve() {
        val p384PublicKey = TerminalFixtures.ecKeyPair(curveName = "secp384r1").public

        assertFailsWith<IllegalArgumentException> {
            TerminalPublicKeys.decode(TerminalKeyAlgorithm.EC_P256, TerminalPublicKeys.encode(p384PublicKey))
        }
    }

    @Test
    @DisplayName("거부 사유 메시지에 키 값을 넣지 않는다.")
    fun doesNotExposeKeyInMessage() {
        val p384Encoded = TerminalPublicKeys.encode(TerminalFixtures.ecKeyPair(curveName = "secp384r1").public)

        val exception = assertFailsWith<IllegalArgumentException> {
            TerminalPublicKeys.decode(TerminalKeyAlgorithm.EC_P256, p384Encoded)
        }

        assertFalse(exception.message.orEmpty().contains(p384Encoded))
    }
}
