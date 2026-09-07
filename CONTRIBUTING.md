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

The repository contains no Git submodules. A plain `git clone` is enough:

```bash
git clone https://github.com/ORCHORDS/OrchordsStudioAi.git
```

The `:material3` module vendors `material-color-utilities` under `material3/src/main/java/` (Apache-2.0; see `material3/src/main/java/LICENSE.material-color-utilities` and `THIRD_PARTY_NOTICES.md`). No submodule bootstrap, manual download, or extra Gradle step is required.

Use the repository Gradle wrapper and the documented toolchain. See [Building ORCHORDS AI](docs/BUILDING.md) for the current build and verification commands.

Do not bypass required security, dependency, build, test, lint, or release-integrity checks.

## Security findings

Do not open a public issue for a suspected vulnerability. Follow [SECURITY.md](SECURITY.md).

## Brand

**ORCHORDS — BUILD DIFFERENT.**
