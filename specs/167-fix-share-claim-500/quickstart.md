# Quickstart: Починить 500 на `POST /api/public/share/claim`

**Дата**: 2026-08-11
**Spec**: [./spec.md](./spec.md) | **Plan**: [./plan.md](./plan.md) | **Research**: [./research.md](./research.md) | **Data Model**: [./data-model.md](./data-model.md) | **API**: [./contracts/api.md](./contracts/api.md)

## Цель

Этот документ — **runbook для проверки** hotfix-релиза. После применения миграций 38/39 и деплоя karaoke-web с фиксом `tryClaim` + `claim`/`create`/`heartbeat`, пройдите 7 сценариев ниже. Каждый проверяет конкретное требование из спеки (`spec.md:FR-001..FR-032`, `SC-001..SC-011`).

## Предусловия

1. **Доступ к прод-серверу** через ssh (только пользователь, см. Constitution «Ограничения агента», п. 2).
2. **Доступ к прод-БД** через `docker exec`:
   ```bash
   ssh root@188.119.64.111
   docker exec -it karaoke-db psql -U postgres -d karaoke
   ```
3. **Cookie/JWT премиум-пользователя** для сценариев создания ссылки. Получить через браузер (DevTools → Application → Cookies → `kp_token_*`) или через `curl` с логином.
4. **Локально собранный karaoke-web.jar** с фиксом:
   ```bash
   ./gradlew :karaoke-web:bootJar
   ls -la karaoke-web/build/libs/karaoke-web-*.jar
   ```
5. **Ожидаемое состояние ДО фикса**: на прод-БД **НЕТ** таблиц `tbl_song_share_links` / `tbl_song_share_sessions`, claim возвращает 500 share.notFound.

## Сценарий 0 — Pre-deploy verification (ДО миграции и деплоя)

Проверяет baseline — что симптом действительно воспроизводится.

```bash
# 0.1. Проверить, что таблиц действительно нет
docker exec karaoke-db psql -U postgres -d karaoke -c "\dt tbl_song_share*"
# Ожидаемо: 0 строк (или только tbl_song_share_links без tbl_song_share_sessions)
```

```bash
# 0.2. Создать тестовую ссылку через UI (или curl с premium-cookie)
# Сохранить secret из ответа

# 0.3. Попытаться сделать claim анонимно
curl -X POST https://sm-karaoke.ru/api/public/share/claim \
  -H "Content-Type: application/json" \
  -d '{"secret":"<SECRET>","browserHash":"<sha256 hex>"}' \
  -w "\nHTTP %{http_code}\n"
# Ожидаемо (ДО фикса):
#   HTTP 500
#   {"errorCode":"share.notFound"}
```

```bash
# 0.4. Проверить, что на проде стоит «старая» версия karaoke-web
ssh root@188.119.64.111 "docker inspect karaoke-web --format '{{.Image}}'"
# Ожидаемо: svoemesto/karaoke-web:<timestamp до фикса>
```

**Результат**: ✅ baseline подтверждён → переходим к Сценарию 1.

## Сценарий 1 — Применение миграции 38 (DDL таблиц)

Только пользователь. Пошагово:

```bash
# 1.1. Скопировать файл миграции на прод
scp deploy/karaoke-db/38_song_share_links.sql root@188.119.64.111:/tmp/

# 1.2. Применить
ssh root@188.119.64.111 "docker exec -i karaoke-db psql -U postgres -d karaoke < /tmp/38_song_share_links.sql"
# Ожидаемо: без ошибок (миграция идемпотентна)
```

```bash
# 1.3. Проверить, что таблицы созданы
ssh root@188.119.64.111 "docker exec karaoke-db psql -U postgres -d karaoke -c '\dt tbl_song_share*'"
# Ожидаемо:
#                  List of relations
#   Schema |         Name          | Type  |  Owner
#  --------+-----------------------+-------+----------
#   public | tbl_song_share_links  | table | postgres
#   public | tbl_song_share_sessions | table | postgres
# (2 rows)
```

```bash
# 1.4. Проверить схему tbl_song_share_links
ssh root@188.119.64.111 "docker exec karaoke-db psql -U postgres -d karaoke -c '\d tbl_song_share_links'"
# Ожидаемо: 18 колонок согласно deploy/karaoke-db/38_song_share_links.sql:32-54
```

```bash
# 1.5. Проверить, что IDENTITY + PRIMARY KEY на месте
ssh root@188.119.64.111 "docker exec karaoke-db psql -U postgres -d karaoke -c \"SELECT a.attname, a.attidentity FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid WHERE c.relname='tbl_song_share_links' AND a.attname='id'\""
# Ожидаемо: attname=id, attidentity=a (GENERATED ALWAYS AS IDENTITY)
```

**Результат**: ✅ обе таблицы созданы, IDENTITY/PK на месте → переходим к Сценарию 2.

## Сценарий 2 — Применение миграции 39 (триггеры)

```bash
# 2.1. Скопировать и применить
scp deploy/karaoke-db/39_song_share_recordhash.sql root@188.119.64.111:/tmp/
ssh root@188.119.64.111 "docker exec -i karaoke-db psql -U postgres -d karaoke < /tmp/39_song_share_recordhash.sql"
# Ожидаемо: без ошибок
```

```bash
# 2.2. Проверить функции
ssh root@188.119.64.111 "docker exec karaoke-db psql -U postgres -d karaoke -c '\df update_tbl_song_share*'"
# Ожидаемо:
#                               List of functions
#    Schema |                  Name                   | Result data type | Argument data types | Type
#   --------+-----------------------------------------+------------------+---------------------+------
#    public | update_tbl_song_share_links_recordhash    | trigger          |                     | func
#    public | update_tbl_song_share_sessions_recordhash | trigger          |                     | func
# (2 rows)
```

```bash
# 2.3. Проверить триггеры
ssh root@188.119.64.111 "docker exec karaoke-db psql -U postgres -d karaoke -c \"SELECT tgname FROM pg_trigger WHERE tgname LIKE '%song_share%'\""
# Ожидаемо: 4 строки
#   update_recordhash_song_share_links_trigger
#   update_last_updated_song_share_links_trigger
#   update_recordhash_song_share_sessions_trigger
#   update_last_updated_song_share_sessions_trigger
```

**Результат**: ✅ триггеры созданы → можно делать деплой кода.

## Сценарий 3 — Деплой нового karaoke-web

Только пользователь. Стандартный `deploy_web.sh`:

```bash
# 3.1. Собрать локально
./gradlew clean :karaoke-web:bootJar --parallel

# 3.2. Деплой
cd deploy && bash do.sh build_start_web

# 3.3. Проверить, что контейнер перезапустился
ssh root@188.119.64.111 "docker inspect karaoke-web --format '{{.Image}}'"
# Ожидаемо: svoemesto/karaoke-web:<timestamp после фикса>

# 3.4. Убедиться, что логи не содержат ERROR при старте
ssh root@188.119.64.111 "docker logs --tail 100 karaoke-web 2>&1 | grep -i error | head -5"
# Ожидаемо: пусто (или только известные WARN)
```

**Результат**: ✅ новый код в проде → переходим к Сценарию 4 (smoke-test claim).

## Сценарий 4 — Smoke-test claim (главный сценарий, US1#1)

Создать новую ссылку через UI/curl и проверить, что claim возвращает 200 OK с `sessionTokenHash`.

```bash
# 4.1. Создать ссылку через UI как премиум-пользователь
#   Открыть https://sm-karaoke.ru/song?id=<ID любой песни со статусом ≥6>
#   Нажать «Временный доступ» → выбрать TTL=1 час → нажать «Создать»
#   Скопировать URL из модалки (формат: https://svoemesto.ru/share/<songId>/<secret>)
#   Из URL извлечь SECRET

# Или через curl (нужен premium-cookie):
curl -X POST "https://sm-karaoke.ru/api/public/share/<songId>/create?ttlSeconds=3600" \
  -b "kp_token=<PREMIUM_TOKEN>" \
  -w "\nHTTP %{http_code}\n"
# Ожидаемо: 200 OK, {"linkId":...,"secret":"...","url":"...","expiresAt":...,"ttlSeconds":3600}
```

```bash
# 4.2. Сгенерировать browserHash (SHA-256 от любого UUID)
BROWSER_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
BROWSER_HASH=$(echo -n "browser:$BROWSER_ID" | sha256sum | awk '{print $1}')
echo "browserHash=$BROWSER_HASH"
# Ожидаемо: 64 hex-символа
```

```bash
# 4.3. Сделать claim анонимно
curl -X POST https://sm-karaoke.ru/api/public/share/claim \
  -H "Content-Type: application/json" \
  -d "{\"secret\":\"<SECRET>\",\"browserHash\":\"$BROWSER_HASH\"}" \
  -w "\nHTTP %{http_code}\n"
# Ожидаемо (ПОСЛЕ фикса):
#   HTTP 200
#   {"linkId":...,"songId":...,"sessionTokenHash":"<64 hex>","expiresAt":...,
#    "redirectTo":"/player/<songId>?share=1&session=<64 hex>", ...}
```

```bash
# 4.4. Проверить, что в БД появилась сессия
ssh root@188.119.64.111 "docker exec karaoke-db psql -U postgres -d karaoke -c \"SELECT id, share_link_id, song_id, browser_hash, finished_at, result FROM tbl_song_share_sessions ORDER BY id DESC LIMIT 1\""
# Ожидаемо: одна строка, finished_at IS NULL, result=''

# 4.5. Проверить, что в tbl_song_share_links обновились active_session_*
ssh root@188.119.64.111 "docker exec karaoke-db psql -U postgres -d karaoke -c \"SELECT id, sessions_total, active_session_browser_hash, active_session_lease_until > now() AS lease_active FROM tbl_song_share_links WHERE song_id=<songId> ORDER BY id DESC LIMIT 1\""
# Ожидаемо: sessions_total=1, lease_active=t
```

**Результат**: ✅ claim работает end-to-end. Переходим к Сценарию 5.

## Сценарий 5 — Smoke-test create (US1 + FR-014)

Проверяет, что `create` эндпоинт тоже работает (а не падает с маскировкой).

```bash
# 5.1. Создать ссылку через curl (анонимно, ожидаем 401)
curl -X POST "https://sm-karaoke.ru/api/public/share/<songId>/create?ttlSeconds=3600" \
  -w "\nHTTP %{http_code}\n"
# Ожидаемо: 401 + {"errorCode":"share.tokenMissing"}
```

```bash
# 5.2. Создать ссылку через curl с premium-cookie
curl -X POST "https://sm-karaoke.ru/api/public/share/<songId>/create?ttlSeconds=3600" \
  -b "kp_token=<PREMIUM_TOKEN>" \
  -w "\nHTTP %{http_code}\n"
# Ожидаемо: 200 OK + {"linkId":...,"secret":"...","url":"...",...}
```

```bash
# 5.3. Создать ссылку с невалидным TTL (ожидаем 400)
curl -X POST "https://sm-karaoke.ru/api/public/share/<songId>/create?ttlSeconds=99999" \
  -b "kp_token=<PREMIUM_TOKEN>" \
  -w "\nHTTP %{http_code}\n"
# Ожидаемо: 400 + {"errorCode":"share.tokenMissing"}
```

**Результат**: ✅ `create` ведёт себя корректно для всех ожидаемых случаев.

## Сценарий 6 — Проверка `/debug` для системных ошибок (US3, FR-020)

Проверяет, что `debug` показывает **реальные классы исключений**, а не маскирует.

### 6.1. Диагностика валидной ссылки (ожидаем OK на всех шагах)

```bash
curl -X POST https://sm-karaoke.ru/api/public/share/debug \
  -H "Content-Type: application/json" \
  -d "{\"secret\":\"<SECRET из Сценария 4>\"}" \
  -w "\nHTTP %{http_code}\n"
# Ожидаемо: 200 OK + JSON со step1_resolve="OK linkId=...", step2_ownerId="OK ownerId=...", и т.д.
```

### 6.2. Диагностика несуществующего секрета (ожидаем 404, а не 500)

```bash
curl -X POST https://sm-karaoke.ru/api/public/share/debug \
  -H "Content-Type: application/json" \
  -d '{"secret":"nonexistent_secret_abc123"}' \
  -w "\nHTTP %{http_code}\n"
# Ожидаемо: 200 OK + JSON с {"step1_resolve":"OK linkId=null"} или 
#           {"error_step1":"class=...NotFound...","step1_resolve":"FAILED: ..."}
#           — НЕ {"step1_resolve":"FAILED: NotFound"}  с пустым message.
```

### 6.3. Локальный тест: смоделировать системную ошибку

На локальной машине (не проде) — DROP таблицы и проверить, что claim возвращает **500 share.internal**, а не 500 share.notFound:

```bash
# Только на LOCAL, не на проде!
docker exec karaoke-db psql -U postgres -d karaoke_local -c "DROP TABLE IF EXISTS tbl_song_share_sessions; DROP TABLE IF EXISTS tbl_song_share_links"
# 6.3.1. Попытаться claim
curl -X POST http://localhost:8897/api/public/share/claim \
  -H "Content-Type: application/json" \
  -d "{\"secret\":\"<SECRET>\",\"browserHash\":\"$BROWSER_HASH\"}" \
  -w "\nHTTP %{http_code}\n"
# Ожидаемо (ПОСЛЕ фикса):
#   HTTP 500
#   {"errorCode":"share.internal"}

# 6.3.2. Восстановить
docker exec -i karaoke-db psql -U postgres -d karaoke_local < deploy/karaoke-db/38_song_share_links.sql
docker exec -i karaoke-db psql -U postgres -d karaoke_local < deploy/karaoke-db/39_song_share_recordhash.sql
```

**Результат**: ✅ системная ошибка корректно классифицируется как `share.internal`.

## Сценарий 7 — Проверка логов (SC-002, SC-003)

Проверяет, что в логах `karaoke-web` видны реальные исключения, а не маскировка.

```bash
# 7.1. Воспроизвести системную ошибку (см. 6.3) и сразу проверить логи
ssh root@188.119.64.111 "docker logs --tail 200 karaoke-web 2>&1 | grep -i 'UNEXPECTED\\|ShareLink tryClaim'"
# Ожидаемо: одна или несколько строк вида
#   ERROR ... ShareLink tryClaim UNEXPECTED class=org.postgresql.util.PSQLException msg=ERROR: relation "tbl_song_share_links" does not exist
#   + полный стек-трейс (несколько строк через \tat ...)
```

```bash
# 7.2. Проверить, что НЕТ записей "share.notFound" в логах для системных ошибок
ssh root@188.119.64.111 "docker logs --tail 1000 karaoke-web 2>&1 | grep -E 'class=NotFound msg=null' | head -5"
# Ожидаемо: пусто (или только legitimate NotFound для revoked links)
```

**Результат**: ✅ логи показывают реальные исключения.

## Сценарий 8 — Regression checks (US2#3, US2#4)

Проверяет, что **ожидаемые** доменные ошибки НЕ изменили поведение.

### 8.1. Claim с неверным секретом → 404 (а не 500)

```bash
curl -X POST https://sm-karaoke.ru/api/public/share/claim \
  -H "Content-Type: application/json" \
  -d '{"secret":"wrong_secret","browserHash":"deadbeef00000000000000000000000000000000000000000000000000000000"}' \
  -w "\nHTTP %{http_code}\n"
# Ожидаемо: HTTP 404 + {"errorCode":"share.notFound"}
```

### 8.2. Claim с пустым secret → 400

```bash
curl -X POST https://sm-karaoke.ru/api/public/share/claim \
  -H "Content-Type: application/json" \
  -d '{"secret":"","browserHash":"deadbeef00000000000000000000000000000000000000000000000000000000"}' \
  -w "\nHTTP %{http_code}\n"
# Ожидаемо: HTTP 400 + {"errorCode":"share.tokenMissing"}
```

### 8.3. Claim с пустым browserHash → 400

```bash
curl -X POST https://sm-karaoke.ru/api/public/share/claim \
  -H "Content-Type: application/json" \
  -d '{"secret":"abc","browserHash":""}' \
  -w "\nHTTP %{http_code}\n"
# Ожидаемо: HTTP 400 + {"errorCode":"share.tokenMissing"}
```

### 8.4. Heartbeat с несуществующим sessionTokenHash → 410 (а не 500)

```bash
curl -X POST https://sm-karaoke.ru/api/public/share/heartbeat \
  -H "Content-Type: application/json" \
  -d '{"sessionTokenHash":"0000000000000000000000000000000000000000000000000000000000000000"}' \
  -w "\nHTTP %{http_code}\n"
# Ожидаемо: HTTP 410 + {"errorCode":"share.leaseExpired"}
```

### 8.5. Параллельно 2 разных browserHash → один получит 409 concurrentLimit

```bash
BROWSER_HASH_2=$(echo -n "browser:$(uuidgen | tr '[:upper:]' '[:lower:]')" | sha256sum | awk '{print $1}')
curl -X POST https://sm-karaoke.ru/api/public/share/claim \
  -H "Content-Type: application/json" \
  -d "{\"secret\":\"<SECRET>\",\"browserHash\":\"$BROWSER_HASH_2\"}" \
  -w "\nHTTP %{http_code}\n"
# Ожидаемо (если уже 2 активных lease): HTTP 409 + {"errorCode":"share.concurrentLimit"}
```

**Результат**: ✅ все ожидаемые доменные ошибки ведут себя по-прежнему.

## Сводка прохождения

| Сценарий | FR / SC | Что проверяет | Должен пройти |
|---|---|---|---|
| 0 | baseline | Воспроизводимость симптома | до фикса |
| 1 | FR-001, FR-003, SC-005 | Применение миграции 38 | ✅ |
| 2 | FR-002, FR-003 | Применение миграции 39 | ✅ |
| 3 | FR-010..FR-014 | Деплой нового кода | ✅ |
| 4 | FR-011..FR-013, SC-001 | Claim работает end-to-end | ✅ |
| 5 | FR-014 | Create работает | ✅ |
| 6 | FR-020, SC-004 | /debug показывает реальные классы | ✅ |
| 7 | SC-002, SC-003 | Логи содержат реальные исключения | ✅ |
| 8 | US2#3, US2#4 | Регрессии нет | ✅ |

Если **любой** сценарий падает — **откат**:

1. `git revert` коммита с фиксом в master (через PR).
2. Пересобрать `karaoke-web.jar`.
3. `bash do.sh build_start_web`.
4. **НЕ откатывать** миграцию 38/39 на проде — таблицы безвредны, если код не пишет в них (catch-all маскирует обратно).

После прохождения всех 8 сценариев — закрыть задачу, обновить:
- `docs/features/guest-share-link.md` (секция «Инварианты / правила» — добавить «Диагностика 500»).
- `docs/architecture-notes.md` (запись о PR, Pass 50+).
- `AGENTS.md` Q&A «500 на `/api/public/share/claim`» (обновить ссылку на `/debug`).

## Что НЕ покрыто этим quickstart

- **Heartbeat/release/sweeper/admin** — backlog spec 164, не в этом hotfix.
- **Таймзоны** — backlog spec 166.
- **Защита `/debug` за `X-Share-Debug-Key`** — backlog spec 164.
- **Production-like нагрузка (100+ claim в минуту)** — не тестируется (нет staging).
- **Мобильные браузеры** — claim работает в Chrome/Firefox/Safari, тестировалось вручную при разработке спеки 164.

## Когда НЕ применять этот hotfix

- Если на проде **уже есть** активные ссылки с реальными пользователями (тогда DROP+remigrate потеряет данные). В текущей ситуации (Pass 47, 2026-08-10) ссылок нет, потому что таблиц не было — данные не теряются.
- Если в проекте есть открытые PR, которые меняют те же файлы (`SongShareLinkService.kt`, `PublicShareController.kt`, `ShareErrorCode.kt`). Координировать merge через rebasing.
