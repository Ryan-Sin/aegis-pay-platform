package io.aegis.platform.mvc.context

import io.aegis.platform.core.context.TerminalContext
import io.aegis.platform.core.exception.CommonErrorCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(
    classes = [CurrentTerminalBaggageIntegrationTest.TestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "management.tracing.sampling.probability=1.0",
        "management.otlp.metrics.export.enabled=false",
    ],
)
class CurrentTerminalBaggageIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Test
    @DisplayName("gateway가 보낸 baggage에서 terminalId·storeId를, X-Terminal-Scopes에서 권한을 읽는다.")
    fun resolvesTerminalContextFromBaggage() {
        val response = get(
            "/terminal",
            "traceparent" to TRACEPARENT,
            "baggage" to "terminalId=TERM-001,storeId=STORE-001",
            TerminalContext.HEADER_TERMINAL_SCOPES to " payment:read, ,payment:write ",
        )

        assertEquals(200, response.statusCode())
        assertEquals("TERM-001|STORE-001|[payment:read, payment:write]", response.body())
    }

    @Test
    @DisplayName("baggage 없이 예전 X-Terminal-Id·X-Store-Id 헤더만 보내면 401이다(헤더는 더 이상 신뢰하지 않는다).")
    fun rejectsLegacyHeadersWithoutBaggage() {
        val response = get(
            "/terminal",
            TerminalContext.HEADER_TERMINAL_ID to "TERM-001",
            TerminalContext.HEADER_STORE_ID to "STORE-001",
        )

        assertEquals(401, response.statusCode())
        assertTrue(response.body().contains(CommonErrorCode.MISSING_TERMINAL_CONTEXT.code))
    }

    @Test
    @DisplayName("baggage에 storeId가 빠지면 401이다.")
    fun rejectsPartialBaggage() {
        val response = get("/terminal", "traceparent" to TRACEPARENT, "baggage" to "terminalId=TERM-001")

        assertEquals(401, response.statusCode())
    }

    @Test
    @DisplayName("starter 기본 설정(correlation.fields)으로 baggage 값이 로그 MDC에도 자동으로 들어간다.")
    fun putsBaggageIntoMdcByStarterDefault() {
        val response = get(
            "/mdc",
            "traceparent" to TRACEPARENT,
            "baggage" to "terminalId=TERM-001,storeId=STORE-001",
        )

        assertEquals("TERM-001|STORE-001", response.body())
    }

    private fun get(path: String, vararg headers: Pair<String, String>): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET()
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        return HttpClient.newHttpClient().use { it.send(request, HttpResponse.BodyHandlers.ofString()) }
    }

    @SpringBootApplication
    class TestApplication {
        @RestController
        class TerminalController {
            @GetMapping("/terminal")
            fun terminal(@CurrentTerminal terminal: TerminalContext): String =
                "${terminal.terminalId}|${terminal.storeId}|${terminal.scopes.sorted()}"

            @GetMapping("/mdc")
            fun mdc(): String =
                "${MDC.get(TerminalContext.BAGGAGE_TERMINAL_ID)}|${MDC.get(TerminalContext.BAGGAGE_STORE_ID)}"
        }
    }

    private companion object {
        const val TRACEPARENT = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"
    }
}
