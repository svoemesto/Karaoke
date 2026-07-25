# Data Model: История прослушиваний (QW-13)

**Branch**: `009-listening-history` | **Phase**: 1 | **Date**: 2026-07-25 (ревизия — новая таблица вместо `tbl_events`, см. `research.md` Decision 1-3)

---

## Новая сущность: `tbl_listening_history` (`ListeningHistory.kt`, `KaraokeDbTable`)

Одна строка на пару (пользователь, песня) — апсертится при каждом
прослушивании, не растёт на каждое событие (в отличие от `tbl_events`).
Участвует в LOCAL↔SERVER синхронизации (`SyncRegistry`, ключ
`listeninghistory`, `SERVER_TO_LOCAL`, все 8 флагов `false` по умолчанию — см.
`research.md` Decision 3).

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | integer, identity, PK | как у всех `KaraokeDbTable`-сущностей проекта |
| `site_user_id` | integer, FK → `tbl_site_users(id)` ON DELETE CASCADE | владелец записи истории |
| `song_id` | bigint | id песни (`tbl_settings.id`), **без FK** — тот же паттерн, что `tbl_site_playlist_items.song_id` («не связываем с sync песен») |
| `play_count` | integer, default 1 | сколько раз прослушано; инкрементируется при апсерте |
| `last_played_at` | timestamp, default now() | **момент последнего прослушивания** — явное требование пользователя, ключ сортировки «последние — выше» |
| `created_at` | timestamp, default now() | когда запись создана (первое прослушивание этой песни этим пользователем) |
| `last_update` | timestamp, default now() | стандартное поле для `KaraokeDbTable`-diff (не путать с `last_played_at` — разный смысл: одно про sync-механику, другое про пользовательский факт) |
| `recordhash` | varchar(32) | стандартный recordhash для LOCAL↔SERVER diff (Constitution Principle II/III) |

**Constraints**:
- Уникальный индекс на `(site_user_id, song_id)` — обеспечивает семантику
  «одна строка на пару», используется как conflict target в апсерте.
- Индекс на `site_user_id` — для быстрого чтения истории конкретного
  пользователя.
- Индекс на `last_played_at` — для сортировки при чтении.
- Индекс на `recordhash` (стандартный для sync-таблиц, см. `09_playlists.sql`).

**Триггеры** (создаются в миграции, по образцу `tbl_site_playlists`):
- `BEFORE INSERT OR UPDATE` → пересчёт `recordhash` из содержимого строки.
- `BEFORE UPDATE` → `update_last_updated()` (существующая общая функция).

**State transitions**: запись создаётся при первом прослушивании песни
пользователем (`play_count=1`), обновляется (`play_count++`,
`last_played_at=now()`) при каждом повторном прослушивании. Не удаляется
автоматически (нет TTL/ротации — таблица по конструкции не растёт
неограниченно, см. `research.md` Decision 2).

---

## Используемая существующая сущность: `tbl_settings` (песня)

Используемые поля: `id` (join-ключ на чтении истории), `song_name`,
`song_author`, `song_album`, `tags` (фильтрация `SKIP`-помеченных песен на
чтении — сама запись в `tbl_listening_history` не удаляется, см. `plan.md`
Constraints).

## Используемая существующая сущность: `tbl_site_users`

FK-родитель для `site_user_id`. Ничего нового не добавляется в эту таблицу.

---

## Новая сущность: `HistoryEntryDto` (API response DTO)

Не БД-сущность — сериализуемое представление одной строки
`tbl_listening_history`, обогащённой данными песни (join с `tbl_settings` на
чтении).

| Поле | Тип | Описание |
|------|-----|----------|
| `songId` | Long | id песни, для формирования ссылки `/song?id=` |
| `songName` | String | название |
| `songAuthor` | String | исполнитель |
| `songAlbum` | String | альбом (опционально показывать в UI) |
| `lastPlayed` | String (ISO-8601) | `last_played_at` — когда слушали в последний раз |
| `playCount` | Int | `play_count` — сколько раз прослушано |

**Validation rules**:
- `songId` всегда > 0.
- Список отсортирован по `lastPlayed` DESC (прямое отражение `ORDER BY
  last_played_at DESC` в запросе — без пересчёта на чтении, агрегация уже
  сделана на записи).
- Длина списка ≤ лимит (по умолчанию 100, см. FR-004 `spec.md`).
- Записи с `SKIP`-помеченной песней исключаются из ответа (join-фильтр).

---

## API Response Shape (см. `contracts/history-api.md` для полного контракта)

```json
{
  "items": [
    {
      "songId": 12345,
      "songName": "Группа крови",
      "songAuthor": "КИНО",
      "songAlbum": "Группа крови",
      "lastPlayed": "2026-07-24T18:30:00",
      "playCount": 3
    }
  ]
}
```

---

## Sync-регистрация (Constitution Principle III)

| Поле | Значение |
|------|----------|
| `key` (SyncTarget) | `listeninghistory` |
| `tableName` | `ListeningHistory.TABLE_NAME` (= `tbl_listening_history`) |
| `displayName` | «История прослушиваний» |
| `oneClickDirection` | `SyncDirection.SERVER_TO_LOCAL` |
| 8 `KaraokeProperty` флагов | `sync_listeninghistory_{push,pull}_{insert,update,delete,move}_allowed`, все `defaultValue = false` |

---

## Что НЕ является сущностью этой фичи

- Изменения в `tbl_events`/`EventsSyncTarget` — не трогаем (см. `research.md`
  Decision 1/4).
- Связка `anon_id → site_user_id` (перенос анонимной истории) — явно Out of
  Scope в `spec.md` (см. `M-3` в `growth.md`).
- Персональные настройки истории (например, «не сохранять историю») — не
  запрошены в spec.md, вне скоупа.
