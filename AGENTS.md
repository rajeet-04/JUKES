# AGENTS.md — AI Agent Quick Guide

Purpose: a short, actionable reference to help AI coding agents become productive in this repository. Prefer links to long docs; do not copy large docs here.

Quick start
- Use the Gradle wrapper from the repository root for builds and tests:
  - `./gradlew assembleDebug`
  - `./gradlew installDebug` (requires device/emulator)
  - `./gradlew test`
  - `./gradlew connectedAndroidTest` (device/emulator)

Key environment
- Java 11, Android SDK (compile/target 36), Kotlin 2.0.21, Gradle 8.13.1.
- Secrets: set `SPOTIFY_CLIENT_ID` and `SPOTIFY_CLIENT_SECRET` in `local.properties`. Never commit secrets.

Where to look first
- App code: `app/src/main/java/com/example/juke/` (UI, ViewModels, services, network, database).
- Documentation: `docs/` (architecture, recommendation system, queue hydration, release notes).
- Agent guidance and conventions: see `.github/copilot-instructions.md` (authoritative agent instructions).

Key docs (short list)
- [.planning/research/CODEBASE_MAP.md](.planning/research/CODEBASE_MAP.md): high-level codebase map and quick orientation.
- [.planning/codebase/ARCHITECTURE.md](.planning/codebase/ARCHITECTURE.md): architecture patterns and data flows.
- [.planning/codebase/CONVENTIONS.md](.planning/codebase/CONVENTIONS.md): coding, naming, and style conventions.
- [.planning/codebase/STRUCTURE.md](.planning/codebase/STRUCTURE.md): directory layout and where to add features.
- [.planning/codebase/STACK.md](.planning/codebase/STACK.md): runtime, libraries, and build tool versions.
- [.planning/codebase/TESTING.md](.planning/codebase/TESTING.md): test commands and guidance.
- [.planning/codebase/CONCERNS.md](.planning/codebase/CONCERNS.md): tech debt, fragile areas, and known bugs.
- [.planning/debug/queue-cache-bugs.md](.planning/debug/queue-cache-bugs.md): recent debug notes.

Conventions (high level)
- Kotlin style: follow the project `gradle.properties` and Kotlin official conventions.
- UI: Jetpack Compose; follow Compose conventions for `@Composable` naming and state handling.
- State: ViewModels expose `StateFlow`; DAOs return `Flow<T>`.

Do / Don't
- Do run the Gradle wrapper for builds and tests.
- Do link to docs rather than embedding long sections.
- Don't commit secrets or edit release notes/changelogs without explicit approval.

Recommended short-lived agent tasks
- `test-runner`: runs `./gradlew test`, reports failures and stack traces.
- `build-smoke`: runs `./gradlew assembleDebug` and basic sanity checks.
- `pr-assistant`: fetches PR diff, suggests minimal fixes, and drafts responses to reviewer comments.

Extending agent support
- If you want project-specific skills or automation, propose adding small skills under `.copilot/skills/` or `.agents/skills/` that run tests, validate styles, or assist with PRs.

Recommended reading order
- Quick orientation: [.planning/research/CODEBASE_MAP.md](.planning/research/CODEBASE_MAP.md)
- Architecture & flows: [.planning/codebase/ARCHITECTURE.md](.planning/codebase/ARCHITECTURE.md)
- Conventions & structure: [.planning/codebase/CONVENTIONS.md](.planning/codebase/CONVENTIONS.md) and [.planning/codebase/STRUCTURE.md](.planning/codebase/STRUCTURE.md)
- Tests & known issues: [.planning/codebase/TESTING.md](.planning/codebase/TESTING.md), [.planning/codebase/CONCERNS.md](.planning/codebase/CONCERNS.md)

Last updated: 2026-04-16
