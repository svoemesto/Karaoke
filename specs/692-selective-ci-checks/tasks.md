---

description: "Task list for selective CI checks by zone of interest (spec 692)"
---

# Tasks: Выборочный запуск CI-проверок по зонам интереса

**Input**: Design documents from `/specs/692-selective-ci-checks/`
(`spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/ci-checks-manifest.md`,
`contracts/ci-checks-cli.md`, `quickstart.md`)

**Branch**: `692-selective-ci-checks` | **Issue**: KARAOKE-363 (карта решений KARAOKE-364)

**Tests**: в проекте тесты guard'ов — это `--self-test` с фикстурами (R-47 п. 3), поэтому отдельной
фазы «Tests» нет; фикстуры идут вместе с реализацией каждой задачи и проверяются командами,
указанными в её описании.

**Порядок фаз** — по решению лида (порядок работ), с оговорёнными отступлениями от порядка
приоритетов историй:

1. манифест → 2. реализация правила → 3. гейт согласованности → 4. workflow ×2 → 5. merge-гейт
→ 6. локальный прогон → 7. страховка → 8. знания и ADR.

Фаза 3 (User Story 4, P2) идёт раньше фаз историй P1: гейт согласованности — инфраструктура,
которая проверяет согласие манифеста и обеих копий workflow, и должна быть готова к моменту, когда
копии начнут меняться. Между фазами 3 и 4 гейт на реальном дереве честно красный (31 job-id против
35 записей манифеста) — это ожидаемое промежуточное состояние; «зеленеет» он задачей T029.

**Формат**: `- [ ] [ID] [P?] [Story?] описание с путём файла` + `→ проверка: <команда>`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: носитель правила — манифест зон.

- [X] T001 Создать `config/ci/checks.yml` — каркас манифеста: `version: 1`, `unknown_paths: all`,
      `groups: {}`, `checks: []`, комментарий-шапка «единственный носитель маппинга „правка →
      проверки“; правка зоны требует ссылки на доказательство (evidence)».
      → проверка: `python3 -c "import yaml; d=yaml.safe_load(open('config/ci/checks.yml')); print(d['version'], d['unknown_paths'])"` → `1 all`
- [X] T002 Наполнить `config/ci/checks.yml` **35 записями** — полный реестр job-id обеих копий
      workflow (D-19, инвариант «джоб ↔ запись» **тотальный, без исключений**): 31 существующий
      job-id из `.forgejo/workflows/lint.yml` + `direct-commits-guard` (T028) +
      `ci-checks-manifest-guard` (T060) + `changes` (T022) + `ci-summary` (T024). Для каждой
      записи — `id`, `class`, `title`, `gate`, `cost`, `mode`, `paths`, `evidence` (ссылка вида
      `lint.yml:270; tools/…:44`).
      **Два класса записей** (contracts/ci-checks-manifest.md §3.1): проверки контура —
      `class: check` (по умолчанию), 31 существующий job-id + `direct-commits-guard`; служебные
      джобы — `class: infrastructure`: `changes` (`gate: false`, `cost: info`), `ci-summary`
      (`gate: false`, `cost: info`), `ci-checks-manifest-guard` (`gate: true`, `cost: light`) — у
      них `mode: always`, зоны и `evidence` пустые, «мёртвая зона» к ним не применяется.
      Обязательные решения из плана: группа `ssoT` = [`ssot-impact`, `knowledge-structure`];
      `baseline-stats` → `gate: false`, `cost: info`; `direct-commits-guard` → `mode: always`
      (FR-008); `secret-in-argv-guard` → `**/*.sh`, `**/*.bash`;
      `docker-image-tags-guard` → `**/Dockerfile*`; `personal-fixtures-guard` → `**/*.mlt`,
      `**/*.kdenlive` + каталоги ресурсов; `root-junk-guard` → зона `/*` (проверка на
      **отсутствие** мусора в корне — зона выражает место, где он мог бы появиться; см.
      contracts/ci-checks-manifest.md §4); зоны `docs/**` и «отсутствующих» путей в манифест
      **не входят** (их ловит `unknown_paths: all`).
      → проверка: `python3 -c "import yaml; d=yaml.safe_load(open('config/ci/checks.yml')); print(len(d['checks']))"` → `35`, и сверка id со списком job-id: `grep -E '^  [a-z0-9-]+:$' .forgejo/workflows/lint.yml | sed -E 's/^  ([a-z0-9-]+):$/\1/' | sort > /tmp/jobs.txt; grep -E '^  - id:' config/ci/checks.yml | sed -E 's/^  - id: //' | sort > /tmp/ids.txt; diff /tmp/jobs.txt /tmp/ids.txt` → после T028/T060/T022/T024 расхождений нет

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: единственная реализация правила применимости.

**⚠️ CRITICAL**: ни одна пользовательская история не может быть реализована до завершения этой фазы.

- [X] T003 Создать `tools/ci-checks.py` — каркас CLI: argparse (`--manifest`, `--workflow`,
      `--base`, `--json`, `--all`), взаимоисключающие режимы `--emit-outputs` / `--summary` /
      `--local` / `--self-test`, коды возврата `0` / `1` / `2`, docstring с контрактом и ссылкой на
      `specs/692-selective-ci-checks/contracts/ci-checks-cli.md`.
      → проверка: `python3 tools/ci-checks.py --help` → rc=0, в списке все четыре режима
- [X] T004 [P] `tools/ci-checks.py` — загрузка и валидация манифеста: `version` (неизвестная →
      rc=2), `unknown_paths`, `groups`, `checks`; нечитаемый/невалидный YAML → rc=2
      («проверка не выполнена», FR-014).
      → проверка: `python3 tools/ci-checks.py --manifest /nonexistent.yml --emit-outputs; echo rc=$?` → `2`
- [X] T005 [P] `tools/ci-checks.py` — матчинг глобов: `**`, `*`, каталог без слэша, ведущий `/`
      (привязка к корню); функция должна быть чистой и покрытой фикстурами T-1…T-4.
      → проверка: `python3 tools/ci-checks.py --self-test` (после T009) → rc=0
- [X] T006 `tools/ci-checks.py` — вычисление изменённых путей: `git diff <base>...HEAD --name-only`
      (база `origin/master`, fallback `master`) + рабочее дерево (staged, unstaged, untracked);
      union, нормализация, переименование = оба пути (C-1); при отсутствии базы/истории → `all=true`.
      → проверка: `python3 tools/ci-checks.py --emit-outputs | head -3` → `all=…` и ключи проверок
- [X] T007 `tools/ci-checks.py` — вычисление применимости: `mode: paths` (зона = объединение глобов),
      `mode: always` (всегда применима), группы (применимость любого члена → применимы все),
      `unknown_paths: all` (путь вне зон → применимо всё), флаг полного набора.
      → проверка: `python3 tools/ci-checks.py --emit-outputs | grep -E '^(all|ssot_impact|knowledge_structure)='` → согласованная пара `ssot_impact`/`knowledge_structure`
- [X] T008 `tools/ci-checks.py --emit-outputs` — печать `all=<bool>` и по ключу на проверку
      (`id` с заменой `-` на `_`), значения `true`/`false` (D-15).
      → проверка: `python3 tools/ci-checks.py --emit-outputs | grep -c '='` → число ключей = 1 + 32
- [X] T009 `tools/ci-checks.py --self-test` — 10 фикстур контракта CLI §6 (T-1…T-10): глоб-совпадения
      и не-совпадения, зона вне изменений, группа `ssoT`, `mode: always`, `unknown_paths: all`,
      неизвестная версия манифеста → rc=2, `--summary` с применимой проверкой без результата → rc=1.
      **Плюс две фикстуры по критериям успеха** (SC-001, SC-002):
      (1) правка только в `knowledge/` и в `specs/` → применимы только лёгкие (`docs-lint`,
      `knowledge-structure`, `ssot-impact` через группу `ssoT`), **тяжёлых — ноль**;
      (2) правка в `tools/`/`config/`/`deploy/` → тяжёлые продуктовые (`backend-build`,
      `backend-test`, `frontend-build`, `karaoke-public-e2e`) **неприменимы**.
      → проверка: `python3 tools/ci-checks.py --self-test; echo rc=$?` → `0`

**Checkpoint**: правило реализовано и покрыто фикстурами; можно переходить к гейту и workflow.

---

## Phase 3: User Story 4 — Правило живёт в одном месте и не расходится между копиями (Priority: P2)

**Goal**: манифест — единственный носитель правила; расхождение манифеста и CI-конфигурации
обнаруживается до слияния.

**Independent Test**: изменить условие запуска в одной копии CI-конфигурации → проверка
согласованности падает; добавить проверку без записи в манифест → падает; задать мёртвую зону →
падает.

### Implementation for User Story 4

- [X] T010 [P] [US4] Создать `tools/check-ci-checks-manifest.sh` — каркас guard'а: шапка с
      контрактом кодов (`0`/`1`/`2`), опции `--root <dir>`, `--quiet`, `--self-test`; разбор YAML
      через `python3` + PyYAML; недоступные данные (нет git, нет PyYAML, нет файла) → rc=2.
      → проверка: `bash tools/check-ci-checks-manifest.sh --help` → rc=0
- [X] T011 [US4] `tools/check-ci-checks-manifest.sh` — проверка 1:1 «джоб ↔ запись манифеста»: джобы
      извлекаются из обеих копий workflow; соответствие **тотальное, без списков исключений**
      (M-2): расхождение в любую сторону → rc=1.
      → проверка: `bash tools/check-ci-checks-manifest.sh --root tools/fixtures/ci-checks-manifest/missing-check; echo rc=$?` → ≠0
- [X] T012 [US4] `tools/check-ci-checks-manifest.sh` — проверка идентичности `if:`-условий **и**
      `needs` по job-id между двумя копиями (FR-019: порядок выполнения обязан быть одинаковым и
      проверяться гейтом; инвариант M-12); расхождение → rc=1.
      → проверка: `bash tools/check-ci-checks-manifest.sh --root tools/fixtures/ci-checks-manifest/divergent-condition; echo rc=$?` → ≠0
- [X] T013 [US4] `tools/check-ci-checks-manifest.sh` — проверка зон: `paths` непустой при
      `mode: paths`; `evidence` непустой; каждый глоб матчит хотя бы один tracked-файл (мёртвая зона
      → ошибка, FR-013) — **проверка применяется только к записям с `mode: paths`**;
      `mode: always` ⇒ без `paths`; `class: infrastructure` ⇒ `mode: always` и без зон (M-9).
      → проверка: `bash tools/check-ci-checks-manifest.sh --root tools/fixtures/ci-checks-manifest/dead-zone; echo rc=$?` → ≠0
- [X] T014 [US4] `tools/check-ci-checks-manifest.sh` — проверка `version`, `unknown_paths`,
      `gate: false` ⇒ `cost: info`, уникальности `id`, членов групп и «проверка — максимум в одной
      группе».
      → проверка: `bash tools/check-ci-checks-manifest.sh --root tools/fixtures/ci-checks-manifest/synced; echo rc=$?` → `0`
- [X] T015 [P] [US4] Создать фикстуру `tools/fixtures/ci-checks-manifest/synced/` — согласованное
      дерево: `tools/fixtures/ci-checks-manifest/synced/config/ci/checks.yml` + обе копии
      workflow `.forgejo/workflows/lint.yml` и `.github/workflows/lint.yml` (зоны манифеста матчат
      tracked-файлы фикстуры).
      → проверка: `bash tools/check-ci-checks-manifest.sh --root tools/fixtures/ci-checks-manifest/synced; echo rc=$?` → `0`
- [X] T016 [P] [US4] Создать фикстуру `tools/fixtures/ci-checks-manifest/missing-check/` — джоб без
      записи в манифесте: `tools/fixtures/ci-checks-manifest/missing-check/config/ci/checks.yml`
      (на один job-id меньше, чем в workflow-фикстуре).
      → проверка: `bash tools/check-ci-checks-manifest.sh --root tools/fixtures/ci-checks-manifest/missing-check; echo rc=$?` → ≠0
- [X] T017 [P] [US4] Создать фикстуру `tools/fixtures/ci-checks-manifest/divergent-condition/` —
      расхождение `if:`-условий между копиями
      `tools/fixtures/ci-checks-manifest/divergent-condition/.forgejo/workflows/lint.yml` и
      `.../.github/workflows/lint.yml`.
      → проверка: `bash tools/check-ci-checks-manifest.sh --root tools/fixtures/ci-checks-manifest/divergent-condition; echo rc=$?` → ≠0
- [X] T018 [P] [US4] Создать фикстуру `tools/fixtures/ci-checks-manifest/dead-zone/` — глоб, не
      матчащий ни одного tracked-файла:
      `tools/fixtures/ci-checks-manifest/dead-zone/config/ci/checks.yml`.
      → проверка: `bash tools/check-ci-checks-manifest.sh --root tools/fixtures/ci-checks-manifest/dead-zone; echo rc=$?` → ≠0
- [X] T019 [US4] `tools/check-ci-checks-manifest.sh --self-test` — прогон четырёх фикстур:
      `synced` → 0, `missing-check` / `divergent-condition` / `dead-zone` → ненулевой код; плюс
      третья фикстура «данные недоступны» (например, `--root` на каталоге без workflow) → rc=2.
      → проверка: `bash tools/check-ci-checks-manifest.sh --self-test; echo rc=$?` → `0`
- [X] T020 [P] [US4] Расширить `tools/check-ci-workflow-sync.sh` — извлечение вызовов
      инструментов репозитория расширить с `tools/...` на `deploy/...` (дыра: вызовы
      `deploy/heap-dump-retention.sh` и `deploy/admin-ip-allowlist.sh` сейчас не контролируются) и
      добавить сверку `if:`-условий по job-id между копиями; фикстуры
      `tools/fixtures/ci-workflow-sync/` расширить негативным случаем расхождения.
      → проверка: `bash tools/check-ci-workflow-sync.sh; echo rc=$?` → `0`
- [X] T021 [US4] `.pre-commit-config.yaml` — зарегистрировать хук `ci-checks-manifest`
      (`entry: bash tools/check-ci-checks-manifest.sh --quiet`, `language: system`,
      `pass_filenames: false`, `files: '^(config/ci/checks\.yml|\.forgejo/workflows/|\.github/workflows/)'`).
      → проверка: `grep -A6 'id: ci-checks-manifest' .pre-commit-config.yaml` → блок хука присутствует

---

## Phase 4: User Story 1 — Правка только документации не тянет тяжёлые проверки (Priority: P1) 🎯 MVP

**Goal**: в CI появляются джоб `changes` (считает применимость) и агрегат `ci-summary` (единственный
сигнал прогона); проверки получают условие запуска по зоне интереса.

**Independent Test**: PR с правкой одного файла в `knowledge/` → тяжёлые джобы не стартуют,
агрегированный сигнал зелёный.

### Implementation for User Story 1

- [X] T022 [US1] `.forgejo/workflows/lint.yml` — джоб `changes`: checkout с `fetch-depth: 0`,
      шаг `id: applicable` с `run: python3 tools/ci-checks.py --manifest config/ci/checks.yml --emit-outputs`,
      блок `outputs:` — `all` + по одному выходу на каждую проверку (имя = `id` с `_`, D-15).
      → проверка: `python3 -c "import yaml; d=yaml.safe_load(open('.forgejo/workflows/lint.yml')); print(sorted(d['jobs']['changes']['outputs'])[:3])"` → ключи с `_`
- [X] T023 [US1] `.forgejo/workflows/lint.yml` — для каждой из **32 проверок контура** (31
      существующая + `direct-commits-guard` из T028) добавить `needs: changes` и условие
      `if: needs.changes.outputs.all == 'true' || needs.changes.outputs.<id_с_underscore> == 'true'`;
      ровно одно условие на проверку, без исключений. Три **инфраструктурных** джоба
      (`changes`, `ci-summary`, `ci-checks-manifest-guard`) условие не получают: `changes` считает
      применимость, `ci-summary` агрегирует (`if: always()`), гейт манифеста запускается всегда.
      → проверка: `grep -c "needs.changes.outputs" .forgejo/workflows/lint.yml` → 32
- [X] T024 [US1] `.forgejo/workflows/lint.yml` — джоб `ci-summary`: `needs: [changes, …все проверки,
      ci-checks-manifest-guard]`, `if: always()`, `env: CI_CHEKS_OUTPUTS: ${{ toJSON(needs.changes.outputs) }}`
      и `CI_CHEKS_RESULTS: ${{ toJSON(needs) }}`, шаг
      `run: python3 tools/ci-checks.py --manifest config/ci/checks.yml --summary`.
      → проверка: `grep -A6 'ci-summary:' .forgejo/workflows/lint.yml` → блок с `toJSON(needs)`
- [X] T025 [US1] `.forgejo/workflows/lint.yml` — триггер `schedule:` с `cron: '0 4 * * 1'`
      (еженедельно, понедельник 04:00) и отдельная `concurrency`-группа для расписания с
      `cancel-in-progress: false` (FR-005, страховка не отменяется обычным прогоном).
      **Не полагаться на трактовку `ref` в scheduled-контексте:** явный `ref:` в checkout для
      расписания не задавать (живой сервер — v15.0.10; для полного набора ref на применимость не
      влияет — там `all=true`). В комментарии к триггеру зафиксировать требование Forgejo v16
      («use proper `${{ forgejo.ref }}` in scheduled workflows», release notes 16.0.0, PR 13081) как
      follow-up к KARAOKE-379; поле `timezone:` сейчас **не добавлять** (на v15 может не приниматься
      валидатором workflow) — тоже follow-up к KARAOKE-379.
      → проверка: `grep -A2 'schedule:' .forgejo/workflows/lint.yml` → cron `0 4 * * 1`, и `grep -n 'forgejo.ref' .forgejo/workflows/lint.yml` → только в комментарии, не в `run:`/`with:`
- [X] T026 [US1] `.github/workflows/lint.yml` — зеркало T022–T025: тот же состав джобов, те же
      `if:`/`needs`, та же cron-строка; различаться могут только обёртка (checkout-URL, `concurrency`).
      → проверка: `bash tools/check-ci-workflow-sync.sh; echo rc=$?` → `0`
- [X] T027 [US1] `tools/ci-checks.py --summary` — режим агрегата: читает `CI_CHEKS_OUTPUTS` и
      `CI_CHEKS_RESULTS`, применяет правила S-1…S-7 (блокирующий набор = записи с `gate: true`;
      `changes` падает → красный независимо от `gate`; `ci-summary` сам не оценивается), печатает
      перечень применимых проверок с результатами и перечень неприменимых с указанием зон (след
      пропуска, FR-017), выводит `SUMMARY: green|red`; перечень неприменимых дублируется в
      **step output** джоба (`skipped_checks`) — носители следа по D-18.
      → проверка: четыре сценария из `quickstart.md` §5 → rc `0`, `1`, `1`, `1`
- [X] T028 [US1] `.forgejo/workflows/lint.yml` и `.github/workflows/lint.yml` — вынести шаг
      `bash tools/check-direct-commits.sh` (+ его `--self-test`) из джоба `knowledge-structure` в
      отдельный джоб `direct-commits-guard` с `needs: changes`, тем же условием запуска и
      `mode: always` в манифесте (FR-008: проверка истории git не пропускается никогда).
      → проверка: `grep -c 'check-direct-commits.sh' .forgejo/workflows/lint.yml` → шаг встречается только в джобе `direct-commits-guard`
- [X] T029 [US1] Согласованность `config/ci/checks.yml` и обеих копий workflow — гейт должен быть
      зелёным на реальном дереве (после T023, T024, T026, T028, **T060**): **35 записей ↔ 35
      job-id** (32 проверки контура + 3 инфраструктурных: `changes`, `ci-summary`,
      `ci-checks-manifest-guard`) — инвариант тотальный, без исключений.
      → проверка: `bash tools/check-ci-checks-manifest.sh; echo rc=$?` → `0`

**Checkpoint**: User Story 1 полностью функционален — прогон различает применимые и неприменимые
проверки и даёт агрегированный сигнал.

---

## Phase 5: User Story 2 — Правка одного фронтенда не тянет другой фронтенд и бэкенд (Priority: P1)

**Goal**: зоны фронтендов разведены; тяжёлая проверка e2e выполняется последовательно с остальными
тяжёлыми (FR-018/FR-019).

**Independent Test**: PR с правкой одного файла в `webvue3/src/` → в прогоне нет джобов бэкенда и
публичного SPA; e2e не выполняется одновременно ни с одной тяжёлой проверкой.

### Implementation for User Story 2

- [X] T030 [US2] `config/ci/checks.yml` — развести зоны фронтендов: `webvue3/**` только у
      `frontend-lint`, `frontend-build`, `webvue3-unit-tests`, `confirm-escape-guard`,
      `marker-escape-guard`, `jsdoc-coverage`; `karaoke-public/**` — у `frontend-lint`,
      `frontend-build`, `karaoke-public-unit-tests`, `karaoke-public-e2e`, `jsdoc-coverage`;
      зоны бэкенда — у `backend-lint`, `backend-build`, `backend-test`, `kdoc-coverage`,
      `no-jpa-imports-guard`, `value-placeholders-guard`, `no-mp4-mentions-guard`,
      `file-permissions-guard`, `personal-fixtures-guard`, `ssot-impact`.
      → проверка: `python3 -c "import yaml; d=yaml.safe_load(open('config/ci/checks.yml')); [print(c['id'], c.get('paths')) for c in d['checks'] if c['id'] in ('frontend-lint','webvue3-unit-tests','karaoke-public-unit-tests')]"` → разные зоны
- [X] T031 [US2] `config/ci/checks.yml` — зона `karaoke-public-e2e` = `karaoke-public/**` +
      `deploy/karaoke-public/Dockerfile`, `cost: heavy` (D-10: общий docker-демон и фиксированный
      порт 17907 — разделяемый ресурс).
      → проверка: `grep -A9 'id: karaoke-public-e2e' config/ci/checks.yml` → зона и `cost: heavy`
- [X] T032 [US2] `.forgejo/workflows/lint.yml` и `.github/workflows/lint.yml` — `karaoke-public-e2e`
      получает явный `needs` на все тяжёлые джобы (`cost: heavy` из манифеста): e2e стартует только
      после их завершения; порядок идентичен в обеих копиях (FR-018/FR-019, SC-009).
      → проверка: `grep -A3 'karaoke-public-e2e:' .forgejo/workflows/lint.yml` → `needs` со всеми тяжёлыми; то же в `.github/`
- [X] T033 [US2] `tools/check-ci-checks-manifest.sh` — инвариант M-11: `needs` джоба
      `karaoke-public-e2e` покрывает все проверки с `cost: heavy` из манифеста, в обеих копиях
      (нарушение → rc=1).
      → проверка: `bash tools/check-ci-checks-manifest.sh; echo rc=$?` → `0`
- [X] T034 [US2] `tools/ci-checks.py --self-test` — фикстуры раздельных зон: правка в `webvue3/`
      активирует `frontend-lint`/`frontend-build`/`webvue3-unit-tests` и **не** активирует
      `backend-build`/`karaoke-public-unit-tests`; правка в `karaoke-public/` — симметрично.
      → проверка: `python3 tools/ci-checks.py --self-test; echo rc=$?` → `0`

**Checkpoint**: User Story 2 полностью функционален.

---

## Phase 6: User Story 3 — Гейт слияния различает «применимо и зелёно», «неприменимо» и «не отработало» (Priority: P1)

**Goal**: решение о merge принимается по агрегированному сигналу `ci-summary`; гейт не обманывается
усечённой выборкой статусов и ручным перезапуском прогона.

**Independent Test**: смоделировать прогон с красной применимой проверкой, с зелёной применимой, с
неприменимой и с неотработавшей — гейт отказывает только в первом и четвёртом случаях.

### Implementation for User Story 3

- [X] T035 [US3] `tools/merge-pr.sh` — решение о merge принимается по **единственному**
      агрегированному сигналу: статус-чек контекста `ci-summary` со статусом `success`; статус-чеки
      отдельных проверок в решении о merge не участвуют (FR-010, D-05); отсутствие сигнала → отказ
      «прогон не отработал», а не merge.
      **Правило выбора контекста** (Forgejo формирует контекст как `Lint / <имя джоба> (<событие>)`,
      поэтому на один коммит приходится до двух контекстов `ci-summary`: от push ветки и от PR):
      гейт собирает **все** контексты, содержащие `ci-summary`; **ни одного такого контекста →
      отказ** («прогон не отработал»); **любой из них не `success` → отказ** с указанием имени
      красного контекста. Приоритета между `(pull_request)` и `(push)` нет — это ослабило бы гейт
      по сравнению с сегодняшним требованием `success` по всем статус-чекам коммита.
      → проверка: `bash tools/merge-pr.sh 41 --dry-run` → выводит состояние сигнала `ci-summary`
- [X] T036 [US3] `tools/merge-pr.sh` — пагинация выборки статусов: **постраничный** сбор — цикл по
      `page` (с `limit`), пока не собрано `X-Total-Count` контекстов; сверка числа полученных с
      заголовком `X-Total-Count`; расхождение → отказ (KARAOKE-377: без `limit` API отдаёт 30
      контекстов из 33, а при росте числа контекстов одного запроса с `limit=100` уже не хватит).
      → проверка: фикстура из T039 + `grep -n 'X-Total-Count' tools/merge-pr.sh` → виден цикл по `page`
- [X] T037 [US3] `tools/merge-pr.sh` — `workflow_dispatch` не создаёт статус-чеков → подсказка
      «перезапусти прогон» заменена на «ручной перезапуск прогон зелёным не делает; нужен новый
      push/PR» (KARAOKE-378).
      → проверка: `grep -n 'workflow_dispatch' tools/merge-pr.sh` → в тексте подсказки
- [X] T038 [US3] `tools/merge-pr.sh` — манифест `config/ci/checks.yml` гейтом используется
      **только для диагностики**: перечень ожидаемых проверок, перечисление пропущенных. Решение о
      слиянии принимается **по `ci-summary`**; статус-чеки отдельных проверок в решении **не
      участвуют** (D-05), зелёного статуса по каждой проверке из манифеста гейт **не требует**.
      Согласованность манифеста и workflow ловит гейт согласованности — гейту merge знать о нём
      не нужно.
      → проверка: `grep -n 'checks.yml' tools/merge-pr.sh` → манифест упомянут в диагностической части
- [X] T039 [US3] `tools/merge-pr.sh --self-test` — вынести разбор выборки статусов и выбор
      контекста `ci-summary` в отдельные функции и добавить фикстуры: (1) **более 30 статусов**,
      где красная проверка «теряется» в усечённом окне — полный обход обязан её увидеть и отказать
      (SC-006); (2) ни одного контекста `ci-summary` → отказ «прогон не отработал»; (3) два
      контекста `ci-summary`, один не `success` → отказ с именем красного контекста.
      → проверка: `bash tools/merge-pr.sh --self-test; echo rc=$?` → `0`

**Checkpoint**: User Story 3 полностью функционален.

---

## Phase 7: User Story 5 — Местный пред-PR прогон совпадает с CI (Priority: P2)

**Goal**: перед PR гоняются команды применимых проверок — список применимых из манифеста, список
команд из workflow по job-id (D-16, R-47 в новой редакции).

**Independent Test**: на одном и том же изменении сравнить список применимых проверок локально и
в прогоне — списки совпадают полностью.

### Implementation for User Story 5

- [X] T040 [US5] `tools/ci-checks.py --local` — список применимых проверок из манифеста (та же
      реализация правила) + список команд из workflow по job-id (`jobs.<id>.steps[*].run`),
      уважение `working-directory`, разворот матрицы на оба значения (`webvue3`,
      `karaoke-public`), фильтрация только guard-команд (`bash tools/`, `python3 tools/`,
      `python tools/`, `./tools/`) — та же фильтрация, что в нынешнем R-47.
      → проверка: `python3 tools/ci-checks.py --local --help` → rc=0; в выводе видно, что для `frontend-lint` развёрнуты оба SPA
- [X] T041 [US5] `tools/ci-checks.py --local` — исполнение команд по порядку, отчёт по каждой
      проверке; код возврата `0`/`1`/`2`; `--all` — полный набор независимо от применимости
      (страховка). **Термины берутся из словаря** [data-model.md §6](data-model.md) — «применимая
      проверка», «неприменимая проверка» («это не „пройдена“ и не „зелёная“»), «не отработало»,
      «блокирующий набор»; собственных терминов вроде `APPLICABLE`/`SKIPPED` в отчёте не
      заводим — словарь один.
      → проверка: `python3 tools/ci-checks.py --local; echo rc=$?` → только применимые проверки исполнены
- [X] T042 [US5] Сверка списков «локально ↔ CI» средствами `tools/ci-checks.py` — правило считается
      одной реализацией (FR-007). Набор фикстур **из не менее чем 10 категорий правок** (SC-007):
      `knowledge/`, `specs/`, `webvue3/`, `karaoke-public/`, бэкенд (`karaoke-app/`, `karaoke-web/`),
      gradle-конфиги (`build.gradle.kts`, `settings.gradle.kts`, `gradle/`), `deploy/`, `tools/`,
      `config/ci/`, смешанная правка. Для каждой категории — список применимых проверок с двух
      сторон; расхождений быть не должно.
      → проверка: `python3 tools/ci-checks.py --emit-outputs | sort > /tmp/ci_list.txt; python3 tools/ci-checks.py --local --list | sort > /tmp/local_list.txt; diff /tmp/ci_list.txt /tmp/local_list.txt` → расхождений нет (для каждой из 10 категорий)

**Checkpoint**: User Story 5 полностью функционален.

---

## Phase 8: User Story 6 — Страховка: полный набор по расписанию и при попадании в главную ветку (Priority: P3)

**Goal**: полный набор проверок выполняется при пуше в `master`, по расписанию и при ручном
полном прогоне — независимо от того, что было пропущено в промежуточных PR.

**Independent Test**: дождаться попадания в главную ветку и убедиться, что выполнены все проверки.

### Implementation for User Story 6

- [X] T043 [US6] `tools/ci-checks.py` — режим полного набора по событию: `push` в `master`,
      `schedule`, ручной `workflow_dispatch` с `full=true` → `all=true` (дифф не считается);
      явный флаг `--all` сохраняется. Источники события — env раннера (`GITHUB_EVENT_NAME`,
      `GITHUB_REF`, `GITHUB_BASE_REF`) и вход `full`, передаваемый в джоб `changes` через env.
      → проверка: `GITHUB_EVENT_NAME=schedule python3 tools/ci-checks.py --emit-outputs | head -1` → `all=true`
- [X] T044 [US6] `.forgejo/workflows/lint.yml` и `.github/workflows/lint.yml` — `workflow_dispatch`
      с входом `full` (boolean, default `false`); вход передаётся в джоб `changes` через env.
      → проверка: `grep -A6 'workflow_dispatch' .forgejo/workflows/lint.yml` → вход `full` присутствует
- [X] T045 [US6] `.forgejo/workflows/lint.yml` и `.github/workflows/lint.yml` — `concurrency`:
      обычная группа — по ref с `cancel-in-progress: true` (как сейчас), отдельная группа для
      расписания и e2e — `cancel-in-progress: false` (полный прогон не отменяется обычным и наоборот).
      → проверка: `grep -n -A3 'concurrency' .forgejo/workflows/lint.yml` → есть группа с `cancel-in-progress: false`
- [X] T046 [US6] Проверка полного набора на фикстуре `tools/ci-checks.py --self-test`: при `all=true`
      применимы все записи манифеста (35) независимо от зон.
      → проверка: `python3 tools/ci-checks.py --self-test; echo rc=$?` → `0` (фикстура «all=true → применимы все»)

**Checkpoint**: User Story 6 полностью функционален.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: зафиксировать правило в живой документации, подготовить governance-правку, прогнать
гейты и довести PR до зелёного CI.

- [X] T047 [P] Создать `knowledge/adr/local-0017-selective-ci-checks.md` — ADR: контекст, решение
      (манифест + `changes`/`ci-summary`), рассмотренные альтернативы (3 варианта механизма,
      2 варианта семантики гейта), последствия («зелёный CI» = «зелёный `ci-summary`»), связь с
      FR-015/FR-016.
      → проверка: `grep -nE '^#{1,3} ' knowledge/adr/local-0017-selective-ci-checks.md` → обязательные разделы ADR
- [X] T048 [P] `knowledge/guidelines/architecture-conventions.md` — добавить R-49 «Проверка
      запускается по зоне интереса» (Rule / Protocol / Failure / Enforcement) и переработать R-47 в
      новую редакцию (применимые проверки; список применимых — из манифеста, список команд — из
      workflow; pre-commit-хуки не меняются).
      → проверка: `grep -n 'R-49' knowledge/guidelines/architecture-conventions.md` → раздел есть
- [X] T049 [P] `knowledge/system/infra/ci-tools.md` — раздел «Правило выборочного запуска»
      (механизм `changes`/`ci-summary`, манифест, след пропуска) + строка в таблице guard'ов
      (`check-ci-checks-manifest.sh`: зона / режим / стоимость) + записи про KARAOKE-377/378.
      → проверка: `grep -n 'ci-checks-manifest' knowledge/system/infra/ci-tools.md` → строка в таблице
- [X] T050 [P] `knowledge/system/infra/pre-commit-config.md` — отметка: pre-commit не меняется;
      правило применимости живёт в манифесте, а не в хуках (R-48); новый хук `ci-checks-manifest`.
      → проверка: `grep -n 'ci-checks-manifest' knowledge/system/infra/pre-commit-config.md` → строка есть
- [X] T051 [P] `knowledge/public/onboarding.md` — «дождаться CI (7 проверок)» → «дождаться CI
      (зелёный `ci-summary`)» + краткое объяснение, что пропущенная проверка — не «зелёная».
      → проверка: `grep -n 'ci-summary' knowledge/public/onboarding.md` → строка есть
- [X] T052 [P] Создать `knowledge/product/features/selective-ci-checks.md` — per-feature документ
      (Constitution VI, FR-009): slug `selective-ci-checks`, status, 6 обязательных секций,
      ссылка на ADR `local-0017`.
      → проверка: `bash tools/check-feature-doc.sh knowledge/product/features/selective-ci-checks.md; echo rc=$?` → `0`
- [X] T053 [P] `CLAUDE.md` — синхронизировать строку про CI («7/7 PASS обязателен перед merge» →
      зелёный `ci-summary`), без изменения версии, если правка не тянет semver-бамп.
      → проверка: `grep -n 'ci-summary' CLAUDE.md` → строка есть
- [X] T054 Подготовить **не применённый** diff для `AGENTS.md` (Tier-1 «Hard Gate — Git — CI-gate
      для master»: «зелёный CI» → «зелёный `ci-summary` (применимые проверки)», semver-бамп
      3.9.0 → 3.10.0, запись в Changelog) и сохранить его как
      `specs/692-selective-ci-checks/agents-md-diff.patch`; сам `AGENTS.md` **не менять**
      (governance-правка: semver-бамп + одобрение владельца).
      → проверка: `git diff --no-index /dev/null specs/692-selective-ci-checks/agents-md-diff.patch` → патч приложится к `AGENTS.md`; `git diff --name-only master..HEAD -- AGENTS.md` → пусто
- [X] T055 Прогнать гейты изменённой области: `python3 tools/check-ssot-impact.py`,
      `python3 tools/check-doc-references.py`, `bash tools/check-knowledge-structure.sh`,
      `python3 tools/lint-knowledge.py`, `bash tools/check-ci-workflow-sync.sh`,
      `bash tools/check-ci-checks-manifest.sh`, `python3 tools/ci-checks.py --self-test`,
      `bash tools/check-ci-checks-manifest.sh --self-test`, `bash tools/merge-pr.sh --self-test`.
      → проверка: каждый гейт — rc=0 (кроме `check-doc-references.py`, где чужое нарушение
      закрывается точечным `SKIP=check-doc-references` — по решению лида; в отчёте — строка о пропуске)
- [X] T056 Локальный контрактный прогон применимых проверок: `python3 tools/ci-checks.py --local`;
      зафиксировать в отчёте, что полный набор (`--local --all`) не прогонялся и почему (время
      раннера), если не прогнан.
      → проверка: вывод `--local` в отчёте шага; причина отсутствия полного прогона указана явно
- [X] T057 Обновить PR #41 (push ветки `692-selective-ci-checks`) и дождаться зелёного CI; merge
      **не** выполнять (только по слову владельца, через `bash tools/merge-pr.sh 41`).
      → проверка: `git ls-remote origin refs/heads/692-selective-ci-checks` → SHA совпадает с
      локальным HEAD; статус прогона — зелёный
- [X] T058 Подготовить `specs/692-selective-ci-checks/report.md` — отчёт для Plane-шага
      `add-comment` (шаги `mark-review`/`close-issue` выполняются после merge — не в этой сессии).
      → проверка: `test -f specs/692-selective-ci-checks/report.md` → файл есть
- [ ] T059 **Follow-up (не входит в этот PR)** — после обновления Forgejo до **v17.0.1+**
      (задача KARAOKE-379; цель апгрейда владельцем изменена с v16.0.7 на v17.0.1+, первопричина
      предупреждения v17.0.0 исправлена в upstream #14866 → #14869): проверить `ref` в
      scheduled-прогоне и при необходимости перевести checkout/выражения на
      `${{ forgejo.ref }}` (release notes Forgejo 16.0.0, PR 13081: «use proper
      `${{ forgejo.ref }}` in scheduled workflows» — требование остаётся актуальным и в v17), а
      также добавить `timezone: 'Europe/Moscow'` в cron-триггер (в v17 поддерживается, но сейчас
      живой сервер v15, где поле может не приниматься валидатором, поэтому в этом PR его нет).
      Отдельно: после апгрейда появляется читаемый через API лог джоба — это закроет
      машино-читаемость следа пропуска (D-18 п.1), но реализация на это не завязана (гейт
      пересчитывает применимость сам). Задачу завести в Plane рядом с KARAOKE-379 со ссылкой на
      эту строку `tasks.md`.
      → проверка: `grep -n 'forgejo.ref\|timezone' .forgejo/workflows/lint.yml` → после обновления
      присутствуют; до обновления — только в комментарии

### Задачи, добавленные после анализа (шаг 3 `speckit-analyze`)

Идентификаторы продолжают сквозную нумерацию; задачи выполняются в фазах, указанных в описании.

- [X] T060 [US4] **Фаза 4 (US1)** — джоб `ci-checks-manifest-guard` в
      `.forgejo/workflows/lint.yml` и `.github/workflows/lint.yml`: регистрация guard'а в CI по
      R-47 п. 3 («скрипт + **джоб** + pre-commit + `--self-test`»). Шаги:
      `bash tools/check-ci-checks-manifest.sh`, `bash tools/check-ci-checks-manifest.sh --self-test`,
      `python3 tools/ci-checks.py --self-test`. **Без** `needs: changes` и без условия по зонам —
      гейт запускается **всегда** (`mode: always`, запись `class: infrastructure`, `gate: true`):
      он дешёвый, а его проверка «мёртвая зона» зависит от состава tracked-файлов (удаление файла
      может сделать зону мёртвой), поэтому привязать его к правке только workflow-файлов нельзя.
      Запись в манифесте — `ci-checks-manifest-guard` (T002).
      → проверка: `grep -A8 'ci-checks-manifest-guard:' .forgejo/workflows/lint.yml` → джоб есть и
      self-test-шаги присутствуют; то же в `.github/workflows/lint.yml`;
      `bash tools/check-ci-workflow-sync.sh; echo rc=$?` → `0`
- [X] T061 [US3] **Фаза 6 (US3)** — `tools/merge-pr.sh`: пересчёт применимости **самим гейтом**
      (D-18 п. 2) — вызов
      `python3 tools/ci-checks.py --manifest config/ci/checks.yml --base origin/master --head <head_sha> --list`
      на head-коммите PR. Это независимый источник истины: гейт не доверяет заданию агрегата, а сам
      знает, какие проверки должны были выполниться, и перечисляет пропущенные в отчёте; при коде
      `2` («данных нет»: нет рефа, нет merge-base, нет инструмента) — **отказ**, а не «зелёно».
      → проверка: `bash tools/merge-pr.sh --self-test; echo rc=$?` → `0` (в том числе фикстура
      «нет данных → отказ»)
- [X] T062 **Фаза 9 (Polish)** — задача-скрипт измерения после внедрения:
      `tools/measure-selective-ci.py` — сбор `/actions/tasks` (`created_at`/`updated_at` → длительности
      заданий) + состав правок по коммиту прогона (локальный git), методика
      `.scratch/wayfinding/ci-path-filters/baseline-metrics.md`; окно наблюдения — ≥30 PR и ≥14 дней
      (SC-003/SC-004/SC-005). Логи джобов **не** используются (на v15 через API недоступны, 404).
      Описание скрипта — в `knowledge/system/infra/ci-tools.md` (рядом с таблицей guard'ов).
      → проверка: `python3 tools/measure-selective-ci.py --help` → rc=0; раздел в `ci-tools.md` есть

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей — манифест можно начать сразу.
- **Foundational (Phase 2)**: зависит от Phase 1 — **блокирует все пользовательские истории**.
- **Phase 3 (US4)**: зависит от Phase 2; гейт согласованности нужен до правки копий workflow.
- **Phase 4 (US1)**: зависит от Phase 3 (гейт) и Phase 2 (правило).
- **Phase 5 (US2)**: зависит от Phase 4 (зоны и `needs` в workflow) и T002.
- **Phase 6 (US3)**: зависит от Phase 4 (существует сигнал `ci-summary`).
- **Phase 7 (US5)**: зависит от Phase 4 (из workflow извлекаются команды) и Phase 2.
- **Phase 8 (US6)**: зависит от Phase 2 (режим полного набора) и Phase 4 (джоб `changes`).
- **Phase 9 (Polish)**: зависит от всех историй; `report.md` — от merge (вне этой сессии).

### User Story Dependencies

- **US1 (P1)**: после Foundational — без зависимостей от других историй.
- **US2 (P1)**: после US1 (те же джобы и условия) — зоны правятся в том же манифесте.
- **US3 (P1)**: после US1 (появляется сигнал `ci-summary`) — независим от US2.
- **US4 (P2)**: после Foundational; обслуживает US1/US2/US3 (проверяет их согласованность).
- **US5 (P2)**: после US1 — читает `run:`-команды изменённого workflow.
- **US6 (P3)**: после US1 — расширяет поведение джоба `changes`.

### Within Each User Story

- фикстуры (`--self-test`) пишутся вместе с реализацией и проверяются в той же задаче;
- реализация правила → режимы CLI → фикстуры;
- манифест → гейт согласованности → регистрация в workflow → регистрация в pre-commit;
- workflow `.forgejo/` → зеркало `.github/` (зеркало — копия, не источник).

### Parallel Opportunities

- T004, T005 ([P]) — независимые части ядра;
- T010, T020 ([P]) — независимые гейты (разные файлы);
- T015–T018 ([P]) — независимые фикстуры гейта согласованности;
- T047–T053 ([P]) — независимые правки знаний (разные файлы);
- T022–T024 и T026 — не параллельны: зеркало правится после основной копии, иначе расхождение
  создаётся дважды.

---

## Parallel Example: Phase 2 (Foundational)

```bash
# Параллельно: две независимые части ядра правила
Task: "T004 [P] tools/ci-checks.py — загрузка и валидация манифеста"
Task: "T005 [P] tools/ci-checks.py — матчинг глобов"
```

---

## Parallel Example: Phase 3 (US4)

```bash
# Параллельно: гейт согласованности, расширение sync-гейта и фикстуры
Task: "T010 [P] [US4] tools/check-ci-checks-manifest.sh — каркас guard'а"
Task: "T020 [P] [US4] tools/check-ci-workflow-sync.sh — расширение"
Task: "T015 [P] [US4] tools/fixtures/ci-checks-manifest/synced/"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1: Setup — манифест (T001, T002)
2. Phase 2: Foundational — реализация правила (T003–T009)
3. Phase 3: US4 — гейт согласованности (T010–T021)
4. Phase 4: US1 — workflow: `changes`, `if:`, `ci-summary` (T022–T029)
5. **СТОП и ПРОВЕРКА**: PR с правкой только `knowledge/` → тяжёлые джобы не стартуют, `ci-summary` зелёный
6. Далее — US2, US3, US5, US6, затем знания.

### Incremental Delivery

1. Setup + Foundational → правило существует и покрыто фикстурами.
2. US1 → CI научился считать применимость и отдавать агрегированный сигнал (MVP).
3. US2 → зоны разведены, тяжёлые не толкаются.
4. US3 → merge-гейт принимает решение по агрегату, гейт не обманывается усечением.
5. US4 → согласованность манифеста и копий под защитой.
6. US5 → локальный прогон совпадает с CI.
7. US6 → страховка полного набора.
8. Polish → знания, ADR, per-feature документ, готовый diff для `AGENTS.md`, PR #41 зелёный.

---

## Notes

- [P] — разные файлы, нет зависимостей;
- [Story] — соответствие историям спеки (US1…US6);
- каждая задача имеет проверяемый результат: файл + команда;
- коммит после каждой логической группы задач, `git add` — только явными путями;
- merge не выполняется: только по слову владельца, через `bash tools/merge-pr.sh 41`;
- `AGENTS.md` в этом PR не меняется (governance-правка) — готовится патч T054;
- чужое нарушение `python3 tools/check-doc-references.py` (правка
  `knowledge/domains/publishing/components/publishing-services.md`) не чинится и не коммитится;
  при коммитах, задевающих `knowledge/`, используется точечный
  `SKIP=check-doc-references git commit …` (не `--no-verify`) — по решению лида, с записью каждого
  пропуска в отчёт шага.
- **Промежуточное «красное» состояние гейта согласованности — ожидаемо, не регресс.** Между
  фазой 3 (гейт создан) и задачей T029 (workflow приведён в соответствие) гейт
  `tools/check-ci-checks-manifest.sh` на реальном дереве возвращает ненулевой код: в копиях
  workflow ещё 31 job-id, а в манифесте — **35 записей** (31 существующий + `direct-commits-guard`
  (T028) + `ci-checks-manifest-guard` (T060) + `changes` (T022) + `ci-summary` (T024)). Красный
  гейт в этом промежутке — следствие порядка фаз, а не дефект; после T029 гейт зелёный.
  На фикстурах (`tools/fixtures/ci-checks-manifest/`) гейт зелёный уже на фазе 3.
- **Решения анализа (шаг 3) внесены:** A1 — манифест стал полным реестром job-id (35 записей,
  инвариант «джоб ↔ запись» тотальный, без исключений), добавлен джоб `ci-checks-manifest-guard`
  (T060) и отдельная инфраструктурная запись для него; C1 — `--self-test` обеих реализаций
  выполняется в CI (шаги джоба гейта); F1 — гейт сверяет равенство `needs` по job-id (M-12);
  F2 — правило выбора контекста `ci-summary` (все контексты, содержащие `ci-summary`; ни одного →
  отказ; любой не `success` → отказ с именем); F3 — манифест в гейте только для диагностики;
  F4 — носитель следа пропуска (D-18: печать в лог + step output, пересчёт гейтом, без
  `permissions: statuses: write`); F5 — инвариант M-12 заведён в обе таблицы; F6 — постраничный
  сбор статусов; D1 — словарь терминов один (data-model §6); U1/U2 — фикстуры SC-001/SC-002
  (T009); U3 — 10 категорий правок (T042); U4 — скрипт измерения (T062).
