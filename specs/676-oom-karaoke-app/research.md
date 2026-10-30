# Research: спека 676 — устойчивость karaoke-app к исчерпанию памяти

> **Ветка**: `676-oom-karaoke-app` | **Дата**: 2026-10-30 | **Спека**: [spec.md](spec.md)
>
> **Phase 0** (`/speckit-plan`): все «NEEDS CLARIFICATION» из Technical Context
> закрыты ниже. Каждое решение — с обоснованием и отвергнутыми альтернативами.
> Факты сверены по коду репозитория на 2026-10-30 (ссылки — файл:строка).

---

## R1. Источник данных о загрузке heap

**Decision**: `Runtime.getRuntime()` — `maxMemory()` (потолок heap) и
`totalMemory() - freeMemory()` (занято). Никаких JMX/`MemoryMXBean`.

**Rationale**:
- Не зависит от состава JRE/JDK и от наличия модуля `java.management`:
  `ManagementFactory.getMemoryMXBean()` требует этот модуль, а проверка мониторинга
  не должна зависеть от того, как именно собран рантайм-образ.
- Процент загрузки MXBean'ом точнее не получить: `Runtime` даёт тот же самый
  максимум heap, относительно которого FR-015 и считает процент.
- Нулевая цена: `Runtime` — поле JVM, чтение не блокирует и не аллоцирует
  (инвариант FR-016: проверка без побочных эффектов, < 5 с).
- `Runtime.maxMemory()` ≈ `-Xmx` минус один survivor-space — для порогов
  «процент от максимума heap» это точное соответствие FR-007/FR-015.

**Факт об образе (проверено по `deploy/karaoke-app/Dockerfile:23`, `:48`)**:
финальный образ `karaoke-app` — `sm-karaoke.ru:5443/library/ubuntu:22.04` с
пакетом `openjdk-17-jre-headless` (JDK 17, JRE headless). Сборка использует
`deploy/karaoke-app/Dockerfile` (`deploy/do.sh:161`, `:207`, `:228`).

**Alternatives considered**:
- `ManagementFactory.getMemoryMXBean().heapMemoryUsage` — отвергнуто: зависимость от
  модуля `java.management` + создание MBean-ов (побочный эффект) + лишний
  overhead в тике, при нулевом выигрыше в точности процента.
- Считывать cgroup-файл `/sys/fs/cgroup/memory.current` — отвергнуто: это память
  контейнера, а не heap; FR-015 говорит именно о загрузке heap, и cgroup-файл
  недоступен вне контейнера (тесты).

---

## R2. Где задаются пороги 75 % / 90 %

**Decision**: env-переменные `MONITOR_HEAP_WARN_PERCENT` (75) и
`MONITOR_HEAP_CRITICAL_PERCENT` (90), читаются через `System.getenv` по
конвенции ADR `local-0001` (`?.toIntOrNull() ?: DEFAULT`). Значения **явно
прописаны** в `deploy/docker-compose-app.yml` (блок `environment:`) — так пара
«порог ↔ предел ↔ heap» видна в отслеживаемой конфигурации.

**Rationale**:
- FR-015 требует «пороги MUST задаваться конфигурацией (ADR local-0001)».
  ADR `local-0001` — это ровно env-only конвенция с несекретными дефолтами.
- Env, а не `KaraokeProperties`: FR-019 запрещает «новые персистентные ключи», а
  реестр `KaraokeProperties` — это персистентный файл `Karaoke.properties`.
  Порог — это параметр развёртывания (как и `-Xmx`), а не runtime-настройка.
- Порог в compose виден без чтения кода → «пара видна в репозитории» (FR-008).

**Alternatives considered**:
- `KaraokeProperties.getInt("monitorHeapWarnPercent")` (как у
  `ProdContainerCheck` → `monitorProdDownCriticalMinutes`) — отвергнуто: это
  персистентный ключ (FR-019) и требует правки реестра на 2700 строк.
- Зашить 75/90 в коде — отвергнуто: FR-015 прямо требует конфигурации.

---

## R3. Категория и формат логов проверки

**Decision**: категория `infra.monitor.heap` (строковая, не имя класса — по
конвенции `knowledge/domains/monitoring/components/log-categories.md`), формат
`event:key=value` по ADR `local-0005`:

| Событие | Уровень | Сообщение |
|---|---|---|
| Тик (каждую минуту) | INFO | `heap:usage usedBytes=… maxBytes=… percent=…` |
| Превышен порог | WARN / ERROR | `heap:thresholdExceeded percent=… threshold=… severity=…` |
| Возврат в норму | INFO | `heap:recovered percent=…` |
| Не удалось прочитать | WARN | `heap:readFailed error="…"` |

**Rationale**:
- FR-017 прямо предлагает `infra.monitor.heap` — по образцу `infra.monitor.tick`
  (`MonitoringService.kt:20`).
- SC-005 требует публикации загрузки **не реже раза в минуту** → нужна строка
  INFO на каждом тике, а не только при превышении.
- Recovery-сообщение обязательно (FR-017, инвариант домена `monitoring` № 4).

**Расхождение в базе знаний (исправляется в FR-020)**:
`monitoring/domain.md:90` говорит «Recovered-сообщения тоже логируются (INFO)»,
а `monitoring/components/log-categories.md:156` — «Уровень WARN для восстановления
— не INFO». Для heap-проверки выбран **INFO** (домен — более свежий документ и
он прямо привязан к FR-017). Расхождение фиксируется при правке базы знаний.

---

## R4. Состояние проверки

**Decision**: одно булево поле `@Volatile private var alerting = false` в объекте
проверки. Никаких новых персистентных ключей, никаких полей в `KaraokeProperties`.

**Rationale**: FR-019 (решение #347) — состояние в памяти; при перезапуске
контейнера счётчик сбрасывается, алерт появляется снова (это же зафиксировано
в § Edge Cases спеки).

**Alternatives considered**: хранение «с какого момента выше порога» для
эскалации WARNING→CRITICAL — отвергнуто: эскалация остаётся TODO в домене
`monitoring` (спека, § «Известные ограничения»), а severity по порогу выбирается
сразу.

---

## R5. Место новой проверки

**Decision**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/checks/HeapUsageCheck.kt`
(object : MonitorCheck) + одна строка в `MonitorRegistry.checks`
(`monitor/MonitorRegistry.kt:16-25`).

**Rationale**: инвариант домена `monitoring` — «Добавление новой проверки: один
`object : MonitorCheck` в `monitor/checks/` + одна строка в `MonitorRegistry.checks`»
(`monitor-core.md:51-52`). Регистрация в синглтоне `MonitorRegistry` означает
автоматический тик раз в минуту (`MonitoringService.kt:61`, `fixedDelay`).

**Расхождение в базе знаний (исправляется в FR-020)**: `monitor-checks.md:46-66`
описывает несуществующий интерфейс (`val name` + `check(): MonitorAlert?`) и несуществующий
алгоритм тика с `lastAlert[check.name]`. Фактический интерфейс —
`fun interface MonitorCheck { fun run(ctx: MonitorContext): List<MonitorAlert> }`
(`monitor/MonitorCheck.kt:24-26`), фактический тик — `snapshot = alerts.associateBy { it.key }`
(`MonitoringService.kt:76`).

---

## R6. Тест уровня вызывающих для FR-004 (высокорисковый пункт)

**Проблема**: FR-004 требует покрытия уровня `getUnPublicationList` /
`getSkipedPublicationList`, а не только `popPublicationStacks`. Обе функции
читают БД через сырой JDBC (`Song.loadListFromDb` — `Song.kt:9085`,
`Author.loadList` — `Author.kt:246` → `KaraokeDbTable.loadListInternal`).

**Decision**: подменить **границу JDBC** в тесте (Mockito, как уже сделано в
`karaoke-app/src/test/.../model/SqlInjectionTestSupport.kt` — `MockJdbc`):
- фейковый `KaraokeConnection` (уже есть `FakeKaraokeConnection.kt`) отдаёт
  мок `java.sql.Connection`;
- `ResultSet` для `tbl_songs` отдаёт 500 строк (`song_author` — по числу авторов),
  `ResultSet` для `tbl_authors` — пустой (для `getUnPublicationList`) или строки
  со `skip = true` (для `getSkipedPublicationList`);
- вызов идёт **через публичный API** `Publication.getUnPublicationList(...)` —
  product-код не меняется.

**Rationale**:
- FR-003 («поведение легаси-пути MUST остаться функционально прежним») —
  любая правка `Publication.kt` рискует этим; подмена на границе JDBC = ноль
  изменений в product-коде.
- Тест падает на прежней реализации: там условие выхода — захваченный максимум
  (`while (it > 0)`), т.е. бесконечный цикл → тест не завершается
  (ловится `@Timeout`); на текущей — конечный результат нужной формы.
- Существующие `PkgS212BehaviorTest` / `PkgS212SourceContractsTest` не трогаются
  (FR-004: «Существующие тесты MUST быть сохранены»).

**Проверено по коду (факты, а не предположения)**:
- `Song.loadListFromDb` читает ~120 колонок через `rs.getString/getInt/getLong/getBoolean`;
  непрочитанные геттеры мока дают `null`/`0` — колонка `song_author` задаётся
  явно, остальные не важны для раскладки (`groupBy { it.author }`).
- `Song.compareTo` → `sortString` (`Song.kt:420`) и `Song.state`
  (`Song.kt:7319`) — чистые функции, БД не трогают.
- `Author.loadList` при `skip = true` даёт `where.raw("skip = true")` без
  параметров → путь `createStatement()`; `KaraokeDbTable.loadListInternal` при
  `ignoreUseInList = true` не ходит в метаданные (`notInSelectList` пуст) →
  `SELECT tbl_authors.*`.
- `Publication(database)` и `publication.publish10 = song` — БД не трогают
  (`Publication.kt:89-107`).

**Fallback (если подмена JDBC окажется нерабочей на шаге implement)**: вынести
тело раскладки во внутреннюю функцию с предзагруженными данными
(`internal fun buildPublicationGrid(...)`) и вызывать её из обоих
`getUnPublicationList`/`getSkipedPublicationList`. Это меняет product-код
легаси-пути, поэтому выбирается только при фактической неудаче основного
варианта — и тогда требует отдельной строки в докладе лиду.

---

## R7. Пара «heap ↔ предел контейнера» в отслеживаемой конфигурации

**Decision**: в `deploy/docker-compose-app.yml` (сервис `karaoke-app`):

```yaml
    mem_limit: 8g                                   # предел памяти контейнера
    command: ${APP_JAVA_OPTS} -Xmx6g -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/dumps -jar /app.jar
```

`-Xmx6g` ставится **после** `${APP_JAVA_OPTS}`.

**Rationale**:
- FR-008: «пара „heap ↔ предел контейнера” MUST быть зафиксирована в
  репозитории (в том же compose — комментарием или явными полями)». Сейчас
  фактический `-Xmx8g` живёт только в неотслеживаемом `deploy/.env:7` — связи
  «heap меньше предела» из репозитория не проверяется.
- Фактический `-Xmx8g` из `deploy/.env` нельзя оставить действующим: при
  `mem_limit: 8g` heap 8 ГБ == предел 8 ГБ → FR-007 нарушен (запас на
  метапространство/native/потоки = 0), и OOM-kill приходит раньше, чем
  сработает `-XX:+HeapDumpOnOutOfMemoryError` (дамп не пишется — теряется
  ровно тот артефакт, ради которого затевались FR-009…FR-011).
- **Эмпирически проверено** на этой машине: `java -Xmx8g -Xmx6g
  -XX:+PrintFlagsFinal -version` → `MaxHeapSize = 6442450944` (6 GiB), т.е.
  **последний `-Xmx` побеждает**. Поэтому `-Xmx6g` после `${APP_JAVA_OPTS}`
  детерминированно перекрывает устаревшее значение из неотслеживаемого `.env`,
  не ломая и не выбрасывая остальные флаги из него (на 2026-10-30 в
  `deploy/.env` ровно `APP_JAVA_OPTS=-Xmx8g` — других флагов нет).
- `-Xmx8g` в `deploy/.env` — файл вне git; я его не меняю и не печатаю
  (Constitution VIII). Рекомендация владельцу: убрать `-Xmx8g` из `deploy/.env`
  (операционное действие вне репозитория), после чего `APP_JAVA_OPTS` можно
  очистить.

**Alternatives considered**:
- Убрать `${APP_JAVA_OPTS}` из compose совсем — отвергнуто: потеряются
  остальные флаги окружения, если владелец их туда добавит; «связь видна в
  репозитории» всё равно обеспечивается явным `-Xmx6g`.
- Задать `APP_HEAP_MAX` как отдельную переменную — отвергнуто: значение снова
  можно переопределить из неотслеживаемого `.env` → FR-007 снова не
  проверяется.
- `mem_limit: 8g` + `-Xmx8g` — отвергнуто: ровно то, что было в инциденте
  2026-10-23 (heap == предел).

**`cpus` не добавляется**: FR-006/FR-007 требуют только предел памяти;
CPU-лимит для контейнера, который гоняет ffmpeg/melt/Playwright, — это
эксплуатационный риск вне контракта. Образец `mem_limit: 1600m / cpus: 2.0`
(`deploy/prod-single-host/docker-compose-web.yml:108-109`) — это про
`karaoke-web` на прод-хосте с 3.8 ГБ RAM, не про admin-машину.

---

## R8. Том дампов

**Decision**: `./karaoke-db/dumps:/dumps` — тот же host-каталог, что уже смонтирован
в `karaoke-db` (`deploy/docker-compose-database.yml:41`, BR-046) и используется
прод-образцом (`deploy/prod-single-host/docker-compose-web.yml:77`).

**Rationale**: каталог `deploy/karaoke-db/dumps` уже существует в репозитории
(`.gitkeep` отслеживается, содержимое — в `.gitignore:127-128`), путь
`/dumps` внутри контейнера — тот же, что в прод-образце (BR-547). FR-010
(«доступен для чтения вне контейнера») выполняется автоматически: это bind-mount.

**Alternatives considered**: отдельный каталог `./karaoke-app-dumps:/dumps` —
отвергнуто: размножает каталоги дампов и расходится с уже принятой конвенцией
BR-046/BR-547.

---

## R9. Политика хранения дампов (последние 2, ≤ 20 ГБ, вытеснение по возрасту)

**Decision**: **host-side** скрипт `deploy/heap-dump-retention.sh` +
systemd-юнит `deploy/heap-dump-retention.service` и таймер
`deploy/heap-dump-retention.timer` — по образцу существующих
`deploy/karaoke-db-backup.{sh,service,timer}`. Скрипт:
1. работает **только** с файлами `*.hprof` (SQL-дампы БД в этом же каталоге не
   трогаются — они `.sql`);
2. оставляет **2 самых новых** файла;
3. удаляет остальные, **вытесняя по возрасту** (сначала самые старые), пока
   суммарный объём `*.hprof` не станет ≤ 20 ГБ;
4. не трогает файл, изменённый менее N минут назад (защита от удаления дампа,
   который пишется прямо сейчас).

**Rationale**:
- Решение владельца — «последние 2 дампа, лимит тома 20 ГБ, вытеснение по
  возрасту» (FR-011).
- Каталог дампов общий с SQL-дампами БД → **любая** политика «удалить старые
  файлы в каталоге» без фильтра по расширению опасна. Фильтр `*.hprof` — это
  то, что делает политику безопасной.
- Политика на стороне хоста, а не внутри контейнера: JVM при OOM **умирает** —
  встроенная в приложение уборка не отработает в самый нужный момент. Хостовый
  таймер работает всегда.
- Не трогаем `entrypoint`/`command` критичного контейнера `karaoke-app`: смена
  способа старта JVM на OOM-критичном сервисе — риск без выгоды.
- Нет нового контейнера, нет нового образа, нет правок `deploy/do.sh`.

**Alternatives considered**:
- *Уборка внутри приложения* (`karaoke-app` удаляет старые дампы при старте и по
  таймеру) — отвергнуто: приложение не может убрать дамп после того, как само
  умерло от OOM; плюс продуктовый код, удаляющий файлы, — лишний риск.
- *Sidecar-контейнер в `docker-compose-app.yml`* — отвергнуто: новый контейнер в
  критичном compose + новый образ на admin-машине; `do.sh start_app` поднимает
  все сервисы файла, т.е. новый контейнер поедет в прод-окружение без
  отдельного решения владельца.
- *Inline-скрипт в `entrypoint`* — отвергнуто: изменение способа старта
  `karaoke-app` (риск «контейнер не стартует») + нужен bash-скрипт на месте.

**Операционное действие (вне репозитория, за владельцем)**: установить и
включить таймер на admin-машине (`systemctl enable --now heap-dump-retention.timer`)
— путь к скрипту в юните указывает на каталог `deploy/` этой машины. До
включения таймера политику можно применять вручную:
`bash deploy/heap-dump-retention.sh`.

**Граница полномочий (решение лида, 2026-10-30)**: скрипт и юниты **создаются
в репозитории**, но **ничего не устанавливается и не включается на машине** —
`systemctl enable --now`, `systemctl start` и любые операции с systemd только
по слову владельца. Поэтому скрипт обязан быть работоспособным **без
установки**: `bash deploy/heap-dump-retention.sh` (ручной путь) — основной
рабочий сценарий, а активация таймера — документированное действие владельца
в runbook'е (FR-013). Том `./karaoke-db/dumps:/dumps` в compose заводится в
любом случае: retention без тома бессмыслен.

---

## R10. Машиночитаемая проверка деплой-контракта

**Decision**: новое правило в `tools/check-deploy-hardening.sh` (формат
`check_brXXX` + регистрация в массиве `CHECKS`), по образцу BR-547
(прод-`karaoke-web`): для `deploy/docker-compose-app.yml` проверяются
`mem_limit`, `-XX:HeapDumpPath=/dumps`, монтирование `./karaoke-db/dumps:/dumps`
и **отношение heap < предел** (`-Xmx6g` против `mem_limit: 8g`).

**Rationale**: FR-008/SC-003 требуют, чтобы связь «heap ↔ предел» была видна и
проверяема; в этом репозитории такие связи уже защищены guard'ами (BR-547).
Регистр BR-кодов — `knowledge/system/infra/audit-336-registry.md` (там же —
формулировка BR-188, которую FR-020 просит синхронизировать).

**Новый BR-код**: в реестре используются BR-600…BR-628, но **BR-629 уже занят** (`karaoke-web`,
контракты публичного API). Для этой работы заведён следующий свободный код —
**BR-630** — и строка в реестре аудита.

---

## R11. Per-feature документ (FR-009 / Constitution VI)

**Decision**: завести **новый** документ
`knowledge/product/features/heap-memory-guardrails.md` (6 обязательных секций
по `tools/check-feature-doc.sh`).

**Rationale**: правка кода (новая проверка мониторинга) обязана сопровождаться
per-feature документом (Constitution VI, FR-009). SSoT-guard
(`tools/check-ssot-impact.py` + `.ssot-map.yml`) для `monitor/**` и
`deploy/**` правил не содержит, т.е. формально не требует — но FR-009 требует
документ при правке кода, и в каталоге нет ни одного документа про
превентивные меры против OOM. Документ аддитивен (не меняет контракт спеки):
FR-020 перечисляет 12 обязательных документов, 13-й их не отменяет.

Существующий `knowledge/product/features/publications-filters.md` обновляется
по факту правок в области публикаций (FR-021).

---

## R12. Новое архитектурное решение → ADR

**Decision**: завести `knowledge/adr/local-0014-heap-memory-guardrails-karaoke-app.md`
(номер `local-0014` свободен; `local-0001`…`local-0013` заняты) и внести его в
`knowledge/adr/README.md`.

**Rationale**: по условию задачи «если понадобится новое архитектурное решение
(например, источник данных о heap или место хранения дампов) — заведи ADR».
Именно такие решения и приняты: источник данных (R1), место хранения дампов и
исполнитель политики хранения (R8, R9), способ фиксации пары «heap ↔ предел»
(R7). ADR фиксирует **почему**, детали — в `knowledge/domains/` и в этой спеке.

**Расхождение в базе знаний и решение лида (2026-10-30)**: ADR `local-0013`
(«внешних оповещений нет — journal-only») трактуется в спеке как решение
**infra-сервера**; FR-018 уточняет, что для `karaoke-app` канал — журнал/SLF4J
по умолчанию. ADR в этом репозитории **append-only** (`knowledge/adr/README.md`:
«нельзя редактировать или удалять ADR после принятия»), поэтому
**`local-0013` НЕ редактируется** — уточнение области решения фиксируется в
новом ADR `local-0014` (раздел «Следствия») и в документах домена `monitoring`
(`domain.md`, `log-categories.md`, `monitor-checks.md`).

**Отклонение от буквы FR-020**: FR-020 перечисляет
`knowledge/adr/local-0013-infra-backups-and-monitoring.md` среди 12 документов
«к приведению в соответствие фактам». Формально документ не меняется — по
решению лида (append-only ADR). Отклонение фиксируется отдельной строкой в
`report.md` и в докладе; решение владельца запрашивается отдельно.

---

## Итог: что уже сделано и что осталось

| Пункт спеки | Состояние на 2026-10-30 | Работа на шаге implement |
|---|---|---|
| FR-001…FR-003 (конечность раскладки) | **Сделано** в master (коммит `4d643554`, BR-188) | Не переписывается; только закрепляется тестом |
| FR-004 (покрытие вызывающих) | Не покрыто | Новый тест (R6) |
| FR-005 (современные пути не тронуты) | — | Контроль: тест/ревью не трогает `unpublications2`/`publicationsdigest` |
| FR-006…FR-008 (лимиты) | Нет в compose | Правка compose (R7) + guard (R10) |
| FR-009…FR-013 (дампы) | Не настроено | Compose (R8) + скрипт/юниты (R9) + runbook |
| FR-014…FR-019 (мониторинг heap) | Нет | Новая проверка (R1–R5) + тесты |
| FR-020…FR-021 (база знаний) | Устарела | 12 документов (без `local-0013` — ADR append-only) + ADR `local-0014` + per-feature + Linking Protocol |

---

## Покрытие FR → где реализуется

| FR | Где | Артефакт плана |
|---|---|---|
| FR-001 | Уже в master (`4d643554`) — не переписывается | контракт `publication-grid.md` § 2 (I1, I2) |
| FR-002 | Уже в master | `publication-grid.md` § 2 (I3, I4) |
| FR-003 | Уже в master; product-код **не меняется** | `publication-grid.md` § 4 (подмена границы JDBC) |
| **FR-004** | **Новый тест уровня вызывающих** | `publication-grid.md` § 3–5, research R6 |
| FR-005 | Не трогаем | `publication-grid.md` § 5.4 |
| FR-006 | `mem_limit: 8g` | `deploy-memory-limits.md` § 2 |
| FR-007 | `-Xmx6g` < `mem_limit: 8g` | `deploy-memory-limits.md` § 2 + guard BR-630 |
| FR-008 | Пара видна в compose + guard | `deploy-memory-limits.md` § 2, research R7/R10 |
| FR-009 | `-XX:+HeapDumpOnOutOfMemoryError`, `-XX:HeapDumpPath=/dumps` | `deploy-memory-limits.md` § 2 |
| FR-010 | `./karaoke-db/dumps:/dumps` (bind-mount — доступен вне контейнера) | `deploy-memory-limits.md` § 2, research R8 |
| FR-011 | Retention: последние 2, ≤ 20 ГБ, вытеснение по возрасту | `deploy-memory-limits.md` § 3, research R9 |
| FR-012 | Дампы вне git: `.gitignore:127-128` (`/deploy/karaoke-db/dumps/*`, кроме `.gitkeep`); путь описан в runbook'е (`knowledge/guidelines/runbooks/log-correlation.md`) | `deploy-memory-limits.md` § 2–3, план (этап D) |
| FR-013 | Runbook: порядок действий при алерте по памяти | `knowledge/guidelines/runbooks/log-correlation.md` (этап D) + `quickstart.md` § 5.3–5.4 |
| FR-014 | `HeapUsageCheck` + строка в `MonitorRegistry.checks` | `heap-monitor-check.md` § 1, research R5 |
| FR-015 | Пороги 75 %/90 % из env (ADR `local-0001`), дефолты консервативные | `heap-monitor-check.md` § 3, research R2 |
| FR-016 | Только чтение `Runtime`; < 5 с (фактически мс) | `heap-monitor-check.md` § 2, § 8.6–8.7, research R1 |
| FR-017 | Категория `infra.monitor.heap`, формат `event:key=value`, recovery-сообщение | `heap-monitor-check.md` § 4, research R3 |
| FR-018 | Канал — журнал/SLF4J + существующие SSE и `GET /api/monitor/alerts` | `heap-monitor-check.md` § 6 |
| FR-019 | Состояние в памяти (`@Volatile`), новых персистентных ключей нет | `heap-monitor-check.md` § 7, research R4 |
| FR-020 | 12 документов базы знаний (кроме `local-0013` — ADR append-only, см. R12) | план, этап D |
| FR-021 | Код + база знаний в одном PR; Linking Protocol L3→L2→L1 | план, этап D |
