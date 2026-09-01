# Quickstart: 288-prod-diagnostics-logging — End-to-End Validation

**Дата**: 2026-09-01
**Привязка**: [specs/288-prod-diagnostics-logging/spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/log-format.md](./contracts/log-format.md)

> Validation-гайд: как за 30 минут проверить фичу end-to-end на admin-машине (которая разделяет с продом ту же версию postgres:16). Не включает полный код реализации — только команды и ожидаемые результаты.

---

## Prerequisites

- Admin-машина с `docker-compose up` Karaoke-стенда (`karaoke-db`, `karaoke-web`, `karaoke-public`, `karaoke-app`).
- SSH-доступ к admin-машине (для `sudo systemctl stop nginx` в US2-Acceptance-Test).
- Доступ к прод-серверу `188.119.64.111` для финального прогона на проде (опционально, per A-009 — только по прямому согласию пользователя).
- Gradle + JDK для `karaoke-app` сборки (per AGENTS.md).

---

## Шаг 1: Применение PostgreSQL runtime-параметров (FR-001..FR-007)

> ⚠️ **Per Constitution § «Категорически запрещено» п. 2**: на проде — только по прямому согласию пользователя. На admin-машине — безопасно.

### Команды

```bash
# На admin-машине
docker exec karaoke-db psql -U postgres -d karaoke <<'EOF'
ALTER SYSTEM SET log_min_duration_statement = 1000;
ALTER SYSTEM SET log_temp_files = 0;
ALTER SYSTEM SET log_lock_waits = on;
ALTER SYSTEM SET log_autovacuum_min_duration = 0;
ALTER SYSTEM SET log_checkpoints = on;
ALTER SYSTEM SET log_line_prefix = '%m [%p] %q%u@%d from %h ';
ALTER SYSTEM SET log_timezone = 'Europe/Moscow';
ALTER SYSTEM SET timezone = 'Europe/Moscow';
SELECT pg_reload_conf();
SHOW log_min_duration_statement;
SHOW log_line_prefix;
SHOW timezone;
EOF
```

### Ожидаемый результат

- `SHOW log_min_duration_statement` → `1000`
- `SHOW log_line_prefix` → `%m [%p] %q%u@%d from %h `
- `SHOW timezone` → `Europe/Moscow`

### Validation

```bash
# Сгенерировать тестовый медленный запрос
docker exec karaoke-db psql -U postgres -d karaoke -c "SELECT pg_sleep(2);"

# Проверить лог
docker logs karaoke-db --since "10s" 2>&1 | grep "duration"
# Ожидаемо:
# 2026-09-01 12:34:56.789 MSK [12345] postgres@karaoke from 172.18.0.5 LOG: duration: 2000 ms statement: SELECT pg_sleep(2);
```

Если строка содержит префикс с timestamp в MSK — параметры применены успешно.

---

## Шаг 2: Обновление `docker-compose-database.yml` (FR-008)

### Правка

```yaml
# deploy/docker-compose-database.yml
services:
  karaoke-db:
    image: postgres:16
    environment:
      POSTGRES_USER: ${DB_LOCAL_POSTGRES_USER}
      POSTGRES_PASSWORD: ${DB_LOCAL_POSTGRES_PASSWORD}
      POSTGRES_DB: "karaoke"
      PGDATA: "/var/lib/postgresql/data/16"
      WORK_IN_CONTAINER: 1
      TZ: Europe/Moscow  # <-- ДОБАВИТЬ (FR-008)
```

> Per A-001: применение TZ к уже работающему контейнеру требует рестарта (per Q2 пользователь НЕ хочет рестарт в этой фиче). Изменение в `docker-compose-database.yml` сработает при следующем `docker-compose up --force-recreate`.

---

## Шаг 3: Обновление `deploy/.env` (FR-010)

### Правка

```bash
# deploy/.env
WEB_JAVA_OPTS=-Xmx2g -Duser.timezone=Europe/Moscow  # <-- ДОБАВИТЬ флаг
```

### Validation

```bash
# После рестарта karaoke-web
docker exec karaoke-web bash -c 'echo "TZ: $(date +%z), JVM TZ: $(java -XshowSettings:properties -version 2>&1 | grep user.timezone)"'
# Ожидаемо: TZ: +0300, JVM TZ: user.timezone = Europe/Moscow
```

---

## Шаг 4: Реализация FR-011..FR-016 в `ProdContainerCheck`

### Правка (упрощённо)

```kotlin
// karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/checks/ProdContainerCheck.kt

import org.slf4j.LoggerFactory

object ProdContainerCheck : MonitorCheck {
    private const val PING_URL = "https://sm-karaoke.ru/"
    private val pingLog = LoggerFactory.getLogger("infra.prod.ping")
    private val dbLog = LoggerFactory.getLogger("infra.prod.db")

    @Volatile private var firstFailureAt: Instant? = null

    override fun run(ctx: MonitorContext): List<MonitorAlert> {
        val (siteUp, siteDurationMs) = pingSite()
        val (dbUp, dbDurationMs) = pingRemoteDb()

        val prevWasFailing = firstFailureAt != null
        if (siteUp && dbUp) {
            if (prevWasFailing) {
                // FR-014: логируем восстановление
                val downForMin = Duration.between(firstFailureAt, Instant.now()).toMinutes()
                pingLog.info("ping:recovered url={} downForMin={}", PING_URL, downForMin)
            }
            firstFailureAt = null
            return emptyList()
        }

        // ... (existing alert logic) ...
    }

    private fun pingSite(): Pair<Boolean, Long> {
        val startMs = System.currentTimeMillis()
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(PING_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.instanceFollowRedirects = true
            val up = conn.responseCode in 200..399
            Pair(up, System.currentTimeMillis() - startMs)
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startMs
            pingLog.warn(
                "ping:failed url={} durationMs={} error=\"{}\" exceptionClass={}",
                PING_URL, durationMs, e.message, e::class.java.name, e
            )
            Pair(false, durationMs)
        } finally {
            conn?.disconnect()
        }
    }

    private fun pingRemoteDb(): Pair<Boolean, Long> {
        val startMs = System.currentTimeMillis()
        val db = Connection.remote()
        return try {
            val connection = db.getConnection()
            val up = connection != null && connection.isValid(3)
            Pair(up, System.currentTimeMillis() - startMs)
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startMs
            val host = KaraokeProperties.getString("db-remote-host")
            val port = KaraokeProperties.getString("db-remote-port")
            dbLog.warn(
                "db:failed host={} port={} durationMs={} error=\"{}\" exceptionClass={}",
                host, port, durationMs, e.message, e::class.java.name, e
            )
            Pair(false, durationMs)
        } finally {
            try { db.getConnection()?.close() } catch (_: Exception) {}
        }
    }
}
```

### Проверка компиляции

```bash
./gradlew :karaoke-app:compileKotlin
```

### Проверка bootJar

```bash
./gradlew :karaoke-app:bootJar
```

(На nsa-i9 согласно машинно-специфичному исключению в AGENTS.md — без явного согласия.)

---

## Шаг 5: End-to-End Validation (Acceptance Test)

### US1 — Медленные SQL видны в pg_log

```bash
# На admin-машине
docker exec karaoke-db psql -U postgres -d karaoke -c "SELECT pg_sleep(2);"
docker logs karaoke-db --since "1m" 2>&1 | grep "duration: 2000 ms"
# ✓ Ожидаемо: строка с duration: 2000 ms statement: SELECT pg_sleep(2)
```

### US2 — Структурированное WARN/INFO

```bash
# Симулировать сбой: остановить nginx
sudo systemctl stop nginx

# Подождать 1+ минуту (следующий тик MonitoringService)
sleep 70

# Проверить логи
docker logs karaoke-app --since "2m" 2>&1 | grep "infra.prod.ping"
# ✓ Ожидаемо:
# 2026-09-01 12:34:56.789 MSK WARN infra.prod.ping - ping:failed url=https://sm-karaoke.ru/ durationMs=5000 error="..." exceptionClass=java.net.SocketTimeoutException

# Восстановить
sudo systemctl start nginx
sleep 70
docker logs karaoke-app --since "2m" 2>&1 | grep "ping:recovered"
# ✓ Ожидаемо:
# 2026-09-01 12:36:00.000 MSK INFO infra.prod.ping - ping:recovered url=https://sm-karaoke.ru/ downForMin=1
```

### US3 — Синхронизированная TZ

```bash
# Текущая TZ в PostgreSQL
docker exec karaoke-db psql -U postgres -d karaoke -c "SHOW timezone;"
# ✓ Ожидаемо: Europe/Moscow

# Текущая TZ в JVM karaoke-web (после рестарта с новым WEB_JAVA_OPTS)
docker exec karaoke-web bash -c 'date +%z && java -XshowSettings:properties -version 2>&1 | grep user.timezone'
# ✓ Ожидаемо: +0300, user.timezone = Europe/Moscow

# Корреляция: одинаковый TZ в PostgreSQL и Spring Boot логах
docker logs karaoke-db --since "1m" | head -5
docker logs karaoke-web --since "1m" | head -5
# ✓ Ожидаемо: оба в формате с "+0300" или "MSK"
```

### US4 — Документация по корреляции

```bash
# Найти документ
find . -name "log-correlation.md"
# ✓ Ожидаемо: ./docs/ops/log-correlation.md

# Ссылка из AGENTS.md
grep -n "log-correlation" AGENTS.md
# ✓ Ожидаемо: строка со ссылкой
```

---

## Шаг 6: Success Criteria Validation

| SC | Команда | Ожидаемо |
|----|---------|----------|
| SC-001 | `docker logs karaoke-db --since "1m" \| head -3` | Строки с timestamp `YYYY-MM-DD HH:MM:SS MSK` |
| SC-002 | `docker exec karaoke-db psql -U postgres -d karaoke -c "SELECT pg_sleep(2);" && docker logs karaoke-db --since "1m" \| grep duration` | Строка с `duration: 2000 ms` |
| SC-003 | `docker logs karaoke-app --since "1d" \| grep -c "ProdContainerCheck: ping"` | `0` (нет больше println) |
| SC-004 | `docker logs karaoke-app --since "1d" \| grep -c "infra.prod.ping"` | `0` (в нормальном режиме) или N (если были сбои) |
| SC-005 | `find . -name "log-correlation.md"` | `./docs/ops/log-correlation.md` |
| SC-006 | Ручной тест (симуляция инцидента) | За 15 минут можно найти момент деградации в pg_log |
| SC-007 | `docker logs karaoke-db --since "1d" \| wc -l` | ≤ 100 (baseline + медленные запросы) |

---

## Rollback Plan

Если что-то пошло не так:

```bash
# Откатить PostgreSQL параметры
docker exec karaoke-db psql -U postgres -d karaoke <<'EOF'
ALTER SYSTEM RESET log_min_duration_statement;
ALTER SYSTEM RESET log_temp_files;
ALTER SYSTEM RESET log_lock_waits;
ALTER SYSTEM RESET log_autovacuum_min_duration;
ALTER SYSTEM RESET log_checkpoints;
ALTER SYSTEM RESET log_line_prefix;
ALTER SYSTEM RESET log_timezone;
ALTER SYSTEM RESET timezone;
SELECT pg_reload_conf();
EOF

# Откатить код: revert merge commit / redeploy предыдущую версию karaoke-app
```

---

## Готово к /speckit.tasks

После успешной валидации всех шагов — `/speckit.tasks` для генерации tasks.md.

---

## История

- Создан: 2026-09-01 (Phase 1)