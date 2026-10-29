# Contributing to FocusGram

Thank you for your interest in contributing to FocusGram! FocusGram is a free, open-source digital wellness tool built under the **GNU Affero General Public License v3.0 (AGPL-3.0)**.

---

## Architecture Principles

Every pull request must uphold these non-negotiable rules:

1. **Local-Only Operation:**
   FocusGram never sends user data off-device. No telemetry, no analytics, no third-party network SDKs, and no remote crash loggers.
2. **The Login Flow is a Black Box:**
   Never write code or content scripts that read, intercept, or modify authentication form fields, credentials, cookies, or 2FA tokens.
3. **No Automation of Instagram Actions:**
   FocusGram is an intentionality tool, not an automation bot. Do not contribute auto-likers, auto-followers, auto-scrollers, or mass-DM tools.
4. **Clean Web Sandboxing:**
   Web content manipulation is strictly client-side via Mozilla GeckoView content scripts (`app/src/main/assets/scripts/` or `app/src/main/assets/extensions/ublock/`). Never inline large raw JavaScript strings directly in Kotlin source files.
5. **FOSS Dependency Licensing:**
   All dependencies must be compatible with AGPL-3.0 (e.g. Apache 2.0, MIT, BSD, MPL 2.0, LGPL/GPL).

---

## Development Environment Setup

### Prerequisites

- **OS:** Linux, macOS, or Windows
- **JDK:** OpenJDK 21 (Temurin or Adoptium recommended)
- **Android SDK:** API Level 26 minimum, API 35 target, Build-Tools 35.0.0
- **Build System:** Gradle (bundled wrapper `./gradlew`)

### Build Commands

```bash
# Clone the repository
git clone https://github.com/Ujwal223/FocusGram.git
cd FocusGram

# Run unit tests
./gradlew :app:testDebugUnitTest

# Build debug APK
./gradlew :app:assembleDebug

# Build standard release APKs (with ABI splits)
./gradlew :app:assembleRelease

# Build uncloned Instagram package edition (com.instagram.android)
./gradlew :app:assembleRelease -PuseInstaPackage=true

# Or use the interactive build script
./build-release.sh
```

---

## Code Style & Standards

- **Kotlin:** Idiomatic Kotlin 2.0+ following official Kotlin code style.
- **Compose:** Jetpack Compose Material 3 using Unidirectional Data Flow (UDF), `StateFlow`, and coroutines.
- **Storage:** Use Jetpack DataStore Preferences for simple preferences and AndroidX Room for relational persistence (session snapshots, screen time records, reel history).
- **Comments:** Follow maintainer-voice commenting. Explain *why* something is designed the way it is, warn about platform traps, and delete restatements of code. Avoid decorative ASCII art or divider lines.

---

## Submitting Pull Requests

1. Fork the repository and create a branch with a descriptive name (`git checkout -b feature/your-feature`).
2. Run `./gradlew :app:testDebugUnitTest` to ensure all tests pass.
3. Keep diffs minimal, focused, and free of drive-by refactors.
4. Open a Pull Request referencing any relevant issues.
