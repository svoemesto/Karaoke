import { reactive, ref } from 'vue'
import { fetchFavoritesIds, fetchMembership, fetchPlaylists } from '../services/playlistApi'
import { useAuth } from './useAuth'

/**
 * Module-level singleton для иконок «Избранное»/«Плейлисты» в страницах списка песен
 * (Закрома/Поиск/Плейлист автора). Pass 239 (specs/239-zakroma-author-songs-batch-render):
 * единый bulk-fetch вместо per-row chunked-вызовов, которые валили сайт на крупных авторах.
 *
 * Три параллельных source'а (module-level reactive):
 * - `favoriteIds: Set<number>` — id песен в «Избранном» (bulk-fetch `/favorites/ids`).
 * - `membership: Map<id, {favorited, playlistIds}>` — НЕ-избранные плейлисты для синей иконки
 *   (bulk-fetch `/playlists/membership`).
 * - `playlists: ref<Array>` — список плейлистов пользователя (для меню синей иконки).
 *
 * Для анонимов все запросы skip'нуты — favoriteIds пустой, membership пустой, иконки на фронте
 * показывают «гостевой» вид с редиректом на /login (Clarification Q2, 2026-08-25).
 *
 * Состояние общее на всё приложение — toggle в одном месте виден везде (через BroadcastChannel).
 * Browser back сохраняет singleton — Pass 246 fix #1, тот же паттерн что в usePlayerReadiness.
 *
 * @see specs/239-zakroma-author-songs-batch-render
 */

// ---- Избранное: плоский Set<number> (Pass 239) --------------------------------------------
const favoriteIds = reactive(new Set())
let favoritesLoaded = false
let favoritesLatest = 0

async function loadFavoritesIds(force = false) {
    const { token } = useAuth()
    const requestId = ++favoritesLatest
    if (!token.value) {
        favoriteIds.clear()
        favoritesLoaded = false
        return
    }
    if (favoritesLoaded && !force) return
    try {
        const { status, body } = await fetchFavoritesIds()
        if (requestId !== favoritesLatest) return // устаревший запрос
        if (status === 200 && Array.isArray(body)) {
            favoriteIds.clear()
            body.forEach((id) => favoriteIds.add(Number(id)))
            favoritesLoaded = true
        }
    } catch (e) {
        if (requestId !== favoritesLatest) return
        // Clarification Q3 (2026-08-25): «off» фиксируется до logout/login/reload, без retry.
    }
}

function isFavorited(id) {
    return favoriteIds.has(Number(id))
}

// ---- Плейлисты (НЕ-избранные): Map<id, {favorited, playlistIds}> ---------------------------
// id -> { favorited: bool, playlistIds: number[] }
const membership = reactive({})
let membershipLatest = 0

function ensureEntry(id) {
  const key = String(id)
  if (!membership[key]) membership[key] = { favorited: false, playlistIds: [] }
  return membership[key]
}

async function load(ids) {
  const { token } = useAuth()
  const requestId = ++membershipLatest
  const unique = [...new Set(ids.map(String))]
  if (!unique.length) return

  // Аноним — членства нет, ничего не грузим.
  if (!token.value) {
    unique.forEach((id) => {
      ensureEntry(id)
      membership[id].favorited = false
      membership[id].playlistIds = []
    })
    return
  }

  try {
    // Pass 239: один bulk-fetch со всем CSV (до ~2500 id = ~7 KB URL-encoded, укладывается
    // в HTTP-лимиты). Раньше был chunked-load 40×3 параллельных — теперь не нужен.
    const { status, body } = await fetchMembership(unique)
    if (requestId !== membershipLatest) return
    const items = (status === 200 && body && body.items) || {}
    unique.forEach((id) => {
      const it = items[id]
      const entry = ensureEntry(id)
      entry.favorited = !!(it && it.favorited)
      entry.playlistIds = (it && it.playlistIds) || []
    })
  } catch (e) {
    if (requestId !== membershipLatest) return
    // Clarification Q3: «off» фиксируется до logout/login/reload.
  }
}

function favStateFor(id) {
    // Без спиннеров: если id нет в membership и membership ещё не грузился — сразу 'off'
    // (одно приложение-wide membership, не per-row).
    const entry = membership[String(id)]
    if (entry && entry.favorited) return 'on'
    return isFavorited(id) ? 'on' : 'off'
}

// В скольких обычных (не «Избранное») плейлистах песня — для синей иконки.
function plStateFor(id) {
    const pls = (membership[String(id)] && membership[String(id)].playlistIds) || []
    return pls.length ? 'on' : 'off'
}

// Локальное обновление после toggle/add/remove (без перезагрузки).
function setFavorited(id, val) {
    ensureEntry(id).favorited = val
    if (val) {
        favoriteIds.add(Number(id))
    } else {
        favoriteIds.delete(Number(id))
    }
}
function setPlaylistIds(id, ids) {
    ensureEntry(id).playlistIds = ids
}

// ---- Список плейлистов пользователя -------------------------------------------------------
const playlists = ref([])
let playlistsLoaded = false
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

// ---- BroadcastChannel (Pass 239: расширен дискриминатором type) ---------------------------
// Синхронизация «Избранного» между вкладками/окнами/iframe одного origin. Раньше шло только
// {songId, favorited}; Pass 239 добавляет {songId, playlistIds} для НЕ-избранных плейлистов
// (и в будущем — {songId, subscribed} для подписок, когда появится UI-управление).
// Один канал с дискриминатором — проще, чем 3 отдельных канала.
const favoritesChannel =
    typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel('km-favorites') : null
if (favoritesChannel) {
    favoritesChannel.onmessage = (e) => {
        const { type, songId } = e.data || {}
        if (songId == null) return
        switch (type) {
            case 'favorited':
                setFavorited(songId, !!e.data.favorited)
                break
            case 'playlist':
                setPlaylistIds(songId, e.data.playlistIds || [])
                break
            // 'subscription' будет обрабатываться в useSongSubscriptions.js (отдельный composable).
        }
    }
}

// То же, что setFavorited(), но дополнительно рассылает изменение в другие вкладки/iframe.
// Вызывать из места, где толчок к изменению — реальное действие пользователя (клик по иконке).
function broadcastFavorited(id, val) {
    setFavorited(id, val)
    favoritesChannel?.postMessage({ type: 'favorited', songId: String(id), favorited: val })
}
function broadcastPlaylistIds(id, ids) {
    setPlaylistIds(id, ids)
    favoritesChannel?.postMessage({ type: 'playlist', songId: String(id), playlistIds: ids })
}

// ---- Reset при logout ----------------------------------------------------------------------
function reset() {
    favoriteIds.clear()
    favoritesLoaded = false
    for (const key in membership) delete membership[key]
    playlists.value = []
    playlistsLoaded = false
}

export function usePlaylistMembership() {
    return {
        // Избранное (Pass 239)
        favoriteIds,
        loadFavoritesIds,
        isFavorited,
        // Плейлисты (НЕ-избранные)
        membership,
        playlists,
        favStateFor,
        plStateFor,
        load,
        loadPlaylists,
        setFavorited,
        broadcastFavorited,
        setPlaylistIds,
        broadcastPlaylistIds,
        // Logout
        reset,
    }
}