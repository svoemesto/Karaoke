# Контракт: перенастройка инструментов, CI и правил

**Feature**: `675-merge-docs-into-knowledge` | **Issue**: `KARAOKE-334` | **Дата**: 2026-10-29
**Источник**: D14–D17, D20 (см. [research.md § 1](../research.md)), полный перечень путей — [migration-map.md § 3.6](./migration-map.md#36-инструменты-с-зашитыми-путями--полный-перечень)

> Контракт — **интерфейс** инструментов после миграции: какие пути они принимают,
> где вызываются и что считается «зеленым». Реализация — правки в существующих файлах.

---

## T1. Проверка структуры Knowledge

| Скрипт | `tools/check-knowledge-structure.sh` |
|---|---|
| Проверка | «[1/9] Проверка структуры (директории + манифесты)», «[2/9] Проверка шаблонов» |

**Изменения**

| # | Было | Стало |
|---|---|---|
| 1 | список каталогов: `knowledge/system knowledge/domains knowledge/adr knowledge/epics knowledge/guidelines knowledge/public knowledge/templates` | + `knowledge/product` |
| 2 | список обязательных README: `knowledge/README.md`, `knowledge/system/README.md`, `knowledge/domains/README.md`, `knowledge/adr/README.md`, `knowledge/epics/README.md`, `knowledge/guidelines/README.md`, `knowledge/public/README.md`, `knowledge/templates/README.md` | + `knowledge/product/README.md` |
| 3 | проверка шаблонов: `domain.md component.md adr.md epic.md` | + `product-feature.md` |

**Инварианты**

| # | Инвариант |
|---|---|
| T-1 | `knowledge/product/README.md` существует ⇒ проверка существования продуктовой ветки |
| T-2 | Шаблоны `domain/component/adr/epic` к продуктовой ветке **не применяются** — они проверяются только как файлы в `knowledge/templates/` |
| T-3 | Порядок и нумерация шагов («[N/9]») сохраняются |

---

## T2. Проверка per-feature документов (профиль B)

| Скрипт | `tools/check-feature-doc.sh` |
|---|---|
| Контракт | `specs/001-code-standards-docs/contracts/per-feature-doc.md` |
| Проверяет | 6 секций FR-009, `slug` == имя файла, `status` ∈ {active, deprecated, experimental} |

**Новый вызов**

```bash
bash tools/check-feature-doc.sh knowledge/product/features/*.md archive/docs/features/*.md
```

**Точки вызова — все перенастраиваются (D17)**

| Файл | Что меняется |
|---|---|
| `.forgejo/workflows/lint.yml:603` | путь |
| `.github/workflows/lint.yml:562` | путь |
| `.pre-commit-config.yaml:325` | `entry: bash -c './tools/check-feature-doc.sh knowledge/product/features/*.md archive/docs/features/*.md'` |
| `.pre-commit-config.yaml:328` | `files: '^(knowledge/product/features\|archive/docs/features)/.*\.md$'` |
| `tools/README.md:22` | документация вызова |
| `PR_DESCRIPTION.md:131` | документация вызова |
| `tools/check-feature-doc.sh:10,44,116` | usage-текст и примеры |
| `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/audit/PkgS335SourceContractsTest.kt:288,294,299` | assert'ы на новую строку |

**Инварианты**

| # | Инвариант |
|---|---|
| T-4 | Архивный путь `archive/docs/features/*.md` **сохраняется** — 30 архивных документов не выпадают из проверки |
| T-5 | Порядок вызова «живые, затем архив» сохраняется |
| T-6 | Контракт `check-feature-doc.sh` (6 секций, slug, status) **не меняется** — меняется только путь |

---

## T3. Линтер профиля A

| Скрипт | `tools/lint-knowledge.py` |
|---|---|
| Обход | `knowledge/domains` + отдельно ADR и навигационные README |

**Изменения**

| # | Было | Стало |
|---|---|---|
| 1 | строка 16: `See also: docs/operations/knowledge-lint-baseline.md (TODO: add doc).` | файл не существует ⇒ **текст без ссылки** |
| 2 | — | **явный комментарий** рядом с `SECTION_EXEMPTIONS`: `knowledge/product/` обслуживается guard'ем FR-009 (`tools/check-feature-doc.sh`), обход линтера туда **не расширяется** |

**Инварианты**

| # | Инвариант |
|---|---|
| T-7 | Исключения (`SECTION_EXEMPTIONS`) **не расширяются** — механизм по точному пути не меняется |
| T-8 | Профиль B не проверяется линтером профиля A — ни сейчас, ни после правки обхода |

---

## T4. Проверка ссылок и упоминаний

| Скрипт | `tools/check-doc-references.py` |
|---|---|---|
| Что проверяет | относительные `.md`-ссылки, пути к файлам кода, `/api`-эндпоинты в `knowledge/`, `docs/`, `archive/docs/`, `.github/` |

**Изменения**

| # | Было | Стало | Обоснование |
|---|---|---|---|
| 1 | `ROOTS = ("knowledge", "docs", "archive/docs", ".github")` | `ROOTS = ("knowledge", "archive/docs", ".github")` | каталога `docs/` нет; `knowledge/product/` покрыт корнем `knowledge` |
| 2 | `GENERATED_PREFIXES = ("docs/api/dokka/", "docs/api/typedoc-")` | `GENERATED_PREFIXES = ("build/api-docs/",)` | генератор переносится в `build/api-docs/` |
| 3 | docstring: «генерируемые каталоги `docs/api/dokka/` и `docs/api/typedoc-*/`» | «`build/api-docs/`» | то же |

**Инварианты**

| # | Инвариант |
|---|---|
| T-9 | `archive/docs/` остаётся в `ROOTS` (вне скоупа, но проверяется на битые ссылки) |
| T-10 | `LINK_ONLY_ROOTS = ("specs/",)` — поведение для `specs/` не меняется (только ссылки) |
| T-11 | `config/knowledge/doc-references-whitelist.txt` — 71 строка; переписываются **4** строки со scope `docs/…` (строки 55, 61, 62, 64); строки со scope `archive/docs/…` (10) не трогаются |

---

## T5. Гейт SSoT-влияния

| Скрипт | `tools/check-ssot-impact.py` |
|---|---|
| Что делает | определяет, требует ли изменение обновления `knowledge/` |

**Изменения**

| # | Было | Стало | Последствие |
|---|---|---|---|
| 1 | `EXCLUDE_PATTERNS` содержит `r"^docs/"` | исключение **снимается** | каталога `docs/` нет ⇒ исключение мёртвое; снятие не меняет поведение гейта, но исключает «мёртвый код правила» |
| 2 | строка 170: `See docs/architecture-notes.md or AGENTS.md` | `archive/docs/architecture-notes.md` | ссылка ведёт в архив, а не в несуществующий файл |

**Инварианты**

| # | Инвариант |
|---|---|
| T-12 | `tools/` из исключений **не** убирается (комментарий в коде прямо это оговаривает) |
| T-13 | Изменение поведения гейта проверяется на наборе из 3 сценариев (см. quickstart) |

---

## T6. Проверка ссылок lychee

| Скрипт | `tools/verify-doc-links.sh` |
|---|---|
| Было | `TARGETS=("docs/features/" "CONTRIBUTING.md" "docs/")` |
| Стало | `TARGETS=("knowledge/product/features/" "knowledge/" "CONTRIBUTING.md")` |

Перевод таргетов — один-в-один, по смыслу каждого:

| Было | Стало | Почему |
|---|---|---|
| `docs/features/` | `knowledge/product/features/` | продуктовая ветка переехала |
| `docs/` (весь слой документации) | `knowledge/` (весь SSoT-слой) | «слой целиком → слой целиком»; при этом охват вырос — нашлись 2 реальные битые ссылки с неверной глубиной (`knowledge/guidelines/code-style.md`, `knowledge/public/onboarding.md`), обе починены |
| `CONTRIBUTING.md` | `CONTRIBUTING.md` | без изменений |

**Инварианты**

| # | Инвариант |
|---|---|
| T-14 | Каталог `docs/` из таргетов убирается — иначе lychee будет проверять несуществующий путь |
| T-15 | Обе копии workflow получают **тот же** набор таргетов, что и дефолт скрипта: `knowledge/product/features/`, `knowledge/`, `CONTRIBUTING.md`. Архивный слой (`archive/docs/features/`) из проверки ссылок убран — он вне скоупа карты, его структуру проверяет отдельный джоб `check-feature-doc.sh` по `archive/docs/features/*.md` |
| T-15a | Исключение lychee `--exclude 'knowledge/templates/components/'` — ровно одна иллюстративная ссылка в шаблоне `knowledge/templates/domain.md` (файла-примера `components/component-name.md` не существует by design; аналог reason `illustrative` в whitelist'е `check-doc-references.py`). Широкий шаблон `knowledge/templates/` не применяется: он маскировал бы ещё 12 живых ссылок на реальные файлы шаблонов |

---

## T7. Проверка SQL-блоков и Kotlin-ссылок

| Скрипт | Изменение |
|---|---|
| `tools/check-no-legacy-tbl-settings.sh:174` | `for f in docs/features/*.md` → `for f in knowledge/product/features/*.md` |
| `tools/verify-kotlin-refs.sh:18` | `TARGETS=(docs/features/*.md)` → `TARGETS=(knowledge/product/features/*.md)` |

**Инварианты**

| # | Инвариант |
|---|---|
| T-16 | Содержание проверок (что именно ищется) не меняется — только путь |

---

## T8. Генератор API-документации

| Файл | Было | Стало |
|---|---|---|
| `tools/generate-docs.sh:5-8` | перечень выходов `docs/api/dokka/karaoke-app/index.html`, `docs/api/dokka/karaoke-web/index.html`, `docs/api/typedoc-webvue3/index.html`, `docs/api/typedoc-karaoke-public/index.html` | `build/api-docs/…` (те же 4 выхода) |
| `tools/generate-docs.sh:38` | `rm -rf docs/api/dokka docs/api/typedoc-webvue3 docs/api/typedoc-karaoke-public` | `rm -rf build/api-docs` |
| `tools/generate-docs.sh:56,62` | `npx typedoc --out ../docs/api/typedoc-webvue3` | `--out ../build/api-docs/typedoc-webvue3` |
| `tools/generate-docs.sh:68` | «Объединяю Dokka в docs/api/dokka/» | `build/api-docs/dokka/` |
| `.gitignore:97-99` | `docs/api/dokka/`, `docs/api/typedoc-*/` | `build/api-docs/` |

**Инварианты**

| # | Инвариант |
|---|---|
| T-17 | Вывод генератора **вне системы контроля версий** (FR-008) — иначе каталог `docs/` не исчезнет с диска |
| T-18 | Путь `build/` уже исключён из проверок `check-doc-references.py` (`SKIP_DIR_PARTS` содержит `/build`) |
| T-19 | В `docs/` не остаётся ни одного генерируемого каталога |

---

## T9. CI: две копии workflow

| Файл | Строки | Что меняется |
|---|---|---|
| `.forgejo/workflows/lint.yml` | 592–593, 603 | таргеты lychee → `knowledge/product/features/`; вызов `check-feature-doc.sh` → новые пути; **добавить** шаг guard'а «нет `docs/`» и шаг проверки синхронности workflow |
| `.github/workflows/lint.yml` | 551–552, 562 | **то же самое** (две копии обязаны содержать одинаковый набор гейтов — FR-012, FR-013) |
| `.pre-commit-config.yaml` | 325, 328 | `entry` и `files` → новые пути; **добавить** hook guard'а «нет `docs/`» |

**Инварианты**

| # | Инвариант |
|---|---|
| T-20 | Набор **гейтовых команд** в обеих копиях workflow совпадает (проверяется guard'ем C2) |
| T-21 | Контрактный тест `PkgS335SourceContractsTest.kt` обновляется под новые пути и **остаётся** (не удаляется) |
| T-22 | Guard'ы включаются в оба workflow — не в один (FR-012: «в CI **обоих** конфигураций сборки») |

---

## T10. Правила верхнего уровня

| Файл | Что меняется | Версия |
|---|---|---|
| `AGENTS.md` | R-32 (Tier-2) — путь per-feature; 3 ссылки-источника (`docs/tracker-setup.md` ×2, `docs/ops/log-correlation.md`); Changelog | 3.7.1 → **3.7.2** |
| `CLAUDE.md` | таблица «Где искать что» — 6 строк; Changelog | 1.8.1 → **1.8.2** |
| `.specify/memory/constitution.md` | § VI FR-006 и FR-009; § VII; § VIII; § IX; § Governance 1; **Sync Impact Report** | 2.4.0 → **2.4.1** |
| `.github/PULL_REQUEST_TEMPLATE.md` | 6 упоминаний `docs/` | — |
| `DEVELOPMENT.md`, `README.md` | ссылки на перемещаемые файлы | — |
| `PR_DESCRIPTION.md`, `tools/README.md` | документация вызова | — |
| `knowledge/README.md` | таблица «Структура» (+`product/`), «Две ветки SSoT» + таблица двух профилей, override п. 5, «Бывшие пути», шаг Initialize | — |
| `knowledge/public/glossary.md` | секция «Продуктовый слой» (4 термина) | — |
| `knowledge/system/infra/governance-docs.md` | переписан (D7) | — |
| `knowledge/guidelines/README.md` | `knowledge-first.md` в «Структуре»; `kdoc-jsdoc.md` из «Планируемого наполнения»; «runbook» вместо «playbook» | — |
| `knowledge/guidelines/runbooks/README.md` | перечень 5 runbook'ов; «runbook» вместо «playbook» | — |

**Инварианты**

| # | Инвариант |
|---|---|
| T-23 | Правки `AGENTS.md` / `CLAUDE.md` / `constitution.md` идут с semver bump и записью в Changelog (PATCH — меняются пути, не смысл) |
| T-24 | В корневых правилах не остаётся упоминаний `docs/`, кроме раздела «Бывшие пути» в `knowledge/README.md` и таблицы соответствия (FR-021, SC-007) |

---

## T11. Контрактные тесты

| Файл | Ассерты | После |
|---|---|---|
| `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/audit/PkgS335SourceContractsTest.kt:288` | `ci.contains("bash tools/check-feature-doc.sh docs/features/*.md archive/docs/features/*.md")` | `... knowledge/product/features/*.md archive/docs/features/*.md` |
| `:294` | `lycheeTargets.contains("docs/features/")` | `lycheeTargets.contains("knowledge/product/features/")` |
| `:299` | `hook.contains("docs/features/*.md archive/docs/features/*.md")` | `hook.contains("knowledge/product/features/*.md archive/docs/features/*.md")` |

**Инварианты**

| # | Инвариант |
|---|---|
| T-25 | Тест **не удаляется** — он теперь фиксирует новые пути (тот же смысл: «оба каталога проверяются») |
| T-26 | Тест читает **только** `.github/workflows/lint.yml` — поэтому нужна отдельная проверка синхронности workflow (C2) |
