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

package moe.chenxy.xiaobaitiao.hooker.entity.systemserver

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.factory.toClassOrNull
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.xiaobaitiao.BuildConfig
import moe.chenxy.xiaobaitiao.utils.XLog

/**
 * system_server 侧 hooker。
 *
 * HyperOS4 官方把 res 布尔配置 config_attachNavBarToAppDuringTransition
 * (framework-res 资源 ID 0x0111003c) 改成了 false，导致
 * com.android.server.wm.DisplayPolicy#mShouldAttachNavBarToAppDuringTransition 恒为 false，
 * 于是 Transition.handleLegacyRecentsStartBehavior 不再把导航栏(小白条)一起挂到应用窗口上，
 * 表现就是「应用内上划时小白条不跟随」。
 *
 * 这里把查询方法 shouldAttachNavBarToAppDuringTransition() 的返回依据字段强制置为 true。
 * 原方法体本身仍会判断 mNavigationBar != null，所以不会在无导航栏的 display 上出问题。
 */
object SystemServerMainHooker : YukiBaseHooker() {
    private const val DISPLAY_POLICY_CLASS = "com.android.server.wm.DisplayPolicy"
    private const val FIELD_ATTACH = "mShouldAttachNavBarToAppDuringTransition"
    private const val METHOD_SHOULD_ATTACH = "shouldAttachNavBarToAppDuringTransition"

    /** 只在第一次失败时打日志，避免系统进程里刷日志。 */
    @Volatile private var logOnce = true

    override fun onHook() {
        XLog.i("[SystemServer] onHook entered, pid=${android.os.Process.myPid()}")
        try {
            if (!isEnabled()) {
                XLog.i("[SystemServer] attach-navbar-to-app disabled by user, skip")
                return
            }
            val displayPolicyClass = DISPLAY_POLICY_CLASS.toClassOrNull()
            if (displayPolicyClass == null) {
                XLog.w("[SystemServer] $DISPLAY_POLICY_CLASS not found")
                return
            }
            displayPolicyClass.method {
                name = METHOD_SHOULD_ATTACH
                paramCount = 0
            }.hook {
                before {
                    try {
                        // Only write when the system has it off: avoids touching the field on
                        // every transition (1 read instead of 1 write per call) while still
                        // re-forcing it after a configuration change resets it.
                        if (!XposedHelpers.getBooleanField(instance, FIELD_ATTACH)) {
                            XposedHelpers.setBooleanField(instance, FIELD_ATTACH, true)
                        }
                    } catch (t: Throwable) {
                        // Never let a reflection failure escape into the window manager.
                        if (logOnce) {
                            logOnce = false
                            XLog.w("[SystemServer] set $FIELD_ATTACH failed: ${t.message}")
                        }
                    }
                }
            }
            XLog.i("[SystemServer] hooked $DISPLAY_POLICY_CLASS#$METHOD_SHOULD_ATTACH")
        } catch (t: Throwable) {
            XLog.e("[SystemServer] onHook failed", t)
        }
    }

    private fun isEnabled(): Boolean = readPref("chen_navbar_attach_to_app", false)

    private fun readPref(key: String, defValue: Boolean): Boolean = try {
        val prefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")
        prefs.reload()
        prefs.getBoolean(key, defValue)
    } catch (t: Throwable) {
        XLog.w("[SystemServer] read prefs $key failed: ${t.message}")
        defValue
    }
}
