# Feature Specification: Починить 500 на `POST /api/public/share/claim`

**Feature Branch**: `167-fix-share-claim-500`
**Created**: 2026-08-11
**Status**: Draft
**Input**: User description: "Задача - добиться правильной работы временных ссылок. Сейчас ссылки формируются, что при попытке их открыть пользователь видит «Ссылка недоступна / Срок действия ссылки истёк, она была отозвана владельцем или песня снята с публикации.», а в нетворке браузера видно, что это из-за того, что POST на `https://sm-karaoke.ru/api/public/share/claim` возвращает 500 (`errorCode: "share.notFound"`). Ссылка при этом гарантированно свежая, песня при этом гарантированно может быть проиграна в плеере (статус 6, все стемы в хранилище есть и т.п.)"

## Контекст и текущее состояние

### Симптом (прод, наблюдение 2026-08-11)

Премиум-владелец песни создаёт временную ссылку через `ShareLinkModal.vue` (`POST /api/public/share/{songId}/create`) — ответ приходит успешно, модалка показывает URL. Владелец пересылает ссылку другу. Друг переходит по ссылке, лендинг `ShareView.vue` отправляет `POST /api/public/share/claim` с телом `{secret, browserHash}`. Бэкенд возвращает `500 Internal Server Error` с телом `{"errorCode":"share.notFound"}`. Лендинг показывает заглушку «Ссылка недоступна». Песня при этом играет в обычном плеере у того же владельца без каких-либо проблем (id_status ≥ 6, все стемы в MinIO).

### Корневая причина (диагностирована частично)

См. AGENTS.md, Q&A «500 на `/api/public/share/claim` — где DDL для share-таблиц?»:

> **Кратко**: живой DDL лежит в `deploy/karaoke-db/38_song_share_links.sql` + `39_song_share_recordhash.sql`. Восстановлен в Pass 47 (2026-08-10) из `git fsck --lost-found` (dangling blobs `c8cc7472a...`, `e6c7d1733...`), потерян при переключении веток — оригинал назывался `28_song_share_links.sql` + `28b_song_share_recordhash.sql`, перенумерован в свободные 38/39.
>
> **Применить миграцию**:
> ```bash
> # Локально:
> docker exec -i karaoke-db psql -U postgres -d karaoke < deploy/karaoke-db/38_song_share_links.sql
> docker exec -i karaoke-db psql -U postgres -d karaoke < deploy/karaoke-db/39_song_share_recordhash.sql
> # На проде (только через пользователя, см. «Ограничения агента»).
> ```

То есть в БД продакшена, с высокой вероятностью, отсутствуют таблицы `tbl_song_share_links` и `tbl_song_share_sessions` (а также сопутствующие триггеры `recordhash`/`last_update`). `SongShareLinkService.tryClaim` падает с SQL-исключением типа `org.postgresql.util.PSQLException: ERROR: relation "tbl_song_share_links" does not exist`.

Это исключение **маскируется в двух местах** под `share.notFound`:

1. **`karaoke-web/.../services/SongShareLinkService.kt:597-602`** — `tryClaim` оборачивает любой `Exception` (включая `SQLException`) в `throw NotFound()`:
   ```kotlin
   } catch (e: Exception) {
       println("[tryClaim] UNEXPECTED class=${e::class.simpleName} msg=${e.message}")
       e.printStackTrace()
       log.error("ShareLink tryClaim UNEXPECTED class=${e::class.simpleName} msg=${e.message}", e)
       throw NotFound()
   }
   ```

2. **`karaoke-web/.../controllers/PublicShareController.kt:172-176`** — `claim` имеет три ветки catch: `ConcurrentLimit` → 409, `RateLimited` → 429, `NotFound` → 404 `share.notFound`, и catch-all `Exception` → **500 `share.notFound`**:
   ```kotlin
   } catch (e: SongShareLinkService.ConcurrentLimit) {
       ResponseEntity.status(409).body(mapOf("errorCode" to e.code.dbValue))
   } catch (e: SongShareLinkService.RateLimited) {
       ResponseEntity.status(429).body(mapOf("errorCode" to e.code.dbValue))
   } catch (_: SongShareLinkService.NotFound) {
       ResponseEntity.status(404).body(mapOf("errorCode" to "share.notFound"))
   } catch (_: Exception) {
       ResponseEntity.status(500).body(mapOf("errorCode" to "share.notFound"))
   }
   ```

**Симптом маскировки**: даже когда причина системная (нет таблицы, ошибка в SQL, NPE в `loadSongInfo`), пользователь видит «Ссылка недоступна», а в логах `tryClaim` стек-трейс всё-таки пишется (`log.error(...)`) — но по факту бросается `NotFound`, и контроллер превращает его в 404/500 с тем же `errorCode`. Диагностика становится «иголка в стоге сена».

### Что НЕ покрыто соседними спеками

- **`specs/164-complete-guest-share-link/spec.md`** — широкий план завершения фичи (heartbeat, release, sweeper, admin, US1-US7). Статус: Draft. US1 «Гость переходит по share-ссылке и смотрит плеер» подразумевает рабочее `claim`, но спека не фокусируется на **диагностике 500** и **не отличает «ссылка реально отозвана» от «упал SQL»**.
- **`specs/166-fix-share-link-timezone/spec.md`** — таймзоны (`expires_at`, МСК vs TZ устройства). Ортогональная проблема.

### Что есть в коде (что работает локально, но не на проде)

- DDL: `deploy/karaoke-db/38_song_share_links.sql` + `39_song_share_recordhash.sql` (восстановлены в Pass 47, идемпотентны — см. заголовок 38_song_share_links.sql:23-27).
- Бэкенд: `SongShareLinkService` (`karaoke-web/.../services/SongShareLinkService.kt`) — все методы.
- Контроллер: `PublicShareController` (`karaoke-web/.../controllers/PublicShareController.kt`) — `/{songId}/create`, `/mine/{songId}`, `/mine/{songId}/revoke`, `/claim`, `/heartbeat`, `/release`, `/debug`.
- Диагностический endpoint: `POST /api/public/share/debug` — возвращает JSON с пошаговой диагностикой `step1_resolve`, `step2_ownerId`, `step3_songId`, `step4_songInfo`, `step5_checkExisting`, и т.д. (см. `SongShareLinkService.kt:639-...`). Уже реализован и сейчас бросает то же `NotFound` на `step1`, если таблиц нет — но не маскирует **внутреннюю** причину.

## Clarifications

### Session 2026-08-11

- **Q1**: Что делать с маскировкой ошибок в catch-all? → **A**: Перевести catch-all в `tryClaim` и в контроллере в режим «вернуть реальное исключение с HTTP 500 и `errorCode: "share.internal"` (новый код)» + отдельное логирование со стек-трейсом на ERROR. Существующие `share.notFound` (404), `share.concurrentLimit` (409), `share.rateLimited` (429), `share.tokenMissing` (400) сохраняются для ожидаемых ситуаций. Это разделение даст среде возможность различать «ссылка действительно отозвана» от «у нас упал SQL».
- **Q2**: Применять ли миграцию `38`/`39` автоматически из скрипта деплоя или только вручную? → **A**: Деплой остаётся ручным (принцип «Ограничения агента» из AGENTS.md — деплой на прод делает только пользователь). Миграция — отдельным коммитом с `CHECKLIST`-отметкой «применить вручную на PROD-БД ДО/вместе с деплоем». README в шапке миграции уже требует именно этого, просто ранее не выполнялось.
- **Q3**: Должен ли `/api/public/share/debug` оставаться доступным на проде? → **A**: **Да**, но за `X-Share-Debug-Key` (env `WEB_SHARE_DEBUG_KEY`, если не задан — endpoint отключён). Иначе это утечка внутреннего состояния. Не критично для текущего фикса, можно перенести в backlog spec 164 — в текущей спеке достаточно того, что `debug` уже есть и им можно пользоваться при диагностике после применения миграции.
- **Q4**: Что делать с catch-блоком в `tryClaim` (строки 597-602) — оставить проброс `NotFound()` или изменить? → **A**: Спек-уровень: ввести **новый тип** `SongShareLinkService.InternalError` (наследник `ShareException`), который НЕ маскируется в 404/500 `share.notFound`, а пробрасывается как 500 `share.internal` контроллером. Существующие `ShareException`-подтипы (`NotFound`, `LeaseExpired`, `ConcurrentLimit`, `RateLimited`) сохраняются. Это разделение — главная цель фикса.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Гость успешно делает claim по валидной свежей ссылке (Priority: P1)

Премиум-владелец создаёт ссылку на свою песню, пересылает другу. Друг переходит по ссылке `https://svoemesto.ru/share/{songId}/{secret}` в приватном окне. Лендинг показывает карточку песни и кнопку «Открыть плеер». Друг нажимает кнопку. Бэкенд успешно обрабатывает `POST /api/public/share/claim` (HTTP 200, JSON с `linkId`, `songId`, `sessionTokenHash`, `expiresAt`, `redirectTo`, `songName`, `author`, `album`, `year`, `albumImageUrl`, `artistImageUrl`). Гость попадает в полноэкранный плеер, слышит аудио, видит текст и аккорды.

**Why this priority**: Это центральный user-journey фичи. Без него весь остальной код бесполезен — share-ссылка превращается в дорогой способ показать «Ссылка недоступна». 100% пользователей фичи упираются в этот сценарий.

**Independent Test**: Полностью end-to-end без зависимостей от других фич: создать премиум-ссылку через UI или `curl`, открыть в анонимном браузере (curl с `Cookie: ` без авторизации), убедиться что claim отвечает 200 OK и JSON содержит `sessionTokenHash` длиной 64 hex-символа.

**Acceptance Scenarios**:

1. **Given** активная share-ссылка, выданная премиум-владельцем на песню в статусе ≥6, **When** анонимный пользователь переходит по `/share/{songId}/{secret}` и `ShareView` отправляет `POST /api/public/share/claim` с `{secret, browserHash}`, **Then** бэкенд возвращает HTTP 200 с телом, содержащим `linkId`, `songId`, `sessionTokenHash` (64 hex), `expiresAt` (epoch ms), `redirectTo` вида `/player/{songId}?share=1&session={sessionTokenHash}`, **And** в `tbl_song_share_sessions` создаётся запись со статусом `finished_at IS NULL, result=''`, **And** в `tbl_song_share_links` обновляются `active_session_token_hash`, `active_session_browser_hash`, `active_session_lease_until`, `first_used_at`, `last_used_at`, `sessions_total = sessions_total + 1`.
2. **Given** та же ссылка, **When** гость делает повторный `claim` с тем же `browserHash` (F5 страницы), **Then** бэкенд возвращает тот же `sessionTokenHash` (existing lease), **And** `sessions_total` НЕ инкрементируется (existingTokenHash-ветка в `tryClaim`, см. `SongShareLinkService.kt:514-530`).
3. **Given** гость делает `claim` с `browserHash`, отличным от `active_session_browser_hash` существующего lease (новое устройство), **When** `active_session_lease_until > now()` и активных сессий < `maxConcurrentSessions` (2), **Then** создаётся новая сессия, `sessions_total` инкрементируется, `active_session_*` обновляются на новый `sessionTokenHash` и `browserHash`.
4. **Given** гость делает `claim` с новым `browserHash`, когда уже 2 активных lease-сессии с разными `browserHash`, **When** запрос приходит, **Then** бэкенд возвращает HTTP 409 с `errorCode: "share.concurrentLimit"`, **And** `tbl_song_share_links.rejected_concurrent` инкрементируется.

---

### User Story 2 — Системные сбои claim больше не маскируются под `share.notFound` (Priority: P1)

Если по любой причине `tryClaim` падает с **неожиданным** исключением (SQL-ошибка, NPE в `loadSongInfo`, отсутствие таблицы, сетевая проблема с БД, конфликт recordhash-триггера), бэкенд возвращает HTTP 500 с **новым** `errorCode: "share.internal"` (а не `share.notFound`), и в логи попадает полный стек-трейс исключения. Это позволяет разработчику и поддержке отличать «ссылка реально отозвана» (404) от «у нас упал SQL» (500 internal).

**Why this priority**: Без этого разделения диагностика любого следующего бага в share-флоу снова превратится в «500 share.notFound, в логах чисто» (см. текущий симптом — прод-инцидент 2026-08-11 занял несколько часов именно из-за маскировки). Это не user-facing фича, но блокер качества поддержки.

**Independent Test**: Локально создать условие для системной ошибки (например, удалить `tbl_song_share_links`, или вставить в `tryClaim` строку `throw RuntimeException("test")` после `resolveForGuest`) и убедиться, что endpoint отдаёт 500 с `share.internal` + полный стек в логах.

**Acceptance Scenarios**:

1. **Given** `tryClaim` падает с `SQLException` (например, `relation "tbl_song_share_links" does not exist`), **When** анонимный пользователь делает `claim`, **Then** бэкенд возвращает HTTP 500 с JSON `{"errorCode":"share.internal"}`, **And** в логах `karaoke-web` присутствует `ERROR ... ShareLink tryClaim UNEXPECTED ...` с полным стек-трейсом (а не только `class=NotFound msg=null`).
2. **Given** `tryClaim` падает с `NullPointerException` в `loadSongInfo` (например, песня удалена из `tbl_songs` между созданием ссылки и claim), **When** анонимный пользователь делает `claim`, **Then** бэкенд возвращает HTTP 500 `share.internal`, **And** в логах — стек с указанием `loadSongInfo` / `songIdOf`.
3. **Given** все ожидаемые ситуации — отозванная ссылка, истёкший TTL, неверный секрет, **When** гость делает `claim`, **Then** бэкенд возвращает HTTP 404 с `errorCode: "share.notFound"` (поведение не изменилось).
4. **Given** гость делает `claim` с пустым/отсутствующим `secret` или `browserHash`, **When** запрос приходит, **Then** бэкенд возвращает HTTP 400 с `errorCode: "share.tokenMissing"` (поведение не изменилось).

---

### User Story 3 — Оператор/разработчик может диагностировать состояние share-БД (Priority: P2)

При саппорте прод-инцидента оператор (или разработчик в dev-окружении) отправляет `POST /api/public/share/debug` с телом `{secret}` (или пустым). Эндпоинт возвращает JSON с пошаговой диагностикой: успешно ли `step1_resolve`, какие `step2_ownerId`, `step3_songId`, `step4_songInfo`, присутствуют ли записи в `tbl_song_share_links` / `tbl_song_share_sessions`, текущие счётчики. Это позволяет за минуту определить, на каком этапе ломается claim — без необходимости смотреть серверные логи.

**Why this priority**: Уже реализован (`SongShareLinkService.kt:639-...`, `PublicShareController.kt:218-225`), но сейчас бесполезен для диагностики PROD-инцидентов из-за маскировки в catch-all. После фикса US2 станет самодостаточным инструментом. Защита `X-Share-Debug-Key` — backlog.

**Independent Test**: Отправить `POST /api/public/share/debug` с известным секретом → получить JSON со всеми шагами и понять, в какой момент что-то идёт не так.

**Acceptance Scenarios**:

1. **Given** активная ссылка с известным `secret`, **When** оператор отправляет `POST /api/public/share/debug {secret}`, **Then** бэкенд возвращает HTTP 200 с JSON вида `{"step1_resolve":"OK linkId=…", "step2_ownerId":"OK ownerId=…", …, "linkId":…, "ownerId":…, "songId":…, …}`.
2. **Given** ссылка с известным `secret`, но таблицы в БД отсутствуют (смоделировано на локали через `DROP TABLE tbl_song_share_links`), **When** оператор отправляет `POST /api/public/share/debug {secret}`, **Then** бэкенд возвращает HTTP 200 с JSON, где `step1_resolve: "FAILED: PSQLException: ERROR: relation \"tbl_song_share_links\" does not exist"` — **и** реальный класс+сообщение исключения видны в JSON-ответе, **а не** маскируются под `"FAILED: NotFound"`.
3. **Given** оператор отправляет `POST /api/public/share/debug` без `secret` или с пустым `secret`, **When** запрос приходит, **Then** бэкенд возвращает HTTP 400 с `errorCode: "share.tokenMissing"` (поведение не изменилось).
4. **Given** `tryClaim` выбрасывает `SQLException` на `step1`, **When** оператор отправляет `POST /api/public/share/debug`, **Then** JSON-ответ содержит поле `error_step1` с реальным классом исключения и сообщением (а не с `NotFound`).

---

### Edge Cases

- **Миграция применена частично**: одна из двух таблиц (`tbl_song_share_links`) есть, а `tbl_song_share_sessions` — нет. После фикса US2 `tryClaim` упадёт на INSERT в `tbl_song_share_sessions` (FK на `tbl_song_share_links.id`) — клиент увидит 500 `share.internal` + в логах будет видно `relation "tbl_song_share_sessions" does not exist`. Оператор сразу понимает, какую миграцию докатить.
- **`recordhash`-триггер не создан** (только таблица, без `39_song_share_recordhash.sql`): INSERT в `tbl_song_share_links` упадёт на `recordhash IS NULL`-проверке в триггере `update_last_updated` (если триггер на `last_update` есть, а на `recordhash` — нет) или наоборот — клиент увидит 500 `share.internal` с указанием `column "recordhash" violates not-null`.
- **Миграция применена, но в БД остались «хвосты» от старых попыток** (полупустые таблицы, конфликтующие IDENTITY, отсутствующий PRIMARY KEY). Идемпотентные `CREATE TABLE IF NOT EXISTS` + `DO`-блоки в `38_song_share_links.sql` должны это покрывать, но если что-то пойдёт не так — фикс US2 даст 500 `share.internal` с реальной причиной.
- **Миграция применена, claim работает, но `active_session_token_hash`/`active_session_browser_hash` колонки не созданы** (восстановленная миграция содержит их — проверить при восстановлении, что `git fsck`-извлечение дало полный DDL, а не обрезанный). После фикса US2 клиент увидит 500 `share.internal` с `column "active_session_token_hash" does not exist`.
- **Секрет валидный, но `tbl_song_share_links.active = false`** (ссылка отозвана) — это **ожидаемое** поведение, отдаём 404 `share.notFound`. Не путать с системной ошибкой.
- **Секрет валидный, `active = true`, но `expires_at < now()`** — это **ожидаемое** поведение, отдаём 404 `share.notFound` (см. `findLinkIdBySecret` / `resolveForGuest` с условием `expires_at>now()`).
- **Rate-limit сработал** (более 10 claim в минуту с одного IP) — это **ожидаемое** поведение, отдаём 429 `share.rateLimited`. Не путать с системной ошибкой.
- **Миграция применена, claim работает, но владелец потерял премиум** — ссылка остаётся валидной до отзыва sweeper'ом (см. spec 164 US5). Claim работает нормально, ошибок нет.

## Requirements *(mandatory)*

### Functional Requirements

#### A. Миграция БД

- **FR-001**: Миграция `deploy/karaoke-db/38_song_share_links.sql` ДОЛЖНА быть применена на **прод-БД** (помимо уже применённой на LOCAL) — создаёт таблицы `tbl_song_share_links`, `tbl_song_share_sessions`, IDENTITY, PRIMARY KEY, уникальные индексы (`idx_tbl_song_share_links_active`, `idx_tbl_song_share_links_token_hash`), индексы для листинга/лимитов/sweeper'а. Миграция идемпотентна (`CREATE TABLE IF NOT EXISTS` + `DO`-блоки), повторное применение безопасно.
- **FR-002**: Миграция `deploy/karaoke-db/39_song_share_recordhash.sql` ДОЛЖНА быть применена на **прод-БД** — создаёт функции `update_tbl_song_share_links_recordhash`, `update_tbl_song_share_sessions_recordhash`, индексы `idx_*_recordhash`, `*_last_update_index`, триггеры `update_recordhash_*_trigger`, `update_last_updated_*_trigger`. Идемпотентна.
- **FR-003**: После применения обеих миграций в прод-БД ДОЛЖНЫ существовать таблицы `public.tbl_song_share_links` и `public.tbl_song_share_sessions` с указанными в `38_song_share_links.sql:32-54, 116-137` колонками и ограничениями. Проверяется `psql \dt tbl_song_share*` + `psql \d tbl_song_share_links`.
- **FR-004**: В `README` шапке миграции `38_song_share_links.sql` ДОЛЖНО быть явное указание «применить на PROD-БД вручную ДО деплоя karaoke-web», а в PR-описании — пункт «apply migrations to PROD before deploy» с командой из AGENTS.md Q&A.

#### B. Разделение ошибок в `tryClaim` и `claim`-контроллере

- **FR-010**: В `karaoke-web/.../services/SongShareLinkService.kt` ДОЛЖЕН быть введён новый sealed-подтип `ShareException` — `InternalError(cause: Throwable)`. Существующие `NotFound`, `LeaseExpired`, `ConcurrentLimit`, `RateLimited`, `LinkAlreadyActive`, `SongUnavailable` сохраняются как ожидаемые `ShareException`.
- **FR-011**: В `tryClaim` catch-блок `Exception` (строки 597-602) ДОЛЖЕН бросать `InternalError(e)` вместо `NotFound()`. Логирование стек-трейса на ERROR остаётся. Существующий catch `ShareException` остаётся без изменений (это для **ожидаемых** доменных ошибок).
- **FR-012**: В `karaoke-web/.../controllers/PublicShareController.kt:claim` catch-блок `_ : Exception` (строки 174-175) ДОЛЖЕН быть **удалён** или заменён на узкий `catch (_: SongShareLinkService.InternalError) { ResponseEntity.status(500).body(mapOf("errorCode" to "share.internal")) }`. Поведение для существующих `ConcurrentLimit`, `RateLimited`, `NotFound` не меняется (409, 429, 404).
- **FR-013**: В `ShareErrorCode.kt` ДОЛЖЕН быть добавлен новый код `INTERNAL("share.internal")` со значением `"share.internal"` (строковое значение для JSON-ответа). Используется только контроллером для 500-ответов при неожиданных исключениях.
- **FR-014**: Все остальные share-эндпоинты (`/heartbeat`, `/release`, `/{songId}/create`, `/mine/{songId}/revoke`, `/debug`) ДОЛЖНЫ быть проаудитированы на предмет аналогичной маскировки — если в каком-то из них есть `catch (_: Exception) { ... "share.notFound" }`, заменить на корректную ветку для `InternalError` или пробрасывать наверх.

#### C. Диагностический `/debug`

- **FR-020**: `POST /api/public/share/debug` уже возвращает пошаговую диагностику (см. `SongShareLinkService.kt:639-...`). После фикса FR-011/FR-012 JSON-ответ ДОЛЖЕН содержать **реальные** классы исключений на каждом шаге (а не `NotFound` при любой проблеме).
- **FR-021**: Поведение `debug` при `secret == null/blank` — HTTP 400 с `share.tokenMissing` (уже реализовано). Сохраняется.

#### D. Документация и релиз

- **FR-030**: В `docs/features/guest-share-link.md` секция «Инварианты / правила» ДОЛЖНА включать пункт «диагностика 500-ошибок claim» с явной инструкцией:
  1. Проверить `psql \dt tbl_song_share*` — таблицы должны быть;
  2. Если таблиц нет — применить `38_song_share_links.sql` + `39_song_share_recordhash.sql`;
  3. Если таблицы есть — отправить `POST /api/public/share/debug {secret}` и посмотреть `step1_resolve` / `step2_ownerId` и т.д.;
  4. В логах `karaoke-web` искать `ShareLink tryClaim UNEXPECTED` / `ShareException` (после фикса FR-011).
- **FR-031**: В `docs/architecture-notes.md` ДОЛЖНА быть добавлена запись о PR (Pass 50+): диагностика 500 share.notFound, применение DDL на прод, разделение ошибок `share.internal` vs `share.notFound`.
- **FR-032**: В `AGENTS.md` Q&A секция «500 на `/api/public/share/claim`» ДОЛЖНА быть обновлена: явно указать, что после применения миграции 500 на этом endpoint'е должны исчезнуть, а оставшиеся 500 — это уже `share.internal` (новый код), и что для диагностики применён `POST /api/public/share/debug`.

### Key Entities

- **`tbl_song_share_links`** (см. `deploy/karaoke-db/38_song_share_links.sql:32-54`): грант. Колонки: `id`, `owner_site_user_id` (FK `tbl_site_users`), `song_id` (bigint, без FK), `token_hash` (SHA-256 hex), `active`, `expires_at`, `created_at`, `revoked_at`, `revoke_reason`, `first_used_at`, `last_used_at`, `active_session_token_hash`, `active_session_browser_hash`, `active_session_lease_until`, `sessions_total`, `rejected_concurrent`, `last_update`, `recordhash`.
- **`tbl_song_share_sessions`** (см. `38_song_share_links.sql:116-137`): playback-сессия. Колонки: `id`, `share_link_id` (FK `tbl_song_share_links`), `song_id` (денормализовано), `browser_hash`, `owner_site_user_id`, `anon_id`, `opened_at`, `started_at`, `last_seen_at`, `finished_at`, `result`, `client_ip_hash`, `user_agent_hash`, `last_update`, `recordhash`.
- **`ShareException.InternalError`** (новое): неожиданное системное исключение, пробрасывается из `tryClaim` при любом `Exception`, не относящемся к ожидаемым доменным ошибкам. Маппится контроллером в HTTP 500 + `share.internal`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% заведомо валидных свежих ссылок (созданных через UI после применения миграции) при `POST /api/public/share/claim` возвращают HTTP 200 с валидным `sessionTokenHash` (64 hex-символа). Проверяется ручным сценарием на проде + автоматическим curl-тестом: создать ссылку через UI → скопировать `secret` → отправить `claim` с пустыми cookie → получить 200.
- **SC-002**: 0 ошибок HTTP 500 на `/api/public/share/*` в продакшен-логах за 7 дней после деплоя при условии, что БД-миграция применена. Все ошибки либо 4xx с осмысленным `errorCode`, либо 5xx с `share.internal` и стек-трейсом в логах.
- **SC-003**: При моделировании системной ошибки (например, `DROP TABLE tbl_song_share_links` на локали + `claim` с валидным секретом) — бэкенд возвращает HTTP 500 с `errorCode: "share.internal"`, а в логах `karaoke-web` присутствует `ERROR ... ShareLink tryClaim UNEXPECTED class=org.postgresql.util.PSQLException ...` с полным стек-трейсом.
- **SC-004**: `POST /api/public/share/debug {secret}` с валидным `secret` возвращает HTTP 200 с JSON, в котором **все** шаги, прошедшие до текущего, присутствуют с префиксом `"OK"` (например, `"step1_resolve":"OK linkId=42"`), а на упавшем шаге — `"FAILED: <класс>: <сообщение>"` с реальным классом исключения. Не маскируется под `NotFound`.
- **SC-005**: На прод-БД после применения миграции выполняется `psql \dt tbl_song_share*` и возвращает ровно 2 строки (`tbl_song_share_links`, `tbl_song_share_sessions`). Проверяется один раз после миграции.
- **SC-006**: В админке `webvue3` (`UserShareLinksModal.vue`) после фикса продолжает работать `shareLinkStore.js` — но это уже зона ответственности spec 164 (FR-030), здесь не проверяется.

### Subjective Outcomes

- **SC-010**: Разработчик/оператор при виде `share.internal` в логах сразу понимает, что это «у нас упало что-то внутри», а не «пользователю показали что-то странное» — текст ошибки не обманывает.
- **SC-011**: Гость, открывший валидную ссылку после фикса, **не видит** сообщение «Ссылка недоступна» — попадает на лендинг с кнопкой «Открыть плеер» и далее в плеер.

## Assumptions

- DDL `38_song_share_links.sql` и `39_song_share_recordhash.sql` — **полные** (Pass 47 восстановил оба blob'а полностью, не обрезанно). Если при восстановлении что-то потерялось, FR-003 это поймает через проверку `\dt` и `\d`.
- Идемпотентность миграций 38/39 покрывает случай «таблицы созданы вручную раньше с другим набором колонок» — НЕ покрывает (если колонки не хватает, идемпотентность ничего не сделает). Это допустимо: при таком сценарии `claim` упадёт с `column "..." does not exist`, оператор увидит через `share.internal` (после FR-011) и примет решение (дропнуть таблицу и применить заново, или дополнить ALTER).
- Существующий код `tryClaim` (`SongShareLinkService.kt:475-603`) после применения миграции на проде заработает без дополнительных правок кода (помимо FR-010/FR-011). Это предположение проверяется acceptance-сценарием US1#1 — если `claim` после миграции всё ещё падает с другой ошибкой (не `relation does not exist`), спека дополняется.
- Кафка/recordhash-триггер из миграции 39 не конфликтует с существующими триггерами на других таблицах — все имена триггеров и функций префиксированы (`update_tbl_song_share_links_recordhash`, `update_tbl_song_share_sessions_recordhash`, `update_recordhash_song_share_links_trigger`, `update_recordhash_song_share_sessions_trigger`).
- На проде `WORKING_DATABASE` указывает на ту же БД, где будут применены миграции 38/39 (иначе fix бессмысленнен). Это вопрос к деплою, не к спеке.
- В spec 164 уже заявлено, что `claim` должен работать (US1 P1, FR-001, SC-001). Текущая спека — фокусный hotfix на **исполнение** этого требования, не дублирование. Spec 164 после мержа остаётся «Draft» до закрытия остальных gap'ов (heartbeat, release, sweeper, admin).

## Out of Scope (для будущих раундов)

- Защита `/api/public/share/debug` за `X-Share-Debug-Key` (env `WEB_SHARE_DEBUG_KEY`) — backlog spec 164.
- Реализация heartbeat/release/sweeper/admin — spec 164.
- Таймзоны (МСК vs TZ устройства) — spec 166.
- Логирование stack-trace через Sentry/аналог — общий observability backlog.
- Rate-limit на `claim` уже реализован в `SongShareLinkService.checkRateLimit` (см. `SongShareLinkService.kt:178-179`) — настройка лимита в `WebShareProperties.share_maxClaimPerMinute` (если есть) — backlog, не блокер.
- Auto-recovery через `claim` с просроченной ссылкой — не реализуется (см. Clarifications spec 164 Q4).
