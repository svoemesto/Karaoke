# AGENTS.md — инструкции для агентов

> **Версия**: 3.4.1 | **Last updated**: 2026-09-30 (Pass 381: исправлен неверный путь эндпоинтов очереди в Pass 380 — `GET /api/process/stop` отдаёт 404).
>
> Compact TOP-style (296 → 200 строк). Каждое правило в формате Rule/Failure/Source.
> Karaoke-override #1: AGENTS.md ≠ CLAUDE.md (см. `knowledge/README.md#Karaoke-overrides`).
> **Breaking change** (3.0.0): формат и density изменены.

## Tier-0: Язык общения

**Rule**: Всё общение с пользователем — **ТОЛЬКО на русском языке**.
**Failure**: на иностранном — непрошенный перевод.

## Tier-1: MUST #0 — Knowledge-first pre-flight (NON-NEGOTIABLE)

**Rule**: Перед ЛЮБОЙ фичей / спекой / правкой — 5 шагов Knowledge-first. Без этого — СТОП.

**Protocol** (5 шагов, см. `knowledge/README.md`):
1. `knowledge/README.md` + `knowledge/domains/README.md` — **полностью**.
2. `grep -r '<keyword>' knowledge/` — минимум **3 попытки**.
3. `domain.md` + **все** `components/*.md` — для каждого домена.
4. **Все** `local-*.md` ADR из `knowledge/adr/`.
5. Только после 1-4 — `codegraph_explore` / `grep` по `src/`.

**Failure**: нет результатов → `spec.md` явно «Searched: ... → no relevant docs»;
игнорирование → спека на `/speckit.clarify`.

**Прецедент**: spec #339 (2026-09-09) — агент изобрёл форму кеша вместо паттернов
из `knowledge/domains/caching/components/caching-patterns.md`.

**Enforcement**: `tools/spec-knowledge-preflight.sh` + секция
«Knowledge References» MANDATORY в `spec.md`.

## Tier-1: Hard Gate — Machine-Specific Exceptions

**Rule**: Каждая машина имеет матрицу разрешений. Перед операцией с контейнерами —
проверить `hostname`.

**Protocol** (единый источник):

| Hostname | OS-user | `karaoke-app` rebuild | `karaoke-app` restart | Лок. контейнер | Прод deploy |
|---|---|---|---|---|---|
| `nsa-i9` / `nsa` (текущая) | `nsa` | ✅ без согласия | ❌ по согласию | ✅ по согласию | ❌ по согласию |
| `dev-pc` / `dev` | `dev` | ✅ без согласия | ✅ без согласия | ✅ без согласия | ❌ по согласию |

**Failure**: вне матрицы → спросить владельца **до** выполнения.

### Pass 380 — порядок перезапуска `karaoke-app`: сначала остановить очередь

**Rule**: согласие владельца на перезапуск `karaoke-app` — это согласие на
перезапуск **после остановки очереди заданий**, а не вместо неё.

**Почему**: контейнер может быть в середине задания `KaraokeProcess`
(транскрибация, демукс, загрузка в хранилище). Поток исполнения живёт внутри
контейнера, и перезапуск его убивает. При старте `KaraokeProcess.setWorkingToWaiting()`
переводит `WORKING` обратно в `WAITING` — то есть задание не падает в отчёт, а
возвращается в очередь уже с потерянным прогрессом и начинается заново, хотя
само задание может длиться десятки минут.

**Protocol** (шаги строго по порядку):

1. **Проверить, что заданий в работе нет.** Активны `WORKING` и `CREATING`
   (полный набор статусов: `CREATING`, `WAITING`, `WORKING`, `DONE`, `ERROR`):

   ```bash
   curl -s -X POST http://localhost:7906/api/processesdigests \
     -H 'Content-Type: application/json' -d '{}' \
     | python3 -c "import json,sys; ps=json.load(sys.stdin)['processesDigests']; \
       print([(p['id'], p.get('name','')[:40]) for p in ps if p.get('status') in ('WORKING','CREATING')])"
   ```

   Пустой список — можно перезапускать. Непустой — шаг 2.

2. **Остановить очередь** — кнопка «Стоп» в шапке админки (компонент
   `ProcessWorker.vue`). Это кнопка запуска/остановки **очереди заданий**,
   а не отдельного задания.

   Если кнопки под рукой нет, эндпоинты очереди живут в `MainController`,
   у которого **нет** префикса `/api`, и nginx их не проксирует. Из-под
   `:7906` они не работают. Обращаться к бэкенду напрямую:

   ```bash
   curl -s http://localhost:8898/process/isworking   # false — очередь стоит
   curl -s http://localhost:8898/process/stop        # остановить
   curl -s http://localhost:8898/process/start       # запустить обратно
   ```

   **Предупреждение (Pass 381):** путь `GET /api/process/stop` в предыдущих
   версиях этого документа был **неверным** — он отдаёт 404. Тот, кто
   последовал бы ему, решил бы, что очередь остановить нечем, и перезапустил
   контейнер с работающим заданием — ровно то, ради чего Pass 380 и написан.
   Проверено 2026-09-30: `:7906/api/process/stop` → 404,
   `:8898/process/stop` → 200.

3. **Дождаться фактической остановки бэка.** Повторять проверку из шага 1, пока
   список активных не опустеет. «Стоп» останавливает очередь, но уже взятое в
   работу задание может дорабатывать — перезапуск в этот момент всё равно его
   убьёт.

4. **Только теперь** перезапускать контейнер (`do.sh start_app`).

5. **После перезапуска** очередь сама не поднимается — её надо включить кнопкой
   «Старт» в шапке админки, если она была включена до перезапуска.

**Failure**: перезапуск при непустом списке активных заданий → потерянное
задание (шаг 3) либо «зависшее», вернувшееся в `WAITING` с нуля (шаг 1).
**Enforcement**: ручная проверка владельцем; `tools/check-container-restart.sh`
проверяет только запрет `docker restart`, но не состояние очереди.

**Примечание**: разрешение владельца, данное на конкретный перезапуск
(«перезапусти, чтобы доставить правку»), **не продлевается** на следующие
перезапуски в той же сессии. Нужно отдельное согласие.

## Tier-1: Hard Gate — Build / Deploy / Containers (Pass 372-375)

**Rule**: gradle с `GRADLE_USER_HOME`. Docker с `DOCKER_CONFIG`. Контейнеры — через
`deploy/do.sh`. Frontend — через `cd <dir> && npm run`.

| Под-правило | Rule | Failure | Enforcement |
|---|---|---|---|
| **Gradle (R-372)** | `./gradlew ...` с `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle` | read-only FS в DSH-sandbox | `check-gradle-user-home.sh` |
| **Docker (R-373)** | `docker build` / `do.sh build_*` с `DOCKER_CONFIG=/home/nsa/Karaoke/.docker` | read-only `~/.docker/buildx/activity/` | `check-docker-config.sh` |
| **Containers (R-374)** | Только через `deploy/do.sh start_<c>` / `restart_<c>`. **Запрещено** `docker restart <c>`. | потеря зависимостей + обход согласия | `check-container-restart.sh` |
| **Frontend (R-375)** | `cd <frontend-dir> && npm run` (`webvue3/` или `karaoke-public/`). В корне `package.json` **нет**. | `npm run` из корня → node_modules не найден | `check-frontend-build.sh` |

## Tier-1: Hard Gate — OpenProject Workflow

**Rule**: При Issue ID — **обязательно** 4 шага (`report.md` — REQUIRED артефакт).

| Шаг | Команда | Когда |
|---|---|---|
| 1. **Claim** | `tracker.sh claim-issue <NNN>` | До первой строки кода |
| 2. **Add comment** | `tracker.sh add-comment <NNN> --file specs/<NNN>-<slug>/report.md` | После merge, до mark-review |
| 3. **Mark review** | `tracker.sh mark-review <NNN>` | После add-comment |
| 4. **Close** | `tracker.sh close-issue <NNN>` | После ревью владельца |

**Failure** (Pass 349): OpenProject #69 без workflow → отчёт задним числом.
**Enforcement**: `tools/check-spec-issue-link.py` + manual review.

## Tier-1: Hard Gate — Git — CI-gate для master ⛔

**Rule**: НИКОГДА `git commit` / `git push` напрямую в `master`. ТОЛЬКО через
feature-ветку + PR + CI.

**Protocol**:
```bash
N=$(./tools/reserve-branch-number.sh my-slug)
git checkout -b "${N}-my-slug" master && <правки>
git push -u origin "${N}-my-slug" && gh pr create --base master
gh pr checks && gh pr merge --merge   # БЕЗ --delete-branch
```

**Failure** (Pass 353): прямая правка master → merge conflict + потеря работы.
**Enforcement**: 3 уровня (branch protection, pre-commit, CI lint).

## Tier-1: Hard Gate — Subagent workspace isolation (Pass 379)

**Rule**: Несколько субагентов для параллельных PR-веток MUST работать в
**отдельных `git worktree`**. НЕ в одном `cwd`.

**Mandatory**:
```bash
for slug in jpa docker-tags mp4; do
  N=$(./tools/reserve-branch-number.sh $slug)
  git worktree add ../Karaoke-${N}-${slug} -b "${N}-${slug}" master
done
```

**Failure**: Два+ субагента в одном workspace → 30 минут на rebase всех PRов
(прецедент Pass 379: чужие коммиты в чужих PRах через `git checkout` race).
**Enforcement**: `tools/check-subagent-isolation.sh`. **Альтернатива**:
`git clone <repo> Karaoke-<slug>` или `gh repo fork`.

## Tier-1: Hard Gate — Knowledge SSoT

**Rule**: Изменения в коде (по `.ssot-map.yml`) требуют синхронного обновления
`knowledge/`. **Rule (structure)**: 9 структурных проверок + cross-links
+ markdown style (NO EMOJI, mandatory headers). **Failure**: новые violations → CI fail
(`--baseline FILE` для допустимых). **Enforcement**: `check-ssot-impact.py`,
`check-knowledge-structure.sh`, `lint-knowledge.py`.

## Tier-1: Hard Gate — Secrets & git hygiene (Pass 374)

**Rule**: Секреты НЕ коммитить. `.gitignore` НЕ достаточно для tracked файлов —
нужен `git rm --cached`.

**Pre-commit check**:
```bash
git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$|\.p12$|\.pfx$'
# MUST возвращать пусто.
```

**Failure** (2026-08-03): `deploy/.env` трекался 3 года с паролями Postgres/MinIO/Docker
Hub в публичном репо. **Enforcement**: `check-ssot-impact.py` + manual secret-rotation.

## Tier-1: Hard Gate — Диагностика через docker logs (Pass 358)

**Rule**: При отладке — сначала `docker logs <container>`, потом гипотезы.
**Failure**: потеря итераций на гипотезы, лог прямо указывал на причину.
**Source**: `docs/ops/log-correlation.md`.

## Tier-1: Hard Gate — Процесс: wayfinder → исполнение (PR #590)

**Rule**: Большая туманная задача — сначала wayfinder-карта решений (скилл
`wayfinder`, включается вручную). Карта закрыта → лид классифицирует остаток
работы, **владелец решает**: «большая» или «маленькая». Маленькая — лид
**спрашивает владельца**: сделать самому или через спеку. Большая — issue-задача
work package в OpenProject (`tools/tracker.sh create-issue`) → `speckit-specify` →
спека уходит владельцу → **СТОП** → по слову владельца долгоживущий субагент ведёт
`speckit-plan → speckit-tasks → speckit-analyze → speckit-implement`, лид ревьюит
каждый шаг по фактам с диска → после merge штатные 4 шага OpenProject-workflow
(см. выше). Лид обращается к владельцу только когда нужно менять спеку:
**спека — контракт**, правки FR/SC — только словом владельца.

**Трекер — только OpenProject**: карта, тикеты-решения и issue-задачи — work
packages проекта `karaoke` (id 3); локальные `issues_NNNN.md` в проекте **не
ведутся**. Раскладка карты и тикетов, статусы и команды — `docs/tracker-setup.md`
§ «Wayfinding в OpenProject». Каталог `.scratch/karaoke/` — только разовые
локальные артефакты (research-выгрузки, черновики), не слой задач.

**Инфраструктура команды — read-only**: репозиторий `/home/nsa/agents-team-srv`
для задач Караоке не изменяется (ни файлов, ни коммитов); изменения для нашего
проекта делаются на копиях и ссылках (`~/.dsh/skills/`, `~/.dsh/plugins/`,
`~/.dsh/romario-team/`).

**Failure**: исполнение «в тумане» без карты и спеки; локальные issue вместо
OpenProject; правка `agents-team-srv` под задачи Караоке; спека как черновик
(правки FR/SC «по ходу» без слова владельца).
**Source**: `~/.dsh/romario-team/wayfinding-operations.md` + `subagents.md`
(командные инструкции, установлены 2026-09-29); `docs/tracker-setup.md`.

## Tier-2: Каталог guards (Pass 379)

| Rule | Source | Tool |
|---|---|---|
| R-04 nginx:stable | `architecture-conventions.md` | `check-docker-image-tags.sh` |
| R-05 node:22-alpine | `architecture-conventions.md` | `check-docker-image-tags.sh` |
| R-07 JPA запрет | `architecture-conventions.md` | `check-no-jpa-imports.sh` |
| R-08 Sanitizer idempotency | `architecture-conventions.md` | code review |
| R-11 MP4/скачивание | `architecture-conventions.md` | `check-no-mp4-mentions.sh` |
| R-32 FR-009 per-feature | `constitution.md` § VI | `check-feature-doc.sh` |
| R-43 redirectErrorStream | `constitution.md` § IV | code review |
| R-44 MLT/melt рендеринг | `architecture-conventions.md` | code review |
| IX.3 Subagent isolation | `constitution.md` § IX.3 | `check-subagent-isolation.sh` |
| Pass 372 Gradle | Этот файл (Tier-1) | `check-gradle-user-home.sh` |
| Pass 373 Docker | Этот файл (Tier-1) | `check-docker-config.sh` |
| Pass 374 Containers | Этот файл (Tier-1) | `check-container-restart.sh` |
| Pass 375 Frontend | Этот файл (Tier-1) | `check-frontend-build.sh` |
| Pass 380 Очередь перед рестартом | Этот файл (Tier-1) | ручная проверка владельцем |
| Процесс wayfinder → исполнение | Этот файл (Tier-1) | manual review |

## Tier-2: Hard Gate — TOP-11 ловушек

**Rule**: Каждая из 11 ловушек → cross-ref или guard. Подробности:
`architecture-conventions.md` § «Ловушки» (R-04, R-05, R-07, R-08, R-11, R-43, R-44)
+ FR-006/FR-009 (R-32) + JPA/Hibernate (R-07) + MP4 (R-11) + Sanitizer (R-08).
**Прецеденты**: Pass 339 (R-08), Pass 245 (R-04), Pass 375 (R-05), Pass 374 (R-09),
Pass 358 (R-09), Pass 353 (R-09).

## Tier-2: Hard Gate — Обязательная проверка после изменения (Pass 239+245)

**Rule**: Vite-build ≠ Docker-образ. **Все gradle с `GRADLE_USER_HOME`**.

```bash
# 1. Backend compile
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel
# 2. Линтеры
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:ktlintCheck
cd webvue3 && npm run lint && cd ..
cd karaoke-public && npm run lint && cd ..
# 3. Backend bootJar (на nsa-i9 — также :karaoke-app:bootJar)
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:bootJar --parallel
# 4. Frontend Vite
cd webvue3 && npm run build && npm run format:check && cd ..
cd karaoke-public && npm run build && npm run format:check && cd ..
# 5. Docker-образы
cd deploy && bash do.sh build_webvue3
cd deploy && bash do.sh build_public && cd ..
```

**Failure**: пропуск → сломанный production build.

## Changelog

- **3.4.1** (2026-09-30, Pass 381): в Pass 380 указан неверный путь эндпоинтов
  очереди — `GET /api/process/stop` отдаёт 404. Эндпоинты `start`/`stop`/
  `isworking` живут в `MainController` без префикса `/api` и nginx их не
  проксирует; обращаться надо напрямую к бэкенду на `:8898`. Смысл правила
  (остановить очередь до перезапуска) не изменился.
- **3.4.0** (2026-09-30, Pass 380): порядок перезапуска `karaoke-app` —
  перезапуск разрешён только после остановки очереди `KaraokeProcess` и
  фактического завершения активных заданий; согласие на конкретный перезапуск
  не продлевается на следующие в той же сессии.
- **3.3.0** (2026-09-29, PR #590): процесс привязан к трекеру проекта —
  карта/тикеты/issue-задачи только в OpenProject (локальные issue не ведутся);
  маленькая задача — лид спрашивает владельца (сам или через спеку); репозиторий
  `agents-team-srv` для задач Караоке — read-only (изменения на копиях).
- **3.2.0** (2026-09-29, PR #589): Процесс команды «wayfinder → исполнение» —
  карты решений и issue-задачи в `.scratch/karaoke/`, спека — контракт,
  длинная цепочка speckit — в долгоживущем субагенте с ревью лида.
- **3.1.0** (Pass 379 wayfinder #113 final compaction): 296 → 200 строк.
- **3.0.0** (Pass 379 wayfinder #113): TOP-rewrite. 682 → 296 строк. **BREAKING CHANGE**.
- **2.8.0** (Pass 379 follow-up): Subagent workspace isolation + governance-PR #486.
- **2.7.0** (Pass 375): Frontend build `cd <dir> && npm run`.
- **2.6.0** (Pass 374): Перезапуск контейнеров через `deploy/do.sh`.
- **2.5.0** (Pass 373): Docker `DOCKER_CONFIG`.
- **2.4.0** (Pass 372): Gradle `GRADLE_USER_HOME` (транзитивно).
- **2.3.0** (Pass 350): Auto-hooks для tracker.
- **2.2.0** (Pass 340): MUST #0 Knowledge-first.
- **2.1.0** и ранее — см. git log.

## Как обновлять этот файл

Правки governance — только через governance-PR с явным **semver bump**.
**НЕ дублировать** детали — каждое правило живёт в ОДНОМ файле:
- Hard-gates (Pass 372-375) → этот файл.
- Build/runtime/docker (R-04..R-44) → `knowledge/guidelines/architecture-conventions.md`.
- Principles I-IX → `constitution.md`.
- Knowledge (MUST #0) → `AGENTS.md` Tier-1 + `knowledge/README.md`.
