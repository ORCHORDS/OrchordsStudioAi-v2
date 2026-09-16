# Batch A map — MCP OAuth security

Status: mapped from current source plus current OAuth best-current-practice documents on 2026-09-16. Re-check sources before implementation.

## Current repo facts

1. `app/src/main/java/com/orchords/orchordsai/data/ai/mcp/McpConfig.kt` defines OAuth state containing `clientId`, `clientSecret`, `accessToken`, `refreshToken`, and `expiresAt`.
2. `app/src/main/java/com/orchords/orchordsai/data/ai/mcp/McpOAuthCoordinator.kt` passes the stored client secret and refresh token into refresh-token requests.
3. `SettingsStore` persists `MCP_SERVERS` as serialized JSON, so this path must be inspected carefully for plaintext credential persistence rather than assumed safe.
4. `SettingsBackupProjectionTest` already contains OAuth sentinel values, which gives us an existing regression surface for checking that exports/backups do not leak credentials.
5. Current repository search found no separate MCP secret store equivalent to provider/V6 secondary-secret storage.

## Standards that apply

- RFC 8252 (OAuth 2.0 for Native Apps): native apps should use an external user-agent rather than an embedded WebView, and public native clients must use PKCE.
  https://www.rfc-editor.org/info/rfc8252/
- RFC 9700 (OAuth 2.0 Security Best Current Practice): public-client refresh tokens must be sender-constrained or use refresh-token rotation; access-token privileges should be restricted to the minimum needed.
  https://www.rfc-editor.org/info/rfc9700/

## Work map

### A1 — persistence boundary
- [ ] Trace the complete encode/decode path from `McpOAuthState` -> `McpServerConfig` -> `SettingsStore.MCP_SERVERS`.
- [ ] Prove with a failing source/serialization test whether `clientSecret`, `accessToken`, and `refreshToken` currently reach DataStore JSON.
- [ ] Define a stable per-MCP-server secure key namespace for those credentials.
- [ ] Keep non-secret discovery/config metadata serializable; mark only credential-bearing fields transient or redact them before persistence.
- [ ] Fail closed: never remove a real token from DataStore unless secure persistence succeeded.

### A2 — migration
- [ ] Add the next DataStore migration only after confirming the current version and exact legacy JSON shape.
- [ ] Extract existing plaintext OAuth credentials into secure storage.
- [ ] Preserve non-secret MCP metadata byte-for-byte where possible.
- [ ] Refuse migration on secure-store failure rather than silently losing refresh/access tokens.
- [ ] Make migration idempotent and cover blank/missing fields.

### A3 — authorization-code flow
- [ ] Verify the authorization request actually opens in an external browser/custom tab, not an embedded WebView.
- [ ] Verify PKCE verifier/challenge generation, lifetime, binding to the pending authorization transaction, and cleanup after completion/failure.
- [ ] Verify redirect URI exact-match assumptions and app-link/custom-scheme handling against the actual providers supported by the MCP flow.
- [ ] Reject mismatched state/redirect responses and stale pending authorization state.

### A4 — refresh lifecycle
- [ ] Trace expiry calculation and refresh scheduling/trigger points.
- [ ] Confirm whether each authorization server rotates refresh tokens; if a refresh response contains a new refresh token, atomically replace the old value.
- [ ] On `invalid_grant`, revoked credentials, or replay indicators, clear unusable credentials and require re-authorization instead of retry-looping indefinitely.
- [ ] Avoid logging tokens/client secrets in request, exception, debug, or analytics paths.
- [ ] Ensure concurrent refresh attempts cannot overwrite a newer token set with an older response.

### A5 — logout/disconnect/deletion
- [ ] Verify disconnect deletes client secret, access token, refresh token, PKCE pending state, and any derived OAuth state from secure storage immediately.
- [ ] Verify deleting an MCP server wipes credentials even if the config object itself is removed before the next ordinary settings save.
- [ ] Verify reset/import-overwrite paths do not orphan old secure entries.

### A6 — backup/export/import
- [ ] Keep MCP OAuth credentials out of Android OS backup allowlists.
- [ ] Confirm first-party settings export/backup projections remove OAuth credentials.
- [ ] If credential export is ever desired, require a separately designed encrypted export rather than adding secrets to ordinary JSON.

## Focused verification to require before checking this batch off

- serialization/redaction test: persisted MCP JSON contains no `clientSecret`, `accessToken`, or `refreshToken` values;
- migration test: legacy plaintext -> secure store + redacted JSON, with fail-closed behavior;
- refresh rotation test;
- revoked/invalid refresh token test;
- concurrent refresh ordering test;
- disconnect/delete wipe test;
- backup/export projection test;
- local preflight plus focused JVM tests on a host Android toolchain.

## Repo evidence

- `app/src/main/java/com/orchords/orchordsai/data/ai/mcp/McpConfig.kt`
- `app/src/main/java/com/orchords/orchordsai/data/ai/mcp/McpOAuthCoordinator.kt`
- `app/src/main/java/com/orchords/orchordsai/data/datastore/PreferencesStore.kt`
- `app/src/test/java/com/orchords/orchordsai/data/sync/SettingsBackupProjectionTest.kt`
- `docs/DATA_DELETION.md`
- `docs/GOOGLE_PLAY_RELEASE_AGENDA.md`
