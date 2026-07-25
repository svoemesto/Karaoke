# Implementation Plan: Страница «О проекте» (QW-9)

**Branch**: `003-about-page` | **Date**: 2026-07-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-about-page/spec.md`

**Note**: этот план написан **постфактум**. Основная часть фичи (маршрут `/about`,
`AboutView.vue`, ссылки в главном меню и футере, трекинг вовлечённости, топ-15
авторов, статистика, CTA) уже реализована и смёржена в master коммитом
`4240b98 feat(public+strategy): страница «О проекте» (QW-9) + стратегия роста`
в обход формального speckit-пайплайна (аналогично тому, как было с
`001-code-standards-docs`). Задача этого плана — задокументировать уже
выбранную архитектуру и явно зафиксировать оставшиеся, ещё не закрытые
требования спецификации, чтобы `/speckit-tasks` → `/speckit-implement` могли
их закрыть.

## Summary

Публичная страница `/about` в `karaoke-public` (SPA), рассказывающая анонимному
посетителю, что за проект и какой контент он найдёт (преимущественно русский
рок). Технически — чисто фронтенд-фича: переиспользует существующие REST API
(`/api/public/authors`, `/api/public/stats`) и существующие composables/сервисы
(`useEngagementTracking`, `useAuth`, `services/tracking.js`, `services/api.js`).
Новых таблиц БД и новых бэкенд-эндпоинтов не требуется.

**Уже реализовано** (US1/US2, FR-001, FR-003..FR-009, NFR-001..NFR-003 в основном):
hero-блок, описание «полная дискография — не сборник хитов», явный акцент
«преимущественно русский рок» с топ-15 авторов (hardcoded список с пометками)
и динамическим списком остальных (из `/api/public/authors`), блок статистики
(из `/api/public/stats`), CTA-блок («Послушать демо» / «Открыть каталог» /
«Зарегистрироваться» / «В личный кабинет»), ссылка в главном меню (`HomeView.vue`)
и в глобальном футере (`App.vue`), `useEngagementTracking('about')` для
scroll-вех и времени на странице.

**Ещё не реализовано** (реальный gap с FR-002, US3):
1. Блок «Площадки» (Sponsr, Dzen, VK, Max, Telegram) с иконками — в спеке
   заявлен явно (FR-002), в `AboutView.vue` отсутствует. Решение: переиспользовать
   готовый компонент `SocialLinks.vue` (уже используется в `HomeView.vue`,
   ничего изобретать не нужно).
2. Кнопка «Поделиться» (US3, P3) — меню с 5+ соцсетями (VK, Telegram, WhatsApp,
   Odnoklassniki, копирование ссылки). Готового компонента нет нигде в
   `karaoke-public` — новый маленький компонент `ShareButton.vue`.
3. `docs/strategy/growth.md` не актуализирован: QW-9 всё ещё висит в роадмапе как
   несделанная задача, а ссылка на спеку — `specs/001-about-page/spec.md`
   (несуществующий путь, фича на самом деле `003-about-page`).

## Technical Context

**Language/Version**: JavaScript (ES2020+), Vue 3.4+ (Options API — весь
существующий `AboutView.vue` в Options API, новые кусочки следуют тому же
стилю файла ради консистентности), Vite.

**Primary Dependencies**: Vue Router (`/about` route уже зарегистрирован),
Vuex (модуль `stats`, используется на `HomeView`; `AboutView` сейчас читает
статистику напрямую через `apiGet`, не через Vuex — несостыковка стиля,
не блокер, вне скоупа этого раунда), существующие:
`composables/useEngagementTracking.js`, `composables/useAuth.js`,
`services/api.js` (`apiGet`), `services/tracking.js` (`trackUi`,
`trackLinkToSocialNetwork`), `components/SocialLinks.vue`, `components/SvgIcon.vue`.

**Storage**: N/A — никаких новых таблиц. Переиспользует `tbl_authors` (через
`GET /api/public/authors`) и `StatBySong`-кеш (через `GET /api/public/stats`),
оба уже существуют в `karaoke-web`.

**Testing**: ручное тестирование в браузере (в `karaoke-public` автотестов нет,
см. constitution.md «Рабочий процесс: Тесты»). Сценарии — в `quickstart.md`.

**Target Platform**: браузер (мобильный + десктоп), karaoke-public SPA
(Docker/nginx, публичный прод).

**Project Type**: web — фронтенд-дополнение к существующему SPA
(`karaoke-public`). Backend не меняется.

**Performance Goals**: NFR-001 — загрузка < 2 сек на мобильном 3G. Уже
достижимо: страница не грузит ничего тяжелее двух лёгких GET-запросов
(`/api/public/authors`, `/api/public/stats`), оба закешированы на бэкенде.

**Constraints**:
- Не создаёт новых БД-таблиц/миграций (Assumptions в spec.md).
- Только CSS-переменные `--km-*` (FR-009) — уже соблюдено во всём
  существующем `AboutView.vue`.
- Иконки для «Поделиться» — в `SvgIcon.vue` нет WhatsApp/Odnoklassniki
  (только `sponsr/boosty/dzen/vk/tg/vkgroup/max/...`). Решение: emoji вместо
  новых SVG-ассетов для MVP (в самом `AboutView.vue` уже используются emoji
  в CTA-кнопках — 🎵📚🔖👤, стилистически консистентно).

**Scale/Scope**: 1 существующий файл на правку (`AboutView.vue`, +2 секции),
1 переиспользуемый компонент (`SocialLinks.vue`, без изменений), 1 новый
маленький компонент (`ShareButton.vue`), 1 документ на правку (`growth.md`).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Принцип | Соответствие | Обоснование |
|---|---------|--------------|-------------|
| I | Self-contained автопайплайн | N/A | Фича не трогает медиа-пайплайн (ffmpeg/MLT/Demucs). |
| II | Сырой JDBC + дифф по хэшам | N/A | Нет новых БД-сущностей, нет sync-диффов. |
| III | Двух-БД синхронизация | N/A | Не затрагивается — фронтенд-only. |
| IV | Async-очередь задач | N/A | Нет долгих операций/subprocess. |
| V | Двух-фронтенд: admin и public | ✅ PASS | Изменения только в `karaoke-public`; `webvue3` не затрагивается. |
| VI | Code Standards | ✅ PASS | Новый JS-компонент получит JSDoc (см. `CONTRIBUTING.md`); `npm run lint`/`prettier` пройдут в CI (`lint.yml`). |

**Итог**: без нарушений. Complexity Tracking не нужен.

## Project Structure

### Documentation (this feature)

```text
specs/003-about-page/
├── plan.md              # Этот файл (написан постфактум)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output (переиспользуемые сущности, новых нет)
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit.tasks — не создаётся этим планом)
```

Директория `contracts/` не создаётся: фича не вводит новых REST-эндпоинтов
или иных интерфейсов — целиком переиспользует `GET /api/public/authors` и
`GET /api/public/stats` (`karaoke-web/.../PublicApiController.kt`), которые
уже задокументированы как часть существующего API.

### Source Code (repository root)

```text
karaoke-public/
├── src/
│   ├── views/
│   │   └── AboutView.vue          # УЖЕ ЕСТЬ, правка: +секция «Площадки», +секция «Поделиться»
│   ├── components/
│   │   ├── SocialLinks.vue        # УЖЕ ЕСТЬ, переиспользуется без изменений
│   │   └── ShareButton.vue        # NEW: меню «Поделиться» (VK/TG/WhatsApp/OK/копировать ссылку)
│   └── router/
│       └── index.js               # УЖЕ ЕСТЬ, /about уже зарегистрирован — правок не требует
docs/
└── strategy/
    └── growth.md                   # правка: отметить QW-9 сделанным, исправить путь на specs/003-about-page/
```

**Structure Decision**: чистое фронтенд-дополнение внутри существующей
структуры `karaoke-public` (Vue 3 SPA). Новых директорий/модулей не вводится —
один новый компонент в уже существующей `src/components/`.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *(нет)* | — | — |

Constitution Check прошёл без нарушений.
