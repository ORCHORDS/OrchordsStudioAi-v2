# Google Play Data Safety — Pre-filled answers

**App:** OrchordsAI (`com.orchords.orchordsai`)
**Source of truth:** `docs/PRIVACY.md`, `docs/DATA_DELETION.md`,
`app/src/main/AndroidManifest.xml`.
**Last code-source review:** 2026-09-16

This document lists the current source-backed answers for the Play
Console **Data Safety** form. Re-check the final release build and the
current Play form before submission; if any answer stops matching the
running app, update the product/privacy implementation first.

Google Play defines data as collected when it is transmitted off a
user's device to the developer. Data sent only when a user deliberately
submits an AI safety report is therefore disclosed here as optional
collection.

---

## 1. Is your app collecting or sharing any of the required user data types?

### Data used on-device and/or sent only for the user's selected feature

| Data type | Collected? | Shared? | Purpose | User controls |
|---|---|---|---|---|
| **Account info** | No | No | — | — |
| **App activity — app interactions** | Yes, locally for normal app operation; a report category is also transmitted only when the user submits an AI safety report | No third-party sale/advertising sharing | App functionality; fraud prevention, security and compliance for report handling | Reporting is optional; delete local conversation; uninstall |
| **App info and performance** (crash logs, diagnostics) | No third-party crash SDK | No | — | — |
| **Approximate location** | Only in the optional AI-report flow: the edge receives the request IP for per-IP abuse/rate limiting; the report endpoint does not persist a location profile | Cloudflare acts as the first-party hosting/service-provider path | Fraud prevention, security and compliance | Do not submit a report; IP is used for the rate-limit check |
| **Audio recordings** | Only if user starts a voice session | Sent to AI provider you configured | Voice-to-text transcription within the assistant | Delete conversation; uninstall |
| **Calendar events** | Only if user grants calendar access through assistant settings | Sent to AI provider you configured when assistant uses calendar context | Calendar context in prompts; optional write-back | Revoke calendar permission in Android Settings or in-app |
| **Contacts** | No | No | — | — |
| **Files and docs** | Only if user attaches a file | Sent to AI provider you configured when attached | Attachments in messages | Delete conversation; uninstall |
| **Other in-app messages / chat content** | Yes — ordinary chat is stored locally and sent to the configured AI provider; additionally, only the visible assistant output selected by the user is collected by ORCHORDS when the user submits an AI safety report | Sent to configured AI provider for normal chat. Safety report is sent to ORCHORDS' first-party reporting service and its service-provider delivery infrastructure | Core chat; optional offensive-content reporting, product safety and compliance | Delete conversation; do not submit a report; report disclosure shown before submission |
| **Other user-generated content** | Optional AI-report note only when the user submits it | No advertising/data-broker sharing | App functionality; fraud prevention, security and compliance | Note is optional; disclosure shown before submission |
| **Photos and videos** | Only if user attaches/captures media | Sent to AI provider you configured when attached | Attachments in messages | Delete conversation; uninstall |
| **Voice or sound recordings** | Only if user starts a voice session | Sent to AI provider you configured | Voice-to-text transcription | Delete conversation; uninstall |

### Data the app does NOT collect for analytics/advertising

- No analytics SDK.
- No advertising SDK.
- No crash-reporting SDK (Android system logging only).
- No background location service or location permission.
- No contacts, SMS, or call-log collection.
- No advertising identifier or device fingerprint is intentionally sent
  by the AI-report payload.

### AI safety-report payload

The optional **Report AI output** flow transmits only:

- report category;
- visible assistant text selected for reporting (maximum 6,000 chars);
- optional note (maximum 1,200 chars);
- bounded model/provider identifiers when available;
- app version/platform;
- an opaque local report id.

It intentionally excludes the rest of the conversation, user messages,
attachments, local file paths, provider/MCP credentials, authorization
headers, account/email, device identifiers, and hidden reasoning/tool
traces. The first-party endpoint performs an additional
credential-shaped-value redaction before operational delivery.

### Encryption in transit

- AI-provider/MCP traffic uses the configured network transport; normal
  public endpoints are expected to use HTTPS.
- AI safety reports are submitted to
  `https://orchords.com/api/ai-report` over HTTPS.
- The on-device `WebServerService` is LAN-scoped and uses its pairing
  controls described by the product documentation.

### Encryption at rest

- App-private storage is protected by Android device-level file-based
  encryption.
- Provider API keys use the encrypted provider credential store.
- MCP OAuth access/refresh tokens, client secrets, and MCP header values
  use the Android-Keystore-backed secondary secret store and are kept
  out of ordinary Settings DataStore JSON.
- AI safety reports are not written to a second local report database by
  the Android app. Submitted reports are delivered to the ORCHORDS
  moderation/contact channel according to `docs/PRIVACY.md`.

### User can delete data

- See `docs/DATA_DELETION.md` for local data paths.
- The app shows an acknowledgement reference after a successful AI
  safety report. A user may provide that reference to ORCHORDS to
  request early deletion of the submitted report; the normal
  operational retention target is described in `docs/PRIVACY.md`.

---

## 2. Data recipients

| Recipient | What is sent | When | User control |
|---|---|---|---|
| AI provider you configured (OpenAI / Anthropic / Google / Ollama / custom) | Conversation context and user-selected attachments needed for the request, plus provider authentication | When you send/generate through that provider | Choose/remove provider; use a local provider where supported |
| MCP servers you configured | MCP-authenticated requests and tool inputs needed for the selected action | When an MCP tool is used | Disconnect/remove MCP server; configure tool approvals |
| ORCHORDS first-party AI safety-report endpoint | Only the bounded report payload described above | Only after the user explicitly taps Send report | Reporting is optional; disclosure is shown before submission |
| Cloudflare / configured operational email or webhook service provider | Processes the optional AI safety report on ORCHORDS' behalf for rate limiting and developer delivery | Only for a submitted report | Reporting is optional |
| Google Play Services (if installed) | Platform-standard Play/Android service data | Through platform services | Android/Play controls |

No report data is sold or used for advertising or cross-app profiling.

---

## 3. Security practices

- Data transmitted to the first-party report endpoint is encrypted in
  transit with HTTPS.
- Provider/MCP credentials use encrypted app-private credential stores.
- AI-report requests are size-limited, category/length validated and
  per-IP rate-limited server-side; the rate limiter fails closed when
  its durable backing binding is unavailable.
- The report endpoint does not echo the reported content in its response
  and does not intentionally log the report body.
- Credential-shaped values in selected report text are redacted before
  operational delivery.
- Users have per-scope local deletion controls plus uninstall.
- The app does not contain ads.
- Android backup/device-transfer rules are allowlist based; conversation
  data and credential namespaces are excluded from the portable
  allowlist.

---

## 4. Account deletion

OrchordsAI has no backend user account. The account-deletion requirement
must therefore be answered based on the final Play Console wording for
apps without account creation. Local deletion behavior is documented in
`docs/DATA_DELETION.md`.

The optional AI safety-report reference is not an account identifier.

---

## 5. Play Console reviewer notes

- Data transmitted off-device by the optional **Report AI output** flow
  must be included in the Data Safety form because Google defines
  off-device transmission to the developer as collection.
- Mark the report-related collection as **optional**: it occurs only
  after a user opens the report UI, reviews the disclosure, selects a
  category, and taps **Send report**.
- Suggested report-related data types to reconcile against the exact
  current Play form: **Other in-app messages** (selected assistant
  output), **Other user-generated content** (optional report note),
  **App interactions/Other actions** (report category/action), and the
  **Approximate location** treatment applicable to the request IP used
  for abuse-rate limiting.
- Suggested purposes: **App functionality** and **Fraud prevention,
  security and compliance**.
- Keep this document and `docs/PRIVACY.md` consistent with the exact
  production endpoint and final release build.

Current Google Play references:
- Data Safety disclosure guidance: https://support.google.com/googleplay/android-developer/answer/10787469
- User Data policy: https://support.google.com/googleplay/android-developer/answer/10144311
- AI-generated content policy: https://support.google.com/googleplay/android-developer/answer/13985936
