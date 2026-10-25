# PKG-S3-28 (OpenProject #400) — karaoke-public: COMPAT, ERR, LEAK, PERF, RACE, SEC — 15 BR

- **Аудит**: #336, волна E, пакет `PKG-S3-28` (`specs/336-code-audit/backlog.md`, блок `### PKG-S3-28`).
- **Ветка**: `629-pkg-s3-28` от `origin/master` `061c484d` (merge PR #726 — пакет #399/`PKG-S3-27`).
- **Worktree**: `/home/nsa/Karaoke/.worktrees/waveE-s3-28`.
- **Источник правды**: карточки `specs/336-code-audit/findings/P-47.md`, `P-48.md`, `P-49.md`;
  номера BR — из `specs/336-code-audit/registry.csv` (`work_package = 400`).
- **Что НЕ делалось**: `specs/336-code-audit/registry.csv` не трогался; миграции не применялись
  ни к LOCAL, ни к PROD; контейнеры, прод и БД не трогались; деплоя не было; push и PR
  не делались (по условию задачи); главная копия и соседние worktree не изменялись.
- **Режим**: пакет собран одним проходом (15 записей), регрессия — двухуровневая
  (поведенческие тесты чистых функций + контрактные тесты по тексту исходника).

## Сводка

| Блок | BR | Статус |
|---|---|---|
| Поиск: индекс авторов | BR-347 (1) | исправлена |
| Плеер плейлиста: таймеры токена и доверие postMessage | BR-348, BR-349 (2) | исправлены |
| Возврат с оплаты: таймер опроса | BR-351 (1) | исправлена |
| Подписки: отмена автопродления | BR-352 (1) | исправлена |
| Закромы: состояние из маршрута, retry, «автор не найден» | BR-353, BR-355, BR-357 (3) | исправлены |
| Закромы: авторские поля (бэкенд + фронт) | BR-354 (1) | исправлена |
| Закромы: поколение стрима (store) | BR-356 (1) | исправлена |
| Share-ссылка: разведение ошибок и lease | BR-358, BR-359 (2) | исправлены |
| Песня: признак SKIP | BR-360 (1) | исправлена |
| Задания стемов: таймер опроса и возврат удалённой карточки | BR-361, BR-362 (2) | исправлены |

**Все 15 записей пакета закрыты.** Ни одна запись не оказалась «уже исправлена пакетом
#399»: фактическое состояние кода проверено по каждой строке реестра **после** merge
PR #726 (`origin/master` `061c484d`) — все 15 дефектов присутствовали.

---

## Сверка с пакетом #399 (PKG-S3-27) — что НЕ дублировалось

Пакет #399 (PR #726) закрыл 40 записей аудита в том же модуле `karaoke-public` и
добавил 13 чистых модулей в `karaoke-public/src/utils/`. Проверка по каждой из
15 записей:

| BR | Пересечение с #399 | Вывод |
|---|---|---|
| BR-347 | #399 менял `SearchView.vue`, но только по BR-344/BR-345 (URL-состояние и флаги ошибок поиска) | `authorIdFor` остался линейным `find` — дефект открыт |
| BR-348 | #399 не трогал `PlayerView.vue` | открыт |
| BR-349 | #399 не трогал `PlayerView.vue` | открыт |
| BR-351 | #399 не трогал `SubscriptionReturnView.vue` (BR-350 закрыт пакетом `PKG-S2-14`) | открыт |
| BR-352 | #399 не трогал `SubscriptionsView.vue` | открыт |
| BR-353 | #399 менял `ZakromaView.vue` (BR-320 — отмена стрима), но не состояние маршрута | открыт |
| BR-354 | #399 менял `store/modules/zakroma.js` (BR-320) и `useZakromaStreamProgress.js` (BR-313) — но авторских полей в `meta` не появилось | открыт |
| BR-355 | #399 не менял `retryLoadZakroma` | открыт |
| BR-356 | #399 добавил cancel-and-restart (BR-320), но **без** поколения: `commit('setZakroma')` делал завершившийся последним стрим | частично закрыт, остаток (чужой автор на странице, чужой прогресс-бар) — открыт |
| BR-357 | #399 не трогал | открыт |
| BR-358 | #399 менял `components/ShareLinkButton.vue`/`ShareLinkModal.vue` (BR-296/BR-301), но не лендинг `ShareView.vue` | открыт |
| BR-359 | #399 не трогал `ShareView.vue` | открыт |
| BR-360 | #399 не трогал `SongView.vue` | открыт |
| BR-361 | #399 не трогал `StemJobsView.vue` | открыт |
| BR-362 | #399 не трогал `StemJobsView.vue` | открыт |

Итог: 14 записей дефектов не коснулись правок #399; **BR-356 закрыт лишь частично** —
механизм отмены (BR-320) появился, а «поколение стрима» (защита от коммита чужого
автора) — нет. Ничего из 15 записей не дублируется и не переписывается заново.

---

## Проверка фактического состояния (до правки)

Прочитана актуальная версия кода на `origin/master` `061c484d` (после merge PR #726)
по каждой строке реестра. Номера строк в бэклоге — от снимка аудита `1977838a`;
ниже — строки текущей ветки до правки.

| BR | Где смотрел | Что было в коде на начало пакета |
|---|---|---|
| BR-347 | `views/SearchView.vue:316-321, 123-124` | `authorTiles.value.find(...)` — линейный поиск по каталогу, два вызова на строку (`v-if` + `params`) |
| BR-348 | `views/PlayerView.vue:87-93, 234-237` | `setTimeout(..., 8000)` без сохранения хэндла; `beforeUnmount` таймеры не снимал, `pendingToken` не резолвил |
| BR-349 | `views/PlayerView.vue:129-131` | `if (!d || d.source !== 'kp-playlist') return` — ни `e.origin`, ни `e.source` не проверялись |
| BR-351 | `views/SubscriptionReturnView.vue:150-152, 213` | `setTimeout(() => this.poll(), delay)` без ручки; `beforeUnmount` отсутствовал; `$router.replace` безусловный |
| BR-352 | `views/SubscriptionsView.vue:109-110` | `await authPost(...)` без проверки `status` и без `catch`, затем безусловный `await this.load()` |
| BR-353 | `views/ZakromaView.vue:446, 563-567` | `authorChosen: hasAuthor` (для `/zakroma/special-bucket` всегда `false`); back-link строил `/zakroma/${selectedAuthorId}/albums` при пустом id |
| BR-354 | `views/ZakromaView.vue:189-204`, `store/modules/zakroma.js:263-270` | `zak.authorWarning/authorDescription/authorShortDescription/authorSkip` в шаблоне; в объекте стрима этих полей нет, в NDJSON `meta` их нет |
| BR-355 | `views/ZakromaView.vue:905-907` | `retryLoadZakroma()` → `onAuthorSelect(...)` → `router.push('/zakroma/{id}/albums')` |
| BR-356 | `store/modules/zakroma.js:254-297` | `commit('setZakroma', ...)` без проверки «чей это стрим»; `finally` всегда гасил `setStreaming(false)` |
| BR-357 | `views/ZakromaView.vue:707-713` | ветка `else` с `typeof this.notify === 'function'` — мёртвый no-op, состояния «автор не найден» нет |
| BR-358 | `views/ShareView.vue:122-125` | `else state.value = 'notfound'` — любой не-200 без доменного кода становился «ссылка мертва» |
| BR-359 | `views/ShareView.vue:87, 118, 141-147` | `expiresAt` сохранялся и нигде не использовался; `openPlayer()` шла в плеер с заведомо истёкшим lease |
| BR-360 | `views/SongView.vue:466-471` | `currentSong.tags` — такого поля в `SongPublicDto` нет → геттер всегда `false` |
| BR-361 | `views/StemJobsView.vue:203, 245-257` | `let pollTimer = null` на уровне модуля; `setInterval` создавался **после** `await this.load()` |
| BR-362 | `views/StemJobsView.vue:322, 392-395` | `this.jobs = body` заменял список целиком — удалённая карточка возвращалась при ближайшем поллинге |

---

## По записям

| BR | Файл:строка (было) | Класс | Что не так | Как правильно | Проверка |
|---|---|---|---|---|---|
| BR-347 | `views/SearchView.vue:316-321` (`setup`), `:123-124` (шаблон) | PERF | `authorIdFor(name)` — линейный `find` по каталогу авторов (`/authors-tiles`, тысячи записей), вызывался **дважды** на строку выдачи (`v-if` + `params`) → O(строк × тайлов × 2) на **каждый** рендер списка (битое превью, смена страницы, событие плеера) | `buildAuthorIndex()` (`src/utils/authorIndex.js`) — `Map<name, id>`, пересборка только при смене `authorTiles`; резолв **один раз** на строку в `computed searchRows` (`song.authorId` в шаблоне) | поведенческая (6) + контрактная (2) |
| BR-348 | `views/PlayerView.vue:87-93` (`requestToken`), `:97-127`, `:234-237` | LEAK | `setTimeout(..., 8000)` без сохранения хэндла: снять таймер при размонтировании нечем, промис не отменялся. После ухода со страницы цепочка жила: таймер → `null` → `advanceAfterEnd()` → следующий трек → новый таймер → запросы к membership и postMessage (до 500 песен) | хэндлы в карте `tokenTimers`; `clearTokenTimers()` (`src/utils/tokenTimers.js`) в `onBeforeUnmount` снимает таймеры и резолвит ожидающие промисы как `null`; флаг `destroyed` останавливает `playPos`/`advanceAfterEnd` | поведенческая (4) + контрактная (1) |
| BR-349 | `views/PlayerView.vue:129-131` | SEC | обработчик `message` проверял только строковый маркер `d.source === 'kp-playlist'` — константу из бандла. Фрейминг публичного сайта не запрещён (на nginx нет `X-Frame-Options`/`frame-ancestors`), а `/player/:id` пускает во фрейм с `?session=` → сторонняя страница могла слать `setqueue`/`playid`/`toggle`/`token` | `isTrustedParentMessage(event, {origin, source})` (`src/utils/messageOrigin.js`): сверяются origin текущего сайта и `window.parent`; строковый маркер остаётся третьим фильтром | поведенческая (5) + контрактная (2) |
| BR-351 | `views/SubscriptionReturnView.vue:213` (`setTimeout`), `:230` (`$router.replace`) | LEAK | таймер опроса не сохранялся и не снимался: цепочка жила ещё 12 с (до 5 лишних `GET`), а при `PAID` для `scope=SONG` выполняла `$router.replace('/song?id=…')` — уводя пользователя с той страницы, на которой он уже был | `pollTimer` в состоянии компонента, `clearPollTimer()` в `beforeUnmount`; авто-переход — только при `!isUnmounted` и `$route.path === '/subscription/return'` | контрактная (2) |
| BR-352 | `views/SubscriptionsView.vue:109-110` | ERR | результат `authPost` игнорировался: при 401/404/500 (или обрыве сети) список молча перезагружался, пользователь не узнавал, что карта **не отвязана** | проверка `status !== 200` → видимый текст ошибки и **без** перезагрузки; успех → подтверждение + перезагрузка; `catch` на сетевой reject | контрактная (2) |
| BR-353 | `views/ZakromaView.vue:446`, `:557-573` | ERR | состояние выводилось из клика, а не из маршрута: прямой заход на `/zakroma/special-bucket` (F5/закладка) рендерил сетку авторов вместо таблицы спец-корзины; back-link в спец-корзине строил `/zakroma//albums` (маршрута нет) → пустая страница | `authorChosenForRoute(path, params)` (`src/utils/zakromaRoute.js`); back-link — `zakromaBackTarget(state)`: спец-корзина проверяется первой, пустой `authorId` в ссылку не попадает | поведенческая (6) + контрактная (1) |
| BR-354 | `views/ZakromaView.vue:189-204`, `store/modules/zakroma.js:263-270`, `karaoke-web/.../ZakromaStreamMessageDto.kt` | COMPAT | шаблон читал `zak.authorWarning`/`authorDescription`/`authorShortDescription`/`authorSkip`, но при переходе страницы песен автора на NDJSON-стрим эти поля **терялись**: в `meta` их не было, в объекте стора — тоже. Предупреждение автора (в т.ч. правовое «удалено по требованию правообладателя») на публичной странице не показывалось **никогда** | бэкенд: `Author.loadPublicMetaByName` (один лёгкий raw-SELECT) → необязательные поля в NDJSON `meta`; фронт: composable читает их из `meta`, store кладёт в объект автора. Поля nullable — старый бэкенд/клиент остаются совместимыми | поведенческая (0) + контрактная (5) + Kotlin (5) |
| BR-355 | `views/ZakromaView.vue:905-907` | ERR | «Повторить» вызывал `onAuthorSelect(...)` → `router.push('/zakroma/{id}/albums')` и сброс состояния: кнопка не повторяла загрузку, а уводила со страницы; упавший стрим не перезапускался | `retryLoadZakroma()` запускает стрим с теми же параметрами, что и штатный старт (`zakromaStreamParams(tile, albumId)` → `author`, `expectedCount`, `albumId`, `force: true`) — **без навигации** | поведенческая (3, общий с BR-353/357) + контрактная (1) |
| BR-356 | `store/modules/zakroma.js:254-297`, `:326-340` | RACE | коммитил результат тот стрим, который завершился последним: стрим автора A, завершившийся после старта стрима B, показывал песни A на странице `/zakroma/B`, а его `finally` гасил `isStreaming` (прогресс-бар B исчезал) и показывал ошибку A | поколение стрима (`src/utils/streamGeneration.js`): коммитить данные/ошибку/`setStreaming` вправе только текущее поколение; отмена инвалидирует поколение; устаревшее поколение молча уходит, освобождая ресурсы в `finally` | поведенческая (4) + контрактная (1) |
| BR-357 | `views/ZakromaView.vue:707-713` | ERR | при отсутствии тайла автора страница была просто пустой (мёртвый `notify`-no-op): ни стрима, ни ошибки, ни объяснения | явное пустое состояние «Автор не найден» (`authorNotFound`), выставляемое **после** загрузки каталога (`isAuthorMissing(tile, tilesLoaded)`) — иначе это «ещё грузится» | поведенческая (3, общий с BR-353/355) + контрактная (1) |
| BR-358 | `views/ShareView.vue:122-125` | ERR | все ответы claim сводились к `notfound`: `500 share.internal`, `400 tokenMissing`, любой не-200 без `errorCode` показывались как «срок действия ссылки истёк» (ложный диагноз), а в этой ветке не было кнопки повтора | `shareClaimState(status, body)` (`src/utils/shareClaim.js`): `notfound` — только `share.notFound`/`share.tokenMissing`; 5xx/`share.internal`/сеть — отдельное состояние `error` («сервис недоступен») с кнопкой «Повторить» | поведенческая (7) + контрактная (1) |
| BR-359 | `views/ShareView.vue:87, 118, 141-147` | ERR | lease (90 с) сохранялся и **не использовался**: лендинг не шлёт heartbeat, поэтому после ~2,5 мин простоя sweeper обнулял сессию → `/playerdata` отдавал 404 → «Ошибка: HTTP 404» вместо плеера | `needsReclaim(expiresAt, now)` перед переходом в плеер: истёкший (или неизвестный) lease → повторный claim; «сессия истекла» показывается пользователю | поведенческая (5, общий с BR-358) + контрактная (1) |
| BR-360 | `views/SongView.vue:466-471` | COMPAT | `currentSong.tags` — такого поля в `SongPublicDto` нет (есть вычисленный `contentRemoved`): геттер всегда `false` → бейдж SKIP не показывался никогда, а кнопки «Поделиться»/«Временный доступ» не скрывались для SKIP-песен (сервер отвечал 409 `SongSkipped`) | используем `currentSong.contentRemoved` — сервер считает его из `tags` **точным** сравнением токена (`split(" ").contains("SKIP")`), а не подстроки | контрактная (1) |
| BR-361 | `views/StemJobsView.vue:203, 245-257` | LEAK | `setInterval` создавался **после** `await this.load()`: уход со страницы во время загрузки приводил к интервалу на размонтированном инстансе (опрос каждые 7 с до закрытия вкладки), а `pollTimer` на уровне модуля перезаписывался следующим монтажом — утекший интервал становилось нечем снять | ручка таймера — **на инстансе** (`data().pollTimer`), интервал создаётся **синхронно** (до `await`), колбэк проверяет `isUnmounted`, `beforeUnmount` снимает таймер | поведенческая (2) + контрактная (2) |
| BR-362 | `views/StemJobsView.vue:322`, `:392-395` | RACE | удаление карточки — только в локальном массиве, а `load(true)` заменял `jobs` ответом сервера целиком: удалённая карточка возвращалась (с `deleteRequested=true` — «Удаляется...», кнопка disabled) до фонового cleanup (раз в 5 мин) | `visibleJobs(body)` (`src/utils/stemJobs.js`) отфильтровывает `deleteRequested === true` при каждой загрузке списка | поведенческая (3) + контрактная (1) |

---

## «До / после»: команда и вывод

Проверка «до» — регрессионные тесты на **старом** коде: изменённые исходники откачены
через `git stash push -- <файлы>` (новые тесты и новые чистые модули `src/utils/`
остаются), после чего тесты прогнаны.

### Контрактные проверки (падают на старом коде, компилируются на нём)

```
$ git stash push -- <13 изменённых исходников>
$ cd karaoke-public && npm run test:unit
✖ BR-347: контракт исходников (падает на старом коде)
✖ BR-348: контракт исходников (падает на старом коде)
✖ BR-349: контракт исходников (падает на старом коде)
✖ BR-351: таймер опроса статуса оплаты
✖ BR-352: отмена автопродления подтверждается сервером
✖ BR-353/BR-355/BR-357: контракт исходников (падает на старом коде)
✖ BR-354: авторские поля доезжают до страницы автора
✖ BR-356: контракт исходников (падает на старом коде)
✖ BR-358/BR-359: контракт исходников (падает на старом коде)
✖ BR-360: признак SKIP-песни
✖ BR-361/BR-362: контракт исходников (падает на старом коде)
ℹ tests 343
ℹ pass 324
ℹ fail 19          ← 19 упавших утверждений по 11 группам BR
$ git stash pop
```

Падения — `AssertionError` (а не «модуль не найден»): контрактные тесты читают
**текст исходника** (`src/utils/__tests__/sourceContract.js`).

### После правки

```
$ cd karaoke-public && npm run test:unit
ℹ tests 343
ℹ pass 343
ℹ fail 0
```

Было 279 тестов (после #399) → стало 343 (+64).

### Показательные «было → стало»

**BR-347 (PERF, поиск).** Было:

```js
const tile = authorTiles.value.find((t) => t && t.author === name)   // O(каталог) × 2 на строку
```

и в шаблоне — два вызова на строку (`v-if="authorIdFor(song.author)"` +
`params: { authorId: authorIdFor(song.author) }`). Стало:

```js
const authorIndex = computed(() => buildAuthorIndex(authorTiles.value))   // Map, пересборка при смене тайлов
searchRows() { ... authorIdFromIndex(index, song.author) ... }             // один резолв на строку, O(1)
```

**BR-348 (LEAK, плеер).** Было:

```js
setTimeout(() => { const arr = pendingToken[songId]; if (arr) { delete pendingToken[songId]; arr.forEach((r) => r(null)) } }, 8000)
```

— хэндл не сохранялся, `beforeUnmount` о таймерах не знал. Стало:

```js
tokenTimers[songId] = setTimeout(...)                 // хэндл сохранён
onBeforeUnmount(() => { destroyed = true; clearTokenTimers(tokenTimers, pendingToken); player?.destroy() })
```

**BR-352 (ERR, подписки).** Было:

```js
await authPost('/api/public/account/subscription/cancel', { id }, this.token)
await this.load()          // статус ответа не проверялся — «отмена прошла» даже при 500
```

Стало: `status !== 200` → видимый текст «Не удалось отключить автопродление» и **без**
перезагрузки списка; успех → подтверждение и перезагрузка; сетевой сбой — отдельный текст.

**BR-355 (ERR, «Повторить»).** Было:

```js
retryLoadZakroma() { this.onAuthorSelect(this.selectedAuthor) }   // → router.push('/zakroma/{id}/albums')
```

Стало: `this.loadZakromaStream(zakromaStreamParams(this.selectedTile, this.selectedAlbumId))` —
тот же запрос (автор, `expectedCount`, `albumId`, `force: true`), без навигации.

**BR-360 (COMPAT, SKIP).** Было:

```js
this.currentSong.tags && this.currentSong.tags.toUpperCase().includes('SKIP')
```

— поля `tags` в `SongPublicDto` нет, геттер всегда `false`. Стало:
`!!(this.currentSong && this.currentSong.contentRemoved)` — серверный флаг (точное
сравнение токена).

---

## Проверки

| Проверка | Команда | Результат |
|---|---|---|
| ESLint (karaoke-public) | `cd karaoke-public && npm run lint` | PASS (0 ошибок) |
| Prettier (karaoke-public) | `cd karaoke-public && npx prettier --check "src/**/*.{js,vue}"` | PASS («All matched files use Prettier code style!») |
| Unit-тесты фронта | `cd karaoke-public && npm run test:unit` | PASS — 343/343 (было 279) |
| Бэкенд: тесты + ktlint | `env -u DISPLAY GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true :karaoke-app:test :karaoke-web:test ktlintCheck --rerun-tasks` | PASS — BUILD SUCCESSFUL (29 задач выполнено; новый `ZakromaStreamMessageMetaTest` — 5/5) |
| Guard: deploy hardening | `bash tools/check-deploy-hardening.sh` | PASS (96/96) |
| Guard: security headers | `python3 tools/check-security-headers.py` | PASS (3/3 конфига) |
| Guard: file permissions | `bash tools/check-file-permissions.sh` | PASS (baseline: 99) |
| Guard: actions pinned | `bash tools/check-actions-pinned.sh` | PASS |
| Guard: secret in argv | `bash tools/check-secret-in-argv.sh` | PASS (83 файла) |
| Guard: recordhash | `python3 tools/check-recordhash-consistency.py` | PASS |
| Guard: no mp4 mentions | `bash tools/check-no-mp4-mentions.sh` | PASS (139 в baseline) |
| Guard: value placeholders | `bash tools/check-value-placeholders.sh` | PASS |
| Guard: root junk | `bash tools/check-root-junk.sh` | PASS |
| Guard: legacy tbl settings | `bash tools/check-no-legacy-tbl-settings.sh` | PASS (1767 файлов) |
| Guard: структура knowledge | `bash tools/check-knowledge-structure.sh` | PASS (9/9) |
| Guard: стиль knowledge | `python3 tools/lint-knowledge.py` | PASS |
| Guard: cross-links knowledge | `bash tools/check-knowledge-cross-links.sh` | PASS (714 ссылок) |
| Guard: FR-009 per-feature | `bash tools/check-feature-doc.sh docs/features/pkg-s3-28.md` | PASS |
| Guard: SSoT impact | `python3 tools/check-ssot-impact.py` | PASS |

---

## SSoT и документация

- `knowledge/system/frontend/karaoke-public-audit-invariants.md` — добавлены
  разделы **9–18** (поколение стрима; ресурсы после ухода со страницы; доверие к
  postMessage; состояние из маршрута; «Повторить» повторяет операцию; авторские
  поля в NDJSON; lease перед использованием; разбор кодов ошибок; флаг из
  существующего поля DTO; каталог — в индекс) и таблица новых модулей
  `src/utils/`.
- `docs/features/pkg-s3-28.md` — per-feature документ (FR-009) с разделом
  **«Известные ловушки»** (12 пунктов).

---

## За владельцем

1. **Заголовки защиты кадров на публичном nginx.** BR-349 закрыт на стороне
   клиента (проверка origin + источника postMessage). Усиление на уровне nginx
   (`Content-Security-Policy: frame-ancestors 'self'`) — **за владельцем**: плеер
   встраивается iframe'ом на странице редактора плейлиста, и запрет фрейминга
   может сломать встраивание в другие сервисы. Файл `karaoke-public/nginx_karaoke-public.conf`
   в этом пакете не трогался (деплой — вне scope).
2. **Поле `authorSkip` в REST-версии.** В этом пакете NDJSON-стрим стал строже
   (`authorSkip` отдаётся только пользователям с правом видеть SKIP-контент), а
   REST-версия (`ZakromaPublicDto`) отдаёт флаг всем. Синхронизация поведения —
   **за владельцем** (изменение REST-контракта затрагивает и другие клиенты).
3. **`SongPublicDto.skipped` (спека 293).** BR-360 закрыт через уже приходящий
   `contentRemoved` — без нового поля в DTO. Если владелец хочет отдельный флаг
   `skipped` (как записано в `specs/293-skip-author-toggle/spec.md`), это отдельная
   правка бэкенда.
4. **Поллинг выдачи поиска и `authorTiles`.** BR-347 убрал O(строк × каталог) из
   рендера; сам каталог (`/authors-tiles`) по-прежнему грузится целиком на каждый
   вход в «Закромы» (кеш на 30 с в сторе). Оптимизация серверной пагинации каталога —
   **за владельцем** (вне S3-периметра).
5. **Запись `P-48-05` / `P-49-03` (смежные).** Вне пакета `PKG-S3-28` (нет в
   `registry.csv` с `work_package = 400`): `SubscriptionsView.load()` по-прежнему
   превращает ошибку загрузки в «Подписок пока нет» (BR-322), а `StemJobsView.load()`
   — в «Заданий пока нет». Не дублировались.

---

## Файлы пакета

Новые (10):

- `karaoke-public/src/utils/authorIndex.js`
- `karaoke-public/src/utils/messageOrigin.js`
- `karaoke-public/src/utils/tokenTimers.js`
- `karaoke-public/src/utils/streamGeneration.js`
- `karaoke-public/src/utils/shareClaim.js`
- `karaoke-public/src/utils/zakromaRoute.js`
- `karaoke-public/src/utils/stemJobs.js`
- `karaoke-public/src/utils/__tests__/{authorIndex,messageOrigin,tokenTimers,streamGeneration,shareClaim,zakromaRoute,stemJobs,subscriptionViews,zakromaAuthorMeta}.test.js`
- `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/dto/ZakromaStreamMessageMetaTest.kt`

Изменённые (13 + 2 документа):

- `karaoke-public/src/views/{SearchView,PlayerView,SubscriptionReturnView,SubscriptionsView,ZakromaView,ShareView,SongView,StemJobsView}.vue`
- `karaoke-public/src/store/modules/zakroma.js`
- `karaoke-public/src/composables/useZakromaStreamProgress.js`
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt`
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/ZakromaStreamMessageDto.kt`
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt`
- `knowledge/system/frontend/karaoke-public-audit-invariants.md`
- `docs/features/pkg-s3-28.md` (новый)
- `specs/400-pkg-s3-28/report.md` (этот файл, новый)
