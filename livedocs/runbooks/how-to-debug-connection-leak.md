# How to: debug connection leak (FATAL: too many clients already)

## Prerequisites

- Доступ к admin-машине (где запускается `karaoke-db` + `karaoke-app`).
- `pg_stat_activity` доступ.

## Симптомы

В логе `karaoke-app`:
```
KaraokeConnection getConnection Exception: FATAL: sorry, too many clients already
```

Cascade:
- Админка (`webvue3`) — дашборды пустые (нет данных).
- `karaoke-public` — публичные endpoints 503/504.

## Steps

### 1. Проверить текущее число подключений

```bash
docker exec -i karaoke-db psql -U postgres -d karaoke -c \
  "SELECT count(*) FILTER (WHERE state = 'active') AS active, count(*) AS total FROM pg_stat_activity WHERE datname = 'karaoke';"
```

- Если `active >= 80` (default `max_connections=100`) — срочно устранять.
- Если `active < 20` — единичный случай, retry сам поможет.

### 2. Список удерживаемых соединений (по приложению)

```bash
docker exec -i karaoke-db psql -U postgres -d karaoke -c \
  "SELECT application_name, client_addr, state, query_start, LEFT(query, 80) FROM pg_stat_activity WHERE datname = 'karaoke' ORDER BY query_start;"
```

Ищем:
- Долгие `idle in transaction` → утечка в `KaraokeConnection`.
- Долгие `active` без query → зависший поток в `KaraokeProcessThread`.

### 3. Срочное лечение

```bash
# 1. Завершить idle-in-transaction старше 10 минут
docker exec -i karaoke-db psql -U postgres -d karaoke -c \
  "SELECT pg_terminate_backend(pid), application_name, state FROM pg_stat_activity WHERE datname = 'karaoke' AND state = 'idle in transaction' AND query_start < now() - INTERVAL '10 minutes';"

# 2. Перезапустить karaoke-app (он держит ThreadLocal connections)
ssh dev-pc-dev  # или локально, если админ
docker restart karaoke-app  # очищает все ThreadLocal-cache

# 3. Подождать 30 сек и проверить
sleep 30
docker logs karaoke-app --tail 50 | grep -i "connection"
```

### 4. Диагностика корневой причины

После стабилизации — найти **кто** держит соединения:

```bash
# Найти KaraokeProcessThread, который долго живёт
docker exec karaoke-app jstack <pid> 2>/dev/null | grep "KaraokeProcessThread" | head -10

# Найти в коде места без releaseForThisThread()
grep -rn "KaraokeProcessThread(" karaoke-app/src/main/kotlin
grep -rn "releaseForThisThread" karaoke-app/src/main/kotlin | wc -l  # должно совпадать
```

**Корневая причина** (см. [features/091-fix-connection-leak.md](../features/091-fix-connection-leak.md)):
- Одноразовые потоки (`KaraokeProcessThread` per task) — должны вызывать
  `KaraokeConnection.releaseForThisThread()` в `finally`. Если не вызывают
  → утечка.

### 5. Долгосрочный фикс (Pass N+)

Уже было исправлено в **фиче 091**:
```kotlin
class KaraokeProcessThread(...) : Thread() {
    override fun run() {
        try {
            // ... обработка задания
        } finally {
            KaraokeConnection.releaseForThisThread()  // MUST!
        }
    }
}
```

Если находите место, где этого нет — добавить.

### 6. Профилактика

- Увеличить `max_connections` в `postgresql.conf` (напр. 200 вместо 100)
  — **только как временная мера**, основной фикс — устранить утечку.
- Мониторинг: `RenderQueueStalledCheck` ловит stalls воркера, но **не**
  ловит leak. TODO — добавить алерт на `pg_stat_activity > 80`.

## Verification

После фикса:
```bash
# Активные подключения стабильны (< 30)
watch -n 5 'docker exec karaoke-db psql -U postgres -d karaoke -t -c \
  "SELECT count(*) FROM pg_stat_activity WHERE datname = '"'"'karaoke'"'"' AND state = '"'"'active'"'"';"'

# Воркер обрабатывает задания без stalls
docker logs karaoke-app --tail 100 | grep "PROCESS_COUNT_WAITING" | tail -10
```

## Related

- LiveDocs: [architecture/dual-db-access.md](../architecture/dual-db-access.md) —
  ThreadLocal + releaseForThisThread.
- LiveDocs feature: [091-fix-connection-leak.md](../features/091-fix-connection-leak.md) —
  оригинальный баг-репорт.
- [architecture/observability.md](../architecture/observability.md) — куда
  смотреть при сбое.