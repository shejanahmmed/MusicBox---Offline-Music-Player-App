# MusicBox 🎵

**The Premium Offline Music Experience for Android**

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-7F52FF.svg?logo=kotlin&style=for-the-badge)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-9.0%2B-3DDC84.svg?logo=android&style=for-the-badge)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-GPLv3-lightgrey.svg?style=for-the-badge)](LICENSE)
[![Maintenance](https://img.shields.io/badge/Maintained%3F-yes-green.svg?style=for-the-badge)](https://github.com/shejanahmmed/MusicBox---Offline-Music-Player-App/graphs/commit-activity)

<p align="center">
  <a href="#-features">Features</a> •
  <a href="#-download">Download</a> •
  <a href="#-roadmap">Roadmap</a> •
  <a href="#-contributing">Contributing</a> •
  <a href="#-license">License</a>
</p>

</div>

---

## 🚀 Overview

**MusicBox** is an open-source, ad-free, and privacy-focused offline music player designed for audiophiles who value aesthetics as much as performance. Built with **Modern Android Development** practices (Kotlin, MVVM, Coroutines), it delivers a buttery smooth experience wrapped in a stunning glassmorphic UI.

Unlike generic players, MusicBox features a **procedural artwork engine** that generates unique, vintage-style vinyl covers for tracks missing metadata, ensuring your library always looks premium.

---

## ✨ Features at a Glance

| Category | Feature | Description |
| :--- | :--- | :--- |
| **🎨 Visuals** | **Vintage Vinyl Engine** | Procedurally generated retro artwork with realistic noise & texture. |
| | **Dynamic Themes** | Fully responsive Light & Dark modes that follow system settings. |
| | **Smart Animations** | Typewriter greetings and fluid motion transitions. |
| **🎧 Playback** | **Gapless Logic** | Optimized media engine for seamless track transitions. |
| | **Mini Player** | Persistent, smart-clipping controls that float above your content. |
| | **Sleep Timer** | Drift off with a customizable playback countdown. |
| **📂 Library** | **Deep Organization** | Auto-sorts usage into Tracks, Albums, Artists, and Playlists. |
| | **Metadata Editor** | Fix tags directly in-app; persistent changes stored locally. |
| | **Hidden Tracks** | filtering for short clips, voice notes, and unwanted audio. |
| **🛠️ Tech** | **Privacy First** | 100% offline. No analytics. No tracking. No ads. |
| | **Modern Stack** | Built with Kotlin, Coroutines, and Jetpack components. |

---

## 📱 Visual Tour

<div align="center">
  <table>
    <tr>
      <td align="center"><b>Home (Dark)</b></td>
      <td align="center"><b>Player (Light)</b></td>
      <td align="center"><b>Library</b></td>
    </tr>
    <tr>
      <td><img src="docs/screenshots/home_dark.png" width="250" alt="Home Dark" /></td>
      <td><img src="docs/screenshots/player_light.png" width="250" alt="Player Light" /></td>
      <td><img src="docs/screenshots/library.png" width="250" alt="Library" /></td>
    </tr>
  </table>
  <p><i>*Screenshots are placeholders.</i></p>
</div>

---

## 🏗️ Architecture & Engineering

MusicBox helps developers understand **Clean Architecture** and **MVVM** in a real-world context.

### Logical Structure

<details>
<summary><b>Click to expand Project Structure</b></summary>

```text
com.shejan.musicbox
├── activities      # UI Entry Points (Single Activity Pattern where possible)
│   ├── MainActivity.kt
│   └── NowPlayingActivity.kt
├── adapters        # High-performance RecyclerView adapters
│   ├── TrackAdapter.kt
│   └── AlbumAdapter.kt
├── managers        # Domain Logic & State Holders
│   ├── MiniPlayerManager.kt
│   └── TrackMenuManager.kt
├── models          # Immutable Data Classes
│   ├── Track.kt
│   └── Album.kt
├── services        # Foreground Services
│   └── MusicService.kt (MediaButtonReceiver, Notifications)
└── utils           # Extension functions & Helpers
    ├── MusicUtils.kt
    └── ImageLoader.kt
```
</details>

### Tech Stack

| Type | Technology | Benefit |
| :--- | :--- | :--- |
| **Language** | **Kotlin** | Null safety, conciseness, and interop. |
| **Concurrency** | **Coroutines** | Efficient background thread management. |
| **Architecture** | **MVVM** | Separation of UI and Business Logic. |
| **UI** | **XML / ConstraintLayout** | Performance-optimized layouts. |
| **Media** | **MediaSession** | System-level integration (Bluetooth, Lockscreen). |

---

## 📥 Download

The latest APK (v1.0.0) is available on the [Releases Page](https://github.com/shejanahmmed/MusicBox---Offline-Music-Player-App/releases).

<a href="https://github.com/shejanahmmed/MusicBox---Offline-Music-Player-App/releases">
  <img src="https://img.shields.io/badge/Download-APK-blue?style=for-the-badge&logo=android" alt="Download APK" />
</a>

---

## 🛣️ Roadmap

- [x] **v1.0**: Core Playback, Dark/Light Themes, Vinyl Engine.
- [ ] **v1.1**: Equalizer integration (System & Custom bands).
- [ ] **v1.2**: Lyrics support (LRC file parsing).
- [ ] **v1.3**: Cloud backup for Playlists & Favorites.
- [ ] **v2.0**: Material You (Monet) Dynamic Theming.

---

## 🤝 Contributing

We love community involvement! Whether you're a developer, designer, or user, your contributions are welcome.

### How to Contribute
1.  **Fork** the repo.
2.  **Clone** your fork (`git clone ...`).
3.  **Create** a branch (`git checkout -b feature/NewThing`).
4.  **Commit** (`git commit -m "Add NewThing"`).
5.  **Push** (`git push origin feature/NewThing`).
6.  **Open** a Pull Request.

### Reporting Bugs
If you find a bug, please create an Issue with:
*   Steps to reproduce.
*   Expected vs. actual behavior.
*   Device/Android version.

### Style Guide
*   Use present tense in commit messages ("Add feature" not "Added feature").
*   Follow standard Kotlin coding conventions.

---

## 👤 Author

**Shejan Ahmmed**

*   🌐 **Website**: [shejan.me](https://www.farjan.me)
*   💻 **GitHub**: [shejanahmmed](https://github.com/shejanahmmed)
*   💼 **LinkedIn**: [Shejan Ahmmed](https://www.linkedin.com/in/farjan-ahmmed/)
*   📧 **Email**: [farjan.swe@gmail.com](mailto:farjan.swe@gmail.com)

---

## 📄 License

Distributed under the **GNU GPLv3**. See `LICENSE` for more information.

<div align="center">
  <sub>Built with precision and passion. © 2026 Shejan.</sub>
</div>
