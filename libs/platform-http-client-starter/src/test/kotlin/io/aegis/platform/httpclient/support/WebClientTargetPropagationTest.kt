package io.aegis.platform.httpclient.support

import io.aegis.platform.httpclient.AsyncHttpClientFactory
import io.aegis.platform.httpclient.get
import io.aegis.platform.httpclient.support.HttpTargetPropagationFixture.assertInternalContextPropagated
import io.aegis.platform.httpclient.support.HttpTargetPropagationFixture.assertOnlyTraceContextPropagated
import io.aegis.platform.httpclient.support.HttpTargetPropagationFixture.externalSpec
import io.aegis.platform.httpclient.support.HttpTargetPropagationFixture.internalSpec
import io.aegis.platform.httpclient.support.HttpTargetPropagationFixture.withRequestContext
import io.micrometer.tracing.Tracer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    classes = [HttpClientTestApplication::class],
    properties = [
        "spring.main.web-application-type=reactive",
        "management.tracing.sampling.probability=1.0",
        "management.tracing.baggage.remote-fields=terminalId,storeId",
        "management.otlp.metrics.export.enabled=false",
    ],
)
class WebClientTargetPropagationTest {

    @Autowired
    private lateinit var factory: AsyncHttpClientFactory

    @Autowired
    private lateinit var tracer: Tracer

    @BeforeEach
    fun setUp() {
        HttpTargetPropagationFixture.reset()
    }

    @Test
    @DisplayName("내부 대상은 Request ID, 단말 컨텍스트, baggage, traceparent를 모두 받는다.")
    fun internalTargetReceivesInternalContext() {
        val client = factory.create(internalSpec())

        val traceId = withRequestContext(tracer) { runBlocking { client.get<String>("/") } }

        assertInternalContextPropagated(traceId)
    }

    @Test
    @DisplayName("외부 대상(기본값)은 traceparent만 받고 내부 식별자와 baggage는 받지 않는다.")
    fun externalTargetReceivesOnlyTraceContext() {
        val client = factory.create(externalSpec())

        val traceId = withRequestContext(tracer) { runBlocking { client.get<String>("/") } }

        assertOnlyTraceContextPropagated(traceId)
    }
}
