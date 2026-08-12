# Research: Бейдж «новые альбомы» в пункте меню «Авторы»

**Feature**: 176-authors-new-albums-badge
**Date**: 2026-08-12
**Spec**: [spec.md](./spec.md)

## Сводка

Все ключевые архитектурные решения для фичи уже приняты в результате исследования существующего кода (см. секцию «Контекст исследуемой кодовой базы» ниже). Никаких `NEEDS CLARIFICATION` не остаётся — спек однозначен, реализация следует существующим паттернам проекта без новых архитектурных решений.

## Решения

### D-1. Где разместить endpoint на бэкенде

**Решение**: Новый метод `apisAuthorsWithNewAlbumCount` в существующем `karaoke-app/.../controllers/ApiController.kt` рядом с `apisAuthorsDigest` (строка 6040). Использовать `@PostMapping("/authors/withnewalbumcount")` + `@ResponseBody fun ... : Int`.

**Обоснование**:
- Все author-related админ-endpoints уже собраны в `ApiController` (`/authors/updateauthor`, `/authors/authorsdigests`, `/songs/coauthors/list`, etc.) — не плодим новый контроллер ради одного метода.
- Spring-бин `storageService` / `storageApiClient` уже инжектится в `ApiController` (через конструктор) — переиспользуем.
- Паттерн «count-endpoint» уже есть: `ChatController.unreadCount` (`@PostMapping("/unreadcount") fun unreadCount(...): Int`), `SongEditorController.submittedCount` (`@PostMapping("/submittedcount") fun submittedCount(...): Int`). Новый endpoint = тот же шаблон.

**Альтернативы отклонены**:
- Создать отдельный `AuthorsAdminController` — избыточно, нарушает консистентность с `ApiController` (где уже живут все admin-эндпоинты по сущностям).
- Добавить в существующий `ChatController` или `SongEditorController` — неверная сущность, не должно смешиваться.

### D-2. Как считать `count(*)` — прямой SQL или `Author.loadList`

**Решение**: Прямой raw-SQL `SELECT COUNT(*) FROM tbl_authors WHERE watched = true AND (ym_id <> '' OR vk_id <> '') AND (last_album_ym <> last_album_processed OR last_album_vk <> last_album_processed)` через новый companion-метод `Author.countWithNewAlbum(database: KaraokeConnection): Int`.

**Обоснование**:
- Условие `haveNewAlbum` — простое выражение на 5 колонках, идеально для SQL WHERE.
- `Author.loadList(whereArgs = mapOf("haveNewAlbum" to "+"))` инстанцирует полные объекты `Author` через reflection (`KaraokeDbTable.loadList`) + пробегает по ним в памяти — дорого для 18k+ записей.
- Прямой SQL — на два порядка быстрее: один `Index Scan` / `Seq Scan` по таблице, никаких POJO, никакого diff-hash.
- Условие WHERE — точная копия `Author.getWhereList["haveNewAlbum=+"]` (строки 170-178 `Author.kt`) и computed property `haveNewAlbum` (строки 94-97). DRY обеспечивается копи-пастой SQL-условия в один комментарий-ссылку на `Author.haveNewAlbum`.

**Альтернативы отклонены**:
- `Author.loadList(...).count { it.haveNewAlbum }` — инстанцирует 18k POJO на каждый tick (раз в 20 сек), неоптимально.
- Полная загрузка DTO (`Author.toDTO()`) — лишняя работа с картинками/URL, не нужна для счётчика.

### D-3. Нет параметра `target` (local/remote)

**Решение**: Endpoint НЕ принимает `target` (в отличие от `/api/chat/unreadcount`, где есть `target: String?`). Использует `WORKING_DATABASE` напрямую (как `apisAuthorsDigest`).

**Обоснование**:
- Авторская БД живёт на одной БД: `WORKING_DATABASE = Connection.local()` (Constants.kt:204). Sync `Author` (LOCAL→SERVER) делает записи одинаковыми на обеих БД, но логический «источник истины» для админ-операций — LOCAL.
- Существующий `/api/authors/authorsdigests` (тот же контроллер, та же сущность) — тоже без `target`. Консистентность.
- Чаты — другое дело (PROD-only, `chatTarget` нужен для отладки в karaoke-public → karaoke-web).

### D-4. Где разместить новый Vuex-action и state

**Решение**: Добавить в `webvue3/src/components/Authors/store.js`:
- `state.authorsWithNewAlbumCount: 0`
- `getters.getAuthorsWithNewAlbumCount(state)`
- `mutations.setAuthorsWithNewAlbumCount(state, count)`
- `actions.loadAuthorsWithNewAlbumCount(ctx)` — `POST /api/authors/withnewalbumcount` → `commit('setAuthorsWithNewAlbumCount', parseInt(data, 10) || 0)`

**Обоснование**:
- `Authors/store.js` уже существует и хранит состояние авторов (`authorsDigest`, `authorsDigestIsLoading`, `authorsTableCurrentPage`). Логически бейдж «новые альбомы» относится к авторам.
- Альтернатива — отдельный `Stats/store.js` (используется для глобальных счётчиков главной страницы). Но бейдж тесно связан с сущностью «Авторы» (как `Chat/store.js` хранит `chatUnreadTotal`, `SongEditor/store.js` хранит `submittedAssignmentsCount`).
- `chatUnreadTotal` живёт в `Chat/store.js`, не в `Stats/store.js` — следуем этому паттерну.

### D-5. Polling в `App.vue` — где именно

**Решение**: В существующем `export default { ... mounted() {...}, beforeUnmount() {...} }` блоке `App.vue` (строки 180-251):
- Добавить `const AUTHORS_NEW_ALBUMS_POLL_INTERVAL_MS = 20000` рядом с `CHAT_UNREAD_POLL_INTERVAL_MS` и `SONGEDITOR_SUBMITTED_POLL_INTERVAL_MS` (строки 135, 138).
- В `mounted()` после `loadSubmittedAssignmentsCount` добавить `this.$store.dispatch('loadAuthorsWithNewAlbumCount')` + `setInterval`.
- В `beforeUnmount()` добавить `clearInterval` для нового таймера.
- Добавить `data()` поле `authorsWithNewAlbumPollTimer: null` (рядом с `chatUnreadPollTimer` и `submittedAssignmentsPollTimer`).

**Обоснование**:
- Существующий `App.vue` уже паттерн «polling бейджа» — переиспользуем.
- Симметрично `chatUnreadPollTimer`/`submittedAssignmentsPollTimer` — легко ревьюить, легко понять.

### D-6. CSS — `authors-nav-link` и `authors-nav-badge`

**Решение**: Скопировать стили `.chat-nav-link` и `.chat-nav-badge` (App.vue:733-748) как `.authors-nav-link` и `.authors-nav-badge`. Не выносить общий класс `.nav-badge` — вне scope фичи, и явная копия точно матчит существующие.

**Обоснование**:
- Спека FR-002 явно требует «тот же цвет, размер, форма». Копи-паста CSS — самый прямой путь.
- Альтернатива (общий `.nav-badge` + `.nav-badge--chat|.nav-badge--songeditor|.nav-badge--authors`) — рефакторинг вне scope фичи, может сломать другие места через специфичность/каскад.

### D-7. Шаблон `<router-link>` — копировать как есть

**Решение**: Заменить
```html
<router-link class="nav-link" to="/authors">Авторы</router-link>
```
на
```html
<router-link class="nav-link authors-nav-link" to="/authors">
  Авторы
  <span v-if="authorsWithNewAlbumCount > 0" class="authors-nav-badge">{{ authorsWithNewAlbumCount }}</span>
</router-link>
```

**Обоснование**: Буквальный mirror `chat-nav-link` (App.vue:39-44) и `songeditor-nav-link` (App.vue:66-72).

## Контекст исследуемой кодовой базы

| Что | Где | Что взяли |
|-----|-----|-----------|
| Pattern chat-badge | `webvue3/src/App.vue:39-44, 733-748` | Шаблон router-link + CSS |
| Pattern songeditor-badge | `webvue3/src/App.vue:66-72, 750-765` | Тот же шаблон, плюс дефолтный `defaultTarget` |
| Vuex-action `loadChatUnreadCount` | `webvue3/src/components/Chat/store.js:192-205` | Шаблон polling-action |
| Vuex-action `loadSubmittedAssignmentsCount` | `webvue3/src/components/SongEditor/store.js:254-265` | Шаблон с `parseInt(data, 10) || 0` |
| Backend `countSubmitted` | `karaoke-app/.../controllers/SongEditorController.kt:599-606` | Шаблон POST controller |
| Backend `countUnreadFromUsers` | `karaoke-app/.../model/SiteChatMessage.kt:271-282` | Шаблон raw-SQL `SELECT COUNT(*)` |
| Model `Author.haveNewAlbum` | `karaoke-app/.../model/Author.kt:94-97` | Источник истины для условия |
| `Author.getWhereList` для `haveNewAlbum` | `karaoke-app/.../model/Author.kt:170-178` | SQL-условие для WHERE (зеркало `haveNewAlbum`) |
| Existing endpoint `/api/authors/authorsdigests` | `karaoke-app/.../controllers/ApiController.kt:6040-6080` | Шаблон controller-метода без `target` |

## Открытые вопросы

**Нет**. Все технические решения определены существующими паттернами проекта. Дополнительных NEEDS CLARIFICATION не требуется — спека однозначна.
