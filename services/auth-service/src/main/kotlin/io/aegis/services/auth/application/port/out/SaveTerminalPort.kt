package io.aegis.services.auth.application.port.out

import io.aegis.services.auth.domain.terminal.Terminal

interface SaveTerminalPort {
    fun save(terminal: Terminal): Terminal
}
