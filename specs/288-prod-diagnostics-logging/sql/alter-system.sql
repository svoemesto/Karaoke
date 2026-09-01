-- specs/288-prod-diagnostics-logging/sql/alter-system.sql
--
-- Расширенное логирование PostgreSQL для post-hoc диагностики инцидентов прода.
-- Применяется через `ALTER SYSTEM SET` + `pg_reload_conf()` — runtime-параметры,
-- НЕ требуют рестарта кластера. Все значения персистятся в postgresql.auto.conf
-- и переживают рестарт контейнера.
--
-- ⚠️  Per Constitution § «Категорически запрещено» п. 2 — выполнение DDL/DML к
-- серверной БД только по прямому согласию пользователя. Агент НЕ выполняет без
-- явного одобрения. Применить ОДНОЙ командой:
--
--   docker exec karaoke-db psql -U postgres -d karaoke \
--     -f specs/288-prod-diagnostics-logging/sql/alter-system.sql
--
-- (или вручную через `docker exec -it karaoke-db psql -U postgres -d karaoke`).
--
-- Спекa: specs/288-prod-diagnostics-logging/spec.md, FR-001..FR-007.

-- FR-001: логировать SQL-запросы длиннее 1 секунды (для post-hoc анализа зависаний).
-- Баланс по текущей нагрузке ~50 RPS: ожидаемо 30-100 записей/день.
ALTER SYSTEM SET log_min_duration_statement = 1000;

-- FR-002: логировать ВСЕ temp-файлы (создаются при превышении work_mem) — диагностика
-- тяжёлых JOIN/sort/hash. Если создаёт слишком много шума — поднять до 1024 (1 MB).
ALTER SYSTEM SET log_temp_files = 0;

-- FR-003: логировать ожидания блокировок дольше deadlock_timeout (1 сек) — индикатор
-- deadlock/spike. Дефолт postgres:16 = on, но фиксируем для идемпотентности.
ALTER SYSTEM SET log_lock_waits = on;

-- FR-004: логировать ВСЮ работу автовакуума (включая короткую). Полезно для отлова
-- ситуации, когда автовакуум не успевает за ростом таблицы (tbl_events).
ALTER SYSTEM SET log_autovacuum_min_duration = 0;

-- FR-005: логировать checkpoint'ы — индикатор I/O-давления.
ALTER SYSTEM SET log_checkpoints = on;

-- FR-006: префикс строк лога: timestamp с TZ, PID, user@database, host.
-- Без этого строки PostgreSQL НЕ имеют timestamp и не коррелируются с другими логами.
-- IP в %h — infrastructure IP (контейнеры Docker network, admin-машина), не PII.
ALTER SYSTEM SET log_line_prefix = '%m [%p] %q%u@%d from %h ';

-- FR-007: установить TZ = Europe/Moscow для логов и для now().
-- Без этого PostgreSQL пишет UTC (TZ контейнера по умолчанию).
ALTER SYSTEM SET log_timezone = 'Europe/Moscow';
ALTER SYSTEM SET timezone = 'Europe/Moscow';

-- Применить все изменения без рестарта кластера.
SELECT pg_reload_conf();

-- Верификация (покажет текущие значения после применения):
-- SHOW log_min_duration_statement;
-- SHOW log_temp_files;
-- SHOW log_lock_waits;
-- SHOW log_autovacuum_min_duration;
-- SHOW log_checkpoints;
-- SHOW log_line_prefix;
-- SHOW log_timezone;
-- SHOW timezone;