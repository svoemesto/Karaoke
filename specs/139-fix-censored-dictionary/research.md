# Phase 0 Research: Исправление цензурирования {songNameCensored} на продакшене

## R1 — Корневая причина: почему {songNameCensored} не цензурируется в реальных публикациях

**Decision**: Корневая причина — `String.censored()` (и вся цепочка
`CensoredWordsDictionary`/`TextFileDictionary.dict`) не принимает параметр `database`. Она всегда
читает словарь через модуль-глобал `com.svoemesto.karaokeapp.WORKING_DATABASE`
(`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Constants.kt:204`,
`val WORKING_DATABASE = Connection.local()`), даже когда вызывающий код уже получил и явно передал
дальше правильное соединение.

Трассировка вызовов (codegraph + прямое чтение исходников):

1. Обе категории auto-новости («доступна»/`premium` и «в эфире»/`air`) создаются **исключительно**
   из `karaoke-web` (см. KDoc `SongReleaseAnnouncementService.kt:14-24`):
   - `detectAndAnnounceAvailability` — вызывается только из
     `karaoke-web/.../controllers/MainController.doChangeRecords`.
   - `checkOnAirWindow` — вызывается только из
     `karaoke-web/.../services/SongReleaseAnnouncementScheduler` (`@Scheduled(fixedDelay = 5 * 60_000L)`,
     единственная точка кода, создающая air-новость).
2. `SongReleaseAnnouncementScheduler.checkOnAir()` корректно передаёт **свой, правильный**
   `com.svoemesto.karaokeweb.WORKING_DATABASE` в `SongReleaseAnnouncementService.checkOnAirWindow(WORKING_DATABASE, ...)`.
3. Внутри `checkOnAirWindow`/`detectAndAnnounceAvailability` этот `database`-параметр **корректно**
   прокидывается в `NewsTemplateService.template(key, database)` (текст шаблона из
   `tbl_public_settings` читается с правильным соединением) — но затем передаётся в
   `NewsTemplateService.render(template, song, news)`, у которой **нет параметра `database`**.
4. `render()` → `buildReplacements(song, news)` → `"songNameCensored" to song.songName.censored()` —
   `censored()` не получает и не может получить `database`: она инстанцирует
   `CensoredWordsDictionary()` без параметров и читает `.dict`, который по умолчанию использует
   `com.svoemesto.karaokeapp.WORKING_DATABASE` (`TextFileDictionary.kt`, `Dictionary.loadValues(dictName(), WORKING_DATABASE)`).
5. Документированная ловушка (`docs/invariants.md`, «Ловушки karaoke-web», раздел «коллизия
   имён»): на проде (внутри JVM `karaoke-web`) обращение к `com.svoemesto.karaokeapp.WORKING_DATABASE`
   **не бросает исключение** — оно резолвится через `Connection.local()` **из пакета karaoke-app**,
   чьи env-флаги (`APP_WORK_ON_SERVER`/`APP_WORK_IN_CONTAINER`) на проде никогда не выставлены →
   получается JDBC URL вида `jdbc:postgresql://localhost:8832/karaoke...`, обращённый к несуществующему
   (или чужому) адресу изнутри контейнера `karaoke-web`. Любая ошибка подключения ловится в
   `TextFileDictionary.dict` через `catch (e: Throwable) { emptyList() }` **без единого лога** →
   словарь молча пуст → `censored()` — no-op → ровно симптом «как будто там нет нужного словаря».

**Почему это не задевает Telegram/ВК-публикацию (независимая проверка)**: `VkAutoPublishService`/
`TelegramAutoPublishService` рендерят пост через `VkTemplateService.renderWithFlags`/аналог
**самостоятельно** (не переиспользуют `News.body`), и весь этот код выполняется только внутри
`karaoke-app` (по конституции проекта — `karaoke-app` разворачивается только на admin-машине, никогда
на проде). Там `com.svoemesto.karaokeapp.WORKING_DATABASE` — «свой», корректный, и указывает на
работающую admin-БД, где нужное слово подтверждённо есть. Поэтому реальный, воспроизводимый разрыв —
именно в «новости на сайте» (`tbl_news.title`/`body`), которую пользователь тоже называл в числе
мест, где видел баг.

**Rationale**: находка объясняет одновременно все наблюдаемые факты — (а) слово есть в БД и на LOCAL,
и на SERVER (не в этом дело), (б) баг воспроизводится именно «на серверной части» (это
специфично для JVM `karaoke-web`, единственного места выполнения этого кода на проде), (в) выглядит
«как будто словаря нет» (пустой список без единого следа в логах — `catch (Throwable) { emptyList() }`
без `println`/лога).

**Alternatives considered**:
- *Гипотеза: не синхронизирован словарь LOCAL→SERVER* — отклонена, пользователь подтвердил наличие
  слова на обеих БД (clarify-сессия, Q1 в исходной специфике).
- *Гипотеза: словарь по-прежнему частично читается из старых текстовых файлов* — отклонена
  чтением исходников: `TextFileDictionary`/`Dictionary` полностью на `tbl_dictionaries`, текстовые
  файлы участвуют только в одноразовом ручном импорте (clarify-сессия, Q2).
- *Гипотеза: `\b`-границы слов не матчат кириллицу в regex `censored()`* — отклонена эмпирической
  проверкой через `jshell` (Java `Pattern`, без флагов) — кириллические словарные слова матчатся
  корректно по умолчанию.
- *Гипотеза: значения словаря без `[...]`-разметки* — не подтверждена на текущих LOCAL-данных (все
  149+ записей уже с разметкой), но остаётся отдельным UX-риском на будущее → см. FR-004/User Story 3.

## R2 — Как исправить, не нарушая ограничение «karaoke-app на проде не разворачивается»

**Decision**: Прокинуть `database: KaraokeConnection` явным параметром через всю цепочку рендера
censored-текста: `String.censored(database: KaraokeConnection = WORKING_DATABASE)` →
`CensoredWordsDictionary(database: KaraokeConnection = WORKING_DATABASE)` → уже существующий
параметризуемый `TextFileDictionary.dict`/`Dictionary.loadValues(dictName, database)`. Дефолт
сохраняет **текущее** поведение (обратная совместимость) для всех мест в `karaoke-app`, которые уже
неявно полагаются на глобал (VK/Telegram publish на admin-машине) — они и дальше получают корректный
результат без изменений вызывающего кода. `NewsTemplateService.render`/`buildReplacements` получают
новый параметр `database: KaraokeConnection` и передают его в `.censored(database)`;
`SongReleaseAnnouncementService` (уже имеющий `database` в сигнатуре) передаёт его на один уровень
глубже, в `render(...)`.

**Rationale**: Это единственное решение, которое не требует разворачивать `karaoke-app` на
прод-сервере (запрещено конституцией) и не создаёт новую отдельную копию логики словаря для
`karaoke-web` (дублирование кода = второй источник рассинхрона в будущем). Изменение — чисто
сигнатурное, обратно совместимое по умолчанию.

**Alternatives considered**:
- *Завести отдельный `com.svoemesto.karaokeweb.CensoredWordsDictionary`, дублирующий логику* —
  отклонено: дублирование уже было причиной путаницы (см. отдельные `Connection`/`WORKING_DATABASE`
  в обоих пакетах), plus два места для будущих правок словарной логики.
  Кроме того, `NewsTemplateService`/`SongReleaseAnnouncementService` живут в `karaoke-app` и уже
  сейчас *умеют* принимать чужой `database` — не хватает только последнего шага (censored()).
- *Развернуть `karaoke-app` дополнительно на проде, чтобы у него был «свой» верный WORKING_DATABASE* —
  прямое нарушение зафиксированного в конституции ограничения — отклонено.
- *Просто исправить резолюцию `com.svoemesto.karaokeapp.WORKING_DATABASE` под `karaoke-web`* — не
  решает системно: это тот же класс проблемы для *любого* будущего karaoke-app-кода, переиспользуемого
  в karaoke-web (см. `docs/invariants.md`, «ловушка шире, чем только `rootFolder`») — параметризация
  явным `database` — рекомендуемый в документе паттерн (по аналогии с
  `KaraokeStorageService`/`StorageApiClient`, которые уже конструкторные Spring-бины, а не глобалы).

## R3 — Наблюдаемость сбоя чтения словаря (FR-002)

**Decision**: В `catch (e: Throwable)` блоке `TextFileDictionary.dict` добавить лог уровня ошибки
(`println`/существующий логгер проекта — по образцу остальных `catch`-блоков в `SongReleaseAnnouncementScheduler`:
`println("[...] error: ${e.message}")`), явно отличимый по тексту от «словарь пуст». Отдельный
monitor-check (по образцу `karaoke-app/.../monitor/checks/*Check.kt`, интерфейс `MonitorCheck`) —
избыточен для этой фичи (нет постоянного recurring-состояния, которое нужно отслеживать между
тиками) — простого лога достаточно, т.к. подключение к БД либо есть, либо разово падает и видно в
`docker logs`.

**Rationale**: Существующий `catch (Throwable) { emptyList() }` — единственное место, которое сейчас
*гарантированно* видит любую ошибку чтения словаря, но её съедает. Минимальное изменение с
максимальным эффектом наблюдаемости.

**Alternatives considered**: полноценный `MonitorCheck` (SSE-алерт в UI) — отложено как избыточное
для точечного бага без recurring-состояния; можно добавить позже, если проблема повторится в другой
форме.

## R4 — Admin-инструмент проверки словаря (FR-003, User Story 2)

**Decision**: Новый лёгкий REST-эндпоинт (например `POST /api/dictionaries/test`) в `karaoke-app`,
принимающий `dictName` + произвольный `text`, возвращающий результат применения **той же** функции
`String.censored(dictName, database)`, что и реальный рендер — без похода к реальной песне (в отличие
от уже существующего `/api/news/templates/preview`, который специфичен для новостных шаблонов и
требует `id` реальной песни). Вызывается из `webvue3` `DictionariesTable.vue` — поле «проверить
строку» рядом с таблицей словаря.

**Rationale**: `/api/news/templates/preview` уже частично закрывает User Story 2 для новостных
шаблонов, но: (а) требует существующий `id` песни, (б) не работает для словарей «Слова с Ё»/«Sync
Ids», (в) живёт в контексте `NewsTemplateController`, что концептуально не про «словари» для
администратора. Отдельный лёгкий эндпоинт в контексте словарей проще для FR-003/SC-003 (одно
действие в админке).

**Alternatives considered**: переиспользовать `/api/news/templates/preview` — отклонено по причинам
выше; тестировать словарь только через реальную публикацию — отклонено, слишком долгий цикл
обратной связи, что и было изначальной жалобой пользователя (FR-003 явно просит независимый способ).

## R5 — Подсказка формата значений словаря (FR-004, User Story 3)

**Decision**: В `DictionariesTable.vue` (поле ввода нового значения, `placeholder="Значение"`,
строка 16) — при выбранном словаре `dictName === 'Censored'` заменить/дополнить placeholder на
пример формата (`сл[о]во — [x] помечает маскируемую часть`) и добавить нестрогую клиентскую
проверку (предупреждение, не блокировка) при сохранении значения без `[`/`]`.

**Rationale**: Наименьшее изменение, не блокирующее легитимные случаи (админ может специально
захотеть слово без маскировки — например, короткий синоним без замены). Небольшая подсказка
устраняет источник будущих «похожих» инцидентов (User Story 3, P3).

**Alternatives considered**: жёсткая серверная валидация формата (отклонение значений без `[...]`)
— отклонено как слишком инвазивное для существующего вольного формата данных без явного запроса
пользователя на такое ограничение.
