# Implementation Plan: Анализ и устранение источников аномальной нагрузки на сайт

**Branch**: `187-site-traffic-anomaly-investigation` | **Date**: 2026-08-14 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/187-site-traffic-anomaly-investigation/spec.md`

## Summary

Эта фича решает проблему периодической недоступности сайта `sm-karaoke.ru` (7-10 мин инциденты раз в 1-2 недели). Основные источники нагрузки (см. [research.md](./research.md) Таблица A — 7 P1-источников):
1. **`AuthorTilePublicDto.authorPictureUrl`** — текущий URL через `/api/public/picture` влечёт 302-redirect через Spring-контроллер для каждого тайла автора (200+ редиректов при открытии `/zakroma`).
2. **`/api/public/song-picture/{id}` / `song-vk-image/{id}`** — генерация `BufferedImage` 500-1500 мс × N запросов ботов.
3. **`/registerevent` / `/api/public/events`** — каждый REST-запрос пишет синхронный INSERT в `tbl_events` без sampling/dedup.
4. **Polling-эндпоинты** (`news/since`, `chat/unreadcount`, `share/heartbeat`, `auth/me`) — без server-side cache создают постоянный поток запросов.
5. **`tbl_events` без retention** — растёт неограниченно (sync не настроен).
6. **nginx `/minio/` без cache headers** — браузер не кеширует картинки.

Технический подход:
- Изменить `AuthorTilePublicDto.authorPictureUrl` на прямой URL `/minio/karaoke/...` (FR-002).
- Добавить nginx `Cache-Control: public, max-age=86400` + `ETag` + короткий TTL для 404 (FR-003/004/005).
- Добавить `SamplingFilter` для `eventType=CALL_REST` (FR-006, clarified 1/20/1/5/1/1).
- Добавить `DedupCache` для `doRegisterEvent` (FR-007, clarified per-user scope).
- Добавить `PollingCache` для polling-эндпоинтов с разными TTL (FR-008, решение D-7).
- Добавить `RateLimitInterceptor` для `/song-picture` / `song-vk-image` (FR-010, 60 req/мин).
- Добавить `EventsRetentionScheduler` для ежедневной очистки `tbl_events` (FR-011, retention 7 дней).
- Добавить `DebugDbController` для `GET /api/public/debug/db` с IP allowlist (FR-013).
- Добавить `loadAuthorTiles` dedup в `zakroma.js` Vuex (FR-014, 30 сек TTL).

---

## Technical Context

**Language/Version**: Kotlin 1.x (JDK 17), Spring Boot 2.x/3.x. JavaScript ES6+ для Vue 3 (karaoke-public).

**Primary Dependencies**:
- Spring Boot (KaraokeWebApplication.kt).
- kotlinx-coroutines (для `SamplingFilter`/`DedupCache` — нет, используем `ConcurrentHashMap` напрямую).
- SLF4J для логирования (FR-012).
- Никаких внешних зависимостей (Redis, Caffeine) — всё через `ConcurrentHashMap` in-memory.

**Storage**: PostgreSQL (через `KaraokeConnection` thread-local, **Constitution II — никакого JPA/Hibernate**). MinIO для картинок. Никаких изменений схемы БД.

**Testing**: ручные сценарии в [quickstart.md](./quickstart.md) (10 сценариев). CI-тестов нет (см. AGENTS.md — тесты в проекте `@Disabled`, не полагаемся на них).

**Target Platform**: Linux server (prod), dev-pc (локально). Docker + docker-compose.

**Project Type**: Web-service (Spring Boot backend) + Vue 3 SPA (frontend).

**Performance Goals**:
- p95 latency ≤ 200 мс при 50 параллельных клиентах (SC-004).
- p99 latency ≤ 500 мс (SC-004).
- INSERT rate в `tbl_events` ≤ 5 строк/мин от polling (SC-003).
- `tbl_events` стабильный по размеру (SC-005).
- Нет 7-10 мин инцидентов при нормальной нагрузке (SC-006).
- FCP `/zakroma` ≤ 4 сек на 10 Mbps/50 ms RTT (SC-007).

**Constraints**:
- `pg max_connections = 100` (НЕ увеличиваем, маскировка проблемы).
- `KaraokeConnection` thread-local cache (НЕ переходим на HikariCP в этой фиче).
- Constitution II (сырой JDBC, recordhash не меняется).
- Constitution VIII (никаких секрет-файлов в git).
- Обратная совместимость (FR-019): старый `/api/public/picture` остаётся 302-redirect.

**Scale/Scope**:
- 1 instance `karaoke-web` на проде.
- 100 одновременных пользователей × 3 вкладки = 300 вкладок (нормальная нагрузка, A-001).
- 18k+ записей в `tbl_settings`, `tbl_events` растёт до равновесия ~N дней × rate.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Principle I: Self-contained автопайплайн
- ✅ Не затрагивается. Фича — публичный сайт, не медиа-пайплайн.

### Principle II: Сырой JDBC + дифф по хэшам
- ✅ Соблюдается. Все INSERT'ы идут через `connection.prepareStatement(...)` (как в текущем `doRegisterEvent`). Никакого JPA/Hibernate.
- ✅ Нет O(n²) сравнений — фича не касается sync LOCAL↔SERVER.
- ✅ `tbl_events` не участвует в sync (`27_listening_history.sql:3-6`) — retention scheduler не сломает sync.

### Principle III: Двух-БД синхронизация через SyncRegistry
- ✅ Не затрагивается. `tbl_events` не в `SyncRegistry.all` (проверено: `tbl_events` sync не настроен).
- ✅ recordhash триггеры не меняются (никаких изменений в SQL-миграциях).

### Principle IV: Async-очередь задач с парсингом stdout
- ✅ Не затрагивается. `EventsRetentionScheduler` — короткий `@Scheduled` (1 DELETE в день), не ProcessBuilder.

### Principle V: Двух-фронтенд: админка и публичный сайт — разные приложения
- ✅ Изменения только в `karaoke-web` (публичная часть) + `karaoke-public` (Vuex state).
- ✅ `webvue3` (админка) НЕ затрагивается.
- ✅ `<select>` — не вводится. `form-select` — не вводится. Bootstrap-классы не используются в этой фиче.

### Principle VI: Code Standards
- ✅ Все новые публичные классы MUST иметь KDoc/JSDoc с `@see docs/features/site-traffic-resilience.md` (FR-020).
- ✅ KDoc обязателен: `SamplingFilter`, `DedupCache`, `PollingCache`, `RateLimitInterceptor`, `EventsRetentionScheduler`, `DebugDbController`.
- ✅ Линтеры ktlint и ESLint будут зелёными (новый код соответствует стандартам проекта).

### Principle VII: Cross-Machine Setup
- ✅ Изменения только в backend/frontend коде. Никаких локальных AI-конфигов.
- ✅ Никаких изменений `.gitattributes` / `.git-blame-ignore-revs`.

### Principle VIII: Секреты и git-гигиена
- ✅ Новые env-переменные (sampling rate, TTL, retention) — не секреты. Не содержат паролей.
- ✅ `KARAOKE_WEB_DEBUG_DB_ALLOWED_IPS` — это IP-адреса, не секреты.
- ✅ Pre-commit проверка (`git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$'`) остаётся пустой.

**GATE: PASS** — все 8 принципов соблюдены.

---

## Project Structure

### Documentation (this feature)

```text
specs/187-site-traffic-anomaly-investigation/
├── plan.md              # Этот файл (/speckit.plan command output)
├── research.md          # Phase 0 output — полный аудит + решения
├── data-model.md        # Phase 1 output — новые runtime-объекты
├── quickstart.md        # Phase 1 output — ручные сценарии проверки
├── contracts/           # Phase 1 output — API-контракты изменений
│   ├── api-changes.md   # детальное описание 11 контрактов (C1-C11)
│   └── CONTRACT-CHECK.md # чек-лист обратной совместимости
├── checklists/
│   └── requirements.md  # quality checklist (все ✅ после clarifications)
├── spec.md              # входные требования (Q1-Q4 clarified)
└── tasks.md             # Phase 2 output — список задач для /speckit.tasks
```

### Source Code (repository root)

Изменения в следующих директориях:

```text
karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
├── controllers/
│   ├── MainController.kt                    # ИЗМЕНЕНИЕ: doRegisterEvent + sampling/dedup
│   ├── PublicApiController.kt               # ИЗМЕНЕНИЕ: authorsTiles + songPicture/songVkImage rate-limit
│   ├── PublicNewsController.kt              # ИЗМЕНЕНИЕ: since → PollingCache
│   ├── PublicChatController.kt              # ИЗМЕНЕНИЕ: unreadCount → PollingCache
│   ├── PublicShareController.kt             # ИЗМЕНЕНИЕ: heartbeat → PollingCache
│   └── DebugDbController.kt                 # НОВЫЙ: GET /api/public/debug/db (FR-013)
├── services/
│   ├── SamplingConfig.kt                    # НОВЫЙ: настройки sampling (FR-006)
│   ├── SamplingFilter.kt                    # НОВЫЙ: применяет sampling + dedup (FR-006/007)
│   ├── DedupCache.kt                        # НОВЫЙ: in-memory TTL cache (FR-007)
│   ├── PollingCache.kt                      # НОВЫЙ: in-memory TTL cache для polling (FR-008)
│   ├── RateLimitInterceptor.kt              # НОВЫЙ: rate-limit на конкретные endpoints (FR-010)
│   ├── EventsRetentionScheduler.kt          # НОВЫЙ: @Scheduled cleanup tbl_events (FR-011)
│   └── DebugDbAccessGuard.kt                # НОВЫЙ: IP allowlist для debug endpoint
└── dto/
    └── AuthorTilePublicDto.kt               # ИЗМЕНЕНИЕ: authorPictureUrl → прямой URL (FR-002)

karaoke-public/src/store/modules/
└── zakroma.js                               # ИЗМЕНЕНИЕ: state.lastLoadedTilesAt + action dedup (FR-014)

deploy/
├── 80to8897                                 # ИЗМЕНЕНИЕ: /minio/ location + cache headers (FR-003/004/005)

docs/features/
├── site-traffic-resilience.md               # НОВЫЙ: per-feature документ (FR-020)
└── README.md                                # ИЗМЕНЕНИЕ: добавить запись в Cross-cutting
```

### Структура изменений (по приоритету)

| Коммит | Содержимое | FR | Файлов |
|---|---|---|---|
| 1. Backend: dedup/sampling/caches | `DedupCache`, `SamplingFilter`, `PollingCache`, `SamplingConfig` + изменения в 5 контроллерах | FR-006, FR-007, FR-008, FR-009, FR-012 | ~10 файлов |
| 2. Backend: rate-limit | `RateLimitInterceptor` + 2 контроллера | FR-010 | ~3 файла |
| 3. Backend: retention | `EventsRetentionScheduler` | FR-011 | ~1 файл |
| 4. Backend: debug | `DebugDbController` + `DebugDbAccessGuard` | FR-013 | ~2 файла |
| 5. Frontend: Vuex dedup | `zakroma.js` state + action | FR-014 | ~1 файл |
| 6. nginx: cache headers | `deploy/80to8897` | FR-003, FR-004, FR-005 | ~1 файл |
| 7. DTO: прямой URL | `AuthorTilePublicDto.kt` | FR-001, FR-002 | ~1 файл |
| 8. Docs: per-feature + README | `docs/features/site-traffic-resilience.md`, `docs/features/README.md` | FR-020 | ~2 файла |

**Структура решения**: 8 коммитов, ~21 файл изменён/создан. Каждый коммит — отдельная логическая фича, может быть смержен независимо (но PR один для согласованности).

---

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Нет нарушений Constitution. Все 8 принципов соблюдены. См. Constitution Check выше.

---

## Implementation Phases (детально для /speckit.tasks)

### Phase 0: Research ✅ ЗАВЕРШЕНО

- [x] Аудит всех 52 публичных REST-эндпоинтов (Таблица A в [research.md](./research.md)).
- [x] Аудит всех 14 `@Scheduled` задач (Таблица B).
- [x] Принятие 8 решений (D-1 до D-8) по узким местам.
- [x] 4 открытых decisions для plan.md (OD-1 до OD-4).

### Phase 1: Design ✅ ЗАВЕРШЕНО

- [x] Описание 7 новых runtime-объектов (`DedupCache`, `SamplingConfig`, `SamplingFilter`, `PollingCache`, `RateLimitInterceptor`, `EventsRetentionScheduler`, `DebugDbController`).
- [x] Изменения в 5 существующих контроллерах и 1 DTO.
- [x] 11 API-контрактов (C1-C11) с проверкой обратной совместимости.
- [x] 10 ручных сценариев проверки (quickstart.md).

### Phase 2: Implementation (для /speckit.tasks)

**Рекомендуемый порядок задач** (по критичности):

1. **T01**: Создать `DedupCache` (базовая утилита).
2. **T02**: Создать `SamplingConfig` + `SamplingFilter` (FR-006/007).
3. **T03**: Изменить `MainController.doRegisterEvent` для использования `SamplingFilter` + логирование SQLException (FR-012).
4. **T04**: Создать `PollingCache` (FR-008 базовая).
5. **T05**: Применить `PollingCache` в `PublicNewsController.since`, `PublicChatController.unreadCount`, `PublicShareController.heartbeat` (FR-008).
6. **T06**: Создать `RateLimitInterceptor` (FR-010 базовая).
7. **T07**: Применить `RateLimitInterceptor` в `PublicApiController.songPicture` / `songVkImage` (FR-010) — через WebMvcConfig регистрацию (URL pattern `/api/public/song-picture/**` и `/api/public/song-vk-image/**`), НЕ через per-method аннотацию (U2 — `@RateLimit` не существует в кодовой базе).
8. **T08**: Создать `EventsRetentionScheduler` (FR-011).
9. **T09**: Создать `DebugDbAccessGuard` + `DebugDbController` (FR-013).
10. **T10**: Изменить `AuthorTilePublicDto.fromAuthorName` для прямого URL (FR-001/002).
11. **T11**: Изменить `zakroma.js` Vuex — добавить `lastLoadedTilesAt` + dedup в `loadAuthorTiles` (FR-014).
12. **T12**: Изменить `deploy/80to8897` — добавить cache headers (FR-003/004/005).
13. **T13**: Создать `docs/features/site-traffic-resilience.md` (FR-020).
14. **T14**: Обновить `docs/features/README.md` — добавить запись в «Cross-cutting».
15. **T15**: Добавить KDoc/JSDoc с `@see docs/features/site-traffic-resilience.md` для всех новых классов (Constitution VI / FR-020).
16. **T16**: Обновить `docs/architecture-notes.md` (Pass 60+ — запись о PR).
17. **T17**: Запустить pre-commit проверки (Constitution VIII.3 + базовая линия).
18. **T18**: Создать PR, дождаться CI 7/7 SUCCESS, merge.

---

## Детали реализации (контекст для tasks.md)

### Где разместить новые классы

В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/` (по convention — там живут `StatsCacheScheduler`, `ShareLinkSweeper`, `SubscriptionRenewalScheduler`).

### Как читать env-переменные

Через `KaraokeProperties.getString/getInt/getLong/getBoolean` (стандартный паттерн в проекте, не Spring `@Value`):

```kotlin
val samplingRate = KaraokeProperties.getInt("KARAOKE_WEB_EVENTS_SAMPLING_ANON", 20)
```

### Как тестировать локально

В `deploy/do.env` установить:
```
KARAOKE_WEB_EVENTS_SAMPLING_ANON=2  # для теста (50% вместо 5%)
KARAOKE_WEB_DEBUG_DB_ALLOWED_IPS=127.0.0.1,::1
```

Перезапустить karaoke-web: `cd deploy && bash do.sh build_start_web`.

### Как деплоить на прод

1. Накатить env в `deploy/do.env` (см. [CONTRACT-CHECK.md](./contracts/CONTRACT-CHECK.md) секция «Необходимые действия при деплое»).
2. Скопировать `deploy/80to8897` на сервер: `ssh root@188.119.64.111 "cp /root/Karaoke/deploy/80to8897 /etc/nginx/sites-enabled/80to8897 && nginx -t && systemctl reload nginx"`.
3. Перезапустить karaoke-web: `cd deploy && bash do.sh build_start_web` (Constitution Principle I — пользователь делает деплой).

---

## Открытые decisions (из research.md OD-1..OD-4)

| ID | Описание | Решение |
|---|---|---|
| OD-1 | TTL для каждого polling endpoint | news=60 сек, chat=10 сек, share=15 сек, auth/me=60 сек (см. D-7) |
| OD-2 | Куда вынести `loadAuthorTiles` dedup | В `zakroma.js` Vuex (FR-014 явно требует) |
| OD-3 | Дебаунс retry для сетевых ошибок тайла | Backlog (не блокирует фичу) |
| OD-4 | Sampling для админов | Все события от админов пишутся (rate=1/1) |

---

## Связанные документы

- [spec.md](./spec.md) — функциональные требования и success criteria.
- [research.md](./research.md) — детальный аудит + 8 решений (D-1..D-8) + 4 открытых decisions (OD-1..OD-4).
- [data-model.md](./data-model.md) — описание новых runtime-объектов.
- [contracts/api-changes.md](./contracts/api-changes.md) — 11 API-контрактов (C1-C11).
- [contracts/CONTRACT-CHECK.md](./contracts/CONTRACT-CHECK.md) — чек-лист обратной совместимости.
- [quickstart.md](./quickstart.md) — 10 ручных сценариев проверки.
- [`docs/features/stats.md`](../../docs/features/stats.md) — прецедент для in-memory cache.
- [`docs/features/dual-db-sync.md`](../../docs/features/dual-db-sync.md) — почему KaraokeConnection + thread-local.
- [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md) — NON-NEGOTIABLE принципы.
- [`AGENTS.md`](../../AGENTS.md) — общие правила для агента.
- [docs/architecture-notes.md](../../docs/architecture-notes.md) — Pass 50-60 (контекст предыдущих фиксов).
