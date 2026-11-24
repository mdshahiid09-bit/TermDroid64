package com.termdroid64.app

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

class PhoneCapabilities(private val context: Context) {

    fun tryHandleCommand(rawLine: String): String? {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(Regex("\\s+"), limit = 2)
        val cmd = parts[0]
        val arg = if (parts.size > 1) parts[1] else ""

        return when (cmd) {
            "help", "phone-help" -> helpText()
            "phone-info" -> deviceInfo()
            "phone-battery" -> batteryInfo()
            "phone-storage" -> storageInfo()
            "phone-network" -> networkInfo()
            "phone-vibrate" -> vibrate(arg)
            "phone-toast" -> showToast(arg)
            "phone-torch" -> toggleTorch(arg)
            "phone-copy" -> copyClipboard(arg)
            "phone-paste" -> pasteClipboard()
            else -> null
        }
    }

    private fun helpText(): String = buildString {
        appendLine("TermDroid64 (aarch64) — Built-in Phone Commands:")
        appendLine("  phone-info           Device model, Android version, ABI, RAM & uptime")
        appendLine("  phone-battery        Battery level, charging status, temp & voltage")
        appendLine("  phone-storage        Internal storage total, used & available space")
        appendLine("  phone-network        Active network type & local IPv4 addresses")
        appendLine("  phone-torch on|off   Toggle camera flashlight")
        appendLine("  phone-vibrate [ms]   Vibrate phone (default 200ms)")
        appendLine("  phone-toast <msg>    Show Android toast message")
        appendLine("  phone-copy <text>    Copy text to Android clipboard")
        appendLine("  phone-paste          Print text from Android clipboard")
        appendLine("  clear                Clear terminal screen")
        appendLine("Standard Android shell commands (ls, pwd, cd, cat, ps, df, ping, uname -a) run directly in /system/bin/sh.")
    }

    private fun deviceInfo(): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val availRamMb = memInfo.availMem / (1024 * 1024)
        val uptimeSec = SystemClock.elapsedRealtime() / 1000
        val hrs = uptimeSec / 3600
        val mins = (uptimeSec % 3600) / 60

        return buildString {
            appendLine("Device      : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android     : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("CPU ABI     : ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            appendLine("RAM         : $availRamMb MB free / $totalRamMb MB total")
            appendLine("Uptime      : ${hrs}h ${mins}m")
        }
    }

    private fun batteryInfo(): String {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return "Unable to read battery state."
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (scale > 0) (level * 100) / scale else level
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val statusStr = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            else -> "Not charging"
        }
        val tempC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
        val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

        return buildString {
            appendLine("Level       : $pct%")
            appendLine("Status      : $statusStr")
            appendLine("Temperature : ${String.format(Locale.US, "%.1f", tempC)} C")
            appendLine("Voltage     : $voltageMv mV")
        }
    }

    private fun storageInfo(): String {
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalGb = stat.totalBytes / (1024.0 * 1024.0 * 1024.0)
        val availGb = stat.availableBytes / (1024.0 * 1024.0 * 1024.0)
        val usedGb = totalGb - availGb
        return buildString {
            appendLine(String.format(Locale.US, "Internal Total : %.2f GB", totalGb))
            appendLine(String.format(Locale.US, "Internal Used  : %.2f GB", usedGb))
            appendLine(String.format(Locale.US, "Internal Free  : %.2f GB", availGb))
        }
    }

    private fun networkInfo(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork
        val caps = if (active != null) cm.getNetworkCapabilities(active) else null
        val transport = when {
            caps == null -> "Disconnected"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Connected"
        }

        val ips = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                val addrs = nif.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        ips.add("${nif.name}: ${addr.hostAddress}")
                    }
                }
            }
        } catch (_: Exception) {
        }

        return buildString {
            appendLine("Transport   : $transport")
            if (ips.isEmpty()) {
                appendLine("IPv4        : None")
            } else {
                ips.forEach { appendLine("IPv4        : $it") }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(arg: String): String {
        val ms = arg.toLongOrNull()?.coerceIn(20L, 3000L) ?: 200L
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    v.vibrate(ms)
                }
            }
            "Vibrated for ${ms}ms.
"
        } catch (e: Exception) {
            "Vibrate failed: ${e.message}
"
        }
    }

    private fun showToast(arg: String): String {
        val msg = arg.ifBlank { "Hello from TermDroid64!" }
        android.os.Handler(context.mainLooper).post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
        return "Toast shown: $msg
"
    }

    private fun toggleTorch(arg: String): String {
        val enable = when (arg.trim().lowercase(Locale.US)) {
            "on", "1", "true" -> true
            "off", "0", "false" -> false
            else -> return "Usage: phone-torch on|off
"
        }
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "No flash hardware found on this device.
"
            cm.setTorchMode(cameraId, enable)
            "Flashlight turned ${if (enable) "ON" else "OFF"}.
"
        } catch (e: Exception) {
            "Torch error: ${e.message}
"
        }
    }

    private fun copyClipboard(arg: String): String {
        if (arg.isEmpty()) return "Usage: phone-copy <text>
"
        var result = "Copied to clipboard.
"
        val latch = java.util.concurrent.CountDownLatch(1)
        android.os.Handler(context.mainLooper).post {
            try {
                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("TermDroid64", arg))
            } catch (e: Exception) {
                result = "Clipboard error: ${e.message}
"
            } finally {
                latch.countDown()
            }
        }
        latch.await()
        return result
    }

    private fun pasteClipboard(): String {
        var text = "(clipboard empty)"
        val latch = java.util.concurrent.CountDownLatch(1)
        android.os.Handler(context.mainLooper).post {
            try {
                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val item = cb.primaryClip?.getItemAt(0)?.coerceToText(context)
                if (!item.isNullOrEmpty()) {
                    text = item.toString()
                }
            } catch (e: Exception) {
                text = "Clipboard error: ${e.message}"
            } finally {
                latch.countDown()
            }
        }
        latch.await()
        return "$text
"
    }
}
