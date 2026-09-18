package com.custom.gboardrgb

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.*
import kotlin.random.Random

class RGBRippleOverlayView(context: Context) : View(context) {

    private var keyboardTargetRef: WeakReference<View>? = null

    var currentEffect: EffectType = EffectType.WATER_DROP
    var waveSpeedMultiplier: Float = 1.0f
    var waveSizeMultiplier: Float = 1.0f
    var keyFlowSpeedMultiplier: Float = 1.0f
    var keyFlowSizeMultiplier: Float = 1.0f

    var speedMultiplier: Float
        get() = waveSpeedMultiplier
        set(value) {
            waveSpeedMultiplier = value
            keyFlowSpeedMultiplier = value
        }

    var sizeMultiplier: Float
        get() = waveSizeMultiplier
        set(value) {
            waveSizeMultiplier = value
            keyFlowSizeMultiplier = value
        }

    // 1. Custom Dual-Tone Colors
    var useCustomColors: Boolean = false
    var customColorPrimary: Int = Color.parseColor("#00FFF5")
    var customColorSecondary: Int = Color.parseColor("#FF00AA")

    // 2. Turbo WPM Dynamics
    var isTurboDynamicsEnabled: Boolean = true
    var turboFactor: Float = 1.0f

    // 3. Swipe / Glide Neon Laser Trail
    var isGlideTrailEnabled: Boolean = true
    private data class GlidePoint(val x: Float, val y: Float, val time: Long)
    private val glidePoints = CopyOnWriteArrayList<GlidePoint>()
    private var glideAlpha = 1.0f
    private var glideFadeAnimator: ValueAnimator? = null

    // 4. Perimeter Underglow (Mechanical Keyboard Edge Lighting)
    var isUnderglowEnabled: Boolean = false
        set(value) {
            field = value
            if (value) {
                startUnderglowAnimation()
            }
            invalidate()
        }
    private var underglowPulseIntensity: Float = 0.0f
    private var lastUnderglowFrameTime: Long = 0L

    // 5. Visual Effect Presets (Canvas ripple, sparks, ambient splash)
    var isVisualEffectEnabled: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    // 6. Mechanical Key Matrix (Fluid travels through keycap shapes)
    var isKeyShapeFlowEnabled: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    // 6. Mechanical Key Border Rim Only (Glow key outlines only, keep letters crisp & visible)
    var isKeyBorderOnlyEnabled: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private val density = context.resources.displayMetrics.density

    var isAmbientRainEnabled: Boolean = false
        set(value) {
            field = value
            mainHandler.removeCallbacks(ambientRainRunnable)
            if (value) {
                mainHandler.postDelayed(ambientRainRunnable, 1500)
            }
        }

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
        elevation = 3000f
        translationZ = 3000f
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
    override fun onTouchEvent(event: MotionEvent): Boolean = false

    fun setKeyboardTarget(view: View) {
        keyboardTargetRef = WeakReference(view)
        postInvalidate()
    }

    fun getActiveKeyboardTarget(): View? {
        val current = keyboardTargetRef?.get()
        if (current != null && current.isShown && current.width > 0 && current.height > 0) {
            return current
        }
        val root = (rootView as? ViewGroup) ?: (parent as? ViewGroup)
        val discovered = root?.let { findVisibleKeyboardHolder(it) }
        if (discovered != null) {
            keyboardTargetRef = WeakReference(discovered)
            return discovered
        }
        return null
    }

    private fun findVisibleKeyboardHolder(view: View): View? {
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
            val found = findVisibleKeyboardHolder(child)
            if (found != null) return found
        }
        if (name.contains("SoftKeyboardView")) {
            return view
        }
        return null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
    }

    // --- Palettes ---
    private val liquidWaterPalette = intArrayOf(
        Color.parseColor("#00FFF5"), Color.parseColor("#0099FF"), Color.parseColor("#7A00FF"),
        Color.parseColor("#FF00AA"), Color.parseColor("#FF3366"), Color.parseColor("#00FFB2"), Color.parseColor("#00FFF5")
    )
    private val liquidPositions = floatArrayOf(0.0f, 0.16f, 0.33f, 0.50f, 0.67f, 0.84f, 1.0f)

    private val chromaColors = intArrayOf(
        Color.parseColor("#FF0055"), Color.parseColor("#FF6600"), Color.parseColor("#FFEE00"),
        Color.parseColor("#00FF66"), Color.parseColor("#00F5FF"), Color.parseColor("#0066FF"),
        Color.parseColor("#AA00FF"), Color.parseColor("#FF0099"), Color.parseColor("#FF0055")
    )
    private val chromaPositions = floatArrayOf(0.0f, 0.125f, 0.25f, 0.375f, 0.5f, 0.625f, 0.75f, 0.875f, 1.0f)

    private val magmaColors = intArrayOf(
        Color.parseColor("#FF1100"), Color.parseColor("#FF6600"), Color.parseColor("#FFAA00"),
        Color.parseColor("#FFDD00"), Color.parseColor("#FF3300"), Color.parseColor("#FF1100")
    )

    private val sonicColors = intArrayOf(
        Color.parseColor("#00FF66"), Color.parseColor("#00FFAA"), Color.parseColor("#00F5FF"),
        Color.parseColor("#0088FF"), Color.parseColor("#00FF66")
    )

    private val lightningColors = intArrayOf(
        Color.parseColor("#00F5FF"), Color.parseColor("#FFFFFF"),
        Color.parseColor("#B026FF"), Color.parseColor("#00FFFF"), Color.parseColor("#00F5FF")
    )

    private val supernovaColors = intArrayOf(
        Color.parseColor("#FF00FF"), Color.parseColor("#00FFFF"),
        Color.parseColor("#FFEE55"), Color.parseColor("#FFFFFF"),
        Color.parseColor("#AA00FF"), Color.parseColor("#FF00FF")
    )

    private val blackHoleColors = intArrayOf(
        Color.parseColor("#7A00FF"), Color.parseColor("#B026FF"),
        Color.parseColor("#00F5FF"), Color.parseColor("#2A0066"), Color.parseColor("#7A00FF")
    )

    private fun getActiveLiquidPalette(): IntArray {
        if (useCustomColors) {
            return intArrayOf(
                customColorPrimary, customColorSecondary, customColorPrimary,
                customColorSecondary, customColorPrimary
            )
        }
        return liquidWaterPalette
    }

    private fun getActiveChromaPalette(): IntArray {
        if (useCustomColors) {
            return intArrayOf(
                customColorPrimary, customColorSecondary, customColorPrimary,
                customColorSecondary, customColorPrimary
            )
        }
        return chromaColors
    }

    private fun getActivePresetPalette(type: EffectType): IntArray {
        if (useCustomColors) {
            return intArrayOf(
                customColorPrimary, customColorSecondary, customColorPrimary,
                customColorSecondary, customColorPrimary
            )
        }
        return when (type) {
            EffectType.RAZER_CHROMA -> chromaColors
            EffectType.MOLTEN_MAGMA -> magmaColors
            EffectType.SONIC_WAVE -> sonicColors
            EffectType.NEON_LIGHTNING -> lightningColors
            EffectType.COSMIC_SUPERNOVA -> supernovaColors
            EffectType.BLACK_HOLE -> blackHoleColors
            EffectType.WATER_DROP, EffectType.AMBIENT_RAIN -> liquidWaterPalette
        }
    }

    private val activeEffects = CopyOnWriteArrayList<ActiveEffect>()

    // Standard high-performance hardware-accelerated paints
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val glidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val glideGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val underglowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val underglowGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val underglowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Mechanical Keycap Matrix Paints (Keycap outlines & interior luminescence)
    private val keycapStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val keycapGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val keycapFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    data class KeycapInfo(val rect: RectF, var cornerRadius: Float)
    private val cachedKeycapRects = mutableListOf<KeycapInfo>()
    private var lastKeyScanTime: Long = 0L
    private var lastKbTargetHashCode: Int = 0
    private val myScreenLoc = IntArray(2)
    private val tempViewLoc = IntArray(2)

    private data class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        val color: Int, val maxRadius: Float
    )

    private data class LightningBolt(val path: Path, val color: Int)

    private data class ActiveEffect(
        val type: EffectType,
        val originX: Float,
        val originY: Float,
        var progress: Float = 0f,
        var waveProgress: Float = 0f,
        var keyFlowProgress: Float = 0f,
        val maxRadius: Float,
        val waveMaxRadius: Float = maxRadius,
        val keyFlowMaxRadius: Float = maxRadius,
        val colorPhaseOffset: Float = 0f,
        val isMiniDrop: Boolean = false,
        val particles: List<Particle> = emptyList(),
        val lightningBolts: List<LightningBolt> = emptyList()
    )

    private val fluidInterpolator = Interpolator { t -> (1.0f - (1.0f - t).pow(2.8f)) }
    private val decelerateInterpolator = DecelerateInterpolator(1.6f)

    // --- Glide Laser Trail Methods ---
    fun addGlidePoint(x: Float, y: Float) {
        if (!isGlideTrailEnabled) return
        glideFadeAnimator?.cancel()
        glideAlpha = 1.0f
        val now = System.currentTimeMillis()
        glidePoints.add(GlidePoint(x, y, now))
        while (glidePoints.size > 26) {
            glidePoints.removeAt(0)
        }
        if (isUnderglowEnabled && underglowPulseIntensity < 0.6f) {
            underglowPulseIntensity = 0.6f
            startUnderglowAnimation()
        }
        invalidate()
    }

    fun finishGlide() {
        if (glidePoints.isEmpty()) return
        glideFadeAnimator?.cancel()
        glideFadeAnimator = ValueAnimator.ofFloat(1.0f, 0f).apply {
            duration = 180L
            addUpdateListener { anim ->
                glideAlpha = anim.animatedValue as Float
                invalidate()
                if (glideAlpha <= 0.02f) {
                    glidePoints.clear()
                }
            }
            start()
        }
    }

    private fun drawGlideTrail(canvas: Canvas) {
        val count = glidePoints.size
        if (count < 2) return

        val headColor = if (useCustomColors) customColorPrimary else Color.parseColor("#00FFF5")
        val tailColor = if (useCustomColors) customColorSecondary else Color.parseColor("#7A00FF")

        for (i in 0 until count - 1) {
            val p1 = glidePoints[i]
            val p2 = glidePoints[i + 1]
            val r = (i + 1).toFloat() / count

            // Outer Neon Glow
            glideGlowPaint.color = tailColor
            glideGlowPaint.strokeWidth = 22f * r + 4f
            glideGlowPaint.alpha = (90 * r * glideAlpha).toInt().coerceIn(0, 255)
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, glideGlowPaint)

            // Core Laser Beam
            glidePaint.color = headColor
            glidePaint.strokeWidth = 11f * r + 2.5f
            glidePaint.alpha = (255 * r * glideAlpha).toInt().coerceIn(0, 255)
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, glidePaint)
        }
    }

    // --- Ambient Raindrop Loop ---
    private val mainHandler = Handler(Looper.getMainLooper())

    private val ambientRainRunnable = object : Runnable {
        override fun run() {
            if (!isAmbientRainEnabled) {
                return
            }
            spawnAmbientDrop()
            mainHandler.postDelayed(this, 2000)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isAmbientRainEnabled) {
            mainHandler.removeCallbacks(ambientRainRunnable)
            mainHandler.postDelayed(ambientRainRunnable, 1000)
        }
        if (isUnderglowEnabled) {
            startUnderglowAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mainHandler.removeCallbacks(ambientRainRunnable)
    }

    fun spawnAmbientDrop() {
        if (!isAmbientRainEnabled || !isVisualEffectEnabled) return

        val kb = keyboardTargetRef?.get()
        val kbW = kb?.width?.toFloat() ?: (if (width > 0) width.toFloat() else 1080f)
        val kbH = kb?.height?.toFloat() ?: (if (height > 0) height.toFloat() else 850f)
        if (kbW <= 50f || kbH <= 50f) return

        val kbLoc = IntArray(2)
        val myLoc = IntArray(2)
        kb?.getLocationOnScreen(kbLoc)
        getLocationOnScreen(myLoc)

        val left = if (kb != null) (kbLoc[0] - myLoc[0]).toFloat() else 0f
        val top = if (kb != null) (kbLoc[1] - myLoc[1]).toFloat() else 0f

        val randX = left + Random.nextFloat() * (kbW * 0.86f) + (kbW * 0.07f)
        val randY = top + Random.nextFloat() * (kbH * 0.72f) + (kbH * 0.14f)

        spawnEffectInternal(EffectType.WATER_DROP, randX, randY, isMiniDrop = true)
    }

    fun spawnRipple(touchX: Float, touchY: Float) {
        if (!isVisualEffectEnabled && !isKeyShapeFlowEnabled) {
            if (isUnderglowEnabled) pulseUnderglow()
            return
        }
        spawnEffectInternal(currentEffect, touchX, touchY, isMiniDrop = false)
    }

    private fun spawnEffectInternal(effectType: EffectType, touchX: Float, touchY: Float, isMiniDrop: Boolean) {
        val target = keyboardTargetRef?.get()
        val kbW = target?.width?.toFloat() ?: (if (width > 0) width.toFloat() else 1080f)
        val kbH = target?.height?.toFloat() ?: (if (height > 0) height.toFloat() else 850f)

        val turbo = if (isTurboDynamicsEnabled && !isMiniDrop) turboFactor else 1.0f
        val baseWaveRadius = max(kbW, kbH) * (if (isMiniDrop) 0.38f else 0.95f) * waveSizeMultiplier * (1.0f + (turbo - 1.0f) * 0.22f)
        val baseKeyFlowRadius = max(kbW, kbH) * (if (isMiniDrop) 0.38f else 0.95f) * keyFlowSizeMultiplier * (1.0f + (turbo - 1.0f) * 0.22f)

        if (!isMiniDrop) {
            pulseUnderglow()
        }

        val particles = when (effectType) {
            EffectType.COSMIC_SUPERNOVA -> generateSupernovaParticles(touchX, touchY, turbo)
            EffectType.MOLTEN_MAGMA -> generateMagmaParticles(touchX, touchY, turbo)
            else -> emptyList()
        }

        val lightningBolts = when (effectType) {
            EffectType.NEON_LIGHTNING -> generateLightningBolts(touchX, touchY, baseWaveRadius * 0.75f, turbo)
            else -> emptyList()
        }

        val randomPhase = Random.nextFloat()
        val effect = ActiveEffect(
            type = effectType,
            originX = touchX,
            originY = touchY,
            progress = 0f,
            waveProgress = 0f,
            keyFlowProgress = 0f,
            maxRadius = baseWaveRadius,
            waveMaxRadius = baseWaveRadius,
            keyFlowMaxRadius = baseKeyFlowRadius,
            colorPhaseOffset = randomPhase,
            isMiniDrop = isMiniDrop,
            particles = particles,
            lightningBolts = lightningBolts
        )
        activeEffects.add(effect)

        val baseDuration = when (effectType) {
            EffectType.NEON_LIGHTNING -> 420L
            EffectType.SONIC_WAVE -> 600L
            EffectType.RAZER_CHROMA -> 520L
            EffectType.MOLTEN_MAGMA -> 700L
            EffectType.BLACK_HOLE -> 750L
            EffectType.COSMIC_SUPERNOVA -> 780L
            EffectType.WATER_DROP, EffectType.AMBIENT_RAIN -> if (isMiniDrop) 680L else 780L
        }

        fun calcDuration(base: Long, speedMult: Float): Long {
            return if (speedMult < 1.0f) {
                (base / speedMult.toDouble().pow(1.6)).toLong()
            } else {
                (base / speedMult.coerceIn(0.2f, 3.0f)).toLong()
            }
        }

        val waveDuration = calcDuration(baseDuration, waveSpeedMultiplier)
        val keyFlowDuration = calcDuration(baseDuration, keyFlowSpeedMultiplier)

        val totalDuration = max(
            if (isVisualEffectEnabled) waveDuration else 0L,
            if (isKeyShapeFlowEnabled) keyFlowDuration else 0L
        ).coerceAtLeast(100L)

        fun getSpeedInterpolator(speedMult: Float): Interpolator {
            return if (speedMult < 0.8f) {
                val power = 1.15f + 1.25f * (speedMult / 0.8f).coerceIn(0f, 1f)
                Interpolator { t -> (1.0f - (1.0f - t).pow(power)) }
            } else when (effectType) {
                EffectType.WATER_DROP, EffectType.AMBIENT_RAIN -> fluidInterpolator
                else -> decelerateInterpolator
            }
        }

        val waveInterpolator = getSpeedInterpolator(waveSpeedMultiplier)
        val keyFlowInterpolator = getSpeedInterpolator(keyFlowSpeedMultiplier)

        post {
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = totalDuration
                this.interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener { anim ->
                    val elapsed = anim.currentPlayTime
                    val waveRaw = (elapsed.toFloat() / waveDuration.coerceAtLeast(1L)).coerceIn(0f, 1f)
                    val keyFlowRaw = (elapsed.toFloat() / keyFlowDuration.coerceAtLeast(1L)).coerceIn(0f, 1f)

                    val wP = waveInterpolator.getInterpolation(waveRaw)
                    val kP = keyFlowInterpolator.getInterpolation(keyFlowRaw)

                    effect.waveProgress = wP
                    effect.progress = wP
                    effect.keyFlowProgress = kP

                    val drag = 1.0f - (0.055f * waveSpeedMultiplier.coerceIn(0.2f, 1.2f))
                    for (p in effect.particles) {
                        p.x += p.vx
                        p.y += p.vy
                        p.vx *= drag
                        p.vy *= drag
                    }
                    invalidate()
                }
            }
            animator.start()
        }
    }

    private fun generateSupernovaParticles(originX: Float, originY: Float, turbo: Float): List<Particle> {
        val list = mutableListOf<Particle>()
        val colors = if (useCustomColors) {
            intArrayOf(customColorPrimary, customColorSecondary, Color.WHITE)
        } else {
            intArrayOf(
                Color.parseColor("#FF00FF"), Color.parseColor("#00FFFF"),
                Color.parseColor("#FFEE55"), Color.parseColor("#FFFFFF"),
                Color.parseColor("#AA00FF")
            )
        }
        val count = (18 * turbo).toInt().coerceIn(12, 36)
        val speedScale = 0.45f + 0.55f * waveSpeedMultiplier.coerceIn(0.2f, 1.2f)
        for (i in 0 until count) {
            val angle = (i.toFloat() / count) * 2 * Math.PI.toFloat() + (Random.nextFloat() - 0.5f) * 0.4f
            val speed = (Random.nextFloat() * 12f + 4f) * (1.0f + (turbo - 1.0f) * 0.35f) * speedScale
            list.add(
                Particle(
                    x = originX, y = originY,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = colors[Random.nextInt(colors.size)],
                    maxRadius = (Random.nextFloat() * 5f + 3f) * (1.0f + (turbo - 1.0f) * 0.2f)
                )
            )
        }
        return list
    }

    private fun generateMagmaParticles(originX: Float, originY: Float, turbo: Float): List<Particle> {
        val list = mutableListOf<Particle>()
        val colors = if (useCustomColors) {
            intArrayOf(customColorPrimary, customColorSecondary, Color.WHITE)
        } else {
            intArrayOf(
                Color.parseColor("#FF2200"), Color.parseColor("#FF7700"),
                Color.parseColor("#FFCC00"), Color.parseColor("#FFFFFF")
            )
        }
        val count = (12 * turbo).toInt().coerceIn(8, 26)
        val speedScale = 0.45f + 0.55f * waveSpeedMultiplier.coerceIn(0.2f, 1.2f)
        for (i in 0 until count) {
            val vx = (Random.nextFloat() - 0.5f) * 14f * turbo * speedScale
            val vy = -(Random.nextFloat() * 18f + 6f) * turbo * speedScale
            list.add(
                Particle(
                    x = originX, y = originY,
                    vx = vx, vy = vy,
                    color = colors[Random.nextInt(colors.size)],
                    maxRadius = (Random.nextFloat() * 4.5f + 2f) * (1.0f + (turbo - 1.0f) * 0.2f)
                )
            )
        }
        return list
    }

    private fun generateLightningBolts(originX: Float, originY: Float, reach: Float, turbo: Float): List<LightningBolt> {
        val bolts = mutableListOf<LightningBolt>()
        val colors = if (useCustomColors) {
            intArrayOf(customColorPrimary, customColorSecondary, Color.WHITE)
        } else {
            intArrayOf(
                Color.parseColor("#00F5FF"), Color.parseColor("#FFFFFF"),
                Color.parseColor("#B026FF"), Color.parseColor("#00FFFF")
            )
        }
        val boltCount = if (turbo > 1.25f) Random.nextInt(5, 8) else Random.nextInt(4, 6)
        for (i in 0 until boltCount) {
            val path = Path()
            path.moveTo(originX, originY)
            val baseAngle = (i.toFloat() / boltCount) * 2 * Math.PI.toFloat() + (Random.nextFloat() - 0.5f) * 0.5f
            var currX = originX
            var currY = originY
            val segments = 6
            val stepDist = (reach * (1.0f + (turbo - 1.0f) * 0.2f)) / segments

            for (s in 1..segments) {
                val segAngle = baseAngle + (Random.nextFloat() - 0.5f) * 0.8f
                currX += cos(segAngle) * stepDist
                currY += sin(segAngle) * stepDist
                path.lineTo(currX, currY)
            }
            bolts.add(LightningBolt(path, colors[Random.nextInt(colors.size)]))
        }
        return bolts
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (activeEffects.isEmpty() && glidePoints.isEmpty() && !isUnderglowEnabled) return

        val kb = getActiveKeyboardTarget()
        canvas.save()

        val left: Float
        val top: Float
        val right: Float
        val bottom: Float

        if (kb != null && kb.isShown && kb.width > 0 && kb.height > 0) {
            val kbLoc = IntArray(2)
            kb.getLocationOnScreen(kbLoc)
            val myLoc = IntArray(2)
            getLocationOnScreen(myLoc)
            left = (kbLoc[0] - myLoc[0]).toFloat()
            top = (kbLoc[1] - myLoc[1]).toFloat()
            right = left + kb.width
            bottom = top + kb.height
        } else {
            left = 0f
            top = 0f
            right = width.toFloat()
            bottom = height.toFloat()
        }

        // 1. Render Perimeter Underglow (Mechanical Edge Lighting) around keyboard boundaries
        if (isUnderglowEnabled && right > left && bottom > top) {
            drawPerimeterUnderglow(canvas, left, top, right, bottom)
        }

        // 2. Clip key ripples and laser trails strictly inside keyboard boundaries
        canvas.clipRect(left, top, right, bottom)

        // 3. Render Swipe/Glide Neon Laser Trail
        if (isGlideTrailEnabled && glidePoints.size >= 2) {
            drawGlideTrail(canvas)
        }

        val keycaps = if (isKeyShapeFlowEnabled) getKeycapRects(kb, left, top, right, bottom) else emptyList()

        val iterator = activeEffects.iterator()
        while (iterator.hasNext()) {
            val fx = iterator.next()
            val waveDone = !isVisualEffectEnabled || fx.waveProgress >= 0.99f
            val keyFlowDone = !isKeyShapeFlowEnabled || fx.keyFlowProgress >= 0.99f

            if (waveDone && keyFlowDone) {
                activeEffects.remove(fx)
                continue
            }

            // 1. Keycap Matrix Illumination (Cascades across key shapes)
            if (isKeyShapeFlowEnabled && keycaps.isNotEmpty() && fx.keyFlowProgress < 0.99f) {
                val kp = fx.keyFlowProgress
                val energyFade = getEffectFade(kp, 1.3f, keyFlowSpeedMultiplier)
                val baseR = kp * fx.keyFlowMaxRadius
                val palette = getActivePresetPalette(fx.type)
                drawKeycapMatrixFlow(canvas, fx, keycaps, kp, baseR, energyFade, palette)
            }

            // 2. Visual Effects Background Preset (Canvas water drops, particles, lightning arcs, etc.)
            if (isVisualEffectEnabled && fx.waveProgress < 0.99f) {
                when (fx.type) {
                    EffectType.WATER_DROP, EffectType.AMBIENT_RAIN -> drawFluidWaterBackground(canvas, fx)
                    EffectType.NEON_LIGHTNING -> drawNeonLightning(canvas, fx)
                    EffectType.COSMIC_SUPERNOVA -> drawCosmicSupernova(canvas, fx)
                    EffectType.MOLTEN_MAGMA -> drawMoltenMagma(canvas, fx)
                    EffectType.SONIC_WAVE -> drawSonicWave(canvas, fx)
                    EffectType.BLACK_HOLE -> drawBlackHole(canvas, fx)
                    EffectType.RAZER_CHROMA -> drawRazerChromaBackground(canvas, fx)
                }
            }
        }

        canvas.restore()

        if (isUnderglowEnabled && isShown) {
            postInvalidateOnAnimation()
        }
    }

    // --- 1. Fluid Water Droplet ---
    private fun getEffectFade(progress: Float, baseExponent: Float = 1.3f, speedMult: Float = waveSpeedMultiplier): Float {
        val exp = if (speedMult < 0.8f) {
            baseExponent * (0.6f + 0.4f * (speedMult / 0.8f))
        } else {
            baseExponent
        }
        return (1.0f - progress).pow(exp)
    }

    private fun drawFluidWaterBackground(canvas: Canvas, fx: ActiveEffect) {
        val p = fx.progress
        val energyFade = getEffectFade(p, 1.3f)
        val baseR = p * fx.maxRadius
        val palette = getActiveLiquidPalette()

        // Continuous Organic Liquid Caustic Ripples
        canvas.save()
        canvas.translate(fx.originX, fx.originY)

        val shader = SweepGradient(0f, 0f, palette, null)
        val mat = Matrix()
        mat.setRotate(fx.colorPhaseOffset * 360f + p * 90f)
        shader.setLocalMatrix(mat)

        if (p < 0.28f) {
            val splashAlpha = ((1.0f - (p / 0.28f)) * 180).toInt().coerceIn(0, 255)
            fillPaint.shader = null
            fillPaint.color = if (useCustomColors) customColorPrimary else Color.parseColor("#8000FFF5")
            fillPaint.alpha = splashAlpha
            canvas.drawCircle(0f, 0f, 25f * (1.0f + p * 2.5f), fillPaint)
        }

        glowPaint.shader = shader
        glowPaint.strokeWidth = 36f * energyFade + 8f
        glowPaint.alpha = (energyFade * 90).toInt().coerceIn(0, 255)
        canvas.drawCircle(0f, 0f, baseR, glowPaint)

        strokePaint.shader = shader
        strokePaint.strokeWidth = 14f * energyFade + 3f
        strokePaint.alpha = (energyFade * 255).toInt().coerceIn(0, 255)
        canvas.drawCircle(0f, 0f, baseR, strokePaint)

        if (baseR > 40f) {
            val r2 = baseR - 28f
            strokePaint.strokeWidth = 9f * energyFade + 2f
            strokePaint.alpha = (energyFade * 180).toInt().coerceIn(0, 255)
            canvas.drawCircle(0f, 0f, r2, strokePaint)
        }

        if (baseR > 80f) {
            val r3 = baseR - 58f
            strokePaint.strokeWidth = 6f * energyFade + 1.5f
            strokePaint.alpha = (energyFade * 110).toInt().coerceIn(0, 255)
            canvas.drawCircle(0f, 0f, r3, strokePaint)
        }

        canvas.restore()
    }

    // --- 2. Cyberpunk Neon Lightning ---
    private fun drawNeonLightning(canvas: Canvas, fx: ActiveEffect) {
        val p = fx.progress
        val flashAlpha = if (p < 0.6f) {
            if (Random.nextFloat() > 0.35f) 255 else 80
        } else {
            ((1.0f - p) / 0.4f * 255).toInt().coerceIn(0, 255)
        }

        val primaryCol = if (useCustomColors) customColorPrimary else Color.parseColor("#00F5FF")
        val secondaryCol = if (useCustomColors) customColorSecondary else Color.parseColor("#B026FF")

        if (p < 0.25f) {
            fillPaint.shader = null
            fillPaint.color = Color.WHITE
            fillPaint.alpha = (200 * (1f - p / 0.25f)).toInt().coerceIn(0, 255)
            canvas.drawCircle(fx.originX, fx.originY, 28f * (1f - p / 0.25f), fillPaint)
        }

        for (bolt in fx.lightningBolts) {
            glowPaint.shader = null
            glowPaint.color = secondaryCol
            glowPaint.strokeWidth = 18f * (1f - p * 0.7f)
            glowPaint.alpha = (flashAlpha * 0.4f).toInt().coerceIn(0, 255)
            canvas.drawPath(bolt.path, glowPaint)

            strokePaint.shader = null
            strokePaint.color = bolt.color
            strokePaint.strokeWidth = 6f * (1f - p * 0.6f) + 1.5f
            strokePaint.alpha = flashAlpha
            canvas.drawPath(bolt.path, strokePaint)

            strokePaint.color = Color.WHITE
            strokePaint.strokeWidth = 2f
            strokePaint.alpha = (flashAlpha * 0.85f).toInt().coerceIn(0, 255)
            canvas.drawPath(bolt.path, strokePaint)
        }
    }

    // --- 3. Cosmic Supernova ---
    private fun drawCosmicSupernova(canvas: Canvas, fx: ActiveEffect) {
        val p = fx.progress
        val fade = getEffectFade(p, 1.5f)
        val r = p * fx.maxRadius

        if (p < 0.22f) {
            val coreAlpha = ((1f - p / 0.22f) * 255).toInt().coerceIn(0, 255)
            fillPaint.shader = null
            fillPaint.color = Color.WHITE
            fillPaint.alpha = coreAlpha
            canvas.drawCircle(fx.originX, fx.originY, 32f * (1f - p * 2f), fillPaint)
        }

        val palette = if (useCustomColors) {
            intArrayOf(customColorPrimary, customColorSecondary, Color.WHITE)
        } else {
            intArrayOf(Color.parseColor("#FF00FF"), Color.parseColor("#00FFFF"), Color.parseColor("#FFEE55"))
        }
        val shockShader = SweepGradient(fx.originX, fx.originY, palette, null)
        val mat = Matrix()
        mat.setRotate(fx.colorPhaseOffset * 360f, fx.originX, fx.originY)
        shockShader.setLocalMatrix(mat)
        glowPaint.shader = shockShader
        glowPaint.strokeWidth = 26f * fade + 4f
        glowPaint.alpha = (fade * 140).toInt().coerceIn(0, 255)
        canvas.drawCircle(fx.originX, fx.originY, r, glowPaint)

        strokePaint.shader = shockShader
        strokePaint.strokeWidth = 10f * fade + 2f
        strokePaint.alpha = (fade * 255).toInt().coerceIn(0, 255)
        canvas.drawCircle(fx.originX, fx.originY, r, strokePaint)

        fillPaint.shader = null
        for (star in fx.particles) {
            fillPaint.color = star.color
            fillPaint.alpha = (fade * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(star.x, star.y, star.maxRadius * fade + 1f, fillPaint)
        }
    }

    // --- 4. Molten Magma ---
    private fun drawMoltenMagma(canvas: Canvas, fx: ActiveEffect) {
        val p = fx.progress
        val fade = getEffectFade(p, 1.4f)
        val r = p * fx.maxRadius

        canvas.save()
        canvas.translate(fx.originX, fx.originY)

        val palette = if (useCustomColors) {
            intArrayOf(customColorPrimary, customColorSecondary, customColorPrimary)
        } else {
            magmaColors
        }
        val shader = SweepGradient(0f, 0f, palette, null)
        val mat = Matrix()
        mat.setRotate(fx.colorPhaseOffset * 360f)
        shader.setLocalMatrix(mat)
        glowPaint.shader = shader
        glowPaint.strokeWidth = 32f * fade + 6f
        glowPaint.alpha = (fade * 160).toInt().coerceIn(0, 255)
        canvas.drawCircle(0f, 0f, r, glowPaint)

        strokePaint.shader = shader
        strokePaint.strokeWidth = 16f * fade + 3f
        strokePaint.alpha = (fade * 255).toInt().coerceIn(0, 255)
        canvas.drawCircle(0f, 0f, r, strokePaint)
        canvas.restore()

        fillPaint.shader = null
        for (ember in fx.particles) {
            fillPaint.color = ember.color
            fillPaint.alpha = (fade * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(ember.x, ember.y, ember.maxRadius * fade + 1.5f, fillPaint)
        }
    }

    // --- 5. Sonic Soundwave ---
    private fun drawSonicWave(canvas: Canvas, fx: ActiveEffect) {
        val p = fx.progress
        val fade = getEffectFade(p, 1.2f)
        val r = p * fx.maxRadius

        val palette = if (useCustomColors) {
            intArrayOf(customColorPrimary, customColorSecondary, customColorPrimary)
        } else {
            sonicColors
        }
        val shader = SweepGradient(fx.originX, fx.originY, palette, null)
        val mat = Matrix()
        mat.setRotate(fx.colorPhaseOffset * 360f, fx.originX, fx.originY)
        shader.setLocalMatrix(mat)
        strokePaint.shader = shader
        strokePaint.strokeWidth = 14f * fade + 3f
        strokePaint.alpha = (fade * 240).toInt().coerceIn(0, 255)

        val wavePath = Path()
        val segments = 72
        for (i in 0..segments) {
            val angle = (i.toFloat() / segments) * 2 * Math.PI.toFloat()
            val wobble = sin(angle * 7f + p * 12f) * (20f * fade)
            val currR = r + wobble
            val px = fx.originX + cos(angle) * currR
            val py = fx.originY + sin(angle) * currR
            if (i == 0) wavePath.moveTo(px, py) else wavePath.lineTo(px, py)
        }
        wavePath.close()
        canvas.drawPath(wavePath, strokePaint)

        if (r > 40f) {
            strokePaint.alpha = (fade * 140).toInt().coerceIn(0, 255)
            canvas.drawCircle(fx.originX, fx.originY, r * 0.65f, strokePaint)
        }
    }

    // --- 6. Quantum Black Hole ---
    private fun drawBlackHole(canvas: Canvas, fx: ActiveEffect) {
        val p = fx.progress
        if (p < 0.35f) {
            val suctionProgress = p / 0.35f
            val contractR = (1.0f - suctionProgress) * 120f + 10f
            strokePaint.shader = null
            strokePaint.color = if (useCustomColors) customColorPrimary else Color.parseColor("#00F5FF")
            strokePaint.strokeWidth = 12f * (1f - suctionProgress) + 3f
            strokePaint.alpha = (suctionProgress * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(fx.originX, fx.originY, contractR, strokePaint)
        } else {
            val shockP = (p - 0.35f) / 0.65f
            val fade = getEffectFade(shockP, 1.3f)
            val shockR = shockP * fx.maxRadius

            canvas.save()
            canvas.translate(fx.originX, fx.originY)
            val palette = if (useCustomColors) getActiveLiquidPalette() else blackHoleColors
            val shader = SweepGradient(0f, 0f, palette, null)
            val mat = Matrix()
            mat.setRotate(fx.colorPhaseOffset * 360f)
            shader.setLocalMatrix(mat)
            strokePaint.shader = shader
            strokePaint.strokeWidth = 22f * fade + 4f
            strokePaint.alpha = (fade * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(0f, 0f, shockR, strokePaint)
            canvas.restore()
        }
    }

    // --- 7. Razer Chroma RGB Wave ---
    private fun drawRazerChromaBackground(canvas: Canvas, fx: ActiveEffect) {
        val p = fx.progress
        val fade = getEffectFade(p, 1.4f)
        val r = p * fx.maxRadius
        val palette = getActiveChromaPalette()

        canvas.save()
        canvas.translate(fx.originX, fx.originY)

        val shader = SweepGradient(0f, 0f, palette, null)
        val mat = Matrix()
        mat.setRotate(fx.colorPhaseOffset * 360f + p * 60f)
        shader.setLocalMatrix(mat)
        glowPaint.shader = shader
        glowPaint.strokeWidth = 36f * fade + 8f
        glowPaint.alpha = (fade * 120).toInt().coerceIn(0, 255)
        canvas.drawCircle(0f, 0f, r, glowPaint)

        strokePaint.shader = shader
        strokePaint.strokeWidth = 20f * fade + 4f
        strokePaint.alpha = (fade * 255).toInt().coerceIn(0, 255)
        canvas.drawCircle(0f, 0f, r, strokePaint)

        if (r > 45f) {
            strokePaint.alpha = (fade * 150).toInt().coerceIn(0, 255)
            canvas.drawCircle(0f, 0f, r - 35f, strokePaint)
        }

        canvas.restore()
    }

    // --- Mechanical Keycap Discovery & Matrix Flow Methods ---
    private fun getKeycapRects(kb: View?, kbLeft: Float, kbTop: Float, kbRight: Float, kbBottom: Float): List<KeycapInfo> {
        val kbWidth = kbRight - kbLeft
        val kbHeight = kbBottom - kbTop
        if (kbWidth <= 10f || kbHeight <= 10f) return emptyList()

        val now = SystemClock.uptimeMillis()
        val targetHash = kb?.hashCode() ?: 0

        if (cachedKeycapRects.isNotEmpty() && (now - lastKeyScanTime < 2500L) && targetHash == lastKbTargetHashCode) {
            return cachedKeycapRects
        }

        cachedKeycapRects.clear()
        lastKeyScanTime = now
        lastKbTargetHashCode = targetHash

        if (kb is ViewGroup) {
            getLocationOnScreen(myScreenLoc)
            collectChildKeyViews(kb, myScreenLoc, cachedKeycapRects)
            normalizeKeycapRows(cachedKeycapRects)
        }

        // If native SoftKeyView hierarchy is absent (e.g. preview mode, virtualized Gboard canvas), generate realistic procedural keycaps
        if (cachedKeycapRects.size < 8) {
            cachedKeycapRects.clear()
            generateProceduralKeyMatrix(kbLeft, kbTop, kbRight, kbBottom, cachedKeycapRects)
        }

        return cachedKeycapRects
    }

    private fun collectChildKeyViews(view: View, overlayLoc: IntArray, outList: MutableList<KeycapInfo>) {
        if (!view.isShown || view.visibility != View.VISIBLE || view.width <= 0 || view.height <= 0) return
        val name = view.javaClass.simpleName

        val isExplicitContainer = name.contains("Keyboard") || name.contains("Holder") || name.contains("Container") ||
                name.contains("Root") || name.contains("Decor") || name.contains("Panel") || name.contains("Strip")

        val bg = view.background

        // If a view has no visual background or is an explicit container, it cannot be a keycap button itself.
        // Recurse into its children to find the actual styled keycaps (e.g. inner FrameLayout for A/L in Gboard).
        if (bg == null || isExplicitContainer) {
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    collectChildKeyViews(view.getChildAt(i), overlayLoc, outList)
                }
            }
            return
        }

        view.getLocationOnScreen(tempViewLoc)
        val l = (tempViewLoc[0] - overlayLoc[0]).toFloat()
        val t = (tempViewLoc[1] - overlayLoc[1]).toFloat()
        val w = view.width.toFloat()
        val h = view.height.toFloat()

        // Filter for genuine key dimensions (exclude huge backgrounds or zero-size spacers)
        if (w >= 18f * density && w <= 320f * density && h >= 22f * density && h <= 130f * density) {
            // In Gboard, view padding defines the exact visual keycap boundary inside the touch cell.
            // Bottom padding (e.g. 37px) separates rows, so subtracting it aligns the rect exactly with the visible button!
            val padL = view.paddingLeft.toFloat().coerceAtLeast(1.5f * density)
            val padT = view.paddingTop.toFloat().coerceAtLeast(1.5f * density)
            val padR = view.paddingRight.toFloat().coerceAtLeast(1.5f * density)
            val padB = view.paddingBottom.toFloat().coerceAtLeast(1.5f * density)

            val keyRect = RectF(l + padL, t + padT, l + w - padR, t + h - padB)

            val isRound = isRoundFunctionKey(view)
            val extractedR = extractCornerRadius(bg, keyRect.width(), keyRect.height())

            val radius = when {
                isRound -> min(keyRect.width(), keyRect.height()) / 2f
                extractedR > 0f -> extractedR
                else -> (11.5f * density).coerceIn(8f * density, 14f * density)
            }

            outList.add(KeycapInfo(keyRect, radius))
            return
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collectChildKeyViews(view.getChildAt(i), overlayLoc, outList)
            }
        }
    }

    private fun isRoundFunctionKey(view: View): Boolean {
        val desc = view.contentDescription?.toString()?.lowercase() ?: ""
        // Backspace, delete, shift, and caps lock are square squircle keys like letters.
        // Only bottom-row function/action keys like ?123, symbols, enter, search, emoji are round pills.
        if (desc.contains("shift") || desc.contains("caps") ||
            desc.contains("delete") || desc.contains("backspace")) {
            return false
        }
        if (desc.contains("123") || desc.contains("symbol") ||
            desc.contains("enter") || desc.contains("search") ||
            desc.contains("comma") || desc.contains("period") ||
            desc.contains("emoji") || desc.contains("language") ||
            desc.contains("switch")) {
            return true
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val c = view.getChildAt(i)
                val cDesc = c.contentDescription?.toString()?.lowercase() ?: ""
                if (cDesc.contains("shift") || cDesc.contains("caps") ||
                    cDesc.contains("delete") || cDesc.contains("backspace")) {
                    return false
                }
                if (cDesc.contains("123") || cDesc.contains("symbol") ||
                    cDesc.contains("enter") || cDesc.contains("search") ||
                    cDesc.contains("comma") || cDesc.contains("period") ||
                    cDesc.contains("emoji") || cDesc.contains("language") ||
                    cDesc.contains("switch")) {
                    return true
                }
                if (c is android.widget.TextView) {
                    val txt = c.text?.toString() ?: ""
                    if (txt.contains("123") || txt.contains("?#+") || txt.contains("!#1")) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun extractCornerRadius(drawable: Drawable?, viewW: Float, viewH: Float): Float {
        if (drawable == null) return 0f
        var d: Drawable? = drawable
        var depth = 0
        while (d != null && depth < 6) {
            depth++
            when (d) {
                is InsetDrawable -> d = d.drawable
                is RippleDrawable -> {
                    var maxR = 0f
                    for (i in 0 until d.numberOfLayers) {
                        val r = extractCornerRadius(d.getDrawable(i), viewW, viewH)
                        if (r > maxR) maxR = r
                    }
                    val mask = d.findDrawableByLayerId(android.R.id.mask)
                    val maskR = extractCornerRadius(mask, viewW, viewH)
                    if (maskR > maxR) maxR = maskR
                    return maxR
                }
                is LayerDrawable -> {
                    var maxR = 0f
                    for (i in 0 until d.numberOfLayers) {
                        val r = extractCornerRadius(d.getDrawable(i), viewW, viewH)
                        if (r > maxR) maxR = r
                    }
                    return maxR
                }
                is GradientDrawable -> {
                    if (d.shape == GradientDrawable.OVAL || d.shape == GradientDrawable.RING) {
                        return min(viewW, viewH) / 2f
                    }
                    val cr = d.cornerRadius
                    if (cr > 0f) {
                        if (cr >= min(viewW, viewH) / 2f - 6f * density) {
                            return min(viewW, viewH) / 2f
                        }
                        return cr
                    }
                    try {
                        val m = d.javaClass.getMethod("getCornerRadii")
                        val radii = m.invoke(d) as? FloatArray
                        if (radii != null && radii.isNotEmpty()) {
                            val maxR = radii.maxOrNull() ?: 0f
                            if (maxR > 0f) {
                                if (maxR >= min(viewW, viewH) / 2f - 6f * density) {
                                    return min(viewW, viewH) / 2f
                                }
                                return maxR
                            }
                        }
                    } catch (ignored: Throwable) {}
                    break
                }
                else -> break
            }
        }
        return 0f
    }

    private fun normalizeKeycapRows(keycaps: MutableList<KeycapInfo>) {
        if (keycaps.size < 10) return

        // 1. Group keycaps by row (similar centerY within 14dp)
        val rows = mutableListOf<MutableList<KeycapInfo>>()
        val sorted = keycaps.sortedBy { it.rect.centerY() }

        for (k in sorted) {
            val matchingRow = rows.find { row ->
                abs(row[0].rect.centerY() - k.rect.centerY()) < 14f * density
            }
            if (matchingRow != null) {
                matchingRow.add(k)
            } else {
                rows.add(mutableListOf(k))
            }
        }

        // Sort rows vertically from top to bottom
        rows.sortBy { it[0].rect.centerY() }
        val numRows = rows.size

        for (rowIndex in rows.indices) {
            val row = rows[rowIndex]
            if (row.isEmpty()) continue
            row.sortBy { it.rect.left }

            val first = row.first()
            val last = row.last()

            val isBottomRow = (rowIndex == numRows - 1)
            val isShiftRow = (rowIndex == numRows - 2)

            if (isBottomRow) {
                // First key is '?123' -> round / pill
                first.cornerRadius = min(first.rect.width(), first.rect.height()) / 2f
                // Last key is 'Enter' -> round / pill
                last.cornerRadius = min(last.rect.width(), last.rect.height()) / 2f

                // In bottom row, all non-spacebar keys (comma, period, emoji) are also round pills
                val maxKeyW = row.maxOfOrNull { it.rect.width() } ?: 0f
                for (k in row) {
                    if (k.rect.width() < maxKeyW * 0.55f) {
                        k.cornerRadius = min(k.rect.width(), k.rect.height()) / 2f
                    }
                }
                continue
            }

            if (isShiftRow) {
                // Shift (Caps Lock) and Backspace are SQUARE (squircle matching letter keys)
                val innerKeys = if (row.size > 2) row.subList(1, row.size - 1) else row
                val avgR = innerKeys.map { it.cornerRadius }.average().toFloat().let {
                    if (it > 0f) it else 11.5f * density
                }
                // Both Shift, Backspace, and inner letter keys are square squircle
                for (k in row) {
                    k.cornerRadius = avgR
                }
                continue
            }

            // For regular letter rows (Row 0: Q..P, Row 1: A..L)
            if (row.size >= 7) {
                val innerKeys = row.subList(1, row.size - 1)
                val avgW = innerKeys.map { it.rect.width() }.average().toFloat()
                val avgR = innerKeys.map { it.cornerRadius }.average().toFloat()
                if (avgW > 10f) {
                    // Harmonize letter key radius across row
                    first.cornerRadius = avgR
                    last.cornerRadius = avgR

                    // In Row 1 (A..L), normalize 'A' and 'L' width if bezel touch wrapper made them artificially wider
                    if (row.size in 8..10) {
                        if (first.rect.width() > avgW * 1.20f && first.rect.height() < avgW * 2.2f) {
                            first.rect.left = first.rect.right - avgW
                        }
                        if (last.rect.width() > avgW * 1.20f && last.rect.height() < avgW * 2.2f) {
                            last.rect.right = last.rect.left + avgW
                        }
                    }
                }
            }
        }
    }

    private fun generateProceduralKeyMatrix(
        kbLeft: Float, kbTop: Float, kbRight: Float, kbBottom: Float,
        outList: MutableList<KeycapInfo>
    ) {
        val totalW = kbRight - kbLeft
        val totalH = kbBottom - kbTop
        if (totalW < 60f || totalH < 50f) return

        val headerOffset = if (totalH > 180f * density) 44f * density else 0f
        val actualTop = kbTop + headerOffset
        val actualH = totalH - headerOffset

        val paddingX = 4f * density
        val paddingY = 6f * density
        val keyGap = 3.5f * density
        val rows = 4
        val rowH = (actualH - paddingY * 2 - keyGap * (rows - 1)) / rows
        if (rowH <= 5f) return

        val stdKeyW = (totalW - paddingX * 2 - keyGap * 9) / 10f
        val stdRadius = 11f * density

        // Row 0: Q W E R T Y U I O P (10 keys)
        val y0_1 = actualTop + paddingY
        val y0_2 = y0_1 + rowH
        var currX0 = kbLeft + paddingX
        for (c in 0 until 10) {
            outList.add(KeycapInfo(RectF(currX0, y0_1, currX0 + stdKeyW, y0_2), stdRadius))
            currX0 += stdKeyW + keyGap
        }

        // Row 1 (Center line): A S D F G H J K L (9 keys, exactly matching stdKeyW, centered)
        val y1_1 = y0_1 + rowH + keyGap
        val y1_2 = y1_1 + rowH
        val row1Indent = paddingX + (stdKeyW + keyGap) / 2f
        var currX1 = kbLeft + row1Indent
        for (c in 0 until 9) {
            outList.add(KeycapInfo(RectF(currX1, y1_1, currX1 + stdKeyW, y1_2), stdRadius))
            currX1 += stdKeyW + keyGap
        }

        // Row 2: Shift, Z X C V B N M, Backspace
        val y2_1 = y1_1 + rowH + keyGap
        val y2_2 = y2_1 + rowH
        val letterKeysW = stdKeyW * 7 + keyGap * 6
        val sideKeyW2 = ((totalW - paddingX * 2 - keyGap * 2) - letterKeysW) / 2f

        var currX2 = kbLeft + paddingX
        // Shift key -> square squircle (stdRadius)
        outList.add(KeycapInfo(RectF(currX2, y2_1, currX2 + sideKeyW2, y2_2), stdRadius))
        currX2 += sideKeyW2 + keyGap
        // 7 Letters (Z..M)
        for (c in 0 until 7) {
            outList.add(KeycapInfo(RectF(currX2, y2_1, currX2 + stdKeyW, y2_2), stdRadius))
            currX2 += stdKeyW + keyGap
        }
        // Backspace key -> square squircle (stdRadius)
        outList.add(KeycapInfo(RectF(currX2, y2_1, currX2 + sideKeyW2, y2_2), stdRadius))

        // Row 3: Bottom action row (?123, comma, space, period, enter)
        val y3_1 = y2_1 + rowH + keyGap
        val y3_2 = y3_1 + rowH
        val spaceW = stdKeyW * 4.2f
        val sideKeyW3 = (totalW - paddingX * 2 - spaceW - keyGap * 4) / 4f

        var currX3 = kbLeft + paddingX
        for (k in 0..1) {
            // ?123 and comma -> round / pill
            outList.add(KeycapInfo(RectF(currX3, y3_1, currX3 + sideKeyW3, y3_2), min(sideKeyW3, rowH) / 2f))
            currX3 += sideKeyW3 + keyGap
        }
        outList.add(KeycapInfo(RectF(currX3, y3_1, currX3 + spaceW, y3_2), 13f * density))
        currX3 += spaceW + keyGap
        for (k in 0..1) {
            // period and enter -> round / pill
            outList.add(KeycapInfo(RectF(currX3, y3_1, currX3 + sideKeyW3, y3_2), min(sideKeyW3, rowH) / 2f))
            currX3 += sideKeyW3 + keyGap
        }
    }

    private fun drawKeycapMatrixFlow(
        canvas: Canvas,
        fx: ActiveEffect,
        keycaps: List<KeycapInfo>,
        progress: Float,
        waveRadius: Float,
        energyFade: Float,
        palette: IntArray
    ) {
        val waveBand = 46f * density

        for (key in keycaps) {
            val rect = key.rect
            val r = key.cornerRadius
            val cx = rect.centerX()
            val cy = rect.centerY()
            val dist = hypot(cx - fx.originX, cy - fx.originY)

            val diff = abs(dist - waveRadius)
            val isOriginKey = (dist < 26f * density && progress < 0.35f)

            if (diff < waveBand || isOriginKey) {
                val waveFactor = if (diff < waveBand) {
                    (1.0f - diff / waveBand).pow(1.5f)
                } else 0f

                val tapFactor = if (isOriginKey) {
                    (1.0f - (progress / 0.35f)).pow(1.2f)
                } else 0f

                val intensity = max(waveFactor, tapFactor) * energyFade
                if (intensity <= 0.03f) continue

                val angle = atan2(cy - fx.originY, cx - fx.originX)
                val rawAngle = ((angle + Math.PI) / (2 * Math.PI)).toFloat()
                val normAngle = (rawAngle + fx.colorPhaseOffset).let { it - floor(it) }.coerceIn(0f, 1f)
                val keyColor = interpolateColorFromPalette(palette, normAngle)

                // 1. Soft liquid glow inside the keycap (disabled when Border Rim Only mode is active)
                if (!isKeyBorderOnlyEnabled) {
                    keycapFillPaint.color = keyColor
                    keycapFillPaint.alpha = (intensity * 115).toInt().coerceIn(0, 255)
                    canvas.drawRoundRect(rect, r, r, keycapFillPaint)
                }

                // 2. Mechanical keycap border aura glow
                keycapGlowPaint.color = keyColor
                keycapGlowPaint.strokeWidth = (4.5f * density) * intensity + 1.5f
                keycapGlowPaint.alpha = (intensity * 140).toInt().coerceIn(0, 255)
                canvas.drawRoundRect(rect, r, r, keycapGlowPaint)

                // 3. Sharp mechanical rim edge lighting
                keycapStrokePaint.color = keyColor
                keycapStrokePaint.strokeWidth = (1.8f * density) * intensity + 0.8f
                keycapStrokePaint.alpha = (intensity * 255).toInt().coerceIn(0, 255)
                canvas.drawRoundRect(rect, r, r, keycapStrokePaint)
            }
        }
    }

    private fun interpolateColorFromPalette(palette: IntArray, position: Float): Int {
        if (palette.isEmpty()) return Color.WHITE
        if (palette.size == 1) return palette[0]
        val clampedPos = position.coerceIn(0f, 1f)
        val scaled = clampedPos * (palette.size - 1)
        val index = scaled.toInt().coerceAtMost(palette.size - 2)
        val fraction = scaled - index
        val c1 = palette[index]
        val c2 = palette[index + 1]

        val a = (Color.alpha(c1) + fraction * (Color.alpha(c2) - Color.alpha(c1))).toInt()
        val r = (Color.red(c1) + fraction * (Color.red(c2) - Color.red(c1))).toInt()
        val g = (Color.green(c1) + fraction * (Color.green(c2) - Color.green(c1))).toInt()
        val b = (Color.blue(c1) + fraction * (Color.blue(c2) - Color.blue(c1))).toInt()

        return Color.argb(a, r, g, b)
    }

    fun pulseUnderglow() {
        if (!isUnderglowEnabled) return
        underglowPulseIntensity = 1.0f
        startUnderglowAnimation()
    }

    private fun startUnderglowAnimation() {
        if (!isUnderglowEnabled) return
        lastUnderglowFrameTime = SystemClock.elapsedRealtime()
        postInvalidateOnAnimation()
    }

    private fun drawPerimeterUnderglow(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        val now = SystemClock.elapsedRealtime()
        if (lastUnderglowFrameTime == 0L) lastUnderglowFrameTime = now
        val dt = (now - lastUnderglowFrameTime).coerceIn(0L, 50L)
        lastUnderglowFrameTime = now

        // Pulse decay: 1.0 down to 0.0 over ~380ms
        if (underglowPulseIntensity > 0f) {
            underglowPulseIntensity = (underglowPulseIntensity - (dt / 380f)).coerceAtLeast(0f)
        }

        // Ambient breathing calculation (smooth sine wave over ~2.4s period)
        val breath = (sin(now / 380.0) * 0.5 + 0.5).toFloat()
        val ambientAlpha = 0.32f + breath * 0.28f
        val turboBoost = if (isTurboDynamicsEnabled) (1.0f + (turboFactor - 1.0f) * 0.45f) else 1.0f
        val brightness = ((ambientAlpha + underglowPulseIntensity * 0.55f) * turboBoost).coerceIn(0.15f, 1.0f)

        // Palette resolution
        val primaryCol = if (useCustomColors) customColorPrimary else when (currentEffect) {
            EffectType.WATER_DROP, EffectType.AMBIENT_RAIN -> Color.parseColor("#00FFF5")
            EffectType.NEON_LIGHTNING -> Color.parseColor("#00F5FF")
            EffectType.COSMIC_SUPERNOVA -> Color.parseColor("#FF00FF")
            EffectType.MOLTEN_MAGMA -> Color.parseColor("#FF2200")
            EffectType.SONIC_WAVE -> Color.parseColor("#00FF66")
            EffectType.BLACK_HOLE -> Color.parseColor("#7A00FF")
            EffectType.RAZER_CHROMA -> Color.parseColor("#00FFF5")
        }
        val secondaryCol = if (useCustomColors) customColorSecondary else when (currentEffect) {
            EffectType.WATER_DROP, EffectType.AMBIENT_RAIN -> Color.parseColor("#FF00AA")
            EffectType.NEON_LIGHTNING -> Color.parseColor("#B026FF")
            EffectType.COSMIC_SUPERNOVA -> Color.parseColor("#00FFFF")
            EffectType.MOLTEN_MAGMA -> Color.parseColor("#FFAA00")
            EffectType.SONIC_WAVE -> Color.parseColor("#0088FF")
            EffectType.BLACK_HOLE -> Color.parseColor("#00FFF5")
            EffectType.RAZER_CHROMA -> Color.parseColor("#FF00AA")
        }

        // 1. Bottom Glow Diffuser Bar (Underglow shine along bottom keyboard bezel)
        val bottomBarHeight = 26f * density
        val bottomGradient = LinearGradient(
            0f, bottom - bottomBarHeight, 0f, bottom,
            intArrayOf(Color.TRANSPARENT, adjustAlpha(secondaryCol, 0.40f * brightness), adjustAlpha(primaryCol, 0.85f * brightness)),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        underglowFillPaint.shader = bottomGradient
        canvas.drawRect(left, bottom - bottomBarHeight, right, bottom, underglowFillPaint)

        // 2. Left & Right Vertical Accent Diffusers
        val railWidth = 14f * density
        val leftGradient = LinearGradient(
            left, 0f, left + railWidth, 0f,
            intArrayOf(adjustAlpha(primaryCol, 0.70f * brightness), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        underglowFillPaint.shader = leftGradient
        canvas.drawRect(left, top, left + railWidth, bottom, underglowFillPaint)

        val rightGradient = LinearGradient(
            right - railWidth, 0f, right, 0f,
            intArrayOf(Color.TRANSPARENT, adjustAlpha(secondaryCol, 0.70f * brightness)),
            null, Shader.TileMode.CLAMP
        )
        underglowFillPaint.shader = rightGradient
        canvas.drawRect(right - railWidth, top, right, bottom, underglowFillPaint)

        // 3. Perimeter Bezel Ribbon (Outer Diffuse Aura + Inner Bright Neon Core)
        val inset = 3f * density
        val perimeterRect = RectF(left + inset, top + inset, right - inset, bottom - inset)
        val cornerRadius = 14f * density

        val borderGradient = LinearGradient(
            left, top, right, bottom,
            intArrayOf(primaryCol, secondaryCol, primaryCol),
            null, Shader.TileMode.MIRROR
        )

        // Outer Diffused Glow
        underglowGlowPaint.shader = borderGradient
        underglowGlowPaint.strokeWidth = 9f * density * (0.8f + brightness * 0.4f)
        underglowGlowPaint.alpha = (90 * brightness).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(perimeterRect, cornerRadius, cornerRadius, underglowGlowPaint)

        // Inner Core Neon Line
        underglowPaint.shader = borderGradient
        underglowPaint.strokeWidth = 2.4f * density
        underglowPaint.alpha = (235 * brightness).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(perimeterRect, cornerRadius, cornerRadius, underglowPaint)
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
