package io.aegis.services.auth.application.port.out

import io.aegis.services.auth.domain.terminal.Terminal

interface LoadTerminalPort {
    fun findByTerminalId(terminalId: String): Terminal?
}
