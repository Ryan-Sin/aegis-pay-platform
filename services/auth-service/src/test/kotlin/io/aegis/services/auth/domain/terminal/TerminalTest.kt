package io.aegis.services.auth.domain.terminal

import io.aegis.services.auth.support.TerminalFixtures
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TerminalTest {

    @Test
    @DisplayName("정지된 단말은 다시 활성화할 수 있다.")
    fun suspendedTerminalCanBeReactivated() {
        val terminal = TerminalFixtures.terminal(status = TerminalStatus.SUSPENDED)

        terminal.changeStatus(TerminalStatus.ACTIVE, TerminalFixtures.NOW.plusSeconds(1))

        assertEquals(TerminalStatus.ACTIVE, terminal.status)
    }

    @Test
    @DisplayName("폐기된 단말은 상태를 바꿀 수 없다.")
    fun revokedTerminalCannotChangeStatus() {
        val terminal = TerminalFixtures.terminal(status = TerminalStatus.REVOKED)

        assertFailsWith<IllegalStateException> {
            terminal.changeStatus(TerminalStatus.ACTIVE, TerminalFixtures.NOW.plusSeconds(1))
        }
        assertEquals(TerminalStatus.REVOKED, terminal.status)
    }
}
