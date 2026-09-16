---
title: "Building ORCHORDS AI"
owner: "Engineering"
status: "approved"
classification: "public"
last-reviewed: "2026-09-16"
review-cycle: "90 days"
next-review: "2026-12-15"
---

# Building ORCHORDS AI

Use JDK 21, Android SDK, Node.js 22, and pnpm 11.

## Setup

The repository ships everything it needs. A plain `git clone` is enough:

```bash
git clone https://github.com/ORCHORDS/OrchordsStudioAi-v2.git
```

`material-color-utilities` is vendored into `material3/src/main/java/` under Apache License 2.0. The license, upstream source, and attribution live alongside the vendored sources (see `material3/src/main/java/LICENSE.material-color-utilities` and `THIRD_PARTY_NOTICES.md`). No submodule, manual download, or extra Gradle step is required.

In `web-ui`, install JavaScript dependencies with:

```bash
pnpm install --frozen-lockfile
```

## Local preflight

Normal issue development does not depend on repository runners. Before a direct push to `main`, run:

```bash
bash scripts/preflight-local.sh
```

The preflight checks whitespace errors, unresolved conflict markers, a small high-confidence typo set, and repository status. It is intentionally lightweight and is not a substitute for targeted tests.

## Standard verification

Run the checks that match the change. For Android behavior changes, the normal verification set is:

```bash
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

For a narrow issue, focused tests are preferred when they cover the changed behavior. Security, migration, deletion-path, credential-handling, release-integrity, and data-loss-sensitive changes should receive their relevant focused checks even when the rest of the repository is not rebuilt.

Keep `local.properties`, signing configuration, API credentials, and other machine-local secrets out of source control. Do not weaken a relevant security or regression check merely to make a local verification pass.

## GitHub Release build

GitHub Releases are produced by `.github/workflows/daily-build.yml` on the private `main-verification` runner. That release path intentionally performs a stronger verification/build pass than normal issue work:

- verifies JDK 21, Node.js 22, pnpm 11 and the pinned Android toolchain;
- runs app, AI and workspace tests plus Android lint;
- builds the configured universal, arm64-v8a and x86_64 APK matrix;
- produces a signed AAB when the configured signing secrets are present;
- verifies embedded version metadata and SHA-256 checksums;
- updates the rolling `latest` tag and GitHub Release and replaces stale release assets.

The release workflow is the repository exception to the normal no-runner development policy. A release should not be called complete until that workflow reaches its final live-release verification step successfully.

## Brand

**ORCHORDS — BUILD DIFFERENT.**
