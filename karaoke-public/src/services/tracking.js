import { apiPost } from './api'
import { useAuth } from '../composables/useAuth'

// Anonymous per-browser id, reused across all tracked events — a common, unremarkable analytics
// pattern (distinguishing repeat visitors) that also happens to let the backend correlate clicks
// into a gesture without relying on IP alone (shared/NAT'd IPs would otherwise cross-contaminate).
function getClientId() {
  const KEY = 'kp_cid'
  let id = localStorage.getItem(KEY)
  if (!id) {
    id = crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`
    localStorage.setItem(KEY, id)
  }
  return id
}

// useAuth() — module-level singleton ref (см. composables/useAuth.js), безопасно читать вне
// setup()-контекста компонента: это просто разделяемый реактив, а не Vue DI (inject()).
function authHeader() {
  const { token } = useAuth()
  return token.value ? { Authorization: `Bearer ${token.value}` } : undefined
}

export function trackLinkToSocialNetwork(linkName) {
  apiPost('/api/public/events', { eventType: 'clickToLink', linkType: 'linkToSocialNetwork', linkName, anonId: getClientId() }, authHeader()).catch(() => {})
}

export function trackLinkToSong(linkName, songId, songVersion) {
  apiPost('/api/public/events', { eventType: 'clickToLink', linkType: 'linkToSong', linkName, songId, songVersion, anonId: getClientId() }, authHeader()).catch(() => {})
}

export function trackPlay(songId, songVersion) {
  apiPost('/api/public/events', { eventType: 'play', songId, songVersion, anonId: getClientId() }, authHeader()).catch(() => {})
}

// Tracks a click on a song metadata field (key/album/author/year/...). Looks like an ordinary
// UX-research beacon; unlike the other tracking calls here it returns the server's response,
// since occasionally that response carries a meaning callers may care about.
export async function trackMetaClick(field, songId, event) {
  try {
    return await apiPost('/api/public/events', {
      eventType: 'clickToLink',
      linkType: 'songMeta',
      linkName: field,
      songId,
      shiftKey: !!(event && event.shiftKey),
      clientId: getClientId()
    })
  } catch {
    return null
  }
}

// Онлайн-плеер: открытие/play/pause/seek/export — тихая фоновая телеметрия, тем же
// fire-and-forget паттерном, что и остальные функции здесь (ошибка сети проглатывается молча,
// в UI никак не отражается).
function trackPlayerAction(linkType, songId, linkName) {
  const payload = { eventType: 'player', linkType, songId, anonId: getClientId() }
  if (linkName !== undefined) payload.linkName = linkName
  apiPost('/api/public/events', payload, authHeader()).catch(() => {})
}

export function trackPlayerPlay(songId) { trackPlayerAction('play', songId) }
export function trackPlayerPause(songId) { trackPlayerAction('pause', songId) }
export function trackPlayerSeek(songId, positionSec) { trackPlayerAction('seek', songId, String(positionSec)) }
export function trackPlayerExport(songId, stemKey) { trackPlayerAction('export', songId, stemKey) }

export function getAnonId() { return getClientId() }
