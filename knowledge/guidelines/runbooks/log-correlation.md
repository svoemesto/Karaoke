# Корреляция логов прода — `knowledge/guidelines/runbooks/log-correlation.md`

**Дата**: 2026-09-01
**Привязка**: [specs/288-prod-diagnostics-logging/spec.md](../../../specs/288-prod-diagnostics-logging/spec.md) — FR-019, FR-020
**Где мы**: при диагностике инцидента «прод подвис» разработчик/administrator открывает этот документ и за ≤15 минут находит причину.

> Прод-сервер: `188.127.240.124` (`sm-karaoke.ru`, с 2026-09-23 — прод и MinIO на одном хосте). Admin-машина, где запущен `karaoke-app`: обычно `nsa-i9`. Все логи пишутся в TZ `Europe/Moscow` (MSK) после фичи 288-prod-diagnostics-logging.

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

## 1a. Формат строки ошибки и реестр кодов (KARAOKE-387)

С 2026-11-02 в `karaoke-app` ошибки, проходящие через `AppLog`
(`karaoke-app/.../AppLog.kt`), пишутся **одной строкой** с машинным кодом:

```
event=db.connect.failed code=DB-CONNECT-001 target=SERVER thread=http-nio-1 host=188.127.240.124:5433 cause=PSQLException: Закончилось время ожидания
```

- `event=` — что произошло (стабильное имя события, грепается как раньше);
- `code=` — машинный код из реестра ниже; по нему ищут в этом документе;
- пары `ключ=значение` — контекст конкретного события (хост без кредов, операция, таблица, `sqlState`, `attempts`, длительность);
- `cause=` — цепочка типов и сообщений исключения (`A: msg <- B: msg`), **без фреймов**;
- стектрейс печатается только при `LOG_STACKTRACES=true` (по умолчанию выключен) — включать на время разбора, когда нужен точный кадр кода.

Правило для новых ошибок: сначала код в реестр, потом `AppLog.error/warn`. Код без записи в реестре дежурному бесполезен.

| Код | Событие | Что значит | Что делать |
|---|---|---|---|
| `DB-CONNECT-001` | `db.connect.failed` | Не удалось открыть соединение с БД (`target=LOCAL/SERVER`, `host=`) | Проверить доступность канала: для `SERVER` — allow-list прод-хоста (порт 5433) и адрес админ-машины; для `LOCAL` — контейнер `karaoke-db` |
| `DB-CLOSE-001` | `db.close.failed` | Сбой закрытия соединения потока (утечка не возникает, но канал мог остаться) | Обычно шум после обрыва; смотреть, не повторяется ли пачками |
| `DB-UNAVAILABLE-001` | (исключение) | Связь с БД потеряна и прозрачный повтор не помог (`operation=`) | Смотреть `cause`; для `SERVER` — канал до прода |
| `DB-UNAVAILABLE-002` | `db.unavailable` | Нет соединения перед операцией (`operation=` `loadList`/`getListHashes`/`getTotalCount`, `table=`) | То же, что `DB-CONNECT-001`; проверить, единичное это или пачка |
| `DB-RETRY-001` | `db.retry` | Идемпотентная операция повторилась после обрыва (`operation=`, `attempt=`, `reason=`) | Действий не требует; если идут пачками — искать причину обрыва |
| `CHAT-UNREAD-001` | `chat.unread.count.failed` | Не посчитались непрочитанные сообщения чата (`sqlState=`) | Смотреть БД; на интерфейс не влияет (счётчик показывается без числа) |
| `STAT-DB-001` | `stat.db.failed` | Статистика по песне не прочитана (`operation=` — метод) | Смотреть БД; на интерфейс влияет только на блок статистики |
| `HIST-DB-001` | `history.db.failed` | История прослушиваний: операция `upsert`/`getForUser` упала | Смотреть БД |
| `SONG-DB-001` | `song.db.failed` | Загрузка песен/авторов/альбомов упала (`operation=`) | Смотреть БД; при `loadListFromDb` — вероятен обрыв соединения |
| `PROCESS-DB-001` | `process.db.failed` | Очередь заданий: счётчики/выборка/обновление упали (`operation=`) | Смотреть БД; очередь может копить задания |
| `WORKER-001` | `worker.failed` | Главный цикл воркера упал необработанным исключением | Смотреть `operation=` и причину; воркер перезапускает цикл сам |
| `DB-001` | `db.failed` | Прочее место слоя БД (`operation=`) | Смотреть БД |
| `ALBUM-DB-001` | `album.author.read.failed` | Автор альбома не прочитан — альбом не создаётся (`author=`) | Смотреть БД |
| `UTIL-DB-001` | `util.author.read.failed` | Автор не прочитан — запись не создаётся (`author=`) | Смотреть БД |
| `UUID-CLOSE-001` | `uuid.close.failed` | Не закрылись ресурсы чтения UUID | Шум; смотреть только при серии |
| `SSE-001` | `sse.record_change.failed` | Не разослано SSE-событие изменения (`table=`) | Клиенты не увидят изменение до следующего опроса |
| `UTIL-SYNC-001/002` | `util.sync.autocommit.failed` / `util.sync.enqueue.failed` | Песня не поставлена в sync-таблицу REMOTE (`songId=`) | Проверить очередь синка; песня не уедет на внешний сервер |
| `UTIL-VPN-001/002` | `util.vpn.request.failed` / `util.vpn.close.failed` | Проверка VPN-доступности упала (`url=`) | Влияет на определение VPN; проверять вручную |
| `UTIL-FS-001/002` | `util.fs.walk.failed` / `util.fs.chmod.failed` | Ошибка файловой системы (`path=`) | Проверить права/диск |
| `UTIL-INDEX-001` | `util.parent_candidates.load.failed` | Не загрузились кандидаты-родители | Смотреть БД |
| `CRYPTO-001/002/003` | `crypto.decrypt.failed` / `crypto.encrypt.failed` / `crypto.failed` | Ошибка шифрования | Значения и ключи в лог не пишутся — только операция и тип ошибки |
| `EDITOR-001/002` | `editor.apply.failed` / `editor.render_demo.failed` | Редактор песен: разметка не применилась (`songId=`) | Смотреть интерфейс редактора |
| `API-CLOSE-001` | `api.jdbc.close.failed` | Не закрылись JDBC-ресурсы эндпоинта (`endpoint=`) | Шум; серия — признак утечки соединений |
| `API-BG-001` | `api.background.interrupted` | Фоновая операция прервана (`operation=`) | Смотреть причину прерывания |
| `HEALTH-POOL-001/002` | `health.recompute.failed` / `health.waiting_task.failed` | Не пересчиталось здоровье песни (`songId=`/`task=`) | Смотреть БД |
| `HEALTH-REPORT-001` | `health.enqueue.waiting.failed` | Не встали в очередь ожидающие задачи | Смотреть БД и очередь |
| `SYNC-SCHED-001/002` | `sync.scheduler.target.failed` / `sync.scheduler.tick.failed` | Ошибка автосинка (`target=`/`startedAt=`) | Смотреть лог синка; синк не состоится |
| `GEOIP-001` | `geoip.failed` | GeoIP-lookup упал | Влияет на статистику по странам |
| `PLAYWRIGHT-001` | `playwright.browser.failed` | Ошибка браузера (Playwright) | Перезапуск задачи |
| `AI-001` | `ai.failed` | Ошибка AI-обработки | Смотреть причину; задача уйдёт в ERROR |
| `SHARE-001` | `share_links.failed` | Ошибка админки ссылок | Смотреть причину |
| `STREAM-001` | `dual_stream.failed` | Ошибка двойного потока | Смотреть причину |
| `UTIL-001` | `util.failed` | Прочая утилита (`operation=`) | Смотреть причину |
| `STORAGE-POLICY-OK` / `STORAGE-POLICY-REPAIRED` / `STORAGE-POLICY-UNCONFIRMED` | `stemjobs:storage:policy …` | Политика бакетов MinIO: подтверждена / дожата / не подтверждена | Для `UNCONFIRMED` — проверить канал до MinIO (порт 9000) и повторить; самопроверка идёт каждые 5 минут (KARAOKE-385) |

## 1b. Guard гигиены логов (KARAOKE-387, продолжение)

Чтобы стектрейсы не возвращались, есть проверка `log-hygiene-guard`
(`tools/check-log-hygiene.sh`): в **CI** (джоб выборочного CI, зона `karaoke-app/**`) и в
**pre-commit** (каждый коммит). Она считает по файлам:

- `printStackTrace` — прямой вывод стека в stdout;
- `log.<level>(…)` с объектом исключения последним аргументом — SLF4J печатает стек после сообщения.

Модель — как у eslint-baseline: `config/log-hygiene-baseline.txt` хранит текущие счётчики. Проверка падает, если появился **новый** файл с этими паттернами или счётчик в файле **вырос**. Уменьшение счётчика — не ошибка, но baseline стоит обновить.

Состояние на 2026-11-02: baseline **пуст** — в `karaoke-app` не осталось ни одного реального `printStackTrace()` и ни одного `log.<level>(…, e)`. Комментарии в подсчёте не участвуют (упоминание «printStackTrace» в тексте комментария — не источник стектрейсов), поэтому baseline может обнулиться.

```bash
bash tools/check-log-hygiene.sh --report            # текущие счётчики
bash tools/check-log-hygiene.sh                     # проверка (exit 1 при регрессе)
bash tools/check-log-hygiene.sh --update-baseline   # пересобрать после миграции
```

Правило: новые ошибки пишутся через `AppLog` (код из реестра §1a, `cause=` цепочкой, стек — только `LOG_STACKTRACES=true`). Если правишь старый файл — переводи его строки на `AppLog` и уменьшай baseline.

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

### 2.4. Загрузка heap `karaoke-app` и дампы (BR-630, спека 676)

```bash
# Текущая загрузка heap (строка INFO на каждом тике мониторинга, раз в минуту)
docker logs karaoke-app --since "1h" | grep "infra.monitor.heap"

# Превышение порога (75 % — WARNING, 90 % — CRITICAL)
docker logs karaoke-app --since "1d" | grep "heap:thresholdExceeded"

# Возврат в норму
docker logs karaoke-app --since "1d" | grep "heap:recovered"

# Дампы состояния heap на хосте (том ./karaoke-db/dumps)
ls -la ~/Karaoke/deploy/karaoke-db/dumps/

# Убрать старые дампы вручную (политика: последние 2, ≤ 20 ГБ, по возрасту)
bash ~/Karaoke/deploy/heap-dump-retention.sh
```

### 2.5. nginx на прод-сервере

```bash
# Через SSH
ssh root@188.127.240.124 'tail -f /var/log/nginx/access.log'

# Live tail с фильтром по status
ssh root@188.127.240.124 'tail -f /var/log/nginx/access.log | awk "\$9 >= 500"'

# Скачать за последний час
ssh root@188.127.240.124 'grep "$(date -d "1 hour ago" "+%Y-%m-%dT%H")" /var/log/nginx/access.log'
```

### 2.5. MinIO (через nginx proxy)

```bash
# Запросы к /minio/karaoke/...
ssh root@188.127.240.124 'grep "/minio/karaoke/" /var/log/nginx/access.log | tail -50'
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
| `infra.monitor.heap` | Загрузка heap JVM `karaoke-app` (BR-630): строка `heap:usage …` — на каждом тике мониторинга |
| `heap:thresholdExceeded` | Превышение порога загрузки heap (75 % — WARNING, 90 % — CRITICAL) |
| `heap:recovered` | Возврат загрузки heap в норму |
| `WARN` / `ERROR` | Уровни логирования (Spring Boot дефолт) |
| `song.locked_save_fallback` | (specs/299) Песня удалена между `loadFromDbByIdForUpdate` и `saveToDbLocked` — fallback на `saveToDb()` без блокировки. Ожидаемая частота: < 1/час. |
| `song.locked_save_failed` | (specs/299) SQL-ошибка внутри `saveToDbLocked` (не lock timeout). Должно быть 0. Если есть — проверить логи БД. |
| `song.lock_timeout` | (specs/299) `PSQLException` SQL state `55P03` (lock timeout) или `40P01` (deadlock detected) в `saveToDbLocked`. Ожидаемая частота: < 1/час. |
| `song.locked_save_skipped` | (specs/299) `saveToDbLocked` вызван для readonly-песни — fallback на `saveToDb()`. Безопасно, но редко должно встречаться. |

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

### 2.6. SSH на прод-хосте (`sm-karaoke.ru`, 188.127.240.124)

sshd на проде пишет через syslog в `/dev/log`, а `/dev/log` — это
`/run/systemd/journal/dev-log`, то есть сообщения уходят **в journald** (rsyslog на
проде не установлен — и не нужен). Проверено 2026-11-01 (KARAOKE-383): за сутки
`_COMM=sshd` — 541 запись, `_COMM=sshd-session` — ~30 000, «Accepted publickey» и
«Disconnected from» на месте.

```bash
# входы и выходы (демон)
journalctl _COMM=sshd --since today --no-pager | grep -E "Accepted|Disconnected|Connection closed"
# форкнутые процессы OpenSSH 9.8+ (PAM, аутентификация)
journalctl _COMM=sshd-session --since today --no-pager | tail -50
# то же по идентификатору syslog
journalctl SYSLOG_IDENTIFIER=sshd --since today --no-pager | tail -50
```

**Ловушка (наша собственная ошибка 2026-11-01).** На проде в journald больше
1.5 млн записей и `/var/log/journal` — около 1 ГБ. Запрос вида
`journalctl _COMM=sshd --since "2026-11-01 00:00" --no-pager | wc -l` **не
возвращает ноль записей — он возвращает ПУСТОЙ вывод**: journalctl не успевает
прочитать объём и отдаёт пустоту (либо соединение отваливается по таймауту).
Это легко принять за «sshd не пишет». Надёжно — ограничивать окно
(`--since today`, `--since "-1h"`) и не полагаться на счётчик по всему объёму.
Проверка «журнал жив» — точечная: `journalctl _COMM=sshd --since "-10min" --no-pager | tail -5`.

**Доступ к прод-хосту.** SSH с админ-машины `nsa-i9` нестабилен (пул адресов
провайдера, VPN — см. KARAOKE-382): соединение то устанавливается, то обрывается
на connect. Надёжный путь — через `msk-f896` как jump-host:

```bash
ssh -o ProxyCommand="ssh -i ~/.config/karaoke/id_ed25519 -p 7487 -o BatchMode=yes -W %h:%p sm@185.71.198.17" \
    -i ~/.config/karaoke/id_ed25519 root@188.127.240.124
```

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
ssh root@188.127.240.124 'date +%z'
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
ssh root@188.127.240.124 'grep "$(date -d "15 minutes ago" "+%d/%b/%Y:%H")" /var/log/nginx/access.log | head -50'

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
ssh root@188.127.240.124 'grep " 50[0-9] " /var/log/nginx/access.log | tail -20'

# 4. Проверить состояние БД
docker exec karaoke-db psql -U postgres -d karaoke -c "SELECT count(*), state FROM pg_stat_activity GROUP BY state;"

# 5. Проверить container status
ssh root@188.127.240.124 'docker ps'
ssh root@188.127.240.124 'docker stats --no-stream'
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

### 5.4. «Алерт по памяти в `karaoke-app` (или OOM)» (BR-630, спека 676)

**Контекст**: в логах `karaoke-app` — `heap:thresholdExceeded` (загрузка heap
выше 75 % / 90 %), либо контейнер упал с `OutOfMemoryError` и в
`deploy/karaoke-db/dumps/` появился `java_pid*.hprof`.

**Порядок действий**:

1. **Снять дамп ДО перезапуска контейнера** (после рестарта файл останется на
   томе, но контекст инцидента в логах уже потерян):

   ```bash
   ls -la ~/Karaoke/deploy/karaoke-db/dumps/
   cp ~/Karaoke/deploy/karaoke-db/dumps/java_pid*.hprof /tmp/heap-$(date +%s).hprof
   ```

2. **Проверить фактические лимиты** (они в репозитории, не в `.env`):

   ```bash
   docker inspect karaoke-app --format '{{.HostConfig.Memory}}'   # 8589934592 = 8 ГБ
   docker exec karaoke-app java -XX:+PrintFlagsFinal -version 2>/dev/null | grep MaxHeapSize
   grep -nE 'mem_limit|-Xmx' ~/Karaoke/deploy/docker-compose-app.yml
   ```

3. **Посмотреть загрузку в динамике** (если контейнер ещё жив):

   ```bash
   docker logs karaoke-app --since "30m" | grep "heap:usage"
   ```

4. **Убрать старые дампы** (том не должен переполняться):

   ```bash
   bash ~/Karaoke/deploy/heap-dump-retention.sh
   ```

5. **Перезапускать контейнер — по правилам Pass 380**: сначала остановить
   очередь заданий (`GET /process/isworking` на `:8898` → `false`, кнопка
   «Стоп»), и только потом `bash deploy/do.sh restart_app`. Очередь заданий
   внутри контейнера при перезапуске теряется.

6. **Своп на admin-машине не трогаем** (решение владельца 2026-10-30): предел
   памяти обеспечивается лимитом контейнера (`mem_limit: 8g` + `-Xmx6g`).
   Увеличение свопа проблему не лечит: при наличии свопа контейнер уходит в
   подкачку вместо явного OOM-kill — тихое подвисание хуже рестарта.

**Если после роста `-Xmx` выше ~9–10 ГБ понадобится изменить лимит тома
дампов** — пересчитать: два дампа по 6 ГБ (12 ГБ) помещаются в 20 ГБ, а
три — уже нет.

## 6. Что делать при инциденте (TL;DR)

```
1. docker logs karaoke-app --since "15m" | grep "infra.prod"
   → Определить тип сбоя (ping, БД, восстановление).
2. docker logs karaoke-db --since "15m" | grep "duration:|ERROR|WARNING"
   → Найти медленные SQL или ошибки БД.
3. ssh root@188.127.240.124 'tail -50 /var/log/nginx/access.log'
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
ssh root@188.127.240.124 'cat /var/log/nginx/access.log' > /tmp/incident-logs/nginx-access.log
ssh root@188.127.240.124 'cat /var/log/nginx/error.log' > /tmp/incident-logs/nginx-error.log
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
- Обновление 2026-10-30 (спека 676, KARAOKE-245, BR-630): раздел 2.4 — загрузка heap и
  дампы `karaoke-app`; маркеры `infra.monitor.heap` / `heap:thresholdExceeded` /
  `heap:recovered` в § 3.2; типичный сценарий 5.4 — порядок действий при алерте по
  памяти и при OOM (снять дамп → проверить лимиты → убрать старые дампы →
  перезапуск по Pass 380; своп не трогаем).
- Обновление 2026-11-01 (KARAOKE-383): раздел 2.6 — журналы SSH на прод-хосте
  (`journalctl _COMM=sshd` / `_COMM=sshd-session`), доступ через jump-host `msk-f896` и ловушка
  пустого вывода `journalctl` на большом объёме (больше 1.5 млн записей).
