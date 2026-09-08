# Data Model: Prod DB Log Audit (Phase 1)

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md) | **Date**: 2026-09-08

> Все сущности живут только в `specs/318-prod-db-log-audit/data/`. Ни одна не
> попадает в БД проекта, MinIO или Redis. Это **read-only анализ**, не feature.

---

## Entity 1: `LogRecord`

**Назначение**: одна строка скачанного `docker logs karaoke-db`, нормализованная
для категоризации и агрегации.

**Файл**: `data/logs.jsonl` (JSON Lines — одна запись = одна строка).

**Схема** (одна запись):

```json
{
  "timestamp": "2026-09-01T12:34:56.789Z",
  "level": "LOG",
  "message_category": "auto_explain",
  "raw_message": "2026-09-01 12:34:56.789 UTC [12345] LOG:  duration: 12.34 ms  statement: SELECT …",
  "raw_message_truncated": false,
  "source_line_number": 1234
}
```

**Поля**:

| Поле | Тип | Обязательность | Описание |
|---|---|---|---|
| `timestamp` | ISO-8601 UTC | обязательно | Извлечено из префикса строки `docker logs --timestamps` (формат `2026-09-01T12:34:56.789Z`). Если распарсить не удалось — `null` и категория `__unclassified__` |
| `level` | enum | обязательно | Один из: `LOG`, `WARNING`, `ERROR`, `FATAL`, `PANIC`, `DEBUG`, `INFO`, `STATEMENT`. Извлекается из Postgres log line `[NNN] LOG:` / `[NNN] ERROR:` |
| `message_category` | enum | обязательно | Имя категории по правилу в `data/categories.json` (см. Entity 2). Если ни одно правило не сработало — `__unclassified__` |
| `raw_message` | string ≤ 4096 | обязательно | Полная исходная строка после timestamp/prefix. **Санитизирована** (см. research R-3): пароли/токены заменены на `[REDACTED:<rule>]` |
| `raw_message_truncated` | bool | обязательно | `true` если исходная строка > 4096 символов и была обрезана |
| `source_line_number` | int ≥ 1 | обязательно | Номер строки в `data/raw.log` (нужно для воспроизводимости SC-002) |

**Валидация** (применяется на этапе parse):
- `timestamp != null` для ≥95 % записей (Edge Case: остальные идут в
  `__unclassified__` с пометкой).
- `level ∈ {LOG, WARNING, ERROR, FATAL, PANIC, DEBUG, INFO, STATEMENT}` —
  иначе строка отбрасывается с warning в `data/parse-warnings.log`.
- `raw_message` после санитизации MUST NOT содержать `password=`, `Bearer `,
  `pat_*` (sanity-check; иначе sanitization скрипт вернул false negative —
  баг, надо чинить).

**State transitions**: нет (immutable записи).

---

## Entity 2: `CategoryRule`

**Назначение**: одно правило категоризации — пара `(regex → category_name)`.
Хранится в `data/categories.json` (не JSONL, потому что маленький и
конфиг-подобный).

**Схема**:

```json
{
  "rules": [
    {
      "id": "R01",
      "pattern": "\\[ERROR\\]|\\[FATAL\\]|\\[PANIC\\]",
      "category": "__errors__",
      "priority": 1,
      "description": "Любой уровень ERROR/FATAL/PANIC → __errors__"
    },
    {
      "id": "R02",
      "pattern": "auto_explain|EXPLAIN \\(analyze",
      "category": "auto_explain",
      "priority": 5,
      "description": "auto_explain verbose (duration + EXPLAIN ANALYZE каждого запроса)"
    }
  ],
  "default_category": "__unclassified__"
}
```

**Поля правила**:

| Поле | Тип | Описание |
|---|---|---|
| `id` | string | Уникальный (`R01`, `R02`, …) для ссылок в отчёте |
| `pattern` | ERE regex | Применяется к `raw_message` через `grep -E` или `awk` (POSIX ERE) |
| `category` | string | Имя категории, в которое попадают совпавшие строки |
| `priority` | int ≥ 1 | Чем меньше — тем раньше применяется. Нужно, чтобы `__errors__` срабатывал ДО кластеров типа `auto_explain` (ERROR внутри EXPLAIN должен идти в `__errors__`, а не в `auto_explain`) |
| `description` | string | Human-readable, попадает в `report.md` |

**default_category**: применяется, если ни одно правило не совпало. По FR-002
спеки это `__unclassified__`. **Edge Case** из спеки: если `__unclassified__`
> 5 % — в отчёте выводится предупреждение.

**Категории, обязательные к покрытию** (из FR-002 спеки):

| Category | Приоритет | Что означает |
|---|---|---|
| `__errors__` | 1 | ERROR/FATAL/PANIC (всегда первое правило) |
| `pg_stat_statements` | 10 | дампы статистики (dealloc/realloc/reset) |
| `auto_explain` | 11 | `auto_explain.log_min_duration=0` |
| `log_min_duration_statement` | 12 | `duration: NN.NN ms statement: …` |
| `log_lock_waits` | 20 | `process NNN acquired / waited for …` |
| `log_checkpoints` | 30 | `checkpoint complete: wrote X buffers` |
| `log_connections` | 31 | `connection received: …` / `connection authorized: …` / `disconnection: …` |
| `log_temp_files` | 32 | `temporary file: …` |
| `__other__` | 99 | всё, что попало в Postgres-логирование, но не в верхние категории (например, `LOG:  database system is ready`) |
| `__unclassified__` | 100 | по умолчанию — non-Postgres stdout (HikariCP, JDBC-стеки и т. п.) |

---

## Entity 3: `IncidentCandidate`

**Назначение**: сообщение уровня `ERROR`/`FATAL`/`PANIC` с контекстом
(3 строки до и после). Попадает в `report.md` раздел «Реальные инциденты».

**Файл**: `data/incidents.jsonl`.

**Схема**:

```json
{
  "timestamp": "2026-09-04T03:21:00.000Z",
  "level": "ERROR",
  "message_category": "__errors__",
  "raw_message": "ERROR:  duplicate key violates unique constraint \"karaoke_vote_pkey\"",
  "context_before": [
    "LOG:  statement: INSERT INTO karaoke_vote …",
    "STATEMENT:  INSERT INTO karaoke_vote …"
  ],
  "context_after": [
    "LOG:  duration: 8.45 ms"
  ],
  "source_line_number": 45678
}
```

**Поля** (все обязательны):

| Поле | Тип | Описание |
|---|---|---|
| `timestamp` | ISO-8601 UTC | Когда произошло |
| `level` | enum | ERROR / FATAL / PANIC |
| `message_category` | string | Почти всегда `__errors__`, но если ERROR возник внутри `auto_explain` блока — может быть `auto_explain` |
| `raw_message` | string | Само сообщение |
| `context_before` | array[string] | До 3 предыдущих строк лога (могут быть пустыми) |
| `context_after` | array[string] | До 3 следующих строк лога |
| `source_line_number` | int | Для трассировки к `data/raw.log` |

**Правила формирования контекста**:
- Берём 3 строки **до** ERROR с тем же `container_pid` (если есть в логе) или
  просто 3 предыдущие строки.
- Если ERROR идёт подряд (>1 подряд) — контекст объединяется (одна запись
  IncidentCandidate на блок ERROR'ов).
- Если ERROR > 100 в файле — в отчёте показываются топ-10 по частоте
  паттерна (group by `raw_message`), не все (иначе отчёт будет огромным).

---

## Entity 4: `AuditMetrics`

**Назначение**: агрегаты для раздела FR-003(c) отчёта «Метрики нагрузки».

**Файл**: `data/metrics.json`.

**Схема**:

```json
{
  "window_start": "2026-09-01T00:00:00.000Z",
  "window_end": "2026-09-08T00:00:00.000Z",
  "actual_window_start": "2026-09-01T12:00:00.000Z",
  "actual_window_end": "2026-09-08T11:55:00.000Z",
  "source_log_driver": "json-file",
  "truncation_warning": "log driver ring buffer — only last ~28 MB available",

  "totals": {
    "total_lines": 1234567,
    "total_bytes": 28912345,
    "bytes_per_day_median": 4128907,
    "bytes_per_day_max": 5234567,
    "error_count": 234,
    "error_share_pct": 0.019
  },

  "by_category": {
    "__errors__": { "lines": 234, "share_pct": 0.019, "unique_messages": 12 },
    "auto_explain": { "lines": 1100000, "share_pct": 89.1, "unique_messages": 5 },
    "log_min_duration_statement": { "lines": 100000, "share_pct": 8.1, "unique_messages": 234 },
    "__other__": { "lines": 32333, "share_pct": 2.6, "unique_messages": 156 },
    "__unclassified__": { "lines": 2000, "share_pct": 0.16, "unique_messages": 89 }
  },

  "log_driver_warning": "json-file driver с max-size=10m,max-file=3 — полные 168 часов недоступны"
}
```

**Поля верхнего уровня**:

| Поле | Описание |
|---|---|
| `window_start`/`window_end` | Запрошенное окно (168 ч назад от момента сбора) |
| `actual_window_start`/`actual_window_end` | Реальное окно из данных (min/max timestamp в `logs.jsonl`) — может быть уже |
| `source_log_driver` | `json-file` / `journald` / `syslog` / `none` |
| `truncation_warning` | Текст для отчёта, если данные обрезаны ring-buffer'ом |
| `totals` | см. таблицу ниже |
| `by_category` | словарь `category → {lines, share_pct, unique_messages}` (топ-10 в отчёте, все в JSON) |

**Поле `totals`**:

| Поле | Описание |
|---|---|
| `total_lines` | `wc -l data/raw.log` |
| `total_bytes` | `wc -c data/raw.log` |
| `bytes_per_day_median` | медиана `(bytes grouped by date)` |
| `bytes_per_day_max` | максимум по дням |
| `error_count` | `grep -c 'ERROR\|FATAL\|PANIC' data/raw.log` |
| `error_share_pct` | `error_count / total_lines * 100` |

---

## Entity 4b: `PostChangeMetrics`

**Назначение**: снимок метрик **через 24 ч после** применения изменений
конфигурации `karaoke-db` (если вердикт = ОТКЛЮЧАТЬ). Используется для
проверки SC-003 (уменьшение объёма ≥50 %, errors не упали >10 %).

**Файл**: `data/post-change-metrics.json`.

**Схема**:

```json
{
  "captured_at_utc": "2026-09-10T12:00:00.000Z",
  "hours_since_change": 24,
  "totals": {
    "total_lines": 234567,
    "total_bytes": 5234567,
    "bytes_per_day_median": 5012345,
    "error_count": 232,
    "error_share_pct": 0.099
  },
  "by_category_top3": {
    "__errors__": { "lines": 232, "share_pct": 0.099 },
    "__other__": { "lines": 234000, "share_pct": 99.7 },
    "__unclassified__": { "lines": 335, "share_pct": 0.143 }
  },
  "comparison_with_baseline": {
    "bytes_per_day_pct_change": -84.5,
    "error_count_pct_change": -0.85,
    "dominant_category_share_pct_change": -89.0
  },
  "sc003_pass": {
    "size_reduced_by_50pct": true,
    "errors_not_reduced_by_10pct": true
  }
}
```

**Поля**:

| Поле | Описание |
|---|---|
| `captured_at_utc` | Когда сделан замер |
| `hours_since_change` | Сколько часов прошло с момента применения T022 (обычно 24) |
| `totals` | Те же агрегаты, что в Entity 4, но за **новое** окно |
| `by_category_top3` | Топ-3 категории (не обязательно те же, что в baseline) |
| `comparison_with_baseline` | % изменения относительно Entity 4 (`metrics.json`) |
| `sc003_pass` | Булевы флаги по SC-003: уменьшился ли объём ≥50 %, и не упали ли errors >10 % |

**Используется в**:
- T024 (создаётся скриптом `scripts/05-post-change-metrics.sh`)
- Раздел «Контрольный замер (через 24 ч)» в `report.md` (дописывается в T024)
- Финальный commit (T042) включает этот файл

---

## Entity 5: `AuditReport`

**Назначение**: финальный human-readable отчёт. Живёт в
`specs/318-prod-db-log-audit/report.md`. **Контракт** отчёта зафиксирован в
[contracts/report-format.md](contracts/report-format.md) — там разделы и их
обязательные поля.

**Краткая структура** (полная — в contracts):

1. **Header** — Feature, окно, метод сбора, дата отчёта.
2. **Резюме** — TL;DR с вердиктом в первой строке.
3. **Топ-10 категорий** — таблица `category → lines → share → пример`.
4. **Реальные инциденты** — топ-10 ERROR с контекстом (см. Entity 3).
5. **Метрики нагрузки** — Entity 4 в читабельной форме.
6. **Вердикт** — `decision ∈ {оставлять, отключать}` + обоснование 2–3 пункта.
7. **Если «отключать»** — конкретные настройки/файлы (см. [contracts/disable-config.md](contracts/disable-config.md)).
8. **Если «оставлять»** — критерии отключения в будущем.
9. **Воспроизводимость** — `md5sum report.md` + команда для повтора.

---

## Связи между сущностями

```text
raw.log ──parse──► logs.jsonl (LogRecord)
                │
                ├─categorize via─► categories.json (CategoryRule)
                │                      │
                └──────────────────────┴──► logs.jsonl (заполнен message_category)
                                              │
                                              ├─► incidents.jsonl (IncidentCandidate)
                                              │   (только ERROR/FATAL/PANIC)
                                              │
                                              └─► metrics.json (AuditMetrics)

scripts/03-generate-report.sh:
   logs.jsonl + incidents.jsonl + metrics.json + categories.json ──► report.md (AuditReport)
```

Все 4 файла данных — **входы** для генератора отчёта; генератор детерминирован
(SC-002).