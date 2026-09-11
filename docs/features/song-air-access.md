# Song air access (эфир и окно бесплатного доступа)

> **Feature**: управление публичным доступом к песне: всегда бесплатно, окно
> бесплатного доступа после эфира, флаг « не снимать с эфира ».
> Per-feature документ по [Constitution VI FR-009](../constitution.md).

## Контекст

После того как песня выходит в эфир (`publish_date` + `publish_time`
наступили), она становится публично доступной на определённый срок
(стандартное окно бесплатного доступа — 1 календарный месяц от даты эфира;
см. [specs/143-song-free-access-window](../../specs/143-song-free-access-window/spec.md)).
После окончания окна песня уходит в premium-only (по таймеру, если только
она не была помечена как `free=true` — «всегда бесплатно»).

В 2026 году появилась потребность (issue #81): пометить конкретную песню
так, чтобы **она оставалась публично доступной после окончания стандартного
окна**, но при этом не была «всегда бесплатной» (т.к. до эфира она всё
ещё premium-only). Это — флаг `free_after_on_air`.

## Флаги и их семантика

| Флаг | Имя в БД | До эфира | После эфира, в окне | После эфира, окно истекло |
|---|---|---|---|---|
| `free` | `free` | open | open | open |
| `free_after_on_air` | `free_after_on_air` | premium-only (как обычно) | open | **open** ← поведение флаг |
| `exclusive` | `exclusive` | premium-only | premium-only | premium-only |
| (без флагов) | — | premium-only | open | premium-only |

Приоритеты в логике доступа (сверху вниз):

1. `idStatus < 6` → IN_WORK (нет публичного доступа вообще).
2. `exclusive=true` → premium-only (бизнес-решение).
3. `free=true` → open (всегда бесплатно).
4. `free_after_on_air=true` И эфир наступил (`dateTimePublish ≤ now`) → open.
5. Стандартное окно (`freeAccessWindowMonths=1`) → open.
6. Иначе → premium-only.

## Техническая реализация

- **БД**: колонка `free_after_on_air BOOLEAN NOT NULL DEFAULT false` в
  `tbl_songs` (см. `deploy/karaoke-db/50_tbl_songs_free_after_on_air.sql`).
- **Kotlin entity**: `Song.freeAfterOnAir: Boolean` (`Song.kt`,
  рядом с `free`).
- **Логика доступа**:
  - `Song.isFreelyAvailableNow` — учитывает `freeAfterOnAir && onAir` (см.
    `Song.kt` ~стр. 664).
  - `SongStateResolver.resolve` — учитывает `freeAfterOnAir` после `free`
    (см. `SongStateResolver.kt`).
- **UI в админке `SongsTable.vue`**: рядом со столбцом `FR` (`flagFree` —
  флаг «всегда бесплатно») добавлен столбец `IA` (`flagFreeAfterOnAir` —
  флаг «не снимать с эфира»). Формат ячейки идентичен FR: `"-"` если флаг не
  установлен, `"✓"` если установлен. CSS: `.fld-flag-free-after-on-air`
  (аналог `.fld-flag-free`).
- **UI**: пара кнопок ДА/НЕТ «Не снимать с эфира (после окна доступа)» в
  `SongEdit.vue`, рядом с блоком «Всегда бесплатно».
- **API**:
  - `/api/songs/getById` — поле `freeAfterOnAir: boolean` в `SongDTO`.
  - `/api/songs/list?filter_free_after_on_air=true|false` — фильтр списка.
  - `/api/songs/{id}/access` — публичный доступ (через
    `PublicPlayerController.canWatch` → `isFreelyAvailableNow`).
- **recordhash** — пересозданы оба триггера
  (`deploy/recordhash_settings.sql`, `deploy/recordhash_settings_sync.sql`)
  для `tbl_songs` И `tbl_songs_sync` (Constitution III).

## Связанные документы

- [specs/369-free-after-onair-flag](../../specs/369-free-after-onair-flag/spec.md) — основная спека (issue #81).
- [knowledge/domains/catalog/components/song-entity.md](../../knowledge/domains/catalog/components/song-entity.md) — описание полей `Song`.
- [knowledge/domains/catalog/components/song-lifecycle.md](../../knowledge/domains/catalog/components/song-lifecycle.md) — жизненный цикл.
- [knowledge/domains/publishing/components/dictionaries.md](../../knowledge/domains/publishing/components/dictionaries.md) — словарь магических кодов.
- `specs/143-song-free-access-window/spec.md` — стандартное окно (1 месяц).
- `docs/strategy/growth.md` — модель монетизации (Модель D, гибрид).

## Changelog

- **Pass 369** (2026-09-11): Initial. Добавлен флаг `free_after_on_air`,
  OpenProject #81 «Флаг не снимать с эфира».