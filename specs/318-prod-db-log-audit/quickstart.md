# Quickstart: Prod DB Log Audit (как воспроизвести анализ end-to-end)

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Data**: [data-model.md](data-model.md) | **Date**: 2026-09-08

> Этот документ описывает **как воспроизвести анализ** с нуля до `report.md`,
> начиная от чистой feature-ветки. Используется:
>
> 1. **Аналитиком** — при первоначальном выполнении (Stage 6).
> 2. **Владельцем** — для верификации, что отчёт воспроизводим (SC-002).
> 3. **Будущим аудитором** — через 30 дней, если вердикт был «оставлять».

---

## Prerequisites

1. **SSH-доступ к прод-серверу** без пароля:
   ```bash
   ssh -o BatchMode=yes root@188.119.64.111 echo OK
   # Ожидаемый вывод: OK
   ```

2. **Локальные утилиты** на admin-машине (`nsa-i9`):
   ```bash
   bash --version | head -1   # bash ≥ 4.4
   jq --version               # jq ≥ 1.6
   awk --version | head -1    # gawk ≥ 4
   git --version              # любой
   md5sum --version | head -1 # coreutils
   ```

3. **Feature-ветка** (уже создана в Stage 1):
   ```bash
   git rev-parse --abbrev-ref HEAD
   # Ожидаемый вывод: 318-prod-db-log-audit
   ```

4. **Секреты НЕ нужны** для анализа. Если в процессе понадобится
   `POSTGRES_PASSWORD` (например, для `ALTER SYSTEM` в T-08) — он берётся
   через `ssh root@188.119.64.111 "cat /root/.pgpass"` и НЕ коммитится.

---

## Pipeline (4 этапа, ~10 минут всего)

### Этап 1 — Сбор логов с прода

```bash
cd /home/nsa/Karaoke
git checkout 318-prod-db-log-audit

# Создаёт data/raw.log (НЕ коммитится) и data/raw.metadata.json
bash specs/318-prod-db-log-audit/scripts/01-fetch-logs.sh
```

**Что делает скрипт**:
1. Проверяет SSH-доступ (`BatchMode=yes`).
2. Определяет log driver контейнера `karaoke-db`.
3. Если `json-file` — `docker logs --since=168h --timestamps karaoke-db`.
4. Если `journald` — `journalctl -u docker --since='7 days ago' | grep karaoke-db`.
5. Если `none` — **останавливается с ошибкой** (Edge Case из спеки).
6. Сохраняет в `specs/318-prod-db-log-audit/data/raw.log`.
7. Пишет в `data/raw.metadata.json`:
   ```json
   {
     "collected_at_utc": "2026-09-08T12:00:00Z",
     "log_driver": "json-file",
     "log_size_bytes": 28912345,
     "line_count": 1234567,
     "first_timestamp": "2026-09-08T05:55:00Z",
     "last_timestamp": "2026-09-08T11:55:00Z",
     "truncation_warning": "json-file ring buffer — only last ~6 h available"
   }
   ```

**Sanity-check после**:
```bash
wc -l data/raw.log         # число строк > 0
head -3 data/raw.log       # видим timestamp + level
tail -3 data/raw.log
```

### Этап 2 — Parse + категоризация

```bash
bash specs/318-prod-db-log-audit/scripts/02-parse-and-categorize.sh
```

**Что делает**:
1. Читает `data/raw.log` построчно.
2. Парсит timestamp, level (regex по Postgres log format).
3. Применяет правила из `data/categories.json` (R01 … RN).
4. Пишет:
   - `data/logs.jsonl` (структурированный)
   - `data/incidents.jsonl` (только ERROR/FATAL/PANIC + контекст)
   - `data/metrics.json` (агрегаты)
   - `data/unclassified-samples.txt` (если `__unclassified__` > 0)
   - `data/parse-warnings.log` (строки с нераспарсенным timestamp/level)

**Sanity-check**:
```bash
# SC-002: воспроизводимость
md5sum data/logs.jsonl
# Запомнить первый хэш
bash specs/318-prod-db-log-audit/scripts/02-parse-and-categorize.sh
md5sum data/logs.jsonl
# Хэши должны совпасть
```

### Этап 3 — Генерация отчёта

```bash
bash specs/318-prod-db-log-audit/scripts/03-generate-report.sh
# → создаётся report.md
```

**Sanity-check**:
```bash
# Структурный чек по contracts/report-format.md
test -f report.md && wc -l report.md   # > 0, ≤ 500
grep -c '^## ' report.md                # = 9 (девять разделов)
grep '^## Вердикт:' report.md          # одно из двух слов
md5sum report.md                        # запомнить для SC-002
```

### Этап 4 — Sanitize + публикация в OpenProject

```bash
# 1. Sanitize
bash specs/318-prod-db-log-audit/scripts/04-sanitize-for-commit.sh

# 2. Pre-commit: проверить, что никаких секретов не попадёт
git add specs/318-prod-db-log-audit/
git status
git ls-files | grep -iE '\.env$|\.key$|\.pem$|do\.env$' || echo "OK no secrets"

# 3. Закоммитить
git commit -m "318: prod-db-log-audit report (verdict: ОТКЛЮЧАТЬ)"   # пример
git push -u origin 318-prod-db-log-audit
gh pr create --base master --title "318: Prod DB log audit (#66)" \
  --body "OpenProject #66 — см. report.md"

# 4. OpenProject
source .env.local-tracker
./tools/tracker.sh add-comment 66 --file specs/318-prod-db-log-audit/report.md
./tools/tracker.sh mark-review 66
```

---

## Что НЕ делается в этом quickstart (для будущих повторов)

- **T-08 (применение изменений конфигурации)**: выполняется **только** если
  вердикт = `ОТКЛЮЧАТЬ` **и** владелец дал согласие. Подробнее — в
  [contracts/disable-config.md](contracts/disable-config.md) (блок C).
- **T-09 (контрольный замер через 24 ч)**: выполняется **после** T-08, через
  24 часа.

---

## Acceptance checks (по SC-001, SC-002, SC-005)

| ID | Проверка | Как |
|----|----------|-----|
| SC-001 | Владелец прочитал `report.md` ≤ 10 мин | Владелец смотрит `report.md` и говорит «понятно» |
| SC-002 | Категоризация воспроизводима | Этап 2 дважды → `md5sum data/logs.jsonl` совпадают |
| SC-005 | Задача #66 в `In review` | OpenProject UI: status = «In review», comment содержит `report.md` |

---

## Если что-то пошло не так

| Симптом | Что делать |
|---------|------------|
| `Permission denied (publickey)` | SSH-ключ не настроен → `ssh-copy-id root@188.119.64.111` (или спросить владельца) |
| `docker logs` возвращает пусто | Log driver = `none` или данные в ring-buffer стёрты → задача блокируется, фиксируем в `report.md` |
| `__unclassified__` > 5 % | Edge Case из спеки → дополнить `data/categories.json`, повторить Этап 2 |
| ERROR в синтаксисе `data/categories.json` | `jq . data/categories.json` покажет ошибку → поправить, повторить Этап 2 |
| Sanitization затёрла > 1 % строк | Подозрение на false positive → переключиться на «только агрегаты» (см. research.md R-3 fallback) |
| `md5sum report.md` не совпадает при повторе | Где-то в шаблоне `$(date)` или hostname → `grep -n '$(date)\\|`date`\\|hostname' scripts/03-generate-report.sh` |