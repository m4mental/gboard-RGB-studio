package com.custom.gboardrgb

import android.os.Bundle
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {

    private lateinit var previewOverlay: RGBRippleOverlayView
    private lateinit var tvSpeedLabel: TextView
    private lateinit var tvSizeLabel: TextView
    private lateinit var chipGroup: ChipGroup
    private lateinit var switchAmbientRain: MaterialSwitch

    private var currentSettings = ConfigManager.Settings()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        currentSettings = ConfigManager.loadSettings(this)

        tvSpeedLabel = findViewById(R.id.tvSpeedLabel)
        tvSizeLabel = findViewById(R.id.tvSizeLabel)
        chipGroup = findViewById(R.id.chipGroupEffects)
        switchAmbientRain = findViewById(R.id.switchAmbientRain)

        setupPreviewCanvas()
        setupChips()
        setupSliders()
        setupAmbientSwitch()
    }

    private fun setupPreviewCanvas() {
        val previewContainer = findViewById<FrameLayout>(R.id.previewContainer)

        previewOverlay = RGBRippleOverlayView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            currentEffect = currentSettings.effectType
            speedMultiplier = currentSettings.speedMultiplier
            sizeMultiplier = currentSettings.sizeMultiplier
            isAmbientRainEnabled = currentSettings.isAmbientRainEnabled
        }
        previewContainer.addView(previewOverlay)

        previewContainer.setOnTouchListener { _, event ->
            val action = event.actionMasked
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                val idx = if (action == MotionEvent.ACTION_POINTER_DOWN) event.actionIndex else 0
                previewOverlay.spawnRipple(event.getX(idx), event.getY(idx))
            }
            true
        }
    }

    private fun setupAmbientSwitch() {
        switchAmbientRain.isChecked = currentSettings.isAmbientRainEnabled
        switchAmbientRain.setOnCheckedChangeListener { _, isChecked ->
            currentSettings.isAmbientRainEnabled = isChecked
            previewOverlay.isAmbientRainEnabled = isChecked
            ConfigManager.saveSettings(this, currentSettings)
        }
    }

    private fun setupChips() {
        val effectChipMap = mapOf(
            EffectType.WATER_DROP to R.id.chipWater,
            EffectType.NEON_LIGHTNING to R.id.chipLightning,
            EffectType.COSMIC_SUPERNOVA to R.id.chipSupernova,
            EffectType.MOLTEN_MAGMA to R.id.chipMagma,
            EffectType.SONIC_WAVE to R.id.chipSonic,
            EffectType.BLACK_HOLE to R.id.chipBlackHole,
            EffectType.AMBIENT_RAIN to R.id.chipAmbientRain,
            EffectType.RAZER_CHROMA to R.id.chipChroma
        )

        val initialChipId = effectChipMap[currentSettings.effectType] ?: R.id.chipWater
        chipGroup.check(initialChipId)

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val selectedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val selectedEffect = effectChipMap.entries.find { it.value == selectedId }?.key ?: EffectType.WATER_DROP

            currentSettings.effectType = selectedEffect
            previewOverlay.currentEffect = selectedEffect

            previewOverlay.post {
                previewOverlay.spawnRipple(previewOverlay.width / 2f, previewOverlay.height / 2f)
            }

            ConfigManager.saveSettings(this, currentSettings)
        }
    }

    private fun setupSliders() {
        val sliderSpeed = findViewById<Slider>(R.id.sliderSpeed)
        val sliderSize = findViewById<Slider>(R.id.sliderSize)

        sliderSpeed.value = currentSettings.speedMultiplier.coerceIn(0.3f, 2.0f)
        sliderSize.value = currentSettings.sizeMultiplier.coerceIn(0.3f, 1.5f)

        updateSpeedLabel(sliderSpeed.value)
        updateSizeLabel(sliderSize.value)

        sliderSpeed.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                currentSettings.speedMultiplier = value
                previewOverlay.speedMultiplier = value
                updateSpeedLabel(value)
                ConfigManager.saveSettings(this, currentSettings)
            }
        }

        sliderSize.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                currentSettings.sizeMultiplier = value
                previewOverlay.sizeMultiplier = value
                updateSizeLabel(value)
                ConfigManager.saveSettings(this, currentSettings)
            }
        }
    }

    private fun updateSpeedLabel(speed: Float) {
        val desc = when {
            speed <= 0.4f -> "Ultra Slow-Mo"
            speed <= 0.7f -> "Cinematic Slow"
            speed >= 1.4f -> "Snappy Fast"
            else -> "Normal"
        }
        tvSpeedLabel.text = String.format("Animation Speed: %.1fx (%s)", speed, desc)
    }

    private fun updateSizeLabel(size: Float) {
        val desc = when {
            size <= 0.4f -> "Micro Keycap Glow"
            size <= 0.7f -> "Tight Key Glow"
            size >= 1.3f -> "Wide Reach"
            else -> "Full Keyboard"
        }
        tvSizeLabel.text = String.format("Wave Size: %.1fx (%s)", size, desc)
    }
}
