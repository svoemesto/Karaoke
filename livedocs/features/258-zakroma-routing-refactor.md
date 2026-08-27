---
status: Active
slug: 258-zakroma-routing-refactor
related:
  - ../architecture/L3-components.md
  - ../../specs/258-zakroma-routing-refactor/spec.md
  - ../../specs/258-zakroma-routing-refactor/plan.md
  - ../../specs/258-zakroma-routing-refactor/research.md
  - ../../specs/258-zakroma-routing-refactor/contracts/index.md
  - 250-unify-site-header
  - 254-fix-zakroma-header-back-link
  - 255-fix-zakroma-state-reset-on-back-nav
  - 008-special-orders
---

# 258 — Закрома: рефакторинг URL-routing + back-link из SongView (LiveDoc)

> Drill-down — [specs/258-zakroma-routing-refactor/spec.md](../../specs/258-zakroma-routing-refactor/spec.md),
> [plan.md](../../specs/258-zakroma-routing-refactor/plan.md),
> [research.md](../../specs/258-zakroma-routing-refactor/research.md).

## Что делает

Устраняет неоднозначность URL `/zakroma` (которая ранее могла означать «тайтлы авторов» ИЛИ «песни выбранного автора» ИЛИ «спец-корзина» в зависимости от query-параметров), разнеся состояние на **три раздельных route**:

| URL                              | Режим                              |
|----------------------------------|-------------------------------------|
| `/zakroma`                       | Тайтлы авторов (только)              |
| `/zakroma/:authorId(\\d+)`       | Песни конкретного автора (Long ID)   |
| `/zakroma/special-bucket`        | Спец-корзина «Отдельные песни разных авторов» |

Решает баг: при открытии песни из `/zakroma?author=X` клик «← Назад» в шапке `SongView` вёл на тайты, а не на список песен автора. Теперь back-link корректно возвращает на страницу песен автора по `authorId` из query.

## Что НЕ делает

- Не меняет публичный API бэкенда (`/api/public/zakroma`, `/api/public/zakroma/stream`) — они по-прежнему принимают `author` как имя (а не ID).
- Не удаляет watcher из спеки 255 как «лишний код» — он УДАЛЁН, потому что vue-router 4 сам пересоздаёт компонент при смене path. Это устраняет целый класс багов «state не сбрасывается при навигации».
- Не выносит спец-корзину в отдельный `SpecialBucketView.vue` — переиспользуется `ZakromaView.vue` через `data.specialBucketShown` (RT-5.A).

## Ключевые решения

| ID       | Решение                                                                    |
|----------|----------------------------------------------------------------------------|
| RT-1.A1  | Добавить `val id: Long` в `AuthorTilePublicDto` + helper `Author.loadIdsByNames` |
| RT-2.A1  | Удалить watcher `'$route.query.author'` (FR-A4 спеки 258)                  |
| RT-3.B   | Global `router.beforeEach` для legacy `/zakroma?author=X` и `/zakroma?specialBucket=true` |
| RT-5.A   | Переиспользовать `ZakromaView` для `/zakroma/special-bucket`               |
| RT-6.A   | Regex `(\\d+)` в path для `:authorId` → 404 на невалидных URL               |
| RT-8.A   | `data.songFilter` сбрасывается через пересоздание компонента, не через watcher |

## Связь с другими фичами

- **Спека 250 (`AppHeader`)** — без изменений. Передаваемый `back` теперь может быть как `{ to, label }`, так и `{ name, params, label }` (для named routes).
- **Спека 254** — header-back-link «← К списку авторов» остаётся, target — `/zakroma` (без query), как и было.
- **Спека 255** — watcher на `'$route.query.author'` УДАЛЁН. Root cause (URL-неоднозначность) устранена через path-segment.
- **Спека 008 (special-orders)** — спец-корзина переехала в собственный route. Логика загрузки данных (`loadSpecialBucket()`) не изменилась.

## Изменённые файлы

### Backend

- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/AuthorTilePublicDto.kt` — добавлено поле `id: Long` (FR-A1)
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt` — `authorsTiles` отдаёт `id` (FR-A1)
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt` — новый helper `loadIdsByNames` (RT-1.A1)

### Frontend

- `karaoke-public/src/router/index.js` — 2 новых route + global `beforeEach` для legacy URLs
- `karaoke-public/src/views/ZakromaView.vue` — `data()` через `params`, `mounted()` через ID→name, удалён watcher, RouterLink на `/song` содержит `authorId`
- `karaoke-public/src/views/SongView.vue` — computed `songHeaderBack` строит back-link по `?authorId`

## Как проверить

См. `specs/258-zakroma-routing-refactor/quickstart.md` — 10 сценариев end-to-end.
