---
title: "ORCHORDS AI Figma Product Design"
owner: "Product & Design"
status: "proposed"
classification: "internal"
last-reviewed: "2026-09-11"
review-cycle: "90 days"
next-review: "2026-12-10"
---

# ORCHORDS AI Figma Product Design

## Purpose

Define the complete Figma design system, screen inventory, interaction model, and responsive behavior for ORCHORDS AI before implementation work begins.

The design must remain faithful to the existing product: ORCHORDS AI is a private-by-design, local-first Android AI workspace for user-selected models and services. It supports multi-provider chat, MCP tools with explicit approval boundaries, local persistence, voice, search, rich rendering, image and video workflows, and workspace tooling.

The product name shown in UI and design documentation is **ORCHORDS AI**. The brand line is **ORCHORDS — BUILD DIFFERENT.**

## Design strategy

Use a **faithful-evolution** approach rather than a ground-up conceptual redesign.

- Keep Chat as the primary entry point and main workspace.
- Preserve the existing product capabilities and mental model.
- Improve information hierarchy, navigation clarity, discoverability, consistency, accessibility, and large-screen behavior.
- Use progressive disclosure for advanced controls so power features remain available without overwhelming first-time users.
- Make local-first/privacy boundaries and tool approvals visually explicit where they affect user trust or action.
- Do not introduce a dashboard-first home screen that replaces Chat.
- Do not convert the Android app into a desktop-style shell on phone layouts.

## Verified implementation foundations

The existing Android application is Kotlin + Jetpack Compose and already uses Material Expressive.

The Figma system must mirror these implementation realities:

- Material Expressive component language.
- Dynamic Android color when supported.
- System, light, and dark color modes.
- AMOLED black background option in dark mode.
- Expressive motion semantics.
- Edge-to-edge Android layouts.
- Existing extended semantic color ramps for red, orange, green, blue, and gray.
- Existing surface-based hierarchy for top bars, cards, and list items.

The Figma file should therefore be implementation-oriented, not a disconnected visual concept.

## Source-of-truth policy

Before creating a custom component in Figma:

1. Inspect libraries available to the target Figma file.
2. Search Figma design-system assets for the needed Material/Android component, variable, or style.
3. Reuse a real library asset when an appropriate one exists.
4. Create an ORCHORDS component only when product-specific behavior or composition requires it.
5. Build product-specific components from library primitives where practical.
6. Avoid hand-drawn one-off substitutes for standard controls.

## Figma file structure

Create one primary design file named **ORCHORDS AI — Product Design** with these pages:

1. `00 Cover & Index`
2. `01 Foundations`
3. `02 Library Components`
4. `03 Chat`
5. `04 Conversations`
6. `05 Assistants`
7. `06 Create & Transform`
8. `07 Extensions & Workspace`
9. `08 Models, Providers & Services`
10. `09 Settings & Data`
11. `10 System States`
12. `11 Large Screen`
13. `12 Prototype Flows`
14. `99 Archive`

Every production-ready frame should use a stable, descriptive name and be grouped by feature and state.

## Foundations

### Color

Represent the implementation theme as semantic variables rather than hard-coded colors.

Required variable groups:

- `color/background`
- `color/surface`
- `color/surface-container`
- `color/surface-bright`
- `color/on-surface`
- `color/on-surface-variant`
- `color/primary`
- `color/on-primary`
- `color/secondary`
- `color/tertiary`
- `color/outline`
- `color/error`
- `color/error-container`
- extended red/orange/green/blue/gray ramps matching the application semantics

Modes:

- Light
- Dark
- AMOLED Dark

Dynamic color is represented as a documented runtime behavior, not a single fixed palette.

### Typography

Use the Material typography hierarchy already expected by Compose. Define text styles for display, headline, title, body, and label roles. Chat content must have a dedicated text/content style layer so conversation rendering can remain readable independently of chrome density.

### Shape

Use Material Expressive shape roles consistently. Product-specific containers may use expressive asymmetry or stronger rounding only when it improves hierarchy; avoid decorative shape changes that cannot map cleanly to Compose.

### Spacing

Use a 4 dp base grid with documented 4/8/12/16/24/32/48 spacing tokens. Interactive targets must meet Android accessibility expectations.

### Iconography

Preserve the product's existing icon language where practical. Standard platform actions should use library icons/components rather than custom drawings.

### Motion

Document transitions in terms implementable with Compose Material Expressive motion:

- shared-element emphasis where already conceptually present;
- short fade/scale for local state changes;
- directional page transitions for forward/back navigation;
- bottom-sheet and drawer motion using platform patterns;
- no ornamental motion that obscures state or delays action.

## Core component library

Build reusable components and variants for:

- Top app bars: small, medium/large flexible, contextual.
- Navigation drawer/sheet.
- Large-screen navigation rail/pane.
- Buttons: filled, tonal, outlined, text, icon, destructive.
- FAB and extended FAB where justified.
- Icon button and menu button.
- Text fields: single-line, multiline, search, prompt composer.
- Select/dropdown controls.
- Chips/tags: filter, status, capability, model/provider.
- Cards: standard, warning, update, backup reminder, service/configuration.
- List item and grouped list/card section.
- Dialogs and confirmation dialogs.
- Bottom sheets.
- Snackbar/toast treatment.
- Empty/loading/error/permission states.
- Avatar and assistant identity.
- Provider/model badge.
- Tool/MCP approval card.
- Message bubble/content block.
- Streaming response state.
- Attachment/file chip.
- Source/citation row.
- Rich content container for code, images, video, documents, and web results.
- Conversation list row with pinned/running state.
- Folder selector/chip.
- Assistant picker.
- Settings row and settings warning card.
- Statistics metric card.
- Workspace file row.
- Terminal command/result block.

Each component must define relevant states: default, pressed, focused, selected, disabled, loading, error, and destructive where applicable.

## Information architecture

### Primary phone experience

Chat remains the launch destination.

The chat drawer is reorganized into four visually distinct zones while preserving existing functionality:

1. **Identity & status** — avatar, nickname/greeting, update notice, backup reminder.
2. **Conversation management** — search/new chat actions, folders, pinned/recent conversations, running generation indicators.
3. **Current assistant** — assistant picker and direct assistant-settings entry.
4. **Product destinations** — Assistants, Translator, Image Generation, Favorites, Statistics, Settings.

Advanced or less-frequent destinations may be grouped under a clear `More` section/menu, but they must remain discoverable.

### Large-screen experience

At expanded widths, replace modal navigation with a persistent multi-pane layout:

- left navigation rail or drawer;
- optional conversation/folder pane;
- primary content pane;
- contextual inspector/details pane only when the feature benefits from it.

Chat should support a two- or three-pane composition without turning the phone design into a desktop clone.

## Screen scope

### Chat

Design all core chat states:

- new/empty conversation;
- active conversation;
- streaming response;
- stopped/failed response;
- tool invocation pending approval;
- tool running;
- tool success/failure;
- attachments before send;
- image/document attachment content;
- rich answer with code, sources, images, video, or web content;
- voice input/listening state;
- TTS playback state;
- conversation actions;
- model/provider selection;
- assistant selection;
- long conversation state;
- offline/network failure state;
- unsupported capability state.

The prompt composer must clearly separate content entry, attachments, voice, model/tool controls, and send/stop actions without crowding the phone layout.

### Conversations

Design:

- History.
- Search.
- Favorites.
- Folder filtering.
- Create/rename/delete folder.
- Pin/unpin conversation.
- Move conversation to folder.
- Move conversation to assistant.
- Regenerate conversation title.
- Delete confirmation.
- Running-generation indicator in lists.
- Empty and no-results states.

### Assistants

Design:

- Assistant list.
- Create/edit assistant.
- Assistant basic details.
- Prompt/instructions.
- Memory.
- Request settings.
- MCP configuration.
- Local tools.
- Extensions/injections.
- Assistant picker states.
- Assistant-linked conversations.

Assistant editing should use progressive disclosure and grouped sections rather than one dense form.

### Create & transform

Design the existing feature destinations:

- Translator.
- Image generation.
- Share/import handler states where user-facing.
- Web content view where user-facing.

Image generation should include prompt, model/service selection where applicable, generation state, result grid/detail, retry/error, and save/share actions supported by the product.

### Extensions, skills, and workspace

Design:

- Extensions landing page.
- Prompt extensions.
- Quick messages.
- Skills list.
- Skill detail.
- Workspace list.
- Workspace detail.
- Workspace file editor.
- Workspace terminal.

Workspace design must distinguish local files/actions from model-generated content and make potentially destructive actions explicit.

### Models, providers, and services

Design the complete settings flows represented in the application:

- Default models.
- Provider list.
- Provider detail/configuration.
- Search service list/detail.
- Speech/TTS configuration.
- MCP server configuration.
- Local web/server configuration.

Use consistent service cards with status, configuration state, credential state without exposing secret values, and test/error feedback.

### Settings

Preserve the verified top-level groups:

**General**

- Color mode.
- Preferences.
- Assistants.
- Extensions.

**Models & Services**

- Default model.
- Providers.
- Search service.
- Speech/TTS.
- MCP.
- Web server.

**Data**

- Backup.
- Chat storage/files.

**About**

- About ORCHORDS AI.
- Documentation.
- Request logs.
- Share app.

Also design the routed preference/detail pages already present in the app, including theme, general preferences, UI preferences, notifications, network preferences, files/storage, and other visible configuration screens.

A missing-provider warning state must remain prominent and actionable.

### Statistics and logs

Design:

- Statistics overview with readable metrics and time/context labels.
- Request/log list.
- Log detail if exposed by current product behavior.
- Empty/error states.

These views should remain diagnostic rather than adopting marketing-dashboard styling.

### Backup and data safety

Design:

- Backup overview.
- Backup reminder state.
- Export/import actions supported by the app.
- Progress state.
- Success/failure state.
- Destructive restore confirmation where relevant.

Use explicit local-data language and never imply cloud backup unless the implementation actually provides it.

## Trust and approval UX

MCP/tool actions that require approval must use a dedicated, visually unmistakable pattern.

Approval UI must show, when available from the product state:

- tool/server identity;
- requested action;
- relevant arguments in readable form;
- local/network/external boundary cue where meaningful;
- approve action;
- reject/cancel action;
- running state;
- success/failure result.

Dangerous or destructive tool actions must not visually resemble routine chat suggestions.

## System states

Create canonical patterns for:

- initial loading/migration;
- skeleton/loading;
- empty;
- search no-results;
- offline;
- network timeout;
- provider/model unavailable;
- missing configuration;
- permission required;
- denied permission;
- authentication required;
- rate limit/service failure;
- destructive confirmation;
- success confirmation;
- update available;
- backup recommended;
- safe-mode/crash recovery if user-facing.

## Responsive breakpoints

Produce at least these canonical frames:

- Compact phone: 360 × 800 dp reference.
- Standard phone: 412 × 915 dp reference.
- Foldable/tablet compact landscape: approximately 840 dp wide.
- Expanded tablet: approximately 1200 dp wide.

Figma frames are design references; Compose should still use adaptive layout logic rather than fixed device checks.

## Accessibility

- Minimum touch target: 48 dp where Android semantics require it.
- Maintain readable text contrast across light, dark, AMOLED, and dynamic-color scenarios.
- Do not use color alone for provider state, approval state, errors, or selection.
- Support large text without clipping critical controls.
- Keep destructive actions spatially and semantically distinct.
- Provide visible focus treatment for keyboard/large-screen interaction.
- Preserve logical reading and traversal order in multi-pane layouts.

## Prototype flows

Create clickable prototypes for these end-to-end scenarios:

1. Launch → new chat → choose model → send → streaming answer → tool approval → tool result.
2. Drawer → existing conversation → favorite/pin/move-to-folder.
3. Drawer → assistant picker → assistant settings → memory/MCP/tool configuration.
4. Settings → provider → configure provider → choose default model → return to chat.
5. Drawer → image generation → generate → result state.
6. Drawer/More → translator → translate.
7. Settings → MCP → configure server → return to chat → approval flow.
8. Extensions → skills → skill detail.
9. Extensions → workspace → file editor/terminal.
10. Settings → backup → successful backup/restore path.
11. Settings → theme/preferences → dark/AMOLED reference state.
12. Large-screen navigation between conversations and chat.

## Content rules

- Use **ORCHORDS AI** for product-facing names.
- Preserve third-party provider, protocol, dependency, model, and service names accurately.
- Do not fabricate provider capabilities, pricing, limits, cloud guarantees, or security guarantees.
- Do not expose example secret keys as if they were real credentials.
- Sample chat content should demonstrate product capability without implying unsupported functionality.

## Figma implementation rules

- Use Auto Layout for production components and screen structures.
- Use variables for semantic color and spacing where supported.
- Use component properties/variants for component states.
- Use nested library instances rather than detached copies when practical.
- Avoid unnecessary absolute positioning.
- Name layers and components semantically.
- Separate production-ready screens from exploration/archive material.
- Keep phone and large-screen variants linked through shared components.
- Document any component that intentionally diverges from an available Figma/Material primitive.

## Acceptance criteria

The design is complete when:

1. The Figma file is built from verified available Figma library assets wherever suitable.
2. Foundations reflect the existing Material Expressive, dynamic-color, light/dark/AMOLED implementation.
3. All existing major product destinations and routed configuration areas are represented.
4. Core components are reusable and variant-driven rather than copied one-offs.
5. Chat, conversations, assistants, tools/MCP, providers, settings, extensions/workspace, image generation, translator, backup, statistics, and logs each have primary, loading/empty, and failure states where applicable.
6. Tool approvals are unmistakable and action-safe.
7. Compact and expanded layouts are defined.
8. The key end-to-end flows are clickable in prototype mode.
9. Accessibility constraints are documented and visible in component decisions.
10. The output can be mapped back to Jetpack Compose without inventing a separate UI framework.

## Out of scope

- Changing model/provider protocol architecture.
- Changing persistence or repository boundaries.
- Inventing new cloud services.
- Removing existing product capabilities without a separate product decision.
- Rewriting application code as part of the Figma task.
- Producing marketing website or sales collateral in this design file.

## Brand

**ORCHORDS — BUILD DIFFERENT.**
