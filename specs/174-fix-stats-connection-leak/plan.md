# Implementation Plan: Починить flood JDBC-соединений при открытии вкладки «Статистика»

**Branch**: `174-fix-stats-connection-leak` | **Date**: 2026-08-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/174-fix-stats-connection-leak/spec.md`

## Summary

Устранить каскад `FATAL: sorry, too many clients already` при открытии вкладки
«Статистика» в `webvue3` за счёт трёх ортогональных механизмов: **(a)** lazy
load неактивных табов в `StatsView.vue` (10–12 параллельных HTTP → ≤2);
**(b)** in-process TTL-кеш 60 секунд для 6 чистых агрегатов (`/summary`,
`/timeseries`, `/channels`, `/countries`, `/referrers`, `/monetization`);
**(c)** осмысленный `503 stats.unavailable` с `<DbOverloadBanner>` вместо
«пустых графиков» при сбое БД. HikariCP connection pool **не включается**
в эту спеку (вынесено в отдельную задачу по решению Q1 в
[spec.md](./spec.md)).

## Technical Context

**Language/Version**:
- Backend: Kotlin 1.x, JDK 17, Gradle multi-module.
- Frontend: Vue 3 + JavaScript (Vuex, Bootstrap-vue-next).

**Primary Dependencies**:
- Backend: Spring Boot 2.x/3.x, `org.postgresql:postgresql` (driver), SLF4J
  (уже в Spring Boot, без новых зависимостей). Без Caffeine/HikariCP —
  `ConcurrentHashMap` достаточно для 6 ключей.
- Frontend: Vue 3, Vuex, Bootstrap-vue-next (BTab, BSpinner, BButton уже
  используются). Без новых зависимостей.

**Storage**: PostgreSQL (через сырой JDBC + `KaraokeConnection` ThreadLocal).
Без изменений схемы БД — это runtime-fix.

**Testing**: В проекте нет CI (см. `AGENTS.md` секция «Тесты»). Проверка —
по ручному quickstart-сценарию в `quickstart.md` этого плана.

**Target Platform**: Linux server (Docker containers: `karaoke-app`,
`karaoke-web`, `karaoke-db`). Затрагивается только `karaoke-app` (backend
fix) и `webvue3` (admin SPA).

**Project Type**: web-service (multi-module Gradle + 2 SPA).

**Performance Goals** (из SC в [spec.md](./spec.md)):
- **SC-001**: ≤3 HTTP-запросов к `/api/stats/*` в первые 2 секунды после
  `mounted()` (сейчас 10–12).
- **SC-002**: 0 сообщений `FATAL: too many clients already` за 30 секунд
  при 10 F5 подряд.
- **SC-003**: ≤70 одновременных соединений в `pg_stat_activity` при
  пиковой нагрузке (5 админов × дашборд + 100 RPS публичный сайт).
- **SC-004**: p95 `/api/stats/summary` ≤500 мс (cache hit <10 мс).
- **SC-005**: 100% вкладок показывают `<DbOverloadBanner>` при сбое БД
  в течение ≤5 секунд.

**Constraints**:
- Без новых зависимостей в `build.gradle.kts` (только SLF4J, уже есть).
- `pg max_connections = 100` (дефолт) — НЕ повышается.
- HikariCP — НЕ включается (отдельная задача, см. FR-007 в
  [spec.md](./spec.md)).
- 174 существующих вызова `KaraokeConnection.getConnection()` по всему
  `karaoke-app` MUST продолжать работать без изменений (FR-008).
- Существующее поведение `StatsController.withDb { ... }` MUST быть
  сохранено (FR-006).

**Scale/Scope**:
- 1 admin-SPA (`webvue3`), 1 backend (`karaoke-app`),
  ~18k+ записей в `tbl_events`, ~6 чистых агрегатов для кеша.
- Frontend: ~7 табов в `StatsView.vue`, новый компонент
  `<DbOverloadBanner>` (~50 строк Vue), модификация `mounted()` (~30 строк).
- Backend: ~1 новый класс `StatsCache`, ~1 новый endpoint
  `POST /api/stats/debug`, ~5 строк в `StatsController` для обёртки
  6 endpoint'ов в кеш + 5 строк для 503-обработки.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Principle I — Self-contained автопайплайн (NON-NEGOTIABLE)

**PASS**. Задача — admin-frontend optimization, не затрагивает
audio/video-пайплайн. Никаких внешних SaaS-зависимостей в горячем пути.

### Principle II — Сырой JDBC + дифф по хэшам (NON-NEGOTIABLE)

**PASS**. Stats-эндпоинты уже используют `KaraokeConnection` через сырой
JDBC (см. `StatBySong.kt`, `MonetizationStats.kt`). FR-006 явно требует
сохранить `StatsController.withDb { ... }`. Новый `StatsCache` —
in-process `ConcurrentHashMap`, не persistence.

### Principle III — Двух-БД синхронизация через SyncRegistry

**PASS** (N/A для этой фичи). Stats-эндпоинты читают через `target`
параметр (LOCAL/SERVER), но не пишут и не синхронизируют. `tbl_events`
не участвует в `SyncRegistry.all` (read-only с точки зрения sync).

### Principle IV — Async-очередь задач с парсингом stdout

**PASS** (N/A). Все stats-эндпоинты — синхронный HTTP. Никаких
subprocess'ов. Никаких `ProcessBuilder`.

### Principle V — Двух-фронтенд: админка и публичный сайт

**PASS**. Изменения только в `webvue3` (admin SPA). `karaoke-public` не
затрагивается. Никакого смешения ответственности.

### Principle VI — Code Standards (NON-NEGOTIABLE)

**PASS с условиями**:
- KDoc обязателен на публичный API новых классов (`StatsCache`,
  `StatsDebugController`, `DbOverloadBanner`) с `@see docs/features/stats.md`
  (см. FR-009 и FR-006).
- JSDoc на props/emits `<DbOverloadBanner>`.
- KDoc `StatsCache` явно упоминает Thread-safety контракт (конкурентные
  read/write — `ConcurrentHashMap` уже thread-safe, но invalidate pattern
  документируем).
- Документация: FR-009 обновляет `docs/features/stats.md` — это
  per-feature документ для подсистемы статистики (см. `docs/features/README.md`).

### Principle VII — Cross-Machine Setup

**PASS** (N/A). Runtime-fix в коде, не setup-изменение.

### Principle VIII — Секреты и git-гигиена

**PASS**. Никаких секретов в этом фиксе. Только:
- SLF4J-логи (без `println` секретов — `errorCode`/`endpoint` нечувствительны).
- `db.getConnection()?.close()` (уже паттерн).
- `git ls-files` после коммита НЕ должен показать ничего нового из
  `deploy/.env` / `do.env` / `*.key` / `*.pem` (Constitution VIII.3).

**Все 8 гейтов PASS**. Никаких нарушений. Complexity Tracking — пусто.

## Project Structure

### Documentation (this feature)

```text
specs/174-fix-stats-connection-leak/
├── plan.md              # Этот файл
├── research.md          # Phase 0 — решения по open questions
├── data-model.md        # Phase 1 — сущности (StatsCache, DbOverloadBanner)
├── quickstart.md        # Phase 1 — ручные сценарии валидации
├── contracts/           # Phase 1 — API контракты
│   ├── stats-debug.md
│   └── stats-unavailable.md
└── tasks.md             # Phase 2 — НЕ создаётся /speckit.plan (см. ниже)
```

### Source Code (repository root)

**Структура multi-module Gradle, затрагиваемые модули**:

```text
karaoke-app/                                            # Spring Boot backend (затрагивается)
└── src/main/kotlin/com/svoemesto/karaokeapp/
    ├── controllers/
    │   └── StatsController.kt                          # MODIFY: wrap 6 endpoint'ов в cache + 503 handling
    └── services/
        ├── StatsCache.kt                               # NEW: in-process TTL cache (ConcurrentHashMap)
        └── StatsDebugController.kt                     # NEW: POST /api/stats/debug endpoint
└── src/main/kotlin/com/svoemesto/karaokeapp/model/
    ├── StatsCacheKey.kt                                # NEW: data class (endpointName, params)
    └── StatsDebugDto.kt                                # NEW: response DTO для debug endpoint

webvue3/                                                # Admin SPA Vue 3 (затрагивается)
└── src/
    ├── views/
    │   └── StatsView.vue                               # MODIFY: lazy load табов + error handling
    └── components/Stats/
        └── DbOverloadBanner.vue                        # NEW: баннер «БД перегружена»

docs/features/
└── stats.md                                            # MODIFY: добавить секции per FR-009
```

**Не затрагиваются**:
- `karaoke-web/` (тонкий слой, не содержит stats-эндпоинтов).
- `karaoke-public/` (публичный SPA, не использует `<DbOverloadBanner>`).
- `deploy/` (нет изменений в docker-compose, env, secrets).
- `KaraokeConnection.kt`, `Connection.kt` (контракт `getConnection()`
  не меняется).

**Structure Decision**: Документированная выше структура следует
существующей multi-module конвенции проекта. Никаких новых модулей не
создаётся. Изменения локализованы в:
- 1 файле `karaoke-app` (модификация + 2 новых класса в `services/` +
  2 новых data class в `model/`),
- 2 файлах `webvue3` (модификация `StatsView.vue` + новый
  `DbOverloadBanner.vue`),
- 1 файле `docs/features/stats.md`.

## Complexity Tracking

> Fill ONLY if Constitution Check has violations that must be justified.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет) | — | — |

**Constitution Check полностью PASS**, Complexity Tracking пуст.

## Phase 0: Research

См. [research.md](./research.md).

## Phase 1: Design & Contracts

См. [data-model.md](./data-model.md), [contracts/](./contracts/),
[quickstart.md](./quickstart.md).

### Re-evaluate Constitution Check (post-design)

Все 8 принципов по-прежнему PASS — дизайн не вводит новых зависимостей,
не нарушает сырой JDBC, не выходит за пределы admin-frontend.

**Готово к Phase 2 (`/speckit.tasks`)**.
