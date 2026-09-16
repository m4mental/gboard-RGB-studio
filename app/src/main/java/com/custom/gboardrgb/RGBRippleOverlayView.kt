package com.custom.gboardrgb

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
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
    var speedMultiplier: Float = 1.0f
    var sizeMultiplier: Float = 1.0f

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
    var isUnderglowEnabled: Boolean = true
        set(value) {
            field = value
            if (value) {
                startUnderglowAnimation()
            }
            invalidate()
        }
    private var underglowPulseIntensity: Float = 0.0f
    private var lastUnderglowFrameTime: Long = 0L
    private val density = context.resources.displayMetrics.density

    var isAmbientRainEnabled: Boolean = true
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
        val maxRadius: Float,
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
        if (!isAmbientRainEnabled) return

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
        spawnEffectInternal(currentEffect, touchX, touchY, isMiniDrop = false)
    }

    private fun spawnEffectInternal(effectType: EffectType, touchX: Float, touchY: Float, isMiniDrop: Boolean) {
        val target = keyboardTargetRef?.get()
        val kbW = target?.width?.toFloat() ?: (if (width > 0) width.toFloat() else 1080f)
        val kbH = target?.height?.toFloat() ?: (if (height > 0) height.toFloat() else 850f)

        val turbo = if (isTurboDynamicsEnabled && !isMiniDrop) turboFactor else 1.0f
        val baseRadius = max(kbW, kbH) * (if (isMiniDrop) 0.38f else 0.95f) * sizeMultiplier * (1.0f + (turbo - 1.0f) * 0.22f)

        if (!isMiniDrop) {
            pulseUnderglow()
        }

        val particles = when (effectType) {
            EffectType.COSMIC_SUPERNOVA -> generateSupernovaParticles(touchX, touchY, turbo)
            EffectType.MOLTEN_MAGMA -> generateMagmaParticles(touchX, touchY, turbo)
            else -> emptyList()
        }

        val lightningBolts = when (effectType) {
            EffectType.NEON_LIGHTNING -> generateLightningBolts(touchX, touchY, baseRadius * 0.75f, turbo)
            else -> emptyList()
        }

        val effect = ActiveEffect(
            type = effectType,
            originX = touchX,
            originY = touchY,
            progress = 0f,
            maxRadius = baseRadius,
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

        val finalDuration = if (speedMultiplier < 1.0f) {
            // Power curve for true slow-motion distinction:
            // 1.0x -> 780ms
            // 0.7x -> 1380ms
            // 0.5x -> 2370ms
            // 0.3x -> 5380ms (dramatic 5.4s slow-mo!)
            (baseDuration / speedMultiplier.toDouble().pow(1.6)).toLong()
        } else {
            (baseDuration / speedMultiplier.coerceIn(0.2f, 3.0f)).toLong()
        }

        val interpolator = if (speedMultiplier < 0.8f) {
            // Progressive gentle ease-out for slow-mo so wave travels steadily across the keyboard
            val power = 1.15f + 1.25f * (speedMultiplier / 0.8f).coerceIn(0f, 1f)
            Interpolator { t -> (1.0f - (1.0f - t).pow(power)) }
        } else when (effectType) {
            EffectType.WATER_DROP, EffectType.AMBIENT_RAIN -> fluidInterpolator
            else -> decelerateInterpolator
        }

        post {
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = finalDuration
                this.interpolator = interpolator
                addUpdateListener { anim ->
                    effect.progress = anim.animatedValue as Float
                    val drag = 1.0f - (0.055f * speedMultiplier.coerceIn(0.2f, 1.2f))
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
        val speedScale = 0.45f + 0.55f * speedMultiplier.coerceIn(0.2f, 1.2f)
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
        val speedScale = 0.45f + 0.55f * speedMultiplier.coerceIn(0.2f, 1.2f)
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

        for (fx in activeEffects) {
            val p = fx.progress
            if (p >= 0.99f) {
                activeEffects.remove(fx)
                continue
            }

            when (fx.type) {
                EffectType.WATER_DROP, EffectType.AMBIENT_RAIN -> drawFluidWater(canvas, fx)
                EffectType.NEON_LIGHTNING -> drawNeonLightning(canvas, fx)
                EffectType.COSMIC_SUPERNOVA -> drawCosmicSupernova(canvas, fx)
                EffectType.MOLTEN_MAGMA -> drawMoltenMagma(canvas, fx)
                EffectType.SONIC_WAVE -> drawSonicWave(canvas, fx)
                EffectType.BLACK_HOLE -> drawBlackHole(canvas, fx)
                EffectType.RAZER_CHROMA -> drawRazerChroma(canvas, fx)
            }
        }

        canvas.restore()

        if (isUnderglowEnabled && isShown) {
            postInvalidateOnAnimation()
        }
    }

    // --- 1. Fluid Water Droplet ---
    private fun getEffectFade(progress: Float, baseExponent: Float = 1.3f): Float {
        val exp = if (speedMultiplier < 0.8f) {
            baseExponent * (0.6f + 0.4f * (speedMultiplier / 0.8f))
        } else {
            baseExponent
        }
        return (1.0f - progress).pow(exp)
    }

    private fun drawFluidWater(canvas: Canvas, fx: ActiveEffect) {
        val p = fx.progress
        val energyFade = getEffectFade(p, 1.3f)
        val baseR = p * fx.maxRadius

        canvas.save()
        canvas.translate(fx.originX, fx.originY)

        val palette = getActiveLiquidPalette()
        val shader = SweepGradient(0f, 0f, palette, null)
        val mat = Matrix()
        mat.setRotate(p * 90f)
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
            val palette = getActiveChromaPalette()
            val shader = SweepGradient(0f, 0f, palette, null)
            strokePaint.shader = shader
            strokePaint.strokeWidth = 22f * fade + 4f
            strokePaint.alpha = (fade * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(0f, 0f, shockR, strokePaint)
            canvas.restore()
        }
    }

    // --- 7. Razer Chroma RGB Wave ---
    private fun drawRazerChroma(canvas: Canvas, fx: ActiveEffect) {
        val p = fx.progress
        val fade = getEffectFade(p, 1.4f)
        val r = p * fx.maxRadius

        canvas.save()
        canvas.translate(fx.originX, fx.originY)

        val palette = getActiveChromaPalette()
        val shader = SweepGradient(0f, 0f, palette, null)
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
