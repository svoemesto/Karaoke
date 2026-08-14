---
status: Active
slug: 131-fix-approve-demo-render-telegram-sync
related:
  - ../domain/editorial.md
  - ../domain/processing.md
  - ../domain/publishing.md
  - ../features/184-approve-status-choice.md
  - ../architecture/data-sync.md
  - ../../specs/131-fix-approve-demo-render-telegram-sync/spec.md
---

# 131 — Авто-пайплайн публикации после approve (LiveDoc)

> Drill-down — [specs/131-fix-approve-demo-render-telegram-sync/spec.md](../../specs/131-fix-approve-demo-render-telegram-sync/spec.md).

## Что делает

При апруве песни в заданиях редактора (`POST /api/songeditor/approve`):
1. **Работает**: задание на сервере помечается `admin_status='approved'`, на
   локальной БД песня поднимается до idStatus=6, **срабатывает триггер
   новости «в коллекции»**.
2. **НЕ работает** (до фикса):
   - Не создаётся **задание на рендер DEMO** (`RenderMp4Demo`).
   - Не публикуется **в Telegram** (новая демо-версия).
   - Не синхронизируется `tbl_songs` на сервер через `updateRemoteSongFromLocalDatabase`,
     что блокирует триггер `newsAvailableAnnounced: false → true` на сервере.

**Фикс**:
1. После approve создавать `KaraokeProcess.submit(RENDER_MP4_DEMO, ..., threadId=HEAVY_RENDER)`.
2. По завершении рендера — publish в Telegram через `TelegramPublishService`.
3. Явный вызов `updateRemoteSongFromLocalDatabase(song.id)` после approve.
4. На сервере — триггер новости (`SongReleaseAnnouncementService.detectAndAnnounceAvailability`).

## User Stories (краткий список)

- **US1** (P1): После approve — DEMO рендер + Telegram publish + sync на сервер.
- **US2** (P1): На сервере — новость «в коллекции» появляется автоматически.

## Functional Requirements (указатель)

- **FR-001**: `SongEditorController.approve()` → enqueue `RENDER_MP4_DEMO`.
- **FR-002**: В `mp4Demo.complete()` → `TelegramPublishService.publishDemo(songId)`.
- **FR-003**: После `saveToDb()` → явный `updateRemoteSongFromLocalDatabase(song.id)`.
- **FR-004**: Retry с exponential backoff для каждой фазы.

## Acceptance Criteria

- [ ] **AC1**: Approve песни → DEMO MP4 в `done_files/` + Telegram пост + новость «в коллекции» на сервере.
- [ ] **AC2**: При ошибке DEMO — retry, не блокировать остальные фазы.
- [ ] **AC3**: Sync на сервер завершён в течение 60 сек после approve.

## Связанные LiveDocs

- Domain: [editorial.md](../domain/editorial.md), [processing.md](../domain/processing.md) (mp4 render), [publishing.md](../domain/publishing.md) (Telegram)
- Feature: [184-approve-status-choice.md](../features/184-approve-status-choice.md) (choosing idStatus)
- Architecture: [data-sync.md](../architecture/data-sync.md)

## Код

- Backend: `karaoke-web/.../controllers/SongEditorController.kt` — `approve()` — добавить шаги
- Backend: `karaoke-app/.../service/RenderMp4Service.kt` — DEMO-фаза
- Backend: `karaoke-app/.../service/TelegramPublishService.kt` — `publishDemo()`
- Backend: `karaoke-app/.../service/SongReleaseAnnouncementService.kt` — server side

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14