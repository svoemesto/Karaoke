---
status: Active
slug: 304-idempotent-path-sanitize
related:
  - ../../specs/304-idempotent-path-sanitize/spec.md
  - ../../docs/features/idempotent-path-sanitize.md
  - ../architecture/L3-components.md
---

# 304 — Idempotent Path Sanitize (LiveDoc)

> Drill-down — [specs/304-idempotent-path-sanitize/spec.md](../../specs/304-idempotent-path-sanitize/spec.md).
> Per-feature документ — [docs/features/idempotent-path-sanitize.md](../../docs/features/idempotent-path-sanitize.md).

## Что чинит

**Bug #53** из OpenProject: при импорте папки с `!` или `?` в имени
файлы не находились. Root cause — старый `rightFileNameSymbols()`
**удалял** проблемные символы (drop), а не заменял. Это нарушало
идемпотентность: первый прогон терял данные, второй «работал»
(потому что искал уже по санитайзенному имени).

## Решение

Единое идемпотентное ядро `SanitizePath` в `karaoke-app`:

- `sanitizePathSegment()` — для «голых» фрагментов (заменяет проблемные
  символы на `_`, не удаляет).
- `sanitizePath()` — для полных путей (сохраняет `/` и `\` как разделители,
  санитайзит каждый сегмент между ними).

Существующие обёртки (`rightFileNameSymbols`, `sanitizeSongFileName`,
`rightFileName`) в `Extentions.kt` стали тонкими алиасами над ядром.
200+ вызывающих мест не сломаны.

## Контракт

```
sanitize(sanitize(s)) == sanitize(s) для любого s
count_logs(sanitize(s)) == count_logs(sanitize(sanitize(s))) (FR-014)
```

## Side-effect идемпотентность (FR-014)

Лог-запись INFO пишется через slf4j **только если была хотя бы одна замена**.
Повторный прогон `sanitize(s)` не плодит новых логов — это обеспечивает
side-effect идемпотентность, проверенную в unit-тестах через
Logback `ListAppender<ILoggingEvent>`.

## Таблица замен

| Категория | Символы | Замена |
|-----------|---------|--------|
| FR-002 problem | `!`, `?`, `\n`, `\r`, `\t`, `\u0000`, `<`, `>`, `\|`, `&`, `;`, `"` | `_` |
| FR-002 path separator | `/`, `\` (только в `sanitizePathSegment`) | `_` |
| FR-004 legacy | `'` → `` ` ``, `$` → `s`, `*` → `x`, `:` → `-` | preserve на повторный прогон |
| FR-003 safe | кириллица, `(`, `)`, `[`, `]`, цифры, `-`, `.`, `=`, `@`, `#` | preserve |

## Граница с дедупликатором (FR-007)

Санитайзер **не знает** о коллизиях. Числовой суффикс `(N)` для дедупликации
добавляется **снаружи** санитайзера. Это сохраняет идемпотентность.

## Cross-references

- Спека: `specs/304-idempotent-path-sanitize/spec.md`
- Per-feature документ: `docs/features/idempotent-path-sanitize.md`
- Реализация: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/SanitizePath.kt`
- Обёртки: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Extentions.kt`
- Тесты: `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/SanitizePathTest.kt`
- 40 unit-тестов покрывают таблицу замен, идемпотентность результата,
  side-effect идемпотентность, обратную совместимость с прод-данными.
