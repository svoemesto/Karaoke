# Onboarding — настройка новой машины разработчика

> **Цель.** За 30-60 минут привести новую машину в состояние «готова к PR,
> который пройдёт CI без правок». Чеклист работает для разработчика
> **с любым AI-агентом** (opencode, Claude Code, Cursor, Cody, Aider и т.д.).

## Скоуп

5 активных модулей: `karaoke-app`, `karaoke-web`, `webvue3`, `karaoke-public`,
`deploy/`. Legacy `karaoke-db` и `karaoke-vue` — вне скоупа.

## Шаг 1. Установить системные зависимости

| Зависимость | Версия | Команда проверки |
|-------------|--------|------------------|
| **JDK** | 18+ (LTS; 22 если есть) | `java -version` |
| **Node** | 22 (LTS) | `node -v` |
| **npm** | 10+ (с Node 22) | `npm -v` |
| **Python** | 3.11+ | `python3 --version` |
| **Docker** | 24+ (с docker compose v2) | `docker --version` |
| **Git** | 2.40+ | `git --version` |
| **PostgreSQL client** | 15+ | `psql --version` |
| **ffmpeg** | 6+ | `ffmpeg -version` |
| **melt (MLT)** | 7+ | `melt -version` |
| **pre-commit** | 3+ | `pre-commit --version` |

### macOS (через Homebrew)
```bash
brew install openjdk@18 node@22 python@3.11 git postgresql@15 ffmpeg melt pre-commit
brew install --cask docker
```

### Ubuntu 22.04+
```bash
sudo apt update
sudo apt install -y openjdk-18-jdk nodejs npm python3.11 git postgresql-client-15 ffmpeg
# melt: см. https://www.mltframework.org/docs/install/
pip3 install pre-commit
```

### Arch Linux
```bash
sudo pacman -S jdk18-openjdk nodejs npm python git postgresql ffmpeg mlt pre-commit
```

---

## Шаг 2. Клонировать репозиторий

```bash
git clone https://github.com/svoemesto/Karaoke.git
cd Karaoke
```

Настроить git для удобной работы:
```bash
git config blame.ignoreRevsFile .git-blame-ignore-revs
# Удобные алиасы (опционально):
git config alias.lg "log --oneline --graph --decorate -20"
git config alias.co checkout
```

---

## Шаг 3. Прочитать ключевые документы

> **AI-агент: прочитай эти файлы в первую очередь при старте сессии.**

| Документ | Зачем | Размер |
|----------|-------|--------|
| [`AGENTS.md`](../AGENTS.md) | Инструкции для opencode (наш primary AI-агент) | 230 строк |
| [`CLAUDE.md`](../CLAUDE.md) | Инструкции для Claude Code | 46 строк |
| [`.specify/memory/constitution.md`](../.specify/memory/constitution.md) | **Непреложные принципы (NON-NEGOTIABLE)** | ~150 строк |
| [`DEVELOPMENT.md`](../DEVELOPMENT.md) | Архитектура + команды сборки/деплоя | 164 строки |
| [`CONTRIBUTING.md`](../CONTRIBUTING.md) | Правила оформления кода | 892 строки |
| [`docs/architecture-notes.md`](./architecture-notes.md) | Changelog последних PR (Pass 1-8) | 268 строк |
| [`docs/features/<slug>.md`](./features/) | Per-feature документы (11 + 1) | 50-180 строк каждый |

**Приоритет при расхождении**:
1. `constitution.md` (NON-NEGOTIABLE)
2. `AGENTS.md` / `CLAUDE.md` (специфичные для AI-агента)
3. `DEVELOPMENT.md` (durable-карта)
4. `CONTRIBUTING.md` (стиль кода)
5. `docs/features/<slug>.md` (детали фичи)

---

## Шаг 4. Настроить AI-агент

### opencode (наш primary)
1. Установить: `npm install -g opencode` или через [opencode.ai](https://opencode.ai).
2. В `~/.config/opencode/AGENTS.md` уже могут быть глобальные инструкции.
3. CodeGraph MCP-сервер уже настроен в нашем конфиге (см. AGENTS.md, секция CodeGraph).

### Claude Code
1. Установить: `npm install -g @anthropic-ai/claude-code` или через [claude.ai/code](https://claude.ai/code).
2. **`CLAUDE.md` — локальный файл**, НЕ в гите. Создать свой в корне проекта.
3. **Добавить `CLAUDE.md` в `.git/info/exclude`** — чтобы `git pull` не давал merge conflict.
4. Шаблон и подробная инструкция — [`docs/claude-code-setup.md`](./claude-code-setup.md).
5. **Claude Code НЕ читает `AGENTS.md` автоматически** — добавить инструкцию в свой
   `CLAUDE.md` «прочитай `AGENTS.md` в начале сессии» (есть в шаблоне).

### Cursor
1. Cursor читает `.cursorrules` (можно создать на основе `AGENTS.md` + `CLAUDE.md`).
2. Symlink: `ln -s AGENTS.md .cursorrules` (Cursor понимает KDoc-стиль).
3. Добавить `.cursorrules` в `.git/info/exclude` (если персональный).

### Любой другой агент
1. Скопировать содержимое `AGENTS.md` + `docs/claude-code-setup.md` в системный промпт.
2. Добавить инструкцию: «Перед любыми правками прочитай
   `constitution.md`, `CONTRIBUTING.md` и `docs/features/<соответствующий>.md`».

---

## Шаг 5. Установить pre-commit хуки

```bash
pip3 install pre-commit
pre-commit install
pre-commit run --all-files
```

Pre-commit запускает **7 проверок** перед каждым коммитом:
1. `ktlint` (Kotlin/Java)
2. `eslint` (webvue3)
3. `eslint` (karaoke-public)
4. `prettier` (webvue3)
5. `prettier` (karaoke-public)
6. `lychee` (проверка ссылок в `.md` файлах)
7. `check-feature-doc` (структура per-feature документов)

Если хук падает — **исправьте** (или `git commit --no-verify` в крайнем случае,
но CI всё равно поймает).

---

## Шаг 6. Собрать проект локально

### Backend (Kotlin/Spring Boot)
```bash
./gradlew karaoke-app:bootJar
./gradlew karaoke-web:bootJar
```

### Frontend (webvue3)
```bash
cd webvue3
npm install
npm run build       # production-сборка в dist/
npm run dev         # dev-сервер на http://localhost:5173
```

### Frontend (karaoke-public)
```bash
cd karaoke-public
npm install
npm run build
npm run dev
```

### Docker-окружение (БД + MinIO + приложение)
```bash
cd deploy
bash do.sh start
```

После старта доступны:
- `karaoke-app`: http://localhost:8080
- `karaoke-web`: http://localhost:8090
- `karaoke-public`: http://localhost:8888
- `MinIO`: http://localhost:9001 (admin/minioadmin)
- `PostgreSQL`: localhost:5432 (karaoke/karaoke)

---

## Шаг 7. Проверить что CI будет зелёным

Перед первым PR запустите локально:

```bash
# Kotlin lint
./gradlew ktlintCheck

# Frontend lint
cd webvue3 && npm run lint:check && cd ..
cd karaoke-public && npm run lint:check && cd ..

# Документация
bash tools/check-feature-doc.sh docs/features/*.md
bash tools/check-kdoc-coverage.sh   # должно быть 100%
bash tools/check-jsdoc-coverage.sh webvue3
bash tools/check-jsdoc-coverage.sh karaoke-public

# Полная сборка
./gradlew :karaoke-app:compileKotlin
./gradlew :karaoke-web:compileKotlin
```

**Все должны быть зелёными** (или baseline = 0).

---

## Шаг 8. Создать первый PR

1. Создать ветку: `git checkout -b 0XX-feature-name`
2. Внести изменения (KDoc/JSDoc, код, документация).
3. **Перед коммитом**:
   - Обновить соответствующий `docs/features/<slug>.md` если меняли код фичи (FR-009).
   - Добавить KDoc/JSDoc на публичные API (FR-006).
   - Запустить pre-commit хуки.
4. Commit + push:
   ```bash
   git add -A
   git commit -m "feat(scope): описание"
   git push -u origin 0XX-feature-name
   ```
5. Создать PR: `gh pr create --title "..." --body "..."`
6. Дождаться CI 7/7 PASS.
7. Если есть ревью — поправить.
8. Merge: `gh pr merge <N> --merge --delete-branch`.

---

## Ловушки (top-5)

1. **Backticks в KDoc** ломают парсер (`*` после `/` = новый комментарий).
   Заменять на «пакет mko», «файлы mko», «multitrack» без backticks.

2. **`redirectErrorStream(false)`** для ffmpeg/ProcessBuilder блокирует
   процесс. Всегда `true`.

3. **Docker `nginx:alpine`** — нет bash, контейнер падает. Использовать
   `nginx:stable` (Debian).

4. **Wildcard imports** в Kotlin — ОК (правило `no-wildcard-imports`
   отключено в `.editorconfig`).

5. **Сидя на старом `master`**: Phase 001 (PR #12-#27) добавил 16 PR за
   2 дня. Перед серьёзной работой — `git pull` и просмотр
   [`docs/architecture-notes.md`](./architecture-notes.md).

---

## Контрольный список (для самопроверки)

Перед первым PR ответьте «да» на все вопросы:

- [ ] JDK 18+, Node 22, Python 3.11+, Docker, pre-commit установлены
- [ ] Репозиторий склонирован, `git config blame.ignoreRevsFile` настроен
- [ ] Прочитал `constitution.md`, `AGENTS.md`/`CLAUDE.md`, `DEVELOPMENT.md`, `CONTRIBUTING.md`
- [ ] Прочитал per-feature документ для фичи, которую буду менять
- [ ] `pre-commit install` выполнен, `pre-commit run --all-files` зелёный
- [ ] `./gradlew ktlintCheck` зелёный
- [ ] `npm run lint:check` (для webvue3 и karaoke-public) зелёный
- [ ] `bash tools/check-kdoc-coverage.sh` показывает 100%
- [ ] `bash tools/check-jsdoc-coverage.sh` показывает 100%
- [ ] Docker-окружение стартует через `bash do.sh start`
- [ ] AI-агент понимает AGENTS.md/CLAUDE.md (проверить через простой вопрос)

**Если все «да» — вы готовы делать PR, который пройдёт CI с первого раза.**

---

## Связанные документы

- [`AGENTS.md`](../AGENTS.md) — инструкции для opencode
- [`CLAUDE.md`](../CLAUDE.md) — инструкции для Claude Code
- [`.specify/memory/constitution.md`](../.specify/memory/constitution.md) — непреложные принципы
- [`DEVELOPMENT.md`](../DEVELOPMENT.md) — архитектура
- [`CONTRIBUTING.md`](../CONTRIBUTING.md) — стиль кода
- [`docs/architecture-notes.md`](./architecture-notes.md) — changelog
- [`docs/features/`](./features/) — per-feature документы
- [`CONTRIBUTING.md#pre-commit-и-ci`](../CONTRIBUTING.md) — детали pre-commit
- [`CONTRIBUTING.md#обновление-этого-документа`](../CONTRIBUTING.md) — как обновлять правила
