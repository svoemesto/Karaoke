# Composable: usePlayerReadiness

> **Домен**: system (frontend)
> **Компонента**: `karaoke-public/src/composables/usePlayerReadiness.js`
> — асинхронная подгрузка готовности плеера.

## Файл

`karaoke-public/src/composables/usePlayerReadiness.js` (88 строк)

## Назначение

Асинхронная подгрузка **готовности плеера** для строк таблицы
(«Закрома»/«Поиск»). Таблица рисуется сразу; статусы иконок
докачиваются фоном.

**`/api/public/player/readiness`** — тяжёлая проверка (загрузка
`Settings` + 2 HEAD в MinIO на песню, см.
`PublicPlayerController.stemsReady`). Поэтому:
- ID шлются **мелкими чанками** (`CHUNK_SIZE = 20`).
- Параллелизм ограничен (`MAX_CONCURRENT = 3`).

## State (module-level, не local)

```javascript
// id -> 'loading' | 'active' | 'disabled'
const states = reactive({})
// id -> 'loading' | 'ready' | 'notready'
const contentStates = reactive({})
// Монотонный маркер запроса
let latest = 0
```

**NB**: state на **module-level**, а не внутри
`usePlayerReadiness()`. Иначе на browser back из `/song/{id}` state
теряется (новый composable instance, state = {}), все иконки вечно
в 'loading'.

**Аналогичный паттерн** в `usePlaylistMembership.js` (тоже
module-level `membership`/`loading`) — потому букмарки/favorites
работают корректно после browser back, а иконки плеера — нет.
**Это fix #246**.

**Объём state ограничен**: ~20k песен × 2 dicts по ~50 байт = ~2 MB, не
memory leak.

## Алгоритм

1. User открывает «Закрома» → таблица рисуется сразу с
   `state[id]='loading'`.
2. Background: `loadAllReadiness(allIds)` —
   - Увеличить `latest` (монотонный маркер).
   - Разбить на чанки по 20.
   - Для каждого чанка: `authPost('/api/public/player/readiness', {ids})`.
   - Параллелизм ≤ 3.
   - На ответ: если `latest` всё ещё актуален — обновить `states` +
     `contentStates`.
3. UI reactive: при изменении `states[id]` — иконка обновляется.

## Domain Invariants

1. **`latest` — монотонный** (Pass 343+: проверить `Math.max` или
   `++`).
2. **State is shared** (module-level) — между разными
   `usePlayerReadiness()` вызовами в одном browser tab.
3. **Chunk size = 20** — соответствует максимальному
   `BatchSize` на backend (Pass 343+: проверить).

## Известные TODO

- [ ] **`authPost`** — детальный контракт.
- [ ] **`loadAllReadiness`** — полный код (выше только contract).
- [ ] **Очистка stale state** — что когда песня удалена.

## Связь

- **PublicPlayerController** ([karaoke-web/public-controllers.md](../../domains/karaoke-web/components/public-controllers.md)) —
  `/api/public/player/readiness`.
- **Storage** ([storage domain](../../domains/storage/domain.md)) —
  HEAD в MinIO.

## Changelog

- **Pass 372** (2026-09-09): Initial. Автор: agent (Karaoke).