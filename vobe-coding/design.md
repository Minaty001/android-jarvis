# Visual Design System & UI Specifications - Android J.A.R.V.I.S.

## 1. Design Tokens (`Ui.kt`)

### 1.1 Color Palette
Android J.A.R.V.I.S. utilizes a **Stitch Cyber-Slate & Holographic Dark Theme**:

| Token Name | Hex Code | Purpose |
|------------|----------|---------|
| `Ui.BG` | `#05090D` | Deep space root background |
| `Ui.SURFACE` | `#0B141E` | Primary glassmorphic container |
| `Ui.SURFACE_2` | `#101A23` | Secondary elevated surface |
| `Ui.BORDER` | `#203442` | Subtle cyan glass card border |
| `Ui.BORDER_GLOW` | `#3C494B` | Active glowing container border |
| `Ui.CYAN` | `#63EFFF` | Primary brand accent & HUD text |
| `Ui.CYAN_DIM` | `#2BA8B8` | Muted cyan background fills |
| `Ui.TEXT` | `#DAE3F1` | Primary text color |
| `Ui.MUTED` | `#869395` | Secondary text & subtitles |
| `Ui.SUCCESS` | `#70F0B0` | Active status & confirmation badges |
| `Ui.WARNING` | `#FFC857` | Caution alerts & uncalibrated badges |
| `Ui.ERROR` | `#FF6B6B` | Error indicators & danger actions |

---

## 2. Typography & HUD Badges

- **Font Family**: System Default Sans-Serif / Monospace for telemetry numbers.
- **Title Text**: Bold, letter-spaced `0.16f`, Cyan (`#63EFFF`), 18sp - 21sp.
- **Subtitle / Label**: Semi-bold, letter-spaced `0.08f`, Muted (`#869395`), 9.5sp - 11sp.
- **HUD Badges (`Ui.hudBadge()`)**: Rounded capsule badge with subtle border, bold text, and status-colored background tint.

---

## 3. Custom Interactive Components

### 3.1 Arc Reactor Hero (`JarvisOrbView.kt`)
- Interactive pulsing holographic core reflecting real-time assistant state:
  - `WAITING_FOR_WAKE`: Cyan idle breathing pulse.
  - `LISTENING`: Bright cyan expanding wave.
  - `THINKING`: Cyan/Amber rotating energy rings.
  - `SPEAKING`: Dynamic amplitude-synced glowing pulse.

### 3.2 Live Audio Waveform (`JarvisWaveformView.kt`)
- Real-time PCM audio amplitude visualizer with smooth cubic bezier smoothing.

### 3.3 Glassmorphic Cards (`Ui.glassCard()`)
- Rounded corners (`12dp` - `16dp`), semi-transparent surface background (`#0B141E`), and glowing borders.

---

## 4. Navigation & Layout Hierarchy (`ResponsiveNav.kt`)
- Bottom/Top responsive navigation bar (`58dp` height) with 6 core tabs:
  1. `⌂ Home` (`DashboardScreen.kt`)
  2. `⏱ Routines` (`RoutinesScreen.kt`)
  3. `⚡ Skills` (`SkillsScreen.kt`)
  4. `◈ Memory` (`MemoryScreen.kt`)
  5. `◌ Brain` (`ApiManagerScreen.kt`)
  6. `⚙ Settings` (`SettingsScreen.kt`)
