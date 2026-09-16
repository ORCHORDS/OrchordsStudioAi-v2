# Batch C map — Google Play release evidence

Status: mapped from current source plus current official Google/Android documentation on 2026-09-16. Re-check before release.

## Current repo facts

- `app/build.gradle.kts` currently declares `compileSdk = 37`, `minSdk = 26`, and `targetSdk = 37`.
- Current release documentation says source-tree native `.so` files are absent, but that does not prove the final AAB contains no transitive native libraries.
- GitHub Actions/runners are intentionally disabled, so release evidence must be produced on an authorized development host or in Play Console rather than inferred from historical workflow output.

## Current official requirements checked

### Target API
Google Play states that from 31 August 2026, new mobile apps and app updates must target Android 16 / API 36 or higher. Existing mobile apps must target API 35 or higher to remain available to new users on newer Android versions.

Source:
https://support.google.com/googleplay/android-developer/answer/11926878

The current repo target of 37 is above that stated minimum, but the actual candidate AAB must still be inspected before submission.

### 16 KB page-size compatibility
Android's current guidance says apps targeting Android 15 / API 35+ on Google Play must support 16 KB page sizes on 64-bit devices, and from 1 February 2027 incompatible updates cannot be released. Apps using native libraries directly or indirectly through SDKs need the relevant compatibility work/testing.

Source:
https://developer.android.com/guide/practices/page-sizes

### Play App Signing
Google Play distinguishes the developer-held upload key from the Play-managed app-signing key and recommends keeping them separate. API/OAuth providers that bind Android clients to signing fingerprints need the Play app-signing certificate fingerprints registered.

Source:
https://support.google.com/googleplay/android-developer/answer/9842756

## Work map

### C1 — produce one exact candidate artifact
- [ ] Build the release AAB on the authorized host with the intended version code/name.
- [ ] Record the Git commit SHA used to build it.
- [ ] Record the AAB SHA-256.
- [ ] Keep signing material outside the repository.

### C2 — inspect the AAB, not only source
- [ ] Confirm application ID.
- [ ] Confirm version code/name.
- [ ] Confirm min/target SDK.
- [ ] Extract declared permissions and exported components from the built artifact.
- [ ] Enumerate packaged native `.so` files, including transitive SDK/AAR content.
- [ ] If native libraries exist, verify 16 KB compatibility/alignment and test on an appropriate environment.

### C3 — signing state
- [ ] Verify Play App Signing enrollment in Play Console.
- [ ] Record app-signing SHA-256/SHA-1 fingerprints where required for API/OAuth registration.
- [ ] Verify upload-key ownership and recovery/reset process.
- [ ] Verify the people/service accounts that can perform release actions use least privilege.

### C4 — permissions/declarations
- [ ] Compare final AAB permission set with the shipped feature map.
- [ ] Remove unused permissions before submission.
- [ ] Complete any Permissions Declaration triggered by the actual uploaded AAB.
- [ ] Reconcile Data Safety, target audience, ads, content rating, app-access instructions, and AI-generated-content answers against this exact candidate.

### C5 — internal/closed test evidence
- [ ] Upload the exact candidate or reproducible equivalent to the intended test track.
- [ ] Review Play pre-launch findings.
- [ ] Review Android vitals once data exists.
- [ ] Map each actionable finding back to the master map before production promotion.

## Verification rule

Do not mark this batch complete from source inspection alone. Completion requires artifact-level evidence and Play Console state for the exact candidate release.
