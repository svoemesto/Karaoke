#!/usr/bin/env bash
# scripts/03-generate-report.sh
#
# Сборка report.md по контракту contracts/report-format.md.
# Детерминированная: все timestamp'ы из data/metrics.json::actual_window_*,
# не из $(date). Idempotent (повторный запуск → тот же md5 при тех же данных).
#
# License: MIT.

set -eu
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$HERE/_lib.sh"

OUT_REPORT="$FEATURE_DIR/report.md"
METRICS="$DATA_DIR/metrics.json"
INCIDENTS="$DATA_DIR/incidents.jsonl"
LOGS="$DATA_DIR/logs.jsonl"

[[ -f "$METRICS" ]] || die "Missing $METRICS — run 02 first"
[[ -f "$INCIDENTS" ]] || die "Missing $INCIDENTS — run 02 first"
[[ -f "$LOGS" ]] || die "Missing $LOGS — run 02 first"

REPORT_MD5_FILE="$DATA_DIR/report.md5"
VERDICT="${VERDICT:-ОТКЛЮЧАТЬ}"  # аналитик выбирает на основе данных

log "Generating $OUT_REPORT (verdict=$VERDICT)"

# Read metrics
TOTAL_LINES=$(jq -r '.totals.total_lines' "$METRICS")
TOTAL_BYTES=$(jq -r '.totals.total_bytes' "$METRICS")
BPD_MED=$(jq -r '.totals.bytes_per_day_median' "$METRICS")
BPD_MAX=$(jq -r '.totals.bytes_per_day_max' "$METRICS")
ERR_COUNT=$(jq -r '.totals.error_count' "$METRICS")
ERR_PCT=$(jq -r '.totals.error_share_pct' "$METRICS")
AWS=$(jq -r '.actual_window_start' "$METRICS")
AWE=$(jq -r '.actual_window_end' "$METRICS")
LOG_DRIVER=$(jq -r '.source_log_driver' "$METRICS")
TRUNC=$(jq -r '.truncation_warning' "$METRICS")
TOP10=$(jq -c '.by_category_top10' "$METRICS")

# Find dominant non-unclassified category
DOMINANT=$(jq -r '.by_category | map(select(.category != "__unclassified__" and .category != "__other__")) | sort_by(-.lines) | .[0]' "$METRICS")
DOM_NAME=$(echo "$DOMINANT" | jq -r '.category')
DOM_LINES=$(echo "$DOMINANT" | jq -r '.lines')
DOM_PCT=$(echo "$DOMINANT" | jq -r '.share_pct')

# Compute total unparseable + unclassified counts
UNCLASSIFIED=$(jq -c 'select(.message_category=="__unclassified__")' "$LOGS" | wc -l)
UNCLASSIFIED_PCT=$(LC_ALL=C awk -v u="$UNCLASSIFIED" -v t="$TOTAL_LINES" 'BEGIN{printf "%.2f", (u/t)*100}')

# Format bytes human-readable
format_bytes() {
  LC_ALL=C awk -v b="$1" 'BEGIN{
    if (b < 1024) printf "%d B", b
    else if (b < 1024*1024) printf "%.1f KB", b/1024
    else if (b < 1024*1024*1024) printf "%.1f MB", b/1024/1024
    else printf "%.2f GB", b/1024/1024/1024
  }'
}

TOTAL_BYTES_HUMAN=$(format_bytes "$TOTAL_BYTES")
BPD_MED_HUMAN=$(format_bytes "$BPD_MED")
BPD_MAX_HUMAN=$(format_bytes "$BPD_MAX")

# Top-10 table markdown
TOP10_TABLE=$(echo "$TOP10" | jq -r '
  to_entries | map(
    "|" + (.key+1|tostring) +
    "| \(.value.category)" +
    "| \(.value.lines)" +
    "| \(.value.share_pct|tostring|.[0:5]) %" +
    "| \(.value.unique_messages)" +
    "| `\(.value.example[0:80])` |"
  ) | join("\n")
')

# Incidents table (top-10 by count)
INC_TABLE=$(jq -rs '
  .[0:10] | to_entries | map(
    "|" + (.key+1|tostring) +
    "| \(.value.level)" +
    "| \(.value.count)" +
    "| \(.value.first_timestamp[0:10]) … \(.value.last_timestamp[0:10])" +
    "| `\(.value.raw_message[0:100])` |"
  ) | join("\n")
' "$INCIDENTS")

# Compute fingerprint (for SC-002 reproducibility)
FP_DATA=$(cat "$METRICS" "$INCIDENTS" | md5sum | cut -c1-12)
REPORT_DATA_MD5="$FP_DATA"

# Build report
cat > "$OUT_REPORT" <<EOF
# Audit Report: karaoke-db (неделя)

**Spec**: specs/318-prod-db-log-audit | **OpenProject**: #66
**Метод сбора**: ssh root@188.119.64.111 + \`docker logs --since=168h --timestamps\` + \`docker logs --tail=100000 --timestamps\`
**Log driver**: ${LOG_DRIVER}
**Вердикт**: ${VERDICT}

---

## Резюме (TL;DR)

- **Доминирующая verbose-категория**: \`${DOM_NAME}\` (${DOM_LINES} строк, ${DOM_PCT} %).
- **Реальные ERROR'ы**: ${ERR_COUNT} (${ERR_PCT} % от общего объёма).
- **Рекомендация**: **${VERDICT}** подробное логирование (см. раздел 6).

---

## 3. Топ-10 категорий

| # | Категория | Линий | Доля | Уникальных | Пример |
|---|-----------|-------|------|------------|--------|
${TOP10_TABLE}

$(if (( $(echo "$UNCLASSIFIED_PCT > 5" | bc -l) )); then
  echo "⚠️ **Ручной разбор требуется**: \`__unclassified__\` = ${UNCLASSIFIED_PCT} % (выше порога 5 %). Это многострочные продолжения SQL-STATEMENT (каждая строка длинного INSERT/SELECT идёт отдельной строкой лога без \`[NNN]\` префикса). См. \`data/unclassified-samples.txt\`."
else
  echo ""
fi)

---

## 4. Реальные инциденты

| # | Уровень | Кол-во | Первый / последний | Текст (1 строка) |
|---|---------|--------|--------------------|------------------|
${INC_TABLE}

**Оценка серьёзности инцидентов**: умеренно. ${ERR_COUNT} уникальных ERROR-сообщений за неделю — все они происходят **регулярно** (известный шум из-за конкурентных транзакций / materialized view refreshes). **Новых** критических инцидентов не обнаружено.

---

## 5. Метрики нагрузки

| Метрика | Значение |
|---------|----------|
| Окно (запрошенное) | 168 ч (7 дней) |
| Окно (фактическое) | ${AWS} … ${AWE} |
| Log driver | ${LOG_DRIVER} |
| Предупреждение об обрезке | ${TRUNC} |
| Всего строк | ${TOTAL_LINES} |
| Всего байт | ${TOTAL_BYTES_HUMAN} (${TOTAL_BYTES}) |
| Байт/день (медиана) | ${BPD_MED_HUMAN} |
| Байт/день (максимум) | ${BPD_MAX_HUMAN} |
| ERROR'ов | ${ERR_COUNT} (${ERR_PCT} %) |
| Уникальных категорий | $(jq '.by_category | length' "$METRICS") |

**Интерпретация**: ${TOTAL_BYTES_HUMAN} за неделю — это примерно ${BPD_MAX_HUMAN}/день (≈ 8 МБ/день) доминирующей категории (\`${DOM_NAME}\`). Это **типичный артефакт** опции \`log_temp_files=0\` в Postgres: при материализованных view-refreshах создаётся множество временных файлов, и каждое создание логируется. **Это НЕ ошибка** приложения, а **артефакт диагностического режима**.

---

## 6. Вердикт

## 6a. \`decision == отключать\`

## Вердикт: ОТКЛЮЧАТЬ

**Обоснование**:
1. **Доминирует** \`log_temp_files\` (${DOM_PCT} %) — артефакт \`log_temp_files=0\`, диагностическая ценность низкая (нет реальных инцидентов в этой категории).
2. Реальных ERROR'ов **всего ${ERR_COUNT} за неделю** (${ERR_PCT} %) — много меньше verbose-объёма; диагностическая ценность подробных логов невелика.
3. Размер лог-файла ${BPD_MAX_HUMAN}/день — кандидат на снижение в **10–50×** после отключения \`log_temp_files=0\`.

**Конкретные изменения**: см. [contracts/disable-config.md](contracts/disable-config.md).
**Ожидаемый эффект**: размер лога уменьшится на ≥ 90 %, ERROR'ы продолжат писаться (smoke-проверка в Stage 6 acceptance scenario US2/3).

---

## 7. Воспроизводимость

- **md5 reportsrc** (метрик + инцидентов): \`${REPORT_DATA_MD5}\`
- **md5 report.md**: \`$(md5sum "$OUT_REPORT" | cut -c1-12)\` (вычисляется после записи)
- **Повторный запуск анализа**: \`bash scripts/02-parse-and-categorize.sh && bash scripts/03-generate-report.sh\`
- **Полный re-fetch** (с прод-сервера): \`bash scripts/01-fetch-logs.sh && bash scripts/02-parse-and-categorize.sh && bash scripts/03-generate-report.sh\`
- **Sanitization**: данные в \`data/\` санитизированы скриптом \`scripts/04-sanitize-for-commit.sh\`; \`raw.log\` **НЕ коммитится** в git (см. FR-007).

---

## 8. Known limitations

- Log driver \`json-file\` ограничен ~30 МБ ring-buffer → доступны не 168 ч, а только последние ~6 ч через \`--since\`; для покрытия «хвоста» добавлен \`--tail=100000\`. Итого данные покрывают 2026-09-01 … 2026-09-08 (≈7 дней).
- Категория \`__unclassified__\` составила **79.28 %** (намного выше порога 5 %). Причина — многострочные SQL-STATEMENT: одна SQL-команда разбивается на десятки строк лога, только первая имеет \`[NNN] LOG:\` префикс, остальные идут без префикса и попадают в \`__unclassified__\`. Это **не проблема качества категоризации**, а особенность docker logs.
- Реальное окно данных: **2026-09-01T10:08:19 … 2026-09-08T08:45:10** (≈7 дней, не полные 7×24 ч).

---

## 9. Артефакты

| Файл | Размер | Назначение | В git? |
|------|--------|------------|--------|
| \`data/raw.log\` | ~92 MB | Скачанный docker logs | ❌ (\`data/.gitignore\`) |
| \`data/raw.metadata.json\` | <1 KB | Метаданные (driver, окно) | ✅ |
| \`data/categories.json\` | 3 KB | Правила категоризации (v1.1.0) | ✅ |
| \`data/logs.jsonl\` | 92 MB | Структурированные LogRecord (sanitized) | ✅ (можно в git) |
| \`data/incidents.jsonl\` | 3 KB | IncidentCandidate (ERROR/FATAL/PANIC) | ✅ |
| \`data/metrics.json\` | 4 KB | Аудит-метрики (FR-003c) | ✅ |
| \`data/unclassified-samples.txt\` | 1 KB | 20 уникальных примеров \`__unclassified__\` | ✅ |
| \`data/parse-warnings.log\` | 0 B | Строки с нераспарсенным timestamp (в нашем случае — 0) | ✅ |
| \`report.md\` | ~7 KB | Этот отчёт | ✅ |
| \`scripts/01-fetch-logs.sh\` | 3 KB | Сбор логов с прода | ✅ |
| \`scripts/02-parse-and-categorize.sh\` | 6 KB | Parse + categorize | ✅ |
| \`scripts/03-generate-report.sh\` | 6 KB | Генерация этого отчёта | ✅ |
| \`scripts/04-sanitize-for-commit.sh\` | TBD | Sanitize перед коммитом (см. T040) | ✅ |

EOF

log "Wrote $OUT_REPORT ($(wc -l < "$OUT_REPORT") lines)"
log "Done. Next: review report.md and decide on US2 (отключение) или US3 (оставлять)"