package com.termdroid64.app

import android.content.Context
import android.os.ParcelFileDescriptor
import java.io.File
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

        val prootBin = File(context.applicationInfo.nativeLibraryDir, "libproot.so")
        val rootfs = AlpineBootstrap.rootfsDir(context)
        val useProot = prootBin.exists() && AlpineBootstrap.isInstalled(context)

        val cmd: String
        val args: Array<String>
        val cwd: String
        val env: Array<String>

        if (useProot) {
            cmd = prootBin.absolutePath
            args = arrayOf(
                "-0",
                "-r", rootfs.absolutePath,
                "-b", "/dev",
                "-b", "/proc",
                "-b", "/sys",
                "-b", "/storage/emulated/0:/sdcard",
                "-w", "/root",
                "/bin/sh", "-l"
            )
            cwd = context.filesDir.absolutePath
            env = arrayOf(
                "TERM=xterm-256color",
                "HOME=/root",
                "PROOT_TMP_DIR=${context.cacheDir.absolutePath}",
                "PROOT_NO_SECCOMP=1",
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                "LANG=en_US.UTF-8"
            )
        } else {
            cmd = "/system/bin/sh"
            args = emptyArray()
            cwd = context.filesDir.absolutePath
            env = arrayOf(
                "TERM=xterm-256color",
                "HOME=${context.filesDir.absolutePath}",
                "TMPDIR=${context.cacheDir.absolutePath}",
                "PATH=/system/bin:/system/xbin:/vendor/bin",
                "LANG=en_US.UTF-8"
            )
        }

        val res = NativePty.createSubprocess(cmd, cwd, args, env, rows, cols) ?: return false

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
            .replace(Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]"), "")
            .replace(Regex("\u001B\\][^\u0007]*\u0007"), "")
            .replace("\r\n", "\n")
            .replace("\r", "")
    }
}
