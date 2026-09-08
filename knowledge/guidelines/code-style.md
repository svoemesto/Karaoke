# Code Style

> **Домен**: `knowledge/guidelines/`
> **Назначение**: краткий справочник по code style Karaoke.
> **Источник**: [CONTRIBUTING.md](../../CONTRIBUTING.md) (полная версия, 892 строки).

Этот документ — **выжимка ключевых правил**. Для глубоких вопросов —
читать [CONTRIBUTING.md](../../CONTRIBUTING.md). Style enforced by:

- **ktlint** для Kotlin (CI-блокер).
- **ESLint + Prettier** для Vue/TS (CI-блокер).
- **pre-commit hooks** для всех проверок.

## Kotlin

### Базовые правила

- **Использовать nullable types** для колонок БД, которые могут быть NULL.
- **Избегать `is*` prefix для boolean** в DTOs (Jackson issue, см.
  [jackson-conventions](../../livedocs/architecture/jackson-conventions.md)).
- **Always URL-encode** query parameters with special characters.
- **`ProcessBuilder.redirectErrorStream(true)`** обязательно для всех
  внешних процессов (см. [ADR-0006](../adr/0006-processbuilder-redirect-errorstream.md)).

### Naming

- **Classes / objects**: `PascalCase`.
- **Functions / properties**: `camelCase`.
- **Constants**: `SCREAMING_SNAKE_CASE`.
- **Package**: lowercase, без underscore.
- **Backticks в KDoc** ломают парсер ktlint. Заменять
  `` `multitrack` `` → «multitrack» (без backticks).

### Тесты

- Юнит-тесты: JUnit 5 (`@Test`, `assertThrows`, etc.).
- Интеграционные тесты: помечены `@Disabled` (см. AGENTS.md).
- **Smoke-тесты** перед merge обязательны (см. [kara-pre-commit](#)).

## Vue / TypeScript

### Базовые правила

- **Composition API** (`<script setup>`) обязательно для новых компонентов.
- **Bootstrap-vue-next** для `webvue3` (admin), **Bootstrap 5** для
  `karaoke-public` (public).
- **Использовать `<script setup>` + `defineProps` + `defineEmits`** вместо
  Options API.

### Naming

- **Components**: `PascalCase.vue`.
- **Props / events / slots**: camelCase.
- **Composables**: `useXxx.ts` (префикс `use` обязателен).
- **Stores**: `xxx.store.ts` (Vuex) или `xxx.ts` (Pinia).

### TypeScript

- **Strict mode** включён (`"strict": true` в tsconfig).
- **Избегать `any`**: использовать `unknown` + type guards.
- **Nullable types** для всех optional значений (`foo: string | null`).

## SQL (PostgreSQL 16)

### Сырой JDBC

- **Без JPA/Hibernate** (см. [ADR-0001](../adr/0001-raw-jdbc.md)).
- **`associateBy { it.id }`** для diff-операций (не O(n²)).
- **`recordhash`-триггеры** для sync LOCAL ↔ SERVER.

### Миграции

- **Append-only**: никогда не редактировать существующие миграции.
- **Нейминг**: `NNN_description.sql`, где NNN — порядковый номер.
- **Defaults**: безопасные, не NULL если возможно.
- **Indexes**: для всех FK и часто используемых WHERE-условий.

## Markdown / docs

- **Frontmatter** обязателен для каждого LiveDoc (`status`, `slug`).
- **Templates**: использовать [livedocs/templates/](../../livedocs/templates/) или
  [knowledge/templates/](../../templates/).
- **C4 mermaid** для архитектурных диаграмм (L1, L2, L3).
- **State-based**, не changelog-style.
- **Эмодзи запрещены** в markdown (линтер падает, см. `tools/lint-knowledge.py`).
  Используйте `[WARN]` вместо эмодзи.
- **Bash-команды в обратных кавычках** для inline-команд.

## Shell / Docker

- **`bash -euo pipefail`** в начале shell-скриптов (fail fast).
- **`nginx:stable`** — НЕ `nginx:alpine` (нет bash, контейнер падает).
- **`node:22-alpine`** — НЕ `node:latest` (недетерминированный).
- **JRE** — НЕ JDK (для prod-образов Karaoke-app).

## Линтеры и форматирование

Перед каждым коммитом запускать (см. [AGENTS.md § Перед каждым git commit](../../AGENTS.md)):

```bash
# Линтеры
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew ktlintCheck
cd webvue3       && npm run lint:check && cd ..
cd karaoke-public && npm run lint:check && cd ..

# Документация
bash tools/check-kdoc-coverage.sh --strict
bash tools/check-jsdoc-coverage.sh webvue3 --strict
bash tools/check-jsdoc-coverage.sh karaoke-public --strict

# Prettier
cd webvue3       && npx prettier --check "src/**/*.{vue,js,ts,json}" && cd ..
cd karaoke-public && npx prettier --check "src/**/*.{vue,js,ts,json}" && cd ..

# Pre-commit (все 7 проверок)
pre-commit run --all-files
```

## Карта Style → CI

| Style | Линтер | CI-проверка |
| --- | --- | --- |
| Kotlin | ktlint | `ktlint (Kotlin/Java)` |
| Vue/TS | ESLint + Prettier | `ESLint + Prettier (webvue3)`, `ESLint + Prettier (karaoke-public)` |
| KDoc | custom | `KDoc coverage` |
| JSDoc | custom | `JSDoc coverage` |
| Markdown | ktlint (жирно) + lint-knowledge.py | `Docs (structure + offline links)`, `LiveDocs structure` |
