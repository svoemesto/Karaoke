# Research: 563 — реализация спеки 557 (закрыть обход оплаты по скрытому жесту)

**Input**: `specs/557-close-gesture-token-paywall/spec.md` (контракт, утверждён владельцем),
`specs/336-code-audit/findings/P-35.md` → BR-018, `specs/336-code-audit/registry.csv` (строка BR-018).

**Проверено на диске** (ветка `563-impl-gesture-token`, base `cf97b82f`).

---

## D1 — Жест перестаёт быть основанием доступа (вариант А владельца)

**Решение.** Публичный `POST /api/public/events` больше не возвращает значение доступа:
в ответе остаётся поле `meta`, но оно всегда `null`. Механика распознавания жеста удаляется
целиком — `PlayerGestureUnlockService.registerClick`, buckets кликов и константы
`TARGET_FIELD`/`REQUIRED_CLICKS`/`CLICK_WINDOW_MS` удаляются.

**Обоснование.** FR-001/FR-007: анонимный посетитель не должен получать «ключ доступа» из
публичного beacon-эндпоинта. Ветка фронтенда просто перестаёт срабатывать — тихий отказ, без
ошибок у посетителя (FR-010): `trackMetaClick` становится fire-and-forget, как остальные
вызовы трекинга, `SongView.onMetaClick` больше не открывает плеер.

**Почему не «просто убрать выдачу токена».** Токен жеста и легитимный токен
структурно неразличимы (один сервис, TTL 30 мин, различие только у демо-токена —
`PlayerGestureUnlockService.kt:36–42,143–154,169–174`). Поэтому право обязано проверяться
в момент отдачи байт — см. D2. Решение владельца №1 (вариант А) прямо это требует.

**Следствие для формата ответа.** FR-014 запрещает менять формат публичных ответов, кроме
прекращения выдачи значения доступа. Поэтому ключ `meta` остаётся (значение — всегда `null`),
а не удаляется.

---

## D2 — Право проверяется в момент отдачи байт (живая проверка), а не по факту владения токеном

**Решение.** В `PublicPlayerController` двойная авторизация
(`gestureUnlockService.validateToken(token, id)` ИЛИ
`shareLinkService.validateShareSession(session, id)`) заменяется на решение, вычисляемое
**в момент запроса**: `PlayerAccessService.evaluate(song, token, session)`.

**Обоснование.** FR-002/FR-004: «выдача полного аудио и полной разметки MUST опираться на
действующее право доступа, проверенное в момент запроса, а не только на факт владения ранее
выданным токеном». Образец «живой» проверки в проекте уже есть — выгрузка пакета файлов
(`playerfile`) требует живого статуса премиума (`PublicPlayerController.kt:480–499`).
Правка сводит отдачу байт к тому же правилу.

**Основания доступа (ровно те, что перечислены в FR-003 + FR-009):**

| # | Основание | Как проверяется в момент запроса |
|---|---|---|
| 1 | Песня публично доступна (всегда бесплатно / эфир в окне бесплатного доступа) | `Song.isFreelyAvailableNow` (`Song.kt:685–692`) |
| 2 | Действующий премиум | живая загрузка `SiteUser` по id → `isEffectivePremium` (`SiteUser.kt:91–99`) |
| 3 | Бессрочная подписка на эту песню | `Subscription.isSubscribedToSong(userId, songId, …)` — живой SQL |
| 4 | Гостевая сессия по публичной share-ссылке | `SongShareLinkService.validateShareSession(session, songId)` — живой lease |
| 5 | Предпросмотр неодобренной разметки по **своему** заданию редактора | живая загрузка `SongAssignment` → `assigneeId == userId && songId == id` (FR-009, Edge Case) |

Основание №5 — не «премиум», а владение заданием; оно проверено в момент выдачи
(`PublicSongEditorController.task()` → `loadOwnedAssignment`) и проверяется повторно в момент
отдачи байт.

---

## D3 — Токен несёт id пользователя и id задания

**Решение.** `TokenInfo` получает поле `userId: Long?`. Токен выдаётся только
`PlayerGestureUnlockService` (три точки выдачи: `/access` — прямой и демо-токен,
`PublicSongEditorController.task()` — токен задания). Ни одна из них больше не выдаёт токен
анонимному посетителю без основания.

**Почему id пользователя в токене, а не только заголовок `Authorization`.** Аудио-эндпоинты
плеера получают токен и share-сессию query-параметрами, но **не** получают заголовок
`Authorization`: плеер грузит стемы через `fetch(url)` без заголовков
(`karaoke-public/src/player/KaraokePlayer.js:1389`, экспорт — `:1282`). Если бы «право»
проверялось только по заголовку, премиум-пользователь потерял бы доступ к своим стемам после
правки. Поэтому сервер берёт идентификатор пользователя из токена и **перечитывает его права
из БД в момент запроса**. Токен при этом — не право, а указатель на проверяемое основание.

**Следствие для `playerdata`/`playerfile`.** Их решения вычисляются по тому же правилу и по
тому же токену, поэтому разойтись не могут (в отличие от прежней схемы, где `authorized()`
смотрел на токен, а `isShareGuest` — на share-сессию, а `canExport` — на заголовок).

---

## D4 — Посетитель без права при живом токене получает демо-фрагмент, а не ошибку

**Решение.** Решение доступа имеет три состояния: `Full` / `Demo(range)` / `None`.

- `Full` — полные байты и полная разметка.
- `Demo(range)` — только фрагмент: байты обрезаются на сервере, маркеры `playerdata`
  обрезаются и перебазируются, в ответе `isDemo=true` + `demoFadeInSeconds`.
- `None` — 404 (нет ни токена, ни сессии).

**Обоснование.** Edge Case спеки: «Право закончилось посреди сессии: премиум истёк или
подписка отозвана, а ранее выданный токен ещё жив → полный доступ больше не предоставляется;
посетитель переходит на демо-режим (фрагмент), а не на ошибку». US3.1 и SC-003 требуют того
же: 100 % запросов аудио от посетителей без полного доступа отдают демо-фрагмент.
Диапазон понижения — тот же, что и у демо-токена: `Song.demoFragmentStartSeconds` /
`demoFragmentEndSeconds` (`Song.kt:3686–3689`).

**Особый случай — токен задания.** Если редактор потерял задание, доступ к предпросмотру
прекращается (`None`, 404 — как для невалидного токена), а не понижается до демо:
предпросмотр черновика — это конкретная задача, и «фрагмент вместо черновика» вводил бы
редактора в заблуждение.

---

## D5 — Обрезка демо-фрагмента должна быть fail-closed

**Решение.** `Mp3Trimmer.trimToRange` при неудачном разборе потока возвращает **пустой**
массив, а не исходные байты.

**Обоснование.** FR-005: «полный файл и полная разметка не покидают сервер ни при каких
условиях». Текущий фолбэк (`Mp3Trimmer.kt:88–90`: `catch (e) { bytes }`) при неразбираемом
потоке отдаёт **полный файл** — то есть ровно тот обход, который закрывает эта задача, только
на другом пути. Для демо-режима «лучше отдать тишину, чем полный файл». На реальных стемах
(LAME VBR, `ApiController.pushMp3ToStorage`) поведение не меняется: поток разбирается, диапазон
режется по границе кадра.

---

## D6 — Журнал отказов: категория `infra.player.access`

**Решение.** Новая категория `infra.player.access` (по конвенции
`knowledge/domains/monitoring/components/log-categories.md`: `infra.<subsystem>.<feature>`).

| Сообщение | Уровень | Когда |
|---|---|---|
| `player:access:denied` | WARN | доступа нет вовсе (`None`) либо полный токен без действующего права (понижение до демо) — это и есть сигнал обхода оплаты |
| `player:access:demo` | INFO | штатный демо-режим: посетитель без полного доступа получил фрагмент |

**Поля записи:** `songId=`, `reason=`. **Сам токен и содержимое аудио в журнал не пишутся**
(FR-012). Время — штатный timestamp logback.

**Обоснование.** FR-012 + SC-005 требуют, чтобы каждый отказ в выдаче полного аудио был
виден в журнале; US3.1 — чтобы запись появлялась и в штатном демо-режиме. Уровни разделены,
чтобы демо-режим (массовый, нормальный) не превращал WARN в шум, а обход оплаты был виден
сразу.

---

## D7 — Живой премиум для выгрузки пакета — из того же источника, что и для стемов

**Решение.** В `playerfile` условие «действующий премиум» считается как
`премиум по заголовку Authorization` **ИЛИ** `премиум по id пользователя из токена` (тот же
живой источник, что и в D2).

**Обоснование.** FR-008: «возможность выгрузки пакета файлов MUST остаться только у
посетителей с действующим премиумом». Проверка только по заголовку не работает: экспорт
стема грузится через `fetch(url)` без заголовков (`KaraokePlayer.js:1282`), поэтому запрос
выгрузки приходит без `Authorization` и премиум-пользователь получает 403. Разница — только
в том, КАК определяется премиум; основание (живой премиум) то же. Побочный эффект: экспорт
для премиум-пользователя начинает работать, как и задумано (`canExport` в `playerdata`
уже вычисляется по живому премиуму). Не-премиум и гость по share-ссылке — по-прежнему 403.

---

## D8 — Что НЕ меняем

- **TTL токена — 30 минут** (FR-013, решение владельца №2): при живой проверке права срок
  жизни токена перестаёт быть единственным барьером; менять его — отдельная правка с риском
  для легитимных сессий.
- **Демо-режим** как продуктовая функция (FR-005) — сохраняется, полный файл не покидает
  сервер.
- **Формат публичных ответов**, кроме `meta` в ответе `POST /api/public/events` (FR-014).
- **Имя класса `PlayerGestureUnlockService`** — историческое; жеста в классе больше нет,
  но переименование тянет за собой SSoT-ссылки (`knowledge/domains/karaoke-web/domain.md`,
  `components/services-overview.md`) и `@see` в других файлах. Отдельная правка.
- **Схема БД и прод-данные** (FR-015) — правка не требует миграций.
- **Защита `POST /api/public/events` от флуда** — вне объёма (см. Out of Scope спеки).

---

## D9 — Тесты «до/после»

**Решение.** Два уровня:

1. **Контроллерный тест «до/после»** (`karaoke-web/src/test/…/PublicPlayerControllerAccessTest.kt`):
   жест (3 клика с `shiftKey` по полю `key`) через `PublicApiController.events()` →
   в ответе нет значения доступа; анонимный запрос полного стема отклоняется.
   **Тест компилируется и на коде без правки и падает на нём** (assertion, а не ошибка
   компиляции) — это и есть требуемое FR-011 «до/после».
2. **Матрица решений** (`…/PlayerAccessServiceTest.kt`): все основания из D2 (публичность /
   премиум / подписка / share-сессия / задание редактора) и все отказы (нет токена, токен без
   права, токен от другой песни, потерянное задание).

**Обоснование.** Решение доступа вынесено в отдельный сервис с инъецируемыми зависимостями
(`PlayerAccessService` + `PlayerRightsProvider`), поэтому матрица тестируется без БД и без
MinIO, а сквозной путь «жест → байты» проверяется на реальных контроллерах.

---

## D10 — Загрузка песни вынесена из контроллера (`PlayerSongLoader`)

**Решение.** Статический вызов `Song.loadFromDbById` в `PublicPlayerController` заменён на
инъецируемый сервис `PlayerSongLoader` (`karaoke-web/.../services/PlayerSongLoader.kt`).

**Обоснование.** Регрессионный тест «до/после» должен прогонять настоящий контроллер: путь
«песня → право → байты». Статический метод `Song.loadFromDbById` — это метод компаньона
(`Song$Companion`), а не статический метод класса, поэтому его нельзя подменить через
`Mockito.mockStatic` — вызов идёт через `Song.Companion.INSTANCE`. Инъецируемый сервис решает
задачу честно и без рефлексии: в тесте подменяется только загрузка песни, а правило доступа,
контроллер и отдача байтов — настоящие. Побочная выгода: `PlayerSongLoader` можно переиспользовать
в других точках публичного плеера.

**Что НЕ меняется.** Способ загрузки песни, её кэширование и поведение при отсутствии песни
(404) — те же.

## D11 — Минимизация изменений во фронтенде

**Решение.** Меняются ровно две точки фронта:

- `karaoke-public/src/services/tracking.js` — `trackMetaClick` становится fire-and-forget
  (ответ больше не несёт ничего полезного, ждать его незачем).
- `karaoke-public/src/views/SongView.vue` — `onMetaClick` больше не сохраняет токен и не
  открывает `/player/:id`.

**Обоснование.** FR-007 требует, чтобы «приём и учёт обычных кликов по метаданным песни
продолжали работать без изменений» — поэтому сам состав трекинг-события
(`eventType=clickToLink`, `linkType=songMeta`, `linkName`, `songId`, `shiftKey`, `clientId`)
не меняется. Легитимный путь получения токена (`usePlayerAccess.js` → `/access`) и
`playerLauncher.js` не трогаются.

---

## Источники (проверено на диске)

- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/PlayerGestureUnlockService.kt`
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlayerController.kt`
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt`
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicSongEditorController.kt`
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/util/Mp3Trimmer.kt`
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` (`isFreelyAvailableNow`,
  `demoFragmentStartSeconds`/`demoFragmentEndSeconds`)
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SiteUser.kt` (`isEffectivePremium`,
  `getSiteUserById`)
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Subscription.kt`
  (`isSubscribedToSong`)
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongAssignment.kt` (`getById`,
  `assigneeId`, `songId`)
- `karaoke-public/src/player/KaraokePlayer.js` (загрузка аудио без `Authorization`),
  `karaoke-public/src/services/tracking.js`, `karaoke-public/src/views/SongView.vue`
- `knowledge/domains/karaoke-web/{domain.md, components/public-controllers.md,
  components/public-controllers-3.md, components/public-controllers-4.md,
  components/services-overview.md, components/song-share-link-service.md}`
- `knowledge/domains/monetization/domain.md`, `knowledge/domains/catalog/components/song-entity.md`
- `knowledge/adr/local-0001-karaoke-properties-defaults.md`,
  `knowledge/adr/local-0006-logging-and-error-handling-karaoke-web.md`
- `docs/features/song-air-access.md`
- `specs/554-close-public-sql-endpoint/` — образец артефактов и регрессионного теста
