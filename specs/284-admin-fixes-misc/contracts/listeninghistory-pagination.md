# Contracts — Админка: пагинация «Истории прослушиваний»

**Feature**: [../spec.md](../spec.md) | **Plan**: [../plan.md](../plan.md)

Этот документ фиксирует **параметры пагинации** между фронтом и бэком для эндпоинта
`POST /api/listeninghistory/digest`. Эндпоинт уже существует в
`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ListeningHistoryController.kt`
и принимает параметры `page` + `pageSize` (строки 66-67). Фича фронта — прокинуть `page`
из Vuex при смене страницы и перезагрузить данные.

## 1. Запрос фронт → бэк

**Метод**: `POST`
**URL**: `/api/listeninghistory/digest`
**Content-type**: `application/x-www-form-urlencoded` (через `promisedXMLHttpRequest` —
`webvue3/src/lib/utils.js:15-47`)

### Параметры (form-urlencoded body)

| Имя               | Тип    | Обязательность | Дефолт | Описание |
|-------------------|--------|----------------|--------|----------|
| `target`          | string | optional       | `local` | `local` \| `remote` (см. AGENTS.md, dual-DB). |
| `page`            | int    | optional       | `1`   | 1-based номер страницы. Бэкенд clamp'ит `page >= 1` (ListeningHistoryController.kt:85), `offset = (safePage - 1) * safePageSize`. |
| `pageSize`        | int    | optional       | `500` | Размер страницы. Бэкенд clamp'ит `1..1000` (ListeningHistoryController.kt:86). |
| `filterUserId`    | long   | optional       | —     | Фильтр по пользователю (ListeningHistoryFilterModal). |
| `filterSongId`    | long   | optional       | —     | Фильтр по песне (ListeningHistoryFilterModal). |
| `filterLastPlayedFrom` | string | optional    | —     | `last_played_at >=`. ISO-ish (`'YYYY-MM-DD HH:MM:SS'`). |
| `filterLastPlayedTo`   | string | optional    | —     | `last_played_at <=`. |
| `sortBy`          | string | optional       | `last_played_at` | Whitelist: `last_played_at` \| `play_count` (ListeningHistoryController.kt:124-128). |
| `sortDir`         | string | optional       | `DESC` | `ASC` \| `DESC` (case-insensitive). |

### Изменения для данной фичи

Только фронт должен слать **`page`** при каждом клике по `<b-pagination>`. Бэкенд ничего
нового не получает — `page` уже принимается через `@RequestParam(required = false, defaultValue = "1")`.

**Действие** `webvue3/src/components/ListeningHistory/store.js`::`loadListeningHistoryDigest(ctx, params)`:
- Принимает `params = { page?, filterUserId?, ... }`.
- `Object.assign({}, params, { target: ctx.state.listeningHistoryTarget, page: params.page || 1 })`
  (или аналогично) → отправляется в бэкенд.
- Принимает параметр через `params.page`, fallback `1` для обратной совместимости с любым
  другим потенциальным вызовом action (на данный момент других нет).

## 2. Ответ бэк → фронт

```json
{
  "listeningHistoryDigest": [
    {
      "id": 123,
      "siteUserId": 456,
      "userEmail": "user@example.com",
      "userDisplayName": "Имя",
      "songId": 789,
      "songName": "Название песни",
      "songAuthor": "Автор",
      "songAlbum": "Альбом",
      "songYear": 2020,
      "playCount": 3,
      "lastPlayedAt": "2026-08-31 12:34:56.789"
    }
  ],
  "totalCount": 12345,
  "page": 1,
  "pageSize": 500
}
```

- `listeningHistoryDigest` — массив строк текущей страницы (уже отфильтрованный по SKIP, обогащённый song + user).
- `totalCount` — **общее число строк после SKIP-фильтрации** (т.е. сколько страниц вообще есть).
- `page`, `pageSize` — эхо от бэка (для удобства отладки).

Фронт **не парсит** `page`/`pageSize` из ответа (мы знаем текущее значение сами), но
мутация `setListeningHistoryDigestTotalCount` уже работает только с `totalCount`.

## 3. Поведение на бэке (уже реализовано)

`ListeningHistoryController.kt::digest(...)`:

1. Собирает `whereList` по `h.*` (tbl_listening_history). SKIP-фильтр НЕ здесь —
   применяется после JOIN на JVM-стороне.
2. Clamp пагинации: `safePage = max(1, page)`, `safePageSize = pageSize.coerceIn(1, 1000)`,
   `offset = (safePage - 1) * safePageSize`.
3. Грузит **всю** порцию `allLoaded` через `KaraokeDbTable.loadList(...)`.
4. Батч-JOIN к `tbl_songs` по всем `songIds` (через `Song.loadListFromDbByIds`).
5. Фильтрует на JVM: `song != null && !songHasSkipTag(song.tags)`.
6. Сортировка по `sortBy`/`sortDir` (whitelist).
7. `totalCount = filtered.size`; `pageItems = sorted.drop(offset).take(safePageSize)`.
8. Батч-JOIN к `tbl_site_users` для email (только по порции страницы).
9. Возвращает `listeningHistoryDigest` + `totalCount` + `page` + `pageSize`.

См. `ListeningHistoryController.kt:62-185`.

## 4. Поведение на фронте (новая правка)

`ListeningHistoryTable.vue`:

```js
watch: {
  digestIsLoading() {
    this.isBusy = this.digestIsLoading
  },
  currentPage(newPage, oldPage) {
    // Сохраняем страницу в store (Vuex), чтобы она восстановилась после переключения на
    // другой компонент в той же сессии.
    this.$store.commit('setListeningHistoryTableCurrentPage', newPage)
    // ТРИГГЕР ЗАГРУЗКИ с новой страницей. Защита от дребезга: на mount currentPage уже
    // равно значению из Vuex, и mount() вызывает reload() сам — здесь не диспатчим.
    if (newPage !== oldPage) {
      this.$store.dispatch('loadListeningHistoryDigest', { page: newPage })
    }
  },
},
mounted() {
  this.reload()  // первичная загрузка — передаёт currentPage (восстановленную из Vuex)
},
methods: {
  reload() {
    this.$store.dispatch('loadListeningHistoryDigest', { page: this.currentPage })
  },
  onTargetChange() {
    this.currentPage = 1
    this.reload()
  },
}
```

Почему `if (newPage !== oldPage)`:
- Vuex `listeningHistoryTableCurrentPage: 1` (initial state).
- `data() { currentPage: this.$store.getters.getListeningHistoryTableCurrentPage || 1 }`
  → `currentPage = 1` на mount (если в Vuex было 1 — Vue не триггерит watcher).
- Если в Vuex была восстановлена страница `> 1` — `data()` ставит `currentPage = N`,
  watcher сработает с `newPage = N, oldPage = 1`. В этом случае диспатч нужен (например,
  пользователь нажал F5 на странице 5 — без watcher-триггера будет показываться первая
  страница, а не пятая). **НО**: `mounted()` уже вызывает `reload()` который вызовет
  `loadListeningHistoryDigest({ page: this.currentPage })` напрямую → защита от двойного
  вызова: если watcher уже отправил запрос, `reload()` всё равно пошлёт ещё один
  (дедупликация на уровне бэка через текущий `isBusy`/`setListeningHistoryDigestIsLoading`
  не покрыта, но и не критично — 2 идентичных запроса подряд не вредят, бэк идемпотентен).
  Защита `newPage !== oldPage` снимает этот риск в большинстве случаев.

Альтернатива (более строгая): убрать вызов `reload()` из `mounted()` и положиться только
на watcher `currentPage`. Но тогда теряется явная точка входа для «обновления текущей
страницы» (например, при ошибке сети — пользователь нажал F5, watcher сработал, но из-за
сетевой ошибки запрос упал; без `reload()` нет второго шанса). Поэтому оставляем
`mounted() { this.reload() }` как есть — он сработает сразу после watcher'а и пойдёт
синхронно одним и тем же `currentPage` (идентичный запрос, бэк идемпотентен).

## 5. Acceptance на бэке

- `safePage = max(1, page)` → отрицательные/нулевые `page` → страница 1.
- `safePageSize.coerceIn(1, 1000)` → большие `pageSize` → clamp до 1000.
- Если `offset >= totalCount` → `drop(offset).take(safePageSize)` = пустой массив.
  Это **не ошибка** — фронт получит `listeningHistoryDigest: []` и пустую таблицу.
- Фильтрация по SKIP и JOIN — без изменений.

## 6. Что НЕ меняется в этом тикете

- Параметр `sortBy`/`sortDir` — фронт уже НЕ передаёт их (ListeningHistory/store.js:67),
  бэк использует дефолты. Сортировка по `lastPlayedAt` через `<b-table v-model:sort-by>`
  остаётся **клиентской** (только для текущей страницы).
- Параметр `pageSize` — фронт НЕ передаёт (используется дефолт `500` бэка). Если потребуется
  сделать pageSize клиентским — это отдельная фича.
- F5 cross-session персистенция через `setWebvueProp`/`getWebvueProp` —
  см. `spec.md` A-006 и `plan.md` секцию «Polish». На данный момент page восстанавливается
  только в **пределах текущей сессии** (Vuex in-memory). Если в `/speckit.implement`
  обнаружится, что нужна F5 cross-session персистенция — это подзадача
  `ListeningHistory/store.js` (добавить `setWebvueProp`/`hydrate`).
