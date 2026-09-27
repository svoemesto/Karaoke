# Idempotent Path Sanitize (Санитайзер путей)

> **Status**: active
> **Feature Key**: idempotent-path-sanitize
> **Last Updated**: 2026-09-27
> **Branch**: `304-idempotent-path-sanitize`
> **Created**: 2026-09-04
> **Spec**: [`specs/304-idempotent-path-sanitize/spec.md`](../../specs/304-idempotent-path-sanitize/spec.md)
> **Source**: OpenProject #53 — «Санитиризация путей и имён файлов и папок в проекте»

## Что делает

Единый идемпотентный санитайзер путей и имён файлов/папок в проекте Karaoke.
Заменяет «проблемные» символы (`!`, `?`, shell-метасимволы, control-chars)
на безопасный `_`, сохраняет legacy-mapping для обратной совместимости
с прод-данными, логирует фактические замены через slf4j.

## Зачем

Проблема из OpenProject #53: если в имени папки альбома есть восклицательный знак,
при импорте из папки и при дальнейших действиях файлы не находятся — символ
экранировался/удалялся при санитаризации пути. Пре-импортный анализ спеки 304
зафиксировал три дефекта прежней реализации:

1. **Неидемпотентность по `!` и `?`** — символ удалялся без замены. При первом
   проходе импорта `2012 - Лучшее!` превращалось в `2012 - Лучшее`, и физическая
   папка не находилась; повторный проход «работал». Классический признак
   преобразования «с потерями».
2. **Неявная неидемпотентность при коллизиях** — если два файла после
   санитайзинга совпадали по имени, одному присваивался суффикс `(2)`, что ломало
   кэши, lookup по `rootFolder + fileName` и дедупликацию.
3. **Неполный список проблемных символов** — не покрыты control-chars, кавычки и
   shell-метасимволы (`\n`, `\r`, `\t`, `\0`, `<`, `>`, `|`, `&`, `;`).

Задача — сделать преобразование полностью идемпотентным, не сломав 200+
существующих вызывающих мест (`StemJobProcessing.kt`, `KaraokeProcess.kt`,
`model/Song.kt`, `controllers/SongEditorController.kt`, `controllers/ApiController.kt`).

## Как работает

### Где находится

- **Ядро**: [`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/SanitizePath.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/SanitizePath.kt)
- **Обёртки**: [`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Extentions.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Extentions.kt)
  (функции `rightFileNameSymbols`, `sanitizeSongFileName`, `rightFileName`)
- **Тесты**: [`karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/SanitizePathTest.kt`](../../karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/SanitizePathTest.kt)

### Контракт FR-001..FR-014 (краткая выжимка)

Полная спека — [`specs/304-idempotent-path-sanitize/spec.md`](../../specs/304-idempotent-path-sanitize/spec.md).

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

### Граница с дедупликатором (FR-007)

Санитайзер **не знает** о коллизиях при импорте. Дедупликация работает
**снаружи** (например, в `Song.createFromPath`):

1. Исходное имя → `sanitize()` → санитайзенное имя.
2. Если в `rootFolder` уже есть песня с таким именем → добавить суффикс `(N)`.
3. Суффикс — это **не часть** санитайзенного имени, а пост-обработка.

Это позволяет:

- Санитайзеру быть чистым и идемпотентным.
- Дедупликатору не делать дополнительной санитиризации.
- Менять логику коллизий без правок санитайзера.

### Использование

Прямой вызов (новый код):

```kotlin
import com.svoemesto.karaokeapp.sanitizePathSegment
import com.svoemesto.karaokeapp.sanitizePath

val safe = "Лучшее!".sanitizePathSegment()  // "Лучшее_"
val path = "/path/to/file!.mp3".sanitizePath()  // "/path/to/file_.mp3"
```

Через обёртки (legacy-код, 200+ вызывающих мест):

```kotlin
val safe = "Лучшее!".rightFileNameSymbols()  // "Лучшее_"
val safe = "Лучшее!".sanitizeSongFileName()  // "Лучшее_"
val path = "/path/to/file!.mp3".rightFileName()  // "/path/to/file_.mp3"
```

### Логирование (FR-014)

При **каждой фактической** замене пишется INFO-запись:

```
INFO  Sanitize: "2012 - Лучшее!" -> "2012 - Лучшее_"
```

На повторный прогон `sanitize(sanitize(s))` не пишется ни одной записи
(потому что `changed == false`), что обеспечивает side-effect идемпотентность.

## Инварианты

Правила проекта, релевантные фиче: [constitution.md](../../.specify/memory/constitution.md)
(принцип IX — Knowledge SSoT) и [AGENTS.md](../../AGENTS.md) (обязательная
проверка после изменения; lint-Kotlin запускается с `GRADLE_USER_HOME`).

- **MUST**: `sanitize(sanitize(s)) == sanitize(s)` — для любого `s`, для обоих API
  (FR-001).
- **MUST**: 12 проблемных символов (`!`, `?`, `\n`, `\r`, `\t`, `\u0000`, `<`,
  `>`, `|`, `&`, `;`, `"`) заменяются на `_`, а не удаляются (FR-002).
- **MUST**: безопасные символы (буквы, включая кириллицу, цифры, `- _ . ( ) [ ]
  + = , ~ @ # % ^`) сохраняются без изменений (FR-003).
- **MUST**: legacy-mapping идемпотентен: `'` → `` ` ``, `$` → `s`, `*` → `x`,
  `:` → `-` (FR-004).
- **MUST**: 200+ вызывающих мест не ломаются — обёртки `rightFileNameSymbols`,
  `sanitizeSongFileName`, `rightFileName` остаются тонкими алиасами над ядром
  (FR-005).
- **MUST**: два API-варианта — `sanitizePathSegment()` для «голых» фрагментов и
  `sanitizePath()` для полных путей (сохраняет `/`, `\`) (FR-006).
- **MUST**: дедупликация коллизий (`(N)`) — вне санитайзера (FR-007).
- **MUST**: `wrapInQuotes()` остаётся вторым уровнем защиты shell (FR-008).
- **MUST**: INFO-лог пишется только при фактической замене; повторный прогон не
  плодит лог-записей (FR-014).
- **SHOULD**: покрытие — 40 unit-тестов, 0 failures (FR-009).

## Известные ловушки

- **Legacy-mapping сам должен быть идемпотентным**: замена `'` → `` ` `` не должна
  повторно срабатывать на `` ` ``. Любая новая пара в FR-004 проверяется тестом на
  идемпотентность, иначе ломается FR-001.
- **`sanitizePath` ≠ `sanitizePathSegment`**: `sanitizePath()` сохраняет `/` и `\`
  как разделители, а `sanitizePathSegment()` заменяет их на `_` (защита от выхода
  за пределы папки). Перепутать их — значит либо сломать путь, либо потерять
  защиту.
- **Лог только при `changed == true`**: безусловное логирование ломает
  side-effect идемпотентность (FR-014) и плодит записи на каждом прогоне.
- **Дедупликатор `(N)` — снаружи санитайзера**: попытка встроить суффикс в
  санитайзер возвращает дефект №2 прежней реализации (неидемпотентность при
  коллизиях).
- **`wrapInQuotes()` — не единственная защита**: `` ` `` (получается из `'`) сам
  проблемен для shell, поэтому `wrapInQuotes()` вызывается **после**
  `rightFileName()` (FR-008).
- **`SanitizePath.kt` содержит литеральный NUL-байт** (`'\u0000'` в `when`) —
  инструменты считают файл двоичным: `grep` без `-a`/`read` отказываются с ним
  работать. Для поиска по файлу использовать `grep -a`.

## Ссылки

- [`specs/304-idempotent-path-sanitize/spec.md`](../../specs/304-idempotent-path-sanitize/spec.md) — основная спека (FR-001..FR-014, User Stories, Clarifications).
- [`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/SanitizePath.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/SanitizePath.kt) — ядро санитайзера.
- [`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Extentions.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Extentions.kt) — обёртки (`rightFileNameSymbols`, `sanitizeSongFileName`, `rightFileName`).
- [`karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/SanitizePathTest.kt`](../../karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/SanitizePathTest.kt) — 40 unit-тестов (FR-009, FR-014).
- [`specs/124-filename-sanitization-rename/`](../../specs/124-filename-sanitization-rename/) — родительская спека (импорт песен с проблемными символами).

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
| `$` | `s` | FR-004 | Legacy-mapping (preserve on idempotent re-run) |
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
Реализация: лог пишется **только если была хотя бы одна замена** (`changed == true`).

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
