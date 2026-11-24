package com.termdroid64.app

import android.content.Context
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

class TerminalSession(
    private val context: Context,
    private val onOutput: (String) -> Unit,
    private val onExit: (Int) -> Unit
) {
    private var pid: Int = -1
    private var masterFd: Int = -1
    private var pfd: ParcelFileDescriptor? = null
    private var inputStream: FileInputStream? = null
    private var outputStream: FileOutputStream? = null
    private val running = AtomicBoolean(false)

    fun start(rows: Int = 30, cols: Int = 80): Boolean {
        if (running.get()) return true

        val homeDir = context.filesDir.absolutePath
        val env = arrayOf(
            "TERM=xterm-256color",
            "HOME=$homeDir",
            "TMPDIR=${context.cacheDir.absolutePath}",
            "PATH=/system/bin:/system/xbin:/vendor/bin",
            "LANG=en_US.UTF-8"
        )

        val res = NativePty.createSubprocess("/system/bin/sh", homeDir, env, rows, cols)
            ?: return false

        pid = res[0]
        masterFd = res[1]
        pfd = ParcelFileDescriptor.adoptFd(masterFd)
        inputStream = FileInputStream(pfd!!.fileDescriptor)
        outputStream = FileOutputStream(pfd!!.fileDescriptor)
        running.set(true)

        Thread {
            val buffer = ByteArray(4096)
            try {
                while (running.get()) {
                    val read = inputStream?.read(buffer) ?: -1
                    if (read <= 0) break
                    val raw = String(buffer, 0, read, StandardCharsets.UTF_8)
                    val cleaned = stripAnsi(raw)
                    if (cleaned.isNotEmpty()) {
                        onOutput(cleaned)
                    }
                }
            } catch (_: Exception) {
            } finally {
                val exitCode = if (pid > 0) NativePty.waitFor(pid) else 0
                running.set(false)
                onExit(exitCode)
            }
        }.start()

        return true
    }

    fun write(data: String) {
        if (!running.get()) return
        Thread {
            try {
                outputStream?.write(data.toByteArray(StandardCharsets.UTF_8))
                outputStream?.flush()
            } catch (_: Exception) {
            }
        }.start()
    }

    fun sendCtrlKey(ch: Char) {
        val code = ch.uppercaseChar().code - 64
        if (code in 1..26) {
            write(code.toChar().toString())
        }
    }

    fun resize(rows: Int, cols: Int) {
        if (masterFd >= 0 && rows > 0 && cols > 0) {
            NativePty.setPtyWindowSize(masterFd, rows, cols)
        }
    }

    fun stop() {
        running.set(false)
        try {
            inputStream?.close()
            outputStream?.close()
            pfd?.close()
        } catch (_: Exception) {
        }
        if (pid > 0) {
            NativePty.killProcess(pid)
            pid = -1
        }
    }

    private fun stripAnsi(input: String): String {
        return input
            .replace(Regex("\u001B\[[0-9;?]*[ -/]*[@-~]"), "")
            .replace(Regex("\u001B\][^\u0007]*\u0007"), "")
            .replace("\r\n", "\n")
            .replace("\r", "")
    }
}
