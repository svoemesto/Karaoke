---
id: 0007-adopt-knowledge-as-ssot
title: "Принять knowledge/ как Single Source of Truth (Living Documentation v2)"
status: Accepted
supersedes: null
superseded_by: null
date: 2026-09-08
modules:
  - documentation
tags:
  - livedocs
  - ssot
  - governance
---

# ADR-0001: Принять knowledge/ как Single Source of Truth (Living Documentation v2)

## Context & Problem | Контекст и проблема

В Karaoke исторически используется директория `livedocs/` со своей
структурой (`README.md`, `INDEX.md`, `architecture/`, `domain/`,
`features/`, `templates/`, `runbooks/`, `decisions/`). Структура
выросла органически, 243 файла, обслуживается 7 bash-инструментами и
отдельным CI-пайплайном.

В корне проекта также существует директория `docs/` (содержит
`api/`, `features/`, `ops/`, `tracker-setup.md`,
`architecture-notes.md`) — это **отдельная** техническая проекция
(сгенерированная Dokka/typedoc-документация + per-feature материалы),
которая **не** пересекается структурно с `livedocs/`.

Параллельно в проекте появился набор skills
(`bootstrap-living-docs`, `managing-living-docs`, `audit-living-docs`),
которые задают более формализованную структуру SSoT:

- `docs/system/` для L1/L2 (C4);
- `docs/domains/<name>/{domain.md, components/*.md}` для L3 (DDD);
- `docs/adr/`, `docs/epics/`, `docs/guidelines/`, `docs/public/`;
- явные шаблоны и линтер.

Разрозненное состояние (две конкурирующие структуры `livedocs/` и `docs/`
+ skills, которые хотят ещё один `docs/`) ведёт к:

1. Новые агенты читают skills и пытаются развернуть структуру,
   конфликтующую с существующими `livedocs/` и `docs/`.
2. Linter и структурные проверки привязаны к `livedocs/` и не
   покрывают новую структуру.
3. Шаблоны и ADR-формат не стандартизированы между старой и новой
   частями документации.
4. Попытка мигрировать на `docs/` (по generic-bootstrap) ломает 22+
   cross-links из `livedocs/features/*.md` (CI падает).

## Decision | Решение

Принимаем `knowledge/` как **новую** Single Source of Truth в Karaoke:

1. Структура `knowledge/` развёрнута в этой спецификации
   (`322-knowledge-scaffold`) и описана в `knowledge/README.md`.
2. **Не переименовываем** существующий `docs/` в `docs-old/` и **не**
   переносим его содержимое — он остаётся на своём месте как
   техническая проекция. Это устраняет 22+ broken-links, которые
   возникают при `git mv docs docs-old`.
3. Содержимое старого `livedocs/` будет **постепенно** перенесено в
   `knowledge/` следующими спецификациями (по доменам, по ADR, по
   guidelines).
4. До завершения миграции `livedocs/`, `docs/` и `knowledge/`
   **сосуществуют**. Существующие `tools/check-livedocs-*.sh`
   продолжают работать.
5. Применяются Karaoke-overrides из `knowledge/README.md` — в частности,
   CLAUDE.md и AGENTS.md **не** приводятся к идентичности, агент
   **не** лишается права коммитить, ADR сохраняют существующую
   нумерацию (`0001-…`, `local-…`).

## Rationale | Обоснование

- **Альтернатива 1**: «Оставить всё как есть, игнорировать skills».
  Отклонено: новые агенты будут спотыкаться о конфликт структур;
  методология skills (impact-surface, double-pass audit, SSoT-marker)
  полезна и заслуживает внедрения.
- **Альтернатива 2**: «Применить `docs/` строго по generic-bootstrap,
  переименовать существующий `docs/` в `docs-old/`, починить 22+
  cross-links в livedocs».
  Отклонено: реально проверено в spec 320 (откат). 257 ссылок в 76
  файлах `livedocs/` нужно править, плюс каждый файл `livedocs/` —
  архивный материал, который не должен меняться при миграции. Цена
  ошибки высокая, выгода нулевая (имя `docs/` ничем не лучше `knowledge/`).
- **Альтернатива 3**: «Полностью применить generic skills, включая
  CLAUDE.md == AGENTS.md и запрет агенту на коммиты».
  Отклонено: эти два правила ломают governance и workflow Karaoke.
  Вместо них — явные Karaoke-overrides в `knowledge/README.md`.

## Consequences | Последствия

1. **Запрет**: не создавать новые документы вне `knowledge/templates/`
   без явного обоснования.
2. **Инвариант**: Linking Protocol L3 → L2 → L1 обязателен при любых
   мутациях `knowledge/`.
3. **Required Update**: при любом изменении домена/ADR/эпика —
   синхронное обновление соответствующего раздела.
4. **Архивирование**: после полной миграции `livedocs/` уйдёт в
   `livedocs-archive/` отдельным PR, и старые инструменты
   `tools/check-livedocs-*.sh` будут удалены.
5. **Старый `docs/` остаётся нетронутым**: его судьба — отдельная
   спецификация (например, `archive-legacy-docs`), если потребуется.
