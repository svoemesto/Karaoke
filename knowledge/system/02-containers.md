# L2 — Containers

> **Статус**: Active (Pass 356). C4 L2 — технические контейнеры
> внутри границы Karaoke.

## Контейнеры (приложения)

| Контейнер | Где | Назначение | См. также |
|---|---|---|---|
| **karaoke-app** | Admin-машина | Backend админ-логики: рендер, парсинг, HealthReport, async-очередь. Spring Boot, Kotlin. Порт 8899. | [processing domain](../domains/processing/domain.md) |
| **karaoke-web** | Admin-машина + прод | Публичное API + UI (Thymeleaf), `useAuth`, `useKaraokeEditor`. Spring Boot, Kotlin. Порт 8899 (admin) / 8899 (прод). | [integration domain](../domains/integration/domain.md) |
| **webvue3** | Admin-машина | Админ-интерфейс (Vue 3 + Vite + Vuex). Порт 7906. | [frontend docs](../system/frontend/) |
| **karaoke-public** | Admin-машина + прод | Публичный сайт (Vue 3 + Vite + composables). Порт 7905. | [frontend docs](../system/frontend/karaoke-public-composables.md) |
| **MinIO (karaoke-storage)** | Admin-машина (local) + прод (remote) | S3-compatible хранилище. | [storage domain](../domains/storage/domain.md) |
| **PostgreSQL** | Admin-машина (LOCAL) + прод (SERVER) | БД для всех entity. | [persistence domain](../domains/persistence/domain.md) |
| **nginx** | Обе машины | path-proxy для внешних сервисов (MTU black-hole обход). | [deploy-overview](../system/infra/deploy-overview.md) |

## Внешние контейнеры (НЕ Karaoke, но критические)

| Контейнер | Где | Назначение |
|---|---|---|
| **Whisper ASR** | Admin-машина (опционально) | Распознавание речи для forced-align. См. `docker-compose-whisper.yml`. |
| **Telegram SOCKS proxy** | Admin-машина (опционально) | Обход MTU black-hole для TG. См. `docker-compose-telegram-proxy.yml`. |
| **OpenProject tracker** | Admin-машина (или remote) | Трекер задач. См. `tracker-docker-compose.yml`. |
| **SearXNG** | (опционально) | Поисковый движок. См. `searxng-settings/`. |
| **alignment-ml** | Admin-машина (Python) | Forced-alignment ML-сервис. |

## Mermaid C4 L2

```mermaid
flowchart TB
    subgraph AdminMachine [Admin-машина (nsa-i9)]
      direction TB
      Webvue3[webvue3<br/>Vue 3 + Vite<br/>:7906]
      KaraokePublicAdmin[karaoke-public<br/>Vue 3 + Vite<br/>:7905]
      KaraokeApp[karaoke-app<br/>Spring Boot, Kotlin<br/>:8899]
      KaraokeWebAdmin[karaoke-web<br/>Spring Boot, Kotlin<br/>:8899]
      MinioLocal[(MinIO<br/>karaoke-storage:9000)]
      PostgresLocal[(PostgreSQL<br/>LOCAL)]
      NginxAdmin[nginx<br/>path-proxy]
      AlignML[alignment-ml<br/>Python FastAPI]
    end

    subgraph ProdServer [Прод-сервер]
      direction TB
      KaraokePublic[karaoke-public<br/>Vue 3 + Vite<br/>:7905]
      KaraokeWeb[karaoke-web<br/>Spring Boot, Kotlin<br/>:8899]
      MinioRemote[(MinIO<br/>отдельный хост)]
      PostgresServer[(PostgreSQL<br/>SERVER)]
      NginxProd[nginx<br/>path-proxy]
    end

    VK[VK]
    TG[Telegram]
    Sponsr[Sponsr]
    Yandex[Yandex SmartCaptcha]
    YooKassa[YooKassa]
    GeoIP[api.country.is]

    Webvue3 --> KaraokeApp
    KaraokePublicAdmin --> KaraokeWebAdmin
    KaraokeApp --> MinioLocal
    KaraokeApp --> PostgresLocal
    KaraokeWebAdmin --> MinioLocal
    KaraokeWebAdmin --> PostgresLocal

    KaraokePublic --> KaraokeWeb
    KaraokeWeb --> MinioRemote
    KaraokeWeb --> PostgresServer
    KaraokeWeb --> NginxProd

    NginxAdmin --> VK
    NginxAdmin --> TG
    NginxAdmin --> Yandex
    NginxAdmin --> YooKassa
    NginxAdmin --> GeoIP
    NginxAdmin --> Sponsr

    KaraokeApp --> AlignML
    KaraokeApp --> NginxAdmin
    KaraokeWeb --> NginxProd
```

## Hot paths

- **Страница «Песни» (webvue3) → karaoke-app** — XHR + SSE подписка.
- **HealthReportList** — N песен × 4+ типов = много MinIO-запросов
  (см. OpenProject #69).
- **Async-очередь рендера** — `THREAD_LANE_HEAVY_RENDER` serial
  (только одно render в момент времени).
- **Two-DB sync** — `AutoOneClickSyncScheduler` каждые 3 часа.

## Known gaps

- [ ] **Whisper ASR** — отдельный контейнер или embedded в
      karaoke-app?
- [ ] **alignment-ml** — запускается ли на проде (admin only)?
- [ ] **nginx path-proxy** — конфигурация для каждого внешнего
      сервиса (Pass 343+).

## Changelog

- **Pass 356** (2026-09-09): Initial C4 L2. Автор: agent (Karaoke).