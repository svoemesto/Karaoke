# Research: Идемпотентная санитиризация путей и имён файлов и папок

**Feature**: `304-idempotent-path-sanitize`
**Date**: 2026-09-04
**Status**: Phase 0 research complete — все NEEDS CLARIFICATION разрешены

## Summary

Spec (`spec.md`) + Clarifications (Q1: пустая строка, Q2: INFO-логирование,
Q3: без truncation) определили контракт. Этот документ фиксирует
**implementation-level** решения: какой подход использовать для
char-by-char маппинга, как тестировать side-effect идемпотентность,
как минимизировать изменения в 200+ вызывающих местах.

## Источниковая база (что было изучено)

### Существующий код санитайзера (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Extentions.kt`)

Прочитано (280 строк). Извлечено:

1. `String.rightFileNameSymbols()` (lines 32-39) — поэлементная подстановка:
   ```kotlin
   .replace("'", "`")
   .replace("?", "")
   .replace(":", "-")
   .replace("!", "")
   .replace("$", "s")
   .replace("*", "x")
   ```
2. `String.sanitizeSongFileName()` (lines 54-59) — надстройка:
   ```kotlin
   .replace("/", "")
   .replace("\\", "")
   .replace("\"", "")
   .rightFileNameSymbols()
   ```
3. `String.rightFileName()` (line 61) — алиас `rightFileNameSymbols()`.

**Известный дефект**: `!` и `?` заменяются на `""` (drop, без replacement),
что нарушает идемпотентность (Q1 из спеки) и приводит к потере данных
при импорте (issue #53).

### Вызывающие места (200+)

Извлечено через `grep -rn "rightFileName\|sanitizeSongFileName" --include="*.kt" -l` (см. plan.md).
Полный список — в `plan.md` § Technical Context.

**Ключевые паттерны использования**:
- `pathToFile*.rightFileName()` — формирование путей в `model/Song.kt`
- `"$tempFolder/upload.ext".rightFileName()` — shell-команды в
  `StemJobProcessing.kt:38,75,125`
- `listOf("mv", oldPath.rightFileName(), newPath.rightFileName())` —
  ProcessBuilder args в `KaraokeProcess.kt:1405-1461`
- `args["..."].rightFileName().lowercase()` — SQL escape в
  `model/Song.kt:7541-7764`
- `Song.fileName.sanitizeSongFileName()` — только в
  `SongUpdateMapper.kt:227` и `Song.kt:8302` (импорт)

### Существующие тесты (паттерн JUnit 5)

`karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/SpecTagsTest.kt`
прочитан как reference. Паттерн:
- `org.junit.jupiter.api.Test`
- `org.junit.jupiter.api.Assertions.assertEquals`
- Backtick test names: `` `parseLine распознаёт тег без значения` ``
- KDoc на класс с `@see` на spec.

### Тестовая инфраструктура

- `tasks.withType<Test> { useJUnitPlatform() }` в `build.gradle.kts:77-79`.
- `testImplementation("org.springframework.boot:spring-boot-starter-test")` —
  подтягивает JUnit 5, AssertJ, Mockito.
- Тесты **не `@Disabled`** (в отличие от `PlaywrightTests.kt`): например,
  `SpecTagsTest`, `SongStateTest` активны. Это подтверждает, что
  unit-тесты для FR-009 будут запускаться в CI.

### slf4j / Logback

- `org.slf4j.LoggerFactory` уже используется в
  `VkIdTokenRefreshScheduler.kt:42`, `StatsCache.kt`,
  `AutoOneClickSyncScheduler.kt`.
- Паттерн: `private val log = LoggerFactory.getLogger(ClassName::class.java)`.
- `logback-classic` — транзитивная зависимость через Spring Boot starter
  (не объявлена явно в `build.gradle.kts`).

## Decisions

### Decision 1: Char-by-char mapping через Kotlin `when`-выражение

**Что выбрано**: Использовать Kotlin `when` (или `if/else`-цепочку) для
пошаговой обработки символов вместо `String.replace(...)`-chain.

**Rationale**:
- `String.replace(Char, Char)` возвращает новую строку — это нормально
  для одной замены, но цепочка из 12+ `.replace(...)` создаёт 12+
  промежуточных строк в памяти (O(n×m) аллокаций).
- `when` с одним проходом по символам — O(n) с одной аллокацией.
- Логика «сначала проверяем problem-symbol, иначе оставляем как есть» —
  естественно ложится на `when`.

**Alternatives considered**:
- **Regex** (`s.replace(Regex("[!?\\n\\r\\t]"), "_")`) — отклонено:
  - Regex с unicode-классами рискует regression Cyrillic (`\b` ASCII-only,
    задокументировано в `Extentions.kt:222-224` для censor-словаря).
  - Регулярка менее читаема и сложнее отлаживается.
  - `String.replace(Regex(...))` всё равно аллоцирует новую строку.
- **Map<Char, Char>** + `for (c in s) result += map[c] ?: c` — **тоже
  вариант**. Принят как fallback, если `when` окажется менее читаемым
  в конкретном случае.

**Вывод**: предпочтительно `when`, fallback — `Map`.

### Decision 2: Существующие обёртки → тонкие алиасы над новым ядром

**Что выбрано**: В `Extentions.kt` оставить `rightFileNameSymbols`,
`sanitizeSongFileName`, `rightFileName` как однострочные обёртки:
```kotlin
fun String.rightFileNameSymbols(): String = SanitizePath.segment(this)
fun String.sanitizeSongFileName(): String = SanitizePath.segment(this)
fun String.rightFileName(): String = SanitizePath.path(this)
```

**Rationale**:
- 200+ вызывающих мест не должны меняться (FR-005).
- Kotlin **не** поддерживает `typealias` для extension-функций
  (только для типов).
- Однострочный алиас = минимальный diff, KDoc остаётся на месте.

**Alternatives considered**:
- **Удалить обёртки, переиментировать в `SanitizePath.*`** — отклонено:
  требует 200+ правок в `StemJobProcessing.kt`, `KaraokeProcess.kt`,
  `model/Song.kt`, `controllers/*.kt`, `karaoke-web/**/*.kt`. Риск
  regression на каждом правке.
- **`typealias` для функций** — **не поддерживается Kotlin** (только
  для типов).

### Decision 3: slf4j logger — статический field, не DI

**Что выбрано**:
```kotlin
private val log = LoggerFactory.getLogger(SanitizePath::class.java)
```

**Rationale**:
- Соответствует существующему паттерну в `VkIdTokenRefreshScheduler.kt:42`,
  `StatsCache.kt:5`, `AutoOneClickSyncScheduler.kt:10`.
- Не требует DI-рефакторинга extension-function API
  (`String.rightFileName()` нельзя сделать через constructor injection).
- `LoggerFactory.getLogger(Class)` кеширует логгер внутри slf4j — нет
  проблем с производительностью.

**Alternatives considered**:
- **DI через конструктор** — **не применимо** к extension-functions.
- **`@JvmStatic val` в `companion object`** — допустимо, но не даёт
  преимуществ (slf4j сам кеширует).

### Decision 4: Logback `ListAppender<ILoggingEvent>` для тестов FR-014

**Что выбрано**: В unit-тесте создать `ListAppender<ILoggingEvent>`,
прикрепить к `SanitizePath::class.java` логгеру, вызвать `sanitize()`
дважды, проверить количество записей в ListAppender после первого и
после второго прогона — должно быть одинаковым.

**Rationale**:
- `ListAppender` — стандартный паттерн Logback для тестирования.
- Не требует новых зависимостей (logback уже в classpath).
- Позволяет верифицировать **side-effect идемпотентность** (FR-014 +
  обновлённый FR-009) точно и без race conditions.

**Alternatives considered**:
- **slf4j-test (`com.github.valfirst.slf4j-test`)** — отклонено:
  требует новой testImplementation зависимости.
- **Mocking `Logger`** через Mockito — отклонено: сложнее в настройке,
  мокки ломают рефакторинг logger'а.

### Decision 5: Per-feature документ — новый файл `docs/features/idempotent-path-sanitize.md`

**Что выбрано**: Создать новый документ `docs/features/
idempotent-path-sanitize.md` (FR-010 спеки + FR-009 конституции).

**Rationale**:
- Такого документа ещё нет (поиск по `docs/features/` подтвердил).
- Существующая структура `docs/features/` — per-feature, не по тикету.

**Содержание** (план):
1. Контракт FR-001..FR-014 (с краткой выжимкой).
2. Таблица замен (FR-002/003/004) в машино-читаемом виде.
3. Идемпотентный контракт — формальное определение + проверка.
4. Side-effect идемпотентность (FR-014) — формат лог-записи.
5. Граница с дедупликатором (FR-007) — что санитайзер НЕ делает.
6. Test coverage map (FR-009).

### Decision 6: Обновление `AGENTS.md` (TOP-10 ловушек) — отдельный пункт

**Что выбрано**: Добавить в раздел «TOP-10 ловушек» (из `CLAUDE.md`)
пункт:
> **TOP-11**. При правке санитайзера (`Extentions.kt`, `SanitizePath.kt`)
> — соблюдать идемпотентность и обратную совместимость с уже-санитайзенными
> именами на проде (см. `docs/features/idempotent-path-sanitize.md`).
> Удаление символа (drop) вместо замены (replace) ломает идемпотентность
> и ведёт к потере данных при импорте (issue #53).

**Rationale**:
- FR-011 спеки.
- Следует существующему стилю TOP-10 (см. `CLAUDE.md`).

## Open questions deferred to Phase 2 (tasks.md)

1. Точная форма lookup table (`when` vs `Map`) — implementation detail,
   влияет только на читаемость.
2. `private val log = LoggerFactory.getLogger(...)` vs lazy — micro-opt,
   не блокирует.
3. Тестовая структура: один большой `SanitizePathTest` или разделение
   на `SanitizePathSegmentTest` / `SanitizePathTest` — стилистическое.

## References

- `spec.md` — основная спека.
- `Extentions.kt` (lines 32-88) — текущая реализация.
- `VkIdTokenRefreshScheduler.kt` (line 4, 42) — паттерн slf4j.
- `SpecTagsTest.kt` — паттерн JUnit 5 тестов.
- `build.gradle.kts` (lines 62, 77-79) — testImplementation и useJUnitPlatform.
- `constitution.md` — governance.