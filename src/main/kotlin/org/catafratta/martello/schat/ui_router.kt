package org.catafratta.martello.schat

import kotlin.js.Json

external interface UiState {
    val params: Json

    fun go(to: String, params: dynamic = definedExternally, options: dynamic = definedExternally): Any?
}
