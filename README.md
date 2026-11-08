# FOD Icon & Animation

An Android **LSPosed module** for trying custom fingerprint icons and Lottie animations in `com.android.systemui`. This is an ordinary installable APK, **not** a Magisk module and **not** a replacement SystemUI APK.

The app lets you choose your own PNG and JSON files on the phone. It does **not** contain your patch animations or your original SystemUI files. With no files selected, SystemUI keeps its original resources. The repository includes a separately selectable, handmade [`iron_man_fire_test.json`](examples/iron_man_fire_test.json) example; it is a test animation, **not** your patch or a bundled override.

> **Important:** Your LSPosed log showed `Applied UDFPS_AOD`, and you confirmed the AOD replacement appeared. That does **not** prove an unlock-success effect will work. The new success effect uses a separate, experimental SystemUI hook that has not yet been tested on your phone. The two names you shared beginning `biometricprompt_sfps` generally refer to a **side fingerprint sensor**, not necessarily under-display FOD.

## Build on Ubuntu

Requirements: JDK 17, Android SDK Platform 35 and build tools, accepted Android SDK licenses, and an internet connection for Gradle/Android dependencies. Set `ANDROID_HOME` to your SDK directory or configure `sdk.dir` in `local.properties` (Android Studio does this automatically). The included Gradle wrapper downloads Gradle 8.9; you do not need a separate Gradle installation.

From this repository directory, one command builds the APK:

```bash
./gradlew assembleDebug
```

The output is `app/build/outputs/apk/debug/app-debug.apk`. The APK is debug-signed by Gradle; it is the **module APK**, not a re-signed SystemUI APK. Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, or copy the APK to your phone and open it. Debug builds from different machines or CI runs can have different signing keys; if Android refuses an update because the signatures differ, uninstall the old app first (which deletes its selected files), install the new one, and re-enable it in LSPosed.

Alternatively, after this project is pushed to GitHub, the **Build Android module** Actions workflow runs tests, builds the debug APK, and uploads the `FOD-Icon-Animation-debug` artifact. Download it from that workflow run's **Artifacts** section. No APK is committed to this repository.

## Enable and use

1. You need a rooted phone with an LSPosed version compatible with its Android version. Have a recovery route for disabling LSPosed/Magisk **before** changing SystemUI; an incompatible resource can make SystemUI unstable.
2. Install and open **FOD Icon & Animation**. To try a brief effect **after a successful fingerprint unlock**, choose a self-contained JSON under **Fingerprint unlock success effect**. The sample above can be copied to your phone, or you can import your own Lottie JSON. Each selection is copied into private app storage; the original file stays untouched.
3. For success-only behavior, tap **Clear** under **Always-on-display FOD animation** if you previously selected an AOD animation. The AOD and unlock-success slots are independent. Other slots are optional; icons must be PNG, not Android vector XML.
4. In LSPosed Manager, enable the module and check **System UI** (`com.android.systemui`) in its scope. The module recommends this scope and will not hook other packages.
5. Reboot after enabling the module **and after changing or clearing files**. Unlock with your fingerprint to test; no effect is intended during idle AOD or a failed scan. Loading the animation can take time after reboot, so try a second unlock if the first has no effect.
6. To revert, tap **Clear** for the relevant slot and reboot, or disable the module in LSPosed and reboot. If SystemUI prevents normal access, use your existing recovery method to disable LSPosed/Magisk.

Do **not** replace or debug-sign the system's `SystemUI.apk`; the earlier instructions to do that were unsafe and are not needed for this module.

## Resource mapping

The following **resource replacements** match exactly and are limited to resources whose package is `com.android.systemui`. Android resource entry names omit the `.json` extension. Each slot is optional; a missing or unreadable selection leaves the stock resource unchanged. The **Fingerprint unlock success effect** is a separate event-driven slot, not another raw-resource mapping; see the limitations below.

| SystemUI resource | App slot | Replacement |
| --- | --- | --- |
| `drawable/ic_fingerprint` | Fingerprint icon | PNG |
| `drawable/fingerprint_bg` | Fingerprint background | PNG |
| `raw/udfps_lockscreen_fp` | Lock-screen FOD animation | Lottie JSON |
| `raw/udfps_aod_fp` | Always-on-display FOD animation | Lottie JSON |
| `raw/biometricprompt_sfps_fingerprint_authenticating` | Fingerprint prompt / SFPS animation | Lottie JSON |
| `raw/biometricprompt_sfps_rear_display_fingerprint_authenticating` | Rear-display side-sensor prompt | Lottie JSON |
| `raw/fingerprint_authenticating` | Fingerprint prompt / SFPS animation | Lottie JSON |
| `raw/fingerprint_to_success` | Success animation | Lottie JSON |
| `raw/fingerprint_to_error` | Error animation | Lottie JSON |

The two SFPS names came from the files you showed. The UDFPS names are AOSP candidates: [AOSP's SystemUI layout](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/packages/SystemUI/res/layout/udfps_keyguard_view_internal.xml) uses them for the lock-screen and always-on fingerprint views. Your log confirms that the AOD resource was intercepted; the other candidates and their actual use on your ROM remain unconfirmed. The slots are independent; selecting the original SystemUI JSON merely supplies the original animation again, so choose your **custom** animation to change its appearance.

## Find the actual FOD resources

With this repo on Ubuntu, scan your decoded SystemUI source in one command:

```bash
python3 tools/inspect_systemui.py "$HOME/SystemUI_src"
```

The read-only script groups filenames into **possible under-display/FOD**, **side fingerprint (SFPS)**, and **other fingerprint** candidates. It checks matching JSON files for basic Lottie metadata, but filenames alone cannot prove what the device actually uses. It never changes or uploads your files. Use `--max-per-group 0` to show every match. Share the output if you want help identifying additional resources to hook.

After installing this version of the module, reboot, use the fingerprint sensor, and open the LSPosed logs. Lines beginning `[FOD Icon & Animation] Observed` show fingerprint-related resources that passed through the hooks; `(not mapped)` means the module doesn't currently replace that resource. Logging is capped at 100 distinct resource names per SystemUI process; a missing name does **not** prove it is unused.

For the new success effect, look for `Unlock success hook installed`, `Unlock effect JSON ready`, `Fingerprint unlock success event observed`, and `Fingerprint unlock success effect started`. These messages identify which stage ran, but do not prove the effect was visible. `Unlock success hook unavailable` or a skipped effect means this SystemUI path was not usable. Only status and resource names are logged, not fingerprint images, identifiers, JSON contents or touch coordinates.

## Validate an animation

Choose the **complete original `.json` file**, not text copied from an expanded browser/file-manager preview. The pasted text with missing punctuation and `[…]` is an incomplete inspector view, so it cannot establish that your file is valid JSON. Earlier replies saying that text was definitely valid were incorrect.

The same read-only script can inspect just the file you named:

```bash
python3 tools/inspect_systemui.py "$HOME/SystemUI_src/res/raw/biometricprompt_sfps_fingerprint_authenticating.json"
```

It and the module check the entire JSON for syntax and basic Lottie metadata (`v`, `fr`, `ip`, `op`, `w`, `h`, `layers`); they report the size, frame rate and duration. These checks do **not** prove that an animation will render. A syntactically valid file can still be unsupported by the Lottie version in your SystemUI, have no visible layers, or need external images/fonts. The app copies only a single JSON file, so use a self-contained animation and test it on the device.

Imports are limited to 4 MiB; PNGs must be at most 2048 × 2048 and Lottie canvas dimensions at most 8192 × 8192. Invalid imports leave the previous selection in place.

## How it works and limitations

The legacy Xposed entry point hooks selected `Resources` drawable/raw loading methods **inside SystemUI only**. It checks the package, resource type and exact entry name, then reads the corresponding imported file through a read-only provider. Files are kept in device-protected private app storage so they are available on the lock screen before unlock. The provider checks the calling UID and only permits the module itself and SystemUI's UID to read; no storage or network permission is required.

The optional **Fingerprint unlock success effect** instead observes the AOSP-style `AuthRippleController` fingerprint-success ripple path (method shapes seen in Android 13–16). It parses the selected JSON in the background using SystemUI's own Lottie library, then briefly adds a non-interactive view to an existing SystemUI view overlay near the reported UDFPS location. It does not add a system window, intercept touch, suppress the stock ripple, invoke fingerprint APIs, or change sensor illumination. Display is capped at 2.5 seconds and is removed on timeout or root-view detach. Face and failed-fingerprint events are not deliberately targeted. Your ROM may lack this path, report no UDFPS location, hide the view during unlock, or use an incompatible Lottie implementation; this feature has not yet been tested on a phone.

All hooks are best-effort, not a universal UDFPS implementation. Resource hooks may not run when a ROM uses different names/packages, a different process or code path, an asset/ZIP, native code, `openRawResourceFd`, or a different drawable implementation. Some AOSP UDFPS icons are drawn from path data instead of `drawable/ic_fingerprint`, so the PNG icon slot will not necessarily affect them. SystemUI retains control of placement, tint, animation caching and fingerprint sensor behavior. An incompatible JSON or hook can still destabilize SystemUI: keep recovery available.

Because overrides are stored in device-protected storage to work before unlock, do not import sensitive personal images or other private data as overrides.

## Tests

```bash
./gradlew testDebugUnitTest
```

Run the read-only inspector tests separately with `python3 -m unittest discover -s tests -v`. The tests cover exact resource mappings, diagnostic name selection, JSON validation, bounded reads, cache fallback, unlock source tracking and effect placement bounds. Building and these tests do not replace a real-device LSPosed/SystemUI test.

The compile-only Xposed API dependency is documented in [`app/libs/NOTICE.txt`](app/libs/NOTICE.txt); the framework supplies its implementation at runtime.
