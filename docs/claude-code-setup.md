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

---

## FAQ / Troubleshooting

### Q: Claude Code не подхватывает мой `CLAUDE.md`

**Проверьте:**
1. Файл лежит **в корне проекта** (рядом с `package.json` и `build.gradle.kts`), а не в подпапке.
2. Имя файла **точно `CLAUDE.md`** (заглавные буквы, без пробелов).
3. Файл не пустой и содержит хотя бы один заголовок (`# ...`).
4. Перезапустите Claude Code (закройте сессию, откройте заново).

**Если не помогло:** Claude Code читает `CLAUDE.md` из **текущей директории**
+ родительских. Запускайте Claude Code из корня проекта:
```bash
cd ~/projects/Karaoke
claude
```

### Q: При `git pull` появляется merge conflict в `CLAUDE.md`

**Решение:** добавьте `CLAUDE.md` в `.git/info/exclude`:
```bash
echo "CLAUDE.md" >> .git/info/exclude
```

Если уже случился конфликт:
```bash
# 1. Сохраните свою версию:
cp CLAUDE.md CLAUDE.md.local.bak

# 2. Откатите origin-версию:
git checkout --theirs CLAUDE.md

# 3. Восстановите свою:
mv CLAUDE.md.local.bak CLAUDE.md

# 4. Добавьте в exclude (чтобы не повторилось):
echo "CLAUDE.md" >> .git/info/exclude

# 5. Продолжайте merge:
git add CLAUDE.md
git merge --continue
```

### Q: Claude Code игнорирует мои инструкции в `CLAUDE.md`

**Возможные причины:**
- `CLAUDE.md` слишком длинный (Claude Code обрезает до ~8K токенов).
  Держите **до 200 строк / 1500 слов**. Длинные правила — в отдельные
  файлы (`.claude/rules.md`) и подключайте через `@include`.
- Инструкции противоречивы (например, «всегда тестируй» + «не трать время на тесты»).
  Claude Code выберет последнее. Сделайте иерархию явной.
- Формат не структурирован (сплошной текст без заголовков).
  Используйте `## Секции` и `- bullet`-списки.

**Как проверить, что Claude Code прочитал:** в начале сессии спросите
«Какие у тебя инструкции по этому проекту?» — должен процитировать
`CLAUDE.md`.

### Q: Claude Code не знает проект (не читал `AGENTS.md`)

По умолчанию Claude Code **НЕ** читает `AGENTS.md`. Добавьте в свой
`CLAUDE.md` явную инструкцию:
```markdown
## Начало сессии

Перед любыми правками прочитай:
1. \`AGENTS.md\` (общие правила проекта, 230 строк)
2. \`.specify/memory/constitution.md\` (непреложные принципы)
3. \`docs/features/<slug>.md\` — если правлю код этой фичи
```

Claude Code прочитает эти файлы при первом упоминании в сессии.

### Q: Хочу, чтобы Claude Code прочитал `AGENTS.md` автоматически

Используйте `.claude/rules.md` с директивой `@include`:
```markdown
<!-- .claude/rules.md -->
# Project rules

@import ../AGENTS.md
@import ../CONTRIBUTING.md
@import ../.specify/memory/constitution.md
```

Claude Code подхватит `.claude/rules.md` при старте сессии.

> ⚠️ `.claude/` — локальная папка, добавьте в `.git/info/exclude`.

### Q: Claude Code делает то, что я не просил

Это поведение «proactive» — Claude Code может предлагать изменения,
которые кажутся ему логичными. Чтобы отключить:
1. В шаблоне `CLAUDE.md` явно: «Не делай непрошеных изменений».
2. Используйте «approval mode» в Claude Code: попросите показывать план
   перед каждым действием.

### Q: Мой `CLAUDE.md` использует другой AI-агент на этой же машине

Если вы переключаетесь между opencode и Claude Code:
- `AGENTS.md` (в гите) — opencode читает его автоматически.
- `CLAUDE.md` (локально) — Claude Code читает его автоматически.
- Не дублируйте содержимое: в `CLAUDE.md` просто сошлитесь на `AGENTS.md`.

### Q: Как синхронизировать `CLAUDE.md` с командой?

**Не синхронизируйте.** `CLAUDE.md` — личный файл. У каждого свой стиль.
**Общие правила** живут в `AGENTS.md` / `CONTRIBUTING.md` / `constitution.md`.
**Персональные настройки** — в `~/.claude/` (глобально) или `CLAUDE.md` (локально).

Если нашли общее полезное правило — предложите PR в `AGENTS.md`.

### Q: CI падает на KDoc/JSDoc, а Claude Code говорит «всё ОК»

**Причина:** Claude Code проверяет синтаксис, но не запускает лит-скрипты.
**Решение:** в `CLAUDE.md` явно пропишите:
```markdown
## Перед commit ОБЯЗАТЕЛЬНО запусти:
./gradlew ktlintCheck
cd webvue3 && npm run lint:check && cd ..
bash tools/check-kdoc-coverage.sh
bash tools/check-jsdoc-coverage.sh webvue3
```

И в начале сессии скажите: «Перед коммитом покажи мне вывод всех 4 команд».

### Q: `npm install` упал с peer dependency conflict

**Не пытайтесь чинить вручную.** Проверьте:
1. Node версия: `node -v` (должна быть 22+).
2. Удалите `node_modules` + `package-lock.json`:
   ```bash
   cd webvue3
   rm -rf node_modules package-lock.json
   npm install
   ```
3. Если не помогло — обновите lock-файл до актуального с `master`:
   ```bash
   git checkout master -- package-lock.json
   npm install
   ```

### Q: Docker-контейнер не стартует

**Частые причины** (см. AGENTS.md, секция «Dockerfile-ловушки»):
- `nginx:alpine` → заменить на `nginx:stable`.
- `node:latest` → заменить на `node:22-alpine`.
- Порты заняты другим процессом → `lsof -i :8080` и убить.
- `do.env` отсутствует → скопировать `do.env.example`.

### Q: Pre-commit хук падает на каждом коммите

**См.** [CONTRIBUTING.md](../CONTRIBUTING.md) секция «Pre-commit и CI».
Часто решается:
```bash
./gradlew ktlintFormat           # авто-форматирование
cd webvue3 && npx prettier --write "src/**/*.{vue,js,ts}" && cd ..
```

Если хук мешает срочному коммиту: `git commit --no-verify`. Но CI всё
равно поймает — лучше исправить сразу.

### Q: Хочу пользоваться VS Code + Claude Code (не CLI)

Расширение Claude Code for VS Code читает `CLAUDE.md` так же, как CLI.
Дополнительно настройте:
1. `Claude > Settings: Workspace Trust` — enabled для проекта.
2. `Claude > Edit Permissions` — `Edit`, `Bash(npm:*)`, `Bash(./gradlew:*)`.

### Q: Где хранить API-ключи для LLM в Claude Code?

**Не в `CLAUDE.md`.** Используйте:
- `~/.claude/.env` (глобально).
- Переменные окружения в shell-профиле.
- Vault (1Password CLI, Bitwarden CLI).

Claude Code автоматически подхватит `.env` из `~/.claude/`.
