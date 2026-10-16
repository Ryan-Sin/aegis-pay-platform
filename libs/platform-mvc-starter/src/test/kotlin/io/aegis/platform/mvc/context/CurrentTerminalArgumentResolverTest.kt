package io.aegis.platform.mvc.context

import io.aegis.platform.core.context.TerminalContext
import org.junit.jupiter.api.Assertions.*
import org.springframework.core.MethodParameter

class CurrentTerminalArgumentResolverTest {

    private val resolver = CurrentTerminalArgumentResolver()

    @Suppress("unused")
    private fun sampleEndpoint(@CurrentTerminal terminal: TerminalContext) = Unit

    private val methodParameter: MethodParameter by lazy {
        val method = this::class.java.getDeclaredMethod("sampleEndpoint", TerminalContext::class.java)
        MethodParameter(method,0)
    }
}