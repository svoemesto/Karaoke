# Синхронизация LOCAL→SERVER: новые песни не доезжали до прода — отчёт

**Дата:** 2026-11-14 · **Задача:** KARAOKE-429 · **Спека:** `specs/739-song-delete-cascade/`

## 1. Симптом (слово владельца)

«Добавил 4 альбома Мумий Тролль — на проде альбомы видны, пишется сколько в них
песен, но при заходе в альбом там видны только песни со статусом 6 (если такие
есть). Где остальные?»

Альбомы на проде появились, песни внутри них — нет.

## 2. Что было сломано

Два независимых дефекта, оба — в механизме синхронизации, оба подтверждены на
проде.

### 2.1. Порядок синхронизации таргетов (FK-инвариант)

Реестр таргетов `SyncRegistry.all` обходился в порядке
`Song, Pictures, Authors, Albums, …` — **песни синкались раньше альбомов**.

Прод применяет тело `POST /changerecords` **целиком в одной транзакции**
(`autoCommit=false`, `commit()` в конце, `rollback()` при любой ошибке).
Поэтому песня, ссылающаяся на ещё не залитый альбом, откатывала **весь батч
песен** (HTTP 500) — ни одна строка батча не применялась.

Проверенный на проде FK-граф (снят с прода 2026-11-14,
`information_schema` + `pg_constraint`):

| Ребёнок | Родитель |
| --- | --- |
| `tbl_albums.author_id` | `tbl_authors(id)` |
| `tbl_songs.album_id` | `tbl_albums(id)` |
| `tbl_song_authors.song_id` / `.author_id` | `tbl_songs(id)` / `tbl_authors(id)` |
| `tbl_news.song_id` | `tbl_songs(id)` |
| `tbl_site_playlists.owner_id` | `tbl_site_users(id)` |
| `tbl_site_playlist_items.playlist_id` | `tbl_site_playlists(id)` |
| `tbl_listening_history.site_user_id` | `tbl_site_users(id)` |
| `tbl_song_assignments.assignee_id` | `tbl_site_users(id)` |
| `tbl_song_assignment_drafts.assignee_id` | `tbl_site_users(id)` |
| `tbl_song_share_links.owner_site_user_id` | `tbl_site_users(id)` |
| `tbl_subscriptions.site_user_id` / `.tariff_id` | `tbl_site_users(id)` / `tbl_price_tariffs(id)` |

### 2.2. Чтение SQL NULL в рефлективном загрузчике

`KaraokeDbTable.loadListInternal` читал SQL NULL в колонку, объявленную в
Kotlin как non-null, и передавал `null` в сеттер:
`property.setter.call(entity, null)` → NPE «Parameter specified as non-null is
null: method …setLastAlbumProcessed, parameter <set-?>».

Такой NPE — **не** `PSQLException`, поэтому прежний `catch (_: PSQLException)`
его не перехватывал. Падала загрузка **ВСЕЙ** таблицы `tbl_authors`, а с ней —
весь таргет синхронизации авторов. Далее по цепочке FK падали альбомы
(`tbl_albums.author_id` → `tbl_authors`) и песни (`tbl_songs.album_id` →
`tbl_albums`): автор не доехал → альбом не доехал → песня не доехала.

## 3. Что сделано

### 3.1. Данные (сделано раньше, тем же заходом)

Удалены оставшиеся фикстуры приёмки T079 (песни 30461–30463, альбомы
2886–2887, автор 151, картинки 3713+), которые сами по себе ломали FK. Затем
выполнен санкционированный путь доставки — `POST /api/sync/oneclick` (HTTP 200,
все таргеты `skipped=false`, в логе `sync:remote:ok status=200`).

Проверка на сервере после доставки:

| Таблица | LOCAL | SERVER |
| --- | --- | --- |
| `tbl_songs` | 26 768 | 26 768 |
| `tbl_albums` | 2 852 | 2 852 |
| `tbl_authors` | 134 | 134 |

Поalbum-счётчики совпали: альбомы 2880–2885 — 10 / 10 / 6 / 8 / 1 / 1 песен,
из них с готовым статусом 6 — 0 / 1 / 1 / 0 / 0 / 0. Локально данные не
потеряны.

### 3.2. Код — PR #110 (merge `09a24f52`)

- **Порядок реестра** переупорядочен по FK-графу: `authors → albums →
  pictures → songs → songcoauthors → dictionaries → news`, затем таргеты в
  сторону LOCAL в своём FK-безопасном порядке (`siteusers → siteplaylists →
  siteplaylistitems → … → pricetariffs → subscriptions`). Порядок реестра —
  это очерёдность и в one-click (`ApiController.postSyncOneClick`), и в
  планировщике (`AutoOneClickSyncScheduler`).
- **NULL-безопасный загрузчик**: решение о присвоении вынесено в чистую
  функцию `shouldAssignColumnValue(value, isMarkedNullable)` — `null` в
  nullable-поле пишется как `null`, `null` в not-null-поле оставляет значение
  по умолчанию из конструктора. Ошибка чтения отдельного поля логируется
  (`DB-READ-004`) и не обрушивает загрузку остальных строк.

### 3.3. Код — PR #111 (планер разовой уборки)

Разовая уборка предлагала удалить **19 чужих объектов**: у «Мумий Тролль»,
«Мумий Тролль, T-Fest» и «Мумий Тролль feat. Скриптонит» есть альбом
«Призраки Завтра» — их объекты РАЗНЫЕ записи, а не копии одной. Ключ объекта —
`$автор/$год - $альбом/$имя песни`; переименование **альбома** меняет только
часть пути после `$автор/$год - `, поэтому сверять надо автора и год, а не
альбом. Правка применена и к живым, и к историческим песням.

## 4. Проверка

**Тесты (проверены «до/после» — на старом коде падают):**

- `SyncFkOrderTest` — FK-граф как константа теста; нарушение порядка роняет
  тест. 3 проверки.
- `KaraokeDbTableNullReadTest` — поведенческий тест с замоканным JDBC:
  аномальная строка не роняет загрузку таблицы, остальные строки не теряются.
  5 проверок.
- `OneTimeCleanupStaleCopyAuthorYearTest` — 6 проверок (совпадение при
  переименовании альбома; различие при другом авторе и другом годе; точность
  сравнения года; пустой префикс) + source-contract на применение сверки в
  обеих ветках.

**Проверки:** `:karaoke-app:test` (весь набор) — зелёные; `:karaoke-app:ktlintCheck`
— зелёный; knowledge-гейты (`lint-knowledge`, `check-knowledge-structure`,
`check-feature-doc`, `check-doc-references`) — зелёные; pre-commit (все хуки) —
пройдены; CI: `ci-summary` success, merge через `tools/merge-pr.sh`.

**Знание:** `knowledge/domains/processing/components/two-db-sync.md` (FK-инвариант
порядка реестра, инвариант NULL-обработки),
`knowledge/product/features/sync-fk-safe-order.md` (per-feature, FR-009),
`knowledge/product/features/song-delete-cascade.md` (правило отбора устаревших
копий).

### 4.1. Живая проверка на админ-машине (после деплоя)

Образ `karaoke-app` пересобран из master (`sha256:1ae870b6`), контейнер
пересоздан по Pass 380 (очередь остановлена → активных заданий WORKING/CREATING
нет → контейнер пересоздан → очередь запущена).

Проверка в работающем приложении:

- `/api/sync/entities` — порядок таргетов именно новый: `authors → albums →
  pictures → songs → songcoauthors → dictionaries → news`, затем
  `siteusers → siteplaylists → siteplaylistitems → … → pricetariffs →
  subscriptions`;
- журнал синхронизации: `Запрашиваем таблицу хэшей … (Авторы)` → `(Альбомы)` →
  `(Песни)` — то есть альбомы запрашиваются и применяются раньше песен;
- `POST /api/sync/oneclick` — HTTP 200, **18 таргетов, 0 ошибок**.

**End-to-end на живых данных** (сценарий владельца — новая песня в новом
альбоме): в LOCAL созданы альбом 2890 (автор 133) и песня 30501 (`album_id=2890`,
`id_status=1`). One-click доставил **оба за один проход**: `albums | created:
['ТестСинхронизацииFK (2099)']`, `songs | created: ['ТестСинхронизацииFK']`,
ошибок нет. На сервере: альбом 2890 — 1, песня 30501 — 1, `song.album_id=2890` и
`album.author_id=133` — FK-цепочка цела.

Затем тестовые записи удалены из LOCAL, и штатное зеркальное удаление убрало их
с сервера (`albums | deleted`, `songs | deleted`, ошибок нет). Итог после
проверки: LOCAL = SERVER = 26 768 песен / 2 852 альбома / 134 автора, тестовых
записей нет.

Отдельно проверено, что FK на сервере работает: попытка вставить песню с
`album_id` несуществующего альбома отклонена БД (`tbl_songs_album_id_fkey`) —
то есть защита на месте, а синк теперь просто не создаёт таких ситуаций.

## 5. Что осталось за рамками

- **34 песни владельца имеют `id_status = 1`** — они не опубликованы. Для гостей
  видна только публикация (`id_status >= 6`, спека 013), так что в публичном
  каталоге их не будет, пока владелец не опубликует. Это редакционное решение
  владельца, а не дефект синхронизации. Перед публикацией нужен рендер
  `RENDER_MP4_DEMO` (он есть только у песен 30471 и 30496).
- **KARAOKE-407** — постоянный туннель admin-машина→прод в режиме allow-list:
  по WireGuard серверная БД доступна (10.77.0.2:5433, mTLS), в старом режиме
  канал не работает.
- **KARAOKE-428** — перенос ключей MinIO при переименовании (дефект
  `renameAllArtifacts`).
- **KARAOKE-429** — ревью владельца и закрытие.
