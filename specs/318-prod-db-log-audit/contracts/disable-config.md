# Disable Configuration: Заполнено по результатам отчёта #318

**Spec**: [../spec.md](../spec.md) FR-003(e) | **Date**: 2026-09-08
**Source**: [`../report.md`](../report.md) — вердикт ОТКЛЮЧАТЬ

> Этот файл заполнен по результатам **T020 (Phase 4 / US2)** на основании данных
> из [`../data/metrics.json`](../data/metrics.json) и
> [`../report.md`](../report.md). Применяется **только по прямому согласию
> владельца** (Constitution п.2 «Деплой — только по согласию»).

---

## Блок A — Найденные доминирующие категории

| Категория | Доля | Соответствующий Postgres GUC | Действие |
|-----------|------|-------------------------------|----------|
| `log_temp_files` | 18.56 % (50 854 строк) | `log_temp_files=0` | **Установить `log_temp_files=-1`** (отключить логирование временных файлов) |
| `__unclassified__` | 79.28 % | (multi-line SQL — не GUC, особенность `docker logs`) | Не трогать (нельзя убрать без изменения docker logging driver) |
| `log_checkpoints_wal` | 1.36 % | `log_checkpoints=on` | Установить `log_checkpoints=off` (опционально) |
| `log_min_duration_statement` | 0.58 % | `log_min_duration_statement=0` | Установить `log_min_duration_statement=500ms` (логировать только медленные запросы) |
| `log_statement` | 0.01 % | `log_statement='all'` или подобное | Проверить текущее значение (см. Блок C) |
| `__errors__` | 0.003 % (9 ERROR) | (не GUC — это реальные инциденты) | **Не трогать** — реальные ERROR'ы должны логироваться |

**Итого основное изменение**: `log_temp_files=-1` (или удалить установку вообще, дефолт = `-1` в Postgres 16).

---

## Блок B — Конкретные изменения в конфигурации `karaoke-db`

### Проверка текущего состояния

```bash
# Текущие значения GUC (через psql)
ssh root@188.119.64.111 "docker exec karaoke-db psql -U SvoeMestoKaraokeUser905 -d karaoke -c \"SHOW log_temp_files; SHOW log_min_duration_statement; SHOW log_checkpoints;\""
```

> ⚠️ **Пароль `SvoeMestoKaraokeUser905` НЕ запрашивается через `-U`** — на проде
> используется peer-аутентификация. Команда выше работает только при запуске
> от пользователя, указанного в `pg_hba.conf`. Если не работает — выполнить
> `ssh root@188.119.64.111 "docker exec karaoke-db psql -U postgres -d karaoke"`
` (потребует пароль, см. \`~/.pgpass\`).

### Вариант 1 (рекомендуемый): `ALTER SYSTEM`

```bash
# 1. Backup текущих значений
ssh root@188.119.64.111 "docker exec karaoke-db psql -U postgres -c 'SHOW ALL;'" > /tmp/before.log

# 2. Применить изменения
ssh root@188.119.64.111 "docker exec karaoke-db psql -U postgres <<EOF
ALTER SYSTEM SET log_temp_files = -1;
ALTER SYSTEM SET log_min_duration_statement = 500;
ALTER SYSTEM SET log_checkpoints = off;
SELECT pg_reload_conf();
EOF"

# 3. Проверить новые значения
ssh root@188.119.64.111 "docker exec karaoke-db psql -U postgres -c 'SHOW log_temp_files; SHOW log_min_duration_statement; SHOW log_checkpoints;'"
```

> ⚠️ `ALTER SYSTEM` пишет в `postgresql.auto.conf`. Если файл read-only
> (некоторые Docker-образы), вариант 2.

### Вариант 2 (fallback): переменные окружения при перезапуске

```bash
# Требует docker stop + start с новыми env. Применять только если ALTER SYSTEM не сработал.
ssh root@188.119.64.111 <<'EOF'
docker stop karaoke-db
docker run -d --rm \
  --name karaoke-db \
  -e POSTGRES_DB=karaoke \
  -e POSTGRES_USER=<FROM_DEPLOY_ENV> \
  -e POSTGRES_PASSWORD=<FROM_DEPLOY_ENV> \
  -e PGUSER=postgres \
  -e POSTGRES_INITDB_ARGS="--encoding=UTF8" \
  postgres:16 \
  -c log_temp_files=-1 \
  -c log_min_duration_statement=500ms \
  -c log_checkpoints=off
EOF
```

> ⚠️ `<FROM_DEPLOY_ENV>` означает «взять из `deploy/.env` локально на машине разработчика»,
> **НЕ подставлять в этот файл**.

---

## Блок C — Команды для применения (Stage 6, task T-022)

> ⚠️ Эти команды должны быть **подтверждены владельцем перед запуском**.
> Constitution п.2: деплой и правка серверных файлов — только по согласию.

```bash
# Полный план:
# 1. Создать backup текущих значений (см. Блок B-1)
# 2. Применить через ALTER SYSTEM (Блок B, Вариант 1)
# 3. Проверить (Блок B-3)
# 4. Проверить, что ERROR'ы продолжают писаться (smoke-test в T023)
# 5. Замерить размер лога через 24 ч (T024)
```

---

## Блок D — Контрольный замер (через 24 ч)

Метрики для сравнения (см. SC-003 спеки):

| Метрика | Baseline (эта неделя) | После (через 24 ч) |
|---------|------------------------|---------------------|
| `total_bytes` за сутки | 8.3 МБ (медиана) | (замерить) |
| `error_count` за сутки | ~1.3 (9 / 7 дней) | (замерить; должно быть ≥ 1) |
| `share_pct` доминирующей категории `log_temp_files` | 18.56 % | (должно упасть) |

Замер делается скриптом `scripts/05-post-change-metrics.sh`
(создаётся на Stage 6, task T-024). Схема снимка — `PostChangeMetrics`
в [data-model.md](../data-model.md).

---

## Известные риски при отключении

- **`ALTER SYSTEM` vs env-vars**: на некоторых Docker-образах `ALTER SYSTEM`
  пишет в `postgresql.auto.conf`, который может может быть read-only. Нужно
  проверить заранее (через `SHOW config_file` внутри контейнера).
- **`log_min_duration_statement=500ms`**: пропустит запросы длительностью
  < 500 мс. Если в production-профилировании нужны короткие запросы —
  оставить 0.
- **`log_checkpoints=off`**: уберёт сообщения о checkpoint (полезные для
  мониторинга WAL). Можно оставить `log_checkpoints=on` (всего 1.36 %),
  если WAL-мониторинг важен.