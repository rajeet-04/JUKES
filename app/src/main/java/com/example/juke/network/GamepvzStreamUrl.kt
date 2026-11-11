package com.example.juke.network

import java.net.URI
import java.net.URLDecoder
import java.util.Base64

/** Use the signed CDN URL supplied in Gamepvz's download wrapper when recognized. */
internal fun gamepvzStreamUrl(proxyUrl: String): String {
    return try {
        val proxy = URI(proxyUrl)
        if (proxy.scheme != "https" || proxy.host != "gamepvz.com" ||
            proxy.path != "/api/download/dl" || proxy.userInfo != null) return proxyUrl
        val encoded = proxy.rawQuery?.split('&')?.firstOrNull { it.startsWith("url=") }
            ?.substringAfter('=') ?: return proxyUrl
        val decoded = String(Base64.getUrlDecoder().decode(URLDecoder.decode(encoded, "UTF-8")), Charsets.UTF_8)
        val cdn = URI(decoded)
        if (cdn.scheme == "https" && cdn.userInfo == null &&
            cdn.host?.startsWith("cdn-spotify") == true && cdn.host.endsWith(".zm.io.vn") &&
            cdn.path.startsWith("/download/")) decoded else proxyUrl
    } catch (_: IllegalArgumentException) {
        proxyUrl
    } catch (_: java.net.URISyntaxException) {
        proxyUrl
    }
}
