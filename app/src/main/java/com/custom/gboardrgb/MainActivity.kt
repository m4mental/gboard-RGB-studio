package com.custom.gboardrgb

import android.graphics.Color
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
    private lateinit var switchTurboDynamics: MaterialSwitch
    private lateinit var switchGlideTrail: MaterialSwitch
    private lateinit var switchHaptic: MaterialSwitch
    private lateinit var switchCustomColors: MaterialSwitch
    private lateinit var chipGroupSwatches: ChipGroup

    private var currentSettings = ConfigManager.Settings()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        currentSettings = ConfigManager.loadSettings(this)

        tvSpeedLabel = findViewById(R.id.tvSpeedLabel)
        tvSizeLabel = findViewById(R.id.tvSizeLabel)
        chipGroup = findViewById(R.id.chipGroupEffects)
        switchAmbientRain = findViewById(R.id.switchAmbientRain)
        switchTurboDynamics = findViewById(R.id.switchTurboDynamics)
        switchGlideTrail = findViewById(R.id.switchGlideTrail)
        switchHaptic = findViewById(R.id.switchHaptic)
        switchCustomColors = findViewById(R.id.switchCustomColors)
        chipGroupSwatches = findViewById(R.id.chipGroupSwatches)

        setupPreviewCanvas()
        setupChips()
        setupSliders()
        setupSwitches()
        setupSwatches()
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
            useCustomColors = currentSettings.useCustomColors
            isTurboDynamicsEnabled = currentSettings.isTurboDynamicsEnabled
            isGlideTrailEnabled = currentSettings.isGlideTrailEnabled

            try {
                customColorPrimary = Color.parseColor(currentSettings.colorPrimary)
                customColorSecondary = Color.parseColor(currentSettings.colorSecondary)
            } catch (e: Exception) {
                // Default
            }
        }
        previewContainer.addView(previewOverlay)

        previewContainer.setOnTouchListener { _, event ->
            val action = event.actionMasked
            when (action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val idx = if (action == MotionEvent.ACTION_POINTER_DOWN) event.actionIndex else 0
                    previewOverlay.spawnRipple(event.getX(idx), event.getY(idx))
                }
                MotionEvent.ACTION_MOVE -> {
                    previewOverlay.addGlidePoint(event.x, event.y)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    previewOverlay.finishGlide()
                }
            }
            true
        }
    }

    private fun setupSwitches() {
        switchAmbientRain.isChecked = currentSettings.isAmbientRainEnabled
        switchAmbientRain.setOnCheckedChangeListener { _, isChecked ->
            currentSettings.isAmbientRainEnabled = isChecked
            previewOverlay.isAmbientRainEnabled = isChecked
            ConfigManager.saveSettings(this, currentSettings)
        }

        switchTurboDynamics.isChecked = currentSettings.isTurboDynamicsEnabled
        switchTurboDynamics.setOnCheckedChangeListener { _, isChecked ->
            currentSettings.isTurboDynamicsEnabled = isChecked
            previewOverlay.isTurboDynamicsEnabled = isChecked
            ConfigManager.saveSettings(this, currentSettings)
        }

        switchGlideTrail.isChecked = currentSettings.isGlideTrailEnabled
        switchGlideTrail.setOnCheckedChangeListener { _, isChecked ->
            currentSettings.isGlideTrailEnabled = isChecked
            previewOverlay.isGlideTrailEnabled = isChecked
            ConfigManager.saveSettings(this, currentSettings)
        }

        switchHaptic.isChecked = currentSettings.isHapticEnabled
        switchHaptic.setOnCheckedChangeListener { _, isChecked ->
            currentSettings.isHapticEnabled = isChecked
            ConfigManager.saveSettings(this, currentSettings)
        }

        switchCustomColors.isChecked = currentSettings.useCustomColors
        switchCustomColors.setOnCheckedChangeListener { _, isChecked ->
            currentSettings.useCustomColors = isChecked
            previewOverlay.useCustomColors = isChecked
            ConfigManager.saveSettings(this, currentSettings)
            previewOverlay.post {
                previewOverlay.spawnRipple(previewOverlay.width / 2f, previewOverlay.height / 2f)
            }
        }
    }

    private fun setupSwatches() {
        val swatchMap = mapOf(
            R.id.chipSwatchNothing to Pair("#FF0033", "#FFFFFF"),
            R.id.chipSwatchCyberpunk to Pair("#FFE600", "#00FFF5"),
            R.id.chipSwatchDracula to Pair("#BD93F9", "#50FA7B"),
            R.id.chipSwatchSunset to Pair("#FF5E3A", "#FF2A68"),
            R.id.chipSwatchIce to Pair("#00F5FF", "#FFFFFF")
        )

        chipGroupSwatches.setOnCheckedStateChangeListener { _, checkedIds ->
            val selectedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val colors = swatchMap[selectedId] ?: return@setOnCheckedStateChangeListener

            currentSettings.useCustomColors = true
            currentSettings.colorPrimary = colors.first
            currentSettings.colorSecondary = colors.second

            switchCustomColors.isChecked = true
            previewOverlay.useCustomColors = true
            previewOverlay.customColorPrimary = Color.parseColor(colors.first)
            previewOverlay.customColorSecondary = Color.parseColor(colors.second)

            ConfigManager.saveSettings(this, currentSettings)

            previewOverlay.post {
                previewOverlay.spawnRipple(previewOverlay.width / 2f, previewOverlay.height / 2f)
            }
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
