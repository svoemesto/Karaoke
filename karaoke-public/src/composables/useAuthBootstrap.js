import { watch } from 'vue'
import { useAuth } from './useAuth'
import { usePlaylistMembership } from './usePlaylistMembership'
import { useSongSubscriptions } from './useSongSubscriptions'

/**
 * Auth bootstrap (Pass 239, specs/239-zakroma-author-songs-batch-render).
 *
 * Следит за сменой токена авторизации и при логине запускает bulk-fetch membership-данных:
 *   1. `/api/public/account/favorites/ids` — плоский список id песен в «Избранном».
 *   2. `/api/public/account/song-subscriptions/ids` — id песен с активной подпиской.
 *   3. `/api/public/account/playlists` — список плейлистов (нужен для меню синей иконки).
 *
 * Per-row membership (`/playlists/membership?ids=...`) грузится отдельным вызовом на странице
 * крупного автора (`membership.load(allSongIds)`), а не здесь — здесь только «глобальные» данные.
 *
 * При logout (token = '') — все store'ы сбрасываются (см. `reset()` каждого composable).
 * Идемпотентность: повторный вызов на тот же токен — skip (useSongSubscriptions.loadOnce +
 * playlistMembership.loadPlaylists оба имеют `loaded`-guard).
 *
 * Должен быть вызван ОДИН раз на приложение — например из main.js после `app.mount()`.
 */
let started = false

export function useAuthBootstrap() {
    if (started) return
    started = true

    const { token } = useAuth()
    const { loadFavoritesIds, loadPlaylists, reset: resetPlaylists } = usePlaylistMembership()
    const { loadOnce, reset: resetSubscriptions } = useSongSubscriptions()

    function onLogin() {
        // Параллельно: избранное + подписки + список плейлистов. membership per-song —
        // отдельный вызов на странице автора.
        Promise.all([loadFavoritesIds(), loadOnce(true), loadPlaylists(true)])
    }

    function onLogout() {
        resetPlaylists()
        resetSubscriptions()
    }

    // Стартуем сразу (если уже залогинен при загрузке страницы — token может быть в localStorage)
    if (token.value) onLogin()

    // Watcher: реагируем на смену токена (login/logout из LoginView и т.п.)
    watch(token, (newToken, oldToken) => {
        if (newToken && !oldToken) onLogin()
        else if (!newToken && oldToken) onLogout()
        // Токен сменился на другой (повторный логин другим юзером): перезагрузить всё
        else if (newToken && oldToken && newToken !== oldToken) {
            onLogout()
            onLogin()
        }
    })
}