# Google Play Release Compliance Agenda

**App:** OrchordsAI (`com.orchords.orchordsai`)
**Repository:** `ORCHORDS/OrchordsStudioAi` (remote: `https://github.com/ORCHORDS/OrchordsStudioAi.git`)
**Status:** Evidence-based pre-release checklist; approval is not guaranteed.
**Prepared:** 2026-09-13

## Existing team agenda and repository state

The existing Play publishing agenda is [`docs/PLAY_PUBLISHING.md`](PLAY_PUBLISHING.md). Related source documents are `docs/DATA_SAFETY.md`, `docs/DATA_DELETION.md`, `docs/PRIVACY.md`, `docs/PERMISSIONS.md`, `docs/CONTENT_RATING.md`, and `docs/LISTING_ASSETS.md`. No separate file named Team One agenda was found; the existing issue material is `.zcode/issue-428-body.md` and `.zcode/plans/plan-sess_efcf20e8-d1aa-4771-8bd9-b73d185934ac.md`.

Read-only inspection recorded: branch `main` tracks `origin/main`; working tree is **not clean**. Modified files include provider/security-related Kotlin, Gradle files, and migration/tests; untracked files include `.zcode/`, security code/tests, and `parse_team2.py`, `team2_all.json`, `team2_snapshot.txt`. No files were changed except this agenda; no commit or push was performed.

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

- [ ] **Pending owner verification:** reconcile `DATA_SAFETY.md` with every current data flow, provider/SDK, optional permission path, retention period, and sharing recipient.
- [ ] **Pending:** publish and test an HTTPS privacy-policy URL, and ensure the same policy is accessible from inside the app and in Play Console.
- [ ] **Pending:** validate deletion behavior and externally reachable deletion/request process against `DATA_DELETION.md`; do not claim account deletion compliance unless account creation/deletion facts are verified.
- [ ] **Pending:** confirm whether API keys, MCP/WebDAV/S3/proxy credentials and other secrets are encrypted at rest. Credential encryption is **in progress, not complete**: issue #428 explicitly leaves several secret surfaces out of scope.
- [ ] **Pending:** update Data Safety answers after the encryption and backup/export implementation is complete and tested.

### Permissions and sensitive access

- [ ] **Pending:** justify and test each manifest permission, including camera, microphone, calendar, notifications, foreground-service types, local-network access, and alarm access; remove permissions not required for the shipped feature set.
- [ ] **Pending:** verify runtime rationale, denial behavior, revocation behavior, and least-privilege handling for every dangerous permission.
- [ ] **Pending:** confirm any restricted API declaration or Play Console declaration required by the final permission set.

### AI content, reporting, and safety

- [ ] **Pending:** document AI-generated content safeguards, user-facing disclosure where appropriate, and an accessible in-app reporting/flagging route for offensive or prohibited output.
- [ ] **Pending:** define moderation/escalation handling and test that reports reach the responsible operator; no policy compliance is inferred from the presence of chat functionality.

### Billing, audience, rating, and ads

- [ ] **Pending:** verify whether any paid digital feature exists. If so, integrate and declare Google Play Billing where required; do not rely on the current documentation claim that there are no in-app purchases without Console/product verification.
- [ ] **Pending:** complete target audience, age/content declarations, and the IARC/content-rating questionnaire from the actual release build.
- [ ] **Pending:** declare ads accurately and verify that ad SDK behavior matches the Data Safety form (current docs say no ads; this remains unverified).
- [ ] **Pending:** verify app access instructions for reviewers, including provider setup, offline/local mode, test data, and any gated functionality.

### Build, SDK, ABI, page size, security

- [ ] **Evidence:** `app/build.gradle.kts` declares `minSdk = 26`, `targetSdk = 37`, `compileSdk = 37`, and ABI filters `arm64-v8a` and `x86_64`.
- [ ] **Pending:** verify the current Play target-API requirement and deadline in Play Console; no deadline is recorded here.
- [ ] **Pending:** inspect the final release AAB for supported ABIs, 16 KB page-size compatibility where applicable, native libraries, exported components, cleartext/network security, and dependency vulnerabilities.
- [x] **OSV scan operational and clean (54cf391):** the Security Analysis OSV Dependency Cross-check job installs `osv-scanner v2.5.1` from the official `osv-scanner_linux_amd64` release asset (no Go toolchain required) and runs `scan source -r .`. Run 34777295551 (deeeedc) surfaced two real vulnerabilities in `web-ui/pnpm-lock.yaml`: `js-yaml 4.3.1` (CVE GHSA-8qvm-5x2c-j2rf) and `morgan 1.11.0` (CVE GHSA-6x3m-p74w-p85c). Both were bumped via `web-ui/pnpm-workspace.yaml` overrides (`js-yaml: 4.3.2`, `morgan: 1.12.0`); `pnpm why` confirms no other version of either package remains in the graph. Run 34778774854 (54cf391) is green for all five Security Analysis jobs (Trivy Misconfig, OSV Cross-check, Gitleaks, Trivy Vulnerabilities, Semgrep SAST).
- [ ] **Pending:** confirm release signing, Play App Signing enrollment/key ownership, upload key custody/rotation, and reproducible artifact fingerprints. Never place signing keys in source control.
- [ ] **PLAY-9 implemented:** `app/build.gradle.kts` registers a `buildAll` task depending on `assembleRelease` and `bundleRelease`. `.github/workflows/daily-build.yml` invokes `buildAll` on the signed path, validates the AAB package id (`com.orchords.orchordsai`), version-name, and version-code with `apkanalyzer`, copies the AAB to `release-assets/orchords-studio-ai.aab`, and uploads it via the `latest-apks` artifact. A separate `play-internal-publish.yml` workflow (`workflow_dispatch` only) downloads that artifact and runs `bundle exec fastlane PlayStore internal`; non-internal tracks are refused at runtime. `fastlane/Appfile`, `fastlane/Fastfile`, and `fastlane/supply.json` exist on `main` with no secrets committed (`.gitignore` covers `fastlane/play-store-key.json` and `fastlane/.bundle/`). `FastlanePublishingPolicyTest` enforces all of the above; it is green on the last signed Daily Build SHA.
- [x] **PLAY-11 implemented (Security Analysis AAB scan, 5e943a6):** a new `Signed AAB Manifest Inspection` job runs in `.github/workflows/security-analysis.yml` (gated on `schedule || workflow_dispatch`). When `RELEASE_KEYSTORE_BASE64` + `ANDROID_SIGNING_CONFIG` are present on the runner it builds `:app:bundleRelease`, extracts the AAB, runs `apkanalyzer` to capture `application-id`, `versionName`, `versionCode`, `minSdk`, `targetSdk`, permissions, and a top-25 file list, generates a SHA-256, then runs Trivy `vuln/misconfig/secret` against the extracted bundle. Evidence is uploaded as the `aab-inspection-evidence` artifact. Without signing secrets on the runner (the current self-hosted configuration) the job takes the explicit-skip path and emits a `n/a` manifest stub so the artifact still uploads. Run 34816179517 (5e943a6) is green for all six Security Analysis jobs (Trivy Misconfig, OSV Cross-check, Gitleaks, Trivy Vulnerabilities, Semgrep SAST, Signed AAB Manifest Inspection).
- [x] **#428 implemented (provider apiKey out of DataStore JSON, 25cd2f3):** `ProviderSetting.apiKey` is now `abstract var` marked `@Transient` so the value never reaches the Settings DataStore JSON. Keys live in `ProviderCredentialStore` (EncryptedSharedPreferences + AES-256-GCM master key). `ProviderSecretCodec` redacts keys on write and hydrates them on read; a V5 DataStore migration forwards any pre-existing plaintext keys into the encrypted store. `ProviderSecretCodecTest`, `ProviderApiKeyRedactionTest`, and `PreferenceStoreV5MigrationTest` cover redaction, hydration, and the no-write-when-keystore-unavailable contract; all green on `:app:testDebugUnitTest` against 25cd2f3.

### Testing, pre-launch, vitals, and Console declarations

- [ ] **Evidence:** `:app:testDebugUnitTest` runs on every Daily Build and the focused `release.*` subset is green (`FastlanePublishingPolicyTest`, `ReleaseWorkflowPolicyTest`, `BuildIdentityTest`, `PrivateRunnerToolchainPolicyTest`). Lint clean (`./gradlew :app:lintDebug`).
- [ ] **Pending:** upload to internal testing first; use pre-launch report and resolve crashes, ANRs, permission failures, policy warnings, and device exclusions.
- [ ] **Pending:** review Android vitals after test distribution and before production promotion; investigate any bad behavior rather than assuming approval.
- [ ] **Pending:** complete Store listing, Data Safety, privacy policy, content rating, target audience, ads, app access, permissions/API declarations, financial declarations, and any Play Console questionnaires shown for this app.
- [ ] **PLAY-10 pending:** verify listing assets. `docs/LISTING_ASSETS.md` currently contains TODO rows and therefore is not a release-ready checklist. Do not fabricate screenshots or placeholder icons.

## Unresolved blockers

1. Listing assets and descriptions remain TODO in `docs/LISTING_ASSETS.md` (PLAY-10).
2. Credential encryption is in progress, not complete; issue #428 and related #229/#242/#87 leave additional secret surfaces unresolved.
3. Data Safety/privacy/deletion answers have not been revalidated against the current build and all SDK/provider flows.
4. Permission, AI reporting, billing, audience/rating, ads, app-access, signing, ABI/page-size, pre-launch, vitals, and Console declaration checks remain pending evidence.
5. Play Console app registration, service-account **Release manager** grant, release keystore upload (`KEY_BASE64` + `SIGNING_CONFIG` secrets), and pre-launch report review remain external to this repository.
6. OSV-Scanner (run 34777295551) now flags `js-yaml 4.3.1` and `morgan 1.11.0` in `web-ui/pnpm-lock.yaml` (transitive deps). Bump the lockfile (or add `pnpm.overrides` for both) and rerun Security Analysis to confirm a clean 5/5.

## Numeric evidence

- Markdown files outside `web-ui/node_modules` were counted with `find ... -name '*.md' | wc -l`: **20**.
- Manifest permission declarations were counted from `app/src/main/AndroidManifest.xml`: **20 lines matching `uses-permission`** (including multiline declarations; exact permission names require final manifest review).
- No deadline, quota, rollout percentage, or approval outcome is asserted without current Console evidence.
