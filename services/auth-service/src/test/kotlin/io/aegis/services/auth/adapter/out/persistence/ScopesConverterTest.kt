package io.aegis.services.auth.adapter.out.persistence

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ScopesConverterTest {

    private val converter = ScopesConverter()

    @Test
    @DisplayName("저장할 때 공백을 지우고 빈 값은 버리고 정렬한다.")
    fun normalizesWhenWriting() {
        assertEquals("a,b", converter.convertToDatabaseColumn(setOf(" b ", "", "a")))
    }

    @Test
    @DisplayName("빈 문자열과 null은 빈 집합이 된다.")
    fun readsEmptyAsEmptySet() {
        assertEquals(emptySet(), converter.convertToEntityAttribute(""))
        assertEquals(emptySet(), converter.convertToEntityAttribute(null))
    }

    @Test
    @DisplayName("읽을 때 공백과 빈 항목을 무시한다.")
    fun ignoresBlankEntriesWhenReading() {
        assertEquals(setOf("a", "b"), converter.convertToEntityAttribute(" a,,b ,"))
    }
}
