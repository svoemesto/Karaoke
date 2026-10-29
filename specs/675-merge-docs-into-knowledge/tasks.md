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

**Базовое число пер-фичеурных документов продуктовой ветки**: **22** (21 продуктовый +
`tonality-bpm-songs-table`) — решение владельца от 2026-10-29, зафиксировано в спеке
(FR-003, SC-004, коммит `47f31bb6`). Четыре инфраструктурных документа в продуктовую ветку
**не входят** — они сворачиваются в `knowledge/system/infra/` и в реестр аудита (задача
**T025**). В самих задачах число нигде не дублируется: оно живёт в спеке и в T025.

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

- [x] T001 Проверить состояние репозитория без правок файлов: `git branch --show-current` = `675-merge-docs-into-knowledge`, `git status --porcelain` пусто, `git log master --oneline -1` = `22d59cd8`, `git ls-files docs/` = 73
- [x] T002 Снять baseline-метрики (вывести в вывод команды, файлы не менять): `git ls-files docs/ | wc -l` = 73; `grep -rhoE "\bBR-[0-9]+" docs/features/ | sort -u | wc -l` = 510; `awk -F'\t' '$1 ~ /^docs\//' config/knowledge/doc-references-whitelist.txt | wc -l` = 4; `git ls-files archive/docs/ | wc -l` = 43
- [x] T003 [P] Создать шаблон второго профиля формата `knowledge/templates/product-feature.md` — 6 секций FR-009 (`## Что делает`, `## Зачем`, `## Как работает`, `## Инварианты`, `## Известные ловушки`, `## Ссылки`), правило «slug == имя файла», `status` ∈ {active, deprecated, experimental}, назначение профиля B
- [x] T004 Создать `knowledge/product/README.md` — назначение ветки, правило границы «что пишется в продуктовую ветку, а что — в компонент домена», термин «продуктовый слой» со ссылкой на `knowledge/README.md`; место для перечня документов заполняется в T022
- [x] T005 [P] Создать каркас `knowledge/system/infra/audit-336-registry.md` — 6 разделов по [contracts/ssoT-structure.md C4](./contracts/ssoT-structure.md): назначение и границы / сводка / таблица кодов / карточки пакетов / коды без зеркала / changelog
- [x] T006 Расширить `tools/check-knowledge-structure.sh` (строки 27–31 и 42–45): `knowledge/product` в список каталогов, `knowledge/product/README.md` в список обязательных README, `product-feature.md` в проверку шаблонов; прогнать `bash tools/check-knowledge-structure.sh` — код 0
- [x] T007 Зафиксировать зелёный базовый уровень: `python3 tools/check-doc-references.py` (код 0), `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt` (код 0), `python3 tools/check-spec-issue-link.py` (код 0)

**Checkpoint**: целевая структура создана, существующие гейты зелёные — можно переносить.

---

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: перенести слой `docs/` и наполнить реестр аудита. **Ни одна пользовательская
история не может быть завершена, пока этот этап не закончен**: ссылки, инструменты и правила
указывают на пути, которые появятся именно здесь.

**⚠️ CRITICAL**: FR-023 — миграция одной атомарной волной; эта фаза и все последующие мерджатся
одним PR.

**Соответствует плану**: P1 (перенос).

- [x] T008 [P] Перенести `git mv` 21 продуктовый пер-фичеурный документ из `docs/features/` в `knowledge/product/features/<slug>.md` (четыре инфраструктурных в перенос **не** входят — T025); перечень — [contracts/migration-map.md § 1.1](./contracts/migration-map.md) и таблица переноса спеки («`docs/features/` — продуктовые | 21»)
- [x] T009 [P] Перенести `git mv` `docs/features/tonality-bpm-songs-table.md` → `knowledge/product/features/tonality-bpm-songs-table.md` (продуктовое решение: колонки Ton/BPM)
- [x] T010 [P] Перенести `git mv` четыре операционных рецепта `docs/ops/crypto-key-rotation.md`, `docs/ops/forgejo-access.md`, `docs/ops/log-correlation.md`, `docs/ops/prod-config-delivery.md` → `knowledge/guidelines/runbooks/`
- [x] T011 [P] Перенести `git mv` `docs/tracker-setup.md` → `knowledge/guidelines/runbooks/tracker-setup.md` (формат runbook'а «How to: …»)
- [x] T012 [P] Перенести `git mv` `docs/api/README.md` → `knowledge/guidelines/kdoc-jsdoc.md`
- [x] T013 [P] Перенести `git mv` `docs/governance/knowledge-first.md` → `knowledge/guidelines/knowledge-first.md`
- [x] T014 [P] Перенести `git mv` `docs/architecture-notes.md` → `archive/docs/architecture-notes.md` (существующие 43 файла `archive/docs/**` не трогать)
- [x] T015 Наполнить `knowledge/system/infra/audit-336-registry.md`: карточки 30 пакетов аудита #336 **и** карточка пакета PKG-S2-08 из `deploy-hardening` (T025) — по каждому пакету: имя, волна, состав, что исправлено, куда легло зеркало; + таблица **всех 510 уникальных BR-кодов** с колонками «код · суть дефекта (одна строка) · компонент-зеркало · пакет». **Коды собираются из всех 65 файлов `docs/features/`, а не только из 18 файлов `pkg-*.md`:** в `pkg-*.md` — **385** кодов, в остальных **47** файлах — ещё **125** (385 + 125 = 510). 125 кодов встречаются только в 8 продуктовых документах (`client-ip-resolution`, `close-gesture-token-paywall`, `escape-confirm-user-data`, `internal-secret-sync`, `public-api-exposure`, `public-settings-perimeter`, `security-headers`, `song-air-access`) и в 5 мост-файлах (`stemjob-private-bucket`, `target-scoped-admin-requests`, `telegram-auto-publish`, `tracker-local-integration`, `zakroma-tiles-sort-order`) — при сборе только из `pkg-*` они потерялись бы (FR-011, SC-002). **После** наполнения удалить 30 исходных файлов пакетов из `docs/features/` (свёртка в реестр); перечень — [contracts/migration-map.md § 1.2](./contracts/migration-map.md)
- [x] T016 Свёртка семи мост-файлов — дописать уникальное содержание в компоненты-приёмники: `storage-metadata-cache` → `knowledge/domains/storage/components/storage-api-client.md` + `knowledge/domains/caching/components/web-caches.md`; `stemjob-private-bucket` → `knowledge/domains/storage/domain.md` + `knowledge/domains/storage/components/storage-flow.md` + `knowledge/domains/karaoke-web/components/internal-stem-job-controller.md`; `tracker-local-integration` → `knowledge/adr/0008-tracker-openproject-migration.md` + `knowledge/system/infra/tracker.md` + `knowledge/adr/local-0012-plane-tracker-migration.md`; `zakroma-tiles-sort-order` → `knowledge/system/infra/sql-migrations.md` + `knowledge/domains/persistence/domain.md` + `knowledge/domains/catalog/components/dictionaries.md`; `zakroma-albums-by-author` → `knowledge/domains/catalog/components/album-entity.md` + `knowledge/adr/local-0007-zakroma-album-id-in-stream-dto.md`; `telegram-auto-publish` → `knowledge/domains/publishing/components/publishing-services.md` + `knowledge/domains/processing/components/schedulers.md`; `song-edit-and-censored` → `knowledge/system/frontend/webvue3-audit-invariants.md` + `knowledge/domains/catalog/components/remaining-models.md` + `knowledge/domains/publishing/components/publishing-services.md`; **BR-258, BR-541, BR-601** → реестр аудита
- [x] T017 Удалить два мост-файла-полных-зеркала: `docs/features/sync-audio-descendants.md` (зеркало — `knowledge/domains/catalog/components/audio-descendant-sync.md`) и `docs/features/target-scoped-admin-requests.md` (зеркало — `knowledge/system/frontend/target-request-guard.md`)
- [x] T018 Удалить `knowledge/system/infra/per-feature-docs.md` и переписать три ссылки на него: `knowledge/domains/README.md:115` (строка каталога `knowledge/system/infra/*` — «`per-feature-docs.md` — docs/features/ (Pass 380)»; заменить на каталог продуктовой ветки), `knowledge/system/infra/pre-commit-config.md:183`, `knowledge/system/infra/ci-tools.md:414` — **граница FR-016 уточнена владельцем 2026-10-29: реестр 16 доменов (список доменов) в `knowledge/domains/README.md` не меняется, но каталог файлов `knowledge/system/infra/*` в том же файле обновляется в части удалённых документов — иначе остаётся ссылка на несуществующий файл, что запрещает FR-014/SC-003. Продуктовым слоем реестр доменов не дополняется (D19)**
- [x] T019 **В том же коммите**, что и перенос (T008–T018), перенастроить точки вызова `tools/check-feature-doc.sh` на новые пути: `.forgejo/workflows/lint.yml:603`, `.github/workflows/lint.yml:562`, `.pre-commit-config.yaml:325` (`entry`) и `:328` (`files`) — иначе glob по несуществующему каталогу не раскроется и pre-commit упадёт
- [x] T020 Переписать внутренние ссылки **перенесённых** документов (22 файла `knowledge/product/features/`, 5 runbook'ов, `knowledge/guidelines/kdoc-jsdoc.md`, `knowledge/guidelines/knowledge-first.md`): пересчёт **от нового места** (цели внутри `knowledge/` — кратчайшая относительная форма `../../domains/x.md` без повторения `knowledge/`; наружу — `../../../tools/…`), а не замена подстроки — [contracts/migration-map.md § 3.3](./contracts/migration-map.md). **Граница с T026:** T020 — только внутри перенесённых файлов, T026 — по остальным файлам `knowledge/`
- [x] T021 Вычислить и записать в реестр раздел «Коды без зеркала» — **после** переноса и после T025 (значение вычисляется на итоговом дереве; до миграции измерено 267, после переноса 21 продуктового документа — меньше); инвентарь кодов остаётся 510
- [x] T022 Заполнить `knowledge/product/README.md` перечнем перенесённых пер-фичеурных документов продуктовой ветки (21 продуктовый + `tonality-bpm-songs-table`)
- [x] T023 Проверка фазы: `git ls-files docs/` = 0; `test -d docs` ложно; `ls knowledge/product/features/*.md | wc -l` = 22 (FR-003); `bash tools/check-feature-doc.sh knowledge/product/features/*.md archive/docs/features/*.md` — код 0; `git log --follow --oneline knowledge/product/features/kortege-execution.md` показывает исходные коммиты `docs/features/…` (SC-001, SC-004, SC-011)
- [x] T024 Проверка фазы: `bash tools/check-knowledge-structure.sh` — код 0; `python3 tools/check-doc-references.py` — код 0; `pre-commit run --all-files` — все хуки Passed (SC-006)

**Checkpoint**: слой `docs/` перенесён, содержимое не потеряно, реестр наполнен — правила и
инструменты можно переводить на новые пути.

---

## Phase 2.5: Свёртка четырёх инфраструктурных документов

**Purpose**: четыре инфраструктурных пер-фичеурных документа **не входят** в продуктовую
ветку — они сворачиваются в `knowledge/system/infra/` и в реестр аудита (решение тикета карты
KARAOKE-324 § 5, подтверждено владельцем 2026-10-29).

**✅ РЕШЕНО владельцем 2026-10-29**: базовая линия продуктовой ветки — **22** пер-фичеурных
документа (21 продуктовый + `tonality-bpm-songs-table`). Спека приведена в соответствие
(FR-003, SC-004, коммит `47f31bb6`).

- [x] T025 Свёртка четырёх инфраструктурных документов — `docs/features/deploy-hardening.md`, `docs/features/gradle-9-upgrade.md`, `docs/features/karaoke-db-module-removal.md`, `docs/features/private-docker-registry.md`
      - **`private-docker-registry.md` — удалить**: содержимое уже зеркалится в `knowledge/system/infra/registry.md` + `knowledge/system/infra/harbor-registry.md` + `knowledge/system/infra/deploy-overview.md` (существуют); уникального содержания нет
      - **`gradle-9-upgrade.md` — свернуть**: уникально только «история отката с 9.1.0» → `knowledge/guidelines/architecture-conventions.md` § R-46 + `knowledge/system/infra/ci-tools.md` (версия 9.8.2, единый wrapper и контрольные суммы уже зафиксированы)
      - **`karaoke-db-module-removal.md` — свернуть**: уникально обоснование (мёртвый класс «Hello world!», свой build и wrapper на 7.5.1) → `knowledge/guidelines/architecture-conventions.md` + `knowledge/system/infra/ci-tools.md` (факт «четвёртый wrapper удалён» уже есть)
      - **`deploy-hardening.md` — в реестр аудита**: 31 BR-код (PKG-S2-08) → карточка пакета + коды в `knowledge/system/infra/audit-336-registry.md` (совместно с T015); фактическая часть (бэкапы БД, секреты, TLS, экспозиция портов, ротация логов) → `knowledge/system/infra/deploy-overview.md`
      - Ни один из четырёх не переносится в `knowledge/product/features/` ⇒ продуктовая ветка содержит 21 продуктовый документ + `tonality-bpm-songs-table` (FR-003)
      - Проверка: `ls knowledge/product/features/*.md | wc -l` = 22; `bash tools/check-feature-doc.sh knowledge/product/features/*.md` — код 0; `grep -c "BR-" knowledge/system/infra/audit-336-registry.md` не уменьшился; `git ls-files docs/` = 0; `grep -rn "private-docker-registry\|gradle-9-upgrade\|karaoke-db-module-removal\|deploy-hardening" knowledge/ --include=*.md` — только ссылки на новые места

---

## Phase 3: User Story 1 — Один слой проектной документации (Priority: P1) 🎯 MVP

**Goal**: в репозитории один слой проектной документации — `knowledge/`; каталога `docs/` в корне
нет; все материалы доступны по новым путям и работают (ссылки, инструменты, CI-таргеты).

**Independent Test**: `git ls-files docs/` = 0, каталог `docs/` на диске отсутствует; каждый
материал слоя `docs/` доступен по пути из `knowledge/README.md` или `knowledge/product/README.md`;
`tools/check-feature-doc.sh`, `check-knowledge-structure.sh`, `check-doc-references.py` — код 0.

**Соответствует плану**: P2 (ссылки), P3 (инструменты).

### Implementation for User Story 1

- [x] T026 [P] [US1] Переписать ссылки в **остальных** файлах `knowledge/` (38 файлов по карте ссылок — вне перенесённых документов из T020): markdown-ссылки и текстовые упоминания на перемещаемые файлы; ссылки на удаляемый `per-feature-docs.md` и сворачиваемые мост-файлы → на компоненты-приёмники по [contracts/migration-map.md § 1.3](./contracts/migration-map.md)
- [x] T027 [P] [US1] Переписать markdown-ссылки в `specs/` — 104 ссылки в 52 файлах; **текстовые упоминания `docs/` не трогать** (политика проекта)
- [x] T028 [P] [US1] Переписать `@see` в KDoc/JSDoc — 74 в 51 файле (karaoke-app 36, webvue3 17, karaoke-web 13, karaoke-public 8) → `knowledge/product/features/<slug>.md`
- [x] T029 [P] [US1] Перенастроить `tools/check-doc-references.py:44` (`ROOTS` — убрать `"docs"`) и `:54` (`GENERATED_PREFIXES` → `("build/api-docs/",)`)
- [x] T030 [P] [US1] Перенастроить `tools/check-ssot-impact.py:33` (снять исключение `r"^docs/"` — каталога нет) и `:170` (текст-ссылка → `archive/docs/architecture-notes.md`)
- [x] T031 [P] [US1] Перенастроить `tools/verify-doc-links.sh:48` (`TARGETS` → `knowledge/product/features/`, `CONTRIBUTING.md`) и `:7` (usage-текст)
- [x] T032 [P] [US1] Перенастроить `tools/check-no-legacy-tbl-settings.sh:174` (`for f in knowledge/product/features/*.md`) и `tools/verify-kotlin-refs.sh:18` (`TARGETS=(knowledge/product/features/*.md)`)
- [x] T033 [P] [US1] Перенастроить `tools/check-feature-doc.sh:10,44,116` (usage-текст и примеры на `knowledge/product/features/*.md`); **контракт проверки (6 секций, slug, status) не менять**
- [x] T034 [P] [US1] Перенастроить `tools/generate-docs.sh:5-8,38,56,62,68` — вывод в `build/api-docs/`; и `.gitignore:97-99` — заменить `docs/api/dokka/`, `docs/api/typedoc-*/` на `build/api-docs/`
- [x] T035 [P] [US1] Переписать 4 строки `config/knowledge/doc-references-whitelist.txt` со scope `docs/…` (строки 55 → `knowledge/guidelines/kdoc-jsdoc.md`; 61, 62 → `knowledge/system/infra/audit-336-registry.md`; 64 → `knowledge/system/infra/audit-336-registry.md`); строки со scope `archive/docs/…` (10) **не трогать**
- [x] T036 [US1] Обновить обе копии workflow — таргеты lychee на `knowledge/product/features/` + `archive/docs/features/` + `CONTRIBUTING.md`: `.forgejo/workflows/lint.yml:592-593` и `.github/workflows/lint.yml:551-552`
- [x] T037 [US1] Обновить контрактный тест `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/audit/PkgS335SourceContractsTest.kt:288,294,299` под новые пути — **тест остаётся**, фиксирует «оба каталога проверяются»
- [x] T038 [US1] Обновить документацию вызова: `tools/README.md:22` и `PR_DESCRIPTION.md:131`
- [x] T039 [US1] Проверка US1: [quickstart.md § 1](./quickstart.md) (слой `docs/` исчез), § 3 (пер-фичеурные документы: `ls knowledge/product/features/*.md | wc -l` = 22, FR-003), § 5 (0 битых ссылок) — все коды 0; `bash tools/verify-doc-links.sh` — код 0 (требует `lychee`) (SC-001, SC-003) — **выполнено**: § 1.1–1.2 (0 / docs/ отсутствует), § 3 (22 документа, код 0), § 5 (cross-links, doc-references, structure, lychee — все коды 0); п. 1.3 (guard `check-no-docs-dir.sh`) появится в US2 (T041)
- [x] T040 [US1] Проверка US1: сверка «до/после» по всем материалам слоя — `git ls-tree -r --name-only 3fb7c8bf docs/ | wc -l` = 73, и каждый старый путь присутствует в справочнике «Бывшие пути» ([quickstart.md § 9](./quickstart.md)); 0 неучтённых (SC-009, SC-010) — **выполнено на 73/73**: счётчик `git ls-tree -r --name-only 3fb7c8bf docs/` = 73; из них 30 перенесены один-в-один, 11 свёрнуты (содержимое → реестр/компоненты), 32 подлежат записи в «Бывшие пути» (US5, T075)

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

- [x] T041 [P] [US2] Создать фикстуры `tools/fixtures/no-docs-dir/vulnerable/docs/` (файл-заглушка «ФИКСТУРА») и `tools/fixtures/no-docs-dir/clean/` (пусто) — по образцу `tools/fixtures/marker-escape/`
- [x] T042 [P] [US2] Создать фикстуры `tools/fixtures/ci-workflow-sync/divergent/` и `tools/fixtures/ci-workflow-sync/synced/` — две копии workflow, в `divergent` — лишний гейт
- [x] T043 [US2] Реализовать `tools/check-no-docs-dir.sh` — CLI-контракт [contracts/guards.md C1](./contracts/guards.md): аргумент `--root <dir>`, проверка отслеживаемых файлов `git ls-files -- docs/` и существования каталога на диске, коды 0 / 1 / 2, сообщение со ссылкой на ADR-0011
- [x] T044 [US2] Реализовать `tools/check-ci-workflow-sync.sh` — CLI-контракт [contracts/guards.md C2](./contracts/guards.md): сравнение **наборов гейтовых команд** двух копий workflow; `runs-on`, `timeout-minutes`, `if`/`needs` и комментарии **не сравнивать**
- [x] T045 [US2] Проверка фикстур guard'а «нет `docs/`»: `bash tools/check-no-docs-dir.sh --root tools/fixtures/no-docs-dir/vulnerable` → код 1; `--root tools/fixtures/no-docs-dir/clean` → код 0 (SC-005, 2 из 2)
- [x] T046 [US2] Проверка фикстуры синхронности: `bash tools/check-ci-workflow-sync.sh --root tools/fixtures/ci-workflow-sync/divergent` → код 1; `--root tools/fixtures/ci-workflow-sync/synced` → код 0

### Implementation for User Story 2

- [x] T047 [US2] Подключить guard «нет `docs/`» в **обоих** workflow: `.forgejo/workflows/lint.yml` и `.github/workflows/lint.yml` — отдельный шаг в джобе `docs-lint` (FR-012: в CI **обоих** конфигураций)
- [x] T048 [US2] Подключить проверку синхронности workflow в оба workflow: `.forgejo/workflows/lint.yml` и `.github/workflows/lint.yml`
- [x] T049 [US2] Подключить guard «нет `docs/`» в `.pre-commit-config.yaml` — hook `no-docs-dir`, `pass_filenames: false`, `always_run: true`, `stages: [pre-commit]`
- [x] T050 [US2] Создать `knowledge/adr/0011-merge-docs-into-knowledge.md` — YAML-frontmatter (`id: 0011-merge-docs-into-knowledge`, `title`, `status: Accepted`, `supersedes: 0007-adopt-knowledge-as-ssot`, `superseded_by: null`, `date: 2026-10-29`, `modules: [documentation]`, `tags: [ssot, livedocs, documentation, wayfinder]`); H1; блок `> **Supersedes**: ADR-0007 — в части п. 2, п. 4 и следствия 5. Остальные решения ADR-0007 остаются в силе.`; секции «Контекст и проблема» (источники — KARAOKE-320 и KARAOKE-323) / «Решение» / «Обоснование» (3 отклонённые альтернативы) / «Последствия» (4 обязательных пункта: каноничная проектная копия скилла и read-only зеркала; **инвариант рантайм-копии** `.dsh/skills/` ранг 100 перекрывает `.agents/` ранг 200; сравнительные таблицы generic ↔ Karaoke не трогаются; правка `audit-living-docs/SKILL.md:156`); числа BR-кодов — 510 / 267
- [x] T051 [US2] Проставить в `knowledge/adr/0007-adopt-knowledge-as-ssot.md` **только** метаданное `superseded_by: 0011-merge-docs-into-knowledge` — **тело не трогать** (append-only); проверить `git diff` = одна изменённая строка
- [x] T052 [US2] Обновить реестр `knowledge/adr/README.md` — строка про `0011-merge-docs-into-knowledge` + уточнение, что `0007` заменён **в части** (п. 2, п. 4, следствие 5)
- [x] T053 [US2] Проверка US2: [quickstart.md § 2](./quickstart.md), § 8 (ADR принят, старый помечен), § 10 (обе копии workflow содержат одинаковый набор проверок) — все коды 0; `grep -c "check-no-docs-dir" .forgejo/workflows/lint.yml` ≥ 1 и столько же в `.github/workflows/lint.yml`

**Checkpoint**: US2 полностью функциональна — возвращение `docs/` ловится локально и в CI.

---

## Phase 5: User Story 3 — Правка кода обновляет продуктовый документ (Priority: P2)

**Goal**: FR-009 указывает на живой путь; правило не вакуумно; второй профиль формата описан в
`knowledge/README.md`; структурная проверка охватывает продуктовую ветку.

**Independent Test**: per-feature документ без одной из шести секций → проверка указывает на файл
и секцию; агент, ищущий, где обновлять per-feature документ, находит один актуальный путь.

**Соответствует плану**: P4 (правила), часть P3 (линтер профиля A).

### Implementation for User Story 3

- [x] T054 [P] [US3] Обновить `AGENTS.md`: Tier-2 R-32 → `knowledge/product/features/<slug>.md`; § «Plane Workflow» source → `knowledge/guidelines/runbooks/tracker-setup.md`; § «Диагностика через docker logs» source → `knowledge/guidelines/runbooks/log-correlation.md`; Tier-1 «Процесс wayfinder» source → `knowledge/guidelines/runbooks/tracker-setup.md`; запись в Changelog; версия 3.7.1 → **3.7.2** (PATCH — меняются пути, не смысл)
- [x] T055 [P] [US3] Обновить `CLAUDE.md`: таблица «Где искать что» — per-feature → `knowledge/product/features/<slug>.md`; стратегия → `archive/docs/strategy/growth.md` + `archive/docs/strategy/growth-audit.md`; логи прода → `knowledge/guidelines/runbooks/log-correlation.md`; трекер → `knowledge/guidelines/runbooks/tracker-setup.md`; changelog PR → `archive/docs/architecture-notes.md`; строку «Настройка Claude Code» (`docs/claude-code-setup.md` — файла нет) → текст без ссылки; запись в Changelog; версия 1.8.1 → **1.8.2**
- [x] T056 [US3] Обновить `.specify/memory/constitution.md`: § VI FR-006 (строка 84: `@see docs/features/<slug>.md` → `knowledge/product/features/<slug>.md`) и FR-009 (путь); § VII (строки 103–104: ссылки на несуществующие `docs/onboarding.md`, `docs/claude-code-setup.md` → текст без ссылки; `docs/architecture-notes.md` → `archive/docs/architecture-notes.md`); § VIII (строка 131: `docs/migration-prod-server.md` — нет → текст без ссылки); § IX (строка 148: `docs/governance/knowledge-first.md` → `knowledge/guidelines/knowledge-first.md`); § Governance 1 (строки 278, 280–281: `docs/architecture-notes-archive.md` → текст без ссылки; `docs/onboarding.md`/`docs/claude-code-setup.md` → текст без ссылки; `docs/architecture-notes.md` → `archive/docs/architecture-notes.md`); § Governance 5 (строка 304: `docs/architecture-notes.md` → `archive/docs/architecture-notes.md`); **Sync Impact Report** в HTML-комментарии в начале файла; версия 2.4.0 → **2.4.1** (PATCH — смысл правил не меняется)
- [x] T057 [P] [US3] Обновить `.github/PULL_REQUEST_TEMPLATE.md` — 6 упоминаний `docs/`; чеклист FR-009 указывает `knowledge/product/features/<slug>.md`
- [x] T058 [P] [US3] Обновить `DEVELOPMENT.md` (5 упоминаний) и `README.md` (3 упоминания) — ссылки на перемещаемые файлы
- [x] T059 [US3] Добавить в `tools/lint-knowledge.py` **явный комментарий** рядом с `SECTION_EXEMPTIONS`: `knowledge/product/` обслуживается guard'ем FR-009, обход линтера туда не расширяется; убрать ссылку на несуществующий `docs/operations/knowledge-lint-baseline.md` (строка 16) — **исключения не расширять**
- [x] T060 [US3] Обновить `knowledge/README.md`: строка `product/` в таблице «Структура»; подсекция «Две ветки SSoT» с таблицей двух профилей (профиль A — компонент, профиль B — per-feature: где / шаблон / секции / проверка / что описывает); Karaoke-override п. 5 заменён на «`docs/` слит в `knowledge/`…»; расширенный шаг Initialize протокола агента; ссылка на ADR-0011
- [x] T061 [P] [US3] Обновить `knowledge/templates/README.md` — описать `product-feature.md` как шаблон второго профиля; добавить ссылку
- [x] T062 [P] [US3] Обновить `knowledge/guidelines/README.md` — строка `knowledge-first.md` (enforcement MUST #0) в таблице «Структура»; `kdoc-jsdoc.md` перенести из «Планируемого наполнения» в «Структуру»; термин «runbook» вместо «playbook»
- [x] T063 [P] [US3] Обновить `knowledge/guidelines/runbooks/README.md` — перечень runbook'ов; термин «runbook» вместо «playbook» (термин «playbook» в Универсальный язык не входит)
- [x] T064 [P] [US3] Добавить в `knowledge/public/glossary.md` секцию «Продуктовый слой (product layer)» с терминами: продуктовый слой / per-feature документ / runbook / реестр аудита #336 — таблица «термин · определение · источник»
- [x] T065 [P] [US3] Обновить проектную копию `.agents/skills/managing-living-docs/SKILL.md`: таблица Karaoke-overrides — строка про второй профиль `knowledge/product/`; § Impact Surface Analysis — уровень «Продуктовая проекция → `knowledge/product/features/<slug>.md`»; § Final Validation — добавить `bash tools/check-feature-doc.sh knowledge/product/features/*.md`; § Quick Reference — пункт про `knowledge/product/` и новый гейт; **сравнительные таблицы generic ↔ Karaoke (строки 10, 13, 36, 38) не трогать**
- [x] T066 [P] [US3] Обновить `.agents/skills/audit-living-docs/SKILL.md:156` — `docs/architecture-notes.md` → `archive/docs/architecture-notes.md`; **сравнительные таблицы (строки 11, 14, 33, 35, 37) не трогать**
- [x] T067 [US3] Проверка US3: `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt` — код 0; `bash tools/check-knowledge-structure.sh` — код 0; `grep -rnE "(^|[^[:alnum:]_/.-])docs/" AGENTS.md CLAUDE.md .specify/memory/constitution.md .github/PULL_REQUEST_TEMPLATE.md` — пусто (SC-007). Паттерн уточнён: литеральный `grep "docs/"` ловит и `archive/docs/…` (существующий архив) — ложное срабатывание; интересуют только ссылки на удалённый слой; `git diff --stat master -- .specify/memory/constitution.md AGENTS.md CLAUDE.md` — версии bumped

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

- [x] T068 [P] [US4] Создать фикстуры `tools/fixtures/audit-registry/orphan-code/` (компонент с `BR-999`, которого нет в реестре), `tools/fixtures/audit-registry/lost-code/` (код в реестре, отсутствующий в `knowledge/` и не помеченный «без зеркала»), `tools/fixtures/audit-registry/consistent/` (код в реестре + код в компоненте + помеченный «без зеркала»)
- [x] T069 [US4] Реализовать `tools/check-audit-registry.py` — CLI-контракт [contracts/guards.md C3](./contracts/guards.md): правило A «BR-код в `knowledge/**` (кроме реестра) есть в реестре», правило B «код реестра есть в `knowledge/**` либо помечен «без зеркала»»; коды 0 / 1 / 2

### Implementation for User Story 4

- [x] T070 [US4] Подключить guard «BR-код ↔ компонент» в оба workflow: `.forgejo/workflows/lint.yml` и `.github/workflows/lint.yml` — джоб структуры Knowledge
- [x] T071 [US4] Подключить guard в `.pre-commit-config.yaml` — hook `audit-registry`
- [x] T072 [US4] Проверка фикстур: `python3 tools/check-audit-registry.py --root tools/fixtures/audit-registry/orphan-code` → код 1; `--root .../lost-code` → код 1; `--root .../consistent` → код 0
- [x] T073 [US4] Сверить инвентарь: `grep -ohE "\bBR-[0-9]+\b" knowledge/system/infra/audit-336-registry.md | sort -u | wc -l` = **551** (510 из слоя `docs/features/` — SC-002 + 41 код, встречающихся только в `knowledge/`, § 6 реестра); перепроверить, что BR-258, BR-541, BR-601 присутствуют (FR-018)
- [x] T074 [US4] Проверка US4: `python3 tools/check-audit-registry.py` (на реальном дереве) — код 0; [quickstart.md § 4](./quickstart.md) — коды 0

**Checkpoint**: US4 полностью функциональна — ни один BR-код не потерян.

---

## Phase 7: User Story 5 — Старые ссылки остаются объяснимыми (Priority: P3)

**Goal**: справочник «бывшие пути» объясняет, куда переехал любой материал слоя `docs/`; уже
битые ссылки в `knowledge/` и корневых правилах починены.

**Independent Test**: открыть справочник «бывших путей» и убедиться, что каждый старый путь из
`docs/` присутствует; проверка целостности ссылок не падает на сознательные отсылки.

**Соответствует плану**: P2 (оставшиеся ссылки), P4 (`governance-docs.md`).

### Implementation for User Story 5

- [x] T075 [P] [US5] Добавить в `knowledge/README.md` раздел «Бывшие пути» — таблица «было → стало» на **все** материалы слоя `docs/`, включая удалённые и свёрнутые (FR-024); отдельный файл-редирект не заводим (иначе второй источник правды о путях)
- [x] T076 [P] [US5] Переписать ссылки на материалы, существующие в `archive/docs/`, — вести на `archive/docs/…` **с пометкой «архив»** (например `docs/strategy/growth.md` → `archive/docs/strategy/growth.md`); не удалять такие ссылки — это живые ссылки на живые документы
- [x] T077 [P] [US5] Заменить ссылки на несуществующие файлы — **текст без ссылки** либо пометка «документ не существует»: `knowledge/domains/integration/components/external-api-clients.md:106,120`, `knowledge/adr/0001…0004`, 10 строк `knowledge/system/infra/governance-docs.md`; осознанные отсылки → `config/knowledge/doc-references-whitelist.txt` с reason `honest-gap`
- [x] T078 [US5] Переписать `knowledge/system/infra/governance-docs.md` — убрать 10 несуществующих строк; строку per-feature заменить на `knowledge/product/features/<slug>.md` (индекс — `knowledge/product/README.md`); добавить строки `knowledge/guidelines/knowledge-first.md`, `knowledge/guidelines/kdoc-jsdoc.md`, `knowledge/guidelines/runbooks/`, `knowledge/product/README.md`, `knowledge/system/infra/audit-336-registry.md`; раздел «Archive» оставить как есть (FR-010)
- [x] T079 [US5] Проверка US5: `git ls-tree -r --name-only 3fb7c8bf docs/ | sed 's#^docs/##' | while read -r p; do grep -q "$p" knowledge/README.md || echo "НЕ ПОКРЫТО: docs/$p"; done` — пустой вывод; `python3 tools/check-doc-references.py` — код 0; `bash tools/check-knowledge-cross-links.sh` — код 0

**Checkpoint**: US5 полностью функциональна — старые ссылки объяснимы, битых ссылок не осталось.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: финальная валидация всей волны, операционные шаги вне git и отчёт в трекере.

**Соответствует плану**: P6 (ADR — T050…T052 выполнены в фазе US2), P7 (операционное).

- [x] T080 [P] Заменить копии рантайм-скиллов симлинками на канон: `.dsh/skills/managing-living-docs/` → `.agents/skills/managing-living-docs`, `.dsh/skills/audit-living-docs/` → `.agents/skills/audit-living-docs` (ранг 100 перекрывает ранг 200 ⇒ без этого правка git-версии не действует); каталог `.dsh/` — gitignored, в коммит не входит
- [x] T081 [P] Проверить синхронизацию скиллов: `sha256sum .agents/skills/managing-living-docs/SKILL.md .dsh/skills/managing-living-docs/SKILL.md` — совпадают; `ls -l .dsh/skills/managing-living-docs` — симлинк; `grep -n "architecture-notes" .agents/skills/audit-living-docs/SKILL.md` → `archive/docs/architecture-notes.md`
- [x] T082 [P] Проверить, что read-only-зеркала **не тронуты**: `sha256sum ~/.dsh/skills/managing-living-docs/SKILL.md` = `767b61dc…a63ae3`; `git -C /home/nsa/agents-team-srv status --porcelain .agents/skills/` — пусто; `~/.dsh/romario-team/**` не изменён
- [x] T083 Полный локальный прогон по [quickstart.md § 13](./quickstart.md) — все коды 0: `check-no-docs-dir.sh`, `check-ci-workflow-sync.sh`, `check-audit-registry.py`, `check-knowledge-structure.sh`, `check-knowledge-cross-links.sh`, `lint-knowledge.py`, `check-doc-references.py`, `check-feature-doc.sh`, `verify-doc-links.sh` (SC-006, SC-008)
- [x] T084 Проверка гигиены: `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$|\.p12$|\.pfx$'` — пусто; `git ls-files docs/` = 0; `git ls-files archive/docs/ | wc -l` — выросло только на `architecture-notes.md`
- [x] T085 Прогнать `pre-commit run --all-files` — все хуки Passed; прогнать скилл `kara-post-edit` (правки кода — только docstring'и `@see`)
- [x] T086 Собрать коммиты PR по порядку плана (перенос → ссылки → инструменты → правила → guard → ADR) с понятными сообщениями — ревью идёт по частям; merge-гейт — зелёный финальный CI
- [x] T087 Создать `specs/675-merge-docs-into-knowledge/report.md` — отчёт о выполнении (обязательный артефакт Plane-workflow)
- [ ] T088 После merge: `bash tools/tracker.sh add-comment 334 --file specs/675-merge-docs-into-knowledge/report.md` → `bash tools/tracker.sh mark-review 334`; далее — ревью владельца и `close-issue`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей — можно начинать сразу
- **Foundational (Phase 2)**: зависит от Setup — **блокирует все истории** (пути, на которые
  указывают ссылки, инструменты и правила, появляются здесь). Внутри фазы порядок исполнения:
  **T008–T014 → T016–T018 → T025 → T015 → T019–T022 → T023 → T024** (T025 — свёртка четырёх
  инфраструктурных документов — выполняется **до** T015: карточка PKG-S2-08 идёт в тот же
  реестр; и **до** T022: перечень в `knowledge/product/README.md` должен отражать итоговый
  состав продуктовой ветки)
- **Phase 2.5 (свёртка четырёх инфраструктурных документов)**: решение владельца принято
  2026-10-29 (базовая линия — 22), ожидания нет; задача T025 реализуется **до** T022 (перечень
  в `knowledge/product/README.md`) и до T019 (перенастройка вызовов `check-feature-doc.sh`) —
  иначе индекс и гейт считают несуществующие документы
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
3. Phase 2.5: свёртка четырёх инфраструктурных документов (T025 — решение владельца принято 2026-10-29)
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

## Открытые вопросы к лиду — все закрыты 2026-10-29

1. ~~**Судьба четырёх инфраструктурных пер-фичеурных документов (26 или 22)**~~ — **решено
   2026-10-29 (владелец)**: базовая линия — **22** пер-фичеурных документа (21 продуктовый +
   `tonality-bpm-songs-table`); четыре инфраструктурных документа в продуктовую ветку не
   входят и сворачиваются в `knowledge/system/infra/` + реестр аудита (FR-003, SC-004, коммит
   `47f31bb6`). Реализация — задача **T025**; ожидание снято.
2. ~~**FR-016 vs FR-014 на одной строке `knowledge/domains/README.md:115`**~~ — **решено
   2026-10-29 (владелец)**: граница FR-016 уточнена — **реестр 16 доменов** (список доменов) не
   меняется, **каталог файлов `knowledge/system/infra/*`** в том же файле обновляется в части
   удалённых и перенесённых документов (иначе остаётся ссылка на несуществующий файл, что
   запрещает FR-014/SC-003). Продуктовым слоем реестр доменов не дополняется (D19).
   Реализация — задача **T018**.
3. ~~**Промежуточные коммиты PR дадут красный CI**~~ — **решено 2026-10-29 (лид)**: принимается
   текущий компромисс — guard'ы в фазе US2 (T043, T047, T048 остаются на месте), `docs/`
   исчезает в фазе Foundational; мержается только финальный коммит по зелёному CI.

---

## Notes

- [P] — разные файлы, нет зависимостей
- [Story] — соответствие пользовательской истории спеки для трассируемости
- **T025 — единственное место, где судьба четырёх инфраструктурных документов описана
  явно**; число пер-фичеурных документов продуктовой ветки живёт в спеке (FR-003) и в T025,
  в остальных задачах формулировка «пер-фичеурные документы продуктовой ветки»
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
