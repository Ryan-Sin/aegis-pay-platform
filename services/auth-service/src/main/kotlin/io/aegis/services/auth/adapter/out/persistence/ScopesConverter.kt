package io.aegis.services.auth.adapter.out.persistence

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class ScopesConverter : AttributeConverter<Set<String>, String> {
    override fun convertToDatabaseColumn(attribute: Set<String>?): String =
        attribute.orEmpty()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .sorted()
            .joinToString(SEPARATOR)

    override fun convertToEntityAttribute(dbData: String?): Set<String> =
        dbData.orEmpty()
            .split(SEPARATOR)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()

    private companion object {
        const val SEPARATOR = ","
    }
}
