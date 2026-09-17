# ⌨️ Gboard RGB Studio

[![Android](https://img.shields.io/badge/Platform-Android%2010%2B-green.svg)](https://android.com)
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed%20%2F%20Zygisk-purple.svg)](https://github.com/LSPosed/LSPosed)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org)
[![Theme](https://img.shields.io/badge/Designed%20For-AMOLED%20Pitch%20Black%20%26%20Rboard%203D-black.svg)]()

**Gboard RGB Studio** is an advanced LSPosed module and real-time companion control app that injects dynamic, reactive visual effects and fluid particle physics directly into **Google Keyboard (Gboard)** on rooted Android devices.

All effects render inside a custom hardware-accelerated canvas overlay dynamically mapped to the keyboard chassis — supporting **Standard Docked Keyboard**, **Floating Keyboard Mode**, and **One-Handed Mode** without altering or displacing the keyboard layout.

---

## ✨ Key Features

- 💡 **Perimeter Underglow (Mechanical Keyboard Edge Lighting):**
  - Continuous ambient luminous breathing aura tracing the outer perimeter of the keyboard chassis.
  - Pulses and surges with reactive brilliance on every keypress.
  - Automatically color-synced with active procedural presets or custom dual-tone palettes.
  - Dedicated one-tap toggle switch in the companion app with zero-latency IPC broadcast synchronization.

- ⌨️ **Keycap Matrix Flow & Authentic Shape Geometry:**
  - Reactive luminescence cascades through physical keycap shapes across rows.
  - Exact key shape recognition: **Caps Lock / Shift (`▲`)** and **Backspace (`⌫`)** are styled with authentic square squircles matching letter keys, while **`?123`** and **`Enter`** render as circular pills.
  - **Keycap Border Rim Only** option: Restrict glowing light exclusively to the mechanical outer borders of keycaps.

- 🎛️ **Independent Effect & Key Flow Toggles:**
  - Dedicated switches for **Visual Effect Presets** and **Keycap Matrix Flow**.
  - Run them individually or layer them together simultaneously for maximum visual impact.

- 🎨 **Built-in 3D Mechanical Themes (Direct Root Engine):**
  - **3D Pitch-Black OLED Edition:** True pitch-black AMOLED canvas with 3D elevated keycaps and crisp borders, built specifically to make RGB luminescence pop!
  - **3D White Edition:** Frosted light aesthetic with subtle mechanical elevation.
  - **Stock Theme Restore:** Instant one-tap restore back to default Gboard theme.
  - Applies directly via Root (`su`) without needing Rboard Theme Manager.

- 🪟 **Complete Floating & One-Handed Keyboard Mode Support:**
  - Full compatibility with Gboard's **Floating Mode** and **One-Handed Mode**.
  - Dynamic discovery of the active keyboard chassis (`KeyboardHolder`) with automatic hit-testing and real-time drag tracking across the screen.

- 💧 **7 Procedural Visual Effect Presets:**
  1. **💧 Fluid Water Droplet:** Realistic liquid splash with expanding caustic refraction ripples.
  2. **⚡ Cyberpunk Neon Lightning:** High-voltage electric crackle arcs branching outwards with rapid flicker fade.
  3. **🌌 Cosmic Supernova:** Central starlight burst scattering 18 drifting nebula spark particles.
  4. **🔥 Molten Magma & Embers:** Blazing lava core explosion with thermal updraft ember particles.
  5. **🔊 Sonic Soundwave:** Undulating sinusoidal acoustic sound waves radiating across keycaps.
  6. **🌀 Quantum Black Hole:** Inward gravitational contraction ring followed by a prismatic event-horizon burst.
  7. **🌧️ Ambient Raindrops:** Gentle, random raindrops falling across idle keyboard corners every 2s, plus heavy splashes on keypress.
  *(Bonus: 🌈 **Razer Chroma** classic mechanical rainbow wave).*

- ⚡ **Zero-Restart Real-Time Synchronization:**
  - Switch presets, themes, or adjust sliders in the companion app; updates broadcast instantly to Gboard in under 5ms without killing or restarting Gboard.

- 🌊 **Multi-Touch Crossover Physics:**
  - Type at full speed with multiple fingers simultaneously — waves do not cancel each other; they collide, overlap, and blend seamlessly in the hardware framebuffer.

- 🔁 **Continuous Long-Press Wave Pulse:**
  - Hold Backspace or any key to trigger rhythmic, continuous wave bursts (280ms threshold, 115ms interval) for satisfying key repeat feedback.

- 🎛️ **Granular Precision Sliders:**
  - **Animation Speed:** Scalable from `0.3x` (*Ultra Slow-Mo Liquid Glide*) up to `2.0x` (*Snappy Fast*).
  - **Wave Size / Reach:** Scalable from `0.2x` (*Ultra-Tight / Neighbors Only*) up to `1.5x` (*Full Keyboard Reach*).

- 🎨 **Custom Dual-Tone Palettes & Curated Swatches:**
  - One-tap switchable aesthetic presets: **Nothing OS** (Red/White), **Cyberpunk** (Yellow/Cyan), **Dracula** (Purple/Green), **Sunset** (Orange/Pink), and **Glacier Ice** (Cyan/White).

- 🔥 **Typing Speed (WPM) Adaptive Dynamics ("Turbo Overheat"):**
  - Instantaneous typing speed tracker (rolling window). Burst fast typing (> 70 WPM) supercharges effects with **+65% extra particles, blazing sparks**, and intensified core glow!

- ✨ **Swipe / Glide Typing Neon Laser Trail:**
  - Fluid, luminous laser beam with outer neon glow that traces swipe gestures across keys and dissipates with graceful stardust decay.

- 📳 **Tactile Haptic Physics Sync:**
  - Sub-millisecond tactile micro-ticks (`EFFECT_TICK`) synced with single taps, liquid droplet impacts, and key repeats.

- 🔒 **Dynamic Boundary Clipping:**
  - Strictly clipped to Gboard's visible boundaries (`SoftKeyboardView` / `KeyboardHolder`), preventing visual spillover into chat history or status bars while preserving outer underglow diffusion.

---

## 📱 Companion Control App

The module includes a native Material 3 AMOLED Dark UI featuring:
- **Interactive Live Preview Canvas:** Tap on the preview box to test any preset, speed, or size immediately.
- **Single-Tap Preset Selectors:** Filter chips for all presets.
- **Toggle Switches:** Real-time controls for Haptic Feedback, Typing Speed Dynamics, Swipe/Glide Trail, and Perimeter Underglow.
- **Embedded Typing Sandbox:** Built-in test field to test Gboard reactions directly inside the app.

---

## 🛠️ Architecture & Technology Stack

| Component | Implementation |
| :--- | :--- |
| **Hook Engine** | LSPosed / Xposed API (`IXposedHookLoadPackage`) targeting `com.google.android.inputmethod.latin` |
| **Render Surface** | Hardware-accelerated custom `View` (`RGBRippleOverlayView`) mounted directly on window `DecorView` with dynamic `KeyboardHolder` chassis tracking |
| **Touch Interception** | Non-intrusive hook on `ViewGroup.dispatchTouchEvent` at root level with active chassis hit-testing and screen coordinate normalization |
| **Inter-Process Comm** | Dual-channel: Android System `BroadcastReceiver` + JSON persistence (`/data/local/tmp/gboard_rgb_config.json`) |
| **Design Language** | Material 3 Dark AMOLED (`#000000` pitch black background, `#00FFF5` electric cyan accents) |

---

## 🚀 Installation & Setup

### Prerequisites
1. Rooted Android device running **Android 10+** (KernelSU, Magisk, or APatch).
2. **LSPosed (Zygisk)** active and operational.
3. **Google Keyboard (Gboard)** installed and selected as the default IME.

### Steps
1. Clone or download the repository:
   ```bash
   git clone https://github.com/m4mental/gboard-RGB-studio.git
   ```
2. Build and install the APK via Android Studio or Gradle:
   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
3. Open **LSPosed Manager**:
   - Enable the **Gboard RGB Studio** module.
   - Ensure the scope includes **Gboard (`com.google.android.inputmethod.latin`)**.
4. Force stop Gboard once or launch the **Gboard RGB Studio** companion app.
5. Tap the test field, select your favorite effect preset, and enjoy next-generation keyboard visuals!

---

## 📄 License
Open source under the [Apache 2.0 License](LICENSE).
