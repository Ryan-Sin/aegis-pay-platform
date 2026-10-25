package io.aegis.services.auth.domain.terminal

import io.aegis.services.auth.domain.auth.TerminalChallenge
import io.aegis.services.auth.support.TerminalFixtures
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalSignaturesTest {

    private val keyPair = TerminalFixtures.ecKeyPair()
    private val message = TerminalChallenge.signingMessage("TERM-001", "nonce-1")

    @Test
    @DisplayName("단말 비밀키로 만든 challenge 서명은 등록된 공개키로 검증된다.")
    fun verifiesValidSignature() {
        val signature = TerminalFixtures.signChallenge(keyPair.private, "TERM-001", "nonce-1")

        assertTrue(TerminalSignatures.verify(TerminalKeyAlgorithm.EC_P256, keyPair.public, message, signature))
    }

    @Test
    @DisplayName("다른 nonce나 다른 단말 ID에 대한 서명은 통과하지 못한다.")
    fun rejectsSignatureForDifferentMessage() {
        val otherNonce = TerminalFixtures.signChallenge(keyPair.private, "TERM-001", "nonce-2")
        val otherTerminal = TerminalFixtures.signChallenge(keyPair.private, "TERM-002", "nonce-1")

        assertFalse(TerminalSignatures.verify(TerminalKeyAlgorithm.EC_P256, keyPair.public, message, otherNonce))
        assertFalse(TerminalSignatures.verify(TerminalKeyAlgorithm.EC_P256, keyPair.public, message, otherTerminal))
    }

    @Test
    @DisplayName("다른 단말의 비밀키로 만든 서명은 통과하지 못한다.")
    fun rejectsSignatureFromOtherKey() {
        val signature = TerminalFixtures.signChallenge(TerminalFixtures.ecKeyPair().private, "TERM-001", "nonce-1")

        assertFalse(TerminalSignatures.verify(TerminalKeyAlgorithm.EC_P256, keyPair.public, message, signature))
    }

    @Test
    @DisplayName("base64가 아니거나 서명 구조가 아닌 값은 예외 없이 false다.")
    fun returnsFalseForMalformedSignature() {
        assertFalse(TerminalSignatures.verify(TerminalKeyAlgorithm.EC_P256, keyPair.public, message, "not base64!!"))
        assertFalse(TerminalSignatures.verify(TerminalKeyAlgorithm.EC_P256, keyPair.public, message, "AAAA"))
    }
}
