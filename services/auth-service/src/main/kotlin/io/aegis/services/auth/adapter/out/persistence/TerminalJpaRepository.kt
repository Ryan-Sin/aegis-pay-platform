package io.aegis.services.auth.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface TerminalJpaRepository : JpaRepository<TerminalJpaEntity, String>
