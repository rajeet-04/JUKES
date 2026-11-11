package androidx.lifecycle

import android.app.Application

open class AndroidViewModel(private val app: Application) : ViewModel() {
    @Suppress("UNCHECKED_CAST") fun <T : Application> getApplication(): T = app as T
}
