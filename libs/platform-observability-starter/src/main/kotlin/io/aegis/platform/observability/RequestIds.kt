package io.aegis.platform.observability

import java.util.UUID

/**
 * 외부에서 들어온 Request ID를 검증한다(ADR-0002).
 * 형식이 맞지 않으면 로그 인젝션과 카디널리티 폭증을 막기 위해 버리고 새로 생성한다.
 */
object RequestIds {

    private val VALID_REQUEST_ID = Regex("[A-Za-z0-9._-]{8,64}")

    fun resolve(candidate: String?): String =
        candidate?.takeIf { VALID_REQUEST_ID.matches(it) } ?: generate()

    fun generate(): String = UUID.randomUUID().toString()
}
