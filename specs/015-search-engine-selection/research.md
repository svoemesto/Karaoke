# Phase 0: Research — выбор поискового движка (тексты песен / обложки альбомов)

## Вопрос 1: Как представить «движок поиска» в коде и в существующей системе настроек?

**Decision**: Два Kotlin-enum'а — `LyricsSearchEngine { YANDEX_SYNC, YANDEX_ASYNC, SEARXNG, FOURGET }`
и `AlbumCoverSearchEngine { SEARXNG, FOURGET }` — и два новых `KaraokeProperty`
(`String`-тип, хранящий имя enum-константы) в `listKaraokeProperties`
(`KaraokeProperties.kt`): `lyricsSearchEngine` (default `"FOURGET"`) и
`albumCoverSearchEngine` (default `"SEARXNG"`, сохраняет сегодняшнее поведение
без изменений).

**Rationale**: `KaraokeProperties` — существующий проектный механизм глобальных
настроек, редактируемых через уже существующий UI «Свойства» (`PropertiesTable.vue`)
без необходимости писать новый экран: любая запись в `listKaraokeProperties`
автоматически появляется там (подтверждено по памяти проекта и по коду
`KaraokeProperties.getDTOs()`/`loadList()`). Тип свойства ограничен
Long/Int/Double/Boolean/String (`KaraokeProperties.types()`) — нативного enum
нет, поэтому значение хранится строкой (имя константы) и парсится в enum на
границе использования (`enumValueOf<LyricsSearchEngine>(...)`, с фолбэком на
default при некорректном значении).

**Alternatives considered**:
- Отдельная таблица БД для настроек поиска — отклонено: `KaraokeProperties`
  уже решает эту задачу для всего проекта, отдельная таблица дублирует
  механизм без пользы.
- Числовой/булевый код вместо строкового имени enum — отклонено: строка
  читаема в UI «Свойства» и в логах, не требует комментария-расшифровки.

## Вопрос 2: Как физически реализовать выбор движка для поиска ТЕКСТА песни?

**Decision**: Существующая функция `getSearXNGSearch` (`UtilsAI.kt:88`) —
несмотря на название, после фичи 014 она реально обращается к `fourget`
(через `LyricsFinderService`/`SearchTool.searchUrls`). Она переименовывается
в движок-нейтральное имя и становится диспетчером:

```kotlin
fun getLyricsSearch(
    settings: Song,
    lyricsFinderService: LyricsFinderService,
    engine: LyricsSearchEngine,
    forceResearch: Boolean = false,
): SearchAsync
```

- `forceResearch = true` → сначала `SearchResult.deleteBySongId(...)` и
  `SearchAsync.deleteBySongId(...)` (новые методы, см. `data-model.md`), затем
  обычная логика поиска (которая иначе вернула бы уже существующий
  `SearchAsync`, если он есть — см. Вопрос 4).
- `YANDEX_SYNC` / `YANDEX_ASYNC` → существующая `getYandexSearch(settings,
  async = false/true)` (Yandex Cloud Search API, IAM-токен) — без изменений в
  самой функции.
- `SEARXNG` → **новый** метод `SearchTool.searchUrlsViaSearxng(query)` —
  прямой запрос к `searxng.base-url` (`/search?q=...&format=json&language=ru`),
  т.е. то, что `SearchTool.searchUrls` делал ДО фичи 014, но как отдельный,
  явно называемый метод (а не единственная реализация).
- `FOURGET` → существующий `SearchTool.searchUrls` (brave→yep, см. фичу 014)
  без изменений.

**Rationale**: `SearchTool` уже параметризован через `@Value` на два разных
базовых URL — можно инжектировать оба (`lyrics-search.base-url` для fourget,
`searxng.base-url` для прямого SearXNG-запроса) в один компонент, т.к.
`AlbumCoverService` — отдельный Spring-bean, инжекция одного и того же
`searxng.base-url` в оба компонента не конфликтует (обычная multi-consumer
конфигурация). Не нужен новый docker-контейнер — SearXNG для текстового
поиска обращается к уже существующему сервису `searxng` (тот же контейнер,
что и для обложек, но другой HTTP-путь: `/search` text, а не
`/search?categories=images`).

**Alternatives considered**:
- Оставить `getSearXNGSearch` как есть (только fourget) и добавить SearXNG
  как самостоятельную отдельную функцию с собственным именем — отклонено:
  привело бы к трём похожим функциям (`getSearXNGSearch`/`getFourgetSearch`/
  что-то ещё) вместо одного диспетчера — хуже читается, сложнее добавить
  5-й движок в будущем.

## Вопрос 3: Как физически реализовать выбор движка для поиска ОБЛОЖКИ альбома?

**Decision**: `AlbumCoverService.search(...)` получает параметр
`engine: AlbumCoverSearchEngine` (по умолчанию —
`KaraokeProperties.getString("albumCoverSearchEngine")`). При `SEARXNG` —
поведение не меняется (`searchSearxngImages`, как сегодня). При `FOURGET` —
новый метод `searchFourgetImages(query)`, обращающийся к тому же `fourget`
(`lyrics-search.base-url`), эндпоинт `/api/v1/images?s=...&scraper=brave`
(тот же принцип «мета-поиск, JSON, brave как рабочий на этой машине
scraper» — см. `specs/014-lyrics-search-replacement/research.md`).

**Rationale**: Яндекс-варианты не рассматриваются для обложек (решение
пользователя, см. `spec.md`) — Yandex Cloud Search API возвращает только
текстовые веб-результаты, не картинки. FOURGET и SEARXNG — оба уже умеют
искать картинки (SearXNG — `categories=images`, fourget — свой `/api/v1/images`,
судя по исходникам того же проекта, что и `/api/v1/web`, см. фичу 014).

**Note for implementation**: точный формат ответа `/api/v1/images` (имя
поля с URL картинки, обёртка `image`/`images`) НЕ был проверен curl'ом на
реальном инстансе (в отличие от `/api/v1/web` в фиче 014) — сверить при
реализации по аналогии с уже подтверждённым `/api/v1/web` (структура ответа
явно включает соседний ключ `image` рядом с `web`, см. research.md фичи 014).

**Alternatives considered**:
- Добавить Yandex как «текстовый» источник обложек через доп. скрейпинг
  og:image со страниц — отклонено пользователем как избыточное усложнение
  вне рамок этой задачи.

## Вопрос 4: Как обойти кэширующее поведение «уже есть SearchAsync — вернуть его»?

**Decision**: `forceResearch` булев-параметр диспетчера (см. Вопрос 2)
выполняет явное удаление СНАЧАЛА, до вызова движко-специфичной логики поиска
— так существующая проверка `if (searchAsyncList.isNotEmpty()) return
searchAsyncList.first()` внутри `getYandexSearch`/фактического SearXNG/
fourget-пути просто не находит старых записей и идёт по «первому поиску»
ветке кода без изменений в этой ветке.

**Rationale**: Минимальное вмешательство — не трогаем саму логику поиска
внутри `getYandexSearch`/`getLyricsSearch`, только то, что предшествует её
вызову. Соответствует FR-007 спецификации.

**Alternatives considered**:
- Добавить параметр `ignoreCache: Boolean` прямо в `getYandexSearch`/поиск —
  отклонено: размывает ответственность (эти функции и так возвращают
  существующую запись как кэш для ВСЕХ вызовов, включая штатный автопоиск при
  импорте песни — трогать это поведение рискованно для остального пайплайна).
  Явное удаление снаружи — безопаснее и локальнее.

## Вопрос 5: Yandex-варианты — не нарушают ли Principle I конституции (self-contained)?

**Decision**: PASS, без нарушения. `getYandexSearch`/`searchSongInYandex`
(Yandex Cloud Search API, IAM-токен) — уже существующий в кодовой базе
механизм (был до фичи 014), не новая зависимость. Пользователь явно запросил
сделать его выбираемой опцией в этой самой задаче — это и есть «получение
одобрения пользователя», которое требует Principle I для нового внешнего API
в горячем пути.

**Rationale**: Principle I требует одобрения при ВВЕДЕНИИ новой внешней
зависимости в горячий путь; здесь зависимость не новая и одобрение получено
явно в постановке задачи.

## Вопрос 6: Как автоматически чистить результаты поиска при готовности песни и массово — для уже готовых?

**Decision**: Переиспользовать уже существующую точку в `Song.saveToDb()`
(`model/Song.kt:4991`) — переменную `crossedReadyThreshold` (`savedSettings.idStatus
< 3L && this.idStatus >= 3L`), которая сегодня уже используется для триггера
`HealthReport.recomputeAndBroadcast(...)` (см. комментарий в коде про порог
`idStatus>=3` для публичного плеера, PR #44). Рядом с этим существующим
вызовом добавляется `SearchResult.deleteBySongId(id, ...)` +
`SearchAsync.deleteBySongId(id, ...)` (те же методы, что и для FR-008, см.
`data-model.md`).

Для массовой очистки уже готовых песен — новый эндпоинт по образцу уже
существующего `POST /api/utils/recalcplayerreadiness`
(`ApiController.doRecalcPlayerReadiness`): фоновый `thread { ... }`, проход по
всем песням с `idStatus >= 3`, `deleteBySongId` для каждой, по завершении —
toast через `SNS.send(SseNotification.message(...))` с итоговым количеством.
Кнопка на `HomeView.vue` — рядом с уже существующей кнопкой «Пересчитать
готовность плеера» (`recalcPlayerReadiness`), тот же UI-паттерн
(`.button-action`, vuex-action → `promisedXMLHttpRequest`).

**Rationale**: `crossedReadyThreshold` — уже посчитанное, проверенное
боевое условие «статус только что пересёк порог готовности» — не нужно
изобретать новую детекцию перехода статуса. `doRecalcPlayerReadiness` — уже
существующий, проверенный паттерн «тяжёлая фоновая массовая операция по всем
песням с тостом по SSE», ровно то, что нужно для FR-012 (не блокировать UI на
потенциально тысячах песен).

**Alternatives considered**:
- Триггерить очистку через отдельный SQL-триггер в БД (аналогично
  `recordhash`) — отклонено: это бизнес-логика уровня приложения (что считать
  «готовой» песней), а не структурный инвариант данных; усложнило бы миграции
  без выгоды.
- Делать очистку синхронно внутри `saveToDb()` без фонового потока для
  массовой версии — неприменимо к массовой операции (потенциально тысячи
  песен), но для одиночного перехода статуса (FR-011) синхронный вызов
  уместен и оставлен как есть (та же транзакционная граница, что и
  `recomputeAndBroadcast`).
