# Permissions Justification Sheet

**App:** OrchordsAI (`com.orchords.orchordsai`)
**Source of truth:** `app/src/main/AndroidManifest.xml`,
`docs/PRIVACY.md §3`.
**Last code-source review:** `git rev 16bce6c` (2026-09-14)

Google Play Console asks for a justification for every declared
permission. The text below is a copy/paste-ready sentence per
permission, suitable for the "Permission justification" field in the
App Content page. It is grounded in the actual code, not aspirational.

---

## Normal permissions

### `android.permission.INTERNET`
Used to send the user's messages, attached files, and attached voice
transcripts to the AI provider the user explicitly configured (OpenAI,
Anthropic, Google, Ollama, or any custom HTTPS endpoint). Also used to
reach MCP servers the user configured. No traffic is sent to any
server controlled by Orchords.com.

### `android.permission.ACCESS_WIFI_STATE`
Used to discover the local network the on-device `WebServerService` is
bound to so the user can pair a desktop browser. Also used to discover
local-network AI providers (e.g. an Ollama server on the LAN) for
auto-detection. No data is sent off-device by this permission.

### `android.permission.CHANGE_WIFI_MULTICAST_STATE`
Required by the mDNS/DNS-SD stack used to discover local-network AI
providers and to advertise the on-device `WebServerService`. Multicast
is LAN-only.

### `android.permission.ACCESS_LOCAL_NETWORK` (Android 13+)
Required to reach LAN-bound services such as an Ollama server or the
on-device `WebServerService` pairing endpoint. Without this permission
the OS rejects LAN connections on Android 13+.

### `android.permission.POST_NOTIFICATIONS` (Android 13+)
Optional. The app requests it so it can show a notification when a
background generation, transcription, or scheduled task completes. The
user can deny without losing core chat. The notification is shown only
for a user-initiated background task.

### `android.permission.POST_PROMOTED_NOTIFICATIONS` (Android 14+)
System-side escalation permission for the foreground-service
notification so the user always sees a visible notification while a
generation is running. Same justification as
`POST_NOTIFICATIONS`.

### `android.permission.FOREGROUND_SERVICE`
Allows a user-initiated generation, transcription, or scheduled task to
continue when the app is in the background. Without this, Android would
kill the work. The service always shows a foreground notification.

### `android.permission.FOREGROUND_SERVICE_DATA_SYNC`
Specific foreground-service type required on Android 14+ for
user-initiated sync-style tasks (transcription, scheduled retries).
Same justification as `FOREGROUND_SERVICE`.

### `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`
Specific foreground-service type required on Android 14+ for the
on-device `WebServerService` pairing endpoint and other custom
user-initiated long-running work.

### `com.android.alarm.permission.SET_ALARM`
Optional. The app uses it to schedule a reminder the assistant set up
through the in-app calendar/reminder tool. The user must explicitly
configure a reminder before this permission is exercised.

### `android.permission.WRITE_EXTERNAL_STORAGE` (declared with `maxSdkVersion="28"`)
Legacy storage write permission used only on Android 9 and below for
exports the user explicitly invokes (e.g. sharing a generated file).
On Android 10+ the app uses scoped storage via `FileProvider` and
`MediaStore`, so this permission is not declared on modern Android.
No shared storage access is performed automatically.

---

## Dangerous (runtime) permissions

### `android.permission.CAMERA`
Optional. Used only when the user taps the camera button to attach a
photo to a message. The app does not record video, does not access the
camera in the background, and does not save the captured image outside
the conversation it is being attached to.

### `android.permission.RECORD_AUDIO`
Optional. Used only when the user starts a voice session through the
voice-interaction service or the in-app microphone button. Recording
stops when the session ends or the user revokes the permission. The
app does not perform background recording.

### `android.permission.READ_CALENDAR`
Optional. Used only when the user grants calendar access through the
assistant-settings flow. Once granted, the assistant may include
upcoming events in prompts and may write events back. The user can
revoke access at any time through Android Settings or the in-app flow.

### `android.permission.WRITE_CALENDAR`
Optional. Used only after `READ_CALENDAR` has been granted, to let the
assistant write events it creates back to the user's calendar. Revoking
`READ_CALENDAR` also disables writes.

---

## Permissions explicitly NOT requested

For reviewer reference, OrchordsAI does **not** request:

- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`
- `READ_CONTACTS`, `WRITE_CONTACTS`, `GET_ACCOUNTS`
- `READ_SMS`, `SEND_SMS`, `RECEIVE_SMS`, `READ_CALL_LOG`, `READ_PHONE_STATE`
- `BODY_SENSORS`, `ACTIVITY_RECOGNITION`
- `READ_EXTERNAL_STORAGE` on modern Android (scoped storage is used)
- `SYSTEM_ALERT_WINDOW`, `REQUEST_INSTALL_PACKAGES`
- `BIND_ACCESSIBILITY_SERVICE` (the app does not run an accessibility service)
- `QUERY_ALL_PACKAGES`

If a future feature needs any of these, the corresponding permission
will be added to `AndroidManifest.xml` first, the privacy policy
updated, and this sheet updated before the feature ships.
