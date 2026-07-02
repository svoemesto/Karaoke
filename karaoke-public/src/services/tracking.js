import { apiPost } from './api'

export function trackLinkToSocialNetwork(linkName) {
  apiPost('/api/public/events', { eventType: 'clickToLink', linkType: 'linkToSocialNetwork', linkName }).catch(() => {})
}

export function trackLinkToSong(linkName, songId, songVersion) {
  apiPost('/api/public/events', { eventType: 'clickToLink', linkType: 'linkToSong', linkName, songId, songVersion }).catch(() => {})
}

export function trackPlay(songId, songVersion) {
  apiPost('/api/public/events', { eventType: 'play', songId, songVersion }).catch(() => {})
}

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
