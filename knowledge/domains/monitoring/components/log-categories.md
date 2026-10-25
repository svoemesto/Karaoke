# Component: log-categories

> **Домен**: [monitoring](../domain.md)
> **Компонент**: реестр SLF4J-категорий проекта Karaoke для grep'аемого
> мониторинга и диагностики.

## Ответственность | Responsibility

В Karaoke есть **соглашение о SLF4J-категориях**: компоненты логируют
через `infra.*` категории для удобного grep'а в логах. Это компонента —
единая точка регистрации всех категорий.

**[WARN]** Логирование через стандартный `LoggerFactory.getLogger(Class)`
(без `infra.*` категории) **запрещено** для компонент мониторинга.
Это ломает grep в `log-correlation.md` runbook.

## Интерфейсы и Контракты | Interfaces and Contracts

### Категории

| Категория | Где | Что логируется | Уровень по умолчанию |
| --- | --- | --- | --- |
| `infra.prod.ping` | `ProdContainerCheck` | HTTP-пинг `sm-karaoke.ru/` | WARN при ошибке, INFO при recovery |
| `infra.prod.db` | `ProdContainerCheck` | JDBC-пинг прод-БД | WARN при ошибке, INFO при recovery |
| `infra.health.circuit` | `karaoke-app/.../HealthReport.kt` (circuitLog, спека #364) | FastFail circuit breaker: `circuit=OPEN storage=local|remote reason=Circuit breaker open` (Pass 429, #153: отдельные breaker'ы для local и remote) | WARN |
| `infra.cache.statbysong` | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/StatBySong.kt` (cacheLog) | Cold-start, refresh, ошибки | WARN при cold-start, INFO при success, WARN при failure |
| `infra.cache.storage` | `karaoke-app/.../services/StorageMetadataCache.kt` (cacheLog, спека #344, OpenProject #69) + `StorageCircuitBreaker.kt` (Pass 351, спека #352, OpenProject #71; Pass 429, #153 — два bean) + `CircuitBreakerController.kt` (Pass 372, спека #405, OpenProject #131) | cache:hit / cache:miss / cache:evicted (Pass 344) + **cache:network:failure** (Pass 351, WARN per fail; Pass 429 — `storage=local\|remote`) + **cache:circuit:state** (Pass 351, INFO per state transition CLOSED↔OPEN↔HALF_OPEN; Pass 429 — `storage=`) + **cache:circuit:watchdog** (Pass 372, WARN — probe stuck; Pass 429 — `storage=`) + **cache:circuit:reset** (Pass 372/429, INFO — `storage=local\|remote\|…`) | INFO hit/miss/circuit/reset, WARN network/watchdog |
| `infra.cache.hrpool` | `karaoke-app/.../services/HealthReportBatchPool.kt` (Pass 128, спека specs/128-async-health-report-list, OpenProject #128) | Старт/стоп пула, размер очереди (debug), ошибки worker'ов | INFO старт/стоп, DEBUG enqueue, WARN worker failure |
| `infra.sync.remote` | `karaoke-app/.../services/SyncRemoteClient.kt` (Pass 431, #155) + `ApiController.postSyncOneClick` | HTTP-синхронизация с прод-сайтом: `sync:remote:ok` / `sync:remote:retry` (транзиентная ошибка, повтор) / `sync:remote:failure` | INFO ok, WARN retry/failure |
| `infra.sync.security` | `karaoke-web/.../controllers/MainController.kt` (`POST /changerecords`, спека 554, OpenProject #357) | Отказы доступа к эндпоинту двух-БД синхронизации: `sync:security:denied` — поля `reason=` (`missing_secret` / `invalid_secret` / `secret_not_configured`), `source=` (адрес источника), `time=`. Тело запроса и текст ошибки БД в запись НЕ пишутся | WARN |
| `infra.player.access` | `karaoke-web/.../services/PlayerAccessService.kt` (спека 557, OpenProject #358, аудит BR-018) | Отказы в выдаче полного аудио/разметки онлайн-плеера: `player:access:denied` — поля `songId=`, `reason=` (`no_credentials` / `no_rights` / `assignment_revoked`); `player:access:demo` — `reason=demo_token` (штатный демо-режим). **Сам токен и содержимое аудио в запись НЕ пишутся** | WARN (denied) / INFO (demo) |
| `infra.settings.security` | `karaoke-web/.../controllers/PublicSettingsWebController.kt` (`/api/properties/**`, спека 564, OpenProject #359) | Отказы доступа к публичным настройкам: `settings:security:denied` — `reason=` (`missing_secret` / `invalid_secret` / `secret_not_configured` / `secret_key_write`), `source=`, `time=`; `settings:security:read-denied` — `reason=key_not_public`, `key=`; `settings:security:secret-read-blocked` — `reason=secret_key`, `key=`. Значения настроек и текст ошибки БД в запись НЕ пишутся | WARN |
| `infra.captcha.config` | `karaoke-web/.../services/CaptchaConfigService.kt` + `YandexCaptchaValidationService.kt` (спека 564, OpenProject #362) | Конфигурация и результаты проверки капчи: `captcha:config:server-key=configured\|not_configured` (старт сервиса, без значения ключа), `captcha:config:not_configured` (регистрация отклонена из-за незаконной конфигурации), `captcha:validation:unavailable` (внешний сервис недоступен). Значение серверного ключа НЕ пишется | INFO (старт), WARN (отказы) |
| `infra.song.voices` | `karaoke-app/.../model/Song.kt` (voiceNormalizeLog, спека `547`) | Нормализация голосов при сохранении: `song.voices.normalized` (удалены пустые голоса, `removed=`, `count=`) и `song.voices.normalize_skipped` (нечитаемые данные — fail-safe) | WARN |
| `infra.queue.render` | **не реализована** | Render queue stalled/lane stalled | (Pass 343+) |
| `infra.telegram.polling` | **не реализована** | Telegram polling state | (Pass 343+) |
| `infra.stemjobs.security` | `karaoke-web/.../controllers/InternalStemJobController.kt` + `PublicStemJobController.kt` (спека 561, OpenProject #361, аудит BR-258) | Отказы доступа к приватным объектам задания «Создать минусовку»: `stemjobs:security:denied` (во внутреннем канале: `reason=` `missing_secret`/`invalid_secret`/`secret_not_configured`, `source=`, `jobId=`, `file=`), `stemjobs:security:rejected` (400, недопустимое имя файла), `stemjobs:security:download-denied` (отказ в отдаче владельцу: `reason=`, `source=`, `jobId=`, `stem=`), `stemjobs:security:stored`/`purged` (INFO). **Содержимое объекта в запись НЕ пишется** | WARN (отказы), INFO (stored/purged) |
| `infra.stemjobs.storage` | `karaoke-app/.../StemJobStoragePolicyInitializer.kt` (спека 561, OpenProject #361) | Применение политики публичности бакетов при старте `karaoke-app`: `stemjobs:storage:policy` (`bucket=`, `policy=private|public`), `... failed` (недоступность MinIO — старт приложения не блокируется, fail-safe) | INFO (policy), ERROR (failed) |
| `infra.pictures` | `karaoke-app/.../UtilsPictures.kt` (BR-117) | Ошибка записи публикуемой картинки: `writePictureOrThrow` → `PictureWriteException`; пишутся путь и стек | ERROR |
| `infra.admin.sharelinks` | `karaoke-app/.../controllers/ShareLinksAdminController.kt` (BR-118) | SQL-ошибки админского дайджеста share-ссылок (`sqlState`, страница/размер) — вместо `printStackTrace` | ERROR |
| `infra.admin.news` | `karaoke-app/.../controllers/NewsController.kt`, `model/News.kt` (BR-119) | SQL-ошибки списка новостей: `news.list.unavailable` / `news.count.unavailable` | ERROR |
| `infra.admin.tasks` | `karaoke-app/.../services/AdminTaskService.kt` (BR-136) | Жизненный цикл фоновых admin-задач: вычистка по TTL, остановка пула при остановке приложения | INFO |
| `infra.editor.draft` | `karaoke-app/.../model/SongAssignmentDraft.kt` (BR-129) | Ошибка очистки черновика редакторского назначения (идентификатор назначения) | ERROR |
| `infra.catalog.rename` | `karaoke-app/.../model/SongArtifactRenamer.kt` (BR-131) | Неудача переименования артефакта песни (путь + причина) | WARN |
| `infra.monitor.check` | `karaoke-app/.../monitor/checks/{StemJobsStuckCheck,SubmittedAssignmentsCheck,UnreadChatMessagesCheck}.kt` (BR-134) | Сбой проверки мониторинга, читающей прод-БД: ошибка пробрасывается и становится алертом `check.<Name>.failure` | ERROR |
| `infra.monitor.tick` | `karaoke-app/.../monitor/MonitoringService.kt` (BR-135) | Тик мониторинга: пропуск пересекающегося тика (single-flight), падение проверки, ошибка рассылки | WARN |
| `infra.alignment` | `karaoke-app/.../services/AlignmentServiceClient.kt` (BR-137) | Сбой обращения к сервису forced-alignment: HTTP-код, тип исключения | ERROR |
| `infra.telegram.publish` | `karaoke-app/.../services/TelegramAutoPublishScheduler.kt` (BR-146) | Сбой выборки кандидатов авто-публикации в Telegram (`sqlState`), падение тика | WARN / ERROR |
| `infra.telegram.polling` | `karaoke-app/.../services/TelegramUpdatesConsumer.kt` (BR-158) | Long-poll Telegram: сбой `deleteWebhook` на старте, ошибка long-poll | WARN |
| `infra.vk.publish` | `karaoke-app/.../services/{VkAutoPublishScheduler,VkAutoPublishService}.kt` (BR-149…BR-157) | Тик авто-публикации ВК, сбой выборки кандидатов, «уже выполняется» рендер, сбой чтения air-новости | WARN / ERROR |
| `infra.whisper.asr` | `karaoke-app/.../services/WhisperAsrService.kt` (BR-159) | Сбой ASR: HTTP-код и тело ответа, пустое тело, невалидный JSON, прерывание | WARN / ERROR |
| `infra.premium.publish` | `karaoke-app/.../services/PremiumAutoPublishScheduler.kt` (BR-165, BR-166) | Сбой выборки очереди премиум-публикаций (`sqlState`), падение тика, неожиданный код канала | WARN / ERROR |
| `infra.cache.storage.local` | `karaoke-app/.../services/KaraokeStorageService.kt` (BR-164) | Ошибки S3 локального хранилища, которые НЕ означают «объекта нет» (код S3, бакет, файл) | WARN |
| `infra.cleanup` | `karaoke-app/.../services/cleanup/{ArtifactCleanupPlanner,ArtifactCleanupService}.kt` (BR-169, BR-170) | Прерванный обход дерева при построении отчёта уборки; невозможность записать след удаления в журнал | ERROR |
| `infra.sponsr.sync` | `karaoke-app/.../services/SponsrSyncService.kt` (BR-167) | Сбой продления премиума Sponsr для конкретного пользователя (`siteUserId`) | WARN |
| `infra.waveform.compare` | `karaoke-app/.../WaveformCompare.kt` (BR-122) | Таймаут декодирования через ffmpeg, ненулевой код возврата, нечитаемый вывод | WARN |
| `infra.stemjobs.transfer` | `karaoke-app/.../StemJobResultPublisher.kt` (спека 561, OpenProject #361) | Передача приватных объектов из `karaoke-app` в `karaoke-web`: `stemjobs:transfer:ok` (`jobId=`, `file=`, `bytes=`), `stemjobs:transfer:retry` (сетевая ошибка, следующая попытка), `stemjobs:transfer:denied` (403 — отказ по правам, повтор не выполняется) | INFO (ok), WARN (retry/denied) |

### Конвенция имён

- `infra.<subsystem>.<feature>` — где:
  - `infra` — префикс для всех мониторинговых логов.
  - `<subsystem>` — подсистема (`prod`, `cache`, `queue`, `telegram`, `sync`, `player`).
  - `<feature>` — конкретная проверка/событие (`ping`, `db`,
    `statbysong`, `render`, `polling`).

**[WARN]** Не путать с категориями `com.svoemesto.*` — это бизнес-логика,
не мониторинг. Мониторинг — только `infra.*`.

## Логика и Алгоритмы | Logic and Algorithms

### Пример: `infra.prod.ping` (ProdContainerCheck)

```kotlin
private val log = LoggerFactory.getLogger("infra.prod.ping")

fun checkPing(): Boolean {
    return try {
        val conn = URL("https://sm-karaoke.ru/").openConnection()
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        val ok = (conn.responseCode in 200..399)
        if (!ok) log.warn("ping failed: statusCode={}", conn.responseCode)
        ok
    } catch (e: Exception) {
        log.warn("ping error='{}'", e.message)
        false
    }
}
```

**Что ищем в логах**:

- `grep "infra.prod.ping" /var/log/karaoke-app.log` — все события пинга.
- `grep "infra.prod.ping" /var/log/karaoke-app.log | grep WARN` — только ошибки.

### Пример: `infra.cache.statbysong` (StatBySong, спека 289)

```kotlin
private val log = LoggerFactory.getLogger("infra.cache.statbysong")

fun refreshCache() {
    if (refreshing.compareAndSet(false, true)) {
        try {
            log.warn("cache:coldStart triggering background refresh")
            // ... load from DB ...
            log.info("cache:refreshed total={} durationMs={}", total, durationMs)
        } catch (e: Exception) {
            log.warn("cache:refreshFailed error='{}'", e.message)
        } finally {
            refreshing.set(false)
        }
    }
}
```

**Что ищем в логах**:

- `grep "infra.cache.statbysong" /var/log/karaoke-web.log | grep coldStart` —
  были ли холодные старты.
- `grep "infra.cache.statbysong" /var/log/karaoke-web.log | grep refreshFailed` —
  были ли ошибки refresh.

## Зависимости | Dependencies

- → [domain](../domain.md) — `MonitorCheck`, `MonitorAlert`.
- → [monitor-checks](monitor-checks.md) — где категории используются.

## Runbook

[`docs/ops/log-correlation.md`](../../../../docs/ops/log-correlation.md) —
полная карта логов с примерами grep-маркеров.

## Ловушки и предупреждения

**[WARN] Логирование через `LoggerFactory.getLogger(Class)`** в
компонентах мониторинга запрещено. Используйте **строковую категорию**
`infra.*` вместо имени класса.

**[WARN] Регистрация новой категории** должна быть зафиксирована в
таблице выше. Несогласованная категория = невидимый grep.

**[WARN] Уровень WARN для восстановления (recovery)** — не INFO.
Восстановления важны для анализа инцидентов, и должны быть видимы
в `tail` лога.

## Связанные ADR | Related ADRs

- ADR по logging conventions (TODO).
