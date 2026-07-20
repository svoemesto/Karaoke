import { reactive, ref } from 'vue'
import { fetchMembership, fetchPlaylists } from '../services/playlistApi'
import { useAuth } from './useAuth'

// Синглтон членства песен в «Избранном»/плейлистах для иконок в таблицах «Закрома»/«Поиск».
// Таблица рисуется сразу; членство докачивается фоном чанками (как usePlayerReadiness). Состояние
// общее на всё приложение — toggle в одном месте виден везде. Для анонимов запросов нет (всё 'off').
const CHUNK_SIZE = 40
const MAX_CONCURRENT = 3

// id -> { favorited: bool, playlistIds: number[] }
const membership = reactive({})
// id -> bool (ещё грузится)
const loading = reactive({})
// Кэш списка плейлистов пользователя (для меню синей иконки — этап 2).
const playlists = ref([])
let playlistsLoaded = false
let latest = 0

function favStateFor(id) {
  if (loading[id]) return 'loading'
  return membership[id] && membership[id].favorited ? 'on' : 'off'
}

// В скольких обычных (не «Избранное») плейлистах песня — для синей иконки.
function plStateFor(id) {
  if (loading[id]) return 'loading'
  const pls = (membership[id] && membership[id].playlistIds) || []
  return pls.length ? 'on' : 'off'
}

function ensureEntry(id) {
  const key = String(id)
  if (!membership[key]) membership[key] = { favorited: false, playlistIds: [] }
  return membership[key]
}

async function load(ids) {
  const { token } = useAuth()
  const requestId = ++latest
  const unique = [...new Set(ids.map(String))]
  // Не сбрасываем всю карту (toggle мог обновить строки других страниц) — только помечаем новые
  // как loading и обновляем их из ответа.
  unique.forEach((id) => {
    loading[id] = true
  })
  if (!unique.length) return

  // Аноним — членства нет, ничего не грузим.
  if (!token.value) {
    unique.forEach((id) => {
      ensureEntry(id)
      membership[id].favorited = false
      membership[id].playlistIds = []
      loading[id] = false
    })
    return
  }

  const chunks = []
  for (let i = 0; i < unique.length; i += CHUNK_SIZE) chunks.push(unique.slice(i, i + CHUNK_SIZE))
  let cursor = 0

  async function worker() {
    while (cursor < chunks.length) {
      const chunk = chunks[cursor++]
      try {
        const { status, body } = await fetchMembership(chunk)
        if (requestId !== latest) return
        const items = (status === 200 && body && body.items) || {}
        chunk.forEach((id) => {
          const it = items[id]
          const entry = ensureEntry(id)
          entry.favorited = !!(it && it.favorited)
          entry.playlistIds = (it && it.playlistIds) || []
          loading[id] = false
        })
      } catch (e) {
        if (requestId !== latest) return
        chunk.forEach((id) => {
          loading[id] = false
        })
      }
    }
  }

  await Promise.all(Array.from({ length: Math.min(MAX_CONCURRENT, chunks.length) }, worker))
}

// Локальное обновление после toggle/add/remove (без перезагрузки).
function setFavorited(id, val) {
  ensureEntry(id).favorited = val
}
function setPlaylistIds(id, ids) {
  ensureEntry(id).playlistIds = ids
}

async function loadPlaylists(force = false) {
  const { token } = useAuth()
  if (!token.value) {
    playlists.value = []
    return []
  }
  if (playlistsLoaded && !force) return playlists.value
  const { status, body } = await fetchPlaylists()
  if (status === 200 && Array.isArray(body)) {
    playlists.value = body
    playlistsLoaded = true
  }
  return playlists.value
}

export function usePlaylistMembership() {
  return {
    membership,
    playlists,
    favStateFor,
    plStateFor,
    load,
    loadPlaylists,
    setFavorited,
    setPlaylistIds,
  }
}
