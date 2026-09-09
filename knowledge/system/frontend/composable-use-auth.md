# Composable: useAuth

> **Домен**: system (frontend)
> **Компонента**: `karaoke-public/src/composables/useAuth.js` —
> module-level singleton для auth state.

## Файл

`karaoke-public/src/composables/useAuth.js` (68 строк)

## Назначение

**Module-level singleton** (паттерн `useDesign.js`) — одно состояние
на всё приложение. В отличие от `useDesign`, здесь — **auth state**.

## State (module-level)

```javascript
const token = ref(localStorage.getItem('km_auth_token') || '')
const user = ref(JSON.parse(localStorage.getItem('km_auth_user') || 'null'))
```

**`localStorage`, не `sessionStorage`**: в отличие от одноразового
токена плеера (`kp_token`), сессия личного кабинета должна
переживать закрытие вкладки.

## Ключи

- `km_auth_token` — JWT-токен.
- `km_auth_user` — JSON SiteUser (для отображения в UI).

## Hot paths

- **`/api/public/auth/me`** — проверка токена + обновление user.
- **Auto-refresh каждые 5 минут** (specs/161-fix-header-stale-premium-status):
  периодический `fetchMe()` для обновления premium-статуса в шапке.

## Архитектурные решения

### Решение 1: Module-level singleton

НЕ `useAuth()` создаёт каждый раз новое state. Все вызовы
`useAuth()` возвращают **те же самые refs** (`token`, `user`).

### Решение 2: Auto-refresh (FR-005 из specs/161)

Без авто-обновления шапка сайта показывает premium-статус **на
момент логина** сколь угодно долго: конец подписки не отражается в
`AuthStatusWidget`, пока пользователь случайно не откроет `/account`.

**`AUTO_REFRESH_INTERVAL_MS = 5 * 60 * 1000`** (5 минут).

### Решение 3: Ошибка сети ≠ потеря сессии

В `fetchMe()` сетевой сбой (`authGet` реджектит промис на
`xhr.onerror`) — оставляем `user/token` **как есть**: временная
недоступность проверки не должна ни ронять фоновый таймер, ни
**изображать premium-статус, которого на самом деле нет**.

### Решение 4: 401 → clearSession

`status === 401` → `clearSession()`. Сессия истекла или
невалидна.

## Domain Invariants

1. **`token` хранится в `localStorage`**, не в cookie — JWT-токен.
2. **`user` хранится в `localStorage`** как JSON.
3. **Module-level singleton** — все компоненты видят **один и тот же
   state**.
4. **Auto-refresh** — 5 минут (FR-005).

## Связь

- **Identity** ([identity domain](../../domains/identity/domain.md)) —
  `SiteUser`.
- **PublicAuthController** ([karaoke-web/public-controllers.md](../../domains/karaoke-web/components/public-controllers.md)) —
  `/api/public/auth/me`.
- **AuthHeader** (UI-компонент) — отображает `user`.

## Известные TODO

- [ ] **`fetchMe()`** — полный contract.
- [ ] **JWT** — алгоритм подписи, expiration.
- [ ] **Refresh token** — есть ли (vs только access token)?

## Changelog

- **Pass 383** (2026-09-09): Initial. Автор: agent (Karaoke).