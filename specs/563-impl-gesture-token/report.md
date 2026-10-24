# Отчёт по #358 — Закрыть обход оплаты по «скрытому жесту» (BR-018, спека 557)

**Ветка**: `563-impl-gesture-token` (worktree `/home/nsa/Karaoke/.worktrees/impl358`, base `cf97b82f`)
**Контракт**: `specs/557-close-gesture-token-paywall/spec.md` (утверждён владельцем 2026-10-24)
**Артефакты реализации**: `specs/563-impl-gesture-token/` (research.md, plan.md, tasks.md, quickstart.md)

## Что было

Анонимный посетитель получал **полный доступ к стемам и разметке платной песни** тремя
анонимными запросами:

1. `POST /api/public/events` с `eventType=clickToLink&linkType=songMeta&linkName=key&songId=<id>&shiftKey=true`
   ×3 за 1,5 с → `PublicApiController.events()` вызывал
   `PlayerGestureUnlockService.registerClick(...)` → в ответе поле `meta` содержало токен
   доступа (TTL 30 минут).
2. `GET /api/public/player/<id>/fileminus.mp3?token=<meta>` → `PublicPlayerController.authorized()`
   считал **факт владения токеном** достаточным основанием → `stemResponse` отдавал полный mp3.

Ключевая находка: токен жеста и легитимный токен **структурно неразличимы** (один сервис,
один TTL, различие только у демо-токена), поэтому «просто убрать выдачу токена» было
недостаточно — право обязано проверяться в момент отдачи байт.

## Что изменено

| Файл | Что |
|---|---|
| `karaoke-web/.../services/PlayerAccessService.kt` (NEW) | **Правило доступа** — единственное место, где решается, можно ли отдать полные байты/разметку. Основания: публичность песни / живой премиум / живая подписка на песню / гостевая share-сессия / владение заданием редактора. Решения: `Full` / `Demo(range)` / `None`. Ведёт журнал отказов `infra.player.access` |
| `karaoke-web/.../services/PlayerRightsProvider.kt` (NEW) | Живой источник прав из БД: `isPremium` (загрузка `SiteUser` → `isEffectivePremium`), `isSubscribedToSong`, `isAssignmentOwner` |
| `karaoke-web/.../services/PlayerSongLoader.kt` (NEW) | Загрузка песни для плеера вынесена из контроллера (исходный `Song.loadFromDbById` — метод компаньона, его нельзя подменить в тесте; см. research.md D10) |
| `karaoke-web/.../services/PlayerGestureUnlockService.kt` | Удалена механика жеста (`registerClick`, bucket'ы кликов, константы `TARGET_FIELD`/`REQUIRED_CLICKS`/`CLICK_WINDOW_MS`). Токен несёт `userId` и разбирается одним методом `describeToken` (вид FULL/DEMO/ASSIGNMENT + assignmentId + demoRange). TTL 30 минут — без изменений (FR-013) |
| `karaoke-web/.../controllers/PublicPlayerController.kt` | `authorized()`/`isShareGuest()` заменены на живое решение `PlayerAccessService.evaluate`. Стимы и `playerdata` отдают демо-фрагмент при отсутствии права, 404 — при отсутствии токена/сессии. `playerfile` — только при полном доступе и живом премиуме |
| `karaoke-web/.../controllers/PublicApiController.kt` | `POST /api/public/events` больше не возвращает значение доступа: `meta` всегда `null`. Зависимость от сервиса токенов убрана. Обычный клик-трекинг по метаданным песни не изменён |
| `karaoke-web/.../controllers/PublicSongEditorController.kt` | Токен задания несёт id редактора — право на предпросмотр перепроверяется в момент отдачи байт |
| `karaoke-web/.../util/Mp3Trimmer.kt` | Обрезка демо-фрагмента **fail-closed**: при неразбираемом потоке возвращается пустой массив, а не исходные байты (полный файл не покидает сервер ни при каких условиях, FR-005) |
| `karaoke-public/src/services/tracking.js` | `trackMetaClick` — fire-and-forget (ответ эндпоинта событий больше ничего не сообщает). Состав трекинг-события не изменён |
| `karaoke-public/src/views/SongView.vue` | `onMetaClick` больше не открывает плеер по ответу на трекинг — тихий отказ, без ошибок у посетителя (FR-010) |
| `karaoke-web/src/test/.../PublicPlayerControllerAccessTest.kt` (NEW) | Регрессионный тест «до/после»: жест не выдаёт значение доступа; анонимный посетитель не получает полный стем |
| `karaoke-web/src/test/.../PlayerAccessServiceTest.kt` (NEW) | Матрица решений: 11 сценариев (все основания доступа и все отказы) |
| `karaoke-web/src/test/.../KaraokeWebTestEnv.kt` (NEW) | Инициализация глобалов `karaoke-web` для юнит-тестов (`WORKING_DATABASE` читает их лениво) |
| `docs/features/close-gesture-token-paywall.md` (NEW) | Per-feature документ (Constitution VI, FR-009) |
| `docs/features/song-air-access.md` | Правило доступа к плееру приведено в соответствие с фактом; публичность песни — одно из пяти оснований |
| `knowledge/domains/karaoke-web/components/public-controllers-4.md` | Контракт плеера: живая проверка права, демо-понижение, fail-closed обрезка |
| `knowledge/domains/karaoke-web/components/public-controllers-3.md` | `POST /events` больше не возвращает значение доступа |
| `knowledge/domains/karaoke-web/components/public-controllers.md` | Формула доступа к плееру и категория журнала |
| `knowledge/domains/karaoke-web/components/services-overview.md` | Каталог сервисов: контракт `PlayerGestureUnlockService` + три новых сервиса |
| `knowledge/domains/karaoke-web/domain.md` | Структура модуля и список сервисов |
| `knowledge/domains/monitoring/components/log-categories.md` | Новая категория `infra.player.access` |
| `knowledge/domains/catalog/components/remaining-models.md` | `LinkType.SONG_META` — жеста больше нет |
| `knowledge/domains/karaoke-web/components/song-share-link-service.md` | Уточнение про in-memory токены плеера |
| `knowledge/system/frontend/composable-use-player-access.md` | Токен из `/access` — не право |
| `specs/336-code-audit/registry.csv` | BR-018 → статус «исправлено» (work_package 358) |

**Не менялось** (по условиям спеки): TTL токена (30 минут), демо-режим как функция, формат
публичных ответов (кроме значения `meta`), схема БД и прод-данные, выгрузка пакета для
не-премиума, предпросмотр редактора, легитимный путь получения токена (`/access`).

## Проверки

| Проверка | Результат |
|---|---|
| `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:compileKotlin :karaoke-app:compileKotlin --parallel` | **PASS** (BUILD SUCCESSFUL) |
| `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:test :karaoke-app:test` | **PASS** — 468 тестов, 0 failures, 0 errors (6 skipped — до правки) |
| `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew ktlintCheck` | **PASS** (все модули, 0 новых нарушений) |
| `:karaoke-web:test --tests PublicPlayerControllerAccessTest --tests PlayerAccessServiceTest` | **PASS** — 13/13 (2 + 11) |
| `cd karaoke-public && npm run lint` | **PASS** (0 ошибок) |
| `cd karaoke-public && npm run build` | **PASS** (vite build, 3.8 s) |
| `cd karaoke-public && npm run format:check` | **PASS** («All matched files use Prettier code style!») |
| `bash tools/check-knowledge-structure.sh` | **PASS** (9/9) |
| `python3 tools/lint-knowledge.py` | **PASS** («All documentation passes architectural style and structural checks») |
| `python3 tools/check-doc-references.py` | **PASS** (4040 ссылок, 2176 путей, 304 эндпоинта) |
| `bash tools/check-feature-doc.sh docs/features/*.md` | **PASS** (все документы валидны) |
| `bash tools/check-subagent-isolation.sh` | **PASS** (см. «Известные проблемы», п. 1) |
| `git ls-files \| grep -iE '\.env$\|do\.env$\|\.key$\|\.pem$\|\.p12$\|\.pfx$'` | **пусто** — секретов в индексе нет |
| `pre-commit run --files <30 изменённых файлов>` | **PASS** — 18 хуков: 15 Passed, 3 Skipped («no files to check») |
| `webvue3` (админский фронт) | **не трогался** — `npm run lint/build/format:check` не запускались (правки только в `karaoke-public`) |

### Регрессионный тест «до/после» — фактическая демонстрация

Файл `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlayerControllerAccessTest.kt`.
Проверено на диске: **частичный откат** правки (возврат старого поведения «токен = право» в
`PlayerAccessService.evaluate` и возврат выдачи значения доступа в `POST /api/public/events`)
заставляет тест падать **по существу** (runtime-assertion, не ошибка компиляции):

```
PublicPlayerControllerAccessTest > скрытый жест не выдаёт значение доступа() FAILED
  AssertionFailedError: после 0-го клика публичный эндпоинт событий не должен возвращать
  значение доступа ==> expected: <null> but was: <e7a6a4fa1628734602f3bdc9795977bf68656113343cb322>

PublicPlayerControllerAccessTest > анонимный посетитель не получает полный стем платной песни() FAILED
  AssertionFailedError: анонимный посетитель не должен получать полный стем:
  status=200, size=41700, full=41700 ==> expected: <true> but was: <false>
```

`size=41700 == full=41700` — это и есть уязвимость: анонимный посетитель получает **полный**
стем платной песни. После правки тест проходит: `meta` пуст, анонимный запрос отклоняется
(404), а «полный» токен без действующего права даёт демо-фрагмент (меньше полного файла).

Полный откат правки (код до спеки 557) дополнительно ломает компиляцию теста — новые сервисы
`PlayerAccessService`/`PlayerRightsProvider`/`PlayerSongLoader` в старом коде отсутствуют.
Прецедент тот же, что в спеке 554 (см. `specs/554-close-public-sql-endpoint/report.md`,
«Известные проблемы», п. 3).

### Известные проблемы (не по этой правке)

1. **CI не запускался** — PR не создавался (по условию задачи: без push/PR). Локальные проверки
   выше воспроизводят те же джобы, что и `.github/workflows/lint.yml` (ktlint, ESLint+Prettier,
   Docs, KDoc, JSDoc, Knowledge SSoT, guard'ы).
2. **`specs/336-code-audit/findings/P-36.md`** (карточка находки P-36-01) лежит на ветке аудита
   `553-336-code-audit` и **в master отсутствует** — в этой ветке обновлена только строка
   реестра `registry.csv` (файл присутствует в master). Карточку P-36 нужно перевести в
   «исправлено» при слиянии ветки аудита.
3. **Ветка аудита не тронута**: правка BR-018 сделана в своей ветке, карточка P-36 в ветке
   аудита не обновлялась (чтобы не плодить конфликты при её слиянии).

## Поведение после фикса

| Ситуация | До | После |
|---|---|---|
| Три «жеста» через `POST /api/public/events` | `meta` = токен доступа | `meta` = `null`, значения доступа нет |
| Анонимный запрос полного стема платной песни | полный mp3 (200) | 404 (нет токена/сессии) |
| Токен есть, но действующего права нет (премиум истёк) | полный mp3 | демо-фрагмент + запись `player:access:denied` |
| Премиум-пользователь, стем | полный mp3 | полный mp3 (живая проверка премиума) |
| Подписка на эту песню | полный mp3 | полный mp3 (живая проверка подписки) |
| Песня в эфире / всегда бесплатно | полный mp3 | полный mp3 |
| Гость по публичной share-ссылке | полный mp3 | полный mp3 (живая проверка lease) |
| Редактор, предпросмотр своего задания | полный mp3 + черновик | полный mp3 + черновик (живая проверка владения заданием) |
| Посетитель без полного доступа | демо-фрагмент | демо-фрагмент (тот же диапазон) |
| Токен одной песни, запрос другой | отказ | отказ |
| Публичный клик по метаданным песни | трекинг + `meta` | трекинг без изменений |
| Срок жизни токена | 30 минут | 30 минут (FR-013) |

## Источник в журнале отказа

Категория `infra.player.access` (`karaoke-web/.../services/PlayerAccessService.kt`):

```
WARN  infra.player.access -- player:access:denied songId=42 reason=no_rights
WARN  infra.player.access -- player:access:denied songId=42 reason=no_credentials
WARN  infra.player.access -- player:access:denied songId=42 reason=assignment_revoked
INFO  infra.player.access -- player:access:demo songId=42 reason=demo_token
```

Поля: `songId=`, `reason=`. **Сам токен и содержимое аудио в запись не пишутся** (FR-012).
`player:access:denied` (WARN) — сигнал обхода оплаты; `player:access:demo` (INFO) — штатный
демо-режим (полный доступ не предоставлен).

## Что осталось за владельцем

1. **Прод-деплой `karaoke-web`** — отдельное решение (в задаче не выполнялось). Развёртывание
   должно сопровождаться **перезапуском контейнера**: токены живут в памяти процесса, поэтому
   ранее выданные (в т.ч. по жесту) переживут выкат максимум 30 минут (Edge Case спеки 557).
2. **`tracker.sh add-comment 358 --file specs/563-impl-gesture-token/report.md`** и
   **`tracker.sh mark-review 358`** — выполняются лидом после merge (шаги 4–5 workflow из
   спеки 557), как и в спеке 554.
3. **PR + CI** — по условию задачи не создавались; после merge пройти 7/7 джобов CI.
4. **Карточка аудита P-36** (`specs/336-code-audit/findings/P-36.md`) — перевести в «исправлено»
   в ветке аудита `553-336-code-audit` (см. «Известные проблемы», п. 2).
5. **Переименование `PlayerGestureUnlockService`** (историческое имя) — отдельная правка,
   сознательно вне объёма (research.md D8).

## Что НЕ сделано (вне объёма)

- Защита `POST /api/public/events` от флуда и накруток (лимиты частоты, sampling) — отдельная
  задача по устойчивости периметра (Out of Scope спеки 557).
- Обход ограничения «SKIP» в плеере (BR-022, S2) — отдельная задача.
- Остальные публичные дефекты волны A: BR-019/BR-518 (#359), BR-123 (#360), BR-258 (#361).
- Рефакторинг системы токенов и сессий плеера, перенос в персистентное хранилище, изменение
  TTL токена.
- Переработка публичного плеера и его интерфейса, кроме отключения ветки скрытого жеста.

## Ветка и коммиты

- Ветка: `563-impl-gesture-token` (от `master` @ `cf97b82f`), worktree
  `/home/nsa/Karaoke/.worktrees/impl358`.
- Коммиты — с маркером `[tracker-claim-358]`:
  - `61a0a06a` — `fix(563): закрыть обход оплаты по скрытому жесту (#358, BR-018) [tracker-claim-358]`
  - `docs(563): отчёт для трекера #358 — закрытие обхода оплаты по «скрытому жесту» [tracker-claim-358]`
    (этот файл; хеш — `git log --oneline -1` — он меняется при amend'е самого отчёта)
- Push/PR не выполнялись (по условию задачи). Master не тронут; правка главной копии
  `/home/nsa/Karaoke` не производилась.
