# ORCHORDS Studio AI release status — 2026-09-16

Current intended GitHub Release:

- versionName: `0.1.2`
- versionCode: `1000002`
- canonical version source: root `gradle.properties`
- release workflow: `.github/workflows/release.yml`
- package: `com.orchords.orchordsai`

The legacy `.github/workflows/daily-build.yml` workflow has been removed. The new release workflow requires a signed release build, runs web and Android verification, verifies APK/AAB embedded version metadata and SHA-256 checksums, publishes a versioned GitHub Release, verifies it, then deletes older Releases and confirms only the new Release remains.

As of this status record, the GitHub Releases API returns an empty list and pushes that should match `release.yml` are creating zero workflow runs. No compile or release publication is therefore claimed yet. Release completion requires a real workflow run and its final live-Release verification step.
