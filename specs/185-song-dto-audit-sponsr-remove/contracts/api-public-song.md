# Public API Contracts — JSON-форма ответов

**Дата**: 2026-08-14
**Spec**: [spec.md](../spec.md)

## Что описываем

Этот контракт фиксирует JSON-форма ответов `/api/public/*` контроллеров ДО и ПОСЛЕ рефакторинга. Цель — дать разработчику и тестировщику конкретный список ключей, который должен (или не должен) присутствовать в ответе после изменений.

## Endpoint 1: `GET /api/public/zakroma?author=...`

**Контроллер**: `PublicApiController.zakroma` (`karaoke-web/.../PublicApiController.kt:185-229`)
**Возвращает**: `List<ZakromaPublicDto>`

### Изменения в полях `albums[].albumSettings[]` (т.е. `ZakromaAlbumSongPublicDto`)

**УДАЛЕНЫ (21 ключ)**:

```diff
- "linkBoosty": "",
- "linkSponsrPlay": "https://sponsr.ru/smkaraoke/...",
- "linkDzenKaraoke": "",
- "linkDzenLyrics": "",
- "linkDzenTabs": "",
- "linkDzenChords": "",
- "linkVkKaraoke": "",
- "linkVkLyrics": "",
- "linkVkTabs": "",
- "linkVkChords": "",
- "linkTgKaraoke": "",
- "linkTgLyrics": "",
- "linkTgTabs": "",
- "linkTgChords": "",
- "linkPlKaraoke": "",
- "linkPlLyrics": "",
- "linkPlTabs": "",
- "linkPlChords": "",
- "linkMaxKaraoke": "",
- "linkMaxLyrics": "",
- "linkMaxTabs": "",
- "linkMaxChords": "",
```

**ОСТАЮТСЯ (10 ключей)**:

```json
{
  "id": 12345,
  "track": 1,
  "songName": "Название песни",
  "onAir": true,
  "datePublish": "01.08.2026",
  "airTimestamp": 1754006400000,
  "songSubscriptionAvailable": true,
  "alwaysFree": false,
  "freelyAvailableNow": true,
  "freeAccessWindowEndText": "Доступно до 01.09.2026"
}
```

## Endpoint 2: `GET /api/public/songs?songName=...` (поиск)

**Контроллер**: `PublicApiController.songs` (`karaoke-web/.../PublicApiController.kt:486-555`)
**Возвращает**: `List<SongPublicDto>` (с `includeDetails = false`)

### Изменения в полях (то же, что для отдельной песни, см. ниже)

**УДАЛЕНЫ** (25 ключей для `/songs`; `formattedTextSong/Tabs/Chords` уже выключены через `includeDetails = false`, `description/shortDescription/warning` тоже):

```diff
- "sponsrLinkGeneral": "https://sponsr.ru/smkaraoke",
- "haveVkGroupLink": false,
- "idStatus": 6,
- "vkPictureBase64": "",
- "linkSponsrPlay": "https://sponsr.ru/smkaraoke/...",
- "linkBoostyTxt": "",
- "linkDzenKaraoke": "",
- "linkDzenLyrics": "",
- "linkDzenTabs": "",
- "linkDzenChords": "",
- "linkVkKaraoke": "",
- "linkVkLyrics": "",
- "linkVkTabs": "",
- "linkVkChords": "",
- "linkTgKaraoke": "",
- "linkTgLyrics": "",
- "linkTgTabs": "",
- "linkTgChords": "",
- "linkPlKaraoke": "",
- "linkPlLyrics": "",
- "linkPlTabs": "",
- "linkPlChords": "",
- "linkMaxKaraoke": "",
- "linkMaxLyrics": "",
- "linkMaxTabs": "",
- "linkMaxChords": "",
```

**ОСТАЮТСЯ** (~13 ключей для `/songs`, остальное — выключено через `includeDetails = false`):

```json
{
  "id": 12345,
  "songName": "Название песни",
  "author": "Автор",
  "authorAlias": "",  // может быть заполнен алиасом из tbl_authors
  "album": "Альбом",
  "year": 2026,
  "track": 1,
  "key": "Am",
  "bpm": 120,
  "onAir": true,
  "datePublish": "01.08.2026",
  "airTimestamp": 1754006400000,
  "alwaysFree": false,
  "freelyAvailableNow": true,
  "freeAccessWindowEndText": "Доступно до 01.09.2026",
  "songPictureUrl": "/api/public/song-picture/12345",
  // formattedText* — пустые для списка (includeDetails=false)
  "formattedTextSong": "",
  "formattedTextTabs": "",
  "formattedTextChords": "",
  // description/shortDescription/warning — пустые для списка
  "description": "",
  "shortDescription": "",
  "warning": "",
  // idVk* — пустые для embed
  "idVkKaraoke": "", "idVkKaraokeOID": "", "idVkKaraokeID": "",
  "idVkLyrics": "", "idVkLyricsOID": "", "idVkLyricsID": "",
  "idVkMelody": "", "idVkMelodyOID": "", "idVkMelodyID": "",
  "idVkChords": "", "idVkChordsOID": "", "idVkChordsID": "",
  "contentRemoved": false,
  "songSubscriptionAvailable": true,
  "assignment": null  // только для self-assign-редакторов, иначе null
}
```

## Endpoint 3: `GET /api/public/song/{id}` (страница песни)

**Контроллер**: `PublicApiController.song` (`karaoke-web/.../PublicApiController.kt:557-606`)
**Возвращает**: `SongPublicDto` (с `includeDetails = true`)

**Изменения — те же 25 удалённых ключей** (что и в `/songs`).

**ОСТАЮТСЯ** (~40 ключей; тут `formattedText*` и описания заполнены, т.к. `includeDetails = true`):

```json
{
  "id": 12345,
  "songName": "Название песни",
  "author": "Автор",
  "authorAlias": "",
  "album": "Альбом",
  "year": 2026,
  "track": 1,
  "key": "Am",
  "bpm": 120,
  "onAir": true,
  "datePublish": "01.08.2026",
  "airTimestamp": 1754006400000,
  "alwaysFree": false,
  "freelyAvailableNow": true,
  "freeAccessWindowEndText": "Доступно до 01.09.2026",
  "songPictureUrl": "/api/public/song-picture/12345",
  "formattedTextSong": "...текст песни...",
  "formattedTextTabs": "...табулатура...",
  "formattedTextChords": "...аккорды...",
  "description": "...",
  "shortDescription": "...",
  "warning": "",
  "idVkKaraoke": "...", "idVkKaraokeOID": "...", "idVkKaraokeID": "...",
  "idVkLyrics": "...", "idVkLyricsOID": "...", "idVkLyricsID": "...",
  "idVkMelody": "...", "idVkMelodyOID": "...", "idVkMelodyID": "...",
  "idVkChords": "...", "idVkChordsOID": "...", "idVkChordsID": "...",
  "contentRemoved": false,
  "songSubscriptionAvailable": true,
  "assignment": null  // для self-assign-редакторов — SongAssignmentBriefDto
}
```

## Endpoint 4: `GET /api/public/zakroma/stream` (NDJSON)

**Контроллер**: `PublicApiController.zakromaStream` (`karaoke-web/.../PublicApiController.kt:254-425`)
**Возвращает**: NDJSON-поток сообщений `ZakromaStreamMessageDto`

### Изменения в сообщениях типа `song`

УДАЛЕНЫ те же 21 ключ из `song.ZakromaAlbumSongPublicDto` (см. Endpoint 1).

ОСТАЮТСЯ 10 ключей.

## Что НЕ меняется

- `GET /api/public/zakroma` → `albums[].albumName`, `year`, `albumPictureUrl`, `albumType`, `albumTypeLabel`, `description`, `shortDescription`, `warning` — без изменений.
- `GET /api/public/song/{id}` → `assignment` поле — без изменений (логика self-assign не задета).
- `GET /api/public/share/*` (Pass 47-50) — НЕ задевается (другая поверхность).
- `GET /api/public/songs-tiles`, `/authors-tiles`, `/news`, `/latest`, `/premium`, `/player/{id}/access`, `/songeditor/assign-self` — НЕ задеваются.

## Backwards-compatibility

- **Сужающий** (breaking) для любого потребителя, который ПАРСИТ JSON по имени удалённого поля (например, `linkSponsrPlay`).
- На текущий момент единственный потребитель — `karaoke-public` (Vue SPA). Внешних потребителей публичного DTO нет (проект публичный, но third-party интеграций не предоставляет).
- Если в будущем появится внешний потребитель — это будет считаться багом потребителя (API сужается явно).

## Тестирование контракта

Минимальный набор проверок (см. quickstart.md для деталей):

```bash
# Endpoint 1: /zakroma — payload без ссылок
curl -s 'http://localhost:8897/api/public/zakroma?author=КИНО' | jq '.[0].albums[0].albumSettings[0] | keys'
# Ожидаемый результат: ["airTimestamp", "alwaysFree", "datePublish", "freeAccessWindowEndText", "freelyAvailableNow", "id", "onAir", "songName", "songSubscriptionAvailable", "track"]
# НЕ должно быть: linkBoosty, linkSponsrPlay, linkDzen*, linkVk*, linkTg*, linkPl*, linkMax*

# Endpoint 2: /songs — payload без ссылок и служебных полей
curl -s 'http://localhost:8897/api/public/songs?songName=Звезда' | jq '.[0] | keys'
# Ожидаемый результат: ~25 ключей (без sponsrLinkGeneral, haveVkGroupLink, idStatus, vkPictureBase64, linkSponsrPlay, linkBoostyTxt, linkDzen*, linkVk*, linkTg*, linkPl*, linkMax*)

# Endpoint 3: /song/{id} — payload без ссылок и служебных полей
curl -s 'http://localhost:8897/api/public/song/12345' | jq 'keys'
# Ожидаемый результат: ~40 ключей (те же 25 удалено; formattedText* и описания заполнены)
```