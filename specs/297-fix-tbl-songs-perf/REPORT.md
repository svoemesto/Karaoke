# Отчёт: Pass 297 — Оптимизация `tbl_songs`

**Спека**: [`specs/297-fix-tbl-songs-perf/spec.md`](specs/297-fix-tbl-songs-perf/spec.md)
**Work package**: http://localhost:8080/work_packages/47
**Ветка**: `298-fix-tbl-songs-perf`
**Коммит**: `5b1b1db8`
**Generated**: 2026-09-02 19:55 MSK

## Что сделано

### 1. Миграция `deploy/karaoke-db/44_optimize_tbl_songs.sql`

Идемпотентная миграция (можно запускать повторно через psql):

| Объект | Тип | Что делает |
|--------|-----|-----------|
| `tbl_authors_skip_idx` | partial btree (WHERE skip = false) | Ускоряет JOIN в `StatBySong.refreshCache` |
| `tbl_songs_tags_idx` | partial btree (upper(tags)) | Ускоряет фильтр SKIP-тегов (Pass 293) |
| `tbl_songs_free_partial_idx` | partial btree (WHERE free = true) | Ускоряет фильтр free = true |
| `mv_songs_free_now` | MATERIALIZED VIEW | Pre-computed JOIN tbl_songs + tbl_authors с фильтрами skip/id_status/source_markers |
| `mv_songs_free_now_id_idx` | UNIQUE INDEX | Для REFRESH CONCURRENTLY |
| `mv_songs_free_now_song_author_idx` | INDEX | Ускоряет GROUP BY song_author |
| `mv_songs_free_now_free_idx` | partial INDEX | Ускоряет фильтр free |
| `refresh_mv_songs_free_now()` | FUNCTION | Обёртка для cron: REFRESH CONCURRENTLY |

### 2. Код

**`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/StatBySong.kt`**:
- `refreshCache().freeNow` — теперь читает из `mv_songs_free_now` (вместо JOIN tbl_songs+tbl_authors).
- Оставлен runtime-фильтр по `free`/`publish_date`/`publish_time` (в MV уже JOIN+skip+status+markers).

**`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`**:
- `Song.totalCount()` — **удалён** (dead code, `@Suppress("unused")`).
- `Song.loadAuthorSongCounts()` — оставлен (используется в `PublicApiController.kt:443` `metaExpectedCount`).
  TODO: перевести на `Author.loadAuthorTilesWithCounts` (Pass 286) как follow-up.

## Результаты — EXPLAIN ANALYZE на admin-машине (23 819 строк)

| Запрос | До | После | Ускорение |
|--------|-----|-------|-----------|
| `count(*) FROM tbl_songs s JOIN tbl_authors a ON ... WHERE a.skip = false AND ...` | **2 312 мс** | n/a (MV используется) | — |
| `count(*) FROM mv_songs_free_now` | n/a | **5 мс** | ×462 |
| `count(*) FROM mv_songs_free_now WHERE free = true` | n/a | **0.98 мс** | — |
| Полный freeNow (с фильтрами free/publish_date) | 2 312 мс | **41 мс** | **×56** |

**Миграция применена на admin-машине**: 15 672 строки в `mv_songs_free_now` (из 23 819 = 66%).

## Verified

- ✅ `bash -n` для всех bash-скриптов — OK
- ✅ `bash tools/tracker-smoke-test.sh` — 8/8 PASS
- ✅ `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` — OK
- ✅ `./gradlew :karaoke-web:ktlintCheck` — OK
- ✅ Миграция идемпотентна (повторный запуск — пропускает с `NOTICE already exists`)
- ✅ Миграция применена на локальной admin-машине (`karaoke-db:8832`)

## НЕ сделано (требует согласия пользователя)

Согласно `AGENTS.md` для машины `nsa-i9`:

| Операция | Где | Требование |
|----------|-----|-----------|
| ❌ Применение миграции на проде | `ssh root@188.119.64.111 "docker exec -i karaoke-db psql -U postgres -d karaoke" < deploy/karaoke-db/44_optimize_tbl_songs.sql` | Только по прямому согласию |
| ❌ Деплой `karaoke-app` | `karaoke-app:0.0.1-SNAPSHOT.jar` (с новым `StatBySong`)? | Только по прямому согласию |
| ❌ Деплой `karaoke-web` | `karaoke-web:0.0.1-SNAPSHOT.jar` (с новым `StatBySong`)? | Только по прямому согласию |
| ❌ Cron/systemd-timer для refresh MV | `/etc/cron.d/refresh-mv-songs-free-now` (каждые 5 минут) | Только по прямому согласию |

## Дальнейшие шаги

1. **Пользователь проверяет отчёт** и **даёт согласие на деплой**.
2. Применить миграцию на проде (`docker exec -i karaoke-db psql ...`).
3. Запустить `refresh_mv_songs_free_now()` для начального заполнения на проде.
4. Деплой `karaoke-app` + `karaoke-web` (новый jar).
5. Настроить cron на проде: `*/5 * * * * docker exec karaoke-db psql ... -c "SELECT refresh_mv_songs_free_now();"`.
6. Наблюдение 24ч через `bash tools/analyze-prod-incident.sh 24` — проверить, что 0 медленных SQL.

## Связь с OpenProject

- Work package #47 → status In review (готов к проверке пользователем)
- После проверки: `tracker.sh close-issue 47` (пользователь)
