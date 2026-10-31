# Component: agent-command-guard

> **Домен**: system (infrastructure)
> **Компонента**: плагин хоста DeepSeek Harness — перехват опасных команд
> AI-агента **до выполнения** и append-only журнал вызовов; рядом — независимый
> сборщик следа `tools/dsh-command-ledger.py`.

## Назначение

Правила Hard Gates в `AGENTS.md` сформулированы как инструкция агенту. Guard'ы
репозитория (`tools/check-container-restart.sh` и соседние) смотрят только в
`git diff HEAD` — они ловят **намерение закоммитить**, а не **исполнение**
команды. Между ними была дыра: агент мог выполнить запрещённое действие, и
следа не оставалось.

Компонент закрывает эту дыру для команд, меняющих состояние среды: перехват
выполняется **до** запуска команды, а решение (запретить или спросить) и текст
команды попадают в журнал, который агент не может переписать.

Граница возможностей зафиксирована: запрет **по тексту обходим**, поэтому цель
компонента — «остановить, спросить и оставить след», а не «сделать нарушение
невозможным» (ADR `local-0016`).

## Как устроен

| Шаг | Механика |
|---|---|
| Перехват | Событие `tools/pre-execute` хоста DSH: проверка идёт до диспетчеризации вызова; текст команды виден целиком, не обрезан |
| Разбор команды | Сегменты по `&&`, `||`, `;`, `|`, перевод строки; отслеживается текущий каталог (`cd`) |
| Сопоставление | Правила из `~/.dsh/policies/deny-commands.json` (JSON, перечитывается по mtime — правка применяется без перезапуска) |
| Состояние | Часть правил состояниезависима: ветка читается из `.git/HEAD` рабочего каталога (учёт worktree через `gitdir:`) |
| Окно согласия | Перед `ask` проверяется `~/.dsh/policies/consent/<session-id>.json`: запись, действующая до конца сессии, снимает вопрос для санкционированных операций |
| Решение | `deny` — запретить вызов; `ask` — спросить владельца через approval-канал DSH (тот же, что у эскалаций песочницы; при его отсутствии — запрет) |
| След | Запись в append-only журнал: 12 полей (см. ниже) |

### Правила и режимы

| Правило | Что ловит | Режим |
|---|---|---|
| `container-restart` | прямой `docker restart/stop/start` контейнеров Karaoke | **deny** |
| `do-sh-consent` | вызовы `deploy/do.sh`, меняющие состояние (`stop_app`, `start_app`, `restart_app`, `build_app`) | **ask** (окно согласия) |
| `compose-mutation-karaoke` | `docker compose up/down` в репозитории Karaoke | ask |
| `git-force-push` | `git push --force/--force-with-lease/-f` | ask |
| `git-no-verify` | `git commit --no-verify` | ask |
| `git-commit-on-master` | `git commit/rebase/merge` при ветке `master` | ask |
| `git-push-master-refspec` | `git push <remote> master` / `HEAD:master` / `master:master` | ask |
| `branch-protection-mutation` | `gh api -X DELETE/PUT/PATCH … branches/…/protection` | ask |

Умолчание для новых правил — `ask`; `deny` — только для проверенного узкого
класса. Область — **только репозиторий Karaoke**.

### Окно согласия

Согласие владельца на санкционированную команду (`deploy/do.sh`) по тексту
неразличимо: это состояние, а не текст. Поэтому источник согласия отдельный:

| Элемент | Значение |
|---|---|
| Где | `~/.dsh/policies/consent/<session-id>.json` (вне reach агента, рядом с политикой) |
| Формат | `{"session": "<id>", "grantedAt": "<ISO>", "ops": [...], "by": "owner"}` |
| Кто пишет | Агент — **только по прямому слову владельца** в чате, **до** вызова `deploy/do.sh` |
| Кто проверяет | Guard: идентификатор сессии доступен как `exec.agent?.session.id` (`packages/core/tools/src/index.ts:962`) |
| Срок | До конца сессии; новая сессия — новое окно |
| Не покрывает | Прямой `docker restart` и `docker compose` — иначе обход `do.sh` был бы возможен «по окну» |

### Схема записи журнала

Одна строка NDJSON на вызов `bash`: `ts`, `tool`, `workdir`, `command`
(секреты замаскированы **до** записи), `rule`, `segment`, `severity`, `verdict`,
`mode`, `test`, `session`, `consent`. Нарушение — это запись с
`verdict: "deny"`; `verdict: "ask"` означает «спрошено», исход одобрения лежит в
журнале сессий DSH (`approval/decided`).

Полей `approvedBy` и `taskRef` нет: рантайм не знает, кто одобрил (в событии
только `id` и `outcome`), а задачу Plane знает агент, не плагин.

## Что ловит и что не ловит

**Ловит** (проверено на 30 335 реальных вызовах `bash` из журналов сессий DSH):
прямой `docker restart/stop/start` контейнеров Karaoke; обход `deploy/do.sh`
через `docker compose up/down` в репозитории Karaoke; force-push; `--no-verify`;
коммит при ветке `master`; пуш в `master` по refspec; мутирующие вызовы
`gh api … protection`.

**Не ловит** — и это зафиксированное ограничение, а не дефект:

- **Обход текста.** `d''ocker`, `$(echo docker)`, запуск из скрипта-файла,
  `podman`, `systemctl`, `kill <pid>`, `docker exec`. Поэтому цель компонента —
  «остановить, спросить и оставить след», а не «сделать нарушение невозможным».
- **Согласие вне окна.** Согласие, не записанное в
  `~/.dsh/policies/consent/<session-id>.json`, для guard'а не существует: окно
  пишет агент по слову владельца, и вне этого механизма вызов `deploy/do.sh`
  будет задан вопрос заново.
- **Другие инструменты.** Покрыт только `bash`; `terminal`, persistent-bash, PTC,
  CUA и внутренние действия MCP-серверов через `tools/pre-execute` не проходят.
- **Исполнение вне DSH.** Журнал сессий DSH такие вызовы не видит вовсе —
  поэтому независимый сборщик (journald, shell-история, снимки docker).

## Где что лежит

| Что | Где | Права |
|---|---|---|
| Исходники плагина | `tools/dsh-agent-command-guard/` | в репозитории |
| Политика запретов | `~/.dsh/policies/deny-commands.json` | 0600 (каталог 0700) |
| Окно согласия | `~/.dsh/policies/consent/<session-id>.json` | 0600 |
| Журнал плагина | `~/.dsh/ledger/agent-commands.ndjson` | 0600 (каталог 0700) |
| Независимый ledger | `~/.dsh/ledger/ledger.ndjson` | 0600 |
| Журнал шагов workflow | `~/.dsh/ledger/workflow.ndjson` | 0600 |
| Сборщик след | `~/.local/bin/dsh-command-ledger` + user-таймер (5 минут) | 0755 |

Независимый сборщик пишет то, что произошло на машине, а не то, что увидел
плагин: полный argv из journald (`dsh-subprocess-*`), новые строки
`~/.bash_history` с метками времени, снимки состояния контейнеров и образов.
Его ценность — независимость: он работает и тогда, когда плагин не установлен,
и даёт базу для «необъяснённых изменений среды».

Журнал шагов workflow (`workflow.ndjson`) пишет `tools/tracker.sh`: отметки
шагов Plane-workflow (claim, mark-review, close) и результаты сверки. Он нужен
потому, что Plane не отдаёт историю состояний.

## Связь с другими компонентами

- **[tracker.md](tracker.md)** — сверка шагов Plane-workflow перед закрытием
  задачи и метка `type:governance-violation` для публичного record'а.
- **[ci-tools.md](ci-tools.md)** — остальные guard'ы репозитория: они ловят
  намерение в git-диффе, этот компонент — исполнение.
- **[governance-docs.md](governance-docs.md)** — процессные документы, часть
  правил которых (Hard Gates) плагин делает проверяемыми.
- **[pre-commit-config.md](pre-commit-config.md)** — первый фильтр перед
  коммитом; журнал плагина заменяет догадку «наверное, никто не правил в обход».
- **[do-sh.md](do-sh.md)** — санкционированный путь для контейнеров, обход
  которого ловит правило `container-restart`, а несанкционированный запуск —
  правило `do-sh-consent`.
- **[tracker-setup.md](../../guidelines/runbooks/tracker-setup.md)** — карта
  решений по этому механизму (KARAOKE-339) и её тикеты.
- **[local-0016-command-guard-enforcement.md](../../adr/local-0016-command-guard-enforcement.md)** —
  ADR: почему enforcement сделан перехватом со следом, а не запретом на уровне
  прав.

## Changelog

- **2026-10-31** (KARAOKE-343, KARAOKE-344, KARAOKE-347): правило `do-sh-consent`
  и окно согласия, схема журнала из 12 полей (`session`, `consent`), ADR
  `local-0016`, ссылка из `AGENTS.md` Tier-1.
- **2026-10-31** (KARAOKE-339, KARAOKE-342): компонент создан. Плагин
  `agent-command-guard` (перехват `tools/pre-execute`, режимы `deny`/`ask`,
  журнал), независимый сборщик следа и его user-таймер. Источники в репозитории:
  `tools/dsh-agent-command-guard/`, `tools/dsh-command-ledger.py`,
  `tools/install-agent-command-guard.sh`.
