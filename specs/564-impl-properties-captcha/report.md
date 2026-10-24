# Отчёт по #359 и #362 — Закрыть публичную запись в `tbl_public_settings` и fail-open проверку капчи (BR-019 + BR-518, спека 564)

> **Спека**: [`../559-close-public-properties-captcha/spec.md`](../559-close-public-properties-captcha/spec.md) ·
> **Ветка**: `564-impl-properties-captcha` | **Дата**: 2026-10-24
> **Аудит**: #336, находки P-35-01 / **BR-019** (severity **S1**) и P-35-02 / **BR-518** (severity **S1**).
> Отчёт публикуется в **обе** задачи: #359 закрывает вход (запись/выдача настроек),
> #362 — поведение проверки капчи и место хранения секрета. Цепочка замкнута только целиком
> (FR-016).

## Что было

Две связанные дыры на `karaoke-web` (прод-сервер, публичный API):

1. **BR-019 (#359)** — весь префикс `/api/properties/**` был доступен анонимно из интернета:
   - `SecurityConfig` помечает `permitAll()` и `/api/**`, и `anyRequest()`;
   - `SiteAuthInterceptor` (единственная проверка прав в этом сервисе) этот путь не покрывает —
     он зарегистрирован только для `/api/public/account/**`, `/api/public/auth/me|logout`,
     `/api/siteusers/**`;
   - `POST /api/properties/setproperty` писал **любой** ключ в `tbl_public_settings` (таблица
     живой прод-БД) без какой-либо проверки;
   - `GET /api/properties/digest` отдавал **все** строки таблицы, включая
     `yandexSmartCaptchaServerKey` — серверный секрет проверки капчи.
2. **BR-518 (#362)** — проверка капчи была **fail-open**: `YandexCaptchaValidationService.validate()`
   при пустом серверном ключе возвращала «проверка пройдена» (`return true`), а серверный ключ
   лежал в той же публично изменяемой таблице и кэшировался на 5 минут без инвалидации.

Итог — замкнутая цепочка: аноним пишет в публичные настройки → обнуляет/подменяет ключ
капчи → регистрация проходит без капчи → массовая регистрация аккаунтов. Починка только
одной половины цепочки обход не закрывает.

## Что изменено

| Файл | Что |
|---|---|
| `karaoke-web/.../controllers/PublicSettingsWebController.kt` | Периметр доступа к `tbl_public_settings`. **Запись** `POST /setproperty` требует общий секрет в заголовке `X-Internal-Secret` (**fail-safe**: сравнение только при непустом настроенном секрете — ловушка образца `InternalStemJobController`, research D2) + сетевой фильтр nginx по адресу админской машины. Отказ → `403`, тело `false` (прежний формат), **ни одной выполненной SQL-команды**, запись `settings:security:denied` в категорию `infra.settings.security` (время, адрес источника, причина; без тела запроса и без значения настройки). **Чтение** — по белому списку: `SECRET_KEYS` (`yandexSmartCaptchaServerKey`) не выдаются **никогда** (ни анонимно, ни по защищённому каналу), `PUBLIC_KEYS` (`yandexSmartCaptchaClientKey`) — анонимно, операционные (kill-switch, шаблоны новостей) — только по защищённому каналу (анонимно пустая строка). Дайджест — анонимно только публичные ключи, по защищённому каналу все, кроме секретных. Запись секретного ключа отклоняется (`reason=secret_key_write`). Текст ошибки БД больше не уходит наружу. Добавлены тест-швы `handleSetProperty` / `handleGetProperty` / `handleDigest` (поддельное JDBC-соединение в unit-тестах). |
| `karaoke-web/.../services/CaptchaConfigService.kt` | **Серверный ключ капчи переехал в env** (`captcha.server-key` ← `CAPTCHA_SERVER_KEY`), читается при старте, кеша нет (окно устаревания = 0). **Клиентский** ключ остался в `tbl_public_settings`, но окно кеша сокращено с 5 минут до 60 секунд (5-минутное окно делили оба ключа). Добавлен `isServerKeyConfigured()` — диагностика «настроена/не настроена» без раскрытия секрета (FR-014) и запись в журнал `infra.captcha.config` при старте. |
| `karaoke-web/.../services/YandexCaptchaValidationService.kt` | Проверка стала **fail-closed** и вернула не `Boolean`, а четыре исхода: `PASSED` / `FAILED` / `NOT_CONFIGURED` (ключ не задан) / `UNAVAILABLE` (внешний сервис недоступен). Пустой ключ больше **не** означает «проверка пройдена». Значение ключа не логируется. |
| `karaoke-web/.../services/CaptchaValidationResult.kt` (NEW) | Перечисление исходов проверки с кодом отказа для публичного API (`captcha` / `captcha_not_configured`). |
| `karaoke-web/.../controllers/PublicAuthController.kt` | Регистрация отклоняется при любом исходе, кроме `PASSED`. Для незаконной конфигурации — **отдельный код** `captcha_not_configured` (решение владельца, п. 6); формат ответа прежний — `400` + `{"error": …}` (FR-018). |
| `karaoke-web/src/main/resources/application.yml` | Новая секция `publicsettings.internal-secret: ${SYNC_INTERNAL_SECRET:}` (тот же общий server-to-server секрет — **новый секрет не заводится**, решение владельца, п. 2) и параметр `captcha.server-key: ${CAPTCHA_SERVER_KEY:}`. |
| `deploy/prod-single-host/80to8897` | Новый `location /api/properties/setproperty`: `allow 185.26.28.109; allow 83.137.50.231; allow 185.26.28.199; deny all;` + проксирование на `127.0.0.1:8897` с теми же заголовками и таймаутами, что у `/changerecords`. nginx выбирает location по **наибольшему совпадению префикса** — фильтр действует только на путь записи, чтение остаётся доступным (проверено на стенде). |
| `deploy/docker-compose-web.yml`, `deploy/prod-single-host/docker-compose-web.yml`, `deploy/web-server-deploy/deploy/docker-compose-web.yml` | env `CAPTCHA_SERVER_KEY=${CAPTCHA_SERVER_KEY}` прокинут в контейнер `karaoke-web`. |
| `deploy/prod-single-host/do.env.example` | Шаблон `CAPTCHA_SERVER_KEY=CHANGE_ME` (значение — только на боевом хосте, не в git). |
| `deploy/karaoke-db/51_public_settings_drop_captcha_server_key.sql` (NEW) | Удаление строки `yandexSmartCaptchaServerKey` из `tbl_public_settings` — таблица больше не хранит серверный секрет (FR-011). **Выполняется владельцем после инвентаризации.** |
| `karaoke-public/src/views/RegisterView.vue` | Код `captcha_not_configured` → сообщение «Регистрация временно недоступна. Попробуйте позже.»; исправлён устаревший комментарий «регистрация всё равно пройдёт». |
| `karaoke-web/src/test/.../PublicSettingsWebControllerTest.kt` (NEW) | 15 регрессионных тестов «до/после» (периметр записи и границы выдачи). |
| `karaoke-web/src/test/.../YandexCaptchaValidationServiceTest.kt` (NEW) | 6 тестов «до/после» (fail-closed, четыре исхода, секрет не логируется). |
| `karaoke-web/src/test/.../PublicAuthControllerCaptchaTest.kt` (NEW) | 4 теста «до/после» (коды отказа регистрации, прежний формат ответа). |
| `docs/features/public-settings-perimeter.md` (NEW) | Per-feature документ (FR-009 конституции). |
| `knowledge/domains/karaoke-web/components/public-controllers-6.md` | Контракт `PublicSettingsWebController`: периметр, белый список ключей, коды ответа. |
| `knowledge/domains/karaoke-web/components/config.md` | Новые параметры `publicsettings.internal-secret` / `captcha.server-key`; предупреждение, что `permitAll()` на `/api/**` — не точка авторизации. |
| `knowledge/domains/karaoke-web/components/services-overview.md` | Контракты `CaptchaConfigService` (env + клиентский из таблицы) и `YandexCaptchaValidationService` (четыре исхода). |
| `knowledge/domains/monitoring/components/log-categories.md` | Зарегистрированы категории `infra.settings.security` и `infra.captcha.config`. |
| `knowledge/domains/integration/components/external-api-clients.md` | Раздел про `YandexCaptchaValidationService`: fail-closed и место хранения ключа. |
| `specs/124-news-flags-backfill/quickstart.md` | Действующий операционный регламент переведён на защищённый канал (старый curl без секрета больше не работает). |

**Не менялось** (по условиям спеки): модель «ключ — значение» и сама таблица; админский
сценарий `webvue3` («Настройки сайта» → `/api/publicsettings/*` в `karaoke-app` — другой
сервис и другой путь); формат ответа `GET /api/public/auth/config`; формат успешной
регистрации; формат успешной записи настройки (`true`/`false`) и чтения (`String`); внешний
сервис проверки капчи, его прокси-маршрут и таймауты; клиентский (публичный) ключ капчи в
таблице.

## Проверки

| Проверка | Результат |
|---|---|
| `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:compileKotlin :karaoke-app:compileKotlin --parallel` | **PASS** (BUILD SUCCESSFUL) |
| `GRADLE_USER_HOME=… ./gradlew :karaoke-web:test :karaoke-app:test` | **PASS** — `karaoke-web` 46 тестов (0 failures, 0 errors), `karaoke-app` 433 теста (0 failures, 0 errors, 6 skipped — до правки) |
| `PublicSettingsWebControllerTest` | **15/15 PASS** |
| `YandexCaptchaValidationServiceTest` | **6/6 PASS** |
| `PublicAuthControllerCaptchaTest` | **4/4 PASS** |
| `GRADLE_USER_HOME=… ./gradlew ktlintCheck` | **PASS** (все модули, 0 нарушений) |
| `nginx -t` (контейнер `nginx:stable`, изменённый `deploy/prod-single-host/80to8897`) | **PASS** — `syntax is ok` / `test is successful` (проверено с временным самоподписанным сертификатом) |
| Проверка выбора location nginx (longest-prefix) | **PASS** (стенд): `/api/properties/setproperty` → отфильтрованный location; `getproperty`/`digest`/прочие → общий `/api/` |
| Регресс «до/после» (снят рубеж приложения) | **PASS**: 7 из 15 тестов `PublicSettingsWebControllerTest` падают (ожидается `403`, а запись выполняется) |
| Регресс «до/после» (возвращён fail-open капчи) | **PASS**: 4 из 10 тестов капчи падают |
| `git diff master...HEAD \| grep -inE 'secret\s*=\s*["'][0-9a-f]{16,}'` | **пусто** — в изменениях только имена параметров и `CHANGE_ME` |
| `git ls-files \| grep -iE '\.env$\|do\.env$\|\.key$\|\.pem$\|\.p12$\|\.pfx$'` | **пусто** — секрет-файлы не отслеживаются |
| `bash tools/check-knowledge-structure.sh` | **9/9 OK** |
| `python3 tools/lint-knowledge.py` | **OK** — «All documentation passes architectural style and structural checks» |
| `python3 tools/check-doc-references.py` | **OK** (4047 ссылок, 2180 путей, 306 эндпоинтов) |
| `bash tools/check-feature-doc.sh docs/features/*.md` | **OK** |
| `bash tools/check-subagent-isolation.sh` | **OK** (ветка без чужих коммитов) |
| `tools/check-no-jpa-imports.sh` / `check-no-mp4-mentions.sh` / `check-docker-image-tags.sh` | **PASS** |
| `python3 tools/check-ssot-impact.py` | **OK** (правил SSoT для изменённых файлов нет) |

### Известные особенности (не по этой правке)

1. **Kotlin-демон не стартует** в этом окружении (`/home/nsa/.local/share/kotlin` — read-only
   FS). Gradle сам переключается на «compile without Kotlin daemon» и сборка проходит; на
   результат проверок это не влияет.
2. **nginx-конфиг требует сертификат**: для `nginx -t` в контейнере нужен файл
   `/etc/keys/www.sm-karaoke.ru.crt` — в проверке использован временный самоподписанный
   (в репозиторий не попадает, каталог удалён).
3. **Сценарий «регистрация проходит» не проверяется unit-тестом**: следующий шаг — создание
   пользователя в БД, а unit-тест не должен трогать базу. Проверка самого исхода `PASSED` —
   в `YandexCaptchaValidationServiceTest`; остальная ветка регистрации не менялась.
4. **Админка `webvue3` ходит в `karaoke-app`, а не в `karaoke-web`** (`webvue3/nginx_webvue3.conf`
   проксирует `/api` в `karaoke-app:8899`). Раздел «Настройки сайта» →
   `/api/publicsettings/digest|update` — другой сервис, правкой не затронут. Свойства
   `KaraokeProperties` (файловый слой) — тоже другой слой: `/api/properties/setproperty` в
   `karaoke-app` пишет в файл, а не в таблицу.

## Поведение после фикса

| Ситуация | До | После |
|---|---|---|
| Анонимная запись настройки (любой ключ) | значение записывалось в прод-БД, ответ `true` | `403`, тело `false`, ни одной SQL-команды, `settings:security:denied` |
| Запись без заголовка / с неверным секретом | то же | `403`, `reason=missing_secret` / `invalid_secret` |
| Секрет не настроен (пусто) + пустой заголовок | то же | `403`, `reason=secret_not_configured` (fail-safe) |
| Запись по защищённому каналу (секрет + адрес админской машины) | — | `200`, `true`, значение изменено, кеш чтения сброшен |
| `GET /api/properties/digest` анонимно | все строки, включая `yandexSmartCaptchaServerKey` | только публичные ключи |
| `GET /api/properties/getproperty?key=yandexSmartCaptchaServerKey` | значение секрета | пустая строка (в любом режиме) |
| `GET /api/properties/getproperty?key=newsAutoPublishKillSwitch` анонимно | значение | пустая строка; по защищённому каналу — значение |
| Регистрация при пустом серверном ключе капчи | проходила **без капчи** | `400`, `{"error":"captcha_not_configured"}` |
| Регистрация при недоступном внешнем сервисе | тихий пропуск | `400`, `{"error":"captcha"}` |
| Регистрация при настроенной капче и решённом токене | работала | работает (сценарий не менялся) |
| Смена ключа капчи | до 5 минут из-за кеша | ключ в env: действует сразу после рестарта контейнера |

## Что осталось за владельцем

1. **Инвентаризация `tbl_public_settings`** (обязательна **до** развёртывания правки):
   снимок всех строк (`key`, `value`, `description`, `last_update`), проверка значения
   `yandexSmartCaptchaServerKey`/`yandexSmartCaptchaClientKey` и `newsAutoPublishKillSwitch`,
   поиск неопознанных ключей, удаление добавленных анонимно. Регламент — шаги B1.1–B1.3 в
   [`quickstart.md`](quickstart.md). Снимок снимается прямым SQL (psql): через API значение
   серверного ключа больше не выдаётся намеренно.
2. **Ротация ключа капчи**, если на инвентаризации найдено подозрительное значение
   (инцидент безопасности): новая пара ключей в панели Yandex SmartCaptcha, старое
   значение аннулировано.
3. **Выдача `CAPTCHA_SERVER_KEY`**: значение — в `do.env` на боевом хосте (шаблон —
   `deploy/prod-single-host/do.env.example`) → env контейнера `karaoke-web`. В репозиторий,
   в сборку и в журналы значение не попадает. **Порядок**: сначала env, потом миграция
   таблицы, потом деплой/рестарт, иначе регистрация будет отклоняться с
   `captcha_not_configured` (ожидаемое fail-closed-поведение, не поломка).
4. **Миграция** `deploy/karaoke-db/51_public_settings_drop_captcha_server_key.sql` —
   удаление строки с серверным ключом (после инвентаризации).
5. **Деплой `karaoke-web`** (новый образ) + рестарт контейнера, чтобы сервис подхватил
   значение ключа из env. Перед рестартом — проверить очередь заданий (AGENTS.md, Pass
   380/381): на боевом хосте `karaoke-app` не развёрнут, но правило действует для
   админской машины.
6. **Обновление nginx на боевом хосте** (`/etc/nginx/sites-enabled/80to8897` ←
   `deploy/prod-single-host/80to8897`) + `nginx -t` + `nginx -s reload`. Конфиг уже
   проверен синтаксически в контейнере `nginx:stable`.
7. **Операционный риск**: адрес админской машины у провайдера плавает. Пока новый адрес не
   добавлен в `allow`-список nginx, операция с него **не пройдёт** — ожидаемое поведение, а
   не регрессия (компенсируется вторым рубежом — заголовком `X-Internal-Secret`).
8. **Наблюдение после деплоя** — 7 суток (SC-001…SC-011): ни одной успешной анонимной
   записи, ни одной выдачи серверного секрета, ни одной регистрации без проверки, все
   отказы видны в журнале в течение 60 секунд.
9. **Шаги 4–6 workflow по AGENTS.md** (`tracker.sh add-comment` / `mark-review` / `close-issue`)
   выполняет лид после merge — в момент написания отчёта они не выполнены.

## Что НЕ сделано (вне объёма)

- **Вторая раскладка nginx** (`deploy/web-server-deploy/deploy/80to8897`) — тот же конфиг
  без нового `location`; правка туда не вносилась (объём спеки 564 —
  `deploy/prod-single-host/80to8897`), требует отдельного решения владельца.
- **`karaoke-app` (`/api/publicsettings/update`, `/api/publicsettings/digest`)** — служебный
  сервис, на проде **не развёрнут** (ADR-0004), поэтому публичного пути записи в таблицу не
  создаёт. Доступ к нему — административный контур; отдельная задача.
- **Файловый слой `KaraokeProperties`** (`/api/properties/setproperty` в `karaoke-app`) —
  другой слой (файл на админской машине), не таблица `tbl_public_settings`.
- **Регрессионный тест «регистрация проходит»** на уровне контроллера — не сделан
  сознательно (создание пользователя пишет в БД; unit-тест базу не трогает).
- **Разбор уже накопленного ущерба** (аккаунты, созданные без проверки) — по условию спеки
  вне объёма.

## Ветка и коммиты

- Ветка: `564-impl-properties-captcha` (от `master` = `cf97b82f`).
- Реализация выполнялась в отдельном worktree (`/home/nsa/Karaoke/.worktrees/impl359`) —
  Constitution IX.3 / Pass 379.
- Коммиты (все с маркерами `[tracker-claim-359]` / `[tracker-claim-362]`):
  - `0152d9d4` — spec(564): research/plan/tasks/quickstart + контракт защищённого канала;
  - `e4d0af82` — feat(564): реализация (код, тесты, nginx, compose, миграция, per-feature
    документ, knowledge, регламент спеки 124);
  - третий коммит — docs(564): этот отчёт (хех — в `git log`, он меняется при amend'е).
