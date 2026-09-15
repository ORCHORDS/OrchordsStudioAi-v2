# Google Play Data Safety — Pre-filled answers

**App:** OrchordsAI (`com.orchords.orchordsai`)
**Source of truth:** `docs/PRIVACY.md`, `docs/DATA_DELETION.md`,
`app/src/main/AndroidManifest.xml`.
**Last code-source review:** `git rev 16bce6c` (2026-09-14)

This document lists the answers the Play Console **Data Safety** form
expects. Each section below maps to a section of the Play Console
questionnaire. Copy these answers when you fill the form; if any answer
stops matching the running app, that is a defect and the privacy policy
must be updated first.

---

## 1. Is your app collecting or sharing any of the required user data types?

### Data the app collects and stores on-device only

| Data type | Collected? | Shared? | Purpose | User controls |
|---|---|---|---|---|
| **Account info** | No | No | — | — |
| **App activity** (in-app actions, app diagnostics, crash logs) | App activity in-app actions: yes. Crash logs: no (no crash SDK is embedded). | No | App activity is stored locally to power the conversation history view, message navigation, and undo. | Delete conversation; uninstall |
| **App info and performance** (crash logs, diagnostics) | No (no third-party SDK collects crash logs) | No | — | — |
| **Audio recordings** | Only if user starts a voice session | Sent to AI provider you configured | Voice-to-text transcription within the assistant | Delete conversation; uninstall |
| **Calendar events** | Only if user grants calendar access through assistant settings | Sent to AI provider you configured when assistant uses calendar context | Calendar context in prompts; optional write-back | Revoke calendar permission in Android Settings or in-app |
| **Contacts** | No | No | — | — |
| **Files and docs** | Only if user attaches a file | Sent to AI provider you configured when attached | Attachments in messages | Delete conversation; uninstall |
| **Health and fitness** | No | No | — | — |
| **Location** | No (no location permission requested) | No | — | — |
| **Messages** (chat content) | Yes — every message and AI response | Sent to AI provider you configured | Core chat feature | Delete conversation; uninstall |
| **Photos and videos** | Only if user taps the camera button to attach | Sent to AI provider you configured when attached | Attachments in messages | Delete conversation; uninstall |
| **Voice or sound recordings** | Only if user starts a voice session | Sent to AI provider you configured | Voice-to-text transcription | Delete conversation; uninstall |

### Data the app does NOT collect

- No analytics SDK.
- No advertising SDK.
- No crash-reporting SDK (we rely on the Android system default; no third
  party receives crash data).
- No location services.
- No background sensors (microphone is foreground-only while a session is
  active).

### Encryption in transit

- All traffic to the AI provider you configure uses HTTPS — the app
  refuses to send requests to plain-HTTP endpoints unless the user
  explicitly toggles "Allow insecure transport" per provider.
- The on-device `WebServerService` is bound to the LAN and refuses
  non-LAN connections; it uses HTTP only because the LAN is trusted, and
  requires a pairing token shown on the app screen.

### Encryption at rest

- App-private storage on Android is encrypted by the device-level file-
  based encryption (FBE) that ships on every Android 6+ device OrchordsAI
  supports (`minSdk = 26`).
- Provider API keys are additionally protected in an Android Keystore-
  backed `EncryptedSharedPreferences` file
  (`app/src/main/java/com/orchords/orchordsai/data/security/ProviderCredentialStore.kt`,
  AES-256 GCM via `MasterKey`). Non-secret provider settings remain in
  ordinary DataStore/JSON.
- Conversation content, voice transcripts, generated media, and the
  provider/MCP credential files are stored in app-private storage
  protected by OS-level FBE only.

### User can delete data

- See `docs/DATA_DELETION.md`. Per-conversation delete, per-provider
  credential remove, MCP disconnect, and uninstall all wipe the
  relevant data immediately and irreversibly.

---

## 2. Data shared with third parties

| Third party | What is shared | When | User control |
|---|---|---|---|
| AI provider you configured (OpenAI / Anthropic / Google / Ollama / custom) | Your messages, attached files, attached voice transcripts, the API key you provided | Every message you send | Choose a different provider; switch to a local-only provider (Ollama); remove the provider from the app |
| MCP servers you configured | Your MCP-authenticated requests | Every MCP tool call you trigger | Disconnect MCP server; remove MCP server from config |
| Google Play Services (only if installed) | Standard APK install attribution and update checks | Implicit through Play Services on the device | Disable Play Services on the device |

There are **no other third-party recipients**. Orchords.com does not
operate a backend that receives user content.

---

## 3. Security practices

- Data is encrypted in transit (HTTPS to provider and MCP servers).
- Data is encrypted at rest (Android FBE).
- Users can request data deletion (per-scope UI plus uninstall).
- The app does not target children under 13.
- The app does not contain ads.
- The app does not contain in-app purchases.
- `targetSdk = 37`, `compileSdk = 37`, `minSdk = 26` (meets Play target-API policy).
- Android Auto Backup and device transfer are allowlist-only
  (`backup_rules.xml`, `data_extraction_rules.xml`): only UI-only
  `orchordsai.preferences` are marked portable. Conversation data,
  generated media, the provider-key encrypted store, and the
  provider/MCP credential files are not matched by any `<include>` and
  are therefore not uploaded to Google Drive or carried across to a new
  device.

---

## 4. Account deletion

Play asks for an "Account deletion" URL even if your app has no
accounts. OrchordsAI has no user account; point the field at
`docs/DATA_DELETION.md` (host the same file at the URL you use for the
Privacy Policy).

---

## 5. Reviewer notes for the form

- "Do you collect or share any of the required user data types?" → **Yes**
  (Messages, App activity, plus the optional Audio/Photos/Calendar/Files
  that the user attaches).
- "Is all of the user data collected by your app encrypted in transit?"
  → **Yes** (HTTPS to configured provider; pairing-token-protected HTTP
  on the LAN only).
- "Do you provide a way for users to request that their data is
  deleted?" → **Yes** (per-scope UI plus uninstall; see
  `docs/DATA_DELETION.md`).
