# Frontend composables (karaoke-public)

> **Домен**: system (cross-cutting)
> **Компонента**: каталог и паттерны 18 composables в `karaoke-public`.

## Назначение

`karaoke-public` — публичный SPA (для посетителей и
зарегистрированных пользователей). Использует **Vue 3 composables**
(Composition API) для state и side effects, а НЕ Vuex.

В отличие от `webvue3` (Vuex), `karaoke-public` следует
современному паттерну Composition API — каждый composable
инкапсулирует state + actions для одной функциональности.

## Каталог composables (18 файлов, отсортированы по размеру)

| # | Composable | Строк | Файл | Назначение |
|---|---|---|---|---|
| 1 | `useKaraokeEditor` | 557 | `useKaraokeEditor.js` | Редактор караоке-песни (ввод маркеров) |
| 2 | `useZakromaStreamProgress` | 488 | `useZakromaStreamProgress.js` | Прогресс стрима «Закрома» автора |
| 3 | `useNewsUnreadCount` | 295 | `useNewsUnreadCount.js` | Счётчик непрочитанных новостей + polling |
| 4 | `usePlaylistMembership` | 210 | `usePlaylistMembership.js` | Управление плейлистами пользователя |
| 5 | `usePlaylistPlayer` | 112 | `usePlaylistPlayer.js` | Плеер плейлиста |
| 6 | `usePlayerReadiness` | 88 | `usePlayerReadiness.js` | Готовность плеера (playerdata + audio) |
| 7 | `useSiteSubscription` | 81 | `useSiteSubscription.js` | Подписка на сайт (scope=SITE) |
| 8 | `useEngagementTracking` | 79 | `useEngagementTracking.js` | Скролл-трекинг для вебвизора |
| 9 | `usePlayerAccess` | 73 | `usePlayerAccess.js` | Доступ к песне (премиум/бесплатная) |
| 10 | `useAuth` | 68 | `useAuth.js` | Auth state (текущий user) |
| 11 | `useSongSubscription` | 60 | `useSongSubscription.js` | Подписка на 1 песню (scope=SONG) |
| 12 | `useCart` | 58 | `useCart.js` | Корзина |
| 13 | `useAuthBootstrap` | 57 | `useAuthBootstrap.js` | Auth bootstrap при загрузке |
| 14 | `useSongSubscriptions` | 53 | `useSongSubscriptions.js` | Несколько подписок (список) |
| 15 | `useShareLink` | 43 | `useShareLink.js` | Share-ссылка на песню |
| 16 | `usePremiumModal` | 37 | `usePremiumModal.js` | Модалка «Стать Premium» |
| 17 | `useDesign` | 25 | `useDesign.js` | Classic/modern дизайн |
| 18 | `editorStatus` | 9 | `editorStatus.js` | ENUM статусов редактора |

## Архитектурные решения

### Решение 1: Composition API, не Vuex

`karaoke-public` — современный Vue 3 стиль. Каждый composable —
отдельная единица функциональности с собственным state.

### Решение 2: SSR-готовность (если применимо)

Composables — это setup-функции, можно использовать в SSR. Vuex
тоже поддерживает SSR, но Composition API обычно проще.

### Решение 3: Polling для unread счётчиков

`useNewsUnreadCount` (295 строк) — самый большой — реализует
**polling + backoff** для бейджа непрочитанных новостей.

Аналог `PollingCache<V>` на backend (см.
[web-caches.md](../../domains/caching/components/web-caches.md)) —
TTL=10s для `unreadcount`.

### Решение 4: Engagement tracking

`useEngagementTracking` (79 строк) — трекинг скролла/времени для
вебвизора. Использует `SamplingFilter` + `DedupCache` на backend
(см. [web-caches.md](../../domains/caching/components/web-caches.md)).

### Решение 5: Auth + AuthBootstrap

`useAuth` + `useAuthBootstrap` — отдельные composables (а не один)
для разделения state и lifecycle. AuthBootstrap запускается ОДИН раз
при загрузке приложения, useAuth — реактивный state.

## Паттерн: composable

```javascript
// useCart.js (пример)
import { ref, computed } from 'vue'
import { useAuth } from './useAuth'

export function useCart() {
    const items = ref([])  // state
    const total = computed(() => items.value.length)  // derived state
    const { user } = useAuth()  // dependency injection

    async function addItem(songId) {
        // action — XHR + commit
    }

    return { items, total, addItem }
}
```

## Известные TODO

- [ ] **Каждый из 18 composables** — детальный state/actions.
- [ ] **SSR** — реально ли используется?
- [ ] **i18n** — поддерживается ли?
- [ ] **CSS-переменные `--km-*`** — где определены, как настраиваются.
- [ ] **Polling intervals** — какие композиции используют polling,
      с какими интервалами.
- [ ] **Error handling** — единый паттерн или разнобой?
- [ ] **Testing** — есть ли unit-тесты?

## Связь с другими компонентами

- **Caching** ([web-caches.md](../../domains/caching/components/web-caches.md)) —
  `DedupCache` + `PollingCache` используются в `useNewsUnreadCount`.
- **SSE** ([sse domain](../../domains/sse/domain.md)) — для real-time
  обновлений.
- **Monetization** ([monetization domain](../../domains/monetization/domain.md)) —
  `useCart`, `useSongSubscription`, `useSiteSubscription`.

## Changelog

- **Pass 350** (2026-09-09): Initial overview. Автор: agent (Karaoke).