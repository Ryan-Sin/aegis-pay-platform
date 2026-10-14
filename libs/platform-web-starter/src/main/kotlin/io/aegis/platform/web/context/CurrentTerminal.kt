package io.aegis.platform.web.context

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CurrentTerminal(
    val required: Boolean = true,
)