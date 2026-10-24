# Черновик бэклога правок по результатам аудита (#336)

> **Статус: черновик на утверждение владельца.** Ничего не заведено в трекере, код не тронут.
> Пакеты — группировка записей реестра по механизму (одна правка закрывает несколько BR).

## Сводка

| Severity | Пакетов | Записей BR |
|---|---:|---:|
| S1 | 6 | 6 |
| S2 | 22 | 159 |
| S3 | 23 | 460 |
| **Всего** | **51** | **625** |

## Волны

| Волна | Содержание | Пакетов | BR |
|---|---|---:|---:|
| A | S1 — публичный периметр, безопасность, деньги (по одной задаче, полный цикл со спекой) | 6 | 6 |
| B | Сетка доверия: guard-скрипты и тесты, которые не проверяют заявленное (чиним первыми после A) | 6 | 44 |
| C | Операционные S2: деплой, бэкапы, платежи, синхронизация, безопасность периметра | 9 | 53 |
| D | Функциональные S2 по модулям | 12 | 103 |
| E | Остаток S3 (гигиена, краевые случаи) | 18 | 419 |

## Связанные локации (находки, слитые с основной записью)

| BR | Дополнительные локации |
|---|---|
| BR-003 | локации из P-38: `DebugDbAccessGuard` (IP-allowlist `/api/public/debug/db`), rate-limit claim’ов share-ссылок, `client_ip_hash` в БД |
| BR-060 | локации из P-06/P-07: `SyncAudioDescendants.kt:173, 333`, `Utils.kt` (одноразовые `thread {}` без `closeThreadConnection()`) |
| BR-123 | локации из P-43/P-52/P-54/P-57/P-64: `CustomConfirm.vue` → `v-html` (email, имя картинки, автор/словарь/новость), `SyncTable.vue` (ошибка БД с email) |
| BR-202 | локация из P-39: `karaoke-web/.../templates/filter.html:308–310` (`${songAuthors}` вместо `${authors}`) |
| BR-312 | локации из P-43/P-58/P-62/P-65: `useKaraokeEditor.js` (karaoke-public), `useReviewModalFormat.js`, `SubsEdit.vue:1703, 4073–4083`, `SongKaraokeEditorView.vue:317` |
| BR-324 | локация из P-52: `webvue3/src/components/Chat/store.js:164` — первое сообщение в новом диалоге не появляется |
| BR-457 | локация из P-65: `webvue3/src/composables/useKaraokeEditor.js:152–177` (квадратичный `specTagAnchors`) |
| BR-499 | корневой `gradle/wrapper/gradle-wrapper.properties:3` (Gradle 8.14) — та же проблема версий wrapper’ов |
| BR-507 | локации из P-68: e2e и unit-тесты `webvue3` не выполняются в CI (тот же корень «в CI нет сборки и тестов») |
| BR-525 | локации из P-68: `webvue3/playwright.config.js:29–35` (ветки `process.env.CI` — мёртвый код), 14 файлов `webvue3/e2e/*.spec.js`, 5 файлов `webvue3/src/**/__tests__/*`; НОВОЕ в P-68-01: unit-тесты фронта (`node --test`) не имеют скрипта запуска в `webvue3/package.json` |
| BR-531 | `karaoke-app/src/test/kotlin/1` (1 916 строк) — тот же MLT с путями `~/Documents/Караоке/…`, потребителей нет (P-30, «Сверки» п.1); `karaoke-app/src/test/kotlin/Неправильный.xml` (7 674 строки) — второй такой же MLT с путями `~/Documents/Караоке/Ю-Питер/…` и `~/Documents/SpaceBox4096/…`, потребителей нет (P-33-01) |
| BR-551 | тот же корень «секрет в argv»: BR-552 (`deploy/tracker-db-backup.sh`, `docker exec -e PGPASSWORD`), BR-605 (`tools/tracker-lib.sh` — токен OpenProject в `-H "Authorization: Basic …"`), P-75-01 (`tools/analyze-prod-incident.sh` — пароль БД в connection string) |
| BR-573 | зеркала из P-76 «Сверки»: registry-smoke (пустой каталог → PASS), check-ssot-impact (неразрешимая база → exit 0), lint-knowledge (шаблон не загружен → проверка отключена) |
| BR-578 | зеркало из P-76: `tools/generate-eslint-baseline.sh:57` — абсолютный `filePath` в ключе baseline (тот же корень) |
| BR-581 | зеркало из P-76: `tools/registry-vendor.sh:52` — `DOCKER_CONFIG=/home/nsa/Karaoke/.docker` (тот же корень «жёсткий путь») |

## Пакеты

### PKG-BR-011 — `POST /changerecords` — публичный endpoint, который исполняет присланный клиентом SQL. Ни `SecurityConfig`, ни…

- **Severity:** S1 · **модули:** karaoke-web · **BR:** 1
- **Волна:** A · **Режим:** полный цикл со спекой
- **Файлов:** 1 · **Риск:** данные/безопасность

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-011 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt:303` | SEC | убрать маршрут с публичного периметра (nginx `allow` только для admin-IP, как у `stream`-портов 5433/9000), авторизацию перевести на общий internal-secret (как в `InternalStemJobCo… |

### PKG-BR-018 — полный доступ к стемам выдаётся по «скрытому жесту», который минтится публичным `POST /api/public/events` без…

- **Severity:** S1 · **модули:** karaoke-web · **BR:** 1
- **Волна:** A · **Режим:** полный цикл со спекой
- **Файлов:** 1 · **Риск:** публичный периметр, данные/безопасность

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-018 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlayerController.kt:99` | SEC | убрать выдачу полного токена из публичного beacon-эндпоинта либо проверять право (эфир/премиум/подписка/SKIP) в момент отдачи байт, а не только валидность токена; если жест нужен к… |

### PKG-BR-019 — весь контроллер `/api/properties/**` доступен анонимно (нет ни `SiteAuthInterceptor`, ни ограничения в `Securi…

- **Severity:** S1 · **модули:** karaoke-web · **BR:** 1
- **Волна:** A · **Режим:** полный цикл со спекой
- **Файлов:** 1 · **Риск:** публичный периметр, данные/безопасность

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-019 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicSettingsWebController.kt:50` | SEC | закрыть весь `/api/properties/**` административной аутентификацией (перехватчик/`SecurityConfig` matcher), вынести запись в admin-only путь; из `digest`/`getproperty` исключить сек… |

### PKG-BR-123 — 25 обработчиков подтверждения собирают `customConfirmParams.body` интерполяцией `this.song.songName` в HTML-ст…

- **Severity:** S1 · **модули:** webvue3 · **BR:** 1
- **Волна:** A · **Режим:** полный цикл со спекой
- **Файлов:** 1 · **Риск:** данные/безопасность

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-123 | `webvue3/src/components/Songs/edit/SongEdit.vue:5034` | SEC | не собирать HTML из имени — передавать имя отдельным текстовым VNode/слотом либо экранировать (`escapeHtml(songName)`) при формировании `body`; в `CustomConfirm` оставить `v-html` … |

### PKG-BR-258 — результаты и исходники премиум-задания «Создать минусовку» кладутся в тот же бакет `karaoke`, что и публичные…

- **Severity:** S1 · **модули:** karaoke-app · **BR:** 1
- **Волна:** A · **Режим:** полный цикл со спекой
- **Файлов:** 1 · **Риск:** данные/безопасность

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-258 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/StemJobProcessing.kt:193` | SEC | вынести объекты `stemjobs/` в отдельный приватный бакет (или приватный префикс с явным `Deny` на `stemjobs/*` в политике бакета) и отдавать их только через `PublicStemJobController… |

### PKG-BR-518 — проверка капчи **fail-open**: при пустом серверном ключе `validate()` возвращает `true` — регистрация проходит…

- **Severity:** S1 · **модули:** karaoke-web · **BR:** 1
- **Волна:** A · **Режим:** полный цикл со спекой
- **Файлов:** 1 · **Риск:** данные/безопасность

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-518 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/YandexCaptchaValidationService.kt:31` | SEC | при пустом/неconfigured серверном ключе **не пропускать** проверку, а отклонять регистрацию (или явно отключать endpoint регистрации флагом, а не «пустой ключ = пропустить»); ключ … |

### PKG-DB-RECORDHASH — Дрейф recordhash-триггеров и sync-таблиц

- **Severity:** S2 · **модули:** deploy · **BR:** 4
- **Волна:** C · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 4 · **Риск:** данные/безопасность, миграции БД, прод-конфигурация
- **Зависит от:** PKG-S2-08 (рабочий бэкап БД — prerequisite)

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-540 | `deploy/recordhash_processes.sql:19` | DATA | привести `deploy/recordhash_processes.sql` в соответствие с текущей схемой (`NEW.song_id` вместо `NEW.settings_id`) и с полным набором колонок; ввести правило «канонический recordh… |
| BR-541 | `deploy/recordhash_authors.sql:10` | DATA | привести `deploy/recordhash_authors.sql` к полному текущему набору колонок (как в `46_author_sort_order.sql` + `44_author_song_counts.sql` + `31_entity_description_fields.sql`) и д… |
| BR-553 | `deploy/karaoke-db/50_tbl_songs_free_after_on_air.sql:342` | DATA | в функции `update_tbl_songs_sync_recordhash()` вернуть префикс `NEW.` во всех колонках (взять эталон из `43_song_name_censored_sync.sql`); добавить в миграцию проверку (`DO $$ … IF… |
| BR-533 | `deploy/karaoke-db/31_entity_description_fields.sql:72` | DATA | отдельной миграцией добавить в `tbl_songs_sync` те же колонки, что в `tbl_songs` (`status_process_demo`, `id_tariff`, `description`, `short_description`, `warning`) — по образцу `4… |

### PKG-ERR-EXITCODE — Код возврата внешней команды игнорируется

- **Severity:** S2 · **модули:** karaoke-app · **BR:** 2
- **Волна:** D · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 2 · **Риск:** данные/безопасность

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-029 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt:221` | ERR | дожидаться `waitFor()` с таймаутом, проверять `exitValue()`; при `!= 0` — `ERROR` с причиной и без выполнения последующих шагов; отдельно распознавать типовые ошибки docker/ffmpeg. |
| BR-161 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/PlayerMp4MuxService.kt:196` | DATA | проверять `waitFor()` != 0 и бросать исключение (или удалять недописанный `output.mp4`), логировать stderr-хвост; добавить `waitFor(timeout)` + `destroyForcibly()`; `executeRenderM… |

### PKG-MONEY-IDEMPOTENCY — Идемпотентность платежей и автопродления

- **Severity:** S2 · **модули:** karaoke-web · **BR:** 4
- **Волна:** C · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 4 · **Риск:** публичный периметр, данные/безопасность

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-014 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicCartController.kt:160` | DATA | порядок «создать платёж → при успехе удалить корзину/перевести подписки в PENDING → при отказе откатить»; ключ идемпотентности checkout — стабильный (например, orderId, выданный до… |
| BR-020 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPaymentController.kt:57` | DATA | выполнять смену статуса и fulfillment в одной транзакции с условным `UPDATE ... WHERE status <> 'PAID'` (проверять число обновлённых строк) либо ввести отдельный признак `fulfilled… |
| BR-021 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicSubscriptionController.kt:163` | RACE | переиспользовать существующий заказ для любого не-`canceled` статуса (`pending`/`waiting_for_capture`/`succeeded`) и обрабатывать `succeeded` как «подписка уже оплачена»; при `veri… |
| BR-514 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SubscriptionRenewalScheduler.kt:84` | DATA | сохранять `result.id` в `renewal.yookassaPaymentId`; выполнять «списание → фиксация» в одной транзакции с условным UPDATE (`WHERE status <> 'PAID'`) либо через отдельный признак `f… |

### PKG-RACE-PUBLISH — Гонка публикации (check-then-act)

- **Severity:** S2 · **модули:** karaoke-app · **BR:** 2
- **Волна:** D · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 2

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-062 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:6938` | RACE | сделать захват атомарным на уровне БД — `UPDATE tbl_songs SET telegram_auto_publish_state='PUBLISHING' WHERE id=? AND id_telegram_demo='' AND telegram_auto_publish_state<>'PUBLISHI… |
| BR-138 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramAutoPublishService.kt:74` | RACE | атомарно «захватывать» публикацию перед `sendVideo` (например, `UPDATE tbl_songs SET … = 'publishing' WHERE id = ? AND (state IS NULL OR state NOT IN ('publishing','rendering')) RE… |

### PKG-RACE-TARGET — Гонка чтения/записи целевого объекта

- **Severity:** S2 · **модули:** webvue3 · **BR:** 3
- **Волна:** D · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 3

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-397 | `webvue3/src/components/NewsTemplates/NewsTemplatesEditor.vue:301` | RACE | зафиксировать target на всю цепочку сохранения (`const target = this.target`), блокировать select/сохранение на время операции или отменять/игнорировать reload по поколению; перечи… |
| BR-407 | `webvue3/src/components/Promotions/store.js:39` | RACE | фиксировать target в замыкании запроса и отбрасывать ответ, если он не соответствует текущему `promoRulesTarget` (или вводить номер поколения); блокировать кнопки сохранения/удален… |
| BR-409 | `webvue3/src/components/ShareLinks/store.js:73` | RACE | помечать каждый запрос target и отбрасывать ответы с target, отличным от текущего; при смене target очищать/блокировать таблицу до прихода ответа; разрушительные действия выполнять… |

### PKG-RES-THREADLOCAL — Не закрываются соединения/потоки (ThreadLocal)

- **Severity:** S2 · **модули:** karaoke-app, karaoke-web · **BR:** 5
- **Волна:** C · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 5 · **Риск:** публичный периметр

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-006 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/EventsBuffer.kt:198` | PERF | убрать `close()` из EventsBuffer (оставить ThreadLocal-кеш как задумано) либо осознанно перейти на пул соединений; сейчас комментарий «вернётся в pool» описывает несуществующий пул… |
| BR-027 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicSettingsWebController.kt:67` | PERF | не закрывать соединение из `getConnection()` в HTTP-потоках; для одноразовой операции использовать отдельное соединение с `use { }` либо оставить кеш как есть. |
| BR-038 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt:733` | LEAK | в `finally` потока `doStart` вызывать `database.closeThreadConnection()` — поток одноразовый, кеш ThreadLocal переиспользовать некому (это тот же паттерн, что уже применён в `Karao… |
| BR-060 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:5752` | LEAK | обернуть тело каждого `thread {}` в `try { … } finally { WORKING_DATABASE.closeThreadConnection() }` (для `Connection.remote()` — соответствующий экземпляр), либо заменить `thread … |
| BR-116 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ChatController.kt:39` | PERF | не закрывать соединение в переиспользуемых потоках (полагаться на `ThreadLocal`-кэш) либо вызывать `closeThreadConnection()` только на одноразовых потоках; привести комментарии к ф… |

### PKG-S2-05 — guards/CI/.github (S2, 3)

- **Severity:** S2 · **модули:** .github · **BR:** 3
- **Волна:** B · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 1

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-507 | `.github/workflows/lint.yml:39` | BUILD | добавить в workflow джобы сборки и тестов (например `backend-build`: `./gradlew compileKotlin test`; `frontend-build`: `npm run build` в матрице SPA) либо явно зафиксировать в `AGE… |
| BR-509 | `.github/workflows/lint.yml:295` | BUILD | в этом job указать `with: fetch-depth: 0` (или ≥ 100) и проверять наличие remote-tracking ref перед подсчётом; при пустом `git log` завершать шаг с ошибкой, а не с успехом. |
| BR-510 | `.github/workflows/lint.yml:139` | BUILD | проверять оба каталога (`tools/check-feature-doc.sh docs/features/*.md archive/docs/features/*.md`) либо, если архив выведен из обслуживания, направить проверку на `docs/features/`… |

### PKG-S2-07 — alignment-ml: DATA, PERF — 3 BR

- **Severity:** S2 · **модули:** alignment-ml · **BR:** 3
- **Волна:** D · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 2 · **Риск:** данные/безопасность
- **Темы внутри:** blocking-sync-in-async, unbounded-upload-read, stale-vocab-reuse

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-489 | `alignment-ml/serve.py:53` | PERF | объявить обработчик `def align(...)` вместо `async def` (Starlette сам выполнит его в threadpool) **или** вынести вычисление в `await run_in_threadpool(align_syllables, tmp.name, t… |
| BR-490 | `alignment-ml/serve.py:68` | PERF | ограничить приём: проверять `Content-Length`/число прочитанных байт и длительность (`sf.info` до декодирования) с явным `HTTPException(413/415)`; читать тело **потоково** (`shutil.… |
| BR-491 | `alignment-ml/train.py:349` | DATA | привязать словарь к составу данных — хранить рядом с `vocab.json` метаданные (например, отпечаток набора текстов/список песен) и **перестраивать** словарь, если текущий набор не со… |

### PKG-S2-08 — deploy: BUILD, DATA, RACE, SEC — 15 BR

- **Severity:** S2 · **модули:** deploy · **BR:** 15
- **Волна:** C · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 14 · **Риск:** данные/безопасность, миграции БД, прод-конфигурация
- **Темы внутри:** migrations-not-from-scratch, secrets-in-compose, subscriptions-cascade, push-errors-ignored, authors-recordhash-loses-fields, backup-no-password…

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-039 | `deploy/karaoke-db/02_result_text_and_index.sql:1` | BUILD | развести «полный дамп» и «инкрементальные патчи»: `01` держать как актуальный полный дамп, а `02` либо удалить (его содержимое уже в `01`), либо переписать на `ADD COLUMN IF NOT EX… |
| BR-040 | `deploy/docker-compose-whisper.yml:32` | SEC | вынести оба значения в `deploy/do.env` (файл уже в `.gitignore`) и подставлять через `${WHISPER_API_KEY}` / `${SEARXNG_SECRET}`; ротировать оба секрета (значения попали в историю g… |
| BR-041 | `deploy/karaoke-db/15_monetization.sql:107` | DATA | для денежных таблиц — `ON DELETE RESTRICT` (запрет удаления пользователя с платежами) либо мягкое удаление/анонимизация `tbl_site_users` с сохранением `tbl_subscriptions`; каскад о… |
| BR-043 | `deploy/do.sh:420` | BUILD | добавить `|| { echo "ERROR: push …" >&2; return 1; }` (или `|| return $?`) на каждый `docker push` и на `do_registry_login`; в `deploy_*.sh` считать WARN «up to date» ошибкой депло… |
| BR-534 | `deploy/karaoke-db/46_author_sort_order.sql:15` | DATA | держать одну каноническую функцию `update_tbl_authors_recordhash()` (отдельный файл-источник, как `deploy/recordhash_authors.sql` для sync-движка) и пересобирать её из полного набо… |
| BR-539 | `deploy/prod-single-host/karaoke-db-backup.sh:50` | DATA | передавать пароль в pg_dump (`PGPASSWORD="$DB_SERVER_POSTGRES_PASSWORD" docker exec -e PGPASSWORD … pg_dump …` либо `postgresql://user:pass@localhost:5432/karaoke`) и убрать `--no-… |
| BR-542 | `deploy/tracker-docker-compose.yml:38` | SEC | убрать `network_mode: host` — публиковать порты явно на loopback (`127.0.0.1:5435:5435`, `127.0.0.1:8080:8080`), а доступ к UI трекера давать через SSH-туннель либо через уже настр… |
| BR-543 | `deploy/tracker-db-backup.service:11` | RACE | удерживать блокировку на весь прогон — `ExecStart=/usr/bin/flock -n /tmp/tracker-db-backup.lock /home/nsa/Karaoke/deploy/tracker-db-backup.sh` (и убрать `ExecStartPre`), либо испол… |
| BR-544 | `deploy/prod-single-host/nginx.conf:74` | SEC | `ssl_protocols TLSv1.2 TLSv1.3;` (в обоих файлах), раскомментировать `server_tokens off;`, при необходимости добавить `ssl_prefer_server_ciphers off;` для TLS 1.3 и HSTS (см. P-73-… |
| BR-545 | `deploy/prod-single-host/nginx.conf:11` | SEC | включить TLS на Postgres (`ssl=on` + сертификаты, `sslmode=require` у клиента) и, для MinIO, перейти с L4-`stream` на HTTPS-прокси с проверкой подписи (либо terminate TLS на stream… |
| BR-546 | `deploy/prod-single-host/docker-compose-web.yml:1` | BUILD | во все `docker-compose-*.yml` прод-хоста добавить `logging: {driver: json-file, options: {max-size: "50m", max-file: "5"}}` (как уже сделано для registry); в `prune-images.sh` допо… |
| BR-554 | `deploy/new_comp/sm-karaoke-system/deploy/docker-compose-web-new-comp.yml:9` | BUILD | переписать `environment` списком (как в `deploy/docker-compose-web.yml`), т.е. `WORK_IN_CONTAINER: 1` заменить на `- WORK_IN_CONTAINER=1`; добавить в CI проверку, что каждый `envir… |
| BR-555 | `deploy/new_comp/sm-karaoke-system/karaoke_watcher.sh:19` | SEC | ограничить каталог (каталог, доступный на запись только конкретному пользователю/группе, права на каталог `700`, файлы `700`), вынести bind-mount `/sm-karaoke/system` из контейнеро… |
| BR-556 | `deploy/new_comp/sm-karaoke-system/deploy/docker-compose-db-new-comp.yml:12` | SEC | публиковать порт БД только на loopback (`"127.0.0.1:8832:5432"`), как в `deploy/docker-compose-database.yml`; доступ из других машин — через SSH-туннель или L4-балансировщик с allo… |
| BR-557 | `deploy/prod-single-host/.env.example:21` | SEC | включить TLS на сервере PostgreSQL (`ssl=on` + сертификаты) и требовать его в строке подключения (`sslmode=verify-full`), либо закрыть канал туннелем (WireGuard/SSH) между админ-ма… |

### PKG-S2-09 — deploy: SEC — 1 BR

- **Severity:** S2 · **модули:** deploy · **BR:** 1
- **Волна:** C · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 1 · **Риск:** данные/безопасность, прод-конфигурация
- **Темы внутри:** no-security-headers-public

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-558 | `deploy/prod-single-host/80to8897:1` | SEC | добавить в `http{}`/server-блоки `add_header X-Frame-Options "SAMEORIGIN" always;` (либо `Content-Security-Policy "frame-ancestors 'self'"`), `add_header X-Content-Type-Options "no… |

### PKG-S2-10 — karaoke-app: DATA, ERR, PERF, RACE, SEC — 15 BR

- **Severity:** S2 · **модули:** karaoke-app · **BR:** 15
- **Волна:** D · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 4 · **Риск:** данные/безопасность
- **Темы внутри:** lost-update, create-process-race, chain-not-atomic, empty-args-loop, copyfields-field-injection, ssrf-albumcover…

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-030 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt:1139` | RACE | один писатель на процесс: прогресс — отдельным `UPDATE` только percentage-колонок и только `WHERE process_status='WORKING'`; терминальные поля — только из потока задания; поля, чит… |
| BR-031 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt:1027` | RACE | атомарный claim на стороне worker (`UPDATE … SET process_status='WORKING' WHERE id=? AND process_status='WAITING'` с проверкой числа строк); в `createProcess` не удалять `WAITING`/… |
| BR-032 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt:1941` | DATA | хранить `process_chain_id` и номер шага, не запускать шаг N+1, пока N не `DONE`, при `ERROR` останавливать цепочку; агрегированный статус песни обновлять по завершении всей цепочки… |
| BR-033 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt:1104` | ERR | валидировать `args` при создании/редактировании (непустой список непустых списков); в воркере пустые `args` переводить в `ERROR` с причиной, не роняя цикл и не блокируя лейн. |
| BR-048 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:612` | DATA | валидировать список полей на сервере по фиксированному whitelist'у копируемых полей (текст/маркеры/служебные поля контента) и явно отклонять `ID`, `ROOT_FOLDER`, `FILE_NAME` и проч… |
| BR-049 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:3173` | SEC | разрешать только `https`/`http` к явному allowlist'у доменов источников обложек (Яндекс.Музыка, SearXNG-прокси), блокировать loopback/приватные диапазоны и порты, ограничить `Conte… |
| BR-050 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:2952` | SEC | валидировать `rootFolder` на сервере (канонизировать `Path`, требовать префикс разрешённого корня библиотеки, запрещать `..`, симлинки и каталоги вне корня) и не принимать `rootFol… |
| BR-063 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:7042` | DATA | сохранять только явно изменённые поля через `saveToDbLocked()` (он берёт `SELECT … FOR UPDATE` и не перезаписывает чужие изменения), либо перечитывать песню непосредственно перед ф… |
| BR-064 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:4810` | PERF | спать только непосредственно перед фактическим `getLyricsSearch` (перенести sleep внутрь ветки 4851) и/или выполнять массовый поиск в фоне (executor + SSE-прогресс/итог, как путь B… |
| BR-071 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:4255` | ERR | валидировать аккорд до рендера (неизвестный аккорд — пропускать/помечать, а не `!!`); в `getFormattedChords` заменить `!!` на `?:` с сохранением исходного текста аккорда; для `getT… |
| BR-101 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:5410` | ERR | отличать «строка удалена» от «ошибка чтения» — `loadListFromDb`/`loadFromDbById` должны пробрасывать `SQLException` (или возвращать `Result`/`null` только при пустом `ResultSet`); … |
| BR-102 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:5652` | DATA | `saveToDb` должен возвращать результат (`Boolean`/`Int` из `executeUpdate`) и пробрасывать `SQLException`; `saveToDbLocked` — коммитить только при `updatedRows > 0` и возвращать `f… |
| BR-104 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:8974` | PERF | убрать запрос из `toDTO()`: заполнять `albumName` пакетно (`Album.getAlbumsByIds` уже существует и используется в `Zakroma.buildFromSongs`, `Album.kt:315`) на уровне сборки списка;… |
| BR-108 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:6345` | DATA | генерировать список колонок из единственного источника (список полей сущности/`SongField`), а не руками; добавить недостающие колонки, для `player_readiness_flags`/`audio_compare_h… |
| BR-109 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:5966` | SEC | валидировать `scPath` (белый список корней экспорта, `Path.normalize()` + проверка `startsWith(allowedRoot)`), санитайзить `scRenameTemplate` через `sanitizeSongFileName()` и запре… |

### PKG-S2-11 — karaoke-app: DATA, ERR, LEAK, PERF — 15 BR

- **Severity:** S2 · **модули:** karaoke-app · **BR:** 15
- **Волна:** D · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 14 · **Риск:** данные/безопасность
- **Темы внутри:** envelope-cache-unbounded, cleanup-plans-retained, listening-history-full-load, playwright-latch-no-timeout, assignment-collapse, executor-wakeup-loop-leak…

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-110 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/WaveformCompare.kt:55` | LEAK | ограничить кэш (LRU/`Caffeine` с `maximumWeight` по числу кадров) либо хранить огибающие только на время одного запроса; при смене mtime удалять прежний ключ того же пути. |
| BR-111 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/CleanupController.kt:34` | LEAK | ограничить число/возраст хранимых планов (TTL + LRU, удаление после `apply`/`status` в терминальном состоянии), либо хранить отчёт на диске/в БД, а в памяти — только метаданные. |
| BR-112 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ListeningHistoryController.kt:90` | PERF | перенести фильтр/сортировку/пагинацию в SQL (JOIN к `tbl_songs`, `ORDER BY`, `LIMIT/OFFSET`, отдельный `COUNT(*)`); грузить песни только для страницы. |
| BR-113 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsPlaywright.kt:41` | LEAK | вынести auth-flow из HTTP-потока (фоновая задача + статус), задать `await(timeout)` с гарантированным закрытием контекста по таймауту, заменить reassignment глобального latch на ло… |
| BR-128 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongAssignment.kt:157` | DATA | вернуть `Map<Long, List<SongAssignment>>` (или выбирать детерминированно: активное, иначе последнее по `id`/`assigned_at`) и подтягивать черновики для всех выбранных назначений; на… |
| BR-132 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/HealthReportBatchPool.kt:368` | LEAK | запускать воркеры один раз (`@PostConstruct`) и будить их через `LinkedBlockingQueue`/`Semaphore`/`Condition`, а не submit'ом новых циклов; либо хранить в пуле только короткоживущи… |
| BR-133 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/propertiesfiledictionary/PropertiesFileDictionary.kt:25` | DATA | кодировать значение (Base64/JSON на строку, как в `KaraokeProperties`), писать атомарно (tmp + `ATOMIC_MOVE`) под lock, а разбор делать устойчивым (пропускать битую строку с предуп… |
| BR-139 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramAutoPublishService.kt:301` | DATA | проверять `Boolean` и при `false` либо повторять запись (с коротким backoff), либо возвращать `SEND_FAILED` с явной ошибкой «message_id получен, но не сохранён», не оставляя состоя… |
| BR-140 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramApiClient.kt:258` | ERR | обернуть каждую попытку в `try/catch`, классифицировать ошибку (сеть/таймаут/5xx/не-JSON — retryable), спать по `backoffScheduleMs`, а на исчерпании возвращать `SEND_FAILED`; в `se… |
| BR-141 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageApiClient.kt:227` | DATA | не глотать ошибку — бросать исключение (как `downloadFile`) или возвращать явный результат; вызывающим проверять `null`/`Result` до перевода задания в `DONE`. Заодно развести «лока… |
| BR-142 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramApiClient.kt:126` | LEAK | держать прокси-клиент полем (как `directClient`), пересоздавать только при смене `telegramProxyUrl`; при пересоздании закрывать старый (`HttpClient.close()` на JDK 21+) либо исполь… |
| BR-143 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StatsDebugController.kt:96` | ERR | возвращать `ResponseEntity.ok(dto)` (тип `ResponseEntity<StatsDebugDto>`), либо отдавать `Map`, собранный явно; каст убрать. |
| BR-148 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishService.kt:37` | ERR | не кэшировать изменяемые свойства в конструкторах синглтонов — читать `KaraokeProperties` на каждом вызове (`warmup`, `uploadCover`) либо передавать в клиент `() -> Boolean`/`() ->… |
| BR-162 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeStorageService.kt:276` | ERR | убрать уборку из `init` (или обернуть в `runCatching` + WARN и сделать асинхронной после старта); ограничить уборку явным списком бакетов Karaoke; при удалении сохранять/восстанавл… |
| BR-176 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/KaraokeDbTable.kt:140` | DATA | в `save()` обрабатывать `null` явно (`ps.setNull(index, Types.TIMESTAMP/…/VARCHAR)` по типу свойства через `RecordDiff`/`KaraokeDbTableField`), а ошибку `executeUpdate()` не только… |

### PKG-S2-12 — karaoke-app: COMPAT, DATA, ERR, PERF, RACE, SEC — 15 BR

- **Severity:** S2 · **модули:** karaoke-app · **BR:** 15
- **Волна:** D · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 11 · **Риск:** публичный периметр, данные/безопасность
- **Темы внутри:** infinite-loop-unpublications, cache-key-no-params, no-pagination-publications, stomp-broker-missing, form-missing-fields, songs-page-no-pagination…

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-188 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Publication.kt:1542` | PERF | условие выхода должно читать живое состояние — `while (listStack.any { it.isNotEmpty() })` (или считать оставшиеся элементы и уменьшать счётчик на каждом `pop()`); убрать неиспольз… |
| BR-195 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/StatsCacheKey.kt:16` | DATA | включать фактические параметры запроса в `StatsCacheKey.params` (`target`, `days`, `mode`) либо не кешировать параметризованные endpoint'ы — как и обещает KDoc `StatsController.kt:… |
| BR-200 | `karaoke-app/src/main/resources/templates/publications.html:125` | PERF | ограничить выборку (limit/offset или диапазон дат по умолчанию) на стороне контроллера и добавить постраничный вывод/виртуализацию в шаблон; блок слота вынести в `th:fragment` с па… |
| BR-201 | `karaoke-app/src/main/resources/templates/processes.html:91` | COMPAT | либо вернуть брокер в `karaoke-app` (и публикацию `recordchange`/`processesrecordchange`), либо убрать `connect()`/`sockjs.js`/`stomp.js` из шаблонов и перейти на polling `GET /pro… |
| BR-205 | `karaoke-app/src/main/resources/templates/songs.html:673` | COMPAT | либо добавить в форму скрытые поля с текущими значениями (и обновлять их при выборе строки/STOMP), либо сделать параметры nullable/defaulted и не присваивать поле, когда параметр н… |
| BR-206 | `karaoke-app/src/main/resources/templates/songs.html:2286` | PERF | серверная пагинация (`limit`/`offset` уже поддержаны в `loadListFromDb`) с сохранением фильтров в query; для списка — явный список колонок вместо `SELECT *`; при необходимости — ви… |
| BR-213 | `karaoke-app/src/main/resources/templates/songs2.html:560` | COMPAT | привести контракт к одному из двух — (а) добавить в форму hidden `song_resultVersion` и передавать его; (б) объявить параметр `String?` и писать `RESULT_VERSION` только когда он пе… |
| BR-214 | `karaoke-app/src/main/resources/templates/songs2.html:598` | PERF | серверный `limit` + пагинация (`page`/`size`) и передача их из `doFilterForm`; либо обязательный непустой фильтр; отдельно — не редиректить AJAX-сохранение на полную страницу. |
| BR-219 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/TariffsController.kt:54` | DATA | проверять инварианты до записи и отвечать `400` с кодом: ровно один default на `scope` (снимать прежний в одной транзакции либо partial unique index + upsert), `periodDays > 0` для… |
| BR-227 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/ScraperAgent.kt:28` | SEC | отделить данные от инструкций (обёртка/разделители в user-сообщении + правило «инструкции внутри текста не выполнять»), передавать в промпт `artist`/`songTitle` и требовать от моде… |
| BR-234 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt:77` | ERR | передавать в функцию `Song.loadListFromDb(...)` (или id из запроса), как в `ApiController`; использовать `THREAD_LANE_*`-константу вместо литерала `1`; возвращать число скопированн… |
| BR-235 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt:441` | PERF | проверять `voice in 0..song.countVoices` (и `voice >= 0`) до вызова, возвращать 400/JSON `bad_voice`, как в `ApiController.kt:3403`; либо ограничить паддинг в `setSourceTextAndMark… |
| BR-244 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt:144` | RACE | один писатель на файл — `@Synchronized`/`ReentrantLock` вокруг «изменить карту → закодировать → записать», либо очередь записи; уникальный tmp на вызов (`Files.createTempFile(dir, … |
| BR-251 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:925` | DATA | проверять `Boolean` каждого вызова `postChangeRecords`; при `false` прерывать операцию и не выполнять удаление из источника (флаг «цель подтверждена» на чанк), возвращать в `SyncRe… |
| BR-266 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:3543` | ERR | заменить рекурсию одним проходом по списку с явным ограничением (не более одного круга) и возвратом `null`, когда проверять некого (`checkLastAlbumYm` уже трактует `null` как `-2`)… |

### PKG-S2-13 — karaoke-app: DATA — 2 BR

- **Severity:** S2 · **модули:** karaoke-app · **BR:** 2
- **Волна:** D · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 1 · **Риск:** данные/безопасность
- **Темы внутри:** check-then-act-repair, circuit-flags-false

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-278 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:767` | DATA | заново проверять `File(pathToFile).exists()` непосредственно перед сравнением (и трактовать отсутствие файла как отдельную ветку «диск исчез», без удаления объекта); либо перейти н… |
| BR-279 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:2555` | DATA | не писать флаг, если отчёт по этому типу получен из недоступного/непроверенного хранилища (передавать в `reconcilePlayerReadinessFlags` множество «неизвестных» ключей — `FATAL_ERRO… |

### PKG-S2-14 — karaoke-public: DATA, ERR, LEAK, RACE, SEC — 13 BR

- **Severity:** S2 · **модули:** karaoke-public · **BR:** 13
- **Волна:** D · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 9 · **Риск:** публичный периметр, данные/безопасность
- **Темы внутри:** share-revoke-status-ignored, abort-guard-identity, share-release-on-visibilitychange, marker-xss-editor, listeners-leak, premium-gate-once…

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-295 | `karaoke-public/src/components/ShareLinkModal.vue:248` | ERR | проверять `status === 200 && body?.revoked === true` до любых локальных изменений; при ином статусе показывать ошибку и оставлять `currentLink`/сохранённый URL; `clearSavedUrl()` в… |
| BR-302 | `karaoke-public/src/player/KaraokePlayer.js:167` | RACE | в начале `init()` завести локальный `controller`, положить его в поле и использовать только `controller.signal` во всех проверках и в `_loadAudio`; после каждого `await` дополнител… |
| BR-303 | `karaoke-public/src/player/KaraokePlayer.js:184` | ERR | не вызывать терминальный `release` на `visibilitychange`; для «ушёл со страницы» достаточно `pagehide`/`beforeunload` (уже есть) и истечения lease по TTL. Если нужно быстро освобож… |
| BR-312 | `karaoke-public/src/composables/useKaraokeEditor.js:489` | SEC | экранировать `label`/текст комментария (`textContent` или `&lt;`/`&gt;`) перед интерполяцией в HTML в обеих копиях `formatText`; при сохранении дополнительно валидировать/санитизир… |
| BR-313 | `karaoke-public/src/composables/useZakromaStreamProgress.js:66` | LEAK | хранить ссылки на обработчики и снимать их в `cleanup()` (плюс очищать `showTimeout`); либо вынести подписки на уровень приложения (создавать один раз) и не инстанцировать composab… |
| BR-319 | `karaoke-public/src/views/ChatView.vue:137` | ERR | не принимать решение о доступе в `mounted()` по кэшу: дождаться `await fetchMe()` (как в `AccountView`/`EditorTasksView`) и только затем грузить чат; дополнительно `watch(isPremium… |
| BR-327 | `karaoke-public/src/views/EditorWorkView.vue:1070` | DATA | при уходе сбрасывать отложенное сохранение принудительно (flush в `beforeRouteLeave`/`beforeUnmount`, `navigator.sendBeacon` для закрытия вкладки) либо блокировать уход предупрежде… |
| BR-328 | `karaoke-public/src/views/EditorWorkView.vue:448` | DATA | не отправлять и не сохранять END при `duration <= 0`; блокировать `submit` до `decode`/успешной загрузки стема и показывать ошибку загрузки аудио. |
| BR-329 | `karaoke-public/src/views/EditorWorkView.vue:734` | LEAK | перед созданием нового экземпляра уничтожать старый (`this.ws.destroy()`, `this.ws = null`, `this.wsRegions = null`), а `load()` сделать идемпотентным/защищённым флагом. |
| BR-330 | `karaoke-public/src/views/EditorWorkView.vue:1076` | ERR | обернуть ожидания в `try/catch/finally`, в `finally` сбрасывать `saveState`/`submitting`/`recalling`, показывать текст ошибки и давать повтор; различать сетевой сбой и HTTP-ошибку. |
| BR-339 | `karaoke-public/src/views/PlaylistsView.vue:97` | ERR | снимать `loading` в `finally`; на ошибку показывать состояние «не удалось загрузить» с кнопкой «Повторить»; задать `xhr.timeout` в `services/authApi.js` (общий механизм для всех `a… |
| BR-340 | `karaoke-public/src/views/RegisterView.vue:93` | ERR | в ветке `existing` дожидаться готовности глобала (`window.smartCaptcha ? onReady() : script.addEventListener('load', onReady)`), при `onerror`/таймауте не включать клиентский гейт … |
| BR-350 | `karaoke-public/src/views/SubscriptionReturnView.vue:131` | ERR | ввести в `poll()` явные состояния «проверяем / ошибка проверки / не найдено / обрабатывается»; на не-200 и на network error показывать «не удалось проверить статус» с кнопкой повто… |

### PKG-S2-15 — karaoke-web: COMPAT, DATA, ERR, LEAK, PERF, RACE, SEC — 14 BR

- **Severity:** S2 · **модули:** karaoke-web · **BR:** 14
- **Волна:** C · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 12 · **Риск:** публичный периметр, данные/безопасность
- **Темы внутри:** oauth-anonymous-endpoints, secret-in-logs, shared-singleton-interceptor, nth-free-race, no-transaction, unpublished-song-leak…

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-001 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicVkIdAuthController.kt:90` | SEC | закрыть оба endpoint'а аутентификацией администратора (server-to-server shared secret по образцу `X-Internal-Secret`, либо токен site-user'а с `isEditor`); привязывать `state` к ин… |
| BR-002 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicVkIdAuthController.kt:231` | SEC | логировать только `user_id`, длину токена и факт наличия `refresh_token`; тело ответа не логировать ни на одном уровне, включая ветку ошибки парсинга. |
| BR-004 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/config/WebMvcConfig.kt:55` | ERR | создавать два независимых экземпляра (конструктор `RateLimitInterceptor(properties, endpointName, limit)` / prototype-scope) либо хранить настройки не в бине, а в `InterceptorRegis… |
| BR-005 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/PriceService.kt:181` | RACE | выделять порядковый номер атомарно (строка-счётчик c `SELECT … FOR UPDATE` в одной транзакции с созданием подписки, либо unique-индекс на `(site_user_id, scope, ordinal)`), а нулев… |
| BR-012 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt:329` | DATA | одна транзакция на батч (`autoCommit=false`, `commit`/`rollback`), ошибки — структурированный код/HTTP-статус вместо `e.message!!`. |
| BR-013 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:965` | SEC | применять `onlyPublishedFor` (+ `SKIP`) к точечной загрузке; для не-редактора отдавать 404. Для картинок — та же проверка. |
| BR-015 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicNewsController.kt:45` | PERF | clamp `size` (например, ≤100) и `page ≥ 0`; для metrics — `@Size(max=…)`/отбрасывание сверх лимита и отдельный `client_max_body_size` на location; при превышении — 400/413. |
| BR-022 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlayerController.kt:152` | SEC |  |
| BR-023 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicSongEditorController.kt:216` | DATA | проверять результат `createDbInstance` и возвращать 5xx/`save_failed`; обрабатывать конкурентную вставку явно (повторное чтение существующего черновика и UPDATE вместо INSERT, либо… |
| BR-500 | `karaoke-web/src/main/resources/templates/statbysong.html:207` | COMPAT | убрать блок `cntBoosty` (`:206–210`) или заменить на существующее поле DTO; если нужен Boosty — вернуть счётчик в `StatBySongDto` и в SQL `getStatBySong`. |
| BR-504 | `karaoke-web/src/main/resources/templates/main.html:221` | SEC | валидировать схему ссылки на стороне записи и на стороне рендера (разрешить только `http(s):`/`/`), а в шаблоне — рендерить ссылку через безопасный префикс/параметр, а не «как есть… |
| BR-515 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SubscriptionRenewalScheduler.kt:55` | ERR | обернуть тело `tryRenew` в `try/catch` и логировать ошибку с `siteUserId` (продолжая цикл); ограничить лог частотой, чтобы один сбойный пользователь не забивал лог на каждом тике. |
| BR-516 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt:235` | LEAK | чистить карту по TTL (запись удаляется через N минут после последнего обращения — как в `RateLimitInterceptor`), либо хранить счётчики в ограниченном кольцевом буфере/фиксированном… |
| BR-517 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt:537` | ERR | убрать отладочный `log.error` (нормальный путь — `log.debug`/`log.info` с requestId); в catch — `log.error("…", e)` через SLF4J (стек в логгер), без `println`/`printStackTrace`; ре… |

### PKG-S2-16 — tools: DATA, ERR, SEC — 6 BR

- **Severity:** S2 · **модули:** tools · **BR:** 6
- **Волна:** C · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 6 · **Риск:** данные/безопасность
- **Темы внутри:** guard-rewrites-tracked-file, guard-ignores-error-code, smoke-auth-arg-missing, lock-pending-overwrites-digest, baseline-truncated-on-failure, poll-swallows-error

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-571 | `tools/check-do-sh-guards.sh:36` | DATA | вынести «зломанный» Dockerfile во временный каталог и проверять на копии (например, собрать через `docker build -f <tmp>/Dockerfile …`), либо установить `trap 'cp "$DOCKERFILE.guar… |
| BR-572 | `tools/check-feature-doc.sh:33` | ERR | в `check_doc` при отсутствии файла увеличивать `errors_total` (и/или в цикле учитывать код возврата `check_doc "$f" || errors_total=$((errors_total+1))`); в CI проверять каталог `d… |
| BR-593 | `tools/registry-smoke.sh:113` | SEC | передавать в `check` 4-й аргумент `1` и подставлять **заведомо неверный** пароль |
| BR-594 | `tools/registry-vendor.sh:189` | DATA | не записывать `PENDING` в колонку digest lock-файла (оставить прежнее значение либо |
| BR-595 | `tools/generate-eslint-baseline.sh:47` | DATA | писать результат во временный файл (`mktemp`) и переносить на место (`mv`) только |
| BR-601 | `tools/tracker-poll.sh:86` | ERR | не смешивать stderr с данными (`2>/dev/null` + отдельный захват stderr), код возврата |

### PKG-S2-17 — webvue3: COMPAT, DATA, ERR, RACE — 15 BR

- **Severity:** S2 · **модули:** webvue3 · **BR:** 15
- **Волна:** D · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 11 · **Риск:** данные/безопасность
- **Темы внутри:** premium-publish-dto-mismatch, autosave-debounce-not-cleared, stale-digest-response, vuex-unknown-action-publishvk, stale-audio-subs, sort-order-400…

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-087 | `webvue3/src/components/Songs/edit/SongEdit.vue:2740` | COMPAT | отдавать эти поля клиенту — добавить их в `SongDTO`/`toDTO()` (и в ответ списка, если карточка открывается без `loadSong`), либо отдельным endpoint'ом состояния публикаций с мержем… |
| BR-088 | `webvue3/src/components/Songs/edit/SongEdit.vue:3099` | DATA | перед сменой песни и в `beforeUnmount` выполнять принудительный `executeSave()` при непустом `diff` (дожидаясь ответа либо отправляя `fetch(..., {keepalive:true})`), а навигацию бл… |
| BR-093 | `webvue3/src/components/Songs/store.js:2733` | RACE | `return promisedXMLHttpRequest(...).then(...).catch(err => { commit('setSongsDigestIsLoading', false); /* показать ошибку */ }).finally(...)`, либо `async/await` с `try/catch/final… |
| BR-124 | `webvue3/src/components/Songs/edit/SongEdit.vue:4398` | ERR | либо вызывать `promisedXMLHttpRequest(request)` напрямую (импорт из `lib/utils`), либо зарегистрировать action; `id` брать из `this.song.id`/`getters.getCurrentSongId`, а не из `st… |
| BR-125 | `webvue3/src/components/Songs/edit/SongEdit.vue:4016` | RACE | фиксировать id песни на момент запроса (`const requestedId = this.song.id`) и применять/открывать ответ только если `requestedId === getCurrentSongId`; для `SubsEdit` передавать `s… |
| BR-370 | `webvue3/src/components/Authors/AuthorsTable.vue:745` | COMPAT | собирать полный payload, как в `AuthorAliasesModal`/`AlbumsTable` (id + author + ymId + vkId + lastAlbumYm + lastAlbumVk + lastAlbumProcessed + watched + skip + sortOrder), либо за… |
| BR-379 | `webvue3/src/components/ListeningHistory/store.js:66` | COMPAT | не добавлять ключ `pageSize` при `undefined` (условное присваивание) либо всегда передавать из таблицы `pageSize: this.perPage`; при 400 показывать пользователю сообщение, а не тол… |
| BR-380 | `webvue3/src/components/Common/HealthReport/store.js:33` | RACE | отдавать из геттера имена, совпадающие с контрактом (`dictName`, `dictValue`), либо переименовать параметры бэкенда; в `create()` перезагружать с текущим фильтром. |
| BR-388 | `webvue3/src/components/Processes/ProcessesTable.vue:395` | DATA | вводить поколение выборки (snapshot + фильтр), очищать/помечать `bulkSelectionIds` при каждом изменении фильтра и при ошибке snapshot, блокировать bulk-кнопки до получения snapshot… |
| BR-399 | `webvue3/src/components/Processes/edit/ProcessEdit.vue:349` | ERR | вызывать `doSave(changes)` в колбэке безусловно (модалка и есть подтверждение) либо добавить в `CustomConfirm.ok()` явный `ret.confirmed = true` и зафиксировать контракт в JSDoc; п… |
| BR-410 | `webvue3/src/components/ShareLinks/ShareLinksTable.vue:335` | COMPAT | передавать `page`, `perPage`, `sortBy`, `sortDir` и рендерить серверный ответ (или явно грузить весь список, если серверная пагинация не нужна); привести допустимый `perPage` к бэк… |
| BR-411 | `webvue3/src/components/Publish/components/PublishTableFooter.vue:140` | ERR | при пустой/невалидной дате не вызывать `setDateTo()`, а сбрасывать `dateTo` в пустую строку; валидировать формат на бэкенде и отвечать 400 вместо 500; предусмотреть очистку уже сох… |
| BR-416 | `webvue3/src/components/SongEditor/SongKaraokeEditorModal.vue:231` | DATA | не собирать payload вручную — использовать `markersToSave()`/`editor.buildPayload()` (как это делают `SongKaraokeEditorView.vue:974–976` и karaoke-public), либо явно переносить `m.… |
| BR-417 | `webvue3/src/components/SongEditor/SongKaraokeEditorModal.vue:211` | DATA | ввести явный dirty-флаг; при закрытии с pending-таймером или dirty-состоянием — либо синхронно выполнить `saveNow()` и дождаться результата, либо показать тот же confirm, что для `… |
| BR-426 | `webvue3/src/components/SongEditor/store.js:276` | COMPAT | собирать `params` условно — добавлять ключ только при непустом значении (как уже сделано в `searchCandidateSongs`, store.js:189–195), либо научить `getParamStringToSend` пропускать… |

### PKG-S2-18 — webvue3: COMPAT, DATA, ERR, RACE — 13 BR

- **Severity:** S2 · **модули:** webvue3 · **BR:** 13
- **Волна:** D · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 7 · **Риск:** данные/безопасность
- **Темы внутри:** time-field-string, save-error-invisible, voice-switch-stale, pagination-dead, stats-race, monetization-not-called…

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-449 | `webvue3/src/components/Songs/edit/SubsEdit.vue:317` | COMPAT | не пускать произвольный текст в числовое поле — `type="number"` + `@change` с `Number(...)`/`parseFloat` и откатом к прежнему значению при `NaN`; в `getMarkersToSave()` приводить `… |
| BR-450 | `webvue3/src/components/Songs/edit/SubsEdit.vue:3165` | ERR | в `catch` выставлять `this.isCustomConfirmVisible = true` (и `isAlert: true`), как в остальных 6 местах файла; ошибку также писать в консоль/тост, а `save()` возвращать признак усп… |
| BR-451 | `webvue3/src/components/Songs/edit/SubsEdit.vue:2267` | RACE | фиксировать запрошенный голос (`const requestedVoice = this.currentVoice`) и применять ответ только при `requestedVoice === this.currentVoice` (счётчик поколений/`AbortController`)… |
| BR-458 | `webvue3/src/components/Subscriptions/SubscriptionsTable.vue:30` | COMPAT | сделать пагинацию серверной: на `update:page`/`update:per-page`/применение фильтра отправлять `page` и `pageSize` (переименовать параметр), очищать `digest` при смене target; либо,… |
| BR-459 | `webvue3/src/components/Stats/store.js:443` | RACE | вводить `requestId`/поколение на endpoint и коммитить только ответ последнего запроса (или `AbortController` + сверка `target`/`days`/`page` в момент `then`). |
| BR-460 | `webvue3/src/components/Stats/store.js:559` | COMPAT | добавить второй endpoint в `tabEndpoints[1]` (или dispatch из `loadDataForActiveTab`) и вернуть `Promise<Boolean>` из action, чтобы TTL взводился по факту загрузки. |
| BR-461 | `webvue3/src/components/Tariffs/store.js:61` | ERR | `.catch` в каждой мутации с возвратом `false` и показом ошибки (CustomConfirm/alert), откат `editable` к `original` при отказе, проверка `Number.isFinite(priceRub) && priceRub >= 0… |
| BR-462 | `webvue3/src/components/StemJobs/store.js:51` | DATA | очищать `stemJobs` (или помечать `loadedTarget`) при смене target; при ошибке показывать сообщение и блокировать действия; в `stopStemJob`/`deleteStemJob` добавить `.catch` с уведо… |
| BR-467 | `webvue3/src/components/Songs/edit/SubsEdit.vue:4137` | ERR | не предполагать форму поля — брать значение как `marker.chord || marker.label` (и проверять тип/`String()`), а в `case 'chord'` возвращать пустое содержимое, если значения нет; отд… |
| BR-483 | `webvue3/src/player/KaraokePlayer.js:2937` | DATA | анимировать стрелки по времени кадра (`dt`, тот же источник, что `_forcedTime`), а не по |
| BR-484 | `webvue3/src/player/KaraokePlayer.js:1435` | ERR | в ветке `inlineData` конструктора задавать `this.songId` (id песни есть в inlineData) |
| BR-485 | `webvue3/src/player/KaraokePlayer.js:3223` | COMPAT | синхронизировать набор версий с `RenderVersion` (добавить CHORDS и TABS) либо |
| BR-487 | `webvue3/src/views/StatsView.vue:745` | DATA | инвалидировать не только активную вкладку — при смене `target` сбрасывать данные и `lastLoadedAt` **всех** вкладок (участвует во всех запросах), при смене `days` — всех day-depende… |

### PKG-SEC-SECRET-ARGV — Секреты в argv процессов

- **Severity:** S2 · **модули:** deploy, tools · **BR:** 3
- **Волна:** C · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 3 · **Риск:** данные/безопасность, прод-конфигурация

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-551 | `deploy/web-server-deploy/deploy/do.sh:37` | SEC | в legacy-копии перевести логин на `--password-stdin` с `REGISTRY_HOST`/`REGISTRY_USER` и добавить проверки кодов возврата (`|| { echo ERROR; return 1; }`); канонично — удалить `dep… |
| BR-552 | `deploy/tracker-db-backup.sh:73` | SEC | не передавать пароль через argv — записывать его в одноразовый `.pgpass` внутри контейнера (`docker exec -i … sh -c 'cat > /tmp/.pgpass …'` с последующим удалением), либо использов… |
| BR-605 | `tools/tracker-lib.sh:161` | SEC | передавать токен через stdin/конфиг curl, а не через argv: `curl --config -` с |

### PKG-SEC-SQLI — SQL-инъекции в параметризованных запросах

- **Severity:** S2 · **модули:** karaoke-app · **BR:** 5
- **Волна:** D · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 5 · **Риск:** данные/безопасность

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-028 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt:885` | SEC | перевести все фильтры на `PreparedStatement` с `?` (включая `ids` → `IN (?, …)`); числовые поля — `setInt/setLong`; при динамическом наборе колонок — белый список имён, значения то… |
| BR-100 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:7817` | SEC | перевести `getWhereList` на параметризованные `?` + `PreparedStatement` (коллекция значений рядом с коллекцией фрагментов); для `filter_*` — парсить значение как число/оператор из … |
| BR-175 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Pictures.kt:213` | SEC | перейти на параметризованный запрос (`PreparedStatement`/`JdbcTemplate` с `?`) либо, как минимум, приводить `id` к `Long` до подстановки и экранировать `'` в `picture_name`/`name` … |
| BR-182 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt:174` | SEC | параметризовать все условия (`PreparedStatement` + `?`), `id` — `setLong`; либо переиспользовать экранирование из `Dictionary.getWhereList`. Динамические имена колонок — только из … |
| BR-237 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Album.kt:227` | SEC | параметризовать условия (`PreparedStatement` + `?`), числовые поля приводить к `Long`/`Int` до подстановки, `album_type` — белый список `AlbumType.entries`; `name`/`name_search` ос… |

### PKG-SEC-XFF — Доверие к заголовку X-Forwarded-For

- **Severity:** S2 · **модули:** karaoke-web · **BR:** 1
- **Волна:** C · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 1 · **Риск:** данные/безопасность

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-003 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/RateLimitInterceptor.kt:96` | SEC | брать последний элемент `X-Forwarded-For` (или `X-Real-IP`, который nginx перезаписывает `$remote_addr`), либо доверять заголовкам только от известных прокси; для debug-allowlist —… |

### PKG-ERR-STATS-ZERO — Нули в статистике при ошибке БД

- **Severity:** S3 · **модули:** karaoke-app, karaoke-web · **BR:** 2
- **Волна:** E · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 2

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-016 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/StatBySong.kt:234` | ERR | обновлять кэш только при успехе всех запросов (атомарная замена набора), при ошибке сохранять прежние значения и логировать throwable; cold-start-условие не должно блокировать повт… |
| BR-185 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/MonetizationStats.kt:53` | ERR | пробрасывать `SQLException`/ошибку соединения наружу (или возвращать `null`), чтобы `respondCached` отдавал 503; агрегировать 11 скаляров одним запросом. |

### PKG-S3-01 — TEST/karaoke-app (S3, 17)

- **Severity:** S3 · **модули:** karaoke-app · **BR:** 17
- **Волна:** B · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 13

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-532 | `karaoke-app/src/test/kotlin/com/svoemesto/PlaywrightTests.kt:15` | TEST | вынести ручные сценарии в отдельный source set/задачу (`manualTest`), не смешивать с `test`; убрать интерактивный `readLine()` в пользу ожидания файла состояния с таймаутом; задава… |
| BR-561 | `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/HealthReportBatchPoolTest.kt:204` | TEST | либо переименовать тест в «lastSentQueueSize не обнуляется без worker-loop» и явно зафиксировать, что инвариант «0 после drain» проверяется в интеграционном тесте (ссылка на него),… |
| BR-562 | `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/HealthReportBatchPoolTest.kt:38` | TEST | завести `private var pool: HealthReportBatchPool? = null` и присваивать его в `newPoolWithDirectExecutor()`; в `tearDown` гасить оба executor'а (`executor` и `waitingExecutor`) у с… |
| BR-563 | `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/StorageCircuitBreakerTest.kt:108` | TEST | готовить состояние OPEN с истёкшим cooldown и отпускать 20 потоков одновременно (`startLatch`), не выдавая probe заранее; ассертить `probeCount == 1` и `fastFailCount == threadCoun… |
| BR-564 | `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/StorageCircuitBreakerTest.kt:138` | TEST | обернуть тело теста в `try { ... } finally { pool.shutdownNow() }` (для `HealthReportBatchPoolTest` — `executors.shutdownNow()` в `finally`), либо использовать `assertTimeoutPreemp… |
| BR-565 | `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/cleanup/DemoMp4CleanupTest.kt:60` | TEST | либо удалить дублирующий тест, либо перестроить его так, чтобы сценарий «публикация провалилась» действительно моделировался (через `stageClassesAfterPublish(tgSuccess = false, vkS… |
| BR-566 | `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/cleanup/DemoMp4CleanupTest.kt:202` | TEST | добавить в тест чтение TSV-файла журнала из `tempDir/logs` и ассерты по `CleanupJournal.HEADER` и колонке `result == "deleted"` (как это уже сделано в `ArtifactCleanupGuardTest.kt:… |
| BR-567 | `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/cleanup/ArtifactCleanupRegistryTest.kt:20` | TEST | проверять инвариант, а не снимок: «каждый класс реестра имеет непустой набор матчеров, кроме MANUAL_FILES», «число классов равно числу элементов enum (тавтология не нужна) — вместо… |
| BR-568 | `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/VkPreviewWarmupClientTest.kt:209` | TEST | добавить ассерт `assertTrue(result.error!!.startsWith("connect:"))` (как в соседних тестах `:116`, `:124`, `:135`, `:146`, `:178`, `:188`, `:198`). |
| BR-569 | `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/StorageTimeoutInterruptTest.kt:86` | TEST | не полагаться на глобальный хук — проверять конкретный результат вместо отсутствия глобального события (например, перехватывать ошибку в конкретном `Mono`), либо выполнять проверку… |
| BR-570 | `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/StorageCircuitBreakerTest.kt:310` | TEST | проверять порядок событий вместо длительности (например, «результат пришёл раньше, чем завершился loader») либо использовать управляемые часы; для watchdog — ждать наступления сост… |
| BR-587 | `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/SearchLastAlbumVkTest.kt:26` | TEST | убрать файл из `src/test` (перенести в ручной сценарий/документ) либо, если файл остаётся, снять `@Disabled`, добавить проверку результата и восстанавливать глобальные значения в `… |
| BR-588 | `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/AlbumCoverFinderScrapersTest.kt:75` | TEST | тест должен работать с чистой функцией `parseAlbumCoverScrapers` (как остальные тесты файла) либо с явно подставленным значением; сравнение с `defaultValue` вынести в отдельное утв… |
| BR-589 | `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/HealthReportWaitingPoolTest.kt:193` | TEST | в `@AfterEach` вернуть глобал в исходное состояние (`HealthReport.attachHealthReportBatchPool(null)` либо добавить в прод отдельный сброс для тестов, как у `HealthReportBatchPool.r… |
| BR-590 | `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/WhisperMarkerAlignerSpecTagsTest.kt:27` | TEST | сохранить прежние значения глобалов в `@BeforeAll` (или в `@BeforeEach`) и восстановить их в `@AfterAll`/`@AfterEach`; значения для теста задавать через тестовый конфиг, а не через… |
| BR-591 | `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/KaraokeProcessCleanupTest.kt:81` | TEST | проверку «файлы не удалены» вынести под отдельное условие «тест запущен не под root» (например, `Assumptions.assumeTrue(System.getProperty("user.name") != "root")` с явной пометкой… |
| BR-592 | `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/SanitizePathTest.kt:120` | TEST | запомнить исходный `logger.level` до изменения и вернуть его в `finally` (в обоих тестах файла). |

### PKG-S3-02 — TEST/karaoke-public (S3, 3)

- **Severity:** S3 · **модули:** karaoke-public · **BR:** 3
- **Волна:** B · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 2 · **Риск:** публичный периметр

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-523 | `karaoke-public/src/composables/__tests__/formatText.test.js:19` | TEST | добавить тест вида `formatText([set('COMMENT|<img src=x onerror=alert(1)>')])` → assert, что в выводе нет «`<img`»/«`onerror`» (а есть экранированная сущность), и такой же для `use… |
| BR-524 | `karaoke-public/e2e/public-editor.spec.js:44` | TEST | проверять поведение, а не текст бандла (например, прогонять публичный редактор в браузере и проверять DOM), либо пометить проверку как «дымовая» и не считать её блокирующей. |
| BR-525 | `karaoke-public/e2e/public-editor.spec.js:15` | TEST | добавить в CI джоб `e2e` (docker-compose с собранным `karaoke-public` + `npx playwright test`), а проверку «без ошибок в консоли» сузить до фильтра сетевых/ресурсных ошибок, игнори… |

### PKG-S3-03 — TEST/karaoke-web (S3, 3)

- **Severity:** S3 · **модули:** karaoke-web · **BR:** 3
- **Волна:** B · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 2

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-511 | `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/util/Mp3TrimmerTest.kt:16` | TEST | `process.waitFor(60, TimeUnit.SECONDS)` + при `false` — `process.destroyForcibly()` и `fail(...)`; вывод `ffmpeg` перенаправлять в файл (`redirectOutput(tmpFile)`) либо читать пото… |
| BR-512 | `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/util/Mp3TrimmerTest.kt:48` | TEST | либо установить `ffmpeg` в CI-образ и убрать `assumeTrue` (тогда отсутствие бинарника — падение), либо оставить `assumeTrue`, но добавить в CI джоб, который ставит ffmpeg и **явно … |
| BR-513 | `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkDateTimeTest.kt:47` | TEST | запускать проверку в отдельном потоке/процессе с принудительно заданной `TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))` (с восстановлением в `finally`) либо использ… |

### PKG-S3-04 — TEST/webvue3 (S3, 11)

- **Severity:** S3 · **модули:** webvue3 · **BR:** 11
- **Волна:** B · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 10

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-615 | `webvue3/e2e/measure-input-latency.spec.js:11` | TEST | вынести измерялку из `testDir` (например, в `webvue3/scripts/measure-input-latency.mjs`, вызываемую вручную или отдельным npm-скриптом), либо добавить в конец явное утверждение по … |
| BR-616 | `webvue3/e2e/measure-formatter-vhtml.spec.js:37` | TEST | либо вынести в измерялку (см. P-68-02), либо проверять заявленный порог (`toBeLessThan(16)`) с явной пометкой «порог согласован владельцем», чтобы файл был либо проверкой, либо изм… |
| BR-617 | `webvue3/e2e/measure-formatter-vhtml.spec.js:11` | TEST | использовать относительный путь (`page.request.post('/api/song/textformatted', …)`) — `baseURL` подставится сам; id песни вынести в переменную окружения/`testInfo`; ожидаемый разме… |
| BR-618 | `webvue3/e2e/save-race.spec.js:83` | TEST | вызвать оба сохранения **внутри одного** `page.evaluate` без промежуточного `await` (например, `Promise.allSettled([inst.proxy.save(), inst.proxy.save()])` или два вызова без `awai… |
| BR-619 | `webvue3/e2e/tabs-variant.spec.js:68` | TEST | проверять кламп на данных, где он вообще может сработать: (а) подставлять список вариантов в компонент (как уже сделано со словарём в `yo-highlight.spec.js` — `setDictionary`), либ… |
| BR-620 | `webvue3/src/utils/__tests__/highlightWords.test.js:109` | TEST | заменить строку под проверкой на строку со словом **из словаря** (например `'<b>всё</b>'`) и добавить второе утверждение — что внутри тега не оказалось «живой» разметки (`!html.inc… |
| BR-621 | `webvue3/e2e/marker-nav.spec.js:44` | TEST | вычислять опорную точку динамически (например, середина массива: `k = Math.floor(vis.length / 2)`) и требовать минимального числа видимых маркеров явной проверкой с внятным сообщен… |
| BR-622 | `webvue3/e2e/ghost-markers.spec.js:125` | TEST | проверять инвариант «регион на каждый видимый маркер и ни одного региона в нуле, кроме маркеров, у которых в данных `time === 0`» — то есть сравнивать `regionsAtZero` с `markersAtZ… |
| BR-623 | `webvue3/e2e/yo-highlight.spec.js:91` | TEST | текст под проверкой тоже задавать тестом (вставлять собственную строку в редактор, как это делает `paste-blank-lines.spec.js`), а ожидания выводить из вставленного текста вместо ко… |
| BR-624 | `webvue3/playwright.config.js:28` | TEST | подтвердить или снять посылку про «общее состояние песни» (заглушки мутаций уже есть) и вернуть `fullyParallel: true`/`workers` по умолчанию; ожидания `waitForTimeout(7000…12000)` … |
| BR-625 | `webvue3/e2e/smoke.spec.js:1` | TEST | вынести id песни в параметр (env/`testInfo`), добавить в набор подготовку данных (создание тестовой песни с нужными маркерами/нотами через API или SQL-фикстуру) — тогда набор перес… |

### PKG-S3-06 — guards/CI/tools (S3, 7)

- **Severity:** S3 · **модули:** tools · **BR:** 7
- **Волна:** B · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 7

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-574 | `tools/check-container-restart.sh:7` | RULE | добавить паттерны `docker\s+compose\b.*\brestart\b` и `docker\s+start\s+<c>` (и `docker\s+restart\s+(-[^\s]+\s+)*<c>`), убрать зависимость от формы `docker-compose`. |
| BR-578 | `tools/check-eslint-baseline.sh:76` | COMPAT | нормализовать путь к репозиторию (относительный от корня) при записи baseline и при сравнении; ключ сделать устойчивым к сдвигу строк (ruleId + относительный путь + текст сообщения… |
| BR-581 | `tools/check-docker-config.sh:29` | RULE | определять ожидаемый путь от корня репозитория (`git rev-parse --show-toplevel`/`.gradle` рядом с ним) либо через матрицу машин; AGENTS.md-команду для gradle/docker сделать перенос… |
| BR-584 | `tools/auto-kdoc.py:128` | RULE | убрать ветку `Entity` из `describe()` (или переформулировать без упоминания JPA); проверять результат генерации guard’ом `check-no-jpa-imports.sh`. |
| BR-599 | `tools/generate-docs.sh:28` | RULE | перед вызовами задать `export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$REPO_ROOT/.gradle}"` |
| BR-602 | `tools/tracker-poll.sh:106` | COMPAT | разделять по пробелу с фиксированным числом колонок (`split(" ")` + `map(select(length>0))`), |
| BR-611 | `tools/tracker-lib.sh:25` | RULE | в библиотеке не менять настройки вызывающего шелла (`set -euo pipefail` убрать из |

### PKG-S3-19 — .github: SEC — 1 BR

- **Severity:** S3 · **модули:** .github · **BR:** 1
- **Волна:** E · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 1 · **Риск:** данные/безопасность
- **Темы внутри:** actions-tag-pinned

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-508 | `.github/workflows/lint.yml:50` | SEC | закрепить actions по SHA коммита (`uses: actions/checkout@<sha> # v4.x.x`) — стандартная практика hardening; при необходимости использовать Dependabot для обновления SHA. |

### PKG-S3-20 — alignment-ml: BUILD, DATA, ERR — 7 BR

- **Severity:** S3 · **модули:** alignment-ml · **BR:** 7
- **Волна:** E · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 6 · **Риск:** данные/безопасность
- **Темы внутри:** eval-swallow, align-error, romanize-fallback, train-data-loss, chunk-boundary, train-script…

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-492 | `alignment-ml/evaluate.py:63` | ERR | развести счётчики (`rows_failed_align` и `rows_skipped_mismatch` — печатать раздельно), печатать тип исключения и трессировку (`traceback.format_exc()`), и завершать прогон ненулев… |
| BR-493 | `alignment-ml/align.py:103` | ERR | откат оставить, но сделать его **явным и наблюдаемым**: прокидывать флаг режима в состояние и в ответ `/align` (например, `star_token: bool`), логировать в stderr с `traceback`, а … |
| BR-494 | `alignment-ml/align.py:125` | ERR | при `ImportError` **прекращать выравнивание** (ошибка 5xx «uroman не установлен — романизация обязательна для MMS_FA»), а не подставлять кириллицу; дополнительно в `_sanitize_for_v… |
| BR-495 | `alignment-ml/train.py:271` | DATA | не подменять пример, а **исключать** его: возвращать sentinel/None и отфильтровывать индекс заранее (список «плохих» индексов накапливать и пересобирать датасет), ошибку логировать… |
| BR-496 | `alignment-ml/chunking.py:26` | DATA | отключить padding на границах, полученных принудительным резом (padding только на «тихих» границах), либо добавлять в текст чанка соседние слова, попавшие в окно; альтернативно — р… |
| BR-497 | `alignment-ml/start_trainig.sh:1` | BUILD | `set -euo pipefail`; определять каталог скрипта (`cd "$(dirname "$0")"`) вместо `~/Karaoke/...`; активировать venv **проверкой** (`test -f .venv/bin/activate` → иначе понятная ошиб… |
| BR-498 | `alignment-ml/requirements.txt:1` | BUILD | зафиксировать версии (`torch==…`, `torchaudio==…` одной парой, `transformers>=4.46`), зафиксировать `torchaudio` **той же** минорной версии, что и `torch`, и добавить в README явно… |

### PKG-S3-21 — deploy: BUILD, DATA, PERF, SEC — 15 BR

- **Severity:** S3 · **модули:** deploy · **BR:** 15
- **Волна:** E · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 13 · **Риск:** публичный периметр, данные/безопасность, миграции БД, прод-конфигурация
- **Темы внутри:** build-tag-constant, editor-role-migration, duplicate-index, deploy-script-guard, storage-compose, false-idempotency-status…
- **Зависит от:** PKG-S2-08 (рабочий бэкап БД — prerequisite)

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-042 | `deploy/do.sh:40` | BUILD | тегировать образом номер сборки, который реально меняется (git SHA / дата / счётчик), и передавать его в прод тем же значением, а не хардкодом `:1`; в прод-`pull_*` использовать `$… |
| BR-044 | `deploy/karaoke-db/13_site_user_editor_role.sql:8` | DATA | держать одну каноническую функцию (отдельный файл-источник, например `deploy/recordhash_site_users.sql`, как сделано для `tbl_authors`) и подключать её из миграций; либо в каждой м… |
| BR-045 | `deploy/karaoke-db/01_initdb.sql:182` | PERF | `DROP INDEX idx_gin_result_text`; создать индекс на `tbl_songs` по колонке (`USING gin (to_tsvector('russian'::regconfig, result_text))`) либо генерируемую `tsvector`-колонку; в `0… |
| BR-046 | `deploy/do.sh:521` | BUILD | смонтировать каталог дампов в локальный compose (`./karaoke-db/dumps:/dumps`); не хардкодить `-U postgres` (пользователь БД в compose — `${DB_LOCAL_POSTGRES_USER}`); убрать `-t` (о… |
| BR-047 | `deploy/docker-compose-storage.yml:4` | BUILD | зафиксировать конкретный релиз MinIO (тег + `@sha256:…`, как уже сделано для `postgres:16` в `deploy/prod-single-host/docker-compose-database.yml:4`) и перейти на `MINIO_ROOT_USER`… |
| BR-535 | `deploy/karaoke-db/32_song_status_lifecycle.sql:14` | DATA | сузить условие до состояний, которых миграция ещё не касалась: `WHERE id_status IN (3, 4, 5)` (эквивалентно `id_status >= 3 AND id_status < 6`) — тогда повторный запуск действитель… |
| BR-536 | `deploy/karaoke-db/29_albums.sql:69` | DATA | включить `album_id` в список колонок INSERT (в `getSqlToInsert` — как `id_tariff`), либо переносить связь «песня↔альбом» отдельным upsert'ом; заодно проверить остальные колонки `tb… |
| BR-537 | `deploy/karaoke-db/22_stem_jobs.sql:20` | DATA | удалять объекты MinIO до удаления строки задания (в т.ч. при каскадном удалении — триггер `AFTER DELETE` на `tbl_stem_jobs` или отдельный обход при удалении пользователя); каскад з… |
| BR-538 | `deploy/karaoke-db/44_author_song_counts.sql:187` | BUILD | `DROP TRIGGER IF EXISTS trg_tbl_songs_update_author_counts ON public.tbl_songs;` перед `CREATE TRIGGER` (либо `CREATE OR REPLACE TRIGGER`, доступный с PostgreSQL 14) и привести шап… |
| BR-547 | `deploy/prod-single-host/docker-compose-web.yml:53` | BUILD | добавить `healthcheck` (например `curl -fsS http://localhost:${WEB_PORT_INSIDE_CONTAINER}/actuator/health` либо `pg_isready` для БД), `mem_limit`/`cpus` (для `karaoke-web` — около … |
| BR-548 | `deploy/tts/silero_say.py:79` | SEC | зафиксировать sha256 модели в репозитории/переменной и проверять его после загрузки (`hashlib.sha256` + сравнение, отказ при несовпадении); при несовпадении — не загружать pickle; … |
| BR-549 | `deploy/prod-single-host/nginx_karaoke-public.conf:19` | SEC | привести лимит к фактически допустимому размеру загрузки публичного API (например 20–64 МБ) и продублировать его в host-nginx; отдельно — в `location /api/public` задать свой, мень… |
| BR-550 | `deploy/prod-single-host/nginx_karaoke-public.conf:21` | PERF |  |
| BR-559 | `deploy/new_comp/sm-karaoke-system/deploy/do.sh:5` | BUILD | добавить `set -euo pipefail` (или `set -e` + явные проверки) в начало скрипта; загружать `.env`/`do.env` через безопасный блок `[ -f "$f" ] && . "$f" || { echo "нет файла $f" >&2; … |
| BR-560 | `deploy/new_comp/sm-karaoke-system/deploy/docker-compose-app-new-comp.yml:51` | BUILD | добавить `depends_on` + `healthcheck` (с проверкой готовности БД/хранилища) либо `restart: unless-stopped` для `karaoke-app`/`karaoke-web`/`karaoke-webvue`/`karaoke-webvue3`; задат… |

### PKG-S3-22 — karaoke-app: COMPAT, DATA, ERR, PERF, RACE, SEC — 40 BR

- **Severity:** S3 · **модули:** karaoke-app · **BR:** 40
- **Волна:** E · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 5 · **Риск:** данные/безопасность
- **Темы внутри:** filter-limit-500, mediainfo-no-timeout, recovery-swallow, soft-deleted-return, unchecked-cast-sheetsage, swallowed-sqlexception-changed…

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-034 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt:931` | ERR | `toIntOrNull()` с 400 при нечисле; срез через `take(limit)`/`drop` без индексации. |
| BR-035 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/MediaInfo.kt:29` | ERR | `waitFor(timeout)` + проверка exit code + `destroyForcibly()` в `finally`; ошибку возвращать явно и логировать с уровнем, не подменять нулём без следа. |
| BR-036 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt:600` | ERR | логировать исключение со стеком (SLF4J), при сбое recovery не запускать цикл или сигналить в мониторинг; добавить колонку причины ошибки (или писать в `process_description`) и запо… |
| BR-037 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt:963` | DATA | добавить в `loadList` фильтр `process_deleted_at IS NULL` (или явный параметр `includeDeleted`), тот же фильтр — в `hasActiveProcess`; отдавать в DTO признак удаления. |
| BR-051 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:946` | ERR | читать значения безопасно (`(map["tempo"] as? String) ?: ""`, `(map["chords"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()`), либо отдавать 404/пустой ответ осознанно и … |
| BR-052 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:558` | ERR | логировать через SLF4J с контекстом (`time`, число строк), возвращать 5xx (или явный признак ошибки в контракте), чтобы polling не терял изменения молча; закрытие ресурсов — через … |
| BR-053 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:3734` | ERR | использовать существующий `loadSongsBatch` (или общий helper) во всех четырёх обработчиках; идемпотентность/`distinct()` — как в `createkaraokeall`. |
| BR-054 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:1378` | COMPAT | заменить `SongVersion.LYRICS` на `SongVersion.CHORDS` в семи перечисленных местах и выровнять лимиты длины с соседними версиями; добавить тест на различие префиксов версий для chor… |
| BR-055 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:3734` | PERF |  |
| BR-056 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:2484` | RACE | сериализовать доступ к файлу истории (общий lock/`ReentrantLock` на путь), писать атомарно через временный файл + `Files.move(ATOMIC_MOVE)`, а ошибку разбора не превращать в «пусту… |
| BR-057 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:3267` | PERF | считать дату SQL-запросом с `WHERE`/`ORDER BY … LIMIT 1` (или отдельным лёгким `SELECT date …`), не материализуя все песни; для «сегодня» — фильтр по дате в SQL. |
| BR-058 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:2914` | ERR | логировать SLF4J `warn` с кодом ответа и хостом, проверять `responseCode in 200..299` и логировать не-2xx; при неуспехе — повтор/фолбэк (или явный признак в ответе), чтобы не терят… |
| BR-059 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:3875` | ERR | привести тексты к фактическому порогу (`>= 4`), лучше — формировать сообщение из той же константы, что и условие. |
| BR-061 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:5855` | ERR | обернуть тело каждого потока в `try/catch (t: Throwable)` с `println`/SLF4J и `SNS.send(SseNotification.error(...))`, как в 5700–5732; закрытие соединения — см. P-09-13. |
| BR-065 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:4714` | PERF | валидировать даты (`start <= end`, максимум N дней, например 31) и/или выполнять обход в фоновом потоке с SSE-прогрессом; при невалидном формате — 400, а не 500. |
| BR-066 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:8270` | PERF | писать ZIP сразу в `response.outputStream` (или временный файл и `FileSystemResource`), не держать стемы и архив в heap; `zip`/потоки закрывать в `finally`; ограничить конкурентнос… |
| BR-067 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:8057` | ERR | вычитывать `process.inputStream` (как `Utils.runProcess`), задавать таймаут через `waitFor(timeout, unit)` с `destroyForcibly()` при истечении, ограничить stderr (`-loglevel error`… |
| BR-068 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:4852` | ERR | логировать `warn` с `song.id`, движком и `e.message` (как в пути B) и/или агрегировать причины в SSE-сводку (`failedReason`), не меняя поведение подсчёта `minIntervalMs`. |
| BR-069 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:8023` | COMPAT | возвращать `ResponseEntity<SearchResultDTO>` и явный `404`, либо `404` через `ResponseStatusException`; привести KDoc/контракт и клиент к одному поведению (клиент должен различать … |
| BR-070 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:6629` | ERR | возвращать `404`/`400` с текстом «путь не найден/не каталог» (и `ResponseEntity`), не подменять путь на `/`; начальный листинг корня — только при явно пустом `path`. |
| BR-072 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:445` | DATA | в геттере не писать в БД: вернуть fallback и залогировать `ERROR`/`WARN` с `songId` и исходной строкой; нормализацию/миграцию формата вынести в явную операцию (как это сделано для … |
| BR-073 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:217` | DATA | убрать запись из геттера; вынести «починку» `root_folder` в явную операцию (health-action или импорт) с проверкой однозначности кандидата (ровно один матч + сверка `file_name`/`aud… |
| BR-074 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:731` | DATA | геттер сделать чистым (только чтение); создание `Pictures` и установку флага перенести в write-путь (импорт, health-action, загрузка обложки); флаг готовности ставить после успешно… |
| BR-075 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:1007` | RACE | писать флаг точечным SQL (`jsonb_set(player_readiness_flags, '{key}', …)` в одном `UPDATE`) либо под `SELECT … FOR UPDATE` (как `saveToDbLocked`); в идеале — вынести флаги из общег… |
| BR-076 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:4095` | ERR | проверять `voice in sourceMarkersList.indices`; при пустом `variants` возвращать `emptyList()` (как уже сделано для пустых `markersNotes` на 4096) либо явную ошибку валидации марке… |
| BR-077 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:4335` | ERR | `coerceIn(0, size - 1)` с проверкой на пустой список; валидировать `indexTabsVariant` в API (0..N-1) и при загрузке из БД; при выходе за диапазон — сбрасывать на 0 с логом. |
| BR-078 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:2153` | ERR | парсер сделать устойчивым (`getOrNull`, проверки длины, `toDoubleOrNull`), при нештатном файле — `WARN` с `songId`/путём и пустой результат; в `updateBpmAndKeyLV` — `try/catch` на … |
| BR-079 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:2130` | ERR | заменить `Map<String, Any>` на типизированный `data class SheetsageInfo(key: String?, tempo: String?, chords: List<String>, beattimes: List<Double>)` либо вернуть `Map<String, Any?… |
| BR-080 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:2711` | ERR | `maxOfOrNull { … } ?: 0L` для `songLengthMs`; в `getMltProp` — явная проверка `sourceMarkersList.isEmpty()` с типизированной ошибкой («маркеры не заданы») до разыменования `[0]`; `… |
| BR-081 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:4406` | ERR | валидировать `stringLad` (`toIntOrNull()`, диапазон 0..5, лад ≥ 0) до рендера; при невалидном значении — `WARN` и фолбэк на автоматический подбор строя (ветка `else`). |
| BR-082 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:3535` | ERR | считать минимум по непустым голосам (`mapNotNull { … minOfOrNull { it } }.minOrNull()`), пустой случай обрабатывать явно (0L); в `catch` — логировать `songId`, а не глушить. |
| BR-083 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:263` | PERF | `ms` — чистое чтение: значение заполнять при импорте/анализе (как `MediaInfo`/`StorageMetadataCache`), при отсутствии — возвращать `0` без subprocess; запись — только из явного шаг… |
| BR-084 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:976` | PERF | мемоизировать разобранный `Map` в объекте (инвалидировать при записи поля) либо хранить флаги типизированными полями; `sourceMarkersList` — аналогично кэшировать по значению строки… |
| BR-085 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:1895` | ERR | использовать уже существующий `parseKeyBpmFileOrNull` (или обернуть чтение/декод в `try/catch`), при неудаче — `WARN` и `Pair("", 0)`; `println` пути заменить на логгер. |
| BR-086 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:4180` | SEC | экранировать **все** интерполируемые значения (`originalKey`, `newKey`, `noteOctave`, `capo`) тем же `escapeHtml()` до сборки HTML; отдельный тест «значение поля с `<` не попадает … |
| BR-103 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:5396` | DATA | нормализовать не `this`, а объединение «свежая строка из БД + изменения, которые этот объект действительно намерен внести» (трёхсторонний diff по исходному снимку), либо применять … |
| BR-105 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:8332` | DATA | считать число голосов в SQL (`jsonb_array_length`/`json_array_length` по `source_markers` с фолбэком на `source_text`) или, если фильтр остаётся в памяти, грузить `source_markers` … |
| BR-106 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:6066` | DATA | сначала DELETE в транзакции, затем — удаление файлов (best-effort с логом результата); `deleteOnExit` заменить на немедленное удаление в `try/catch`; `prepareStatement` — в `use {}… |
| BR-107 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:7534` | ERR | ловить только `SQLException` с признаком рассинхрона сиквенса (или проверять `sqlState`/сообщение), остальные — пробрасывать; возвращать `null` только после реального отсутствия `g… |
| BR-114 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/config/SecurityConfig.kt:42` | SEC | закрыть admin-API на уровне кода (правило `authenticated()` для `/api/**`, кроме явного публичного списка) либо на уровне nginx (`allow/deny` + `auth_basic`); не принимать `X-Admin… |

### PKG-S3-23 — karaoke-app: BUILD, DATA, ERR, LEAK, PERF, RACE — 40 BR

- **Severity:** S3 · **модули:** karaoke-app · **BR:** 40
- **Волна:** E · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 31 · **Риск:** данные/безопасность
- **Темы внутри:** image-errors-swallowed, printstacktrace-empty, unbounded-page, timestamp-validation, chromium-double-run, ffmpeg-no-timeout…

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-117 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsPictures.kt:650` | ERR | логировать через SLF4J с `path` и исключением и возвращать/бросать признак ошибки, чтобы endpoint отдавал 5xx/ошибку и не рапортовал успех; создать каталог перед записью там, где е… |
| BR-118 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ShareLinksAdminController.kt:145` | ERR | логировать через SLF4J и возвращать 500/503, а не пустой успешный ответ; валидировать даты до SQL. |
| BR-119 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/NewsController.kt:64` | ERR | `page`/`pageSize` clamp'ить (`page >= 0`, `pageSize` в разумном максимуме), ошибку SQL не глотать, а отдавать 5xx. |
| BR-120 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/PromoController.kt:90` | ERR | валидировать/парсить безопасно (как в `NewsController.parsePublishAt`), возвращать 400 с описанием поля. |
| BR-121 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/YandexLyricsSearchOutcome.kt:59` | PERF | убрать «прогрев» либо переиспользовать один контекст на операцию; сериализовать доступ к `USER_DATA_DIR` (lock) или давать каждому вызову отдельный профиль (копию storageState); за… |
| BR-122 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/WaveformCompare.kt:129` | LEAK | `waitFor(timeout)` + `destroyForcibly()` при истечении, закрывать/дренировать поток в `finally`, удалять temp-файл при любом исходе. |
| BR-129 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongAssignmentDraft.kt:157` | ERR | обернуть statement в `use { }` (гарантированное закрытие на любой ветке), логировать через SLF4J с категорией, возвращать различаемый результат и в вызывающих не удалять назначение… |
| BR-130 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongVoice.kt:67` | ERR | в начале `actuateChilds` выходить при `getLines().isEmpty()`; `chords.maxOf`/`textLines.maxBy` заменить на `maxOfOrNull`/`maxByOrNull` с обработкой `null`; `linesTransformPropertie… |
| BR-131 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongArtifactRenamer.kt:72` | ERR | собирать неудачи (файл + причина) и писать WARN в лог; возвращать их наружу, чтобы `SongUpdateMapper` мог не сохранять новое `fileName` (или вернуть `fileNameRenameError`, как для … |
| BR-134 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/checks/StemJobsStuckCheck.kt:22` | ERR | не подменять ошибку нулём — либо пробрасывать исключение (тик сделает WARNING `check.<Name>.failure`), либо возвращать явный алерт «проверка недоступна» с `detail`; логировать чере… |
| BR-135 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/MonitoringService.kt:40` | RACE | `fixedDelay` вместо `fixedRate` плюс явный guard (`AtomicBoolean.compareAndSet`) с логом «skipped — previous tick still running»; долгую диагностику (curl/ssh) вынести из тика в от… |
| BR-136 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/AdminTaskService.kt:41` | LEAK | удалять завершённые задачи по TTL (например, `finishedAt + 1 ч`) или ограничить карту (`LinkedHashMap` с eviction/`Caffeine`), добавить `@PreDestroy` с `executor.shutdown()`. |
| BR-137 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/AlignmentServiceClient.kt:73` | ERR | логировать исключение через SLF4J (`infra.*`-категория, `Throwable`) или возвращать типизированную причину; вынести `OkHttpClient` в общий singleton (с `callTimeout` из настроек). |
| BR-144 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageMetadataCache.kt:325` | DATA | не кешировать результат, полученный из недоступного хранилища (передавать в loader признак «ответ достоверен» либо возвращать `Boolean?` и писать в кеш только `true/false` при успе… |
| BR-145 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageCircuitBreaker.kt:249` | ERR | обнулять `failureCount` на любом успехе в `CLOSED` (или вести скользящее окно/время последнего сбоя), чтобы порог означал именно серию подряд. |
| BR-146 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramAutoPublishScheduler.kt:149` | ERR | логировать исключение через SLF4J (`log.warn(..., e)`) и различать «ошибка» от «пусто» (например, не продолжать тик, а вернуть признак сбоя); `printStackTrace` заменить на логгер. |
| BR-147 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SseNotificationService.kt:217` | RACE | не разыменовывать повторно — `val list = emitters.computeIfAbsent(key) { mutableListOf() }` и работать с `list`; удаление ключа делать только под тем же lock'ом, что и вставку. |
| BR-149 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishService.kt:243` | ERR | различать «уже выполняется» и «не удалось поставить»: при `0` оставлять состояние `RENDERING` и не писать ошибку (или возвращать из `createProcess` типизированный результат `ALREAD… |
| BR-150 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishService.kt:279` | RACE | под локом перечитывать песню целиком (или отдельным `SELECT news_premium_vk_sent`) и проверять флаг по свежему значению; для PREMIUM — атомарный CAS-флаг в БД перед отправкой. |
| BR-151 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishScheduler.kt:55` | ERR | `log.error("VkAutoPublishScheduler.tick failed", e)`; обернуть обработку каждой новости в `runCatching` (как `StemJobPollScheduler.pollWaiting`), чтобы сбой одной не блокировал ост… |
| BR-152 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishScheduler.kt:181` | ERR | логировать `SQLException` (SQLState + сообщение) через SLF4J и возвращать различаемый результат (успех/ошибка), чтобы вызывающий не публиковал текст с потерянным `{newsBody}`; не п… |
| BR-153 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkApiClient.kt:481` | ERR | обернуть `proxy.send` в `try/catch` и бросать `VkNetworkException` (симметрично прямой ветке); при сбое прокси — рассмотреть fallback на прямой запрос; ошибки разбора ответа VK API… |
| BR-154 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkApiClient.kt:319` | LEAK | держать один `HttpClient` на конфигурацию прокси (кэш по URL или пересоздавать только при смене `vkProxyUrl`), один `VkApiClient` на синглтон, один `OkHttpClient` на `WhisperAsrSer… |
| BR-155 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishService.kt:40` | LEAK | удалять лок после выхода из `synchronized` (ref-count или `ConcurrentHashMap.remove(id, lock)` под проверкой), либо перейти на striped locks фиксированного размера. |
| BR-156 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishScheduler.kt:187` | PERF | отбирать кандидатов одним SQL с `JOIN tbl_songs` по `(id_vk IS NULL OR id_vk = '')` и `LIMIT`; не грузить полную песню, пока не подтверждено, что публикация нужна. |
| BR-157 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishScheduler.kt:43` | ERR | занимать слот только при фактическом `wall.post` (или возвращать слот при `SCHEDULED`/`RENDERING`/`SEND_FAILED`); при необходимости персистить счётчик. |
| BR-158 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramUpdatesConsumer.kt:64` | ERR | обернуть `deleteWebhook` в `try/catch` с логом; в конце `doStart` (в `finally`) выставлять `isWork=false`; синхронизировать `start()` (или `AtomicBoolean.compareAndSet`) и не остав… |
| BR-159 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/WhisperAsrService.kt:113` | ERR | различать «не настроено» (null) и «ошибка» (результат/исключение с HTTP-кодом и телом), логировать WARN/ERROR; не глушить `InterruptedException`. |
| BR-160 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkIdTokenRefreshScheduler.kt:44` | ERR | порог не меньше интервала cron (45–55 мин) либо тик чаще (например, каждые 10 минут); при истечении токена — обновлять по требованию перед запросом. |
| BR-163 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeStorageService.kt:347` | LEAK | обернуть поток в `use { }` внутри path-перегрузки (и в местах, где поток создаёт сам вызывающий); при массовых загрузках — ограничить батч/явно закрывать. |
| BR-164 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeStorageService.kt:499` | ERR | различать коды (`NoSuchKey`/`NoSuchBucket` → отсутствие, остальное → исключение или явный «неизвестно»); для `getFileStat` не подменять ошибку `null`-ом, а пробрасывать/возвращать … |
| BR-165 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/PremiumAutoPublishScheduler.kt:397` | ERR | логировать через SLF4J с `Throwable` и фазой/`songId`; не возвращать «пусто» на ошибке БД, а прерывать тик с явным WARN (следующий тик повторит); `printStackTrace` заменить логгеро… |
| BR-166 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/PremiumAutoPublishScheduler.kt:231` | ERR | обрабатывать все нетерминальные коды явно (считать попытку/писать `premiumAutoPublishLastError`/логировать причину), ограничить общее число попыток и по исчерпанию переводить в `FA… |
| BR-167 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SponsrSyncService.kt:152` | DATA | обернуть обработку одного пользователя в `try/catch` с логом (`siteUserId`, причина), продолжить батч, вернуть в результате список неудачных; либо одна транзакция на весь батч (тог… |
| BR-168 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeProcessAdminService.kt:347` | DATA | выполнять UPDATE и audit в одной транзакции (`autoCommit=false` + `commit`/`rollback`), либо batch-INSERT audit до/вместе с UPDATE; для bulk — один `INSERT ... SELECT`/batch по все… |
| BR-169 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/cleanup/ArtifactCleanupPlanner.kt:145` | ERR | записывать факт прерывания обхода в `CleanupPlan.warnings` (или отдельным полем) и отклонять `apply` по такому |
| BR-170 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/cleanup/ArtifactCleanupService.kt:186` | ERR | трактовать `append == false` как незавершённое удаление — прекращать прогон и переводить его в `FAILED`/`PARTIAL` |
| BR-171 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/cleanup/ArtifactCleanupService.kt:141` | RACE | делать `runId` уникальным на прогон (счётчик/UUID в составе, как в контракте) и/или отклонять второй `apply` для |
| BR-172 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/textfiledictionary/TextFileDictionary.kt:24` | ERR | заменить reflection на явный `when (dictAction)` с фиксированным набором операций (или проверять арность и ловить |
| BR-173 | `karaoke-app/src/main/resources/application.yml:67` | BUILD | удалить блок `app:` (или связать его с реальным лимитом), чтобы в файле остался один источник истины. |

### PKG-S3-24 — karaoke-app: COMPAT, DATA, ERR, PERF, RACE, SEC — 40 BR

- **Severity:** S3 · **модули:** karaoke-app · **BR:** 40
- **Волна:** E · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 23 · **Риск:** публичный периметр, данные/безопасность
- **Темы внутри:** full-walk-http, cursor-order, news-sql-swallow, iioexception-npe, exponential-splits, unique-race…

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-174 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/cleanup/ArtifactCleanupPlanner.kt:132` | PERF | строить отчёт вне HTTP-потока (202 + `status`, как уже сделано для удаления) либо кэшировать отчёт и проверять |
| BR-177 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/News.kt:283` | COMPAT | сделать курсор согласованным с сортировкой — либо `ORDER BY id DESC` (курсор по `id`), либо `ORDER BY publish_at DESC, id DESC` и курсор `(publish_at, id)`; при усечении отдавать п… |
| BR-178 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/News.kt:249` | ERR | логировать через SLF4J с исключением (ADR local-0005), возвращать различаемый результат (или пробрасывать), чтобы контроллер отдавал 5xx и лента не выглядела пустой при живой БД. |
| BR-179 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Picture.kt:311` | ERR | в `bi()` проверять `pathToFile.isNotBlank() && File(pathToFile).isFile` и подставлять placeholder/явную ошибку; проверять результат `ImageIO.read` на `null`; не полагаться на дефол… |
| BR-180 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Picture.kt:453` | PERF | заменить перебор разбиений на ДП по позициям разрыва (максимум строк ограничить, `O(n²·maxLines)`), либо ограничить число слов/вариантов и брать первые `k`; освобождать `Graphics2D… |
| BR-181 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Pictures.kt:272` | ERR | в `createNewPicture` повторно читать по имени не только в `catch`, но и когда `createDbInstance` вернул `null`; в `createDbInstance` логировать SQLState и не выдавать «тихий null» … |
| BR-183 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/MltNode.kt:35` | DATA | экранировать XML в одном месте (`&`, `<`, `>`, `"`, `'`) при сериализации атрибутов и текста; убрать ручные `replace` в вызывающих. |
| BR-184 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/KaraokeDbTable.kt:459` | ERR | различать «нет данных» и «ошибка» (Result/исключение); в путях create-or-get не создавать сущность при сбое чтения; добавить UNIQUE на `tbl_authors.author` либо дедупликацию. |
| BR-186 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt:118` | PERF | одним запросом загрузить картинки по именам (`WHERE name IN (…)`) и раздать их в `toDTO` параметром; либо кешировать `pictureId/url` на автора. |
| BR-187 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/KaraokeDbTable.kt:305` | ERR | заменить `printStackTrace()`/`println()` на `LoggerFactory.getLogger(...)` с `log.warn("…", e)` по конвенции ADR local-0005; сохранить `println` только там, где он осознанно нужен … |
| BR-189 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Publication.kt:1471` | DATA | ввести инвариант «не более одной песни на `(publish_date, publish_time)`» — частичный UNIQUE-индекс в БД плюс проверка перед сохранением (по образцу editorial-инварианта `(song_id,… |
| BR-190 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Publication.kt:1357` | COMPAT | свести имя ключа к одному (`filter_date_from`) во всех контроллерах либо читать оба ключа в модели (`args["filter_date_from"] ?: args["publish_date_from"]`); добавить тест на `getP… |
| BR-191 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Publication.kt:1455` | ERR | валидировать `publish_date`/`publish_time` на записи (регулярка + календарная проверка) и отклонять неверный формат с понятной ошибкой; в `getPublicationList` — разбирать дату один… |
| BR-192 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SearchResult.kt:352` | PERF | заменить цикл на `KaraokeDbTable.deleteIn(TABLE_NAME, ids, database)` (один DELETE), проверять возвращённое число строк и логировать расхождение; при необходимости обернуть удалени… |
| BR-193 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SiteChatMessage.kt:147` | ERR | логировать через SLF4J с объектом исключения (`log.warn("…", e)`), как требует ADR `local-0005`; для методов-счётчиков и `markThreadRead` возвращать признак ошибки (Boolean/Result)… |
| BR-194 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SiteUser.kt:106` | ERR | ставить флаг только после успешного создания сообщения и успешного сохранения (`createNew(...) ?: return`, затем проверять результат `save()`), либо сделать операцию атомарной в БД… |
| BR-196 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/StatBySong.kt:268` | PERF | единый helper нормализации для всех методов статистики (`limit.coerceIn(1, MAX)`, `offset.coerceAtLeast(0)`), включая `offset` в `getStatBySong`, либо валидация в контроллере с `40… |
| BR-197 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Uuids.kt:153` | PERF | один раз за рендер (или один раз на процесс с инвалидацией) загружать все пары `(id, uuid)` пакетно (`SELECT id, uuid FROM tbl_uuids`, несколько тысяч строк) и отдавать из in-memor… |
| BR-198 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/StemJob.kt:266` | RACE | атомарная вставка с условием (`INSERT … SELECT … WHERE (SELECT count(*) …) < 5` в одной транзакции) либо `pg_advisory_xact_lock` по `site_user_id`; ошибку подсчёта не подменять нул… |
| BR-199 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Uuids.kt:30` | ERR | убрать запятую (`SET uuid = ? WHERE id = ?`), обернуть statement в `use {}`, ошибку логировать через SLF4J (ADR local-0005), а не пробрасывать молча в UI-поток. |
| BR-202 | `karaoke-app/src/main/resources/templates/main.html:296` | COMPAT | заменить `${songAuthors}` на `${authors}` (имя, которое реально кладёт контроллер) либо переименовать атрибут в контроллере. |
| BR-203 | `karaoke-app/src/main/resources/templates/editsubs.html:434` | SEC | собирать результат через `textContent`/`document.createTextNode` (или экранировать label перед вставкой), `innerHTML` оставить только для собственных `<span>`/`<br>`; текст в `<tex… |
| BR-204 | `karaoke-app/src/main/resources/templates/editsubs.html:365` | ERR | вернуть значение из функции (`for` с `return` или `Array.from(buttons).find(b => b.classList.contains('active')).value`), а признаком выбора считать класс `active`. |
| BR-207 | `karaoke-app/src/main/resources/templates/songs.html:496` | ERR | проверять `currentRow`/`el_id` до любых действий; не менять DOM до подтверждения 2xx; добавить `error`/`fail` с показом статуса и откатом строки; серверу — возвращать результат (20… |
| BR-208 | `karaoke-app/src/main/resources/templates/songs.html:1084` | RACE | сохранять строку в локальную переменную до запроса (`var row = currentRow`) и удалять её; серверу — возвращать число удалённых строк/404, а клиенту проверять результат. |
| BR-209 | `karaoke-app/src/main/resources/templates/songs.html:2177` | COMPAT | заменить на `${authors}`/`${albums}` либо добавить в модель `songAuthors`/`songAlbums`; проверить те же имена в `main.html:296` и `area_left_column.html:6–9` (там тоже `songAuthors… |
| BR-210 | `karaoke-app/src/main/resources/templates/songs.html:723` | COMPAT | читать `filter_flag_tl/tk/tc` и добавлять `flag_telegram_*` в URL; при желании — тест/guard на соответствие полей формы параметрам контроллера (`check-endpoint-field-coverage.sh` с… |
| BR-211 | `karaoke-app/src/main/resources/templates/songs.html:88` | COMPAT | перевести страницу на актуальный канал `SseNotificationService` (`/api/subscribe`, SSE) либо вернуть STOMP-конфиг в `karaoke-app`; синхронизировать имя таблицы (`tbl_songs`) и ключ… |
| BR-212 | `karaoke-app/src/main/resources/templates/songs.html:735` | ERR | использовать существующий `copyToClipboard` либо feature-detect: `if (navigator.clipboard?.writeText) ... else copyToClipboard(...)`, с сообщением при отказе. |
| BR-215 | `karaoke-app/src/main/resources/templates/songs2.html:579` | COMPAT | добавить три поля в формируемый URL (и заполнять их из `param`, если состояние фильтра должно сохраняться после перезагрузки). |
| BR-216 | `karaoke-app/src/main/resources/templates/songs2.html:2083` | COMPAT | читать `recordId`/`tableName`/`diffs`, сравнивать с `tbl_songs` (`tbl_processes` — как есть), добавить `try/catch` вокруг `JSON.parse`. |
| BR-217 | `karaoke-app/src/main/resources/templates/songs2.html:2009` | ERR | проверять `status`/`onerror` (или `$.ajax({error: …})`), показывать причину, ставить индикатор только после успешного ответа. |
| BR-218 | `karaoke-app/src/main/resources/templates/songs2.html:626` | PERF | перевести на async (`Promise`/`await` или `$.ajax`), блокировать кнопку на время запроса. |
| BR-220 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SitePlaylistsController.kt:46` | PERF | один агрегат `SELECT playlist_id, count(*) FROM tbl_site_playlist_items WHERE playlist_id IN (…) GROUP BY playlist_id` и батч владельцев через `KaraokeDbTable.loadByIds` (ADR-0001,… |
| BR-221 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SiteUsersController.kt:81` | PERF | SQL `LIMIT/OFFSET` (или keyset) + отдельный `COUNT(*)` для `totalCount`; фильтры (`filterStatus`/`filterAuthor` у `songeditor`) перенести в `WHERE`, а не фильтровать в памяти после… |
| BR-222 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt:341` | DATA | валидировать `idStatus` до любых мутаций; переводить статус задания атомарным `UPDATE … WHERE admin_status <> 'APPROVED' RETURNING` до/вместе с применением разметки и проверять фак… |
| BR-223 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt:360` | RACE | атомарный переход статуса (`UPDATE tbl_song_assignments SET admin_status='APPROVED' WHERE id=? AND admin_status<>'APPROVED' RETURNING id`) и запуск push/render только победителем; … |
| BR-224 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt:463` | ERR | `private val log = LoggerFactory.getLogger(SongEditorController::class.java)` и `log.error("approve:push:failed songId={} durationMs={}", song.id, …, e)`; то же для sync-related; н… |
| BR-225 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/docs:0` | COMPAT |  |
| BR-226 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/StatsController.kt:71` | PERF | single-flight на ключ (например, `ConcurrentHashMap<StatsCacheKey, CompletableFuture<Any>>` или `PollingCache.getOrCompute` с guard'ом), чтобы второй запрос ждал результат первого,… |

### PKG-S3-25 — karaoke-app: COMPAT, DATA, ERR, LEAK, PERF, RACE, SEC — 40 BR

- **Severity:** S3 · **модули:** karaoke-app · **BR:** 40
- **Волна:** E · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 16 · **Риск:** данные/безопасность
- **Темы внутри:** jsoup-no-limit, searxng-no-filter, uuid-per-node, process-update-validation, ajax-redirect-full-render, telegram-vk-headers…

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-228 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/LyricsFinderService.kt:155` | PERF | задать `.maxBodySize(...)` (порядка 1–2 МБ, с запасом над 8 000 символов после очистки) и/или ограничить по `Content-Length`; семантика обрезки для LLM не меняется. |
| BR-229 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Tools.kt:107` | ERR | применить тот же `filterUselessLyricsUrls` (и дедупликацию) в `searchUrlsViaSearxng` — один фильтр на оба движка; либо явно задокументировать разницу и причину. |
| BR-230 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/Mlt.kt:50` | PERF | получать/создавать UUID пачкой (один `SELECT … WHERE id IN (…)` + `INSERT … ON CONFLICT DO NOTHING` на песню) либо кэшировать `getStoredUuid` в памяти на время генерации; не ходить… |
| BR-231 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt:219` | ERR | брать числа через `toLongOrNull()`/`toIntOrNull()` и отклонять запрос с 400 (или игнорировать поле), объявить параметры nullable с дефолтами, проверять `process_status` по `Karaoke… |
| BR-232 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt:229` | PERF | для XHR возвращать `204`/короткий JSON (`{ok:true}`) и обновлять строку на клиенте; либо отдельный batch-эндпоинт для «New prior»; передавать `limit`/`offset` в `loadList` на стран… |
| BR-233 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt:1263` | COMPAT | в трёх методах (`getSongTextTelegramLyricsHeader` `:1263`, `…KaraokeHeader` `:1283`, `…ChordsHeader` `:1303`) заменить `getDescriptionVkHeader` на `getDescriptionTelegramHeader` (к… |
| BR-236 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt:702` | ERR | логировать исключение через логгер со стеком и `songId`/`voice`; возвращать различимый код (400 для невалидного JSON, 404 для отсутствующей песни) или JSON `{ok:false,error}` и про… |
| BR-238 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko/MkoSplashStart.kt:49` | ERR | заменить `!==` на `!=` (или `isNotEmpty()`); чтобы ветка с описанием аккордов вообще могла работать — заполнять `SONG_CHORD_DESCRIPTION` (например, из `Song.getChordDescription(son… |
| BR-239 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko/MkoMelodyTabs.kt:238` | ERR | валидировать `stringLad` (`Regex("^[0-5]\\|\\d{1,2}$")`), при несоответствии — не рисовать таб и логировать предупреждение, а не бросать; либо валидировать маркеры при сохранении в… |
| BR-240 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Album.kt:589` | ERR | различать «нет строк» и «ошибка» (проброс исключения или `Result`/`null`), на публичном эндпоинте отдавать `503`/ошибку, а не пустой список; не кэшировать результат, полученный пос… |
| BR-241 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko/MkoMelodyTabs.kt:98` | ERR | не превращать отсутствие элемента в nameless-узел: либо не создавать producer (`producer()`/`template()` возвращают `null` и `xmldata` не добавляется), либо возвращать валидный пус… |
| BR-242 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko/MkoMainBin.kt:150` | COMPAT | для не-sequence типов ссылаться на существующий узел — `MltGenerator.nameTractor(type, voiceId, childId, elementId)` (для `LINETRACK` он создаётся `MkoLineTrack.tractor()`), либо н… |
| BR-243 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko/MkoCounter.kt:76` | ERR | проверять размер списка и подставлять fallback (последний/первый цвет, дефолтный шрифт) вместо индексации; при невалидном значении свойства — писать в лог и использовать `defaultVa… |
| BR-245 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt:155` | ERR | `toLongOrNull`/`toIntOrNull`/`toDoubleOrNull` → 400 с причиной; boolean — явный разбор (`true/false/1/0`, иначе 400); неизвестный ключ — 400/404 вместо no-op; `getDTO`/`getProperty… |
| BR-246 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt:88` | COMPAT | зарегистрировать три ключа в `listKaraokeProperties` с типами и дефолтами из спецификаций (Boolean/`false` для `batch-enabled`, Int/`500` для `batch-max-buffer-size`, Boolean/`true… |
| BR-248 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt:39` | RACE | `@Volatile` + двойная проверка под `synchronized`/`ReentrantLock`; публиковать карту целиком после загрузки (immutable snapshot в `@Volatile`-поле), а не наполнять общий `HashMap` … |
| BR-249 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt:75` | ERR |  |
| BR-250 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt:65` | DATA | разбирать построчно, пропуская битые строки с логом «ключ/строка», а не отклонять файл целиком; при провале загрузки заполнять карту дефолтами всех ключей **до** первой записи; 0-б… |
| BR-252 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:859` | DATA | выполнять DELETE+INSERT в одной транзакции (`autoCommit=false`, `commit`/`rollback`) или сначала INSERT с `ON CONFLICT (id) DO UPDATE`; проверку соединения делать до удаления; искл… |
| BR-253 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:262` | RACE | заменить пару «флаг + присваивание» на `AtomicBoolean.compareAndSet(false, true)` либо `synchronized`-блок; снимать флаг в `finally` (см. P-07-05). |
| BR-254 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:370` | ERR | обернуть тело потока в `try { … } finally { flag = false }` и снимать флаг также при неудаче создания потока (ставить флаг внутри `try` вокруг `thread {}` либо использовать `Atomic… |
| BR-255 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:2590` | LEAK | `Files.walk(Path(pathToFolder)).use { stream -> stream.filter(...).map(...).toList() }`; ошибки обхода логировать (не глушить) и отличать от «не найдено» — как минимум вернуть приз… |
| BR-256 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:1607` | ERR | ловить `Exception` (или `RuntimeException`) вокруг разбора строки и считать такую строку пропуском с логированием `id`/файла; заменить `printStackTrace()` на логгер; возвращать счё… |
| BR-257 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:2132` | DATA | во всех ветках брать текстовое содержимое (`ownText()`/`text()`), а при необходимости сохранить переводы строк — предварительно заменять блочные теги на `\n`; перед записью в `sour… |
| BR-259 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/StemJobCleanup.kt:52` | RACE | сопоставлять шаги по `jobId` (префикс описания/отдельное поле), а не по точной строке; `executeFinalizeStemJob` должен перечитывать статус задания и не финализировать задание в `ER… |
| BR-260 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/StemJobPollScheduler.kt:82` | DATA | ставить `WORKING` только после успешной вставки шага (или откатывать в `ERROR` при `null`); проверять результат `createDbInstance` в обоих местах. |
| BR-261 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsAI.kt:104` | PERF | присваивать `Karaoke.requestIamTokenLastTimeMs = currentDateTime` сразу после успешного обновления; ошибку получения токена отделять от ошибки HTTP-поиска (свой тип/сообщение, без … |
| BR-262 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsAI.kt:345` | ERR | собирать тело через `ObjectMapper.writeValueAsString` (Jackson уже в зависимостях) или явно экранировать `"`, `\` и управляющие символы в `queryText`. |
| BR-263 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/StemJobPollScheduler.kt:74` | ERR | развести три исхода `ffprobe` (успех / не удалось измерить / превышение лимита) и писать в `errorMessage` фактическую причину. |
| BR-264 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/SyncAudioDescendants.kt:344` | ERR | возвращать различие «пусто» и «ошибка» (исключение/`Result`), в `syncAll` отдавать ошибку вместо `"OK"`, в сводку выводить счётчик ошибок. |
| BR-265 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsAI.kt:865` | ERR | логировать причину (`e::class.simpleName`, `message`) и различать в возвращаемом значении сетевой сбой, не-2xx и пустой документ. |
| BR-267 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:3583` | ERR | заменить на `author.ymId != ""` (или `author.ymId.isNotEmpty()`); условие целиком выразить через `Author.haveNewAlbum`. |
| BR-268 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:3065` | DATA | в `getSyllables` обрабатывать все слова (`findAll`/`split` + проход слияния один раз по строке, как уже сделано в `WhisperMarkerAligner.buildTargetWords`, `WhisperMarkerAligner.kt:… |
| BR-269 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:3674` | LEAK | читать тело в `conn.inputStream.use { it.bufferedReader().use(BufferedReader::readText) }`, а соединение закрывать в `finally { conn.disconnect() }`; ошибку логировать, а не только… |
| BR-270 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4614` | ERR | `process.waitFor(timeout, TimeUnit.MILLISECONDS)` с `destroyForcibly()` при истечении; `reader.use { }`; убивать и закрывать процесс в `finally`; ограничить объём накапливаемого вы… |
| BR-271 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4102` | SEC | не собирать shell-строку из значений — передавать аргументы позиционными параметрами (`sh script "$1" "$2"`) или через `exec` с массивом; экранировать `'` как `'\''`; права `700` в… |
| BR-272 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4657` | ERR | обрабатывать `null` от `getChordNote` (возвращать исходное значение и логировать один раз), убрать `!!`; при пустой тональности не транспонировать. |
| BR-273 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4907` | PERF | один запрос на весь цикл (или SQL-нормализация с индексом) и `Map` «нормализованное имя → кандидат»; `findDuplicateOriginal`/`searchSongsByNormalizedName` — та же пакетная загрузка… |
| BR-274 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeConnection.kt:54` | PERF | валидировать соединение только при открытии и после `SQLException` (флаг «помечено битым»), а не в горячем пути; `isValid` — по таймеру/при простое, не на каждый вызов. |
| BR-275 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeConnection.kt:67` | ERR | передавать в `log.warn` сам объект исключения последним аргументом (SLF4J добавит стек и cause), сохранив placeholder'ы; `println` оставить только с сообщением. |

### PKG-S3-26 — karaoke-app: DATA, ERR, PERF, RACE, RULE, SEC — 19 BR

- **Severity:** S3 · **модули:** karaoke-app · **BR:** 19
- **Волна:** E · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 11 · **Риск:** данные/безопасность
- **Темы внутри:** voices-key-flashcolor, broken-symlink-repair-noop, n-plus-one-processes, cached-fileinfo-ok, repair-guard-stuck, remote-delete-swallow…

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-276 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Karaoke.kt:652` | DATA | заменить ключ на `"voices"`; добавить тест round-trip свойства (запись → чтение). |
| BR-277 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4738` | ERR | использовать `Files.isSymbolicLink(path)` / `Files.deleteIfExists` без предварительного `exists()`, либо `File.exists() || Files.isSymbolicLink()`. |
| BR-280 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:752` | PERF | один запрос на песню — `loadList(args = mapOf("song_id" to …))` с фильтрацией по `process_type`/`thread_id`/`process_args` в Kotlin (или `IN`-список типов), результат передавать в … |
| BR-281 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:183` | ERR | различать «объекта нет» и «не удалось узнать» (типизированный результат/`Result`/`null` только для «неизвестно» с `WARN`-логом в `catch`), и не считать `null` доказательством актуа… |
| BR-282 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:2749` | RACE | брать и снимать guard в `try/finally` вокруг **всего** тела (включая синхронный `recomputeAndBroadcast` и `submit`), либо снимать guard в `catch` перед пробросом. |
| BR-283 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:1176` | ERR | не глушить ошибку удаления в лямбде: логировать через SLF4J с throwable и помечать действие неуспешным (пробросить исключение в repair-цикл или вернуть результат в эндпоинт), чтобы… |
| BR-284 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:2726` | ERR | оборачивать каждое действие в `try/catch` с логом и продолжением (или собирать ошибки и отправлять SSE/`Message(type="error")`), а в задаче пула — `catch (e: Exception)` с финальны… |
| BR-285 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:944` | RULE | выполнять `solutionActions` одиночного отчёта так же, как каскад, — в `repairExecutor` (или в очереди `KaraokeProcess`), а эндпоинт делать `202 Accepted` с результатом по SSE. |
| BR-286 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:2462` | ERR | добавлять `HealthReport(WARNING, "Каталог ссылок не прочитан: …")` (без действия) и логировать исключение целиком через SLF4J, чтобы пропуск проверки был виден. |
| BR-287 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:2679` | ERR | возвращать пару (обработано, ошибок) или бросать/сообщать об ошибках отдельным SSE-событием; в `catch` логировать throwable через SLF4J, а не только `e.message`. |
| BR-288 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/CacheEtagSizeBackfill.kt:169` | ERR | различать «ошибка выборки» и «нечего обрабатывать»: при исключении/`null`-соединении отправлять SSE `error` и не печатать «завершено»; возвращать из `loadRowsNeedingBackfill`/выбор… |
| BR-289 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Extentions.kt:213` | ERR | экранировать значение словаря (`Regex.escape`) при сборке шаблона; отбрасывать/логировать пустые и заведомо неразбираемые значения при записи в словарь; собирать шаблоны один раз (… |
| BR-290 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Functions.kt:241` | ERR | не делить на `maxDuration <= 0` — при нулевом максимуме не вставлять пустые линии (или считать базу от `song.songLengthMs`/1 мс); отбрасывать пустые и комментарийные линии до расчё… |
| BR-291 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Crypto.kt:83` | ERR | SLF4J `log.error` с типом операции (без значений/ключей); валидировать длину ключа (16/24/32 байта) и IV (16 байт) при старте с явным отказом; возвращать типизированный результат и… |
| BR-292 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Converter.kt:30` | ERR | проверять размер после `split` и подставлять `defaultValue` свойства (как это уже сделано для отсутствующих полей заливки через `?:`), а `Color`/`Font` строить внутри `try` с fallb… |
| BR-293 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/AlbumCoverFinder.kt:125` | ERR | обернуть Яндекс-ветку в `try/catch`, логировать и возвращать `NotFound` (как уже сделано для `VpnBlocked`/`BotDetected`/`AuthExpired`), чтобы фолбэк работал при любом сбое; для про… |
| BR-294 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Extentions.kt:235` | PERF | читать словарь один раз на пакет и передавать разобранные пары в `censored(...)` (или кэшировать `Pattern` с инвалидацией по версии словаря); компилировать шаблоны один раз. |
| BR-526 | `karaoke-app/src/main/resources/test.xml:1` | SEC | удалить `karaoke-app/src/main/resources/test.xml` (потребителя нет, файл не участвует ни в одной фиче); если это задел на интеграцию с ЕГРН — перенести в `src/test/resources` и зам… |
| BR-531 | `karaoke-app/src/test/kotlin/Правильный.xml:1` | SEC | удалить файл (потребителя нет); если он нужен как эталон MLT — переименовать в `src/test/resources/` с обезличенными путями (относительные, без личных каталогов) и с очищенными име… |

### PKG-S3-27 — karaoke-public: COMPAT, DATA, ERR, LEAK, PERF, RACE, SEC — 40 BR

- **Severity:** S3 · **модули:** karaoke-public · **BR:** 40
- **Волна:** E · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 23 · **Риск:** публичный периметр, данные/безопасность
- **Темы внутри:** clipboard-false-success, cart-toggle-error-ignored, chat-badge-premium-watch, share-secret-localstorage, price-request-race, share-created-poll-mismatch…

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-296 | `karaoke-public/src/components/ShareButton.vue:77` | ERR | `copyToClipboard()` должен возвращать `true/false` (`navigator.clipboard` успех ИЛИ `document.execCommand('copy') === true`); `copied` выставлять только при успехе, иначе показыват… |
| BR-297 | `karaoke-public/src/components/CartIcon.vue:38` | ERR | обрабатывать `status`/`body.error`: для `already_subscribed` — показывать «подписка уже оформлена» (и скрывать/деактивировать иконку), для `song_not_for_subscription` — «подписка н… |
| BR-298 | `karaoke-public/src/components/ChatUnreadBadge.vue:53` | ERR | наблюдать за `isPremium` (`watch: { isPremium(v) { v ? this.startPolling() : this.stopPolling() } }`) вместо/вместе с `isLoggedIn`; `visible` уже реактивен. |
| BR-299 | `karaoke-public/src/components/ShareLinkModal.vue:107` | SEC | не хранить capability-URL в `localStorage` (держать в памяти/`sessionStorage` на время вкладки) либо очищать префикс `karaoke-share-url:` в `clearSession()`/при logout и по истечен… |
| BR-300 | `karaoke-public/src/components/SongSubscriptionModal.vue:132` | RACE | ввести счётчик поколений (`let seq = 0`) и применять ответ только если `seq` не изменился; сбрасывать `priceInfo`/`loadingPrice` при смене `songId`; при необходимости — `AbortContr… |
| BR-301 | `karaoke-public/src/components/ShareLinkModal.vue:27` | ERR | при расхождении `currentLink.linkId` с созданным (или `currentLink.active === false`/`null`) сбрасывать `created` и показывать фактическое состояние; либо не дублировать состояние … |
| BR-304 | `karaoke-public/src/player/KaraokePlayer.js:290` | ERR | в `catch` выходить без сообщения при `AbortError`/`signal.aborted` и при несовпадении поколения загрузки; успешное завершение `init()` должно скрывать `#kp-loading` (или сообщение … |
| BR-305 | `karaoke-public/src/player/KaraokePlayer.js:1653` | ERR | при паузе в pre-roll сохранять состояние так, чтобы `_getDisplayTime()` продолжал возвращать `_dtPaused` (оставлять `_isPrerolling=true` с отдельным флагом «на паузе» либо переводи… |
| BR-306 | `karaoke-public/src/player/KaraokePlayer.js:2128` | DATA | захватить `const songId = this.songId` в замыкание таймера; очищать `_seekTrackTimer` в `playSong()` и `destroy()`. |
| BR-307 | `karaoke-public/src/player/KaraokePlayer.js:1516` | LEAK | ввести поколение загрузки/флаг `_destroyed` и перед созданием инстансов сверять его; при устаревшем поколении не создавать WaveSurfer (или сразу уничтожать созданное); в `destroy()… |
| BR-308 | `karaoke-public/src/player/KaraokePlayer.js:1785` | PERF | вынести обработку в Web Worker/AudioWorklet (OfflineAudioContext) либо резать на чанки с `await` между ними; не держать одновременно оригиналы и две транспонированные копии (освобо… |
| BR-309 | `karaoke-public/src/player/KaraokePlayer.js:2249` | PERF | генерировать текстуру один раз на инстанс либо кэшировать по варианту палитры/размеру (seed влияет только на выбор палитры и раскладку — можно кэшировать 6 вариантов); уменьшить ра… |
| BR-310 | `karaoke-public/src/player/KaraokePlayer.js:1905` | ERR | обернуть чтение в `try/catch` с fallback `_transpose = 0` (как в `_loadPersistedSettings`), чтобы недоступное хранилище не блокировало плеер. |
| BR-311 | `karaoke-public/src/player/KaraokePlayer.js:2229` | COMPAT | проверять наличие `OffscreenCanvas` с fallback на `document.createElement('canvas')`; для `roundRect` — проверять метод и рисовать `rect`/`arc` при отсутствии; обернуть тело кадра … |
| BR-314 | `karaoke-public/src/composables/useCart.js:13` | RACE | сбрасывать `items`/`songIds`/`loaded` при смене токена (вызывать `reset()` из `useAuthBootstrap` или `watch` на токен) и не помечать корзину загруженной для анонима (грузить при ка… |
| BR-315 | `karaoke-public/src/composables/useSiteSubscription.js:32` | RACE | latest-request guard (`requestId`) или `AbortController`, чтобы применять только ответ последнего выбранного тарифа; при устаревшем ответе не трогать `priceInfo`/`error`. |
| BR-316 | `karaoke-public/src/router/index.js:172` | ERR | обернуть `dispatch` в `try/catch` и не отменять навигацию: при ошибке пропустить резолв имени (страница сама покажет «Автор не найден»/ошибку) либо отдать пользователю явное сообще… |
| BR-317 | `karaoke-public/src/composables/useNewsUnreadCount.js:120` | LEAK | останавливать polling при `!isAuthenticated()` (в `applyRouteState` и по `watch` на токен), не полагаясь только на скрытые маршруты; при следующем входе запускать заново. |
| BR-318 | `karaoke-public/src/composables/usePlaylistMembership.js:132` | ERR | обернуть `fetchPlaylists()` в `try/catch` (как соседние загрузчики), при ошибке не менять текущий список и возвращать признак ошибки; в `openMenu` показывать сообщение/повтор. |
| BR-320 | `karaoke-public/src/store/modules/zakroma.js:225` | LEAK | держать активный composable (или AbortController) в модуле store, отдавать наружу `cancelZakromaStream`/`abortZakromaStream`, вызывать `cleanup()` в `finally` и при старте нового с… |
| BR-321 | `karaoke-public/src/store/modules/songs.js:127` | RACE | один общий счётчик поколения выдачи, который инкрементят и `search`, и сброс фильтров; `loadMoreSearchResults` должен запоминать поколение на старте и отбрасывать результат при его… |
| BR-322 | `karaoke-public/src/views/HistoryView.vue:63` | ERR | единый шаблон загрузки — `try/catch/finally` с явным состоянием ошибки (`error` + retry-кнопка), различать «пусто» и «не загрузилось» по `status`; в действиях показывать сообщение … |
| BR-323 | `karaoke-public/src/store/modules/stats.js:48` | ERR | ввести состояние `error`/`loaded` в модуле `stats` (или считать `loaded` по факту `setStats`) и в шаблоне показывать «Статистика недоступна» вместо нулей; ошибку логировать/показыв… |
| BR-324 | `karaoke-public/src/views/ChatView.vue:173` | ERR | убрать early-return по пустому списку — при пустом окне запрашивать свежую страницу (без `afterId`, `limit: PAGE_SIZE`); обернуть `fetchMessages` в `try/catch` с отображением состо… |
| BR-325 | `karaoke-public/src/views/AuthorPlaylistView.vue:202` | PERF | один раз на изменение `songs`/`isPremium`/`subscriptionIds` строить `Map<songId, 'available'|'premium'|'notready'>` (computed) и в шаблоне/`playableIds`/`hasPlayable` читать из нег… |
| BR-326 | `karaoke-public/src/views/AboutView.vue:162` | COMPAT | не хранить цены в тексте страницы — либо тянуть `GET /api/public/account/subscription/tariffs?scope=SITE`/`scope=SONG` (публично доступный GET) и подставлять `priceRub`/`final`, ли… |
| BR-331 | `karaoke-public/src/views/EditorWorkView.vue:1160` | DATA | `saveNow` должен возвращать признак успеха (или бросать), а `submit` — прерываться с сообщением, если сохранение не удалось. |
| BR-332 | `karaoke-public/src/views/EditorWorkView.vue:1087` | COMPAT | не перезаписывать композитный статус значением из ответа save; перечитывать задание после сохранения либо возвращать с сервера композитный статус; баннер отклонения показывать до п… |
| BR-333 | `karaoke-public/src/views/EditorWorkView.vue:653` | LEAK | ставить флаг `_unmounted`/`AbortController` и проверять его после каждого `await`; вешать листенеры до асинхронной загрузки или снимать их в `finally`. |
| BR-334 | `karaoke-public/src/views/EditorWorkView.vue:695` | ERR | `try/catch/finally` вокруг `fetchTask` с выставлением `loadError` и `loading=false`, кнопка «Повторить». |
| BR-335 | `karaoke-public/src/views/EditorWorkView.vue:789` | PERF | обновлять превью только при смене индекса маркера (или троттлить до rAF/100 мс), а подсветку текущего слога делать переключением класса на уже отрисованных узлах, не пересобирая HT… |
| BR-336 | `karaoke-public/src/views/EditorWorkView.vue:928` | ERR | сначала загружать, потом фиксировать `activeSound` (или откатывать при ошибке), ловить reject, показывать ошибку и разрешать повтор. |
| BR-337 | `karaoke-public/src/views/EditorWorkView.vue:1313` | RACE | на `window.blur`/`document.visibilitychange` очищать все `scrubTimers` и `heldKeys`, восстанавливать `playbackRate`/паузу (как в `stopScrub`). |
| BR-338 | `karaoke-public/src/views/EditorWorkView.vue:713` | DATA | не переразмечать маркеры при отсутствующем тексте (пропускать `relabelSyllables`), не сохранять «синтезированные» пустые голоса и явно предупреждать о расхождении длин. |
| BR-341 | `karaoke-public/src/views/PlaylistEditView.vue:367` | ERR | различать 401/403 (→ «войдите заново»/`LoginRequired`) и 5xx (→ «не удалось загрузить, повторить»), а `notFound` ставить только на 404; тело ошибки (`body.error`) хотя бы логироват… |
| BR-342 | `karaoke-public/src/views/PlaylistEditView.vue:537` | ERR | обрабатывать результат каждой мутации (`await` + проверка `status`), при ошибке откатывать оптимистичное изменение и показывать сообщение; `catch` на сетевой реджект; `pushQueue` в… |
| BR-343 | `karaoke-public/src/views/NewsView.vue:70` | ERR | различать «пусто» и «ошибка»: при reject ставить отдельный флаг ошибки и показывать «Не удалось загрузить новости» + «Повторить», а `markAllSeen()` вызывать только после успешной з… |
| BR-344 | `karaoke-public/src/views/SearchView.vue:376` | ERR | восстанавливать срез явно в `mounted`/`onSearch` (последовательно грузить страницы до `pageFromUrl`, как это делает `restoreFromUrl`), а `restoreFromUrl()` для `targetPage <= 1` вы… |
| BR-345 | `karaoke-public/src/views/SearchView.vue:202` | ERR | отдельный флаг ошибки для первичного поиска (`searchError`) и отдельный для подгрузки; «Повторить» должен повторять то действие, которое упало (первичный поиск — заново `search({pa… |
| BR-346 | `karaoke-public/src/views/PremiumView.vue:93` | ERR | показывать `error` из composable в ветке `v-else` (цена недоступна → сообщение + «Повторить» через `loadPrice(selectedTariffId)`), а `submitError` оставить только для ошибок оформл… |

### PKG-S3-28 — karaoke-public: COMPAT, ERR, LEAK, PERF, RACE, SEC — 15 BR

- **Severity:** S3 · **модули:** karaoke-public · **BR:** 15
- **Волна:** E · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 8 · **Риск:** публичный периметр, данные/безопасность
- **Темы внутри:** author-id-find, request-token-timeout, postmessage-origin, return-timeout, cancel-ignores-status, route-state…

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-347 | `karaoke-public/src/views/SearchView.vue:123` | PERF | один `computed` c `Map<authorName, authorId>` (перестраивается только при смене `authorTiles`) и один вызов на строку (результат — в локальную переменную/`v-slot`), либо прокинуть … |
| BR-348 | `karaoke-public/src/views/PlayerView.vue:87` | LEAK | хранить id таймера (или `AbortController`/флаг `destroyed`) и в `onBeforeUnmount` очищать таймеры и резолвить все `pendingToken` как `null`; в `playPos`/`advanceAfterEnd` проверять… |
| BR-349 | `karaoke-public/src/views/PlayerView.vue:129` | SEC | в `onParentMessage` проверять `e.origin === window.location.origin` и `e.source === window.parent` (и `e.source === playerIframe`-эквивалент для родителя); на сервере/nginx добавит… |
| BR-351 | `karaoke-public/src/views/SubscriptionReturnView.vue:150` | LEAK | хранить id таймера и очищать его в `beforeUnmount`; перед `$router.replace` проверять, что компонент ещё смонтирован и маршрут всё ещё `/subscription/return`. |
| BR-352 | `karaoke-public/src/views/SubscriptionsView.vue:102` | ERR | проверять `status === 200`; при ошибке показывать текст («не удалось отключить автопродление») и не перезагружать список как при успехе; успех подтверждать сообщением. |
| BR-353 | `karaoke-public/src/views/ZakromaView.vue:437` | ERR |  |
| BR-354 | `karaoke-public/src/views/ZakromaView.vue:189` | COMPAT | либо добавить авторские поля в NDJSON-сообщение `meta` (и в `ZakromaAlbumMetaPublicDto`/`meta`-DTO), либо гидрировать блок автора из `authorTiles`/отдельного запроса, либо сохранят… |
| BR-355 | `karaoke-public/src/views/ZakromaView.vue:905` | ERR | retry должен повторять именно запуск стрима с теми же параметрами, что `tryStartZakromaStream` (`author`, `expectedCount`, `albumId`, `force: true`), без навигации; `onAuthorSelect… |
| BR-356 | `karaoke-public/src/views/ZakromaView.vue:678` | RACE |  |
| BR-357 | `karaoke-public/src/views/ZakromaView.vue:701` | ERR | явное пустое состояние «Автор не найден / у автора нет песен» (или редирект на `/zakroma` с уведомлением), выставлять его после того, как `authorTiles` загружены и тайл не найден; … |
| BR-358 | `karaoke-public/src/views/ShareView.vue:122` | ERR | развести состояния — `notfound` только для `share.notFound`/`share.tokenMissing`, отдельное `error` (с текстом «сервис недоступен») для 5xx/`share.internal`/сети, с кнопкой «Повтор… |
| BR-359 | `karaoke-public/src/views/ShareView.vue:141` | ERR |  |
| BR-360 | `karaoke-public/src/views/SongView.vue:466` | COMPAT | не заводить новое поле — использовать уже приходящий `contentRemoved` (сервер считает его из `tags` точным сравнением токена) либо добавить в `SongPublicDto` поле `skipped` (как за… |
| BR-361 | `karaoke-public/src/views/StemJobsView.vue:245` | LEAK | держать таймер на инстансе и заводить его синхронно (до `await`), а в колбэке проверять `this._isUnmounted`/`this.$el`; в `beforeUnmount` выставлять флаг и очищать таймер; либо пер… |
| BR-362 | `karaoke-public/src/views/StemJobsView.vue:392` | RACE | в `load()` отфильтровывать `job.deleteRequested === true` (или хранить набор локально удалённых id и не добавлять их обратно); после успешного delete не полагаться только на локаль… |

### PKG-S3-29 — karaoke-web: COMPAT, DATA, ERR, SEC — 17 BR

- **Severity:** S3 · **модули:** karaoke-web · **BR:** 17
- **Волна:** E · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 14 · **Риск:** публичный периметр, данные/безопасность
- **Темы внутри:** missing-403-mapping, println-instead-logger, credentials-env-vs-spring, rightfilename-as-sql-escape, wrong-debug-metrics, refuse-without-status-guard…

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-007 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/SiteShareLinksController.kt:103` | ERR | `@ResponseStatus(HttpStatus.FORBIDDEN)` на исключении либо handler, возвращающий 403 с code `share.notEditor`; заодно маппинг `IllegalStateException` (отсутствует атрибут) в 500 с … |
| BR-008 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/PaymentService.kt:165` | ERR | `LoggerFactory` + `log.warn("payment:create:failed sub={} status={}", sub.id, status, e)`; `println` в этих сервисах убрать. |
| BR-009 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/PaymentService.kt:38` | ERR | инжектить те же `@Value("${yookassa.shop-id}")`/`@Value("${yookassa.secret-key}")`, что и `WebClientConfig`, и проверять оба непустыми. |
| BR-010 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/EventsBuffer.kt:95` | DATA | parameterized `PreparedStatement` (или хотя бы SQL-escape `''` для всех строковых значений), а `rightFileName` не использовать вне файловых путей. |
| BR-017 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/DebugDbController.kt:58` | DATA | брать `ThreadPoolExecutor.maximumPoolSize` у Tomcat-коннектора и `HikariPoolMXBean` (total/idle/active) либо убрать поля из контракта и KDoc. |
| BR-024 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicSongEditorController.kt:292` | DATA | разрешить `refuse` только из `assigned`/`in_progress`/`rejected` (и, если нужно, `submitted`), для `approved` возвращать 409 и направлять в `delete`; либо явно фиксировать решение … |
| BR-025 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicOgSongController.kt:78` | ERR | возвращать `ResponseEntity<String>` с 400/404 (и `Cache-Control: noindex`), либо помечать страницу ошибки `noindex`. |
| BR-026 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicShareController.kt:219` | ERR | передать `shouldCache = { it.statusCode.is2xxSuccessful || it.statusCode.value() == 410 }`, чтобы 500 не кешировался (как и заявлено в комментарии). |
| BR-501 | `karaoke-web/src/main/resources/templates/song.html:4` | ERR | в контроллере — при `song == null` отдавать 404 (`ResponseEntity`/`@ResponseStatus`) до рендера; в шаблоне — `th:if="${song != null}"`/`th:unless` с веткой «песня не найдена». |
| BR-502 | `karaoke-web/src/main/resources/templates/filter.html:479` | ERR | удалить мёртвый блок с `field_author` из `filter.html` (он относится к странице «Закрома») либо восстановить элемент; в общем случае — проверять результат `getElementById` на `null… |
| BR-503 | `karaoke-web/src/main/resources/templates/filter.html:521` | COMPAT | кодировать параметры через `encodeURIComponent` (как уже сделано в `webvue3/src/lib/utils.js:71–72`) вместо ручного `replaceAll('&', …)` — во всех трёх местах `filter.html` и трёх … |
| BR-505 | `karaoke-web/src/main/resources/templates/statbysong.html:151` | COMPAT | либо ссылаться на абсолютный URL admin-хоста с этим endpoint'ом (с указанием, что он доступен только администратору), либо продублировать пагинированную выгрузку в `karaoke-web` (и… |
| BR-506 | `karaoke-web/src/main/resources/templates/testpage.html:288` | SEC | не «экранировать на входе», а разбирать на клиенте безопасно: выводить готовые `<span>/<br>` через `innerHTML` только для собственной разметки, а пользовательский текст — через `te… |
| BR-519 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt:265` | DATA | считать лимит с исключением ссылки на ту же песню (например `WHERE owner_site_user_id=? AND active AND song_id<>?`) либо сначала деактивировать заменяемую ссылку и только потом счи… |
| BR-520 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt:304` | DATA | выполнять отзыв и вставку в одной транзакции (`autoCommit=false` + `commit`/`rollback`, как в `revokeLinkById`), либо вставлять новую ссылку первой и отзывать старую после успешног… |
| BR-521 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/StemJobTempCleanupScheduler.kt:29` | ERR | проверять результат `delete()`/`runCatching` и логировать неудачи (с именем файла и причиной) хотя бы сrate-лимитом; агрегировать счётчик неудачных удалений и отдавать его в метрик… |
| BR-522 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/YandexCaptchaValidationService.kt:52` | ERR | логировать через `log.warn("captcha:validation:unavailable", e)` (со стеком), различать в ответе «сервис проверки недоступен» (503) и «капча не пройдена» (400); при недоступности в… |

### PKG-S3-30 — tools: DATA, ERR, SEC — 24 BR

- **Severity:** S3 · **модули:** tools · **BR:** 24
- **Волна:** E · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 21 · **Риск:** данные/безопасность
- **Темы внутри:** guard-empty-input-pass, guard-counter-multiplied, guard-unreachable-branch, guard-wrong-field-source, guard-discards-exit-code, guard-diff-context-false-positive…

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-573 | `tools/check-endpoint-field-coverage.sh:74` | ERR | различать «нет нарушений» и «нет данных»: при пустом наборе — печатать ошибку и возвращать ненулевой код (для `baseline-stats.sh` — считать значение как «N/A», а не «0»); в `check-… |
| BR-575 | `tools/check-container-restart.sh:100` | ERR | вынести проверку `docker-compose … restart` за пределы цикла по контейнерам (один раз на файл) либо добавить `break`/флаг «паттерн без контейнера». |
| BR-576 | `tools/check-docker-image-tags.sh:200` | ERR | проверять «похоже на nginx/node» до вынесения вердикта (например, отдельной функцией по исходному `ref`), а не в ветке `n/a`; добавить в `--selftest` кейс, где вердикт `n/a`, но им… |
| BR-577 | `tools/check-endpoint-field-coverage.sh:96` | ERR | строить список поддерживаемых полей **из контроллера пары** (использовать `controller`/`controller_method` из `endpoint-pairs.yml`), а маппер использовать только для пар, которые н… |
| BR-579 | `tools/check-eslint-baseline.sh:54` | ERR | сохранять код возврата eslint (`ESLINT_EXIT=$?`), не глушить stderr (или писать его в лог), и явно отличать «eslint упал» (exit ≥ 2 / пустой невалидный JSON) от «найдены нарушения»… |
| BR-580 | `tools/check-frontend-build.sh:77` | ERR | строить контекст из полного содержимого файла (индекс строки из diff + полный файл), а не из списка добавленных строк; номер строки брать из diff. |
| BR-582 | `tools/check-no-legacy-tbl-settings.sh:71` | SEC | использовать `mktemp`/`mktemp -d` с `trap`-очисткой (как уже сделано в `check-endpoint-field-coverage.sh` и `check-songedit-field-coverage.sh` в этой же порции). |
| BR-583 | `tools/auto-jsdoc.py:78` | ERR | в `NAME_SLUG_OVERRIDE` / `CLASS_SLUG_OVERRIDE` / `CLASS_DESCRIPTIONS` использовать актуальный префикс `archive/docs/features/` и существующие слаги; для `auto-kdoc-quality.py` — хр… |
| BR-585 | `tools/auto-kdoc.py:196` | DATA | искать существующий KDoc по признаку закрывающего `*/` с последующим сканированием вверх до `/**` (как это уже сделано в `tools/auto-kdoc-quality.py:89-104`), а не фиксированным ок… |
| BR-586 | `tools/check-enforcement.sh:50` | ERR | использовать переносимый способ подсчёта (`grep -E` + отдельная проверка `grep -P` с fallback), проверять, что `enforced`/`must_rules` — числа (иначе ошибка и ненулевой код), и зав… |
| BR-596 | `tools/check-spec-issue-link.py:68` | ERR | извлекать Issue ID в обоих форматах (например, отдельным regex для |
| BR-597 | `tools/check-subagent-isolation.sh:79` | ERR | проверять код возврата `git log`/`git rev-parse` и завершаться с ненулевым кодом |
| BR-598 | `tools/generate-docs.sh:13` | ERR | `set -euo pipefail` и явная проверка кода возврата каждого шага |
| BR-600 | `tools/install-tracker.sh:130` | ERR | сохранять код возврата compose-команды (`… || { echo "ERROR: compose up failed"; exit 1; }` |
| BR-603 | `tools/tracker-poll.sh:33` | ERR | перенести вычисление `ASSIGNEE` после `tracker_load_env` (строка 68) либо брать значение |
| BR-604 | `tools/tracker-lib.sh:168` | ERR | не полагаться на `-w` при ошибке соединения — определять недоступность по |
| BR-606 | `tools/tracker-smoke-test.sh:74` | ERR | убрать `pipefail`-ловушку (`grep -c` вместо конвейера, либо `|| true` в пайпе), |
| BR-607 | `tools/tracker-bootstrap-board.sh:167` | ERR | `OUT=$(docker exec … 2>&1 | tail -1) || true` (или `if ! OUT=$(…); then err "…: $OUT"; exit 1; fi`), |
| BR-608 | `tools/tracker-bootstrap-board.sh:142` | ERR | резолвить ID статусов по имени (`Status.find_by(name: …).id`) вместо числовых |
| BR-609 | `tools/tracker-implement-done.sh:93` | ERR | при отсутствии `report.md` **не публиковать** комментарий и **не** переводить в |
| BR-610 | `tools/tracker-bootstrap.sh:44` | ERR | убрать безграничные `оп\w*`/`op\w*` (оставить только `оп`/`op` с явным разделителем |
| BR-612 | `tools/specify-bootstrap.sh:150` | ERR | не глушить stderr (`2>&1` в лог), проверять, что `FEATURE_NUM` — число, и при пустом |
| BR-613 | `tools/tracker-spec-for-issue.sh:90` | ERR | обернуть пайп в `|| true` (`NEXT_NUM=$( { … ; } || true )`), затем валидировать |
| BR-614 | `tools/tracker-lib.sh:139` | ERR | при невозможности записи выводить предупреждение в stderr (один раз, не на каждый |

### PKG-S3-31 — webvue3: COMPAT, DATA, ERR, LEAK, PERF, RACE, RULE — 40 BR

- **Severity:** S3 · **модули:** webvue3 · **BR:** 40
- **Волна:** E · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 24 · **Риск:** данные/безопасность
- **Темы внутри:** showmelody-max-wrong, sponsr-id-normalization, formatter-response-race, numeric-fields-free-text, no-promise-loading-stuck, substring-id-sync…

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-089 | `webvue3/src/components/Songs/edit/SongEdit.vue:2038` | ERR | заменить `showMelodyVk` на `showMelodyMax` в блоке Max-вкладки; добавить в шаблонный чек «каждая вкладка использует свои `show*`/`get*`/`openLink*`» (сейчас проверено вручную по вс… |
| BR-090 | `webvue3/src/components/Songs/edit/SongEdit.vue:3193` | DATA | извлекать id регулярным выражением, допускающим оба вида URL (`/\/smkaraoke\/(?:manage\/post\/)?(\d+)(?:[/?#]|$)/`), и не трогать поле, если id не найден (оставить вставленное знач… |
| BR-091 | `webvue3/src/components/Songs/edit/SongEdit.vue:3126` | RACE | привязывать каждый запрос к id песни на момент отправки (`const requestedId = this.song.id`) и игнорировать ответ, если `requestedId !== this.song.id`; либо отменять предыдущую пач… |
| BR-092 | `webvue3/src/components/Songs/edit/SongEdit.vue:180` | DATA | либо валидировать числовые поля на клиенте (парсить перед сохранением, показывать ошибку и не включать поле в diff при невалидном значении), либо валидировать в `SongUpdateMapper` … |
| BR-094 | `webvue3/src/components/Songs/store.js:2733` | ERR |  |
| BR-095 | `webvue3/src/components/Songs/store.js:3001` | COMPAT | `JSON.parse(ids)` и сравнение как чисел/строк по элементам (`parsed.includes(String(id))` или `new Set`), либо перевести эндпоинт на JSON-массив и разбирать его. |
| BR-096 | `webvue3/src/components/Songs/store.js:1770` | RACE | вынести запрос в action, в мутации оставить только присвоение; состояние переключать по подтверждённому сервером результату (`then` в action), блокировать кнопку на время запроса и… |
| BR-097 | `webvue3/src/components/Songs/store.js:982` | RULE | перенести запись в actions (`async setLastPriorX(ctx,payload){ commit(...); try { await promisedXMLHttpRequest(...) } catch(e){ /* toast/лог */ } }`), в мутациях оставить только `s… |
| BR-098 | `webvue3/src/components/Songs/store.js:1866` | ERR | перед запросом очищать список (`commit('setCurrentSongHealthReports', [])`) либо в `catch` коммитить пустой список; ошибку показывать в модалке/тосте. |
| BR-099 | `webvue3/src/components/Songs/store.js:1827` | RULE | заменить прямые записи на `commit` (`setToSync`, `setFreeTimeSlots`, `replaceSongInPage`); после каждого `await` повторять проверку `currId === ctx.state.currentSongId` (или сравни… |
| BR-126 | `webvue3/src/components/Songs/edit/SongEdit.vue:4355` | COMPAT | на успех с непустым `messageId` выполнять `setCurrentSongField({name:'idTelegramDemo', value: data.messageId})` (или перечитывать песню), а `success=false` + `state=published` трак… |
| BR-127 | `webvue3/src/components/Songs/edit/SongEdit.vue:4020` | ERR | обернуть `findAudioParent`/`showSubsEdit` в `try/catch` с тостом; проверять `data` и `voices` перед `JSON.parse`/использованием; `await` удаления и закрывать карточку только при ус… |
| BR-363 | `webvue3/src/components/Albums/store.js:97` | ERR | ввести счётчик поколения запроса (`seq = ++state.digestRequestSeq`) и коммитить результат только при `seq === state.digestRequestSeq`; флаг загрузки сбрасывать только владельцу пок… |
| BR-364 | `webvue3/src/components/Albums/store.js:97` | RACE |  |
| BR-365 | `webvue3/src/components/Albums/AlbumsTable.vue:677` | ERR | передавать в перезагрузку `...this.$store.getters.getAlbumsFilter` (для модалки автора — хотя бы `filterAuthorId` этого автора); при необходимости сохранять и параметры страницы. |
| BR-366 | `webvue3/src/components/Albums/filter/store.js:84` | PERF | сохранять фильтр на `ok()`/`blur` (или debounce 300–500 мс), а не на каждый `input`; ошибку записи хотя бы логировать/показывать, а не оставлять необработанный промис. |
| BR-367 | `webvue3/src/components/Authors/AuthorAliasesModal.vue:73` | DATA | перед сохранением перечитывать автора с сервера либо слать только `id` + `aliases` через отдельный endpoint/patch-семантику; как минимум не пересылать поля, которые модалка не реда… |
| BR-368 | `webvue3/src/components/Authors/AuthorAliasesModal.vue:86` | ERR | проверять `Number(response) !== 0`, при нуле показывать ошибку и не закрывать модалку; `saving` сбрасывать в `finally`. |
| BR-369 | `webvue3/src/App.vue:200` | ERR | обернуть восстановление настроек в `try/catch` (или `Promise.allSettled` с одним параллельным запросом), а создание polling-таймеров вынести до/вне блока восстановления. |
| BR-371 | `webvue3/src/components/Chat/store.js:118` | RACE | ввести поколение запроса/идентификатор треда и применять ответ только при совпадении с текущим состоянием (`siteUserId` + `target`); блокировать параллельный poll флагом in-flight;… |
| BR-372 | `webvue3/src/components/Common/CustomConfirm.vue:203` | LEAK | хранить id (`this.timer = setInterval(...)`), очищать его в `beforeUnmount`, в `ok()`/`close()` и по достижении нуля. |
| BR-373 | `webvue3/src/components/Authors/store.js:71` | ERR | сбрасывать флаг в `finally`, возвращать промис из action, ошибку показывать в UI (тост/строка), а не только в консоли. |
| BR-374 | `webvue3/src/components/Authors/AuthorsTable.vue:605` | COMPAT | `fldValue: item.haveNewAlbum`. |
| BR-375 | `webvue3/src/components/Authors/AuthorsTable.vue:526` | ERR | `if (item.vkId)`. |
| BR-376 | `webvue3/src/components/Authors/AuthorsTable.vue:659` | ERR | сравнивать `String(result) !== '0'` (или `Number(result) > 0`), ошибку показывать пользователю (тост/алерт), модалку закрывать после успешного ответа. |
| BR-377 | `webvue3/src/components/Authors/AuthorsTable.vue:533` | DATA | отправлять только изменённые поля (diff), а не весь снимок; на бэкенде — patch-семантика или оптимистичная блокировка (`updated_at`/version) с 409 при расхождении. |
| BR-378 | `webvue3/src/components/Common/FileExplorer/FileExplorerModal.vue:153` | RACE | сохранять запрошенный путь (или поколение) и применять ответ только при совпадении с текущим; отменять предыдущий запрос (`AbortController`) или игнорировать устаревший; `isBusy` —… |
| BR-381 | `webvue3/src/components/Common/HealthReport/store.js:41` | ERR | хранить признак ошибки (`healthReportListError`) и показывать её в модалке вместо текста «нарушений не найдено»; пустой успешный ответ и ошибку различать. |
| BR-382 | `webvue3/src/components/ListeningHistory/ListeningHistoryFilterModal.vue:94` | ERR | хранить фильтры в Vuex (по образцу `Dictionaries/filter/store.js`), собирать параметры загрузки в одном месте (фильтры + page + pageSize) и сбрасывать `currentPage` в 1 при примене… |
| BR-383 | `webvue3/src/components/ListeningHistory/store.js:75` | RACE | ввести счётчик поколения (`seq = ++state.loadSeq`) и коммитить только при `seq === state.loadSeq`; для сброса страницы использовать флаг/прямой вызов загрузки, не полагаясь на watc… |
| BR-384 | `webvue3/src/components/Dictionaries/filter/store.js:33` | COMPAT |  |
| BR-385 | `webvue3/src/components/Common/SmartCopy/SmartCopyModal.vue:309` | ERR | не закрывать модалку до завершения запроса (закрывать в `.then`/`.catch` после показа результата) либо показывать результат через глобальный тост/`CustomConfirm` родителя; добавить… |
| BR-386 | `webvue3/src/components/Common/Monitor/store.js:38` | ERR | хранить состояние ошибки загрузки и показывать его в светофоре/модалке (например, серый/красный «нет связи с мониторингом») вместо зелёного; повторять запрос; добавить `.catch` для… |
| BR-387 | `webvue3/src/components/Common/ProcessWorker.vue:195` | ERR | добавить `.catch` и `try/catch` вокруг `JSON.parse` (как в `checkCountWaiting`), при ошибке показывать неопределённое состояние и не давать переключать очередь до успешной синхрони… |
| BR-389 | `webvue3/src/components/News/NewsTable.vue:281` | COMPAT | сделать `perPage` computed от `getRowsPerPage('news')` (или добавить мутацию/экшен `setNewsPerPage` и синхронизировать с `tableSettings`), передавать `page`/`perPage` в `loadNews` … |
| BR-390 | `webvue3/src/components/Pictures/PicturesTable.vue:219` | ERR | либо грузить дайджест в `mounted` (страница уже клиентски пагинируется), либо показывать явную подсказку о необходимости применить фильтр и не выдавать пустой список за отсутствие … |
| BR-391 | `webvue3/src/components/Pictures/store.js:137` | ERR | сбрасывать флаг в `finally`, возвращать промис и показывать ошибку (тост/строка), а не только в консоли. |
| BR-392 | `webvue3/src/components/News/store.js:78` | ERR | ввести `newsError`/`newsListError` в state, коммитить его в `catch`, показывать в шаблоне; в компоненте показывать ошибку операции (тост/строка), а не только консоль. |
| BR-393 | `webvue3/src/components/News/store.js:85` | RACE | ввести счётчик поколения запроса в state и коммитить результат только при совпадении; либо отменять предыдущий XHR (`AbortController`) и сбрасывать loading только владельцу поколен… |
| BR-394 | `webvue3/src/components/Processes/ProcessesTable.vue:447` | RACE | не полагаться на watcher при программном сбросе страницы (флаг/`skipNextLoad`), либо ввести поколение запроса в `loadProcesses` и игнорировать устаревшие ответы; грузить страницу о… |

### PKG-S3-32 — webvue3: COMPAT, DATA, ERR, LEAK, PERF, RACE, SEC — 40 BR

- **Severity:** S3 · **модули:** webvue3 · **BR:** 40
- **Волна:** E · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 26 · **Риск:** данные/безопасность
- **Темы внутри:** autosave-stale, delete-no-wait, csv-injection, error-hidden, poll-silent, bulk-delete-stale…

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-395 | `webvue3/src/components/Pictures/store.js:163` | RACE | коммитить snapshot из отправленного payload (`Object.assign({}, snapshot, savedDiffs)`), а не из живого `pictureCurrent`; сбрасывать таймер и отменять запрос в `beforeUnmount`. |
| BR-396 | `webvue3/src/components/Pictures/edit/PictureEdit.vue:299` | ERR | перенести запросы из мутаций в экшены, возвращать и `await` промис, показывать ошибку (тост) и закрывать модалку только после успеха; после удаления обновлять дайджест (или splice … |
| BR-398 | `webvue3/src/components/Processes/ProcessBulkReportModal.vue:104` | SEC | при экспорте префиксовать значения, начинающиеся с `= + - @ \t \r`, апострофом/пробелом (или использовать `="..."`-безопасную схему) до общего quoting. |
| BR-400 | `webvue3/src/components/Processes/edit/ProcessEdit.vue:3` | ERR | вынести error-блок из-под `v-if="process"`; ввести состояние загрузки `loading|loaded|error` и в `error` показывать сообщение с кнопкой «Повторить»/«Закрыть», а не пустую форму и н… |
| BR-401 | `webvue3/src/components/Processes/store.js:673` | ERR | в `catch` фиксировать терминальное состояние (например `status: 'FAILED'`, текст ошибки) в `bulkTaskStatus` и `bulkOperationReport`, не бросать исключение из таймерного цикла; при … |
| BR-402 | `webvue3/src/components/Processes/store.js:694` | RACE | передавать в `pollBulkTask` id, с которыми запущена операция, и удалять из `items` только их; для `bulk_update` — перезагружать страницу/`items` по завершении, а не полагаться на с… |
| BR-403 | `webvue3/src/components/Processes/audit/ProcessAuditModal.vue:70` | RACE | очищать `currentProcessAudit` перед запросом, хранить `currentProcessAuditId` и игнорировать ответ, если `id`/поколение не совпадает с текущим, показывать индикатор загрузки вместо… |
| BR-404 | `webvue3/src/components/Properties/PropertiesTable.vue:290` | ERR | показывать ошибку пользователю (строка/тост) и оставлять диалог открытым или переоткрывать его; валидировать значение на клиенте по `item.type` (Int/Long/Double/Boolean) до отправк… |
| BR-405 | `webvue3/src/components/Properties/PropertiesTable.vue:245` | ERR | грузить дайджест в `mounted()` (клиентская пагинация уже есть) либо явно показывать подсказку о необходимости применить фильтр и не выдавать пустой список за отсутствие данных. |
| BR-406 | `webvue3/src/components/Promotions/PromotionsTable.vue:165` | ERR | оборачивать мутации в `try/catch` и показывать ошибку (тост/строка над таблицей); проверять возвращённый id/boolean (`0`/`false` — отказ) и не выдавать это за успех. |
| BR-408 | `webvue3/src/components/Processes/filter/ProcessesFilterModal.vue:243` | COMPAT | при применении фильтра передавать `limit`/`offset` из таблицы (или вызывать её `loadPage()`), сбрасывать страницу на 1 и добавлять `catch` на оба диспатча. |
| BR-412 | `webvue3/src/components/Publish/store.js:188` | RACE | хранить активный `filterCond` вместе с дайджестом и передавать его в тихий reload; либо не выполнять фоновую перезагрузку, пока открыт cond-вид. |
| BR-413 | `webvue3/src/components/Properties/store.js:54` | ERR | сбрасывать флаг в `catch`/`finally` и возвращать промис из экшена. |
| BR-414 | `webvue3/src/components/SitePlaylists/store.js:70` | RACE | запоминать запрошенный id и игнорировать ответы с другим id; ввести явное состояние ошибки/загрузки. |
| BR-415 | `webvue3/src/components/SiteUsers/UserPlaylistsModal.vue:58` | ERR | очищать digest при смене субъекта и показывать состояние ошибки; хранить данные с ключом `siteUserId`. |
| BR-418 | `webvue3/src/components/SongEditor/SongKaraokeEditorModal.vue:218` | DATA | dirty-флаг и повторный запуск сохранения в `finally`, либо очередь/сериализация сохранений; `'saved'` ставить только если после старта запроса не было новых `change`. |
| BR-419 | `webvue3/src/components/SongEditor/SongEditorTable.vue:273` | ERR | различать `null`/ошибку в `loadAssignmentById` (например, `result.found`/`error`), показывать текст и не открывать модалку; кнопку «Закрыть» вынести из `v-if="a"`, а `openReview` о… |
| BR-420 | `webvue3/src/components/SiteUsers/edit/SiteUserEdit.vue:399` | ERR | проверять тело ответа (`ok`/Boolean) и ловить reject в actions либо в компоненте; не закрывать диалог/панель до подтверждённого успеха; показывать ошибку в форме (сейчас в `SiteUse… |
| BR-421 | `webvue3/src/components/SongEditor/AssignModal.vue:269` | RACE | запретить закрытие при `busy` либо ввести флаг отмены, проверяемый в цикле; на закрытии всё равно уведомлять родителя о фактически назначенных песнях (эмит `assigned` до unmount). |
| BR-422 | `webvue3/src/components/SongEditor/AssignModal.vue:235` | ERR | отдельное состояние ошибки (`isError`/текст) и сообщение «Не удалось выполнить поиск», не выдавая сбой за пустой результат. |
| BR-423 | `webvue3/src/components/SongEditor/SongEditorTable.vue:261` | RACE | счётчик поколений или `AbortController` на каждый reload, коммит только для актуального запроса; на ошибку — видимое сообщение (и не выдавать старый список за новый). |
| BR-424 | `webvue3/src/components/SiteUsers/filter/store.js:114` | PERF | persist один раз — при «Применить фильтр»/закрытии модалки (debounce или `blur`), а гидратацию делать одним запросом/`Promise.all`; ответы обрабатывать (`catch`). |
| BR-425 | `webvue3/src/components/SongEditor/SongKaraokeEditorModal.vue:180` | COMPAT | разбирать `body.error` и показывать отдельные тексты (минимум для `song_not_found_in_local_db` — «песня не стянута в локальную БД, запустите sync»); сохранить общий текст как fallb… |
| BR-427 | `webvue3/src/components/SongEditor/store.js:143` | COMPAT | читать `prop.value` (строкой), как это делают другие потребители (`ResourceLimitToggle.vue:37–38`, `SubsEdit.vue:5020–5027`), и сравнивать значение; при отсутствии/пустом — `'remot… |
| BR-428 | `webvue3/src/components/SongEditor/useReviewModalFormat.js:88` | DATA | не дублировать `loadEditorSettings`/`saveEditorSettings` с урезанным набором ключей: читать полный объект и мержить в него partial (либо вынести общий модуль настроек и импортирова… |
| BR-429 | `webvue3/src/components/Songs/edit/FamilySongsModal.vue:128` | ERR | добавить `catch` с сообщением об ошибке (отдельное поле/блок) и не показывать «Ничего не найдено» при неуспешном запросе; при ошибке поиска сохранять прежний список с явной пометко… |
| BR-430 | `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue:1235` | LEAK | добавлять `window.addEventListener('blur', ...)` (и/или `visibilitychange`) с остановкой всех `scrubTimers` и восстановлением `playbackRate`, как это делает `stopScrub`. |
| BR-431 | `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue:978` | LEAK | при скрытии останавливать и уничтожать инстанс (`this.kpPlayer.destroy(); this.kpPlayer = null`), либо скрывать контейнер через `v-show` и ставить плеер на паузу — но не оставлять … |
| BR-432 | `webvue3/src/components/Songs/edit/AlbumCoverModal.vue:234` | ERR | обернуть обе цепочки в `try/catch` с явным сообщением («Не удалось получить текущую обложку», «Не удалось прочитать файл») и не подменять отказ запроса сообщением об отсутствии кар… |
| BR-433 | `webvue3/src/components/SongEditor/store.js:88` | ERR | хранить состояние ошибки в сторе и показывать его в таблице (баннер/тост), при ошибке не выдавать текущий список за актуальный. |
| BR-434 | `webvue3/src/components/Songs/edit/AiTextEditorModal.vue:100` | PERF | заменить LCS-матрицу на алгоритм с линейной памятью (Майерс/Хиршберг) или ограничить diff по длине (при превышении — не подсвечивать, а показывать результат целиком), с явным лимит… |
| BR-435 | `webvue3/src/components/Songs/SongsTable.vue:705` | DATA | вычислять страницу по тому же массиву, который реально отдан в `b-table` (передавать в таблицу уже отсортированный список либо учитывать `sortBy` при построении `songIdAndPageId`),… |
| BR-436 | `webvue3/src/components/Songs/SongsTable.vue:1638` | ERR | не парсить тело — эти эндпоинты ничего не возвращают; показывать результат по факту 2xx, ошибки ловить `.catch` с сообщением. Если нужен результат — вернуть с бэкенда JSON и разбир… |
| BR-437 | `webvue3/src/components/Songs/SongsTable.vue:1373` | RACE | игнорировать ответ, если набор запрошенных id уже не совпадает с текущим `currentPageSongIds` (или проставлять номер запроса/токен), либо мержить карту, а не заменять её. |
| BR-438 | `webvue3/src/components/Songs/SongsTable.vue:1422` | PERF | построить один `Map(id → song)` (или `Map(id → page)`) на текущий `songsDigests` и брать песню по ключу вместо `filter`/`find` в цикле; свести три вызова к одному на page-change. |
| BR-439 | `webvue3/src/components/Songs/SongsTable.vue:1157` | COMPAT | либо реализовать серверную пагинацию (принимать `page`/`perPage`, отдавать срез и общее число), либо убрать мёртвые параметры и зафиксировать в документации клиентскую пагинацию — … |
| BR-440 | `webvue3/src/components/Songs/SongsTable.vue:1479` | PERF | ограничить параллелизм (очередь с лимитом, как HR), показывать прогресс/итог и ошибки, либо сделать один серверный bulk-эндпоинт. |
| BR-441 | `webvue3/src/components/Songs/SongsTable.vue:1168` | LEAK | хранить id обоих таймеров в `data` и очищать их в `beforeUnmount`; после unmount не отправлять активный набор. |
| BR-442 | `webvue3/src/components/Songs/SongsTable.vue:1822` | COMPAT | передавать `threadId: result.threadId` в оба action; для SHEETSAGE завести отдельный ключ приоритета (`setLastPriorSheetsage`), а не переиспользовать demucs. |
| BR-443 | `webvue3/src/components/Songs/filter/store.js:190` | COMPAT | один общий builder параметров (модалка должна использовать `getSongsFilter`, либо getter — параметры модалки); `params.flagFree`; всегда передавать `target` при непустом `filterAss… |

### PKG-S3-33 — webvue3: BUILD, COMPAT, DATA, ERR, LEAK, PERF, RACE, RULE — 32 BR

- **Severity:** S3 · **модули:** webvue3 · **BR:** 32
- **Волна:** E · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 16 · **Риск:** данные/безопасность
- **Темы внутри:** history-keys-wrong, history-no-catch, stale-result, max-vs-union, no-try, mouseup-interval…

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-444 | `webvue3/src/components/Songs/filter/SongsFilterModal.vue:988` | COMPAT | восстановить `filter_audio_parent_id` и `flag_free`, читать `is_sync`; для полей, отсутствующих в `historyArgs`, явно сбрасывать значение (как сделано для `filterAssignmentStatus`)… |
| BR-445 | `webvue3/src/components/Songs/filter/SongsFilterModal.vue:838` | ERR | развязать загрузку истории и авторов (`Promise.allSettled` или отдельный вызов), добавить `catch` с сообщением пользователю; в `loadSongsHistory` возвращать промис и сбрасывать фла… |
| BR-446 | `webvue3/src/components/SponsrSync/store.js:86` | ERR | в `catch` очищать `lastResult` (или класть `{ ok:false, messages:[текст ошибки] }`), показывать ошибку в панели; ошибку транспорта отличать от `ok:false` бизнес-ответа. |
| BR-447 | `webvue3/src/components/Stats/MonetizationPanel.vue:105` | DATA | считать union на бэкенде (`count(distinct id)` по `tbl_site_users` с `OR` тех же условий) и отдавать отдельным полем; либо, если объединение недоступно, честно подписать «не менее … |
| BR-448 | `webvue3/src/components/Songs/edit/SearchText.vue:301` | ERR | обернуть оба действия в `try/catch/finally`, показывать ошибку (тост/алерт) и при ошибке не оставлять экран в состоянии «всё получилось»; либо возвращать промис в `CustomConfirm` и… |
| BR-452 | `webvue3/src/components/Songs/edit/SubsEdit.vue:665` | LEAK | вешать `mouseup`/`pointerup` на `window` (или `setPointerCapture`) и очищать все `pressed*` по `blur`/`visibilitychange`; в watcher'ах сначала гасить интервал (`clearInterval`) при… |
| BR-453 | `webvue3/src/components/Songs/edit/SubsEdit.vue:2889` | ERR | проверять `if (this.isUnmounted) return` после каждого `await` в `mounted()` (как в watcher'е `currentVoice`) и перед блоком навешивания обработчиков; по возможности отменять сами … |
| BR-454 | `webvue3/src/components/Songs/edit/SubsEdit.vue:3092` | ERR | восстанавливать режим в `returnSearchText` так же, как в `closeSearchText` (или снимать `isEditMode` не в `doSearchText`, а в самом `SearchText` через `focus/blur`-события), чтобы … |
| BR-455 | `webvue3/src/components/Songs/edit/SubsEdit.vue:1908` | RULE | убрать присваивание из геттера — вычислять локальную пару `[string, lad]` для отрисовки, а `marker.stringLad` писать только по явному действию пользователя (кнопка «зафиксировать л… |
| BR-456 | `webvue3/src/components/Songs/edit/SubsEdit.vue:3268` | ERR | гасить хоткеи на время любой открытой модалки (`if (this.isCustomConfirmVisible || this.isSearchTextVisible || this.isAiTextEditorVisible || this.isWhisperDebugVisible) return` в н… |
| BR-457 | `webvue3/src/components/Songs/edit/SubsEdit.vue:2196` | PERF | считать слоги инкрементально — по одному проходу на строку (`computeSyllables(line).length` + накопленный счётчик), либо один раз на весь текст с запоминанием границ строк; тег-яко… |
| BR-463 | `webvue3/src/components/Subscriptions/SubscriptionsFilterModal.vue:145` | COMPAT | отправлять стенное время без зоны (`YYYY-MM-DD HH:mm:ss`) либо перевести колонку/сравнение на `timestamptz` с явной зоной. |
| BR-464 | `webvue3/src/components/Sync/SyncTable.vue:208` | RACE | сериализовать изменения по сущности (очередь/блокировка чекбокса до ответа) либо применять ответ только если его значение совпадает с последним отправленным. |
| BR-465 | `webvue3/src/components/Stats/store.js:479` | ERR | очищать state перед запросом, возвращать `Promise<Boolean>` и показывать ошибку в модалке (или блокировать открытие до успешной загрузки). |
| BR-466 | `webvue3/src/components/Sync/store.js:45` | ERR | `.catch` в `mounted()` (или внутри action) с состоянием ошибки и текстом в таблице; у `loadSyncAutoStatusPromise` — `.catch(() => null)` вместо молчаливого реджекта. |
| BR-468 | `webvue3/src/components/Songs/edit/SubsEdit.vue:4517` | ERR | единый хелпер `removeMarkerRegion(marker)` с `if (marker.region) { marker.region.remove(); marker.region = null }` и заменить все шесть вызовов; в `createBeatMarkers` брать длитель… |
| BR-469 | `webvue3/src/components/Songs/edit/SubsEdit.vue:4394` | ERR | нормализовать индекс (`const idx = Math.max(0, this.currentMarkersIndex)`), а замену разрешать только при `currentMarkersIndex >= 0`; `markerToDelete` проверять на `undefined`; отд… |
| BR-470 | `webvue3/src/components/Songs/edit/SubsEdit.vue:3615` | DATA | создавать/пересоздавать регион только при `ws.getDuration() > 0` (как `createRegionMarker`) либо пересоздавать его по `decode`; `regionMarkerStart/End` обновлять из событий региона… |
| BR-471 | `webvue3/src/components/Songs/edit/SubsEdit.vue:3670` | DATA | копировать все поля маркера (`note`, `chord`, `stringLad`, `lockLad` включительно), например `{...markerToCopy, time: newTime, region: undefined}`; chord/note-маркеры без значения … |
| BR-472 | `webvue3/src/components/Songs/edit/SubsEdit.vue:4854` | ERR | `if (this.sourceMarkers.length === 0) return` в начале метода (и, по желанию, `:disabled="sourceMarkers.length === 0"` на кнопке). |
| BR-473 | `webvue3/src/components/Songs/edit/SubsEdit.vue:5725` | PERF | хранить сигнатуру отрисованного содержимого на маркере (например, `marker._contentSignature = markertype + '\u0000' + label + '\u0000' + note + '\u0000' + chord`) и сравнивать её, … |
| BR-474 | `webvue3/src/components/Songs/edit/SubsEdit.vue:5207` | ERR | добавить `.catch((e) => { … })` с сообщением пользователю (тост/`customConfirmParams` + `isCustomConfirmVisible`), фиксировать инстанс (`const ws = this.ws`) и проверять `if (this.… |
| BR-475 | `webvue3/src/views/HomeView.vue:295` | ERR | добавить `.catch()` во все цепочки `dispatch(...).then(...)` с показом ошибки в модалке (`isAlert` + `alertType: 'danger'`); для ответов-счётчиков проверять формат (число/строка `'… |
| BR-476 | `webvue3/src/views/HomeView.vue:880` | ERR | сбрасывать `authYmInProgress` в `finally` (или в `.catch`) у обеих команд; при старте выставлять флаг только после успешного ответа, а до него блокировать кнопки отдельным состояни… |
| BR-477 | `webvue3/src/views/PublishTemplatesView.vue:280` | ERR | ошибку сохранения показывать локально у карточки шаблона (как `tpl.previewError`, `:147–149`), а не общим флагом `error`, который прячет редактор; либо разделить флаги «ошибка загр… |
| BR-478 | `webvue3/src/views/PlayerView.vue:43` | LEAK | ввести в `PlayerView` флаг отмены (`this.destroyed = true` в `onBeforeUnmount`), а в `KaraokePlayer.init` проверять его после каждого `await` и прерывать инициализацию (с закрытием… |
| BR-479 | `webvue3/src/composables/useKaraokeEditor.js:325` | PERF | перевести мини-редактор на `findMarkerIndexByTime` из `webvue3/src/utils/markerIndex.js` (индексы по времени) и не пересобирать `formattedTextHtml` целиком на каждом `timeupdate` (… |
| BR-480 | `webvue3/src/composables/useKaraokeEditor.js:608` | COMPAT | добавить `songType` в контракт ответа редакторского API (`SongEditorController.getById`) и прокинуть его в пропсы мини-редактора и в `buildInlinePlayerData`; заодно сверить остальн… |
| BR-481 | `webvue3/src/lib/utils.js:15` | ERR | задавать конечный `xhr.timeout` (и обрабатывать `ontimeout` → reject с понятным сообщением), добавить `onabort`; в `setWebvueProp` — обрабатывать отказ (`.catch`) и не считать сохр… |
| BR-482 | `webvue3/src/lib/utils.js:57` | RACE | дебаунсировать запись фильтра (или писать по факту закрытия модалки — `ok()` уже дублирует финальную запись) и передавать монотонную версию/время изменения, чтобы сервер принимал т… |
| BR-486 | `webvue3/src/player/KaraokePlayer.js:281` | DATA | считать `_silentOffset` до `_buildFlashTimes()` (переставить две строки в `init()`), либо |
| BR-488 | `webvue3/vite.config.js:5` | BUILD | добавить в `webvue3/vite.config.js` блок `server.proxy` с маршрутом `'/api'` на адрес `karaoke-app` (порт — из `deploy/do.env`, как в `karaoke-public/vite.config.js`), и/или зафикс… |

### PKG-S3-34 — прочее: BUILD, SEC — 5 BR

- **Severity:** S3 · **модули:** прочее · **BR:** 5
- **Волна:** E · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 4 · **Риск:** данные/безопасность, миграции БД
- **Темы внутри:** gradle-wrapper-outdated, jackson-version-skew, gradle-wrapper-no-checksum, heap-dump-world-readable, dead-duplicate-build-config
- **Зависит от:** PKG-S2-08 (рабочий бэкап БД — prerequisite)

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-499 | `karaoke-db/gradle/wrapper/gradle-wrapper.properties:3` | BUILD | выровнять версию wrapper'а на используемую в `karaoke-app`/`karaoke-web` (9.1.0) либо убрать модуль `karaoke-db` целиком (см. «Сверки»). |
| BR-527 | `build.gradle.kts:45` | BUILD | убрать явную версию `jackson-databind` в модулях (взять управляемую BOM'ом 2.19.2) — тогда весь стек Jackson будет одной версии; если 2.14.2 нужен осознанно (совместимость Dokka 1.… |
| BR-528 | `gradle/wrapper/gradle-wrapper.properties:1` | BUILD | зафиксировать `distributionSha256Sum=<sha256 дистрибутива gradle-8.14-bin.zip>` в каждом из четырёх wrapper'ов (хеш берётся из официального `gradle-8.14-bin.zip.sha256`); при несов… |
| BR-529 | `gradle.properties:9` | SEC | убрать `-XX:+HeapDumpOnOutOfMemoryError`/`HeapDumpPath` из общих `jvmargs` (или писать дамп в каталог с правами 0700 внутри `GRADLE_USER_HOME`, а не в общий `/tmp`); в CI — отключи… |
| BR-530 | `build.gradle.kts:34` | BUILD | удалить `pom.xml` (проект на Gradle; Maven-сборка не используется ни CI, ни скриптами) либо явно пометить его как справочный и синхронизировать версии; в корневом `build.gradle.kts… |

### PKG-SEC-CHMOD — Файлы с правами 666 (пароли, превью)

- **Severity:** S3 · **модули:** karaoke-app · **BR:** 2
- **Волна:** E · **Режим:** короткий цикл: ТЗ из карточек аудита
- **Файлов:** 2 · **Риск:** данные/безопасность

| BR | Файл:строки | Класс | Суть / поправка |
|---|---|---|---|
| BR-115 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsPictures.kt:564` | SEC | выставить минимально необходимые права (владелец-группа контейнера + `640`/`750`) и настроить общий group/umask на томах; не использовать world-writable биты. |
| BR-247 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt:75` | SEC | вернуть из `savePropertiesMap`/`set` результат, на ошибке — 5xx с причиной и записью в лог со стеком (SLF4J); `chmod` выполнять до публикации успеха и не смешивать его ошибку с оши… |

