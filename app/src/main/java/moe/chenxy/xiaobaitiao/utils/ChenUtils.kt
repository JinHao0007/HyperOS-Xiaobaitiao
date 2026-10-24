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

import android.content.Context
import android.os.Build.VERSION
import android.os.VibrationEffect
import android.os.VibratorManager
import java.io.DataOutputStream

class ChenUtils {
    companion object {
        enum class AndroidVersion(val releaseCode: Int) {
            V(15),
            U(14),
            T(13),
            S(12),
        }
        fun performVibrateClick(context: Context) {
            val vibrator = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibrator.defaultVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        }

        fun performVibrateHeavyClick(context: Context) {
            val vibrator = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibrator.defaultVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }

        fun performVibrateAnyIndex(context: Context, id: Int) {
            val vibrator = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibrator.defaultVibrator.vibrate(VibrationEffect.createPredefined(id))
        }

        fun cancelAnyVibration(context: Context) {
            val vibrator = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibrator.cancel()
        }

        fun lerp(start: Float, stop: Float, amount: Float): Float {
            return start + (stop - start) * amount
        }

        fun execShell(command: String) {
            try {
                val p = Runtime.getRuntime().exec("su")
                val outputStream = p.outputStream
                val dataOutputStream = DataOutputStream(outputStream)
                dataOutputStream.writeBytes(command)
                dataOutputStream.flush()
                dataOutputStream.close()
                outputStream.close()
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        fun isAboveAndroidVersion(version: AndroidVersion) : Boolean {
            val releaseCode = VERSION.RELEASE_OR_CODENAME
            val releaseCodeInt = try {
                releaseCode.toInt()
            } catch (e: Exception) {
                -1
            }

            if (releaseCodeInt != -1) {
                return releaseCodeInt >= version.releaseCode
            }
            return false
        }
    }
}