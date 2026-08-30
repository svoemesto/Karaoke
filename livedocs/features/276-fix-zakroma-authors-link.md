---
status: Active
slug: 276-fix-zakroma-authors-link
related:
  - ../../specs/276-fix-zakroma-authors-link/spec.md
  - ../../specs/276-fix-zakroma-authors-link/plan.md
  - 250-unify-site-header
  - 258-zakroma-routing-refactor
---

# 276 — Исправление навигации «← К списку авторов» (LiveDoc)

> Drill-down — [specs/276-fix-zakroma-authors-link/spec.md](../../specs/276-fix-zakroma-authors-link/spec.md),
> [plan.md](../../specs/276-fix-zakroma-authors-link/plan.md).

## Что делает

Исправляет баг: на странице песен автора (`/zakroma/:authorId`) клик на ссылку «← К списку авторов» в шапке (`AppHeader.back`) обновляет URL в адресной строке, но содержимое страницы остаётся прежним — таблица песен автора не сменяется сеткой тайлов.

**Корень**: `ZakromaView.vue` смонтирован на трёх маршрутах одновременно (`/zakroma`, `/zakroma/:authorId`, `/zakroma/special-bucket`, см. [`router/index.js:40-44`](../../karaoke-public/src/router/index.js)). Vue-router переиспользует экземпляр компонента при переходе между ними (все 3 маршрута имеют `component: ZakromaView`), поэтому `data()` не вызывается заново и поле `authorChosen = true` остаётся прежним.

**Решение** (см. `research.md` R-1):
1. **`ZakromaView.vue:544-575`** — добавить watcher `'$route.path'(newPath, oldPath)` в блок `watch:`. Условие `newPath === '/zakroma' && oldPath !== '/zakroma'` вызывает `this.backToAuthors()` для сброса локального state. Заменяет ошибочный комментарий спеки 258 о «vue-router пересоздаёт инстанс компонента при смене path» (это не так).
2. **`AppHeader.vue:6`** — добавить атрибут `replace` на `<RouterLink>` для back-link. Превращает клик в `vue-router.replace` (а не `push`), чтобы `/zakroma/:authorId` не дублировался в истории браузера. Логотип `<RouterLink to="/">` остаётся `push` — это «новая страница», back-link семантически означает «возврат».

## User Stories (краткий список)

- **US1** (P1): Возврат на сетку тайлов с шапки страницы песен автора.
- **US2** (P2): Корректная работа системной «Назад» в браузере.
- **US3** (P3): Сброс локального состояния при возврате на сетку.

## Functional Requirements (указатель)

- **FR-001..FR-006** — спека [spec.md](../../specs/276-fix-zakroma-authors-link/spec.md#requirements).
- Ключевые: FR-003 (сброс 5 полей + отмена стрима), FR-004 (явно требует `vue-router.replace`).

## Acceptance Criteria

- [ ] **AC1**: Клик на «← К списку авторов» с `/zakroma/50` → URL `/zakroma`, на экране сетка тайлов (US1 Scenario 1).
- [ ] **AC2**: Клик на «← К списку авторов» с `/zakroma/special-bucket` → URL `/zakroma`, на экране сетка тайлов со спец-плашкой в конце (US1 Scenario 2).
- [ ] **AC3**: После возврата на `/zakroma` повторный клик на тайл автора открывает его песни (US1 Scenario 3 — watcher не сбрасывает state при переходе `/zakroma` → `/zakroma/<id>`).
- [ ] **AC4**: Системная «Назад» после `/zakroma/50` → `/zakroma` (через шапку) возвращает не на `/zakroma/50`, а на ту внешнюю страницу, откуда посетитель изначально пришёл (FR-004 + US2 Scenario 1).
- [ ] **AC5**: Поле быстрого фильтра по песням сбрасывается при возврате на `/zakroma` (US3 Scenario 1).
- [ ] **AC6**: Персистентные пользовательские настройки (`albumDisplayMode`, `hiddenAlbumTypes`, тема) сохраняются при возврате (US3 Scenario 2, FR-005).
- [ ] **AC7**: Активный стрим загрузки песен отменяется при возврате на `/zakroma` — нет «зависших» прогресс-баров (edge case Q6 в quickstart).
- [ ] **AC8**: 0 НОВЫХ нарушений ESLint baseline (`./tools/check-eslint-baseline.sh karaoke-public`).
- [ ] **AC9**: `npm run build && npm run lint && npm run format:check` — PASS (karaoke-public).
- [ ] **AC10**: Docker-сборка public проходит: `cd deploy && bash do.sh build_public` (Pass 245, multi-stage Dockerfile копирует только `karaoke-public/`).

## Связанные LiveDocs

- [250-unify-site-header](250-unify-site-header.md) — компонент `<AppHeader>`, в который добавлен атрибут `replace` для back-link. Логотип `<RouterLink to="/">` остаётся без `replace` (это «новая страница», не возврат).
- [258-zakroma-routing-refactor](258-zakroma-routing-refactor.md) — ввёл path-based routing (`/zakroma` / `/zakroma/:authorId` / `/zakroma/special-bucket`). Ошибочно предполагал, что vue-router пересоздаёт компонент при смене path между ними; спека 276 исправляет это предположение.

## Регрессии (что проверять при будущих правках маршрутизации)

- Если кто-то добавит НОВЫЙ маршрут, использующий `component: ZakromaView`, watcher на `$route.path` нужно пересмотреть: новая логика выбора/сброса автора может потребовать дополнительных условий.
- Если кто-то вынесет `ZakromaView` на 3 раздельных sub-view (тайлы / песни автора / спец-корзина) — watcher можно убрать, т.к. переиспользования инстанса не будет. Это ОТЛОЖЕННЫЙ рефакторинг (см. spec.md Out of Scope).
- Если кто-то изменит контракт `AppHeader.back` (например, добавит `replace: false` flag) — атрибут `replace` на `<RouterLink>` остаётся для back-link, см. [`contracts/zakroma-view-state.md`](../../specs/276-fix-zakroma-authors-link/contracts/zakroma-view-state.md).
