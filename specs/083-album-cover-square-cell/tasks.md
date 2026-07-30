---
description: "Task list for 083-album-cover-square-cell"
---

# Tasks: Альбомы — квадратная ячейка обложки альбома

**Input**: Design documents from `/specs/083-album-cover-square-cell/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, quickstart.md

**Tests**: OPTIONAL — в спеке не запрошены, не генерируются.
Валидация — ручная по `quickstart.md` (8 сценариев S1-S8).

**Organization**: Tasks grouped by user story (2 stories из spec.md:
P1/US1 — ячейка квадратная, P2/US2 — scope ограничен колонкой `(альбом)`).
Каждая story — независимый инкремент.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно запустить параллельно (разные файлы, без зависимостей)
- **[Story]**: к какой user story относится задача (US1, US2)
- В описании — точные пути файлов

## Path Conventions

- Admin SPA: `webvue3/src/components/`
- Артефакты спеки: `specs/083-album-cover-square-cell/`
- Single-file change: все правки в `webvue3/src/components/Albums/AlbumsTable.vue`
  (см. `plan.md` «Project Structure»).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка ветки и контекста перед правкой кода.

- [ ] T001 Создать ветку `083-album-cover-square-cell` через `./tools/reserve-branch-number.sh album-cover-square-cell` (на машине с `dev-pc`/`dev` — без согласия пользователя; на любой другой машине — по согласованию). Номер 083 уже зарезервирован на этапе `/speckit.specify` через lightweight-тег `refs/tags/seq/083` на origin.
- [ ] T002 [P] Прочитать `specs/083-album-cover-square-cell/spec.md`, `plan.md`, `research.md`, `quickstart.md` для контекста перед правкой кода.

**Checkpoint**: ветка готова, контекст прочитан, можно приступать к коду.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: отсутствует. Фича не требует backend-изменений, миграций БД,
новых Vuex-модулей, новых эндпоинтов, новых полей. Единственный файл —
`webvue3/src/components/Albums/AlbumsTable.vue`. Все правки относятся к
стилю/разметке этого файла и могут идти напрямую в US1.

---

## Phase 3: User Story 1 — Ячейка с обложкой альбома квадратная (Priority: P1) 🎯 MVP

**Goal**: В таблице «Альбомы» (`/Albums`) колонка `(альбом)` имеет ширину
54px (= высоте строки), ячейка визуально квадратная. Картинка вписана
пропорционально через `object-fit: contain`, без обрезки.

**Independent Test**: см. `quickstart.md` S1-S3. Открыть `/Albums` →
применить фильтр → ячейка `(альбом)` ≈ 54×54px в DevTools. Картинки 1:1,
4:3, 3:4 вписаны без обрезки. Плейсхолдер «Нет изображения» занимает
весь квадрат.

### Implementation for User Story 1

- [x] T003 [US1] Сделать ячейку `(альбом)` квадратной в `webvue3/src/components/Albums/AlbumsTable.vue` — три атомарных правки в одном файле:
  1. **В `<script>` `computed.albumDigestFields`**: у поля с `key: 'albumPicture'` изменить `style: { minWidth: '125px', maxWidth: '125px', textAlign: 'center', fontSize: 'small' }` → `style: { minWidth: '54px', maxWidth: '54px', textAlign: 'center', fontSize: 'small' }` (см. `AlbumsTable.vue:269-273`).
  2. **В `<style scoped>` `.fld-picture-preview`**: убрать строку `max-width: 125px;` (или заменить на `max-width: 100%`); остальные свойства (`min-width: 50px; height: 54px; display: flex; align-items: center; justify-content: center; overflow: hidden; background-color: black;`) оставить без изменений (см. `AlbumsTable.vue:716-727`).
  3. **В `<style scoped>` `.preview-image`**: заменить `width: auto; height: 50px; object-fit: contain; vertical-align: middle;` на `max-width: 50px; max-height: 50px; width: auto; height: auto; object-fit: contain; vertical-align: middle;` (см. `AlbumsTable.vue:731-736`). Это даст `object-fit: contain` bounding box 50×50 и сохранит пропорции.
  Все 3 правки — в одном файле, должны попасть в один коммит. JSDoc-блок `AlbumsTable.vue:191-203` остаётся без изменений (не добавляем новых `export default`).

**Checkpoint**: US1 готов. Ячейка `(альбом)` — квадратная 54×54px.
Картинки вписаны пропорционально. Плейсхолдер «Нет изображения»
занимает весь квадрат. Клик по preview по-прежнему открывает
`AlbumCoverModal` (логика не тронута).

---

## Phase 4: User Story 2 — Квадрат применяется только к колонке `(альбом)`, не к `(автор)` (Priority: P2)

**Goal**: Колонка `(автор)` сохраняет прежний прямоугольный вид (125×54).
Правки внесены ТОЛЬКО в `AlbumsTable.vue`, никаких изменений в
`AuthorsTable.vue`, `SongsTable.vue` или `karaoke-public`.

**Independent Test**: см. `quickstart.md` S7. В DevTools ячейка
`(автор)` ≈ 125×54px (не квадрат). `git diff --stat` показывает
правки только в `webvue3/src/components/Albums/AlbumsTable.vue`.

### Verification for User Story 2

- [x] T004 [P] [US2] Проверить через `git diff --stat` и `git diff webvue3/src/components/Albums/AlbumsTable.vue`, что:
  - Поле `authorPicture` в `albumDigestFields` (см. `AlbumsTable.vue:264-268`) **не** изменилось (`minWidth: '125px', maxWidth: '125px'` сохранены).
  - CSS-стили для `cell(authorPicture)` шаблона (см. `AlbumsTable.vue:48-62`) **не** изменились.
  - Класс `.preview-image` (общий для обеих колонок) **изменён** только в части, относящейся к размерам (`max-width: 50px; max-height: 50px; width: auto; height: auto`); `object-fit: contain` и `vertical-align: middle` сохранены. Это допустимо — `.preview-image` шарится между `cell(authorPicture)` и `cell(albumPicture)` (см. `AlbumsTable.vue:48-62` и `64-81`), изменение работает в обе стороны, но **визуально** в колонке `(автор)` с шириной 125px это незаметно: `max-width: 50px` ограничивает картинку 50px в любом случае, а в колонке `(автор)` 125×54 ячейка вмещает 50×50.
  - Никаких других файлов в `git status` нет (кроме `specs/083-album-cover-square-cell/`, если коммитим спек вместе с кодом).

**Checkpoint**: US2 готов. Scope подтверждён — изменения в одном файле,
колонка `(автор)` сохраняет прежний вид.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Сборка, линт, end-to-end ручная валидация, коммит.

- [x] T005 [P] Запустить `cd /home/nsa/Karaoke/webvue3 && npm run lint:check` — должен быть exit 0, 0 новых violations. Допускается baseline (см. `webvue3/.eslint-baseline.json`).
- [x] T006 [P] Запустить `cd /home/nsa/Karaoke/webvue3 && npm run build` — должен быть exit 0, успешный Vite production build.
- [ ] T007 Ручное end-to-end тестирование по `specs/083-album-cover-square-cell/quickstart.md` — все 8 сценариев S1-S8. Результат зафиксировать в PR-описании. Ключевые проверки: ячейка квадратная в DevTools (S1), картинки разных пропорций вписаны без обрезки (S2), плейсхолдер в квадрате (S3), клик открывает `AlbumCoverModal` без регрессии (S4), высота остальных строк не изменилась (S5), пагинация/фильтр работают (S6), колонка `(автор)` без изменений (S7), квадрат сохраняется при zoom/resize (S8).
- [x] T008 [P] Обновить запись в `docs/architecture-notes.md` (Pass 30) — краткое описание PR: «webvue3: квадратная ячейка обложки альбома в AlbumsTable (54×54, object-fit contain)», 1 файл, 0 миграций БД, 0 изменений бэкенда. Секция Pass 30 создана.
- [ ] T009 Создать коммит на ветке `083-album-cover-square-cell` по образцу `area: краткое описание` (русский, по стилю проекта; см. constitution §Рабочий процесс). Пример сообщения: `webvue3: квадратная ячейка обложки альбома (54×54, object-fit contain)`. Состав коммита — только `webvue3/src/components/Albums/AlbumsTable.vue`. Спеку `specs/083-album-cover-square-cell/` коммитить отдельным коммитом: `spec: 083-album-cover-square-cell (AlbumsTable — квадратная ячейка)`. **НЕ пушить** без явного согласия пользователя.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 → T002. Можно начать сразу.
- **Foundational (Phase 2)**: N/A — отсутствует.
- **User Story 1 (Phase 3)**: T003. Зависит от Phase 1.
- **User Story 2 (Phase 4)**: T004. Зависит от T003 (verify-only после правки).
- **Polish (Phase 5)**: T005-T009. Зависят от T003+T004.

### User Story Dependencies

- **US1 (P1)**: зависит от Phase 1. **Независима** от US2.
- **US2 (P2)**: зависит от US1 (verify-only после T003).

### Within Each Phase

- T001, T002 — последовательно, оба в контексте одной сессии.
- T003 — единственная правка кода, все 3 атомарных изменения в одном файле → один проход `Edit` (или три, но в одной сессии).
- T004 — verify-only после T003 (`git diff` показывает ожидаемый scope).
- T005, T006 — **разные команды** в одном каталоге, можно последовательно или условно-параллельно (lint перед build, чтобы поймать ошибки раньше).
- T007, T008 — независимы, можно параллельно после T005+T006.
- T009 — после всех T005-T008.

### Parallel Opportunities

```bash
# Phase 1 — T002 можно запустить параллельно с T001 (но обе требуют
# контекст shell-сессии, так что последовательно в одной сессии).

# Phase 5 — после T003+T004 можно параллельно:
Task: "T005 npm run lint:check"        # [P] — exit 0
Task: "T006 npm run build"             # [P] — exit 0

# T007 (manual) и T008 (docs) — независимы, можно параллельно
Task: "T007 ручное тестирование quickstart.md"
Task: "T008 обновить docs/architecture-notes.md"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

Минимально жизнеспособный релиз — это Phase 1 (T001+T002) + Phase 3
(T003). После этого:

1. Ячейка `(альбом)` визуально квадратная.
2. Картинки и плейсхолдер вписаны пропорционально.
3. Клик-логика сохранена (нет регрессии в `AlbumCoverModal`).
4. Колонка `(автор)` не тронута (US2 выполнен «автоматически», потому что
   T003 меняет только поле `albumPicture` и общие стили `.preview-image`/
   `.fld-picture-preview` без побочного эффекта на колонку автора).

US2 (T004) — verify-only, требуется для уверенности, что побочки нет.

### Incremental Delivery

1. Phase 1 (T001+T002) → ветка готова, контекст прочитан.
2. Phase 3 (T003) → MVP: ячейка квадратная. Дев-сервер `npm run dev` для визуальной проверки.
3. Phase 4 (T004) → scope подтверждён через `git diff`.
4. Phase 5 (T005-T009) → lint, build, ручное тестирование, документация, коммит.

### Parallel Team Strategy

С одним разработчиком — последовательно.
С 2+ разработчиками: после Phase 1:
- Dev A: Phase 3 (T003) — единственная правка кода.
- Dev B: Phase 4 (T004) — verify-only, ждёт T003.
- Все вместе: Phase 5 (T005-T009) — после T003+T004.

---

## Notes

- [P] задачи = разные файлы / разные команды, без зависимостей.
- T003, T004 — в одном файле `AlbumsTable.vue`, поэтому [P] не ставлю
  (формально можно делать параллельно разными агентами, но
  синхронизация коммитов усложнится).
- [Story] лейбл — для traceability (US1, US2 из spec.md).
- Каждая user story независимо завершаема и тестируема.
- Тесты в фиче НЕ пишутся (в спеке не запрошены, в CI их нет).
  Валидация — ручная по `quickstart.md` (8 сценариев).
- Коммитить после T003 (одна правка кода — один коммит) и после T008
  (спека — отдельный коммит). См. T009.
- **Не пушить** без явного согласия пользователя (см. constitution
  §Рабочий процесс и AGENTS.md «Git»).
- **Не коммитить** `deploy/ollama_data/`, `dist/`, `node_modules/`,
  `deploy/.env`, `deploy/do.env`.
- **На машине с hostname `dev-pc` под пользователем `dev`**: можно
  пересобирать/перезапускать локальные контейнеры и делать локальные
  миграции БД без согласия пользователя. На любой другой машине —
  только с явного согласия.
- Никаких миграций БД, никаких `ALTER TABLE`, никаких изменений
  `recordhash`-триггеров (см. `plan.md` §Constitution Check).
- Никаких новых per-feature документов в `docs/features/` (AlbumsTable
  — UI-компонент, не подсистема; в `docs/features/README.md` Albums не
  входит в 12 ключевых подсистем, FR-009 не применяется).
