# Latest APK Release Implementation Plan

Goal: keep GitHub's real Latest Release synchronized with the verified `main` build and publish the complete configured Android APK matrix.

## Rules

- Work on `main` only.
- Do not advance while any applicable check for the current commit is red or non-terminal.
- Build exactly the configured universal, arm64-v8a and x86_64 APK outputs.
- Run unit tests and Android lint before publishing.
- Generate and verify SHA-256 checksums for the exact APK files.
- Publish stable release asset names and verify `/releases/latest` points to the exact build commit.
- Do not use GitHub artifact attestations on this private user-owned repository because GitHub does not provide that feature for this repository type; rely on verified SHA-256 checksums and the Actions artifact bundle instead.
- If a check fails, fix the proven root cause and rerun before any later work.
