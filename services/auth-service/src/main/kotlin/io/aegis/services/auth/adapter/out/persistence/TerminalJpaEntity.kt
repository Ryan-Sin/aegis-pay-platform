package io.aegis.services.auth.adapter.out.persistence

import io.aegis.services.auth.domain.terminal.Terminal
import io.aegis.services.auth.domain.terminal.TerminalKeyAlgorithm
import io.aegis.services.auth.domain.terminal.TerminalStatus
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "terminal")
class TerminalJpaEntity(
    @Id
    @Column(name = "terminal_id", nullable = false, updatable = false, length = 64)
    val terminalId: String,

    @Column(name = "store_id", nullable = false, length = 64)
    val storeId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "key_algorithm", nullable = false, length = 16)
    val keyAlgorithm: TerminalKeyAlgorithm,

    @Column(name = "public_key", nullable = false, length = 512)
    val publicKey: String,

    @Convert(converter = ScopesConverter::class)
    @Column(name = "scopes", nullable = false)
    val scopes: Set<String>,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    val status: TerminalStatus,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
) {
    fun toDomain(): Terminal = Terminal(
        terminalId = terminalId,
        storeId = storeId,
        keyAlgorithm = keyAlgorithm,
        publicKey = publicKey,
        scopes = scopes,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun from(terminal: Terminal): TerminalJpaEntity = TerminalJpaEntity(
            terminalId = terminal.terminalId,
            storeId = terminal.storeId,
            keyAlgorithm = terminal.keyAlgorithm,
            publicKey = terminal.publicKey,
            scopes = terminal.scopes,
            status = terminal.status,
            createdAt = terminal.createdAt,
            updatedAt = terminal.updatedAt,
        )
    }
}
