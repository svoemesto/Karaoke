# Implementation Plan: Добавить тип альбома «Архивные записи»

**Branch**: `030-add-archive-album-type` | **Date**: 2026-07-29 | **Spec**:
[spec.md](./spec.md)

**Input**: Feature specification from `/specs/030-add-archive-album-type/spec.md`

## Summary

Добавить шестую константу `ARCHIVE` в Kotlin-enum `AlbumType`
(`dbValue = "archive"`, `groupLabel = "Архивные записи"`,
`filterLabel = "Архивные"`, `description = "Исторические/архивные записи"`) и
поместить её последним элементом в `ZAKROMA_GROUP_ORDER` (после `BOOTLEG`).
Синхронно добавить `'archive'` в три хардкод-списка `webvue3` —
`AlbumsFilterModal.vue`, `AlbumsTable.vue` и `AuthorAlbumsModal.vue`. Никаких
миграций БД, никаких изменений `recordhash`-триггеров, никаких новых полей.

## Technical Context

- **Language/Version**:
  - Backend: Kotlin (JDK 17, Gradle multi-module, Spring Boot).
  - Frontend: Vue 3 + Vite + Node 22 + Vuex + Bootstrap-vue-next.
- **Primary Dependencies**:
  - Backend: kotlinx-serialization, Jackson, сырой JDBC
    (`KaraokeConnection`). Никаких новых зависимостей.
  - Frontend: Vue 3, Vuex 4, Bootstrap-vue-next. Никаких новых
    зависимостей.
- **Storage**: PostgreSQL через сырой JDBC. Колонка
  `tbl_albums.album_type VARCHAR(20)` уже принимает произвольные
  lowercase-строки — миграция не требуется.
- **Testing**: тестов в CI нет (см. constitution §Рабочий процесс,
  `docs/features/ci-lint-enforcement.md`); существующие интеграционные
  тесты `@Disabled` и требуют сеть/браузер. Проверка делается вручную
  на admin-машине по сценариям `quickstart.md`.
- **Target Platform**: Linux (admin LOCAL + прод-сервер 79.174.95.69);
  Docker + docker-compose; JRE-контейнер для backend.
- **Project Type**: multi-module web-service (Spring Boot backend + 2
  Vue 3 SPA).
- **Performance Goals**: N/A. Изменение аддитивное — не влияет на
  горячий путь. Фильтр по `album_type` уже индексируется на уровне
  SQL `WHERE album_type = ?` в `Album.getWhereList` (см. Album.kt:210).
- **Constraints**:
  - Backward-compat: `AlbumType.fromDb(null)` и `fromDb("")` →
    `STUDIO` (поведение сохранено).
  - Никаких изменений `recordhash`-функции (`tbl_albums` уже в
    `SyncRegistry` как `AlbumsSyncTarget`, константа enum не
    участвует в хешировании).
  - Никаких миграций БД, никаких `ALTER TABLE`.
- **Scale/Scope**:
  - 18 858 альбомов в БД (на проде).
  - Из них новым типом `archive` предположительно будет отмечено
    ≤100 альбомов (архивные записи редки).
  - Изменения: 1 файл бэкенда + 3 файла фронта (точечные правки
    ~3-4 строк в каждом).

## Constitution Check

*GATE: must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Принцип | Применимость | Статус |
|---|---------|--------------|--------|
| I | Self-contained автопайплайн | Не затрагивает: фича — изменение enum'а, не новый ML/внешний API | PASS |
| II | Сырой JDBC + дифф по хэшам | Не затрагивает: SQL-запросы для `album_type` уже пишутся через сырой JDBC в `Album.getWhereList`; новая строка `'archive'` — обычная VARCHAR, не ломает `recordhash` | PASS |
| III | Двух-БД синхронизация через SyncRegistry | `tbl_albums` уже в `SyncRegistry` как `AlbumsSyncTarget` (см. `sync/SyncTarget.kt:292-302`); добавлять новую запись в `SyncRegistry.all` не нужно; recordhash-триггер уже покрывает `album_type` как обычную колонку | PASS |
| IV | Async-очередь задач | Не затрагивает: фича не в горячем пути обработки медиа | N/A |
| V | Двух-фронтенд | Затрагивает обе части: `webvue3` (AlbumsTable, AlbumsFilterModal, AuthorAlbumsModal) + `karaoke-public` (через `ZakromaPublicDto.albumTypeCounts`, который **уже** формируется из `ZAKROMA_GROUP_ORDER` — новый ARCHIVE автоматически попадёт) | PASS |
| VI | Code Standards (KDoc/JSDoc, FR-006/007/009) | Нужно: добавить KDoc на `ARCHIVE` константу (уже есть примеры для других констант в `AlbumType.kt:28-32`); обновить JSDoc-комментарии в 3 .vue/.js файлах, где перечислены типы; НЕ нужно: создавать новый per-feature документ (AlbumType — не самостоятельная подсистема, это атрибут сущности `Album`, FR-009 покрывается через кросс-ссылку в `docs/features/dual-db-sync.md` если потребуется) | PASS |
| VII | Cross-Machine Setup | Не затрагивает | N/A |

**Итог**: все применимые принципы — PASS. Нарушений нет.
`Complexity Tracking` — пустая (нет нарушений, которые нужно
обосновывать).

## Project Structure

### Documentation (this feature)

```text
specs/030-add-archive-album-type/
├── plan.md              # Этот файл (/speckit.plan output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── api.md           # Phase 1 output: JSON-контракт ZakromaPublicDto
├── checklists/
│   └── requirements.md  # Создан /speckit.specify
└── spec.md              # Создан /speckit.specify
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
└── model/
    └── AlbumType.kt                              # +1 enum-константа

webvue3/src/components/
├── Albums/
│   ├── AlbumsTable.vue                           # +1 строка в ALBUM_TYPE_OPTIONS, +1 в ALBUM_TYPE_LABELS
│   └── filter/
│       └── AlbumsFilterModal.vue                 # +1 объект в ALBUM_TYPE_LABEL_OPTIONS
└── Authors/
    └── AuthorAlbumsModal.vue                     # +1 строка в ALBUM_TYPE_LABELS
```

Файлы вне этих четырёх **не затрагиваются**:

- `Album.kt`, `AlbumDTO.kt`, `Zakroma.kt`,
  `karaoke-web/.../ZakromaPublicDto.kt` — `albumType` уже
  типизирован как `String` (dbValue) и `ZAKROMA_GROUP_ORDER` уже
  формирует `albumTypeCounts` автоматически. См.
  `ZakromaPublicDto.kt:111-129`: новый ARCHIVE попадёт в JSON
  без правок DTO.
- `Stat.kt`, `cachedTotal`, `StatsCacheScheduler` — счётчики
  работают по диапазону `id_status>=3`, тип альбома не участвует.
- `KaraokeProperties.kt`, `KaraokeProcess*`, MLT-генератор —
  не затрагиваются.

**Structure Decision**: правки строго точечные, в 4 файлах,
без новых модулей/папок/сервисов. Изменение enum + 3 хардкод-списка
— это полный объём фичи.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет)     | —          | —                                    |

**Re-evaluation after Phase 1 design**: см. секцию «Re-check после
Phase 1 design» в конце этого файла (после `data-model.md` и
`contracts/`).

## Сводка артефактов Phase 0 / Phase 1

- **`research.md`** — решения по 7 вопросам (имя dbValue,
  позиция в `ZAKROMA_GROUP_ORDER`, лейблы, hardcoded-списки во
  фронте, миграция БД, sync, KDoc).
- **`data-model.md`** — модель `AlbumType` (расширение enum),
  модель `Album.albumType` (без изменений), DTO
  `AlbumDTO.albumType` / `ZakromaAlbumPublicDto.albumType` /
  `AlbumTypeSummaryDto` (без изменений), Vuex state
  `albumsFilterAlbumType` (без изменений).
- **`contracts/api.md`** — JSON-контракт `ZakromaPublicDto` до и
  после фичи, включая новый ключ `archive` в
  `albumTypeCounts`; невалидные значения → дефолт `studio`.
- **`quickstart.md`** — 6 ручных сценариев валидации end-to-end
  (админка, фильтр, Закрома, sync LOCAL↔SERVER, регрессии).

## Re-check после Phase 1 design

| # | Принцип | Re-check |
|---|---------|----------|
| I | Self-contained | PASS — нет новых внешних API. |
| II | Сырой JDBC + дифф | PASS — `Album.albumType` уже `@KaraokeDbTableField`, запись нового значения через существующий `Album.saveToDb()` (без изменений `getDiff()`); `getSqlToInsert` сериализует поле как обычную строку. |
| III | Sync | PASS — `tbl_albums.album_type` уже покрыта `recordhash`-триггером (существующая миграция, см. `docs/features/dual-db-sync.md`); новая константа enum не требует нового триггера. `AlbumsSyncTarget` (SyncTarget.kt:292) — без изменений. |
| V | Двух-фронтенд | PASS — `karaoke-public` получает `archive` автоматически через `ZAKROMA_GROUP_ORDER` (без правок DTO/эндпоинта); `webvue3` обновляет 3 файла с хардкод-списками (точечные правки). |
| VI | Code Standards | PASS — KDoc на `ARCHIVE` будет добавлен, JSDoc в 3 .vue/.js файлах — обновлён с явной cross-ссылкой на бэкенд-источник. `lint:check` (ktlint + ESLint) должен проходить без новых violations. |

**Итог**: фича готова к `/speckit.tasks` (фаза 2 — генерация задач).
