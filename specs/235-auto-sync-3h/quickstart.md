# Quickstart: Автозапуск «Синхронизации в 1 клик»

> Phase 1 output для фичи 235. Validation guide — НЕ implementation. Детали реализации — в `tasks.md` (Phase 2).

## Prerequisites

- Karaoke-app собирается и стартует на admin-машине (стандартный dev-loop).
- Postgres-контейнеры `karaoke-db-local` и `karaoke-db-server` запущены (`bash deploy/do.sh start_all` или эквивалент).
- webvue3 admin SPA доступна на `http://localhost:5173` (или через `karaoke-web` reverse-proxy).
- `Karaoke.properties` (base64-файл) доступен через UI Properties или напрямую (`/sm-karaoke/system/Karaoke.properties`).

## Сценарий 1: Базовый автозапуск (US1 AC1)

**Цель**: убедиться, что автозапуск действительно срабатывает каждые 3 часа.

1. Открыть UI Properties → раздел Sync → выставить `autoOneClickSyncIntervalMs = 60_000` (1 минута, для ускорения теста). `autoOneClickSyncEnabled = true`.
2. Перезапустить `karaoke-app` (`bash deploy/do.sh restart_karaoke_app`).
3. Подождать ≥ 5 минут (`autoOneClickSyncInitialDelayMs = 300_000`).
4. `tail -F karaoke-app.log | grep AutoOneClickSyncScheduler`:
   ```
   [AutoOneClickSyncScheduler] tick=2026-08-16T15:00:00 SUCCESS totals=...
   ```
5. Сделать правку в `tbl_songs` SERVER-БД вручную (`psql … -c "UPDATE tbl_songs SET … WHERE id=1"`).
6. Подождать ещё 1 минуту.
7. `psql -d local_karaoke -c "SELECT … FROM tbl_songs WHERE id=1"` — правка появилась в LOCAL-БД. **Сценарий пройден.**

**Откат**: вернуть `autoOneClickSyncIntervalMs = 10800000` (3 ч).

## Сценарий 2: Ручной клик во время автозапуска (US1 AC2)

**Цель**: убедиться, что ручной клик получает 409 Conflict, не ломая автозапуск.

1. Временно выставить `autoOneClickSyncIntervalMs = 30_000` (30 секунд), `autoOneClickSyncInitialDelayMs = 5_000` (для быстрого старта).
2. Перезапустить `karaoke-app`.
3. Дождаться первого автотика (в логах `[AutoOneClickSyncScheduler] tick=... RUNNING` — если повезёт поймать; иначе сразу SUCCESS).
4. **Сразу** после лога `RUNNING` (пока sync не закончился) — открыть `http://localhost:5173/sync` и кликнуть «🔄 Синхронизация в 1 клик».
5. Ожидаемое поведение:
   - UI показывает alert «Автосинхронизация уже выполняется в фоне, дождитесь завершения».
   - В логах: `org.springframework.web.bind.annotation.ResponseStatusException: 409 …` (или эквивалентный лог от Tomcat) — НЕ `[AutoOneClickSyncScheduler] acquired lock` повторно.
6. Дождаться завершения автотика (`SUCCESS` или `FAILED`).
7. Кликнуть кнопку ещё раз — теперь 200 OK и нормальный результат.

**Альтернатива** (если не получается поймать окно): добавить `Thread.sleep(60_000)` в scheduler-тин через временный `println` + `Thread.sleep`, чтобы удлинить окно для теста. Убрать после теста.

## Сценарий 3: Отключение автозапуска (US2)

**Цель**: убедиться, что `autoOneClickSyncEnabled=false` останавливает автозапуск, не ломая ручную кнопку.

1. `autoOneClickSyncEnabled = false` (через UI Properties).
2. Перезапустить `karaoke-app`.
3. В логах при старте:
   ```
   [AutoOneClickSyncScheduler] disabled by config (autoOneClickSyncEnabled=false)
   ```
4. Подождать ≥ 1 минуты (или 5 минут с дефолтным интервалом). В логах **НЕТ** записей `tick=... SUCCESS`/`FAILED`.
5. Открыть `http://localhost:5173/sync` → в блоке «Автозапуск»:
   - `enabled: false`
   - `lastRun: null` (если первый запуск после установки `false`).
6. Кликнуть «🔄 Синхронизация в 1 клик» — работает как обычно, 200 OK.
7. Вернуть `autoOneClickSyncEnabled = true`, перезапустить `karaoke-app` — автозапуск возобновляется.

## Сценарий 4: Блок «Автозапуск» в UI (US3 AC1-AC3)

**Цель**: убедиться, что UI отображает корректный статус.

1. После прохождения Сценария 1 (хотя бы 1 автотик прошёл).
2. Открыть `http://localhost:5173/sync`.
3. В верхней части страницы (над таблицей `SyncTable`) — блок «Автозапуск»:
   ```
   Автозапуск: ✅ включён
   Интервал: 3 ч (10800000 мс)
   Начальная задержка: 5 мин (300000 мс)
   Последний запуск: 2026-08-16 15:00:47 (SUCCESS)
     добавлено: 3, изменено: 12, удалено: 1
   Следующий (оценка): 2026-08-16 18:00:47
   ```
4. Проверить: если автозапуск **ещё не выполнялся** с момента старта — `Последний запуск: ещё не было`.
5. Если последний тик был **пропущен** (предыдущий ещё шёл) — `Последний запуск: пропущен (предыдущий ещё выполнялся)`.
6. F5 — блок обновляется (`loadSyncAutoStatusPromise` вызывается в `mounted()`).

## Сценарий 5: Сбой БД → fail-fast (FR-016, SC-009)

**Цель**: убедиться, что scheduler не падает при недоступности БД.

1. `autoOneClickSyncIntervalMs = 60_000` (1 минута).
2. Перезапустить `karaoke-app`, дождаться первого SUCCESS.
3. `docker stop karaoke-db-local` (или эквивалент — Postgres LOCAL контейнер).
4. Подождать 1 минуту. В логах:
   ```
   [AutoOneClickSyncScheduler] tick=2026-08-16T15:05:00 FAILED
   java.sql.SQLException: ...
   ```
5. `GET /api/sync/auto-status`:
   - `lastRun.status = "FAILED"`
   - `lastRun.reason = "SQLException: …"`
6. `docker start karaoke-db-local`, дождаться подъёма.
7. Подождать ещё 1 минуту. В логах:
   ```
   [AutoOneClickSyncScheduler] tick=2026-08-16T15:06:00 SUCCESS ...
   ```
8. Scheduler **не остановлен** — тики продолжаются.

## Сценарий 6: Per-target try/catch (FR-012, SC-007)

**Цель**: убедиться, что упавшая сущность не ломает остальные.

1. Создать в `tbl_test_failing_entity` (или любой существующей) намеренно сломанную запись (например, `recordhash` битый, или FK constraint нарушен).
2. Дождаться автотика.
3. В логах:
   ```
   [AutoOneClickSyncScheduler] target=test_failing_entity failed: <SQLException>
   ...
   [AutoOneClickSyncScheduler] tick=... SUCCESS totals={...}  // остальные прошли
   ```
4. В `lastRun.perTarget`:
   - `test_failing_entity`: `skipped=true` (или per-target с `direction="?"` и пустыми списками — по образцу п. 1 contracts).
   - Остальные: нормальные `created/updated/deleted`.
5. Следующий тик — снова пытается обработать `test_failing_entity` (это поведение существующего `runEntitySync`, retry-логика на уровне scheduler'а НЕ добавляется).

## Сценарий 7: Метрика «ровно 8 тиков за 24 часа» (SC-004)

**Цель**: подтвердить, что `fixedDelay` от завершения даёт стабильный ритм.

1. `autoOneClickSyncIntervalMs = 10800000` (3 ч), `autoOneClickSyncEnabled = true`.
2. `bash deploy/do.sh restart_karaoke_app` (записать `app_start_time`).
3. Подождать 24 часа. **Не выключать машину** (desktop-приложение).
4. `grep "AutoOneClickSyncScheduler.*SUCCESS" karaoke-app.log | wc -l` → должно быть `8 ± 1` (один тик мог быть FAILED, но FAILED тоже считается; проверка на `tick=` без фильтра статуса даст то же `8 ± 1`).

## Sanity checks (быстрые)

| Проверка | Команда / действие | Ожидаемый результат |
|----------|-------------------|---------------------|
| Scheduler зарегистрирован | `grep "AutoOneClickSyncScheduler" karaoke-app.log` | `disabled by config` или `tick=...` (в зависимости от настройки) |
| Endpoint работает | `curl http://localhost:8080/api/sync/auto-status` | 200 OK + JSON |
| Ручной клик в обычном режиме | `curl -X POST http://localhost:8080/api/sync/oneclick` | 200 OK + JSON list |
| Свойства в UI | `http://localhost:5173/properties` | три ключа `autoOneClickSync*` видны с правильными default |
| KDoc / JSDoc | `git grep "FR-235" -- '*.kt' '*.vue' '*.js' '*.md'` | Все новые публичные API помечены `@see` ссылкой на `docs/features/235-auto-sync-3h.md` (post-merge) |

## Что НЕ тестируется этим quickstart

- **Производительность** под нагрузкой 18k+ записей — out of scope (sync-движок уже оптимизирован через `recordhash` + `associateBy`, см. constitution II).
- **Кластерное развёртывание** — out of scope (karaoke-app — desktop, см. constitution §«Технологический стек»).
- **Persist'енция истории между рестартами** — by design не persist'ится (in-memory, см. `data-model.md`).
- **Live-push (SSE) обновление блока «Автозапуск»** — by design НЕ реализуется (Q2 в Clarifications).

## Rollback plan

Если что-то пошло не так:

1. `autoOneClickSyncEnabled = false` через UI Properties → автозапуск останавливается без рестарта `karaoke-app`.
2. Если `karaoke-app` не стартует — `git revert` PR и пересобрать `./gradlew clean karaoke-app:bootJar`.
3. Frontend (webvue3) — `git revert` → `npm run build`. Если сломан UI, можно временно откатить `SyncTable.vue` к версии до фичи.
