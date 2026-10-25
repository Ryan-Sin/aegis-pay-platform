package io.aegis.platform.mvc.context

import io.aegis.platform.core.context.TerminalContext
import io.aegis.platform.core.exception.BusinessException
import io.aegis.platform.core.exception.CommonErrorCode
import io.micrometer.tracing.BaggageManager
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

class CurrentTerminalArgumentResolver(
    private val baggageManager: BaggageManager?,
) : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.hasParameterAnnotation(CurrentTerminal::class.java) &&
                parameter.parameterType == TerminalContext::class.java
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?
    ): Any? {
        val annotation = parameter.getParameterAnnotation(CurrentTerminal::class.java)!!

        val terminalId = baggageValue(TerminalContext.BAGGAGE_TERMINAL_ID)
        val storeId = baggageValue(TerminalContext.BAGGAGE_STORE_ID)

        if (terminalId == null && storeId == null && !annotation.required) {
            return null
        }

        if (terminalId == null || storeId == null) {
            throw BusinessException(
                CommonErrorCode.MISSING_TERMINAL_CONTEXT,
                detail = "terminal baggage missing (terminalId=${terminalId != null}, storeId=${storeId != null}, " +
                    "tracing=${baggageManager != null})",
            )
        }

        val scopes = webRequest.getHeader(TerminalContext.HEADER_TERMINAL_SCOPES).orEmpty()
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()

        return TerminalContext(
            terminalId = terminalId,
            storeId = storeId,
            scopes = scopes
        )
    }

    private fun baggageValue(name: String): String? =
        baggageManager?.getBaggage(name)?.get()?.takeIf { it.isNotBlank() }
}
