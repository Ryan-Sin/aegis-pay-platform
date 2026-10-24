package io.aegis.platform.observability

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RequestIdsTest {

    @ParameterizedTest
    @ValueSource(strings = ["req-1234", "0f8fad5b-d9cb-469f-a165-70867728950e", "a.b_c-D.9_"])
    @DisplayName("형식에 맞는 ID는 그대로 사용한다.")
    fun keepsValidId(candidate: String) {
        assertEquals(candidate, RequestIds.resolve(candidate))
    }

    @Test
    @DisplayName("길이 경계: 8자와 64자는 허용하고, 7자와 65자는 교체한다.")
    fun appliesLengthBoundary() {
        assertEquals("a".repeat(8), RequestIds.resolve("a".repeat(8)))
        assertEquals("a".repeat(64), RequestIds.resolve("a".repeat(64)))
        assertNotEquals("a".repeat(7), RequestIds.resolve("a".repeat(7)))
        assertNotEquals("a".repeat(65), RequestIds.resolve("a".repeat(65)))
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["", "        ", "\${jndi:ldap://x}", "req-1234\nFAKE LOG", "요청아이디-12345", "req 1234 5678"])
    @DisplayName("없거나 형식이 틀린 ID(공백, 로그 인젝션, 비ASCII)는 새 UUID로 교체한다.")
    fun replacesInvalidIdWithUuid(candidate: String?) {
        val resolved = RequestIds.resolve(candidate)

        assertNotEquals(candidate, resolved)
        UUID.fromString(resolved)
    }
}
