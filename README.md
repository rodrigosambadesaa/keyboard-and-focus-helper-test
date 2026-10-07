# Keyboard and Focus Helper Test

Android test application and CI harness for the exact source of [KeyboardAndFocusHelper.kt](https://gist.github.com/rodrigosambadesaa/fcf03abc572f23286d13755344595de7).

## Exact Gist source under test

`app/src/main/java/KeyboardAndFocusHelper.kt` is downloaded verbatim from the pinned Gist revision:

`e4d47500bd1658d3134b39a4c10e4072984da580`

The test harness, Activity, Gradle configuration, and tests may change. The Gist source itself is not adapted, repackaged, or patched before compilation.

GitHub Actions downloads that RAW revision again and compares it byte-for-byte with the repository copy before running the build. `.github/workflows/sync-gist.yml` is the reproducible synchronization path.

## Corrected implementation

`app/src/main/java/dev/rodrigosambade/keyboardtest/fixed/FixedKeyboardAndFocusHelper.kt` is a separate corrected implementation. It does **not** replace or modify the exact Gist source.

The regression found on Android 11 / API 30 concerns the IME-show path that obtains a controller through `ViewCompat.getWindowInsetsController(view)`. AndroidX documents an API-30-specific controller-construction regression and recommends using the Window + View based controller path. The corrected implementation therefore resolves the owning Activity and uses `WindowInsetsControllerCompat(window, view)`, falling back to `InputMethodManager` when no Activity-backed window can be resolved.

## Test layout

- `KeyboardDeviceTest`: general contracts against the exact Gist.
- `OriginalGistImeRegressionTest`: real IME show/measure/hide test against the exact Gist.
- `FixedKeyboardDeviceTest`: the same real IME regression against the corrected implementation.
- Robolectric coverage: API 19, 28, 30, and 34.
- Device/emulator coverage: API 23, 28, 30, and 35.

On API 30 the exact-Gist IME regression is recorded separately so it can reproduce the known bug without masking failures in the corrected implementation. The corrected implementation remains a required passing test.

## Build

Requires Java 17, Android SDK 35, and Gradle 8.9 installed locally.

```sh
gradle :app:assembleDebug :app:testDebugUnitTest
gradle :app:connectedDebugAndroidTest
```

CI uses hosted runners. Real OEM-device and IME checks such as Samsung One UI, Xiaomi/HyperOS, Huawei/EMUI, ColorOS, physical keyboards, gesture navigation, split screen, floating keyboards, and API 16 hardware still require separate device validation.

A successful automated suite does not prove that every physical IME or OEM behaves identically.
