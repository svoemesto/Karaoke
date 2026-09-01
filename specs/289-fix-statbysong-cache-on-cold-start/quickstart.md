# Quickstart: 289-fix-statbysong-cache-on-cold-start — End-to-End Validation

**Дата**: 2026-09-01
**Привязка**: [specs/289-fix-statbysong-cache-on-cold-start/spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/log-format.md](./contracts/log-format.md)

> Validation-гайд: как за 30 минут проверить фичу end-to-end на admin-машине + проде. Не включает полный код реализации — только команды и ожидаемые результаты.

---

## Prerequisites

- Admin-машина (nsa-i9) с `karaoke-web` (порт 7799) и `karaoke-db` (порт 8832).
- SSH-доступ к прод-серверу `188.119.64.111`.
- Пароль `DB_SERVER_POSTGRES_PASSWORD` в `deploy/.env`.
- Спека 288 развёрнута на проде (`log_min_duration_statement = 1000`).

---

## Шаг 1: Применение индекса на локальной admin-машине (безопасно)

> Это можно сделать без per-action согласия — локальная БД, не прод.

```bash
# Создать миграцию (если ещё не создана)
cat > /tmp/admin-idx.sql <<'EOF'
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_songs_id_status_source_markers
ON tbl_songs(id_status, source_markers);
EOF

# Применить через локальный docker
docker cp /tmp/admin-idx.sql karaoke-db:/tmp/admin-idx.sql
docker exec karaoke-db psql -U postgres -d karaoke -f /tmp/admin-idx.sql
```

### Ожидаемый результат

```
CREATE INDEX
```

### Validation (на admin-машине)

```bash
docker exec karaoke-db psql -U postgres -d karaoke -c "\d+ tbl_songs" 2>&1 | grep idx_songs_id_status_source_markers
# Ожидаемо: индекс в списке

# Тест: должен быть Index Scan, а не Seq Scan
docker exec karaoke-db psql -U postgres -d karaoke -c "
EXPLAIN ANALYZE
select count(DISTINCT id) as cnt from tbl_songs
where id_status >= 6
  AND btrim(coalesce(source_markers, '')) != ''
  AND (tags IS NULL OR NOT ('SKIP' = ANY(string_to_array(upper(coalesce(tags,'')), ' '))));
"
# Ожидаемо: 'Index Scan using idx_songs_id_status_source_markers' (НЕ 'Seq Scan on tbl_songs')
```

---

## Шаг 2: Применение индекса на проде (требует per-action согласия)

> ⚠️ **Constitution § п. 2**: DDL на проде — только по прямому согласию.

После подтверждения пользователя:

```bash
# Вариант A: через docker exec на локальном karaoke-db с подключением к прод-БД
docker exec karaoke-db psql "host=188.119.64.111 port=5433 user=SvoeMestoKaraokeUser905 password=Pass4Sm-23052008-newpass dbname=karaoke sslmode=disable" \
  -c "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_songs_id_status_source_markers ON tbl_songs(id_status, source_markers);"

# Вариант B: через SSH на проде (если есть доступ)
ssh root@188.119.64.111 'docker exec karaoke-db psql -U postgres -d karaoke -c "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_songs_id_status_source_markers ON tbl_songs(id_status, source_markers);"'
```

### Ожидаемый результат

```
CREATE INDEX
```

---

## Шаг 3: Создание миграции для новых контейнеров (commit в Karaoke)

```bash
# Создать файл
cat > /home/nsa/Karaoke/deploy/karaoke-db/45_idx_songs_id_status_source_markers.sql <<'EOF'
-- specs/289-fix-statbysong-cache-on-cold-start (FR-001)
-- Ускоряет фильтр 'id_status >= 6 AND btrim(coalesce(source_markers, '')) !='''
-- в StatBySong.refreshCache() (см. karaoke-web/.../StatBySong.kt).
--
-- CONCURRENTLY — без блокировки таблицы (zero-downtime на проде).
-- IF NOT EXISTS — idempotent.
-- ~18k записей, ~1-3 MB индекс, < 5 сек создание.
--
-- Применяется:
-- 1. На новых контейнерах — автоматически через docker-entrypoint-initdb.d/
-- 2. На существующем прод-контейнере — ручной CREATE INDEX CONCURRENTLY через psql
--    (см. docs/ops/log-correlation.md и спеку 289, A-002/A-003).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_songs_id_status_source_markers
ON tbl_songs(id_status, source_markers);
EOF
```

---

## Шаг 4: Правка `StatBySong.kt` (FR-004..FR-008)

> Компиляция и сборка jar — после реализации. Здесь — концептуальный план.

### Изменения в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/StatBySong.kt`

**Добавить**:
- `import org.slf4j.LoggerFactory`
- `import java.util.concurrent.Executors`
- `import java.util.concurrent.ScheduledExecutorService`
- `import java.util.concurrent.atomic.AtomicBoolean`

**В `companion object`**:
```kotlin
companion object {
    private val cacheLog = LoggerFactory.getLogger("infra.cache.statbysong")
    private val refreshing = AtomicBoolean(false)
    private val bgExecutor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "StatBySong-ColdStart").apply { isDaemon = true }
        }
}
```

**Заменить `ensureCacheInitialized`**:
```kotlin
private fun ensureCacheInitialized(database: KaraokeConnection) {
    if (cachedTotal.get() < 0 && refreshing.compareAndSet(false, true)) {
        cacheLog.warn("cache:coldStart triggering background refresh")
        bgExecutor.submit {
            try {
                refreshCache(database)
            } catch (e: Exception) {
                cacheLog.warn("cache:refreshFailed error=\"{}\" exceptionClass={}",
                    e.message, e::class.java.name, e)
            } finally {
                refreshing.set(false)
            }
        }
    }
    // Все запросы возвращают fallback (0) пока не прогрет — НЕ блокируем.
}
```

**В конце `refreshCache()` (после `println` существующего)** добавить `cacheLog.info`:
```kotlin
cacheLog.info(
    "cache:refreshed total={} collection={} freeNow={} subscriptionOnly={} inWork={} durationMs={}",
    total, collection, freeNow, subscriptionOnly, inWork,
    System.currentTimeMillis() - startMs,
)
```

**KDoc обновить** (FR-010).

---

## Шаг 5: Компиляция и сборка (на nsa-i9 — без явного согласия)

```bash
./gradlew :karaoke-web:compileKotlin
# Ожидаемо: BUILD SUCCESSFUL

./gradlew :karaoke-web:ktlintCheck
# Ожидаемо: BUILD SUCCESSFUL (без новых нарушений)

./gradlew :karaoke-web:bootJar
# Ожидаемо: BUILD SUCCESSFUL
```

---

## Шаг 6: Deploy `karaoke-web` на прод (требует per-action согласия)

```bash
# Копировать jar на прод
scp /home/nsa/Karaoke/karaoke-web/build/libs/karaoke-web-*.jar root@188.119.64.111:/Karaoke/deploy/karaoke-web-jar/

# Рестарт на проде
ssh root@188.119.64.111 'cd /sm-karaoke/system/deploy && docker compose -f docker-compose-web.yml up -d karaoke-web'
```

---

## Шаг 7: End-to-End Validation

### US1 — Cold-start не блокирует (SC-001)

```bash
# Рестарт karaoke-web
ssh root@188.119.64.111 'cd /sm-karaoke/system/deploy && docker compose -f docker-compose-web.yml restart karaoke-web'

# Сразу после рестарта — тестовый запрос с замером
time curl -s "https://sm-karaoke.ru/api/public/stats"
# Ожидаемо: < 100 мс (vs 12 сек до фикса)

# Через 15 сек — счётчики должны быть актуальные
sleep 15
curl -s "https://sm-karaoke.ru/api/public/stats"
# Ожидаемо: total=18500 (или близкое значение, не 0)
```

### US2 — Индекс ускоряет SQL (SC-002)

```bash
# После deploy + индекса на проде
ssh root@188.119.64.111 'docker logs karaoke-db --since "1h" 2>&1 | grep "duration:" | grep "count.*id_status"'
# Ожидаемо: duration < 500 мс (или вообще не в логе, если < 1000 мс)

# Или через tools/analyze-prod-incident.sh
/home/nsa/Karaoke/tools/analyze-prod-incident.sh 24
# Секция 2 (медленные SQL): пусто или только pg_sleep тесты
```

### US3 — Single-flight guard (SC-003)

```bash
# Рестарт karaoke-web
ssh root@188.119.64.111 'cd /sm-karaoke/system/deploy && docker compose -f docker-compose-web.yml restart karaoke-web'

# 5 параллельных запросов СРАЗУ после рестарта
for i in {1..5}; do (curl -s "https://sm-karaoke.ru/api/public/stats" &); done
wait

# Проверить pg_log: сколько select count появилось
ssh root@188.119.64.111 'docker logs karaoke-db --since "30s" 2>&1 | grep -c "count(DISTINCT id)"'
# Ожидаемо: 3 (1 refresh × 3 запроса), НЕ 15 (5 × 3)
```

### US1 — WARN/INFO логирование

```bash
# После cold-start в logs/karaoke-web
ssh root@188.119.64.111 'docker logs karaoke-web --since "5m" 2>&1 | grep "infra.cache.statbysong"'
# Ожидаемо:
#   WARN infra.cache.statbysong - cache:coldStart triggering background refresh
#   INFO infra.cache.statbysong - cache:refreshed total=18500 ... durationMs=12333
```

---

## Шаг 8: Success Criteria Validation

| SC | Команда | Ожидаемо |
|----|---------|----------|
| SC-001 | `time curl -s https://sm-karaoke.ru/api/public/stats` (сразу после рестарта) | < 100 мс |
| SC-002 | `ssh root@188.119.64.111 'docker logs karaoke-db --since "1h" \| grep "duration:.*count.*id_status"'` | duration < 500 мс (или пусто) |
| SC-003 | `ssh root@188.119.64.111 'docker logs karaoke-db --since "30s" \| grep -c "count(DISTINCT id)"'` (после 5 параллельных curl) | = 3 |
| SC-004 | `tools/analyze-prod-incident.sh 24` (через 24ч после deploy) | секция 2: пусто (или только тестовые pg_sleep) |
| SC-005 | `ssh root@188.119.64.111 'docker stats karaoke-web --no-stream'` | память не выросла значительно (< 10 MB) |

---

## Rollback Plan

Если что-то пошло не так:

```bash
# 1. Откатить код: revert merge commit karaoke-web / redeploy предыдущую версию

# 2. Откатить индекс (если он вредит):
ssh root@188.119.64.111 'docker exec karaoke-db psql -U postgres -d karaoke -c "DROP INDEX CONCURRENTLY IF EXISTS idx_songs_id_status_source_markers;"'

# 3. Удалить миграцию (если ещё не закоммичена):
rm /home/nsa/Karaoke/deploy/karaoke-db/45_idx_songs_id_status_source_markers.sql
```

---

## Готово к /speckit.tasks

После успешной валидации всех шагов — `/speckit.tasks` для генерации tasks.md.

---

## История

- Создан: 2026-09-01 (Phase 1)