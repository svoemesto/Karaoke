# Feature Specification: Временный полный доступ к песне (завершение)

**Feature Branch**: `164-complete-guest-share-link`
**Created**: 2026-08-10
**Status**: Draft
**Input**: User description: "Задача - доделать фичу по предоставлению временной ссылки на песню. Много что ещё не работает. Надо оформить в виде спеки."

## Контекст и текущее состояние

Премиум-пользователь karaoke-public может создать временную ссылку на любую песню из своей коллекции и переслать её друзьям/знакомым. Получатель переходит по ссылке `/share/{id}/{secret}`, лендинг делает `claim` и редиректит на полноэкранный плеер — гость смотрит песню без авторизации, в полном качестве, со стемами, в течение ограниченного времени (TTL ссылки + lease playback-сессии).

**Что уже есть в коде (см. `docs/features/guest-share-link.md`, упоминается в SongShareLinkService.kt:33, PublicShareController.kt:17):**

- DDL: `deploy/karaoke-db/38_song_share_links.sql` + `39_song_share_recordhash.sql` (восстановлены в Pass 47 из git-fsck).
- Backend: `SongShareLinkService` — все методы (`createLink`, `tryClaim`, `heartbeat`, `release`, `revoke`, `listLinksForUser`, `listSessionsForLink`, `debugTryClaim`, `validateShareSession`, `findLinkIdBySecret`).
- Backend: `PublicShareController` — endpoints owner-зоны (`/{songId}/create`, `/mine/{songId}`, `/mine/{songId}/revoke`) + guest-зоны (`/claim`, `/heartbeat`, `/release`, `/debug`).
- Frontend (`karaoke-public`): `useShareLink.js` (create/getCurrent/revoke), `ShareLinkModal.vue` (UI владельца), `ShareLinkButton.vue` (кнопка в SongView), `ShareView.vue` (лендинг гостя), `songShareLink.js` (services: claimShare/heartbeat/release).
- Админка (`webvue3`): `shareLinkStore.js` (Vuex), `UserShareLinksModal.vue` (таблица ссылок и сессий).

**Что НЕ работает (gap-анализ, источники ниже):**

1. **КРИТИЧНО:** Гость после `claim` редиректится на `/player/{songId}?share=1&session={sessionTokenHash}`, но `PlayerView.vue` (`karaoke-public/src/views/PlayerView.vue:165-167`) и роутер (`karaoke-public/src/router/index.js:104-106`) ждут `sessionStorage['kp_token_${id}']` — у гостя его нет. Бэкенд (`PublicPlayerController.authorized()`, см. `karaoke-web/.../PublicPlayerController.kt:93-96`) тоже принимает только gesture token. Метод `SongShareLinkService.validateShareSession()` (`SongShareLinkService.kt:693-711`) объявлен, но нигде не вызывается. → **гость физически не может открыть плеер**.
2. **КРИТИЧНО:** `KaraokePlayer` (karaoke-public) нигде не вызывает `heartbeat()` (`songShareLink.js:66`) — lease истечёт через `props.leaseTtlSeconds` (~60 сек) и плеер сломается через минуту.
3. **КРИТИЧНО:** `KaraokePlayer` нигде не вызывает `release()` (`songShareLink.js:90`) ни на `_onEnded`, ни на `beforeunload` — `tbl_song_share_sessions` остаётся со статусом `finished_at IS NULL`, лимит 2 устройств не освобождается при честном закрытии.
4. **КРИТИЧНО:** webvue3 шлёт на `/api/siteusers/share/links`, `/api/siteusers/share/sessions`, `/api/siteusers/share/links/revoke` (`webvue3/.../shareLinkStore.js:49-86`) — **ни один из этих endpoint'ов не реализован в karaoke-web**. `UserShareLinksModal.vue` показывает «Загрузка…» и ничего. Упоминание этих путей в коде — только в комментарии `ShareErrorCode.kt:5`.
5. **ВАЖНО:** `ShareLinkSweeper`, заявленный в KDoc (`SongShareLinkService.kt:49-51`), не существует. Нет авто-отзыва при: потере премиума владельцем, появлении тега SKIP, появлении `dateTimePublish` у песни, истечении `active_session_lease_until` (нужно фиксировать `finished_at` в `tbl_song_share_sessions`).
6. **UX:** Гость попадает на лендинг `ShareView`, нажимает «Открыть плеер», но не имеет альтернативного пути (нет кнопки «Скопировать ссылку» / «Открыть в новой вкладке» — но `ShareView.vue:120-126` её и не показывает явно).
7. **UX:** В `ShareLinkModal.vue` нет автообновления статуса — если активная ссылка истекла по `expires_at` (время пришло), модалка продолжает показывать «Активна до …» без рефреша.

## Clarifications

### Session 2026-08-10

- Q: Должен ли гость по share-ссылке иметь возможность экспортировать стемы/скачивать .smkaraoke? → A: A — canExport=false. Гость смотрит плеер, но не может скачать. Премиум остаётся воронкой конверсии; поведение совпадает с существующим `isPremiumUser(request)` в PublicPlayerController.kt:431.
- Q: Должен ли heartbeat отправляться, когда плеер на паузе? → A: A — heartbeat всегда, пока вкладка открыта. Независимо от `isPlaying`. Простая реализация (`setInterval` без проверок), предсказуемый UX.
- Q: Где хранить sessionTokenHash на стороне гостя? → A: A — sessionStorage, ключ `kp_share_session_<songId>`. Изоляция по вкладке; F5 сохраняет сессию, закрытие вкладки естественно освобождает lease (через sendBeacon release + sweeper fallback).
- Q: Что делает плеер при 410 leaseExpired / 404 (revoked) во время воспроизведения? → A: A — overlay + кнопка «Закрыть», без auto-recovery. Плеер ставит на паузу, освобождает lease через `release()` с `result='revoked'`/`'timeout'`, показывает overlay с понятным текстом. Ссылка — одноразовый грант; владелец контролирует время жизни, гость не должен пытаться «оживить» отозванную ссылку.
- Q: Какие варианты TTL должны быть доступны владельцу? → A: B — 1ч / 24ч / 7д. Покрывает сценарии «послушать сейчас / до завтра / на выходных». 30 дней и «без ограничения» отложены в backlog (нужен отдельный abuse-контроль).

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Гость переходит по share-ссылке и смотрит плеер (Priority: P1)

Анонимный пользователь получает от друга ссылку вида `https://svoemesto.ru/share/12345/<секрет>`. Переходит по ней в браузере. На лендинге `/share/{id}/{secret}` видит карточку песни (обложка, название, автор, альбом, год) с кнопкой «Открыть плеер». Нажимает кнопку — попадает на полноэкранный плеер и смотрит песню со стемами в полном качестве, без авторизации.

**Why this priority**: Это центральный user-journey фичи. Без него весь функционал бесполезен — всё остальное (heartbeat, release, админка, sweeper) — поддержка этого сценария.

**Independent Test**: Можно полностью протестировать end-to-end без зависимостей: создать премиум-ссылку через UI/curl, открыть в анонимном браузере, убедиться что плеер играет. MVP готов, если этот сценарий работает.

**Acceptance Scenarios**:

1. **Given** активная share-ссылка, выданная премиум-владельцем, **When** анонимный пользователь переходит по `/share/{songId}/{secret}` в браузере без авторизации, **Then** открывается лендинг с карточкой песни (название, автор, обложка альбома/автора, год), кнопкой «Открыть плеер», и бейджем «Доступно до <дата/время>».
2. **Given** лендинг `/share/{id}/{secret}`, **When** пользователь нажимает «Открыть плеер», **Then** открывается полноэкранный плеер KaraokePlayer на той же песне, **And** пользователь слышит аудио, видит разметку текста и аккорды, может play/pause/seek, **And** НЕ имеет доступа к экспорту стемов/скачиванию `.smkaraoke`/транспонированию (`canExport=false` — см. Clarifications Q1).
3. **Given** плеер гостя по share-ссылке, **When** песня доиграна до конца, **Then** плеер показывает финальный экран «Трек завершён», **And** активная lease-сессия помечается как завершённая (`tbl_song_share_sessions.finished_at = now()`, `result = 'ended'`).
4. **Given** активный плеер гостя, **When** гость закрывает вкладку / уходит со страницы, **Then** lease-сессия помечается как завершённая с `result = 'closed'` (через `navigator.sendBeacon` на `beforeunload` / `pagehide`).

---

### User Story 2 — Lease продлевается heartbeat, истекает при неактивности (Priority: P2)

Гость смотрит плеер. Через ~25 секунд плеер автоматически отправляет heartbeat на бэкенд — lease продлевается. Если гость отошёл от плеера больше чем на `leaseTtlSeconds` (по умолчанию 90 сек) — lease истекает, активная сессия закрывается, лимит устройств освобождается. Если кто-то ещё пытается claim с тем же секретом в пределах `active=true` ссылки — получает тот же активный lease, пока он не истёк.

**Why this priority**: Без heartbeat lease истечёт через минуту и плеер сломается прямо во время просмотра — это нерабочий UX. Без корректной обработки истечения lease невозможно переиспользовать лимит 2 устройств.

**Independent Test**: Запустить claim, через 30 сек проверить `tbl_song_share_links.active_session_lease_until` — должен быть продлён. Если остановить heartbeat на >`leaseTtlSeconds` — `active_session_lease_until` истекает, `tbl_song_share_sessions.finished_at` ставится через sweeper.

**Acceptance Scenarios**:

1. **Given** активный плеер гостя по share-ссылке, **When** проходит ~25 секунд воспроизведения, **Then** плеер отправляет `POST /api/public/share/heartbeat` с `sessionTokenHash`, **And** бэкенд продлевает `active_session_lease_until` на `leaseTtlSeconds` от текущего момента.
2. **Given** активный плеер гостя, **When** пользователь ставит паузу и уходит от плеера на >`leaseTtlSeconds`, **Then** heartbeat продолжает отправляться (пока вкладка открыта), **And** lease продлевается — сессия НЕ истекает в течение разумного времени паузы. **And** если вкладка закрывается/сворачивается (browser tab unloaded) — lease истекает через `leaseTtlSeconds`, **And** следующий `POST /heartbeat` (если будет) от того же sessionTokenHash получает `410 share.leaseExpired`.
3. **Given** ссылка с истёкшим lease, **When** новое устройство делает `claim`, **Then** получает новый `sessionTokenHash` (а не старый) и может смотреть плеер.
4. **Given** гость закрыл вкладку, **When** плеер вызвал `release()` через `sendBeacon`, **Then** `tbl_song_share_sessions.finished_at` ставится, `result='closed'`, **And** `tbl_song_share_links.active_session_*` обнуляются, **And** следующий claim с того же browserHash получает свежую сессию (не возвращается старый sessionTokenHash через `existingTokenHash`-ветку).

---

### User Story 3 — Владелец управляет своими ссылками (создание, копирование, перевыпуск, отзыв) (Priority: P2)

Премиум-пользователь karaoke-public открывает страницу любой песни. Видит кнопку «Временный доступ» (рядом с ShareButton). Нажимает — открывается модалка с полями: «Срок жизни» (1 час / 24 часа), большое поле с получившейся ссылкой после создания, кнопки «Копировать», «Перевыпустить», «Отозвать». Может создать ссылку, скопировать, переслать; перевыпустить при компрометации; отозвать в любой момент.

**Why this priority**: Без UI владельца фича не запускается. Backend уже работает (PublicShareController.create/getMine/revoke), но фронт (`ShareLinkModal.vue`) местами сырой.

**Independent Test**: Залогиниться как премиум, открыть `/song?id=<id>`, нажать «Временный доступ», пройтись по сценарию создания/копирования/отзыва. Проверить запись в `tbl_song_share_links` и поведение ссылки.

**Acceptance Scenarios**:

1. **Given** премиум-пользователь на странице `/song?id=<id>`, **When** нажимает «Временный доступ» и выбирает TTL=1 час, **Then** модалка создаёт запись в `tbl_song_share_links`, **And** показывает ссылку вида `https://svoemesto.ru/share/<songId>/<secret>`, **And** автоматически копирует её в буфер обмена, **And** пишет `active=true, expires_at = now() + 1h`.
2. **Given** модалка с активной ссылкой, **When** пользователь нажимает «Перевыпустить», **Then** старая ссылка помечается `active=false, revoked_at=now(), revoke_reason='replaced'`, **And** создаётся новая `active=true` с новым секретом, **And** модалка обновляется на новую ссылку.
3. **Given** модалка с активной ссылкой, **When** пользователь нажимает «Отозвать» и подтверждает, **Then** запись помечается `active=false, revoke_reason='manual'`, **And** все активные lease-сессии завершаются (`finished_at`/`result='revoked'`), **And** попытка `claim` со старым секретом возвращает `404 share.notFound`.
4. **Given** модалка, **When** открывается на странице песни, у которой уже есть активная ссылка, **Then** модалка показывает текущую активную ссылку (включая её `expiresAt`, `sessionsTotal`, `firstUsedAt`, `lastUsedAt`), **And** если ссылка уже истекла по `expires_at` — показывает «Срок ссылки истёк, перевыпустить?» без запроса подтверждения «отзыв старой» (он не нужен — она и так мертва).

---

### User Story 4 — Админ просматривает и отзывает ссылки пользователей (Priority: P3)

Администратор karaoke (webvue3) открывает карточку site-user'а, нажимает «Временный доступ». Видит модалку с таблицей: активные и завершённые ссылки пользователя (по `tbl_song_share_links`), с полями «Песня», «Создана», «Истекает», «Открытий» (sessionsTotal), «Отказов» (rejectedConcurrent), «Причина отзыва». Может развернуть «Сессии» по ссылке — список `tbl_song_share_sessions` с «Открыто», «Завершено», «Результат», browser hash. Может принудительно отозвать любую ссылку (`revoke_reason='admin:<text>'`).

**Why this priority**: Нужно для модерации и антифрод-расследований, но это не блокер основной фичи. Без этого шага админ не сможет отозвать злоупотребленную ссылку.

**Independent Test**: Залогиниться как editor/admin в webvue3, открыть site-user, нажать «Временный доступ», проверить загрузку ссылок, открыть сессии, отозвать.

**Acceptance Scenarios**:

1. **Given** админ открывает модалку для site-user, **When** данные загружаются, **Then** таблица содержит все ссылки пользователя (активные и завершённые), **And** сортировка по `created_at DESC`, **And** лимит 50 по умолчанию, **And** query-параметр `target=local|remote` выбирает БД (LOCAL — `WORKING_DATABASE`, REMOTE — продакшен-БД через siteusers-роуты).
2. **Given** таблица ссылок, **When** админ нажимает «Сессии» по строке, **Then** под таблицей разворачивается список `tbl_song_share_sessions` для этой ссылки, **And** каждая строка показывает `opened_at`, `finished_at`, `result`, первые 12 символов `browser_hash`.
3. **Given** активная ссылка в таблице, **When** админ нажимает «Отозвать», **Then** ссылка переводится в `active=false, revoke_reason='admin:...'` (с пометкой кто отозвал, через передачу причины в query-param `reason`), **And** все активные lease завершаются.
4. **Given** модалка, **When** активных ссылок нет, **Then** показывается «Ссылок нет», **And** таб «Активные (0)» / «Завершённые (N)» обновляется.

---

### User Story 5 — Авто-отзыв ссылок фоновым sweeper'ом (Priority: P3)

Фоновая задача (Spring `@Scheduled`) раз в N секунд (настраивается) проходит по активным ссылкам и проверяет:
- владелец потерял `isEffectivePremium` → отзываем `revoke_reason='premium_lost'`;
- у песни появился тег SKIP → отзываем `revoke_reason='song_unavailable'`;
- у песни появился будущий `dateTimePublish` → отзываем (`song_unavailable`);
- истёк `active_session_lease_until` и в `tbl_song_share_sessions` есть `finished_at IS NULL` сессия для этой ссылки → ставим `finished_at`, `result='timeout'`, обнуляем `active_session_*`.

**Why this priority**: Без sweeper'а ссылки «висят» мёртвыми, лимиты не освобождаются, юзер может видеть «Активна» по факту мёртвую ссылку. Не блокер основного flow, но критично для production-ready.

**Independent Test**: Создать ссылку, у владельца снять премиум (или присвоить песне SKIP) → дождаться следующего тика sweeper'а → проверить `tbl_song_share_links.active=false` и `revoke_reason`.

**Acceptance Scenarios**:

1. **Given** ссылка активна, владелец — премиум, **When** владелец теряет премиум (бан/конец подписки), **Then** в течение `sweepIntervalSeconds` ссылка отзывается `revoke_reason='premium_lost'`, **And** все `active_session_*` обнуляются, **And** попытка `claim` возвращает `403 share.notOwner` (или `404` — не суть, главное — отказ).
2. **Given** активная ссылка на песню, у которой появился тег `SKIP` в `tbl_settings.tags`, **When** sweeper проходит, **Then** ссылка отзывается `revoke_reason='song_unavailable'`.
3. **Given** ссылка с истёкшим `active_session_lease_until` и незавершённой `tbl_song_share_sessions` строкой, **When** sweeper проходит, **Then** в `tbl_song_share_sessions` ставится `finished_at = active_session_lease_until`, `result='timeout'`, **And** `tbl_song_share_links.active_session_*` обнуляются.
4. **Given** sweeper работает, **When** нет активных ссылок под отзыв, **Then** тик завершается без SQL-запросов (или с одним `SELECT count(*)` для early-exit), **And** не пишет в лог на каждом тике.

---

### User Story 6 — Гость видит встроенный плеер на `/song?id=X` если у него есть share-сессия (Priority: P4)

Если пользователь зашёл на обычную страницу песни `/song?id=X` в браузере, где у него уже есть активная share-сессия для этой же песни (lease не истёк, sessionTokenHash сохранён), он видит встроенный плеер без необходимости уходить на лендинг и обратно.

**Why this priority**: Приятный UX-бонус. Не блокер — основной flow (User Story 1) уже работает через лендинг → плеер. Можно перенести в backlog.

**Independent Test**: Залогинироваться анонимно, сделать claim через лендинг, перейти на `/song?id=<songId>` → проверить, что встроенный плеер показывается.

**Acceptance Scenarios**:

1. **Given** в браузере есть активная share-сессия (sessionTokenHash в `sessionStorage['kp_share_session_<songId>']`), **When** пользователь переходит на `/song?id=<songId>`, **Then** `SongView`/`usePlayerAccess.checkAccess` подхватывает sessionTokenHash, **And** плеер встраивается с `canWatch=true`, `canExport=false` (гость не премиум, экспорт недоступен — см. Clarifications Q1, Q3).
2. **Given** нет активной share-сессии, **When** пользователь переходит на `/song?id=<songId>` анонимно, **Then** плеер НЕ встраивается (показывается видео ВК для песен «в эфире» или заглушка «Недоступно»), **And** поведение совпадает с поведением для анонимов без сессии.

---

### User Story 7 — UX: автообновление статуса модалки и явные ошибки (Priority: P4)

`ShareLinkModal.vue` периодически (раз в 30 сек) проверяет актуальный статус активной ссылки через `getCurrentShareLink`. Если ссылка истекла по `expires_at` — модалка сразу показывает «Срок истёк», без перезагрузки. Если `revoke_reason != ''` (например, владелец отозвал или потерял премиум) — показывает «Ссылка отозвана: <причина>».

**Why this priority**: Полировка UX, без которой модалка «врёт» о состоянии ссылки. Не блокер.

**Independent Test**: Открыть модалку с активной ссылкой, в другом месте её отозвать → через 30 сек в модалке должно появиться «Отозвана».

**Acceptance Scenarios**:

1. **Given** открытая модалка с активной ссылкой, **When** параллельно кто-то отзывает ссылку (другая вкладка/админка), **Then** в течение ≤30 сек модалка показывает «Ссылка отозвана», **And** кнопки «Копировать», «Перевыпустить», «Отозвать» скрываются, остаётся только «Закрыть» + «Перевыпустить».
2. **Given** модалка с истёкшей (по `expires_at`) ссылкой, **When** пользователь открывает модалку, **Then** она сразу показывает «Срок истёк», **And** кнопка «Перевыпустить» доступна без диалога подтверждения «отзыв старой» (старая и так мертва).
3. **Given** любая ошибка бэкенда при создании/перевыпуске/отзыве, **When** модалка получает её, **Then** показывает понятное сообщение на русском (`share.notOwner`, `share.songUnavailable`, `share.concurrentLimit`, `share.rateLimited`, `share.notFound`, `share.tokenMissing`, `share.linkAlreadyActive`, …), **And** сетит осмысленный текст вместо «Не удалось…».

---

### Edge Cases

- **Ссылка отозвана владельцем пока гость смотрит плеер.** Через ≤30 сек heartbeat получает 410 (или playerdata получает 404) — плеер показывает «Ссылка отозвана» overlay с кнопкой «Закрыть» (без auto-recovery, см. Clarifications Q4), ставит на паузу, освобождает lease через `release()` с `result='revoked'`.
- **Владелец потерял премиум.** Ссылка отзывается sweeper'ом (US5). Если в этот момент активна lease-сессия — она обрывается heartbeat'ом на 410.
- **Гость переслал секрет третьему лицу.** Один browserHash = одно устройство. Если два разных устройства откроют — первое получит lease, второе получит `409 share.concurrentLimit` (лимит 2 устройств). Тот же browserHash во второй вкладке — получит тот же sessionTokenHash (без инкремента счётчика).
- **Гость в режиме «инкогнито» / «приватная вкладка».** Каждая инкогнито-сессия = новый browserId → новый browserHash → новое устройство. Два разных инкогнито-окна одной песни = 2 устройства, лимит исчерпан.
- **Rate-limit на claim.** Не более N (настраивается, дефолт 10/мин на IP) `claim` запросов в минуту с одного IP. Превышение → `429 share.rateLimited` (см. существующий `checkRateLimit` в SongShareLinkService.kt:178-179).
- **Секрет в URL известен, но ссылка уже перевыпущена.** Старый секрет → `tbl_song_share_links WHERE token_hash=X AND active=true` ничего не возвращает → `404 share.notFound`. Гость увидит заглушку «Ссылка недоступна».
- **`expires_at` истёк, но `active=true` (забыли sweeper).** Claim возвращает `404 share.notFound` (см. условие `expires_at>now()` в `findLinkIdBySecret`/`resolveForGuest`). Ссылка отзывается sweeper'ом (US5).
- **claim при истёкшем `active_session_lease_until` для этого browserHash.** `tryClaim` идёт в ветку «создать новую сессию» (existingTokenHash-null или leaseUntil-null). Корректно.
- **Гость заходит сразу на `/player/{id}?share=1&session=X` минуя `/share/{id}/{secret}`.** Если sessionTokenHash валиден и lease не истёк — плеер работает. Если истёк/невалиден — бэкенд отдаёт 404, плеер показывает заглушку.
- **Время в `expires_at` хранится naive в МСК.** В UI выводится `expiresAtLabel` (МСК). Сравнение «истёк ли срок» на фронте — через `expiresAtMs` (реальный epoch ms).
- **`songIsShareable` блокирует выдачу ссылки/claim.** Песня не готова (нет стемов/картинок) или помечена SKIP — claim возвращает `409 share.songUnavailable`, создание ссылки — то же.

## Requirements *(mandatory)*

### Functional Requirements

#### A. Гостевой плеер (User Story 1)

- **FR-001**: Бэкенд ДОЛЖЕН принимать session token (sessionTokenHash) в `/api/public/player/{id}/access`, `/{id}/playerdata`, `/{id}/fileminus.mp3`, `/{id}/filevoice.mp3`, `/{id}/filebass.mp3`, `/{id}/filedrums.mp3`. Если токен валиден И не истёк lease И соответствует `song_id` — доступ разрешён. Если gesture token тоже присутствует и валиден — он приоритетнее (можно смотреть и как премиум с экспортом, если залогинен). **Гость по share всегда получает `canExport=false`** независимо от наличия gesture token (см. Clarifications Q1).
- **FR-002**: `PublicPlayerController.authorized()` ДОЛЖЕН сначала проверять gesture token, и ТОЛЬКО при его отсутствии/невалидности — `SongShareLinkService.validateShareSession(sessionTokenHash, songId)`. Передача — либо query-param `?session=...`, либо заголовок `X-Share-Session`.
- **FR-003**: Роутер `/player/:id` в `karaoke-public/src/router/index.js` ДОЛЖЕН разрешать переход при наличии в query/share-session'е, а не только при `sessionStorage['kp_token_${id}']`. Конкретный механизм — `beforeEnter` должен пускать если есть валидный `?session=` ИЛИ `sessionStorage['kp_share_session_${id}']` (см. Clarifications Q3).
- **FR-004**: `PlayerView.vue` ДОЛЖЕН при `mounted()` читать `route.query.session` (или `sessionStorage['kp_share_session_${id}']` при reload, см. Clarifications Q3), проверять через `validateShareSession`-эквивалент (новый `/api/public/player/{id}/access?session=...` endpoint или прямо вызов бэкенда для обмена sessionTokenHash на kp_token), **And** класть обычный `kp_token_{id}` в sessionStorage, чтобы существующая логика плеера продолжала работать без изменений в `KaraokePlayer`. SessionTokenHash после успешной валидации ДОЛЖЕН быть сохранён в `sessionStorage['kp_share_session_${id}']` для переживания F5.
- **FR-005**: Альтернативно — `PlayerView.vue` ДОЛЖЕН прокидывать sessionTokenHash в `KaraokePlayer` через новый параметр конструктора и далее в API-запросы плеера. Выбор между подходами — деталь имплементации, спека фиксирует требование «гость должен смотреть плеер».
- **FR-006**: На лендинге `/share/{id}/{secret}` (`ShareView.vue`) ДОЛЖНА быть кнопка «Скопировать ссылку» для удобства пересылки, **And** по возможности — кнопка «Открыть в Telegram/VK» (предложение share-target в мобильных браузерах).
- **FR-007**: На лендинге ДОЛЖЕН отображаться `expiresAtLabel` (МСК) — «Доступно до ДД.ММ.ГГГГ ЧЧ:ММ». Если ссылка уже истекла по `expires_at` — заглушка «Срок ссылки истёк» вместо кнопки «Открыть плеер».
- **FR-008**: `SHARE_TTL_OPTIONS` в `useShareLink.js` ДОЛЖЕН содержать три варианта: 1 час (`3600`), 24 часа (`86400`), 7 дней (`604800`) — см. Clarifications Q5. Бэкенд (`PublicShareController.create`) ДОЛЖЕН принимать все три значения; остальные — 400 `share.tokenMissing`.

#### B. Heartbeat и Release (User Story 2)

- **FR-010**: `KaraokePlayer` (`karaoke-public/src/player/KaraokePlayer.js`) ДОЛЖЕН при инициализации, если конструктор получил `shareSessionTokenHash`, запускать таймер heartbeat каждые 25 секунд (`setInterval(25000)`), **And** посылать `POST /api/public/share/heartbeat` через `services/songShareLink.js:heartbeat`. Heartbeat отправляется **независимо от play/pause**, пока вкладка открыта (см. Clarifications Q2).
- **FR-011**: При получении 410 (`share.leaseExpired`) heartbeat-таймер ДОЛЖЕН остановиться, **And** плеер показать overlay «Время сессии истекло» с кнопкой «Закрыть» (без auto-recovery, см. Clarifications Q4), **And** вызвать `release()` с `result='timeout'`, **And** поставить плеер на паузу.
- **FR-012**: На событиях `_onEnded`, `beforeunload`, `pagehide` (`visibilitychange` если hidden) `KaraokePlayer` ДОЛЖЕН вызвать `release()` через `navigator.sendBeacon('/api/public/share/release', ...)` с `result='ended'` (для `_onEnded`) или `result='closed'` (для остальных). `sendBeacon` гарантирует отправку даже при уходе со страницы.
- **FR-013**: При успешном `claim` НЕ ДОЛЖЕН возвращаться существующий sessionTokenHash, если `browserHash` отличается от `active_session_browser_hash` ссылки (см. текущий TODO в `SongShareLinkService.kt:473-475`). Если расходятся — это другое устройство, инкрементируем счётчик, проверяем лимит 2 устройств.
- **FR-014**: `KaraokeProperties` / `WebShareProperties` ДОЛЖНЫ экспонировать `heartbeatIntervalSeconds` (дефолт 25) и `leaseTtlSeconds` (дефолт 90), чтобы можно было настроить соотношение без перекомпиляции.

#### C. UI владельца (User Story 3)

- **FR-020**: `ShareLinkModal.vue` ДОЛЖЕН при открытии запрашивать текущую активную ссылку через `getCurrentShareLink(songId, token)` (`useShareLink.js`), **And** показывать её если есть, **And** сохранять `url` в `localStorage['karaoke-share-url:<linkId>']` (для восстановления после перезагрузки — секрет не хранится, но локально можно хранить сам URL).
- **FR-021**: На «Перевыпустить» модалка ДОЛЖНА показать `confirm('Перевыпустить ссылку? Старая ссылка перестанет работать.')` ТОЛЬКО если старая ссылка ещё активна (не истекла). Если истекла — сразу создавать новую без подтверждения.
- **FR-022**: На «Отозвать» модалка ДОЛЖНА показать `confirm('Отозвать ссылку? Все активные сессии будут завершены.')`. После успеха — очистить `localStorage['karaoke-share-url:<linkId>']` и закрыть модалку.
- **FR-023**: При ошибке `share.linkAlreadyActive` от бэкенда модалка ДОЛЖНА показать текст с `reason` и `limit` из JSON-ответа (например, «Превышен лимит перевыпусков: <limit>/час, попробуйте позже»).
- **FR-024**: Модалка ДОЛЖНА корректно обрабатывать и отображать `errorCode` от бэкенда — маппинг в человеческие тексты на русском (словарь в `useShareLink.js` или в самой модалке).

#### D. Админка (User Story 4)

- **FR-030**: Бэкенд ДОЛЖЕН реализовать endpoint'ы:
  - `POST /api/siteusers/share/links` — список ссылок пользователя, params: `siteUserId`, `activeOnly` (default false), `limit` (default 50), `target` (`local`|`remote`).
  - `POST /api/siteusers/share/links/revoke` — отзыв ссылки, params: `shareLinkId`, `reason` (default 'admin'), `target`.
  - `POST /api/siteusers/share/sessions` — список сессий по ссылке, params: `shareLinkId`, `target`.
- **FR-031**: Endpoint'ы `/api/siteusers/share/*` ДОЛЖНЫ быть защищены `SiteAuthInterceptor` (требуют залогиненного site-user с `isEditor=true`), **And** дополнительно проверять, что вызывающий — editor/admin (`tbl_site_users.is_editor = true` или роль «admin»).
- **FR-032**: `target=local` ДОЛЖЕН работать с `WORKING_DATABASE` (локальная БД разработчика), `target=remote` — с прод-БД через существующий `getRemoteDatabase()` (см. аналогичный паттерн в `SiteUsersController`). Если `target=remote` и remote-БД недоступна — `503 site.remote_unavailable`.
- **FR-033**: `UserShareLinksModal.vue` ДОЛЖЕН корректно отображать пустой массив (текст «Ссылок нет»), **And** `loadSiteUserShareLinks` НЕ ДОЛЖЕН крашить модалку при ошибке (уже не крашит — обрабатывает catch).
- **FR-034**: `shareLinkStore.js` уже корректно шлёт запросы — после реализации FR-030 админка заработает без изменений во фронте.

#### E. Sweeper (User Story 5)

- **FR-040**: ДОЛЖЕН быть создан `ShareLinkSweeper` (Spring `@Component`) с `@Scheduled(fixedDelay = ${web-share.sweep-interval-seconds:30}000)` (дефолт 30 сек), который:
  - проверяет активные ссылки с истёкшим `active_session_lease_until` — закрывает `tbl_song_share_sessions.finished_at`, ставит `result='timeout'`, обнуляет `tbl_song_share_links.active_session_*`;
  - проверяет ссылки, у которых владелец потерял премиум (`site_user.is_effective_premium = false` через `SiteUser.isEffectivePremium`) — отзывает `revoke_reason='premium_lost'`;
  - проверяет ссылки, у которых песня имеет тег SKIP (`tbl_settings.tags LIKE '%SKIP%'`) или будущий `dateTimePublish` (`tbl_settings.publish_date IS NOT NULL AND tbl_settings.publish_date > now()`) — отзывает `revoke_reason='song_unavailable'`.
- **FR-041**: `WebShareProperties` ДОЛЖЕН экспонировать `sweepIntervalSeconds` (дефолт 30), `leaseTtlSeconds` (дефолт 90), `heartbeatIntervalSeconds` (дефолт 25), `maxConcurrentSessions` (дефолт 2), `maxActivePerUser` (дефолт 10), `maxGenerationsPerDay` (дефолт 30), `maxReissuesPerHour` (дефолт 5). Все уже есть в `WebShareProperties`, нужно проверить наличие + добавить недостающие.
- **FR-042**: При отзыве sweeper'ом всех активных lease-сессий — обнулять `active_session_*` одной транзакцией.

#### F. UX-полировки (User Stories 6, 7)

- **FR-050**: `usePlayerAccess.checkAccess(songId)` ДОЛЖЕН принимать опциональный параметр `shareSessionTokenHash` (из `route.query.session` или `sessionStorage['kp_share_session_${songId}']`, см. Clarifications Q3), **And** передавать его в `GET /api/public/player/{songId}/access?session=...`, **And** бэкенд ДОЛЖЕН при наличии валидного session — выдавать `canWatch=true`, `canExport=false` (гость не премиум, см. Clarifications Q1).
- **FR-051**: `ShareLinkModal.vue` ДОЛЖЕН запускать таймер автообновления (`setInterval(30000)`) при открытии и опрашивать `getCurrentShareLink`, **And** останавливать таймер при закрытии (`onUnmounted`/cleanup), **And** при получении `link == null` или `revoke_reason != ''` или `expiresAt < Date.now()` — показывать обновлённый текст статуса.
- **FR-052**: В `ShareView.vue` ДОЛЖНА быть визуальная индикация состояния: «Доступно до …», «Открыть плеер» — primary button, «Скопировать ссылку» — secondary. На мобильных — `shareTarget='telegram'` для шаринга в мессенджеры (опционально, можно в backlog).

#### G. Кросс-сквозные

- **FR-060**: Все share-эндпоинты ДОЛЖНЫ быть за `WORKING_DATABASE` (прод-БД, см. комментарий в SongShareLinkService.kt:10-14). SyncRegistry НЕ расширяется. Ничего не уезжает в sync.
- **FR-061**: В `tbl_song_share_sessions` `client_ip_hash` и `user_agent_hash` ДОЛЖНЫ быть SHA-256 от `<value> + daily-rotating-salt` (GDPR-совместимо, уже заложено в DDL).
- **FR-062**: В `ShareLinkModal.vue` при отсутствии авторизации (`token == null`) кнопка «Временный доступ» ДОЛЖНА быть disabled с tooltip «Доступно только для премиум-аккаунтов» (уже есть в ShareLinkButton.vue).
- **FR-063**: В `SongView.vue` кнопка `ShareLinkButton` ДОЛЖНА показываться для премиум-пользователей И для всех остальных (disabled с tooltip для не-премиум). Не показывать только если пользователь не залогинен — но судя по коду сейчас она показывается без проверки.

### Key Entities

- **ShareLink** (`tbl_song_share_links`): долгоживущий грант. Поля: `id`, `owner_site_user_id` (FK tbl_site_users), `song_id` (bigint, без FK — не связываем с sync песен), `token_hash` (SHA-256 от секрета), `active`, `expires_at`, `created_at`, `revoked_at`, `revoke_reason`, `first_used_at`, `last_used_at`, `active_session_token_hash`, `active_session_browser_hash`, `active_session_lease_until`, `sessions_total`, `rejected_concurrent`, `last_update`, `recordhash`.
- **ShareSession** (`tbl_song_share_sessions`): короткоживущая playback-сессия. Поля: `id`, `share_link_id` (FK tbl_song_share_links), `song_id` (денормализовано для быстрого листинга), `browser_hash` (SHA-256 от browserId), `owner_site_user_id` (для админских запросов без JOIN), `anon_id`, `opened_at`, `started_at`, `last_seen_at`, `finished_at`, `result` ('ended'|'closed'|'timeout'|'revoked'|'replaced'), `client_ip_hash`, `user_agent_hash`, `last_update`, `recordhash`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% сценариев User Story 1 проходят end-to-end за <5 сек (от перехода по ссылке до старта плеера), включая claim + redirect + первый кадр.
- **SC-002**: При активном просмотре гостем плеер работает непрерывно ≥10 минут без обрыва (heartbeat продлевает lease, 30 вызовов за 10 мин, все 200 OK).
- **SC-003**: После закрытия вкладки гостем — `tbl_song_share_sessions.finished_at` ставится в течение ≤5 сек (через `sendBeacon`).
- **SC-004**: При отзыве ссылки владельцем — heartbeat гостя возвращает 410 в течение ≤30 сек (следующий heartbeat-тик), плеер показывает overlay «Ссылка отозвана» с кнопкой «Закрыть» (без auto-recovery, см. Clarifications Q4).
- **SC-005**: Админ видит список ссылок site-user'а в `UserShareLinksModal` с задержкой <2 сек (100 ссылок, target=local).
- **SC-006**: Sweeper обрабатывает 1000 активных ссылок за <5 сек (один тик). Деградация не линейная — батчами по 100 за запрос.
- **SC-007**: 0 ошибок 500 на `/api/public/share/*` в продакшен-логах после деплоя (все ошибки приходят с осмысленным `errorCode`).
- **SC-008**: 0 попыток `claim` со старым секретом после перевыпуска успешно проходят — все 404 (rate-limit не считается, только notFound).
- **SC-009**: В админке у одного пользователя может быть одновременно ≤`maxActivePerUser` (10) активных ссылок; 11-я попытка → 429 `share.linkAlreadyActive`.
- **SC-010**: Rate-limit на `/claim` срабатывает при >`N` запросов в минуту с одного IP (настраивается, дефолт 10) → 429 `share.rateLimited`.

## Assumptions

- Песня должна быть в состоянии `id_status >= 6` (или иметь готовые стемы), чтобы ссылка выдавалась — `songIsShareable()` уже проверяет это.
- Владелец должен иметь `isEffectivePremium == true` на момент создания ссылки. На момент просмотра гостем — НЕ обязательно (если ссылка ещё активна и lease не истёк).
- Ссылка создаётся в той же БД, что и читается (`WORKING_DATABASE`). SyncRegistry.share.* НЕ подключается — данные PROD-only.
- BrowserId хранится в `localStorage['km_share_browser_id']`, генерируется `crypto.randomUUID()` при первом визите. Разные инкогнито-окна = разные browserId (это документированное поведение).
- `app.public-site-url` (используется в PublicShareController.create для формирования URL) корректно настроен в проде — `https://svoemesto.ru`. Если настроен неправильно (например, `localhost`), ссылки будут невалидными.
- Default TTL ссылки: 1 час (3600 сек), 24 часа (86400 сек), 7 дней (604800 сек) — см. Clarifications Q5. Другие значения бэкенд отвергает 400 (`tokenMissing`).
- `WebShareProperties` уже содержит большинство настроек — нужно только добавить `heartbeatIntervalSeconds` (если отсутствует) и убедиться, что они читаются в sweeper/heartbeat-логике.
- `tbl_songs` (бывший `tbl_settings`) уже содержит поля `tags`, `publish_date`, `publish_time` — sweeper использует их для авто-отзыва.
- `SiteUser.isEffectivePremium` уже есть в `karaoke-app/.../SiteUser.kt` — используется в `tryClaim` через ownerIdOf, нужно убедиться что sweeper тоже может прочитать это поле.
- Использование `sendBeacon` для release — надёжно работает на закрытии вкладки в Chrome/Firefox/Safari. На iOS Safari в background-режиме есть нюансы (pagehide может не сработать), но это приемлемо для нашего use-case.

## Out of Scope (для будущих раундов)

- Шаринг в мессенджеры через Web Share API на лендинге (опционально можно добавить в US6 как P5).
- QR-код для ссылки — отдельная фича.
- Уведомление владельца о том, что гость смотрит (in-app или email).
- Лимиты по IP для владельца (защита от злоупотреблений).
- Auto-revoke по истечении N неактивных дней ссылки (не lease, а самой ссылки).
- Групповой share — одна ссылка на N песен (album-share).
- Статистика для владельца: «Сколько друзей посмотрели по моей ссылке» — в UserShareLinksModal можно, но не блокер.
