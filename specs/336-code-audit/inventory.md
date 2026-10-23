# Инвентаризация кода Karaoke и нарезка на порции аудита

- Карта решений: #336 «Полный аудит кода Karaoke».
- Тикет: OpenProject #338 «[wayfinder:research] #336 Инвентаризация кода и нарезка на порции аудита».
- Снимок: `master` @ `1977838a9b80ff5a3fc58f630ab991d6e9b67cc7` (2026-10-23).
- Режим: только чтение. Исходники, конфиги и БД не изменялись; ветки и коммиты не создавались.
- Артефакты: `specs/336-code-audit/inventory.md`, `specs/336-code-audit/coverage-ledger.csv`.

## 1. Методика и команды

### 1.1. Что считается кодом

Охват — каталоги `karaoke-app`, `karaoke-web`, `karaoke-db`, `karaoke-public`, `webvue3`, `alignment-ml`, `deploy`, `config`, `tools`, `.github`, плюс корневые build-файлы (`build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `pom.xml`, `gradle/wrapper/gradle-wrapper.properties`) и `alignment-ml/requirements.txt`.

Включённые типы: `.kt`, `.kts`, `.java`, `.vue`, `.js`, `.mjs`, `.cjs`, `.ts`, `.tsx`, `.jsx`, `.py`, `.sh`, `.bash`, `.sql`, `.yml`, `.yaml`, `.conf`, `.properties`, `.xml`, `.json`, `.html`, `.css`, `.scss`, `.service`, `.timer`, `.template`, `.example`, `Dockerfile*`, а также исполняемые файлы без расширения `deploy/karaoke-app/files/demucs{2,5,7}`, `deploy/prod-single-host/{80to8897,5443to5000}`, `karaoke-app/src/test/kotlin/1`.

Это шире, чем «только языковой код»: в охват сознательно попали Thymeleaf-шаблоны (server-rendered админка), проектные CSS/JS из `src/main/resources/static`, nginx/systemd/compose-конфиги и build-файлы. Vendored-библиотеки, минифицированный код, lock-файлы и сгенерированные артефакты исключены (см. § 6).

### 1.2. Подсчёт строк

Число строк — ровно `wc -l`, то есть количество символов перевода строки. Для файла без завершающего `\n` последняя строка не считается, как и в `wc -l`. Проверка в § 7 пересчитывает каждый файл тем же способом и сверяет с CSV.

### 1.3. Классификация prod/test и модулей

- `test`: путь содержит `/src/test/`, `/test/`, `/tests/`, `/e2e/`, `__tests__`, имя содержит `.test.`/`.spec.`, либо это `playwright.config.js`. Всё остальное — `prod`.
- Модуль: верхний каталог; `.github` → `ci`; корневые build-файлы → `config`; `alignment-ml/requirements.txt` → `alignment-ml`.
- Тестовые XML-фикстуры (`2.xml`, `Правильный.xml`, `Неправильный.xml`, `1`) классифицированы как `test`, хотя это данные: они лежат в `src/test` и входят в охват по условию.

### 1.4. Нарезка

- Файл не разрывается между порциями.
- Файл > 2 000 строк — отдельная порция (даже если она больше 4 000 строк).
- Остальные файлы режутся последовательно по пути, с приоритетом закрытия на границе каталога: порция закрывается при ≥ 3 300 строк, жёсткий предел — 4 000 строк.
- Границы модуля и признака prod/test не пересекаются. Хвост < 2 000 строк по возможности перебалансирован с предыдущей порцией; группы с файлом > 2 000 строк не сливаются ни с чем.
- Итог: 78 порций. Отдельных порций из одного файла > 2 000 строк — 16. По размеру: > 4 000 строк — 7 (крупные файлы), 2 000–4 000 — 62, < 2 000 — 9 (малые модули `karaoke-db`, `ci`, `config`, `alignment-ml` и хвосты, где слияние превысило бы 4 000).

### 1.5. Команды

Сбор файлов и LOC (сокращённо; полный набор расширений — в § 1.1):

```bash
find karaoke-app karaoke-web karaoke-db karaoke-public webvue3 alignment-ml deploy config tools .github \
  -type f \( -name '*.kt' -o -name '*.vue' -o -name '*.js' -o -name '*.ts' -o -name '*.py' \
             -o -name '*.sh' -o -name '*.sql' -o -name '*.yml' -o -name '*.html' \
             -o -name 'Dockerfile*' -o -name '*.xml' -o -name '*.json' \) \
| grep -Ev '/(node_modules|\.venv|build|dist|target|logs|\.git|\.idea|sockjs-client|checkpoints|whisper_models|ollama_data)/' \
| grep -Ev '\.min\.(js|css)$|package-lock\.json$' \
| while read -r f; do printf '%s\t%s\n' "$(wc -l < "$f")" "$f"; done
```

Плюс точечно, вне этого сокращённого `find`: `karaoke-app/src/test/kotlin/1`, `deploy/karaoke-app/files/demucs{2,5,7}`, `deploy/prod-single-host/{80to8897,5443to5000}`, `deploy/**/*.service`, `*.timer`, `*.template`, `*.example`.

Нарезка и ведомость построены одним детерминированным скриптом на Python (обход `os.walk`, те же исключения, `bytes.count(b"\n")` = `wc -l`, жадная упаковка с границами каталогов). Скрипт не сохранялся как артефакт: его результат — CSV, по которому § 7 проверяет полноту.

## 2. Сводка

**Всего: 1 032 файла, 259 827 строк.** Из них production — 948 файлов / 237 494 строки, test — 84 файла / 22 333 строки.

| Модуль | Файлов | Строк | prod файлов | prod строк | test файлов | test строк |
|---|---:|---:|---:|---:|---:|---:|
| `karaoke-app` | 412 | 126 983 | 358 | 107 626 | 54 | 19 357 |
| `webvue3` | 200 | 67 453 | 180 | 65 231 | 20 | 2 222 |
| `karaoke-public` | 105 | 26 407 | 98 | 25 952 | 7 | 455 |
| `karaoke-web` | 89 | 16 274 | 86 | 15 975 | 3 | 299 |
| `deploy` | 147 | 12 140 | 147 | 12 140 | 0 | 0 |
| `tools` | 55 | 8 430 | 55 | 8 430 | 0 | 0 |
| `alignment-ml` | 10 | 1 310 | 10 | 1 310 | 0 | 0 |
| `config` | 10 | 430 | 10 | 430 | 0 | 0 |
| `karaoke-db` | 3 | 32 | 3 | 32 | 0 | 0 |
| `ci` | 1 | 368 | 1 | 368 | 0 | 0 |
| **Итого** | **1032** | **259 827** | **948** | **237 494** | **84** | **22 333** |

В `deploy` — 63 SQL-файла; всего SQL — 76: ещё 12 учебных запросов в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/docs` и 1 в `tools/cleanup-test-songs.sql`. Тестовых конфигов Playwright — 2.

## 3. Порции аудита

Порций: **78**. Размер: минимум 32, максимум 9004, среднее 3331 строк. Распределение: < 2 000 — 9; 2 000–4 000 — 62; > 4 000 — 7.

Ведомость `coverage-ledger.csv` — 1 032 строки данных + заголовок; колонки `portion_id,portion_name,module,file_path,kind,loc,notes`; `loc` — `wc -l`.

| ID | Название | Модуль | kind | Файлов | Строк |
|---|---|---|---|---:|---:|
| P-01 | karaoke-app: root — build + base utils | `karaoke-app` | prod | 18 | 3 037 |
| P-02 | karaoke-app: HealthReport.kt [крупный файл] | `karaoke-app` | prod | 1 | 2 837 |
| P-03 | karaoke-app: root — Karaoke* core | `karaoke-app` | prod | 11 | 2 168 |
| P-04 | karaoke-app: root — process/queue | `karaoke-app` | prod | 7 | 3 924 |
| P-05 | karaoke-app: KaraokeProperties.kt [крупный файл] | `karaoke-app` | prod | 1 | 2 383 |
| P-06 | karaoke-app: root — storage/sync/stem | `karaoke-app` | prod | 9 | 3 256 |
| P-07 | karaoke-app: Utils.kt [крупный файл] | `karaoke-app` | prod | 1 | 5 510 |
| P-08 | karaoke-app: controllers — admin API | `karaoke-app` | prod | 24 | 3 942 |
| P-09 | karaoke-app: controllers/ApiController.kt [крупный файл] | `karaoke-app` | prod | 1 | 8 381 |
| P-10 | karaoke-app: controllers/MainController.kt [крупный файл] | `karaoke-app` | prod | 1 | 2 026 |
| P-11 | karaoke-app: controllers + dto + docs | `karaoke-app` | prod | 31 | 3 464 |
| P-12 | karaoke-app: llm + mlt | `karaoke-app` | prod | 19 | 3 903 |
| P-13 | karaoke-app: mlt/mko | `karaoke-app` | prod | 21 | 3 897 |
| P-14 | karaoke-app: mlt/mko + model | `karaoke-app` | prod | 21 | 3 538 |
| P-15 | karaoke-app: model (1) | `karaoke-app` | prod | 19 | 3 526 |
| P-16 | karaoke-app: model (2) | `karaoke-app` | prod | 14 | 3 622 |
| P-17 | karaoke-app: model (3) | `karaoke-app` | prod | 18 | 3 917 |
| P-18 | karaoke-app: model/Song.kt [крупный файл] | `karaoke-app` | prod | 1 | 9 004 |
| P-19 | karaoke-app: model (4) | `karaoke-app` | prod | 20 | 3 728 |
| P-20 | karaoke-app: model (5) | `karaoke-app` | prod | 20 | 3 684 |
| P-21 | karaoke-app: monitor + health/schedulers | `karaoke-app` | prod | 24 | 3 329 |
| P-22 | karaoke-app: services (1) | `karaoke-app` | prod | 12 | 3 960 |
| P-23 | karaoke-app: services (2) | `karaoke-app` | prod | 17 | 3 903 |
| P-24 | karaoke-app: services (3) | `karaoke-app` | prod | 13 | 3 366 |
| P-25 | karaoke-app: cleanup + textfiles + static | `karaoke-app` | prod | 21 | 3 708 |
| P-26 | karaoke-app: templates | `karaoke-app` | prod | 10 | 3 576 |
| P-27 | karaoke-app: templates/songs.html [крупный файл] | `karaoke-app` | prod | 1 | 3 120 |
| P-28 | karaoke-app: templates/songs2.html [крупный файл] | `karaoke-app` | prod | 1 | 2 454 |
| P-29 | karaoke-app: resources/test.xml (prod) — уточнено при аудите: это образец запроса ЕГРН, а не MLT-шаблон | `karaoke-app` | prod | 1 | 463 |
| P-30 | karaoke-app (test): fixtures + PlaywrightTests | `karaoke-app` | test | 3 | 3 449 |
| P-31 | karaoke-app (test): root + model | `karaoke-app` | test | 34 | 3 955 |
| P-32 | karaoke-app (test): services + cleanup | `karaoke-app` | test | 15 | 2 392 |
| P-33 | karaoke-app (test): Неправильный.xml [крупный файл] | `karaoke-app` | test | 1 | 7 674 |
| P-34 | karaoke-app (test): Правильный.xml | `karaoke-app` | test | 1 | 1 887 |
| P-35 | karaoke-web: root + config + public API | `karaoke-web` | prod | 23 | 3 930 |
| P-36 | karaoke-web: public controllers — payment/player/share | `karaoke-web` | prod | 12 | 3 615 |
| P-37 | karaoke-web: public auth + payment services | `karaoke-web` | prod | 30 | 3 377 |
| P-38 | karaoke-web: share links + storage | `karaoke-web` | prod | 13 | 2 476 |
| P-39 | karaoke-web: templates | `karaoke-web` | prod | 8 | 2 577 |
| P-40 | karaoke-web (test): services + util | `karaoke-web` | test | 3 | 299 |
| P-41 | karaoke-db: build + Main.java | `karaoke-db` | prod | 3 | 32 |
| P-42 | karaoke-public: root + components | `karaoke-public` | prod | 26 | 3 734 |
| P-43 | karaoke-public: components + composables | `karaoke-public` | prod | 37 | 3 929 |
| P-44 | karaoke-public: player/KaraokePlayer.js [крупный файл] | `karaoke-public` | prod | 1 | 3 876 |
| P-45 | karaoke-public: views + store | `karaoke-public` | prod | 15 | 3 972 |
| P-46 | karaoke-public: views/EditorWorkView.vue [крупный файл] | `karaoke-public` | prod | 1 | 2 001 |
| P-47 | karaoke-public: views (1) | `karaoke-public` | prod | 9 | 3 878 |
| P-48 | karaoke-public: views (2) | `karaoke-public` | prod | 3 | 2 344 |
| P-49 | karaoke-public: views + router | `karaoke-public` | prod | 6 | 2 218 |
| P-50 | karaoke-public (test): unit + e2e | `karaoke-public` | test | 7 | 455 |
| P-51 | webvue3: root + Albums | `webvue3` | prod | 15 | 3 495 |
| P-52 | webvue3: Chat + Authors | `webvue3` | prod | 11 | 3 600 |
| P-53 | webvue3: Common — HealthReport/Monitor | `webvue3` | prod | 21 | 3 741 |
| P-54 | webvue3: Processes + News | `webvue3` | prod | 12 | 3 948 |
| P-55 | webvue3: Processes — edit/filter | `webvue3` | prod | 12 | 3 575 |
| P-56 | webvue3: Properties + Publish + SiteUsers | `webvue3` | prod | 20 | 3 957 |
| P-57 | webvue3: SongEditor + SiteUsers/edit | `webvue3` | prod | 10 | 3 996 |
| P-58 | webvue3: SongEditor + Songs/edit | `webvue3` | prod | 6 | 3 908 |
| P-59 | webvue3: components/Songs/SongsTable.vue [крупный файл] | `webvue3` | prod | 1 | 2 552 |
| P-60 | webvue3: Songs/edit + Stats | `webvue3` | prod | 14 | 3 939 |
| P-61 | webvue3: components/Songs/edit/SongEdit.vue [крупный файл] | `webvue3` | prod | 1 | 6 492 |
| P-62 | webvue3: components/Songs/edit/SubsEdit.vue [крупный файл] | `webvue3` | prod | 1 | 6 561 |
| P-63 | webvue3: components/Songs/store.js [крупный файл] | `webvue3` | prod | 1 | 3 023 |
| P-64 | webvue3: Stats + Subscriptions | `webvue3` | prod | 15 | 3 542 |
| P-65 | webvue3: views + utils + composables | `webvue3` | prod | 32 | 3 723 |
| P-66 | webvue3: player/KaraokePlayer.js [крупный файл] | `webvue3` | prod | 1 | 4 021 |
| P-67 | webvue3: views + router | `webvue3` | prod | 7 | 1 158 |
| P-68 | webvue3 (test): e2e + unit | `webvue3` | test | 20 | 2 222 |
| P-69 | alignment-ml: pipeline (train/align/serve) | `alignment-ml` | prod | 10 | 1 310 |
| P-70 | deploy: root + SQL + docker | `deploy` | prod | 52 | 3 962 |
| P-71 | deploy: SQL migrations | `deploy` | prod | 30 | 3 986 |
| P-72 | deploy: legacy new_comp + SQL tail | `deploy` | prod | 27 | 2 081 |
| P-73 | deploy: prod-single-host + web-server-deploy | `deploy` | prod | 38 | 2 111 |
| P-74 | config: detekt/ktlint + root build | `config` | prod | 10 | 430 |
| P-75 | tools: checks (1) | `tools` | prod | 27 | 3 985 |
| P-76 | tools: checks + git hooks | `tools` | prod | 17 | 2 179 |
| P-77 | tools: tracker (2) | `tools` | prod | 11 | 2 266 |
| P-78 | ci: lint workflow | `ci` | prod | 1 | 368 |

## 4. Крупнейшие файлы

| # | Строк | Модуль | kind | Файл |
|---:|---:|---|---|---|
| 1 | 9 004 | `karaoke-app` | prod | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` |
| 2 | 8 381 | `karaoke-app` | prod | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` |
| 3 | 7 674 | `karaoke-app` | test | `karaoke-app/src/test/kotlin/Неправильный.xml` |
| 4 | 6 561 | `webvue3` | prod | `webvue3/src/components/Songs/edit/SubsEdit.vue` |
| 5 | 6 492 | `webvue3` | prod | `webvue3/src/components/Songs/edit/SongEdit.vue` |
| 6 | 5 510 | `karaoke-app` | prod | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` |
| 7 | 4 021 | `webvue3` | prod | `webvue3/src/player/KaraokePlayer.js` |
| 8 | 3 876 | `karaoke-public` | prod | `karaoke-public/src/player/KaraokePlayer.js` |
| 9 | 3 120 | `karaoke-app` | prod | `karaoke-app/src/main/resources/templates/songs.html` |
| 10 | 3 023 | `webvue3` | prod | `webvue3/src/components/Songs/store.js` |
| 11 | 2 837 | `karaoke-app` | prod | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt` |
| 12 | 2 552 | `webvue3` | prod | `webvue3/src/components/Songs/SongsTable.vue` |
| 13 | 2 454 | `karaoke-app` | prod | `karaoke-app/src/main/resources/templates/songs2.html` |
| 14 | 2 383 | `karaoke-app` | prod | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt` |
| 15 | 2 026 | `karaoke-app` | prod | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt` |
| 16 | 2 001 | `karaoke-public` | prod | `karaoke-public/src/views/EditorWorkView.vue` |
| 17 | 1 973 | `karaoke-app` | prod | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt` |
| 18 | 1 950 | `webvue3` | prod | `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` |
| 19 | 1 916 | `karaoke-app` | test | `karaoke-app/src/test/kotlin/1` |
| 20 | 1 887 | `karaoke-app` | test | `karaoke-app/src/test/kotlin/Правильный.xml` |

Крупные XML-файлы (`Неправильный.xml`, `Правильный.xml`, `2.xml`, `1`) — тестовые MLT-фикстуры, не исполняемый код; они оставлены в охвате как `test` по условию «всё в `src/test`». `Song.kt` (9 004) и `ApiController.kt` (8 381) — крупнейшие production-файлы, каждый образует отдельную порцию.

## 5. Порции с максимальным риском

| ID | Строк | Категория риска | Почему |
|---|---:|---|---|
| P-09 | 8 381 | данные / API / конкурентность | `controllers/ApiController.kt` — 8 381 строка: админский API, CRUD/массовые операции, SSE, storage, sync, процессы. Второй по размеру файл проекта после `Song.kt`; отдельная порция. |
| P-35 | 3 930 | публичный периметр / auth / деньги | `PublicApiController.kt` (1 297), `SecurityConfig`, `SiteAuthInterceptor`, `PublicAuthController`, `PublicCartController`, `PublicChatController`, `MainController`. |
| P-36 | 3 615 | публичный периметр / деньги | `PublicPaymentController`, `PublicSubscriptionController`, `PublicPlayerController`, `PublicOgSongController`, `PublicShareController`, `PublicSongEditorController` и `PublicSongeditorController` (имена различаются только регистром). |
| P-37 | 3 377 | деньги / auth / конкурентность | `PaymentService` (ЮKassa, чеки 422-ФЗ), `PriceService`, `SiteUserTokenService`, `RateLimitInterceptor`, `EventsBuffer`, `PublicVkIdAuthController`. |
| P-04 | 3 924 | данные / конкурентность | `KaraokeProcess`, `KaraokeProcessWorker`, DTO/статусы, `MediaInfo`: очередь, потоки, переходы состояний. |
| P-70 | 3 962 | данные / деньги | SQL: `16_cart_and_orders`, `15_monetization`, `14_site_user_premium_sources`, `07_public_settings`, `recordhash_*`; плюс docker/deploy-обвязка. |
| P-61 | 6 492 | данные / потеря при сохранении | `Songs/edit/SongEdit.vue` — 6 492 строки, жизненный цикл save/target; рядом e2e `save-race`. |
| P-63 | 3 023 | данные / гонки | `Songs/store.js` — 3 023 строки состояния редактора/списка песен. |

Порядок чтения для аудита: сначала P-35 → P-36 → P-37 (публичный периметр и деньги), затем P-09 (админский API и данные), P-04 (очередь/конкурентность), P-70 (SQL), P-61/P-63 (сохранение редактора). Это рекомендация по риску, а не требование протокола.

## 6. Исключённые пути и причины

| Путь / шаблон | Причина | Масштаб (где считался) |
|---|---|---|
| `archive/` | Архив; по условию вне охвата | 43 файла |
| `node_modules/`, `.venv/`, `build/`, `dist/`, `target/`, `.gradle/`, `.build/`, `.kotlin/`, `.npm-cache/` | Зависимости и артефакты сборки | не считались |
| `logs/`, `playwright-report/`, `test-results/`, `coverage/` | Артефакты прогонов | не считались |
| `.git/`, `.idea/`, `.codegraph/`, `.scratch/` | Служебные/мета-каталоги | не считались |
| `docs/`, `knowledge/`, `specs/`, `openspec/`, `.specify/` | Документация и слой спецификаций, не код | не считались |
| `webvue3/src/lib/sockjs-client/` | Vendored SockJS 1.6.x: свой package-lock, тесты, minified jQuery | 103 файла / 29 631 строка |
| `webvue3/src/assets/js/{sockjs,stomp,stompjs,jquery-3.6.0.min}.js` | Vendored/minified сторонние библиотеки | 4 файла / 12 526 строк |
| `karaoke-app/src/main/resources/static/{sockjs,stomp,bootstrap*,jquery*}` | Vendored/minified сторонние библиотеки | 5 файлов / 5 734 строки |
| `**/package-lock.json`, `yarn.lock` | Сгенерированные lock-файлы | 2 файла / 7 903 строки |
| `alignment-ml/checkpoints/` | Артефакты обучения (trainer_state.json и чекпоинты) | 26 файлов / 38 647 747 строк |
| `deploy/whisper_models/`, `deploy/ollama_data/`, model-каталоги | Веса моделей и бинарные блобы | бинарные, не считались |
| `deploy/new_comp/sm-karaoke-system/dumps/karaoke_clear_dump.sql` | Сгенерированный дамп БД, не миграция | 1 файл / 3 316 строк |
| `deploy/.env`, `deploy/do.env`, `.../deploy/.env`, `.../deploy/do.env`, `htpasswd.example`, `*.crt` | Секреты/сертификаты; содержимое не читалось | 9 файлов / 342 строки |
| `*.ttf`, `*.png`, `*.jpg`, `*.ico`, `*.mp3`, `*.jar`, блобы моделей | Бинарные ассеты | не считались |
| `deploy/Заметки`, `deploy/new_comp/.../Слова_с_буквой_ё.txt` | Заметки/данные, не код | 2 файла |
| `karaoke-vue/`, `web/` | Легаси-каталог только с 3 бинарными ассетами; `web/` пуст | кода нет |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` | Сгенерированная обёртка Gradle; `gradle-wrapper.properties` включён | не считались |
| `*.min.js`, `*.min.css` | Минифицированный код | не считались |

Исключения не входят в 1 032 файла и 259 827 строк. В частности, `deploy/whisper_models/models--mobiuslabsgmbh--faster-whisper-large-v3-turbo/blobs/*` — бинарные веса; часть файлов недоступна на чтение (600/root), но для инвентаризации кода они не нужны.

## 7. Сходимость

Проверка после записи артефактов:

```text
файлов в inventory:            1 032
строк в inventory:             259827
строк в coverage-ledger.csv:   259827
уникальных file_path в CSV:    1 032
дублей file_path:              0
порций:                        78
сумма LOC по порциям:          259827
файлов с расхождением wc -l:   0
```

Команда проверки (читает только артефакты и файлы на диске):

```python
import csv, os, collections
rows = list(csv.DictReader(open('specs/336-code-audit/coverage-ledger.csv', encoding='utf-8')))
print('files', len(rows), 'loc', sum(int(r['loc']) for r in rows))
print('unique', len({r['file_path'] for r in rows}))
per = collections.Counter()
for r in rows: per[r['portion_id']] += int(r['loc'])
print('portions', len(per), 'sum', sum(per.values()))
bad = [r['file_path'] for r in rows
       if open(r['file_path'],'rb').read().count(b'\n') != int(r['loc'])]
print('mismatch_wc', len(bad))
```

Ожидаемый вывод: `files 1032 loc 259827`, `unique 1032`, `portions 78 sum 259827`, `mismatch_wc 0`.

## 8. Не классифицировано

Нет. Все 1 032 файла охвата получили модуль и признак prod/test; всё, что не вошло, перечислено в § 6 с причиной. Единственный пограничный случай — `deploy/new_comp/sm-karaoke-system/Karaoke.properties` (0 строк): включён как пустой конфиг legacy-контура, порция P-72.
