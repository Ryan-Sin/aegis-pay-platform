package io.aegis.platform.mvc.context

import io.aegis.platform.core.context.TerminalContext
import io.aegis.platform.core.exception.BusinessException
import io.aegis.platform.core.exception.CommonErrorCode
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.MethodParameter
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

class CurrentTerminalArgumentResolver : HandlerMethodArgumentResolver {
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
        val request = webRequest.getNativeRequest(HttpServletRequest::class.java)
            ?: throw BusinessException(CommonErrorCode.SERVLET_REQUEST_NOT_FOUND)

        val terminalId = request.getHeader(TerminalContext.HEADER_TERMINAL_ID)?.takeIf { it.isNotBlank() }
        val storeId = request.getHeader(TerminalContext.HEADER_STORE_ID)?.takeIf { it.isNotBlank() }

        if (terminalId == null || storeId == null) {
            if (!annotation.required) return null

            val missingHeader = if (terminalId == null) {
                TerminalContext.HEADER_TERMINAL_ID
            } else {
                TerminalContext.HEADER_STORE_ID
            }
            throw MissingRequestHeaderException(missingHeader, parameter)
        }

        val scopesHeader = request.getHeader(TerminalContext.HEADER_TERMINAL_SCOPES).orEmpty()
        val scopes = scopesHeader
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
}