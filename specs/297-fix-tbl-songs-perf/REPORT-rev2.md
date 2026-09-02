# Отчёт (rev.2): Pass 297 — Оптимизация `tbl_songs`

**Спека**: [`specs/297-fix-tbl-songs-perf/spec.md`](specs/297-fix-tbl-songs-perf/spec.md)
**Work package**: http://localhost:8080/work_packages/47
**Ветка**: `298-fix-tbl-songs-perf`
**Generated**: 2026-09-02 23:36 MSK

## Что сделано в rev.2 (пункт 4 — cron на проде)

### 1. `/.pgpass` на проде (Pass 297)

Создан файл `/root/.pgpass` (chmod 600) — пароль для пользователя `SvoeMestoKaraokeUser905`:

```
localhost:5432:karaoke:SvoeMestoKaraokeUser905:Pass4Sm-23052008-newpass
127.0.0.1:5432:karaoke:SvoeMestoKaraokeUser905:Pass4Sm-23052008-newpass
```

### 2. Cron-задача на проде

Создан файл `/etc/cron.d/refresh-mv-songs-free-now`:

```
*/5 * * * * root /usr/bin/docker exec karaoke-db psql \
  -U SvoeMestoKaraokeUser905 -d karaoke \
  -c "SELECT refresh_mv_songs_free_now();" \
  >> /var/log/karaoke/mv-refresh.log 2>&1
```

- Запускается **каждые 5 минут** (`*/5`).
- Выполняется от `root` (через `/etc/cron.d/`).
- Логирует в `/var/log/karaoke/mv-refresh.log`.
- REFRESH CONCURRENTLY (не блокирует читателей).

### 3. Верификация

| Шаг | Результат |
|-----|-----------|
| `.pgpass` создан | OK (`-rw------- root root`) |
| Manual test (`refresh_mv_songs_free_now()`) | OK (15673 строк в MV) |
| Cron autostart через 5 минут | OK (видно в логе — успешный `(1 row)` после моего manual test в 23:30:14) |
| Next run | каждые 5 минут автоматически |

## Состояние Pass 297 (полная сводка)

### Локальная admin-машина (nsa-i9)
- ✅ Миграция `44_optimize_tbl_songs.sql` применена
- ✅ Код `StatBySong.refreshCache.freeNow` использует `mv_songs_free_now`
- ✅ Dead code `Song.totalCount()` удалён
- ✅ Compile OK, ktlintCheck OK, smoke-test 8/8 PASS

### Прод (188.119.64.111)
- ✅ Миграция `44_optimize_tbl_songs.sql` применена (15672 строк в MV, 2736 kB)
- ✅ `refresh_mv_songs_free_now()` работает
- ✅ Cron настроен (`/etc/cron.d/refresh-mv-songs-free-now`)
- ✅ `/root/.pgpass` (chmod 600) для `SvoeMestoKaraokeUser905`
- ✅ Логирование в `/var/log/karaoke/mv-refresh.log`
- ✅ Деплой `karaoke-app` + `karaoke-web` (выполнен пользователем)

## Связь с OpenProject

- Work package #47 → status In review
- После проверки пользователем: `tracker.sh close-issue 47`

## Следующий шаг (24ч спустя)

- `bash tools/analyze-prod-incident.sh 24` — должно показать **0 медленных SQL** для count(*)
  и `duration: >1s` (раньше было 17 запросов по 4-5 сек за 24ч).
- `cat /var/log/karaoke/mv-refresh.log` — должно быть ~288 строк записей (24ч × 12 refresh/час).
- `pg_stat_user_tables.tbl_songs.seq_tup_read` — не должен расти экспоненциально.
