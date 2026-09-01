# Корреляция логов прода — `docs/ops/log-correlation.md`

**Дата**: 2026-09-01
**Привязка**: [specs/288-prod-diagnostics-logging/spec.md](../specs/288-prod-diagnostics-logging/spec.md) — FR-019, FR-020
**Где мы**: при диагностике инцидента «прод подвис» разработчик/administrator открывает этот документ и за ≤15 минут находит причину.

> Прод-сервер: `188.119.64.111` (`sm-karaoke.ru`). Admin-машина, где запущен `karaoke-app`: обычно `nsa-i9`. Все логи пишутся в TZ `Europe/Moscow` (MSK) после фичи 288-prod-diagnostics-logging.

---

## 1. Карта источников логов

| Источник | Что | Где | Команда | TZ |
|----------|-----|-----|---------|-----|
| **PostgreSQL** | SQL-запросы, ошибки, lock waits, temp-файлы, autovacuum | `karaoke-db` контейнер | `docker logs karaoke-db --since "..."` | MSK (после FR-007) |
| **karaoke-web** (Spring Boot) | HTTP-запросы, ошибки, polling | `karaoke-web` контейнер | `docker logs karaoke-web --since "..."` | MSK (после FR-010) |
| **karaoke-app** (admin) | `ProdContainerCheck` пинги (WARN/INFO), `MonitoringService` алерты | stdout контейнера или процесса на admin-машине | `docker logs karaoke-app --since "..."` или локальный stdout | TZ admin-машины (проверить) |
| **karaoke-public** (nginx) | Статика SPA | `karaoke-public` контейнер | `docker logs karaoke-public --since "..."` | TZ контейнера |
| **nginx** (основной прокси) | HTTP-request/response для `https://sm-karaoke.ru/...` | `/var/log/nginx/access.log`, `/var/log/nginx/error.log` на хосте прод-сервера | `ssh prod 'tail -f /var/log/nginx/access.log'` | MSK (если `$time_iso8601`) или TZ хоста |
| **MinIO** | S3 API запросы (bucket `karaoke`) | nginx proxy logs (`/var/log/nginx/...`) или stdout контейнера `karaoke-storage` | `docker logs karaoke-storage --since "..."` | TZ контейнера |

---

## 2. Команды просмотра

### 2.1. PostgreSQL (`karaoke-db`)

```bash
# Все логи за последние 10 минут
docker logs karaoke-db --since "10m"

# Конкретный интервал
docker logs karaoke-db --since "2026-09-01T15:00:00" --until "2026-09-01T15:10:00"

# Только медленные SQL (>1 сек)
docker logs karaoke-db --since "1h" | grep "duration:"

# Только ошибки
docker logs karaoke-db --since "1h" | grep -E "ERROR:|FATAL:|FATAL ERROR"

# Только lock waits
docker logs karaoke-db --since "1h" | grep "still waiting"

# Только temp-файлы (превышение work_mem)
docker logs karaoke-db --since "1h" | grep "temporary file"

# Live tail
docker logs karaoke-db --follow --tail 100
```

После применения `ALTER SYSTEM SET log_line_prefix = '%m [%p] %q%u@%d from %h '` (FR-006) каждая строка имеет формат:
```
2026-09-01 15:34:56.789 MSK [12345] postgres@karaoke from 172.18.0.5 LOG: duration: 1523 ms statement: SELECT ...
```

### 2.2. karaoke-web (`/api/public/*`, `/api/admin/*`)

```bash
# Все HTTP-запросы за последние 5 минут
docker logs karaoke-web --since "5m"

# Только WARN/ERROR
docker logs karaoke-web --since "1h" | grep -E " WARN | ERROR "

# Live tail
docker logs karaoke-web --follow --tail 100
```

### 2.3. `ProdContainerCheck` (admin-машина, где запущен `karaoke-app`)

```bash
# Все сообщения категории infra.prod.ping
docker logs karaoke-app --since "1d" | grep "infra.prod.ping"

# Все WARN за сегодня
docker logs karaoke-app --since "1d" | grep -E " WARN infra.prod."

# Только восстановления (recovery)
docker logs karaoke-app --since "1d" | grep "ping:recovered"

# Только ping-БД
docker logs karaoke-app --since "1d" | grep "infra.prod.db"
```

### 2.4. nginx на прод-сервере

```bash
# Через SSH
ssh root@188.119.64.111 'tail -f /var/log/nginx/access.log'

# Live tail с фильтром по status
ssh root@188.119.64.111 'tail -f /var/log/nginx/access.log | awk "\$9 >= 500"'

# Скачать за последний час
ssh root@188.119.64.111 'grep "$(date -d "1 hour ago" "+%Y-%m-%dT%H")" /var/log/nginx/access.log'
```

### 2.5. MinIO (через nginx proxy)

```bash
# Запросы к /minio/karaoke/...
ssh root@188.119.64.111 'grep "/minio/karaoke/" /var/log/nginx/access.log | tail -50'
```

---

## 3. Grep-маркеры (быстрый поиск)

### 3.1. PostgreSQL (`pg_log`)

| Маркер | Значение |
|--------|----------|
| `LOG:  duration:` | Медленный SQL (после `log_min_duration_statement`) |
| `LOG:  temporary file:` | Использование temp-файла |
| `LOG:  process N still waiting` | Lock wait |
| `LOG:  automatic vacuum` | autovacuum |
| `LOG:  checkpoint` | Checkpoint |
| `WARNING:` | Warning (не ошибка, но заслуживает внимания) |
| `ERROR:` | Ошибка |
| `FATAL:` | Критическая ошибка |
| `statement:` | Содержит SQL-запрос (медленный) |

### 3.2. Spring Boot (`docker logs karaoke-web` или `karaoke-app`)

| Маркер | Значение |
|--------|----------|
| `infra.prod.ping` | Пинги прода (HTTP) |
| `infra.prod.ping - ping:failed` | Неуспешный пинг прода |
| `infra.prod.ping - ping:recovered` | Восстановление прода после сбоя |
| `infra.prod.db` | Пинги прод-БД |
| `WARN` / `ERROR` | Уровни логирования (Spring Boot дефолт) |

### 3.3. nginx (`access.log`)

| Маркер | Значение |
|--------|----------|
| `$request_time` (или `request_time=`) | Длительность запроса в секундах (если используется `log_format` с `$request_time`) |
| `status` (HTTP code) | 2xx/3xx OK, 4xx client error, 5xx server error |
| `upstream_response_time` | Длительность ответа upstream (для проксированных запросов) |

Стандартный формат строки nginx access.log (combined):
```
192.168.1.1 - - [01/Sep/2026:15:34:56 +0300] "GET /api/public/songs/123 HTTP/1.1" 200 1234 "-" "Mozilla/5.0"
```

---

## 4. TZ-синхронизация (важно для корреляции)

**Цель**: все 4 источника логов должны использовать одну TZ для однозначной корреляции по timestamp.

| Источник | TZ до фичи | TZ после фичи 288 | Как включено |
|----------|-------------|-------------------|--------------|
| PostgreSQL `pg_log` | UTC (дефолт Docker) | MSK | `ALTER SYSTEM SET log_timezone = 'Europe/Moscow'` (FR-007) + `TZ: Europe/Moscow` в `docker-compose-database.yml` (FR-008) |
| Spring Boot `karaoke-web` | TZ JVM (UTC в Docker) | MSK | `-Duser.timezone=Europe/Moscow` в `WEB_JAVA_OPTS` (FR-010) |
| nginx `access.log` | TZ хоста (прод — MSK или UTC) | TZ хоста | Требует ручной правки `log_format` (Out of Scope, см. спеку) |
| `karaoke-app` (admin) | TZ admin-машины | TZ admin-машины | По умолчанию; если JVM запускается с TZ-флагом, можно синхронизировать |

**Проверка после применения фичи 288**:
```bash
docker exec karaoke-db psql -U postgres -d karaoke -c "SHOW log_timezone;"
# → Europe/Moscow
docker exec karaoke-web bash -c 'date +%z'
# → +0300
ssh root@188.119.64.111 'date +%z'
# → +0300 (или TZ хоста)
```

---

## 5. Типичные сценарии

### 5.1. «Прод подвис после одобрения задания редактора»

**Контекст**: пользователь одобрил задание редактора в `webvue3`, после чего сайт перестал отвечать на 5-10 минут.

**Шаги диагностики** (≤15 минут):

```bash
# 1. Проверить, что `karaoke-app` зафиксировал проблему (с admin-машины)
docker logs karaoke-app --since "15m" | grep "infra.prod.ping"
# Ожидаемо: WARN infra.prod.ping - ping:failed ... в момент инцидента

# 2. Найти момент начала в pg_log
docker logs karaoke-db --since "15m" | grep -E "duration:|ERROR" | head -50

# 3. Найти медленные SQL — кандидаты на причину
docker logs karaoke-db --since "15m" | grep "duration:" | head -20
# Если duration > 5 сек — это явный hotspot

# 4. Коррелировать с nginx (HTTP-запросы в то же время)
ssh root@188.119.64.111 'grep "$(date -d "15 minutes ago" "+%d/%b/%Y:%H")" /var/log/nginx/access.log | head -50'

# 5. Проверить активные сессии БД (если инцидент продолжается)
docker exec karaoke-db psql -U postgres -d karaoke -c "SELECT pid, state, query_start, LEFT(query, 80) FROM pg_stat_activity WHERE state != 'idle' ORDER BY query_start;"

# 6. Посмотреть lock waits
docker logs karaoke-db --since "15m" | grep "still waiting"

# 7. Проверить temp-файлы (признак тяжёлого JOIN/sort)
docker logs karaoke-db --since "15m" | grep "temporary file"
```

**Что искать**:
- В момент одобрения задания редактора — длинные SQL (>1 сек), которые инициированы batch-операцией редактора.
- Например, `Song.loadFromDbById` в цикле (N+1), или `Song.loadAuthorSongCounts` (full-scan `tbl_songs`).
- Коррелировать с nginx-логами: какие именно URL пришли в момент начала деградации (с `upstream_response_time` высоким).

### 5.2. «Сайт недоступен (5xx)»

**Контекст**: пользователь видит 502/503 при попытке открыть `sm-karaoke.ru`.

```bash
# 1. `ProdContainerCheck` уже должен был поднять алерт (WARNING/CRITICAL).
# Проверить:
docker logs karaoke-app --since "10m" | grep "infra.prod"

# 2. Посмотреть ошибки Spring Boot
docker logs karaoke-web --since "10m" | grep -E " ERROR " | head -20

# 3. Проверить nginx (502 = upstream не отвечает, 503 = upstream вернул 503)
ssh root@188.119.64.111 'grep " 50[0-9] " /var/log/nginx/access.log | tail -20'

# 4. Проверить состояние БД
docker exec karaoke-db psql -U postgres -d karaoke -c "SELECT count(*), state FROM pg_stat_activity GROUP BY state;"

# 5. Проверить container status
ssh root@188.119.64.111 'docker ps'
ssh root@188.119.64.111 'docker stats --no-stream'
```

### 5.3. «Плановый дебаг новой фичи — нужно понять, какие SQL идут»

**Контекст**: разработчик хочет посмотреть, какие SQL-запросы генерирует новая фича в karaoke-web.

```bash
# 1. Временно понизить log_min_duration_statement до 0 (логировать ВСЁ)
docker exec karaoke-db psql -U postgres -d karaoke -c "ALTER SYSTEM SET log_min_duration_statement = 0; SELECT pg_reload_conf();"

# 2. Запустить фичу, собрать pg_log
docker logs karaoke-db --since "5m" | grep "statement:" | head -50

# 3. Вернуть порог обратно
docker exec karaoke-db psql -U postgres -d karaoke -c "ALTER SYSTEM SET log_min_duration_statement = 1000; SELECT pg_reload_conf();"
```

---

## 6. Что делать при инциденте (TL;DR)

```
1. docker logs karaoke-app --since "15m" | grep "infra.prod"
   → Определить тип сбоя (ping, БД, восстановление).
2. docker logs karaoke-db --since "15m" | grep "duration:|ERROR|WARNING"
   → Найти медленные SQL или ошибки БД.
3. ssh root@188.119.64.111 'tail -50 /var/log/nginx/access.log'
   → Коррелировать с HTTP-трафиком.
4. docker logs karaoke-web --since "15m" | grep " ERROR "
   → Найти ошибки Spring Boot.
5. docker exec karaoke-db psql ... pg_stat_activity
   → Активные сессии БД в момент инцидента.
```

Если причина не найдена за 15 минут — собрать логи в архив и эскалировать:
```bash
# Архив логов за последний час (для последующего анализа)
mkdir -p /tmp/incident-logs
docker logs karaoke-db --since "1h" > /tmp/incident-logs/karaoke-db.log
docker logs karaoke-web --since "1h" > /tmp/incident-logs/karaoke-web.log
docker logs karaoke-app --since "1h" > /tmp/incident-logs/karaoke-app.log
ssh root@188.119.64.111 'cat /var/log/nginx/access.log' > /tmp/incident-logs/nginx-access.log
ssh root@188.119.64.111 'cat /var/log/nginx/error.log' > /tmp/incident-logs/nginx-error.log
tar czf /tmp/incident-logs.tgz /tmp/incident-logs
```

---

## 7. Куда добавлять информацию

- **Новые grep-маркеры** (если в karaoke-app/karaoke-web добавляются новые категории SLF4J) — обновить секцию 3.
- **Новые источники логов** (если добавляется новый контейнер) — обновить секцию 1.
- **Новые типичные сценарии** (после очередного инцидента) — добавить в секцию 5.

---

## История

- Создан: 2026-09-01 (specs/288-prod-diagnostics-logging, FR-019).
- Обновление: после крупного инцидента (добавить новый типичный сценарий в секцию 5).