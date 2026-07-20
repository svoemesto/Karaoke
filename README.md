# Karaoke (svoemesto)

> **Self-hosted pipeline для автоматического создания караоке-видео.**
> Kotlin/Spring Boot бэкенд + Vue 3 фронтенд.

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
| [AGENTS.md](./AGENTS.md) | Полные инструкции для AI-агента (русский) |
| [DEVELOPMENT.md](./DEVELOPMENT.md) | Архитектурный контекст, dated-история, ловушки |
| [CONTRIBUTING.md](./CONTRIBUTING.md) | **Правила оформления кода** (Kotlin, Vue, SQL, Shell, Docker) |
| [constitution.md](./.specify/memory/constitution.md) | Непреложные принципы проекта |
| [docs/features/](./docs/features/) | **Per-feature документы** для 9 ключевых подсистем |
| [docs/api/](./docs/api/) | Инструкция по KDoc/JSDoc |
| [docs/architecture-notes-archive.md](./docs/architecture-notes-archive.md) | История фич и баг-фиксов |

## Стандарты оформления кода

Проект использует **AGENTS.md + автоматические линтеры** с baseline-подходом.
Полные правила — в [CONTRIBUTING.md](./CONTRIBUTING.md). Краткая выжимка:

- **Kotlin**: ktlint (форматирование) + detekt (code smells). См. раздел
  [Kotlin / Spring Boot](./CONTRIBUTING.md#kotlin--spring-boot).
- **Vue/TS**: eslint\(eslint + prettier\). См. [Vue 3 / TypeScript](./CONTRIBUTING.md#vue-3--typescript).
- **Pre-commit**: `pip install pre-commit && pre-commit install`.
  Обход: `git commit --no-verify`.
- **Baseline**: текущие нарушения зафиксированы в `config/ktlint/baseline.xml`,
  `config/detekt/baseline.xml`, `webvue3/.eslint-baseline.json`,
  `karaoke-public/.eslint-baseline.json`. Темп сокращения: ≥10%/мес.

Скрипты в `tools/`:

- `tools/baseline-stats.sh` — статистика по baseline.
- `tools/generate-eslint-baseline.sh` — генерация baseline для ESLint.
- `tools/check-eslint-baseline.sh` — проверка, что новых нарушений нет.
- `tools/check-enforcement.sh` — проверка, что MUST-правила покрыты baseline.
- `tools/verify-doc-links.sh` — линк-чекер документации (lychee).
- `tools/check-feature-doc.sh` — структура per-feature документов.
- `tools/verify-kotlin-refs.sh` — валидация Kotlin-ссылок в документах.

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
