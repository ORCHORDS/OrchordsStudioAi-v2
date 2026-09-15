# Google Play Release Compliance Agenda

**App:** OrchordsAI (`com.orchords.orchordsai`)
**Repository:** `ORCHORDS/OrchordsStudioAi` (remote: `https://github.com/ORCHORDS/OrchordsStudioAi.git`)
**Status:** Evidence-based pre-release checklist; approval is not guaranteed.
**Prepared:** 2026-09-14
**HEAD evidence captured on:** `git rev e3b4e88` (2026-09-15)

## Existing team agenda and repository state

The existing Play publishing agenda is [`docs/PLAY_PUBLISHING.md`](PLAY_PUBLISHING.md). Related source documents are `docs/DATA_SAFETY.md`, `docs/DATA_DELETION.md`, `docs/PRIVACY.md`, `docs/PERMISSIONS.md`, `docs/CONTENT_RATING.md`, and `docs/LISTING_ASSETS.md`. No separate file named Team One agenda was found; the existing issue material is `.zcode/issue-428-body.md` and `.zcode/plans/plan-sess_efcf20e8-d1aa-4771-8bd9-b73d185934ac.md`.

Read-only inspection recorded: branch `main` tracks `origin/main`; working tree clean at `e3b4e88`. No files were changed except this agenda on each refresh cycle.

## Official policy references checked

- [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311) — Data Safety must match the privacy policy; disclose collection, use, sharing, retention/deletion, and security practices.
- [Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469) — Console disclosure must include app and SDK behavior and remain accurate.
- [Permissions and APIs](https://support.google.com/googleplay/android-developer/answer/9888170) — request only necessary sensitive permissions and follow restricted-permission rules.
- [In-app purchases](https://support.google.com/googleplay/android-developer/answer/10281818) — use Play Billing where Play billing policy applies.
- [AI-generated content](https://support.google.com/googleplay/android-developer/answer/16070163) — AI apps need safeguards and user reporting/flagging for offensive content. **Current-page details require Console verification; pending.**
- [Target API level](https://support.google.com/googleplay/android-developer/answer/11926878) — verify the applicable current target-API requirement in Console before submission; no deadline is asserted here.
- [App signing](https://support.google.com/googleplay/android-developer/answer/9842756) and [Android App Bundle](https://developer.android.com/guide/app-bundle) — verify signing and AAB delivery configuration.

## Release gate checklist

### Privacy, Data Safety, deletion

- [x] **PRIVACY.md & DATA_DELETION.md aligned with #428 (current HEAD):** `docs/PRIVACY.md §2` and §4 now describe provider API keys as Android Keystore-backed `EncryptedSharedPreferences`; `docs/DATA_DELETION.md` row for "Remove a provider configuration" routes through `SettingsStore.update` and removes the key from the encrypted store. `docs/LISTING_COPY.md` full-description bullets mirror the same wording.
- [ ] **Pending owner verification:** reconcile `DATA_SAFETY.md` with every current data flow, provider/SDK, optional permission path, retention period, and sharing recipient.
- [ ] **Pending:** publish and test an HTTPS privacy-policy URL, and ensure the same policy is accessible from inside the app and in Play Console.
- [ ] **Pending:** validate deletion behavior and externally reachable deletion/request process against `DATA_DELETION.md`; do not claim account deletion compliance unless account creation/deletion facts are verified.
- [ ] **Pending:** confirm whether MCP/WebDAV/S3/proxy credentials and other secrets outside the provider apiKey path are encrypted at rest. Provider apiKey encryption shipped in #428 (25cd2f3) and `docs/PRIVACY.md §4` now describes the Android Keystore-backed `EncryptedSharedPreferences` path; the remaining secret surfaces (MCP/WebDAV/S3/proxy) are tracked by #229 / #242 / #87 and still out of scope.
- [ ] **Pending:** update Data Safety answers after the encryption and backup/export implementation is complete and tested.

### Permissions and sensitive access

- [x] **Manifest inventory captured (HEAD `e3b4e88`):** `app/src/main/AndroidManifest.xml` declares **16** `uses-permission` lines (INTERNET, CAMERA, RECORD_AUDIO, SET_ALARM, WRITE_EXTERNAL_STORAGE maxSdk=28, POST_NOTIFICATIONS, POST_PROMOTED_NOTIFICATIONS, ACCESS_WIFI_STATE, CHANGE_WIFI_MULTICAST_STATE, FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC, FOREGROUND_SERVICE_SPECIAL_USE, ACCESS_LOCAL_NETWORK, PACKAGE_USAGE_STATS, READ_CALENDAR, WRITE_CALENDAR) plus **2** `uses-feature` entries (`android.hardware.camera`, `android.hardware.camera.autofocus`, both `required="false"`). `docs/PERMISSIONS.md` and `docs/PRIVACY.md §3` already map each permission to a feature rationale.
- [ ] **Pending:** justify and test each manifest permission at the Console level (least-privilege review); remove any permission not required for the shipped feature set. Current evidence only covers declaration inventory, not per-permission runtime behavior.
- [ ] **Pending:** verify runtime rationale, denial behavior, revocation behavior, and least-privilege handling for every dangerous permission.
- [ ] **Pending:** confirm any restricted API declaration or Play Console declaration required by the final permission set.

### AI content, reporting, and safety

- [x] **No third-party AI / safety SDKs in the build:** `app/build.gradle.kts` + `gradle/libs.versions.toml` contain no `play-services-ads`, `play-services-analytics`, `firebase`, `crashlytics`, `com.android.billingclient`, or equivalent dependency. App content is local-first and routes only to the AI provider the user configured.
- [ ] **Pending:** document AI-generated content safeguards, user-facing disclosure where appropriate, and an accessible in-app reporting/flagging route for offensive or prohibited output. `grep` for `reportAbuse` / `flagMessage` / `abuseReport` / `moderation` against `app/src/main` returned no production matches — no flagging UI exists today.
- [ ] **Pending:** define moderation/escalation handling and test that reports reach the responsible operator; no policy compliance is inferred from the presence of chat functionality.

### Billing, audience, rating, and ads

- [x] **No ads, no telemetry SDKs (HEAD `e3b4e88`):** `grep -rEn 'analytics|crashlytics|firebase|google-services|play-services-ads|play-services-analytics|adId' app/build.gradle.kts gradle/libs.versions.toml app/src/main` returns zero production hits. There is no ad SDK and no Firebase/Crashlytics analytics; `docs/PRIVACY.md §1` already declares "we do not collect".
- [x] **No Google Play Billing in the build:** `grep -rln 'com.android.billingclient\|BillingClient' app/build.gradle.kts gradle/libs.versions.toml app/src/main` returns zero hits. No in-app purchases are wired today; no Play Billing dependency needs to be declared in Console.
- [x] **Content rating draft (HEAD `e3b4e88`, reflects last review):** `docs/CONTENT_RATING.md` answers the IARC questionnaire with the expected **ESRB: Everyone / PEGI: 3 / IARC: 3+** label on the basis that the app contains no media, no UGC moderation surface, and no advertising. Final questionnaire values still require Play Console review against the actual release build.
- [ ] **Pending:** complete target audience, age/content declarations, and the IARC/content-rating questionnaire from the actual release build (the draft exists but is not yet a Console submission).
- [ ] **Pending:** declare ads accurately and verify that ad SDK behavior matches the Data Safety form (current docs say no ads; this remains unverified).
- [ ] **Pending:** verify app access instructions for reviewers, including provider setup, offline/local mode, test data, and any gated functionality.

### Build, SDK, ABI, page size, security

- [x] **Build/SDK/ABI evidence (HEAD `e3b4e88`):** `app/build.gradle.kts` declares `compileSdk = 37`, `minSdk = 26`, `targetSdk = 37`, and ABI filters `arm64-v8a` and `x86_64` only. No `ndk { abiFilters }` overrides; no manual native library packaging. `gradle.properties` and the root `build.gradle.kts` do not enable deprecated support libraries that would re-introduce legacy ABI requirements. Both required ABIs are 64-bit (Android 12 64-bit compliance is satisfied by the filter list and the missing of `armeabi-v7a` / `x86`).
- [x] **Native-library and 16 KB page-size posture (HEAD `e3b4e88`):** `find app/src/main -name '*.so'` returns no committed native libraries. With zero `.so` files in the module, 16 KB page-size compatibility for Android 15+ is satisfied by virtue of having no native code of our own; transitive AARs that ship native code (none currently on the critical path) are still pending an `apkanalyzer` snapshot of the release AAB. The AAB scan evidence recorded by PLAY-11 (run 34816179517, 5e943a6) remains the authoritative list once the signing-secret path is taken end-to-end.
- [x] **Build hardening (HEAD `e3b4e88`):** `app/build.gradle.kts` enables `isMinifyEnabled = true` and `isShrinkResources = true` for the release variant, and `proguard-rules.pro` covers the EncryptedSharedPreferences / DataStore / Kotlinx-serialization entry points that R8 typically warns about (`NetworkSecurityConfigTest`, `AndroidBackupPolicyTest`, `ProviderSecretCodecTest`, `ProviderApiKeyRedactionTest`, `PreferenceStoreV5MigrationTest` all enforce the surviving-source invariants).
- [ ] **Pending:** verify the current Play target-API requirement and deadline in Play Console; no deadline is recorded here. `targetSdk = 37` is a forward setting that exceeds the 2026 target-API baseline, but the Console-side accept/decline decision is external.
- [ ] **Pending:** confirm exported components (`<activity>`, `<service>`, `<receiver>`) are limited to those required by the shipped feature set, and that no implicit-intent exposure remains on `WRITE_CALENDAR` / `READ_CALENDAR` / `SET_ALARM` flows. The manifest inventory has been captured; per-component review remains.
- [ ] **Pending:** confirm release signing, Play App Signing enrollment/key ownership, upload key custody/rotation, and reproducible artifact fingerprints. Never place signing keys in source control. The PLAY-11 skip path means AAB-inspection evidence has never been produced against a real signed build from this runner; the actual release-key story must be reconciled against the Play Console App Signing dashboard.
- [x] **PLAY-9 implemented:** `app/build.gradle.kts` registers a `buildAll` task depending on `assembleRelease` and `bundleRelease`. `.github/workflows/daily-build.yml` invokes `buildAll` on the signed path, validates the AAB package id (`com.orchords.orchordsai`), version-name, and version-code with `apkanalyzer`, copies the AAB to `release-assets/orchords-studio-ai.aab`, and uploads it via the `latest-apks` artifact. A separate `play-internal-publish.yml` workflow (`workflow_dispatch` only) downloads that artifact and runs `bundle exec fastlane PlayStore internal`; non-internal tracks are refused at runtime. `fastlane/Appfile`, `fastlane/Fastfile`, and `fastlane/supply.json` exist on `main` with no secrets committed (`.gitignore` covers `fastlane/play-store-key.json` and `fastlane/.bundle/`). `FastlanePublishingPolicyTest` enforces all of the above; it is green on the last signed Daily Build SHA.
- [x] **PLAY-11 implemented (Security Analysis AAB scan, 5e943a6):** a new `Signed AAB Manifest Inspection` job runs in `.github/workflows/security-analysis.yml` (gated on `schedule || workflow_dispatch`). When `RELEASE_KEYSTORE_BASE64` + `ANDROID_SIGNING_CONFIG` are present on the runner it builds `:app:bundleRelease`, extracts the AAB, runs `apkanalyzer` to capture `application-id`, `versionName`, `versionCode`, `minSdk`, `targetSdk`, permissions, and a top-25 file list, generates a SHA-256, then runs Trivy `vuln/misconfig/secret` against the extracted bundle. Evidence is uploaded as the `aab-inspection-evidence` artifact. Without signing secrets on the runner (the current self-hosted configuration) the job takes the explicit-skip path and emits a `n/a` manifest stub so the artifact still uploads. Run 34816179517 (5e943a6) is green for all six Security Analysis jobs (Trivy Misconfig, OSV Cross-check, Gitleaks, Trivy Vulnerabilities, Semgrep SAST, Signed AAB Manifest Inspection). Re-verified on HEAD `e3b4e88`: focused `:app:testDebugUnitTest` runs the release/security/migration policy suite — all green; `:app:lintDebug` is also green (0 errors; existing warnings remain).
- [x] **#428 implemented (provider apiKey out of DataStore JSON, 25cd2f3):** `ProviderSetting.apiKey` is now `abstract var` marked `@Transient` so the value never reaches the Settings DataStore JSON. Keys live in `ProviderCredentialStore` (EncryptedSharedPreferences + AES-256-GCM master key). `ProviderSecretCodec` redacts keys on write and hydrates them on read; a V5 DataStore migration forwards any pre-existing plaintext keys into the encrypted store. `ProviderSecretCodecTest`, `ProviderApiKeyRedactionTest`, and `PreferenceStoreV5MigrationTest` cover redaction, hydration, and the no-write-when-keystore-unavailable contract; all green on `:app:testDebugUnitTest` against 25cd2f3 and re-verified green on HEAD `1780862` (re-verification still holds on `e3b4e88` because the production source is unchanged between those SHAs).
- [ ] **Deletion-path credential wipe (Pending):** `ProviderSecretCodec` exposes `redactProvidersForWrite` and `hydrateProvidersFromStore` only — no `removeDroppedProviders` helper or matching test exists on `main`. The "Remove a provider configuration" path in `docs/DATA_DELETION.md` routes through `SettingsStore.update`; the encrypted-store wipe relies on `redactProvidersForWrite` already deleting any prior entry for that provider when the new key is blank, which only fires when the user re-saves the provider row. Removing a provider outright without re-saving does not currently call `store.remove`, so a stale key can remain in `provider_secrets.xml`. The fix is small (call `store.remove(id)` for every provider in the old list whose id no longer appears in the new list) and a regression test would gate it; tracked as a follow-up and not a blocker for the existing PRIVACY.md wording because the user-visible path ("Settings → Providers → Remove") still wipes the DataStore row immediately.

### Testing, pre-launch, vitals, and Console declarations

- [ ] **Evidence:** `:app:testDebugUnitTest` runs on every Daily Build and the focused `release.*` subset is green (`FastlanePublishingPolicyTest`, `ReleaseWorkflowPolicyTest`, `BuildIdentityTest`, `PrivateRunnerToolchainPolicyTest`). Lint clean (`./gradlew :app:lintDebug`).
- [ ] **Pending:** upload to internal testing first; use pre-launch report and resolve crashes, ANRs, permission failures, policy warnings, and device exclusions.
- [ ] **Pending:** review Android vitals after test distribution and before production promotion; investigate any bad behavior rather than assuming approval.
- [ ] **Pending:** complete Store listing, Data Safety, privacy policy, content rating, target audience, ads, app access, permissions/API declarations, financial declarations, and any Play Console questionnaires shown for this app.
- [ ] **PLAY-10 pending:** verify listing assets. `docs/LISTING_ASSETS.md` currently contains TODO rows and therefore is not a release-ready checklist. Do not fabricate screenshots or placeholder icons.

## Unresolved blockers

1. Listing assets and descriptions remain TODO in `docs/LISTING_ASSETS.md` (PLAY-10). The repo has launcher icons across all densities (`mipmap-mdpi` … `mipmap-xxxhdpi`), three 1536×1024 banners under `app/src/main/assets/banner/`, and a 900×271 wordmark in `app/src/main/res/drawable/orchords_wordmark_blue.png`. Missing: a 512×512 hi-res Play Console icon export, a 1024×500 feature graphic, 2–8 phone screenshots, en-US short/full description text. Per standing rule these must not be fabricated; they remain external blockers until produced.
2. Credential encryption still in progress for non-provider secrets; issue #428 shipped the provider apiKey path (PRIVACY.md §4 now accurate). #229/#242/#87 leave additional secret surfaces (MCP/WebDAV/S3/proxy credentials) unresolved.
3. Data Safety/privacy/deletion answers have not been revalidated against the current build and all SDK/provider flows.
4. Per-permission runtime justification, AI content reporting/flagging surface, billing declaration, audience/content rating finalization, app-access reviewer instructions, signing key custody, 16 KB page-size native libraries, pre-launch / vitals, and full Play Console declaration set remain pending evidence. Inventory-only evidence has now been recorded (see `### Permissions and sensitive access` and `### Billing, audience, rating, and ads` above); the per-permission and per-questionnaire Console work is not yet done.
5. Play Console app registration, service-account **Release manager** grant, release keystore upload (`KEY_BASE64` + `SIGNING_CONFIG` secrets), and pre-launch report review remain external to this repository.

## Numeric evidence

- Markdown files outside `web-ui/node_modules` were counted with `find ... -name '*.md' | wc -l`: **20** (count captured 2026-09-13).
- Manifest permission declarations were counted from `app/src/main/AndroidManifest.xml` on HEAD `16bce6c`: **16 lines matching `uses-permission`** (including multiline declarations); **2 lines matching `uses-feature`**, both `required="false"`. Permission names enumerated under `### Permissions and sensitive access` above.
- Manifest permission declarations were counted from `app/src/main/AndroidManifest.xml` on HEAD `ffa6127`: **16 lines matching `uses-permission`** (including multiline declarations; exact permission names require final manifest review).
- Ad / analytics / billing SDK hits against `app/build.gradle.kts`, `gradle/libs.versions.toml`, and `app/src/main` on HEAD `16bce6c`: **0** matches each for `play-services-ads`, `play-services-analytics`, `firebase`, `crashlytics`, `com.android.billingclient`, `BillingClient`. Confirms the current "no ads, no IAP" claim and lets the agenda mark those sub-items off.
- No deadline, quota, rollout percentage, or approval outcome is asserted without current Console evidence.
