# Implementation Plan: Строка «Временная ссылка» в таблице FREE vs PREMIUM

**Branch**: `169-share-link-in-premium-compare` | **Date**: 2026-08-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/169-share-link-in-premium-compare/spec.md`

## Summary

Добавить **12-ю строку** «Временная ссылка на песню» (FREE ❌, PREMIUM ✅)
в существующую таблицу сравнения «FREE vs PREMIUM» на `/premium`
(см. [`specs/005-free-vs-premium/spec.md`](../005-free-vs-premium/spec.md),
`PremiumView.vue:204-220`). Параллельно расширить фразу «Что вы получили» в
премиум-блоке добавлением упоминания временной ссылки с TTL/устройствами.

Технический подход (из `research.md`):
- **Только фронтенд** — никаких изменений в backend/БД/дизайне/трекинге.
- Правка одного файла: `karaoke-public/src/views/PremiumView.vue` —
  добавление 1 записи в массив `COMPARISON_ROWS` + дополнение одной фразы.
- Точные числа (1 ч / 24 ч / 7 д, до 2 устройств) hardcoded — паттерн
  из `005-free-vs-premium/research.md` Decision 2 («числа в исходниках, не API»).

## Technical Context

**Language/Version**: JavaScript (ES2020+), Vue 3.4 SFC, `<script>` (Options API).
Файлы правки: `karaoke-public/src/views/PremiumView.vue` (только `.vue`, без TS).

**Primary Dependencies**:
- `vue@3.4` (composition/api options — в этом файле Options API)
- `vue-router@4` (`<RouterLink>`)
- Внутренние: `useAuth` (composable), `useSiteSubscription` (composable),
  `authGet` (service), `trackUi` (для трекинга клика по CTA, уже подключён)
- Стили: Bootstrap 5 (CDN, общие стили) + `--km-*` CSS-переменные
  (karaoke-public dual classic/modern)

**Storage**: **N/A** — никаких изменений в БД.

**Testing**: **ручное** + визуальное (как и в `005-free-vs-premium/quickstart.md`).
Тестов в CI нет (`AGENTS.md` «Тесты: в CI нет»). Линтер:
`cd karaoke-public && npm run lint:check` (часть CI, см.
[`docs/features/ci-lint-enforcement.md`](../../docs/features/ci-lint-enforcement.md)).

**Target Platform**: Web — публичный SPA `karaoke-public`, открывается на
desktop (1280+) и mobile (360-500). Адаптивность таблицы уже реализована
в `PremiumView.vue:437-470`.

**Project Type**: web-service (frontend SPA). Vue 3 + Vite.

**Performance Goals**: **N/A** — фичи добавляют 1 элемент в inline-массив
(без HTTP-запросов, по `NFR-001` исходной таблицы).

**Constraints**:
- Существующая адаптивность на 360-500px не должна пострадать (FR-007 spec).
- Premium-выделение колонки (иконка 👑, фон, WCAG AA-индикаторы) сохраняется.
- Соблюдать конвенцию `--km-*` CSS-переменных.
- Без новых HTTP-запросов от самой таблицы (NFR-001).

**Scale/Scope**: 1 файл, 2-3 правки, **минимальный PR**:
- массив `COMPARISON_ROWS`: +1 строка (12-я по счёту);
- премиум-блок «Что вы получили» `PremiumView.vue:59-61`: +1 предложение.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Проверяем соответствие каждому принципу `.specify/memory/constitution.md` (v2.1.0):

| # | Принцип | Применимо? | Решение |
|---|---|---|---|
| I | Self-contained автопайплайн (NON-NEGOTIABLE) | n/a | Фича не про пайплайн обработки медиа, а про UI-таблицу. ✅ PASS |
| II | Сырой JDBC + дифф по хэшам (NON-NEGOTIABLE) | n/a | Никаких изменений в БД, фича фронтенд-only. ✅ PASS |
| III | Двух-БД синхронизация через SyncRegistry | n/a | Нет новых сущностей. ✅ PASS |
| IV | Async-очередь задач с парсингом stdout | n/a | Не задействована. ✅ PASS |
| V | Двух-фронтенд (админка vs публичный сайт) | **да** | Правка только в `karaoke-public` (не `webvue3`). Запрещённого смешивания нет. ✅ PASS |
| VI | Code Standards (FR-006 KDoc, FR-007 линтеры, FR-009 per-feature) | n/a | Массив `COMPARISON_ROWS` — это JS-данные, не API-endpoint, JSDoc не требуется (FR-006 требует для `export default`). `npm run lint:check` обязателен (FR-007) — будет в `tasks.md`. Per-feature документ `docs/features/guest-share-link.md` уже есть и не меняется (фича 169 его не правит, только ссылается). ✅ PASS |
| VII | Cross-Machine Setup (VII.1 локальные AI-конфиги, VII.2 git-blame-ignore-revs, VII.3 .gitattributes, VII.4 cross-machine docs) | n/a | Локальных конфигов не правим, line endings новых строк не появляются (только UTF-8 кириллица в строках — `.gitattributes` уже настроен глобально). ✅ PASS |
| VIII | Секреты и git-гигиена (VIII.1–VIII.5) | n/a | Нет секрет-файлов, нет изменений в `deploy/`, `do.env`, `.env`. Pre-commit `git ls-files | grep -iE '\.env$\|do\.env$'` остаётся пусто. ✅ PASS |

**Gates summary**:

- **До Phase 0 (Tech Context заполнен)**: ✅ все 8 принципов PASS — нет
  нарушений. Phase 0 можно запускать.
- **После Phase 1 design (Re-check)**: ✅ все 8 принципов PASS — design
  (правка `PremiumView.vue`, без новых компонентов / API / БД)
  полностью соответствует конституции.

**Нет нарушений**, требующих обоснования в Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/169-share-link-in-premium-compare/
├── plan.md              # Этот файл (вывод /speckit.plan)
├── research.md          # Phase 0 output — построчная верификация share-link
├── spec.md              # Feature Spec
├── data-model.md        # Phase 1 output — UI-данные для сравнения
├── quickstart.md        # Phase 1 output — ручная проверка в браузере
├── contracts/           # Phase 1 output — UI-контракт массива COMPARISON_ROWS
│   └── comparison-row.md
├── checklists/
│   └── requirements.md  # Quality checklist (создан /speckit.specify)
└── tasks.md             # Phase 2 output (создаётся /speckit.tasks, не /speckit.plan)
```

### Source Code (repository root)

```text
karaoke-public/
└── src/
    └── views/
        └── PremiumView.vue       # ← единственный файл правки (2-3 строки)
```

**Structure Decision**: **Option 2 (Web application) — фронтенд-часть**.
Никаких новых модулей, компонентов, страниц, store-модулей. Правка в одном
существующем файле `PremiumView.vue`. Ничего больше не затрагивается.

### Изменения по файлам

| Файл | Тип | Строк | Что меняется |
|---|---|---|---|
| `karaoke-public/src/views/PremiumView.vue` | edit | +2 / −0 | +1 строка в массиве `COMPARISON_ROWS` (после строки 219), +1 предложение в `<p class="km-card-body">` блока «Что вы получили» (после строки 61). |
| `specs/169-share-link-in-premium-compare/` | create | — | Спека + research + plan + data-model + contracts + quickstart + checklist. |

Никакие другие файлы проекта (gradle/Vuex/миграции/конфиги) не трогаются.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Пусто — нет нарушений. Все gates PASS.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |

## Phase 1 deliverables (краткий обзор)

Подробности — в отдельных файлах:

- **`data-model.md`** — описание объекта `COMPARISON_ROW` (JS-структура,
  не сущность БД): `{ feature: string, free: boolean|string, premium: boolean|string }`.
  Тип `boolean` → `✅/❌`, `string` → текст в ячейке.
- **`contracts/comparison-row.md`** — UI-контракт массива `COMPARISON_ROWS`:
  формат элемента, валидация, инварианты порядка строк (1 файл, ~80 строк).
  Никаких сетевых/API-контрактов (новых не появляется).
- **`quickstart.md`** — ручная проверка в браузере: 3 сценария (аноним,
  free, премиум), 8 чек-поинтов визуальной проверки таблицы на desktop
  и mobile, без автоматизированных тестов (соответствует AGENTS.md
  «Тесты: в CI нет»).

## Связь с существующими фичами

- **005-free-vs-premium** (QW-1) — расширяется: 11 строк → 12 строк.
  Никаких изменений в `SPEC.md 005` (там 11 строк — это **состояние на
  момент QW-1**); обновляется только `PremiumView.vue`. При желании
  можно добавить отдельную запись в `docs/architecture-notes.md` при
  мердже PR (#TBD).
- **guest-share-link** (163/164/166/167) — **не правится**, только
  ссылается. Техническое состояние share-link покрыто
  [`docs/features/guest-share-link.md`](../../docs/features/guest-share-link.md)
  и `SongShareLinkService.kt`. На момент 169 эти компоненты работают в
  проде и не требуют изменений.

## Notes

- Frontend-only правка, без backend coordination — задача для одного
  разработчика (single-PR).
- Линтер `npm run lint:check` в `karaoke-public` обязателен к CI-проходу
  (см. [`docs/features/ci-lint-enforcement.md`](../../docs/features/ci-lint-enforcement.md)).
  Перевод `Boolean` (маленькая b) в `boolean` — ktlint-стиль для Kotlin;
  для `.vue` ESLint-проверки ничего не меняется.
- Принцип «без новых секретов и без новых .env» соблюдён — никаких
  затрагиваемых файлов из Principle VIII.2.
