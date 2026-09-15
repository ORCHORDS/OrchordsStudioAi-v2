---
title: "Releasing ORCHORDS AI"
owner: "Release Engineering"
status: "approved"
classification: "public"
last-reviewed: "2026-08-31"
review-cycle: "90 days"
next-review: "2026-11-29"
---

# Releasing ORCHORDS AI

Update version metadata, run the required checks, build with private signing configuration, verify signatures and installability, and publish release notes.

## Artifact rules

Release APKs use the established `OrchordsAI-<version>-<abi>.apk` artifact pattern on disk, and the uploaded GitHub Actions artifact is named `latest-apks` (it carries the universal, arm64-v8a, and x86_64 APKs, the signed AAB at `orchords-studio-ai.aab`, and `SHA256SUMS`). Preserve artifact naming required by automation and consumers even when public prose uses the `ORCHORDS AI` brand.

Never commit signing keys, signing credentials, local signing configuration, or generated APKs to source control. Release provenance and checksums must be verified before publication.

## Play publishing

For Play-track releases, the chain is two workflows:

1. `.github/workflows/daily-build.yml` — runs `bundleRelease` on the
   signed path (when `KEY_BASE64`, `SIGNING_CONFIG`, `KEYSTORE_PASSWORD`,
   `KEY_ALIAS`, `KEY_PASSWORD` are available), validates the AAB with
   `apkanalyzer`, and uploads the APKs, AAB, and checksums to the
   `latest-apks` artifact.
2. `.github/workflows/play-internal-publish.yml` — `workflow_dispatch`
   only; downloads `latest-apks`, materializes signing material from
   `KEY_BASE64`, `SIGNING_CONFIG`, `PLAY_STORE_JSON_KEY` for the
   duration of the job, and runs `bundle exec fastlane PlayStore internal`.

Promotion to production, staged rollout fractions, and the corresponding
review checklist live in [`docs/PLAY_PUBLISHING.md`](PLAY_PUBLISHING.md).

## Brand

**ORCHORDS — BUILD DIFFERENT.**
