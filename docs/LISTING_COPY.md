# Play Store Listing Copy — en-US (Draft)

**App:** OrchordsAI (`com.orchords.orchordsai`)
**Source of truth:** `docs/PRIVACY.md`, `docs/PERMISSIONS.md`, `docs/DATA_SAFETY.md`,
`docs/CONTENT_RATING.md`, `app/src/main/AndroidManifest.xml`.
**Status:** **Draft.** Copy is sourced from the in-repo privacy and
permission documents above and is intentionally factual rather than
promotional. Any claim added before submission must be verifiable
against the shipped APK and the privacy policy URL.

Both fields are plain text entered into Play Console, not files in the
repo. The limits below are Play Console hard caps; the wording here is
within budget.

---

## 1. Short description (≤ 80 characters)

**Draft (78 characters):**

> Local-first AI chat. Your data stays on your device.

This is the same value proposition that `docs/PRIVACY.md` §1 grounds
("local-first Android app … no conversation messages … are transmitted
to any server controlled by Orchords.com"). It does not mention any
feature the shipping APK does not already expose (chat with an AI
provider you configure).

---

## 2. Full description (≤ 4000 characters)

**Draft (~1 350 characters, well under the 4 000 cap):**

> OrchordsAI is a local-first Android client for AI providers you
> configure. It does not run a backend: every conversation, every
> provider API key, and every MCP credential stays on your device
> unless you explicitly send it to a provider you chose.
>
> Configure an AI provider (OpenAI, Anthropic, Google, a local Ollama
> endpoint, or any provider that speaks the OpenAI or Anthropic HTTP
> API). Your provider API key is stored in Android Keystore-backed
> `EncryptedSharedPreferences` on the device and is never logged.
> Non-secret provider settings live in DataStore. When you send a
> message, OrchordsAI talks directly to that provider over HTTPS.
> Orchords.com does not proxy or inspect the traffic.
>
> What you can do:
>
> • Hold multiple conversations with branching message trees,
>   assistant definitions, and chat suggestions.
> • Attach images from your camera or gallery, calendar events, voice
>   transcripts, and files when you choose to.
> • Run generation or transcription in a foreground service so a
>   long-running task is not killed when your screen turns off.
> • Pair a desktop browser on the same Wi-Fi network through the
>   on-device web server, gated by a pairing token you see in-app.
> • Add Model Context Protocol (MCP) servers and OAuth-protected MCP
>   integrations; refresh tokens live in app-private storage.
> • Export your data through Settings; per-conversation delete and
>   provider removal wipe the encrypted credential immediately. A
>   one-tap global wipe is planned — see `docs/DATA_DELETION.md`.
>
> What OrchordsAI does not do:
>
> • No accounts, no signup, no analytics, no ads.
> • No crash-reporting SDK, no remote logging.
> • No background location, contacts, SMS, call log, microphone-while-
>   idle, or accessibility-service access.
> • Android Auto Backup, cloud backup, and device-to-device transfer
>   are disabled by default to keep your credentials on the device.
>
> Permissions are optional and requested with rationale. See the in-app
> privacy page for the full list and the privacy policy linked below.
>
> Source of truth for every statement above:
> https://github.com/ORCHORDS/OrchordsStudioAi-v2/blob/main/docs/PRIVACY.md

The structure follows `docs/PRIVACY.md` §1–§7 and §10 (issue tracker
contact) and `docs/PERMISSIONS.md`. The "What OrchordsAI does not do"
block intentionally mirrors the negative disclosures in PRIVACY.md so
the listing copy cannot drift out of sync with the policy without the
discrepancy being obvious.

---

## 3. Claims that must NOT be added before verification

To keep the listing truthful, do not paste any of the following
without shipping evidence:

- "Free" / "Pro" / "subscription" / "premium tier" wording — no paid
  digital feature is implemented in the current APK; Play Billing is
  not wired.
- "Multi-device sync" / "cloud sync" — backups and device-to-device
  transfer are explicitly disabled in `res/xml/backup_rules.xml` and
  `res/xml/data_extraction_rules.xml`.
- "Works offline with any provider" — only a self-hosted (e.g. local
  Ollama) provider works without internet; remote providers require
  network access.
- "Voice mode in the background" — voice input runs while the
  foreground service is active; it is not always-listening.
- Specific model counts, benchmark numbers, or comparison claims —
  there is no in-repo benchmark the listing can cite.
- Awards, "Editor's Choice", press quotes — none are sourced.

---

## 4. Pre-submission verification

Run from the repo root before submitting the listing copy:

1. `./gradlew :app:testDebugUnitTest` — must be green.
2. `./gradlew :app:lintDebug` — must be 0 errors.
3. Open `docs/PRIVACY.md` and the linked privacy-policy URL in a
   browser; the wording served at the URL must match the file in the
   repo byte-for-byte (excluding the effective-date line).
4. Re-check that `docs/PRIVACY.md` §1–§7 still describe the shipped
   APK; if any of the negative disclosures ("does not do") is no
   longer accurate, update both files in the same PR.

If any of those checks fail, treat this draft as stale and revise
before upload.
