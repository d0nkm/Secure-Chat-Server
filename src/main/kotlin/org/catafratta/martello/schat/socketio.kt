package org.catafratta.martello.schat

external val io: SocketIO

external interface SocketIO {
    fun connect(namespace: String = definedExternally, options: dynamic = definedExternally): Socket
}

external interface Socket {
    fun emit(name: String, data: dynamic = definedExternally, ackListener: (result: dynamic) -> Unit = definedExternally)
    fun on(name: String, listener: () -> Unit)
    fun on(name: String, listener: (data: dynamic) -> Unit)
    fun on(name: String, listener: (data: dynamic, ack: (dynamic) -> Unit) -> Unit)
}