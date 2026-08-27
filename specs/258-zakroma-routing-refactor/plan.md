# Implementation Plan: Закрома — header-back-link из SongView + рефакторинг URL-routing

**Branch**: `258-zakroma-routing-refactor` | **Date**: 2026-08-27 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/258-zakroma-routing-refactor/spec.md`

## Summary

Устранение бага «header-back-link из SongView возвращает на тайты вместо списка песен автора» через **рефакторинг URL-routing** (выбранный в Clarifications вариант А):

- `/zakroma` → тайтлы авторов (только)
- `/zakroma/:authorId` → песни автора (ID = `Long`, regex `\d+`)
- `/zakroma/special-bucket` → спец-корзина (вынесена в отдельный route)

Дополнительно: добавление поля `id` в `AuthorTilePublicDto` для фронта, чтобы резолвить `tbl_authors.id` без дополнительных lookup-запросов.

Удаление хрупкого watcher'а из спеки 255 (vue-router сам пересоздаёт компонент при смене path).

Legacy redirect через `router.beforeEach` global guard для обратной совместимости `/zakroma?author=X` и `/zakroma?specialBucket=true`.

## Technical Context

**Language/Version**: Kotlin 1.x (JDK 17), Vue 3 + JavaScript ES2022 (karaoke-public)
**Primary Dependencies**: Spring Boot 3.x (backend), Vue Router 4, Vuex 3, Bootstrap 5, vue-router 4
**Storage**: PostgreSQL (raw JDBC, через `KaraokeConnection`) — НЕ ИЗМЕНЯЕТСЯ
**Testing**: нет автотестов (отсутствуют в проекте, см. AGENTS.md); проверка — пользователем в браузере
**Target Platform**: Linux (admin-машина + прод-сервер), Node.js 22 (frontend build)
**Project Type**: web-service (SPA frontend + REST backend)
**Performance Goals**:
- `/authors-tiles` chunked SELECT по 100 ID — без overhead на N+1
- `loadIdsByNames` для ~50 авторов — < 50 мс (один запрос)
- `loadAuthorTiles` dedup 30 сек (уже есть в спеце 248)
**Constraints**:
- Никаких изменений в backend кроме DTO `AuthorTilePublicDto` + нового helper `Author.loadIdsByNames`
- Никаких изменений в существующих API endpoints (`/zakroma`, `/zakroma/stream`, `/authors-tiles` — последний расширяется новым полем)
- Только frontend: router, view, watcher'ы
**Scale/Scope**:
- ~50-200 авторов (число тайлов)
- ~10000+ песен (число треков)
- 5 frontend-файлов (router + 2 views + store read + components/AppHeader без изменений)
- 2 backend-файла (DTO + Controller + новый helper)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Проверка | Статус |
|-----------|----------|--------|
| **I. Self-contained автопайплайн** | Все изменения — frontend + минимальный backend patch (DTO). Никаких внешних SaaS, ML-моделей, зависимостей. | ✅ PASS |
| **II. Сырой JDBC + дифф по хэшам** | Новый helper `Author.loadIdsByNames` использует raw SELECT (chunked, с explicit `PreparedStatement`). Никакого JPA/Hibernate. | ✅ PASS |
| **III. Двух-БД синхронизация через SyncRegistry** | Не затрагивается: `tbl_authors` не добавлена в `SyncRegistry` (только чтение). | ✅ N/A |
| **IV. Async-очередь задач с парсингом stdout** | Не затрагивается: рефакторинг не связан с длительными процессами. | ✅ N/A |
| **V. Двух-фронтенд** | Изменения только в `karaoke-public`, никаких смешений с admin (`webvue3`). | ✅ PASS |
| **VI. Code Standards (NON-NEGOTIABLE)** | Будет проверено через `./gradlew ktlintCheck` и `npm run lint` после имплементации. | ⏳ post-impl |
| **VII. Cross-Machine Setup** | Не затрагивается: нет изменений в локальных конфигах, `.gitattributes`, `.git-blame-ignore-revs`. | ✅ N/A |
| **VIII. Секреты и git-гигиена** | Никаких секретов: меняется публичный DTO и frontend код. | ✅ PASS |

**Итог**: все применимые principles проходят. Никаких нарушений не требуется обосновывать в Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/258-zakroma-routing-refactor/
├── plan.md              # Этот файл (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command) — RT-1..RT-8
├── data-model.md        # Phase 1 output — entities, fields, validation
├── quickstart.md        # Phase 1 output — 10 validation scenarios
├── contracts/           # Phase 1 output — C-1..C-9 contract definitions
│   └── index.md
├── checklists/
│   └── requirements.md  # Spec quality checklist
├── spec.md              # Source specification
└── tasks.md             # Phase 2 output (/speckit.tasks command — НЕ создаётся /speckit.plan)
```

### Source Code (repository root)

Затрагивает только 2 backend-модуля и 1 frontend-модуль:

```text
# BACKEND changes (минимальные)

karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
├── controllers/PublicApiController.kt    # +5 строк: AuthorTilePublicDto.fromAuthorName(id=...)
└── dto/AuthorTilePublicDto.kt            # +1 поле val id: Long, +1 параметр в fromAuthorName

karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
└── model/Author.kt                       # +30 строк: companion object fun loadIdsByNames(names, database)


# FRONTEND changes (основной объём)

karaoke-public/src/
├── router/index.js                       # +50 строк: 2 новых route + beforeEach guard
├── views/
│   ├── ZakromaView.vue                   # -25/+15 строк: data() через path, удалён watcher, mounted через ID
│   └── SongView.vue                      # +5 строк: computed songHeaderBack
└── store/modules/zakroma.js              # БЕЗ ИЗМЕНЕНИЙ (только чтение state.authorTiles[].id)
```

**Structure Decision**: Web-приложение с разделением backend (Kotlin) / frontend (Vue). Изменения минимально инвазивны: 3 frontend-файла + 2 backend-файла. Никаких новых модулей, никаких новых зависимостей.

## Phase 0 Research — резюме

8 research-вопросов (RT-1..RT-8), все разрешены. Детали — в `research.md`:

| ID | Вопрос | Решение |
|----|--------|---------|
| RT-1 | Backend не отдаёт `id` автора | Добавить `id: Long` в `AuthorTilePublicDto` + новый helper `Author.loadIdsByNames` |
| RT-2 | Watcher из спеки 255 — нужен ли? | Нет, vue-router пересоздаёт компонент при смене path |
| RT-3 | Legacy redirect — per-route или global guard? | Global `router.beforeEach` (async, доступ к Vuex) |
| RT-4 | Где брать имя автора по ID? | Computed в `ZakromaView` через `state.authorTiles.find` |
| RT-5 | `SpecialBucketView` или переиспользовать `ZakromaView`? | Переиспользовать (меньше дублирования) |
| RT-6 | Валидация `:authorId` — regex в path или в компоненте? | Regex в path `(\\d+)` → 404 на невалидных |
| RT-7 | Спец-корзина: путь к загрузке данных | `loadSpecialBucket()` уже вызывается в mounted() |
| RT-8 | `songFilter` — сброс через watcher или через пересоздание? | Через пересоздание компонента (data() заново) |

## Phase 1 Design — резюме

5 сущностей затронуты (детали в `data-model.md`):

1. **`AuthorTilePublicDto`** — добавляется `val id: Long`.
2. **`Author.loadIdsByNames(names, database)`** — новый raw-SQL helper.
3. **`PublicApiController.authorsTiles`** — расширяется для передачи `id` в DTO.
4. **vue-router routes** — добавляются `/zakroma/:authorId(\\d+)` и `/zakroma/special-bucket` + legacy redirects.
5. **`ZakromaView.vue`** — `data()` инициализируется из `params`, watcher удалён, `mounted()` резолвит ID → name.
6. **`SongView.vue`** — computed `songHeaderBack` строит back-link динамически.

9 контрактов (C-1..C-9) описаны в `contracts/index.md`. Ключевые:
- **C-1**: Backend `/authors-tiles` отдаёт `id` (добавление поля, не breaking change).
- **C-4**: Frontend routes + `beforeEach` guard.
- **C-6**: `ZakromaView` state-машина.
- **C-7**: `SongView` back-link.

10 сценариев валидации описаны в `quickstart.md` (от базового flow до regression-проверок).

## Re-evaluation Constitution Check (post-design)

| Principle | Проверка | Статус |
|-----------|----------|--------|
| **I. Self-contained** | Same as pre-design. | ✅ PASS |
| **II. Raw JDBC** | `Author.loadIdsByNames` — explicit raw SELECT с chunked PreparedStatement. | ✅ PASS |
| **V. Двух-фронтенд** | Same. | ✅ PASS |
| **VI. Code Standards** | Линтеры должны пройти post-impl. | ⏳ post-impl |
| **VIII. Секреты** | Same. | ✅ PASS |

**Итог**: post-design check не выявил новых нарушений. Все gates по-прежнему PASS.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| _none_    | _—_       | _—_ |

**Complexity Tracking пуст** — никаких нарушений Constitution для обоснования.

## Что НЕ входит в этот план (out of scope)

См. спеку § «Что НЕ входит в эту спеку»:
- Изменение спек 254/255 (фикс уже применён, watcher удаляется как часть 258).
- Рефакторинг `AuthorPlaylistView.vue`.
- Мультиязычность / i18n.
- SEO meta-теги для новых URL.
- Изменение backend API (кроме одного поля в DTO и одного helper).
- Аналитика кликов на back-link.

## Следующие шаги

1. `/speckit.tasks` — генерация задач (`tasks.md`) на основе `plan.md`.
2. Реализация задач по `tasks.md`.
3. Валидация по `quickstart.md` (10 сценариев).
4. Code review + линтеры + build (см. AGENTS.md «Обязательная проверка после ЛЮБОГО изменения кода»).
5. PR в master через `gh pr create` + `gh pr merge`.

## Артефакты, сгенерированные этим планом

| Файл | Назначение |
|------|------------|
| `specs/258-zakroma-routing-refactor/plan.md` | Этот файл — implementation plan |
| `specs/258-zakroma-routing-refactor/research.md` | Phase 0: 8 research-вопросов и решений (RT-1..RT-8) |
| `specs/258-zakroma-routing-refactor/data-model.md` | Phase 1: модель данных (6 затронутых сущностей) |
| `specs/258-zakroma-routing-refactor/contracts/index.md` | Phase 1: 9 контрактов (C-1..C-9) |
| `specs/258-zakroma-routing-refactor/quickstart.md` | Phase 1: 10 validation-сценариев |
