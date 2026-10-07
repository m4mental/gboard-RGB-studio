package com.custom.gboardrgb

import android.annotation.SuppressLint
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
        private var currentRootViewRef: WeakReference<ViewGroup>? = null
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
        private var holdPointerId = -1
        private var holdOverlayX = 0f
        private var holdOverlayY = 0f
        private var holdRawStartX = 0f
        private var holdRawStartY = 0f
        private val repeatWaveRunnable = object : Runnable {
            override fun run() {
                if (isHoldingKey) {
                    val overlay = currentOverlayRef?.get()
                    if (overlay != null) {
                        val density = overlay.resources.displayMetrics.density
                        val jitterRadius = 5.5f * density
                        val jitterAngle = kotlin.random.Random.nextFloat() * 2f * Math.PI.toFloat()
                        val jitterDist = kotlin.random.Random.nextFloat() * jitterRadius
                        val jX = holdOverlayX + kotlin.math.cos(jitterAngle) * jitterDist
                        val jY = holdOverlayY + kotlin.math.sin(jitterAngle) * jitterDist
                        overlay.spawnRipple(jX, jY)
                    }
                    val ctx = currentInputViewRef?.get()?.context ?: overlay?.context ?: currentRootViewRef?.get()?.context
                    if (ctx != null) {
                        triggerHaptic(ctx)
                    }
                    holdHandler?.postDelayed(this, 115)
                }
            }
        }

        @SuppressLint("MissingPermission")
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

        // Hook 1: setInputView
        try {
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
            XposedBridge.log("[$TAG] Hook installed: setInputView")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Failed to hook setInputView: ${t.message}")
        }

        // Hook 2: onWindowShown
        try {
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
            XposedBridge.log("[$TAG] Hook installed: onWindowShown")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Failed to hook onWindowShown: ${t.message}")
        }

        // Hook 3: onStartInputView
        try {
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
            XposedBridge.log("[$TAG] Hook installed: onStartInputView")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Failed to hook onStartInputView: ${t.message}")
        }

        // Hook 4: ViewGroup.dispatchTouchEvent
        try {
            XposedHelpers.findAndHookMethod(
                ViewGroup::class.java,
                "dispatchTouchEvent",
                MotionEvent::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as? ViewGroup ?: return
                        if (view.context.packageName != TARGET_PACKAGE) return

                        val root = currentRootViewRef?.get() ?: (view.rootView as? ViewGroup)
                        val isRoot = (view === root) || (view.javaClass.simpleName == "DecorView")

                        // Process touch event exactly once per gesture at the top-level window/DecorView
                        if (!isRoot) return

                        if (currentRootViewRef?.get() == null) {
                            currentRootViewRef = WeakReference(view)
                        }

                        var overlay = currentOverlayRef?.get()
                        if (overlay == null || overlay.parent == null) {
                            mountOverlaySafely(view)
                            overlay = currentOverlayRef?.get() ?: return
                        }

                        val event = param.args[0] as? MotionEvent ?: return
                        val action = event.actionMasked

                        val ptrIdx = if (action == MotionEvent.ACTION_POINTER_DOWN) event.actionIndex else 0
                        val rawX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) event.getRawX(ptrIdx) else event.rawX
                        val rawY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) event.getRawY(ptrIdx) else event.rawY

                        // Find currently active keyboard body (supports docked, floating, one-handed)
                        var kb = currentKeyboardHolderRef?.get()
                        if (kb == null || !kb.isShown || kb.width <= 0 || kb.height <= 0) {
                            kb = findKeyboardHolder(view)
                            if (kb != null) {
                                currentKeyboardHolderRef = WeakReference(kb)
                                overlay.setKeyboardTarget(kb)
                            }
                        }

                        // Filter out touches outside keyboard area (e.g. background taps when in floating mode)
                        if (kb != null && kb.isShown && kb.width > 0 && kb.height > 0) {
                            val kbLoc = IntArray(2)
                            kb.getLocationOnScreen(kbLoc)
                            val left = kbLoc[0].toFloat()
                            val top = kbLoc[1].toFloat()
                            val right = left + kb.width
                            val bottom = top + kb.height

                            if (rawX < left || rawX > right || rawY < top || rawY > bottom) {
                                return
                            }
                        }

                        when (action) {
                            MotionEvent.ACTION_DOWN -> {
                                holdPointerId = event.getPointerId(0)
                                handleTouchDown(rawX, rawY, overlay, view.context)
                            }
                            MotionEvent.ACTION_POINTER_DOWN -> {
                                holdPointerId = event.getPointerId(event.actionIndex)
                                handleTouchDown(rawX, rawY, overlay, view.context)
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
                            MotionEvent.ACTION_POINTER_UP -> {
                                val releasedPointerId = event.getPointerId(event.actionIndex)
                                if (releasedPointerId == holdPointerId) {
                                    stopHolding()
                                }
                                if (isGlideTrailEnabled) {
                                    overlay.finishGlide()
                                }
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                stopHolding()
                                if (isGlideTrailEnabled) {
                                    overlay.finishGlide()
                                }
                            }
                        }
                    }
                }
            )
            XposedBridge.log("[$TAG] Hook installed: dispatchTouchEvent")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Failed to hook dispatchTouchEvent: ${t.message}")
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
        holdHandler?.removeCallbacks(repeatWaveRunnable)
        holdHandler?.postDelayed(repeatWaveRunnable, 280)
    }

    private fun stopHolding() {
        isHoldingKey = false
        holdPointerId = -1
        holdHandler?.removeCallbacks(repeatWaveRunnable)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerSettingsReceiver(context: Context) {
        try {
            val filter = IntentFilter(ConfigManager.ACTION_UPDATE_SETTINGS)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent == null) return
                    val effectId = intent.getIntExtra(ConfigManager.EXTRA_EFFECT_ID, 0)
                    val speed = intent.getFloatExtra(ConfigManager.EXTRA_SPEED, 1.0f)
                    val size = intent.getFloatExtra(ConfigManager.EXTRA_SIZE, 1.0f)
                    val waveSpeed = intent.getFloatExtra(ConfigManager.EXTRA_WAVE_SPEED, speed)
                    val waveSize = intent.getFloatExtra(ConfigManager.EXTRA_WAVE_SIZE, size)
                    val keyFlowSpeed = intent.getFloatExtra(ConfigManager.EXTRA_KEY_FLOW_SPEED, speed)
                    val keyFlowSize = intent.getFloatExtra(ConfigManager.EXTRA_KEY_FLOW_SIZE, size)
                    val isAmbient = intent.getBooleanExtra(ConfigManager.EXTRA_AMBIENT_RAIN, false)

                    val useCustom = intent.getBooleanExtra(ConfigManager.EXTRA_USE_CUSTOM_COLORS, false)
                    val colPrimStr = intent.getStringExtra(ConfigManager.EXTRA_COLOR_PRIMARY) ?: "#00FFF5"
                    val colSecStr = intent.getStringExtra(ConfigManager.EXTRA_COLOR_SECONDARY) ?: "#FF00AA"
                    val turbo = intent.getBooleanExtra(ConfigManager.EXTRA_TURBO_DYNAMICS, true)
                    val glide = intent.getBooleanExtra(ConfigManager.EXTRA_GLIDE_TRAIL, true)
                    val haptic = intent.getBooleanExtra(ConfigManager.EXTRA_HAPTIC, true)
                    val underglow = intent.getBooleanExtra(ConfigManager.EXTRA_UNDERGLOW, false)
                    val visualEffect = intent.getBooleanExtra(ConfigManager.EXTRA_VISUAL_EFFECT, true)
                    val keyFlow = intent.getBooleanExtra(ConfigManager.EXTRA_KEY_SHAPE_FLOW, true)
                    val borderOnly = intent.getBooleanExtra(ConfigManager.EXTRA_KEY_BORDER_ONLY, true)

                    val rawSettings = ConfigManager.Settings(
                        EffectType.fromId(effectId), speed, size, waveSpeed, waveSize, keyFlowSpeed, keyFlowSize,
                        isAmbient, useCustom, colPrimStr, colSecStr, turbo, glide, haptic, underglow, visualEffect, keyFlow, borderOnly
                    )
                    val sanitized = ConfigManager.sanitize(rawSettings)

                    // Apply static flags only after confirming payload validity
                    isHapticEnabled = sanitized.isHapticEnabled
                    isTurboDynamicsEnabled = sanitized.isTurboDynamicsEnabled
                    isGlideTrailEnabled = sanitized.isGlideTrailEnabled

                    val overlay = currentOverlayRef?.get() ?: return
                    overlay.currentEffect = sanitized.effectType
                    overlay.waveSpeedMultiplier = sanitized.waveSpeedMultiplier
                    overlay.waveSizeMultiplier = sanitized.waveSizeMultiplier
                    overlay.keyFlowSpeedMultiplier = sanitized.keyFlowSpeedMultiplier
                    overlay.keyFlowSizeMultiplier = sanitized.keyFlowSizeMultiplier
                    overlay.isAmbientRainEnabled = sanitized.isAmbientRainEnabled
                    overlay.useCustomColors = sanitized.useCustomColors
                    overlay.isTurboDynamicsEnabled = sanitized.isTurboDynamicsEnabled
                    overlay.isGlideTrailEnabled = sanitized.isGlideTrailEnabled
                    overlay.isUnderglowEnabled = sanitized.isUnderglowEnabled
                    overlay.isVisualEffectEnabled = sanitized.isVisualEffectEnabled
                    overlay.isKeyShapeFlowEnabled = sanitized.isKeyShapeFlowEnabled
                    overlay.isKeyBorderOnlyEnabled = sanitized.isKeyBorderOnlyEnabled

                    try {
                        overlay.customColorPrimary = Color.parseColor(sanitized.colorPrimary)
                        overlay.customColorSecondary = Color.parseColor(sanitized.colorSecondary)
                    } catch (e: Exception) {
                        // Keep current
                    }

                    ConfigManager.saveLocalGboardConfig(ctx ?: context, sanitized)
                    XposedBridge.log("[$TAG] Real-time setting switch applied! effect=${sanitized.effectType.name}")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    receiver,
                    filter,
                    ConfigManager.PERMISSION_SETTINGS_BROADCAST,
                    null,
                    Context.RECEIVER_EXPORTED
                )
            } else {
                context.registerReceiver(
                    receiver,
                    filter,
                    ConfigManager.PERMISSION_SETTINGS_BROADCAST,
                    null
                )
            }
            isReceiverRegistered = true
            XposedBridge.log("[$TAG] Real-time settings receiver registered in Gboard with broadcastPermission!")
        } catch (e: Exception) {
            XposedBridge.log("[$TAG] Failed to register receiver: ${e.message}")
        }
    }

    private fun mountOverlaySafely(anchorView: ViewGroup) {
        anchorView.post {
            try {
                val root = (anchorView.rootView as? ViewGroup) ?: anchorView
                currentRootViewRef = WeakReference(root)

                val kbBody = findKeyboardHolder(root)
                if (kbBody != null) {
                    currentKeyboardHolderRef = WeakReference(kbBody)
                }

                var overlay = currentOverlayRef?.get()
                if (overlay != null && overlay.parent === root) {
                    if (kbBody != null) overlay.setKeyboardTarget(kbBody)
                    root.bringChildToFront(overlay)
                    overlay.bringToFront()
                    return@post
                }

                if (overlay != null && overlay.parent != null) {
                    (overlay.parent as? ViewGroup)?.removeView(overlay)
                }

                overlay = RGBRippleOverlayView(root.context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }

                val initialSettings = ConfigManager.loadSettings(root.context)
                overlay.currentEffect = initialSettings.effectType
                overlay.waveSpeedMultiplier = initialSettings.waveSpeedMultiplier
                overlay.waveSizeMultiplier = initialSettings.waveSizeMultiplier
                overlay.keyFlowSpeedMultiplier = initialSettings.keyFlowSpeedMultiplier
                overlay.keyFlowSizeMultiplier = initialSettings.keyFlowSizeMultiplier
                overlay.isAmbientRainEnabled = initialSettings.isAmbientRainEnabled
                overlay.useCustomColors = initialSettings.useCustomColors
                overlay.isTurboDynamicsEnabled = initialSettings.isTurboDynamicsEnabled
                overlay.isGlideTrailEnabled = initialSettings.isGlideTrailEnabled
                overlay.isUnderglowEnabled = initialSettings.isUnderglowEnabled
                overlay.isVisualEffectEnabled = initialSettings.isVisualEffectEnabled
                overlay.isKeyShapeFlowEnabled = initialSettings.isKeyShapeFlowEnabled
                overlay.isKeyBorderOnlyEnabled = initialSettings.isKeyBorderOnlyEnabled

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

                root.addView(overlay)
                root.bringChildToFront(overlay)
                overlay.bringToFront()
                currentOverlayRef = WeakReference(overlay)
                XposedBridge.log("[$TAG] Mounted overlay on root: ${root.javaClass.simpleName} with effect: ${overlay.currentEffect.name}")

                // Asynchronously query SettingsProvider in background without blocking Gboard UI thread
                ConfigManager.syncFromProviderAsync(root.context) { freshSettings ->
                    root.post {
                        val sanitized = ConfigManager.sanitize(freshSettings)
                        isHapticEnabled = sanitized.isHapticEnabled
                        isTurboDynamicsEnabled = sanitized.isTurboDynamicsEnabled
                        isGlideTrailEnabled = sanitized.isGlideTrailEnabled

                        val activeOverlay = currentOverlayRef?.get() ?: return@post
                        activeOverlay.currentEffect = sanitized.effectType
                        activeOverlay.waveSpeedMultiplier = sanitized.waveSpeedMultiplier
                        activeOverlay.waveSizeMultiplier = sanitized.waveSizeMultiplier
                        activeOverlay.keyFlowSpeedMultiplier = sanitized.keyFlowSpeedMultiplier
                        activeOverlay.keyFlowSizeMultiplier = sanitized.keyFlowSizeMultiplier
                        activeOverlay.isAmbientRainEnabled = sanitized.isAmbientRainEnabled
                        activeOverlay.useCustomColors = sanitized.useCustomColors
                        activeOverlay.isTurboDynamicsEnabled = sanitized.isTurboDynamicsEnabled
                        activeOverlay.isGlideTrailEnabled = sanitized.isGlideTrailEnabled
                        activeOverlay.isUnderglowEnabled = sanitized.isUnderglowEnabled
                        activeOverlay.isVisualEffectEnabled = sanitized.isVisualEffectEnabled
                        activeOverlay.isKeyShapeFlowEnabled = sanitized.isKeyShapeFlowEnabled
                        activeOverlay.isKeyBorderOnlyEnabled = sanitized.isKeyBorderOnlyEnabled

                        try {
                            activeOverlay.customColorPrimary = Color.parseColor(sanitized.colorPrimary)
                            activeOverlay.customColorSecondary = Color.parseColor(sanitized.colorSecondary)
                        } catch (e: Exception) {}

                        activeOverlay.invalidate()
                        ConfigManager.saveLocalGboardConfig(root.context, sanitized)
                    }
                }
            } catch (e: Exception) {
                XposedBridge.log("[$TAG] Mount error: ${e.message}")
            }
        }
    }

    private fun findKeyboardHolder(view: View): View? {
        val found = searchKeyboardHolder(view)
        if (found == null) {
            XposedBridge.log("[$TAG] Diagnostic: Neither KeyboardHolder nor SoftKeyboardView found in view tree under ${view.javaClass.simpleName}")
        }
        return found
    }

    private fun searchKeyboardHolder(view: View): View? {
        if (view !is ViewGroup) return null
        if (!view.isShown || view.visibility != View.VISIBLE || view.width <= 0 || view.height <= 0) {
            return null
        }
        val name = view.javaClass.simpleName
        if (name.contains("KeyboardHolder")) {
            return view
        }
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            val found = searchKeyboardHolder(child)
            if (found != null) return found
        }
        if (name.contains("SoftKeyboardView")) {
            return view
        }
        return null
    }
}
