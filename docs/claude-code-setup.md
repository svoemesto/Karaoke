# Claude Code — настройка для проекта Karaoke

> **Для разработчиков, использующих Claude Code** (а не opencode).
> Этот документ — инструкция по настройке вашего **локального** `CLAUDE.md`,
> чтобы Claude Code понимал наши правила и паттерны.

## ⚠️ `CLAUDE.md` — локальный файл, НЕ в гите

`CLAUDE.md` в корне проекта читается Claude Code автоматически при старте
сессии. **Этот файл — персональный для каждого разработчика**, потому что:

- У разных разработчиков разный стиль работы с AI.
- У разных разработчиков разные настройки MCP-серверов.
- `git pull` с конфликтом по `CLAUDE.md` = потерянная локальная работа.

**Поэтому**: `CLAUDE.md` должен быть в `.git/info/exclude` (только для вас)
или в личном `~/.gitignore_global` — НЕ в коммите.

### Добавить в `.git/info/exclude` (рекомендуется)
```bash
# Один раз на машине:
echo "CLAUDE.md" >> .git/info/exclude
```

### Или в `~/.gitignore_global`
```bash
git config --global core.excludesfile ~/.gitignore_global
echo "CLAUDE.md" >> ~/.gitignore_global
```

---

## Где Claude Code берёт правила

Claude Code в проекте Karaoke должен прочитать **2 файла**:

| Файл | Где | Что |
|------|-----|-----|
| `CLAUDE.md` | **локально** в корне проекта | Персональные настройки + project-specific паттерны |
| `AGENTS.md` | в гите (общий) | Универсальные правила: KDoc/JSDoc, per-feature docs, pre-commit, ktlint-ловушки, Docker |

> Claude Code **не** читает `AGENTS.md` автоматически. Вы должны либо
> скопировать содержимое в свой `CLAUDE.md`, либо дать Claude Code инструкцию
> «прочитай `AGENTS.md` в начале сессии» (см. пример ниже).

---

## Рекомендуемый `CLAUDE.md` (шаблон)

Создайте `CLAUDE.md` в корне проекта со следующим содержимым:

```markdown
# Karaoke — Claude Code

> Персональный конфиг для работы с проектом Karaoke (svoemesto).
> Файл НЕ в гите — добавить в .git/info/exclude.

## Начало сессии

Перед любыми правками прочитай:
1. \`AGENTS.md\` (общие правила проекта, 230 строк)
2. \`.specify/memory/constitution.md\` (непреложные принципы, NON-NEGOTIABLE)
3. \`CONTRIBUTING.md\` (стиль кода, 892 строки)
4. \`DEVELOPMENT.md\` (архитектура, 164 строки)
5. \`docs/features/<slug>.md\` — per-feature документ, если правлю код этой фичи

## Правила проекта (краткая выжимка)

- **KDoc/JSDoc обязателен** на публичных API (FR-006 spec.md).
- **per-feature документ** обновлять при правке кода фичи (FR-009 spec.md).
- **ktlint baseline = 0** — никаких новых нарушений в PR.
- **ESLint baseline = 0** для webvue3 и karaoke-public.
- **KDoc coverage = 100%**, **JSDoc coverage = 100%**.
- **CI 7 jobs enforced** — все PASS перед merge.
- **Pre-commit хуки** (7 проверок) — запускаются перед каждым коммитом.

## Перед commit

\`\`\`bash
# Лит
./gradlew ktlintCheck
cd webvue3 && npm run lint:check && cd ..
cd karaoke-public && npm run lint:check && cd ..

# Coverage
bash tools/check-kdoc-coverage.sh
bash tools/check-jsdoc-coverage.sh webvue3
bash tools/check-jsdoc-coverage.sh karaoke-public

# Pre-commit (7 проверок)
pre-commit run --all-files
\`\`\`

Если что-то падает — **исправьте** (или `git commit --no-verify` в крайнем случае).

## Ловушки (top-5)

1. **Backticks в KDoc** ломают парсер. Заменять на «пакет mko», «multitrack» без backticks.
2. **\`redirectErrorStream(false)\`** для ffmpeg/ProcessBuilder блокирует процесс. Всегда \`true\`.
3. **\`nginx:alpine\`** — нет bash, контейнер падает. Использовать \`nginx:stable\`.
4. **Wildcard imports** в Kotlin — ОК (правило отключено).
5. **Сидя на старом master**: Phase 001 (PR #12-#28) добавил 17 PR за 2 дня.
   Перед серьёзной работой — \`git pull\` и просмотр \`docs/architecture-notes.md\`.

## Документы проекта

| Файл | Зачем |
|------|-------|
| \`AGENTS.md\` | Правила opencode-стиля (читай обязательно) |
| \`DEVELOPMENT.md\` | Архитектура + команды |
| \`CONTRIBUTING.md\` | Стиль кода (Kotlin/Vue/SQL/MD) |
| \`docs/onboarding.md\` | Setup новой машины |
| \`docs/architecture-notes.md\` | Changelog последних PR |
| \`docs/features/<slug>.md\` | Per-feature (11 + 1 документ) |
| \`docs/api/\` | API-эндпоинты (сгенерировано из кода) |
| \`.specify/memory/constitution.md\` | NON-NEGOTIABLE принципы |

## MCP-серверы (если нужны)

\`codegraph\` — read-only индекс символов. Использовать перед grep/Read
для понимания кода:
\`\`\`
codegraph_explore query="KaraokeProcess class location"
\`\`\`

## Tech Stack

- Backend: Kotlin 2.2.20, Spring Boot 3.5.6, JDK 18, Gradle
- Frontend: Vue 3 + Vite + Bootstrap 5
- DB: PostgreSQL 15 (без JPA/Hibernate — сырой JDBC)
- Storage: MinIO
- MLT framework (melt CLI) для видео
- Demucs, ffmpeg, Sheetsage для аудио

## Правила работы

1. **Не делай непрошеных изменений.** Если пользователь спросил «как работает X» —
   отвечай, не правь код.
2. **Объясняй перед действием.** Перед значимыми правками покажи план.
3. **Обновляй per-feature документ** в том же PR, что и код фичи.
4. **Не коммить без явного запроса** пользователя.
5. **Проверяй CI** (7/7 PASS) перед merge.
```

---

## Адаптация под ваш стиль

Этот шаблон — отправная точка. Адаптируйте под себя:

- **Если вы используете MCP-серверы** (Sentry, GitHub, Docker) — добавьте секцию.
- **Если у вас есть pre-commit aliases** — добавьте.
- **Если предпочитаете другой стиль языка** (более формальный/неформальный) — измените.

---

## Workflow с Claude Code

### 1. Старт сессии
Claude Code автоматически читает `CLAUDE.md`. Дайте ему 2-3 секунды на
«прогрев» (он может прочитать `AGENTS.md` и `constitution.md` если
попросите).

### 2. Задача
Опишите задачу в стиле:
```
Сделай X в файле Y. Учти паттерны из docs/features/<slug>.md.
```

### 3. Проверка
Перед commit Claude Code должен:
- Запустить ktlint/eslint (или предложить вам запустить).
- Обновить per-feature документ (если менял код фичи).
- Проверить KDoc/JSDoc на новых публичных API.

### 4. Commit + PR
Claude Code может сделать commit + push + создать PR, но **только если вы
явно попросите**. По умолчанию — показывает diff и ждёт подтверждения.

---

## Что НЕ нужно класть в `CLAUDE.md`

- **Секреты** (API-ключи, токены) — Claude Code не хранит их безопасно.
- **Локальные пути** (`~/projects/karaoke/...`) — отличаются у других.
- **Личные настройки IDE** — в `~/.claude/`, не в проекте.

---

## Связанные документы

- [`docs/onboarding.md`](./onboarding.md) — общий setup для любого AI-агента
- [`AGENTS.md`](../AGENTS.md) — общие правила opencode (читать обязательно)
- [`CONTRIBUTING.md`](../CONTRIBUTING.md) — стиль кода
- [`DEVELOPMENT.md`](../DEVELOPMENT.md) — архитектура
- [`.specify/memory/constitution.md`](../.specify/memory/constitution.md) — NON-NEGOTIABLE
- [`docs/architecture-notes.md`](./architecture-notes.md) — changelog
