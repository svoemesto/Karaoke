# Research: Админ-таблицы «Подписки», «История прослушиваний», «Временные ссылки»

**Feature**: 171-admin-subscriptions-history
**Date**: 2026-08-11
**Status**: Phase 0 complete (all NEEDS CLARIFICATION resolved)

## Контекст

Все три таблицы уже существуют и активно используются (`tbl_subscriptions`, `tbl_listening_history`, `tbl_song_share_links`).
Backend-модели (`Subscription`, `ListeningHistory`, `SongShareLinkService`) уже реализованы.
В админке уже есть `UserSubscriptionsModal` (per-user), `shareLinkStore` (per-user), `UserEventsModal` (events).
**Нового в фиче**: три **глобальных** админ-таблицы с фильтрами + один новый action («Отозвать» для share) поверх существующего API.

## Research Questions (Phase 0)

### RQ-1. Какой контроллер делать для каждой таблицы?

**Decision**: 3 отдельных контроллера, каждый со своим `digest`-эндпоинтом.
- `SubscriptionsController` — `/api/subscriptions/digest`
- `ListeningHistoryController` — `/api/listeninghistory/digest`
- `ShareLinksAdminController` — `/api/sharelinks/digest`

**Rationale**:
- Контроллеры уже сгруппированы по доменам (`SiteUsersController`, `SitePlaylistsController`, `SiteShareLinksController`, `PublicShareController`, `PublicHistoryController`). Новый фиче — новый контроллер = чистая ответственность.
- Расширение существующего `SiteShareLinksController` (на `/api/siteusers/share/...`) сломает обратную совместимость per-user эндпоинтов + семантически «share-links пользователя» ≠ «все share-ссылки».
- Альтернатива: добавить в существующий `ApiController.kt` (~3400 строк, разросшийся god-файл). Отклонено — god-file, плохая навигация, ломает существующих вызывающих.

**Alternatives considered**:
- ❌ Расширить существующий `SiteShareLinksController` параметром `?global=true` — смешивает per-user и global use case, трудно поддерживать.
- ❌ Один «MetaController» со всеми digest — против паттерна «один контроллер = одна сущность».
- ✅ Отдельные контроллеры (выбрано) — соответствует паттерну `SitePlaylistsController`, `SiteUsersController`.

### RQ-2. Куда положить контроллеры — в `karaoke-app` или `karaoke-web`?

**Decision**: Все 3 контроллера — в `karaoke-web` (`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/`).

**Rationale**:
- Существующие админ-эндпоинты для похожих read-only таблиц живут в `karaoke-web`:
  - `SiteUsersController.kt` (`/api/siteusers/...`) — в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/` (!)
  - `SitePlaylistsController.kt` — в `karaoke-app/.../controllers/` (!)
  - `SiteShareLinksController.kt` — в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/`
  - `PublicApiController.kt` — в `karaoke-web/.../controllers/`
- ВОПРОС ГДЕ ЖИВЁТ: после проверки grep выяснилось, что `SiteUsersController` и `SitePlaylistsController` — **в `karaoke-app`**, а не в `karaoke-web`.
- Однако модели (`Subscription`, `ListeningHistory`) живут в `karaoke-app/model/`. `karaoke-web` уже зависит от `karaoke-app` через `implementation(project(":karaoke-app"))`.
- **ПОПРАВКА к Decision**: после уточнения — разместить в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/`, как и `SiteUsersController`, `SitePlaylistsController`. Это сохранит консистентность и не сломает существующий convention.

**Final Decision**: 3 новых контроллера в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/`:
- `SubscriptionsController.kt` — `/api/subscriptions/digest`
- `ListeningHistoryController.kt` — `/api/listeninghistory/digest`
- `ShareLinksAdminController.kt` — `/api/sharelinks/digest`

**Alternatives considered**:
- ❌ В `karaoke-web` — нарушил бы паттерн (нет ни одного «admin digest» контроллера в `karaoke-web`, все в `karaoke-app`).
- ✅ В `karaoke-app` (выбрано) — соответствует паттерну.

### RQ-3. SQL-фильтрация — `KaraokeDbTable.loadList` или сырой JDBC?

**Decision**: Использовать `KaraokeDbTable.loadList` с динамическим `whereList`.

**Rationale**:
- Это **конституция II NON-NEGOTIABLE**: «Доступ к БД — только через сырой JDBC (`KaraokeConnection`, `Connection.local()/remote()/virtual()`). Никакого JPA/Hibernate/Exposed».
- `KaraokeDbTable.loadList` уже принимает `whereList: List<String>` — это «сырой JDBC под капотом», и так делает `Subscription.loadByUser` (см. код `karaoke-app/.../Subscription.kt:148`).
- Для JOIN-ов (обогащение user/song/tariff) — добавить отдельный helper-метод или сырой запрос через `KaraokeConnection.getConnection()` (как в `ListeningHistory.getForUser`, `SongShareLinkService.getShareLinksForUser`).
- Альтернативы (JPA, JOOQ, Exposed) — ЗАПРЕЩЕНЫ.

**Alternatives considered**:
- ❌ JPA/Hibernate — запрещено конституцией II.
- ❌ Отдельная ORM-библиотека — запрещено.
- ✅ `KaraokeDbTable.loadList` + сырой `getConnection()` для JOIN (выбрано).

### RQ-4. Какой паттерн пагинации на бэкенде?

**Decision**: page + pageSize, OFFSET-LIMIT в SQL.

**Rationale**:
- Существующий `SiteUsersController` и `SitePlaylistsController` принимают параметры `page`/`pageSize` и возвращают `{items: [...], totalCount: N}`.
- Стандартный паттерн Spring Data. Offset-LIMIT допустим для наших объёмов (10k-50k записей, не миллионы).
- Альтернатива cursor-based — over-engineering для админки (нет realtime-scroll, нет deep-paging).

**Alternatives considered**:
- ❌ Cursor-based — over-engineering.
- ✅ Offset-LIMIT с totalCount (выбрано).

### RQ-5. Какой паттерн фильтров на фронтенде?

**Decision**: Отдельный `<Entity>FilterModal.vue` (как в `SiteUsersFilterModal`).

**Rationale**:
- Существующая таблица `SiteUsersTable` использует `SiteUsersFilterModal` — паттерн модального окна с фильтрами.
- `SitePlaylistsTable` НЕ имеет фильтра (только `filterOwnerId`) — это потому что плейлистов мало. Для подписок/истории/share фильтры нужны, так как данных много.
- `PromotionsTable`, `TariffsTable`, `NewsTable` имеют аналогичные модалки (можно подсмотреть).

**Alternatives considered**:
- ❌ Inline-фильтры в toolbar (без модалки) — перегрузят toolbar для 5+ фильтров.
- ✅ Модалка (выбрано) — соответствует `SiteUsersFilterModal`.

### RQ-6. Как переиспользовать существующий `revokeSiteUserShareLink`?

**Decision**: Переиспользовать 100% — НЕ создавать новый action.

**Rationale**:
- `webvue3/src/components/SiteUsers/shareLinkStore.js:64` уже содержит `revokeSiteUserShareLink({ shareLinkId, reason, target })`, который вызывает `POST /api/siteusers/share/links/revoke`.
- Эндпоинт уже работает с любым `shareLinkId` (не привязан к `siteUserId`).
- Переиспользование: в новой таблице `ShareLinksTable.vue` метод `revokeLink(id)` просто диспатчит `revokeSiteUserShareLink({ shareLinkId: id, reason: 'admin', target: currentTarget })`.
- Никаких изменений на бэкенде не нужно для action «Отозвать».

**Alternatives considered**:
- ❌ Создать новый action `revokeShareLink` в `shareLinksStore.js` — дублирование.
- ✅ Переиспользовать `revokeSiteUserShareLink` (выбрано).

### RQ-7. Где взять список всех share-ссылок на бэкенде?

**Decision**: Новый эндпоинт `POST /api/sharelinks/digest` (через `KaraokeConnection.getConnection()` + сырой SQL).

**Rationale**:
- Существующий `POST /api/siteusers/share/links` (`SongShareLinkService.getShareLinksForUser`) — возвращает ссылки **одного** пользователя (`WHERE owner_site_user_id = ?`).
- Для глобальной админ-таблицы нужен список **всех** ссылок с фильтрами (owner, song, activeOnly, date range).
- Расширять существующий эндпоинт параметром `?allUsers=true` — сломает его текущую семантику (per-user) и контракт.
- Новый эндпоинт чище: `POST /api/sharelinks/digest` с фильтрами.

**Alternatives considered**:
- ❌ Параметр `allUsers=true` в существующем — смешение use case.
- ❌ Передать `ownerSiteUserId=0` как «all» — неочевидно, не документируется.
- ✅ Новый `/api/sharelinks/digest` (выбрано).

### RQ-8. Должен ли быть SSE-канал для live-обновления таблиц?

**Decision**: НЕТ. Только ручной F5 (как в `SitePlaylists`).

**Rationale**:
- Существующие read-only админ-таблицы (`SitePlaylists`) НЕ обновляются через SSE.
- `tbl_subscriptions`, `tbl_listening_history`, `tbl_song_share_links` не в списке таблиц для SSE в `App.vue:275-279` (там только `tbl_songs`, `tbl_processes`).
- Добавление SSE — отдельная фича (Out of Scope).

**Alternatives considered**:
- ❌ Добавить SSE — Out of Scope, отдельная фича.
- ✅ Ручной F5 (выбрано).

### RQ-9. Какой размер страницы выбрать?

**Decision**:
- Подписки: 25 строк/стр.
- История: 500 строк/стр.
- Share-ссылки: 25 строк/стр.

**Rationale**:
- Подписки — «жирные» (денежные, длинные текстовые поля — `tariffName`, `songName`, `promoApplied`); 25 — комфортная плотность.
- История — «тонкая» (одна строка = одна пара user×song, без денег); 500 даёт хороший overview для аналитики.
- Share — аналогично подпискам (25).

**Alternatives considered**:
- � Единый размер 25 для всех — неудобно для истории.
- ❌ Единый размер 100 для всех — перегруз для подписок.
- ✅ 25/500/25 (выбрано).

### RQ-10. Что делать с уже существующим `UserSubscriptionsModal.vue`?

**Decision**: НЕ трогать, оставить как есть. Новая таблица `SubscriptionsTable.vue` использует **отдельный** `SubscriptionsTable.vue` со своим набором колонок и фильтров.

**Rationale**:
- `UserSubscriptionsModal` — это компактный обзор подписок одного пользователя (в карточке). 8 колонок, нет фильтров, нет пагинации.
- `SubscriptionsTable` — глобальный обзор всех подписок. Больше фильтров, пагинация, drill-down.
- У них разный UX и разные потребности — нет смысла унифицировать.

**Alternatives considered**:
- ❌ Сделать общий компонент с пропсами — over-engineering, разные UX.
- ✅ Отдельные компоненты (выбрано).

### RQ-11. Должны ли мы создать per-feature документ в `docs/features/`?

**Decision**: НЕТ (для подписок и истории). **ДА** — обновить `docs/features/guest-share-link.md` (для share-ссылок).

**Rationale**:
- Согласно FR-009 constitution + AGENTS.md: per-feature документ нужен для 9 ключевых подсистем из `docs/features/README.md`. В списке нет «subscriptions», «listening-history» (есть только публичная фича в specs/009/, но это другая сущность).
- `guest-share-link.md` уже есть — это ровно про `tbl_song_share_links`. Нужно добавить секцию «Админ-таблица /sharelinks» с ссылкой на новый компонент.
- Per-feature для subscriptions и listening history не требуется (фича слишком узкая, нет отдельной доменной логики).

**Alternatives considered**:
- ❌ Создать `docs/features/admin-subscriptions.md` etc. — over-engineering.
- ✅ Обновить `guest-share-link.md` (выбрано для share).
- ✅ Ничего не создавать для subscriptions/history (выбрано).

## Решённые NEEDS CLARIFICATION

Никаких `NEEDS CLARIFICATION` в спеке не было (см. Phase 0 — спека уже полная). Все 11 research questions выше — это вопросы реализации, не функциональной спеки.

## Открытые вопросы для `/speckit.plan` (если возникнут)

Все технические развилки разрешены через `Decision`/`Rationale` выше. Если в Phase 1 (data-model.md, contracts/) обнаружатся новые развилки — будут добавлены в этот файл как дополнительные `RQ-N+1` секции.

## Ссылки на источники

- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Subscription.kt` — модель подписок.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/ListeningHistory.kt` — модель истории.
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt` — сервис share.
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/ShareLinkSweeper.kt` — фоновый sweeper.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SiteUsersController.kt` — образец admin controller.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SitePlaylistsController.kt` — образец read-only controller.
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/SiteShareLinksController.kt` — образец share controller.
- `webvue3/src/components/SiteUsers/SiteUsersTable.vue` + `SiteUsersFilterModal.vue` + `store.js` — образец админ-таблицы с фильтрами.
- `webvue3/src/components/SitePlaylists/SitePlaylistsTable.vue` + `store.js` — образец простой админ-таблицы.
- `webvue3/src/components/SiteUsers/UserSubscriptionsModal.vue` — существующий модал per-user подписок (НЕ трогаем).
- `webvue3/src/components/SiteUsers/shareLinkStore.js` — существующий store для share (переиспользуем `revokeSiteUserShareLink`).
- `webvue3/src/router/index.js` — добавляем 3 роута.
- `webvue3/src/App.vue` — добавляем 3 пункта меню.
- `docs/features/guest-share-link.md` — обновляем с упоминанием админ-таблицы.
