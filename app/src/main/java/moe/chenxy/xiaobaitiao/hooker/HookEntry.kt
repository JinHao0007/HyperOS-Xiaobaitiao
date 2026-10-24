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

package moe.chenxy.xiaobaitiao.hooker

import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.YukiHookAPI.configs
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import de.robv.android.xposed.XSharedPreferences
import moe.chenxy.xiaobaitiao.BuildConfig
import moe.chenxy.xiaobaitiao.hooker.entity.systemserver.SystemServerMainHooker
import moe.chenxy.xiaobaitiao.hooker.entity.systemui.SystemUIMainHooker

@InjectYukiHookWithXposed
object HookEntry : IYukiHookXposedInit {
    private val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")

    override fun onHook() = YukiHookAPI.encase {
        try {
            mainPrefs.reload()
        } catch (t: Throwable) {
            // ignore: 某些进程（如 system_server）可能读不到模块的 SharedPreferences
        }
        loadApp("com.android.systemui", SystemUIMainHooker)
        loadSystem(SystemServerMainHooker)
    }

    override fun onInit() = configs {
        isDebug = false
    }
}
