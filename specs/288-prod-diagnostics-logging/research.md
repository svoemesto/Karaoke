# Research: Технические решения для спеки 288-prod-diagnostics-logging

**Дата**: 2026-09-01
**Привязка**: [specs/288-prod-diagnostics-logging/spec.md](./spec.md)

> Phase 0 output — резюме research по каждому техническому решению. Все NEEDS CLARIFICATION уже резолвнуты в спеке (3 вопроса, см. Clarifications секцию spec.md). Этот документ фиксирует best practices и подтверждает informed guesses.

## Решение 1: `log_min_duration_statement = 1000` (1 сек)

### Decision
Установить `ALTER SYSTEM SET log_min_duration_statement = 1000;` — логировать SQL-запросы дольше 1 секунды.

### Rationale
- **Текущая нагрузка**: ~50 RPS на проде (visitor + admin + API) — оценка из спеки 241 (FR-108 → backlog, но baseline-нагрузка осталась).
- **Ожидаемый объём логов**: 30-100 записей/день при нормальной работе (достаточно для отлова аномалий, не заваливает лог).
- **PostgreSQL docs**: `log_min_duration_statement` — runtime-параметр, изменяется через `ALTER SYSTEM SET` + `pg_reload_conf()` без рестарта кластера (https://www.postgresql.org/docs/16/runtime-config-logging.html#GUC-LOG-MIN-DURATION-STATEMENT).
- **Пользователь может динамически поднять порог** (например, до 2000-3000 мс) без правки кода, если объём логов окажется больше ожидаемого.

### Alternatives Considered
- **0 (логировать всё)**: rejected — слишком много шума при нормальной нагрузке (5000+ записей/день).
- **500 мс**: rejected как дефолт — больше логов, включая polling-эндпоинты с допустимыми задержками (но это можно настроить позже).
- **3000 мс**: rejected как дефолт — пропустит умеренные проблемы (например, JOIN на 5000 записей, занимающий 2 сек).
- **-1 (выключено)**: rejected — это текущий дефолт postgres:16, основная причина, по которой спека написана.

### Validation
- На admin-машине выполнить `ALTER SYSTEM SET log_min_duration_statement = 0; SELECT pg_reload_conf(); SELECT pg_sleep(2);` — `docker logs karaoke-db` показывает строку `LOG: duration: 2000 ms statement: SELECT pg_sleep(2)`. Вернуть `log_min_duration_statement = 1000` после теста.

---

## Решение 2: `log_line_prefix = '%m [%p] %q%u@%d from %h '`

### Decision
Установить `ALTER SYSTEM SET log_line_prefix = '%m [%p] %q%u@%d from %h ';` — префикс строк PostgreSQL-лога: timestamp с TZ, PID, user@database, host.

### Rationale
- `%m` — timestamp с миллисекундами в формате `YYYY-MM-DD HH:MM:SS.SSS TZ` (https://www.postgresql.org/docs/16/runtime-config-logging.html#GUC-LOG-LINE-PREFIX).
- `[%p]` — PID процесса PostgreSQL (полезно для отслеживания конкретной сессии).
- `%q` — пусто если нет кавычек (стандартная практика).
- `%u@%d` — `user@database` (например, `karaoke@karaoke`) — какой пользователь какой БД запрашивает.
- `from %h` — IP хоста клиента (для pg_log это **IP контейнеров** Docker network или admin-машины, **не** IP пользователей сайта).

### Compliance: IP в pg_log ≠ PII
- `%h` логирует IP **инфраструктурных** клиентов (контейнеры `karaoke-web`, admin-машина), а **не** IP конечных пользователей сайта (те подключаются только к nginx → karaoke-web → БД через Docker network).
- IP инфраструктурных компонентов **не считается ПДн** по 152-ФЗ РФ (нет связи с конкретным человеком, это служебный endpoint).
- Compliance-анализ подтверждён — IP остаётся в полном виде (`%h`), без маскирования.

### Alternatives Considered
- **`'%m [%p] %u@%d '`** (без `%h` и `%q`): rejected — потеряем информацию о клиенте (полезно для отслеживания, какой контейнер делает медленные запросы).
- **`'%t [%p]: [%l-1] user=%u,db=%d '`** (стандартный пример из документации): rejected — `%l` — это session line number (для syslog), не нужно для stderr-вывода.
- **`''`** (пустой, дефолт postgres:16): rejected — без timestamp корреляция с другими логами невозможна.

### Validation
- `docker logs karaoke-db --since "1m"` показывает первую строку любой записи в формате `2026-09-01 12:34:56.789 MSK [12345] karaoke@karaoke from 172.18.0.5 LOG: ...`.

---

## Решение 3: `log_temp_files = 0`

### Decision
Установить `ALTER SYSTEM SET log_temp_files = 0;` — логировать ВСЕ temp-файлы, создаваемые при превышении `work_mem`.

### Rationale
- Диагностика тяжёлых JOIN/sort/hash, превышающих `work_mem` (дефолт 4 MB).
- `log_temp_files = 0` логирует каждый temp-файл с указанием размера — для большинства запросов = 0 записей (нет temp-файлов).
- Для аномально тяжёлых запросов — запись в логе с размером temp-файла (прямой индикатор проблемы).
- Runtime-параметр (https://www.postgresql.org/docs/16/runtime-config-logging.html#GUC-LOG-TEMP-FILES).

### Alternatives Considered
- **`-1`** (дефолт): rejected — не логируется ничего, основная причина проблемы (большие temp-файлы) не видна.
- **`1024`** (1 MB): rejected как дефолт — но пользователь может поднять, если окажется, что мелкие temp-файлы создают шум.

---

## Решение 4: Категория логгера `infra.prod.ping` и `infra.prod.db`

### Decision
Использовать `LoggerFactory.getLogger("infra.prod.ping")` и `LoggerFactory.getLogger("infra.prod.db")` — **явные категории** для grep'а и условного форматирования.

### Rationale
- По local-0005 ([livedocs/architecture/decisions/local-0005-structured-logging-karaoke-app.md](../../livedocs/architecture/decisions/local-0005-structured-logging-karaoke-app.md)) рекомендуется `LoggerFactory.getLogger(javaClass)`, но для **категоризированных логов** (cross-cutting concern: мониторинг инфраструктуры) явная категория лучше.
- Преимущества:
  - `grep "infra.prod.ping" log.txt` находит ВСЕ записи о пинге прода (даже если они в разных классах).
  - logback-spring.xml (если будет создан) может настроить отдельный appender для этой категории через `<logger name="infra.prod.*" level="INFO"/>`.
- Аналогия с существующим `PublicSongeditorController.selfAssignLog` (см. [karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicSongeditorController.kt:19](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicSongeditorController.kt)) — категория `PublicSongeditorController` используется как grep-маркер.

### Alternatives Considered
- **`LoggerFactory.getLogger(ProdContainerCheck::class.java)`** (по local-0005): rejected — нельзя grep'ать по категории, приходится знать имя класса.
- **`LoggerFactory.getLogger("monitor.checks.ProdContainerCheck")`**: rejected — слишком длинное имя для grep'а, без выигрыша по сравнению с `infra.prod.ping`.

---

## Решение 5: Формат WARN/INFO сообщения в `ProdContainerCheck`

### Decision
Использовать key=value формат (per local-0005), примеры:

**WARN при неуспешном пинге:**
```
2026-09-01 12:34:56.789 MSK WARN infra.prod.ping - ping:failed url=https://sm-karaoke.ru/ durationMs=5000 error="Read timed out" exceptionClass=java.net.SocketTimeoutException
```

**INFO при восстановлении:**
```
2026-09-01 13:05:00.000 MSK INFO infra.prod.ping - ping:recovered downForMin=30 url=https://sm-karaoke.ru/
```

**WARN при неуспешном пинге-БД:**
```
2026-09-01 12:34:56.789 MSK WARN infra.prod.db - db:failed host=188.119.64.111 port=5433 durationMs=3000 error="Connection refused" exceptionClass=org.postgresql.util.PSQLException
```

### Rationale
- Соответствует local-0005 (structured key=value, не plain text).
- Все поля разделены пробелами — легко парсить через `awk '{for(i=1;i<=NF;i++) print $i}'` или `cut -d'=' -f2`.
- Exception передаётся через SLF4J `Throwable`-параметр (не в message) — stacktrace попадает в лог автоматически.

---

## Решение 6: Способ применения `ALTER SYSTEM SET` на проде

### Decision
Применять через `docker exec karaoke-db psql -U postgres -d " karaoke" -c "ALTER SYSTEM SET ..."`.

### Rationale
- Альтернативы:
  - **SSH + psql**: rejected — требует прямого SSH-доступа к хосту, отдельная команда.
  - **Admin-эндпоинт karaoke-web**: rejected — добавляет attack surface (даже с IP-allowlist), спека FR-013 спеки 187 уже добавила `/api/public/debug/db` — больше не нужно.
  - **Прямой JDBC из скрипта**: rejected — лишний шаг, docker exec проще.
- `docker exec` — стандартный паттерн в Karaoke-проекте для операций с контейнерами (см. `Настройка сервера.md:36`).
- Кредды postgres в env `DB_LOCAL_POSTGRES_PASSWORD` — передаются через env-переменную или интерактивный prompt.

### Требования
- Per Constitution § «Категорически запрещено» п. 2: выполнение `ALTER SYSTEM SET` на проде требует **прямого согласия пользователя на каждое действие**. Агент НЕ выполняет без явного одобрения в каждой сессии (per spec A-009).

---

## Решение 7: Расположение документа `log-correlation.md` — `docs/ops/`

### Decision
Создать документ в `docs/ops/log-correlation.md` (а не в `livedocs/runbooks/`).

### Rationale
- `docs/ops/` — новая директория для операционных документов (runbooks, инструкции по деплою, логирование). Создаётся как часть этой фичи.
- Альтернатива `livedocs/runbooks/` — там уже есть `how-to-*.md` (например, `how-to-debug-connection-leak.md`), но формат LiveDocs предполагает cross-cutting concerns, а `log-correlation.md` — операционный документ для админов/дежурных.
- `docs/ops/` — стандартная конвенция для ops-документации в индустрии (datadog, kubernetes).

### Alternatives Considered
- **`livedocs/runbooks/how-to-correlate-prod-logs.md`**: rejected — LiveDocs — для architectural knowledge (cross-cutting), а это operational runbook.
- **`README.md`** в корне проекта: rejected — слишком заметное место для технической инструкции.
- **KAROKE-specific docs index**: rejected — на данный момент нет такого файла.

---

## Решение 8: Per-feature документ — обновление существующего

### Decision
Добавить изменения `ProdContainerCheck` в существующий per-feature документ [livedocs/features/154-remove-scheduled-publications-monitoring.md](../../livedocs/features/154-remove-scheduled-publications-monitoring.md) — там уже есть секция про monitoring-checks, можно дополнить.

### Rationale
- `ProdContainerCheck` — не новая фича, а модификация существующего check.
- Документ 154 уже описывает мониторинг-чеки (включая `ProdContainerCheck`).
- Создание нового `livedocs/features/288-prod-diagnostics-logging.md` дублировало бы информацию и нарушало принцип «один источник правды».

### Alternatives Considered
- **Новый документ `livedocs/features/288-prod-diagnostics-logging.md`**: rejected — избыточно, 154 уже покрывает.
- **Без per-feature документа**: rejected — нарушает Constitution FR-006 (KDoc обязателен, но per-feature ссылка нужна для traceability).

---

## Сводка NEEDS CLARIFICATION — все резолвнуты

| ID | Тема | Решение | Источник |
|----|------|---------|----------|
| Q1 | Порог `log_min_duration_statement` | 1000 мс | Clarifications Q1 (spec.md) |
| Q2 | `logging_collector=on`? | НЕ включать | Clarifications Q2 (spec.md) |
| Q3 | Уровень успешного пинга | INFO только при смене состояния | Clarifications Q3 (spec.md) |

Дополнительные архитектурные решения (D-1..D-6 выше) — резолвнуты в этом `research.md` как informed guesses с обоснованием.