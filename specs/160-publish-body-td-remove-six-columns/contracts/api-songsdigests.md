# API Contract: `/api/songsdigests` и связанные endpoints

**Phase**: 1 (Design & Contracts)
**Branch**: `160-publish-body-td-remove-six-columns`
**Spec**: [`./spec.md`](./spec.md)
**Date**: 2026-08-06

Этот документ фиксирует публичный контракт JSON-ответов, изменяемый этим PR. Бэкенд удаляет 27 неиспользуемых полей `processColor*` из всех DTO-ответов; фронт уже перестал их использовать после US1/US3.

---

## 1. Endpoints, попадающие под изменение

Все endpoints ниже отдают `SongDTO` или `SongDTOdigest` (или массив таких). Удаляются одинаковые 27 полей во всех ответах:

| Endpoint | DTO-тип | Контроллер / строка |
|---|---|---|
| `GET /api/songs` | `SongDTO` (полный) | `ApiController.kt` (использует `.map { it.toDTO() }`) |
| `GET /api/song/{id}` | `SongDTO` | `ApiController.kt:2819, 2868` (`.toDTO()`) |
| `GET /api/songsdigests` | `SongDTOdigest` | `ApiController.kt:2499` (`it.toDTO().toDtoDigest()`) |
| `GET /api/songshistory` | `SongDTOdigest` (per row) | `ApiController.kt:2325` (`SongsHistory().toDTO()`) |
| `GET /api/publications` | `SongDTOdigest` (для каждой `publish10..publish23`) | `ApiController.kt:2186` (`publication.map { it.toDTO() }`) |
| `GET /api/unpublications` | `SongDTOdigest` (per row) | `ApiController.kt:2203` (`publish.map { it.toDTO() }`) |
| `GET /api/song/update` (POST) | ответ содержит `SongDTO` | `ApiController.kt` (mutation) |
| `GET /api/songshistory/...` | per row `SongDTOdigest` | `ApiController.kt:2651` |

---

## 2. Endpoint `/api/songsdigests` — канонический пример

### 2.1. Request

```http
GET /api/songsdigests HTTP/1.1
Host: <host>
Cookie: <session>
```

Параметры: нет (фильтры — query-string, не меняются этим PR).

### 2.2. Response — ДО PR (выдержка)

```json
[
  {
    "id": 12345,
    "idPrevious": 12344,
    "songName": "Пример песни",
    "author": "Пример автора",
    "album": "Пример альбома",
    "color": "#00FF00",
    "processColorBoosty": "#A9A9A9",
    "processColorSponsr": "#A9A9A9",
    "processColorVk": "#00FF00",
    "processColorMeltLyrics": "#00FF00",
    "processColorMeltKaraoke": "#FF0000",
    "processColorMeltChords": "#A9A9A9",
    "processColorMeltMelody": "#A9A9A9",
    "processColorPlayerDemo": "#A9A9A9",
    "processColorDzenLyrics": "#A9A9A9",
    "processColorDzenKaraoke": "#A9A9A9",
    "processColorDzenChords": "#A9A9A9",
    "processColorDzenMelody": "#A9A9A9",
    "processColorVkLyrics": "#A9A9A9",
    "processColorVkKaraoke": "#A9A9A9",
    "processColorVkChords": "#A9A9A9",
    "processColorVkMelody": "#A9A9A9",
    "processColorTelegramLyrics": "#A9A9A9",
    "processColorTelegramKaraoke": "#A9A9A9",
    "processColorTelegramChords": "#A9A9A9",
    "processColorTelegramMelody": "#A9A9A9",
    "processColorPlLyrics": "#A9A9A9",
    "processColorPlKaraoke": "#A9A9A9",
    "processColorPlChords": "#A9A9A9",
    "processColorPlMelody": "#A9A9A9",
    "processColorMaxLyrics": "#A9A9A9",
    "processColorMaxKaraoke": "#A9A9A9",
    "processColorMaxChords": "#A9A9A9",
    "processColorMaxMelody": "#A9A9A9",
    "...": "остальные поля"
  }
]
```

### 2.3. Response — ПОСЛЕ PR (выдержка)

```json
[
  {
    "id": 12345,
    "idPrevious": 12344,
    "songName": "Пример песни",
    "author": "Пример автора",
    "album": "Пример альбома",
    "color": "#00FF00",
    "processColorPlayerDemo": "#A9A9A9",
    "...": "остальные поля"
  }
]
```

**Diff**: 27 полей `processColor*` отсутствуют. Остальные поля (`id`, `songName`, ..., `flag*`, `version*`, `rate`, ..., `albumId`, `albumName`, `idTelegramDemo`) — без изменений.

### 2.4. Объём ответа — экономия

- **До**: ~28 `processColor*` × ~12 байт (имя + `":"` + `#XXXXXX"` + `,`) = ~336 байт/песня × 18 858 ≈ **6.3 МБ**.
- **После**: 1 поле × ~28 байт = 28 байт/песня × 18 858 ≈ **~530 КБ**.
- **Экономия**: ~5.8 МБ (~92%).

---

## 3. Endpoint `/api/songs` — полный DTO

Идентичные изменения: те же 27 полей удаляются. Потребители `/api/songs`:
- Vuex-модуль `songs` в `webvue3` — после обновления компонентов не использует `processColor*` (FR-001, US3).

---

## 4. Endpoint `/api/publications` — публикации с `processColorPlayerDemo`

Структура каждой строки:
```json
{
  "publish10": { /* SongDTOdigest */ },
  "publish11": { /* SongDTOdigest */ },
  // ... publish12..publish23
}
```

Каждый `SongDTOdigest` теряет 27 полей `processColor*`, сохраняет `processColorPlayerDemo`.

**Влияние на `Publication`/`PublicationDTO`**: НЕ затрагивается, потому что `Publication.kt:249+` использует геттеры `Song` (`publish10!!.processColorMeltLyrics`), а не DTO-поля.

---

## 5. Endpoint `/song/{id}` — серверный Thymeleaf (НЕ ИЗМЕНЯЕТСЯ)

`MainController.getSong` возвращает **сырой `Song`**, не DTO. Используется шаблонами `publications.html`/`unpublications.html` для JS-обновлений через SSE.

**Поведение после PR**: без изменений. Все `processColor*` геттеры сохранены в `Song.kt:2454–2538`. Шаблоны продолжают работать.

---

## 6. SSE-события (НЕ ИЗМЕНЯЮТСЯ)

SSE-канал публикует события вида `recordDiffValueName: "processColorMeltLyrics"`. Источник — `Song.kt:diff` (6816–6966), который сравнивает геттеры. Поскольку геттеры сохраняются, события продолжают публиковаться с теми же именами.

**Подписчики**:
- `webvue3/src/components/.../SongsTable.vue` — обновляет цвет бейджа по SSE (через `/song/{id}`, не через SSE-event).
- `publications.html`, `unpublications.html` — JS обрабатывает `case 'processColorMeltLyrics':` (строки 220–232, 737–744).

**Влияние**: НЕТ. Имена событий и payload не меняются.

---

## 7. Совместимость

### 7.1. Backward incompatible change

Удаление полей из JSON-ответа — формально **breaking change** для клиентов, которые читают эти поля.

**Подтверждённая зона воздействия**:
- `webvue3` (админка) — все потребители удалены этим PR.
- `karaoke-public` (публичный сайт) — 0 ссылок на `processColor*`.
- `karaoke-web` (Thymeleaf-шаблоны) — использует `MainController.getSong` (raw `Song`), не DTO.

**Внешние потребители**: grep подтвердил отсутствие в репо. Если есть внешние интеграции — они сломаются (нужно предупредить заранее, по решению пользователя).

### 7.2. Forward compatibility

Старый фронт (со ссылками на удалённые поля) после деплоя нового бэкенда получит JSON без них. Vue-биндинги вернут `undefined`, но в живом коде таких обращений нет — JS-движок не выбросит `TypeError`. CSS-стиль `:style="{ backgroundColor: undefined }"` = `backgroundColor: ''` = «без фона», что и является целью этого PR.

---

## 8. Миграция

**Не требуется**. Никаких БД-миграций, никаких конвертаций данных. Изменение чисто косметическое на уровне wire-protocol DTO.

Деплой атомарный (фронт + бэкенд в одном PR):
1. После деплоя фронт уже не запрашивает 27 полей.
2. После деплоя бэкенда JSON перестаёт их отдавать.
3. Между шагами — окно в минуты/часы, в течение которого фронт может получить старые поля (нормально, новый код их игнорирует) или не получить их вообще (новый код тоже не падает).
