# feat: code standards docs (001-code-standards-docs)

> **Type**: Refactoring / Standards / Documentation
> **Risk**: Low (только форматирование, инфраструктура, документация; runtime-код не менялся)
> **Reviewer**: @svoemesto
> **Spec**: `specs/001-code-standards-docs/spec.md`

## Что меняет этот PR

Внедрение единых стандартов оформления кода и документации для проекта Karaoke.
Состоит из **7 фаз** SpecKit-фичи `001-code-standards-docs`. Не меняет runtime-поведение,
только форматирование, документацию, инфраструктуру линтеров и тесты-документы.

## Phase 1-2: Setup + Foundational

- ✅ Speckit-артефакты: `spec.md`, `plan.md`, `research.md`, `data-model.md`,
  `quickstart.md`, `tasks.md` (65 задач), 3 контракта, `requirements.md` чек-лист.
- ✅ Constitution ratified (v1.0.0 → v1.1.0, см. ниже).
- ✅ Ветка `001-code-standards-docs` создана и синхронизирована с master.

## Phase 3-4: US1+US2 — Линтеры и Baseline (FR-007)

- ✅ **ktlint 12.3.0** в корневом `build.gradle.kts` + per-module baseline
  (`config/ktlint/baseline-*.xml`).
- ✅ **ESLint 8.57** + **Prettier 3.3** для `webvue3` и `karaoke-public` (отдельные конфиги).
- ✅ **Per-module baseline-файлы**:
  - `config/ktlint/baseline-karaoke-app.xml` (2038)
  - `config/ktlint/baseline-karaoke-web.xml` (25)
  - `config/ktlint/baseline-karaoke-db.xml` (0)
  - `webvue3/.eslint-baseline.json` (105)
  - `karaoke-public/.eslint-baseline.json` (61)
- ✅ **Pre-commit hooks** (`.pre-commit-config.yaml`): 7 хуков
  (ktlint, eslint × 2, prettier × 2, verify-doc-links, check-feature-doc).
- ✅ **detekt** отключён до выхода версии с поддержкой Kotlin 2.2 (комментарий в build.gradle.kts).
- ✅ **TypeScript** ESLint отключён из-за несовместимости `@vue/eslint-config-typescript`
  с Node 25 (TODO: вернуть после фикса).

## Phase 5: US3 — Per-feature документы (FR-004, FR-009)

- ✅ 9 документов в `docs/features/` (по ~80 строк каждый, 6 секций):
  - `mlt-generator.md` — генератор MLT для караоке-видео
  - `async-process-queue.md` — `KaraokeProcess*` + threadId-лейны
  - `dual-db-sync.md` — `recordhash` + `SyncRegistry` + O(n) `associateBy`
  - `mp4-render.md` — Playwright + ffmpeg рендер LYRICS/KARAOKE/DEMO
  - `sse-notifications.md` — `SseNotificationService` + broadcast/adress
  - `premium-stems.md` — `PublicPlayerController.stemsReady` + MinIO
  - `llm-lyrics-search.md` — `LyricsFinderService` + SearXNG + Ollama
  - `telegram-auto-publish.md` — `@SponsrSync` Scheduler
  - `monitoring.md` — `MonitoringService` + `MonitorRegistry` (светофор)
- ✅ `docs/features/README.md` — оглавление с featureKey ↔ slug.

## Phase 6: US4 — KDoc / JSDoc (FR-006)

- ✅ KDoc добавлен в **12 Kotlin-классов** P1 (главные компоненты):
  `KaraokeProcessWorker`, `Settings`, `KaraokeDbTable`, `Connection`,
  `SyncTarget`, `SseNotificationService`, `MltGenerator`, `LyricsFinderService`,
  `KaraokeStorageService`, `MonitoringService`, `MainController`, и др.
- ✅ JSDoc добавлен в **7 Vue/JS-файлов**: `SongsTable.vue`, `Songs/store.js`,
  `SyncTable.vue`, `SitePlaylistsTable.vue`, `HomeView.vue`, `PlayerView.vue`.
- ✅ Все KDoc/JSDoc-блоки содержат `@see docs/features/<slug>.md`.

## Phase 7: US4 — HTML-документация (FR-006)

- ✅ **Dokka 1.9.20** (`dokkaJavadoc` task) — генерирует 26M HTML для `karaoke-app` + 3.9M для `karaoke-web`.
- ✅ **typedoc 0.28** — генерирует HTML для `webvue3` (188K) и `karaoke-public` (180K).
- ✅ **`tools/generate-docs.sh`** — единая точка входа для всех 4 сайтов.
- ✅ **Почему `dokkaJavadoc` вместо `dokkaHtml`**: Dokka 1.9/2.0 ломается на
  Jackson 2.19 (от Spring Boot 3.5 BOM) — `dokkaHtml` использует JSON-templating,
  ожидающий удалённый конструктор. `dokkaJavadoc` использует другой код path.

## Baseline reduction (SC-002, целевой темп ≥10%/мес)

| Baseline | До | После | Δ |
|----------|-----|-------|---|
| ktlint karaoke-app | 26397 | 2038 | **−92%** |
| ktlint karaoke-web | 1430 | 25 | **−98%** |
| ktlint karaoke-db | 0 | 0 | 0% |
| eslint webvue3 | 2724 | 105 | **−96%** |
| eslint karaoke-public | 61 | 61 | 0% (легитимные) |
| **TOTAL** | **30612** | **2229** | **−93%** |

Дополнительные нарушения (166 в eslint) — легитимные, не auto-fixable
(`vue/no-unused-components`, `vue/require-toggle-inside-transition` и т.д.).
Будут исправляться инкрементально в следующих PR.

## Constitution amend (semver MINOR)

- **v1.0.0 → v1.1.0** — добавлен **Принцип VI «Code Standards»** (NON-NEGOTIABLE):
  - **FR-006**: KDoc/JSDoc на публичных API с `@see docs/features/<slug>.md`.
  - **FR-007**: Линтеры + baseline-процесс + CI-gate.
  - **FR-009**: per-feature документ при правке кода фичи (чек-лист в PR-template).
- Sync Impact Report в HTML-комментарии `constitution.md:1-15`.
- Все шаблоны `.specify/templates/*.md` синхронизированы.

## Файлы

| Категория | Количество | Примеры |
|-----------|------------|---------|
| New infra | 11 | `.eslintrc.cjs`, `.prettierrc.json`, `.pre-commit-config.yaml`, `tsconfig.json`, 3 `baseline-*.xml`, 2 `.eslint-baseline.json`, `config/ktlint/*` |
| New docs | 13 | `CONTRIBUTING.md` (822 строк), 9 per-feature `docs/features/*.md`, `docs/api/README.md`, `README.md` |
| New tools | 8 | `tools/{baseline-stats,check-enforcement,check-eslint-baseline,check-feature-doc,generate-eslint-baseline,verify-doc-links,verify-kotlin-refs,generate-docs}.sh` |
| KDoc/JSDoc | 19 | См. Phase 6 |
| Auto-formatted | ~220 | ktlintFormat (KT) + eslint --fix (JS/Vue) |
| Modified | 30+ | `AGENTS.md`, `DEVELOPMENT.md`, `build.gradle.kts`, `constitution.md`, `package.json` × 2, `tsconfig.json` × 2, `.github/PULL_REQUEST_TEMPLATE.md`, `.gitignore` |

**Всего**: 374 файла в `git status`. ~150 из них — auto-format от ktlint/eslint
(только whitespace/форматирование, без логики).

## Как тестировать (reviewer)

```bash
# 1. Линтеры
./gradlew ktlintCheck                                    # OK
./tools/check-eslint-baseline.sh webvue3                 # OK
./tools/check-eslint-baseline.sh karaoke-public          # OK

# 2. Build
./gradlew :karaoke-app:bootJar :karaoke-web:bootJar      # OK
bash -lc 'cd webvue3 && npm run build'                   # OK
bash -lc 'cd karaoke-public && npm run build'            # OK

# 3. Генерация документации
./tools/generate-docs.sh
# Открыть docs/api/dokka/karaoke-app/index.html
# Открыть docs/api/typedoc-webvue3/index.html

# 4. Baseline
./tools/baseline-stats.sh                                # 2229

# 5. Per-feature документы
./tools/check-feature-doc.sh docs/features/*.md          # 0 errors
```

## Что НЕ входит в этот PR (backlog)

- **detekt** — отключён до выхода версии с поддержкой Kotlin 2.2 (T049).
- **TypeScript ESLint** — отключён из-за несовместимости с Node 25 (TODO: после фикса `@vue/eslint-config-typescript`).
- **typedoc-plugin-vue** — нужен для парсинга `.vue` single-file components
  (сейчас typedoc парсит только `.js`/`.ts`).
- **lychee** — нужен для `./tools/verify-doc-links.sh` (требует `apt install lychee`).
- **P3 KDoc/JSDoc** — `SongView.vue`, `useAuth.js`, `usePlayer.js`, `useFavorites.js`.

## Чек-лист (FR-009 + CONTRIBUTING.md)

### Документация
- [x] Per-feature документы созданы (FR-004) и обновлены в этом PR.
- [x] `docs/api/README.md` создан с инструкцией по KDoc/JSDoc.
- [x] Если добавляется новое MUST-правило — `CONTRIBUTING.md` обновлён + `constitution.md` v1.1.0 (semver MINOR).

### Линтеры
- [x] `./gradlew ktlintCheck` — SUCCESS.
- [x] `cd webvue3 && npm run lint:check` — OK (105 = baseline).
- [x] `cd karaoke-public && npm run lint:check` — OK (61 = baseline).
- [x] `./tools/baseline-stats.sh` — 2229 (сократилось с 30612, −93%).

### Constitution Check (6/6)
- [x] **I. Self-contained** — не нарушен.
- [x] **II. Сырой JDBC** — не нарушен.
- [x] **III. SyncRegistry** — не нарушен.
- [x] **IV. Async-очередь** — не нарушен.
- [x] **V. Двух-фронтенд** — не нарушен (отдельные ESLint-конфиги для admin и public).
- [x] **VI. Code Standards** — выполнен (KDoc, baseline, per-feature docs).

### KDoc / JSDoc (FR-006)
- [x] Публичные API имеют KDoc/JSDoc с `@see docs/features/<slug>.md` (12 Kotlin + 7 Vue).
- [x] `./tools/generate-docs.sh` отработал — 4 HTML сайта сгенерированы.

## Связанные артефакты

- `specs/001-code-standards-docs/spec.md` — формальная спецификация (5 FR).
- `specs/001-code-standards-docs/plan.md` — план реализации.
- `specs/001-code-standards-docs/tasks.md` — 65 задач в 7 фазах.
- `specs/001-code-standards-docs/research.md` — 9 технических решений.
- `.specify/memory/constitution.md` — v1.1.0 (принцип VI добавлен).
- `CONTRIBUTING.md` — 25+ правил с примерами «правильно/неправильно».
- `docs/features/README.md` — 9 per-feature документов.

Closes #001-code-standards-docs
