# Implementation Plan: Свой приватный Docker-registry на sm-karaoke.ru

**Branch**: `540-private-docker-registry` | **Date**: 2026-10-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification из `/specs/540-private-docker-registry/spec.md`;
Wayfinder-карта #274 (закрыта), issue-задача исполнения #286 (заклеймлена).

**Терминология**: в этом плане «образ» = **контейнерный образ** (см. spec.md § Key
Entities и Assumptions). Медиа-изображения к теме не относятся.

## Summary

Развернуть на прод-хосте `sm-karaoke.ru` (`188.127.240.124`) приватный registry
**Zot `ghcr.io/project-zot/zot:v2.1.22`** в одном контейнере, слушающий
`127.0.0.1:5000`, с публикацией наружу через **существующий host-nginx на отдельном
порту 5443** и **существующим сертификатом** `sm-karaoke.ru` (TLS-терминация на
nginx; `insecure-registries` и правка `daemon.json` не используются нигде).

Доступ закрыт целиком (FR-002): `htpasswd` с bcrypt, один комплект учётных данных на
проект, выдача и отзыв — строкой в файле (Zot перечитывает его на лету, без
перезапуска). Namespace = проект: `karaoke/**` — наши образы, `mirror/**` —
вендоренные сторонние с сохранением исходного пути; `library/**` — **только**
pull-through, туда никогда не пушим.

Прод получает базовые образы **вендорингом** (решение владельца #276), а не через
`registry-mirrors`; сборка на admin переключается на наш registry (FR-017), из-за
чего **расширяется guard R-04/R-05** (FR-014, SC-009) — сегодня он сопоставляет
строки ровно `^FROM nginx:` / `^FROM node:` и на именах с registry ослепнет молча.
Обновляется `knowledge/` (FR-013); бэкапа нет — вместо него проверенная процедура
восстановления; срок сертификата контролируется суточной проверкой `notAfter`.

## Technical Context

**Platform**: прод-хост `sm-karaoke.ru` = `188.127.240.124` (Ubuntu 26.04, 6 vCPU,
3.8 ГБ RAM, 985 ГБ, swap 8 ГБ), host-nginx 1.28.x + host-сеть без firewall;
admin-машина `nsa-i9` (сборка, push, вендоринг).
**Registry**: `ghcr.io/project-zot/zot:v2.1.22` — один статический бинарник,
`Entrypoint=/usr/local/bin/zot-linux-amd64`, `Cmd=["serve","/etc/zot/config.json"]`,
`User=0`. Замеренный RSS: 49.9 МиБ покой → **114.3 МиБ пик** на push 5.36 ГБ → 69.3 МиБ
после цикла (слои в памяти не буферизуются).
**Transport**: HTTPS `sm-karaoke.ru:5443`; сертификат GlobalSign
`/etc/keys/www.sm-karaoke.ru.{crt,key}` (`checkhost sm-karaoke.ru` → match,
`notAfter = 2027-04-03T10:34:27Z` = 13:34 МСК); nginx терминирует TLS, проксирует на
`http://127.0.0.1:5000`; у Zot `http.compat: ["docker2s2"]`.
**Auth**: `http.auth.htpasswd.path=/etc/zot/htpasswd`, bcrypt (`$2b$…`), `failDelay: 5`;
анонимный доступ запрещён полностью, UI и search выключены, `hashKey` не задаём.
**Sync**: ровно один upstream `https://registry-1.docker.io`, `onDemand: true`,
`tlsVerify: true`, обязательный `content: [{"prefix": "library/**"}]` (без него push
нашего нового образа падает с 503 — измерено на стенде).
**Storage**: bind-mount `/sm-karaoke/system/registry`; данные лежат вне
docker-конфайнмента, поэтому `karaoke-docker-prune.timer` их не касается — рост
ограничивает retention самого Zot (`storage.retention`, inline GC без остановки).
**Clients**: `docker` CLI на admin (push) и на проде (pull);
`DOCKER_CONFIG=/home/nsa/Karaoke/.docker` (Pass 373); учётные данные — только в
`do.env` (вне git).
**Testing**: `bash tools/check-docker-image-tags.sh --selftest` (новый self-test
guard'а), `zot verify <config.json>` и `zot verify-feature retention` (dry-run),
`bash tools/registry-smoke.sh <host:port>` (новая приёмка, `curl`+`docker`, без правок
на хосте), `bash -n`/`shellcheck` по `do.sh`, репетиция восстановления на admin.
**Scale/Scope**: 5 своих образов (≈11.7 ГБ с `demucs`) + **8** вендоренных сторонних
(≈11.8 ГБ по измеренным шести из решения #279; для `minio` и `nvidia/cuda` замеров нет —
см. ниже); один контейнер
registry, один серверный блок nginx, ноль новых внешних зависимостей в рантайме
прода.

**Вендорится восемь сторонних образов, а не шесть — следствие FR-007 и FR-017.**
Исходное решение владельца (#279) перечисляло шесть небиблиотечных образов
(`demucs`, `ollama`, `whisper-server:cuda`, `searxng`, `4get`, `xray-core`); карта #274
отдельно оставляла открытым вопрос про `quay.io/minio/minio` (`map-v7.md` § Not yet
specified, «параметр спеки»). Утверждённая спека закрывает его дважды:

- **FR-007** — «прод MUST получать все нужные ему образы без обращения к внешним
  реестрам», а прод использует `quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z`
  ⟹ MinIO вендорится в `mirror/minio/minio:<пин>` (проверяется тестом **US3-1**:
  развернуть прод-конфигурацию, не имея доступа к внешним реестрам);
- **FR-017** — «сборка MUST получать базовые образы из нашего registry», а
  `deploy/karaoke-app/DockerfileDemucs` строится от `nvidia/cuda:12.6.2-base-ubuntu22.04`,
  которого нет в allow-list `library/**` ⟹ он вендорится в
  `mirror/nvidia/cuda:12.6.2-base-ubuntu22.04`.

Это не пересмотр решения владельца, а его следствие; подтверждено лидом на ревью
плана 2026-10-11 (см. § «Решения, зафиксированные на ревью»). Если владелец захочет
оставить MinIO на quay.io — это правка **FR-007** его словом, а не решение плана.

## Constitution Check

*GATE: пройдено до Phase 0 research. Перепроверено после Phase 1 design — см. ниже.*

| Принцип / hard-gate | Как соблюдается |
|---|---|
| **I. Self-contained автопайплайн** | Registry — локально развёрнутый сервис на своём хосте; внешний SaaS не добавляется. Наоборот, убирается зависимость от Docker Hub. |
| **II. Сырой JDBC** | Кода БД нет. Не затрагивается. |
| **III. SyncRegistry** | Сущностей LOCAL↔SERVER не добавляется. Не затрагивается. |
| **IV. Async-очередь** | `KaraokeProcess*` не меняется. Не затрагивается. |
| **V. Два фронтенда** | `webvue3`/`karaoke-public` не меняются. |
| **VI. Code Standards** | Публичных API не добавляется, поэтому KDoc/JSDoc не требуется. **R-32/FR-009 (per-feature документ) применяется**: спека правит код деплоя (`deploy/do.sh`, Dockerfile'ы, конфигурацию nginx), а конституция требует документ «при правке кода» — заводится `docs/features/private-docker-registry.md` с шестью обязательными секциями; прецедент инфраструктурного документа в этом каталоге — `docs/features/tracker-local-integration.md`. Проверка — `tools/check-feature-doc.sh`. |
| **VII. Cross-Machine Setup** | Локальные конфиги не трогаем; секреты — только `*.example`. |
| **VIII. Секреты и git-гигиена** | `htpasswd` с реальными кредами НЕ коммитится: `deploy/prod-single-host/registry/htpasswd.example` + запись в `.gitignore` + проверка `git ls-files \| grep -iE 'htpasswd$'` → пусто. `REGISTRY_PASSWORD` живёт только в `do.env`. Пароль не печатается в вывод. |
| **IX. Knowledge-first** | Pre-flight выполнен в спеке (4 grep-попытки, явный no-op по теме). План читает `deploy-overview.md`, `do-sh.md`, `architecture-conventions.md` (R-04/R-05), ADR `local-0003`. Обновление `knowledge/` — FR-013, обязательный пункт. |
| **IX.3 Subagent workspace isolation** | Параллельных субагентов на разных PR нет: одна ветка `540-private-docker-registry`, один долгоживущий субагент. |
| **Tier-1 Hard Gate — Machine-Specific** | `nsa-i9`/`nsa`: сборка ✅, локальные контейнеры по согласию, **прод-deploy ❌ без отдельного слова владельца**. План это учитывает: шаги, меняющие прод, помечены «владелец/по согласию» — см. § Этапы. |
| **Tier-1 Hard Gate — Build/Deploy/Containers** | `GRADLE_USER_HOME` и `DOCKER_CONFIG` экспортируются самим `do.sh` (Pass 372/373) — сохраняется. Контейнеры только через `do.sh`, `docker restart` запрещён. |
| **Tier-1 Hard Gate — Git** | Работа в feature-ветке + PR + CI. Коммит/деплой — только по отдельному слову владельца. |
| **Tier-1 Hard Gate — OpenProject** | Claim выполнен (2026-10-11). После merge — `report.md` + `add-comment` + `mark-review`. `report.md` — REQUIRED артефакт, создаётся на этапе `implement`. |
| **Tier-1 Hard Gate — TOP-11 ловушек** | R-04/R-05 (теги `nginx:stable`, `node:22-alpine`) не ослабляются, а **усиливаются**: guard расширяется под registry-квалифицированные имена (FR-014/FR-017/SC-009). R-09 (`docker logs` первым) — при отладке Zot. |
| **Tier-2 — Обязательная проверка после изменения** | Gradle-сборки в этой спеке не требуются (Kotlin/JS не меняются). Проверки: guard-скрипты, `bash -n`, `zot verify`, knowledge-линтеры. |

**Нарушений нет.** `Complexity Tracking` не заполняется (см. одноимённый раздел).

## Решения плана

Все решения ниже опираются на замеры стенда, чтение первоисточников (Zot v2.1.22:
`pkg/api/config/config.go`, `pkg/retention/retention.go`, `pkg/storage/gc/gc.go`,
`pkg/api/routes.go`) и на факты с диска репозитория. Расхождений со спекой нет;
там, где спека молчит — решение принимает план (помечено «план»).

| # | Решение | Обоснование / источник |
|---|---|---|
| D1 | Порт **5443**, `DOCKER_REGISTRY=sm-karaoke.ru:5443/karaoke` | Решение 3 владельца; 5443 уже проверен живым слушателем снаружи (#284). Значение обязано содержать точку и порт, иначе docker не распознает хост (дефект №3 карты). |
| D2 | Zot за host-nginx, TLS на nginx, Zot на `127.0.0.1:5000` | `distribution` § nginx: «requires that you move TLS termination from the Registry to the proxy itself». MinIO уже опубликован так же (loopback + host-nginx). |
| D3 | Сертификат — **существующий** `/etc/keys/www.sm-karaoke.ru.{crt,key}`, `server_name sm-karaoke.ru` | `checkhost` → match; порт на проверку имени/цепочки не влияет. Certbot и поддомен отклонены владельцем (#285). |
| D4 | Конфиг Zot = **`stand/config.json` без изменения структуры** (меняются только `http.realm`, `log.level`, retention-политики `mirror/**`) | Конфиг принят Zot v2.1.22 без правок на стенде; `retention`, `gcDelay`, `gcInterval`, `syncTimeout`, `failDelay`, `compat` валидны. |
| D5 | `content: [{"prefix": "library/**"}]` — **обязателен** | Ловушка `onDemand` без `content`: push нашего нового образа → 503. Измерено. |
| D6 | Схема имён: `karaoke/**` — свои, `mirror/<upstream-path>` — сторонние, `library/**` — **только** pull-through | FR-018. Инвариант: **в `library/**` не пушим никогда** — этот префикс в allow-list sync, и HEAD при push пошёл бы наверх (та же ловушка D5, но с 200 вместо 401 — поведение непредсказуемо). Полная грамматика — `contracts/image-naming.md`. |
| D7 | Прод получает базовые образы вендорингом и **ни одного pull-through**; вендорится **8** сторонних образов | Решение владельца #276 + FR-007 (прод не ходит во внешние реестры). К шести образам решения #279 добавляются `minio` (FR-007/US3-1) и `nvidia/cuda` (FR-017); полный список — `contracts/image-naming.md` § 3.2. Pull-through `library/**` остаётся подстраховкой для сборок на admin (решение #279). |
| D8 | Сборка (`FROM`) переключается на `<registry>/library/...` (pull-through), кроме `nvidia/cuda` → вендорится в `mirror/nvidia/cuda:...` | FR-017. `library/**` покрывает `openjdk`, `ubuntu`, `eclipse-temurin`, `node`, `nginx`; `nvidia/cuda` — нет. CI docker-образы не собирает (проверено: `.github/workflows/lint.yml` только запускает guard) → секретов в CI не нужно. |
| D9 | Пин версий: `<tag>@sha256:<digest>` + карта `registry/images.lock` | FR-008. Четыре из шести сторонних образов идут под `latest`/`cuda` — для них пин только по дайджесту. |
| D10 | Guard R-04/R-05: нормализация имени (срезать registry-хост) + `--selftest` | FR-014/SC-009. Сейчас guard видит 4 строки; после D8 увидит 0 и станет молча зелёным — это регресс защиты. Контракт — `contracts/tag-guard.md`. |
| D11 | `do.sh`: логин разделён на host/user/password, `--password-stdin`; `do_pull_*` тоже логинятся; dual-push через `DUAL_PUSH=1` | Дефекты №1–2 карты (`--username ${DOCKER_REGISTRY}` без SERVER = логин в Docker Hub, пароль в argv). FR-015. Контракт — `contracts/do-sh-cli.md`. |
| D12 | Окно dual-push ≤ 2 успешных прод-деплоя И ≤ 1 месяц, затем `DUAL_PUSH=0`; namespace `svoemestodev` не удаляем | Решение владельца #280. |
| D13 | Retention: `karaoke/**` — keep 10 последних push ИЛИ push за 90 дней, `deleteUntagged: true`; `mirror/**` — `deleteUntagged: true` без tag-лимита; `**` — дефолтная политика | FR-010. Проверено по исходникам: правила внутри политики комбинируются по **ИЛИ** (`retention.go:219`), tag-retention применяется только к репозиториям, у которых в политике есть `keepTags` (`gc.go:786`) — значит пины в `mirror/**` под авто-удаление тегов не попадают. |
| D14 | Напоминание о сертификате: `tools/check-cert-expiry.sh` + systemd-таймер на **admin** с существующим `announce` | FR-016. На проде канала уведомлений нет (проверено: в `deploy/prod-single-host/*.sh` только `notify-send` в интерактивной ветке `do.sh`), поэтому таймер, пишущий в journal прода, не закрывает «владелец знает». Admin использует `announce` в каждом деплое — канал проверенный. Порог 45 дней (запас к 30 дням SC-007). Подробности — § «Открытые вопросы». |
| D15 | Процедура восстановления + репетиция на admin | FR-011. Registry не бэкапится (решение #281): наши образы воспроизводимы `do.sh`, сторонние — из `images.lock`. Репетиция выполняется на admin в одноразовом каталоге — прода не касается. |
| D16 | Обновление `knowledge/system/infra/{deploy-overview,do-sh}.md` + новый `knowledge/system/infra/registry.md` + строка в `knowledge/system/README.md` | FR-013, MUST #0. Новый документ повторяет форму `deploy-overview.md` (title, `> **Домен**`, `> **Компонента**`, секции, Changelog) — файлы `system/` не подпадают под обязательные заголовки линтера (проверено: `lint-knowledge.py:105-120` классифицирует только `adr/` и `domains/`). |

### Инварианты (проверяемые)

1. **I-1**: ни один прод-контейнер не получает образ от внешнего реестра (FR-007).
2. **I-2**: в `library/**` нет ни одной манифест-записи, созданной нами (только sync).
3. **I-3**: каждый сторонний образ в `mirror/**` содержит пин (дайджест) и есть в `images.lock` (FR-008).
4. **I-4**: тег, на который ссылается прод-compose, не может быть удалён retention (FR-010 vs SC-003).
5. **I-5**: анонимный запрос снаружи не даёт ни одной операции (FR-002, SC-006).

## Project Structure

### Documentation (this feature)

```text
specs/540-private-docker-registry/
├── spec.md              # вход (контракт, не переписывается)
├── plan.md              # этот файл
├── research.md          # Phase 0: решения + обоснования (в т.ч. новые находки)
├── data-model.md        # Phase 1: сущности, инвентарь, состояния окна dual-push
├── contracts/           # Phase 1: интерфейсные контракты (4 файла)
│   ├── image-naming.md      # грамматика имён + карта «старое → новое»
│   ├── registry-transport.md# внешний HTTP-контракт (порт, TLS, лимиты nginx)
│   ├── do-sh-cli.md         # контракт команд do.sh после правки
│   └── tag-guard.md         # контракт guard R-04/R-05
├── quickstart.md        # Phase 1: проверяемые сценарии под FR/SC
└── tasks.md             # Phase 2 (/speckit.tasks — НЕ создаётся здесь)
```

### Repository files

```text
# --- Registry (канон прода) ---
deploy/prod-single-host/registry/config.json         # NEW: конфиг Zot
deploy/prod-single-host/registry/images.lock         # NEW: карта «upstream → наш ref + digest»
deploy/prod-single-host/registry/htpasswd.example    # NEW: формат (bcrypt), без секретов
deploy/prod-single-host/registry/RUNBOOK.md          # NEW: доступ, восстановление, сертификат
deploy/prod-single-host/registry/README.md           # NEW: раскладка и порядок развёртывания
deploy/prod-single-host/docker-compose-registry.yml  # NEW: контейнер karaoke-registry
deploy/prod-single-host/5443to5000                   # NEW: sites-enabled, TLS 5443 → 127.0.0.1:5000
deploy/prod-single-host/README.md                    # MODIFY: раскладка + порядок

# --- Сборка и публикация ---
deploy/do.sh                                         # MODIFY: логин (host/user/password), dual-push, pull-логины,
                                                     #         demucs без :latest, справка команд
deploy/deploy_web.sh                                 # MODIFY: тексты шагов (Push в Hub → Push в registry)
deploy/deploy_public.sh                              # MODIFY: то же
deploy/karaoke-app/Dockerfile                        # MODIFY: FROM <registry>/library/{openjdk,ubuntu}
deploy/karaoke-web/Dockerfile                        # MODIFY: FROM <registry>/library/eclipse-temurin
deploy/karaoke-public/Dockerfile                     # MODIFY: FROM <registry>/library/{node,nginx}
deploy/karaoke-webvue3/Dockerfile                    # MODIFY: FROM <registry>/library/{node,nginx}
deploy/karaoke-app/DockerfileDemucs                  # MODIFY: FROM <registry>/mirror/nvidia/cuda:<пин>
deploy/docker-compose*.yml                           # MODIFY: сторонние образы → <registry>/mirror/...@sha256:...
deploy/prod-single-host/docker-compose-{database,storage}.yml  # MODIFY: базовые образы прода → mirror/**
deploy/prod-single-host/do.sh                        # MODIFY: pull_web/pull_public — логин в наш registry
deploy/prod-single-host/do.env.example               # MODIFY: REGISTRY_HOST/USER/PASSWORD, DOCKER_REGISTRY, DUAL_PUSH
deploy/.gitignore-запись                             # MODIFY: /deploy/prod-single-host/registry/htpasswd

# --- Инструменты ---
tools/check-docker-image-tags.sh                     # MODIFY: нормализация имени + --selftest
tools/registry-vendor.sh                             # NEW: вендоринг по images.lock (admin)
tools/registry-smoke.sh                              # NEW: приёмка FR-002/003/004/006/007
tools/check-cert-expiry.sh                          # NEW: проверка notAfter (FR-016)

# --- Знание (SSoT, FR-013) ---
knowledge/system/infra/deploy-overview.md            # MODIFY: registry в раскладке deploy/
knowledge/system/infra/do-sh.md                      # MODIFY: команды registry, логин, dual-push
knowledge/system/infra/registry.md                   # NEW: компонента «приватный registry»
knowledge/system/README.md                           # MODIFY: listing infra/ (15 → 16 документов)

# --- Per-feature документ (R-32 / FR-009, Constitution §VI) ---
docs/features/private-docker-registry.md             # NEW: 6 обязательных секций
                                                     #   (Что делает / Зачем / Как работает /
                                                     #    Инварианты / Известные ловушки / Ссылки)
                                                     #   статус: active; шаблон — specs/001-code-standards-docs/
                                                     #   contracts/per-feature-doc.md; прецедент инфраструктурного
                                                     #   документа — docs/features/tracker-local-integration.md
docs/features/README.md                              # MODIFY: строка в оглавлении каталога
```

**Structure Decision**: регистрационные артефакты живут **внутри канона прода**
(`deploy/prod-single-host/`), а не в отдельном `deploy/registry/`: на хосте это
`/root/Karaoke/deploy/`, и `docker-compose-registry.yml` монтирует
`./registry/config.json` — относительный путь внутри одной папки, без связей между
каталогами. Инструменты — в `tools/` (рядом с `check-docker-image-tags.sh` и
`migration-smoke.sh`), знание — в `knowledge/system/infra/` (рядом с
`deploy-overview.md` и `do-sh.md`).

## Phase 0 — Outline & Research

`research.md` создан (Phase 0). Открытых `NEEDS CLARIFICATION` в спеке нет
(чек-лист `checklists/requirements.md` — все пункты пройдены после ответов
владельца). Research закрывает не «неизвестное из спеки», а **технические развилки
плана** и фиксирует четыре новые находки, которых не было ни в разведке, ни в карте:

1. **`ExternalURL` в Zot v2.1.22 не влияет на `Location` при загрузке блобов** —
   `getBlobUploadSessionLocation(request.URL, …)` возвращает путь из request-URL
   (`routes.go:3199`), а за nginx это origin-form без scheme/host, то есть
   **относительный** `Location`. Значит редиректы не утекут на
   `http://127.0.0.1:5000`. Риск снят фактом из исходников, но требует живой
   проверки push через 5443 (см. quickstart).
2. **`mirror/**` невозможно случайно «затереть» pull-through**: префикс
   `library/**` в `content`-allow-list означает, что push в этот же префикс
   активирует sync-HEAD — поэтому в `library/**` не пушим вообще (инвариант I-2),
   а вендоренные копии библиотечных образов кладём в `mirror/library/**`.
3. **`python3 -c "import crypt"` перестанет работать на самом проде**: Ubuntu 26.04
   несёт Python ≥ 3.13, где модуль `crypt` удалён (PEP 594). На admin (Python 3.12.3)
   команда работает — поэтому bcrypt генерируется **только на admin**, а на прод
   попадает готовый файл. Это надо записать в runbook, иначе следующий, кто будет
   выдавать доступ «на сервере», получит ModuleNotFoundError.
4. **`zot verify-feature retention` требует остановленного сервера** — команда
   обращается к метаданным хранилища, и документация Zot прямо предупреждает: «the
   zot server must be stopped before running this command» (`pkg/cli/server/
   verify_retention.go:31-33`); дополнительно требуется `gc: true` в конфиге.
   Следствие: проверка retention — **стендовая** процедура, на живом проде она
   означала бы недоступность registry. На проде политика контролируется логами GC.
   Это же снимает соблазн «проверить на бою».

## Phase 1 — Design & Contracts

- `data-model.md` — сущности (`Контейнерный образ`, `Namespace`, `Учётные данные`,
  `Пин`, `Политика retention`, `Переходное окно`, `Хранилище`, `Напоминание о
  сертификате`), правила валидации из FR, переходы состояний окна dual-push,
  полный инвентарь образов (свой/сторонний/базовый) с именами до и после.
- `contracts/image-naming.md` — грамматика ссылки на контейнерный образ и карта
  «старое → новое»; инварианты I-1…I-3.
- `contracts/registry-transport.md` — внешний контракт: `listen 5443 ssl`,
  `client_max_body_size 0`, `proxy_request_buffering off`,
  `chunked_transfer_encoding on`, `Host $http_host`, таймауты; набор эндпоинтов и
  ожидаемые коды (401/200/404); почему не нужен `daemon.json`.
- `contracts/do-sh-cli.md` — контракт команд `do.sh` (admin и прод): `login`,
  `push_*`, `pull_*`, `DUAL_PUSH`, коды возврата; что считается «успешным прод-деплоем»
  для счётчика окна.
- `contracts/tag-guard.md` — контракт guard'а R-04/R-05: вход (tracked `Dockerfile*`),
  нормализация имени, whitelist, `--quiet`, `--selftest`, коды возврата, критерий
  SC-009.
- `quickstart.md` — проверяемые сценарии: приёмка на стенде (admin), приёмка прода,
  намеренная поломка guard (SC-009), репетиция восстановления (FR-011/SC-005),
  проверка напоминания о сертификате (FR-016/SC-007), негативная проверка анонимного
  доступа (SC-006).

**Constitution Check (повторно, после Phase 1)**: без изменений — ни одно проектное
решение Phase 1 не добавило нарушений. Новых сущностей уровня приложения нет, схема
БД не меняется, публичных API не добавляется, секреты остаются вне git. Нарушений нет.

## Этапы работ

Порядок — от безопасного (репо) к прод-хосту. Шаги, меняющие прод, выполняются
**только по отдельному слову владельца** (Tier-1 Machine-Specific + «никаких
деплоев без слова владельца»).

### Этап A — Контракты, конфиг и инструменты в репо (PR, без прод)

1. `deploy/prod-single-host/registry/config.json` из `stand/config.json` (D4, D13):
   `http.realm = "karaoke-registry"`, `log.level = "info"`, retention-политика
   `mirror/**`, upstream один (D6).
2. `deploy/prod-single-host/registry/htpasswd.example` + запись в `.gitignore` +
   проверка `git ls-files | grep -i 'htpasswd'` → пусто (VIII.1).
3. `deploy/prod-single-host/registry/images.lock` — карта пинов (D9) с колонками
   `upstream_ref | our_ref | digest | checked_at`.
4. `deploy/prod-single-host/docker-compose-registry.yml` — `karaoke-registry`,
   `127.0.0.1:5000:5000`, bind-mount `/sm-karaoke/system/registry`, `restart:
   unless-stopped`, `healthcheck` на `/v2/` (401 = живо).
5. `deploy/prod-single-host/5443to5000` — серверный блок nginx (D1, D2, D3).
6. `tools/registry-vendor.sh`, `tools/registry-smoke.sh`, `tools/check-cert-expiry.sh`.
7. `tools/check-docker-image-tags.sh` — нормализация + `--selftest` (D10).
8. `deploy/do.sh`, `deploy/prod-single-host/do.sh`, `deploy_web.sh`,
   `deploy_public.sh`, `do.env.example` — контракт D11/D12.
9. `deploy/prod-single-host/registry/{README.md,RUNBOOK.md}` (D15, D14).
10. `docs/features/private-docker-registry.md` — per-feature документ (R-32/FR-009,
    Constitution §VI): шесть обязательных секций, статус `active`, слаг совпадает с
    именем файла; шаблон — `specs/001-code-standards-docs/contracts/per-feature-doc.md`.
    Заводится **в этом же PR**, что и правки `deploy/`; в «Ссылки» — спека, план,
    `knowledge/system/infra/registry.md` и контракты. `docs/features/README.md` —
    строка в оглавлении.

**Checkpoint A**: `bash tools/check-docker-image-tags.sh` (зелёный, 4/4),
`bash tools/check-docker-image-tags.sh --selftest` (зелёный), намеренная поломка
ловится, `bash -n` по всем изменённым `*.sh`, `zot verify registry/config.json`
(в контейнере `zot` на admin), `docker compose -f docker-compose-registry.yml config`,
`bash tools/check-feature-doc.sh docs/features/private-docker-registry.md` (exit 0).

### Этап B — Стенд-репетиция на admin (без прода)

11. Поднять `karaoke-registry` на admin в одноразовом каталоге
    (`/tmp/registry-rehearsal`), с тем же `config.json` и сгенерированным
    `htpasswd` (bcrypt через `python3 -c "import crypt"` — только admin, находка 3).
12. Прогнать `tools/registry-smoke.sh` и полный цикл push/pull (FR-005, FR-006):
    push образа ≥ 5 ГБ **с включённым sync** (это и есть проверка ловушки D5).
13. Репетиция восстановления: `rm -rf` каталога данных, поднять заново, восстановить
    из `images.lock` + `do.sh push` (FR-011, SC-005).

**Checkpoint B**: `registry-smoke.sh` — все проверки PASS; push 5.4 ГБ успешен;
после репетиции — снова PASS.

### Этап C — Перевод сборки и guard (PR, без прод)

14. `FROM` в 4 боевых Dockerfile'ах → `<registry>/library/...`; `DockerfileDemucs`
15. **(owner-gated: требуется уже развёрнутый registry — пп. 20–21)** Пересборка образов проекта на
    новых базах (FR-017): `do.sh build_public`, `build_webvue3`, `build_web`, `build_app` — сборка не
    должна обращаться к `docker.io`, а в логе Zot должны быть видны запросы к `/v2/library/<name>/…`.
    Стендовое подтверждение того, что pull-through обслуживает сборку, сделано раньше (Этап B п. 12).
    → `mirror/nvidia/cuda:<пин>` (D8).
16. Вендоринг **8** сторонних образов в `mirror/**` (шесть из решения #279 +
    `minio` по FR-007 + `nvidia/cuda` по FR-017) через `tools/registry-vendor.sh`
    (D7, D9) — требуется доступ к admin-машине и нашему registry; прод не
    затрагивается.
17. Compose-файлы: сторонние образы → `<registry>/mirror/...@sha256:...`; прод
    `docker-compose-{database,storage}.yml` → явные пины (FR-007, FR-017).
18. `demucs`: тег `latest` → `${BUILD_VERSION}` (`do_build_demucs`, `do_push_demucs`).
19. Обновить `docs/features/private-docker-registry.md` (раздел «Как работает» —
    вендоринг восьми образов и переключение `FROM` на наш registry; «Инварианты» —
    запрет записи в `library/**`): R-32 требует документ в том же PR, что и правка.

**Checkpoint C**: `docker compose config` по каждому изменённому compose (без `up`);
пересборка (п. 15) проходит без обращения к `docker.io` (owner-gated — только после пп. 20–21);
`grep -rn 'image:'` не содержит ни одного внешнего имени в путях прода и сборки;
guard зелёный на новых именах; `bash tools/check-docker-image-tags.sh --selftest`.

### Этап D — Фаза 0 (прод): развернуть registry, не трогая работающие сервисы

20. Владелец/по согласию: `scp` `deploy/prod-single-host/*` → `/root/Karaoke/deploy/`,
    создать `/sm-karaoke/system/registry`, положить `registry/htpasswd` (0600).
21. По согласию: `docker compose -f docker-compose-registry.yml up -d` — **новый**
    контейнер; существующие четыре не перезапускаются (FR-012, SC-003).
22. По согласию: `5443to5000` → `/etc/nginx/sites-enabled/`, `nginx -t`,
    `systemctl reload nginx` (reload, не restart).

**Checkpoint D**: `tools/registry-smoke.sh sm-karaoke.ru:5443` — PASS; снаружи
`/v2/` без кредов → 401, с кредами → 200; RSS `docker stats --no-stream karaoke-registry`
≤ **150 МиБ** в покое (замер стенда: 49.9 МиБ покой, 114.3 МиБ пик на push 5.36 ГБ).

### Этап E — Фаза 1 (прод): перевод прод-деплоя и окно dual-push

23. `do.env` прода: `REGISTRY_HOST/USER/PASSWORD`, `DOCKER_REGISTRY`, `DUAL_PUSH=1`.
24. Первый прод-деплой в окне (FR-015): push в Hub **и** в наш registry, pull с
    нашего. Проверка: `Status: Downloaded newer image` + `docker inspect` показывает
    наш registry в имени образа.
25. Второй прод-деплой — закрывает счётчик окна.

**Checkpoint E**: два успешных прод-деплоя; SC-001 (ноль обращений к внешнему
реестру за образами проекта — проверяется по логу Zot и по отсутствию docker.io в
пути).

### Этап F — Закрытие окна и знание

26. `DUAL_PUSH=0`; namespace `svoemestodev` остаётся архивом (решение #280);
    `do_push_*` больше не пушат в Hub (SC-008).
27. `knowledge/system/infra/{deploy-overview,do-sh}.md` + новый `registry.md` +
    `knowledge/system/README.md` (FR-013).
28. `specs/540-private-docker-registry/report.md` + `tracker.sh add-comment 286` +
    `mark-review 286` (Tier-1 OpenProject).

**Checkpoint F**: `python3 tools/lint-knowledge.py` → 0; `bash
tools/check-knowledge-structure.sh` → 9/9; `bash tools/spec-knowledge-preflight.sh`
если применимо; SC-008.

### Этап G — Эксплуатация: напоминание о сертификате (FR-016)

29. Установка таймера с `check-cert-expiry.sh` (D14) — операционный шаг владельца
    на admin (нужен sudo); в репо — сам скрипт и инструкция в `RUNBOOK.md`.

**Checkpoint G**: `bash tools/check-cert-expiry.sh sm-karaoke.ru:443
sm-karaoke.ru:5443 --warn-days 400` → exit 1 и слышимое/видимое предупреждение
(намеренная проверка канала, аналог SC-009); при штатном пороге 45 — exit 0.

## Dependencies

- Этап A → B → C (репо и стенд) — можно параллелить A и C частично, B зависит от A.
- C требует доступного registry: сначала стенд (B), затем прод (D).
- D → E → F. Этап G независим (кроме строки про порт registry в проверке).
- Внешние зависимости: A-запись/сертификат **не нужны** (используем существующий
  домен и сертификат); DNS не меняется; firewall не меняется (#284).

## Risks

- **Retention удалит образ, на который смотрит прод** → проверить `zot verify-feature
  retention` в dry-run и правило «правила внутри политики — ИЛИ», тег прод-образа
  всегда самый свежий (`retention.go:219`, `gc.go:786`); тег `mirror/**` не подпадает
  под tag-retention.
- **`library/**` залит нашим push** → инвариант I-2 + проверка `GET /v2/_catalog`
  в `registry-smoke.sh` (в `library/*` не должно быть дайджеста, которого нет в
  upstream).
- **Обрыв push 5.4 ГБ** (FR-005, SC-002) → `client_max_body_size 0`,
  `proxy_request_buffering off`, `chunked_transfer_encoding on`, таймауты 900 с;
  повторный push идемпотентен (blob-upload по дайджесту).
- **Просрочка сертификата = остановка деплоя** (Docker валит handshake) → D14,
  порог 45 дней; сертификат остаётся зависимостью, снятой только вручную.
- **`crypt` на проде отсутствует** (Python ≥ 3.13) → bcrypt генерируется только на
  admin; записано в `RUNBOOK.md`.
- **Прод-деплой без слова владельца** → этапы D/E/F выполняются только по
  отдельному согласию; план их не запускает.

## Complexity Tracking

Нарушений Constitution Check нет — таблица обоснований не заполняется.

## Решения, зафиксированные на ревью (2026-10-11)

Ревью плана лидом пройдено (8/8 контрольных сумм сверены побайтово, гейты прогнаны
заново). Четыре вопроса плана закрыты — правки внесены в этот документ:

1. **MinIO и `nvidia/cuda` вендорятся — подтверждено владельцем.** Спека (FR-007,
   US3-1) и FR-017 покрывают оба: прод использует `quay.io/minio/minio`, а
   `DockerfileDemucs` строится от `nvidia/cuda`. Итого **восемь** сторонних образов,
   а не шесть. **Расхождение снято в самом контракте:** Assumptions спеки приведены
   словом владельца к восьми образам (2026-10-11, `spec.md` sha256 `ee8363b6…`);
   **ни один FR и ни один SC не тронут**. Прежняя оценка «≈12 ГБ» относится к
   измеренным шести образам и больше не выдаётся за общий объём: для `minio` и
   `nvidia/cuda` замеров нет, точный объём фиксируется при переносе в
   `registry/images.lock`. Зафиксировано явно: § Technical Context, решения D7/D8,
   `contracts/image-naming.md` § 3.2, Этап C п. 16.
2. **Напоминание о сертификате — план утверждён как есть**: скрипт в репозитории +
   таймер на admin с существующим `announce`. Новый канал на проде (mail/Telegram) и
   правки Kotlin (health-report) **не заводятся**: FR-016 требует результата, а не
   новой подсистемы. Решение D14 остаётся без изменений.
3. **Пины сторонних образов выбирает исполнитель на этапе `implement`.**
   Предварительное утверждение владельцем не требуется: они попадут в
   `registry/images.lock` и будут видны в диффе на гейте ревью. Формат —
   `<tag>@sha256:<digest>` (D9).
4. **Per-feature документ заводится** — `docs/features/private-docker-registry.md`.
   Основание: спека правит код деплоя (`deploy/do.sh`, Dockerfile'ы, конфигурация
   nginx), а Constitution §VI / R-32 требует документ **при правке кода**; прецедент
   инфраструктурного документа — `docs/features/tracker-local-integration.md`.
   Место в работах: Этап A п. 10 (создание), Этап C п. 19 (обновление в том же PR, что
   и правка Dockerfile'ов), проверка — `tools/check-feature-doc.sh`.

## Остаётся непроверенным (перенесено в `implement`)

Не открытые вопросы, а проверки, которые нельзя выполнить чтением:

1. **Живой push 5.4 ГБ через nginx-прокси на 5443** (не напрямую в Zot, как на
   стенде): относительный `Location`, отсутствие буферизации, таймауты. Вывод об
   относительном `Location` получен чтением исходников Zot — живого прогона нет.
   Сценарий Q2 в [quickstart.md](./quickstart.md).
2. **Dry-run retention на реальном наборе тегов** (стенд, контейнер остановлен) —
   подтвердить инвариант I-4 наблюдением. Сценарии Q1 и Q7.
3. **Точные версии, дайджесты и объём** восьми сторонних образов — снимаются `docker pull`
   при вендоринге (Этап C п. 16); для `minio` и `nvidia/cuda` размер не измерен вовсе.
