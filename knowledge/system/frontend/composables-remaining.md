# Composables: remaining (5 файлов)

> **Домен**: system (frontend)
> **Компонента**: детальный обзор 5 оставшихся composables.

## Файлы (5 stores)

| Composable | Файл | Строк | Назначение |
|---|---|---|---|
| `useAuthBootstrap` | `karaoke-public/.../useAuthBootstrap.js` | 57 | **Bootstrap membership** при логине (Pass 239) |
| `usePremiumModal` | `karaoke-public/.../usePremiumModal.js` | 37 | **Singleton-контроллер** модалки premium-апселла |
| `useSiteSubscription` | `karaoke-public/.../useSiteSubscription.js` | 81 | Подписка на сайт (scope=SITE) |
| `useSongSubscription` | `karaoke-public/.../useSongSubscription.js` | 60 | Подписка на 1 песню (scope=SONG) |
| `useSongSubscriptions` | `karaoke-public/.../useSongSubscriptions.js` | 53 | Module-level singleton — Set<id> песен с подпиской |
| `usePlayerGestureUnlock` | `karaoke-web/.../usePlayerGestureUnlock.js` (175) | 175 | Gesture unlock для мобильного плеера |
| `editorStatus` | `karaoke-public/.../editorStatus.js` | 9 | ENUM-метки статусов редактора |

## Детальные контракты

### `useAuthBootstrap` (Pass 239)

**Следит за сменой токена** авторизации и при логине запускает
**bulk-fetch** membership-данных:

- `/api/public/account/favorites/ids` — плоский список id песен
  в «Избранном».
- `/api/public/account/playlists` — playlists.
- `/api/public/account/song-subscriptions/ids` — подписки на песни.

**Связан**: `usePlaylistMembership` ([composable-playlist-membership.md](composable-playlist-membership.md)),
`useSongSubscriptions`.

### `usePremiumModal`

**Singleton-контроллер** модалки premium-апселла / достижения
лимита. Монтируется **один раз** (`PremiumUpsellModal` в `App.vue`),
открывается из любого места (`FavoriteIcon`, `PlaylistIcon` и т.п.).

```javascript
const state = reactive({
    open: false,
    title: '',
    message: '',
    limit: null,
    benefits: [],
})
```

### `useSiteSubscription` (81 строка)

**Периодическая подписка на сайт** (scope=SITE). Аналог
`useSongSubscription`, но с **выбором тарифа** (несколько
сроков/цен) и флагом **автопродления** (включён по умолчанию).

```javascript
const tariffs = ref([]) // [{id, name, priceRub, periodDays, isDefault}, ...]
const loadingTariffs = ref(false)
```

### `useSongSubscription` (60 строк)

**Оформление подписки на ОДНУ песню** (бессрочно) — scope=SONG.

> Термин «покупка» нигде не используется. Цена всегда
> **пересчитывается на сервере** (`PriceService`) — это лишь
> **ОТОБРАЖЕНИЕ** цены пользователю ДО оплаты, сервер **не
> доверяет** тому, что ушло обратно.

### `useSongSubscriptions` (53 строки, module-level)

**Module-level singleton**: `Set<number>` id песен с активной
персональной подпиской (scope='SONG', status='PAID').

Загружается одним bulk-fetch параллельно
`favorites`/`playlists` (specs/239).

**Используется** `PlayerIcon` как условие «зелёный vs золотой» плеера
— без per-row запроса на бэкенд.

### `usePlayerGestureUnlock` (175 строк)

**Gesture unlock** для мобильного плеера (touch/swipe). См.
[karaoke-web/services-overview.md](../../domains/karaoke-web/components/services-overview.md#6-playergestureunlockservice-175-строк).

### `editorStatus` (9 строк)

Простой ENUM-меток для статусов редактора:

```javascript
export const STATUS_LABELS = {
    assigned: 'Назначено',
    in_progress: 'В работе',
    submitted: 'На проверке',
    approved: 'Одобрено',
    rejected: 'Отклонено',
}
```

Используют `EditorTasksView` и `EditorWorkView`. Держим в одном
месте для согласованности.

## Hot paths

- **useAuthBootstrap** — на каждый login/logout (Pass 239).
- **usePremiumModal** — на каждый upsell-trigger (FavoriteIcon,
  PlaylistIcon).
- **useSongSubscription / useSiteSubscription** — на странице оплаты.
- **useSongSubscriptions** — на каждое открытие плейлиста/закла.

## Связь

- **Monetization** ([monetization domain](../../domains/monetization/domain.md)) —
  PaymentService, PriceService.
- **Identity** ([identity domain](../../domains/identity/domain.md)) —
  SiteUser.

## Changelog

- **Pass 411-415** (2026-09-09): Initial. Автор: agent (Karaoke).