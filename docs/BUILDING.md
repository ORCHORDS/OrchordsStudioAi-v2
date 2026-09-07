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

Use JDK 17, Android SDK, Git submodules, Node.js 22, and pnpm 11.

## Setup

Clone or initialize Git submodules recursively. The `material3/material-color-utilities`
submodule is required for `:material3` to compile. Recommended clone:

```bash
git clone --recurse-submodules https://github.com/ORCHORDS/OrchordsStudioAi.git
```

If you already cloned, run from the repo root:

```bash
git submodule update --init --recursive
```

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
