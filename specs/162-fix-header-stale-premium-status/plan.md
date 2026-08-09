# Implementation Plan: Устаревший премиум-статус в шапке сайта после окончания подписки

**Branch**: `162-fix-header-stale-premium-status` | **Date**: 2026-08-09 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/162-fix-header-stale-premium-status/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Шапка публичного сайта (`AuthStatusWidget.vue`) читает премиум-статус (`user.effectivePremium`) из клиентского composable `useAuth.js`, где объект пользователя закэширован в `localStorage` **на момент логина** и обновляется только когда одна из трёх конкретных страниц (`AccountView`, `EditorWorkView`, `EditorTasksView`) явно вызывает `fetchMe()`. Само приложение (`App.vue`) при старте `fetchMe()` не вызывает. Из-за этого после окончания подписки шапка продолжает показывать премиум-значок 🪙, пока пользователь случайно не попадёт на одну из этих трёх страниц или не перелогинится.

Технический подход: без изменений на бэкенде (существующий эндпоинт `GET /api/public/auth/me` уже отдаёт живой, не кэшируемый на сервере статус — см. `SiteUserResolver`/`PublicAuthController.me`) добавить в `useAuth.js` периодическое фоновое обновление статуса (`fetchMe()` по таймеру, пока пользователь залогинен) плюс немедленный вызов при первом монтировании виджета шапки — это закрывает и "долгую сессию без перезагрузки" (FR-002), и заодно делает поведение при обычном заходе на страницу (FR-003) явным, а не случайным следствием того, какая страница открыта.

## Technical Context

**Language/Version**: JavaScript (ES modules), Vue 3 Composition API — существующий стек `karaoke-public`, без нового языка/рантайма.

**Primary Dependencies**: Vue 3, Vue Router (уже используются); новых зависимостей не требуется — фикс переиспользует существующий `services/authApi.js` (`authGet`).

**Storage**: Клиентский `localStorage` (`km_auth_token`, `km_auth_user`) — уже существует, схема ключей не меняется. Изменений в PostgreSQL/MinIO нет.

**Testing**: В проекте нет CI-тестов для `karaoke-public` (см. Конституцию, раздел «Рабочий процесс», «Тесты: в CI нет»); проверка — ручная, через `npm run dev` / `do.sh build_start_public` в браузере (сценарии из `quickstart.md`).

**Target Platform**: Браузер, публичный SPA `karaoke-public` (десктоп/мобильный веб).

**Project Type**: Web-приложение — правки только во фронтенд-модуле `karaoke-public`; backend (`karaoke-web`) не затрагивается, т.к. используемый эндпоинт `GET /api/public/auth/me` уже существует и уже живой (не кэшируется на сервере).

**Performance Goals**: Незначительная фоновая нагрузка — один лёгкий `GET /api/public/auth/me` раз в несколько минут на залогиненную вкладку; не должен ощущаться пользователем и не должен заметно нагружать `WORKING_DATABASE`.

**Constraints**: Никаких новых backend-эндпоинтов; не увеличивать заметно задержку логина/логаута; фоновые сетевые сбои не должны ни ломать UI, ни искусственно показывать премиум, которого нет (FR-005); кросс-вкладочная мгновенная синхронизация не требуется (см. Assumptions в spec.md).

**Scale/Scope**: Точечное изменение одного composable (`useAuth.js`) и, при необходимости, точки монтирования `AuthStatusWidget.vue`; исходных сущностей/схемы БД не добавляется.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Применимость | Статус |
|---|---|---|
| I. Self-contained автопайплайн | N/A — не затрагивает ffmpeg/MLT/Demucs/Sheetsage | ✅ |
| II. Сырой JDBC + дифф по хэшам | N/A — изменений в доступе к БД нет, backend не трогаем | ✅ |
| III. Двух-БД синхронизация (SyncRegistry) | N/A — новых/изменённых синхронизируемых сущностей нет | ✅ |
| IV. Async-очередь задач | N/A — не связано с `KaraokeProcess*`/долгими задачами | ✅ |
| V. Двух-фронтенд: admin/public — разные приложения | Применимо — правка только в `karaoke-public`, `webvue3` не трогаем | ✅ |
| VI. Code Standards (KDoc/JSDoc, ktlint/ESLint, per-feature doc) | Применимо — изменённые/новые функции в `useAuth.js` должны получить JSDoc с `@see docs/features/<slug>.md`; после реализации обновить/создать per-feature документ (FR-009) | ⚠️ обязательно к выполнению в implementation-фазе, не блокер плана |
| VII. Cross-Machine Setup | N/A — не про AI-конфиги/line endings | ✅ |
| VIII. Секреты и git-гигиена | N/A — секреты не затрагиваются | ✅ |

Нарушений нет — Complexity Tracking не заполняется.

## Project Structure

### Documentation (this feature)

```text
specs/162-fix-header-stale-premium-status/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

Артефакт `contracts/` не создаётся: фича не добавляет и не меняет ни один backend-эндпоинт —
переиспользуется уже существующий `GET /api/public/auth/me` (см. research.md, Decision 1).

### Source Code (repository root)

```text
karaoke-public/                        # публичный SPA (единственный затронутый модуль)
└── src/
    ├── composables/
    │   └── useAuth.js                 # источник бага: module-level кэш user, ручной fetchMe();
    │                                   # сюда добавляется периодическое фоновое обновление
    ├── components/
    │   └── AuthStatusWidget.vue       # шапка сайта — читает user.effectivePremium, не меняется
    │                                   # по данным, только точка первого запуска обновления
    ├── services/
    │   └── authApi.js                 # authGet() — уже существует, переиспользуется как есть
    └── views/
        ├── HomeView.vue               # точки монтирования AuthStatusWidget (уже существуют)
        ├── SearchView.vue
        ├── ZakromaView.vue
        └── SongView.vue

karaoke-web/                           # backend — НЕ затрагивается
└── src/main/kotlin/.../controllers/PublicAuthController.kt  # GET /api/public/auth/me — читается
                                                               # только как reference-контракт,
                                                               # изменений не требует
```

**Structure Decision**: Изменения полностью локализованы в `karaoke-public/src` (публичный SPA,
Принцип V Конституции — не смешивать с `webvue3`). Backend (`karaoke-web`) не меняется: эндпоинт
`GET /api/public/auth/me` уже отдаёт живой статус на каждый вызов (см. research.md). Новых
директорий/модулей не создаётся — правка укладывается в существующий composable-паттерн проекта
(`useAuth.js` уже является module-level singleton).

## Complexity Tracking

*Пусто — нарушений Constitution Check нет (см. таблицу выше), обоснование не требуется.*
