# Feature Specification: Расширенное логирование на проде для отлова зависаний

**Feature Branch**: `288-prod-diagnostics-logging`

**Created**: 2026-09-01

**Status**: Draft

**Input**: User description: "Прод продолжает регулярно подвисать. Например только что он повис после того, как я в админке одобрил задание редактора. У меня есть подозрения, что подвисания прода связаны в том числе и с активностью в админке. Давай продолжим отлавливать зависания на проде. В предыдущих спеках ты предлагал что-то там включить в логировании на базе данных постгреса на проде. Так же наверное будет не лишним отдельно логировать сообщения консоли 'ProdContainerCheck: ping https://sm-karaoke.ru/ не удался: Read timed out' чтобы потом по времени сравнить с тем, что происходит в логах nxinx сервера, базы данных прода и контейнеров прода."

## Контекст и предыстория

Сайт `sm-karaoke.ru` (прод-окружение) продолжает периодически подвисать на 7–10 минут. Первый раунд фиксов (Pass 52, спека 174) устранил самый очевидный источник — `/api/public/news/since` для анонимов (3.5 MB JSON × 45 сек × N пользователей → exhaustion `pg max_connections = 100`). Второй раунд (Pass 244–260, спеки 187, 241–248, 270, 272, 274) — полный аудит hotspots + Tier-1/2 оптимизации (`KaraokeProcessWorker` sync-батч, schema-cache, `getSongsCreateKaraokeAll` батч, streaming для MP4-download, кеш для `/api/public/authors-tiles`, `getProperty`, индексы БД, batch INSERT для `tbl_events`, ограничение `limit` в `/statbysong`).

Однако инциденты продолжаются. Последний — после одобрения задания редактора в админке. Подозрение: **активность в админке может провоцировать каскад тяжёлых SQL/HTTP** на проде (например, batch-операции редактора тянут N+1 запросы, или фоновый sync-cycle `karaoke-app` подхватывает нагрузку при определённых условиях).

Для post-hoc диагностики таких инцидентов **критически не хватает** двух вещей:

1. **Логирование медленных запросов PostgreSQL** (`pg_log`). Сейчас на проде включены только дефолты postgres:16:
   - `log_min_duration_statement = -1` (медленные SQL **НЕ** логируются)
   - `log_temp_files = -1` (использование temp-файлов **НЕ** логируется)
   - `log_line_prefix = ''` (в логе **нет timestamp** — корреляция с другими логами невозможна)
   - `log_timezone`/`timezone` — дефолт UTC.
   
   В спеках 241, 242, 244, 248, 274 это уже обсуждалось: `pg_stat_statements` (FR-108 спеки 241) переведён в backlog из-за `shared_preload_libraries` + рестарт кластера, но **простое логирование через `log_min_duration_statement`, `log_lock_waits`, `log_temp_files`** — runtime-настраиваемые параметры, которые меняются через `ALTER SYSTEM SET ... + pg_reload_conf()` **без рестарта кластера**. Эта спека закрывает этот пробел.

2. **Структурированное логирование `ProdContainerCheck`**. Сейчас в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/checks/ProdContainerCheck.kt:82` неуспешный пинг пишется через `println("ProdContainerCheck: ping $PING_URL не удался: ${e.message}")` — это просто stdout. Успешные пинги вообще **не логируются** (нет baseline для корреляции). Это нарушает конвенцию [livedocs/architecture/decisions/local-0005-structured-logging-karaoke-app.md](../../livedocs/architecture/decisions/local-0005-structured-logging-karaoke-app.md) (SLF4J + MDC + key=value).

Пользователь явно указал цель: «отдельно логировать сообщения консоли 'ProdContainerCheck: ping https://sm-karaoke.ru/ не удался: Read timed out' **чтобы потом по времени сравнить** с тем, что происходит в логах nginx сервера, базы данных прода и контейнеров прода». Для этого нужна:
- **Единая TZ** (Europe/Moscow) во всех логах (PostgreSQL + Spring Boot + nginx).
- **Префикс строк логов PostgreSQL** с timestamp (через `log_line_prefix`).
- **Структурированный формат** SLF4J в `ProdContainerCheck` с явным `category=infra.prod.ping` для grep.

## Цель фичи

1. **Включить расширенное логирование PostgreSQL на проде** (runtime-параметры `log_min_duration_statement`, `log_temp_files`, `log_lock_waits`, `log_autovacuum_min_duration`, `log_checkpoints`, `log_line_prefix`, `log_timezone`, `timezone`) — через `ALTER SYSTEM SET` + `pg_reload_conf()` без рестарта кластера.
2. **Заменить `println` на SLF4J WARN** в `ProdContainerCheck` с правильным форматом по local-0005 (MDC + key=value + явная категория `infra.prod.ping`).
3. **Логировать успешные пинги только при смене состояния** (восстановление после сбоя) — INFO. В обычном режиме успешные пинги НЕ логируются (минимум шума).
4. **Логировать ping-БД** тоже через SLF4J с категорией `infra.prod.db` — для корреляции с pg_log при инциденте с БД.
5. **Синхронизировать TZ** (Europe/Moscow) между PostgreSQL, JVM karaoke-web, JVM karaoke-app — для однозначной корреляции логов по времени.
6. **Зафиксировать в LiveDocs** инструкцию по корреляции логов (где смотреть, как grep'ать, какие маркеры).

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Медленные SQL-запросы видны в логах PostgreSQL (Priority: P1)

Разработчик/админ post-hoc изучает инцидент «прод подвис» (например, после одобрения задания редактора). Он открывает логи контейнера PostgreSQL (`docker logs karaoke-db --since "10m"`) и видит SQL-запросы, выполнение которых заняло больше 1 сек, с timestamp в Europe/Moscow и полным префиксом строки (`user@database from ip`). Это позволяет найти конкретный SQL, который и стал причиной (или индикатором) зависания.

**Why this priority**: Без логирования медленных SQL диагностика инцидента = гадание. Сейчас единственный способ — включать `log_min_duration_statement` руками через SSH на проде после инцидента (когда уже поздно). Превентивное включение даёт готовый артефакт для post-hoc анализа.

**Independent Test**: На admin-машине (которая разделяет с продом ту же версию postgres:16) выполнить `ALTER SYSTEM SET log_min_duration_statement = 0; SELECT pg_reload_conf();` → запустить `SELECT pg_sleep(2);` → `docker logs karaoke-db --since "1m"` показывает строку с `duration: 2000 ms statement: SELECT pg_sleep(2)`. После возврата `log_min_duration_statement = 1000` — строка появляется только для запросов > 1 сек.

**Acceptance Scenarios**:

1. **Given** на проде включён `log_min_duration_statement = 1000`, **When** любой SQL-запрос выполняется дольше 1000 мс (например, `Song.loadAuthorSongCounts` с full-scan по `tbl_songs`), **Then** в `docker logs karaoke-db` появляется строка вида `2026-09-01 12:34:56 MSK [12345] karaoke@karaoke from 188.119.64.111 LOG: duration: 1523 ms statement: SELECT song_author, count(*) FROM tbl_songs GROUP BY song_author`.
2. **Given** включён `log_temp_files = 0`, **When** любой SQL-запрос создаёт temp-файл (например, hash join на больших таблицах, превышающий `work_mem`), **Then** в `docker logs karaoke-db` появляется строка `LOG: temporary file: path ..., size N` с указанием размера файла.
3. **Given** включён `log_lock_waits = on` (дефолт postgres:16, но фиксируем через `ALTER SYSTEM` для идемпотентности), **When** сессия ждёт блокировку дольше `deadlock_timeout` (1 сек), **Then** в `docker logs karaoke-db` появляется `LOG: process 12345 still waiting for ...` (это индикатор потенциальной deadlock/spike).
4. **Given** включён `log_line_prefix = '%m [%p] %q%u@%d from %h '`, **When** PostgreSQL пишет любую строку в лог, **Then** строка начинается с timestamp в формате `YYYY-MM-DD HH:MM:SS TZ` (например, `2026-09-01 12:34:56 MSK`), затем PID, user@database, host — что позволяет однозначно коррелировать по времени с логами nginx и docker.
5. **Given** на проде развёрнуто изменение через `ALTER SYSTEM SET ...; SELECT pg_reload_conf();`, **When** рестарта кластера PostgreSQL НЕ происходит (только reload), **Then** все новые параметры применяются немедленно, и старые параметры в `postgresql.auto.conf` сохраняются для перезапуска контейнера (`pg_reload_conf` не сбрасывает их).

---

### User Story 2 — `ProdContainerCheck` пишет структурированные WARN/INFO (Priority: P1)

Оператор admin-машины (на которой запущен `karaoke-app`) видит в логах karaoke-app **структурированное** сообщение о недоступности прод-сайта вместо обычного `println`. Сообщение содержит: timestamp, категорию `infra.prod.ping`, URL, тип ошибки, длительность пинга, exception. При восстановлении после сбоя пишется INFO «прод восстановлен после N минут простоя». Успешные пинги в обычном режиме НЕ логируются.

**Why this priority**: Сейчас `println("ProdContainerCheck: ping $PING_URL не удался: ${e.message}")` — единственный сигнал о недоступности прода. Без структуры и категории нельзя grep'ать (`grep "infra.prod.ping" log.txt`), фильтровать по уровню (WARN скрыть/показать), связывать с другими событиями по timestamp. Без логирования восстановления — нельзя узнать, когда именно прод вернулся.

**Independent Test**: На admin-машине остановить nginx (`sudo systemctl stop nginx`) на 30 сек → в `docker logs karaoke-app` (или в stdout если karaoke-app запущен как процесс) появится WARN с категорией `infra.prod.ping` и duration. После запуска nginx обратно (`sudo systemctl start nginx`) — INFO «прод восстановлен после N мин». Проверить `grep "infra.prod.ping" docker_logs.txt | tail -10`.

**Acceptance Scenarios**:

1. **Given** `ProdContainerCheck.pingSite()` вызывается и HTTP-запрос к `https://sm-karaoke.ru/` падает с `SocketTimeoutException: Read timed out`, **When** метод обрабатывает исключение, **Then** в логи пишется WARN с structured format по local-0005: `2026-09-01 12:34:56 MSK WARN infra.prod.ping - ping:failed url=https://sm-karaoke.ru/ durationMs=5000 error="Read timed out" exceptionClass=java.net.SocketTimeoutException`, **And** НЕ используется `println`.
2. **Given** ping падает, потом через 30 сек проходит (восстановление), **When** следующий tick `MonitoringService` запускает `ProdContainerCheck.run()`, **Then** в логи пишется INFO `infra.prod.ping - ping:recovered downForMin=0 url=https://sm-karaoke.ru/`, **And** `firstFailureAt` сбрасывается в `null` (текущее поведение уже делает это, см. `ProdContainerCheck.kt:41`).
3. **Given** ping проходит, потом опять падает, **When** это второй независимый сбой, **Then** в логи пишется WARN как обычно (не INFO), **And** `firstFailureAt` устанавливается в текущее время.
4. **Given** `ProdContainerCheck.pingRemoteDb()` вызывается и JDBC-соединение к прод-БД не проходит, **When** метод обрабатывает исключение, **Then** в логи пишется WARN с категорией `infra.prod.db` (не `infra.prod.ping`): `infra.prod.db - db:failed host=188.119.64.111 port=5433 durationMs=3000 error="Connection refused" exceptionClass=org.postgresql.util.PSQLException`.
5. **Given** в обычном режиме все пинги проходят (типичное состояние), **When** `ProdContainerCheck.run()` вызывается раз в минуту, **Then** в логи НЕ пишется ничего (нет INFO/DEBUG на успехе), **And** счётчик логов не растёт неограниченно.

---

### User Story 3 — Синхронизированная TZ во всех логах прода (Priority: P1)

Разработчик, изучая инцидент, может коррелировать события из разных источников по времени без сдвигов TZ: PostgreSQL `pg_log`, Spring Boot `karaoke-web`/`karaoke-app`, nginx `access.log`/`error.log`. Все логи используют Europe/Moscow (MSK, UTC+3).

**Why this priority**: Сейчас TZ в логах различается: PostgreSQL по умолчанию в UTC, контейнеры Docker тоже в UTC, nginx на хосте скорее всего в MSK (или тоже UTC). Без единой TZ корреляция «postgres-statement в 12:34:56» с «nginx-request в 12:34:56» — это лотерея, могут отличаться на ±3 часа.

**Independent Test**: На admin-машине выполнить `docker logs karaoke-db --since "1m"` → первая строка с префиксом содержит `MSK` (или другой явный TZ-маркер). На `karaoke-web` сделать тестовый запрос и проверить timestamp в stdout контейнера — совпадает с TZ PostgreSQL с точностью до секунд.

**Acceptance Scenarios**:

1. **Given** на проде применён `ALTER SYSTEM SET log_timezone = 'Europe/Moscow'; ALTER SYSTEM SET timezone = 'Europe/Moscow'; SELECT pg_reload_conf();`, **When** PostgreSQL пишет любую строку в `log_line_prefix`, **Then** timestamp в логе имеет TZ Europe/Moscow (например, `2026-09-01 15:34:56 MSK`), **And** `now()` в SQL возвращает MSK-время.
2. **Given** в `docker-compose.yml` для `karaoke-db` добавлен `TZ: Europe/Moscow` env, **When** контейнер перезапускается, **Then** системная TZ контейнера = Europe/Moscow (видно через `docker exec karaoke-db date`).
3. **Given** в JVM-опциях karaoke-web добавлен `-Duser.timezone=Europe/Moscow` (через `WEB_JAVA_OPTS` в `deploy/.env`), **When** Spring Boot логирует событие, **Then** timestamp в stdout имеет TZ Europe/Moscow (соответствует `logback` pattern), **And** `LocalDateTime.now()` в коде возвращает MSK-время.
4. **Given** nginx на проде, **When** он пишет в `access.log`/`error.log`, **Then** строки содержат timestamp в формате ISO 8601 с явным TZ (например, `$time_iso8601` вместо `$time_local` в `log_format`).

---

### User Story 4 — Документация по корреляции логов (Priority: P2)

Разработчик, который впервые столкнулся с инцидентом «прод подвис», открывает один документ (`docs/ops/log-correlation.md` или раздел в LiveDocs) и видит:
- Где находятся логи каждого компонента (PostgreSQL, karaoke-web, karaoke-public, nginx).
- Как их смотреть (`docker logs`, `docker exec ... cat ...`, `tail -f`).
- Как grep'ать по маркерам (`infra.prod.ping`, `duration: N ms`, `$request_time`).
- Как сопоставлять по времени (общий TZ MSK, синхронизация).
- Типичные сценарии («прод завис после одобрения задания» — что смотреть).

**Why this priority**: Без инструкции каждый инцидент = переизобретение корреляции. Не P1, потому что технические фиксы важнее, но без документации эффективность диагностики падает в разы.

**Independent Test**: Новый разработчик (или сам пользователь через месяц) открывает `docs/ops/log-correlation.md`, по нему за 5 минут находит как grep'ать все 4 источника логов по одному timestamp.

**Acceptance Scenarios**:

1. **Given** создан документ `docs/ops/log-correlation.md`, **When** разработчик читает его, **Then** он содержит: (а) карту логов (PostgreSQL, Spring Boot, nginx, MinIO); (б) команды `docker logs`/`docker exec`/`tail -f` с `--since`/`--until`; (в) пример grep по маркерам; (г) типичные сценарии.
2. **Given** документ существует, **When** в README корневой секции (или AGENTS.md) добавлена ссылка, **Then** новый разработчик может найти его через 1 клик.

---

### Edge Cases

- **PostgreSQL рестарт сбрасывает настройки `ALTER SYSTEM`?** — Нет. `ALTER SYSTEM SET` пишет в `postgresql.auto.conf`, который **сохраняется** при рестарте (это отдельный файл от `postgresql.conf`). `pg_reload_conf()` применяет параметры runtime, `restart` — перечитывает `postgresql.auto.conf`.
- **Что если `work_mem` превышен и создаются temp-файлы при обычных запросах?** — С `log_temp_files = 0` это будет логироваться (на каждый temp-файл). Если это создаёт слишком много шума — пользователь может поднять `log_temp_files = 1024` (1 MB) без правки кода (через ещё один `ALTER SYSTEM SET`).
- **Что если TZ контейнера `karaoke-db` нельзя изменить через env `TZ`?** — `postgres:16` поддерживает `TZ` env, влияет на системный TZ. Проверено: docker exec postgres date показывает TZ из env. Дополнительно — `ALTER SYSTEM SET timezone` гарантирует TZ в SQL независимо от системного.
- **Что если `pg_log` смешивается с другим stdout контейнера?** — Да, по умолчанию log_destination=stderr и PostgreSQL не имеет префикса. Без `log_line_prefix` невозможно отличить postgres-логи от других. С этой фичей (`log_line_prefix` + категория) — отличимы через grep `LOG: ` / `WARNING: ` / `ERROR: `.
- **Что если WARN-сообщение `infra.prod.ping` приходит в момент, когда сайт «мигает» (1 сек даунтайм)?** — Это нормальное поведение. `firstFailureAt` устанавливается только при первой неудаче, повторные WARN сбрасываются через `null` при восстановлении (см. `ProdContainerCheck.kt:41`). INFO «recovery» пишется только если `firstFailureAt != null && now()`.
- **Что если у пользователя на проде `pg_max_connections` уже выставлен в 5 (тестовая конфигурация из спеки 174)?** — `ALTER SYSTEM SET log_*` параметры не конфликтуют с `max_connections`. Безопасно.
- **Что если админ случайно включит `log_statement = all`?** — Спека **НЕ** предлагает включать `log_statement` (FR-006 — дефолт `none`). Если кто-то включит отдельно — это уже не наш scope.
- **Что если в `ProdContainerCheck` вызывается `printStackTrace()`?** — Сейчас нет (используется `println(e.message)`). С фичей — `log.warn("...", e)` — будет печатать stacktrace через SLF4J (через `Throwable` параметр).

## Requirements *(mandatory)*

### Functional Requirements

#### PostgreSQL runtime-параметры (FR-001..FR-007)

- **FR-001**: На проде (контейнер `karaoke-db`) MUST быть выполнен `ALTER SYSTEM SET log_min_duration_statement = 1000; SELECT pg_reload_conf();` — логировать SQL-запросы дольше 1 секунды. Значение 1000 мс выбрано как баланс между объёмом логов и шумом (Q1, ответ пользователя: «посмотри сам» → informed guess по результатам анализа текущей нагрузки ~50 RPS).
- **FR-002**: На проде MUST быть выполнен `ALTER SYSTEM SET log_temp_files = 0; SELECT pg_reload_conf();` — логировать ВСЕ temp-файлы, которые создаются при превышении `work_mem` (диагностика тяжёлых JOIN/sort).
- **FR-003**: На проде MUST быть выполнен `ALTER SYSTEM SET log_lock_waits = on; SELECT pg_reload_conf();` — логировать сессии, ждущие блокировки дольше `deadlock_timeout` (1 сек). Дефолт postgres:16 = on, но явная установка через `ALTER SYSTEM` для идемпотентности (если кто-то сбросил параметр).
- **FR-004**: На проде MUST быть выполнен `ALTER SYSTEM SET log_autovacuum_min_duration = 0; SELECT pg_reload_conf();` — логировать ВСЮ работу автовакуума (включая короткую). Полезно для отлова ситуации, когда автовакуум не успевает за ростом таблицы (`tbl_events`).
- **FR-005**: На проде MUST быть выполнен `ALTER SYSTEM SET log_checkpoints = on; SELECT pg_reload_conf();` — логировать checkpoint'ы (когда pg записывает dirty buffers на диск). Полезно для отлова I/O-давления.
- **FR-006**: На проде MUST быть выполнен `ALTER SYSTEM SET log_line_prefix = '%m [%p] %q%u@%d from %h '; SELECT pg_reload_conf();` — добавить префикс строк лога: timestamp с TZ, PID, user@database, host. Без этого строки PostgreSQL не имеют timestamp и не коррелируются с другими логами. Документация: https://www.postgresql.org/docs/16/runtime-config-logging.html#GUC-LOG-LINE-PREFIX.
- **FR-007**: На проде MUST быть выполнен `ALTER SYSTEM SET log_timezone = 'Europe/Moscow'; ALTER SYSTEM SET timezone = 'Europe/Moscow'; SELECT pg_reload_conf();` — установить TZ = Europe/Moscow для логов и для `now()`. Без этого логи PostgreSQL — в UTC (TZ контейнера по умолчанию).

#### Контейнер karaoke-db (FR-008..FR-009)

- **FR-008**: В `deploy/docker-compose-database.yml` MUST быть добавлена переменная окружения `TZ: Europe/Moscow` в секцию `environment` сервиса `karaoke-db`. Это гарантирует системную TZ контейнера (= MSK) при создании контейнера с нуля. Влияние на уже работающий контейнер — нет (для применения нужен рестарт, см. FR-009).
- **FR-009**: Документация по фиче MUST явно отмечать, что применение `TZ=Europe/Moscow` к **уже работающему** контейнеру требует `docker restart karaoke-db` (per Q2 пользователь выбрал НЕ делать рестарт в этой фиче; см. Assumptions A-001). Применение через `ALTER SYSTEM SET timezone` (FR-007) уже работает для SQL-уровня без рестарта. Для полной консистентности TZ рестарт БД отложен в backlog (см. Out of Scope).

#### JVM karaoke-web (FR-010)

- **FR-010**: В `deploy/.env` MUST быть добавлен `WEB_JAVA_OPTS=-Xmx2g -Duser.timezone=Europe/Moscow` (или эквивалент, дополняющий существующий `WEB_JAVA_OPTS`). Это обеспечит TZ = MSK для JVM karaoke-web (через user.timezone JVM-параметр). Применяется при рестарте контейнера karaoke-web (next deploy). Применимо к `karaoke-public` если используется Spring Boot (сейчас нет — nginx + статика).

#### Удалить println, добавить SLF4J в ProdContainerCheck (FR-011..FR-016)

- **FR-011**: В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/checks/ProdContainerCheck.kt:82` MUST быть удалён `println("ProdContainerCheck: ping $PING_URL не удался: ${e.message}")` и заменён на `log.warn(...)` с логгером `infra.prod.ping` (явная категория для grep'а), с structured args по local-0005 (`%X` для MDC не нужен, но key=value формат обязателен).
- **FR-012**: `ProdContainerCheck.pingSite()` MUST логировать **длительность** каждого вызова (даже при ошибке — `durationMs` от `System.currentTimeMillis()` до catch). Это нужно для baseline: если пинг начал занимать 3 сек вместо обычных 200 мс — это уже индикатор проблемы.
- **FR-013**: `ProdContainerCheck.pingSite()` MUST возвращать длительность пинга из метода (`Pair<Boolean, Long>` или через out-parameter / поле в `MonitorContext`). Это нужно для логирования и в будущем — для передачи в алерт.
- **FR-014**: `ProdContainerCheck.run()` MUST логировать INFO `infra.prod.ping - ping:recovered downForMin=N url=...` **при смене состояния** с WARNING/CRITICAL → OK (когда `firstFailureAt != null` И `siteUp && dbUp` стали true). В обычном режиме (когда пинги проходят постоянно) — НЕ логировать ничего.
- **FR-015**: `ProdContainerCheck.pingRemoteDb()` MUST логировать WARN с категорией `infra.prod.db` (не `infra.prod.ping`) при ошибке JDBC-соединения. Должен включать `host`, `port`, `durationMs`, `error`, `exceptionClass`.
- **FR-016**: `ProdContainerCheck.pingRemoteDb()` MUST возвращать длительность пинга-БД (для consistency с `pingSite`).

#### KDoc и FR-006 Constitution (FR-017..FR-018)

- **FR-017**: `ProdContainerCheck` MUST иметь KDoc-комментарий с `@see livedocs/architecture/decisions/local-0005-structured-logging-karaoke-app.md` (per Constitution FR-006 — public API с KDoc). KDoc должен описывать: (а) почему теперь SLF4J вместо println; (б) почему WARN на ошибке, INFO на восстановлении, NO-OP на успехе в обычном режиме; (в) маркеры для grep (`infra.prod.ping`, `infra.prod.db`).
- **FR-018**: Per-feature документ MUST быть создан или обновлён. Поскольку `ProdContainerCheck` — это не новая фича, а модификация существующей, документ добавляется в `livedocs/features/` (можно как часть существующего `154-remove-scheduled-publications-monitoring.md` или новый `288-prod-diagnostics-logging.md`). Решение — в `plan.md` (`D-1`).

#### Документация по корреляции логов (FR-019..FR-020)

- **FR-019**: Создать документ `docs/ops/log-correlation.md` (или `livedocs/runbooks/how-to-correlate-prod-logs.md` — решение в `plan.md`). Документ MUST содержать: (а) карту источников логов (PostgreSQL, Spring Boot, nginx, MinIO); (б) команды просмотра (`docker logs`, `docker exec`, `tail -f`) с `--since`/`--until` и TZ; (в) маркеры для grep (PostgreSQL: `LOG:`, `WARNING:`, `ERROR:`, `duration:`, `temporary file:`; Spring Boot: `infra.prod.ping`, `infra.prod.db`; nginx: `$request_time`); (г) типичные сценарии («прод завис после одобрения задания — что смотреть»).
- **FR-020**: Ссылка на `docs/ops/log-correlation.md` MUST быть добавлена в `AGENTS.md` (секция «LiveDocs CI / pre-commit» или новая секция «Где смотреть логи прода») и в `livedocs/README.md` (если документ живёт в `livedocs/runbooks/`).

#### Конституционные инварианты (FR-021..FR-023)

- **FR-021**: Фиксы НЕ ДОЛЖНЫ нарушать Constitution: (а) никакого JPA/Hibernate (только сырой JDBC); (б) `recordhash`-триггеры не должны измениться; (в) `SyncRegistry` остаётся как есть; (г) никаких изменений в `KaraokeConnection.kt` или `Connection.local()/remote()/virtual()`.
- **FR-022**: Никакие секреты НЕ ДОЛЖНЫ попасть в логи (per Constitution § VIII.5). В частности, `Connection.remote()` URL может содержать пароль — НЕ логировать полный JDBC URL, только `host` и `port`.
- **FR-023**: Per AGENTS.md «Обязательная проверка после ЛЮБОГО изменения кода» — после реализации FR-011..FR-016 MUST быть выполнен полный pipeline проверки: compile, lint, bootJar, vite, docker-образ (если менялся karaoke-public/webvue3 — но в этой фиче только karaoke-app код).

### Key Entities *(include if feature involves data)*

- **PostgreSQL runtime-parameter** (`log_min_duration_statement`, `log_temp_files`, и т.д.): ключ-значение в `postgresql.auto.conf`. Применяется через `pg_reload_conf()` (runtime) или `restart` (postgresql.conf overrides). Сохраняется при рестарте контейнера.
- **Категория логгера** (`infra.prod.ping`, `infra.prod.db`): строка-идентификатор для SLF4J `LoggerFactory.getLogger("infra.prod.ping")`. Используется как grep-маркер и для условного форматирования (`<logger name="infra.prod.*" level="..."/>` в logback-spring.xml, если будет).
- **PingState** (логическое состояние в `ProdContainerCheck`): `OK`, `WARNING`, `CRITICAL`. Только переходы `OK → WARNING/CRITICAL` (логируется WARN) и `WARNING/CRITICAL → OK` (логируется INFO) пишутся в лог. Стационарные состояния — нет.

### Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: После применения FR-001..FR-007 на проде — `docker logs karaoke-db --since "1m"` показывает префикс каждой строки с timestamp `YYYY-MM-DD HH:MM:SS MSK`. Без фикса префикс пустой (`log_line_prefix = ''` дефолт).
- **SC-002**: После применения FR-001..FR-007 — `ALTER SYSTEM SET log_min_duration_statement = 1000; SELECT pg_sleep(2);` (тестовая команда, не на проде, на admin-машине) порождает строку `LOG: duration: 2000 ms statement: SELECT pg_sleep(2)` в `docker logs karaoke-db`. `SELECT pg_sleep(0.5)` НЕ порождает строку (ниже порога).
- **SC-003**: После применения FR-011..FR-016 — `grep -c "ProdContainerCheck: ping" $(docker logs karaoke-app --since "1d")` возвращает 0 (нет больше `println`-сообщений в формате «ProdContainerCheck: ping»). Заменены на `grep -c "infra.prod.ping" $(docker logs karaoke-app --since "1d")` — возвращает N строк (сколько было WARN за день).
- **SC-004**: После применения FR-014 — в нормальном режиме (все пинги OK) за 24 часа логирование `infra.prod.ping` порождает 0 строк (только при смене состояния). Замер: `grep -c "infra.prod.ping" $(docker logs karaoke-app --since "1d")` = 0.
- **SC-005**: После применения FR-019..FR-020 — новый разработчик может найти `docs/ops/log-correlation.md` через 1 grep в репо: `find . -name "log-correlation.md"` или через ссылку в AGENTS.md. Время поиска ≤ 1 минута.
- **SC-006**: При реальном инциденте (например, «прод завис на 5 минут после одобрения задания редактора») — за 15 минут можно найти: (а) точный момент начала деградации (через `pg_log`); (б) какой именно SQL вызвал задержку; (в) корреляция с `nginx access.log` по timestamp (через общий TZ MSK); (г) корреляция с `ProdContainerCheck` WARN (через тот же TZ). Без фикса эта задача занимает часы или невозможна.
- **SC-007**: Фикс НЕ приводит к значительному росту объёма логов PostgreSQL. Baseline (дефолт postgres:16): ~10-50 строк/день (`log_lock_waits` + autovacuum). После фикса: ≤100 строк/день при нормальной нагрузке (основной источник — `log_min_duration_statement`, ожидаемо ≤ 50 медленных запросов/день). На инциденте — больше (это и есть цель).

## Assumptions

- **A-001** (per Q2, ответ пользователя «НЕ включать logging_collector — stderr достаточно»): в этой фиче НЕ делается `docker restart karaoke-db`. Все FR-001..FR-007 применяются через `ALTER SYSTEM SET` + `pg_reload_conf()` — runtime-параметры, не требуют рестарта кластера. `logging_collector=on` и `TZ=Europe/Moscow` для контейнера (FR-008) добавляются в `docker-compose-database.yml`, но **применяются только при следующем создании контейнера с нуля** (например, при `docker-compose up --force-recreate`). Существующий контейнер сохраняет дефолт UTC.
- **A-002**: Текущая нагрузка на проде ~50 RPS (visitor + admin + API). На этой нагрузке `log_min_duration_statement = 1000` даёт оценочно 30-100 записей/день (достаточно для отлова аномалий, не заваливает лог). Если реальная нагрузка выше — пользователь может поднять порог (например, до 2000-3000 мс) без правки кода, через ещё один `ALTER SYSTEM SET log_min_duration_statement`.
- **A-003**: Пользователь работает в РФ, TZ Europe/Moscow (MSK, UTC+3). Если в будущем команда переедет — TZ поменяется одним `ALTER SYSTEM SET log_timezone = ...`.
- **A-004**: На проде используется `postgres:16` (см. `deploy/docker-compose-database.yml:4`). Все параметры в этой спеке — runtime или применяются через `ALTER SYSTEM` + reload, валидно для PostgreSQL 14+ (16 проверено).
- **A-005**: `pg_log` доступен через `docker logs karaoke-db --since "1m" --until "2m ago"`. Эти фильтры поддерживаются Docker 18+ (на проде давно 20+). Альтернатива — `docker exec karaoke-db bash -c 'cat /var/lib/postgresql/data/16/log/postgresql-*.log'`, но это работает только если `logging_collector=on` (которого нет — см. A-001).
- **A-006**: `ProdContainerCheck` запускается из `MonitoringService.tick()` раз в минуту (см. `MonitorRegistry.kt:18` и `MonitoringService.kt:40`). На admin-машине, не на проде (см. Constitution § Технологический стек: «karaoke-app на проде не разворачивается вовсе»). Сообщения от `ProdContainerCheck` идут в **stdout admin-машины** (где запущен `karaoke-app`), а не в логи прод-контейнеров. Корреляция — по timestamp (общий TZ MSK) с логами `docker logs karaoke-web`/`karaoke-db`/`nginx`.
- **A-007**: Логи `karaoke-web` на проде — stdout контейнера (`docker logs karaoke-web`). Внутри Spring Boot использует Logback (дефолт), формат `timestamp [thread] LEVEL logger - message` (дефолт Logback). TZ в логах — TZ JVM (UTC по умолчанию в Docker). После FR-010 — Europe/Moscow.
- **A-008**: nginx на проде работает **вне** Docker (см. `deploy/web-server-deploy/deploy/80to8897`), на хосте скорее всего в TZ MSK (или UTC — зависит от настройки хоста). Корреляция через `$time_iso8601` (явный TZ) — если `$time_local` (дефолт), то формат без TZ. Изменение `log_format` в nginx требует редактирования конфига на хосте — это Out of Scope (см. ниже).
- **A-009**: Все FR-001..FR-007 — это DDL/DML к серверной БД (`ALTER SYSTEM SET`). По Constitution § «Категорически запрещено» п. 2 это требует **прямого согласия пользователя на каждое `ALTER SYSTEM SET`**. Спека описывает ЧТО сделать; выполнение — пользователем или под явным согласием пользователя. Агент НЕ выполняет `ALTER SYSTEM SET` без явного одобрения в каждой сессии.
- **A-010**: Per Constitution § VI FR-006, KDoc на `ProdContainerCheck` обновляется как часть этой фичи. Низкий приоритет (документация), но требуется для consistency.

## Out of Scope

- **Включение `pg_stat_statements`** (FR-108 спеки 241) — перенесено в backlog, требует `shared_preload_libraries` + рестарт кластера. Не делается.
- **Включение `logging_collector=on`** (FR-002 этого scope не касается) — отложено, требует рестарта (per Q2 пользователь выбрал stderr).
- **Изменение nginx `log_format`** на `$time_iso8601` — требует редактирования файлов на сервере (Constitution § п. 3 «Категорически запрещено»). Отдельная фича.
- **Полноценный ELK / Kibana / Grafana / Loki** для централизованного хранения — overkill для текущего объёма (per local-0005.md «Alternatives Considered»). Сейчас `docker logs` + grep достаточно.
- **Логирование в `karaoke-public`** (Vue 3 + Vite SPA) — это статика в nginx, нет backend-логов. Логи nginx покрывают.
- **Замена println в других местах** кода (например, `KaraokeProcessWorker`, `SyncRegistry.tick()`) — точечный фикс только для `ProdContainerCheck`. Полный аудит `println` — отдельная фича.
- **Полнотекстовое логирование SQL (`log_statement = all`)** — завалит лог, не делается.
- **Мониторинг `pg_log` объёма и ротация** — отдельная фича, требует `log_rotation_age`/`log_rotation_size` + файлового логирования.
- **Изменение `KaraokeConnection.kt` или `Connection.local()/remote()/virtual()`** — Constitution § II (сырой JDBC), не трогаем.

## Open Questions (для `/speckit.clarify`)

Все 3 вопроса резолвнуты в текущей сессии (см. секцию `## Clarifications`). Дополнительных неоднозначностей для этой сессии не выявлено — оставшиеся архитектурные детали (формат logback-spring.xml, способ доставки `ALTER SYSTEM SET` через `karaoke-app` admin-эндпоинт или ручной psql) вынесены в `plan.md` как технические решения.

## Clarifications

### Session 2026-09-01

- **Q1**: Какой порог для `log_min_duration_statement` на проде? → **A: 1000 мс (1 сек)**. Пользователь ответил «посмотри сам» — на основе текущей нагрузки ~50 RPS и спеки 241 (FR-108 в backlog) выбран баланс: видны аномалии, минимум шума. Пользователь может поднять порог позже без правки кода (через ещё один `ALTER SYSTEM SET`).
- **Q2**: Включать ли `logging_collector=on` для записи PostgreSQL-логов в файлы? → **A: НЕ включать (stderr достаточно)**. Минимальное воздействие на прод, логи через `docker logs karaoke-db`. `TZ=Europe/Moscow` для контейнера — в `docker-compose-database.yml`, применяется при следующем `--force-recreate` (см. A-001).
- **Q3**: На каком уровне логировать успешный пинг `ProdContainerCheck`? → **A: только INFO при СМЕНЕ состояния** (WARN на ошибке, INFO при восстановлении, NO-OP в обычном режиме). Минимум шума, базовая диагностика при смене состояния.