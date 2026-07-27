# Research: Альбом как сущность + переименование Settings→Song

Все архитектурные развилки в этой фиче уже были явно обсуждены и решены с пользователем
на этапе `/speckit-specify` (см. `spec.md` → Assumptions). Ниже — консолидация этих решений
в формате Decision/Rationale/Alternatives, плюс несколько узко-технических решений, найденных
при разведке кодовой базы для Phase 1 (data-model/contracts).

## 1. Album ↔ Song: связь

- **Decision**: настоящий FK `tbl_songs.album_id → tbl_albums.id` (`ON DELETE SET NULL`;
  `tbl_songs` — новое имя `tbl_settings` после переименования, см. §5).
  Существующие текстовые поля `song_author`/`song_album`/`song_year` (`SettingField.AUTHOR/ALBUM/YEAR`)
  сохраняются как есть — для отображения, поиска и как источник данных для одноразового бэкфилла.
- **Rationale**: пользователь явно попросил «песня ссылается на альбом» — свободнотекстовое
  сопоставление (как сейчас делает `Zakroma.kt`, группируя по `(author, album)` строкам) не даёт
  настоящей ссылки и не может нести собственные метаданные альбома (тип, сортировка).
  `ON DELETE SET NULL` — песня не должна ломаться/удаляться, если удалён альбом (см. Edge Cases spec.md).
- **Alternatives considered**: чисто текстовое сопоставление (Album как параллельный справочник,
  без FK) — отклонено пользователем как «не настоящая ссылка».

## 2. Album ↔ Author: связь

- **Decision**: `tbl_albums.author_id → tbl_authors.id`, `NOT NULL`, `ON DELETE RESTRICT`
  (нельзя удалить автора, пока у него есть альбомы — админ должен сначала переназначить/удалить альбомы).
  Один автор на альбом (без many-to-many).
- **Rationale**: подтверждено пользователем. `ON DELETE RESTRICT` выбран как более безопасный
  дефолт, чем `CASCADE` (не превращать удаление автора в скрытое каскадное удаление альбомов) —
  консистентно с общим принципом проекта «не терять данные молча».
- **Alternatives considered**: many-to-many Album↔Author (для сборников «разные исполнители») —
  отклонено пользователем, вынесено в Out of Scope spec.md; при необходимости сборники VA
  привязываются к отдельному «автору»-заглушке в существующей таблице `tbl_authors`.

## 3. Song ↔ Author: множественность (соавторы)

- **Decision**: главный автор песни остаётся как сейчас — свободнотекстовое поле
  `song_author`/`SettingField.AUTHOR` (без изменений, никакого нового `author_id` FK на `tbl_songs`
  не добавляется). Дополнительно вводится **настоящая связь многие-ко-многим** через новую таблицу
  `tbl_song_authors(id, song_id → tbl_songs.id, author_id → tbl_authors.id)` с уникальным
  ограничением `(song_id, author_id)`, зарегистрированная как собственная `SyncTarget`-сущность.
- **Rationale**: пользователь подтвердил и подчеркнул, что соавторов «может быть много» —
  без верхнего предела. Отдельная join-таблица (а не JSON-массив ID в одном поле) даёт: (a)
  referential integrity через FK, (b) согласованность с тем, как в проекте уже моделируются
  N:M-подобные связи (`tbl_listening_history` — факт-таблица со своим `id` и FK на обе стороны),
  (c) точечные insert/delete по одному соавтору без перезаписи всего поля (FR-011 — «добавлять/
  удалять по одному»).
- **Alternatives considered**:
  - JSON-массив `author_ids` в одной колонке `tbl_songs` (по образцу `PLAYER_READINESS_FLAGS`) —
    минимальные изменения схемы, но никакой referential integrity (осиротевшие ID при удалении
    автора) и сложнее эффективно запросить «все песни соавтора X». Отклонено — пользователь
    явно акцентировал важность корректного моделирования множественности, а не признак/JSON-костыль.
  - Не добавлять отдельный главный `author_id` FK на `tbl_songs` при этом изменении — оставлено
    как есть (вне рамок этой фичи): существующий паттерн сопоставления по имени (`Author.getAuthorByName`)
    используется повсеместно (`Zakroma`, поиск, спецзаказы) и его замена на FK — отдельный,
    более рискованный рефакторинг, не запрошенный пользователем.

## 4. Тип альбома (studio/live/compilation/bootleg) — представление в БД

- **Decision**: колонка `tbl_albums.album_type VARCHAR NOT NULL DEFAULT 'studio'`, backed Kotlin-enum
  `AlbumType(dbValue: String, description: String)` с компаньон-методом `fromDb(value: String?)`.
- **Rationale**: точное повторение уже существующего в проекте паттерна `SongType`
  (`karaoke-app/.../model/SongType.kt`, `tbl_settings.song_type`, миграция `24_song_type.sql`) —
  «enum только как типобезопасная обёртка над уже сохранённой строкой, не `.name`/`.ordinal`»,
  чтобы дальнейшие переименования enum-констант в коде не требовали миграции данных.
- **Alternatives considered**: Postgres `CHECK`-ограничение с перечислением значений внутри —
  отклонено, в проекте нет ни одного прецедента `CHECK (col IN (...))` для такого рода полей;
  `SongType`-паттерн (без `CHECK`, валидация на уровне Kotlin) — уже проверенный путь, взят как есть.

## 5. Переименование Settings→Song: глубина и границы

- **Decision** (подтверждено пользователем как «полная глубина», см. `spec.md` Assumptions):
  - `Settings` → `Song`, `SettingsDTO` → `SongDTO`, `SettingsDTOdigest` → `SongDTOdigest`,
    `SettingsSyncTarget` → `SongSyncTarget`, `SettingField` → `SongField`,
    `SettingVoice`/`SettingVoiceLine` → `SongVoice`/`SongVoiceLine`,
    `CrossSettingsRow`/`CrossSettingsCell` → `CrossSongRow`/`CrossSongCell`.
  - Существующий класс `Song` (обёртка `settings: Settings` + `songVersion` для MLT-рендера) →
    переименовывается в **`SongRenderContext`**, чтобы освободить имя `Song` для сущности.
    Мёртвый дубликат `Song2.kt` (тот же shape, не используется нигде в репозитории) — удаляется.
  - webvue3: имена store-экшенов/URL, отражающие старое название (`updateOneRemoteSettingsPromise`,
    `updateRemoteSettingsPromise`, `updateLocalSettingsPromise`, URL
    `/api/utils/updateremotesettingsfromlocaldatabase`, параметр `updateSettings`) — переименовываются
    синхронно с бэкендом.
- **UPDATE (обнаружено при реализации T043)**: коллизия имён оказалась на уровень глубже, чем
  описано в spec.md/plan.md. `model/Song.kt` (бывший рендер-класс, переименованный в
  `SongRenderContext`) содержал СВОЮ собственную, независимую семью вложенных классов, УЖЕ
  называвшихся `SongVoice`/`SongVoiceLine`/`SongVoiceLineType`/`SongVoiceLineSymbol` (данные для
  позиционирования строк при рендере) — при переименовании `SettingVoice`/`SettingVoiceLine`
  (из `Settings.kt`, данные голоса песни для БД) в `SongVoice`/`SongVoiceLine` это дало прямую
  коллизию имён класса. Также семья файлов оказалась шире ожидаемой: не только
  `SettingVoice.kt`/`SettingVoiceLine.kt`, но и `SettingVoiceLineElementTypes.kt`,
  `SettingVoiceLineElementSyllable.kt`, `SettingVoiceLineElement.kt`.
  **Разрешено** тем же приёмом, что и внешняя коллизия `Settings`/`Song`: старая (рендер-контекстная)
  семья переименована в `SongRenderVoice`/`SongRenderVoiceLine`/`SongRenderVoiceLineType`/
  `SongRenderVoiceLineSymbol` (используется только внутри `SongRenderContext.kt` + типы в
  `mlt/MltProp.kt` (`getScrollTrack`/`getScrollLines`/`getVoicelines`) и `mlt/mko/MkoSongText.kt`
  (`SongVoiceLineType` → `SongRenderVoiceLineType`)). **Урок на будущее**: при "полном"
  переименовании сущности стоит заранее грепать не только точное имя класса, но и его вероятные
  вложенные/предметно-специфичные производные имена (`<OldName><Suffix>`) на предмет коллизии с
  уже существующими одноимёнными классами в других частях кодовой базы — простого поиска "что уже
  называется Settings*" недостаточно, если целевое имя-заместитель (`Song*`) само уже занято.
- **UPDATE (после ревью пользователем)**: физическая таблица **переименовывается тоже** —
  `tbl_settings` → `tbl_songs`, `tbl_settings_sync` → `tbl_songs_sync`. Первоначальная
  рекомендация («оставить физическое имя как есть») была явно отклонена пользователем: риск
  признан оправданным, несмотря на объём (~379 упоминаний в 20+ миграционных файлах,
  дамп-файл для чистой установки, имена индексов/ограничений/последовательности/триггеров).
  Механика — новая миграция `28_rename_settings_to_songs.sql`, выполняемая **до** переключения
  Kotlin-кода на новое имя таблицы (см. §8, §5.1 ниже за пошаговым runbook).
- **Explicit exception (по-прежнему НЕ переименовывается)**:
  - Строковый ключ регистрации синхронизации `SyncTarget.key = "settings"` и все 8 связанных
    флагов `KaraokeProperties.kt` (`sync_settings_push_insert_allowed` и т.п.) — этот ключ
    зашит в файл `Karaoke.properties` на машине администратора, который **не хранится в git**
    и не может быть автоматически смигрирован этим PR. Переименование ключа = скрытая потеря
    уже настроенных пользователем флагов синхронизации без предупреждения. Это НЕ связано с
    именем физической таблицы (`Song.TABLE_NAME` — отдельная константа от `SyncTarget.key`),
    поэтому таблицу можно переименовать, не трогая ключ.
  - Отдельная, не связанная с песней функциональность `PublicSettingsController`/`PublicSettingDto`/
    `tbl_public_settings`/webvue3 `components/PublicSettings/*` (общесайтовая конфигурация вроде
    капчи) — вообще не является предметом этого переименования (FR-014).
- **Rationale**: пользователь подтвердил «полную глубину» и явно настоял на переименовании
  таблицы после ознакомления с рисками. Ключ синхронизации — отдельная техническая деталь
  с независимым риском (потеря несохранённого в git локального конфига), не входящая в то,
  о чём просил пользователь.
- **Alternatives considered**: оставить `tbl_settings` как есть (первоначальная рекомендация) —
  отклонено пользователем; переименование ключа синхронизации вместе с таблицей — не запрошено,
  добавлять этот риск без явного запроса нецелесообразно.

### 5.1. Runbook переименования таблицы (операционная последовательность)

Порядок критичен — Kotlin-код и обе базы (LOCAL, SERVER) не должны разойтись по имени таблицы:

1. **На LOCAL**: применить `28_rename_settings_to_songs.sql` (`ALTER TABLE tbl_settings RENAME TO
   tbl_songs`, аналогично для `tbl_settings_sync`→`tbl_songs_sync`, переименовать sequence,
   PK/unique-констрейнты, ~25 индексов, функции/триггеры `recordhash`/`update_last_updated`).
   Postgres `ALTER TABLE ... RENAME` — атомарная DDL-операция, не требует пересчёта данных.
2. Собрать и (только пользователь, см. ограничения конституции) перезапустить `karaoke-app`
   с обновлённым `Song.TABLE_NAME = "tbl_songs"` — **после** шага 1, не раньше (иначе
   `relation "tbl_songs" does not exist`).
3. Проверить на LOCAL сценарий 5 из `quickstart.md` (список/редактирование/синхронизация песен
   работают как раньше).
4. Только после подтверждения на LOCAL — по прямому согласию пользователя (см. ограничения
   агента, "Деплой на сервер... только по прямому согласию, на каждое действие отдельно")
   выполнить тот же `28_rename_settings_to_songs.sql` на SERVER и задеплоить обновлённый
   `karaoke-web`/`karaoke-app`-код туда.
5. Обновить/пересоздать `deploy/new_comp/sm-karaoke-system/dumps/karaoke_clear_dump.sql`
   (эталонный дамп для чистой установки) — иначе он останется ссылаться на устаревшее имя
   `tbl_settings` и будет непригоден для новых установок с этой версией кода.

**Откат**: до шага 2 (перезапуска `karaoke-app`) откат — обратный `ALTER TABLE ... RENAME TO`
(таблица физически не менялась, только имя) — дёшево и безопасно. После шага 2 откат требует
синхронного отката и схемы, и кода.

## 6. Бэкфилл существующих Album-данных

- **Decision**: одноразовый идемпотентный backfill-скрипт (Kotlin, запускается вручную
  администратором на каждой из баз — LOCAL и затем SERVER, т.к. в проекте нет авто-миграций
  данных, только ручные SQL/скрипт-шаги — см. `AGENTS.md`), который:
  1. Группирует все строки `tbl_songs` (уже переименованной, см. §5, к моменту бэкфилла) с непустыми `song_author`+`song_album` по точному
     совпадению `(author-text, year, album-text)`.
  2. Для каждой группы находит/создаёт `Author` по имени (уже существующий путь
     `Author.getAuthorByName`/`createNewAuthor`), затем создаёт `tbl_albums`-запись
     (`INSERT ... ON CONFLICT (author_id, year, name) DO NOTHING` — идемпотентность при повторном
     запуске), `sort_order` по умолчанию = порядковый номер в текущей сортировке `(year, name)`.
  3. Обновляет `tbl_songs.album_id` для всех песен группы.
  4. Песни с пустым `song_album` — пропускаются (остаются `album_id = NULL`).
- **Rationale**: соответствует FR-005/SC-003 (100% существующих песен с непустым альбомом
  связаны без ручного ввода) и Assumption 4/5/6 из `spec.md`. Уникальный индекс
  `(author_id, year, name)` на `tbl_albums` делает повторный запуск скрипта безопасным.
- **Alternatives considered**: SQL-миграция чистым DDL/DML без Kotlin — отклонено, т.к. сопоставление
  текста с `tbl_authors` (включая уже существующие алиасы, `Author.resolveByTerm`) удобнее и
  надёжнее переиспользовать из существующего Kotlin-кода, чем дублировать в SQL.

## 7. Регистрация синхронизации новых сущностей

- **Decision**: `Album` — через `GenericKaraokeDbTableSyncTarget<Album>` (reflection-based,
  `@KaraokeDbTableField` на каждом поле) — ровно как `AuthorsSyncTarget`. `tbl_song_authors`
  (соавторы) — отдельный `GenericKaraokeDbTableSyncTarget<SongCoAuthor>` того же вида. Оба
  регистрируются в `SyncRegistry.all`, получают по 8 флагов в `KaraokeProperties.kt`
  (`sync_albums_*`, `sync_song_coauthors_*`).
- **Rationale**: Principle III конституции — обязательная регистрация, `recordhash`-триггер
  пересоздаётся при любом изменении колонок затронутых таблиц (в т.ч. добавление `album_id`
  в `tbl_songs` требует пересоздания `update_tbl_songs_recordhash()` с новой колонкой
  и одноразового backfill `recordhash`).
- **Alternatives considered**: bespoke `SyncTarget` (как у `Song`/бывший `Settings`) — не нужен,
  обе новые сущности — простые скалярные записи без «тяжёлых» побочных геттеров, generic-путь
  подходит без изменений (тот же путь, что уже используют `Authors`/`Pictures`/`ListeningHistory`).

## 8. Следующий номер миграции

- **Decision**: три отдельных, последовательно применяемых файла:
  1. `deploy/karaoke-db/28_rename_settings_to_songs.sql` — физическое переименование
     `tbl_settings`→`tbl_songs`, `tbl_settings_sync`→`tbl_songs_sync` (+ sequence/констрейнты/
     индексы/триггеры), см. §5.1 за runbook.
  2. `deploy/karaoke-db/29_albums.sql` — `tbl_albums` + `tbl_songs.album_id` (ссылается уже на
     новое имя таблицы) + пересборка recordhash-триггера `tbl_songs`.
  3. `deploy/karaoke-db/30_song_coauthors.sql` — `tbl_song_authors` (FK на `tbl_songs.id`).
- **Rationale**: последние существующие файлы — `27_author_special_order.sql` и
  `27_listening_history.sql` (числа переиспользуются не строго монотонно) — `28`/`29`/`30`
  свободны. Раздельные файлы — для независимого отката/ревью каждой части; переименование
  таблицы вынесено в отдельный файл-предшественник, т.к. это отдельный, более рискованный шаг
  со своим runbook (§5.1), который логически не зависит от появления Album/соавторов.

## 9. Per-feature документация (Constitution Principle VI / FR-009)

- **Decision**: при реализации ОБЯЗАТЕЛЬНО обновить `docs/features/dual-db-sync.md` (две новые
  `SyncTarget`-записи) и `docs/features/mlt-generator.md` (переименование `Settings`→`Song`
  как основного входного параметра генератора). Вопрос о заведении нового 13-го per-feature
  документа (`docs/features/album-catalog.md`) для продуктовой части (Album/дискография) —
  оставлен на усмотрение реализации/тимлида на этапе `/speckit-tasks`, т.к. `docs/features/README.md`
  явно допускает добавление новой ключевой подсистемы через PR.

## Резюме: NEEDS CLARIFICATION

Открытых `[NEEDS CLARIFICATION]` не осталось — все пункты выше решены в диалоге со пользователем
на этапе specify или являются узко-техническими следствиями уже принятых решений, без
альтернативных бизнес-интерпретаций.
