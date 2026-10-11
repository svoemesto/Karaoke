# Data Model: приватный контейнерный registry

**Спека**: [spec.md](./spec.md) | **План**: [plan.md](./plan.md) | **Дата**: 2026-10-11

> Терминология: «образ» здесь = **контейнерный образ** (spec.md § Key Entities).
> Медиа-изображения — из другого ADR, к этой модели не относятся.

Модель описывает **не код, а инфраструктурные сущности**: registry как хранилище,
пространства имён как границы доступа, креды как единицу выдачи/отзыва, пины как
фиксацию версии, политики как правило роста, окно dual-push как состояние перехода.
Каждая сущность привязана к требованиям спеки (FR) и к проверяемому артефакту в
репозитории — «где это лежит на диске» и «чем проверяется».

---

## E1. Контейнерный образ (Container Image)

Единица хранения и доставки. Адресуется ссылкой по грамматике из
[`contracts/image-naming.md`](./contracts/image-naming.md); полный реестр конкретных
имён (старое → новое) — там же.

| Поле | Тип | Описание | Источник |
|---|---|---|---|
| `ref` | строка | полная ссылка `sm-karaoke.ru:5443/<namespace>/<path>:<tag>[@sha256:<digest>]` | FR-001, FR-018 |
| `origin` | `own` \| `vendored` \| `pull-through` | кто произвёл образ | FR-017, FR-018 |
| `namespace` | строка | `karaoke` \| `mirror` \| `library` (первый компонент пути) | FR-003 |
| `pinned` | bool | зафиксирована ли версия (для `origin != own` обязателен `true`) | FR-008 |
| `consumer` | `prod` \| `admin` \| `both` | кто забирает образ | FR-007 |

**Правила валидации.**
1. `origin = own` ⟹ `namespace = karaoke` и `tag = BUILD_VERSION` (никаких `latest`).
2. `origin ∈ {vendored, pull-through}` ⟹ `namespace ∈ {mirror, library}` и
   `pinned = true`; для `vendored` — тег дайджест-фиксирован.
3. `namespace = library` ⟹ `origin = pull-through` **всегда**: ни один образ,
   созданный нами, не может иметь `namespace = library` (инвариант I-2 плана).
4. `consumer ∋ prod` ⟹ `origin = vendored` (прод не ходит ни в pull-through, ни во
   внешний реестр) — FR-007.

**Где на диске.** Ссылки на образы проекта — `deploy/do.sh` (теги сборки),
`deploy/docker-compose*.yml` и `deploy/prod-single-host/docker-compose-*.yml`
(`image:`); сторонние — `deploy/prod-single-host/registry/images.lock`.

**Чем проверяется.** `tools/registry-smoke.sh` (каталог и теги), `bash
tools/check-docker-image-tags.sh` (теги `nginx`/`node`), `grep -rn 'image:'` (нет
внешних имён в путях прода и сборки).

---

## E2. Namespace

Раздел registry, который одновременно является **границей разграничения доступа**
(FR-003) и **признаком происхождения** образа (FR-018).

| Namespace | Назначение | Кто пишет | Кто читает | Политика |
|---|---|---|---|---|
| `karaoke/**` | образы проекта Караоке | admin (`do.sh push_*`) | admin, прод | retention: keep 10 / 90 дней |
| `mirror/**` | сторонние образы, перенесённые к нам, с сохранением исходного пути | admin (`tools/registry-vendor.sh`) | admin, прод | tag-retention выключен (пины неприкосновенны) |
| `library/**` | кэш pull-through из `docker.io` | **только Zot** (sync) | admin (сборка) | default-политика `**` |

**Правила валидации.**
1. `library/**` — read-only для людей и скриптов (инвариант I-2).
2. Namespace проекта (`karaoke`) не может быть прочитан-изменён держателем кред
   другого проекта: пара «namespace + креды» — единица изоляции (FR-003).
3. Внешний временный пользователь получает **свой** namespace и **свои** креды;
   при снятии доступа не остаётся ни кред, ни содержимого (US2-3).

**Чем проверяется.** `GET /v2/_catalog` с кредами каждого проекта; попытка обратиться
к чужому namespace тем же клиентом → 401/403 (US2-1).

---

## E3. Учётные данные проекта (Project Credential)

Пара «имя/секрет», которой проект обращается к registry.

| Поле | Тип | Описание |
|---|---|---|
| `user` | строка | имя в `htpasswd` (напр. `karaoke`) |
| `secret_hash` | строка | bcrypt-хеш `$2b$…` (Zot поддерживает только bcrypt) |
| `namespaces` | список | какие namespace доступны (в текущей модели — по соглашению об именах, RBAC отложен) |
| `state` | `active` \| `revoked` | наличие/отсутствие строки в `htpasswd` |

**Жизненный цикл.** `выдать` = добавить строку в `htpasswd`; `отозвать` = удалить
строку. Zot перечитывает файл на лету → `state: revoked` наступает **без остановки
registry** (FR-004, SC-004). Пароль хранится только в `do.env` (вне git, VIII.1).

**Правила валидации.**
1. Хеш обязан быть bcrypt (`$2b$`, cost ≥ 10); `htpasswd`-формат `user:hash`.
2. Файл `htpasswd` не коммитится: в репо только `htpasswd.example`, в `.gitignore`
   — путь реального файла; проверка `git ls-files | grep -i htpasswd` → пусто.
3. Генерация — **только на admin**: `python3 -W ignore -c "import crypt;
   print('karaoke:'+crypt.crypt('…', crypt.mksalt(crypt.METHOD_BLOWFISH)))"`.
   На проде (Ubuntu 26.04, Python ≥ 3.13) модуль `crypt` удалён — команда там не
   сработает (находка плана, § R-6 research).

**Чем проверяется.** Матрица авторизации в `registry-smoke.sh`: без кредов → 401,
неверный пароль → 401, верный → 200; после удаления строки — снова 401 (без
перезапуска контейнера).

---

## E4. Пин (Version Pin)

Фиксация версии стороннего образа (FR-008). Хранится в
`deploy/prod-single-host/registry/images.lock`.

| Поле | Пример | Комментарий |
|---|---|---|
| `upstream_ref` | `docker.io/searxng/searxng:latest` | что было |
| `our_ref` | `sm-karaoke.ru:5443/mirror/searxng/searxng:latest@sha256:…` | что стало |
| `digest` | `sha256:…` | манифест-дайджест, снятый при вендоринге |
| `checked_at` | `2026-10-11` | когда проверено |

**Правила валидации.**
1. Плавающий тег (`latest`, `cuda` без версии, `16` без минорной) допустим **только**
   вместе с `@sha256:<digest>`; иначе `pinned = false` и запись невалидна.
2. Изменение пина — только новой строкой с новой датой (запись не переписывается
   молча: старый дайджест остаётся, пока на него ссылается образ у потребителя).
3. Для образов с `consumer ∋ prod` пин обязателен **и** в compose-файле прода.

**Чем проверяется.** `tools/registry-vendor.sh --verify` — сверяет локальный дайджест,
дайджест в нашем registry и запись в `images.lock`; расхождение → exit 1.

---

## E5. Политика хранения (Retention Policy)

Правило, ограничивающее рост хранилища (FR-010). Живёт в
`deploy/prod-single-host/registry/config.json`, `storage.retention.policies[]`.

| Политика | `repositories` | tag-правило | untagged-правило |
|---|---|---|---|
| P1 | `karaoke/**` | keep 10 последних push **или** push за 2160 ч (90 дней) | удалять |
| P2 | `mirror/**` | нет (теги неприкосновенны) | удалять |
| P3 (default) | `**` | нет | keepUntagged: pulledWithin 720 ч (30 дней) |

**Семантика (подтверждена исходниками Zot v2.1.22).**
- Правила внутри политики комбинируются по **ИЛИ** — `pkg/retention/retention.go:219`.
- Tag-retention применяется только там, где в сработавшей политике есть `keepTags` —
  `pkg/storage/gc/gc.go:786`. Для P2 `keepTags` нет ⟹ пины не удаляются (I-4).
- Порядок политик значим: «first policy it matches».

**Правила валидации.**
1. Ни один тег, на который ссылается действующий прод-compose, не должен попадать
   под удаление (I-4). Прод тянет тег `1`, который перепушивается каждым деплоем →
   всегда самый свежий.
2. GC — inline, остановка registry не требуется (отличие от distribution).
3. Перед включением боевого режима — прогон `zot verify-feature retention` в
   `dryRun: true`.

**Чем проверяется.** Dry-run retention (`dryRun: true`) **на стенде** + наблюдение:
заведомо старый тег исчезает, тег прод-образа остаётся. На живом проде
`zot verify-feature retention` запускать **нельзя**: команда обращается к метаданным
хранилища, и документация Zot требует «the zot server must be stopped before running
this command» — то есть остановки registry. На проде политика контролируется по
логам GC, а её корректность доказывается на стенде.

---

## E6. Переходное окно dual-push (Transition Window)

Состояние перехода источников образов. Отвечает FR-015 и SC-008.

```
S0 Hub-only ──(registry развёрнут)──> S1 Registry-ready ──(деплой переключён)──> S2 Dual-push
                                                                                    │
                                                        (2 успешных деплоя ИЛИ 30 дней)
                                                                                    v
                                                                              S3 Registry-only
                                                                                    │
                                                        (откат: DUAL_PUSH=1) <───────┘
```

| Состояние | `DOCKER_REGISTRY` | `DUAL_PUSH` | Пуш идёт в | Прод тянет из |
|---|---|---|---|---|
| S0 | `svoemestodev` | — (нет) | Hub | Hub |
| S1 | `sm-karaoke.ru:5443/karaoke` | `1` | Hub + registry | registry |
| S2 | `sm-karaoke.ru:5443/karaoke` | `1` | Hub + registry | registry |
| S3 | `sm-karaoke.ru:5443/karaoke` | `0` | registry | registry |

**Правила перехода.**
1. `S1 → S2` — по факту первого успешного прод-деплоя; счётчик ведётся по числу
   **успешных развёртываний прода** (не по числу push).
2. `S2 → S3` — по достижении 2 успешных деплоев или по истечении 30 дней, что
   наступит раньше. Ветка `S2 → S0` (полный откат) остаётся доступной до `S3`.
3. Namespace `svoemestodev` **не удаляется** ни в одном состоянии: это архив отката
   (решение #280).
4. `S3` необратим «в одну сторону» только по счётчику: вернуться в `S2` можно в любой
   момент (`DUAL_PUSH=1`), пока Hub-архив существует.

**Чем проверяется.** `docker inspect <container>` показывает наш registry в имени
образа (SC-001); логи Zot — ноль обращений к `docker.io` за образами проекта
(SC-008); `Status: Downloaded newer image` при pull с нашего registry.

---

## E7. Хранилище (Storage)

| Поле | Значение |
|---|---|
| `root` | `/sm-karaoke/system/registry` (bind-mount в контейнер как `/var/lib/registry`) |
| `owner` | root (как у остальных каталогов `/sm-karaoke/system/`) |
| `backup` | **нет** (решение #281); вместо него — процедура восстановления |
| `cleanup` | только retention Zot (P1–P3); `karaoke-docker-prune` до этих данных не достаёт |
| `growth_rule` | FR-010: старые версии удаляются retention'ом автоматически |

**Правила валидации.**
1. Каталог вне docker-конфайнмента ⟹ `docker image prune` и `karaoke-docker-prune.timer`
   его не трогают; единственный механизм роста — retention.
2. Объём не является ограничением (985 ГБ), ограничение — RAM (3.8 ГБ).
3. Восстановление — из `images.lock` + `do.sh push_*`, целевое время ≤ 1 рабочего дня
   (SC-005).

**Чем проверяется.** `du -sh /sm-karaoke/system/registry`; `df -h /`;
`docker stats --no-stream karaoke-registry` (RSS в бюджете: 49.9 МиБ покой, пик
114.3 МиБ на push 5.4 ГБ).

---

## E8. Напоминание о сертификате (Certificate Reminder)

| Поле | Значение |
|---|---|
| `endpoint` | `sm-karaoke.ru:443` **и** `sm-karaoke.ru:5443` (у registry свой путь трафика) |
| `notAfter` | `2027-04-03T10:34:27Z` = 13:34 МСК |
| `threshold` | 45 суток (запас к 30 суткам SC-007) |
| `mechanism` | `tools/check-cert-expiry.sh` + таймер на admin с `announce` |
| `failure_mode` | при `notAfter < now`: nginx продолжает работать, docker валит handshake — **деплой встаёт** |

**Правила валидации.**
1. Проверка обязана покрывать порт registry отдельной строкой: ошибка в
   `ssl_certificate` нового server-блока не видна на 443.
2. Порог ≥ 30 суток (SC-007) и ≤ 45 суток (запас на 200-дневный сертификат).
3. Напоминание считается работающим только если канал **проверен намеренно**
   (запуск с завышенным порогом даёт видимое/слышимое предупреждение).

**Чем проверяется.** `bash tools/check-cert-expiry.sh sm-karaoke.ru:443
sm-karaoke.ru:5443 --warn-days 400` → exit 1 + предупреждение; при штатном пороге —
exit 0.

---

## Диаграмма связей

```text
E3 Учётные данные ──выдаёт/отзывает──> E2 Namespace ──содержит──> E1 Контейнерный образ
                                            │                          │
                                            │                          ├── E4 Пин (для vendored/pull-through)
                                            │                          └── E5 Политика хранения (для karaoke/**)
                                            v
                                     E7 Хранилище (том на прод-хосте)
                                            │
E6 Переходное окно ──определяет источник──> ┘        E8 Напоминание о сертификате ──гейт доступа──> registry
```

## Инварианты (сквозные)

| # | Инвариант | Проверка |
|---|---|---|
| I-1 | Ни один прод-контейнер не получает образ от внешнего реестра | compose прода: только `sm-karaoke.ru:5443/mirror/**` и `${DOCKER_REGISTRY}/karaoke/**` |
| I-2 | В `library/**` нет ни одной созданной нами манифест-записи | `GET /v2/_catalog` + `images.lock` (в `library/*` — только то, что есть у upstream) |
| I-3 | Каждый образ в `mirror/**` имеет пин и запись в `images.lock` | `registry-vendor.sh --verify` |
| I-4 | Тег действующего прод-образа не может быть удалён retention | `zot verify-feature retention` (dry-run) + правило «ИЛИ» внутри политики |
| I-5 | Анонимный запрос снаружи не даёт ни одной операции | `registry-smoke.sh`: `/v2/`, `/v2/_catalog`, `/v2/<name>/manifests/<tag>` — все 401 (SC-006) |
