# Architecture Conventions

> **Домен**: `knowledge/guidelines/`
> **Назначение**: **primary reference** для build/runtime/docker конвенций
> Karaoke (R-04, R-05, R-06, R-08, R-11, R-44, R-46) и правил работы с секретами
> («Секреты не передаются через argv»). Детальные описания —
> в `knowledge/domains/<name>/` и `knowledge/adr/`.
>
> **Статус**: Этот документ стал **single source of truth** для всех build/docker
> правил после Pass 379 wayfinder #101 (#104 resolution, Q2). До этого
> правила были **разбросаны** по AGENTS.md / CLAUDE.md / constitution.md.
> Теперь — единый справочник с явным форматом **Rule/Protocol/Failure**.

Этот документ содержит **краткий справочник** по архитектурным конвенциям
Karaoke. Каждое правило — в формате `Rule / Protocol / Failure / Enforcement`.

## Build / runtime / docker конвенции

Эти правила machine-readable enforceable через `tools/check-*.sh` guards
(см. Pass 372-375, Pass 379). Изменения здесь — через governance-PR.

### R-04 — `nginx:stable` (НЕ `nginx:alpine`)

**Rule**: В `Dockerfile` для production должна быть строка `FROM nginx:stable`
(или `nginx:stable-alpine`, `nginx:1.27-alpine` для LTS-версий). **Запрещены**
теги `nginx:alpine`, `nginx:latest`, без тега.

**Protocol**:
- Использовать `nginx:stable` (production preferred).
- Если нужен alpine: `nginx:stable-alpine`.
- Указать **major.minor** для воспроизводимости: `nginx:1.27-alpine`.

**Failure**: Использование `nginx:alpine` → контейнер падает с
`bash: not found` (Pass 245 прецедент).

**Source**: `deploy/karaoke-public/Dockerfile:16`, `deploy/karaoke-webvue3/Dockerfile:16`.

**Enforcement**: `tools/check-docker-image-tags.sh` (Pass 379, R-04).

### R-05 — `node:22-alpine` (НЕ `node:latest`)

**Rule**: В `Dockerfile` для production должна быть строка `FROM node:22-alpine`
(или `node:20-alpine`, `node:20-bookworm-slim` для LTS-версий). **Запрещены**
теги `node:alpine`, `node:latest`, `node:lts`, без тега.

**Protocol**:
- Использовать `node:22-alpine` (current LTS).
- Если нужна конкретная версия: `node:22.x-alpine`.
- **Никогда** `:latest` (недетерминированный, может сломаться).

**Failure**: Использование `node:latest` → непредсказуемые версии
зависимостей, невоспроизводимые сборки.

**Source**: `deploy/karaoke-public/Dockerfile:1`, `deploy/karaoke-webvue3/Dockerfile:1`.

**Enforcement**: `tools/check-docker-image-tags.sh` (Pass 379, R-05).

### R-06 — TOP-10 ловушек (краткий список)

**Rule**: См. раздел «Ловушки» ниже. Полный список с примерами —
в AGENTS.md § «TOP-10 ловушек» и `AGENTS.md` § «HARD GATES».

**Failure**: Каждая ловушка имеет свой failure-stop (см. AGENTS.md).

**Enforcement**: Ряд pre-commit hooks (`tools/check-*.sh`).

### R-08 — Sanitizer idempotency

**Rule**: `sanitize(sanitize(s)) == sanitize(s)` — функция sanitize должна
быть **идемпотентна**. Применение дважды = применение один раз.

**Protocol**:
- Никогда не использовать `replace` если `drop` сохраняет идемпотентность.
- Тесты: `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/SanitizePathTest.kt` проверяет идемпотентность.

**Failure**: Нарушение → потеря данных при импорте (issue #53: `!`/`?`
молча удалялись → файлы не находились).

**Reference**: `docs/features/idempotent-path-sanitize.md`.

**Enforcement**: `tools/check-no-mp4-mentions.sh` baseline (125 записей legacy).

### R-11 — MP4/скачивание запрет (оферта «доступ только онлайн»)

**Rule**: В production-коде (webvue3, karaoke-public, karaoke-app, karaoke-web)
запрещено упоминание `\bmp4\b` или `скачивани` в **публичном контексте**.

**Исключения** (legitimate use, в baseline):
- Backend MP4-рендеринг (`PlayerMp4RenderService.kt`, mlt/Consumer) —
  производственный пайплайн.
- Имена файлов `*.mp4` в model (Song.kt, SongOutputFile.kt).
- Auto-publish в VK/Telegram (admin-пайплайн, не публичное скачивание).
- KaraokeProcess.kt, AlbumCoverFinder.kt (admin-render).
- UI-текст ОФЕРТЫ (явно говорит «без скачивания»).
- webvue3/karaoke-public player (headless mp4 export, admin).

**Failure**: Упоминание MP4 в публичных UI-обещаниях → нарушение оферты
(`karaoke-public/src/views/OfertaView.vue`), legal/compliance риск.

**Enforcement**: `tools/check-no-mp4-mentions.sh` (Pass 379, R-11).

База исключений сопоставляется **по содержимому строки**, не по номеру
(Pass 455): ключ — `<путь>:sha256(trim(строки))[:16]`. До этого база была
`file:line` и ломалась от любой вставки строк выше — guard рапортовал «новое
нарушение R-11» там, где упоминания не добавлялись (трижды за одну сессию:
Pass 451, 452, 454). Вставка/удаление строк и смена отступа теперь базу не
ломают; изменение самого текста упоминания — ломает, и это намеренно.
Перегенерация: `bash tools/check-no-mp4-mentions.sh --update-baseline`.

### R-44 — MLT/melt рендеринг (НЕ ffmpeg)

**Rule**: Караоке-видео рендеринг — через MLT framework (`melt` CLI).
**Не использовать** прямой `ffmpeg` для основного рендеринг-пайплайна
(допускается для вспомогательных операций: extract audio, probe, конвертация).

**Protocol**:
- Все rendering jobs → `melt` (MLT framework).
- Queue lanes: HEAVY_RENDER=0, LIGHT_BACKGROUND=-1, REMOTE_STORE_UPLOAD=-2.
- JPEG quality: 95.

**Reference**: [ADR-0002-mlt-instead-of-ffmpeg.md](../adr/0002-mlt-instead-of-ffmpeg.md),
[rendering domain](../domains/rendering/components/mlt-pipeline.md).

**Enforcement**: code review (no guard script — требует domain knowledge).

### R-46 — Конфигурация сборки: один Gradle, один Jackson, без мёртвых дублей

**Rule** (аудит #336, OpenProject #406, пакет `PKG-S3-34`):

1. **Один gradle-wrapper на весь репозиторий.** Все четыре wrapper'а
   (`gradle/`, `karaoke-app/`, `karaoke-web/`, `karaoke-db/`) указывают на
   **одну** версию дистрибутива — ту, на которой зелёная CI-сборка, — и не
   ниже минимума `8.14`.
2. **Контрольная сумма дистрибутива зафиксирована.** В каждом wrapper'е есть
   `distributionSha256Sum=<сумма с services.gradle.org>`.
3. **Jackson — одной версии, управляемой BOM'ом.** В build-файлах **нет** явных
   версий `com.fasterxml.jackson.*`: версию всего семейства задаёт BOM Spring
   Boot (2.19.2).
4. **Дамп кучи — только явным opt-in и с приватными правами.** Каталог дампов
   `0700` внутри `GRADLE_USER_HOME`, файл дампа — `0600`.
5. **Нет мёртвого дублирующего build-конфига.** В корневом `build.gradle.kts`
   нет блока `dependencies`; в корне репозитория нет `pom.xml`.

**Protocol**:

- Смена версии Gradle — отдельным PR с зелёной сборкой; версия ниже `8.14`
  запрещена (на ней ломается Kotlin 2.2.20 / Spring Boot 3.5.6).
- Сумму дистрибутива брать **только** из официального
  `https://services.gradle.org/distributions/gradle-<v>-bin.zip.sha256`
  (тот же файл публикует `downloads.gradle.org`), не выдумывать и не брать
  из локального кеша. Таблица эталонных сумм — в `tools/check-gradle-wrapper.sh`.
- Jackson: версию не задавать (`implementation("com.fasterxml.jackson.core:jackson-databind")`).
- Дамп кучи: `bash tools/gradle-heap-dump.sh <задачи>` — скрипт ставит
  `umask 077` и создаёт каталог `0700`; в `gradle.properties` флагов дампа нет.
- Зависимости модулей — только в build-файлах модулей. Корневой проект
  исходников не имеет, `implementation` в subprojects не наследуется.

**Failure**:

- три разных Gradle в репозитории → CI проверяет не то, что запускает
  разработчик в каталоге модуля (BR-499);
- wrapper без суммы → Gradle распаковывает и запускает ~130 МБ бинарника без
  проверки целостности (компрометация CDN/зеркала/прокси) (BR-528);
- `jackson-databind:2.14.2` рядом с `jackson-core:2.19.2` → смешанный стек
  семейства, не покрытый поддержкой производителя (BR-527);
- `-XX:HeapDumpPath=/tmp/…` → полный дамп кучи (вместе с `System.getenv()`,
  т.е. секретами окружения) читается любым локальным пользователем (BR-529);
- мёртвый блок `dependencies` в корне и `pom.xml` → правка зависимости «не
  туда», фантомный Maven-проект в IDE, сборка с Java-target 16 (BR-530).

**Source**: `gradle/wrapper/gradle-wrapper.properties`,
`karaoke-{app,web,db}/gradle/wrapper/gradle-wrapper.properties`,
`build.gradle.kts`, `karaoke-app/build.gradle.kts`, `karaoke-web/build.gradle.kts`,
`gradle.properties`, `tools/gradle-heap-dump.sh`.

**Enforcement**: `tools/check-gradle-wrapper.sh` (версии + checksum'ы),
`tools/check-build-config.sh` (Jackson, мёртвый конфиг, дампы кучи),
`tools/gradle-heap-dump.sh --self-test` (эмпирическая проверка прав 700/600).
Джоб `build-config-guard` в `.github/workflows/lint.yml`, pre-commit-хуки
`gradle-wrapper-guard` и `build-config-guard`. Per-feature документ —
[`docs/features/pkg-s3-34.md`](../../docs/features/pkg-s3-34.md).

## Контейнеры и инфраструктура

| Тема | Ключевые правила |
| --- | --- |
| **L1 System Context** | Внешние актёры: посетители, Telegram, VK ID OAuth. См. [system/01-context.md](../system/01-context.md). |
| **L2 Containers** | `karaoke-app`, `karaoke-web`, `webvue3`, `karaoke-public`, PostgreSQL, MinIO. См. [system/02-containers.md](../system/02-containers.md). |
| **L3 Components** | Технические компоненты внутри контейнеров. См. `domains/<name>/components/*.md`. |
| **Dual-DB access** | LOCAL ↔ SERVER sync, `recordhash`-триггеры. |
| **Database** | PostgreSQL 16, схемы, индексы. |
| **Deployment** | Деплой через `deploy/do.sh`. |
| **Nginx conventions** | `nginx:stable` (НЕ `nginx:alpine`!), production headers. |
| **Docker conventions** | `node:22-alpine`, минимизация слоёв. |
| **CI/CD pipeline** | GitHub Actions, проверки knowledge/ и code style. |

### Права на файлы, создаваемые приложением

Права выставляются **в процессе JVM** через хелперы `Extentions.kt`:

| Что | Права | Хелпер |
| --- | --- | --- |
| Опубликованный файл (картинка, превью, субтитр) | `644` | `setPublishedFilePermissions()` / `setSongFilePermissions()` |
| Каталог публикации | `755` | `setPublishedDirectoryPermissions()` |
| Файл с секретами (`Karaoke.properties`, `.bak`) | `600` | `setSecretFilePermissions()` |

`644`/`755` — владелец пишет, остальные только читают (и обходят каталог);
секреты не читает никто, кроме владельца-процесса.

**Не использовать `runCommand(listOf("chmod", ...))`.** Почему:

- `666`/`777` даёт мировое право на **запись**, хотя пишут эти файлы только
  приложение и он же единственный писатель (контейнер под root); мир-запись
  позволяет подменить опубликованный артефакт или переписать конфиг с токенами
  из любого контейнера с тем же томом;
- внешний процесс ради одной операции — лишний запуск на каждый файл;
- отказ ломал сохранение: в одном месте `chmod` стоял вне `try/catch` и уже
  после записи данных, поэтому запрос отдавал ошибку при применённых правках.

Хелперы логируют ошибку и **не прерывают** вызывающий код: файл
уже создан, а отсутствие прав на чтение сторонним инструментам — повод
разобраться, но не причина объявлять правку несохранённой.

**Миграция не завершена.** `chmod 666`/`777` ещё встречается в
`KaraokeProcess.kt`, `Song.kt`, `Utils.kt`, `Functions.kt`, `CleanupJournal.kt`,
`SyncAudioDescendants.kt`. Часть затрагивает рендеринг — Kdenlive и ffmpeg
читают эти файлы, — поэтому замена требует отдельной проверки, а не правки по
коду. Эти места принадлежат другим записям аудита #336 (в частности BR-271,
OpenProject #397) и внесены в baseline guard'а.

**Enforcement**: `tools/check-file-permissions.sh` (baseline
`tools/check-file-permissions.baseline`, только legacy-места других пакетов).
Guard падает на `chmod 666/777`, `o+w`/`a+w` и символических формах; проверяет
`karaoke-app/src/main`, `karaoke-web/src/main`, `deploy`, `tools`.
Прецеденты: BR-115 (`UtilsPictures.kt` — `777` на каталог и `666` на 12 файлов)
и BR-247 (`KaraokeProperties.kt` — `666` на файл с токенами), OpenProject #407.

### Секреты не передаются через argv (BR-551/BR-552/BR-605, OpenProject #382)

**Rule**: секрет (пароль, API-токен) **не должен** попадать в аргументы команды.
`argv` читает любой пользователь машины (`ps -eo args`, `/proc/<pid>/cmdline`),
поэтому секрет в аргументах — это утечка в список процессов.

**Protocol**: выбирать безопасный канал по типу секрета.

| Секрет | Запрещено | Правильно |
|---|---|---|
| Пароль registry | `docker login --password $PASS` | `printf '%s' "$PASS" \| docker login … --password-stdin` |
| Пароль БД | `docker exec -e PGPASSWORD=$PASS …` | `PGPASSWORD` в окружении процесса, `.pgpass` (`PGPASSFILE`), `--env-file`, stdin |
| Токен API (Basic/Bearer) | `curl -H "Authorization: …"`, `curl -u user:pass` | `curl --config -` с `header = "Authorization: …"`, поданным на stdin |
| Пароль в строке подключения | `psql "… password=$PASS …"` | `PGPASSWORD`/`.pgpass`, без пароля в аргументах |

**Failure**: секрет в списке процессов машины. Прецедент: BR-551 (пароль
registry в legacy-`do.sh`), BR-552 (пароль БД трекера), BR-605 (admin-токен
OpenProject в `tools/tracker-lib.sh`).

**Enforcement**: guard `tools/check-secret-in-argv.sh` (pre-commit-хука
`secret-in-argv-guard`). Baseline `tools/check-secret-in-argv.baseline` —
известные legacy-нарушения вне пакета OpenProject #382 (сопоставление по
содержимому строки, конвенция Pass 455). Безопасные формы (`PGPASSWORD=` в
окружении, `--password-stdin`, `curl --config -`, `.pgpass`) нарушением не
считаются.

## Бизнес-конвенции

| Тема | Ключевые правила |
| --- | --- |
| **Monetization** | Эфир-N-дней + 1 трек/исполнитель + 1 альбом/исполнитель free. |
| **Conversion funnel** | visitor → registration → premium. См. [stats domain](../domains/stats/components/event-funnel.md). |
| **Public modules** | Доступ — только онлайн (НЕ упоминать MP4/скачивание). См. **R-11**. |
| **Share-link** | Гостевой доступ по ссылке. |
| **Idempotency** | `sanitize(sanitize(s)) == sanitize(s)`. См. **R-08**. |
| **Concurrent editing** | Optimistic locking, `recordhash`. |
| **Data sync** | LOCAL ↔ SERVER. |
| **DB migration playbook** | Миграции: append-only, никогда не удалять. |
| **Censoring** | Цензурирование мата. |

## Операционные паттерны

| Тема | Ключевые правила |
| --- | --- |
| **DSH sandbox conventions** | Работа в DSH-окружении (см. AGENTS.md § Gradle/Docker). |
| **Queue lanes** | HEAVY_RENDER lane (threadId=0). См. [rendering domain](../domains/rendering/components/mlt-pipeline.md). См. **R-44**. |
| **MLT pipeline** | MLT/melt рендеринг, JPEG quality 95. См. **R-44**. |
| **Cache invalidation** | Cron-update + dirty-флаг. См. [caching domain](../domains/caching/components/caching-patterns.md). |
| **Observability** | SLF4J-категории `infra.*`, мониторинг. См. [monitoring domain](../domains/monitoring/). |
| **Invariants** | Список инвариантов Karaoke. |

## Языковые/форматные конвенции

| Тема | Ключевые правила |
| --- | --- |
| **Jackson conventions** | `is`-prefix запрещён, `@JsonProperty` обязателен. Версию Jackson не задавать вручную — BOM Spring Boot. См. [identity dictionaries](../domains/identity/components/dictionaries.md). → **R-46**. |
| **Webvue3 patterns** | Vue 3 + Bootstrap-vue-next. |
| **Documentation conventions** | Markdown, frontmatter, mermaid. |

## Куда смотреть при изменениях

| Что меняется | Сначала прочитать | Потом проверить |
| --- | --- | --- |
| Авторизация | [security-config](../domains/identity/components/security-config.md) | nginx conventions (R-04) |
| БД / миграция | [ADR-0001-raw-jdbc](../adr/0001-raw-jdbc.md) + db-migration playbook | KR 022 |
| Deploy | deploy workflow | docker conventions (R-04, R-05) |
| MLT/рендер | [mlt-pipeline](../domains/rendering/components/mlt-pipeline.md) + [ADR-0002](../adr/0002-mlt-instead-of-ffmpeg.md) | [rendering domain](../domains/rendering/) и **R-44** |
| Auth (Spring) | [security-config](../domains/identity/components/security-config.md) | [ADR-0006](../adr/0006-processbuilder-redirect-errorstream.md) |
| Конфигурация сборки (wrapper/зависимости/дампы) | **R-46** + [ci-tools](../system/infra/ci-tools.md) | `tools/check-gradle-wrapper.sh`, `tools/check-build-config.sh` |

### Гигиена репозитория: чужие фикстуры и локальные пути (BR-526, BR-531, #398)

- `karaoke-app/src/main/resources/test.xml` — образец запроса ЕГРН из чужого проекта с
  ПДн-подобными полями (ФИО, телефон, e-mail, СНИЛС, адрес). Потребителя не было,
  но файл упаковывался в `BOOT-INF/classes/` каждого боевого jar и лежал в публичном
  репозитории. **Удалён**; в `src/main/resources` не должно быть XML/JSON-фикстур с
  полями ПДн.
- `karaoke-app/src/test/kotlin/{Правильный.xml,Неправильный.xml,1,2.xml}` — реальные
  MLT-проекты с абсолютными путями `~/Documents/Караоке/…` (личные каталоги и название
  песни автора) в публичном репозитории. Потребителей не было. **Удалены**;
  в тестовых ресурсах не должно быть фикстур с локальными путями.

## Ловушки (TOP-11 из AGENTS.md)

См. AGENTS.md § «HARD GATES» — полный список. Краткий preview:

1. **Backticks в KDoc** ломают парсер ktlint. Заменять `` `multitrack` `` → «multitrack».
2. **`redirectErrorStream(false)`** для `ProcessBuilder` блокирует процесс. Всегда `true`.
3. **`nginx:alpine`** — нет bash, контейнер падает. Использовать `nginx:stable`. → **R-04**.
4. **`node:latest`** — недетерминированный. Использовать `node:22-alpine`. → **R-05**.
5. **Двух-фронтенд**: admin (`webvue3`) и public (`karaoke-public`) — **разные приложения**.
6. **Сырой JDBC + recordhash** (никакого JPA/Hibernate). → guard в tools/check-no-jpa-imports.sh.
7. **Per-feature документ** обновлять при правке кода фичи (FR-009).
8. **Git push через VPN** — может упасть `EOF` / `400 Bad request`.
9. **CI блокирует merge** при любом failing check.
10. **«Доступ — только онлайн»** (см. оферту). НЕ упоминать MP4/скачивание. → **R-11**.
11. **Санитайзер идемпотентен** (`SanitizePath.kt`, `Extentions.kt`). → **R-08**.

### Ловушки конфигурации сборки (R-46)

1. **Корень сборки — не место для зависимостей.** У корневого проекта нет `src/`,
   а `implementation` **не наследуется** в subprojects: правка зависимости в
   корневом `build.gradle.kts` меняет ровно ничего в собираемом приложении.
   Правило: зависимости — только в build-файлах модулей.
2. **Явная версия зависимости перебивает BOM.** `jackson-databind:2.14.2` рядом
   с `jackson-core:2.19.2` — смешанный стек одного семейства. Правило: версии
   семейства задаёт BOM, в build-файле версии нет.
3. **`validateDistributionUrl=true` — не проверка целостности.** Она проверяет
   доступность URL. Без `distributionSha256Sum` Gradle распаковывает ~130 МБ
   бинарника и запускает его без сверки с официальной суммой.
4. **`-XX:HeapDumpPath=/tmp/…` — дамп читает любой пользователь.** Права файла
   дампа = `0666 & ~umask` (обычно 0644), а в дампе — `System.getenv()`, то есть
   секреты окружения сборки. Правило: каталог `0700` внутри `GRADLE_USER_HOME`
   + `umask 077` → файл `0600` (см. `tools/gradle-heap-dump.sh`).
5. **Один репозиторий — один Gradle.** CI и `deploy/do.sh` собирают корневым
   wrapper'ом; если в модуле другой wrapper, «зелёная сборка» не означает, что
   разработчик проверяет то же самое.
6. **`pom.xml` в корне Gradle-проекта — не безобидный legacy.** IDE
   автоимпортирует его как отдельный Maven-проект (с другими версиями
   зависимостей и Java-target), а `mvn package` собирает отдельный артефакт.
   Правило: в корне Gradle-проекта Maven-дескрипта нет.
7. **Версия Gradle ниже, чем умеет тулчейн.** Kotlin 2.2.20 / Spring Boot
   3.5.6 не проверены на Gradle 9.x: «выровнять версии» ≠ «обновить версию».
   Правило: смена версии — отдельный PR с зелёной сборкой; guard держит
   минимум.
8. **Guard, обходящий каталог `find`'ом, цепляет чужие checkout'ы.** В главном
   checkout'е Karaoke лежат `.worktrees/*` — отдельные git-worktree со своими
   `gradle-wrapper.properties`. Обход всего каталога видел их и давал ложные
   нарушения (в CI, где checkout свежий, этого нет). Правило: список файлов
   для проверки — из `git ls-files` (ровно то, что видит CI) либо с явным
   исключением вложенных репозиториев.

## Когда добавлять правило

**Добавить новый раздел** здесь если:
- Это правило **повторяется** в 2+ файлах (AGENTS.md, CLAUDE.md, constitution.md).
- Это правило **может быть проверено** через grep/guard-скрипт.
- Изменение в **исходном коде** (Dockerfile, package.json, build.gradle.kts)
  может нарушить это правило.

**Не добавлять** если:
- Это **knowledge-fact** (не правило, а описание).
- Это **специфичная настройка** одной машины (→ AGENTS.md § «Машинно-специфичные исключения»).
- Это **experiment-specific** (Pass 379+: можно в `specs/<NNN>-<slug>/`).

## Compliance

- [ ] Каждое правило в формате `Rule/Protocol/Failure/Enforcement`.
- [ ] Source-of-truth указан (файл + строка или ADR).
- [ ] cross-reference в соответствующий ADR или domain компонент.
- [ ] Правило enforce'ится через guard-скрипт **ИЛИ** задокументировано почему нет.
