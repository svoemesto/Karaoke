---
id: domain-storage
title: "Domain: Storage (MinIO + локальные файлы)"
status: Active
slug: storage
related:
  - ../health/domain.md
  - ../processing/domain.md
  - ../rendering/domain.md
---

# Domain: Storage (MinIO + локальные файлы)

> Bounded context для хранения бинарных данных (медиа-файлы,
> обложки, превью, экспортные артефакты). Два физических бэкенда —
> MinIO (S3-compatible) и локальная FS.
>
> Прецедент создания — задачи OpenProject #65 (race condition в
> remote storage) и #69 (кеширование). Также связано с тем, что
> «HealthReport для каждой песни дёргает MinIO N раз» (#69).

## Обзор контекста (Bounded Context)

Storage — слой абстракции над **двумя физическими бэкендами**:

1. **MinIO (S3-compatible)** — основное хранилище медиа. Используется
   в двух режимах:
   - **Local MinIO** (`karaoke-storage:9000`, контейнер в docker-сети
     `karaokenet` на admin-машине) — полный read/write для
     `karaoke-app`.
   - **Remote MinIO** (на отдельном хосте прод-сервера, доступ через
     nginx path-proxy `minio-proxy`) — read/write для `karaoke-app`,
     read-only неподписанные GET/HEAD для `karaoke-web`.
2. **Local Filesystem (FS)** — обычные файлы на диске admin-машины.
   Не S3, не通过网络. Доступны только `karaoke-app`. Используется
   для файлов, которые нужны MLT-движку и аудио-степаратору (большие
   файлы, которыми обмениваются локальные подпроцессы).

**Граница**: контекст НЕ отвечает за:

- Консистентность MinIO↔FS (→ [health](../health/domain.md) —
  HealthReport);
- Postgres БД (отдельный `KaraokeConnection`);
- Платёжные данные (нет);
- Логирование доступа к MinIO — отдельная тема, см. TODO.

## Ubiquitous Language | Единый язык

| Термин | Определение | Где в коде |
| --- | --- | --- |
| **`KaraokeStorageService`** | Spring-бин, абстракция над MinIO для `karaoke-app` (read+write) | `karaoke-app/.../services/KaraokeStorageService.kt:34` |
| **`KaraokeStorageServiceImpl`** | Реализация: MinioClient SDK → local MinIO | `karaoke-app/.../services/KaraokeStorageService.kt:151` |
| **`WebKaraokeStorageServiceImpl`** | **Заглушка** для `karaoke-web`: все методы бросают `UnsupportedOperationException` (см.ниже) | `karaoke-web/.../services/WebKaraokeStorageServiceImpl.kt:35` |
| **`StorageApiClient`** | Spring-бин, HTTP-клиент к MinIO через nginx path-proxy | `karaoke-app/.../services/StorageApiClient.kt:24` |
| **`StorageApiClientImpl`** | Реализация: прямой MinioClient SDK к remote MinIO | `karaoke-app/.../services/StorageApiClient.kt:133` |
| **`StorageApiClientWeb`** | Реализация для `karaoke-web`: WebClient (reactor) к nginx-прокси | `karaoke-web/.../services/StorageApiClientWeb.kt:29` |
| **`StorageFileInfo`** | Data class: `bucketName`, `fileName`, `etag`, `size` | `karaoke-app/.../services/KaraokeStorageService.kt:119` |
| **`bucket`** | MinIO bucket. Имена: `karaoke`, `karaoke-cache`, `stemjobs`, и др. | см. мини-словарь ниже |
| **`minio-proxy`** | nginx path-proxy на проде, через который идёт весь доступ к MinIO | `deploy/nginx/minio-proxy.conf` |

### Мини-словарь buckets

(Известные, по использованию в коде; неполный — нужно сканировать.)

| Bucket | Что хранит | Кто пишет | Кто читает |
|---|---|---|---|
| `karaoke` | Основной: обложки, видео, аудио | `karaoke-app` | `karaoke-app`, `karaoke-web` (через nginx-proxy) |
| `karaoke-cache` | Кеш изображений (см. `local-0003-shared-minio-image-cache.md`) | `karaoke-app` | `karaoke-app`, `karaoke-web` |
| `stemjobs` | Премиум-фича «Создать минусовку»: загруженные + результаты | `karaoke-app` | `karaoke-web` (стрим через `PublicStemJobController`) |

## Ключевые компоненты

- **`KaraokeStorageService`** (интерфейс) — основной API для `karaoke-app`:
  `uploadFile`, `downloadFile`, `deleteFile`, `fileExists`,
  `listFiles`, `getFileInfo`, `fileIsActual`, `getFileStat`,
  `setBucketPublic` / `setBucketPrivate`, `getPresignedUrl`.
- **`StorageApiClient`** (интерфейс) — то же самое, но **возвращает
  `Mono`/`Flow`** (reactive), потому что вызов идёт через HTTP
  (nginx-proxy → remote MinIO). Используется для прогресса upload
  (`CountingInputStream`).
- **`WebKaraokeStorageServiceImpl`** — **заглушка** для `karaoke-web`,
  см. секцию «Архитектурные решения».
- **`StorageFileInfo`** — data class для метаданных файла (etag, size).

## Архитектурные решения (принятые)

### Решение 1: **`karaoke-web` НЕ обращается к MinIO напрямую**

`karaoke-web` имеет бин `WebKaraokeStorageServiceImpl`, но **все его
методы бросают `UnsupportedOperationException`**. Реальный доступ:

- Через **nginx path-proxy** (`minio-proxy` на проде) — неподписанные
  GET/HEAD для публичных картинок и аудио. См. `fetchFromMinIO`,
  `existsInMinIO` в `PublicApiController`, `PublicPlayerController`.
- Через **`StorageApiClientWeb`** (WebClient + reactor) — для премиум
  фич, где нужна авторизация (например, скачивание результатов
  StemJob в `PublicStemJobController`).

**Причина**: на проде MinIO вынесен на отдельный хост. Прямой
MinioClient из docker-контейнера `karaoke-web` не работает (MTU
black-hole, ломает SigV4-подпись). Поэтому — nginx-прокси, который
позволяет неподписанные GET/HEAD.

**Бин `WebKaraokeStorageServiceImpl` обязателен только для DI** —
несколько web-классов держат его как конструкторную зависимость.
По реально достижимым публичным путям его методы НЕ вызываются.

### Решение 2: **`StorageApiClient` reactive (Mono), `KaraokeStorageService` blocking**

- `KaraokeStorageService` — блокирующий (Spring Service), используется
  в `karaoke-app` где всё равно всё синхронное.
- `StorageApiClient` — reactive (Spring reactive), потому что идёт через
  HTTP-прокси и может долго (upload больших файлов).

### Решение 3: **`CountingInputStream` для прогресса upload**

`StorageApiClient.uploadFile(file: ByteArray, onProgress: ((Int) -> Unit)?)`
использует `CountingInputStream` для callback'а процента. Это
используется в AdminTask для UI прогресс-баров.

### Решение 4: **`decodeFileNameIfEncoded` для URL-encoded имён файлов**

Если имя файла содержит `%`, предполагается URL-encoded UTF-8, и
перед запросом к MinIO делается `URLDecoder.decode`. Это нужно
потому что веб-прокси иногда кодирует имена файлов (русские
буквы, спецсимволы).

## Domain Invariants

1. **`fileExists` и `fileIsActual` — single source of truth**: ни один
   другой компонент НЕ должен проверять наличие файла через
   `File.exists()` — только через эти методы. Нарушение → рассинхрон
   между MinIO и тем, что думает код.
2. **Все upload/download** проходят через одну из реализаций
   (`StorageApiClient` или `KaraokeStorageService`). Прямой `MinioClient`
   из другого места — ЗАПРЕЩЁН.
3. **`bucketName` ЗАПРЕЩЕНО хардкодить** в контроллерах — только через
   `KaraokeProperties` или как параметр из `Song.*FileType*`. Прецедент
   — спека 314 (ещё не задокументирована, см. gaps).
4. **MinIO-операции НЕ выполняются в HTTP-handler-треде для
   пользовательских запросов** (см. инвариант async-очереди в
   [processing](../processing/domain.md)). Для длинных — через
   `KaraokeProcess`.

## Hot paths (связанные с задачами #65, #69, #75)

| Путь | Частота | Где болит | Status |
|---|---|---|---|
| `HealthReport.getHealthReportList` → `actionsLocalStorage` / `actionsRemoteStorage` → `cachedFileExists` (Pass 344+ через `StorageMetadataCache`) | ~72k раз на страницу Songs в webvue3 → после кеша ~144/страница (уникальные ключи) | OpenProject #69 | **FIXED (Pass 344, спека #344)** |
| `Song.loadFromDbById` → `KaraokeStorageService.fileExists` / `fileIsActual` | 1 раз на каждую песню при загрузке Songs | то же | **FIXED** (через `cachedFileExists`) |
| `SongEdit.vue` (webvue3) → `getHealthReportList` для одной песни | 1 раз на открытие редактирования | то же | **FIXED** |
| `StorageApiClient.fileExists` HTTP round-trip (REMOTE) | 120-250ms каждый (холодный кеш) | OpenProject #75 | **OPTIMIZED (Pass 364, US2): async cold-start, 50ms max** |
| `actionsLocalStorage` → MinIO (LOCAL) | 25-55ms каждый (холодный кеш) | OpenProject #75 | **FIXED (Pass 364, US3): circuit breaker** |
| `startRepairAll` → executeResolvable | sync, блокирует HTTP thread | OpenProject #75 | **FIXED (Pass 364, US3): async via repairExecutor** |
| `MinioClient.statObject` (внутри `fileExists`) | 10-30ms (прямой SDK на admin) | локальная задержка | OK (минимальная) |

## Связь с OpenProject и задачами

- **#65** «Ошибка при проверке наличия файла в удаленном хранилище»:
  race condition в HTTP-вызовах к `StorageApiClient.fileExists`.
  Детальный анализ — TODO (см. Known gaps).
- **#69** «Кеширование информации из хранилища»: спека 339 (2026-09-09)
  была провалена из-за пропуска Knowledge-first (Pass 340). После
  Pass 341 Knowledge достаточно для новой спеки. **FIXED (Pass 344, спека #344)**.
- **#75** «Ускорение HealthReport» (Pass 364, спека #364):
  - US1 (≤200ms): частично достигнуто (LOCAL_STORAGE 25-55ms, REMOTE async 50ms)
  - US2 (async cold-start): **IMPLEMENTED** (`getFileExistsAsync`, 50ms timeout)
  - US3 (non-blocking repair): **IMPLEMENTED** (`repairExecutor`, fire-and-forget)
  - FR-006 (circuit breaker): **IMPLEMENTED** (`StorageCircuitBreaker` в `actionsLocalStorage`)

## Код (физическая реализация)

### Admin-сторона (`karaoke-app`)

- `karaoke-app/.../services/KaraokeStorageService.kt` (интерфейс + Impl)
- `karaoke-app/.../services/StorageApiClient.kt` (интерфейс + Impl)
- `karaoke-app/.../services/KaraokeStorage.kt` (доп. wrapper, частично
  используется; см. gaps)
- `karaoke-app/.../KaraokeStorage.kt` (?? уточнить, не путать с services/)

### Web-сторона (`karaoke-web`)

- `karaoke-web/.../services/StorageApiClientWeb.kt`
- `karaoke-web/.../services/WebKaraokeStorageServiceImpl.kt` (заглушка)
- `karaoke-web/.../controllers/PublicApiController.kt` (`fetchFromMinIO`)
- `karaoke-web/.../controllers/PublicPlayerController.kt` (`existsInMinIO`)
- `karaoke-web/.../controllers/PublicStemJobController.kt` (стрим stemjobs)

### Деплой

- `deploy/nginx/minio-proxy.conf` (nginx path-proxy)
- `deploy/docker-compose.yml` (контейнер `karaoke-storage`)

## Known gaps

- [ ] **`StorageApiClient.fileExists` race** — детальный анализ
      (задача #65). Нужен repro-сценарий.
- [ ] **`karaoke-app/.../KaraokeStorage.kt`** — отдельный от
      `services/KaraokeStorageService.kt`. Wrapper? alias? legacy?
      Не проверено.
- [ ] **`StorageApiClient.checkIfExists`** (строка 68) — bulk-метод
      для проверки многих файлов за раз. **Не используется** в коде
      (предположительно для оптимизации — пересекается с #69).
- [ ] **Мини-словарь buckets** неполный — нужен grep по всем
      вызовам.
- [ ] **`getPresignedUrl`** vs **nginx-proxy GET** — где что
      используется (предположительно presigned для admin-внутренних
      операций, nginx-proxy для публичных).
- [ ] **NGINX-конфиг** — как именно настроен `minio-proxy`, какие
      методы пропускает, есть ли auth.
- [ ] **`uploadFile` overloads** (через pathToFile vs ByteArray vs
      InputStream) — какой когда используется, decision tree.
- [ ] **Bucket policies** (`setBucketPublic`/`setBucketPrivate`) —
      какой bucket какой политикой, кто управляет.
- [ ] **MinIO quotas / lifecycle rules** — есть ли policy на
      auto-cleanup (например, для `stemjobs` после expire).
- [ ] **Логирование MinIO-операций** — отдельная тема для
      `monitoring/domain.md` P2.

## Связанные ADR | Related ADRs

- `knowledge/adr/local-0003-shared-minio-image-cache.md` —
  прецедент: TTL + scheduled cleanup в MinIO для image-cache.
  Применимо к кешу метаданных (#69)?
- `archive/docs/features/premium-stems.md` — упомянут в KDoc.
- `archive/docs/features/mlt-generator.md` — упомянут в KDoc.
- ADR `0004-karaoke-app-admin-only.md` — `karaoke-app` только на
  admin-машине.