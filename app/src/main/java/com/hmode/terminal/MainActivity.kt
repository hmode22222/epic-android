package com.hmode.terminal

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

class MainActivity : Activity(), TerminalSessionClient, TerminalViewClient {

    companion object {
        private const val TAG = "EpicTerminal"
    }

    private lateinit var terminalView: TerminalView
    private lateinit var terminalSession: TerminalSession

    @Volatile
    private var ctrlDown = false

    @Volatile
    private var altDown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        terminalView = TerminalView(this, null)
        terminalView.setTerminalViewClient(this)
        terminalView.setTextSize(18)
        terminalView.isFocusable = true
        terminalView.isFocusableInTouchMode = true

        terminalSession = TerminalSession(
            "/system/bin/sh",
            filesDir.absolutePath,
            arrayOf(),
            buildEnvironment(),
            5000,
            this
        )

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.BLACK)

        terminalView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )

        root.addView(terminalView)
        root.addView(createExtraKeysBar())

        setContentView(root)

        terminalView.attachSession(terminalSession)

        terminalView.post {
            terminalView.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::terminalSession.isInitialized) {
            terminalSession.finishIfRunning()
        }
    }

    private fun buildEnvironment(): Array<String> {
        val home = filesDir.absolutePath
        return arrayOf(
            "HOME=$home",
            "TERM=xterm-256color",
            "PATH=/sbin:/system/sbin:/system/bin:/system/xbin",
            "ANDROID_ROOT=/system",
            "ANDROID_DATA=/data",
            "ANDROID_STORAGE=/storage",
            "EXTERNAL_STORAGE=/sdcard",
            "SHELL=/system/bin/sh",
            "LANG=en_US.UTF-8",
            "PWD=$home",
            "TMPDIR=$home/cache"
        )
    }

    private fun createExtraKeysBar(): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER
        bar.setBackgroundColor(Color.parseColor("#101418"))

        val buttonParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        buttonParams.setMargins(2, 6, 2, 6)

        fun addKey(label: String, onTap: () -> Unit): Button {
            val button = Button(this)
            button.text = label
            button.textSize = 13f
            button.isAllCaps = false
            button.setTextColor(Color.parseColor("#e8e8e8"))
            button.setBackgroundColor(Color.parseColor("#1f2730"))
            button.setOnClickListener { onTap() }
            bar.addView(button, buttonParams)
            return button
        }

        addKey("ESC") { terminalSession.write("\u001b") }
        addKey("TAB") { terminalSession.write("\t") }

        val ctrlButton = addKey("CTRL") {
            ctrlDown = !ctrlDown
            paintToggle(ctrlButton, ctrlDown)
        }
        val altButton = addKey("ALT") {
            altDown = !altDown
            paintToggle(altButton, altDown)
        }

        addKey("\u2191") { terminalView.handleKeyCode(KeyEvent.KEYCODE_DPAD_UP, 0) }
        addKey("\u2193") { terminalView.handleKeyCode(KeyEvent.KEYCODE_DPAD_DOWN, 0) }
        addKey("\u2190") { terminalView.handleKeyCode(KeyEvent.KEYCODE_DPAD_LEFT, 0) }
        addKey("\u2192") { terminalView.handleKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT, 0) }

        addKey("|") { terminalSession.write("|") }
        addKey("&") { terminalSession.write("&") }

        return bar
    }

    private fun paintToggle(button: Button, active: Boolean) {
        button.setBackgroundColor(
            if (active) Color.parseColor("#3f6b9b") else Color.parseColor("#1f2730")
        )
    }

    // ---------------------------------------------------------------------
    // TerminalSessionClient
    // ---------------------------------------------------------------------

    override fun onTextChanged(changedSession: TerminalSession) {
        terminalView.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text))
        } catch (e: Exception) {
            logError(TAG, "Failed to copy text to clipboard: ${e.message}")
        }
    }

    override fun onPasteTextFromClipboard(session: TerminalSession) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip ?: return
            if (clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(this).toString()
                if (text.isNotEmpty()) session.write(text)
            }
        } catch (e: Exception) {
            logError(TAG, "Failed to paste text from clipboard: ${e.message}")
        }
    }

    override fun onBell(session: TerminalSession) {
    }

    override fun onColorsChanged(session: TerminalSession) {
    }

    override fun onTerminalCursorStateChange(state: Boolean) {
    }

    override fun getTerminalCursorStyle(): Int? = null

    override fun logError(tag: String, message: String) = Log.e(tag, message)

    override fun logWarn(tag: String, message: String) = Log.w(tag, message)

    override fun logInfo(tag: String, message: String) = Log.i(tag, message)

    override fun logDebug(tag: String, message: String) = Log.d(tag, message)

    override fun logVerbose(tag: String, message: String) = Log.v(tag, message)

    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) =
        Log.e(tag, message, e)

    override fun logStackTrace(tag: String, e: Exception) = Log.e(tag, Log.getStackTraceString(e))

    // ---------------------------------------------------------------------
    // TerminalViewClient
    // ---------------------------------------------------------------------

    override fun onScale(scale: Float): Float = scale

    override fun onSingleTapUp(e: MotionEvent) {
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun shouldEnforceCharBasedInput(): Boolean = false

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) {
    }

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean = ctrlDown

    override fun readAltKey(): Boolean = altDown

    override fun readShiftKey(): Boolean = false

    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean =
        false

    override fun onEmulatorSet() {
    }
}
