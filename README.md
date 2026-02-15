<div align="center">

# MusicBox 🎵

**The Premium Offline Music Experience for Android**

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-7F52FF.svg?logo=kotlin&style=for-the-badge)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-9.0%2B-3DDC84.svg?logo=android&style=for-the-badge)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-GPLv3-lightgrey.svg?style=for-the-badge)](LICENSE)
[![Maintenance](https://img.shields.io/badge/Maintained%3F-yes-green.svg?style=for-the-badge)](https://github.com/shejanahmmed/MusicBox---Offline-Music-Player-App/graphs/commit-activity)
[![Build Status](https://img.shields.io/github/actions/workflow/status/shejanahmmed/MusicBox---Offline-Music-Player-App/android.yml?branch=main&style=for-the-badge&logo=github)](https://github.com/shejanahmmed/MusicBox---Offline-Music-Player-App/actions)

<p align="center">
  <a href="#-features">Features</a> •
  <a href="#-download">Download</a> •
  <a href="#-tech-stack">Tech Stack</a> •
  <a href="#-roadmap">Roadmap</a> •
  <a href="#-contributing">Contributing</a>
</p>

</div>

---

## 🚀 Overview

**MusicBox** is an open-source, ad-free, and privacy-focused offline music player designed for audiophiles who value **aesthetics** as much as **performance**.

Built with **Modern Android Development** practices (Kotlin, MVVM, Coroutines), it delivers a buttery smooth experience wrapped in a stunning **Glassmorphic UI**. Unlike generic players, MusicBox features a **procedural artwork engine** that generates unique, vintage-style vinyl covers for tracks missing metadata, ensuring your library always looks premium.

---

## 📱 Visual Showcase

> _Screenshots coming soon..._

|                            **Now Playing**                             |                            **Library**                             |                              **Themes**                              |
| :--------------------------------------------------------------------: | :----------------------------------------------------------------: | :------------------------------------------------------------------: |
| <img src="docs/screenshots/now_playing_placeholder.png" width="200" /> | <img src="docs/screenshots/library_placeholder.png" width="200" /> | <img src="docs/screenshots/dark_mode_placeholder.png" width="200" /> |

---

## ✨ Key Features

### 🎨 **Premium Visuals**

- **Glassmorphic Design**: A modern, translucent UI that adapts to your album art.
- **Vintage Vinyl Engine**: Procedurally generates retro artwork with realistic noise & texture for songs without covers.
- **Smart Animations**: Fluid motion transitions and typewriter-style greetings.
- **Dynamic Themes**: Fully responsive Light & Dark modes that follow system settings.

### 🎧 **Immersive Playback**

- **Gapless Audio Engine**: Optimized for seamless track transitions.
- **Haptic Feedback**: Tactile vibrations for controls, favorites, and swipe gestures.
- **Mini Player**: Floating controls with swipe gestures (Left/Right) for easy navigation.
- **Smart Auto-Scroll**: Your active track is always in view when you open the list.
- **Sleep Timer**: Drift off with a customizable playback countdown.

### 📂 **Deep Organization**

- **Smart Library**: Auto-sorts your music into Tracks, Albums, Artists, and Playlists.
- **Metadata Editor**: Fix tags directly in-app; persistent changes are stored locally.
- **Hidden Tracks**: Filter out short clips, voice notes, and unwanted audio.

### 🛡️ **Privacy First**

- **100% Offline**: No internet access required.
- **No Tracking**: Zero analytics or data collection.
- **Ad-Free**: A distinctively clean experience forever.

---

## 🏗️ Architecture & Tech Stack

MusicBox is built to demonstrate **Clean Architecture** and **MVVM** principles in a real-world context.

### **The Stack**

- **Language**: [Kotlin](https://kotlinlang.org/) (100%)
- **Architecture**: MVVM (Model-View-ViewModel)
- **Concurrency**: [Coroutines](https://developer.android.com/kotlin/coroutines) & Flow
- **UI**: XML with [Material Components](https://material.io/develop/android) & ConstraintLayout
- **Media**: [MediaSession](https://developer.android.com/guide/topics/media-apps/working-with-a-media-session) for system integration

### **Project Structure**

<details>
<summary><b>Click to expand</b></summary>

```text
com.shejan.musicbox
├── activities      # UI Entry Points (Single Activity Pattern where possible)
├── adapters        # High-performance RecyclerView adapters
├── managers        # Domain Logic & State Holders (MiniPlayer, TrackMenu)
├── models          # Immutable Data Classes
├── services        # Foreground Media Services
└── utils           # Extension functions & Helpers
```

</details>

---

## 📥 Download

The latest APK is available on the [Releases Page](https://github.com/shejanahmmed/MusicBox---Offline-Music-Player-App/releases).

<a href="https://github.com/shejanahmmed/MusicBox---Offline-Music-Player-App/releases">
  <img src="https://img.shields.io/badge/Download-APK-blue?style=for-the-badge&logo=android" alt="Download APK" />
</a>

---

## 🛣️ Roadmap

| Version  | Status       | Focus                                                    |
| :------- | :----------- | :------------------------------------------------------- |
| **v1.0** | ✅ Completed | Core Playback, Dark/Light Themes, Vinyl Engine.          |
| **v1.1** | ✅ Completed | UI Polish, Haptic Feedback, Auto-Scroll, Swipe Gestures. |
| **v1.2** | 🚧 Planned   | Equalizer integration (System & Custom bands).           |
| **v1.3** | 🚧 Planned   | Lyrics support (LRC file parsing).                       |
| **v2.0** | 🔮 Future    | Material You (Monet) Dynamic Theming.                    |

---

## 🤝 Contributing

We love community involvement! Whether you're a developer, designer, or user, your contributions are welcome.

1.  **Fork** the repo.
2.  **Clone** your fork.
3.  **Create** a branch (`git checkout -b feature/NewThing`).
4.  **Commit** (`git commit -m "Add NewThing"`).
5.  **Push** (`git push origin feature/NewThing`).
6.  **Open** a Pull Request.

---

## 👤 Author

**Shejan Ahmmed**

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

---

## 📄 License

Distributed under the **GNU GPLv3**. See `LICENSE` for more information.

<div align="center">
  <sub>Built with precision and passion. © 2026 Shejan.</sub>
</div>
