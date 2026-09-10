# ADR-0009: GET → POST для больших CSV-payload в membership-endpoint

> **Date**: 2026-09-10
> **Status**: Accepted
> **Context**: Spec #361 / OpenProject #77 (414 Request-URI Too Large)
> **Supersedes**: — (нет)
> **Superseded by**: — (нет)

## Context

Endpoint `GET /api/public/account/playlists/membership?ids=<csv>` на
крупных авторах (≈2500+ песен) превышал **8 КБ** HTTP request-line
лимит nginx (`large_client_header_buffers 4 8k`). Браузер получал
`414 Request-URI Too Large`, и membership-карта не доходила до фронта →
иконки избранного/плейлистов оставались в нейтральном состоянии.
Прецедент: OpenProject #77, «Машина Времени» с ≈2500 песнями.

Endpoint уже использовал bulk-fetch (Pass 239, specs/239) — переход от
per-row запросов к одному CSV. Но bulk-fetch через query-string упёрся
в инфраструктурный лимит nginx.

## Decision

**Добавить новый `POST /api/public/account/playlists/membership` с
JSON-телом `{"ids": number[]}`. Старый GET сохранить для
backward-compat.**

### Альтернативы рассмотрены и отклонены

1. **Увеличить nginx `large_client_header_buffers`** (до 64 КБ и выше).
   Не помогает для HTTP/1.1 RFC: многие прокси/CDN имеют свой лимит
   (Cloudflare = 8 КБ, Akamai = 8 КБ default). Сайт может оказаться
   за другим прокси в будущем. **Решает симптом, не причину.**

2. **Chunked GET** (несколько запросов по 500 id). Уже пробовали в
   спеке #239 до bulk-fetch — фрагментировали запросы и сайт «лежал»
   от N×3 фоновых вызовов. **Bulk-fetch через POST делает это одним
   запросом** без фрагментации.

3. **GraphQL**. Никогда не использовался в проекте. Архитектурный
   overkill для одной операции.

4. **`@RequestParam` form-encoded** (как у существующих POST в
   `PublicPlaylistController`). Возможный компромисс, но: менее
   идиоматично для больших payloads, Spring `@RequestParam` имеет
   лимит на количество параметров, URL-encoding `%2C` ломает
   читаемость. **JSON — стандарт для HTTP API.**

5. **`multipart/form-data`** с повторяющимся `ids`. Самый гибкий, но:
   сложнее для бэка, фронт уже использует `authUpload` для файлов
   (naming collision). Overkill.

## Consequences

### Положительные

- ✅ Решает баг #77 — крупные авторы работают.
- ✅ Стандартный REST-паттерн (POST для больших payloads) — общепринято.
- ✅ JSON-body тестируется проще, чем CSV (`curl -d '{"ids":[1,2,3]}'`
  vs `curl -d 'ids=1,2,3'` с правильным URL-encoding).
- ✅ Backward-compat сохранён — старый GET продолжает работать.

### Отрицательные

- ❌ Два эндпоинта для одной операции (рост API surface).
  Компенсация: GET помечен DEPRECATED в JSDoc, в планах — удалить
  через 1-2 месяца после стабилизации POST.

- ❌ POST менее cachable на стороне браузера/прокси (HTTP-метод не
  идемпотентный). Компенсация: `/api/public/account/*` — private
  endpoints за JWT, не cachable в принципе.

- ❌ Требует CSRF-защиты для cookie-based сессий. В нашем проекте
  аутентификация через JWT в `Authorization: Bearer` header
  (см. `SiteAuthInterceptor.kt:25`) — **CSRF невозможен by design**.
  Если проект когда-нибудь перейдёт на cookie — нужно будет
  пересмотреть (добавить CSRF-token middleware).

- ❌ Тело POST парсится (JSON → DTO), что чуть медленнее, чем GET
  с query-string. Компенсация: на 2500 id время парсинга ~1 мс —
  ничтожно по сравнению с сетевой задержкой и SQL-запросом.

## Compliance

- ✅ **Constitution Principle II** (raw JDBC): SQL без изменений,
  переиспользуется существующий `SitePlaylistItem.songIdsInPlaylists(...)`.
- ✅ **Constitution Principle VIII** (secrets): без новых секретов.
- ✅ **Constitution Principle IX** (Knowledge-first): pre-flight в
  spec.md § Knowledge References.

## Backward-compat стратегия

| Период | GET | POST |
|---|---|---|
| **Сейчас** (Pass 361) | Работает, DEPRECATED-предупреждение в JSDoc | Работает, основной |
| **+1 месяц** | Работает, WARN-лог на каждый вызов | Основной |
| **+2-3 месяца** | Можно удалить (если `membership_get_calls_total` < 1%) | Основной |

Решение об удалении GET принимается владельцем по метрике.

## Changelog

- **2026-09-10** (Pass 361): Initial. Автор: agent (Karaoke).
- Связанные спеки: [specs/239](../specs/239-zakroma-author-songs-batch-render/spec.md),
  [specs/361](../specs/361-playlists-membership-uri-length/spec.md).
- Связанный Issue: OpenProject #77.