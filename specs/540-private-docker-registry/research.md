# Research: технические развилки плана «Свой Docker-registry на sm-karaoke.ru»

**Дата**: 2026-10-11 | **Спека**: [spec.md](./spec.md) | **План**: [plan.md](./plan.md)

> **Назначение.** Спека не содержит `NEEDS CLARIFICATION` (чек-лист
> `checklists/requirements.md` пройден после ответов владельца) — все требования
> закрыты. Этот файл закрывает то, чего в спеке нет по определению: **технические
> развилки плана** — как именно требования реализуются и почему выбранный путь
> безопаснее альтернатив. Формат — «Решение / Обоснование / Отклонённые
> альтернативы», как требует `/speckit.plan`.
>
> **Границы метода.** Ничего не разворачивалось и не менялось: чтение репозитория,
> чтение артефактов разведки (`.scratch/karaoke/registry/`), чтение исходников Zot
> v2.1.22 (архив `v2.1.22.tar.gz`, распакован в `/tmp/zot-src`) и документации
> zotregistry.dev. Ни одного изменения на прод-хосте.

---

## Входные артефакты (проверенные, а не переизобретённые)

| Артефакт | Что из него взято |
|---|---|
| [`.scratch/karaoke/registry/research-registry-software.md`](../../.scratch/karaoke/registry/research-registry-software.md) (sha256 `01bb1442…`) | инвентарь образов с замерами, сравнение кандидатов, черновик конфига, 12 открытых вопросов |
| [`.scratch/karaoke/registry/stand/RESULTS.md`](../../.scratch/karaoke/registry/stand/RESULTS.md) | RSS Zot 49.9→114.3 МиБ, сохранение upstream-пути, ловушка `onDemand` без `content`, матрица авторизации |
| [`.scratch/karaoke/registry/stand/config.json`](../../.scratch/karaoke/registry/stand/config.json) | рабочий конфиг Zot (с `content: [{"prefix": "library/**"}]`) |
| [`.scratch/karaoke/registry/bodies/map-v7.md`](../../.scratch/karaoke/registry/bodies/map-v7.md) | финальное тело карты #274: все решения владельца |
| [`.scratch/karaoke/registry/research-cert-renewal.md`](../../.scratch/karaoke/registry/research-cert-renewal.md) (sha256 `1da03317…`) | сертификат: кто продлевает, что будет при просрочке, схема мониторинга `notAfter` |
| [`specs/165-migration-one-server-research/tls-certificate.md`](../165-migration-one-server-research/tls-certificate.md) | риск № 6 (напоминатель/мониторинг) — открыт с 2026-09-23 |

---

## R-1. Транспорт: TLS на host-nginx, Zot на loopback

**Решение.** `nginx` получает новый `server { listen 5443 ssl; server_name
sm-karaoke.ru; }`, терминирует TLS существующим сертификатом
`/etc/keys/www.sm-karaoke.ru.{crt,key}` и проксирует на `http://127.0.0.1:5000`.
Контейнер Zot публикует порт только на loopback: `"127.0.0.1:5000:5000"`.

**Обоснование.**
- Первоисточник distribution («Authenticate proxy with nginx») прямо требует
  «move TLS termination from the Registry to the proxy itself» и даёт ручки
  `client_max_body_size 0`, `chunked_transfer_encoding on`,
  `proxy_set_header Host $http_host`.
- Тот же приём уже применён в этом проекте к MinIO: контейнер слушает
  `127.0.0.1:8890`, наружу отдаёт host-nginx.
- Порт 5443 уже проверен живым слушателем снаружи (#284): firewall на проде
  отсутствует, провайдер высокие порты не фильтрует — открыть порт означает
  «заставить nginx слушать», правок firewall/панели не нужно.
- Существующий сертификат валиден для `sm-karaoke.ru` (`openssl x509 -checkhost` →
  match), а порт на проверку имени и цепочки не влияет.

**Отклонённые альтернативы.**
- *Поддомен `repository.sm-karaoke.ru` + certbot* — отклонён владельцем (#285):
  поддомен и новый сертификат не выпускаем. Черновик разведки §2/§4 написан
  именно под эту схему и в части транспорта не применяется.
- *`insecure-registries` / правка `daemon.json` на клиентах* — «You cannot use
  authentication with authentication schemes that send credentials as clear
  text» (distribution), то есть несовместимо с FR-002/FR-009. Плюс это остановка
  Docker на проде (FR-007/FR-012).
- *Zot со своим TLS-сертификатом* — дублирует терминацию, сертификат пришлось бы
  копировать в контейнер и обновлять в двух местах.

**Проверить в реализации (не снято фактом).** `Location` при blob-upload. В Zot
v2.1.22 `getBlobUploadSessionLocation(request.URL, sessionID)` возвращает
`url.String()` от request-URL (`pkg/api/routes.go:3199`), а `http.ExternalURL` из
конфига используется только для OIDC-redirect'ов и cookie-origin
(`pkg/api/authn.go:728`, `pkg/api/config/config.go:445`) — на `Location` он не
влияет. За nginx request-URL приходит в origin-form (без scheme/host), значит
`Location` будет **относительным** и docker разрешит его относительно
`https://sm-karaoke.ru:5443`. Вывод получен чтением кода; живой push через 5443 —
обязательный шаг приёмки (`quickstart.md`, сценарий Q2).

---

## R-2. Схема имён: три namespace, и почему `library/**` только для чтения

**Решение.** Грамматика ссылки:

```
<registry-host>:<port>/<namespace>/<repo-path>[:<tag>][@sha256:<digest>]
   sm-karaoke.ru:5443  /  karaoke | mirror | library
```

- `karaoke/**` — образы, которые собираем мы (`karaoke-app`, `karaoke-web`,
  `karaoke-public`, `karaoke-webvue3`, `demucs`).
- `mirror/<upstream-path>` — сторонние образы, перенесённые к нам, **с сохранением
  исходного пути**: `mirror/searxng/searxng`, `mirror/luuul/4get`,
  `mirror/ollama/ollama`, `mirror/hwdsl2/whisper-server`, `mirror/xtls/xray-core`,
  `mirror/minio/minio`, `mirror/library/postgres`, `mirror/nvidia/cuda`.
- `library/**` — **только** pull-through из `docker.io`; мы туда не пушим никогда.

**Обоснование.**
- FR-018 требует «служебный namespace с сохранением исходного пути» — `mirror/`
  плюс upstream-путь это требование выполняет буквально: `mirror/luuul/4get`
  читается как «4get от luuul», проект-владелец угадывать не нужно.
- Namespace = проект (решение #278): `karaoke/**` — граница проекта Караоке, один
  комплект кред на проект, отзыв = удаление строки в `htpasswd`.
- **Инвариант I-2 (новый, из чтения кода).** `library/**` — это ровно тот префикс,
  который стоит в `extensions.sync.registries[].content` (обязательный allow-list
  из ловушки #275). `docker push` перед заливкой манифеста делает `HEAD`, и Zot
  трактует промах по allow-list как «сходить наверх». Для нашего приватного образа
  upstream отвечает `unauthorized` → 503 (измерено). Для `library/postgres` upstream
  ответил бы `200` — то есть push в `library/**` попадает в другой, **непроверенный**
  режим (частично sync-управляемый репозиторий). Ставить прод на непроверенное
  поведение нельзя; поэтому библиотечные образы, которые мы вендорим, кладутся в
  `mirror/library/**`, а `library/**` остаётся read-only зоной pull-through.

**Отклонённые альтернативы.**
- *Вендорить в `library/**` (совпадает с upstream-путём «как есть»)* — конфликт с
  allow-list sync, поведение push непредсказуемо (см. выше).
- *Раскладывать сторонние образы по namespace'ам проектов-потребителей* —
  отклонено владельцем (#276): требует решать, чей образ, и дублировать его при
  втором потребителе.
- *Плоский `mirror/<image>` без upstream-пути* — теряется происхождение, а
  `library/*` и `luuul/*` могут содержать одноимённые репозитории.

---

## R-3. Конфиг Zot: база — стендовый, изменения минимальны

**Решение.** За основу берётся `stand/config.json` (принят Zot v2.1.22 без правок).
Изменения относительно стенда: `http.realm` (`karaoke-registry`),
`log.level` (`info` вместо `debug`), добавление retention-политики для `mirror/**`,
один upstream (блоки `ghcr.io`/`quay.io` из черновика разведки **удалены**).

**Обоснование.**
- Стендовый конфиг — единственный проверенный факт: `retention`, `gcDelay`,
  `gcInterval`, `syncTimeout`, `failDelay`, `compat` валидны в v2.1.22.
- `http.compat: ["docker2s2"]` обязателен: «Without this compatibility setting, zot
  rejects Docker schema 2 content» (Zot docs, mirroring).
- `content: [{"prefix": "library/**"}]` обязателен (ловушка `onDemand`, FR-006).
- Один upstream — решение владельца (#276): каждый upstream есть дверь наружу,
  открываемая по запросу владельца валидного пароля.
- UI и search не включаем (решение #277): без конфига роуты не поднимаются, что
  подтверждено логами стенда; `hashKey` не задаём — предупреждение Zot безвредно.

**Отклонённые альтернативы.**
- *Черновик разведки §2 целиком* — содержит три upstream и написан под
  поддомен+certbot; обе части отменены решениями владельца.
- *`distribution` `registry:3` (план Б)* — не нужен: RSS Zot измерен и влезает
  (49.9 МиБ покой, 114.3 МиБ пик). У `registry:3` нет retention, а GC
  stop-the-world — на одном хосте с продом это недоступность pull'ов.

---

## R-4. Retention: правила внутри политики — ИЛИ, tag-retention — только там, где есть `keepTags`

**Решение.** Политики (`storage.retention.policies`, порядок важен — «first policy
it matches»):

1. `karaoke/**` — `deleteUntagged: true`, `keepTags: [{ mostRecentlyPushedCount: 10,
   pushedWithin: "2160h" }]`.
2. `mirror/**` — `deleteUntagged: true`, **без** `keepTags` (пины не удаляются
   автоматически).
3. `**` — дефолтная политика документации: `deleteUntagged: true`,
   `keepUntagged: { pulledWithin: "720h" }`.

**Обоснование (из исходников Zot v2.1.22).**
- Правила внутри одной политики комбинируются по **ИЛИ**: «we retain candidates if
  any of the below rules are met (OR logic between rules)»
  (`pkg/retention/retention.go:219`). Значит тег выживает, если он среди 10 последних
  push **или** пушился за 90 дней — прод-тег (всегда самый свежий, `BUILD_VERSION=1`)
  под удаление не попадает.
- Tag-retention применяется только к репозиториям, у которых сработавшая политика
  содержит `keepTags`: `if !gc.policyMgr.HasTagRetention(repo) { … }`
  (`pkg/storage/gc/gc.go:786`). `mirror/**` попадает на политику без `keepTags` →
  теги вендоренных образов авто-удалению не подлежат (инвариант I-4).
- GC у Zot inline — «it is not necessary to take the registry offline»
  (Zot docs, admin-configuration), в отличие от `registry garbage-collect`
  у distribution («ensure that the registry is in read-only mode or not running»).

**Отклонённые альтернативы.**
- *Один `keepTags` на все репозитории* — тогда сторонние пины могли бы быть удалены
  как «старые» (они пушатся один раз и больше не обновляются).
  Проверять — `zot verify-feature retention` в dry-run (`dryRun: true` на время
  проверки, `false` в бою).
- *Чистить своим cron-скриптом* — внешний скрипт не знает, на что ссылается прод;
  ошибка = недоступный образ.

**Ограничение проверки (находка при чтении исходников).** `verify-feature retention`
обращается к метаданным хранилища, и предупреждение команды гласит: «the zot server
must be stopped before running this command» (`pkg/cli/server/verify_retention.go:31-33`),
плюс требуется `gc: true`. Значит на **живом проде** эту проверку делать нельзя — она
означала бы остановку registry. Политика проверяется на стенде (контейнер остановлен,
`dryRun: true`, каталог данных примонтирован); на проде корректность подтверждается
логами GC. Это уточнение попадает в `RUNBOOK.md`.

**Открытое следствие.** Прод-compose ссылается на тег `:1` (совпадает с
`BUILD_VERSION` из `build.gradle.kts: version = "1"` и `deploy/.version`), и он
перепушивается каждым деплоем — то есть всегда «самый свежий». Если владелец сменит
схему версионирования на неуникальные теги, инвариант I-4 надо перепроверять.

---

## R-5. Прод получает базовые образы вендорингом; сборка — из registry

**Решение.**
- Прод-compose: `postgres:16` → `sm-karaoke.ru:5443/mirror/library/postgres:<пин>`,
  `quay.io/minio/minio:…` → `sm-karaoke.ru:5443/mirror/minio/minio:…<пин>`.
- Боевые `Dockerfile`: `FROM <registry>/library/{openjdk,ubuntu,eclipse-temurin,node,nginx}:<тег>`
  (pull-through), `DockerfileDemucs`: `FROM <registry>/mirror/nvidia/cuda:<пин>`.
- `registry-mirrors` и `/etc/docker/daemon.json` не используются нигде.

**Обоснование.**
- FR-007 запрещает прода обращаться к внешним реестрам и запрещает требовать
  перезапуск обслуживающего контейнеры компонента. Документация Docker: «Save the
  file and reload Docker for the change to take effect» — то есть `daemon.json` =
  остановка всех четырёх прод-контейнеров (Pass 380-adjacent риск). На проде
  `daemon.json` вообще отсутствует.
- FR-017 требует, чтобы сборка получала базовые образы из нашего registry.
  `FROM <registry>/library/node:22-alpine` делает это без `daemon.json`: имя образа
  само указывает хост, docker идёт в наш registry, Zot при промахе идёт в docker.io
  по allow-list `library/**` и кэширует. Для сборки это не нарушает FR-008: кэш
  наполняется по запросу, но `FROM` фиксирует тег (`node:22-alpine`, а не `latest`).
- CI docker-образы не собирает (`.github/workflows/lint.yml` — только guard), значит
  секретов registry в GitHub Actions не потребуется.

**Отклонённые альтернативы.**
- *`registry-mirrors` на проде* — отклонено владельцем (#276): перезапуск Docker.
- *`registry-mirrors` на admin* — на admin работают чужие проекты (top, syp, ivfx),
  перезапуск Docker задел бы их (отклонено владельцем в «Решённых уточнениях» спеки).
- *Оставить пин `postgres:16` тегом* — тег плавающий, FR-008 требует фиксации;
  пин делается по дайджесту.

---

## R-6. Guard R-04/R-05: нормализация имени + постоянный self-test

**Решение.** `tools/check-docker-image-tags.sh` учится срезать registry-хост и порт
из `FROM`-строки и сопоставлять **последний компонент пути + тег** с whitelist'ом;
добавляется режим `--selftest` с фиксированными положительными и отрицательными
примерами.

**Обоснование (факт с диска).** Сегодня guard сопоставляет `^FROM[[:space:]]+(nginx|node):`
(`tools/check-docker-image-tags.sh:128`) и на текущем репозитории находит 4 строки в
5 файлах (проверено запуском). После R-5 строки станут
`FROM sm-karaoke.ru:5443/library/node:22-alpine AS build-stage` — шаблон не совпадёт,
`checked` станет 0, guard выведет «No 'FROM nginx:' or 'FROM node:' found (R-04/R-05
N/A)» и **вернёт 0**. То есть правило R-04/R-05 перестанет работать, оставаясь
зелёным. Это ровно то, что описано в FR-014/SC-009.

**Отклонённые альтернативы.**
- *Захардкодить в guard префикс `sm-karaoke.ru:5443`* — guard ослепнет снова при смене
  хоста/порта (а спека допускает переезд).
- *Проверять «есть ли вообще хоть одна FROM-строка с nginx/node»* — не различает
  «нет таких образов» и «мы перестали их видеть».
- *Полагаться на ручное ревью* — SC-009 требует намеренной поломки, то есть
  воспроизводимой проверки, а не обещания.

**Форма проверки.** `--selftest` прогоняет матчер на встроенном наборе строк
(`nginx:stable` → ок; `sm-karaoke.ru:5443/library/nginx:alpine` → нарушение;
`mirror/library/nginx:latest` → нарушение) и падает, если матчер сломан. Плюс
SC-009 проверяется временной подстановкой запрещённого тега в реальный Dockerfile
(см. `quickstart.md`, сценарий Q4).

---

## R-7. `do.sh`: раздельный логин, логин на pull, dual-push

**Решение.** В `deploy/do.sh` и `deploy/prod-single-host/do.sh` строка
`${DOCKER} login --username ${DOCKER_REGISTRY} --password ${DOCKER_PASSWORD}` (6 раз
в `deploy/do.sh`, 2 раза в прод-`do.sh`) заменяется на логин с явным сервером и
паролем через stdin:

```bash
${DOCKER} login "${REGISTRY_HOST}" -u "${REGISTRY_USER}" --password-stdin <<<"${REGISTRY_PASSWORD}"
```

`do_pull_app`/`do_pull_web`/`do_pull_webvue3` получают логин (сейчас не логинятся
вовсе). Пока `DUAL_PUSH=1`, `do_push_*` дополнительно тегирует и пушит в Hub
(`DOCKER_HUB_USER`/`DOCKER_HUB_PASSWORD`, совместимо со старым `DOCKER_PASSWORD`).

**Обоснование.**
- Сегодня `--username ${DOCKER_REGISTRY}` = `svoemestodev` **без SERVER** — логин
  уходит в Docker Hub, а не туда, куда потом пушится. Для нашего registry это
  гарантированная ошибка (дефект №1 карты).
- `--password` в argv виден в `ps`; документация Docker описывает `[SERVER]` и
  предпочтительный ввод пароля через stdin/credential store.
- Значение `DOCKER_REGISTRY` обязано содержать точку и порт, иначе docker не
  распознает первый компонент как хост registry (дефект №3 карты).
- FR-015 требует сохранить возможность вернуться к прежнему источнику в течение
  окна; `DUAL_PUSH` — самый дешёвый переключатель, не требующий пересборки образов
  (второй тег ставится `docker tag`).

**Отклонённые альтернативы.**
- *Собирать образы сразу с двумя именами (`-t` дважды)* — плодит второй тег на каждой
  сборке и захламляет `docker images` на admin; тег ставится только на push.
- *Отдельный скрипт `do_push_hub.sh`* — раздваивает точку входа деплоя, а `deploy_web.sh`
  вызывает именно `do.sh push_web`.
- *Креденшел-стор (helpers)* — на проде в неинтерактивной сессии лишняя зависимость;
  `do.env` (вне git) уже принят проектом как SSoT секретов.

---

## R-8. Напоминание о сертификате: скрипт + таймер на admin

**Решение.** `tools/check-cert-expiry.sh` (проверяет `notAfter` для
`sm-karaoke.ru:443` и `sm-karaoke.ru:<порт registry>`, порог по умолчанию 45 дней,
exit 1 при превышении) + systemd-таймер на admin-машине, использующий существующий
`announce` (TTS + `notify-send`). Процедура установки — в `RUNBOOK.md`.

**Обоснование.**
- FR-016: срок должен быть известен владельцу не позднее чем за 30 дней; порог 45
  даёт запас, что важно после падения лимита срока публичных сертификатов с 398 до
  200 дней (следующий лист проживёт ~200 дней).
- Канала уведомлений на проде **нет** — проверено: в `deploy/prod-single-host/*.sh`
  единственное упоминание уведомлений — `notify-send` в интерактивной ветке
  `do.sh:103`; `prune-images.sh` и `karaoke-db-backup.sh` просто пишут в stdout.
  Таймер на проде, пишущий в journal, формально «мониторит», но не делает дату
  известной владельцу.
- На admin канал `announce` (Silero TTS + `notify-send`) используется каждым
  деплоем — он проверен практикой.
- Скрипт проверяет **оба** порта: у registry свой путь трафика, и ошибка в
  `ssl_certificate` нового server-блока не проявится на 443.

**Отклонённые альтернативы.**
- *Prometheus blackbox_exporter* — на хосте 3.8 ГБ RAM, стека нет; тащить его ради
  одной метрики нерационально (зафиксировано в research-cert-renewal).
- *Таймер на проде* — без нового канала уведомлений не закрывает SC-007.
- *Интеграция в health-report приложения* — потребовала бы правок Kotlin
  (риск № 6 спеки 165 предлагал и такой путь); это шире FR-016. Отклонено на ревью
  плана 2026-10-11: FR-016 требует результата, а не новой подсистемы.
- *certbot / автопродление* — отклонено владельцем (#285).

---

## R-9. Восстановление вместо бэкапа

**Решение.** Registry не бэкапится (решение #281). Вместо бэкапа — `RUNBOOK.md` с
процедурой: (1) пересоздать контейнер из `docker-compose-registry.yml` +
`config.json` + `htpasswd`; (2) восстановить свои образы `do.sh push_*` из текущего
чекаута admin; (3) восстановить сторонние по `registry/images.lock` через
`tools/registry-vendor.sh`; (4) принять `tools/registry-smoke.sh`. Процедура
**репетируется** на admin (одноразовый каталог данных), а не только описывается.

**Обоснование.** В registry нет данных, которых нет в другом месте: свои образы
воспроизводимы из git сборкой, сторонние публично доступны у upstream. FR-011
требует «документированную процедуру, проверенную на практике» — отсюда репетиция,
а SC-005 задаёт целевое время ≤ 1 рабочего дня (восстановление — это в основном
время на перезаливку ≈11.8 ГБ измеренных шести сторонних образов; объём `minio`
и `nvidia/cuda` не измерен).

**Отклонённые альтернативы.**
- *Rsync-бэкап каталога данных* — 985 ГБ диска позволяют, но это копия того, что и
  так воспроизводимо; решение владельца — не бэкапить.
- *Отметка риска «во время окна dual-push копия есть в Hub»* — верно только для
  окна; после его закрытия единственная копия — registry. Это записано в RUNBOOK.

---

## R-10. Что план **не** делает (границы)

- Не меняет спеку: FR/SC — контракт, правки только словом владельца.
- Не трогает прод-хост на этапе планирования (SSH read-only).
- Не трогает `deploy/web-server-deploy/` (deprecated) и `Dockerfile{Backup,Backup2,Old,Full}`
  (исключены guard'ом и не участвуют в сборке).
- Не переписывает ADR `local-0003-shared-minio-image-cache.md` (там «образ» = медиа);
  терминологическая развязка — в спеке и в плане.
- Не решает вопрос «нужен ли RBAC с самообслуживанием» и «когда переезжать на
  отдельный хост» — это «Not yet specified» карты #274.

---

## Остаётся непроверенным

Полный список — в [plan.md](./plan.md) § «Остаётся непроверенным (перенесено в
`implement`)». Развилки этого документа закрыты; проверки, которые нельзя выполнить
чтением:

1. **Push 5.4 ГБ именно через nginx-прокси на 5443** (не напрямую в Zot, как на
   стенде): относительный `Location`, отсутствие буферизации, таймауты. Вывод о
   `Location` получен чтением исходников, живой проверки нет.
2. **`zot verify-feature retention` в dry-run на реальном наборе тегов** (стенд,
   контейнер остановлен) — подтвердить инвариант I-4 наблюдением, а не чтением кода.
3. **Точные версии/дайджесты** восьми сторонних образов — снимаются `docker pull` при
   вендоринге. Утверждение владельцем не требуется: значения видны в диффе
   `registry/images.lock` на гейте ревью (решение ревью 2026-10-11, п. 3).

Решения по вопросам, которые этот документ поднимал, зафиксированы в
[plan.md](./plan.md) § «Решения, зафиксированные на ревью (2026-10-11)»: MinIO и
`nvidia/cuda` вендорятся (восемь образов), admin-таймер для сертификата утверждён,
per-feature документ заводится.
