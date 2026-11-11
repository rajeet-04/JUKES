package androidx.lifecycle

/** The desktop app is "foreground" while its window is open. */
object ProcessLifecycleOwner : LifecycleOwner {
    private val reg = LifecycleRegistry.createUnsafe(this).also { it.currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle get() = reg
    fun get(): LifecycleOwner = this
    fun setForeground(on: Boolean) { reg.currentState = if (on) Lifecycle.State.RESUMED else Lifecycle.State.CREATED }
}
