# Research — Слияние слоя `docs/` в `knowledge/` (Phase 0)

**Feature**: `675-merge-docs-into-knowledge` | **Дата**: 2026-10-29 | **Issue**: `KARAOKE-334`
**Источник**: карта решений `KARAOKE-323` (тело — `.scratch/karaoke/wayfinding/docs-merge/map.md`),
10 тикетов-решений `KARAOKE-324…333` (ответы — `.scratch/karaoke/wayfinding/docs-merge/answers/`),
research-артефакты (`research/inventory-docs.md`, `research/references-to-docs.md`,
`research/skill-managing-living-docs.md`).

> **Снимок**: все числа перепроверены с диска `master` @ `3fb7c8bf` (ветка `675-merge-docs-into-knowledge`)
> 2026-10-29. Расхождения между картой и диском — § 3, явно и поимённо.

---

## 1. Решения (D1…D20) — свод ответов карты

| # | Решение | Источник | Суть |
|---|---|---|---|
| D1 | **Две ветки одного SSoT** | KARAOKE-324 | Архитектурная — `knowledge/domains/`, `knowledge/system/`, `knowledge/guidelines/`, `knowledge/adr/`; продуктовая — `knowledge/product/` (per-feature FR-009 + `knowledge/product/README.md` как индекс). |
| D2 | **Второй профиль формата — таблицей** | KARAOKE-324 | В `knowledge/README.md` — таблица «Профиль A (компонент) ↔ Профиль B (per-feature)»: где / шаблон / секции / проверка / что описывает. Шаблон профиля B — новый `knowledge/templates/product-feature.md`; `knowledge/templates/README.md` обновляется. |
| D3 | **`knowledge/system/infra/per-feature-docs.md` — удалить** | KARAOKE-324 | Файл объявлял 7 документов при фактических 65. Роль индекса → `knowledge/product/README.md`. Три ссылки переписать: `knowledge/domains/README.md:115`, `knowledge/system/infra/pre-commit-config.md:183`, `knowledge/system/infra/ci-tools.md:414`. |
| D4 | **Судьба 4 инфраструктурных per-feature** | KARAOKE-324 | `private-docker-registry.md` — удалить; `gradle-9-upgrade.md` и `karaoke-db-module-removal.md` — свернуть в `architecture-conventions.md` + `ci-tools.md`; `deploy-hardening.md` — в реестр аудита (PKG-S2-08) + фактическая часть в `deploy-overview.md`. **Принято решением владельца 2026-10-29 — см. § 3.1.** |
| D5 | **Реестр аудита — `knowledge/system/infra/audit-336-registry.md`** | KARAOKE-324/327 | 6 разделов: назначение и границы / сводка / таблица кодов / карточки пакетов / коды без зеркала / changelog. Отдельный ADR для реестра не нужен (ADR фиксируют решения, реестр — данные). |
| D6 | **10 мост-файлов** | KARAOKE-325 | Правило: сворачивается, если уникальное содержание техническое; переезжает в `knowledge/product/features/`, если это продуктовое решение. Уникальные BR-коды → реестр, а не в продуктовую ветку. Поимённо: 1 переезд (`tonality-bpm-songs-table`), 7 свёрток, 2 удаления. |
| D7 | **Governance-слой** | KARAOKE-326 | `docs/governance/knowledge-first.md` → `knowledge/guidelines/knowledge-first.md` (+ строка в `knowledge/guidelines/README.md`). `knowledge/system/infra/governance-docs.md` переписывается: 10 несуществующих строк убраны, строка per-feature заменена, добавлены 5 новых мест. |
| D8 | **Глубина реестра аудита** | KARAOKE-327 | Запись = `BR-NNN` · одна строка «что сломано → что стало» · ссылка на компонент-зеркало · пакет. Полные описания дефектов не дублируются. Карточка пакета — блок внутри реестра. Обратный указатель «код → компонент» обязателен. |
| D9 | **Guard «BR-код ↔ компонент»** | KARAOKE-327 | Каждый `BR-NNN` в `knowledge/` есть в реестре; каждый код реестра — в `knowledge/` либо помечен «нет зеркала». |
| D10 | **Граница переписывания ссылок** | KARAOKE-328 | Переписывается **каждая ссылка на перемещаемый файл**, где бы она ни была: `knowledge/`, корневые правила, `tools/`+CI (обе копии workflow), `specs/` (только md-ссылки), код (`@see` KDoc/JSDoc), внутренние ссылки перенесённых файлов. `archive/`, `livedocs/`, `.agents/skills/`, `~/.dsh/skills/` — **не трогать**. |
| D11 | **Пересчёт относительных путей** | KARAOKE-328 | Пересчёт **от нового места**, а не заменой подстроки. Цели внутри `knowledge/` — кратчайшая относительная форма (`../../domains/x.md`, без повторения `knowledge/`); наружу — `../../../tools/…`. |
| D12 | **«Бывшие пути» — таблица в `knowledge/README.md`** | KARAOKE-328 | Отдельный файл-редирект не заводим (иначе второй источник правды о путях). |
| D13 | **Попутная починка битых ссылок — включена** | KARAOKE-328 | Ссылка на файл, существующий в `archive/docs/` → ведём на `archive/docs/…` с пометкой «архив». Ссылка на несуществующий файл → текст без ссылки либо whitelist `honest-gap`. |
| D14 | **Разделение профилей — по каталогу, не конфигом** | KARAOKE-329 | Линтер профиля A обходит `knowledge/domains`, профиля B — `knowledge/product/features`. Новых конфигов нет. |
| D15 | **`check-knowledge-structure.sh`** | KARAOKE-329 | `knowledge/product` в список каталогов; `product-feature.md` в проверку шаблонов («[2/9]»). |
| D16 | **`lint-knowledge.py`** | KARAOKE-329 | Исключений не требует (обходит только `knowledge/domains`) — нужен **явный комментарий** в коде: `knowledge/product/` обслуживается guard'ем FR-009. |
| D17 | **`check-feature-doc.sh` — один новый путь + архивный** | KARAOKE-329 | Вызов: `bash tools/check-feature-doc.sh knowledge/product/features/*.md archive/docs/features/*.md`. Перенастроить все точки вызова: оба workflow, `.pre-commit-config.yaml`, `PR_DESCRIPTION.md:131`, `tools/README.md:22`. |
| D18 | **Частичная замена ADR-0007** | KARAOKE-330 | Отменяются только п. 2, п. 4 и следствие 5. Новый ADR — `knowledge/adr/0011-merge-docs-into-knowledge.md` (YAML-frontmatter + блок `> **Supersedes**:` после H1, по образцу ADR-0010). В ADR-0007 — **только** метаданное `superseded_by`, тело не трогаем (append-only). Частичность фиксируется в новом ADR и в `knowledge/adr/README.md`. |
| D19 | **Продуктовый слой — структурная категория, не домен** | KARAOKE-331 | `knowledge/domains/README.md` **не меняется** (16 доменов); Linking Protocol к `knowledge/product/**` не применяется; родитель — `knowledge/product/README.md`. Термин — в `knowledge/README.md` (канон) и `knowledge/public/glossary.md` (публичная проекция). «playbook» в Универсальный язык не входит. |
| D20 | **Одна атомарная волна** | KARAOKE-332 | Перенос, ссылки, инструменты, правила, guard, ADR — в одном PR. Git не хранит пустые каталоги ⇒ отдельная волна «удалить `docs/`» не нужна; guard приземляется вместе с удалением; правила и инструменты совпадают с фактом в один момент. Вторая волна — дочистка, вне этой спеки. |

### Отклонённые альтернативы (зафиксированы решениями карты)

| Альтернатива | Почему отклонена | Решение |
|---|---|---|
| Оставить `docs/` отдельным слоем | ADR-0007 следствие 5 требовало отдельной спецификации — спека написана (KARAOKE-320) | D20, D18 |
| Полная отмена ADR-0007 | ADR-0007 ввёл Linking Protocol и запрет документов вне `knowledge/templates/` — это остаётся в силе | D18 |
| Конвертация 65 per-feature в шаблон компонента | Форматы несовместимы: «Зачем» и «Известные ловушки» аналогов в профиле A не имеют | D1, D2 |
| Два линтера-профиля как один конфиг | Ровно два профиля с разными инструментами; общая абстракция преждевременна | D14, D15 |
| Отдельный файл «бывшие пути» | Второй источник правды о путях | D12 |
| `knowledge/product/` как домен (17-й домен) | Продуктовые решения не имеют общего языка домена и инвариантов, проверяемых кодом | D19 |
| Две волны миграции | Промежуточное состояние, в котором FR-009 вакуумна, а `docs/` может вернуться незамеченным | D20 |

---

## 2. Факты с диска (перепроверено 2026-10-29)

### 2.1. Слой `docs/` — 73 файла

`git ls-files docs/ | wc -l` → **73**. Состав: `docs/features/` — 65, `docs/ops/` — 4, корень — 2 (`architecture-notes.md`, `tracker-setup.md`), `docs/api/README.md` — 1, `docs/governance/knowledge-first.md` — 1.

Классификация `docs/features/` (25 + 30 + 10 = 65, `research/inventory-docs.md` § 3а):

| Группа | Файлов | Итог решения |
|---|---|---|
| а.1 Продуктовые фичи | 25 | 21 → `knowledge/product/features/`; 4 — по D4 (см. § 3.1) |
| а.2 Пакеты аудита #336 | 30 | → карточки + BR-коды в реестр (`audit-336-registry.md`) |
| а.3 Мост-файлы | 10 | 1 → `knowledge/product/features/`; 7 → свёртка; 2 → удаление |

### 2.2. BR-коды

| Величина | Значение | Согласуется |
|---|---|---|
| Уникальных `BR-NNN` в `docs/features/` | **510** | FR-011, SC-002 ✓ (совпадает) |
| Упоминаний `\bBR-\d+\b` | 827 | расходится с «530» в ответе 327 (методика подсчёта) |
| Уникальных кодов, отсутствующих в `knowledge/` (до миграции) | 267 | ответ 327 (инвентарь называл 274) |
| Файлов `docs/features/`, содержащих BR-коды | 43 | ответ 327: «32 файла держат 507 кодов» — расхождение методики |
| `docs/features/deploy-hardening.md` | 31 уникальный BR-код | ответ 327 (PKG-S2-08) ✓ |

**Следствие для плана**: инвентарь BR-кодов фиксируется **510** (совпадает с FR-011). Множество «кодов без зеркала» вычисляется **после** переноса (коды из продуктовых документов окажутся в `knowledge/` и получат зеркало) ⇒ оно ≤ 267.

### 2.3. Ссылки

| Группа | Объём (карта) | Проверка на диске |
|---|---|---|
| `knowledge/` | 104 упоминания в 38 файлах | `grep -rn "docs/" knowledge/ --include=*.md` → **142** строки (включая `archive/docs/`) — карта считала actionable-упоминания `docs/` без `archive/docs/` |
| Корневые правила | ~50 в 7 файлах | `AGENTS.md`, `CLAUDE.md`, `.specify/memory/constitution.md`, `PR_DESCRIPTION.md`, `.github/PULL_REQUEST_TEMPLATE.md`, `DEVELOPMENT.md`, `README.md` — подтверждено |
| `tools/` + CI | 30 строк в 9 файлах | подтверждено, состав ниже |
| `specs/` | 104 md-ссылки в 52 файлах | правятся **только** md-ссылки; текстовые — нет |
| Код `@see` | 74 в 51 файле | karaoke-app 36, webvue3 17, karaoke-web 13, karaoke-public 8 |

### 2.4. Инструменты с зашитыми путями (факт на диске)

| Файл | Строки | Что зашито |
|---|---|---|
| `tools/check-doc-references.py` | 44, 54 | `ROOTS = ("knowledge", "docs", "archive/docs", ".github")`; `GENERATED_PREFIXES = ("docs/api/dokka/", "docs/api/typedoc-")` |
| `tools/check-ssot-impact.py` | 33 | `EXCLUDE_PATTERNS` содержит `r"^docs/"` — исключение слоя `docs/` из «значимых» изменений |
| `tools/verify-doc-links.sh` | 48 | `TARGETS=("docs/features/" "CONTRIBUTING.md" "docs/")` (путь lychee) |
| `tools/check-no-legacy-tbl-settings.sh` | 174 | `for f in docs/features/*.md` — проверка SQL-блоков |
| `tools/verify-kotlin-refs.sh` | 18 | `TARGETS=(docs/features/*.md)` |
| `tools/generate-docs.sh` | 5–8, 38, 56, 62, 68 | вывод в `docs/api/dokka/`, `docs/api/typedoc-*` |
| `.gitignore` | 97–99 | `docs/api/dokka/`, `docs/api/typedoc-*/` |
| `tools/check-feature-doc.sh` | 10, 44, 116 | примеры `docs/features/*.md` в usage-тексте |
| `tools/check-knowledge-structure.sh` | 27–31, 42–45 | список каталогов `knowledge/…` (без `knowledge/product`) и шаблонов `domain/component/adr/epic` |
| `tools/lint-knowledge.py` | 16 | `See also: docs/operations/knowledge-lint-baseline.md (TODO)` — ссылка на несуществующий файл |
| `config/knowledge/doc-references-whitelist.txt` | 55, 61, 62, 64 | **4** строки со scope `docs/…` (см. § 3.2) |
| `.forgejo/workflows/lint.yml` | 592–593, 603 | lychee-таргеты `docs/features/`, `archive/docs/features/`; вызов `check-feature-doc.sh docs/features/*.md archive/docs/features/*.md` |
| `.github/workflows/lint.yml` | 551–552, 562 | **те же** строки (две копии workflow расходятся в других местах — контрактный тест покрывает только `.github/`) |
| `.pre-commit-config.yaml` | 325, 328 | `entry: bash -c './tools/check-feature-doc.sh docs/features/*.md archive/docs/features/*.md'`; `files: '^(archive/)?docs/features/.*\.md$'` |
| `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/audit/PkgS335SourceContractsTest.kt` | 288, 294, 299 | assert'ы на строку `docs/features/*.md` в `.github/workflows/lint.yml` и `.pre-commit-config.yaml` |
| `tools/README.md` | 22 | документация вызова `check-feature-doc.sh` |
| `PR_DESCRIPTION.md` | 131 | то же |

### 2.5. Целевые места — что уже есть на диске

| Цель | Состояние | Примечание |
|---|---|---|
| `knowledge/guidelines/runbooks/README.md` | есть, каталог «пока пусто» | формат runbook'а задан: «How to: …», Контекст / Шаги / Проверка / Rollback; термин «playbook» в тексте — заменить на «runbook» (D19) |
| `knowledge/guidelines/README.md` | есть | таблица «Планируемое наполнение» уже содержит `kdoc-jsdoc.md` ← `docs/api/README.md` — после переноса строка переезжает в «Структуру» |
| `knowledge/public/glossary.md` | есть (11 782 Б) | место публичной проекции терминов (D19) |
| `knowledge/adr/README.md` | есть | нумерация: `0001…0008` глобальные, `local-NNNN` — контейнерные; фактически последний глобальный — `0010-lm-studio-instead-of-ollama.md` ⇒ **следующий свободный — 0011** ✓ |
| `knowledge/templates/adr.md` | есть | YAML-frontmatter с полями `supersedes` / `superseded_by` |
| `knowledge/adr/0007-adopt-knowledge-as-ssot.md` | Accepted, `superseded_by: null` | правится **только** это поле |
| `knowledge/system/infra/governance-docs.md` | есть | 10 несуществующих ссылок на `docs/*.md` — переписывается (D7) |
| `knowledge/system/infra/per-feature-docs.md` | есть | удаляется (D3) |
| `archive/docs/` | 43 файла: `features/` (29), `onboarding-handoff/` (2), `specs-archive/` (8), `strategy/` (3) | вне скоупа, кроме добавления `architecture-notes.md` |

### 2.6. Цели для уже битых ссылок (D13)

| Ссылка | Факт на диске | Решение |
|---|---|---|
| `docs/strategy/growth.md`, `docs/strategy/growth-audit.md` (`CLAUDE.md`, `AGENTS.md`, `knowledge/domains/{monetization,publishing,stats}/*`) | **существуют** как `archive/docs/strategy/growth.md`, `archive/docs/strategy/growth-audit.md` | вести на `archive/docs/…` с пометкой «архив» |
| `docs/onboarding.md`, `docs/claude-code-setup.md` (constitution § VII, § Governance 1, `governance-docs.md`) | нет ни в `docs/`, ни в `archive/docs/` | текст без ссылки либо whitelist `honest-gap` |
| `docs/architecture-notes.md` | переезжает в `archive/docs/architecture-notes.md` | переписать на новый путь |
| `docs/architecture-notes-archive.md`, `docs/migration-prod-server.md` (constitution) | не существуют | текст без ссылки либо whitelist `honest-gap` |
| `docs/features/README.md` (`docs/api/README.md:119`) | не существует | индекс → `knowledge/product/README.md` |
| `knowledge/domains/integration/components/external-api-clients.md:106,120`, `knowledge/adr/0001…0004` | файлов нет нигде | текст без ссылки либо whitelist `honest-gap` |

### 2.7. Скиллы (KARAOKE-333)

| Копия | Путь | Ранг | Роль | Действие |
|---|---|---|---|---|
| Канон Karaoke | `.agents/skills/managing-living-docs/SKILL.md` (sha `030f0f03…f8f14`, под git) | 200 | каноничная | **править** (таблица Karaoke-overrides, § Impact Surface Analysis, § Final Validation, § Quick Reference) |
| Рантайм-копия | `.dsh/skills/managing-living-docs/` (тот же sha, gitignored) | **100** | перекрывает `.agents/` | **заменить симлинком** на `.agents/skills/managing-living-docs` (как уже сделано для `bootstrap-living-docs` и `speckit-*`) |
| Generic-зеркало | `~/.dsh/skills/managing-living-docs/` (sha `767b61dc…a63ae3`) | 400 | зеркало `agents-team-srv` | **read-only, не трогать** (`~/.dsh/romario-team/SYNC.md:5`) |
| SSoT-команды | `/home/nsa/agents-team-srv/.agents/skills/managing-living-docs/` | — | чужой репозиторий | **не трогать** (AGENTS.md Tier-1 «Инфраструктура команды — read-only») |
| Проектная копия | `.agents/skills/audit-living-docs/SKILL.md:156` | 200 | единственная ссылка на Karaoke-путь `docs/` | `docs/architecture-notes.md` → `archive/docs/architecture-notes.md` |

4 упоминания `docs/` в Karaoke-копии `managing-living-docs` (строки 10, 13, 36, 38) и 5 в `audit-living-docs` (11, 14, 33, 35, 37) — **сравнительные таблицы** «generic `docs/` ↔ Karaoke `knowledge/`» ⇒ **не трогаются**.

---

## 3. Расхождения карты и диска (обязательно к исполнению)

### 3.1. Число документов в продуктовой ветке — **решено 2026-10-29: базовая линия 22**

Арифметика по диску (инвентарь § 3а, ответы 324/325):

```
группа а.1 «Продуктовые фичи»            = 25 файлов
  из них по D4 вне продуктовой ветки:
    deploy-hardening.md      → в реестр аудита          (–1)
    gradle-9-upgrade.md      → свёртка в конвенции     (–1)
    karaoke-db-module-removal.md → свёртка              (–1)
    private-docker-registry.md   → удалить               (–1)
  итого в продуктовую ветку из а.1: 25 – 4 = 21
группа а.3 «мост-файлы»: +1 (tonality-bpm-songs-table)
                                    ⇒ 21 + 1 = 22 файла
```

**Исходное расхождение (зафиксировано до решения).** Первая редакция спеки требовала **26**
(FR-003: «25 продуктовых + `tonality-bpm-songs-table`»; SC-004: 26/26), повторяя Notes карты
п. 2 и ответ 325. Ответ 324 (таблица § 5) требовал **иной судьбы** для тех же 4 файлов группы
а.1, что даёт **22**. Оба прочтения несовместимы: получить 26, уважая D4 буквально, невозможно,
а получить 22 — значит не выполнить FR-003. Расхождение вынесено лиду и владельцу.

**Решение владельца (2026-10-29):** базовая линия — **22**; спека приведена в соответствие
(FR-003, SC-004, коммит `47f31bb6`, статус спеки → `Accepted — контракт уточнён словом
владельца`). Принято буквальное прочтение ответа 324 § 5 — четыре инфраструктурных документа
в продуктовую ветку **не входят**:

| Файл | Судьба | Целевое место |
|---|---|---|
| `private-docker-registry.md` | **удалить** — содержимое уже зеркалится | `knowledge/system/infra/registry.md` + `harbor-registry.md` + `deploy-overview.md` (существуют) |
| `gradle-9-upgrade.md` | **свернуть** — уникально только «история отката с 9.1.0» | `knowledge/guidelines/architecture-conventions.md` § R-46 + `knowledge/system/infra/ci-tools.md` |
| `karaoke-db-module-removal.md` | **свернуть** — уникально обоснование (мёртвый класс «Hello world!», свой build и wrapper на 7.5.1) | `knowledge/guidelines/architecture-conventions.md` + `knowledge/system/infra/ci-tools.md` |
| `deploy-hardening.md` | **в реестр аудита** — 31 BR-код (PKG-S2-08) + фактическая часть | карточка пакета и коды → `knowledge/system/infra/audit-336-registry.md`; фактическая часть (бэкапы, секреты, TLS, порты, ротация логов) → `knowledge/system/infra/deploy-overview.md` |

**Побочное следствие 1.** Список «коды без зеркала» вычисляется на итоговом дереве: 21 продуктовый
документ уезжает в `knowledge/`, поэтому часть кодов получает зеркала, и список станет ≤ 267
(измерено до миграции). Инвентарь кодов фиксируется **510** — совпадает с FR-011.

**Побочное следствие 2.** BR-коды лежат не только в 30 пакетах аудита, но и в 8 продуктовых
документах и 5 мост-файлах ⇒ реестр собирается из **всех 65** файлов `docs/features/`, иначе
часть кодов потерялась бы (FR-011, SC-002).

**Побочное следствие 3.** Граница FR-016 уточнена владельцем: **реестр 16 доменов** в
`knowledge/domains/README.md` (список доменов) не меняется, но **каталог файлов
`knowledge/system/infra/*`** в том же файле обновляется в части удалённых документов — иначе
остаётся ссылка на несуществующий `per-feature-docs.md`, что запрещает FR-014/SC-003.
Продуктовым слоем реестр доменов не дополняется (D19).

### 3.2. Whitelist: 4 строки со scope `docs/…`, а не 11

Ответ 329 говорит «11 строк со scope `docs/…`». На диске (`config/knowledge/doc-references-whitelist.txt`, TSV `scope/kind/value/reason`, 71 непустая строка):

```
awk -F'\t' '$1 ~ /^docs\//' → 4 строки:
  55: docs/api/README.md             path  Settings.kt                     honest-gap
  61: docs/features/pkg-s3-26.md     path  karaoke-app/.../test.xml        honest-gap
  62: docs/features/pkg-s3-26.md     path  2.xml                           honest-gap
  64: docs/features/pkg-s3-34.md     path  pom.xml                         honest-gap
```

Число «11» в ответе 329, вероятно, относилось к scope `knowledge/system` (ровно 11 строк — совпадение). План переписывает **4** строки:
`docs/api/README.md` → `knowledge/guidelines/kdoc-jsdoc.md`; `docs/features/pkg-s3-26.md` и `docs/features/pkg-s3-34.md` → `knowledge/system/infra/audit-336-registry.md` (документы-пакеты поглощаются реестром). Строки со scope `archive/docs/…` (10) **не трогаются**.

### 3.3. Нет `docs/features/README.md`

Notes карты п. 8: «Индекс `docs/features/README.md` переезжает вместе с каталогом». Файла **нет** ни в `docs/`, ни в `archive/docs/` (это отмечено и в инвентаре § 5.5: «расхождение числа per-feature документов (7 в `per-feature-docs.md`, 12 в `governance-docs.md`, 65 фактически)»). Индекс создаётся **с нуля** — `knowledge/product/README.md` (D1, D2).

### 3.4. `docs/architecture-notes.md` и `archive/docs/`

`docs/architecture-notes.md` (512 строк) → `archive/docs/architecture-notes.md` — решение Notes п. 5. `archive/docs/` уже содержит 43 файла; новый файл добавляется, существующие **не меняются** (вне скоупа).

### 3.5. Конституция требует amend, а не только правки путей

`.specify/memory/constitution.md` § VI FR-006 и FR-009 указывают `docs/features/<slug>.md`; § VII и § Governance 1 — на `docs/onboarding.md`, `docs/claude-code-setup.md`, `docs/architecture-notes.md`, `docs/architecture-notes-archive.md`; § VIII — на `docs/migration-prod-server.md`; § IX — на `docs/governance/knowledge-first.md`. По `constitution.md` § Governance 2 и 5 изменение Конституции оформляется коммитом `docs: amend constitution to vX.Y.Z (...)` **с Sync Impact Report** в HTML-комментарии в начале файла. Смысл правил FR-006/FR-009 не меняется (путь меняется) ⇒ **PATCH 2.4.0 → 2.4.1**. Аналогично `AGENTS.md` 3.7.1 → **3.7.2** (R-32 и три ссылки-источника) и `CLAUDE.md` 1.8.1 → **1.8.2** (таблица «Где искать что»), с записями в Changelog.

### 3.6. Формулировка ADR-0011 про BR-коды

Ответ 330 (п. 3.4) предлагает в «Последствия» ADR-0011 написать «274 из 530 кодов существуют только в `docs/`». Диск даёт **510 уникальных** (совпадает с FR-011/SC-002) и **267** кодов без зеркала на момент до миграции (ответ 327: «расхождение методики, вывод тот же»). В ADR-0011 и в реестре используются числа **510 / 267 (на момент до миграции)** — иначе ADR разойдётся с FR-011.

---

## 4. Неизвестные

**NEEDS CLARIFICATION: нет.** Все технические неизвестные закрыты research-артефактами карты и
перепроверены на диске. Вопрос, требовавший решения лида/владельца (число документов в
продуктовой ветке), **решён 2026-10-29** — базовая линия 22, спека приведена в соответствие
коммитом `47f31bb6`; см. § 3.1. Остальные расхождения с диском (§ 3.2–3.6) — факты, принятые
как есть.

---

## 5. Ссылки на источники

- Карта: `.scratch/karaoke/wayfinding/docs-merge/map.md`
- Ответы: `.scratch/karaoke/wayfinding/docs-merge/answers/{324,325,326,327,328,329,330,331,332}-answer.md`
- Research: `.scratch/karaoke/wayfinding/docs-merge/research/{inventory-docs,references-to-docs,skill-managing-living-docs}.md`
- Спека: [`spec.md`](./spec.md) · Конституция: `.specify/memory/constitution.md` (v2.4.0)
