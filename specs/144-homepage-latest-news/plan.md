# Implementation Plan: 144-homepage-latest-news

**Branch**: `144-homepage-latest-news` | **Date**: 2026-08-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/144-homepage-latest-news/spec.md`

## Summary

Добавить на главную страницу сайта компактный блок «последние 5 новостей» (дата/время, заголовок, ссылка на песню/новость) в двух местах:

- SPA-главная `karaoke-public` (`/`) — компонент `LatestNewsSection.vue` между блоком статистики и блоком навигационных карточек в `HomeView.vue`.
- Legacy Thymeleaf-главная `karaoke-web` (`/`) — таблица из 5 строк в `main.html`, отрендеренная сервером через `MainController.main()`.

Данные берутся из уже существующей модели `News.loadPublished` (`karaoke-app/.../model/News.kt:212`) — никаких новых колонок/таблиц/индексов/SyncRegistry-целей не требуется. Контракт бэкенда — переиспользование существующего `GET /api/public/news?page=0&size=5` (минимум изменений; единый код-путь с пагинацией ленты; дополнительный код-путь не нужен). Ошибки запроса деградируют «тихо» — блок просто не рендерится.

## Technical Context

**Language/Version**: Kotlin 1.x (JDK 17) для бэкенда; JavaScript/Vue 3 для обоих фронтендов; Thymeleaf для legacy.

**Primary Dependencies**: Spring Boot 3.x, Bootstrap 5 (`karaoke-public`) / Bootstrap 4 (`karaoke-web`), Vue 3 + Vite + Vuex (только `karaoke-public` для новостей не нужен — состояние локально в `HomeView.vue`).

**Storage**: PostgreSQL через сырой JDBC (таблица `tbl_news` уже существует; новых миграций не требуется).

**Testing**: ручная проверка пользователем на dev/staging/prod (см. AGENTS.md — тестов в CI нет, существующие интеграционные тесты `@Disabled`). Автоматизированная валидация — quickstart.md (dev-сценарии).

**Target Platform**: Linux server, Docker + nginx (бэкенд `karaoke-web` + SPA `karaoke-public` + nginx `80to8897`).

**Project Type**: web-service (бэкенд `karaoke-web`) + 2 SPA-фронтенда (`karaoke-public`, legacy `karaoke-web/main.html`).

**Performance Goals**: SC-002 спеки — эндпоинт `GET /api/public/news?page=0&size=5` отвечает <300 мс на объёме 19000+ записей в `tbl_news`. Достигается за счёт существующего `LIMIT 5` и индекса на `publish_at` (см. `News.loadPublished`, уже работает в продакшене для ленты `/news`).

**Constraints**:
- Никаких новых полей/таблиц/индексов/миграций — фича чисто презентационная.
- Никаких изменений в SyncRegistry, `news`-sync-цели, существующих контроллерах за пределами возможного нового эндпоинта (который мы НЕ делаем — переиспользуем существующий).
- Никаких зависимостей от `karaoke-app` для `karaoke-web` (модель `News` безопасна для `karaoke-web` — она не тянет `ConstantsKt`, см. комментарий в `PublicNewsController.kt:11-17`).
- Совместимость с уже работающим `GET /api/public/news/since` (бейдж непрочитанных) и с `GET /api/public/news` (пагинация ленты) — оба НЕ трогаем.
- Graceful degradation при ошибке бэкенда (FR-013 спеки).

**Scale/Scope**: 1 эндпоинт (без нового кода — переиспользуем), 2 фронтенд-блока (1 Vue + 1 Thymeleaf). Никаких изменений в БД, никаких новых сущностей.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Релевантность | Решение |
|---|---|---|
| I. Self-contained автопайплайн | Не применимо — фича не трогает hot-path обработки медиа | ✅ pass |
| II. Сырой JDBC + дифф по хэшам | Не применимо — фича только читает `tbl_news` (SELECT с LIMIT 5), никаких новых полей/таблиц/диффов/SyncRegistry | ✅ pass |
| III. Двух-БД синхронизация через SyncRegistry | Не применимо — нет новой сущности и нет новой sync-цели | ✅ pass |
| IV. Async-очередь задач с парсингом stdout | Не применимо — нет новых OS-подпроцессов | ✅ pass |
| V. Двух-фронтенд: админка и публичный сайт — разные приложения | **Применимо** — фича трогает и `karaoke-public` (SPA), и `karaoke-web` (legacy Thymeleaf), но не смешивает ответственности: SPA-блок — в `karaoke-public`, Thymeleaf — в `karaoke-web`. Аналогично уже сделано для счётчиков главной (StatBySong → `/api/public/stats` для обоих). | ✅ pass |
| VI. Code Standards | **Применимо** — новый Vue-компонент MUST иметь JSDoc с `@see docs/features/homepage-latest-news.md` (FR-006 конституции). Новый per-feature документ `docs/features/homepage-latest-news.md` MUST быть создан в этом PR (FR-009 конституции, список — `docs/features/README.md`). ktlint/ESLint baseline — без новых нарушений. | ✅ pass (планируется выполнить в Phase 1) |
| VII. Cross-Machine Setup | Не применимо напрямую — никаких новых конфигов, никаких изменений в `.gitattributes`/`.git-blame-ignore-revs` | ✅ pass |
| VIII. Секреты и git-гигиена | Не применимо — никаких секретов, никаких новых env-переменных | ✅ pass |

**Итог**: все 8 принципов проходят. Complexity Tracking остаётся пустым (нет нарушений, которые нужно обосновывать).

## Project Structure

### Documentation (this feature)

```text
specs/144-homepage-latest-news/
├── plan.md              # Этот файл (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── public-news-api.md  # контракт GET /api/public/news (переиспользуемый)
└── tasks.md             # Phase 2 output (/speckit.tasks command — НЕ создаётся /speckit.plan)
```

### Source Code (repository root)

Фича затрагивает 3 места:

```text
karaoke-public/
└── src/
    ├── views/
    │   └── HomeView.vue                  # правка: добавить <LatestNewsSection/> между блоком статистики и навигационных карточек
    └── components/
        └── LatestNewsSection.vue         # НОВЫЙ: Vue 3 SFC (template + script + scoped style), вызов GET /api/public/news?page=0&size=5

karaoke-web/
└── src/
    └── main/
        ├── kotlin/com/svoemesto/karaokeweb/controllers/
        │   └── MainController.kt         # правка: добавить атрибут model.addAttribute("latestNews", ...) в main()
        └── resources/templates/
            └── main.html                 # правка: добавить таблицу из 5 строк в <table id="areaBody">

docs/features/
├── README.md                             # правка: добавить запись о homepage-latest-news.md (11 → 12 фич)
└── homepage-latest-news.md               # НОВЫЙ: per-feature документ по контракту (FR-006/FR-009 конституции)
```

**Бэкенд НЕ правится** (никакого нового кода). Переиспользуем существующий `GET /api/public/news?page=0&size=5` (`karaoke-web/.../controllers/PublicNewsController.kt:30`).

**`tbl_news` НЕ правится** (никаких новых колонок/индексов/миграций).

**SyncRegistry НЕ правится** (sync-цель `news` остаётся как есть).

**Structure Decision**: фича не вводит новых проектов/модулей. Используем существующую структуру `karaoke-public` (Vue 3 SPA) и `karaoke-web` (Spring Boot + Thymeleaf). Никаких новых Gradle-модулей, никакого нового Docker-образа, никаких новых миграций БД.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Нарушений нет — таблица пустая (Constitution Check все 8 принципов прошли).

---

## Phase 0: Outline & Research

См. [research.md](./research.md) — все NEEDS CLARIFICATION уже разрешены на этапе спеки, открытых вопросов нет.

Ключевые решения (для фиксации в research.md):

1. **Контракт эндпоинта**: переиспользовать существующий `GET /api/public/news?page=0&size=5` (выбран по итогам Clarifications).
2. **Логика фильтрации**: на бэкенде уже есть `News.loadPublished` (publish_at <= now(), сортировка publish_at DESC, id DESC) — никаких изменений.
3. **Фильтр строк без `link`/`title`**: применяется на фронте (Vue computed) и в Thymeleaf (th:if="${!#strings.isEmpty(n.link) && !#strings.isEmpty(n.title)}").
4. **Тихaя деградация**: SPA блок не рендерится, если запрос упал/таймаут/невалидный JSON (через `try/catch` + `v-if="items && items.length"`).
5. **Размещение**: между блоком статистики и навигационных карточек (см. `HomeView.vue:43-97`).
6. **Формат даты**: `Intl.DateTimeFormat('ru-RU', {day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'})` или `dd.MM.yyyy HH:mm` — нативный JS, без новых зависимостей.

## Phase 1: Design & Contracts

См. [data-model.md](./data-model.md), [contracts/public-news-api.md](./contracts/public-news-api.md), [quickstart.md](./quickstart.md).

Контракт бэкенда не меняется (переиспользуем существующий), поэтому новых API-файлов не создаётся — `contracts/` содержит ссылку на уже существующий `PublicNewsController.list()` с фиксацией «как используется блоком на главной» (параметры запроса, формат ответа, поведение при пустом/ошибочном ответе).

---

## Re-evaluate Constitution Check post-design

После Phase 1 дизайн не вводит новых зависимостей/таблиц/sync-целей/проектов — все 8 принципов остаются в `pass`. Никаких дополнительных обоснований не требуется.
