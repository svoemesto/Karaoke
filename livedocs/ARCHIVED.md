---
status: Archived
slug: livedocs-v1-archived
type: meta-document
related:
  - ../knowledge/README.md
---

# ARCHIVED — Living Documentation v1 (legacy collection)

> **Статус (2026-09-08)**: эта коллекция — **архивная**. Single Source
> of Truth (SSoT) для проекта Karaoke перенесена в
> [`knowledge/`](../knowledge/) (спецификации 322, 326-332).

## Почему эта директория осталась

`livedocs/` — первая попытка структурировать документацию Karaoke,
выросшая органически до 243 файлов. В 2026-09 принято решение о
миграции на новую структуру с явными соглашениями:

- **Bounded Contexts** в `knowledge/domains/<name>/{domain.md, components/}`.
- **ADR** в `knowledge/adr/` (с сохранением ID).
- **Guidelines** в `knowledge/guidelines/` (architecture-conventions,
  code-style, runbooks).
- **Public** в `knowledge/public/` (glossary, onboarding, c4-overview).
- **Templates** в `knowledge/templates/`.
- **System** (C4 L1/L2) в `knowledge/system/`.

Старая коллекция **оставлена как есть** по следующим причинам:

1. **Не ломать CI.** `tools/check-livedocs-*.sh` (4 скрипта) ходят в
   `livedocs/` и обслуживают существующие проверки в `.github/workflows/lint.yml`.
   Переименование директории потребовало бы синхронного обновления
   скриптов — отдельный эпик.
2. **Историческая ценность.** `livedocs/` содержит per-feature документы
   (149 файлов), которые ссылаются из кода, спецификаций и коммитов.
   Архив остаётся источником drill-down.
3. **Не содержит конфликтов с новой SSoT.** Материал в `livedocs/` и
   `knowledge/` не дублируется, а **дополняется** — knowledge/ содержит
   структурированное описание текущего состояния, livedocs/ —
   исторические сводки фич.

## Что НЕ архивируется

- **CI-скрипты** `tools/check-livedocs-*.sh` — продолжают работать,
  проверяют старую коллекцию.
- **Cross-links** на `livedocs/*` — продолжают работать (структура
  сохраняется).

## Что перенесено в `knowledge/` (полная карта)

| Из `livedocs/` | В `knowledge/` | Спека |
| --- | --- | --- |
| `domain/*.md` (9 доменов) | `domains/<name>/{domain.md, components/*.md}` | 326-330 |
| `architecture/decisions/*.md` (13 ADR) | `adr/` (с сохранением ID) | 331 |
| `architecture/` (26 конвенций) | `guidelines/architecture-conventions.md` (индекс) | 332 |
| `runbooks/` (12 playbook'ов) | `guidelines/runbooks/README.md` (индекс) | 332 |
| `CONTRIBUTING.md` (выжимка) | `guidelines/code-style.md` | 332 |
| `onboarding.md` (выжимка) | `public/onboarding.md` | 332 |
| `INDEX.md` (карта) | `public/glossary.md`, `public/c4-overview.md` | 332 |

## Следующие шаги (отдельный эпик, когда будет готов)

1. **Переименование** `livedocs/` → `livedocs-archive/` (отдельный PR
   с обновлением 4 CI-скриптов).
2. **Удаление** `tools/check-livedocs-*.sh` (после переименования —
   скрипты больше не нужны).
3. **Обновление** `.github/workflows/lint.yml` (убрать ссылки на эти
   скрипты).

Эти шаги **отложены** до момента, когда команда будет готова
принять риск (поломка CI на несколько часов, пока скрипты не будут
обновлены синхронно).

## Контактный вопрос

Если ты — AI-агент и читаешь эту страницу:

1. **Сначала проверь** [`knowledge/README.md`](../knowledge/README.md) — это
   текущая SSoT.
2. Если нужной информации нет — поищи в `livedocs/` (это архив).
3. Если нашёл противоречие между `livedocs/` и `knowledge/` —
   **верь `knowledge/`**, не `livedocs/`.
