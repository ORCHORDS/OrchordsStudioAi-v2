# OrchordsAI Privacy Policy

**Effective date:** 2026-09-16
**App package:** `com.orchords.orchordsai`
**Last code-source review:** 2026-09-16

This policy describes how OrchordsAI handles your data. The statements
below are grounded in what the Android application actually does and
stores on-device; they are the source of truth for the Google Play Data
Safety form, in-app disclosures, and the data-deletion flow.

If anything in the running application disagrees with this document,
that is a bug and must be reported as a privacy/security issue.

---

## 1. What data we collect

OrchordsAI is a local-first Android app. We do not operate a backend
that receives your ordinary conversations, voice recordings, photos,
calendar events, files, provider API keys, MCP credentials, or provider
usage telemetry.

There is one narrow first-party collection path: when **you explicitly
choose “Report AI output”** on an assistant message and submit the
in-app safety form, OrchordsAI sends the selected report to
`https://orchords.com/api/ai-report` so ORCHORDS can review offensive or
prohibited AI-generated content and improve safeguards.

An AI safety report contains only:

- the category you selected;
- the **visible assistant text** you chose to report, bounded to 6,000
  characters;
- an optional note, bounded to 1,200 characters;
- bounded model/provider identifiers when available;
- app version and platform;
- an opaque report reference and submission time.

A report does **not** intentionally include the rest of the
conversation, user messages, attachments, local file paths, provider
API keys, MCP credentials, authorization headers, account/email,
device identifiers, or hidden reasoning/tool traces. The report
endpoint also redacts common credential-shaped values from the selected
text before delivering it to the developer.

Apart from an explicit safety report, the app's outbound network traffic
is the request you explicitly send to an AI provider (OpenAI,
Anthropic, Google, a local Ollama endpoint, or any provider you
configure), the MCP endpoints you configure, and ordinary Play Services
/ Android system network calls. Those provider/MCP requests are not
routed through Orchords.com infrastructure.

---

## 2. Data stored on your device

The following data is persisted locally in app-private storage and a
local Room/SQLite database. It never leaves the device except when you
explicitly send it to a configured provider/MCP service, export/share
it, or submit the limited AI safety report described above.

| Category | Concrete fields | Where |
|---|---|---|
| Conversations | id, assistantId, title, node list, timestamps, custom system prompt, mode/lorebook bindings, pinned flag, chat suggestions | Room `ConversationEntity` |
| Messages | id, conversationId, role, content (text or JSON-serialized `UIMessage`), token counts, model id, reasoning level, tool calls/responses, attachments | Room `MessageNodeEntity` (+ payload blob for large content) |
| Provider usage | model, prompt tokens, completion tokens, timestamp, assistantId | Room `ProviderUsageEventEntity` |
| Favorites | conversationId, timestamp | Room `FavoriteEntity` |
| Folders | name, parent, timestamps | Room `FolderEntity` |
| Generated media | model, prompt, seed, timestamps, file path reference | Room `GenMediaEntity` |
| Workspace | per-conversation workspace metadata, cwd, .env/secrets references | Room `WorkspaceEntity` + on-disk `.orchords` files |
| Memories | assistantId, fact text, timestamps | Room `MemoryEntity` |
| Provider configuration | baseUrl, model selection, non-secret configuration; API keys use the provider encrypted credential store | DataStore + encrypted credential store |
| MCP configuration | endpoint, transport, tools, non-secret OAuth metadata; OAuth secrets and all MCP header values are encrypted separately | DataStore + Android-Keystore-backed secondary secret store |
| AI assistant definitions | name, prompt, regex transforms, tool result retention settings | Room `AssistantEntity` (Kotlinx-serialization) |
| Voice activation | VoiceInteractionService prompt phrases | Resource bundled with APK |
| Onboarding | first-run completion flag, version | DataStore |
| Crash logs | Android system logs only; we do not embed a crash SDK | Device default |

The AI safety-report form is transient in the app; submitting it does
not create a second local copy of the selected message.

---

## 3. Permissions and why

| Permission | Why OrchordsAI requests it |
|---|---|
| `INTERNET` | Send your messages to the AI/MCP provider you configured and submit an AI safety report only when you explicitly choose Report AI output. |
| `CAMERA` | Optional: capture an image to attach to a message (only when you tap the camera button). |
| `RECORD_AUDIO` | Optional: transcribe voice input to text or run the bundled voice-interaction service (only when you start a voice session). |
| `READ_CALENDAR` | Optional: include calendar context in your prompts (only when you grant access through the assistant-settings flow). |
| `WRITE_CALENDAR` | Optional: write events the assistant creates back to your calendar. |
| `POST_NOTIFICATIONS` (Android 13+) | Optional: notify you when a background generation, transcription, or scheduled task completes. You can deny without losing core chat. |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` / `FOREGROUND_SERVICE_SPECIAL_USE` | Run a user-initiated generation/transcription session in the foreground so the OS does not kill it when the screen is off. The foreground notification is always visible. |
| `ACCESS_WIFI_STATE` / `CHANGE_WIFI_MULTICAST_STATE` / `ACCESS_LOCAL_NETWORK` | Discover local-network AI providers (e.g. Ollama running on your LAN) and the on-device `WebServerService` so you can pair a desktop browser. |
| `SET_ALARM` | Optional: schedule a reminder the assistant set up. |
| `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion="28"`) | Legacy read/write access for older Android versions; superseded by scoped storage on Android 10+. The app does not access shared storage on modern Android. |

No background location, contacts, SMS, call log, microphone-while-idle,
or accessibility-service permission is requested.

---

## 4. AI provider, MCP, and safety-report data

When you configure an AI provider (OpenAI, Anthropic, Google, Ollama,
etc.) the app stores your provider API key in its encrypted provider
credential store and writes a blanked placeholder to ordinary
DataStore/JSON. Non-secret provider settings remain in app-private
DataStore/JSON. The API key is never intentionally logged.

When you send a message, the app can send to your selected provider:

- your current conversation context (messages, system prompt, tool
  definitions);
- any file, image, voice transcript, or calendar event you explicitly
  attached to the message;
- provider authentication needed for that request.

The remote provider's privacy policy then applies to that request. We
do not proxy or intercept the provider traffic.

MCP servers you configure behave the same way: the app makes requests
directly to the MCP endpoint you chose. OAuth access/refresh tokens,
client secrets, and MCP header values (including PAT/API-key headers)
are encrypted with an Android-Keystore-backed key and kept out of
ordinary Settings DataStore JSON. Portable settings backups exclude
MCP server configuration.

### In-app AI content reports

Submitting **Report AI output** is optional and requires a deliberate
user action. The report is sent over HTTPS to ORCHORDS' first-party
`/api/ai-report` endpoint. The endpoint validates and rate-limits the
submission, redacts common credential-shaped values, and delivers the
minimal report to the ORCHORDS operational moderation/contact channel.
It does not echo the reported content in its response.

Reports are used only to review offensive/prohibited AI output,
investigate repeated failure patterns, and improve product safeguards
or meet safety/compliance obligations. They are not used for
advertising or user profiling.

Operational policy: safety reports should be reviewed and deleted from
the moderation/contact channel within **90 days** after resolution,
unless a longer period is reasonably required for an active security,
fraud, legal, or regulatory investigation. The app has no account
identity to associate with a report; references are opaque report ids.

---

## 5. On-device web server

OrchordsAI exposes a small local HTTP server (`WebServerService`) so you
can open a browser on the same Wi-Fi network and pair it with the app
for desktop input. The server binds only to your device's local
network, refuses connections from outside the LAN, requires the pairing
token shown on the app screen, and is off by default. No data sent over
this local server leaves your device.

---

## 6. Backups and device transfer

Android backup/transfer rules are allowlist-based. Credential stores,
conversation databases, generated media, workspace files, MCP
configuration, provider API keys, MCP OAuth tokens, and MCP header
secrets are not included in the portable backup allowlist.

If you want to move supported data to a new device, use the in-app
backup/export flows described by the current product UI and
`docs/DATA_DELETION.md`. Safety reports already submitted to ORCHORDS
are not part of device backup/restore.

---

## 7. How to delete your data

The currently shipped deletion paths include:

- **Delete a conversation:** swipe it away in the conversation list, or
  open it and choose **Delete**.
- **Delete provider credentials:** Settings → **Providers → [name] →
  Remove**. The API key is wiped from storage immediately.
- **Disconnect MCP/GitHub:** Settings → **MCP → [server] → Disconnect**
  clears authentication while retaining non-secret connector settings.
- **Remove MCP/GitHub:** removes the configuration and its secure MCP
  credential entries.
- **Uninstall the app:** removes all on-device app data automatically.

A safety report that has already been submitted cannot be recovered
from the app because the app does not retain a second report copy. To
request deletion of a submitted safety report before its normal
retention period ends, contact ORCHORDS and provide the acknowledgement
reference shown after submission, if you still have it.

A **Settings → Storage → Clear all data** path that drops all local
namespaces in one action remains a tracked enhancement. Until that
lands, uninstall + reinstall is the supported complete local wipe.

Because OrchordsAI has no backend user account, there is no server-side
account to delete.

---

## 8. Children

OrchordsAI is not directed to children under 13. Do not let a child use
the app without parental supervision, because the AI provider you
configure may produce unfiltered content.

---

## 9. Changes to this policy

Material changes will be reflected by bumping the effective date above
and in-app via the product's privacy-policy presentation. The current
in-repo source-of-truth is `docs/PRIVACY.md`; the URL used in the Play
listing must serve materially the same disclosure.

---

## 10. Contact

Privacy questions: open an issue at
<https://github.com/ORCHORDS/OrchordsStudioAi-v2/issues> with the label
`privacy`, or use the ORCHORDS contact channel published on
<https://orchords.com/>.

Security issues: see `SECURITY.md`.
