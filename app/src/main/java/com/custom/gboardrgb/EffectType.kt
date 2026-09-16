package com.custom.gboardrgb

enum class EffectType(val id: Int, val displayName: String, val description: String) {
    WATER_DROP(0, "Fluid Water", "Pani ki boond drop splash with 3 fluid wave crests"),
    NEON_LIGHTNING(1, "Neon Lightning", "Jagged electric arc sparks crackling outward"),
    COSMIC_SUPERNOVA(2, "Cosmic Supernova", "Star flash core with floating stardust nebula particles"),
    MOLTEN_MAGMA(3, "Molten Magma", "Volcanic lava explosion with rising burning embers"),
    SONIC_WAVE(4, "Sonic Soundwave", "Harmonic undulating acoustic frequency ripples"),
    BLACK_HOLE(5, "Quantum Black Hole", "Gravitational light suction + event horizon shockwave"),
    AMBIENT_RAIN(6, "Ambient Raindrop", "Random rain falling on keyboard + heavy tap splash"),
    RAZER_CHROMA(7, "Razer Chroma", "Mechanical 360° rainbow spectrum wave");

    companion object {
        fun fromId(id: Int): EffectType {
            return entries.find { it.id == id } ?: WATER_DROP
        }
    }
}
