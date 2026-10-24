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

package moe.chenxy.xiaobaitiao.hooker.entity.systemui

import android.view.View
import com.highcapable.yukihookapi.hook.factory.toClassOrNull
import com.highcapable.yukihookapi.hook.type.android.ContextClass
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.xiaobaitiao.hooker.entity.systemui.MiBlurCompatUtils.isSupportMiBlur
import moe.chenxy.xiaobaitiao.utils.ChenUtils

object MiBlurCompatUtils {
    var classLoader: ClassLoader? = null
    @JvmStatic
    fun View.setPassWindowBlurEnabledCompat(enable: Boolean) =
        "com.miui.systemui.util.MiBlurCompat".toClassOrNull(classLoader)?.let {
            XposedHelpers.callStaticMethod(
                it, "setPassWindowBlurEnabledCompat", this, enable
            )
        }

    @JvmStatic
    fun View.getPassWindowBlurEnabled() =
        XposedHelpers.callMethod(this, "getPassWindowBlurEnabled") as Boolean


    @JvmStatic
    fun View.setMiBackgroundBlurModeCompat(mode: Int) = XposedHelpers.callMethod(
        this, "setMiBackgroundBlurMode", mode)

    @JvmStatic
    fun View.getMiBackgroundBlurModeCompat() : Int = XposedHelpers.callMethod(
        this, "getMiBackgroundBlurMode") as Int

    @JvmStatic
    fun View.setMiBackgroundBlurRadius(radius: Int) =
        XposedHelpers.callMethod(this, "setMiBackgroundBlurRadius", radius)

    @JvmStatic
    fun View.setMiViewBlurMode(mode: Int) =
        XposedHelpers.callMethod(this, "setMiViewBlurMode", mode)

    @JvmStatic
    fun View.isSupportMiBlur() =
        "com.miui.systemui.util.MiBlurCompat".toClassOrNull(classLoader)?.let {
            XposedHelpers.callStaticMethod(
                it,
                "getBackgroundBlurOpened",
                this.context
            ) as Boolean
        } ?: false
}