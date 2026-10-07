# Keyboard and Focus Helper Test

Android test application and CI harness for the exact source of [KeyboardAndFocusHelper.kt](https://gist.github.com/rodrigosambadesaa/fcf03abc572f23286d13755344595de7).

## Source under test

`app/src/main/java/KeyboardAndFocusHelper.kt` is downloaded verbatim from the pinned Gist revision
`e4d47500bd1658d3134b39a4c10e4072984da580`.

The test harness, Activity, Gradle configuration, and tests may change. The Gist source itself is not adapted, repackaged, or patched before compilation.

`.github/workflows/sync-gist.yml` is the reproducible synchronization path and downloads the RAW Gist directly from `gist.githubusercontent.com`.

## Included

- Interactive Android screen: show/hide keyboard, focus and cursor, adjustResize/adjustPan, and current IME state.
- Robolectric tests on API 19, 28, 30, and 34.
- Device instrumentation tests on API 23, 28, 30, and 35 through GitHub Actions.
- AndroidX Core 1.12.0 so the app can retain the Gist's API 16 minimum.

## Build

Requires Java 17, Android SDK 35, and Gradle 8.9 installed locally.

```sh
gradle :app:assembleDebug :app:testDebugUnitTest
gradle :app:connectedDebugAndroidTest
```

CI uses hosted runners. Real OEM-device and IME checks such as MIUI/One UI/EMUI/ColorOS, physical keyboards, gesture navigation, split screen, and API 16 hardware still require separate device validation.

A successful build or automated test suite does not prove that every physical IME behaves identically.
