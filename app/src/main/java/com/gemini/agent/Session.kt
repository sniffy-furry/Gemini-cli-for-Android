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

        val args = arrayOf(
            "proot",
            "--link2symlink", "--kill-on-exit",
            "-b", "${d.usr.path}:$FAKE_USR",
            "-b", "${d.home.path}:$FAKE_HOME",
            "-w", FAKE_HOME,
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
        return TerminalSession(d.proot.path, d.home.path, args, env, 2000, client)
    }
}
