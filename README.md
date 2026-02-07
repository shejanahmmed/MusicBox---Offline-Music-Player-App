# MusicBox 🎵

**An Elegant, Offline Music Player for Android**

[![Kotlin Version](https://img.shields.io/badge/Kotlin-1.9.0-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
[![Android API](https://img.shields.io/badge/API-28%2B-3DDC84.svg?logo=android)](https://android.com)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM-blue.svg)](https://developer.android.com/topic/architecture)
[![License](https://img.shields.io/badge/License-GPLv3-lightgrey.svg)](LICENSE)

MusicBox is a modern, native Android music player built purely with **Kotlin**. It emphasizes a clean **MVVM architecture**, efficient media handling via `MediaSession`, and a premium ad-free user experience.

Designed for simplicity and performance, it features a custom-built scanning engine, robust metadata management, and a unique procedural artwork generator.

---

## 🏗️ Architecture

The app follows the recommended **Model-View-ViewModel (MVVM)** architecture to ensure separation of concerns and testability.

*   **View Layer**: Fragments and Activities handling UI logic (`MainActivity`, `NowPlayingActivity`).
*   **ViewModel Layer**: Manages UI state and communicates with repositories.
*   **Model/Repository Layer**: Handles data operations with `MediaStore` and local JSON storage.
*   **Service Layer**: `MusicService` handles background audio playback and notification management, ensuring the music keeps playing even when the app is killed.

---

## 🛠️ Tech Stack

| Component | Library / Tool | Purpose |
| :--- | :--- | :--- |
| **Language** | [Kotlin](https://kotlinlang.org/) | 100% native development. |
| **Async** | [Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) | Asynchronous background tasks. |
| **Android UI** | [ConstraintLayout](https://developer.android.com/training/constraint-layout) | Responsive UI layouts. |
| **Architecture** | [ViewModel & LiveData](https://developer.android.com/topic/libraries/architecture) | State management and lifecycle awareness. |
| **Media** | `MediaPlayer` & `MediaSession` | Core audio playback and system integration. |
| **Storage** | `SharedPreferences` & `File I/O` | User preferences and playlist persistence. |
| **Image Loading** | `Coil` / Custom implementation | Efficient artwork loading and caching. |
| **Build System** | Gradle (Kotlin DSL) | Dependency management and build configuration. |

---

## 📂 Logical Structure

The application codebase is organized into several key functional groups:

*   **Activities**: Main UI entry points (`MainActivity`, `NowPlayingActivity`, `SettingsActivity`).
*   **Adapters**: Connects data to UI lists (`TrackAdapter`, `AlbumAdapter`, `QueueAdapter`).
*   **Managers**: Handles specific business logic (`MiniPlayerManager`, `FavoritesManager`, `TrackMenuManager`).
*   **Services**: Background operations (`MusicService`).
*   **Utils**: shared helper functions (`MusicUtils`, `NavUtils`, `ImageLoader`).
*   **Models**: Data classes for media objects (`Track`, `Album`, `Playlist`).

---

## ✨ Core Features

### 🎧 Audio Engine
*   **Gapless-style logic** for smooth track transitions.
*   **Foreground Service** implementation for reliable background playback.
*   **Focus Management** handling audio interruptions (calls, other apps).
*   **Bluetooth/Headset Integration** via `MediaButtonReceiver`.

### 🎨 Procedural Artwork
*   **Vintage Vinyl Generation**: A custom algorithm generates unique, retro-styled vinyl artwork for tracks missing covers.
*   **Vector Construction**: Uses Android `VectorDrawable` paths for crisp, noise-textured graphics without bitmap artifacts.

### 💾 Data Management
*   **Custom Indexing**: Scans `MediaStore` efficiently to build a rich library of Tracks, Albums, and Artists.
*   **JSON Persistence**: Playlists and custom metadata are stored in local JSON files for portability and ease of backup.

---

## 🚀 Getting Started

### Prerequisites
*   **Android Studio**: Iguana (2023.2.1) or newer.
*   **JDK**: Version 17.
*   **SDK**: Minimum API 28 (Android 9.0).

### Implementation Steps

1.  **Clone the Repository**
    ```bash
    git clone https://github.com/shejanahmmed/MusicBox---Offline-Music-Player-App.git
    ```

2.  **Open in Android Studio**
    *   File -> Open -> Select project root.
    *   Allow Gradle sync to finish.

3.  **Build & Run**
    *   Select `app` configuration.
    *   Run on Emulator or Device (Ensure distinct profile for storage permission tests).

---

## 🤝 Contributing

Contributions are welcome! Please follow these guidelines:

1.  **Fork** the repository.
2.  **Branch** off `main` (`git checkout -b feature/dynamic-colors`).
3.  **Commit** with clear messages (`git commit -m "Feat: Add dynamic color support"`).
4.  **Pull Request** targeted at `main`.

Please ensure your code follows the **Kotlin Style Guide** and includes relevant comments.

---

## 👤 Author

**Shejan Ahmmed**
*   **GitHub**: [shejanahmmed](https://github.com/shejanahmmed)
*   **LinkedIn**: [Shejan Ahmmed](https://www.linkedin.com/in/farjan-ahmmed/)

---

## 📄 License

This project is licensed under the **GNU General Public License v3.0**. See the [LICENSE](LICENSE) file for details.
