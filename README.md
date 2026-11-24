# TermDroid64 (`aarch64` / `arm64-v8a`)

An Android terminal emulator app built specifically for **aarch64 (`arm64-v8a`)** devices, featuring a native C++ PTY (`/system/bin/sh`) bridge and built-in Android phone capability commands.

## Features
- **Native `arm64-v8a` PTY Shell**: Real pseudo-terminal (`openpty` + `fork` + `execve`) running `/system/bin/sh`.
- **Built-in Phone Capabilities (`phone-*`)**:
  - `phone-info`: Device model, Android version, ABI, RAM, uptime
  - `phone-battery`: Battery percentage, charging state, temperature, voltage
  - `phone-storage`: Internal storage total, used, and free space
  - `phone-network`: Active Wi-Fi/Cellular status and local IPv4 addresses
  - `phone-torch on|off`: Toggle camera flashlight
  - `phone-vibrate [ms]`: Haptic vibration
  - `phone-toast <text>`: Show an Android Toast notification
  - `phone-copy <text>` / `phone-paste`: Read and write Android clipboard
- **Termux-Style Extra Keys**: `ESC`, `TAB`, `CTRL`, `^C`, `↑`/`↓` command history, `/`, `-`, `|`.

## How to Build the `.apk` Automatically on GitHub
1. Create a new repository on [GitHub](https://github.com/new).
2. Extract this ZIP and upload all files (including the hidden `.github/workflows/build-apk.yml` folder) to the repository.
3. Open the **Actions** tab in your GitHub repository.
4. Click the **Build TermDroid64 (arm64-v8a) APK** workflow run (or trigger it manually with **Run workflow**).
5. Once finished, download **TermDroid64-arm64-debug-apk** from the **Artifacts** section and install `app-debug.apk` on your `aarch64` Android phone.
