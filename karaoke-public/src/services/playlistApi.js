// Клиент «Избранное»/«Плейлисты». Поверх authApi (тело ответа доступно и на 4xx — нужно для
// limit_reached/premium_required). Токен читаем из localStorage напрямую (как usePlayerReadiness),
// чтобы не тянуть useAuth в низкоуровневый сервис. GET-параметры собираем в query-string вручную —
// authGet отправляет только path (см. authApi.js / promisedXMLHttpRequest quirk).
import { authGet, authPost } from './authApi'

function token() {
  return localStorage.getItem('km_auth_token') || ''
}

function qs(params) {
  const pairs = []
  for (const k in params) {
    if (params[k] === undefined || params[k] === null) continue
    pairs.push(encodeURIComponent(k) + '=' + encodeURIComponent(params[k]))
  }
  return pairs.length ? '?' + pairs.join('&') : ''
}

const BASE = '/api/public/account'

// ---- Избранное / членство --------------------------------------------------------------------
export function fetchMembership(ids) {
  return authGet(`${BASE}/playlists/membership${qs({ ids: ids.join(',') })}`, token())
}
export function toggleFavorite(songId) {
  return authPost(`${BASE}/favorites/toggle`, { songId }, token())
}

// ---- Плейлисты -------------------------------------------------------------------------------
export function fetchPlaylists() {
  return authGet(`${BASE}/playlists`, token())
}
export function fetchPlaylist(id) {
  return authGet(`${BASE}/playlists/${id}`, token())
}
export function createPlaylist({ name, songId } = {}) {
  return authPost(`${BASE}/playlists/create`, { name, songId }, token())
}
export function renamePlaylist(id, name) {
  return authPost(`${BASE}/playlists/${id}/rename`, { name }, token())
}
export function deletePlaylist(id) {
  return authPost(`${BASE}/playlists/${id}/delete`, {}, token())
}
export function updatePlaylistSettings(id, { continuous, repeatMode, shuffle } = {}) {
  return authPost(`${BASE}/playlists/${id}/settings`, { continuous, repeatMode, shuffle }, token())
}
export function addSongToPlaylist(id, songId) {
  return authPost(`${BASE}/playlists/${id}/addsong`, { songId }, token())
}
export function removeSongFromPlaylist(id, songId) {
  return authPost(`${BASE}/playlists/${id}/removesong`, { songId }, token())
}
export function reorderPlaylist(id, songIds) {
  return authPost(`${BASE}/playlists/${id}/reorder`, { order: songIds.join(',') }, token())
}
export function setSongMute(id, songId, muted) {
  return authPost(`${BASE}/playlists/${id}/mute`, { songId, muted }, token())
}
