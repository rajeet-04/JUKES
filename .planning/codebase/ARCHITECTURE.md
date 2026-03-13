# Architecture

**Analysis Date:** 2026-03-08

## Pattern Overview

**Overall:** MVVM (Model-View-ViewModel) with Repository pattern

**Key Characteristics:**
- Jetpack Compose UI layer with reactive state management
- ViewModel layer managing business logic and state
- Repository pattern through Room database for local persistence
- Network layer using Ktor for REST API communication
- Service layer for background media playback
- Modular architecture with clear separation of concerns

## Layers

**Presentation Layer:**
- Purpose: UI rendering and user interaction handling
- Location: `app/src/main/java/com/example/juke/ui/`
- Contains: Compose components, screens, themes, and UI utilities
- Depends on: ViewModel layer for data
- Used by: Android framework to render UI

**ViewModel Layer:**
- Purpose: Business logic and UI state management
- Location: `app/src/main/java/com/example/juke/viewmodels/`
- Contains: ViewModel classes that expose state via StateFlow
- Depends on: Repository and network layers
- Used by: UI components for reactive state updates

**Repository Layer:**
- Purpose: Data access abstraction for local storage
- Location: `app/src/main/java/com/example/juke/database/`
- Contains: Room DAOs, database entities, and converters
- Depends on: Room persistence library
- Used by: ViewModel layer

**Network Layer:**
- Purpose: API communication with external services
- Location: `app/src/main/java/com/example/juke/network/`
- Contains: API clients using Ktor, service objects for Spotify integration
- Depends on: Ktor HTTP client library
- Used by: ViewModel and service layers

**Domain Model Layer:**
- Purpose: Data models and business objects
- Location: `app/src/main/java/com/example/juke/models/`
- Contains: Data classes representing tracks, artists, albums, etc.
- Depends on: None (pure data structures)
- Used by: All other layers

**Service Layer:**
- Purpose: Background processing and system integration
- Location: `app/src/main/java/com/example/juke/services/`
- Contains: Playback service, music service, queue management
- Depends on: Android Media3 framework, repository layer
- Used by: Android framework for background operations

**Utility Layer:**
- Purpose: Helper functions and common utilities
- Location: `app/src/main/java/com/example/juke/utils/`
- Contains: Helper classes for various operations
- Depends on: Various Android and third-party libraries
- Used by: All layers as needed

## Data Flow

**Music Playback Flow:**

1. User selects a track in UI (`ui/screens/`)
2. UI calls methods in `MusicViewModel` to handle playback
3. ViewModel coordinates with `PlaybackManager` and `QueueManager`
4. `PlaybackService` handles actual media playback using Media3
5. Playback state changes are propagated back through `PlaybackManager`
6. ViewModel updates UI state via StateFlow emissions
7. UI recomposes with new state

**Track Download Flow:**

1. User initiates download via UI interaction
2. ViewModel adds track to download queue
3. Network layer (`SpotifyApi`) fetches track data
4. Service layer handles download and storage
5. Repository layer persists track metadata
6. UI receives updates through StateFlow

**State Management:**
- Reactive: StateFlow/LiveData for observing changes
- Centralized: Single source of truth in ViewModels
- Persistent: Room database for durable storage

## Key Abstractions

**PlaybackManager:**
- Purpose: Central coordinator for playback operations
- Examples: `app/src/main/java/com/example/juke/services/PlaybackManager.kt`
- Pattern: Singleton with coroutine-based state management

**QueueManager:**
- Purpose: Manages playback queue and recommendations
- Examples: `app/src/main/java/com/example/juke/services/QueueManager.kt`
- Pattern: Singleton with algorithmic queue augmentation

**SpotifyApi:**
- Purpose: Interface to Spotify Web API
- Examples: `app/src/main/java/com/example/juke/network/SpotifyApi.kt`
- Pattern: Object-oriented service with OAuth authentication

## Entry Points

**Main Activity:**
- Location: `app/src/main/java/com/example/juke/MainActivity.kt`
- Triggers: App launch, intent handling
- Responsibilities: Root component composition, navigation setup, permission handling

**Playback Service:**
- Location: `app/src/main/java/com/example/juke/services/PlaybackService.kt`
- Triggers: Media session interactions, notification controls
- Responsibilities: Background media playback, lifecycle management

## Error Handling

**Strategy:** Mixed approaches with explicit error types

**Patterns:**
- Sealed classes for operation results
- Extension functions for exception categorization
- Logging with appropriate severity levels
- User-facing error messages with recovery suggestions

## Cross-Cutting Concerns

**Logging:** Android Log with tag-based categorization
**Validation:** Input sanitization in ViewModel layers
**Authentication:** Spotify OAuth2 client credentials flow

---

*Architecture analysis: 2026-03-08*