# Contract: Public Song SKIP API (OG/SEO, share-link, history)

**Backend**: `karaoke-web/.../controllers/PublicOgSongController`,
`karaoke-web/.../services/SongShareLinkService`,
`karaoke-app/.../controllers/ListeningHistoryController`,
`karaoke-web/.../StatBySong`
**Frontend**: `karaoke-public/src/views/SongView`
**Spec**: [../spec.md](../spec.md) FR-008, FR-011, FR-012

## 1. OG/SEO страницы (`PublicOgSongController`)

### `buildSeoHtmlForBots(songId, ...)` (для Googlebot, VK crawler)

**Изменения**: метод `isSkipped(song: Song): Boolean` →
`isSkipped(song: Song, canSeeSkipped: Boolean = false): Boolean`.

```kotlin
// PublicOgSongController.kt:437 — изменённая сигнатура
private fun isSkipped(song: Song, canSeeSkipped: Boolean = false): Boolean {
    if (canSeeSkipped) return false  // редактор с галочкой
    return song.tags
        .split(" ")
        .map { it.uppercase() }
        .contains("SKIP")
}
```

Все вызывающие места в `PublicOgSongController` (3 места — строки 106,
267, 333) обновляются для прокидывания
`siteUserResolver.resolve(request)?.canWorkWithSkipped ?: false`.

**Важно**: `buildSeoHtmlForBots` (для ботов) ВСЕГДА вызывается без
`Authorization`-заголовка → `canSeeSkipped = false` → SKIP-песни
скрыты от индексации (это и требовалось для compliance).

### `buildListenSection(...)` (для пользователя)

Та же логика: для авторизованного редактора с галочкой → песня
рендерится полностью (плеер), для остальных — заглушка.

## 2. Share-link (`SongShareLinkService`)

### Создание share-link

**Изменения**: метод `createShareLink(...)` (или эквивалентный) —
добавить проверку **в самом начале** (до генерации токена):

```kotlin
// SongShareLinkService — псевдокод
fun createShareLink(song: Song, ...): ShareLink {
    // Compliance (specs/293-skip-author-toggle FR-012): SKIP-песни
    // не должны распространяться через share-link независимо от прав
    // инициатора.
    if (songHasSkipTag(song.tags)) {
        throw ShareLinkForSkippedContentException(
            message = "Невозможно создать share-link для SKIP-контента"
        )
    }
    // ... существующая логика ...
}
```

Контроллер переводит исключение в HTTP 409 Conflict:
```kotlin
catch (e: ShareLinkForSkippedContentException) {
    return ResponseEntity
        .status(HttpStatus.CONFLICT)
        .body(mapOf(
            "error" to "share_link_forbidden",
            "message" to e.message,
        ))
}
```

### Получение share-link (для анонимного получателя)

Существующая фильтрация `songHasSkipTag(song.tags)` в
`SongShareLinkService.kt:981` сохраняется как defense in depth — даже
если ссылка создана в обход UI (что теперь невозможно), анонимный
получатель увидит заглушку «Удалено по требованию правообладателя».

## 3. История прослушиваний (`ListeningHistoryController`)

### `GET /api/public/history` (или аналог)

**Изменения**: метод `getForUser(siteUserId, ...)` — после загрузки
списка истории, фильтр `songHasSkipTag` обходится, если у пользователя
есть галочка.

```kotlin
// ListeningHistoryController.kt — псевдокод (адаптация существующего)
val user = usersById[siteUserId]  // уже загружается в текущем коде
val canSeeSkipped = user?.canWorkWithSkipped == true

// Существующий строк 117-121:
val filtered = allLoaded.filter { h ->
    val song = allSongsById[h.songId]
    when {
        song == null -> false
        canSeeSkipped -> true  // редактор видит всё, включая SKIP
        else -> !songHasSkipTag(song.tags)
    }
}
```

## 4. StatBySong (SQL-фильтр)

**Файл**: `karaoke-web/.../StatBySong.kt`

**Изменения**: SQL `SKIP_FILTER` остаётся для публичных счётчиков
(главная страница, общая статистика). Для авторизованных пользователей
с галочкой — отдельный контроллерный фильтр (см.
[../research.md §R3](../research.md#r3-sql-варианты-skip_filter-в-statsbyang)).

**Практически** — `StatBySong.SKIP_FILTER` НЕ меняется в этой фиче.
Изменения в контроллерах, использующих `StatBySong`:
- `PublicApiController.authorsTiles` — вызывает
  `Author.loadAuthorTilesWithCounts(onlyPublished = onlyPublished, ...)`,
  где `onlyPublished = onlyPublishedFor(request)`. Эта функция уже
  умеет определять «редактор смотрит на полную версию» (см. комментарий
  в `PublicApiController.kt:267`). Добавить аналогичную проверку
  `canSeeSkipped` — если true, счётчик должен включать SKIP-песни.

## 5. UI (karaoke-public) — карточка песни

### Бейдж SKIP на странице песни

```vue
<div v-if="song.tags && song.tags.includes('SKIP') && user?.canWorkWithSkipped" class="km-skip-badge">
  <span class="badge text-bg-warning">SKIP</span>
  <small class="text-muted ms-2">Удалено по требованию правообладателя</small>
</div>
```

### Кнопка «Поделиться»

```vue
<button
  v-if="!song.tags?.toUpperCase().includes('SKIP')"
  @click="shareSong(song)"
  class="km-share-btn"
>
  Поделиться
</button>
<!-- v-if срабатывает для ВСЕХ пользователей, включая редакторов с галочкой:
     SKIP-песни запрещены для share-link независимо от прав (FR-012). -->
```

## Тестовые сценарии

- **AC-3.1**: Googlebot запрашивает `GET /songs/{id}` без
  `Authorization` → получает SEO-заглушку «Удалено по требованию
  правообладателя» (даже если SKIP-песня существует в БД).
- **AC-3.2**: Редактор Иван запрашивает ту же страницу с
  `Authorization: Bearer <token>` (где токен имеет
  `can_work_with_skipped=true`) → получает полный плеер + бейдж
  «SKIP» в UI.
- **AC-3.3**: Редактор Иван пытается создать share-link на SKIP-песню
  через UI → кнопка скрыта. Через прямой API → получает `409 Conflict`
  с сообщением «Невозможно создать share-link для SKIP-контента».
- **AC-3.4**: Обычный пользователь открывает share-link на
  НЕ-SKIP-песню → видит контент (стандартное поведение).
- **AC-3.5**: Анонимный пользователь пытается открыть share-link
  (если бы он каким-то образом был создан для SKIP-песни) →
  получает заглушку (defense in depth).
- **AC-3.6**: Редактор Иван открывает `/account/history` → видит
  свои прослушивания, включая SKIP-песни (которые раньше были
  отфильтрованы).