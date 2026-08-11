# Research — Единая трактовка дат share-ссылок

**Feature**: `166-fix-share-link-timezone`
**Branch**: `166-fix-share-link-timezone`
**Дата**: 2026-08-11

## 1. Воспроизведение дефекта в числах

Чтобы зафиксировать модели, на которые опирается план, ниже — полный путь от строки
в БД до пикселя на экране для конкретного случая из отчёта.

**Вход:** `tbl_song_share_links.expires_at = '2026-08-11 09:57:36'` (naive, источник
правды — МСК; записано `timestamp without time zone`).

| Слой | Что происходит | Числовое значение | Что видит пользователь |
|------|----------------|-------------------|------------------------|
| Postgres `EXTRACT(EPOCH FROM expires_at) * 1000` | naive трактуется как UTC | `1786442256000` | (промежуточное) |
| Postgres `EXTRACT(EPOCH FROM expires_at AT TIME ZONE 'Europe/Moscow') * 1000` | реальный момент | `1786431456000` | (промежуточное) |
| `SongShareLinkService.OwnerLinkView.expiresAt` (Long) | первый запрос | `1786442256000` | (промежуточное) |
| `SongShareLinkService.OwnerLinkView.expiresAtMs` (Long) | второй запрос | `1786431456000` | (промежуточное) |
| `SongShareLinkService.formatMskLabel(1786442256000)` | `LocalDateTime.ofEpochSecond(1786442256, 0, ZoneOffset.UTC)` → `2026-08-11T09:57:36` → `MSK_LABEL_FORMATTER.format(...)` | строка `"11.08.2026 09:57"` | показано пользователю «09:57» (формально верно для этого epoch ms) |
| `new Date(1786442256000).toLocaleString('ru-RU')` (Москва) | JS видит 09:57 UTC, переводит в МСК+3 | строка `"11.08.2026, 12:57"` | +3 часа |
| `new Date(1786431456000).toLocaleString('ru-RU')` (Москва) | JS видит 06:57 UTC, переводит в МСК+3 | строка `"11.08.2026, 09:57"` | **правильно** |

**Корневая причина «−3 часа» в отчёте** — `expiresAt` (Long) на бэке возвращает
`EXTRACT(EPOCH FROM naive_as_UTC) * 1000` = +3ч сдвинутое значение, и при этом
**рядом** существует `expiresAtMs` (Long) с реальным моментом. Два поля с одним
названием-префиксом, разным смыслом — источник всех багов. Конкретные проявления
в UI:

- `UserShareLinksModal.vue` (админка): `formatDate(s.openedAt)` где `openedAt`
  = +3ч сдвинутое значение → на МСК-устройстве показывает «12:57» вместо «09:57».
  **Это главный наблюдаемый баг.**
- `ShareLinkModal.vue` (владелец): `expiresAtLabel = formatMskLabel(expiresAt)`
  — `formatMskLabel` интерпретирует `expiresAt` как UTC и форматирует без зоны,
  получает «09:57». Формально правильно для этого epoch ms, но не потому что
  верен код, а потому что +3ч сдвиг компенсируется интерпретацией «как UTC».
  В JS-`isExpired` стоит явный костыль `link.expiresAt - 3*3600*1000 <= Date.now()`.
- `ShareView.vue` (гость): `expiresAtLabel` (отсутствует в текущей выдаче бэка
  → пустая строка), `isExpired` опирается на 0 → «бессрочно».

`expiresAtMs` (через `AT TIME ZONE 'Europe/Moscow'`) — это правильный реальный момент,
но во фронт он сейчас не попадает как самостоятельная временная метка; попытка
задействовать его в `ShareLinkModal.vue:165-170` (`isExpired`) — единственное место, а
для отображения метка всё равно берётся из `expiresAtLabel`. Помимо этого, в
`UserShareLinksModal.vue` поля `*At` (без `Ms`) тоже сдвинуты, и
`formatDate(s.openedAt)` даёт «12:57» для МСК-устройства (та же ошибка, но без
параллельного `*Ms`).

## 2. Решения, требующие обоснования

### 2.1 Формат передачи даты (Q1 → A)

**Decision**: единственное числовое поле с историческим именем (например,
`expiresAt`) = реальный момент времени (epoch ms); поля `*Ms` и `*Label` удаляются.

**Rationale**:
- Это устраняет «два числа с одинаковым именем, разным смыслом» — корень проблемы.
- Хранение в БД остаётся неизменным: `timestamp without time zone` со значением
  в МСК. Семантика имени поля меняется, но DDL не трогаем → `recordhash` остаётся
  стабильным → `tbl_song_share_links` остаётся консистентной (см. Assumption #3).
- Исторические имена полей оставлены, чтобы:
  - не менять контракт полностью (минимум diff);
  - упростить точечный `@JsonProperty` (если бы потребовался) — но их нет,
    JSON-сериализация Kotlin data class использует имена полей как есть.

**Alternatives considered**:
- `expiresAtMs` оставить, `expiresAt` сделать реальным моментом — создаёт два
  поля с одинаковым смыслом, что и есть исходный дефект в более узком виде.
- `expiresAt` оставить как «метка naive как UTC» (статус-кво) — не решает дефект.
- Полная замена имени на `expiresAtEpoch` / `expiresAtUtcMs` — больше diff,
  никаких преимуществ.

### 2.2 Источник часового пояса (Q2 → A)

**Decision**: на всех серверных операциях (запись, чтение, перевод) `ZoneId.of("Europe/Moscow")`
объявляется в коде, а не берётся из `ZoneId.systemDefault()` или JVM TZ.

**Rationale**:
- `ENV TZ="Europe/Moscow"` в `docker-compose` для `karaoke-web` сейчас выставляет
  JVM TZ = Europe/Moscow, что **скрывает** проблему записи: `Timestamp(epochMs).toString()`
  в МСК-TZ даёт «09:57» и БД получает «09:57». Но это побочный эффект окружения.
  Без явного `ZoneId.of("Europe/Moscow")` код сломается на машине разработчика
  без этой ENV (например, macOS с TZ=Europe/Berlin).
- Алгоритмический, не конфигурационный, источник правды — нужен для тестов
  на машинах в любом поясе (FR-012, FR-014).

**Alternatives considered**:
- `@PostConstruct` инициализация JVM TZ = `Europe/Moscow` — глобальный side-effect
  на весь процесс, опасно для других фич.
- ENV-переменная `APP_TIMEZONE` — плодит конфиг; нет оснований усложнять.

### 2.3 Запись vs синхронизация (Q3 → A)

**Decision**: запись проверяется на реальных данных; правится только при расхождении;
триггер `recordhash` и `SyncRegistry` не трогаются.

**Rationale**:
- По чтению кода (`createLink`, `revokeLink`, `tryClaim`, `release`, `heartbeat`)
  запись дат идёт либо через `now()` (DB-сторона, время = `LOCAL TIMESTAMP` в TZ
  сессии Postgres), либо через `setTimestamp(Timestamp(epochMs))` (JVM-сторона).
- Семантика записи в МСК зависит от TZ сессии Postgres и JVM TZ. На проде обе
  считаются `Europe/Moscow` (Dockerfile `ENV TZ="Europe/Moscow"` + явный
  `?timezone=Europe/Moscow` в JDBC URL — нужно проверить). Если это так — записи
  уже корректны, объём работы сокращается до чтения и отображения.
- **Если проверка обнаружит, что в БД попадает UTC**, а не МСК — потребуется
  явная запись через `LocalDateTime.toString()` + `setString(...)` или
  `setObject(..., LocalDateTime)`. Это пере-формулирует Assumptions #1 спеки.
- `recordhash` (миграция 39) включает `expires_at::TEXT`, `created_at::TEXT`,
  `revoked_at::TEXT`, `first_used_at::TEXT`, `last_used_at::TEXT` и
  `active_session_lease_until::TEXT`. Хэш зависит **от строки в БД**, а не от
  её интерпретации. Поскольку хранимое значение не меняется, хэш остаётся
  стабильным. Это **подтверждено** в `38_song_share_links.sql` и
  `39_song_share_recordhash.sql`: включены `expires_at::TEXT`,
  дата пересчитывается только при изменении самого значения.

**Alternatives considered**:
- Переписать триггер так, чтобы `recordhash` учитывал не значение, а
  «логический момент» — выходит за рамки и нарушает Assumption #3 спеки.
- Мигрировать старые записи в UTC — Assumptions спеки прямо запрещает.

### 2.4 Пояс отображения (Q4 → A)

**Decision**: интерфейсы форматируют дату в часовом поясе устройства; сервер
не вычисляет «МСК-метку».

**Rationale**:
- Соответствует спросу на универсальность (разные пользователи — разные TZ):
  гость во Владивостоке не должен видеть «09:57» непонятно в какой TZ.
- Снимает необходимость в `expiresAtLabel` → убирает «ещё одно представление даты»,
  на котором и локализован дефект.
- В обоих фронтендах уже есть распространённый паттерн:
  `new Date(epochMs).toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })`
  — нужно лишь гарантировать, что на входе **правильный** epoch ms (FR-003).

**Alternatives considered**:
- Возвращать ISO-строку с зоной (`2026-08-11T09:57:36+03:00`) — лишний слой
  парсинга на клиенте, не нужен.
- Возвращать пару `{epochMs, label}` — статус-кво, отвергнут Q1.

### 2.5 Глубина тестов (Q5 → A)

**Decision**: серверные тесты + тесты форматирования в обоих интерфейсах;
интеграционные с БД не требуются.

**Rationale**:
- Дефект проявился на стыке сервера и UI. Только серверный слой поймал бы
  расхождение в `formatMskLabel`, но не поймал бы костыль
  `link.expiresAt - 3*3600*1000` в `ShareLinkModal.vue`. Только UI-слой
  мог бы поймать костыль, но не поймал бы серверный сдвиг.
- Тесты не уходят в CI (конституция: «в CI нет»). Они запускаются разработчиком
  локально или в подобном окружении, как и существующие `Mp3TrimmerTest.kt`.

**План тестов:**

| Слой | Файл | Что покрывает |
|------|------|---------------|
| Сервер (Kotlin) | `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkDateTimeTest.kt` | `LocalDateTime.ofInstant(Instant.ofEpochMilli(expires), ZoneId.of("Europe/Moscow"))` для известного epoch ms возвращает ожидаемое `09:57:36`; сценарий «08:57 + 1ч = 09:57» |
| Публичный SPA (Node) | `karaoke-public/src/utils/__tests__/dateFormat.test.js` через `node --test` | `new Date(epochMs).toLocaleString('ru-RU', { timeZone: 'Europe/Moscow' })` = `'11.08.2026 09:57'`; без `timeZone` = то же для МСК-устройства |
| Admin SPA (Node) | `webvue3/src/utils/__tests__/dateFormat.test.js` через `node --test` | то же; плюс: прочерк для пустой даты |

**Проверка независимости от TZ машины** (FR-014): для каждого теста
устанавливать `process.env.TZ = 'UTC'` / `'Europe/Vladivostok'` / `'America/New_York'`
и убеждаться, что результат для `timeZone: 'Europe/Moscow'` не меняется.

**Alternatives considered**:
- vitest — нужно добавлять в `package.json` двух фронтендов, расширять
  `vite.config.js`, CI конституцией не используется.
- jest — то же, лишний setup.
- `node --test` (встроен в Node 22) — без зависимостей, простой запуск
  (`node --test karaoke-public/src/utils/__tests__/dateFormat.test.js`).

## 3. Граничные случаи и риски

### 3.1 «Часы сбиты у пользователя» (Edge Case)

Текущий `isExpired` уже сравнивает `expiresAt` с `Date.now()` (поле реального
момента после исправления). Часы пользователя не должны влиять: при создании
ссылки сравнение идёт на стороне сервера (`expires_at > now()` в SQL), а на UI
— `expiresAt <= Date.now()`. После исправления и то и другое использует
реальный момент, без сдвигов — поведение инвариантно.

### 3.2 Летнее/зимнее время

`Europe/Moscow` с 2014 года — фиксированный UTC+3, переходов нет. Логика
устойчива. Для будущих гипотетических переходов — `ZoneId.of("Europe/Moscow")`
корректно их обрабатывает; `ZoneOffset.ofHours(3)` — нет (захардкоженное
смещение). Использовать `ZoneId`, не `ZoneOffset`.

### 3.3 Запись в БД и TZ сессии Postgres

Сейчас все `INSERT`/`UPDATE` выставляют `setTimestamp(Timestamp(epochMs))`.
`Timestamp.toString()` использует JVM TZ. На проде `ENV TZ="Europe/Moscow"`
→ JVM TZ = МСК → `Timestamp(1786431456000).toString()` = `'2026-08-11 09:57:36'`
(что мы и хотим). Но на машине разработчика без этой ENV — JVM TZ может
быть `Europe/Berlin`, тогда `'2026-08-11 08:57:36'` (на 1 час меньше).

**Защита:** для всех `setTimestamp` в `SongShareLinkService` (и связанных хелперах)
заменить на `setObject(..., LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.of("Europe/Moscow")), Types.TIMESTAMP)`.
Это даст стабильную запись в МСК независимо от JVM TZ — даже если ENV TZ сбита.

### 3.4 Чтение и таймзона DB-сессии

`EXTRACT(EPOCH FROM expires_at AT TIME ZONE 'Europe/Moscow')` приводит значение
из МСК в UTC, `* 1000` → epoch ms. Это **алгоритмический** перевод, не зависит
от TZ сессии Postgres. То есть работает в любой TZ DB-сессии. **OK.**

### 3.5 Существующий код поиск-поиск (resolveForGuest, findLinkIdBySecret, validateShareSession)

Все три используют `expires_at > now()` — серверное сравнение, таймзона не
важна. **Без изменений.**

### 3.6 Рекордхэш и миграции

Изменения не должны затронуть `recordhash` (см. Assumption #3 спеки). Это
гарантируется тем, что:
- DDL не меняется (`38_song_share_links.sql`, `39_song_share_recordhash.sql`).
- Формат значений в БД не меняется (по-прежнему `timestamp without time zone`
  в МСК).
- Изменения только в логике формирования `expiresAt` (Long) и в удалении
  `*Ms`/`*Label` полей.

## 4. Не входит в задачу (Out of Scope, подтверждение)

- `docs/features/`: автозамена `expires_at` в `22_stem_jobs.sql` и
  `06_site_users.sql` — у этих таблиц тот же дефект, но область задачи
  ограничена share-ссылками (FR-001, Assumption #5).
- `tbl_site_users.subscription_expires_at`, `tbl_stem_jobs.expires_at` — Out of Scope.
- Изменение типа колонок на `timestamp with time zone` — Assumption #4 спеки.
- Логика `isExpired` на UI (сравнение `expiresAtMs <= Date.now()`) —
  функционально корректна после исправления, правки не требуются.

## 5. Резюме для Phase 1

В `data-model.md` фиксируем:
- изменение контракта полей `OwnerLinkView` (Long), `SessionView` (Long),
  `CreateResult` (Long): `expiresAt` и пр. = реальный epoch ms; поля `*Ms`
  и `*Label` удалены.
- DDL не меняется.
- `recordhash` остаётся стабильным.

В `contracts/` фиксируем:
- server-side JSON: новые/удалённые поля эндпоинтов `/api/public/share/{id}/create`,
  `/api/public/share/mine/{id}`, `/api/siteusers/share/links`, `/api/siteusers/share/sessions`.
- `expectecShape` для `EpochMs` (Long) — реальный момент.

В `quickstart.md` — пять шагов ручной проверки:
1. Создать ссылку → срок в модалке = значению в БД (МСК-устройство).
2. Перезагрузить страницу → срок тот же.
3. Посмотреть с устройства во Владивостокском TZ → срок локальный.
4. Админ-таблица → все даты совпадают с БД.
5. Истёкшая ссылка → `isExpired=true` и в модалке, и в ShareView.
