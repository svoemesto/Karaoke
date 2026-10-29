# Implementation Plan: Слияние слоя `docs/` в `knowledge/` и запрет параллельной документации

**Branch**: `675-merge-docs-into-knowledge` | **Date**: 2026-10-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/675-merge-docs-into-knowledge/spec.md` (KARAOKE-334; карта
решений KARAOKE-323; 10 тикетов-решений KARAOKE-324…KARAOKE-333).

**Note**: план опирается на решения карты (`.scratch/karaoke/wayfinding/docs-merge/answers/*.md`),
research-артефакты (`inventory-docs.md`, `references-to-docs.md`, `skill-managing-living-docs.md`) и
**перепроверку фактов с диска** `master` @ `3fb7c8bf` от 2026-10-29. Расхождения между картой и
диском — [research.md § 3](./research.md#3-расхождения-карты-и-диска-обязательно-к-исполнению); одно из
них (§ 3.1, 26 против 22 per-feature документов) требует решения лида — план реализует базовую
линию «26» по спеке и карте.

---

## Summary

В репозитории два слоя проектной документации: `knowledge/` (архитектурный SSoT, ADR-0007) и
`docs/` (73 файла: 65 per-feature документов FR-009, 4 операционных runbook'а, корневые
`architecture-notes.md` и `tracker-setup.md`, гайд по KDoc/JSDoc, памятка MUST #0). `docs/` — не
второй SSoT: 30 его файлов — пакеты аудита #336 (содержимое уже продублировано в `knowledge/`),
25 — продуктовые решения (в `knowledge/` их нет и быть не может: они не описывают устройство
системы), 10 — мост-файлы между продуктовым решением и кодом. ADR-0007 следствием 5 отправил
`docs/` «на отдельную спецификацию» — это и есть KARAOKE-320, а карта KARAOKE-323 (10 решений
владельца) определяет, куда всё это девается.

План — **одна атомарная волна** (FR-023, D20): перенос всех 73 файлов через `git mv` (история
сохраняется), сворачивание мост-файлов и пакетов аудита, создание продуктовой ветки
`knowledge/product/` со вторым профилем формата, переписывание **каждой** ссылки на перемещаемый
файл, перенастройка 9 инструментов, обеих копий CI-конфигурации, pre-commit и whitelist,
обновление правил (`knowledge/README.md`, `AGENTS.md`, `CLAUDE.md`, конституция, PR-шаблон) и
проектных копий скиллов, три новых guard'а с фикстурами, ADR-0011 с частичной заменой ADR-0007.

Что меняется по существу:

1. **Две ветки одного SSoT.** Архитектурная (`knowledge/domains/`, `knowledge/system/`,
   `knowledge/guidelines/`, `knowledge/adr/`) и продуктовая (`knowledge/product/`) — со **вторым
   профилем формата** (FR-009 рядом с шаблоном компонента). Разделение — по каталогу, не по
   конфигу: два инструмента, два профиля, одна таблица в `knowledge/README.md`.
2. **Реестр аудита #336** (`knowledge/system/infra/audit-336-registry.md`) — носитель всех 510
   уникальных BR-кодов слоя `docs/features/` с обратным указателем «код → компонент» и явным
   списком кодов без зеркала. До слияния 267 кодов существовали только в `docs/`.
3. **Guard, закрывающий возврат.** Каталог `docs/` в корне запрещён и ловится в обоих workflow,
   в pre-commit и на фикстурах; плюс отдельная проверка синхронности двух копий workflow и
   проверка соответствия BR-кодов реестру.
4. **ADR-0011** частично заменяет ADR-0007 (отменяются п. 2, п. 4 и следствие 5; остальное в
   силе), Karaoke-override #5 в `knowledge/README.md` заменяется, появляется справочник
   «Бывшие пути».

Схема БД, рантайм-код, контейнеры и прод **не затрагиваются**: правится только содержимое
docstring'ов (`@see`, 74 в 51 файле).

---

## Technical Context

**Language/Version**: Markdown (Kotlin-разметка, без сырого HTML), YAML-frontmatter для ADR
(`knowledge/templates/adr.md`). Инструменты — bash 4+, Python 3 (`tools/lint-knowledge.py`,
`tools/check-doc-references.py`, `tools/check-ssot-impact.py`), lychee (проверка ссылок), ripgrep
(`tools/verify-kotlin-refs.sh`), pre-commit, markdownlint (`docs-lint`). Git ≥ 2.30 (`git mv`,
`git log --follow`).

**Primary Dependencies**: git; bash; python3 (+PyYAML — уже требуется `check-ssot-impact.py`);
lychee; ripgrep; node 22 (для `npx typedoc` в `tools/generate-docs.sh` — только при пересборке
API-документации, в этой волне не требуется).

**Storage**: файловая система репозитория. Базы данных не затрагиваются. Единственный новый
путь на диске — `build/api-docs/` для вывода генератора (в `.gitignore`, вне SSoT).

**Testing**: контрактные тесты исходников (JUnit 5, `karaoke-app/src/test/.../audit/PkgS335SourceContractsTest.kt` —
читает workflow и pre-commit как текст); guard'ы с фикстурами в `tools/fixtures/`; pre-commit-хуки;
CI в двух конфигурациях (Forgejo Actions + GitHub Actions). Отдельного набора unit-тестов для
логики нет — предмет миграции — документация.

**Target Platform**: Linux admin-машина (`nsa-i9`); CI — Forgejo Actions (`.forgejo/workflows/lint.yml`,
каноничная копия для проекта) и GitHub Actions (`.github/workflows/lint.yml`, вторая копия —
контрактный тест покрывает только её).

**Project Type**: документационная миграция в монорепозитории (Kotlin backend + два фронтенда).
Затрагиваются только слой документации, инструменты, CI-конфигурация, правила и ADR; рантайм-код
не меняется, кроме строк `@see` в KDoc/JSDoc.

**Performance Goals**: неприменимо (миграция документации). Единственное измеримое ограничение —
**ревьюимость**: порядок коммитов внутри PR (перенос → ссылки → инструменты → правила → guard →
ADR), каждый коммит — отдельное осмысленное изменение (D20, ответ 332 § «Риск, который
остаётся»).

**Constraints** (hard gates AGENTS.md v3.7.1):

| Ограничение | Как соблюдается в плане |
|---|---|
| Одна атомарная волна | FR-023; всё в одном PR; merge — только по зелёному финальному CI |
| Только feature-ветка + PR | ветка `675-merge-docs-into-knowledge`; `master` не коммитится и не мержится |
| `GRADLE_USER_HOME` / `DOCKER_CONFIG` от корня репозитория | правок кода нет; если понадобится сборка — по правилу |
| Контейнеры только через `deploy/do.sh`; `karaoke-app` не перезапускать | контейнеры не трогаются |
| Фронтенд — `cd webvue3 && npm run` | фронтенд не пересобирается (правок кода нет) |
| Секреты не коммитить | правок в `deploy/`, `*.env`, `*.key` нет; проверка в quickstart § 14 |
| ADR append-only | в ADR-0007 меняется **только** `superseded_by` |
| Правка `AGENTS.md` — governance-PR с semver bump | 3.7.1 → 3.7.2, запись в Changelog |
| Правка конституции — amend с Sync Impact Report | 2.4.0 → 2.4.1, Sync Impact Report в HTML-комментарии |
| `~/.dsh/skills/**`, `/home/nsa/agents-team-srv/**` — read-only | не трогаются; операционно правится только `.dsh/skills/` **внутри репозитория** (gitignored) |
| `archive/docs/**` — вне скоупа | существующие 43 файла не меняются; добавляется только `architecture-notes.md` |
| Grep по репозиторию — только с `--include` | `alignment-ml/` (20 ГБ) и `deploy/` (1,6 ГБ) исключены из всех прогонов |
| Не менять FR/SC молча | единственное расхождение (§ 3.1) вынесено лиду; FR/SC не меняются |

**Scale/Scope**:

| Величина | Значение |
|---|---|
| Файлов `docs/` | 73 (65 + 4 + 2 + 1 + 1) |
| Per-feature документов в продуктовой ветке | 26 (25 + `tonality-bpm-songs-table`) |
| Уникальных BR-кодов в реестре | 510 (267 без зеркала на момент до миграции) |
| Переписываемых ссылок | ~1 500 в 7 группах: `knowledge/` 104/38, корневые правила ~50/7, `tools/`+CI 30/9, `specs/` 104 md-ссылок/52, код `@see` 74/51, внутренние ссылки 65 файлов |
| Инструментов к перенастройке | 9 скриптов + whitelist + юнит-тест + `tools/README.md` + `PR_DESCRIPTION.md` |
| CI | 2 workflow + `.pre-commit-config.yaml` |
| Новых guard'ов | 3 (+ 3 набора фикстур) |
| Правил | `knowledge/README.md`, `AGENTS.md`, `CLAUDE.md`, конституция, PR-шаблон, `DEVELOPMENT.md`, `README.md`, glossary, guidelines, governance-docs |
| ADR | 1 новый + метаданное в 1 существующем + реестр |
| Скиллов (проектных копий) | 2 |

**NEEDS CLARIFICATION**: нет. Все технические неизвестные закрыты research-артефактами карты и
перепроверены на диске. Вопрос, требующий решения лида, один — [research.md § 3.1](./research.md#31-число-документов-в-продуктовой-ветке-26-fr-003-против-22-d4--d6--вопрос-лидувладельца);
план реализует базовую линию «26» (спека + Notes владельца), при которой FR-003 и SC-004
выполнимы без правки контракта.

---

## Constitution Check

*GATE: проверяется до Phase 0 и повторно после Phase 1.*

| Принцип | Вердикт | Обоснование |
|---|---|---|
| **I. Self-contained автопайплайн** | PASS | Внешних SaaS не добавляется; пайплайн исполнения не затрагивается. Генератор API-документации (Dokka/typedoc) остаётся локальным, только меняется каталог вывода (`build/api-docs/`). |
| **II. Сырой JDBC + дифф по хэшам** | PASS | Изменений в коде доступа к данным нет; правятся только строки `@see` в docstring'ах. |
| **III. Двух-БД синхронизация через SyncRegistry** | PASS | Новых сущностей, колонок и sync-флагов нет. `SyncRegistry.all`, `recordhash`-триггеры и миграции не затрагиваются. |
| **IV. Async-очередь + `redirectErrorStream(true)`** | PASS | `ProcessBuilder`-конвейер не затрагивается. |
| **V. Два фронтенда** | PASS | `webvue3` и `karaoke-public` не меняются; в `webvue3`/`karaoke-public` правятся только `@see`-строки в docstring'ах (17 и 8 файлов). |
| **VI. Code Standards (FR-006, FR-007, FR-009)** | **PASS с обязательным amend** | Смысл правил не меняется — **меняются пути**: FR-006 (`@see docs/features/<slug>.md` → `knowledge/product/features/<slug>.md`) и FR-009. По `constitution.md` § Governance 2 и 5 это оформляется amend'ом `docs: amend constitution to v2.4.1 (...)` с **Sync Impact Report** в HTML-комментарии в начале файла. FR-007 (ktlint/ESLint) не затрагивается. Спека FR-009 при этом **сохраняется** — меняется только путь (D17). |
| **VII. Cross-machine Setup** | PASS | Локальные AI-конфиги не меняются; `.git-blame-ignore-revs`/`.gitattributes` не затрагиваются. Ссылки конституции § VII на несуществующие `docs/onboarding.md` / `docs/claude-code-setup.md` превращаются в текст без ссылки (D13) — это починка, а не ослабление правила. |
| **VIII. Секреты и git-хигита** | PASS | Новых env/секретов нет; правок в `deploy/`, `deploy/.env`, `deploy/do.env` нет. Проверка `git ls-files \| grep -iE '\.env$\|...'` — обязательна (quickstart § 14). |
| **IX. Knowledge-first** | PASS | Pre-flight выполнен: `knowledge/README.md` и `knowledge/domains/README.md` прочитаны полностью, 3+ grep по `knowledge/` выполнены, `domain.md` целевых доменов и `local-*.md` ADR просмотрены, конституция прочитана. **Эта миграция сама является изменением SSoT** — Linking Protocol применяется к `knowledge/domains/**` (правки 3 ссылок на удаляемый `per-feature-docs.md` + 7 свёрнутых мост-файлов), к `knowledge/product/**` — **не применяется** (D19). |
| **IX.3 Subagent workspace isolation** | PASS | Субагент работает в общем checkout'е единственной feature-ветки; параллельных PR-веток на эту задачу нет — изоляция требуется при параллельной работе нескольких субагентов, здесь она не применима. |

**Прочие проектные ограничения**

| Ограничение | Как соблюдается |
|---|---|
| Git: только feature-ветка + PR + CI | ветка `675-merge-docs-into-knowledge`; push — **по слову лида** |
| Трекер (Plane) | issue `KARAOKE-334`; после merge — `add-comment` (файл `specs/675-merge-docs-into-knowledge/report.md`) → `mark-review` → ревью → `close-issue` |
| Скилл `kara-post-edit` | обязателен после правок кода — в этой волне правки кода есть только в `@see`-строках docstring'ов, скилл всё равно прогоняется |
| Скилл `kara-pre-commit` | обязателен перед каждым коммитом (7 проверок CI) |
| Клавиатура (grep без `--include`) | все прогоны — с `--include` по расширениям; `alignment-ml/` и `deploy/` исключены |
| Секреты | не читаются, не пишутся |

**Re-check после Phase 1 (design)**: нарушений не появилось. Отдельно проверено:
(а) разделение профилей по каталогу не создаёт второй конфиг линтера и не расширяет обход
`lint-knowledge.py` — пересечения исключены конструкцией (D14, D16);
(б) guard'ы не подменяют существующие проверки, а дополняют их: C1 проверяет **отсутствие**
`docs/`, `check-knowledge-structure.sh` — **наличие** каталогов `knowledge/`; C2 покрывает обе
копии workflow, тогда как контрактный тест покрывает только `.github/`;
(в) контракты ([contracts/](./contracts/)) не вводят новых внешних интерфейсов приложения —
это контракты структуры документации и CLI-контракты guard'ов;
(г) FR-003 (26 документов) остаётся достижимым в базовой линии плана.

---

## Project Structure

### Documentation (this feature)

```text
specs/675-merge-docs-into-knowledge/
├── plan.md                       # этот файл (/speckit.plan)
├── research.md                   # Phase 0: решения D1…D20, факты, расхождения карты и диска
├── data-model.md                 # Phase 1: сущности E1…E8, связи, инварианты, переходы
├── contracts/
│   ├── ssot-structure.md         # C1…C8: две ветки, два профиля, индексы, правила, ADR, скиллы
│   ├── migration-map.md          # поимённая судьба 73 файлов + правила переписывания ссылок
│   ├── guards.md                 # CLI-контракты трёх guard'ов + фикстуры + места вызова
│   └── tooling.md                # T1…T11: перенастройка 9 инструментов, CI, правил, тестов
├── quickstart.md                 # Phase 1: 15 сценариев проверки
├── checklists/requirements.md    # чек-лист спеки (существует)
├── spec.md                       # контракт (не меняется)
└── report.md                     # REQUIRED-артефакт для Plane (создаётся на шаге implement)
```

### Source Code (repository root)

```text
# ── НОВОЕ: продуктовая ветка SSoT ─────────────────────────────────────────────
knowledge/product/
├── README.md                                     # НОВЫЙ: индекс ветки + правило границы
└── features/                                     # НОВЫЙ: 26 per-feature документов (FR-009)
    └── <slug>.md                                 # ← git mv из docs/features/<slug>.md

# ── НОВОЕ: артефакты реестра и шаблона ────────────────────────────────────────
knowledge/system/infra/audit-336-registry.md       # НОВЫЙ: 510 BR-кодов + карточки пакетов
knowledge/templates/product-feature.md             # НОВЫЙ: шаблон профиля B
knowledge/adr/0011-merge-docs-into-knowledge.md    # НОВЫЙ: ADR (Accepted, частично заменяет 0007)

# ── ПЕРЕНОСЫ (git mv, история сохраняется) ───────────────────────────────────
docs/features/<slug>.md          → knowledge/product/features/<slug>.md   (26)
docs/features/<slug>.md          → реестр (свёртка)                       (30)
docs/features/<slug>.md          → компонент knowledge/… (свёртка)        (7)
docs/features/<slug>.md          → удаление (полное зеркало)               (2)
docs/ops/<slug>.md               → knowledge/guidelines/runbooks/<slug>.md (4)
docs/tracker-setup.md             → knowledge/guidelines/runbooks/tracker-setup.md
docs/api/README.md               → knowledge/guidelines/kdoc-jsdoc.md
docs/governance/knowledge-first.md→ knowledge/guidelines/knowledge-first.md
docs/architecture-notes.md       → archive/docs/architecture-notes.md
knowledge/system/infra/per-feature-docs.md        → удаление (индекс → product/README.md)

# ── ИНСТРУМЕНТЫ (перенастройка путей) ─────────────────────────────────────────
tools/check-no-docs-dir.sh                        # НОВЫЙ: guard «docs/ отсутствует»
tools/check-ci-workflow-sync.sh                   # НОВЫЙ: guard синхронности 2 workflow
tools/check-audit-registry.py                     # НОВЫЙ: guard «BR-код ↔ компонент»
tools/fixtures/no-docs-dir/{vulnerable,clean}/    # НОВЫЙ: фикстуры guard'а C1
tools/fixtures/ci-workflow-sync/{divergent,synced}/# НОВЫЙ: фикстуры guard'а C2
tools/fixtures/audit-registry/{orphan-code,lost-code,consistent}/  # НОВЫЙ: фикстуры C3
tools/check-doc-references.py                     # ROOTS, GENERATED_PREFIXES
tools/check-ssot-impact.py                        # EXCLUDE_PATTERNS, текст-ссылка
tools/verify-doc-links.sh                         # TARGETS lychee
tools/check-no-legacy-tbl-settings.sh             # цикл по per-feature
tools/verify-kotlin-refs.sh                       # TARGETS
tools/generate-docs.sh                            # вывод → build/api-docs/
tools/check-feature-doc.sh                        # usage-текст
tools/check-knowledge-structure.sh                 # +knowledge/product, +product-feature.md
tools/lint-knowledge.py                           # комментарий про knowledge/product/
tools/README.md                                   # документация вызова
config/knowledge/doc-references-whitelist.txt     # 4 строки со scope docs/…
.gitignore                                        # docs/api/… → build/api-docs/

# ── CI ─────────────────────────────────────────────────────────────────────────
.forgejo/workflows/lint.yml                       # lychee-таргеты, check-feature-doc, +2 guard'а
.github/workflows/lint.yml                         # то же (две копии синхронны)
.pre-commit-config.yaml                           # check-feature-doc, +hook guard'а

# ── ПРАВИЛА ───────────────────────────────────────────────────────────────────
knowledge/README.md                                # структура, 2 профиля, override #5, бывшие пути
knowledge/domains/README.md                       # ссылка :115 на удаляемый per-feature-docs.md
knowledge/system/infra/governance-docs.md          # переписан (D7)
knowledge/system/infra/pre-commit-config.md       # ссылка :183
knowledge/system/infra/ci-tools.md                # ссылка :414
knowledge/system/infra/deploy-overview.md          # фактическая часть deploy-hardening
knowledge/guidelines/README.md                    # knowledge-first.md, kdoc-jsdoc.md, «runbook»
knowledge/guidelines/runbooks/README.md           # 5 runbook'ов, «runbook»
knowledge/guidelines/architecture-conventions.md   # § R-46 + уникальное из 2 свёрнутых
knowledge/public/glossary.md                      # секция «Продуктовый слой»
knowledge/adr/README.md                           # реестр ADR (0011 + частичная замена 0007)
knowledge/adr/0007-adopt-knowledge-as-ssot.md     # ТОЛЬКО superseded_by
knowledge/domains/{catalog,storage,caching,publishing,processing,persistence}/…
                                                   # приёмники 7 свёрнутых мост-файлов
AGENTS.md                                         # R-32 + 3 ссылки, 3.7.1 → 3.7.2
CLAUDE.md                                         # таблица «Где искать что», 1.8.1 → 1.8.2
.specify/memory/constitution.md                   # FR-006/FR-009 + § VII/VIII/IX + Sync Impact
.github/PULL_REQUEST_TEMPLATE.md                  # чеклист FR-009
DEVELOPMENT.md, README.md                         # ссылки
PR_DESCRIPTION.md                                 # документация вызова
.agents/skills/managing-living-docs/SKILL.md       # Karaoke-override, Impact Surface, Final Validation, Quick Ref
.agents/skills/audit-living-docs/SKILL.md:156     # → archive/docs/architecture-notes.md

# ── КОД (только docstring'и, логика не меняется) ──────────────────────────────
karaoke-app/src/**, karaoke-web/src/**, webvue3/src/**, karaoke-public/src/**
                                                   # @see docs/features/… → knowledge/product/features/…
karaoke-app/src/test/.../audit/PkgS335SourceContractsTest.kt   # ассерты на новые пути
```

**Structure Decision**: структура документации меняется точечно — **ни один существующий
каталог `knowledge/` не перестраивается**; добавляются одна ветка (`knowledge/product/`), один
файл реестра, один шаблон и один ADR; удаляется один устаревший каталог
(`knowledge/system/infra/per-feature-docs.md`). Инструменты не переписываются — меняются
зашитые в них пути и добавляются три guard'а. Код приложения не меняется — правится только
содержимое docstring'ов. Это минимизирует «площадьSurface» PR: риск приходится на ссылки и
правила, а не на логику.

---

## Phase 0 — Research (итог)

Полный разбор — в [research.md](./research.md). Кратко:

| # | Решение | Суть |
|---|---|---|
| D1 | Две ветки одного SSoT | архитектурная (`domains/`, `system/`, `guidelines/`, `adr/`) + продуктовая (`product/`) |
| D2 | Второй профиль формата — таблицей | в `knowledge/README.md`; шаблон `knowledge/templates/product-feature.md` |
| D3 | `per-feature-docs.md` удаляется | индекс → `knowledge/product/README.md`; 3 ссылки переписать |
| D4 | Судьба 4 инфраструктурных per-feature | см. [research.md § 3.1](./research.md#31-число-документов-в-продуктовой-ветке-26-fr-003-против-22-d4--d6--вопрос-лидувладельца) |
| D5 | Реестр аудита — `knowledge/system/infra/audit-336-registry.md` | 6 разделов; отдельный ADR не нужен |
| D6 | 10 мост-файлов | 1 переезд, 7 свёрток, 2 удаления; BR-коды → реестр |
| D7 | Governance-слой | `knowledge-first.md` → `guidelines/`; `governance-docs.md` переписан |
| D8 | Глубина реестра | код + одна строка + ссылка на компонент; карточка пакета внутри реестра |
| D9 | Guard «BR-код ↔ компонент» | обязателен |
| D10 | Граница переписывания ссылок | каждая ссылка на перемещаемый файл; `archive/`, `livedocs/`, скиллы — нет |
| D11 | Пересчёт относительных путей | от нового места, не заменой подстроки |
| D12 | «Бывшие пути» — таблица в `knowledge/README.md` | отдельный файл не заводим |
| D13 | Попутная починка битых ссылок | включена (иначе «0 битых ссылок» недостижимо) |
| D14 | Разделение профилей — по каталогу | без новых конфигов |
| D15 | `check-knowledge-structure.sh` | +`knowledge/product`, +`product-feature.md` |
| D16 | `lint-knowledge.py` | без исключений, но с явным комментарием |
| D17 | `check-feature-doc.sh` | новый путь + архивный; все точки вызова |
| D18 | Частичная замена ADR-0007 | п. 2, п. 4, следствие 5; тело 0007 не трогаем |
| D19 | Продуктовый слой — категория, не домен | `domains/README.md` не меняется; Linking Protocol не применяется |
| D20 | Одна атомарная волна | перенос + ссылки + инструменты + правила + guard + ADR — вместе |

---

## Phase 1 — Design (итог)

- [data-model.md](./data-model.md) — сущности E1…E8 (продуктовый слой, per-feature документ,
  runbook, реестр аудита, профиль формата, бывшие пути, guard, ADR), связи, инварианты I-1…I-30,
  переходы состояний guard'ов, полная таблица «было → стало».
- [contracts/ssoT-structure.md](./contracts/ssoT-structure.md) — C1…C8: две ветки, два профиля,
  индексы и навигация, справочник бывших путей, правила верхнего уровня, ADR-0011, скиллы.
- [contracts/migration-map.md](./contracts/migration-map.md) — поимённая судьба всех 73 файлов,
  правила переписывания ссылок, полный перечень инструментов с зашитыми путями, инварианты M-1…M-7.
- [contracts/guards.md](./contracts/guards.md) — CLI-контракты трёх guard'ов: аргументы, логика,
  коды возврата, сообщения, фикстуры, места вызова.
- [contracts/tooling.md](./contracts/tooling.md) — T1…T11: перенастройка 9 инструментов, whitelist,
  CI, правил и контрактного теста; инварианты T-1…T-26.
- [quickstart.md](./quickstart.md) — 15 сценариев проверки, покрывающих SC-001…SC-011 и FR-001…FR-024.

---

## Implementation phases

Порядок коммитов внутри PR — по решению карты (D20): **перенос → ссылки → инструменты →
правила → guard → ADR**. Атомарность волны обеспечивается тем, что всё в одном PR и мержится
только по зелёному финальному CI; промежуточные коммиты — инструмент ревью, а не мержатся
по отдельности.

### P1. Перенос слоя `docs/` и целевая структура

**FR**: FR-001, FR-002, FR-003, FR-004, FR-005, FR-007, FR-008, FR-009, FR-011, FR-016, FR-017 (частично), FR-019, FR-022, FR-024.

1. **Перенос 73 файлов через `git mv`** — по карте [migration-map.md § 1–2](./contracts/migration-map.md#1-поимённая-карта--docsfeatures-65-файлов):
   - 26 per-feature → `knowledge/product/features/` (25 продуктовых + `tonality-bpm-songs-table`);
   - 5 → `knowledge/guidelines/runbooks/`; `docs/api/README.md` → `knowledge/guidelines/kdoc-jsdoc.md`;
     `docs/governance/knowledge-first.md` → `knowledge/guidelines/knowledge-first.md`;
     `docs/architecture-notes.md` → `archive/docs/architecture-notes.md`;
   - 30 пакетов аудита → содержимое в реестр (карточки пакетов + BR-коды).
   - Git не хранит пустые каталоги ⇒ после переноса `docs/` исчезает из репозитория сам; отдельная
     операция «удалить `docs/`» не нужна (D20).
2. **Создать** `knowledge/product/README.md` (индекс 26 документов + правило границы),
   `knowledge/templates/product-feature.md` (6 секций FR-009, slug == имя файла, статусы),
   `knowledge/system/infra/audit-336-registry.md` (6 разделов, 510 BR-кодов, карточки пакетов,
   список кодов без зеркала — вычисляется **после** переноса).
3. **Удалить** `knowledge/system/infra/per-feature-docs.md`; переписать 3 ссылки на него
   (`knowledge/domains/README.md:115`, `knowledge/system/infra/pre-commit-config.md:183`,
   `knowledge/system/infra/ci-tools.md:414`).
4. **Свернуть 7 мост-файлов** — уникальное содержание дописывается в компонент-приёмник
   (`storage-metadata-cache`, `stemjob-private-bucket` + BR-258, `tracker-local-integration` + BR-601,
   `zakroma-tiles-sort-order` + BR-541, `zakroma-albums-by-author`, `telegram-auto-publish`,
   `song-edit-and-censored`); 2 полных зеркала удаляются (`sync-audio-descendants`,
   `target-scoped-admin-requests`).
5. **Содержательные решения по 4 помеченным файлам** (D4) — уникальное содержание
   `gradle-9-upgrade` и `karaoke-db-module-removal` → `architecture-conventions.md` + `ci-tools.md`;
   BR-коды и фактическая часть `deploy-hardening` → реестр + `deploy-overview.md`.
6. **Перенастроить гейты, которые сломались бы от переноса** (в этом же коммите):
   `check-feature-doc.sh` — новые пути во всех точках вызова (оба workflow, `.pre-commit-config.yaml`);
   `check-knowledge-structure.sh` — +`knowledge/product` (+README), +`product-feature.md`.
   Обоснование: без этого промежуточный коммит не проходит pre-commit (glob по несуществующему
   каталогу не раскрывается ⇒ «проверка не выполнена»).
7. **Проверить**: `git log --follow` на перенесённом файле показывает исходную историю;
   `bash tools/check-feature-doc.sh knowledge/product/features/*.md archive/docs/features/*.md` — 0.

### P2. Переписывание ссылок

**FR**: FR-003, FR-014, FR-021, FR-024.

1. Порядок — `knowledge/` → `specs/` → `@see` в коде → корневые правила → инструменты и CI
   (D10, ответ 329 «Следствие для порядок работ»).
2. **Внутренние ссылки перенесённых файлов** — пересчёт **от нового места** (D11): цели внутри
   `knowledge/` — кратчайшая форма (`../../domains/x.md`), наружу — `../../../tools/…`.
3. **`specs/`** — только markdown-ссылки (104 в 52 файлах); текстовые упоминания не трогаются.
4. **Код** — `@see` в KDoc/JSDoc: 74 в 51 файле (karaoke-app 36, webvue3 17, karaoke-web 13,
   karaoke-public 8).
5. **Попутная починка уже битых ссылок** (D13): ссылки на существующие файлы `archive/docs/` →
   ведём на `archive/docs/…` с пометкой «архив»; ссылки на несуществующие — текст без ссылки либо
   whitelist `honest-gap`.
6. **Проверить после каждого шага**: `tools/check-knowledge-cross-links.sh` +
   `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt`.

### P3. Инструменты и CI

**FR**: FR-008, FR-012 (частично), FR-014, FR-022.

1. Перенастроить 9 инструментов по [contracts/tooling.md T1–T8](./contracts/tooling.md) —
   построчно, с сохранением кодов возврата и сообщений.
2. `tools/generate-docs.sh` → вывод в `build/api-docs/`; `.gitignore:97-99` → `build/api-docs/`.
3. `config/knowledge/doc-references-whitelist.txt` — переписать **4** строки со scope `docs/…`
   (55, 61, 62, 64); строки `archive/docs/…` (10) не трогать.
4. Обе копии workflow — одинаковый набор таргетов lychee и одинаковый вызов `check-feature-doc.sh`.
5. Обновить контрактный тест `PkgS335SourceContractsTest.kt:288,294,299` — тест **остаётся**.
6. `tools/lint-knowledge.py` — комментарий про `knowledge/product/` (D16) + убрать ссылку на
   несуществующий `docs/operations/knowledge-lint-baseline.md`.

### P4. Правила, governance и скиллы

**FR**: FR-004, FR-006, FR-007, FR-009, FR-010, FR-016, FR-020, FR-021.

1. **`knowledge/README.md`** — строка `product/` в таблице «Структура»; подсекция «Две ветки SSoT»
   с таблицей двух профилей; Karaoke-override п. 5 заменён; раздел «Бывшие пути» (73 строки);
   расширенный шаг Initialize протокола агента.
2. **`knowledge/system/infra/governance-docs.md`** — переписан (D7): 10 несуществующих строк убраны,
   строка per-feature заменена, 5 новых мест добавлены, раздел «Archive» без изменений.
3. **Корневые правила** — `AGENTS.md` (R-32 + 3 ссылки, 3.7.1 → 3.7.2), `CLAUDE.md` (таблица, 1.8.1 →
   1.8.2), `.specify/memory/constitution.md` (FR-006/FR-009, § VII/VIII/IX, Governance 1,
   **Sync Impact Report**, 2.4.0 → 2.4.1), `.github/PULL_REQUEST_TEMPLATE.md`, `DEVELOPMENT.md`,
   `README.md`, `PR_DESCRIPTION.md`, `tools/README.md`.
4. **`knowledge/public/glossary.md`** — секция «Продуктовый слой» с 4 терминами (D19);
   **`knowledge/guidelines/README.md`** и **`runbooks/README.md`** — «runbook» вместо «playbook».
5. **Скиллы (проектные копии)** — `.agents/skills/managing-living-docs/SKILL.md` (Karaoke-override,
   Impact Surface Analysis, Final Validation, Quick Reference) и
   `.agents/skills/audit-living-docs/SKILL.md:156` → `archive/docs/architecture-notes.md`.
   Сравнительные таблицы generic ↔ Karaoke **не трогаются**.

### P5. Guard'ы и CI-обвязка

**FR**: FR-012, FR-013, FR-015.

1. **`tools/check-no-docs-dir.sh`** — CLI-контракт C1; фикстуры `tools/fixtures/no-docs-dir/{vulnerable,clean}/`.
2. **`tools/check-ci-workflow-sync.sh`** — C2; фикстуры `tools/fixtures/ci-workflow-sync/{divergent,synced}/`.
3. **`tools/check-audit-registry.py`** — C3; фикстуры `tools/fixtures/audit-registry/{orphan-code,lost-code,consistent}/`.
4. **Оба workflow** — шаг guard'а «нет `docs/`» + шаг проверки синхронности workflow + шаг
   «BR-код ↔ компонент» (FR-012: в CI **обоих** конфигураций).
5. **`.pre-commit-config.yaml`** — hook guard'а «нет `docs/`».
6. **Проверить**: фикстуры дают 2/2 ожидаемых исхода (SC-005); `bash tools/check-ci-workflow-sync.sh` — 0.

### P6. ADR-0011 и реестр ADR

**FR**: FR-017.

1. **`knowledge/adr/0011-merge-docs-into-knowledge.md`** — YAML-frontmatter, H1, блок
   `> **Supersedes**: ADR-0007 — в части п. 2, п. 4 и следствия 5. Остальные решения ADR-0007
   остаются в силе.`, секции шаблона; § «Контекст» — источники **KARAOKE-320** и **KARAOKE-323**;
   § «Обоснование» — 3 отклонённые альтернативы; § «Последствия» — 4 обязательных пункта
   (включая инвариант рантайм-копии скилла).
2. **`knowledge/adr/0007-adopt-knowledge-as-ssot.md`** — **только** `superseded_by:
   0011-merge-docs-into-knowledge`; тело не трогаем (append-only).
3. **`knowledge/adr/README.md`** — строка про 0011 + уточнение, что 0007 заменён в части.

### P7. Операционные шаги вне git (на машине агента)

**FR**: FR-020.

1. `.dsh/skills/managing-living-docs/` и `.dsh/skills/audit-living-docs/` — **заменить копии
   симлинками** на `.agents/skills/…` (ранг 100 перекрывает ранг 200 ⇒ без этого правка git-версии
   не доходит до агента). Каталог `.dsh/` — gitignored, в коммит не входит.
2. Проверить совпадение `sha256sum` канона и рантайм-копии.
3. Убедиться, что `~/.dsh/skills/**` и `/home/nsa/agents-team-srv/**` **не изменены**
   (read-only; «правка зеркала без правки исходника запрещена»).
4. Передать `report.md` в Plane: `tracker.sh add-comment 334 --file specs/675-merge-docs-into-knowledge/report.md`
   → `mark-review 334` (после merge).

---

## Complexity Tracking

Нарушений Constitution Check нет. Осознанные упрощения и компромиссы (альтернативы отклонены в
[research.md § 1](./research.md#1-решения-d1d20--свод-ответов-карты)):

| Решение | Почему | Что отклонено |
|---|---|---|
| Разделение профилей **по каталогу**, без конфигу | два инструмента, два профиля; пересечения исключены конструкцией; линтер профиля A туда не заходит | единый «профиль документа» в линтере (преждевременно: профилей ровно два, с разными инструментами) |
| «Бывшие пути» — таблица в `knowledge/README.md` | один источник правды о путях | отдельный файл-редирект (стал бы вторым источником) |
| Реестр аудита — **средняя** глубина карточек | 5–10 тыс. строк дублирования не нужны; полные описания остаются в git-истории | полные карточки дефектов (до-после, тесты, разбор) |
| `docs/` «не трогаем» в `archive/`, `livedocs/`, скиллах | вне скоупа; переписывание даст 2000+ правок без выигрыша | массовое переписывание архива и сравнительных таблиц |
| Встраивание guard'ов в оба workflow, а не в один | FR-012 прямо требует обоих; рассинхрон — реальный риск | только `.github/` (не ловит Forgejo-рассинхрон) |
| Атомарная волна = **один PR**, а не один коммит | 73 файла + ссылки + инструменты одним коммитом неревьюимы; KARAOKE-332 прямо допускает 2–3 коммита внутри PR | 2–3 волны (промежуточное состояние с вакуумным FR-009 и窗口ом для возврата `docs/`) |
| 4 «инфраструктурных» per-feature — переносятся (базовая линия) | FR-003 и SC-004 требуют 26 документов; Notes владельца требуют переноса всех 25; ответ 324 § 5 требует иной судьбы для тех же 4 файлов | буквальное прочтение ответа 324 (⇒ 22 документа ⇒ правка FR-003 — только словом владельца) |

---

## Риски и открытые вопросы

### К лиду — нужно решение (не блокирует реализацию по базовой линии)

1. **26 против 22 per-feature документов** — [research.md § 3.1](./research.md#31-число-документов-в-продуктовой-ветке-26-fr-003-против-22-d4--d6--вопрос-лидувладельца).
   Спека (FR-003, SC-004) и Notes владельца требуют **26**; ответ 324 § 5 требует иной судьбы
   для 4 файлов группы а.1, что даёт **22**. Оба прочтения несовместимы.
   **Базовая линия плана — 26** (спека + Notes; FR/SC не меняются). Если выбирается 22 — это
   **правка контракта** (FR-003, SC-004), то есть только по слову владельца; тогда задачи P1.1 и
   P1.5 корректируются.
2. **«11 строк со scope `docs/…`» в whitelist** — на диске **4** строки (55, 61, 62, 64);
   «11» — совпадение с числом строк scope `knowledge/system` (research.md § 3.2). План переписывает
   4. Если лид считает по-другому — уточнить, какие именно строки имеются в виду.

### Риски (снижаемые, не блокирующие)

3. **Промежуточные коммиты PR дают красный CI** — guard'ы и проверки «нет `docs/`» включаются в
   P5, а `docs/` исчезает в P1. До P5 CI-джоб guard'а (если он уже есть) был бы красным. Компромисс
   осознанный (ревьюимость важнее зелёного промежуточного состояния); мержается только финальный
   коммит по зелёному CI. Альтернатива — вынести guard'ы в P1 (P1 становится неревьюимым).
4. **Объём переписывания ссылок** (~1 500 строк в 7 группах) — наибольший источник пропусков.
   Снижение: скрипт пересчёта относительных путей **от нового места** (не замена подстроки) +
   гейты `check-knowledge-cross-links.sh`, `check-doc-references.py`, lychee после каждой группы.
   Текстовые упоминания в `specs/` (~1600) **по политике проекта не трогаются** — это известное
   исключение, а не пропуск.
5. **Число «кодов без зеркала» меняется после переноса** — 267 измерено **до** миграции; 25
   продуктовых документов уезжают в `knowledge/`, поэтому часть кодов получает зеркала, и список
   станет ≤ 267. Инвариант FR-011 — «явный список кодов, не имеющих зеркала» — считается **по
   итоговому состоянию**; число 510 (инвентарь) неизменно.
6. **Две копии workflow расходятся и после правки** — поэтому отдельный guard C2. Риск: guard
   слишком строгий и начнёт ловить нерелевантные различия. Снижение: сравниваются только
   **гейтовые команды**, инфраструктурные различия (`runs-on`, `timeout-minutes`) исключены.
7. **Правка `AGENTS.md` — governance-документ** — по правилам проекта меняется владельцем через
   спецпроцедуру. Карта прямо включает `AGENTS.md` в усилие; план оформляет это как governance-PR
   с semver bump 3.7.1 → 3.7.2 и записью в Changelog. Требует внимания лида при ревью PR.
8. **Конституция требует amend, а не молчаливой правки** — 2.4.0 → 2.4.1 + Sync Impact Report в
   HTML-комментарии. Смысл правил не меняется (путь), поэтому версия — PATCH.
9. **Рантайм-копия скилла перекрывает git-версию** — если P7 пропущен, правка скиллов не дойдёт
   до агента (ранг 100 перекрывает 200). Поэтому P7 — обязательный шаг, а не опциональный.
10. **Волна 2 (дочистка) не входит в приёмку** — попутно обнаруженные битые ссылки, остатки
    `specs/`, синхронизация рантайм-копий на других машинах — отдельная задача после зелёного CI.
11. **`lychee` может быть не установлен** — сценарий 5.4 quickstart не выполнится без него;
    остальные сценарии проверяются штатно. Установка — `cargo install lychee`.
12. **Греп по репозиторию без `--include`** — `alignment-ml/` (20 ГБ) и `deploy/` (1,6 ГБ);
    все прогоны в плане используют `--include` по расширениям.

---

## Rollout / rollback

**Rollout**

| # | Шаг | Проверка |
|---|---|---|
| 1 | Коммиты P1…P6 в ветку `675-merge-docs-into-knowledge` | `git log --oneline master..HEAD` — 6–7 коммитов с понятными сообщениями |
| 2 | Полный локальный прогон | quickstart § 13 — все коды 0 |
| 3 | Push + PR (база `master`) | по слову лида |
| 4 | CI в обеих конфигурациях | зелёный финальный коммит; `gh pr checks` |
| 5 | Ревью лида по категориям | перенос → ссылки → инструменты → правила → guard → ADR |
| 6 | Merge | `--merge`, **без** `--delete-branch` |
| 7 | P7 — симлинки рантайм-копий скиллов | `sha256sum` совпадает |
| 8 | Plane: `add-comment` (report.md) → `mark-review` → ревью владельца → `close-issue` | `tools/tracker.sh` |

**Rollback**

| Ситуация | Действие |
|---|---|
| Ревизия PR до merge | `git reset --hard master` в ветке + пересоздать PR; `master` не затронут |
| Проблема после merge | `git revert` коммитов PR в обратном порядке (P6 → P1) + отдельный PR; либо revert одного коммита, если проблема локальна (например, guard'а) |
| Перенос `docs/` оказался неверным | revert P1 возвращает 73 файла на место (`git mv` сохраняет содержимое; правки ссылок — revert'ятся отдельно) |
| Расхождение по 26/22 | ветка не мержится; лид уточняет базовую линию; корректируются P1.1/P1.5 и (при выборе 22) — FR-003 по слову владельца |

**Откат не трогает**: `archive/docs/**` (кроме добавленного `architecture-notes.md`, который
откатывается вместе с P1), `~/.dsh/skills/**`, `/home/nsa/agents-team-srv/**`, прод, контейнеры, БД.

---

## Знания (Knowledge References)

| Документ | Что взято |
|---|---|
| `knowledge/README.md` | структура SSoT, Karaoke-override п. 5 (заменяется), Linking Protocol, протокол агента |
| `knowledge/domains/README.md` | реестр 16 доменов (не меняется), строка :115 на удаляемый `per-feature-docs.md` |
| `knowledge/adr/0007-adopt-knowledge-as-ssot.md` | ADR, частично заменяемый ADR-0011 (п. 2, п. 4, следствие 5) |
| `knowledge/adr/README.md` | правила нумерации ADR; следующий свободный ID — 0011 |
| `knowledge/adr/0010-lm-studio-instead-of-ollama.md` | образец **частичной** замены ADR + стиль блока `> **Supersedes**:` |
| `knowledge/adr/local-0012-plane-tracker-migration.md` | прецедент ADR о миграции трекера |
| `knowledge/templates/adr.md` | формат frontmatter нового ADR |
| `knowledge/templates/README.md` | правила шаблонов; место шаблона профиля B |
| `knowledge/guidelines/README.md` | индекс guidelines; «Планируемое наполнение» → `kdoc-jsdoc.md` |
| `knowledge/guidelines/runbooks/README.md` | формат runbook'а (How to / Контекст / Шаги / Проверка / Rollback) |
| `knowledge/system/infra/governance-docs.md` | каталог governance-слоя — переписывается (D7) |
| `knowledge/system/infra/per-feature-docs.md` | устаревший каталог per-feature — удаляется (D3) |
| `knowledge/system/infra/tracker.md` | источник деталей по трекеру; `docs/tracker-setup.md` становится runbook'ом |
| `knowledge/system/infra/ci-tools.md`, `pre-commit-config.md` | ссылки на удаляемый `per-feature-docs.md` (:414, :183) |
| `knowledge/system/infra/deploy-overview.md` | приёмник фактической части `deploy-hardening` |
| `knowledge/guidelines/architecture-conventions.md` | приёмник § R-46 и уникального содержания 2 свёрнутых файлов |
| `knowledge/public/glossary.md` | публичная проекция Универсального языка — место термина «продуктовый слой» |
| `knowledge/domains/catalog/components/audio-descendant-sync.md` | зеркало удаляемого `sync-audio-descendants` |
| `knowledge/system/frontend/target-request-guard.md` | зеркало удаляемого `target-scoped-admin-requests` |
| `knowledge/domains/{storage,caching,catalog,publishing,processing,persistence}/…` | приёмники 7 свёрнутых мост-файлов |
| `.specify/memory/constitution.md` | Principles I–IX, FR-006/FR-009, Governance 2/5 (amend + Sync Impact Report) |
| `AGENTS.md` v3.7.1 | MUST #0, Tier-2 R-32, Plane workflow, правила guard'ов |
| `CLAUDE.md` v1.8.1 | таблица «Где искать что» |
