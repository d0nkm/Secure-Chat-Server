package org.catafratta.martello.schat

import org.w3c.dom.events.MouseEvent

external interface MdDialog {
    fun <T> show(options: dynamic): AngularPromise<*, T>
    fun alert(): MdDialogPreset
    fun confirm(): MdDialogPreset
    fun prompt(): MdDialogPreset

    fun hide(response: dynamic = definedExternally): AngularPromise<*, *>
    fun cancel(response: dynamic = definedExternally): AngularPromise<*, *>
}

external interface MdDialogPreset {
    fun title(title: String): MdDialogPreset
    fun textContent(content: String): MdDialogPreset
    fun htmlContent(html: String): MdDialogPreset
    fun placeholder(placeholder: String): MdDialogPreset
    fun initialValue(initialValue: String): MdDialogPreset
    fun ok(okText: String): MdDialogPreset
    fun cancel(cancelText: String): MdDialogPreset
    fun theme(theme: String): MdDialogPreset
    fun targetEvent(event: MouseEvent): MdDialogPreset
}

external interface MdMedia {
    operator fun invoke(query: String)
}

object MdQueries {
    const val XS: String = "xs"                 // (max-width: 599px)
    const val GT_XS: String = "gt-xs"           // (min-width: 600px)
    const val SM: String = "sm"                 // (min-width: 600px) and (max-width: 959px)
    const val GT_SM: String = "gt-sm"           // (min-width: 960px)
    const val MD: String = "md"                 // (min-width: 960px) and (max-width: 1279px)
    const val GT_MD: String = "gt-md"           // (min-width: 1280px)
    const val LG: String = "lg"                 // (min-width: 1280px) and (max-width: 1919px)
    const val GT_LG: String = "gt-lg"           // (min-width: 1920px)
    const val XL: String = "xl"                 // (min-width: 1920px)
    const val LANDSCAPE: String = "landscape"   // landscape
    const val PORTRAIT: String = "portrait"     // portrait
    const val PRINT: String = "print"           // print
}