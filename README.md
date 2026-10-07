# Keyboard and Focus Helper Test

Android test application and CI harness for [KeyboardAndFocusHelper.kt](https://gist.github.com/rodrigosambadesaa/fcf03abc572f23286d13755344595de7).

## Included
- Interactive Android screen: show/hide keyboard, focus and cursor, adjustResize/adjustPan, and current IME state.
- Robolectric tests configured for API 19, 28, 30, and 35.
- Device instrumentation tests configured for API 23, 28, 30, and 35 via GitHub Actions.
- An adapted copy of the Gist, including improvements for legacy geometry and lifecycle cancellation.

## Build

Requires Java 17, Android SDK 35, and Gradle 8.9 installed locally. Run:

```sh
gradle :app:assembleDebug :app:testDebugUnitTest
gradle :app:connectedDebugAndroidTest
```

CI uses hosted runners. Real OEM-device and IME checks (rotation, gesture navigation, split screen, physical keyboards, API 16, adjustPan, and OEM keyboards) still require separate validation.

**Source of truth:** The original Gist is not modified by commits here; corrected behavior is maintained in this repository. A successful build or automated unit test does not prove that every physical IME behaves identically.
