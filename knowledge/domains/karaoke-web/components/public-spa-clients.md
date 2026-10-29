# Спецификация компонента: публичный SPA `karaoke-public` — границы доверия и отказоустойчивость

## Ответственность | Responsibility

`karaoke-public` — публичный SPA (Vue 3 + Vuex + WaveSurfer), который отдаёт
nginx (`deploy/karaoke-public/Dockerfile`). Он не имеет собственного бэкенда:
все данные идут через `/api/public/**` в `karaoke-web`, а действия владельца —
через `/api/public/account/**` (Bearer JWT из `localStorage`, 30 дней).

Компонент отвечает за три вещи, которые нельзя делегировать серверу:

1. **Границы доверия на клиенте.** Пользовательский текст (метки `COMMENT|`,
   слоги, имя плейлиста) и ответы сервера не считаются разметкой.
2. **Отказоустойчивость к сети.** Незавершённый, зависший или отклонённый
   запрос обязан приводить к видимому состоянию с возможностью повтора, а не
   к вечному «Загрузка...» и не к ложному «ничего не найдено».
3. **Корректность отображения статуса доступа.** Премиум-статус берётся из
   живого `user.effectivePremium` (`useAuth().fetchMe()`, обновление раз в
   5 минут), а не из кэша `km_auth_user`.

## Интерфейсы и Контракты | Interfaces и Contracts

- **`utils/shareRevoke.js` → `isRevokeConfirmed(status, body) → boolean`** —
  истина только при `status === 200 && body.revoked === true`. `ShareLinkModal`
  меняет локальное состояние (обнуляет `currentLink`, вызывает `clearSavedUrl`)
  только по `true`; иначе показывает причину и оставляет URL.
- **`player/loadGuard.js` → `isStaleLoad(activeController, myController) → boolean`** —
  загрузка трека устарела, если её контроллер заменён или отменён.
  **`resolveShareReleaseReason(eventType) → 'closed'|null`** — терминальный
  `release` share-сессии допустим только для `pagehide`/`beforeunload`.
- **`utils/xhrOutcome.js` → `classifyAuthOutcome(error, status)` /
  `describeAuthOutcome(outcome)`** — единая классификация исхода запроса:
  `ok | network | timeout | unauthorized | http` и текст ошибки для
  пользователя. Используется экранами редактора и плейлистов.
- **`utils/subscriptionPoll.js`** — состояния страницы возврата из ЮKassa:
  `checking | processing | terminal | not-found | check-error | unauthorized`;
  `not-found` возможен только после хотя бы одного успешного `200`.
- **`utils/captchaReady.js`** — готовность SmartCaptcha по глобалу
  `window.smartCaptcha`, а не по наличию `<script>`; клиентский гейт отправки
  включается только при реально отрисованном виджете.
- **`utils/chatAccess.js`** — состояние доступа к чату
  (`login | checking | locked | ready`) и различение
  `403 premium_required` / `401` / остальных ошибок.
- **`utils/autosave.js` → `autosaveFlushPlan({timerArmed, canEdit})`** — при
  взведённом таймере отложенного автосохранения уход со страницы обязан
  выполнить flush, а не отменить таймер.
- **`utils/wavesurferTeardown.js`** — `destroyWaveform(owner)` (идемпотентно
  уничтожает WaveSurfer и обнуляет `ws`/`wsRegions`), `beginWaveformLoad`/
  `endWaveformLoad` (запрет повторного входа в создание вейвформы).
- **`utils/listenerBag.js` → `createListenerBag()`** — учёт подписок
  `window`/`document` с гарантированным снятием в `cleanup()`.

## Логика и Алгоритмы | Logic and Algorithms

- **Эшелонированная защита от XSS в редакторе.** Текст слога и метки
  `COMMENT|…` экранируются (`&`, `<`, `>`, `"`, `'`) в обеих копиях
  `formatText`: `karaoke-public/src/composables/useKaraokeEditor.js` и
  `webvue3/src/components/SongEditor/useReviewModalFormat.js`, а также в
  `webvue3/src/composables/useKaraokeEditor.js` и в инлайн-шаблоне `COMMENT` в
  `SubsEdit.vue`. Обе копии `formatText` нарочно самодостаточны (локальная
  `escapeHtml`, без импорта) — этого требует
  `tools/check-marker-escape-guard.sh`, который прогоняет файл изолированно.
- **Поколение загрузки трека.** `KaraokePlayer.init()` создаёт локальный
  `AbortController`, кладёт его в `_activeAbortController` и передаёт
  `controller.signal` в `fetch` и `_loadAudio(signal)`. После каждого `await` —
  `isStaleLoad(...)`: если поле уже указывает на следующую загрузку, `init()`
  выходит, не записывая state чужого трека. Тот же signal идёт в оба
  `_fetchAudio`, поэтому отмена прерывает и скачивание, и (пост-фактум) decode.
- **Жизненный цикл share-сессии.** `pagehide`/`beforeunload` → терминальный
  `release('closed')`. `visibilitychange → hidden` терминальный release НЕ
  шлёт (переключение вкладки, свёрнутое окно, блокировка экрана): слот
  освобождается истечением lease по TTL, а ссылка остаётся валидной.
- **Автосохранение разметки.** Таймер 3 с; до ухода с маршрута
  (`beforeRouteLeave`) отложенное сохранение выполняется через `await
  saveNow()`, в `beforeUnmount` — отправляется без ожидания (страница
  выгружается). Ошибка сохранения блокирует отправку на проверку: на проверку
  не должна уходить предыдущая версия черновика.
- **Опрос статуса оплаты.** Попытки (`6 × 2 с`) идут, только пока статус не
  терминальный и ответ не `401`. `200` с массивом считается успешной проверкой,
  `5xx`/сеть/не-массив — «не удалось проверить» с кнопкой повтора. Текст
  предупреждает не оплачивать повторно: `PAID` ставит вебхук ЮKassa, а не этот
  GET.
- **Капча.** Наличие `<script>` не равно готовности: состояния
  `load → wait (ожидание load с таймаутом) → ready | unavailable`; упавший тег
  удаляется, чтобы следующий заход создал его заново. При `unavailable`
  клиентский гейт выключен — решение остаётся серверной проверке
  (`captcha_not_configured`/`captcha`, fail-closed на бэкенде).
- **Чат.** `mounted()` ждёт `fetchMe()` и только затем решает вопрос доступа;
  `watch(isPremium)` догружает тред при появлении премиума и останавливает
  поллинг при его потере.

## Зависимости | Dependencies

- → `karaoke-web` (`/api/public/**`, `/api/public/account/**`) — источник данных
  и единственный авторитет по правам, премиуму и статусу оплаты.
- → `useAuth` (`composables/useAuth.js`) — `user`, `isLoggedIn`, `fetchMe`;
  кэш `localStorage['km_auth_user']` — только подсказка для UI.
- → WaveSurfer 7 + Regions/Minimap/Hover plugins — вейвформа и регионы редактора.
- → Yandex SmartCaptcha — сторонний скрипт, грузится в `<head>`.
- ← `webvue3` импортирует не код SPA, а его поведенческие копии (`formatText`,
  `loadEditorSettings`); синхронность копий проверяет
  `tools/check-marker-escape-guard.sh`.

## Ссылки

- [Отчёт пакета PKG-S2-14 и доказательства «до/после»](../../../../specs/377-pkg-s2-14/report.md)
- [Per-feature документ: пакет аудита PKG-S2-14](../../../system/infra/audit-336-registry.md)
- [Карточки аудита: P-42, P-43, P-44, P-45, P-46, P-47, P-49](../../../../specs/336-code-audit/findings/)
- [Домен karaoke-web](../domain.md)
- [SongShareLinkService — серверная сторона share-ссылок](song-share-link-service.md)
