# Data Model: 288-prod-diagnostics-logging

**Дата**: 2026-09-01
**Привязка**: [specs/288-prod-diagnostics-logging/spec.md](./spec.md)
**Phase**: 1 of `/speckit.plan`

> Сущности этой фичи — не «данные в БД», а артефакты runtime-конфигурации и логические состояния. Все извлечены из `Key Entities` секции spec.md и дополнены из research.md.

---

## Entity 1: `PostgreSQL runtime-parameter`

**Описание**: Пара ключ-значение в `postgresql.auto.conf`, которая применяется через `pg_reload_conf()` (runtime) или при рестарте кластера (для postgresql.conf overrides).

### Поля (атрибуты сущности)

| Поле | Тип | Описание | Пример |
|------|-----|----------|--------|
| `parameter_name` | String | Имя PostgreSQL GUC-параметра | `log_min_duration_statement` |
| `current_value` | String | Текущее значение параметра | `1000` |
| `applied_via` | Enum | Способ применения | `ALTER_SYSTEM`, `POSTGRESQL_CONF`, `DEFAULT` |
| `requires_restart` | Boolean | Требуется ли рестарт кластера | `false` |
| `category` | Enum | Категория параметра | `LOGGING`, `RUNTIME`, `TZ` |
| `expected_log_volume_per_day` | Integer | Оценочный объём записей/день | `50` |

### Параметры этой фичи (FR-001..FR-007)

| `parameter_name` | `current_value` | `requires_restart` | `category` | `expected_log_volume_per_day` |
|------------------|-----------------|--------------------|-----------|---------------------------------|
| `log_min_duration_statement` | `1000` | false | LOGGING | 30-100 |
| `log_temp_files` | `0` | false | LOGGING | 0-10 (только при аномалиях) |
| `log_lock_waits` | `on` | false | LOGGING | 0-5 |
| `log_autovacuum_min_duration` | `0` | false | LOGGING | 5-20 |
| `log_checkpoints` | `on` | false | LOGGING | 1-5 |
| `log_line_prefix` | `'%m [%p] %q%u@%d from %h '` | false | LOGGING | N/A (формат) |
| `log_timezone` | `'Europe/Moscow'` | false | TZ | N/A |
| `timezone` | `'Europe/Moscow'` | false | TZ | N/A |

### Валидация
- FR-001..FR-007: каждое значение применяется через `ALTER SYSTEM SET ...; SELECT pg_reload_conf();`.
- Per Constitution § «Категорически запрещено» п. 2 — применение требует прямого согласия пользователя.

### Жизненный цикл
1. **Default**: значение из `postgresql.conf` или compile-time default (postgres:16).
2. **Override via ALTER SYSTEM**: записывается в `postgresql.auto.conf`, применяется через `pg_reload_conf()` (runtime).
3. **Persist через restart**: при рестарте контейнера `postgresql.auto.conf` перечитывается, значения сохраняются.
4. **Reset**: `ALTER SYSTEM RESET ...; SELECT pg_reload_conf();` удаляет строку из `postgresql.auto.conf`.

---

## Entity 2: `Категория логгера` (Logger Category)

**Описание**: Строковый идентификатор для `LoggerFactory.getLogger("...")` в SLF4J. Используется как grep-маркер и для условного форматирования в logback-spring.xml (если будет).

### Поля

| Поле | Тип | Описание | Пример |
|------|-----|----------|--------|
| `category_name` | String | Полное имя категории (FQN-style) | `infra.prod.ping` |
| `log_level` | Enum | Уровень логирования по умолчанию | `INFO`, `WARN`, `ERROR` |
| `used_in_class` | String | Класс, который создаёт этот логгер | `ProdContainerCheck` |
| `purpose` | String | Назначение категории | `инфраструктурный пинг прода` |
| `grep_marker` | String | Строка для `grep` | `infra.prod.ping` |

### Категории этой фичи (FR-011..FR-015)

| `category_name` | `log_level` | `used_in_class` | `purpose` | `grep_marker` |
|-----------------|-------------|-----------------|-----------|----------------|
| `infra.prod.ping` | INFO/WARN | `ProdContainerCheck.pingSite()` | HTTP-пинг прода | `infra.prod.ping` |
| `infra.prod.db` | INFO/WARN | `ProdContainerCheck.pingRemoteDb()` | JDBC-пинг прод-БД | `infra.prod.db` |

### Валидация
- FR-011: `infra.prod.ping` для неуспешных пингов (WARN).
- FR-014: `infra.prod.ping` для восстановления (INFO).
- FR-015: `infra.prod.db` для неуспешных JDBC-соединений (WARN).

### Связь с существующими категориями
- Не пересекается с существующими `com.svoemesto.karaokeapp.*` категориями — это новая namespace `infra.*`.
- Аналогия: `PublicSongeditorController.selfAssignLog` в `karaoke-web` использует категорию-строку для grep'а.

---

## Entity 3: `PingState` (логическое состояние мониторинга прода)

**Описание**: Текущее состояние `ProdContainerCheck` — стационарное (`OK`) или переходное (`WARNING`/`CRITICAL`). Только переходы между состояниями логируются.

### Поля (state + transition)

| Поле | Тип | Описание | Пример |
|------|-----|----------|--------|
| `state` | Enum | Текущее состояние | `OK`, `WARNING`, `CRITICAL` |
| `since` | Instant? | Время входа в текущее состояние (null для `OK`) | `2026-09-01T12:30:00 MSK` |
| `site_up` | Boolean | Результат `pingSite()` | `false` |
| `db_up` | Boolean | Результат `pingRemoteDb()` | `false` |
| `last_failure_reason` | String? | Текст последней ошибки | `Read timed out` |
| `duration_since_failure_min` | Long | Минут с момента первого сбоя (0 если `OK`) | `5` |

### Состояния и переходы

```
   ┌──────┐  failure (site || db)  ┌───────────┐  duration >= critical  ┌────────────┐
   │  OK  │ ─────────────────────> │ WARNING   │ ────────────────────> │ CRITICAL   │
   └──────┘                        └───────────┘                        └────────────┘
       ▲                                  │                                    │
       │           both up                │                                    │
       └──────────────────────────────────┴────────────────────────────────────┘
                                     recovery
```

### Правила логирования (FR-014)

| Переход | Уровень | Сообщение |
|---------|---------|-----------|
| `OK → WARNING` (первый сбой) | WARN | `infra.prod.ping - ping:failed url=... durationMs=N error="..." exceptionClass=...` |
| `OK → WARNING` (повторный сбой, тот же сеанс) | WARN | (то же) |
| `WARNING → CRITICAL` (duration >= `monitorProdDownCriticalMinutes`) | WARN | (то же сообщение, severity меняется в `MonitorAlert`, не в логе) |
| `WARNING/CRITICAL → OK` | INFO | `infra.prod.ping - ping:recovered downForMin=N url=...` |
| `OK` стационарное | (no log) | — |
| `WARNING` стационарное | WARN каждый тик | (только первое WARN при входе в WARNING) |
| `CRITICAL` стационарное | (no log; severity уже поднят до CRITICAL в MonitorAlert) | — |

### Валидация (FR-014, Acceptance Scenario 5)
- В нормальном режиме (все пинги OK) за 24 часа: `grep -c "infra.prod.ping" $(docker logs karaoke-app --since "1d")` = 0.

---

## Отсутствующие сущности

### Почему нет таблиц/колонок в БД
Эта фича **не создаёт таблицы, индексы, миграции**. Всё — через runtime-параметры PostgreSQL и SLF4J-логирование. Поэтому:
- Нет миграций в `deploy/karaoke-db/`.
- Нет DDL (`CREATE TABLE`, `ALTER TABLE`, etc.).
- Нет моделей в `karaoke-app/model/`.

### Почему нет API endpoints
- `/api/public/debug/db` уже существует (спека 187, FR-013).
- Эта фича не вводит новых HTTP-эндпоинтов.

---

## Связи между сущностями

```
[PostgreSQL runtime-parameter]                    [Категория логгера]
        │                                                │
        │                                                │
        ▼                                                ▼
   pg_log (stderr → docker logs)                  docker logs karaoke-app
        │                                                │
        │                                                │
        └────────────────► [PingState] ◄─────────────────┘
                          (корреляция
                           по timestamp MSK)
```

**Главная корреляция**: при инциденте разработчик смотрит `docker logs karaoke-db` (PostgreSQL) и `docker logs karaoke-app` (ProdContainerCheck WARN). Общий TZ MSK + префикс `log_line_prefix` (PostgreSQL) + timestamp в stdout (Spring Boot) позволяет точно сопоставить события по времени.

---

## История

- Создан: 2026-09-01 (Phase 1)
- Источник: spec.md секция Key Entities + research.md решения D-2, D-4, D-5.