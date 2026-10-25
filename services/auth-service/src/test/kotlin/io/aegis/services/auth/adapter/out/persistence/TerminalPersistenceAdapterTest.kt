package io.aegis.services.auth.adapter.out.persistence

import io.aegis.services.auth.domain.terminal.TerminalKeyAlgorithm
import io.aegis.services.auth.domain.terminal.TerminalPublicKeys
import io.aegis.services.auth.domain.terminal.TerminalStatus
import io.aegis.services.auth.support.MySqlContainerConfiguration
import io.aegis.services.auth.support.TerminalFixtures
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@DataJpaTest
@Import(MySqlContainerConfiguration::class, TerminalPersistenceAdapter::class)
class TerminalPersistenceAdapterTest {

    @Autowired
    private lateinit var adapter: TerminalPersistenceAdapter

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    @DisplayName("init SQL로 만든 terminal 테이블에 저장한 단말을 도메인 모델로 그대로 다시 읽는다.")
    fun savesAndLoadsTerminal() {
        val keyPair = TerminalFixtures.ecKeyPair()
        adapter.save(TerminalFixtures.terminal(keyPair = keyPair))
        flushAndClear()

        val loaded = adapter.findByTerminalId("TERM-001")!!

        assertEquals("STORE-001", loaded.storeId)
        assertEquals(TerminalKeyAlgorithm.EC_P256, loaded.keyAlgorithm)
        assertEquals(setOf("payment:approve", "payment:cancel"), loaded.scopes)
        assertEquals(TerminalStatus.ACTIVE, loaded.status)
        assertEquals(TerminalFixtures.NOW, loaded.createdAt)
        assertEquals(keyPair.public, TerminalPublicKeys.decode(loaded.keyAlgorithm, loaded.publicKey))
    }

    @Test
    @DisplayName("없는 단말은 null이다.")
    fun returnsNullForUnknownTerminal() {
        assertNull(adapter.findByTerminalId("UNKNOWN"))
    }

    @Test
    @DisplayName("시각은 JVM 시간대와 상관없이 UTC로 저장된다.")
    fun storesTimestampsInUtc() {
        adapter.save(TerminalFixtures.terminal())
        flushAndClear()

        val storedCreatedAt = jdbcTemplate.queryForObject(
            "select date_format(created_at, '%Y-%m-%dT%H:%i:%s.%f') from terminal where terminal_id = ?",
            String::class.java,
            "TERM-001",
        )

        assertEquals("2026-10-25T03:00:00.123456", storedCreatedAt)
    }

    @Test
    @DisplayName("scopes는 정렬된 콤마 문자열로 저장된다.")
    fun storesScopesAsSortedCommaSeparatedString() {
        adapter.save(TerminalFixtures.terminal(scopes = setOf("payment:cancel", "payment:approve")))
        flushAndClear()

        val storedScopes = jdbcTemplate.queryForObject(
            "select scopes from terminal where terminal_id = ?",
            String::class.java,
            "TERM-001",
        )

        assertEquals("payment:approve,payment:cancel", storedScopes)
    }

    @Test
    @DisplayName("도메인에서 바꾼 상태를 저장하면 status와 updated_at에 반영된다.")
    fun persistsStatusChange() {
        adapter.save(TerminalFixtures.terminal())
        flushAndClear()

        val suspendedAt = TerminalFixtures.NOW.plusSeconds(60)
        val terminal = adapter.findByTerminalId("TERM-001")!!
        terminal.changeStatus(TerminalStatus.SUSPENDED, suspendedAt)
        adapter.save(terminal)
        flushAndClear()

        val loaded = adapter.findByTerminalId("TERM-001")!!
        assertEquals(TerminalStatus.SUSPENDED, loaded.status)
        assertEquals(suspendedAt, loaded.updatedAt)
        assertFalse(loaded.isActive)
    }

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }
}
