---
status: Active
slug: 235-auto-sync-3h
related:
  - ../architecture/data-sync.md
  - ../architecture/dual-db-access.md
  - ../domain/catalog.md
  - ../../specs/235-auto-sync-3h/spec.md
  - ../../specs/235-auto-sync-3h/plan.md
  - ../../specs/235-auto-sync-3h/research.md
---

# 235 — Автозапуск «Синхронизации в 1 клик» каждые 3 часа (LiveDoc)

> Drill-down — [specs/235-auto-sync-3h/spec.md](../../specs/235-auto-sync-3h/spec.md).

## Что делает

Добавляет периодический автозапуск существующей бизнес-логики «Синхронизации в 1 клик»
(`POST /api/sync/oneclick`) в `karaoke-app`. По умолчанию — каждые 3 часа, от момента
завершения предыдущего тика (`fixedDelay`). Настраивается через `KaraokeProperties`
(3 новых ключа: `autoOneClickSyncEnabled`, `autoOneClickSyncIntervalMs`,
`autoOneClickSyncInitialDelayMs`). Ручная кнопка «🔄 Синхронизация в 1 клик» в
админке продолжает работать; при попытке ручного клика во время автозапуска
эндпоинт возвращает HTTP `409 Conflict`.

## Почему это было нужно

До фичи администратор должен был вручную нажимать «Синхронизация в 1 клик» при
каждом заходе на admin-машину (после ночи, выходных, поездки). За время
отсутствия изменения, сделанные другими редакторами/пользователями на SERVER-БД,
не попадали в LOCAL-БД и наоборот. Фича автоматизирует это — пока admin-машина
работает, синхронизация LOCAL↔SERVER запускается сама.

## Что изменилось

| Компонент | До | После |
|-----------|-----|-------|
| `karaoke-app` | sync только по ручному клику | sync ещё и автоматически каждые `autoOneClickSyncIntervalMs` (default 3 ч) |
| `KaraokeProperties.kt` | — | +3 ключа: `autoOneClickSyncEnabled` (default `true`), `autoOneClickSyncIntervalMs` (default `10_800_000L` = 3 ч), `autoOneClickSyncInitialDelayMs` (default `300_000L` = 5 мин) |
| `POST /api/sync/oneclick` | всегда запускал sync | если автозапуск или другой ручной клик уже идёт → **HTTP `409 Conflict`** с телом `{"error":"sync_in_progress","message":"..."}` |
| `webvue3 SyncTable.vue` | только кнопка | + блок «Автозапуск» со статусом, `lastRun`, `nextRunEstimate` |
| `webvue3 store.js` | `loadSyncEntitiesPromise` | + `loadSyncAutoStatusPromise` (GET `/api/sync/auto-status`) |
| `GET /api/sync/auto-status` | — | NEW — возвращает `AutoOneClickSyncStatusDto` для UI |

## User Stories (краткий список)

- **US1** (P1, MVP): Существующая бизнес-логика «Синхронизации в 1 клик» запускается
  автоматически каждые 3 часа. Ручной клик во время автозапуска получает 409 Conflict.
  Scheduler не падает при сбое БД (fail-fast + `try/catch(Throwable)`).
- **US2** (P2): Админ может отключить автозапуск через `autoOneClickSyncEnabled=false`
  (для миграций `tbl_songs`, когда незакоммиченные правки не должны уезжать на SERVER).
- **US3** (P3): UI-блок «Автозапуск» на странице `/sync` показывает `enabled`,
  `lastRun.startedAt`, `lastRun.totals`, `nextRunEstimate`.

## Functional Requirements (указатель)

Полный список — в [spec.md](../../specs/235-auto-sync-3h/spec.md). Ключевые:

- **FR-001..FR-003**: автозапуск каждые `autoOneClickSyncIntervalMs` (default 3 ч),
  `fixedDelay` от завершения, `initialDelay = autoOneClickSyncInitialDelayMs` (default 5 мин).
- **FR-004..FR-006**: 3 настройки в `KaraokeProperties` (см. таблицу «Что изменилось»).
- **FR-007**: in-process `AtomicBoolean running` (lock для «ручной + авто не одновременно»).
- **FR-008..FR-009**: логирование + REST `GET /api/sync/auto-status` для UI-блока.
- **FR-012, FR-016**: per-target `try/catch(Throwable)` (одна упавшая сущность не ломает
  остальные) + внешний `try/catch(Throwable)` (scheduler не падает при сбое БД).
- **FR-015**: HTTP `409 Conflict` при попытке ручного клика во время автозапуска.

## Acceptance Criteria (чеклист)

- [ ] **AC1** (US1): через 5 мин после старта `karaoke-app` в логах появляется первая
  запись `[AutoOneClickSyncScheduler] tick=… SUCCESS` (или `FAILED`).
- [ ] **AC2** (US1): ручной клик во время автозапуска возвращает 409, автозапуск
  не прерывается.
- [ ] **AC3** (US1): при сбое БД (оба контейнера падают) scheduler **не останавливается**:
  тик помечается `FAILED`, следующий тик через 3 ч отрабатывает штатно.
- [ ] **AC4** (US2): `autoOneClickSyncEnabled=false` останавливает автозапуск, ручной клик работает.
- [ ] **AC5** (US3): UI-блок «Автозапуск» на `/sync` показывает корректные `enabled`/`lastRun`/`nextRunEstimate`.

## Связанные LiveDocs

- Architecture: [data-sync.md](../architecture/data-sync.md) (механизм `SyncRegistry`,
  `oneClickDirection`, `recordhash`).
- Architecture: [dual-db-access.md](../architecture/dual-db-access.md) (LOCAL/SERVER
  подключения, `Connection.local()` / `Connection.remote()`).
- Domain: [catalog.md](../domain/catalog.md) (Song, tbl_songs — основная сущность,
  которая едет в oneClick sync).
- Specs: [specs/235-auto-sync-3h/](../../specs/235-auto-sync-3h/),
  [contracts/api-contracts.md](../../specs/235-auto-sync-3h/contracts/api-contracts.md),
  [quickstart.md](../../specs/235-auto-sync-3h/quickstart.md).
- Предыдущие фичи sync: [232-admin-song-editor-local-db.md](232-admin-song-editor-local-db.md)
  (sync как явная операция, не побочный эффект).

## Код

- Backend (новые файлы):
  - `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/AutoOneClickSyncScheduler.kt`
    — `@Component`, `@Scheduled(fixedDelay = 60_000L)`, internal-interval-check,
    `AtomicBoolean running`, `ConcurrentLinkedDeque<AutoOneClickSyncRun>`.
  - `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/AutoOneClickSyncRun.kt`
    — value-класс (run + Totals).
  - `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/dto/AutoOneClickSyncDtos.kt`
    — 3 DTO + `AutoOneClickSyncDtos.toDto/toDtos` конвертеры.
  - `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/AutoOneClickSyncStatusController.kt`
    — `@RestController`, `GET /api/sync/auto-status`.

- Backend (изменённые):
  - `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt:319+`
    — +3 `KaraokeProperty` записи.
  - `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:195`
    — + `autoOneClickSyncScheduler` в конструктор; `postSyncOneClick` обёрнут в
    `compareAndSet` + `try/finally` (FR-007, FR-015).

- Frontend (изменённые):
  - `webvue3/src/lib/utils.js:27-44` — `promisedXMLHttpRequest` теперь пробрасывает
    `error.status` и `error.responseBody` (для обработки 409 в UI).
  - `webvue3/src/components/Sync/store.js` — + `loadSyncAutoStatusPromise`,
    `getSyncAutoStatus` getter, `setSyncAutoStatus` mutation, `state.autoStatus`.
  - `webvue3/src/components/Sync/SyncTable.vue` — + блок «Автозапуск» (template,
    computed `autoStatus`, methods `formatDate`/`statusClass`, CSS),
    `doOneClick` обрабатывает 409; `mounted()` дёргает `loadSyncAutoStatusPromise`.

## История

- Создан: 2026-08-16
- Последнее обновление: 2026-08-16
