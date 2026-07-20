# Data Model: Приведение кода к стандартам и документирование фич

**Branch**: `001-code-standards-docs` | **Phase**: 1 | **Date**: 2026-07-20

Описание ключевых сущностей этой фичи. Не runtime-данные (никаких таблиц БД);
это **артефакты в git-репозитории**, их схемы и правила.

---

## Entity 1: CodeStyleRule (запись в `CONTRIBUTING.md`)

Описание правила оформления кода. Живёт как секция в `CONTRIBUTING.md`,
НЕ как структурированная БД-запись. Markdown — основной формат.

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | String (slug) | Уникальный идентификатор, kebab-case. Пример: `kotlin-naming-functions`, `vue-select-form-select`. |
| `title` | String | Краткое название правила. |
| `section` | Enum | Раздел: `kotlin`, `vue`, `sql`, `json`, `markdown`, `shell`, `gradle`, `docker`. |
| `severity` | Enum | `MUST` / `SHOULD` / `MAY` (семантика RFC 2119). |
| `description` | Markdown | Что за правило, зачем. |
| `do` | Code block (Kotlin/Vue/etc.) | Пример правильного кода. |
| `dont` | Code block (Kotlin/Vue/etc.) | Пример неправильного кода. |
| `rationale` | Markdown | Почему именно так. |
| `linkedInvariants` | List<String> | Ссылки на инварианты в `DEVELOPMENT.md` / `AGENTS.md` / `constitution.md` в формате `path#anchor`. |
| `enforcedBy` | Enum | `ktlint` / `detekt` / `eslint` / `prettier` / `pre-commit` / `code-review-only`. |

**Validation rules**:
- `id` уникален в пределах `CONTRIBUTING.md`.
- `severity` ∈ {MUST, SHOULD, MAY}.
- Если `enforcedBy` = `code-review-only`, то в SC-002 это правило НЕ считается
  автоматически проверяемым; baseline не включает его.
- Каждый MUST-правил со `enforcedBy ≠ code-review-only` должен быть покрыт
  baseline-файлом (если baseline-файл не содержит правила — baseline-чек
  в CI ловит несоответствие).

**Отношения**:
- `linkedInvariants` → `ConstitutionSection` (по anchor).
- `enforcedBy` → `BaselineViolation` (если правило нарушено, появляется запись).

---

## Entity 2: PerFeatureDocument (файл в `docs/features/<slug>.md`)

Per-feature документ. Файл — Markdown, 6 обязательных разделов (FR-005).

| Поле | Тип | Описание |
|------|-----|----------|
| `slug` | String (kebab-case) | Имя файла без `.md`. Пример: `mlt-generator`, `async-process-queue`, `dual-db-sync`. |
| `title` | String | Полное название фичи (заголовок H1). |
| `featureKey` | Enum | Один из 9 ключей: `mlt-generator`, `async-process-queue`, `dual-db-sync`, `mp4-render`, `sse-notifications`, `premium-stems`, `llm-lyrics-search`, `telegram-auto-publish`, `monitoring`. |
| `status` | Enum | `active` / `deprecated` / `experimental`. |
| `sectionWhat` | Markdown | «Что делает» — 1-2 предложения. |
| `sectionWhy` | Markdown | «Зачем» — какую проблему решает. |
| `sectionHow` | Markdown | «Как работает (кратко)» — архитектурный обзор. |
| `sectionInvariants` | Markdown | «Инварианты / правила» — MUST/SHOULD-правила. |
| `sectionPitfalls` | Markdown | «Известные ловушки» — что ломало прод. |
| `sectionRefs` | List<String> | «Ссылки на ключевые классы/файлы» в формате `path/to/file.kt:NNN` или `Class#method`. |
| `lastUpdated` | Date | Дата последнего изменения (из git). |

**Validation rules**:
- Все 6 секций непустые (валидируется на code review или в pre-commit
  через простой скрипт `tools/check-feature-doc.sh`).
- Все ссылки в `sectionRefs` валидны (через `lychee` для файлов, через
  `verify-kotlin-refs.sh` для Kotlin-символов — P3).
- `featureKey` уникален в пределах `docs/features/`.
- `slug` соответствует `featureKey` kebab-case-преобразованием.
- `status` ∈ {active, deprecated, experimental}.

**Отношения**:
- `featureKey` → `ConstitutionSection.affectedPrinciples` (cross-ref в обе стороны).
- `sectionRefs` → `SourceCodeSymbol` (валидируется линтером).
- `sectionInvariants` → `CodeStyleRule` (cross-link через `linkedInvariants`).

---

## Entity 3: BaselineViolation (запись в baseline-файле)

Известное нарушение линтера, подавленное baseline'ом. Файл —
`config/ktlint/baseline.xml` / `config/detekt/baseline.xml` /
`webvue3/.eslint-baseline.json` / `karaoke-public/.eslint-baseline.json`.

### Формат для ktlint/detekt (XML):

| Поле | Тип | Описание |
|------|-----|----------|
| `ruleId` | String | Идентификатор правила (например, `detekt:LongMethod`, `ktlint:standard:no-wildcard-imports`). |
| `file` | String | Относительный путь к файлу. |
| `line` | Integer | Номер строки (опц., для ktlint может отсутствовать). |
| `message` | String | Текст нарушения (опц.). |

### Формат для ESLint (JSON-массив):

| Поле | Тип | Описание |
|------|-----|----------|
| `ruleId` | String | Идентификатор правила. |
| `file` | String | Относительный путь. |
| `line` | Integer | Номер строки. |
| `column` | Integer | Номер колонки. |
| `message` | String | Текст. |

**Validation rules**:
- Каждое нарушение в baseline **должно** иметь linked suppression reason:
  - `// ktlint-disable <ruleId> // intentional: <reason>` в коде, **или**
  - комментарий в самом baseline-файле `<!-- intentional: <reason> -->`.
- Если нарушение в baseline не имеет suppression reason — CI warning (не
  блокер; цель — привлечь внимание, не затормозить).
- Diff между baseline-файлами двух релизов: количество нарушений должно
  уменьшаться на ≥10%/мес (SC-002). Рост baseline > 0% → блокер CI.

**Отношения**:
- `ruleId` → `CodeStyleRule.id`.
- `file` → `SourceCodeSymbol.path`.

---

## Entity 4: DocLink (ссылка внутри per-feature документа)

Конкретная ссылка `файл:строка` или `класс#метод` в per-feature документе.

| Поле | Тип | Описание |
|------|-----|----------|
| `sourceDoc` | String | Путь к документу-источнику: `docs/features/<slug>.md`. |
| `sourceLine` | Integer | Строка в документе-источнике. |
| `target` | String | Цель ссылки: `path/to/file.kt:NNN` или `Class#method`. |
| `targetType` | Enum | `file` / `class` / `method` / `package` / `external-url`. |
| `lastVerified` | Date | Дата последней валидации (через `lychee` / `verify-kotlin-refs.sh`). |

**Validation rules**:
- `targetType = file` или `method`: target должен существовать в кодовой базе
  на момент проверки.
- `targetType = external-url`: target отвечает 2xx/3xx на HEAD.
- Если target не валиден — `lastVerified` = null → link-checker ловит,
  CI warning.

**Отношения**:
- `sourceDoc` → `PerFeatureDocument.path`.
- `target` → `SourceCodeSymbol.path` (если `targetType ∈ {file, class, method}`).

---

## Entity 5: SourceCodeSymbol (символ в коде)

Kotlin-класс / функция / Vue-компонент. Содержимое — в git, **не** в БД.
Регистрируется автоматически (KDoc-linker, kotlin-indexer).

| Поле | Тип | Описание |
|------|-----|----------|
| `kind` | Enum | `class` / `interface` / `object` / `function` / `property` / `vue-component` / `vue-store`. |
| `name` | String | Имя символа (например, `KaraokeProcessWorker`, `SongsTable.vue`). |
| `qualifiedName` | String | Fully-qualified (например, `com.svoemesto.karaokeapp.KaraokeProcessWorker`). |
| `file` | String | Относительный путь к файлу. |
| `line` | Integer | Строка объявления. |
| `signature` | String | Краткая сигнатура (например, `fun runFunctionWithArgs(args: List<String>): Int`). |
| `module` | Enum | `karaoke-app` / `karaoke-web` / `webvue3` / `karaoke-public` / `deploy`. |

**Validation rules**:
- `file` существует в git tree.
- `line` соответствует фактическому объявлению (Kotlin-парсер, опционально).
- `module` ∈ {5 активных модулей}.

**Отношения**:
- `module` → `ModuleCoverage` (статистика покрытия per-feature документацией).
- `qualifiedName` → `DocLink.target` (если ссылаются на него).

---

## Entity 6: ModuleCoverage (покрытие per-feature документацией)

Агрегированная статистика: какие фичи документированы, какие нет.

| Поле | Тип | Описание |
|------|-----|----------|
| `featureKey` | Enum | Один из 9 ключей. |
| `docExists` | Boolean | Есть ли `docs/features/<slug>.md`. |
| `allSectionsFilled` | Boolean | Все 6 секций непустые. |
| `allRefsValid` | Boolean | Все ссылки в `sectionRefs` валидны. |
| `linkedKDocCount` | Integer | Сколько KDoc-блоков ссылается на этот документ. |
| `outstandingIssues` | List<String> | Что нужно доделать. |

**Validation rules** (используется в `quickstart.md`):
- `docExists = true` для всех 9 фич (SC-001).
- `allSectionsFilled = true` (FR-005).
- `allRefsValid = true` (SC-005).

**Отношения**:
- `featureKey` → `PerFeatureDocument.featureKey` (one-to-one).

---

## Entity 7: ConstitutionSection (принцип конституции)

Ссылка на принципы `constitution.md`, на которые влияет фича.

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | String | Римская цифра + название. Пример: `I.SelfContained`, `V.DualFrontend`. |
| `title` | String | Заголовок секции. |
| `version` | String | Версия конституции (semver). |
| `lastAmended` | Date | Дата последнего amend. |
| `affectedByThisFeature` | Boolean | Затрагивает ли эта фича данный принцип. |

**Validation rules**:
- `id` уникален.
- `version` следует semver (см. constitution.md Governance).

**Отношения**:
- `affectedByThisFeature = true` для: `I` (линтеры — локальные инструменты,
  self-contained), `IV` (pre-commit — обёртка, не подменяет очередь),
  `V` (отдельные ESLint-конфиги).

---

## Связи между сущностями (сводка)

```
CodeStyleRule ──[enforced by, ruleId]──> BaselineViolation
CodeStyleRule ──[links via invariant]──> ConstitutionSection
PerFeatureDocument ──[contains]──> DocLink
DocLink ──[references]──> SourceCodeSymbol
SourceCodeSymbol ──[belongs to]──> ModuleCoverage
PerFeatureDocument ──[has key]──> ModuleCoverage
ConstitutionSection ──[referenced in]──> CodeStyleRule.linkedInvariants
```

---

## Что НЕ является сущностью этой фичи

- **Runtime-таблицы БД** — фича не вводит новых таблиц. Существующие таблицы
  Karaoke (`tbl_settings`, `tbl_processes`, и т.д.) не затрагиваются.
- **API endpoints** — фича не вводит новых публичных эндпоинтов. Pre-commit
  хук и `tools/verify-doc-links.sh` — локальные инструменты.
- **Конфиги приложения** — `KaraokeProperties` (см. AGENTS.md) не расширяется
  этой фичей. Baseline-файлы линтеров — НЕ runtime-конфиги, а тулинг.

---

## State Transitions

- `CodeStyleRule`: создаётся → может быть отредактирована → может быть
  помечена `enforcedBy = code-review-only` (если автоматическая проверка
  невозможна) → может быть удалена (через governance).
- `PerFeatureDocument`: создаётся (статус `active`) → может быть помечена
  `deprecated` или `experimental` → может быть удалена (с удалением фичи).
- `BaselineViolation`: появляется при первом запуске линтера → уменьшается
  по мере рефакторинга (цель: 0 через несколько итераций) → может быть
  «восстановлена», если код снова нарушает правило.
- `ConstitutionSection`: версия инкрементируется по semver при amend; см.
  constitution.md Governance.
