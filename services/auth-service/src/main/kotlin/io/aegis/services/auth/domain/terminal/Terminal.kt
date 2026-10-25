package io.aegis.services.auth.domain.terminal

import java.time.Instant

class Terminal(
    val terminalId: String,
    val storeId: String,
    val keyAlgorithm: TerminalKeyAlgorithm,
    val publicKey: String,
    val scopes: Set<String>,
    status: TerminalStatus,
    val createdAt: Instant,
    updatedAt: Instant,
) {
    var status: TerminalStatus = status
        private set

    var updatedAt: Instant = updatedAt
        private set

    val isActive: Boolean
        get() = status == TerminalStatus.ACTIVE

    fun changeStatus(newStatus: TerminalStatus, now: Instant) {
        check(status != TerminalStatus.REVOKED) { "Revoked terminal cannot change status: terminalId=$terminalId" }
        status = newStatus
        updatedAt = now
    }
}
