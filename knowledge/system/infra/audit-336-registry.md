# Реестр аудита #336 — BR-коды и карточки пакетов

> **Домен**: `knowledge/system/infra/`
> **Назначение**: единственный носитель BR-кодов аудита #336 после слияния слоя
> `docs/` в `knowledge/` (ADR-0011). Реестр — **данные**, а не решение: отдельный
> ADR для него не нужен (ADR фиксируют решения).

## 1. Назначение и границы

Аудит #336 — крупнейшая ревизия проекта: волны A–E, десятки пакетов исправлений,
сотни дефектов, каждый со своим идентификатором `BR-NNN`. До слияния эти
идентификаторы жили в 30 файлах `docs/features/pkg-*.md` и в ряде продуктовых
и мост-файлов; после слияния слой `docs/` в корне репозитория отсутствует, а
коды должны остаться **адресуемыми**.

**Как читать реестр.** Реестр отвечает на вопрос «дефект BR-NNN — что это и где
смотреть исправление»:

- **Таблица кодов** (§ 3) — по идентификатору: одна строка о сути дефекта и
  ссылка на компонент-зеркало в `knowledge/`.
- **Карточки пакетов** (§ 4) — по пакету: волна, состав, что было исправлено,
  куда легло зеркало.
- **Коды без зеркала** (§ 5) — дефекты, чьё содержание не перенесено в
  компоненты: их правка остаётся открытой работой.

**Границы.** Реестр не дублирует полные описания дефектов (до-после, тесты,
разбор) — они остаются в git-истории перенесённых файлов. Для кодов, у которых
**нет** зеркала в `knowledge/`, строка реестра самодостаточна: называет, что
чинить.

**Проверка соответствия** — guard FR-015: каждый `BR-NNN`, упомянутый в
`knowledge/**`, присутствует в реестре; каждый код реестра либо встречается в
`knowledge/**`, либо помечен в § 5 как «нет зеркала». Скрипт проверки и его
фикстуры добавляются вместе с подключением гейта (фаза US4).

## 2. Сводка

| Величина | Значение |
|---|---|
| Уникальных BR-кодов в слое `docs/features/` | **510** (в 43 файлах из 65) |
| Из них в 18 файлах `pkg-*.md` | 385 |
| Из них только в остальных 47 файлах (8 продуктовых документов + 5 мост-файлов + прочие) | **125** |
| Кодов, встречающихся только в `knowledge/` (не в слое `docs/features/`) — § 6 | **41** |
| Зарегистрировано в реестре всего | **553** |
| Кодов с зеркалом в `knowledge/` | **250** |
| Кодов без зеркала (список в § 5) | **262** |
| Пакетов аудита #336 (карточки в § 4) | **37** |

Волны аудита: S2 (инфраструктура и данные), S3 (функциональность), ERR (единообразные
ошибки). Карточка пакета в § 4 указывает волну, состав по кодам и зеркало в `knowledge/`;
полные описания дефектов остались в git-истории перенесённых файлов.

## 3. Таблица кодов

Колонки: **код** · **суть дефекта** (одна строка) · **зеркало** (файл в `knowledge/`,
где описано исправление; `—` = зеркала нет, код в списке § 5) · **пакет**.


### PKG-S2

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-001 | Аудит #336 (порция P-37). Оба endpoint'а (/api/public/utils/vkIdOAuthUrl | [domains/karaoke-web/components/public-controllers-2.md](../../domains/karaoke-web/components/public-controllers-2.md) | PKG-S2 |
| BR-002 | Аудит #336 (порция P-37). Оба endpoint'а (/api/public/utils/vkIdOAuthUrl | [domains/karaoke-web/components/public-controllers-2.md](../../domains/karaoke-web/components/public-controllers-2.md) | PKG-S2 |
| BR-004 | 2026-10-24 (OpenProject #378): единое правило видимости для | [product/features/public-api-exposure.md](../../product/features/public-api-exposure.md) | PKG-S2 |
| BR-005 | 2026-10-24 (OpenProject #378): транзакция батча sync | [domains/karaoke-web/components/services-overview.md](../../domains/karaoke-web/components/services-overview.md) | PKG-S2 |
| BR-012 | 6. Транзакционность применения действий не менялась (отдельная задача ) | [domains/karaoke-web/components/main-controller.md](../../domains/karaoke-web/components/main-controller.md) | PKG-S2 |
| BR-013 | 2026-10-24 (OpenProject #378): единое правило видимости для | [product/features/public-api-exposure.md](../../product/features/public-api-exposure.md) | PKG-S2 |
| BR-015 | 2026-10-24 (OpenProject #378): единое правило видимости для | [product/features/public-api-exposure.md](../../product/features/public-api-exposure.md) | PKG-S2 |
| BR-023 | 2026-10-24 (OpenProject #378): транзакция батча sync | [domains/karaoke-web/components/public-controllers-6.md](../../domains/karaoke-web/components/public-controllers-6.md) | PKG-S2 |
| BR-500 | 2026-10-24 (OpenProject #378): удалена колонка cntBoosty, ссылка новости | [domains/karaoke-web/components/thymeleaf-templates.md](../../domains/karaoke-web/components/thymeleaf-templates.md) | PKG-S2 |
| BR-504 | 2026-10-24 (OpenProject #378): удалена колонка cntBoosty, ссылка новости | [domains/karaoke-web/components/main-controller.md](../../domains/karaoke-web/components/main-controller.md) | PKG-S2 |
| BR-515 | 2026-10-24 (OpenProject #378): транзакция батча sync | [domains/karaoke-web/components/services-overview.md](../../domains/karaoke-web/components/services-overview.md) | PKG-S2 |
| BR-516 | 2026-10-24 (OpenProject #378): транзакция батча sync | [domains/karaoke-web/components/song-share-link-service.md](../../domains/karaoke-web/components/song-share-link-service.md) | PKG-S2 |

### OpenProject #384

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-003 | Source: OpenProject #384 — «S2: Доверие к заголовку X-Forwarded-For» (аудит кода #336, «xff-first-element», порции P-37 / P-38) | [product/features/client-ip-resolution.md](../../product/features/client-ip-resolution.md) | OpenProject #384 |
| BR-011 | Source: OpenProject #357 — «S1: POST /changerecords — публичный endpoint, который исполняет присланный клиентом SQL » | [product/features/client-ip-resolution.md](../../product/features/client-ip-resolution.md) | OpenProject #384 |

### PKG-S3-29

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-007 | Как работает — Отказы контроллеров : свои | — | PKG-S3-29 |
| BR-008 | Как работает — Логи и конфигурация : println в | — | PKG-S3-29 |
| BR-009 | Как работает — Логи и конфигурация : println в | — | PKG-S3-29 |
| BR-010 | Данные : INSERT событий параметризован; лимит | — | PKG-S3-29 |
| BR-017 | Метрики : pgMaxConnections — реальный SHOW maxconnections | — | PKG-S3-29 |
| BR-024 | Как работает — Отказы контроллеров : свои | — | PKG-S3-29 |
| BR-025 | Как работает — Отказы контроллеров : свои | — | PKG-S3-29 |
| BR-026 | Как работает — Отказы контроллеров : свои | — | PKG-S3-29 |
| BR-501 | Как работает — Отказы контроллеров : свои | — | PKG-S3-29 |
| BR-502 | Как работает — Шаблоны : мёртвый блок удалён, параметры | — | PKG-S3-29 |
| BR-503 | Как работает — Шаблоны : мёртвый блок удалён, параметры | — | PKG-S3-29 |
| BR-505 | Как работает — Шаблоны : мёртвый блок удалён, параметры | — | PKG-S3-29 |
| BR-506 | Как работает — Шаблоны : мёртвый блок удалён, параметры | — | PKG-S3-29 |
| BR-519 | Данные : INSERT событий параметризован; лимит | — | PKG-S3-29 |
| BR-520 | Транзакции : отзыв старой ссылки и вставка новой — одна | — | PKG-S3-29 |
| BR-521 | Как работает — Логи и конфигурация : println в | — | PKG-S3-29 |
| BR-522 | Как работает — Логи и конфигурация : println в | [system/frontend/karaoke-web-error-contract.md](../../system/frontend/karaoke-web-error-contract.md) | PKG-S3-29 |

### PKG-MONEY

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-014 | checkout удалял корзину до создания платежа: при отказе ЮKassa корзина | — | PKG-MONEY |
| BR-020 | статус PAID сохранялся до начисления и вне транзакции: ретрай вебхука | — | PKG-MONEY |
| BR-021 | повторное оформление переиспользовало заказ только при ровно pending | — | PKG-MONEY |
| BR-514 | автопродление отбрасывало result.id (вебхук не мог найти подписку) | [domains/karaoke-web/components/services-overview.md](../../domains/karaoke-web/components/services-overview.md) | PKG-MONEY |

### PKG-ERR-STATS-ZERO

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-016 | StatBySong (karaoke-web): runCountQuery возвращал 0 при | [domains/publishing/components/stats-cache.md](../../domains/publishing/components/stats-cache.md) | PKG-ERR-STATS-ZERO |
| BR-185 | MonetizationStats (karaoke-app): scalarInt/scalarDouble | [domains/stats/domain.md](../../domains/stats/domain.md) | PKG-ERR-STATS-ZERO |

### OpenProject #358

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-018 | specs/557-close-gesture-token-paywall/spec.md — контракт (OpenProject #358, аудит ) | [product/features/close-gesture-token-paywall.md](../../product/features/close-gesture-token-paywall.md) | OpenProject #358 |

### OpenProject #359

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-019 | Source: OpenProject #359 — «S1: /api/properties/ доступен анонимно — публичная запись в tblpublicsettings и утечка серверного ключа капчи » и #362 — «S1: проверка капчи fail-… | [product/features/public-settings-perimeter.md](../../product/features/public-settings-perimeter.md) | OpenProject #359 |
| BR-518 | Source: OpenProject #359 — «S1: /api/properties/ доступен анонимно — публичная запись в tblpublicsettings и утечка серверного ключа капчи » и #362 — «S1: проверка капчи fail-… | [product/features/public-settings-perimeter.md](../../product/features/public-settings-perimeter.md) | OpenProject #359 |

### —

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-022 | 2026-10-24 (OpenProject #378, пакет PKG-S2-15): тег SKIP добавлен высшим | [product/features/song-air-access.md](../../product/features/song-air-access.md) | — |
| BR-533 | 52tblsongssyncdescriptionfields.sql — недостающие колонки tblsongssync + recordhash | [product/features/song-air-access.md](../../product/features/song-air-access.md) | — |
| BR-541 | Канонический deploy/recordhashauthors.sql тоже обязан включать sortorder: до аудита #336 (OpenProject #363) он отставал от схемы на семь колонок, поэтому применение «канониче… | [domains/catalog/components/dictionaries.md](../../domains/catalog/components/dictionaries.md) | — |
| BR-552 | MUST: секреты трекера не передаются через argv — API-токен уходит в curl через stdin (curl --config -, header = "Authorization: …"), пароль БД — через stdin в одноразовый .pg… | [system/infra/tracker.md](../../system/infra/tracker.md) | — |
| BR-553 | Забыли пересобрать recordhash-триггер tblsongssync: запись в sync-таблицу упадёт с column freeafteronair does not exist, либо её recordhash не будет включать значение → LOCAL… | [product/features/song-air-access.md](../../product/features/song-air-access.md) | — |
| BR-601 | Polling-сводка задач (--human / --json / --quiet). Режим --json разбирает вывод list-issues --raw (колонки разделены табом) → id / subject / status заполнены корректно. stder… | [system/infra/tracker.md](../../system/infra/tracker.md) | — |
| BR-605 | Общие функции: HTTP wrapper с retry на 429, JSON-логирование, Basic Auth. Библиотека подключается через source и не меняет настройки вызывающего шелла (режим set задаёт сам в… | [system/infra/tracker.md](../../system/infra/tracker.md) | — |

### PKG-SEC-SQLI

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-028 | восемь семейств (ids, albumid, publishdate/time, status, songbpm/tone/year/track, songtype, flag, filter) — сырые | [domains/persistence/domain.md](../../domains/persistence/domain.md) | PKG-SEC-SQLI |
| BR-100 | восемь семейств (ids, albumid, publishdate/time, status, songbpm/tone/year/track, songtype, flag, filter) — сырые | [domains/persistence/domain.md](../../domains/persistence/domain.md) | PKG-SEC-SQLI |
| BR-175 | восемь семейств (ids, albumid, publishdate/time, status, songbpm/tone/year/track, songtype, flag, filter) — сырые | [domains/catalog/components/pictures.md](../../domains/catalog/components/pictures.md) | PKG-SEC-SQLI |
| BR-237 | id, authorid, year, albumtype — без кавычек; name/namesearch — ручное '' | [domains/catalog/components/album-entity.md](../../domains/catalog/components/album-entity.md) | PKG-SEC-SQLI |

### PKG-ERR-EXITCODE

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-029 | # Код возврата внешнего процесса проверяется (аудит #336, ) | [domains/processing/components/async-process-queue.md](../../domains/processing/components/async-process-queue.md) | PKG-ERR-EXITCODE |
| BR-161 | # Код возврата внешнего процесса проверяется (аудит #336, ) | [domains/processing/components/playwright-rendering.md](../../domains/processing/components/playwright-rendering.md) | PKG-ERR-EXITCODE |

### PKG-S2-10

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-030 | заданий , неатомарную цепочку шагов , падение | [domains/processing/components/async-process-queue.md](../../domains/processing/components/async-process-queue.md) | PKG-S2-10 |
| BR-031 | заданий , неатомарную цепочку шагов , падение | [domains/processing/components/async-process-queue.md](../../domains/processing/components/async-process-queue.md) | PKG-S2-10 |
| BR-032 | заданий , неатомарную цепочку шагов , падение | [domains/processing/components/async-process-queue.md](../../domains/processing/components/async-process-queue.md) | PKG-S2-10 |
| BR-033 | воркера на пустых processargs , инъекцию полей в | [domains/processing/components/async-process-queue.md](../../domains/processing/components/async-process-queue.md) | PKG-S2-10 |
| BR-048 | copyfieldsfromanother , SSRF через прокси обложек | — | PKG-S2-10 |
| BR-049 | copyfieldsfromanother , SSRF через прокси обложек | — | PKG-S2-10 |
| BR-050 | подмену rootFolder через POST /api/song/update , перезапись | — | PKG-S2-10 |
| BR-063 | чужих изменений после сетевого вызова публикации , sleep на | — | PKG-S2-10 |
| BR-064 | каждой итерации массового поиска текста , NPE на неизвестном | — | PKG-S2-10 |
| BR-071 | аккорде , «успех» при неудавшемся сохранении песни ( | — | PKG-S2-10 |
| BR-101 | аккорде , «успех» при неудавшемся сохранении песни ( | [domains/catalog/components/dictionaries.md](../../domains/catalog/components/dictionaries.md) | PKG-S2-10 |
| BR-102 | ), N+1 по альбомам в toDTO , неполный список колонок | [domains/catalog/components/dictionaries.md](../../domains/catalog/components/dictionaries.md) | PKG-S2-10 |
| BR-104 | ), N+1 по альбомам в toDTO , неполный список колонок | [domains/catalog/components/dictionaries.md](../../domains/catalog/components/dictionaries.md) | PKG-S2-10 |
| BR-109 | Что делает — INSERT и path traversal в SmartCopy | — | PKG-S2-10 |

### PKG-S3-22

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-034 | KaraokeProcess.applyFilterLimit: take(limit) вместо | [domains/processing/components/async-process-queue.md](../../domains/processing/components/async-process-queue.md) | PKG-S3-22 |
| BR-035 | MediaInfo.runMediaInfo: чтение stdout отдельным демон-потоком | [domains/catalog/components/dictionaries.md](../../domains/catalog/components/dictionaries.md) | PKG-S3-22 |
| BR-036 | deleteDone/setWorkingToWaiting возвращают Boolean | — | PKG-S3-22 |
| BR-037 | loadList добавляет processdeletedat IS NULL по умолчанию | — | PKG-S3-22 |
| BR-051 | ApiController.sheetsageString/StringList/DoubleList: безопасное чтение | — | PKG-S3-22 |
| BR-052 | /songs/changed, /processes/changed: ERROR в лог + 5xx вместо | — | PKG-S3-22 |
| BR-053 | 055/059 — batch-эндпоинты: loadSongsBatch, distinct, «успех = есть | [domains/processing/components/async-process-queue.md](../../domains/processing/components/async-process-queue.md) | PKG-S3-22 |
| BR-054 | семь chords-эндпоинтов формируют текст версии SongVersion.CHORDS | — | PKG-S3-22 |
| BR-056 | TextFileHistory: ReentrantLock на путь, атомарная запись, копия | [system/utilities.md](../../system/utilities.md) | PKG-S3-22 |
| BR-057 | /publications/date: Song.loadPublicationDateCandidates (4 колонки) | [domains/catalog/components/song-entity.md](../../domains/catalog/components/song-entity.md) | PKG-S3-22 |
| BR-058 | notifyStatsDirty: проверка 200..299, WARN с хостом и кодом | — | PKG-S3-22 |
| BR-061 | runBackgroundReported: фоновые потоки ловят Throwable, пишут стек и | [domains/processing/components/async-process-queue.md](../../domains/processing/components/async-process-queue.md) | PKG-S3-22 |
| BR-065 | smartcopyperodbyday: 400 на невалидную дату/start > end/период > 31 дня | — | PKG-S3-22 |
| BR-066 | playerfile: ZIP стримится в ответ, finish/close в finally | — | PKG-S3-22 |
| BR-067 | convertFlacToMp3: вывод ffmpeg в файл, таймаут + destroyForcibly | — | PKG-S3-22 |
| BR-068 | массовый поиск текста: причина неудачи в лог и в SSE-сводку | — | PKG-S3-22 |
| BR-069 | extractlyricsbysearchresultid: ResponseEntity + явный 404 | — | PKG-S3-22 |
| BR-070 | /api/files: 404 «путь не найден» / 400 «не каталог» вместо подмены на / | — | PKG-S3-22 |
| BR-072 | 080/083 — геттеры Song больше не пишут в БД и не запускают процессы | [domains/catalog/components/song-entity.md](../../domains/catalog/components/song-entity.md) | PKG-S3-22 |
| BR-073 | Song.rootFolder — чистое чтение: геттер не подбирает «похожий» | [domains/catalog/components/song-entity.md](../../domains/catalog/components/song-entity.md) | PKG-S3-22 |
| BR-074 | Song.pictureAuthor / pictureAlbum — чистое чтение tblpictures | [domains/catalog/components/pictures.md](../../domains/catalog/components/pictures.md) | PKG-S3-22 |
| BR-075 | readiness-флаги (playerreadinessflags) пишутся точечным | [domains/catalog/components/song-entity.md](../../domains/catalog/components/song-entity.md) | PKG-S3-22 |
| BR-076 | 077/081 — валидация аппликатур и индексов: voice in indices | — | PKG-S3-22 |
| BR-078 | разбор файлов Sheetsage устойчив: trim/removePrefix вместо | [domains/catalog/components/song-entity.md](../../domains/catalog/components/song-entity.md) | PKG-S3-22 |
| BR-079 | Song.sheetsageInfo возвращает типизированный SheetsageInfo | [domains/catalog/components/song-entity.md](../../domains/catalog/components/song-entity.md) | PKG-S3-22 |
| BR-082 | тишина перед первым слогом считается по непустым голосам | — | PKG-S3-22 |
| BR-084 | разбор playerreadinessflags кэшируется в объекте (ключ кэша | [domains/catalog/components/song-entity.md](../../domains/catalog/components/song-entity.md) | PKG-S3-22 |
| BR-085 | getKeyBpmFromFile через parseKeyBpmFileOrNull (битый файл — WARN + пустой | — | PKG-S3-22 |
| BR-086 | все интерполируемые значения в HTML аккордов/табов проходят escapeHtml | — | PKG-S3-22 |
| BR-103 | слияние голосов при сохранении: saveToDb сливает «устаревший | [domains/catalog/components/song-entity.md](../../domains/catalog/components/song-entity.md) | PKG-S3-22 |
| BR-105 | фильтр списка «Количество голосов» считается по реальным маркерам | [domains/catalog/components/song-entity.md](../../domains/catalog/components/song-entity.md) | PKG-S3-22 |
| BR-106 | Song.deleteFromDb: сначала DELETE строки (в use {}, ошибка → | [domains/catalog/components/song-entity.md](../../domains/catalog/components/song-entity.md) | PKG-S3-22 |
| BR-107 | Song.createDbInstance ловит только SQLException и «лечит» только | — | PKG-S3-22 |
| BR-114 | AdminApiTokenFilter: при заданном karaoke.security.admin-token | [domains/identity/components/security-config.md](../../domains/identity/components/security-config.md) | PKG-S3-22 |

### PKG-S2-08

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-039 | Feature: Hardening deploy-контура (PKG-S2-08, аудит #336, OpenProject  — > | [system/infra/sql-migrations.md](../../system/infra/sql-migrations.md) | PKG-S2-08 |
| BR-040 | Feature: Hardening deploy-контура (PKG-S2-08, аудит #336, OpenProject  — > | — | PKG-S2-08 |
| BR-041 | платёжная история удалялась вместе с пользователем | [system/infra/sql-migrations.md](../../system/infra/sql-migrations.md) | PKG-S2-08 |
| BR-042 | > Продолжение: волна E, пакет PKG-S3-21 (15 S3-записей | — | PKG-S2-08 |
| BR-043 | «деплой успешен» при недоставленном образе : неудачный | — | PKG-S2-08 |
| BR-044 | Повторное применение старой миграции tblsiteusers — регресс. До каждая из | [domains/persistence/domain.md](../../domains/persistence/domain.md) | PKG-S2-08 |
| BR-045 | Feature: Hardening deploy-контура (PKG-S2-08, аудит #336, OpenProject  — > | — | PKG-S2-08 |
| BR-046 | Feature: Hardening deploy-контура (PKG-S2-08, аудит #336, OpenProject  — > | — | PKG-S2-08 |
| BR-047 | Feature: Hardening deploy-контура (PKG-S2-08, аудит #336, OpenProject  — > | — | PKG-S2-08 |
| BR-108 | ON DELETE RESTRICT в 22 + миграция 55; удаление — приложение (сначала объекты) | [domains/catalog/components/dictionaries.md](../../domains/catalog/components/dictionaries.md) | PKG-S2-08 |
| BR-534 | Feature: Hardening deploy-контура (PKG-S2-08, аудит #336, OpenProject  — > | [domains/persistence/domain.md](../../domains/persistence/domain.md) | PKG-S2-08 |
| BR-535 | Feature: Hardening deploy-контура (PKG-S2-08, аудит #336, OpenProject  — > | — | PKG-S2-08 |
| BR-536 | Известные ловушки — Регрессия закрыта guard'ом | — | PKG-S2-08 |
| BR-537 | Feature: Hardening deploy-контура (PKG-S2-08, аудит #336, OpenProject  — > | [system/infra/sql-migrations.md](../../system/infra/sql-migrations.md) | PKG-S2-08 |
| BR-538 | Feature: Hardening deploy-контура (PKG-S2-08, аудит #336, OpenProject  — > | — | PKG-S2-08 |
| BR-539 | бэкапов прод-БД не существовало : pgdump --no-password | [system/infra/deploy-overview.md](../../system/infra/deploy-overview.md) | PKG-S2-08 |
| BR-542 | Feature: Hardening deploy-контура (PKG-S2-08, аудит #336, OpenProject  — > | — | PKG-S2-08 |
| BR-543 | Feature: Hardening deploy-контура (PKG-S2-08, аудит #336, OpenProject  — > | — | PKG-S2-08 |
| BR-544 | меняет пакет #371 , nginxkaraoke-public.conf — пакет #393 | [product/features/security-headers.md](../../product/features/security-headers.md) | PKG-S2-08 |
| BR-545 | меняет пакет #371 , nginxkaraoke-public.conf — пакет #393 | [product/features/security-headers.md](../../product/features/security-headers.md) | PKG-S2-08 |
| BR-546 | Feature: Hardening deploy-контура (PKG-S2-08, аудит #336, OpenProject  — > ) → пакет PKG-S2-08 | [system/infra/deploy-overview.md](../../system/infra/deploy-overview.md) | PKG-S2-08 |
| BR-547 | Feature: Hardening deploy-контура (PKG-S2-08, аудит #336, OpenProject  — > | — | PKG-S2-08 |
| BR-548 | Feature: Hardening deploy-контура (PKG-S2-08, аудит #336, OpenProject  — > | — | PKG-S2-08 |
| BR-549 | python3 tools/check-security-headers.py (файл SPA-nginx) | [product/features/security-headers.md](../../product/features/security-headers.md) | PKG-S2-08 |
| BR-550 | Feature: Hardening deploy-контура (PKG-S2-08, аудит #336, OpenProject  — > ), OpenProject #393, ветка 611-pkg-s3-21 | — | PKG-S2-08 |
| BR-554 | Feature: Hardening deploy-контура (PKG-S2-08, аудит #336, OpenProject  — > ) → пакет PKG-S2-08 | — | PKG-S2-08 |
| BR-555 | Feature: Hardening deploy-контура (PKG-S2-08, аудит #336, OpenProject  — > ) → пакет PKG-S2-08 | — | PKG-S2-08 |
| BR-556 | Feature: Hardening deploy-контура (PKG-S2-08, аудит #336, OpenProject  — > ) → пакет PKG-S2-08 | — | PKG-S2-08 |
| BR-557 | stream-прокси без TLS + sslmode=prefer (драйвер | — | PKG-S2-08 |
| BR-559 | Feature: Hardening deploy-контура (PKG-S2-08, аудит #336, OpenProject  — > ), OpenProject #393, ветка 611-pkg-s3-21 | — | PKG-S2-08 |
| BR-560 | Feature: Hardening deploy-контура (PKG-S2-08, аудит #336, OpenProject  — > ), OpenProject #393, ветка 611-pkg-s3-21 | — | PKG-S2-08 |

### PKG-RACE-PUBLISH

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-062 | > Source: OpenProject #366 — пакет PKG-RACE-PUBLISH аудита #336 | [domains/publishing/components/publishing-services.md](../../domains/publishing/components/publishing-services.md) | PKG-RACE-PUBLISH |
| BR-138 | > Source: OpenProject #366 — пакет PKG-RACE-PUBLISH аудита #336 | [domains/publishing/components/publishing-services.md](../../domains/publishing/components/publishing-services.md) | PKG-RACE-PUBLISH |

### PKG-S2-17

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-087 | 2026-10-25 (088/093/124/125/370/379/380/388/399/410/411/416/417/426 | [domains/catalog/components/dictionaries.md](../../domains/catalog/components/dictionaries.md) | PKG-S2-17 |
| BR-088 | Аудит #336, пакет PKG-S2-17 (OpenProject #380, записи | [system/frontend/vuex-patterns.md](../../system/frontend/vuex-patterns.md) | PKG-S2-17 |
| BR-093 | прежнего фильтра, а «repair» в модалке HR отправлял ремонт ЧУЖОЙ песни | [system/frontend/vuex-patterns.md](../../system/frontend/vuex-patterns.md) | PKG-S2-17 |
| BR-124 | Аудит #336, пакет PKG-S2-17 (OpenProject #380, записи | [system/frontend/vuex-patterns.md](../../system/frontend/vuex-patterns.md) | PKG-S2-17 |
| BR-125 | Зачем — песне, а не к той, для которой ушёл запрос | [system/frontend/vuex-patterns.md](../../system/frontend/vuex-patterns.md) | PKG-S2-17 |
| BR-370 | Зачем — (HTTP 400 на каждое изменение колонки) | — | PKG-S2-17 |
| BR-379 | Зачем — ) | [system/frontend/vuex-patterns.md](../../system/frontend/vuex-patterns.md) | PKG-S2-17 |
| BR-380 | прежнего фильтра, а «repair» в модалке HR отправлял ремонт ЧУЖОЙ песни | [system/frontend/vuex-patterns.md](../../system/frontend/vuex-patterns.md) | PKG-S2-17 |
| BR-388 | Массовое удаление процессов брало id из snapshot прошлого фильтра | [system/frontend/vuex-patterns.md](../../system/frontend/vuex-patterns.md) | PKG-S2-17 |
| BR-399 | Зачем — закрывалась без сохранения | — | PKG-S2-17 |
| BR-410 | Дайджест share-ссылок грузил только первую страницу из 25 строк | [system/frontend/target-request-guard.md](../../system/frontend/target-request-guard.md) | PKG-S2-17 |
| BR-411 | с HTTP 500, а испорченный фильтр переживал F5 | [system/frontend/target-request-guard.md](../../system/frontend/target-request-guard.md) | PKG-S2-17 |
| BR-416 | стирала поля админа. Новая копия сборки payload — это возврат | [system/frontend/vuex-patterns.md](../../system/frontend/vuex-patterns.md) | PKG-S2-17 |
| BR-417 | привязку к ладу , а закрытие в течение 3 с теряло правку | — | PKG-S2-17 |
| BR-426 | Зачем — ) | [system/frontend/vuex-patterns.md](../../system/frontend/vuex-patterns.md) | PKG-S2-17 |

### PKG-S3-31

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-089 | и карточки specs/336-code-audit/bug-report.md (записи …) | — | PKG-S3-31 |
| BR-090 | Что делает — Карточка песни | — | PKG-S3-31 |
| BR-091 | Что делает — Карточка песни | — | PKG-S3-31 |
| BR-092 | Что делает — Карточка песни | — | PKG-S3-31 |
| BR-094 | песен закрыт пакетом #380; «Sync Ids» сравнивается по элементам | — | PKG-S3-31 |
| BR-095 | Что делает — Стор песен — дайджест | — | PKG-S3-31 |
| BR-096 | Что делает — Стор песен — дайджест | — | PKG-S3-31 |
| BR-097 | Что делает — Стор песен — дайджест | — | PKG-S3-31 |
| BR-098 | Что делает — Стор песен — дайджест | — | PKG-S3-31 |
| BR-099 | Что делает — Стор песен — дайджест | — | PKG-S3-31 |
| BR-126 | Что делает — Карточка песни | — | PKG-S3-31 |
| BR-127 | Что делает — Карточка песни | — | PKG-S3-31 |
| BR-363 | Что делает — Альбомы — загрузка дайджеста с | — | PKG-S3-31 |
| BR-364 | Что делает — Альбомы — загрузка дайджеста с | — | PKG-S3-31 |
| BR-365 | Что делает — Альбомы — загрузка дайджеста с | — | PKG-S3-31 |
| BR-366 | Что делает — Альбомы — загрузка дайджеста с | — | PKG-S3-31 |
| BR-367 | Что делает — Авторы — перед | — | PKG-S3-31 |
| BR-368 | Что делает — Авторы — перед | — | PKG-S3-31 |
| BR-369 | Что делает — Приложение и общие компоненты ( | — | PKG-S3-31 |
| BR-372 | Что делает — Приложение и общие компоненты ( | — | PKG-S3-31 |
| BR-373 | Что делает — Авторы — перед | — | PKG-S3-31 |
| BR-374 | Что делает — Авторы — перед | — | PKG-S3-31 |
| BR-375 | Что делает — Авторы — перед | — | PKG-S3-31 |
| BR-376 | Что делает — Авторы — перед | — | PKG-S3-31 |
| BR-377 | Что делает — Авторы — перед | — | PKG-S3-31 |
| BR-378 | Что делает — Приложение и общие компоненты ( | — | PKG-S3-31 |
| BR-381 | Что делает — Приложение и общие компоненты ( | — | PKG-S3-31 |
| BR-382 | Что делает — История прослушиваний и словари — фильтры | — | PKG-S3-31 |
| BR-383 | Что делает — История прослушиваний и словари — фильтры | — | PKG-S3-31 |
| BR-384 | Что делает — История прослушиваний и словари — фильтры | — | PKG-S3-31 |
| BR-385 | Что делает — Приложение и общие компоненты ( | — | PKG-S3-31 |
| BR-386 | ) — App.vue не роняет бейджи из-за отказа восстановления | — | PKG-S3-31 |
| BR-387 | ) — App.vue не роняет бейджи из-за отказа восстановления | — | PKG-S3-31 |
| BR-389 | Что делает — Новости и картинки — «Строк на | — | PKG-S3-31 |
| BR-390 | Что делает — Новости и картинки — «Строк на | — | PKG-S3-31 |
| BR-391 | Что делает — Новости и картинки — «Строк на | — | PKG-S3-31 |
| BR-392 | Что делает — Новости и картинки — «Строк на | — | PKG-S3-31 |
| BR-393 | Что делает — Новости и картинки — «Строк на | — | PKG-S3-31 |
| BR-394 | Процессы — смена «строк на страницу» грузит страницу один раз | — | PKG-S3-31 |

### PKG-S2-11

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-110 | LRU-кэш огибающих на 64 записи; при смене mtime прежний ключ того же файла удаляется | [system/utilities.md](../../system/utilities.md) | PKG-S2-11 |
| BR-111 | plans/runs — LRU (32/128) + TTL 30 мин; отчёт удаляется после терминального apply | [domains/storage/domain.md](../../domains/storage/domain.md) | PKG-S2-11 |
| BR-112 | фильтр/сортировка/пагинация в SQL (EXISTS, ORDER BY, LIMIT/OFFSET, COUNT); песни — только для страницы | [system/utilities.md](../../system/utilities.md) | PKG-S2-11 |
| BR-113 | loadBySongIds → Map<Long, List<SongAssignment>>; детерминированный выбор (open, затем max id); публичная карточка ищет назначение текущего редактора | [domains/processing/components/playwright-rendering.md](../../domains/processing/components/playwright-rendering.md) | PKG-S2-11 |
| BR-128 | loadBySongIds → Map<Long, List<SongAssignment>>; детерминированный выбор (open, затем max id); публичная карточка ищет назначение текущего редактора | [domains/editorial/components/assignment-lifecycle.md](../../domains/editorial/components/assignment-lifecycle.md) | PKG-S2-11 |
| BR-132 | loadBySongIds → Map<Long, List<SongAssignment>>; детерминированный выбор (open, затем max id); публичная карточка ищет назначение текущего редактора | [domains/health/components/health-report-batch-pool.md](../../domains/health/components/health-report-batch-pool.md) | PKG-S2-11 |
| BR-133 | Base64-значения, атомарная запись под lock, устойчивый разбор (битая строка → WARN) | [domains/processing/components/karaoke-properties.md](../../domains/processing/components/karaoke-properties.md) | PKG-S2-11 |
| BR-139 | каждая попытка sendVideo в try/catch; сеть/таймаут/не-JSON — retryable; исходная ошибка как cause | [domains/publishing/components/publishing-services.md](../../domains/publishing/components/publishing-services.md) | PKG-S2-11 |
| BR-140 | каждая попытка sendVideo в try/catch; сеть/таймаут/не-JSON — retryable; исходная ошибка как cause | [domains/publishing/components/publishing-services.md](../../domains/publishing/components/publishing-services.md) | PKG-S2-11 |
| BR-141 | каждая попытка sendVideo в try/catch; сеть/таймаут/не-JSON — retryable; исходная ошибка как cause | [domains/storage/domain.md](../../domains/storage/domain.md) | PKG-S2-11 |
| BR-142 | uploadFile(path) бросает исключение; нет файла и сбой загрузки различимы | [domains/publishing/components/publishing-services.md](../../domains/publishing/components/publishing-services.md) | PKG-S2-11 |
| BR-143 | DTO отдаётся как ResponseEntity<StatsDebugDto>, без as Map | — | PKG-S2-11 |
| BR-148 | VkAutoPublishService.kt, VkPreviewWarmupClient.kt, VkPhotoUploadClient.kt | [domains/processing/components/karaoke-properties.md](../../domains/processing/components/karaoke-properties.md) | PKG-S2-11 |
| BR-162 | уборка только бакетов karaoke/stemjobs; сбой уборки при старте → WARN, не отказ контекста | [domains/storage/domain.md](../../domains/storage/domain.md) | PKG-S2-11 |
| BR-176 | null биндится как типизированный SQL NULL; ошибка executeUpdate пробрасывается | — | PKG-S2-11 |

### PKG-S3-23

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-117 | Что делает — P-08 (…) — картинки , дайджест share-ссылок | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | PKG-S3-23 |
| BR-118 | Что делает — P-08 (…) — картинки , дайджест share-ссылок | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | PKG-S3-23 |
| BR-119 | список новостей , границы акции , поиск текста в Яндекс.Музыке | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | PKG-S3-23 |
| BR-120 | список новостей , границы акции , поиск текста в Яндекс.Музыке | — | PKG-S3-23 |
| BR-121 | Что делает — ffmpeg без таймаута | [domains/processing/domain.md](../../domains/processing/domain.md) | PKG-S3-23 |
| BR-122 | Что делает — P-08 (…) — картинки , дайджест share-ссылок | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | PKG-S3-23 |
| BR-129 | P-19 (…) — черновик редакторского назначения , пустой | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | PKG-S3-23 |
| BR-130 | голос в раскладке , переименование артефактов песни | [domains/catalog/domain.md](../../domains/catalog/domain.md) | PKG-S3-23 |
| BR-131 | P-19 (…) — черновик редакторского назначения , пустой | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | PKG-S3-23 |
| BR-134 | P-21 (…) — проверки мониторинга , пересечение тиков | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | PKG-S3-23 |
| BR-135 | Что делает — реестр admin-задач , клиент forced-alignment | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | PKG-S3-23 |
| BR-136 | Что делает — реестр admin-задач , клиент forced-alignment | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | PKG-S3-23 |
| BR-137 | P-21 (…) — проверки мониторинга , пересечение тиков | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | PKG-S3-23 |
| BR-144 | P-22/P-23/P-24 (…) — кеш метаданных хранилища | [domains/monitoring/components/error-semantics.md](../../domains/monitoring/components/error-semantics.md) | PKG-S3-23 |
| BR-145 | circuit breaker , авто-публикация в Telegram , подписка SSE | [domains/monitoring/components/error-semantics.md](../../domains/monitoring/components/error-semantics.md) | PKG-S3-23 |
| BR-146 | circuit breaker , авто-публикация в Telegram , подписка SSE | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | PKG-S3-23 |
| BR-147 | Что делает — авто-публикация в VK (…), long-poll Telegram | [domains/sse/domain.md](../../domains/sse/domain.md) | PKG-S3-23 |
| BR-149 | Что делает — авто-публикация в VK (…), long-poll Telegram | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | PKG-S3-23 |
| BR-157 | Что делает — авто-публикация в VK (…), long-poll Telegram | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | PKG-S3-23 |
| BR-158 | Что делает — ASR Whisper , локальное хранилище | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | PKG-S3-23 |
| BR-159 | Что делает — ASR Whisper , локальное хранилище | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | PKG-S3-23 |
| BR-160 | Что делает — авто-публикация в VK (…), long-poll Telegram | [domains/monitoring/components/error-semantics.md](../../domains/monitoring/components/error-semantics.md) | PKG-S3-23 |
| BR-163 | Что делает — ASR Whisper , локальное хранилище | [domains/monitoring/components/error-semantics.md](../../domains/monitoring/components/error-semantics.md) | PKG-S3-23 |
| BR-164 | Что делает — ASR Whisper , локальное хранилище | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | PKG-S3-23 |
| BR-165 | Что делает — премиум-публикация | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | PKG-S3-23 |
| BR-166 | P-22/P-23/P-24 (…) — кеш метаданных хранилища | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | PKG-S3-23 |
| BR-167 | P-22/P-25 (…) — синк Sponsr , audit-транзакция | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | PKG-S3-23 |
| BR-168 | Что делает — процессов , уборка артефактов (…), словари | [domains/processing/components/process-admin.md](../../domains/processing/components/process-admin.md) | PKG-S3-23 |
| BR-169 | Что делает — процессов , уборка артефактов (…), словари | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | PKG-S3-23 |
| BR-171 | Что делает — процессов , уборка артефактов (…), словари | [domains/health/domain.md](../../domains/health/domain.md) | PKG-S3-23 |
| BR-172 | Что делает — процессов , уборка артефактов (…), словари | — | PKG-S3-23 |
| BR-173 | P-22/P-25 (…) — синк Sponsr , audit-транзакция | [domains/monitoring/components/error-semantics.md](../../domains/monitoring/components/error-semantics.md) | PKG-S3-23 |

### OpenProject #360

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-123 | Source: OpenProject #360 — «S1: 25 обработчиков подтверждения собирают customConfirmParams.body интерполяцией имени песни » | [product/features/escape-confirm-user-data.md](../../product/features/escape-confirm-user-data.md) | OpenProject #360 |

### PKG-S3-35

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-154 | Каждый вызов VK API в прокси-режиме создавал новый | [domains/integration/components/external-api-clients.md](../../domains/integration/components/external-api-clients.md) | PKG-S3-35 |
| BR-207 | (208/209/210/211/212/215/216/217/218). Страница публикаций | — | PKG-S3-35 |
| BR-216 | перенесены в SPA. Правки 217/218, сделанные «на месте» в легаси-странице | [domains/sse/domain.md](../../domains/sse/domain.md) | PKG-S3-35 |
| BR-217 | Что делает — Легаси-страница песен и CI ( | — | PKG-S3-35 |
| BR-218 | …. Легаси-страница песен подписывалась на STOMP-эндпоинт | — | PKG-S3-35 |
| BR-220 | Диджесты . SitePlaylistItem.countItemsByPlaylistIds | [domains/identity/components/dictionaries.md](../../domains/identity/components/dictionaries.md) | PKG-S3-35 |
| BR-221 | серверная пагинация есть, но админские таблицы всё ещё | [domains/identity/components/dictionaries.md](../../domains/identity/components/dictionaries.md) | PKG-S3-35 |
| BR-222 | Редактор (…). approve разделён на публичный метод | [domains/editorial/components/assignment-lifecycle.md](../../domains/editorial/components/assignment-lifecycle.md) | PKG-S3-35 |
| BR-223 | Что делает — karaoke-app, диджесты и редактор ( | [domains/editorial/components/assignment-lifecycle.md](../../domains/editorial/components/assignment-lifecycle.md) | PKG-S3-35 |
| BR-224 | Сбой push'а на SERVER и сбой фоновой синхронизации | [domains/editorial/components/assignment-lifecycle.md](../../domains/editorial/components/assignment-lifecycle.md) | PKG-S3-35 |
| BR-225 | guard tools/check-no-legacy-tbl-settings.sh расширен | — | PKG-S3-35 |
| BR-226 | TTL-кеш статистики не имел single-flight: два одновременных | [domains/stats/domain.md](../../domains/stats/domain.md) | PKG-S3-35 |
| BR-507 | Зачем — В CI не было сборки фронтенда; guard прямых | — | PKG-S3-35 |
| BR-509 | ) — контракт realtime в легаси-странице песен приведён к серверному | — | PKG-S3-35 |
| BR-510 | ) — контракт realtime в легаси-странице песен приведён к серверному | — | PKG-S3-35 |
| BR-517 | Отдельно: (karaoke-web) — отладочный println/printStackTrace в | — | PKG-S3-35 |

### PKG-S3-24

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-174 | Партия 1 (…10 записей) — отчёт сухого прогона чистки строится вне | — | PKG-S3-24 |
| BR-182 | Author.getWhereList (POST /api/authors/authorsdigests, albumsdigests?filterAuthorName=) | [domains/catalog/components/author-entity.md](../../domains/catalog/components/author-entity.md) | PKG-S3-24 |
| BR-187 | Партия 1 (…10 записей) — отчёт сухого прогона чистки строится вне | [domains/persistence/domain.md](../../domains/persistence/domain.md) | PKG-S3-24 |
| BR-189 | Партия 2 (…10 записей) — конфликт слота публикации виден; имя | [domains/catalog/domain.md](../../domains/catalog/domain.md) | PKG-S3-24 |
| BR-199 | Партия 2 (…10 записей) — конфликт слота публикации виден; имя | — | PKG-S3-24 |
| BR-202 | Партия 3 (…10 записей) — <datalist> читают реальные имена модели | — | PKG-S3-24 |
| BR-215 | Партия 3 (…10 записей) — <datalist> читают реальные имена модели | — | PKG-S3-24 |

### PKG-S2-12

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-188 | `popPublicationStacks` в `model/Publication.kt`: условие выхода — **живое состояние стопок** (`while (stacks.any { it.isNotEmpty() })`), а не захваченный максимум размера (прежний вариант давал вечный цикл и OOM); покрыто тестом уровня вызывающих (спека 676, FR-004) | [domains/catalog/components/remaining-models.md](../../domains/catalog/components/remaining-models.md) | PKG-S2-12 |
| BR-195 | StatsCacheKey.forEndpoint(endpoint, target, params) включает | [domains/stats/domain.md](../../domains/stats/domain.md) | PKG-S2-12 |
| BR-200 | 206/214 — karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Pagination.kt: LEGACYPAGESIZE = 200 | [domains/catalog/components/remaining-models.md](../../domains/catalog/components/remaining-models.md) | PKG-S2-12 |
| BR-201 | из processes.html, publications.html, unpublications.html | — | PKG-S2-12 |
| BR-205 | 213 — songsUpdate/songs2Update объявляют отсутствующие в формах | — | PKG-S2-12 |
| BR-206 | Что делает — Закрывает 15 записей аудита | — | PKG-S2-12 |
| BR-213 | ) закрыты волнами A–D — их правки не дублируются, ссылки на пакеты | — | PKG-S2-12 |
| BR-214 | ) закрыты волнами A–D — их правки не дублируются, ссылки на пакеты | — | PKG-S2-12 |
| BR-219 | validateTariffValues (karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/TariffValidation.kt) до записи | [domains/monetization/domain.md](../../domains/monetization/domain.md) | PKG-S2-12 |
| BR-227 | ScraperAgent.extractLyrics(pageContent, artist, songTitle) | [domains/integration/domain.md](../../domains/integration/domain.md) | PKG-S2-12 |
| BR-234 | MainController.doCollectStore грузит реальный список песен | — | PKG-S2-12 |
| BR-235 | isValidVoiceIndex(voice, countVoices) до вызова | — | PKG-S2-12 |
| BR-244 | KaraokeProperties: propertiesWriteLock вокруг «карта → | [domains/processing/components/karaoke-properties.md](../../domains/processing/components/karaoke-properties.md) | PKG-S2-12 |
| BR-251 | postChunksConfirming отправляет чанки по одному и | [domains/processing/components/two-db-sync.md](../../domains/processing/components/two-db-sync.md) | PKG-S2-12 |
| BR-266 | authorRequestOrder даёт один круг кандидатов; рекурсия | [system/utilities.md](../../system/utilities.md) | PKG-S2-12 |

### PKG-S3-25

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-228 | единый фильтр URL для обоих движков поиска , пакетная | [domains/monitoring/components/boundary-validation.md](../../domains/monitoring/components/boundary-validation.md) | PKG-S3-25 |
| BR-229 | единый фильтр URL для обоих движков поиска , пакетная | [domains/monitoring/components/boundary-validation.md](../../domains/monitoring/components/boundary-validation.md) | PKG-S3-25 |
| BR-230 | P-12 (…) — llm + mlt: размер скачиваемой страницы | [domains/monitoring/components/boundary-validation.md](../../domains/monitoring/components/boundary-validation.md) | PKG-S3-25 |
| BR-231 | не менялись, экран показывал пустые значения . Неразобранные маркеры | [domains/monitoring/components/boundary-validation.md](../../domains/monitoring/components/boundary-validation.md) | PKG-S3-25 |
| BR-232 | Генерация .mlt делала ~9 000 запросов к БД на песню только за | [domains/monitoring/components/boundary-validation.md](../../domains/monitoring/components/boundary-validation.md) | PKG-S3-25 |
| BR-233 | VK-заголовком без ссылки на песню . Каждое изменение поля в админке | — | PKG-S3-25 |
| BR-236 | → 200 с текстом Error, клиент считал, что сохранилось . Сбой БД при | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | PKG-S3-25 |
| BR-238 | P-14/P-13 (…) — mlt/mko + model/Album: сравнение строк по | [domains/monitoring/components/boundary-validation.md](../../domains/monitoring/components/boundary-validation.md) | PKG-S3-25 |
| BR-239 | значению , валидация stringLad , сбой БД ≠ «нет альбомов» | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | PKG-S3-25 |
| BR-240 | загрузке альбомов → 200 с , и результат ещё кэшировался на TTL | [domains/monitoring/components/boundary-validation.md](../../domains/monitoring/components/boundary-validation.md) | PKG-S3-25 |
| BR-241 | валидный узел вместо nameless , ссылки mainbin на | [domains/monitoring/components/boundary-validation.md](../../domains/monitoring/components/boundary-validation.md) | PKG-S3-25 |
| BR-242 | существующие узлы , проверка размера списков счётчика | [domains/monitoring/components/boundary-validation.md](../../domains/monitoring/components/boundary-validation.md) | PKG-S3-25 |
| BR-243 | IndexOutOfBoundsException в template . Отсутствие TEXT-элемента | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | PKG-S3-25 |
| BR-245 | свойства вместо исключения , регистрация трёх ключей, которые читает | [domains/monitoring/components/boundary-validation.md](../../domains/monitoring/components/boundary-validation.md) | PKG-S3-25 |
| BR-246 | karaoke-web , volatile-флаг загрузки + публикация карты после | — | PKG-S3-25 |
| BR-248 | наполнения , наблюдаемый результат записи файла , построчный | — | PKG-S3-25 |
| BR-249 | наполнения , наблюдаемый результат записи файла , построчный | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | PKG-S3-25 |
| BR-250 | P-05 (…) — KaraokeProperties: разбор значения по типу | [domains/monitoring/components/boundary-validation.md](../../domains/monitoring/components/boundary-validation.md) | PKG-S3-25 |
| BR-252 | одной транзакции , атомарный захват флагов , снятие флага в | [domains/monitoring/components/boundary-validation.md](../../domains/monitoring/components/boundary-validation.md) | PKG-S3-25 |
| BR-253 | одной транзакции , атомарный захват флагов , снятие флага в | [domains/monitoring/components/boundary-validation.md](../../domains/monitoring/components/boundary-validation.md) | PKG-S3-25 |
| BR-254 | finally , закрытие потока обхода файлов , битая строка CSV | [domains/monitoring/components/boundary-validation.md](../../domains/monitoring/components/boundary-validation.md) | PKG-S3-25 |
| BR-255 | finally , закрытие потока обхода файлов , битая строка CSV | [domains/monitoring/domain.md](../../domains/monitoring/domain.md) | PKG-S3-25 |
| BR-256 | как пропуск , текст песни без HTML-разметки , сопоставление | [domains/monitoring/components/boundary-validation.md](../../domains/monitoring/components/boundary-validation.md) | PKG-S3-25 |
| BR-257 | как пропуск , текст песни без HTML-разметки , сопоставление | [domains/monitoring/components/boundary-validation.md](../../domains/monitoring/components/boundary-validation.md) | PKG-S3-25 |
| BR-259 | шагов задания по jobId , статус WORKING только после вставки | [domains/monitoring/components/boundary-validation.md](../../domains/monitoring/components/boundary-validation.md) | PKG-S3-25 |
| BR-260 | шага , кеш IAM-токена , JSON без интерполяции | [domains/monitoring/components/boundary-validation.md](../../domains/monitoring/components/boundary-validation.md) | PKG-S3-25 |
| BR-261 | шага , кеш IAM-токена , JSON без интерполяции | [domains/monitoring/components/boundary-validation.md](../../domains/monitoring/components/boundary-validation.md) | PKG-S3-25 |
| BR-262 | шага , кеш IAM-токена , JSON без интерполяции | [domains/monitoring/components/boundary-validation.md](../../domains/monitoring/components/boundary-validation.md) | PKG-S3-25 |
| BR-263 | различение исходов ffprobe , сбой выборки ≠ «пусто» | — | PKG-S3-25 |
| BR-264 | различение исходов ffprobe , сбой выборки ≠ «пусто» | [domains/monitoring/components/boundary-validation.md](../../domains/monitoring/components/boundary-validation.md) | PKG-S3-25 |
| BR-265 | различение исходов загрузки HTML , сравнение ymId по значению | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | PKG-S3-25 |
| BR-267 | P-07/P-06 (…) — Utils/UtilsAI/стемы/синк: DELETE+INSERT в | — | PKG-S3-25 |
| BR-268 | все слова , закрытие соединения определения страны , таймаут | — | PKG-S3-25 |
| BR-269 | все слова , закрытие соединения определения страны , таймаут | — | PKG-S3-25 |
| BR-270 | и уборка внешнего процесса , экранирование значений в скрипте для | [domains/monitoring/components/boundary-validation.md](../../domains/monitoring/components/boundary-validation.md) | PKG-S3-25 |
| BR-271 | хоста , безопасное транспонирование аккорда , пакетная | [domains/monitoring/components/boundary-validation.md](../../domains/monitoring/components/boundary-validation.md) | PKG-S3-25 |
| BR-272 | хоста , безопасное транспонирование аккорда , пакетная | — | PKG-S3-25 |
| BR-273 | загрузка кандидатов-родителей , валидация соединения не в горячем | [domains/monitoring/domain.md](../../domains/monitoring/domain.md) | PKG-S3-25 |
| BR-274 | Что делает — пути , стек исключения в логе | [domains/monitoring/components/boundary-validation.md](../../domains/monitoring/components/boundary-validation.md) | PKG-S3-25 |
| BR-275 | P-07b/P-03 (…) — Utils/KaraokeConnection: разбиение на | [domains/monitoring/components/boundary-validation.md](../../domains/monitoring/components/boundary-validation.md) | PKG-S3-25 |

### OpenProject #361

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-258 | Source: OpenProject #361 — «S1: результаты и исходники премиум-задания „Создать минусовку“ кладутся в тот же бакет karaoke, что и публичные картинки/mp3 » | [domains/storage/components/storage-flow.md](../../domains/storage/components/storage-flow.md) | OpenProject #361 |

### PKG-S3-26

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-276 | voices писал в flashColor ; проверяйте ключ при отладке | — | PKG-S3-26 |
| BR-277 | Известные ловушки — кнопки «Удалить» в отчёте о здоровье | [domains/health/components/health-report.md](../../domains/health/components/health-report.md) | PKG-S3-26 |
| BR-278 | Известные ловушки — DISKVANISHED / UNKNOWN . UNKNOWN → WARNING + | [domains/health/components/health-report.md](../../domains/health/components/health-report.md) | PKG-S3-26 |
| BR-279 | «Не удалось узнать размер объекта» ≠ «файл актуален»: вердикт UNKNOWN → WARNING + storageStateUnknown | [domains/health/components/health-report.md](../../domains/health/components/health-report.md) | PKG-S3-26 |
| BR-280 | Health-отчёты и ремонт (…8 записей) — отчёт больше не показывает | [domains/health/components/health-report.md](../../domains/health/components/health-report.md) | PKG-S3-26 |
| BR-281 | «Не удалось узнать размер объекта» ≠ «файл актуален»: вердикт UNKNOWN → WARNING + storageStateUnknown | [domains/health/components/health-report.md](../../domains/health/components/health-report.md) | PKG-S3-26 |
| BR-282 | обязана брать attemptEnterRepair и снимать exitRepair | [domains/health/components/health-report.md](../../domains/health/components/health-report.md) | PKG-S3-26 |
| BR-285 | Известные ловушки — SSE | [domains/health/components/health-report.md](../../domains/health/components/health-report.md) | PKG-S3-26 |
| BR-286 | в println: пропуск проверки обязан быть виден в логах . Новые категории | [domains/health/components/health-report.md](../../domains/health/components/health-report.md) | PKG-S3-26 |
| BR-287 | Health-отчёты и ремонт (…8 записей) — отчёт больше не показывает | [domains/health/components/health-report.md](../../domains/health/components/health-report.md) | PKG-S3-26 |
| BR-289 | Настройки, словари, крипта (…8 записей) — настройка | [domains/catalog/components/song-entity.md](../../domains/catalog/components/song-entity.md) | PKG-S3-26 |
| BR-293 | Поиск обложек и гигиена репозитория (3 записи) — сбой | [domains/integration/components/external-api-clients.md](../../domains/integration/components/external-api-clients.md) | PKG-S3-26 |
| BR-294 | Настройки, словари, крипта (…8 записей) — настройка | [domains/catalog/components/song-entity.md](../../domains/catalog/components/song-entity.md) | PKG-S3-26 |
| BR-526 | регрессионный тест PkgS326BehaviorTest : он ищет фикстуры с | [guidelines/architecture-conventions.md](../../guidelines/architecture-conventions.md) | PKG-S3-26 |
| BR-531 | регрессионный тест PkgS326BehaviorTest : он ищет фикстуры с | [guidelines/architecture-conventions.md](../../guidelines/architecture-conventions.md) | PKG-S3-26 |

### PKG-S2-14

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-295 | utils/shareRevoke.js:isRevokeConfirmed(status, body); revoke | — | PKG-S2-14 |
| BR-302 | KaraokePlayer.init захватывает локальный AbortController | — | PKG-S2-14 |
| BR-303 | листенер visibilitychange → release удалён | — | PKG-S2-14 |
| BR-312 | экранирование (&, <, >, ", ') текста слога и метки | [product/features/escape-confirm-user-data.md](../../product/features/escape-confirm-user-data.md) | PKG-S2-14 |
| BR-313 | подписки composable useZakromaStreamProgress идут через | — | PKG-S2-14 |
| BR-319 | ChatView.mounted ждёт await fetchMe, доступ считается | — | PKG-S2-14 |
| BR-327 | beforeRouteLeave при взведённом таймере выполняет | — | PKG-S2-14 |
| BR-328 | ensureEndMarker(markers, duration) не трогает маркеры при | — | PKG-S2-14 |
| BR-329 | utils/wavesurferTeardown.js: destroyWaveform(owner) перед | — | PKG-S2-14 |
| BR-330 | saveNow/submit/recall обёрнуты в try/catch/finally | — | PKG-S2-14 |
| BR-339 | authApi.request/authPostJson/authUpload задают xhr.timeout | — | PKG-S2-14 |
| BR-340 | utils/captchaReady.js: captchaScriptState различает | — | PKG-S2-14 |
| BR-350 | utils/subscriptionPoll.js: classifyPollResponse + | — | PKG-S2-14 |
| BR-523 | SubsEdit.vue. Оба регрессионных теста включены | [domains/README.md](../../domains/README.md) | PKG-S2-14 |

### PKG-S3-27

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-296 | и карточки specs/336-code-audit/bug-report.md (записи …) | [system/frontend/webvue3-audit-invariants.md](../../system/frontend/webvue3-audit-invariants.md) | PKG-S3-27 |
| BR-297 | Что делает — Компоненты и share-ссылки ( | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | PKG-S3-27 |
| BR-298 | Что делает — Компоненты и share-ссылки ( | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | PKG-S3-27 |
| BR-299 | Что делает — Компоненты и share-ссылки ( | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | PKG-S3-27 |
| BR-300 | Что делает — Компоненты и share-ссылки ( | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | PKG-S3-27 |
| BR-301 | ) — копирование подтверждается результатом; ответ корзины разбирается | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | PKG-S3-27 |
| BR-304 | Плеер (…) — отменённая загрузка не показывается как ошибка | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | PKG-S3-27 |
| BR-311 | Плеер (…) — отменённая загрузка не показывается как ошибка | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | PKG-S3-27 |
| BR-314 | Что делает — Сессия и навигация — корзина | [system/frontend/composable-use-cart.md](../../system/frontend/composable-use-cart.md) | PKG-S3-27 |
| BR-316 | Что делает — Сессия и навигация — корзина | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | PKG-S3-27 |
| BR-317 | Что делает — Сессия и навигация — корзина | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | PKG-S3-27 |
| BR-318 | Что делает — Сессия и навигация — корзина | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | PKG-S3-27 |
| BR-320 | Что делает — Сессия и навигация — корзина | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | PKG-S3-27 |
| BR-321 | Хранилища и store — одно поколение выдачи для | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | PKG-S3-27 |
| BR-322 | Хранилища и store — одно поколение выдачи для | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | PKG-S3-27 |
| BR-323 | Хранилища и store — одно поколение выдачи для | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | PKG-S3-27 |
| BR-324 | Просмотры — поллинг чата работает и для пустого | — | PKG-S3-27 |
| BR-325 | Просмотры — поллинг чата работает и для пустого | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | PKG-S3-27 |
| BR-326 | Просмотры — поллинг чата работает и для пустого | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | PKG-S3-27 |
| BR-331 | Редактор заданий (…) — отправка прерывается, если черновик | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | PKG-S3-27 |
| BR-338 | Редактор заданий (…) — отправка прерывается, если черновик | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | PKG-S3-27 |
| BR-346 | и карточки specs/336-code-audit/bug-report.md (записи …) | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | PKG-S3-27 |

### PKG-S3-28

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-347 | каталог авторов — Map-индекс (authorIndex.js), резолв id один раз на строку (в computed searchRows), а не два линейных find в шаблоне | — | PKG-S3-28 |
| BR-348 | таймеры ожидания токена снимаются при размонтировании, ожидающие промисы резолвятся null; postMessage принимается только от своего origin и родительского окна | — | PKG-S3-28 |
| BR-349 | таймеры ожидания токена снимаются при размонтировании, ожидающие промисы резолвятся null; postMessage принимается только от своего origin и родительского окна | — | PKG-S3-28 |
| BR-351 | id таймера опроса сохраняется и снимается в beforeUnmount; авто-переход на песню — только если компонент смонтирован и маршрут всё ещё /subscription/return | — | PKG-S3-28 |
| BR-352 | отмена автопродления проверяет status и ответ сети: успех подтверждается сообщением, отказ — текстом, список перезагружается только после успеха | — | PKG-S3-28 |
| BR-353 | состояние выводится из маршрута; back-link строится чистой функцией; «Повторить» повторяет запуск стрима без навигации; «Автор не найден» — явное пустое состояние | — | PKG-S3-28 |
| BR-354 | состояние выводится из маршрута; back-link строится чистой функцией; «Повторить» повторяет запуск стрима без навигации; «Автор не найден» — явное пустое состояние | — | PKG-S3-28 |
| BR-355 | состояние выводится из маршрута; back-link строится чистой функцией; «Повторить» повторяет запуск стрима без навигации; «Автор не найден» — явное пустое состояние | — | PKG-S3-28 |
| BR-356 | «ссылка мертва» только для доменных кодов; 5xx/сеть — «сервис недоступен» с повтором; lease сессии проверяется перед переходом в плеер, истёкший — продлевается повторным claim | — | PKG-S3-28 |
| BR-357 | состояние выводится из маршрута; back-link строится чистой функцией; «Повторить» повторяет запуск стрима без навигации; «Автор не найден» — явное пустое состояние | — | PKG-S3-28 |
| BR-358 | «ссылка мертва» только для доменных кодов; 5xx/сеть — «сервис недоступен» с повтором; lease сессии проверяется перед переходом в плеер, истёкший — продлевается повторным claim | — | PKG-S3-28 |
| BR-359 | «ссылка мертва» только для доменных кодов; 5xx/сеть — «сервис недоступен» с повтором; lease сессии проверяется перед переходом в плеер, истёкший — продлевается повторным claim | — | PKG-S3-28 |
| BR-360 | «ссылка мертва» только для доменных кодов; 5xx/сеть — «сервис недоступен» с повтором; lease сессии проверяется перед переходом в плеер, истёкший — продлевается повторным claim | — | PKG-S3-28 |
| BR-361 | таймер опроса — на инстансе и создаётся до await; удалённые задания не возвращаются в список при поллинге | — | PKG-S3-28 |
| BR-362 | таймер опроса — на инстансе и создаётся до await; удалённые задания не возвращаются в список при поллинге | — | PKG-S3-28 |

### PKG-S3-32

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-395 | и карточки specs/336-code-audit/bug-report.md (записи …) | — | PKG-S3-32 |
| BR-396 | Картинки — снимок автосейва собирается из отправленного | — | PKG-S3-32 |
| BR-398 | Что делает — Процессы — CSV-экспорт | — | PKG-S3-32 |
| BR-400 | Что делает — Процессы — CSV-экспорт | — | PKG-S3-32 |
| BR-401 | Что делает — Процессы — CSV-экспорт | — | PKG-S3-32 |
| BR-402 | Что делает — Процессы — CSV-экспорт | — | PKG-S3-32 |
| BR-403 | Что делает — Процессы — CSV-экспорт | — | PKG-S3-32 |
| BR-404 | Что делает — Настройки, акции, публикации | — | PKG-S3-32 |
| BR-405 | Что делает — Настройки, акции, публикации | — | PKG-S3-32 |
| BR-406 | Что делает — Настройки, акции, публикации | — | PKG-S3-32 |
| BR-408 | Что делает — Процессы — CSV-экспорт | — | PKG-S3-32 |
| BR-412 | Что делает — Настройки, акции, публикации | — | PKG-S3-32 |
| BR-413 | Что делает — Настройки, акции, публикации | — | PKG-S3-32 |
| BR-414 | Что делает — Плейлисты и пользователи сайта — detail | — | PKG-S3-32 |
| BR-415 | Что делает — Плейлисты и пользователи сайта — detail | — | PKG-S3-32 |
| BR-418 | Что делает — Онлайн-редактор ( | — | PKG-S3-32 |
| BR-419 | Что делает — Онлайн-редактор ( | — | PKG-S3-32 |
| BR-420 | Что делает — Плейлисты и пользователи сайта — detail | — | PKG-S3-32 |
| BR-421 | Что делает — Онлайн-редактор ( | — | PKG-S3-32 |
| BR-422 | Что делает — Онлайн-редактор ( | — | PKG-S3-32 |
| BR-423 | Что делает — Онлайн-редактор ( | — | PKG-S3-32 |
| BR-424 | Что делает — Плейлисты и пользователи сайта — detail | — | PKG-S3-32 |
| BR-425 | Что делает — Онлайн-редактор ( | — | PKG-S3-32 |
| BR-427 | Что делает — Онлайн-редактор ( | — | PKG-S3-32 |
| BR-428 | ) — правка во время сохранения не теряется и не | — | PKG-S3-32 |
| BR-429 | Что делает — Песни ( | — | PKG-S3-32 |
| BR-430 | ) — правка во время сохранения не теряется и не | — | PKG-S3-32 |
| BR-431 | ) — правка во время сохранения не теряется и не | — | PKG-S3-32 |
| BR-432 | Что делает — Песни ( | — | PKG-S3-32 |
| BR-433 | ) — правка во время сохранения не теряется и не | — | PKG-S3-32 |
| BR-434 | Что делает — Песни ( | — | PKG-S3-32 |
| BR-435 | Что делает — Песни ( | — | PKG-S3-32 |
| BR-436 | Что делает — Песни ( | — | PKG-S3-32 |
| BR-437 | Что делает — Песни ( | — | PKG-S3-32 |
| BR-438 | Что делает — Песни ( | — | PKG-S3-32 |
| BR-439 | Что делает — Песни ( | — | PKG-S3-32 |
| BR-440 | ) — «похожие песни» и обложка альбома различают | — | PKG-S3-32 |
| BR-441 | ) — «похожие песни» и обложка альбома различают | — | PKG-S3-32 |
| BR-442 | ) — «похожие песни» и обложка альбома различают | — | PKG-S3-32 |
| BR-443 | и карточки specs/336-code-audit/bug-report.md (записи …) | — | PKG-S3-32 |

### PKG-RACE

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-397 | > Source: OpenProject #367 — «[#336] S2: Гонка чтения/записи целевого объекта» | [system/frontend/vuex-patterns.md](../../system/frontend/vuex-patterns.md) | PKG-RACE |
| BR-407 | > Source: OpenProject #367 — «[#336] S2: Гонка чтения/записи целевого объекта» | [system/frontend/target-request-guard.md](../../system/frontend/target-request-guard.md) | PKG-RACE |
| BR-409 | > Source: OpenProject #367 — «[#336] S2: Гонка чтения/записи целевого объекта» | [system/frontend/target-request-guard.md](../../system/frontend/target-request-guard.md) | PKG-RACE |

### PKG-S3-33

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-444 | Фильтр песен — восстановление из «Истории» читает ключи по | — | PKG-S3-33 |
| BR-445 | Фильтр песен — восстановление из «Истории» читает ключи по | — | PKG-S3-33 |
| BR-446 | Что делает — Панели и таблицы — отказ | — | PKG-S3-33 |
| BR-447 | Что делает — Панели и таблицы — отказ | — | PKG-S3-33 |
| BR-448 | Что делает — Главная страница и шаблоны — каждая операция | — | PKG-S3-33 |
| BR-452 | Что делает — Редактор субтитров ( | — | PKG-S3-33 |
| BR-453 | Что делает — Редактор субтитров ( | — | PKG-S3-33 |
| BR-454 | Что делает — Редактор субтитров ( | — | PKG-S3-33 |
| BR-455 | Что делает — Редактор субтитров ( | — | PKG-S3-33 |
| BR-456 | Что делает — Редактор субтитров ( | — | PKG-S3-33 |
| BR-457 | Что делает — Редактор субтитров ( | — | PKG-S3-33 |
| BR-463 | Что делает — Панели и таблицы — отказ | — | PKG-S3-33 |
| BR-464 | Что делает — Панели и таблицы — отказ | — | PKG-S3-33 |
| BR-465 | Что делает — Панели и таблицы — отказ | [system/frontend/store-stats.md](../../system/frontend/store-stats.md) | PKG-S3-33 |
| BR-466 | Что делает — Панели и таблицы — отказ | — | PKG-S3-33 |
| BR-468 | Что делает — Редактор субтитров ( | — | PKG-S3-33 |
| BR-469 | Что делает — ) — удержание кнопок транспорта | — | PKG-S3-33 |
| BR-470 | Что делает — ) — удержание кнопок транспорта | — | PKG-S3-33 |
| BR-471 | Что делает — ) — удержание кнопок транспорта | — | PKG-S3-33 |
| BR-472 | Что делает — ) — удержание кнопок транспорта | — | PKG-S3-33 |
| BR-473 | Что делает — ) — удержание кнопок транспорта | — | PKG-S3-33 |
| BR-474 | Что делает — ) — удержание кнопок транспорта | — | PKG-S3-33 |
| BR-475 | Что делает — Главная страница и шаблоны — каждая операция | — | PKG-S3-33 |
| BR-476 | Что делает — Главная страница и шаблоны — каждая операция | — | PKG-S3-33 |
| BR-477 | Что делает — Главная страница и шаблоны — каждая операция | — | PKG-S3-33 |
| BR-478 | Что делает — Мини-редактор и плеер — индексы маркеров | — | PKG-S3-33 |
| BR-479 | Что делает — Мини-редактор и плеер — индексы маркеров | — | PKG-S3-33 |
| BR-480 | Что делает — Мини-редактор и плеер — индексы маркеров | — | PKG-S3-33 |
| BR-481 | Транспорт и запись настроек — запрос получает конечный | — | PKG-S3-33 |
| BR-482 | Транспорт и запись настроек — запрос получает конечный | — | PKG-S3-33 |
| BR-486 | Что делает — Мини-редактор и плеер — индексы маркеров | — | PKG-S3-33 |
| BR-488 | и карточки specs/336-code-audit/bug-report.md (записи …) | — | PKG-S3-33 |

### PKG-S2-18

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-449 | Аудит #336, пакет PKG-S2-18 (OpenProject #381, 13 записей | [system/frontend/subs-edit-admin-editor.md](../../system/frontend/subs-edit-admin-editor.md) | PKG-S2-18 |
| BR-450 | Аудит #336, пакет PKG-S2-18 (OpenProject #381, 13 записей | [system/frontend/subs-edit-admin-editor.md](../../system/frontend/subs-edit-admin-editor.md) | PKG-S2-18 |
| BR-451 | Аудит #336, пакет PKG-S2-18 (OpenProject #381, 13 записей | [system/frontend/subs-edit-admin-editor.md](../../system/frontend/subs-edit-admin-editor.md) | PKG-S2-18 |
| BR-458 | Известные ловушки — страницы 2+ окажутся пустыми | [system/frontend/store-monetization-stores.md](../../system/frontend/store-monetization-stores.md) | PKG-S2-18 |
| BR-459 | TTL нужно сбрасывать у всех затронутых вкладок | [system/frontend/store-stats.md](../../system/frontend/store-stats.md) | PKG-S2-18 |
| BR-460 | Зачем — ) | [system/frontend/store-stats.md](../../system/frontend/store-stats.md) | PKG-S2-18 |
| BR-461 | Зачем — ) | [system/frontend/store-monetization-stores.md](../../system/frontend/store-monetization-stores.md) | PKG-S2-18 |
| BR-462 | Известные ловушки — задания из хранилища | [system/frontend/store-stem-jobs.md](../../system/frontend/store-stem-jobs.md) | PKG-S2-18 |
| BR-467 | Известные ловушки — но данные не переписывает | [system/frontend/subs-edit-admin-editor.md](../../system/frontend/subs-edit-admin-editor.md) | PKG-S2-18 |
| BR-483 | анимация по стенным часам делает файл невоспроизводимым | [system/frontend/player-offline-mp4-render.md](../../system/frontend/player-offline-mp4-render.md) | PKG-S2-18 |
| BR-484 | Зачем — ) | [system/frontend/player-offline-mp4-render.md](../../system/frontend/player-offline-mp4-render.md) | PKG-S2-18 |
| BR-485 | Зачем — ) | [system/frontend/player-offline-mp4-render.md](../../system/frontend/player-offline-mp4-render.md) | PKG-S2-18 |
| BR-487 | TTL нужно сбрасывать у всех затронутых вкладок | [system/frontend/store-stats.md](../../system/frontend/store-stats.md) | PKG-S2-18 |

### PKG-S3-20

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-489 | alignment-ml/tests/testservelimits.py — 490 (синхронный обработчик, отзывчивость | — | PKG-S3-20 |
| BR-490 | HTTP-сервис (serve.py) — 415, превышение размера/длительности — 413 | — | PKG-S3-20 |
| BR-491 | (смоук → полный прогон, чекпоинты, битый словарь, --resume) | [domains/integration/components/alignment-ml.md](../../domains/integration/components/alignment-ml.md) | PKG-S3-20 |
| BR-492 | Status: active (493/494/495/496/497/498, аудит #336, пакет PKG-S3-20) | [domains/integration/components/alignment-ml.md](../../domains/integration/components/alignment-ml.md) | PKG-S3-20 |
| BR-493 | Тесты — alignment-ml/tests/testalignmodes.py (494) | [domains/integration/components/alignment-ml.md](../../domains/integration/components/alignment-ml.md) | PKG-S3-20 |
| BR-494 | HTTP-сервис (serve.py) — запрос тоже отклоняется | [domains/integration/components/alignment-ml.md](../../domains/integration/components/alignment-ml.md) | PKG-S3-20 |
| BR-495 | Тесты — alignment-ml/tests/testdatasetexclusion.py | [domains/integration/components/alignment-ml.md](../../domains/integration/components/alignment-ml.md) | PKG-S3-20 |
| BR-496 | Тесты — alignment-ml/tests/testchunkingboundaries.py | [domains/integration/components/alignment-ml.md](../../domains/integration/components/alignment-ml.md) | PKG-S3-20 |
| BR-497 | (ALIGNMANIFEST) и зовут ./.venv/bin/python train.py … | [domains/integration/components/alignment-ml.md](../../domains/integration/components/alignment-ml.md) | PKG-S3-20 |
| BR-498 | Тесты — alignment-ml/tests/testrequirementspin.py | [domains/integration/components/alignment-ml.md](../../domains/integration/components/alignment-ml.md) | PKG-S3-20 |

### PKG-S3-34

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-499 | Новый guard: нет явных версий Jackson, нет разнобоя версий, нет мёртвого блока dependencies в корне, нет pom.xml, дампы кучи — только в sanctioned-скрипте и с приватными прав… | [guidelines/architecture-conventions.md](../../guidelines/architecture-conventions.md) | PKG-S3-34 |
| BR-527 | Новый guard: нет явных версий Jackson, нет разнобоя версий, нет мёртвого блока dependencies в корне, нет pom.xml, дампы кучи — только в sanctioned-скрипте и с приватными прав… | [guidelines/architecture-conventions.md](../../guidelines/architecture-conventions.md) | PKG-S3-34 |
| BR-528 | Новый guard: нет явных версий Jackson, нет разнобоя версий, нет мёртвого блока dependencies в корне, нет pom.xml, дампы кучи — только в sanctioned-скрипте и с приватными прав… | [guidelines/architecture-conventions.md](../../guidelines/architecture-conventions.md) | PKG-S3-34 |
| BR-529 | Новый guard: нет явных версий Jackson, нет разнобоя версий, нет мёртвого блока dependencies в корне, нет pom.xml, дампы кучи — только в sanctioned-скрипте и с приватными прав… | [guidelines/architecture-conventions.md](../../guidelines/architecture-conventions.md) | PKG-S3-34 |
| BR-530 | Новый guard: нет явных версий Jackson, нет разнобоя версий, нет мёртвого блока dependencies в корне, нет pom.xml, дампы кучи — только в sanctioned-скрипте и с приватными прав… | [guidelines/architecture-conventions.md](../../guidelines/architecture-conventions.md) | PKG-S3-34 |

### PKG-S2-09

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-558 | # Feature: Заголовки безопасности публичного сайта (аудит #336, OpenProject #372) | [product/features/security-headers.md](../../product/features/security-headers.md) | PKG-S2-09 |

### PKG-S3-30

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-573 | «нет данных» ≠ «нет нарушений»: отсутствующий component/controller/method или компонент без v-model полей — ошибка с ненулевым кодом, а не continue. Источник поддерживаемых п… | — | PKG-S3-30 |
| BR-575 | «нет данных» ≠ «нет нарушений»: отсутствующий component/controller/method или компонент без v-model полей — ошибка с ненулевым кодом, а не continue. Источник поддерживаемых п… | — | PKG-S3-30 |
| BR-576 | ветка самодиагностики «матчер ослеп» была недостижима (точный матч nginx/node исключал вердикт n/a). Семейство теперь определяется по исходному ref нечётко (nginx/node), обра… | — | PKG-S3-30 |
| BR-577 | «нет данных» ≠ «нет нарушений»: отсутствующий component/controller/method или компонент без v-model полей — ошибка с ненулевым кодом, а не continue. Источник поддерживаемых п… | — | PKG-S3-30 |
| BR-579 | код возврата eslint сохраняется (ESLINTEXIT), stderr пишется в лог и показывается при падении; «eslint упал» (exit ≥ 2 / пустой / невалидный JSON) даёт код 2, а не «новых нар… | — | PKG-S3-30 |
| BR-580 | контекст «есть ли cd webvue3 перед npm run» берётся из полного содержимого файла, номера строк — из diff'а. Раньше cd webvue3 из контекста diff'а был не виден и guard ложно б… | — | PKG-S3-30 |
| BR-582 | код возврата eslint сохраняется (ESLINTEXIT), stderr пишется в лог и показывается при падении; «eslint упал» (exit ≥ 2 / пустой / невалидный JSON) даёт код 2, а не «новых нар… | — | PKG-S3-30 |
| BR-583 | то же для DIRTOSLUG/CLASSSLUGOVERRIDE; существующий KDoc ищется по закрывающему / с подъёмом до / (раньше — фиксированное окно в 8 строк, из-за чего длинный KDoc дублировался… | — | PKG-S3-30 |
| BR-585 | то же для DIRTOSLUG/CLASSSLUGOVERRIDE; существующий KDoc ищется по закрывающему / с подъёмом до / (раньше — фиксированное окно в 8 строк, из-за чего длинный KDoc дублировался… | — | PKG-S3-30 |
| BR-586 | подсчёт переведён на переносимый grep -cE (без PCRE -P); счётчики проверяются на «число», иначе — ошибка с кодом 2; проверка, которая не могла быть выполнена (нет CONTRIBUTIN… | — | PKG-S3-30 |
| BR-596 | Issue ID извлекается в обоих форматах, которые guard принимает (Issue ID: NNN и ### Issue ID): раньше спека с заголовком проходила проверку поля, но Workflow-команды не прове… | — | PKG-S3-30 |
| BR-597 | Issue ID извлекается в обоих форматах, которые guard принимает (Issue ID: NNN и ### Issue ID): раньше спека с заголовком проходила проверку поля, но Workflow-команды не прове… | — | PKG-S3-30 |
| BR-598 | код возврата git rev-parse/git log проверяется, базовая ветка разрешается явно (master → origin/master → main → origin/main); невозможность проверить даёт код 2, а не «всё хо… | — | PKG-S3-30 |
| BR-600 | код возврата docker compose up -d сохраняется: падение compose больше не превращается в сообщение об успешном запуске контейнеров | — | PKG-S3-30 |
| BR-603 | значение по умолчанию для --assignee вычисляется после trackerloadenv: раньше .env.local-tracker ещё не читался и опрашивался чужой пользователь | — | PKG-S3-30 |
| BR-604 | echo "000" дописывал ещё 000 → 000000` не совпадало ни с одной веткой). Ошибка записи аудит-лога больше не проглатывается — предупреждение в stderr (один раз за сессию) | — | PKG-S3-30 |
| BR-606 | падение rails runner больше не убивает скрипт до печати диагностики (код возврата docker exec проверяется, вывод сохраняется целиком). ID статусов резолвятся по имени (Status… | — | PKG-S3-30 |
| BR-607 | падение rails runner больше не убивает скрипт до печати диагностики (код возврата docker exec проверяется, вывод сохраняется целиком). ID статусов резолвятся по имени (Status… | — | PKG-S3-30 |
| BR-608 | падение rails runner больше не убивает скрипт до печати диагностики (код возврата docker exec проверяется, вывод сохраняется целиком). ID статусов резолвятся по имени (Status… | — | PKG-S3-30 |
| BR-609 | падение rails runner больше не убивает скрипт до печати диагностики (код возврата docker exec проверяется, вывод сохраняется целиком). ID статусов резолвятся по имени (Status… | — | PKG-S3-30 |
| BR-610 | регулярка Issue ID больше не срабатывает на «опубликация 5», «operation 7», «drop 9»: «оп»/«op» — только отдельное слово с явным разделителем. Добавлен --self-test (14 + 8 фи… | — | PKG-S3-30 |
| BR-612 | сбой reserve-branch-number.sh больше не проглатывается (stderr показывается, код возврата проверяется), FEATURENUM обязан быть числом — иначе ветка не создаётся | — | PKG-S3-30 |
| BR-613 | значение по умолчанию NEXTNUM достижимо: пайп не роняет скрипт под set -euo pipefail, результат валидируется как число | — | PKG-S3-30 |
| BR-614 | echo "000" дописывал ещё 000 → 000000` не совпадало ни с одной веткой). Ошибка записи аудит-лога больше не проглатывается — предупреждение в stderr (один раз за сессию) | — | PKG-S3-30 |

### OpenProject #410

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-627 | (отдельная задача #410): утечка соединений. Правки этого | [domains/persistence/domain.md](../../domains/persistence/domain.md) | OpenProject #410 |

### Спека 676 / KARAOKE-245 (BR-630)

| Код | Суть дефекта | Зеркало | Пакет |
|---|---|---|---|
| BR-630 | Превентивные меры против OOM в контейнере `karaoke-app` (admin-машина): не было предела памяти контейнера (`mem_limit`), значение heap жило только в неотслеживаемом `deploy/.env` (в git его нет), не было дампа состояния heap при OOM и политики хранения дампов. Теперь: `mem_limit: 8g` + `-Xmx6g` (после `${APP_JAVA_OPTS}` — последний `-Xmx` побеждает), `-XX:+HeapDumpOnOutOfMemoryError` + `-XX:HeapDumpPath=/dumps`, том `./karaoke-db/dumps`, retention-политика (последние 2 / ≤ 20 ГБ / по возрасту), 8-я проверка мониторинга `HeapUsageCheck` (пороги 75 % / 90 %) | [system/infra/deploy-overview.md](../../system/infra/deploy-overview.md) | Спека 676 / KARAOKE-245 |

### Спека 677 / KARAOKE-337 (BR-631)

Аудит #336 не относится к этому коду: он фиксирует пробел, найденный при разборе
инцидента 2026-10-30 (смена внешнего IP админ-машины → потеря доступа к
прод-каналу `5433`/`9000` → простой конвейера и ложный CRITICAL «Прод недоступен»).

| BR | Дефект | Зеркало | Источник |
|---|---|---|---|
| BR-631 | Доступ админ-машины к прод-каналу `5433`/`9000` ограничен allow-list в `/etc/nginx/nginx.conf` host-nginx, а адрес админ-машины меняется (пул провайдера плавает ежемесячно; при включённом VPN прод видит адрес VPS `87.58.202.244`, которого в allow-list не было). Механизма доставки `nginx.conf` из репозитория на прод нет, автоматизации правки тоже не было: каждый разворот адреса означал ручную правку на проде ночью. Теперь: `deploy/admin-ip-allowlist.sh` под таймером (10 минут) сам определяет текущий внешний адрес (публичный HTTPS-сервис + резерв), валидирует его и добавляет одну строку `allow <ip>;` в оба stream-блока — с бэкапом, двойной проверкой синтаксиса, контрольной проверкой и откатом; ограничение частоты (одна правка за 30 минут), журнал + Telegram-уведомления, PR через Forgejo API (сливает владелец) | [system/infra/deploy-overview.md](../../system/infra/deploy-overview.md) | Спека 677 / KARAOKE-337 |

## 4. Карточки пакетов

### PKG-S2

- **Волна**: S2
- **Кодов в пакете**: 12
- **Состав**: BR-001, BR-002, BR-004, BR-005, BR-012, BR-013, BR-015, BR-023 … (полный список — в § 3)
- **Где описано**: `product/features/public-api-exposure.md`, `domains/karaoke-web/components/public-controllers-2.md`, `domains/karaoke-web/components/services-overview.md`
- **Что закрывал**: см. § 3 — строки кодов пакета


### OpenProject #384

- **Волна**: —
- **Кодов в пакете**: 2
- **Состав**: BR-003, BR-011 (полный список — в § 3)
- **Где описано**: `product/features/client-ip-resolution.md`
- **Что закрывал**: см. § 3 — строки кодов пакета


### PKG-S3-29

- **Волна**: S3
- **Кодов в пакете**: 17
- **Состав**: BR-007, BR-008, BR-009, BR-010, BR-017, BR-024, BR-025, BR-026 … (полный список — в § 3)
- **Где описано**: `system/frontend/karaoke-web-error-contract.md`
- **Что закрывал**: Пакет закрывает 17 записей аудита #336 в модуле `karaoke-web` — приложении, которое отдаёт публичный сайт и его API. Все записи — одного класса: **приложение не различало «не знаю» и «знаю, что плохо»**:


### PKG-MONEY

- **Волна**: S2
- **Кодов в пакете**: 4
- **Состав**: BR-014, BR-020, BR-021, BR-514 (полный список — в § 3)
- **Где описано**: `domains/karaoke-web/components/services-overview.md`
- **Что закрывал**: см. § 3 — строки кодов пакета


### PKG-ERR-STATS-ZERO

- **Волна**: ERR
- **Кодов в пакете**: 2
- **Состав**: BR-016, BR-185 (полный список — в § 3)
- **Где описано**: `domains/publishing/components/stats-cache.md`, `domains/stats/domain.md`
- **Что закрывал**: Закрывает две записи аудита #336 класса ERR, у которых один симптом: сбой БД превращается в `0` и неотличим от настоящего нуля.


### OpenProject #358

- **Волна**: —
- **Кодов в пакете**: 1
- **Состав**: BR-018 (полный список — в § 3)
- **Где описано**: `product/features/close-gesture-token-paywall.md`
- **Что закрывал**: см. § 3 — строки кодов пакета


### OpenProject #359

- **Волна**: —
- **Кодов в пакете**: 2
- **Состав**: BR-019, BR-518 (полный список — в § 3)
- **Где описано**: `product/features/public-settings-perimeter.md`
- **Что закрывал**: см. § 3 — строки кодов пакета


### —

- **Волна**: —
- **Кодов в пакете**: 7
- **Состав**: BR-022, BR-533, BR-541, BR-552, BR-553, BR-601, BR-605 (полный список — в § 3)
- **Где описано**: `product/features/song-air-access.md`, `system/infra/tracker.md`, `domains/catalog/components/dictionaries.md`
- **Что закрывал**: см. § 3 — строки кодов пакета


### PKG-SEC-SQLI

- **Волна**: S2
- **Кодов в пакете**: 4
- **Состав**: BR-028, BR-100, BR-175, BR-237 (полный список — в § 3)
- **Где описано**: `domains/persistence/domain.md`, `domains/catalog/components/pictures.md`, `domains/catalog/components/album-entity.md`
- **Что закрывал**: см. § 3 — строки кодов пакета


### PKG-ERR-EXITCODE

- **Волна**: ERR
- **Кодов в пакете**: 2
- **Состав**: BR-029, BR-161 (полный список — в § 3)
- **Где описано**: `domains/processing/components/async-process-queue.md`, `domains/processing/components/playwright-rendering.md`
- **Что закрывал**: см. § 3 — строки кодов пакета


### PKG-S2-10

- **Волна**: S2
- **Кодов в пакете**: 14
- **Состав**: BR-030, BR-031, BR-032, BR-033, BR-048, BR-049, BR-050, BR-063 … (полный список — в § 3)
- **Где описано**: `domains/processing/components/async-process-queue.md`, `domains/catalog/components/dictionaries.md`
- **Что закрывал**: Пакет закрывает 15 записей аудита #336 в `karaoke-app`: гонки очереди заданий (BR-030, BR-031), неатомарную цепочку шагов (BR-032), падение воркера на пустых `process_args` (BR-033), инъекцию полей в `copyfieldsfromanother` (BR-048), SSRF через прокси обложек (BR-049), подмену `rootFolder` через `POST /api/song/update` (BR-050), перезапись чужих изменений после сетевого вызова публикации (BR-063), sleep на каждой итерации массового поиска текста (BR-064), NPE на неизвестном аккорде (BR-071), «успех» при неудавшемся сохранении песни (BR-101, BR-102), N+1 по альбомам в `toDTO()` (BR-104), неполный список колонок `INSERT` (BR-108) и path traversal в SmartCopy (BR-109).


### PKG-S3-22

- **Волна**: S3
- **Кодов в пакете**: 34
- **Состав**: BR-034, BR-035, BR-036, BR-037, BR-051, BR-052, BR-053, BR-054 … (полный список — в § 3)
- **Где описано**: `domains/catalog/components/song-entity.md`, `domains/processing/components/async-process-queue.md`, `domains/catalog/components/dictionaries.md`
- **Что закрывал**: Закрывает 40 дефектов S3 в `karaoke-app`, найденных аудитом #336 (порции P-04, P-08, P-09, P-18): очередь заданий и `mediainfo` (потеря причины `ERROR`, soft-deleted задания как «вечно активные», `filter_limit`, валящий запрос в 500, сбой восстановления при старте), `ApiController` (непроверенные касты, проглоченные `SQLException`, ZIP в heap, отсутствие таймаута у `ffmpeg`, подмена пути на `/`, контракт 404) и `Song.kt` (геттеры, которые пишут в БД, unchecked-индексы и касты, XSS в HTML аккордов, нормализация устаревшего снимка, удаление файлов до строки БД, атомарность флагов готовности, типизация разбора Sheetsage, счётчик голосов по заглушке, проглатывание ошибок `INSERT`).


### PKG-S2-08

- **Волна**: S2
- **Кодов в пакете**: 31
- **Состав**: BR-039, BR-040, BR-041, BR-042, BR-043, BR-044, BR-045, BR-046 … (полный список — в § 3)
- **Где описано**: `system/infra/sql-migrations.md`, `product/features/security-headers.md`, `domains/persistence/domain.md`
- **Что закрывал**: Закрывает 15 S2-находок аудита в deploy-контуре: миграции, секреты, платёжная история, push-ошибки, бэкапы, сетевая экспозиция, TLS, ротация логов, compose-окружение и исполнение скриптов на хосте. Поведение прода по умолчанию не меняется — все переключатели (TLS, порты, пароли) получили те же значения, но заданы явно и проверяются guard’ом.


### PKG-RACE-PUBLISH

- **Волна**: S2
- **Кодов в пакете**: 2
- **Состав**: BR-062, BR-138 (полный список — в § 3)
- **Где описано**: `domains/publishing/components/publishing-services.md`
- **Что закрывал**: см. § 3 — строки кодов пакета


### PKG-S2-17

- **Волна**: S2
- **Кодов в пакете**: 15
- **Состав**: BR-087, BR-088, BR-093, BR-124, BR-125, BR-370, BR-379, BR-380 … (полный список — в § 3)
- **Где описано**: `system/frontend/vuex-patterns.md`, `system/frontend/target-request-guard.md`, `domains/catalog/components/dictionaries.md`
- **Что закрывал**: см. § 3 — строки кодов пакета


### PKG-S3-31

- **Волна**: S3
- **Кодов в пакете**: 39
- **Состав**: BR-089, BR-090, BR-091, BR-092, BR-094, BR-095, BR-096, BR-097 … (полный список — в § 3)
- **Где описано**: — (содержимое не перенесено — см. § 5)
- **Что закрывал**: Пакет закрывает 40 записей аудита #336 в модуле `webvue3` — админском SPA. Записи семи классов: **гонки ответов, неразличимые состояния «пусто / ошибка», ложные «успехи», тихие отказы, потерянные фильтры, незакрытые ресурсы и нарушения контракта Vuex**. Правки сгруппированы по восьми блокам:


### PKG-S2-11

- **Волна**: S2
- **Кодов в пакете**: 15
- **Состав**: BR-110, BR-111, BR-112, BR-113, BR-128, BR-132, BR-133, BR-139 … (полный список — в § 3)
- **Где описано**: `domains/storage/domain.md`, `domains/publishing/components/publishing-services.md`, `system/utilities.md`
- **Что закрывал**: см. § 3 — строки кодов пакета


### PKG-S3-23

- **Волна**: S3
- **Кодов в пакете**: 32
- **Состав**: BR-117, BR-118, BR-119, BR-120, BR-121, BR-122, BR-129, BR-130 … (полный список — в § 3)
- **Где описано**: `domains/monitoring/components/log-categories.md`, `domains/monitoring/components/error-semantics.md`, `domains/processing/domain.md`
- **Что закрывал**: Закрывает 39 записей аудита #336 в `karaoke-app` (и двух местах в `karaoke-web`). Все они — варианты одного корня: **штатный результат и отказ имели одинаковое представление**, поэтому админ, бот или health-report принимали «сбой» за «данных нет» (или наоборот — «успех» за «сделано»).


### OpenProject #360

- **Волна**: —
- **Кодов в пакете**: 1
- **Состав**: BR-123 (полный список — в § 3)
- **Где описано**: `product/features/escape-confirm-user-data.md`
- **Что закрывал**: см. § 3 — строки кодов пакета


### PKG-S3-35

- **Волна**: S3
- **Кодов в пакете**: 16
- **Состав**: BR-154, BR-207, BR-216, BR-217, BR-218, BR-220, BR-221, BR-222 … (полный список — в § 3)
- **Где описано**: `domains/editorial/components/assignment-lifecycle.md`, `domains/identity/components/dictionaries.md`, `domains/integration/components/external-api-clients.md`
- **Что закрывал**: Пакет закрывает 15 записей аудита #336, которые НЕ достались ни одному пакету волны E (или остались не закрыты в своём). Три блока:


### PKG-S3-24

- **Волна**: S3
- **Кодов в пакете**: 7
- **Состав**: BR-174, BR-182, BR-187, BR-189, BR-199, BR-202, BR-215 (полный список — в § 3)
- **Где описано**: `domains/catalog/components/author-entity.md`, `domains/persistence/domain.md`, `domains/catalog/domain.md`
- **Что закрывал**: Пакет закрывает 40 записей аудита #336 в модуле `karaoke-app` (модель, сервисы, шаблоны Thymeleaf). Правки сгруппированы по трём партиям:


### PKG-S2-12

- **Волна**: S2
- **Кодов в пакете**: 15
- **Состав**: BR-188, BR-195, BR-200, BR-201, BR-205, BR-206, BR-213, BR-214 … (полный список — в § 3)
- **Где описано**: `domains/catalog/components/remaining-models.md`, `domains/stats/domain.md`, `domains/monetization/domain.md`
- **Что закрывал**: Закрывает 15 записей аудита: BR-188, BR-195, BR-200, BR-201, BR-205, BR-206, BR-213, BR-214, BR-219, BR-227, BR-234, BR-235, BR-244, BR-251, BR-266. Меняет поведение legacy-страниц `karaoke-app` (`/songs`, `/songs2`, `/publications`, `/processes`, `/unpublications`), кеша статистики, sync-контура LOCAL↔SERVER, записи файла настроек, выбора автора для поиска альбома и LLM-извлечения текста песни.


### PKG-S3-25

- **Волна**: S3
- **Кодов в пакете**: 40
- **Состав**: BR-228, BR-229, BR-230, BR-231, BR-232, BR-233, BR-236, BR-238 … (полный список — в § 3)
- **Где описано**: `domains/monitoring/components/boundary-validation.md`, `domains/monitoring/components/log-categories.md`, `domains/monitoring/domain.md`
- **Что закрывал**: Закрывает 40 записей аудита #336 в `karaoke-app` (и одной в `karaoke-web`). Общий корень у них один: **непроверенный вход доходил до артефакта или до ответа** — и ломал не одну запись, а всё целиком (генерацию `.mlt`, страницу админки, публичный API).


### OpenProject #361

- **Волна**: —
- **Кодов в пакете**: 1
- **Состав**: BR-258 (полный список — в § 3)
- **Где описано**: `domains/storage/components/storage-flow.md`
- **Что закрывал**: см. § 3 — строки кодов пакета


### PKG-S3-26

- **Волна**: S3
- **Кодов в пакете**: 15
- **Состав**: BR-276, BR-277, BR-278, BR-279, BR-280, BR-281, BR-282, BR-285 … (полный список — в § 3)
- **Где описано**: `domains/health/components/health-report.md`, `domains/catalog/components/song-entity.md`, `guidelines/architecture-conventions.md`
- **Что закрывал**: Пакет закрывает 19 записей аудита #336 в модуле `karaoke-app` (health-отчёты, настройки рендера, крипта двух-БД sync, поиск обложек, гигиена репозитория). Правки сгруппированы по трём блокам:


### PKG-S2-14

- **Волна**: S2
- **Кодов в пакете**: 14
- **Состав**: BR-295, BR-302, BR-303, BR-312, BR-313, BR-319, BR-327, BR-328 … (полный список — в § 3)
- **Где описано**: `product/features/escape-confirm-user-data.md`, `domains/README.md`
- **Что закрывал**: Закрывает 13 дефектов S2 публичного SPA `karaoke-public`, найденных аудитом #336 (порции P-42…P-49): отозванная «только в UI» share-ссылка, гонка двух загрузок трека в плеере, терминальный release share-сессии на переключении вкладки, stored XSS через метку комментария редактора, утечка листенеров и WaveSurfer, гейт чата по устаревшему кэшу, потеря автосохранения при уходе со страницы, END-маркер в нуле при незагруженном аудио, «залипшие» флаги `saveState`/`submitting`/`loading` при сетевом сбое, отсутствие таймаута XHR, «капча готова» без `window.smartCaptcha` и ложное «подписка не найдена» после реальной оплаты.


### PKG-S3-27

- **Волна**: S3
- **Кодов в пакете**: 22
- **Состав**: BR-296, BR-297, BR-298, BR-299, BR-300, BR-301, BR-304, BR-311 … (полный список — в § 3)
- **Где описано**: `system/frontend/karaoke-public-tests.md`, `system/frontend/webvue3-audit-invariants.md`, `system/frontend/composable-use-cart.md`
- **Что закрывал**: Пакет закрывает 40 записей аудита #336 в модуле `karaoke-public` — публичном SPA сайта. Все записи — одного класса: **дефекты обращения с состоянием**, а не «логика неверна». Правки сгруппированы по шести блокам:


### PKG-S3-28

- **Волна**: S3
- **Кодов в пакете**: 15
- **Состав**: BR-347, BR-348, BR-349, BR-351, BR-352, BR-353, BR-354, BR-355 … (полный список — в § 3)
- **Где описано**: — (содержимое не перенесено — см. § 5)
- **Что закрывал**: Пакет закрывает 15 записей аудита #336 в модуле `karaoke-public` — публичном SPA сайта. Записи разных классов, но одного свойства: **публичная страница врала пользователю** — показывала «ссылка мертва» при сбое сервера, «автор не найден» вместо объяснения, пустое предупреждение правообладателя, «успешную» отмену подписки, которая не выполнилась.


### PKG-S3-32

- **Волна**: S3
- **Кодов в пакете**: 40
- **Состав**: BR-395, BR-396, BR-398, BR-400, BR-401, BR-402, BR-403, BR-404 … (полный список — в § 3)
- **Где описано**: — (содержимое не перенесено — см. § 5)
- **Что закрывал**: Пакет закрывает 40 записей аудита #336 в модуле `webvue3` — админском SPA. Записи восьми классов: **гонки ответов (RACE), неразличимые состояния «пусто / ошибка» (ERR), ложные «успехи» (COMPAT), потерянные данные (DATA), тихие отказы (RULE), незакрытые ресурсы (LEAK), лишняя работа на каждый ввод (PERF) и CSV-формулы (SEC)**. Правки сгруппированы по шести блокам:


### PKG-RACE

- **Волна**: S2
- **Кодов в пакете**: 3
- **Состав**: BR-397, BR-407, BR-409 (полный список — в § 3)
- **Где описано**: `system/frontend/target-request-guard.md`, `system/frontend/vuex-patterns.md`
- **Что закрывал**: см. § 3 — строки кодов пакета


### PKG-S3-33

- **Волна**: S3
- **Кодов в пакете**: 32
- **Состав**: BR-444, BR-445, BR-446, BR-447, BR-448, BR-452, BR-453, BR-454 … (полный список — в § 3)
- **Где описано**: `system/frontend/store-stats.md`
- **Что закрывал**: Пакет закрывает 32 записи аудита #336 в модуле `webvue3` — админском SPA. Записи восьми классов: **запросы без конечного срока (ERR), гонки записи состояния (RACE), ложные «успехи» в главном окне (ERR), потерянные поля при копировании маркера (DATA), неразличимые состояния «пусто / ошибка» (ERR), незакрытые ресурсы (LEAK), лишняя работа на каждый кадр и на каждое нажатие (PERF), разрыв контракта API (COMPAT) и отсутствие dev-прокси (BUILD)**. Правки сгруппированы по семи блокам:


### PKG-S2-18

- **Волна**: S2
- **Кодов в пакете**: 13
- **Состав**: BR-449, BR-450, BR-451, BR-458, BR-459, BR-460, BR-461, BR-462 … (полный список — в § 3)
- **Где описано**: `system/frontend/subs-edit-admin-editor.md`, `system/frontend/store-stats.md`, `system/frontend/player-offline-mp4-render.md`
- **Что закрывал**: см. § 3 — строки кодов пакета


### PKG-S3-20

- **Волна**: S3
- **Кодов в пакете**: 10
- **Состав**: BR-489, BR-490, BR-491, BR-492, BR-493, BR-494, BR-495, BR-496 … (полный список — в § 3)
- **Где описано**: `domains/integration/components/alignment-ml.md`
- **Что закрывал**: см. § 3 — строки кодов пакета


### PKG-S3-34

- **Волна**: S3
- **Кодов в пакете**: 5
- **Состав**: BR-499, BR-527, BR-528, BR-529, BR-530 (полный список — в § 3)
- **Где описано**: `guidelines/architecture-conventions.md`
- **Что закрывал**: Пакет закрывает 5 записей аудита #336 в модуле «прочее» (сборка и безопасность репозитория). Общий признак дефектов: **конфигурация сборки в репозитории расходилась сама с собой и ничего не проверяла по целостности** — три разных gradle-wrapper'а без контрольных сумм, явный пин Jackson поверх BOM'а, дамп кучи в общем `/tmp` и две мёртвые копии build-конфига.


### PKG-S2-09

- **Волна**: S2
- **Кодов в пакете**: 1
- **Состав**: BR-558 (полный список — в § 3)
- **Где описано**: `product/features/security-headers.md`
- **Что закрывал**: см. § 3 — строки кодов пакета


### PKG-S3-30

- **Волна**: S3
- **Кодов в пакете**: 24
- **Состав**: BR-573, BR-575, BR-576, BR-577, BR-579, BR-580, BR-582, BR-583 … (полный список — в § 3)
- **Где описано**: — (содержимое не перенесено — см. § 5)
- **Что закрывал**: Пакет закрывает 24 записи аудита #336 в модуле `tools/` — в самих guard'ах, которыми проверяется всё остальное. Общий признак дефектов: **guard объявлял успех там, где проверка не была выполнена**, — пустой вход, потерянный код возврата, недостижимая ветка, неверный источник данных, умноженный счётчик.


### OpenProject #410

- **Волна**: —
- **Кодов в пакете**: 1
- **Состав**: BR-627 (полный список — в § 3)
- **Где описано**: `domains/persistence/domain.md`
- **Что закрывал**: см. § 3 — строки кодов пакета


## 5. Коды без зеркала

Коды, которые после слияния **не имеют** зеркала в `knowledge/`: их правка не вынесена
в компоненты домена, поэтому содержание остаётся носителем только здесь — строки § 3
самодостаточны (называют, что сломано). Список считается на итоговом дереве после
переноса (до миграции таких кодов было 267).


- **PKG-S3-29**: BR-007, BR-008, BR-009, BR-010, BR-017, BR-024, BR-025, BR-026, BR-501, BR-502, BR-503, BR-505, BR-506, BR-519, BR-520, BR-521

- **PKG-MONEY**: BR-014, BR-020, BR-021

- **PKG-S3-22**: BR-036, BR-037, BR-051, BR-052, BR-054, BR-058, BR-065, BR-066, BR-067, BR-068, BR-069, BR-070, BR-076, BR-082, BR-085, BR-086, BR-107

- **PKG-S2-08**: BR-040, BR-042, BR-043, BR-045, BR-046, BR-047, BR-535, BR-536, BR-538, BR-542, BR-543, BR-547, BR-548, BR-550, BR-554, BR-555, BR-556, BR-557, BR-559, BR-560

- **PKG-S2-10**: BR-048, BR-049, BR-050, BR-063, BR-064, BR-071, BR-109

- **PKG-S3-31**: BR-089, BR-090, BR-091, BR-092, BR-094, BR-095, BR-096, BR-097, BR-098, BR-099, BR-126, BR-127, BR-363, BR-364, BR-365, BR-366, BR-367, BR-368, BR-369, BR-372, BR-373, BR-374, BR-375, BR-376, BR-377, BR-378, BR-381, BR-382, BR-383, BR-384, BR-385, BR-386, BR-387, BR-389, BR-390, BR-391, BR-392, BR-393, BR-394

- **PKG-S3-23**: BR-120, BR-172

- **PKG-S2-11**: BR-143, BR-176

- **PKG-S3-24**: BR-174, BR-199, BR-202, BR-215

- **PKG-S2-12**: BR-201, BR-205, BR-206, BR-213, BR-214, BR-234, BR-235

- **PKG-S3-35**: BR-207, BR-217, BR-218, BR-225, BR-507, BR-509, BR-510, BR-517

- **PKG-S3-25**: BR-233, BR-246, BR-248, BR-263, BR-267, BR-268, BR-269, BR-272

- **PKG-S3-26**: BR-276

- **PKG-S2-14**: BR-295, BR-302, BR-303, BR-313, BR-319, BR-327, BR-328, BR-329, BR-330, BR-339, BR-340, BR-350

- **PKG-S3-27**: BR-324

- **PKG-S3-28**: BR-347, BR-348, BR-349, BR-351, BR-352, BR-353, BR-354, BR-355, BR-356, BR-357, BR-358, BR-359, BR-360, BR-361, BR-362

- **PKG-S2-17**: BR-370, BR-399, BR-417

- **PKG-S3-32**: BR-395, BR-396, BR-398, BR-400, BR-401, BR-402, BR-403, BR-404, BR-405, BR-406, BR-408, BR-412, BR-413, BR-414, BR-415, BR-418, BR-419, BR-420, BR-421, BR-422, BR-423, BR-424, BR-425, BR-427, BR-428, BR-429, BR-430, BR-431, BR-432, BR-433, BR-434, BR-435, BR-436, BR-437, BR-438, BR-439, BR-440, BR-441, BR-442, BR-443

- **PKG-S3-33**: BR-444, BR-445, BR-446, BR-447, BR-448, BR-452, BR-453, BR-454, BR-455, BR-456, BR-457, BR-463, BR-464, BR-466, BR-468, BR-469, BR-470, BR-471, BR-472, BR-473, BR-474, BR-475, BR-476, BR-477, BR-478, BR-479, BR-480, BR-481, BR-482, BR-486, BR-488

- **PKG-S3-20**: BR-489, BR-490

- **PKG-S3-30**: BR-573, BR-575, BR-576, BR-577, BR-579, BR-580, BR-582, BR-583, BR-585, BR-586, BR-596, BR-597, BR-598, BR-600, BR-603, BR-604, BR-606, BR-607, BR-608, BR-609, BR-610, BR-612, BR-613, BR-614

## 6. Коды аудита, встречающиеся только в `knowledge/`

Эти коды были найдены аудитом #336 (и связанными аудитами), но **никогда не
были в слое `docs/features/`** — они жили и живут только в компонентах домена,
ADR и guidelines. Реестр § 3 собирался из слоя `docs/features/`, поэтому они в
него не попали; FR-015 требует, чтобы код, упомянутый в `knowledge/`, был
зарегистрирован. Зеркало у всех — они встречаются в `knowledge/`.

| Код | Зеркало | Суть (по первому упоминанию) | Упоминаний |
|---|---|---|---|
| BR-006 | [adr/local-0009-thread-local-connection-lifecycle.md](../../adr/local-0009-thread-local-connection-lifecycle.md) | &#124; BR-006 &#124; karaoke-web/.../EventsBuffer.kt &#124; connection.close() в finally (поток Tomcat / @Scheduled) &#124; закрытия нет — кеш потока сохранён &#124; | 5 |
| BR-027 | [adr/local-0009-thread-local-connection-lifecycle.md](../../adr/local-0009-thread-local-connection-lifecycle.md) | &#124; BR-027 &#124; karaoke-web/.../PublicSettingsWebController.kt &#124; connection.close() в finally (поток Tomcat) &#124; закрытия нет — кеш потока сохранён &#124; | 3 |
| BR-038 | [adr/local-0009-thread-local-connection-lifecycle.md](../../adr/local-0009-thread-local-connection-lifecycle.md) | &#124; BR-038 &#124; karaoke-app/.../KaraokeProcessWorker.kt &#124; поток очереди doStart не закрывал соединение &#124; startOneShotThread → закрытие в finally &#124; | 4 |
| BR-060 | [adr/local-0009-thread-local-connection-lifecycle.md](../../adr/local-0009-thread-local-connection-lifecycle.md) | &#124; BR-060 &#124; karaoke-app/.../ApiController.kt (+ SyncAudioDescendants, Utils) &#124; 12 сырых thread { … } без закрытия &#124; startOneShotThread → закрытие в fin | 3 |
| BR-115 | [domains/catalog/components/pictures.md](../../domains/catalog/components/pictures.md) | 644, каталог публикации — 755 (BR-115, OpenProject #407). Раньше было | 2 |
| BR-116 | [adr/local-0009-thread-local-connection-lifecycle.md](../../adr/local-0009-thread-local-connection-lifecycle.md) | &#124; BR-116 &#124; karaoke-app/.../ChatController.kt &#124; db.getConnection()?.close() в finally (поток Tomcat) &#124; закрытия нет — кеш потока сохранён &#124; | 3 |
| BR-150 | [domains/integration/domain.md](../../domains/integration/domain.md) | под локом, — иначе два параллельных вызова публиковали дубли (BR-150) | 1 |
| BR-151 | [domains/integration/domain.md](../../domains/integration/domain.md) | runCatching (BR-151, BR-152) | 1 |
| BR-152 | [domains/monitoring/components/error-semantics.md](../../domains/monitoring/components/error-semantics.md) | runCatching (BR-151, BR-152) | 3 |
| BR-153 | [domains/monitoring/components/error-semantics.md](../../domains/monitoring/components/error-semantics.md) | VkNetworkException — так же, как в прямой ветке (BR-153) | 3 |
| BR-155 | [domains/monitoring/components/error-semantics.md](../../domains/monitoring/components/error-semantics.md) | &#124; BR-149…BR-153, BR-155…BR-160 &#124; PkgS323Batch2SourceContractsTest, PkgS323Batch3SourceContractsTest &#124; | 1 |
| BR-170 | [domains/health/domain.md](../../domains/health/domain.md) | (FR-012) (BR-170) | 3 |
| BR-179 | [domains/catalog/domain.md](../../domains/catalog/domain.md) | не выглядит как NPE. BR-179 | 1 |
| BR-181 | [domains/catalog/domain.md](../../domains/catalog/domain.md) | имени — иначе у проигравшей песни не появлялась обложка. BR-181 | 2 |
| BR-183 | [domains/catalog/domain.md](../../domains/catalog/domain.md) | ручное экранирование в вызывающих запрещено. BR-183 | 2 |
| BR-184 | [domains/persistence/domain.md](../../domains/persistence/domain.md) | create-or-get, где пустой список приводил к созданию дубликата. BR-184 | 2 |
| BR-186 | [domains/catalog/domain.md](../../domains/catalog/domain.md) | а не по SELECT'у на автора. BR-186 | 1 |
| BR-190 | [domains/editorial/domain.md](../../domains/editorial/domain.md) | BR-190 | 1 |
| BR-191 | [domains/catalog/components/dictionaries.md](../../domains/catalog/components/dictionaries.md) | ### Формат даты и времени публикации (BR-191, аудит #336, пакет PKG-S3-24) | 2 |
| BR-194 | [domains/identity/components/dictionaries.md](../../domains/identity/components/dictionaries.md) | ### Флаг приветственного сообщения премиума (BR-194, аудит #336, пакет PKG-S3-24) | 1 |
| BR-196 | [domains/editorial/domain.md](../../domains/editorial/domain.md) | (StatsByEvents.normalizeStatsPaging). BR-196 | 1 |
| BR-198 | [domains/processing/domain.md](../../domains/processing/domain.md) | проходят вместе. BR-198 | 2 |
| BR-247 | [domains/processing/components/karaoke-properties.md](../../domains/processing/components/karaoke-properties.md) | (encodePropertiesMap()); после записи файл имеет права 600 (BR-247, #407) | 2 |
| BR-283 | [domains/health/components/health-report.md](../../domains/health/components/health-report.md) | &#124; BR-283 &#124; Ошибка удаления объекта из REMOTE гасилась println внутри лямбды — UI показывал «исправлено», объект оставался &#124; deleteRemoteFileOrThrow: S | 2 |
| BR-284 | [domains/health/components/health-report.md](../../domains/health/components/health-report.md) | &#124; BR-284 &#124; Исключение в одном действии обрывало весь каскад: остальные действия не выполнялись, финальный пересчёт и SSE не уходили &#124; Каждое действие  | 4 |
| BR-288 | [domains/health/components/health-report.md](../../domains/health/components/health-report.md) | &#124; BR-288 &#124; karaoke-app/.../CacheEtagSizeBackfill.kt &#124; Ошибка выборки строк кеша пробрасывается (SSE error), а не превращается в пустой список с сообще | 1 |
| BR-290 | [domains/rendering/components/mlt-generator.md](../../domains/rendering/components/mlt-generator.md) | ### Голос без непустых по времени линий (BR-290, #398) | 1 |
| BR-291 | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | &#124; infra.crypto.config &#124; karaoke-app/.../services/KaraokeAppService.kt (cryptoLog) &#124; Проверка конфигурации AES при старте: неверная длина CRYPTO_AES_KE | 2 |
| BR-292 | [domains/monitoring/components/log-categories.md](../../domains/monitoring/components/log-categories.md) | &#124; infra.converter &#124; karaoke-app/.../Converter.kt (converterLog) &#124; Разбор настроек голосов/цветов/шрифтов: усечённая строка настройки, поле без значени | 2 |
| BR-315 | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | &#124; karaoke-public/src/utils/__tests__/requestGuard.test.js &#124; BR-300, BR-315, BR-321: поколения запросов &#124; | 1 |
| BR-334 | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | &#124; karaoke-public/src/utils/__tests__/loadState.test.js &#124; BR-318, BR-322, BR-323, BR-334, BR-341, BR-343, BR-346: «пусто» ≠ «ошибка» &#124; | 1 |
| BR-341 | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | &#124; karaoke-public/src/utils/__tests__/loadState.test.js &#124; BR-318, BR-322, BR-323, BR-334, BR-341, BR-343, BR-346: «пусто» ≠ «ошибка» &#124; | 1 |
| BR-343 | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | &#124; karaoke-public/src/utils/__tests__/loadState.test.js &#124; BR-318, BR-322, BR-323, BR-334, BR-341, BR-343, BR-346: «пусто» ≠ «ошибка» &#124; | 1 |
| BR-344 | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | &#124; karaoke-public/src/utils/__tests__/searchPagination.test.js &#124; BR-344, BR-345: восстановление среза и разные флаги ошибок &#124; | 1 |
| BR-345 | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | &#124; karaoke-public/src/utils/__tests__/searchPagination.test.js &#124; BR-344, BR-345: восстановление среза и разные флаги ошибок &#124; | 1 |
| BR-524 | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | Решения, зафиксированные BR-524 и BR-525 | 2 |
| BR-525 | [system/frontend/karaoke-public-tests.md](../../system/frontend/karaoke-public-tests.md) | Решения, зафиксированные BR-524 и BR-525 | 2 |
| BR-540 | [domains/persistence/domain.md](../../domains/persistence/domain.md) | Инварианты формулы (аудит #336, BR-540/541/553/533, OpenProject #363) | 2 |
| BR-551 | [guidelines/architecture-conventions.md](../../guidelines/architecture-conventions.md) | ### Секреты не передаются через argv (BR-551/BR-552/BR-605, OpenProject #382) | 4 |
| BR-571 | [system/infra/do-sh.md](../../system/infra/do-sh.md) | ## Guard деплой-пути: tools/check-do-sh-guards.sh (OpenProject #379, BR-571) | 1 |
| BR-628 | [system/infra/sql-migrations.md](../../system/infra/sql-migrations.md) | &#124; 57 &#124; authors_unique_author &#124; UNIQUE-индекс на tbl_authors.author (BR-628) &#124; | 1 |

