# Google OTA page fixtures

These files are reduced extracts of unauthenticated public Google HTML pages captured on 2026-10-25. They are offline parser regression inputs, not a current firmware database.

| File | Source |
| --- | --- |
| `versions.html` | https://developer.android.com/about/versions?hl=en |
| `stable.html` | https://developers.google.com/android/ota?hl=en, after acknowledging the download terms |
| `beta.html` | https://developer.android.com/about/versions/17/qpr2/download-ota?hl=en |
| `qpr3.html` | https://developer.android.com/about/versions/17/qpr3/download-ota?hl=en |

The extracts retain tables and supported Android-version links, plus OTA ZIP anchors needed to exercise download-dialog parsing. Unrelated scripts, styles, navigation widgets, nonces, and service configuration have been removed. Table rows, filenames, release dates, and checksums retain their source values.

`beta.html` is also copied to `app/src/androidTest/assets/beta.html` for the emulator UI check. Neither source set is included in the main APK.

Retain provenance when updating a fixture and add a regression assertion for the behavior being fixed. Do not replace samples with live network calls in unit tests. Google material remains subject to applicable source licenses; see https://developer.android.com/license.
