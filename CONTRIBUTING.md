# Contributing to FocusGram

Thank you for your interest in contributing to FocusGram! FocusGram is a free, open-source digital wellness tool built under the **GNU Affero General Public License v3.0 (AGPL-3.0)**.

---

## Pull Request Scope & Restrictions

Merge requests containing only the following changes will **NOT** be accepted:

1. **README or other supporting docs changes:** Edits, additions, deletions, or typo fixes in documentation, guides, or Markdown files.
2. **Code comments changes:** Additions, edits, deletions, or rephrasing of source code comments.
3. **Test changes:** Adding, editing, or deleting unit, UI, or integration tests without accompanying functional implementation or bug fixes.
4. **License changes:** Edits to license files, terms, or copyright headers.
5. **Cosmetic formatting & whitespace:** Reformatting, linting adjustments, import reordering, or whitespace edits that introduce no functional or behavioral difference.
6. **Trivial refactoring & churn:** Variable renaming, minor syntactic restructuring, or automated/bot-generated PRs without clear performance or correctness justification.
7. **Unsolicited dependency updates:** Version bumps or build dependency updates without prior discussion or security advisory backing.

It is strongly recommended to **open an issue instead** to report typos, documentation gaps, missing test scenarios, or suggestions. Pull requests should focus on substantive bug fixes, core architectural improvements, or approved feature additions.

---

## Architecture Principles

Every pull request must uphold these non-negotiable rules:

1. **Local-Only Operation:**
   FocusGram never sends user data off-device. No user telemetry, no tracking analytics, and no remote crash loggers. Core Instagram traffic is strictly sandboxed. The only permitted external network calls are official GitHub release/notice checks and the isolated, user-initiated rewarded ad WebView.
2. **The Login Flow is a Black Box:**
   Never write code or content scripts that read, intercept, or modify authentication form fields, credentials, cookies, or 2FA tokens.
3. **No Automation of Instagram Actions:**
   FocusGram is an intentionality tool, not an automation bot. Do not contribute auto-likers, auto-followers, auto-scrollers, or mass-DM tools.
4. **Clean Web Sandboxing:**
   Web content manipulation is strictly client-side via Mozilla GeckoView content scripts (`app/src/main/assets/scripts/` or `app/src/main/assets/extensions/content-engine/`). Never inline large raw JavaScript strings directly in Kotlin source files.
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
5. If using an AI coding assistant or autonomous agent, ensure it strictly adheres to the protocol, interrogation requirements, and scope checks defined in **[AGENTS.md](AGENTS.md)**.
