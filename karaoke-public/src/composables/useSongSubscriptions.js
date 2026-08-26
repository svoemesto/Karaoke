import { reactive } from 'vue'
import { fetchSongSubscriptionsIds } from '../services/playlistApi'
import { useAuth } from './useAuth'

/**
 * Module-level singleton: плоский Set<number> id песен с активной персональной подпиской
 * (scope='SONG', status='PAID') у текущего пользователя. Загружается одним bulk-fetch
 * параллельно favorites/playlists (см. specs/239-zakroma-author-songs-batch-render).
 *
 * Используется `PlayerIcon` как условие «зелёный vs золотой» плеера — без per-row запроса
 * /api/public/player/{id}/access на каждую песню (что раньше валило сайт на крупных авторах).
 *
 * Состояние общее на всё приложение — toggle в одном месте виден везде.
 * Аноним (токена нет) → пустой Set, вызов backend пропускается.
 *
 * @see specs/239-zakroma-author-songs-batch-render
 */
const subscriptionIds = reactive(new Set())
let loaded = false
let latest = 0

async function loadOnce(force = false) {
    const { token } = useAuth()
    const requestId = ++latest
    if (!token.value) {
        subscriptionIds.clear()
        loaded = false
        return
    }
    if (loaded && !force) return
    try {
        const { status, body } = await fetchSongSubscriptionsIds()
        if (requestId !== latest) return // устаревший запрос
        if (status === 200 && Array.isArray(body)) {
            subscriptionIds.clear()
            body.forEach((id) => subscriptionIds.add(Number(id)))
            loaded = true
        }
    } catch (e) {
        if (requestId !== latest) return
        // Падение fetch — оставляем текущий Set как есть (Clarification Q3 2026-08-25:
        // «off» фиксируется до logout/login/reload, без retry/таймеров).
    }
}

function reset() {
    subscriptionIds.clear()
    loaded = false
}

export function useSongSubscriptions() {
    return { subscriptionIds, loadOnce, reset }
}