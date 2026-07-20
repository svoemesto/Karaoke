# План реализации: CI lint enforcement

**Feature Key**: `ci-lint-enforcement`
**Дата**: 2026-07-21

## Стратегия

Один GitHub Actions workflow (`lint.yml`) с 4 job-ами и matrix по двум
SPA. Минимальный, идемпотентный, читаемый. Все 4 job-а параллельны.

## Архитектура

```
push/PR в master
        │
        ▼
┌───────────────────────────────────────────────────────────────┐
│                      GitHub Actions                           │
│                                                               │
│  ┌──────────────┐  ┌────────────────────────┐                 │
│  │ backend-lint │  │   frontend-lint        │                 │
│  │              │  │   matrix:              │                 │
│  │ JDK 17       │  │   - webvue3            │                 │
│  │ ./gradlew    │  │   - karaoke-public     │                 │
│  │  ktlintCheck │  │                        │                 │
│  │ baseline-    │  │ Node 22                │                 │
│  │ aware        │  │ - npm ci               │                 │
│  │              │  │ - ESLint baseline      │                 │
│  │              │  │ - Prettier strict      │                 │
│  └──────────────┘  └────────────────────────┘                 │
│                                                               │
│  ┌──────────────┐  ┌──────────────────┐                       │
│  │ docs-lint    │  │ baseline-stats   │                       │
│  │              │  │ (informational)  │                       │
│  │ - lychee     │  │                  │                       │
│  │   --offline  │  │ - jq + counts    │                       │
│  │ - check-     │  │ - continue-on-   │                       │
│  │   feature-   │  │   error: true    │                       │
│  │   doc.sh     │  │                  │                       │
│  └──────────────┘  └──────────────────┘                       │
└───────────────────────────────────────────────────────────────┘
        │
        ▼
   ✓ required check для branch protection
```

## Поток данных

1. **`actions/checkout@v4`** → repo HEAD.
2. **`actions/setup-java@v4`** с `distribution: temurin`, `java-version: 17`,
   `cache: gradle` → JDK + `~/.gradle/caches` cache.
3. **`actions/setup-node@v4`** с `node-version: 22`, `cache: npm`,
   `cache-dependency-path: $spa/package-lock.json` → Node + `~/.npm` cache.
4. **Backend job**:
   - `./gradlew ktlintCheck --no-daemon --stacktrace`
   - Плагин `org.jlleitschuh.gradle.ktlint:12.3.0` читает baseline из
     `config/ktlint/baseline-${project.name}.xml` для каждого subproject.
   - Exit 0 если все нарушения в baseline; non-zero если есть новые.
5. **Frontend job** (per-SPA matrix):
   - `npm ci` — clean install.
   - `bash tools/check-eslint-baseline.sh $spa` — сравнивает текущие
     ESLint violations с baseline, exit 1 если есть новые.
   - `npx prettier --check "src/**/*.{vue,js,ts,json}"` — strict,
     exit 1 при любом несоответствии.
6. **Docs job**:
   - `lycheeverse/lychee-action@v1.9.0 --offline` — только локальные
     файлы, не HEAD-запросы.
   - `bash tools/check-feature-doc.sh docs/features/*.md` — структура.
7. **Baseline stats job** (informational):
   - `tools/baseline-stats.sh` — печатает таблицу.
   - `continue-on-error: true` — не валит CI.

## Ключевые решения

### D1: matrix по SPA, не два отдельных job-а

`webvue3` и `karaoke-public` имеют **разные** `package.json`,
**разные** ESLint configs, **разные** node_modules. Matrix
обеспечивает:
- Параллельный прогон (`fail-fast: false`).
- DRY — один job-блок для двух SPA.
- Изолированный cache-dependency-path.

### D2: Prettier strict (без baseline)

Prettier — non-functional формат. Любое несоответствие исправляется
одной командой `npm run format`. Baseline для него не введён, потому что:
- Цель Prettier — единообразие (никаких «известных исключений»).
- Если бы был baseline, он бы разрастался.
- Prettier умеет форматировать, а не жаловаться.

В рамках этой фичи: перед добавлением workflow прогоняется
`prettier --write` на обоих SPA, после чего регенерируются
eslint-baselines (строки сместились). Текущее состояние: 0
prettier-нарушений.

### D3: lychee --offline

Онлайн-проверка ссылок в CI flaky:
- IP-диапазоны GitHub Actions часто блокируются внешними сервисами.
- Внешний сервис может быть временно недоступен → ложный fail.
- `ip-api.com` возвращает 403/502 из Docker-сетей (см. AGENTS.md).

Решение: `--offline` для PR-gate. Полная проверка — отдельный
ежедневный workflow (вне scope).

### D4: `permissions: contents: read`

Workflow не пишет в репо (нет `gh release create`, нет push).
Минимизация blast radius при компрометации action. Также
ускоряет approval, если когда-нибудь включат `required-workflow-review`.

### D5: pinned major versions

`actions/checkout@v4`, `actions/setup-java@v4`, `actions/setup-node@v4`,
`lycheeverse/lychee-action@v1.9.0`. Minor/patch получаем
автоматически (security), major — через явный PR.

## Изменения в файлах

| Файл | Тип | Описание |
|------|-----|----------|
| `.github/workflows/lint.yml` | new | workflow, 4 job-а |
| `docs/features/ci-lint-enforcement.md` | new | per-feature документ (FR-009) |
| `docs/features/README.md` | edit | добавить `ci-lint-enforcement` в оглавление |
| `CONTRIBUTING.md` | edit | раздел «Поток разработки: CI flow» |
| `specs/002-ci-lint-enforcement/spec.md` | new | спека |
| `specs/002-ci-lint-enforcement/plan.md` | new | план (этот файл) |
| `specs/002-ci-lint-enforcement/tasks.md` | new | tasks |
| `webvue3/src/**/*.vue` + `karaoke-public/src/**/*.vue` | edit | prettier --write (272 файла) |
| `webvue3/.eslint-baseline.json` | edit | позиции строк сместились |
| `karaoke-public/.eslint-baseline.json` | edit | позиции строк сместились |

## Риски

### R1: ktlint cold start ~6 мин

Первый запуск без кэша Gradle качает Spring Boot 3.5 BOM + Jackson 2.14
+ плагины. Решение: `cache: gradle` (уже в workflow).
`timeout-minutes: 20` с запасом.

### R2: ESLint baseline staleness при prettier reformat

При изменении файлов prettier строки смещаются, baseline перестаёт
совпадать. Решение: после `prettier --write` всегда регенерировать
baseline (см. `tools/generate-eslint-baseline.sh`). В этой фиче
это уже сделано.

### R3: lychee action API change

`lycheeverse/lychee-action` обновляется независимо. Решение:
pinned на `v1.9.0` (проверенная стабильная). Обновление —
отдельный PR.

### R4: pre-commit хуки vs CI дубль

`.pre-commit-config.yaml` уже прогоняет те же линтеры локально.
Дублирование? — Нет, это разные уровни защиты:
- **pre-commit** — локально, до push (developer feedback).
- **CI** — после push, до merge (PR gate).

Один без другого — дыра. Оба нужны.

## Acceptance

- [x] `.github/workflows/lint.yml` валиден (YAML parse OK).
- [x] Per-feature документ проходит `check-feature-doc.sh`.
- [x] Локально: `ktlintCheck` BUILD SUCCESSFUL.
- [x] Локально: `check-eslint-baseline.sh webvue3` и `karaoke-public` — OK.
- [x] Локально: `prettier --check` — OK (0 нарушений).
- [x] Локально: `check-feature-doc.sh docs/features/*.md` — OK.
- [x] Все baseline-файлы закоммичены в PR.
- [ ] На GitHub: workflow запускается на push в master и PR.
- [ ] На GitHub: workflow валится при добавлении `// noinspection` в Kotlin.
- [ ] На GitHub: branch protection rule добавлен (вручную).
