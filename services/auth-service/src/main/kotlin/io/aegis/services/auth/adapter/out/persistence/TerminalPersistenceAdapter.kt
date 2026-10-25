package io.aegis.services.auth.adapter.out.persistence

import io.aegis.services.auth.application.port.out.LoadTerminalPort
import io.aegis.services.auth.application.port.out.SaveTerminalPort
import io.aegis.services.auth.domain.terminal.Terminal
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class TerminalPersistenceAdapter(
    private val terminalJpaRepository: TerminalJpaRepository,
) : LoadTerminalPort, SaveTerminalPort {

    override fun findByTerminalId(terminalId: String): Terminal? =
        terminalJpaRepository.findByIdOrNull(terminalId)?.toDomain()

    override fun save(terminal: Terminal): Terminal =
        terminalJpaRepository.save(TerminalJpaEntity.from(terminal)).toDomain()
}
