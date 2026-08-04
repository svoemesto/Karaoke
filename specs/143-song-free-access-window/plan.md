# Implementation Plan: Временное окно бесплатного доступа к песням

**Branch**: `143-song-free-access-window` | **Date**: 2026-08-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/143-song-free-access-window/spec.md`

## Summary

Заменяем текущую модель доступа («в эфире — бесплатно всем и навсегда, не в
эфире — только премиум») на модель с временным окном: готовая песня
бесплатна всем с момента эфира и **1 календарный месяц** после него, дальше —
только по подписке (сайтовой премиум или разовой на конкретную песню).
Отдельный флаг `free` (переиспользуется) переводит песню в «вечный эфир»
безусловно. Флаг `exclusive` полностью убирается из бизнес-логики, DTO и
админ-UI (везде, где сейчас ветвится по нему), но **DB-колонка `exclusive`
не удаляется** — см. Research Decision 2 (нет смысла трогать recordhash-
триггер обеих БД ради поля, которое просто перестаёт читаться/писаться).

Технически: новый расчёт «свободно доступна сейчас» вводится ИСКЛЮЧИТЕЛЬНО
в местах, отвечающих за платный доступ (`PublicPlayerController`,
публичные DTO, `StatBySong`, `ZakromaView`/`SearchView`/`SongView`) — и
**не подменяет** существующий `Song.isPubliclyWatchable`/`onAir`, которые
управляют не платным доступом, а разовым событием «песня вышла в эфир»
(триггер авто-новости `SongReleaseAnnouncementService`, specs/089). Это
разделение — ключевой инвариант плана (см. Research Decision 1).

## Technical Context

**Language/Version**: Kotlin (JDK 17, Spring Boot, `constitution.md`
«Технологический стек») для `karaoke-app`/`karaoke-web`; Vue 3 + Vite для
`webvue3` (admin) и `karaoke-public` (публичный сайт).

**Primary Dependencies**:
- `karaoke-app/model/Song.kt` — новые вычисляемые свойства
  (`freeAccessWindowEnd`, `isFreelyAvailableNow`, `freeAccessWindowEndText`);
  удаление `exclusive`-свойства, двух веток `SongState`, ветки `datePublish`
  getter'а
- `karaoke-web/controllers/PublicPlayerController.kt` — `canWatch`/`watchable`
  переключаются с `song.onAir` на `song.isFreelyAvailableNow`
- `karaoke-web/StatBySong.kt` — SQL-счётчики переписываются под новое
  правило, два JSON-ключа переименовываются (`onAir`→`freeNow`,
  `exclusive`→`subscriptionOnly`, см. Research Decision 4)
- `karaoke-web/dto/SongPublicDto.kt`, `dto/ZakromaPublicDto.kt` — новые поля
  вместо `exclusive`
- `webvue3`: `SongsTable.vue`, `filter/SongsFilterModal.vue`,
  `filter/store.js`, `edit/SongEdit.vue` — удаление UI/store для `exclusive`,
  переименование лейбла `free`-переключателя
- `karaoke-public`: `ZakromaView.vue`, `SearchView.vue`, `SongView.vue`,
  `AboutView.vue`, `HomeView.vue`, `store/modules/stats.js` — новая логика
  показа/скрытия текста об эфире, новый текст правил, переименованные ключи
  статистики

**Storage**: PostgreSQL (через сырой JDBC, `Connection.local()/remote()`).
**Миграция БД не требуется** — `exclusive`/`free` уже существующие колонки
`tbl_songs`/`tbl_songs_sync`; `exclusive` просто перестаёт читаться/писаться
из Kotlin (см. Research Decision 2). Recordhash-триггеры не пересоздаются.

**Testing**: Интеграционное, вручную (CI-тестов нет, `constitution.md`
«Тесты»). Сценарии — `quickstart.md`: подмена `publish_date`/`publish_time`
у тестовой песни на разные точки внутри/вне окна, проверка
`/api/public/player/{id}/access`, `/api/public/stats`, отображения в
Закромах и на странице песни для premium/non-premium/anon пользователей.

**Target Platform**: `karaoke-web` (прод-сервер, публичный API) + `karaoke-app`
(admin-машина, модель `Song`/admin API) + оба Vue SPA.

**Project Type**: backend-модули в существующем multi-module Gradle проекте
+ оба фронтенда. Новый per-feature документ `docs/features/song-free-access.md`
(Constitution Principle VI FR-009) — фича достаточно кросс-cutting
(player + zakroma + stats + about), чтобы заслужить собственный документ, а
не быть дописана в уже существующие `songs-table.md`/`stats.md` (те тоже
обновляются точечно, см. Project Structure).

**Performance Goals**: без изменений относительно текущего поведения —
`StatBySong` кеш обновляется раз в час, `PublicPlayerController.access()`/
`readiness()` остаются без живых обращений к MinIO (только вычисление по
уже загруженным полям `Song`).

**Constraints**:
- `Song.isPubliclyWatchable`/`onAir` НЕ меняются (используются
  `SongReleaseAnnouncementService`, specs/089 — другой, не платёжный,
  триггер)
- Длительность окна — фиксированная константа (1 календарный месяц),
  не привязана к БД/настройкам (FR-002 spec.md)
- Никаких новых миграций/DDL на проде (см. Research Decision 2) — снимает
  необходимость в отдельном согласовании прод-DDL с пользователем
  (Конституция, «Категорически запрещено» п.2, здесь неприменимо)

**Scale/Scope**: затрагивает весь каталог песен (~18k) в части подсчёта
статистики (один пересчитываемый раз в час SQL-запрос, не построчно) и
каждый показ Закромов/страницы песни (вычисление на лету по уже
загруженным полям, без доп. запросов к БД/MinIO).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Статус | Обоснование |
|-----------|--------|-------------|
| I. Self-contained автопайплайн | ✅ PASS | Фича не трогает ffmpeg/melt/Demucs/Sheetsage/внешние API в горячем пути обработки медиа — только доступ/отображение уже готового контента. |
| II. Сырой JDBC + дифф по хэшам | ✅ PASS | Все изменения полей — через существующий `Song.saveToDb()`/reflection-diff. `StatBySong` — уже существующий паттерн raw-SQL агрегатов (O(1) COUNT-запросы, не построчный обход). |
| III. Двух-БД синхронизация | ✅ PASS | Миграция не требуется (Decision 2) — колонки `exclusive`/`free` не меняются, recordhash-триггеры не трогаются. `free` продолжает синкаться как раньше. |
| IV. Async-очередь с парсингом stdout | ✅ PASS | Фича не создаёт новых `ProcessBuilder`-задач и не трогает существующие. |
| V. Двух-фронтенд | ✅ PASS | admin-изменения — только `webvue3` (убрать `exclusive` UI); публичные изменения — только `karaoke-public` (новая логика доступности + тексты). Ничего не пересекается. |
| VI. Code Standards (KDoc/JSDoc) | ✅ PASS | Новые/изменённые публичные свойства (`Song.isFreelyAvailableNow` и т.п.), функции DTO и Vue-компоненты — с KDoc/JSDoc и `@see docs/features/song-free-access.md` (новый per-feature документ создаётся в рамках этой фичи). |
| VII. Cross-Machine Setup | ✅ PASS | Изменения не затрагивают секреты/AI-конфиги/сборку/CI-инфраструктуру. |
| VIII. Секреты и git-гигиена | ✅ PASS | Токены/пароли не затрагиваются; изменения не касаются `.env`/секрет-файлов. |

**GATE RESULT**: ✅ PASS — все 8 принципов соблюдены. Нарушений нет.
Complexity Tracking содержит одну зафиксированную сознательную уступку
(легаси `SongState`), не являющуюся нарушением принципов.

## Project Structure

### Documentation (this feature)

```text
specs/143-song-free-access-window/
├── plan.md              # Этот файл
├── research.md          # Phase 0: 7 решений (isPubliclyWatchable/окно/миграция/SongState/счётчики/SongView/сфера "unpublish"-легаси)
├── data-model.md        # Phase 1: новые вычисляемые поля Song, DTO, счётчики, состояния UI
├── quickstart.md        # Phase 1: ручная сквозная проверка (окно, всегда-бесплатно, счётчики, закрома, страница песни)
├── contracts/
│   └── public-api.md    # Phase 1: изменённые публичные + admin-эндпоинты (JSON-контракты до/после)
└── tasks.md             # Phase 2 (/speckit.tasks — НЕ создаётся здесь)
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── model/
│   ├── Song.kt                 # ИЗМЕНЯЕТСЯ: + freeAccessWindowEnd/isFreelyAvailableNow/freeAccessWindowEndText;
│   │                            #   − var exclusive (геттер/сеттер), 2 ветки SongState, ветка datePublish,
│   │                            #   ветка getVKGroupDescriptionSponsr(), запись RecordDiff("exclusive",...),
│   │                            #   INSERT/UPDATE Pair-список и row-load (rs.getBoolean("exclusive"))
│   ├── SongField.kt             # ИЗМЕНЯЕТСЯ: убрать EXCLUSIVE из enum
│   ├── SongState.kt             # ИЗМЕНЯЕТСЯ: убрать EXCLUSIVE/EXCLUSIVE_FREE (см. research.md Decision 3)
│   ├── SongDTO.kt                # ИЗМЕНЯЕТСЯ: убрать поле exclusive (объявление + fromDto + toEntity)
│   ├── SongDTOdigest.kt          # ИЗМЕНЯЕТСЯ: убрать поля exclusive/flagExclusive
│   └── Zakroma.kt                # ИЗМЕНЯЕТСЯ: ZakromaAlbumSong — убрать exclusive, добавить
│                                  #   freelyAvailableNow/freeAccessWindowEndText/alwaysFree
└── controllers/
    └── ApiController.kt          # ИЗМЕНЯЕТСЯ: убрать query-параметр exclusive и его обработку (2969/3098),
                                   #   убрать flagExclusive из двух список-эндпоинтов (2426/2606)
                                   #   (raw-SQL filter_exclusive/flag_exclusive/"unpublish" В Song.kt — БЕЗ
                                   #   ИЗМЕНЕНИЙ, см. research.md Decision 7)

karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
├── controllers/
│   ├── PublicPlayerController.kt # ИЗМЕНЯЕТСЯ: access()/readiness() — song.onAir → song.isFreelyAvailableNow
│   └── PublicApiController.kt    # ИЗМЕНЯЕТСЯ: /api/public/stats — ключи onAir→freeNow, exclusive→subscriptionOnly
├── StatBySong.kt                 # ИЗМЕНЯЕТСЯ: SQL-условие onAir → freeNow (free=true OR окно), cachedExclusive
│                                  #   → cachedSubscriptionOnly (= collection − freeNow)
└── dto/
    ├── SongPublicDto.kt          # ИЗМЕНЯЕТСЯ: − exclusive, + alwaysFree/freelyAvailableNow/freeAccessWindowEndText
    └── ZakromaPublicDto.kt       # ИЗМЕНЯЕТСЯ: ZakromaAlbumSongPublicDto — то же самое

webvue3/src/components/Songs/
├── SongsTable.vue                 # ИЗМЕНЯЕТСЯ: убрать колонку flagExclusive (шаблон + fields[])
├── filter/SongsFilterModal.vue    # ИЗМЕНЯЕТСЯ: убрать поле фильтра Exclusive
├── filter/store.js                # ИЗМЕНЯЕТСЯ: убрать songsFilterFlagExclusive (state/getter/mutation/action)
└── edit/SongEdit.vue              # ИЗМЕНЯЕТСЯ: убрать переключатель «Эксклюзивно на sponsr» + setExclusive/
                                    #   exclusiveButtonClass; переименовать лейбл «Бесплатно на sponsr:» →
                                    #   «Всегда бесплатно (вечный эфир):»; убрать v-if="!song.exclusive" на
                                    #   links-tabs-widget (строка 671)

karaoke-public/src/
├── views/
│   ├── ZakromaView.vue            # ИЗМЕНЯЕТСЯ: showCoin/showDate — на новых полях DTO, showDate + !isPremium (FR-010)
│   ├── SearchView.vue             # ИЗМЕНЯЕТСЯ: то же самое (дублированная логика, см. feedback-память
│                                   #   про верификацию ВСЕХ независимых копий алгоритма)
│   ├── SongView.vue                # ИЗМЕНЯЕТСЯ: waitingTitle/waitingBody — s.exclusive → s.onAir (FR-015,
│                                    #   переиспользует старый текст); шаблон — различить "не готово" (VK-видео
│                                    #   фоллбек) от "готово, но окно истекло" (карточка ожидания); новый
│                                    #   computed playerReady
│   ├── AboutView.vue                # ИЗМЕНЯЕТСЯ: новый абзац с правилом бесплатного доступа (FR-012);
│                                     #   stats.onAir/exclusive → stats.freeNow/subscriptionOnly
│   └── HomeView.vue                 # ИЗМЕНЯЕТСЯ: mapGetters('stats', [...]) — onAir/exclusive → freeNow/subscriptionOnly
└── store/modules/stats.js           # ИЗМЕНЯЕТСЯ: state/getters/mutations — onAir→freeNow, exclusive→subscriptionOnly

docs/features/
├── song-free-access.md            # НОВЫЙ: per-feature документ (Constitution Principle VI FR-009)
└── README.md                       # ИЗМЕНЯЕТСЯ: +1 строка в таблице (20-я подсистема)
```

**Structure Decision**: Изменения — точечные правки в уже существующей
структуре всех 4 модулей, без новых сервисов/модулей. Компонент, ближе
всего к «новому», — вычисляемые свойства в `Song.kt` (аналог существующих
`onAir`/`isContentReady`/`isPubliclyWatchable`, тот же файл, тот же стиль).
Единственный полностью новый файл — per-feature документ
`docs/features/song-free-access.md`.

## Complexity Tracking

> Не нарушение принципов, но сознательное сужение скоупа — фиксируется для
> прозрачности ревью.

| Решение | Почему так | Более полная альтернатива отвергнута, потому что |
|---------|------------|----------------------------------------------------|
| Легаси `SongState` (`Song.kt:5950+`, `SongState.kt`) не переосмысливается — убираются только 2 ветки `EXCLUSIVE`/`EXCLUSIVE_FREE`, песни проваливаются в следующую по порядку ветку (обычно `IN_WORK`) | `SongState` — это ~150-строчная legacy-система admin-раскраски для **другой**, давно частично мёртвой задачи (полнота публикации на Sponsr/Dzen/VK/PL/Telegram), не имеющая отношения к платному доступу. Спека `140` не описывает и не требует новой admin-раскраски. | Полный редизайн `SongState` под новую модель доступа — отдельная, гораздо большая задача (нужно решить, что означает admin-цвет в мире без "эксклюзивности", для всех ~15 состояний), не запрошенная пользователем и не нужная для реализации ни одного FR/US этой спеки. |

## Post-Design Constitution Re-Check

Все решения Phase 0/1 (research.md, data-model.md) не вводят новых внешних
зависимостей, миграций или изменений в двух-фронтенд/двух-БД границах.
Повторная проверка после дизайна: ✅ PASS, без изменений относительно
первичной проверки выше.
