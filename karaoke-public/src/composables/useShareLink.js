import { authPost, authGet } from '../services/authApi'

// Управление share-ссылками владельца (add-song-share-link). Используется из SongView.vue —
// кнопка «Временный полный доступ» рядом с ShareButton. Бэкенд: /api/public/share/* (см.
// PublicShareController.kt).

export const SHARE_TTL_OPTIONS = [
  { value: 3600, label: '1 час' },
  { value: 86400, label: '24 часа' },
  { value: 604800, label: '7 дней' },
]

export async function createShareLink(songId, ttlSeconds, token) {
  const { status, body } = await authPost(
    `/api/public/share/${songId}/create`,
    { ttlSeconds },
    token,
  )
  return { status, body }
}

export async function getCurrentShareLink(songId, token) {
  const { status, body } = await authGet(`/api/public/share/mine/${songId}?target=`, token)
  return { status, body }
}

export async function revokeShareLink(songId, reason, token) {
  const { status, body } = await authPost(
    `/api/public/share/mine/${songId}/revoke`,
    { reason },
    token,
  )
  return { status, body }
}

export function useShareLink() {
  return {
    SHARE_TTL_OPTIONS,
    createShareLink,
    getCurrentShareLink,
    revokeShareLink,
  }
}
