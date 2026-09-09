# Specs catalog (detailed)

> **Домен**: system (infrastructure)
> **Компонента**: детальный каталог 151 спецификаций в `specs/`.
> Pass 374-375 Knowledge-аудита.

## Назначение

`specs/NNN-<slug>/` — 151 спецификация. Каждая описывает фичу или
фикс через `spec.md` + `plan.md` + `tasks.md` + контракты. Многие
уже **реализованы** (см. merge в master), другие — в работе.

## Каталог (по диапазонам NNN)

### Pass 1-50 (старые, реализованные)

| NNN | Slug | Статус | Прецедент |
|---|---|---|---|
| 001 | code-standards-docs | ✅ | AGENTS.md (стиль) |
| 002 | ci-lint-enforcement | ✅ | pre-commit hooks |
| 003 | about-page | ✅ | Страница «О проекте» (visitor→registration) |
| 004 | reasons-to-register | ✅ | 5 причин зарегистрироваться |
| 005 | free-vs-premium | ✅ | Таблица FREE vs PREMIUM (visitor→premium) |
| 008 | special-orders | ✅ | Спецзаказы |
| 009 | listening-history | ✅ | История прослушиваний |
| 010 | lyrics-spec-tags | ✅ | Спец-теги в lyrics |
| 011 | album-song-rename | ✅ | Переименование |
| 012 | entity-description-fields | ✅ | Поля описания |
| 013 | song-status-filter | ✅ | Фильтр статусов |
| 014 | album-cell-album-cover-modal | ✅ | Модалка обложки |
| 014 | lyrics-search-replacement | ✅ | Поиск lyrics |
| 015 | search-engine-selection | ✅ | Выбор поискового движка |
| 016-020 | fix-* | ✅ | Баг-фиксы |
| 021 | dev-pc-agent-permissions | ✅ | Pass 282 (см. [dev-pc-exception.md](dev-pc-exception.md)) |
| 022 | song-status-lifecycle | ✅ | Lifecycle статусов |
| 023 | songs-audio-root-column | ✅ | Колонка audio root |
| 029 | fix-queue-lane-stall | ✅ | Stall в очереди |
| 030 | add-archive-album-type | ✅ | Archive album type |
| 031 | add-tribute-cover-album-type | ✅ | Tribute/Cover type |
| 082 | fix-import-folder-oom | ✅ | OOM при импорте |
| 083 | album-cover-square-cell | ✅ | Square ячейка |
| 087 | fix-shared-db-connection | ✅ | Shared connection leak |

### Pass 100-300 (средние)

| NNN | Тема |
|---|---|
| ~100-200 | Mostly bug fixes, оптимизации, refactor'ы (точные номера — см. `ls specs/`) |
| 130 | vk-preview-generation (Pass 345) |
| 138 | vk-photo-preview-attachment (Pass 345) |
| 235 | auto-sync-3h (Pass 341 P1 — two-db-sync) |
| 241 | (parent) for sync chunk sizes |
| 246 | (player readiness, см. composable-player-readiness.md) |
| 274 | events-batch-insert (FR-109) |
| 280+ | (много) |

### Pass 300+ (новые)

| NNN | Тема | Связанный Knowledge |
|---|---|---|
| 295 | issue-tracker OpenProject | Pass 340 — task #69 найден через трекер |
| 302 | fix-censored-name-loss (FR-005/006/007/008) | pre-commit-config (Pass 359) |
| 319 | process-bulk-actions-v2 | Pass 341 P1 — async-process-queue |
| 322 | knowledge-scaffold | Исходный каркас Knowledge |
| 323 | (knowledge migration) | |
| 326 | identity migration | spec 326 → [identity domain](../../domains/identity/domain.md) |
| 327 | catalog+rendering migration | spec 327 → [catalog domain](../../domains/catalog/domain.md) + [rendering domain](../../domains/rendering/domain.md) |
| 328 | processing+publishing migration | spec 328 → [processing domain](../../domains/processing/domain.md) + [publishing domain](../../domains/publishing/domain.md) |
| 329 | editorial+monitoring migration | spec 329 → [editorial domain](../../domains/editorial/domain.md) + [monitoring domain](../../domains/monitoring/domain.md) |
| 330 | stats+caching migration | spec 330 → [stats domain](../../domains/stats/domain.md) + [caching domain](../../domains/caching/domain.md) |
| 335 | knowledge-ci-integration | tools/check-knowledge-* (Pass 340) |
| 336 | livedocs-removal | Archive → knowledge (Pass 340+) |
| 338 | ssot-impact-gate | Pass 340+ SSoT gate (см. [ci-tools.md](ci-tools.md)) |
| 339 | health-domain-P0 | spec 339 → [health domain](../../domains/health/domain.md) |
| 340 | governance-knowledge-first | Pass 340 (см. PR #340) |
| 341 | knowledge P0-P2 | Pass 341 |
| 343 | knowledge detail pass | Pass 343 |
| 344-365 | knowledge P3, scheduler, API, frontend, MLT | Pass 344-365 |
| 366-370 | knowledge MLT internals | Pass 366-370 |

## Архитектурные решения

### Решение 1: 3 файла на спецификацию

`spec.md` + `plan.md` + `tasks.md` + (опционально) `contracts/*.md`.
Spec-Kit (Pass 340) стандартизирует формат.

### Решение 2: NNN — резервируется через `tools/reserve-branch-number.sh`

Каждая спецификация = feature-ветка `NNN-<slug>`. Несколько specs
могут жить в одной ветке (Pass 343+ — несколько коммитов).

### Решение 3: Knowledge-first (Pass 340)

Spec **обязана** иметь секцию `## Knowledge References` (см.
spec-template.md). Pass 340+ enforced.

## Связь с Knowledge

- **Каждая спецификация** должна иметь ссылки на
  `knowledge/domains/*/components/*.md` файлы, которые описывают
  соответствующую область.
- **Саммари**: [specs-catalog.md](specs-catalog.md) — компактный
  overview (Pass 358).

## Известные TODO

- [ ] **Каждая из 151 specs** — упоминание в Knowledge (Pass 343+)?
      Скорее всего НЕ нужно — только для активных/важных.
- [ ] **Связь specs ↔ OpenProject** — какой маппинг?

## Changelog

- **Pass 374-375** (2026-09-09): Initial detailed. Автор: agent (Karaoke).