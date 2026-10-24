# Отчёт по #384 — BR-003 «xff-first-element»: адрес источника запроса

> **Ветка**: `574-pkg-sec-xff` (worktree `.worktrees/waveC-xff`, от `master` 4b5f16a7) |
> **Пакет**: `PKG-SEC-XFF` (волна C, S2) | **Дата**: 2026-10-24 |
> **Аудит**: #336, порции P-37 (P-37-03) / P-38 («Сверки» №1), BR-003.

## Что было

Адрес источника запроса на `karaoke-web` определялся по **первому** элементу
`X-Forwarded-For` — ровно тому, который контролирует клиент. Прод-nginx формирует
заголовок как `$proxy_add_x_forwarded_for`, то есть **дописывает** реальный адрес в
конец и сохраняет клиентский префикс (`deploy/prod-single-host/80to8897:41,55,94,124`).

Последствия (все локации пакета):

| Локация | Симптом |
|---|---|
| `RateLimitInterceptor.clientIp` (своя копия правила) | новый bucket на каждый подделанный XFF — лимит 60/мин на `/api/public/song-picture/{id}` и `/api/public/song-vk-image/{id}` не срабатывал никогда |
| `ClientIpResolver.resolve` | то же правило — от него зависели остальные потребители |
| `DebugDbAccessGuard.isAllowed` | подстановка разрешённого адреса в префикс XFF открывала `/api/public/debug/db` (endpoint по умолчанию выключен) |
| `SongShareLinkService.checkRateLimit` | обход лимита claim'ов (10/мин) — плюс «свежие» ключи `"claim:$ip:$минута"` |
| `SongShareLinkService.clientIpHash` | в `tbl_song_share_sessions.client_ip_hash` писался хэш подделанного адреса — след для расследований обесценивался |
| `MainController.doRegisterEvent` | подделанный IP в аналитике `tbl_events` |

Правило было продублировано в двух местах, а журнал безопасности (BR-011, #357) уже
использовал другое, более узкое правило — `X-Real-IP` без XFF.

## Что изменено

| Файл | Что |
|---|---|
| `karaoke-web/.../util/ClientIpResolver.kt` | **Единое правило определения адреса источника**: 1) `X-Real-IP` (наш nginx перезаписывает его значением `$remote_addr` — клиент подделать не может); 2) **последний** непустой элемент `X-Forwarded-For` (прокси дописывает реальный адрес в конец цепочки, клиентский префикс игнорируется); 3) `request.remoteHost` (dev без nginx; в контейнере — адрес docker-шлюза). Пустые значения игнорируются, при полном отсутствии данных — `"unknown"`. Правило вынесено в чистую функцию `resolve(xRealIp, xForwardedFor, remoteHost)` для юнит-тестов. KDoc — с топологией прод-nginx и обоснованием приоритетов. |
| `karaoke-web/.../services/RateLimitInterceptor.kt` | Своя копия правила («первый элемент XFF») **удалена** — адрес берётся из `ClientIpResolver`. KDoc интерсептора дополнен правилом источника адреса. |
| `karaoke-web/.../services/DebugDbAccessGuard.kt` | Логика не менялась (уже `ClientIpResolver`); KDoc дополнен источником адреса. |
| `karaoke-web/.../services/SongShareLinkService.kt` | Логика не менялась (уже `ClientIpResolver`); `checkRateLimit` и `clientIpHash` переведены на короткий импорт, `clientIpHash` сделан `internal` (видимость в пределах модуля — как у `toMskLocalDateTime` в этом же файле), чтобы тест мог проверить инвариант напрямую. |
| `karaoke-web/.../controllers/MainController.kt` | **Поведение `syncSourceAddress` не менялось** (журнал безопасности по-прежнему пишет `source=` только из `X-Real-IP`, BR-011/#357) — обновлён только устаревший комментарий («его правка — отдельная задача BR-003»). |
| `karaoke-web/.../controllers/PublicSettingsWebController.kt` | То же: правило `sourceAddress` (только `X-Real-IP`) сохранено, обновлён комментарий. |
| `karaoke-web/src/test/.../util/ClientIpResolverTest.kt` (NEW) | 9 регрессионных тестов правила: подделка XFF не меняет источник; fallback — последний элемент XFF; приоритет `X-Real-IP`; список в `X-Real-IP`; dev-фолбэк на TCP-peer; пустые заголовки; пустые элементы внутри XFF; `"unknown"`; разные клиенты → разные адреса. |
| `karaoke-web/src/test/.../services/RateLimitInterceptorTest.kt` (NEW) | 3 теста: подделка XFF не обходит лимит (429) и не плодит bucket'ы; разные реальные клиенты — разные bucket'ы. |
| `karaoke-web/src/test/.../services/DebugDbAccessGuardTest.kt` (NEW) | 4 теста: разрешённый адрес проходит; подделка XFF **не** даёт доступ; выключенный флаг и пустой allowlist закрывают всех. |
| `karaoke-web/src/test/.../services/SongShareLinkClientIpHashTest.kt` (NEW) | 3 теста: подделка XFF не меняет `client_ip_hash`; разные клиенты — разные хэши; «честный» и «подделанный» запросы дают одинаковый хэш. |
| `docs/features/client-ip-resolution.md` (NEW) | Per-feature документ (FR-009): правило, топология, потребители, исключение для журнала безопасности, инварианты, ловушки. |
| `knowledge/domains/karaoke-web/domain.md` | Новое «Решение 5: Адрес источника запроса» + `ClientIpResolver` в Ubiquitous Language + Changelog. |
| `knowledge/domains/karaoke-web/components/services-overview.md` | Правило источника адреса в разделе «Rate limit»; новый раздел «Адрес источника запроса (ClientIpResolver)»; Changelog. |
| `knowledge/domains/karaoke-web/components/main-controller.md` | Уточнено правило для `doRegisterEvent`; отмечено, что правило журнала безопасности не менялось; Changelog. |
| `knowledge/domains/karaoke-web/components/internal-controllers.md` | Уточнено правило для `DebugDbAccessGuard` и `doRegisterEvent`; Changelog. |

**Не менялось**: конфигурация nginx, поведение журнала безопасности (BR-011/#357),
схема БД и формат `client_ip_hash` (`SHA-256("ip:$ip:share-salt")` — прежний),
конфигурация rate-limit'ов.

## «До/после» (регрессионный тест)

Тест временно откатывался на старое правило (первый элемент `X-Forwarded-For`) —
правка в `ClientIpResolver.resolve` была заменена на
`firstToken(xForwardedFor) → firstToken(xRealIp)`, после чего возвращена обратно.

**ДО** (старое правило):

```
$ GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:test \
    --tests "com.svoemesto.karaokeweb.util.ClientIpResolverTest" \
    --tests "com.svoemesto.karaokeweb.services.RateLimitInterceptorTest" \
    --tests "com.svoemesto.karaokeweb.services.DebugDbAccessGuardTest" \
    --tests "com.svoemesto.karaokeweb.services.SongShareLinkClientIpHashTest"

> Task :karaoke-web:test FAILED
DebugDbAccessGuardTest > spoofedForwardedForCannotGrantAccessToDebugEndpoint() FAILED
DebugDbAccessGuardTest > allowedClientIsAllowedDespiteSpoofedForwardedFor() FAILED
RateLimitInterceptorTest > spoofedForwardedForDoesNotBypassRateLimit() FAILED
RateLimitInterceptorTest > spoofedForwardedForDoesNotCreateNewBuckets() FAILED
SongShareLinkClientIpHashTest > clientIpHashIgnoresSpoofedForwardedForPrefix() FAILED
SongShareLinkClientIpHashTest > clientIpHashUsesRealIpNotSpoofedEntry() FAILED
ClientIpResolverTest > forwardedForFallbackUsesLastElement() FAILED
ClientIpResolverTest > emptyElementsInsideForwardedForAreSkipped() FAILED
ClientIpResolverTest > spoofedForwardedForDoesNotChangeSource() FAILED
ClientIpResolverTest > realIpPreferredOverForwardedFor() FAILED
19 tests completed, 10 failed
BUILD FAILED
```

Ключевые сообщения падения: «подделка X-Forwarded-For не должна давать доступ к
debug-endpoint'у ==> expected: <false> but was: <true>», «подделанный префикс
X-Forwarded-For не должен обходить лимит ==> expected: <false> but was: <true>»,
«подделанный префикс X-Forwarded-For не должен плодить bucket'ы ==> expected: <1> but
was: <5>».

**ПОСЛЕ** (новое правило):

```
$ GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:test \
    --tests "com.svoemesto.karaokeweb.util.ClientIpResolverTest" \
    --tests "com.svoemesto.karaokeweb.services.RateLimitInterceptorTest" \
    --tests "com.svoemesto.karaokeweb.services.DebugDbAccessGuardTest" \
    --tests "com.svoemesto.karaokeweb.services.SongShareLinkClientIpHashTest"

BUILD SUCCESSFUL
```

Тесты: `ClientIpResolverTest` — 9, `RateLimitInterceptorTest` — 3,
`DebugDbAccessGuardTest` — 4, `SongShareLinkClientIpHashTest` — 3 (всего 19, 0 падений).

## Проверки

| Проверка | Результат |
|---|---|
| `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:test` | **PASS** — 98 тестов (17 наборов), 0 failures, 0 errors |
| `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test` | **PASS** — 448 тестов (51 набор), 0 failures, 0 errors (прогон без build-cache) |
| `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew ktlintCheck` | **PASS** — все модули, 0 новых нарушений |
| `bash tools/check-knowledge-structure.sh` | **PASS** — 9/9 проверок |
| `python3 tools/lint-knowledge.py` | **PASS** — «All documentation passes architectural style and structural checks» |
| `python3 tools/check-doc-references.py` | **PASS** — битых ссылок/путей/эндпоинтов нет (4118 ссылок, 2240 путей, 313 эндпоинтов) |
| `bash tools/check-feature-doc.sh docs/features/client-ip-resolution.md` | **PASS** |
| `bash tools/check-subagent-isolation.sh` | **PASS** (после коммита — «все commits в ветке уникальны, race condition нет») |
| `bash tools/check-audit-coverage.sh` | **FAIL — предсуществующий дефект, не связан с правкой**: `EventsBuffer.kt` не упомянут в `specs/336-code-audit/research.md`. Тот же FAIL воспроизводится на `master` 4b5f16a7 (проверено в `/home/nsa/Karaoke`) |
| `pre-commit run --files <изменённые файлы>` | **PASS** — все хуки (ktlint, lychee, feature-doc, doc-references, guards) |

## Что осталось за владельцем

- **Выкатка на прод и проверка вживую** — не выполнялась (запрет на деплой в задании).
  После деплоя имеет смысл убедиться, что rate-limit на публичных картинках и
  IP-allowlist debug-endpoint'а работают с реальным трафиком (значения `X-Real-IP`
  в логах nginx совпадают с адресом клиента).
- **`X-Real-IP` в проде не изменится** — правило полностью совпадает с тем, что nginx
  уже выставляет; данные в БД (`client_ip_hash`) не требуют миграции, но **старые
  строки** посчитаны от подделываемого адреса и несопоставимы с новыми (зафиксировано
  в per-feature документе).
- **Отдельная задача BR-516 (#378)** — рост карты `claimRateBuckets` (без TTL-очистки).
  Наша правка убирает подделку как источник новых ключей, но саму очистку по TTL
  делает BR-516 — в этот пакет не входит.
- **Закрытие тикета** — по workflow трекера закрытие делает владелец после ревью.
  Статус #384 сейчас «In review», комментарий с этим отчётом добавлен (id 1324);
  push и PR не выполнялись (запрет в задании) — это за лидом.
- Второй nginx в цепочке (`karaoke-public`, порт 7907, только loopback) выставляет
  `X-Forwarded-For-Client` и **не** выставляет `X-Real-IP`; запросы, пришедшие этим
  путём (в проде — только с самого хоста), обрабатываются вторым уровнем правила.
  Зафиксировано в per-feature документе; отдельного решения не требует.

## Коммиты

- `119cf1c316fb1356144b994c955ae78c76583b27` — `fix(karaoke-web): BR-003 — адрес
  источника по X-Real-IP / последнему XFF` (код, тесты, knowledge, per-feature документ);
  ветка `574-pkg-sec-xff` (от `master` 4b5f16a7), без push и PR — по условиям задания.
- `4ea9f1fc` — `docs(384): отчёт по BR-003` (этот файл).
- `bd30799f` — `specs(336): статус BR-003 → «исправлено»` (registry.csv + карточки
  P-37 / P-38).
- Последний коммит ветки — уточнение хэшей в этом отчёте (его хэш — `git log -1` в
  `574-pkg-sec-xff`).
