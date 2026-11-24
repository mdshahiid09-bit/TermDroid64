package com.termdroid64.app

object NativePty {
    init {
        System.loadLibrary("termdroid64")
    }

    @JvmStatic
    external fun createSubprocess(
        cmd: String,
        cwd: String,
        argsArray: Array<String>,
        envArray: Array<String>,
        rows: Int,
        cols: Int
    ): IntArray?

    @JvmStatic
    external fun setPtyWindowSize(fd: Int, rows: Int, cols: Int)

    @JvmStatic
    external fun waitFor(pid: Int): Int

    @JvmStatic
    external fun killProcess(pid: Int)

    @JvmStatic
    external fun closeFd(fd: Int)
}
