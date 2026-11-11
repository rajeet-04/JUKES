package android.net

class Uri private constructor(private val s: String) {
    companion object { fun parse(s: String) = Uri(s); fun fromFile(f: java.io.File) = Uri(f.toURI().toString()) }
    override fun toString() = s
    val scheme: String? get() = s.substringBefore(':', "").ifEmpty { null }
    val path: String? get() = runCatching { java.net.URI(s).path }.getOrNull()
    val host: String? get() = runCatching { java.net.URI(s).host }.getOrNull()
    val lastPathSegment: String? get() = path?.trimEnd('/')?.substringAfterLast('/')
    override fun equals(other: Any?) = other is Uri && other.s == s
    override fun hashCode() = s.hashCode()
}
class Network
class NetworkCapabilities { companion object { const val NET_CAPABILITY_INTERNET = 12; const val TRANSPORT_CELLULAR = 0; const val TRANSPORT_WIFI = 1 }; fun hasCapability(c: Int) = true; fun hasTransport(t: Int) = t == TRANSPORT_WIFI }
class ConnectivityManager {
    val activeNetwork: Network? = Network()
    fun getNetworkCapabilities(n: Network?): NetworkCapabilities? = NetworkCapabilities()
    fun registerDefaultNetworkCallback(cb: NetworkCallback) {}
    fun unregisterNetworkCallback(cb: NetworkCallback) {}
    open class NetworkCallback { open fun onAvailable(network: Network) {}; open fun onLost(network: Network) {}; open fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {}; open fun onBlockedStatusChanged(network: Network, blocked: Boolean) {} }
}
