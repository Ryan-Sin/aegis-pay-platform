package io.aegis.services.auth.domain.terminal

enum class TerminalKeyAlgorithm(
    val jcaKeyAlgorithm: String,
    val curveName: String,
    val jcaSignatureAlgorithm: String,
) {
    EC_P256(jcaKeyAlgorithm = "EC", curveName = "secp256r1", jcaSignatureAlgorithm = "SHA256withECDSA"),
}
