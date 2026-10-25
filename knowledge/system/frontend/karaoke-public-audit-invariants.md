# Component: karaoke-public-audit-invariants (инварианты публичного SPA)

> **Домен**: system (cross-cutting)
> **Компонента**: инварианты `karaoke-public`, зафиксированные аудитом #336
> (волна E, пакет `PKG-S3-27`, OpenProject #399).

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

## Зависимости

- -> [`karaoke-public-composables.md`](karaoke-public-composables.md) —
  каталог composables публичного SPA.
- -> [`karaoke-public-tests.md`](karaoke-public-tests.md) — тестовая
  инфраструктура (в том числе «до/после»-контрактные тесты).
- -> [`composable-use-cart.md`](composable-use-cart.md),
  [`composable-news-unread.md`](composable-news-unread.md) — детальные
  документы изменённых composables.
- -> [`store-songs.md`](store-songs.md), [`store-stats.md`](store-stats.md) —
  store-модули публичного SPA.
- -> `knowledge/domains/monetization/domain.md` — тарифы, корзина, подписки
  (домен, к которому относятся инварианты 1–3).
