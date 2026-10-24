/*
 * Xiaobaitiao - a standalone port of the home handle (navigation bar)
 * module of MIUI-Extra <https://github.com/Art-Chen/MIUI-Extra>.
 *
 * Original work Copyright (C) Art_Chen.
 * This file has been modified for the standalone port: package renamed,
 * features unrelated to the home handle removed, and HyperOS 4 support
 * added. The list of changes is documented in the README of this project.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version. See the LICENSE file for the full text.
 */

package moe.chenxy.xiaobaitiao.utils

import android.util.Log
import de.robv.android.xposed.XposedBridge

/**
 * 日志工具。
 *
 * HyperOS 上 system_server 里的普通 Log.i 会被日志系统丢掉（实测 logcat 里一条都没有），
 * 只有 XposedBridge.log 能稳定落盘到 LSPosed 的 modules 日志，因此两边都写。
 */
object XLog {
    private const val TAG = "Xiaobaitiao"
    private const val PREFIX = "XBT: "

    fun i(message: String) {
        Log.i(TAG, message)
        runCatching { XposedBridge.log(PREFIX + message) }
    }

    fun w(message: String) {
        Log.w(TAG, message)
        runCatching { XposedBridge.log(PREFIX + "W/" + message) }
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        val detail = throwable?.let { " (${it.javaClass.simpleName}: ${it.message})" } ?: ""
        runCatching { XposedBridge.log(PREFIX + "E/" + message + detail) }
    }
}
