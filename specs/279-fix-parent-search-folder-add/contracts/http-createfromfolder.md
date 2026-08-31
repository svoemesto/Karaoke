# Contract: HTTP endpoint /api/utils/createfromfolder (UI-контракт)

**Дата**: 2026-08-31
**Спека**: [../spec.md](../spec.md)
**Файл backend**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:5390`
**Файл UI**: `webvue3/src/views/HomeView.vue` + `webvue3/src/components/Songs/store.js` (`createFromFolderPromise`)

Этот контракт не изменяется в рамках спеки 279 — фикс локализован в backend-логике `Utils.findDuplicateOriginal`. Контракт задокументирован здесь для полноты (FR-011 спеки).

## Сигнатура

### HTTP

```
POST /api/utils/createfromfolder
Content-Type: application/x-www-form-urlencoded
POST body: folder=<абсолютный_путь_к_папке>
```

(Также существует legacy-эндпоинт `POST /utils/createfromfolder` через шаблон `main.html`, см. спеку 238 — оба используют общую функцию `Song.createFromPath`.)

### Response

```json
// 200 OK, тело не используется фронтом — результат приходит через SSE-уведомление
```

### SSE-уведомление (по завершении)

Канал `SNS` (SSE), сообщение типа `info`:

```json
{
  "type": "info",
  "head": "Добавление файлов из папки",
  "body": "Добавлено файлов из папки «<путь>»: <количество> (пропущено: <количество>)"
}
```

## UI-вызов

### `webvue3/src/components/Songs/store.js`

```javascript
createFromFolderPromise(ctx, payload) {
  let params = { folder: payload.folder }
  let request = { method: 'POST', url: '/api/utils/createfromfolder', params: params }
  return promisedXMLHttpRequest(request)
},
```

### `webvue3/src/views/HomeView.vue`

Кнопка «Добавить файлы из папки» вызывает:

```javascript
addFilesFromFolder() {
  // открывает CustomConfirm с полем ввода пути
  // callback: this.doAddFilesFromFolder
}

doAddFilesFromFolder() {
  this.$store.dispatch('createFromFolderPromise', { folder: this.pathToFolder })
}
```

## Что меняется в поведении (после фикса 279)

### До фикса

- `POST /api/utils/createfromfolder?folder=/path/to/folder` принимает запрос.
- `Song.createFromPath` создаёт песни.
- `findDuplicateOriginal` возвращает `null` для кириллических авторов (из-за `LOWER(...)` в локали C/POSIX).
- `applyDuplicateOriginal` НЕ вызывается.
- Новые песни остаются с `root_id = 0`, без скопированного текста/маркеров от родителя.

### После фикса

- Те же шаги, но `findDuplicateOriginal` использует `song_author ILIKE ?` (вместо `LOWER(...) = LOWER(...)`).
- Для кириллических авторов родитель находится корректно.
- `applyDuplicateOriginal` вызывается, копирует `root_id` и текстовые поля.
- В БД: `root_id` заполнен, `source_text` и `formatted_text_*` скопированы, `id_status = 1`.

## Гарантии

| Свойство | Статус |
|----------|--------|
| HTTP-метод | Не меняется (`POST`). |
| URL | Не меняется (`/api/utils/createfromfolder` и `/utils/createfromfolder`). |
| Параметры запроса | Не меняются (`folder: String`). |
| Формат ответа | Не меняется. |
| SSE-уведомление | Не меняется. |
| UI-компоненты | Не меняются. |
| store.js action `createFromFolderPromise` | Не меняется. |
| Vuex-диспатч | Не меняется. |

## Совместимость

- Старые клиенты (включая `webvue3` всех текущих версий) продолжат работать без изменений.
- Скрипты, автоматизирующие импорт через `curl POST /api/utils/createfromfolder?folder=...`, продолжат работать без изменений.
- Никаких новых эндпоинтов не добавляется, никаких старых не удаляется.

## Тестирование контракта

См. [../quickstart.md](../quickstart.md) — ручная проверка end-to-end.
