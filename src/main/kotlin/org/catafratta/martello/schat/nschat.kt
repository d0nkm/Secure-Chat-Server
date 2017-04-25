package org.catafratta.martello.schat

import kotlin.js.json

fun main(args: Array<String>) {
    forge.options.usePureJavaScript = true

    val app = angular.module("SecureChat", arrayOf("ui.router", "ngMaterial", "luegg.directives"))

    app.controller("AppController", AppController::class.js)
    app.controller("KeyFormController", KeyFormController::class.js)
    app.controller("ChatController", ChatController::class.js)
    app.service("Crypto", CryptoImpl::class.js)
    app.service("ChatService", ChatServiceImpl::class.js)

    app.config { `$stateProvider`: dynamic ->
        `$stateProvider`
                .state("home", json(
                        "url" to "",
                        "templateUrl" to "partials/main.html"
                ))
                .state("home.connected", json(
                        "url" to "",
                        "controller" to "KeyFormController",
                        "templateUrl" to "partials/keyform.html"
                ))
                .state("home.chat", json(
                        "url" to "",
                        "controller" to "ChatController",
                        "templateUrl" to "partials/chat.html",
                        "params" to json("recipient" to null)
                ))
    }

    app.run { `$rootScope`: AngularRootScope, `$state`: UiState ->
        GlobalState.rootScope = `$rootScope`
        `$rootScope`["sc"] = GlobalState
        `$state`.go("home")
    }
}

object GlobalState {
    const val uwu = "˘~˘"

    var rootScope: AngularRootScope? = null

    var loading = false
        set(value) {
            field = value
            rootScope?.`$applyAsync`()
        }

    var loadingMessage: String? = null
        set(value) {
            field = value
            rootScope?.`$applyAsync`()
        }


    fun startLoading(msg: String? = null) {
        loadingMessage = msg
        loading = true
    }

    fun stopLoading() {
        loading = false
    }
}