# Публичный SPA `karaoke-public`: 13 записей аудита (BR-295…BR-350)

> **Status**: active
> **Feature Key**: pkg-s2-14-audit-fixes
> **Last Updated**: 2026-10-25
> **Spec**: [specs/377-pkg-s2-14/report.md](../../specs/377-pkg-s2-14/report.md)
> **Issue**: OpenProject #377 (пакет `PKG-S2-14`, волна D)
> **Branch**: `599-pkg-s2-14`
> Per-feature документ (Constitution § VI FR-009).

## Что делает

Закрывает 13 дефектов S2 публичного SPA `karaoke-public`, найденных аудитом
#336 (порции P-42…P-49): отозванная «только в UI» share-ссылка, гонка двух
загрузок трека в плеере, терминальный release share-сессии на переключении
вкладки, stored XSS через метку комментария редактора, утечка листенеров и
WaveSurfer, гейт чата по устаревшему кэшу, потеря автосохранения при уходе со
страницы, END-маркер в нуле при незагруженном аудио, «залипшие» флаги
`saveState`/`submitting`/`loading` при сетевом сбое, отсутствие таймаута XHR,
«капча готова» без `window.smartCaptcha` и ложное «подписка не найдена» после
реальной оплаты.

## Зачем

Это публичный периметр и денежный путь. Ошибки здесь стоят либо денег
(повторная оплата после ложного «не найдена», отозванная на сервере, но живая
в браузере share-ссылка с полным доступом к премиум-плееру), либо данных
(потеря правки редактора, END-маркер в начале трека у всех голосов), либо
безопасности админки (stored XSS через метку комментария, которую админ
открывает в ReviewModal). Отдельная категория — «тихие» зависания: экран
бесконечно показывает «Загрузка...», потому что reject промиса никто не
обработал.

## Как работает

- **BR-295** — `utils/shareRevoke.js:isRevokeConfirmed(status, body)`; `revoke()`
  в `ShareLinkModal.vue` меняет локальное состояние (обнуляет `currentLink`,
  вызывает `clearSavedUrl`) только при `200 + {revoked: true}`, иначе оставляет
  URL и показывает причину (401 — «войдите заново»).
- **BR-302** — `KaraokePlayer.init()` захватывает локальный `AbortController`,
  передаёт `controller.signal` в `fetch` и `_loadAudio(signal)`, и после каждого
  `await` проверяет `isStaleLoad(this._activeAbortController, controller)`
  (`player/loadGuard.js`).
- **BR-303** — листенер `visibilitychange → release` удалён;
  `resolveShareReleaseReason()` возвращает `'closed'` только для
  `pagehide`/`beforeunload`, слот освобождается истечением lease по TTL.
- **BR-312** — экранирование (`&`, `<`, `>`, `"`, `'`) текста слога и метки
  `COMMENT|` в `formatText` обеих копий
  (`karaoke-public/src/composables/useKaraokeEditor.js`,
  `webvue3/src/components/SongEditor/useReviewModalFormat.js`), в
  `webvue3/src/composables/useKaraokeEditor.js` и в инлайн-шаблоне `COMMENT` в
  `SubsEdit.vue`. Оба регрессионных теста BR-523 включены
  (`SKIP_UNTIL_BR312 → false`).
- **BR-313** — подписки composable `useZakromaStreamProgress` идут через
  `utils/listenerBag.js`; `cleanup()` снимает их и чистит `showTimeout`; store
  освобождает предыдущий экземпляр перед новым и после завершения стрима.
- **BR-319** — `ChatView.mounted()` ждёт `await fetchMe()`, доступ считается
  через `utils/chatAccess.js:chatAccessState`; `watch(isPremium)` догружает тред
  при появлении премиума и останавливает поллинг при потере; 403
  `premium_required` — отдельное состояние, а не пустой тред.
- **BR-327** — `beforeRouteLeave` при взведённом таймере выполняет
  `await saveNow()` (`utils/autosave.js:autosaveFlushPlan`), `beforeUnmount` —
  отправляет сохранение без ожидания.
- **BR-328** — `ensureEndMarker(markers, duration)` не трогает маркеры при
  `duration <= 0`; `submit()` блокируется, пока аудио не декодировано.
- **BR-329** — `utils/wavesurferTeardown.js`: `destroyWaveform(owner)` перед
  созданием нового экземпляра и в `beforeUnmount`, `beginWaveformLoad`/
  `endWaveformLoad` запрещают повторный вход в `initWaveSurfer`; `load()`
  разделён на guard и `_loadTask()`.
- **BR-330** — `saveNow`/`submit`/`recall` обёрнуты в `try/catch/finally`,
  причины различаются `utils/xhrOutcome.js` (`network | timeout | unauthorized |
  http`), `submitting`/`recalling`/`saveState` сбрасываются в `finally`;
  отправка не уходит, если черновик не сохранился.
- **BR-339** — `authApi.request/authPostJson/authUpload` задают `xhr.timeout`
  (15 с; 120 с для загрузки стема) и `ontimeout → reject('timeout')`;
  `PlaylistsView`/`PlaylistEditView` снимают `loading` в `finally` и показывают
  ошибку с кнопкой «Повторить».
- **BR-340** — `utils/captchaReady.js`: `captchaScriptState` различает
  `load/wait/ready/unavailable` (ожидание `load` с таймаутом 10 с, удаление
  упавшего `<script>`); `captchaClientGateEnabled` включает клиентский гейт
  только при отрисованном виджете.
- **BR-350** — `utils/subscriptionPoll.js`: `classifyPollResponse` +
  `subscriptionPollView` дают состояния `checking | processing | terminal |
  not-found | check-error | unauthorized`; «не найдена» — только после
  успешного `200`; у ошибки есть «Повторить проверку» и предупреждение не
  оплачивать повторно.

## Инварианты

1. Локальное «ссылка отозвана» возможно только при `200 + body.revoked === true`.
2. `init()` плеера пишет в state только если её контроллер всё ещё текущий и не
   отменён; чужой signal не считается своим.
3. Терминальный `release` share-сессии — только `pagehide`/`beforeunload`;
   `visibilitychange → hidden` сессию не разрушает.
4. Пользовательский текст не становится разметкой: `formatText` экранирует и
   слоги, и метку `COMMENT|`; обе копии покрыты тестом, включённым вместе с
   фиксом.
5. Каждая подписка на `window`/`document`, сделанная composable, снимается в
   `cleanup()`.
6. Решение о премиум-доступе принимается по ответу `/auth/me`, а не по
   `localStorage`; смена `isPremium` пересобирает экран.
7. Отложенное автосохранение не отменяется при уходе: маршрут ждёт flush.
8. `END` не ставится и не переносится при неизвестной длительности трека.
9. `loading`/`saveState`/`submitting`/`recalling` не остаются включёнными после
   сетевого сбоя.
10. Запрос без ответа обрывается таймаутом; «не найдена» не подменяет ошибку
    проверки.
11. Клиентский гейт капчи включается только при реально отрисованном виджете.

## Известные ловушки

- **`formatText` существует в четырёх местах.** Правка одной копии оставляет
  дыру: публичное превью (`karaoke-public/src/composables/useKaraokeEditor.js`),
  админский ReviewModal (`webvue3/src/components/SongEditor/useReviewModalFormat.js`),
  админский редактор (`webvue3/src/composables/useKaraokeEditor.js`) и инлайн-шаблон
  `COMMENT` в `SubsEdit.vue`. Обе «главные» копии обязаны оставаться
  самодостаточными (локальная `escapeHtml`, без импорта) — иначе
  `tools/check-marker-escape-guard.sh`, копирующий один файл в staging, ломается.
- **`visibilitychange → hidden` — не «уход со страницы».** Переключение вкладки,
  свёрнутое окно и блокировка экрана мобильного приходят тем же событием.
  Терминальный `release` там обнуляет серверную сессию у ещё валидной ссылки;
  слот должен освобождаться истечением lease.
- **`sendBeacon` не подходит для сохранения разметки:** save-эндпоинт требует
  `Authorization: Bearer`, а `sendBeacon` не умеет заголовки. В `beforeUnmount`
  остаётся обычный XHR «в надежде успеть»; надёжный flush на закрытии вкладки
  потребовал бы токен в теле запроса, то есть поддержки на бэкенде — за
  владельцем.
- **`authGet`/`authPost` НЕ реджектят на 4xx/5xx** — это осознанный контракт
  (`authApi.js`), тело ответа нужно и на ошибке. Поэтому проверять только
  `status` недостаточно, а `catch` обязателен: сеть и таймаут приходят reject'ом.
- **`xhr.timeout` не отменяет уже начатый `decodeAudioData`** — Web Audio API не
  поддерживает abort на decode, поэтому `_fetchAudio` дополнительно проверяет
  `signal.aborted` после декодирования.
- **Кэш `km_auth_user` — не истина.** Он обновляется только `fetchMe()` (раз в
  5 минут) и после входа. Любое решение о доступе по нему сразу после оплаты
  или окончания подписки — лотерея.
- **`v-html` в шаблоне не защищён экранированием вызывающего кода**:
  `formattedTextHtml`, `parsedMarkupHtml` и строка превью `SubsEdit.vue`
  принимают готовый HTML. Экранировать нужно в источнике, а не в шаблоне.
- **`vue/no-reserved-keys`**: служебные поля компонента с `_` нельзя держать в
  `data()` (нереактивные флаги вроде `_loadingTask` инициализируются в
  `created()`).
- **Счётчик успешных опросов оплаты нельзя заменить на «прошло N секунд»**:
  только успешный `200` с массивом доказывает, что сервер ответил; иначе
  «не найдена» показывается на 401/500/сетевом сбое.

## Ссылки

- [Отчёт пакета PKG-S2-14 и доказательства «до/после»](../../specs/377-pkg-s2-14/report.md)
- [Бэклог аудита #336, блок PKG-S2-14](../../specs/336-code-audit/backlog.md)
- [Карточки аудита (порции P-42…P-49)](../../specs/336-code-audit/findings/)
- [SSoT: публичный SPA `karaoke-public`](../../knowledge/domains/karaoke-web/components/public-spa-clients.md)
- [SSoT: домен karaoke-web](../../knowledge/domains/karaoke-web/domain.md)
- [SSoT: SongShareLinkService — серверная сторона share-ссылок](../../knowledge/domains/karaoke-web/components/song-share-link-service.md)
