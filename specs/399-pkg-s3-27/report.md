# PKG-S3-27 (OpenProject #399) — karaoke-public: COMPAT, DATA, ERR, LEAK, PERF, RACE, SEC — 40 BR

- **Аудит**: #336, волна E, пакет `PKG-S3-27` (`specs/336-code-audit/backlog.md`, блок `### PKG-S3-27`).
- **Ветка**: `628-pkg-s3-27` от `origin/master` `033ebf96` (merge PR #724, реестр волны E).
- **Коммит пакета**: один коммит в ветке `628-pkg-s3-27` с сообщением
  `fix(336): BR-296..BR-346 — 40 записей аудита #336 в karaoke-public (PKG-S3-27)`.
  Точный SHA фиксируется в трекере (комментарий #399) и в финальном отчёте: он
  меняется при amend, поэтому в документе не зафиксирован. Push/PR не делались
  (по условию задачи).
- **Worktree**: `/home/nsa/Karaoke/.worktrees/waveE-s3-27`.
- **Источник правды**: карточки `specs/336-code-audit/bug-report.md` (P-42, P-43, P-44, P-45,
  P-46, P-47); порции определены по `registry.csv` (`work_package = 399`).
- **Что НЕ делалось**: миграции не применялись ни к LOCAL, ни к PROD; контейнеры, прод, БД и
  `specs/336-code-audit/registry.csv` не трогались; деплоя не было; push/PR не делались.
- **Режим**: пакет собран одним проходом (40 записей), регрессия — двумя уровнями тестов.

## Сводка

| Блок | BR | Статус |
|---|---|---|
| Компоненты: копирование, корзина, чат, share-ссылка | BR-296…BR-301 (6) | исправлены |
| Плеер `KaraokePlayer.js` | BR-304…BR-311 (8) | исправлены |
| Сессия, навигация, store | BR-314…BR-323 (10) | исправлены |
| Просмотры (чат, автор, «О проекте») | BR-324…BR-326 (3) | исправлены |
| Редактор заданий `EditorWorkView.vue` | BR-331…BR-338 (8) | исправлены |
| Плейлисты, новости, поиск, премиум | BR-341…BR-346 (5) | исправлены |

**Все 40 записей пакета закрыты.** Ни одна запись не оказалась «уже исправлена другим
пакетом»: фактическое состояние кода проверено по каждой строке реестра до правки
(см. «Проверка фактического состояния»). Реестр из `origin/master`
(`99135a8a`, «реестр волны E») проверен отдельно — все 40 BR остаются в `work_package = 399`.

---

## Проверка фактического состояния (до правки)

Для каждой из 40 записей прочитан актуальный код (номера строк в бэклоге — от снимка
аудита `1977838a`; ниже — строки текущей ветки до правки). Ни одна запись не была помечена
«исправлено ранее»: все 40 дефектов присутствовали в коде.

| BR | Где смотрел | Что было в коде на начало пакета |
|---|---|---|
| BR-296 | `components/ShareButton.vue:77-100` | `this.copied = true` безусловно; `execCommand('copy')` без проверки результата |
| BR-297 | `components/CartIcon.vue:38-51` | `await toggle(props.songId)` в `try/finally` без `catch` и без разбора `status`/`body` |
| BR-298 | `components/ChatUnreadBadge.vue:53-59` | `watch` только на `isLoggedIn`; `isPremium` из `user.effectivePremium` не наблюдался |
| BR-299 | `components/ShareLinkModal.vue:107-137` | capability-URL в `localStorage` под `karaoke-share-url:<linkId>`, без очистки |
| BR-300 | `components/SongSubscriptionModal.vue:132-140` + `composables/useSongSubscription.js:15-33` | один `priceInfo` на все песни, без идентификатора запроса |
| BR-301 | `components/ShareLinkModal.vue:27-39` | блок `created` со статическим `shareUrl`; polling правил только `currentLink` |
| BR-304 | `player/KaraokePlayer.js:290-300` | `catch` писал `e.message` в `#kp-loading` через общий `querySelector` |
| BR-305 | `player/KaraokePlayer.js:1653-1660` | `_pause()` сбрасывал `_isPrerolling` — время прыгало на `_preroll` |
| BR-306 | `player/KaraokePlayer.js:2128-2130` | `setTimeout(() => trackPlayerSeek(this.songId, …))` — `songId` читался в колбэке |
| BR-307 | `player/KaraokePlayer.js:1516-1545` | `.then` динамического `import('wavesurfer.js')` создавал инстансы без проверки поколения |
| BR-308 | `player/KaraokePlayer.js:1785-1815` | `while (written < outLength)` без единого `await` — блокировка главного потока |
| BR-309 | `player/KaraokePlayer.js:2249-2500` | `this._bgCanvas = this._generateStarfield()` на каждый `init()` (64 МиБ) |
| BR-310 | `player/KaraokePlayer.js:1905-1912` | `localStorage.getItem` без `try/catch` в `_restoreTranspose()` |
| BR-311 | `player/KaraokePlayer.js:2229-2235` | `new OffscreenCanvas(...)`, `ctx.roundRect(...)` без feature-detection; кадр без `try/catch` |
| BR-314 | `composables/useCart.js:13-19` | для анонима `loaded = true`; флаг не привязан к токену |
| BR-315 | `composables/useSiteSubscription.js:32-42` | `loadPrice()` без идентификатора запроса |
| BR-316 | `router/index.js:172-174` | `await store.dispatch('zakroma/loadAuthorTiles')` без `try/catch` |
| BR-317 | `composables/useNewsUnreadCount.js:120-124, 193-197` | `isAuthenticated()` только при создании интервала |
| BR-318 | `composables/usePlaylistMembership.js:132-144` | `fetchPlaylists()` без `try/catch` (единственная из трёх bulk-загрузок) |
| BR-320 | `store/modules/zakroma.js:225` | `composable.cleanup()` не вызывался; отмены потока не было |
| BR-321 | `store/modules/songs.js:127-140` | независимые `latestSearchId` / `latestLoadMoreId` |
| BR-322 | `views/HistoryView.vue:63-68` | ни `catch`, ни ветки на `status !== 200` |
| BR-323 | `store/modules/stats.js:48-56` | `catch` отсутствовал; нули рисовались как данные |
| BR-324 | `views/ChatView.vue:173-186` | `if (!this.messages.length) return` + `fetchMessages` без `catch` |
| BR-325 | `views/AuthorPlaylistView.vue:202-214` | `statusOf()` — линейный `find` по массиву, 4 вызова на строку |
| BR-326 | `views/AboutView.vue:162-166` | «449 ₽/мес» и «49 ₽» зашиты в текст страницы |
| BR-331 | `views/EditorWorkView.vue:1160-1161` | `saveNow()` ничего не возвращал; `submit()` шёл независимо |
| BR-332 | `views/EditorWorkView.vue:1087-1089` | `if (body && body.status) this.status = body.status` |
| BR-333 | `views/EditorWorkView.vue:653-670` | листенеры `window` навешивались ПОСЛЕ двух `await` |
| BR-334 | `views/EditorWorkView.vue:695-702` | `fetchTask` без `try/catch`; `loading` оставался `true` |
| BR-335 | `views/EditorWorkView.vue:789-791` | `currentTime` обновлялся каждый `timeupdate` (60/с) |
| BR-336 | `views/EditorWorkView.vue:928-941` | `activeSound` выставлялся ДО `await this.ws.load(url)`; reject не ловился |
| BR-337 | `views/EditorWorkView.vue:1313-1329` | `blur`/`visibilitychange` не слушались; `keyup` мог не прийти |
| BR-338 | `views/EditorWorkView.vue:713-725` | `relabelSyllables(markers, [])` при пустом тексте обнулял `label` |
| BR-341 | `views/PlaylistEditView.vue:367-371` | любой не-200 → `notFound = true` («Плейлист не найден») |
| BR-342 | `views/PlaylistEditView.vue:537-562` | fire-and-forget мутации: `.then` без `catch`, без ветки «не 200» |
| BR-343 | `views/NewsView.vue:70-77` | пустой `catch` → «Пока новостей нет» при 500/503 |
| BR-344 | `views/SearchView.vue:376-384` | `watch` с `immediate: false`; `onSearch()` грузил только страницу 1 |
| BR-345 | `views/SearchView.vue:202-205` | один флаг `searchPaginationError` на поиск и подгрузку |
| BR-346 | `views/PremiumView.vue:93-95` | ошибка расчёта цены не показывалась (шаблон — только при `priceInfo`) |

---

## По записям

| BR | Файл:строка (было) | Класс | Что не так | Как правильно | Проверка |
|---|---|---|---|---|---|
| BR-296 | `components/ShareButton.vue:77-100`, `components/ShareLinkModal.vue:262-292` | ERR | `document.execCommand('copy')` возвращает `false` или бросает, а `copied = true` выставлялся **безусловно** — UI писал «Ссылка скопирована» при пустом буфере | `copyTextToClipboard()` (`src/utils/clipboard.js`) возвращает `true` только при реальном успехе; `copied` — по результату, иначе «Скопируйте вручную» | поведенческая (7) + контрактная (3) |
| BR-297 | `components/CartIcon.vue:38-51` | ERR | результат `toggle()` не анализировался: 409 `already_subscribed`, 400 `song_not_for_subscription`, 401, 5xx — иконка молча не менялась | `classifyCartToggle()` (`src/utils/cartToggle.js`) → сообщение + скрытие иконки при `already_subscribed`; `catch` на сетевой reject | поведенческая (6) + контрактная (2) |
| BR-298 | `components/ChatUnreadBadge.vue:53-59` | ERR | `watch` только на `isLoggedIn`; премиум меняется из `user.effectivePremium` (фоновый `fetchMe`) — бейдж не появлялся, а polling не останавливался | `watch` на `isPremium` + `chatPollDecision()` (`src/utils/statusMap.js`); `poll()` в `try/catch` | поведенческая (2) + контрактная (2) |
| BR-299 | `components/ShareLinkModal.vue:107-137` | SEC | bearer-capability URL (`/share/{linkId}/{secret}`) лежал в `localStorage` и не удалялся при выходе/истечении — на общем устройстве оставался действующий доступ прежнего владельца | `sessionStorage` (`resolveSecretStorage`) + очистка префикса `karaoke-share-url:` в `useAuth.clearSession`/`setSession` (`src/utils/shareSecret.js`) | поведенческая (8) + контрактная (3) |
| BR-300 | `composables/useSongSubscription.js:15-33`, `components/SongSubscriptionModal.vue:132-140` | RACE | один `priceInfo` на все песни; ответ предыдущей песни применялся к текущей (цена чужой песни при оплате текущей) | `createRequestGuard()` — ответ применяется только к последнему запросу; смена `songId` сбрасывает `priceInfo`/`loadingPrice` | поведенческая (6) + контрактная (2) |
| BR-301 | `components/ShareLinkModal.vue:27-39` | ERR | polling обновлял только `currentLink`, а блок `created` рендерил статический `shareUrl` — после перевыпуска/отзыва показывался мёртвый URL как активный | `reconcileCreatedLink()` сверяет `createdLinkId` с ответом сервера; при расхождении `created` сбрасывается | поведенческая (6) + контрактная (2) |
| BR-304 | `player/KaraokePlayer.js:290-300` | ERR | `catch` не различал отмену и устаревшую загрузку и писал ошибку в `#kp-loading` **следующего** трека («The user aborted a request» поверх видео) | `classifyInitError()` (`player/playerGuards.js`): `AbortError`/`signal.aborted`/stale → выход без сообщения; успешный `init()` скрывает `#kp-loading` | поведенческая (5) + контрактная (2) |
| BR-305 | `player/KaraokePlayer.js:1653-1660` | ERR | пауза в pre-roll снимала `_isPrerolling`; `_getDisplayTime()` возвращал `_preroll + pausedAt` (5.0 с) — время прыгало, splash и silent offset пропускались | отдельный флаг `_prerollPaused`: остаёмся в pre-roll, но стоп-часы заморожены; `prerollDisplayTime()` считает позицию | поведенческая (3) + контрактная (2) |
| BR-306 | `player/KaraokePlayer.js:2128-2130` | DATA | `songId` читался в колбэке таймера — при переходе на следующий трек за 400 мс seek уходил с ID другой песни | `createSeekTracker()` фиксирует `songId` в момент перемотки; таймер снимается в `playSong()` и `destroy()` | поведенческая (3) + контрактная (2) |
| BR-307 | `player/KaraokePlayer.js:1516-1545` | LEAK | `.then` динамического импорта создавал WaveSurfer даже после `destroy()`/`playSong()` — дубли canvas и удержанные инстансы | поколение загрузки (`isStaleGeneration`): устаревшее поколение не создаёт инстансы (или уничтожает их сразу); `destroy()` чистит контейнеры вейвформ | поведенческая (2) + контрактная (1) |
| BR-308 | `player/KaraokePlayer.js:1785-1815` | PERF | транспонирование крутило SoundTouch по всем сэмплам без `await` — UI замирал на секунды, риск OOM на мобильных | `_transposeBuffer` стал async, обработка идёт чанками с уступкой главного потока (`transposeYieldDue`) | поведенческая (3) + контрактная (1) |
| BR-309 | `player/KaraokePlayer.js:2249-2500` | PERF | текстура 4096×4096 (≈64 МиБ) генерировалась на **каждый** `init()` (каждый трек плейлиста) | `shouldGenerateStarfield()` — текстура генерируется один раз на инстанс | поведенческая (2) + контрактная (1) |
| BR-310 | `player/KaraokePlayer.js:1905-1912` | ERR | `localStorage.getItem` без `try/catch` — в iframe без `allow-same-origin` плеер навсегда оставался в `_ready = false` | `readStoredNumber()` (`player/playerGuards.js`) — безопасное чтение с fallback `null` | поведенческая (5) + контрактная (1) |
| BR-311 | `player/KaraokePlayer.js:2229-2235` | COMPAT | `new OffscreenCanvas` и `ctx.roundRect` без feature-detection (Safari < 16.4 / Firefox < 112) — падение `init()` или остановка рендер-цикла | `createBackgroundCanvas()` (fallback на `<canvas>`), `roundRectPath()` (fallback на `arcTo`), тело кадра в `try/catch` | поведенческая (7) + контрактная (2) |
| BR-314 | `composables/useCart.js:13-19` | RACE | для анонима `loaded = true`; флаг не привязан к токену — после входа корзина не грузилась, после выхода оставались позиции прежнего пользователя | `needsCartLoad()` + `loadedForToken`; для анонима `loaded = false`; `reset()` вызывается из `useAuthBootstrap.onLogout()` | поведенческая (4) + контрактная (3) |
| BR-315 | `composables/useSiteSubscription.js:32-42` | RACE | ответ на первый тариф мог прийти последним — в UI цена первого тарифа при выбранном втором | `createRequestGuard()` в composable; устаревший ответ не трогает `priceInfo`/`error` | поведенческая (6, общий с BR-300/321) + контрактная (1) |
| BR-316 | `router/index.js:172-174` | ERR | отклонённый guard отменял навигацию — при прямой ссылке `router-view` пустой (белый экран), при клике переход «не срабатывал» | `try/catch` + `routeGuardOutcome()`: ошибка предзагрузки не отменяет навигацию, страница показывает своё сообщение | поведенческая (2) + контрактная (1) |
| BR-317 | `composables/useNewsUnreadCount.js:120-124, 193-197` | LEAK | polling новостей продолжал тикать каждые 45 с после выхода из аккаунта (запрос без токена) | `shouldPollNews()` в `applyRouteState` + `watch` на токен (вход возобновляет polling) | поведенческая (3) + контрактная (2) |
| BR-318 | `composables/usePlaylistMembership.js:132-144` | ERR | `fetchPlaylists()` без `try/catch` — reject ронял `Promise.all` при логине и `openMenu` до `open.value = true` (меню не открывалось) | `try/catch`, текущий список не меняется, наружу возвращается признак ошибки; `openMenu` открывает меню всегда | контрактная (1) + покрыта классификацией из BR-322 |
| BR-320 | `store/modules/zakroma.js:225` | LEAK | `cleanup()`/`cancel()` не вызывались; кнопка «Отмена» перезапускала action → ВТОРОЙ NDJSON-стрим поверх живого первого (двойная нагрузка на hot-path эндпоинт) | store держит активный composable и отдаёт `cancelZakromaStream`; `releaseStream()` в `finally` и перед стартом нового; страница вызывает экшен отмены и прерывает стрим в `beforeUnmount` | поведенческая (4) + контрактная (2) |
| BR-321 | `store/modules/songs.js:127-140` | RACE | независимые счётчики `search`/`loadMore` — страница 2 старого запроса дописывалась к новой выдаче, `searchPagination` перезаписывался старыми значениями | один счётчик поколения выдачи (`searchGuard`); `search` синхронно сбрасывает пагинацию; `loadMore` запоминает поколение на старте | поведенческая (6, общий с BR-300/315) + контрактная (1) |
| BR-322 | `views/HistoryView.vue:63-68` | ERR | ни `catch`, ни ветки на `status !== 200` — 5xx показывался как «Вы пока ничего не слушали» | `classifyLoad()` + `try/catch/finally` + явный `error` с кнопкой «Повторить» | контрактная (1) + классификация (6, общий с BR-323/334/341/343/346) |
| BR-323 | `store/modules/stats.js:48-56` | ERR | `catch` отсутствовал — на главной рисовались нули как реальные данные («0 Песен в коллекции») | состояния `loaded`/`error` в модуле; `HomeView` показывает «Статистика недоступна» + «Повторить» | контрактная (2) + классификация (общая) |
| BR-324 | `views/ChatView.vue:173-186` | ERR | ранний `return` по пустому списку — первое сообщение автора не появлялось до перезагрузки; `fetchMessages` без `catch` → unhandled rejection каждые 7 с | пустое окно запрашивает свежую страницу (без `afterId`); `try/catch` + видимое состояние `pollError` | контрактная (1) + поведенческая (общая классификация) |
| BR-325 | `views/AuthorPlaylistView.vue:202-214` | PERF | `statusOf()` — линейный `find`, 4 вызова на строку + `hasPlayable`/`playableIds` → O(n²) на каждый рендер (n ≈ 2500) | `buildSongStatusMap()` — `Map<songId, status>` за O(n), чтение O(1); `playableSongIds()` из того же прохода | поведенческая (7) + контрактная (2) |
| BR-326 | `views/AboutView.vue:162-166` | COMPAT | «449 ₽/мес» и «49 ₽» зашиты в текст — после правки тарифа админом страница показывала старую цену | тарифы берутся из публичного API (`scope=SITE`/`scope=SONG`), текст собирается через `priceTexts()`; если тарифов нет — цены не показываются | поведенческая (5) + контрактная (1) |
| BR-331 | `views/EditorWorkView.vue:1160-1161` | DATA | `saveNow()` ничего не возвращал — `submit()` отправлял задание со **старым** черновиком | `saveNow()` возвращает `true`/`false`; `submit()` прерывается с сообщением (`isSaveSuccessful`) — fail-closed | поведенческая (3) + контрактная (2) |
| BR-332 | `views/EditorWorkView.vue:1087-1089` | COMPAT | `body.status` из ответа save (всегда `in_progress`) затирал композитный `rejected` — баннер «Возвращено на доработку» исчезал после автосейва | `mergeSaveStatus()`: `rejected`/`submitted` не затираются ответом save | поведенческая (4) + контрактная (1) |
| BR-333 | `views/EditorWorkView.vue:653-670` | LEAK | листенеры `window` навешивались **после** двух `await` — при уходе со страницы они оставались на `window` до конца вкладки | листенеры вешаются **до** асинхронной загрузки; флаг `_unmounted` проверяется после каждого `await`; `beforeUnmount` снимает всё | контрактная (2) |
| BR-334 | `views/EditorWorkView.vue:695-702` | ERR | `fetchTask` без `catch` — `loading` оставался `true` навсегда («Загрузка задания…») | `try/catch/finally` вокруг `fetchTask`, `loadError` + кнопка «Повторить» | контрактная (1) |
| BR-335 | `views/EditorWorkView.vue:789-791` | PERF | `timeupdate` (60/с) перерисовывал компонент целиком; превью разметки пересобиралось на каждом тике | rAF-троттлинг `currentTime`; `previewMarkerIndex` обновляется только при смене индекса маркера (`shouldRefreshPreview`) | поведенческая (2) + контрактная (2) |
| BR-336 | `views/EditorWorkView.vue:928-941` | ERR | `activeSound` выставлялся до загрузки; reject от `ws.load()` не ловился — повторный клик по той же кнопке ничего не делал | `soundSwitchOutcome()`: стем фиксируется **после** успешной загрузки, reject → сообщение и возможность повтора | поведенческая (3) + контрактная (2) |
| BR-337 | `views/EditorWorkView.vue:1313-1329` | RACE | интервалы перемотки останавливались только по `keyup` — при Alt-Tab `keyup` не приходит, интервал крутился бесконечно | `blur`/`visibilitychange` → `releaseAllScrubTimers()` + восстановление скорости и паузы | поведенческая (2) + контрактная (1) |
| BR-338 | `views/EditorWorkView.vue:713-725` | DATA | у голоса без текста `relabelSyllables(markers, [])` обнулял `label` всех слоговых маркеров; автосейв закреплял пустые метки → после апрува текст голоса терялся | `voiceLabelPlan()`: без текста `relabelSyllables` не вызывается; `trimTrailingEmptyVoices()` не сохраняет «синтезированные» пустые голоса; предупреждение о расхождении длин | поведенческая (7) + контрактная (2) |
| BR-341 | `views/PlaylistEditView.vue:367-371` | ERR | 401/403/5xx трактовались как «Плейлист не найден» | `classifyLoad()` с `notFoundStatuses: [404]`: 401/403 → «войдите заново», 5xx → «не удалось загрузить, повторить», `body.error` логируется в тексте | контрактная (1) + классификация (общая) |
| BR-342 | `views/PlaylistEditView.vue:537-562` | ERR | мутации fire-and-forget: `.then` без `catch` и без ветки «не 200»; состояние менялось оптимистично — UI показывал неприменённое изменение | `await` + проверка `status`, откат оптимистичного изменения, сообщение (`notifyMutationFailed`); `pushQueue` — только после подтверждения | контрактная (1, общий с BR-341) |
| BR-343 | `views/NewsView.vue:70-77` | ERR | пустой `catch` превращал 500/503 в «Пока новостей нет»; `markAllSeen()` при ошибке не сбрасывал бейдж | отдельный флаг `loadError` + «Повторить»; `markAllSeen()` только после успешной загрузки | контрактная (1) |
| BR-344 | `views/SearchView.vue:376-384` | ERR | `watch` с `immediate: false` + `onSearch()` грузил только страницу 1 — при F5 на `?page=3` URL врал о позиции; `restoreFromUrl()` для `page <= 1` ничего не сбрасывал | `onSearch()` при `page > 1` вызывает `restoreFromUrl()` (страницы 1..N); `restorePlan()` сбрасывает срез при `page <= 1` | поведенческая (4) + контрактная (2) |
| BR-345 | `views/SearchView.vue:202-205` | ERR | один флаг на поиск и подгрузку: ошибка поиска показывалась как «Не удалось загрузить ещё», а retry упирался в guard `hasMore=false` и не запрашивал ничего → ложное «Ничего не найдено» | отдельные флаги `searchError` (первичный) и `searchPagination.error` (подгрузка); `retrySearch()` повторяет именно поиск; при ошибке прежние результаты очищаются | поведенческая (3) + контрактная (3) |
| BR-346 | `views/PremiumView.vue:93-95` | ERR | ошибка расчёта цены не показывалась (шаблон — только при `priceInfo`) — checkout молча исчезал: нет цены, кнопки, сообщения и повтора | ветка `v-else-if="priceError"` с сообщением и `retryPrice()` (`loadPrice(selectedTariffId)`); `submitError` остался только для ошибок оформления | контрактная (1) |

---

## «До / после»: команда и вывод

Проверка «до» — регрессионные тесты на **старом** коде: изменённые исходники откачены
через `git stash push -- <файлы>` (новые тесты и новые модули `src/utils/` остаются),
после чего тесты прогнаны.

### Контрактные проверки (падают на старом коде, компилируются на нём)

Контрактные тесты читают **текст исходников** (`karaoke-public/src/utils/__tests__/sourceContract.js`),
поэтому на старом коде они не «не находят модуль», а дают обычный `AssertionError` —
это и есть настоящее доказательство «до/после».

```bash
cd /home/nsa/Karaoke/.worktrees/waveE-s3-27
git stash push -m "s3-27-before-check" -- $(git status --porcelain | awk '$1=="M"{print $2}')
cd karaoke-public && npm run test:unit
```

**До (старый код):**

```
ℹ tests 279
ℹ pass 234
ℹ fail 45
```

(45 упавших тестов — 57 уникальных имён, включая все 16 контрактных наборов:
`BR-296: контракт исходников`, `BR-299 / BR-301: …`, `BR-304…BR-311: …`,
`BR-331…BR-338: …` и т.д.)

**После (код с правками):**

```
ℹ tests 279
ℹ suites 69
ℹ pass 279
ℹ fail 0
```

### Поведенческие проверки (чистые функции)

Те же тесты проверяют сами функции: `copyTextToClipboard` возвращает `false`, когда
`execCommand('copy')` вернул `false`; `reconcileCreatedLink` сбрасывает `created` при
перевыпуске ссылки; `classifyInitError` игнорирует `AbortError`; `createSeekTracker`
фиксирует `songId` в момент перемотки; `voiceLabelPlan` запрещает переразметку голоса
без текста.

### Показательные «было → стало»

**1. BR-296 — копирование в буфер (`components/ShareButton.vue:77-100`)**

Было:
```js
try { await navigator.clipboard.writeText(window.location.href) }
catch (e) { /* … */ document.execCommand('copy') }   // результат игнорируется
this.copied = true                                   // UI: «Ссылка скопирована»
```
Стало:
```js
const ok = await copyTextToClipboard(window.location.href)
if (!ok) { this.copied = false; this.copyFailed = true; return }  // «Скопируйте вручную»
this.copied = true
```

**2. BR-321 — гонка выдачи поиска (`store/modules/songs.js:127-140`)**

Было: `latestSearchId` и `latestLoadMoreId` — два независимых счётчика; ответ старой
подгрузки дописывался к новой выдаче.
Стало: один `searchGuard` на выдачу — `search` открывает поколение, `loadMore` запоминает
его на старте и отбрасывает ответ при смене.

**3. BR-299 — секрет capability-ссылки (`components/ShareLinkModal.vue:107-137`)**

Было: `localStorage.setItem('karaoke-share-url:' + linkId, url)` — секрет переживал выход
из аккаунта на общем устройстве.
Стало: `sessionStorage` + `clearAllShareUrls()` в `useAuth.clearSession`/`setSession`.

**4. BR-338 — потеря текста голоса (`views/EditorWorkView.vue:713-725`)**

Было: `relabelSyllables(markers, splitSyllables(''))` → `label` всех слоговых маркеров
обнулялся, автосейв закреплял пустые метки, после апрува текст голоса исчезал.
Стало: `voiceLabelPlan()` — без текста `relabelSyllables` не вызывается; плюс явное
предупреждение о расхождении длин.

**5. BR-320 — «Отмена» стрима (`views/ZakromaView.vue:909-931`)**

Было: `cancelZakromaStream()` повторно диспатчил тот же action → на первом заходе
стартовал ВТОРОЙ NDJSON-стрим поверх живого первого.
Стало: `this.cancelZakromaStreamAction()` — store прерывает поток (`AbortController`),
сбрасывает состояние и метки дедупа.

---

## Побочные эффекты и совместимость

- `apiGet` (`services/api.js`) **не** принимает query-строку в пути — всегда собирает
  `?anonId=…&referrer=…`. Поэтому в `AboutView` тарифы запрашиваются как
  `apiGet('/api/public/account/subscription/tariffs', { scope: 'SITE' })`, а не
  `?scope=SITE` в пути (иначе получился бы `…?scope=SITE?anonId=…`).
- `useCart` получил `reset()`; `useAuthBootstrap.onLogout()` теперь вызывает три
  сброса (плейлисты, подписки, корзина) — порядок не важен, все синхронные.
- `songs/search` теперь очищает `searchResults` при ошибке — прежние результаты больше
  не выдаются за результат нового поиска.
- `PremiumView.priceError` использует `error` из composable: ошибки **расчёта цены**
  отделены от ошибок **оформления** (`submitError`).
- Текстура фона теперь одна на инстанс плеера: палитра звездного фона не меняется при
  переходе на следующий трек внутри одной сессии (осознанный компромисс — см. «Известные
  ловушки»).
- Публичный плеер дублируется в `webvue3` — правки инвариантов в обе копии не переносились.

---

## Решения за владельцем

1. **Вынос транспонирования в Web Worker/AudioWorklet (BR-308).** Сейчас блокировка
   главного потока снята чанковой уступкой, но оригиналы и транспонированные копии
   живут одновременно (нужны для повторной смены тональности). Полное решение требует
   пересборки обработки вне UI-потока.
2. **Вторая копия плеера в `webvue3`** (`webvue3/src/player/KaraokePlayer.js`) не
   обновлялась: BR-304…BR-311 закрыты только в публичной копии. Синхронизация — за
   владельцем (в `webvue3` есть свой пакет аудита).
3. **Палитра фона в пределах сессии плеера** — побочный эффект BR-309 (одна текстура на
   инстанс). Если нужна смена палитры на каждом треке — решение за владельцем.
4. **Очистка capability-URL в `localStorage` у ранее записанных пользователей.** Миграция
   данных в браузере не выполнялась (запись в БД/хранилища пакетом запрещена): старые
   ключи `karaoke-share-url:*` в `localStorage` останутся до ручной очистки браузера.
5. **Публичный API тарифов** (`GET /api/public/account/subscription/tariffs?scope=SITE|SONG`)
   используется теперь и на странице «О проекте»: если он закрыт для анонимов на
   проде, блок цен просто не покажется (без падения страницы) — проверить конфигурацию
   `WebMvcConfig.excludePathPatterns`.

---

## Проверки

| Команда | Результат |
|---|---|
| `cd karaoke-public && npm run lint` | OK (0 ошибок, 0 предупреждений) |
| `cd karaoke-public && npm run test:unit` | OK — 279/279 (было 106/106) |
| `bash tools/check-knowledge-structure.sh` | OK: knowledge/ structure valid (9/9 checks passed) |
| `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt` | OK: All documentation passes architectural style and structural checks |
| `python3 tools/check-doc-references.py` | OK: 0 проблем |
| `python3 tools/check-recordhash-consistency.py` | OK |
| `bash tools/check-no-mp4-mentions.sh` | OK |
| `bash tools/check-feature-doc.sh docs/features/*.md` | OK: все документы валидны |
| `bash tools/check-subagent-isolation.sh` | OK |
| `bash tools/check-file-permissions.sh` | OK |
| `python3 tools/check-ssot-impact.py` | OK (правил для `karaoke-public` в `.ssot-map.yml` нет) |
| `GRADLE_USER_HOME=… ./gradlew --rerun-tasks :karaoke-app:test :karaoke-web:test` | OK — 1034 теста (karaoke-app 852, karaoke-web 182), 0 падений |
| `GRADLE_USER_HOME=… ./gradlew ktlintCheck` | OK |

### Особенность локального прогона gradle (урок для следующих пакетов)

На этой машине переменная `DISPLAY=:0` (есть GUI-сессия), поэтому AWT в тестовом
JVM пытается поднять X11 и падает с
`NoClassDefFoundError: java.awt.GraphicsEnvironment$LocalGE` — 6 тестов
(`VkPreviewWarmupClientTest`, `PkgS326BehaviorTest`, `PkgS324Batch1BehaviorTest`).
Это **не связано с пакетом** (бэкенд пакетом не менялся) и в CI не воспроизводится:
в GitHub Actions `DISPLAY` нет, и AWT сам выбирает headless-режим.

Правильный локальный прогон:

```bash
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle env -u DISPLAY \
  ./gradlew --max-workers=2 --rerun-tasks \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m -Djava.awt.headless=true" \
  -Djava.awt.headless=true :karaoke-app:test :karaoke-web:test
```

Без `--rerun-tasks` Gradle рапортует `UP-TO-DATE`/`FROM-CACHE` по закешированным
классам и пропускает ошибку компиляции — это ровно тот случай, о котором
предупреждает постановка задачи.

### `node_modules` в worktree

В worktree нет `node_modules` (git его не хранит), поэтому для прогона фронта
создан симлинк на общий кеш главного checkout'а:

```bash
cd karaoke-public && ln -sfn /home/nsa/Karaoke/karaoke-public/node_modules node_modules
```

Каталог в `.gitignore`, в коммит не попадает и на файлы главного checkout'а
не влияет. Тот же приём, что и с `GRADLE_USER_HOME`.

---

## Артефакты пакета

| Артефакт | Путь |
|---|---|
| Отчёт | `specs/399-pkg-s3-27/report.md` |
| Per-feature документ (FR-009) | `docs/features/pkg-s3-27.md` |
| SSoT: инварианты публичного SPA | `knowledge/system/frontend/karaoke-public-audit-invariants.md` |
| SSoT: обновлённые документы | `knowledge/system/frontend/{karaoke-public-composables,karaoke-public-tests,composable-use-cart,composable-news-unread}.md`, `knowledge/system/README.md`, `knowledge/domains/README.md` |
| Чистые функции (новый слой) | `karaoke-public/src/utils/{clipboard,cartToggle,shareSecret,requestGuard,loadState,statusMap,editorGuards,routeGuard,streamLifecycle,pricing,searchPagination,notify}.js`, `karaoke-public/src/player/playerGuards.js` |
| Регресс-тесты | `karaoke-public/src/utils/__tests__/*.test.js` (11 + `sourceContract.js`), `karaoke-public/src/player/__tests__/playerGuards.test.js` |
