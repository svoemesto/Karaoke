---
status: Active
slug: 176-authors-new-albums-badge
related:
  - ../domain/catalog.md
  - ../domain/identity.md
  - ../features/177-fix-process-count-waiting-spam.md
  - ../architecture/L3-components.md
  - ../../specs/176-authors-new-albums-badge/spec.md
---

# 176 — Бейдж «новые альбомы» в пункте меню «Авторы» (LiveDoc)

> Drill-down — [specs/176-authors-new-albums-badge/spec.md](../../specs/176-authors-new-albums-badge/spec.md).

## Что делает

В левом сайдбаре админки `webvue3` у пункта «Авторы» теперь красный бейдж с
количеством авторов, у которых `haveNewAlbum = true` — таких же, как у
«Чат» и «Задания редактора». Бейдж виден только при `count > 0`.

**Логика `haveNewAlbum`** (из `Author.haveNewAlbum` getter): `watched && (ymId != "" || vkId != "") && (lastAlbumYm != lastAlbumProcessed || lastAlbumVk != lastAlbumProcessed)`.

Бейдж обновляется автоматически в фоне (polling) каждые 20 секунд — без
перезагрузки страницы, без ручных действий. Backend endpoint
`POST /api/authors/withnewalbumcount` возвращает **только число** (int), без
DTO/картинок — дешёво даже на 18k+ авторах.

## User Stories (краткий список)

- **US1** (P1): Виджет счётчика в сайдбаре `webvue3` рядом с пунктом «Авторы».
- **US2** (P1): Визуальная консистентность с другими бейджами (тот же hex `#d02c3a`, белый текст, скруглённые углы `border-radius: 10px`, размер 18×18, шрифт 11px).
- **US3** (P2): Автообновление счётчика без перезагрузки страницы (polling 20 секунд).

## Functional Requirements (указатель)

- **FR-001**: Бейдж в `webvue3/src/App.vue`, пункт «Авторы» — `authors-nav-badge`, видим только при `count > 0`.
- **FR-002**: Визуальное соответствие `chat-nav-badge` / `songeditor-nav-badge`: `#d02c3a`, белый, 18×18, `border-radius: 10px`.
- **FR-003**: Backend endpoint `POST /api/authors/withnewalbumcount` возвращает `int` (только число).
- **FR-004**: Endpoint дешёвый — только count без лишних запросов (≤ 100 мс на 18k+).
- **FR-005**: Vuex: `authorsWithNewAlbumCount` state + `getAuthorsWithNewAlbumCount` getter + `setAuthorsWithNewAlbumCount` mutation + `loadAuthorsWithNewAlbumCount` action.
- **FR-006**: Polling 20 сек (константа `AUTHORS_NEW_ALBUMS_POLL_INTERVAL_MS = 20000`).
- **FR-007**: Cleanup `clearInterval` в `beforeUnmount` App.vue (нет утечек при HMR).
- **FR-008**: Первый вызов `loadAuthorsWithNewAlbumCount` сразу при `mounted` (без ожидания первого интервала).
- **FR-009**: Layout сайдбара без переноса строки «Авторы» при `count > 0` (`display: flex; justify-content: space-between`).
- **FR-010**: При падении запроса — предыдущее значение сохраняется (не сбрасывается в 0).

## Acceptance Criteria

- [ ] **AC1**: ≥ 1 автора с `haveNewAlbum=true` → бейдж появляется ≤ 20 сек.
- [ ] **AC2**: 0 авторов → бейдж отсутствует (DOM-элемент не рендерится).
- [ ] **AC3**: Изменение состояния автора → бейдж обновляется ≤ 20 сек без F5.
- [ ] **AC4**: `/api/authors/withnewalbumcount` отвечает ≤ 100 мс на 18k+.
- [ ] **AC5**: Визуальное оформление совпадает с бейджами «Чат» / «Задания редактора».
- [ ] **AC6**: HMR-перезагрузки не накапливают таймеры (Performance Monitor).
- [ ] **AC7**: Пункт «Авторы» остаётся в одну строку при `count > 0`.

## Связанные LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (Author как AR)
- Architecture: [L3-components.md](../architecture/L3-components.md) (контроллер + бейджи в App.vue)
- Feature: [177-fix-process-count-waiting-spam.md](../features/177-fix-process-count-waiting-spam.md) (похожий паттерн — честный SSE/poll без шума)

## Код

- Backend: `karaoke-app/src/main/kotlin/.../controller/AuthorsController.kt` — `POST /api/authors/withnewalbumcount`
- Backend: `karaoke-app/src/main/kotlin/.../model/Author.kt` — getter `haveNewAlbum` (единственный источник истины)
- Frontend: `webvue3/src/store/modules/Authors/store.js` — Vuex модуль (по образцу Chat/store.js)
- Frontend: `webvue3/src/App.vue` — бейдж + polling + cleanup
- CSS: `webvue3/src/assets/main.css` — `.authors-nav-badge` (класс с теми же стилями)

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14