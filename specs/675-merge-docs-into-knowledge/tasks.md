---

description: "Задачи миграции слоя docs/ в knowledge/ (KARAOKE-334, карта KARAOKE-323)"
---

# Tasks: Слияние слоя `docs/` в `knowledge/` и запрет параллельной документации

**Input**: Design documents from `/specs/675-merge-docs-into-knowledge/`

**Prerequisites**: [plan.md](./plan.md) (required), [spec.md](./spec.md) (required — контракт),
[research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/),
[quickstart.md](./quickstart.md)

**Tests**: тестовые задачи включены **не как TDD-практика**, а потому что их прямо требуют
FR-012 (фикстуры guard'а), FR-013 (проверка синхронности workflow), FR-015 (guard «BR-код ↔
компонент») и [contracts/guards.md](./contracts/guards.md). Отдельного набора unit-тестов для
логики миграции нет — предмет работы — документация.

**Organization**: задачи сгруппированы по пользовательским историям спеки (US1…US5, в порядке
приоритета), чтобы каждая история проверялась независимо. Внутри историй — порядок фаз плана
P1…P7.

**⚠️ Ограничение на формулировки**: число пер-фичеурных документов продуктовой ветки **не
фиксируется** в задачах. Решение владельца (26 или 22) вынесено в отдельную помеченную задачу
**T025**; все остальные задачи говорят «пер-фичеурные документы продуктовой ветки». Правка
базовой линии = правка одной задачи.

---

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимостей)
- **[Story]**: к какой пользовательской истории относится задача (US1…US5)
- В описании — точные пути файлов и команды

**Path conventions**: единственный проект — корень репозитория `/home/nsa/Karaoke`; пути ниже
относительные.

---

## Phase 1: Setup (shared infrastructure)

**Purpose**: подготовить целевую структуру и зелёный базовый уровень **до** переноса, чтобы ни
один промежуточный коммит не ломал pre-commit.

**Соответствует плану**: P1 (подготовка).

- [ ] T001 Проверить состояние репозитория без правок файлов: `git branch --show-current` = `675-merge-docs-into-knowledge`, `git status --porcelain` пусто, `git log master --oneline -1` = `22d59cd8`, `git ls-files docs/` = 73
- [ ] T002 Снять baseline-метрики (вывести в вывод команды, файлы не менять): `git ls-files docs/ | wc -l` = 73; `grep -rhoE "\bBR-[0-9]+" docs/features/ | sort -u | wc -l` = 510; `awk -F'\t' '$1 ~ /^docs\//' config/knowledge/doc-references-whitelist.txt | wc -l` = 4; `git ls-files archive/docs/ | wc -l` = 43
- [ ] T003 [P] Создать шаблон второго профиля формата `knowledge/templates/product-feature.md` — 6 секций FR-009 (`## Что делает`, `## Зачем`, `## Как работает`, `## Инварианты`, `## Известные ловушки`, `## Ссылки`), правило «slug == имя файла», `status` ∈ {active, deprecated, experimental}, назначение профиля B
- [ ] T004 Создать `knowledge/product/README.md` — назначение ветки, правило границы «что пишется в продуктовую ветку, а что — в компонент домена», термин «продуктовый слой» со ссылкой на `knowledge/README.md`; место для перечня документов заполняется в T022
- [ ] T005 [P] Создать каркас `knowledge/system/infra/audit-336-registry.md` — 6 разделов по [contracts/ssoT-structure.md C4](./contracts/ssoT-structure.md): назначение и границы / сводка / таблица кодов / карточки пакетов / коды без зеркала / changelog
- [ ] T006 Расширить `tools/check-knowledge-structure.sh` (строки 27–31 и 42–45): `knowledge/product` в список каталогов, `knowledge/product/README.md` в список обязательных README, `product-feature.md` в проверку шаблонов; прогнать `bash tools/check-knowledge-structure.sh` — код 0
- [ ] T007 Зафиксировать зелёный базовый уровень: `python3 tools/check-doc-references.py` (код 0), `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt` (код 0), `python3 tools/check-spec-issue-link.py` (код 0)

**Checkpoint**: целевая структура создана, существующие гейты зелёные — можно переносить.

---

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: перенести слой `docs/` и наполнить реестр аудита. **Ни одна пользовательская
история не может быть завершена, пока этот этап не закончен**: ссылки, инструменты и правила
указывают на пути, которые появятся именно здесь.

**⚠️ CRITICAL**: FR-023 — миграция одной атомарной волной; эта фаза и все последующие мерджатся
одним PR.

**Соответствует плану**: P1 (перенос).

- [ ] T008 [P] Перенести `git mv` пер-фичеурные документы продуктовой ветки из `docs/features/` в `knowledge/product/features/<slug>.md` (кроме четырёх инфраструктурных — см. T025); список — [contracts/migration-map.md § 1.1](./contracts/migration-map.md)
- [ ] T009 [P] Перенести `git mv` `docs/features/tonality-bpm-songs-table.md` → `knowledge/product/features/tonality-bpm-songs-table.md` (продуктовое решение: колонки Ton/BPM)
- [ ] T010 [P] Перенести `git mv` четыре операционных рецепта `docs/ops/crypto-key-rotation.md`, `docs/ops/forgejo-access.md`, `docs/ops/log-correlation.md`, `docs/ops/prod-config-delivery.md` → `knowledge/guidelines/runbooks/`
- [ ] T011 [P] Перенести `git mv` `docs/tracker-setup.md` → `knowledge/guidelines/runbooks/tracker-setup.md` (формат runbook'а «How to: …»)
- [ ] T012 [P] Перенести `git mv` `docs/api/README.md` → `knowledge/guidelines/kdoc-jsdoc.md`
- [ ] T013 [P] Перенести `git mv` `docs/governance/knowledge-first.md` → `knowledge/guidelines/knowledge-first.md`
- [ ] T014 [P] Перенести `git mv` `docs/architecture-notes.md` → `archive/docs/architecture-notes.md` (существующие 43 файла `archive/docs/**` не трогать)
- [ ] T015 Наполнить `knowledge/system/infra/audit-336-registry.md` содержимым 30 пакетов аудита #336: карточка каждого пакета (имя, волна, состав, что исправлено, куда легло зеркало) + все 510 уникальных BR-кодов с колонками «код · суть дефекта (одна строка) · компонент-зеркало · пакет»; **после** наполнения удалить 30 исходных файлов `docs/features/` (свёртка в реестр); перечень — [contracts/migration-map.md § 1.2](./contracts/migration-map.md)
- [ ] T016 Свёртка семи мост-файлов — дописать уникальное содержание в компоненты-приёмники: `storage-metadata-cache` → `knowledge/domains/storage/components/storage-api-client.md` + `knowledge/domains/caching/components/web-caches.md`; `stemjob-private-bucket` → `knowledge/domains/storage/domain.md` + `knowledge/domains/storage/components/storage-flow.md` + `knowledge/domains/karaoke-web/components/internal-stem-job-controller.md`; `tracker-local-integration` → `knowledge/adr/0008-tracker-openproject-migration.md` + `knowledge/system/infra/tracker.md` + `knowledge/adr/local-0012-plane-tracker-migration.md`; `zakroma-tiles-sort-order` → `knowledge/system/infra/sql-migrations.md` + `knowledge/domains/persistence/domain.md` + `knowledge/domains/catalog/components/dictionaries.md`; `zakroma-albums-by-author` → `knowledge/domains/catalog/components/album-entity.md` + `knowledge/adr/local-0007-zakroma-album-id-in-stream-dto.md`; `telegram-auto-publish` → `knowledge/domains/publishing/components/publishing-services.md` + `knowledge/domains/processing/components/schedulers.md`; `song-edit-and-censored` → `knowledge/system/frontend/webvue3-audit-invariants.md` + `knowledge/domains/catalog/components/remaining-models.md` + `knowledge/domains/publishing/components/publishing-services.md`; **BR-258, BR-541, BR-601** → реестр аудита
- [ ] T017 Удалить два мост-файла-полных-зеркала: `docs/features/sync-audio-descendants.md` (зеркало — `knowledge/domains/catalog/components/audio-descendant-sync.md`) и `docs/features/target-scoped-admin-requests.md` (зеркало — `knowledge/system/frontend/target-request-guard.md`)
- [ ] T018 Удалить `knowledge/system/infra/per-feature-docs.md` и переписать три ссылки на него: `knowledge/domains/README.md:115` (строка каталога `knowledge/system/infra/*` — «`per-feature-docs.md` — docs/features/ (Pass 380)»; заменить на каталог продуктовой ветки), `knowledge/system/infra/pre-commit-config.md:183`, `knowledge/system/infra/ci-tools.md:414` — **⚠️ FR-016 читается как «список 16 доменов не меняется»; техническая правка одной строки :115 обязательна по FR-014/SC-003 (иначе остаётся ссылка на удалённый файл). Реестр доменов в `knowledge/domains/README.md` не дополняется — см. § «Открытые вопросы к лиду»**
- [ ] T019 **В том же коммите**, что и перенос (T008–T018), перенастроить точки вызова `tools/check-feature-doc.sh` на новые пути: `.forgejo/workflows/lint.yml:603`, `.github/workflows/lint.yml:562`, `.pre-commit-config.yaml:325` (`entry`) и `:328` (`files`) — иначе glob по несуществующему каталогу не раскроется и pre-commit упадёт
- [ ] T020 Переписать внутренние ссылки перенесённых документов: пересчёт **от нового места** (цели внутри `knowledge/` — кратчайшая относительная форма `../../domains/x.md` без повторения `knowledge/`; наружу — `../../../tools/…`), а не замена подстроки — [contracts/migration-map.md § 3.3](./contracts/migration-map.md)
- [ ] T021 Вычислить и записать в реестр раздел «Коды без зеркала» — **после** переноса (значение ≤ 267, измерено до миграции); инвентарь кодов остаётся 510
- [ ] T022 Заполнить `knowledge/product/README.md` перечнем перенесённых пер-фичеурных документов продуктовой ветки (без указания числа — см. T025)
- [ ] T023 Проверка фазы: `git ls-files docs/` = 0; `test -d docs` ложно; `bash tools/check-feature-doc.sh knowledge/product/features/*.md archive/docs/features/*.md` — код 0; `git log --follow --oneline knowledge/product/features/kortege-execution.md` показывает исходные коммиты `docs/features/…` (SC-011)
- [ ] T024 Проверка фазы: `bash tools/check-knowledge-structure.sh` — код 0; `python3 tools/check-doc-references.py` — код 0; `pre-commit run --all-files` — все хуки Passed

**Checkpoint**: слой `docs/` перенесён, содержимое не потеряно, реестр наполнен — правила и
инструменты можно переводить на новые пути.

---

## Phase 2.5: Задача с ожиданием решения владельца (26 или 22)

**Purpose**: судьба четырёх инфраструктурных пер-фичеурных документов — единственный открытый
вопрос контракта. **Спека (FR-003, SC-004) и ответ 324 § 5 несовместимы**; решение принимает
владелец.

**⚠️ ВАЖНО**: задача **не выполняется** до решения владельца. Число пер-фичеурных документов
продуктовой ветки нигде в наборе задач не зафиксировано.

- [ ] T025 ⛔ **РЕШЕНИЕ ВЛАДЕЛЬЦА ОЖИДАЕТСЯ (26 или 22)** — судьба `docs/features/deploy-hardening.md`, `docs/features/gradle-9-upgrade.md`, `docs/features/karaoke-db-module-removal.md`, `docs/features/private-docker-registry.md`
      - **Вариант A (базовая линия плана, «26»)**: все четыре переносятся `git mv` в `knowledge/product/features/` без изменений; дополнительно — уникальное содержание `gradle-9-upgrade` (история отката с 9.1.0) и `karaoke-db-module-removal` (мёртвый класс «Hello world!», свой build и wrapper на 7.5.1) → `knowledge/guidelines/architecture-conventions.md` § R-46 + `knowledge/system/infra/ci-tools.md`; 31 BR-код `deploy-hardening` (PKG-S2-08) → карточка пакета + коды в `knowledge/system/infra/audit-336-registry.md`, фактическая часть (бэкапы БД, секреты, TLS, экспозиция портов, ротация логов) → `knowledge/system/infra/deploy-overview.md`; содержимое `private-docker-registry` дубли не создаёт (зеркала — `knowledge/system/infra/registry.md` + `harbor-registry.md` + `deploy-overview.md`)
      - **Вариант B («22»)**: `private-docker-registry.md` удалить (зеркало полное), `gradle-9-upgrade.md` и `karaoke-db-module-removal.md` свернуть в `architecture-conventions.md` + `ci-tools.md`, `deploy-hardening.md` → реестр аудита + `deploy-overview.md`; **требует правки FR-003 и SC-004 спеки — только по слову владельца**
      - Проверка после любого варианта: `bash tools/check-feature-doc.sh knowledge/product/features/*.md` — код 0; `grep -c "BR-" knowledge/system/infra/audit-336-registry.md` не уменьшился; `git ls-files docs/` = 0

---

## Phase 3: User Story 1 — Один слой проектной документации (Priority: P1) 🎯 MVP

**Goal**: в репозитории один слой проектной документации — `knowledge/`; каталога `docs/` в корне
нет; все материалы доступны по новым путям и работают (ссылки, инструменты, CI-таргеты).

**Independent Test**: `git ls-files docs/` = 0, каталог `docs/` на диске отсутствует; каждый
материал слоя `docs/` доступен по пути из `knowledge/README.md` или `knowledge/product/README.md`;
`tools/check-feature-doc.sh`, `check-knowledge-structure.sh`, `check-doc-references.py` — код 0.

**Соответствует плану**: P2 (ссылки), P3 (инструменты).

### Implementation for User Story 1

- [ ] T026 [P] [US1] Переписать ссылки в `knowledge/` — 104 упоминания в 38 файлах: markdown-ссылки и текстовые упоминания на перемещаемые файлы; ссылки на удаляемый `per-feature-docs.md` и сворачиваемые мост-файлы → на компоненты-приёмники по [contracts/migration-map.md § 1.3](./contracts/migration-map.md)
- [ ] T027 [P] [US1] Переписать markdown-ссылки в `specs/` — 104 ссылки в 52 файлах; **текстовые упоминания `docs/` не трогать** (политика проекта)
- [ ] T028 [P] [US1] Переписать `@see` в KDoc/JSDoc — 74 в 51 файле (karaoke-app 36, webvue3 17, karaoke-web 13, karaoke-public 8) → `knowledge/product/features/<slug>.md`
- [ ] T029 [P] [US1] Перенастроить `tools/check-doc-references.py:44` (`ROOTS` — убрать `"docs"`) и `:54` (`GENERATED_PREFIXES` → `("build/api-docs/",)`)
- [ ] T030 [P] [US1] Перенастроить `tools/check-ssot-impact.py:33` (снять исключение `r"^docs/"` — каталога нет) и `:170` (текст-ссылка → `archive/docs/architecture-notes.md`)
- [ ] T031 [P] [US1] Перенастроить `tools/verify-doc-links.sh:48` (`TARGETS` → `knowledge/product/features/`, `CONTRIBUTING.md`) и `:7` (usage-текст)
- [ ] T032 [P] [US1] Перенастроить `tools/check-no-legacy-tbl-settings.sh:174` (`for f in knowledge/product/features/*.md`) и `tools/verify-kotlin-refs.sh:18` (`TARGETS=(knowledge/product/features/*.md)`)
- [ ] T033 [P] [US1] Перенастроить `tools/check-feature-doc.sh:10,44,116` (usage-текст и примеры на `knowledge/product/features/*.md`); **контракт проверки (6 секций, slug, status) не менять**
- [ ] T034 [P] [US1] Перенастроить `tools/generate-docs.sh:5-8,38,56,62,68` — вывод в `build/api-docs/`; и `.gitignore:97-99` — заменить `docs/api/dokka/`, `docs/api/typedoc-*/` на `build/api-docs/`
- [ ] T035 [P] [US1] Переписать 4 строки `config/knowledge/doc-references-whitelist.txt` со scope `docs/…` (строки 55 → `knowledge/guidelines/kdoc-jsdoc.md`; 61, 62 → `knowledge/system/infra/audit-336-registry.md`; 64 → `knowledge/system/infra/audit-336-registry.md`); строки со scope `archive/docs/…` (10) **не трогать**
- [ ] T036 [US1] Обновить обе копии workflow — таргеты lychee на `knowledge/product/features/` + `archive/docs/features/` + `CONTRIBUTING.md`: `.forgejo/workflows/lint.yml:592-593` и `.github/workflows/lint.yml:551-552`
- [ ] T037 [US1] Обновить контрактный тест `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/audit/PkgS335SourceContractsTest.kt:288,294,299` под новые пути — **тест остаётся**, фиксирует «оба каталога проверяются»
- [ ] T038 [US1] Обновить документацию вызова: `tools/README.md:22` и `PR_DESCRIPTION.md:131`
- [ ] T039 [US1] Проверка US1: [quickstart.md § 1](./quickstart.md) (слой `docs/` исчез), § 3 (пер-фичеурные документы), § 5 (0 битых ссылок) — все коды 0; `bash tools/verify-doc-links.sh` — код 0 (требует `lychee`)
- [ ] T040 [US1] Проверка US1: сверка «до/после» по всем материалам слоя — `git ls-tree -r --name-only 3fb7c8bf docs/ | wc -l` = 73, и каждый старый путь присутствует в справочнике «Бывшие пути» ([quickstart.md § 9](./quickstart.md)); 0 неучтённых (SC-009)

**Checkpoint**: US1 полностью функциональна — второй слой документации отсутствует, материалы
доступны и связаны.

---

## Phase 4: User Story 2 — Параллельный слой не возвращается (Priority: P1)

**Goal**: каталог `docs/` в корне не может появиться снова: guard ловит его локально и в CI
**обеих** конфигураций; рассинхрон двух копий workflow тоже ловится.

**Independent Test**: создать каталог `docs/` с одним файлом → локальная проверка и CI-джоб
падают; убрать каталог → проверки снова зелёные; расхождение копий workflow → проверка
синхронности это замечает.

**Соответствует плану**: P5 (guard), P6 (ADR — нормативное основание запрета).

### Tests for User Story 2 (обязательны — FR-012, FR-013)

- [ ] T041 [P] [US2] Создать фикстуры `tools/fixtures/no-docs-dir/vulnerable/docs/` (файл-заглушка «ФИКСТУРА») и `tools/fixtures/no-docs-dir/clean/` (пусто) — по образцу `tools/fixtures/marker-escape/`
- [ ] T042 [P] [US2] Создать фикстуры `tools/fixtures/ci-workflow-sync/divergent/` и `tools/fixtures/ci-workflow-sync/synced/` — две копии workflow, в `divergent` — лишний гейт
- [ ] T043 [US2] Реализовать `tools/check-no-docs-dir.sh` — CLI-контракт [contracts/guards.md C1](./contracts/guards.md): аргумент `--root <dir>`, проверка отслеживаемых файлов `git ls-files -- docs/` и существования каталога на диске, коды 0 / 1 / 2, сообщение со ссылкой на ADR-0011
- [ ] T044 [US2] Реализовать `tools/check-ci-workflow-sync.sh` — CLI-контракт [contracts/guards.md C2](./contracts/guards.md): сравнение **наборов гейтовых команд** двух копий workflow; `runs-on`, `timeout-minutes`, `if`/`needs` и комментарии **не сравнивать**
- [ ] T045 [US2] Проверка фикстур guard'а «нет `docs/`»: `bash tools/check-no-docs-dir.sh --root tools/fixtures/no-docs-dir/vulnerable` → код 1; `--root tools/fixtures/no-docs-dir/clean` → код 0 (SC-005, 2 из 2)
- [ ] T046 [US2] Проверка фикстуры синхронности: `bash tools/check-ci-workflow-sync.sh --root tools/fixtures/ci-workflow-sync/divergent` → код 1; `--root tools/fixtures/ci-workflow-sync/synced` → код 0

### Implementation for User Story 2

- [ ] T047 [US2] Подключить guard «нет `docs/`» в **обоих** workflow: `.forgejo/workflows/lint.yml` и `.github/workflows/lint.yml` — отдельный шаг в джобе `docs-lint` (FR-012: в CI **обоих** конфигураций)
- [ ] T048 [US2] Подключить проверку синхронности workflow в оба workflow: `.forgejo/workflows/lint.yml` и `.github/workflows/lint.yml`
- [ ] T049 [US2] Подключить guard «нет `docs/`» в `.pre-commit-config.yaml` — hook `no-docs-dir`, `pass_filenames: false`, `always_run: true`, `stages: [pre-commit]`
- [ ] T050 [US2] Создать `knowledge/adr/0011-merge-docs-into-knowledge.md` — YAML-frontmatter (`id: 0011-merge-docs-into-knowledge`, `title`, `status: Accepted`, `supersedes: 0007-adopt-knowledge-as-ssot`, `superseded_by: null`, `date: 2026-10-29`, `modules: [documentation]`, `tags: [ssot, livedocs, documentation, wayfinder]`); H1; блок `> **Supersedes**: ADR-0007 — в части п. 2, п. 4 и следствия 5. Остальные решения ADR-0007 остаются в силе.`; секции «Контекст и проблема» (источники — KARAOKE-320 и KARAOKE-323) / «Решение» / «Обоснование» (3 отклонённые альтернативы) / «Последствия» (4 обязательных пункта: каноничная проектная копия скилла и read-only зеркала; **инвариант рантайм-копии** `.dsh/skills/` ранг 100 перекрывает `.agents/` ранг 200; сравнительные таблицы generic ↔ Karaoke не трогаются; правка `audit-living-docs/SKILL.md:156`); числа BR-кодов — 510 / 267
- [ ] T051 [US2] Проставить в `knowledge/adr/0007-adopt-knowledge-as-ssot.md` **только** метаданное `superseded_by: 0011-merge-docs-into-knowledge` — **тело не трогать** (append-only); проверить `git diff` = одна изменённая строка
- [ ] T052 [US2] Обновить реестр `knowledge/adr/README.md` — строка про `0011-merge-docs-into-knowledge` + уточнение, что `0007` заменён **в части** (п. 2, п. 4, следствие 5)
- [ ] T053 [US2] Проверка US2: [quickstart.md § 2](./quickstart.md), § 8 (ADR принят, старый помечен), § 10 (обе копии workflow содержат одинаковый набор проверок) — все коды 0; `grep -c "check-no-docs-dir" .forgejo/workflows/lint.yml` ≥ 1 и столько же в `.github/workflows/lint.yml`

**Checkpoint**: US2 полностью функциональна — возвращение `docs/` ловится локально и в CI.

---

## Phase 5: User Story 3 — Правка кода обновляет продуктовый документ (Priority: P2)

**Goal**: FR-009 указывает на живой путь; правило не вакуумно; второй профиль формата описан в
`knowledge/README.md`; структурная проверка охватывает продуктовую ветку.

**Independent Test**: per-feature документ без одной из шести секций → проверка указывает на файл
и секцию; агент, ищущий, где обновлять per-feature документ, находит один актуальный путь.

**Соответствует плану**: P4 (правила), часть P3 (линтер профиля A).

### Implementation for User Story 3

- [ ] T054 [P] [US3] Обновить `AGENTS.md`: Tier-2 R-32 → `knowledge/product/features/<slug>.md`; § «Plane Workflow» source → `knowledge/guidelines/runbooks/tracker-setup.md`; § «Диагностика через docker logs» source → `knowledge/guidelines/runbooks/log-correlation.md`; Tier-1 «Процесс wayfinder» source → `knowledge/guidelines/runbooks/tracker-setup.md`; запись в Changelog; версия 3.7.1 → **3.7.2** (PATCH — меняются пути, не смысл)
- [ ] T055 [P] [US3] Обновить `CLAUDE.md`: таблица «Где искать что» — per-feature → `knowledge/product/features/<slug>.md`; стратегия → `archive/docs/strategy/growth.md` + `archive/docs/strategy/growth-audit.md`; логи прода → `knowledge/guidelines/runbooks/log-correlation.md`; трекер → `knowledge/guidelines/runbooks/tracker-setup.md`; changelog PR → `archive/docs/architecture-notes.md`; строку «Настройка Claude Code» (`docs/claude-code-setup.md` — файла нет) → текст без ссылки; запись в Changelog; версия 1.8.1 → **1.8.2**
- [ ] T056 [US3] Обновить `.specify/memory/constitution.md`: § VI FR-006 (`@see docs/features/<slug>.md` → `knowledge/product/features/<slug>.md`) и FR-009 (путь); § VII и § Governance 1 (ссылки на несуществующие `docs/onboarding.md`, `docs/claude-code-setup.md`, `docs/architecture-notes-archive.md` → текст без ссылки; `docs/architecture-notes.md` → `archive/docs/architecture-notes.md`); § VIII (`docs/migration-prod-server.md` — нет → текст без ссылки); § IX (`docs/governance/knowledge-first.md` → `knowledge/guidelines/knowledge-first.md`); **Sync Impact Report** в HTML-комментарии в начале файла; версия 2.4.0 → **2.4.1** (PATCH — смысл правил не меняется)
- [ ] T057 [P] [US3] Обновить `.github/PULL_REQUEST_TEMPLATE.md` — 6 упоминаний `docs/`; чеклист FR-009 указывает `knowledge/product/features/<slug>.md`
- [ ] T058 [P] [US3] Обновить `DEVELOPMENT.md` (5 упоминаний) и `README.md` (3 упоминания) — ссылки на перемещаемые файлы
- [ ] T059 [US3] Добавить в `tools/lint-knowledge.py` **явный комментарий** рядом с `SECTION_EXEMPTIONS`: `knowledge/product/` обслуживается guard'ем FR-009, обход линтера туда не расширяется; убрать ссылку на несуществующий `docs/operations/knowledge-lint-baseline.md` (строка 16) — **исключения не расширять**
- [ ] T060 [US3] Обновить `knowledge/README.md`: строка `product/` в таблице «Структура»; подсекция «Две ветки SSoT» с таблицей двух профилей (профиль A — компонент, профиль B — per-feature: где / шаблон / секции / проверка / что описывает); Karaoke-override п. 5 заменён на «`docs/` слит в `knowledge/`…»; расширенный шаг Initialize протокола агента; ссылка на ADR-0011
- [ ] T061 [P] [US3] Обновить `knowledge/templates/README.md` — описать `product-feature.md` как шаблон второго профиля; добавить ссылку
- [ ] T062 [P] [US3] Обновить `knowledge/guidelines/README.md` — строка `knowledge-first.md` (enforcement MUST #0) в таблице «Структура»; `kdoc-jsdoc.md` перенести из «Планируемого наполнения» в «Структуру»; термин «runbook» вместо «playbook»
- [ ] T063 [P] [US3] Обновить `knowledge/guidelines/runbooks/README.md` — перечень runbook'ов; термин «runbook» вместо «playbook» (термин «playbook» в Универсальный язык не входит)
- [ ] T064 [P] [US3] Добавить в `knowledge/public/glossary.md` секцию «Продуктовый слой (product layer)» с терминами: продуктовый слой / per-feature документ / runbook / реестр аудита #336 — таблица «термин · определение · источник»
- [ ] T065 [P] [US3] Обновить проектную копию `.agents/skills/managing-living-docs/SKILL.md`: таблица Karaoke-overrides — строка про второй профиль `knowledge/product/`; § Impact Surface Analysis — уровень «Продуктовая проекция → `knowledge/product/features/<slug>.md`»; § Final Validation — добавить `bash tools/check-feature-doc.sh knowledge/product/features/*.md`; § Quick Reference — пункт про `knowledge/product/` и новый гейт; **сравнительные таблицы generic ↔ Karaoke (строки 10, 13, 36, 38) не трогать**
- [ ] T066 [P] [US3] Обновить `.agents/skills/audit-living-docs/SKILL.md:156` — `docs/architecture-notes.md` → `archive/docs/architecture-notes.md`; **сравнительные таблицы (строки 11, 14, 33, 35, 37) не трогать**
- [ ] T067 [US3] Проверка US3: `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt` — код 0; `bash tools/check-knowledge-structure.sh` — код 0; `grep -rn "docs/" AGENTS.md CLAUDE.md .specify/memory/constitution.md .github/PULL_REQUEST_TEMPLATE.md | grep -v "Бывшие пути"` — пусто (SC-007); `git diff --stat master -- .specify/memory/constitution.md AGENTS.md CLAUDE.md` — версии bumped

**Checkpoint**: US3 полностью функциональна — правило FR-009 указывает на живой путь, второй
профиль описан и проверяется.

---

## Phase 6: User Story 4 — Ничего не потеряно (Priority: P2)

**Goal**: реестр аудита #336 отвечает на вопрос «что чинить» по любому BR-коду; код, упомянутый
в `knowledge/`, не может потеряться; код без зеркала помечен явно.

**Independent Test**: взять BR-код из реестра, найти строку «дефект → компонент» и ссылку на
компонент; код, упомянутый в `knowledge/`, но отсутствующий в реестре, роняет проверку.

**Соответствует плану**: P5 (guard BR↔компонент), T015/T021 (наполнение реестра).

### Tests for User Story 4 (обязательны — FR-015)

- [ ] T068 [P] [US4] Создать фикстуры `tools/fixtures/audit-registry/orphan-code/` (компонент с `BR-999`, которого нет в реестре), `tools/fixtures/audit-registry/lost-code/` (код в реестре, отсутствующий в `knowledge/` и не помеченный «без зеркала»), `tools/fixtures/audit-registry/consistent/` (код в реестре + код в компоненте + помеченный «без зеркала»)
- [ ] T069 [US4] Реализовать `tools/check-audit-registry.py` — CLI-контракт [contracts/guards.md C3](./contracts/guards.md): правило A «BR-код в `knowledge/**` (кроме реестра) есть в реестре», правило B «код реестра есть в `knowledge/**` либо помечен «без зеркала»»; коды 0 / 1 / 2

### Implementation for User Story 4

- [ ] T070 [US4] Подключить guard «BR-код ↔ компонент» в оба workflow: `.forgejo/workflows/lint.yml` и `.github/workflows/lint.yml` — джоб структуры Knowledge
- [ ] T071 [US4] Подключить guard в `.pre-commit-config.yaml` — hook `audit-registry`
- [ ] T072 [US4] Проверка фикстур: `python3 tools/check-audit-registry.py --root tools/fixtures/audit-registry/orphan-code` → код 1; `--root .../lost-code` → код 1; `--root .../consistent` → код 0
- [ ] T073 [US4] Сверить инвентарь: `grep -ohE "\bBR-[0-9]+\b" knowledge/system/infra/audit-336-registry.md | sort -u | wc -l` = 510 (SC-002); перепроверить, что BR-258, BR-541, BR-601 присутствуют (FR-018)
- [ ] T074 [US4] Проверка US4: `python3 tools/check-audit-registry.py` (на реальном дереве) — код 0; [quickstart.md § 4](./quickstart.md) — коды 0

**Checkpoint**: US4 полностью функциональна — ни один BR-код не потерян.

---

## Phase 7: User Story 5 — Старые ссылки остаются объяснимыми (Priority: P3)

**Goal**: справочник «бывшие пути» объясняет, куда переехал любой материал слоя `docs/`; уже
битые ссылки в `knowledge/` и корневых правилах починены.

**Independent Test**: открыть справочник «бывших путей» и убедиться, что каждый старый путь из
`docs/` присутствует; проверка целостности ссылок не падает на сознательные отсылки.

**Соответствует плану**: P2 (оставшиеся ссылки), P4 (`governance-docs.md`).

### Implementation for User Story 5

- [ ] T075 [P] [US5] Добавить в `knowledge/README.md` раздел «Бывшие пути» — таблица «было → стало» на **все** материалы слоя `docs/`, включая удалённые и свёрнутые (FR-024); отдельный файл-редирект не заводим (иначе второй источник правды о путях)
- [ ] T076 [P] [US5] Переписать ссылки на материалы, существующие в `archive/docs/`, — вести на `archive/docs/…` **с пометкой «архив»** (например `docs/strategy/growth.md` → `archive/docs/strategy/growth.md`); не удалять такие ссылки — это живые ссылки на живые документы
- [ ] T077 [P] [US5] Заменить ссылки на несуществующие файлы — **текст без ссылки** либо пометка «документ не существует»: `knowledge/domains/integration/components/external-api-clients.md:106,120`, `knowledge/adr/0001…0004`, 10 строк `knowledge/system/infra/governance-docs.md`; осознанные отсылки → `config/knowledge/doc-references-whitelist.txt` с reason `honest-gap`
- [ ] T078 [US5] Переписать `knowledge/system/infra/governance-docs.md` — убрать 10 несуществующих строк; строку per-feature заменить на `knowledge/product/features/<slug>.md` (индекс — `knowledge/product/README.md`); добавить строки `knowledge/guidelines/knowledge-first.md`, `knowledge/guidelines/kdoc-jsdoc.md`, `knowledge/guidelines/runbooks/`, `knowledge/product/README.md`, `knowledge/system/infra/audit-336-registry.md`; раздел «Archive» оставить как есть (FR-010)
- [ ] T079 [US5] Проверка US5: `git ls-tree -r --name-only 3fb7c8bf docs/ | sed 's#^docs/##' | while read -r p; do grep -q "$p" knowledge/README.md || echo "НЕ ПОКРЫТО: docs/$p"; done` — пустой вывод; `python3 tools/check-doc-references.py` — код 0; `bash tools/check-knowledge-cross-links.sh` — код 0

**Checkpoint**: US5 полностью функциональна — старые ссылки объяснимы, битых ссылок не осталось.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: финальная валидация всей волны, операционные шаги вне git и отчёт в трекере.

**Соответствует плану**: P6 (ADR — T050…T052 выполнены в фазе US2), P7 (операционное).

- [ ] T080 [P] Заменить копии рантайм-скиллов симлинками на канон: `.dsh/skills/managing-living-docs/` → `.agents/skills/managing-living-docs`, `.dsh/skills/audit-living-docs/` → `.agents/skills/audit-living-docs` (ранг 100 перекрывает ранг 200 ⇒ без этого правка git-версии не действует); каталог `.dsh/` — gitignored, в коммит не входит
- [ ] T081 [P] Проверить синхронизацию скиллов: `sha256sum .agents/skills/managing-living-docs/SKILL.md .dsh/skills/managing-living-docs/SKILL.md` — совпадают; `ls -l .dsh/skills/managing-living-docs` — симлинк; `grep -n "architecture-notes" .agents/skills/audit-living-docs/SKILL.md` → `archive/docs/architecture-notes.md`
- [ ] T082 [P] Проверить, что read-only-зеркала **не тронуты**: `sha256sum ~/.dsh/skills/managing-living-docs/SKILL.md` = `767b61dc…a63ae3`; `git -C /home/nsa/agents-team-srv status --porcelain .agents/skills/` — пусто; `~/.dsh/romario-team/**` не изменён
- [ ] T083 Полный локальный прогон по [quickstart.md § 13](./quickstart.md) — все коды 0: `check-no-docs-dir.sh`, `check-ci-workflow-sync.sh`, `check-audit-registry.py`, `check-knowledge-structure.sh`, `check-knowledge-cross-links.sh`, `lint-knowledge.py`, `check-doc-references.py`, `check-feature-doc.sh`, `verify-doc-links.sh`
- [ ] T084 Проверка гигиены: `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$|\.p12$|\.pfx$'` — пусто; `git ls-files docs/` = 0; `git ls-files archive/docs/ | wc -l` — выросло только на `architecture-notes.md`
- [ ] T085 Прогнать `pre-commit run --all-files` — все хуки Passed; прогнать скилл `kara-post-edit` (правки кода — только docstring'и `@see`)
- [ ] T086 Собрать коммиты PR по порядку плана (перенос → ссылки → инструменты → правила → guard → ADR) с понятными сообщениями — ревью идёт по частям; merge-гейт — зелёный финальный CI
- [ ] T087 Создать `specs/675-merge-docs-into-knowledge/report.md` — отчёт о выполнении (обязательный артефакт Plane-workflow)
- [ ] T088 После merge: `bash tools/tracker.sh add-comment 334 --file specs/675-merge-docs-into-knowledge/report.md` → `bash tools/tracker.sh mark-review 334`; далее — ревью владельца и `close-issue`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей — можно начинать сразу
- **Foundational (Phase 2)**: зависит от Setup — **блокирует все истории** (пути, на которые
  указывают ссылки, инструменты и правила, появляются здесь)
- **Phase 2.5 (решение владельца)**: независима от Setup/Foundational по файлам, но **блокирует**
  завершение US1/US3 (число документов) — выполняется после решения владельца
- **User Stories (Phase 3+)**: все зависят от завершения Foundational; US2, US3, US4, US5 — между
  собой независимы и могут идти параллельно (разные файлы), кроме общих правок workflow и
  pre-commit (T036, T047, T048, T049, T070, T071) — их выполнять **последовательно**
- **Polish (Phase 8)**: зависит от всех нужных историй

### User Story Dependencies

- **US1 (P1)**: после Foundational — без зависимостей от других историй; это MVP-объём
- **US2 (P1)**: после Foundational; независима от US1/US3/US4/US5 (собственные файлы), но её
  ADR-задачи (T050–T052) опираются на структуру, созданную в Foundational
- **US3 (P2)**: после Foundational; опирается на шаблон `knowledge/templates/product-feature.md`
  (T003) и структурную проверку (T006); правки `AGENTS.md`/`CLAUDE.md`/конституции —
  governance-PR с semver bump
- **US4 (P2)**: после Foundational (реестр наполнен в T015, T021); guard реализуется
  независимо
- **US5 (P3)**: после Foundational и US3 (раздел «Бывшие пути» живёт в `knowledge/README.md`,
  который правится в T060) — координация по одному файлу ⇒ US5 правит **другую** секцию того же
  файла, выполнять последовательно с T060

### Within Each User Story

- Ссылки (T026–T028) → инструменты (T029–T038) → проверка: правило FR-009 не должно указывать
  на ещё не существующий путь
- Структура (шаблоны, индексы, README) → наполнение (реестр, перечень) → гейты
- Guard: фикстуры → скрипт → подключение к CI/pre-commit → проверка на фикстурах
- История: сначала ADR-0011, затем метаданное в ADR-0007, затем реестр `knowledge/adr/README.md`

### Parallel Opportunities

- Phase 1: T003, T005, T007 — параллельно; T006 после T004
- Phase 2: T008–T014 — параллельно (разные файлы, `git mv` независимы); T015 и T016 — после
  T008–T014 (нужны BR-коды из перенесённых документов); T017–T019 — после T015/T016;
  T020–T022 — после T015–T019
- Phase 3: T026–T035 — параллельно; T036–T038 — последовательно (общие workflow/тест);
  T039–T040 — после всех
- Phase 4: T041, T042 — параллельно; T043, T044 — параллельно; T047–T049 — последовательно
  (общие workflow и pre-commit); T050–T052 — последовательно
- Phase 5: T054–T058 — параллельно; T059–T066 — параллельно; T067 — после всех
- Phase 6: T068 параллельно с реализацией; T070–T071 последовательно
- Phase 8: T080–T082 — параллельно; T083–T085 — после

---

## Parallel Example: User Story 1

```bash
# Ссылочная правка — разные файлы, идут вместе:
Task: "Переписать ссылки в knowledge/ — 104 упоминания в 38 файлах"
Task: "Переписать markdown-ссылки в specs/ — 104 ссылки в 52 файлах"
Task: "Переписать @see в KDoc/JSDoc — 74 в 51 файле"

# Перенастройка инструментов — разные файлы, идут вместе:
Task: "Перенастроить tools/check-doc-references.py:44,54"
Task: "Перенастроить tools/check-ssot-impact.py:33,170"
Task: "Перенастроить tools/verify-doc-links.sh:48"
Task: "Перенастроить tools/check-no-legacy-tbl-settings.sh:174"
Task: "Перенастроить tools/verify-kotlin-refs.sh:18"
Task: "Перенастроить tools/generate-docs.sh + .gitignore"
```

## Parallel Example: User Story 2

```bash
# Фикстуры и скрипты — параллельно:
Task: "Создать фикстуры tools/fixtures/no-docs-dir/{vulnerable,clean}/"
Task: "Создать фикстуры tools/fixtures/ci-workflow-sync/{divergent,synced}/"
Task: "Реализовать tools/check-no-docs-dir.sh"
Task: "Реализовать tools/check-ci-workflow-sync.sh"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1: Setup
2. Phase 2: Foundational (CRITICAL — блокирует все истории)
3. Phase 2.5: решение владельца по T025
4. Phase 3: User Story 1
5. **СТОП и ПРОВЕРКА**: [quickstart.md § 1, § 3, § 5, § 9, § 12](./quickstart.md) — коды 0
6. Демо: `git ls-files docs/` = 0, материалы доступны по новым путям

> **Оговорка по MVP**: FR-023 требует **одной атомарной волны** — US1 в одиночку не мержится.
> MVP-срез означает «минимальный проверяемый инкремент внутри PR», а не отдельный релиз.

### Incremental Delivery (внутри одного PR)

1. Setup + Foundational → слой перенесён, содержимое сохранено
2. US1 → ссылки и инструменты переведены на новые пути
3. US2 → запрет возврата `docs/` защищён guard'ами
4. US3 → правила указывают на живой путь, второй профиль описан
5. US4 → реестр аудита полон и защищён guard'ом
6. US5 → «бывшие пути» и починка битых ссылок
7. Polish → ADR, симлинки скиллов, полный прогон, отчёт

Каждый шаг — отдельный коммит с понятным сообщением (ревью по частям); мержается только
финальный, по зелёному CI.

---

## Трассируемость FR → задачи

| FR | Кратко | Задачи |
|---|---|---|
| FR-001 | нет отслеживаемых файлов в `docs/` | T008–T017, T019, T023, T039, T081, T083 |
| FR-002 | каждому из 73 файлов — однозначное место | T008–T017, T023, T040 |
| FR-003 | per-feature документы продуктовой ветки | T003, T004, T008, T009, T022, **T025**, T039 |
| FR-004 | `knowledge/product/README.md` — индекс и граница | T004, T022, T060 |
| FR-005 | шаблон `product-feature.md` + `templates/README.md` | T003, T061 |
| FR-006 | `knowledge/README.md`: `product/`, 2 профиля, override #5, бывшие пути, Initialize | T060, T075 |
| FR-007 | 5 runbook'ов в `knowledge/guidelines/runbooks/` | T010, T011, T063 |
| FR-008 | `knowledge/guidelines/kdoc-jsdoc.md` + генератор → `build/api-docs/` | T012, T029, T034 |
| FR-009 | `knowledge/guidelines/knowledge-first.md` + строка в индексе | T013, T062 |
| FR-010 | `governance-docs.md` без битых ссылок | T078 |
| FR-011 | реестр: 510 BR-кодов + карточки + коды без зеркала | T005, T015, T021, T073 |
| FR-012 | guard «нет `docs/`» в обоих CI + pre-commit + фикстуры | T041, T043, T045, T047, T049 |
| FR-013 | проверка синхронности двух workflow | T042, T044, T046, T048 |
| FR-014 | нет устаревших ссылок + починка битых | T020, T026–T028, T036, T076, T077, T079 |
| FR-015 | guard «BR-код ↔ компонент» | T068, T069, T070, T071, T072 |
| FR-016 | реестр доменов не меняется | T018 (одна строка :115 — см. открытый вопрос 2) |
| FR-017 | ADR-0011 + `superseded_by` + реестр ADR | T050, T051, T052 |
| FR-018 | BR-258 / BR-541 / BR-601 в реестре | T016, T073 |
| FR-019 | история перенесённых файлов сохранена | T008–T014, T023 |
| FR-020 | скиллы приведены в соответствие + рантайм-копия | T065, T066, T080, T081, T082 |
| FR-021 | корневые правила и инструменты без `docs/` | T054–T058, T075, T084 |
| FR-022 | `knowledge/product` в структурной проверке + шаблон | T003, T006 |
| FR-023 | одна атомарная волна | T001–T024, T086 (все коммиты в одном PR) |
| FR-024 | справочник «бывших пути» покрывает весь слой | T075, T079, T040 |

## Трассируемость SC → задачи

| SC | Критерий | Задачи |
|---|---|---|
| SC-001 | 0 файлов в `docs/` | T023, T039, T084 |
| SC-002 | 510 BR-кодов в реестре | T015, T073 |
| SC-003 | 0 битых ссылок на перемещённые файлы | T020, T026–T028, T036, T076–T079, T083 |
| SC-004 | все пер-фичеурные документы проходят FR-009 | T019, T023, T025, T039 |
| SC-005 | guard: 2 из 2 сценария | T041, T043, T045 |
| SC-006 | 0 новых нарушений линтера и структурных проверок | T007, T024, T067, T083 |
| SC-007 | 0 упоминаний `docs/` в корневых правилах и инструментах | T054–T058, T084 |
| SC-008 | CI зелёный, обе копии workflow с одинаковым набором проверок | T036, T047, T048, T083, T086 |
| SC-009 | 73 из 73 учтённых | T008–T017, T040, T079 |
| SC-010 | 10 решений карты найдено место в итоговом состоянии | T008–T018, T050, T060, T078 |
| SC-011 | `git log --follow` показывает исходную историю | T008–T014, T023 |

---

## Открытые вопросы к лиду (не блокируют старт реализации)

1. **Судьба четырёх инфраструктурных пер-фичеурных документов (26 или 22)** — задача **T025**.
   Спека (FR-003, SC-004) и ответ 324 § 5 несовместимы; решение владельца. До решения T025 не
   выполняется, число документов нигде в задачах не зафиксировано.
2. **FR-016 vs FR-014 на одной строке `knowledge/domains/README.md:115`** — FR-016 требует, чтобы
   `knowledge/domains/README.md` не изменялся; FR-014/SC-003 требуют, чтобы не осталось ссылки на
   удаляемый `knowledge/system/infra/per-feature-docs.md`. План трактует FR-016 как «реестр доменов
   (список 16 доменов) не меняется» и правит **только** строку :115 (каталог `knowledge/system/infra/*`),
   не дополняя реестр продуктовым слоем (D19). Альтернатива — не трогать файл и оставить устаревшую
   ссылку, что ломает SC-003. Требуется подтверждение трактовки.
3. **Промежуточные коммиты PR дадут красный CI** — guard'ы включаются в фазе US2 (T047–T049),
   а `docs/` исчезает в фазе Foundational. Компромисс осознанный (ревьюимость важнее зелёного
   промежуточного состояния); мержается только финальный коммит по зелёному CI. Если лид
   предпочитает «guard сразу с переносом» — T043/T047/T048 переносятся в фазу Foundational.

---

## Notes

- [P] — разные файлы, нет зависимостей
- [Story] — соответствие пользовательской истории спеки для трассируемости
- **T025 — единственное место, где число пер-фичеурных документов может быть названо**; в
  остальных задачах формулировка «пер-фичеурные документы продуктовой ветки»
- **Не трогать**: `~/.dsh/skills/**`, `~/.dsh/romario-team/**`, `/home/nsa/agents-team-srv/**`,
  существующие `archive/docs/**` (кроме добавления `architecture-notes.md`), `livedocs/**`,
  сравнительные таблицы generic ↔ Karaoke в скиллах
- **Не менять** `spec.md` — FR/SC меняются только по слову владельца
- Коммит после каждой задачи или логической группы; коммит только в ветку
  `675-merge-docs-into-knowledge`, push — по слову лида
- Grep по репозиторию — **только с `--include`** по расширениям (`alignment-ml/` — 20 ГБ,
  `deploy/` — 1,6 ГБ)
- Контейнеры не трогать; `karaoke-app` не перезапускать без согласия и без остановки очереди
  (Pass 380)
