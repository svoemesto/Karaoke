# Composable: usePlayerAccess

> **Домен**: system (frontend)
> **Компонента**: `karaoke-public/src/composables/usePlayerAccess.js`
> — проверка доступа к плееру песни.

## Файл

`karaoke-public/src/composables/usePlayerAccess.js` (73 строки)

## Назначение

Опрашивает `/api/public/player/{id}/access`, который решает, можно ли
**прямо сейчас** встроить онлайн-плеер на странице песни вместо
видео ВК ("в эфире") или сообщения об ожидании.

## State

```javascript
const ready = ref(false)              // бэкенд ответил
const isPremiumUser = ref(false)      // текущий юзер — premium
const canWatch = ref(false)           // можно встроить плеер
const canExport = ref(false)          // можно экспортировать (premium)
const isDemo = ref(false)             // demo-фрагмент
const demoFadeInSeconds = ref(null)
const loaded = ref(false)             // индикатор загрузки
```

## Алгоритм `checkAccess(songId, shareSessionTokenHash)`

1. Сбросить state.
2. `authGet('/api/public/player/{id}/access?session=...')`.
3. На ответ — обновить refs.
4. **`canWatch=true`** → бэкенд выдал токен → кладём в
   `sessionStorage` под `kp_token_<id>`, который читает
   существующий `/player/:id` роут.
5. **`isDemo=true`** (canWatch остаётся false) — тот же токен, но
   плеер встроится, сам обрежет до demo-фрагмента
   (см. `KaraokePlayer.js` — фейд-ин/watermark/оверлей).

## Архитектурные решения

### Решение 1: Reuse через iframe

Сам плеер (`PlayerView.vue`, router guard) **переиспользуется без
изменений** — просто встраивается через iframe вместо открытия в
новой вкладке. Токен — в `sessionStorage` под `kp_token_<id>`.

### Решение 2: Share-сессия передаётся в /access

`session=<shareSessionTokenHash>` — если есть валидная share-сессия,
бэкенд выдаёт `canWatch=true, canExport=false` (см. spec FR-050 +
research.md Decision 2).

### Решение 3: Demo-фрагмент

`isDemo=true` + `demoFadeInSeconds` — плеер **сам** обрежет
себя до демо-фрагмента (`data.isDemo`, `data.demoFadeInSeconds`).
`demoFadeInSeconds` здесь дублируется на случай показа длительности
ДО открытия плеера.

## Связь

- **PublicPlayerController** ([karaoke-web/public-controllers.md](../../domains/karaoke-web/components/public-controllers.md)) —
  `/api/public/player/{id}/access`.
- **SongShareLinkService** ([song-share-link-service.md](../../domains/karaoke-web/components/song-share-link-service.md)) —
  share-сессии.
- **KaraokePlayer.js** — обрабатывает `kp_token_<id>` + demo.

## Changelog

- **Pass 383** (2026-09-09): Initial. Автор: agent (Karaoke).