package com.custom.gboardrgb

import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    private lateinit var layoutRgbStudio: View
    private lateinit var layoutThemes: View

    private lateinit var tvActiveThemeStatus: TextView
    private lateinit var switch3dBlackBorders: MaterialSwitch
    private lateinit var btnApply3dBlack: MaterialButton
    private lateinit var switch3dWhiteBorders: MaterialSwitch
    private lateinit var btnApply3dWhite: MaterialButton
    private lateinit var btnRestoreStock: MaterialButton

    private lateinit var previewOverlay: RGBRippleOverlayView
    private lateinit var tvWaveSpeedLabel: TextView
    private lateinit var tvWaveSizeLabel: TextView
    private lateinit var tvKeyFlowSpeedLabel: TextView
    private lateinit var tvKeyFlowSizeLabel: TextView
    private lateinit var chipGroup: ChipGroup
    private lateinit var switchAmbientRain: MaterialSwitch
    private lateinit var switchTurboDynamics: MaterialSwitch
    private lateinit var switchGlideTrail: MaterialSwitch
    private lateinit var switchHaptic: MaterialSwitch
    private lateinit var switchUnderglow: MaterialSwitch
    private lateinit var switchVisualEffect: MaterialSwitch
    private lateinit var switchKeyShapeFlow: MaterialSwitch
    private lateinit var switchKeyBorderOnly: MaterialSwitch
    private lateinit var switchCustomColors: MaterialSwitch
    private lateinit var chipGroupSwatches: ChipGroup
    private lateinit var layoutCustomColorsControls: LinearLayout
    private lateinit var layoutVisualEffectControls: LinearLayout
    private lateinit var layoutKeyFlowControls: LinearLayout

    private var currentSettings = ConfigManager.Settings()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        currentSettings = ConfigManager.loadSettings(this)

        tabLayout = findViewById(R.id.tabLayout)
        layoutRgbStudio = findViewById(R.id.layoutRgbStudio)
        layoutThemes = findViewById(R.id.layoutThemes)

        tvActiveThemeStatus = findViewById(R.id.tvActiveThemeStatus)
        switch3dBlackBorders = findViewById(R.id.switch3dBlackBorders)
        btnApply3dBlack = findViewById(R.id.btnApply3dBlack)
        switch3dWhiteBorders = findViewById(R.id.switch3dWhiteBorders)
        btnApply3dWhite = findViewById(R.id.btnApply3dWhite)
        btnRestoreStock = findViewById(R.id.btnRestoreStock)

        tvWaveSpeedLabel = findViewById(R.id.tvWaveSpeedLabel)
        tvWaveSizeLabel = findViewById(R.id.tvWaveSizeLabel)
        tvKeyFlowSpeedLabel = findViewById(R.id.tvKeyFlowSpeedLabel)
        tvKeyFlowSizeLabel = findViewById(R.id.tvKeyFlowSizeLabel)
        chipGroup = findViewById(R.id.chipGroupEffects)
        switchAmbientRain = findViewById(R.id.switchAmbientRain)
        switchTurboDynamics = findViewById(R.id.switchTurboDynamics)
        switchGlideTrail = findViewById(R.id.switchGlideTrail)
        switchHaptic = findViewById(R.id.switchHaptic)
        switchUnderglow = findViewById(R.id.switchUnderglow)
        switchVisualEffect = findViewById(R.id.switchVisualEffect)
        switchKeyShapeFlow = findViewById(R.id.switchKeyShapeFlow)
        switchKeyBorderOnly = findViewById(R.id.switchKeyBorderOnly)
        switchCustomColors = findViewById(R.id.switchCustomColors)
        chipGroupSwatches = findViewById(R.id.chipGroupSwatches)
        layoutCustomColorsControls = findViewById(R.id.layoutCustomColorsControls)
        layoutVisualEffectControls = findViewById(R.id.layoutVisualEffectControls)
        layoutKeyFlowControls = findViewById(R.id.layoutKeyFlowControls)

        setupTabs()
        setupThemeControls()
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
            waveSpeedMultiplier = currentSettings.waveSpeedMultiplier
            waveSizeMultiplier = currentSettings.waveSizeMultiplier
            keyFlowSpeedMultiplier = currentSettings.keyFlowSpeedMultiplier
            keyFlowSizeMultiplier = currentSettings.keyFlowSizeMultiplier
            isAmbientRainEnabled = currentSettings.isAmbientRainEnabled
            useCustomColors = currentSettings.useCustomColors
            isTurboDynamicsEnabled = currentSettings.isTurboDynamicsEnabled
            isGlideTrailEnabled = currentSettings.isGlideTrailEnabled
            isUnderglowEnabled = currentSettings.isUnderglowEnabled
            isVisualEffectEnabled = currentSettings.isVisualEffectEnabled
            isKeyShapeFlowEnabled = currentSettings.isKeyShapeFlowEnabled
            isKeyBorderOnlyEnabled = currentSettings.isKeyBorderOnlyEnabled

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

        switchUnderglow.isChecked = currentSettings.isUnderglowEnabled
        switchUnderglow.setOnCheckedChangeListener { _, isChecked ->
            currentSettings.isUnderglowEnabled = isChecked
            previewOverlay.isUnderglowEnabled = isChecked
            ConfigManager.saveSettings(this, currentSettings)
        }

        updateVisualEffectCardState(currentSettings.isVisualEffectEnabled, animate = false)
        switchVisualEffect.isChecked = currentSettings.isVisualEffectEnabled
        switchVisualEffect.setOnCheckedChangeListener { _, isChecked ->
            currentSettings.isVisualEffectEnabled = isChecked
            previewOverlay.isVisualEffectEnabled = isChecked
            updateVisualEffectCardState(isChecked, animate = true)
            ConfigManager.saveSettings(this, currentSettings)
            previewOverlay.post {
                previewOverlay.spawnRipple(previewOverlay.width / 2f, previewOverlay.height / 2f)
            }
        }

        updateKeyFlowCardState(currentSettings.isKeyShapeFlowEnabled, animate = false)
        switchKeyShapeFlow.isChecked = currentSettings.isKeyShapeFlowEnabled
        switchKeyShapeFlow.setOnCheckedChangeListener { _, isChecked ->
            currentSettings.isKeyShapeFlowEnabled = isChecked
            previewOverlay.isKeyShapeFlowEnabled = isChecked
            updateKeyFlowCardState(isChecked, animate = true)
            ConfigManager.saveSettings(this, currentSettings)
            previewOverlay.post {
                previewOverlay.spawnRipple(previewOverlay.width / 2f, previewOverlay.height / 2f)
            }
        }

        switchKeyBorderOnly.isChecked = currentSettings.isKeyBorderOnlyEnabled
        switchKeyBorderOnly.setOnCheckedChangeListener { _, isChecked ->
            currentSettings.isKeyBorderOnlyEnabled = isChecked
            previewOverlay.isKeyBorderOnlyEnabled = isChecked
            ConfigManager.saveSettings(this, currentSettings)
            previewOverlay.post {
                previewOverlay.spawnRipple(previewOverlay.width / 2f, previewOverlay.height / 2f)
            }
        }

        updateCustomColorsCardState(currentSettings.useCustomColors, animate = false)
        switchCustomColors.isChecked = currentSettings.useCustomColors
        switchCustomColors.setOnCheckedChangeListener { _, isChecked ->
            currentSettings.useCustomColors = isChecked
            previewOverlay.useCustomColors = isChecked
            updateCustomColorsCardState(isChecked, animate = true)
            ConfigManager.saveSettings(this, currentSettings)
            previewOverlay.post {
                previewOverlay.spawnRipple(previewOverlay.width / 2f, previewOverlay.height / 2f)
            }
        }
    }

    private fun setViewGroupEnabled(viewGroup: ViewGroup, enabled: Boolean) {
        viewGroup.isEnabled = enabled
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            child.isEnabled = enabled
            if (child is ViewGroup) {
                setViewGroupEnabled(child, enabled)
            }
        }
    }

    private fun updateCustomColorsCardState(isEnabled: Boolean, animate: Boolean = false) {
        val targetAlpha = if (isEnabled) 1.0f else 0.45f
        if (animate) {
            layoutCustomColorsControls.animate().alpha(targetAlpha).setDuration(200).start()
        } else {
            layoutCustomColorsControls.alpha = targetAlpha
        }
        setViewGroupEnabled(layoutCustomColorsControls, isEnabled)
    }

    private fun updateVisualEffectCardState(isEnabled: Boolean, animate: Boolean = false) {
        val targetAlpha = if (isEnabled) 1.0f else 0.45f
        if (animate) {
            layoutVisualEffectControls.animate().alpha(targetAlpha).setDuration(200).start()
        } else {
            layoutVisualEffectControls.alpha = targetAlpha
        }
        setViewGroupEnabled(layoutVisualEffectControls, isEnabled)
    }

    private fun updateKeyFlowCardState(isEnabled: Boolean, animate: Boolean = false) {
        val targetAlpha = if (isEnabled) 1.0f else 0.45f
        if (animate) {
            layoutKeyFlowControls.animate().alpha(targetAlpha).setDuration(200).start()
        } else {
            layoutKeyFlowControls.alpha = targetAlpha
        }
        setViewGroupEnabled(layoutKeyFlowControls, isEnabled)
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
        val sliderWaveSpeed = findViewById<Slider>(R.id.sliderWaveSpeed)
        val sliderWaveSize = findViewById<Slider>(R.id.sliderWaveSize)
        val sliderKeyFlowSpeed = findViewById<Slider>(R.id.sliderKeyFlowSpeed)
        val sliderKeyFlowSize = findViewById<Slider>(R.id.sliderKeyFlowSize)

        sliderWaveSpeed.isSaveEnabled = false
        sliderWaveSize.isSaveEnabled = false
        sliderKeyFlowSpeed.isSaveEnabled = false
        sliderKeyFlowSize.isSaveEnabled = false

        val waveSpeed = (kotlin.math.round(currentSettings.waveSpeedMultiplier * 10f) / 10f).coerceIn(0.3f, 2.0f)
        val waveSize = (kotlin.math.round(currentSettings.waveSizeMultiplier * 10f) / 10f).coerceIn(0.2f, 1.5f)
        val keyFlowSpeed = (kotlin.math.round(currentSettings.keyFlowSpeedMultiplier * 10f) / 10f).coerceIn(0.3f, 2.0f)
        val keyFlowSize = (kotlin.math.round(currentSettings.keyFlowSizeMultiplier * 10f) / 10f).coerceIn(0.2f, 1.5f)

        sliderWaveSpeed.value = waveSpeed
        sliderWaveSize.value = waveSize
        sliderKeyFlowSpeed.value = keyFlowSpeed
        sliderKeyFlowSize.value = keyFlowSize

        updateWaveSpeedLabel(waveSpeed)
        updateWaveSizeLabel(waveSize)
        updateKeyFlowSpeedLabel(keyFlowSpeed)
        updateKeyFlowSizeLabel(keyFlowSize)

        sliderWaveSpeed.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val clean = (kotlin.math.round(value * 10f) / 10f).coerceIn(0.3f, 2.0f)
                currentSettings.waveSpeedMultiplier = clean
                previewOverlay.waveSpeedMultiplier = clean
                updateWaveSpeedLabel(clean)
                ConfigManager.saveSettings(this, currentSettings)
                previewOverlay.post {
                    previewOverlay.spawnRipple(previewOverlay.width / 2f, previewOverlay.height / 2f)
                }
            }
        }

        sliderWaveSize.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val clean = (kotlin.math.round(value * 10f) / 10f).coerceIn(0.2f, 1.5f)
                currentSettings.waveSizeMultiplier = clean
                previewOverlay.waveSizeMultiplier = clean
                updateWaveSizeLabel(clean)
                ConfigManager.saveSettings(this, currentSettings)
                previewOverlay.post {
                    previewOverlay.spawnRipple(previewOverlay.width / 2f, previewOverlay.height / 2f)
                }
            }
        }

        sliderKeyFlowSpeed.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val clean = (kotlin.math.round(value * 10f) / 10f).coerceIn(0.3f, 2.0f)
                currentSettings.keyFlowSpeedMultiplier = clean
                previewOverlay.keyFlowSpeedMultiplier = clean
                updateKeyFlowSpeedLabel(clean)
                ConfigManager.saveSettings(this, currentSettings)
                previewOverlay.post {
                    previewOverlay.spawnRipple(previewOverlay.width / 2f, previewOverlay.height / 2f)
                }
            }
        }

        sliderKeyFlowSize.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val clean = (kotlin.math.round(value * 10f) / 10f).coerceIn(0.2f, 1.5f)
                currentSettings.keyFlowSizeMultiplier = clean
                previewOverlay.keyFlowSizeMultiplier = clean
                updateKeyFlowSizeLabel(clean)
                ConfigManager.saveSettings(this, currentSettings)
                previewOverlay.post {
                    previewOverlay.spawnRipple(previewOverlay.width / 2f, previewOverlay.height / 2f)
                }
            }
        }
    }

    private fun updateWaveSpeedLabel(speed: Float) {
        val desc = when {
            speed <= 0.4f -> "Ultra Slow-Mo"
            speed <= 0.7f -> "Cinematic Slow"
            speed >= 1.4f -> "Snappy Fast"
            else -> "Normal"
        }
        tvWaveSpeedLabel.text = String.format("Wave Speed: %.1fx (%s)", speed, desc)
    }

    private fun updateWaveSizeLabel(size: Float) {
        val desc = when {
            size <= 0.25f -> "Ultra-Tight / Concentrated"
            size <= 0.5f -> "Medium Spread"
            size >= 1.3f -> "Wide Reach"
            else -> "Full Reach"
        }
        tvWaveSizeLabel.text = String.format("Wave Size: %.1fx (%s)", size, desc)
    }

    private fun updateKeyFlowSpeedLabel(speed: Float) {
        val desc = when {
            speed <= 0.4f -> "Ultra Slow-Mo"
            speed <= 0.7f -> "Cinematic Flow"
            speed >= 1.4f -> "Instant Snap"
            else -> "Normal"
        }
        tvKeyFlowSpeedLabel.text = String.format("Key Glow Speed: %.1fx (%s)", speed, desc)
    }

    private fun updateKeyFlowSizeLabel(size: Float) {
        val desc = when {
            size <= 0.25f -> "Neighbors Only"
            size <= 0.5f -> "Subtle Matrix Glow"
            size >= 1.3f -> "Full Keyboard Cascade"
            else -> "Full Keyboard"
        }
        tvKeyFlowSizeLabel.text = String.format("Key Glow Spread: %.1fx (%s)", size, desc)
    }

    private fun setupTabs() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        layoutRgbStudio.visibility = View.VISIBLE
                        layoutThemes.visibility = View.GONE
                    }
                    1 -> {
                        layoutRgbStudio.visibility = View.GONE
                        layoutThemes.visibility = View.VISIBLE
                        refreshActiveThemeDisplay()
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupThemeControls() {
        refreshActiveThemeDisplay()

        btnApply3dBlack.setOnClickListener {
            btnApply3dBlack.isEnabled = false
            btnApply3dBlack.text = "⏳ Deploying 3D Black..."
            lifecycleScope.launch {
                val withBorders = switch3dBlackBorders.isChecked
                val res = ThemeInstaller.applyTheme(this@MainActivity, "3D_Black.zip", withBorders)
                btnApply3dBlack.isEnabled = true
                btnApply3dBlack.text = "🚀 Apply 3D Black Theme"
                if (res.isSuccess) {
                    Toast.makeText(this@MainActivity, "🌑 3D Black Theme applied! Open Gboard.", Toast.LENGTH_LONG).show()
                    refreshActiveThemeDisplay()
                } else {
                    Toast.makeText(this@MainActivity, "Failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnApply3dWhite.setOnClickListener {
            btnApply3dWhite.isEnabled = false
            btnApply3dWhite.text = "⏳ Deploying 3D White..."
            lifecycleScope.launch {
                val withBorders = switch3dWhiteBorders.isChecked
                val res = ThemeInstaller.applyTheme(this@MainActivity, "3D_White.zip", withBorders)
                btnApply3dWhite.isEnabled = true
                btnApply3dWhite.text = "Apply 3D White Theme"
                if (res.isSuccess) {
                    Toast.makeText(this@MainActivity, "⚪ 3D White Theme applied! Open Gboard.", Toast.LENGTH_LONG).show()
                    refreshActiveThemeDisplay()
                } else {
                    Toast.makeText(this@MainActivity, "Failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnRestoreStock.setOnClickListener {
            btnRestoreStock.isEnabled = false
            lifecycleScope.launch {
                val res = ThemeInstaller.restoreDefaultTheme(this@MainActivity)
                btnRestoreStock.isEnabled = true
                if (res.isSuccess) {
                    Toast.makeText(this@MainActivity, "🔄 Stock theme restored.", Toast.LENGTH_SHORT).show()
                    refreshActiveThemeDisplay()
                } else {
                    Toast.makeText(this@MainActivity, "Failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun refreshActiveThemeDisplay() {
        lifecycleScope.launch {
            val active = ThemeInstaller.getActiveThemeName()
            val text = when {
                active.contains("3D_Black", ignoreCase = true) -> "Current Theme: 🌑 3D Black Edition (Active)"
                active.contains("3D_White", ignoreCase = true) -> "Current Theme: ⚪ 3D White Edition (Active)"
                active.isNotBlank() -> "Current Theme: $active"
                else -> "Current Theme: System Default / Stock"
            }
            tvActiveThemeStatus.text = text
        }
    }
}
