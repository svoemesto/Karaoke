---
id: domain-integration
title: "Domain: Integration (внешние API)"
status: Active
slug: integration
related:
  - ../storage/domain.md
  - ../processing/domain.md
---

# Domain: Integration (внешние API)

> Bounded context для внешних HTTP-сервисов, с которыми
> взаимодействует Karaoke: VK, Telegram, Yandex Captcha, LM Studio,
> Whisper ASR, Forced Alignment, GeoIP. Прецедент создания — Pass 345
> Knowledge-аудита.

## Обзор контекста (Bounded Context)

Все **внешние HTTP-клиенты** проекта. Это сервисы, которые **не
являются частью Karaoke**, но с которыми мы общаемся через HTTP API.

**Архитектурное решение**: внешние сервисы идут через
**nginx path-proxy** (`minio-proxy`, `yookassa-proxy`,
`telegram-proxy`, и т.д.), чтобы обойти MTU black-hole в
docker-сети и подписать SigV4 если нужно. См.
[storage-flow.md](../storage/components/storage-flow.md).

**Граница**: контекст НЕ отвечает за:

- Хранение результатов в БД (это другие домены).
- Расписание публикаций — см. [schedulers.md](../processing/components/schedulers.md).

## Ubiquitous Language | Единый язык

| Термин | Определение |
|---|---|
| **`VkApiClient`** | Тонкая обёртка над VK Open API (wall.post, photos.*). |
| **`VkPhotoUploadClient`** | HTTP-оркестратор загрузки обложки в группу VK. |
| **`VkPreviewWarmupClient`** | Прогрев preview-кэша VK перед публикацией. |
| **`TelegramApiClient`** | Telegram Bot API (sendMessage, sendPhoto). |
| **`TelegramUpdatesConsumer`** | Long-polling для отлова channel_post. |
| **`TelegramProxyManager`** | Управление SOCKS-прокси (MTU black-hole). |
| **`LmStudioService`** | OpenAI-совместимый /v1/chat/completions (LAN-only). |
| **`WhisperAsrService`** | Whisper ASR (HTTP multipart). |
| **`AlignmentServiceClient`** | Forced alignment маркеров. |
| **`GeoIpService`** | api.country.is (с двухуровневым кэшем). |
| **`YandexCaptchaValidationService`** | SmartCaptcha (Yandex). |

## Архитектурные решения

### Решение 1: nginx-proxy

Все внешние HTTP-вызовы из контейнеров идут через
**host-level nginx path-proxy** (тот же паттерн, что и для MinIO,
YooKassa, Captcha). Это обходит MTU black-hole в docker-сети и
позволяет подписанные S3-вызовы для MinIO.

### Решение 2: LM Studio — LAN-only

LM Studio с включённым «Обслуживание по локальной сети» слушает
конкретный LAN IP хоста (НЕ `0.0.0.0`), поэтому
`host.docker.internal` НЕ подходит. URL настраивается через
`lmStudioUrl` в `KaraokeProperties`.

### Решение 3: Telegram rate-limits

Telegram Bot API имеет жёсткий rate-limit (1 запрос/сек на токен).
Решение — встроенный rate-limiter или очередь `KaraokeProcess` (см.
[async-process-queue.md](../processing/components/async-process-queue.md)).

## Domain Invariants

1. **Все внешние вызовы MUST идти через nginx-proxy** (не
   напрямую из контейнера).
2. **API ключи MUST быть в env-переменных** (`@Value` / `System.getenv`),
   не в коде (Constitution VIII).
3. **Rate-limits MUST быть обработаны** (Telegram 1/sec, VK 3/sec).

## Hot paths

| Клиент | Частота |
|---|---|
| `TelegramUpdatesConsumer` | long-polling (постоянное соединение) |
| `TelegramAutoPublishScheduler` → `TelegramApiClient` | раз в 60с |
| `VkAutoPublishScheduler` → `VkApiClient` + `VkPhotoUploadClient` | раз в 60с |
| `GeoIpService` | per событие (через `WebEvent` analytics) |
| `YandexCaptchaValidationService` | per login/register |

## Код (физическая реализация)

- `karaoke-app/.../services/Vk*.kt` (7 файлов)
- `karaoke-app/.../services/Telegram*.kt` (7 файлов)
- `karaoke-app/.../services/LmStudioService.kt`
- `karaoke-app/.../services/WhisperAsrService.kt`
- `karaoke-app/.../services/AlignmentServiceClient.kt`
- `karaoke-app/.../services/GeoIpService.kt`
- `karaoke-web/.../services/YandexCaptchaValidationService.kt`

Подробное описание каждого клиента — см.
[external-api-clients.md](components/external-api-clients.md).

## Known gaps

- [ ] **Конкретные эндпоинты** каждого клиента (что и куда шлёт).
- [ ] **Rate-limit handling** — Telegram (1 req/sec) и VK (3 req/sec).
- [ ] **Retry policy** при HTTP failure.
- [ ] **MTU black-hole**: конфигурация nginx-proxy для каждого
      внешнего сервиса.
- [ ] **`alignment-ml`** Python-сервис — отдельный документ.

## Changelog

- **Pass 345** (2026-09-09): Initial. Автор: agent (Karaoke).