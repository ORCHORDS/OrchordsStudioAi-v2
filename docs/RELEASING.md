---
title: "Releasing ORCHORDS AI"
owner: "Release Engineering"
status: "approved"
classification: "public"
last-reviewed: "2026-09-16"
review-cycle: "90 days"
next-review: "2026-12-15"
---

# Releasing ORCHORDS AI

Update the canonical version in `gradle.properties`, run the required checks, build with private signing configuration, verify the produced binaries and checksums, then publish the versioned GitHub Release.

## Version rules

`gradle.properties` is the release source of truth:

```properties
releaseVersionName=0.1.2
releaseVersionCode=1000002
```

Both values must advance for a new shipped version. The release workflow refuses to reuse an existing version tag for different source. Android/Google Play requires `versionCode` to increase for updates and caps Play uploads at `2100000000`.

## Artifact rules

The GitHub Release publishes:

- `orchords-studio-ai-universal.apk`
- `orchords-studio-ai-arm64-v8a.apk`
- `orchords-studio-ai-x86_64.apk`
- `orchords-studio-ai.aab`
- `SHA256SUMS`
- `mapping.txt` when produced by the minified release build

The matching GitHub Actions artifact is named `latest-apks` for downstream release tooling.

Never commit signing keys, signing credentials, local signing configuration, or generated APK/AAB files to source control. A GitHub Release must use the signed release path; the workflow fails rather than silently publishing a debug APK when signing secrets are unavailable.

## GitHub Release workflow

`.github/workflows/release.yml`:

1. checks out current `main`;
2. installs the pinned private Android/JDK/Node toolchain;
3. reads and validates the canonical version from `gradle.properties`;
4. requires `KEY_BASE64` and `SIGNING_CONFIG`;
5. runs local preflight, web Planning tests/typecheck/build, app/AI/workspace tests and Android lint;
6. builds the signed APK matrix and AAB;
7. verifies embedded package/version metadata with `apkanalyzer`;
8. generates and verifies SHA-256 checksums;
9. refuses to reuse an existing version tag for different source;
10. publishes `v<releaseVersionName>` as the latest GitHub Release;
11. verifies the new Release and its assets;
12. only then deletes older GitHub Releases and confirms exactly one current Release remains.

## Play publishing

For Play-track releases, the chain is:

1. `.github/workflows/release.yml` — builds and verifies the signed AAB/APK matrix and uploads the `latest-apks` artifact.
2. `.github/workflows/play-internal-publish.yml` — manual internal-track publishing path using the validated AAB and Play credentials.

Promotion to production, staged rollout fractions, and the corresponding review checklist live in [`docs/PLAY_PUBLISHING.md`](PLAY_PUBLISHING.md).

## Brand

**ORCHORDS — BUILD DIFFERENT.**
