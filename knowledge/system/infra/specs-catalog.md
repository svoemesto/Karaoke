# Specs catalog (specs/)

> **Домен**: system (infrastructure)
> **Компонента**: каталог 151 спецификаций в `specs/`.

## Назначение

`specs/NNN-<slug>/` — **151 спецификаций** проекта, каждая
описывает одну фичу или фикс. Формат — `spec.md` + `plan.md` +
`tasks.md` + контракты.

## Типы specs

| Тип | Пример | Что описывает |
|---|---|---|
| **Feature** | `005-free-vs-premium`, `003-about-page` | Новая функциональность. |
| **Fix** | `029-fix-queue-lane-stall`, `082-fix-import-folder-oom` | Исправление бага. |
| **Refactor** | `001-code-standards-docs` | Перестройка без изменения поведения. |
| **Infrastructure** | `002-ci-lint-enforcement` | DevOps/CI улучшения. |
| **Integration** | `295-...` (OpenProject tasks) | Tasks from OpenProject tracker. |

## Скоуп покрытия

**С Pass 341-358** Knowledge покрывает:

- `Health` (Pass 341 P0) — критически для #65, #69.
- `Storage` (Pass 341 P0) — критически для #65, #69.
- `WebCaches` (Pass 341 P0) — для кеша в #69.
- `Async Process Queue` (Pass 341 P1).
- `Two-DB sync` (Pass 341 P1).
- `Monetization` (Pass 341 P1).
- `SSE` (Pass 341 P2).
- `KaraokeDbTable` (Pass 341 P2).
- `Schedulers` (Pass 344).
- `External API clients` (Pass 345).
- `Alignment-ml` (Pass 346).
- `Process Admin` (Pass 347).
- `Remaining models` (Pass 348).
- `Vuex patterns` (Pass 349).
- `karaoke-public composables` (Pass 350).
- `webvue3 views` (Pass 351).
- `DTOs` (Pass 352).
- `CI tools` (Pass 353).
- `deploy` (Pass 354).
- `do.sh` (Pass 355).
- **C4 L1 + L2** (Pass 356).

## Известные TODO

- [ ] **Каждая из 151 specs** — упоминание в Knowledge? (Pass 343+)
- [ ] **Связь specs ↔ OpenProject tasks** — есть ли маппинг?
- [ ] **Archived specs** — куда переезжают после реализации.

## Связь с другими компонентами

- **OpenProject** — задачи импортируются в specs.
- **AGENTS.md** — упоминает speckit workflow.

## Changelog

- **Pass 358** (2026-09-09): Initial. Автор: agent (Karaoke).