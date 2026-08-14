# API Contracts: Изменения для site-traffic-anomaly-investigation

**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-08-14

Этот документ описывает изменения контрактов API для фичи 187. Включает:
1. Изменённые существующие контракты.
2. Новые контракты (debug endpoint).
3. nginx-конфиг (вне Kotlin, но контракт уровня HTTP).

---

## C1: `/api/public/authors-tiles` — изменение формата `authorPictureUrl`

### До фикса
```json
{
  "author": "Кино",
  "authorPictureUrl": "/api/public/picture?file=%D0%9A%D0%B8%D0%BD%D0%BE%2F%D0%9A%D0%B8%D0%BD%D0%BE.preview.author.png",
  "songCount": 142,
  "isSpecialOrder": false
}
```
- Фронт грузит тайл через `/api/public/picture?file=...` → 302 → `/minio/...` → Spring-контроллер задействован.

### После фикса
```json
{
  "author": "Кино",
  "authorPictureUrl": "/minio/karaoke/%D0%9A%D0%B8%D0%BD%D0%BE/%D0%9A%D0%B8%D0%BD%D0%BE.preview.author.png",
  "songCount": 142,
  "isSpecialOrder": false
}
```
- Фронт грузит тайл напрямую через nginx `/minio/...` → Spring-контроллер НЕ задействован.

### Обратная совместимость
- `GET /api/public/picture?file=<encoded>` остаётся работоспособным (FR-001, FR-019). Используется только legacy-кодом (если такой есть).
- Если в каком-то месте проекта всё ещё формируется URL `/api/public/picture?file=...` — оно продолжит работать через 302.

### Тест
```bash
# До фикса:
curl -s https://sm-karaoke.ru/api/public/authors-tiles | jq '.[0].authorPictureUrl'
"/api/public/picture?file=..."

# После фикса:
curl -s https://sm-karaoke.ru/api/public/authors-tiles | jq '.[0].authorPictureUrl'
"/minio/karaoke/..."
```

---

## C2: `/api/public/song-picture/{id}` — добавление rate-limit (FR-010)

### До фикса
- Возвращает `200 OK` с `image/png` (800×194).
- При 100 запросах в минуту с одного IP — каждый обрабатывается, CPU тратится на генерацию `BufferedImage`.

### После фикса
- Если за последние 60 секунд с одного IP уже было ≥ 60 запросов — возвращает `429 Too Many Requests` с заголовком `Retry-After: 60`.
- До 60 запросов в минуту — обычное поведение.

### Контракт ответа при rate-limit
```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
Retry-After: 60

{"error": "rate_limit_exceeded", "retryAfterSeconds": 60}
```

### Тест
```bash
# 70 запросов за минуту с одного IP — последние 10 должны быть 429:
for i in $(seq 1 70); do
  curl -s -o /dev/null -w "%{http_code}\n" "https://sm-karaoke.ru/api/public/song-picture/1"
done
# Ожидаем: 60 строк "200" + 10 строк "429"
```

---

## C3: `/api/public/song-vk-image/{id}` — добавление rate-limit (FR-010)

Аналогично C2, лимит 60 req/мин на IP.

### Тест
```bash
for i in $(seq 1 70); do
  curl -s -o /dev/null -w "%{http_code}\n" "https://sm-karaoke.ru/api/public/song-vk-image/1"
done
```

---

## C4: `/api/public/news/since` — server-side cache (FR-008)

### До фикса
- Каждый запрос делает `SELECT ... WHERE publish_at > since`.
- При 60 запросах в минуту от одного юзера — 60 SELECT.

### После фикса
- Первый запрос: SELECT + cache в `PollingCache` (TTL 60 сек, ключ = `(since, userId)`).
- Последующие запросы в течение 60 сек — из cache (нет SELECT в БД).
- Новый timestamp в `tbl_news` НЕ инвалидирует кэш автоматически (TTL-based invalidation). Это допустимо — пользователь увидит новость с задержкой ≤ 60 сек.

### Контракт (НЕ меняется)
- `GET /api/public/news/since?timestamp=<epoch_ms>`
- Ответ: `{"news": [...]}` или `{"news": []}` для анонимов (Pass 52).

### Тест
```bash
# Сравнить время ответа двух последовательных запросов:
TIMESTAMP=$(date +%s%3N)
time curl -s "https://sm-karaoke.ru/api/public/news/since?timestamp=$TIMESTAMP" > /dev/null
time curl -s "https://sm-karaoke.ru/api/public/news/since?timestamp=$TIMESTAMP" > /dev/null
# Первый — SELECT (медленнее), второй — cache (быстрее).
```

---

## C5: `/api/public/account/chat/unreadcount` — server-side cache (FR-008)

### До фикса
- Каждый запрос (polling 20 сек) для премиум-юзера делает `SELECT count(*) FROM tbl_site_chat_messages WHERE ...`.
- Для non-premium — short-circuit без DB (уже оптимизировано).

### После фикса
- Для премиум-юзера: первый запрос — SELECT + cache в `PollingCache` (TTL 10 сек).
- Последующие в течение 10 сек — из cache.
- Бейдж «новых сообщений» может лагать на ≤ 10 сек (приемлемо, polling и так 20 сек).

### Контракт (НЕ меняется)
- `GET /api/public/account/chat/unreadcount`
- Ответ: `{"count": <int>}` (для премиум), `{"count": 0}` (для non-premium).

### Тест
```bash
# С авторизованным премиум-юзером:
TOKEN="..."
time curl -s -H "Authorization: Bearer $TOKEN" "https://sm-karaoke.ru/api/public/account/chat/unreadcount"
time curl -s -H "Authorization: Bearer $TOKEN" "https://sm-karaoke.ru/api/public/account/chat/unreadcount"
```

---

## C6: `/api/public/share/heartbeat` — server-side cache (FR-008)

### До фикса
- Каждые 25 сек share-гость отправляет heartbeat → UPDATE в `tbl_song_share_sessions`.
- Не пишет в `tbl_events` (FR-009 уже выполнено).

### После фикса
- Первый heartbeat: UPDATE + cache.
- Последующие в течение 15 сек — no-op (возвращает 200 OK без UPDATE).
- TTL 15 сек + polling 25 сек = каждый 2-й запрос no-op.

### Контракт (НЕ меняется)
- `POST /api/public/share/heartbeat` с телом `{"sessionTokenHash": "<hash>"}`.
- Ответ: `{"ok": true}`.

### Тест
- В Network-логе увидеть: 1 UPDATE → несколько 200 OK → 1 UPDATE → ...

---

## C7: `/api/public/debug/db` — НОВЫЙ endpoint (FR-013)

### Контракт
- **Метод**: `GET`
- **Путь**: `/api/public/debug/db`
- **Доступ**: только из IP-allowlist (env `KARAOKE_WEB_DEBUG_DB_ALLOWED_IPS`, comma-separated).
- **Ответ**:
```json
{
  "pgActiveConnections": 12,
  "pgIdleConnections": 88,
  "pgMaxConnections": 100,
  "currentThreadCount": 45,
  "currentTomcatMaxThreads": 200,
  "sampledAt": 1723645200000
}
```

### Тест
```bash
# С IP из allowlist:
curl -s "https://sm-karaoke.ru/api/public/debug/db" | jq .

# С IP НЕ из allowlist:
curl -s -w "%{http_code}\n" "https://sm-karaoke.ru/api/public/debug/db"
# 403 Forbidden
```

### Безопасность
- По умолчанию `KARAOKE_WEB_DEBUG_DB_ALLOWED_IPS=""` — endpoint отключён (404 Not Found).
- На production настраивается через env (IP админа + IP сервера).
- На dev-pc можно включить через `KARAOKE_WEB_DEBUG_DB_ALLOWED_IPS=127.0.0.1,::1`.

---

## C8: nginx `/minio/` location — cache headers (FR-003/004/005)

### До фикса
```nginx
location /minio/ {
    proxy_pass http://minio:9000/;
    proxy_set_header Host $host;
}
```
- nginx проксирует ответы MinIO без изменения cache headers.
- MinIO возвращает `Cache-Control: no-cache` для статики по умолчанию.

### После фикса
```nginx
location /minio/ {
    proxy_pass http://minio:9000/;
    proxy_set_header Host $host;
    # FR-003: Cache-Control 24ч для успешных ответов
    expires 24h;
    add_header Cache-Control "public, max-age=86400";
    # FR-004: ETag / Last-Modified (пробрасываем из MinIO)
    add_header ETag $upstream_http_etag;
    # FR-005: 404 — короткий TTL
    error_page 404 = @minio_404;
}
location @minio_404 {
    add_header Cache-Control "public, max-age=300";
    return 404;
}
```

### Тест
```bash
curl -sI "https://sm-karaoke.ru/minio/karaoke/Кино/Кино.preview.author.png" | grep -iE "cache-control|etag|last-modified"
# Cache-Control: public, max-age=86400
# ETag: "abc123..."
# Last-Modified: ...
```

---

## C9: `KaraokeProperties` — новые env-переменные

### До фикса
- Не было переменных для sampling/dedup/rate-limit/retention.

### После фикса
- `KARAOKE_WEB_EVENTS_SAMPLING_ANON` (int, default 20).
- `KARAOKE_WEB_EVENTS_SAMPLING_LOGGED` (int, default 5).
- `KARAOKE_WEB_EVENTS_SAMPLING_ADMIN` (int, default 1).
- `KARAOKE_WEB_EVENTS_DEDUP_TTL_SECONDS` (long, default 30).
- `KARAOKE_WEB_EVENTS_RETENTION_DAYS` (long, default 7).
- `KARAOKE_WEB_DEBUG_DB_ENABLED` (bool, default false).
- `KARAOKE_WEB_DEBUG_DB_ALLOWED_IPS` (string, default "").
- `KARAOKE_WEB_RATE_LIMIT_SONG_PICTURE_PER_MINUTE` (int, default 60).
- `KARAOKE_WEB_RATE_LIMIT_SONG_VK_IMAGE_PER_MINUTE` (int, default 60).

### Тест
```bash
docker exec karaoke-web env | grep KARAOKE_WEB_EVENTS
# KARAOKE_WEB_EVENTS_SAMPLING_ANON=20
# KARAOKE_WEB_EVENTS_SAMPLING_LOGGED=5
# ...
```

---

## Обратная совместимость (FR-019)

| Контракт | Старый формат | Новый формат | Совместимость |
|---|---|---|---|
| `/api/public/authors-tiles` | `authorPictureUrl` через `/api/public/picture` | `authorPictureUrl` через `/minio/...` | ✅ Старый URL продолжает работать (FR-001) |
| `/api/public/song-picture/{id}` | `200 OK` | `200 OK` + `429` при rate-limit | ✅ Без rate-limit поведение не меняется |
| `/api/public/song-vk-image/{id}` | `200 OK` | `200 OK` + `429` при rate-limit | ✅ Без rate-limit поведение не меняется |
| `/api/public/news/since` | `200 OK` всегда из БД | `200 OK` из cache ≤ 60 сек | ✅ Ответ тот же, но может быть на ≤ 60 сек старше |
| `/api/public/account/chat/unreadcount` | `200 OK` всегда из БД | `200 OK` из cache ≤ 10 сек | ✅ Бейдж лагает ≤ 10 сек (приемлемо) |
| `/api/public/share/heartbeat` | `200 OK` всегда | `200 OK` иногда без UPDATE | ✅ Семантически эквивалентно |
| `/api/public/debug/db` | N/A | Новый endpoint | ✅ По умолчанию отключён |
| `doRegisterEvent` | INSERT всегда | INSERT по sampling/dedup | ✅ INSERT пропускается только для CALL_REST |

---

## Несовместимые изменения

**Нет**. Все изменения либо:
- backward-compatible (старый URL продолжает работать).
- additive (новый endpoint, новые env-переменные).
- behavioral с явным улучшением (rate-limit защищает от ботов, cache улучшает latency).

---

## См. также

- [spec.md](./spec.md) — функциональные требования.
- [plan.md](./plan.md) — имплементационный план.
- [research.md](./research.md) — детальный аудит и обоснование решений.
- [data-model.md](./data-model.md) — описание новых entities.
- [quickstart.md](./quickstart.md) — ручные сценарии.
- [CONTRACT-CHECK.md](./CONTRACT-CHECK.md) — checklist обратной совместимости.
