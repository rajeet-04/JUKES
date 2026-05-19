# Testing Patterns

**Analysis Date:** 2026-05-19

## Test Framework

**Runner:**
- JUnit 4 (unit tests)
- AndroidJUnitRunner (instrumented tests)
- Compose UI Test (Compose tests)

**Assertion Library:**
- JUnit assertions (`org.junit.Assert.assertEquals`)
- Truth assertions (`com.google.common.truth.Truth.assertThat`)
- Compose test assertions

**Run Commands:**
```bash
# Run all unit tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.example.juke.ExampleUnitTest"

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Generate coverage report
./gradlew testDebugUnitTestCoverage

# Run Compose tests
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.juke.ui.test.HomeScreenTest
```

## Test File Organization

**Location:**
- Unit tests: `app/src/test/java/com/example/juke/`
- Instrumented tests: `app/src/androidTest/java/com/example/juke/`
- Compose UI tests: `app/src/androidTest/java/com/example/juke/ui/test/`

**Naming:**
- Unit tests: `*Test.kt`
- Instrumented tests: `*Test.kt`
- Compose tests: `*Test.kt` (typically in ui/test package)

**Structure:**
```
app/
└── src/
    ├── test/
    │   └── java/
    │       └── com/example/juke/
    │           ├── ExampleUnitTest.kt
    │           └── utils/
    │               └── TestHelpers.kt
    └── androidTest/
        └── java/
            └── com/example/juke/
                ├── database/
                │   └── MusicDatabaseTest.kt
                ├── ui/
                │   └── test/
                │       ├── HomeScreenTest.kt
                │       └── PlayerScreenTest.kt
                └── services/
                    └── MusicServiceTest.kt
```

## Test Structure

**Suite Organization:**
```kotlin
package com.example.juke

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MusicViewModelTest {

    private lateinit var viewModel: MusicViewModel
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        viewModel = MusicViewModel(context)
    }

    @Test
    fun `should initialize with empty state`() {
        assertThat(viewModel.uiState.value.currentTrack).isNull()
        assertThat(viewModel.uiState.value.queue).isEmpty()
    }
}
```

**Compose Test Structure:**
```kotlin
@ExperimentalCoroutinesApi
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreen_displaysAppName() {
        composeTestRule.setContent {
            HomeScreen(
                musicViewModel = mockMusicViewModel(),
                onSettingsClick = {},
                onSeeAllClick = {},
                bottomPadding = 0.dp
            )
        }

        composeTestRule.onNodeWithText("Juke").assertIsDisplayed()
    }
}
```

## Mocking

**Framework:**
- MockK for Kotlin mocking (preferred over Mockito)
- Mockito for Android framework mocking when needed
- turbomock for final class mocking

**Patterns:**
```kotlin
// MockK example
val mockApi = mockk<SpotifyApi>(relaxed = true)
every { mockApi.fetchTrack(any()) } returns Result.success(track)

// Mockito example
val mockContext = mock(Context.class)
when(mockContext.getString(R.string.app_name)).thenReturn("Juke")
```

**What to Mock:**
- Network responses (API calls)
- Database operations (DAOs, repositories)
- File system access
- Android framework classes (Context, SharedPreferences)
- Third-party SDKs (Spotify, Coil, etc.)

**What NOT to Mock:**
- Simple utility functions
- Data classes
- Extension functions (unless complex)
- Value classes

## Fixtures and Factories

**Test Data:**
- Factory functions for complex objects
- Test data builders for fluent object creation
- Resource files for JSON fixtures
- Kopytiam for snapshot testing (when adopted)

**Location:**
- Inline in test files for simple cases
- `app/src/test/java/com/example/juke/testutils/` for shared fixtures
- `app/src/test/resources/` for JSON/XML fixtures

**Example Factory:**
```kotlin
fun createTestTrack(
    id: String = UUID.randomUUID().toString(),
    title: String = "Test Track",
    artist: String = "Test Artist",
    durationSec: Int = 180
): Track = Track(
    id = id,
    title = title,
    artist = artist,
    durationSec = durationSec,
    localUri = null
)
```

## Coverage

**Requirements:** None enforced

**View Coverage:**
```bash
./gradlew testDebugUnitTestCoverage
```

**Current Coverage Status:**
- Unit tests: ~5% (limited to basic examples)
- Instrumented tests: 0%
- Line coverage: Minimal
- Branch coverage: Minimal

## Test Types

**Unit Tests:**
- Scope: Pure functions, utility classes, ViewModels (with mocked dependencies)
- Location: `app/src/test/`
- Current: Minimal (placeholder tests)
- Focus: Business logic, state transformations, error handling

**Integration Tests:**
- Scope: Database operations, API parsing, service interactions
- Location: `app/src/androidTest/`
- Current: None
- Recommended: Room database tests, API client tests with MockWebServer

**UI Tests:**
- Scope: Compose component behavior, screen navigation, user interactions
- Location: `app/src/androidTest/java/com/example/juke/ui/test/`
- Framework: Compose UI Test
- Current: None
- Recommended: Screen composables, complex components, state interactions

**Property-Based Testing:**
- Not currently used
- Could use kotlintest or jqwik for property-based testing of algorithms

## Common Patterns

**Async Testing:**
```kotlin
// Coroutine testing
@get:Rule
val instantTaskExecutorRule = InstantTaskExecutorRule()

@Test
fun `should load track asynchronously`() = runTest {
    // Given
    coEvery { mockApi.fetchTrack("test-id") } returns track

    // When
    viewModel.loadTrack("test-id")

    // Then
    assertThat(viewModel.uiState.value.currentTrack).isEqualTo(track)
}
```

**Error Testing:**
```kotlin
@Test(expected = IllegalArgumentException::class)
fun `should throw on invalid input`() {
    // Test code that should throw
    viewModel.processInvalidInput()
}

// Alternative with Truth
@Test
fun `should throw on invalid input`() {
    assertThatIllegalArgumentException()
        .isThrownBy { viewModel.processInvalidInput() }
        .withMessageContaining("invalid")
}
```

**Database Testing:**
```kotlin
@get:Rule
val instantExecutorRule = InstantTaskExecutorRule()

@Test
fun `should insert and retrieve track`() = runTest {
    // Given
    val track = createTestTrack()

    // When
    musicDao.insertTrack(track.toEntity())
    val result = musicDao.getTrackByUuid(track.id)

    // Then
    assertThat(result).isNotNull()
    assertThat(result?.title).isEqualTo(track.title)
}
```

## Development Practices

**Test-Driven Development:**
- Not currently practiced
- Tests written after feature implementation
- Opportunity to adopt for complex logic

**Continuous Testing:**
- Tests run on PR via GitHub Actions (when configured)
- Local development: manual test execution
- No pre-commit hooks for test execution

**Test Maintenance:**
- Tests updated when breaking changes made
- No systematic test review process
- Flaky tests addressed as discovered

**Recommendations:**
1. Adopt MockK as standard mocking framework
2. Create test utilities for common objects (Track, ViewModel states)
3. Implement Repository pattern for easier API mocking
4. Add Compose UI tests for critical user flows
5. Establish minimum coverage thresholds for new code
6. Use turbomock for mocking final classes when needed
7. Implement snapshot testing for UI components
8. Add contract tests for API boundaries