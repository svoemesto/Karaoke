# Tasks: Schema-cache в KaraokeDbTable.loadList (FR-102)

**Input**: Design documents from `/specs/243-db-table-schema-cache/`
- `plan.md` (required) — дизайн, структуры данных, Constitution Check
- `spec.md` (required) — US1 (P1), US2 (P2) + Edge Cases + FR-001..FR-010

**Tests**: ручные по quickstart ниже. В проекте автоматических тестов нет (AGENTS.md, раздел «Тесты»).

**Organization**: задачи сгруппированы по user story. Изменения локализованы в одном файле
(`KaraokeDbTable.kt`) + одна регистрация свойства (`KaraokeProperties.kt`).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно запустить параллельно (разные файлы, нет зависимостей)
- **[Story]**: к какой user story относится задача (US1, US2)
- Include exact file paths in descriptions

## Path Conventions

- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/KaraokeDbTable.kt`
- Backend (регистрация свойства): `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt`

## Quickstart (ручная проверка)

Для локальной проверки (после реализации):
1. Запустить `karaoke-app` в DEBUG с логированием SQL PostgreSQL (или временно добавить
   `println(sql)` в `columnsFromDb()`).
2. Вызвать из любого места `Song.loadList(args, db)` дважды — на втором вызове SQL к
   `information_schema.columns` НЕ должно быть в логе.
3. Вызвать `invalidateSchemaCache()` — следующий вызов снова идёт в БД.
4. Поменять `karaoke.db.schema_cache.enabled = false` в `Karaoke.properties` — каждый вызов
   идёт в БД.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: подготовительные работы — прочитать существующий код, найти всех caller'ов,
подтвердить наличие API в `KaraokeProperties`.

- [X] T001 Подтвердить чистый `git status` в worktree `/tmp/karaoke-243` на ветке
  `243-db-table-schema-cache` (выполнить `git status`, `git log --oneline -3`).
- [X] T002 Прочитать текущий `KaraokeDbTable.kt:columns()` (строки 224-257) — зафиксировать
  точную сигнатуру, SQL, обработку SQLException, finally-блок.
- [X] T003 Подтвердить наличие публичного метода `KaraokeProperties.getBoolean(key: String): Boolean`
  (через grep по `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt`).
- [X] T004 Найти всех caller'ов `KaraokeDbTable.loadList` через grep (`grep -rn "KaraokeDbTable.loadList"
  /tmp/karaoke-243/karaoke-app/src`); задокументировать в отчёте, что они НЕ требуют изменений
  (изменение прозрачно на уровне companion object).
- [X] T005 Подтвердить, что `KaraokeConnection.name` — стабильный строковый идентификатор
  (через чтение `KaraokeConnection.kt`, поле `open val name: String`).

**Checkpoint**: понимание существующего кода и API подтверждено.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: подготовить структуры данных кеша (entry, map, TTL const). Без этого ни одна
user story не может быть реализована.

**⚠️ CRITICAL**: никакая user story не может стартовать до завершения этой фазы.

- [X] T006 В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/KaraokeDbTable.kt`
  в начало `companion object` (после `companion object {`) добавить импорт
  `java.util.concurrent.ConcurrentHashMap` (если ещё не импортирован).
- [X] T007 В `companion object` `KaraokeDbTable.kt` добавить приватные объявления:
  - `private data class SchemaCacheEntry(val columnNames: List<String>, val expiresAtMs: Long)`
  - `private val schemaCache = ConcurrentHashMap<Pair<String, String>, SchemaCacheEntry>()`
  - `private const val SCHEMA_CACHE_TTL_MS = 3_600_000L`
- [X] T008 Подтвердить, что импорт `java.util.concurrent.ConcurrentHashMap` присутствует
  (если нет — добавить в начало файла в секцию `import`).

**Checkpoint**: структуры данных добавлены, компиляция `KaraokeDbTable.kt` всё ещё проходит
(пока без использования — просто объявления).

---

## Phase 3: User Story 1 — Прозрачный schema-cache при loadList (Priority: P1)

**Goal**: добавить кеширование в `columns()` + вынести существующую логику в `columnsFromDb()`
+ регистрация свойства в `KaraokeProperties` + KDoc.

**Independent Test criteria**: первый вызов `loadList(ignoreUseInList=false)` для пары
`(tableName, db)` идёт в БД; второй — из кеша.

- [X] T009 [US1] Модифицировать `KaraokeDbTable.columns()` (текущий блок 224-257):
  - Добавить проверку `isSchemaCacheEnabled()` в начале; если `false` — делегировать
    напрямую в `columnsFromDb(...)` без кеша.
  - Перед SQL проверить `schemaCache[tableName to database.name]`: если `expiresAtMs > now`,
    вернуть `entry.columnNames`; если `expiresAtMs <= now` — `schemaCache.remove(key, entry)`
    и идти в БД.
- [X] T010 [US1] Вынести существующую логику SQL в `private fun columnsFromDb(tableName: String,
  database: KaraokeConnection): List<String>` — тело один-в-один из текущего `columns()`,
  включая println, try/catch, finally.
- [X] T011 [US1] В `columnsFromDb()` после успешного SQL (перед `return result`) добавить
  возврат `result` через вызывающий код, который сам решает, кешировать ли. Альтернатива:
  `columnsFromDb` возвращает `List<String>` без знания о кеше (выбран этот вариант — проще).
- [X] T012 [US1] В `columns()` после получения `fresh = columnsFromDb(...)` сохранить в кеш
  ТОЛЬКО если `fresh.isNotEmpty()`:
  ```kotlin
  if (fresh.isNotEmpty()) {
      schemaCache[key] = SchemaCacheEntry(
          columnNames = fresh,
          expiresAtMs = now + SCHEMA_CACHE_TTL_MS,
      )
  }
  ```
  (FR-002 + FR-003)
- [X] T013 [US1] Добавить приватный метод `isSchemaCacheEnabled(): Boolean`:
  ```kotlin
  private fun isSchemaCacheEnabled(): Boolean = try {
      KaraokeProperties.getBoolean("karaoke.db.schema_cache.enabled")
  } catch (_: Throwable) { true }
  ```
  (FR-007)
- [X] T014 [US1] В `KaraokeProperties.kt` в `listKaraokeProperties` добавить запись
  ```kotlin
  KaraokeProperty(
      key = "karaoke.db.schema_cache.enabled",
      defaultValue = true,
      description = "Кешировать список колонок information_schema в KaraokeDbTable.loadList (TTL=1ч). false = каждый loadList идёт в БД (отладка schema-related багов).",
  ),
  ```
- [X] T015 [US1] Добавить KDoc на `columns()`, `columnsFromDb()`, `isSchemaCacheEnabled()`,
  `SchemaCacheEntry`, `schemaCache`, `SCHEMA_CACHE_TTL_MS` — со ссылкой на спек 243 и
  кратким описанием назначения (Constitution § VI — FR-008).

**Checkpoint**: US1 функционально завершён. SC-001, SC-002, SC-005 выполнены (требуют
ручной проверки).

---

## Phase 4: User Story 2 — Управляемая инвалидация кеша (Priority: P2)

**Goal**: публичный метод `invalidateSchemaCache(...)` с 4 режимами + KDoc с примерами.

**Independent Test criteria**: после `invalidateSchemaCache(...)` следующий `loadList`
идёт в БД; последующие — снова из кеша.

- [X] T016 [US2] В `companion object` `KaraokeDbTable.kt` добавить публичный метод:
  ```kotlin
  @Suppress("unused")
  fun invalidateSchemaCache(
      tableName: String? = null,
      database: KaraokeConnection? = null,
  ) {
      when {
          tableName != null && database != null ->
              schemaCache.remove(tableName to database.name)
          tableName != null ->
              schemaCache.keys.removeAll { it.first == tableName }
          database != null ->
              schemaCache.keys.removeAll { it.second == database.name }
          else ->
              schemaCache.clear()
      }
  }
  ```
  (FR-005)
- [X] T017 [US2] KDoc на `invalidateSchemaCache(...)` с примерами:
  - Полная очистка
  - По `tableName`
  - По `database`
  - По паре `(tableName, database)`
  - Типичные сценарии вызова (пост-миграционный хук, тест).
  Ссылка на FR-005 и спек 243.
- [X] T018 [US2] Задокументировать в KDoc ограничение `schemaCache.keys.removeAll` —
  возможен `ConcurrentModificationException` при параллельной модификации (типичный
  вызов — редкий, в хуках/тестах, приемлемо).
- [X] T019 [US2] (опционально) Проверить, что `@Suppress("unused")` не нужен — поискать
  caller'ов `invalidateSchemaCache` (их нет в рамках этой спеки; suppress остаётся
  на случай будущих ручных вызовов из тестов).
- [X] T020 [US2] Самопроверка: 4 режима инвалидации покрывают все комбинации:
  - `(null, null)` → clear
  - `("tbl_x", null)` → remove by tableName
  - `(null, db)` → remove by database
  - `("tbl_x", db)` → remove specific key
  ✅
- [X] T021 [US2] Подтвердить, что SC-003 (после invalidate → SQL виден, потом снова из кеша)
  выполнен через логику US1 (US2 только сбрасывает запись; следующий обращение через US1
  идёт в БД из-за cache miss, потом сохраняет заново).

**Checkpoint**: US2 функционально завершён. SC-003 выполнен.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: обязательная проверка (AGENTS.md § «Обязательная проверка после ЛЮБОГО изменения
кода»), подготовка отчёта. Без коммита (по требованию задачи).

- [X] T022 Запустить `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`.
  **Должно пройти без ошибок**. Если падает — починить (типичные причины: не импортирован
  `ConcurrentHashMap`, неправильная сигнатура `KaraokeProperties.getBoolean`).
- [X] T023 Запустить `./gradlew :karaoke-web:ktlintCheck`. **Без НОВЫХ нарушений**. Если
  падает — починить (длинные строки в KDoc — разбить на несколько строк).
- [X] T024 Запустить `./gradlew :karaoke-web:bootJar --parallel`. **jar должен собраться**.
- [X] T025 Подтвердить `git status` — изменения только в двух файлах: `KaraokeDbTable.kt` и
  `KaraokeProperties.kt` + новые файлы спеки в `specs/243-db-table-schema-cache/`.
- [X] T026 Подтвердить `git diff --stat` — разумный размер изменения (ориентир: < 200 строк
  в `KaraokeDbTable.kt`).
- [X] T027 НЕ делать коммит. Ветка остаётся в worktree с незакоммиченными изменениями.
- [X] T028 НЕ обновлять LiveDocs (`livedocs/`) — out of scope для этого PR (отдельная задача
  T030, отдельный коммит).
- [X] T029 Сформировать отчёт (см. ниже в задаче).
- [X] T030 (out of scope) Обновить `livedocs/architecture-notes.md` записью о schema-cache —
  в отдельном коммите после мержа этого PR (FR-014 из AGENTS.md).

**Checkpoint**: всё готово к ревью и мёрджу.

---

## Dependencies & Execution Order

- Phase 1 (Setup) → нет зависимостей, выполняется первой.
- Phase 2 (Foundational) → зависит от Phase 1.
- Phase 3 (US1) → зависит от Phase 2.
- Phase 4 (US2) → зависит от Phase 2 (можно параллельно с Phase 3, но в одном файле — последовательно).
- Phase 5 (Polish) → зависит от Phase 3 и Phase 4.

## Формат отчёта (T029)

```markdown
## Schema-cache FR-102 — отчёт

### Реализовано (завершённые задачи)
- [x] T001 ... T005 (Setup)
- [x] T006 ... T008 (Foundational)
- [x] T009 ... T015 (US1)
- [x] T016 ... T021 (US2)
- [x] T022 ... T028 (Polish, без коммита)

### Изменённые файлы + diff size
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/KaraokeDbTable.kt`: +N/-M строк
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt`: +6/-0 строк
- `specs/243-db-table-schema-cache/spec.md`: новый файл
- `specs/243-db-table-schema-cache/plan.md`: новый файл
- `specs/243-db-table-schema-cache/tasks.md`: новый файл
- `specs/243-db-table-schema-cache/checklists/requirements.md`: новый файл

### Результаты проверки
- `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`: PASS / FAIL
- `./gradlew :karaoke-web:ktlintCheck`: PASS / FAIL (N новых нарушений)
- `./gradlew :karaoke-web:bootJar --parallel`: PASS / FAIL

### Отклонения от tasks.md / plan.md
(список или "нет")

### Caveats / ограничения
(список или "нет")
```
