# ORCHORDS AI Planning Mode Design

## Goal

Add a first-class, conversation-scoped Planning Mode to ORCHORDS AI that lets the assistant inspect, research, clarify, and produce an implementation plan before execution, without creating a parallel model/provider stack.

Planning Mode is modeled after the plan-first workflow described in current OpenAI Codex guidance, but it is implemented entirely through ORCHORDS AI's existing mode-injection, conversation, tool-approval, Android, and web architectures.

## External behavioral references

Current references rechecked on 2026-09-16:

- OpenAI, "How OpenAI uses Codex": https://openai.com/business/guides-and-resources/how-openai-uses-codex/
- OpenAI Academy, Codex planning-mode guide: https://academy.openai.com/en/public/clubs/higher-education-05x4z/resources/codex-for-faculty-and-researchers-follow-along-guide-2026-06-09
- OpenAI, "How we used Codex to build Sora for Android in 28 days": https://openai.com/index/shipping-sora-for-android-with-codex/
- OpenAI Help, "ChatGPT Work and Codex": https://help.openai.com/en/articles/20001275/

The relevant public pattern is: inspect context first, produce/review a plan, then explicitly switch into execution. No undocumented OpenAI internals are copied or assumed.

## Existing repo architecture

The existing app already provides the primitives this feature needs:

- `Settings.modeInjections` stores mode definitions.
- `PromptInjection.ModeInjection` is the native mode representation.
- assistants store `modeInjectionIds`.
- conversations persist independent `modeInjectionIds`.
- `PromptInjectionTransformer` applies selected conversation/assistant injections.
- Android `ExtensionSelector` already edits conversation-scoped mode IDs.
- web chat input state, DTOs, and conversation routes already carry `modeInjectionIds`.
- `GenerationHandler` owns automatic tool execution and `needsApproval` gating.

Planning Mode must reuse these boundaries rather than add a new settings boolean, new model, or new conversation persistence field.

## Stable identity

Planning Mode uses stable UUID:

`164b9a03-828e-434e-8aa9-82c0e019a7fb`

Expose that id through one shared Android/Kotlin constant so prompt definition, runtime safety policy, UI, and tests use the same value.

## Built-in planning prompt

The built-in mode is named `Planning` and is injected after the system prompt.

The planning prompt must require the assistant to:

1. inspect available conversation context, repo/files, and tool capabilities before recommending changes;
2. separate verified facts from assumptions;
3. use current authoritative web/search evidence for external or time-sensitive facts when relevant;
4. ask only material clarification questions that would change architecture, safety, scope, or user-visible behavior;
5. produce an ordered plan containing scope, affected components/files when known, dependencies, risks, tests, and verification steps;
6. avoid claiming commands, edits, tests, commits, deployments, messages, purchases, or other actions occurred unless they actually did;
7. avoid autonomous state-changing actions during planning;
8. stop after the plan with an explicit handoff such as `Ready to execute`;
9. not expose hidden chain-of-thought or private scratchpad material.

The plan itself is ordinary assistant-visible content.

## Conversation state model

Planning Mode is ON when the effective conversation mode-id set contains the stable Planning Mode UUID.

Android and web controls must mutate the existing conversation-scoped `modeInjectionIds` state. They must not introduce a second planning boolean.

If conversation-scoped injection is unavailable for an assistant, the dedicated Plan control is disabled rather than silently mutating assistant defaults.

Turning Planning Mode off removes only the Planning Mode UUID and leaves other custom mode IDs untouched.

## Tool safety policy

A planning prompt alone is insufficient because `GenerationHandler` automatically executes tools whose `needsApproval` policy returns false.

When Planning Mode is active, ORCHORDS AI applies an additional approval overlay:

- clearly read-only built-in research/inspection tools may retain their existing automatic behavior;
- all MCP tools default to approval-required during planning, regardless of server-supplied annotations;
- workspace write/edit/shell tools are approval-required;
- unknown tools are approval-required;
- existing stricter approval policies remain stricter; Planning Mode can add approval requirements but never remove one.

Initial planning-safe automatic allowlist:

- `search_web`
- `scrape_web`
- `workspace_read_file`

No tool outside that allowlist becomes auto-approved merely because its name sounds read-only. Future additions require explicit tests and review.

User-approved tool execution remains possible through the existing approval UI, but Planning Mode must never silently execute a non-allowlisted tool.

## Android UI

Add a compact `Plan` toggle adjacent to the existing chat capability controls.

Requirements:

- visible active/inactive state;
- updates current conversation `modeInjectionIds` through the existing conversation update path;
- does not replace the generic Extensions selector;
- survives conversation reload because state is persisted on the conversation;
- disabled when conversation-scoped prompt injection is not allowed/available;
- uses existing Material/ToggleSurface patterns rather than a new control system.

## Web UI

Add equivalent Plan control in `web-ui/app/components/input/chat-input.tsx` near Search/Reasoning/MCP controls.

Requirements:

- state derives from the existing chat-input/conversation `modeInjectionIds`;
- mutation uses existing conversation injection/update plumbing;
- no parallel local-only planning state;
- reload/sync reflects persisted conversation mode ids.

## Built-in installation and upgrades

Existing installs must receive Planning Mode without duplicates.

Use the same built-in content merge/normalization path that protects other first-party modes. The stable UUID is the deduplication key. Built-in upgrades may refresh built-in name/content/position while preserving unrelated user-created modes.

## Testing

Required regression coverage:

1. stable UUID and built-in mode presence;
2. built-in installation/merge is idempotent;
3. planning prompt contains research-first, no-write, no-false-claim, explicit-handoff requirements;
4. runtime policy leaves the three explicit read-only allowlisted tools unchanged;
5. runtime policy forces approval for MCP, workspace write/edit/shell, and unknown tools;
6. planning-off behavior leaves existing tool approval behavior unchanged;
7. Android Plan control source/state path uses conversation `modeInjectionIds`;
8. web Plan control source/state path uses existing conversation/input mode ids;
9. removing Planning Mode preserves other mode ids.

## Non-goals

- no second model/provider;
- no hidden chain-of-thought surface;
- no automatic plan-to-execution transition;
- no replacement of custom Mode Injections;
- no weakening of connector permissions or approval policies;
- no new persistence column/field solely for Planning Mode.

## Definition of source-complete

Source implementation is complete when the stable built-in mode, planning-aware tool-approval overlay, Android Plan control, web Plan control, and focused tests are all present on `main`, issue #18 contains commit/source evidence, and master #5 reflects completion. Release-build verification remains centralized in master #5.