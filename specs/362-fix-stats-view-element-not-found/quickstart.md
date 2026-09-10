# Quickstart: Валидация фикса «Element not found» в StatsView

**Spec**: [`spec.md`](./spec.md)
**Plan**: [`plan.md`](./plan.md)
**Date**: 2026-09-10
**Branch**: `362-fix-stats-view-element-not-found`

## Цель

Подтвердить, что Issue #79 исправлен: при открытии компонента «Статистика»
в `webvue3` НЕТ ошибок `Element not found` в браузерной консоли.

## Prerequisites

1. **Dev-окружение**:
   - Машина `nsa-i9` (см. AGENTS.md, раздел «Машинно-специфичные исключения»).
   - Локальные контейнеры `karaoke-web` и `webvue3` запущены через
     `deploy/do.sh build_webvue3`.
   - Браузер Chrome 120+ (DevTools).

2. **Аккаунт**:
   - Администратор (`webvue3` имеет `permitAll()`, но сессия нужна
     для проверки `siteUserId > 0` событий).

3. **Данные**:
   - `tbl_events` содержит ≥100 событий за последние 30 дней
     (для проверки графиков).

## Setup

```bash
# 1. Проверить ветку
git branch --show-current  # должно быть 362-fix-stats-view-element-not-found

# 2. Проверить, что webvue3 собран
cd webvue3 && npm run build && cd ..

# 3. Перезапустить webvue3 (если были изменения)
cd deploy && bash do.sh build_webvue3

# 4. Открыть webvue3 в браузере
open http://localhost:7905/  # или другой URL из deploy/do.sh
```

## Сценарии валидации

### Сценарий SC-001: Главный критерий — 0 ошибок в консоли при открытии «Статистики»

**Что проверяем**: SC-001 — `0 ошибок Element not found` после `mounted()`.

**Шаги**:
1. Открыть DevTools → Console (включить фильтр "Errors").
2. Открыть DevTools → Network (включить фильтр `/api/stats/`).
3. Очистить Console (кнопка 🚫 Clear console).
4. Перейти в меню «Статистика».
5. Подождать 10 секунд (все графики должны отрисоваться).

**Ожидаемый результат**:
- Console: **0 ошибок** `Element not found`, 0 unhandled promise rejection.
- Network: **1-2 запроса** к `/api/stats/summary` и `/api/stats/monetization`
  (для активной вкладки KPI + Монетизация).

**До фикса (regression)**: 11 параллельных HTTP-запросов, ≥1 ошибка
`Element not found`.

### Сценарий SC-002: ≤ 2 HTTP-запросов при `mounted()`

**Что проверяем**: SC-002 — при первом `mounted()` отправляется не более
2 HTTP-запросов к `/api/stats/*`.

**Шаги**:
1. Hard reload (Cmd+Shift+R / Ctrl+Shift+R) страницы webvue3.
2. Перейти в «Статистику».
3. Подождать 5 секунд.
4. В DevTools → Network → filter `/api/stats/`.

**Ожидаемый результат**: ровно **2 запроса** (или 1, если monetization
ещё не подключён):
- `GET /api/stats/summary?target=local`
- `GET /api/stats/monetization?target=local`

**До фикса**: 11-12 параллельных запросов (FATAL: too many clients already
на пике).

### Сценарий SC-003: Переключение табов = lazy load ровно нужных endpoint'ов

**Что проверяем**: SC-003 — при переключении на вкладку загружаются
только endpoint'ы этой вкладки (не все 11).

**Шаги**:
1. Открыть «Статистику» (активна KPI).
2. Кликнуть по табу «Динамика».
3. В Network наблюдать запросы.

**Ожидаемый результат**: **1 запрос** `GET /api/stats/timeseries?target=...&days=30&mode=all`.

**Повторить для каждой вкладки**:

| Вкладка | Ожидаемое кол-во HTTP |
|---|---|
| KPI (уже загружена) | 0 (TTL) |
| Монетизация | 1 (`/api/stats/monetization`) |
| Динамика | 1 (`/api/stats/timeseries`) |
| Разбивки | 3 (`/api/stats/by-type`, `/api/stats/channels`, `/api/stats/by-detail`) |
| География | 2 (`/api/stats/countries`, `/api/stats/referrers`) |
| Пользователи | 1 (`/api/stats/top-users`) |
| Слушают | 1 (`/api/stats/top-listened`) |
| События | 2 (`/api/stats/by-song`, `/api/webevents`) |

### Сценарий SC-004: TTL работает при возврате на страницу в течение 60 сек

**Что проверяем**: SC-004 — 0 новых HTTP при возврате на страницу
в течение 60 сек (US3 acceptance 2).

**Шаги**:
1. Открыть «Статистику».
2. Подождать загрузки KPI (1-2 запроса).
3. Перейти в другую страницу админки (например, «Песни»).
4. Подождать 30 секунд.
5. Вернуться в «Статистику».

**Ожидаемый результат**:
- В Network: **0 запросов** к `/api/stats/*` (TTL жив, 30s < 60s).
- В Console: **0 ошибок**.
- Графики показываются сразу (из кеша store).

### Сценарий SC-005: Кнопка «Обновить всё» заменена на «Обновить»

**Что проверяем**: SC-005 — кнопка удалена / переименована.

**Шаги**:
1. Открыть «Статистику».
2. Найти кнопку в toolbar.

**Ожидаемый результат**:
- Кнопка называется «Обновить» (без «всё»).
- Клик по кнопке вызывает `loadDataForActiveTab(currentTab)` —
  **только** endpoint'ы активной вкладки (1-3 запроса, не 11).

**До фикса**: кнопка «Обновить всё» → клик → 11 параллельных HTTP →
race → ошибка.

### Сценарий SC-006: Backward-compat — переключение БД работает без 11 запросов

**Что проверяем**: SC-006 — переключение `local`/`remote` обновляет
только активную вкладку + endpoint'ы с фильтром days.

**Шаги**:
1. Открыть «Статистику» (active=KPI).
2. В Network: счётчик = 2 (summary + monetization).
3. Переключить БД на «Сервер».
4. Подождать 5 секунд.

**Ожидаемый результат**:
- В Network: **+2 запроса** (с `target=remote`): summary + monetization.
- **НЕ** 11 запросов.
- В Console: 0 ошибок.

## Ожидаемое время валидации

- **Smoke test (SC-001 + SC-002)**: 5 минут (достаточно для подтверждения,
  что Issue #79 исправлен).
- **Full validation (SC-001..SC-006)**: 20-30 минут.
- **Regression check (сравнение с Pass 174)**: 10 минут (убедиться,
  что существующая функциональность статистики не сломана).

## Репорт о результате

После прохождения сценариев, написать отчёт в
`specs/362-fix-stats-view-element-not-found/report.md` (создаётся
на этапе `/speckit.implement`):

```markdown
# Report: Validation of spec 362 fix

## Сценарии

- [x] SC-001: 0 ошибок в console ✅
- [x] SC-002: 2 HTTP на mounted ✅
- [x] SC-003: lazy load ровно нужных endpoint'ов ✅
- [x] SC-004: TTL работает (0 запросов при возврате) ✅
- [x] SC-005: кнопка «Обновить» (не «всё») ✅
- [x] SC-006: backward-compat переключения БД ✅

## Скриншоты / DevTools snippets

- Console: `screenshot-console-clean.png`
- Network при mounted: `screenshot-network-mounted.png`
- Network при переключении табов: `screenshot-network-tabs.png`

## Заключение

Issue #79 исправлен. Spec 362 готова к merge.
```

## Что делать, если сценарий не проходит

### Console показывает `Element not found`

1. Проверить, что webvue3 действительно перезапущен с новой сборкой:
   `docker logs webvue3 --tail 50` — должно быть `Image is up to date`
   или `Downloaded newer image`.
2. Проверить, что в браузере кеш очищен (hard reload).
3. Если ошибка остаётся — посмотреть в DevTools → Sources →
   найти StatsView.vue, проверить, что `reloadAll()` действительно
   удалён.
4. Если ошибка воспроизводится — открыть тикет с описанием
   (шаги воспроизведения + screenshot).

### Network показывает > 2 запросов при mounted

1. Проверить, что `mounted()` вызывает `loadDataForActiveTab(0)`,
   а не `reloadAll()`.
2. Проверить, что `loadDataForActiveTab` корректно маппит
   `activeTab=0 → ['summary']` (а не все 11).
3. Проверить, что `STATS_FRONT_TTL_MS = 60_000` и
   `lastLoadedAt` инициализирован как `{}`.

## Связанные документы

- [`spec.md`](./spec.md) — функциональные требования (FR-001..FR-014)
- [`plan.md`](./plan.md) — Implementation Plan + Constitution Check
- [`data-model.md`](./data-model.md) — entities и state transitions
- [`research.md`](./research.md) — Phase 0 research (3 вопроса)
- `archive/docs/features/stats.md` — per-feature документ
  (будет обновлён в этом PR).
- `specs/174-fix-stats-connection-leak/spec.md` — предыдущая попытка
  фикса (обещала lazy load, но не реализовала).
