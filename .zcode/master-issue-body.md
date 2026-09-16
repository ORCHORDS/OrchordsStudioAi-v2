# ORCHORDS AI master work map

This file mirrors the master issue body so the roadmap remains available even if GitHub Issues is temporarily disabled. Every item below is based on the current repository state or current official documentation checked on 2026-09-16. Re-check external requirements before implementation; do not treat this snapshot as permanent policy.

## Working rules

- Work directly on `main`; no topic branches or normal PR flow.
- GitHub Actions/runners stay disabled for ordinary development.
- Before each implementation batch: inspect current code, research current authoritative docs, implement, run local preflight/focused checks, re-read the diff, then push directly to `main`.
- Independent tasks may be investigated in parallel and landed together when they do not share risky state.
- Do not invent compliance status. Console-side facts remain pending until verified in Play Console.

## P0 — credential and auth boundaries

- [x] Provider API keys are removed from Settings DataStore and stored via the existing provider credential path.
- [x] WebDAV password, S3 secret access key, proxy username/password, and web-server access password are moved behind the V6 secondary-secret boundary.
- [ ] Complete host-side Gradle verification for the V6 migration and secondary-secret persistence tests when a local Android toolchain is available.
- [ ] Design and implement MCP OAuth credential storage separately: access token, refresh token, client secret, expiry/refresh lifecycle, logout/revocation, failed-refresh handling, and migration from any plaintext persistence.
- [ ] Verify every credential deletion path removes encrypted material immediately, including clearing a field, deleting a configuration, logout/disconnect, reset, import overwrite, and app-data deletion.
- [ ] Re-audit export/backup/import projections so secrets are excluded unless an explicit encrypted export format is designed and documented.

Sources:
- Android Keystore: https://developer.android.com/privacy-and-security/keystore
- AndroidX Security Crypto deprecation: https://developer.android.com/jetpack/androidx/releases/security
- Current repo: `app/src/main/java/com/orchords/orchordsai/data/security/`, `PreferencesStore.kt`, V5/V6 migrations, `docs/GOOGLE_PLAY_RELEASE_AGENDA.md`.

## P0 — Google Play AI-generated-content requirements

- [ ] Add a clearly accessible in-app reporting/flagging path for offensive or prohibited AI-generated content if the shipped app remains within the policy scope.
- [ ] Define what a report contains, what is intentionally excluded for privacy, where it is sent, retention, user acknowledgement, failure/offline handling, abuse-rate limiting, and operator response process.
- [ ] Add regression tests for the report entry point, submission state, failure state, and privacy boundary.
- [ ] Align user-facing disclosure, privacy documentation, Data Safety answers, and listing copy with the implemented reporting flow.

Source:
- Google Play AI-Generated Content policy: https://support.google.com/googleplay/android-developer/answer/14094294

## P0 — release verification and signing

- [ ] Verify the actual release AAB locally with `apkanalyzer`/bundle inspection after signing configuration exists on the development host.
- [ ] Record package name, version code/name, min/target SDK, exported components, permissions, native libraries, and SHA-256 of the exact candidate artifact.
- [ ] Verify Play App Signing enrollment and distinguish the app-signing key from the upload key; keep upload-key material outside source control.
- [ ] Register the Play app-signing certificate fingerprints with any external API/OAuth providers that bind Android clients to signing certificates.
- [ ] Document upload-key custody, recovery/reset procedure, and who can perform signing-related Play Console actions.

Sources:
- Play App Signing: https://support.google.com/googleplay/android-developer/answer/9842756
- Android app signing: https://developer.android.com/studio/publish/app-signing

## P1 — target SDK and 16 KB compatibility

- [x] Repository currently declares a target SDK above Google Play's 2026 mobile minimum.
- [ ] Reconfirm target/compile SDK from the actual candidate build before each release rather than relying on documentation snapshots.
- [ ] Inspect the final AAB for direct and transitive native `.so` files; source-tree absence alone is not enough to prove final-bundle absence.
- [ ] If native libraries are present, verify ELF alignment/page-size compatibility and test on a 16 KB Android environment.

Sources:
- Google Play target API requirements: https://support.google.com/googleplay/android-developer/answer/11926878
- Android 16 KB page-size guidance: https://developer.android.com/guide/practices/page-sizes

## P1 — privacy, Data Safety and deletion

- [ ] Reconcile `docs/DATA_SAFETY.md` against current production code after the V6 credential migration and any MCP OAuth changes.
- [ ] Verify every data type, purpose, sharing recipient, retention rule, optional permission, external provider and SDK against the final release build.
- [ ] Publish and test the HTTPS privacy-policy URL and ensure the same policy is reachable from inside the app.
- [ ] Determine from the shipped product whether account creation exists. If it does, provide both in-app and external account-deletion request paths and remove associated data rather than only disabling the account.
- [ ] Verify local-data deletion actions for conversations, assistants, provider credentials, backups, generated files, MCP credentials and app reset.

Sources:
- Google Play User Data policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Account deletion requirements: https://support.google.com/googleplay/android-developer/answer/13327111

## P1 — permission and exported-component review

- [ ] Re-read the final merged manifest and map every declared permission to a reachable shipped feature.
- [ ] Remove permissions that no longer have a shipped use case.
- [ ] Verify runtime rationale, denial, revocation and retry behavior for dangerous/special permissions.
- [ ] Inspect every exported activity/service/receiver/provider and every intent filter for minimum exposure.
- [ ] Complete any Play Console Permissions Declaration that the final AAB triggers.

Source:
- Google Play permission declaration guidance: https://support.google.com/googleplay/android-developer/answer/9214102

## P1 — store submission evidence

- [ ] Finish production listing assets from real app screens: hi-res icon, feature graphic, and required phone screenshots.
- [ ] Validate short/full descriptions against current shipped capabilities; remove stale promises and internal implementation wording.
- [ ] Complete target audience, content rating, ads declaration, app access instructions and all App Content questionnaires using the actual release build.
- [ ] Review pre-launch report and Android vitals after an internal/closed test build exists; map each actionable finding back into this master issue or a child issue.

Sources:
- Current repo: `docs/LISTING_ASSETS.md`, `docs/LISTING_COPY.md`, `docs/CONTENT_RATING.md`, `docs/GOOGLE_PLAY_RELEASE_AGENDA.md`.
- Google Play policy/help index: https://support.google.com/googleplay/android-developer/

## P1 — local verification without runners

- [x] Lightweight `scripts/preflight-local.sh` exists for whitespace, merge markers, typo guard and repository sanity.
- [ ] Restore a reproducible host-side verification recipe that works without GitHub Actions: required JDK/Android SDK versions, Gradle commands, targeted test selectors, lint, AAB inspection and secret scanning.
- [ ] Add/maintain focused tests beside each security or migration change rather than depending on a global runner.
- [ ] Keep `.github/workflows` non-operational while they remain historical/reference material; prevent documentation from implying that disabled workflows currently protect `main`.

## P2 — repository/public-release cleanup

- [ ] Search current public source for stale old-repository slugs, obsolete runner claims, internal-only paths, accidental development evidence, TODOs that should not be public, and misleading historical status claims.
- [ ] Review public docs for exact product naming, contact routes, privacy/security reporting paths, license/third-party notices and contributor instructions.
- [ ] Run a public-secret sweep over the complete current Git history/source snapshot before wider promotion; distinguish real credentials from fixtures/placeholders.

## Execution batches

### Batch A — MCP OAuth security
1. Inspect every OAuth model/store/use site.
2. Search current OAuth 2.0 / Android security guidance relevant to the exact provider flow.
3. Write failing migration/storage/lifecycle tests.
4. Implement secure persistence and refresh/logout behavior.
5. Run local focused tests/preflight.
6. Update privacy/data-deletion docs and this master map.

### Batch B — AI content reporting
1. Map current chat/output UI and existing feedback/report surfaces.
2. Re-read current Play AI-generated-content policy.
3. Design the smallest compliant user flow without adding unrelated moderation infrastructure.
4. Implement states/tests and document the operator path.
5. Reconcile privacy/Data Safety/listing language.

### Batch C — release evidence
1. Produce the signed candidate AAB locally.
2. Inspect manifest/native libs/page-size posture.
3. Verify Play signing/upload-key state in Console.
4. Complete declarations/assets and internal test release.
5. Process pre-launch/vitals findings.

## Definition of done for a mapped item

An item is only checked off when the current source has been re-read, current external requirements were re-checked where relevant, the implementation is on `main`, focused local verification evidence exists, documentation matches the shipped behavior, and no claim depends on a disabled runner or an unverified Console state.
