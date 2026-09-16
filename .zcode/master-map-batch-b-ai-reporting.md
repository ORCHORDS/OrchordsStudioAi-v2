# Batch B map — AI-generated-content reporting

Status: mapped from current source search plus current Google Play policy on 2026-09-16. Re-check before implementation.

## Current repo facts

- Current production-source search returned no `reportAbuse`, `flagMessage`, `abuseReport`, moderation, or equivalent reporting implementation.
- The current release agenda already records the absence of a production flagging surface.
- ORCHORDS AI is a text/voice/image AI workspace, so the shipped product needs to be evaluated against Google Play's AI-Generated Content policy using actual release behavior, not assumptions.

## Current policy source

Google Play says generative-AI apps are responsible for preventing prohibited/offensive AI-generated content and incorporating user feedback. The policy specifically covers text-to-text AI chatbot apps and other generative AI experiences.

Source:
https://support.google.com/googleplay/android-developer/answer/14094294

## Work map

### B1 — locate the smallest user-facing entry point
- [ ] Map the current generated-message component and overflow/context actions.
- [ ] Identify one consistent place to expose `Report` / `Flag generated content` without redesigning unrelated chat UX.
- [ ] Cover text and other generated-output types that fall within the shipped policy scope.

### B2 — report payload/privacy design
- [ ] Decide the minimum payload needed to investigate a report.
- [ ] Do not silently upload full conversations, credentials, hidden system prompts, local files, or unrelated user data.
- [ ] Clearly disclose what leaves the device before submission when required by current policy/privacy rules.
- [ ] Define report identifier, category/reason, generated-content excerpt/reference, model/provider metadata if needed, timestamps, and optional user note.

### B3 — destination and operator process
- [ ] Define the actual receiving endpoint/operator workflow before building the button.
- [ ] Define success acknowledgement, offline/failure retry behavior, duplicate/rate-limit handling, and abuse protection.
- [ ] Define retention/deletion and operator access boundaries.
- [ ] Document what happens after a report; do not promise moderation SLAs that are not operationally supported.

### B4 — tests
- [ ] Report action is reachable from generated content.
- [ ] Submission includes only approved fields.
- [ ] Failure/offline state is visible and recoverable.
- [ ] Repeated taps do not create accidental duplicate reports.
- [ ] Private/local-only data stays excluded unless explicitly selected/disclosed.

### B5 — policy/docs reconciliation
- [ ] Update `docs/PRIVACY.md` with actual report data flow.
- [ ] Update `docs/DATA_SAFETY.md` only after implementation is final.
- [ ] Update listing copy and Play Console declarations so they match the shipped route.
- [ ] Re-check the current Google Play policy again before marking complete.

## Repo evidence

- `docs/GOOGLE_PLAY_RELEASE_AGENDA.md`
- production-source search for reporting/moderation terms returned no implementation on 2026-09-16.
