# Implementation Plan: Починить 500 на `POST /api/public/share/claim`

**Branch**: `167-fix-share-claim-500` | **Date**: 2026-08-11 | **Spec**: [./spec.md](./spec.md)

**Input**: Feature specification from `/specs/167-fix-share-claim-500/spec.md`

## Summary

Hotfix фичи «Временный полный доступ к песне» (`add-song-share-link`): в проде `POST /api/public/share/claim` возвращает HTTP 500 с `errorCode: "share.notFound"` для заведомо валидных свежих ссылок. Двухслойная причина: (1) на проде не применены миграции `38_song_share_links.sql` / `39_song_share_recordhash.sql` → SQL-исключение `relation "tbl_song_share_links" does not exist`; (2) это исключение маскируется под `share.notFound` двумя catch-all'ами — в `SongShareLinkService.tryClaim:597-602` и в `PublicShareController.claim:174-175`. План: применить DDL вручную на проде, добавить новый sealed-подтип `ShareException.InternalError` + код `share.internal` для системных ошибок, заменить `catch (_: Exception)`-маскировку на корректный проброс.

## Technical Context

**Language/Version**: Kotlin 1.x (JDK 17) — `karaoke-web`. Конкретно правки в `SongShareLinkService.kt`, `PublicShareController.kt`, `ShareErrorCode.kt`. См. `constitution.md` «Технологический стек».

**Primary Dependencies**: Spring Boot 2.x/3.x (Web, JDBC). Новых зависимостей **нет** — всё на уже подключённых. См. `constitution.md`.

**Storage**: PostgreSQL через сырой JDBC (`KaraokeConnection`, `Connection.local()` = `WORKING_DATABASE`). Новых таблиц **не создаём** — DDL уже в гите (`deploy/karaoke-db/38_song_share_links.sql`, `39_song_share_recordhash.sql`), нужно применить на проде. См. Constitution II (NON-NEGOTIABLE).

**Testing**: нет CI-тестов; существующие тесты `@Disabled`. Проверка — по `quickstart.md` (ручные сценарии через `curl` + `psql`). См. `constitution.md` «Рабочий процесс».

**Target Platform**: Linux server. Production: `https://sm-karaoke.ru` (`app.public-site-url` в `application.yml` прод-окружения). Затрагивается только `karaoke-web` (контроллер + сервис + enum). Фронт (`karaoke-public`) уже умеет обрабатывать `errorCode` через `ShareErrorCode` — без изменений.

**Project Type**: web-service (backend API). Изменения только в `karaoke-web`. Никаких правок в `karaoke-app`/`karaoke-public`/`webvue3` не требуется для выполнения FR — только для диагностики/документации (см. FR-030..FR-032).

**Performance Goals**:
- `POST /api/public/share/claim` после фикса: 200 OK с `sessionTokenHash` (64 hex) — тот же p95, что до бага (~150-300 мс по локальным замерам, не SLO).
- `POST /api/public/share/debug`: остаётся узким диагностическим endpoint'ом, объём невелик.
- Никаких новых «горячих путей» — фикс чинит уже существующий.

**Constraints**:
- Никаких новых секретов (Constitution VIII). Параметры через существующие `WebShareProperties` без изменений.
- Никаких изменений в SyncRegistry (share-таблицы PROD-only, см. `SongShareLinkService.kt:10-14` + спека 164 FR-060).
- Никакого JPA/Hibernate (Constitution II).
- Никаких новых таблиц (DDL уже есть).
- Ручной деплой на прод — только пользователь (Constitution, «Ограничения агента», п. 2). Применение миграции 38/39 — отдельным шагом пользователя.

**Scale/Scope**:
- ~5k премиум-пользователей.
- ~30 генераций ссылок/сутки на активного пользователя.
- ~2 одновременных устройств на ссылку.
- Изменения: ~10-30 строк Kotlin в 3 файлах (`SongShareLinkService.kt`, `PublicShareController.kt`, `ShareErrorCode.kt`) + 1 строка в DDL README + 2 строки в `docs/features/guest-share-link.md` + 1 запись в `docs/architecture-notes.md` + 1 параграф в `AGENTS.md` Q&A.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Соблюдение | Заметки |
|---|---|---|
| **I. Self-contained** | ✅ | Никаких внешних SaaS. Изменения только в существующем коде. |
| **II. Сырой JDBC + recordhash** | ✅ | Все SQL-запросы остаются через `prepareStatement` (`SongShareLinkService.kt`). recordhash-триггер уже в DDL 39. Никаких правок в `KaraokeDbTable` (нет такого контракта для share — таблицы не синкаются). |
| **III. SyncRegistry** | ✅ | **НЕ расширяем** SyncRegistry — share-таблицы PROD-only (FR-060 спеки 164, явно закреплено в шапке миграции 38). |
| **IV. Async-очередь** | N/A | Не добавляем KaraokeProcess/Sweeper — это в спеке 164 (FR-040). Текущий hotfix — только правка catch-all + новый sealed-подтип. |
| **V. Двух-фронтенд** | ✅ | Изменения только в `karaoke-web` (backend). Фронт (`karaoke-public`) уже обрабатывает `errorCode` через `ShareErrorCode` — без правок. |
| **VI. Code Standards** | ✅ | Новый sealed-подтип `ShareException.InternalError` будет иметь KDoc с `@see docs/features/guest-share-link.md`. Все изменения в существующих классах — KDoc уже есть. |
| **VII. Cross-Machine** | ✅ | Без изменений в `.git-blame-ignore-revs` / `.gitattributes`. PR оформляется стандартно по конвенции (`167-fix-share-claim-500`). |
| **VIII. Секреты** | ✅ | Никаких hardcoded секретов. Никаких новых env-переменных. Никаких изменений в `deploy/.env` / `deploy/do.env`. |

**Gates**: ✅ Все проходят. **Complexity Tracking не требуется.**

## Project Structure

### Documentation (this feature)

```text
specs/167-fix-share-claim-500/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── api.md           # Phase 1 output (HTTP API контракт эндпоинтов)
├── checklists/
│   └── requirements.md  # (created by /speckit.specify)
├── spec.md              # (created by /speckit.specify)
└── tasks.md             # Phase 2 output (NOT created by /speckit.plan)
```

### Source Code (repository root)

Изменения в существующих модулях + новые файлы:

```text
karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
├── controllers/
│   └── PublicShareController.kt        # MODIFY: catch-all в /claim → share.internal
├── services/
│   └── SongShareLinkService.kt         # MODIFY: catch Exception → throw InternalError
└── util/
    └── ShareErrorCode.kt               # MODIFY: + INTERNAL("share.internal")

deploy/karaoke-db/
├── 38_song_share_links.sql             # (no source change) — applied to PROD
└── 39_song_share_recordhash.sql        # (no source change) — applied to PROD

docs/
├── features/guest-share-link.md        # MODIFY: секция «Диагностика 500» в Инвариантах
└── architecture-notes.md               # MODIFY: запись о PR (Pass 50+)

AGENTS.md                               # MODIFY: Q&A «500 на /api/public/share/claim» — обновить ссылку на /debug
```

**Structure Decision**: Web-service hotfix; никаких новых модулей. Все правки в `karaoke-web/...`. Документация обновляется в `docs/features/guest-share-link.md` (FR-009 — per-feature документ в том же PR), `docs/architecture-notes.md` (Pass 50+ запись), `AGENTS.md` Q&A (явная ссылка на новый диагностический flow).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Нет нарушений. Все 8 принципов соблюдены или явно N/A.

## Re-check после Phase 1 Design

Все решения Phase 1 (data-model, contracts, quickstart) **не нарушают** ни одного из 8 принципов:

| Принцип | Re-check после Phase 1 | Заметки |
|---|---|---|
| **I. Self-contained** | ✅ | Никаких внешних SaaS в новых структурах (`InternalError`, `INTERNAL`). |
| **II. Сырой JDBC + recordhash** | ✅ | data-model ссылается на существующий DDL 38/39 — никаких новых SQL-запросов, никаких новых таблиц. recordhash-контракт уже учтён в DDL 39. |
| **III. SyncRegistry** | ✅ | data-model явно говорит «SyncRegistry.all НЕ расширяется». share-таблицы PROD-only, как было. |
| **IV. Async-очередь** | N/A | Нет новых KaraokeProcess / @Scheduled. |
| **V. Двух-фронтенд** | ✅ | Никаких изменений во фронте в этом hotfix (только errorCode в JSON, фронт уже умеет generic-fallback). |
| **VI. Code Standards** | ✅ | `InternalError` имеет KDoc с `@see docs/features/guest-share-link.md` (FR-006). Per-feature документ `docs/features/guest-share-link.md` обновляется в том же PR (FR-009). |
| **VII. Cross-Machine** | ✅ | PR создаётся в ветке `167-fix-share-claim-500` (per AGENTS.md конвенции). Никаких изменений в `.git-blame-ignore-revs` / `.gitattributes`. |
| **VIII. Секреты** | ✅ | Никаких новых env-переменных. Никаких изменений в `deploy/.env` / `deploy/do.env`. `/debug` остаётся публичным (защита — backlog spec 164). |

**Post-design Gates**: ✅ Все проходят. **Complexity Tracking не требуется.**

## Phase 0: Research Summary

См. [research.md](./research.md) — детальный разбор:
- **R1**: где живёт catch-all маскировка и какие ветки catch нужны
- **R2**: структура `ShareException` (sealed class) и место для нового `InternalError`
- **R3**: состав `ShareErrorCode` enum и конвенция именования
- **R4**: идемпотентность миграций 38/39 и edge cases «частичного применения»
- **R5**: scope FR-014 audit (какие именно catch-all'ы с маскировкой реально есть)

## Phase 1: Design Artifacts

- [data-model.md](./data-model.md) — таблицы `tbl_song_share_links` + `tbl_song_share_sessions`, новый sealed-подтип `ShareException.InternalError`, новый код `ShareErrorCode.INTERNAL`
- [contracts/api.md](./contracts/api.md) — HTTP-контракты изменённых эндпоинтов `/claim`, `/create`, `/heartbeat`, `/release`, `/debug`, `/{songId}/create`, `/mine/{songId}/revoke`
- [quickstart.md](./quickstart.md) — 7 ручных сценариев: pre-deploy verification, apply migration, post-deploy smoke, simulated system error, debug endpoint, regression checks, rollback

## FR-014 Audit Conclusion (Pass 50, реализовано T012+T013)

В рамках hotfix мы **аудировали все 7 эндпоинтов** `PublicShareController.kt` на наличие
маскирующих `catch (_: Exception) { 500 share.notFound }`-паттернов:

| Endpoint | Метод | catch-all в коде | Действие | Audit |
|----------|-------|------------------|----------|-------|
| `POST /api/public/share/{songId}/create` | `create` (line 87-89) | ✅ Есть `(_: Exception) → 500 share.notFound` | T012: заменён на `(_: SongShareLinkService.InternalError) → 500 share.internal` | ✅ FIXED |
| `POST /api/public/share/claim` | `claim` (line 174-175) | ✅ Есть `(_: Exception) → 500 share.notFound` | T008: заменён на `(_: SongShareLinkService.InternalError) → 500 share.internal` | ✅ FIXED |
| `POST /api/public/share/heartbeat` | `heartbeat` (line 189-191) | ✅ Есть `(_: Exception) → 410 share.leaseExpired` | T013: заменён на `(_: SongShareLinkService.InternalError) → 500 share.internal` | ✅ FIXED |
| `POST /api/public/share/release` | `release` (line 197-210) | ❌ **Нет** `catch (_: Exception)` | Audit PASS: Spring default handler вернёт 500 без `errorCode` | ✅ PASS |
| `GET /api/public/share/mine/{songId}` | `getMine` (line 92-119) | ❌ **Нет** `catch (_: Exception)` | Audit PASS: Spring default handler вернёт 500 без `errorCode` | ✅ PASS |
| `POST /api/public/share/mine/{songId}/revoke` | `revoke` (line 121-130) | ❌ **Нет** `catch (_: Exception)` | Audit PASS: Spring default handler вернёт 500 без `errorCode` | ✅ PASS |
| `POST /api/public/share/debug` | `debug` (line 218-225) | ❌ **Нет** `catch (_: Exception)` | Audit PASS: Spring default handler вернёт 500 без `errorCode` | ✅ PASS |

**Итог**: 3 из 7 эндпоинтов маскировали системные ошибки (`share.notFound` или `share.leaseExpired` для не-доменных сбоев) — исправлены в T008, T012, T013. 4 из 7 эндпоинтов уже были корректны (нет catch-all, системные ошибки не маскируются, попадают в Spring default-handler с 500 без `errorCode`) — закрывают traceability FR-014 → tasks. Эти 4 эндпоинта НЕ требуют правок в этом hotfix.

**Trade-off**: 4 неисправленных эндпоинта при системной ошибке возвращают HTTP 500 с пустым телом (не JSON с `errorCode: "share.internal"`). Это **acceptable для hotfix** — диагностика идёт через логи karaoke-web + /debug endpoint (FR-020). В backlog spec 164 запланировано: либо добавить `@ExceptionHandler(InternalError::class)` в `@ControllerAdvice` для глобального маппинга, либо добавить InternalError catch-all в каждый оставшийся эндпоинт.
