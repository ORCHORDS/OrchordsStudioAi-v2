# Play Store Listing Assets — Checklist & Current State

**App:** OrchordsAI (`com.orchords.orchordsai`)
**Source of truth:** `app/src/main/AndroidManifest.xml`,
`app/src/main/res/mipmap-*/`, `app/build.gradle.kts`, `docs/*.md`.
**Last code-source review:** `git rev ffa6127` (2026-09-14)

This document lists every asset that Google Play Console expects to see
on the store listing, where it lives in the repository (if it already
exists), and the exact format / size constraint Play requires. Each row
is an action item: **DONE** means the file is checked in and meets the
constraint, **TODO** means it still has to be produced before submission.

---

## 1. App icon

Play Console displays the launcher icon automatically from the APK
(adaptive icon + density buckets). It also requires a dedicated
**512×512 hi-res icon** that is *not* in the APK; it must be uploaded
manually.

| Asset | Format | Size | Current state |
| --- | --- | --- | --- |
| Launcher icon (adaptive) | XML + layered PNGs | as in `mipmap-anydpi-v26` | **DONE** — `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` plus `ic_launcher.png`, `ic_launcher_background.png`, `ic_launcher_foreground.png`, `ic_launcher_monochrome.png` for `mdpi` → `xxxhdpi` |
| Hi-res app icon (Play Console upload) | 32-bit PNG, no transparency | exactly 512×512 px | **TODO** — derive from `ic_launcher_foreground.png` + `ic_launcher_background.png` (omit monochrome layer), export at 512×512 |

The hi-res icon is the only one that cannot be auto-extracted from the
APK. The render is mandatory even though Play will already show the
in-APK icon on the device.

---

## 2. Feature graphic

Play Console requires a **1024×500 px** banner shown at the top of the
listing. It is *not* part of the APK.

| Asset | Format | Size | Current state |
| --- | --- | --- | --- |
| Feature graphic | PNG or JPEG | exactly 1024×500 px, ≤ 1 MB | **TODO** — not in repo. The shipped `app/src/main/assets/banner/banner-1.png`, `banner-2.png`, `banner-3.png` are 1536×1024 (in-app marketing banners), not the 1024×500 Play Console feature-graphic slot. Resize one of them to 1024×500 or compose a new asset from `drawable/orchords_wordmark_blue.png` + tagline. |

The `banner-1.png`, `banner-2.png`, `banner-3.png` in
`app/src/main/assets/banner/` are 1536×1024 (verified on HEAD `ffa6127`
with `file app/src/main/assets/banner/banner-*.png`) and can be reused
as in-app marketing banners. None of them is already at the 1024×500
Play feature-graphic size; a separate resize or new composition is
required before upload.

---

## 3. Phone screenshots

Minimum **2**, maximum **8** per device class. Each must be a PNG or
JPEG, landscape or portrait, with a minimum dimension of 320 px and a
maximum of 3840 px; the longest side must be at least **2× the
shortest** (no square screenshots).

| Asset | Count | Min dims | Current state |
| --- | --- | --- | --- |
| Phone screenshots | 2–8 | portrait 1080×1920 or larger preferred | **TODO** — not in repo. Capture from an Android emulator running the signed debug APK; recommended screens: home (conversation list), chat with code block, settings → provider config, MCP server list |

The four “recommended screens” above are the highest-value screenshots
because they cover the only features the Play listing copy talks
about (conversations, code rendering, provider configuration, MCP
integration). Additional screenshots (voice transcription, document
upload) are optional.

---

## 4. Tablet screenshots (optional)

Play Console allows a separate tablet slot. Same constraints as phone
but typically a 1200×1920 or 2560×1600 capture.

| Asset | Count | Current state |
| --- | --- | --- |
| Tablet screenshots (7″ and 10″) | 0–8 each | **TODO** — not blocking; defer until after the first phone-only upload |

Tablet assets are not required for the first submission. Add them only
if a tablet is a target device in `app/build.gradle.kts`.

---

## 5. Promo video (optional)

Play Console accepts a YouTube URL. Not required.

| Asset | Current state |
| --- | --- |
| Promo video URL | **TODO (optional)** — none planned |

---

## 6. Short description & full description

Pulled directly from `docs/PRIVACY.md` and the existing Play-store copy
that ships in `app/src/main/res/values/strings.xml` if present. The
short description must be ≤ 80 characters; the full description ≤ 4000
characters.

| Asset | Limit | Source |
| --- | --- | --- |
| Short description | ≤ 80 chars | **TODO** — author one-line value prop |
| Full description | ≤ 4000 chars | **TODO** — derive from PRIVACY.md §1–§2 + permission rationale in PERMISSIONS.md |

Both fields are uploaded into Play Console as plain text, not files in
the repo. Suggested wording is recorded in `docs/LISTING_COPY.md` (to
be written alongside PLAY-8).

---

## 7. Localization

Play Console stores per-language listing text. English (en-US) is
required; other locales are optional.

| Locale | Status |
| --- | --- |
| en-US (default) | **TODO** — short + full description, screenshots |
| Other locales | **TODO (optional)** — defer until a contributor offers translations |

---

## Pre-submission verification

Before pressing *Review and roll out*, run this checklist from the repo
root:

1. `./gradlew :app:testDebugUnitTest` — confirms 744 tests, 0 failures.
2. `./gradlew :app:lintDebug` — confirms 0 lint errors.
3. `./gradlew :app:assembleRelease` — confirms R8 + resource shrinking
   succeed (`isMinifyEnabled = true`, `isShrinkResources = true`).
4. Verify the APK output `app/build/outputs/apk/release/*.apk` contains
   `res/mipmap-anydpi-v26/ic_launcher.xml` and density-bucket PNGs.
5. Confirm `releaseVersionName` / `releaseVersionCode` match a Git tag
   that the Daily Build workflow has signed and uploaded to the
   *GitHub Latest* release.

Items marked **TODO** above are not blocking CI; they block Play
Console submission. Until they are produced, treat the Daily Build as
the canonical APK surface and defer the Play upload.
