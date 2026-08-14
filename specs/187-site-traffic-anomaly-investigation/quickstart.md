# Quickstart: Валидация фикса site-traffic-anomaly-investigation

**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-08-14

## Цель

Подтвердить end-to-end, что после фичи:
1. `/zakroma` не нагружает Spring-контроллер `/api/public/picture` (SC-001).
2. Браузер кеширует картинки авторов (SC-002).
3. `tbl_events` получает значительно меньше INSERT'ов (SC-003).
4. `tbl_events` стабилен по размеру (SC-005).
5. Нет 7-10 мин инцидентов при 100 пользователях × 3 вкладки (SC-006).
6. Загрузка `/zakroma` ≤ 4 сек FCP (SC-007).
7. Боты получают rate-limit 429 на `/song-picture` / `/song-vk-image` (SC-008).
8. Debug endpoint возвращает корректные pg-метрики (SC-009 / FR-013).

## Предусловия

- Docker-стек запущен: `deploy/do.sh start` (karaoke-db, karaoke-web, webvue3, karaoke-public, MinIO, nginx).
- В БД есть как минимум:
  - **200 авторов** (для теста `/zakroma` со всеми тайлами).
  - **1 крупный автор** с ≥ 100 песен (для теста SC-007).
- В MinIO есть превью-картинки для всех авторов (стандартная поставка).
- Доступ к `https://localhost:8897` (или соответствующий URL окружения).
- DevTools открыты (для Network/Performance).

## Подготовка

### Скрипт для проверки URL формата

```bash
# Сохранить в файл:
curl -s https://localhost:8897/api/public/authors-tiles | jq '.[0].authorPictureUrl'
```

**До фикса (FAIL)**:
```
"/api/public/picture?file=%D0%9A%D0%B8%D0%BD%D0%BE%2F..."
```

**После фикса (PASS)**:
```
"/minio/karaoke/%D0%9A%D0%B8%D0%BD%D0%BE/%D0%9A%D0%B8%D0%BD%D0%BE.preview.author.png"
```

---

## Сценарий 1: `/zakroma` не нагружает Spring (SC-001)

**Предусловия**: чистый кеш браузера, открыть DevTools → Network → фильтр `/api/public/picture`.

**Шаги**:
1. Открыть `/zakroma` анонимно.
2. Дождаться полной загрузки сетки тайлов.
3. В DevTools → Network проверить: число запросов на `/api/public/picture`.

**Ожидаемый результат (PASS)**:
- **0 запросов** на `/api/public/picture`.
- 200+ запросов на `/minio/karaoke/...` (каждый тайл напрямую через nginx).

**До фикса (FAIL)**:
- 200+ запросов на `/api/public/picture?file=...`.

---

## Сценарий 2: HTTP-кеш картинок (SC-002)

**Предусловия**: страница `/zakroma` уже загружена (из сценария 1).

**Шаги**:
1. Открыть DevTools → Network → сохранить HAR.
2. Нажать F5.
3. В DevTools проверить `Transfer-Size` для запросов к `/minio/karaoke/...`.

**Ожидаемый результат (PASS)**:
- Все запросы `/minio/...` имеют `Transfer-Size: 0` (из дискового кеша).
- Или `304 Not Modified` с пустым телом.

**До фикса (FAIL)**:
- Все запросы идут заново, `Transfer-Size` > 0 (полный PNG каждый раз).

---

## Сценарий 3: Меньше INSERT'ов в tbl_events (SC-003)

**Предусловия**: 30 минут обычной нагрузки (открыть 5 вкладок как залогиненный пользователь).

**Шаги**:
1. Открыть 5 вкладок как залогиненный пользователь.
2. Подождать 5 минут (polling: news 45 сек, chat 20 сек, share 25 сек, auth/me 5 мин).
3. В SQL выполнить:
   ```sql
   SELECT count(*) FROM tbl_events
   WHERE event_type='CALL_REST'
     AND last_update > now() - interval '1 minute';
   ```
4. Повторить через 60 сек, ещё раз через 60 сек.

**Ожидаемый результат (PASS)**:
- Число INSERT'ов от `CALL_REST` за 5 минут **≤ 25 строк** (rate 1/5 для залогиненных × polling).
- При sampling rate=1/5 + dedup 30 сек: 5 вкладок × (45+20+25+300) сек polling × rate=1/5 = 5 × ~390 сек / 5 = 390 INSERT'ов за 5 минут. Это выше, но с dedup должно быть значительно меньше.

**До фикса (FAIL)**:
- Десятки-сотни INSERT'ов в минуту от тех же 5 вкладок.

---

## Сценарий 4: Размер tbl_events стабилен (SC-005)

**Предусловия**: подождать 8 дней после деплоя (retention scheduler работает раз в сутки).

**Шаги**:
1. Выполнить:
   ```sql
   SELECT pg_size_pretty(pg_total_relation_size('tbl_events'));
   SELECT count(*) FROM tbl_events;
   ```
2. Подождать ещё 24 часа.
3. Повторить запросы.

**Ожидаемый результат (PASS)**:
- Размер стабилен (колеблется в пределах ±10% от среднего).
- Размер ≈ (rate INSERT/день) × 7 дней.

**До фикса (FAIL)**:
- Размер растёт линейно день ото дня.

---

## Сценарий 5: Нет 7-10 мин инцидентов под нагрузкой (SC-006)

**Предусловия**: wrk установлен. Локально или на dev-pc.

**Шаги**:
```bash
# 50 параллельных клиентов, 30 минут:
wrk -t50 -c50 -d30s --latency "https://localhost:8897/api/public/authors-tiles"
wrk -t50 -c50 -d30s --latency "https://localhost:8897/api/public/zakroma"
wrk -t50 -c50 -d30s --latency "https://localhost:8897/api/public/stats"
```

**Ожидаемый результат (PASS)**:
- p99 latency ≤ 500 мс.
- 0 ошибок (5xx).
- 0 задержек > 1 сек на запрос.

**До фикса (FAIL)**:
- p99 latency может быть 1000+ мс при высокой нагрузке.
- Возможны 5xx ошибки при exhaustion `pg_max_connections`.

---

## Сценарий 6: Загрузка `/zakroma` ≤ 4 сек FCP (SC-007)

**Предусловия**: чистый кеш. Network throttling: 10 Mbps / 50 ms RTT.

**Шаги**:
1. Chrome DevTools → Network → изменить throttling на «Slow 3G» или custom 10 Mbps / 50 ms.
2. Открыть `/zakroma`.
3. Performance → First Contentful Paint.

**Ожидаемый результат (PASS)**:
- FCP ≤ 4 сек.

**До фикса (FAIL)**:
- FCP 5-7 сек (из-за 200+ 302-редиректов через Spring).

---

## Сценарий 7: Rate-limit на `/song-picture` (SC-008)

**Шаги**:
```bash
for i in $(seq 1 70); do
  curl -s -o /dev/null -w "%{http_code}\n" \
    "https://localhost:8897/api/public/song-picture/1"
done | sort | uniq -c
```

**Ожидаемый результат (PASS)**:
- 60 строк `200`.
- 10 строк `429`.

**До фикса (FAIL)**:
- 70 строк `200` (rate-limit не срабатывает).

---

## Сценарий 8: Debug endpoint (FR-013)

**Предусловия**: `KARAOKE_WEB_DEBUG_DB_ALLOWED_IPS=127.0.0.1,::1` в env.

**Шаги**:
```bash
curl -s "https://localhost:8897/api/public/debug/db" | jq .
```

**Ожидаемый результат (PASS)**:
```json
{
  "pgActiveConnections": 5,
  "pgIdleConnections": 30,
  "pgMaxConnections": 100,
  "currentThreadCount": 25,
  "currentTomcatMaxThreads": 200,
  "sampledAt": 1723645200000
}
```

**До фикса (FAIL)**:
- 404 Not Found (endpoint отсутствует).

**С IP НЕ из allowlist (например, с другого хоста)**:
- 403 Forbidden.

---

## Сценарий 9: Retention scheduler работает (FR-011)

**Предусловия**: подождать 24 часа после деплоя.

**Шаги**:
1. Вставить старую запись в `tbl_events` (для теста):
   ```sql
   INSERT INTO tbl_events (event_type, rest_name, last_update)
   VALUES ('TEST', 'retention-test', now() - interval '10 days');
   ```
2. Подождать следующего 3:00 AM (время retention scheduler).
3. Выполнить:
   ```sql
   SELECT * FROM tbl_events WHERE rest_name = 'retention-test';
   ```

**Ожидаемый результат (PASS)**:
- Запись удалена (retention = 7 дней).

**До фикса (FAIL)**:
- Запись остаётся (нет scheduler).

---

## Сценарий 10: nginx cache headers (FR-003/004/005)

**Шаги**:
```bash
curl -sI "https://localhost:8897/minio/karaoke/<author>/<author>.preview.author.png" \
  | grep -iE "cache-control|etag|last-modified"
```

**Ожидаемый результат (PASS)**:
```
Cache-Control: public, max-age=86400
ETag: "<hash>"
Last-Modified: <timestamp>
```

**404 fallback (FR-005)**:
```bash
curl -sI "https://localhost:8897/minio/karaoke/nonexistent.png"
```
**Ожидаемый**:
```
Cache-Control: public, max-age=300
HTTP/1.1 404 Not Found
```

---

## Сводка проверки

| Сценарий | FR/SC | Время | Пройдено? |
|----------|-------|-------|-----------|
| 1. `/zakroma` без `/api/public/picture` | FR-002, SC-001 | 5 мин | ☐ |
| 2. HTTP-кеш картинок | FR-003, SC-002 | 2 мин | ☐ |
| 3. Меньше INSERT'ов | FR-006/007, SC-003 | 10 мин | ☐ |
| 4. Стабильный размер tbl_events | FR-011, SC-005 | 24 часа | ☐ |
| 5. Нет инцидентов под нагрузкой | FR-006/007/008/010, SC-006 | 30 мин | ☐ |
| 6. FCP ≤ 4 сек | FR-002, SC-007 | 5 мин | ☐ |
| 7. Rate-limit `/song-picture` | FR-010, SC-008 | 2 мин | ☐ |
| 8. Debug endpoint | FR-013 | 1 мин | ☐ |
| 9. Retention scheduler | FR-011 | 24 часа | ☐ |
| 10. nginx cache headers | FR-003/004/005 | 1 мин | ☐ |

**Все 10 сценариев должны быть PASS для merge.**

---

## Когда что-то идёт не так

| Симптом | Вероятная причина | Что делать |
|---|---|---|
| Сценарий 1 FAIL: запросы на `/api/public/picture` всё ещё идут | `AuthorTilePublicDto.fromAuthorName` не обновлён | Проверить код: `authorPictureUrl` должен формировать прямой URL `/minio/karaoke/...` |
| Сценарий 3 FAIL: INSERT'ов столько же | `SamplingFilter` не подключён или sampling rate=0 | Проверить env `KARAOKE_WEB_EVENTS_SAMPLING_ANON`, `LOGGED`, `ADMIN` |
| Сценарий 5 FAIL: p99 > 500 мс | Один из polling-эндпоинтов не кешируется | Проверить `PollingCache` и TTL для каждого эндпоинта |
| Сценарий 7 FAIL: 70 запросов = 70 успешных | `RateLimitInterceptor` не зарегистрирован | Проверить конфигурацию Spring `@Configuration` для interceptor |
| Сценарий 8 FAIL: 404 на debug endpoint | `KARAOKE_WEB_DEBUG_DB_ALLOWED_IPS` пуст | Установить переменную, перезапустить karaoke-web |
| Сценарий 9 FAIL: старая запись не удалилась | `EventsRetentionScheduler` не запускается | Проверить логи на наличие ошибок в cron; проверить `@EnableScheduling` |
| Сценарий 10 FAIL: нет Cache-Control | nginx reload не сделал | Проверить `nginx -t && nginx -s reload`; проверить, что `expires 24h` в `/minio/` location |

---

## Автоматизация

Сценарии 1-2, 6, 8, 10 — через Chrome DevTools Protocol + Puppeteer/Jest.
Сценарии 3, 5, 7 — через bash + curl/wrk + SQL.
Сценарии 4, 9 — требуют ожидания (не автоматизируются).

---

## См. также

- [spec.md](./spec.md) — функциональные требования и success criteria.
- [plan.md](./plan.md) — имплементационный план.
- [research.md](./research.md) — детальный аудит.
- [contracts/](./contracts/) — API-контракты изменений.
- [`docs/features/site-traffic-resilience.md`](../../docs/features/site-traffic-resilience.md) — per-feature документ (создаётся в plan.md).
