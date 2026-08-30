# Implementation Plan: 276-fix-zakroma-authors-link

**Branch**: `276-fix-zakroma-authors-link` | **Date**: 2026-08-30 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/276-fix-zakroma-authors-link/spec.md`

## Summary

Исправление бага: на странице песен автора (`/zakroma/:authorId`) клик на ссылку «← К списку авторов» в шапке (`AppHeader.back`) обновляет URL в адресной строке, но содержимое страницы остаётся прежним — список песен автора не сменяется сеткой тайлов. Корень — `ZakromaView.vue` смонтирован на трёх маршрутах (`/zakroma`, `/zakroma/:authorId`, `/zakroma/special-bucket`), vue-router переиспользует экземпляр компонента при переходе между ними, поэтому `data()` не вызывается заново и `authorChosen = true` сохраняется.

Технический подход (см. `research.md`): добавить watcher на `$route.path` в `ZakromaView.vue`, который при смене path сбрасывает локальное состояние (`selectedAuthorId`, `selectedAuthor`, `authorChosen`, `specialBucketShown`, `songFilter`) и (если активен стрим) инициирует AbortController-отмену. Метод `backToAuthors()` уже содержит нужный сброс + `$router.replace` — он будет вызван из watcher'а, что устранит дублирование.

## Technical Context

**Language/Version**: JavaScript (ES2022) + Vue 3 SFC `<script>` (Options API) — фронтенд публичного сайта `karaoke-public`. Никаких изменений Kotlin/бэкенда.

**Primary Dependencies**:
- Vue 3 (`vue@^3.x`), Vue Router 4 (`vue-router@^4.x`), Vuex 4 (`vuex@^4.x`).
- Все зависимости уже установлены в `karaoke-public/package.json`; новых npm-пакетов не требуется.

**Storage**: N/A — изменение только в клиентском SPA-стейте и vue-router; никаких изменений БД, MinIO, кэшей.

**Testing**: ручное e2e-тестирование в браузере (проект по конституции не имеет CI-тестов; `karaoke-app/src/test` — `@Disabled`, см. AGENTS.md). Тест-сценарии — в `quickstart.md`. Линтеры: `cd karaoke-public && npm run lint` + `tools/check-eslint-baseline.sh karaoke-public` — никаких новых нарушений baseline.

**Target Platform**: браузеры пользователей публичного сайта (десктоп + мобильные, ≥ ~2-летние версии). Сборка фронта — Vite, прода — через `deploy/do.sh build_public` (multi-stage Docker, кросс-импорты из `webvue3` проверяются на Docker-сборке, не на Vite-build, см. AGENTS.md).

**Project Type**: web SPA (однофронтовое изменение внутри существующего модуля `karaoke-public`).

**Performance Goals**:
- SC-001: переход `/zakroma/50` → `/zakroma` через шапку за ≤ 500 мс (воспринимаемая задержка).
- Никаких новых HTTP-запросов: watcher переиспользует уже закэшированные в Vuex `authorTiles` + `specialBucket`.
- Если активен стрим — `AbortController.abort()` синхронно прерывает fetch.

**Constraints**:
- **Constitution V**: всё изменение — в `karaoke-public` (публичный SPA). `webvue3` (админка) НЕ трогаем.
- **Constitution VI (FR-007)**: новый код должен проходить ESLint без новых нарушений baseline.
- **Constitution VI (FR-009)**: в том же PR обновить `livedocs/features/276-fix-zakroma-authors-link.md` (per-feature документ).
- **AGENTS.md (Pass 245)**: после правки обязательно Docker-сборка образа `webvue3` И `public` (multi-stage Dockerfile копирует только свой каталог, кросс-импорты могут сломать сборку). Хотя 276 не вводит кросс-импортов, Docker-сборка обязательна как страховка.
- Не использовать `node:latest`, `nginx:alpine`, JDK вместо JRE — в этой фиче не применимо (никаких Docker-изменений).
- A-3 (spec.md): НЕ возвращаться к query-based routing (`/zakroma?author=X`) — это регресс.

**Scale/Scope**: одна view-страница (`ZakromaView.vue`), один компонент (`AppHeader.vue` используется без изменений), одна store-логика (`zakroma.js`). Изменение локально — никаких внешних API-вызовов, никаких новых store-модулей.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Applicable? | Status |
|-----------|------------|--------|
| **I. Self-contained автопайплайн** | Нет (нет ML/ffmpeg пайплайна в этой фиче) | N/A |
| **II. Сырой JDBC + diff по хэшам** | Нет (нет изменений БД) | N/A |
| **III. Двух-БД синхронизация** | Нет | N/A |
| **IV. Async-очередь задач** | Нет (AbortController во фронте, не KaraokeProcess) | N/A |
| **V. Двух-фронтенд** | **Да** — изменение только в `karaoke-public`. `webvue3` НЕ затронут. Смешения ответственностей нет. | ✓ PASS |
| **VI. Code Standards (FR-006/007/009)** | **Да** — нужен KDoc для нового watcher'а (Vue-компонент), `npm run lint` без новых нарушений, обновление `livedocs/features/276-…md`. | ✓ PASS (с условиями — см. Plan) |
| **VII. Cross-Machine Setup** | Нет (не добавляем конфиги, не трогаем `.gitattributes`) | N/A |
| **VIII. Секреты и git-гигиена** | Нет (никаких секрет-файлов) | N/A |

**Re-check после Phase 1**: см. секцию «Constitution Re-Check» ниже.

## Project Structure

### Documentation (this feature)

```text
specs/276-fix-zakroma-authors-link/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── zakroma-view-state.md
├── checklists/
│   └── requirements.md
└── spec.md
```

### Source Code (repository root)

Изменения только в одном файле:

```text
karaoke-public/
├── src/
│   ├── views/
│   │   └── ZakromaView.vue          # правка: добавить watcher $route.path → вызвать backToAuthors() (или эквивалентный reset)
│   ├── store/
│   │   └── modules/
│   │       └── zakroma.js           # возможно: expose `cancelStream()` action, если watcher вызывает его явно (см. research.md)
│   ├── router/
│   │   └── index.js                 # НЕ меняется (route definitions уже корректные, см. спек 258)
│   └── components/
│       └── AppHeader.vue            # НЕ меняется (prop `back` уже принимает `{ to: '/zakroma' }`)
└── package.json                     # НЕ меняется (никаких новых зависимостей)
```

```text
livedocs/
└── features/
    └── 276-fix-zakroma-authors-link.md   # новый файл (FR-009, per-feature документ)
```

**Structure Decision**: это `Option 2: Web application` в части «frontend-only patch» — изменение локализовано в публичном SPA. Никакого нового backend-кода, никаких новых тестовых модулей (тесты — ручные, в `quickstart.md`).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет нарушений) | — | — |

План НЕ нарушает ни одного принципа Конституции. Никаких justified violations не требуется.

## Phase 0: Research

См. детали в [research.md](research.md). Краткое резюме решений:

- **R-1 (выбор механизма сброса состояния)**: `watcher` на `$route.path` внутри `ZakromaView.vue` + вызов существующего `backToAuthors()`. Отвергнуты: `:key="$route.path"` на `<router-view>` (слишком грубо — пересоздаёт ВСЕ view, ломает state других страниц), `beforeRouteUpdate` в компоненте (хуже читается; смешивает navigation guard с reset).
- **R-2 (отмена активного стрима)**: переиспользовать существующий force-refresh через `loadZakromaStream({ author: this.selectedAuthor, expectedCount: undefined })` — store-action сам создаст новый composable с `controller.abort()` на прошлом (см. `ZakromaView.vue:744-767 cancelZakromaStream`). Дополнительной экспозиции `cancelStream()` в store не требуется.
- **R-3 (защита от зацикливания watcher'а)**: watcher сравнивает `$route.path` с предыдущим значением и срабатывает только при реальной смене (vue watch по умолчанию так и делает — срабатывает только на изменение). Рекурсии не будет.
- **R-4 (per-feature документ FR-009)**: создать `livedocs/features/276-fix-zakroma-authors-link.md` со ссылками на `spec.md`, `plan.md`, описание бага и фикса. Тот же шаблон, что `livedocs/features/250-unify-site-header.md`.

## Phase 1: Design & Contracts

См. детали в [data-model.md](data-model.md), [contracts/zakroma-view-state.md](contracts/zakroma-view-state.md), [quickstart.md](quickstart.md).

Краткое резюме:

- **DM-1**: `ZakromaView.data()` поля, которые сбрасываются при возврате на `/zakroma`: `selectedAuthorId`, `selectedAuthor`, `authorChosen`, `specialBucketShown`, `songFilter`. Поля НЕ сбрасываемые (постоянные настройки посетителя): `albumDisplayMode` (из localStorage), `hiddenAlbumTypes` (из localStorage).
- **C-1 (UI contract)**: `AppHeader.back = { to: '/zakroma', label: '← К списку авторов' }` — без изменений. `RouterLink` сам обрабатывает vue-router replace (см. `AppHeader.vue:118-130` — формат `{ to }`).
- **QS-1 (validation)**: 5 ручных e2e-сценариев, покрывающих US1 (3 сценария), US2 (1 сценарий), US3 (1 сценарий) + edge case «отмена активного стрима».

## Constitution Re-Check (после Phase 1)

| Principle | Status |
|-----------|--------|
| **V. Двух-фронтенд** | ✓ PASS — изменения только в `karaoke-public`. `webvue3` не затронут. |
| **VI. Code Standards (FR-006/007/009)** | ✓ PASS — KDoc для нового watcher'а запланирован в `tasks.md`; ESLint-чек включён в Definition of Done; per-feature документ `livedocs/features/276-fix-zakroma-authors-link.md` запланирован. |
| **Constitution compliance** | ✓ PASS — никаких изменений бэкенда, БД, секретов, Docker-конфигов. |
| **A-3 (spec.md)**: не возвращаться к query-based routing | ✓ PASS — фикс через watcher, query-based остаётся в `router/index.js:149-163` только как legacy-redirect. |

Все gates пройдены. План готов к `/speckit.tasks`.
