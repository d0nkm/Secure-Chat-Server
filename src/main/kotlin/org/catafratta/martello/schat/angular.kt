package org.catafratta.martello.schat

import org.w3c.dom.HTMLElement
import kotlin.js.Json

external val angular: Angular

external interface Angular {
    fun module(name: String, requires: Array<String> = definedExternally, configFn: Function<*> = definedExternally): AngularModule
    fun injector(modules: Array<String>, strictDi: Boolean = definedExternally): AngularInjector
    fun injector(modules: Array<JsClass<*>>, strictDi: Boolean = definedExternally): AngularInjector
    fun element(query: String): dynamic
    fun element(el: HTMLElement): dynamic
    fun <T> extend(dst: T, vararg src: dynamic): T
    fun <T> copy(obj: T): T
}

external interface AngularModule {
    fun run(initFn: Function<*>)
    fun config(configFn: Function<*>)
    fun controller(name: String, constructor: JsClass<*>): AngularModule
    fun service(name: String, constructor: JsClass<*>): AngularModule
}

internal fun AngularModule.componentDeclaration(type: String, name: String,
                                                deps: Array<out String>, constructor: JsClass<*>): AngularModule =
        asDynamic()[type](name, arrayOf(*deps, constructor))

fun AngularModule.controller(name: String, vararg deps: String, constructor: JsClass<*>) =
        componentDeclaration("controller", name, deps, constructor)

fun AngularModule.service(name: String, vararg deps: String, constructor: JsClass<*>) =
        componentDeclaration("service", name, deps, constructor)

fun AngularModule.config(vararg deps: String, configFn: Function<*>) =
        asDynamic()["config"](arrayOf(*deps, configFn))

fun AngularModule.run(vararg deps: String, initFn: Function<*>) =
        asDynamic()["run"](arrayOf(*deps, initFn))

fun eventHandler(handler: (event: ScopeEvent, extras: Array<dynamic>) -> Unit): Function<*> {
    fun _handler(event: ScopeEvent, vararg extras: dynamic) = handler(event, extras.copyOf())
    return ::_handler
}

external interface AngularInjector {
    val modules: Json

    fun get(name: String, caller: String = definedExternally): Any?
    fun <T> invoke(fn: Function<T>, self: Any? = definedExternally, locals: dynamic): T
    fun has(name: String): Boolean
    fun <T : Any> instantiate(constructor: JsClass<T>, locals: dynamic): T
    fun annotate(fn: Function<*>, strictDi: Boolean): Array<String>
}

typealias DeregistrationFunction = () -> Unit

external interface AngularScope : Json {
    val `$id`: Int
    val `$parent`: AngularScope?
    val `$root`: AngularRootScope

    fun `$new`(isolate: Boolean, parent: AngularScope = definedExternally): AngularScope
    fun `$destroy`()

    fun `$eval`(expression: String, locals: dynamic = definedExternally): Any?
    fun `$eval`(function: (AngularScope) -> Any?, locals: dynamic = definedExternally): Any?

    fun `$evalAsync`(expression: String, locals: dynamic = definedExternally)
    fun `$evalAsync`(function: (AngularScope) -> Any?, locals: dynamic = definedExternally)

    fun `$on`(name: String, listener: Function<*> = definedExternally): DeregistrationFunction
    fun `$emit`(name: String, vararg extras: dynamic): ScopeEvent
    fun `$broadcast`(name: String, vararg extras: dynamic): ScopeEvent

    fun `$digest`()

    fun `$apply`()
    fun `$apply`(expression: String)
    fun `$apply`(expression: (AngularScope) -> dynamic)

    fun `$applyAsync`()
    fun `$applyAsync`(expression: String = definedExternally)
    fun `$applyAsync`(expression: (AngularScope) -> dynamic = definedExternally)

    fun `$watch`(expression: String, listener: (newValue: dynamic, oldValue: dynamic, scope: AngularScope) -> Unit,
                 objectEquality: Boolean = definedExternally): DeregistrationFunction
}

external interface AngularRootScope : AngularScope {
    val Scope: (providers: dynamic, instanceCache: dynamic) -> AngularScope
}

external interface ScopeEvent {
    val targetScope: AngularScope
    val currentScope: AngularScope?
    val name: String
    val stopPropagation: () -> Unit
    val preventDefault: () -> Unit
    val defaultPrevented: Boolean
}

typealias PromiseCallback = (dynamic) -> dynamic

external interface AngularQ {
    // operator fun <T> invoke(resolver: (resolve: (value: T) -> Unit, reject: (reason: dynamic) -> Unit) -> Unit): AngularPromise<T>

    fun <N, T> defer(): AngularDeferred<N, T>

    fun <N, T> `when`(value: T, successCallback: PromiseCallback = definedExternally, errorCallback: PromiseCallback = definedExternally,
                      notifyCallback: (N) -> Unit = definedExternally): AngularPromise<N, T>

    fun <N, T> resolve(value: T, successCallback: PromiseCallback = definedExternally, errorCallback: PromiseCallback = definedExternally,
                       notifyCallback: (N) -> Unit = definedExternally): AngularPromise<N, T>

    fun reject(reason: dynamic = definedExternally): AngularPromise<*, *>
    fun <N, T> all(promises: Array<AngularPromise<N, T>>): AngularPromise<N, T>
    fun <N, T> race(promises: Array<AngularPromise<N, T>>): AngularPromise<N, T>
}

operator fun <N, T> AngularQ.invoke(resolver: (resolve: (value: T) -> Unit, reject: (reason: Throwable) -> Unit) -> Unit): AngularPromise<N, T> {
    val deferred = this.defer<N, T>()
    resolver(deferred::resolve, deferred::reject)
    return deferred.promise
}

external interface AngularDeferred<N, R> {
    val promise: AngularPromise<N, R>

    fun resolve(value: R = definedExternally)
    fun reject(reason: Throwable = definedExternally)
    fun notify(value: N = definedExternally)
}

external interface AngularPromise<N, out T> {
    fun <R> then(successCallback: (T) -> R, errorCallback: PromiseCallback = definedExternally,
                 notifyCallback: (N) -> Unit = definedExternally): AngularPromise<N, R>

    fun catch(errorCallback: (Throwable) -> Unit): AngularPromise<*, *>

    fun finally(successCallback: (T) -> dynamic, notifyCallback: (N) -> Unit = definedExternally)
}