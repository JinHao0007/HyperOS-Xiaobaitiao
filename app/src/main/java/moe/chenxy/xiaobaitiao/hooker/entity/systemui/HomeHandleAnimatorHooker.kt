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

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Insets
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.animation.doOnEnd
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.constructor
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.factory.toClassOrNull
import com.highcapable.yukihookapi.hook.type.android.AttributeSetClass
import com.highcapable.yukihookapi.hook.type.android.ContextClass
import com.highcapable.yukihookapi.hook.type.android.LayoutInflaterClass
import com.highcapable.yukihookapi.hook.type.android.ViewGroupClass
import com.highcapable.yukihookapi.hook.type.java.FloatType
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.java.StringClass
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.xiaobaitiao.BuildConfig
import moe.chenxy.xiaobaitiao.utils.ChenUtils
import kotlin.math.abs


object HomeHandleAnimatorHooker : YukiBaseHooker() {
    private val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")

    @Volatile
    var mIsInHome = false

    enum class EventType {
        NORMAL,
        HOME,
        PRESSED
    }

    data class HomeHandleSettings(
        val transDegree: Int,
        val scaleX: Float,
        val scaleY: Float,
        val offsetX: Float,
        val offsetY: Float,
        val alphaAnimDuration: Long,
        val scaleAnimDuration: Long,
        val xyAnimDuration: Long,
    )

    private lateinit var zoomXAnimator: ValueAnimator
    private lateinit var zoomYAnimator: ValueAnimator
    private lateinit var xAnimator: ValueAnimator
    private lateinit var yAnimator: ValueAnimator
    private lateinit var alphaAnimator: ValueAnimator
    
    private lateinit var normalSettings: HomeHandleSettings
    private lateinit var pressedSettings: HomeHandleSettings
    private lateinit var onHomeSettings: HomeHandleSettings

    @SuppressLint("DiscouragedApi")
    override fun onHook() {
        val isAboveU = ChenUtils.isAboveAndroidVersion(ChenUtils.Companion.AndroidVersion.U)
        // HyperOS 4 moved navbar classes into "views" sub-package. Detect by probing, not by SDK version.
        val isHyperOS4 = "com.android.systemui.navigationbar.views.NavigationBarInflaterView".toClassOrNull() != null
        val navBarInflaterViewCls = if (isHyperOS4)
            "com.android.systemui.navigationbar.views.NavigationBarInflaterView"
        else
            "com.android.systemui.navigationbar.NavigationBarInflaterView"
        val navigationBarCls = if (isHyperOS4)
            "com.android.systemui.navigationbar.views.NavigationBar"
        else
            "com.android.systemui.navigationbar.NavigationBar"
        val barTransitionsCls = if (isHyperOS4)
            "com.android.systemui.shared.statusbar.phone.BarTransitions"
        else
            "com.android.systemui.statusbar.phone.BarTransitions"
        Log.i("Art_Chen", "[Xiaobaitiao] HyperOS4 navbar layout detected: $isHyperOS4")
        var mContext: Context? = null
        var mHandler: Handler? = null

        lateinit var mHomeHandle: View
        // mHomeHandleProbe is called only from posted runnables; it either returns the
        // initialized view or throws UninitializedPropertyAccessException (Error family).
        fun mHomeHandleProbe(): View = mHomeHandle

        var mHomeHandleId: Int = -1
        var barHeight = 0
        var origBarHeight = 0
        var yOffset = 0f
        var mLightColor = -1
        var mDarkColor = -1
        var orientation = 0
        var mNavigationHandle : Any? = null
        var isHidden = false
        var lastIsHidden = false
        var disableHomeHandleMovement = mainPrefs.getBoolean("home_handle_disable_move_event", false)

        var topActivityObserver: Any? = null

        fun getSetting(type: EventType): HomeHandleSettings {
            return HomeHandleSettings(
                mainPrefs.getInt("home_handle_setting_transdegree_${type.name.lowercase()}", 30),
                mainPrefs.getInt("home_handle_setting_scalex_${type.name.lowercase()}", 100)
                    .toFloat() / 100,
                mainPrefs.getInt("home_handle_setting_scaley_${type.name.lowercase()}", 100)
                    .toFloat() / 100,
                mainPrefs.getInt("home_handle_setting_offsetx_${type.name.lowercase()}", 0)
                    .toFloat(),
                mainPrefs.getInt("home_handle_setting_offsety_${type.name.lowercase()}", 0)
                    .toFloat(),
                mainPrefs.getInt("home_handle_setting_duration_alpha_${type.name.lowercase()}", 600).toLong(),
                mainPrefs.getInt("home_handle_setting_duration_scale_${type.name.lowercase()}", 600).toLong(),
                mainPrefs.getInt("home_handle_setting_duration_xy_${type.name.lowercase()}", 600).toLong(),
            )
        }

        fun updateSettings(force: Boolean = false) {
            if (force || mainPrefs.hasFileChanged()) {
                mainPrefs.reload()
                normalSettings = getSetting(EventType.NORMAL)
                pressedSettings = getSetting(EventType.PRESSED)
                onHomeSettings = getSetting(EventType.HOME)

                disableHomeHandleMovement = mainPrefs.getBoolean("home_handle_disable_move_event", false)
            }
        }

        fun animateZoomTo(scaleX: Float, scaleY: Float, duration: Long) {
            if (!this::zoomXAnimator.isInitialized) {
                zoomXAnimator = ValueAnimator()
                zoomYAnimator = ValueAnimator()
                zoomXAnimator.interpolator = PathInterpolator(0.39f, 1.58f, 0.44f, 1.07f)
                zoomYAnimator.interpolator = PathInterpolator(0.39f, 1.58f, 0.44f, 1.07f)

                zoomXAnimator.addUpdateListener {
                    mHomeHandle.scaleX = it.animatedValue as Float
                }
                zoomYAnimator.addUpdateListener {
                    mHomeHandle.scaleY = it.animatedValue as Float
                }
            }

            mHomeHandle.pivotX = (mHomeHandle.width / 2).toFloat()
            if (mainPrefs.getBoolean("chen_home_handle_anim_turbo_mode", false)) {
                mHomeHandle.pivotY = barHeight - origBarHeight + origBarHeight / 2f
            }
            if (zoomXAnimator.isRunning) zoomXAnimator.cancel()
            if (zoomYAnimator.isRunning) zoomYAnimator.cancel()

            // A zero duration makes ValueAnimator jump to the end instantly - that is the
            // "bar teleports to its home size with no animation" bug. Clamp to one frame.
            val safeDuration = duration.coerceAtLeast(16L)
            zoomXAnimator.setFloatValues(mHomeHandle.scaleX, scaleX)
            zoomYAnimator.setFloatValues(mHomeHandle.scaleY, scaleY)
            zoomXAnimator.duration = safeDuration
            zoomYAnimator.duration = safeDuration

            zoomXAnimator.start()
            zoomYAnimator.start()
        }

        fun animateHomeHandleX(duration: Long, offset: Float) {
            if (!this::xAnimator.isInitialized) {
                xAnimator = ValueAnimator()
                xAnimator.interpolator = PathInterpolator(0.39f, 1.58f, 0.44f, 1.07f)
                xAnimator.addUpdateListener {
                    mHomeHandle.translationX = it.animatedValue as Float
                }
            }
            xAnimator.duration = duration.coerceAtLeast(16L)
            xAnimator.setFloatValues(mHomeHandle.translationX, offset)
            xAnimator.start()
        }

        fun animateHomeHandleY(duration: Long, offset: Float) {
            if (!this::yAnimator.isInitialized) {
                yAnimator = ValueAnimator()
                yAnimator.interpolator = PathInterpolator(0.39f, 1.58f, 0.44f, 1.07f)

                yAnimator.addUpdateListener {
                    mHomeHandle.translationY = it.animatedValue as Float
                }
            }

            yAnimator.duration = duration.coerceAtLeast(16L)
            yAnimator.setFloatValues(mHomeHandle.translationY, offset)

            yAnimator.start()
        }

        fun cancelHomeHandleXYAnim() {
            xAnimator.cancel()
            yAnimator.cancel()
        }

        fun animateHomeHandleXYToNormal(type: EventType) {
            val setting = when (type) {
                EventType.NORMAL -> normalSettings
                EventType.HOME -> onHomeSettings
                EventType.PRESSED -> pressedSettings
            }
            yOffset = setting.offsetY
            animateHomeHandleX(setting.xyAnimDuration, setting.offsetX)
            animateHomeHandleY(setting.xyAnimDuration, setting.offsetY)
        }

        fun setDarkIntensity(f: Float) {
            if (mNavigationHandle != null) {
                XposedHelpers.callMethod(mNavigationHandle, "setDarkIntensity", f)
            }
        }

        val opacityHomeHandleRunnable = Runnable {
            if (alphaAnimator.isRunning) {
                alphaAnimator.cancel()
            }
            alphaAnimator.start()
        }

        val doOnOpacityUpdate = ValueAnimator.AnimatorUpdateListener {
            // Pure alpha mode - blur removed by design (it inverted the transparency
            // semantics depending on wallpaper color, which confused everyone).
            mHomeHandle.alpha = it.animatedValue as Float
        }

        fun opacityTo(to: Float, duration: Long) {
            if (!this::alphaAnimator.isInitialized) {
                alphaAnimator = ValueAnimator()
            }
            if (alphaAnimator.isRunning) alphaAnimator.cancel()

            alphaAnimator.duration = duration
            alphaAnimator.setFloatValues(mHomeHandle.alpha, to)

            alphaAnimator.removeAllListeners()
            alphaAnimator.addUpdateListener {
                mHomeHandle.alpha = it.animatedValue as Float
            }
            alphaAnimator.start()
        }

        fun opacityHomeHandle(type: EventType) {
            var notStart = false
            updateSettings()

            if (!this::alphaAnimator.isInitialized) {
                alphaAnimator = ValueAnimator()
                alphaAnimator.addUpdateListener(doOnOpacityUpdate)
            }

            // A running animator means a transition is in flight. Use cancel(), NOT
            // end(): end() jumps to the final value and fires the update listener -
            // a visible "twitch" on every state switch (press/release). cancel() keeps
            // the current value so the next animation glides from where the bar is.
            if (alphaAnimator.isRunning) {
                alphaAnimator.cancel()
            }

            val duration = (
                when {
                    type == EventType.HOME
                        // Normal or Press to Home
                    -> onHomeSettings.alphaAnimDuration

                    type == EventType.NORMAL && mHomeHandle.alpha != 0f
                        // Pressed to Normal
                    -> normalSettings.alphaAnimDuration * 2

                    type == EventType.NORMAL
                        // Home to Normal
                    -> normalSettings.alphaAnimDuration

                    else
                        // Normal to Press
                    -> pressedSettings.alphaAnimDuration
                }
            ).coerceAtLeast(16L)

            // Alpha only - blur removed
            val to = when (type) {
                EventType.HOME -> 1 - (onHomeSettings.transDegree.toFloat() / 100)
                EventType.NORMAL -> 1 - (normalSettings.transDegree.toFloat() / 100)
                else -> 1 - (pressedSettings.transDegree.toFloat() / 100)
            }
            // "notStart" only skips the ALPHA animation. The zoom animation must still
            // run: after a swipe-to-home the system's HomeHandleVisibilityController
            // resets view scale/translation to defaults, so even when the alpha target
            // happens to match, the size usually differs and the user expects to see
            // the resize animation.
            if (mHomeHandle.alpha == to) notStart = true
            alphaAnimator.setFloatValues(
                mHomeHandle.alpha,
                to
            )
            alphaAnimator.duration = duration

            if (!notStart) {
                mHandler?.removeCallbacks(opacityHomeHandleRunnable)
                mHandler?.post(opacityHomeHandleRunnable)
            }

            when (type) {
                EventType.NORMAL -> animateZoomTo(normalSettings.scaleX,
                    normalSettings.scaleY,
                    normalSettings.scaleAnimDuration)
                EventType.HOME -> animateZoomTo(onHomeSettings.scaleX, onHomeSettings.scaleY, onHomeSettings.scaleAnimDuration)
                else -> animateZoomTo(pressedSettings.scaleX, pressedSettings.scaleY, pressedSettings.scaleAnimDuration)
            }
        }
        var motionTriggered = false
        // Timeout watchdog: once HyperOS recognizes a swipe-to-home it swallows the touch
        // stream - no UP/CANCEL ever arrives. If no MOVE has been seen for 400ms, reset
        // the gesture state SILENTLY (no animation) - the visible recovery belongs to
        // ACTION_UP / enterHome / leaveHome, each of which has its own trigger. Playing
        // a recovery animation here caused the bar to "twitch" against the user's
        // finger during long presses and against the system's surface layer during
        // swipe-to-home gestures.
        var motionWatchdogRunnable: Runnable? = null
        fun scheduleMotionWatchdog() {
            motionWatchdogRunnable?.let { mHandler?.removeCallbacks(it) }
            val r = Runnable {
                if (motionTriggered) {
                    motionTriggered = false
                }
            }
            motionWatchdogRunnable = r
            mHandler?.postDelayed(r, 400)
        }
        fun leaveHome() {
            opacityHomeHandle(EventType.NORMAL)
            animateHomeHandleXYToNormal(EventType.NORMAL)
        }
        var enterHomeRunnable: Runnable? = null
        fun enterHome() {
            // On HyperOS the system swallows the touch stream once it recognizes a
            // swipe-to-home gesture - our ACTION_UP never arrives, so motionTriggered
            // stays true forever and the HOME animation used to be skipped. Force-finish
            // the gesture here.
            motionTriggered = false
            // OS4's HomeHandleVisibilityController takes over the bar through SurfaceControl
            // transactions during the swipe-to-home animation and resets the view properties
            // when the launcher settles. Animating before that handover finishes means our
            // animation is masked ("bar jumps to home size, no animation").
            //
            // Instead of a fixed 200ms wait (which left a visible dead gap when the system
            // was faster), poll for the moment the bar is actually on screen and animate
            // right then. Capped at ~320ms so a bar that never becomes visible cannot leak
            // a runnable forever.
            enterHomeRunnable?.let { mHandler?.removeCallbacks(it) }
            val r = object : Runnable {
                private var attempt = 0
                override fun run() {
                    attempt++
                    try {
                        val handle: View? = try {
                            mHomeHandleProbe()
                        } catch (t: Throwable) {
                            null
                        }
                        val ready = handle != null && handle.isAttachedToWindow &&
                            handle.windowVisibility == View.VISIBLE && handle.isShown
                        if (!ready && attempt < 10) {
                            mHandler?.postDelayed(this, 32)
                            return
                        }
                        if (handle == null || !handle.isAttachedToWindow) {
                            Log.w("Art_Chen", "[Xiaobaitiao] handle detached on enterHome; waiting for inflateLayout")
                            return
                        }
                        opacityHomeHandle(EventType.HOME)
                        animateHomeHandleXYToNormal(EventType.HOME)
                    } catch (t: Throwable) {
                        Log.w("Art_Chen", "[Xiaobaitiao] enterHome anim failed: ${t.message}")
                    }
                }
            }
            enterHomeRunnable = r
            mHandler?.post(r)
        }

        var baseX = -1f
        var baseY = -1f

        val isBoostMode = mainPrefs.getBoolean("chen_home_handle_anim_turbo_mode", false)
        // Cache Screen Height. IPC is expensive
        var screenRealHeight: Int
        var screenHeight: Int

        fun onInputEventInner(motionEvent: MotionEvent) {
            // mHomeHandle is a local lateinit in onHook(); probe it via a helper that
            // catches UninitializedPropertyAccessException (an Error-family throw).
            val handle: View? = try {
                mHomeHandleProbe()
            } catch (t: Throwable) {
                null
            }
            if (handle == null) {
                motionEvent.recycle()
                return
            }
            try {
                // Note: do NOT drop ACTION_DOWN just because alpha == 0. When the user sets
                // "Auto Transparent Degree" to 100 the bar is invisible by design, but the
                // PRESSED animation must still trigger on touch - otherwise the first press
                // is eaten ("not following the finger" feel). Only skip when the handle is
                // hidden by the system (immersive) or scaled to nothing.
                if (motionEvent.actionMasked == MotionEvent.ACTION_DOWN
                    && (mHomeHandle.scaleX <= 0f || mHomeHandle.scaleY <= 0f
                            || (mHomeHandle.windowVisibility != View.VISIBLE || isHidden))) {
                    return
                }

                if (disableHomeHandleMovement && motionEvent.actionMasked == MotionEvent.ACTION_MOVE) {
                    // drop if disable home handle movement
                    return
                }

                screenRealHeight = getScreenRealHeight(mContext!!)
                screenHeight = getScreenHeight(mContext!!)

                val isNavigationBarArea: Boolean =
                    if (!isAboveU && screenRealHeight - screenHeight > 1) {
                        motionEvent.y > screenHeight
                    } else {
                        motionEvent.y > screenRealHeight - origBarHeight
                    }

                if (isNavigationBarArea) {
                    if (motionEvent.actionMasked == MotionEvent.ACTION_DOWN) {
                        // Always fire the PRESSED animation on touch - even at alpha 0
                        // (degree 100), the press should bring the bar back visually.
                        opacityHomeHandle(EventType.PRESSED)
                        animateHomeHandleXYToNormal(EventType.PRESSED)

                        baseX = motionEvent.x
                        baseY = motionEvent.y
                        motionTriggered = true
                        scheduleMotionWatchdog()
                    }
                    if (motionEvent.actionMasked == MotionEvent.ACTION_MOVE && motionTriggered && (!isBoostMode && orientation == 0)) {
                        scheduleMotionWatchdog()
                        val offsetNeeded =
                            -(baseY - motionEvent.y) * 0.15f + yOffset
                        // Clamp inside the visible window instead of dropping the move.
                        val maxOffset: Float = (barHeight / 2 - 6).toFloat().coerceAtLeast(6f)
                        if (abs(offsetNeeded) < maxOffset) {
                            mHomeHandle.translationY = offsetNeeded
                        } else {
                            mHomeHandle.translationY = maxOffset * (if (offsetNeeded > 0) 1 else -1)
                        }
                    }
                }

                if (motionEvent.actionMasked == MotionEvent.ACTION_MOVE && motionTriggered) {
                    scheduleMotionWatchdog()
                    cancelHomeHandleXYAnim()
                    if (isBoostMode && orientation == 0) {
                        val offsetNeeded =
                            -(baseY - motionEvent.y) * 0.2f + yOffset
                        val maxOffset: Float = (barHeight / 2 + 6).toFloat().coerceAtLeast(6f)
                        if (abs(offsetNeeded) < maxOffset) {
                            mHomeHandle.translationY = offsetNeeded
                        } else {
                            mHomeHandle.translationY = maxOffset * (if (offsetNeeded > 0) 1 else -1)
                        }
                    } else if (orientation != 0) {
                        // Let the Landscape has a little move effect instead of zero y offset.
                        val offsetNeeded =
                            -(baseY - motionEvent.y) * 0.02f + yOffset
                        val maxOffset: Float = (barHeight / 2 + 6).toFloat().coerceAtLeast(6f)
                        if (abs(offsetNeeded) < maxOffset) {
                            mHomeHandle.translationY = offsetNeeded
                        } else {
                            mHomeHandle.translationY = maxOffset * (if (offsetNeeded > 0) 1 else -1)
                        }
                    }
                    mHomeHandle.translationX = -(baseX - motionEvent.x) * 0.2f
                }

                // ACTION_UP or ACTION_POINTER_UP both end the gesture - the old code only
                // watched ACTION_UP, so multi-finger or cancelled gestures leaked the
                // motionTriggered flag and the handle never returned home.
                if ((motionEvent.actionMasked == MotionEvent.ACTION_UP
                            || motionEvent.actionMasked == MotionEvent.ACTION_POINTER_UP
                            || motionEvent.actionMasked == MotionEvent.ACTION_CANCEL)
                    && motionTriggered
                ) {
                    motionWatchdogRunnable?.let { mHandler?.removeCallbacks(it) }
                    val eventType = if (!mIsInHome) EventType.NORMAL else EventType.HOME
                    opacityHomeHandle(eventType)
                    animateHomeHandleXYToNormal(eventType)
                    motionTriggered = false
                }
            } finally {
                motionEvent.recycle()
            }
        }

        fun onInputEvent(inputEvent: InputEvent) {
            if (inputEvent is MotionEvent) {
                // Process directly in the callback thread (the receiver already runs on
                // the main looper). The old mHandler.post hop added a frame of latency and
                // let fast-fling event bursts queue up - causing lost animation frames.
                val motionEvent = MotionEvent.obtain(inputEvent)
                try {
                    onInputEventInner(motionEvent)
                } catch (t: Throwable) {
                    Log.w("Art_Chen", "[Xiaobaitiao] onInputEvent failed: ${t.message}")
                }
            }
        }

        fun onUserPresent() {
            if (topActivityObserver == null) return

            try {
                // HyperOS 4: the getter is gone; topActivity lives in the mState field
                // (TaskInfoState.topActivity), with mTopActivity as legacy fallback.
                val currentTopActivity = try {
                    XposedHelpers.getObjectField(
                        topActivityObserver,
                        "mTopActivity"
                    ) as ComponentName
                } catch (e: Throwable) {
                    val state = XposedHelpers.getObjectField(topActivityObserver, "mState")
                    XposedHelpers.getObjectField(state, "topActivity") as ComponentName
                }

                if (currentTopActivity.packageName == "com.miui.home") enterHome()
            } catch (t: Throwable) {
                Log.w("Art_Chen", "[Xiaobaitiao] onUserPresent failed: ${t.message}")
            }
        }

        // Hooks
        navBarInflaterViewCls.toClass().apply {
            if (!isAboveU) {
                method {
                    name = "createView"
                    param(StringClass, ViewGroupClass, LayoutInflaterClass)
                }.hook {
                    after {
                        val str = this.args[0] as String
                        if ("home_handle" == extractButton(str)) {
                            mHomeHandleId = (this.result as View).id
                        }
                    }
                }
            }

            constructor {
                param(ContextClass, AttributeSetClass)
            }.hook {
                after {
                    mContext = this.args[0] as Context
                    mHandler = Handler(Looper.getMainLooper())

                    // Input listener now defaults ON: it uses the official InputManagerGlobal
                    // gesture monitor (safe on OS4). The legacy fod path is unreachable on
                    // OS4 (isAboveU is always true there). Config can still force-disable.
                    val enableInputListener = mainPrefs.getBoolean("chen_debug_enable_input_listener", true)
                    if (enableInputListener) {
                        appClassLoader?.let { ChenInputEventDispatcher.init(mContext!!, it) }
                    } else {
                        Log.i("Art_Chen", "[Xiaobaitiao] input listener disabled by config (chen_debug_enable_input_listener)")
                    }
                    updateSettings(true)

                    if (enableInputListener && ChenInputEventDispatcher.isInited()) {
                        ChenInputEventDispatcher.registerInputEventListener(object : ChenInputEventListener {
                            override fun onInputEvent(inputEvent: InputEvent) {
                                onInputEvent(inputEvent)
                            }
                        })
                        Log.i("Art_Chen", "ChenInputEventDispatcher registered!")
                    }

                    // Register user-present listener (blur broadcast removed with the blur feature)
                    val eventListener = object : BroadcastReceiver() {
                        override fun onReceive(p0: Context?, p1: Intent?) {
                            if (p1?.action == Intent.ACTION_USER_PRESENT) {
                                onUserPresent()
                            }
                        }
                    }

                    val eventFilter = IntentFilter(Intent.ACTION_USER_PRESENT)
                    // Android 14+ requires an explicit export flag for non-system broadcasts,
                    // otherwise it throws SecurityException and crashes SystemUI on HyperOS 4.
                    mContext!!.registerReceiver(eventListener, eventFilter, Context.RECEIVER_EXPORTED)
                }
            }

            method {
                name = "inflateLayout"
                param(StringClass)
            }.hook {
                after {
                    // The whole init is fragile on OS4 (view tree / resource ids may change);
                    // any failure here must never crash SystemUI.
                    try {
                        // init home handle status
                        val mHorizontal =
                            XposedHelpers.getObjectField(this.instance, "mHorizontal") as FrameLayout
                        if (isAboveU && mHomeHandleId == -1) {
                            mHomeHandleId =
                                appResources!!.getIdentifier("home_handle", "id", appContext?.packageName)
                        }

                        val found = mHorizontal.findViewById<View>(mHomeHandleId)
                        if (found == null) {
                            // home_handle view missing on this OS build - disable gracefully
                            Log.w("Art_Chen", "[Xiaobaitiao] home_handle view not found (id=$mHomeHandleId), skip anim init")
                            return@after
                        }
                        mHomeHandle = found

                        // Let the handle draw outside its parent bounds when dragged upward -
                        // otherwise the OS4 nav window (60px) clips it and the bar looks
                        // "thinner" or vanishes mid-gesture.
                        try {
                            mHorizontal.clipChildren = false
                            mHorizontal.clipToPadding = false
                            (mHorizontal.parent as? android.view.ViewGroup)?.let { gp ->
                                gp.clipChildren = false
                                gp.clipToPadding = false
                            }
                        } catch (t: Throwable) {
                            Log.w("Art_Chen", "[Xiaobaitiao] clipChildren disable failed: ${t.message}")
                        }

                        if (XposedHelpers.getIntField(this.instance, "mNavBarMode") != 2) {
                            // Not supported. set mHomeHandle to stub view to prevent crash
                            mHomeHandle = TextView(mContext)
                            Toast.makeText(mContext, "Xiaobaitiao: Please disable Home Handle Anim on Virtual Key Mode !! * 3", Toast.LENGTH_LONG).show()
                        }

                        mainPrefs.reload()
                        normalSettings = getSetting(EventType.NORMAL)
                        pressedSettings = getSetting(EventType.PRESSED)
                        onHomeSettings = getSetting(EventType.HOME)

                        // Run async
                        mHandler?.post {
                            try {
                                opacityHomeHandle(EventType.NORMAL)
                                animateHomeHandleXYToNormal(EventType.NORMAL)
                                animateZoomTo(normalSettings.scaleX, normalSettings.scaleY, normalSettings.scaleAnimDuration)
                            } catch (t: Throwable) {
                                Log.w("Art_Chen", "[Xiaobaitiao] initial anim failed: ${t.message}")
                            }
                        }
                    } catch (t: Throwable) {
                        Log.w("Art_Chen", "[Xiaobaitiao] inflateLayout init failed: ${t.message}")
                    }
                }
            }
        }

        navigationBarCls.toClass().apply {
            method {
                name = "getBarLayoutParamsForRotation"
                param(IntType)
            }.hook {
                after {
                    // HyperOS 4 note: writing back the reflected InsetsProvider array crashes
                    // the native window manager (SIGSEGV, no Java exception). The plain
                    // lp.height *= 2 mutation is a safe Java-layer field and keeps working.
                    // So on OS4: keep height doubling (turbo) but never touch Insets.
                    val orientationFor = this.args[0] as Int
                    val lp = this.result as WindowManager.LayoutParams
                    val isTurboMode =
                        mainPrefs.getBoolean("chen_home_handle_anim_turbo_mode", false)

                    if (XposedHelpers.getIntField(this.instance, "mNavBarMode") != 2) {
                        if (origBarHeight > 0) {
                            lp.height = origBarHeight
                        }
                        if (orientationFor == 0) {
                            barHeight = origBarHeight
                        }
                        return@after
                    }

                    if (orientationFor == 0) {
                        origBarHeight = lp.height
                    }

                    if (isTurboMode && orientationFor == 0 && !isHyperOS4) {
                        // OS2/OS3: double height AND rewrite insets (legacy behavior)
                        lp.height *= 2
                    }
                    if (isTurboMode && orientationFor == 0 && isHyperOS4) {
                        // OS4: double height only. The inset rewrite is fatal here.
                        lp.height *= 2
                    }

                    if (!isHyperOS4) {
                        lateinit var inset: Insets
                        lateinit var insets: Array<Insets>
                        lateinit var insetsProviderArr: Array<*>
                        lateinit var insetsProviderOverride: Any
                        if (!isAboveU) {
                            insets =
                                XposedHelpers.getObjectField(
                                    lp,
                                    "providedInternalInsets"
                                ) as Array<Insets>

                            inset = insets[1]
                        } else {
                            // is U
                            insetsProviderArr = XposedHelpers.getObjectField(lp, "providedInsets") as Array<*>
                            insetsProviderOverride = insetsProviderArr[0]!!
                            inset = XposedHelpers.callMethod(insetsProviderOverride, "getInsetsSize") as Insets
                        }

                        if (mainPrefs.getBoolean(
                                "chen_home_handle_no_space",
                                false
                            ) && orientationFor == 0
                        ) {
                            inset = if (isAboveU) {
                                Insets.of(inset.left, 0, inset.right, 0)
                            } else {
                                Insets.of(inset.left, lp.height, inset.right, 0)
                            }
                        } else if (isTurboMode) {
                            inset = if (isAboveU) {
                                Insets.of(inset.left, inset.top, inset.right, origBarHeight)
                            } else {
                                Insets.of(inset.left, lp.height - origBarHeight, inset.right, 0)
                            }
                        }

                        // Apply Inset
                        if (!isAboveU) {
                            insets[1] = inset
                            XposedHelpers.setObjectField(lp, "providedInternalInsets", insets)
                        } else {
                            XposedHelpers.callMethod(insetsProviderOverride, "setInsetsSize", inset)
                            XposedHelpers.setObjectField(lp, "providedInsets", insetsProviderArr)
                        }
                    } else {
                        // HyperOS 4 immersive (no-space) via height only: shrink the window
                        // so apps get no reserved area for the handle. Inset rewriting is
                        // fatal on OS4, so we approximate immersion by reducing window height
                        // itself - the handle stays drawn inside a smaller window.
                        if (mainPrefs.getBoolean("chen_home_handle_no_space", false) && orientationFor == 0) {
                            // Don't shrink: OS4 enforces minimum nav window. Instead accept it.
                            // (Kept as a no-op to document the limitation.)
                        }
                    }
                    if (orientationFor == 0) {
                        barHeight = lp.height
                    }
                    Log.i(
                        "Art_Chen",
                        "screenHeight is ${getScreenHeight(mContext!!)}, realScreenHeight is ${getScreenRealHeight(mContext!!)}, origBarHeight $origBarHeight, currentBarHeight $barHeight"
                    )
                    screenHeight = getScreenHeight(mContext!!)
                    screenRealHeight = getScreenRealHeight(mContext!!)
                }
            }

            method {
                name = "repositionNavigationBar"
                param(IntType)
            }.hook {
                before {
                    orientation = this.args[0] as Int
                }
            }
        }

        val activityChangedNotify =
            if (!ChenUtils.isAboveAndroidVersion(ChenUtils.Companion.AndroidVersion.U)) {
                "com.miui.systemui.util.MiuiActivityUtil".toClass().method {
                    name = "notifyListeners"
                }
            } else {
                // on U
                "com.miui.systemui.functions.MiuiTopActivityObserver".toClass().method {
                    name = "onTaskMovedToFront"
                    paramCount = 1
                }
            }

        var mLastTopActivity: ComponentName? = null
        var mLastInSmallWindow = false
        activityChangedNotify.hook {
            after {
                // This callback runs on the system message loop; any uncaught throwable
                // here crashes SystemUI. Never let it escape.
                try {
                    if (topActivityObserver == null) topActivityObserver = this.instance

                    // HyperOS 4 removed mTopActivity field from MiuiTopActivityObserver.
                    // The hook param itself (ActivityManager.RunningTaskInfo from AOSP) carries
                    // a public topActivity field - prefer it, fall back to the legacy field.
                    val currentTopActivity: ComponentName = try {
                        XposedHelpers.getObjectField(this.args[0], "topActivity") as ComponentName
                    } catch (e: Throwable) {
                        XposedHelpers.getObjectField(this.instance, "mTopActivity") as ComponentName
                    }

                    // HyperOS 4 removed mInSmallWindow from MiuiTopActivityObserver
                    val mInSmallWindow = try {
                        XposedHelpers.getBooleanField(this.instance, "mInSmallWindow")
                    } catch (e: Throwable) {
                        false
                    }

                    if (mLastInSmallWindow == mInSmallWindow && mLastTopActivity == currentTopActivity) return@after
                    mLastInSmallWindow = mInSmallWindow
                    mLastTopActivity = currentTopActivity

                    val intent = Intent("chen.action.top_activity.switched")
                    intent.`package`= "com.miui.home"
                    // mContext may also be renamed on HyperOS 4 - fall back to appContext
                    val mUtilsContext = try {
                        XposedHelpers.getObjectField(this.instance, "mContext") as Context
                    } catch (e: Throwable) {
                        appContext
                    }

                    if (currentTopActivity.packageName == "com.miui.home") {
                        intent.putExtra("isEnteredHome", true)
                        mIsInHome = true
                        enterHome()
                    } else {
                        intent.putExtra("isEnteredHome", false)
                        mIsInHome = false
                        leaveHome()
                    }
                    mUtilsContext?.sendBroadcast(intent)
                } catch (t: Throwable) {
                    Log.w("Art_Chen", "[Xiaobaitiao] onTaskMovedToFront callback failed: ${t.message}")
                }
            }
        }

        "com.android.systemui.navigationbar.gestural.NavigationHandle".toClass().method {
            name = "setDarkIntensity"
            param(FloatType)
        }.hook {
            before {
                try {
                    if (mDarkColor == -1) {
                        mDarkColor = XposedHelpers.getIntField(this.instance, "mDarkColor")
                        mLightColor = XposedHelpers.getIntField(this.instance, "mLightColor")
                        mNavigationHandle = this.instance
                    } else {
                        XposedHelpers.setIntField(this.instance, "mDarkColor", mDarkColor)
                        XposedHelpers.setIntField(this.instance, "mLightColor", mLightColor)
                    }
                } catch (t: Throwable) {
                    Log.w("Art_Chen", "[Xiaobaitiao] setDarkIntensity failed: ${t.message}")
                }
            }
        }
        // NOTE: the experiment hooking NavigationHandle.setAlpha as a "gesture progress"
        // signal has been REMOVED. It could not distinguish system calls from our own
        // alpha-animator writes (View.alpha IS setAlpha), so every frame of our PRESSED
        // animation pushed translationY upward - the "bar twitches upward the instant
        // you touch it" bug. Also, during swipe-to-home the system fades the handle to
        // near zero, so the mirrored offset was invisible anyway.
        //
        // The in-app follow itself is provided by the system: SystemServerMainHooker forces
        // DisplayPolicy#mShouldAttachNavBarToAppDuringTransition to true, which makes the
        // navigation bar travel with the app window during the transition (see settings item
        // chen_navbar_attach_to_app). Drawing an extra handle ourselves was tried and dropped:
        // the real handle is set INVISIBLE for the duration of the gesture, and the whole bar
        // window is attached to the app, so an overlay would have to duplicate visual style,
        // color and dark-intensity handling for a very small gain.

        if (mainPrefs.getBoolean("home_handle_wa_no_space_not_hide", false)) {
            // Workaround immersive mode can not hide the handle
            navigationBarCls.toClass().method {
                name("setWindowState")
                param(IntType, IntType, IntType)
            }.hook {
                after {
                    try {
                        isHidden = XposedHelpers.getBooleanField(
                            this.instance,
                            "mShowOrientedHandleForImmersiveMode"
                        )
                        if (lastIsHidden == isHidden) return@after

                        if (isHidden) {
                            opacityTo(0f, 300)
                        } else {
                            opacityHomeHandle(EventType.NORMAL)
                        }
                        lastIsHidden = isHidden
                    } catch (t: Throwable) {
                        Log.w("Art_Chen", "[Xiaobaitiao] setWindowState hook failed: ${t.message}")
                    }
                }
            }
        }

        if (mainPrefs.getBoolean("chen_home_handle_no_space", false)) {
            val statusBarCls = "com.android.systemui.statusbar.phone.PhoneStatusBarTransitions".toClass()
            barTransitionsCls.toClass().apply {
                method {
                    name = "applyModeBackground"
                    paramCount = 2
                }.hook {
                    before {
                        // ignored status bar transition
                        if (statusBarCls.isInstance(this.instance)) return@before

                        val mode = this.args[0]
                        when (mode) {
                            1, 2, 3, 4 -> this.args[0] = 0
                        }
                    }
                }
            }
        }
    }

    private fun getScreenHeight(context: Context): Int {
        return context.resources?.displayMetrics?.heightPixels ?: 0
    }

    private var sRealSizes = arrayOfNulls<Point>(2)
    private fun getScreenRealHeight(context: Context): Int {
        var orientation = context.resources?.configuration?.orientation
        orientation = if (orientation == 1) 0 else 1
        if (sRealSizes[orientation] == null) {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val point = Point()
            display.getRealSize(point)
            sRealSizes[orientation] = point
        }
        return sRealSizes[orientation]?.y ?: getScreenRealHeight(context)
    }

    private fun getScreenRealWidth(context: Context): Int {
        var orientation = context.resources?.configuration?.orientation
        orientation = if (orientation == 1) 0 else 1
        if (sRealSizes[orientation] == null) {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val point = Point()
            display.getRealSize(point)
            sRealSizes[orientation] = point
        }
        return sRealSizes[orientation]?.x ?: getScreenRealWidth(context)
    }


    private fun extractButton(str: String): String {
        return if (!str.contains("[")) str else str.substring(0, str.indexOf("["))
    }

}
