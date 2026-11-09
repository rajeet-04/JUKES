package com.example.juke.desktop

import java.io.File

/**
 * Makes the installed Windows app findable from everywhere, per user (no admin): juke:// links, `JUKE` in Win+R / the
 * Start search / a terminal (App Paths), and the install folder on the user PATH. Runs at every start and only writes
 * what is missing or has moved. Best effort: a failure here never affects the app. Other OSes: no-op
 * (macOS and Linux installers register the app with Spotlight / the menu themselves).
 */
object WindowsIntegration {
    fun register() {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return
        runCatching {
            val exe = ProcessHandle.current().info().command().orElse(null)?.let(::File) ?: return
            if (!exe.name.equals("JUKE.exe", true)) return // `gradle run` from the IDE: nothing to register
            val q = "\"${exe.path}\""
            fun reg(vararg a: String) = ProcessBuilder("reg", *a, "/f").redirectErrorStream(true).start().also { it.inputStream.readBytes() }.waitFor()
            val proto = "HKCU\\Software\\Classes\\juke"
            reg("add", proto, "/ve", "/d", "URL:JUKE")
            reg("add", proto, "/v", "URL Protocol", "/d", "")
            reg("add", "$proto\\DefaultIcon", "/ve", "/d", "$q,0")
            reg("add", "$proto\\shell\\open\\command", "/ve", "/d", "$q \"%1\"")
            val ap = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\App Paths\\JUKE.exe"
            reg("add", ap, "/ve", "/d", exe.path)
            reg("add", ap, "/v", "Path", "/d", exe.parent)
            // user PATH, so `JUKE` works in a terminal; re-run safe (skips when already there)
            val dir = exe.parent.replace("'", "''")
            ProcessBuilder("powershell", "-NoProfile", "-Command",
                "\$p=[Environment]::GetEnvironmentVariable('Path','User'); if(-not \$p){\$p=''}; " +
                "\$parts=\$p -split ';' | ? { \$_ -and \$_ -ne '$dir' -and \$_ -notmatch '\\\\JUKE\$' }; " +
                "[Environment]::SetEnvironmentVariable('Path',((\$parts + '$dir') -join ';'),'User')")
                .redirectErrorStream(true).start().also { it.inputStream.readBytes() }.waitFor()
        }.onFailure { android.util.Log.w("WindowsIntegration", "register failed", it) }
    }
}
