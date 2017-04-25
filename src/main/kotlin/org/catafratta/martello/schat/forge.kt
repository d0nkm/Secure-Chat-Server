@file:Suppress("Unused")

package org.catafratta.martello.schat

import kotlin.js.Json
import kotlin.js.Promise
import kotlin.js.json
import kotlin.reflect.KParameter

typealias ForgeCallback = (err: dynamic, dynamic) -> Unit

external val forge: Forge

external interface Forge {
    val pki: ForgePki
    val cipher: ForgeCipherModule
    val hmac: ForceHmacModule
    val util: ForgeUtil
    val random: ForgeRandom
    val pkcs5: ForgePkcs5
    val md: ForgeMd
    val options: ForgeOptions
}

external interface ForgeOptions {
    var usePureJavaScript: Boolean
}

external interface ForgePkcs5 {
    fun pbkdf2(password: String, salt: String, numIterations: Int, length: Int): String
    fun pbkdf2(password: String, salt: String, numIterations: Int, length: Int, callback: (err: Throwable?, derivedKey: String) -> Unit): Unit
}

fun ForgePkcs5.pbkdf2Comoda(password: String, salt: String, numIterations: Int, length: Int) = Promise<String> { resolve, reject ->
    pbkdf2(password, salt, numIterations, length, { err, derivedKey ->
        if (err != null) reject(err) else resolve(derivedKey)
    })
}

external interface ForgeUtil {
    fun createBuffer(data: String, encoding: String = definedExternally): ByteStringBuffer
    fun encode64(data: String): String
    fun decode64(data: String): String
}

external interface ForgeRandom {
    fun getBytesSync(count: Int): String
    fun getBytes(count: Int, callback: (err: Throwable?, bytes: String) -> Unit): dynamic
}

fun ForgeRandom.getBytes(count: Int) = Promise<String> { resolve, reject ->
    getBytes(count, { err, bytes ->
        if (err != null) reject(err) else resolve(bytes)
    })
    Unit
}

external interface ForgePki {
    val rsa: ForgeRsa

    fun privateKeyFromPem(pem: String): PrivateKey
    fun publicKeyFromPem(pem: String): PublicKey
    fun privateKeyToPem(key: PrivateKey): String
    fun publicKeyToPem(key: PublicKey): String
    fun publicKeyToPem(key: PrivateKey): String

    fun getPublicKeyFingerprint(key: PublicKey, options: dynamic): String
}

external interface ForgeRsa {
    fun generateKeyPair(bits: Int = definedExternally, e: Int = definedExternally,
                        options: dynamic = definedExternally, callback: (err: Throwable?, pair: KeyPair) -> Unit = definedExternally): Unit
}

fun ForgeRsa.generateKeyPair(bits: Int) = Promise<KeyPair> { resolve, reject ->
    generateKeyPair(bits) { err, pair ->
        if (err != null) reject(err) else resolve(pair)
    }
}

external interface KeyPair {
    val privateKey: PrivateKey
    val publicKey: PublicKey
}

external interface PrivateKey {
    fun decrypt(data: String, scheme: String = definedExternally, schemeOptions: dynamic = definedExternally): String
    fun sign(md: ForgeDigest, scheme: String = definedExternally): String
}

external interface PublicKey {
    fun encrypt(data: String, scheme: String = definedExternally, schemeOptions: dynamic = definedExternally): String
    fun verify(digest: String, signature: String, scheme: String = definedExternally): Boolean
}

fun PublicKey.hexFingerprint(): String = forge.pki.getPublicKeyFingerprint(this, json("encoding" to "hex"))

external interface ForgeMdAlgorithm {
    fun create(): ForgeDigest
}

external interface ForgeMd {
    val sha256: ForgeMdAlgorithm
}

external interface ForgeDigest {
    fun update(data: String): ForgeDigest
    fun digest(): ByteStringBuffer
}

external interface ForceHmacModule {
    fun create(): ForgeHmac
}

external interface ForgeHmac {
    fun start(algorithm: String, key: String)
    fun update(data: String)
    fun digest(): ByteStringBuffer
}

external interface ForgeCipherModule {
    fun createCipher(algorithm: String, key: String): ForgeCipher
    fun createDecipher(algorithm: String, key: String): ForgeCipher
}

external interface ForgeCipher {
    val output: ByteStringBuffer

    fun start(params: Json)
    fun update(data: ByteStringBuffer)
    fun finish(): Boolean
}

fun ForgeCipher.start(iv: String) = start(json("iv" to iv))

external interface ByteStringBuffer {
    fun bytes(): String
    fun toHex(): String
}