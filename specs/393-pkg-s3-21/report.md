# PKG-S3-21 (OpenProject #393) — deploy: BUILD, DATA, PERF, SEC — 15 BR

- **Аудит**: #336, волна E, пакет `PKG-S3-21` (`specs/336-code-audit/backlog.md`).
- **Ветка**: `611-pkg-s3-21` от `origin/master` `35026a20`.
- **Worktree**: `/home/nsa/Karaoke/.worktrees/waveE-s3-21`.
- **Источник правды**: карточки `specs/336-code-audit/findings/P-70.md` (P-70-04…09),
  `P-71.md` (P-71-03…06), `P-72.md` (P-72-07/08), `P-73.md` (P-73-09…12); порции
  определены по `registry.csv` (P-70, P-71, P-72, P-73).
- **Что НЕ делалось**: миграции не применялись ни к LOCAL, ни к PROD; контейнеры, прод,
  БД и `specs/336-code-audit/registry.csv` не трогались; деплоя не было.

## Сводка

| BR | Класс | Было | Стало | Проверка |
|---|---|---|---|---|
| BR-042 | BUILD | сборка тегировала `:${BUILD_VERSION}`, прод тянул литерал `:1` | `IMAGE_TAG` — единый тег на build/push/pull; тег передаётся на прод по ssh; `${IMAGE_TAG:-1}` в compose; предыдущий тег в `.version.prev` | guard, 7 проверок |
| BR-044 | DATA | 7 миграций `tbl_site_users` — 7 разных наборов колонок; повторный прогон старой сужал md5 | канонический `deploy/recordhash_site_users.sql` (19 колонок) + все 7 миграций повторяют его целиком и сами добавляют колонки; миграция `56` — на существующие БД | `check-recordhash-consistency.py`, check 2/5 |
| BR-045 | PERF | GIN-индекс по литералу `'result_text'` на `tbl_settings`; поиск — по `result_text` в `tbl_songs` | индекс по колонке в `01`/`02`; `02` пересоздаёт через `DROP`+`CREATE`; `54` пересоздаёт на существующих БД | guard, 4 проверки |
| BR-046 | BUILD | `restore_db`/`create_clear_db`: нет mount `/dumps`, `-U postgres`, `docker exec -it`, код возврата не проверялся | mount `./karaoke-db/dumps:/dumps`, `-U ${DB_LOCAL_POSTGRES_USER}`, `exec -i`, проверка кода до `announce` | guard, 4 проверки |
| BR-047 | BUILD | `minio/minio:latest`, `MINIO_ACCESS_KEY/SECRET_KEY` | пин `RELEASE.2025-09-07T16-13-09Z@sha256:a1a8bd4a…` (registry-зеркало, как прод), `MINIO_ROOT_USER/PASSWORD` в локальном и прод-compose | guard, 4 проверки |
| BR-535 | DATA | `WHERE id_status >= 3` переводил незавершённые песни (3/4/5) в READY | `WHERE id_status IN (3, 4, 5)`; шапка больше не обещает ложную идемпотентность | guard, 2 проверки |
| BR-536 | DATA | INSERT песни не переносил `album_id` и поздние колонки | **уже исправлено** пакетом PKG-S2-10 (BR-108) — правок нет, добавлена регрессионная проверка | guard, 1 проверка (ловит регресс) |
| BR-537 | DATA | FK `tbl_stem_jobs.site_user_id ON DELETE CASCADE` → объекты MinIO остаются сиротами, оплаченный результат теряется | `ON DELETE RESTRICT` в `22` + миграция `55` для существующих БД | guard, 2 проверки |
| BR-538 | BUILD | `44` обещала `CREATE OR REPLACE TRIGGER`, в тексте — `CREATE TRIGGER` → падение при повторе | `DROP TRIGGER IF EXISTS` перед `CREATE TRIGGER`, шапка приведена в соответствие | guard, 1 проверка |
| BR-547 | BUILD | прод-`karaoke-web`: нет healthcheck/лимитов; `-XX:HeapDumpPath=/dumps` без mount | healthcheck (`/api/public/authors-tiles`, read-only), `mem_limit: 1600m`, `cpus: 2.0`, mount `./karaoke-db/dumps:/dumps` | guard, 3 проверки |
| BR-548 | SEC | TTS-модель скачивалась без сверки, грузилась через pickle | sha256 зафиксирован (`896ab963…`, 40 107 184 байта); загрузка во временный файл + `os.replace` после сверки; проверка до `load_pickle`; `torch`/`soundfile` — после проверки | guard, 4 проверки + runtime-тест |
| BR-549 | SEC | у SPA-nginx публичного сайта нет защитных заголовков | `X-Frame-Options`/`nosniff`/`Referrer-Policy` в `server` и явно в `location` со своими `add_header`; HSTS — на host-nginx (TLS-терминация) | `check-security-headers.py` (файл добавлен в проверку) |
| BR-550 | PERF | `client_max_body_size 1024m` на публичном контуре | `200m` — как `spring.servlet.multipart` (karaoke-web) и host-nginx | guard, 3 проверки |
| BR-559 | BUILD | new_comp `do.sh`: `source .env` без проверки, без fail-fast → работа на пустых значениях | `set -euo pipefail`, проверка наличия `.env`/`do.env`, ошибки compose валят скрипт | guard, 3 проверки |
| BR-560 | BUILD | new_comp compose: нет `restart`/healthcheck/лимитов | `restart: unless-stopped`, healthcheck, `mem_limit`/`cpus` у app/web, `restart` у webvue/webvue3 | guard, 5 проверок |

Итог: 14 записей исправлены, 1 (BR-536) уже была закрыта ранее — добавлена регрессионная
проверка. Все 15 покрыты guard'ами.

## Изменённые файлы

Миграции: `deploy/karaoke-db/{01,02,12,13,14,17,21,22,32,40,44,45}_*.sql`,
новые `54_fix_result_text_gin_index.sql`, `55_stem_jobs_on_delete_restrict.sql`,
`56_site_users_recordhash_canonical.sql`, канонический `deploy/recordhash_site_users.sql`,
`deploy/karaoke-db/dumps/.gitkeep` (каталог дампов, `.gitignore`).

Скрипты/compose: `deploy/do.sh`, `deploy/deploy_web.sh`, `deploy/deploy_public.sh`,
`deploy/docker-compose-database.yml`, `deploy/docker-compose-storage.yml`,
`deploy/prod-single-host/{do.sh,docker-compose-web.yml,docker-compose-public.yml,docker-compose-storage.yml,nginx_karaoke-public.conf}`,
`deploy/new_comp/sm-karaoke-system/deploy/{do.sh,docker-compose-{app,web,webvue,webvue3}-new-comp.yml}`,
`deploy/tts/silero_say.py`.

Код/guard'ы/доки: `karaoke-app/.../model/SiteUser.kt` (useInHash=false у `last_login_at`/
`created_at`), `tools/check-deploy-hardening.sh` (+13 правил: 042, 045, 046, 047, 535, 536,
537, 538, 547, 548, 550, 559, 560), `tools/check-recordhash-consistency.py` (канонический
`site_users` + check 5), `tools/check-security-headers.py` (SPA-конфиг + поиск `server{}`
внутри `http{}`), `docs/features/deploy-hardening.md`,
`knowledge/system/infra/sql-migrations.md`, `knowledge/domains/persistence/domain.md`.

## «До / после»: команды и вывод

Baseline — `HEAD~4` (`35026a20`, до коммитов пакета): `git archive` в `.verify-before`;
guard'ы запускаются из рабочего дерева (новые правила) против старого снимка.

### deploy-hardening (13 записей)

```bash
$ bash tools/check-deploy-hardening.sh --repo .verify-before | grep -E '[BR-(042|045|...)]'
# было: 7 FAIL BR-042, 4 FAIL BR-045, 4 FAIL BR-046, 4 FAIL BR-047, 2 FAIL BR-535,
#       2 FAIL BR-537, 1 FAIL BR-538, 3 FAIL BR-547, 3 FAIL BR-548, 2 FAIL BR-550,
#       3 FAIL BR-559, 3 FAIL BR-560 (BR-536 PASS — уже исправлено ранее)
#   --- итог: PASS=56 FAIL=39 ---

$ bash tools/check-deploy-hardening.sh
# стало: PASS=95 FAIL=0  (по нашим BR: 042×7, 045×4, 046×4, 047×4, 535×2, 536×1,
#        537×2, 538×1, 547×3, 548×4, 550×3, 559×3, 560×5)
```

### BR-044 — recordhash `tbl_site_users`

```bash
# было (старые миграции + новый канонический файл): check 5 видит дрейф всех 7 миграций
$ python3 tools/check-recordhash-consistency.py --root .verify-before
FAIL deploy/karaoke-db/12_site_user_limits.sql: [check 5] формула recordhash tbl_site_users
     отличается от канонической … повторное применение откатит набор колонок в md5
… (13/14/17/21/40/45 — то же; в старом SiteUser.kt нет useInHash=false у last_login_at/created_at)
# стало
$ python3 tools/check-recordhash-consistency.py --quiet; echo $?
0
```

### BR-549 — заголовки SPA-nginx

```bash
$ python3 tools/check-security-headers.py .verify-before/deploy/prod-single-host/nginx_karaoke-public.conf
FAIL: … server-блок «server» не объявляет «X-Frame-Options» / «X-Content-Type-Options» /
      «Referrer-Policy»; location /api/public и /api/storage не наследуют серверные заголовки
$ python3 tools/check-security-headers.py
OK [deploy/prod-single-host/80to8897]: заголовки безопасности на месте
OK [deploy/prod-single-host/nginx_karaoke-public.conf]: заголовки безопасности на месте
```

### BR-548 — целостность TTS-модели (runtime)

```bash
$ TMPHOME=$(mktemp -d); mkdir -p "$TMPHOME/.cache/silero-tts"
$ printf 'corrupt-model' > "$TMPHOME/.cache/silero-tts/v4_ru.pt"
$ HOME="$TMPHOME" python3 deploy/tts/silero_say.py "тест"; echo $?
ERROR: размер модели TTS 13 != ожидаемого 40107184 байт; файл …v4_ru.pt не загружаем (BR-548)
1                       # pickle не загружен, кода из файла не было
$ python3 -c "…silero_say.ensure_model(); print(sha256_file(MODEL_PATH))"
896ab96347d5bd781ab97959d4fd6885620e5aab52405d3445626eb7c1414b00   # настоящая модель проходит
```

### BR-536 — регресс уже закрытой записи

```bash
$ mkdir -p .verify-b536/…/model && echo 'val x = 1' > …/Song.kt
$ bash tools/check-deploy-hardening.sh --repo .verify-b536 | grep BR-536
FAIL [BR-536] …: колонка album_id не переносится INSERT'ом
FAIL [BR-536] …: колонка status_process_demo не переносится INSERT'ом   # и остальные поздние
```

### Общий self-test правил

```bash
$ bash tools/check-deploy-hardening.sh --self-test
✅ self-test: все 28 правил ловят пустое (битое) состояние
```

## Проверки (как в CI)

| Проверка | Результат |
|---|---|
| `GRADLE_USER_HOME=… ./gradlew --max-workers=2 … :karaoke-app:test :karaoke-web:test ktlintCheck` | PASS — `BUILD SUCCESSFUL in 1m 23s`, 29 tasks |
| `bash tools/check-knowledge-structure.sh` | PASS (`9/9`) |
| `python3 tools/lint-knowledge.py --baseline …` | PASS |
| `python3 tools/check-doc-references.py` | PASS (после добавления этого отчёта) |
| `python3 tools/check-recordhash-consistency.py` | PASS |
| `bash tools/check-no-legacy-tbl-settings.sh` | PASS (миграция 54 не ссылается на старое имя `tbl_settings`) |
| `bash tools/check-deploy-hardening.sh` | PASS (`PASS=95 FAIL=0`) + `--self-test` |
| `python3 tools/check-security-headers.py` | PASS (2 файла) |
| `bash tools/check-no-mp4-mentions.sh` | PASS |
| `bash tools/check-feature-doc.sh docs/features/*.md` | PASS |
| `bash tools/check-subagent-isolation.sh` | PASS |

## За владельцем

1. **BR-537**: автоматическая уборка объектов MinIO перед удалением пользователя (app-level
   outbox: сначала объекты, потом строки). Сейчас максимум безопасного — `RESTRICT`:
   удаление пользователя с заданиями отклоняется БД (`false` в ответе), данные не теряются.
2. **BR-044/045/535/537**: применить миграции `54`, `55`, `56` на LOCAL и PROD в одном окне
   (формула md5 `tbl_site_users` меняется у всех строк; рассинхрон окна даст полный diff).
   Применение — только после бэкапа (prerequisite пакета PKG-S2-08) и словом владельца.
3. **BR-042**: политика хранения тегов (сколько предыдущих держать в registry, нужен ли
   автоматический откат) — сейчас один предыдущий тег в `.version.prev`.
4. **BR-547/BR-560**: значения `mem_limit`/`cpus` подобраны из `-Xmx` (3 ГБ для app при
   `-Xmx2g`, 1.6 ГБ для web при `-Xmx1200m`); подтвердить на реальной нагрузке.
5. **BR-549**: HSTS сознательно не дублируется в контейнере (TLS терминирует host-nginx и
   отдаёт HSTS для всего хоста); если нужен HSTS и в SPA-конфиге — это отдельное решение,
   guard считает HSTS в не-TLS `server` ошибкой.
6. **BR-550**: при росте лимита загрузки продукта (>200 МБ) синхронно менять
   `spring.servlet.multipart`, `nginx.conf` (`/api/`) и `nginx_karaoke-public.conf`.
7. **BR-548**: обновление модели TTS — осознанная замена константы `MODEL_SHA256`/`MODEL_SIZE_BYTES`
   после сверки источника.
8. **BR-047**: пин указывает на registry-зеркало (`sm-karaoke.ru:5443`); машина без доступа
   к registry не сможет `docker compose pull` для storage — это ожидаемо и задокументировано.

## Риски и отклонения

- **BR-536** — правок не потребовалось: `album_id` и поздние колонки уже в
  `Song.getSqlToInsert` (пакет PKG-S2-10, BR-108). Запись закрыта регрессионной проверкой.
- **BR-549** — host-часть записи была закрыта раньше (`#372`, `tools/check-security-headers.py`);
  в этом пакете закрыт SPA-контейнер, guard расширен на его конфиг.
- Миграции `54`/`55`/`56` **не применялись** (запрет на БД/прод). Они идемпотентны и
  предназначены для ручного прогона на LOCAL и PROD.
- Изменения конфигурации прода (compose/nginx/do.sh) — только в репозитории; ни один
  контейнер не пересоздавался и не перезапускался.

## Коммиты

| Хэш | Что |
|---|---|
| `a27a6838` | BR-045/535/537/538 — SQL-миграции и новые `54`, `55` |
| `e62d89cb` | BR-044 — канонический recordhash `tbl_site_users`, `56`, guard check 5 |
| `135325fd` | BR-042/046/047/547/559/560 — тег образа, restore_db, MinIO, лимиты, new_comp |
| `bb7f8797` | BR-548/549/550 — sha256 TTS, заголовки SPA-nginx, лимит 200m |
| (этот коммит) | guard'ы (13 правил + расширения), feature-док, отчёт |
