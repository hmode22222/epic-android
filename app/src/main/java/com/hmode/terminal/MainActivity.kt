package com.hmode.terminal

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File
import java.io.IOException

class MainActivity : Activity(), TerminalSessionClient, TerminalViewClient {

    companion object {
        private const val TAG = "EpicTerminal"

        private const val BUSYBOX_ASSET_DIR = "busybox"
        private const val BUSYBOX_NAME = "busybox"

        private const val PROOT_ASSET_DIR = "proot"
        private const val PROOT_NAME = "proot"

        private const val ROOTFS_ASSET_DIR = "rootfs"
        private const val ROOTFS_TARBALL = "alpine.tar.gz"

        private const val QUICK_EXIT_MS = 15000L
    }

    private lateinit var terminalView: TerminalView
    private lateinit var errorView: TextView
    private var terminalSession: TerminalSession? = null
    private var sessionStartedAt = 0L

    @Volatile
    private var ctrlDown = false

    @Volatile
    private var altDown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                File(filesDir, "crash.log").writeText(Log.getStackTraceString(throwable))
            } catch (_: Exception) {
            }
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.BLACK)

        errorView = TextView(this)
        errorView.setTextColor(Color.parseColor("#ff5555"))
        errorView.textSize = 12f
        errorView.setPadding(24, 24, 24, 24)
        errorView.visibility = View.GONE
        root.addView(
            errorView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        terminalView = TerminalView(this, null)
        terminalView.setTerminalViewClient(this)
        terminalView.setTextSize(18)
        terminalView.isFocusable = true
        terminalView.isFocusableInTouchMode = true
        terminalView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )

        root.addView(terminalView)
        root.addView(createExtraKeysBar())

        setContentView(root)

        try {
            setupTerminal()
            terminalView.post {
                terminalView.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
            }
        } catch (e: Exception) {
            showStartupError(e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        terminalSession?.finishIfRunning()
    }

    /**
     * Start a terminal session. The preferred setup is proot + Alpine minirootfs which gives a
     * real Linux environment (with apk package manager). If that is not available for the device
     * ABI (or fails), fall back to plain busybox ash.
     */
    private fun setupTerminal() {
        logToFile("=== startup " + java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date()) + " ===")
        val busyboxPath = extractBusybox()
        logToFile("busybox at $busyboxPath")
        try {
            if (setupProotTerminal(busyboxPath)) {
                logToFile("proot terminal started")
                return
            }
            logToFile("proot not available for this ABI, using busybox")
        } catch (e: Exception) {
            logToFile("proot setup failed: $e")
            Log.w(TAG, "proot setup failed, falling back to busybox", e)
        }
        setupBusyboxTerminal(busyboxPath)
        logToFile("busybox terminal started")
    }

    /** Run the Alpine rootfs under proot. Returns false if no proot binary for this ABI. */
    private fun setupProotTerminal(busyboxPath: String): Boolean {
        val prootPath = extractProot() ?: return false
        logToFile("proot at $prootPath")

        val rootfs = extractRootfs(busyboxPath)
        logToFile("rootfs at ${rootfs.absolutePath}")

        val prootTmpDir = File(filesDir, "proot_tmp")
        prootTmpDir.mkdirs()

        val env = arrayOf(
            "HOME=/root",
            "TERM=xterm-256color",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "SHELL=/bin/sh",
            "LANG=C.UTF-8",
            "PWD=/root",
            "TMPDIR=/tmp",
            "PROOT_TMP_DIR=${prootTmpDir.absolutePath}"
        )

        val args = arrayOf(
            "--link2symlink",
            "-0",
            "-r", rootfs.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-w", "/root",
            "/bin/sh"
        )

        startSession(prootPath, args, File(rootfs, "root").absolutePath, env)
        return true
    }

    /** Plain busybox ash terminal, used when proot is not available. */
    private fun setupBusyboxTerminal(busyboxPath: String) {
        val homeDir = File(filesDir, "home")
        val binDir = File(filesDir, "bin")
        val tmpDir = File(filesDir, "tmp")
        val etcDir = File(filesDir, "etc")
        homeDir.mkdirs()
        binDir.mkdirs()
        tmpDir.mkdirs()
        etcDir.mkdirs()

        writeProfile(File(etcDir, "profile"))

        val install = ProcessBuilder(busyboxPath, "--install", "-s", binDir.absolutePath)
            .redirectErrorStream(true)
            .start()
        val installOutput = install.inputStream.readBytes()
        install.waitFor()
        if (install.exitValue() != 0) {
            Log.w(TAG, "busybox --install failed: " + String(installOutput))
        }

        val home = homeDir.absolutePath
        val env = arrayOf(
            "HOME=$home",
            "TERM=xterm-256color",
            "PATH=${binDir.absolutePath}:/system/bin:/system/xbin:/sbin",
            "ANDROID_ROOT=/system",
            "ANDROID_DATA=/data",
            "ANDROID_STORAGE=/storage",
            "EXTERNAL_STORAGE=/storage/emulated/0",
            "SHELL=$busyboxPath",
            "LANG=en_US.UTF-8",
            "PWD=$home",
            "TMPDIR=${tmpDir.absolutePath}",
            "ENV=${File(etcDir, "profile").absolutePath}"
        )

        startSession(busyboxPath, arrayOf("sh"), home, env)
    }

    private fun startSession(cmd: String, args: Array<String>, cwd: String, env: Array<String>) {
        sessionStartedAt = SystemClock.uptimeMillis()
        val session = TerminalSession(cmd, cwd, args, env, 5000, this)
        terminalSession = session
        terminalView.attachSession(session)
    }

    /** Copy the static busybox binary matching the device ABI out of assets. */
    private fun extractBusybox(): String {
        val dest = File(filesDir, BUSYBOX_NAME)
        if (dest.isFile && dest.length() > 100000L) {
            return dest.absolutePath
        }
        val abi = Build.SUPPORTED_ABIS.firstOrNull { assetExists("$BUSYBOX_ASSET_DIR/$it/$BUSYBOX_NAME") }
            ?: throw IOException("No busybox binary for ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
        dest.parentFile?.mkdirs()
        assets.open("$BUSYBOX_ASSET_DIR/$abi/$BUSYBOX_NAME").use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        if (!dest.setExecutable(true, false)) {
            throw IOException("Failed to make $dest executable")
        }
        logToFile("extracted busybox for $abi")
        return dest.absolutePath
    }

    /** Copy the static proot binary matching the device ABI, or null if none exists. */
    private fun extractProot(): String? {
        val dest = File(filesDir, PROOT_NAME)
        if (dest.isFile && dest.length() > 500000L) {
            return dest.absolutePath
        }
        val abi = Build.SUPPORTED_ABIS.firstOrNull { assetExists("$PROOT_ASSET_DIR/$it/$PROOT_NAME") }
            ?: return null
        dest.parentFile?.mkdirs()
        assets.open("$PROOT_ASSET_DIR/$abi/$PROOT_NAME").use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        if (!dest.setExecutable(true, false)) {
            throw IOException("Failed to make $dest executable")
        }
        logToFile("extracted proot for $abi")
        return dest.absolutePath
    }

    /** Extract the Alpine minirootfs for the device ABI into filesDir/rootfs. */
    private fun extractRootfs(busyboxPath: String): File {
        val rootfs = File(filesDir, "rootfs")
        val marker = File(rootfs, "etc/alpine-release")
        if (marker.isFile) {
            return rootfs
        }
        val abi = Build.SUPPORTED_ABIS.firstOrNull { assetExists("$ROOTFS_ASSET_DIR/$it/$ROOTFS_TARBALL") }
            ?: throw IOException("No rootfs for ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")

        val tarball = File(filesDir, ROOTFS_TARBALL)
        assets.open("$ROOTFS_ASSET_DIR/$abi/$ROOTFS_TARBALL").use { input ->
            tarball.outputStream().use { output -> input.copyTo(output) }
        }
        rootfs.mkdirs()

        val extract = ProcessBuilder(busyboxPath, "tar", "-xzf", tarball.absolutePath, "-C", rootfs.absolutePath)
            .redirectErrorStream(true)
            .start()
        val extractOutput = extract.inputStream.readBytes()
        extract.waitFor()
        tarball.delete()

        if (extract.exitValue() != 0) {
            throw IOException("rootfs extraction failed: " + String(extractOutput))
        }
        if (!marker.isFile) {
            throw IOException("rootfs extraction produced no etc/alpine-release")
        }
        File(rootfs, "etc/resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
        logToFile("extracted rootfs for $abi")
        return rootfs
    }

    private fun assetExists(path: String): Boolean = try {
        assets.open(path).use { }
        true
    } catch (e: IOException) {
        false
    }

    private fun writeProfile(profile: File) {
        profile.writeText(
            "# Epic Terminal profile, sourced by busybox ash on interactive shell start.\n" +
                "export PS1='\\u@\\h:\\w\\\$ '\n" +
                "alias ll='ls -l'\n" +
                "alias la='ls -la'\n" +
                "alias grep='grep --color'\n"
        )
    }

    private fun logToFile(message: String) {
        try {
            File(filesDir, "startup.log").appendText(message + "\n")
        } catch (_: Exception) {
        }
    }

    private fun showStartupError(e: Exception) {
        Log.e(TAG, "Failed to start terminal", e)
        terminalView.visibility = View.GONE
        errorView.text = "Failed to start terminal:\n\n${Log.getStackTraceString(e)}"
        errorView.visibility = View.VISIBLE
    }

    private fun createExtraKeysBar(): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER
        bar.setBackgroundColor(Color.parseColor("#101418"))

        val buttonParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        buttonParams.setMargins(2, 6, 2, 6)

        fun addKey(label: String): Button {
            val button = Button(this)
            button.text = label
            button.textSize = 13f
            button.isAllCaps = false
            button.setTextColor(Color.parseColor("#e8e8e8"))
            button.setBackgroundColor(Color.parseColor("#1f2730"))
            bar.addView(button, buttonParams)
            return button
        }

        val escKey = addKey("ESC")
        escKey.setOnClickListener { terminalSession?.write("\u001b") }

        val tabKey = addKey("TAB")
        tabKey.setOnClickListener { terminalSession?.write("\t") }

        val ctrlButton = addKey("CTRL")
        ctrlButton.setOnClickListener {
            ctrlDown = !ctrlDown
            paintToggle(ctrlButton, ctrlDown)
        }

        val altButton = addKey("ALT")
        altButton.setOnClickListener {
            altDown = !altDown
            paintToggle(altButton, altDown)
        }

        val upKey = addKey("\u2191")
        upKey.setOnClickListener { terminalSession?.let { terminalView.handleKeyCode(KeyEvent.KEYCODE_DPAD_UP, 0) } }

        val downKey = addKey("\u2193")
        downKey.setOnClickListener { terminalSession?.let { terminalView.handleKeyCode(KeyEvent.KEYCODE_DPAD_DOWN, 0) } }

        val leftKey = addKey("\u2190")
        leftKey.setOnClickListener { terminalSession?.let { terminalView.handleKeyCode(KeyEvent.KEYCODE_DPAD_LEFT, 0) } }

        val rightKey = addKey("\u2192")
        rightKey.setOnClickListener { terminalSession?.let { terminalView.handleKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT, 0) } }

        val pipeKey = addKey("|")
        pipeKey.setOnClickListener { terminalSession?.write("|") }

        val ampKey = addKey("&")
        ampKey.setOnClickListener { terminalSession?.write("&") }

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
        val quickExit = SystemClock.uptimeMillis() - sessionStartedAt < QUICK_EXIT_MS
        runOnUiThread {
            if (quickExit && terminalSession == finishedSession) {
                val emulator = finishedSession.emulator
                val lastOutput = if (emulator != null) {
                    try {
                        emulator.screen.getSelectedText(0, 0, emulator.mColumns, emulator.mRows)
                    } catch (e: Exception) {
                        ""
                    }
                } else {
                    ""
                }
                errorView.text = "Shell exited (status ${finishedSession.exitStatus}):\n$lastOutput"
                errorView.visibility = View.VISIBLE
            }
        }
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

    override fun logError(tag: String, message: String) {
        Log.e(tag, message)
    }

    override fun logWarn(tag: String, message: String) {
        Log.w(tag, message)
    }

    override fun logInfo(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun logDebug(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun logVerbose(tag: String, message: String) {
        Log.v(tag, message)
    }

    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
        Log.e(tag, message, e)
    }

    override fun logStackTrace(tag: String, e: Exception) {
        Log.e(tag, Log.getStackTraceString(e))
    }

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
