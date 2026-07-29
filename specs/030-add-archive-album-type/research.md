# Research: Добавить тип альбома «Архивные записи»

**Phase 0 output for**: `030-add-archive-album-type`
**Date**: 2026-07-29
**Spec**: [spec.md](./spec.md)

## Вопросы для research

7 вопросов, вытекающих из Technical Context и спек. Каждый вопрос
завершается Decision / Rationale / Alternatives considered.

---

### Q1. Какой `dbValue` выбрать для новой константы?

**Decision**: `archive` (lowercase, единственное число, по конвенции
`SongType`).

**Rationale**:
- Существующие значения: `studio`, `live`, `compilation`, `bootleg`,
  `single` — все lowercase, в единственном числе, без суффиксов
  (см. `AlbumType.kt:28-32`).
- `archive` — стандартный термин, не требует перевода, однозначно
  читается в SQL-выборках.
- Альтернатива `archival` (прилагательное) отвергнута: не
  соответствует шаблону `существительное` остальных значений.

**Alternatives considered**:
- `archival` — отвергнуто (см. rationale).
- `archive_records` — отвергнуто (содержит `_`, не соответствует
  конвенции).
- `historical` — отвергнуто (семантически уже, чем договорённая
  «исторические/ранее не издававшиеся»).

---

### Q2. Где разместить в `ZAKROMA_GROUP_ORDER`?

**Decision**: последним, после `BOOTLEG`.
Итоговый порядок: `STUDIO → SINGLE → LIVE → COMPILATION → BOOTLEG → ARCHIVE`.

**Rationale**:
- Согласовано с пользователем на этапе `/speckit.specify`.
- ARCHIVE — самая «экзотическая» категория (исторические/ранее
  не издававшиеся записи встречаются реже остальных); логично
  показывать последними.
- Минимально ломает привычную раскладку Закромов (5 разделов
  → 6 разделов, остальные позиции сохраняются).

**Alternatives considered**:
- Между `COMPILATION` и `BOOTLEG` — отвергнуто пользователем.
- Между `LIVE` и `COMPILATION` — отвергнуто пользователем.

---

### Q3. Какие подписи (`groupLabel`, `filterLabel`, `description`)?

**Decision**:
- `description = "Исторические/архивные записи"` (полное
  пояснение, отображается в DTO `ZakromaAlbumPublicDto.albumTypeLabel`
  под названием альбома).
- `groupLabel = "Архивные записи"` (заголовок раздела на
  Закромах, родительный падеж).
- `filterLabel = "Архивные"` (кнопка быстрого фильтра, краткое
  прилагательное).

**Rationale**:
- Согласовано с пользователем (семантика = исторические/ранее
  не издававшиеся).
- Соответствует стилю остальных 5 констант (см. `AlbumType.kt:28-32`).

**Alternatives considered**:
- `groupLabel = "Архив"` (короткая форма) — отвергнуто: остальные
  группы названы полностью («Студийные альбомы», «Сборники»).
- `description = "Архив"` (без пояснения) — отвергнуто: теряется
  семантика.

---

### Q4. Нужно ли править `Album.albumTypeEnum` getter/setter?

**Decision**: НЕТ. Геттер/сеттер работает через `AlbumType.fromDb(value)`
и `value.dbValue` (см. `Album.kt:71-75`) — он автоматически
подхватит новую константу.

**Rationale**:
- `albumTypeEnum` геттер делает `AlbumType.fromDb(albumType)` —
  достаточно добавить новую константу в enum, и она начнёт
  матчиться по `dbValue`.
- Аналогично сеттер: `albumType = value.dbValue` записывает
  строку `"archive"` в БД без специальной логики.

**Alternatives considered**: добавить `when` на стороне `Album.kt`
— отвергнуто, не нужно.

---

### Q5. Нужно ли править `AlbumDTO` или `ZakromaPublicDto`?

**Decision**: НЕТ.

**Rationale**:
- `AlbumDTO.albumType: String` (см. `AlbumDTO.kt`) — хранит
  `dbValue` как обычную строку, новое значение `"archive"`
  проходит без изменений DTO.
- `ZakromaPublicDto.albumTypeCounts: List<AlbumTypeSummaryDto>`
  (см. `ZakromaPublicDto.kt:95`) — формируется из
  `AlbumType.ZAKROMA_GROUP_ORDER` (`ZakromaPublicDto.kt:111-129`).
  Добавление ARCHIVE в `ZAKROMA_GROUP_ORDER` автоматически
  даёт новый элемент в `albumTypeCounts` для авторов с
  архивными альбомами.
- `ZakromaAlbumPublicDto.albumType` / `albumTypeLabel` — оба
  String, передаются как есть.

**Alternatives considered**:
- Добавить поле `isArchive: Boolean` в DTO — отвергнуто: тип
  уже передаётся через `albumType`, дублирование не нужно.

---

### Q6. Нужно ли править `tbl_albums` схему / миграцию БД?

**Decision**: НЕТ.

**Rationale**:
- Колонка `tbl_albums.album_type` уже `VARCHAR(20)` (или
  аналогичный свободный тип) — принимает любую строку длиной
  до 20 символов. Значение `"archive"` укладывается.
- `recordhash`-триггер уже покрывает `album_type` как
  обычную колонку: строки хешируются посимвольно, без
  whitelist допустимых значений. LOCAL и SERVER дадут
  одинаковый md5 для одной и той же записи (включая
  `album_type = 'archive'`).
- Существующий `getWhereList` (`Album.kt:210`) фильтрует по
  `album_type = '${whereArgs["album_type"]}'` — обычный SQL,
  работает для нового значения.

**Alternatives considered**:
- Создать CHECK-constraint с whitelist значений — отвергнуто:
  сломает обратную совместимость (старые записи могут иметь
  `NULL`/пустую строку/нестандартные значения) и потребует
  миграцию.

---

### Q7. Нужно ли править 3 хардкод-списка во фронте?

**Decision**: ДА. 3 файла, точечные правки ~3-4 строк в каждом.

**Rationale**:
- `webvue3/src/components/Albums/filter/AlbumsFilterModal.vue:128-134` —
  `ALBUM_TYPE_LABEL_OPTIONS` (5 объектов value/label). Добавить
  `{ value: 'archive', label: 'Архивный' }`. **Важно**: лейбл
  здесь в мужском роде («Архивный»), как и остальные
  («Студийный», «Концертный», «Сборник», «Бутлег», «Сингл»).
- `webvue3/src/components/Albums/AlbumsTable.vue:172-179` —
  `ALBUM_TYPE_OPTIONS` (массив строк) + `ALBUM_TYPE_LABELS`
  (map). Добавить `'archive'` в массив + `archive: 'Архивный'`
  в map.
- `webvue3/src/components/Authors/AuthorAlbumsModal.vue:59-65` —
  `ALBUM_TYPE_LABELS` (map). Добавить `archive: 'Архивный'`.

**Альтернативы (отвергнуты)**:
- Сделать единый источник правды на бэкенде (REST-эндпоинт
  `/api/album/types`) и грузить лейблы динамически — отвергнуто
  как отдельный рефакторинг, выходит за scope фичи. См.
  «Известные ограничения» ниже.
- Использовать `albumTypeLabel` из DTO (есть в
  `ZakromaAlbumPublicDto.albumTypeLabel`) — частично решает
  для public-фронта, но НЕ решает для admin-таблицы и фильтра,
  где `AlbumDTO` не содержит `albumTypeLabel`.

**Известные ограничения (technical debt)**:
- Лейблы типов альбома дублируются в 3 местах webvue3
  (AlbumsFilterModal, AlbumsTable, AuthorAlbumsModal). Это
  нарушает принцип «единый источник правды» (FR-018
  спек 012-entity-description-fields для Закромов) и
  порождает риск рассинхронизации при добавлении новых
  типов. Рефакторинг — отдельная фича (backlog).
- В рамках текущей фичи просто синхронно добавляем ARCHIVE
  в 3 места, как уже сделано для SINGLE (см. комментарий
  в AlbumsFilterModal.vue:127: «держать в синхроне при
  добавлении новых типов»).

---

## Сводка изменений

| Файл | Тип правки | LOC |
|------|------------|-----|
| `karaoke-app/.../model/AlbumType.kt` | +1 enum-константа (1 строка) + обновить `ZAKROMA_GROUP_ORDER` (1 строка) | 2 |
| `webvue3/.../Albums/filter/AlbumsFilterModal.vue` | +1 объект в `ALBUM_TYPE_LABEL_OPTIONS` | 1 |
| `webvue3/.../Albums/AlbumsTable.vue` | +1 в `ALBUM_TYPE_OPTIONS` + +1 в `ALBUM_TYPE_LABELS` | 2 |
| `webvue3/.../Authors/AuthorAlbumsModal.vue` | +1 в `ALBUM_TYPE_LABELS` | 1 |
| **ИТОГО** | **4 файла** | **~6 строк** |

Никаких новых файлов, никаких миграций, никаких изменений DTO,
никаких новых эндпоинтов. Фича — чисто аддитивная.

## Открытые вопросы

Нет. Все 7 вопросов resolved.

## Cross-references

- `docs/features/dual-db-sync.md` — sync LOCAL↔SERVER через
  recordhash (Principle II/III).
- `docs/features/ci-lint-enforcement.md` — ktlint/ESLint,
  проходит без новых violations.
- `specs/012-entity-description-fields/spec.md` — FR-018 про
  единый источник правды для лейблов (нарушен в webvue3,
  см. technical debt выше).
- `specs/011-album-song-rename/spec.md` — переименование
  альбомов/песен, откуда идёт паттерн с `albumType` dbValue.
