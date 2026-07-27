# Runbook для администратора: раскатка «Альбом + Settings→Song»

Пошаговая инструкция по применению изменений БД и деплою кода этой фичи — на LOCAL (admin-машина)
и затем на SERVER (прод). Каждый пункт — конкретная команда с ожидаемым результатом. Источники
конвенций: `docs/database.md` (ручные SQL-миграции), `docs/deployment.md` (права на
сборку/перезапуск/деплой), `research.md` §5.1 (риски переименования таблицы).

**Общее правило порядка**: миграция БД → код (собрать) → перезапуск → проверка. Никогда не
пересобирать/перезапускать `karaoke-app` раньше, чем применена соответствующая миграция —
и никогда не применять миграцию, не будучи готовым сразу пересобрать код (иначе бьющий по
живой таблице код будет ошибаться до тех пор, пока код не обновлён).

---

## Часть 1 — Миграция 28: переименование `tbl_settings`→`tbl_songs`

### 1.1. LOCAL — статус на данный момент

Выполнено в этой сессии (с согласия пользователя, песочница):

- [x] `deploy/karaoke-db/28_rename_settings_to_songs.sql` применена на LOCAL БД (`docker exec -i karaoke-db psql -U postgres -d karaoke < ...`) — `tbl_settings`→`tbl_songs`, `tbl_settings_sync`→`tbl_songs_sync`, все индексы/констрейнты/sequence/функции переименованы. Проверено: 19441 строка сохранена, `to_regclass('tbl_settings')` = NULL.
- [x] Kotlin-код (`karaoke-app`, `karaoke-web`) обновлён: `Settings.TABLE_NAME = "tbl_songs"` + все raw-SQL литералы в 16 файлах (см. `tasks.md` T005/T006). `karaoke-app:compileKotlin`/`karaoke-web:compileKotlin` — чисто.
- [x] `karaoke-web` пересобран и перезапущен (`bash do.sh build_web && bash do.sh start_web` — агенту разрешено без согласия). Логи чистые: `StatBySong.refreshCache: total=19119...` — запросы к `tbl_songs` работают.
- [ ] **`karaoke-app` — ТОЛЬКО пользователь.** На момент этой записи `karaoke-app` ещё работает на старом джаре и **уже ошибается** (таблица переименована, старый код ищет `tbl_settings`, которой больше нет). webvue3 (список песен, редактор) сейчас, скорее всего, не работает — это ожидаемо и пройдёт после пересборки ниже.

### 1.2. LOCAL — что нужно сделать администратору

```bash
cd ~/Karaoke/deploy && bash do.sh build_app          # сборка образа из репозитория (разрешено без спроса)
cd /sm-karaoke/system/deploy && bash do.sh start_app  # ПЕРЕЗАПУСК — делает только пользователь
```

Проверить после перезапуска:
1. `docker logs karaoke-app --tail 50` — нет `relation "tbl_settings" does not exist`.
2. В `webvue3` открывается список песен без ошибок.
3. Отредактировать любую песню, сохранить — без ошибок.
4. Запустить «Синхронизацию в 1 клик» — проходит как раньше.

Если что-то не так — до следующего шага (SERVER) НЕ переходить, сообщить о проблеме.

### 1.3. SERVER — когда LOCAL полностью подтверждён

⚠️ Каждый шаг — **только по прямому согласию пользователя**, ничего не выполняется автоматически.

1. Выяснить реальную роль Postgres на сервере (на проде роли `postgres` нет):
   ```bash
   ssh root@<PROD_SERVER_IP> "docker exec karaoke-db env | grep '^POSTGRES_USER='"
   ```
2. Скопировать и применить миграцию на серверную БД:
   ```bash
   scp deploy/karaoke-db/28_rename_settings_to_songs.sql root@<PROD_SERVER_IP>:/tmp/
   ssh root@<PROD_SERVER_IP> "docker exec -i -u <РОЛЬ_ИЗ_ШАГА_1> karaoke-db psql -d karaoke < /tmp/28_rename_settings_to_songs.sql"
   ```
3. Сразу вслед — задеплоить обновлённый `karaoke-web` (он один и работает на проде из бэкенд-модулей; `karaoke-app` на проде не разворачивается вообще):
   ```bash
   cd ~/Karaoke/deploy && bash deploy_web.sh
   ```
   После успеха проверить (см. `docs/deployment.md`):
   - в логах push нет `EOF`/`400 Bad request`;
   - на сервере `Status: Downloaded newer image` (не `Image is up to date`);
   - `docker exec karaoke-web env | grep <VAR>` — реальные env.
4. Проверить публичный сайт (`karaoke-public`) — страницы песен/дискографии открываются, плеер работает.

**Важно**: между шагом 2 (миграция) и шагом 3 (деплой) на проде живой трафик может ненадолго
получать ошибки от `karaoke-web` (старый джар всё ещё ищет `tbl_settings`) — минимизировать разрыв,
выполнять шаги 2 и 3 подряд, без пауз.

---

## Часть 2 — Миграция 29: сущность `Album` + `tbl_songs.album_id`

### 2.1. LOCAL — статус на данный момент

- [x] `deploy/karaoke-db/29_albums.sql` написана и применена на LOCAL БД: `tbl_albums` (FK на
  `tbl_authors.id` ON DELETE RESTRICT, unique `(author_id, year, name)`, recordhash-триггер),
  `tbl_songs.album_id` (FK на `tbl_albums.id` ON DELETE SET NULL), recordhash-функция `tbl_songs`
  пересобрана с учётом `album_id`, все 19441 строки пересчитаны (`UPDATE ... SET id = id`).
- [x] Kotlin-модели (`Album.kt`, `AlbumDTO.kt`, `AlbumType.kt`), sync-регистрация (`AlbumsSyncTarget`),
  8 флагов `sync_albums_*`, API-эндпоинты (`/api/albums/albumsdigests|createalbum|updatealbum|deletealbum`),
  поле `Settings.albumId` (+ проверка FR-008), backfill-скрипт (`AlbumBackfill.kt` +
  `/api/utils/backfillalbumsfromsongs`) — написаны и компилируются (`karaoke-app:compileKotlin`).
- [ ] webvue3 UI (раздел «Альбомы», выбор альбома в редакторе песни) — не реализовано (`tasks.md` T021-T025).
- [ ] karaoke-web / karaoke-public (`/zakroma` с `sortOrder`/`albumType`) — не реализовано (T026-T027).

### Что нужно администратору ПРЯМО СЕЙЧАС

```bash
cd ~/Karaoke/deploy && bash do.sh build_app          # сборка образа (весь код US1 уже в репозитории)
cd /sm-karaoke/system/deploy && bash do.sh start_app  # ПЕРЕЗАПУСК — только пользователь
```

Это тот же шаг, что и в 1.2 (один перезапуск покрывает и переименование таблицы, и весь код Album,
если оба ещё не применялись) — **делать один раз после того, как весь нужный вам объём кода готов**,
не после каждой отдельной задачи.

После перезапуска, чтобы выполнить бэкфилл (T020):
```bash
curl -X POST http://localhost:<ADMIN_APP_PORT>/api/utils/backfillalbumsfromsongs
```
Результат придёт SSE-уведомлением в `webvue3` (как у «Пересчёт готовности плеера»), и залогируется
в `docker logs karaoke-app`. Ожидаемо: ~2162 альбома создано, ~19441 песня привязана (см. baseline
в `tasks.md` T002). Проверка — `quickstart.md` Сценарий 1.

### 2.2. SERVER

Применяется той же процедурой, что и миграция 28 (см. 1.3), **после** переноса на сервер
миграции 28 и соответствующего кода, и только когда весь функционал Album проверен на LOCAL
(`quickstart.md` Сценарии 1, 2, часть Сценария 4). Так как `karaoke-app` на проде не
разворачивается — на сервере эта функциональность видна только через то, что читает
`karaoke-web` (`/zakroma`, публичная дискография) — см. T026/T027.

**Важно**: эндпоинт `/api/utils/backfillalbumsfromsongs` живёт в `karaoke-app`, который на PROD
не запускается вообще. Повторно «запустить бэкфилл на сервере» — невозможно и не нужно.
Данные (записи `tbl_albums` + проставленные `tbl_songs.album_id`), созданные бэкфиллом на LOCAL,
должны попасть на SERVER **через обычный sync-механизм** (`SyncRegistry`), как и любые другие
данные каталога:

1. В `webvue3` → «Синхронизация» — убедиться, что включены нужные флаги (по умолчанию **все 8
   флагов у каждой сущности выключены** — `sync_albums_push_insert_allowed` и `sync_settings_
   push_update_allowed` как минимум должны быть включены; ключ синхронизации песен остался
   `"settings"`, см. research.md §5).
2. Запустить синхронизацию в направлении LOCAL→SERVER — либо «Синхронизация в 1 клик» (у `Album`
   и `SongCoAuthor` `oneClickDirection = LOCAL_TO_SERVER`), либо целевой push отдельно для
   «Альбомы» (создаст 2162 строки в `tbl_albums` на сервере) и для «Настройки песен» (протащит
   новое значение `album_id` в уже существующих на сервере строках `tbl_songs` — это обычный
   field-level diff, ничего специального делать не нужно, если у песен уже когда-либо был
   push_update).
3. Проверить на сервере: `SELECT count(*) FROM tbl_albums;` / `SELECT count(*) FROM tbl_songs
   WHERE album_id IS NOT NULL;` (через ту же процедуру подключения, что в 1.3, шаг 1) — либо
   косвенно через `/zakroma` на публичном сайте (без ручной перестановки `sortOrder` порядок
   останется алфавитным — это ожидаемое поведение бэкфилла, см. `research.md` §6).

---

## Часть 3 — Миграция 30: соавторы песни (`tbl_song_authors`)

*Заполняется по мере реализации US2 (`tasks.md` T029-T037). На момент этой записи —
только заготовка файла `deploy/karaoke-db/30_song_coauthors.sql`.*

---

## Часть 4 — Полное переименование кода (US3) и финальная раскатка на SERVER

### 4.1. Статус на данный момент

- [x] Весь код US3 написан: `Settings`→`Song`, `SettingsDTO`→`SongDTO`, `SettingField`→`SongField`,
  `SettingVoice*`→`SongVoice*` (+ найденная по ходу коллизия — старый рендер-класс `Song`
  (уже переименованный в `SongRenderContext`) САМ содержал одноимённые вложенные классы
  `SongVoice`/`SongVoiceLine`/`SongVoiceLineType`/`SongVoiceLineSymbol` — переименованы в
  `SongRenderVoice*`, см. `tasks.md` T043 за подробностями), `CrossSettingsRow/Cell`→`CrossSongRow/Cell`,
  `SettingsSyncTarget`→`SongSyncTarget` (ключ `"settings"` НЕ менялся), `SettingsPublicDto`→`SongPublicDto`,
  webvue3 store-экшены/URL/параметры.
- [x] **Найден и исправлен реальный баг**, не пойманный компиляцией: `webvue3/src/App.vue` — SSE-обработчик
  сверял `case 'tbl_settings'` с именем таблицы из живых серверных событий; без фикса на `'tbl_songs'`
  реалтайм-обновления списка песен в админке молча сломались бы после деплоя. Также отменены два
  случайных зацепленных переименования несвязанных функций (`loadEditorSettings`/`saveEditorSettings` —
  настройки редактора; `_loadPersistedSettings`/`_savePersistedSettings` — настройки плеера).
- [x] `karaoke-app:compileKotlin`, `karaoke-web:compileKotlin`, `webvue3 npm run build`,
  `karaoke-public npm run build` — все четыре чистые.
- [x] `karaoke-app` пересобран и перезапущен в этой песочнице (с согласия пользователя) —
  логи чистые, `tbl_songs` работает.
- [x] Бэкфилл запущен и проверен: 2162 альбома, 19441 песня привязана, идемпотентность
  подтверждена повторным запуском.
- [x] Album CRUD, FR-008 (отклонение чужого альбома), US2 edge cases (главный автор/дубликат),
  `/api/sync/entities`, FR-007 (живая смена sortOrder на `/api/public/zakroma`) — проверены через curl.
- [x] Все 7 CI-проверок зелёные (ktlint, ESLint×2, prettier×2, KDoc, JSDoc, lychee, per-feature doc).
  `docs/features/dual-db-sync.md`/`mlt-generator.md` обновлены (FR-009).
- [ ] Полный проход по живому UI в браузере — не выполнен (нет браузера в этой сессии).
- [ ] Регенерация `karaoke_clear_dump.sql` — не выполнена (не блокирует ветку/CI).

### 4.2. SERVER — порядок финальной раскатки

Только по прямому согласию пользователя, строго в этом порядке (после того как ВСЁ выше проверено
на LOCAL):
1. `28_rename_settings_to_songs.sql` на серверную БД (см. 1.3).
2. `29_albums.sql` на серверную БД.
3. `30_song_coauthors.sql` на серверную БД.
4. `deploy_web.sh` (обновлённый `karaoke-web` — единственный бэкенд-модуль на проде).
5. Проверка `karaoke-public` (страницы песен/дискографии/плеер).
