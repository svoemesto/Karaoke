# Отчёт PKG-S2-08 — deploy: BUILD, DATA, RACE, SEC (аудит #336, OpenProject #371)

> **Пакет**: PKG-S2-08 (волна C, S2), 15 записей
> **Ветка**: `579-pkg-s2-08` (от `master` `534766da`), worktree
> `/home/nsa/Karaoke/.worktrees/waveC-s2-08`
> **Дата**: 2026-10-24
> **Записи**: BR-039, BR-040, BR-041, BR-043, BR-534, BR-539, BR-542, BR-543,
> BR-544, BR-545, BR-546, BR-554, BR-555, BR-556, BR-557
> **Guard пакета**: `tools/check-deploy-hardening.sh` (52 проверки, `--self-test`)

## Итог

Все 15 записей закрыты в коде/конфигурации; поведение прода по умолчанию не
изменилось (все переключатели TLS/портов имеют прежние значения, но заданы
явно). Регрессия зафиксирована guard'ом: на `master` — **PASS=8 FAIL=61**, на
ветке — **PASS=52 FAIL=0**, по каждой записи есть отдельная строка PASS.

Четыре вопроса вынесены владельцу (не блокируют мерж, зафиксированы ниже):
сертификаты TLS и выбор «TLS vs туннель» (BR-545/BR-557), судьба платёжной
истории и сообщение в UI при отказе удаления (BR-041), сервисный пользователь
watcher'а и вынос bind-mount `/sm-karaoke/system` (BR-555), ротация секретов,
попавших в историю git (BR-040).

## Записи: было → стало

| BR | Файл:строка (карточка) | Было | Стало |
|---|---|---|---|
| BR-039 | `deploy/karaoke-db/02_result_text_and_index.sql:1`, `18_author_aliases.sql:3` | `02` повторно делал `ADD result_text` / `CREATE INDEX` поверх объектов из `01_initdb.sql` → `column already exists` на БД с нуля, initdb падал (`ON_ERROR_STOP=1`), контейнер в цикле рестартов; `18` делал `ALTER TABLE tbl_authors`, которую не создавал ни один файл каталога | `ADD COLUMN IF NOT EXISTS` + `CREATE INDEX IF NOT EXISTS`; в `18` — идемпотентный bootstrap `tbl_authors` (полный набор колонок) + recordhash-триггер |
| BR-040 | `deploy/docker-compose-whisper.yml:32`, `docker-compose.yml:73`, `docker-compose-app.yml:85`, `new_comp/.../docker-compose-app-new-comp.yml:74`, `searxng-settings/settings.yml:13` | рабочие `WHISPER_API_KEY=nafanya_…` и `SEARXNG_SECRET=i-love-…` в git (ключ — единственная аутентификация ASR, порты 8015/8016 опубликованы) | `${WHISPER_API_KEY:?…}` / `${SEARXNG_SECRET:?…}` из `deploy/do.env` (fail-closed), шаблон `deploy/do.env.example`; в `settings.yml` — плейсхолдер `ultrasecretkey` (entrypoint подменяет на `$SEARXNG_SECRET`) |
| BR-041 | `deploy/karaoke-db/15_monetization.sql:107` | `site_user_id … ON DELETE CASCADE` — удаление пользователя уносило все подписки, включая PAID (`yookassa_payment_id`, `final_price`, `paid_at`) | `ON DELETE RESTRICT` в `15` (новые БД) + идемпотентная миграция `51_subscriptions_on_delete_restrict.sql` (существующие БД: дропает FK при любом имени, создаёт заново) |
| BR-043 | `deploy/do.sh:420–431`, `deploy_web.sh:33`, `deploy_public.sh:33` | push и `do_registry_login` без проверки кода возврата; последний `announce` съедал статус; на проде «Image is up to date» = WARN и exit 0 | `\|\| { echo ERROR; return 1; }` на каждом login/push (в push-, pull-функциях); «Image is up to date» → `exit 1` |
| BR-534 | `deploy/karaoke-db/46_author_sort_order.sql:15–35` | функция переопределена с 12 колонками — потеряны `description`, `short_description`, `warning` (31) и `ready_songs_count`, `total_songs_count` (44); sync-diff правки этих полей не видел | 17 колонок в каноническом порядке (12 исторических + 5 в конце), миграция самодостаточна (`ADD COLUMN IF NOT EXISTS`), backfill; порядок сверяется guard'ом с `18` |
| BR-539 | `deploy/prod-single-host/karaoke-db-backup.sh:50–57` | `pg_dump --no-password` без пароля против `scram-sha-256` → `fe_sendauth: no password supplied`, дамп удалялся; резервных копий прод-БД не было | пароль из `DB_SERVER_POSTGRES_PASSWORD` (ранняя проверка) → stdin → одноразовый `.pgpass` (umask 077, `PGPASSFILE`), удаление после дампа; в argv пароля нет; дамп через `.tmp` + `mv`, чистка старых `.tmp` |
| BR-542 | `deploy/tracker-docker-compose.yml:38–44`, `tools/install-tracker.sh` | `network_mode: host`: postgres `0.0.0.0:5435`, UI `0.0.0.0:8080` на admin-машине с публичными IP; фолбэк портов не работал | bridge-сеть, `127.0.0.1:${TRACKER_HTTP_PORT}:8080` и `127.0.0.1:${TRACKER_DB_PORT}:5435`, `DATABASE_URL` по имени сервиса; installer пишет `TRACKER_HTTP_PORT`/`TRACKER_DB_PORT` |
| BR-543 | `deploy/tracker-db-backup.service:11` | `ExecStartPre=flock` отпускал блокировку до старта бэкапа → два прогона писали в один `.dump.tmp` → битый дамп | `ExecStart=/usr/bin/flock -n /tmp/tracker-db-backup.lock …/tracker-db-backup.sh` (`ExecStartPre` убран) |
| BR-544 | `deploy/prod-single-host/nginx.conf:74`, `deploy/web-server-deploy/deploy/nginx.conf:60` | `ssl_protocols TLSv1 TLSv1.1 TLSv1.2 TLSv1.3`; `server_tokens off` закомментирован | `TLSv1.2 TLSv1.3`; `server_tokens off` в обоих файлах |
| BR-545 | `deploy/prod-single-host/nginx.conf:11–50`, `docker-compose-database.yml` | stream `5433`→Postgres и `9000`→MinIO без TLS; Postgres `ssl=off` | управляемое включение серверного TLS: `DB_SSL_ON=on` + `ssl_cert_file`/`ssl_key_file` (+ `./certs/postgres:ro`), по умолчанию `off` (прежнее поведение); в `nginx.conf` описаны TLS-вариант stream и вариант SSH-туннеля |
| BR-546 | `deploy/prod-single-host/docker-compose-web.yml:1–65` (+ db/storage/public) | ни у одного контейнера прод-хоста нет ротации логов (только у registry) | `logging: json-file, max-size 50m, max-file 5` у всех 4 сервисов; `prune-images.sh` валит юнит при занятости `/` ≥ 85 % |
| BR-554 | `deploy/new_comp/.../docker-compose-web-new-comp.yml:9–14` | `environment`: отображение + список → YAML склеивал всё в одно значение, 4 переменные БД не доходили | список `- VAR=value` (как в `deploy/docker-compose-web.yml`) |
| BR-555 | `deploy/new_comp/sm-karaoke-system/karaoke_watcher.sh:19–48` | исполнялся любой `.sh` из общей с 4 контейнерами директории; дедуп закомментирован; `chmod +x` на месте (TOCTOU) | только `<uuid>.sh`; `mv` в приватный каталог 700 до запуска; проверки владельца/прав/размера/symlink; карантин; fail-closed при group/other-write на общей директории |
| BR-556 | `deploy/new_comp/.../docker-compose-db-new-comp.yml:12–13` | `"8832:5432"` → Postgres 16 без TLS слушал все интерфейсы | `"127.0.0.1:8832:5432"` |
| BR-557 | `deploy/prod-single-host/.env.example:21–22,48` | JDBC без `sslmode` (драйвер молча деградирует в plaintext), MinIO по `http://` | `DB_REMOTE_SSL_MODE` (`application.yml` → `KaraokeAppService` → `buildRemoteJdbcUrl`, default `prefer`; целевое `require`/`verify-full`) + `DB_SSL_ON` в шаблоне; для MinIO зафиксировано требование HTTPS |

## Регрессия «до/после» (команда + ключевой вывод)

1. **Guard по всем 15 записям** (52 проверки, по строке на запись):

```
$ bash tools/check-deploy-hardening.sh --repo /home/nsa/Karaoke   # master 534766da
--- итог: PASS=8 FAIL=61 ---
Нарушенные записи: BR-039 BR-040 BR-041 BR-043 BR-534 BR-539 BR-542 BR-543
BR-544 BR-545 BR-546 BR-554 BR-555 BR-556 BR-557

$ bash tools/check-deploy-hardening.sh                              # ветка
--- итог: PASS=52 FAIL=0 ---

$ bash tools/check-deploy-hardening.sh --self-test
✅ self-test: все 15 правил ловят пустое (битое) состояние
```

2. **BR-554 — compose реально видит переменные** (`docker compose config`):

```
ДО (master, файл без .env):        ПОСЛЕ (ветка):
environment:                       environment:
  WORK_IN_CONTAINER: 1 - DB_LOCAL_POSTGRES_USER= - DB_LOCAL_POSTGRES_PASSWORD=
    - DB_SERVER_POSTGRES_USER= - DB_SERVER_POSTGRES_PASSWORD=
                                     DB_LOCAL_POSTGRES_USER: ""
                                     DB_LOCAL_POSTGRES_PASSWORD: ""
                                     DB_SERVER_POSTGRES_USER: ""
                                     DB_SERVER_POSTGRES_PASSWORD: ""
                                     WORK_IN_CONTAINER: "1"
```

3. **BR-555 — watcher: произвольный файл не исполняется** (реальный прогон
   скрипта на временных каталогах, маркер — созданный файл):

```
ДО (master watcher, /tmp/wt-before): «Запускаю: /tmp/wt-before/scripts/evil.sh»
  → /tmp/wt-before/EVIL создан (исполнение произвольного файла)
ПОСЛЕ (ветка, /tmp/wt-test):
  «Запускаю: …/exec/8392b4ae-….sh»  → /tmp/wt-test/EXECUTED-8392b4ae-… создан
  «REJECT: имя 'evil.sh' не соответствует шаблону <uuid>.sh — в карантин»
  → /tmp/wt-test/EVIL отсутствует, evil.sh в /tmp/wt-test/rejected
```

4. **BR-539 — пароль доходит до pg_dump, но не виден в argv** (локальная
   симуляция того же `sh -c` с фейковым `pg_dump`, без контейнеров и БД):

```
ДО:   pg_dump -h localhost -U user --dbname=karaoke --no-password
      → pg_dump: error: fe_sendauth: no password supplied
ПОСЛЕ: PGPASSFILE_CONTENT=localhost:5432:karaoke:user:p@ss\:wor\ld   (0600, удалён после)
      ARGV=-h localhost -p 5432 -U user --dbname=karaoke --clean --create --if-exists --no-password
      .pgpass удалён: ДА
```

5. **BR-545/BR-557 — Kotlin-тест** `RemoteJdbcUrlTest` (падает/не компилируется
   на старом коде — функции `buildRemoteJdbcUrl` нет; проходит после):

```
$ ./gradlew :karaoke-app:test --tests '*RemoteJdbcUrlTest*'
BUILD SUCCESSFUL
TEST-com.svoemesto.karaokeapp.RemoteJdbcUrlTest.xml: tests=5 failures=0 errors=0
```

6. **BR-039/BR-534 — идемпотентность и порядок колонок** проверяются guard'ом
   (`ADD COLUMN IF NOT EXISTS` в `02`, bootstrap `tbl_authors` в `18`, 17
   колонок и совпадение порядка `18` ↔ `46`). Прогон initdb-цепочки на пустом
   PGDATA в этом окружении недоступен (запрет на контейнеры/БД) — проверка
   статическая; «прогнать набор на пустом PGDATA» остаётся ручным шагом.

## Проверки

| Проверка | Команда | Результат |
|---|---|---|
| Тесты приложения | `GRADLE_USER_HOME=… ./gradlew --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" :karaoke-app:test` | BUILD SUCCESSFUL |
| Тесты web | то же с `:karaoke-web:test` | BUILD SUCCESSFUL |
| Kotlin-стиль | `:karaoke-app:ktlintCheck :karaoke-web:ktlintCheck` | BUILD SUCCESSFUL |
| Guard пакета | `bash tools/check-deploy-hardening.sh` | PASS=52 FAIL=0 |
| Self-test guard'а | `bash tools/check-deploy-hardening.sh --self-test` | 15/15 правил ловят пустое состояние |
| Knowledge structure | `bash tools/check-knowledge-structure.sh` | OK (9/9) |
| Ссылки в доках | `python3 tools/check-doc-references.py` | 0 проблем (после коммита доков) |
| Изоляция субагентов | `bash tools/check-subagent-isolation.sh` | commits ветки уникальны, worktrees — 6 (каждый в своём) |
| Синтаксис shell | `bash -n` для изменённых скриптов | OK |
| YAML прод-compose | `python3 -c yaml.safe_load(...)` | у всех 4 сервисов `logging` с max-size/max-file |
| Секреты | `git ls-files \| grep -iE '\.env$\|\.key$\|\.pem$'` + `grep` по `deploy/` | пусто; значений `nafanya_…`/`i-love-…` в `deploy/` нет |

## За владельцем (сделано максимум безопасного)

1. **BR-040 — ротация секретов.** Значения `WHISPER_API_KEY` и
   `SEARXNG_SECRET` остались в истории git (удаление из рабочего дерева не
   убирает их из прошлых коммитов). Нужна ротация обоих + при желании
   `git filter-repo`/BFG (переписывание истории — только по слову владельца).
   Порты whisper (8015/8016) и searxng (8888) по-прежнему публикуются на все
   интерфейсы: адрес ASR задаётся в `tbl_properties` (`whisperAsrUrl`) и может
   быть host-IP, поэтому перевод на loopback без проверки сломал бы распознавание.
2. **BR-041 — судьба платёжной истории.** `RESTRICT` останавливает потерю
   данных: удаление пользователя с платежами теперь падает ошибкой БД
   (fail-safe). Нужно решение: мягкое удаление/анонимизация `tbl_site_users`
   либо явное «удалить вместе с историей» с подтверждением; заодно —
   человекочитаемое сообщение в админке вместо 500 (`SiteUsersController.delete`).
   Миграцию `51` нужно применить на LOCAL и PROD вручную.
3. **BR-545/BR-557 — TLS.** Механизм включён, но сертификаты не выпущены:
   нужен выбор «TLS на сервере Postgres/MinIO» vs «SSH/WireGuard-туннель» и
   установка сертификатов в `deploy/prod-single-host/certs/postgres/`, затем
   `DB_SSL_ON=on` и `DB_REMOTE_SSL_MODE=require|verify-full` (для MinIO —
   `MINIO_CERTS_DIR` + `STORAGE_REMOTE_ENDPOINT=https://…`).
4. **BR-555 — архитектура watcher'а.** Пока контейнерный процесс — root на
   bind-mount, права каталога его не останавливают: нужен отдельный сервисный
   пользователь для watcher'а и/или удаление `/sm-karaoke/system` из
   контейнеров webvue/webvue3/mlt (проверить, какие пути им реально нужны),
   либо отказ от исполнения файлов на хосте.

## Коммиты

| Коммит | Что |
|---|---|
| `75b91b72` | BR-039, BR-534 — миграции с нуля, recordhash авторов |
| `a7ee6585` | BR-041 — платёжная история и `ON DELETE RESTRICT` (+ миграция 51) |
| `fbf39f01` | BR-040 — секреты whisper/searxng из окружения (+ `do.env.example`) |
| `19f37e06` | BR-043 — push и «Image is up to date» валят деплой |
| `3658d08c` | BR-539, BR-543 — бэкап прод-БД и блокировка трекера |
| `acda88bf` | BR-546 — ротация логов + порог диска |
| `340a71cb` | BR-544, BR-545, BR-557 — TLS (nginx, Postgres, sslmode клиента) + `RemoteJdbcUrlTest` |
| `7ade45a1` | BR-542, BR-554, BR-556 — loopback-порты и `environment` списком |
| `d64e0a91` | BR-555 — watcher исполняет только проверенные файлы |
| последний коммит ветки (`git log -1`) | guard `tools/check-deploy-hardening.sh`, knowledge/, `docs/features/deploy-hardening.md`, этот отчёт |

Примечание по раскладке: строка `DB_REMOTE_SSL_MODE` в
`deploy/docker-compose-app.yml` попала в коммит BR-040 (`fbf39f01`) — файл
содержал изменения двух записей; в коммите BR-546 (`acda88bf`) из
`docker-compose-web.yml`/`docker-compose-database.yml` вошёл только блок
`logging`, TLS-часть тех же файлов — в `340a71cb`.

## Изменённая SSoT/документация

- `knowledge/system/infra/sql-migrations.md` — Решение 5 (initdb-цепочка с
  нуля) и Решение 6 (канонический recordhash авторов), каталог дополнен 46/51.
- `knowledge/system/infra/sql-migrations-monetization.md` — миграция 51 и
  `ON DELETE RESTRICT`.
- `knowledge/system/infra/deploy-overview.md` — раздел «Hardening S2» с
  инвариантами пакета и списком «за владельцем».
- `knowledge/domains/persistence/domain.md` — инвариант: recordhash покрывает
  все значимые колонки в фиксированном порядке.
- `docs/features/deploy-hardening.md` — per-feature документ (FR-009).

## Дополнение 2026-10-25: пакет требовал ручного шага в do.env

**Что случилось.** После merge BR-040 (`WHISPER_API_KEY` / `SEARXNG_SECRET` вынесены из
compose в `${VAR:?…}`) `karaoke-app` перестал запускаться:

```
error while interpolating services.searxng.environment.[]: required variable
SEARXNG_SECRET is missing a value: SEARXNG_SECRET is required (set in deploy/do.env or deploy/.env)
```

Простой — 54 минуты. Шаблон `deploy/do.env.example` с `CHANGE_ME` был добавлен, а сам
`deploy/do.env` на машине — нет: значение просто исчезло из конфигурации.

**Что сделано.** Значения восстановлены в `deploy/do.env` (из истории git, коммит
`c97b2800^`) с явной пометкой «считать скомпрометированными, требуется ротация»;
`docker compose config` разрешается (exit 0); приложение поднято и проверено
(`/`, `/songs`, `/processes` → 200, очередь не запущена — 0 активных заданий).

**Вывод для процесса.** Пакет, переводящий значение в переменную окружения, не может
считаться закрытым без обновления `deploy/do.env` на каждой машине. Правило занесено в
`specs/336-code-audit/protocol.md` § «Обязательные переменные окружения».

**Прод не затронут:** его compose-файлы (`deploy/prod-single-host/*.yml`) этих переменных
не требуют, и сервисов `searxng`/`whisper` на проде нет.
