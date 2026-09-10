# Implementation Plan: 414 Request-URI Too Large на /playlists/membership у крупных авторов

**Branch**: `361-playlists-membership-uri-length` | **Date**: 2026-09-10 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/361-playlists-membership-uri-length/spec.md`

## Summary

Исправить баг OpenProject #77 — `GET /api/public/account/playlists/membership?ids=22982,...` превышает 8 КБ HTTP-лимит nginx у крупных авторов (≈2500+ песен), в результате браузер получает `414 Request-URI Too Large` и иконки избранного/плейлистов не отрисовываются.

**Технический подход**: добавить новый `POST /api/public/account/playlists/membership` endpoint с JSON-телом `{"ids": number[]}`. Старый GET-эндпоинт оставить для backward-compat (согласно FR-002 / US3 спеки). Фронт (`karaoke-public/src/services/playlistApi.js`) переключить с `authGet` на `authPost`. Никаких изменений в SQL — переиспользуется существующий `SitePlaylistItem.songIdsInPlaylists(...)`. Без CSRF-middleware (аутентификация через JWT в `Authorization: Bearer`, см. `SiteAuthInterceptor.kt:25` + Clarifications § Session 2026-09-10).

## Technical Context

**Language/Version**: Kotlin 2.x, JDK 17 (backend), Vue 3 + Vite (frontend)
**Primary Dependencies**: Spring Boot 3.x (Spring Web MVC), Jackson (JSON), Bootstrap 5, Vue Composition API
**Storage**: PostgreSQL (через raw JDBC, существующая таблица `tbl_site_playlist_items`, без миграций)
**Testing**: ручное (CI не имеет автоматических тестов, см. AGENTS.md § Тесты)
**Target Platform**: Linux-сервер (karaoke-web в Docker-контейнере + nginx reverse proxy)
**Project Type**: web-service (HTTP API + SPA frontend, single repo multi-module Gradle)
**Performance Goals**: ≤ 5 сек до отрисовки первой страницы песен на 2500 id (не хуже, чем GET, см. SC-002 спеки)
**Constraints**:
- nginx `large_client_header_buffers` = 8 КБ (по умолчанию) — лимит для GET
- nginx `client_max_body_size` ≥ 1m (по умолчанию) — для POST должно хватить
- Constitution Principle II: только raw JDBC, без JPA/Hibernate
- Constitution Principle VIII: никаких новых секретов
- Constitution Principle IX: Knowledge-first ✅ (pre-flight в спеке)

**Scale/Scope**:
- 1 backend endpoint (POST + сохранение GET)
- 1 DTO (MembershipRequest)
- 1 frontend service method (authPost вместо authGet)
- ~10 строк кода (Kotlin + JS)
- Без миграций БД, без новых таблиц

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Principle I — Self-contained автопайплайн

✅ **PASS**. Фича не затрагивает ML/рендер-пайплайн. Чисто HTTP API.

### Principle II — Сырой JDBC + дифф по хэшам

✅ **PASS**. Никаких новых SQL-запросов. Переиспользуется существующий
`SitePlaylistItem.songIdsInPlaylists(...)`. Никакого JPA/Hibernate.

### Principle III — Двух-БД синхронизация через SyncRegistry

✅ **N/A**. Endpoint читает только membership (read-only), не пишет в
БД. `tbl_site_playlist_items` не входит в sync (это user-data, не
каталог песен).

### Principle IV — Async-очередь задач с парсингом stdout

✅ **N/A**. Синхронный HTTP endpoint, не subprocess.

### Principle V — Двух-фронтенд

✅ **PASS**. Затрагивает только `karaoke-public` (фронт) и
`karaoke-web` (бэкенд). `webvue3` (admin) — не затрагивается.

### Principle VI — Code Standards

✅ **WILL COMPLY**.
- **FR-006**: новый `MembershipRequest` DTO + `PublicPlaylistController.membershipPost(...)`
  MUST иметь KDoc с `@see` на `docs/features/playlist-membership.md`
  (проверить наличие в tasks.md; создать если отсутствует).
- **FR-007**: ktlint + ESLint MUST запускаться через pre-commit hooks.
- **FR-009**: при правке `PublicPlaylistController.kt` MUST обновить
  per-feature документ `docs/features/playlist-membership.md` (если
  существует; иначе — создать).

### Principle VII — Cross-Machine Setup

✅ **N/A**. Локальная фича.

### Principle VIII — Секреты и git-гигиена

✅ **PASS**. Никаких новых секретов. Используется тот же JWT.

### Principle IX — Knowledge-first

✅ **PASS**. Pre-flight выполнен в `spec.md § Knowledge References`.
Дополнительная проверка перед implement (MUST): ещё раз перечитать
`knowledge/domains/karaoke-web/components/public-controllers-6.md` для
уточнения текущего состояния `PublicPlaylistController.kt`.

## Project Structure

### Documentation (this feature)

```text
specs/361-playlists-membership-uri-length/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── api-public-account-playlists-membership.md  # OpenAPI-style contract
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (by /speckit.tasks)
```

### Source Code (repository root)

Затрагиваемые файлы:

| Файл | Изменение |
|---|---|
| `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlaylistController.kt` | Добавить `@PostMapping("/playlists/membership") fun membershipPost(...)`. Вынести общую логику в private `fun buildMembershipResponse(user: SiteUser, songIds: List<Long>): Map<String, Any>`. Старый `@GetMapping("/playlists/membership")` оставить — body делегирует в ту же private. |
| `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/dtos/MembershipRequest.kt` | **Новый** DTO `data class MembershipRequest(@JsonProperty("ids") val ids: List<Long>)`. |
| `karaoke-public/src/services/playlistApi.js` | `fetchMembership(ids)` переписать: `authPost(BASE + '/playlists/membership', { ids }, token())` вместо `authGet(...?ids=...)`. Использовать существующий `authPost`. |
| `karaoke-public/src/services/authApi.js` | Проверить, что `authPost` ставит `Content-Type: application/json` (если нет — добавить в `tasks.md`). |
| `docs/features/playlist-membership.md` | Если существует — обновить (FR contract + curl example). Если нет — создать минимальный (Constitution FR-009). |
| `knowledge/domains/karaoke-web/components/public-controllers-6.md` | Добавить упоминание `POST /api/public/account/playlists/membership`. |
| `knowledge/system/frontend/composable-playlist-membership.md` | Упомянуть, что `usePlaylistMembership.load()` теперь использует POST. |
| `knowledge/adr/0009-get-vs-post-large-payload.md` | **Новый** ADR — обоснование выбора POST (см. research.md § Decision 1). |

Без миграций БД. Без изменений в `webvue3` (admin).

**Structure Decision**: web-service структура (мульти-модуль Gradle);
изменения локализованы в 2 модулях (`karaoke-web` для backend,
`karaoke-public` для frontend).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Нет нарушений Constitution. Complexity tracking не требуется.

## Phase 0: Outline & Research

См. [research.md](./research.md) — все open questions (CSRF, альтернативы,
nginx limits) разрешены. Decision: POST с JSON body, без CSRF-middleware,
nginx лимиты не меняем.

## Phase 1: Design & Contracts

- [data-model.md](./data-model.md) — модель `MembershipRequest` + существующие entities (без изменений).
- [contracts/api-public-account-playlists-membership.md](./contracts/api-public-account-playlists-membership.md) — OpenAPI-style контракт обоих методов (GET backward-compat + POST новый).
- [quickstart.md](./quickstart.md) — пошаговая ручная проверка: как воспроизвести 414 → как проверить, что POST работает → как убедиться, что GET всё ещё работает.

## Re-evaluation Constitution Check (post-design)

После Phase 1 design переоценка:

- **Principle VI (FR-006 KDoc coverage)** — будет применён в tasks.md:
  каждый новый публичный метод MUST иметь KDoc с `@see` на
  `docs/features/playlist-membership.md`.
- **Principle IX (Knowledge-first)** — должно быть повторено в
  начале `/speckit.implement` (MUST-проверка в tasks.md).

Все остальные Principles остаются в PASS / N/A.

## Phase 2 (NOT INCLUDED — generated by /speckit.tasks)

`tasks.md` создаётся следующим шагом (Stage 4).