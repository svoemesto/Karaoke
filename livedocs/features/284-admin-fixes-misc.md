---
status: Active
slug: 284-admin-fixes-misc
related:
  - ./277-song-name-censored.md
  - ./009-listening-history.md
  - ../domain/identity.md
  - ../architecture/L3-components.md
  - ../../specs/284-admin-fixes-misc/spec.md
  - ../../specs/284-admin-fixes-misc/plan.md
  - ../../specs/284-admin-fixes-misc/contracts/listeninghistory-pagination.md
---

# 284 — Админка: мелкие правки UI (Censored-лейбл, rows=2, починка пагинации истории) (LiveDoc)

> Drill-down — [specs/284-admin-fixes-misc/spec.md](../../specs/284-admin-fixes-misc/spec.md).

## Что делает

Три UI-фикса в админ-SPA `webvue3`:

1. **`SongEdit.vue`** — лейбл «Композиция (цензурированная):» → «Censored:» (карточка песни,
   поле цензурированного названия). `title`-тултип и `v-model` без изменений.
2. **`SongEdit.vue`** — `<textarea>` «Описание:» `rows="4"` → `rows="2"` (визуально в 2 раза ниже,
   многострочный текст продолжает работать через встроенную прокрутку).
3. **`ListeningHistoryTable.vue` + `ListeningHistory/store.js`** — **починка пагинации** истории
   прослушиваний:
   - Action `loadListeningHistoryDigest(ctx, params)` теперь прокидывает `params.page || 1` на бэк
     (параметр уже принимался `ListeningHistoryController.digest`, фронт его не передавал).
   - Watcher `currentPage(newPage, oldPage)` после commit в Vuex **диспатчит** reload
     при `newPage !== oldPage` — раньше только сохранял значение в Vuex и не перезагружал данные
     (root-cause).
   - С `<b-table>` убраны `:per-page` и `:current-page` (включали клиентскую пагинацию массива
     `digest`, который уже содержит одну серверную страницу — для страницы N>1 показывал пустоту).
   - Добавлен computed `itemsShownOnCurrentPage` для корректного «Показано X из Y» на серверной
     пагинации (на последней странице меньше `perPage`).

## Зачем

- Censored-лейбл: единый стиль с короткими английскими терминами в карточках альбомов/песен
  (`id`, `root_id`, `id_status`); экономит вертикальное место в форме.
- rows=2: уменьшает визуальный шум для песен с коротким описанием; длинные описания прокручиваются.
- Пагинация: без неё список >500 строк непригоден — админ не видит прослушивания за пределами
  первой страницы (блокирует типичные сценарии аудита).

## User Stories (краткий список)

- **US1** (P1): Censored-лейбл в карточке песни.
- **US2** (P2): textarea описания в 2 раза ниже.
- **US3** (P1): пагинация «Истории прослушиваний» работает (клик по странице → backend reload).

## Functional Requirements (указатель)

- **FR-001…FR-004** (US1, US2): см. спек 284 — текстовая правка лейбла + атрибут rows.
- **FR-005…FR-010** (US3): см. спек 284 — watcher триггерит reload, action прокидывает `page`,
  клиентская пагинация `<b-table>` отключена, footer учитывает размер текущей страницы.

## Ключевые изменения в общих компонентах

- **`ListeningHistoryTable.vue`**: добавился computed `itemsShownOnCurrentPage` (≈5 строк),
  watcher `currentPage` теперь принимает `(newPage, oldPage)` (раньше только `newPage`).
- **`ListeningHistory/store.js`**: action `loadListeningHistoryDigest` теперь принимает
  `params.page` и мержит с `target` при формировании form-urlencoded body.

## Acceptance Criteria

- [ ] **AC1** (Сценарий 1 quickstart): в карточке песни лейбл — «Censored:», `title`-тултип сохранён.
- [ ] **AC2** (Сценарий 2): `<textarea>` описания имеет `rows="2"` (DevTools), ввод длинного текста работает.
- [ ] **AC3** (Сценарий 3): клик по странице «2» → ровно 1 `POST /api/listeninghistory/digest`
      с `page=2`, таблица обновляется строками страницы 2.
- [ ] **AC4** (Сценарий 7): mount на новой странице → ровно 1 POST с `page=1` (без дребезга от watcher).
- [ ] **AC5** (Сценарий 9): регрессия — остальные поля формы `SongEdit.vue` не сломаны
      (`Композиция:`, `Исполнитель:`, `Год:`, `Альбом:`, undo/copy/paste работают).
- [ ] **AC6** (Сценарий 10): регрессия — соседние таблицы админки (`/songs`, `/albums`,
      `/authors`, `/news`, `/subscriptions`) работают как раньше.

## Связанные спеки

- [specs/277-song-name-censored](../../specs/277-song-name-censored/spec.md) —
  почему поле `songNameCensored` существует и какое у него поведение в публикациях.
- [specs/009-listening-history](../../specs/009-listening-history/spec.md) —
  публичная «История» в karaoke-public; административная — на её основе.
- [specs/284/contracts/listeninghistory-pagination](../../specs/284-admin-fixes-misc/contracts/listeninghistory-pagination.md) —
  контракт параметра `page`/`pageSize` на бэке (бэк уже принимал — только прокинули с фронта).

## Архитектурные заметки

- Бэкенд `ListeningHistoryController.kt` НЕ менялся — параметр `page`/`pageSize` уже принимался,
  clamp работал корректно (`safePage = max(1, page)`, `safePageSize.coerceIn(1, 1000)`,
  `offset = (safePage - 1) * safePageSize`). Все правки — только фронт.
- vuex-стейт `listeningHistoryTableCurrentPage` сохраняется **в пределах текущей SPA-сессии**
  (in-memory). F5 cross-session персистенция через `setWebvueProp`/`getWebvueProp` —
  **не включена** в этот тикет (см. `plan.md` секция Polish, FR-009 A-006).
- Никаких изменений в БД, миграциях, sync-флагах, публичном API.
