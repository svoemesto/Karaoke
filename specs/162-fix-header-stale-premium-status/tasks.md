---

description: "Task list template for feature implementation"
---

# Tasks: Устаревший премиум-статус в шапке сайта после окончания подписки

**Input**: Design documents from `/specs/162-fix-header-stale-premium-status/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, quickstart.md

**Tests**: Тесты не запрошены в спецификации; в проекте нет CI-тестов для `karaoke-public` (см. plan.md → Technical Context → Testing). Проверка — только ручная, по `quickstart.md`.

**Organization**: В `spec.md` описана ровно одна пользовательская история (P1) с пятью acceptance-сценариями (включая симметричный случай появления премиума) — задачи организованы одной фазой User Story 1, которая одновременно является MVP.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1)
- Include exact file paths in descriptions

## Path Conventions

Изменения полностью локализованы в `karaoke-public/src/` (публичный SPA). Backend (`karaoke-web`) не затрагивается — см. `plan.md` → Structure Decision.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Готовность окружения к правке — новых зависимостей/скаффолдинга фича не требует (изменяется существующий composable существующего SPA).

- [X] T001 Убедиться, что локальный dev-сервер `karaoke-public` поднимается и отдаёт текущее (ещё нерабочее) поведение бага для контроля "до" (`npm run dev` в `karaoke-public/` или `do.sh build_start_public`, см. `DEVELOPMENT.md`); залогиниться под тестовым premium-аккаунтом и зафиксировать, что значок 🪙 показывается в шапке

**Checkpoint**: Окружение готово, воспроизведён baseline "до фикса".

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Общая инфраструктура, которую разделяли бы несколько пользовательских историй.

**N/A для этой фичи**: в `spec.md` ровно одна пользовательская история (P1), новых сущностей/схемы БД/эндпоинтов нет (`data-model.md`, `research.md` Decision 1) — блокирующих общих задач, отдельных от самой User Story 1, не существует. Переходим сразу к Phase 3.

---

## Phase 3: User Story 1 - Статус в шапке сайта перестаёт быть премиум сразу после окончания подписки (Priority: P1) 🎯 MVP

**Goal**: Шапка сайта (`AuthStatusWidget.vue`) должна отражать актуальный, серверный премиум-статус пользователя в течение сессии — без обязательной перезагрузки страницы или повторного входа — и симметрично корректно показывать премиум сразу после его оформления/продления.

**Independent Test**: Взять аккаунт с активной premium-подпиской, снять премиум на сервере, не перезагружая страницу и не переходя на `/account`/`/account/editor/*` — убедиться, что значок 🪙 в шапке пропадает сам в течение одного фонового цикла обновления (см. `research.md` Decision 2 — 5 минут).

### Implementation for User Story 1

- [X] T002 [US1] В `karaoke-public/src/composables/useAuth.js` обернуть сетевой вызов внутри `fetchMe()` в `try/catch`: на отклонённый промисе (`authGet` реджектит на `xhr.onerror`, см. `services/authApi.js`) — тихо вернуть `null`, не трогая `user.value`/`token.value` (реализует `research.md` Decision 4 / FR-005 — временный сбой сети не должен ронять таймер и не должен изобретать премиум, которого нет)

- [X] T003 [US1] В `karaoke-public/src/composables/useAuth.js` добавить module-level guarded singleton `startAutoRefresh()`: если ещё не запущен и `isLoggedIn.value === true`, поставить `setInterval`, вызывающий `fetchMe()` каждые 5 минут (`5 * 60 * 1000` мс); гарантировать, что таймер стартует ровно один раз за жизнь вкладки, даже если `useAuth()` вызывается из нескольких компонентов/страниц (`AuthStatusWidget` монтируется на `HomeView`, `SearchView`, `ZakromaView`, `SongView`) — реализует `research.md` Decision 2 и Decision 3, закрывает FR-002 (depends on T002)

- [X] T004 [US1] В `karaoke-public/src/composables/useAuth.js` вызывать `startAutoRefresh()` (и через него — немедленный первый `fetchMe()`, не дожидаясь первого тика таймера) из самой функции `useAuth()` при каждом обращении, пока пользователь залогинен — это даёт свежий статус сразу при обычной загрузке/переходе на страницу с шапкой, а не только раз в 5 минут (усиливает FR-003 применительно к обычному заходу на страницу, депендс на T003) (depends on T003)

- [X] T005 [P] [US1] Проверить (без изменений кода, если предположение подтвердится), что `karaoke-public/src/components/AuthStatusWidget.vue` не требует правок: `isPremium` уже вычисляется из `user.effectivePremium`, а `user` — тот же реактивный module-level ref из `useAuth()`, который теперь обновляется автоматически (T002-T004) — если предположение не подтвердится (например, кэш `computed` не реагирует на мутацию `user.value`), внести минимальную правку в этот файл

- [X] T006 [US1] Прогнать все сценарии из `specs/162-fix-header-stale-premium-status/quickstart.md` (снятие премиума без перезагрузки, обновление страницы, logout/login, регрессия при стабильном премиуме, симметричный случай оформления премиума, edge case сетевого сбоя) на локальном dev-сервере под тестовым аккаунтом (depends on T004, T005)

**Checkpoint**: User Story 1 полностью функциональна и проверена независимо — это же MVP и вся фича целиком.

---

## Phase 4: Polish & Cross-Cutting Concerns

**Purpose**: Финальная гигиена перед PR — по правилам проекта (`CLAUDE.md` → «ОБЯЗАТЕЛЬНО перед каждым git commit»).

- [X] T007 [P] Прогнать `cd karaoke-public && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}"` и поправить замечания в изменённых файлах
- [X] T008 [P] Добавить короткую запись в `docs/architecture-notes.md` (датированный changelog) о фиксе устаревшего премиум-статуса в шапке — фича не входит в список 20+ ключевых подсистем `docs/features/README.md`, поэтому отдельный per-feature документ per FR-009 не требуется, но changelog-запись — существующая практика проекта
- [X] T009 Финальный ручной regression-проход: убедиться, что логин/логаут и обычная навигация по сайту (`/`, `/filter`, `/zakroma`, `/song`) не деградировали по скорости/поведению из-за нового фонового таймера (depends on T006)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей — можно начинать сразу
- **Foundational (Phase 2)**: N/A для этой фичи (см. выше) — Phase 3 стартует сразу после Phase 1
- **User Story 1 (Phase 3)**: единственная история, зависит только от Phase 1
- **Polish (Phase 4)**: зависит от завершения Phase 3

### Within User Story 1

- T002 → T003 → T004 — строго последовательно, один и тот же файл (`useAuth.js`), каждая правка опирается на предыдущую
- T005 — независим от T002-T004 по файлу, может выполняться в любой момент (параллельно), но осмысленно проверять его после T004 (когда реактивность уже гарантирована)
- T006 — прогон quickstart-сценариев, требует завершения T004 и T005

### Parallel Opportunities

- T005 может выполняться параллельно с T002-T004 (разные файлы)
- T007 и T008 в Phase 4 могут выполняться параллельно друг с другом (разные файлы), оба после T006

---

## Parallel Example: User Story 1

```bash
# T002-T004 строго последовательны (один файл useAuth.js) — не параллелить.
# T005 можно запускать параллельно с ними (другой файл):
Task: "Проверить AuthStatusWidget.vue на необходимость правок в karaoke-public/src/components/AuthStatusWidget.vue"
```

---

## Implementation Strategy

### MVP = вся фича (единственная User Story)

1. Complete Phase 1: Setup (T001)
2. Phase 2 пропускается (N/A)
3. Complete Phase 3: User Story 1 (T002-T006) — это же весь функциональный объём фичи
4. **STOP and VALIDATE**: прогнать `quickstart.md` целиком (T006)
5. Complete Phase 4: Polish (T007-T009) перед PR

### Инкрементальность

Инкрементальная поставка по историям здесь неприменима — история одна. Внутри неё естественная инкрементальность — по задачам T002 → T003 → T004 (каждая добавляет наблюдаемое поведение поверх предыдущей: сначала безопасная обработка ошибок, потом периодическое обновление, потом немедленное обновление при заходе на страницу).

---

## Notes

- [P] tasks = разные файлы, без зависимостей друг от друга
- [US1] — все implementation-задачи фичи относятся к единственной пользовательской истории
- Commit — по логическим группам (например, после T004 — «ядро фикса»; после T006 — «проверено вручную»); конкретное разбиение коммитов и не коммитить без явного запроса пользователя (см. `CLAUDE.md`)
- Backend (`karaoke-web`) не трогаем — эндпоинт `GET /api/public/auth/me` уже живой (см. `plan.md`, `research.md`)
