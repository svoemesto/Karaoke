# Feature: Hardening deploy-контура (PKG-S2-08, аудит #336, OpenProject #371)

> **Status**: active
> **Feature Key**: deploy-hardening
> **Last Updated**: 2026-10-24
> **Spec**: [specs/371-pkg-s2-08/report.md](../../specs/371-pkg-s2-08/report.md)
> **Created**: 2026-10-24
> **История**: аудит #336 (порции P-70, P-71, P-72, P-73; 15 записей
> BR-039, BR-040, BR-041, BR-043, BR-534, BR-539, BR-542, BR-543, BR-544,
> BR-545, BR-546, BR-554, BR-555, BR-556, BR-557) → пакет PKG-S2-08,
> OpenProject #371, ветка `579-pkg-s2-08`.

## Что делает

Пакет закрывает 15 S2-находок аудита в deploy-контуре: миграции,
секреты, платёжную историю, push-ошибки, бэкапы, сетевую экспозицию,
TLS, ротацию логов, compose-окружение и исполнение скриптов на хосте.
Поведение прода по умолчанию **не меняется**: все переключатели
(TLS, порты, пароли) имеют те же значения, что были, но заданы явно и
проверяются guard'ом.

| BR | Было | Стало |
|---|---|---|
| 039 | `02` повторно добавлял `result_text`/индекс; `18` делал `ALTER TABLE tbl_authors`, которой никто не создавал | идемпотентные `IF NOT EXISTS` + bootstrap `tbl_authors` и recordhash-триггера в `18` |
| 040 | `WHISPER_API_KEY` / `SEARXNG_SECRET` в git (рабочие) | `${VAR:?…}` из `deploy/do.env`, шаблон `deploy/do.env.example` |
| 041 | `tbl_subscriptions.site_user_id ON DELETE CASCADE` | `ON DELETE RESTRICT` (в `15` для новых БД + миграция `51` для существующих) |
| 043 | push логина/образа не проверялся, «up to date» = WARN | `|| return 1` на каждом push/логине; «up to date» — `exit 1` |
| 534 | `46` переопределяла recordhash-функцию и теряла 5 колонок | 17 колонок, канонический порядок, самодостаточная миграция, backfill |
| 539 | `pg_dump --no-password` без пароля → дампов нет | пароль по stdin в одноразовый `.pgpass`, дамп пишется через `.tmp` + `mv` |
| 542 | трекер в `network_mode: host` (БД 5435 и UI 8080 на 0.0.0.0) | bridge-сеть, порты только на `127.0.0.1`, `DATABASE_URL` по имени сервиса |
| 543 | `ExecStartPre=flock` отпускал блокировку до старта бэкапа | `ExecStart=/usr/bin/flock -n … script` |
| 544 | `ssl_protocols TLSv1 TLSv1.1 …`, `server_tokens off` закомментирован | `TLSv1.2 TLSv1.3`, `server_tokens off` в обоих nginx |
| 545 | stream-прокси 5433/9000 без TLS, Postgres `ssl=off` | управляемый `DB_SSL_ON` (default off), `ssl_cert_file`/`ssl_key_file`, описан TLS-вариант stream-прокси |
| 546 | у прод-контейнеров нет ротации логов | `logging: json-file, max-size 50m, max-file 5` у всех 4 сервисов + порог 85 % в `prune-images.sh` |
| 554 | `environment:` — смесь отображения и списка, переменные БД не доходили | список `- VAR=value`, как в основной схеме |
| 555 | watcher исполнял любой `.sh` из общей с контейнерами директории (`chmod +x` на месте) | только `<uuid>.sh`, перенос в приватный каталог 700, проверки владельца/прав/размера |
| 556 | БД new_comp-контура слушала `0.0.0.0:8832` | `127.0.0.1:8832:5432` |
| 557 | JDBC без `sslmode`, MinIO по `http://` | `DB_REMOTE_SSL_MODE` (default `prefer`, целевое `verify-full`), `DB_SSL_ON`, требование HTTPS для MinIO зафиксировано |

## Зачем

Находки аудита #336 — не стиль, а работающие дефекты:

- **бэкапов прод-БД не существовало** (BR-539): `pg_dump --no-password`
  против `scram-sha-256` из конфигурации доступа (`pg_hba`) образа `postgres:16` падал с
  `fe_sendauth: no password supplied`, скрипт удалял пустой дамп;
- **платёжная история удалялась вместе с пользователем** (BR-041):
  каскад уносил `PAID`-записи с `yookassa_payment_id`, нужные для чеков
  54-ФЗ и сверки с ЮKassa;
- **«деплой успешен» при недоставленном образе** (BR-043): неудачный
  push не валил скрипт, прод тянул предыдущий `:1`, а «Image is up to
  date» печаталось как WARN;
- **БД и UI трекера слушали все интерфейсы** admin-машины, у которой
  публичные IP (BR-542/BR-556);
- **прод-данные и пароль БД шли через интернет открытым текстом**
  (BR-545/BR-557): stream-прокси без TLS + `sslmode=prefer` (драйвер
  молча деградирует в plaintext);
- **исполнение кода на хосте из общего с контейнерами каталога**
  (BR-555).

## Как работает

- **Миграции**: `deploy/karaoke-db/` монтируется в
  `/docker-entrypoint-initdb.d`; entrypoint выполняет файлы по порядку с
  `ON_ERROR_STOP=1`. Поэтому каждый файл идемпотентен, а `18` создаёт
  `tbl_authors` (её не создавал ни один файл каталога). Миграция `51`
  пересоздаёт FK `tbl_subscriptions.site_user_id` с `RESTRICT` на уже
  существующих БД (применять вручную на LOCAL и PROD отдельно).
- **Секреты**: `deploy/do.sh` подгружает `deploy/do.env` через
  `set -a`, compose подставляет `${WHISPER_API_KEY}` /
  `${SEARXNG_SECRET}`. `${VAR:?…}` — fail-closed: без значения
  `docker compose up` падает с понятным сообщением. SearXNG: в
  `settings.yml` лежит плейсхолдер `ultrasecretkey`, entrypoint образа
  заменяет его на `$SEARXNG_SECRET` (файл в bind-mount, см. ловушки).
- **Push/деплой**: каждая `docker image push` и `do_registry_login`
  проверяют код возврата; `deploy_web.sh` / `deploy_public.sh` считают
  «Image is up to date» ошибкой доставки (`exit 1`).
- **Бэкапы**: `karaoke-db-backup.sh` кладёт пароль в одноразовый
  `.pgpass` внутри контейнера (stdin, umask 077), `PGPASSFILE` — в
  окружении `pg_dump`; секрет не виден в `ps`. Блокировку трекера
  держит `flock` вокруг всего прогона.
- **Сеть/TLS**: порты локальных сервисов — `127.0.0.1:<host>:<container>`;
  серверный TLS Postgres включается `DB_SSL_ON=on` +
  `deploy/prod-single-host/certs/postgres/`, клиент — `DB_REMOTE_SSL_MODE`.
- **Логи**: `logging.max-size/max-file` у каждого сервиса прод-compose;
  `prune-images.sh` дополнительно валит юнит при занятости диска ≥ 85 %.
- **Watcher**: `createScriptForHost` кладёт `<uuid>.sh` в
  `/sm-karaoke/system/scriptsFromDocker`; watcher принимает только это
  имя, переносит файл в `/tmp/karaoke-script-exec` (700), проверяет
  владельца/права/размер и только потом запускает.

## Инварианты

1. Любой файл `deploy/karaoke-db/*.sql` MUST быть идемпотентным и MUST NOT
   падать на БД, созданной из предыдущих файлов каталога.
2. В отслеживаемых файлах репозитория MUST NOT быть значений
   `WHISPER_API_KEY`, `SEARXNG_SECRET` и прочих секретов (`grep` по
   `deploy/` — пусто).
3. `recordhash`-функция `tbl_authors` MUST покрывать все 17 колонок в
   каноническом порядке, зафиксированном в `46` и `18`.
4. Денежные таблицы MUST NOT терять историю при удалении смежной сущности
   (`ON DELETE RESTRICT`, не `CASCADE`).
5. Каждый `docker push`/логин в `do.sh` MUST проверять код возврата;
   «up to date» в деплое MUST быть ошибкой.
6. Пароли MUST NOT попадать в argv (ни `docker exec -e`, ни URL в
   командной строке).
7. Порты БД/UI локальных сервисов MUST публиковаться только на
   `127.0.0.1`.
8. `ssl_protocols` MUST NOT содержать TLS 1.0/1.1; `server_tokens off`
   MUST быть включён.
9. У каждого сервиса прод-compose MUST быть `logging` с `max-size` и
   `max-file`.
10. `environment:` в compose MUST быть либо списком, либо отображением —
    без смешения.
11. Watcher MUST NOT исполнять файл из общей директории: только
    `<uuid>.sh` после переноса в приватный каталог.

Все 11 инвариантов проверяет `bash tools/check-deploy-hardening.sh`
(50 проверок, по одной строке PASS/FAIL на каждую запись пакета; `--repo
PATH` — чужой checkout, `--self-test` — проверка самих правил).

## Известные ловушки

- **`searxng-settings/settings.yml` мутируется entrypoint'ом.** Каталог
  смонтирован bind-mount'ом, а entrypoint делает `sed` плейсхолдера
  `ultrasecretkey` на `$SEARXNG_SECRET` прямо в файле — после старта
  контейнера **хостовый** файл содержит реальный секрет. Не коммитить
  его в таком состоянии: `git checkout -- deploy/searxng-settings/settings.yml`;
  guard ловит отличие `secret_key` от плейсхолдера.
- **`environment`-смесь не является ошибкой YAML.** `WORK_IN_CONTAINER: 1`
  + список ниже склеивается в одно значение, и compose молча не передаёт
  ни одной переменной. Проверять `docker compose config`.
- **«Image is up to date» — не успех.** При неизменном digest (например,
  сборка не менялась) деплой теперь считается неуспешным: артефакт не
  доставлен. Отличать от настоящего успеха — по `Downloaded newer image`.
- **`15_monetization.sql` не переигрывается** (`CREATE TABLE IF NOT
  EXISTS`) — на существующей БД `RESTRICT` появляется только после
  применения `51`.
- **Смена порядка колонок в recordhash-функции** делает хэши несравнимыми
  между LOCAL и SERVER: каждая сторона считает свой md5, sync видит
  расхождение по всем строкам. Менять набор/порядок можно только вместе
  на обеих БД и с backfill.
- **`docker exec -e PGPASSWORD=…`** светит пароль в `ps`/`docker inspect` —
  использовать stdin + `.pgpass`.
- **Каталог watcher'а общий с контейнерами**: пока контейнерный процесс —
  root на bind-mount, права каталога его не останавливают. Полное
  решение (сервисный пользователь + вынос mount'ов) — за владельцем.

## Ссылки

- [specs/371-pkg-s2-08/report.md](../../specs/371-pkg-s2-08/report.md) — отчёт пакета.
- [knowledge/system/infra/sql-migrations.md](../../knowledge/system/infra/sql-migrations.md) — Решения 5 и 6.
- [knowledge/system/infra/sql-migrations-monetization.md](../../knowledge/system/infra/sql-migrations-monetization.md) — миграция 51.
- [knowledge/system/infra/deploy-overview.md](../../knowledge/system/infra/deploy-overview.md) — раздел «Hardening S2».
- [knowledge/domains/persistence/domain.md](../../knowledge/domains/persistence/domain.md) — инвариант recordhash.
- [knowledge/guidelines/security-headers.md](../../knowledge/guidelines/security-headers.md) — соседний пакет PKG-S2-09 (заголовки).
- `tools/check-deploy-hardening.sh` — guard пакета.
