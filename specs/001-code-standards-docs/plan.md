# Implementation Plan: Приведение кода к стандартам и документирование фич

**Branch**: `001-code-standards-docs` | **Date**: 2026-07-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-code-standards-docs/spec.md`

## Summary

Канонизировать и автоматизировать стандарты оформления кода для активных
модулей проекта Karaoke (Kotlin/Spring Boot + Vue 3) и создать per-feature
документацию для 9 ключевых подсистем, с KDoc/JSDoc на публичных API.
Подход: правила из `AGENTS.md` + автоматические линтеры (`ktlint`/`detekt` для
Kotlin, `eslint`/`prettier` для Vue/TS) с baseline-подходом; per-feature
документы в `docs/features/` + cross-refs из KDoc через `@see`. Скоуп:
`karaoke-app`, `karaoke-web`, `webvue3`, `karaoke-public`, `deploy/`. Legacy
`karaoke-db`/`karaoke-vue` — вне скоупа.

## Technical Context

**Language/Version**: Kotlin 1.9 (Spring Boot 2.7/3.x), JDK 17 (уже зафиксировано в
`AGENTS.md` и `DEVELOPMENT.md`); TypeScript 5.x, Vue 3.4+ (для `webvue3` и
`karaoke-public`).

**Primary Dependencies**:
- **Kotlin (бэк)**: `com.github.pinterest:ktlint-gradle` (форматирование),
  `io.gitlab.arturbosch.detekt:detekt-gradle-plugin` (code smells), Dokka
  (генерация KDoc-HTML). Подключаются как Gradle-плагины к корневому
  `build.gradle.kts` и `karaoke-app`/`karaoke-web`.
- **Vue/TS (фронт)**: `eslint@8.x` + `@vue/eslint-config-typescript` +
  `@vue/eslint-config-prettier` (правила); `prettier@3.x` (форматирование).
  Подключаются через `webvue3/.eslintrc.cjs` и `karaoke-public/.eslintrc.cjs`
  (или `.eslintrc.json`).
- **Pre-commit-хук**: `pre-commit` (Python-фреймворк) с хуками `ktlint`,
  `detekt`, `eslint --fix --max-warnings 0`, `markdown-link-check`.
  Конфиг — `.pre-commit-config.yaml` в корне.
- **CI**: отдельная стадия `lint` в GitHub Actions / GitLab CI (на усмотрение
  пользователя; на текущем проде CI нет — pre-commit локально достаточно).
- **Валидация документации**: `lychee` (быстрый link-checker, написан на Rust;
  один бинарь, не требует Node-зависимостей) или `markdown-link-check` (Node).
  Выбор: **lychee** — single binary, проще в pre-commit и в скрипте
  `tools/verify-doc-links.sh`.

**Storage**: N/A. Эта фича — инженерный процесс (тулинг + документация), не
работает с runtime-данными. Артефакты (baseline-файлы, KDoc) живут в git
и в `docs/`.

**Testing**: ручной онбординг + автоматические проверки через SC-002 (baseline-
diff). Тестов для самих линтеров нет — они и есть проверка. KDoc-валидация —
через Dokka-сборку.

**Target Platform**: Linux (admin-машина, прод-сервер). Pre-commit-хук — на
admin-машине разработчика; в CI — опционально. Dokka-генерация — на admin-
машине при `./gradlew dokkaHtml`.

**Project Type**: tooling (линтеры + документация) внутри существующего
multi-module web-service проекта. Не отдельный проект — дополнение к уже
работающему Karaoke.

**Performance Goals**:
- `ktlintCheck` + `detekt` на полном karaoke-app: ≤60 секунд.
- `npm run lint` в `webvue3` и `karaoke-public`: ≤30 секунд каждый.
- `lychee` на `docs/`: ≤10 секунд.
- `dokkaHtml` на karaoke-app: ≤120 секунд.
- Pre-commit-хук (все линтеры по staged-файлам): ≤30 секунд.

**Constraints**:
- Линтеры НЕ в горячем пути `bootJar` — отдельная Gradle-стадия, не блокирует
  сборку прод-образа.
- Baseline-файлы фиксируют 100% текущих нарушений; темп сокращения ≥10%/мес
  (SC-002). Достигается инкрементальным рефакторингом.
- Документация на русском (согласно AGENTS.md).
- Pre-commit-хук НЕ должен блокировать срочный коммит (есть `git commit
  --no-verify`); но baseline-check в CI строгий.
- Докa (per-feature) ссылки `файл:строка` — валидируются `lychee` (для
  Markdown → HTML-якорей); для Kotlin-ссылок `класс#метод` — отдельный
  простой скрипт или статический анализ через `ktlint`-кастом-правило
  (опционально, P3).

**Scale/Scope**:
- 5 активных модулей (`karaoke-app`, `karaoke-web`, `webvue3`,
  `karaoke-public`, `deploy/`).
- ~10k+ LOC Kotlin (оценка по `karaoke-app`), ~5k+ LOC Vue/TS (в двух SPA).
- 9 per-feature документов (FR-004): MLT-генератор, async-очередь
  `KaraokeProcess*`, двух-БД sync, рендер MP4, SSE-уведомления, премиум-стемы,
  LLM-поиск текстов, Telegram-автопубликация, мониторинг.
- 1 `CONTRIBUTING.md` (или `docs/code-style.md`) с правилами для Kotlin и Vue.
- 3+1 baseline-файла: `config/ktlint/baseline.xml`, `config/detekt/baseline.xml`,
  `webvue3/.eslint-baseline.json`, `karaoke-public/.eslint-baseline.json`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Принцип | Соответствие | Обоснование |
|---|---------|--------------|-------------|
| I | Self-contained автопайплайн | ✅ PASS | Линтеры (`ktlint`, `detekt`, `eslint`, `prettier`), Dokka, `lychee`, pre-commit — всё локальные open-source инструменты, без облачных зависимостей в горячем пути. |
| II | Сырой JDBC + дифф по хэшам | ✅ PASS | Фича не затрагивает слой данных. Изменений в `KaraokeDbTable`/SQL-триггерах не требуется. |
| III | Двух-БД синхронизация через SyncRegistry | ✅ PASS | Не затрагивается. `recordhash`-триггеры и `SyncRegistry.all` остаются как есть. |
| IV | Async-очередь задач с парсингом stdout | ✅ PASS | Pre-commit-хук — обёртка над линтерами, не подменяет существующую очередь. Сами линтеры синхронны (не `KaraokeProcess*`). |
| V | Двух-фронтенд: admin и public | ✅ PASS | ESLint-конфиги для `webvue3` и `karaoke-public` — отдельные, не смешиваются. Per-feature документ для admin-SPA и для public-SPA — отдельные файлы в `docs/features/`. |

**Итог**: 5/5 — все принципы соблюдены. Нарушений нет, Complexity Tracking
пуст.

## Project Structure

### Documentation (this feature)

```text
specs/001-code-standards-docs/
├── plan.md              # Этот файл (/speckit.plan output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── baseline-format.md
│   ├── per-feature-doc.md
│   └── code-style-doc.md
├── checklists/
│   └── requirements.md  # Уже создан в /speckit.specify
└── tasks.md             # Phase 2 output (/speckit.tasks — НЕ создаётся /speckit.plan)
```

### Source Code (repository root)

Структура репозитория Karaoke **не меняется**. Эта фича добавляет
артефакты в существующие директории и создаёт одну новую (`docs/features/`):

```text
Karaoke/
├── CONTRIBUTING.md                         # NEW: правила оформления кода
├── docs/                                   # NEW (top-level docs dir)
│   └── features/                           # NEW: per-feature документы
│       ├── mlt-generator.md
│       ├── async-process-queue.md
│       ├── dual-db-sync.md
│       ├── mp4-render.md
│       ├── sse-notifications.md
│       ├── premium-stems.md
│       ├── llm-lyrics-search.md
│       ├── telegram-auto-publish.md
│       └── monitoring.md
├── config/                                 # NEW: тулинг-конфиги
│   ├── ktlint/
│   │   └── baseline.xml
│   └── detekt/
│       └── baseline.xml
├── tools/                                  # NEW: вспомогательные скрипты
│   └── verify-doc-links.sh                 # обёртка вокруг lychee
├── .pre-commit-config.yaml                 # NEW: pre-commit-хук
├── karaoke-app/
│   ├── build.gradle.kts                    # +plugins: ktlint, detekt, dokka
│   └── ... (код без изменений в этой итерации)
├── karaoke-web/
│   ├── build.gradle.kts                    # +plugins: ktlint, detekt
│   └── ...
├── webvue3/
│   ├── package.json                        # +scripts: lint, lint:fix
│   ├── .eslintrc.cjs                       # NEW
│   └── .eslint-baseline.json               # NEW
├── karaoke-public/
│   ├── package.json                        # +scripts: lint, lint:fix
│   ├── .eslintrc.cjs                       # NEW
│   └── .eslint-baseline.json               # NEW
├── deploy/                                 # без изменений (уже документирован в DEVELOPMENT.md)
│   └── ...
└── ...
```

**Structure Decision**: Web-приложение (Option 2 в шаблоне). Существующая
multi-module структура Karaoke **сохраняется** (это тулинговая фича внутри
проекта, не новый проект). Новые артефакты: `CONTRIBUTING.md`, `docs/features/`,
`config/`, `tools/`, `.pre-commit-config.yaml`, конфиги линтеров в существующих
модулях.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *(нет)* | — | — |

Constitution Check прошёл без нарушений. Complexity Tracking пуст.
