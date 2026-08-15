---
status: Active
slug: 190-playlist-play-button-and-stems-cancel
related:
  - ../domain/publishing.md
  - ../domain/rendering.md
  - ../architecture/webvue3-patterns.md
  - ../features/101-audio-transpose-player.md
  - ../../specs/190-playlist-play-button-and-stems-cancel/spec.md
  - ../../archive/docs/features/playlist-play-button-and-stems-cancel.md
  - ../../archive/docs/features/playlist-play-button-and-stems-cancel.md
  - ../architecture/ci-cd-pipeline.md
---

# 190 — Плейлисты: запуск с любой песни, превью альбома/автора и фикс задвоения вейвформ (LiveDoc)

> Drill-down — [specs/190-playlist-play-button-and-stems-cancel/spec.md](../../specs/190-playlist-play-button-and-stems-cancel/spec.md)
> и [docs/features/playlist-play-button-and-stems-cancel.md](../../archive/docs/features/playlist-play-button-and-stems-cancel.md).

## What it does

Три фичи в одном релизе для публичного кабинета (`karaoke-public`):

1. **Запуск воспроизведения с любой песни плейлиста** — кнопка ▶ в каждой строке плейлиста
   (включая «Избранное»). Клик запускает именно эту песню как текущий трек, без
   необходимости предварительно нажимать «▶ Запустить плейлист» или перетаскивать песню
   в начало.
2. **Превью картинок альбома И автора в каждой строке** — две миниатюры (альбом 48×48
   слева, автор 120×48 справа, аспект 5:2) в общем `<div class="km-song-pictures">` с
   чёрным фоном и скруглением. Картинки/плейсхолдеры — без своего фона, со скруглением
   `border-radius: 6px`. CSS-плейсхолдер (иконка ♪/👤) при отсутствии файла или сетевой
   ошибке (`@error`).
3. **Фикс задвоения вейвформ при быстром переключении треков** — `AbortController` в
   `KaraokePlayer` отменяет все in-flight HTTP-запросы предыдущей песни в начале
   `playSong()`. Старая fetch/decode предыдущей песни не перезаписывает `accBuffer`/
   `vocBuffer` нового трека. Защитный destroy в `_buildWaveforms()` страхует от race
   с уже-отрисованными старыми вейвформами.

## User Stories (краткий список)

- **US1** (P1): запуск с любой песни плейлиста; toggle pause/resume на текущей играющей;
  ▶ disabled для muted/locked с tooltip.
- **US2** (P2): превью альбома и автора в каждой строке; плейсхолдер при пустом URL или
  сетевой ошибке; подзаголовок «Автор - год, альбом».
- **US3** (P1): фикс задвоения вейвформ при быстром переключении (≤2 canvas после
  spam-click по ▶); race-condition pushQueue↔playPos закрыт через `setTimeout(50ms)`.

## Functional Requirements (указатель)

- **FR-001..FR-005, FR-003a**: UI плейлиста — `PlaylistEditView.vue` (template +
  handler `onSongPlay` + CSS).
- **FR-006**: backend — 2 поля в `SitePlaylistItemDto` + заполнение в
  `PublicPlaylistController.playlistDetail()`.
- **FR-007..FR-009**: фикс вейвформ — `KaraokePlayer.js` (`_activeAbortController`,
  `_abortActive()`, signal во все fetch, guard после `decodeAudioData`,
  `_buildWaveforms` защита).
- **FR-010**: race-condition pushQueue↔playPos — `pushQueueDeferred()` (50 мс
  `setTimeout`) для `onReorder`/`toggleMute`/`removeItem`.
- **FR-011**: прогресс только текущей — закрыт через guard после `decodeAudioData`.
- **FR-012**: нет регрессии single-song — `_activeAbortController` создаётся в каждом
  `init()`, но `playSong()` вызывается только в плейлист-режиме.

## Acceptance Criteria

- [ ] **AC1** (US1): ▶ в любой строке плейлиста запускает именно эту песню как
      текущий трек (SC-001).
- [ ] **AC2** (US1): ▶ на текущей играющей — toggle pause/resume (FR-003a).
- [ ] **AC3** (US1): ▶ для muted/locked — disabled с tooltip «Эта песня пропускается —
      сначала включите её» (FR-004).
- [ ] **AC4** (US2): 100% строк показывают обе картинки или плейсхолдеры
      фиксированного размера; 0 битых изображений (SC-005).
- [ ] **AC5** (US2): подзаголовок формата «Автор - год, альбом»; разделитель
      ` - ` показывается только если есть что-то после (уточнение пользователя
      2026-08-14).
- [ ] **AC6** (US3): после spam-click по ▶ ровно 2 canvas в DOM (`#kp-ws-acc` +
      `#kp-ws-voc`); DevTools Network — запросы предыдущих песен canceled, не 200
      (SC-002/SC-003).
- [ ] **AC7** (US3): drag-drop сразу после клика ▶ не перетирает очередь плеера
      (`setTimeout(50)` перед `setqueue`, FR-010).
- [ ] **AC8**: single-song (`/player/:id` без `?pl=1`) — поведение идентично до
      фикса (FR-012).

## Related LiveDocs

- Domain: [publishing.md](../domain/publishing.md) (плейлисты — публикация пользовательских
  подборок), [rendering.md](../domain/rendering.md) (плеер, вейвформы).
- Architecture: [webvue3-patterns.md](../architecture/webvue3-patterns.md) (postMessage-
  мост между родителем и iframe плеера; применяется и в `karaoke-public` — единый паттерн).
- Feature: [101-audio-transpose-player.md](../features/101-audio-transpose-player.md) — тот
  же `KaraokePlayer.js`, тот же подход к UI-расширениям плеера.

## Code

- **Backend**:
  - `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SitePlaylistItem.kt` —
    +2 transient-поля (без `@KaraokeDbTableField`, не пишутся в БД).
  - `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SitePlaylistItemDto.kt` —
    +2 поля с дефолтом `""`.
  - `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlaylistController.kt` —
    +`authorPreviewUrl()` / `albumPreviewUrl()`; заполнение 2 полей в `playlistDetail()`.
- **Frontend**:
  - `karaoke-public/src/views/PlaylistEditView.vue` — кнопка ▶, два `<img>` с @error-fallback,
    `pushQueueDeferred` для FR-010, исправление подзаголовка.
  - `karaoke-public/src/player/KaraokePlayer.js` — `_activeAbortController`, `_abortActive()`,
    signal во все fetch, guard после `decodeAudioData`, защита в `_buildWaveforms()`.

## Архитектурные детали

- URL превью — **прямой на MinIO через nginx-прокси** `/minio/karaoke/<encoded>`
  (паттерн Pass 50 из `AuthorTilePublicDto.fromAuthorName` и
  `PublicPlayerController.pictureAlbumStorageKey`). Минует Spring-контроллер
  `/api/public/picture?file=...`, кэшируется nginx с `Cache-Control: public,
  max-age=86400`.
- Никаких HEAD-проверок `existsInMinIO` на бэкенде — фронт по `@error` показывает
  CSS-плейсхолдер (premium-плейлист до 200 песен = до 400 лишних HEAD).
- Формулы storage-ключей **предсказуемые**, без SQL-lookup:
  - Альбом: `${author}/${year} - ${album}/${author} - ${year} - ${album}.preview.album.png`
  - Автор: `${author}/${author}.preview.author.png`
- `decodeAudioData` (Web Audio API) **НЕ** поддерживает `AbortController` — Promise
  всё равно зарезолвится. Поэтому после decode явный guard `if (signal?.aborted) return null`,
  и `_loadAudio` игнорирует результат (не перезаписывает `accBuffer`/`vocBuffer`). Это
  закрывает первопричину бага с задвоением вейвформ.

## History

- Created: 2026-08-14
- Last updated: 2026-08-14
