# Contract: Формат лог-сообщений `infra.prod.ping` и `infra.prod.db`

**Привязка**: [specs/288-prod-diagnostics-logging/spec.md](../spec.md) — FR-011..FR-016

> «Контракт» в этой фиче — это **формат log-сообщений**, не HTTP API. Это соглашение между кодом (`ProdContainerCheck`) и инструментами диагностики (grep, awk, logback appender).

---

## 1. Категории логгеров

| Имя категории | Назначение | Уровни | Класс-владелец |
|---------------|------------|--------|----------------|
| `infra.prod.ping` | HTTP-пинг прод-сайта `https://sm-karaoke.ru/` | WARN, INFO | `ProdContainerCheck.pingSite()` |
| `infra.prod.db` | JDBC-пинг прод-БД через `Connection.remote()` | WARN | `ProdContainerCheck.pingRemoteDb()` |

### Идинификация через SLF4J

```kotlin
import org.slf4j.LoggerFactory

private val pingLog = LoggerFactory.getLogger("infra.prod.ping")
private val dbLog = LoggerFactory.getLogger("infra.prod.db")
```

---

## 2. Формат WARN-сообщения (HTTP-пинг неуспешен)

### Шаблон

```
{TIMESTAMP} {LEVEL} {CATEGORY} - {EVENT_KEY} {KEY=VALUE} ...
```

### Поля

| Поле | Обязательно | Тип | Описание | Пример |
|------|-------------|-----|----------|--------|
| `TIMESTAMP` | yes | ISO 8601 / Logback pattern | `yyyy-MM-dd HH:mm:ss.SSS TZ` (Europe/Moscow после FR-010) | `2026-09-01 12:34:56.789 MSK` |
| `LEVEL` | yes | WARN/INFO/ERROR | — | `WARN` |
| `CATEGORY` | yes | String | имя логгера | `infra.prod.ping` |
| `EVENT_KEY` | yes | String | ключ события в формате `domain:action` | `ping:failed` |
| `url` | yes | String | URL пинга | `https://sm-karaoke.ru/` |
| `durationMs` | yes | Long | Длительность пинга в мс | `5000` |
| `error` | yes | String | Сообщение ошибки (кавычки экранированы) | `"Read timed out"` |
| `exceptionClass` | yes | String | Класс исключения (FQN) | `java.net.SocketTimeoutException` |

### Пример полного сообщения

```
2026-09-01 12:34:56.789 MSK WARN infra.prod.ping - ping:failed url=https://sm-karaoke.ru/ durationMs=5000 error="Read timed out" exceptionClass=java.net.SocketTimeoutException
```

### Stacktrace

Передаётся через SLF4J `Throwable`-параметр, не в message:

```kotlin
pingLog.warn(
    "ping:failed url={} durationMs={} error=\"{}\" exceptionClass={}",
    url, durationMs, e.message, e::class.java.name,
    e  // stacktrace в SLF4J-формате
)
```

### Парсинг (пример для grep + awk)

```bash
# Все неуспешные пинги за последний час
docker logs karaoke-app --since "1h" | grep "infra.prod.ping" | grep "ping:failed"

# Средняя длительность неуспешных пингов
docker logs karaoke-app --since "1d" | grep "infra.prod.ping" | grep "ping:failed" | \
  awk '{for(j=1;j<=NF;j++) if(match($j, /durationMs=([0-9]+)/, m)) print m[1]}' | \
  awk '{sum+=$1; n++} END {if(n>0) print "avg:", sum/n, "ms (n=" n ")"}'
```

---

## 3. Формат INFO-сообщения (восстановление после сбоя)

### Шаблон

```
{TIMESTAMP} {LEVEL} {CATEGORY} - {EVENT_KEY} {KEY=VALUE} ...
```

### Поля

| Поле | Обязательно | Тип | Описание | Пример |
|------|-------------|-----|----------|--------|
| `EVENT_KEY` | yes | String | ключ события | `ping:recovered` |
| `url` | yes | String | URL пинга | `https://sm-karaoke.ru/` |
| `downForMin` | yes | Long | Минут с момента первого сбоя | `30` |

### Пример

```
2026-09-01 13:05:00.000 MSK INFO infra.prod.ping - ping:recovered url=https://sm-karaoke.ru/ downForMin=30
```

### Условие записи

Пишется **только при смене состояния** с WARNING/CRITICAL → OK (per FR-014, spec.md, Acceptance Scenario 2):
- `firstFailureAt != null` (то есть был зафиксирован сбой)
- `siteUp && dbUp` (оба пинга прошли)
- В обычном режиме (когда пинги постоянно OK) — НЕ пишется.

---

## 4. Формат WARN-сообщения (JDBC-пинг-БД неуспешен)

### Шаблон

```
{TIMESTAMP} {LEVEL} {CATEGORY} - {EVENT_KEY} {KEY=VALUE} ...
```

### Поля

| Поле | Обязательно | Тип | Описание | Пример |
|------|-------------|-----|----------|--------|
| `CATEGORY` | yes | String | `infra.prod.db` (отличается от ping) | `infra.prod.db` |
| `EVENT_KEY` | yes | String | ключ события | `db:failed` |
| `host` | yes | String | Host прод-БД (БЕЗ пароля) | `188.119.64.111` |
| `port` | yes | Int | Port прод-БД | `5433` |
| `durationMs` | yes | Long | Длительность пинга-БД в мс | `3000` |
| `error` | yes | String | Сообщение ошибки | `"Connection refused"` |
| `exceptionClass` | yes | String | Класс исключения | `org.postgresql.util.PSQLException` |

### Пример

```
2026-09-01 12:34:56.789 MSK WARN infra.prod.db - db:failed host=188.119.64.111 port=5433 durationMs=3000 error="Connection refused" exceptionClass=org.postgresql.util.PSQLException
```

### Security note (FR-022)

`Connection.remote()` возвращает JDBC URL, который может содержать пароль. **НЕ ДОПУСКАЕТСЯ** логировать полный JDBC URL — только `host` и `port`. Это per Constitution § VIII.5.

Пример ошибки (DO NOT):
```kotlin
dbLog.warn("db:failed url={}", db.url)  // ❌ содержит пароль в URL
```

Пример правильный:
```kotlin
dbLog.warn("db:failed host={} port={} ...", host, port, ...)  // ✓ только host/port
```

---

## 5. Формат WARN-сообщения (recovery БД)

Аналогично ping:recovered, но для БД:

```
{TIMESTAMP} {LEVEL} {CATEGORY} - {EVENT_KEY} {KEY=VALUE} ...
```

| Поле | Обязательно | Тип | Описание |
|------|-------------|-----|----------|
| `CATEGORY` | yes | String | `infra.prod.db` |
| `EVENT_KEY` | yes | String | `db:recovered` |
| `host` | yes | String | Host прод-БД |
| `port` | yes | Int | Port прод-БД |
| `downForMin` | yes | Long | Минут с момента сбоя БД |

### Пример

```
2026-09-01 13:05:00.000 MSK INFO infra.prod.db - db:recovered host=188.119.64.111 port=5433 downForMin=15
```

---

## 6. Условное форматирование в logback-spring.xml (если будет создан)

Если в будущем будет создан `karaoke-app/src/main/resources/logback-spring.xml`:

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <!--
                Logback default pattern + MDC context.
                TZ зависит от JVM (после FR-010 будет Europe/Moscow).
            -->
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS XXX} [%thread] %-5level %logger{36} - %msg %X%n</pattern>
        </encoder>
    </appender>

    <!-- Default levels -->
    <logger name="com.svoemesto.karaokeapp" level="INFO"/>
    <logger name="org.postgresql" level="WARN"/>

    <!-- Infra категории (эта фича) -->
    <logger name="infra.prod.ping" level="INFO"/>
    <logger name="infra.prod.db" level="INFO"/>

    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

`%d{...} XXX` в паттерне означает ISO 8601 timezone (`+03:00` для MSK) — это **backup** на случай, если JVM TZ не настроен (per FR-010). После FR-010 это работает даже без явного pattern (JVM TZ = Europe/Moscow).

---

## 7. Совместимость с другими инструментами

| Инструмент | Поддержка формата | Примечание |
|------------|-------------------|------------|
| `grep` (POSIX) | ✓ | `grep "infra.prod.ping"` — substring match |
| `awk` | ✓ | `$j` для key=value парсинга |
| `cut -d'='` | ✓ | Разделение по `=` |
| ELK / Kibana | ✓ (через JSON-конвертер) | Текущий scope не предполагает ELK (per Out of Scope) |
| Datadog / Sentry | ✓ (через JSON-конвертер) | Текущий scope не предполагает |

---

## История

- Создан: 2026-09-01 (Phase 1)