# Quickstart: фича 131 — валидация пайплайна approve

> **Статус**: Phase 1 (design). Этот документ — **руководство по
> end-to-end-проверке фичи на dev-машине**. Он не содержит кода
> реализации или миграций — только сценарии проверки и ожидаемые результаты.
>
> **Целевая машина**: `dev-pc` под пользователем `dev` (здесь допустимо
> пересобирать контейнер `karaoke-app` локально, см. AGENTS.md → «Разрешено»).

## Содержание

1. [Предусловия](#1-предусловия)
2. [Подготовка тестовой песни](#2-подготовка-тестовой-песни)
3. [Сценарии проверки](#3-сценарии-проверки)
   - 3.1 [S-001 Happy path: новый approve — DEMO + Telegram + sync + новость](#31-s-001-happy-path)
   - 3.2 [S-002 Идемпотентность: повторный approve](#32-s-002-идемпотентность-повторный-approve)
   - 3.3 [S-003 Идемпотентность: ручной «Рендер MP4 DEMO» поверх approve](#33-s-003-идемпотентность-ручной-рендер-mp4-demo-поверх-approve)
   - 3.4 [S-004 Параллельный approve + ручной триггер Telegram](#34-s-004-параллельный-approve--ручной-триггер-telegram)
   - 3.5 [S-005 Сбой Telegram-публикации](#35-s-005-сбой-telegram-публикации)
   - 3.6 [S-006 Сбой sync-related](#36-s-006-сбой-sync-related)
   - 3.7 [S-007 Сбой рендера](#37-s-007-сбой-рендера)
   - 3.8 [S-008 `telegramAutoPublishEnabled=false`: рендер+sync идут, Telegram — нет](#38-s-008-telegramautopublishenabledfalse)
   - 3.9 [S-009 Idempotency гарда по активному процессу](#39-s-009-idempotency-гарда-по-активному-процессу)
4. [Быстрая самопроверка после деплоя](#4-быстрая-самопроверка-после-деплоя)
5. [Чек-лист ошибок, требующих расследования](#5-чек-лист-ошибок-требующих-расследования)

---

## 1. Предусловия

| Компонент | Как проверить | Критерий готовности |
|---|---|---|
| Локальный `karaoke-app` запущен и доступен | `curl http://localhost:8080/api/version` | HTTP 200, JSON-ответ `{"version":"..."}`. |
| БД LOCAL подключена | в логах `karaoke-app` | строка «Установлено соединение с базой данных LOCAL». |
| БД SERVER (prod) доступна из admin-машины | `curl https://svoemesto.org/` | 200 (только проверка сети). |
| MinIO содержит стемы для тестовой песни | браузером `http://localhost:9001` зайти в бакет `karaoke` | `<author>/<year> - <album>/<songname>.<stem_ext>` существуют. |
| Telegram-канал доступен боту | `curl 'https://api.telegram.org/bot<TOKEN>/getMe'` | возвращает username бота. |
| `TelegramAutoPublish` конфигурация | `tbl_settings` -> `telegram_auto_publish_enabled = true` | соответствует. Если нет — S-008. |
| Старая новость «в коллекции» для тестовой песни удалена (или тестовая песня новая) | `SELECT id FROM tbl_news WHERE id_song=? AND category='premium'` | 0 строк. |

## 2. Подготовка тестовой песни

```text
ID = 1                                // любой id из tbl_songs
author = "Гражданская Оборона"        // уже есть в tbl_authors
year = 1989
album = "Всё идёт по плану"           // уже есть в tbl_albums
fileName = "Харакири"                  // файл стемов в MinIO есть
current id_status ∈ {3, 4, 5}         // ещё не READY
news_available_announced = false       // ещё не публиковалось
date_time_publish = "<вчерашний день или текущий момент>"   // чтобы не "опоздавшая"
tags = ""                              // без SKIP
```

> Если взять песню с `news_available_announced=true` — новость на сервере не появится
> (нет перехода). См. `docs/features/stats.md` о флаге и
> `specs/101-song-news-flag/spec.md` FR-004.

---

## 3. Сценарии проверки

### 3.1 S-001 Happy path

**Условие**: фича развёрнута, Telegram бот жив, sync ходит, рендер проходит
нормально.

**Действия**:

1. Зайти в админку `/subs-edit?id=<ID>`.
2. Поправить 1-2 маркера (опционально — для имитации обычной approve-работы).
3. Нажать «Одобрить» (`POST /editor/song/approve?id=<ID>`).
4. Дождаться ответа (должен быть `{"ok":true,"status":"approved"}` за ≤5 с).
5. Открыть вкладку `/processes`. Должна появиться строка с
   `process_type='RENDER_MP4_DEMO'`, `song_id=<ID>`, статус сначала
   `WAITING`, потом `WORKING`, потом `DONE`.
6. Через 1-10 минут (зависит от длины трека) дождаться `DONE`.
7. Открыть Telegram-канал. Должен появиться пост с коротким превью песни
   и пометкой «ДЕМО» (шаблон фичи 113).
8. На публичной главной `/news` (или в Vuex-сторе `stats.newsBadge`)
   должна появиться новость «Песня появилась в коллекции: ...».
9. На публичной странице `/song/<ID>` обновлена обложка исполнителя/альбома
   (если раньше отличалась — это значит, что sync `tbl_pictures/authors/albums`
   сработал).

**Ожидание успеха**: все 9 пунктов, по порядку.

**Где наблюдать артефакты**:

- Логи `karaoke-app`:
  - `[approve/timing] push на SERVER: …` (existing).
  - `[approve/sync-related] push related на SERVER: …, created=X updated=Y` (NEW).
  - `[render-demo/post-hook] …` или просто `sendVideo OK` (NEW).
- `tbl_news` (`SELECT id, category, title FROM tbl_news WHERE id_song=<ID>`):
  - ровно 1 новость с `category='premium'`, заголовок по шаблону 101.
- `tbl_songs` (`SELECT id, id_telegram_demo, player_readiness_flags FROM tbl_songs WHERE id=<ID>`):
  - `id_telegram_demo` непустой.
  - `telegram_auto_publish_state = "published"`.

### 3.2 S-002 Идемпотентность: повторный approve

**Действия**:

1. После завершения S-001 (песня одобрена, опубликована, новость создана)
   нажать «Одобрить» ещё раз.

**Ожидание**:

- HTTP-ответ `{ok:true, status:"already_approved"}` (existing 094).
- В `/processes` НЕ появилась новая строка `RENDER_MP4_DEMO` (гард сработал
  — если процесс из S-001 ещё не удалён и не завершился с ошибкой, гард
  видит существующий и skip).
- В Telegram НЕ появился повторный пост (`idTelegramDemo` уже заполнен,
  `publishToTelegram` early-return).
- На главной НЕ появилась вторая новость (`detectAndAnnounceAvailability`
  early-return по `wasAvailableBefore=true`).

### 3.3 S-003 Идемпотентность: ручной «Рендер MP4 DEMO» поверх approve

**Действия**:

1. После S-001 (всё успешно).
2. На странице `/song-edit?id=<ID>` нажать «Рендер MP4 DEMO»
   (`POST /song/renderMp4Preview` — это уже существующая кнопка).

**Ожидание**:

- Появилась **новая** задача `RENDER_MP4_DEMO` в `/processes` (ручной
  триггер не имеет нашего гарда — он работает по своим правилам).
- После завершения `DONE` Telegram снова НЕ публикует (уже опубликовано).
- Sync-related повторно делает diff — без изменений (то, что уже синк'нуто,
  не пушится; recordhash не менялся).

### 3.4 S-004 Параллельный approve + ручной триггер Telegram

**Действия**:

1. До approve: убедиться, что `tbl_processes` для `<ID>` нет активного
   `RENDER_MP4_DEMO`.
2. Нажать «Одобрить».
3. Сразу (не дожидаясь) параллельно нажать «Опубликовать в Telegram»
   (`POST /song/publishtotelegram`) для той же песни.

**Ожидание**:

- В `/processes` появилась ровно 1 задача `RENDER_MP4_DEMO` (гард из approve
  пропустил 2-ю попытку из ручного триггера, или наоборот — ровно одна
  из двух попыток создала задачу).
- В Telegram — ровно 1 пост.
- На главной — ровно 1 новость.

### 3.5 S-005 Сбой Telegram-публикации

**Условие**: телеграм-бот неправильно настроен (например, невалидный
`telegramBotToken` в `tbl_settings`).

**Действия**:

1. Подменить `tbl_settings.telegram_bot_token` на «invalid» (на dev-машине).
2. Approve произвольной новой песни.

**Ожидание**:

- DEMO-MP4 успешно отрендерен (`DONE`).
- Telegram не опубликован (HTTP 401 от Telegram → `TelegramAutoPublishState.SEND_FAILED`,
  `tbl_songs.telegram_auto_publish_state='send_failed'`,
  `tbl_songs.news_premium_telegram_sent=true` (?) — поведение зависит от
  Фазы 2, проверить код).
- Sync на сервер прошёл (sync-related не зависит от Telegram).
- Новость на сервере появилась.
- На стороне `karaoke-app` ошибка видна в логах (`TelegramApiClient.send fail`).
- Админ видит UI-индикатор «не отправлено в Telegram», может нажать
  «Опубликовать в Telegram сейчас» после восстановления токена.

### 3.6 S-006 Сбой sync-related

**Условие**: подключение к PROD-БД (`PROD_HOST`) недоступно.

**Действия**:

1. Заблокировать `188.119.64.111` через `/etc/hosts` (`<IP> svoemesto.org` →
   `<IP> 0.0.0.0`).
2. Approve песни.

**Ожидание**:

- DEMO-MP4 успешно отрендерен.
- Approve вернул `{"ok":true}` без задержки (sync в `thread`).
- В логах `[approve/sync-related] ошибка sync related: ...`.
- На сервере `tbl_pictures/authors/albums` НЕ обновлены, `tbl_songs` —
  обновлена (existing `updateRemoteSongFromLocalDatabase` бросит, но
  approve всё равно `ok:true`).
- Telegram-публикация прошла (она не зависит от sync-related; post-hook
  выполняется после `DONE`).
- После восстановления сети — нажать «Обновить на сервере» вручную
  (`POST /utils/updateremotedatabasefromlocaldatabase`), чтобы добать
  related-таблицы.

### 3.7 S-007 Сбой рендера

**Условие**: подсунуть в MinIO «битый» mp3-стем для песни (или удалить).

**Действия**:

1. Approve песни с битым стемом.
2. Дождаться окончания `RENDER_MP4_DEMO` (5-10 мин).

**Ожидание**:

- В `/processes` строка со статусом `ERROR`, `priority=-1`.
- Логи содержат ошибку рендера (ffmpeg/demucs).
- Telegram НЕ опубликован (наш пост-хук **не** срабатывает на `ERROR`).
- Sync-related прошёл.
- На главной появилась новость «в коллекции».
- Telegram-пост опубликуется позже при ручном триггере
  «Рендер MP4 DEMO» + «Опубликовать в Telegram сейчас» — после
  восстановления стема.

### 3.8 S-008 `telegramAutoPublishEnabled=false`

**Условие**: `tbl_settings.telegram_auto_publish_enabled = false`.

**Действия**:

1. Approve песни.

**Ожидание**:

- DEMO-MP4 успешно отрендерен (`approve` создал задачу безусловно, гард
  на `telegramAutoPublishEnabled` находится только в `publishToTelegram`).
- `tbl_songs.telegram_auto_publish_state` остаётся `'rendering'` или
  переходит в `'publishing'`, но `id_telegram_demo` остаётся пустым
  (`publishToTelegram` возвращает `SCHEDULED` сразу — бот не активен).
- Sync-related прошёл.
- Новость на сервере появилась.
- В Telegram поста нет.
- На dev-машине в логах: `[render-demo/post-hook] publishToTelegram state=scheduled` (или подобное).

> **Замечание**: spec FR-012 говорит «telegramAutoPublishEnabled управляет только
> публикацией в Telegram, не блокирует рендер/sync». Этот сценарий — его
> проверка.

### 3.9 S-009 Idempotency гарда по активному процессу

**Условие**: преднамеренно создать «зависший» `RENDER_MP4_DEMO`-процесс.

**Действия**:

1. Вставить вручную в `tbl_processes` строку
   `(song_id=<ID>, process_type='RENDER_MP4_DEMO', process_status='WORKING',
   start=NOW(), priority=5, thread_id=0)` (на dev-машине SQL-запросом).
2. Approve этой же песни.

**Ожидание**:

- В `/processes` НЕ появилась новая строка `RENDER_MP4_DEMO` для этой песни.
- В логах approve — нет `[approve/render-demo] создан процесс` (или
  аналогичного лога создания).
- Approve вернул `ok=true`.
- Если затем удалить «зависший» процесс (`DELETE FROM tbl_processes WHERE id=<HUNG>`)
   и снова нажать approve — создастся новая задача.

---

## 4. Быстрая самопроверка после деплоя

Если вы тестируете на dev-машине (и имеете право пересобирать
`karaoke-app`), после `bash deploy/do.sh build_app && do.sh start`:

```text
1. curl http://localhost:8080/api/version                                # sanity
2. SQL: SELECT name FROM karaoke_properties WHERE name='approvePipelineTriggerDemoRender'  # внутренний проперти, если добадим
3. /subs-edit -> одобрить тестовую песню                              # S-001
4. Через ≤10 мин: /processes -> RENDER_MP4_DEMO DONE                  # SC-001
5. Через ≤1 мин после DONE: Telegram пост                             # SC-002
6. На главной /news: новость «появилась в коллекции»                  # SC-004
```

Если все 6 пунктов успешны — фича отрабатывает happy path.

---

## 5. Чек-лист ошибок, требующих расследования

| Симптом | Вероятная причина | Что смотреть |
|---|---|---|
| Approve зависает >5 с | sync-related блокирует HTTP-выход (не ушёл в `thread`) | логи approve — есть ли `[approve/sync-related]` ДО HTTP-ответа. |
| Approve OK, но `tbl_processes` без `RENDER_MP4_DEMO` | Гард по активному процессу пропустил, а в БД процесса нет (например, race-condition). | `SELECT * FROM tbl_processes WHERE song_id=<ID> AND process_type='RENDER_MP4_DEMO'`. |
| Telegram не публикуется после `DONE` | Пост-хук не сработал, или `publishToTelegram` вернул `SEND_FAILED` (невалидный токен). | Логи: должен быть `[render-demo/post-hook]`; `state` в `tbl_songs.telegram_auto_publish_state`. |
| Sync-related пишет `created=0 updated=0` | На LOCAL нет изменений в related-таблицах с момента прошлого sync (норма). | `SELECT * FROM tbl_songs JOIN tbl_settings_sync ...` — diff хэшей. |
| Новость на сервере не появилась | `wasAvailableBefore=true` (флаг уже был до approve). | `SELECT player_readiness_flags FROM tbl_songs WHERE id=<ID>` на LOCAL и на SERVER. |
| В логах approve — старые строки `[approve/timing]`, нет новых | Контейнер `karaoke-app` не пересобран после фичи. | `git log` в `karaoke-app` — есть ли коммит фичи; `do.sh build_app` + `do.sh restart karaoke-app`. |
