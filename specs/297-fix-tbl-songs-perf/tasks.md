# Tasks for 297 — Оптимизация производительности `tbl_songs`

> Pass 297 — реализация FR-001..FR-010 из `spec.md`.

## Phase 1: Research ✅
- [x] Прочитать отчёт `specs/296-Проверка-инцидентов-на-проде/REPORT.md`
- [x] Найти код `count(*)` для `tbl_songs` → `StatBySong.kt:160`, `Song.kt:7089,7289`
- [x] Найти `COPY tbl_songs` → `deploy/karaoke-db-backup.sh` (pg_dump, норма)
- [x] SSH на прод: `pg_stat_user_tables.tbl_songs` (9 847 seq_scan, 220M seq_tup_read)
- [x] SSH на прод: `pg_indexes WHERE tablename='tbl_songs'` — отсутствуют индексы на `tags`, `free`, `source_markers`
- [x] SSH на прод: `pg_indexes WHERE tablename='tbl_authors'` — отсутствует индекс на `skip`

## Phase 2: Design ✅
- [x] Спека `specs/297-fix-tbl-songs-perf/spec.md` (FR-001..FR-010)
- [x] SQL миграция `deploy/karaoke-db/44_optimize_tbl_songs.sql`

## Phase 3: Implementation

### Шаг 1: миграция БД
- [ ] Создать `deploy/karaoke-db/44_optimize_tbl_songs.sql` (см. spec.md, FR-008)
- [ ] Применить на admin-машине (локально):
  ```bash
  docker exec -i karaoke-db psql -U postgres -d karaoke < deploy/karaoke-db/44_optimize_tbl_songs.sql
  ```
- [ ] Применить на проде (ssh + psql):
  ```bash
  ssh root@188.119.64.111 "docker exec -i karaoke-db psql -U postgres -d karaoke" < deploy/karaoke-db/44_optimize_tbl_songs.sql
  ```
- [ ] Проверить EXPLAIN ANALYZE до/после для `count(*) FROM tbl_songs s JOIN tbl_authors a ON ...`

### Шаг 2: код (Pass 297 follow-up если материализация)
- [ ] Изменить `StatBySong.refreshCache()` — читать `freeNow` из `mv_songs_free_now`
  вместо JOIN с фильтрами (FR-009)
- [ ] Удалить dead code: `Song.kt:7087 totalCount()` (FR-006)
- [ ] ~~Удалить dead code: `Song.kt:7267 loadAuthorSongCounts()`~~ — ОТМЕНЕНО (используется в PublicApiController, follow-up)
- [ ] Проверить `grep "fun totalCount\|fun loadAuthorSongCounts"` = 0

### Шаг 3: cron для refresh MATERIALIZED VIEW
- [x] Создать `/etc/cron.d/refresh-mv-songs-free-now` на проде (Pass 297 шаг 4)
- [x] Создать `/root/.pgpass` (chmod 600) — пароль для `SvoeMestoKaraokeUser905`
- [x] Первый авто-запуск подтверждён в логе `/var/log/karaoke/mv-refresh.log`

## Phase 4: Verification

- [ ] `bash tools/tracker-smoke-test.sh` — 8/8 PASS
- [ ] `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` — OK
- [ ] `./gradlew :karaoke-web:ktlintCheck` — OK
- [ ] На admin-машине: `EXPLAIN ANALYZE SELECT count(*) FROM tbl_songs s JOIN tbl_authors a ON ...` ≤500 мс
- [ ] На проде через 24ч: `bash tools/analyze-prod-incident.sh 24` — 0 медленных SQL `duration: >1s` для count/group by
- [ ] `pg_stat_user_tables.tbl_songs.seq_tup_read` стабилизируется (не растёт экспоненциально)

## Phase 5: Documentation & Close

- [ ] `tracker.sh add-comment 47 --file REPORT.md` (markdown-отчёт о результатах)
- [ ] `tracker.sh mark-review 47`
- [ ] (пользователь) `tracker.sh close-issue 47`
- [ ] Закоммитить в `297-fix-tbl-songs-perf` ветку
- [ ] Создать PR в master через `gh pr create --base master`
