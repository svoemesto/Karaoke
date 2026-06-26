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
