# Composable: useCart

> **Домен**: system (frontend)
> **Компонента**: `karaoke-public/src/composables/useCart.js` —
> корзина (module-level singleton).

## Файл

`karaoke-public/src/composables/useCart.js` (58 строк)

## Назначение

**Синглтон «Корзины»** — небольшой личный список. В отличие от
`usePlaylistMembership` **не требует** чанкованной батч-загрузки по
id строк таблицы: грузим **весь список целиком** одним запросом.

## State (module-level)

```javascript
const items = ref([])                              // [{ songId, songName, author }]
const songIds = reactive(new Set())                // для быстрой проверки isInCart
let loaded = false                                 // single-load flag
```

## API

```javascript
load(force = false)        // async — загрузить из backend
isInCart(songId)           // sync — проверка
toggleCart(songId)         // async — добавить/удалить
clearCart()                // async — очистить
```

## Логика `load(force)`

- Если `!token` (аноним) — очистить `items` + `songIds`, `loaded=true`,
  return.
- Если `loaded && !force` — skip.
- Иначе — `fetchCart()`, наполнить `items` и `songIds`.

## Зависимости

- `useAuth` ([composable-use-auth.md](composable-use-auth.md)) — token.

## Hot paths

- **`/api/public/cart/list`** — загрузка.
- **`/api/public/cart/{id}/toggle`** — toggle.
- **`/api/public/cart/clear`** — clear.

## Связь

- **CartItem** ([monetization domain](../../domains/monetization/domain.md)) —
  entity.
- **PublicCartController** ([karaoke-web/public-controllers.md](../../domains/karaoke-web/components/public-controllers.md)) — backend.

## Changelog

- **Pass 401** (2026-09-09): Initial. Автор: agent (Karaoke).