# Contracts: 254 — header-back-link «К списку авторов»

**Branch**: `254-fix-zakroma-header-back-link` | **Date**: 2026-08-27

## Резюме

Изменение **не** вводит и **не** модифицирует **никаких внешних контрактов**:

- Не добавляет REST-endpoint.
- Не меняет формат стрима `meta`/`song`/`done` для `/api/public/zakroma`.
- Не меняет DTO `ZakromaPublicDto` / `AlbumTypeSummaryDto` / `Author*`.
- Не меняет схему БД, sync-флаги, миграции.
- Не меняет Vuex-стейт, composables.
- **Не** меняет публичный API Vue-компонента `AppHeader.vue`:
  - Props `back` (type Object, default null) уже поддерживает передачу `null`;
  - Computed `backRouteTo()` уже корректно обрабатывает `back.to = '/zakroma'` (path-only, без query);
  - Template `v-if="back"` (AppHeader.vue:6) уже скрывает ссылку, если `back` falsy.

AppHeader API — **стабильный контракт**, фикс спеки 254 использует его «as is».

## Что НЕ является внешним контрактом

- **Computed `zakromaHeaderBack` в `ZakromaView.vue`** — приватный computed конкретного view; другие view не зависят от него.
- **Удаление in-page `<button class="km-back-btn">`** — приватная разметка view; тестов / парсеров на ней нет.
- **Удаление scoped CSS `.km-back-btn`** — приватные стили; scoped-CSS Vue не достигают других view (Vue scoped); нет cross-file зависимостей.

## Контракт с логическим контрактом спеки 250 (AppHeader)

| Контракт спеки 250 | Сохраняется? |
|--------------------|--------------|
| AppHeader: `<RouterLink v-if="back" class="km-back">…</RouterLink>` слева | **Да** — фикс передаёт `null` для скрытия, не изменяет логику |
| `back` prop: `Object, default: null` | **Да** — используем именно эту сигнатуру |
| `backRouteTo` computed для `query`/`to` обработки | **Да** — не задействован напрямую, но работает |

## Контракт с логическим контрактом спеки 008 (special bucket)

| Контракт спеки 008 | Сохраняется? |
|--------------------|--------------|
| `?specialBucket=true` открывает «табличное отображение» | **Да** |
| Возврат к тайлам авторов — через `backToAuthors()` / router-replace | **Да** — теперь ещё и через header-back-link |
| `isSpecialBucketSelected` флаг управляет UI | **Да** — используется в новом `zakromaHeaderBack` |

## Контракт с логическим контрактом спек 252 + 253 (sticky)

| Контракт спек 252/253 | Сохраняется? |
|------------------------|--------------|
| `.km-author-header-sticky` sticky на `top: 53px/49px/46px` | **Да** — никаких изменений в этом правиле |
| AppHeader sticky на `top: 0` (спека 250) | **Да** — header всегда на верху, back-link внутри него |

## Поэтому

`/contracts/` создан, но **пуст** — нет новых файлов с контрактами. AppHeader API **уже поддерживает** всё необходимое для этой спеки (`null` для скрытия, `to` без `query` для перехода на `/zakroma` без query). Любые будущие фичи, требующие динамического back-link, могут использовать тот же паттерн без изменения AppHeader.
