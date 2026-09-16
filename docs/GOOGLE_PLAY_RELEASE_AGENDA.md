# Google Play Release Compliance Agenda

**App:** ORCHORDS AI (`com.orchords.orchordsai`)
**Repository:** `ORCHORDS/OrchordsStudioAi-v2`
**Status:** active release-readiness map
**Last reviewed:** 2026-09-16

The actionable roadmap, research sources, implementation batches, and completion evidence are tracked in GitHub master issue **#5**:

https://github.com/ORCHORDS/OrchordsStudioAi-v2/issues/5

This file stays intentionally short so the public repository does not duplicate planning state across multiple locations.

## Current release gates

- MCP OAuth credential persistence, refresh lifecycle, disconnect/delete cleanup, and export/backup exclusion.
- AI-generated-content reporting path and matching privacy/Data Safety disclosures.
- Exact release AAB inspection: package/version, SDK levels, permissions, exported components, native libraries, 16 KB compatibility, and SHA-256.
- Play App Signing and upload-key custody verification.
- Final permission review and any Play Console declarations triggered by the actual AAB.
- Privacy policy URL, Data Safety reconciliation, account/data deletion verification, target audience, content rating, app access, ads declaration, and listing assets.
- Internal/closed testing, pre-launch report, and Android vitals review before production promotion.

## Official sources

- Google Play User Data policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Google Play target API requirements: https://support.google.com/googleplay/android-developer/answer/11926878
- Google Play AI-Generated Content policy: https://support.google.com/googleplay/android-developer/answer/14094294
- Play App Signing: https://support.google.com/googleplay/android-developer/answer/9842756
- Android app signing: https://developer.android.com/studio/publish/app-signing
- Android 16 KB page-size guidance: https://developer.android.com/guide/practices/page-sizes
- Android Keystore: https://developer.android.com/privacy-and-security/keystore

## Working rule

Before marking any release item complete, re-check the current repository state and current authoritative documentation, then attach concrete verification evidence to master issue #5. Historical runner output is not a substitute for current host-side or Play Console evidence.
