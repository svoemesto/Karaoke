# Contract: Public Account `/me` endpoint

**Backend**: `karaoke-web/.../controllers/PublicAccountController`
**Frontend**: `karaoke-public/src/composables/useAuth`
**Spec**: [../spec.md](../spec.md) FR-010, FR-003

## Endpoint

### `GET /api/public/account/me`

**Изменения**: DTO-ответ получает новое поле `canWorkWithSkipped`.
Никаких изменений в UI (по выбору пользователя в /speckit.specify,
Q1: «Только в webvue3 (админ)» — поле НЕ рендерится в `AccountView.vue`).

**Ответ** (добавляется поле):
```json
{
  "id": 42,
  "email": "user@example.com",
  "displayName": "Иван Петров",
  "isEditor": true,
  "canSelfAssignTasks": true,
  "canWorkWithSkipped": true,
  "isEffectivePremium": false,
  "personalDiscountPercent": 0,
  ...
}
```

## Зачем прокидывать, если UI не рендерит?

1. **FR-010**: поле прокидывается через DTO «для будущих фич». Текущая
   фича не использует его в `AccountView`, но если в будущем потребуется
   self-toggle (см. A-001) — DTO уже готов.
2. **Бейдж SKIP в UI** (FR-011): на страницах «Закрома»,
   `AuthorPlaylistView`, `SongView` поле `canWorkWithSkipped` нужно для
   условия рендера бейджа (`v-if="user?.canWorkWithSkipped"`). Хотя
   `user` в karaoke-public composable уже загружается из `/me` —
   добавление поля автоматически делает его доступным.
3. **Share-link кнопка**: для SKIP-песен кнопка скрывается через
   `v-if="!songHasSkippedTag(song.tags)"` — не зависит от
   `canWorkWithSkipped`, так как share-link запрещён для всех (FR-012).

## Изменения в composable

`useAuth()` в `karaoke-public/src/composables/useAuth.js` (или `.ts`)
НЕ требует изменений — он уже хранит `user.value` как реактивный объект
из ответа `/me`. Vue автоматически подхватит новое поле при следующем
обновлении.

## Анонимный пользователь

Без `Authorization`-заголовка `/me` возвращает 401 (или пустой объект).
В этом случае `user?.canWorkWithSkipped` будет `undefined`, и
`!!user?.canWorkWithSkipped` === `false` — UI корректно скроет бейджи.

## Тестовые сценарии

- **AC-4.1**: Залогиненный редактор Иван запрашивает `/me` → ответ
  содержит `canWorkWithSkipped: true` (если админ выставил).
- **AC-4.2**: Залогиненный обычный пользователь запрашивает `/me` →
  ответ содержит `canWorkWithSkipped: false`.
- **AC-4.3**: Анонимный пользователь запрашивает `/me` → 401, в UI
  бейджи SKIP не отображаются.