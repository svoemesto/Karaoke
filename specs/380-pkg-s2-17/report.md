# PKG-S2-17 — webvue3: COMPAT, DATA, ERR, RACE

- **Задача**: OpenProject #380, пакет `PKG-S2-17` (волна D, бэклог аудита #336)
- **Ветка**: `600-pkg-s2-17` (от `origin/master` @ `58f338fe`), worktree `.worktrees/waveD-s2-17`
- **Дата**: 2026-10-25
- **Записи пакета (15)**: BR-087, BR-088, BR-093, BR-124, BR-125, BR-370, BR-379, BR-380,
  BR-388, BR-399, BR-410, BR-411, BR-416, BR-417, BR-426
- **Источник правды**: `specs/336-code-audit/findings/` — порции P-52 (BR-370), P-53
  (BR-379, BR-380), P-54 (BR-388), P-55 (BR-399), P-56 (BR-410, BR-411), P-57 (BR-416,
  BR-417), P-58 (BR-426), P-61a/P-61b (BR-087, BR-088, BR-124, BR-125), P-63 (BR-093);
  сверено с `registry.csv` (15 строк, `work_package=380`)
- **ТЗ**: `specs/336-code-audit/backlog.md`, блок `### PKG-S2-17`

## Итог

| BR | Класс | Было | Стало | Файл:строка | Тест | Статус |
|---|---|---|---|---|---|---|
| BR-087 | COMPAT | `SongEdit` вычисляет статус премиум-публикации из `newsPremium*`/`premiumAttempt*`/`*AutoPublishState`, которых НЕТ в `SongDTO` — состоянии публикации и кнопки «Повторить» не рендерились никогда (FR-006…FR-008 спеки 122) | Поля добавлены в `SongDTO` и в `Song.toDTO()` (карточка `/api/song` и список `/api/songs` — один контракт); `toDTO()` по-прежнему не ходит в БД | `model/SongDTO.kt:179-197`, `model/Song.kt:9112-9120` | `SongDtoPremiumPublishStateTest` (1) | исправлено |
| BR-088 | DATA | Автосейв отложен на 1 с и нигде не сбрасывался: переход по стрелке/закрытие карточки обнуляли `diff`, правка в БД не уходила, а локальная копия выглядела сохранённой (после F5 правка исчезала). Вызов сохранения во время запроса отбрасывался (`if (this.isSaving) return`) | `flushPendingSave()` перед каждой навигацией и в `beforeUnmount`; вызов во время запроса ставится в очередь (`lib/autosave-queue.js`) и выполняется после текущего; при неудаче навигация блокируется (fail-closed) | `Songs/edit/SongEdit.vue:3786-3797,4647-4666,5820-5901`, `lib/autosave-queue.js` | `songedit-autosave-flush.test.js` (5), `autosave-queue.test.js` (4) | исправлено |
| BR-093 | RACE | Дайджест песен применял ЛЮБОЙ ответ: при двух перекрывающихся загрузках таблица показывала строки прежнего фильтра, `countRows` и массовые операции шли по устаревшему набору id; промис не возвращался, при ошибке `songsDigestIsLoading` залипал | Поколение запроса (побеждает последний запрос); флаг снимает только владелец поколения, `finally` снимает при ошибке; action возвращает промис; ошибка видна в таблице | `Songs/store.js:40-108,2747-2756`, `Songs/SongsTable.vue:48-53,731-733` | `songs-digest-race.test.js` (4) | исправлено |
| BR-124 | ERR | `dispatch('promisedXMLHttpRequest')` — несуществующий action: Vuex возвращал `undefined`, `.then` бросал TypeError ДО отправки; `isPublishingVk` оставался `true` навсегда. `id` брался из `state.currentSongId`, которого нет (модуль живёт под ключом `song`) | Прямой вызов импортированной `promisedXMLHttpRequest`; id — из `this.song.id`; флаг сбрасывается в `finally`; `idVk` из ответа пишется только для той же песни | `Songs/edit/SongEdit.vue:2542-2543,4418-4465` | `songedit-publish-guards.test.js` (4) | исправлено |
| BR-125 | RACE | `findAudioParent`/`showSubsEdit`/`loadCoAuthors` применяли ответ к песне, открытой СЕЙЧАС: поля `audio*` песни A писались в B (и автосейв отправлял их в B), разметка A затирала разметку B при сохранении субтитров | id фиксируется на отправке и сверяется перед применением; модалка субтитров получает `song-id` и сохраняет по нему (`payload.songId`), а не по `currentSongId` | `Songs/edit/SongEdit.vue:4-10,3841-3850,4022-4038,5086-5113`, `Songs/edit/SubsEdit.vue:1450-1460,3176`, `Songs/store.js:2647-2656` | `songedit-stale-response-guards.test.js` (4) | исправлено |
| BR-370 | COMPAT | Inline-сохранение `sort_order` отправляло `{fldName, fldValue, id}`: бэкенд `apisUpdateAuthor` требует `author`/`ymId`/`vkId`/`lastAlbum*`/`watched`/`skip`, а `sortOrder` не передавался вовсе — каждое изменение колонки Sort давало HTTP 400 и откат значения | Собирается полный payload (как в `AuthorAliasesModal`/`AlbumsTable`): все обязательные параметры + новое `sortOrder` | `Authors/AuthorsTable.vue:748-752`, `lib/author-payload.js` | `author-payload.test.js` (4) | исправлено |
| BR-379 | COMPAT | `loadListeningHistoryDigest` безусловно добавлял `pageSize: undefined` → в теле `pageSize=undefined` → Spring 400 → «Применить фильтр» в истории прослушиваний не работал никогда, ошибка только в консоли | Сериализатор `getParamStringToSend` пропускает `undefined`/`null` (корень для всех вызывающих); в `catch` флаг загрузки снимается | `lib/utils.js:64-95`, `ListeningHistory/store.js:65-72` | `params-serializer.test.js` (5) | исправлено (показ ошибки пользователю — за владельцем) |
| BR-380 | RACE | HR-список — один глобальный массив; ответ применялся безусловно. Ответ песни A, пришедший позже ответа B, затирал список B: модалка B показывала строки A, а «repair» отправлял ремонт ЧУЖОЙ песни | Поколение запроса + `healthReportListSongId` (песня показанного списка); `repairOneRecord` отказывает (fail-closed), если строка относится к другой песне | `Common/HealthReport/store.js:1-98,110-130`, `HealthReport/components/*.vue` | `health-report-generation.test.js` (4) | исправлено |
| BR-388 | DATA | Массовые операции брали id из snapshot, который не очищался при смене фильтра: до прихода нового snapshot (или при его сбое) кнопки были активны, а `effectiveBulkIds` отдавал id ПРОШЛОГО фильтра; fallback на `items` подменял «фильтр» «текущей страницей» | Поколение snapshot + признак готовности `bulkSelectionReady`: выборка помечается устаревшей ДО запроса, применяется только своё поколение, при сбое остаётся пустой; fallback на `items` убран; после операции snapshot пересчитывается | `Processes/store.js:59-106,280-310,575-590`, `Processes/ProcessesTable.vue:392-420,424-437` | `bulk-selection-generation.test.js` (4) | исправлено |
| BR-399 | ERR | `CustomConfirm.ok()` собирал `ret` только из `params.fields` и не выставлял `confirmed`; `ProcessEdit` проверяет `ret.confirmed` → `doSave` не вызывался никогда: форма редактирования процесса закрывалась «как будто сохранила» | «Да» = подтверждение: `buildConfirmResult(params, true)` возвращает `{confirmed: true, ...поля}`; отмена/таймаут — `{confirmed: false}`; контракт зафиксирован в JSDoc и helper'е | `Common/CustomConfirm.vue:87,236-246`, `lib/confirm-result.js` | `confirm-result.test.js` (4) | исправлено |
| BR-410 | COMPAT | Дайджест share-ссылок грузил только `{}`: бэкенд отдаёт ОДНУ страницу (25 строк, `LIMIT/OFFSET`), UI показывал её как полный набор — страницы 2+ пусты, сортировка внутри 25 строк; `perPage` уходил ключом, которого бэкенд не знает | Передаются `page`/`pageSize`/`sortBy`/`sortDir`, рендерится серверный ответ (локальная нарезка и локальная сортировка выключены); `perPage` клампится к 1..100; белый список сортировки расширен до колонок таблицы (только константные SQL-выражения) | `ShareLinks/ShareLinksTable.vue:34-50,155-200,310-400`, `lib/sharelinks-digest-params.js`, `controllers/ShareLinksAdminController.kt:24-52,118-133` | `sharelinks-digest-params.test.js` (6), `ShareLinksSortWhitelistTest` (3) | исправлено (сортировка по клиентскому `status` — за владельцем) |
| BR-411 | ERR | Пустая дата от `/api/publications/date` (штатный ответ для EXCLUSIVE/IN_WORK) уходила в `setDateTo()`, `stringDDMMYYaddDays('')` давала `NaN.NaN.N`; бэкенд падал с 500, а значение сохранялось в webvueProp и переживало F5 | Невалидная дата = «границы нет»: `stringDDMMYYaddDays` возвращает `''`, `setDateTo()` очищает `dateTo`; сохранённый отравленный фильтр чистится при загрузке; контроллер отвечает 400 на некорректный формат, разбор в `Publication` больше не бросает | `lib/utils.js:115-170`, `Publish/components/PublishTableFooter.vue:82,125-133,155-165`, `controllers/ApiController.kt:2282-2305`, `model/Publication.kt:10-30,1791-1815` | `publish-date-window.test.js` (5), `PublishFilterDateTest` (5) | исправлено |
| BR-416 | DATA | Сохранение из лёгкого редактора собирало маркеры вручную с `note:''`, `chord:''`, `stringLad:''`, `lockLad:''`: бэкенд ПОЛНОСТЬЮ заменяет набор маркеров голоса, поэтому каждое сохранение стирало ноты/аккорды/лад админа, `Song.hasChords` → false | Payload собирает `markersToSave()` (общий helper, «пронести, не управляя»), как в `SongKaraokeEditorView` и karaoke-public | `SongEditor/SongKaraokeEditorModal.vue:77,238-246` | `songkaraoke-editor-modal.test.js` (2) | исправлено |
| BR-417 | DATA | Правка планировала автосейв через 3 с, но закрытие при `saveState === 'idle'` (сразу после правки) не сохраняло и не спрашивало: `beforeUnmount` гасил таймер, POST не уходил | Явный dirty-флаг `hasUnsavedChanges`; `requestClose` синхронно сохраняет и ждёт результата, при отказе — то же подтверждение, что для `error`; `beforeUnmount` отправляет остаток | `SongEditor/SongKaraokeEditorModal.vue:128-140,165-180,215-235,262-300` | `songkaraoke-editor-modal.test.js` (4) | исправлено |
| BR-426 | COMPAT | `deleteApprovedAssignments` кладёт пустые фильтры как `undefined` → `filterAssigneeId=undefined` → Spring 400 → «Удалить одобренные» не работало, ошибки пользователю не показывалось | Корневая правка сериализатора (см. BR-379): `undefined`/`null` не передаются, сервер применяет `defaultValue` | `lib/utils.js:64-95`, `SongEditor/store.js:276-286` | `params-serializer.test.js` (5) | исправлено |

Итого: 15/15 записей закрыты правкой; ни одна не оставлена без изменения кода.
За владельцем — только части, требующие нового контракта/схемы (см. раздел ниже).

## Регресс-контроль «до/после»

Новые тесты: **59 JS** (node --test) + **9 Kotlin** (JUnit). Полный прогон — в разделе
«Проверки».

### Показательные «до/после» на старом коде

**BR-088 (DATA, потеря правки).** Тест на прежней версии компонента (`git show
HEAD~1:...SongEdit.vue`):

```
$ cp .tmp/SongEdit.old.vue webvue3/src/components/Songs/edit/SongEdit.vue
$ cd webvue3 && node --test src/components/Songs/edit/__tests__/songedit-autosave-flush.test.js
▶ автосейв сбрасывается перед сменой песни и размонтированием (BR-088)
  ✖ переход по стрелкам сначала сохраняет непустой diff
  ✖ навигация не выполняется, если сохранение не удалось (fail-closed)
  ✖ beforeUnmount отправляет непустой diff, а не просто гасит таймер
  ✖ executeSave больше не отбрасывает вызов во время запроса
  ✖ flushPendingSave дожидается и текущего, и поставленного в очередь сохранения
ℹ tests 5 · pass 0 · fail 5
  AssertionError: goToPreviousSong обязан сохранить правку
```

После правки (текущий код):

```
$ cd webvue3 && node --test src/components/Songs/edit/__tests__/songedit-autosave-flush.test.js
✔ переход по стрелкам сначала сохраняет непустой diff
✔ навигация не выполняется, если сохранение не удалось (fail-closed)
✔ beforeUnmount отправляет непустой diff, а не просто гасит таймер
✔ executeSave больше не отбрасывает вызов во время запроса
✔ flushPendingSave дожидается и текущего, и поставленного в очередь сохранения
ℹ tests 5 · pass 5 · fail 0
```

**BR-093 (RACE, устаревший ответ).** Реплика старого action (`Songs/store.js:2733-2745`
из `58f338fe`) с управляемым порядком ответов — B отвечает первым, A позже:

```
$ node .tmp/demo-br093-old.mjs
применено наборов: 2 | последний: {"songsDigests":[{"id":1}],"countRows":99}
loading-флагов: 4 (при ошибке старый код не снимал флаг вовсе)
```

Старый код применил ОБА ответа, последним — устаревший A. Новый код (`songs-digest-race.test.js`,
тот же сценарий):

```
✔ поздний ответ прежнего фильтра не затирает новый
  AssertionError не возник: применён ровно один ответ, songsDigests[0].id === 2
✔ устаревший ответ не снимает флаг загрузки актуального
✔ ошибка снимает флаг загрузки и показывается пользователю
✔ action возвращает промис (await dispatch дожидается ответа)
ℹ tests 4 · pass 4
```

**BR-379 / BR-426 (COMPAT, `undefined` в параметрах).**

```
$ node .tmp/demo-br379-old.mjs         # реплика старого getParamStringToSend
BR-379 старый: target=local&page=1&pageSize=undefined
BR-426 старый: target=local&filterAssigneeId=undefined&filterStatus=undefined&filterAuthor=undefined

$ node -e "import('./webvue3/src/lib/utils.js').then(...)"   # production-код
BR-379 новый: target=local&page=1
BR-426 новый: target=local
```

**BR-124 (ERR, несуществующий action).** Тест на прежней версии `SongEdit.vue`:

```
$ node --test src/components/Songs/edit/__tests__/songedit-publish-guards.test.js
✖ не диспатчит несуществующий action promisedXMLHttpRequest
  AssertionError: dispatch неизвестного action возвращает undefined — .then упадёт до отправки запроса
✖ фантомный action не используется во всём webvue3
✖ id берётся из объекта песни, а не из несуществующего state.currentSongId
✖ флаг isPublishingVk сбрасывается в finally
ℹ tests 4 · pass 0 · fail 4
```

**BR-416/BR-417 (DATA, потеря нот и правок).** Тест на прежней версии модалки:

```
$ node --test src/components/SongEditor/__tests__/songkaraoke-editor-modal.test.js
✖ saveNow собирает маркеры общим helper-ом, а не вручную
✔ helper, который теперь использует модалка, проносит поля сервера
✖ правка помечается несохранённой сразу
✖ успешное сохранение снимает флаг, отказ сервера — нет
✖ requestClose сохраняет непустой pending перед закрытием
✖ внешнее размонтирование тоже отправляет несохранённые правки
ℹ tests 6 · pass 1 · fail 5
```

### Остальные записи — команда и результат

| BR | Команда | Результат |
|---|---|---|
| BR-087 | `:karaoke-app:test --tests '*SongDtoPremiumPublishStateTest*'` | tests=1, failures=0 |
| BR-093 | `node --test src/components/Songs/__tests__/songs-digest-race.test.js` | tests=4, pass=4 |
| BR-125 | `node --test src/components/Songs/edit/__tests__/songedit-stale-response-guards.test.js` | tests=4, pass=4 |
| BR-370 | `node --test src/lib/__tests__/author-payload.test.js` | tests=4, pass=4 |
| BR-379/426 | `node --test src/lib/__tests__/params-serializer.test.js` | tests=5, pass=5 |
| BR-380 | `node --test src/components/Common/HealthReport/__tests__/health-report-generation.test.js` | tests=4, pass=4 |
| BR-388 | `node --test src/components/Processes/__tests__/bulk-selection-generation.test.js` | tests=4, pass=4 |
| BR-399 | `node --test src/lib/__tests__/confirm-result.test.js` | tests=4, pass=4 |
| BR-410 | `node --test src/lib/__tests__/sharelinks-digest-params.test.js` + `--tests '*ShareLinksSortWhitelistTest*'` | tests=6 + 3, failures=0 |
| BR-411 | `node --test src/lib/__tests__/publish-date-window.test.js` + `--tests '*PublishFilterDateTest*'` | tests=5 + 5, failures=0 |
| BR-088 | `node --test src/lib/__tests__/autosave-queue.test.js` | tests=4, pass=4 |

**Где тест невозможен — честно.** `SongEdit.vue`, `SubsEdit.vue` и
`SongKaraokeEditorModal.vue` — однофайловые компоненты; в проекте нет `@vue/test-utils`
(добавление зависимости выходит за рамки пакета), поэтому их инварианты (BR-088, BR-124,
BR-125, BR-416, BR-417) проверяются **статически по исходнику** — так же, как уже
существующий `subsEdit-invariants.test.js` (BR-619). Тесты режут `.vue` регулярным
выражением и сопоставляют скобки, а комментарии вырезаются до анализа (иначе тест
«находит» старый код в собственном объяснении дефекта). Поведенческая часть BR-088
(очередь не теряет вызов) вынесена в `lib/autosave-queue.js` и покрыта обычным тестом.

## Проверки

Полный прогон (последний коммит перед отчётом):

```
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle JAVA_TOOL_OPTIONS="-Djava.awt.headless=true" \
  ./gradlew --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" \
  -Djava.awt.headless=true :karaoke-app:test :karaoke-web:test ktlintCheck
# karaoke-app:test + karaoke-web:test: 747 тестов, 0 failures, 0 errors
# ktlintCheck: BUILD SUCCESSFUL
```

> **Про `JAVA_TOOL_OPTIONS`.** Без него 4 теста `VkPreviewWarmupClientTest` падают с
> `java.awt.AWTError: Can't connect to X11 window server using ':0'` и
> `NoClassDefFoundError: Could not initialize class java.awt.GraphicsEnvironment$LocalGE`.
> `-Djava.awt.headless=true` в аргументах Gradle применяется к JVM сборки, а тесты
> форкаются в отдельную JVM без этого свойства. К правкам пакета отношения не имеет
> (файлы `VkPreviewWarmupClient` не менялись); с `JAVA_TOOL_OPTIONS` — зелено.

Webvue3 (юнит-тесты фронта + линтеры):

```
$ cd webvue3 && npm run test:unit
ℹ tests 186 · suites 31 · pass 186 · fail 0

$ cd webvue3 && npx prettier --check "src/**/*.{vue,js,ts,json}"
All matched files use Prettier code style!

$ bash tools/check-eslint-baseline.sh webvue3
Текущих нарушений: 0 · В baseline: 0 · OK: новых нарушений нет
```

CI-гейты документации и структуры:

```
$ bash tools/check-knowledge-structure.sh
OK: knowledge/ structure valid (9/9 checks passed)

$ python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt
All documentation passes architectural style and structural checks.

$ python3 tools/check-doc-references.py
Проверено: ссылок 4257, путей 2432, эндпоинтов 327; whitelist: 51 записей.
(находок нет)

$ bash tools/check-recordhash-consistency.py
$ bash tools/check-no-mp4-mentions.sh
$ bash tools/check-feature-doc.sh docs/features/*.md
$ bash tools/check-subagent-isolation.sh
$ python3 tools/check-ssot-impact.py
```

Точные выводы этих пяти проверок — в разделе «Финальный прогон» ниже.

## За владельцем

Ни одна запись не оставлена «на потом» целиком, но четыре части требуют решения,
выходящего за безопасный объём пакета:

1. **BR-379 — показ ошибки загрузки пользователю в истории прослушиваний.** Корень
   (`undefined` в параметрах) устранён, флаг загрузки снимается, но `catch`
   `loadListeningHistoryDigest` по-прежнему пишет только в консоль. Для полноценного
   сообщения в админке нужен общий механизм тостов/уведомлений (сейчас тосты есть только
   в `SongEdit` через `useToast`), а это отдельная правка UX-слоя.
2. **BR-410 — сортировка по колонке `status`.** Статус share-ссылки вычисляется на
   клиенте из `active`/`expiresAt`; серверной сортировки по нему нет. Включить её —
   значит добавить в SQL вычисляемое выражение статуса (меняет контракт `ORDER BY`).
   Сейчас колонка явно не сортируема (`sortable: false`) вместо «сортировки по одной
   странице». Расширение белого списка — за владельцем.
3. **BR-088 — сохранение при закрытии ВКЛАДКИ браузера.** `beforeUnmount` покрывает
   навигацию внутри SPA и закрытие карточки, но не закрытие вкладки: `promisedXMLHttpRequest`
   строит обычный XHR без `keepalive`. Для этого нужен отдельный транспорт на `fetch(...,
   {keepalive: true})` — менять общий транспорт ради одного сценария пакет не берёт.
4. **BR-416 — уже стёртые ноты/аккорды.** Правка останавливает дальнейшую потерю, но
   данные, стёртые прежними сохранениями, не восстанавливаются: нужен бэкап/сверка с
   `SubsEdit`-источником или `recordhash`-аудит по конкретным песням (решение владельца).

Дополнительно, вне пакета (не чинилось намеренно): в `AuthorsTable` соседние inline-хендлеры
(`lastAlbumYm`, `lastAlbumVk`, `lastAlbumProcessed`, `watched`, `skip`) используют тот же
generic-saver `setAuthorValuePromise` с `fldName`/`fldValue` — то есть страдают тем же
классом дефекта, что и BR-370, но эта запись в пакет не входила. Заводить её отдельной
записью — за владельцем.

## Файлы пакета

Код:

- `karaoke-app/.../model/SongDTO.kt`, `model/Song.kt` (BR-087)
- `karaoke-app/.../controllers/ShareLinksAdminController.kt` (BR-410)
- `karaoke-app/.../controllers/ApiController.kt`, `model/Publication.kt` (BR-411)
- `webvue3/src/lib/utils.js` (BR-379, BR-411, BR-426)
- `webvue3/src/lib/confirm-result.js`, `author-payload.js`, `autosave-queue.js`,
  `sharelinks-digest-params.js` (новые модули с тестами)
- `webvue3/src/components/Songs/store.js`, `Songs/SongsTable.vue` (BR-093, BR-125)
- `webvue3/src/components/Songs/edit/SongEdit.vue`, `Songs/edit/SubsEdit.vue` (BR-088, BR-124, BR-125)
- `webvue3/src/components/Common/HealthReport/store.js`, `components/HealthReportTableRow.vue`, `HealthReportTableBody.vue` (BR-380)
- `webvue3/src/components/Processes/store.js`, `Processes/ProcessesTable.vue` (BR-388)
- `webvue3/src/components/Common/CustomConfirm.vue` (BR-399)
- `webvue3/src/components/ShareLinks/ShareLinksTable.vue` (BR-410)
- `webvue3/src/components/Publish/components/PublishTableFooter.vue` (BR-411)
- `webvue3/src/components/Authors/AuthorsTable.vue` (BR-370)
- `webvue3/src/components/SongEditor/SongKaraokeEditorModal.vue` (BR-416, BR-417)

Документация (SSoT + FR-009):

- `knowledge/domains/catalog/components/dictionaries.md` — DTO песни и премиум-статус
- `knowledge/system/frontend/vuex-patterns.md` — гонки в админ-SPA (поколение, очередь, адресность)
- `docs/features/admin-web-client-robustness.md` — per-feature документ (включая `## Известные ловушки`)

## Хэши

| Коммит | Содержание |
|---|---|
| `e5a66a17` | BR-379/399/410/411, BR-426 — параметры запросов, confirm, серверная пагинация share-ссылок, дата публикаций |
| `054db6e5` | BR-093/380/388 — поколение ответов дайджеста песен, HR-списка и bulk-snapshot процессов |
| `1731d8a7` | BR-087/088/124/125/370/416/417 — премиум-статус в DTO, flush автосейва, ручная публикация ВК, адресация ответов, payload автора, поля маркеров |
| `1b7be3bd` | SSoT (`knowledge/`), per-feature документ, отчёт пакета; ktlint-фикс `ApiController.kt` |
| этот коммит | финальный прогон: точные выводы проверок; правит только этот отчёт |

## Финальный прогон

Дословно (последний прогон перед финальным коммитом):

```
$ GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle JAVA_TOOL_OPTIONS="-Djava.awt.headless=true" \
    ./gradlew --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" \
    -Djava.awt.headless=true :karaoke-app:test :karaoke-web:test
BUILD SUCCESSFUL in 1m 17s
# test-results: 747 тестов, 0 failures, 0 errors

$ ./gradlew ... ktlintCheck
BUILD SUCCESSFUL in 8s (19 actionable tasks: 19 up-to-date)

$ cd webvue3 && npm run test:unit
ℹ tests 186 · suites 31 · pass 186 · fail 0

$ cd webvue3 && npx prettier --check "src/**/*.{vue,js,ts,json}"
All matched files use Prettier code style!

$ bash tools/check-eslint-baseline.sh webvue3
Текущих нарушений: 0 · В baseline: 0 · OK: новых нарушений нет

$ bash tools/check-knowledge-structure.sh
OK: knowledge/ structure valid (9/9 checks passed)

$ python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt
All documentation passes architectural style and structural checks.

$ python3 tools/check-doc-references.py
Проверено: ссылок 4257, путей 2432, эндпоинтов 327; whitelist: 51 записей.
OK: битых ссылок, несуществующих путей и эндпоинтов нет.

$ python3 tools/check-recordhash-consistency.py
OK recordhash-контракт: NEW-префикс в триггерах, канонические файлы == модели
(+бэкфилл), tbl_songs_sync содержит все колонки INSERT.

$ bash tools/check-no-mp4-mentions.sh
✅ OK: all 139 MP4 mentions are in baseline (grandfathered)

$ bash tools/check-feature-doc.sh docs/features/*.md
==> Все документы валидны

$ bash tools/check-subagent-isolation.sh
✅ Все commits в ветке 600-pkg-s2-17 уникальны (нет race condition)

$ python3 tools/check-ssot-impact.py
[3/3] Checked 1 rules; 0 violation(s)
OK: all matched SSoT-impact rules satisfied.
```

Примечание: `tools/check-recordhash-consistency.py` — это Python-скрипт
(`#!/usr/bin/env python3`), CI вызывает его как `python3 tools/check-recordhash-consistency.py`
(`.github/workflows/lint.yml:560`); запуск через `bash` даёт синтаксические ошибки bash и
ничего не проверяет.
