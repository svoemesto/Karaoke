# Research: Приведение кода к стандартам и документирование фич

**Branch**: `001-code-standards-docs` | **Phase**: 0 | **Date**: 2026-07-20

Цель Phase 0 — зафиксировать технические решения по каждому «NEEDS
CLARIFICATION» / развилке из Technical Context плана. Каждое решение: что
выбрано, почему, что отвергнуто.

---

## Decision 1: Kotlin-линтеры

**Выбрано**: `ktlint` (форматирование) + `detekt` (code smells).

**Rationale**:
- `ktlint` — де-факто стандарт форматирования Kotlin, поддерживается Pinterest,
  zero-config для большинства правил, встроенный baseline-механизм
  (`--baseline`).
- `detekt` — лидер по code smells в Kotlin, 200+ правил, поддерживает baseline,
  легко расширяется кастомными правилами.
- Два инструмента дополняют друг друга: ktlint отвечает за стиль
  (отступы, переносы, импорты), detekt — за семантику (сложность, naming,
  потенциальные баги).
- Оба имеют Gradle-плагины (`com.github.pinterest:ktlint-gradle` и
  `io.gitlab.arturbosch.detekt:detekt-gradle-plugin`), CI-режим через
  `ktlintCheck` / `detekt` task.

**Alternatives**:
- **Spotless** — единый форматтер для Kotlin/JS/JSON/SQL, не code-smell. Может
  заменить ktlint, но не закрывает задачу «code smells». Отвергнут: дублирует
  ktlint и не даёт детект сложности.
- **KtLint-only** — использовать только ktlint без detekt. Проще, но не
  ловит code smells. Отвергнут: правила в `AGENTS.md` касаются не только
  форматирования, но и архитектурных инвариантов.
- **Scalastyle** — для Scala, не применимо.
- **detekt-only** — detekt умеет немного форматировать, но это не его
  основная задача; ktlint — лучше. Отвергнут.

---

## Decision 2: Vue/TypeScript-линтеры

**Выбрано**: `eslint@8.x` + `@vue/eslint-config-typescript` +
`@vue/eslint-config-prettier` + `prettier@3.x`.

**Rationale**:
- ESLint — стандарт для Vue/TS. Конфиги из `@vue`-семейства поддерживаются
  Vue-командой официально.
- `@vue/eslint-config-typescript` — типобезопасные правила для TS внутри Vue.
- `@vue/eslint-config-prettier` — отключает конфликтующие правила ESLint
  (форматирование — за Prettier).
- `prettier@3.x` — де-факто форматтер для Vue/TS/JSON/MD.
- ESLint flat-config (`eslint.config.js`) — пока не рекомендуется для Vue 3.4
  (многие пресеты не мигрировали), остаёмся на legacy `.eslintrc.cjs`.

**Alternatives**:
- **Biome** — быстрый, единый для JS/TS/CSS, но Vue-поддержка ещё молодая
  (экспериментальная). Отвергнут: риск нестабильности для прод-проекта.
- **ESLint flat-config** — новая система, не все плагины совместимы. Отвергнут
  для текущей итерации; миграция возможна позже.
- **TSLint** — deprecated. Отвергнут.

---

## Decision 3: Baseline-формат

**Выбрано**:
- `ktlint`: встроенный XML-формат (`config/ktlint/baseline.xml`), создаётся
  через `./gradlew ktlintGenerateBaseline` (после прогона по текущему коду).
- `detekt`: встроенный XML-формат (`config/detekt/baseline.xml`), создаётся
  через `./gradlew detektBaseline`.
- `eslint`: встроенного baseline нет → использовать
  `eslint-formatter-friendly` или кастомный скрипт, который
  парсит JSON-вывод и подавляет known-нарушения по списку `ruleId + file +
  line`. Baseline-файл — `.eslint-baseline.json` (массив
  `{ruleId, file, line, message}`).

**Rationale**:
- ktlint/detekt дают baseline из коробки — это снижает трудоёмкость.
- Для ESLint нет официального baseline, но формат friendly-formatter
  стабилен и легко генерируется.

**Alternatives**:
- **Кастомный baseline-формат для всех трёх** — больше работы, нет выгоды.
  Отвергнут.
- **Игнорировать baseline, исправлять всё разом** — заблокирует CI на старте
  (тысячи нарушений). Отвергнут по решению Q1=B.

---

## Decision 4: Pre-commit фреймворк

**Выбрано**: `pre-commit` (Python, https://pre-commit.com) +
`.pre-commit-config.yaml` в корне.

**Rationale**:
- Простой YAML-конфиг, не требует Node-зависимостей.
- Работает с любыми инструментами (Kotlin, Node, shell, Rust) — не привязан
  к экосистеме.
- В Karaoke уже используется подход «всё через bash-скрипты» (см.
  `deploy/build-lock.sh`, `deploy/announce.sh`); pre-commit — в той же
  парадигме.
- `pre-commit` запускается через `pip install pre-commit` один раз.

**Alternatives**:
- **husky + lint-staged** (Node-only) — требует `npm install` на каждой
  машине, привязан к Node. Отвергнут: проект multi-language, husky плохо
  покрывает Kotlin.
- **lefthook** (Go binary) — быстрый, кросс-языковой, но менее зрелый и
  менее распространённый. Отвергнут: меньше документации, меньше
  community-хуков.
- **Самописный git-хук** — больше работы, нет готовых хуков. Отвергнут.

---

## Decision 5: Doc-link-checker

**Выбрано**: `lychee` (https://github.com/lycheeverse/lychee) — Rust binary,
single executable, проверяет ссылки в Markdown.

**Rationale**:
- Один бинарь, не требует Node/Python — проще в pre-commit и CI.
- Очень быстрый (написан на Rust).
- Поддерживает offline-режим (только проверка синтаксиса) и online (HEAD-
  запросы).
- Проверяет как внешние (http/https), так и внутренние (`файл.md#anchor`,
  `путь/к/файлу.kt#метод`) ссылки.

**Alternatives**:
- **markdown-link-check** (Node) — медленнее, требует npm. Отвергнут.
- **Самописный скрипт на bash/python** — больше работы, нет смысла.
  Отвергнут.

**Ограничение**: `lychee` не понимает Kotlin-синтаксис
(`ClassName.kt:123:MethodName`) «из коробки», но понимает относительные
пути к файлам `.kt` и строки `:NNN`. Для ссылок `класс#метод` —
дополнительный простой скрипт `tools/verify-kotlin-refs.sh` (P3, см. SC-005).

---

## Decision 6: KDoc-генератор

**Выбрано**: Dokka (https://github.com/Kotlin/dokka) от JetBrains.

**Rationale**:
- Поддерживает KDoc, JavaDoc, смешанный Kotlin/Java.
- Вывод в HTML / Markdown / Jekyll (для GitHub Pages).
- Gradle-плагин `org.jetbrains.dokka:dokka-gradle-plugin`.
- Те же `@see`, `@param`, `@throws`, что и в Javadoc.
- Стандарт в Kotlin-экосистеме.

**Alternatives**:
- **Стандартный Javadoc** — Dokka читает KDoc, который совместим с Javadoc.
  Dokka даёт больше (Markdown в комментариях, мульти-модуль, Kotlin-типы).
  Отвергнут.
- **Самописный генератор** — нет смысла.

---

## Decision 7: Где жить CONTRIBUTING.md

**Выбрано**: `CONTRIBUTING.md` в корне репозитория.

**Rationale**:
- GitHub-конвенция (`.github/CONTRIBUTING.md` или `/CONTRIBUTING.md` →
  автоматически подсвечивается в PR-UI).
- `AGENTS.md` остаётся как «инструкции для AI-агента», `CONTRIBUTING.md` —
  для людей-разработчиков.
- Дублирования не будет: `AGENTS.md` и `CONTRIBUTING.md` ссылаются друг
  на друга; общая канва — в `CONTRIBUTING.md`, agent-specific — в `AGENTS.md`.

**Alternatives**:
- **`docs/code-style.md`** — глубже в дереве, не подхватывается GitHub'ом
  автоматически. Отвергнут.
- **`.github/CONTRIBUTING.md`** — эквивалентно корневому, но GitHub
  предпочитает корень. Отвергнут.

---

## Decision 8: Per-feature шаблон (6 разделов)

**Выбрано**: фиксированный шаблон с 6 обязательными разделами (из FR-005):

1. **Что делает** — 1-2 предложения, что это за фича.
2. **Зачем** — какую проблему решает, для кого.
3. **Как работает (кратко)** — архитектурный обзор, диаграмма или список
   ключевых компонентов, без излишних деталей.
4. **Инварианты / правила** — MUST/SHOULD-правила, вытекающие из
   `DEVELOPMENT.md` / `AGENTS.md` / `constitution.md`.
5. **Известные ловушки** — что ломало прод, какие edge-cases
   неочевидны, на что обращать внимание.
6. **Ссылки на ключевые классы/файлы** — `файл:строка` или
   `класс#метод`, валидируемые `lychee` + (опц.) `verify-kotlin-refs.sh`.

**Rationale**:
- 6 разделов — достаточно, чтобы покрыть все аспекты; не больше, чтобы не
  превращать доку в «простыню».
- Шаблон применяется ко всем 9 фичам из FR-004 одинаково — единообразие
  упрощает навигацию.

**Alternatives**:
- **Diátaxis-framework** (tutorial/how-to/reference/explanation) — хорошо
  для пользовательской документации, но избыточно для engineering-документации
  внутри репозитория. Отвергнут.
- **ADR (Architecture Decision Records)** — частично покрывает, но не
  описывает «как работает» и «ловушки». Отвергнут как основной формат;
  может быть добавлен как дополнительный (P3).

---

## Decision 9: Скоуп и миграция legacy

**Выбрано**: только активный код (`karaoke-app`, `karaoke-web`, `webvue3`,
`karaoke-public`, `deploy/`). Legacy `karaoke-db` (только `Main.java`,
не используется в проде) и `karaoke-vue` (только `src/assets`, не в
сборке) — вне скоупа.

**Rationale**:
- Из Q2=B (см. spec.md Clarifications).
- `karaoke-vue` уже помечен в `AGENTS.md` как «заброшенный, не в сборке».
  Документировать или линтить его — трата усилий. Удаление legacy —
  отдельная задача, не входит в эту фичу.

**Alternatives**:
- **Включить legacy в скоуп** — Q2 отверг пользователь. Не делаем.
- **Удалить legacy в рамках этой фичи** — Q2 не включал это; удаление
  может быть отдельной задачей позже.

---

## Итог Phase 0

Все развилки из Technical Context разрешены. Решения зафиксированы. Можно
переходить к Phase 1 (data-model.md, contracts/, quickstart.md).
