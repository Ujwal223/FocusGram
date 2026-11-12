<!--
Before submitting, please review AGENTS.md and CONTRIBUTING.md.
Pull requests that contain ONLY documentation, code comments, test-only changes,
cosmetic formatting, or unsolicited dependency updates will be closed immediately.
-->

## Associated Issue
Closes #

## Summary of Changes
<!-- Provide a concise description of what was changed, why, and the technical approach. -->

## Architectural Layer
<!-- Check all layers touched by this pull request: -->
- [ ] `core.engine` (GeckoView runtime, session management, WebExtension bridge)
- [ ] `core.datastore` / `core.database` (Local state, Room SQLite, DataStore)
- [ ] `feature.*` (Settings, Session, Guardrails, Notifications, Lock)
- [ ] `assets/scripts/` or `assets/extensions/content-engine/` (Web scripts)
- [ ] Build configuration or CI/CD workflows

## Mandatory Confirmations
<!-- Every item below is required before a pull request can be reviewed. -->
- [ ] I am a human contributor, I understand the code in this PR, and I am not blindly submitting output from an AI agent.
- [ ] I have verified that Instagram language is set to English during all testing.
- [ ] This PR is NOT documentation-only, comment-only, test-only, cosmetic formatting, or an unsolicited dependency update.
- [ ] All web modifications reside in `assets/scripts/` or `assets/extensions/content-engine/` (zero inline JavaScript strings in Kotlin files).
- [ ] This change does not touch, inspect, or intercept Instagram login flows, credentials, 2FA tokens, or cookies.
- [ ] This change transmits zero telemetry, analytics, or user data off-device.
- [ ] This change does not automate user actions (no auto-liking, auto-scrolling, or bots).
- [ ] Any newly introduced feature is independently toggleable from Settings.

## Testing & Verification
<!-- Describe the testing performed to ensure no regressions. -->
- [ ] Local unit test suite passed: `./gradlew :app:testDebugUnitTest`
- [ ] Debug APK assembled successfully: `./gradlew :app:assembleDebug`
- [ ] Tested on a physical device or emulator (Device: , Android Version: )
