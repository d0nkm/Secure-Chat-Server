package org.catafratta.martello.schat

import org.w3c.dom.get
import kotlin.browser.localStorage

class CryptoImpl(`$q`: AngularQ) {
    private val pairDeferred = `$q`.defer<Unit, KeyPair>()
    private var userKeys: KeyPair? = null

    fun obtainUserKeys(): AngularPromise<Unit, KeyPair> {
        if (userKeys != null) return pairDeferred.promise

        if (localStorage["userKeys"] != null) {
            userKeys = localStorage["userKeys"]?.let { encoded ->
                JSON.parse<KeyPair>(encoded) { key, value ->
                    when (key) {
                        "publicKey" -> forge.pki.publicKeyFromPem(value as String)
                        "privateKey" -> forge.pki.privateKeyFromPem(value as String)
                        else -> value
                    }
                }
            }

            pairDeferred.resolve(userKeys!!)
        } else {
            forge.pki.rsa.generateKeyPair(2048) { err, pair: KeyPair ->
                if (err != null) {
                    pairDeferred.reject(err)
                    return@generateKeyPair
                }

                userKeys = pair
                localStorage.setItem("userKeys", JSON.stringify(userKeys) { key, value ->
                    if (key.startsWith("\$\$")) return@stringify undefined

                    when (key) {
                        "publicKey" -> forge.pki.publicKeyToPem(value.unsafeCast<PublicKey>())
                        "privateKey" -> forge.pki.privateKeyToPem(value.unsafeCast<PrivateKey>())
                        else -> value
                    }
                })

                pairDeferred.resolve(userKeys!!)
            }
        }

        return pairDeferred.promise
    }

    fun getUserPublicPem(): AngularPromise<Unit, String> {
        return obtainUserKeys().then({ pair: KeyPair ->
            forge.pki.publicKeyToPem(pair.publicKey)
        })
    }

    fun sign(data: String): AngularPromise<Unit, String> {
        return obtainUserKeys().then({ pair: KeyPair -> pair.privateKey.sign(forge.md.sha256.create().update(data)) })
    }

    fun verify(key: PublicKey, data: String, signature: String): Boolean {
        val sha2 = forge.md.sha256.create()
        return key.verify(sha2.update(data).digest().bytes(), signature)
    }
}