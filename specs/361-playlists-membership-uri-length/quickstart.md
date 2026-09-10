# Quickstart: 414 → POST /playlists/membership

> **Date**: 2026-09-10
> **Spec**: [spec.md](./spec.md)
> **Issue**: OpenProject #77

Пошаговое руководство для ручной проверки фикса на локальном
окружении + на проде (после deploy). Соответствует SC-001..SC-006 из
spec.md.

## Prerequisites

- Запущенный `karaoke-web` + `karaoke-public` (локально или на проде)
- Доступ к БД (`karaoke-db`) — для проверки membership-данных
- Браузер с DevTools
- JWT-токен залогиненного пользователя (для curl-проверок)
- Доступ к nginx-конфигурации прокси (для проверки лимитов)

## Шаг 0 — Воспроизвести баг (опционально, для sanity check)

**Цель**: убедиться, что баг реально воспроизводится **до** фикса.

1. Открыть DevTools Network.
2. Залогиниться на `https://localhost:7907` (или прод).
3. Перейти на `/zakroma?author=Машина Времени` (или любой автор с
   ≥ 1500 песен).
4. Отфильтровать Network по `playlists/membership`.
5. **Ожидаемый ответ ДО фикса**:
   - GET `/api/public/account/playlists/membership?ids=22982,...` (≈ 2500 id, URL ≈ 12 КБ)
   - HTTP Status: **414 Request-URI Too Large**
   - Иконки избранного/плейлистов — нейтральные (outline), не активные.

Если баг НЕ воспроизводится (URL помещается в 8 КБ), спека неприменима
к этому автору — попробовать другого (например, крупные сборники типа
«Лучшие песни 90х»).

## Шаг 1 — Проверить, что фикс работает на локальной машине

### 1.1 Backend

```bash
# Из корня проекта (на nsa-i9 — Linux-контейнер)
cd /home/nsa/Karaoke

# Собрать backend
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:bootJar --parallel

# Перезапустить контейнер (только на nsa-i9 — разрешено)
cd deploy && bash do.sh build_webvue3   # пересоберёт образ karaoke-web
# Если не пересобирать karaoke-app — только web:
# docker restart karaoke-web
```

### 1.2 Frontend

```bash
# Karaoke-public (Vue 3 SPA)
cd karaoke-public
npm run dev   # для разработки
# или
npm run build && cd ../deploy && bash do.sh build_public  # для сборки в Docker
```

### 1.3 Sanity check через curl

```bash
# Получить JWT (например, через /api/public/auth/login)
JWT="<ваш JWT токен>"

# Тест POST (рекомендуемый)
curl -X POST http://localhost:7907/api/public/account/playlists/membership \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{"ids":[22982,22983,22984]}'

# Ожидаемый ответ:
# {"items":{"22982":{"favorited":false,"playlistIds":[]}, ...}}

# Тест GET (backward-compat — должен работать)
curl -X GET "http://localhost:7907/api/public/account/playlists/membership?ids=22982,22983" \
  -H "Authorization: Bearer $JWT"

# Ожидаемый ответ:
# {"items":{"22982":{"favorited":false,"playlistIds":[]}, ...}}
```

### 1.4 Проверить в браузере

1. Открыть `/zakroma?author=Машина Времени` (или крупный автор).
2. В DevTools Network найти запрос к `playlists/membership`:
   - **Method**: POST (не GET)
   - **Status**: 200
   - **Payload**: `{"ids":[...]}` (JSON, в body)
   - **Size**: ~20 КБ request body
3. Иконки избранного/плейлистов — сразу финальные, без спиннеров.
4. Переключиться на другого крупного автора — membership обновляется
   без утечек от предыдущего (паттерн `requestId/latest` уже работает).

## Шаг 2 — Проверить nginx limits (на проде)

### 2.1 Проверить `large_client_header_buffers`

```bash
ssh <PROD_SERVER>
nginx -T 2>/dev/null | grep -A1 'large_client_header_buffers'
```

Ожидаемый результат: `large_client_header_buffers 4 8k;` (default).
Если больше — зафиксировать.

### 2.2 Проверить `client_max_body_size`

```bash
ssh <PROD_SERVER>
nginx -T 2>/dev/null | grep 'client_max_body_size'
```

Ожидаемый результат: либо отсутствует (= default `1m`), либо ≥ 1m.
Если меньше 32 КБ — добавить в `deploy/nginx.conf`:

```nginx
client_max_body_size 1m;   # default, должен быть OK
```

И сделать `nginx -t && nginx -s reload` (см. AGENTS.md § nginx reload).

### 2.3 Проверить фактический POST с большим payload

```bash
# Сгенерировать JSON с 5000 id
python3 -c "import json; print(json.dumps({'ids': list(range(22982, 27982))}))" > /tmp/big.json

curl -X POST https://karaoke.svoemesto.ru/api/public/account/playlists/membership \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  --data @/tmp/big.json

# Ожидаемый ответ: HTTP 200 + корректный JSON (или 401 если токен протух).
```

## Шаг 3 — Проверить observability

### 3.1 Метрики в логах

```bash
# Найти логи за последний час с тегом membership
docker logs karaoke-web 2>&1 | grep 'infra.prod.api.membership' | tail -20
```

Ожидаемый результат:
- `infra.prod.api.membership.post user=<id> ids=2500` — для POST-вызовов
- `infra.prod.api.membership.get user=<id> ids=42` — для legacy GET (если кто-то ещё использует)

### 3.2 Проверить счётчики (если есть Prometheus)

```bash
curl http://karaoke-web:8080/actuator/prometheus 2>/dev/null | grep -E 'membership_(get|post)_calls_total'
```

(Если actuator не настроен — игнорируем, log-based метрики достаточно.)

## Шаг 4 — Проверить regression на анонимах

1. Открыть инкогнито-окно.
2. Перейти на `/zakroma?author=Машина Времени`.
3. В DevTools Network проверить: **нет** запросов к
   `/api/public/account/*` (membership-fetch skip'нут для анонимов по
   FR-009 спеки #239).
4. Иконки избранного/плейлистов — «гостевые» (серая ★, серая ▶|).
5. Клик по «гостевой» иконке → редирект на `/login?redirect=...`.

## Шаг 5 — Убедиться, что старый GET не сломан

```bash
# Проверить, что GET с маленькими ids всё ещё работает
curl -X GET "http://localhost:7907/api/public/account/playlists/membership?ids=1,2,3" \
  -H "Authorization: Bearer $JWT"

# Ожидаемый ответ: HTTP 200 + корректный JSON
```

Если 405/404 — регрессия, немедленно откатывать.

## Шаг 6 — Проверить обновление документации

После merge убедиться, что:

1. `knowledge/domains/karaoke-web/components/public-controllers-6.md`
   содержит упоминание POST метода.
2. `knowledge/system/frontend/composable-playlist-membership.md`
   упоминает POST в `fetchMembership`.
3. `knowledge/adr/0009-get-vs-post-large-payload.md` создан.
4. `docs/features/playlist-membership.md` создан или обновлён
   (FR contract + curl example).
5. `docs/architecture-notes.md` содержит запись о PR.

## Чек-лист соответствия Success Criteria

| SC | Проверка | Ожидаемый результат |
|---|---|---|
| SC-001 | DevTools Network на крупном авторе залогиненным | 0 запросов с 4xx/5xx для membership |
| SC-002 | Время отрисовки ≤ 5 сек | Замерить в Performance tab |
| SC-003 | GET с маленькими ids | HTTP 200 (regression check) |
| SC-004 | 2500 строк без спиннеров | Визуально + Network tab |
| SC-005 | Метрика POST/GET ratio | POST ≥ 95%, GET < 5% |
| SC-006 | Премиум видит премиум-плейлисты | Ручная проверка |

## Откат (rollback)

Если после deploy что-то сломалось:

```bash
# 1. Откатить PR через gh pr revert
gh pr revert <PR_NUMBER>

# 2. Пересобрать + перезапустить
cd deploy && bash do.sh build_webvue3

# 3. Проверить
docker logs --tail 50 karaoke-web | grep -E 'error|exception' | head -20
```

Старый GET-endpoint **НЕ** удаляется в этом PR (см. spec.md US3),
поэтому откат безопасен: фронт просто продолжит использовать POST,
бэк продолжит работать как раньше.

## Done When

- [ ] POST возвращает 200 для 2500 id
- [ ] GET возвращает 200 для маленьких ids (backward-compat)
- [ ] В браузере на крупном авторе иконки отрисовываются без 414
- [ ] Аноним не делает membership-запрос (FR-009 спеки #239)
- [ ] В логах видна метрика `infra.prod.api.membership.post`
- [ ] nginx `client_max_body_size` подтверждён ≥ 32 КБ
- [ ] Knowledge обновлён (4 файла + новый ADR)
- [ ] docs/features/playlist-membership.md создан/обновлён