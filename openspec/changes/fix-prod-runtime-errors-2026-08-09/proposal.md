# Proposal: fix-prod-runtime-errors-2026-08-09

## Why

Анализ логов `karaoke-web` на проде за 2026-08-08 — 2026-08-09 выявил **3 категории runtime-ошибок**, которые приводят либо к потере денег (ЮKassa recurring), либо к 5xx на проде (Thymeleaf, KaraokeProperties). Все три проявляются стабильно, не зависят от пользовательских действий и требуют исправления до того, как они масштабируются (например, ЮKassa — каждый день +4 пользователя без продления).

## What Changes

- **Fix 1 (платежи, потеря денег)**: исправить маршрутизацию nginx для `/yookassa/` на `minio-proxy` и добавить детальное логирование 4xx-ответов в `PaymentService.chargeRecurring` (без него причину `400 Bad Request` невозможно диагностировать).
- **Fix 2 (Thymeleaf, 5xx на главной)**: исправить рендеринг поля `n.publishAt` в `templates/main.html` — сейчас шаблон ожидает `java.util.Date`, а из БД приходит `String` в формате `yyyy-MM-dd HH:mm:ss.SSS`, и Spring 6.2 + Java 22 не могут конвертировать через deprecated `Date.parse()`.
- **Fix 3 (KaraokeProperties, 5xx на `/api/song-vk-image/{id}`)**: предотвратить падение `KaraokeProperties.loadPropertiesMap()` на проде, где путь `/sm-karaoke/system/Karaoke.properties` доступен только на admin-машине (`karaoke-app`), а не в контейнере `karaoke-web`. Двухчастный фикс: (a) **убрать обращения к `KaraokeProperties` из `karaoke-web/`** — на проде файлов `Karaoke.properties` нет, и в идеале обращений быть не должно; реальные обращения — только 2 (`vkPreviewImageWidth`, `vkPreviewImageHeight`) в `PublicApiController.kt:467-468`, оба используют fallback на дефолты 1200/630; захардкодить эти константы в `songVkImage`. (b) **try/catch вокруг `savePropertiesMap()` в `loadPropertiesMap()`** как defensive programming — даже после убирания обращений с веба класс остаётся в shared-коде и любое будущее использование не должно превращаться в 500.

Каждое из исправлений самостоятельное; объединены в один change, чтобы катить и тестировать одним деплоем.

## Capabilities

### New Capabilities

- `runtime-errors`: реестр production runtime-ошибок `karaoke-web` и правила их обработки/диагностики — единая точка для всех трёх исправлений.

### Modified Capabilities

Нет. Это новая capability, описывающая поведение «ошибки продакшна должны логироваться понятно и не превращаться в 5xx без видимой причины». Существующие specs по payments/news/properties не меняют требования на уровне спеки (это bugfix, а не изменение требований).

## Impact

### Затронутый код

| Файл | Что меняется |
|------|--------------|
| `karaoke-web/src/main/kotlin/.../services/PaymentService.kt` (chargeRecurring, ~236) | Логирование `statusCode` и тела ответа при `WebClientResponseException` |
| `karaoke-web/src/main/resources/templates/main.html` (line 200) | Парсинг `n.publishAt` из строки вместо вызова `#dates.format(Date, ...)` |
| `karaoke-app/src/main/kotlin/.../KaraokeProperties.kt` (loadPropertiesMap, savePropertiesMap) | Try/catch для `Exception` при `savePropertiesMap()` (defensive programming) |
| `karaoke-web/src/main/kotlin/.../controllers/PublicApiController.kt` (songVkImage, ~467-468) | Удалить 2 вызова `KaraokeProperties.getInt("vkPreviewImageWidth" / "vkPreviewImageHeight")`, захардкодить `1200` и `630` (это текущие fallback-значения) |

### Затронутая инфраструктура

- **nginx на `minio-proxy`** (прод-сервер `188.119.64.111`): добавить `location /yookassa/` с проксированием на `https://api.yookassa.ru/v3`. Проверить, что nginx передаёт `Authorization`, `Idempotence-Key`, `Content-Type: application/json`. Эта правка требует **ручного деплоя на проде** — пользователь это делает сам (см. AGENTS.md, раздел «Ограничения агента»).

### Что НЕ меняется

- API-контракты `GET /api/song-vk-image/{id}`, `GET /`, `POST /api/payments/webhook` — без изменений.
- Схема БД — без изменений (миграций нет).
- Зависимости — без изменений (WebClient и Spring Thymeleaf уже используются).

### Обратная совместимость

Все исправления — bugfix; наблюдаемое поведение после фикса — это и есть **корректное** поведение:
- ЮKassa-recurring начнёт возвращать детальные логи (раньше был только `e.message` без `statusCode`).
- Thymeleaf main.html перестанет падать на новостях с заполненным `publishAt`.
- `/api/song-vk-image/{id}` перестанет давать 500 на проде.

Никаких переходов на новый контракт не требуется.

### Связанные документы

- `AGENTS.md`, секция «Ограничения агента» — что может делать агент, а что нет (nginx на проде правит только пользователь).
- `AGENTS.md`, Q&A «redirectErrorStream(false) блокирует процесс» — аналогичная ситуация с подавлением stderr привела к дебагу вслепую; здесь — аналогично: логируем `statusCode`, а не `e.message`.
- `AGENTS.md`, секция «CI-gate» — все исправления идут через feature-ветку + PR + CI 7/7.
