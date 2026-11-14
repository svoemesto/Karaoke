# RUNBOOK: эксплуатация приватного registry

> **Терминология**: «образ» здесь = **контейнерный образ**.
> **Хост**: `sm-karaoke.ru` (`188.127.240.124`), канон — `/root/Karaoke/deploy/`.
> **Спека**: `specs/540-private-docker-registry/` (FR-004, FR-010, FR-011, FR-016).

Разделы: [1. Доступ](#1-доступ-выдача-и-отзыв) · [2. Восстановление](#2-восстановление-содержимого)
· [3. Сертификат](#3-срок-tls-сертификата)

---

## 1. Доступ: выдача и отзыв

**Модель.** Namespace = проект. Один комплект учётных данных на проект; права —
строкой в `htpasswd` плюс политика проекта в `config.json`. Выдача и отзыв — **одна
процедура** (FR-004), укладывается в **одну минуту** (SC-004), registry при этом не
останавливается. Анонимного доступа нет ни на одну операцию (FR-002).

**Выдать доступ (одна процедура: две правки, обе — без перезапуска, суммарно ≤ 1 мин).** Изоляция по namespace
(FR-003) обеспечивается **политиками** в `config.json`, а не только строкой в
`htpasswd`: без политик любой валидный кред видит **все** репозитории (проверено на
стенде 2026-10-11 — второй пользователь читал чужой namespace и получал 200).

1. Строка в `htpasswd`. bcrypt генерируется **только на admin-машине** (на проде
   Ubuntu 26.04 несёт Python ≥ 3.13, где модуль `crypt` удалён — PEP 594):

   ```bash
   cd deploy/prod-single-host/registry
   python3 -W ignore -c "import crypt; print('ПРОЕКТ:'+crypt.crypt('СЕКРЕТ', crypt.mksalt(crypt.METHOD_BLOWFISH)))" >> htpasswd
   chmod 600 htpasswd
   scp htpasswd root@sm-karaoke.ru:/root/Karaoke/deploy/registry/htpasswd
   ```

2. Политика в `config.json` — свой namespace проекта:

   ```json
   "ПРОЕКТ/**": { "policies": [ { "users": ["ПРОЕКТ"],
                                 "actions": ["read","create","update","delete"] } ] }
   ```

   `scp config.json root@sm-karaoke.ru:/root/Karaoke/deploy/registry/config.json`

**Отозвать доступ (одна процедура: одна правка).** Удалить строку проекта — на admin и на проде:

```bash
sed -i '/^ПРОЕКТ:/d' deploy/prod-single-host/registry/htpasswd
ssh root@sm-karaoke.ru "sed -i '/^ПРОЕКТ:/d' /root/Karaoke/deploy/registry/htpasswd"
```

Политику можно оставить (пользователя больше нет — она ни на что не влияет) или
удалить для чистоты.

**Перезапуск не нужен.** Zot перечитывает и `htpasswd`, и `config.json` на лету —
проверено на стенде: правка обоих файлов дала `403` на чужой namespace и `404`/`200`
на свой **без перезапуска контейнера** (в логе — `config file changed, trying to
reload config` и `loaded new configuration settings`).

**ЛОВУШКА (проверено 2026-10-11, стоила бы тихой потери контроля доступа).**
Hot-reload работает, **только если смонтирован каталог**. При монтировании файлов
по отдельности (`- ./registry/htpasswd:/etc/zot/htpasswd:ro`) bind-mount привязан к
inode: `sed -i`/`scp` заменяют файл, а контейнер продолжает видеть старый — удалённый
пользователь получал `200`, Zot писал `loaded htpasswd file users: 2` при одном
пользователе в файле. Поэтому в `docker-compose-registry.yml` монтируется
`./registry:/etc/zot:ro`.

**Проверка после каждой выдачи/отзыва** (обязательна — «удалить строку» без проверки
ничего не доказывает):

```bash
bash tools/registry-smoke.sh sm-karaoke.ru:5443     # аноним 401, свои креды 200, чужой namespace 403
```

**Внешний временный пользователь.** Получает **свой** namespace и **свой** комплект
учётных данных (отдельная строка в `htpasswd`). При снятии доступа удаляется строка —
ни действующих кред, ни доступа к содержимому не остаётся. Пока он не поднял свой
registry, его образы живут в namespace `mirror/**` (общий служебный).

**Секреты.** `htpasswd` и `do.env` в git не попадают. Проверка:
`git ls-files | grep -iE 'htpasswd$|\.env$|do\.env$'` → пусто.

---

## 2. Восстановление содержимого

Registry **не бэкапится** (решение владельца #281): свои образы воспроизводимы сборкой,
сторонние публично доступны у upstream. Вместо копии — эта процедура; она **проверена
репетицией** на admin-машине (см. `quickstart.md`, сценарий Q8).

**Когда применять.** Потеря данных `/sm-karaoke/system/registry`, потеря контейнера,
переезд на другой хост.

**Шаги.**

1. Восстановить контейнер и конфигурацию:

   ```bash
   mkdir -p /sm-karaoke/system/registry
   cd /root/Karaoke/deploy
   docker compose -f docker-compose-registry.yml up -d
   # при переезде: scp -r deploy/prod-single-host/* root@<хост>:/root/Karaoke/deploy/
   ```

2. Восстановить **свои** образы — пересобрать и запушить с admin-машины:

   ```bash
   cd ~/Karaoke/deploy
   bash do.sh build_app && bash do.sh push_app
   bash do.sh build_web && bash do.sh push_web
   bash do.sh build_public && bash do.sh push_public
   ```

3. Восстановить **сторонние** образы по карте пинов:

   ```bash
   bash tools/registry-vendor.sh           # пройдёт по images.lock и зальёт всё заново
   bash tools/registry-vendor.sh --verify  # сверка дайджестов с images.lock
   ```

4. Принять результат:

   ```bash
   bash tools/registry-smoke.sh sm-karaoke.ru:5443
   curl -s -u ПРОЕКТ https://sm-karaoke.ru:5443/v2/_catalog | python3 -m json.tool
   ```

**Целевое время** — не более одного рабочего дня (SC-005); практически это время на
перезаливку сторонних образов (измеренные шесть — ≈11.8 ГБ, объём `minio` и
`nvidia/cuda` не измерен).

**Риск, который надо держать в голове.** Пока идёт окно dual-push (до двух успешных
прод-деплоев, не дольше месяца), свежая копия наших образов есть ещё и в Docker Hub
(namespace `svoemestodev`, архив). **После закрытия окна единственная копия — в этом
registry.**

**MinIO перестал быть воспроизводимым из upstream (проверено 2026-10-11).**
`quay.io/minio/minio` отдаёт `401 UNAUTHORIZED` на **любой** тег (`latest`,
`RELEASE.2025-09-07T16-13-09Z`), при том что остальной quay.io работает; в Docker Hub
репозитория `minio/minio` тоже нет (`pull access denied`). Образ, который сейчас
работает на проде, удалось перенести в наш registry **из локальной копии на
admin-машине** (тот же релиз: `Created 2025-09-07T18:42:37Z`) — `images.lock` это
фиксирует.

Следствие для восстановления: если registry потерян и копии MinIO нет ни на admin, ни
в локальном кэше прода — **взять его будет негде**. Поэтому:

- не удалять локальный образ `minio/minio…` на admin и на проде без нужды
  (`docker image prune -a` не трогает образы, на которые ссылается контейнер, но
  остановленный контейнер — трогает);
- при плановом обновлении MinIO сначала убедиться, что новый тег вообще доступен
  из какого-либо источника, и только потом переключать прод.

Это единственное исключение из посылки «содержимое registry воспроизводимо из
upstream»; остальные семь сторонних образов доступны у upstream.

**Регламентные проверки.** Копий нет — значит нет и «восстановления из копии»; есть
только описанный выше путь. Периодически стоит убеждаться, что `images.lock` заполнен
(`PENDING` в колонке `digest` — признак незавершённого переноса).

---

## 2а. Если образ не поднялся (авария деплоя)

**Симптом.** Сайт отдаёт 502; в `docker ps` нет `karaoke-public` (или `karaoke-web`);
в выводе деплоя — `failed to resolve reference "sm-karaoke.ru:5443/karaoke/…"`.

**Что произошло.** Деплой снёс контейнер и не смог подтянуть образ. Так было
2026-10-11: сборка упала (`npm install` → `ETIMEDOUT`), но `do.sh` вернул 0,
деплой пошёл дальше, `pull` не нашёл образ, а контейнер к тому моменту уже был
удалён.

**Порядок действий — сначала поднять сервис, потом чинить причину.**

```bash
# 1. Убедиться, что образ в registry действительно отсутствует (дешёвая проверка).
source /root/Karaoke/deploy/do.env
curl -sSI -u "$REGISTRY_USER:$REGISTRY_PASSWORD" \
  -H 'Accept: application/vnd.docker.distribution.manifest.v2+json' \
  "https://$REGISTRY_HOST/v2/karaoke/karaoke-public/manifests/1" | head -1
# 404 → образа нет, это и есть причина

# 2. Найти прежний рабочий образ в локальном кэше прода.
docker images --format '{{.Repository}}:{{.Tag}} {{.ID}}' | grep karaoke-public
# обычно это архивная копия из Docker Hub, например svoemestodev/karaoke-public:1

# 3. Поставить ему имя, которое ждёт compose, и поднять контейнер ШТАТНО.
docker tag svoemestodev/karaoke-public:1 "$DOCKER_REGISTRY/karaoke-public:1"
cd /root/Karaoke/deploy && bash do.sh start_public
curl -sS -o /dev/null -w '%{http_code}\n' https://sm-karaoke.ru/     # ждём 200

# 4. Только теперь — причина, а не следствие: пересобрать и запушить образ,
#    затем штатный pull (он сам проверит, что образ есть, и лишь потом
#    тронет контейнер).
cd <admin> && bash deploy/deploy_public.sh
```

**Важно помнить.**

- Локальный тег из шага 3 — **подпорка**, а не решение: источника правды в
  registry для этого тега по-прежнему нет. Пока шаг 4 не выполнен, следующее
  пересоздание контейнера снова упрётся в отсутствующий образ.
- `restart: always` поднимает контейнер из **локального** образа, поэтому
  подпорка переживает перезагрузку — но не `docker compose up -d`.
- С версии от 2026-10-11 `do.sh` устроен так, что эта авария больше не
  воспроизводится: `pull_web`/`pull_public` сначала качают образ и проверяют
  результат, и только потом останавливают контейнер, а `do_start_*` отказывается
  работать, если образа нет локально. Падающая сборка теперь возвращает
  ненулевой код и останавливает деплой. Проверка —
  `bash tools/check-do-sh-guards.sh --prod`.

## 2б. Сбои операционки, найденные на деплое 2026-11-14

> Три симптома, каждый из которых останавливал деплой. Проверено на проде
> 2026-11-14 (выкладка `karaoke-web` и `karaoke-public`).
>
> **Исправлено 2026-11-15 (решение владельца).** Причина § 2б.1 устранена: базовые
> образы вендорены в `mirror/library/**`, `FROM` переключены, on-demand sync
> отключён. § 2б.2 и § 2б.3 остаются в силе как обходы операционки.

### 2б.1. `library/**` — pull-through зона: синк долгий, GC вычищает результат

**Причина (полный разбор).** Зона не «зависала» — она была **опустошена**:

1. `config.json` включает sync-on-demand с `"syncTimeout": "2h"`,
   `"maxRetries": 3`, `"retryDelay": "5m"` и префиксом `library/**`.
   Синк с Docker Hub идёт **15–17 минут** (три ретрая по 5 минут), а HTTP-запрос
   клиента всё это время **висит открытым** — docker-клиент сдаётся раньше.
2. `storage.gc: true`, `gcDelay: "2h"`, `gcInterval: "24h"`, а retention-политика
   `{"repositories": ["**"], "deleteUntagged": true}` действует **и на `library/**`**:
   GC вычищает нетегированные манифесты и блобы, которые ещё не привязаны к тегу.
   Цикл GC по репозиториям идёт ~40 минут — и **гарантированно наезжает** на синк.
3. Метаданные `.sync/` в репозиториях пусты → Zot считает образ
   «не синхронизированным» и **пересинхронизирует при каждом запросе**, даже если
   манифест и тег лежат локально.

**Хронология (логи Zot, 2026-11-14).**

| Время | Событие |
|---|---|
| 14:45:40 | GC по `library/alpine`: «skipping missing image manifest blob» |
| 14:50:29 | «sync: syncing image» — старт скачивания из Docker Hub |
| 15:06:46 | «pushing synced local image to local registry» — через **16 мин 17 с** |
| 15:06:48 | 4× «skipping missing manifest blob in image index» |
| 15:07:05 | «successfully synced image» (индекс ссылается на отсутствующие манифесты) |
| 15:25:45 | GC по `library/eclipse-temurin`: «pruned stale manifest entry from index» |

После этого `library/eclipse-temurin` — **32 КБ** вместо ~90 МБ, `index.json` =
`{"manifests":[]}`. Проверено на всех пяти репозиториях зоны: `library/node`,
`library/nginx`, `library/alpine`, `library/ubuntu`, `library/eclipse-temurin` —
доступных базовых образов **ноль**.

**Сеть до Docker Hub при этом в порядке** (проверено с прод-хоста):
`https://registry-1.docker.io/v2/` → 401 за 1.16 с, блоб тянется за 0.57 с.

**Исправление (2026-11-15).**

1. Пять базовых образов вендорены в `mirror/library/**`
   (`tools/registry-vendor.sh --update-lock`, пины — в `images.lock`):
   `eclipse-temurin:22-jre-jammy`, `node:22-alpine`, `nginx:stable`,
   `ubuntu:22.04`, `openjdk:17-ea-jdk-slim`.
2. `FROM` в `deploy/karaoke-{web,app,public,webvue3}/Dockerfile` переключены с
   `…/library/…` на `…/mirror/library/…`.
3. В `config.json` выставлено `"onDemand": false` — зона `library/**` больше не
   наполняется и не чистится; запрос к ней отдаёт **404 сразу**, а не висит
   15 минут. Конфиг Zot перечитывается на лету (fsnotify) — **рестарт контейнера
   `karaoke-registry` не нужен**.

Инвариант I-2 («в `library/**` не пушим никогда») не изменился.

**Обход на время разбора (без правки репозитория).** Базовый образ тянется напрямую
с Docker Hub и тегуется под имя из репозитория — buildkit берёт образ из локального
хранилища и registry не опрашивает:

```bash
docker pull eclipse-temurin:22-jre-jammy
docker tag eclipse-temurin:22-jre-jammy sm-karaoke.ru:5443/library/eclipse-temurin:22-jre-jammy
```

В логе сборки видно, что взят **тот же digest**, что синкает Zot.


**Корень проблемы — не наш, но чинить надо у себя:** upstream-синк Zot виснет на
втором Docker Hub с этого IP. Варианты: (а) вендорить базовые образы в
`mirror/**` через `tools/registry-vendor.sh` и переключить `FROM` в
`deploy/karaoke-{web,app,public}/Dockerfile`; (б) отключить sync-on-demand для
`library/**` и держать базовые образы только вендоренными.

### 2б.2. Реестр пишет медленно — docker-клиент отваливается, запись всё равно проходит

PUT манифеста в Zot отвечает `201` через **41–45 с**. Docker-клиент ждёт 30 с и
падает с `timeout awaiting response headers`, **хотя запись на сервере состоялась**:
манифест лежит по своему digest, но **тег остался на старом**. Проверено:
`docker push` → ошибка, а `HEAD /v2/<repo>/manifests/<digest>` → 200.

**Обход.** Проставить тег вручную (curl не имеет клиентского таймаута):

```bash
D=sha256:<digest манифеста образа>
curl -s -u "$REGISTRY_USER:$REGISTRY_PASSWORD" \
     -H 'Accept: application/vnd.oci.image.manifest.v1+json,application/vnd.oci.image.index.v1+json' \
     -o manifest.json "https://sm-karaoke.ru:5443/v2/<repo>/manifests/$D"
curl -s -u "$REGISTRY_USER:$REGISTRY_PASSWORD" -X PUT \
     -H 'Content-Type: application/vnd.oci.image.manifest.v1+json' \
     --data-binary @manifest.json \
     "https://sm-karaoke.ru:5443/v2/<repo>/manifests/<тег>"
```

Проверка после: `HEAD` тега должен вернуть **новый** `Docker-Content-Digest`.

### 2б.3. С прод-хоста registry доступен по `127.0.0.1:5000`, а не по имени

На проде `sm-karaoke.ru` резолвится в собственный публичный IP (`188.127.240.124`),
и HTTPS к 5443 **оттуда виснет** (curl без ответа), тогда как
`http://127.0.0.1:5000/v2/` отвечает за 0.002 с. Поэтому `do.sh pull_web`/`pull_public`
на проде падают на «логин в registry не удался — контейнер НЕ трогаем».

**Рабочий путь на проде:**

```bash
printf '%s' "$REGISTRY_PASSWORD" | docker login 127.0.0.1:5000 -u "$REGISTRY_USER" --password-stdin
docker pull 127.0.0.1:5000/karaoke/karaoke-web:1
docker tag 127.0.0.1:5000/karaoke/karaoke-web:1 sm-karaoke.ru:5443/karaoke/karaoke-web:1
bash do.sh start_web   # compose поднимает уже локальный образ с правильным именем
```

### 2б.4. `sync_prod_config` требует `PROD_HOST` в окружении

`deploy/sync_prod_config.sh` берёт хост из `PROD_HOST`; без него `scp`/`ssh` уходят
с пустым хостом («Could not resolve hostname»), а проверка валидности конфига
проваливается и скрипт откатывает «замену». Штатные `deploy_web.sh`/`deploy_public.sh`
задают `PROD_HOST` из `deploy/.env` **до** sourcing. При ручном вызове:

```bash
export PROD_HOST="$(sed -n 's/^PROD_HOST=//p' deploy/.env | tail -1 | tr -d ' \r')"
```

---

## 3. Срок TLS-сертификата

**Зависимость.** Доступность registry держится на сертификате домена
`sm-karaoke.ru` (в registry он используется на порту 5443). Продление — **вручную
владельцем** (решение #285, certbot и поддомен отклонены).

**Ключевые даты.**

| Дата | Что |
|---|---|
| 2027-04-03 13:34 МСК | `notAfter` текущего листа (`2027-04-03T10:34:27Z`). С этой секунды **деплой встанет** |
| ~2027-03-04 | открывается окно ручного продления у регистратора (за 30 дней) |

**Что будет при просрочке.** nginx **продолжит работать** (он не сравнивает `NotAfter`),
сайт отдаст просроченный лист и клиенты начнут рвать handshake. Критично для нас
другое: Docker написан на Go и упадёт с `x509: certificate has expired or is not yet
valid` — то есть **встанут и push, и pull, то есть деплой**. Обход через
`--insecure-registry` мы сознательно не используем. Работающие контейнеры при этом
не падают — падает только пересоздание.

**Напоминание (FR-016, SC-007).** Автоматики на проде нет; напоминание делает скрипт
`tools/check-cert-expiry.sh`, который проверяет `notAfter` **на двух портах** —
`:443` (сайт) и `:5443` (registry): у registry свой путь трафика, и ошибка в
`ssl_certificate` нового блока на 443 не видна.

```bash
# разовая проверка
bash tools/check-cert-expiry.sh sm-karaoke.ru:443 sm-karaoke.ru:5443
# намеренная проверка канала (заведомо высокий порог → должен сработать алерт)
bash tools/check-cert-expiry.sh sm-karaoke.ru:443 sm-karaoke.ru:5443 --warn-days 400
```

Порог по умолчанию — **45 суток** (запас к требуемым 30: после 2026-03-15 срок
публичных TLS-сертификатов упал с 398 до 200 дней, следующий лист проживёт вдвое меньше).

**Установка суточного таймера на admin-машине** (нужен sudo, делает владелец):
юнит-файлы — `deploy/prod-single-host/karaoke-cert-expiry.{service,timer}`, канал
уведомления — существующий `announce` (TTS + `notify-send`), тот же, что используют
все деплои. На проде канала уведомлений нет, поэтому таймер ставится на admin.

**Замена сертификата — ручная операция:** `scp` новых файлов в `/etc/keys/` →
`nginx -t && systemctl reload nginx` → проверить `openssl s_client -connect sm-karaoke.ru:5443`
и `notAfter`.

---

## Изменения

| Дата | Что |
|---|---|
| 2026-11-15 | § 2б.1 переписан: полная причина (медленный sync + storage-GC + пустые `.sync`) и исправление — вендоринг пяти базовых образов в `mirror/library/**`, переключение `FROM`, `"onDemand": false` |
| 2026-11-14 | Раздел «2б. Сбои операционки»: pull-through `library/**` с зависающим синком, медленные PUT манифеста (docker-клиент отваливается по таймауту, запись проходит), доступ к registry с прод-хоста по `127.0.0.1:5000`, требование `PROD_HOST` для `sync_prod_config` |
| 2026-10-11 | Создан каркас (T007) |
