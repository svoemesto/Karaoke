# Data Model: 253 — sticky-блок приклеивается к AppHeader на узких экранах

**Branch**: `253-fix-header-sticky-offset-responsive` | **Date**: 2026-08-27

## Резюме

Багфикс является **чисто клиентским CSS-изменением**: добавление глобальной
CSS-переменной `:root --km-header-height` в `karaoke-public/src/style.css`
и переключение `.km-author-header-sticky { top: 53px }` на `top: var(--km-header-height, 53px)`
в `ZakromaView.vue` (scoped CSS).

Никакие сущности предметной области, DTO, поля моделей, SQL-миграции,
Vuex-модули или composables не добавляются / не изменяются.

## Что делается

| Артефакт | Было | Стало |
|----------|------|-------|
| `karaoke-public/src/style.css` (глобальный) | (нет переменной высоты шапки) | `:root { --km-header-height: 53px; }` + `@media (max-width: 700px) { --km-header-height: 49px; }` + `@media (max-width: 500px) { --km-header-height: 46px; }` |
| `karaoke-public/src/views/ZakromaView.vue` (scoped CSS, правило `.km-author-header-sticky`) | `top: 53px;` | `top: var(--km-header-height, 53px);` (fallback сохранён) |

## Что НЕ меняется

| Слой | Файл / артефакт | Почему не трогаем |
|------|------------------|--------------------|
| AppHeader.vue | `karaoke-public/src/components/AppHeader.vue` | Scoped CSS не достигает других view (Vue scoped). Владелец своей высоты — AppHeader. Синхронизация через `:root`-переменную + комментарий в `style.css`. См. research.md § D3-D4. |
| Backend Kotlin | `karaoke-app/`, `karaoke-web/` | CSS-only, backend не задействован |
| Backend DTO | `ZakromaPublicDto.kt`, `AlbumTypeSummaryDto.kt` | Никаких изменений контракта |
| Соседние view | `SearchView.vue`, `AccountView.vue`, `AuthorPlaylistView.vue`, `SongView.vue` | Каждая view использует свой sticky-блок (если есть); они у себя или уже правильно настроены, или это отдельная фича |
| Vuex store | `karaoke-public/src/store/modules/zakroma.js` | Без изменений |
| Composables | `useZakromaStreamProgress.js`, `useAuth`, `useEngagementTracking` | Не задействованы |
| Bootstrap | `*.css` overrides | Не задействованы |

## Валидация (заменяет «validation rules»)

Визуальная и DevTools-проверка: `gap = stickyWrapper.top - header.bottom` ∈ `[-1, 1]` px на viewport 1280 / 700 / 500 / 375. Конкретные приёмочные сценарии — в [quickstart.md](quickstart.md), общие acceptance criteria — в [spec.md § Success Criteria](spec.md#success-criteria-mandatory).

## Стейт-машина

Не применимо — ни process-states, ни новая бизнес-логика, ни Vuex-переходы. Глобальная CSS-переменная — declarative, нативный CSS-engine.

## Открытые вопросы по data model

Нет.
