# Tasks: 190-playlist-play-button-and-stems-cancel

**Input**: Design documents from `/specs/190-playlist-play-button-and-stems-cancel/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md
**Tests**: НЕ запрошены — Constitution §Тесты явно указывает, что проверка делается пользователем вручную (см. quickstart.md), CI-тесты в репозитории `@Disabled`.

**Organization**: задачи сгруппированы по user story (P1 → P2 → P1) для независимой реализации и проверки каждой истории.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно параллельно (разные файлы, нет зависимостей на не-завершённые задачи).
- **[Story]**: метка user story (US1, US2, US3).
- Все пути — абсолютные от корня репозитория.

## Path Conventions

Multi-module Gradle (karaoke-app, karaoke-web) + Vue 3 SPA (karaoke-public) — см. plan.md §Project Structure.

---

## Phase 1: Setup (Shared Infrastructure)

**Цель**: подтверждение, что стенд собирается; никаких новых проектов/модулей не создаём.

- [ ] T001 Проверить, что `./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel` собирается без ошибок в текущей ветке
- [ ] T002 Проверить, что `cd karaoke-public && npm run build` собирается без ошибок (baseline для последующих UI-изменений)

**Checkpoint**: стенд собирается — можно приступать к foundational-фазам.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Цель**: расширение DTO/entity — без этого ни US1 (UI-кнопка ▶ использует данные), ни US2 (UI-картинки), ни US3 (плеер читает обновлённое API) не могут быть завершены. **CRITICAL**: ни одна user story не может стартовать, пока эта фаза не завершена.

- [ ] T003 Добавить transient-поля `albumPictureUrl: String = ""` и `authorPictureUrl: String = ""` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SitePlaylistItem.kt` (entity-зеркало, БЕЗ `@KaraokeDbTableField`, не пишутся в БД, не участвуют в recordhash-sync)
- [ ] T004 Добавить те же 2 поля в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SitePlaylistItemDto.kt` (DTO, сериализуются в JSON, дефолт `""`)
- [ ] T005 [P] Реализовать `authorPreviewUrl(author: String): String` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlaylistController.kt` — формула `$author/$author.preview.author.png` → URLEncoder → `/minio/karaoke/<encoded>` (паттерн `AuthorTilePublicDto.kt:47-61`, Pass 50)
- [ ] T006 [P] Реализовать `albumPreviewUrl(song: Song): String` в том же файле — формула `${song.author}/${song.year} - ${song.album}/${song.author} - ${song.year} - ${song.album}.preview.album.png` → URLEncoder → `/minio/karaoke/<encoded>` (паттерн `PublicPlayerController.kt:265-267`)
- [ ] T007 В `playlistDetail()` того же контроллера (метод `GET /api/public/account/playlists/{id}`, строки 107-137): для каждого `itemsDto` заполнить `albumPictureUrl = albumPreviewUrl(s)` и `authorPictureUrl = authorPreviewUrl(s.author)` (поля передаются контроллеру уже сейчас — без отдельных SQL-запросов, формула детерминирована)

**Checkpoint**: backend-расширение готово — JSON-ответ `GET /api/public/account/playlists/{id}` содержит два новых поля для каждого item.

---

## Phase 3: User Story 1 — Запуск воспроизведения с любой песни плейлиста (Priority: P1) 🎯 MVP

**Goal**: пользователь может запустить воспроизведение любой песни в плейлисте одним кликом ▶ на её строке; работает toggle pause/resume на текущей играющей.

**Independent Test**: открыть плейлист из 3+ песен → кликнуть ▶ на 3-й песне → плеер начинает с неё; клик ▶ на той же строке во время игры → пауза, повторный клик → continue.

### Implementation for User Story 1

- [ ] T008 [US1] В `karaoke-public/src/views/PlaylistEditView.vue` добавить в template строки `<template #item>` новую кнопку `<button class="km-song-play" :disabled="..." :title="..." @click="onSongPlay(item)">▶</button>` между `<span class="km-song-num">` и `<div class="km-song-info">` (перед info, после badge)
- [ ] T009 [US1] В том же файле реализовать `onSongPlay(item)` в `setup()`:
  - если `item.muted` ИЛИ `readiness.stateFor(item.songId) === 'locked'` → ничего (disabled-кнопка не должна вызывать действие);
  - если `item.songId === currentSongId.value && isPlaying.value` → `send('toggle')` (handler уже есть в `PlayerView.vue:151`);
  - иначе → `send('setqueue', { ids: playableIds() })`, затем `send('playid', { songId: item.songId })` (handler уже есть в `PlayerView.vue:139-141`)
- [ ] T010 [US1] В том же файле добавить CSS-стили для `.km-song-play` в `<style scoped>`: 32×32 px, прозрачный фон, hover-эффект, `disabled { opacity: 0.4; cursor: not-allowed; }` (без inline-стилей — иначе сломается hover и tooltip)
- [ ] T032 [US1] В `karaoke-public/src/views/PlaylistEditView.vue` обеспечить отсутствие race-condition между `onSongPlay(item)` (US1) и `pushQueue()` (FR-010): после `send('playid', ...)` внутри `onSongPlay` НЕ вызывать `pushQueue()` синхронно — очередь формируется плеером внутри `playPos(p)`. Если пользователь делает drag-drop/mute **сразу** после клика ▶, `onReorder()`/`toggleMute()` отправляют `send('setqueue', ...)` **отложенно** через `setTimeout(..., 50)` (за это время `playPos()` завершает init и применяет свою очередь; наш `setqueue` уже идёт после). Без этого guard старая очередь от drag-drop может перезаписать новую, поставленную плеером.

**Checkpoint**: US1 полностью функциональна и проверяема изолированно (кнопка ▶ работает в любой строке; toggle pause на текущей; race с pushQueue устранён). MVP готов — можно деплоить.

---

## Phase 4: User Story 2 — Превью картинок альбома И автора в каждой строке (Priority: P2)

**Goal**: каждая строка плейлиста показывает превью альбома (48×48, слева) и превью автора (48×48, справа от альбома) с `@error` fallback на CSS-плейсхолдер.

**Independent Test**: открыть плейлист из 2+ песен → у каждой строки видны обе картинки (или плейсхолдеры); нет битых изображений, нет сдвигов вёрстки.

### Implementation for User Story 2

- [ ] T011 [US2] В `karaoke-public/src/views/PlaylistEditView.vue` добавить в template строки между `<span class="km-song-num">` и кнопкой `km-song-play` (T008) **контейнер `<div class="km-song-pictures">`** с чёрным фоном, содержащий оба превью-элемента:
  - `<img v-if="item.albumPictureUrl && !item._albumPictureFailed" class="km-song-cover" :src="item.albumPictureUrl" alt="" @error="item._albumPictureFailed = true" />`
  - `<div v-else class="km-song-cover km-song-cover-fallback" aria-hidden="true">♪</div>` — превью **альбома** слева (квадрат 48×48)
  - `<img v-if="item.authorPictureUrl && !item._authorPictureFailed" class="km-song-author" :src="item.authorPictureUrl" alt="" @error="item._authorPictureFailed = true" />`
  - `<div v-else class="km-song-author km-song-author-fallback" aria-hidden="true">👤</div>` — превью **автора** справа от альбома (аспект 5:2: width 120px, height 48px — пользователь уточнил 2026-08-14)
- [ ] T012 [P] [US2] В том же файле добавить CSS для контейнера `.km-song-pictures` (display: flex, gap: 5px, margin: 5px, padding: 5px, flex-shrink: 0); для `.km-song-cover`/`.km-song-cover-fallback` (48×48 px, `object-fit: cover`, `border-radius: 0`, `background: transparent`); для `.km-song-author`/`.km-song-author-fallback` (120×48 px, аспект 5:2, остальные стили — те же, без фона и без скругления — пользователь уточнил 2026-08-14); плейсхолдеры — центрированная иконка ♪/👤, color: #888, font-size: 1.3rem.
- [ ] T013 [P] [US2] В том же файле убедиться, что при `load()` (строка 228 в текущей версии) `items.value = body.items || []` корректно подхватывает 2 новых поля; при необходимости — сбросить `item._albumPictureFailed = false` / `item._authorPictureFailed = false` (эти transient-флаги не приходят с сервера, нужно проинициализировать)

**Checkpoint**: US2 полностью функциональна — обе картинки видны (или плейсхолдеры); fallback на ошибку работает.

---

## Phase 5: User Story 3 — Фикс задвоения вейвформ при быстром переключении (Priority: P1)

**Goal**: при переключении трека плеер немедленно отменяет все in-flight HTTP-запросы предыдущей песни; новые вейвформы рисуются ровно один раз.

**Independent Test**: на странице плейлиста spam-click ▶ по разным песням → в плеере ровно 2 вейвформы; в DevTools Network — запросы стемов предыдущих песен canceled, не 200; `document.querySelectorAll('#kp-ws-acc canvas, #kp-ws-voc canvas').length === 2`.

### Implementation for User Story 3

- [ ] T014 [US3] В `karaoke-public/src/player/KaraokePlayer.js` добавить поле `_activeAbortController = null` в `constructor()` (около строки 47 рядом с `audioCtx = null`)
- [ ] T015 [US3] В том же файле реализовать метод `_abortActive()`:
  - `if (this._activeAbortController) { this._activeAbortController.abort(); this._activeAbortController = null }`
- [ ] T016 [US3] В `init()` (строка 154) в самом начале, **после** `_buildUI()`, создать новый `this._activeAbortController = new AbortController()` (если старый ещё жив — старый автоматически перезаписывается; явный abort не нужен здесь, т.к. `playSong()` уже сделал это)
- [ ] T017 [US3] В том же `init()`:
  - `fetch(${this.apiBase}/${this.songId}/playerdata?...)` — добавить `{ ..., signal: this._activeAbortController.signal }`
  - В блоке `Promise.all([FontFace...])` (строка 220-237) — оставить без изменений (FontFace.load не принимает signal)
  - `this._loadImage(this.data.albumImageUrl)` (строки 239, 242) — добавить поддержку `signal` через второй параметр `_loadImage(url, signal)` (см. T018)
- [ ] T018 [US3] Изменить `_loadImage(url)` → `_loadImage(url, signal = null)` (строки 238-243): передать `signal` в `fetch(url, signal ? { signal } : undefined).then(r => r.blob()).then(blob => createImageBitmap(blob))` или эквивалент (используем существующую реализацию, см. `KaraokePlayer.js` — найти функцию и адаптировать)
- [ ] T019 [US3] В `_loadAudio()` (строка 1294):
  - `Promise.all([this._fetchAudio(...), this._fetchAudio(...)])` — передать `signal: this._activeAbortController.signal` в каждый вызов
- [ ] T020 [US3] В `_fetchAudio(url, onProgress, signal)` (строка 1338):
  - добавить параметр `signal`;
  - `fetch(url, signal ? { signal } : undefined)` — первая строка;
  - в начале функции — guard `if (signal?.aborted) return null` (если abort уже произошёл, нет смысла даже начинать);
  - в блоке чтения потока (`while (true) { ... reader.read() }`) — добавить обработку `AbortError` (return без throw — fetch уже прерван, нет смысла throw);
  - **критично**: после `decodeAudioData(all.buffer)` (строка ~1361) добавить guard `if (signal?.aborted) return null` — **до того**, как caller запишет результат в `this.accBuffer`/`this.vocBuffer`. `decodeAudioData` не поддерживает `AbortController`, поэтому Promise всё равно зарезолвится; но если мы уже aborted, мы не должны перезаписывать state плеера старыми буферами (это и есть первопричина бага R1 из research.md).
- [ ] T021 [US3] В `_buildWaveforms()` (строка 1420) **в самом начале** добавить защитный destroy:
  - `if (this.wsAcc) { this.wsAcc.destroy(); this.wsAcc = null }`
  - `if (this.wsVoc) { this.wsVoc.destroy(); this.wsVoc = null }`
  - `const ac = this.container.querySelector('#kp-ws-acc'); if (ac) ac.innerHTML = ''`
  - `const vc = this.container.querySelector('#kp-ws-voc'); if (vc) vc.innerHTML = ''`
  - (страховка от race, когда `_loadAudio()` старого трека ещё в полёте и уже мог дописать в `this.accBuffer`/`this.vocBuffer` после `playSong()`)
- [ ] T022 [US3] В `playSong()` (PlayerView.vue:1900 — вызывающий код) **первой строкой** добавить `await this._abortActive()` (метод из T015)
- [ ] T023 [US3] В `destroy()` (KaraokePlayer.js:3635) в самом начале добавить `this._abortActive()` (страховка от утечки на закрытие вкладки)

**Checkpoint**: US3 полностью функциональна — spam-click не приводит к задвоению вейвформ; все запросы предыдущих песен canceled; регрессии в одиночной песне (FR-012) нет.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Цель**: KDoc/JSDoc (Constitution VI FR-006), обновление LiveDocs (Constitution FR-014, если меняется bounded context или C4-уровень), ручная проверка по quickstart.md.

- [ ] T024 [P] KDoc на новые методы `authorPreviewUrl` и `albumPreviewUrl` в `PublicPlaylistController.kt` со ссылкой на `docs/features/playlist-play-button-and-stems-cancel.md` (или новый per-feature документ, если создаётся)
- [ ] T025 [P] JSDoc на новые поля `albumPictureUrl`/`authorPictureUrl` в `SitePlaylistItemDto.kt` (FR-006 Constitution VI — публичные API MUST иметь KDoc)
- [ ] T026 [P] JSDoc на новый метод `onSongPlay` в `PlaylistEditView.vue` со ссылкой на per-feature документ
- [ ] T027 [P] JSDoc на новые методы/поля `_activeAbortController`/`_abortActive()` в `KaraokePlayer.js` со ссылкой на per-feature документ
- [ ] T028 [P] Создать per-feature документ `docs/features/playlist-play-button-and-stems-cancel.md` (Constitution VI FR-009 — при правке кода одной из ключевых подсистем MUST обновить соответствующий per-feature документ)
- [x] T029 [P] Обновить `LiveDocs` (`livedocs/features/...`) — выполнено по запросу пользователя (не строго обязательно по governance, т.к. фича не меняет bounded context / C4-уровень, но улучшает discoverability). Добавлен `livedocs/features/190-playlist-play-button-and-stems-cancel.md` со frontmatter, related-links и 3 acceptance criteria; обновлён `livedocs/INDEX.md`. Все 3 LiveDocs CI проверки прошли ✅.
- [ ] T030 [P] Проверить линтеры: `./gradlew ktlintCheck`, `cd webvue3 && npm run lint` (baseline не должен расти), `cd karaoke-public && npm run lint` — без новых нарушений
- [ ] T031 Прогнать ручные сценарии из `specs/190-playlist-play-button-and-stems-cancel/quickstart.md` (только пользователь на dev-pc после деплоя; отметить чек-лист в quickstart)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: нет зависимостей — стартует немедленно.
- **Phase 2 (Foundational)**: зависит от Phase 1 — **БЛОКИРУЕТ** все user stories.
- **Phase 3 (US1, P1)**: зависит от Phase 2 — независима от US2 и US3.
- **Phase 4 (US2, P2)**: зависит от Phase 2 — независима от US1 и US3 (UI работает с DTO-полями, которые уже добавлены в Phase 2).
- **Phase 5 (US3, P1)**: зависит от Phase 2 — независима от US1 и US2 (фикс плеера не затрагивает UI плейлиста).
- **Phase 6 (Polish)**: зависит от завершения всех user stories, которые идут в релиз.

### User Story Dependencies

- **US1 (P1)**: Phase 2 → Phase 3. Не зависит от других stories.
- **US2 (P2)**: Phase 2 → Phase 4. Не зависит от других stories.
- **US3 (P1)**: Phase 2 → Phase 5. Не зависит от других stories.

US1 и US3 — оба P1; US2 — P2. Можно релизить:
- **MVP** (только US1, P1): Phase 1 + 2 + 3 → минимально полезная фича (кнопка ▶).
- **MVP+регресс-фикс** (US1 + US3): Phase 1 + 2 + 3 + 5 → кнопка ▶ без бага задвоения.
- **Полный релиз** (US1 + US2 + US3): все фазы → максимальная ценность.

### Within Each User Story

- US1: template (T008) → handler (T009) → CSS (T010). Все три — последовательные (один файл).
- US2: template (T011) → CSS (T012, parallel) → init-flag (T013, parallel).
- US3: поле (T014) → метод abort (T015) → init+fetch (T016-T020, последовательные в одном файле) → buildWaveforms-защита (T021) → playSong integration (T022) → destroy (T023).

### Parallel Opportunities

- **Phase 2**: T005 и T006 — параллельно (одна правка в одном файле, но логически независимы; можно править вместе одним edit). T003, T004 — параллельно (разные файлы).
- **Phase 3**: только последовательно (один файл `PlaylistEditView.vue`).
- **Phase 4**: T012 и T013 — параллельно (CSS + JS-логика в одном файле, но можно править параллельно).
- **Phase 5**: всё последовательно (один файл `KaraokePlayer.js`, кроме T022 который трогает `PlayerView.vue` — формально параллельно с T021).
- **Phase 6**: T024, T025, T026, T027, T028, T029 — все `[P]`, можно параллельно. T030 — последовательно (после всех правок). T031 — последовательно (после деплоя).

---

## Parallel Example: User Story 1

```bash
# Phase 3 — последовательно (один файл):
Task T008: "Добавить кнопку ▶ в template строки плейлиста в karaoke-public/src/views/PlaylistEditView.vue"
Task T009: "Реализовать onSongPlay(item) handler в karaoke-public/src/views/PlaylistEditView.vue"
Task T010: "Добавить CSS-стили для .km-song-play в karaoke-public/src/views/PlaylistEditView.vue"
```

---

## Parallel Example: Phase 2 (Foundational)

```bash
# Параллельно (разные файлы):
Task T003: "Добавить transient-поля в SitePlaylistItem.kt"
Task T004: "Добавить поля в SitePlaylistItemDto.kt"

# В одном файле, но логически независимо:
Task T005: "Реализовать authorPreviewUrl() в PublicPlaylistController.kt"
Task T006: "Реализовать albumPreviewUrl() в PublicPlaylistController.kt"
```

---

## Parallel Example: Phase 6 (Polish)

```bash
# Параллельно (разные файлы):
Task T024: "KDoc на authorPreviewUrl/albumPreviewUrl в PublicPlaylistController.kt"
Task T025: "JSDoc на новые поля в SitePlaylistItemDto.kt"
Task T026: "JSDoc на onSongPlay в PlaylistEditView.vue"
Task T027: "JSDoc на _activeAbortController/_abortActive в KaraokePlayer.js"
Task T028: "Создать per-feature документ docs/features/playlist-play-button-and-stems-cancel.md"
Task T029: "Обновить LiveDocs в livedocs/features/..."
```

---

## Implementation Strategy

### MVP First (только US1, P1)

1. Phase 1 — Setup.
2. Phase 2 — Foundational (DTO-поля, backend-URL).
3. Phase 3 — US1 (кнопка ▶ + handler).
4. **STOP and VALIDATE**: открыть плейлист, кликнуть ▶ на любой песне → играет с неё.
5. Деплой/демо — минимально полезная фича готова (без превью, без фикса задвоения).

### MVP + регресс-фикс (US1 + US3)

1. Phase 1 + 2.
2. Phase 3 (US1) → тест.
3. Phase 5 (US3) → тест (spam-click по ▶, проверка DOM-снимка вейвформ).
4. Деплой — обе P1-фичи, без US2 (превью).

### Полный релиз (US1 + US2 + US3)

1. Phase 1 + 2.
2. Phase 3 (US1).
3. Phase 4 (US2) — параллельно или после US1; тест.
4. Phase 5 (US3) — параллельно или после; тест.
5. Phase 6 (Polish) — KDoc/JSDoc, lint, ручная проверка по quickstart.
6. Деплой — все 3 истории.

### Incremental Delivery

- Каждая user story **добавляет ценность**, не ломая предыдущие:
  - US1 (P1) — без превью и без фикса задвоения; работающая кнопка ▶, но если быстро переключать — баг останется.
  - US3 (P1) поверх US1 — кнопка ▶ без бага.
  - US2 (P2) поверх US1+US3 — превью картинки.
- Можно релизить US1+US3 как hotfix (багфикс + минимальная фича), затем US2 как улучшение.

---

## Notes

- [P] tasks = разные файлы или независимые участки одного файла, нет cross-зависимостей.
- [Story] метка привязывает задачу к user story для traceability.
- Каждая user story завершаема и проверяема независимо (своим чек-листом в quickstart.md).
- Commit after each task or logical group (Phase 2 — один коммит «feat: add playlist item picture URLs»; Phase 3 — один «feat: per-row play button» и т.д.).
- Stop at any checkpoint to validate story independently.
- Avoid: vague tasks, same file conflicts (Phase 3 — один файл, все 3 задачи последовательные), cross-story dependencies that break independence.

---

## Summary

- **Total tasks**: 32
- **Phase 1 (Setup)**: 2
- **Phase 2 (Foundational)**: 5 (T003-T007)
- **Phase 3 (US1, P1 — MVP)**: 4 (T008-T010, T032)
- **Phase 4 (US2, P2)**: 3 (T011-T013)
- **Phase 5 (US3, P1 — регресс-фикс)**: 10 (T014-T023)
- **Phase 6 (Polish)**: 8 (T024-T031)
- **Suggested MVP scope**: Phase 1 + 2 + 3 (US1) — минимально полезный релиз «▶ на любой песне» (без превью и без фикса задвоения).
- **Suggested hotfix scope**: Phase 1 + 2 + 3 + 5 (US1+US3) — обе P1-фичи.
- **Full release**: все фазы 1-6.
