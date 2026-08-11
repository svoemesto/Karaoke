# Research: Временный полный доступ к песне (завершение)

**Date**: 2026-08-10
**Status**: Complete
**Spec**: [./spec.md](./spec.md)
**Branch**: `164-complete-guest-share-link`

## Цель research

Заполнить оставшиеся `NEEDS CLARIFICATION` из спеки и зафиксировать технические решения по архитектурным развилкам, которые спекa оставила «деталям реализации». Все 5 вопросов в `## Clarifications` спеки уже разрешены, но план требует конкретики:

1. **Как именно связать sessionTokenHash гостя с gesture token плеера** (FR-004) — два подхода: (a) обмен на kp_token, (b) прямой проброс sessionTokenHash в API плеера.
2. **Как передавать sessionTokenHash в бэкенд** — query-param vs заголовок `X-Share-Session`.
3. **Какие настройки прокинуть в `WebShareProperties`** — `heartbeatIntervalSeconds` отсутствует, другие есть.
4. **Как защитить `/api/siteusers/share/*`** — расширить path-patterns SiteAuthInterceptor или новый interceptor / ручная проверка.
5. **Как sweeper читает isEffectivePremium + SKIP** — через SiteUser/Song или прямой SQL.

## Решения

### Decision 1: Прямой проброс sessionTokenHash в API плеера (не обмен на kp_token)

**Decision**: Используем подход (b) — `PlayerView.vue` НЕ делает обмен sessionTokenHash на gesture token, а пробрасывает sessionTokenHash в `KaraokePlayer` через новый опциональный параметр конструктора. KaraokePlayer передаёт его в `GET /api/public/player/{id}/access`, `/{id}/playerdata` и `/{id}/file*.mp3` через query-param `?session=...`.

**Rationale**:
- Проще: один путь (`?session=` query), без второго запроса на обмен.
- Не требует хранения gesture token на бэкенде для share-сессий (лишняя БД-нагрузка).
- Lease уже привязан к sessionTokenHash — двойная косвенность через kp_token только усложняет инвалидацию.
- Логика `validateShareSession(sessionTokenHash, songId)` уже реализована в `SongShareLinkService.kt:693-711`, но не используется — её нужно вызвать из `PublicPlayerController.authorized()`.

**Alternatives considered**:
- (a) Обмен sessionTokenHash → kp_token через `/api/public/share/access-token`. Минус: новый endpoint, дополнительный round-trip при каждом открытии плеера, gesture token генерируется на сервере, но не несёт никакой новой информации — лишнее звено.
- (c) Embed guest session в URL как JWT. Минус: спека явно говорит про sessionTokenHash и lease, JWT добавляет криптографию без явной пользы.

### Decision 2: sessionTokenHash передаётся через query-param `?session=`

**Decision**: `?session=<hex64>` в каждом запросе к `/api/public/player/{id}/*`. Альтернативно — заголовок `X-Share-Session` (см. KDoc в SongShareLinkService.kt:430).

**Rationale**:
- Query-param проще для `<img src=...>` / `<audio src=...>` / `<video src=...>` тегов (если понадобится в будущем) — заголовки выставляются только через fetch/XHR.
- На фронте уже используется query-param в `/player/{id}?share=1&session=<hash>` (см. `ShareView.vue:124`) — консистентно.
- Бэкенд `PublicPlayerController` уже принимает `token` через `@RequestParam` — добавить `session` тривиально.

**Alternatives considered**:
- Только заголовок. Минус: для каждого `fetch` в KaraokePlayer нужно явно ставить заголовок, больше кода.
- Cookie. Минус: cookie ставится на домен, share-сессия per-song не выражается в cookie scoping; HTTP-only не нужен (это не auth-token, а анонимный идентификатор сессии).

### Decision 3: Добавить `heartbeatIntervalSeconds` в `WebShareProperties`, остальные уже есть

**Decision**: Расширяем `WebShareProperties.kt` (см. `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/config/WebShareProperties.kt`) полем `heartbeatIntervalSeconds: Long = 25`. Остальные нужные поля уже есть:
- `maxActivePerUser` = 5
- `maxGenerationsPerDay` = 30
- `maxReissuesPerSongPerHour` = 3
- `claimRateLimitPerIpPerMin` = 10
- `maxConcurrentSessions` = 2
- `leaseTtlSeconds` = 90
- `gracePauseSeconds` = 120
- `sweepIntervalSeconds` = 60

**Rationale**: Существующая структура префикса `karaoke.share.*` останется, добавляется только одно поле. Дефолты из спеки/вопроса Q2/Q4/Q5: heartbeat=25 сек, lease=90 сек — соответствует.

**ВНИМАНИЕ**: Спека FR-009 в спеке упоминала "maxActivePerUser (default 10)", но реальный дефолт 5. Уточнить в задачах: либо оставить 5, либо поднять до 10. Дефолт 5 — адекватный для типичного пользователя, 10 — для продвинутого. Решение: оставить 5 в `WebShareProperties` (не трогать без явной причины), а в спеке обновить SC-009 на «≤`maxActivePerUser` активных ссылок (дефолт 5)». Это не блокирующее уточнение.

**Alternatives considered**:
- Хардкодить в коде. Минус: нельзя тюнить через env без перекомпиляции.
- Завести отдельный класс ShareRuntimeProperties. Минус: ещё один бин ради одного поля — over-engineering.

### Decision 4: Новый метод авторизации для `/api/siteusers/share/*` — расширить SiteAuthInterceptor + ручная проверка `isEditor`

**Decision**:
- Расширяем `WebMvcConfig.kt` (см. `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/config/WebMvcConfig.kt:19`) — добавляем `/api/siteusers/**` в `addPathPatterns` SiteAuthInterceptor (требуется залогиненный пользователь).
- В новом `SiteShareLinksController.kt` дополнительно проверяем `user.isEditor` (поле `tbl_site_users.is_editor`) — не каждый залогиненный может смотреть чужие share-ссылки.

**Rationale**:
- Существующий паттерн `SiteAuthInterceptor` + `request.getAttribute(SITE_USER_ATTR)` уже используется в `PublicChatController`, `PublicStemJobController`, `PublicSongEditorController` — добавить новый контроллер по тому же паттерну тривиально.
- `isEditor` уже используется в `PublicSongEditorController` (`PublicSongEditorController.kt:94,132,191,230,255`) — проверенный паттерн.

**Alternatives considered**:
- Новый interceptor `EditorOnlyInterceptor`. Минус: ещё один бин, ещё одна конфигурация path-patterns, сложнее отлаживать.
- Spring Security `@PreAuthorize`. Минус: проект НЕ использует Spring Security (см. `SecurityConfig.kt` — `permitAll()` для webvue3); для karaoke-web SiteAuthInterceptor — это согласованная «домашняя» защита.

### Decision 5: Sweeper читает напрямую SQL + использует существующий `SiteUser.isEffectivePremium`

**Decision**: `ShareLinkSweeper` — Spring `@Component` с `@Scheduled(fixedDelayString = "${karaoke.share.sweep-interval-seconds:60}000")`. Логика:
1. `SELECT id, owner_site_user_id, song_id FROM tbl_song_share_links WHERE active=true AND expires_at<now()` → уже истёкшие по `expires_at` → пометить `active=false, revoke_reason='expired'` (фикс текущего поведения).
2. `SELECT id, song_id FROM tbl_song_share_links WHERE active=true` + JOIN на `tbl_site_users` для проверки `isEffectivePremium` — если нет премиума → `revoke_reason='premium_lost'`.
3. `SELECT id, song_id FROM tbl_song_share_links WHERE active=true` + JOIN на `tbl_songs` для проверки SKIP-тега в `tags` (`tags LIKE '% SKIP %' OR tags LIKE 'SKIP %' OR tags LIKE '% SKIP' OR tags = 'SKIP'`) → `revoke_reason='song_unavailable'`. Уже есть `songHasSkipTag()` (`SongShareLinkService.kt:925`) — переиспользуем.
4. `SELECT link_id FROM tbl_song_share_links WHERE active_session_lease_until IS NOT NULL AND active_session_lease_until<now()` → закрываем `tbl_song_share_sessions.finished_at = active_session_lease_until, result='timeout'`, обнуляем `active_session_*`.

**Rationale**:
- Sweeper работает по cron (`@Scheduled`), запускается на проде karaoke-web (там же где `StatsCacheScheduler` и `StemJobTempCleanupScheduler` — паттерн уже есть).
- SQL-подход (сырой JDBC, Constitution Principle II) — никакого ORM, всё через `prepareStatement` батчами.
- `SiteUser.isEffectivePremium` — геттер, считается динамически (`SiteUser.kt:91-98`), не требует сохранения.

**Alternatives considered**:
- Перебор всех записей по одной. Минус: N+1 запросов (1000 ссылок = 1001 запрос), медленно. Делаем батчами по 100, как в спеке SC-006.
- Cron через Linux + curl. Минус: не интегрируется с lifecycle karaoke-web (при рестарте планировщик ломается).

### Decision 6: TTL 7 дней — обновляем и бэкенд и фронт

**Decision**: Clarifications Q5 → 1ч / 24ч / 7д. Изменения:
- `PublicShareController.kt:54` — `if (ttlSeconds != 3600L && ttlSeconds != 86_400L && ttlSeconds != 604_800L)` → допускаем третий вариант.
- `useShareLink.js:7-10` — `SHARE_TTL_OPTIONS` получает третью запись `{ value: 604800, label: '7 дней' }`.
- DDL менять НЕ надо (`expires_at` — `timestamp`, любой TTL ок).

**Rationale**: 7 дней = 604800 сек, укладывается в `Long`. Никаких рисков переполнения.

**Alternatives considered**:
- 30 дней. Минус: спека отложила в backlog (требует дополнительный abuse-контроль).

## Используемые существующие компоненты

| Компонент | Файл | Использование |
|---|---|---|
| `SongShareLinkService.tryClaim` | `karaoke-web/.../SongShareLinkService.kt:433` | Без изменений — уже работает для `claim` |
| `SongShareLinkService.heartbeat` | `.../SongShareLinkService.kt:564` | Без изменений |
| `SongShareLinkService.release` | `.../SongShareLinkService.kt:656` | Без изменений |
| `SongShareLinkService.validateShareSession` | `.../SongShareLinkService.kt:693` | **Будет вызываться** из `PublicPlayerController.authorized()` |
| `SongShareLinkService.findLinkIdBySecret` | `.../SongShareLinkService.kt:408` | Остаётся «на всякий случай», не используется |
| `SongShareLinkService.songHasSkipTag` | `.../SongShareLinkService.kt:925` | Переиспользуется в sweeper |
| `PublicPlayerController.authorized()` | `karaoke-web/.../PublicPlayerController.kt:93` | **Расширяется** — принимает опциональный `session` param |
| `SiteAuthInterceptor` | `karaoke-web/.../SiteAuthInterceptor.kt` | **Расширяется** в `WebMvcConfig.kt:19` для `/api/siteusers/**` |
| `SiteUser.isEffectivePremium` | `karaoke-app/.../SiteUser.kt:91` | Используется в sweeper |
| `Connection.remote()` | `karaoke-app/.../Connection.kt:92` | Для `target=remote` в admin endpoint'ах |
| `KaraokePlayer` (karaoke-public) | `karaoke-public/src/player/KaraokePlayer.js` | **Расширяется** — опциональный `shareSessionTokenHash`, heartbeat/release таймеры |
| `PlayerView.vue` | `karaoke-public/src/views/PlayerView.vue` | **Расширяется** — читает `route.query.session`, пробрасывает в KaraokePlayer |
| `usePlayerAccess.checkAccess` | `karaoke-public/src/composables/usePlayerAccess.js:26` | **Расширяется** — принимает опциональный `shareSessionTokenHash` |
| `WebShareProperties` | `karaoke-web/.../config/WebShareProperties.kt` | **Дополняется** полем `heartbeatIntervalSeconds` |

## Новые компоненты (создаются)

| Компонент | Файл | Назначение |
|---|---|---|
| `ShareLinkSweeper` | `karaoke-web/.../services/ShareLinkSweeper.kt` | Spring `@Scheduled` — авто-отзыв ссылок |
| `SiteShareLinksController` | `karaoke-web/.../controllers/SiteShareLinksController.kt` | 3 endpoint'а для webvue3 admin |
| `docs/features/guest-share-link.md` | `docs/features/` | Per-feature документация (FR-009 constitution) |
| `karaoke.share.*` секция в `application.yml` | `karaoke-web/src/main/resources/application.yml` | Опциональная настройка лимитов через env |

## Обновляемые компоненты (без полной переписки)

| Файл | Изменение |
|---|---|
| `PublicPlayerController.kt` | `authorized()` принимает session param; `access/playerData/file*.mp3` читают его |
| `WebShareProperties.kt` | +1 поле `heartbeatIntervalSeconds` |
| `WebMvcConfig.kt` | +1 path-pattern `/api/siteusers/**` |
| `PublicShareController.kt` | TTL whitelist расширен на 604800 |
| `useShareLink.js` | +1 TTL option |
| `KaraokePlayer.js` (karaoke-public) | +опц. `shareSessionTokenHash`, heartbeat/release таймеры |
| `PlayerView.vue` | +чтение `?session=`, прокидывание в KaraokePlayer |
| `usePlayerAccess.js` | +опц. `shareSessionTokenHash` |
| `ShareLinkModal.vue` | +авто-обновление 30 сек + маппинг errorCode на русский |
| `ShareView.vue` | +кнопка «Скопировать ссылку», expiresAtLabel |

## Конституция (NON-NEGOTIABLE) — проверка

| Принцип | Соблюдение | Заметки |
|---|---|---|
| I. Self-contained | ✅ | Никаких внешних SaaS — всё локально. |
| II. Сырой JDBC + recordhash | ✅ | DDL уже использует recordhash-триггеры (см. `39_song_share_recordhash.sql`); новый код использует `prepareStatement`. |
| III. SyncRegistry | ✅ | FR-060 спеки явно: НЕ расширяем SyncRegistry, share-таблицы PROD-only. |
| IV. Async-очередь | N/A | sweeper — синхронный `@Scheduled`, не KaraokeProcess. |
| V. Двух-фронтенд | ✅ | Изменения только в `karaoke-public` (плеер, лендинг, модалка) и `webvue3` (admin модалка). Без смешивания. |
| VI. Code Standards | ✅ | Все новые классы будут с KDoc `@see docs/features/guest-share-link.md`; компоненты с JSDoc. Per-feature документ создаётся. |
| VII. Cross-Machine | ✅ | Без изменений в `.git-blame-ignore-revs` или `.gitattributes`. |
| VIII. Секреты | ✅ | Никаких hardcoded секретов — env-переменные через `${VAR}` в `application.yml`. |

**Gates**: ✅ Все проходят. Complexity Tracking не требуется.

## Out-of-scope для plan (отложено в backlog)

- Auto-recovery при 410 (см. Clarifications Q4 — не делаем).
- `canExport=true` для гостя (см. Clarifications Q1 — не делаем).
- TTL 30 дней / без ограничения (см. Clarifications Q5 — отложено).
- Шаринг в мессенджеры через Web Share API (см. спека Out of Scope).
- QR-код для ссылки.
- Компрометация секрета — `force_revoke_all_user_links()` (нет в спеке).
- Threat model / формальный анализ безопасности.
- Auto-revoke по N неактивных дней (не lease, а самой ссылки).

## Границы доступа агента (для этой реализации)

Согласно `constitution.md` п. «Ограничения и доступы агента»:

- **karaoke-web**: можно редактировать код, собирать `./gradlew karaoke-web:bootJar`, перезапускать локальный контейнер. ✅
- **karaoke-app**: только код (heartbeat/release вызываются с фронта, backend не меняется). ✅
- **karaoke-public**: можно редактировать код, собирать `npm run build`, перезапускать контейнер. ✅
- **webvue3**: можно редактировать код. ✅
- **DDL**: миграция уже в гите (`38_*.sql`, `39_*.sql`), применять локально (`docker exec -i karaoke-db psql < ...`). На проде — пользователь.
- **Деплой на сервер**: только пользователь.
