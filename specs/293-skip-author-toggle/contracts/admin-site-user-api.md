# Contract: Admin SiteUser API (webvue3)

**Backend**: `karaoke-app` (`SiteUsersController`)
**Frontend**: `webvue3/src/components/SiteUsers/`
**Spec**: [../spec.md](../spec.md) FR-003, FR-004, FR-005

## Endpoints

Все endpoints уже существуют; изменения — только в JSON-payload (DTO
получает новое поле `canWorkWithSkipped`).

### `GET /api/site-users` — список пользователей

**Ответ** (без изменений в структуре, добавляется `canWorkWithSkipped`):
```json
{
  "id": 42,
  "email": "user@example.com",
  "displayName": "Иван Петров",
  "isEditor": true,
  "canSelfAssignTasks": true,
  "canWorkWithSkipped": false,
  "isBanned": false,
  "personalDiscountPercent": 0,
  "maxFavorites": 0,
  "maxPlaylists": 0,
  "maxPlaylistItems": 0,
  ...
}
```

### `GET /api/site-users/{id}` — карточка пользователя

**Ответ**: тот же DTO с заполненным `canWorkWithSkipped`.

### `POST /api/site-users` (или `PUT`) — сохранение

**Тело запроса** (добавляется `canWorkWithSkipped`):
```json
{
  "id": 42,
  "email": "user@example.com",
  "displayName": "Иван Петров",
  "isEditor": true,
  "canSelfAssignTasks": true,
  "canWorkWithSkipped": true,
  ...
}
```

**Ответ**: 200 OK с обновлённым DTO.

**Ошибки**:
- `400 Bad Request` — некорректный email (стандартная валидация `SiteUserDto`).
- `404 Not Found` — пользователь не найден (для PUT по id).

## UI (webvue3)

### `SiteUserEdit.vue` — новая секция после `canSelfAssignTasks`

```vue
<div class="label-and-input">
  <div class="label">Может работать со SKIP-авторами и песнями:</div>
  <label class="sue-checkbox-label">
    <input v-model="siteUserCurrent.canWorkWithSkipped" type="checkbox" />
    <span class="sue-hint">
      (Снимает фильтр SKIP в «Закромах», истории прослушиваний, share-link и
      OG-SEO: редактор видит песни/авторов с тегом SKIP или
      <code>tbl_authors.skip = true</code>; бейдж «SKIP» отображается в UI;
      share-link для SKIP-песен всё равно запрещён)
    </span>
  </label>
</div>
```

### `SiteUsersTable.vue` — новая колонка

В `columns` массив:
```js
{
  key: 'canWorkWithSkipped',
  label: 'SKIP-доступ',
  sortable: true,
  formatter: (value) => value ? '✓' : '',
}
```

В template — добавить ячейку `cell(canWorkWithSkipped)="data"`:
```vue
<template #cell(canWorkWithSkipped)="data">
  <span :style="{ color: data.value ? 'darkgreen' : 'gray' }">
    {{ data.value ? '✓' : '' }}
  </span>
</template>
```

## Поведение

- `canWorkWithSkipped` редактируется только админом в `webvue3`
  (аналогично `canSelfAssignTasks`).
- Изменение отражается у пользователя на следующем HTTP-запросе
  (см. [../research.md §R1](../research.md#r1-паттерн-передачи-флага-в-runtime--siteuserresolver-без-нового-bean)
  и [../research.md §R8](../research.md#r8-race-condition--админ-меняет-флаг-во-время-активной-сессии)).
- `null` в payload = не менять поле (стандартный паттерн `SiteUsersController.kt:118`).

## Тестовые сценарии

- **AC-1.1**: Админ открывает карточку редактора → видит чекбокс
  «Может работать со SKIP-авторами и песнями» в позиции после
  «Может сам назначать себе задания». Состояние чекбокса совпадает с
  `canWorkWithSkipped` в БД.
- **AC-1.2**: Админ ставит галочку, нажимает «Сохранить» → DTO
  содержит `canWorkWithSkipped: true`, БД обновляется, перезагрузка
  страницы показывает галочку активной.
- **AC-1.3**: Админ снимает галочку → DTO содержит
  `canWorkWithSkipped: false`, БД обновляется.
- **AC-1.4**: В таблице пользователей колонка «SKIP-доступ» отображает
  ✓ / пусто для всех пользователей согласно БД.