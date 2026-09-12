# OrchordsAI Privacy Policy

**Effective date:** 2026-09-13
**App package:** `com.orchords.orchordsai`
**Last code-source review:** `git rev 420300e` (2026-09-13)

This policy describes how OrchordsAI handles your data. The statements
below are grounded in what the Android application actually does and
stores on-device; they are the source of truth for the Google Play Data
Safety form, in-app disclosures, and the account-deletion flow.

If anything in the running application disagrees with this document,
that is a bug and must be reported as a privacy/security issue.

---

## 1. What data we do not collect

We do not operate a backend that receives your content. OrchordsAI is a
local-first Android app. **No conversation messages, voice recordings,
photos, calendar events, files, provider API keys, MCP credentials, or
provider usage telemetry are transmitted to any server controlled by
Orchords.com.**

The app's only outbound network traffic is the request you explicitly
send to an AI provider (OpenAI, Anthropic, Google, a local Ollama
endpoint, or any provider you configure), the MCP endpoints you
configure, and ordinary Play Services / Android system network calls.
None of that traffic is routed through Orchords.com infrastructure.

---

## 2. Data stored on your device

The following data is persisted locally in app-private storage and a
local Room/SQLite database. It never leaves the device except when you
explicitly export it or share it via Android's share sheet.

| Category | Concrete fields | Where |
|---|---|---|
| Conversations | id, assistantId, title, node list, timestamps, custom system prompt, mode/lorebook bindings, pinned flag, chat suggestions | Room `ConversationEntity` |
| Messages | id, conversationId, role, content (text or JSON-serialized `UIMessage`), token counts, model id, reasoning level, tool calls/responses, attachments | Room `MessageNodeEntity` (+ payload blob for large content) |
| Provider usage | model, prompt tokens, completion tokens, timestamp, assistantId | Room `ProviderUsageEventEntity` |
| Favorites | conversationId, timestamp | Room `FavoriteEntity` |
| Folders | name, parent, timestamps | Room `FolderEntity` |
| Generated media | model, prompt, seed, timestamps, file path reference | Room `GenMediaEntity` |
| Workspace | per-conversation workspace metadata, cwd, .env/secrets references | Room `WorkspaceEntity` + on-disk `.orchards` files |
| Memories | assistantId, fact text, timestamps | Room `MemoryEntity` |
| Provider configuration | baseUrl, apiKey, custom headers, custom body, reasoning/streaming settings, OAuth clientId/secret/refreshToken for MCP | DataStore + `McpConfig.json` |
| AI assistant definitions | name, prompt, regex transforms, tool result retention settings | Room `AssistantEntity` (Kotlinx-serialization) |
| Voice activation | VoiceInteractionService prompt phrases | Resource bundled with APK |
| Onboarding | first-run completion flag, version | DataStore |
| Crash logs | Android system logs only; we do not embed a crash SDK | Device default |

These data classes are visible in `app/src/main/java/com/orchords/orchordsai/data/db/entity/*.kt`.

---

## 3. Permissions and why

| Permission | Why OrchordsAI requests it |
|---|---|
| `INTERNET` | Send your messages to the AI provider you configured. No default provider. |
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

## 4. AI provider and MCP data

When you configure an AI provider (OpenAI, Anthropic, Google, Ollama,
etc.) the app stores your API key, base URL, and custom headers in
**app-private** DataStore/JSON on your device. The key is never logged.

When you send a message, the app sends:

- Your current conversation context (messages, system prompt, tool
  definitions).
- Any file, image, voice transcript, or calendar event you explicitly
  attached to the message.
- An authorization header containing the API key you configured.

The remote provider's privacy policy then applies to that request. We
do not proxy or intercept the traffic. If you do not want a provider to
retain your data, configure that provider's "do not train" / "zero
retention" setting through your provider account.

MCP servers you configure behave the same way: the app makes HTTPS
calls directly to the MCP endpoint you chose and stores your MCP
credentials on-device. OAuth refresh tokens are stored in app-private
storage and are never sent to Orchords.com.

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

Android Auto Backup, cloud backup, and device-to-device transfer are
**disabled** (`res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`).
We do this because provider API keys, MCP OAuth tokens, and `.env`
files are stored in the same namespaces as ordinary settings; we will
not silently upload credentials to Google Drive or a new device.

If you want to move your data to a new device, use the in-app export
flow under **Settings → Backup & Restore** (ships in a follow-up
release; see `docs/DATA_DELETION.md` for the current workaround).

---

## 7. How to delete your data

The currently shipped deletion paths are:

- **Delete a conversation:** swipe it away in the conversation list, or
  open it and choose **Delete**.
- **Delete provider credentials:** Settings → **Providers → [name] →
  Remove**. The API key is wiped from storage immediately.
- **Revoke OAuth:** Settings → **MCP → [server] → Disconnect**.
- **Uninstall the app:** removes all on-device data automatically
  (recommended for a complete wipe today).

A **Settings → Storage → Clear all data** path that drops the Room
database, DataStore, workspace files, and cached media in one action is
tracked as a follow-up enhancement (`docs/DATA_DELETION.md` lists the
gap and the proposed UI). Until that lands, **uninstall + reinstall**
is the supported complete-wipe flow.

Because OrchordsAI has no backend account, there is nothing to delete
on a server. The path above is complete.

---

## 8. Children

OrchordsAI is not directed to children under 13. Do not let a child use
the app without parental supervision, because the AI provider you
configure may produce unfiltered content.

---

## 9. Changes to this policy

Material changes will be reflected by bumping the effective date above
and in-app via a privacy-policy update dialog. The current in-repo
source-of-truth is `docs/PRIVACY.md`; the URL you point the Play
listing at must serve the same content.

---

## 10. Contact

Privacy questions: open an issue at
<https://github.com/ORCHORDS/OrchordsStudioAi/issues> with the label
`privacy`.

Security issues: see `SECURITY.md`.
