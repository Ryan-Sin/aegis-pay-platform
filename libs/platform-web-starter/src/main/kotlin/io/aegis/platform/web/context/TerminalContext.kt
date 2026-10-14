package io.aegis.platform.web.context

data class TerminalContext(
    val terminalId: String,
    val storeId: String,
    val scopes: Set<String> = emptySet(),
) {
    companion object {
        const val HEADER_TERMINAL_ID = "X-Terminal-Id"
        const val HEADER_STORE_ID = "X-Store-Id"
        const val HEADER_TERMINAL_SCOPES = "X-Terminal-Scopes"
    }
}