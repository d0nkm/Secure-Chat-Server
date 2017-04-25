package org.catafratta.martello.schat

import kotlin.js.json

class KeyFormController(`$scope`: AngularScope, `$state`: UiState, ChatService: ChatServiceImpl) {
    init {
        `$scope`["prober"] = Prober
        `$scope`["recipient"] = Recipient

        `$scope`["probe"] = probe@ { ->
            if (Recipient.pubkey.isNullOrBlank()) return@probe

            Recipient.pubkey?.let { pem ->
                try {
                    val pubkey = forge.pki.publicKeyFromPem(pem)
                    Prober.probing = true
                    ChatService.startChat(pubkey).finally({
                        Prober.probing = false
                        `$state`.go("home.chat")
                    })
                    Prober.msg = "Waiting for recipient..."
                } catch (t: Throwable) {
                    Prober.errMsg = t.message
                    return@let
                }
            }
        }
    }

    private object Prober {
        @JsName("probing")
        var probing = false

        @JsName("errMsg")
        var errMsg: String? = null

        @JsName("msg")
        var msg: String? = null
    }

    private object Recipient {
        @JsName("pubkey")
        var pubkey: String? = null
    }
}