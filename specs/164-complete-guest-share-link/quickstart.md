# Quickstart: Временный полный доступ к песне (валидация)

**Spec**: [./spec.md](./spec.md)
**Data Model**: [./data-model.md](./data-model.md)
**Contracts**: [./contracts/api.md](./contracts/api.md)
**Branch**: `164-complete-guest-share-link`

Этот документ — руководство по ручной проверке фичи end-to-end. Автоматизированных тестов в проекте нет (см. `constitution.md` п. «Рабочий процесс»: «в CI нет, существующие тесты `@Disabled`»). Проверка делается вручную или в production-like окружении.

## Prerequisites

### Локальная среда разработчика

1. Docker-стек поднят: `karaoke-db`, `karaoke-web`, `karaoke-public`, `webvue3`, MinIO, nginx.
2. Применены миграции `38_*.sql` и `39_*.sql`:
   ```bash
   docker exec -i karaoke-db psql -U postgres -d karaoke < deploy/karaoke-db/38_song_share_links.sql
   docker exec -i karaoke-db psql -U postgres -d karaoke < deploy/karaoke-db/39_song_share_recordhash.sql
   ```
   Проверка: `docker exec karaoke-db psql -U postgres -d karaoke -c "\dt tbl_song_share*"` — должно показать 2 таблицы.
3. Собраны jar'ы:
   ```bash
   ./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel
   cd webvue3 && npm install && npm run build && cd ..
   cd karaoke-public && npm install && npm run build && cd ..
   ```
4. В `application.yml` (`karaoke-web`) опционально настроить `karaoke.share.*` секцию (если хотите переопределить дефолты). Текущие дефолты — адекватные.

### Тестовые данные

- Премиум-пользователь (например, ID=42, email `test@example.com`) с подпиской.
- Песня с `id_status >= 6`, готовыми стемами и картинками (например, ID=789).
- Эта же песня не должна быть помечена тегом `SKIP`.

### Браузеры для проверки

- Chrome/Firefox/Safari — основной.
- Chrome в режиме «Инкогнито» — для проверки 2 устройств.
- Мобильный браузер (опционально) — для проверки expiresAtLabel и Share-таргета.

## Сценарий 1 — Создание ссылки премиум-владельцем

**Шаги:**
1. Залогиниться как `test@example.com` в `karaoke-public` (получить `km_auth_token` в localStorage).
2. Открыть `/song?id=789` в браузере.
3. Найти кнопку «Временный доступ» (рядом с ShareButton, иконка 🔗).
4. Нажать — открывается модалка.
5. В модалке выбрать TTL = 24 часа, нажать «Создать ссылку».

**Ожидаемый результат:**
- Модалка показывает URL вида `https://sm-karaoke.ru/share/789/<base64url-секрет>`.
- URL автоматически скопирован в буфер обмена (clipboard).
- В `tbl_song_share_links` появилась строка с `active=true, expires_at=now()+86400, token_hash=SHA256(секрет)`.
- В UI модалки — кнопки «Копировать», «Перевыпустить», «Отозвать».

## Сценарий 2 — Гость открывает ссылку и смотрит плеер

**Шаги:**
1. Скопировать URL из Сценария 1.
2. Открыть новое окно в режиме «Инкогнито» (или другой браузер — без авторизации).
3. Вставить URL в адресную строку, нажать Enter.

**Ожидаемый результат:**
- Открывается лендинг `/share/789/<secret>` — карточка песни с обложкой альбома, обложкой автора, названием, автором, годом.
- В верхней части — бейдж «Доступно до 11.08.2026 22:00» (МСК).
- Кнопка «Открыть плеер» (primary).
- Кнопка «Скопировать ссылку» (secondary).
- Нажать «Открыть плеер».

**Ожидаемый результат (плеер):**
- Открывается `/player/789?share=1&session=<hash>` — полноэкранный плеер.
- Аудио играет, видны субтитры/маркеры текста, аккорды.
- Кнопки «В избранное», «Транспонировать», «Скачать», «Экспорт аудио...» ОТСУТСТВУЮТ или заблокированы overlay «Оформите подписку» (canExport=false).
- В `tbl_song_share_sessions` появилась строка с `opened_at=now(), browser_hash=SHA256('browser:'+browserId), finished_at=null, result=''`.

## Сценарий 3 — Heartbeat продлевает lease

**Шаги:**
1. Из Сценария 2 — НЕ закрывать плеер, оставить на паузе.
2. Подождать 30 секунд.
3. В другом окне проверить `tbl_song_share_links.active_session_lease_until` для нашей ссылки.

**Ожидаемый результат:**
- `active_session_lease_until` обновлён (было ~now()+90s, после heartbeat стало ~now()+90s заново).
- `tbl_song_share_sessions.last_seen_at` тоже обновлено.

## Сценарий 4 — Закрытие вкладки освобождает lease

**Шаги:**
1. Из Сценария 2 — закрыть вкладку с плеером.
2. Подождать 5 секунд.
3. Проверить `tbl_song_share_sessions.finished_at` и `result`.

**Ожидаемый результат:**
- `finished_at` ≈ момент закрытия вкладки (плюс 1-2 сек на sendBeacon).
- `result = 'closed'`.
- `tbl_song_share_links.active_session_*` обнулены.

## Сценарий 5 — Истечение lease без heartbeat (sweeper)

**Шаги:**
1. Создать ссылку, открыть плеер.
2. Остановить браузер процесс (`kill -STOP <PID>`) или выключить JavaScript console (пауза >90 сек без отправки heartbeat).
3. Подождать 1 sweep-цикл (60 сек).
4. Проверить `tbl_song_share_sessions`.

**Ожидаемый результат:**
- `finished_at = active_session_lease_until` (момент, когда lease формально истёк).
- `result = 'timeout'`.
- `active_session_*` обнулены.

## Сценарий 6 — Отзыв владельцем

**Шаги:**
1. В окне владельца (из Сценария 1) — нажать «Отозвать» → подтвердить.
2. В окне гостя (из Сценария 2) — следующий heartbeat (через ≤25 сек) вернёт 410.

**Ожидаемый результат:**
- Плеер показывает overlay «Ссылка отозвана. Попросите владельца прислать новую» с кнопкой «Закрыть».
- Аудио на паузе.
- `tbl_song_share_sessions.result = 'revoked'`.
- `tbl_song_share_links.active=false, revoke_reason='manual'`.

## Сценарий 7 — Лимит 2 устройств

**Шаги:**
1. Создать ссылку, открыть в Chrome (1 устройство).
2. Открыть ту же ссылку в Firefox (2 устройство) — оба работают.
3. Открыть ту же ссылку в Safari (3 устройство) → ожидаем `409 share.concurrentLimit`.

**Ожидаемый результат:**
- 3-е устройство видит заглушку «Уже открыто максимум устройств (2)».
- 1-е и 2-е продолжают работать (lease не отзывается).

## Сценарий 8 — Лимит перевыпусков

**Шаги:**
1. 4 раза подряд перевыпустить ссылку на одну и ту же песню в течение часа (с интервалом 1 минута).

**Ожидаемый результат:**
- 1-3 перевыпуска — успешны.
- 4-й — `429 share.linkAlreadyActive` с `reason='max_reissues_per_song_per_hour', limit=3`.

## Сценарий 9 — Потеря премиума владельцем (sweeper)

**Шаги:**
1. Создать ссылку (премиум активен).
2. Снять премиум у владельца (например, через `tbl_site_users.is_premium = false`).
3. Подождать 1 sweep-цикл (60 сек).
4. Проверить `tbl_song_share_links`.

**Ожидаемый результат:**
- `active=false, revoke_reason='premium_lost'`.
- Все активные lease-сессии закрыты (`result='revoked'`).

## Сценарий 10 — Песня помечена SKIP (sweeper)

**Шаги:**
1. Создать ссылку (песня без SKIP).
2. Поставить песне тег SKIP (`tbl_songs.tags = 'SKIP'`).
3. Подождать 1 sweep-цикл.
4. Проверить `tbl_song_share_links`.

**Ожидаемый результат:**
- `active=false, revoke_reason='song_unavailable'`.

## Сценарий 11 — Админ просматривает ссылки

**Шаги:**
1. Залогиниться в `webvue3` как editor (`isEditor=true`).
2. Открыть список site-users, выбрать `test@example.com`.
3. Нажать «Временный доступ» (или аналогичная кнопка, открывающая `UserShareLinksModal`).

**Ожидаемый результат:**
- Таблица ссылок пользователя с полями «Песня», «Создана», «Истекает», «Открытий», «Отказов», «Причина».
- Вкладки «Активные (N)» / «Завершённые (M)».
- По кнопке «Сессии» — раскрывается список `tbl_song_share_sessions`.
- Кнопка «Отозвать» для активных.

## Сценарий 12 — TTL 7 дней

**Шаги:**
1. В модалке создания ссылки проверить наличие варианта «7 дней».
2. Выбрать «7 дней», создать.

**Ожидаемый результат:**
- В `tbl_song_share_links.expires_at` — `now() + 604800 секунд`.
- В модалке — бейдж «Доступно до 17.08.2026 22:00».

## Сценарий 13 — F5 в плеере сохраняет сессию

**Шаги:**
1. Открыть плеер гостя (из Сценария 2).
2. Нажать F5 (перезагрузка страницы).

**Ожидаемый результат:**
- Плеер перезагружается, но НЕ редиректит на `/` (новое поведение — раньше редиректил).
- Аудио продолжает играть (или ставится на паузу сразу).
- sessionTokenHash сохранился в `sessionStorage['kp_share_session_789']`.

## Сценарий 14 — Закрытие и новое открытие вкладки

**Шаги:**
1. Открыть плеер гостя.
2. Закрыть вкладку.
3. Подождать 5 секунд.
4. Открыть плеер снова по тому же URL `/player/789?share=1&session=<hash>`.

**Ожидаемый результат:**
- Плеер НЕ открывается (или открывается и сразу 410 → overlay).
- Новая попытка `claim` с того же browserId должна сработать (если лимит не исчерпан).
- В `tbl_song_share_sessions` — две записи: старая `result='closed'` и новая `result=null`.

## Команды для проверки БД

```bash
# Все активные ссылки
docker exec karaoke-db psql -U postgres -d karaoke -c \
  "SELECT id, owner_site_user_id, song_id, active, expires_at, revoke_reason, sessions_total FROM tbl_song_share_links WHERE active=true ORDER BY created_at DESC LIMIT 10;"

# Активные lease
docker exec karaoke-db psql -U postgres -d karaoke -c \
  "SELECT id, share_link_id, song_id, browser_hash, opened_at, finished_at, result FROM tbl_song_share_sessions WHERE finished_at IS NULL ORDER BY opened_at DESC LIMIT 10;"

# Sweeper-кандидаты на отзыв (premium_lost / song_unavailable)
docker exec karaoke-db psql -U postgres -d karaoke -c \
  "SELECT l.id, l.song_id, l.owner_site_user_id, u.is_premium, u.is_permanent_premium, u.sponsr_premium_until, u.site_premium_until
   FROM tbl_song_share_links l
   JOIN tbl_site_users u ON u.id = l.owner_site_user_id
   WHERE l.active=true;"
```

## Метрики успеха (см. SC в спеке)

| SC | Критерий | Как проверить |
|---|---|---|
| SC-001 | Открытие плеера за <5 сек | Секундомер от клика по ссылке до первого аудио-кадра |
| SC-002 | 10 минут непрерывной работы | Открыть, оставить на 10 мин, проверить `last_seen_at` обновляется |
| SC-003 | `finished_at` за ≤5 сек после закрытия | Сценарий 4 |
| SC-004 | 410 на heartbeat после revoke | Сценарий 6 |
| SC-005 | Admin загрузка за <2 сек | Сценарий 11 |
| SC-006 | Sweeper 1000 ссылок за <5 сек | Сгенерировать 1000 ссылок через bash, дождаться тика |
| SC-007 | 0 ошибок 500 | `grep "500" logs/karaoke-web.log` |
| SC-008 | Старые секреты после reissue → 404 | Сценарий 1, потом перевыпустить, попробовать старый URL |
| SC-009 | Лимит активных ссылок | Сценарий 8 (или попробовать создать 6 на разные песни) |
| SC-010 | Rate-limit на claim | 11 запросов `/claim` за минуту с одного IP |
