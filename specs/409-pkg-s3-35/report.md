# Отчёт пакета `PKG-S3-35` — остаток аудита #336 (OpenProject #409)

> **Issue**: OpenProject #409 — «[#336] S3: остаток аудита — 15 записей (karaoke-app, CI, документация)»
> **Пакет**: `PKG-S3-35`, ветка `638-pkg-s3-35` (от `origin/master`)
> **Дата**: 2026-10-26
> **Состав**: BR-154, BR-216, BR-217, BR-218, BR-220, BR-221, BR-222, BR-223, BR-224, BR-225, BR-226, BR-507, BR-509, BR-510, BR-517 — 15 записей (14 × S3 + BR-509, BR-517 × S2).

## 0. Итог

| Запись | Severity | Что | Статус |
|---|---|---|---|
| BR-154 | S3 | HTTP-клиенты VK/Whisper создавались на каждый вызов | исправлено |
| BR-216 | S3 | realtime-контракт `songs2.html` не совпадал с сервером | исправлено |
| BR-217 | S3 | AJAX-ошибки `songs2.html` игнорировались | исправлено |
| BR-218 | S3 | 64 синхронных XHR в `songs2.html` | исправлено |
| BR-220 | S3 | N+1 при подсчёте элементов плейлиста | исправлено |
| BR-221 | S3 | полная загрузка админских диджестов | исправлено (частично — за владельцем) |
| BR-222 | S3 | валидация `idStatus` ПОСЛЕ мутации | исправлено |
| BR-223 | S3 | check-then-act при двойном апруве | исправлено |
| BR-224 | S3 | `println` вместо SLF4J со стеком | исправлено |
| BR-225 | S3 | легаси-документация по `tbl_settings` | исправлено |
| BR-226 | S3 | у TTL-кеша статистики нет single-flight | исправлено |
| BR-507 | S3 | CI не собирает фронтенд | исправлено |
| BR-509 | S2 | guard прямых коммитов не ловил нарушение | исправлено |
| BR-510 | S3 | offline-проверка ссылок не смотрела на живые документы | исправлено |
| BR-517 | S2 | `println`/`printStackTrace` в `tryClaim` | исправлено |

**Закрыто 15 из 15.** Два решения остаются **за владельцем** (см. § 4):
удаление легаси-страниц `/songs`, `/songs2` и перевод админских таблиц
webvue3 на серверную пагинацию.

---

## 1. Карточки: что не так, где, как правильно

### BR-154 (S3) — HTTP-клиент создаётся на каждый вызов

**Где**: `karaoke-app/.../services/VkApiClient.kt:319` (`proxyClient()`),
`VkAutoPublishScheduler.kt:175`, `VkIdTokenRefreshScheduler.kt:111`,
`ApiController.kt:7957`, `WhisperAsrService.kt:119`.

**Что не так**: `VkApiClient()` в конструкторе создавал новый
`java.net.http.HttpClient` (свой selector-поток и пул соединений; `close()`
у него нет), а сам экземпляр создавался на каждый вызов. `proxyClient()`
создавал новый клиент на КАЖДЫЙ запрос в прокси-режиме. `WhisperAsrService`
создавал новый `OkHttpClient` (свой connection pool и dispatcher) на каждый
`transcribe`.

**Было**:
```kotlin
private val directClient: HttpClient = HttpClient.newBuilder()…build()   // на экземпляр
private fun proxyClient(): HttpClient? = … HttpClient.newBuilder()…build() // на запрос
```

**Стало**: прямой клиент — общий на класс (`SHARED_DIRECT_CLIENT` в
`companion object`), прокси-клиент кэшируется по URL
(`sharedProxyClient`, пересоздаётся при смене `vkProxyUrl` — как в
`TelegramApiClient`, BR-142); места, создававшие экземпляр на вызов,
используют `VkApiClient.shared` (это же сохраняет состояние прокси-режима
`useProxy`, которое иначе терялось на каждом новом экземпляре);
`WhisperAsrService` кэширует `OkHttpClient` по таймауту.

**Почему так**: минимальная правка в духе уже принятого в проекте приёма
BR-142; поведение не меняется, меняется только жизненный цикл клиента.

### BR-216 (S3) — realtime-контракт `songs2.html` рассинхронизирован

**Где**: `karaoke-app/src/main/resources/templates/songs2.html` —
`connect()` (стр. 38), `updateSettingsFromMessage()` (стр. 1988),
`updateProcessesFromMessage()` (стр. 2260).

**Что не так** (три независимых дефекта):
1. Клиент читал `recordChangeId` / `recordChangeTableName` /
   `recordChangeDiffs`, а DTO `RecordChangeMessage`
   (`model/Messages.kt:119`) отдаёт `recordId` / `tableName` / `diffs`.
2. Сравнение шло с `'tbl_settings'`, а таблица песен — `tbl_songs`
   (`Song.TABLE_NAME`, миграция 28 `28_rename_settings_to_songs.sql`).
3. Подписка шла на STOMP-эндпоинт `/api/message`, которого в `karaoke-app`
   **нет** (конфиг брокера удалён в 2024-08-15) — live-обновления не
   работали никогда.

**Стало**: подписка — SSE `GET /api/subscribe` (как в `songs.html`, BR-211),
payload — `recordId`/`tableName`/`diffs`, имя таблицы — `tbl_songs`,
`JSON.parse` — в `try/catch`. Прогресс процессов читается из `record`
(`KaraokeProcessDTO`): сервер шлёт `diffs = emptyList()` для
`tbl_processes`, поэтому прежний разбор diff'ов не давал ничего.

### BR-217 (S3) — ошибки AJAX игнорируются

**Где**: `songs2.html` — 14 вызовов `$.ajax` (стр. 567, 1008, 1024, 1046,
1063, 1078, 1100, 1127, 1153, 1180, 1195, 1217, 1345, 1897) и `doProcess()`
(стр. 1780).

**Что не так**: ни один вызов не имел `error`-обработчика; `doProcess`
красил индикатор жёлтым сразу после `send()`, не глядя на статус; `doDelete`
показывал «Удалено» на любом 2xx, хотя сервер возвращает `0`, когда песни
нет (`MainController.doDelete`: `if (song == null) return 0`).

**Стало**: у всех `$.ajax` добавлен `error`-обработчик (`reportAjaxError` —
сообщение с кодом и причиной); `doProcess` красит индикатор только в
`onSuccess`; `doDelete` различает «0» (песни нет) и «1» (удалено) и не
убирает строку из таблицы при «0».

### BR-218 (S3) — синхронный XHR

**Где**: `songs2.html` — 64 вызова `request.open('GET', …, false)`
(`doRowColor`, 40 функций копирования текста, `getSongPictureAlbum`,
`getSongPictureAuthor`, функции блока 1528–1966).

**Что не так**: синхронный XHR блокирует главный поток; на странице с
десятками тысяч строк любой медленный ответ БД — «страница не отвечает».

**Стало**: все запросы идут через `getText(url, onSuccess, onError)` —
асинхронный GET с проверкой результата (ошибка — с причиной, `onSuccess`
только при HTTP 200). Для картинок добавлена проверка «ответ не для той
строки» (`el_id.value !== id`), потому что запрос теперь асинхронный.

**Побочно**: вызовы `navigator.clipboard.writeText` заменены на
`copyToClipboardSafely` (тот же приём, что в `songs.html`, BR-212) — Clipboard
API недоступен на `http://`, и в асинхронном callback'е исключение ушло бы
в консоль без всякой реакции.

### BR-220 (S3) — N+1 при подсчёте элементов плейлиста

**Где**: `SitePlaylistsController.kt:71` (`SitePlaylistItem.countItems(pl.id, db)`),
`:60` (`SiteUser.getSiteUserById` на каждого владельца).

**Что не так**: на `N` плейлистов — `N` отдельных `SELECT COUNT(*)` и `M`
отдельных `SELECT` на владельцев, то есть `2N+1` запросов на один рендер
таблицы.

**Стало**: `SitePlaylistItem.countItemsByPlaylistIds()` — один
`SELECT playlist_id, COUNT(*) … GROUP BY playlist_id`; `SiteUser.loadByIds()`
— владельцы одним `id IN (…)` (ADR-0001 «пакетная загрузка, не по одной»).

### BR-221 (S3) — полная загрузка админских диджестов

**Где**: `SiteUsersController.kt:81` (`SiteUser.loadList` без `limit`),
`SitePlaylistsController.kt:53`, `SongEditorController.kt:222` (фильтры
`assigneeId`/`author` — в памяти после полной загрузки).

**Что не так**: диджесты грузили таблицы целиком в heap без SQL-пагинации и
без ограничения размера ответа; фильтры редактора применялись после
полной выборки.

**Стало**: диджесты принимают `page`/`perPage` (SQL `LIMIT`/`OFFSET`) и
отдают `totalCount` отдельным `COUNT(*)` с тем же набором условий
(`SiteUser.countList`, `SitePlaylist.countList`); `perPage = 0` (по умолчанию)
— полный список, как раньше (обратная совместимость); фильтры
`assigneeId` и `author` редактора перенесены в SQL
(`SongAssignment.loadList`). В `KaraokeDbTable.loadList` добавлен параметр
`orderBy` — без детерминированного `ORDER BY` страницы перекрываются.

**За владельцем**: перевод админских таблиц webvue3 на серверную пагинацию
(UI-изменение) — см. § 4.

### BR-222 (S3) — рассинхрон состояния подтверждения у редактора

**Где**: `SongEditorController.kt` — валидация `idStatus` стояла ПОСЛЕ
`song.setAllVoices(...)` (сохраняет песню в БД).

**Что не так**: запрос с `idStatus=7` успевал записать маркеры/текст и
поднять `id_status`, и только потом отвечал `invalid_idstatus` — состояние
БД не соответствовало ответу. Контракт спеки #184 требует: «валидация
выполняется до любых записей».

**Стало**: валидация перенесена в начало `approveInternal()` — до чтения
черновика и до любых мутаций.

**Проверено**: вторая часть карточки («ветка `catch` вокруг `aRead.save()`
недостижима, `save()` глотает `SQLException`) **уже неактуальна** — после
BR-176 `KaraokeDbTable.save()` бросает `IllegalStateException`, поэтому
ветка достижима и отвечает `save_failed`.

### BR-223 (S3) — двойное подтверждение

**Где**: `SongEditorController.kt:360` (check-then-act на `adminStatus`),
`SongEditorController.kt:549` (`aRead.save()`).

**Что не так**: два параллельных запроса оба читали
`adminStatus != APPROVED`, оба применяли разметку, оба пушили песню на
SERVER и оба ставили тяжёлый процесс `RENDER_MP4_DEMO` (HEAVY_RENDER).

**Стало**: переход статуса — атомарный условный `UPDATE`:
```sql
UPDATE tbl_song_assignments SET admin_status='approved', reviewed_at=?, review_comment=''
WHERE id = ? AND admin_status <> 'approved'
```
(`SongAssignment.markApprovedIfNotApproved`) — ровно один вызов выигрывает,
остальные получают `already_approved`. Плюс полосовый лок по `id % 16` в
`approve()` (фиксированный массив — память не растёт, в отличие от
`songLocks` в BR-155): второй запрос ждёт первый и после перечитывания
видит `approved`.

### BR-224 (S3) — `printStackTrace` в редакторе

**Где**: `SongEditorController.kt:463` (сбой push на SERVER), `:516` (сбой
фоновой синхронизации), `:471` (сбой применения разметки), `:1136`
(render-demo helper).

**Что не так**: `println` с одним `e.message` — без логгера, уровня и
стека; в фоновом `thread` throwable вообще не оставался.

**Стало**: `log.error("approve: … ", …, e)` через SLF4J
(`LoggerFactory.getLogger(SongEditorController::class.java)`), стек
сохраняется. Информационные `println` с префиксом `[approve/…]` оставлены —
они диагностические (grep по префиксу) и не являются «потерей стека».

### BR-225 (S3) — легаси-документация по `tbl_settings`

**Где**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/docs/*.sql`
(9 файлов, 11 вхождений).

**Что не так**: рабочие ad-hoc-запросы админа обращались к таблице
`tbl_settings`, которую переименовали в `tbl_songs` (миграция 28) — запросы
падали с `relation "tbl_settings" does not exist`.

**Стало**: имя таблицы приведено к действующей схеме (`tbl_songs`); guard
`tools/check-no-legacy-tbl-settings.sh` получил секцию [6/6] на легаси-
документацию (комментарии `--` допускаются, как и в миграциях). Механизм
baseline не изменён; в baseline **ничего не добавлено**.

**Важно (решение владельца)**: удаление легаси-страниц `/songs`, `/songs2`
**не принято** — страницы НЕ удалялись, документация приведена в соответствие
с действующей схемой. См. § 4.

### BR-226 (S3) — отсутствие single-flight для статистики

**Где**: `StatsController.kt:81` (`respondCached`), `StatsCache.kt`.

**Что не так**: `get` → (промах) → `compute` → `put` без дедупликации: два
одновременных запроса после истечения TTL оба выполняли тяжёлый `COUNT(*)`
по `tbl_events` — ровно то, ради чего кеш вводился (spec 174).

**Стало**: `StatsCache.getOrCompute(key, supplier)` — первый поток берёт
ключ в `inflight` (`ConcurrentHashMap<StatsCacheKey, CompletableFuture>`) и
считает, остальные ждут его результат. Ошибка вычислителя доходит до
ожидающих как исходное исключение (`ExecutionException` разворачивается) —
`SQLException` по-прежнему даёт `503 stats.unavailable`.

### BR-507 (S3) — CI без сборки/тестов

**Где**: `.github/workflows/lint.yml`.

**Что не так**: сборки и тесты бэкенда уже были (джобы `backend-build` /
`backend-test`, пакет #369), но **фронтенд не собирался**: PR с не
собирающимся SPA проходил CI и мержился в master.

**Стало**: добавлен джоб `frontend-build` (`npm run build` в матрице
`webvue3` / `karaoke-public`) — та же команда, что локально по AGENTS.md
§ «Обязательная проверка после изменения».

### BR-509 (S2) — «прямой коммит» guard поверхностный

**Где**: `.github/workflows/lint.yml`, шаг «No direct commits to master».

**Что не так** (разбор): инлайн-шаг считал коммиты так:
```bash
DIRECT_COMMITS=$(git log --first-parent origin/master --since=24.hours --no-merges \
                 --pretty=format:"%H %s" | wc -l | tr -d ' ')
```
Вывод `git log --pretty=format:` **не заканчивается переводом строки**, а
`wc -l` считает именно переводы строк. Проверено на фикстуре: при 5
коммитах `wc -l` даёт **4**. Значит **один** прямой коммит давал
`DIRECT_COMMITS = 0` — guard не падал никогда. В истории master такой
коммит есть: `6cbaad02` (2026-10-24, «specs(336): задачи волн B–E …») —
прямой коммит в master, минуя PR, и guard его не заметил.

**Стало**: guard вынесен в отдельный скрипт `tools/check-direct-commits.sh`:
- считает через `git log --first-parent` с разбором числа родителей (без
  off-by-one);
- проверяет окно `--since` (по умолчанию 30 дней) и baseline
  `tools/check-direct-commits.baseline` (известные исторические нарушения);
- недоступный ref → код **2** («проверка не выполнена»), а не «нарушений
  нет» (правило пакета #402);
- `--self-test` — негативная фикстура: (а) корректная история → PASS,
  (б) подсовытый прямой коммит → FAIL с ненулевым кодом, (в) недоступный
  ref → код 2. Скрипт подключён к CI (джоб `knowledge-structure`).

### BR-510 (S3) — каталог per-feature документов неверный

**Где**: `.github/workflows/lint.yml` (шаг lychee), `.pre-commit-config.yaml`.

**Что не так**: проверка **структуры** per-feature документов уже смотрела
оба каталога (`docs/features/*.md archive/docs/features/*.md`, пакет #369),
но offline-проверка **ссылок** (lychee) — только `archive/docs/features/`:
битые ссылки в живых документах (60 файлов) не ловились.

**Стало**: в аргументы lychee добавлен `docs/features/`.

### BR-517 (S2) — `debug println` в `tryClaim`

**Где**: `karaoke-web/.../services/SongShareLinkService.kt:591`
(отладочный `log.error` на штатном пути), `:711` и `:716` (`println` +
`printStackTrace` в catch).

**Что не так**: каждый успешный claim логировался на уровне ERROR («DEBUG:
принудительно log.error вместо log.info») — шум в алертах; в catch стек
писался через `println` + `printStackTrace()` в stdout контейнера, где нет
ротации docker-логов, а в логгер попадало только сообщение без стека.

**Стало**: штатный путь — `log.debug`; в catch — `log.error("…", e)` со
стеком, без `println`/`printStackTrace`.

---

## 2. Регрессионные проверки «до/после»

### 2.1 Юнит-тесты (новые)

| Файл | Что проверяет |
|---|---|
| `karaoke-app/src/test/.../services/VkHttpClientReuseTest.kt` | BR-154: прокси-клиент переиспользуется (в т.ч. между экземплярами), прямой клиент общий, `OkHttpClient` ASR переиспользуется |
| `karaoke-app/src/test/.../services/StatsCacheSingleFlightTest.kt` | BR-226: при 8 параллельных запросах supplier вызывается **ровно один** раз; ошибка доходит до ожидающих как есть |
| `karaoke-app/src/test/.../audit/PkgS335SourceContractsTest.kt` | BR-216…BR-225, BR-507, BR-509, BR-510 — контракты исходников и шаблонов (13 тестов) |
| `karaoke-web/src/test/.../audit/PkgS335SourceContractsTest.kt` | BR-517 — нет `println`/`printStackTrace`, штатный путь не ERROR (2 теста) |

### 2.2 Guard-скрипты (до/после)

| Guard | «До» | «После» |
|---|---|---|
| `tools/check-no-legacy-tbl-settings.sh` (секция [6/6]) | легаси-документация с `tbl_settings` — нарушение не ловилось | `FAIL` (код 1) на подсовытом `tbl_settings`, `OK` (код 0) после правки — проверено вручную |
| `tools/check-direct-commits.sh --self-test` | guard'а не существовало | (а) корректное дерево → PASS, (б) подсовытый прямой коммит → FAIL (код 1), (в) недоступный ref → код 2 |

### 2.3 Проверка «до/после» по контрактам (скрипт)

Для каждой записи проверено, что соответствующее утверждение теста
**меняет состояние** между `HEAD` (до правки) и рабочей копией (после):

```
BR-216 ключи recordId/tableName/diffs              ДО=False ПОСЛЕ=True
BR-216 таблица tbl_songs                            ДО=False ПОСЛЕ=True
BR-216 SSE вместо STOMP                             ДО=False ПОСЛЕ=True
BR-217 error-обработчики у всех ajax                ДО=False ПОСЛЕ=True
BR-218 нет синхронного XHR                          ДО=False ПОСЛЕ=True
BR-220 countItemsByPlaylistIds вместо countItems    ДО=False ПОСЛЕ=True
BR-220 владельцы батчем                             ДО=False ПОСЛЕ=True
BR-221 totalCount в ответе                          ДО=False ПОСЛЕ=True
BR-221 фильтры в SQL                                ДО=False ПОСЛЕ=True
BR-222 валидация idStatus до setAllVoices           ДО=False ПОСЛЕ=True
BR-223 атомарный markApprovedIfNotApproved          ДО=False ПОСЛЕ=True
BR-224 SLF4J-логгер контроллера                     ДО=False ПОСЛЕ=True
BR-224 нет println в push-catch                     ДО=False ПОСЛЕ=True
BR-224 нет println в sync-related                   ДО=False ПОСЛЕ=True
BR-225 в легаси-документах нет tbl_settings         ДО=False ПОСЛЕ=True
BR-226 single-flight getOrCompute                   ДО=False ПОСЛЕ=True
BR-507 джоб frontend-build                          ДО=False ПОСЛЕ=True
BR-509 guard отдельным скриптом                     ДО=False ПОСЛЕ=True
BR-509 старый off-by-one убран                      ДО=False ПОСЛЕ=True
BR-510 lychee args: docs/features                  ДО=False ПОСЛЕ=True
```

---

## 3. Проверки

### 3.1 Backend (gradle, `--rerun-tasks`)

```bash
env -u DISPLAY GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true \
  :karaoke-app:test :karaoke-web:test ktlintCheck --rerun-tasks
```

Результат: **BUILD SUCCESSFUL** (см. § 5 — список прогонов и коды).

### 3.2 Guard'ы из `.github/workflows/lint.yml`

Полный список `run:`-команд, извлечённый из workflow, прогнан локально:

| Команда | Код |
|---|---|
| `bash tools/check-actions-pinned.sh` | 0 |
| `bash tools/check-actions-pinned.sh --self-test` | 0 |
| `bash tools/check-root-junk.sh` | 0 |
| `bash tools/check-gradle-wrapper.sh` | 0 |
| `bash tools/check-gradle-wrapper.sh --self-test` | 0 |
| `bash tools/check-build-config.sh` | 0 |
| `bash tools/check-build-config.sh --self-test` | 0 |
| `bash tools/gradle-heap-dump.sh --self-test` | 0 |
| `python3 tools/check-confirm-escape.py` | 0 |
| `bash tools/check-confirm-escape-fixtures.sh` | 0 |
| `bash tools/check-marker-escape-guard.sh` | 0 |
| `bash tools/check-secret-in-argv.sh` | 0 |
| `bash tools/check-secret-in-argv.sh --self-test` | 0 |
| `bash tools/check-file-permissions.sh` | 0 |
| `bash tools/check-file-permissions.sh --self-test` | 0 |
| `bash tools/check-feature-doc.sh docs/features/*.md archive/docs/features/*.md` | 0 |
| `bash tools/baseline-stats.sh` | 0 |
| `bash tools/check-kdoc-coverage.sh --strict` | 0 |
| `bash tools/check-jsdoc-coverage.sh --strict` | 0 |
| `python3 tools/check-ssot-impact.py` | 0 |
| `bash tools/check-knowledge-structure.sh` | 0 |
| `bash tools/check-no-legacy-tbl-settings.sh` | 0 |
| `python3 tools/check-recordhash-consistency.py` | 0 |
| `bash tools/check-knowledge-cross-links.sh` | 0 |
| `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt` | 0 |
| `python3 tools/check-doc-references.py` | 0 |
| `python3 tools/check-spec-issue-link.py` | 0 |
| `bash tools/check-direct-commits.sh` | 0 |
| `bash tools/check-direct-commits.sh --self-test` | 0 |
| `bash tools/check-docker-image-tags.sh --quiet` | 0 |
| `bash tools/check-no-jpa-imports.sh --quiet` | 0 |

(Полный вывод и коды — в § 5.)

### 3.3 Фронтенд

Фронтенд (webvue3 / karaoke-public) **не менялся** — правки только в
backend, CI и документации. Соответственно, `npm run lint` /
`npm run build` / `npm run test:unit` для SPA не потребовались; в CI добавлен
джоб `frontend-build` (`npm run build`), который отработает на следующем
прогоне workflow.

---

## 4. Что осталось за владельцем

1. **Удаление легаси-страниц `/songs` и `/songs2`.** Решение владельца **не
   принято** — страницы оставлены, BR-216/217/218 исправлены **на месте**
   (в `songs2.html`). При удалении легаси-страниц эти три записи станут
   **неактуальны** (их следует закрыть как «неактуально», а не переносить:
   `songs.html` уже приведён к SSE-контракту в BR-211 пакета #396).
2. **Перевод админских таблиц webvue3 на серверную пагинацию.** Бэкенд
   готов (параметры `page`/`perPage` + `totalCount`), но UI (SiteUsersTable,
   SitePlaylistsTable) пока пагинирует на клиенте. Это UI-решение владельца;
   до него поведение прежнее (`perPage = 0` → полный список).
3. **Windows для guard'а прямых коммитов.** Сейчас 30 дней + baseline из
   одного исторического нарушения. Увеличение окна (и перенос остальных
   известных нарушений в baseline) — решение владельца: переписывать историю
   нельзя.
4. **Squash-merge.** Guard считает нарушением любой не-merge коммит в
   первой-родительской цепочке, включая squash-merge (`(#N)` в subject).
   Политика репозитория — «в master только merge-коммиты»
   (`gh pr merge --merge`); если владелец решит разрешить squash-merge —
   правило guard'а надо ослабить (решение владельца).

---

## 5. Журнал проверок (фактические выводы)

### 5.1 Backend

```
$ env -u DISPLAY GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 \
    -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true \
    :karaoke-app:test :karaoke-web:test ktlintCheck --rerun-tasks
BUILD SUCCESSFUL in 58s
```

Результаты тестов (XML-отчёты JUnit):

| Модуль | Классов | Тестов | Пропущено | Падений | Ошибок |
|---|---|---|---|---|---|
| `karaoke-app` + `karaoke-web` | 151 | 1085 | 0 | 0 | 0 |

Новые классы пакета: `PkgS335SourceContractsTest` (karaoke-app) — 13 тестов,
`PkgS335SourceContractsTest` (karaoke-web) — 2 теста, `VkHttpClientReuseTest` —
5 тестов, `StatsCacheSingleFlightTest` — 3 теста. Итого 23 новых теста.

`ktlintCheck` (baseline-aware) — без нарушений.

### 5.2 Guard'ы из `.github/workflows/lint.yml`

Все 36 `run:`-команд, извлечённых из workflow, прогнаны локально —
**все с кодом 0** (см. таблицу в § 3.2). Отдельно проверено поведение
guard'ов «до/после»:

```
$ bash tools/check-no-legacy-tbl-settings.sh
[6/6] Проверка легаси-документации karaoke-app/.../docs/*.sql...
OK: 1800 файлов проверено, нарушений нет.            # код 0

# подсовытое нарушение (tbl_settings в легаси-документации):
FAIL: найдено 1 нарушений (проверено 1800 файлов).   # код 1

$ bash tools/check-direct-commits.sh
OK: 202 коммитов проверено (окно: 30.days), прямых коммитов нет.   # код 0

$ bash tools/check-direct-commits.sh --self-test
OK: корректная история (только merge-коммиты) — PASS
OK: прямой коммит пойман (код возврата 1, ожидался != 0)
OK: недоступный ref → код 2 (проверка не выполнена)
SELF-TEST: PASS                                        # код 0
```

### 5.3 Фронтенд

Фронтенд не менялся (правки — backend, CI, документация). Для полноты
прогнан ESLint-baseline для обоих SPA (`bash tools/check-eslint-baseline.sh
webvue3` / `karaoke-public`) — код 0.

---

## 6. Документация

- `docs/features/pkg-s3-35.md` — per-feature документ (FR-009), раздел
  «## Известные ловушки» (легаси-страницы, окно guard'а, серверная пагинация,
  `filterStatus` в памяти, BR-627 из #410).
- `knowledge/domains/integration/components/external-api-clients.md` — BR-154.
- `knowledge/domains/stats/domain.md` — BR-226 (single-flight).
- `knowledge/domains/editorial/components/assignment-lifecycle.md` —
  BR-222/223/224.
- `knowledge/domains/sse/domain.md` — BR-216 (контракт payload'а).
- `knowledge/domains/identity/components/dictionaries.md` — BR-220/221
  (пакетная загрузка `SiteUser`, требуется правилом `.ssot-map.yml`).
- `knowledge/system/infra/ci-tools.md` — BR-509/507/510/225.

---

## 7. Вне области пакета

- **BR-627 (S2, утечка соединений)** — отдельная задача **#410**. Правки
  этого пакета не добавляют новых мест, где соединение открывается на
  поток, и не ухудшают ситуацию: `withDb` по-прежнему закрывает соединение
  в `finally`; новых `Connection.local()/remote()` во внутренних циклах нет.
- `specs/336-code-audit/registry.csv` — **не трогали** (источник истины —
  ведёт лид).
