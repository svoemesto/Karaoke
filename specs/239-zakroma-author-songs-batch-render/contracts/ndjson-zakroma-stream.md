# API Contract: NDJSON `zakromaStream` — расширенные поля песни

**Branch**: `239-zakroma-author-songs-batch-render` | **Date**: 2026-08-25

## Endpoint

`GET /api/public/zakroma/stream?author=Машина Времени` (существующий, спека 181)

## Изменение

Каждое NDJSON-сообщение типа «song» дополняется 3 полями:

| Поле | Тип | Источник | Назначение |
|------|-----|----------|-----------|
| `idStatus` | `Int` | `song.idStatus` | Готовность песни по lifecycle (0-6). Используется для иконки плеера (≥4 для админа, ≥6 для публичного плеера). |
| `isFreelyAvailableNow` | `Boolean` | `song.isFreelyAvailableNow` | `is_in_air \|\| flag_free`. Используется для «зелёный vs золотой». |
| `contentReady` | `Boolean` | `song.isContentReady` | Готовность контента (stems + pictures, persistent flags Pass 100). Используется для «серая disabled vs готовая». |

## NDJSON-сообщение песни (новый формат)

```json
{
  "type": "song",
  "id": 1234,
  "author": "Машина Времени",
  "album": "Лучшие песни",
  "year": 1996,
  "songName": "Поворот",
  "songType": "song",
  "status": "READY",
  
  "idStatus": 6,
  "isFreelyAvailableNow": true,
  "contentReady": true,
  
  "color": "#22a447",
  "voiceCount": 1,
  "hasMinus": true,
  "pictureFull": "machine_vremeni_povorot.jpg"
}
```

## Логика иконки плеера (клиент)

```
contentReadyState = contentReady
isActive (зелёный) = contentReady AND (isFreelyAvailableNow OR premium OR hasSubscription)
isDemo (золотой) = contentReady AND NOT isActive
isDisabled (серый) = NOT contentReady
```

Где:
- `premium` — из `useAuth().isPremium`
- `hasSubscription` — `songId ∈ userSongSubscriptions` (из `useSongSubscriptions` store)

## Обратная совместимость

- Старые клиенты, которые не знают о новых полях, игнорируют их — продолжают работать.
- Поля `isFreelyAvailableNow` и `contentReady` всегда присутствуют (boolean), не `null`.

## KDoc (для DTO)

```kotlin
/**
 * Сообщение NDJSON-стрима `/api/public/zakroma/stream` с расширенными полями
 * готовности плеера (Pass 239, см. specs/239-zakroma-author-songs-batch-render).
 *
 * Раньше клиент делал per-row readiness-запросы к `/api/public/player/readiness`
 * после получения списка — это валило MinIO/БД на крупных авторах (~2500 песен).
 * Теперь все нужные для иконки плеера данные приходят в самом сообщении:
 *
 * @property idStatus готовность по lifecycle (см. specs/022-song-status-lifecycle).
 *   ≥4 для админа, ≥6 для публичного плеера.
 * @property isFreelyAvailableNow `is_in_air || flag_free`. Используется для логики
 *   «зелёный vs золотой».
 * @property contentReady persistent flags из tbl_settings (Pass 100, см.
 *   deploy/karaoke-db/26_player_readiness_flags.sql). True = можно открыть плеер.
 */
```