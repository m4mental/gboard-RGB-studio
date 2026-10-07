# ⌨️ Gboard RGB Studio

[![Android](https://img.shields.io/badge/Platform-Android%2010%2B-green.svg)](https://android.com)
[![LSPosed](https://img.shields.io/badge/LSPosed%20API-93%2B-purple.svg)](https://github.com/LSPosed/LSPosed)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**Gboard RGB Studio** is an LSPosed module and real-time companion control app that injects dynamic, reactive visual effects and fluid particle physics directly into **Google Keyboard (Gboard)** on rooted Android devices.

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
  - Exact key shape recognition: **Caps Lock / Shift (`▲`)**, **Backspace (`⌫`)**, **Comma (`,`)**, **Emoji**, and **Period (`.`)** are styled with authentic square squircles matching letter keys (`A, S, D, F`), while **`?123`** and **`Enter`** render as circular action pills.
  - **Keycap Border Rim Only** option: Restrict glowing light exclusively to the mechanical outer borders of keycaps.

- 🎛️ **Decoupled Independent Dual Tuning Sliders:**
  - Dedicated master switches for **Visual Effect Presets** and **Mechanical Key Matrix Flow**.
  - **Independent Speed Multipliers**: Tune Wave propagation speed (`0.3x` to `2.0x`) independently from Keycap Matrix lighting transitions (`0.3x` to `2.0x`).
  - **Independent Reach / Size Multipliers**: Adjust circular wave radius spread independently from keycap matrix cascade reach.

- 🎚️ **Modular Card Architecture & Master Toggle Dimming:**
  - Control app redesign featuring high-end modular cards for **Visual Effect Presets**, **Mechanical Key Matrix**, and **Custom Dual-Tone Palette**.
  - Intelligent real-time control state: Turning any master switch OFF automatically dims child sliders and chips to `45% alpha` and disables interaction for an authentic high-end RGB suite (Corsair iCUE / Razer Synapse style).

- 🎨 **Rboard & AMOLED Theme Compatibility:**
  - Compatible with **Rboard Theme Manager** and stock Gboard AMOLED / Pitch Black themes.
  - Adapts lighting shaders, drop shadows, and particle effects to match active Rboard or Material You themes without conflicts.

- 🪟 **Complete Floating & One-Handed Keyboard Mode Support:**
  - Compatible with Gboard's **Floating Mode** and **One-Handed Mode**.
  - Dynamic discovery of the active keyboard chassis (`KeyboardHolder`) with automatic hit-testing and real-time drag tracking across the screen.

- 💧 **8 Visual Effect Presets:**
  1. **💧 Fluid Water Droplet:** Realistic liquid splash with expanding caustic refraction ripples.
  2. **⚡ Cyberpunk Neon Lightning:** High-voltage electric crackle arcs branching outwards with rapid flicker fade.
  3. **🌌 Cosmic Supernova:** Central starlight burst scattering drifting nebula spark particles.
  4. **🔥 Molten Magma & Embers:** Blazing lava core explosion with thermal updraft ember particles.
  5. **🔊 Sonic Soundwave:** Undulating sinusoidal acoustic sound waves radiating across keycaps.
  6. **🌀 Quantum Black Hole:** Inward gravitational contraction ring followed by a prismatic event-horizon burst.
  7. **🌧️ Ambient Raindrops:** Gentle, random raindrops falling across idle keyboard corners every 2s, plus heavy splashes on keypress.
  8. **🌈 Razer Chroma:** Classic mechanical rainbow wave cycling through full spectrum hues.

- ⚡ **Zero-Restart Real-Time Synchronization:**
  - Switch presets, themes, or adjust sliders in the companion app; updates broadcast immediately to Gboard via local IPC without restarting Gboard.

- 🌊 **Multi-Touch Crossover Physics & Long-Press Jitter:**
  - Multi-finger typing support with colliding, overlapping, and blending ripple waves in the hardware framebuffer.
  - Polar touch micro-jitter during continuous long-press key repeats for authentic mechanical key switch vibrations.

- 🎨 **Custom Dual-Tone Palettes & Curated Swatches:**
  - One-tap switchable aesthetic presets: **Nothing OS** (Red/White), **Cyberpunk** (Yellow/Cyan), **Dracula** (Purple/Green), **Sunset** (Orange/Pink), and **Glacier Ice** (Cyan/White).

- 🔥 **Typing Speed (WPM) Adaptive Dynamics ("Turbo Overheat"):**
  - Instantaneous typing speed tracker (rolling window). Fast typing (> 70 WPM) enhances effects with extra particles and intensified core glow.

- ✨ **Swipe / Glide Typing Neon Laser Trail:**
  - Fluid, luminous laser beam with outer neon glow that traces swipe gestures across keys and dissipates with graceful stardust decay.

- 📳 **Tactile Haptic Physics Sync:**
  - Tactile micro-ticks (`EFFECT_TICK`) synced with single taps, liquid droplet impacts, and key repeats.

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
| **Hook Engine** | LSPosed / Xposed API (min version 93, `IXposedHookLoadPackage`) targeting `com.google.android.inputmethod.latin` |
| **Render Surface** | Hardware-accelerated custom `View` (`RGBRippleOverlayView`) mounted directly on window `DecorView` with dynamic `KeyboardHolder` chassis tracking |
| **Touch Interception** | Non-intrusive hook on `ViewGroup.dispatchTouchEvent` at root level with active chassis hit-testing and screen coordinate normalization |
| **Inter-Process Comm** | Dual-channel: Android System `BroadcastReceiver` protected by signature-level permission + root JSON persistence |
| **Design Language** | Material 3 Dark AMOLED (`#000000` pitch black background, `#00FFF5` electric cyan accents) |

---

## ⚠️ Known Limitations

- **View Discovery Heuristics**: Keyboard chassis and view holder detection relies on simple class-name heuristics (`KeyboardHolder`, `SoftKeyboardView`). Substantial obfuscation or layout restructuring in future Gboard releases may require fallback to procedural geometry.
- **Language-Dependent Function Key Detection**: Automatic corner radius mapping for squircle/pill shapes relies on English `contentDescription` strings (e.g. "shift", "delete", "123", "enter"). On non-English locale descriptions, standard keycap geometry is used.
- **Procedural Key-Matrix Fallback**: If native child key views cannot be located or queried (e.g. in certain virtualized canvas themes or preview environments), a realistic 4-row procedural keycap matrix is rendered instead.
- **Theme Restore Force-Stop**: Restoring default stock Gboard themes requires root privileges to clear theme configuration XMLs and force-stops the Gboard process (`am force-stop com.google.android.inputmethod.latin`), requiring user confirmation before execution.

---

## 🧪 Tested On

| Device / Model | Android Version | Gboard Version | LSPosed / Root Solution | Result |
| :--- | :--- | :--- | :--- | :--- |
| Google Pixel (Generic) | Android 14 (API 34) | Latest Play Store | KernelSU / LSPosed (Zygisk) | Pass |
| Generic AOSP / Custom ROM | Android 13 (API 33) | 13.x | Magisk / LSPosed | Pass |

---

## ✅ Manual Verification Checklist

- [ ] **Hook Mount**: Confirm LSPosed logs indicate successful injection into `com.google.android.inputmethod.latin` and overlay view mounts onto window DecorView.
- [ ] **Docked Mode**: Open keyboard in standard docked position; verify perimeter underglow, keycap matrix ripples, and laser glide trail follow keyboard bounds.
- [ ] **Floating Mode**: Switch Gboard to floating mode; drag keyboard across screen and verify overlay tracks and re-anchors to new screen coordinates.
- [ ] **One-Handed Mode**: Switch to left- or right-handed one-handed mode; ensure geometry cache updates and effects clip strictly within the narrowed keyboard chassis.
- [ ] **Settings Sync**: Change effects, sliders, and color switches in companion app; observe instant update in typing preview and live keyboard without Gboard restart.
- [ ] **Theme Restore**: Trigger theme restore in companion app; verify confirmation dialog appears, root script executes cleanly, Gboard is restarted, and stock theme is restored without errors.

---

## 🚀 Installation & Setup

### Prerequisites
1. Rooted Android device running **Android 10+** (API 29+) (KernelSU, Magisk, or APatch).
2. **LSPosed (Zygisk)** active and operational with **Xposed API minimum version 93**.
3. **Google Keyboard (Gboard)** scope set to **`com.google.android.inputmethod.latin`**.

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
