# Quickstart: Идемпотентная санитиризация путей и имён файлов и папок

**Feature**: `304-idempotent-path-sanitize`
**Date**: 2026-09-04
**Audience**: разработчик Karaoke, который хочет запустить тесты и
проверить работу санитайзера end-to-end.

## Prerequisites

- JDK 17 (`java -version` → 17.x).
- Gradle wrapper (`./gradlew`).
- Git branch: `304-idempotent-path-sanitize` (уже создана).
- OpenProject issue #53 (уже claimed: status = In progress, assignee = ai-agent).

## 1. Сборка

```bash
cd /home/nsa/Karaoke
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin --parallel
```

Ожидаемый результат: `BUILD SUCCESSFUL`. Никаких новых зависимостей не
требуется (slf4j уже транзитивно через Spring Boot starter).

## 2. Запуск unit-тестов

```bash
cd /home/nsa/Karaoke
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test --tests "com.svoemesto.karaokeapp.SanitizePathTest"
```

Ожидаемый результат: `BUILD SUCCESSFUL`. Все тесты `SanitizePathTest`
должны быть зелёными.

### Категории тестов (FR-009)

| Категория | Что проверяет | Пример теста |
|-----------|---------------|--------------|
| **Каждый символ таблицы** | `sanitize("!") == "_"`, `sanitize("?") == "_"` и т.д. | `test_exclamation_mark_replaced_with_underscore` |
| **Комбинации** | `sanitize("a!b?c") == "a_b_c"` | `test_combination_of_problem_symbols` |
| **Unicode (кириллица)** | `sanitize("Лучшее!") == "Лучшее_"` | `test_cyrillic_preserved_with_exclamation_replaced` |
| **Пустые строки** | `sanitize("") == ""` | `test_empty_string` |
| **Only-problematic** | `sanitize("!?*") == "___"` | `test_only_problematic_chars` |
| **Идемпотентность результата** | `sanitize(sanitize(s)) == sanitize(s)` | `test_idempotent_for_each_symbol` |
| **Side-effect идемпотентность (FR-014)** | `count_logs(sanitize(s))` == `count_logs(sanitize(sanitize(s)))` | `test_no_log_on_idempotent_rerun` с `ListAppender<ILoggingEvent>` |
| **Legacy-mapping (FR-004)** | `sanitize("Queen' s") == "Queen` + ` s"` (после двух проходов: preserve) | `test_legacy_mapping_idempotent` |
| **Без truncation (Q3)** | Длинное имя НЕ обрезается (если > 255 байт, sanitizer не вмешивается) | `test_long_name_not_truncated` |
| **Обратная совместимость с прод-именами (FR-004)** | 100 случайных имён из прод-БД → sanitize → результат == исходник | `test_backward_compatibility_with_prod_samples` |

## 3. Линтеры (FR-007 конституции)

```bash
cd /home/nsa/Karaoke
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:ktlintCheck
```

Ожидаемый результат: `BUILD SUCCESSFUL` (или violations только в
baseline).

Если есть новые нарушения, не покрытые baseline — обновить baseline:
```bash
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:ktlintGenerateBaseline
git diff config/ktlint/baseline-karaoke-app.xml  # Проверить, что diff разумный
```

## 4. KDoc coverage (FR-006 конституции)

```bash
cd /home/nsa/Karaoke
bash tools/check-kdoc-coverage.sh
```

Ожидаемый результат: coverage ≥ 50% (строгий gate в CI).

## 5. Pre-commit (если будете коммитить)

```bash
cd /home/nsa/Karaoke
pre-commit run --all-files
```

Проверяет:
- `.gitignore` не нарушен (секреты не трекаются, FR-VIII конституции).
- Линтеры.
- KDoc/JSDoc coverage.

## 6. Ручная проверка в REPL (Kotlin scratch)

Открыть `Tools → Kotlin → REPL` (или `kotlinc -script`) и выполнить:

```kotlin
import com.svoemesto.karaokeapp.sanitizePathSegment
import com.svoemesto.karaokeapp.sanitizePath

// Базовый кейс из issue #53
println("!"  .sanitizePathSegment())    // "_"
println("!?*".sanitizePathSegment())    // "___"
println("".sanitizePathSegment())       // ""

// Кириллица
println("Лучшее!".sanitizePathSegment()) // "Лучшее_"

// Идемпотентность
val s1 = "Hello, World!?"
val s2 = s1.sanitizePathSegment()
val s3 = s2.sanitizePathSegment()
println(s2 == s3)  // true

// Полный путь
println("/path/to/file!.mp3".sanitizePath())  // "/path/to/file_.mp3"

// Legacy-mapping (idempotent)
val s = "Queen$1's*file:"
val once = s.sanitizePathSegment()
val twice = once.sanitizePathSegment()
println(once)   // "Queens1`xfile-"
println(twice)  // "Queens1`xfile-" (идентично once)
```

## 7. End-to-end: импорт папки с `!` в имени (User Story 1)

### Setup
1. Поднять локальную БД (`deploy/do.sh start_local_db` или эквивалент).
2. Поднять `karaoke-app` локально (`deploy/do.sh start_karaoke_app`).
3. Поднять `webvue3` локально (`deploy/do.sh start_webvue3`).
4. Авторизоваться в админке.

### Test
1. Создать папку: `mkdir -p /tmp/import-test/2012\ -\ Лучшее\!/`
2. Положить туда 2 MP3: `cd /tmp/import-test/2012\ -\ Лучшее\!/ && touch "Track 1.mp3" "Track 2.mp3"`
3. В админке выбрать «Импорт из папки», указать `/tmp/import-test/`.
4. Проверить:
   - В логах `karaoke-app` есть строки вида `Sanitize [pos=12, char='!']: "2012 - Лучшее!" → "2012 - Лучшее_"`.
   - В БД `tbl_albums` появилась запись с `album_name = '2012 - Лучшее_'`.
   - В БД `tbl_songs` появились 2 записи с правильным `file_absolute_path`.
   - Физические файлы на диске найдены (lookup по санитайзенному пути).
5. Повторить импорт — НЕ должно появиться дубликатов (idempotency).

### Verify
```bash
# В БД:
SELECT id, album_name FROM tbl_albums WHERE album_name LIKE '2012%';
# Должно быть 1 запись.

SELECT id, song_name, file_name FROM tbl_songs WHERE song_name LIKE '%Лучшее%';
# Должно быть 2 записи с правильными именами.

# В логах:
docker logs karaoke-app | grep "Sanitize.*Лучшее"
# Должны быть ровно 2 INFO-записи (по одной на каждый файл, т.к. имя
# папки санитайзенное один раз, потом в БД — preserve).

# В файловой системе:
ls "/path/to/storage/2012 - Лучшее_/" 2>&1
# Должны быть файлы (доказательство того, что lookup по санитайзенному пути работает).
```

## 8. Обратная совместимость (User Story 3)

### Setup
- В прод-БД или на прод-like стенде: 100+ песен с именами, санитайзенными
  по старому алгоритму (т.е. `!`/`?` удалены, `$` → `s`, `'` → `` ` ``).

### Test
```bash
# Скрипт для верификации (псевдокод):
SELECT file_name FROM tbl_songs ORDER BY random() LIMIT 100
# Для каждого результата:
#   actual = sanitize(file_name)
#   expected = file_name  # потому что уже санитайзенный
#   assert actual == expected
```

Ожидаемый результат: 0 ошибок. Все 100 имён сохраняются как-is после
нового алгоритма (доказательство идемпотентности с прод-данными).

## Done Checklist

- [ ] Все unit-тесты `SanitizePathTest` зелёные (`./gradlew :karaoke-app:test`).
- [ ] ktlintCheck проходит без новых нарушений.
- [ ] KDoc coverage ≥ 50% (`tools/check-kdoc-coverage.sh`).
- [ ] Manual REPL test (шаг 6) показывает корректное поведение на всех
      примерах.
- [ ] End-to-end импорт папки с `!` (шаг 7) работает без ошибок.
- [ ] Обратная совместимость с прод-данными (шаг 8) — 0 регрессий.
- [ ] `docs/features/idempotent-path-sanitize.md` создан (FR-010).
- [ ] `AGENTS.md` TOP-10 обновлён (FR-011, добавить TOP-11).
- [ ] OpenProject issue #53 → `mark-review` после коммита + PR.

## References

- `spec.md` — основная спека.
- `data-model.md` — таблица замен.
- `contracts/sanitizer-contract.md` — API contract.
- `plan.md` — implementation plan.
- `research.md` — implementation decisions.