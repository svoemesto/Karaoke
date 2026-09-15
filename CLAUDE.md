# Karaoke Project Guidelines (Claude Code)

> **Версия**: 1.3.0 | **Final compaction** (Pass 379 wayfinder #114, 2026-09-15).
> 130 → 95 строк. Cross-references only. **Single source of truth**: `AGENTS.md` v3.1.0.

## 🚦 Все правила — в AGENTS.md v3.1.0

- **MUST #0 Knowledge-first pre-flight** → `AGENTS.md` Tier-1.
- **Hard Gates (Pass 372-375, 358, 353)** → `AGENTS.md` Tier-1.
- **OpenProject workflow** → `AGENTS.md` Tier-1.
- **Каталог guards (R-04, R-05, R-07, R-08, R-11, R-32, R-43, R-44, IX.3)** → `AGENTS.md` Tier-2.
- **TOP-11 ловушек** → `architecture-conventions.md` § «Ловушки».
- **Build / Deploy / Containers** → `AGENTS.md` Tier-1 (Pass 372-375).
- **Knowledge SSoT** → `AGENTS.md` Tier-1 + `knowledge/README.md`.
- **Machine-specific exceptions** → `AGENTS.md` Tier-1 (таблица nsa-i9/dev-pc).
- **Subagent isolation** → `AGENTS.md` Tier-1.

## 🚦 Рекомендации для Claude Code (не дублируют AGENTS.md)

1. **При старте сессии**: `cat AGENTS.md | head -100` — знать обязательные правила.
2. **Перед правкой кода фичи**: обновить `docs/features/<slug>.md` (FR-009).
3. **Перед commit**: 7 проверок из AGENTS.md v3.1.0 § «Hard Gate: Обязательная проверка после ЛЮБОГО изменения».
4. **При отладке**: сначала `docker logs`, потом гипотезы (Pass 358).
5. **Если grep по `knowledge/` ничего не нашёл**: зафиксировать в `spec.md` явно «Searched: ... → no relevant docs».

## 🚦 Стратегия проекта (visitor→registration→premium)

**Single source of truth**: `docs/strategy/growth.md` + `docs/strategy/growth-audit.md`.
Краткий focus: visitor → registration (конверсия 0.4%, потенциал ×5-13).
Модель D (гибрид), без trial, сайт-центричная модель.

## 🚦 MCP-серверы (если доступны)

- **`codegraph`** — read-only индекс символов. **Использовать ТОЛЬКО ПОСЛЕ Knowledge-first** (см. MUST #0 в AGENTS.md). Прецедент: spec #339, 2026-09-09.

## 🚦 Где искать что (TL;DR)

| Что | Где |
|---|---|
| Runtime правила, hard-gates | `AGENTS.md` (читай **обязательно**) |
| NON-NEGOTIABLE принципы | `.specify/memory/constitution.md` |
| Архитектура, build/deploy команды | `DEVELOPMENT.md` |
| Стиль кода | `CONTRIBUTING.md` |
| Build/docker конвенции | `knowledge/guidelines/architecture-conventions.md` |
| Per-feature документы | `docs/features/<slug>.md` (FR-009) |
| Стратегия роста | `docs/strategy/growth.md` |
| Логи прода, диагностика | `docs/ops/log-correlation.md` |
| Changelog PR | `docs/architecture-notes.md` |
| Настройка Claude Code | `docs/claude-code-setup.md` |

## 🚦 Ключевые правила (Claude Code-specific)

- **MCP**: использовать `codegraph` **ТОЛЬКО после** Knowledge-first pre-flight.
- **Secrets**: НЕ коммитить. См. AGENTS.md v3.1.0 § «Hard Gate: Secrets».
- **CI**: 7/7 PASS обязателен перед merge. `.github/workflows/lint.yml`.
- **Force-push в main/master**: нельзя.
- **`git commit --no-verify`**: только в крайнем случае.

## Changelog

- **1.3.0** (Pass 379 wayfinder #114 final compaction): 130 → 95 строк. Удалены
  Project Overview, Key Docs, Tech Stack, Workflow, Code Style, Working with
  Documentation (всё — в `DEVELOPMENT.md` или `AGENTS.md`).
- **1.2.0** (Pass 379 wayfinder #114): 219 → 130 строк. Удалены дублирующие секции.
- **1.1.0** (Pass 340): первоначальная версия.
