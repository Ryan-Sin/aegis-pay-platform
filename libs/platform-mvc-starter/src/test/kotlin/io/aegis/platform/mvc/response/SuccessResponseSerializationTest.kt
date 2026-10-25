package io.aegis.platform.mvc.response

import io.aegis.platform.core.response.SuccessResponse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

class SuccessResponseSerializationTest {

    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(SampleController()).build()

    @Test
    @DisplayName("성공 응답은 timestamp, code, data 구조로 직렬화되고 message는 없다.")
    fun serializesDataInEnvelope() {
        mockMvc.get("/sample").andExpect {
            status { isOk() }
            jsonPath("$.code") { value(SuccessResponse.SUCCESS_CODE) }
            jsonPath("$.message") { doesNotHaveJsonPath() }
            jsonPath("$.data.orderId") { value("ORDER-1") }
            jsonPath("$.data.amount") { value(1000) }
            jsonPath("$.timestamp") { isString() }
        }
    }

    @Test
    @DisplayName("결과가 없는 성공 응답은 data 필드 자체가 빠진다(null을 내려보내지 않는다).")
    fun serializesEmptyEnvelope() {
        mockMvc.get("/sample/empty").andExpect {
            status { isOk() }
            jsonPath("$.code") { value(SuccessResponse.SUCCESS_CODE) }
            jsonPath("$.timestamp") { isString() }
            jsonPath("$.data") { doesNotHaveJsonPath() }
        }
    }

    data class SampleResult(val orderId: String, val amount: Long)

    @RestController
    class SampleController {
        @GetMapping("/sample")
        fun sample(): SuccessResponse<SampleResult> = SuccessResponse.of(SampleResult("ORDER-1", 1000))

        @GetMapping("/sample/empty")
        fun empty(): SuccessResponse<Nothing> = SuccessResponse.empty()
    }
}
