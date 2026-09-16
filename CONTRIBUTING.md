<p align="center">
  <img src="https://raw.githubusercontent.com/ORCHORDS/docs/main/assets/1080x360.jpg" width="1080" alt="ORCHORDS — BUILD DIFFERENT.">
</p>

# Contributing to ORCHORDS AI

**Independent software studio founded in 2025.**

We welcome focused fixes, tests, documentation improvements, and discussed enhancements that strengthen ORCHORDS AI without widening a change unnecessarily.

## Working model

1. **Work on `main` only.** This repository does not use topic branches, feature branches, or long-lived working branches. Push commits directly to `main`. Do not open a Pull Request for normal project work; PRs are treated as superseded by the direct-main workflow.
2. **No runner dependency.** GitHub Actions are disabled at the repository level. Normal development must not rely on hosted or self-hosted runners.
3. **Verify locally before pushing.** Run `bash scripts/preflight-local.sh`, then run the focused build, tests, lint, or other checks appropriate to the files you changed.
4. **Research before changing behavior.** Start issue work from the current repository state. For external APIs, platform behavior, policies, dependencies, security guidance, or other version-sensitive facts, check current authoritative web documentation before deciding what is correct. Previous notes, cached assumptions, or model memory are not a source of truth.
5. **Multiple issues may be handled together.** Independent issues may be inspected and worked in parallel, then landed in one direct-to-`main` batch when the combined change is coherent and locally verified. Do not create branches merely to separate concurrent investigations.
6. **Keep public communication human.** Issue comments, commit messages, and documentation should be plain, specific, and natural rather than padded with generic automated wording.

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
git clone https://github.com/ORCHORDS/OrchordsStudioAi-v2.git
```

The `:material3` module vendors `material-color-utilities` under `material3/src/main/java/` (Apache-2.0; see `material3/src/main/java/LICENSE.material-color-utilities` and `THIRD_PARTY_NOTICES.md`). No submodule bootstrap, manual download, or extra Gradle step is required.

Start every issue-focused push with the lightweight local guard:

```bash
bash scripts/preflight-local.sh
```

Then use the repository Gradle wrapper and documented toolchain for the relevant verification. See [Building ORCHORDS AI](docs/BUILDING.md). Run only the checks that are useful for the change, but do not skip a relevant security, migration, data-loss, release-integrity, or regression test just to make a push faster.

## Security findings

Do not open a public issue for a suspected vulnerability. Follow [SECURITY.md](SECURITY.md).

## Brand

**ORCHORDS — BUILD DIFFERENT.**
