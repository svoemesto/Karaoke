# Implementation Plan: Временный полный доступ к песне (завершение)

**Branch**: `164-complete-guest-share-link` | **Date**: 2026-08-10 | **Spec**: [./spec.md](./spec.md)

**Input**: Feature specification from `/specs/164-complete-guest-share-link/spec.md`

## Summary

Завершение фичи «Временный полный доступ к песне» (add-song-share-link / guest-share-link): backend и DDL уже есть (восстановлены в Pass 47 из git-fsck, см. `deploy/karaoke-db/38_*.sql`, `39_*.sql`), но критичные части не работают — гость не может открыть плеер, heartbeat/release не вызываются, админские endpoint'ы для webvue3 не реализованы, фоновый sweeper отсутствует. План: расширить `PublicPlayerController` для приёма session token, добавить heartbeat/release таймеры в `KaraokePlayer`, реализовать `SiteShareLinksController` для админки, создать `ShareLinkSweeper`, дополнить `WebShareProperties` и `WebMvcConfig`.

## Technical Context

**Language/Version**: Kotlin 1.x (JDK 17) — `karaoke-app`, `karaoke-web`; JavaScript ES2022 (Vue 3) — `karaoke-public`, `webvue3`. См. `constitution.md` «Технологический стек».

**Primary Dependencies**: Spring Boot 2.x/3.x (Web, JDBC, Scheduling), PostgreSQL JDBC, Vue 3 + Vite + Vuex, Bootstrap 5 / Bootstrap-vue-next. См. `constitution.md`.

**Storage**: PostgreSQL через сырой JDBC (`KaraokeConnection`, `Connection.local()`/`remote()`). Новых таблиц НЕТ — DDL уже в гите (`tbl_song_share_links`, `tbl_song_share_sessions`). См. Constitution II (NON-NEGOTIABLE).

**Testing**: нет CI-тестов; существующие тесты `@Disabled`. Проверка — по `quickstart.md` (14 ручных сценариев). См. `constitution.md` «Рабочий процесс».

**Target Platform**: Linux server (Jammy/JRE 22 для backend, Debian stable для nginx, Node 22-alpine для frontend). Production: `https://sm-karaoke.ru` (`app.public-site-url` в `application.yml`).

**Project Type**: web-service (backend API) + SPA (frontend). Модули:
- `karaoke-web` (Kotlin/Spring Boot) — backend, разворачивается на проде.
- `karaoke-public` (Vue 3 SPA) — публичный сайт + лендинг + плеер.
- `webvue3` (Vue 3 SPA) — админка, **только UI**.

**Performance Goals**:
- Открытие плеера гостем: <5 сек end-to-end (SC-001).
- Heartbeat: 25 сек интервал, lease продлевается мгновенно.
- Sweeper: 1000 активных ссылок / тик за <5 сек (SC-006, батчами по 100).
- Admin modal: <2 сек (SC-005).

**Constraints**:
- Никаких новых секретов (Constitution VIII). Лимиты через env `karaoke.share.*`.
- Никаких изменений в `tbl_settings`/`tbl_songs` (DDL share уже в гите).
- Никаких изменений в SyncRegistry (share-таблицы PROD-only).
- Никакого JPA/Hibernate (Constitution II).

**Scale/Scope**:
- ~5k премиум-пользователей.
- ~30 генераций ссылок/сутки на активного пользователя (дефолт).
- ~2 одновременных устройств на ссылку (дефолт).
- ~10k активных ссылок на проде (оценочно).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Соблюдение | Заметки |
|---|---|---|
| **I. Self-contained** | ✅ | Никаких внешних SaaS. |
| **II. Сырой JDBC + recordhash** | ✅ | Используем `prepareStatement` для всех новых запросов. DDL share уже содержит recordhash-триггеры (`39_song_share_recordhash.sql`). |
| **III. SyncRegistry** | ✅ | **НЕ расширяем** SyncRegistry — share-таблицы PROD-only (FR-060 спеки). |
| **IV. Async-очередь** | N/A | Sweeper — синхронный `@Scheduled`, не KaraokeProcess. |
| **V. Двух-фронтенд** | ✅ | Изменения только в `karaoke-public` (плеер/лендинг/модалка) и `webvue3` (admin модалка). Без смешивания. |
| **VI. Code Standards** | ✅ | Все новые классы получают KDoc с `@see docs/features/guest-share-link.md`. Создаём per-feature документ. |
| **VII. Cross-Machine** | ✅ | Без изменений в `.git-blame-ignore-revs` / `.gitattributes`. |
| **VIII. Секреты** | ✅ | Никаких hardcoded секретов — env через `${VAR}` в `application.yml`. |

**Gates**: ✅ Все проходят. **Complexity Tracking не требуется.**

## Project Structure

### Documentation (this feature)

```text
specs/164-complete-guest-share-link/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── api.md           # Phase 1 output
├── checklists/
│   └── requirements.md  # (created by /speckit.specify)
└── tasks.md             # Phase 2 output (NOT created by /speckit.plan)
```

### Source Code (repository root)

Изменения в существующих модулях + новые файлы:

```text
karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
├── config/
│   ├── WebShareProperties.kt           # MODIFY: + heartbeatIntervalSeconds
│   └── WebMvcConfig.kt                 # MODIFY: + /api/siteusers/** в path-patterns
├── controllers/
│   ├── PublicShareController.kt        # MODIFY: TTL whitelist (+604800)
│   ├── PublicPlayerController.kt       # MODIFY: authorized() принимает session
│   └── SiteShareLinksController.kt     # NEW: 3 admin endpoints
├── services/
│   ├── SongShareLinkService.kt         # USE: validateShareSession (уже есть)
│   └── ShareLinkSweeper.kt             # NEW: @Scheduled, 4 типа отзыва
└── util/
    └── ShareErrorCode.kt               # USE: существующие коды

karaoke-web/src/main/resources/
└── application.yml                     # MODIFY: + karaoke.share.* секция (опционально)

karaoke-public/src/
├── player/
│   └── KaraokePlayer.js                # MODIFY: +shareSessionTokenHash, heartbeat/release таймеры
├── views/
│   ├── PlayerView.vue                  # MODIFY: читает route.query.session
│   └── ShareView.vue                   # MODIFY: +кнопка «Скопировать ссылку», expiresAtLabel
├── components/
│   └── ShareLinkModal.vue              # MODIFY: автообновление 30 сек, error mapping
└── composables/
    ├── usePlayerAccess.js              # MODIFY: +опц. shareSessionTokenHash
    └── useShareLink.js                 # MODIFY: SHARE_TTL_OPTIONS +7 дней

docs/features/
└── guest-share-link.md                 # NEW: per-feature документация (FR-009 constitution)
```

## Complexity Tracking

Нет нарушений — все Constitution gates проходят. Таблица пустая по правилу шаблона.

## Артефакты Phase 0 / Phase 1

Сгенерированы в этой же директории:
- `research.md` — 6 архитектурных решений, обоснования, альтернативы.
- `data-model.md` — сущности, поля, lifecycle, validation rules, capacity assumptions.
- `contracts/api.md` — 4 группы endpoint'ов (Owner/Guest/Player/Admin), error codes.
- `quickstart.md` — 14 ручных сценариев + команды для проверки БД.

## Сводка решений (для следующих фаз)

| # | Решение | Файл | Ссылка |
|---|---|---|---|
| D1 | Прямой проброс sessionTokenHash в API плеера (не обмен на kp_token) | `PublicPlayerController.kt`, `KaraokePlayer.js` | research.md D1 |
| D2 | Передача session через query-param `?session=` | `PublicPlayerController.kt` | research.md D2 |
| D3 | `heartbeatIntervalSeconds` в `WebShareProperties` | `WebShareProperties.kt` | research.md D3 |
| D4 | `SiteAuthInterceptor` + ручная проверка `isEditor` | `WebMvcConfig.kt`, `SiteShareLinksController.kt` | research.md D4 |
| D5 | Sweeper: SQL + `SiteUser.isEffectivePremium` | `ShareLinkSweeper.kt` | research.md D5 |
| D6 | TTL whitelist: 3600 / 86400 / 604800 | `PublicShareController.kt`, `useShareLink.js` | research.md D6 |

## Границы доступа агента (см. constitution.md)

- ✅ Редактировать код во всех модулях.
- ✅ Собирать gradle-джары и npm build.
- ✅ Пересобирать локальные контейнеры `karaoke-web`, `karaoke-public`, `webvue3`.
- ⚠️ Локальный `karaoke-app` — собирать, но НЕ перезапускать (если не `dev-pc`/`dev`).
- ⚠️ Миграции применять локально (`docker exec -i karaoke-db psql < ...`), на проде — пользователь.
- ❌ Деплой на сервер — только пользователь.
- ❌ Прямой DDL/DML к прод-БД — только пользователь.

## Готовность к Phase 2

✅ Все NEEDS CLARIFICATION разрешены (5/5 в `## Clarifications` спеки).
✅ Архитектурные развилки решены (6 решений в `research.md`).
✅ Data model описан (`data-model.md`).
✅ API contracts зафиксированы (`contracts/api.md`).
✅ Валидационный план готов (`quickstart.md`).
✅ Constitution gates проходят.

Готово к `/speckit.tasks` — генерация задач для реализации.
