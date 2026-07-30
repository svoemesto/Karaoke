# Feature Specification: Audio Transpose for Premium Users

**Feature Branch**: `[095-transpose-audio]`

**Created**: 2026-07-30

**Status**: Draft

**Input**: User description: "Задача - транспонирование аудио от базовой тональности. Фича - премиальная. Пользователь должен выбрать в какую тональность транспонировать песню (для начала +/- 6 полутонов от базовой тональности). Продумать вариант делать это "на лету", без сохранения транспонированных стемов в хранилище (если сервер справится). Функционал переключения тональностей долен быть доступен премиум-пользователей из меню онлайн-плеера."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Transpose Song in Online Player (Priority: P1)

A premium user is listening to a karaoke track in the online player and wants to sing in a more comfortable key. The user opens the player’s tone/key menu, sees the current base key, and selects a transposition value (e.g., +2 semitones). The audio immediately starts playing in the new key without reloading the page. The user can change the key again at any time during playback.

**Why this priority**: This is the core value of the feature — allowing premium users to adjust the key on the fly while listening, directly addressing the primary user need.

**Independent Test**: A premium user can open any song in the online player, open the transpose menu, select a different key, and hear the audio transposed in real time. The change should be audible within 1 second.

**Acceptance Scenarios**:

1. **Given** a premium user is playing a song in the online player, **When** the user opens the transpose menu and selects +3 semitones, **Then** the audio playback continues in the transposed key with no audible gap longer than 1.5 seconds.
2. **Given** a premium user has already transposed a song by -2 semitones, **When** the user changes the transpose value to +1 semitone, **Then** the audio updates to +1 from the original base key (not cumulative) and playback resumes smoothly.
3. **Given** a premium user transposes a song, **When** the user refreshes the page, **Then** the player restores the last selected transpose value for that song (if persisted) or defaults to 0 semitones.

---

### User Story 2 - Free User Sees Premium Prompt (Priority: P2)

A free (non-premium) user opens the online player and notices the transpose menu in the player UI. When attempting to use it, the user sees a prompt or banner indicating that key transposition is a premium feature, with a call-to-action to upgrade.

**Why this priority**: This supports the business model by clearly communicating premium value to free users, potentially increasing conversion to premium. It can be delivered independently of the core transpose engine.

**Independent Test**: A free user can open any song in the online player, click the transpose menu, and see a premium upsell message instead of functioning transpose controls.

**Acceptance Scenarios**:

1. **Given** a free user is playing a song in the online player, **When** the user clicks the transpose menu, **Then** a premium feature prompt is displayed and no actual transposition occurs.
2. **Given** a free user sees the premium prompt, **When** the user dismisses it or clicks the upgrade CTA, **Then** the prompt closes or navigates to the premium subscription page without interrupting playback.

---

### User Story 3 - Display Base Key and Transposed Key (Priority: P3)

When the transpose menu is open, the user sees the song’s detected base key (e.g., "C major") and, for each transpose option, the resulting key name (e.g., "D major" for +2). This helps users who understand music theory select the correct key intuitively.

**Why this priority**: Enhances usability and reduces user error when selecting keys. Can be added after core transposition works.

**Independent Test**: A premium user opens the transpose menu and sees both the current base key and the target key names for each transpose option.

**Acceptance Scenarios**:

1. **Given** a song with a detected base key of "A minor", **When** the transpose menu is opened, **Then** the base key "A minor" is shown and each transpose option displays the corresponding target key name (e.g., +1 → "B♭ minor").
2. **Given** a song has no detected base key, **When** the transpose menu is opened, **Then** only semitone offsets are shown (e.g., "+1", "-2") without key names.

---

### Edge Cases

- What happens when the user selects the maximum transposition (+6 or -6) and the audio quality degrades?
- How does the system handle a song for which the base key is unknown?
- What happens if the server is under high load and real-time transposition introduces latency or stuttering?
- How does the player behave if the user rapidly switches between transpose values (e.g., toggling +1 and -1 repeatedly)?
- What happens to the transpose setting when a premium subscription expires while the user is mid-session?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow premium users to transpose audio playback by integer semitone values from -6 to +6, inclusive, relative to the song’s original base key.
- **FR-002**: The transpose control MUST be accessible from the online player’s menu (UI element TBD — e.g., settings or a dedicated key icon).
- **FR-003**: Transposition MUST be applied in real time ("on the fly") during playback without requiring a full page reload or pre-generation of transposed audio files.
- **FR-004**: The system SHOULD attempt real-time transposition server-side or client-side, depending on performance; if real-time processing is not feasible, the system MAY fallback to on-demand generation with brief buffering, but MUST NOT permanently store transposed stems in storage unless caching is explicitly approved later.
- **FR-005**: Free (non-premium) users MUST see a premium upsell prompt when attempting to access the transpose control, and MUST NOT be able to apply transposition.
- **FR-006**: The transpose menu MUST display the current base key (if known) and the resulting key name for each transpose option (e.g., "+2 → D major").
- **FR-007**: If the user changes the transpose value during playback, the audio MUST resume from the same approximate playback position, avoiding a restart from the beginning.
- **FR-008**: The default transpose value for any song MUST be 0 semitones (original key).
- **FR-009**: The system SHOULD remember the last selected transpose value per user per song for the duration of the session, and MAY persist it across sessions.

### Key Entities *(include if feature involves data)*

- **Song / Track**: Represents a karaoke song with a detected or manually set base key (e.g., "C major", "A minor"). The base key is used as the reference point for transposition calculations.
- **User (Premium/Free)**: The listener. Premium status determines whether transposition is functional or gated behind an upsell prompt.
- **Transpose Setting**: A per-song, per-user runtime preference holding the selected semitone offset (-6 to +6) and optionally the target key name derived from the base key.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Premium users can change the playback key and hear the transposed audio within 2 seconds of selection.
- **SC-002**: Transposition is available for 100% of songs that have separable instrumental and vocal stems in the online player.
- **SC-003**: The transpose feature does not cause perceptible audio gaps (>1.5 seconds) or dropouts during key switches for 95% of user interactions.
- **SC-004**: Free users who interact with the transpose control see a premium upsell prompt in 100% of cases, with a clear CTA to upgrade.
- **SC-005**: Users correctly identify the target key name in the transpose menu for at least 90% of songs where the base key is known.

## Assumptions

- Premium status is already determined by an existing subscription/role check in the online player; this feature reuses that check.
- The server has sufficient CPU/resources to perform real-time pitch shifting (e.g., via ffmpeg or a Web Audio API approach) for a reasonable number of concurrent premium users. If not, a fallback to on-demand generation with temporary caching (not persistent storage) will be considered.
- The song’s base key is either already stored in the database or can be derived from existing metadata (e.g., Sheetsage output). If unavailable, only semitone offsets are shown.
- The online player currently streams audio stems (instrumental and vocal) separately, making stem-level transposition feasible.
- Mobile browser support is required and Web Audio API or server-side stream manipulation must work on modern mobile browsers.
- No additional licensing or copyright implications arise from real-time transposition of already-licensed karaoke tracks.
