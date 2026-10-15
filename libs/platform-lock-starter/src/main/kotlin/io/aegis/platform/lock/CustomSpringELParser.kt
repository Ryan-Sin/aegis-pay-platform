package io.aegis.platform.lock

import io.aegis.platform.core.exception.BusinessException
import io.aegis.platform.lock.exception.LockErrorCode
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext

object CustomSpringELParser {
    private val parser = SpelExpressionParser()

    fun getDynamicValue(
        parameterNames: Array<String>,
        args: Array<Any?>,
        keyExpression: String
    ): String {
        val context = StandardEvaluationContext()

        for (i in parameterNames.indices) {
            context.setVariable(parameterNames[i], args[i])
        }

        return parser.parseExpression(keyExpression).getValue(context, String::class.java)
            ?.takeIf { it.isNotBlank() }
            ?: throw BusinessException(LockErrorCode.LOCK_KEY_EXPRESSION_INVALID, keyExpression)
    }
}