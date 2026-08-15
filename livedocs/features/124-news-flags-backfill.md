---
status: Active
slug: 124-news-flags-backfill
related:
  - ../domain/publishing.md
  - ../domain/catalog.md
  - ../architecture/data-sync.md
  - ../../specs/124-news-flags-backfill/spec.md
  - ../../archive/docs/features/news-publish-backfill.md
---

# 124 — Backfill флагов публикаций готовых песен (LiveDoc)

> Drill-down — [specs/124-news-flags-backfill/spec.md](../../specs/124-news-flags-backfill/spec.md).

## Что делает

Разовая операция на LOCAL для приведения news-флагов ~15000 готовых песен
(`id_status=6`) в «уже опубликовано» состояние — без создания ложных
новостей «появилась в коллекции» при последующих sync'ах.

**Проблема**: на LOCAL для старой готовой песни делают `save()` → обновляются
флаги → sync LOCAL→PROD → на PROD создаётся новость «появилась в коллекции»
для песни, которая там уже давно.

**Решение**:
1. **Backfill на LOCAL**: `newsAvailableAnnounced=true`, `premiumAutoPublishState=COMPLETE`,
   `newsPremiumPublishPending=false`, оба канала `Telegram`/`VK` помечены как отправленные.
2. **Kill-switch на PROD** в `KaraokeProperties.karaokeNewsAutoPublishKillSwitch` —
   блокирует создание auto-новостей в `detectAndAnnounceAvailability` и
   `checkOnAirWindow` на время sync-окна.
3. **Sync LOCAL→PROD** разносит флаги штатным движком (recordhash → `doChangeRecords`).
4. **Снятие kill-switch** после подтверждения 0 новых записей в `tbl_news`.

## User Stories (краткий список)

- **US1** (P1): одноразовый backfill на LOCAL + sync + снятие kill-switch —
  0 новых новостей на PROD.
- **US2** (P2): отчёт с разбивкой «исправлено / пропущено / уже OK» по
  каждому флагу; dry-run режим.

## Functional Requirements (указатель)

- **FR-001..FR-005**: backfill на LOCAL — `newsAvailableAnnounced=true`,
  премиум-флаги в COMPLETE, идемпотентность, без `tbl_news` и без
  автопубликации.
- **FR-006..FR-008**: skip-правила — `id_status<6`, активная публикация,
  пустые `source_markers`.
- **FR-009..FR-012**: распространение через sync LOCAL→PROD, kill-switch
  в `KaraokeProperties`, идемпотентность `markNewsAvailableIfReady`.
- **FR-013..FR-015**: dry-run, отчёт, SSE-прогресс по чанкам, endpoint
  `POST /utils/backfillnewsavailable` в `ApiController`.
- **FR-016..FR-018**: `newsPremiumPublishPending` не переустанавливается
  для COMPLETE/FAILED; валидация `player_readiness_flags` JSON; видимость
  в SSE-ленте.

## Acceptance Criteria

- [ ] **AC1** (SC-001, SC-002): ≥ 99.9% готовых песен на PROD имеют
      `newsAvailableAnnounced=true` и премиум-флаги в COMPLETE.
- [ ] **AC2** (SC-003, SC-004): 0 новых записей в `tbl_news` на PROD
      за период backfill+sync; 0 новых публикаций в Telegram/VK.
- [ ] **AC3** (SC-005, SC-006): после save() готовой старой песни + sync
      — 0 новых новостей, `newsPremiumPublishPending` остаётся `false`.
- [ ] **AC4** (SC-007): время backfill ≤ 15 минут на ~15000 песен.
- [ ] **AC5** (SC-008): повторный backfill — 0 исправлений (идемпотентность).
- [ ] **AC6** (SC-009): новая песня после снятия kill-switch получает
      новость «появилась в коллекции» (нормальный flow сохранён).
- [ ] **AC7** (SC-010): dry-run числа совпадают с числами «исправлено».
- [ ] **AC8** (SC-011): во время kill-switch на PROD 0 записей
      `source='auto'` в `tbl_news`.

## Связанные LiveDocs

- Domain: [publishing.md](../domain/publishing.md) — публикация и авто-новости.
- Domain: [catalog.md](../domain/catalog.md) — сущность Song и её ready-флаги.
- Architecture: [data-sync.md](../architecture/data-sync.md) — sync LOCAL↔PROD,
  `doChangeRecords`, recordhash.

## Код

- **Backend**: `karaoke-app/src/main/kotlin/.../controllers/ApiController.kt`
  — `doBackfillNewsAvailable` (по образцу существующего, но с разбивкой по
  флагам + dry-run).
- **Backend**: `karaoke-app/src/main/kotlin/.../services/SongReleaseAnnouncementService.kt`
  — точка `detectAndAnnounceAvailability` (читает kill-switch).
- **Backend**: `karaoke-app/src/main/kotlin/.../services/News.kt` — единая
  точка `createAutoAnnouncement` (FR-011: блокирует оба пути).
- **Backend**: `karaoke-app/src/main/kotlin/.../properties/KaraokeProperties.kt`
  — `karaokeNewsAutoPublishKillSwitch` (base64, hot-reload через
  `/api/properties/setproperty`).
- **Frontend**: `webvue3/src/...` — кнопка backfill в админке, SSE-тосты
  прогресса.
- **Допущения**: все ~15000 готовых песен УЖЕ были опубликованы (до введения
  `id_telegram_demo`/`id_vk`).

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14
