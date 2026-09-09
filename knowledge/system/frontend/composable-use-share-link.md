# Composable: useShareLink

> **Домен**: system (frontend)
> **Компонента**: `karaoke-public/src/composables/useShareLink.js`
> — управление share-ссылками владельца.

## Файл

`karaoke-public/src/composables/useShareLink.js` (43 строки)

## Назначение

Управление share-ссылками **владельца** (`add-song-share-link`).
Используется из `SongView.vue` — кнопка «Временный полный доступ»
рядом с `ShareButton`.

**Бэкенд**: `/api/public/share/*` (см. [PublicShareController](../../domains/karaoke-web/components/public-controllers.md)).

## API

```javascript
export const SHARE_TTL_OPTIONS = [
  { value: 3600,   label: '1 час' },
  { value: 86400,  label: '24 часа' },
  { value: 604800, label: '7 дней' },
];

export async function createShareLink(songId, ttlSeconds, token) {
  const { status, body } = await authPost(
    `/api/public/share/${songId}/create`,
    { ttlSeconds },
    token,
  );
  return { status, body };
}

export async function getCurrentShareLink(songId, token) { ... }
export async function revokeShareLink(songId, reason, token) { ... }

export function useShareLink() {
  return { SHARE_TTL_OPTIONS, createShareLink, getCurrentShareLink, ... };
}
```

## Hot paths

- **Owner** нажимает «Временный полный доступ» → `createShareLink`.
- **Owner** нажимает «Отменить» → `revokeShareLink`.
- **Owner** проверяет текущую ссылку → `getCurrentShareLink`.

## Связь

- **PublicShareController** ([karaoke-web/public-controllers.md](../../domains/karaoke-web/components/public-controllers.md)) —
  `/api/public/share/{id}/create|mine|revoke`.
- **SongShareLinkService** ([song-share-link-service.md](../../domains/karaoke-web/components/song-share-link-service.md)) —
  реализация на бэкенде.
- **useAuth** ([composable-use-auth.md](composable-use-auth.md)) —
  передача `token` в `authPost`.

## Changelog

- **Pass 383** (2026-09-09): Initial. Автор: agent (Karaoke).