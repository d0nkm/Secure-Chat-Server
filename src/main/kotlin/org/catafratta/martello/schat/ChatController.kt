package org.catafratta.martello.schat

import org.w3c.dom.css.CSS
import kotlin.js.Date
import kotlin.js.Json

class ChatController(`$scope`: AngularScope, `$mdDialog`: MdDialog, ChatService: ChatServiceImpl) {
    init {
        `$scope`["message"] = Message
        `$scope`["recipient"] = Recipient

        val nickPrompt = `$mdDialog`.prompt()
                .title("Choose a nickname")
                .placeholder("Your nickname")
                .ok("OK")

        Recipient.name = ChatService.session?.keyId
        // qui è già stabilita ma servono ulteriori informazioni

        ChatService.onMessage = { content ->
            Recipient.history += MessageEntry(content, false)
            `$scope`.`$applyAsync`()
        }

        ChatService.promise.then({
            console.log("Yo")
            Recipient.history += StartedEntry()

            ChatService.session?.nickPromise?.then({ nick -> Recipient.name = nick })
            `$mdDialog`.show<String>(nickPrompt).then({ nick ->
                ChatService.session?.sendNick(nick)
            })

        }, { cause: Throwable ->
            // TODO Mostra errore e torna a keyform
        })

        `$scope`["sendMessage"] = sendMessage@ {
            if (Message.content.isNullOrBlank()) return@sendMessage

            val toSend = Message.content ?: return@sendMessage
            ChatService.session?.sendMessage(toSend)?.then({
                Recipient.history += MessageEntry(toSend, true)
            }) ?: return@sendMessage

            Message.content = null
        }
    }

    private object Message {
        @JsName("content")
        var content: String? = null
    }

    private object Recipient {
        @JsName("name")
        var name: String? = null

        @JsName("history")
        var history: Array<Entry> = emptyArray()
    }

    private open class Entry(val type: String, val time: Date = Date())
    private class MessageEntry(val content: String, val sent: Boolean) : Entry("msg")
    private class StartedEntry : Entry("started")
}