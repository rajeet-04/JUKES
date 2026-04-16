# Testing Patterns

**Analysis Date:** 2026-04-16

## Test Framework

**Runner:**
- JUnit 4 (unit tests)
- AndroidJUnitRunner (instrumented tests)
- Compose UI Test (Compose tests)

**Assertion Library:**
- JUnit assertions (`org.junit.Assert.assertEquals`)
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
```

## Test File Organization

**Location:**
- Unit tests: `app/src/test/java/com/example/juke/`
- Instrumented tests: `app/src/androidTest/java/com/example/juke/`

**Naming:**
- Unit tests: `*Test.kt`
- Instrumented tests: `*Test.kt`

**Structure:**
- Follows standard JUnit 4 structure
- `@Test` annotated methods

## Test Structure

**Suite Organization:**
```kotlin
package com.example.juke

import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}
```

## Mocking

**Framework:** Not explicitly used

**Patterns:** Would use MockK for Kotlin mocking if tests were expanded

**What to Mock:**
- Network responses
- Database operations
- File system access

**What NOT to Mock:**
- Simple utility functions
- Data classes

## Fixtures and Factories

**Test Data:**
- Minimal fixture usage currently
- Would use factory functions or builders for complex objects

**Location:**
- Inline in test files (currently)

## Coverage

**Requirements:** None enforced

**View Coverage:**
```bash
./gradlew testDebugUnitTestCoverage
```

## Test Types

**Unit Tests:**
- Scope: Pure functions, utility classes
- Location: `app/src/test/`
- Current: Minimal (one example test)

**Integration Tests:**
- Scope: Database operations, API parsing
- Location: Would be in `app/src/androidTest/`
- Current: None

**E2E Tests:**
- Framework: Not used
- Alternative: Manual testing via debug APK

## Common Patterns

**Async Testing:**
```kotlin
// Current pattern: minimal async tests
// Would use runBlocking for suspend functions
runBlocking {
    val result = someSuspendFunction()
    assertEquals(expected, result)
}
```

**Error Testing:**
```kotlin
@Test(expected = SomeException::class)
fun shouldThrowException() {
    // Test code that should throw
}
```

## Current Test Status

**Summary:**
- Unit tests: 1 example test (placeholder)
- Instrumented tests: 0
- Coverage: Not measured

**Recommended Tests to Add:**

| Component | Test Type | Coverage |
|-----------|-----------|----------|
| `MusicService` | Unit | Download retry, LRU eviction |
| `QueueManager` | Unit | Scoring algorithm, blacklist filtering |
| `SpotifyApi` | Unit (Mock) | Token refresh, response parsing |
| `TrackDao` | Instrumented | CRUD operations |
| `ArtistUtils` | Unit | Matching algorithms |
| UI Screens | Compose UI test | Navigation, state |

---

*Testing analysis: 2026-04-16*
