# Component: DTOs (API contracts)

> **Домен**: integration (API contracts)
> **Компонента**: каталог всех DTO (Data Transfer Object) проекта.

## Назначение

DTO — сериализуемое представление сущностей для API/UI. Каждая
сущность имеет свой `*Dto.kt` (26 в karaoke-app, 8 публичных в
karaoke-web). DTO НЕ содержат бизнес-логики, только поля + Jackson
аннотации.

## DTO в karaoke-app (26 файлов)

| DTO | Что | Используется в |
|---|---|---|
| `AlbumDTO` | Альбом | `/api/albums/list` |
| `AuthorDTO` | Автор | `/api/authors/list` |
| `CartItemDto` | Позиция корзины | `/api/cart/list` |
| `DictionaryDto` | Словарь | `/api/dictionaries/list` |
| `KaraokeDbTableDto` | Базовый DTO (id) | Все DTO extend'ятся |
| `ListeningHistoryDto` | История прослушиваний | `/api/listening-history/list` |
| `NewsDto` | Новость | `/api/news/list` |
| `PicturesDTO` | Картинка | `/api/pictures/list` |
| `PriceTariffDto` | Тариф | `/api/tariffs/list` |
| `PromoRuleDto` | Акция | `/api/promorules/list` |
| `SearchAsyncDTO` | Async-поиск | `/api/search/async` |
| `SearchResultDTO` | Результат async-поиска | (тот же) |
| `SiteChatMessageDto` | Сообщение чата | `/api/chat/...` |
| `SitePlaylistDto` | Плейлист | `/api/siteplaylists/list` |
| `SitePlaylistItemDto` | Позиция плейлиста | (тот же) |
| `SiteUserDto` | Пользователь | `/api/siteusers/list` |
| `SongAssignmentDraftDto` | Draft задания | `/api/songassignmentdraft/list` |
| `SongAssignmentDto` | Задание | `/api/songassignment/list` |
| `SongCoAuthorDTO` | Со-автор | (internal) |
| `SongDTO` | Песня (главный) | `/api/songs/list`, повсюду |
| `SongDTOdigest` | Краткая инфа о песне | `/api/songs/digest` |
| `SongShortInfoDto` | Ещё короче | (internal) |
| `StatsDebugDto` | Debug статистика | `/api/stats/debug` |
| `StemJobDto` | StemJob | `/api/stemjobs/list` |
| `SubscriptionDto` | Подписка | `/api/subscriptions/list` |
| `WebEventDTO` | WebEvent | (analytics) |

## DTO в karaoke-web (8 публичных)

| DTO | Что | Используется в |
|---|---|---|
| `AuthorTilePublicDto` | Тайл автора (главная) | `/api/public/authors/...` |
| `HistoryEntryDto` | Запись истории | `/api/public/history/...` |
| `PagedSongsDto` | Страница песен (public) | `/api/public/songs/...` |
| `SongPublicDto` | Песня (public) | `/api/public/songs/{id}` |
| `ZakromaAlbumMetaPublicDto` | Мета альбома «Закрома» | `/api/public/zakroma/...` |
| `ZakromaPublicDto` | «Закрома» автора | (тот же) |
| `ZakromaStreamMessageDto` | Сообщение стрима «Закрома» | `/api/public/zakroma/stream` |
| `ZakromaStreamMetricDto` | Метрика стрима | (тот же) |

## Jackson конвенции

Из CLAUDE.md раздела `Code Style`:

> Avoid `is*` prefix for boolean fields in DTOs (Jackson serialization issue).

То есть `isActive` → `active`, `isPublic` → `public`. Это критично
для совместимости с JavaScript-фронтом (camelCase vs snake_case).

## Архитектурные решения

### Решение 1: DTO ≠ Entity

Entity (например, `Song`) содержит **бизнес-логику** (методы
`save()`, `loadList()`). DTO (`SongDTO`) — только данные для
сериализации. Чёткое разделение.

### Решение 2: DTO генерируются вручную (не Jackson auto)

Jackson может генерировать DTO из entity через `@JsonView`, но
проект предпочитает явные `*Dto.kt` для контроля над сериализацией.

### Решение 3: Public vs internal DTO

- **Internal** (`SongDTO`) — для admin (webvue3).
- **Public** (`SongPublicDto`) — для публичного сайта (ограниченный
  набор полей, скрыты internal-флаги).

Это **защита от утечки**: `songDTO` содержит ВСЕ поля
(`isReady`, `recordHash`, и т.д.), `songPublicDto` — только то, что
нужно пользователю.

## Связь с другими компонентами

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

- **Pass 352** (2026-09-09): Initial. Автор: agent (Karaoke).