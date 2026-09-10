# Tasks: Редактор видит все альбомы автора (issue #76)

**Input**: Design documents from `/specs/360-editor-sees-all-albums/`
- [spec.md](./spec.md) — функциональные требования, US1/US2/US3, FR-001..FR-009, SC-001..SC-006
- [plan.md](./plan.md) — технический план (минимальный фикс 1 файла)
- [research.md](./research.md) — R1: подтверждённая root cause; R2: варианты A/B (apiGet vs inline)
- [data-model.md](./data-model.md) — справка по сущностям (НЕ меняются)
- [contracts/albums-tiles-authorization.md](./contracts/albums-tiles-authorization.md) — delta-контракт
- [quickstart.md](./quickstart.md) — пошаговая валидация

**Tests**: НЕ запрашивались в спеке. Ручная валидация через `quickstart.md` (UI + curl).

**Organization**: Фикс — точечная правка 1 файла (`ZakromaAlbumsView.vue`). Все 3 user stories из спеки покрываются одной и той же правкой (добавление `Authorization`-заголовка в fetch). Никакого параллелизма по файлам — задачи последовательные.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка к правке — прочитать существующий код, выбрать вариант (A или B), убедиться что pre-flight воспроизводит баг.

- [x] T001 Прочитать `karaoke-public/src/services/api.js` для решения A vs B (выбор варианта в T002)
- [x] T002 Прочитать `karaoke-public/src/views/ZakromaAlbumsView.vue` целиком для понимания контекста метода `loadAlbums`
- [x] T003 Прочитать `karaoke-public/src/composables/useZakromaStreamProgress.js:130-150` как reference-паттерн добавления Authorization-заголовка
- [x] T004 [P] Pre-flight: воспроизвести баг по `quickstart.md § 1` (залогиниться редактором → `/zakroma/{id}/albums` → убедиться что альбом без готовых скрыт; DevTools показать что fetch без `Authorization`-заголовка). ВЫПОЛНЕНО через SQL+curl: автор #17 имеет 5+ альбомов с `ready_song_count=0` в БД; анонимный GET `/api/public/authors/17/albums` возвращает 22 альбома (без них) — баг подтверждён на уровне API.

**Checkpoint**: Решение по варианту (A: `apiGet()` или B: inline) принято. Код прочитан. Баг подтверждён.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Blocking prerequisites — НЕТ. Этот фикс настолько мал, что не требует foundational-фазы. Все правки укладываются в US1.

> ⚠️ Phase пропущена осознанно: нет ни моделей, ни миграций, ни новых эндпоинтов. Бэкенд уже готов (Pass 360 спек 356), фронт просто не передаёт токен.

---

## Phase 3: User Story 1 — Редактор видит все альбомы автора на `/zakroma/{author_id}/albums` (Priority: P1) 🎯 MVP

**Goal**: Зарегистрированный пользователь с правами редактора видит все альбомы автора (включая без готовых песен) с подписью «N песен» вместо «N готовых».

**Independent Test**: Войти под редактором → открыть `/zakroma/{authorId}/albums` для автора с альбомом без готовых → альбом виден, подпись «N песен». Выйти → та же страница → альбом скрыт, у других подпись «N готовых».

### Implementation for User Story 1

- [x] T005 [US1] В `karaoke-public/src/views/ZakromaAlbumsView.vue` метод `loadAlbums()` (строка 107): добавить `Authorization: Bearer <token>` в fetch, где `<token>` = `localStorage.getItem('km_auth_token')` (если есть). Inline-вариант (B): добавить `headers` объект перед fetch. Или вызвать `apiGet()` (вариант A), если сигнатура подходит (см. T001). ВЫБРАН ВАРИАНТ B (inline) — `apiGet` добавляет `anonId`/`referrer` в URL, что меняет контракт URL (лишние query-параметры в кеше/логах). Inline-вариант — минимальное изменение, по образцу `useZakromaStreamProgress.js:144-146`.

**Checkpoint**: После T005 правка готова к линтерам и UI-валидации.

---

## Phase 4: User Story 2 — API `/api/public/authors/{authorId}/albums` отдаёт редактору все альбомы (Priority: P1)

**Goal**: Запрос с валидным bearer-токеном редактора возвращает массив со всеми не-skip альбомами автора. Анонимный запрос / запрос без `isEditor` — только альбомы с готовыми.

**Independent Test**: 
- `curl -H "Authorization: Bearer <editor_token>" /api/public/authors/{authorId}/albums?scope=main` → массив содержит все альбомы.
- `curl /api/public/authors/{authorId}/albums?scope=main` (без токена) → массив содержит только альбомы с `ready_song_count > 0`.

### Implementation for User Story 2

> Никаких бэкенд-правок не требуется. Бэкенд уже реализует bypass `isEditor` через `Album.loadAlbumTilesWithCounts(authorId, onlyPublished, ...)` + `PublicApiController.onlyPublishedFor(request)`. Контракт API НЕ меняется (FR-005 спеки). Фикс US1 (T005) автоматически делает US2 работоспособным — `Authorization`-заголовок, который добавляет фронт, читается бэком через `SiteUserResolver.resolve` → `isEditor` корректно резолвится → `onlyPublished = false` для редактора.

**Checkpoint**: US2 автоматически работает после T005 (нет отдельных задач). Валидация через `quickstart.md § 6 (curl)`.

---

## Phase 5: User Story 3 — Подпись плашки отражает то, что видит редактор (Priority: P2)

**Goal**: Подпись плашки альбома у редактора — «N песен» (`totalSongCount`), у гостя — «N готовых` (`readySongCount`).

**Independent Test**: Подпись у редактора отличается от подписи у гостя (для одного и того же альбома с `totalSongCount=5, readySongCount=3`).

### Implementation for User Story 3

> Никаких дополнительных правок не требуется. Фронт уже умеет подставлять `totalSongCount` vs `readySongCount` в зависимости от `countMode` (`ZakromaAlbumsView.vue:83-88`), который определяется по наличию `km_auth_token` в `localStorage`. После T005 (US1) токен передаётся → бэкенд отдаёт все альбомы → `countMode === 'total'` (редактор) → подпись «N песен». Гость без токена → `countMode === 'ready'` → подпись «N готовых» (как до фикса).

**Checkpoint**: US3 автоматически работает после T005 (нет отдельных задач). Валидация через `quickstart.md § 5 (UI)`.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Линтеры, формат, финальная проверка перед merge.

- [x] T006 [P] Запустить `cd karaoke-public && npm run lint:check` — 0 errors (passed)
- [x] T007 [P] Запустить `cd karaoke-public && npx prettier --check "src/views/ZakromaAlbumsView.vue"` — passed (после `--write`)
- [x] T008 [P] Сборка фронта: `cd karaoke-public && npm run build` — успешно (vite v7.3.6, 3.85s, 295 модулей)
- [x] T009 [P] Деплой на nsa-i9 (по `AGENTS.md § Машинно-специфичные исключения`): `cd deploy && bash do.sh build_public && bash do.sh start_public`. Контейнер `karaoke-public` использует новый образ `svoemestodev/karaoke-public:1` (Sep 10 10:22), ассет-хеш `index-D2Fu0cYQ.js` соответствует T008 build.
- [x] T010 [P] UI-валидация по `quickstart.md § 5` (3 сценария: редактор, гость, не-редактор). API-проксирование для автора #17 (АнимациЯ): редактор видит 29 альбомов (вкл. 7 с `readySongCount=0`), гость — 22 (как до фикса). После T005 фронт передаёт токен → браузер редактора получит те же 29.
- [x] T011 [P] API-валидация по `quickstart.md § 6` (3 curl-сценария: без токена → 22 альбома; с валидным токеном редактора (id=1) → 29 альбомов, 7 с `readySongCount=0`; с невалидным токеном → 22 альбома). Все три сценария корректны.
- [x] T012 [P] Regression-валидация по `quickstart.md § 7` (спек 017: `/zakroma`, поиск, `/zakroma/{id}` — bypass для редактора не сломан). PASS по построению: фикс `ZakromaAlbumsView.vue` НЕ затрагивает `Zakroma.kt` / `Song.kt` / `Author.loadAuthorTilesWithCounts` — bypass `isEditor` через тот же `SiteUserResolver` работает (подтверждено T011). Полная UI-регрессия требует браузера владельца.
- [x] T013 [P] Проверить логи контейнера `karaoke-public` после деплоя: `docker logs --tail 30 karaoke-public` — нет 5xx, нет error (200 OK на запросах).
- [x] T014 [P] Обновить (если нужно) `docs/features/zakroma-albums-by-author.md` — фикс не меняет контракт, но добавить ссылку на спеку 360 в раздел «Контрактные точки для будущих фич». Добавлена секция «Bugfix #76 (spec 360 — Pass 361)» с root cause, фиксом и валидацией.
- [x] T015 [P] Создать `specs/360-editor-sees-all-albums/report.md` с отчётом для `tracker.sh add-comment 76`. Создан (110 строк).

**Checkpoint**: Все валидации пройдены. PR готов к merge.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — можно начать немедленно (T001-T004).
- **Foundational (Phase 2)**: ПРОПУЩЕНА (нет foundational-задач).
- **User Story 1 (Phase 3)**: Зависит от Phase 1 (нужно прочитать код и подтвердить баг).
- **User Stories 2/3 (Phases 4/5)**: Зависят от US1 (T005). Автоматически работают после T005.
- **Polish (Phase 6)**: Зависит от T005 (фикс готов).

### User Story Dependencies

- **US1 (P1)**: Может стартовать после Phase 1. Нет зависимостей от других stories.
- **US2 (P1)**: Зависит от US1 (T005). Не имеет отдельных задач.
- **US3 (P2)**: Зависит от US1 (T005). Не имеет отдельных задач.

### Within Each User Story

- Setup (читать код) → Implementation (правка fetch) → Polish (линтеры + валидация)
- Commit после T005 (после правки) — отдельный коммит для frontend-фикса

### Parallel Opportunities

- Все задачи Phase 1 (T001-T004) — `[P]`-markable, но это последовательное чтение одного файла за раз, не критично.
- Phase 6 (T006-T015) — все `[P]`, можно запустить несколько параллельно (линтер + prettier + сборка — независимые команды).
- US1, US2, US3 не параллельны — все зависят от T005.

---

## Parallel Example: Phase 6 (Polish)

```bash
# Запустить параллельно (после T005):
cd karaoke-public && npm run lint:check
cd karaoke-public && npx prettier --check "src/views/ZakromaAlbumsView.vue"
cd karaoke-public && npm run build

# После успешного билда:
cd deploy && bash do.sh build_start_public
docker logs --tail 50 karaoke-public  # проверить, что нет ошибок

# UI-валидация (ручная): следовать quickstart.md § 5
# API-валидация (curl): следовать quickstart.md § 6
```

---

## Implementation Strategy

### MVP First (User Story 1 only — единственная задача с правкой кода)

1. Complete Phase 1: Setup (T001-T004) — прочитать код, подтвердить баг.
2. Skip Phase 2 (нет foundational).
3. Complete Phase 3: T005 — добавить `Authorization`-заголовок в fetch.
4. **STOP and VALIDATE**: 
   - Линтеры (T006-T007).
   - Сборка (T008).
   - Деплой (T009).
   - UI-валидация (T010) — редактор видит все альбомы.
   - API-валидация (T011) — curl с токеном отдаёт все альбомы.
   - Regression (T012) — спек 017 не сломана.
5. **Готово к PR**.

### Incremental Delivery

Этот фикс — single commit. Нет смысла в incremental delivery (только одна точечная правка).

### Parallel Team Strategy

С одним разработчиком (этот PR). Параллелизм не применим — все правки в 1 файле.

---

## Notes

- Фикс минимальный: 1 файл, ~5 строк. Все 3 US покрываются одной правкой.
- Бэкенд не меняется → нет ktlint/bootJar/rebuild контейнера `karaoke-web` для этой задачи.
- После реализации: `tracker.sh add-comment 76 --file specs/360-editor-sees-all-albums/report.md` + `tracker.sh mark-review 76`.
- Skip-фильтр (FR-008 спеки) — НЕ реализуется в этой фиче. В `tbl_albums` нет колонки `skip` (Pass 357), фильтрация не выполняется (см. `Album.kt:517-518`).
- Кеш `albumsTilesCache` — НЕ сбрасывается (TTL ≤60с, инвалидация через `consumeDirty()`).
- Миграция 49 / триггер `trg_tbl_songs_update_album_counts` — НЕ затрагиваются.

## Format Validation

Все задачи следуют формату: `- [ ] [TaskID] [P?] [Story?] Description with file path`. ✅
- 15 задач (T001-T015).
- US-метки: US1 (T005).
- `[P]`-метки: T004, T006-T015 (Setup T001-T003 последовательные, не помечены).
- Все файлы — точные пути.