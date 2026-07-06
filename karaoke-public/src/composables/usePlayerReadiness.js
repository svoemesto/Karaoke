import { reactive } from 'vue'
import { authPost } from '../services/authApi'
import { openPlayer } from '../services/playerLauncher'

// Асинхронная подгрузка готовности плеера для строк таблицы («Закрома»/«Поиск»). Таблица рисуется
// сразу; статусы иконок докачиваются фоном: /api/public/player/readiness — тяжёлая проверка (загрузка
// Settings + 2 HEAD в MinIO на песню, см. PublicPlayerController.stemsReady), поэтому id шлём мелкими
// чанками с ограниченным параллелизмом, а иконки становятся активными/недоступными по мере ответов.
const CHUNK_SIZE = 20
const MAX_CONCURRENT = 3

export function usePlayerReadiness() {
  // id -> 'loading' | 'active' | 'disabled' (watchable — может ли ТЕКУЩИЙ посетитель открыть плеер)
  const states = reactive({})
  // id -> 'loading' | 'ready' | 'notready' (contentReady — премиум-независимая готовность контента;
  // нужна для золотой vs серебряной монетки «Доступно для премиум-пользователей»).
  const contentStates = reactive({})
  // Монотонный маркер запроса: смена автора/новый поиск начинает новый load — ответы устаревших
  // чанков игнорируются (аналог latestRequestId в store/modules/zakroma.js).
  let latest = 0

  function stateFor(id) {
    return states[id] || 'loading'
  }

  // 'loading' | 'ready' | 'notready'
  function contentReadyFor(id) {
    return contentStates[id] || 'loading'
  }

  async function load(ids) {
    const requestId = ++latest
    // Сбрасываем карты и выставляем все id в 'loading'.
    for (const k of Object.keys(states)) delete states[k]
    for (const k of Object.keys(contentStates)) delete contentStates[k]
    const unique = [...new Set(ids.map(String))]
    unique.forEach(id => { states[id] = 'loading'; contentStates[id] = 'loading' })
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
            token
          )
          if (requestId !== latest) return // устаревший запрос — не трогаем текущую карту
          const items = (status === 200 && body && body.items) || {}
          chunk.forEach(id => {
            states[id] = items[id] && items[id].watchable ? 'active' : 'disabled'
            contentStates[id] = items[id] && items[id].contentReady ? 'ready' : 'notready'
          })
        } catch (e) {
          if (requestId !== latest) return
          chunk.forEach(id => { states[id] = 'disabled'; contentStates[id] = 'notready' })
        }
      }
    }

    await Promise.all(Array.from({ length: Math.min(MAX_CONCURRENT, chunks.length) }, worker))
  }

  return { states, stateFor, contentReadyFor, load, openPlayer }
}
