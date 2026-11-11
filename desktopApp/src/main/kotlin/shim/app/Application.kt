package android.app

import android.content.Context

open class Application : Context() {
    companion object { val instance: Application by lazy { Application() } }
}
