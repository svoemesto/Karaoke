# Component: storage-api-client

> **Домен**: [storage](../domain.md)
> **Компонента**: интерфейс `StorageApiClient` и его реализации
> (`StorageApiClientImpl` для `karaoke-app`, `StorageApiClientWeb`
> для `karaoke-web`).

## Ответственность | Responsibility

Spring-бин для доступа к **remote MinIO** через HTTP-прокси.

Две реализации:

- **`StorageApiClientImpl`** (`karaoke-app`) — **прямой** MinioClient
  SDK к remote MinIO (admin-машина имеет полныйный доступ через
  docker-сеть или VPN).
- **`StorageApiClientWeb`** (`karaoke-web`) — **WebClient** (reactive)
  через nginx path-proxy `minio-proxy` на проде (karaoke-web не
  имеет прямого доступа, см. [storage/domain.md](../domain.md)).

## Интерфейсы и Контракты

### `StorageApiClient` (interface)

Расположение: `karaoke-app/.../services/StorageApiClient.kt:24`.

```kotlin
interface StorageApiClient {
    fun uploadFile(bucketName: String, fileName: String, pathToFileOnDisk: String,
                   onProgress: ((Int) -> Unit)? = null): String?
    fun uploadFile(bucketName: String, fileName: String, fileContent: ByteArray,
                   onProgress: ((Int) -> Unit)? = null): Mono<String>
    fun getFileUrl(bucketName: String, fileName: String): Mono<String>
    fun getPresignedUrl(bucketName: String, fileName: String, expiry: Int = 604800): Mono<String>
    fun downloadFile(bucketName: String, fileName: String): Mono<ByteArray>
    fun downloadFile(bucketName: String, fileName: String, pathToFileOnDisk: String): File
    fun deleteFile(bucketName: String, fileName: String): Mono<String>
    fun listFiles(bucketName: String): Mono<List<String>>
    fun checkIfExists(bucketName: String, fileName: String): Mono<Map<String, Boolean>>  // BULK, см. gaps
    fun fileExists(bucketName: String, fileName: String): Boolean  // BLOCKING!
    fun fileIsActual(bucketName: String, fileName: String, pathToFileOnDisk: String): Boolean
    fun fileIsActual(bucketName: String, fileName: String, storageFileInfo: StorageFileInfo): Boolean
    fun setBucketPublic(bucketName: String): Mono<String>
    fun setBucketPrivate(bucketName: String): Mono<String>
    fun isBucketPublic(bucketName: String): Mono<Map<String, Boolean>>
    fun getFileStat(bucketName: String, fileName: String): Mono<StatObjectResponse>
    fun getFileInfo(bucketName: String, fileName: String): Mono<StorageFileInfo>
    fun listFilesInfo(bucketName: String): Mono<List<StorageFileInfo>>
}
```

**NB**: `fileExists` — **блокирующий** (синхронный `Boolean`), хотя
большинство методов возвращают `Mono`. Это потому, что в HealthReport
онте вызывается из блокирующего контекста. См. tasks #65 и #69.

## Логика и Алгоритмы

### `fileExists` — главный hot path

Синхронный (НЕ `Mono`), потому что используется в `HealthReport.kt`
через `actionsRemoteStorage` и `actionsLocalStorage`.

Реализация (StorageApiClientImpl):

1. `decodeFileNameIfEncoded(fileName)`.
2. `statObjectOrNull(bucketName, decodedName)` — MinioClient SDK.
3. Возвращает `true` если объект есть, `false` иначе.

**Известные проблемы**:

- HTTP round-trip через nginx-прокси на проде: ~50-100ms.
- Race condition в обработке `NoSuchKey` — задача #65.

### `uploadFile` с прогрессом

`uploadFile(fileContent: ByteArray, onProgress: ((Int) -> Unit)?)` —
реактивная загрузка с callback'ом прогресса. Использует
`CountingInputStream` для отслеживания байт.

Применяется в:

- `AdminTaskController` (UI прогресс-бар).
- `StemJobPollScheduler` (premium фича «Создать минусовку»: загрузка
  результатов стем-сепарации; см. `archive/docs/features/premium-stems.md`).

### `checkIfExists` — bulk-проверка

`Mono<Map<String, Boolean>>` — возвращает map с одним ключом `exists`
(см. реализацию `StorageApiClient.kt:319-336`: возвращает
`mapOf("exists" to (statObject != null))`).

**Где используется**:

- `karaoke-app/.../controllers/StorageController.kt:213` — admin
  endpoint для проверки наличия файла.
- `karaoke-app/.../Utils.kt:732` — `storageApiClient.checkIfExists(...)`,
  затем `checkIfExists?.get("exists") ?: false`.
- Реализация: `StorageApiClient.kt:319`, `StorageApiClientWeb.kt:242`.

NB: несмотря на название ("bulk"), в текущей реализации
`checkIfExists` принимает ОДИН `(bucketName, fileName)` и
возвращает map из одного элемента. Имя унаследовано от API
YooKassa/Amazon S3 (multi-object check). Для реального bulk —
нужно расширить сигнатуру.

### URL-encoded имена файлов

`decodeFileNameIfEncoded` — приватный helper. Аналогичен
одноимённому в `KaraokeStorageService`. Необходим, потому что
nginx-прокси иногда кодирует имена файлов.

## Реализации

### `StorageApiClientImpl` (admin)

Файл: `karaoke-app/.../services/StorageApiClient.kt:133`.

Прямой `MinioClient` SDK. Имеет те же timeouts что и
`KaraokeStorageServiceImpl`. Разница — **возвращает `Mono`** для
большинства операций (потому что многие операции идут через reactive
путь в UI).

### `StorageApiClientWeb` (прод)

Файл: `karaoke-web/.../services/StorageApiClientWeb.kt:29`.

`WebClient` (reactor) к nginx-прокси. Используется в:

**DI-параметр** в нескольких сервисах karaoke-web (без прямого вызова,
т.к. реальный доступ идёт через nginx-proxy):

- `KaraokeWebService.kt:39` — DI-параметр.
- `PriceService.kt:48` — DI-параметр для расчёта скидок.
- `SiteUserTokenService`, `ShareLinkSweeper`, `SubscriptionRenewalScheduler`.

**Прямой вызов** — `PublicStemJobController` (стрим stemjobs через
WebClient).

Конфигурация `WebClient` — `WebClientConfig.kt` (базовый URL, retry,
connection-pool).

## Зависимости

- `io.minio.*` (MinIO SDK)
- `okhttp3.*`
- `reactor.core.publisher.Mono` (reactor)

## Edge cases

- **URL-encoded имена**: см. `decodeFileNameIfEncoded`.
- **`NoSuchKey` vs `NoSuchBucket`**: различать, чтобы не маскировать
  ошибки конфигурации.
- **Timeout на upload**: hardcoded в `StorageApiClient.kt:144-146`:
  - `connectTimeout = 15s`
  - `readTimeout = 60s`
  - `writeTimeout = 300s` (5 минут — для больших файлов).

  **НЕ** настраивается через `KaraokeProperties`. Если нужен
  тюнинг — править хардкод (Pass 343+).

## Pass 351: graceful degradation (Spec #352, OpenProject #71)

В Pass 351 добавлен `StorageCircuitBreaker` (NEW `@Component` в
`karaoke-app/.../services/StorageCircuitBreaker.kt`), который оборачивает
`fileExists`, `fileIsActual`, `getFileInfo` через `circuit.decorate(...)`:

- **Per-call timeout**: `storage.file-exists-timeout-seconds` (default 5s, override `STORAGE_FILE_EXISTS_TIMEOUT_SECONDS`).
- **Circuit breaker FSM**: CLOSED → (N consecutive failures) → OPEN → (cooldown elapsed) → HALF_OPEN → (probe success) → CLOSED. (N = `storage.circuit-breaker-threshold`, default 5; cooldown = `storage.circuit-breaker-cooldown-seconds`, default 30).
- **In-memory only** (AtomicReference): state сбрасывается при рестарте `karaoke-app`.
- **Half-open probe pattern** (per Q3): первый call после cooldown — single probe. Success → CLOSED, failure → OPEN (reset openedAtMs).

При OPEN state — `fileExists`/`fileIsActual` возвращают `false` мгновенно (без MinIO call). При timeout — `false` после `timeoutSeconds`. При успехе — нормальный результат.

Новые SLF4J events (Pass 351, `infra.cache.storage`):
- `cache:network:failure` (WARN) — per-call network failure.
- `cache:circuit:state` (INFO) — state transition (CLOSED↔OPEN↔HALF_OPEN).

Это **root-cause fix** для OpenProject #65 «Ошибка при проверке наличия
файла в удаленном хранилище». См. `specs/352-storage-graceful-degradation/spec.md`,
`docs/features/storage-metadata-cache.md`, `specs/349-tracker-must-link/report-65.md`.

## Связь с другими компонентами

- **HealthReport** (`actionsRemoteStorage`): `fileExists`,
  `downloadFile`, `uploadFile` для восстановления из remote.
- **Async Process Queue**: `StemJobPollScheduler` использует
  `StorageApiClient` для скачивания результатов стем-сепарации
  (см. `archive/docs/features/premium-stems.md`).
- **`StorageApiClientWeb`**: используется ТОЛЬКО в
  `PublicStemJobController` (пока).

## Known gaps

- [ ] **Race condition в `fileExists`** (задача #65) — детальный
      анализ, repro-сценарий.
- [ ] **`checkIfExists`** не используется — почему? Можно ли
      переиспользовать для #69 (batch metadata cache)?
- [ ] **`WebClientConfig.kt`** — где настроен baseUrl,
      connection-pool, retry-policy.
- [ ] **`StorageApiClientImpl` vs `KaraokeStorageServiceImpl`** — оба
      ходят к одному MinIO (remote vs local), но разные
      транспорты. Где пересекаются, где комплементарны.
- [ ] **`getPresignedUrl` vs nginx-proxy GET** — где какой
      используется. Возможно, разные сценарии.
- [ ] **Прогресс через WebClient** (`StorageApiClientWeb`) — нет
      callback'а прогресса (reactive stream), в отличие от admin.
      Это by design или gap?
- [ ] **`decodeFileNameIfEncoded`** дублируется в обоих импл —
      нужен общий helper в `karaoke-app/.../services/`.

## Код

- `karaoke-app/.../services/StorageApiClient.kt` (интерфейс + Impl,
  ~470 строк)
- `karaoke-web/.../services/StorageApiClientWeb.kt` (Web impl,
  ~280 строк)
- `karaoke-web/.../config/WebClientConfig.kt` (см. gaps)