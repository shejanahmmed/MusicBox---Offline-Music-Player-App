# MusicBox 🎵
**An Elegant, Offline Music Player for Android**

MusicBox is a modern, feature-rich offline music player built with Kotlin for Android. It focuses on a premium user experience, featuring a sleek dark-themed UI, intuitive scrollable navigation, and robust library management with advanced customization options.

---

## ✨ Key Features

### 🎵 Music Library
*   **Comprehensive Library Management**: Automatically scans and organizes your local audio files into **Tracks**, **Albums**, **Artists**, **Folders**, and **Playlists**.
*   **Smart Filtering**: Filter tracks by minimum duration to hide short audio clips and notifications.
*   **Hidden Tracks Manager**: Hide unwanted tracks from your library and restore them anytime from the Deleted Tracks page.
*   **Custom Metadata Editor**: Edit track titles, artists, and album names with persistent custom metadata storage.

### 🎨 Personalization
*   **Customizable Home Screen**: Reorder, show/hide home boxes (Favorites, Tracks, Albums, Artists, Playlists, Equalizer) to create your perfect layout.
*   **Custom Artwork Editor**: Set custom album artwork for any track with an 85% screen-height drawer for better visibility.
*   **Dynamic Greeting**: Personalized time-based greetings (Good Morning, Good Afternoon, Good Evening) with typing animation.
*   **Flexible Navigation**: Set any page as your default home screen (Home, Tracks, Albums, Artists, Playlists, Search).

### 🎧 Playback & Organization
*   **❤️ Favorites & Playlists**: Create unlimited custom playlists with full CRUD operations (Create, Read, Update, Delete).
*   **Advanced Sorting**: Sort by Title, Date Added, or Date Modified with persistent preferences per page.
*   **Shuffle & Repeat Modes**: Full playback control with shuffle and repeat (All/One/Off) modes.
*   **Queue Management**: View and reorder your "Up Next" queue with visual indicators for the currently playing track.
*   **Mini Player**: Persistent mini-player controls allowing you to manage playback while browsing the app.

### 🔍 Discovery & Navigation
*   **Smart Search**: Instantly find any song, artist, or album with a powerful search feature.
*   **📂 Folder Browsing**: Navigate your storage directly to play music from specific folders with breadcrumb navigation.
*   **🎛️ Dynamic Navigation**: Features a unique **Scrollable Bottom Navigation Bar** that provides quick access to 6+ categories while keeping essential tools like Search and Settings pinned.

### 📤 Sharing & Integration
*   **Share Music**: Share audio files directly from the Now Playing screen or Track Options menu.
*   **System Equalizer**: Quick access to your device's built-in equalizer for audio customization.

### 🎨 Premium UI/UX
*   **Dark Mode First**: Designed with a premium dark aesthetic, utilizing glassmorphism elements and smooth transitions.
*   **Typing Animations**: Dynamic greeting text with typewriter effect.
*   **Consistent Header Styling**: Unified header design across all content pages with dynamic item counts.
*   **Context-Aware Navigation**: Bottom navigation intelligently highlights the relevant tab based on current content.

## 🛠️ Tech Stack

*   **Language**: [Kotlin](https://kotlinlang.org/)
*   **Architecture**: MVVM / Standard Android Architecture Patterns
*   **UI Components**:
    *   XML Layouts
    *   `ConstraintLayout`, `RecyclerView`, `HorizontalScrollView`, `BottomSheetDialog`
    *   Custom Drawable Resources & Animations
*   **Core APIs**:
    *   `MediaStore` API (for fetching audio files)
    *   `MediaPlayer` (for audio playback)
    *   `SharedPreferences` (for settings and simple persistence)
    *   JSON-based local storage (for playlists and custom metadata)
    *   `FileProvider` (for secure file sharing)
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

## 📱 App Structure

*   **Home**: Customizable dashboard with quick access to all music categories
*   **Tracks**: Complete track listing with sorting and filtering options
*   **Albums**: Browse music organized by album with hidden track filtering
*   **Artists**: View all artists with track counts and dynamic navigation
*   **Playlists**: Create and manage custom playlists stored locally
*   **Folders**: Navigate your file system to play music from specific directories
*   **Search**: Global search across tracks, albums, and artists
*   **Settings**: Customize app behavior, navigation, home layout, and track filtering
*   **Now Playing**: Full-screen player with artwork, controls, queue, and sharing options

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

## 📄 License

Copyright (C) 2026 Shejan

This project is licensed under the **GNU General Public License v3.0**.  
You may copy, distribute, and modify the software as long as you track changes/dates in source files. Any modifications to or software including (via compiler) GPL-licensed code must also be made available under the GPL along with build & install instructions.

See the [LICENSE](LICENSE) file for details.

---

*Made with ❤️ and Kotlin.*
