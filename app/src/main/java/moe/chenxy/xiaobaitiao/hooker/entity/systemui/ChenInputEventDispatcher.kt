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

import android.content.Context
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.InputEvent
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.constructor
import com.highcapable.yukihookapi.hook.factory.toClassOrNull
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.xiaobaitiao.hooker.entity.systemui.HomeHandleAnimatorHooker.appContext
import moe.chenxy.xiaobaitiao.hooker.entity.systemui.HomeHandleAnimatorHooker.loadHooker
import moe.chenxy.xiaobaitiao.hooker.entity.systemui.HomeHandleAnimatorHooker.toClass
import moe.chenxy.xiaobaitiao.utils.ChenUtils
import java.lang.reflect.Proxy

object ChenInputEventDispatcher {
    private val listeners = ArrayList<ChenInputEventListener>()
    private val isAboveU = ChenUtils.isAboveAndroidVersion(ChenUtils.Companion.AndroidVersion.U)
    private val isAboveV = ChenUtils.isAboveAndroidVersion(ChenUtils.Companion.AndroidVersion.V)
    private var inited = false
    private var mInputEventReceiver: Any? = null
    private var mInputMonitor: Any? = null

    fun isInited(): Boolean {
        return inited
    }

    fun init(context: Context, appClassLoader: ClassLoader) {
        if (inited) return

        if (isAboveU) {
            // Android U+ registers the listener via the official gesture-input monitor.
            // On HyperOS 4, R8 stripped the outer InputEventReceiver class - its real
            // constructor lives on the synthetic inner class $1 (BatchedInputEventReceiver
            // subclass) with (InputChannel, Looper, Choreographer, listener).
            var inputChannel: Any? = null
            try {
                val inputEventListenerCls = "com.android.systemui.shared.system.InputChannelCompat\$InputEventListener".toClass(appClassLoader)
                val inputManagerGlobalCls = "android.hardware.input.InputManagerGlobal".toClass(appClassLoader)
                val inputManager = XposedHelpers.callStaticMethod(inputManagerGlobalCls, "getInstance")
                mInputMonitor = XposedHelpers.callMethod(inputManager, "monitorGestureInput", "Chen-HomeHandle-Touch", appContext?.display?.displayId)
                inputChannel = XposedHelpers.callMethod(mInputMonitor, "getInputChannel")
                val looper = appContext?.mainLooper
                val choreographer = Choreographer.getInstance()
                val chenListener = Proxy.newProxyInstance(
                    appClassLoader,
                    arrayOf(inputEventListenerCls)
                ) { _, _, args ->
                    val ev = args?.get(0) as InputEvent
                    notifyListeners(ev)
                }

                // Prefer the R8-surviving inner class $1 (4-arg ctor), fall back to the
                // outer class (5-arg on V+, 4-arg deprecated) for other builds.
                val innerReceiverCls = "com.android.systemui.shared.system.InputChannelCompat\$InputEventReceiver\$1".toClassOrNull(appClassLoader)
                mInputEventReceiver = if (innerReceiverCls != null) {
                    // Kotlin can't reflectively construct across a synthetic outer ref;
                    // the $1 class on OS4 has NO outer reference (R8 removed it), so a
                    // plain 4-arg constructor call works.
                    innerReceiverCls.declaredConstructors.firstOrNull { c -> c.parameterTypes.size == 4 }
                        ?.also { it.isAccessible = true }
                        ?.newInstance(inputChannel, looper, choreographer, chenListener)
                        ?: throw NoSuchMethodException("inner InputEventReceiver$1 ctor(4) not found")
                } else {
                    val outerReceiverCls = "com.android.systemui.shared.system.InputChannelCompat\$InputEventReceiver".toClass(appClassLoader)
                    val ctor = outerReceiverCls.declaredConstructors.firstOrNull { c -> c.parameterTypes.size == 5 }
                        ?: outerReceiverCls.declaredConstructors.firstOrNull { c -> c.parameterTypes.size == 4 }
                        ?: throw NoSuchMethodException("outer InputEventReceiver ctor(4/5) not found")
                    ctor.isAccessible = true
                    if (ctor.parameterTypes.size == 5) {
                        ctor.newInstance("ChenHomeHandleTouchHandler", inputChannel, looper, choreographer, chenListener)
                    } else {
                        ctor.newInstance(inputChannel, looper, choreographer, chenListener)
                    }
                }
                Log.i(
                    "Art_Chen",
                    "[ChenInputEventDispatcher] init done via InputManagerGlobal (OS4 inner-class path)"
                )
                inited = true
            } catch (e: Throwable) {
                // Clean up the leaked monitor - a live InputMonitor with no receiver
                // destabilizes the input pipeline on OS4.
                try {
                    mInputMonitor?.let { XposedHelpers.callMethod(it, "dispose") }
                } catch (ignore: Throwable) {
                }
                mInputMonitor = null
                inputChannel = null
                // On OS4 we must NOT fall back to the fod listener - it crashes SystemUI
                // natively. Stay uninitialized; the drag animation will be absent.
                Log.w("Art_Chen", "[ChenInputEventDispatcher] U-path init failed: ${e.message}. Input listener stays DISABLED (fod path is fatal on HyperOS 4).")
            }
            if (inited) return
        }

        // Android T (OS2/OS3) uses the fod touch listener. This path is NOT reachable on
        // HyperOS 4 when isAboveU is true; kept only for real Android T devices.
        val miuiGestureListenerClass =
            ("com.android.keyguard.fod.MiuiGestureListener".toClassOrNull()
                ?: "com.miui.keyguard.biometrics.fod.MiuiGestureListener".toClassOrNull()
                ?: return)
        val miuiGestureMonitorClass =
            ("com.android.keyguard.fod.MiuiGestureMonitor".toClassOrNull()
                ?: "com.miui.keyguard.biometrics.fod.MiuiGestureMonitor".toClassOrNull()
                ?: return)
        try {
            val chenListener = Proxy.newProxyInstance(
                appClassLoader,
                arrayOf(miuiGestureListenerClass)
            ) { _, _, args ->
                notifyListeners(args?.get(0) as InputEvent)
            }
            val miuiGestureMonitorInstance = XposedHelpers.callStaticMethod(
                miuiGestureMonitorClass,
                "getInstance",
                context
            )
            Log.i(
                "Art_Chen",
                "[ChenInputEventDispatcher] init on Android T (Legacy Fod Listener)"
            )
            XposedHelpers.callMethod(
                miuiGestureMonitorInstance,
                "registerPointerEventListener",
                chenListener
            )
            inited = true
        } catch (e: Throwable) {
            Log.w("Art_Chen", "[ChenInputEventDispatcher] fod-path init failed: ${e.message}")
        }
    }

    fun unregister() {
        if (isAboveU) {
            if (mInputEventReceiver != null) {
                try {
                    // On OS4 the receiver IS the BatchedInputEventReceiver ($1); on older
                    // builds the real receiver sits in the mReceiver field.
                    val inner = try {
                        XposedHelpers.getObjectField(mInputEventReceiver, "mReceiver")
                    } catch (e: Throwable) {
                        null
                    }
                    if (inner != null) {
                        XposedHelpers.callMethod(inner, "dispose")
                    } else {
                        XposedHelpers.callMethod(mInputEventReceiver, "dispose")
                    }
                } catch (e: Throwable) {
                    Log.w("Art_Chen", "[ChenInputEventDispatcher] receiver dispose failed: ${e.message}")
                }
            }

            if (mInputMonitor != null) {
                try {
                    XposedHelpers.callMethod(mInputMonitor, "dispose")
                } catch (e: Throwable) {
                    Log.w("Art_Chen", "[ChenInputEventDispatcher] monitor dispose failed: ${e.message}")
                }
            }
        }

        inited = false
    }

    // Dedup guard: NavigationBarInflaterView can be constructed once per display on OS4;
    // each construction re-registers a listener, multiplying every touch event and
    // feeding the animation re-entrancy path. Only one listener may ever exist.
    @Volatile
    private var listenerRegistered = false

    private fun notifyListeners(inputEvent: InputEvent) {
        // Iterate over a snapshot: a misbehaving listener must not corrupt the list.
        for (listener in ArrayList(listeners)) {
            try {
                listener.onInputEvent(inputEvent)
            } catch (t: Throwable) {
                Log.w("Art_Chen", "[ChenInputEventDispatcher] listener failed: ${t.message}")
            }
        }
    }

    fun registerInputEventListener(chenInputEventListener: ChenInputEventListener) {
        if (listenerRegistered) return
        listenerRegistered = true
        listeners.add(chenInputEventListener)
    }

    fun unregisterInputEventListener(chenInputEventListener: ChenInputEventListener) {
        listeners.remove(chenInputEventListener)
        listenerRegistered = false
    }
}

interface ChenInputEventListener {
    fun onInputEvent(inputEvent: InputEvent)
}