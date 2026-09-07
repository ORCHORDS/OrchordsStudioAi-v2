<p align="center">
  <img src="https://raw.githubusercontent.com/ORCHORDS/docs/main/assets/1080x360.jpg" width="1080" alt="ORCHORDS — BUILD DIFFERENT.">
</p>

# Contributing to ORCHORDS AI

**Independent software studio founded in 2025.**

We welcome focused fixes, tests, documentation improvements, and discussed enhancements that strengthen ORCHORDS AI without widening a change unnecessarily.

## Before changing the repository

1. Keep the change narrow and tie behavior changes to a concrete issue or verified defect.
2. Add or update tests for behavior changes and report the verification you ran.
3. Preserve security boundaries around credentials, MCP tools, local data, network requests, and release artifacts.
4. Do not include credentials, private logs, generated build output, production conversations, personal data, or private development metadata.
5. Preserve established ORCHORDS AI naming in user-facing text. Do not rename package IDs, artifact names, protocols, or third-party projects merely for visual consistency.
6. Follow [Branding and Documentation Style](docs/BRANDING.md) for public Markdown and GitHub-facing copy.

## Build and verify

Clone with submodules so `material3/material-color-utilities` is populated:

```bash
git clone --recurse-submodules https://github.com/ORCHORDS/OrchordsStudioAi.git
```

If you already cloned without `--recurse-submodules`, run:

```bash
git submodule update --init --recursive
```

Skipping this step causes `material3:compileDebugKotlin` to fail with unresolved references to `dynamiccolor.*`. The `:material3` build script guards configuration with a clear message in that case.

Use the repository Gradle wrapper and the documented toolchain. See [Building ORCHORDS AI](docs/BUILDING.md) for the current build and verification commands.

Do not bypass required security, dependency, build, test, lint, or release-integrity checks.

## Security findings

Do not open a public issue for a suspected vulnerability. Follow [SECURITY.md](SECURITY.md).

## Brand

**ORCHORDS — BUILD DIFFERENT.**
