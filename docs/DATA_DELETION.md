# Data Deletion Path

**Status:** partial — current per-scope deletion paths are documented; a single in-app global wipe is still a separate gap.
**Last code-source review:** 2026-09-16

This document is the source of truth for local data-deletion behavior and
for the in-app disclosures referenced from `docs/PRIVACY.md`.

---

## What works today

| Action | Where | Implementation | Effect |
|---|---|---|---|
| Delete one conversation | Conversation list / chat deletion controls | conversation repository / Room | Removes the conversation and its associated persisted message data according to the repository deletion path. |
| Delete all conversations of one assistant | Assistant deletion flow | assistant/conversation repository | Removes conversations scoped to that assistant. |
| Remove a provider configuration | Settings → Providers → [name] → Remove | `SettingsStore.update` + `ProviderSecretCodec.removeDroppedProviders` | Removes provider configuration and the associated encrypted provider API credential before the Settings update is accepted. |
| Disconnect GitHub MCP | Settings → MCP → GitHub → Disconnect | `withGitHubMcpDisconnected()` → `SettingsStore.update` → `McpSecretCodec.redactServersForWrite` | Clears PAT/OAuth runtime values; encrypted MCP auth entries are removed while endpoint/toolsets/read-only/lockdown configuration remains. |
| Remove an MCP server | Settings → MCP → remove server | `SettingsStore.update` → `McpSecretCodec.redactServersForWrite` | Removes the MCP config and encrypted OAuth/header secret keys associated with that server id before persisting the new server list. |
| Clear/rewrite an MCP credential-bearing header | MCP editor | `SettingsStore.update` → `McpSecretCodec` | Replaces/removes the encrypted header entry in the same secure-store update that precedes DataStore persistence. |
| OAuth `invalid_grant` / revoked refresh token | automatic refresh path | `McpOAuthCoordinator.ensureFreshToken` → Settings update | Clears unusable access/refresh token state and transitions the connector to authorization-required rather than retaining stale credentials. |
| Uninstall the app | Android system Settings | OS-level | Removes the app-private data directory, including Room, DataStore and encrypted credential stores. |

The V7 settings migration also extracts legacy MCP OAuth/header secret
values from `MCP_SERVERS` JSON before stripping them from that JSON. It
fails closed if secure persistence is unavailable rather than silently
erasing a legacy credential.

Focused source tests exist for MCP redaction/hydration/drop/fail-closed
behavior and V7 migration. The complete Android-host verification gate
is `scripts/verify-github-connector-local.sh`; its execution is tracked
by issue #13.

---

## Portable backup / import boundary

The ordinary portable settings projection excludes `mcpServers` and
other authentication/connection objects. MCP OAuth tokens, client
secrets and header credentials therefore do not enter the normal JSON
backup path.

Import-overwrite/deletion behavior involving secure MCP entries must
remain covered by the connector security verification gate; an ordinary
backup/import flow must not recreate plaintext credentials.

---

## Gap: no global one-tap wipe

There is not yet a single in-app action that clears every local storage
namespace in one operation. A user who wants to wipe everything while
keeping the app installed must use the available per-scope deletion
controls.

For a complete device-local wipe today, uninstalling the app removes its
private storage.

---

## Proposed follow-up: Settings → Storage → Clear all data

A future global-wipe action should explicitly clear:

1. Room tables and associated payload data.
2. Workspace/generated-media/cache directories owned by the app.
3. DataStore preferences.
4. Provider encrypted credentials.
5. Secondary encrypted credentials, including WebDAV/S3/proxy/web-server
   secrets and all MCP OAuth/header entries.
6. Any other app-private files introduced before the feature ships.

It should have a clear destructive confirmation and focused tests for
every storage namespace. Do not claim that this path exists until it is
implemented and verified.

---

## Account deletion

The current Android product does not create an Orchords.com backend
account for the local app content described here. If account creation is
introduced before release, both the in-app and external account-deletion
requirements must be re-evaluated against the shipped product and the
current Google Play User Data policy.
