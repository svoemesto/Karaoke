# Feature: пакет аудита PKG-S2-12 (karaoke-app: COMPAT, DATA, ERR, PERF, RACE, SEC)

**Spec**: [`specs/375-pkg-s2-12/report.md`](../../specs/375-pkg-s2-12/report.md)
**Status**: active (ветка `597-pkg-s2-12`, пакет `PKG-S2-12` аудита #336)
**Created**: 2026-10-24
**Last updated**: 2026-10-24
**Source**: OpenProject #375 — пакет `PKG-S2-12` бэклога аудита #336
**Feature Key**: `pkg-s2-12-audit-fixes`

## Что делает

Закрывает 15 записей аудита: BR-188, BR-195, BR-200, BR-201, BR-205, BR-206,
BR-213, BR-214, BR-219, BR-227, BR-234, BR-235, BR-244, BR-251, BR-266.
Меняет поведение legacy-страниц `karaoke-app` (`/songs`, `/songs2`,
`/publications`, `/processes`, `/unpublications`), кеша статистики, sync-контура
LOCAL↔SERVER, записи файла настроек, выбора автора для поиска альбома и
LLM-извлечения текста песни.

## Зачем

Это S2-дефекты публичного/админского периметра и денежного пути: вечный цикл и
OOM на `/unpublications`, неверные цифры дашборда из-за кеша без параметров,
127–438 МиБ HTML на страницах каталога, мёртвая live-подписка STOMP, 500 на
«Сохранить» в обеих legacy-формах песен, тарифы с отрицательной ценой и без
периода (оплата без премиума), prompt injection из внешней страницы, пустышка
«Обновить хранилище», OOM от `voice=10^8`, гонка записи настроек, ложный успех
синхронизации с удалением источника и остановка очереди из-за
`StackOverflowError`.

## Как работает

- **BR-188** — `popPublicationStacks` в `model/Publication.kt`: раскладка идёт,
  пока есть непустая стопка (раньше — захваченный максимум размера).
- **BR-195** — `StatsCacheKey.forEndpoint(endpoint, target, params)` включает
  нормализованный `target` и `days`/`mode`; `StatsController.respondCached`
  строит ключ только через него.
- **BR-200/206/214** — `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Pagination.kt`: `LEGACY_PAGE_SIZE = 200`,
  `PUBLICATION_PAGE_SIZE = 31`, `pageOffset`/`pageSlice`/`hasNextPage`. Контроллер
  грузит `LIMIT+1` (песни) или режет готовый список (расписание), шаблоны
  рисуют `#areaPager`, передают `filter_page` и сохраняют фильтры.
- **BR-201** — из `processes.html`, `publications.html`, `unpublications.html`
  удалены SockJS/STOMP (брокера в `karaoke-app` нет с 2024-08-15); на
  `/processes` — polling `GET /process/lastupdated` каждые 5 с.
- **BR-205/213** — `songsUpdate`/`songs2Update` объявляют отсутствующие в формах
  `song_resultVersion` и `song_idBoostyFiles` как `String?` и не пишут поле,
  если параметр не передан (fail-closed: не затираем сохранённое значение).
- **BR-219** — `validateTariffValues` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/TariffValidation.kt`) до записи;
  `saveWithSingleDefault` снимает прежний default и сохраняет новый в одной
  транзакции; `PriceTariff.getDefault` — один SELECT с явным порядком.
- **BR-227** — `ScraperAgent.extractLyrics(pageContent, artist, songTitle)`
  передаёт данные между `PAGE_DATA_START`/`PAGE_DATA_END`, системный промпт
  запрещает исполнять инструкции из данных; `LyricsFinderService` передаёт
  исполнителя и название.
- **BR-234** — `MainController.doCollectStore` грузит реальный список песен,
  вызывает `collectDoneFilesToStoreFolderAndCreate720pForAllUncreated` с
  `THREAD_LANE_HEAVY_RENDER` и возвращает `[countCopy, countCode]`.
- **BR-235** — `isValidVoiceIndex(voice, countVoices)` до вызова
  `setSourceTextAndMarkers`; вне диапазона — `400 bad_voice`.
- **BR-244** — `KaraokeProperties`: `propertiesWriteLock` вокруг «карта →
  encode → запись», `Files.createTempFile` на вызов (общий `<имя>.tmp`
  убран), `writePropertiesFileAtomic` дополнительно сериализована JVM-локом.
- **BR-251** — `postChunksConfirming` отправляет чанки по одному и
  останавливается на первом отказе; `SyncResult.error=true` при неподтверждённой
  цели, источник не очищается, `POST /api/sync/run` отвечает `502`.
- **BR-266** — `authorRequestOrder` даёт один круг кандидатов; рекурсия
  заменена проходом, пустой результат — `null`; `ymId != ""` вместо `!==`.

## Инварианты

1. Legacy-страница каталога не отдаёт весь список: всегда `LIMIT`/срез по
   странице, фильтры и `filter_page` сохраняются в URL.
2. В шаблонах `karaoke-app` нет подключения к STOMP/WebSocket: брокер живёт
   только в `karaoke-web`.
3. Отсутствующий в форме параметр сохранения песни не затирает значение в БД.
4. Тариф не сохраняется, если `priceRub < 0` или `scope=SITE` с
   `periodDays <= 0`; default на scope ровно один по приложению.
5. Текст внешней страницы в LLM-промпте — только данные, ограниченные
   маркерами; инструкции внутри не выполняются.
6. `SyncResult.error=true` означает «цель не подтвердила», списки пусты,
   источник в move-режиме не очищался.
7. Файл настроек пишется под блокировкой и с уникальным временным файлом.
8. Выбор автора для поиска альбома — конечный обход, не рекурсия.

## Известные ловушки

- **`StatsCacheKey(endpoint, emptyMap())` возвращать нельзя**: TTL 60 с
  смешивает `target`/`days`/`mode` и показывает цифры другой БД. Новые
  параметризованные endpoint'ы обязаны передавать `params`.
- **`SELECT *` без `LIMIT` на `/songs`/`/songs2`** снова даст 90–438 МиБ HTML;
  `withoutMarkersAndText=true` не уменьшает I/O — тяжёлые колонки всё равно
  читаются. Явный список колонок — отдельная задача (за владельцем).
- **`filter_page` живёт в `#areaPager`**, а `doFilterForm` читает его из DOM.
  Если перенести пейджер/скрытое поле или переименовать id, фильтры потеряют
  номер страницы (или упадут на `null.value`).
- **`doFilterAll`/`doFilterFromToday` в `publications.html` не передают
  `filter_page`** — это сброс на первую страницу, а не баг пагинации.
- **`songsUpdate`/`songs2Update` не обновляют `song_resultVersion`** с legacy-
  страниц: поля в форме нет, а fail-closed запрещает писать `null`. Если
  редактирование версии нужно — сначала добавить hidden-поле с текущим
  значением (решение за владельцем).
- **`saveWithSingleDefault` — транзакция приложения, а не БД.** Partial unique
  index на `(scope, is_default)` не добавлен (запрет трогать БД в этом пакете);
  при прямом SQL-доступе два default всё ещё возможны.
- **`postChunksConfirming` при отказе не пытается «доотправить» остаток**:
  частичное применение в цели возможно, точный состав неизвестен, поэтому
  `SyncResult` возвращает пустые списки и `error=true`. Повторный запуск
  идемпотентен по recordhash, но UI обязан показать ошибку.
- **`getAuthorForRequest` ходит в БД на каждого кандидата** (до N авторов за
  один круг). Это дешевле прежней рекурсии (которая ещё и заново читала весь
  список авторов на каждом уровне), но при большом каталоге автор-кандидат
  остаётся дорогим — кеширование списка авторов не делалось.
- **LLM всё ещё может вернуть текст чужой песни**: изоляция промпта снимает
  prompt injection, но не гарантирует соответствие; автоматическая запись в
  `Song.sourceText` сохранена.

## Ссылки

- [Отчёт пакета и доказательства «до/после»](../../specs/375-pkg-s2-12/report.md)
- [Карточки аудита: P-05, P-07, P-10, P-11, P-12, P-17, P-20, P-26, P-27, P-28](../../specs/336-code-audit/findings/)
- [Бэклог аудита #336, блок PKG-S2-12](../../specs/336-code-audit/backlog.md)
- [SSoT: Utils.kt и legacy-страницы](../../knowledge/system/utilities.md)
- [SSoT: файл настроек](../../knowledge/domains/processing/components/karaoke-properties.md)
- [SSoT: two-db-sync](../../knowledge/domains/processing/components/two-db-sync.md)
- [SSoT: Publication](../../knowledge/domains/catalog/components/remaining-models.md)
- [SSoT: тарифы](../../knowledge/domains/monetization/domain.md)
- [SSoT: статистика и кеш](../../knowledge/domains/stats/domain.md)
- [SSoT: LLM-клиенты](../../knowledge/domains/integration/domain.md)
