package com.gemini.agent

import android.content.Context
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File

object Session {
    fun create(ctx: Context, apiKey: String, client: TerminalSessionClient): TerminalSession {
        val d = Dirs(ctx)
        val first = !File(d.home, ".gemini_installed").exists()
        val inner = if (first) "sh \$HOME/firstrun.sh; exec bash -l" else "exec bash -l"

        // punctele de montare trebuie sa existe in rootfs, altfel proot nu le poate lega
        listOf("dev", "proc", "sys", "tmp").forEach { File(d.rootfs, it).mkdirs() }
        val dirBinds = listOf("/system", "/apex", "/vendor", "/product", "/odm", "/system_ext")
            .filter { File(it).exists() }
        dirBinds.forEach { File(d.rootfs, it).mkdirs() }
        val fileBinds = listOf("/linkerconfig/ld.config.txt", "/linkerconfig/com.android.art/ld.config.txt")
            .filter { File(it).exists() }
        fileBinds.forEach {
            val mp = File(d.rootfs, it)
            mp.parentFile?.mkdirs()
            if (!mp.exists()) mp.createNewFile()
        }
        val binds = dirBinds + fileBinds

        val args = mutableListOf(
            "proot", "-r", d.rootfs.path,
            "--link2symlink", "--kill-on-exit"
        )
        binds.forEach { args += listOf("-b", it) }
        args += listOf(
            "-w", FAKE_HOME,
            "$FAKE_USR/bin/env", "-u", "LD_LIBRARY_PATH",
            "$FAKE_USR/bin/bash", "-lc", inner
        )
        val env = arrayOf(
            "PATH=$FAKE_USR/bin",
            "HOME=$FAKE_HOME",
            "PREFIX=$FAKE_USR",
            "TMPDIR=$FAKE_USR/tmp",
            "LANG=en_US.UTF-8",
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "GEMINI_API_KEY=$apiKey",
            "PROOT_TMP_DIR=${d.tmp.path}",
            "PROOT_LOADER=${d.loader.path}",
            "PROOT_NO_SECCOMP=1",
            "LD_LIBRARY_PATH=${File(d.usr, "lib").path}"
        )
        return TerminalSession(d.proot.path, d.home.path, args.toTypedArray(), env, 2000, client)
    }
}
