# Planning Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a conversation-scoped Planning Mode that performs research/inspection first, produces a visible plan, and prevents silent execution of non-read-only tools until the user explicitly proceeds.

**Architecture:** Reuse `PromptInjection.ModeInjection` and conversation `modeInjectionIds` as the single state model. Add a stable built-in planning mode plus a planning-aware tool-approval overlay in `GenerationHandler`; add dedicated Android/web composer controls that mutate the same existing conversation state.

**Tech Stack:** Kotlin, Jetpack Compose, Kotlin serialization, existing ORCHORDS AI tool/runtime APIs, React/TypeScript web UI.

**Spec:** `docs/superpowers/specs/2026-09-16-planning-mode-design.md`

## Global Constraints

- Planning Mode UUID is exactly `164b9a03-828e-434e-8aa9-82c0e019a7fb`.
- Do not add a new provider/model or a new conversation persistence field.
- Planning Mode must reuse conversation `modeInjectionIds`.
- Planning Mode may add approval requirements but must never weaken an existing one.
- Automatic planning-safe allowlist is exactly `search_web`, `scrape_web`, `workspace_read_file` in the initial implementation.
- All MCP tools and unknown tools are approval-required while Planning Mode is active.
- Do not expose hidden chain-of-thought.
- Direct-to-`main` workflow is authorized; no PR/runners are required for source completion.

---

### Task 1: Built-in Planning Mode definition

**Files:**
- Create: `app/src/main/java/com/orchords/orchordsai/data/ai/planning/PlanningMode.kt`
- Modify: `app/src/main/java/com/orchords/orchordsai/data/datastore/PreferencesStore.kt`
- Test: `app/src/test/java/com/orchords/orchordsai/data/ai/planning/PlanningModeTest.kt`

**Interfaces:**
- Produces `PLANNING_MODE_ID: Uuid`.
- Produces `PLANNING_MODE_PROMPT: String`.
- Produces `planningModeInjection(): PromptInjection.ModeInjection`.
- Produces `Set<Uuid>.isPlanningModeEnabled(): Boolean`.

- [ ] **Step 1: Write failing tests** for stable id, prompt contract, and built-in mode shape.
- [ ] **Step 2: Verify tests fail** because `PlanningMode.kt` does not exist yet.
- [ ] **Step 3: Implement minimal planning definition** with stable UUID, name `Planning`, `AFTER_SYSTEM_PROMPT`, enabled=true, and the approved planning prompt.
- [ ] **Step 4: Add planning mode to `DEFAULT_MODE_INJECTIONS`** using the shared factory; preserve existing built-ins.
- [ ] **Step 5: Verify focused tests/source checks pass** or, if Android test execution is unavailable in this environment, record the exact limitation and perform source-level checks without claiming Gradle success.
- [ ] **Step 6: Commit** with message `feat(planning): add built-in planning mode`.

### Task 2: Planning-aware tool approval overlay

**Files:**
- Create: `app/src/main/java/com/orchords/orchordsai/data/ai/planning/PlanningToolPolicy.kt`
- Modify: `app/src/main/java/com/orchords/orchordsai/data/ai/GenerationHandler.kt`
- Test: `app/src/test/java/com/orchords/orchordsai/data/ai/planning/PlanningToolPolicyTest.kt`

**Interfaces:**
- Consumes `PLANNING_MODE_ID`.
- Produces `planningToolNeedsApproval(toolName: String): Boolean`.
- Produces `Tool.withPlanningApprovalOverlay(enabled: Boolean): Tool`.

- [ ] **Step 1: Write failing policy tests** proving `search_web`, `scrape_web`, `workspace_read_file` are not newly gated; `workspace_write_file`, `workspace_edit_file`, `workspace_shell`, `mcp__*`, and unknown names are gated.
- [ ] **Step 2: Verify RED** against missing policy implementation.
- [ ] **Step 3: Implement the policy** with explicit allowlist and conservative default.
- [ ] **Step 4: Wrap tool definitions in `GenerationHandler.generateText()`** when `conversationModeInjectionIds` contains Planning Mode; overlay must be `existingNeedsApproval || planningPolicy`.
- [ ] **Step 5: Verify planning-off behavior** leaves original `needsApproval` unchanged.
- [ ] **Step 6: Commit** with message `feat(planning): gate state-changing tools`.

### Task 3: Android conversation Plan control

**Files:**
- Create: `app/src/main/java/com/orchords/orchordsai/ui/components/ai/PlanningModeButton.kt`
- Modify: `app/src/main/java/com/orchords/orchordsai/ui/components/ai/ChatInput.kt`
- Test: `app/src/test/java/com/orchords/orchordsai/ui/components/ai/PlanningModeUiPolicyTest.kt`

**Interfaces:**
- Consumes `PLANNING_MODE_ID` and current conversation `modeInjectionIds`.
- Produces a callback with updated mode-id set; adding/removing Planning ID must preserve all other ids.

- [ ] **Step 1: Write failing source/policy tests** for dedicated Plan control, stable id usage, conversation-scoped update, and preservation of unrelated mode ids.
- [ ] **Step 2: Verify RED** before UI implementation.
- [ ] **Step 3: Implement `PlanningModeButton`** using existing `ToggleSurface` patterns with visible active state and text/icon semantics.
- [ ] **Step 4: Wire it into `ChatInput.kt`** adjacent to Search/MCP/reasoning controls using the existing conversation update callback/path; disable when conversation injection is unavailable.
- [ ] **Step 5: Keep `ExtensionSelector` unchanged** so custom modes remain available.
- [ ] **Step 6: Commit** with message `feat(planning): add Android Plan control`.

### Task 4: Web Plan control and state parity

**Files:**
- Create: `web-ui/app/components/input/planning-mode-button.tsx`
- Modify: `web-ui/app/components/input/chat-input.tsx`
- Modify only if required by current API shape: `web-ui/app/stores/slices/chat-input-slice.ts`
- Test: `web-ui/app/components/input/planning-mode-button.test.tsx` or existing project-equivalent test location.

**Interfaces:**
- Uses stable Planning Mode UUID as a web constant local to the component/module unless an existing shared generated contract is available.
- Consumes existing `modeInjectionIds` input/conversation state.
- Uses existing conversation injection update API/store action; no new planning boolean.

- [ ] **Step 1: Write failing state tests** for toggling Planning ID while preserving other IDs and reflecting persisted state.
- [ ] **Step 2: Verify RED** before component creation.
- [ ] **Step 3: Implement dedicated Plan button** near Search/Reasoning/MCP controls.
- [ ] **Step 4: Wire to existing mode-injection state/update flow**; do not fork conversation state.
- [ ] **Step 5: Verify reload/state derivation path** through source tests/current store behavior.
- [ ] **Step 6: Commit** with message `feat(planning): add web Plan control`.

### Task 5: Built-in upgrade/idempotence and cross-surface regression guard

**Files:**
- Modify or create focused tests under `app/src/test/java/com/orchords/orchordsai/data/datastore/` for built-in merge behavior.
- Create: `app/src/test/java/com/orchords/orchordsai/planning/PlanningModeSourcePolicyTest.kt` if a cross-file source policy test is the existing project pattern.
- Update: `docs/superpowers/specs/2026-09-16-planning-mode-design.md` only if implementation reveals a necessary design correction.

**Interfaces:**
- Built-in merge keyed by stable UUID must not duplicate Planning Mode.
- Existing user-created modes remain untouched.

- [ ] **Step 1: Add regression tests** for idempotent built-in installation/normalization and non-planning-mode preservation.
- [ ] **Step 2: Add cross-surface source policy checks** pinning Android/web controls to the Planning UUID and runtime overlay to conversation mode IDs.
- [ ] **Step 3: Run focused local/preflight checks available in the current environment**; do not claim unavailable Gradle/Node executions.
- [ ] **Step 4: Reread diffs for prompt safety, state duplication, and accidental tool-permission weakening.**
- [ ] **Step 5: Commit** with message `test(planning): pin mode persistence and safety`.

### Task 6: Tracking closeout

**Files:**
- GitHub issue #18
- GitHub master issue #5

- [ ] **Step 1: Comment #18** with exact commits, files, tests/checks, and current OpenAI behavioral-reference links.
- [ ] **Step 2: Rewrite #18 checklist** so completed source work is checked and release-only evidence is not used to keep the child open.
- [ ] **Step 3: Close #18 as completed** only after all source tasks above are present on `main`.
- [ ] **Step 4: Update master #5** to move #18 from active to completed child work and retain any release-build verification needed for the final app candidate.
