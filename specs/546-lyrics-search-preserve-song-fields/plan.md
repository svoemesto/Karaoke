# Implementation Plan: Подстановка найденного текста не перезатирает песню

**Branch**: `546-lyrics-search-preserve-song-fields` | **Date**: 2026-10-18 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/546-lyrics-search-preserve-song-fields/spec.md`
(OpenProject #324).

## Summary

Дефект: поиск текста идёт десятки секунд, и по его завершении в песню записывается
**устаревший in-memory снимок** — `Song.saveToDb()` считает diff «снимок против БД» и
записывает значения снимка для всех расходящихся полей (`Song.kt:5258`, `getDiff` —
`Song.kt:6843`). `saveToDbLocked()` (`Song.kt:5641`) добавляет `SELECT ... FOR NO KEY UPDATE`,
но diff по-прежнему считается от устаревшего объекта, поэтому блокировка лишь сериализует
писателей и логирует WARN `song.locked_save_diff_overlap` уже после решения перезаписать.

Технический подход: ввести **примитив записи ограниченного набора полей**
(`SongFieldUpdate`): в одной транзакции под блокировкой строки читается актуальное состояние,
проверяются guard-условия (текст ещё пуст, статус ещё `0`), выполняется `UPDATE` **только
колонок операции**, после чего отправляются прежние побочные эффекты (SSE `recordChange`,
хук `SyncAudioDescendants`, push в remote при включённой настройке). `Song.saveToDb()` не
меняется. На примитив переводятся: `applyFoundLyricsIfMissing` и три однотипных помощника
копирования (`applyDuplicateOriginal`, `applyAudioParentMarkers`, `applyFamilySongSelection`),
а также ветка синхронного Яндекс-поиска в `doCreateFromFolder` (перечитывание там становится
ненужным).

## Technical Context

**Language/Version**: Kotlin 2.2.20, JVM 17 (`jvmTarget = JVM_17`), Gradle (Kotlin DSL).

**Primary Dependencies**: Spring Boot (web/сервисы `karaoke-app`), Playwright (поиск текста —
не меняется), PostgreSQL (сырой JDBC через `KaraokeConnection`), Jackson /
kotlinx-serialization, SLF4J-логирование (категория `infra.prod.ping` для операционных WARN).

**Storage**: PostgreSQL `LOCAL` (админ-машина) + `SERVER` (прод) с two-DB sync по `recordhash`;
MinIO (файлы) — вне scope.

**Testing**: JUnit 5 (`karaoke-app/src/test/kotlin/...`) — unit-тесты чистых функций
(планирование записи и SQL-билдер); интеграционная проверка — ручной чек-лист на dev-хосте
(импорт папки + параллельная правка) и проверка логов.

**Target Platform**: Linux x86_64, контейнер `karaoke-app` (хост `nsa-i9`), Java 17.

**Project Type**: web-service (модуль `karaoke-app`); фронтенды `webvue3` / `karaoke-public`
не меняются.

**Performance Goals**: применение текста — не более +1 `SELECT` в существующей транзакции
(< 50 мс на типичной записи); общая длительность поиска не меняется; SC-006 (ухудшение
времени появления текста ≤ 10%).

**Constraints**: без миграций БД (FR-013); только сырой JDBC (Constitution II, R-07); контракт
`Song.saveToDb()` неизменен (31 вызывающее место); `lock_timeout` — существующая настройка
`songSaveLockedTimeoutMs`; запрет на JPA/Hibernate.

**Scale/Scope**: 18k+ песен в каталоге; массовый импорт папок — десятки песен за прогон;
`lyricsSearchExecutor` — 4 потока; 3 точки входа подстановки текста × 4 движка.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Как соблюдается | Статус |
|---|---|---|
| **I. Self-contained автопайплайн** | Не затрагивается: поиск текста (Playwright/SearXNG/Fourget) и очередь процессов остаются как есть. | PASS |
| **II. Сырой JDBC + дифф по хэшам** | Примитив — `PreparedStatement` + транзакция на `KaraokeConnection`; JPA не используется. `recordhash`-триггер обновляется любым `UPDATE` строки → sync видит изменение. | PASS |
| **III. Двух-БД синхронизация** | Поля не меняются; запись остаётся обычным `UPDATE tbl_songs` → триггер `tg_tbl_songs_recordhash` срабатывает. Немедленный push (`updateRemoteSongFromLocalDatabase`) сохраняется в побочных эффектах. | PASS |
| **IV. Async-очередь задач** | Очередь не меняется; воркер (`KaraokeProcessWorker`) лишь вызывает новый примитив вместо `saveToDbLocked()`. | PASS |
| **V. Два фронтенда** | Изменений нет; `SongEdit.vue` остаётся вторым писателем без правок. | PASS |
| **VI. Code Standards (FR-006/FR-007/FR-009)** | Новые публичные функции получают KDoc + `@see` на `docs/features/lyrics-search-preserve-song-fields.md`; ktlint в CI; per-feature документ создаётся в том же PR. | PASS (задача в tasks) |
| **VII. Cross-Machine Setup** | Локальные конфиги не трогаются. | PASS |
| **VIII. Секреты и git-гигиена** | Новых секретов нет; `.env.local-tracker` не коммитится. | PASS |
| **IX. Knowledge-first** | Pre-flight выполнен (9 ссылок, маяк `.specify/.preflight-lyrics-search-preserve-song-fields.ok`). Knowledge-SSoT обновляется: новый L3-компонент `persistence/components/field-scoped-save.md` + L2-ссылка + `domains/README.md` + правка `catalog/components/song-entity.md`. | PASS (задача в tasks) |

Нарушений нет → Complexity Tracking не заполняется.

## Project Structure

### Documentation (this feature)

```text
specs/546-lyrics-search-preserve-song-fields/
├── plan.md              # этот файл
├── research.md          # Phase 0: R1-R9 (решения и альтернативы)
├── data-model.md        # Phase 1: наборы полей, состояния, инварианты
├── quickstart.md        # Phase 1: как проверить фичу
├── contracts/
│   ├── field-scoped-save.md      # контракт внутреннего примитива
│   ├── lyrics-write-policy.md    # политика: guard → результат, наборы полей
│   ├── log-format.md             # формат WARN-логов и событий
│   └── manual-test-checklist.md  # приёмка руками
├── checklists/requirements.md    # (создан на этапе specify)
├── spec.md
└── tasks.md             # Phase 2 (/speckit.tasks)
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── model/
│   ├── Song.kt                      # НЕ меняем saveToDb(); адаптер/точка вызова примитива
│   ├── SongFieldUpdate.kt           # NEW: транзакция, блокировка, UPDATE только колонок
│   └── SongWritePlan.kt             # NEW: чистые функции планирования (guard + набор полей)
├── UtilsAI.kt                       # applyFoundLyricsIfMissing → примитив
├── Utils.kt                         # applyDuplicateOriginal / applyAudioParentMarkers /
│                                    #   applyFamilySongSelection → примитив
├── KaraokeProcessWorker.kt          # дозавершение async-поиска → примитив
└── controllers/ApiController.kt     # ветка Yandex.Sync в doCreateFromFolder: без reload

karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/
├── SongWritePlanTest.kt             # NEW: guard-политика и наборы полей (без БД)
└── SongFieldUpdateSqlTest.kt        # NEW: SQL-билдер — только разрешённые колонки

docs/features/lyrics-search-preserve-song-fields.md            # NEW (Constitution VI, FR-009)
knowledge/domains/persistence/components/field-scoped-save.md  # NEW L3
knowledge/domains/persistence/domain.md                        # + L3-ссылка
knowledge/domains/README.md                                    # + компонент в таблице
knowledge/domains/catalog/components/song-entity.md            # + контракт сохранения
```

**Structure Decision**: изменения локализованы в backend-модуле `karaoke-app`
(модель `Song` + её вызывающие). Новых модулей, фронтенд-правок и миграций нет.

## Phase 0 — Research (итог)

Полные решения и альтернативы — в [research.md](research.md). Кратко:

- **R1** — перечитывание и блокировка строки не устраняют дефект: diff считается от
  устаревшего объекта, `UPDATE` пишет значения объекта. Зафиксировано как тест-кейс «не
  повторять».
- **R2** — вводится примитив `SongFieldUpdate.apply(...)`: транзакция + `FOR NO KEY UPDATE`
  + `UPDATE` только колонок операции + повторный `SELECT` для фактического diff.
- **R3** — переход `id_status 0→1` вычисляется под блокировкой по актуальному состоянию
  (никакого SQL `CASE` не требуется): статус читается в транзакции, поэтому решение
  «понижать/не понижать» принимается по свежему значению.
- **R4** — «не перезаписывать существующий текст»: guard по фактическому `source_text`
  (пустая строка / `[""]` / `NULL`) под блокировкой.
- **R5** — побочные эффекты сохраняются: SSE `recordChange` (по фактическому diff),
  `SyncAudioDescendants.onSongSaved`, push в remote, `crossedReadyThreshold`; не переносятся
  только те, что относятся к полной записи (`renameFilesIfDiff`, version-bump, `markNews…`).
- **R6** — тестируемость: чистые функции (`planLyricsWrite`, `buildFieldScopedUpdateSql`) —
  JUnit 5 без БД, как `SyncAudioDescendantsTest`.
- **R7** — три помощника копирования переводятся на тот же примитив со своими наборами полей.
- **R8** — 50 вызовов `saveToDbLocked()` (14 файлов, в основном сервисы публикации) не входят
  в scope #324
  (FR-008 называет только операции подстановки данных); их аудит — отдельная задача.

## Phase 1 — Design (итог)

- [data-model.md](data-model.md) — наборы полей по операциям, переходы статуса, инварианты,
  соответствие «поле → колонка → тип».
- [contracts/field-scoped-save.md](contracts/field-scoped-save.md) — контракт примитива
  (сигнатура, транзакция, гарантии, коды результата, побочные эффекты).
- [contracts/lyrics-write-policy.md](contracts/lyrics-write-policy.md) — таблица «условие →
  исход» и наборы полей для четырёх операций.
- [contracts/log-format.md](contracts/log-format.md) — формат событий для операционного разбора.
- [contracts/manual-test-checklist.md](contracts/manual-test-checklist.md) — приёмка руками
  (включая сценарий #324 и проверку «ни одно лишнее поле не изменилось»).
- [quickstart.md](quickstart.md) — команды сборки, тестов и ручной проверки.

## Post-Design Constitution Re-check

После проектирования гейты не изменились: примитив не вводит ORM, не меняет схему БД,
не трогает фронтенды, оставляет `recordhash`-триггер единственным источником sync-diff.
Единственное добавление — новый Knowledge-компонент, что соответствует Linking Protocol
(`domains/README.md` + `domain.md`).

## Complexity Tracking

Нарушений Constitution Check нет — таблица не заполняется.
