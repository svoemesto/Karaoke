import { reactive } from 'vue'
import { authPost } from '../services/authApi'
import { openPlayer } from '../services/playerLauncher'

// Асинхронная подгрузка готовности плеера для строк таблицы («Закрома»/«Поиск»). Таблица рисуется
// сразу; статусы иконок докачиваются фоном: /api/public/player/readiness — тяжёлая проверка (загрузка
// Settings + 2 HEAD в MinIO на песню, см. PublicPlayerController.stemsReady), поэтому id шлём мелкими
// чанками с ограниченным параллелизмом, а иконки становятся активными/недоступными по мере ответов.
const CHUNK_SIZE = 20
const MAX_CONCURRENT = 3

// Module-level state (НЕ local в `usePlayerReadiness()`!) — иначе на browser back
// из /song/{id} state теряется (новый composable instance, state = {}), все иконки
// вечно в 'loading'. Аналогичный паттерн в `usePlaylistMembership.js` (тоже
// module-level `membership`/`loading`) — потому букмарки/favorites работают
// корректно после browser back, а иконки плеера — нет. Это fix #246.
//
// Объём state ограничен: ~20k песен × 2 dicts по ~50 байт = ~2 MB, не memory leak.
//
// id -> 'loading' | 'active' | 'disabled' (watchable — может ли ТЕКУЩИЙ посетитель открыть плеер)
const states = reactive({})
// id -> 'loading' | 'ready' | 'notready' (contentReady — премиум-независимая готовность контента;
// нужна для золотой vs серебряной монетки «Доступно для премиум-пользователей»).
const contentStates = reactive({})
// Монотонный маркер запроса: смена автора/новый поиск начинает новый load — ответы устаревших
// чанков игнорируются (аналог latestRequestId в store/modules/zakroma.js).
let latest = 0

export function usePlayerReadiness() {
  function stateFor(id) {
    return states[id] || 'loading'
  }

  // 'loading' | 'ready' | 'notready'
  function contentReadyFor(id) {
    return contentStates[id] || 'loading'
  }

  async function load(ids) {
    const requestId = ++latest
    const unique = [...new Set(ids.map(String))]
    // НЕ сбрасываем state ids, которых нет в этой загрузке — они могут
    // принадлежать другой View (Search/Zakroma/Playlist), которая ещё
    // использует их. Только помечаем новые id как 'loading'.
    unique.forEach((id) => {
      if (!states[id]) states[id] = 'loading'
      if (!contentStates[id]) contentStates[id] = 'loading'
    })
    if (!unique.length) return

    const chunks = []
    for (let i = 0; i < unique.length; i += CHUNK_SIZE) {
      chunks.push(unique.slice(i, i + CHUNK_SIZE))
    }

    const token = localStorage.getItem('km_auth_token')
    let cursor = 0

    async function worker() {
      while (cursor < chunks.length) {
        const chunk = chunks[cursor++]
        try {
          const { status, body } = await authPost(
            '/api/public/player/readiness',
            { ids: chunk.join(',') },
            token,
          )
          if (requestId !== latest) return // устаревший запрос — не трогаем текущую карту
          const items = (status === 200 && body && body.items) || {}
          chunk.forEach((id) => {
            const inItems = items[id]
            if (inItems) {
              states[id] = inItems.watchable ? 'active' : 'disabled'
              contentStates[id] = inItems.contentReady ? 'ready' : 'notready'
            }
          })
        } catch (e) {
          if (requestId !== latest) return
          // On error: don't change states (could be transient network error).
        }
      }
    }

    await Promise.all(Array.from({ length: Math.min(MAX_CONCURRENT, chunks.length) }, worker))
  }

  return { states, stateFor, contentReadyFor, load, openPlayer }
}
