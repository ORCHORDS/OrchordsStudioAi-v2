---
title: "Building ORCHORDS AI"
owner: "Engineering"
status: "approved"
classification: "public"
last-reviewed: "2026-08-31"
review-cycle: "90 days"
next-review: "2026-11-29"
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

## Standard verification

```bash
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

Keep `local.properties`, signing configuration, API credentials, and other machine-local secrets out of source control. Do not bypass repository security or dependency checks to make a build appear green.

## Brand

**ORCHORDS — BUILD DIFFERENT.**
