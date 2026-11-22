package com.example.juke.network

/** An error whose message was written for people: [userMessage] shows it as is. */
class ShownException(message: String) : Exception(message)

/**
 * What to put on screen for this error, never its raw message (URLs, socket timeouts and server
 * bodies used to leak into the UI). Known kinds anywhere in the cause chain get a plain sentence;
 * anything else gets [fallback], which should say what failed.
 */
fun Throwable.userMessage(fallback: String): String {
    val chain = generateSequence(this) { it.cause }.take(10).toList()
    chain.firstOrNull { it is ShownException }?.message?.let { return it }
    if (chain.any { it is java.net.SocketTimeoutException || it is io.ktor.client.plugins.HttpRequestTimeoutException }) {
        return "That took too long. Check your connection and try again."
    }
    if (this is OfflineException || isOffline()) return "You're offline. Check your connection and try again."
    if (chain.any { it is javax.net.ssl.SSLException }) return "Couldn't connect securely. Try again."
    for (e in chain) {
        val code = (e as? io.ktor.client.plugins.ResponseException)?.response?.status?.value
            // ponytail: some throws only carry the code in their text ("status 503"); type them if this grows.
            ?: STATUS.find(e.message.orEmpty())?.groupValues?.get(1)?.toInt()
        httpMessage(code)?.let { return it }
    }
    return fallback
}

private val STATUS = Regex("""status (\d{3})""")

private fun httpMessage(code: Int?) = when (code) {
    401, 403 -> "Access was refused. Try again in a moment."
    404 -> "That couldn't be found."
    429 -> "Too many requests. Wait a moment and try again."
    in 500..599 -> "The service is having trouble. Try again later."
    else -> null
}
