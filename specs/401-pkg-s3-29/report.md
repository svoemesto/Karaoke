# PKG-S3-29 — karaoke-web: ошибки врали, ошибки глотались, ошибки были неразличимы

**OpenProject:** #401 · **Волна:** E · **Записей:** 17 · **Модуль:** `karaoke-web` (бэкенд прод-SPA + Thymeleaf-шаблоны)

Сквозной корень пакета: **система не различала «не знаю» и «знаю, что плохо»** — отказ
выглядел как успех, ошибка молча проглатывалась, а «капча не пройдена» и «сервис капчи
лежит» были одним и тем же кодом ответа.

---

## 1. Карточки записей

### BR-007 · `SiteShareLinksController` · ERR — отказ «вы не редактор» отдавался как 500

**Было.** `resolveEditorOrThrow()` бросал `private class ShareAccessDeniedException` —
**без `@ResponseStatus`**, и обработчика исключений в проекте не было. Комментарий в коде
обещал «403 share.notEditor (Spring-уровневый маппинг в JSON)», но маппинга не
существовало: `RuntimeException` уезжал в стандартный 500. Не-редактор, вызвавший
`/api/siteusers/share/sessions`, получал «сервер сломался» вместо «доступ запрещён».

**Стало.** Исключение вынесено на верхний уровень файла с `@ResponseStatus(FORBIDDEN)`;
добавлен `@RestControllerAdvice(assignableTypes = [SiteShareLinksController::class])`:
`ShareAccessDeniedException` → **403** `{"errorCode":"share.notEditor"}`,
`IllegalStateException` (например, `SiteAuthInterceptor` не выставил атрибут) → **500**
`{"errorCode":"share.internal"}` со стеком в логе.

---

### BR-008 · `PaymentService` · ERR — ошибки платежей уходили в stdout без стека

**Было.** 10 × `println(...)` в платёжном сервисе. Ни стека, ни категории журнала, ни
структурированных полей: в логах контейнера — строка текста, по которой нельзя понять,
**какой** статус вернул ЮKassa и **какой** подписки это касается.

**Стало.** `LoggerFactory` + `log.warn(...)` с ключами `sub`/`order`/`paymentId`/`status`
и **самим исключением** (стек). Сообщения приведены к формату `payment:create:failed
sub={} status={}` — по ним видно, что именно сломалось.

---

### BR-009 · `PaymentService` · ERR — проверка ключей обходила Spring-конфиг

**Было.** `hasCredentials()` = `System.getenv("YOOKASSA_SHOP_ID") != null ||
System.getProperty("yookassa.shop-id") != null`. То есть:
- ключ, заданный в `application.yml` или через `--spring.config`, **не считался**;
- проверялся **только** `shop-id`: при заданном shop-id и пустом secret-key сервис
  считал, что «ключи есть», а `WebClient` слал Basic-auth с пустым секретом — платёж
  падал уже в ЮKassa (401), и по логам это выглядело как «ЮKassa не работает».

**Стало.** Те же ключи, что инжектит `WebClientConfig`:
`@Value("\${yookassa.shop-id}")` и `@Value("\${yookassa.secret-key}")`; проверяются
**оба**: `shopId.isNotBlank() && secretKey.isNotBlank()`.

---

### BR-010 · `EventsBuffer` · DATA — SQL собирался конкатенацией, значения искажались

**Было.** `buildInsertSql()` склеивал SQL из значений, прогоняя каждое через
`rightFileName()` — **санитайзер имён файлов**:
- `referer` (`https://…`) → `https-//…` (':' → '-') — ломалась ссылка и агрегация
  источников трафика;
- `AC/DC` → `AC_DC`, `'` → backtick, `$` → `s`, `*` → `x` — данные портились;
- любое значение с `&` (например, URL с query-параметрами) **ломало SQL**;
- безопасность запроса держалась на побочном эффекте `sanitizePathSegment()` (он
  заменяет кавычку на backtick) — то есть на одном забытом `escapeHtml()` в новой
  ветке уже был бы инъекцией.

**Стало.** INSERT **параметризованный**: `buildInsert()` собирает SQL из имён колонок и
плейсхолдеров `?`, значения биндятся `setString`/`setLong`/`setInt`/`setBoolean`.
Имена колонок проверяются на `[a-z_]+` (страховка от будущих изменений). Значения
не искажаются: `referer` сохраняется как есть. `rightFileName` из сервиса убран.

---

### BR-017 · `DebugDbController` · DATA — метрики врали

**Было.**
- `pgMaxConnections` — **константа 100**, никак не related к настройке БД;
- `currentTomcatMaxThreads` — `ThreadMXBean.peakThreadCount`, то есть **пик потоков всей
  JVM**, названный максимумом потоков Tomcat;
- KDoc обещал «HikariCP pool», хотя в проекте его нет: соединения — собственный
  thread-local пул на raw JDBC (ADR-0001).

**Стало.** `pgMaxConnections` — реальный `SHOW max_connections` (`-1` = «не удалось
прочитать»); `currentTomcatMaxThreads` — `ThreadPoolExecutor.maximumPoolSize` пула
Tomcat-коннектора (запасной путь — свойство коннектора `maxThreads`); KDoc приведён в
соответствие с реальностью.

---

### BR-024 · `PublicSongEditorController` · DATA — «отказаться» удаляло одобренное задание

**Было.** `refuse` **не проверял статус**: он удалял черновик и запись назначения
любого статуса. Для `approved` черновик — архив разметки после апрува (FR-012), то
есть отказ по прямому POST **уничтожал архив одобренной работы**. Для `submitted` есть
отдельный `/recall` (вернуть в работу), а `refuse` удалял работу пользователя целиком.

**Стало.** `REFUSABLE_STATUSES = {assigned, in_progress, rejected}`. Для остальных —
**409** `{"error":"not_refusable","status":…,"use":"recall"|"delete"}`: ответ прямо
указывает, каким endpoint'ом воспользоваться.

---

### BR-025 · `PublicOgSongController` · ERR — страница ошибки отдавалась с HTTP 200

**Было.** И «не указан id», и «песня не найдена» возвращали **200 OK** с HTML-страницей
«Ошибка». Для краулера это «страница существует»: страницы ошибок попадали в индекс, а
настоящая 404 была неотличима от успеха.

**Стало.** `ResponseEntity<String>`: нет/некорректный id → **400**, песня не найдена →
**404**; в обоих случаях `Cache-Control: noindex, nofollow` и `<meta name="robots">`.

---

### BR-026 · `PublicShareController` · ERR — 500 кешировался на 15 секунд

**Было.** Комментарий в коде прямо говорил «внутренние ошибки НЕ кешируем», но
`heartbeatPollingCache.getOrCompute(key, ttl) { … }` кешировал **любой** ответ, включая
500 `share.internal`. При падении БД публичный клиент получал залипший 500 ещё 15 секунд
после восстановления БД.

**Стало.** Явный предикат:
`shouldCache = { it.statusCode.is2xxSuccessful || it.statusCode.value() == 410 }`.

---

### BR-501 · `song.html` + `MainController` · ERR — «песня не найдена» была 500

**Было.** Контроллер клал в модель `song = null`, а шаблон безусловно читал
`${song.songName}` → SpEL `EL1007E` → **HTTP 500** вместо 404.

**Стало.** Контроллер отдаёт `HttpServletResponse.SC_NOT_FOUND`; шаблон имеет обе ветки
(`th:if="${song != null}"` / `th:unless="${song != null}"`) и видимую надпись
«Песня не найдена».

---

### BR-502 · `filter.html` · ERR — мёртвый блок убивал всю аналитику страницы

**Было.** `const fieldAuthor = document.getElementById('field_author');` — элемента на
странице `/filter` нет (его `<select>` **закомментирован**), поэтому следующая строка
`fieldAuthor.addEventListener(...)` бросала `TypeError`. Ошибка прерывала **весь**
ready-обработчик — и обвязка ссылок (`getElementsByName('link')` с регистрацией клика в
аналитике) не навешивалась **вообще**: страница теряла события `clickToLink` по всем
песням.

**Стало.** Мёртвый блок удалён (он относился к странице «Закрома», где поле есть).

---

### BR-503 · `filter.html`, `zakroma.html` · COMPAT — ручное экранирование `&` ломало запросы

**Было.** `value.replaceAll('&', '%26')` кодировал **только** `&`. Остальное ломалось:
`C++` → `C  ` (`'+'` → пробел на сервере), `#` обрезал URL, `%` давал невалидную
последовательность, пробелы и `&` внутри значения ломали разбор.

**Стало.** `encodeURIComponent(...)` во всех шести местах (3 в `filter.html`,
3 в `zakroma.html`).

---

### BR-505 · `statbysong.html` + `MainController` · COMPAT — ссылка «полной выгрузки» вела в никуда

**Было.** Баннер на `/statbysong` вёл на `/api/stats/by-song?page=1&pageSize=50`. Такой
эндпоинт есть только в **karaoke-app** (админское приложение), а страницу отдаёт
**karaoke-web**. Ссылка была относительной → на хосте karaoke-web такого пути нет →
404: «полная выгрузка», о которой обещал баннер, была недоступна.

**Стало.** Эндпоинт выгрузки добавлен в karaoke-web — **вне `/api/`**, по пути
`/statbysong-export` (JSON `items`/`totalCount`/`page`/`pageSize`, тот же источник
`StatsByEvents`, `pageSize` ограничен 1..1000).

> **Осознанное отклонение от буквального ТЗ** («продублировать выгрузку в karaoke-web»
> — имелся в виду `/api/stats/by-song`). Прод-nginx маршрутизирует `/api/` на karaoke-web
> (`deploy/prod-single-host/80to8897`), поэтому эндпоинт под `/api/` стал бы **публичным**
> на sm-karaoke.ru — выгрузка статистики по всем песням утекла бы наружу. Путь
> `/statbysong-export` находится там же, где сама страница `/statbysong`: на проде этот
> путь отдаёт публичный SPA, локально — доступен, как и раньше.

---

### BR-506 · `testpage.html` · SEC — разметка попадала в DOM через `innerHTML`

**Было.** `textFormatted.innerHTML = textFormattedHtml.innerText` — разметка, собранная
сервером, вставлялась как HTML. Безопасность держалась **только** на серверном
`escapeHtml()` в каждой ветке `getTextFormatted()`: любая новая ветка без escapeHtml() —
XSS.

**Стало.** Носитель разметки — инертный `<textarea>` (RCDATA: содержимое не разбирается
как HTML). Разбор делает клиент через `DOMParser` (инертный документ): в результат
попадают только теги из allow-list (`span`, `br`, `hr`) и только CSS-свойства из
allow-list (`color`, `font-size`, `font-style`, `font-weight`, `border`), а пользовательский
текст вставляется **только** текстовым узлом (`createTextNode`). Разметка из данных не
исполняется ни при каком содержимом.

---

### BR-519 · `SongShareLinkService` · DATA — перевыпуск блокировался собственным лимитом

**Было.** `countActiveForUser` считал **все** активные ссылки пользователя, включая
ссылку на **ту же песню**, которую перевыпуск собирается заменить. Пользователь, у
которого уже был выбран лимит активных ссылок, **не мог обновить свою же ссылку** —
ловил `LinkAlreadyActive`. Кроме того, `active` остаётся true до отработки sweeper'а,
поэтому **истёкшая** ссылка тоже занимала лимит.

**Стало.** `countActiveForUser(siteUserId, database, excludeSongId = songId)` —
`AND song_id <> ?` (заменяемая ссылка не считается) и `AND expires_at > now()`
(просроченные не считаются).

---

### BR-520 · `SongShareLinkService` · DATA — отзыв старой ссылки не был откатываем

**Было.** В `createLink` отзыв старой ссылки (`UPDATE … active=false`) шёл **первым** и
коммитился сам по себе (`autoCommit=true`), а `INSERT` новой ссылки выполнялся следующим
отдельным оператором. Если `INSERT` падал (нарушение ограничения, обрыв БД, таймаут) —
старая ссылка уже была отозвана, новой не появилось: пользователь **терял рабочую
ссылку** на свою песню.

**Стало.** Отзыв и вставка — **одна транзакция** (`autoCommit=false` → `commit()` /
`rollback()`), как в `revokeLinkById`. При любой ошибке — откат, состояние не меняется;
режим соединения восстанавливается в `finally` (соединение thread-local и переиспользуется
следующим запросом потока).

---

### BR-521 · `StemJobTempCleanupScheduler` · ERR — неудачное удаление не давало сигнала

**Было.** `runCatching { file.delete() }` — результат проглатывался. Файл, который не
удалился (занят другим процессом, нет прав, read-only FS), не давал **никакого**
сигнала: чистка молча переставала работать, а temp-диск тем временем заполнялся.

**Стало.** Результат **проверяется**: `runCatching { … }.getOrDefault(false)`; неудача →
счётчик `failedDeletions`, запись в журнал `infra.stemjobs.cleanup`
(`stemjobs:cleanup:delete-returned-false file=…`) и наружу — `fun failedDeletionCount()`
(метрика «диск забивается, а чистка не работает»).

---

### BR-522 · `YandexCaptchaValidationService` · ERR — «сервис лежит» и «капча не пройдена» — одно и то же

**Было.**
1. `log.warn("captcha:validation:unavailable error={}", e.message)` — **без стека**:
   таймаут, DNS и 500 валидатора выглядели одинаково.
2. `CaptchaValidationResult.UNAVAILABLE` имел код отказа `"captcha"` — **тот же**, что у
   `FAILED`. Ответ «сервис проверки недоступен» был **неотличим** от «капча не пройдена»:
   посетителю говорили «вы не робот» (400), хотя виноват был внешний сервис.

**Стало.**
1. `log.warn("captcha:validation:unavailable", e)` — исключение целиком, со стеком.
2. `UNAVAILABLE("captcha_unavailable")` — отдельный код; `PublicAuthController` отдаёт
   **503** для `captcha_unavailable` и **400** для `captcha`. Фронт
   (`karaoke-public/src/views/RegisterView.vue`) обрабатывает новый код тем же текстом
   «Регистрация временно недоступна. Попробуйте позже.», что и для
   `captcha_not_configured`.

---

## 2. Доказательство «до/после»

Тесты пакета лежат в `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/audit/`:

| Файл | Тестов | Что проверяет |
|---|---|---|
| `PkgS329SourceContractsTest` | 17 | контракт исходника: требуемый вызов/лог/страховка присутствует, запрещённая конструкция отсутствует |
| `PkgS329BehaviorTest` | 6 | поведение: параметризованный INSERT, кеш heartbeat, рендер `song.html` без `song`, исходы капчи |

Оба файла **компилируются и на старом коде** (не используют новых API) — поэтому
`git stash push -- <исходники>` даёт честное падение, а не «класс не найден».

**Проверка «до»** (исходники откатаны, тесты оставлены):

```
git stash push -- karaoke-web/src/main karaoke-public/src
→ 33 теста (пакет + два обновлённых капча-теста), упавших 23:
  • PkgS329SourceContractsTest — упали 17 из 17 (по одной на каждую запись пакета);
  • PkgS329BehaviorTest — упали 4 из 6 (BR-010, BR-026 ×2, BR-501, BR-522);
  • PublicAuthControllerCaptchaTest — упал 1 (BR-522: 400 «captcha» вместо 503
    «captcha_unavailable»);
  • YandexCaptchaValidationServiceTest — упал 1 (BR-522: код отказа UNAVAILABLE).
```

**Проверка «после»**: `git stash pop` → `BUILD SUCCESSFUL`, 33/33.

Каждая из **17 записей** пакета имеет падающий на старом коде тест — ни одна не «проверена
только чтением кода».

---

## 3. Проверки

- `env -u DISPLAY ./gradlew :karaoke-app:test :karaoke-web:test ktlintCheck --rerun-tasks` —
  BUILD SUCCESSFUL (полный набор тестов обоих модулей).
- `karaoke-public`: `npm run lint`, `npm run test:unit`, `npm run build` — PASS.
- guard'ы: `check-knowledge-structure` 9/9, `lint-knowledge`, `check-doc-references`,
  `check-recordhash-consistency`, `check-no-mp4-mentions`, `check-feature-doc`,
  `check-subagent-isolation`, `check-file-permissions`, `check-ssot-impact`,
  `check-no-personal-fixtures`, `check-audit-coverage` — PASS.

---

## 4. За владельцем

1. **`/statbysong-export`** (BR-505) — путь вне `/api/` выбран намеренно, чтобы выгрузка
   статистики не стала публичной на проде. Если нужна выгрузка и с прод-хоста (например,
   для внешнего BI), нужен другой маршрут (nginx-правило с ограничением по IP или
   admin-путь) — решение за вами.
2. **`captcha_unavailable` + 503** (BR-522) — публичный контракт регистрации изменился:
   новый код отказа и новый статус. Старый фронт (не обновлённый) покажет «Не удалось
   зарегистрироваться» — в этом пакете фронт обновлён, но если где-то есть второй
   клиент регистрации, его надо синхронизировать.
3. **Статусы `refuse`** (BR-024) — `submitted` теперь отклоняется с подсказкой
   «используй recall», `approved` — «используй delete». Если редактору нужна возможность
   отказаться и от отправленного задания (с удалением работы) — скажите, верну.
4. **`pgMaxConnections = -1`** (BR-017) при недоступной БД — если ваш мониторинг ждёт
   число, а не -1, скажите (можно отдавать 0).

## 5. Связанные записи вне пакета

- `SubscriptionsView.load()` и `StemJobsView.load()` (публичный SPA) ещё превращают
  ошибку в «пусто» — это другой корень, не в `work_package=401`, здесь не дублировал.
- `EventsBuffer` — зеркало BR-010 в `ExportAlignmentDataset.kt` (вне пакета) не тронуто.
