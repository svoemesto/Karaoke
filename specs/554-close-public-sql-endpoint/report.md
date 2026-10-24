# Отчёт по #357 — Закрыть публичный доступ к `POST /changerecords` (BR-011, спека 554)

> **Спека**: [spec.md](spec.md) | **Ветка**: `554-close-public-sql-endpoint` |
> **PR**: [#647](https://github.com/svoemesto/Karaoke/pull/647) — **MERGED** 2026-10-24 | **Дата**: 2026-10-24 |
> **Аудит**: #336, порция P-35, находка P-35-01 / BR-011 (severity **S1**).

## Что было

`POST https://sm-karaoke.ru/changerecords` на `karaoke-web` — по контракту внутренний
server-to-server эндпоинт двух-БД синхронизации песен — был доступен анонимно из
интернета:

- host-nginx проксировал `location /changerecords` без `allow`/`deny`
  (`deploy/prod-single-host/80to8897:74–90`);
- в приложении не было проверки прав: `SecurityConfig` — `anyRequest().permitAll()`,
  `SiteAuthInterceptor` этот путь не покрывает; единственная проверка — расшифровка
  фиксированной константы `Crypto.WORDS_TO_CHECK` (шифр AES/CBC без MAC, ключ по
  умолчанию — захардкоженный в репозитории, если env `CRYPTO_AES_KEY` не задан);
- тело запроса задаёт и SQL-действия: `UPDATE $tableName SET $setTextDecrypted WHERE id = $idRecord`.

Итог — неаутентифицированный канал исполнения произвольного SQL в боевой БД.
Дополнительно: `catch` в обработчике возвращал наружу `e.message!!` — текст ошибки БД
(имена таблиц/колонок) уходил клиенту.

## Что изменено

| Файл | Что |
|---|---|
| `karaoke-web/.../controllers/MainController.kt` | `POST /changerecords` требует заголовок `X-Internal-Secret` (`syncDenialReason`, **fail-safe**: сравнение только при непустом настроенном секрете — research D2). Отказ → `403` + короткое тело `Forbidden`, ни одной выполненной SQL-команды, запись `sync:security:denied` в категорию `infra.sync.security` (время, источник, причина `missing_secret`/`invalid_secret`/`secret_not_configured`; без тела запроса). `catch` больше не отдаёт `e.message!!` — нейтральный `5xx` + текст ошибки только в журнал. Тело обработчика вынесено в `handleChangeRecords` с тест-швом `connectionProvider`. |
| `karaoke-web/src/main/resources/application.yml` | Новый параметр `sync.internal-secret: ${SYNC_INTERNAL_SECRET:}` (рядом с `stemjobs:`), с комментарием про назначение и fail-safe. |
| `karaoke-app/.../services/SyncRemoteClient.kt` | В каждый запрос добавляется заголовок `X-Internal-Secret` (значение — `Karaoke.syncInternalSecret`). Ответ `403` — отказ по правам, **повтор не выполняется**; сетевые ошибки — по-прежнему одна повторная попытка. WARN `sync:remote:secret_missing`, если секрет не настроен. |
| `karaoke-app/.../KaraokeProperties.kt` | Свойство `syncInternalSecret` (default `""`) в реестре `listKaraokeProperties`. |
| `karaoke-app/.../Karaoke.kt` | Доступ `Karaoke.syncInternalSecret` (get/set). |
| `deploy/docker-compose-web.yml`, `deploy/prod-single-host/docker-compose-web.yml`, `deploy/web-server-deploy/deploy/docker-compose-web.yml` | env `SYNC_INTERNAL_SECRET=${SYNC_INTERNAL_SECRET}` прокинут в контейнер `karaoke-web` (по образцу `STEMJOBS_INTERNAL_SECRET`). |
| `deploy/prod-single-host/do.env.example` | Шаблон переменной `SYNC_INTERNAL_SECRET=CHANGE_ME` (значение — только на боевом хосте, не в git). |
| `deploy/prod-single-host/80to8897` | `location /changerecords`: `allow 185.26.28.109; allow 83.137.50.231; allow 185.26.28.199; deny all;` — тот же список, что у внутренних портов 5433/9000 (`nginx.conf:22–32, 41–50`), с комментарием про плавающий адрес. |
| `karaoke-web/src/test/.../MainControllerChangeRecordsTest.kt` (NEW) | 7 регрессионных тестов «до/после»: без заголовка → 403 и SQL не выполнено; с корректным заголовком → действия применяются; пустой секрет + пустой заголовок → 403; неверный секрет → 403; в ответе нет текста ошибки БД; HTTP-уровень (MockMvc) → 403; отказ есть в журнале `infra.sync.security` и без тела запроса. |
| `karaoke-app/src/test/.../SyncRemoteClientTest.kt` | +2 теста: заголовок `X-Internal-Secret` уходит в запрос; `403` → повтора нет, возвращается `false`. Существующие 7 тестов ретрая не изменены. |
| `docs/features/internal-secret-sync.md` (NEW) | Per-feature документ (FR-009): назначение, стороны, контракт, fail-safe, порядок выдачи и ротации секрета. |
| `knowledge/domains/karaoke-web/components/main-controller.md` | Контракт `POST /changerecords` (заголовок, коды ответа, fail-safe, второй рубеж nginx). |
| `knowledge/domains/karaoke-web/components/config.md` | Где задаётся новый секрет (`sync.internal-secret` / env `SYNC_INTERNAL_SECRET`). |
| `knowledge/domains/processing/components/two-db-sync.md` | Клиент отправляет `X-Internal-Secret`; `403` без повтора. |
| `knowledge/domains/monitoring/components/log-categories.md` | Зарегистрирована категория `infra.sync.security`. |
| `specs/336-code-audit/findings/P-35.md`, `specs/336-code-audit/registry.csv` | Статус BR-011 → «исправлено» (`work_package` = 3577). |

**Не менялось** (по условиям спеки): формат тела запроса, кодовое слово, состав и порядок
SQL-действий, транзакционность применения (аудит BR-012 — отдельная задача), другие
эндпоинты, публичное поведение сайта.

## Проверки

| Проверка | Результат |
|---|---|
| `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:compileKotlin :karaoke-app:compileKotlin --parallel` | **PASS** (BUILD SUCCESSFUL) |
| `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:test :karaoke-app:test` | **PASS** — 454 теста, 0 failures, 0 errors (6 skipped — до правки) |
| `MainControllerChangeRecordsTest` | **7/7 PASS** |
| `SyncRemoteClientTest` | **9/9 PASS** (7 существующих + 2 новых) |
| `GRADLE_USER_HOME=... ./gradlew ktlintCheck` | **PASS** (все модули, 0 новых нарушений) |
| `nginx -t` (контейнер `nginx:stable`, изменённый `deploy/prod-single-host/80to8897`) | **PASS** — `syntax is ok` / `test is successful` |
| `git diff master...HEAD \| grep -inE 'secret\s*=\s*["'][0-9a-f]{16,}'` | **пусто** — в изменениях только имена параметров и `CHANGE_ME` |
| `pre-commit run --files <изменённые>` | 18 хуков — **18 PASS** (после пересборки истории ветки; см. «Известные проблемы», п. 1) |
| CI PR #647 (12 джобов) | **все PASS**: ktlint, ESLint+Prettier (webvue3, karaoke-public), Docs, KDoc, JSDoc, Knowledge SSoT (impact+structure), baseline stats, guard'ы (docker-tags, no-jpa, no-mp4) |
| `tools/check-knowledge-structure.sh` | **9/9 OK** |
| `tools/check-knowledge-cross-links.sh` | **OK** (680 ссылок) |
| `python3 tools/lint-knowledge.py --baseline …` | **OK** |
| `python3 tools/check-doc-references.py` | **OK** (3985 ссылок) |
| `bash tools/check-feature-doc.sh docs/features/*.md` | **OK** |
| `tools/check-no-jpa-imports.sh` / `check-no-mp4-mentions.sh` / `check-docker-image-tags.sh` | **PASS** |

### Известные проблемы (не по этой правке)

1. **`subagent-isolation-guard` (Pass 379+) — устранено лидом.** Изначально ветка была
   создана от `553-336-code-audit` и делила с ней коммиты аудита (#336) — гейт падал.
   Лид пересобрал историю: ветка перенесена на `master`, в неё вошли только 554-коммиты
   (спека + реализация + отчёт); артефакты аудита остались в `553-336-code-audit` (PR #646).
   Гейт после пересборки: **PASS** («все commits уникальны»).
2. **Линтеры фронтов** (`webvue3`, `karaoke-public`) не запускались: `node_modules` в
   worktree отсутствуют, фронтовые файлы правка не трогает. В CI эти джобы отработают.
3. **Регрессионный тест «до/после»**: на коде **до** правки файл тестов не компилируется
   (нет `handleChangeRecords` и параметра `syncInternalSecret`) — т.е. сборка падает,
   регрессия ловится. При частичном откате (снятие только проверки секрета) тесты
   падают по существу: ожидается 403, а SQL выполняется.

## Поведение после фикса

| Ситуация | До | После |
|---|---|---|
| Запрос без заголовка `X-Internal-Secret` | SQL-действия выполнялись, ответ `OK` | `403` + `Forbidden`, ни одной выполненной команды, запись `sync:security:denied` |
| Запрос с неверным секретом | то же | `403`, `reason=invalid_secret` |
| Секрет не настроен (пусто) + пустой заголовок | то же | `403`, `reason=secret_not_configured` (fail-safe) |
| Запрос с корректным секретом | `OK` | `OK` — формат ответа прежний |
| Ошибка БД при применении | наружу текст ошибки БД (`e.message!!`) | `5xx` + `Internal error`, детали — в журнале |
| Ответ `403` клиенту | — | повтор не выполняется (`sync:remote:denied`) |
| Сетевая ошибка клиента | 1 повтор | 1 повтор (без изменений) |

## Источник в журнале отказа

В записи `sync:security:denied` адрес источника берётся из `X-Real-IP` (его выставляет
**наш** nginx: `proxy_set_header X-Real-IP $remote_addr;`, `deploy/prod-single-host/80to8897:79`),
фолбэк — `request.remoteHost`. `X-Forwarded-For` в этом месте **не используется**: этот
заголовок клиент подделывает (аудит BR-003 «xff-first-element»), поэтому адрес из XFF в
журнале безопасности не является доказательством. Общий `ClientIpResolver` в этой точке
намеренно не используется (его правка — отдельная задача BR-003).

## Что осталось за владельцем

1. **Выдать и раздать секрет** (одно значение на обе стороны):
   - боевой хост: `SYNC_INTERNAL_SECRET=<значение>` в `do.env` (шаблон —
     `deploy/prod-single-host/do.env.example`) → env контейнера `karaoke-web`;
   - админская машина: свойство `syncInternalSecret` в `Karaoke.properties`
     (`Karaoke.syncInternalSecret`).
   Значение в репозиторий не попадает; в журналы не пишется.
2. **Обновить nginx на боевом хосте** (`/etc/nginx/sites-enabled/80to8897` ←
   `deploy/prod-single-host/80to8897`) и проверить `nginx -t` + `nginx -s reload`.
   Конфиг уже проверен синтаксически в контейнере `nginx:stable`.
3. **Задеплоить `karaoke-web`** (новый образ с проверкой секрета) и перезапустить
   `karaoke-app` на админской машине (в нём теперь читается `Karaoke.syncInternalSecret`).
   Порядок: сначала сервер, затем клиент — иначе синхронизация временно откажется (403).
4. **Операционный риск**: адрес админской машины у провайдера плавает. Пока новый адрес
   не добавлен в `allow`-список nginx, синхронизация с него **не пройдёт** — это
   ожидаемое поведение, а не регрессия (компенсируется вторым рубежом — внутренним
   ключом — и своевременным добавлением адреса). В `allow`-списке сейчас три исторических
   адреса (решение владельца от 2026-10-07).
5. **Наблюдение после деплоя** — 7 суток (SC-001…SC-004): ни одного внешнего исполнения,
   ни одного неуспешного обмена, все отказы видны в журнале, ни одного ответа с текстом
   ошибки БД.
6. **За рамками этой задачи** (рекомендации находки P-35-01, отдельные задачи волны A):
   смена ключей `CRYPTO_AES_*` и удаление legacy-fallback в `Crypto`, белый список таблиц
   для `dataUpdate`/`dataCreate`, транзакционность применения (BR-012).

## Что НЕ сделано (вне объёма)

- Второй вариант раскладки nginx — `deploy/web-server-deploy/deploy/80to8897` — содержит
  такой же `location /changerecords` **без** фильтра. Правка туда не вносилась (объём
  спеки 554 — `deploy/prod-single-host/80to8897`); требует отдельного решения лида/владельца.
- `tracker.sh add-comment` / `mark-review` для #357 выполняет лид после merge (шаги 4–5
  workflow по AGENTS.md) — в момент написания отчёта ещё не выполнены.

## Ветка и коммиты

- Ветка: `554-close-public-sql-endpoint` → PR **#647** → `master` (merge-коммит `40d08982`).
- Состав ветки (5 коммитов): спека (`1a3196d2`), уточнение FR-005 по решению владельца
  (`24043b1c`), план/research/контракт/data-model/quickstart/tasks (`2285dfa5`),
  реализация с тестами, конфигурацией, nginx, документацией и knowledge (`5a32102f`),
  отчёт (`6dd7b3c6`) — все с маркером `[tracker-claim-357]`.
- Реализация выполнялась в отдельном worktree (`git worktree add`), как требует
  Constitution IX.3 / Pass 379; по завершении worktree удалён.
- Обновление карточки аудита (`specs/336-code-audit/findings/P-35.md`, `registry.csv`)
  перенесено в ветку `553-336-code-audit` (PR #646), чтобы фикс-PR не зависел от артефактов
  аудита, которых ещё нет в master.
