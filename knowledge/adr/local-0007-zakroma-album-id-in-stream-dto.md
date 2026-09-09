# Local ADR-0007: albumId обязателен в NDJSON-стриме `ZakromaAlbumMetaPublicDto`

* **Status**: Accepted
* **Date**: 2026-09-09
* **Deciders**: команда Karaoke
* **Issue**: OpenProject #70 (spec 356)

> **English version**: [../../../livedocs-en/decisions/local-0007-zakroma-album-id-in-stream-dto.md](../../../livedocs-en/decisions/local-0007-zakroma-album-id-in-stream-dto.md)
>
> **Note**: this is **local** ADR — описывает конкретный технический паттерн в `karaoke-web` (а не глобальное архитектурное решение).

## Context

Спека 356 (Pass 70) добавила промежуточный этап «Альбомы автора» в навигации
`/zakroma/{authorId}?albumId={id}`. Фронт фильтрует `state.zakroma` по `alb.albumId === ?albumId`.

Стрим-эндпоинт `/api/public/zakroma/stream` (используется основной
страницей песен) использует свой собственный DTO — `ZakromaAlbumMetaPublicDto`,
в котором исторически НЕТ поля `albumId`. DTO `ZakromaAlbumPublicDto`
(используется обычным `?author=...&stream=false` эндпоинтом) — есть.

## Проблема

Прецедент (Pass 359): пользователь переходит на `/zakroma/135?albumId=2777`.
Фронт делает стрим-запрос → получает 200 OK с 48 KB данных → стейт
`zakroma` заполнен 6 альбомами. Но фильтр `String(alb.albumId) === '2777'`
не находит совпадений, потому что `albumId` в `ZakromaAlbumMetaPublicDto`
НЕТ. Страница пустая.

REST-эндпоинт `/authors-tiles` (по которому я ранее проверял) — работает
с `AlbumTilePublicDto` (там `albumId` есть). Это ввело в заблуждение —
казалось, что данные есть, но фильтр по стриму не работал.

## Decision

**`albumId: Long = 0` обязательное поле `ZakromaAlbumMetaPublicDto`**:

```kotlin
data class ZakromaAlbumMetaPublicDto(
    val albumId: Long = 0,  // Pass 359: добавлено
    val albumName: String,
    val year: Long,
    val albumPictureUrl: String,
    ...
)
```

`fromAlbum(album: ZakromaAlbum)` пробрасывает `album.albumId`
(уже есть в `ZakromaAlbum` с Pass 357, миграция 29 + Pass 357).

## Альтернативы

- **Фронт отслеживает `currentAlbumId` через порядок сообщений** (album→song→song→...→album): rejected — хрупкий, требует ручной защиты от race-conditions, если `album` сообщение потеряется — песни «прилипнут» к неправильному альбому.
- **Не передавать albumId в album-сообщении, а передавать в song-сообщении** (каждый song знает свой albumId): rejected — увеличивает payload на ~10 байт × 128 песен = 1.3 KB, и не решает race-condition: если album-сообщение потерялось, песни останутся без albumId.
- **Сделать отдельный эндпоинт `?author=...&albumId=N`** (фильтрация на бэкенде): rejected — нужно менять контракт стрима, добавлять server-side фильтр; UI может захотеть сменить альбом без перезагрузки стрима. Текущий подход (UI фильтрует) гибче.

## Consequences

### Positive
- Фильтр `String(alb.albumId) === String(?)` в `ZakromaView.filteredZakroma` работает на стрим-данных.
- Frontend changes НЕ нужны — `{...msg.album}` spread автоматически подхватывает новое поле.
- Совместимость с фронтом сохраняется: старый клиент просто игнорирует `albumId` (для него это лишнее поле).

### Negative
- +1 Long (8 байт) на каждый `album`-чанк в стриме. Несущественно.
- Если у песни нет `album_id` (legacy `song_album` без FK), `ZakromaAlbum.albumId = 0` — фронт фильтрует `String(0)` против `String(2777)`, не совпадает. То же поведение что и до фикса (для legacy — фильтр не работает).

### Neutral
- Стоимость: ровно +1 строка в DTO + 1 параметр в `fromAlbum`.
- Размер NDJSON-чанка: album-чанк +8 байт. Для 6 альбомов: +48 байт. Незаметно.

## References

- [deploy/karaoke-db/29_albums.sql](../../deploy/karaoke-db/29_albums.sql) — оригинальная схема `tbl_albums`
- [deploy/karaoke-db/44_author_song_counts.sql](../../deploy/karaoke-db/44_author_song_counts.sql) — паттерн триггера для `tbl_authors` (Pass 286)
- [deploy/karaoke-db/49_albums_song_counts.sql](../../deploy/karaoke-db/49_albums_song_counts.sql) — триггер для `tbl_albums` (Pass 357)
- [specs/286-author-song-counts-cache](../specs/286-author-song-counts-cache/spec.md) — прецедент денормализации
- [specs/356-zakroma-albums-by-author/spec.md](../specs/356-zakroma-albums-by-author/spec.md) — Issue #70
- [docs/features/zakroma-albums-by-author.md](../../docs/features/zakroma-albums-by-author.md) — per-feature документ

## История

- 2026-09-09: создан (Pass 359) после того как пользователь сообщил что `?albumId=2777` показывает пустую страницу при том что REST-эндпоинт `/authors-tiles` отдаёт правильный `albumId`. Диагноз: NDJSON-стрим использует свой собственный DTO без `albumId`.
