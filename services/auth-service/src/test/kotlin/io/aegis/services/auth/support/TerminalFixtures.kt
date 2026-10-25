package io.aegis.services.auth.support

import io.aegis.services.auth.domain.auth.TerminalChallenge
import io.aegis.services.auth.domain.terminal.Terminal
import io.aegis.services.auth.domain.terminal.TerminalKeyAlgorithm
import io.aegis.services.auth.domain.terminal.TerminalPublicKeys
import io.aegis.services.auth.domain.terminal.TerminalStatus
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

object TerminalFixtures {
    val NOW: Instant = Instant.parse("2026-10-25T03:00:00.123456Z")

    fun ecKeyPair(curveName: String = "secp256r1"): KeyPair =
        KeyPairGenerator.getInstance("EC")
            .apply { initialize(ECGenParameterSpec(curveName)) }
            .generateKeyPair()

    fun terminal(
        terminalId: String = "TERM-001",
        storeId: String = "STORE-001",
        keyPair: KeyPair = ecKeyPair(),
        scopes: Set<String> = setOf("payment:approve", "payment:cancel"),
        status: TerminalStatus = TerminalStatus.ACTIVE,
        now: Instant = NOW,
    ): Terminal = Terminal(
        terminalId = terminalId,
        storeId = storeId,
        keyAlgorithm = TerminalKeyAlgorithm.EC_P256,
        publicKey = TerminalPublicKeys.encode(keyPair.public),
        scopes = scopes,
        status = status,
        createdAt = now.truncatedTo(ChronoUnit.MICROS),
        updatedAt = now.truncatedTo(ChronoUnit.MICROS),
    )

    fun signChallenge(privateKey: PrivateKey, terminalId: String, nonce: String): String {
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(TerminalChallenge.signingMessage(terminalId, nonce))
            sign()
        }
        return Base64.getEncoder().encodeToString(signature)
    }
}
