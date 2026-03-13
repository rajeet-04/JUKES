# Testing Patterns

**Analysis Date:** 2026-03-08

## Test Framework

**Runner:**
- JUnit 4.13.2 - Unit testing framework
- AndroidJUnit4 - Instrumentation testing framework
- Config: `app/src/test/java/com/example/juke/ExampleUnitTest.kt`
- Config: `app/src/androidTest/java/com/example/juke/ExampleInstrumentedTest.kt`

**Assertion Library:**
- Standard JUnit assertions

**Run Commands:**
```bash
./gradlew test                           # Run all unit tests
./gradlew connectedAndroidTest           # Run instrumentation tests
```

## Test File Organization

**Location:**
- Unit tests: `app/src/test/` (co-located with source code by package)
- Instrumentation tests: `app/src/androidTest/` (mirroring source structure)

**Naming:**
- Class name + "Test": `ExampleUnitTest.kt`, `SpotifyApiTest.kt` (inferred pattern)

**Structure:**
```
app/src/
├── test/                    # Unit tests
│   └── java/
│       └── com/example/juke/   # Mirrors main source structure
└── androidTest/             # Instrumentation tests
    └── java/
        └── com/example/juke/   # Mirrors main source structure
```

## Test Structure

**Suite Organization:**
```kotlin
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}
```

**Patterns:**
- Arrange-Act-Assert pattern
- One test class per production class convention
- Use of standard JUnit annotations (@Test, @Before, @After)

## Mocking

**Framework:** 
- Not explicitly configured (would typically use Mockito or similar)

**Patterns:**
- Not currently implemented in the codebase
- Would likely use Mockito for Android/Kotlin testing

**What to Mock:**
- Network calls for unit tests (when implemented)
- Database operations for isolated business logic testing
- External service dependencies

**What NOT to Mock:**
- Simple data classes and models
- Pure functions without side effects

## Fixtures and Factories

**Test Data:**
```kotlin
// Basic test data setup in existing tests
val appContext = InstrumentationRegistry.getInstrumentation().targetContext
```

**Location:**
- Test data embedded within test methods for simple cases
- Would benefit from dedicated test fixture factories (not yet implemented)

## Coverage

**Requirements:** None enforced currently

**View Coverage:**
```bash
./gradlew jacocoTestReport              # Would generate coverage report (if configured)
```

## Test Types

**Unit Tests:**
- Scope: Individual functions and classes in isolation
- Approach: Currently minimal implementation with placeholder test
- Location: `app/src/test/`

**Integration Tests:**
- Scope: Module interactions and database operations
- Approach: Minimal implementation with placeholder test
- Location: `app/src/androidTest/`

**E2E Tests:**
- Framework: Not currently implemented
- Scope: Full user flows through the application UI
- Approach: Would use Espresso or similar UI testing framework

## Common Patterns

**Async Testing:**
```kotlin
// Would use runTest coroutine test builder when implemented
@Test
fun testAsyncFunction() = runTest {
    // Test suspending functions
}
```

**Error Testing:**
```kotlin
// Would use assertThrows equivalent when implemented
@Test(expected = IllegalArgumentException::class)
fun testException() {
    // Code that should throw
}
```

---

*Testing analysis: 2026-03-08*