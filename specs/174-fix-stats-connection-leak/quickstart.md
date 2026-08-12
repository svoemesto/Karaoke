# Quickstart: Починить flood JDBC-соединений при открытии вкладки «Статистика»

**Branch**: `174-fix-stats-connection-leak` | **Date**: 2026-08-12
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md) | **Data Model**: [data-model.md](./data-model.md)

## Prerequisites

- Рабочая dev-машина с `karaoke-app`, `karaoke-web`, `karaoke-db` контейнерами
  (стандартный `deploy/do.sh start`).
- Frontend `webvue3` запущен локально (`cd webvue3 && npm run dev`) или
  собран (`npm run build`) и задеплоен.
- Тестовый администратор (admin-зона, `permitAll()`, отдельная авторизация
  не требуется).
- Инструменты: `docker`, `curl`, `psql` (или `pg_stat_activity` через
  UI-инструмент).

## Setup

1. **Пересобрать и перезапустить backend** (на dev-машине):
   ```bash
   cd deploy
   bash do.sh build_app           # gradle build karaoke-app
   bash do.sh start_app           # перезапустить контейнер (только на dev-pc под dev)
   ```
   Подробнее см. [AGENTS.md](../../AGENTS.md) секция «Разрешено агенту» — на
   `dev-pc` под пользователем `dev` это можно без отдельного согласия.

2. **Пересобрать frontend**:
   ```bash
   cd webvue3
   npm install                     # если менялся package.json
   npm run build                   # production build → dist/
   # или npm run dev для hot-reload во время разработки
   ```

3. **Проверить, что контейнеры работают**:
   ```bash
   docker ps | grep -E 'karaoke-app|karaoke-db|webvue3'
   docker logs karaoke-app --tail 20
   ```

## Сценарий 1: Базовый smoke test (открытие дашборда)

**Цель**: проверить SC-001 (≤3 HTTP-запросов при `mounted()`) и SC-002
(0 exceptions при 10 F5).

1. Открыть `http://localhost:8080/admin/stats` (или эквивалентный URL
   `webvue3` dev-сервера).

2. Открыть DevTools → Network panel. Фильтр: `/api/stats/`.

3. **Expected**: при первой загрузке видны **≤3 запроса** (только для
   дефолтной активной вкладки «KPI» — `/api/stats/summary` + `/api/stats/monetization`).
   НЕ должно быть 10–12 запросов, как раньше.

4. Переключиться на таб «Динамика». **Expected**: 1 новый запрос —
   `GET /api/stats/timeseries?mode=...`.

5. Переключиться на таб «География». **Expected**: 2 запроса —
   `GET /api/stats/countries` + `GET /api/stats/referrers`.

6. Вернуться на таб «Динамика» в течение 60 секунд. **Expected**:
   **0 новых запросов** (cache hit).

7. F5 страницы 10 раз подряд за 30 секунд. После каждого F5 проверить
   лог backend:
   ```bash
   docker logs karaoke-app --since 30s | grep -c "too many clients"
   ```
   **Expected**: `0`.

## Сценарий 2: Cache invalidation через TTL

**Цель**: проверить, что TTL работает (cache hit <60s, cache miss >60s).

1. Открыть `http://localhost:8080/admin/stats`, дождаться загрузки
   вкладки «KPI».

2. Записать время загрузки: `T0`.

3. Подождать 70 секунд (больше TTL=60).

4. Переключиться на другой таб и обратно на «KPI». **Expected**: 1 новый
   запрос `GET /api/stats/summary` (cache expired, refetch).

5. Проверить логи `karaoke-app`:
   ```bash
   docker logs karaoke-app --tail 100 | grep "stats.cache"
   ```
   **Expected** (с включённым DEBUG уровнем):
   ```
   stats.cache endpoint=summary hit=false age=70
   stats.cache endpoint=summary hit=true age=12
   ```

## Сценарий 3: 503 при перегрузке БД

**Цель**: проверить SC-005 (100% вкладок показывают `<DbOverloadBanner>`).

1. Подключиться к Postgres и занять 99 соединений (имитация
   `max_connections = 100`):
   ```bash
   docker exec -it karaoke-db psql -U postgres -d karaoke -c \
     "SELECT pg_sleep(3600) FROM generate_series(1,99);" &
   ```
   Или (более чисто) снизить `max_connections`:
   ```bash
   docker exec -it karaoke-db psql -U postgres -d karaoke -c \
     "ALTER SYSTEM SET max_connections = 5; SELECT pg_reload_conf();"
   # ПЕРЕЗАПУСТИТЬ postgres:
   docker restart karaoke-db
   ```

2. Открыть `http://localhost:8080/admin/stats`.

3. **Expected**:
   - На каждой вкладке видно `<DbOverloadBanner>` «БД перегружена,
     retry через 10 секунд» с disabled-кнопкой «Retry now» и обратным
     отсчётом.
   - Через 10 секунд — автоматический retry (одна попытка).
   - Если БД всё ещё недоступна — баннер остаётся.

4. Проверить response в DevTools → Network:
   ```bash
   curl -i http://localhost:8080/api/stats/summary
   ```
   **Expected**:
   ```
   HTTP/1.1 503 Service Unavailable
   Retry-After: 10
   Content-Type: application/json

   {"errorCode":"stats.unavailable","retryAfterSeconds":10,"endpoint":"/api/stats/summary"}
   ```

5. Восстановить `max_connections`:
   ```bash
   docker exec -it karaoke-db psql -U postgres -d karaoke -c \
     "ALTER SYSTEM RESET max_connections; SELECT pg_reload_conf();"
   docker restart karaoke-db
   ```

## Сценарий 4: Debug endpoint

**Цель**: проверить FR-010 (`/api/stats/debug`).

1. Открыть `http://localhost:8080/admin/stats` (загрузить все вкладки).

2. Вызвать debug endpoint:
   ```bash
   curl -X POST http://localhost:8080/api/stats/debug | jq
   ```

3. **Expected**:
   ```json
   {
     "cacheSize": 6,
     "cacheKeys": [
       {"endpoint": "summary", "params": {}, "ageSeconds": 5, "expired": false},
       ...
     ],
     "pgActiveConnections": 12,
     "pgMaxConnections": 100,
     "timestamp": "2026-08-12T10:23:45.123Z"
   }
   ```

4. **Verify**:
   - `cacheSize === 6` (все 6 кешируемых endpoint'ов загружены).
   - `pgActiveConnections < pgMaxConnections` (нет перегрузки).

## Сценарий 5: Регрессия (lazy load табов не сломал существующее)

**Цель**: проверить, что пользовательский сценарий «открыть → переключить
таб → обновить → закрыть» работает без regression.

1. Открыть `http://localhost:8080/admin/stats`.

2. Переключиться между всеми 7 табами (KPI → Монетизация → Динамика →
   Разбивки → География → Пользователи → Слушают → KPI).

3. На каждом табе нажать кнопку «Обновить» (если есть) или F5.

4. Закрыть вкладку (X в браузере) и открыть заново.

5. **Expected**:
   - Каждый таб показывает свои данные.
   - Нет «пустых графиков» (если БД доступна).
   - Нет 500 ошибок в DevTools.
   - Персистентность пагинации работает (вернуться на ту же страницу
     после переключения меню) — см. [AGENTS.md](../../AGENTS.md) секция
     «Персистентность страницы пагинации в `webvue3`».

## Сценарий 6: Метрики `pg_stat_activity` под нагрузкой

**Цель**: проверить SC-003 (≤70 одновременных соединений при пике).

1. Симулировать нагрузку публичного сайта:
   ```bash
   # В одном терминале
   wrk -t4 -c100 -d60s http://localhost:8080/api/public/songs  # 100 RPS
   ```

2. В другом терминале — открыть 5 экземпляров `webvue3` в разных
   браузерах/инкогнито, нажать «Статистика» в каждом.

3. Проверить `pg_stat_activity`:
   ```bash
   docker exec -it karaoke-db psql -U postgres -d karaoke -c \
     "SELECT count(*), application_name FROM pg_stat_activity GROUP BY application_name"
   ```

4. **Expected**: общее количество соединений **≤70** (5 admin + ~50 public
   + ~10 sync). Сравнить с baseline ДО фикса (там было 100+ в пиках).

## Что делать если что-то не работает

| Симптом | Где смотреть |
|---|---|
| 10+ запросов при `mounted()` (SC-001 fail) | `webvue3/src/views/StatsView.vue` — `reloadAll()` должен вызывать только lazy load для активной вкладки |
| `too many clients` в логах (SC-002 fail) | `karaoke-app/.../controllers/StatsController.kt` — `withDb` не сохранился, или HikariCP нужен (открыть задачу XXX-fix-stats-connection-pool) |
| 70+ connections (SC-003 fail) | Cache не работает — `StatsCache.put()` не вызывается в контроллере. Проверить DEBUG логи |
| p95 > 500ms (SC-004 fail) | SQL-запрос тяжёлый. EXPLAIN ANALYZE на `tbl_events`. Возможно нужен индекс |
| Баннер не появляется при сбое (SC-005 fail) | `StatsView.vue` обработчик 503 не подключён. Проверить Network panel — приходит ли `503 stats.unavailable` |
| Debug endpoint не отвечает | `StatsDebugController.kt` не зарегистрирован в Spring. Проверить `@RestController` и `@PostMapping` |

## Done Definition

Спека считается реализованной, когда ВСЕ 6 сценариев выше проходят
успешно (с Expected результатами) на dev-машине. После этого —
создать PR (через feature-ветку `174-fix-stats-connection-leak`,
см. [AGENTS.md](../../AGENTS.md) секция «CI-gate для master»), дождаться
CI 7/7 SUCCESS, merge в master.
