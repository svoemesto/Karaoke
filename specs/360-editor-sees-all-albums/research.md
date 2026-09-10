# Research: Редактор видит все альбомы автора (issue #76)

**Дата**: 2026-09-10
**Spec**: [spec.md](./spec.md)
**Branch**: `360-editor-sees-all-albums`

## R1. Подтверждённая root cause (из кода)

### R1.1. Бэкенд — bypass `isEditor` УЖЕ реализован

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Album.kt:533-561`

```kotlin
fun loadAlbumTilesWithCounts(
    authorId: Long,
    onlyPublished: Boolean,
    includeSkipped: Boolean = false,
    database: KaraokeConnection,
): List<Album> {
    // ...
    val sql = buildString {
        append("SELECT id, author_id, year, name, album_type, sort_order, description, short_description, warning, ")
        append("total_song_count, ready_song_count ")
        append("FROM $TABLE_NAME ")
        append("WHERE author_id = ? ")
        if (onlyPublished) {
            append("AND ready_song_count > 0 ")
        }
        append("ORDER BY year ASC NULLS LAST, name ASC")
    }
    // ...
}
```

KDoc явно ссылается на FR-011 спеки #356:
> `onlyPublished=false` (редактор): ВСЕ альбомы автора (без фильтра по счётчику) — issue #70 явно: «Для обычных редакторов альбом показывается в любом случае и кол-во песен это общее количество».

**Файл**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:283`

```kotlin
private fun onlyPublishedFor(request: HttpServletRequest): Boolean =
    siteUserResolver.resolve(request)?.isEditor != true
```

**Файл**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:403-426`

```kotlin
@GetMapping("/authors/{authorId}/albums")
fun authorAlbums(
    @PathVariable authorId: Long,
    @RequestParam(required = false, defaultValue = "main") scope: String?,
    request: HttpServletRequest,
): List<AlbumTilePublicDto> {
    val onlyPublished = onlyPublishedFor(request)
    val canSeeSkipped = siteUserResolver.resolve(request)?.canWorkWithSkipped ?: false
    return getCachedAlbumsTiles(scope ?: "main", authorId, onlyPublished, canSeeSkipped) {
        val authorEntity = Author.getAuthorById(authorId, WORKING_DATABASE, KSS_APP, SAC_APP)
        val authorName = authorEntity?.author ?: ""
        val albums = Album.loadAlbumTilesWithCounts(
            authorId = authorId,
            onlyPublished = onlyPublished,
            includeSkipped = canSeeSkipped,
            database = WORKING_DATABASE,
        )
        albums.map { album -> AlbumTilePublicDto.fromAlbum(album, authorName) }
    }
}
```

**Decision**: бэкенд корректен. Сигнатура `loadAlbumTilesWithCounts(authorId, onlyPublished, ...)` принимает нужный флаг. Контроллер передаёт его.

**Rationale**: спека #356 была реализована в Pass 360 (см. `docs/features/zakroma-albums-by-author.md`). KDoc модели явно ссылается на FR-011 спеки #356. Никаких бэкенд-правок не требуется.

### R1.2. Резолв токена — работает только через `Authorization`-заголовок

**Файл**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SiteUserResolver.kt:24-28`

```kotlin
fun resolve(request: HttpServletRequest): SiteUser? {
    val header = request.getHeader("Authorization") ?: return null
    val token = header.removePrefix("Bearer ").trim().takeIf { it.isNotBlank() } ?: return null
    return siteUserTokenService.resolveToken(token, WORKING_DATABASE)
}
```

KDoc явно фиксирует:
> Опциональный (не кидающий 401) резолв SiteUser по Authorization-заголовку — общий для всех мест, где нужно «если пользователь залогинен, вот кто это» без блокировки анонимных запросов.

**Decision**: контракт резолвера не трогаем — он используется многими контроллерами и менять его = рискованный breaking change.

**Rationale**: `Authorization: Bearer <token>` — стандартный паттерн, описанный в спеке 017 для `/api/public/zakroma` и `/api/public/authors-tiles`. Cookie не используется (намеренно — спека 017 контракт с bearer).

### R1.3. Фронт — `ZakromaAlbumsView.vue` НЕ передаёт заголовок

**Файл**: `karaoke-public/src/views/ZakromaAlbumsView.vue:107-126`

```js
async loadAlbums() {
  this.loading = true
  this.error = null
  try {
    const response = await fetch(`/api/public/authors/${this.authorId}/albums?scope=main`, {
      credentials: 'include',
    })
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`)
    }
    const data = await response.json()
    this.albums = Array.isArray(data) ? data : []
  } catch (e) {
    console.error('[ZakromaAlbumsView] loadAlbums failed:', e)
    this.error = `Не удалось загрузить альбомы: ${e.message}`
    this.albums = []
  } finally {
    this.loading = false
  }
}
```

`credentials: 'include'` — отправляются cookie. Но `SiteUserResolver` не смотрит на cookie, только на `Authorization`. Поэтому для этого эндпоинта `onlyPublished == true` всегда, даже если пользователь залогинен.

**Decision**: добавить `Authorization: Bearer <token>` в fetch, взяв токен из `localStorage.getItem('km_auth_token')` (по аналогии с другими fetch'ами).

**Rationale**: 
- Это единственный fetch во всём `karaoke-public/src/` без заголовка. Подтверждено grep'ом `fetch.*authors.*albums|api/public/authors` (9 результатов, все кроме `ZakromaAlbumsView.vue` либо используют `apiGet()` из `services/api.js:16-17`, либо не относятся к нашему эндпоинту).
- Паттерн уже отработан: `useZakromaStreamProgress.js:130-150` явно комментирует «Composer.authHeader(): для залогиненного редактора шлём Bearer» — это критично для фичи 017 (bypass `id_status >= 6`).

## R2. Паттерны исправления (варианты)

### Вариант A: использовать существующий `apiGet()` helper

**Файл**: `karaoke-public/src/services/api.js:14-22`

```js
function authHeader() {
  const t = localStorage.getItem('km_auth_token')
  return t ? { Authorization: `Bearer ${t}` } : undefined
}

export async function apiGet(url, params = {}) {
  // ...уже умеет добавлять authHeader...
}
```

**Плюсы**:
- Унификация подхода (другие 8+ запросов через `apiGet`).
- Если завтра добавится CSRF-токен или другой header — фикс один раз в `api.js`.
- Меньше кода, чем inline.

**Минусы**:
- Нужно проверить сигнатуру `apiGet(url, params)` — поддерживает ли query-параметры и `credentials: 'include'`.
- Если `apiGet` не возвращает сырой JSON-массив (например, оборачивает в envelope) — нужна адаптация.

### Вариант B: inline-заголовок в `fetch

```js
const headers = {}
const token = localStorage.getItem('km_auth_token')
if (token) headers.Authorization = `Bearer ${token}`
const response = await fetch(
  `/api/public/authors/${this.authorId}/albums?scope=main`,
  { credentials: 'include', headers },
)
```

**Плюсы**:
- Минимальное изменение, не трогает сигнатуру fetch.
- Очевидное соответствие существующему коду (`StemJobsView.vue:455` делает inline).

**Минусы**:
- Дублирование паттерна (ещё одно место, где надо не забыть про токен).

### R2.3. Решение

**Decision**: выбрать **вариант A** (apiGet), если сигнатура подходит. Иначе — **вариант B** (inline). Конкретное решение принимается на стадии `/speckit.implement` после чтения `services/api.js` целиком.

**Rationale**: вариант A унифицирует подход. Если сигнатура не подходит — вариант B как fallback. Документация (FR-006 спеки) говорит: «Никаких новых механизмов авторизации» — это явно исключает новый helper.

## R3. Альтернативы, отклонённые

### R3.1. Изменить `SiteUserResolver` для поддержки cookie

**Отклонено**: рискованный breaking change. `SiteUserResolver` используется во многих контроллерах; добавление cookie-source может дать сайд-эффекты (например, для `/api/public/account/**` уже есть отдельный `SiteAuthInterceptor` с cookie-auth, дублирование логики).

**Альтернатива лучше**: поправить только одно место, где fetch не передаёт заголовок.

### R3.2. Использовать `apiGet()` из `useZakromaStreamProgress.js` (custom fetch внутри composable)

**Отклонено**: `useZakromaStreamProgress.js` — это composable для SSE-стрима, а не обычный GET-запрос. Переиспользовать его для обычного запроса = нецелевое использование.

### R3.3. Добавить `Authorization`-заголовок через Vue-router middleware

**Отклонено**: `Authorization` header — нельзя поставить в middleware для конкретных fetch'ов из браузера, только на уровне отдельного fetch.

## R4. Skip-фильтр (FR-008 спеки) — НЕ входит в этот issue

**Справка**: `Album.kt:517-518` явно фиксирует:

> Skip-фильтр: в `tbl_albums` сейчас нет колонки `skip` (Pass 357), фильтрация не выполняется. `includeSkipped` зарезервирован на будущее.

**Decision**: FR-008 спеки «skip-альбомы скрыты для всех» — НЕ реализуется в этом issue. Issue #76 — про видимость для редактора, не про skip.

**Rationale**: минимальный фикс должен решать только issue #76. Skip — отдельная задача (Pass 357 ещё не добавил колонку `skip` в `tbl_albums`).

## R5. Совместимость

| Сценарий | Поведение до фикса | Поведение после фикса | OK? |
|---|---|---|---|
| Анонимный GET `/api/public/authors/{id}/albums` | `onlyPublished=true` → только `ready_song_count > 0` | `onlyPublished=true` (заголовок не отправляется) → то же | ✅ |
| Залогиненный (не редактор) GET | `onlyPublished=true` → то же | `onlyPublished=true` (isEditor=false) → то же | ✅ |
| Залогиненный редактор GET | `onlyPublished=true` (баг!) → только `ready_song_count > 0` | `onlyPublished=false` (isEditor=true) → все альбомы | ✅ FIX |
| Невалидный/просроченный токен | `onlyPublished=true` → то же | `Authorization` отправляется, но резолв вернёт null → `onlyPublished=true` → то же | ✅ |

**Decision**: backward compatible. Никаких изменений в поведении для не-редакторов.

## R6. Что проверить перед merge

1. `cd karaoke-public && npm run lint:check` — линтер без warnings.
2. `cd karaoke-public && npx prettier --check "src/views/ZakromaAlbumsView.vue"` — prettier OK.
3. `cd karaoke-public && npx eslint src/views/ZakromaAlbumsView.vue` — eslint OK.
4. Ручная проверка: войти под редактором → `/zakroma/{id}/albums` → видны все альбомы → подпись «N песен».
5. Ручная проверка: войти под гостем → `/zakroma/{id}/albums` → видны только альбомы с готовыми → подпись «N готовых».
6. `curl -H "Authorization: Bearer <editor_token>" /api/public/authors/{id}/albums` → массив содержит все альбомы.
7. `curl /api/public/authors/{id}/albums` (без токена) → массив содержит только альбомы с готовыми (regression check).

## R7. Что НЕ проверять

- Бэкенд не меняется → `ktlintCheck`, bootJar, рестарт контейнера `karaoke-web` НЕ требуются для проверки этого фикса (но требуются по CI-gate workflow для всего PR — это уже вне scope этого research).
- БД не меняется → миграции, триггеры, sync — НЕ затрагиваются.
- `docs/features/zakroma-albums-by-author.md` — НЕ требует правки (контракт уже описан правильно, фикс делает реализацию соответствующей контракту).