# Implementation Plan: Порядок плашек в Закромах и явная сортировка спец-авторов

**Branch**: `307-special-authors-zakroma-order` | **Date**: 2026-09-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/307-special-authors-zakroma-order/spec.md`

## Summary

Задача №56 (OpenProject) — две связанных правки:

1. **Плашка «Отдельные песни разных авторов» должна стать ПЕРВОЙ** в сетке `/zakroma` (сейчас — последняя, через слот `<template #trailing>` в `karaoke-public/src/views/ZakromaView.vue`).
2. **Новое поле `sort_order` в `tbl_authors`** (INTEGER, default 0) — позволяет редактору управлять порядком плашек авторов: ненулевые `sort_order` идут перед нулевыми; внутри обеих групп — `ORDER BY sort_order ASC, author ASC`.

Технический подход:
- Миграция БД `deploy/karaoke-db/46_author_sort_order.sql` — добавляет колонку + пересоздаёт `recordhash`-триггер (идемпотентно).
- Backend: поле `sortOrder` в `Author.kt` + проброс в `AuthorDTO` + новый `ORDER BY` в `Author.loadAuthorTilesWithCounts`; поле `sortOrder` в `AuthorTilePublicDto`.
- Backend: новая колонка `sortOrder` в API `/api/public/authors-tiles`.
- Frontend публичный (`karaoke-public`): спец-плашка переезжает из `#trailing` в начало сетки в `ZakromaView.vue`.
- Frontend админка (`webvue3`): редактируемая колонка `sort_order` в `AuthorsTable.vue`.
- Per-feature документ `docs/features/zakroma-tiles-sort-order.md`.
- PR-чеклист фиксирует применение миграции на LOCAL и PROD.

Контрактные решения (из Clarifications): кеш `authorsTilesCache` НЕ меняем (только TTL ≤60с); миграция вручную на обе стороны; никаких новых визуальных эффектов для спец-плашки.

## Technical Context

**Language/Version**:
- Backend: Kotlin 2.x на JDK 17 (Spring Boot 3.x) — модули `karaoke-app` (admin-движок), `karaoke-web` (публичный API/Thymeleaf).
- Frontend: Vue 3 + Vite на Node 22 (LTS) — `webvue3` (admin, Bootstrap-vue-next) и `karaoke-public` (публичный, Bootstrap 5).
- БД: PostgreSQL с сырым JDBC (без JPA/Hibernate — Constitution Principle II).

**Primary Dependencies**:
- Backend: Spring Boot, `KaraokeDbTable` (raw-ORM через reflection по аннотации `@KaraokeDbTableField`), Jackson (DTO), `KaraokeConnection` (мульти-БД LOCAL/SERVER).
- Frontend: Vuex, Vue Router, Bootstrap 5 / bootstrap-vue-next.
- Сборка: Gradle multi-module (Kotlin), npm (Vue).

**Storage**:
- PostgreSQL (таблица `public.tbl_authors` — получает новую колонку `sort_order INTEGER NOT NULL DEFAULT 0`).
- Запись `recordhash` в md5 включает новое поле (миграция пересоздаёт триггер `update_tbl_authors_recordhash`).

**Testing**:
- В CI нет автоматических тестов (Constitution: «тесты — пользователем вручную»).
- Локально — manual SQL-проверка (см. SC-002) + browser-проверка `/zakroma` (SC-001).
- Если есть существующие unit/integration тесты, не должны ломаться (SC-005).

**Target Platform**:
- Server: Linux + Docker (Docker образы `eclipse-temurin:22-jre-jammy` для karaoke-web/app, `nginx:stable`, `node:22-alpine`).
- Browser: публичная сетка `/zakroma` (Chromium / Firefox / Safari — современные).
- Admin: `webvue3` SPA в `karaoke-app` admin-контексте.

**Project Type**: Web-приложение (двух-БД sync, multi-module: backend + 2 SPA).

**Performance Goals**:
- API `/api/public/authors-tiles` — без регрессии по сравнению с текущим: один SQL-запрос, добавляется только `ORDER BY sort_order ASC, author ASC` (стоимость сортировки незначительна для типичной выборки в сотни-тысячи строк).
- Кеш `authorsTilesCache` — TTL ≤60с (без изменений; см. Clarification Q5).
- Никаких новых внешних запросов, никаких новых микросервисов.

**Constraints**:
- **Сырой JDBC** (Constitution II): никакого JPA/Hibernate. Используем `KaraokeDbTable.loadList` + аннотация `@KaraokeDbTableField`.
- **recordhash** обязательно включает новое поле (Constitution II/III): иначе sync LOCAL↔SERVER сломается.
- **Backward compatibility API**: `AuthorTilePublicDto` получает новое поле `sortOrder` (default = 0); старые клиенты без этого поля получают дефолт — без breaking change.
- **Идемпотентность миграции**: `ADD COLUMN IF NOT EXISTS` + `CREATE OR REPLACE FUNCTION` — повторный запуск безопасен (SC-004).
- **Кеш**: НЕ трогаем `cache key` (Clarification Q5).
- **Визуал**: НЕ вводим новых эффектов (Clarification Q6).
- **Админка**: НЕ меняем серверную пагинацию/сортировку (FR-009).

**Scale/Scope**:
- БД: ~10k строк в `tbl_authors` (порядок на основе публичных данных).
- API: `/authors-tiles` отдаёт до ~10k тайлов (по реальной выборке `skip=false AND ready_songs_count>0` — обычно <1k для публичной поверхности).
- Тронем 5 файлов исходного кода + 1 файл миграции + 1 per-feature документ + 1 файл PR-чеклиста.
- 3 user stories, 13 FR, 7 SC.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Каждый принцип проверен против спеки. Все — PASS без нарушений:

- **I. Self-contained автопайплайн**: фича не затрагивает ML/обработку медиа. **PASS**.
- **II. Сырой JDBC + дифф по хэшам (NON-NEGOTIABLE)**: используем `KaraokeDbTable.loadList` с аннотациями; `recordhash`-триггер пересоздаётся в миграции (FR-002). **PASS**.
- **III. Двух-БД синхронизация через SyncRegistry**: колонка `sort_order` входит в `recordhash` (FR-002). Sync-механизм не ломается. PR-чеклист фиксирует применение миграции на обеих сторонах (FR-013, Clarification Q4). **PASS**.
- **IV. Async-очередь задач с парсингом stdout**: фича не затрагивает процесс-обработку. **PASS**.
- **V. Двух-фронтенд**: админка (`webvue3`) и публичный сайт (`karaoke-public`) — разные приложения. Изменения:
  - `karaoke-public/src/views/ZakromaView.vue` — публичный (рендеринг сетки).
  - `webvue3/src/components/Authors/AuthorsTable.vue` — админка (редактирование колонки).
  Ответственности НЕ смешиваются. **PASS**.
- **VI. Code Standards (NON-NEGOTIABLE)**: KDoc/JSDoc на новых публичных полях DTO/модели, линтеры (ktlintCheck + ESLint) пройдут (правки минимальны), per-feature документ создаётся (FR-012, SC-007). **PASS**.
- **VII. Cross-Machine Setup**: спека не затрагивает конфиги AI-агентов. **PASS**.
- **VIII. Секреты и git-гигиена**: спека не вводит секрет-файлов; миграция SQL не содержит credentials. **PASS**.

**Verdict: PASS** — никаких нарушений, Complexity Tracking не требуется.

## Project Structure

### Documentation (this feature)

```text
specs/307-special-authors-zakroma-order/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── authors-tiles-api.md
├── checklists/
│   └── requirements.md  # Уже создан в /speckit.specify
└── tasks.md             # Phase 2 output (/speckit.tasks — следующая команда)
```

### Source Code (repository root)

Тронем следующие файлы (точно — на основе референсов из спеки):

```text
deploy/
└── karaoke-db/
    └── 46_author_sort_order.sql        # НОВЫЙ: миграция БД (FR-001, FR-002, SC-004)

karaoke-app/
└── src/main/kotlin/com/svoemesto/karaokeapp/
    └── model/
        └── Author.kt                   # + поле sortOrder (FR-004), + ORDER BY (FR-003)

karaoke-web/
└── src/main/kotlin/com/svoemesto/karaokeweb/
    └── dto/
        └── AuthorTilePublicDto.kt      # + поле sortOrder (FR-004, FR-006)

karaoke-public/
└── src/
    └── views/
        └── ZakromaView.vue             # Перенос спец-плашки из #trailing в начало (FR-005)
    └── components/
        └── AuthorTiles.vue             # Опционально: очистить #trailing слот (FR-005b)

webvue3/
└── src/components/Authors/
    └── AuthorsTable.vue                # + редактируемая колонка sortOrder (FR-008)

docs/features/
└── zakroma-tiles-sort-order.md         # НОВЫЙ: per-feature документ (FR-012, SC-007)
```

**Structure Decision**: не вводим новых модулей или директорий; правки локализованы в существующих файлах, где логически живут эти сущности. Миграция БД — в стандартный каталог `deploy/karaoke-db/`, следующий свободный номер 46.

## Complexity Tracking

> **Не заполняется** — Constitution Check PASS без нарушений.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| —        | —         | —                                  |

## Phase 0 — Research (резюме)

Полный research.md будет создан после исследования; ниже — предварительный список вопросов, которые НЕ требуют `NEEDS CLARIFICATION` (всё закрыто в Clarifications спеки):

- ✅ **Q1. Отрицательные `sort_order`** — Clarification Q1 в спеке.
- ✅ **Q2. Плашка при пустом specialBucket** — Clarification Q2.
- ✅ **Q3. Сортировка в админке** — Clarification Q3 (НЕ трогаем).
- ✅ **Q4. Контракт синхронизации схемы LOCAL↔PROD** — Clarification Q4.
- ✅ **Q5. Поведение кеша** — Clarification Q5 (TTL достаточно).
- ✅ **Q6. Визуал спец-плашки** — Clarification Q6 (без изменений).

Никаких `NEEDS CLARIFICATION` не остаётся — все контрактные точки явно зафиксированы.

## Phase 1 — Design & Contracts (резюме)

Контрактные артефакты будут созданы в следующих файлах:
- `data-model.md` — таблица `tbl_authors` (новая колонка), `Author` model, `AuthorDTO`, `AuthorTilePublicDto`.
- `contracts/authors-tiles-api.md` — контракт API `/api/public/authors-tiles` (вход: `scope`; выход: `List<AuthorTilePublicDto>` с добавленным `sortOrder`).
- `quickstart.md` — пошаговая валидация: применить миграцию на LOCAL → пересобрать `karaoke-web` → открыть `/zakroma` → проверить порядок; затем на PROD.

## Следующая команда

После завершения `/speckit.plan` (создания `research.md`, `data-model.md`, `contracts/`, `quickstart.md`) — `/speckit.tasks` для генерации упорядоченного списка задач имплементации.
