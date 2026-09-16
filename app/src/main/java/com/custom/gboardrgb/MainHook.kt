package com.custom.gboardrgb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.*
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.ref.WeakReference
import kotlin.math.hypot
import kotlin.math.min

class MainHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "GboardRGBWave"
        private const val TARGET_PACKAGE = "com.google.android.inputmethod.latin"
        
        private var currentInputViewRef: WeakReference<ViewGroup>? = null
        private var currentKeyboardHolderRef: WeakReference<View>? = null
        private var currentOverlayRef: WeakReference<RGBRippleOverlayView>? = null
        private var isReceiverRegistered = false

        // Feature flags
        private var isHapticEnabled = true
        private var isTurboDynamicsEnabled = true
        private var isGlideTrailEnabled = true

        // WPM / Keystroke Timing Buffer
        private val recentTapTimes = LongArray(6)
        private var tapCounter = 0

        // Haptic Engine
        private var vibrator: Vibrator? = null

        // Long-Press Continuous Wave System
        private var holdHandler: Handler? = null
        private var isHoldingKey = false
        private var holdOverlayX = 0f
        private var holdOverlayY = 0f
        private var holdRawStartX = 0f
        private var holdRawStartY = 0f
        private val repeatWaveRunnable = object : Runnable {
            override fun run() {
                if (isHoldingKey) {
                    val overlay = currentOverlayRef?.get()
                    overlay?.spawnRipple(holdOverlayX, holdOverlayY)
                    val inputView = currentInputViewRef?.get()
                    if (inputView != null) {
                        triggerHaptic(inputView.context)
                    }
                    holdHandler?.postDelayed(this, 115)
                }
            }
        }

        private fun triggerHaptic(context: Context) {
            if (!isHapticEnabled) return
            try {
                if (vibrator == null) {
                    vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                        vm?.defaultVibrator
                    } else {
                        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    }
                }
                if (vibrator?.hasVibrator() == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                    } else {
                        vibrator?.vibrate(12)
                    }
                }
            } catch (e: Exception) {
                // Silently ignore if permission/hardware unavailable
            }
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return

        XposedBridge.log("[$TAG] Initializing Advanced Gboard RGB Suite...")

        try {
            // Hook 1: setInputView
            XposedHelpers.findAndHookMethod(
                "android.inputmethodservice.InputMethodService",
                lpparam.classLoader,
                "setInputView",
                View::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val inputView = param.args[0] as? ViewGroup ?: return
                        currentInputViewRef = WeakReference(inputView)
                        mountOverlaySafely(inputView)

                        val service = param.thisObject as? Context
                        if (service != null && !isReceiverRegistered) {
                            registerSettingsReceiver(service)
                        }
                    }
                }
            )

            // Hook 2: onWindowShown
            XposedHelpers.findAndHookMethod(
                "android.inputmethodservice.InputMethodService",
                lpparam.classLoader,
                "onWindowShown",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val inputView = currentInputViewRef?.get()
                        if (inputView != null) {
                            mountOverlaySafely(inputView)
                        }

                        val service = param.thisObject as? Context
                        if (service != null && !isReceiverRegistered) {
                            registerSettingsReceiver(service)
                        }
                    }
                }
            )

            // Hook 3: onStartInputView
            XposedHelpers.findAndHookMethod(
                "android.inputmethodservice.InputMethodService",
                lpparam.classLoader,
                "onStartInputView",
                "android.view.inputmethod.EditorInfo",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val inputView = currentInputViewRef?.get()
                        if (inputView != null) {
                            mountOverlaySafely(inputView)
                        }
                    }
                }
            )

            // Hook 4: ViewGroup.dispatchTouchEvent
            XposedHelpers.findAndHookMethod(
                ViewGroup::class.java,
                "dispatchTouchEvent",
                MotionEvent::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as? ViewGroup ?: return
                        val inputView = currentInputViewRef?.get() ?: return

                        if (view === inputView) {
                            val event = param.args[0] as? MotionEvent ?: return
                            val overlay = currentOverlayRef?.get() ?: return

                            val action = event.actionMasked
                            val ptrIdx = if (action == MotionEvent.ACTION_POINTER_DOWN) event.actionIndex else 0
                            val rawX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) event.getRawX(ptrIdx) else event.rawX
                            val rawY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) event.getRawY(ptrIdx) else event.rawY

                            when (action) {
                                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                                    handleTouchDown(rawX, rawY, overlay, inputView.context)
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    if (isHoldingKey) {
                                        val dist = hypot(rawX - holdRawStartX, rawY - holdRawStartY)
                                        if (dist > 35f) {
                                            stopHolding()
                                        }
                                    }
                                    if (isGlideTrailEnabled) {
                                        val overlayLoc = IntArray(2)
                                        overlay.getLocationOnScreen(overlayLoc)
                                        overlay.addGlidePoint(rawX - overlayLoc[0], rawY - overlayLoc[1])
                                    }
                                }
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
                                    stopHolding()
                                    if (isGlideTrailEnabled) {
                                        overlay.finishGlide()
                                    }
                                }
                            }
                        }
                    }
                }
            )

            XposedBridge.log("[$TAG] All advanced hooks active!")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Hook initialization failed: ${t.message}")
        }
    }

    private fun handleTouchDown(rawX: Float, rawY: Float, overlay: RGBRippleOverlayView, context: Context) {
        val overlayLoc = IntArray(2)
        overlay.getLocationOnScreen(overlayLoc)
        val overlayX = rawX - overlayLoc[0]
        val overlayY = rawY - overlayLoc[1]

        // 1. Calculate Real-Time WPM Turbo Factor
        if (isTurboDynamicsEnabled) {
            val now = SystemClock.elapsedRealtime()
            recentTapTimes[tapCounter % 6] = now
            tapCounter++
            val sampleCount = min(tapCounter, 6)
            if (sampleCount >= 3) {
                val oldest = recentTapTimes[(tapCounter - sampleCount) % 6]
                val avgDelta = (now - oldest) / (sampleCount - 1)
                overlay.turboFactor = when {
                    avgDelta < 150L -> 1.65f // Burst fast typing (> 70 WPM)
                    avgDelta < 220L -> 1.35f // Brisk typing (~50 WPM)
                    avgDelta < 300L -> 1.15f // Normal typing
                    else -> 1.0f
                }
            } else {
                overlay.turboFactor = 1.0f
            }
        } else {
            overlay.turboFactor = 1.0f
        }

        // 2. Trigger Tactile Haptic Micro-Tick
        triggerHaptic(context)

        // 3. Initial single tap wave
        overlay.spawnRipple(overlayX, overlayY)

        // 4. Setup long-press auto-repeat
        holdOverlayX = overlayX
        holdOverlayY = overlayY
        holdRawStartX = rawX
        holdRawStartY = rawY
        isHoldingKey = true

        if (holdHandler == null) {
            val looper = Looper.getMainLooper() ?: Looper.myLooper()
            if (looper != null) {
                holdHandler = Handler(looper)
            }
        }
        holdHandler?.removeCallbacksAndMessages(null)
        holdHandler?.postDelayed(repeatWaveRunnable, 280)
    }

    private fun stopHolding() {
        isHoldingKey = false
        holdHandler?.removeCallbacksAndMessages(null)
    }

    private fun registerSettingsReceiver(context: Context) {
        try {
            val filter = IntentFilter(ConfigManager.ACTION_UPDATE_SETTINGS)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent == null) return
                    val effectId = intent.getIntExtra(ConfigManager.EXTRA_EFFECT_ID, 0)
                    val speed = intent.getFloatExtra(ConfigManager.EXTRA_SPEED, 1.0f)
                    val size = intent.getFloatExtra(ConfigManager.EXTRA_SIZE, 1.0f)
                    val isAmbient = intent.getBooleanExtra(ConfigManager.EXTRA_AMBIENT_RAIN, true)

                    val useCustom = intent.getBooleanExtra(ConfigManager.EXTRA_USE_CUSTOM_COLORS, false)
                    val colPrimStr = intent.getStringExtra(ConfigManager.EXTRA_COLOR_PRIMARY) ?: "#00FFF5"
                    val colSecStr = intent.getStringExtra(ConfigManager.EXTRA_COLOR_SECONDARY) ?: "#FF00AA"
                    val turbo = intent.getBooleanExtra(ConfigManager.EXTRA_TURBO_DYNAMICS, true)
                    val glide = intent.getBooleanExtra(ConfigManager.EXTRA_GLIDE_TRAIL, true)
                    val haptic = intent.getBooleanExtra(ConfigManager.EXTRA_HAPTIC, true)

                    isHapticEnabled = haptic
                    isTurboDynamicsEnabled = turbo
                    isGlideTrailEnabled = glide

                    val overlay = currentOverlayRef?.get() ?: return
                    overlay.currentEffect = EffectType.fromId(effectId)
                    overlay.speedMultiplier = speed
                    overlay.sizeMultiplier = size
                    overlay.isAmbientRainEnabled = isAmbient
                    overlay.useCustomColors = useCustom
                    overlay.isTurboDynamicsEnabled = turbo
                    overlay.isGlideTrailEnabled = glide

                    try {
                        overlay.customColorPrimary = Color.parseColor(colPrimStr)
                        overlay.customColorSecondary = Color.parseColor(colSecStr)
                    } catch (e: Exception) {
                        // Keep current
                    }

                    XposedBridge.log("[$TAG] Real-time setting switch applied! customColors=$useCustom, turbo=$turbo, glide=$glide, haptic=$haptic")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            isReceiverRegistered = true
            XposedBridge.log("[$TAG] Real-time settings receiver registered in Gboard!")
        } catch (e: Exception) {
            XposedBridge.log("[$TAG] Failed to register receiver: ${e.message}")
        }
    }

    private fun mountOverlaySafely(inputView: ViewGroup) {
        inputView.post {
            try {
                val kbBody = findKeyboardHolder(inputView)
                if (kbBody != null) {
                    currentKeyboardHolderRef = WeakReference(kbBody)
                }

                var overlay = currentOverlayRef?.get()
                if (overlay != null && overlay.parent === inputView) {
                    if (kbBody != null) overlay.setKeyboardTarget(kbBody)
                    inputView.bringChildToFront(overlay)
                    overlay.bringToFront()
                    return@post
                }

                if (overlay != null && overlay.parent != null) {
                    (overlay.parent as? ViewGroup)?.removeView(overlay)
                }

                overlay = RGBRippleOverlayView(inputView.context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }

                val initialSettings = ConfigManager.loadSettings(null)
                overlay.currentEffect = initialSettings.effectType
                overlay.speedMultiplier = initialSettings.speedMultiplier
                overlay.sizeMultiplier = initialSettings.sizeMultiplier
                overlay.isAmbientRainEnabled = initialSettings.isAmbientRainEnabled
                overlay.useCustomColors = initialSettings.useCustomColors
                overlay.isTurboDynamicsEnabled = initialSettings.isTurboDynamicsEnabled
                overlay.isGlideTrailEnabled = initialSettings.isGlideTrailEnabled

                try {
                    overlay.customColorPrimary = Color.parseColor(initialSettings.colorPrimary)
                    overlay.customColorSecondary = Color.parseColor(initialSettings.colorSecondary)
                } catch (e: Exception) {
                    // Default
                }

                isHapticEnabled = initialSettings.isHapticEnabled
                isTurboDynamicsEnabled = initialSettings.isTurboDynamicsEnabled
                isGlideTrailEnabled = initialSettings.isGlideTrailEnabled

                if (kbBody != null) {
                    overlay.setKeyboardTarget(kbBody)
                }

                inputView.addView(overlay)
                inputView.bringChildToFront(overlay)
                overlay.bringToFront()
                currentOverlayRef = WeakReference(overlay)
                XposedBridge.log("[$TAG] Mounted overlay with effect: ${overlay.currentEffect.name}")
            } catch (e: Exception) {
                XposedBridge.log("[$TAG] Mount error: ${e.message}")
            }
        }
    }

    private fun findKeyboardHolder(view: View): View? {
        if (view !is ViewGroup) return null
        val name = view.javaClass.simpleName
        if (name.contains("KeyboardHolder") || name.contains("SoftKeyboardView")) {
            return view
        }
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            val found = findKeyboardHolder(child)
            if (found != null) return found
        }
        return null
    }
}
