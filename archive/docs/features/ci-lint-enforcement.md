# CI: Lint enforcement

> **Feature Key**: `ci-lint-enforcement`
> **Status**: active
> **Slug**: `ci-lint-enforcement`
> **FR-ссылка**: FR-007 (linters+baseline), FR-009 (per-feature docs)

## Что делает

Запускает на каждый `push` в `master` и каждый `pull_request` в `master`
GitHub Actions job-ы, которые проверяют, что код соответствует требованиям
CONTRIBUTING.md, **собирается**, **проходит unit-тесты** и соответствует
per-feature контрактам.

### Сборка и тесты (gate перед merge)

1. **`backend-build`** — `./gradlew :karaoke-app:compileKotlin
   :karaoke-web:compileKotlin --parallel` и `./gradlew :karaoke-app:bootJar
   :karaoke-web:bootJar --parallel`. Это те же команды, что локально
   (AGENTS.md § «Обязательная проверка после изменения», шаги 1 и 3),
   с явным `GRADLE_USER_HOME`.
   **Добавлен в BR-507 (P-78-01):** до этого workflow только ЛИНТЕРОВАЛ
   код — PR с некомпилируемым Kotlin-кодом проходил CI и мержился
   в master.
2. **`backend-test`** — `./gradlew :karaoke-app:test :karaoke-web:test
   --parallel`; JUnit-отчёты выгружаются артефактом `backend-test-reports`.
   Локально — та же команда (DEVELOPMENT.md § Build & run).
   **Добавлен в BR-507 (P-78-01).** Тесты, требующие живых внешних
   сервисов/браузера, помечены `@Disabled` (Pass 465) — набор CI-safe.

### Линтеры

3. **`backend-lint`** — `./gradlew ktlintCheck` (baseline-aware).
   Прогоняется против per-module baselines в `config/ktlint/`.
   Новые нарушения валят сборку, старые (из baseline) — нет.
4. **`frontend-lint`** (matrix: `webvue3`, `karaoke-public`) —
   `npm ci` + `tools/check-eslint-baseline.sh <spa>` (baseline-aware)
   + `npx prettier --check` (strict).

### Документация и guard-скрипты

5. **`docs-lint`** — `lychee --offline` (только локальные ссылки)
   + `tools/check-feature-doc.sh docs/features/*.md archive/docs/features/*.md`
   (6 секций + slug + status). Проверяются **оба** каталога — живые
   (`docs/features/`) и архив (`archive/docs/features/`): раньше workflow
   проверял только один каталог, поэтому нарушения FR-009 в живых
   per-feature документах не ловились (**BR-510**, P-78-04).
6. **`baseline-stats`** — `tools/baseline-stats.sh` (informational,
   `continue-on-error: true`); публикует текущие счётчики в summary.
7. **`kdoc-coverage`** — `tools/check-kdoc-coverage.sh` (informational,
   `continue-on-error: true`); FR-006 — процент top-level Kotlin-классов
   с KDoc-блоком. Strict-mode (`--strict`) валит при < 50%.
8. **`jsdoc-coverage`** — `tools/check-jsdoc-coverage.sh` (informational,
   `continue-on-error: true`); FR-006 — процент Vue/TS-компонентов
   с JSDoc-блоком. Включая `<script setup>` (Composition API):
   ищет JSDoc в первых 15 строках после `<script setup>`.
   Strict-mode (`--strict`) валит при < 50%.

Остальные джобы workflow: `confirm-escape-guard`, `webvue3-unit-tests`
(`node --test`), `ssot-impact`, `knowledge-structure` (включая guard
«No direct commits to master»), `docker-image-tags-guard`,
`no-jpa-imports-guard`, `no-mp4-mentions-guard` — полный перечень
в `.github/workflows/lint.yml`.

## Зачем

- **Без CI baseline — фикция.** До этого PR baseline-файлы
  (`config/ktlint/baseline-*.xml`, `webvue3/.eslint-baseline.json`,
  `karaoke-public/.eslint-baseline.json`) существовали, но никто
  не проверял, что они соблюдаются на PR. Любой разработчик мог
  добавить тысячу новых нарушений и упасть в baseline — CI бы это
  не поймал.
- **Защита инварианта SC-002 (≥10%/мес сокращение baseline).** Без CI
  этот показатель — договорённость, а не enforced метрика.
- **Снижает нагрузку на code review.** Ревьюер видит «Lint failed»
  ещё до открытия PR — базовые проблемы ловятся автоматически.
- **Соответствие Principle VI (NON-NEGOTIABLE).** Constitution v1.1.0
  требует, чтобы стандарты были enforced, а не добровольны.

## Как работает

### Триггеры

```yaml
on:
  push:
    branches: [master]
  pull_request:
    branches: [master]
```

- **push в master** — post-merge sanity-check. Если baseline случайно
  испортили, узнаем сразу, а не через месяц.
- **pull_request в master** — gate. Зелёный CI — обязательное условие
  для merge (см. `docs/branch-protection.md`, когда появится).

### Concurrency

```yaml
concurrency:
  group: lint-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
```

При force-push в PR отменяется предыдущий запуск — иначе CI занят
впустую на устаревшем коммите.

### Кэширование

| Job | Cache | Ключ |
|-----|-------|------|
| `backend-lint` | Gradle | `setup-java@v4 cache=gradle` → `~/.gradle/caches` |
| `backend-build` | Gradle | `setup-java@v4 cache=gradle` → `$GRADLE_USER_HOME/caches` |
| `backend-test` | Gradle | `setup-java@v4 cache=gradle` → `$GRADLE_USER_HOME/caches` |
| `frontend-lint` | npm | `setup-node@v4 cache= npm` + `cache-dependency-path: $spa/package-lock.json` |

Без кэша `ktlintCheck` холодным стартом — ~6 минут (загрузка
Spring Boot 3.5 + Jackson 2.14 + всех Gradle плагинов). С кэшем —
~1-2 минуты.

В джобах `backend-build`/`backend-test` `GRADLE_USER_HOME` задан явно
на уровне джобы: `${{ github.workspace }}/.gradle` — тот же приём, что
локально (AGENTS.md Tier-1, R-372: Gradle User Home вне read-only `$HOME`).
`actions/setup-java@v4` (cache=gradle) берёт путь кэша из этой же переменной,
поэтому кэш и сборка используют один каталог.

### Baseline-стратегия

#### ktlint (Kotlin/Java)

`build.gradle.kts:122-129` настраивает `baseline = file("${rootDir}/config/ktlint/baseline-${project.name}.xml")`
для каждого subproject. Плагин `org.jlleitschuh.gradle.ktlint:12.3.0`
**автоматически** применяет baseline при `ktlintCheck`:

- Violations, перечисленные в baseline (точное совпадение `file:line:column:rule`):
  → exit 0 (не считаются ошибкой).
- Violations, не перечисленные в baseline:
  → exit non-zero (блокирует CI).

Это значит: **PR может уменьшать baseline, не увеличивая**. Если PR
фиксит 5 нарушений из baseline — CI зелёный, baseline сокращается.

#### ESLint (Vue/JS/TS)

`tools/check-eslint-baseline.sh` реализует ту же стратегию вручную:

1. Запускает `eslint --format json` → плоский JSON нарушений.
2. Читает `webvue3/.eslint-baseline.json` (или `karaoke-public/.eslint-baseline.json`).
3. Сравнивает по ключу `ruleId:filePath:line:column`.
4. Находит записи, которых нет в baseline → **новые** нарушения.
5. Если `новых > 0` → exit 1, иначе exit 0.

Причина ручной реализации (а не встроенного ESLint-флага):
ESLint не имеет встроенной поддержки baseline, в отличие от ktlint.

#### Prettier (strict, без baseline)

```bash
npx prettier --check "src/**/*.{vue,js,ts,json}"
```

Prettier — non-functional формат. Baseline для него **не введён намеренно**:
любое несоответствие исправляется одной командой `npm run format`.

В рамках PR #12 (001-code-standards-docs) уже прогнан `prettier --write`
на обоих SPA (272 файла), после чего регенерированы eslint-baselines
(позиции строк сместились). Текущее состояние: 0 prettier-нарушений.

### lychee offline

`lycheeverse/lychee-action@v1.9.0` с `--offline` — проверяет только
**локальные** Markdown-ссылки (файлы в `docs/features/`, `CONTRIBUTING.md`).
Не делает HEAD-запросы к внешним URL.

Причины `--offline`:

- IP-диапазоны GitHub Actions часто блокируются внешними сервисами
  (например, `ip-api.com` возвращает 403/502, см. AGENTS.md).
- Внешние ссылки могут быть временно недоступны — flaky CI хуже
  отсутствия проверки.
- Проверка ссылок — задача отдельного workflow (ежедневного), не PR-gate.

`--accept 200,203,206,301,302,303,304,307,308,403,429` — разрешает
типичные не-200 ответы (редиректы, rate-limited) чтобы не валить
на чужих API, которые возвращают 403/429.

## Инварианты

1. **Baseline монотонно уменьшается.** PR, увеличивающий любое
   число в `tools/baseline-stats.sh` (ktlint/eslint), валит CI.
   Единственный способ добавить нарушение в baseline — намеренно
   через `./tools/generate-eslint-baseline.sh` / `./gradlew ktlintGenerateBaseline`,
   с явным комментарием в commit message (`chore(baseline): ...`).

2. **`STATUS=active` per-feature документы валидны.** Проверяется
   `tools/check-feature-doc.sh docs/features/*.md archive/docs/features/*.md`
   — **оба** каталога (BR-510, P-78-04). Документы со
   статусом `deprecated`/`experimental` тоже проверяются.

3. **PR не проходит, если `frontend-lint` упал хотя бы в одном SPA.**
   `strategy.fail-fast: false` + параллельное выполнение — оба
   SPA прогоняются, оба отчитываются.

4. **PR не проходит без зелёной сборки и зелёных тестов бэкенда.**
   Джобы `backend-build` и `backend-test` выполняют те же команды,
   что и локальная проверка из AGENTS.md, поэтому «зелёный CI»
   означает «код компилируется и его тесты проходят» (BR-507, P-78-01).

5. **Guard «No direct commits to master» оценивает master, а не
   «думает, что всё хорошо».** Джоб `knowledge-structure` обязан
   делать checkout с `fetch-depth: 0`; если `origin/master` недоступен —
   шаг **падает**, а не рапортует «нарушений нет» (BR-509, P-78-03).

6. **`actions/setup-java@v4` + `actions/setup-node@v4` — pinned
   major.** Minor-обновления получаем автоматически (security patches),
   breaking changes — только через явный PR.

7. **`permissions: contents: read`** — workflow не пишет в репо
   (нет `gh release create` и т.п.). Минимизирует blast radius
   при компрометации action.

## Известные ловушки

### `ktlintCheck` UP-TO-DATE = пройден

Если в логах job `Run ktlintCheck` видно `UP-TO-DATE` для всех
17 задач — это OK. Gradle закэшировал результат (хеш источников
не изменился). Не нужно триггерить rebuild через `touch`.

### `npm ci` vs `npm install`

Используется **`npm ci`** (не `npm install`). CI-clean install по
`package-lock.json` — гарантирует воспроизводимость. Если в PR
изменился `package.json` без обновления `package-lock.json`,
`npm ci` упадёт — это правильное поведение.

### ESLint baseline vs prettier reformat

При прогоне `prettier --write` eslint может обнаружить «новые»
нарушения, потому что строки сместились. Решение:

```bash
# 1. Прогнать prettier
cd webvue3 && npx prettier --write "src/**/*.{vue,js,ts,json}"
cd karaoke-public && npx prettier --write "src/**/*.{vue,js,ts,json}"

# 2. Регенерировать baseline
bash tools/generate-eslint-baseline.sh webvue3
bash tools/generate-eslint-baseline.sh karaoke-public

# 3. Коммит: один для prettier-формата, один для baseline-обновления
```

### lychee v1.9.0 vs v1.10.0

Action pinned на `v1.9.0`. Версия 1.10.0 меняет формат `--accept`
флага. Обновление — отдельный PR с проверкой на тестовом PR.

### Backend job ~6 мин cold start

Первый запуск (без кэша) качает Spring Boot 3.5 BOM + Jackson 2.14
+ все Gradle плагины. После первого прогона — ~1-2 мин.

Если CI ломается по таймауту (`timeout-minutes: 20` у `backend-lint`,
45 у `backend-build`, 30 у `backend-test`), первое действие — проверить
`actions/cache` для Gradle.

### Shallow checkout = «зелёный» guard, который ничего не проверяет

`actions/checkout@v4` по умолчанию клонирует **один** коммит
(`fetch-depth: 1`). В сборке `pull_request` при этом не создаётся
remote-tracking ref `origin/master` — есть только ref самого PR.
`git log origin/master` в таком клоне падает с «unknown revision»,
вывод пустой, счётчик прямых коммитов всегда 0 → guard
«No direct commits to master» печатал «✅ нарушений нет» и **никогда
не падал** (BR-509, P-78-03).

Теперь джоб `knowledge-structure` делает checkout с `fetch-depth: 0`
(actions/checkout при этом тянет `+refs/heads/*:refs/remotes/origin/*`,
то есть `origin/master` есть и в PR-сборке), а сам шаг **проверяет
наличие ref и падает**, если оценить master невозможно.

Тот же механизм касается джоба `ssot-impact` (`fetch-depth: 50`):
при глубине < 0 checkout не тянет `origin/master`, и
`tools/check-ssot-impact.py` не находит базу для `git diff` — печатает
`WARN: cannot resolve any of [...]` и работает как no-op. Это отдельная
находка (вне пакета PKG-S2-05) — см. отчёт по #369.

### Per-feature документы проверяются в двух каталогах

`tools/check-feature-doc.sh` вызывается с `docs/features/*.md` **и**
`archive/docs/features/*.md`. Если после правки оставить только один
каталог, нарушения FR-009 в другом снова перестанут ловиться
(BR-510, P-78-04). Тот же набор — в pre-commit-хуке
`check-feature-doc` (`.pre-commit-config.yaml`).

### `webvue3` + `karaoke-public` без `npm test`

`npm test` в обоих SPA не настроен (Vue-проекты без unit-тестов,
см. AGENTS.md). Если появятся тесты — добавить отдельный job
`frontend-test` (а не подключать к `frontend-lint`).

## Ссылки

- [.github/workflows/lint.yml](../../.github/workflows/lint.yml) — workflow.
- [CONTRIBUTING.md](../../../CONTRIBUTING.md) — правила, которые CI enforced.
- [.specify/memory/constitution.md](../../../.specify/memory/constitution.md) — Принцип VI (Code Standards, NON-NEGOTIABLE).
- [tools/baseline-stats.sh](../../tools/baseline-stats.sh) — счётчики baseline.
- [tools/check-eslint-baseline.sh](../../tools/check-eslint-baseline.sh) — ESLint baseline-aware чек.
- [tools/generate-eslint-baseline.sh](../../tools/generate-eslint-baseline.sh) — регенерация baseline.
- [tools/check-feature-doc.sh](../../tools/check-feature-doc.sh) — структура per-feature документа.
- [tools/verify-doc-links.sh](../../tools/verify-doc-links.sh) — offline link checker.
- [.pre-commit-config.yaml](../../.pre-commit-config.yaml) — локальные хуки (тот же набор).
- [build.gradle.kts:102-145](../../build.gradle.kts) — ktlint + Dokka.
- [PR #12](https://github.com/svoemesto/Karaoke/pull/12) — фича, которую CI enforced.
- [lycheeverse/lychee-action](https://github.com/lycheeverse/lychee-action) — действие для проверки ссылок.
- [actions/setup-java](https://github.com/actions/setup-java) — JDK setup.
- [actions/setup-node](https://github.com/actions/setup-node) — Node.js setup.
- [specs/336-code-audit/findings/P-78.md](../../../specs/336-code-audit/findings/P-78.md) — карточки аудита #336: P-78-01 (BR-507, нет сборки/тестов), P-78-03 (BR-509, shallow checkout), P-78-04 (BR-510, один каталог per-feature документов).
