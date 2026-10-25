# Component: boundary-validation

> **Домен**: [monitoring](../domain.md)
> **Компонент**: границы доверия в `karaoke-app`/`karaoke-web` — данные проверяются
> ДО использования, ссылки ведут на существующие узлы.

## Ответственность | Responsibility

Компонент фиксирует **сквозной инвариант**, проверенный аудитом #336 (волна E,
пакет `PKG-S3-25`):

> **Данные, пришедшие извне (HTTP-запрос, страница в интернете, свойство из
> админки, значение из БД), не должны использоваться без проверки.** Проверка
> обязана быть ДО того, как значение попадёт в артефакт (`.mlt`, HTTP-ответ,
> SQL), иначе один кривой вход ломает не одну запись, а весь артефакт.

Компонент дополняет [error-semantics](error-semantics.md): там — про то, что
**сбой не должен выглядеть как штатный результат**; здесь — про то, что
**штатный результат не должен быть получен из непроверенного входа**.

Три класса нарушений, которые этот компонент закрывает:

1. **Непроверенный вход ломает артефакт целиком.** `stringLad` вида
   `undefined|undefined` (его пишет редактор маркеров) ронял `NumberFormatException`
   и генерацию `.mlt` ВСЕЙ песни, а не только слоя табов (BR-239).
2. **Сравнение по ссылке вместо по значению.** `cds !== ""` истинно всегда,
   потому что `replace(...)` возвращает новый объект `String` — ветка «фолбэк»
   не срабатывала НИКОГДА (BR-238).
3. **Ссылка ведёт на несуществующий узел.** `main_bin` ссылался на
   `producer_linetrack_*`, которого в `.mlt` нет (нет `<producer>` — этот тип
   не переопределяет `producer()`), — 182 битые ссылки на 12 свежих проектах
   (BR-242).

## Интерфейсы и Контракты | Interfaces and Contracts

### Канонические проверки

| Вход | Проверка | Код |
|---|---|---|
| HTTP-параметр (число) | `toLongOrNull()`/`toIntOrNull()`; `null`/пусто → «не менять», нечисло → 400 с причиной | `parseOptionalInt` (BR-231) |
| HTTP-параметр (enum-подобный) | белый список допустимых значений | `KaraokeProcessStatuses.entries.none { it.name == value }` (BR-231) |
| HTML-страница из интернета | лимит тела на уровне HTTP-клиента | `Jsoup.maxBodySize(MAX_PAGE_BODY_BYTES)` (BR-228) |
| URL из поисковой выдачи | **один** фильтр на оба движка поиска | `filterUselessLyricsUrls` (BR-229) |
| `stringLad` маркера | регулярное выражение `^[0-5]\|\d{1,2}$` + диапазон строки 0..5 | `parseStringLad` (BR-239) |
| Список из админского свойства | проверка размера + fallback | `Karaoke.countersColors.getOrNull(childId)` (BR-243) |
| Строка из свойства | сравнение ПО ЗНАЧЕНИЮ (`isNotBlank`), не по ссылке (`!==`) | `MkoSplashStart.chordDescriptionText` (BR-238) |
| Ссылка в `main_bin` | цель ссылки обязана существовать | `PRODUCER_TYPES_WITHOUT_PRODUCER` → `nameTractor` (BR-242) |
| Узел MLT | у узла есть ИМЯ (`kdenlivetitle`), а не пустое | `emptyKdenliveTitle()` (BR-241) |

### Механизмы в коде

- **Fail-closed на границе HTTP.** `parseOptionalInt(raw, reason)`: пустое
  значение — «поле не передано, оставить как было»; нечисловое — 400 с
  machine-readable причиной (`bad_process_order`, `bad_process_priority`,
  `bad_process_status`). Наружу уходит `ResponseStatusException`, не 500.
- **Отказ не равен штатному результату.** Отсутствие песни — 404
  (`song_not_found`), неразобранные маркеры — 400 (`bad_source_markers`),
  сбой БД при загрузке альбомов — `AlbumTilesUnavailableException` → 503 на
  публичном эндпоинте (`albums.unavailable`). Раньше все три случая были
  «200 + `Error`/пустой список» (BR-236, BR-240).
- **Пакетная загрузка вместо N+1.** `UuidCache` (BR-230) читает `tbl_uuids`
  ОДНИМ `SELECT` и раздаёт UUID из памяти; точечный `SELECT` на каждый узел MLT
  (~9 000 на песню) устранён.
- **Разделение по типу запроса.** XHR получает короткий JSON `{ok:true}`, а не
  редирект на полную страницу `/processes` (BR-232) — иначе каждое изменение
  поля в админке скачивало всю страницу целиком, а «New prior» — N раз подряд.

## Логика и Алгоритмы | Logic and Algorithms

Порядок проверки в обработчике HTTP-запроса (BR-231, BR-232):

1. Разобрать `id` → `toLongOrNull()`; нет числа → 400 `bad_process_id`.
2. Разобрать `order`/`priority` → `toIntOrNull()`; пусто → `null` («не менять»),
   нечисло → 400 с причиной.
3. Проверить `status` по белому списку `KaraokeProcessStatuses`; неизвестное →
   400 `bad_process_status`.
4. Загрузить процесс; нет такого → 404 `process_not_found`.
5. Применить только переданные (непустые) поля, сохранить, обновить статус
   песни.
6. Ответ: XHR → 200 `{ok:true}`; обычный запрос → 302 на `/processes`.

Проверка ссылки в `main_bin` (BR-242):

1. Для sequence-типа — ссылка `{uuid}` (uuid всегда есть, иначе WARN
   «ПУСТОЙ UUID»).
2. Для не-sequence-типа — `producer_<type>`, ЕСЛИ тип переопределяет
   `producer()`; иначе — `tractor_<type>` (трактор создаётся всегда).

## Зависимости | Dependencies

- → [error-semantics](error-semantics.md) — различение «сбой»/«штатный результат».
- → [log-categories](log-categories.md) — новые категории `infra.admin.processes`,
  `infra.render.tabs`, `infra.render.counters`.
- → [mko-main-bin](../../rendering/components/mko-main-bin.md) — `main_bin` и
  его `<entry>`-ссылки (BR-242).
- → [mlt-generator](../../rendering/components/mlt-generator.md) — генерация
  `.mlt`, `UuidCache` (BR-230).

## Связанные ADR | Related ADRs

- [[ADR-0001]] — raw JDBC: чтение `tbl_uuids` одним запросом вместо N+1.
- [[ADR-0002]] — MLT вместо ffmpeg: `.mlt` — артефакт, который должен быть
  well-formed (BR-241, BR-242).
