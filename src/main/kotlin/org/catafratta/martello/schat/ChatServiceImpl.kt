package org.catafratta.martello.schat

import kotlin.js.*
import kotlin.reflect.KFunction

private typealias RelayHandler = (data: Json) -> Promise<Json>?

class ChatServiceImpl(private val `$rootScope`: AngularRootScope, private val `$q`: AngularQ, private val Crypto: CryptoImpl) {
    private var socket: Socket? = null
    var session: ChatSession? = null
        private set
    private var yo: AngularDeferred<Unit, Unit>? = null
    private var deferred: AngularDeferred<Unit, Unit> = `$q`.defer<Unit, Unit>()

    val promise get() = deferred.promise

    var onMessage: ((content: String) -> Unit)? = null

    fun connect(): AngularPromise<Unit, Unit> {
        if (socket != null) return `$q`.`when`(Unit)

        val deferred = `$q`.defer<Unit, Unit>()

        val socket = io.connect("/", json("path" to "/schat/socket.io"))

        socket.on("connect", { -> Crypto.getUserPublicPem().then({ pem -> socket.emit("auth.pubkey", pem) }) })

        socket.on("auth.challenge", { challenge: String, ack ->
            Crypto.sign(challenge).then({ signature -> ack(signature) })
        })

        socket.on("auth.ok", { ->
            this.socket = socket
            deferred.resolve()
        })

        socket.on("reconnecting", { attempt: Int ->
            `$rootScope`.`$broadcast`("reconnecting", attempt)
        })

        socket.on("reconnect", { ->
            `$rootScope`.`$broadcast`("reconnect")
        })

        socket.on("available", { keyId: String ->
            if (session?.keyId == keyId) {
                yo?.resolve()
            }
        })

        socket.on("relay", { data: Json ->
            session?.let { session ->
                val from = data["keyId"] as String? ?: return@let
                val type = data["type"] as String? ?: return@let

                try {
                    session.handlers[type]?.invoke(data)?.then({ response ->
                        response["keyId"] = from
                        socket.emit("relay", response)
                        console.log("Handled $type, ready: ${session.ready}")
                    }, { err ->
                        console.error(err)
                        this.session = null
                        yo = null
                        deferred.reject(err)
                    })
                } catch (t: Throwable) {
                    console.error(t)
                    this.session = null
                    yo = null
                    deferred.reject(t)
                }
            }
        })

        return deferred.promise
    }

    fun startChat(publicKey: PublicKey): AngularPromise<Unit, Unit> {
        val deferred = `$q`.defer<Unit, Unit>()

        val keyId = publicKey.hexFingerprint()

        val session = ChatSession(publicKey)
        this.session = session
        this.deferred = `$q`.defer()

        yo = `$q`.defer()
        yo!!.promise.then({
            sendTo(keyId, session.prepareYo())
            promise.then(deferred::resolve, deferred::reject)
        })

        socket!!.emit("subscribe", keyId)

        return deferred.promise
    }

    fun sendTo(keyId: String, msg: Json) {
        msg["keyId"] = keyId
        socket?.emit("relay", msg)
    }

    inner class ChatSession(val publicKey: PublicKey) {
        val keyId = publicKey.hexFingerprint()
        private val yoN: Double = Math.random() * 10e15
        private var sentChallenge: String? = null
        private var sentPre: String? = null
        private var receivedPre: String? = null
        private var secret: String? = null
        private var otherReady = false
        var ready = false
            private set(value) {
                field = value
                if (value) deferred.resolve()
                `$rootScope`.`$broadcast`("chatChanged")
            }
        private val nickDeferred: AngularDeferred<Unit, String> = `$q`.defer()
        val nickPromise get() = nickDeferred.promise

        val handlers = mapOf<String, RelayHandler>(
                "yo" to this::handleYo,
                "challenge" to this::challenge,
                "challenge-response" to this::challengeResponse,
                "pre-master" to this::preMaster,
                "ready" to this::handleReady,
                "message" to this::message,
                "nick" to this::nick)

        fun prepareYo(): Json {
            return json("type" to "yo", "num" to yoN)
        }

        fun prepareChallenge(): Promise<Json> { // questo prepara un messaggio di tipo challenge
            return Promise { resolve, _ ->
                forge.random.getBytes(64).then({ bytes ->
                    sentChallenge = bytes // teniamo da parte una copia per confrontare la risposta

                    Crypto.getUserPublicPem().then({ pem ->
                        resolve(json(
                                "type" to "challenge",
                                "challenge" to forge.util.encode64(bytes),
                                "pubkey" to pem
                        ))
                    })
                })
            }
        }

        fun handleYo(data: Json): Promise<Json>? {
            if (data["num"].asDynamic() > yoN) { // client mode, iniziamo noi l'handshake?
                return prepareChallenge() // si, inviamo la challenge
            }

            return null // no, aspettiamo la challenge dall'altro
        }

        fun challenge(data: Json): Promise<Json> { // hey, una challenge!
            // val key = data["pubkey"] as String? ?: return Promise.reject(Exception("Public key mismatch"))
            val challenge = forge.util.decode64(data["challenge"] as String? ?: return Promise.reject(Exception("Missing challenge")))

            return Promise { resolve, _ ->
                // sembra tutto a posto, rispondiamo

                Crypto.sign(challenge).then({ signature ->
                    // firmiamo i byte casuali inviati dall'altro
                    resolve(json(// poi rispondiamo
                            "type" to "challenge-response",
                            "signature" to forge.util.encode64(signature)
                    ))

                    if (sentChallenge == null) { // Noi non abbiamo ancora inviato la challenge?
                        prepareChallenge().then({ challenge -> sendTo(keyId, challenge) })
                    }
                })
            }
        }

        fun challengeResponse(data: Json): Promise<Json>? { // l'altro ha risposto alla nostra challenge!
            val response = forge.util.decode64(data["signature"] as String? ?: return Promise.reject(Exception("Missing signature")))

            return Promise { resolve, reject ->
                // sembra tutto a posto, verifichiamo la firma ora

                val sent = sentChallenge
                if (sent == null) { // Una risposta ad una challenge mai inviata?
                    reject(Exception("Unattended challenge-response"))
                    return@Promise
                }

                if (!Crypto.verify(publicKey, sent, response)) { // Firma non valida
                    reject(Exception("Challenge failed"))
                    return@Promise
                }

                // La firma è valida, ora generiamo ed inviamo la nostra metà del pre-master
                forge.random.getBytes(16).then({ bytes ->
                    sentPre = bytes

                    val encrypted = publicKey.encrypt(bytes)
                    Crypto.sign(encrypted).then({ signature ->
                        resolve(json(
                                "type" to "pre-master",
                                "secret" to forge.util.encode64(encrypted),
                                "signature" to forge.util.encode64(signature)
                        ))
                    })
                }).then({
                    // se a questo punto mancava solo il nostro pezzo di pre-master, generiamo il segreto condiviso
                    generateMasterIfPossible().then({
                        sendTo(keyId, json("type" to "ready"))
                    })
                })
            }
        }

        fun preMaster(data: Json): Promise<Json>? { // L'altro ci ha inviato la sua metà del pre-master
            val secret = forge.util.decode64(data["secret"] as String? ?: return Promise.reject(Exception("Missing pre-master")))
            val signature = forge.util.decode64(data["signature"] as String? ?: return Promise.reject(Exception("Missing pre-master signature")))

            return Promise { resolve, reject ->
                // c'è tutto, verifichiamo e vediamo

                if (!Crypto.verify(publicKey, secret, signature)) { // Firma non valida
                    reject(Exception("Pre-master signature check failed"))
                    return@Promise
                }

                // Verificato che è autentico, teniamolo
                Crypto.obtainUserKeys().then({ pair ->
                    receivedPre = pair.privateKey.decrypt(secret)

                    // Ora, se abbiamo entrambe le metà calcoliamo il segreto
                    generateMasterIfPossible().then({
                        resolve(json("type" to "ready"))
                    })
                })
            }
        }

        private fun generateMasterIfPossible() = Promise<Unit> { resolve, _ ->
            receivedPre?.let { recv ->
                sentPre?.let { sent ->
                    val (key, salt) = if (sent > recv) (recv to sent) else (sent to recv)

                    //                                        ↓↓ 256 bit, dimensione della chiave per AES
                    forge.pkcs5.pbkdf2Comoda(key, salt, 1024, 32).then({ secret ->
                        this.secret = secret

                        if (otherReady) {
                            ready = true
                        }

                        resolve(Unit)
                    })
                }
            }
        }

        fun handleReady(data: Json): Promise<Json>? {
            otherReady = true

            if (secret != null) {
                ready = true
            }

            return null
        }

        fun message(data: Json): Promise<Json>? { // abbiamo ricevuto un messaggio!
            val ciphertext = forge.util.decode64(data["content"] as String? ?: return Promise.reject(Exception("Missing message content")))
            val mac = forge.util.decode64(data["mac"] as String? ?: return Promise.reject(Exception("Missing message authentication code")))

            // Il messaggio sembra valido, decriptiamo e verifichiamo
            decryptMessage(ciphertext, mac).then({ text ->
                // Valido e pronto per l'utente
                onMessage?.invoke(text)
            }, {
                console.error(Exception("Message authentication fail"))
            })

            return null
        }

        fun nick(data: Json): Promise<Json>? {
            val ciphertext = forge.util.decode64(data["nick"] as String? ?: return Promise.reject(Exception("Missing nick content")))
            val mac = forge.util.decode64(data["mac"] as String? ?: return Promise.reject(Exception("Missing nick authentication code")))

            decryptMessage(ciphertext, mac).then({ text ->
                // Valido e pronto per l'utente
                nickDeferred.resolve(text)
            }, {
                console.error(Exception("Nick authentication fail"))
            })

            return null
        }

        fun sendNick(nick: String) {
            encryptMessage(nick).then({ (encrypted, mac) ->
                // Prepariamo tutto col segreto condiviso ed inviamo
                sendTo(keyId, json(
                        "type" to "nick",
                        "nick" to forge.util.encode64(encrypted),
                        "mac" to forge.util.encode64(mac)
                ))
                deferred.resolve()
            })
        }

        fun sendMessage(content: String): AngularPromise<Unit, Unit> { // ok, dobbiamo inviare un messaggio
            val deferred = `$q`.defer<Unit, Unit>()

            encryptMessage(content).then({ (encrypted, mac) ->
                // Prepariamo tutto col segreto condiviso ed inviamo
                sendTo(keyId, json(
                        "type" to "message",
                        "content" to forge.util.encode64(encrypted),
                        "mac" to forge.util.encode64(mac)
                ))
                deferred.resolve()
            })

            return deferred.promise
        }

        private fun encryptMessage(data: String): Promise<Pair<String, String>> { // ciphertext to MAC
            return Promise { resolve, _ ->
                forge.random.getBytes(16) { _, iv ->
                    val aes = forge.cipher.createCipher("AES-CBC", secret!!)
                    aes.start(iv)
                    aes.update(forge.util.createBuffer(data, "utf-8"))
                    aes.finish()
                    val result = iv + aes.output.bytes()
                    val mac = computeMac(result)
                    resolve(result to mac)
                }
                Unit
            }
        }

        private fun decryptMessage(data: String, hmac: String): Promise<String> { // cleartext, reject on hmac mismatch
            return Promise { resolve, reject ->
                if (computeMac(data) != hmac) {
                    reject(Exception("Message authentication failed"))
                    return@Promise
                }

                val aes = forge.cipher.createDecipher("AES-CBC", secret!!)
                aes.start(data.slice(0..15))
                aes.update(forge.util.createBuffer(data.substring(16), "binary"))
                aes.finish()
                resolve(aes.output.bytes())
            }
        }

        private fun computeMac(data: String): String {
            return forge.hmac.create().let { hmac ->
                hmac.start("sha256", secret!!)
                hmac.update(data)
                hmac.digest().bytes()
            }
        }
    }
/*
    class ChatSessiodn(val pubkey: PublicKey, private val Crypto: CryptoImpl) {
        var state = State.NONE
            private set

        lateinit var skey: String
        private var challenge: String? = null
        private val sentQueue = mutableMapOf<String, String>() // msgId to content
        private var idCounter: Long = 0

        fun on(type: String?, msg: Json): Promise<Json>? {
            return when (type) {
                TYPE_NEWCHAT -> newChat(msg)
                TYPE_CHALLENGE -> challenge(msg)
                TYPE_CHALLENGE_RESPONSE -> challengeResponse(msg)
                TYPE_READY -> ready()
                TYPE_MESSAGE -> message(msg)
                TYPE_ACK -> ack(msg["id"] as String?)
                TYPE_RESET -> reset()
                else -> throw IllegalArgumentException()
            }
        }

        fun startChat(): Promise<Json> {
            state mustBe State.NONE

            return Promise { resolve, _ ->
                Crypto.getUserPublicPem().then({ pem ->
                    resolve(json("pubkey" to pem, "keyId" to keyId, "type" to TYPE_NEWCHAT))
                })
            }
        }

        private fun newChat(msg: Json): Promise<Json> {
            state mustBe State.NONE

            pubkey = forge.pki.publicKeyFromPem(msg["pubkey"] as String)

            val id = forge.pki.getPublicKeyFingerprint(pubkey, json("encoding" to "hex"))
            if (keyId != id) throw IllegalArgumentException()

            return Promise { resolve, _ ->
                forge.random.getBytes(64) { _, bytes ->
                    challenge = bytes
                    state = State.CHALLENGE_SENT
                    Crypto.getUserPublicPem().then({ pem ->
                        resolve(json("type" to TYPE_CHALLENGE, "challenge" to bytes, "pubkey" to pem))
                    })
                }
                Unit
            }
        }

        private fun challenge(msg: Json): Promise<Json> {
            state mustBe arrayOf(State.ID_SENT, State.CHALLENGE_SENT)

            return Promise { resolve, reject ->
                if (state == State.ID_SENT) {
                    val pem = msg["pubkey"] as String?
                    if (pem == null) {
                        reject(Exception("Missing pubkey"))
                        return@Promise
                    }

                    pubkey = forge.pki.publicKeyFromPem(pem)
                    val id = forge.pki.getPublicKeyFingerprint(pubkey, json("encoding" to "hex"))
                    if (keyId != id) {
                        reject(Exception("Pubkey id mismatch"))
                        return@Promise
                    }
                }

                Crypto.sign(msg["challenge"] as String).then({ signature ->
                    val response = json("type" to TYPE_CHALLENGE_RESPONSE, "response" to signature)

                    if (state != State.PEER_VERIFIED) {
                        forge.random.getBytes(64) { _, bytes ->
                            response["challenge"] = bytes
                            resolve(response)
                        }
                    } else {
                        resolve(response)
                    }
                    Unit
                })
            }
        }

        private fun challengeResponse(msg: Json): Promise<Json> {
            state mustBe State.CHALLENGE_SENT

            return Promise { resolve, reject ->
                if (Crypto.verify(pubkey, challenge!!, msg["signature"] as String)) {
                    state = State.PEER_VERIFIED

                    val challenge = msg["challenge"] as String?
                    if (challenge != null) {
                        challenge(msg).then({ response ->
                            resolve(response)
                        })
                    } else {
                        forge.random.getBytes(32) { _, bytes ->
                            Crypto.sign(bytes).then({ signature ->
                                state = State.KEY_SENT
                                skey = bytes
                                resolve(json("type" to TYPE_SKEY,
                                        "key" to pubkey.encrypt(bytes),
                                        "signature" to signature)) // ˘~˘
                            })
                        }
                        Unit
                    }
                } else {
                    state = State.ERROR
                    reject(IllegalArgumentException())
                }
            }
        }

        private fun skey(msg: Json): Promise<Json> {
            state mustBe State.PEER_VERIFIED

            return Promise { resolve, reject ->
                val key = msg["key"] as String?
                val signature = msg["signature"] as String?

                if (key == null || signature == null) {
                    reject(IllegalArgumentException())
                    return@Promise
                }

                Crypto.obtainUserKeys().then({ pair ->
                    val decoded = pair.privateKey.decrypt(key)
                    if (Crypto.verify(pubkey, decoded, signature)) {
                        skey = decoded
                        state = State.READY
                        resolve(json("type" to TYPE_READY))
                    } else {
                        reject(IllegalArgumentException())
                    }
                })
            }
        }

        private fun ready(): Promise<Json>? {
            state mustBe State.KEY_SENT
            state = State.READY
            History.addEvent(keyId, HistoryImpl.EventEntry.EV_STARTED)
            return null
        }

        private fun message(msg: Json): Promise<Json> {
            state mustBe State.READY

            return Promise { resolve, reject ->
                val content = msg["content"] as String?
                val hmac = msg["hmac"] as String?

                if (content == null || hmac == null) {
                    reject(IllegalArgumentException())
                    return@Promise
                }

                decryptMessage(content, hmac).then({ text ->
                    History.addMessage(keyId, text, false)
                    resolve(json("type" to TYPE_ACK, "id" to msg["id"]))
                }, { cause ->
                    reject(cause)
                })
            }
        }

        fun sendMessage(content: String): Promise<Json> {
            state mustBe State.READY

            return Promise { resolve, _ ->
                val msgId = idCounter++.toString()
                encryptMessage(content).then({ (ciphertext, hmac) ->
                    resolve(json("type" to TYPE_MESSAGE, "content" to ciphertext, "hmac" to hmac, "msgId" to msgId))
                })
            }
        }

        private fun ack(msgId: String?): Promise<Json>? {
            sentQueue[msgId]?.let { content ->
                History.addMessage(keyId, content, true)
                sentQueue.remove(msgId)
            }

            return null
        }

        fun reset(): Promise<Json>? {
            if (state != State.NONE) {
                state = State.NONE
                challenge = null
                return Promise.resolve(json("type" to TYPE_RESET))
            }

            return null
        }

        private fun encryptMessage(data: String): Promise<Pair<String, String>> { // ciphertext to MAC
            return Promise { resolve, _ ->
                forge.random.getBytes(16) { _, iv ->
                    val aes = forge.cipher.createCipher("aes", skey)
                    aes.start(iv)
                    aes.update(data)
                    aes.finish()
                    val result = iv + aes.output.bytes()
                    val mac = computeMac(result)
                    resolve(result to mac)
                }
                Unit
            }
        }

        private fun decryptMessage(data: String, hmac: String): Promise<String> { // cleartext, reject on hmac mismatch
            return Promise { resolve, reject ->
                if (computeMac(data) != hmac) {
                    reject(Exception("Message authentication failed"))
                    return@Promise
                }

                val aes = forge.cipher.createDecipher("aes", skey)
                aes.start(data.slice(0..15))
                aes.update(data.substring(16))
                aes.finish()
                resolve(aes.output.bytes())
            }
        }

        private fun computeMac(data: String): String {
            return forge.hmac.create().let { hmac ->
                hmac.start("sha256", skey)
                hmac.update(data)
                hmac.digest().bytes()
            }
        }

        fun fail(): Any? {
            state = State.ERROR
            return json("dio" to "non lo so, vedremo")
        }

        enum class State {
            NONE, ID_SENT, CHALLENGE_SENT, PEER_VERIFIED, KEY_SENT, READY, ERROR;

            infix fun mustBe(s: State) {
                if (this != s) throw IllegalStateException("Expected session state: ${s.name}, found: $name")
            }

            infix fun mustBe(s: Array<State>) {
                if (this !in s) throw IllegalStateException("Expected session states: ${s.joinToString { it.name }}, found: $name")
            }
        }

        companion object {
            const val TYPE_NEWCHAT = "newchat"
            const val TYPE_CHALLENGE = "challenge"
            const val TYPE_CHALLENGE_RESPONSE = "challenge-response"
            const val TYPE_SKEY = "skey"
            const val TYPE_READY = "ready"
            const val TYPE_MESSAGE = "message"
            const val TYPE_RESET = "reset"
            const val TYPE_ACK = "ack"
        }
    }
    */
}