# Data Model: Альбом как сущность + переименование Settings→Song

Источник решений: `research.md`. Здесь — конкретные сущности/поля/связи/правила валидации
для Phase 2 (`/speckit-tasks`) и реализации.

## Song (переименовано из `Settings`)

Физическая таблица: `tbl_settings` → **переименовывается в `tbl_songs`** (см. research.md §5/§5.1
за runbook переименования; должна быть переименована ДО перехода Kotlin-кода на новое имя).
Kotlin: `model/Song.kt` (бывший `model/Settings.kt`), `SongDTO`, `SongDTOdigest`.

Новые/изменённые поля относительно текущего состояния:

| Поле (Kotlin) | Колонка БД | Тип | Обязательность | Заметки |
|---|---|---|---|---|
| `albumId` | `album_id` | `Long?` / `INTEGER NULL` | опционально | FK → `tbl_albums.id`, `ON DELETE SET NULL`. Новое поле. |
| `author` *(без изменений)* | `song_author` | `String` | как сейчас | Главный автор — свободный текст, НЕ FK (вне рамок фичи, см. research.md §3). |
| `album` *(без изменений)* | `song_album` | `String` | как сейчас | Сохраняется для отображения/поиска/бэкфилла — не единственный источник истины после бэкфилла. |
| `year` *(без изменений)* | `song_year` | `Long` | как сейчас | Сохраняется аналогично `album`. |

Валидация/инварианты:
- Если `albumId` задан, привязанный `Album.authorId` ДОЛЖЕН соответствовать (по имени, через
  `Author.getAuthorByName(song.author)`) главному автору песни (FR-008). Проверяется на
  запись (update-эндпоинт), не на уровне БД (нет FK между `tbl_songs.song_author`-текстом
  и `tbl_authors`).
- `albumId = null` — валидное состояние (сингл без альбома, FR-006).

Переименования сопутствующих типов (см. research.md §5 для полного списка/находок разведки):
`SettingsDTO→SongDTO`, `SettingsDTOdigest→SongDTOdigest`, `SettingsSyncTarget→SongSyncTarget`,
`SettingField→SongField`, `SettingVoice/SettingVoiceLine→SongVoice/SongVoiceLine`,
`CrossSettingsRow/Cell→CrossSongRow/Cell`. Существующий класс `Song` (рендер-обёртка)
→ `SongRenderContext`; `Song2.kt` (мёртвый дубликат) — удаляется.

## Album (новая сущность)

Физическая таблица: `tbl_albums` (новая). Kotlin: `model/Album.kt`, `AlbumDTO.kt`.

| Поле (Kotlin) | Колонка БД | Тип | Обязательность | Заметки |
|---|---|---|---|---|
| `id` | `id` | `Long` (identity) | PK | Как у всех `KaraokeDbTable`-сущностей. |
| `authorId` | `author_id` | `Long` / `INTEGER NOT NULL` | обязательно | FK → `tbl_authors.id`, `ON DELETE RESTRICT`. |
| `year` | `year` | `Int` / `INTEGER NOT NULL DEFAULT 0` | обязательно | 0 = год неизвестен (консистентно с текущим `Song.year` fallback). |
| `name` | `name` | `String` / `VARCHAR NOT NULL` | обязательно | Название альбома. |
| `albumType` | `album_type` | `AlbumType` (enum) / `VARCHAR NOT NULL DEFAULT 'studio'` | обязательно | `AlbumType.STUDIO \| LIVE \| COMPILATION \| BOOTLEG`, `dbValue`-паттерн как `SongType` (research.md §4). |
| `sortOrder` | `sort_order` | `Int` / `INTEGER NOT NULL DEFAULT 0` | обязательно | Порядок отображения внутри `(authorId, year)`; меньше = раньше. Уникальность не требуется (админ может оставить одинаковые значения — тай-брейк по `id`). |
| `recordhash` | `recordhash` | `VARCHAR(32)` | генерируется триггером | Стандартный для sync (Principle II/III). |

Ограничения БД:
- `UNIQUE (author_id, year, name)` — идемпотентность бэкфилла и защита от дублей при ручном
  создании альбома администратором.
- Индекс на `(author_id, year)` — для быстрой выборки "альбомы автора за год" (сортировка на
  публичном сайте).

Отношения: один `Author` → много `Album`; один `Album` → много `Song` (через `Song.albumId`).

## SongCoAuthor (новая связь, служебная сущность)

Физическая таблица: `tbl_song_authors` (новая). Kotlin: `model/SongCoAuthor.kt`.

| Поле (Kotlin) | Колонка БД | Тип | Обязательность | Заметки |
|---|---|---|---|---|
| `id` | `id` | `Long` (identity) | PK | Собственный surrogate PK — нужен для reflection-based sync/diff (Principle II), по образцу `tbl_listening_history`. |
| `songId` | `song_id` | `Long` / `INTEGER NOT NULL` | обязательно | FK → `tbl_songs.id`, `ON DELETE CASCADE` (удалена песня — удаляются её строки соавторства). |
| `authorId` | `author_id` | `Long` / `INTEGER NOT NULL` | обязательно | FK → `tbl_authors.id`, `ON DELETE CASCADE` (удалён автор — он перестаёт быть чьим-либо соавтором). |
| `recordhash` | `recordhash` | `VARCHAR(32)` | генерируется триггером | Стандартный для sync. |

Ограничения БД:
- `UNIQUE (song_id, author_id)` — не допускает дублирования одного и того же соавтора у песни.
- Приложение ДОЛЖНО отклонять попытку добавить в соавторы автора, совпадающего с главным
  автором песни (edge case из `spec.md`) — проверка на уровне API, не БД (главный автор —
  свободный текст, не FK).

Отношения: многие-ко-многим `Song ↔ Author` (через эту таблицу), не влияет на `Song.author`
(главного автора) и не создаёт отдельных URL/страниц на публичном сайте (FR-010).

## Author (существующая сущность — расширение связей, без изменения схемы)

Без изменений полей/таблицы `tbl_authors`. Новые связи (логические, не новые колонки):
- один `Author` → много `Album` (`Album.authorId`).
- один `Author` → много `Song` как главный автор (уже существовало, свободный текст).
- один `Author` → много `Song` как соавтор (через `SongCoAuthor`, новое).

## Диаграмма связей

```text
Author (существующая, tbl_authors)
  │ 1
  │
  ├──< Album (новая, tbl_albums.author_id)          [ON DELETE RESTRICT]
  │       │ 1
  │       │
  │       └──< Song (tbl_songs.album_id)          [ON DELETE SET NULL]
  │
  └──< SongCoAuthor (новая, tbl_song_authors)         [ON DELETE CASCADE обе стороны]
          │ N
          │
          └──> Song (tbl_songs.id, через song_id)
```

## Состояния / переходы

- `Song.albumId`: `null` (без альбома) ⇄ `<id>` (привязана). Переход в обе стороны — обычное
  редактирование в админке; удаление альбома переводит все его песни в `null` автоматически
  (`ON DELETE SET NULL`).
- Бэкфилл (research.md §6) выполняется один раз при выпуске функциональности и переводит
  часть песен из `null` в `<id>`; не переопределяет уже вручную выставленный `albumId`
  (скрипт должен работать только с `WHERE album_id IS NULL`, чтобы быть безопасным при повторном
  запуске и не затирать ручные правки, сделанные между запусками).
