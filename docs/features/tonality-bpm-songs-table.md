# Тональность и темп в таблице песен (Ton / BPM)

> **Status**: active
> **Feature Key**: tonality-bpm-songs-table
> **Last Updated**: 2026-09-21

## Что делает

В главной таблице песен админки (`SongsView` → `SongsTable.vue`) между
колонками `t/c` и `HR` отображаются две новые колонки:

- **Ton** — тональность песни сокращённо (`C minor` → `Cm`, `D major` → `D`,
  `Bb minor` → `A#m`), идентично обозначению в плеере;
- **BPM** — темп; `-`, если не задан.

## Зачем

Тональность и темп — ключевые музыкальные характеристики песни, по которым
владелец принимает решения (совместимость, подбор, проверка результата
keybpmfinder). Раньше их приходилось открывать в редакторе по одной песне;
теперь они видны прямо в списке. Сокращённое обозначение совпадает с плеером,
чтобы не заводить второй визуальный язык для той же сущности.

## Как работает

1. Backend: `SongDTO.toDtoDigest()` заполняет `SongDTOdigest.key` (сырая строка,
   например `C minor`) и `SongDTOdigest.bpm` (`0`, если не задан). Поля берутся
   из `SongDTO.key`/`SongDTO.bpm`, которые уже присутствуют.
2. Эндпоинт `POST /api/songsdigests` (`ApiController.apisSongsDigests`) отдаёт
   расширенный digest-массив.
3. Frontend: `SongsTable.vue` добавляет в `songDigestFields` колонки `tonality`
   (`Ton`) и `bpm` (`BPM`) между `timecode` (`t/c`) и `healthReportText` (`HR`).
   - `#cell(tonality)` → `shortKey(data.item.key)`; метод делегирует в
     `KaraokePlayer._shortKey` и подставляет `-` при пустом результате.
   - `#cell(bpm)` → `data.item.bpm > 0 ? data.item.bpm : '-'`.

## Инварианты / правила

- **MUST**: сокращение тональности — через `KaraokePlayer._shortKey`
  (`webvue3/src/player/KaraokePlayer.js`), единый источник с плеером.
- **MUST**: `SongDTOdigest` — единственный источник данных таблицы; новые поля
  добавляются туда, а не отдельным запросом.
- **MUST**: `-` вместо пустых значений (паттерн проекта для таблиц админки).

## Известные ловушки

- **`SongDTOdigest` ≠ `SongDTO`**: поля `key`/`bpm` были только в `SongDTO`.
  Таблица получает **digest**, поэтому без правки `SongDTOdigest` +
  `toDtoDigest()` новые колонки были бы всегда пустыми.
- **Название prop `key` в Vue**: в шаблоне `:key=` — это vnode-директива, а не
  prop (см. комментарий в `SongKaraokeEditorView.vue`). В конфиге полей
  bootstrap-vue-next `key` — это имя поля, конфликта нет, но при передаче как
  prop использовать `tonality`.
- **Число `bpm`**: `0` — «не задан», а не «0 ударов». Отображать `-`.

## Ссылки на ключевые классы/файлы

- [`karaoke-app/.../model/SongDTOdigest.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongDTOdigest.kt) — поля `key`, `bpm`.
- [`karaoke-app/.../model/SongDTO.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongDTO.kt) — `toDtoDigest()`.
- [`karaoke-app/.../controllers/ApiController.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt) — `POST /api/songsdigests`.
- [`webvue3/src/components/Songs/SongsTable.vue`](../../webvue3/src/components/Songs/SongsTable.vue) — колонки `Ton`/`BPM`, метод `shortKey`.
- [`webvue3/src/player/KaraokePlayer.js`](../../webvue3/src/player/KaraokePlayer.js) — `_shortKey`/`_parseKey`.
- [spec `417-142-tonality-bpm-songs-table`](/specs/417-142-tonality-bpm-songs-table/spec.md) — полная спека.
