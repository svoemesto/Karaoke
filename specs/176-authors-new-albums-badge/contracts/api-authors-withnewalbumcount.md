# Contract: `POST /api/authors/withnewalbumcount`

**Feature**: 176-authors-new-albums-badge
**Date**: 2026-08-12
**Spec**: [spec.md](./spec.md)
**Related**: [data-model.md](./data-model.md)

## Назначение

Возвращает количество авторов из `tbl_authors`, для которых выполняется условие `haveNewAlbum = true` (см. [data-model.md](./data-model.md)). Используется для бейджа в левом сайдбаре админки `webvue3`.

## Endpoint

| Свойство | Значение |
|----------|----------|
| **Метод** | `POST` |
| **Путь** | `/api/authors/withnewalbumcount` |
| **Авторизация** | Нет (`permitAll()` — админка без авторизации, см. Principle V конституции) |
| **Content-Type запроса** | `application/x-www-form-urlencoded` (Spring `@RequestParam`) |
| **Content-Type ответа** | `text/plain` (raw int) |
| **Идемпотентность** | Да — чистое чтение, никаких мутаций |

## Параметры запроса

Нет. Endpoint не принимает параметров (в т.ч. нет `target` — в отличие от `/api/chat/unreadcount`, см. D-3 в [research.md](./research.md)).

## Ответ

### Успешный (200 OK)

Тело ответа — plain `int` (raw integer, как `SongEditorController.submittedCount`):

```http
HTTP/1.1 200 OK
Content-Type: text/plain
Content-Length: 2

12
```

Примеры значений:
- `0` — нет авторов с новыми альбомами, бейдж скрыт
- `3` — три автора с новыми альбомами
- `1247` — для крупной базы (теоретически)

### Ошибочный

Endpoint **не** возвращает 4xx/5xx в нормальном режиме (только SELECT). При исключении JDBC (например, БД недоступна) Spring default handler вернёт 500; для polling-клиента это означает «оставить предыдущее значение бейджа» (см. FR-010 спеки).

## SQL-запрос

Выполняется через `Author.countWithNewAlbum(database: KaraokeConnection): Int` (новый companion-метод):

```sql
SELECT COUNT(*) AS cnt
FROM tbl_authors
WHERE watched = true
  AND (ym_id <> '' OR vk_id <> '')
  AND (last_album_ym <> last_album_processed
       OR last_album_vk <> last_album_processed)
```

Условие WHERE — точная SQL-копия `Author.haveNewAlbum` (`Author.kt:94-97`) и `Author.getWhereList["haveNewAlbum=+"]` (`Author.kt:170-178`).

## Поведение клиента

### Запрос (webvue3)

```javascript
// webvue3/src/components/Authors/store.js
loadAuthorsWithNewAlbumCount(ctx) {
  return promisedXMLHttpRequest({
    method: 'POST',
    url: '/api/authors/withnewalbumcount',
  })
    .then((data) => {
      ctx.commit('setAuthorsWithNewAlbumCount', parseInt(data, 10) || 0)
    })
    .catch((error) => console.log(error))
}
```

`promisedXMLHttpRequest` — стандартная обёртка проекта (`webvue3/src/lib/utils.js`), используется во всех polling-action.

### Отображение

```vue
<!-- webvue3/src/App.vue, sidebar -->
<router-link class="nav-link authors-nav-link" to="/authors">
  Авторы
  <span v-if="authorsWithNewAlbumCount > 0" class="authors-nav-badge">{{ authorsWithNewAlbumCount }}</span>
</router-link>
```

`authorsWithNewAlbumCount` — computed-property, читающее `this.$store.getters.getAuthorsWithNewAlbumCount`.

## Polling

| Свойство | Значение |
|----------|----------|
| Интервал | 20 000 ms (константа `AUTHORS_NEW_ALBUMS_POLL_INTERVAL_MS`) |
| Первый вызов | В `App.vue.mounted()` сразу после инициализации SSE (синхронно) |
| Cleanup | В `App.vue.beforeUnmount()` через `clearInterval(this.authorsWithNewAlbumPollTimer)` |

Интервал и поведение совпадают с уже существующими бейджами «Чат» (`CHAT_UNREAD_POLL_INTERVAL_MS`) и «Задания редактора» (`SONGEDITOR_SUBMITTED_POLL_INTERVAL_MS`).

## Производительность

| Метрика | Ожидание |
|---------|----------|
| Латентность endpoint | ≤ 100 ms на 18k строк (SC-004) |
| Частота вызовов | 3 раза в минуту (1 / 20 сек) |
| Нагрузка на БД | ~9 SELECT/мин, мизерная |
| Размер ответа | 1-7 байт (число) |

## Совместимость

- **Обратная совместимость**: endpoint новый, никаких breaking changes.
- **Версионирование**: не применимо (нет `/v1/` префиксов, см. существующие контроллеры).
- **Тестирование**: ручное (по [quickstart.md](./quickstart.md)). Автотестов нет (CI для бэка отсутствует — см. AGENTS.md «Тесты»).

## Известные ограничения

1. Endpoint читает только `WORKING_DATABASE = Connection.local()`. На проде (где `karaoke-app` не развёрнут) вызов вернёт ошибку, но бэкенд на проде не обслуживает админку — N/A.
2. Не включает архивированных/удалённых авторов (в `tbl_authors` soft-delete не используется).
3. Тег SKIP не учитывается — намеренно (см. [data-model.md](./data-model.md)).
