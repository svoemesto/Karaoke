# Research: Шаблоны автоматических новостей сайта

**Branch**: `128-news-publish-templates` | **Date**: 2026-08-03

Phase 0 research для `plan.md`. Раскрывает неизвестные Technical Context и
Caveat из Constitution Check (Principle III — sync `tbl_public_settings`).

## R1. Как `tbl_public_settings` расходится между LOCAL и SERVER

**Decision**: `tbl_public_settings` — физически отдельная таблица на
каждом окружении (LOCAL Postgres на admin-машине, SERVER Postgres на
проде). **SyncRegistry НЕ участвует** — таблица `tbl_public_settings` не
является `KaraokeDbTable`-сущностью, не имеет `recordhash`-триггеров и не
зарегистрирована в `SyncRegistry.all`. Расхождение значений между
окружениями — ручная ответственность администратора через параметр
`target=local|remote` в UI/endpoint'е.

**Rationale** (найдено в коде):
- `PublicSettingsController` (`karaoke-app`) — endpoint
  `/api/publicsettings/digest` и `/api/publicsettings/update` принимают
  `@RequestParam target`. `resolveDb(target)` возвращает
  `Connection.remote()` при `target="remote"`, иначе `Connection.local()`.
  Комментарий в шапке класса явно фиксирует: «настройки, нужные сервисам,
  которые реально работают на боевом сервере ... эта таблица в Postgres,
  поэтому доступна и локально, и на сервере через тот же паттерн
  target=local|remote».
- `PublicSettingsTable.vue` (webvue3) — `<select v-model="target">` с
  опциями `local`/`remote`. Каждое сохранение (`savePublicSettingValue`)
  шлёт `target` в `/api/publicsettings/update`.
- Аналогично `News.isNewsAutoPublishKillSwitchActive` (kill-switch,
  specs/125) — читает `tbl_public_settings` на той БД, в которую
  вызвано, без sync. Админ включает kill-switch на прода через тот же
  `PublicSettingsTable` с `target=remote`.

**Применение к фиче**: Администратор редактирует 4 шаблонных ключа на
admin-машине через webvue3 с `target=remote` (prod-БД). Prod-рендеринг
(`SongReleaseAnnouncementScheduler`/`MainController.doChangeRecords`)
читает те же ключи из prod-БД. Sync этих 4 ключей через `SyncRegistry`
НЕ требуется, отдельной миграции sync-флагов НЕ нужно. Принцип III
(Constitution) не нарушен — `tbl_public_settings` по дизайну вне
sync-инфраструктуры.

**Альтернативы отклонены**:
- *Sync через `SyncRegistry`* — `tbl_public_settings` не
  `KaraokeDbTable`, нет `recordhash`, внедрение sync = переделка таблицы
  под 8 флагов `sync_*_push/pull_*`. Избыточно для 4 строк.
- *Запись в обе БД одновременно* — усложняет endpoint, нарушает
  существующий паттерн `target=local|remote` (один запрос → одна БД).
- *Хранение в `KaraokeProperties` (файл)* — уже отвергнуто в spec.md
  (FR-016): файл недоступен на проде.

## R2. Запись в `tbl_public_settings` — `UPDATE` vs UPSERT

**Decision**: Использовать **UPSERT** (`INSERT ... ON CONFLICT (key) DO
UPDATE`) для endpoint'а записи шаблонов новостей. Не трогать
существующий `PublicSettingsController.update` (он делает `UPDATE` и
работает для ключей, уже вставленных миграцией — Yandex SmartCaptcha,
`newsAutoPublishKillSwitch`).

**Rationale** (найдено в коде):
- `PublicSettingsController.update` делает `UPDATE tbl_public_settings
  SET value=?, last_update=now() WHERE key=?` → возвращает
  `updated > 0`. Если ключа в таблице НЕТ — `updated == 0`, метод
  вернёт `false` (запись «провалилась»).
- `VkTemplateService.templateFor` читает `KaraokeProperties.getString(...)`
  (файл), где ключи `vkTemplateAir`/`vkTemplatePremium` уже существуют
  (пустыми или с дефолтом). Поэтому для VK `UPDATE`-паттерн работает —
  ключ есть в файле всегда.
- Для шаблонов новостей сайта FR-016 явно говорит: «в БД ключи
  отсутствуют до первого сохранения». Значит первый `UPDATE`
  `PublicSettingsController.update` вернёт `false` → UI покажет ошибку,
  администратор не сможет сохранить шаблон без предварительной
  ручной SQL-вставки.

**Применение к фиче**: Новый endpoint `/api/news/templates` (см. R3)
использует `INSERT ... ON CONFLICT (key) DO UPDATE SET value=...,
last_update=now()` — работает и для нового ключа (INSERT), и для
существующего (UPDATE). Без отдельной миграции seed-значений.

**Альтернативы отклонены**:
- *Миграция seed 4 ключей с пустыми значениями* — лишний SQL-файл в
  `deploy/karaoke-db/`, который нужно накатить и на LOCAL, и на PROD.
  UPSERT делает это избыточным.
- *Правка `PublicSettingsController.update` на UPSERT* — меняет
  контракт generic endpoint'а (используется `PublicSettingsTable` для
  всех ключей, включая captcha/kill-switch). Риск регрессии.

## R3. Endpoint-паттерн для шаблонов новостей сайта

**Decision**: Отдельный контроллер `NewsTemplateController` в
`karaoke-app` (пакет `com.svoemesto.karaokeapp.controllers`) с маршрутами
`/api/news/templates/*`, зеркалирующий существующие `/api/vk/templates/*`
и `/api/telegram/templates/*`. Внутри — `target=local|remote` параметр
(по образцу `PublicSettingsController`) и UPSERT-запись (R2).

**Rationale**:
- Спека FR-012 разрешает «делегировать существующему
  `/api/publicsettings/*` либо быть отдельным контроллером — на
  усмотрение планирования». Отдельный контроллер лучше по 3 причинам:
  1. **Валидация ключей**: только 4 разрешённых (`newsTemplateAirTitle`,
     `newsTemplateAirBody`, `newsTemplatePremiumTitle`,
     `newsTemplatePremiumBody`) — generic `PublicSettingsController.update`
     принимает любой `key`, что позволяет администратору случайно создать
     мусорный ключ.
  2. **UPSERT**: новый контроллер делает `INSERT ... ON CONFLICT` (R2),
    не трогает generic `UPDATE`.
  3. **Defaults + Placeholders + Preview**: endpoints
    `/api/news/templates/defaults` и `/api/news/templates/preview`
    требуют бизнес-логики (`NewsTemplateService.DEFAULT_*`,
    `NewsTemplateService.render`), которая не относится к generic
    key/value таблице. Зеркалирование `/api/vk/templates/*` —
    естественный паттерн.
- Параметр `target=local|remote` — по образцу
  `PublicSettingsController.resolveDb` (см. R1), тот же `withDb`-хелпер
  с `close()` в `finally` (чтобы не исчерпать пул Postgres).

**Где живёт контроллер**: `karaoke-app` (не `karaoke-web`), потому что
admin-UI webvue3 работает против admin-машины, где запущен `karaoke-app`.
На проде `karaoke-app` нет, но **endpoint'ом записи/превью пользуется
только admin-UI** на admin-машине. Prod-рендеринг (чтение шаблона в
`SongReleaseAnnouncementService`) использует прямой JDBC к prod-БД (по
образцу `isNewsAutoPublishKillSwitchActive`), НЕ через HTTP-endpoint.

**Альтернативы отклонены**:
- *Делегирование `/api/publicsettings/*`* — нет валидации ключей,
  нет defaults/preview, пришлось бы добавлять их как отдельные
  endpoints в `PublicSettingsController`, ломая SRP.
- *Контроллер в `karaoke-web`* — admin-UI не ходит на прод за
  шаблонами, он ходит на admin-машину. `karaoke-web`-контроллер
  был бы мёртвым кодом для admin-UI.

## R4. Рендеринг шаблона — `NewsTemplateService` и составные плейсхолдеры

**Decision**: Новый `object NewsTemplateService` в
`karaoke-app/services` (по образцу `VkTemplateService`), с методами
`template(key, database)`, `render(template, song)`, `placeholders()`,
константами `DEFAULT_*` и `PLACEHOLDERS`. Составные плейсхолдеры
`{albumYearSuffix}`/`{bodyDetails}` вызывают существующие хелперы
`SongReleaseAnnouncementService.albumYearSuffix`/`bodyDetails` —
**методы перестают быть `private` и становятся `internal`** (или
переносятся в `NewsTemplateService` как новые `public` функции,
принимающие `Song`). Второй вариант чище — оставляет
`SongReleaseAnnouncementService` без правок API-видимости.

**Rationale** (найдено в коде):
- `VkTemplateService.renderWithFlags` (lines 128-163) — образец
  рендерера: `placeholderRegex = Regex("""\{(\w+)}""")`,
  `replacements: Map<String, String>`, `placeholderRegex.replace` с
  fallback на `mr.value` (literal-treatment неизвестных, FR-023).
  `NewsTemplateService.render` повторяет этот паттерн 1-в-1, расширяя
  `replacements` двумя составными ключами `albumYearSuffix` и
  `bodyDetails`.
- `albumYearSuffix(song)` (SongReleaseAnnouncementService:46-51) и
  `bodyDetails(song)` (lines 54-59) — чистые функции от `Song`, без
  побочных эффектов. Перенос в `NewsTemplateService` (или вызов
  `internal`) сохраняет byte-идентичность — те же строки/проверки
  `isNotBlank`/`year > 0`/`joinToString(", ")`.
- `News.createAutoAnnouncement` (News.kt:337) уже принимает `title` и
  `body` как `String` параметры — менять сигнатуру НЕ нужно. Правки в
  `SongReleaseAnnouncementService.checkOnAirWindow` (line 198-199) и
  `detectAndAnnounceAvailability` (line 88-89) минимальны: хардкод-строки
  заменяются на `NewsTemplateService.render(template, song)` для
  `title` и `body` соответственно.

**Чтение шаблона в рендере**:
```kotlin
fun template(key: String, database: KaraokeConnection): String {
    val connection = database.getConnection() ?: return defaultFor(key)
    return try {
        connection.prepareStatement("SELECT value FROM tbl_public_settings WHERE key = ?").use { ps ->
            ps.setString(1, key)
            ps.executeQuery().use { rs ->
                if (rs.next()) rs.getString("value").ifBlank { defaultFor(key) } else defaultFor(key)
            }
        }
    } catch (e: Exception) {
        println("NewsTemplateService.template($key) error: ${e.message}")
        defaultFor(key)
    }
}
```
По образцу `isNewsAutoPublishKillSwitchActive` (fail-open: при ошибке
чтения — дефолт из кода, не падение).

**Альтернативы отклонены**:
- *Статический кэш шаблонов* — нарушает FR-008 (без перезапуска).
  Прямой JDBC на каждое создание auto-новости — это редкое событие
  (один раз при выходе песни в эфир), ~один SELECT на новость.
  Накладные ~1-5 мс, в shadow других затрат `News.createAutoAnnouncement`.
- *Чтение через `KaraokeProperties`* — отвергнуто в FR-016.

## R5. UI-паттерн — третья вкладка в `PublishTemplatesView.vue`

**Decision**: Добавить вкладку «Новости сайта» в существующий
`PublishTemplatesView.vue`, повторяющую структуру ВК/Telegram, но с
**двумя полями** (`title` + `body`) на тип вместо одного `caption`.
Превью возвращает пару `title`+`body`. Сброс к дефолту — per-field.

**Rationale**: Спека FR-001..FR-003 явно требует «наравне с
ВК/Telegram», тот же `nav nav-tabs` + `nav nav-pills`-паттерн.
Единственное отличие — два поля на тип — отражает структуру `tbl_news`
(`title`/`body` отдельно), не требует нового UI-фреймворка. Подсветка
плейсхолдеров и предупреждение о несбалансированных скобках —
переиспользуются из ВК/Telegram-вкладок (DRY).

**Альтернативы отклонены**:
- *Отдельный роут/экран* — нарушает FR-001 («наравне с
  ВКонтакте/Telegram», «вкладка») и SC-004 («в одной вкладке … без
  отдельных UI/экранов»).

## Resolved NEEDS CLARIFICATION

Все NEEDS CLARIFICATION из Technical Context разрешены:
- ✅ Constitution Check Principle III — sync `tbl_public_settings` (R1)
- ✅ Запись в БД — UPSERT vs UPDATE (R2)
- ✅ Endpoint-паттерн — отдельный контроллер (R3)
- ✅ Рендеринг — `NewsTemplateService` + перенос хелперов (R4)
- ✅ UI — третья вкладка (R5)

Нет открытых вопросов. Переход к Phase 1.