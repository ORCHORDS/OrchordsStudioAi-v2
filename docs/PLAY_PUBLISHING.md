# Play Console Publishing Path

**App:** OrchordsAI (`com.orchords.orchordsai`)
**Source of truth:** `fastlane/`,
`app/build.gradle.kts`, `.github/workflows/daily-build.yml`,
`.github/workflows/play-internal-publish.yml`,
`docs/LISTING_ASSETS.md`, `docs/PRIVACY.md`, `docs/DATA_SAFETY.md`,
`docs/PERMISSIONS.md`, `docs/CONTENT_RATING.md`.
**Last code-source review:** `git rev 16bce6c` (2026-09-14).

This document describes the end-to-end path from a green Daily Build to
a release published on the Google Play Console *internal* track. It is
intentionally concrete: every step names a command or a UI screen so a
new maintainer can execute it without re-deriving anything.

---

## 1. What is implemented in the repository

The following pieces are in `main` and exercised by CI:

| Piece | Path | Notes |
|---|---|---|
| Fastlane scaffold | `fastlane/Appfile`, `fastlane/Fastfile`, `fastlane/supply.json` | All credentials via env vars; no secrets committed. |
| AAB + APK build | `app/build.gradle.kts` `buildAll` task | Depends on `assembleRelease` and `bundleRelease`. |
| AAB publishing | `.github/workflows/daily-build.yml` | Signed path invokes `buildAll`, validates AAB package + version with `apkanalyzer`, copies the AAB to `release-assets/orchords-studio-ai.aab`. |
| Manual internal publish | `.github/workflows/play-internal-publish.yml` | `workflow_dispatch`, internal track only, secret-only auth, refuses non-internal tracks with an explicit error. |
| Policy guardrails | `app/src/test/java/.../release/FastlanePublishingPolicyTest.kt` | Verifies Fastlane config, gitignored credentials, AAB validation, manual-publish workflow invariants. |
| `.gitignore` | `fastlane/play-store-key.json`, `fastlane/.bundle/` | Service-account JSON and bundler state never committed. |

---

## 2. Prerequisites (operator-side)

The following are **not** in the repo and must be supplied by the
operator before any production publication:

- Google Play Console developer account with **Release manager** role
  for `com.orchords.orchordsai`.
- A Google Cloud service account with the **Release manager (Play
  Console)** role. The JSON key is uploaded as the GitHub Actions
  secret `PLAY_STORE_JSON_KEY`.
- Release signing material uploaded as GitHub Actions secrets
  `KEY_BASE64` (base64 of the upload keystore) and `SIGNING_CONFIG`
  (the `storeFile/storePassword/keyAlias/keyPassword` lines consumed by
  `app/build.gradle.kts`).
- `docs/LISTING_ASSETS.md` resolved for the target track — see §5.
- Play pre-launch report reviewed (this is a Play Console action; no
  automation).

---

## 3. Repository layout for the publishing lane

```
fastlane/
  Appfile                      # package_name + gitignored JSON key path
  Fastfile                     # lanes :bundle, :internal, :promote
  supply.json                  # track-only config; no secrets
```

### 3.1 `Appfile`

```ruby
package_name("com.orchords.orchordsai")
json_key_file(ENV.fetch("PLAY_STORE_JSON_KEY_FILE", "fastlane/play-store-key.json"))
```

### 3.2 `Fastfile`

```ruby
default_platform(:android)

platform :android do
  desc "Build the signed Android App Bundle"
  lane :bundle do
    gradle(
      task: "bundleRelease",
      properties: {
        "releaseVersionName" => ENV.fetch("RELEASE_VERSION_NAME"),
        "releaseVersionCode" => ENV.fetch("RELEASE_VERSION_CODE"),
      },
    )
  end

  desc "Upload a validated bundle to internal testing"
  lane :internal do
    upload_to_play_store(
      track: "internal",
      aab: ENV.fetch("PLAY_STORE_AAB"),
      mapping: ENV["PLAY_STORE_MAPPING"],
      release_status: "completed",
    )
  end

  desc "Promote the current internal release to production"
  lane :promote do
    # A staged rollout (fraction < 1.0) must be released as
    # `inProgress`; `completed` is only valid for a full rollout.
    rollout_fraction = ENV.fetch("PLAY_STORE_ROLLOUT", "0.1").to_f
    release_status = if rollout_fraction >= 1.0 then "completed" else "inProgress" end
    upload_to_play_store(
      track: "internal",
      track_promote_to: "production",
      rollout: rollout_fraction,
      release_status: release_status,
    )
  end
end
```

The `:internal` lane **does not** rebuild the AAB; it consumes the
exact AAB artifact produced by Daily Build. This keeps the canonical
build inside the CI pipeline that gates on Main Verification, Daily
Build, and Security Analysis.

### 3.3 `supply.json`

```json
{
  "package_name": "com.orchords.orchordsai",
  "track": "internal",
  "skip_upload_apk": true,
  "skip_upload_aab": false,
  "skip_upload_metadata": true,
  "skip_upload_images": true
}
```

---

## 4. Per-track flow

### 4.1 Internal track (smoke test)

1. Wait for a green Daily Build on `main`. The signed build produces
   `app/build/outputs/bundle/release/app-release.aab`, validated by
   `apkanalyzer` for package id `com.orchords.orchordsai`, matching
   version-name and version-code, and SHA-256 checksummed. The same
   build copies the AAB to `release-assets/orchords-studio-ai.aab` for
   the publishing workflow to download.
2. In GitHub → Actions → *Play Internal Publish* → *Run workflow*,
   leave the `track` input as `internal`. The workflow:
   - Re-checks the AAB checksum.
   - Materializes the signing material and Play service-account JSON
     from GitHub Secrets.
   - Downloads the validated `latest-apks` artifact.
   - Runs `bundle exec fastlane PlayStore internal`.
   - Removes the signing material and service-account JSON on exit.
3. Add a closed-testers list in Play Console → Testing → Internal
   testing → Testers. The build is available to those accounts within
   minutes.
4. Confirm the *Data Safety*, *Permissions*, *Content rating*,
   *Privacy policy* sections all show green ticks. The text in each
   section is sourced from `docs/DATA_SAFETY.md`, `docs/PERMISSIONS.md`,
   `docs/CONTENT_RATING.md`, and the URL in `docs/PRIVACY.md` §6.

The workflow refuses to run with `track=closed` or `track=open`. Use
the Play Console UI for those tracks until a dedicated lane is added.

### 4.2 Closed alpha → open beta

Promote via the Play Console UI, or by extending `Fastfile` with a
lane that calls `upload_to_play_store` with `track_promote_to: "beta"`
and re-running with no rebuild (Play reuses the same AAB).

### 4.3 Production

Production publication is **never** automatic. The :promote lane
exists in `Fastfile` for explicit operator invocation after the
Play Console UI confirms:

1. `docs/LISTING_ASSETS.md` lists zero TODO rows for the *production*
   track (the 512×512 hi-res icon is the last blocker per PLAY-10).
2. The Daily Build SHA on `main` has **Main Verification**,
   **Daily Build**, and **Security Analysis** all green.
3. The `:app:testDebugUnitTest` suite is green on the same SHA.
4. `releaseVersionName` is a semantic-version bump (no `-SNAPSHOT`).
5. `docs/PRIVACY.md` URL resolves to an HTTPS host that returns 200
   and the Play Console *Privacy policy* field is set to that URL.
6. The Play pre-launch report (generated after the internal-track
   upload) has been reviewed.

If those gates are met, an operator with `release` access can run
`bundle exec fastlane PlayStore promote` locally with
`PLAY_STORE_ROLLOUT=0.1` (defaults to `0.1`) to roll the internal
build out to production at 10%; the lane automatically switches
`release_status` to `inProgress` for any staged rollout and to
`completed` only when `PLAY_STORE_ROLLOUT=1.0`. Operators then
increase to 25%, 50%, 100% over the following 24–72 hours by re-running
the lane with a higher `PLAY_STORE_ROLLOUT`.

---

## 5. Listing assets (PLAY-10)

The 512×512 hi-res icon, 1024×500 feature graphic, 2–8 phone
screenshots, and en-US short/full descriptions are external assets.
They must be authored or sourced outside the repository and dropped
into the locations listed in `docs/LISTING_ASSETS.md` *before* any
production publication. **Do not** fabricate screenshots or
placeholder icons; Play rejects them at review time and the audit
trail is harder to recover than the missing asset.

PLAY-10 is open until those rows in `LISTING_ASSETS.md` are all
filled in with real files.

---

## 6. Rollback

`upload_to_play_store` cannot un-publish an AAB. To roll back:

1. Play Console → Release management → App bundle explorer → select the
   previous internal build → *Promote to production*.
2. Halt the current staged rollout first (the *Halt rollout* button is
   on the same screen).

Do **not** push a "rollback" AAB to the internal track; the auditor
questions that surface at the next Data Safety re-review are not worth
the audit trail.

---

## 7. Pre-flight checklist

Before triggering the *Play Internal Publish* workflow, confirm:

- [ ] The Daily Build SHA on `main` has **Main Verification**,
      **Daily Build**, and **Security Analysis** all green.
- [ ] `docs/LISTING_ASSETS.md` lists zero TODO rows for the *internal*
      track (the 512×512 hi-res icon is the last blocker; PLAY-10).
- [ ] The `:app:testDebugUnitTest` suite is green on the same SHA.
- [ ] `releaseVersionName` is a semantic-version bump (no `-SNAPSHOT`).
- [ ] `docs/PRIVACY.md` URL resolves to an HTTPS host that returns 200
      and the Play Console *Privacy policy* field is set to that URL.
- [ ] GitHub Actions secrets `KEY_BASE64`, `SIGNING_CONFIG`, and
      `PLAY_STORE_JSON_KEY` are present on the environment used by the
      workflow (`play-internal`).

Production publication additionally requires the listing-asset gates
in §4.3.

---

## 8. Open work (external to this repository)

These items must be done in the Play Console UI or by uploading
external material; they cannot be automated from the repo alone:

- Register `com.orchords.orchordsai` in the Play Console and complete
  the *App access*, *Ads*, *Content rating*, *Target audience*, and
  *Data safety* forms.
- Grant the Google Cloud service account **Release manager** access to
  the Play Console app.
- Upload the release keystore (and the corresponding `KEY_BASE64`
  base64 blob) into GitHub Actions secrets.
- Author and upload the listing assets listed in
  `docs/LISTING_ASSETS.md`.
- Submit the pre-launch report (this is triggered automatically after
  the internal-track upload; the operator must review the report
  before promoting to production).

These remain open until the corresponding rows in
`docs/GOOGLE_PLAY_RELEASE_AGENDA.md` are ticked off with concrete
evidence.
