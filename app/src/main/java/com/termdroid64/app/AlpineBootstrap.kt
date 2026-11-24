package com.termdroid64.app

import android.content.Context
import android.system.Os
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

object AlpineBootstrap {
    private const val ROOTFS_URL =
        "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.0-aarch64.tar.gz"

    fun rootfsDir(context: Context): File = File(context.filesDir, "rootfs")

    fun isInstalled(context: Context): Boolean {
        val root = rootfsDir(context)
        return File(root, "bin/busybox").exists() && File(root, "sbin/apk").exists()
    }

    fun installIfNeeded(context: Context, onProgress: (String) -> Unit): Boolean {
        if (isInstalled(context)) return true
        val root = rootfsDir(context)
        if (root.exists()) root.deleteRecursively()
        root.mkdirs()

        return try {
            onProgress("Downloading Alpine Linux 3.21 (aarch64)...\n")
            val conn = (URL(ROOTFS_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 60000
                instanceFollowRedirects = true
            }
            if (conn.responseCode !in 200..299) {
                onProgress("Download failed: HTTP ${conn.responseCode}\n")
                return false
            }

            onProgress("Extracting Linux rootfs...\n")
            BufferedInputStream(conn.inputStream).use { bis ->
                GZIPInputStream(bis).use { gis ->
                    extractTar(gis, root)
                }
            }

            val etcDir = File(root, "etc").apply { mkdirs() }
            File(etcDir, "resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
            File(root, "root").mkdirs()
            File(root, "tmp").mkdirs()

            onProgress("Alpine Linux aarch64 ready! Run 'apk update' & 'apk add python3 git nano' etc.\n\n")
            true
        } catch (e: Exception) {
            onProgress("Bootstrap error: ${e.message}\n")
            false
        }
    }

    private fun extractTar(input: InputStream, destDir: File) {
        val header = ByteArray(512)
        var longName: String? = null

        while (true) {
            val n = readFully(input, header)
            if (n < 512 || header.all { it == 0.toByte() }) break

            val typeFlag = header[156].toInt().toChar()
            val size = parseOctal(header, 124, 12)
            val mode = parseOctal(header, 100, 8).toInt()

            val rawName = extractString(header, 0, 100)
            val prefix = extractString(header, 345, 155)
            val entryName = longName ?: if (prefix.isNotEmpty()) "$prefix/$rawName" else rawName
            longName = null

            if (typeFlag == 'L') {
                val nameBytes = ByteArray(size.toInt())
                readFully(input, nameBytes)
                longName = String(nameBytes).trimEnd('\u0000', '\n')
                skipPadding(input, size)
                continue
            }
            if (typeFlag == 'x' || typeFlag == 'g') {
                skipBytes(input, size)
                skipPadding(input, size)
                continue
            }

            val cleanName = entryName.trimStart('.', '/')
            if (cleanName.isEmpty()) {
                skipBytes(input, size)
                skipPadding(input, size)
                continue
            }

            val outFile = File(destDir, cleanName)
            when (typeFlag) {
                '5' -> {
                    outFile.mkdirs()
                    try { Os.chmod(outFile.absolutePath, mode) } catch (_: Exception) {}
                }
                '2' -> {
                    val target = extractString(header, 157, 100)
                    outFile.parentFile?.mkdirs()
                    if (outFile.exists()) outFile.delete()
                    try { Os.symlink(target, outFile.absolutePath) } catch (_: Exception) {}
                }
                '1' -> {
                    val targetName = extractString(header, 157, 100).trimStart('.', '/')
                    val targetFile = File(destDir, targetName)
                    outFile.parentFile?.mkdirs()
                    if (outFile.exists()) outFile.delete()
                    try { Os.link(targetFile.absolutePath, outFile.absolutePath) } catch (_: Exception) {}
                }
                else -> {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        val buf = ByteArray(8192)
                        var remaining = size
                        while (remaining > 0) {
                            val toRead = minOf(buf.size.toLong(), remaining).toInt()
                            val r = input.read(buf, 0, toRead)
                            if (r <= 0) break
                            fos.write(buf, 0, r)
                            remaining -= r
                        }
                    }
                    try { Os.chmod(outFile.absolutePath, mode) } catch (_: Exception) {}
                }
            }
            if (typeFlag != '5' && typeFlag != '2' && typeFlag != '1') {
                skipPadding(input, size)
            }
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val r = input.read(buf, total, buf.size - total)
            if (r < 0) break
            total += r
        }
        return total
    }

    private fun skipBytes(input: InputStream, count: Long) {
        var rem = count
        val buf = ByteArray(4096)
        while (rem > 0) {
            val r = input.read(buf, 0, minOf(buf.size.toLong(), rem).toInt())
            if (r <= 0) break
            rem -= r
        }
    }

    private fun skipPadding(input: InputStream, size: Long) {
        val rem = (512 - (size % 512)) % 512
        if (rem > 0) skipBytes(input, rem)
    }

    private fun parseOctal(buf: ByteArray, off: Int, len: Int): Long {
        var res = 0L
        for (i in off until off + len) {
            val b = buf[i].toInt()
            if (b == 0 || b == ' '.code) continue
            if (b in '0'.code..'7'.code) {
                res = (res shl 3) + (b - '0'.code)
            }
        }
        return res
    }

    private fun extractString(buf: ByteArray, off: Int, len: Int): String {
        val end = (off until off + len).firstOrNull { buf[it] == 0.toByte() } ?: (off + len)
        return String(buf, off, end - off)
    }
}
