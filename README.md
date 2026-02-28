<div align="center">

# MusicBox 🎵

**The Premium Offline Music & Video Experience for Android**

<a href="https://play.google.com/store/apps/details?id=com.shejan.musicbox">
  <img alt="Get it on Google Play" height="60" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" />
</a>
<a href="https://github.com/shejanahmmed/MusicBox---Offline-Music-Player-App/releases">
  <img alt="Get it on GitHub" height="60" src="https://raw.githubusercontent.com/rubenpgrady/get-it-on-github/refs/heads/main/get-it-on-github.png" />
</a>

<p align="center">
  <a href="#-features">Features</a> •
  <a href="#-download">Download</a> •
  <a href="#-tech-stack">Tech Stack</a> •
  <a href="#-changelog">Changelog</a> •
<a href="#-contributing">Contributing</a>

</p>

</div>

---

## 🚀 Overview

**MusicBox** is an open-source, ad-free, and privacy-focused offline music & video player designed for audiophiles who value **aesthetics** as much as **performance**.

Built with **Modern Android Development** practices (Kotlin, MVVM, Coroutines), it delivers a buttery smooth experience wrapped in a stunning **Glassmorphic UI**. Unlike generic players, MusicBox features a **procedural artwork engine** that generates unique, vintage-style vinyl covers for tracks missing metadata, ensuring your library always looks premium.

---

## ✨ Key Features

### 🎨 **Premium Visuals**

- **Glassmorphic Design**: A modern, translucent UI that adapts to your album art.
- **Vintage Vinyl Engine**: Procedurally generates retro artwork with realistic noise & texture for songs without covers.
- **Smart Animations**: Fluid motion transitions and typewriter-style greetings.
- **Dynamic Themes**: Fully responsive Light & Dark modes that follow system settings.
- **Edge-to-Edge UI**: True full-screen experience — app background extends seamlessly behind the status bar.

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
- **Deleted Tracks Folder**: Restore any hidden audio or video file from one place.

### 🎬 **Videos Page** _(New in v1.3.0)_

- **Dedicated Videos Tab**: Browse all local video files sorted by title, date added, or date modified.
- **Video Options Menu**: Full 3-dot options dialog for each video — share, favorite, add to playlist, view metadata, and delete.
- **Video Duration Filter**: Set minimum and maximum duration to filter your video library in Settings.
- **Deleted Videos**: Hidden videos appear in the Deleted Tracks folder alongside audio, with full restore support.

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

## 📋 Changelog

### v1.3.0 _(Latest)_

- 🎬 **Videos Page**: Dedicated tab for browsing local video files with sort controls
- ⋮ **Video Options Menu**: Full options dialog for videos (share, favorite, playlist, metadata, delete)
- ⏱️ **Video Duration Filter**: Set min/max duration range for the video library in Settings
- 🗑️ **Deleted Videos**: Hidden videos now appear in Deleted Tracks folder with restore support
- 🖥️ **Edge-to-Edge UI**: App background extends seamlessly behind the status bar on all devices
- 🐛 **Bug Fixes**: Resolved double video load on launch, SharedPrefs anti-pattern, missing deprecation suppressions

### v1.2.0

- Initial Videos page with navigation
- Library Preferences section in Settings
- Audio track duration filter

---

## 📥 Download

**MusicBox is now available on the Google Play Store!**

<a href="https://play.google.com/store/apps/details?id=com.shejan.musicbox">
  <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="60" />
</a>
<br>

Alternatively, you can download the latest APK from the [Releases Page](https://github.com/shejanahmmed/MusicBox---Offline-Music-Player-App/releases).

<a href="https://github.com/shejanahmmed/MusicBox---Offline-Music-Player-App/releases">
  <img src="https://raw.githubusercontent.com/rubenpgrady/get-it-on-github/refs/heads/main/get-it-on-github.png" alt="Get it on GitHub" height="60" />
</a>

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
