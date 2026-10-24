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

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import de.robv.android.xposed.XSharedPreferences
import moe.chenxy.xiaobaitiao.BuildConfig

object SystemUIMainHooker : YukiBaseHooker() {
    override fun onHook() {
        val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")
        MiBlurCompatUtils.classLoader = this.appClassLoader

        if (mainPrefs.getBoolean("chen_home_handle_anim", false)) {
            loadHooker(HomeHandleAnimatorHooker)
        }
    }
}
