# Implementation Plan: Выборочный запуск CI-проверок по зонам интереса

**Branch**: `692-selective-ci-checks` | **Date**: 2026-11-01 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/692-selective-ci-checks/spec.md`
(FR-001…FR-019, SC-001…SC-009), проект решения — `.scratch/wayfinding/ci-path-filters/design.md`
(sha256 `2b3a539d9743647e55ae5985b198fcebe0a02909fbd615ffc397211d36e082f2`, 406 строк),
инвентарь зон — `.scratch/wayfinding/ci-path-filters/job-zone-inventory.md`
(sha256 `67021c40df87462b47c784d306f922ae452dbc3397eea3188c5af4f01b6b1afb`),
поведение Forgejo — `.scratch/wayfinding/ci-path-filters/forgejo-skipped-status.md`
(sha256 `6dedb501d310ffcb8909506479b5c13b19d2a90d6c755a5407b7d10e4e93fa5f`),
базовая статистика — `.scratch/wayfinding/ci-path-filters/baseline-metrics.md`
(sha256 `3bfae5c579060b694a374350a89c33bd779b000dfe3df87ae32ca67cef60aafb`).

**Note**: артефакты `.scratch/` — read-only вход, не коммитится и не является носителем правила.
Носитель правила — `config/ci/checks.yml` (R-48, FR-006).

## Summary

Правило «каждый PR гоняет все 31 проверку» заменяется на правило «проверка запускается тогда и
только тогда, когда изменение затрагивает её зону интереса» (R-49). Носитель маппинга
«правка → проверки» — машиночитаемый манифест `config/ci/checks.yml` (версия 1). Единственная
реализация правила применимости — `tools/ci-checks.py`; её используют джоб `changes` (считает
применимые проверки и публикует их как outputs), джоб `ci-summary` (сверяет «должно было
выполниться» с фактическими результатами и падает при расхождении), локальный пред-PR прогон
(R-47 в новой редакции) и гейт согласованности `tools/check-ci-checks-manifest.sh`.

Технический подход: **условия на уровне джобов**, а не внутри джобов и не сторонним action'ом
фильтрации путей. Причина — цель владельца «избежать лишних джобов»: джоб, который не стартовал,
не занимает слот раннера (раннер один, машина разработчика — ADR local-0011) и не тратит время
на setup. Агрегатный джоб `ci-summary` — единственный сигнал для merge-гейта, потому что на
Forgejo 15 состояния статуса `skipped` не существует: пропущенный джоб отдаётся как `success` +
`Has been skipped`, то есть по статус-чекам «неприменимо» неотличимо от «зелёного» (FR-003,
FR-011). «Зелёный CI» в проекте теперь означает «зелёный `ci-summary`».

## Technical Context

**Language/Version**: Python 3.12 (`tools/ci-checks.py`), Bash (`tools/check-ci-checks-manifest.sh`,
`tools/merge-pr.sh`), YAML (манифест и обе копии workflow)

**Primary Dependencies**: PyYAML 6.x (уже фактическая зависимость репозитория — импортирует
`tools/check-ssot-impact.py`; в CI ставится `pip install pyyaml`), `git` (checkout есть в каждом
джобе), PyYAML-парсер workflow для извлечения `run:`-команд (локальный прогон)

**Storage**: N/A (файловый манифест в репозитории; состояние прогона — GitHub Actions outputs)

**Testing**: конвенция `--self-test` guard'ов репозитория (чисто → `0`, нарушение → ненулевой,
нет данных → `2`) + `--self-test` самого `tools/ci-checks.py` (набор фикстур зон)

**Target Platform**: Linux, раннер на машине `nsa-i9`; CI — Forgejo 15 Actions (рабочая копия
`.forgejo/workflows/lint.yml`) и GitHub Actions (зеркало `.github/workflows/lint.yml`)

**Project Type**: internal tooling (инфраструктура CI), не продуктовый код

**Performance Goals**: джоб `changes` — ≤ 30 с (checkout `fetch-depth: 0` + один вызов скрипта);
джоб `ci-summary` — ≤ 30 с; локальный прогон — время применимых проверок (цель SC-003: PR-гейт
8.8 → 4.3 мин, тяжёлые джобы 1596 → 116 мин на окно наблюдения)

**Constraints**:
- две копии workflow обязаны оставаться синхронными (`tools/check-ci-workflow-sync.sh`);
- коды возврата guard'ов строго `0` / `1` / `2`; «нет данных» ≠ «нет нарушений» (FR-014);
- две копии workflow обязаны содержать одинаковый набор гейтовых команд — значит и одинаковый
  набор вызовов `deploy/...` (дыра, найденная в инвентаре: `check-ci-workflow-sync.sh:127-128`
  извлекает только вызовы `tools/...`);
- секреты не попадают в argv (токен Forgejo — через `curl --config -`);
- изменения в код требуют синхронной правки `knowledge/` (Knowledge SSoT);
- `AGENTS.md` — governance-правка (semver-бамп + одобрение владельца): готовится diff, но не
  применяется в этом PR.

**Scale/Scope**: 31 job-id в каждой копии workflow (33 исполнения из-за матрицы SPA `webvue3` /
`karaoke-public`) → манифест — **полный реестр job-id обеих копий: 35 записей** (31 существующий +
`direct-commits-guard`, выделенный из `knowledge-structure`, + `ci-checks-manifest-guard` +
`changes` + `ci-summary`); 3 новых джоба в каждой копии (`changes`, `ci-summary`,
`ci-checks-manifest-guard`); 1 новый guard; 1 новый файл-носитель правила; 1 новый скрипт-реализация
правила; 1 скрипт измерения после внедрения.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Проверка | Итог |
|---|---|---|
| I. Self-contained автопайплайн | не затронут — CI-инфраструктура, продуктовый пайплайн не меняется | PASS |
| II. Сырой JDBC + дифф по хэшам | не затронут | PASS |
| III. Двух-БД синхронизация | не затронут | PASS |
| IV. Async-очередь | не затронут | PASS |
| V. Двух-фронтенд | не затронут; наоборот — разделение `webvue3` / `karaoke-public` усиливается (зоны интереса разведены по инвентарю) | PASS |
| VI. Code Standards (FR-006/007/009) | новый код (`tools/ci-checks.py`, `tools/check-ci-checks-manifest.sh`) — стиль репозитория, `--self-test`, контракт кодов; per-feature документ фичи обязателен (FR-009) → `knowledge/product/features/selective-ci-checks.md` | PASS (с обязательным per-feature документом) |
| VII. Cross-Machine Setup | не затронут | PASS |
| VIII. Секреты и git-гигиена | `tools/merge-pr.sh` правится в части выборки статусов — токен по-прежнему через `--config -`, в argv не попадает | PASS |
| IX. Knowledge-first | выполнен: прочитаны `knowledge/system/infra/ci-tools.md`, `knowledge/guidelines/architecture-conventions.md` (R-04…R-48), `knowledge/system/infra/pre-commit-config.md`, `knowledge/public/onboarding.md`, ADR `local-0011`, `local-0012`, `local-0016`; проект решения и инвентарь зон — из карты решений KARAOKE-364 | PASS |
| IX.3. Subagent isolation | работа в существующей feature-ветке `692-selective-ci-checks`, отдельных worktree для параллельных PR не требуется | PASS |
| Governance 2. Внесение изменений | изменений в Конституции нет | PASS |
| Governance 4. Compliance review | секция «Complexity Tracking» ниже | PASS |

**Post-design re-check (после Phase 1)**: нарушений не выявлено; перечисленные ниже отступления
от текущей структуры CI обоснованы в Complexity Tracking и разрешены спецификацией (Assumptions:
«Состав проверок не меняется, кроме переноса одной проверки истории git в отдельную проверку»).

## Project Structure

### Documentation (this feature)

```text
specs/692-selective-ci-checks/
├── plan.md              # этот файл (/speckit.plan)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── ci-checks-manifest.md   # контракт манифеста config/ci/checks.yml
│   └── ci-checks-cli.md        # контракт CLI tools/ci-checks.py
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
config/ci/
└── checks.yml                     # НОВЫЙ — единственный носитель маппинга «правка → проверки»

tools/
├── ci-checks.py                   # НОВЫЙ — единственная реализация правила применимости
├── check-ci-checks-manifest.sh    # НОВЫЙ — гейт согласованности (манифест ↔ workflow)
├── merge-pr.sh                    # ПРАВКА — агрегатный сигнал ci-summary, пагинация, workflow_dispatch
├── check-ci-workflow-sync.sh      # ПРАВКА — сравнение вызовов deploy/... и if:-условий
├── check-direct-commits.sh        # без правок (переносится вызов в отдельный джоб)
└── fixtures/ci-checks-manifest/   # НОВЫЙ — фикстуры гейта согласованности
    ├── synced/                    #   согласованное дерево → 0
    ├── missing-check/             #   джоб без записи в манифесте → 1
    ├── divergent-condition/       #   расхождение if: между копиями → 1
    └── dead-zone/                 #   мёртвая зона → 1

.forgejo/workflows/lint.yml         # ПРАВКА — changes, ci-summary, if:-условия, needs, cron, concurrency
.github/workflows/lint.yml          # ПРАВКА — зеркало, идентично по составу

.pre-commit-config.yaml             # ПРАВКА — хук ci-checks-manifest

knowledge/
├── adr/local-0017-selective-ci-checks.md          # НОВЫЙ — ADR (FR-015)
├── guidelines/architecture-conventions.md         # ПРАВКА — R-49 + переработка R-47
├── system/infra/ci-tools.md                       # ПРАВКА — раздел правила + таблица guard'ов
├── system/infra/pre-commit-config.md              # ПРАВКА — pre-commit не меняется
├── public/onboarding.md                           # ПРАВКА — «дождаться CI (зелёный ci-summary)»
└── product/features/selective-ci-checks.md        # НОВЫЙ — per-feature документ (FR-009)

CLAUDE.md                           # ПРАВКА — синхронизация строки про CI
AGENTS.md                           # НЕ ПРАВИТЬ в этом PR — готовый diff приложен к отчёту (governance)
```

**Structure Decision**: инфраструктурная фича внутри существующих слоёв проекта — новых
модулей нет, новый каталог только `config/ci/` (машинные настройки проверок — рядом с
существующими `config/ktlint/`, `config/detekt/`, `config/frontend/`, `config/knowledge/`).
Реализация правила — один скрипт `tools/ci-checks.py`, используемый четырьмя потребителями
(FR-006, FR-007, R-48).

## Phase 0 — Research

Полный результат — в [research.md](research.md) (решения D-01…D-19). Ключевые решения:

| # | Вопрос | Решение | Основание |
|---|---|---|---|
| D-01 | Где считать применимость | джоб `changes` + `tools/ci-checks.py`, условия на уровне джобов | design §2; сторонний action отвергнут (пин по SHA + нестабильная сеть), «скрипт в каждом джобе» отвергнут (джобы всё равно стартуют) |
| D-02 | Формат манифеста | `config/ci/checks.yml`, `version: 1`, `unknown_paths: all`, `groups`, `checks[]` | design §3; решение владельца №6 |
| D-03 | Семантика glob | git-стиль: `**` — любая глубина, `*` — внутри сегмента, путь каталога без слэша = каталог целиком; без regex и отрицаний | design §3.4 |
| D-04 | База диффа | `origin/master` (`git diff origin/master...HEAD --name-only`), fallback `master`; локально — плюс рабочее дерево (staged/unstaged/untracked) | design §2.3, §5; конвенция `tools/check-ssot-impact.py:73-87` |
| D-05 | Семантика merge-гейта | единственный сигнал — статус контекста `ci-summary` == `success`; статус-чеки отдельных проверок в решении о merge не участвуют | design §4; Forgejo 15 не умеет `skipped` |
| D-06 | Пагинация статусов | `?limit=` + сверка `X-Total-Count` с числом полученных | KARAOKE-377; факт: без `limit` — 30 из 33 |
| D-07 | `workflow_dispatch` | не создаёт статус-чеков → подсказка гейта заменяется; «прогон не выполнялся» ≠ «зелёный» | KARAOKE-378 |
| D-08 | Локальный прогон | `--local [--all]`: команды извлекаются из workflow по job-id, отбор — по манифесту; pre-commit-хуки не меняются | R-47 в новой редакции; решение владельца №7 |
| D-09 | Страховка | push в master → `all=true`; cron `0 4 * * 1`; `workflow_dispatch` — полный набор по флагу | FR-005, design §6 |
| D-10 | Сериализация тяжёлых | `karaoke-public-e2e` получает явный `needs` на все тяжёлые джобы; порядок идентичен в обеих копиях и проверяется гейтом | FR-018/FR-019, SC-009 |
| D-11 | Особые джобы | `baseline-stats` → `gate: false`; `ssot-impact` ↔ `knowledge-structure` → группа `ssoT`; `check-direct-commits` → отдельный джоб `mode: always` | design §8 |
| D-12 | Гейт согласованности | новый `tools/check-ci-checks-manifest.sh` (1:1 джоб↔манифест, идентичность `if:`, непустые зоны, мёртвая зона, версия, `unknown_paths`, `--self-test`) + расширение `check-ci-workflow-sync.sh` | FR-012/FR-013, design §7 |
| D-13 | Документирование | ADR `local-0017`, R-49, переработка R-47, `ci-tools.md`, `pre-commit-config.md`, `onboarding.md`, `CLAUDE.md`; `AGENTS.md` — только diff | FR-015, design §10 |
| D-14 | Per-feature документ | `knowledge/product/features/selective-ci-checks.md` | Constitution VI (FR-009) |
| D-15 | Имена outputs джоба `changes` | `id` с заменой `-` на `_` (`backend_build`); результаты и outputs передаются в агрегат JSON'ом (`toJSON(needs)`) | в выражениях движка дефис — оператор вычитания |
| D-16 | Два источника локального прогона | список применимых — из манифеста (`tools/ci-checks.py`), список команд — из workflow по job-id | FR-007 (зеркалирование CI) + FR-006/R-48 (нет второго носителя) |
| D-17 | Расписание на v15 | cron `0 4 * * 1` без явного `ref:` и без `timezone:`; требование v16+ (`${{ forgejo.ref }}` в scheduled-контексте) зафиксировано как follow-up | живой сервер v15.0.10; обновление до v17.0.1+ — KARAOKE-379 |
| D-18 | Носитель следа пропуска | агрегат печатает перечень неприменимых с зонами в лог и в step output; гейт **пересчитывает** применимость сам (`--list`); `permissions: statuses: write` не расширяем | FR-017/SC-005; логи джобов на v15 недоступны через API (404) — гейту нужен список, а он получает его из манифеста и диффа |
| D-19 | Классы записей манифеста | манифест — полный реестр job-id обеих копий (**35 записей**), инвариант «джоб ↔ запись» тотальный без исключений; два класса: `check` и `infrastructure` (`mode: always`, без зон) | R-47 п. 3 требует отдельного джоба для нового guard'а; списки исключений — ещё один список, который надо синхронизировать |

## Phase 1 — Design & Contracts

- **Data model** — [data-model.md](data-model.md): сущности «Проверка», «Зона интереса»,
  «Манифест», «Группа», «Изменение», «Прогон», «Агрегированный сигнал» + инварианты.
- **Contracts** —
  - [contracts/ci-checks-manifest.md](contracts/ci-checks-manifest.md): схема `config/ci/checks.yml`,
    семантика полей и glob, коды возврата при неизвестной версии.
  - [contracts/ci-checks-cli.md](contracts/ci-checks-cli.md): интерфейс `tools/ci-checks.py`
    (режимы `--emit-outputs`, `--summary`, `--local [--all]`, `--self-test`), формат входа/выхода,
    контракт кодов `0/1/2`.
- **Quickstart** — [quickstart.md](quickstart.md): воспроизводимые сценарии проверки фичи
  (локальный прогон применимых проверок, сверка манифеста и workflow, проверка гейта,
  имитация прогона в фикстурах).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Два новых джоба в workflow (`changes`, `ci-summary`) вместо правки существующих 31 | `changes` — единственный носитель вычисления применимости в прогоне; `ci-summary` — единственный носитель сигнала «что должно было выполниться» | Вычисление применимости внутри каждого джоба: 31 джоб всё равно стартует, слот раннера занят, цель «избежать лишних джобов» не достигнута (design §2.1) |
| `ci-summary` читает `needs.*.result` (внутренний API раннера) | Forgejo 15 не умеет статус `skipped` → применимость невыразима в статус-чеках; альтернатива — статус-чек с нестандартным описанием, но он всё равно `success` | Список «допустимых» статусов в гейте: по факту 365 пропущенный джоб = `success` + `Has been skipped` → применимость из статусов не восстанавливается |
| Выделение `check-direct-commits` из `knowledge-structure` в отдельный джоб | Проверка работает с историей git, а не с файлами → по FR-008 не пропускается никогда; внутри `knowledge-structure` пропуск джоба выключил бы и её | Оставить внутри: FR-008 нарушается; `mode: always` без отдельного джоба технически невыразим |
| Расширение `check-ci-workflow-sync.sh` (вызовы `deploy/...`, сравнение `if:`) | Две копии workflow обязаны быть эквивалентны; сейчас контролируется только состав вызовов `tools/...`, а условия запуска не контролируются вовсе | Оставить как есть: FR-006/FR-019 не выполняются — расхождение условий проходит незамеченным |
| `config/ci/checks.yml` как отдельный файл, а не секция в workflow | FR-006: манифест — единственный носитель правила; манифест нужен локальному прогону и гейту согласованности, которые не читают workflow | Правило в каждой копии workflow: два носителя → расхождение (ровно то, что фиксирует FR-006) |

## Риски и открытые вопросы

| Риск | Источник | Обработка |
|---|---|---|
| Обе копии workflow разъедутся по `if:`/`needs:` | FR-019 | гейт согласованности сверяет условия и `needs` по job-id; расширенный sync-гейт сверяет вызовы `deploy/...` |
| Мёртвая зона (glob не матчит ни одного tracked-файла) | FR-013 | гейт согласованности: ошибка; зоны берутся из инвентаря KARAOKE-366 с доказательством |
| Путь вне объединения зон → полный набор | FR-004 | `unknown_paths: all`; гейт докладывает список таких путей (диагностика, не ошибка) |
| Перенос шага `check-direct-commits` меняет состав проверок | Assumptions спеки | перенос разрешён спецификацией; проверка остаётся той же командой, только в отдельном джобе |
| «Зелёный CI» меняет смысл | design §4 | ADR `local-0017` + R-49 + `ci-tools.md`; `AGENTS.md` — governance-правка, готовится diff и передаётся владельцу |
| Расписание не сработает как ожидается на v15.0.10 | release notes Forgejo 16.0.0 (PR 13081): в scheduled-контексте правильный ref — `${{ forgejo.ref }}`; `timezone` в расписаниях поддерживается с v16 и в v17 есть | cron без явного `ref:` и без `timezone:` (D-17); для расписания всегда `all=true`, поэтому ref на применимость не влияет; проверка фактического поведения — follow-up T059 к KARAOKE-379 (обновление до v17.0.1+) |
| След пропуска недоступен гейту и измерителю | FR-017/SC-005; логи джобов через API на v15 недоступны (404) | D-18: агрегат печатает перечень в лог и в step output, а гейт **пересчитывает** применимость сам из манифеста (`--list`) — независимый источник истины; при отсутствии данных гейт отказывает (код `2` ≠ «зелёно») |
