package io.aegis.platform.core.context

data class TerminalContext(
    val terminalId: String,
    val storeId: String,
    val scopes: Set<String> = emptySet(),
) {
    companion object {
        const val HEADER_TERMINAL_ID = "X-Terminal-Id"
        const val HEADER_STORE_ID = "X-Store-Id"
        const val HEADER_TERMINAL_SCOPES = "X-Terminal-Scopes"

        const val BAGGAGE_TERMINAL_ID = "terminalId"
        const val BAGGAGE_STORE_ID = "storeId"

        const val CLAIM_STORE_ID = "storeId"
        const val CLAIM_SCOPES = "scopes"
    }
}