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
explicitly export/share it or when a configured external service needs
the data to perform the action you requested.

| Category | Concrete fields | Where |
|---|---|---|
| Conversations | id, assistantId, title, node list, timestamps, custom system prompt, mode/lorebook bindings, pinned flag, chat suggestions | Room `ConversationEntity` |
| Messages | id, conversationId, role, content (text or JSON-serialized `UIMessage`), token counts, model id, reasoning level, tool calls/responses, attachments | Room `MessageNodeEntity` (+ payload blob for large content) |
| Provider usage | model, prompt tokens, completion tokens, timestamp, assistantId | Room `ProviderUsageEventEntity` |
| Favorites | conversationId, timestamp | Room `FavoriteEntity` |
| Folders | name, parent, timestamps | Room `FolderEntity` |
| Generated media | model, prompt, seed, timestamps, file path reference | Room `GenMediaEntity` |
| Workspace | per-conversation workspace metadata, cwd, `.env`/secret references | Room `WorkspaceEntity` + on-disk `.orchords` files |
| Memories | assistantId, fact text, timestamps | Room `MemoryEntity` |
| Provider configuration | non-secret provider metadata/settings | app-private DataStore |
| Provider API credentials | provider API key | encrypted credential store backed by Android Keystore |
| MCP configuration | endpoint, transport, server name, enabled tools, non-secret OAuth discovery metadata and safety settings | app-private DataStore |
| MCP credentials | OAuth client secret, access/refresh tokens and user-supplied MCP header values such as GitHub Authorization/PAT | encrypted `orchordsai_secondary_secrets` store backed by Android Keystore; hydrated into memory only when needed |
| AI assistant definitions | name, prompt, regex transforms, tool result retention settings | Room `AssistantEntity` (Kotlinx serialization) |
| Voice activation | VoiceInteractionService prompt phrases | Resource bundled with APK |
| Onboarding | first-run completion flag, version | DataStore |
| Crash logs | Android system logs only; we do not embed a crash SDK | Device default |

Relevant implementations include `ProviderCredentialStore`,
`SecondarySecretStore`, `ProviderSecretCodec`, `SecondarySecretCodec`,
`McpSecretCodec`, and `PreferenceStoreV7Migration`.

---

## 3. Permissions and why

| Permission | Why OrchordsAI requests it |
|---|---|
| `INTERNET` | Send your messages to the AI provider or MCP server you configured. |
| `CAMERA` | Optional: capture an image to attach to a message (only when you tap the camera button). |
| `RECORD_AUDIO` | Optional: transcribe voice input to text or run the bundled voice-interaction service (only when you start a voice session). |
| `READ_CALENDAR` | Optional: include calendar context in your prompts (only when you grant access through the assistant-settings flow). |
| `WRITE_CALENDAR` | Optional: write events the assistant creates back to your calendar. |
| `POST_NOTIFICATIONS` (Android 13+) | Optional: notify you when a background generation, transcription, or scheduled task completes. You can deny without losing core chat. |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` / `FOREGROUND_SERVICE_SPECIAL_USE` | Run a user-initiated generation/transcription session in the foreground so the OS does not kill it when the screen is off. The foreground notification is visible. |
| `ACCESS_WIFI_STATE` / `CHANGE_WIFI_MULTICAST_STATE` / `ACCESS_LOCAL_NETWORK` | Discover local-network providers and support the optional on-device web server. |
| `SET_ALARM` | Optional: schedule a reminder the assistant set up. |
| `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion="28"`) | Legacy read/write access for older Android versions; superseded by scoped storage on modern Android. |

No background location, contacts, SMS, call log, or accessibility-service
permission is requested.

---

## 4. AI provider and MCP data

Provider API keys are kept outside ordinary Settings JSON in an
encrypted credential store. Provider configuration persisted in
DataStore contains non-secret metadata; runtime code hydrates the API
key only when the provider is used.

When you send a message, the app may send to the provider you chose:

- your current conversation context;
- files/images/voice transcripts/calendar data you explicitly attach or authorize;
- the authorization credential required by that provider.

The remote provider's privacy policy applies to that request. OrchordsAI
does not proxy the traffic through Orchords.com.

MCP servers behave similarly: the app connects directly to the MCP
endpoint you configured. Beginning with the V7 settings migration,
OAuth `clientSecret`, access/refresh tokens and MCP HTTP header values
are stripped from ordinary `MCP_SERVERS` DataStore JSON and stored in
the Android-Keystore-backed secondary secret store. The app hydrates
those values into the runtime MCP configuration only when needed.

For the first-class GitHub connector, the PAT is represented at runtime
as an Authorization header, but the persisted Settings JSON contains a
blank header value. Read-only/toolset/lockdown configuration remains
non-secret metadata.

MCP/OAuth diagnostic text is sanitized before it is surfaced through
the MCP status UI/clipboard and the affected MCP Logcat paths so known
credential forms are not intentionally emitted as diagnostics.

---

## 5. On-device web server

OrchordsAI exposes an optional local HTTP server (`WebServerService`) so
you can use a browser on the same network with the app. The server is
off by default. Its access password is also part of the encrypted
secondary-secret boundary rather than ordinary Settings JSON.

---

## 6. Backups and device transfer

Android backup rules use an explicit allowlist. Credential stores are
not included in the allowed backup surface. The app's ordinary portable
settings projection also excludes provider credentials, MCP servers and
other connection/authentication objects; it does not export MCP OAuth
or header secrets.

If an encrypted credential-export feature is ever added, it must be a
separately designed opt-in format. Secrets must not be added to the
ordinary portable JSON backup.

---

## 7. How to delete your data

Current deletion paths include:

- **Delete a conversation:** use the conversation deletion controls.
- **Delete provider credentials:** remove the provider configuration;
  its encrypted API key is removed with it.
- **Disconnect the first-class GitHub MCP connector:** Settings → MCP →
  GitHub → **Disconnect**. This clears GitHub PAT/OAuth material while
  retaining the non-secret connector endpoint/toolset/safety profile.
- **Remove an MCP connector:** delete/remove the MCP configuration. The
  MCP secret codec removes the encrypted OAuth/header entries associated
  with that server id before the Settings update is persisted.
- **Uninstall the app:** Android removes the app's private storage,
  including Room/DataStore/credential stores.

A one-tap in-app wipe of every local namespace is still tracked as a
separate product gap in `docs/DATA_DELETION.md`. Until that exists,
uninstall remains the complete device-local wipe.

OrchordsAI currently has no Orchords.com backend account containing the
local app content described above.

---

## 8. Children

OrchordsAI is not directed to children under 13. Do not let a child use
the app without parental supervision, because the configured AI
provider may produce unfiltered content.

---

## 9. Changes to this policy

Material changes will be reflected by updating this document and the
matching in-app/Play listing privacy-policy copy. The published HTTPS
privacy-policy URL must serve equivalent current content before release.

---

## 10. Contact

Privacy questions: open an issue at
<https://github.com/ORCHORDS/OrchordsStudioAi-v2/issues> with the label
`privacy`.

Security issues: see `SECURITY.md`.
