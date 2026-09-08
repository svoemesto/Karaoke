# Research: Prod DB Log Audit (Phase 0)

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-09-08

> Назначение: зафиксировать **best practices**, **риски**, и **fallback-стратегии**
> до того, как мы потащим данные с прода. После Phase 1 design секции «Decision»
> считаются обязательными; раздел «Open Questions» должен быть пуст к Stage 4.

---

## R-1. Postgres verbose logging: какие категории ожидать

**Decision**: категории в `data/categories.json` будут покрывать шесть кластеров
из FR-002 спеки + один запасной (`__other__` → `__unclassified__`). Конкретные
GUC-параметры, которые могут доминировать (в порядке вероятности):

| Кластер | Postgres GUC | Типичный объём |
|---|---|---|
| `pg_stat_statements` | `shared_preload_libraries='pg_stat_statements'`, `pg_stat_statements.track=all` | Дамп статистики при `pg_stat_statements_reset()` или checkpoint |
| `auto_explain` | `auto_explain.log_min_duration=0`, `shared_preload_libraries='auto_explain'`, `auto_explain.log_analyze=on` | **ОГРОМНЫЙ** (каждый запрос с EXPLAIN ANALYZE) |
| `log_min_duration_statement` | `log_min_duration_statement=0` (или `-1`) | Каждый SQL-запрос с duration |
| `log_lock_waits` | `log_lock_waits=on` | Умеренно |
| `log_statement` | `log_statement='all'` или `'mod'` | ОГРОМНЫЙ (каждый SQL текстом) |
| `log_checkpoints` | `log_checkpoints=on` | Небольшой (по событию) |
| `log_connections`/`log_disconnections` | `log_connections=on` | Умеренно (на каждое подключение) |
| `log_temp_files` | `log_temp_files=0` | Зависит от нагрузки |
| `__errors__` | `ERROR`/`FATAL`/`PANIC` в любом из режимов | Должно быть мало — если много, то это реальные инциденты |
| `__unclassified__` | всё остальное | Сюда попадают логи HikariCP, JDBC-стеки, прочий non-Postgres stdout контейнера |

**Rationale**: спека уже зафиксировала кластеры (FR-002); research уточняет
**GUC-параметры**, которые в каждом кластере могут доминировать — это нужно для
раздела «конкретные настройки/файлы для изменения» в `report.md` (FR-003e) при
вердикте «отключать».

**Alternatives considered**:
- *Группировать по коду ошибки SQLSTATE*: технически возможно (Postgres
  пишет SQLSTATE в лог), но для verbose-диагностики менее информативно, чем
  по GUC-источнику.
- *Использовать готовый pgaudit*: в проекте не развёрнут (проверить `SHOW
  shared_preload_libraries` на проде через `psql` — но это вне scope).

**Open**: после сбора `data/raw.log` нужно подтвердить, что regex-правила в
`data/categories.json` покрывают ≥95 % строк. Если нет — дополнить и
зафиксировать в `report.md` (Edge Case «категория не распознана»).

---

## R-2. docker logs retention: сколько данных реально доступно

**Decision**: **сначала проверяем, что вообще доступно**, прежде чем писать
скрипт анализа. `docker logs --since=168h` зависит от log-driver и ring-buffer:

| Log driver | Поведение |
|---|---|
| `json-file` (driver по умолчанию) | Хранит до `log-opt max-size=10m,max-file=3` (~30 MB на контейнер). **168 ч скорее всего НЕ доступно** — перезаписано. |
| `journald` | Зависит от `SystemMaxUse` в journald.conf (часто 4 GB). **168 ч обычно доступно**. |
| `syslog` | Зависит от rsyslog rotation. Непредсказуемо. |
| `none` | Ничего не сохраняется. |

**Rationale**: важно понять **до сбора**, есть ли вообще данные за неделю.
Если `json-file` — лог обрезан; если `journald` — есть полные 7 дней.

**Как проверить (Stage 6, task T-01)**:

```bash
# 1. Какой log driver у karaoke-db?
ssh root@188.119.64.111 "docker inspect karaoke-db --format '{{.HostConfig.LogConfig.Type}}'"

# 2. Если journald — альтернативный источник:
ssh root@188.119.64.111 "journalctl -u docker --since='7 days ago' --no-pager | grep karaoke-db > /tmp/karaoke-db.log"

# 3. Если json-file — есть только последние ~30 MB; нужно честно сказать в отчёте
#    «данные за полную неделю недоступны, доступно ~X МБ за последние ~Y часов»
```

**Alternatives considered**:
- `docker logs --follow` + ручной сбор в файл: не подходит (нужно 168 ч истории).
- Sysdig/Falco: избыточно, требует установки агента.
- Поднять Loki/Grafana: явный overkill для one-shot.

**Open**: драйвер логов и реальная глубина истории станут известны только при
выполнении Stage 6 task T-01. Если драйвер `none` — задача блокируется.

---

## R-3. Sanitization паттернов перед коммитом

**Decision**: pre-commit-хук **обязан** просканировать `data/raw.log` и
`data/logs.jsonl` на типичные секретные паттерны (пароли, токены, ключи). Скрипт
`scripts/04-sanitize-for-commit.sh`:

```bash
# 1. Регулярки для известных секретов проекта (см. Constitution Principle VIII):
PATTERNS=(
  'password[[:space:]]*[:=][[:space:]]*[^[:space:]]+'    # password=...
  'KARAOKE_DB_PASSWORD[[:space:]]*[:=][[:space:]]*[A-Za-z0-9_-]+'
  'postgres://[^:]+:[^@]+@'                              # postgres://user:pass@host
  'Bearer[[:space:]]+[A-Za-z0-9._-]+'                    # Bearer tokens
  'pat_[A-Za-z0-9_-]+'                                   # GitHub/Docker PATs
  '[a-f0-9]{32}'                                         # hex 32-char (md5-ish)
)

# 2. Для каждой строки файла: если совпало — заменить на [REDACTED:<rule>]
# 3. Сохранить в data/sanitized.log; если original_size != sanitized_size —
#    вывести варнинг, но не падать (важно для SC-002 воспроизводимости).
```

**Rationale**: Constitution VIII.3 требует, чтобы ни один секрет-файл не
попал в индекс. Наш `data/raw.log` — не секрет-файл per se, но может случайно
содержать SQL c inline-паролями или JDBC-URL. Sanitization **до коммита**
снимает риск.

**Alternatives considered**:
- Коммитить raw.log as-is и полагаться на `git log` для отзыва: плохо
  (Constitution VIII.1 — после `git add` уже поздно, нужна перезапись истории).
- Не коммитить raw.log вообще, только report.md + metrics.json (агрегаты):
  хороший fallback, **если sanitization даёт false positives**. Решаемо в
  Stage 6: если sanitization затирает >1 % строк — переключаемся на
  «только агрегаты» (это уже решается в `data-model.md`).

**Open**: уточнить список секретных паттернов после первого прогона
sanitization на реальных данных.

---

## R-4. Bash + jq как средство анализа: паттерны воспроизводимости

**Decision**: один bash-скрипт на этап, **без** Python/Node (минимизируем
зависимости). Структура `data-model.md` использует JSONL (одна строка =
один LogRecord), потому что:

- `jq` умеет stream-фильтрацию (`--stream` flag), не грузит весь файл в RAM.
- JSONL тривиально диффить (`diff <(jq -c . data/logs.jsonl) <(jq -c . data/logs.jsonl)` = 0).
- md5 от отсортированного `logs.jsonl` совпадает при повторном запуске
  категоризации (SC-002).

**Rationale**: SC-002 требует воспроизводимости. JSONL + детерминированная
сортировка + jq дают это «бесплатно».

**Alternatives considered**:
- Python + pandas: удобнее для ad-hoc, но лишняя зависимость и reproducibility
  сложнее (Python 3.10 vs 3.12, версии библиотек, locale).
- DuckDB: мощно, но overkill + лишний бинарь для one-shot.

**Open**: при Stage 6 проверить, что `jq --version` ≥1.6 на admin-машине.

---

## R-5. Метрики нагрузки (FR-003c)

**Decision**: фиксируем **4 метрики**, собираемые из raw.log без обращения к
docker API:

| Метрика | Формула | Источник |
|---|---|---|
| `total_lines` | `wc -l < data/raw.log` | POSIX |
| `total_bytes` | `wc -c < data/raw.log` | POSIX |
| `bytes_per_day` | `(lines grouped by date) * mean_size` | `jq`/`awk` |
| `error_share` | `errors / total_lines * 100` | `grep -c 'ERROR\|FATAL\|PANIC'` |

**Что НЕ собираем** (вне scope / невозможно без агентских доступов):
- CPU/RAM контейнера (`docker stats` — это runtime-метрика, для недельного
  среза нужно было заранее снять Prometheus/Scraper; не делаем).
- Disk I/O (аналогично).
- Размер WAL (`pg_database_size`, `pg_xact`) — требует psql-доступа,
  Constitution запрещает без согласования, **опционально** через уже
  разрешённый `ssh` (read-only запрос SELECT).

**Rationale**: метрики должны быть получены **из тех же данных**, которые мы
уже скачали (raw.log), без новых ssh-вызовов. Иначе отчёт нельзя будет
воспроизвести из коммита.

**Alternatives considered**:
- `docker stats karaoke-db --no-stream` (live): даёт CPU/MEM прямо сейчас, но
  не за неделю — бесполезно для отчёта.
- Prometheus: не развёрнут.

**Open**: при желании владельца — добавить `psql`-запрос для
`pg_stat_database.numbackends`, `xact_commit` за последние 7 дней. Это
read-only и допустимо без дополнительных согласований, **но не входит в
минимальный deliverable** (Stage 6 решает по времени).

---

## R-6. Воспроизводимость отчёта (SC-002)

**Decision**: проверка воспроизводимости встроена в `scripts/03-generate-report.sh`:

```bash
# 1. Генерируем отчёт первый раз → report.md, fingerprint = md5sum report.md
# 2. Удаляем report.md
# 3. Генерируем второй раз → report.md, fingerprint2 = md5sum report.md
# 4. assert fingerprint == fingerprint2
```

Если `report.md` содержит `Date:` / `hostname` / `now()` — это ломает
воспроизводимость. **Правило**: все timestamp'ы в report.md берутся **из
собранных данных** (min/max `timestamp` из `logs.jsonl`), а не из `date`.

**Rationale**: SC-002 нужен для верификации, что категоризация детерминирована
(а не для красоты). Если падает — значит где-то есть
не-детерминированный источник (race condition, `$(date)` в скрипте).

**Open**: после Stage 6 — закоммитить `report.md.fingerprint` и убедиться,
что повторный запуск на той же ветке (без новых данных) даёт тот же md5.

---

## Итог Phase 0

- 6 research-пунктов закрыты, каждое с **Decision / Rationale / Alternatives / Open**.
- **0 нерешённых NEEDS CLARIFICATION** к Phase 1.
- 4 «Open» помечены как **stage-dependent** — разрешаются на Stage 6 при
  выполнении задач.

Готов к **Phase 1: Design & Contracts**.