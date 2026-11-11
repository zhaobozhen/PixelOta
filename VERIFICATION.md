# Verification record

This is a dated record, not a guarantee that the same firmware remains the latest release or that all Pixel models have been tested.

## Initial implementation: 2026-10-25

The initial implementation was validated during development as follows:

| Check | Observed result |
| --- | --- |
| `:app:assembleDebug` | Passed; development-signed APK generated |
| `:app:testDebugUnitTest` | Seven tests passed, zero failures |
| `:app:lintDebug` | Passed with zero errors and ten warnings |
| Pixel 6a on Android 17 | Automatic codename/build detection and live matching succeeded; copy action exercised |
| Desktop invocation of the production repository | Exact-build and latest-beta results returned the same expected official URL, with complete beta-source checks |
| HEAD request to the official ZIP | HTTP 200, `application/zip`, 3,030,454,148 bytes |
| Android 17 emulator instrumentation | Empty, partial, populated, and exact clipboard-value checks passed with official HTML samples |
| UI rendering | Regular viewport, 360 x 640 dp, and 320 x 480 dp with 1.3x font scale inspected |
| APK verification | APK Signature Scheme v2 verified |

The regression suite covers beta-dialog and stable-catalog parsing, exact device/build matching including suffixes, invalid hosts and factory filenames, checksum prefixes, date selection, source discovery, QPR3 coverage, and history persistence.

Physical-device testing exposed a discovery gap: a published QPR3 page was absent from older navigation. Known QPR route checks were added, and a regression confirmed that the newer build wins by release date. Scrolling verification also led to a system-bar inset fix.

The ten lint warnings concerned Chinese strings outside resources, newer compile/target SDK and dependency suggestions, and Android backup-rule guidance. No lint baseline was added to hide them.

### Date-specific validation package

The public test case was `bluejay`, Build ID `DP11.261002.005`, published on 2026-10-20:

```text
https://dl.google.com/developers/android/cinnamonbun/images/ota/bluejay_beta-ota-dp11.261002.005-7940ca55.zip
```

Google's published SHA-256 was:

```text
7940ca558ec0fc467f861820f958c40885d8aa3ebbe08ffcbeffa574adf55860
```

The ZIP was not downloaded in full. Its contents, full-file digest, Google package signature, and flashing compatibility were not verified. No operating-system installation or downgrade was performed.

## Public repository preparation

The English README and engineering guide describe the existing implementation. Full-page HTML snapshots were reduced to catalog tables and relevant public links before publication, removing unrelated scripts and site metadata. After reducing the samples, `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebugAndroidTest` passed again. All seven JVM tests passed with zero failures. English documentation passed the punctuation check, relative documentation links were checked, and the staged source was reviewed for local paths and credential patterns. Device and emulator UI runs from the initial record were not repeated for this documentation-and-fixture preparation.

Device screenshots, serial numbers, local SDK paths, signing keys, application-private cache files, APKs, and build outputs are excluded from the source publication.

## Remaining gaps

- Real hardware coverage is limited to a Pixel 6a. Emulator fixture checks do not establish compatibility with every Pixel.
- Discovery is bounded and depends on Google's page and URL conventions.
- The app has a Chinese light-theme UI and no dedicated offline browsing mode.
- Native UI appearance and network reachability vary by device and environment.
- APK signing verification confirms the development APK's signature, not the authenticity of an OTA package.

## Kotlin, Miuix, bilingual, R8, and adaptive icon migration: 2026-11-11

After migrating to Kotlin, Gradle Version Catalog + `.kts` scripts, Compose + Miuix `0.9.4` (light/dark green themes with progressive `progressiveTextureBlur` top bar), centralized `OtaUrls`, bilingual `values/` (English) + `values-zh/` (Chinese) resources, `minSdk 33` / `compileSdk 37` / `targetSdk 37`, R8 release minification + resource shrinking, and an adaptive launcher icon (`ic_launcher` + `ic_launcher_round` + `monochrome`), the final build and runtime checks observed:

| Check | Observed result |
| --- | --- |
| `:app:assembleDebug` and `:app:assembleRelease` | Passed with Gradle `9.9.0`, AGP `9.5.1`, Kotlin `2.4.21`, JDK 25; unsigned R8 release APK size reduced from `23.6 MiB` (earlier unminified build) to `2,681,950` bytes (`2.56 MiB` / `2.68 MB`) |
| `:app:testDebugUnitTest` | Eight tests in `OtaCatalogTest` passed, zero failures, zero skipped |
| `:app:lintDebug` | Passed with zero errors and three warnings (`ObsoleteLintCustomCheck` in upstream `lifecycle-runtime`, `AndroidGradlePluginVersion` noting Gradle `9.9.1`, `DataExtractionRules`) |
| Android 17 emulator `UiCheck` | `INSTRUMENTATION_RESULT: checks=empty, partial, populated, clipboard exact URL: PASS; fixture UI only` (`INSTRUMENTATION_CODE: -1`); English light-mode screenshot visually inspected |
| Physical Pixel 9a on Android 17 (`tegu`, Build ID `CP3A.261005.005`) | Temporarily debug-signed R8 release APK installed (`2.99 MB` reported app storage); live query matched exact installed stable OTA (`tegu-ota-cp3a.261005.005-c83a4af1.zip`) and latest dated Beta (`tegu_beta-ota-dp11.261016.003-5d0fde5f.zip`, released `2026-10-29`); inspected progressive top-bar blur during scroll, `"More"` menu -> Google terms dialog, `"Copy download URL"` Snackbar + clipboard preview, and adaptive launcher icon in App Info |

### Skipped checks and remaining gaps for 2026-11-11

- `LiveCheck` desktop main was not invoked separately because live network lookup was exercised directly on the physical Pixel 9a against official Google pages.
- Gradle Wrapper remains pinned at `9.9.0` (matching Miuix) rather than `9.9.1` due to slow upstream download during setup.
- The release APK was signed only temporarily in `/tmp` with a local debug keystore for physical-device validation of the R8 build; production release signing remains external to the repository.
