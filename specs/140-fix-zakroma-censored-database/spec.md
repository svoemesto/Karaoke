# Feature Specification: Исправление падения /api/public/zakroma после PR #179

**Feature Branch**: `140-fix-zakroma-censored-database`

**Created**: 2026-08-04

**Status**: Ready for review

**Input**: Срочный баг — Закрома на сайте перестали работать, у любого автора
`/api/public/zakroma` возвращает 500/Handler dispatch failed. Лог показывает
`IllegalStateException: Property APP_WORK_ON_SERVER should be initialized before get.`

## Контекст

После PR #179 (commit `72ea8eba`) на проде (`karaoke-web`, контейнер без модуля `karaoke-app`)
перестали работать все эндпоинты, в которых `String.censored()` вызывается из публичного
пути — на момент репорта это `/api/public/zakroma` и `/api/public/zakroma?specialBucket=true`,
но любое другое обращение к `Song.songName.censored()` / `Song.songNameCensored` /
`Publication.publishNNtext` / `Song.getDescription*` упало бы тем же исключением.

## Корневая причина

`String.censored()` (karaoke-app/Extentions.kt:210) получил дефолтный параметр
`database: KaraokeConnection = WORKING_DATABASE`. Без явной передачи `database` это ссылка на
karaoke-app-глобал `com.svoemesto.karaokeapp.WORKING_DATABASE`, который инициализируется через
`Connection.local()` → `Connection.<clinit>` читает `APP_WORK_ON_SERVER`
(`Delegates.notNull<Boolean>()` в `KaraokeAppService.kt:14-15`).

`APP_WORK_ON_SERVER` инициализируется **только** в `KaraokeAppService.init{}` (модуль
karaoke-app), а на проде работает только karaoke-web (без бина `KaraokeAppService`). Поэтому
любой вызов `censored()` из karaoke-web без явного `database` падает с
`IllegalStateException: Property APP_WORK_ON_SERVER should be initialized before get` →
`ExceptionInInitializerError` для `ConstantsKt` → 500.

PR #179 исправил 4 места (Telegram/Vk/News TemplateServices, DictionariesController), но
**пропустил** 35 других вызовов `.censored()` без явного `database` — в т.ч. критичный
`Zakroma.buildFromSongs:203`, который и был триггером бага.

## Решение

Сделать `database` обязательным параметром `String.censored()` без дефолта (compile-time
гарантия, что такая регрессия не повторится), и прокинуть явный `database` во все 35 мест
вызова:

| Файл | Кол-во мест | Какой `database` передаётся |
|------|-------------|------------------------------|
| `karaoke-app/Extentions.kt` | сигнатура | `database` стал обязательным, без дефолта |
| `karaoke-app/model/Zakroma.kt` | 1 | параметр `database` `buildFromSongs` |
| `karaoke-app/model/Song.kt` | 12 | поле `this.database` (KaraokeDbTable) |
| `karaoke-app/model/Publication.kt` | 14 | `publishXX!!.database` (поле Song) |
| `karaoke-app/UtilsPictures.kt` | 7 | `song.database` (admin-only функции) |
| `karaoke-app/controllers/MainController.kt` | 1 | `WORKING_DATABASE` (admin-эндпоинт `/utils/censored`) |
| `karaoke-app/services/*TemplateService.kt` | только KDoc | — (обновлены комментарии) |
| `karaoke-app/textfiledictionary/TextFileDictionary.kt` | только KDoc | — (обновлён комментарий) |

**Итого**: 10 файлов, +50/−46 строк.

## Проверка

1. `curl -G "http://localhost:8897/api/public/zakroma" --data-urlencode "author=Кино"` →
   HTTP 200, JSON с альбомами/песнями.
2. `curl -G "http://localhost:8897/api/public/zakroma?specialBucket=true"` →
   HTTP 200, JSON со спецзаказными авторами.
3. В логах karaoke-web **нет** `IllegalStateException`/`ExceptionInInitializerError` для
   `ConstantsKt`/`ConnectionKt` после рестарта.
4. `gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin` — успешно.
5. `gradlew :karaoke-app:ktlintCheck :karaoke-web:ktlintCheck` — успешно.

## Известные ограничения (отдельные задачи)

- В логах при чтении словаря «Censored» теперь видны предупреждения
  `lateinit property KSS_APP has not been initialized` — karaoke-web намеренно не
  инициализирует `KSS_APP` (MTU black-hole проблема, см. `KaraokeWebService.kt:51-62`).
  Словарь возвращается пустым, цензурирование на karaoke-web фактически не работает.
  Это **отдельная задача**, не блокирует функционирование Закромов — раньше всё падало,
  теперь работает без цензуры (временная деградация).