# Research: Добавить тип альбома «Трибьют/Кавер»

**Phase 0 output for**: `031-add-tribute-cover-album-type`
**Date**: 2026-07-29
**Spec**: [spec.md](./spec.md)

## Вопросы для research

7 вопросов, вытекающих из Technical Context и спек. Каждый вопрос
завершается Decision / Rationale / Alternatives considered.

> **Примечание**: фича полностью симметрична
> `030-add-archive-album-type` (та же структура, те же 4 файла,
> те же технические решения). Различия — только в dbValue,
> лейблах и позиции в `ZAKROMA_GROUP_ORDER`. Все альтернативы и
> ловушки 1:1 применимы.

---

### Q1. Какой `dbValue` выбрать для новой константы?

**Decision**: `tribute` (lowercase, единственное число, по конвенции
`SongType`).

**Rationale**:
- Существующие значения: `studio`, `live`, `compilation`, `bootleg`,
  `single`, `archive` — все lowercase, в единственном числе, без
  суффиксов (см. `AlbumType.kt:28-33`).
- `tribute` выбран вместо `cover` потому, что:
  - Семантика спеки — **объединённый тип** для cover и tribute
    альбомов (slash = синонимы).
  - `tribute` — более общее/нейтральное понятие: tribute album
    (посвящён другому исполнителю) исторически и лингвистически
    включает кавер-исполнения.
  - `cover` слишком узкое (только переработка одним исполнителем).
- Альтернатива `cover` отвергнута: не покрывает tribute-кейс.
- Альтернатива `tribute_cover` отвергнута: не соответствует шаблону
  `существительное` остальных значений.

**Alternatives considered**:
- `cover` — отвергнуто (см. rationale).
- `tribute_cover` — отвергнуто (содержит `_`, не соответствует
  конвенции).
- `covers` (множественное число) — отвергнуто: остальные значения
  в единственном числе.

---

### Q2. Где разместить в `ZAKROMA_GROUP_ORDER`?

**Decision**: последним, после `ARCHIVE`. Итоговый порядок:
`STUDIO → SINGLE → LIVE → COMPILATION → BOOTLEG → ARCHIVE → TRIBUTE`.

**Rationale**:
- Согласовано с пользователем на этапе `/speckit.specify`.
- TRIBUTE — самая «экзотическая» категория (трибьют/кавер-альбомы
  встречаются реже остальных); логично показывать последними.
- Минимально ломает привычную раскладку Закромов.
- Если фича 030-add-archive-album-type (ARCHIVE) и
  031-add-tribute-cover-album-type (TRIBUTE) смёржены в одной
  сборке, итоговый порядок согласован: ARCHIVE перед TRIBUTE.

**Alternatives considered**:
- Между `COMPILATION` и `BOOTLEG` — отвергнуто пользователем.
- Между `BOOTLEG` и `ARCHIVE` — отвергнуто пользователем.

---

### Q3. Какие подписи (`groupLabel`, `filterLabel`, `description`)?

**Decision**:
- `description = "Альбом каверов/трибьютов"` (полное пояснение,
  отображается в DTO `ZakromaAlbumPublicDto.albumTypeLabel` под
  названием альбома).
- `groupLabel = "Трибьют/Кавер"` (заголовок раздела на Закромах).
- `filterLabel = "Трибьют/Кавер"` (кнопка быстрого фильтра —
  совпадает с `groupLabel`, т.к. длинный лейбл тут не нужен).

**Rationale**:
- Согласовано с пользователем (slash = синонимы в бытовом
  использовании).
- В `description` — полная форма с «каверов/трибьютов» (род.
  падеж, множественное число) для ясности.
- В `groupLabel`/`filterLabel` — короткая форма через `/` (как в
  имени самой категории).

**Alternatives considered**:
- `groupLabel = "Трибьют-альбомы"` (без `/`) — отвергнуто:
  пользователь явно использовал `/` в задании.
- `filterLabel = "Трибьют"` (короткая форма) — отвергнуто: не
  покрывает «кавер».

---

### Q4. Нужно ли править `Album.albumTypeEnum` getter/setter?

**Decision**: НЕТ. Геттер/сеттер работает через
`AlbumType.fromDb(value)` и `value.dbValue`
(см. `Album.kt:71-75`) — он автоматически подхватит новую
константу.

**Rationale**:
- `albumTypeEnum` геттер делает `AlbumType.fromDb(albumType)` —
  достаточно добавить новую константу в enum, и она начнёт
  матчиться по `dbValue`.
- Аналогично сеттер: `albumType = value.dbValue` записывает
  строку `"tribute"` в БД без специальной логики.

**Alternatives considered**: добавить `when` на стороне `Album.kt`
— отвергнуто, не нужно.

---

### Q5. Нужно ли править `AlbumDTO` или `ZakromaPublicDto`?

**Decision**: НЕТ.

**Rationale**:
- `AlbumDTO.albumType: String` (см. `AlbumDTO.kt`) — хранит
  `dbValue` как обычную строку, новое значение `"tribute"`
  проходит без изменений DTO.
- `ZakromaPublicDto.albumTypeCounts: List<AlbumTypeSummaryDto>`
  (см. `ZakromaPublicDto.kt:95`) — формируется из
  `AlbumType.ZAKROMA_GROUP_ORDER`
  (`ZakromaPublicDto.kt:111-129`). Добавление TRIBUTE в
  `ZAKROMA_GROUP_ORDER` автоматически даёт новый элемент в
  `albumTypeCounts` для авторов с трибьют/кавер-альбомами.
- `ZakromaAlbumPublicDto.albumType` / `albumTypeLabel` — оба
  String, передаются как есть.

**Alternatives considered**:
- Добавить поле `isTribute: Boolean` в DTO — отвергнуто: тип уже
  передаётся через `albumType`, дублирование не нужно.

---

### Q6. Нужно ли править `tbl_albums` схему / миграцию БД?

**Decision**: НЕТ.

**Rationale**:
- Колонка `tbl_albums.album_type` уже `VARCHAR(20)` (или
  аналогичный свободный тип) — принимает любую строку длиной
  до 20 символов. Значение `"tribute"` укладывается (7 символов).
- `recordhash`-триггер уже покрывает `album_type` как
  обычную колонку: строки хешируются посимвольно, без
  whitelist допустимых значений. LOCAL и SERVER дадут
  одинаковый md5 для одной и той же записи (включая
  `album_type = 'tribute'`).
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
  `ALBUM_TYPE_LABEL_OPTIONS` (6 объектов value/label). Добавить
  `{ value: 'tribute', label: 'Трибьют/Кавер' }`. **Важно**: лейбл
  здесь такой же, как и `groupLabel` (короткий, через `/`),
  в отличие от 030 (где `filterLabel = "Архивные"`, мужской род).
  Тут `filterLabel = "Трибьют/Кавер"` с `/` — и в фильтре тоже `/`.
- `webvue3/src/components/Albums/AlbumsTable.vue:172-179` —
  `ALBUM_TYPE_OPTIONS` (массив строк) + `ALBUM_TYPE_LABELS`
  (map). Добавить `'tribute'` в массив +
  `tribute: 'Трибьют/Кавер'` в map.
- `webvue3/src/components/Authors/AuthorAlbumsModal.vue:59-65` —
  `ALBUM_TYPE_LABELS` (map). Добавить
  `tribute: 'Трибьют/Кавер'`.

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
- В рамках текущей фичи просто синхронно добавляем TRIBUTE
  в 3 места, как уже сделано для ARCHIVE (см. комментарий
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
- `specs/030-add-archive-album-type/` — близнец-фича, та же
  структура. Все технические решения и ловушки 1:1 применимы.
- `specs/030-add-archive-album-type/research.md` — для
  сравнения.
