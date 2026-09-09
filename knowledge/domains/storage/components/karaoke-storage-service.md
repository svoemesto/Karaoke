# Component: karaoke-storage-service

> **Домен**: [storage](../domain.md)
> **Компонента**: интерфейс `KaraokeStorageService` и его реализация
> `KaraokeStorageServiceImpl` для `karaoke-app`.

## Ответственность | Responsibility

Spring-бин для `karaoke-app`, инкапсулирующий доступ к **local
MinIO** (`karaoke-storage:9000`). Полный **read+write** (upload,
download, delete, list, метаданные). Блокирующий (НЕ reactive).

Используется в:

- `karaoke-app/.../HealthReport.kt` — `actionsLocalStorage` для
  восстановления файлов из локального MinIO.
- `karaoke-app/.../services/KaraokeStorage.kt` — обёртка над этим
  сервисом (см. gaps).
- `karaoke-app/.../controllers/StorageController.kt` — admin REST API
  для просмотра/управления файлами.

## Интерфейсы и Контракты

### `KaraokeStorageService` (interface)

Расположение: `karaoke-app/.../services/KaraokeStorageService.kt:34`.

```kotlin
interface KaraokeStorageService {
    fun uploadFile(bucketName: String, fileName: String, file: InputStream, size: Long?)
    fun uploadFile(bucketName: String, fileName: String, pathToFileOnDisk: String)
    fun getFileUrl(bucketName: String, fileName: String): String
    fun downloadFile(bucketName: String, fileName: String): InputStream
    fun downloadFile(bucketName: String, fileName: String, pathToFileOnDisk: String): File
    fun deleteFile(bucketName: String, fileName: String)
    fun getPresignedUrl(bucketName: String, fileName: String, expiry: Int = 604800): String
    fun bucketExists(bucketName: String): Boolean
    fun fileExists(bucketName: String, fileName: String): Boolean
    fun listFiles(bucketName: String): List<String>
    fun setBucketPublic(bucketName: String)
    fun setBucketPrivate(bucketName: String)
    fun isBucketPublic(bucketName: String): Boolean
    fun createBucketIfNotExists(bucketName: String)
    fun deleteAllEmptyBuckets()
    fun getFileStat(bucketName: String, fileName: String): StatObjectResponse?
    fun getFileInfo(bucketName: String, fileName: String): StorageFileInfo
    fun fileIsActual(bucketName: String, fileName: String, pathToFileOnDisk: String): Boolean
    fun fileIsActual(bucketName: String, fileName: String, storageFileInfo: StorageFileInfo): Boolean
    fun listFilesInfo(bucketName: String): List<StorageFileInfo>
}
```

### `StorageFileInfo` (data class)

```kotlin
data class StorageFileInfo(
    val bucketName: String,
    val fileName: String,
    val etag: String,        // ETag от MinIO, может быть пустым
    val size: Long,          // байт; -1 если неизвестно
)
```

## Логика и Алгоритмы

### `fileExists` и `fileIsActual` — главные hot path методы

**`fileExists(bucketName, fileName)`** — возвращает `Boolean`:

1. Создаёт bucket, если не существует (`createBucketIfNotExists`).
2. `storageClient.statObject(...)` — MinioClient SDK.
3. Возвращает `true` если объект есть, `false` если `NoSuchKey`.

**`fileIsActual(bucketName, fileName, pathToFileOnDisk)`** — проверяет,
совпадает ли локальный файл с тем, что в MinIO:

1. Если локальный файл не существует → `false`.
2. `getFileInfo(bucketName, fileName)` → `StorageFileInfo`.
3. Возвращает `localFile.length() == storageFileInfo.size`.

**NB**: `fileIsActual` **не проверяет etag** — только размер. Это
упрощение: если размер совпадает, считаем актуальным. Если файл
изменён через внешний инструмент, изменение размера — единственный
детектор. См. tasks #69 для возможного улучшения через etag.

### `uploadFile` — 3 overloads

| Overload | Что принимает | Когда использовать |
|---|---|---|
| `(bucket, name, InputStream, size)` | Поток в памяти | Программный upload (нет файла) |
| `(bucket, name, pathToFileOnDisk)` | Путь на диске | Upload готового файла |
| (нет третьего — есть в `StorageApiClient`) | ByteArray | Для reactive (см. [storage-api-client](storage-api-client.md)) |

### Bucket policies

- `setBucketPublic` — устанавливает bucket policy `"{\"Version\":\"...\",\"Statement\":[{...\"Principal\":{\"AWS\":[\"*\"],...}]}"`.
- `setBucketPrivate` — устанавливает пустую политику.

Где какой bucket — см. мини-словарь в [storage/domain.md](../domain.md#мини-словарь-buckets).

### `decodeFileNameIfEncoded`

Приватный helper: если имя файла содержит `%`, пробует
`URLDecoder.decode(name, UTF-8)`. Если декодирование падает —
возвращает исходное имя.

Используется во ВСЕХ методах, чтобы не получить «не найдено» на
имени типа `песня%20с%20ёжиком.mp3`.

## Зависимости

- **MinioClient SDK** (`io.minio.*`) — стандартный SDK.
- **OkHttpClient** — для HTTP-транспорта, специальные timeouts:
  - `connectTimeout` = 10 сек
  - `readTimeout` = 30 сек
  - `writeTimeout` = 30 сек
  - `connectionPool(0, 1, NANOSECONDS)` — **no connection reuse**.
    Каждый вызов — новое соединение. Это **намеренно**: защита от
    устаревших keep-alive на nginx-прокси. (TODO: проверить, есть
    ли аналогичное решение в `StorageApiClient`.)

## Edge cases

- `etag` от MinIO может содержать кавычки и сложные символы — при
  сравнении обязательно учитывать.
- `size` может быть `-1` (если stat не вернул).
- `pathToFileOnDisk` с URL-encoded символами — через
  `decodeFileNameIfEncoded`.

## Связь с другими компонентами

- **HealthReport** (`actionsLocalStorage`): вызывает `fileExists`,
  `downloadFile`, `uploadFile` для восстановления файлов.
- **Async Process Queue** (Pass 342): repair-процессы могут
  вызывать `KaraokeStorageService` через `KaraokeProcess`.
- **`StorageApiClient`**: дублирует API, но для remote MinIO через
  HTTP-прокси. **Не использовать как fallback**: разные транспорты,
  разные гарантии.

## Known gaps (TODO для следующих Pass)

- [ ] **`karaoke-app/.../KaraokeStorage.kt`** (вне `services/`) —
      обёртка или alias? Не проверено.
- [ ] **`deleteAllEmptyBuckets`** (строка 92) — где вызывается,
      зачем. Cleanup job?
- [ ] **`StatObjectResponse?`** — частично используется в
      `getFileStat`. Что возвращает, где нужно.
- [ ] **`uploadFile` через pathToFileOnDisk** — есть ли flush,
      атомарность, rollback при ошибке.
- [ ] **TTL/lifecycle rules на MinIO buckets** — настроены ли, кто
      управляет.
- [ ] **Concurrency** — что если два потока одновременно делают
      `uploadFile` на один и тот же `(bucket, fileName)`. Race?
      (MinIO overwrite atomic, см. `local-0003` ADR.)

## Код

- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeStorageService.kt`
  (интерфейс + `KaraokeStorageServiceImpl`, 590 строк)
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeStorage.kt`
  (см. gaps)
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/StorageController.kt`
  (admin REST API)