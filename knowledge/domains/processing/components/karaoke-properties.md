# Component: karaoke-properties

> **Домен**: [processing](../domain.md)
> **Компонент**: единая точка конфигурации рендера
> (`KaraokeProperties.kt` + `/sm-karaoke/system/Karaoke.properties`).

## Ответственность | Responsibility

В Karaoke **~150 параметров** управляют рендерингом караоке-видео:
размер шрифта, цвета, позиции, размер видео, fps, codec, fade-in/out,
background gradient, и т.д. Все они живут в одном месте — этот
компонент — единая точка правды.

**[WARN] Правка параметров рендера** через UI обязательна, нельзя
править напрямую в `Karaoke.properties` — UI-state рассинхронизируется.

## Интерфейсы и Контракты | Interfaces and Contracts

### `KaraokeProperties.kt` — Kotlin-класс, НЕ Spring-биндинг

- **Поведение** (проверено по коду, Pass 463): обычный `class KaraokeProperties`
  с `companion object`. Ни `@ConfigurationProperties`, ни Spring-бина здесь нет —
  прежняя версия этой страницы описывала механизм, которого в коде не существует.
  Значения читаются из in-memory `karaokePropertiesMap`, которую при первом
  обращении наполняет `loadPropertiesMap()` (значения файла + дефолты из
  `listKaraokeProperties`). Чтение идёт **на каждый вызов**
  (`KaraokeProperties.getInt(...)`), поэтому hot-fix действует без передеплоя.
- **Использование**: во всех mko-классах и рендер-сервисах.
- **Размер**: ~150 полей (см. live-документацию по конкретным группам).

### `/sm-karaoke/system/Karaoke.properties` — внешний файл

- **Где**: на прод-сервере, в `/sm-karaoke/system/Karaoke.properties`.
- **Формат**: НЕ Java properties (как утверждала прежняя версия). Одна строка на
  параметр, каждая строка — Base64 от JSON `{key, serializableValue}`
  (`encodePropertiesMap()`); после записи файл получает `chmod 666`.
- **Запись атомарная и с резервной копией** (Pass 468): содержимое пишется во
  временный файл рядом и переименовывается поверх (`ATOMIC_MOVE`), а перед заменой
  предыдущая версия копируется в `/sm-karaoke/system/Karaoke.properties.bak`
  (`writePropertiesFileAtomic()`). До этого была запись «на месте»
  (`File.writeText`), то есть файл сначала усекался: падение процесса в этот момент
  оставляло частично записанный файл.
- **Битый файл больше не откатывает настройки молча** (Pass 468). Раньше исключение
  разбора проглатывалось (`catch (_: Exception) {}`), карта оставалась пустой, все
  параметры подменялись дефолтами и при следующем изменении настройки дефолты
  записывались в файл — то есть один сбой чтения безвозвратно терял настройки
  оператора. Теперь: сначала пробуем `.bak`, о неудаче пишем в лог, а повторное
  чтение файла на каждое обращение к свойству (`propertiesLoadAttempted`) исключено.
- **[WARN] Руками файл править бесполезно**: карта читается один раз и живёт в
  памяти, а `savePropertiesMap()` затем перезапишет файл **из памяти**. Правка
  файла мимо UI не только не применится, но и будет затёрта.

### Admin UI — `webvue3/src/components/Properties/`

- **Поведение**: редактирование параметров (`PropertiesTable.vue` + `store.js`).
  Компонента `KaraokePropertiesView.vue`, на которую ссылалась прежняя версия,
  в репозитории нет.
- **Сохранение**: `POST /api/properties/setproperty` (рядом —
  `/properties/getproperties`, `/properties/getproperty`,
  `/properties/setpropertydefault`). Endpoint `PATCH /api/karaoke-properties/save`
  в коде отсутствует.

## Логика и Алгоритмы | Logic and Algorithms

### Группы параметров

Параметры сгруппированы по домену рендера:

| Группа | Что описывает | Кол-во |
| --- | --- | --- |
| `video.*` | Размер (1280×720 / 1920×1080), fps, codec | ~10 |
| `lyrics.*` | Шрифт, размер, цвет, подсветка, тени | ~30 |
| `background.*` | Градиент, blur, opacity, image-fit | ~15 |
| `audio.*` | Громкость вокала/аккомпанемента по версии | ~5 |
| `marker.*` | Цвета маркеров на таймлайне, толщина | ~8 |
| `fade.*` | In/Out длительность, кривая | ~5 |
| `karaoke-title.*` | Заголовок песни: шрифт, позиция, тень | ~10 |
| `demo-fragment.*` | Стартовая/конечная секунда DEMO | ~3 |
| ... | (прочие группы) | ~60 |

### Цикл обновления параметра

```
User in Admin UI → PropertiesTable.vue (webvue3/src/components/Properties/)
  ↓ POST /api/properties/setproperty
KaraokeProperties.set(key, value)
  ↓
[1] Обновляется in-memory karaokePropertiesMap — она и есть источник правды
    на время работы процесса
[2] savePropertiesMap() пишет ВЕСЬ файл атомарно: Base64(JSON) построчно во
    временный файл -> ATOMIC_MOVE поверх, предыдущая версия уходит в .bak,
    затем chmod 666
  ↓
[3] Следующее чтение KaraokeProperties.get*(...) отдаёт новое значение —
    передеплой не нужен
```

**[WARN]** Значения читаются в момент построения рендер-проекта, поэтому уже
идущий рендер продолжает использовать то, что прочитал при старте. Чтобы
изменение вступило в силу, нужно дождаться завершения текущего рендера.

## Не-рендер параметры | Operational properties

Часть `KaraokeProperty` не относится к рендеру, а управляет поведением
сервисов. В частности:

- **`vpnHomeCountry`** (default `"RU"`, Pass 427, #151) — **список** кодов
  стран сервера без ВПН (ISO 3166-1 alpha-2) через `,`/`;`/пробел, например
  `DE,RU`. Используется `isVpnActive()` (`Utils.kt`) и веткой
  `checkLastAlbumYm` (`AlbumSearchResult.Unknown`). Машина может легально
  находиться в нескольких странах, поэтому значение — список. Пустое значение
  → fail-open (ВПН не считается). Подробнее — [utilities.md](../../../system/utilities.md).
- **`vpnCheckCacheTtlSeconds`** (default `300`, Pass 454) — сколько секунд
  кэшировать определённую страну внешнего IP в `isVpnActive()`. `0` или меньше —
  кэш выключен, страна определяется при каждом вызове. Кэшируется только ФАКТ
  страны, а решение о ВПН считается заново из `vpnHomeCountry`, поэтому правка
  списка стран действует сразу, не дожидаясь TTL. Неудача не кэшируется.
  Смысл кэша — защита суточной квоты резервного `ipapi.co` (~1000 запросов/сутки
  против 1440 при вызове раз в минуту), а НЕ частота вызовов: она невелика
  (в `KaraokeProcessWorker` вызов ограничен `requestNewSongTimeoutMs`, в
  `AutoOneClickSyncScheduler` — раз в минуту). Реализация — общий `PollingCache`
  (Pass 456). Подробнее — [utilities.md](../../../system/utilities.md).

## Зависимости | Dependencies

- → [domain](../domain.md) — используется во всех mko-классах.
- → [playwright-rendering](playwright-rendering.md) — читает параметры
  для каждого кадра.
- → [rendering domain](../../rendering/domain.md) — `MLTProject` строится
  на основе параметров.

## Ловушки и предупреждения

**[WARN] Прямая правка `Karaoke.properties` без UI** → изменения не
применятся (карта уже прочитана в память), а при следующем сохранении через UI
будут перезаписаны. Правьте только через
`webvue3/src/components/Properties/PropertiesTable.vue`.

**[WARN] Изменения не применяются к активным рендерам** — нужно
дождаться завершения.

**[WARN] Параметр `video.codec`** — если переключить с `libx264` на
`libx265`, время рендера вырастет в 5-10 раз. Не делать без миграционного
эпика.

**[WARN] `audio.vocals.volume` и `audio.accompaniment.volume`** —
изменение вступает в силу только для **новых** рендеров; существующие
MP4 в MinIO не перекодируются автоматически. Для перекодировки —
отдельный эпик «re-render all».

## Связанные ADR | Related ADRs

- ADR по admin UI для KaraokeProperties (TODO).
