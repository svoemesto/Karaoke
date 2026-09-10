# Report: Сброс фильтра категории альбома при открытии песен конкретного альбома

**Issue**: #78
**Branch**: `363-album-type-filter-reset-on-album-open`
**Date**: 2026-09-10
**Status**: Code ready for review (Docker-сборка и merge отложены на владельца — см. ниже)

## Summary

Исправлен баг OpenProject #78: при открытии конкретного альбома автора через `/zakroma/{id}?albumId=Y`, если тип альбома (например, `single`) скрыт в фильтре категорий (`hiddenAlbumTypes`), страница показывала пустоту. Теперь тип открытого альбома авто-исключается из фильтра на время просмотра этого альбома (transient computed, без побочных эффектов), а фильтр-бар скрывается через `v-if`, чтобы не провоцировать случайную правку.

## Что изменено

| Файл | Δ | Описание |
|---|---|---|
| `karaoke-public/src/views/ZakromaView.vue` | +30 −3 | Добавлен метод `effectiveHiddenAlbumTypes(zak)` (transient auto-reset). `visibleAlbums(zak)` использует его. Вся панель `.km-album-controls-bar` (переключатель «Сквозной/По типам» + фильтр категорий) скрыта через `v-if="zakromaAlbumTypeCounts.length > 0 && !selectedAlbumId"` (Clarifications Q1 + Q2 уточнение). |

## User stories закрыты

- **US1 (P1)** — открытие альбома со скрытой категорией → авто-сброс, песни видны.
- **US2 (P2)** — нет регрессии в `toggleAlbumType` и persistence в `localStorage`.
- **US3 (P3)** — auto-reset не срабатывает «превентивно» (no-op когда тип не скрыт).

## Проверки (локально)

| # | Проверка | Команда | Результат |
|---|---|---|---|
| T009 | ESLint | `cd karaoke-public && npm run lint:check` | ✅ All matched files use ESLint code style. |
| T009 | Prettier | `npx prettier --check "src/**/*.{vue,js,ts,json}"` | ✅ All matched files use Prettier code style. |
| T010 | JSDoc coverage | `bash tools/check-jsdoc-coverage.sh karaoke-public` | ✅ 94.0% (47/50) ≥ 50%. |

T011 (Docker-сборка `karaoke-public`) — выполнена частично: заблокирована sandbox-окружением DSH (read-only `/home/nsa/.docker/buildx/activity`); код прошёл все статические проверки. Владелец собирает локально:

```bash
cd deploy && bash do.sh build_public
```

T012 (ручной quickstart) — DevTools-сценарии из `quickstart.md`. Владелец прогоняет при ревью:

- Сценарий 1 (главный кейс — скрыть `single`, открыть сингл, проверить `localStorage`).
- Сценарий 2 (контр-кейс — открыть студийный альбом, `hiddenAlbumTypes` не меняется).
- Сценарий 3 (regression — UI фильтра через кнопки).
- Сценарий 4 (edge case — невалидный `?albumId=999999`).
- Сценарий 5 (edge case — все типы скрыты).

## Что НЕ сделано (по согласованию с владельцем)

- ❌ `git commit` / `git push` / `gh pr create` — отложено на владельца (выбрана опция «Только код + локальная проверка»).
- ❌ Tracker `add-comment` + `mark-review` — выполнятся Pass 350 хуком или владельцем после merge.
- ❌ CI 7/7 — не запускался локально; GitHub Actions сделает это после push.

## Комментарий по .ssot-map.yml

Правил для `karaoke-public/**/ZakromaView.vue` нет → no-op. Per-feature документ `docs/features/<slug>.md` не требуется (FR-009 — single-file fix, не новая C4 L3).

## Рекомендации для ревью

1. Проверить `effectiveHiddenAlbumTypes` (line 730+) — реактивность, edge-cases.
2. Прогнать `quickstart.md` → 5 сценариев в DevTools.
3. Подтвердить что `.km-album-type-filters` действительно не виден при `?albumId=`.
4. Проверить что `toggleAlbumType` (строка 727, не тронут) + кнопки фильтра работают как раньше без `?albumId=`.

## Связанные артефакты

- [`spec.md`](./spec.md) — спецификация с Clarifications Q1.
- [`plan.md`](./plan.md) — Implementation Plan (Constitution Check ✓).
- [`research.md`](./research.md) — 5 решений (Decision 1..5).
- [`data-model.md`](./data-model.md) — `effectiveHiddenAlbumTypes` schema.
- [`quickstart.md`](./quickstart.md) — 5 ручных сценариев.
- [`tasks.md`](./tasks.md) — 21 задача, чек-лист.
- [`checklists/requirements.md`](./checklists/requirements.md) — 16/16 ✓.