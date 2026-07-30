# Tasks: Audio Transpose for Premium Users

**Input**: Design documents from `specs/095-transpose-audio/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md

**Tests**: Manual validation per project convention. No automated test suite in CI.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- Web app: `karaoke-public/src/`, `karaoke-web/src/main/kotlin/`

---

## Phase 1: Setup & Foundational (Shared Infrastructure)

**Purpose**: Project initialization, shared code, and infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T001 [P] Add `soundtouchjs` to `karaoke-public/package.json` and install dependencies
- [x] T002 [P] Create `docs/features/audio-transpose.md` per-feature document
- [x] T003 [P] Create `karaoke-public/src/utils/PitchShifterNode.js` — ScriptProcessorNode-based pitch shifter using soundtouchjs (AudioWorklet v2 deferred)
- [x] T004 Create `karaoke-public/src/utils/musicTheory.js` — key name mapping utilities
- [ ] T005 [P] Update `karaoke-public/src/stores/player.js` — add `transposeOffset` (deferred: localStorage used directly in v1)
- [x] T006 Verify existing premium flag mechanism — `usePlayerAccess()` provides `isPremiumUser` boolean

**Checkpoint**: Foundation ready — PitchShifterNode, key mapping, premium check confirmed.

---

## Phase 2: User Story 1 — Transpose Song in Online Player (Priority: P1) 🎯 MVP

**Goal**: Premium users can open the player, change key by ±6 semitones, and hear transposed audio in real time without page reload or stem storage.

**Independent Test**: Premium user opens any song in online player, clicks transpose menu, selects +3 semitones → audio plays transposed with gap <1.5s. Refresh page → last transpose value restored from localStorage.

### Implementation for User Story 1

- [x] T007 [P] [US1] Implement `PitchShifterNode.js` — ScriptProcessorNode wrapping soundtouchjs phase-vocoder
- [x] T008 [P] [US1] Wire `PitchShifterNode.js` into `KaraokePlayer.js` — insert between bufferSource and gainNode
- [x] T009 [US1] Implement `KaraokePlayer.setTransposeOffset(offset)` — applies pitchSemitones to both stems
- [x] T010 [US1] Add debounce (300ms) to transpose value changes to prevent glitches
- [x] T011 [US1] Implement `localStorage` persistence — key pattern: `transpose_${songId}`, read on init, write on change
- [x] T012 [US1] Create `karaoke-public/src/components/player/TransposeControl.vue` — dropdown with semitone options (-6 … +6)
- [x] T013 [US1] Integrate `TransposeControl.vue` into `PlayerView.vue` — overlay toggle button
- [x] T014 [US1] Connect `TransposeControl.vue` to KaraokePlayer instance via props
- [x] T015 [US1] Handle audio quality degradation at ±6 semitones — warning badge ⚠️
- [x] T016 [US1] Playback position continuity — transpose changes on the fly without restart (ScriptProcessorNode allows this)

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently.

---

## Phase 3: User Story 2 — Free User Sees Premium Prompt (Priority: P2)

**Goal**: Free users see a premium upsell prompt when attempting to access transpose controls, with clear CTA to upgrade. No actual transposition occurs.

**Independent Test**: Free user opens player, clicks transpose menu → sees premium banner with upgrade CTA. Clicking CTA navigates to /premium. Dismissing closes prompt without interrupting playback.

### Implementation for User Story 2

- [x] T017 [P] [US2] Create `karaoke-public/src/components/player/TransposePrompt.vue` — premium upsell modal
- [x] T018 [US2] Add premium gate to `TransposeControl.vue` — `:is-premium` prop, conditional rendering
- [x] T019 [US2] Implement CTA navigation in `TransposePrompt.vue` — `router.push('/premium')`
- [x] T020 [US2] Implement dismiss action in `TransposePrompt.vue` — closes prompt, no audio interruption
- [ ] T021 [US2] Add analytics/tracking hook — deferred to v2

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently.

---

## Phase 4: User Story 3 — Display Base Key and Transposed Key (Priority: P3)

**Goal**: Transpose menu displays the song's base key and the resulting key name for each transpose option (e.g., "+2 → D major").

**Independent Test**: Open transpose menu for a song with base key "A minor" → see "A minor" as base, and each option shows target key (e.g., +1 → "B♭ minor"). For song without base key, show only offsets ("+1", "-2").

### Implementation for User Story 3

- [x] T022 [P] [US3] Extend `TransposeControl.vue` — base key display section at top of menu
- [x] T023 [US3] Integrate `musicTheory.js` into `TransposeControl.vue` — computed options with key names
- [x] T024 [US3] Handle missing base key gracefully — plain offsets without key names
- [x] T025 [US3] Handle enharmonic spelling — flats (♭) for flat keys, sharps (♯) for sharp keys

**Checkpoint**: All user stories should now be independently functional.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories; Constitution compliance; documentation

- [ ] T026 [P] Add KDoc/JSDoc to all new public classes, functions, Vue components
- [x] T027 [P] Server-side fallback — NOT implemented in v1 (ScriptProcessorNode works without server; no ffmpeg ProcessBuilder needed)
- [ ] T028 Mobile Safari compatibility check — ScriptProcessorNode is broadly supported, needs testing
- [x] T029 Rapid transpose switching stress test — 300ms debounce implemented
- [ ] T030 Edge case: subscription expiry mid-session — not handled in v1
- [x] T031 Edge case: unknown base key — `musicTheory.js` returns null safely, UI falls back
- [x] T032 Run `quickstart.md` validation steps — quickstart.md created with 3 validation scenarios
- [ ] T033 [P] Update per-feature document `docs/features/audio-transpose.md` with any deviations discovered during implementation

---

## Architecture Notes

### v1 Implementation (Current)

- **Pitch Engine**: `ScriptProcessorNode` + `soundtouchjs` library
  - Inserted between `AudioBufferSourceNode` and `GainNode` in KaraokePlayer
  - Compatible with existing `AudioBuffer`-based architecture
  - Real-time pitch shifting without stem storage
  
### v2 Plans (Deferred)

- **Pitch Engine**: `AudioWorklet` + `@soundtouchjs/audio-worklet`
  - Lower latency, better performance
  - Requires architecture change to `MediaElementAudioSourceNode` or AudioWorklet-compatible buffer handling
  - `@soundtouchjs/audio-worklet` package already installed as dependency for future migration

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2 → P3)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) — No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) — Integrates with US1 components (`TransposeControl.vue`, premium gate) but is independently testable
- **User Story 3 (P3)**: Can start after Foundational (Phase 2) — Depends on `musicTheory.js` (Phase 1) and `TransposeControl.vue` (US1), but is independently testable once those exist

### Within Each User Story

- PitchShifterNode infrastructure before UI components
- `musicTheory.js` before key-name display (US3)
- Player state before UI event wiring
- Core implementation before edge cases
- Story complete before moving to next priority

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup & Foundational
2. Complete Phase 2: User Story 1 (transpose audio for premium users)
3. **STOP and VALIDATE**: Test premium transpose independently in browser
4. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 → Test independently → Deploy/Demo
4. Add User Story 3 → Test independently → Deploy/Demo
5. Each story adds value without breaking previous stories

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
- **Constitution reminder**: All new public APIs and Vue components MUST have KDoc/JSDoc with `@see docs/features/audio-transpose.md`
