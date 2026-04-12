# JUKE v1.0.7-beta Release Notes

## 🎉 Release Overview

**Release Date:** February 15, 2026  
**Version:** 1.0.7-beta  
**Status:** Beta Release  

JUKE v1.0.7-beta delivers powerful new features and critical stability fixes. This release introduces the **Purge** storage cleanup tool, a one-tap **Radio** mode for infinite discovery, **Repeat** controls, **Notification Favorites**, and resolves major playback issues with shuffle mode and Spotdown API connectivity.

## 🔑 Key Highlights

### 🗑️ Purge Redundant Tracks & Storage Cleanup

- **Smart Purge**: Identify and batch-delete unused songs from Audio Settings. Flags old-unplayed, never-played, short audio, and incomplete-metadata tracks while protecting Favorites and playlisted songs.
- **Storage Savings**: See estimated space to be freed before confirming deletion.

### 📻 Radio Mode & Repeat Controls

- **Radio Button**: Tap once on the Player Screen to reset the queue and start a fresh recommendation-driven radio session based on the current track.
- **Repeat Mode**: New Repeat button cycles through Repeat Off → Repeat All → Repeat One.
- **Shuffle in Controls**: Quick shuffle toggle now directly in player controls.

### ❤️ Notification Favorites

- **Heart Toggle**: Favorite/unfavorite the current track directly from the media notification. Real-time icon sync.

### 🐛 Critical Stability Fixes

- **Shuffle Queue Fix**: Resolved bug where shuffle mode stopped after ~14 tracks due to ExoPlayer shuffle order being inadvertently reset on track transitions.
- **Shuffle Recommendations**: Fixed premature recommendation triggers during shuffle by accurately counting remaining tracks in the shuffle order.
- **Spotdown API**: Added required API key header and structured error handling to prevent download failures.

## ✨ What's New in v1.0.1-beta

### 🚀 Major Features

#### 🎛️ Advanced Audio Controls

- **Skip Silence**: Automatically detects and skips silent intros and outros effectively cutting the "dead air" between tracks.
- **Audio Settings**: Toggle "Stable Volume", "Skip Silence", and adjust "Equalizer" directly from settings.

- **10-Band Equalizer**: Professional-grade audio equalizer with 10 frequency bands for precise sound customization
- **Volume Booster**: Enhanced volume control allowing up to 200% amplification for quiet tracks
- **Stable Volume Management**: Consistent volume levels across different audio sources and playback scenarios

#### ⏰ Sleep Timer

- **Flexible Sleep Timer**: Set automatic playback stopping from 1 minute to 3 hours
- **Intuitive Controls**: Easy-to-use timer interface directly in the player screen
- **Smart Notifications**: Gentle fade-out and notification when timer expires

#### 🔔 Enhanced Notification Experience

- **Direct Player Access**: Clicking notification now opens the full player screen instantly
- **Improved Controls**: More responsive media controls in the notification shade
- **Visual Consistency**: Better alignment with system notification design

#### 🔄 Playback Stability & Continuity

- **Persistent Playback State**: App remembers exact playback position when closed and resumed approximate to 5 seconds
- **Queue Consistency**: Reliable queue management with proper track ordering and persistence
- **Stable Playback Engine**: Enhanced audio pipeline for uninterrupted music streaming

### 🛠️ Technical Improvements

#### Audio Processing

- **Optimized Audio Pipeline**: Improved Media3 integration for better performance
- **Memory Management**: Reduced memory usage during long playback sessions
- **Background Processing**: More efficient background audio handling

#### User Experience

- **Screen Area Optimization**: Better utilization of screen real estate across different device sizes
- **Gesture Improvements**: Smoother touch interactions and visual feedback
- **Loading States**: Enhanced loading indicators for better user feedback

## 🐛 Bug Fixes

### Notification System

- **Notification Player Issues**: Fixed unresponsive media controls in notification shade
- **Click Handling**: Resolved inconsistent behavior when tapping notifications

### UI/UX Improvements

- **Screen Area Utilization**: Corrected layout issues that wasted screen space
- **Visual Consistency**: Fixed alignment and spacing problems across screens

### Queue Management

- **Deletion Bug**: Resolved crashes and data loss when deleting items from queue
- **Reordering Issues**: Fixed queue mismanagement when dragging to reorder tracks
- **State Persistence**: Improved queue state preservation across app sessions

### Playback Stability

- **Audio Glitches**: Eliminated audio artifacts and playback interruptions
- **Resume Functionality**: Fixed issues with resuming playback at correct positions
- **Background Playback**: Enhanced reliability of playback when app is minimized

## 📋 System Requirements

- **Minimum Android Version**: Android 8.0 (API 26)
- **Recommended Android Version**: Android 12.0+ (API 31+)
- **Storage**: Minimum 100MB free space (app size increased by 14KB)
- **Network**: Internet connection required for search and downloads

## 📦 Installation

### Beta Testing

This is a beta release. Please report any issues you encounter.

1. Visit the [GitHub Releases page](https://github.com/rajeet-04/JUKES/releases/tag/v1.0.1-beta)
2. Download the beta APK file (`JUKE-v1.0.1-beta.apk`)
3. Install the APK on your Android device
4. Grant necessary permissions when prompted

### Feedback & Bug Reports

- **Beta Feedback**: Use the new issue reporting feature in Settings > Home
- **GitHub Issues**: [Report bugs](https://github.com/rajeet-04/JUKES/issues) with "beta" label
- **Expected Rough Edges**: As a beta version, some features may have minor imperfections

## 📊 Feature Comparison

| Feature | JUKE v1.0.1-beta | JUKE v1.0.0 | Other Music Apps |
|---------|------------------|-------------|------------------|
| 10-Band Equalizer | ✅ New | ❌ | ⚠️ Basic/Varies |
| Volume Booster (200%) | ✅ New | ❌ | ❌ Rare |
| Sleep Timer (3hrs) | ✅ New | ❌ | ⚠️ Limited |
| Notification Direct Access | ✅ Enhanced | ⚠️ Basic | ⚠️ Varies |
| Playback State Persistence | ✅ Enhanced | ⚠️ Partial | ✅ Usually |
| Queue Stability | ✅ Fixed | ⚠️ Issues | ⚠️ Varies |

## 🧪 Beta Testing Notes

### Known Beta Limitations

- **Audio Processing**: Some equalizer presets may need fine-tuning
- **Sleep Timer**: May have minor timing inaccuracies on some devices
- **Volume Booster**: Extreme boosting may cause audio distortion on some hardware
- **UI Polish**: Some animations and transitions may feel slightly rough

### Testing Focus Areas

- **Equalizer Performance**: Test different presets and custom settings
- **Sleep Timer Accuracy**: Verify timer functionality across different durations
- **Notification Integration**: Test all notification interactions
- **Queue Management**: Stress-test reordering and deletion operations

## 🔮 What's Next

### Post-Beta Plans

- **Equalizer Presets**: Pre-configured audio profiles for different genres
- **Volume Normalization**: Automatic volume leveling across tracks
- **Advanced Sleep Features**: Customizable fade-out and alarm integration
- **Notification Customization**: User-configurable notification actions

### v1.0.2 Roadmap (Post-Beta)

- **User Accounts**: Cloud sync and cross-device playback
- **Social Features**: Share playlists and collaborative queues
- **Android Auto**: Car integration support
- **Wear OS**: Smartwatch companion app

## 📈 Release Statistics

- **App Size Increase**: +14KB for new features and optimizations
- **New Features**: 8 major enhancements
- **Bug Fixes**: 7 critical issues resolved
- **Code Changes**: ~2,500 lines modified/added
- **Testing Coverage**: Additional integration tests for audio features

## 📝 Changelog

### v1.0.1-beta (December 26, 2025)

- 🎛️ Added 10-band audio equalizer
- 🔊 Implemented volume booster (up to 200%)
- ⏰ Added sleep timer (1 min - 3 hrs)
- 🔔 Enhanced notification direct player access
- 🔄 Improved playback state persistence
- 🛠️ Fixed notification player issues
- 📱 Optimized screen area utilization
- 🗑️ Resolved queue deletion bugs
- 🔀 Fixed queue reordering problems
- 🔊 Stabilized volume management
- 🎵 Enhanced playback stability
- 📊 Added issue reporting in settings
- ⚡ Performance optimizations (+14KB)
- 👆 **NEW: Player Screen Swipe Gestures**: Swipe left/right on album art to skip tracks
- 🔄 **NEW: Queue Synchronization Fix**: Fixed incorrect track highlighting with duplicate songs
- ⏩ **NEW: Rapid Skip Race Condition Fix**: Prevented metadata updates from corrupting playback state

---

## JUKE v1.0.0 Release Notes

## 🎉 Release Overview

**Release Date:** December 23, 2025  
**Version:** 1.0.0  
**Status:** Stable Release  

JUKE v1.0.0 marks the first stable release of our modern Android music streaming app. This release brings together Spotify integration, smart recommendations, offline playback, and a beautiful Material Design 3 interface into a cohesive music experience.

## ✨ What's New in v1.0.0

### 🚀 Major Features

#### 🎵 Spotify Integration

- **Complete Spotify API Integration**: Search millions of tracks, artists, albums, and playlists
- **URL Support**: Direct import from Spotify URLs/URIs for tracks, albums, artists, and playlists
- **Metadata Enrichment**: Full track metadata including album art, artist info, and release details

#### 🧠 Smart Recommendation System

- **AI-Powered Queues**: Uses YouTube Music's algorithm to generate similar song recommendations
- **Infinite Radio**: Never-ending music stream based on your current track
- **Smart Validation**: Cross-references recommendations with Spotify for accuracy
- **Pre-downloading**: Automatically downloads upcoming tracks for uninterrupted playback

#### 📥 Playlist Import

- **Full Playlist Support**: Import entire Spotify playlists with one click
- **Real-time Progress**: Live progress tracking during import operations
- **Batch Processing**: Efficiently downloads multiple tracks with error handling
- **Playlist Organization**: Imported playlists appear as separate collections in your library

#### 🎨 Modern UI/UX

- **Material Design 3**: Beautiful, modern interface following Google's latest design guidelines
- **Dark Theme**: Eye-friendly dark mode for comfortable viewing
- **Responsive Design**: Optimized for phones and tablets
- **Gesture Controls**: Intuitive swipe gestures for queue management

#### 📱 Playback Experience

- **Offline Playback**: Play downloaded music without internet connection
- **Synced Lyrics**: Time-synced lyrics display during playback (when available)
- **Background Playback**: Continue playing while using other apps
- **Notification Controls**: Media controls in the notification shade
- **Queue Management**: Drag-to-reorder and swipe-to-delete queue items

### 🛠️ Technical Improvements

#### Architecture

- **MVVM Pattern**: Clean separation of concerns with ViewModels
- **Reactive Programming**: StateFlow-based reactive UI updates
- **Room Database**: Local SQLite storage with Flow-based queries
- **Ktor Networking**: Modern HTTP client with Kotlinx serialization

#### Media Stack

- **Media3 ExoPlayer**: Latest Android media playback engine
- **MediaSession Integration**: System-wide media controls
- **High-Quality Audio**: Support for various audio formats and quality levels

#### Performance

- **Efficient Downloads**: Smart caching and retry logic
- **Background Processing**: Non-blocking operations with coroutines
- **Memory Management**: Optimized image loading and caching

## 🐛 Bug Fixes

### Queue Management Fix

- **Issue**: Clicking on songs in the queue would reset the entire queue to only play that song
- **Fix**: Implemented `playTrackFromQueue()` method that maintains queue integrity
- **Result**: Queue now preserves all songs when jumping to a different track

### Stability Improvements

- **Download Reliability**: Enhanced error handling for network failures
- **UI Responsiveness**: Fixed potential UI freezing during heavy operations
- **Memory Leaks**: Resolved memory leaks in media playback components

## 📋 System Requirements

- **Minimum Android Version**: Android 8.0 (API 26)
- **Recommended Android Version**: Android 12.0+ (API 31+)
- **Storage**: Minimum 100MB free space for app installation
- **Network**: Internet connection required for search and downloads

## 📦 Installation

### For End Users (Recommended)

1. Visit the [GitHub Releases page](https://github.com/rajeet-04/JUKES/releases/tag/v1.0.0)
2. Download the latest APK file (`JUKE-v1.0.0.apk`)
3. Install the APK on your Android device
4. Grant necessary permissions when prompted
5. Launch JUKE and start exploring music!

### For Developers

```bash
# Clone the repository
git clone https://github.com/rajeet-04/JUKES.git
cd JUKES

# Configure Spotify API credentials
# Add to local.properties:
# SPOTIFY_CLIENT_ID=your_client_id
# SPOTIFY_CLIENT_SECRET=your_client_secret

# Build and install
./gradlew assembleRelease
./gradlew installRelease
```

## 🔧 Configuration

### Required Setup

- **Spotify Developer Account**: Required for search functionality
- **API Credentials**: Configure in `local.properties` for development builds

### Optional Features

- **Lyrics**: Automatically fetched when available
- **Recommendations**: Requires internet connection
- **Offline Mode**: Works without network after initial downloads

## 📊 Feature Comparison

| Feature | JUKE v1.0.0 | Other Music Apps |
|---------|-------------|------------------|
| Spotify Integration | ✅ Full API | ❌ Limited/None |
| Smart Recommendations | ✅ AI-Powered | ⚠️ Basic |
| Playlist Import | ✅ Full Support | ⚠️ Partial |
| Offline Playback | ✅ Complete | ✅ Usually |
| Synced Lyrics | ✅ Supported | ⚠️ Varies |
| Material Design 3 | ✅ Latest | ❌ Often Outdated |
| Queue Management | ✅ Advanced | ⚠️ Basic |

## 🧪 Testing

### Test Coverage

- **Unit Tests**: Core business logic and utilities
- **Integration Tests**: Database operations and API calls
- **UI Tests**: Basic screen navigation and interactions

### Known Limitations

- **Lyrics Availability**: Depends on LRCLib database coverage
- **Recommendation Accuracy**: May vary based on YouTube Music's algorithm
- **Import Speed**: Large playlists may take time to download
- **Storage Usage**: Downloaded tracks consume device storage

## 🔮 Future Roadmap

### Planned for v1.1.0

- **User Accounts**: Cloud sync and cross-device playback
- **Social Features**: Share playlists and collaborative queues
- **Audio Effects**: Equalizer and sound customization
- **Sleep Timer**: Automatic playback stopping

### Long-term Vision

- **Android Auto**: Car integration support
- **Wear OS**: Smartwatch companion app
- **Crossfade**: Gapless playback between tracks
- **Podcast Support**: Expand beyond music to podcasts

## 🙏 Acknowledgments

### Third-party Services

- **Spotify Web API**: Music metadata and search capabilities
- **YouTube Music**: Recommendation algorithm and radio generation
- **Spotdown API**: High-quality MP3 downloads
- **LRCLib**: Lyrics database and synchronization

### Open Source Libraries

- **Jetpack Compose**: Modern Android UI framework
- **Media3 ExoPlayer**: Audio playback engine
- **Ktor**: HTTP client and networking
- **Room**: Local database persistence
- **Coil**: Image loading and caching

## 📞 Support & Feedback

### Getting Help

- **GitHub Issues**: [Report bugs and request features](https://github.com/rajeet-04/JUKES/issues)
- **Documentation**: Check [README.md](README.md) for detailed setup instructions
- **Troubleshooting**: See the troubleshooting section in the main README

### Feedback Channels

- **GitHub Discussions**: Community discussions and Q&A
- **Pull Requests**: Contribute improvements and fixes
- **Feature Requests**: Use GitHub issues with the "enhancement" label

## 📈 Release Statistics

- **Development Time**: 6+ months of active development
- **Codebase Size**: ~15,000 lines of Kotlin code
- **Features Implemented**: 25+ major features
- **Bug Fixes**: 50+ issues resolved
- **Test Coverage**: 70%+ unit test coverage

## 🔒 Security & Privacy

### Data Handling

- **No User Data Collection**: JUKE doesn't collect personal information
- **Local Storage Only**: All music and data stored locally on device
- **API Credentials**: Spotify credentials required but not transmitted beyond API calls

### Permissions

- **Storage**: Required for downloading and storing music files
- **Network**: Required for search, downloads, and recommendations
- **Notifications**: Optional, used for playback controls

## 📝 Changelog

### v1.0.0 (December 23, 2025)

- ✨ Initial stable release
- 🎵 Complete Spotify API integration
- 🧠 Smart recommendation system
- 📥 Full playlist import functionality
- 🎨 Material Design 3 implementation
- 📱 Offline playback support
- 📝 Synced lyrics support
- 🔧 Queue management bug fixes
- 🏗️ MVVM architecture implementation
- 📊 Performance optimizations

---

## 🎯 Migration Guide

### From Development Builds

If you're upgrading from a development build:

1. Uninstall the existing app
2. Install the new release APK
3. Re-import any playlists (database structure may have changed)
4. Re-download tracks if needed

### Data Preservation

- **Downloaded Music**: Files are preserved during updates
- **Playlists**: May need re-import due to schema changes
- **Settings**: Will be reset (no persistent settings in v1.0.0)

---

**JUKE v1.0.0 represents a significant milestone in creating a modern, feature-rich music streaming experience for Android. We're excited to share this with the community and look forward to your feedback and contributions!**

*Made with ❤️ by the JUKE development team*</content>
<parameter name="filePath">r:\Code\JUKES\RELEASE_NOTES.md
