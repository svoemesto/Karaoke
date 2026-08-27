# Data Model: 252 — Закрома: корректное скрытие блока типов альбомов

**Branch**: `252-fix-author-album-types-hide` | **Date**: 2026-08-27

## Резюме

Багфикс является **чисто клиентским CSS/HTML-изменением** в одном Vue SFC.
Никакие сущности предметной области, DTO или поля моделей не добавляются
и не изменяются. SQL-миграции не требуются. Стейт Vuex не затрагивается.

Поэтому полноценный «data model» (entities / fields / relationships /
validation / transitions) для этой спеки **не применим**.

## Что делается

| Артефакт | Было | Стало |
|----------|------|-------|
| `karaoke-public/src/views/ZakromaView.vue` template | `.km-filter-bar` и `.km-album-controls-bar` — два независимых DOM-узла | То же; опционально — обёртка `<div class="km-author-header-sticky">…</div>` (FR-004) |
| `ZakromaView.vue` scoped CSS | `.km-filter-bar { position: sticky; top: 53px; z-index: 90; }` + `.km-album-controls-bar { position: sticky; top: 53px; z-index: 89; }` | Вариант A (FR-002): `.km-album-controls-bar { top: calc(53px + var(--km-filter-bar-height, 50px)); z-index: 89; }`<br>Вариант B (FR-004): новый `.km-author-header-sticky { position: sticky; top: 53px; z-index: 90; }`, оба внутренних блока теряют `position: sticky` |

## Что НЕ меняется

| Слой | Файл / артефакт | Почему не трогаем |
|------|------------------|--------------------|
| Backend Kotlin | `karaoke-web/.../MainController.kt:zakroma` (спека 008, 012, 030) | DTO `ZakromaPublicDto` уже отдаёт `albumTypeCounts` (FR-025) — фронт его правильно использует |
| Backend DTO | `karaoke-web/.../dto/ZakromaPublicDto.kt` (AlbumTypeSummaryDto) | Без изменений |
| Backend stream | `karaoke-web/.../PublicApiController.kt` (`/api/public/zakroma`) | Контракт стрима `meta`/`song`/`done` без изменений |
| Vuex store | `karaoke-public/src/store/modules/zakroma.js` | Без изменений |
| Composables | `useZakromaStreamProgress.js`, `useAuth`, `useCart`, `usePlaylistMembership`, `useSongSubscriptions` | Не задействованы |
| Соседние view | `AppHeader.vue`, `AuthorTiles.vue`, `SongView.vue`, `AuthorPlaylistView.vue` | Без изменений (AppHeader высоту не трогаем — это отдельный рефакторинг, см. research.md D2) |

## Валидация (заменяет «validation rules»)

Так как нет data-entities, валидация — это **визуальная и DevTools-проверка**
CSS-разметки. Конкретные приёмочные сценарии — в [quickstart.md](quickstart.md),
общие acceptance criteria — в [spec.md § Success Criteria](spec.md#success-criteria-mandatory).

## Стейт-машина

Не применимо — ни process-states, ни новая бизнес-логика не вводятся.
Sticky-поведение — чистый CSS без `v-show`/`v-if`-переключений.
