# Idempotent Path Sanitize (Санитайзер путей)

> **Branch**: `304-idempotent-path-sanitize`
> **OpenProject**: [#53](http://localhost:8080/work_packages/53) (Санитиризации путей и имён файлов и папок в проекте)
> **Spec**: `specs/304-idempotent-path-sanitize/spec.md`
> **Created**: 2026-09-04
> **Status**: ✅ Implemented

## Что делает

Единый идемпотентный санитайзер путей и имён файлов/папок в проекте Karaoke.
Заменяет «проблемные» символы (`!`, `?`, shell-метасимволы, control-chars)
на безопасный `_`, сохраняет legacy-mapping для обратной совместимости
с прод-данными, логирует фактические замены через slf4j.

## Где находится

- **Ядро**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/SanitizePath.kt`
- **Обёртки**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Extentions.kt`
  (функции `rightFileNameSymbols`, `sanitizeSongFileName`, `rightFileName`)
- **Тесты**: `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/SanitizePathTest.kt`

## Контракт (FR-001..FR-014)

См. `specs/304-idempotent-path-sanitize/spec.md` для полной спеки.
Краткая выжимка:

- **FR-001**: `sanitize(sanitize(s)) == sanitize(s)` для любого `s`
- **FR-002**: 12 «проблемных» символов заменяются на `_`:
  `!`, `?`, `\n`, `\r`, `\t`, `\u0000`, `<`, `>`, `|`, `&`, `;`, `"`
- **FR-003**: «безопасные» символы сохраняются:
  буквы (включая кириллицу), цифры, `-`, `_`, `.`, `(`, `)`, `[`, `]`,
  `+`, `=`, `,`, `~`, `@`, `#`, `%`, `^`
- **FR-004**: legacy-mapping (идемпотентно):
  `'` → `` ` ``, `$` → `s`, `*` → `x`, `:` → `-`
- **FR-005**: 200+ существующих вызывающих мест (`StemJobProcessing.kt`,
  `KaraokeProcess.kt`, `model/Song.kt`, ...) не сломаны — обёртки
  остаются как тонкие алиасы над ядром.
- **FR-006**: два варианта API:
  `String.sanitizePathSegment()` — для «голых» фрагментов (без разделителей)
  `String.sanitizePath()` — для полных путей (сохраняет `/`, `\` как разделители)
- **FR-007**: дедупликация коллизий (`(N)` суффикс) — вне санитайзера,
  работает на уже-санитайзенных именах.
- **FR-008**: `wrapInQuotes()` остаётся вторым уровнем защиты shell.
- **FR-009**: 40 unit-тестов покрывают таблицу замен, идемпотентность,
  обратную совместимость, side-effect идемпотентность.
- **FR-014**: INFO-логи через slf4j при каждой **фактической** замене;
  повторный прогон не плодит новых логов.

## Таблица замен (машино-читаемая)

| Символ | Замена | Источник | Категория |
|--------|--------|----------|-----------|
| `!` | `_` | FR-002 | Problem-symbol (был drop, теперь replace — фикс #53) |
| `?` | `_` | FR-002 | Problem-symbol (был drop, теперь replace — фикс #53) |
| `\n` | `_` | FR-002 | Problem-symbol (control) |
| `\r` | `_` | FR-002 | Problem-symbol (control) |
| `\t` | `_` | FR-002 | Problem-symbol (control) |
| `\u0000` | `_` | FR-002 | Problem-symbol (control) |
| `<` | `_` | FR-002 | Problem-symbol (shell-meta) |
| `>` | `_` | FR-002 | Problem-symbol (shell-meta) |
| `|` | `_` | FR-002 | Problem-symbol (shell-meta) |
| `&` | `_` | FR-002 | Problem-symbol (shell-meta) |
| `;` | `_` | FR-002 | Problem-symbol (shell-meta) |
| `"` | `_` | FR-002 | Problem-symbol (FS-dangerous) |
| `'` | `` ` `` | FR-004 | Legacy-mapping (preserve on idempotent re-run) |
| ` ` | `s` | FR-004 | Legacy-mapping (preserve on idempotent re-run) |
| `*` | `x` | FR-004 | Legacy-mapping (preserve on idempotent re-run) |
| `:` | `-` | FR-004 | Legacy-mapping (preserve on idempotent re-run) |
| `/` | `_` (только в `sanitizePathSegment`) | FR-001 | Path separator (защита от выхода за пределы папки) |
| `\` | `_` (только в `sanitizePathSegment`) | FR-001 | Path separator (защита от выхода за пределы папки) |

## Идемпотентность (формально)

Для любого `s: String`:

```
sanitizePathSegment(sanitizePathSegment(s)) == sanitizePathSegment(s)
sanitizePath(sanitizePath(s)) == sanitizePath(s)
```

Это инвариант, проверяемый в `SanitizePathTest` (10+ параметризованных тестов).

### Side-effect идемпотентность (FR-014)

```
count_logs(sanitizePathSegment(s)) == count_logs(sanitizePathSegment(sanitizePathSegment(s)))
```

Проверяется через Logback `ListAppender<ILoggingEvent>` в `SanitizePathTest`.
Реализация: лог пишется **только если была хотя быная замена** (`changed == true`).

## Граница с дедупликатором (FR-007)

Санитайзер **не знает** о коллизиях при импорте. Дедупликация работает
**снаружи** (например, в `Song.createFromPath`):

1. Исходное имя → `sanitize()` → санитайзенное имя.
2. Если в `rootFolder` уже есть песня с таким именем → добавить суффикс `(N)`.
3. Суффикс — это **не часть** санитайзенного имени, а пост-обработка.

Это позволяет:
- Санитайзер быть чистым и идемпотентным.
- Дедупликатор не делать дополнительной санитиризации.
- Логику коллизий изменять без правок санитайзера.

## Test coverage map (FR-009)

| Тест | Покрывает |
|------|-----------|
| `sanitizePathSegment заменяет восклицательный знак на _` | US1, FR-001, FR-002 |
| `sanitizePathSegment заменяет вопросительный знак на _` | US1, FR-001, FR-002 |
| `sanitizePathSegment обрабатывает пустую строку и only-problematic` | US1, Q1 (clarification) |
| `sanitizePathSegment сохраняет кириллицу и заменяет проблемные символы` | US1, FR-003, FR-012 |
| `sanitizePath сохраняет разделители и санитайзит сегменты` | FR-006 |
| `обёртки в Extentions вызывают SanitizePath` | FR-005 |
| `FR-002 каждый проблемный символ заменяется на _` (8 parameterized) | US2, FR-002 |
| `FR-002 управляющие символы тоже заменяются на _` | US2, FR-002 |
| `FR-003 безопасные символы сохраняются` (18 parameterized) | US2, FR-003 |
| `FR-004 legacy mapping применяется идемпотентно` | US2, FR-004 |
| `FR-014 side-effect идемпотентность` | US2, FR-014 |
| `FR-014 sanitize не пишет лог если ничего не изменилось` | US2, FR-014 |
| `US3 прод-имена с удалёнными проблемными символами сохраняются` | US3, FR-004 |
| `US3 прод-имена с legacy mapping сохраняются идемпотентно` | US3, FR-004 |
| `US3 прод-имена с уже идемпотентной структурой полностью сохраняются` | US3, SC-003 |
| `US3 синтетическая выборка 100+ legacy-имён идемпотентна` | US3, SC-003 (100+ имён) |

**Всего**: 40 unit-тестов, 0 failures, 0 errors.

## Использование

### Прямой вызов (новый код)

```kotlin
import com.svoemesto.karaokeapp.sanitizePathSegment
import com.svoemesto.karaokeapp.sanitizePath

val safe = "Лучшее!".sanitizePathSegment()  // "Лучшее_"
val path = "/path/to/file!.mp3".sanitizePath()  // "/path/to/file_.mp3"
```

### Через обёртки (legacy-код, 200+ вызывающих мест)

```kotlin
val safe = "Лучшее!".rightFileNameSymbols()  // "Лучшее_"
val safe = "Лучшее!".sanitizeSongFileName()  // "Лучшее_"
val path = "/path/to/file!.mp3".rightFileName()  // "/path/to/file_.mp3"
```

## Логирование (FR-014)

При **каждой фактической** замене пишется INFO-запись:

```
INFO  Sanitize: "2012 - Лучшее!" -> "2012 - Лучшее_"
```

На повторный прогон `sanitize(sanitize(s))` не пишется ни одной записи
(потому что `changed == false`), что обеспечивает side-effect идемпотентность.

## Cross-references

- `specs/304-idempotent-path-sanitize/spec.md` — основная спека.
- `specs/124-filename-sanitization-rename/` — родительская спека
  (импорт песен с проблемными символами).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/SanitizePath.kt` — реализация.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Extentions.kt` — обёртки.
