# Feature: Определение адреса источника запроса (source address)

**Source**: OpenProject #384 — «S2: Доверие к заголовку X-Forwarded-For» (аудит кода #336, BR-003 «xff-first-element», порции P-37 / P-38)
**Status**: active (2026-10-24, OpenProject #384, аудит BR-003)
**Created**: 2026-10-24
**Last updated**: 2026-10-24
**Feature Key**: `client-ip-resolution`

## Что делает

Одно место в проекте (`karaoke-web/.../util/ClientIpResolver.kt`) решает, какой адрес
считать адресом источника запроса, и применяет к нему единое правило:

1. `X-Real-IP` — заголовок, который наш host-nginx **перезаписывает** значением
   `$remote_addr` (реальный адрес клиента); клиент подделать его не может;
2. **последний** непустой элемент `X-Forwarded-For` — fallback для прокси, которые не
   выставляют `X-Real-IP`: реальный адрес дописывается прокси в **конец** цепочки,
   клиентский префикс игнорируется;
3. `request.remoteHost` — TCP-peer запроса (dev-окружение без nginx перед приложением).

Правило используют rate-limit публичных картинок, IP-allowlist debug-endpoint'а,
rate-limit claim'ов share-ссылок, `client_ip_hash` в БД и аналитика событий.

## Зачем

Аудит кода #336 (находка P-37-03, BR-003 «xff-first-element») обнаружил, что адрес
источника брался из **первого** элемента `X-Forwarded-For`. Прод-nginx формирует этот
заголовок как `$proxy_add_x_forwarded_for`, то есть **дописывает** реальный адрес в
конец, сохраняя клиентский префикс. Клиент, приславший свой `X-Forwarded-For`,
получал:

- **новый bucket** на каждый запрос в `RateLimitInterceptor` — лимит 60/мин на
  `/api/public/song-picture/{id}` и `/api/public/song-vk-image/{id}` не срабатывал
  никогда (второй уровень anti-bot защиты снимался);
- **доступ** к `/api/public/debug/db`, если разрешённый адрес подставлен в префикс
  `X-Forwarded-For` (IP-allowlist `DebugDbAccessGuard`);
- **недостоверный** `client_ip_hash` в `tbl_song_share_sessions` и IP в аналитике
  `tbl_events` — след для расследований обесценивался; тот же адрес использовался как
  ключ rate-limit'а claim'ов share-ссылок, что размножало и bucket'ы, и записи.

Дополнительно правило было продублировано в двух местах (`ClientIpResolver` и
`RateLimitInterceptor.clientIp`), а журнал безопасности (аудит BR-011, OpenProject #357)
уже использовал другое, более узкое правило — `X-Real-IP` без XFF. Расхождение делало
«адрес источника» в проекте неоднозначным.

## Как работает

### Топология (прод, `deploy/prod-single-host`)

```text
клиент → host-nginx (443, sites-enabled/80to8897)
           ├─ proxy_set_header X-Real-IP $remote_addr;            ← перезаписывает клиентское значение
           └─ proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;   ← "<префикс клиента>, $remote_addr"
        → 127.0.0.1:8897 → контейнер karaoke-web
```

В контейнере TCP-peer — это docker-шлюз (`172.18.0.1`), реальный адрес клиента в нём
недоступен: единственный источник адреса — заголовки от нашего nginx. Порт приложения
в проде опубликован на `127.0.0.1` (`deploy/prod-single-host/docker-compose-web.yml`),
поэтому снаружи приложение недоступно напрямую — заголовки всегда ставит наш nginx.

### Правило

| Приоритет | Источник | Почему именно он |
|---|---|---|
| 1 | `X-Real-IP` | наш nginx **перезаписывает** его значением `$remote_addr` — клиентское значение не доходит до приложения; |
| 2 | последний непустой элемент `X-Forwarded-For` | прокси дописывает реальный адрес в конец цепочки; клиентский префикс игнорируется; |
| 3 | `request.remoteHost` | dev без nginx перед приложением (в контейнере — адрес docker-шлюза). |

Пустые/пробельные значения заголовков игнорируются; если адрес определить не удалось —
`"unknown"`.

### Потребители правила

| Потребитель | Как используется адрес | Что даёт подделка XFF |
|---|---|---|
| `RateLimitInterceptor` | ключ bucket'а `"$ip|$endpointName"` | была новая пара «IP + эндпоинт» на каждый запрос — лимит не срабатывал; теперь ключ не меняется |
| `DebugDbAccessGuard` | сравнение с `KARAOKE_WEB_DEBUG_DB_ALLOWED_IPS` | был обход allowlist'а; теперь адрес в allowlist недостижим подделкой |
| `SongShareLinkService.checkRateLimit` | ключ `"claim:$ip:$минута"` | был обход лимита claim'ов (10/мин) |
| `SongShareLinkService.clientIpHash` | SHA-256 от адреса в `tbl_song_share_sessions.client_ip_hash` | в БД попадал хэш подделанного значения |
| `MainController.doRegisterEvent` | `client_ip` в `tbl_events` | в аналитику попадал подделанный адрес |

### Исключение — журнал безопасности

`MainController.syncSourceAddress` и `PublicSettingsWebController.sourceAddress` берут
адрес **только** из `X-Real-IP` (фолбэк — `request.remoteHost`), без XFF: запись в журнале
безопасности не должна опираться на заголовок, который клиент контролирует хотя бы
частично. Это правило аудита BR-011 (OpenProject #357) и после правки BR-003 **не
изменено**.

### dev-окружение

Без nginx перед приложением заголовков нет — работает третий уровень правила
(`request.remoteHost`). В контейнере это адрес docker-шлюза, поэтому в dev все клиенты
делят один bucket rate-limit'а (это не регрессия — так было и раньше) и один адрес в
аналитике.

### Что не менялось

- конфигурация nginx (`deploy/prod-single-host/80to8897`, `deploy/web-server-deploy/deploy/80to8897`);
- поведение журнала безопасности (BR-011);
- схема БД и формат `client_ip_hash` (SHA-256 от `"ip:$ip:share-salt"` — прежний).

## Инварианты

1. Адрес источника **никогда** не берётся из первого элемента `X-Forwarded-For`
   (аудит BR-003, OpenProject #384).
2. `X-Real-IP` имеет приоритет над `X-Forwarded-For`: клиентское значение `X-Real-IP`
   до приложения не доходит (nginx перезаписывает его).
3. Если используется `X-Forwarded-For`, берётся **последний** непустой элемент.
4. Правило живёт в одном месте — `util/ClientIpResolver`; копий правила (как была в
   `RateLimitInterceptor.clientIp`) в проекте нет.
5. Подделка `X-Forwarded-For` не меняет: ключ bucket'а rate-limit'а, результат
   IP-allowlist'а debug-endpoint'а, `client_ip_hash` в БД и IP в аналитике.
6. Журнал безопасности (`source=` в `infra.*`) продолжает писать адрес **только** из
   `X-Real-IP` (BR-011, #357) — это более узкое правило, и оно намеренно не объединено
   с правилом `ClientIpResolver`.
7. Правило не бросает исключений: при отсутствии заголовков возвращается TCP-peer, при
   полном отсутствии данных — `"unknown"`.
8. При смене правила значения `client_ip_hash`, записанные ранее, становятся
   несопоставимыми с новыми (старые посчитаны от подделываемого адреса) — это ожидаемо,
   миграция не предусмотрена.

## Известные ловушки

- **`$proxy_add_x_forwarded_for` ДОПИСЫВАЕТ адрес, а не заменяет заголовок.** Поэтому
  «первый элемент XFF» — это всегда клиентский префикс, а не адрес клиента. Любая
  правка, которая снова возьмёт первый элемент, вернёт обход (регрессионные тесты
  `ClientIpResolverTest`, `RateLimitInterceptorTest`, `DebugDbAccessGuardTest`,
  `SongShareLinkClientIpHashTest` ловят это).
- **Второй nginx в цепочке — `karaoke-public` (порт 7907, только loopback).** Он
  проксирует `/api/public` и `/api/storage` в тот же контейнер `karaoke-web`, но
  выставляет только `X-Forwarded-For-Client` и **не** выставляет `X-Real-IP`
  (`deploy/prod-single-host/nginx_karaoke-public.conf`). Запросы, пришедшие этим
  путём (в проде — только с самого хоста), обрабатываются вторым уровнем правила
  (последний элемент XFF). Основной путь `/api/**` идёт через host-nginx
  (`location /api/`) и оба заголовка получает корректно.
- **`X-Real-IP` доверен только потому, что перед приложением стоит наш nginx.** В dev
  (порт опубликован на все интерфейсы) клиент может прислать любой `X-Real-IP` сам —
  это не security-граница, а dev-окружение. В проде порт приложения привязан к
  `127.0.0.1`, поэтому внешний клиент до приложения не доходит.
- **В контейнере TCP-peer — адрес docker-шлюза (`172.18.0.1`), а не адрес клиента.**
  Fallback `request.remoteHost` в проде без nginx дал бы адрес шлюза; поэтому nginx
  перед приложением обязателен.
- **Не «унифицировать» журнал безопасности с `ClientIpResolver`.** У resolver'а есть
  fallback на последний элемент XFF; у журнала — нет. Расхождение намеренное.
- **Правило меняет ключи rate-limit'а в памяти.** Бucket'ы in-memory и при деплое
  обнуляются — первую минуту после выката лимит считается заново (ожидаемо).
- **Старые строки `tbl_song_share_sessions.client_ip_hash`** посчитаны от адреса, который
  клиент мог подделать, — при расследованиях их нельзя сравнивать с новыми.

## Ссылки

- [`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/util/ClientIpResolver.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/util/ClientIpResolver.kt) — единое правило определения адреса источника
- [`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/RateLimitInterceptor.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/RateLimitInterceptor.kt) — rate-limit публичных картинок (ключ bucket'а)
- [`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/DebugDbAccessGuard.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/DebugDbAccessGuard.kt) — IP-allowlist debug-endpoint'а
- [`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt) — rate-limit claim'ов и `client_ip_hash`
- [`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt) — аналитика событий и журнал безопасности синхронизации
- [`karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/util/ClientIpResolverTest.kt`](../../karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/util/ClientIpResolverTest.kt) — регрессионные тесты правила
- [`karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/services/RateLimitInterceptorTest.kt`](../../karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/services/RateLimitInterceptorTest.kt) — подделка XFF не обходит лимит
- [`karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/services/DebugDbAccessGuardTest.kt`](../../karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/services/DebugDbAccessGuardTest.kt) — подделка XFF не открывает debug-endpoint
- [`karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkClientIpHashTest.kt`](../../karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkClientIpHashTest.kt) — подделка XFF не меняет `client_ip_hash`
- [`specs/336-code-audit/findings/P-37.md`](../../specs/336-code-audit/findings/P-37.md) — P-37-03 (BR-003), первичная находка
- [`specs/336-code-audit/findings/P-38.md`](../../specs/336-code-audit/findings/P-38.md) — «Сверки» №1, зеркала BR-003
- [`specs/336-code-audit/backlog.md`](../../specs/336-code-audit/backlog.md) — связанные локации пакета
- [`knowledge/domains/karaoke-web/domain.md`](../../knowledge/domains/karaoke-web/domain.md) — «Решение 5: Адрес источника запроса»
- [`knowledge/domains/karaoke-web/components/services-overview.md`](../../knowledge/domains/karaoke-web/components/services-overview.md) — потребители правила
- [`deploy/prod-single-host/80to8897`](../../deploy/prod-single-host/80to8897) — прод-nginx: `X-Real-IP` и `X-Forwarded-For`
- [`docs/features/public-settings-perimeter.md`](public-settings-perimeter.md) — тот же приём «`X-Real-IP` для журнала безопасности»
- [`docs/features/internal-secret-sync.md`](internal-secret-sync.md) — журнал безопасности синхронизации (BR-011)

## Связанные ADR

- ADR нет (изменение локальное: правило определения адреса источника).
- Конвенции: `knowledge/adr/local-0006-logging-and-error-handling-karaoke-web.md`
  (журналы безопасности — по адресу, который клиент подделать не может).
