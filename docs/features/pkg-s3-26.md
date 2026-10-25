# PKG-S3-26 — правки аудита #336 (волна E, karaoke-app)

> **Status**: active
> **Feature Key**: pkg-s3-26
> **Last Updated**: 2026-10-25
> **Spec**: [specs/398-pkg-s3-26/report.md](../../specs/398-pkg-s3-26/report.md)
> **Issue**: OpenProject #398 (пакет `PKG-S3-26`, волна E бэклога аудита #336)
> **Branch**: `626-pkg-s3-26`
> Per-feature документ (Constitution § VI FR-009).

## Что делает

Пакет закрывает 19 записей аудита #336 в модуле `karaoke-app` (health-отчёты,
настройки рендера, крипта двух-БД sync, поиск обложек, гигиена репозитория).
Правки сгруппированы по трём блокам:

- **Health-отчёты и ремонт** (BR-280…BR-287, 8 записей) — отчёт больше не показывает
  «всё хорошо», когда проверка не выполнялась; ремонт не обрывается на первом
  упавшем действии и не «залипает» намертво; одиночное действие больше не
  выполняется в потоке HTTP-запроса.
- **Настройки, словари, крипта** (BR-276, BR-289…BR-294, 8 записей) — настройка
  голосов пишется в свой ключ; словарь цензуры экранируется и читается один раз на
  пакет; деление на длительность линии защищено от нуля; ошибки крипты логируются
  через SLF4J, а длина ключа/IV проверяется явно; разбор настроек устойчив к мусору
  в БД.
- **Поиск обложек и гигиена репозитория** (BR-293, BR-526, BR-531, 3 записи) — сбой
  Яндекс-источника не роняет эндпоинт (работает фолбэк); из `src/main/resources`
  удалена чужая фикстура с полями ПДн, из тестовых ресурсов — мёртвые MLT-проекты с
  локальными путями.

## Зачем

Каждая запись аудита — наблюдаемый дефект, а не стилистика:

- **Ложный «зелёный» отчёт.** При недоступном MinIO отчёт о здоровье показывал `OK`
  («файл актуален»), потому что «не удалось узнать размер» и «размер совпадает» были
  одним и тем же `null`. Админ видел исправное хранилище, которого не было.
- **Молчаливый ремонт.** Ошибка удаления объекта гасилась в `println` — UI показывал
  «исправлено», а объект оставался в MinIO; ошибка одного действия обрывала весь
  каскад, и админ не получал ни ошибки, ни обновления.
- **Залипший ремонт.** Исключение в пересчёте оставляло per-song guard включённым
  навсегда: «Исправить всё» для этой песни переставал работать до перезапуска
  приложения, без единого сообщения.
- **N+1 к БД.** Сбор отчёта по песне делал ~18 SQL-запросов только на проверку
  «не выполняется ли уже загрузка»; страница «Песни» — тысячи запросов.
- **Блокирующий HTTP-тред.** Одиночное «выполнить действие» качало файл из MinIO
  внутри запроса Tomcat.
- **Порча данных.** Значение словаря цензуры подставлялось в regex как есть: `х*й`
  маскировало «й» в любом слове, а значение с незакрытой скобкой роняло сохранение
  песни.
- **Гигиена репозитория.** В боевой jar попадал образец запроса ЕГРН с ПДн-подобными
  полями, а в публичном репозитории лежали реальные MLT-проекты с личными путями
  `~/Documents/Караоке/…`.

## Как работает

| Слой | Файлы | Что изменилось |
|---|---|---|
| Health-отчёты | `karaoke-app/.../HealthReport.kt`, `controllers/ApiController.kt` | типизированный `StorageFileInfoProbe` и вердикт `FileActualityVerdict`; задания песни одним запросом + `filterSongProcesses`; guard ремонта в `try/catch/finally`; `deleteRemoteFileOrThrow`; `executeResolvable` с per-action `try/catch` и счётчиком ошибок; `executeHealthReportActionsAsync` (+`202 Accepted`); `WARNING` для непрочитанного каталога ссылок; `ReadinessRecalcResult` |
| Настройки и словари | `karaoke-app/.../Karaoke.kt`, `Extentions.kt`, `Functions.kt`, `Converter.kt` | ключ `voices`; `CensoredDictionaryRules` (`Regex.escape` + компиляция один раз); деление на `maxDuration` только при `maxDuration > 0`; `getOrNull` после `split` + `safeColor` |
| Крипта | `karaoke-app/.../Crypto.kt`, `services/KaraokeAppService.kt` | `validateKeyMaterial` + `CryptoConfigurationException`; SLF4J `crypto: encrypt/decrypt failed op=…`; проверка конфигурации при старте (`infra.crypto.config`) |
| Обложки | `karaoke-app/.../AlbumCoverFinder.kt` | Яндекс-ветка в `try/catch` → `NotFound`, фолбэк работает |
| Ресурсы | `karaoke-app/src/main/resources/test.xml`, `karaoke-app/src/test/kotlin/{Правильный,Неправильный}.xml`, `1`, `2.xml` | удалены (потребителей нет) |

Общие принципы, которым подчинены правки:

1. **Сбой ≠ отсутствие данных.** «Не удалось узнать» — это отдельное состояние
   (`Unknown`), а не «файла нет» и не «всё хорошо».
2. **Fail-closed.** Неизвестное состояние → `WARNING` без действий; ошибка действия →
   лог + SSE `type=error`; неверная конфигурация крипты → операции отказываются
   работать.
3. **Ошибка не теряется.** `println`/`printStackTrace` заменены на SLF4J с throwable
   в затронутых местах; причины ошибок видны в логах.
4. **Работа вне HTTP-потока.** Долгие операции MinIO — в `repairExecutor`.
5. **Один источник данных на операцию.** Словарь, задания песни, ключ настройки.

## Инварианты

| Инвариант | Где зафиксирован |
|---|---|
| «Не удалось узнать размер объекта» ≠ «файл актуален»: вердикт `UNKNOWN` → `WARNING` + `storageStateUnknown` | `HealthReport.kt` (`StorageFileInfoProbe`, `fileActualityVerdict`) |
| Персистентные флаги готовности плеера не сбрасываются по неполной проверке | `HealthReport.kt` (`localStorageReadinessVerdict`, BR-279 + BR-281) |
| Guard `repairInFlight` всегда снимается (`try/catch/finally`) | `HealthReport.kt` (`startRepairAll`) |
| Каждое действие ремонта изолировано `try/catch`; число ошибок возвращается и показывается в SSE | `HealthReport.kt` (`executeResolvable`, `reportRepairFailure`) |
| `POST /api/song/executeHealthReportActions` не выполняет блокирующие операции MinIO в потоке запроса (ответ `202`) | `controllers/ApiController.kt` |
| Значение словаря «Censored» — литеральный текст, а не regex; словарь читается один раз на пакет | `Extentions.kt` (`CensoredDictionaryRules`) |
| Деление на длительность линии только при `maxDuration > 0` | `Functions.kt` (`getVoices`) |
| Длина AES-ключа (16/24/32 байта) и IV (16 байт) проверяется; ошибки логируются без значений | `Crypto.kt`, `services/KaraokeAppService.kt` |
| Сбой внешнего источника обложек не роняет эндпоинт — фолбэк всегда доступен | `AlbumCoverFinder.kt` |
| В `src/main/resources` нет чужих фикстур с полями ПДн; в тестовых ресурсах — фикстур с локальными путями | `specs/398-pkg-s3-26/report.md`, регресс-тест `PkgS326BehaviorTest` |

## Известные ловушки

1. **`null` из `cachedFileInfo` больше не значит «файл актуален».** Вызывающий обязан
   разобрать четыре исхода `FileActualityVerdict`: `ACTUAL` / `NOT_ACTUAL` /
   `DISK_VANISHED` (BR-278) / `UNKNOWN` (BR-281). `UNKNOWN` → `WARNING` +
   `storageStateUnknown = true`.
2. **Новый `KaraokeProcess.loadList` внутри `actions*` — это возврат N+1.** Задания
   песни читаются один раз в `getHealthReportList` и передаются параметром
   `songProcesses`; отбор — `filterSongProcesses` (BR-280).
3. **Guard ремонта снимается только в `finally`.** Любая новая точка входа в repair
   обязана брать `attemptEnterRepair` и снимать `exitRepair` (BR-282).
4. **Контракт `POST /api/song/executeHealthReportActions` изменился:** было `200` с
   пустым телом и синхронным выполнением, стало `202 Accepted` + выполнение в
   `repairExecutor`; фактический результат приходит по SSE (`HEALTH_REPORTS` и
   сообщение об ошибке). Клиент, который ждал «пустой успех», теперь должен читать
   SSE (BR-285).
5. **`recalculatePlayerReadiness` возвращает `ReadinessRecalcResult`, а не `Int`.**
   Новые вызывающие обязаны показывать оба числа (`processed` и `failed`), иначе
   вернётся ложный «всё посчитано» (BR-287).
6. **Формат значения словаря «Censored» — «[слово]»**: в скобках то, чем маскируют
   (для многобуквенных слов), для однобуквенных — `█` (прежнее поведение
   `getCensoredPair`). Экранирование значения (`Regex.escape`) не меняет формат, но
   значение с метасимволами теперь ищется буквально (BR-289).
7. **Правила цензурирования надо переиспользовать, а не перечитывать словарь.**
   В пакетных операциях — `CensoredDictionaryRules.load(database)` один раз, затем
   `text.censored(rules)` (BR-294).
8. **Крипта теперь бросает `CryptoConfigurationException` при неверной длине ключа
   или IV.** Вызывающий, который полагался на `null`, получит исключение — это
   намеренный fail-closed. Полный отказ приложения на старте (fail-fast) **не**
   сделан — решение за владельцем.
9. **`Karaoke.voices` и `Karaoke.flashColor` — разные ключи настройки.** Раньше сеттер
   `voices` писал в `flashColor` (BR-276); проверяйте ключ при отладке.
10. **Битый симлинк удаляется через `Files.isSymbolicLink` / `Files.deleteIfExists`.**
    `File.exists()` для битого симлинка = `false` — это и было причиной «мёртвой»
    кнопки «Удалить» в отчёте о здоровье (BR-277).
11. **Логика health-отчёта пишется через SLF4J-категорию `infra.health.report`**, а не
    в `println`: пропуск проверки обязан быть виден в логах (BR-286). Новые категории
    пакета: `infra.health.report`, `infra.converter`, `infra.crypto.config` — они
    зарегистрированы в `knowledge/domains/monitoring/components/log-categories.md`.
12. **Мёртвые фикстуры удалены, но «похожие» могут появиться снова.** Guard —
    регрессионный тест `PkgS326BehaviorTest` (BR-526/BR-531): он ищет фикстуры с
    полями ПДн в `src/main/resources` и с локальными путями в `src/test`.

## Ссылки

- Отчёт пакета: [specs/398-pkg-s3-26/report.md](../../specs/398-pkg-s3-26/report.md) (по записям, «до/после», побочные эффекты,
  решения за владельцем).
- Источник правды: [бэклог аудита § PKG-S3-26](../../specs/336-code-audit/backlog.md) и карточки
  `specs/336-code-audit/findings/P-01.md`, `P-02.md`, `P-03.md`, `P-07b.md`,
  `P-29.md`, `P-34.md`.
- Регрессия: `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/audit/PkgS326BehaviorTest.kt`
  (поведенческие проверки) и `PkgS326SourceContractsTest.kt` (контрактные проверки
  по исходникам).
- Знание (SSoT): `knowledge/domains/health/components/health-report.md`,
  `knowledge/domains/storage/components/storage-flow.md`,
  `knowledge/domains/catalog/components/song-entity.md`,
  `knowledge/domains/processing/components/two-db-sync.md`,
  `knowledge/domains/rendering/components/mlt-generator.md`,
  `knowledge/domains/integration/components/external-api-clients.md`,
  `knowledge/domains/monitoring/components/log-categories.md`,
  `knowledge/guidelines/architecture-conventions.md`.
- Предыдущие пакеты волны E: `specs/396-pkg-s3-24/report.md` (`PKG-S3-24`),
  `specs/395-pkg-s3-23/report.md` (`PKG-S3-23`).
