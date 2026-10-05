package com.gemini.agent

import android.content.Context
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File

object Session {
    private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    fun create(ctx: Context, apiKey: String, client: TerminalSessionClient): TerminalSession {
        val d = Dirs(ctx)
        d.tmp.mkdirs()
        val first = !File(d.home, ".gemini_installed").exists()
        val inner = if (first) "sh \$HOME/firstrun.sh; exec bash -l" else "exec bash -l"

        // radacina ramane "/" (sistemul Android vizibil direct); legam doar folderul com.termux din rootfs
        val termuxHost = File(d.rootfs, "data/data/com.termux").path
        val cmd = listOf(
            d.proot.path, "-r", "/", "--link2symlink", "--kill-on-exit",
            "-b", "$termuxHost:/data/data/com.termux",
            "-w", FAKE_HOME,
            "$FAKE_USR/bin/env", "-u", "LD_LIBRARY_PATH",
            "$FAKE_USR/bin/bash", "-lc", inner
        )

        // Daca proot se opreste, ramanem intr-un shell Android (depanare) cu variabilele ROOTFS si PROOT
        val script = "export ROOTFS=${q(d.rootfs.path)} PROOT=${q(d.proot.path)}; " +
            cmd.joinToString(" ") { q(it) } +
            "; echo; echo \"[proot a iesit cu codul \$? - shell de depanare, scrie: ls -ld /system]\"; exec /system/bin/sh"

        val env = arrayOf(
            "PATH=$FAKE_USR/bin:/system/bin",
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
        return TerminalSession("/system/bin/sh", d.home.path, arrayOf("sh", "-c", script), env, 2000, client)
    }
}
