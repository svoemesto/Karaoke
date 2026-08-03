# Implementation Plan: Шаблоны автоматических новостей сайта

**Branch**: `128-news-publish-templates` | **Date**: 2026-08-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/128-news-publish-templates/spec.md`

## Summary

Третья вкладка/платформа «Новости сайта» в существующем компоненте
`webvue3` `PublishTemplatesView.vue` (наравне с ВК/Telegram), позволяющая
администратору редактировать заголовок и тело автоматических новостей
двух категорий (`air`, `premium`) с поддержкой плейсхолдеров, превью и
сброса к дефолту. Шаблоны (4 строковых ключа) хранятся в
`tbl_public_settings` (Postgres — единый источник истины, доступный и с
admin, и с прода), читаются прямым JDBC-запросом в момент создания
auto-новости (`SongReleaseAnnouncementService`), что заменяет текущие
хардкод-формулировки. Заводские дефолты в коде
(`NewsTemplateService.DEFAULT_*`) byte-идентичны текущим хардкод-строкам
(`albumYearSuffix`/`bodyDetails` хелперы вызываются как цельные составные
плейсхолдеры), обеспечивая прозрачный релиз без изменения вида ленты
новостей.

## Technical Context

**Language/Version**: Kotlin 1.x (JDK 17/22 JRE), Vue 3 + Vite (Node 22 LTS)

**Primary Dependencies**:
- Backend: Spring Boot 2.x/3.x, сырой JDBC (`KaraokeConnection`)
- Frontend: Vue 3, Vuex, Bootstrap-vue-next, существующий `PublishTemplatesView.vue`
- Reference pattern: `VkTemplateService.kt` (render, PLACEHOLDERS, defaults),
  `TelegramTemplateService.kt`, `ApiController.kt` (`/vk/templates/*`,
  `/telegram/templates/*` endpoints)
- Storage access: `tbl_public_settings` через `PublicSettingsController`
  (admin, `/api/publicsettings/*`) и `PublicSettingsWebController` (prod),
  по образцу `News.isNewsAutoPublishKillSwitchActive`,
  `CaptchaConfigService`

**Storage**: PostgreSQL через сырой JDBC. Новых таблиц НЕ вводится —
используется существующая `tbl_public_settings` (key/value, читаемая и с
admin, и с прода). Схема `tbl_news` НЕ меняется. Миграции НЕ требуются
(ключи отсутствуют в БД до первого сохранения → рендер использует дефолт
из кода).

**Testing**: CI-тестов нет (интеграционные `@Disabled`). Проверка —
ручная (см. `quickstart.md`): правка шаблона → создание/моделирование
auto-новости → проверка `tbl_news`. Lint-гейты: ktlint, ESLint webvue3,
KDoc/JSDoc coverage, docs structure.

**Target Platform**: admin-машина (`karaoke-app`, `webvue3`) + прод-сервер
(`karaoke-web`, рендеринг шаблона в точках создания auto-новостей).

**Project Type**: web-service (backend Kotlin/Spring Boot + frontend Vue 3 SPA),
multi-module.

**Performance Goals**: Превью ≤ 3 сек (SC-003). Рендеринг шаблона в момент
создания auto-новости — незначительные накладные (один JDBC-запрос к
`tbl_public_settings` + строковая подстановка).

**Constraints**:
- Без перезапуска `karaoke-app`/`karaoke-web` (FR-008, SC-005) —
  чтение из БД при каждом применении, без статического кэша.
- Без зависимости от `KaraokeProperties`-файла (FR-016) — на проде его нет.
- Byte-идентичность дефолтов текущим хардкод-строкам (FR-010, SC-002).
- Без изменения схемы `tbl_news` (FR-006).
- Лимиты длины `tbl_news.title`/`body` — по образцу существующего
  `News.createNew` (Edge Cases).

**Scale/Scope**: 4 шаблонных ключа × 1 админ-машина + prod-рендеринг.
Лента новостей — существующий объём (сотни записей). UI — одна новая
вкладка в существующем компоненте.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Note |
|-----------|--------|------|
| **I. Self-contained автопайплайн** | ✅ PASS | Фича не касается горячего пути обработки медиа; хранилище `tbl_public_settings` — локальный Postgres, не внешний SaaS. |
| **II. Сырой JDBC + дифф по хэшам** | ✅ PASS | Доступ к `tbl_public_settings` — сырой JDBC (по образцу `News.isNewsAutoPublishKillSwitchActive`). `tbl_public_settings` НЕ участвует в LOCAL↔SERVER sync (это settings key/value, не сущность с `recordhash`), поэтому `associateBy`-инвариант неприменим. Сравнение/дифф не нужен — каждое чтение один ключ по `WHERE key = ?`. |
| **III. Двух-БД синхронизация** | ✅ PASS | `tbl_public_settings` НЕ участвует в LOCAL↔SERVER sync (это settings key/value, не `KaraokeDbTable`-сущность, нет `recordhash`). Расхождение значений — ручная ответственность администратора через `target=local|remote` параметр endpoint'а (по образцу `PublicSettingsController`, `PublicSettingsTable.vue`). Администратор редактирует 4 ключа на admin-машине с `target=remote` → пишется в prod-БД. Prod-рендеринг читает prod-БД. SyncRegistry НЕ требуется. См. `research.md` R1. |
| **IV. Async-очередь** | ✅ N/A | Фича не вводит длительных операций. Рендеринг шаблона — синхронная строковая подстановка. |
| **V. Двух-фронтенд: admin vs public** | ✅ PASS | UI шаблонов — в `webvue3` (admin SPA), НЕ в `karaoke-public`. Рендеринг — в `karaoke-web` (prod), НЕ в `karaoke-public`. Смешивание ответственностей не происходит. `<select>`-паттерн не нужен (шаблоны — textarea). |
| **VI. Code Standards** | ✅ PASS (pending impl) | KDoc на новые публичные классы (`NewsTemplateService`) с `@see docs/features/<slug>.md`. Per-feature документ — `docs/features/news-templates.md` (новый, 12-я подсистема). ESLint на Vue-правки `PublishTemplatesView.vue`. Baseline — не растёт. |
| **VII. Cross-Machine Setup** | ✅ PASS | Фича не вводит локальных AI-конфигов или machine-specific настроек. |
| **VIII. Секреты и git-гигиена** | ✅ PASS | Не трогает секрет-файлы. Шаблоны — публичный контент (текст новостей), не секреты. |

**Violations**: нет. Complexity Tracking не нужен.

**Caveat (исследовать в Phase 0)**: Принцип III (sync) — **RESOLVED** в
`research.md` R1. `tbl_public_settings` физически отдельная таблица на
LOCAL и на SERVER, расхождение ручное через `target=local|remote`
(существующий паттерн `PublicSettingsController`,
`PublicSettingsTable.vue`, `News.isNewsAutoPublishKillSwitchActive`).
SyncRegistry НЕ участвует. Принцип III не нарушен — таблица по дизайну
вне sync-инфраструктуры.

## Project Structure

### Documentation (this feature)

```text
specs/128-news-publish-templates/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── api.md           # REST endpoints contract
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── services/
│   ├── NewsTemplateService.kt          # NEW: render, PLACEHOLDERS, DEFAULT_*
│   └── SongReleaseAnnouncementService.kt # EDIT: хардкод → NewsTemplateService.render
├── controllers/
│   ├── ApiController.kt                 # EDIT: +/api/news/templates/* endpoints
│   └── PublicSettingsController.kt      # REF (maybe EDIT: validate newsTemplate* keys)
└── model/
    └── News.kt                          # REF (createAutoAnnouncement — без правок)

karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
├── controllers/
│   ├── PublicSettingsWebController.kt   # REF (prod-side public settings)
│   └── MainController.kt                # REF (doChangeRecords — premium trigger)
└── services/
    └── SongReleaseAnnouncementScheduler.kt # REF (air trigger on prod)

webvue3/src/
├── views/
│   └── PublishTemplatesView.vue         # EDIT: +вкладка «Новости сайта»
├── components/                          # REF: existing VK/Telegram tab components
└── store/modules/                      # REF: existing template store pattern
```

**Structure Decision**: Web application (backend + admin frontend).
Backend правки — в `karaoke-app` (новый `NewsTemplateService` + endpoints
в `ApiController` + рендеринг в `SongReleaseAnnouncementService`).
`karaoke-web` на проде использует те же классы из `karaoke-app` (тонкий
слой), поэтому `NewsTemplateService` живёт в `karaoke-app` и доступен на
проде через dependency. Frontend — одна новая вкладка в существующем
`PublishTemplatesView.vue` (наследует паттерн ВК/Telegram).

## Complexity Tracking

> Constitution Check violations — нет. Таблица не заполняется.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |