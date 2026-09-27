# Component: Thymeleaf templates (karaoke-web)

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: 8 Thymeleaf-шаблонов `templates/`.


## Ответственность | Responsibility


8 Thymeleaf-шаблонов `templates/`.

## Назначение

`karaoke-web/src/main/resources/templates/*.html` — Thymeleaf
HTML-шаблоны для статических страниц (не SPA). Рендерятся
сервер-сайд через `MainController` (см. [main-controller.md](main-controller.md)).

## Интерфейсы и Контракты | Interfaces and Contracts

Контракт шаблона — пара «endpoint → имя view (`return "..."`)», а
также набор model-атрибутов, которые контроллер кладёт в `Model` и
которые шаблон читает через `${...}`. Ниже — по фактическому коду
`MainController` и самим шаблонам.

| # | Шаблон | Endpoint | Model-атрибуты (контракт) |
|---|---|---|---|
| 1 | `main.html` | `GET /` | `onSponsr`, `onAir`, `exclusive`, `inWork`, `total`, `latestNews` |
| 2 | `zakroma.html` | `GET /zakroma` | `author_init`, `authors`, `zakroma` |
| 3 | `song.html` | `GET /song?id=` | `song` |
| 4 | `song-removed.html` | `GET /song?id=` (SKIP-песня) | `song` |
| 5 | `filter.html` | `GET /filter` | `authors`, `song` |
| 6 | `statbysong.html` | `GET /statbysong` | `stats` (`List<StatBySong>`), `totalCount` |
| 7 | `webevents.html` | `GET /webevents` | `webevents` |
| 8 | `testpage.html` | `GET /testpage/{id}` | `song` |

**Контракт конфигурации** (из `karaoke-web/src/main/resources/application.yml`,
секция `spring.thymeleaf`): `prefix: classpath:/templates/`,
`suffix: .html`, `mode: HTML`, `encoding: UTF-8`,
`check-template-location: true`, `cache: false`. То есть имя view —
это имя файла без расширения (`return "main"` → `templates/main.html`).

**NB**: ни один из 8 шаблонов не объявляет `xmlns:th` (проверено
grep по `templates/*.html`) — при `mode: HTML` префикс пространства
имён Thymeleaf 3 не требуется.

## Логика и Алгоритмы | Logic and Algorithms

### Поток рендера

1. Spring MVC вызывает метод `MainController` по URL.
2. Метод считает данные и складывает их в `Model` через
   `model.addAttribute(...)` — это и есть контракт с шаблоном.
3. Метод возвращает **имя view** (строку), не HTML.
4. `ThymeleafViewResolver` достраивает имя по `prefix`/`suffix` из
   `application.yml` и рендерит файл сервер-сайд
   (`cache: false` — шаблон перечитывается при каждом рендере, без
   кеша в памяти).

Реальный пример (фрагмент `main.html` — блок «Последние новости»):

```html
<tr th:if="${latestNews != null and !#lists.isEmpty(latestNews)}">
    <td colspan="6" style="width: 400px; padding-top: 16px; padding-bottom: 8px">
        ...
    </td>
</tr>
<tr th:each="n : ${latestNews}"
    th:if="${!#strings.isEmpty(n.link) and !#strings.isEmpty(n.title)}"
    style="cursor: pointer"
    th:attr="data-link=${n.link}">
```

Второй короткий шаблон — `song.html` (9 строк) целиком: `<title th:text="${song.songName} ...">`
и `<img th:src="'/api/public/song-vk-image/' + ${song.id}" ...>`.

Реальный пример контроллера (`MainController`, страница `/`):

```kotlin
@GetMapping("/")
fun main(model: Model, request: HttpServletRequest): String {
    model.addAttribute("total", StatBySong.getCountSongsTotal(database = WORKING_DATABASE))
    model.addAttribute(
        "latestNews",
        try { News.loadPublished(database = WORKING_DATABASE, limit = 5, offset = 0) }
        catch (e: Exception) { emptyList() },
    )
    doRegisterEvent(mapOf("eventType" to EventType.CALL_REST.dbValue, ...), request)
    return "main"  // → templates/main.html
}
```

### Краевые случаи

- **SKIP-песня**: `/song?id=` с тегом `SKIP` возвращает view
  `song-removed` вместо `song` (проверка по `song.tags`), HTTP-статус
  остаётся 200.
- **Сбой БД на главной**: `News.loadPublished` обёрнут в `try/catch` —
  `latestNews` остаётся пустым, `main.html` рендерит блок по
  `th:if="${latestNews != null and !#lists.isEmpty(latestNews)}"`.
- **Пустой поиск**: `/filter` при суммарной длине запроса `< 3`
  символов отдаёт пустой `song`, не ходя в БД.
- **Аналитика**: почти каждая страница после сборки модели вызывает
  `doRegisterEvent(...)` (`CALL_REST` + `RestName` страницы); исключения —
  `/statbysong`, `/webevents`, `/testpage/{id}`.
- **`/statbysong`**: `stats` — top-1000 (`getStatBySong(limit = 1000)`),
  `totalCount` — полное число для баннера; полная выгрузка — через REST
  `/api/stats/by-song`.

### Данные страниц (источники)

- `main.html`: 5 счётчиков `StatBySong` + `News.loadPublished(limit = 5)`.
- `zakroma.html`: `Song.loadListAuthors(...)` (с учётом `canSeeSkipped`)
  + `Zakroma.getZakroma(onlyPublished = true)`.
- `filter.html`: `Song.loadListAuthors` + `Song.loadListFromDb`
  (фильтр `id_status >= 6`).
- `song.html` / `song-removed.html` / `testpage.html`:
  `Song.loadFromDbById(id)`.
- `statbysong.html` / `webevents.html`: `StatsByEvents`.

## Hot paths

- Каждое открытие `/`, `/zakroma`, `/song` = Thymeleaf render
  (server-side, без SPA).
- SSR-кеширование (если есть) — `Cache-Control: max-age=N` (Pass 343+).

## Зависимости | Dependencies

- [main-controller.md](main-controller.md) — рендерит эти шаблоны.
- [services-overview.md](services-overview.md) — данные для шаблонов.

## Известные TODO

- [ ] **CSS/JS** — где определены (Pass 343+)?
- [ ] **Thymeleaf version** — какая используется?
- [ ] **i18n** — поддерживается ли (если нет — добавить, см. CLAUDE.md).
- [ ] **Кеширование** — `Cache-Control` headers, ETag.

## Changelog

- **Pass 481** (2026-09-27, spec `481-knowledge-domain-karaoke-web`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 439** (2026-09-09): Initial. Автор: agent (Karaoke).
