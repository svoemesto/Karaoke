# Quickstart — Единая трактовка дат share-ссылок

**Feature**: `166-fix-share-link-timezone`
**Branch**: `166-fix-share-link-timezone`

Руководство по локальной валидации после применения изменений. Все сценарии
повторяют User Stories из `spec.md` и Success Criteria. Подходит как
разработчику, так и при ручном приёмочном тестировании.

## 1. Подготовка

### Машина

- Linux/macOS, JDK 17, Node 22 LTS, Docker, docker-compose.
- `karaoke-web` и `karaoke-public` собираются локально.
- База данных `karaoke-db` поднята; миграции `38_song_share_links.sql` и
  `39_song_share_recordhash.sql` применены.

### Сценарий «без перевыпуска JVM TZ»

```bash
# Проверить, что JVM TZ не зависит от системы (FR-014):
TZ=America/New_York ./gradlew :karaoke-web:test --tests com.svoemesto.karaokeweb.services.SongShareLinkDateTimeTest
# Все тесты должны быть зелёными.
```

### Запуск бэкенда

```bash
./gradlew karaoke-web:bootJar
docker-compose -f deploy/docker-compose.yml up -d karaoke-web
```

### Запуск фронтов

```bash
cd karaoke-public && npm install && npm run dev   # порт 5173
cd webvue3 && npm install && npm run dev           # порт 5174
```

## 2. Сценарии ручной проверки

### S1. Создать ссылку — верный срок (US1, SC-001, SC-002)

1. Открыть `http://localhost:5173/song/12345` (Московское устройство, TZ=Europe/Moscow).
2. Войти как премиум-пользователь.
3. Нажать «Временный доступ к песне».
4. Выбрать «1 час», нажать «Создать и скопировать».
5. Запомнить отображаемый срок.
6. Проверить в БД:

```sql
SELECT id, expires_at, created_at FROM tbl_song_share_links
WHERE owner_site_user_id = (ваш id) AND song_id = 12345 AND active;
```

**Ожидание**: если экран показал «11.08.2026 09:57», то в БД
`expires_at = '2026-08-11 09:57:…'` (МСК). Разница — 0 минут.

### S2. Повторное открытие модалки (US2, SC-004)

1. Закрыть модалку.
2. Перезагрузить страницу (F5).
3. Снова нажать «Временный доступ к песне».
4. Сравнить срок с тем, что был в S1.

**Ожидание**: сроки совпадают.

### S3. Другой часовой пояс (US1, US4, SC-003)

1. В `karaoke-public` установить системный TZ:

```bash
TZ='Asia/Vladivostok' npm run dev
```

   Или в DevTools браузера: `Override` → `Asia/Vladivostok`.

2. Создать новую ссылку.
3. Сравнить срок с БД.

**Ожидание**: на устройстве во Владивостоке `expires_at='2026-08-11 09:57:36'`
(МСК) отображается как «11.08.2026 16:57» (МСК+7).

### S4. Админ-таблица (US3, SC-001, SC-005)

1. Перейти в `webvue3` (`http://localhost:5174/admin/site-users`).
2. Открыть карточку нужного пользователя.
3. Нажать «Временный доступ к песне» (или «Share links»).
4. Сравнить столбцы таблицы «Создана», «Истекает», «Редакции/Открытий»
   со значениями в БД.

**Ожидание**: все даты, показанные на МСК-устройстве, совпадают с БД.

5. Открыть вкладку «Сессии» по конкретной ссылке.
6. Сравнить «Открыто», «Завершено» с `tbl_song_share_sessions`.

**Ожидание**: совпадают.

### S5. Срок истёк (US4, SC-006)

1. Подождать, пока `expires_at` ссылочной ссылки уйдёт в прошлое
   (или создать ссылку с TTL=60 сек и подождать).
2. Открыть модалку владельца.

**Ожидание**: «Срок ссылки истёк (ДД.ММ.ГГГГ ЧЧ:ММ)».

3. По URL-у гостя открыть `/share/{id}/{secret}`.

**Ожидание**: «Срок действия этой ссылки истёк. Попросите владельца
прислать новую.».

### S6. Прочерк для пустых дат (FR-009, US3)

1. Открыть админ-таблицу, неотозванную, неиспользованную ссылку.
2. Посмотреть столбцы «Отозвана», «Перв. использ.».

**Ожидание**: прочерк, а не «01.01.1970 03:00».

### S7. Защита от регрессии (SC-005, SC-008)

```bash
# Server: ровно одна точка сдвига -3ч или +3ч должна остаться либо
# внутри JVM-таймзоны (ENV TZ="Europe/Moscow"), либо быть удалена.
grep -rn "3 \* 3600" karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt
grep -rn "formatMskLabel" karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
grep -rn "EXTRACT(EPOCH FROM" karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/ | grep -v "AT TIME ZONE"
```

**Ожидание**: пустой вывод во всех трёх случаях.

```bash
# Front: те же проверки.
grep -rn "3 \* 3600" karaoke-public/src/components/ShareLinkModal.vue
grep -rn "3 \* 3600" webvue3/src/components/SiteUsers/UserShareLinksModal.vue
```

**Ожидание**: пусто.

### S8. Тесты (FR-012, SC-007, SC-008)

```bash
# Server:
./gradlew :karaoke-web:test --tests com.svoemesto.karaokeweb.services.SongShareLinkDateTimeTest

# Front public (Node 22 --test, без зависимостей):
node --test karaoke-public/src/utils/__tests__/dateFormat.test.js

# Front admin (тоже Node --test):
node --test webvue3/src/utils/__tests__/dateFormat.test.js
```

**Ожидание**: все три теста зелёные. Проверка в немосковском TZ:

```bash
TZ='Asia/Vladivostok' node --test karaoke-public/src/utils/__tests__/dateFormat.test.js
TZ='America/New_York' node --test webvue3/src/utils/__tests__/dateFormat.test.js
```

**Ожидание**: те же три зелёные — тесты не зависят от TZ машины.

## 3. Контрольные числа (golden data)

Чтобы избежать машинозависимости, golden-числа для тестов:

| Поле | Эпоха (epoch ms) | Что это |
|------|------------------|---------|
| `expires_at='2026-08-11 09:57:36'` (МСК) | `1786431456000` | реальный момент |
| `created_at='2026-08-11 08:57:36'` (МСК) | `1786427856000` | реальный момент |
| `expires_at - created_at = 1h` | `3600000` | TTL=1 час |

Тесты сверяют, что:
- `LocalDateTime.ofInstant(Instant.ofEpochMilli(1786431456000), ZoneId.of("Europe/Moscow"))`
  = `2026-08-11T09:57:36`.
- `new Date(1786431456000).toLocaleString('ru-RU', { timeZone: 'Europe/Moscow', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })`
  = `'11.08.2026 09:57'`.
- `new Date(1786431456000).toLocaleString('ru-RU', { timeZone: 'Asia/Vladivostok', ... })`
  = `'11.08.2026 16:57'`.

## 4. Что откатить, если что-то пошло не так

Все изменения локализованы в одном сервисе и трёх фронт-компонентах:

```bash
git diff --stat 166-fix-share-link-timezone master
# Должно показать:
#   karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt
#   karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicShareController.kt
#   karaoke-public/src/views/ShareView.vue
#   karaoke-public/src/components/ShareLinkModal.vue
#   webvue3/src/components/SiteUsers/UserShareLinksModal.vue
#   + новые тестовые файлы
```

Если что-то не работает — откат ветки `166-fix-share-link-timezone` без
последствий для прода: прод-контейнер `karaoke-web` ещё на старой версии.
