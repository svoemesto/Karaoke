import { apiPost } from './api'
import { getAnonId } from './clientId'
import { useAuth } from '../composables/useAuth'

// getAnonId вынесен в services/clientId.js (общий с api.js). Реэкспортируем, т.к. на него
// исторически завязаны внешние потребители (usePlayerAccess.js).
export { getAnonId }

// useAuth() — module-level singleton ref (см. composables/useAuth.js), безопасно читать вне
// setup()-контекста компонента: это просто разделяемый реактив, а не Vue DI (inject()).
function authHeader() {
  const { token } = useAuth()
  return token.value ? { Authorization: `Bearer ${token.value}` } : undefined
}

export function trackLinkToSocialNetwork(linkName) {
  apiPost(
    '/api/public/events',
    { eventType: 'clickToLink', linkType: 'linkToSocialNetwork', linkName, anonId: getAnonId() },
    authHeader(),
  ).catch(() => {})
}

export function trackLinkToSong(linkName, songId, songVersion) {
  apiPost(
    '/api/public/events',
    {
      eventType: 'clickToLink',
      linkType: 'linkToSong',
      linkName,
      songId,
      songVersion,
      anonId: getAnonId(),
    },
    authHeader(),
  ).catch(() => {})
}

export function trackPlay(songId, songVersion) {
  apiPost(
    '/api/public/events',
    { eventType: 'play', songId, songVersion, anonId: getAnonId() },
    authHeader(),
  ).catch(() => {})
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
      clientId: getAnonId(),
    })
  } catch {
    return null
  }
}

// Онлайн-плеер: открытие/play/pause/seek/export/progress/ended — тихая фоновая телеметрия, тем же
// fire-and-forget паттерном, что и остальные функции здесь (ошибка сети проглатывается молча,
// в UI никак не отражается).
function trackPlayerAction(linkType, songId, linkName) {
  const payload = { eventType: 'player', linkType, songId, anonId: getAnonId() }
  if (linkName !== undefined) payload.linkName = linkName
  apiPost('/api/public/events', payload, authHeader()).catch(() => {})
}

export function trackPlayerPlay(songId) {
  trackPlayerAction('play', songId)
}
export function trackPlayerPause(songId) {
  trackPlayerAction('pause', songId)
}
export function trackPlayerSeek(songId, positionSec) {
  trackPlayerAction('seek', songId, String(positionSec))
}
export function trackPlayerExport(songId, stemKey) {
  trackPlayerAction('export', songId, stemKey)
}
export function trackPlayerProgress(songId, percent) {
  trackPlayerAction('progress', songId, String(percent))
}
export function trackPlayerEnded(songId) {
  trackPlayerAction('ended', songId)
}

// UI-действие: навигация по маршрутам, смена темы, глубина скролла. eventType='ui',
// linkType=подтип (navigate|theme|scroll), linkName=деталь (маршрут/тема/процент).
export function trackUi(subtype, detail, songId) {
  const payload = {
    eventType: 'ui',
    linkType: subtype,
    linkName: String(detail),
    anonId: getAnonId(),
  }
  if (songId !== undefined && songId !== null) payload.songId = songId
  apiPost('/api/public/events', payload, authHeader()).catch(() => {})
}

// Время на странице (секунды видимости). Отправляется в момент ухода со страницы/скрытия вкладки,
// поэтому НЕ через обычный XHR (может не успеть), а через fetch с keepalive. Именно fetch, а не
// navigator.sendBeacon: beacon не умеет ставить заголовок Authorization, и событие потеряло бы
// site_user_id залогиненного пользователя. keepalive сохраняет заголовки и переживает выгрузку.
export function trackPageEngagement(page, seconds, songId) {
  if (!seconds || seconds < 1) return
  const params = new URLSearchParams()
  params.set('eventType', 'engagement')
  params.set('page', page)
  params.set('linkName', String(Math.round(seconds)))
  params.set('anonId', getAnonId())
  if (songId !== undefined && songId !== null) params.set('songId', String(songId))
  const headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
  const auth = authHeader()
  if (auth) headers.Authorization = auth.Authorization
  try {
    fetch('/api/public/events', {
      method: 'POST',
      body: params.toString(),
      headers,
      keepalive: true,
    }).catch(() => {})
  } catch {
    /* игнорируем — телеметрия не должна ломать выгрузку страницы */
  }
}
