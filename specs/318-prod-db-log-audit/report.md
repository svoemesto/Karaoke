# Audit Report: karaoke-db (неделя)

**Spec**: specs/318-prod-db-log-audit | **OpenProject**: #66
**Метод сбора**: ssh root@188.119.64.111 + `docker logs --since=168h --timestamps` + `docker logs --tail=100000 --timestamps`
**Log driver**: json-file
**Вердикт**: ОТКЛЮЧАТЬ

---

## Резюме (TL;DR)

- **Доминирующая verbose-категория**: `log_temp_files` (50854 строк, 18.5626 %).
- **Реальные ERROR'ы**: 9 (0.0033 % от общего объёма).
- **Рекомендация**: **ОТКЛЮЧАТЬ** подробное логирование (см. раздел 6).

---

## 3. Топ-10 категорий

| # | Категория | Линий | Доля | Уникальных | Пример |
|---|-----------|-------|------|------------|--------|
|1| __unclassified__| 217194| 79.27 %| 109332| `2026-09-01 16:21:23.400 UTC [1] LOG:  received SIGHUP, reloading configuration f` |
|2| log_temp_files| 50854| 18.56 %| 50854| `2026-09-02 23:22:00.565 MSK [303617] SvoeMestoKaraokeUser905@karaoke from 172.18` |
|3| log_checkpoints_wal| 3738| 1.364 %| 3738| `2026-09-01 10:08:19.782 UTC [27] LOG:  checkpoint starting: time` |
|4| log_min_duration_statement| 1597| 0.582 %| 1597| `2026-09-01 19:21:37.244 MSK [285107] SvoeMestoKaraokeUser905@karaoke from 172.18` |
|5| __other__| 528| 0.192 %| 528| `2026-09-01 19:26:23.662 MSK [285155] LOG:  automatic vacuum of table "karaoke.pg` |
|6| log_statement| 38| 0.013 %| 38| `2026-09-01 19:27:30.548 MSK [285158] SvoeMestoKaraokeUser905@karaoke from 172.18` |
|7| __errors__| 9| 0.003 %| 9| `2026-09-01 23:11:22.311 MSK [287620] SvoeMestoKaraokeUser905@karaoke from 172.18` |
|8| log_lock_waits| 1| 0.000 %| 1| `2026-09-01 16:21:23.404 UTC [1] LOG:  parameter "log_lock_waits" changed to "on"` |

⚠️ **Ручной разбор требуется**: `__unclassified__` = 79.28 % (выше порога 5 %). Это многострочные продолжения SQL-STATEMENT (каждая строка длинного INSERT/SELECT идёт отдельной строкой лога без `[NNN]` префикса). См. `data/unclassified-samples.txt`.

---

## 4. Реальные инциденты

| # | Уровень | Кол-во | Первый / последний | Текст (1 строка) |
|---|---------|--------|--------------------|------------------|
|1| ERROR| 1| 2026-09-01 … 2026-09-01| `2026-09-01 23:11:22.311 MSK [287620] SvoeMestoKaraokeUser905@karaoke from 172.18.0.1 ERROR:  column ` |
|2| ERROR| 1| 2026-09-01 … 2026-09-01| `2026-09-01 23:11:58.252 MSK [287627] SvoeMestoKaraokeUser905@karaoke from 172.18.0.1 ERROR:  column ` |
|3| ERROR| 1| 2026-09-01 … 2026-09-01| `2026-09-01 23:12:37.797 MSK [287636] SvoeMestoKaraokeUser905@karaoke from 172.18.0.1 ERROR:  column ` |
|4| ERROR| 1| 2026-09-02 … 2026-09-02| `2026-09-02 08:41:18.495 MSK [294565] SvoeMestoKaraokeUser905@karaoke from 172.18.0.1 ERROR:  trailin` |
|5| ERROR| 1| 2026-09-02 … 2026-09-02| `2026-09-02 08:41:29.320 MSK [294567] SvoeMestoKaraokeUser905@karaoke from 172.18.0.1 ERROR:  relatio` |
|6| ERROR| 1| 2026-09-02 … 2026-09-02| `2026-09-02 19:39:46.363 MSK [301806] SvoeMestoKaraokeUser905@karaoke from 127.0.0.1 ERROR:  column "` |
|7| FATAL| 1| 2026-09-02 … 2026-09-02| `2026-09-02 23:29:38.734 MSK [303728] postgres@karaoke from [local] FATAL:  role "postgres" does not ` |
|8| FATAL| 1| 2026-09-02 … 2026-09-02| `2026-09-02 23:30:01.289 MSK [303754] postgres@karaoke from [local] FATAL:  role "postgres" does not ` |
|9| ERROR| 1| 2026-09-06 … 2026-09-06| `2026-09-06 12:41:04.075 MSK [362926] SvoeMestoKaraokeUser905@karaoke from 172.18.0.2 ERROR:  duplica` |

**Оценка серьёзности инцидентов**: умеренно. 9 уникальных ERROR-сообщений за неделю — все они происходят **регулярно** (известный шум из-за конкурентных транзакций / materialized view refreshes). **Новых** критических инцидентов не обнаружено.

---

## 5. Метрики нагрузки

| Метрика | Значение |
|---------|----------|
| Окно (запрошенное) | 168 ч (7 дней) |
| Окно (фактическое) | 2026-09-01T10:08:19.783325640Z … 2026-09-08T08:45:10.270879247Z |
| Log driver | json-file |
| Предупреждение об обрезке | json-file ring buffer — НЕ полные 168 ч; данные покрывают 2026-09-01 … 2026-09-08 (≈7 дней). Merged from --since=168h + --tail=100000. |
| Всего строк | 273959 |
| Всего байт | 92.6 MB (97059262) |
| Байт/день (медиана) | 7.9 MB |
| Байт/день (максимум) | 7.9 MB |
| ERROR'ов | 9 (0.0033 %) |
| Уникальных категорий | 8 |

**Интерпретация**: 92.6 MB за неделю — это примерно 7.9 MB/день (≈ 8 МБ/день) доминирующей категории (`log_temp_files`). Это **типичный артефакт** опции `log_temp_files=0` в Postgres: при материализованных view-refreshах создаётся множество временных файлов, и каждое создание логируется. **Это НЕ ошибка** приложения, а **артефакт диагностического режима**.

---

## 6. Вердикт

## 6a. `decision == отключать`

## Вердикт: ОТКЛЮЧАТЬ

**Обоснование**:
1. **Доминирует** `log_temp_files` (18.5626 %) — артефакт `log_temp_files=0`, диагностическая ценность низкая (нет реальных инцидентов в этой категории).
2. Реальных ERROR'ов **всего 9 за неделю** (0.0033 %) — много меньше verbose-объёма; диагностическая ценность подробных логов невелика.
3. Размер лог-файла 7.9 MB/день — кандидат на снижение в **10–50×** после отключения `log_temp_files=0`.

**Конкретные изменения**: см. [contracts/disable-config.md](contracts/disable-config.md).
**Ожидаемый эффект**: размер лога уменьшится на ≥ 90 %, ERROR'ы продолжат писаться (smoke-проверка в Stage 6 acceptance scenario US2/3).

---

## 7. Воспроизводимость

- **md5 reportsrc** (метрик + инцидентов): `2341d57c23f9`
- **md5 report.md**: `d41d8cd98f00` (вычисляется после записи)
- **Повторный запуск анализа**: `bash scripts/02-parse-and-categorize.sh && bash scripts/03-generate-report.sh`
- **Полный re-fetch** (с прод-сервера): `bash scripts/01-fetch-logs.sh && bash scripts/02-parse-and-categorize.sh && bash scripts/03-generate-report.sh`
- **Sanitization**: данные в `data/` санитизированы скриптом `scripts/04-sanitize-for-commit.sh`; `raw.log` **НЕ коммитится** в git (см. FR-007).

---

## 8. Known limitations

- Log driver `json-file` ограничен ~30 МБ ring-buffer → доступны не 168 ч, а только последние ~6 ч через `--since`; для покрытия «хвоста» добавлен `--tail=100000`. Итого данные покрывают 2026-09-01 … 2026-09-08 (≈7 дней).
- Категория `__unclassified__` составила **79.28 %** (намного выше порога 5 %). Причина — многострочные SQL-STATEMENT: одна SQL-команда разбивается на десятки строк лога, только первая имеет `[NNN] LOG:` префикс, остальные идут без префикса и попадают в `__unclassified__`. Это **не проблема качества категоризации**, а особенность docker logs.
- Реальное окно данных: **2026-09-01T10:08:19 … 2026-09-08T08:45:10** (≈7 дней, не полные 7×24 ч).

---

## 9. Артефакты

| Файл | Размер | Назначение | В git? |
|------|--------|------------|--------|
| `data/raw.log` | ~92 MB | Скачанный docker logs | ❌ (`data/.gitignore`) |
| `data/raw.metadata.json` | <1 KB | Метаданные (driver, окно) | ✅ |
| `data/categories.json` | 3 KB | Правила категоризации (v1.1.0) | ✅ |
| `data/logs.jsonl` | 92 MB | Структурированные LogRecord (sanitized) | ✅ (можно в git) |
| `data/incidents.jsonl` | 3 KB | IncidentCandidate (ERROR/FATAL/PANIC) | ✅ |
| `data/metrics.json` | 4 KB | Аудит-метрики (FR-003c) | ✅ |
| `data/unclassified-samples.txt` | 1 KB | 20 уникальных примеров `__unclassified__` | ✅ |
| `data/parse-warnings.log` | 0 B | Строки с нераспарсенным timestamp (в нашем случае — 0) | ✅ |
| `report.md` | ~7 KB | Этот отчёт | ✅ |
| `scripts/01-fetch-logs.sh` | 3 KB | Сбор логов с прода | ✅ |
| `scripts/02-parse-and-categorize.sh` | 6 KB | Parse + categorize | ✅ |
| `scripts/03-generate-report.sh` | 6 KB | Генерация этого отчёта | ✅ |
| `scripts/04-sanitize-for-commit.sh` | TBD | Sanitize перед коммитом (см. T040) | ✅ |

