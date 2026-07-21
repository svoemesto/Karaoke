# `tbl_public_settings` и ручные SQL-миграции

> **Status**: active
> **Last Updated**: 2026-07-21 (PR #24, 012-development-md-rewrite)

## `tbl_public_settings`

`tbl_public_settings` (`deploy/karaoke-db/07_public_settings.sql`) — маленькая key/value таблица
в Postgres для настроек, нужных сервисам **на сервере** (сейчас — ключи капчи).

**Не путать с `KaraokeProperties`** (~150 файловых настроек рендера, только на admin-машине).

Всё, что должно работать в проде, — через `tbl_public_settings` с паттерном
`target=local|remote` (`PublicSettingsController.kt`, раздел «Настройки сайта» в webvue3).

## Ручные SQL-миграции

**Новые таблицы `deploy/karaoke-db/*.sql` и `ALTER TABLE` — применять вручную и на LOCAL,
и на серверной БД (`<PROD_SERVER_IP>:8832) отдельно** (миграция сама на сервер не попадает):

```bash
# LOCAL
docker exec -i -u postgres karaoke-db psql -d karaoke < migration.sql
```

**На проде роли `postgres` НЕТ** — узнать реальную роль:
```bash
docker exec karaoke-db env | grep '^POSTGRES_USER='
```
(узкий grep, не полный env — иначе утечёт пароль), передать `-U <роль>`. Флаг `-i` обязателен
(иначе heredoc не долетает до psql).

## recordhash-триггеры (важно)

Любая правка схемы таблицы, участвующей в recordhash-diff, обязана обновить и **функцию триггера**
(+ разовый `UPDATE .. SET recordhash = ..` для существующих строк), иначе
`SyncTarget.listHashes()` не увидит новую колонку как diff и LOCAL↔SERVER sync молча игнорирует
изменение поля.

Таблицы с recordhash-триггерами:
- `tbl_events` — `deploy/recordhash_events.sql`.
- `tbl_site_users` — триггер `update_tbl_site_users_recordhash`, инлайн в `06_site_users.sql`/
  `12_site_user_limits.sql`/`13_site_user_editor_role.sql`.

## См. также

- [docs/deployment.md](./deployment.md) — деплой на прод
- [docs/architecture-notes-archive.md](./architecture-notes-archive.md) — история миграций
