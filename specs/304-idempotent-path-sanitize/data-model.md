# Data Model: Идемпотентная санитиризация путей и имён файлов и папок

**Feature**: `304-idempotent-path-sanitize`
**Date**: 2026-09-04

> Санитайзер — **pure function** над `String`. «Data model» здесь —
> это **таблица замен** (Char → Char) + API-контракт (String → String).

## Сущности

### 1. `SanitizePathSegment` (новый файл `SanitizePath.kt`)

**Что представляет**: Объект-неймспейс с публичными extension-functions
для санитайзинга «голых» фрагментов имён файлов/папок (без разделителей
пути) и полных путей.

**Атрибуты** (логические, не поля):

| Имя | Тип | Описание |
|-----|-----|----------|
|`segment(input: String): String`|fun (extension)|Санитайзит «голый» фрагмент: убирает `/`, `\`, `"`, заменяет проблемные символы, сохраняет legacy-mapping.|
|`path(input: String): String`|fun (extension)|Санитайзит полный путь: сохраняет `/`, `\` как разделители, санитайзит каждый сегмент между ними через `segment()`.|
|`REPLACEMENT_TABLE: Map<Char, Char>`|private const|Таблица замен (см. ниже). `null` в map = символ сохраняется без изменений.|
| `LOG: Logger` | private val | slf4j логгер для INFO-записей (FR-014). |

**Validation rules** (контракт):
- Тип возврата: `String` (не `String?`).
- Не бросает исключений (даже на `""` или only-problematic input).
- Идемпотентность: `sanitize(sanitize(s)) == sanitize(s)` для любого `s`.
- Side-effect идемпотентность: повторный прогон не плодит новых
  лог-записей (FR-014).

### 2. `ReplacementTable` (внутренняя структура, не отдельный класс)

**Что представляет**: Карта замен, на которую опирается `segment()`.

**Тип**: `Map<Char, Char>` (или эквивалент — `when`-выражение).

**Содержимое** (полная таблица — из FR-002, FR-003, FR-004 спеки):

| Символ (Char) | Замена | Источник | Категория |
|---------------|--------|----------|-----------|
| `!` | `_` | FR-002 (был drop, теперь replace) | Problem-symbol |
| `?` | `_` | FR-002 (был drop, теперь replace) | Problem-symbol |
| `\n` | `_` | FR-002 | Problem-symbol (control) |
| `\r` | `_` | FR-002 | Problem-symbol (control) |
| `\t` | `_` | FR-002 | Problem-symbol (control) |
| `\0` | `_` | FR-002 | Problem-symbol (control) |
| `<` | `_` | FR-002 | Problem-symbol (shell-meta) |
| `>` | `_` | FR-002 | Problem-symbol (shell-meta) |
| `\|` | `_` | FR-002 | Problem-symbol (shell-meta) |
| `&` | `_` | FR-002 | Problem-symbol (shell-meta) |
| `;` | `_` | FR-002 | Problem-symbol (shell-meta) |
| `"` | `_` | FR-002 (был drop в `sanitizeSongFileName`, теперь replace) | Problem-symbol (FS-dangerous) |
| `'` | `` ` `` | FR-004 legacy | Legacy-mapping (preserve on idempotent re-run) |
| `$` | `s` | FR-004 legacy | Legacy-mapping (preserve on idempotent re-run) |
| `*` | `x` | FR-004 legacy | Legacy-mapping (preserve on idempotent re-run) |
| `:` | `-` | FR-004 legacy | Legacy-mapping (preserve on idempotent re-run) |

**Символы, которые НЕ заменяются** (preserve as-is — FR-003):
- Все буквы (любые Unicode-категории, включая кириллицу, латиницу, и т.п.).
- Все цифры (`0`-`9`).
- `-` (дефис).
- `_` (подчёркивание — уже после замены).
- `.` (точка).
- `(`, `)` (скобки — структурно нужны для шаблона имени файла).
- `[`, `]` (квадратные скобки — структурно нужны).
- `+`, `=`, `,`, `~`, `@`, `#`, `%`, `^` (прочие безопасные).
- **Разделители пути** (`/`, `\`) — для `path()`, не для `segment()`.

**Идемпотентность таблицы**: для каждой пары (in, out) выполняется
`out ∈ preserve_set` ИЛИ `out ∈ table`. Доказательство:
- `!` → `_` → `_` (preserve) ✅
- `?` → `_` → `_` ✅
- `'` → `` ` `` → `` ` `` (preserve) ✅
- `$` → `s` → `s` (preserve) ✅
- `*` → `x` → `x` (preserve) ✅
- `:` → `-` → `-` (preserve) ✅

Все 16 замен идемпотентны.

**Символы-разделители** (для `path()`, НЕ для `segment()`):
- `/` → сохраняется.
- `\` → сохраняется.
- Оба могут быть в `path()`, не заменяются.

**Символы, которые удаляются** (только в `segment()`, не в `path()`):
- Нет ни одного символа, который **полностью удаляется** (drop) —
  это и есть ключевое отличие от старого `rightFileNameSymbols()`.
- Все 12 «проблемных» символов заменяются на `_` (replace, не drop).
- `/`, `\`, `"` в `segment()` тоже заменяются на `_` (защита от выхода
  за пределы папки).

### 3. `LogEntry` (только для тестов FR-014)

**Что представляет**: Одна запись в логе от санитайзера при фактической
замене.

**Формат**: `Sanitize [pos=N, char='X']: "<before>" → "<after>"`

| Поле | Тип | Описание |
|------|-----|----------|
| `pos` | `Int` | Позиция символа в исходной строке (0-indexed). |
| `char` | `Char` | Заменённый символ. |
| `before` | `String` | Полная исходная строка. |
| `after` | `String` | Полная строка после замены. |

**Контракт идемпотентности side-effect** (FR-014):
- На первом вызове `sanitize(s)` где `s` содержит проблемные символы —
  пишется **N записей** (по одной на каждый проблемный символ).
- На повторном вызове `sanitize(sanitize(s))` — пишется **0 записей**
  (потому что результат первого прогона не имеет проблемных символов,
  и при его обработке ни одна замена не выполняется).

## Связи (Relationships)

```
SanitizePath (object/namespace)
├── segment(input) -> String        # для голых имён
├── path(input) -> String             # для полных путей
├── REPLACEMENT_TABLE: Map<Char, Char>  # private const
└── LOG: Logger                     # private val (slf4j)

SanitizePath.path(input)
└── splits input by '/' или '\'
    └── для каждого сегмента вызывает SanitizePath.segment()
    └── joins segments back с сохранёнными разделителями

SanitizePath.segment(input)
└── для каждого char в input
    ├── если char в REPLACEMENT_TABLE -> LOG.info(...) + replace
    └── иначе -> preserve as-is
```

## State Transitions

N/A — санитайзер **stateless**. Каждый вызов независим.

## Data Volume / Scale

- Типичная длина входной строки: 10-200 символов (имена файлов песен).
- Типичная длина пути: 100-500 символов (с разделителями).
- Количество вызовов за жизненный цикл проекта: 10⁴-10⁶ (импорт +
  SongEdit + каждый shell-команд в пайплайне).
- Аллокаций на вызов: 1 (одна `StringBuilder` или `String` в конце).
- Сложность: O(n) по длине строки.

## Validation Rules (cross-reference)

| Правило | FR | Где проверяется |
|---------|-----|------------------|
|`sanitize("") == ""`|Q1 (clarification), FR-001|`SanitizePathTest`|
|`sanitize("!?*") == "_"`|Q1 (clarification), FR-001, FR-002|`SanitizePathTest`|
|`sanitize(s) == sanitize(sanitize(s))`|FR-001|`SanitizePathTest`|
|Без лог-записей на повторный прогон|FR-014|`SanitizePathTest` с `ListAppender`|
|Сохранение кириллицы|FR-003, FR-012|`SanitizePathTest`|
|Без truncation длинных имён|Q3 (clarification)|`SanitizePathTest` (negative test: имя >255 байт не обрезается)|
|Обратная совместимость с legacy-mapping|FR-004|`SanitizePathTest` с прод-выборкой имён|