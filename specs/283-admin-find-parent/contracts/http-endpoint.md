# HTTP Contract: `POST /api/utils/findparentforauthor`

**Branch**: `283-admin-find-parent`
**Date**: 2026-08-31
**Spec**: `../spec.md`
**Backend**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`
**Vuex action**: `webvue3/src/components/Songs/store.js` → `findParentForAuthorPromise`

## Назначение

Запустить фоновый поиск **текстового родителя** (`root_id`) для всех песен одного автора, у которых `root_id = 0`. Используется админом через главную страницу `webvue3`.

Аналог существующего `POST /api/utils/customfunction`, но с тремя отличиями:

1. Фильтр по автору (обязательный).
2. Только фаза 1 (текстовый родитель). **Без** фазы 2 (акустический аудио-родитель).
3. Опциональный флаг `crossAuthor` (по умолчанию `false` — поиск только среди того же автора).

---

## Запрос

`POST /api/utils/findparentforauthor`

### Параметры

| Параметр      | Где     | Тип      | Required | Default | Описание                                       |
|---------------|---------|----------|----------|---------|------------------------------------------------|
| `author`      | query   | `String` | да       | —       | Имя автора; trimmed; case-insensitive.          |
| `crossAuthor` | query   | `Boolean`| нет      | `false` | Разрешить подбор кандидатов среди других авторов. |

### Примеры

```
# По умолчанию (crossAuthor=false)
POST /api/utils/findparentforauthor?author=%D0%9F%D1%83%D0%BF%D0%BA%D0%B8%D0%BD%20%D0%92.

# С разрешённым поиском среди других авторов
POST /api/utils/findparentforauthor?author=%D0%9F%D1%83%D0%BF%D0%BA%D0%B8%D0%BD%20%D0%92.&crossAuthor=true
```

### Ограничения

- `author.trim().isNotEmpty()` — иначе HTTP `400 Bad Request` с телом `author must not be blank`. (Защита на случай, если фронт всё-таки отправит пустую строку.)
- Тело запроса — пустое.

---

## Ответ

### `200 OK` — запущено в фоне

Тело: строка `"OK"`.

```http
HTTP/1.1 200 OK
Content-Type: text/plain;charset=UTF-8

OK
```

### `200 OK` — уже идёт предыдущий запуск

Тело: строка `"ALREADY_RUNNING"`.

```http
HTTP/1.1 200 OK
Content-Type: text/plain;charset=UTF-8

ALREADY_RUNNING
```

Фронт различает значения в теле и показывает:
- `"OK"` → info-тост «Операция запущена в фоне».
- `"ALREADY_RUNNING"` → warning-тост «Уже запущено — дождитесь завершения текущего прогона».

### `400 Bad Request` — пустой автор

```http
HTTP/1.1 400 Bad Request
Content-Type: text/plain;charset=UTF-8

author must not be blank
```

В UI не должно возникать (кнопка `disabled` при пустом авторе), но защита остаётся на стороне бэка.

---

## Поведение сервера (high-level)

1. Проверить `author.trim().isNotEmpty()` → иначе `400`.
2. Проверить флаг `isFindParentInProgress`:
   - `true` → немедленно вернуть `"ALREADY_RUNNING"`.
   - `false` → выставить `true` (в finally — сбросить).
3. В фоновом потоке (`thread { … }`):
   - `SELECT id FROM tbl_songs WHERE root_id = 0 AND LOWER(song_author) = LOWER(?) ORDER BY id`.
   - Для каждого `id` — `Song.loadFromDbById` + `findParentCandidateId(song, WORKING_DATABASE, crossAuthor = crossAuthor)`.
     **Важно**: `findParentCandidateId` ищет родителя среди **всех** песен с тем же нормализованным названием — независимо от наличия `source_text` (т.е. «сирота» без текста тоже может быть родителем).
   - Логика применения — копия фазы 1 `customFunction` (Utils.kt:118-186): если кандидат найден и у самой песни ещё нет проверенного текста (`sourceText.isBlank() || id_status < 2`) — привязать. Если у найденного оригинала есть `source_text` — `applyDuplicateOriginal(song, original)`, иначе просто `song.rootId = original.id; song.saveToDb()`.
   - Логировать: «Найдено песен: N», per-song `[родитель i/N] … — …», финальная сводка.
4. Отправить SSE-уведомление:
   ```
   type = "info"
   head = "Поиск родителя (автор «<author>»)"
   body = "Обработано N, родитель назначен M (найдено, но пропущено из-за текста: K)"
   ```

---

## Поведение клиента (webvue3)

### Vuex-action

```js
findParentForAuthorPromise(ctx, payload) {
  let params = {
    author: payload.author,
    crossAuthor: !!payload.crossAuthor,
  }
  let request = {
    method: 'POST',
    url: '/api/utils/findparentforauthor',
    params: params,
  }
  return promisedXMLHttpRequest(request)
}
```

### Обработчик кнопки (псевдокод)

```js
findParentForAuthor() {
  this.customConfirmParams = {
    header: 'Подтвердите действие',
    body:
      `Запустить поиск родителя для всех песен автора «<strong>${this.author}</strong>» с root_id=0?<br>` +
      `Для каждой такой песни будет выполнен поиск родителя по точному совпадению нормализованного названия.<br>` +
      `Если родитель найден и у песни ещё нет проверенного текста — root_id будет проставлен.<br>` +
      `<strong>Операция тяжёлая и идёт в фоне — итог придёт уведомлением.</strong>`,
    fields: [
      {
        fldName: 'crossAuthor',
        fldLabel: 'Искать среди песен других авторов:',
        fldValue: false,
        fldIsBoolean: true,
        fldLabelStyle: { width: '320px', textAlign: 'right', paddingRight: '5px' },
        fldValueStyle: { flex: '1' },
      },
    ],
    timeout: 15,
    callback: this.doFindParentForAuthor,
  }
  this.isCustomConfirmVisible = true
}

doFindParentForAuthor(result) {
  this.$store
    .dispatch('findParentForAuthorPromise', {
      author: this.author,
      crossAuthor: result.crossAuthor === 'true' || result.crossAuthor === true,
    })
    .then((response) => {
      this.customConfirmParams = {
        isAlert: true,
        alertType: response === 'ALREADY_RUNNING' ? 'warning' : 'info',
        header: 'Поиск родителя',
        body:
          response === 'ALREADY_RUNNING'
            ? `Уже запущено — дождитесь завершения текущего прогона.`
            : `Операция запущена в фоне.<br>Итог придёт уведомлением по завершении.`,
        timeout: 10,
      }
      this.isCustomConfirmVisible = true
    })
}
```

---

## Шаблон `<button>`

Размещение: **первая** кнопка в блоке `<div class="field-and-buttons-wrapper">` с полем «Автор», **перед** `<button … Автопривязать оригинал по аудио (статус 1 → 2)>`.

```vue
<button
  class="button-action"
  :disabled="!author"
  @click="findParentForAuthor"
>
  Поиск родителя
</button>
```

---

## Не в скоупе контракта

- Аудио-фаза (`findAudioParentByWaveform`) — **отключена** для этого эндпоинта (для аудио есть отдельный `/api/utils/findaudioparentforauthor`, см. ниже).
- Глобальный запуск (по всем авторам) — для этого остаётся `/api/utils/customfunction`.
- Передача `author` через JSON-body — используется `query`/`form`, как у соседнего `/api/songs/autoassignoriginalall`.
- Аутентификация — админка `webvue3` идёт через `permitAll()` (Constitution § V), отдельной проверки прав не требуется.

---

# HTTP Contract: `POST /api/utils/findaudioparentforauthor`

**Branch**: `283-admin-find-parent`
**Date**: 2026-08-31
**Spec**: `../spec.md`
**Backend**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`
**Vuex action**: `webvue3/src/components/Songs/store.js` → `findAudioParentForAuthorPromise`

## Назначение

Запустить фоновый поиск **аудио-родителя** (`audio_parent_id`) среди песен в семье для всех песен одного автора с `root_id <> 0` **И** `audio_parent_id = 0`. Используется админом через главную страницу `webvue3`.

Отличия от `POST /api/utils/findparentforauthor`:

1. Ищет аудио-родителя, а не текстового.
2. Кандидаты — **только в семье** (`findFamilySongIds` транзитивно по `root_id`); НЕ ищем среди песен других авторов.
3. SQL-фильтр включает `audio_parent_id = 0` для идемпотентности — песни, для которых `findAudioParentByWaveform` уже отработал ранее, повторно не обрабатываются.
4. Действие — только запись `audio_parent_id`/`audio_similarity_percent`/`audio_delta_ms` (через существующую `findAudioParentByWaveform`); **без** копирования маркеров/текста/статуса, **без** перевода в статус 2, **без** записи `.srt`.

---

## Запрос

`POST /api/utils/findaudioparentforauthor`

### Параметры

| Параметр | Где   | Тип      | Required | Default | Описание                                       |
|----------|-------|----------|----------|---------|------------------------------------------------|
| `author` | query | `String` | да       | —       | Имя автора; trimmed; case-insensitive.         |

### Пример

```
POST /api/utils/findaudioparentforauthor?author=%D0%9F%D1%83%D0%BF%D0%BA%D0%B8%D0%BD%20%D0%92.
```

### Ограничения

- `author.trim().isNotEmpty()` — иначе HTTP `400 Bad Request` с телом `author must not be blank`.
- Тело запроса — пустое.

---

## Ответ

| HTTP | Тело ответа       | Условие                                              |
|------|-------------------|------------------------------------------------------|
| 200  | `"OK"`            | Запущено в фоне.                                     |
| 200  | `"ALREADY_RUNNING"` | Уже идёт предыдущий запуск (фронт — warning-тост).   |
| 400  | `author must not be blank` | Пустой `author` после trim.                |

---

## Поведение сервера (high-level)

1. Проверить `author.trim().isNotEmpty()` → иначе `400`.
2. Проверить флаг `isFindAudioParentInProgress`:
   - `true` → немедленно вернуть `"ALREADY_RUNNING"`.
   - `false` → выставить `true` (в finally — сбросить).
3. В фоновом потоке (`thread { … }`):
   - `SELECT id FROM tbl_songs WHERE root_id <> 0 AND audio_parent_id = 0 AND LOWER(song_author) = LOWER(?) ORDER BY id`.
   - Для каждого `id` — `Song.loadFromDbById` + `findAudioParentByWaveform(song, …, searchOtherAuthors = false)`.
   - Логика `findAudioParentByWaveform` (Utils.kt:5117+):
     - кандидаты = `findFamilySongIds(song, db)` (т.к. `searchOtherAuthors=false`);
     - сверка через `WaveformCompare.compareWaveforms` (с кэшем `audio_compare_history`);
     - при лучшем кандидате ≥ `AUDIO_PARENT_THRESHOLD = 95%` → флэттенинг дерева `audio_parent_id` и запись `audio_parent_id`/`audio_similarity_percent`/`audio_delta_ms`.
   - Текст/маркеры/статус песни **не** меняются.
   - Логировать: «Найдено песен в семье без audio_parent_id: N», per-song `[аудио-родитель i/N] … — …» (reason из `AudioParentResult.reason`), финальная сводка.
4. Отправить SSE-уведомление:
   ```
   type = "info"
   head = "Поиск аудио-родителя (автор «<author>»)"
   body = "Обработано N, аудио-родитель назначен M, пропущено K"
   ```

---

## Поведение клиента (webvue3)

### Vuex-action

```js
findAudioParentForAuthorPromise(ctx, payload) {
  let params = { author: payload.author }
  let request = { method: 'POST', url: '/api/utils/findaudioparentforauthor', params: params }
  return promisedXMLHttpRequest(request)
}
```

### Обработчик кнопки (псевдокод)

```js
findAudioParentForAuthor() {
  this.customConfirmParams = {
    header: 'Подтвердите действие',
    body:
      `Запустить поиск аудио-родителя среди всех претендентов в семье для песен автора «<strong>${this.author}</strong>» с root_id ≠ 0, у которых ещё не найден audio_parent_id?<br>` +
      `Для каждой такой песни будет выполнена акустическая сверка (WaveformCompare) с другими песнями в её семье — и при совпадении ≥ 95% будет записан audio_parent_id.<br>` +
      `Текст/маркеры/статус песни НЕ изменяются.<br>` +
      `<strong>Операция очень тяжёлая (ffmpeg-декод на каждого кандидата) и идёт в фоне — итог придёт уведомлением.</strong>`,
    timeout: 15,
    callback: this.doFindAudioParentForAuthor,
  }
  this.isCustomConfirmVisible = true
}

doFindAudioParentForAuthor() {
  this.$store
    .dispatch('findAudioParentForAuthorPromise', { author: this.author })
    .then((response) => {
      this.customConfirmParams = {
        isAlert: true,
        alertType: response === 'ALREADY_RUNNING' ? 'warning' : 'info',
        header: 'Поиск аудио-родителя',
        body:
          response === 'ALREADY_RUNNING'
            ? `Уже запущено — дождитесь завершения текущего прогона.`
            : `Операция запущена в фоне.<br>Итог придёт уведомлением по завершении.`,
        timeout: 10,
      }
      this.isCustomConfirmVisible = true
    })
}
```

---

## Шаблон `<button>`

Размещение: **вторая** кнопка в блоке `<div class="field-and-buttons-wrapper">` с полем «Автор», **между** `<button … Поиск родителя>` и `<button … Автопривязать оригинал по аудио (статус 1 → 2)>`.

```vue
<button
  class="button-action"
  :disabled="!author"
  @click="findAudioParentForAuthor"
>
  Найти аудио-родителя
</button>
```

---

## Не в скоупе контракта

- Текстовый поиск родителя (`findParentCandidateId`) — **отключён** для этого эндпоинта (для текста есть отдельный `/api/utils/findparentforauthor`).
- Поиск среди песен других авторов (`searchSongsByNormalizedName`) — **отключён** (`searchOtherAuthors=false`).
- Применение выбора (копирование маркеров, статус 2, запись `.srt`) — **отключено** (в отличие от `autoAssignOriginalAll`).
- Параметр `threshold` — **отсутствует** (используется hardcoded `AUDIO_PARENT_THRESHOLD = 95%`).
- Аутентификация — админка `webvue3` через `permitAll()` (Constitution § V).
