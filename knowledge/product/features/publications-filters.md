# Publications filters (UNPUBLISH и UNDONE в админке)

> **Status**: active
> **Feature Key**: publications-filters
> **Last Updated**: 2026-10-22
> **Branch**: `550-publications-filters`
> **Source**: OpenProject #326 — «Админка, компонент Публикации»
> Per-feature документ по [Constitution VI FR-009](../../../.specify/memory/constitution.md).

## Что делает

Меняет две кнопки-фильтра в подвале админского раздела «Публикации»
(`webvue3/src/components/Publish/components/PublishTableFooter.vue`):

- **UNPUBLISH** — показывает только полностью готовые песни (статус 6), которым
  не назначена публикация: пусты и `publish_date`, и `publish_time`, флаг
  `free=false`, песня не skip.
- **UNDONE** — новая кнопка: показывает все песни, которые не полностью готовы
  (`id_status < 6`, включая статус 5 «отрендерено, ждёт approve»), кроме skip.

Оба фильтра выводятся в той же раскладке, что и текущий UNPUBLISH:
`CrossSong.unpublications` — колонки это авторы, строки — порядковый номер
песни внутри автора. Дата-сетка (`CrossSong.publications`) для них не
используется: у большинства неготовых песен даты нет.

## Зачем

До #326 UNPUBLISH смешивал всё: в выборку попадали песни любого статуса
(включая нетронутые импорты), `free` не исключался, а условие
`exclusive = true AND id_sponsr = ''` скрывало часть эксклюзивных песен.
Владелец сформулировал две разные задачи:

- «не опубликовано» = готово, но публикация не назначена;
- «не доделано» = ещё не готово, независимо от публикации.

Смешивать их в одном фильтре нельзя: у них разные статусы и разный смысл
(`free` — это опубликовано, но не «доделано»).

## Как работает

### Условия

| Фильтр | `id_status` | Дата/время | `free` | skip | Песен в локальной БД |
|---|---|---|---|---|---|
| UNPUBLISH | `= 6` | оба пусты | `false` | исключён | 2 704 |
| UNDONE | `< 6` | не влияет | не влияет | исключён | 8 221 |

- **«Публикация назначена»** = заданы и дата, и время. Пустое время означает,
  что публикация не назначена. Единственная песня с датой без времени имеет
  `free=true` и в UNPUBLISH не попадает по флагу.
- **skip** = автор с `tbl_authors.skip = true` **или** тег `SKIP` в
  `tbl_songs.tags` (список слов через пробел, без учёта регистра). Сейчас все
  322 песни с тегом `SKIP` принадлежат skip-автору, поэтому счётчики от
  добавления проверки тега не меняются; правило зафиксировано на будущее.
- **Условие `exclusive/id_sponsr` удалено**: эксклюзивная песня без даты теперь
  тоже считается неопубликованной (+59 песен к UNPUBLISH).
- **`free` в UNDONE включается**: неготовая песня не публикуется, даже если
  помечена «всегда бесплатно» (264 песни статуса < 6 с `free=true`).

### Состав UNDONE (2026-10-22)

| Статус | Песен |
|---|---|
| 0 | 33 |
| 1 (импорт, ещё не в работе) | 7 583 |
| 5 (отрендерено, ждёт approve) | 617 |
| Итого | 8 221 |

Статус 5 включён намеренно: иначе 617 песен не видны ни в UNDONE, ни в
UNPUBLISH (там только статус 6).

### Техническая реализация

- **Предикаты**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/PublicationFilters.kt` —
  чистые функции `isUnpublished` / `isUndone` / `hasSkipTag`.
- **Загрузка**: `Publication.getSongListForPublications` — ветки `unpublish` и
  `undone`; SQL сужает по `id_status`, `free`, дате/времени, Kotlin-фильтр
  добавляет skip.
- **API**: `POST /api/publicationsdigest` с `filterCond=undone`; ветки
  `unpublish` и `undone` отдают `CrossSong.unpublications`.
- **UI**: кнопка UNDONE в `PublishTableFooter.vue`, рядом с UNPUBLISH. Кнопки
  «С неготовой» и «С непроверенной» не меняются: они по-прежнему переходят по
  датам.
- **Тесты**: `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/PublicationFiltersTest.kt`.

## Инварианты

- **MUST**: UNPUBLISH показывает только `id_status = 6`; песни в работе туда не
  попадают.
- **MUST**: UNDONE показывает `id_status < 6`, включая 5; статус 6 и выше — нет.
- **MUST**: `free=true` исключает песню из UNPUBLISH (она считается
  опубликованной) и не исключает из UNDONE (она не готова).
- **MUST**: для UNPUBLISH пусты **оба** поля — `publish_date` и `publish_time`.
- **MUST**: skip-автор и тег `SKIP` исключают песню из обоих фильтров.
- **MUST**: оба фильтра используют раскладку `CrossSong.unpublications`
  (по авторам), а не дата-сетку.

## Известные ловушки

- **`free` и `freeAfterOnAir` — разные флаги.** «Всегда в эфире» — это только
  `free=true`. `freeAfterOnAir` действует лишь после наступления эфира и без
  даты не делает песню опубликованной.
- **Статус 5 — не «готово».** Песня отрендерена, но approve не выдан; поэтому
  она в UNDONE и не попадает в UNPUBLISH. Не «исправлять» порог обратно на
  `idStatus < 5` без решения владельца.
- **`tags` — список слов через пробел.** Проверка тега `SKIP` идёт сравнением
  целого слова без учёта регистра, а не подстрокой: `NO-SKIP` и `SKIPPED` не
  считаются тегом.
- **`exclusive/id_sponsr` больше не участвует.** Не возвращать это условие в
  UNPUBLISH: оно скрывало 59 готовых песен без даты.
- **Раскладка.** Оба фильтра используют `CrossSong.unpublications` (по
  авторам), а не дата-сетку: у 7 583 песен статуса 1 даты нет.

## Ссылки

- [OpenProject #326](http://localhost:8080/work_packages/326) — исходная задача.
- [PublicationFilters.kt](../../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/PublicationFilters.kt) — предикаты фильтров.
- [Publication.kt](../../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Publication.kt) — загрузка списков.
- [ApiController.kt](../../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt) — `POST /api/publicationsdigest`.
- [PublishTableFooter.vue](../../../webvue3/src/components/Publish/components/PublishTableFooter.vue) — кнопки фильтров.
- [PublicationFiltersTest.kt](../../../karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/PublicationFiltersTest.kt) — юнит-тесты.
- [dictionaries.md](../../domains/catalog/components/dictionaries.md) — коды `IdStatus` и их использование в фильтрах.

## Что не входит

- Пересмотр SKIPED / IN_WORK и кнопок перехода по датам.
- Массовые операции, пагинация и виртуализация таблицы: текущий UNPUBLISH на
  11 тыс. песен работает в той же раскладке.
- Публичная выдача сайта: фильтры только админские.
