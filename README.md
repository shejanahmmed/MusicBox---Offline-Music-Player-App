<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" alt="MusicBox Logo" width="128" height="128" />

# MusicBox 🎵

### *The Premium Offline Music & Video Experience for Android*

[![GitHub Release](https://img.shields.io/github/v/release/shejanahmmed/MusicBox---Offline-Music-Player-App?style=for-the-badge&color=7C4DFF&logo=github)](https://github.com/shejanahmmed/MusicBox---Offline-Music-Player-App/releases)
[![License](https://img.shields.io/github/license/shejanahmmed/MusicBox---Offline-Music-Player-App?style=for-the-badge&color=00E676)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_7.0%2B_(API_24)-00E5FF?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Built With Kotlin](https://img.shields.io/badge/Built_With-Kotlin-orange?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)

<p align="center">
  <a href="#-key-features"><b>Key Features</b></a> •
  <a href="#-whats-new-in-v270"><b>What's New (v2.7.0)</b></a> •
  <a href="#-architecture--tech-stack"><b>Tech Stack</b></a> •
  <a href="#-download"><b>Download</b></a> •
  <a href="#-changelog"><b>Changelog</b></a> •
  <a href="#-author"><b>Author</b></a>
</p>

---

</div>

## 🚀 Overview

**MusicBox** is an open-source, ad-free, and privacy-first offline music and video player crafted for users who demand **visual elegance**, **audiophile audio quality**, and **premium performance**.

Built with modern Android engineering standards (Kotlin DSL, Coroutines, Media3 ExoPlayer, MVVM with StateFlow, Clean Architecture), it merges a buttery-smooth **Glassmorphic UI** with a state-of-the-art local playback engine. MusicBox features high-res lossless codec playback (24-bit/192kHz FLAC, ALAC, Opus, WAV, AAC, MP3), micro-volume fades, transient focus ducking, a **procedural vintage vinyl engine**, interactive micro-animations, and customizable home screen widgets.

---

## ✨ Key Features

### 🎧 Audiophile Audio & Video Engine
*   **AndroidX Media3 ExoPlayer:** High-performance local playback engine delivering lossless audio quality up to 24-bit/192kHz.
*   **Dynamic Audio Quality & Resolution Inspection:** Real-time hardware analysis of audio bit depth (16/24-bit), sample rate (up to 192kHz), and bitrate (up to 320 kbps), displaying clean audiophile badges in Track Info (e.g., `24-BIT / 192k`, `16-BIT / 44.1k`, `320 kbps`).
*   **Micro-Volume Fades:** Gentle interpolated fading on play (180ms) and pause (150ms) to eliminate sudden clicks and pops.
*   **Intelligent Audio Focus & Ducking:** Smooth volume ducking during notifications and dynamic auto-pause when headphones or Bluetooth disconnect.
*   **Precision Equalizer & DSP:** 5-band customized EQ with live `audioSessionId` auto-binding, 6 acoustic presets, and real-time speed & pitch controls.
*   **Swipe-to-Control Mini Player:** Easily swipe left or right to skip tracks, tap to pause, or expand into the full player.
*   **Advanced Video Hub:** Full-featured local video player with custom duration filtering, sorting options, and dedicated metadata inspection.

### 🎨 Premium Visual Experience
*   **Glassmorphic Design:** Translucent, adaptive interface components that dynamically tone and blur based on active album art.
*   **Vintage Vinyl Engine:** Generates procedurally rendered retro vinyl records with realistic textures and animations for files without embedded covers.
*   **Interactive Micro-interactions:** Tactile feedback on controls and a spring-loaded Instagram-style favorite animation.
*   **Edge-to-Edge Layout:** Immersive layout that extends content directly behind the system status and navigation bars.
*   **Cassette-Style Widgets:** Beautiful Light and Dark mode home screen widgets with playback controls and dynamic equalizer animations.

### 📂 Advanced Library & Queue Control
*   **On-the-Fly Queueing:** Prepend or append songs to your active playlist with "Play Next" and "Play Last" action buttons.
*   **Thread-Safe Playback State:** Concurrency-protected queue operations and sequential background state serialization.
*   **Dynamic Fast-Scroll:** Enhanced scrolling layout with letter-bubble tracking and custom scroll-indicator pill visibility.
*   **Local Metadata Editor:** Edit titles, artists, and album fields directly inside the app, persisting changes cleanly.
*   **Safe-Keep Deleted Trash:** Hidden audio/video clips go into a trash folder where they can be restored or permanently purged.

### 🛡️ Privacy & Reliability
*   **100% Offline operation:** No telemetry, zero internet connections, completely private.
*   **Ad-Free forever:** Transparent open-source license with no monetization or trackers.

---

## ⚡ What's New in v2.7.0

The **v2.7.0 Release** brings performance optimizations, rock-solid background audio stability, notification rate-limiting, and modern UI refinements:

| Feature | Description |
| :--- | :--- |
| **🖼️ Glide Image Engine & Bitmap Pooling** | Migrated artwork loading to high-performance Glide with hardware bitmap pooling and annotation processing, drastically reducing GC churn. |
| **🔔 Debounced Media Notifications** | Intelligent 200ms rate-limiting for media session notification updates, preventing system rate-limiting and notification spam on rapid skips. |
| **🎧 Reliable Background Micro-Fades** | Replaced VSYNC/choreographer-bound animators with Handler-based faders for seamless, popping-free track transitions when the screen is off. |
| **✨ Bento-Grid About Screen** | Redesigned developer & about view with modern bento cards, interactive social profile intents (LinkedIn, GitHub, Website), and direct Play Store linking. |
| **📱 Android 15 Edge-to-Edge Ready** | Updated AndroidX Activity and Material dependencies with smooth scroll-away headers in Settings and About screens. |

---

## 🏗️ Architecture & Tech Stack

MusicBox is built with **Clean Architecture** patterns under the **MVVM** architecture paradigm:

```text
com.shejan.musicbox
├── viewmodels      # Reactive StateFlow ViewModels (Tracks, Albums, Artists, Videos)
├── repository      # Centralized MediaStore repository with Coroutines
├── activities      # UI entry points & main activities
├── adapters        # High-performance list & media recyclers
├── managers        # Domain logic, EqManager, miniplayer handlers, track actions
├── services        # Media3 ExoPlayer background playback & lifecycle
├── widgets         # Interactive Home Screen widgets & providers
└── utils           # Extension libraries, view animations, helpers
```

### Technical Specifications
*   **Core Language:** [Kotlin](https://kotlinlang.org/) (100% codebase)
*   **Audio Engine:** [AndroidX Media3 ExoPlayer](https://developer.android.com/media/media3/exoplayer)
*   **Architecture:** MVVM with Kotlin Coroutines & `StateFlow`
*   **Build System:** Gradle Version Catalog (`libs.versions.toml`) + Kotlin DSL (`build.gradle.kts`)
*   **User Interface:** Edge-to-edge XML layouts with custom Material Design components & Lottie animations
*   **System Integration:** Android Jetpack libraries, MediaSessionCompat, and Foreground Services
*   **Image Loading:** High-performance Glide cache with blur transformations

---

## 📥 Installation

MusicBox is available for install via official channels:

<table align="center">
  <tr>
    <td align="center">
      <a href="https://play.google.com/store/apps/details?id=com.shejan.musicbox">
        <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="55" /><br/>
        <b>Google Play Store</b>
      </a>
    </td>
    <td align="center">
      <a href="https://github.com/shejanahmmed/MusicBox---Offline-Music-Player-App/releases">
        <img src="https://raw.githubusercontent.com/rubenpgrady/get-it-on-github/refs/heads/main/get-it-on-github.png" alt="Get it on GitHub" height="55" /><br/>
        <b>Direct APK Releases</b>
      </a>
    </td>
  </tr>
</table>

---

## 📋 Changelog

### v2.7.0 _(Current Release)_
- **🔔 Debounced Media Notifications:** Added 200ms debounce rate-limiting to `MusicService` notification dispatchers, preventing Android notification spam and system rate-limiting when rapidly skipping tracks.
- **🖼️ Glide Annotation Processor Integration:** Configured official Glide compiler annotation processor in Gradle build catalog for optimized image decoder compilation.
- **⚡ Version Code 27 Alignment:** Bumped `versionCode` to 27 and `versionName` to 2.7.0.

### v2.6.0
- **🖼️ High-Performance Glide Image Engine:** Integrated Glide for album art caching and hardware bitmap pooling, drastically reducing garbage collection overhead and memory churn during fast library scrolling.
- **🎧 Reliable Background Micro-Fades:** Replaced VSYNC-dependent view animators with Handler-based faders, ensuring smooth 180ms/150ms audio fades during background track transitions without screen dependency.
- **✨ Bento-Grid About Screen:** Complete redesign of About & Developer screen featuring modern bento layout cards, active version Play Store linking, and native social profile intent triggers (LinkedIn, GitHub, Website).
- **📜 Scroll-Away Headers:** Implemented subtle scroll-away header animations in Settings and About screens for immersive navigation.
- **📱 Android 15 Edge-to-Edge Compliance:** Upgraded AndroidX Activity and Material library components to ensure seamless edge-to-edge UI compliance.
- **🎨 Home Dashboard Touch Polish:** Refined dashboard grid layout with modernized ripples and manifest lint optimizations.

### v2.5.0
- **📊 Dynamic Audio Quality Classifier:** Background hardware audio analyzer inspecting sample rates (up to 192kHz), bit depth (16/24-bit), and bitrate, showing clear resolution badges in the track metadata options dialog.
- **🎧 AndroidX Media3 / ExoPlayer Engine:** Full playback engine upgrade supporting high-res lossless codecs (24-bit/192kHz FLAC, ALAC, Opus, WAV, AAC, MP3) and eliminating legacy player error states.
- **✨ Micro-Volume Fades & Acoustic Smoothing:** Added soft volume fading on play (180ms) and pause (150ms) to eliminate popping sounds.
- **🔊 Smart Audio Focus Ducking:** Intelligent transient ducking during navigation/system notifications and auto-pause upon headphone disconnect (`ACTION_AUDIO_BECOMING_NOISY`).
- **🎛️ Dynamic EQ Session Binding:** Synchronized `EqManager` audio session ID binding across track transitions with high-resolution speed and pitch controls.
- **🏛️ Modern MVVM Architecture:** Unified `MusicRepository` coroutine queries with reactive `StateFlow` ViewModels and lifecycle-aware collection.
- **🛡️ Thread-Safe Queue State:** Concurrency-protected playlist mutation APIs and sequential background state serialization.
- **⚡ Version Code 25 Alignment:** Bumped to version 2.5.0 (Build 25) across build files, Settings, and About views.

### v2.1.0
- **📻 Cassette-Style Widgets:** Fully functional cassette-style home screen widgets in Light and Dark mode, featuring metadata display, dynamic equalizer visuals, and full control listeners.
- **🧪 Experimental Features Settings:** Added a dedicated toggle inside settings under "Experimental Features" to let users enable/disable home screen widgets. Dynamically updates system receiver states.
- **📐 Uniform Spacing:** Standardized card margins to `24dp` before settings section headers for a premium, clean visual structure.

### v2.0.0
- **🎛️ Equalizer Refactor:** Replaced standard sliders with beautiful custom `VerticalSeekBar` elements featuring precise visual tracking, interactive labels, and instant-apply preset chips.
- **❤️ Micro-Interaction Pops:** Added a spring-elastic Instagram-style pop animation to favorite buttons inside the metadata drawer and Now Playing screen.
- **🔀 Queue Injection:** Introduced `Play Next` and `Play Last` controls in the track action menu to dynamically manage playback without interrupting current queues.
- **⚡ Smooth Fast-Scroll:** Replaced standard Android scrollbars with a custom dynamic pill, expanded touch targets, and fixed ViewPropertyAnimator bugs to ensure buttery-smooth fast scrolling and proper letter bubble animations.
- **📜 Marquee Path Viewer:** Upgraded static path widgets to an elegant auto-scrolling marquee path visualizer within the track properties menu.
- **🔄 Background Stability:** Fixed playback drops by ensuring explicit `MusicService` initialization for state persistence across background lifecycles.
- **📐 Onboarding Polish:** Polished layout alignment, resolved rendering/animation clipping issues, and added dynamic layout-height equalizer animation loops.
- **✅ Global Version Alignment:** Full upgrade of all files (gradle files, activities, setting panels, resources) to official release version `2.0.0`.

### v1.6.5
- 🎨 **Premium Theme Enhancements**: Significantly improved both **Light and Dark modes** with refined color palettes (Premium Ash) and better contrast.
- 💎 **Sleek UI Design**: Enhanced card layouts, buttons, and iconography across the app for a more professional and modern aesthetic.
- 🛠️ **UI Layout Fixes**: Increased bottom padding in all scrollable lists (Tracks, Albums, etc.) to prevent content from being hidden behind the mini-player.
- 📐 **Navigation Refinement**: Adjusted "Now Playing" header spacing and alignment for better visual balance.
- ✅ **Version Alignment**: Standardised versioning to v1.6.5 (Build 15).

### v1.6.0
- ⚙️ **Home Customization**: Added comprehensive settings to reorder and hide boxes on the Home screen.
- 🎨 **Visual Tweaks**: Navigation and layout improvements across list items.

### v1.3.1
- 🎬 **Videos Page**: Dedicated tab for browsing local video files with sort controls.
- ⋮ **Video Options Menu**: Full options dialog for videos (share, favorite, playlist, metadata, delete).
- ⏱️ **Video Duration Filter**: Set min/max duration range for the video library in Settings.
- 🗑️ **Deleted Videos**: Hidden videos now appear in Deleted Tracks folder with restore support.
- 🖥️ **Edge-to-Edge UI**: App background extends seamlessly behind the status bar on all devices.
- 🐛 **Bug Fixes**: Resolved double video load on launch, SharedPrefs anti-pattern, missing deprecation suppressions.

### v1.2.0
- Initial Videos page with navigation.
- Library Preferences section in Settings.
- Audio track duration filter.

---

## 🤝 Contributing

We welcome community feedback, pull requests, and suggestions!

1. **Fork** the repository
2. **Create** a branch (`git checkout -b feature/AmazingFeature`)
3. **Commit** your changes (`git commit -m 'feat: Add AmazingFeature'`)
4. **Push** to the branch (`git push origin feature/AmazingFeature`)
5. **Open** a Pull Request

---

## 👥 Authors & Contributors

**Shejan Ahmmed** — Lead Developer & Designer

<p align="left">
  <a href="https://shejan.me">
    <img src="https://img.shields.io/badge/Website-shejan.me-blue?style=for-the-badge&logo=google-chrome&logoColor=white" alt="Website" />
  </a>
  <a href="https://github.com/shejanahmmed">
    <img src="https://img.shields.io/badge/GitHub-shejanahmmed-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub" />
  </a>
  <a href="https://www.linkedin.com/in/farjan-ahmmed/">
    <img src="https://img.shields.io/badge/LinkedIn-Shejan%20Ahmmed-0077B5?style=for-the-badge&logo=linkedin&logoColor=white" alt="LinkedIn" />
  </a>
  <a href="mailto:farjan.swe@gmail.com">
    <img src="https://img.shields.io/badge/Email-farjan.swe%40gmail.com-D14836?style=for-the-badge&logo=gmail&logoColor=white" alt="Email" />
  </a>
</p>

---

## 📄 License

This project is licensed under the **GNU General Public License v3.0** - see the [LICENSE](LICENSE) file for details.

<div align="center">
  <sub>Built with precision, style, and passion. © 2026 Shejan Ahmmed.</sub>
</div>
