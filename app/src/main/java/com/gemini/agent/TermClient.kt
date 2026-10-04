package com.gemini.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

class TermClient(private val ctx: Context) : TerminalViewClient, TerminalSessionClient {
    var view: TerminalView? = null
    var session: TerminalSession? = null

    // --- TerminalSessionClient ---
    override fun onTextChanged(s: TerminalSession) { view?.onScreenUpdated() }
    override fun onTitleChanged(s: TerminalSession) {}
    override fun onSessionFinished(s: TerminalSession) {}
    override fun onCopyTextToClipboard(s: TerminalSession, text: String?) {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("term", text))
    }
    override fun onPasteTextFromClipboard(s: TerminalSession?) {
        val t = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
        if (!t.isNullOrEmpty()) session?.emulator?.paste(t)
    }
    override fun onBell(s: TerminalSession) {}
    override fun onColorsChanged(s: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun setTerminalShellPid(s: TerminalSession, pid: Int) {}
    override fun getTerminalCursorStyle(): Int? = null

    // --- TerminalViewClient ---
    override fun onScale(scale: Float) = scale
    override fun onSingleTapUp(e: MotionEvent?) {
        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        view?.let { imm.showSoftInput(it, 0) }
    }
    override fun shouldBackButtonBeMappedToEscape() = false
    override fun shouldEnforceCharBasedInput() = true
    override fun shouldUseCtrlSpaceWorkaround() = false
    override fun isTerminalViewSelected() = true
    override fun copyModeChanged(copyMode: Boolean) {}
    override fun onKeyDown(keyCode: Int, e: KeyEvent?, s: TerminalSession?) = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent?) = false
    override fun onLongPress(event: MotionEvent?) = false
    override fun readControlKey() = false
    override fun readAltKey() = false
    override fun readShiftKey() = false
    override fun readFnKey() = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, s: TerminalSession?) = false
    override fun onEmulatorSet() {}

    // --- logging (comun ambelor interfete) ---
    override fun logError(tag: String?, message: String?) { Log.e(tag, message ?: "") }
    override fun logWarn(tag: String?, message: String?) { Log.w(tag, message ?: "") }
    override fun logInfo(tag: String?, message: String?) { Log.i(tag, message ?: "") }
    override fun logDebug(tag: String?, message: String?) { Log.d(tag, message ?: "") }
    override fun logVerbose(tag: String?, message: String?) { Log.v(tag, message ?: "") }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { Log.e(tag, message ?: "", e) }
    override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag, "", e) }
}
