package org.catafratta.martello.schat

import kotlin.browser.document
import kotlin.js.json

class AppController(`$scope`: AngularScope, `$mdDialog`: MdDialog, `$state`: UiState,
                    Crypto: CryptoImpl, ChatService: ChatServiceImpl) {
    init {
        GlobalState.startLoading("Generating an RSA key pair...")

        Crypto.getUserPublicPem().then({ _ ->
            GlobalState.loadingMessage = "Connecting to chat server..."

            ChatService.connect().then({
                GlobalState.stopLoading()

                `$state`.go("home.connected")
            })
        })

        `$scope`.`$on`("reconnecting", { _: Any?, attempt: Int ->
            `$state`.go("home")
            GlobalState.startLoading("Reconnecting to server (attempt $attempt)...")
        })

        `$scope`.`$on`("reconnect", { ->
            GlobalState.stopLoading()
            `$state`.go("home.connected")
        })

        `$scope`["pubkeyDialog"] = { ->
            `$mdDialog`.show<Unit>(json(
                    "templateUrl" to "partials/pubkey.html",
                    "parent" to angular.element(document.body!!),
                    "clickOutsideToClose" to true,
                    "controller" to { `$scope`: AngularScope, `$mdDialog`: MdDialog, pem: String ->
                        `$scope`["pem"] = pem
                        `$scope`["hide"] = { -> `$mdDialog`.hide() }
                    },
                    "resolve" to json(
                            "pem" to { Crypto: CryptoImpl ->
                                Crypto.getUserPublicPem()
                            }
                    )
            ))
        }
    }
}