package com.gemini.agent

import android.content.Context
import android.system.Os
import java.io.File
import java.util.zip.ZipInputStream

const val FAKE_USR = "/data/data/com.termux/files/usr"
const val FAKE_HOME = "/data/data/com.termux/files/home"

class Dirs(ctx: Context) {
    val root = File(ctx.filesDir, "termux")
    val usr = File(root, "usr")
    val home = File(root, "home")
    val tmp = File(root, "tmp")
    val prootDir = File(root, "proot-bin")
    val proot = File(prootDir, "proot")
    val loader = File(prootDir, "loader")
    val ready = File(root, ".ready")
}

object SetupManager {
    const val SETUP_VERSION = "2"
    fun isReady(ctx: Context) = Dirs(ctx).ready.let { it.exists() && it.readText() == SETUP_VERSION }

    fun install(ctx: Context, progress: (Int, String) -> Unit) {
        val d = Dirs(ctx)
        d.root.deleteRecursively()
        d.usr.mkdirs(); d.home.mkdirs(); d.tmp.mkdirs(); d.prootDir.mkdirs()

        progress(5, "Extrag bootstrap-ul Termux...")
        val symlinks = mutableListOf<Pair<String, String>>()
        ctx.assets.open("bootstrap-aarch64.zip").buffered().use { raw ->
            ZipInputStream(raw).use { zin ->
                var e = zin.nextEntry
                while (e != null) {
                    val name = e.name
                    if (name == "SYMLINKS.txt") {
                        zin.readBytes().toString(Charsets.UTF_8).lines().forEach { line ->
                            val p = line.split("\u2190")
                            if (p.size == 2) symlinks += p[0] to p[1]
                        }
                    } else if (e.isDirectory) {
                        File(d.usr, name).mkdirs()
                    } else {
                        val f = File(d.usr, name)
                        f.parentFile?.mkdirs()
                        f.outputStream().use { zin.copyTo(it) }
                        if (name.startsWith("bin/") || name.startsWith("libexec/") || name.startsWith("lib/apt/") ||
                            name.endsWith(".sh") || name.startsWith("etc/termux/")) f.setExecutable(true, false)
                    }
                    zin.closeEntry()
                    e = zin.nextEntry
                }
            }
        }

        progress(60, "Creez legaturile simbolice...")
        symlinks.forEach { (target, link) ->
            val l = File(d.usr, link)
            l.parentFile?.mkdirs(); l.delete()
            Os.symlink(target, l.path)
        }

        progress(75, "Instalez proot...")
        fun copyAsset(asset: String, to: File, exec: Boolean) {
            ctx.assets.open(asset).use { i -> to.outputStream().use { i.copyTo(it) } }
            if (exec) to.setExecutable(true, false)
        }
        copyAsset("proot/proot", d.proot, true)
        copyAsset("proot/loader", d.loader, true)
        File(d.usr, "lib").mkdirs()
        (ctx.assets.list("proot/lib") ?: emptyArray()).forEach { n ->
            copyAsset("proot/lib/$n", File(d.usr, "lib/$n"), false)
        }

        progress(90, "Scriptul de prima rulare...")
        File(d.home, "firstrun.sh").writeText(
            """
            #!/bin/sh
            S=${'$'}PREFIX/etc/termux/termux-bootstrap/second-stage/termux-bootstrap-second-stage.sh
            [ -f "${'$'}S" ] && sh "${'$'}S"
            echo "== Instalez nodejs, python, git =="
            pkg install -y nodejs python git || exit 1
            echo "== Instalez Gemini CLI =="
            npm install -g @google/gemini-cli || exit 1
            touch ${'$'}HOME/.gemini_installed
            echo "== Gata. Scrie: gemini =="
            """.trimIndent() + "\n"
        )
        d.ready.writeText(SETUP_VERSION)
        progress(100, "Gata")
    }
}
