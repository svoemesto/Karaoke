# Karaoke (svoemesto)

> **Self-hosted pipeline для автоматического создания караоке-видео.**
> Kotlin/Spring Boot бэкенд + Vue 3 фронтенд.

[![Lint](https://github.com/svoemesto/Karaoke/actions/workflows/lint.yml/badge.svg)](https://github.com/svoemesto/Karaoke/actions/workflows/lint.yml)

## Что это

Karaoke — это автопайплайн, который находит/загружает аудио, тексты,
аккорды и метаданные песни, прогоняет аудио-анализ и source-separation,
генерирует MLT-проект с визуальной композицией караоке-видео
(бегущий текст, аккорды, гриф, водяной знак, счётчики) и публикует
готовое видео. Vue 3 admin SPA управляет пайплайном через Kotlin/Spring
Boot бэкенд.

## Документация

| Документ | Назначение |
|----------|------------|
| [CLAUDE.md](./CLAUDE.md) | Краткая шпаргалка для AI-агентов |
| [AGENTS.md](./AGENTS.md) | Полные инструкции для AI-агента (русский, ≤ 100 строк) |
| [DEVELOPMENT.md](./DEVELOPMENT.md) | Архитектурный контекст, dated-история, ловушки |
| [CONTRIBUTING.md](./CONTRIBUTING.md) | **Правила оформления кода** (Kotlin, Vue, SQL, Shell, Docker) |
| [constitution.md](./.specify/memory/constitution.md) | Непреложные принципы проекта |
| [**Knowledge (SSoT)**](./knowledge/README.md) | **ПЕРВЫЙ источник знаний** — SDD/DDD/C4 (домены, компоненты, ADR) |
| [knowledge/domains/README.md](./knowledge/domains/README.md) | Карта доменов и их компонентов |
| [docs/architecture-notes.md](./docs/architecture-notes.md) | Датированный changelog архитектуры |
| [knowledge/adr/](./knowledge/adr/README.md) | Architecture Decision Records (включая `local-*`) |
| [archive/docs/strategy/growth.md](./archive/docs/strategy/growth.md) | Стратегия роста (воронка: visitor → registration → premium) |
| [knowledge/public/onboarding.md](./knowledge/public/onboarding.md) | Настройка новой машины разработчика |
| [knowledge/system/infra/](./knowledge/system/infra/) | Операционные how-to (деплой, миграции, CI) |
| [archive/docs/features/](./archive/docs/features/) | Legacy per-feature документы (FR-017 спеки 189) |
| [docs/api/](./docs/api/) | Build artifacts: KDoc/JSDoc |

**AI-агент: начни с [knowledge/README.md](./knowledge/README.md) → [knowledge/domains/README.md](./knowledge/domains/README.md).**

### Документация: генераторы

| Скрипт | Что делает |
|--------|-----------|
| `python3 tools/auto-kdoc.py` | Генерирует базовый KDoc (описание + `@see`) для Kotlin-классов без KDoc |
| `python3 tools/auto-jsdoc.py` | То же для JS/Vue |
| `python3 tools/auto-kdoc-quality.py` | Проверяет качество сгенерированного KDoc |
| `bash tools/generate-docs.sh` | Генерирует HTML-документацию (Dokka + typedoc) в `docs/api/` |
| `bash tools/check-kdoc-coverage.sh`, `bash tools/check-jsdoc-coverage.sh` | Покрытие KDoc/JSDoc (гейт) |
| `bash tools/verify-kotlin-refs.sh` | Проверяет ссылки на Kotlin-сущности в документации |

## Стандарты оформления кода

Проект использует **AGENTS.md + автоматические линтеры** с baseline-подходом.
Полные правила — в [CONTRIBUTING.md](./CONTRIBUTING.md). Краткая выжимка:

- **Kotlin**: ktlint (форматирование). detekt отключён (несовместим с Kotlin 2.2.20).
  См. `.editorconfig` + раздел
  [Kotlin / Spring Boot](./CONTRIBUTING.md#kotlin--spring-boot).
- **Vue/TS**: ESLint (vue3-recommended) + Prettier. TypeScript-проверка отдельно
  (`vue-tsc --noEmit`). См. [Vue 3 / TypeScript](./CONTRIBUTING.md#vue-3--typescript).
- **Pre-commit**: `pip install pre-commit && pre-commit install`.
  Обход: `git commit --no-verify`.
- **CI**: [`.github/workflows/lint.yml`](./.github/workflows/lint.yml) —
  ktlint (Kotlin/Java), ESLint (webvue3/karaoke-public), Prettier,
  lychee, проверка per-feature документов. Запускается на push
  в `master` и pull_request в `master`. Per-feature документ:
  [`archive/docs/features/ci-lint-enforcement.md`](./archive/docs/features/ci-lint-enforcement.md).
- **Baseline**: текущие нарушения зафиксированы в
  `config/ktlint/baseline-*.xml`, `webvue3/.eslint-baseline.json`,
  `karaoke-public/.eslint-baseline.json`. Темп сокращения: ≥10%/мес.

Скрипты в `tools/`:

**Backend / фронтенд / baseline**:
- `tools/baseline-stats.sh` — статистика по baseline.
- `tools/generate-eslint-baseline.sh` — генерация baseline для ESLint.
- `tools/check-eslint-baseline.sh` — проверка, что новых нарушений нет.
- `tools/check-enforcement.sh` — проверка, что MUST-правила покрыты baseline.
- `tools/check-kdoc-coverage.sh` — KDoc coverage ≥ 100%.
- `tools/check-jsdoc-coverage.sh` — JSDoc coverage ≥ 100%.
- `tools/check-audit-coverage.sh` — аудит кода.

**Knowledge SSoT** (Tier-1 AGENTS.md; прежний LiveDocs-тулчейн снят):
- `tools/check-knowledge-structure.sh` — 9 структурных проверок `knowledge/`.
- `tools/check-knowledge-cross-links.sh` — валидация относительных ссылок и `related:`.
- `python3 tools/lint-knowledge.py` — markdown style (NO EMOJI, обязательные секции, структурная целостность).
- `python3 tools/check-doc-references.py` — ссылки, пути к файлам и `/api`-эндпоинты в текущей документации.
- `python3 tools/check-ssot-impact.py` — SSoT-гейт: правки кода по `.ssot-map.yml` требуют обновления `knowledge/`.
- `bash tools/verify-kotlin-refs.sh`, `python3 tools/check-spec-issue-link.py` — ссылки на Kotlin-сущности и связка спек с OpenProject.

## Сборка и запуск

```bash
# Backend
./gradlew karaoke-app:bootJar
./gradlew karaoke-web:bootJar

# Frontend (admin)
cd webvue3 && npm install && npm run dev   # dev
cd webvue3 && npm run build                # production

# Frontend (public)
cd karaoke-public && npm run dev
cd karaoke-public && npm run build

# Деплой (всегда из deploy/)
cd deploy
bash do.sh build
bash do.sh build_app
bash do.sh start / stop
```

Полная документация по сборке/деплою — в [DEVELOPMENT.md](./DEVELOPMENT.md#build--run).

## Скоуп

- **Активные модули** (5): `karaoke-app`, `karaoke-web`, `webvue3`,
  `karaoke-public`, `deploy/`.
- **Legacy** (вне скоупа): `karaoke-db`, `karaoke-vue`. Удаляются в
  отдельной задаче.

## Лицензия

Внутренний проект.
