package com.termdroid64.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var terminalOutput: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var commandInput: EditText
    private lateinit var statusBadge: TextView
    private lateinit var ctrlToggle: Button

    private lateinit var phoneCapabilities: PhoneCapabilities
    private var session: TerminalSession? = null
    private var ctrlActive = false
    private val history = mutableListOf<String>()
    private var historyIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        terminalOutput = findViewById(R.id.terminalOutput)
        scrollView = findViewById(R.id.terminalScroll)
        commandInput = findViewById(R.id.commandInput)
        statusBadge = findViewById(R.id.statusBadge)
        ctrlToggle = findViewById(R.id.keyCtrl)

        phoneCapabilities = PhoneCapabilities(this)
        statusBadge.text = "aarch64 • Alpine"

        appendOutput("TermDroid64 v2.0 [aarch64 Linux + Phone API]\n")

        setupToolbar()
        setupInput()
        setupExtraKeys()
        requestStorageIfNeeded()

        Thread {
            AlpineBootstrap.installIfNeeded(this) { msg ->
                runOnUiThread { appendOutput(msg) }
            }
            runOnUiThread { startNewSession() }
        }.start()
    }

    private fun requestStorageIfNeeded() {
        val perms = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        val missing = perms.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) {
            ActivityCompat.requestPermissions(this, perms, 101)
        }
    }

    private fun setupToolbar() {
        findViewById<ImageButton>(R.id.btnClear).setOnClickListener { terminalOutput.text = "" }
        findViewById<ImageButton>(R.id.btnCopyAll).setOnClickListener {
            val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cb.setPrimaryClip(ClipData.newPlainText("TermDroid64 Output", terminalOutput.text))
            Toast.makeText(this, "Terminal output copied", Toast.LENGTH_SHORT).show()
        }
        findViewById<ImageButton>(R.id.btnRestart).setOnClickListener {
            session?.stop()
            terminalOutput.text = ""
            appendOutput("Restarting aarch64 Linux shell...\n\n")
            startNewSession()
        }
    }

    private fun setupInput() {
        findViewById<Button>(R.id.btnSend).setOnClickListener { submitCommand() }
        commandInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                submitCommand()
                true
            } else false
        }
    }

    private fun setupExtraKeys() {
        ctrlToggle.setOnClickListener {
            ctrlActive = !ctrlActive
            ctrlToggle.alpha = if (ctrlActive) 1.0f else 0.6f
            ctrlToggle.text = if (ctrlActive) "CTRL*" else "CTRL"
        }
        findViewById<Button>(R.id.keyCtrlC).setOnClickListener {
            session?.sendCtrlKey('C')
            appendOutput("^C\n")
        }
        findViewById<Button>(R.id.keyTab).setOnClickListener { session?.write("\t") }
        findViewById<Button>(R.id.keyEsc).setOnClickListener { session?.write("\u001B") }
        findViewById<Button>(R.id.keyUp).setOnClickListener {
            if (history.isNotEmpty()) {
                historyIndex = (historyIndex - 1).coerceAtLeast(0)
                commandInput.setText(history[historyIndex])
                commandInput.setSelection(commandInput.text.length)
            }
        }
        findViewById<Button>(R.id.keyDown).setOnClickListener {
            if (history.isNotEmpty()) {
                if (historyIndex < history.size - 1) {
                    historyIndex++
                    commandInput.setText(history[historyIndex])
                } else {
                    historyIndex = history.size
                    commandInput.setText("")
                }
                commandInput.setSelection(commandInput.text.length)
            }
        }
        findViewById<Button>(R.id.keySlash).setOnClickListener { insertSymbol("/") }
        findViewById<Button>(R.id.keyDash).setOnClickListener { insertSymbol("-") }
        findViewById<Button>(R.id.keyPipe).setOnClickListener { insertSymbol("|") }
    }

    private fun insertSymbol(sym: String) {
        val start = commandInput.selectionStart.coerceAtLeast(0)
        commandInput.text.insert(start, sym)
    }

    private fun submitCommand() {
        val raw = commandInput.text.toString()
        commandInput.setText("")

        if (ctrlActive && raw.length == 1) {
            ctrlActive = false
            ctrlToggle.alpha = 0.6f
            ctrlToggle.text = "CTRL"
            session?.sendCtrlKey(raw[0])
            appendOutput("^${raw[0].uppercaseChar()}\n")
            return
        }

        if (raw.isNotBlank()) {
            history.add(raw)
            historyIndex = history.size
        }

        if (raw.trim() == "clear") {
            terminalOutput.text = ""
            return
        }
        if (raw.trim() == "phone-setup-storage") {
            requestStorageIfNeeded()
            appendOutput("Storage permission requested. Your phone storage is mounted at /sdcard\n")
            return
        }

        val phoneResult = phoneCapabilities.tryHandleCommand(raw)
        if (phoneResult != null) {
            appendOutput("$ $raw\n$phoneResult\n")
            return
        }

        session?.write(raw + "\n")
    }

    private fun startNewSession() {
        val newSession = TerminalSession(
            context = this,
            onOutput = { text -> runOnUiThread { appendOutput(text) } },
            onExit = { code -> runOnUiThread { appendOutput("\n[Process exited with code $code]\n") } }
        )
        session = newSession
        if (!newSession.start()) {
            appendOutput("Failed to start shell PTY.\n")
        }
    }

    private fun appendOutput(text: String) {
        terminalOutput.append(text)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        super.onDestroy()
        session?.stop()
    }
}
