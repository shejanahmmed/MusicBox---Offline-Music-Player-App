# MusicBox 🎵
**An Elegant, Offline Music Player for Android**

MusicBox is a modern, feature-rich offline music player built with Kotlin for Android. It focuses on a premium user experience, featuring a sleek dark-themed UI, intuitive scrollable navigation and robust library management.

---

## ✨ Key Features

*   **🎧 Comprehensive Library Management**: Automatically scans and organizes your local audio files into **Tracks**, **Albums**, **Artists**, **Folders** and **Playlists**.
*   **❤️ Favorites & Playlists**: Easily create custom playlists and mark songs as favorites for quick access.
*   **🔍 Smart Search**: Instantly find any song, artist, or album with a powerful search feature.
*   **📂 Folder Browsing**: Navigate your storage directly to play music from specific folders.
*   **🎛️ Dynamic Navigation**: Features a unique **Scrollable Bottom Navigation Bar** that provides quick access to 6+ categories (Home, Tracks, Albums, Folders, Artists, Playlists) while keeping essential tools like Search and Settings pinned.
*   **🎨 Premium UI/UX**: Designed with a "Dark Mode first" aesthetic, utilizing glassmorphism elements, smooth transitions and a clean, clutter-free interface.
*   **🔄 Sorting Options**: Sort your music by Title, Date Added, or Date Modified.
*   **⏯️ Mini Player**: Persistent mini-player controls allowing you to manage playback while browsing the app.


## 🛠️ Tech Stack

*   **Language**: [Kotlin](https://kotlinlang.org/)
*   **Architecture**: MVVM / Standard Android Architecture Patterns
*   **UI Components**:
    *   XML Layouts
    *   `ConstraintLayout`, `RecyclerView`, `HorizontalScrollView`
    *   Custom Drawable Resources
*   **Core APIs**:
    *   `MediaStore` API (for fetching audio files)
    *   `MediaPlayer` (for audio playback)
    *   `SharedPreferences` (for settings and simple persistence)
*   **Permissions**: Handles Runtime Permissions for `READ_MEDIA_AUDIO` (Android 13+) and `READ_EXTERNAL_STORAGE`.

## 🚀 Getting Started

### Prerequisites
*   Android Studio Iguana or newer.
*   JDK 17 or newer.
*   Android Device or Emulator running Android 9 (Pie) or higher.

### Installation

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/shejanahmmed/MusicBox---Offline-Music-Player-App.git
    ```
2.  **Open in Android Studio**:
    *   Launch Android Studio.
    *   Select **Open an Existing Project**.
    *   Navigate to the cloned directory and select it.
3.  **Build and Run**:
    *   Wait for Gradle sync to complete.
    *   Connect your device or start an emulator.
    *   Click the **Run** button (green play icon).

## 🔒 Permissions

To function correctly, MusicBox requires access to your device's storage to read audio files.
*   On first launch, you will be prompted to grant **Storage/Music** permissions.
*   The app gracefully handles permission denial with informative prompts.

## 🤝 Contributing

Contributions are welcome! If you have suggestions for new features or bug fixes:

1.  Fork the repository.
2.  Create a new branch (`git checkout -b feature/AmazingFeature`).
3.  Commit your changes (`git commit -m 'Add some AmazingFeature'`).
4.  Push to the branch (`git push origin feature/AmazingFeature`).
5.  Open a Pull Request.

## 👤 Author

**Farjan Ahmmed**

*   **Website**: [farjan.me](https://www.farjan.me)
*   **GitHub**: [shejanahmmed](https://github.com/shejanahmmed)
*   **LinkedIn**: [Farjan Ahmmed](https://www.linkedin.com/in/farjan-ahmmed/)
*   **Instagram**: [iamshejan](https://www.instagram.com/iamshejan/)
*   **Email**: [farjan.swe@gmail.com](mailto:farjan.swe@gmail.com)

---

*Made with ❤️ and Kotlin.*
