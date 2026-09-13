# Play Console Publishing Path

**App:** OrchordsAI (`com.orchords.orchordsai`)
**Source of truth:** `app/build.gradle.kts`,
`.github/workflows/daily-build.yml`, `docs/LISTING_ASSETS.md`,
`docs/PRIVACY.md`, `docs/DATA_SAFETY.md`, `docs/PERMISSIONS.md`,
`docs/CONTENT_RATING.md`.
**Last code-source review:** `git rev 8da35ce` (2026-09-13)

This document describes the end-to-end path from a green Daily Build to
a release published on the Google Play Console *internal* track. It is
intentionally concrete: every step names a command or a UI screen so a
new maintainer can execute it without re-deriving anything.

---

## 1. Prerequisites

- Google Play Console developer account with **Release manager** role
  for `com.orchords.orchordsai`.
- A Google Cloud service account with the **Release manager (Play
  Console)** role. Download the JSON key once; it is the only secret
  the publishing pipeline needs.
- Local `bundletool` 1.16+ and `apksigner` from the Android SDK.
- `fastlane` 2.220+ (`brew install fastlane` or `gem install fastlane`).
- `gcloud` auth for the Google service account (`gcloud auth
  activate-service-account … --key-file=…`).

All of these are operator-side; none of them change the APK.

---

## 2. Repository layout for the publishing lane

The repo currently has no `fastlane/` directory. Add it on the
publishing branch, not on `main`, and merge back once a single dry-run
succeeds:

```
fastlane/
  Appfile                      # package_name + json_key_file path
  PlayStoreFastfile            # lanes :bundle, :internal, :promote
  supply.json                  # track-only config; no secrets
```

### 2.1 `Appfile`

```ruby
json_key_file("fastlane/play-store-key.json")  # git-ignored
package_name("com.orchords.orchordsai")
```

### 2.2 `PlayStoreFastfile`

```ruby
default_platform(:android)

platform :android do
  desc "Build a signed AAB for the current releaseVersionName"
  lane :bundle do
    gradle(
      task: "bundleRelease",
      properties: {
        "releaseVersionName" => ENV["RELEASE_VERSION_NAME"],
        "releaseVersionCode" => ENV["RELEASE_VERSION_CODE"],
      },
    )
  end

  desc "Upload the just-built AAB to the internal track"
  lane :internal do
    bundle
    upload_to_play_store(
      track: "internal",
      aab: "app/build/outputs/bundle/release/app-release.aab",
      mapping: "app/build/outputs/mapping/release/mapping.txt",
      release_status: "completed",
    )
  end

  desc "Promote the most recent internal build to production"
  lane :promote do
    upload_to_play_store(
      track: "internal",
      track_promote_to: "production",
      rollout: "0.1",            # start at 10% then increase
      release_status: "completed",
    )
  end
end
```

### 2.3 `supply.json`

Empty placeholder so `supply` validates the package name:

```json
{ "package_name": "com.orchords.orchordsai" }
```

---

## 3. Per-track flow

### 3.1 Internal track (smoke test)

1. `./gradlew :app:bundleRelease` — emits
   `app/build/outputs/bundle/release/app-release.aab` with R8 minification
   and resource shrinking (locked by `PLAY-1` and exercised by
   `ReleaseR8MinifyPolicyTest`).
2. `bundle exec fastlane PlayStore internal` — uploads the AAB to the
   *internal* track under the latest release tag.
3. Add a closed-testers list in Play Console → Testing → Internal
   testing → Testers. The build is available to those accounts within
   minutes.
4. Confirm the *Data Safety*, *Permissions*, *Content rating*,
   *Privacy policy* sections all show green ticks. The text in each
   section is sourced from `docs/DATA_SAFETY.md`, `docs/PERMISSIONS.md`,
   `docs/CONTENT_RATING.md`, and the URL in `docs/PRIVACY.md` §6.

### 3.2 Closed alpha → open beta

Promote via the Play Console UI, or by changing the lane to
`track_promote_to: "beta"` and re-running with no rebuild (Play
reuses the same AAB).

### 3.3 Production

1. Verify the *Store listing* shows the correct short + full description,
   hi-res 512×512 icon, 1024×500 feature graphic, and 2–8 phone
   screenshots. The set of items required is enumerated in
   `docs/LISTING_ASSETS.md`; that document must show zero TODO rows
   before this step.
2. `bundle exec fastlane PlayStore promote` to roll the internal build
   out to production at 10%. Increase to 25%, 50%, 100% over the
   following 24–72 hours.

---

## 4. Mapping to the Daily Build workflow

`daily-build.yml` already produces the *GitHub Latest* APK that this
lane consumes. To wire the two together:

1. The Daily Build step *Upload exact APK artifact bundle* should also
   upload the matching `app-release.aab` from the same `assembleRelease`
   invocation. Add it as a sibling artifact so the lane can pull it
   without re-building.
2. Trigger the lane from a manual `workflow_dispatch` job so the
   signing key (still in GitHub Actions secrets) can be reused; do not
   move the signing key into the local lane runner.

This keeps the canonical APK build inside Daily Build (so we keep the
*4 of 4 required status checks* green) and only ever *uploads* from the
lane.

---

## 5. Rollback

`upload_to_play_store` cannot un-publish an AAB. To roll back:

1. Play Console → Release management → App bundle explorer → select the
   previous internal build → *Promote to production*.
2. Halt the current staged rollout first (the *Halt rollout* button is
   on the same screen).

Do **not** push a "rollback" AAB to the internal track; the auditor
questions that surface at the next Data Safety re-review are not worth
the audit trail.

---

## 6. Pre-flight checklist

Before running `fastlane PlayStore internal`, confirm every box:

- [ ] `docs/LISTING_ASSETS.md` lists zero TODO rows for the *internal*
      track (the 512×512 hi-res icon is the last blocker).
- [ ] The Daily Build SHA on `main` has **Main Verification**,
      **Daily Build**, and **Security Analysis** all green.
- [ ] `:app:testDebugUnitTest` shows 751 / 3 / 0 / 0 on the same SHA.
- [ ] `releaseVersionName` is a semantic-version bump (no `-SNAPSHOT`).
- [ ] `docs/PRIVACY.md` URL resolves to an HTTPS host that returns 200.
- [ ] The Play Console *Privacy policy* field is set to the URL from
      `docs/PRIVACY.md` §6.

Once those six boxes are checked the lane is one command away from an
internal-track build.
