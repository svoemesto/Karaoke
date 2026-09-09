# Component: storage-flow

> **Домен**: [storage](../domain.md)
> **Компонента**: high-level диаграмма потоков данных между
> `karaoke-app`, `karaoke-web` и MinIO.

## Архитектура (C4 L1 + L2)

```
┌──────────────────────────────────────────────────────────────────┐
│ Admin-машина (только karaoke-app)                                │
│                                                                  │
│  ┌─────────────────┐                                             │
│  │  karaoke-app    │──── KaraokeStorageService ───┐               │
│  │  (Spring Boot,  │     (MinioClient SDK)        │               │
│  │   8899)         │                              ▼               │
│  │                 │   ┌──────────────────────┐                   │
│  │                 │──▶│ Local MinIO          │                   │
│  │                 │   │ (karaoke-storage:9000)│                   │
│  │                 │   │ read+write           │                   │
│  │                 │   └──────────────────────┘                   │
│  │                 │                                              │
│  │                 │──── StorageApiClientImpl ──────┐              │
│  │                 │     (MinioClient SDK)          │              │
│  └─────────────────┘                                │              │
│                                                     ▼              │
│  ┌─────────────────┐                                │              │
│  │ WebVue3 admin   │                                │              │
│  │ (Vue 3 + Vite,  │  ─────── HTTP ─────────►  nginx-proxy       │
│  │  7906)          │   через / /api/*        (admin: прямо)      │
│  │                 │                                              │
│  └─────────────────┘                                              │
└──────────────────────────────────────────────────────────────────┘

Только прод (другая машина):

┌──────────────────────────────────────────────────────────────────┐
│ Прод-сервер                                                       │
│                                                                  │
│  ┌─────────────────┐                                              │
│  │  karaoke-web    │──── StorageApiClientWeb ───┐                 │
│  │  (Spring Boot,  │     (WebClient reactive)    │                 │
│  │   8899)         │                              │                 │
│  │                 │                              │                 │
│  │                 │   ┌──────────────────────┐  │                 │
│  │                 │   │ nginx-proxy          │◀─┘                 │
│  │                 │   │ (host-level path-proxy)│                │
│  │                 │   └──────────┬───────────┘                     │
│  └─────────────────┘              │                               │
│                                   ▼                               │
│  ┌─────────────────┐    ┌──────────────────────┐                  │
│  │  karaoke-public │    │ Remote MinIO         │                  │
│  │  (Vue 3 + Vite, │    │ (отдельный хост)      │                  │
│  │   7905)         │    │ read+write (admin)    │                  │
│  │                 │    │ read (public через nginx)               │
│  │                 │    └──────────────────────┘                  │
│  └─────────────────┘                                              │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

## Потоки данных

### Поток 1: Admin загружает файл (write в MinIO)

```
webvue3 (file input)
  └─► POST /api/storage/upload (StorageController)
       └─► KaraokeStorageServiceImpl.uploadFile (Local MinIO)
            └─► MinioClient.putObject → local MinIO
```

Также возможен параллельный upload в remote:

```
StorageController.uploadFile
  └─► StorageApiClientImpl.uploadFile
       └─► MinioClient.putObject → remote MinIO (через прямое
           подключение admin → прод, см. infra)
```

**NB**: в текущем коде это **два отдельных вызова**, **не транзакция**.
Может возникнуть несогласованность: файл в local, но не в remote
(или наоборот). HealthReport ловит это через `fileIsActual`.

### Поток 2: HealthReport проверяет наличие файла (read из MinIO)

```
webvue3 → POST /api/health/getHealthReportList
  └─► HealthReport.getHealthReportList (per Song)
       └─► actionsRemoteStorage(fileExists) → StorageApiClientImpl.fileExists
       └─► actionsLocalStorage(fileExists)  → KaraokeStorageServiceImpl.fileExists
```

**Это и есть проблема #69**: ~72k HTTP round-trips на страницу Songs.

### Поток 3: Public пользователь играет песню (read nginx-proxy)

```
karaoke-public (Player)
  └─► GET /api/public/player/{id}/access
       └─► PublicApiController.fetchFromMinIO (HTTP HEAD через nginx-proxy)
            └─► nginx path-proxy → remote MinIO → 200 OK / 404
  └─► GET /api/public/player/{id}/fileminus.mp3
       └─► PublicPlayerController.existsInMinIO (HTTP HEAD)
            └─► nginx path-proxy → remote MinIO → stream
```

Этот поток НЕ использует `KaraokeStorageService` (прямой SDK),
потому что на проде нет контейнера `karaoke-storage`. Только nginx.

### Поток 4: StemJob (premium фича) — двусторонний

```
1. karaoke-public → POST /api/public/account/stemjobs/create
   ├─► сохраняет файл на СВОЙ диск (temp-dir, НЕ MinIO)
   └─► StemJob.createNew() → INSERT в tbl_stem_jobs (WAITING)

2. karaoke-app → StemJobPollScheduler (каждые N сек)
   └─► для каждого WAITING:
        ├─► скачать оригинал (НЕ из MinIO, а с диска public-host!
        │   через InternalStemJobController — отдельная схема,
        │   см. gaps)
        ├─► стем-сепарация (premium, см. premium-stems.md)
        └─► upload результатов в MinIO:
             ├─► KaraokeStorageServiceImpl.uploadFile (local MinIO)
             └─► StorageApiClientImpl.uploadFile (remote MinIO)

3. karaoke-public → GET /api/public/account/stemjobs/{id}/download
   └─► PublicStemJobController.download
        └─► StorageApiClientWeb.getPresignedUrl → nginx-proxy → stream
```

**Это сложный поток, подробнее — Pass 342.**

## Кто пишет / кто читает (сводная таблица)

| Сценарий | Локальный MinIO | Remote MinIO | Local FS |
|---|---|---|---|
| Admin upload из webvue3 | ✅ пишет | ✅ пишет (отдельно) | ✅ пишет |
| webvue3 просмотр файла | ✅ читает (через direct URL?) | ✅ читает (через nginx-proxy) | ❌ |
| Public песня играется | ❌ | ✅ читает (через nginx-proxy) | ❌ |
| HealthReport repair | ✅ пишет+читает | ✅ пишет+читает | ✅ пишет+читает+symlinks |
| StemJob processing | ✅ пишет | ✅ пишет | ✅ пишет (temp) |
| Стем-сепарация (premium) | ❌ | ❌ | ✅ читает (исходный файл) |

## Инвариант согласованности

**Теоретически**: каждый файл должен существовать во всех нужных
местах одновременно.

**Практически**: MinIO upload и FS upload **не атомарны** друг
относительно друга. HealthReport — единственный механизм, который
это выявляет и чинит.

**Следствие**: если webvue3 и karaoke-app стартуют одновременно и
оба начинают чинить одну песню — возможен конфликт. Текущая защита:
`recomputeAndBroadcast` синхронизирует через SSE, но **не
предотвращает race на repair**.

**TODO**: проверить, есть ли single-flight guard на repair-loop.
(Предположительно нет — это и может быть причиной #65 race.)

## Known gaps

- [ ] **StorageController** — admin REST API для просмотра/управления
      MinIO. Где именно, какие эндпоинты. (TODO: прочитать.)
- [ ] **nginx-proxy конфиг** (`deploy/nginx/minio-proxy.conf`) —
      какие методы, есть ли auth, лимиты.
- [ ] **StemJobPollScheduler ↔ InternalStemJobController** —
      как именно karaoke-app получает исходный файл с public-host.
      Это второй поток между машинами, не описанный.
- [ ] **Race на repair-loop** — single-flight guard или нет.
- [ ] **MinIO-транзакции** — есть ли multi-bucket atomic upload.
- [ ] **Backup/restore** — есть ли cron-бэкап MinIO на отдельный
      сервер, откуда восстанавливается при потере.
- [ ] **CORS / public-read** — какие bucket'ы публичные, какие
      private, кто управляет.

## Связь с другими компонентами

- **HealthReport** (`actionsLocalStorage`, `actionsRemoteStorage`).
- **Async Process Queue** (Pass 342) — `StemJobPollScheduler` и др.
- **SSE / WebSocket** (P2) — `recomputeAndBroadcast`.