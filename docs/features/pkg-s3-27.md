# PKG-S3-27 — правки аудита #336 (волна E, karaoke-public)

> **Status**: active
> **Feature Key**: pkg-s3-27
> **Last Updated**: 2026-10-25
> **Spec**: [specs/399-pkg-s3-27/report.md](../../specs/399-pkg-s3-27/report.md)
> **Issue**: OpenProject #399 (пакет `PKG-S3-27`, волна E бэклога аудита #336)
> **Branch**: `628-pkg-s3-27`
> Per-feature документ (Constitution § VI FR-009).

## Что делает

Пакет закрывает 40 записей аудита #336 в модуле `karaoke-public` — публичном
SPA сайта. Все записи — одного класса: **дефекты обращения с состоянием**,
а не «логика неверна». Правки сгруппированы по шести блокам:

- **Компоненты и share-ссылки** (BR-296, BR-297, BR-298, BR-299, BR-300,
  BR-301) — копирование подтверждается результатом; ответ корзины разбирается;
  бейдж чата следит за премиумом, а не только за входом; секрет
  capability-ссылки не переживает вкладку; цена песни/тарифа применяется
  только от последнего запроса; блок «создана ссылка» сверяется с polling.
- **Плеер** (BR-304…BR-311) — отменённая загрузка не показывается как ошибка;
  пауза в pre-roll не сбивает время; seek-событие несёт id песни момента
  перемотки; WaveSurfer не создаётся после `destroy()`; транспонирование
  уступает главный поток; фоновая текстура генерируется один раз на инстанс;
  `localStorage` читается безопасно; Canvas API — с feature-detection.
- **Сессия и навигация** (BR-314, BR-316, BR-317, BR-318, BR-320) — корзина
  привязана к пользователю; падение предзагрузки не отменяет переход;
  polling новостей останавливается при выходе; список плейлистов не роняет
  вызывающий код; NDJSON-стрим можно прервать по-настоящему.
- **Хранилища и store** (BR-321, BR-322, BR-323) — одно поколение выдачи для
  поиска и подгрузки; «пусто» ≠ «ошибка» в истории и статистике.
- **Просмотры** (BR-324, BR-325, BR-326) — поллинг чата работает и для пустого
  треда; статус песни берётся из карты, а не из линейного поиска; цены на
  странице «О проекте» берутся из API.
- **Редактор заданий** (BR-331…BR-338) — отправка прерывается, если черновик
  не сохранён; композитный статус не затирается; листенеры не остаются на
  `window`; превью не пересобирается 60 раз в секунду; стем фиксируется после
  успешной загрузки; потеря фокуса останавливает перемотку; голос без текста
  не переразмечается.

## Зачем

Каждая запись — наблюдаемый дефект, а не стилистика:

- **Публичный периметр.** Пользователю показывали «Ссылка скопирована» при
  пустом буфере, «0 песен в коллекции» при упавшей статистике, «Пока новостей
  нет» при 503, «Плейлист не найден» при 401.
- **Данные.** Цена чужой песни в модалке подписки; страница 2 старого поиска,
  подмешанная к новой выдаче; пустые метки, закреплённые в черновике и
  уничтожавшие текст голоса после апрува.
- **Гонки.** Два одновременных NDJSON-стрима на публичный hot-path эндпоинт;
  два события seek, приписанные разным песням; WaveSurfer, созданный после
  уничтожения плеера.
- **Ресурсы.** Фоновая текстура 64 МиБ генерировалась на каждый трек;
  транспонирование 5-минутной песни блокировало UI на секунды; interval
  перемотки крутился бесконечно после Alt-Tab.

## Как работает

Изменения по слоям:

| Слой | Файлы | Что изменилось |
|---|---|---|
| Чистые функции | `karaoke-public/src/utils/*.js` (12 модулей), `karaoke-public/src/player/playerGuards.js` | классификация ответов, счётчики поколений, статусы, безопасные обёртки над DOM- и storage-API |
| Компоненты | `components/{ShareButton,CartIcon,ChatUnreadBadge,ShareLinkModal,SongSubscriptionModal,PlaylistIcon}.vue` | проверка результата копирования, разбор ответа корзины, гейт премиума, sessionStorage для секрета, сверка с polling |
| Composables | `composables/{useAuth,useAuthBootstrap,useCart,useNewsUnreadCount,usePlaylistMembership,useSiteSubscription,useSongSubscription}.js` | поколения запросов, привязка корзины к токену, остановка polling при выходе, catch в bulk-загрузке |
| Store | `store/modules/{songs,stats,zakroma}.js` | одно поколение выдачи, состояния `loaded`/`error` в статистике, отмена NDJSON-стрима |
| Роутер | `router/index.js` | падение предзагрузки не отменяет навигацию |
| Views | `views/{HistoryView,HomeView,NewsView,ChatView,AboutView,AuthorPlaylistView,SearchView,PremiumView,PlaylistEditView,EditorWorkView,ZakromaView}.vue` | явные состояния ошибки, карта статусов, цены из API, раздельные флаги ошибок, fail-closed в редакторе |
| Плеер | `player/KaraokePlayer.js` | поколения загрузки, пауза в pre-roll, чанковое транспонирование, feature-detection Canvas |
| Тесты | `src/utils/__tests__/*.test.js` (11), `src/player/__tests__/playerGuards.test.js` | 279 кейсов, двухуровневая схема «поведение + контракт по исходнику» |

Общие принципы, которым подчинены правки:

1. **«Пусто» ≠ «ошибка».** Успешный ответ без данных и сбой — разные
   состояния с разными текстами и разными действиями (retry).
2. **Fail-closed.** Если состояние неизвестно (черновик не сохранён, поток
   прерван, ответ устарел) — действие не выполняется.
3. **Ответ последнего запроса.** Ресурс, который можно запросить повторно,
   читается через счётчик поколений.
4. **Подтверждение сервером.** Оптимистичное изменение состояния откатывается,
   если сервер не подтвердил.
5. **Чистые функции — отдельно.** Правило живёт в модуле без зависимостей,
   а не размазано по компонентам.

## Инварианты

| Инвариант | Где зафиксирован |
|---|---|
| Копирование в буфер возвращает `true` только при реальном успехе | `karaoke-public/src/utils/clipboard.js` |
| Ответ классифицируется: `READY` / `EMPTY` / `NOT_FOUND` / `UNAUTHORIZED` / `ERROR` | `karaoke-public/src/utils/loadState.js` |
| Ответ применяется только к последнему запросу (поколение выдачи) | `karaoke-public/src/utils/requestGuard.js`, `karaoke-public/src/store/modules/songs.js` |
| Корзина загружена для конкретного токена; для анонима флаг «загружено» снят | `karaoke-public/src/utils/cartToggle.js`, `karaoke-public/src/composables/useCart.js` |
| Capability-URL живёт только в `sessionStorage` и чистится при выходе/смене пользователя | `karaoke-public/src/utils/shareSecret.js`, `karaoke-public/src/composables/useAuth.js` |
| Блок «создана ссылка» сверяется с ответом polling (перевыпуск/отзыв) | `karaoke-public/src/utils/shareSecret.js` (`reconcileCreatedLink`) |
| Отменённая/устаревшая загрузка плеера не показывается как ошибка | `karaoke-public/src/player/playerGuards.js` (`classifyInitError`) |
| Экземпляры WaveSurfer создаются только в своём поколении загрузки | `karaoke-public/src/player/playerGuards.js` (`isStaleGeneration`) |
| Транспонирование уступает главный поток между чанками | `karaoke-public/src/player/playerGuards.js` (`transposeYieldDue`) |
| Фоновая текстура генерируется один раз на инстанс | `karaoke-public/src/player/playerGuards.js` (`shouldGenerateStarfield`) |
| Canvas API используется только с feature-detection; тело кадра рендера не роняет цикл | `karaoke-public/src/player/playerGuards.js` (`createBackgroundCanvas`, `roundRectPath`) |
| `saveNow()` возвращает признак успеха; `submit()` прерывается без сохранённого черновика | `karaoke-public/src/utils/editorGuards.js` (`isSaveSuccessful`) |
| Композитный статус задания не затирается ответом `save` | `karaoke-public/src/utils/editorGuards.js` (`mergeSaveStatus`) |
| Голос без текста не переразмечается; пустые хвостовые голоса не сохраняются | `karaoke-public/src/utils/editorGuards.js` (`voiceLabelPlan`, `trimTrailingEmptyVoices`) |
| Потеря фокуса останавливает интервалы перемотки | `karaoke-public/src/utils/editorGuards.js` (`releaseAllScrubTimers`) |
| Статус песни берётся из карты `Map<songId, status>` за O(1) | `karaoke-public/src/utils/statusMap.js` |
| Polling новостей/чата выключен без токена | `karaoke-public/src/utils/routeGuard.js` (`shouldPollNews`) |
| Ошибка предзагрузки в роутере не отменяет навигацию | `karaoke-public/src/utils/routeGuard.js` (`routeGuardOutcome`) |
| NDJSON-стрим можно прервать; новый поток заменяет текущий | `karaoke-public/src/utils/streamLifecycle.js` |

## Известные ловушки

1. **`localStorage` в публичном SPA недоступен в iframe без
   `allow-same-origin`.** Обращение бросает `SecurityError`, и необязательное
   чтение (сохранённый сдвиг тональности) роняло весь плеер. Теперь чтение идёт
   через `readStoredNumber` (`karaoke-public/src/player/playerGuards.js`) с
   fallback `null`. Новые обращения к `localStorage` в плеере обязаны идти
   через этот хелпер.
2. **`document.execCommand('copy')` возвращает `false`, а не бросает.** Это
   главная причина ложного «Ссылка скопирована»: браузер без разрешения на
   clipboard (Firefox, iOS Safari) отвечает `false`. Новые кнопки копирования
   обязаны вызывать `copyTextToClipboard`
   (`karaoke-public/src/utils/clipboard.js`) и **проверять** результат.
3. **Секрет share-ссылки — bearer-capability.** URL
   `/share/{linkId}/{secret}` даёт полный доступ к премиум-плееру без
   авторизации (на сервере хранится только SHA-256). Хранить его в
   `localStorage` нельзя: профиль браузера на общем устройстве переживает
   выход из аккаунта. Используется `sessionStorage` + очистка по префиксу при
   выходе и смене пользователя.
4. **В `karaoke-public` есть `store` (Vuex) и composables одновременно.**
   Состояние корзины, списков плейлистов и подписок — module-level
   синглтоны в composables; состояние выдачи поиска и статистики — в Vuex.
   Поколения запросов живут в обоих местах: `createRequestGuard()` в
   composable — на инстанс модалки, в `store/modules/songs.js` — **один на
   выдачу** (поиск и подгрузка делят поколение).
5. **`apiGet` реджектит промис на любом не-2xx, `authGet`/`authPost`
   реджектят только на сетевой ошибке.** Отсюда разные требования к `catch`:
   для `apiGet` нужен `catch` вокруг вызова, для `authGet` — проверка `status`.
   Единая классификация ответов — `classifyLoad`
   (`karaoke-public/src/utils/loadState.js`).
6. **В шаблоне `karaoke-public` используется `v-html` для разметки.** Текст
   маркеров экранируется в `formatText`; фикстуры XSS-регрессии — в
   `karaoke-public/src/composables/__tests__/formatText-escape.test.js` и
   guard `tools/check-marker-escape-guard.sh`. Новые места с `v-html`
   обязаны прогоняться через этот guard.
7. **Публичный плеер дублируется в `webvue3`.** Логика плеера в
   `karaoke-public/src/player/KaraokePlayer.js` и в
   `webvue3/src/player/KaraokePlayer.js` — две копии; правки инвариантов
   (пауза в pre-roll, поколение загрузки, feature-detection Canvas) нужно
   переносить в обе. В этом пакете изменена только публичная копия —
   синхронизация второй копии **за владельцем**.
8. **Вынос транспонирования в Web Worker/AudioWorklet не выполнен.** Сейчас
   обработка идёт чанками с уступкой главного потока (UI не замирает), но
   память всё ещё держит оригиналы и транспонированные копии одновременно
   (нужны для повторной смены тональности). Полное решение — за владельцем.
9. **Фоновая текстура теперь одна на инстанс плеера.** Палитра звездного
   фона не меняется при переходе на следующий трек внутри одной сессии
   плеера (раньше менялась). Это осознанный компромисс в пользу отсутствия
   джанка и пика памяти на каждом треке.
10. **Ключ `karaoke-share-url:` в `sessionStorage`** — это секрет, а не
    метаданные. Любая выгрузка `sessionStorage` наружу (например, в
    диагностический отчёт) раскрывает доступ к песне. Чистка выполняется в
    `useAuth.clearSession`/`setSession`.
11. **Регрессионные тесты публичного SPA двух уровней.** Поведенческие
    проверяют чистые функции; контрактные читают текст исходника
    (`karaoke-public/src/utils/__tests__/sourceContract.js`) и падают на
    старом коде. Новый фикс без контрактного теста останется непроверенным
    на «до/после».

## Ссылки

- Отчёт пакета: [specs/399-pkg-s3-27/report.md](../../specs/399-pkg-s3-27/report.md)
  (по записям, «до/после», побочные эффекты, решения за владельцем).
- Источник правды: [бэклог аудита § PKG-S3-27](../../specs/336-code-audit/backlog.md)
  и карточки `specs/336-code-audit/bug-report.md` (записи BR-296…BR-346).
- Регресс-тесты: `karaoke-public/src/utils/__tests__/` (11 файлов) и
  `karaoke-public/src/player/__tests__/playerGuards.test.js`; запуск —
  `cd karaoke-public && npm run test:unit` (279/279).
- SSoT: [karaoke-public-audit-invariants.md](../../knowledge/system/frontend/karaoke-public-audit-invariants.md) —
  инварианты публичного SPA; [karaoke-public-tests.md](../../knowledge/system/frontend/karaoke-public-tests.md) —
  тестовая инфраструктура; [karaoke-public-composables.md](../../knowledge/system/frontend/karaoke-public-composables.md) —
  каталог composables.
- Домены: [monetization](../../knowledge/domains/monetization/domain.md) —
  тарифы, корзина, подписки; [editorial](../../knowledge/domains/editorial/domain.md) —
  задания редакторов; [karaoke-web](../../knowledge/domains/karaoke-web/domain.md) —
  public API и share-ссылки на бэкенде.
