# API Contract: SanitizePath / SanitizePathSegment

**Feature**: `304-idempotent-path-sanitize`
**Date**: 2026-09-04
**Audience**: разработчики Karaoke (внутренний API), особенно авторы
`StemJobProcessing.kt`, `KaraokeProcess.kt`, `model/Song.kt`,
`controllers/*.kt`, `karaoke-web/services/*.kt`.

## Overview

Два публичных символа в `SanitizePath.kt`:

| Symbol | Назначение | Где использовать |
|--------|------------|------------------|
|`String.sanitizePathSegment(): String`|Санитайзит «голый» фрагмент имени файла/папки (без разделителей).|При импорте (`Song.createFromPath`), при переименовании в SongEdit (`songs2Update`), при формировании отдельных компонентов пути.|
|`String.sanitizePath(): String`|Санитайзит полный путь (с разделителями `/`, `\`). Сохраняет разделители, санитайзит каждый сегмент между ними.|В `model/Song.kt` (`pathToFileLyrics`, `pathToFileKaraoke`, `nameFile*`), в `StemJobProcessing.kt` (`"$tempFolder/upload.ext"`), в `KaraokeProcess.kt` (ProcessBuilder args).|

## Сигнатура

```kotlin
package com.svoemesto.karaokeapp

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Санитайзер путей и имён файлов/папок. Полное описание контракта см. в
 * `docs/features/idempotent-path-sanitize.md` (FR-001..FR-014 спеки
 * `specs/304-idempotent-path-sanitize/spec.md`).
 *
 * Идемпотентен: для любого `s` выполняется
 *   sanitizeX(sanitizeX(s)) == sanitizeX(s).
 *
 * Side-effect идемпотентен: повторный прогон не плодит новых лог-записей
 * (FR-014).
 *
 * @see docs/features/idempotent-path-sanitize.md
 */
object SanitizePath {

    private val LOG: Logger = LoggerFactory.getLogger(SanitizePath::class.java)

    /**
     * Санитайзит «голый» фрагмент имени файла/папки (без разделителей пути).
     *
     * - Разделители `/`, `\` заменяются на `_` (защита от выхода за
     *   пределы папки, FR-001).
     * - Двойные кавычки `"` заменяются на `_` (FR-002).
     * - Символы из FR-002 заменяются на `_` (! ? \n \r \t \0 < > | & ;).
     * - Legacy-mapping FR-004: ' → `, $ → s, * → x, : → -.
     * - Прочие символы (включая кириллицу, (, ), [, ], +, = и т.п.) —
     *   сохраняются (FR-003).
     *
     * @return санитайзенная строка; никогда не null; никогда не бросает
     *   исключений; `sanitizePathSegment("") == ""`,
     *   `sanitizePathSegment("!?*") == "_"`.
     *
     * @see docs/features/idempotent-path-sanitize.md
     */
    fun String.sanitizePathSegment(): String

    /**
     * Санитайзит полный путь. Сохраняет разделители `/` и `\`,
     * санитайзит каждый сегмент между ними через [sanitizePathSegment].
     *
     * Примеры:
     *   sanitizePath("/path/to/file!.mp3") == "/path/to/file_.mp3"
     *   sanitizePath("C:\\Users\\test\\file?.flac") == "C:\\Users\\test\\file_.flac"
     *
     * @return санитайзенный путь; гарантии идемпотентности как у
     *   [sanitizePathSegment].
     *
     * @see docs/features/idempotent-path-sanitize.md
     */
    fun String.sanitizePath(): String
}
```

## Behavior Contract (формальный)

### Вход / Выход

| Метод | Вход | Выход | Исключения |
|-------|------|-------|------------|
|`sanitizePathSegment()`|`String`|Не-null `String`|Нет|
|`sanitizePath()`|`String`|Не-null `String`|Нет|

### Edge cases (контрактные значения)

| Input | `sanitizePathSegment()` | `sanitizePath()` |
|-------|-------------------------|-------------------|
|`""` (empty)|`""`|`""`|
|`"!?!?"`|`"____"` (4 индивидуальных замены)|`"____"` |
|`"!?*"`|`"___"` (3 индивидуальных замены)|`"___"` |
|`"safe_name.mp3"`|`"safe_name.mp3"` (preserve)|`"safe_name.mp3"` |
|`"Лучшее!"` (кириллица + `!`)|`"Лучшее_"` (только `!` заменён)|`"Лучшее_"` |
|`"/path/to/file!.mp3"`|`"_path_to_file_.mp3_"` (всё одной строкой)|`"/path/to/file_.mp3"` (разделители сохранены)|
|`"path\\to\\file"`|`"path_to_file_"` (всё одной строкой)|`"path\\to\\file"` (`\` сохранён как разделитель)|
|`"a'b$c"`|`"a\`bsc"` (legacy mapping)|`"a\`bsc"` |
|`"safe/path"`|`"safe_path"` (всё одной строкой)|`"safe/path"` |
| `   ` (3 пробела) | `   ` (пробелы — preserve) | `   ` |

### Идемпотентность

Для любого `s: String`:
```kotlin
sanitizePathSegment(sanitizePathSegment(s)) == sanitizePathSegment(s)
sanitizePath(sanitizePath(s)) == sanitizePath(s)
```

Это — **инвариант**, который проверяется в unit-тестах (FR-009).

### Side-effect идемпотентность (FR-014)

Если `count_logs(sanitizePathSegment(s)) == N`, то
`count_logs(sanitizePathSegment(sanitizePathSegment(s))) == N`
(а не `2N`). Это тоже проверяется в unit-тестах с `ListAppender`.

### Логирование (FR-014)

- Логгер: `LoggerFactory.getLogger(SanitizePath::class.java)`.
- Уровень: `INFO`.
- Формат: `Sanitize [pos={N}, char='{X}']: "{before}" → "{after}"`,
  где `{N}` — позиция символа (0-indexed), `{X}` — заменённый символ,
  `{before}` — полная исходная строка, `{after}` — полная строка после
  замены.
- Лог пишется **только при фактической замене** символа.
- Если замен не было — лог не пишется (даже DEBUG).

Пример:
```
INFO  Sanitize: Sanitize [pos=12, char='!']: "2012 - Лучшее!" → "2012 - Лучшее_"
```

## Таблица замен (для reference)

См. `data-model.md` § 2. Полная таблица из FR-002/003/004 спеки.

## Backward Compatibility (FR-005)

Существующие обёртки в `Extentions.kt` остаются как тонкие алиасы:

```kotlin
fun String.rightFileNameSymbols(): String = SanitizePath.sanitizePathSegment(this)
fun String.sanitizeSongFileName(): String = SanitizePath.sanitizePathSegment(this)
fun String.rightFileName(): String = SanitizePath.sanitizePath(this)
```

Это **сохраняет** 200+ вызывающих мест без изменений.

## Backward Compatibility with Already-Sanitized Paths (FR-004)

Уже-санитайзенные пути на проде:
- `2012 - Daj zaru.flac` (был `!`, удалён) → `sanitize("2012 - Daj zaru.flac") == "2012 - Daj zaru.flac"` (preserve).
- `Queen s` (был `$`, заменён) → `sanitize("Queen s") == "Queen s"` (preserve).
- `Queen` + `` ` `` (был `'`, заменён) → `sanitize("Queen` + ` ") == "Queen` + ` "` (preserve, `` ` `` сохранён).

**Регрессионный тест**: 100 случайных имён из прод-БД → sanitize → результат совпадает с уже-санитайзенным на диске.

## Thread Safety

`SanitizePath` — stateless object (нет mutable fields кроме статического `LOG`).
Все методы чистые (нет глобального состояния, нет I/O кроме логгера).
**Thread-safe** по конструкции.

## Versioning

Версия = `specs/304-idempotent-path-sanitize/spec.md` v1 (2026-09-04).
При будущих изменениях таблицы замен — bump в `docs/features/
idempotent-path-sanitize.md` + bump в `constitution.md` (Principle VI
требует per-feature doc sync, FR-009).

## References

- `spec.md` — основная спека (FR-001..FR-014).
- `data-model.md` — таблица замен + API-структура.
- `quickstart.md` — как запустить тесты и проверить вручную.
- `Extentions.kt` (lines 32-88) — текущая реализация (заменяемая).
- `VkIdTokenRefreshScheduler.kt:42` — паттерн slf4j.