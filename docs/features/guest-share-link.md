# Временный полный доступ к песне (guest share link)

> **Status**: active
> **Feature Key**: guest-share-link
> **Last Updated**: 2026-08-11 (Pass 51 — админ-таблица `/sharelinks`: новый эндпоинт `POST /api/sharelinks/digest` + UI в webvue3 для глобального обзора всех share-ссылок с фильтрами и действием «Отозвать»; спека `171-admin-subscriptions-history`)

## Что делает

Позволяет премиум-пользователю karaoke-public создать **временную ссылку** на
одну песню и переслать её кому угодно. Получатель открывает ссылку без
авторизации и получает полный режим онлайн-плеера на срок жизни ссылки
(1 час, 24 часа или 7 дней). Доступен одновременно максимум на двух устройствах.

## Зачем

Премиум-пользователь хочет поделиться понравившейся песней с друзьями или
коллегами, не передавая аккаунт и не заставляя регистрироваться. Сейчас
механизма нет: 30-минутный токен `PlayerGestureUnlockService`
привязан к жесту разблокировки и in-memory; ни переслать URL, ни пережить
рестарт karaoke-web он не может. Эта фича добавляет:

- персистентный грант в PostgreSQL (переживает рестарт karaoke-web);
- лимит до 2 одновременных playback-устройств (владелец сам разрешил);
- автоотзыв при потере владельцем премиума или снятии песни;
- аудит-трейл сессий для админа.

## Как работает

**Два уровня**:

1. **Грант** (`tbl_song_share_links`) — долгоживущая запись. Премиум создаёт
   через `POST /api/public/share/{songId}/create` с TTL 1h / 24h / 7д. В БД
   хранится `SHA-256(секрет)`, исходный секрет (32 байта SecureRandom →
   base64url) возвращается только в ответе на создание. На одну песню у
   пользователя — максимум один активный грант (уникальный индекс
   `(owner, song) WHERE active`). Перевыпуск отзывает старый с
   `revoke_reason='replaced'`.

2. **Playback-сессия** (`tbl_song_share_sessions`) — создаётся анонимным
   гостем через `POST /api/public/share/claim` (атомарный UPDATE lease
   через `WHERE active_session_lease_until IS NULL OR < now() OR
   browser_hash=...` + `< 2 активных сессий`). Клиент сохраняет
   `sessionTokenHash` и передаёт его в `X-Share-Session` для каждого
   запроса `/playerdata`, `/fileminus.mp3` и т.п. Heartbeat каждые 25 сек
   продлевает `active_session_lease_until = now() + 90s`.

**Устройство** = стабильный `browserHash` (random UUID в `localStorage`).
SHA-256(`browser:<uuid>`). Разные вкладки одного браузера — одно устройство.
`< 2` активных сессий с разными `browser_hash` = параллельный доступ
двум людям. Третий получает `share.concurrentLimit`.

**Окончание**:

- Гость отправляет `release` через `navigator.sendBeacon` на
  `beforeunload` (best-effort) или явно на `_onEnded` (`result='ended'`).
- Фоновый `ShareLinkSweeper @Scheduled(60s)` завершает сессии с
  `last_seen_at < now() - gracePause(120s)` как `result='timeout'`.
- На отзыв ссылки (владельцем или админом) — sweeper переводит её в
  `active=false`, на ближайшем heartbeat клиент получает 410 → аудио
  останавливается.

**Доступ гостя** (см. `PublicPlayerController.access` / `playerData`):

| Поле | Гость | Премиум | Аноним (нет ссылки) |
|---|---|---|---|
| `canWatch` (полный трек) | true | true | false |
| `canExport` | **false** | true | false |
| `canTranspose` | true | true | false |

Транспонирование и скорость разрешены гостю (полный режим), экспорт
стемов — нет (см. design D6). Гость НЕ получает прав аккаунта владельца:
избранное, чат, плейлисты, доступ к другим песням.

**Автоотзыв**:

- Потеря владельцем `isEffectivePremium` (бан / истечение `sitePremiumUntil`
  / `sponsrPremiumUntil`) — sweeper отзывает все его активные ссылки с
  `revoke_reason='premium_lost'`.
- Песня с тегом `SKIP` или `date+time > now()` (будущий эфир) — sweeper
  отзывает `song_unavailable`.
- Лимит одновременных устройств на ссылку: ≤2 (задаётся в
  `KaraokeProperties.share_maxConcurrentSessions`).

## Трактовка дат (FR-011, FR-013)

**Источник правды — naive timestamp в МСК** в `tbl_song_share_links.*_at`
(`expires_at`, `created_at`, `revoked_at`, `first_used_at`, `last_used_at`,
`active_session_lease_until`) и `tbl_song_share_sessions.*_at` (`opened_at`,
`started_at`, `last_seen_at`, `finished_at`). DDL не меняется: всё остаётся
`timestamp without time zone` в МСК. Миграция `38_song_share_links.sql` и
`recordhash`-триггер `39_song_share_recordhash.sql` — не трогаем.

**Сервер читает через `EXTRACT(EPOCH FROM ts AT TIME ZONE 'Europe/Moscow')*1000`**
— это алгоритмический перевод, не зависит от TZ сессии Postgres. Получаем
единственное числовое поле = реальный момент времени (epoch ms). На сервере
используется явный `ZoneId.of("Europe/Moscow")` (а не `ZoneId.systemDefault()`),
поэтому запись через `setObject(..., LocalDateTime, Types.TIMESTAMP)` даёт
стабильную МСК-запись даже на машинах с JVM TZ ≠ Europe/Moscow (FR-014).

**Во фронт уходит одно числовое поле `expiresAt: Long`** (а не пара
`expiresAtMs` + `expiresAtLabel`). Никаких сдвигов −3ч / +3ч в JS-коде.
Сравнение с `Date.now()` (`isExpired`) и форматирование в метку
«Доступно до ДД.ММ.ГГГГ ЧЧ:ММ» (`formatDate`) делаются фронтом по
**одному и тому же** epoch ms.

**Отображение — в TZ устройства.** `karaoke-public/src/utils/dateFormat.js`
и `webvue3/src/utils/dateFormat.js` (по сути одна функция, скопированная
в оба SPA — общего пакета пока нет):

```js
new Date(epochMs).toLocaleString('ru-RU', {
  day: '2-digit', month: '2-digit', year: 'numeric',
  hour: '2-digit', minute: '2-digit',
})
```

Без указания `timeZone` — V8 берёт TZ устройства. Гость во Владивостоке
видит «11.08.2026 16:57», владелец в Москве — «11.08.2026 09:57» для
одной и той же записи `expires_at = '2026-08-11 09:57:36'`.

**Пустая дата → прочерк.** Админ-таблица показывает «—» для `revokedAt`,
`firstUsedAt`, `finishedAt` (если значение null/0). Реализовано через
`formatDate` (возвращает `''`) + тернарный оператор в `UserShareLinksModal.vue`.

**Тесты:** `karaoke-web/src/test/.../SongShareLinkDateTimeTest.kt`
(JUnit 5), `karaoke-public/src/utils/__tests__/dateFormat.test.js`
(`node --test`), `webvue3/src/utils/__tests__/dateFormat.test.js`
(`node --test`). Покрыты golden-числа (`1786431456000` →
`11.08.2026 09:57` в МСК / `16:57` во Владивостоке) и инвариантность
от TZ JVM (`-DTZ=Asia/Vladivostok ./gradlew :karaoke-web:test`).

## Инварианты / правила

1. Срок жизни ссылки — 1 час, 24 часа или 7 дней (радио в модалке, см. Clarifications Q5).
   По умолчанию 1 час. Бэкенд отвергает любой другой TTL 400 `share.tokenMissing`.
2. ≤2 одновременных playback-устройств на ссылку; ≤5 живых ссылок на пользователя,
   ≤30 генераций/сутки, ≤3 перевыпуска одной песни в час
   (см. `WebShareProperties` в `karaoke-web`).
3. Heartbeat каждые 25 сек, lease 90 сек, grace-pause 120 сек → автоtimeout.
   Release отправляется через `navigator.sendBeacon` на `beforeunload` /
   `pagehide` (результат доходит даже при уходе со страницы).
4. Секрет ссылки — 32 байта SecureRandom (base64url), в БД только SHA-256.
   Исходный секрет отдаётся ровно один раз при создании.
5. Унифицированный `404 share.notFound` для **доменных** негативных кейсов
   (несуществующий / отозванный / просроченный / SKIP-песня / неизвестный
   browserHash). **Системные** ошибки (БД недоступна, relation does not exist,
   NPE в SQL-обёртке) → `500 share.internal` (см. L10 и [Pass 50](#pass-50-fix-share-claim-500)).
6. Rate-limit claim — 10 запросов в минуту с одного IP (`share.rateLimited`).
7. Гость видит `canExport=false` независимо от реального premium-статуса.
8. Транспонирование разрешено гостю (отдельный флаг `canTranspose`).
9. После `ENDED` — экран «Песня завершена» с кнопками «На страницу песни»,
   «На главную», «Повторить», через 8 сек автопереход на `/song/{id}`.
10. История (список ссылок + сессии) видна только админу через webvue3.
    Сам владелец ссылки свою историю НЕ видит.

### Диагностика 500-ошибок claim (FR-020, FR-030, Pass 50)

Если гость жалуется на «ссылка не работает / 500», диагностика в 4 шага:

1. **Проверить таблицы на проде**: `ssh root@${PROD_HOST:-188.119.64.111} 'docker exec karaoke-db psql -U postgres -d karaoke -c "\\dt tbl_song_share*"'`. Должно быть 2 строки (`tbl_song_share_links`, `tbl_song_share_sessions`). Если нет → применить миграции `38_song_share_links.sql` + `39_song_share_recordhash.sql` (см. их header — идемпотентны).
2. **Проверить функции**: `\\df update_tbl_song_share*` → 2 функции. **Триггеры**: `SELECT tgname FROM pg_trigger WHERE tgname LIKE '%song_share%'` → 4 триггера. Если меньше — применить `39_*.sql`.
3. **Если таблицы и триггеры есть** — диагностировать через `/debug`: `curl -X POST https://sm-karaoke.ru/api/public/share/debug -H 'Content-Type: application/json' -d '{"secret":"<полный-секрет>"}'`. JSON покажет пошагово результаты каждого шага (`step1_resolve`, `step2_ownerId`, `step3_songId`, `step4_songIsShareable`) с реальным классом исключения на упавшем шаге.
4. **В логах karaoke-web** искать строки:
   - `[tryClaim] UNEXPECTED class=<FQN> msg=...` — системная ошибка, попадает в `share.internal`
   - `[tryClaim] ShareException class=... msg=...` — доменная ошибка
   - `ShareLink tryClaim UNEXPECTED class=<FQN>` — то же, на уровне slf4j
   - `ShareException` — любая ShareException подтип (NotFound, ConcurrentLimit, и т.п.)

Подробнее — `specs/167-fix-share-claim-500/quickstart.md` (7 manual scenarios).

## Известные ловушки

- [L1] **Не DRM.** Гость может сохранить MP3 через DevTools, записать
  экран или продолжить слушать уже загруженный буфер после отзыва. Lease
  защищает от обычной массовой раздачи, но не от записи. Документировано
  в спеке `guest-share-link.spec.md`.
- [L2] **Идентификация устройства по `localStorage`.** Очистка localStorage
  = «новое устройство». Владелец явно разрешил 2 устройства, поэтому
  инкогнито-окно + основной браузер — нормальный сценарий.
- [L3] **Секрет в URL.** При автоматическом GET страницы `/share/{id}/{secret}`
  роботами/антивирусами nginx пишет URL в access.log. Логи НЕ должны
  содержать исходный секрет — обход через фрагмент `#secret` отклонён (см.
  design D5), поэтому ограничиваемся тем, что nginx access.log за
  пределами scope проекта; в логи самого приложения секрет не пишется.
- [L4] **`tbl_events.referer`** потенциально содержит URL — нужно маскировать
  секрет через `ShareSecretMask.mask`. В этой фиче маскирование не
  подключено напрямую, но helper добавлен (см. `ShareErrorCode.kt` —
  `ShareSecretMask`).
- [L5] **Совместимость с уже существующим `kp_token_*` жестом.** Маршрут
  `/player/:id` обновлён: `?share=1&session=...` пропускает guard, иначе —
  прежняя проверка `sessionStorage.getItem('kp_token_{id}')`. Старый flow
  не задет.
- [L6] **`canTranspose` отделено от `canExport`.** Раньше эти флаги были
  синонимами (transpose привязан к canExport). Теперь это разные поля —
  гость может транспонировать, но не экспортировать. Если бэкенд не
  передаёт `canTranspose` — fallback на `canExport` для обратной совместимости.
- [L7] **`< 2 active sessions` race-condition.** Атомарный UPDATE lease
  решает на уровне SQL (см. design D3): один round-trip, без гонки. Если
  нагрузка вырастет — добавить индекс `(share_link_id, finished_at) WHERE
  finished_at IS NULL` (уже есть в миграции `38_song_share_links.sql`).
- [L8] **Кеш плагиат.** IP/UA гостя попадают в `tbl_song_share_sessions` в
  виде хэшей (`SHA-256(ip+share-salt)`, GDPR-совместимо). Админ видит
  только хэши, исходные IP/UA НЕ возвращаются (см. спеку
  `guest-share-link-admin.spec.md`).
- [L9] **«−3 часа» в датах (Pass 47, исправлено в Pass 49).** В БД
  `expires_at` лежит как naive МСК, но `EXTRACT(EPOCH FROM naive_ts)`
  трактует naive как UTC. Старая логика отдавала фронту 2 поля: `expiresAt`
  (сдвинутый) + `expiresAtMs` (реальный) + `expiresAtLabel` (МСК-строка от
  бэка). Корень проблемы — два числа с одинаковым именем, разным смыслом.
  Исправлено: единственное числовое поле = реальный момент (FR-013),
  форматирование в TZ устройства на фронте (FR-011). Подробнее —
  `specs/166-fix-share-link-timezone/`.
- [L10] **`share.notFound` маскирует системные ошибки (Pass 50).** До
  `spec 167-fix-share-claim-500` все 3 catch-all в `PublicShareController`
  (`/claim`, `/create`, `/heartbeat`) ловили `Exception` и отдавали
  500 `share.notFound` или 410 `share.leaseExpired` — невозможно было
  отличить «ссылка битая» от «у нас БД упала». Сейчас catch-all в этих
  3 эндпоинтах ловит только `SongShareLinkService.InternalError` → 500
  `share.internal`. 4 остальных эндпоинта (`/release`, `/mine/{songId}`,
  `/mine/{songId}/revoke`, `/debug`) уже были корректны (нет catch-all).
  Подробнее — `specs/167-fix-share-claim-500/plan.md` § «FR-014 Audit
  Conclusion».

## Файлы / точки расширения

**Backend:**

- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt` — основной сервис
- `karaoke-web/.../controllers/PublicShareController.kt` — эндпоинты `/api/public/share/*`
- `karaoke-web/.../services/ShareLinkSweeper.kt` — фоновый sweeper (`@Scheduled 60s`)
- `karaoke-web/.../util/ShareErrorCode.kt` — коды ошибок + `ShareSecretMask`
- `karaoke-app/.../controllers/SiteShareLinksController.kt` — админские эндпоинты
- `karaoke-web/.../controllers/PublicPlayerController.kt` — поддержка `shareSecret`/`X-Share-Session`
- `karaoke-app/.../model/EventTypes.kt` — `SHARE_OPENED|CLAIMED|RELEASED|REVOKED|REJECTED`
- `karaoke-app/.../KaraokeProperties.kt` — секция `share_*`

**Frontend публичного сайта:**

- `karaoke-public/src/views/ShareView.vue` — публичный лендинг для `/share/{id}/{secret}`
- `karaoke-public/src/views/PlayerView.vue` — режим `?share=1`, экран завершения
- `karaoke-public/src/player/KaraokePlayer.js` — heartbeat, X-Share-Session, canTranspose
- `karaoke-public/src/components/ShareLinkModal.vue` — модалка владельца
- `karaoke-public/src/composables/useShareLink.js` — API для владельца
- `karaoke-public/src/services/songShareLink.js` — browserId, claim, heartbeat, release
- `karaoke-public/src/router/index.js` — `/share/:id/:secret`

**Frontend админки:**

- `webvue3/src/components/SiteUsers/UserShareLinksModal.vue` — таблица + сессии
- `webvue3/src/components/SiteUsers/shareLinkStore.js` — Vuex store
- `webvue3/src/store/index.js` — регистрация модуля
- `webvue3/src/components/SiteUsers/edit/SiteUserEdit.vue` — кнопка «Временный доступ»

**DB:**

- `deploy/karaoke-db/38_song_share_links.sql` — две таблицы + индексы
- `deploy/karaoke-db/39_song_share_recordhash.sql` — recordhash-триггеры

**Nginx:**

- `deploy/web-server-deploy/deploy/80to8897` — location `~ ^/share/[0-9]+/[A-Za-z0-9_-]+$`
  с `Cache-Control: no-store` и `Referrer-Policy: no-referrer`

## Ссылки

- `karaoke-web/.../services/SongShareLinkService.kt` ([исходник](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt), KDoc)
- [proposal.md](../../openspec/changes/add-song-share-link/proposal.md)
- [specs/guest-share-link/spec.md](../../openspec/changes/add-song-share-link/specs/guest-share-link/spec.md)
- [specs/guest-share-link-admin/spec.md](../../openspec/changes/add-song-share-link/specs/guest-share-link-admin/spec.md)
- [design.md](../../openspec/changes/add-song-share-link/design.md)
- [Pass 43 в architecture-notes.md](../architecture-notes.md) — запись об этом PR.
- [Pass 50 в architecture-notes.md](../architecture-notes.md) — запись о hotfix `167-fix-share-claim-500` (разделение `share.internal` vs `share.notFound`).
- [specs/167-fix-share-claim-500/spec.md](../../specs/167-fix-share-claim-500/spec.md) — спека hotfix.
- [specs/167-fix-share-claim-500/plan.md](../../specs/167-fix-share-claim-500/plan.md) — план hotfix + FR-014 Audit Conclusion.
- [specs/167-fix-share-claim-500/quickstart.md](../../specs/167-fix-share-claim-500/quickstart.md) — 7 manual scenarios + rollback.

## Админ-таблица «/sharelinks» (Pass 51+)

> Per Convection FR-009 (см. [.specify/memory/constitution.md](../../.specify/memory/constitution.md) и [AGENTS.md](../../AGENTS.md) «Per-feature doc»), это per-feature-документ для подсистемы share-ссылок.
> Поэтому **секция админ-таблицы** живёт здесь, а не в отдельном документе.

### Что

Глобальный read-only список **всех** share-ссылок (`tbl_song_share_links`) для админ-SPA — в отличие от per-user `UserShareLinksModal`, который показывает ссылки одного пользователя.

### Эндпоинт

- `POST /api/sharelinks/digest` — список с фильтрами (target, page, pageSize, `filterActiveOnly`, `filterOwnerId`, `filterSongId`, `filterCreatedFrom`, `filterCreatedTo`, sortBy, sortDir).
- Контроллер: [`SubscriptionsController.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ShareLinksAdminController.kt) (в `karaoke-app`, как и `SiteUsersController` / `SitePlaylistsController`).
- Контракт: [specs/171-admin-subscriptions-history/contracts/sharelinks-digest.md](../../specs/171-admin-subscriptions-history/contracts/sharelinks-digest.md).

### Frontend

- Роут: `/sharelinks` (`webvue3/src/router/index.js`).
- Компонент: [`ShareLinksTable.vue`](../../webvue3/src/components/ShareLinks/ShareLinksTable.vue) (таблица 25/стр, BPagination, target-aware toolbar, фильтры, drill-down к `/siteusers` и `/songs`).
- Filter modal: [`ShareLinksFilterModal.vue`](../../webvue3/src/components/ShareLinks/ShareLinksFilterModal.vue).
- Store: [`store.js`](../../webvue3/src/components/ShareLinks/store.js).

### Действие «Отозвать»

**Переиспользует** существующий `POST /api/siteusers/share/links/revoke` из
[`SiteShareLinksController.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/SiteShareLinksController.kt)
(см. action `revokeSiteUserShareLink` в [`shareLinkStore.js:64`](../../webvue3/src/components/SiteUsers/shareLinkStore.js))
с `reason='admin'`. **НЕ создаём** новый эндпоинт — иначе будет дублирование логики и
race-condition между двумя путями отзыва.

После успешного revoke — строка обновляется **in-place** через mutation `updateShareLinksDigestItem`
(без F5 и без полной перезагрузки таблицы). Подтверждение — кастомная модалка (НЕ `confirm()` —
нужно показать email владельца, song, target).

### Известные нюансы

- **`active=true` но `expires_at < now()`** — формально «активна, но истекла» (sweep ещё не отозвал).
  В UI показывается как «Истекла (sweep)» оранжевым.
- **JOIN к удалённой песне** — `LEFT JOIN tbl_songs` → `songName = ''` → UI показывает «песня удалена».
- **`token_hash`** (НЕ `secret`) — SHA-256 от секрета, который мы не храним в открытом виде.
  В UI показываются первые 8 символов (как в `UserSubscriptionsModal` для `orderId`).
- **target=Local vs Remote** — одинаково работает на обеих БД. По умолчанию Local (как везде).
