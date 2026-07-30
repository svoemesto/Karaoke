# Research: Audio Transposition Approaches

**Feature**: Audio Transpose for Premium Users  
**Date**: 2026-07-30

## Unknowns Resolved

### 1. How to transpose audio in real time without storing files

**Decision**: Primary approach — client-side pitch shifting via Web Audio API. Fallback — server-side ffmpeg stream processing.

**Rationale**:
- Web Audio API runs entirely in the browser, zero server CPU for the transpose operation, zero storage.
- Modern browsers (Chrome 90+, Firefox 88+, Safari 14.5+, Edge 90+) support Web Audio API with `AudioWorklet`, enabling low-latency pitch shifting in a dedicated audio thread.
- The existing player already streams stems (instrumental + vocal separately) — Web Audio API can process both streams independently and mix them in real time.
- Server-side fallback documented for mobile browsers where Web Audio performance may degrade, but v1 targets desktop-first with mobile as gradual enhancement.

**Alternatives considered**:
- **Pre-generate transposed stems with ffmpeg and cache in MinIO** — REJECTED: violates spec requirement (FR-004) to avoid persistent storage of transposed stems. Also creates 12 new files per song (±6 semitones × 2 stems), exploding storage.
- **Server-side on-the-fly ffmpeg via HTTP streaming (Icecast/HLS)** — REJECTED as primary: requires server CPU per stream, latency introduced by round-trip, adds infrastructure complexity. ACCEPTED as documented fallback only.
- **WebRTC or external pitch-shift SaaS** — REJECTED: violates Constitution Principle I (self-contained, no external SaaS in hot path).

### 2. Which client-side pitch-shift library/algorithm

**Decision**: Use Web Audio API `AudioWorklet` with a phase-vocoder pitch-shifter (e.g., `soundtouch-js` or custom `PitchShifterNode`).

**Rationale**:
- `soundtouch-js` is a mature, battle-tested port of the SoundTouch C++ library to JavaScript/Web Audio. Supports real-time streaming, arbitrary pitch ratios, works with `AudioWorklet` for glitch-free performance.
- Phase vocoder is the industry-standard algorithm for monophonic/polyphonic pitch shifting with acceptable quality for ±6 semitones.
- Alternative: custom FFT-based shifter — higher complexity, risk of artifacts, longer development time. Not justified for ±6 semitones where phase vocoder is sufficient.

**Alternatives considered**:
- **RubberBand WebAssembly port** — superior quality (time-stretch + pitch-shift), but WASM binary ~500KB, higher CPU usage. Overkill for pure pitch shift without time change.
- **Native `web audio pitch shifter` node** — no such native node exists; must be implemented via `AudioWorklet`.
- **OfflineAudioContext pre-render** — REJECTED: requires full download and offline processing, breaks "on the fly" requirement (FR-003).

### 3. How to display base key and transposed key names

**Decision**: Use a static mapping of semitone offset to key names based on the Circle of Fifths, computed client-side. No backend music-theory library needed.

**Rationale**:
- The base key string (e.g., "C major", "A minor") is already available from existing Sheetsage/database metadata.
- Transposed key = base key chromatic index + offset (mod 12), then map back to key name with enharmonic preference (sharps for sharp keys, flats for flat keys).
- A simple 12-element array of key names (with major/minor variants) covers all cases. No need for `music21` or `tonal` libraries.

**Alternatives considered**:
- **Backend music theory library (e.g., `tonal` npm package server-side)** — REJECTED: adds dependency and round-trip latency for a trivial calculation. Client-side array is sufficient.

### 4. Premium gating mechanism

**Decision**: Reuse existing premium check from `karaoke-public` store/state (already used for other premium features).

**Rationale**:
- The project already has a premium/role system. Transpose menu is conditionally rendered based on the same boolean flag / API response.
- No new authentication or authorization mechanism needed.

**Alternatives considered**:
- **Separate transpose-specific entitlement** — REJECTED: unnecessary complexity. The project uses a single premium tier currently.

### 5. Persisting user transpose preference

**Decision**: Store transpose offset in `localStorage` per user per song (simple key: `transpose_${songId}`). Optionally sync to backend via lightweight API if cross-device persistence is desired later.

**Rationale**:
- `localStorage` is zero-backend-cost, instant, and survives page refreshes.
- If premium users use multiple devices, an API endpoint (`POST /api/player/transpose`) can persist the value in the existing user profile table (already synced via SyncRegistry if needed).
- For v1, `localStorage` satisfies FR-009 (remember last selected value).

**Alternatives considered**:
- **Database-only persistence** — REJECTED: adds API latency for a UI preference that should be instant. Hybrid approach (localStorage first, lazy sync) is best.

## Summary of Decisions

| Area | Decision | Rationale |
|------|----------|-----------|
| Transpose engine | Client-side Web Audio API (`AudioWorklet` + phase vocoder) | Zero server cost, zero storage, real-time, meets "on the fly" requirement |
| Fallback | Documented server-side ffmpeg stream path | For mobile/low-power devices if client-side fails |
| Library | `soundtouch-js` or equivalent `AudioWorklet` pitch shifter | Mature, real-time, sufficient quality for ±6 semitones |
| Key name display | Client-side chromatic index mapping | Simple array, no backend dependency |
| Premium gate | Reuse existing premium flag | Already implemented, no new auth needed |
| Preference storage | `localStorage` per song, optional lazy backend sync | Instant, survives refresh, minimal backend surface |

## Risk Notes

- **Mobile Safari Web Audio limitations**: iOS Safari historically had `AudioWorklet` support gaps. Mitigation: test on iOS 15+; if issues arise, fall back to server-side stream or disable transpose on mobile temporarily.
- **Audio quality at ±6 semitones**: Phase vocoders introduce artifacts ("phasiness") at extreme shifts. Mitigation: cap at ±6 as specified; consider soft-warning UI at ±5/±6.
- **Rapid switching**: Rapid toggle between transpose values can cause audio glitches if not debounced. Mitigation: debounce user input by 300ms before applying shift.
