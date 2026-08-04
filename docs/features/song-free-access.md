# Временное окно бесплатного доступа к песням

> **Status**: active
> **Feature Key**: song-free-access
> **Last Updated**: 2026-08-04 (specs/143-song-free-access-window)

## Что делает

Определяет, доступна ли песня бесплатно (без премиум-подписки и без личной
подписки на конкретную песню) ПРЯМО СЕЙЧАС: либо песня помечена «всегда
бесплатно» (`Song.free`), либо с момента её эфира прошло не более 1
календарного месяца (`Song.isFreelyAvailableNow`). Заменяет прежнее правило
«в эфире = бесплатно навсегда» и полностью убирает из системы флаг
`exclusive` («эксклюзивно на Sponsr») как элемент бизнес-логики.

## Зачем

Проект больше не публикует песни на сторонних площадках (Sponsr и др.) —
только онлайн-плеер на сайте + демо в Telegram/VK. Модель «бесплатно
навсегда после эфира» больше не соответствует бизнес-модели подписки;
модель «эксклюзивно только на Sponsr» физически устарела (Sponsr не
используется). Новое правило — понятное пользователю и стимулирующее
подписку окно (месяц бесплатного доступа после премьеры).

## Как работает (кратко)

- **Источник истины** — три вычисляемых свойства в `Song.kt` (рядом с
  `onAir`): `freeAccessWindowEnd` (момент эфира + 1 календарный месяц,
  `Calendar.add(MONTH, 1)`), `isFreelyAvailableNow` (`free ||
  (onAir && now < freeAccessWindowEnd)`), `freeAccessWindowEndText`
  (отформатированная дата конца окна для UI).
- **Платный гейт** — `PublicPlayerController.access()`/`readiness()`
  используют `song.isFreelyAvailableNow` вместо `song.onAir` в формуле
  `canWatch`/`watchable`.
- **Счётчики** — `StatBySong.kt` считает `freeNow` (SQL: `free=true OR
  окно ещё не истекло`) и `subscriptionOnly` (`collection − freeNow`)
  вместо бывших `onAir`/`exclusive`.
- **UI** — Закрома/Поиск (`ZakromaView.vue`/`SearchView.vue`) показывают
  непремиум-пользователю без личной подписки «Будет в эфире с …» (ещё не
  вышла) или «В эфире до …» (в окне); ничего — для всегда-бесплатных,
  купленных песен и для премиум-пользователей. Страница песни
  (`SongView.vue`) показывает карточку ожидания с текстом «Эта песня
  доступна только по подписке» для аналогичного состояния «в эфире, окно
  истекло».
- **DB-миграция не требуется** — колонка `exclusive` осталась в схеме
  (`tbl_songs`/`tbl_songs_sync`), но больше не читается и не пишется
  Kotlin-кодом.

## Инварианты / правила

- **MUST**: `Song.onAir`/`Song.isPubliclyWatchable` НЕ ДОЛЖНЫ
  переиспользоваться/переопределяться под правило бесплатного доступа —
  они управляют одноразовым триггером авто-новости «песня вышла в эфир»
  (`SongReleaseAnnouncementService`, [`telegram-auto-publish.md`](./telegram-auto-publish.md),
  specs/089-auto-news-song-release). Платный доступ ВСЕГДА идёт через
  `isFreelyAvailableNow`, отдельное свойство.
- **MUST**: Длительность окна — фиксированная системная константа
  (`Song.FREE_ACCESS_WINDOW_MONTHS = 1`), не настраивается на уровне
  песни ([constitution.md](../../.specify/memory/constitution.md), spec.md
  FR-002).
- **SHOULD**: Раз DB-колонка `exclusive` не удаляется (см. ловушку ниже),
  новый код НЕ ДОЛЖЕН читать/писать её напрямую — единственные legacy
  raw-SQL исключения перечислены в «Известных ловушках».

## Известные ловушки

- **Колонка `exclusive` физически всё ещё существует в БД**, но полностью
  выведена из Kotlin-модели (`Song.kt`) — три raw-SQL места в
  `Song.kt` (query-билдер: `flag_exclusive`, `filter_exclusive`,
  `unpublish`-фильтр) ссылаются на неё напрямую по имени колонки и
  намеренно НЕ тронуты (см. `specs/143-song-free-access-window/research.md`
  Decision 7) — они не читают Kotlin-свойство, поэтому продолжают
  компилироваться, но UI-контролов, которые могли бы их вызвать, больше
  нет (мёртвый, но безопасный код).
- **Thymeleaf-шаблон `main.html`** (`karaoke-web`, legacy landing на `/`)
  использует переменные `${onAir}`/`${exclusive}` — это исторические
  ИМЕНА переменных шаблона, не связанные с новыми Kotlin-именами
  (`freeNow`/`subscriptionOnly`); `MainController.kt` явно прокидывает
  новые данные под старыми именами атрибутов, шаблон трогать не пришлось.
- **`SongView.vue`**: до этой фичи состояние «в эфире, контент готов, но
  недоступен» было физически недостижимо (при `onAir=true` и `ready=true`
  раньше `canWatch` было гарантированно `true`), поэтому шаблон не
  различал «не готово» (легаси VK-видео-блок) от «не положено» (карточка
  ожидания). Смотреть `playerReady` computed — без него легко случайно
  показать не тот блок для нового состояния «окно истекло».
- **`showDate`/`showCoin` продублированы** в `ZakromaView.vue` и
  `SearchView.vue` (независимые копии одного и того же алгоритма) — при
  следующих изменениях этой логики проверять ОБА файла.

## Ссылки на ключевые классы/файлы

- [`karaoke-app/.../model/Song.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt) — `freeAccessWindowEnd`/`isFreelyAvailableNow`/`freeAccessWindowEndText` (рядом с `onAir`)
- [`karaoke-web/.../controllers/PublicPlayerController.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlayerController.kt) — `access()`/`readiness()`, платный гейт
- [`karaoke-web/.../StatBySong.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/StatBySong.kt) — счётчики `freeNow`/`subscriptionOnly`
- [`karaoke-public/src/views/ZakromaView.vue`](../../karaoke-public/src/views/ZakromaView.vue) — `showCoin`/`showDate`/`dateLabel`
- [`karaoke-public/src/views/SearchView.vue`](../../karaoke-public/src/views/SearchView.vue) — та же логика, независимая копия
- [`karaoke-public/src/views/SongView.vue`](../../karaoke-public/src/views/SongView.vue) — `playerReady`/`waitingTitle`/`waitingBody`
- [`specs/143-song-free-access-window/spec.md`](../../specs/143-song-free-access-window/spec.md) — исходная спецификация и все FR
- [`specs/089-auto-news-song-release`](../../specs/089-auto-news-song-release/spec.md) — независимый триггер, использующий `onAir`/`isPubliclyWatchable`
