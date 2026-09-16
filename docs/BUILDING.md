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

Use JDK 17, Android SDK, Node.js 22, and pnpm 11.

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

GitHub Actions are disabled for this repository, so issue work is verified on the contributor's host rather than by a repository runner. Before a direct push to `main`, run:

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

## Brand

**ORCHORDS — BUILD DIFFERENT.**
