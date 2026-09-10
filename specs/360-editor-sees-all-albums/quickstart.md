# Quickstart: Валидация фикса #76 (Редактор видит все альбомы автора)

**Дата**: 2026-09-10
**Branch**: `360-editor-sees-all-albums`
**Spec**: [spec.md](./spec.md)

Минимальный фикс фронта — добавить `Authorization: Bearer <token>` в fetch на `ZakromaAlbumsView.vue`. Бэкенд не меняется. Ниже — пошаговая валидация.

## Prerequisites

- Доступ к `karaoke-public` SPA (браузер).
- Учётная запись редактора (`SiteUser.isEditor == true`) — например, редактор, который бракует задания.
- Автор с альбомом без готовых песен (`total_song_count > 0, ready_song_count == 0`). Если такого нет — создать через `karaoke-app` (admin): добавить альбом с одной песней в статусе idStatus < 6 (например, 3 — PROCESSING).
- Альбомы с готовыми песнями для regression check (любой автор с альбомами).

## Шаги валидации

### 1. Pre-flight (до правки кода)

Проверить, что баг воспроизводится в текущей версии:

1. Залогиниться под редактором в `karaoke-public`.
2. Открыть `/zakroma/{authorId}/albums` для автора с альбомом без готовых.
3. **Ожидаемое (текущее, баг)**: альбом без готовых НЕ отображается. Видны только альбомы с готовыми.
4. Открыть DevTools → Network → посмотреть запрос `/api/public/authors/{authorId}/albums?scope=main`.
5. **Ожидаемое (текущее)**: запрос НЕ содержит заголовка `Authorization`. Только `Cookie` (если есть).
6. Открыть в браузере ответ: массив содержит только альбомы с `readySongCount > 0`.

Если шаги 1-6 воспроизводятся — баг подтверждён, фикс нужен.

### 2. Применить фикс

Сделать точечную правку `karaoke-public/src/views/ZakromaAlbumsView.vue` в методе `loadAlbums()` (строка 107):

```js
async loadAlbums() {
  this.loading = true
  this.error = null
  try {
    const headers = {}
    const token = localStorage.getItem('km_auth_token')
    if (token) headers.Authorization = `Bearer ${token}`
    const response = await fetch(
      `/api/public/authors/${this.authorId}/albums?scope=main`,
      { credentials: 'include', headers },
    )
    // ...rest unchanged...
  }
}
```

Альтернатива (вариант B) — использовать `apiGet()` из `services/api.js`, если сигнатура подходит (см. [research.md](./research.md) § R2).

### 3. Проверить линтеры

```bash
cd /home/nsa/Karaoke/karaoke-public
npm run lint:check
npx prettier --check "src/views/ZakromaAlbumsView.vue"
```

Оба должны вернуть `0 errors`. Если падают — исправить и повторить.

### 4. Пересобрать фронт

```bash
cd /home/nsa/Karaoke/karaoke-public
npm run build
cd /home/nsa/Karaoke/deploy
bash do.sh build_start_public
```

(На `nsa-i9` это допустимо без явного согласия владельца — см. `AGENTS.md § Машинно-специфичные исключения`.)

### 5. Функциональная проверка (UI)

#### Сценарий A — редактор видит все альбомы

1. Залогиниться под редактором.
2. Открыть `/zakroma/{authorId}/albums` для автора с альбомом без готовых.
3. **Ожидаемое**: альбом без готовых отображается с подписью «N песен» (где N = `totalSongCount`, например «1 песня»).
4. DevTools → Network → запрос `/api/public/authors/{authorId}/albums` → теперь содержит `Authorization: Bearer <token>`.
5. Ответ: массив содержит ВСЕ не-skip альбомы автора (включая без готовых).

#### Сценарий B — гость видит только готовые (regression)

1. Выйти из учётной записи (logout).
2. Открыть `/zakroma/{authorId}/albums`.
3. **Ожидаемое**: видны только альбомы с готовыми песнями; подпись «N готовых».
4. Альбом без готовых НЕ отображается.

#### Сценарий C — залогиненный не-редактор (regression)

1. Залогиниться под обычным зарегистрированным пользователем (НЕ редактором).
2. Открыть `/zakroma/{authorId}/albums`.
3. **Ожидаемое**: как у гостя — только альбомы с готовыми.

### 6. Проверка через curl (API-уровень)

#### Без токена (анонимный)

```bash
curl -s 'http://localhost:8897/api/public/authors/{authorId}/albums?scope=main' | jq
```

**Ожидаемое**: массив содержит только альбомы с `readySongCount > 0`. Альбомов с `readySongCount == 0` нет.

#### С токеном редактора

```bash
TOKEN=$(<editor_token>)
curl -s -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8897/api/public/authors/{authorId}/albums?scope=main' | jq
```

**Ожидаемое**: массив содержит ВСЕ альбомы (включая без готовых). У таких альбомов `readySongCount == 0, totalSongCount > 0`.

#### С невалидным токеном

```bash
curl -s -H "Authorization: Bearer invalid_xxx" \
  'http://localhost:8897/api/public/authors/{authorId}/albums?scope=main' | jq
```

**Ожидаемое**: как у анонимного (резолв токена → null → `onlyPublished = true`).

### 7. Regression: спек 017 (закрома/поиск/песни)

Бэкенд `PublicApiController.zakroma`, `authorsTiles`, `songs` используют тот же `onlyPublishedFor(request)` — проверить, что они не сломались:

1. Залогиниться под редактором.
2. Открыть `/zakroma` — видны авторы с альбомами без готовых (это работало и до фикса).
3. Открыть `/zakroma/{authorId}` — видны ВСЕ песни автора (включая недоготовые) — это работало и до фикса (по спеке 017).
4. Поиск `/search?text=...` — находит песни любого статуса (по спеке 017).

Если что-то сломалось — вероятно, мы случайно изменили `PublicApiController` (это не предполагается). `git diff` должен показать только `ZakromaAlbumsView.vue`.

## Quick checks

| Что | Команда / как |
|---|---|
| Линтер | `cd karaoke-public && npm run lint:check` |
| Prettier | `cd karaoke-public && npx prettier --check "src/views/ZakromaAlbumsView.vue"` |
| Сборка | `cd karaoke-public && npm run build` |
| Деплой на nsa-i9 | `cd deploy && bash do.sh build_start_public` |
| Логи контейнера | `docker logs --tail 50 karaoke-public` |

## Halt conditions

- Если шаг 1 (pre-flight) не воспроизводит баг → откатить фикс, переоткрыть issue #76 с новой информацией.
- Если шаг 5 (UI) не показывает правильное поведение → проверить, что fetch передаёт заголовок (DevTools → Network → Headers).
- Если шаг 6 (curl) возвращает одинаковые ответы с токеном и без → бэкенд не резолвит токен; проверить, что бэкенд поднят с актуальным кодом (`docker ps` — свежий образ).
- Если шаг 7 (regression 017) сломался → откатить правку и разобраться, почему изменение затронуло бэкенд.