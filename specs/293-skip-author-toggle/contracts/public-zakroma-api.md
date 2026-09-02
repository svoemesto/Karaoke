# Contract: Public Zakroma API

**Backend**: `karaoke-web/.../controllers/{MainController, PublicApiController}`,
`karaoke-app/.../model/{Zakroma, Song}`
**Frontend**: `karaoke-public/src/views/{ZakromaView, AuthorPlaylistView}`
**Spec**: [../spec.md](../spec.md) FR-007, FR-008, FR-011

## Endpoints

### `GET /zakroma` (Thymeleaf, MainController)

**Изменения**: в `MainController.kt:108` параметр `withSkiped` для
`Song.loadListAuthors` берётся из
`siteUserResolver.resolve(request)?.canWorkWithSkipped ?: false`.

```kotlin
// MainController.kt:zakroma() — псевдокод
val canSeeSkipped = siteUserResolver.resolve(request)?.canWorkWithSkipped ?: false
model.addAttribute(
    "authors",
    Song.loadListAuthors(
        withSkiped = canSeeSkipped,  // было: withSkiped = false
        database = WORKING_DATABASE,
    ),
)
model.addAttribute(
    "zakroma",
    Zakroma.getZakroma(
        author = author ?: "",
        canSeeSkipped = canSeeSkipped,  // NEW параметр
        database = WORKING_DATABASE,
        storageService = storageService,
        storageApiClient = storageApiClient,
        onlyPublished = true,
    ),
)
```

### `GET /api/public/zakroma` (JSON, PublicApiController)

**Изменения**: тот же паттерн — `withSkiped` и `canSeeSkipped` берутся
из резолвера.

**Запрос**: query-параметры без изменений:
- `author?: String` — имя автора (опционально)
- `specialBucket?: Boolean = false`
- `anonId?: String` — для EventTypes
- `referrer?: String` — для EventTypes

**Ответ** (`List<ZakromaPublicDto>`): без изменений в структуре DTO,
но состав элементов меняется:
- Без `Authorization`-заголовка ИЛИ `canWorkWithSkipped=false`:
  → SKIP-авторы и SKIP-песни исключены (как сейчас).
- С `Authorization` И `canWorkWithSkipped=true`:
  → SKIP-авторы и SKIP-песни **включены**, в DTO поле `skipped`
  (см. `SongPublicDto.kt:138`) выставлено в `false`.

**Пример ответа** (для редактора с галочкой):
```json
[
  {
    "author": "Skip-автор Тест",
    "picture": "...",
    "albums": [
      {
        "name": "Album 1",
        "year": 2020,
        "songs": [
          {
            "id": 12345,
            "songName": "SKIP-песня 1",
            "tags": "rock SKIP",
            "skipped": false,
            "onAir": true,
            ...
          }
        ]
      }
    ]
  }
]
```

Обратите внимание: `skipped: false` для авторизованного пользователя с
галочкой (даже при наличии тега `SKIP`).

### `Zakroma.getZakroma` (Kotlin)

**Изменения сигнатуры** (см. [../research.md §R10](../research.md#r10-zakromagetzakroma--прокидывание-флага-через-сигнатуру)):

```kotlin
fun getZakroma(
    author: String,
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
    onlyPublished: Boolean = false,
    canSeeSkipped: Boolean = false,  // NEW
): List<Zakroma>
```

Внутри — выбор `withSkiped` для `Song.loadListAuthors` (по `canSeeSkipped`)
и пост-фильтрация в `buildFromSongs` через `canSeeSkipped` (если
`canSeeSkipped=true`, то SKIP-песни пропускаются, иначе фильтруются).

## UI (karaoke-public)

### Бейдж SKIP

Inline-разметка в карточке автора/песни, рендеринг через
`v-if="canSeeSkipped && (isSkippedAuthor || isSkippedSong)"`:

```vue
<span
  v-if="canSeeSkipped && isSkippedSong"
  class="badge text-bg-warning ms-2"
  title="Удалено по требованию правообладателя"
>
  SKIP
</span>
```

Аналогично для автора:
```vue
<span
  v-if="canSeeSkipped && author.skip"
  class="badge text-bg-warning ms-2"
  title="Автор скрыт от публики"
>
  SKIP
</span>
```

CSS-классы Bootstrap 5 (`badge text-bg-warning`) уже подключены в
`karaoke-public` — никаких новых стилей.

### Условие рендера бейджа

`canSeeSkipped` — boolean из `/me` endpoint'а:
```js
const canSeeSkipped = !!user?.canWorkWithSkipped
```

Где `user` — реактивная переменная из `useAuth().user` (стандартный
composable в karaoke-public).

## Тестовые сценарии

- **AC-2.1**: Анонимный пользователь запрашивает `GET /api/public/zakroma`
  → ответ содержит только не-SKIP авторов (как до фичи).
- **AC-2.2**: Редактор Иван (с `can_work_with_skipped=true`) запрашивает
  тот же endpoint с `Authorization: Bearer <token>` → ответ содержит
  ВСЕХ авторов, включая skip-автора, с полем `skipped: false` для
  SKIP-песен.
- **AC-2.3**: Редактор Иван открывает `/zakroma?author=Skip-автор` в UI
  → видит список песен (включая SKIP-тегированные) и бейдж «SKIP»
  рядом с именем автора.
- **AC-2.4**: Обычный пользователь (без галочки) открывает ту же
  страницу → skip-автор не отображается в списке (UI-фильтр по
  `withSkiped=false`).