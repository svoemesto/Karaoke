# Onboarding — Новый разработчик Karaoke

> **Домен**: `knowledge/public/`
> **Назначение**: 30-минутный вход в проект.
> **Полная версия**: [livedocs/onboarding.md](../../../livedocs/onboarding.md) (историческая).

Это **сжатая** версия onboarding для новых разработчиков. Чтение
занимает ~15 минут, после чего вы знаете, где что искать.

## 1. Структура проекта (2 минуты)

```
karaoke/                          ← корень проекта
├── karaoke-app/                  ← Kotlin/Spring admin-API + ML-пайплайн
├── karaoke-web/                  ← Kotlin/Spring админ-web + security
├── webvue3/                      ← Vue 3 admin SPA
├── karaoke-public/               ← Vue 3 публичный сайт
├── deploy/                       ← Docker, scripts
├── specs/                        ← спецификации (NNN-<slug>/)
├── livedocs/                     ← Living Documentation v1 (legacy, 243 файла)
├── knowledge/                    ← Living Documentation v2 (SSoT)
├── docs/                         ← прочая техническая документация
└── tools/                        ← скрипты (линтеры, проверки)
```

**Главное правило**: код-факты живут в `knowledge/domains/<name>/`,
спецификации — в `specs/NNN-*/`, livedocs v1 — архивная коллекция.

## 2. Что прочитать в первую очередь (10 минут)

| Документ | Зачем |
| --- | --- |
| [AGENTS.md](../../../AGENTS.md) | Runtime-инструкции, governance, git workflow |
| [CONTRIBUTING.md](../../../CONTRIBUTING.md) | Code style (Kotlin, Vue, SQL, MD, Sh, Docker) |
| [CLAUDE.md](../../../CLAUDE.md) | Гайдлайны для Claude Code |
| [DEVELOPMENT.md](../../../DEVELOPMENT.md) | Архитектура + команды сборки/деплоя |
| [knowledge/README.md](../README.md) | Карта LivingDocs v2 |

## 3. C4-модель (5 минут)

Проект Karaoke описан в C4-нотации:

- **L1 — System Context** ([livedocs/architecture/L1-system-context.md](../../../livedocs/architecture/L1-system-context.md))
  Место Karaoke в мире: посетители, Telegram, VK ID OAuth.
- **L2 — Containers** ([livedocs/architecture/L2-containers.md](../../../livedocs/architecture/L2-containers.md))
  Технические контейнеры: `karaoke-app`, `karaoke-web`, `webvue3`,
  `karaoke-public`, PostgreSQL, MinIO.
- **L3 — Components** ([livedocs/architecture/L3-components.md](../../../livedocs/architecture/L3-components.md))
  Технические компоненты внутри контейнеров.

## 4. Bounded Contexts (5 минут)

Karaoke разбит на **9 доменов** (Bounded Contexts). Каждый — отдельная
директория в `knowledge/domains/`:

| Домен | Что делает |
| --- | --- |
| [identity](../../domains/identity/domain.md) | Пользователи, авторизация, сессии. |
| [catalog](../../domains/catalog/domain.md) | Каталог песен (18k+ записей). |
| [rendering](../../domains/rendering/domain.md) | Рендеринг MP4 через MLT/melt. |
| [processing](../../domains/processing/domain.md) | Demucs, Sheetsage, Playwright. |
| [publishing](../../domains/publishing/domain.md) | Эфир, подписка, статистика главной. |
| [editorial](../../domains/editorial/domain.md) | Задания редакторов, self-assign. |
| [stats](../../domains/stats/domain.md) | Аналитика трафика, воронка. |
| [caching](../../domains/caching/domain.md) | Паттерны кеширования. |
| [monitoring](../../domains/monitoring/domain.md) | Мониторинг, SLF4J-категории. |

Полный глоссарий — [glossary.md](glossary.md).

## 5. Главные ADR (3 минуты)

Прочитайте **5 ключевых ADR**, чтобы понять основные архитектурные решения:

| ADR | О чём |
| --- | --- |
| [0001-raw-jdbc](../adr/0001-raw-jdbc.md) | Почему сырой JDBC, без JPA/Hibernate. |
| [0002-mlt-instead-of-ffmpeg](../adr/0002-mlt-instead-of-ffmpeg.md) | Почему MLT, а не ffmpeg. |
| [0004-karaoke-app-admin-only](../adr/0004-karaoke-app-admin-only.md) | Разделение admin vs public. |
| [0005-self-hosted-ml](../adr/0005-self-hosted-ml.md) | Self-hosted ML (Demucs, Sheetsage). |
| [0006-processbuilder-redirect-errorstream](../adr/0006-processbuilder-redirect-errorstream.md) | `redirectErrorStream(true)` обязательно. |

## 6. Главные конвенции (5 минут)

Из [AGENTS.md](../../../AGENTS.md) и [CONTRIBUTING.md](../../../CONTRIBUTING.md):

1. **Сырой JDBC + recordhash** (никакого JPA/Hibernate).
2. **`nginx:stable`** (НЕ `nginx:alpine`).
3. **`node:22-alpine`** (НЕ `node:latest`).
4. **JRE** для prod-образов (не JDK).
5. **`ProcessBuilder.redirectErrorStream(true)`** обязательно.
6. **Двух-flow авторизация**: `karaoke-web` (cookies) и `karaoke-public`
   (отдельный flow).
7. **`is`-prefix запрещён** для boolean в DTO (Jackson).
8. **Санитайзер идемпотентен** (`sanitize(sanitize(s)) == sanitize(s)`).
9. **«Доступ — только онлайн»**: НЕ упоминать MP4/скачивание.
10. **Сайт-центричная модель**: площадки (Sponsr/Dzen/VK/Max/TG) —
    технический канал (DEMO), НЕ реклама.

## 7. Workflow (3 минуты)

### Типичный цикл фичи

1. Прочитать [AGENTS.md](../../../AGENTS.md) § «Git — CI-gate для master».
2. Создать feature-ветку `NNN-<slug>` от master (через
   `./tools/reserve-branch-number.sh <slug>`).
3. Создать спецификацию в `specs/NNN-<slug>/` (через
   [livedocs/templates/](../../../livedocs/templates/) или spec-template).
4. Реализовать фичу в коде.
5. Обновить соответствующий **домен** в `knowledge/domains/`
   ([соответствующий файл](../../domains/)).
6. Запустить линтеры (`./gradlew ktlintCheck`, `npm run lint`,
   `bash tools/check-livedocs-structure.sh`, `python3 tools/lint-knowledge.py`).
7. Push и PR через `gh pr create --base master`.
8. Дождаться CI (7 проверок), при зелёном — `gh pr merge --merge`.

### Куда смотреть перед merge

- [Knowledge state mutation lifecycle](../README.md#жизненный-цикл-документации):
  обновить домен + ADR + guideline при изменении кода.
- [Pre-commit checklist](../../../AGENTS.md): 7 проверок, lint, prettier.

## 8. Типичные ошибки (TOP-10)

1. **Прямые коммиты в master** — запрещены (см. AGENTS.md).
2. **Пренебрежение линтерами** — CI блокирует merge.
3. **Игнорирование Knowledge SSoT** — обновление обязательно.
4. **Использование `is`-prefix** в DTO — Jackson вырежет поле.
5. **Правка AGENTS.md** без согласования — нарушение governance.
6. **`nginx:alpine`** — контейнер упадёт (нет bash).
7. **`node:latest`** — недетерминированный.
8. **`redirectErrorStream(false)`** — ProcessBuilder блокируется.
9. **Упоминание MP4/скачивания** в рекламе — нарушение оферты.
10. **`git push` через VPN** — может упасть с `EOF`.

## 9. Команды (cheat-sheet)

```bash
# Резервировать номер ветки
N=$(./tools/reserve-branch-number.sh my-slug)

# Создать feature-ветку
git checkout -b "${N}-my-slug" master

# Линтеры
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew ktlintCheck
bash tools/check-livedocs-structure.sh
python3 tools/lint-knowledge.py

# Сборка
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew clean bootJar --parallel

# Pre-commit
pre-commit run --all-files
```

## 10. Куда обращаться

- **Гайдлайны**: AGENTS.md, CONTRIBUTING.md, CLAUDE.md.
- **Архитектура**: knowledge/domains/, knowledge/adr/, knowledge/system/.
- **Оперативные playbook'и**: [knowledge/guidelines/runbooks/](../../guidelines/runbooks/),
  [livedocs/runbooks/](../../../livedocs/runbooks/).
- **Стратегия роста**: [livedocs/strategy/](../../../livedocs/strategy/).

Добро пожаловать в Karaoke!
