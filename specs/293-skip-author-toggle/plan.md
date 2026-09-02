# Implementation Plan: 293 — Галочка «Работа со SKIP-авторами и песнями» в настройках пользователя

**Branch**: `293-skip-author-toggle` | **Date**: 2026-09-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/293-skip-author-toggle/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Добавить булев флаг `can_work_with_skipped` в `tbl_site_users`, разрешающий
залогиненному пользователю (админу/редактору) видеть и работать с контентом,
скрытым механизмами SKIP (`tbl_authors.skip = TRUE` или тег `SKIP` в
`tbl_songs.tags`). Галочка выставляется **только админом** в форме
редактирования пользователя в `webvue3/SiteUsers/edit/SiteUserEdit.vue` (по
выбору пользователя в `/speckit.specify` — НЕ отображается в
`karaoke-public/AccountView`).

Технический подход:
- **Backend (Kotlin/Spring Boot, Flyway)**: миграция V45 добавляет колонку
  `can_work_with_skipped BOOLEAN NOT NULL DEFAULT FALSE` в `tbl_site_users`,
  пересоздаёт триггер `update_tbl_site_users_recordhash` (Constitution §III).
  Поле `canWorkWithSkipped` в `SiteUser`/`SiteUserDto` (data class без Jackson-
  суффикса — `canWorkWithSkipped` уже camelCase, без `is`-префикса).
- **Runtime-проверка прав**: расширяем существующий `SiteUserResolver`
  (`karaoke-web/.../services/SiteUserResolver.kt:24`), который уже возвращает
  `SiteUser?` — добавление нового поля в `SiteUser` автоматически делает
  `siteUserResolver.resolve(request)?.canWorkWithSkipped` доступным во всех
  контроллерах. **Без нового SQL на каждый запрос** (выполняет NFR-001).
- **Фильтрация SKIP**:
  - `tbl_authors.skip`: во всех публичных вызовах `Song.loadListAuthors(...)`
    параметр прокидывается на основе `canWorkWithSkipped` —
    `withSkiped = siteUser?.canWorkWithSkipped ?: false`. **Изменение
    дефолта НЕ требуется** (см. FR-007 — дефолт остаётся `true`, чтобы
    сохранить обратную совместимость внутренних вызовов из `karaoke-app`).
  - тег `SKIP` в `tbl_songs.tags`: в каждой точке фильтрации
    (`PublicOgSongController.isSkipped`, `SongShareLinkService`,
    `ListeningHistoryController`, `StatBySong`, `SongPublicDto.isSkipped`)
    добавляется проверка `siteUser?.canWorkWithSkipped`. В SQL
    (`StatBySong.SKIP_FILTER`) — два варианта SQL: строгий для анонимов
    и OR-вариант для пользователя с правом (см. research.md R3).
- **UI (webvue3/Vue 3)**: новая галочка в `SiteUserEdit.vue` и колонка
  «SKIP-доступ» в `SiteUsersTable.vue`. В `karaoke-public` — без изменений
  (по выбору пользователя).
- **Бейдж SKIP в UI**: новая Vue-компонента или inline-разметка в карточке
  автора/песни; показывается только при `canWorkWithSkipped = true`.
- **Share-link для SKIP запрещён** (FR-012, compliance): `SongShareLinkService`
  возвращает `409 Conflict` для SKIP-песен независимо от прав инициатора.

## Technical Context

**Language/Version**: Kotlin 1.x (JDK 17), Vue 3 (Composition API + Options API
микс — проект исторически), JavaScript ES2022, Node 22.
**Primary Dependencies**: Spring Boot 3.x (backend), Vue 3 + Vite + Bootstrap
5/Bootstrap-vue-next (frontend), PostgreSQL (через сырой JDBC, без JPA,
Constitution §II), Flyway для миграций БД.
**Storage**: PostgreSQL (одна БД, две роли: LOCAL admin и SERVER prod,
синхронизация через `SyncRegistry` + recordhash-триггеры, Constitution §III).
**Testing**: в CI тестов нет (`Constitution §II` — тесты `@Disabled`).
Проверка делается пользователем вручную после деплоя.
**Target Platform**: Linux (admin — local Docker compose; prod — сервер
`<PROD_SERVER_IP>`, образы `eclipse-temurin:22-jre-jammy`, `nginx:stable`,
`node:22-alpine`, см. Constitution).
**Project Type**: multi-module Gradle: `karaoke-app` (admin core engine),
`karaoke-web` (публичный API/Thymeleaf), `karaoke-public` (Vue 3 SPA),
`webvue3` (Vue 3 admin SPA).
**Performance Goals**: SQL-проверка флага — **не чаще 1 раза на HTTP-запрос**
(NFR-001, достигается через `SiteUserResolver` без кэша). UI-фильтрация
авторов/песен в публичных списках — те же N+1-паттерны, что и сейчас
(`loadListFromDb` с `author_in`-фильтром, см. `Zakroma.kt`).
**Constraints**: Constitution — без JPA, без Redis, сырой JDBC, dual-db sync,
recordhash; все секреты в `.gitignore`; образы строго JRE/node:22-alpine/
nginx:stable (НЕ `nginx:alpine`/`node:latest`).
**Scale/Scope**: 1 новая колонка БД + 1 новое поле DTO + 1 новый блок
webvue3 UI + 1 новая колонка в таблице webvue3 + бейдж в UI karaoke-public +
patch в 5 местах фильтрации SKIP + 1 место share-link блокировка. ~30–40
строк кода в каждом из 7 файлов.

## Constitution Check

*Gate: must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Применимость | Соответствие |
|---------|--------------|--------------|
| §I Self-contained автопайплайн | Не применимо — фича не про пайплайн медиа | **N/A** |
| §II Сырой JDBC + recordhash | Применимо — миграция через сырой `ALTER TABLE`, новая колонка попадает в recordhash-триггер | ✅ |
| §III Dual-db sync через SyncRegistry | `tbl_site_users` уже синхронизируется через `SyncTarget<SiteUser>`, новая колонка будет в recordhash | ✅ (миграция пересоздаёт триггер) |
| §IV Async-очередь | Не применимо | **N/A** |
| §V Двух-фронтенд | webvue3 (admin) и karaoke-public (public) — никакого смешивания; галочка только в admin | ✅ |
| §VI Code Standards | KDoc на новые публичные поля SiteUser/SiteUserDto, per-feature документ `editor-skipped-content-access.md` создаётся | ✅ |
| §VII Cross-Machine | Не применимо (нет кросс-машинных изменений конфигов) | **N/A** |
| §VIII Секреты | Не применимо — фича не про секреты | **N/A** |
| Ограничения доступа (nsa-i9/nsa) | Сборка `karaoke-app` разрешена, перезапуск контейнера — нет (см. AGENTS.md, Pass 282); все правки кода OK | ✅ (per user instruction in AGENTS.md) |

**Gates**: PASS — нет нарушений.

### Re-check после Phase 1 design

| Принцип | Соответствие после design |
|---------|---------------------------|
| §II Сырой JDBC + recordhash | ✅ Миграция V45 — сырой `ALTER TABLE` + пересоздание `update_tbl_site_users_recordhash` (см. `data-model.md`). Загрузка `SiteUser` через `KaraokeDbTable` (сырой reflection-loader, без JPA). |
| §III Dual-db sync | ✅ `tbl_site_users` уже в `SyncRegistry.all` (`SyncTarget<SiteUser>`); добавление `can_work_with_skipped` в recordhash — обязательное условие sync. Миграция содержит `UPDATE ... SET recordhash = md5(...) WHERE id > 0` для пересчёта md5 на существующих строках (см. `data-model.md`). |
| §V Двух-фронтенд | ✅ Все UI-изменения в admin (webvue3): галочка в `SiteUserEdit.vue`, колонка в `SiteUsersTable.vue`. В karaoke-public — только read-only рендер бейджа и скрытие share-кнопки для SKIP-песен. Никакого смешивания. |
| §VI Code Standards | ✅ KDoc на `SiteUser.canWorkWithSkipped` со ссылкой на спеку 293; KDoc на `SiteUserDto.canWorkWithSkipped` с `@get:JsonProperty("canWorkWithSkipped")` для единообразия; per-feature документ `docs/features/editor-skipped-content-access.md` создаётся в том же PR (см. quickstart.md «Связанные документы»). |
| Ограничения доступа | ✅ Никаких изменений в `deploy/.env`, никаких новых секретов. Изменения в коде — additive patches, не требуют перезапуска `karaoke-app` (можно пересобрать на nsa-i9/nsa без явного согласия по Pass 282). |

**Re-check итог**: PASS — design не вносит новых нарушений.

## Project Structure

### Documentation (this feature)

```text
specs/293-skip-author-toggle/
├── plan.md              # Этот файл
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── admin-site-user-api.md   # webvue3 SiteUser CRUD с canWorkWithSkipped
│   ├── public-zakroma-api.md    # /api/public/zakroma с учётом флага
│   ├── public-song-skip-api.md  # /api/public/songs/{id}, share-link, OG
│   └── public-account-me.md     # /api/public/account/me (DTO содержит поле)
├── checklists/
│   └── requirements.md  # Quality checklist
├── spec.md              # Feature spec
└── tasks.md             # Phase 2 (NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
# Изменения в существующих модулях (никаких новых модулей):
karaoke-app/
├── src/main/kotlin/com/svoemesto/karaokeapp/model/
│   ├── SiteUser.kt                              # +1 поле canWorkWithSkipped
│   └── SiteUserDto.kt                           # +1 поле canWorkWithSkipped
├── src/main/kotlin/com/svoemesto/karaokeapp/controllers/
│   ├── SiteUsersController.kt                   # PUT/POST поддерживает новое поле (через DTO)
│   ├── ListeningHistoryController.kt            # фильтрация SKIP через флаг
│   └── ApiController.kt                         # место использования фильтра SKIP — патч
└── src/main/resources/db/migration/
    └── (Flyway-номер не нужен — миграции в deploy/karaoke-db/, см. ниже)

karaoke-web/
├── src/main/kotlin/com/svoemesto/karaokeweb/
│   ├── controllers/
│   │   ├── MainController.kt                    # /zakroma прокидывает withSkiped
│   │   ├── PublicApiController.kt               # /zakroma, /authors-tiles с флагом
│   │   ├── PublicOgSongController.kt            # isSkipped() с флагом
│   │   └── PublicAccountController.kt           # /me возвращает поле (для будущих фич)
│   ├── services/
│   │   └── SongShareLinkService.kt              # 409 Conflict для SKIP-песен
│   └── StatBySong.kt                            # SKIP_FILTER с OR-вариантом
deploy/karaoke-db/
└── 45_site_user_can_work_with_skipped.sql       # Новая миграция (V45)

webvue3/
├── src/components/SiteUsers/
│   ├── edit/SiteUserEdit.vue                    # +1 блок с чекбоксом
│   ├── SiteUsersTable.vue                       # +1 колонка «SKIP-доступ»
│   └── store/siteUsers.js                       # columns + payload c новым полем
└── src/services/api.js (или аналог)             # PATCH/POST payload

karaoke-public/
├── src/views/
│   ├── ZakromaView.vue                          # (опц.) бейдж SKIP для редакторов
│   └── AuthorPlaylistView.vue                   # (опц.) бейдж SKIP
├── src/components/
│   └── (опц.) KmBadgeSkip.vue                   # Vue-компонента бейджа
└── src/composables/ (опц.)                      # useCanWorkWithSkipped (через /me)
```

**Structure Decision**: Существующая структура multi-module проекта. Никаких
новых Gradle-модулей, никакого нового фронтенд-приложения. Все изменения
— additive patches в существующих файлах + 1 новая SQL-миграция.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет нарушений) | — | — |

## Phase 0: Research

См. [research.md](./research.md).

## Phase 1: Design & Contracts

См. [data-model.md](./data-model.md), [contracts/](./contracts/),
[quickstart.md](./quickstart.md).

## Notes

- **AGENTS.md § «Обязательная проверка после ЛЮБОГО изменения кода»**:
  после правок — обязательная последовательность:
  1. Backend compile: `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`
  2. Линтеры: `./gradlew :karaoke-web:ktlintCheck` + webvue3 + karaoke-public ESLint
  3. Backend bootJar: `:karaoke-web:bootJar` (на nsa-i9/nsa — также `:karaoke-app:bootJar`)
  4. Frontend Vite (оба): webvue3 `npm run build && npm run format:check`,
     karaoke-public `npm run build && npm run format:check`
  5. Docker-образы (оба): `cd deploy && bash do.sh build_webvue3` (и `build_public` если менялся)
  6. Только после всех 5 шагов OK — сообщать «готово к деплою».
- **Constitution §III**: миграция `45_site_user_can_work_with_skipped.sql`
  применяется отдельно на LOCAL и SERVER (см. шапку миграции V40 для
  шаблона команд). Сначала LOCAL → проверить UI/логику → потом SERVER.
- **Контракт per-feature документа** (Constitution §VI FR-009):
  создать `docs/features/editor-skipped-content-access.md` в том же PR.
- **LiveDocs**: добавить `livedocs/features/293-skip-author-toggle.md` с
  SDD-сводкой после реализации.
- **Backfill**: не требуется (DEFAULT FALSE).
- **Регресс-тест**: после миграции проверить, что для анонимного
  пользователя `/api/public/zakroma` возвращает тот же набор авторов/песен,
  что и до фичи (см. SC-003).