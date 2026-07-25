# Implementation Plan: История прослушиваний (QW-13)

**Branch**: `009-listening-history` | **Date**: 2026-07-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-listening-history/spec.md`

## Summary

Раздел «История прослушиваний» в личном кабинете `karaoke-public`. **Ревизия
2026-07-25**: первоначальный план (переиспользовать `tbl_events`) отклонён
пользователем — `tbl_events` регулярно **опустошается** на PROD через
sync-механизм (`sync_events_pull_move_allowed = true` по умолчанию,
`EventsSyncTarget.oneClickDirection = SERVER_TO_LOCAL`, «move» = перенос с
удалением на источнике). История, построенная на этой таблице, теряла бы
данные при каждой синхронизации. См. `research.md` Decision 1 (обновлено).

**Новое решение** (согласовано с пользователем): новая небольшая таблица
`tbl_listening_history` — **одна строка на пару (пользователь, песня)**,
апсертится при каждом прослушивании (не растёт бесконечно, в отличие от
`tbl_events`, поэтому не нуждается в ротации). Явно **регистрируется в
`SyncRegistry`** (пользователь настоял на этом), по тому же паттерну, что
`tbl_site_playlists`/`tbl_site_playlist_items` — данные создаются
пользователями на PROD, `SERVER_TO_LOCAL`, все 8 sync-флагов по умолчанию
`false` (админ включает синхронизацию вручную при необходимости, ничего не
происходит автоматически). Отдельное поле `last_played_at` — момент последнего
прослушивания, по нему сортировка «последние слушали — выше» (явное
требование пользователя).

## Technical Context

**Language/Version**: Kotlin (Spring Boot, `karaoke-app`/`karaoke-web`, JDK 17)
для бэкенда; JavaScript (Vue 3.4+, Options API) для фронтенда (`karaoke-public`).

**Primary Dependencies**:
- Backend: сырой JDBC через `KaraokeConnection` (Constitution Principle II).
  Новая модель `ListeningHistory` реализует `KaraokeDbTable` (тот же интерфейс,
  что `SitePlaylist`/`WebEvent`) — переиспользует существующий
  reflection-loader/`recordhash`-diff инфраструктуру бесплатно.
  `SiteAuthInterceptor` — auth-гейт для `/api/public/account/*` (как у
  `PublicPlaylistController.kt`).
- Frontend: `composables/useAuth.js`, `components/LoginRequired.vue`.

**Storage**: PostgreSQL, **новая таблица** `tbl_listening_history` (миграция
`deploy/karaoke-db/27_listening_history.sql`, по шаблону
`09_playlists.sql` — `id` identity, FK на `tbl_site_users`, `song_id` без FK
(не связываем с sync песен, тот же паттерн, что `tbl_site_playlist_items`),
`play_count`, `last_played_at`, `created_at`, `last_update`, `recordhash`).
Применяется вручную на LOCAL и PROD (как все миграции этого проекта —
`AGENTS.md`).

**Testing**: ручное тестирование (constitution.md «Рабочий процесс: Тесты» —
автотестов для этого слоя в проекте нет). Сценарии — в `quickstart.md`.

**Target Platform**: браузер (`karaoke-public` SPA) + Spring Boot backend
(`karaoke-web`, публичный API) + `karaoke-app` (модель + recordhash-diff слой,
общий для sync).

**Project Type**: web — новая таблица + новая модель + новый read/write
backend-эндпоинт + новая страница фронтенда + регистрация в `SyncRegistry`.
Не затрагивает `webvue3` напрямую — раздел «Синхронизация» подхватывает новую
сущность автоматически через `SyncRegistry.all` (динамический список, не
хардкод — см. `ApiController.getSyncEntities()`), правок в `webvue3` не
требуется.

**Performance Goals**: NFR не заданы явно сверх SC-004 spec.md («без
ощутимой задержки при ≤100 записях»). Таблица по конструкции ограничена
размером «число пользователей × число разных песен, которые они слушали» —
на порядки меньше `tbl_events` (там одна строка на КАЖДОЕ прослушивание).

**Constraints**:
- Раздел доступен только зарегистрированным (FR-005) — `SiteAuthInterceptor`.
- Апсерт при каждом прослушивании — `INSERT ... ON CONFLICT (site_user_id,
  song_id) DO UPDATE SET play_count = play_count + 1, last_played_at = now()`
  — race-safe на уровне БД (не read-then-write из приложения).
- Исключать `SKIP`-помеченные песни из отображения (join с `tbl_settings`,
  фильтр по `tags`, тот же паттерн, что везде в проекте) — сама запись в
  `tbl_listening_history` не удаляется (это была бы потеря данных о реальном
  прослушивании), фильтруется только на чтении.
- Новая таблица требует `recordhash`-триггер (Constitution Principle III) —
  создаётся сразу в миграции, по образцу `tbl_site_playlists`.
- **Новая таблица регистрируется в `SyncRegistry`** (Constitution Principle
  III, явное требование пользователя) — 8 новых `KaraokeProperty`-флагов
  (`sync_listeninghistory_{push,pull}_{insert,update,delete,move}_allowed`),
  все `defaultValue = false` (тот же безопасный дефолт, что у плейлистов —
  ничего не синхронизируется автоматически, админ включает вручную).
- Никаких изменений в `tbl_events`/`EventsSyncTarget` — существующий
  механизм не трогаем (не наша зона, работает для своей цели — аналитика).

**Scale/Scope**:
- Миграция: 1 новый файл (`27_listening_history.sql`), применяется на LOCAL и
  PROD вручную.
- Backend: 1 новая модель (`ListeningHistory.kt`, `karaoke-app`), 1 запись в
  `SyncTarget.kt` (`ListeningHistorySyncTarget`) + `SyncRegistry.all`, 8 новых
  `KaraokeProperty` в `KaraokeProperties.kt`, 1 новый метод апсерта (вызывается
  из существующей ветки `EventType.PLAY` в `MainController.doRegisterEvent`,
  **дополнительно** к существующей записи в `tbl_events`, не вместо неё), 1
  новый read-метод для истории конкретного пользователя, 1 новый контроллер
  (`karaoke-web/.../PublicHistoryController.kt`), 1 новый DTO.
- Frontend: 1 новый файл (`HistoryView.vue`), 1 новый сервис
  (`historyApi.js`), 1 новая запись в `router/index.js`, 1 новая ссылка в
  `AccountView.vue`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Принцип | Соответствие | Обоснование |
|---|---------|--------------|-------------|
| I | Self-contained автопайплайн | N/A | Не медиа-пайплайн. |
| II | Сырой JDBC + дифф по хэшам | ✅ PASS | Новая модель реализует `KaraokeDbTable`, `recordhash`-триггер создаётся в миграции. Чтение истории — один запрос с `WHERE site_user_id = ?`, не O(n²). |
| III | Двух-БД синхронизация через SyncRegistry | ✅ PASS (по явному требованию) | `ListeningHistorySyncTarget` добавлен в `SyncRegistry.all`, 8 флагов в `KaraokeProperties.kt` (все `false` по умолчанию — безопасно, ничего не мигрирует само собой). При изменении колонок таблицы recordhash-триггер обязателен к пересозданию — учтено в задачах implement. |
| IV | Async-очередь задач | N/A | Апсерт — синхронная быстрая операция внутри уже существующего HTTP-запроса `doRegisterEvent`, не долгая операция, не через `KaraokeProcess*`. |
| V | Двух-фронтенд: admin и public | ✅ PASS | `karaoke-public` (новая страница) + `karaoke-app`/`karaoke-web` (модель/API). `webvue3` подхватывает новую sync-сущность автоматически (динамический `SyncRegistry.all`), без ручных правок. |
| VI | Code Standards | ✅ PASS | Новые Kotlin/Vue символы получат KDoc/JSDoc (CI это блокирует строго, `002-ci-lint-enforcement`). |

**Итог**: без нарушений. Complexity Tracking не нужен — таблица небольшая,
паттерн полностью повторяет уже существующий `tbl_site_playlists`.

## Project Structure

### Documentation (this feature)

```text
specs/009-listening-history/
├── plan.md              # Этот файл
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── history-api.md   # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
deploy/karaoke-db/
└── 27_listening_history.sql          # NEW: CREATE TABLE + recordhash-триггер
                                        #   (применяется вручную на LOCAL и PROD)

karaoke-app/
└── src/main/kotlin/com/svoemesto/karaokeapp/
    ├── model/
    │   └── ListeningHistory.kt        # NEW: KaraokeDbTable-модель + upsert + запрос по пользователю
    ├── sync/
    │   └── SyncTarget.kt              # УЖЕ ЕСТЬ, правка: +ListeningHistorySyncTarget, +в SyncRegistry.all
    ├── KaraokeProperties.kt           # УЖЕ ЕСТЬ, правка: +8 sync_listeninghistory_* флагов
    └── controllers/
        └── MainController.kt          # УЖЕ ЕСТЬ, правка: EventType.PLAY ветка doRegisterEvent
                                        #   дополнительно апсертит ListeningHistory

karaoke-web/
└── src/main/kotlin/com/svoemesto/karaokeweb/
    ├── controllers/
    │   └── PublicHistoryController.kt # NEW: GET /api/public/account/history
    └── dto/
        └── HistoryEntryDto.kt         # NEW

karaoke-public/
└── src/
    ├── views/
    │   ├── HistoryView.vue            # NEW
    │   └── AccountView.vue            # УЖЕ ЕСТЬ, правка: +ссылка на /account/history
    ├── services/
    │   └── historyApi.js              # NEW: fetchHistory()
    └── router/
        └── index.js                   # УЖЕ ЕСТЬ, правка: +роут /account/history
```

**Structure Decision**: новая sync-таблица по устоявшемуся в проекте паттерну
(`tbl_site_playlists`), не эксперимент с новой архитектурой. Запись в новую
таблицу происходит рядом с уже существующей записью в `tbl_events` (внутри
той же ветки `doRegisterEvent`), не заменяет её — `tbl_events` продолжает
служить общей аналитике, `tbl_listening_history` — персональной истории
пользователя.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *(нет)* | — | — |

Constitution Check прошёл без нарушений.
