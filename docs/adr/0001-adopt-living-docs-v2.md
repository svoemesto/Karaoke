---
id: 0001-adopt-living-docs-v2
title: "Принять Living Documentation v2 (docs/) как Single Source of Truth"
status: Accepted
supersedes: null
superseded_by: null
date: 2026-09-08
modules:
  - documentation
tags:
  - livedocs
  - ssoT
  - governance
---

# ADR-0001: Принять Living Documentation v2 (docs/) как Single Source of Truth

## Context & Problem | Контекст и проблема

В Karaoke исторически используется директория `livedocs/` со своей
структурой (`README.md`, `INDEX.md`, `architecture/`, `domain/`,
`features/`, `templates/`, `runbooks/`, `decisions/`). Структура
выросла органически, 243 файла, обслуживается 7 bash-инструментами и
отдельным CI-пайплайном.

Параллельно в проекте появился набор skills
(`bootstrap-living-docs`, `managing-living-docs`, `audit-living-docs`),
которые задают более формализованную структуру SSoT:

- `docs/system/` для L1/L2 (C4);
- `docs/domains/<name>/{domain.md, components/*.md}` для L3 (DDD);
- `docs/adr/`, `docs/epics/`, `docs/guidelines/`, `docs/public/`;
- явные шаблоны и линтер `tools/lint-docs.py`.

Разрозненное состояние (две конкурирующие структуры + skills, которые
не знают про Karaoke) ведёт к:

1. Новые агенты читают skills и пытаются развернуть структуру,
   конфликтующую с существующей.
2. Linter и структурные проверки привязаны к `livedocs/` и не
   покрывают новую структуру.
3. Шаблоны и ADR-формат не стандартизированы между старой и новой
   частями документации.

## Decision | Решение

Принимаем `docs/` как **новую** Single Source of Truth в Karaoke:

1. Структура `docs/` развёрнута в этой спецификации
   (`320-living-docs-v2-bootstrap`) и описана в `docs/README.md`.
2. Содержимое старого `livedocs/` будет **постепенно** перенесено в
   `docs/` следующими спецификациями (по доменам, по ADR, по
   guidelines).
3. До завершения миграции `livedocs/` и `docs/` **сосуществуют**.
   Существующие `tools/check-livedocs-*.sh` продолжают работать.
4. Текущая директория `docs/` (с `architecture-notes.md`, `features/`,
   `api/`, `ops/`, `tracker-setup.md`) перенесена в `docs-old/`,
   чтобы освободить корень `docs/` под новую структуру.
5. Генерируемые артефакты (`docs/api/dokka/`, `docs/api/typedoc-*/`)
   остаются технической проекцией и закрыты через `.gitignore`.
6. Применяются Karaoke-overrides из `docs/README.md` — в частности,
   CLAUDE.md и AGENTS.md **не** приводятся к идентичности, агент
   **не** лишается права коммитить, ADR сохраняют существующую
   нумерацию (`0001-…`, `local-…`).

## Rationale | Обоснование

- **Альтернатива 1**: «Оставить всё как есть, игнорировать skills».
  Отклонено: новые агенты будут спотыкаться о конфликт структур;
  методология skills (impact-surface, double-pass audit, SSoT-marker)
  полезна и заслуживает внедрения.
- **Альтернатива 2**: «Мигрировать одним большим PR».
  Отклонено: 243 файла, расщепление доменов, риск регрессии в
  CI/линтерах. Поэтапная миграция позволяет валидировать каждый
  домен/ADR отдельно и откатываться при проблемах.
- **Альтернатива 3**: «Полностью применить generic skills, включая
  CLAUDE.md == AGENTS.md и запрет агенту на коммиты».
  Отклонено: эти два правила ломают governance и workflow Karaoke.
  Вместо них — явные Karaoke-overrides в `docs/README.md`.

## Consequences | Последствия

1. **Запрет**: не создавать новые документы вне `docs/templates/`
   без явного обоснования.
2. **Инвариант**: Linking Protocol L3 → L2 → L1 обязателен при любых
   мутациях `docs/`.
3. **Required Update**: при любом изменении домена/ADR/эпика —
   синхронное обновление соответствующего раздела.
4. **Архивирование**: после полной миграции `livedocs/` уйдёт в
   `livedocs-archive/` отдельным PR, и старые инструменты
   `tools/check-livedocs-*.sh` будут удалены.
