# Keyboard and Focus Helper Test

Android test application and CI harness for the exact source of [KeyboardAndFocusHelper.kt](https://gist.github.com/rodrigosambadesaa/fcf03abc572f23286d13755344595de7).

## Exact Gist source under test

`app/src/main/java/KeyboardAndFocusHelper.kt` is the Gist source verbatim, pinned to revision:

`e4d47500bd1658d3134b39a4c10e4072984da580`

The Gist currently has a single revision. The test harness, Activity, Gradle configuration, and tests may change, but the source under test is not adapted, repackaged, or patched before compilation.

GitHub Actions downloads the pinned RAW file again and compares it byte-for-byte with the repository copy before building. `.github/workflows/sync-gist.yml` provides the reproducible synchronization path.

## Corrected implementation

`app/src/main/java/dev/rodrigosambade/keyboardtest/fixed/FixedKeyboardAndFocusHelper.kt` is a separate corrected implementation. It does **not** replace or modify the exact Gist source.

The test work identified several robustness issues worth correcting separately:

- **IME show timing on Android 11/API 30+:** a request can be issued after attachment but before the Activity window has focus. The corrected implementation waits briefly for attachment + window focus, then uses `WindowCompat.getInsetsController(window, view)` and also performs an `InputMethodManager` best-effort request.
- **Deprecated controller path:** the exact Gist uses `ViewCompat.getWindowInsetsController(view)`; the corrected implementation binds the controller explicitly to the owning Window and editor View.
- **Legacy keyboard geometry:** the exact Gist mixes a local view height with a screen-coordinate visible-frame bottom. The corrected implementation converts the root bottom to screen coordinates with `getLocationOnScreen()` before calculating the obscured region.
- **Background subscription removal:** the exact Gist does not set `removed=true` until its main-thread cleanup runs. The corrected implementation invalidates the subscription synchronously, preventing a queued install/dispatch from reviving it.
- **Floating IMEs:** a visible floating/undocked IME may legitimately contribute a zero bottom inset. The corrected implementation treats `visible=true, height=0` as valid instead of forcing an artificial positive height.

The API 30 IME symptom is **timing-sensitive rather than deterministic**. Earlier CI runs reproduced a failure to make the IME visible with the exact Gist; the final unified KVM run did not reproduce it. The corrected implementation is therefore maintained as a robustness fix, not as evidence that the exact Gist must fail on every Android 11 run.

## Tests

- `KeyboardAndFocusHelperTest`: Robolectric contracts against the exact Gist.
- `FixedKeyboardAndFocusHelperTest`: regression for immediate off-main-thread subscription removal.
- `KeyboardDeviceTest`: general device contracts against the exact Gist.
- `OriginalGistImeRegressionTest`: real IME show/measure/hide against the exact Gist.
- `FixedKeyboardDeviceTest`: real IME show/measure/hide against the corrected implementation.
- Robolectric coverage: API 19, 28, 30, and 34.
- Emulator coverage: API 23, 28, 30, and 35.

## Validated CI result

Workflow run [37712774546](https://github.com/rodrigosambadesaa/keyboard-and-focus-helper-test/actions/runs/37712774546) completed successfully.

- Exact pinned Gist byte comparison: **PASS**
- Build + debug APK + instrumentation APK: **PASS**
- Unit tests: **21/21 PASS**
- API 23 instrumentation: **PASS**
- API 28 instrumentation: **PASS**
- API 30 instrumentation: **PASS**
- Exact Gist API 30 IME regression test in the final run: **PASS** (the earlier failure is timing-sensitive and did not reproduce)
- API 35 instrumentation: **PASS**
- Artifact: `android-debug-apk-and-tests`

The emulator matrix runs sequentially on one Ubuntu KVM host to avoid host-to-host KVM variability.

## Build

Requires Java 17, Android SDK 35, and Gradle 8.9 installed locally.

```sh
gradle :app:assembleDebug :app:testDebugUnitTest
gradle :app:connectedDebugAndroidTest
```

CI uses hosted runners. Real OEM-device and IME checks such as Samsung One UI, Xiaomi/HyperOS, Huawei/EMUI, ColorOS, physical keyboards, gesture navigation, split screen, floating keyboards, and API 16 hardware still require separate device validation.

A successful automated suite does not prove that every physical IME or OEM behaves identically.
