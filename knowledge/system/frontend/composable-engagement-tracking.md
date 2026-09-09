# Composable: useEngagementTracking

> **Домен**: system (frontend)
> **Компонента**: `karaoke-public/src/composables/useEngagementTracking.js`
> — трекинг вовлечённости на странице.

## Файл

`karaoke-public/src/composables/useEngagementTracking.js` (79 строк)

## Назначение

Трекинг вовлечённости на странице:
- **Время видимости** (в секундах) — `document.visibilityState`.
- **Глубина скролла** по вехам **25/50/75/100%**.

Подключается в `setup()`-компоненте:
`useEngagementTracking('song', () => id)`.

## API

```javascript
useEngagementTracking(pageName, getSongId)
// pageName: 'home' | 'zakroma' | 'search' | 'song'
// getSongId: id песни (функция или значение)
```

## State (внутренний)

```javascript
let accumulatedMs = 0          // накопленное время в мс
let lastStart = null           // последний момент старта таймера
const scrollFlags = { 25: false, 50: false, 75: false, 100: false }
let scrollScheduled = false
```

## Логика

- **Timer**: при `visibilitychange: visible` — `startTimer()`; при
  `hidden` — `stopTimer()` (накапливает `accumulatedMs`).
- **Scroll**: на `scroll` event — `requestAnimationFrame` throttle
  (`scrollScheduled`).
- **Flush**: при `pagehide` или `visibilitychange: hidden` отправляет
  `trackPageEngagement({ pageName, songId, durationSec, scrollMaxPct })`.
- **Обнуляет** `accumulatedMs` после flush, чтобы не задвоить при
  повторном flush на `pagehide` после `visibilitychange`.

## Связь

- **Tracking** (`trackPageEngagement` + `trackUi`) — backend
  аналитика.
- [entities-catalog.md#webevent](../../domains/catalog/components/entities-catalog.md#webevent) — events.

## Changelog

- **Pass 400** (2026-09-09): Initial. Автор: agent (Karaoke).