# Component: DTOs (API contracts)

> **Домен**: integration (API contracts)
> **Компонента**: каталог всех DTO (Data Transfer Object) проекта.


## Ответственность | Responsibility


каталог всех DTO (Data Transfer Object) проекта.

## Назначение

DTO — сериализуемое представление сущностей для API/UI. Каждая
сущность имеет свой `*Dto.kt` (26 в karaoke-app, 8 публичных в
karaoke-web). DTO НЕ содержат бизнес-логики, только поля + Jackson
аннотации.

## Интерфейсы и Контракты | Interfaces and Contracts

Контракт DTO — это его поля (тип + источник значения) и endpoint, через
который он сериализуется. Общий базовый контракт задаёт интерфейс
`KaraokeDbTableDto`:

| Метод | Вход → Выход | Назначение |
|---|---|---|
| `isValid()` | `()` → `Boolean` | Валидация DTO перед записью (default `true`). |
| `validationErrors()` | `()` → `List<String>` | Список ошибок валидации (default пустой). |
| `fromDto(database)` | `KaraokeConnection` → `KaraokeDbTable` | Обратный маппинг DTO → entity. |

Типы полей DTO — простые сериализуемые (`Long`, `Int`, `String`,
`Boolean`, `Double`, `Date?`, вложенные DTO); идентификаторы — `Long`,
ссылки на автора/альбом — `String` (имя), опциональные поля — nullable
(`Long?`, `String?`, `Date?`).

### DTO в karaoke-app (26 файлов)

| DTO | Что | Используется в |
|---|---|---|
| `AlbumDTO` | Альбом | `POST /api/albums/albumsdigests` |
| `AuthorDTO` | Автор | `POST /api/authors/authorsdigests` |
| `CartItemDto` | Позиция корзины | `GET /api/public/account/cart/list` |
| `DictionaryDto` | Словарь | `/api/dictionaries/list` |
| `KaraokeDbTableDto` | Базовый DTO (id) | Все DTO extend'ятся |
| `ListeningHistoryDto` | История прослушиваний | `/api/listeninghistory/digest` |
| `NewsDto` | Новость | `POST /api/news/list` |
| `PicturesDTO` | Картинка | `POST /api/pictures/picturesdigests` |
| `PriceTariffDto` | Тариф | `/api/tariffs/list` |
| `PromoRuleDto` | Акция | `/api/promorules/list` |
| `SearchAsyncDTO` | Async-поиск | `POST /api/song/searchasync` |
| `SearchResultDTO` | Результат async-поиска | (тот же) |
| `SiteChatMessageDto` | Сообщение чата | `/api/chat/...` |
| `SitePlaylistDto` | Плейлист | `POST /api/siteplaylists/digest` |
| `SitePlaylistItemDto` | Позиция плейлиста | (тот же) |
| `SiteUserDto` | Пользователь | `POST /api/siteusers/digest` |
| `SongAssignmentDraftDto` | Draft задания | `POST /api/songeditor/digest` (в составе задания) |
| `SongAssignmentDto` | Задание | `POST /api/songeditor/digest` |
| `SongCoAuthorDTO` | Со-автор | (internal) |
| `SongDTO` | Песня (главный) | `POST /api/songs`, повсюду |
| `SongDTOdigest` | Краткая инфа о песне | `/api/songsdigests` (в т.ч. `key`/`bpm` для колонок Ton/BPM в `SongsTable.vue`, OpenProject #142) |
| `SongShortInfoDto` | Ещё короче | (internal) |
| `StatsDebugDto` | Debug статистика | `/api/stats/debug` |
| `StemJobDto` | StemJob | `/api/stemjobs/list` |
| `SubscriptionDto` | Подписка | `/api/subscriptions/digest` |
| `WebEventDTO` | WebEvent | (analytics) |

### DTO в karaoke-web (8 публичных)

| DTO | Что | Используется в |
|---|---|---|
| `AuthorTilePublicDto` | Тайл автора (главная) | `/api/public/authors/...` |
| `HistoryEntryDto` | Запись истории | `/api/public/history/...` |
| `PagedSongsDto` | Страница песен (public) | `GET /api/public/songs` |
| `SongPublicDto` | Песня (public) | `GET /api/public/song/{id}` |
| `ZakromaAlbumMetaPublicDto` | Мета альбома «Закрома» | `/api/public/zakroma/...` |
| `ZakromaPublicDto` | «Закрома» автора | (тот же) |
| `ZakromaStreamMessageDto` | Сообщение стрима «Закрома» | `/api/public/zakroma/stream` |
| `ZakromaStreamMetricDto` | Метрика стрима | (тот же) |

## Логика и Алгоритмы | Logic and Algorithms

**Маппинг entity ↔ DTO**: у каждой entity есть `toDTO()`, у DTO —
`fromDto(database)` (см. `KaraokeDbTableDto`). DTO — плоское
сериализуемое представление: вложенные сущности разворачиваются в
`String`-имена или id, служебные поля БД (например, `recordHash`,
`isDeleted`) в публичные DTO не попадают.

**Сериализация Jackson**: ключ JSON = имя Kotlin-свойства; для
boolean-полей с `is`-префиксом Kotlin-геттер и Jackson расходятся,
поэтому там ставится явный `@get:JsonProperty(...)` (примеры:
`AuthorDTO.isSpecialOrder` → `"isSpecialOrder"`,
`SiteUserDto.canSelfAssignTasks`, `canWorkWithSkipped`).

### Jackson конвенции

Из CLAUDE.md раздела `Code Style`:

> Avoid `is*` prefix for boolean fields in DTOs (Jackson serialization issue).

То есть `isActive` → `active`, `isPublic` → `public`. Это критично
для совместимости с JavaScript-фронтом (camelCase vs snake_case).

### Архитектурные решения

#### Решение 1: DTO ≠ Entity

Entity (например, `Song`) содержит **бизнес-логику** (методы
`save()`, `loadList()`). DTO (`SongDTO`) — только данные для
сериализации. Чёткое разделение.

#### Решение 2: DTO генерируются вручную (не Jackson auto)

Jackson может генерировать DTO из entity через `@JsonView`, но
проект предпочитает явные `*Dto.kt` для контроля над сериализацией.

#### Решение 3: Public vs internal DTO

- **Internal** (`SongDTO`) — для admin (webvue3).
- **Public** (`SongPublicDto`) — для публичного сайта (ограниченный
  набор полей, скрыты internal-флаги).

Это **защита от утечки**: `songDTO` содержит ВСЕ поля
(`isReady`, `recordHash`, и т.д.), `songPublicDto` — только то, что
нужно пользователю.

## Зависимости | Dependencies

- **Entity** ([entities-catalog.md](../../../domains/catalog/components/entities-catalog.md)) — каждой
  entity соответствует DTO.
- **KaraokeDbTable** ([persistence domain](../../../domains/persistence/domain.md)) —
  `toDTO()` метод на каждой entity.

## Известные TODO

- [ ] **Поля каждого DTO** — детальный обзор (Pass 343+).
- [ ] **Контракт API** — `docs/api/` (автогенерированный, но не полный).
- [ ] **Версионирование** — есть ли версия API (`/api/v1/`, `/api/v2/`)?
- [ ] **Backward compatibility** — как управляется.

## Changelog

- **Pass 484** (2026-09-27, spec `484-knowledge-domain-integration`): секции приведены к шаблону. Автор: agent (Karaoke).
- **Pass 352** (2026-09-09): Initial. Автор: agent (Karaoke).