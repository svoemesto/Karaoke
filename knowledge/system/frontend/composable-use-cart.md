# Composable: useCart

> **Домен**: system (frontend)
> **Компонента**: `karaoke-public/src/composables/useCart.js` —
> корзина (module-level singleton).

## Файл

`karaoke-public/src/composables/useCart.js` (76 строк)

## Назначение

**Синглтон «Корзины»** — небольшой личный список. В отличие от
`usePlaylistMembership` **не требует** чанкованной батч-загрузки по
id строк таблицы: грузим **весь список целиком** одним запросом.

## State (module-level)

```javascript
const items = ref([])                              // [{ songId, songName, author }]
const songIds = reactive(new Set())                // для быстрой проверки isInCart
let loaded = false                                 // single-load flag
let loadedForToken = null                         // для какого токена грузили (BR-314)
```

## API

```javascript
load(force = false)        // async — загрузить из backend
isInCart(songId)           // sync — проверка
toggleCart(songId)         // async — добавить/удалить
clearCart()                // async — очистить
reset()                    // sync — полный сброс (logout / смена пользователя)
```

## Логика `load(force)`

- Если `!token` (аноним) — очистить `items` + `songIds`, **сбросить `loaded` в
  `false`** и `loadedForToken` в `null`, return.
- Если `loaded && !force && loadedForToken === token` — skip.
- Иначе — `fetchCart()`, наполнить `items` и `songIds`, запомнить токен.

## Инварианты (BR-314, аудит #336, OpenProject #399)

1. Для анонима корзина **не** помечается «загруженной»: после входа `load()`
   обязан реально сходить на сервер, иначе иконка корзины в шапке не появляется.
2. `loaded` привязан к токену (`loadedForToken`): смена пользователя в том же
   браузере требует перезагрузки корзины — корзины у пользователей разные.
3. `reset()` вызывается из `useAuthBootstrap.onLogout()`: позиции прежнего
   пользователя не должны оставаться видимыми следующему.
4. Ответ `toggle` разбирается вызывающим кодом (`classifyCartToggle` в
   `karaoke-public/src/utils/cartToggle.js`): `already_subscribed` — «подписка уже оформлена»
   (иконка прячется), `song_not_for_subscription` — «песня не продаётся поштучно»,
   401/403 — «войдите заново», прочее — общий отказ. Раньше результат
   игнорировался, и кнопка «молча не работала».

## Зависимости

- `useAuth` ([composable-use-auth.md](composable-use-auth.md)) — token.

## Hot paths

- **`GET /api/public/account/cart/list`** — загрузка.
- **`POST /api/public/account/cart/toggle`** — toggle (`songId` — параметр body).
- **`POST /api/public/account/cart/clear`** — clear.

## Связь

- **CartItem** ([monetization domain](../../domains/monetization/domain.md)) —
  entity.
- **PublicCartController** ([karaoke-web/public-controllers.md](../../domains/karaoke-web/components/public-controllers.md)) — backend.

## Связанные документы

- [karaoke-public-audit-invariants.md](karaoke-public-audit-invariants.md) —
  сквозные инварианты публичного SPA (аудит #336, `PKG-S3-27`).

## Changelog

- **Pass 401** (2026-09-09): Initial. Автор: agent (Karaoke).
