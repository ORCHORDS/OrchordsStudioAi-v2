# Data Deletion Path

**Status:** partial — covers current behavior, flags gap, and proposes
the follow-up enhancement.
**Last code-source review:** `git rev 7115142` (2026-09-15)

This document is the source of truth for the Google Play **Account
deletion / Data deletion** URL field and for the in-app disclosures
referenced from `docs/PRIVACY.md §7`.

---

## What works today

The following deletion actions are wired into the shipped UI:

| Action | Where | Implementation | Effect |
|---|---|---|---|
| Delete one conversation | Conversation list (swipe) and Chat header overflow | `ChatVM.deleteConversation` → `ConversationRepository.deleteConversation` (Room) | Removes `ConversationEntity`, child `MessageNodeEntity` rows, payload blobs, and FTS index entries |
| Delete all conversations of one assistant | Assistant settings → Delete assistant | `AssistantVM.deleteConversationOfAssistant` → `ConversationRepository.deleteConversationOfAssistant` | Same per-conversation wipe, scoped to the assistant id |
| Remove a provider configuration | Settings → Providers → [name] → Remove | `ProviderRepository.delete` → `SettingsStore.update` (which calls `ProviderSecretCodec.removeDroppedProviders` before redaction) | Wipes the baseUrl and custom headers/body from DataStore **and** removes the provider API key from the encrypted `EncryptedSharedPreferences` immediately — no re-save required. Covered by `ProviderSecretCodecTest.removeDroppedProviders*` (HEAD current; 11/11 green). |
| Disconnect an MCP server | Settings → MCP → [server] → Disconnect | `McpOAuthDiscoveryClient` + `McpConfig` mutation | Clears clientId, clientSecret, access/refresh tokens, OAuth state |
| Uninstall the app | Android system Settings | OS-level | Removes `/data/data/com.orchords.orchordsai/` and all on-device data |

All five are first-class, exercised by the unit test suite, and require
no server roundtrip.

---

## Gap: no global one-tap wipe

There is no single UI action today that drops the entire local data
store in one shot. A user who wants to wipe everything but keep the app
installed must perform every per-scope action above manually.

This is acceptable for the first Play submission (Play's data-deletion
requirement is satisfied by the uninstall path + per-scope UI), but it
is worth closing.

---

## Proposed follow-up: Settings → Storage → Clear all data

### UI

A new entry under **Settings → Storage** with a confirmation dialog:

```
Clear all data?

This will permanently delete every conversation, message, attachment,
provider key, MCP credential, memory, workspace, and cached generated
media stored by OrchordsAI on this device. You cannot undo this.

Anonymized crash-free usage counters will remain so we can measure
aggregate retention; nothing tied to you or your content is kept.

[Cancel]  [Clear all data]
```

The action is irreversible; the confirmation uses the same pattern as
the existing "Delete assistant" dialog.

### Implementation

1. New `LocalDataWipeRepository` that, inside a single
   `database.withTransaction { ... }`:
   - Drops all Room tables (`database.clearAllTables()`).
   - Deletes the on-device workspace root (`fileManager.workspacesDir`).
   - Empties the generated-media cache directory.
   - Calls `preferencesStore.clear()` for DataStore-backed preferences.
   - Removes MCP configs, OAuth state, and any persisted provider
     credentials.
2. Wire it into `ChatVM` or a new `SettingsVM`.
3. Show the dialog only after the user types "DELETE" in a text field
   (same anti-fat-finger guard as destructive actions elsewhere in the
   app).

### Tests to add

- `LocalDataWipeRepositoryTest` covering each storage namespace.
- A code-source regression test asserting the "Clear all data" entry
  and confirmation string live in `Settings/Storage`.

### Privacy-policy change after this ships

Update `docs/PRIVACY.md §7` to reference the new path; remove the
"uninstall is the supported complete-wipe flow" caveat.

---

## Account deletion

Play's policy also asks whether a "user account" can be deleted.
OrchordsAI has **no user account** — there is no email, no signup, no
server-side identity. Every byte of user-controlled data lives on the
device. The Play listing field for "Data deletion URL" should point at
this document.
