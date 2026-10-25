# Component: karaoke-public-audit-invariants (инварианты публичного SPA)

> **Домен**: system (cross-cutting)
> **Компонента**: инварианты `karaoke-public`, зафиксированные аудитом #336
> (волна E, пакеты `PKG-S3-27` — OpenProject #399, `PKG-S3-28` — OpenProject #400).

## Назначение

Публичный SPA (`karaoke-public`) — единственный фронт, который видит посетитель
сайта. Аудит #336 (волна E, `PKG-S3-27`) нашёл в нём 40 записей одного класса:
дефекты не «в логике», а в **обращении с состоянием** — гонки запросов,
неразличимые состояния «пусто / ошибка», ложные «успехи», тихие отказы,
незакрытые ресурсы.

Этот документ фиксирует **инварианты**, которые должны выполняться во всём
публичном фронте. Они не привязаны к одному компоненту — это сквозные правила
слоя `karaoke-public/src/utils/` и `src/components/`/`src/views/`.

Правило в основе: **пользователю нельзя показывать «успех», которого не
было, и «пусто», которое на самом деле «ошибка»**.

## Интерфейсы и Контракты

Слой чистых функций вынесен в `karaoke-public/src/utils/` — модули без
зависимостей (тестируются `node --test` без `node_modules` и без браузера):

| Модуль | Контракт |
|---|---|
| `clipboard.js` | `copyTextToClipboard(text, deps)` -> `Promise<boolean>`; `true` только если текст реально в буфере |
| `cartToggle.js` | `classifyCartToggle(status, body)` -> `{result, message, hideIcon}`; `needsCartLoad({...})` -> `boolean` |
| `shareSecret.js` | `resolveSecretStorage`, `readShareUrl`, `saveShareUrl`, `clearShareUrl`, `clearAllShareUrls`, `reconcileCreatedLink` |
| `requestGuard.js` | `createRequestGuard()` -> `{begin, isCurrent, invalidate, current}` — счётчик поколений |
| `loadState.js` | `classifyLoad(status, body, opts)` -> `{state, message}`; состояния `READY/EMPTY/NOT_FOUND/UNAUTHORIZED/ERROR` |
| `statusMap.js` | `chatPollDecision(isPremium)`, `buildSongStatusMap(songs, isPremium, subscriptionIds)`, `songStatusOf`, `playableSongIds` |
| `editorGuards.js` | `isSaveSuccessful`, `mergeSaveStatus`, `isUnmounted`, `shouldRefreshPreview`, `soundSwitchOutcome`, `releaseAllScrubTimers`, `voiceLabelPlan`, `trimTrailingEmptyVoices` |
| `routeGuard.js` | `routeGuardOutcome(error)` -> `{abort, notify}`; `shouldPollNews(isAuthenticated, hiddenRoute)` |
| `streamLifecycle.js` | `releaseStream(stream)`, `shouldReplaceActiveStream(activeStream)` |
| `pricing.js` | `pickDefaultTariff(tariffs)`, `priceTexts(siteTariffs, songTariffs)` |
| `searchPagination.js` | `urlPage(query)`, `needsSliceRestore`, `restorePlan`, `searchErrorScope`, `searchErrorText` |
| `notify.js` | `notify(vm, message, kind)` — единый тост ошибки |
| `player/playerGuards.js` | `classifyInitError`, `prerollDisplayTime`, `createSeekTracker`, `isStaleGeneration`, `transposeYieldDue`, `shouldGenerateStarfield`, `readStoredNumber`, `createBackgroundCanvas`, `roundRectPath` |
| `authorIndex.js` (PKG-S3-28) | `buildAuthorIndex(tiles)` -> `Map<name, id>`; `authorIdFromIndex(index, name)` -> `string|null` |
| `messageOrigin.js` (PKG-S3-28) | `isTrustedParentMessage(event, {origin, source})` -> `boolean`; `currentOrigin()` -> `string` |
| `tokenTimers.js` (PKG-S3-28) | `clearTokenTimers(timers, pendingToken)` -> `number`; `pendingTokenCount(pendingToken)` |
| `streamGeneration.js` (PKG-S3-28) | `createGenerationCounter()`, `nextGeneration(counter)`, `isCurrentGeneration(counter, gen)`, `invalidateGeneration(counter)` |
| `shareClaim.js` (PKG-S3-28) | `shareClaimState(status, body)` -> `SHARE_STATE`; `shareErrorText(state)`; `shareLeaseState(expiresAt, now)`; `needsReclaim(expiresAt, now, marginMs)` |
| `zakromaRoute.js` (PKG-S3-28) | `isAuthorRoute(params)`, `isSpecialBucketPath(path)`, `authorChosenForRoute(path, params)`, `zakromaBackTarget(state)`, `zakromaStreamParams(tile, albumId)`, `isAuthorMissing(tile, tilesLoaded)` |
| `stemJobs.js` (PKG-S3-28) | `visibleJobs(jobs)` (без `deleteRequested`), `hasActiveJobs(jobs)` |

## Логика и Алгоритмы

### 1. «Пусто» и «ошибка» — разные состояния

Загрузчик данных обязан различать успешный ответ без данных и сбой. Единая
классификация — `classifyLoad` (`karaoke-public/src/utils/loadState.js`):

- `200` + данные -> `READY`;
- `200` + пусто -> `EMPTY` (показываем «ничего нет», НЕ «не удалось загрузить»);
- `404` -> `NOT_FOUND`;
- `401`/`403` -> `UNAUTHORIZED` («войдите заново»);
- прочее (5xx, сеть) -> `ERROR` (сообщение + кнопка «Повторить»).

Затронутые места: `views/HistoryView.vue`, `views/NewsView.vue`,
`views/PlaylistEditView.vue`, `views/PremiumView.vue`, `views/ChatView.vue`,
`views/EditorWorkView.vue`, `store/modules/stats.js`,
`composables/usePlaylistMembership.js`.

### 2. Ответ применяется только к последнему запросу

Любой ресурс, который можно запросить повторно (цена тарифа, цена песни,
страница выдачи), читается через **счётчик поколений** —
`createRequestGuard()` (`karaoke-public/src/utils/requestGuard.js`). Ответ с устаревшим
поколением отбрасывается целиком: он не трогает ни данные, ни флаги загрузки,
ни текст ошибки.

Для выдачи поиска поколение **одно** на `search` и `loadMoreSearchResults`
(`store/modules/songs.js`): новый поиск инвалидирует in-flight подгрузку.

### 3. Мутация подтверждается сервером

Оптимистичное изменение состояния допустимо только с откатом: пока сервер не
ответил `200`, изменение считается неapplied и откатывается, пользователь
получает сообщение. `pushQueue` в плеер отправляется только после подтверждения.

### 4. Секрет capability-ссылки не переживает вкладку

URL вида `/share/{linkId}/{secret}` — bearer-capability (даёт полный доступ к
премиум-плееру без авторизации; на сервере хранится только SHA-256). Поэтому:

- хранится в `sessionStorage` (не `localStorage`) — исчезает вместе с вкладкой;
- чистится при выходе из аккаунта и при смене пользователя
  (`useAuth.clearSession`/`setSession` -> `clearAllShareUrls`);
- блок «создано» сверяется с ответом polling: если ссылку перевыпустили или
  отозвали, показывается фактическое состояние, а не старый URL.

### 5. Копирование подтверждается результатом

`copyTextToClipboard` возвращает `true` только если `navigator.clipboard`
разрешился ИЛИ `document.execCommand('copy')` вернул `true`. Иначе UI показывает
«Скопируйте вручную», а не «Ссылка скопирована».

### 6. Плеер: поколения загрузки и уступка главному потоку

- `init()` различает отменённую/устаревшую загрузку и настоящую ошибку:
  ложная ошибка не пишется в `#kp-loading` следующего трека;
- создание `WaveSurfer` проверяет **поколение загрузки** — запоздалый колбэк
  динамического импорта не создаёт инстансы в чужом контейнере;
- транспонирование (`_transposeBuffer`) идёт чанками с уступкой главного потока;
- фоновая текстура генерируется **один раз на инстанс**, а не на каждый трек;
- `localStorage` читается через безопасный хелпер (`readStoredNumber`);
- Canvas API — с feature-detection (`OffscreenCanvas`, `roundRect`), тело кадра
  рендер-цикла — в `try/catch`.

### 7. Редактор заданий: fail-closed

- `saveNow()` возвращает признак успеха; `submit()` прерывается, если черновик
  не сохранён;
- композитный статус задания (`rejected`) не затирается ответом `save`
  (сервер всегда отвечает `in_progress`);
- листенеры `window`/`document` навешиваются **до** асинхронной загрузки и
  снимаются в `beforeUnmount`; флаг `_unmounted` проверяется после каждого
  `await`;
- у голоса **без текста** маркеры не переразмечаются (иначе `label` всех
  слоговых маркеров обнуляется и текст голоса теряется после апрува);
- потеря фокуса окна останавливает интервалы перемотки.

### 8. Сессия: корзина, polling, стрим

- корзина сбрасывается при выходе и перезагружается при входе/смене
  пользователя; для анонима она **не** помечается «загруженной»;
- polling новостей и чата останавливается, когда пользователь вышел из
  аккаунта (а не только на скрытых маршрутах);
- NDJSON-стрим «Закромы» можно прервать по-настоящему: store держит активный
  composable и отдаёт `cancelZakromaStream`; новый стрим всегда заменяет текущий.

### 9. Поколение стрима: коммитит только последний (PKG-S3-28)

У NDJSON-стрима «Закромы» есть **номер поколения** (`streamGeneration.js`).
Коммитить данные (`setZakroma`), ошибку (`setStreamError`) и флаг
`setStreaming` вправе **только текущее** поколение. Устаревшее поколение
(перебитое новым стримом, снятым явной отменой) молча уходит, освобождая
свои ресурсы.

Проверка обязательна в трёх точках `store/modules/zakroma.js`: после `await`
успеха, в `catch` и в `finally` (`setStreaming(false)`). Иначе стрим автора A,
завершившийся после старта стрима автора B, показывал песни A на странице B
и гасил прогресс-бар B.

### 10. Ресурсы, живущие после ухода со страницы (PKG-S3-28)

Любой ресурс, созданный компонентом, обязан сниматься в `beforeUnmount`:

- таймеры ожидания токена в плеере плейлиста (`tokenTimers.js`) — хэндл
  сохраняется, при размонтировании таймер снимается, а ожидающие промисы
  резолвятся `null`;
- таймер опроса заданий стемов (`StemJobsView`) — ручка живёт **на инстансе**
  (не на уровне модуля), интервал создаётся **синхронно** (до `await`), а
  колбэк проверяет флаг размонтирования;
- таймер опроса статуса оплаты (`SubscriptionReturnView`) — id таймера
  сохраняется и снимается при размонтировании; авто-переход (`$router.replace`)
  выполняется только если компонент ещё смонтирован и маршрут всё ещё
  `/subscription/return`.

### 11. Доверие к postMessage (PKG-S3-28)

Сообщение `postMessage` принимается только если совпадают **origin** (свой
сайт) **и** источник (`window.parent`). Строковый маркер в `e.data` — третий
фильтр, а не барьер: константа из бандла не защищает от фрейминга. Обёртка —
`isTrustedParentMessage` (`karaoke-public/src/utils/messageOrigin.js`).

### 12. Состояние страницы выводится из маршрута (PKG-S3-28)

Для `ZakromaView` (`/zakroma`, `/zakroma/{id}`, `/zakroma/{id}/albums`,
`/zakroma/special-bucket`) состояние (`authorChosen`, `specialBucketShown`,
`selectedAuthorId`) вычисляется **из маршрута**, а не из обработчика клика:
один и тот же URL обязан вести себя одинаково при прямом заходе, F5 и переходе
изнутри SPA. Back-link строится чистой функцией `zakromaBackTarget`
(спец-корзина проверяется первой; пустой `authorId` в ссылку не попадает).

### 13. «Повторить» повторяет операцию, а не меняет страницу (PKG-S3-28)

Кнопка «Повторить» повторяет ровно ту операцию, которая упала, с теми же
параметрами. Для стрима «Закромов» это `zakromaStreamParams(tile, albumId)`
(автор, expectedCount, albumId, `force: true`) — **без навигации**. Раньше
«Повторить» вызывал `onAuthorSelect` и уводил на страницу альбомов: упавший
стрим так и не перезапускался.

### 14. Авторские поля не теряются при переходе на поток (PKG-S3-28)

NDJSON-сообщение `meta` несёт авторские поля страницы песен автора
(`authorDescription`, `authorShortDescription`, `authorWarning`, `authorSkip`) —
те же, что отдаёт REST-версия (`ZakromaPublicDto`). Поля **необязательны**:
старый бэкенд их просто не присылает, и фронт остаётся рабочим. Флаг
`authorSkip` отдаётся только тем, кто имеет право видеть SKIP-контент.

### 15. Короткоживущая сессия проверяется перед использованием (PKG-S3-28)

Lease сессии share-ссылки живёт 90 секунд и продлевается heartbeat'ом плеера;
лендинг heartbeat не шлёт. Поэтому перед переходом в плеер lease проверяется
(`needsReclaim`), а истёкший (или неизвестный) lease продлевается повторным
claim. Иначе пользователь получал «Ошибка: HTTP 404» вместо плеера.

### 16. Ответ сервера разбирается по кодам, а не по «всё остальное — 404» (PKG-S3-28)

Доменные коды ошибок разводятся по состояниям: `share.notFound` /
`share.tokenMissing` — «ссылка недоступна» (без retry), `share.internal` и любой
5xx/сетевой сбой — «сервис недоступен» **с кнопкой «Повторить»**. Общая
классификация ответов — `classifyLoad`
(`karaoke-public/src/utils/loadState.js`); для share-claim —
`shareClaimState` (`karaoke-public/src/utils/shareClaim.js`).

### 17. Флаг из DTO, а не из несуществующего поля (PKG-S3-28)

Флаги берутся из того поля, которое реально присылает сервер. Пример: признак
SKIP-песни — это `SongPublicDto.contentRemoved` (сервер считает его из `tags`
точным сравнением токена), а НЕ `currentSong.tags` (такого поля в DTO нет —
геттер был всегда `false`). Проверка «а поле вообще есть в DTO?» — обязательный
шаг при чтении флагов из ответа.

### 18. Каталоги — один раз в индекс (PKG-S3-28)

Линейный поиск по каталогу (`Array.find` по тысячам тайлов) в шаблоне — не
допускается: каталог разворачивается в `Map` (`authorIndex.js`), пересборка —
только при смене источника, а резолв выполняется один раз на строку
(в `computed`, а не в шаблоне).

## Зависимости

- -> [`karaoke-public-composables.md`](karaoke-public-composables.md) —
  каталог composables публичного SPA.
- -> [`karaoke-public-tests.md`](karaoke-public-tests.md) — тестовая
  инфраструктура (в том числе «до/после»-контрактные тесты).
- -> [`composable-use-cart.md`](composable-use-cart.md),
  [`composable-news-unread.md`](composable-news-unread.md) — детальные
  документы изменённых composables.
- -> [`composable-zakroma-stream.md`](composable-zakroma-stream.md) —
  жизненный цикл NDJSON-стрима «Закромы» (поколение стрима, `meta`).
- -> [`store-songs.md`](store-songs.md), [`store-stats.md`](store-stats.md) —
  store-модули публичного SPA.
- -> `knowledge/domains/monetization/domain.md` — тарифы, корзина, подписки
  (домен, к которому относятся инварианты 1–3).
- -> `knowledge/domains/karaoke-web/domain.md` — публичный API и share-ссылки
  (домен, к которому относятся инварианты 14–16).
