package com.custom.gboardrgb

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.View
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

    fun setKeyboardTarget(view: View) {
        keyboardTargetRef = WeakReference(view)
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

    private val activeEffects = CopyOnWriteArrayList<ActiveEffect>()

    // Standard high-performance hardware-accelerated paints (No conflicting xfermode)
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

        val baseRadius = max(kbW, kbH) * (if (isMiniDrop) 0.38f else 0.95f) * sizeMultiplier

        val particles = when (effectType) {
            EffectType.COSMIC_SUPERNOVA -> generateSupernovaParticles(touchX, touchY)
            EffectType.MOLTEN_MAGMA -> generateMagmaParticles(touchX, touchY)
            else -> emptyList()
        }

        val lightningBolts = when (effectType) {
            EffectType.NEON_LIGHTNING -> generateLightningBolts(touchX, touchY, baseRadius * 0.75f)
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
            EffectType.NEON_LIGHTNING -> 300L
            EffectType.SONIC_WAVE -> 520L
            EffectType.RAZER_CHROMA -> 420L
            EffectType.MOLTEN_MAGMA -> 600L
            EffectType.BLACK_HOLE -> 680L
            EffectType.COSMIC_SUPERNOVA -> 700L
            EffectType.WATER_DROP, EffectType.AMBIENT_RAIN -> if (isMiniDrop) 650L else 720L
        }
        val finalDuration = (baseDuration / speedMultiplier.coerceIn(0.2f, 3.0f)).toLong()

        val interpolator = when (effectType) {
            EffectType.WATER_DROP, EffectType.AMBIENT_RAIN -> fluidInterpolator
            else -> decelerateInterpolator
        }

        post {
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = finalDuration
                this.interpolator = interpolator
                addUpdateListener { anim ->
                    effect.progress = anim.animatedValue as Float
                    for (p in effect.particles) {
                        p.x += p.vx
                        p.y += p.vy
                        p.vx *= 0.94f
                        p.vy *= 0.94f
                    }
                    invalidate()
                }
            }
            animator.start()
        }
    }

    private fun generateSupernovaParticles(originX: Float, originY: Float): List<Particle> {
        val list = mutableListOf<Particle>()
        val colors = intArrayOf(
            Color.parseColor("#FF00FF"), Color.parseColor("#00FFFF"),
            Color.parseColor("#FFDD00"), Color.parseColor("#FFFFFF"), Color.parseColor("#B026FF")
        )
        for (i in 0 until 18) {
            val angle = Random.nextFloat() * 2 * Math.PI.toFloat()
            val speed = Random.nextFloat() * 22f + 6f
            list.add(
                Particle(
                    x = originX, y = originY,
                    vx = cos(angle) * speed, vy = sin(angle) * speed,
                    color = colors[Random.nextInt(colors.size)],
                    maxRadius = Random.nextFloat() * 5f + 3f
                )
            )
        }
        return list
    }

    private fun generateMagmaParticles(originX: Float, originY: Float): List<Particle> {
        val list = mutableListOf<Particle>()
        val colors = intArrayOf(
            Color.parseColor("#FF2200"), Color.parseColor("#FF7700"),
            Color.parseColor("#FFCC00"), Color.parseColor("#FFFFFF")
        )
        for (i in 0 until 12) {
            val vx = (Random.nextFloat() - 0.5f) * 14f
            val vy = -(Random.nextFloat() * 18f + 6f)
            list.add(
                Particle(
                    x = originX, y = originY,
                    vx = vx, vy = vy,
                    color = colors[Random.nextInt(colors.size)],
                    maxRadius = Random.nextFloat() * 4.5f + 2f
                )
            )
        }
        return list
    }

    private fun generateLightningBolts(originX: Float, originY: Float, reach: Float): List<LightningBolt> {
        val bolts = mutableListOf<LightningBolt>()
        val colors = intArrayOf(
            Color.parseColor("#00F5FF"), Color.parseColor("#FFFFFF"),
            Color.parseColor("#B026FF"), Color.parseColor("#00FFFF")
        )
        val boltCount = Random.nextInt(4, 6)
        for (i in 0 until boltCount) {
            val path = Path()
            path.moveTo(originX, originY)
            val baseAngle = (i.toFloat() / boltCount) * 2 * Math.PI.toFloat() + (Random.nextFloat() - 0.5f) * 0.5f
            var currX = originX
            var currY = originY
            val segments = 6
            val stepDist = reach / segments

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
        if (activeEffects.isEmpty()) return

        val kb = keyboardTargetRef?.get()
        canvas.save()

        if (kb != null && kb.isShown && kb.width > 0 && kb.height > 0) {
            val kbLoc = IntArray(2)
            kb.getLocationOnScreen(kbLoc)
            val myLoc = IntArray(2)
            getLocationOnScreen(myLoc)
            val left = (kbLoc[0] - myLoc[0]).toFloat()
            val top = (kbLoc[1] - myLoc[1]).toFloat()
            canvas.clipRect(left, top, left + kb.width, top + kb.height)
        } else {
            canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
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
    }

    // --- 1. Fluid Water Droplet ---
    private fun drawFluidWater(canvas: Canvas, fx: ActiveEffect) {
        val p = fx.progress
        val energyFade = (1.0f - p).pow(1.3f)
        val baseR = p * fx.maxRadius

        canvas.save()
        canvas.translate(fx.originX, fx.originY)

        val shader = SweepGradient(0f, 0f, liquidWaterPalette, liquidPositions)
        val mat = Matrix()
        mat.setRotate(p * 90f)
        shader.setLocalMatrix(mat)

        if (p < 0.28f) {
            val splashAlpha = ((1.0f - (p / 0.28f)) * 180).toInt().coerceIn(0, 255)
            fillPaint.shader = null
            fillPaint.color = Color.parseColor("#8000FFF5")
            fillPaint.alpha = splashAlpha
            canvas.drawCircle(0f, 0f, 25f * (1.0f + p * 2.5f), fillPaint)
        }

        glowPaint.shader = shader
        glowPaint.strokeWidth = 36f * energyFade + 8f
        glowPaint.alpha = (energyFade * 90).toInt().coerceIn(0, 255)
        canvas.drawCircle(0f, 0f, baseR, glowPaint)

        strokePaint.shader = shader
        strokePaint.strokeWidth = 18f * energyFade + 4f
        strokePaint.alpha = (energyFade * 255).toInt().coerceIn(0, 255)
        canvas.drawCircle(0f, 0f, baseR, strokePaint)

        val r2 = baseR * 0.72f
        if (r2 > 10f) {
            strokePaint.strokeWidth = 14f * energyFade + 3f
            strokePaint.alpha = (energyFade * 190).toInt().coerceIn(0, 255)
            canvas.drawCircle(0f, 0f, r2, strokePaint)
        }

        val r3 = baseR * 0.45f
        if (r3 > 15f) {
            strokePaint.strokeWidth = 9f * energyFade + 2f
            strokePaint.alpha = (energyFade * 130).toInt().coerceIn(0, 255)
            canvas.drawCircle(0f, 0f, r3, strokePaint)
        }

        canvas.restore()
    }

    // --- 2. Cyberpunk Neon Lightning ---
    private fun drawNeonLightning(canvas: Canvas, fx: ActiveEffect) {
        val p = fx.progress
        val fade = (1.0f - p).coerceIn(0f, 1f)
        val flicker = if (Random.nextFloat() > 0.25f) 1.0f else 0.45f

        for (bolt in fx.lightningBolts) {
            glowPaint.shader = null
            glowPaint.color = bolt.color
            glowPaint.strokeWidth = 24f * fade + 4f
            glowPaint.alpha = (fade * flicker * 120).toInt().coerceIn(0, 255)
            canvas.drawPath(bolt.path, glowPaint)

            strokePaint.shader = null
            strokePaint.color = Color.WHITE
            strokePaint.strokeWidth = 7f * fade + 2f
            strokePaint.alpha = (fade * flicker * 255).toInt().coerceIn(0, 255)
            canvas.drawPath(bolt.path, strokePaint)
        }
    }

    // --- 3. Cosmic Supernova ---
    private fun drawCosmicSupernova(canvas: Canvas, fx: ActiveEffect) {
        val p = fx.progress
        val fade = (1.0f - p).pow(1.5f)

        if (p < 0.35f) {
            val coreAlpha = ((1.0f - (p / 0.35f)) * 255).toInt().coerceIn(0, 255)
            fillPaint.shader = null
            fillPaint.color = Color.WHITE
            fillPaint.alpha = coreAlpha
            canvas.drawCircle(fx.originX, fx.originY, (p * 50f) + 12f, fillPaint)
        }

        fillPaint.shader = null
        for (particle in fx.particles) {
            fillPaint.color = particle.color
            fillPaint.alpha = (fade * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(particle.x, particle.y, particle.maxRadius * fade + 1f, fillPaint)
        }
    }

    // --- 4. Molten Magma & Embers ---
    private fun drawMoltenMagma(canvas: Canvas, fx: ActiveEffect) {
        val p = fx.progress
        val fade = (1.0f - p).pow(1.4f)
        val r = p * fx.maxRadius

        canvas.save()
        canvas.translate(fx.originX, fx.originY)

        val shader = SweepGradient(0f, 0f, magmaColors, null)
        glowPaint.shader = shader
        glowPaint.strokeWidth = 38f * fade + 6f
        glowPaint.alpha = (fade * 140).toInt().coerceIn(0, 255)
        canvas.drawCircle(0f, 0f, r, glowPaint)

        strokePaint.shader = shader
        strokePaint.strokeWidth = 18f * fade + 4f
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
        val fade = (1.0f - p).pow(1.2f)
        val baseR = p * fx.maxRadius

        val shader = SweepGradient(fx.originX, fx.originY, sonicColors, null)
        strokePaint.shader = shader
        strokePaint.strokeWidth = 14f * fade + 3f
        strokePaint.alpha = (fade * 240).toInt().coerceIn(0, 255)

        val wavePath = Path()
        val segments = 72
        for (i in 0..segments) {
            val angle = (i.toFloat() / segments) * 2 * Math.PI.toFloat()
            val wobble = sin(angle * 7f + p * 12f) * (20f * fade)
            val currR = baseR + wobble
            val px = fx.originX + cos(angle) * currR
            val py = fx.originY + sin(angle) * currR
            if (i == 0) wavePath.moveTo(px, py) else wavePath.lineTo(px, py)
        }
        wavePath.close()
        canvas.drawPath(wavePath, strokePaint)

        if (baseR > 40f) {
            strokePaint.alpha = (fade * 140).toInt().coerceIn(0, 255)
            canvas.drawCircle(fx.originX, fx.originY, baseR * 0.65f, strokePaint)
        }
    }

    // --- 6. Quantum Black Hole ---
    private fun drawBlackHole(canvas: Canvas, fx: ActiveEffect) {
        val p = fx.progress
        if (p < 0.35f) {
            val suctionProgress = p / 0.35f
            val contractR = (1.0f - suctionProgress) * 120f + 10f
            strokePaint.shader = null
            strokePaint.color = Color.parseColor("#00F5FF")
            strokePaint.strokeWidth = 12f * (1f - suctionProgress) + 3f
            strokePaint.alpha = (suctionProgress * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(fx.originX, fx.originY, contractR, strokePaint)
        } else {
            val shockP = (p - 0.35f) / 0.65f
            val fade = (1.0f - shockP).pow(1.3f)
            val shockR = shockP * fx.maxRadius

            canvas.save()
            canvas.translate(fx.originX, fx.originY)
            val shader = SweepGradient(0f, 0f, chromaColors, chromaPositions)
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
        val fade = (1.0f - p).pow(1.4f)
        val r = p * fx.maxRadius

        canvas.save()
        canvas.translate(fx.originX, fx.originY)

        val shader = SweepGradient(0f, 0f, chromaColors, chromaPositions)
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
}
