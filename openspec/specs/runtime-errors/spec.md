## Purpose

Реестр наблюдаемых runtime-ошибок `karaoke-web` на проде и поведенческие требования к их логированию и обработке, чтобы любой 5xx имел достаточно контекста для диагностики без перезапуска контейнера и чтения слепков памяти.

## Requirements

### Requirement: Платежи ЮKassa с автопродлением логируют statusCode и тело ответа при 4xx/5xx

Система SHALL при попытке рекуррентного списания через ЮKassa (`PaymentService.chargeRecurring`) логировать HTTP status code ответа и тело ответа ЮKassa, если запрос завершился с ошибкой `WebClientResponseException`. Сообщение в логе SHALL содержать `sub_id`, `statusCode` и первые 500 символов тела ответа ЮKassa.

#### Scenario: Успешное списание
- **WHEN** ЮKassa возвращает 200 OK с телом платежа
- **THEN** в логе появляется только успешный идентификатор платежа (как сейчас), дополнительный error-лог НЕ пишется

#### Scenario: ЮKassa возвращает 4xx
- **WHEN** ЮKassa возвращает 400/402/403/404 с телом вида `{"type":"error","code":"...","description":"..."}`
- **THEN** в логе появляется строка вида `PaymentService.chargeRecurring: ошибка автосписания для sub=<id>: statusCode=400 body=<первые 500 символов тела>`
- **AND** scheduler помечает подписку как `failed` с явной причиной (как сейчас)

#### Scenario: Сетевая ошибка (нет ответа)
- **WHEN** `WebClient` бросает `WebClientException` (timeout, connection refused)
- **THEN** в логе появляется `PaymentService.chargeRecurring: сетевая ошибка для sub=<id>: <e.message>` (как сейчас)

### Requirement: Thymeleaf-шаблон main.html не падает на новостях с заполненным publishAt

Система SHALL корректно отображать дату публикации новости (`publishAt`) в шаблоне `templates/main.html` line 200 независимо от того, приходит значение как `java.util.Date`, `Timestamp` или `String` в формате `yyyy-MM-dd HH:mm:ss.SSS`.

#### Scenario: Новость с заполненным publishAt (String)
- **WHEN** в БД есть новость с `publish_at = '2026-08-08 20:17:14.741'` (формат `Timestamp.toString()`)
- **THEN** на главной странице (`GET /`) в блоке новостей эта дата отображается как `08.08.2026 20:17`
- **AND** сервер возвращает HTTP 200

#### Scenario: Новость с publishAt = NULL
- **WHEN** в БД есть новость с `publish_at IS NULL` (черновик)
- **THEN** ячейка с датой пустая (как сейчас)
- **AND** сервер возвращает HTTP 200

#### Scenario: Несколько новостей на странице с разными значениями publishAt
- **WHEN** в БД есть 5 новостей: 2 с `publishAt IS NULL`, 2 с заполненным `publishAt`, 1 с `publishAt` в формате `Timestamp.valueOf("2026-08-08 20:17:14.741")`
- **THEN** страница рендерится полностью без ошибок
- **AND** даты отображаются в формате `dd.MM.yyyy HH:mm` для всех заполненных полей

### Requirement: KaraokeProperties.loadPropertiesMap() не падает на проде при недоступности файла

Система SHALL при первом обращении к `KaraokeProperties.get*()` в любом контейнере (где путь `/sm-karaoke/system/Karaoke.properties` физически недоступен — например, `karaoke-web` на проде) использовать значения по умолчанию из `listKaraokeProperties` БЕЗ попытки записи файла.

#### Scenario: Любой вызов getInt при недоступном файле
- **WHEN** контейнер `karaoke-web` (или другой контейнер без доступа к `/sm-karaoke/`) вызывает `KaraokeProperties.getInt(...)`
- **AND** файл `/sm-karaoke/system/Karaoke.properties` отсутствует и нет прав на его создание
- **THEN** метод возвращает дефолтное значение из `listKaraokeProperties`
- **AND** `Exception` от `writeText` логируется как WARN `KaraokeProperties: не удалось сохранить файл <path>: <message>`, но НЕ пробрасывается наверх
- **AND** вызывающий код получает дефолтное значение и продолжает работу (HTTP 200 OK, не 500)

#### Scenario: Повторные вызовы после первой ошибки
- **WHEN** сценарий выше уже произошёл один раз (лог WARN записан)
- **AND** приходит следующий запрос, который снова вызывает `KaraokeProperties.getInt(...)`
- **THEN** метод возвращает дефолтное значение БЕЗ повторной попытки записи файла в той же сессии JVM (writeText даже не вызывается после первой неудачи)

#### Scenario: На admin-машине (karaoke-app)
- **WHEN** контейнер `karaoke-app` стартует, файл `/sm-karaoke/system/Karaoke.properties` существует
- **THEN** метод читает файл (как сейчас) — поведение не меняется

### Requirement: На вебе (karaoke-web) нет обращений к KaraokeProperties

Система SHALL НЕ иметь вызовов `KaraokeProperties.*` в модуле `karaoke-web/` в продакшн-коде. `KaraokeProperties` — это объект состояния admin-машины (`karaoke-app`), и публичный веб не должен зависеть от файлов `/sm-karaoke/`, которых на проде нет.

#### Scenario: Поиск обращений в karaoke-web
- **WHEN** запускается `grep -r "KaraokeProperties\." karaoke-web/src/main/kotlin --include="*.kt"`
- **THEN** результат содержит ТОЛЬКО `import`-statements (если вообще нужны), без реальных вызовов в runtime-коде

#### Scenario: songVkImage использует захардкоженные размеры
- **WHEN** приходит запрос `GET /api/song-vk-image/{id}`
- **THEN** метод использует константы `frameW = 1200`, `frameH = 630` напрямую (без обращения к `KaraokeProperties.getInt(...)`)
- **AND** импорт `com.svoemesto.karaokeapp.KaraokeProperties` удалён из `PublicApiController.kt` (если других обращений нет)

#### Scenario: Если в будущем потребуется конфигурируемый размер
- **WHEN** в публичном коде потребуется конфигурируемое значение (вместо захардкоженного)
- **THEN** использовать `tbl_public_settings` (Postgres) — то же хранилище, что и Yandex SmartCaptcha в `CaptchaConfigService.kt` — НЕ `KaraokeProperties`

### Requirement: nginx на minio-proxy маршрутизирует /yookassa/ в API ЮKassa

nginx на хосте `minio-proxy` SHALL содержать `location /yookassa/` с `proxy_pass https://api.yookassa.ru/v3`, передающий заголовки `Authorization`, `Idempotence-Key`, `Content-Type` без искажений.

#### Scenario: Запрос chargeRecurring
- **WHEN** контейнер `karaoke-web` отправляет `POST http://minio-proxy/yookassa/payments` с заголовками `Authorization: Basic <base64>`, `Idempotence-Key: sub-XX-...`, `Content-Type: application/json`
- **THEN** nginx проксирует запрос на `https://api.yookassa.ru/v3/payments`
- **AND** ЮKassa получает те же заголовки и тело
- **AND** ответ возвращается контейнеру с тем же HTTP status code

#### Scenario: Запрос с валидным payment_method_id и балансом
- **WHEN** запрос прошёл через nginx корректно, у пользователя есть привязанная карта с балансом
- **THEN** ЮKassa возвращает 200 OK с `status: succeeded` (или `pending`)
- **AND** подписка продлевается

#### Scenario: Запрос с невалидным или истёкшим payment_method_id
- **WHEN** ЮKassa возвращает 404 с `{"type":"error","code":"payment_method_not_found"}`
- **THEN** `PaymentService.chargeRecurring` пишет детальный лог (см. Requirement 1)
- **AND** scheduler помечает подписку как `failed` с `reason="payment_method_not_found"`
