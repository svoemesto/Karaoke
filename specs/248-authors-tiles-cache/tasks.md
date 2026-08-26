---
description: "Task list для FR-105: кеш для /api/public/authors-tiles"
---

# Tasks: Кеш для /api/public/authors-tiles

**Input**: Design documents from `/specs/248-authors-tiles-cache/`
- plan.md (required)
- spec.md (required for user stories)

**Tests**: Конституция § Тесты — автоматические тесты в karaoke-app `@Disabled`. Тестирование — ручное на проде + `pg_log`-замеры. Tests-фазы НЕ включены.

**Organization**: Tasks сгруппированы по user story (US1 — основная, US2 — инвалидация по dirty-флагу).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно делать параллельно (разные файлы, без зависимостей)
- **[Story]**: к какой user story относится (US1, US2)
- В описаниях — точные file:line

## Path Conventions

- **Single project**: `karaoke-web/src/main/kotlin/...`, `karaoke-app/src/main/kotlin/...`, тесты отсутствуют (см. Constitution)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка ветки и preconditions проверка.

- [ ] T001 [P] Переключиться на ветку `248-authors-tiles-cache`, убедиться что `git status` чистый.
- [ ] T002 [P] Прочитать `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:authorsTiles()` (строки 141-181). Подтвердить сигнатуру: `scope: String?`, `request: HttpServletRequest`, возвращает `List<AuthorTilePublicDto>`.
- [ ] T003 [P] Проверить наличие `StatBySong.consumeDirty(): Boolean` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/StatBySong.kt:60` и `StatBySong.markDirty()` на строке 54. Если нет — fallback на локальный `AtomicBoolean dirty` в `PublicApiController`.
- [ ] T004 [P] Проверить `KaraokeProperties.getBoolean(key: String): Boolean` доступность в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt:96`. Дефолт — `false`; для нашего свойства будет зарегистрирован `defaultValue = true`.
- [ ] T005 [P] Проверить `cross-module import`: `karaoke-web` зависит от `karaoke-app` через `implementation(project(":karaoke-app"))` в `karaoke-web/build.gradle.kts:24`. Импорт `com.svoemesto.karaokeapp.KaraokeProperties` в `PublicApiController` — корректен.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Регистрация property в `KaraokeProperties` и базовая инфраструктура кеша.

**⚠️ CRITICAL**: User story не может начаться, пока Phase 2 не завершена.

- [ ] T006 [P] Добавить в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt` в список `listKaraokeProperties` (рядом с `karaoke.db.schema_cache.enabled`, строка ~322) новое свойство:
  ```kotlin
  // Кеш для /api/public/authors-tiles (spec 248). Дефолт true — кеш прозрачен
  // для всех вызовов endpoint'а, TTL=30 мин, инвалидация через StatBySong.consumeDirty().
  // false — отключает кеш (полезно при отладке данных плашек авторов).
  KaraokeProperty(
      key = "karaoke.public.authors-tiles-cache.enabled",
      defaultValue = true,
      description = "Кеш для /api/public/authors-tiles (TTL=30 мин). false = каждый запрос идёт в БД (отладка данных плашек).",
  ),
  ```
- [ ] T007 Добавить в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt` новые импорты (после строки 13):
  ```kotlin
  import com.svoemesto.karaokeapp.KaraokeProperties
  import java.util.concurrent.ConcurrentHashMap
  ```
- [ ] T008 Запустить `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` — должен пройти без ошибок. Если есть — починить.

**Checkpoint**: Foundation готова — можно начинать US1.

---

## Phase 3: User Story 1 — Быстрый ответ главной страницы «Закромов» (Priority: P1) 🎯 MVP

**Goal**: Warm path `/api/public/authors-tiles` отвечает за <50 мс (0 SQL). Cold start — за <500 мс (2 SQL). Cache hits в течение 30 минут.

**Independent Test**: 100 повторных вызовов через curl → `pg_log` ≤2 SQL. Browser devtools показывает warm path <50 мс.

### Implementation for User Story 1

- [ ] T009 [US1] Добавить в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt` после объявления класса (строка 63) новый `companion object` с константами, data class, ConcurrentHashMap и helper-функциями `getCachedAuthorsTiles` + `isCacheEnabled` (полный код — в plan.md, Phase 2). KDoc обязателен на оба helper'а (FR-006 spec.md, Constitution § VI FR-006).
- [ ] T010 [US1] Заменить тело `authorsTiles()` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:141-181` на обёрнутую в `getCachedAuthorsTiles(scope ?: "main", onlyPublished) { ... }` версию (полный код — в plan.md, Phase 3). Cache key = `"$scope:$onlyPublished"` (FR-008).
- [ ] T011 [US1] Добавить KDoc на `authorsTiles()` (если отсутствует) с `@see specs/248-authors-tiles-cache FR-001, FR-105 parent`.
- [ ] T012 [P] [US1] Запустить `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` — должен пройти. Если есть ошибки типов — починить.
- [ ] T013 [P] [US1] Запустить `./gradlew :karaoke-web:ktlintCheck` — никаких новых нарушений baseline. Если есть — починить (или добавить в baseline-файл с обоснованием, см. Constitution § VI FR-007).
- [ ] T014 [P] [US1] Запустить `./gradlew :karaoke-web:bootJar :karaoke-app:bootJar --parallel` — должен пройти.
- [ ] T015 [US1] Локальная проверка (опционально): запустить karaoke-web локально, открыть `/api/public/authors-tiles?scope=main` в браузере. Должно вернуть список авторов. В docker logs — `cache miss scope=main onlyPublished=true` (1 раз). Повторный вызов — без `cache miss` (cache hit).

**Checkpoint**: US1 функциональна и независимо тестируема. Warm path <50 мс, cold start <500 мс.

---

## Phase 4: User Story 2 — Инвалидация кеша при изменении данных (Priority: P2)

**Goal**: При `StatBySong.markDirty()` (save/sync песни) следующий вызов endpoint сбрасывает cache через `consumeDirty()` и пересчитывает данные.

**Замечание**: Вся логика US2 реализована в T009/T010 (helper `getCachedAuthorsTiles` уже вызывает `consumeDirty()` в строке 2 алгоритма). Отдельной реализации нет — только verify.

### Verification for User Story 2

- [ ] T016 [US2] Локальная проверка (опционально): запустить karaoke-web локально, открыть `/api/public/authors-tiles?scope=main` 2 раза (cache populated). Вызвать `StatBySong.markDirty()` через тестовый endpoint или admin-панель. Открыть `/api/public/authors-tiles?scope=main` ещё раз → cache cleared, новый loadFn(). В docker logs — `cache cleared by consumeDirty()` + `cache miss scope=main onlyPublished=true`.
- [ ] T017 [P] [US2] Запустить `./gradlew :karaoke-web:ktlintCheck` — никаких новых нарушений. Подтвердить, что helper `getCachedAuthorsTiles` корректно обрабатывает `consumeDirty()` (FR-004).

**Checkpoint**: US2 автоматически удовлетворена через US1. Verification step.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Финализация, regression-тесты, документация.

- [ ] T018 [P] Создать per-feature документ `livedocs/features/248-authors-tiles-cache.md` с frontmatter, drill-down на spec.md/plan.md, кратким описанием что делает, эффект (SQL reduction), реализация (FR-001..FR-009), acceptance criteria.
- [ ] T019 [P] Обновить `livedocs/features/241-db-storage-perf-audit.md` — добавить ссылку на дочернюю фичу 248 (Tier-2 / FR-105) в раздел «Дочерние фичи».
- [ ] T020 [P] Regression-тест: убедиться, что endpoint работает для всех 3 scope (`main`, `special`, `all`) — cache key корректно разделяет.
- [ ] T021 [P] Regression-тест: убедиться, что `onlyPublished=true` (аноним) vs `onlyPublished=false` (редактор) дают разные результаты (разные cache keys).
- [ ] T022 [P] Code-review checklist (Constitution § VI FR-006, FR-009): KDoc на новых helper'ах есть, per-feature документ создан, baseline линтера не вырос.
- [ ] T023 [P] Замер `pg_log` за 24 ч после деплоя (SC-004): должно быть ≥80% снижение SQL от `/api/public/authors-tiles` при пиковой нагрузке (10 RPS). Сравнить с baseline.
- [ ] T024 Создать PR через `gh pr create --base master` (AGENTS.md «CI-gate для master»). Title: `authors-tiles-cache: TTL-кеш для /api/public/authors-tiles (FR-105)`.
- [ ] T025 Дождаться CI 8/8 PASS (`gh pr checks`), merge через `gh pr merge --merge` (БЕЗ `--delete-branch` — AGENTS.md «Lifecycle: ветка живёт после мёрджа»).
- [ ] T026 Deploy на прод через `deploy/deploy_public.sh` (karaoke-web). Post-deploy: снять `pg_log` через 24 ч, сравнить с T023, подтвердить SC-001..SC-005.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: нет зависимостей — можно начать сразу.
- **Phase 2 (Foundational)**: зависит от Phase 1 — БЛОКИРУЕТ все user stories.
- **Phase 3 (US1)**: зависит от Phase 2 — основная реализация.
- **Phase 4 (US2)**: зависит от Phase 3 — только verify (нет нового кода).
- **Phase 5 (Polish)**: зависит от всех user stories.

### Within Each User Story

- T001-T005 → параллельно (Setup preconditions).
- T006-T008 → последовательно (Foundational: property registration).
- T009-T015 → последовательно (US1: companion + wrap + compile).
- T016-T017 → параллельно (US2 verify).
- T018-T026 → параллельно где возможно (Polish).

### Parallel Opportunities

- Phase 1 полностью параллельна (T001-T005).
- T006 и T007 можно делать параллельно (property + imports).
- T012, T013, T014 можно делать параллельно (compile + lint + bootJar).
- T018-T023 можно делать параллельно (docs + regression + review).

---

## Implementation Strategy

### MVP First (US1 Only)

1. ✅ Phase 1: Setup (T001-T005).
2. ✅ Phase 2: Foundational (T006-T008) — зарегистрировать property.
3. ✅ Phase 3: US1 (T009-T015) — основной рефакторинг (companion + wrap).
4. **STOP and VALIDATE**: запустить на проде, проверить docker logs + `pg_log`.
5. Deploy на прод (karaoke-web).

### Incremental Delivery

Эта фича — US1 = MVP. US2 — только verify, нет нового кода. Не требует отдельного деплоя.

### Parallel Team Strategy

Фича маленькая (~1 час кодинга), один разработчик. Параллельная работа с другими Tier-1 фичами (FR-101, FR-102, FR-103, FR-104) и Tier-3 фичами возможна, потому что они в разных файлах.

---

## Notes

- Это **prod-критичная** фича — endpoint `/api/public/authors-tiles` вызывается на главной странице «Закромов» и влияет на пользователей. Deploy только на прод.
- `StatBySong.consumeDirty()` — существующий API (`karaoke-web/.../StatBySong.kt:60`), взводится `InternalStatsController.markDirty()` при save/sync песни. Использование — безопасно (атомарно сбрасывает флаг, не ломает `StatsCacheScheduler.refreshIfDirty`).
- `KaraokeProperties.getBoolean(key)` — существующий API (`karaoke-app/.../KaraokeProperties.kt:96`). Новое свойство регистрируется в `listKaraokeProperties` рядом с `karaoke.db.schema_cache.enabled` (тематически похожие cache-property).
- TTL 30 минут — компромисс между свежестью данных и нагрузкой. Альтернативы (TTL 1 час / 6 часов) ухудшают свежесть после save. Для пользователя «Закромов» 30 минут + dirty-инвалидация — оптимальный баланс.
- **Не блокирует** другие Tier-1/Tier-2 фичи — разные файлы, разные ветки.
- `pg_stat_statements` НЕ включается (parent спека, Clarifications Session 2026-08-26).
- После успешного merge в master — feature-ветка `248-authors-tiles-cache` НЕ удаляется (AGENTS.md «Lifecycle: ветка живёт после мёрджа»).
- Per-feature документ `livedocs/features/248-authors-tiles-cache.md` создаётся в T018 (Phase 5).