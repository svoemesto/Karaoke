# Report: VK proxy-fallback resilience (Pass 437, #161)

**Spec**: [`spec.md`](./spec.md) | **Tasks**: [`tasks.md`](./tasks.md) | **Branch**: `437-vk-proxy-fallback-resilience`

## Проблема

Лог `karaoke-app` (2026-09-23 06:17-06:18 MSK), 12× подряд:

```
java.lang.IllegalStateException: VK недоступен напрямую, а vkProxyUrl не задан
    at VkApiClient.send(VkApiClient.kt:462)
    at VkApiClient.tryWallPost(VkApiClient.kt:566)
    at VkApiClient.wallPost(VkApiClient.kt:512)
    at VkAutoPublishService.publishTextOnly(VkAutoPublishService.kt:474)
    at VkAutoPublishService.publishToVk(VkAutoPublishService.kt:122)
    at PremiumAutoPublishScheduler.processSong(PremiumAutoPublishScheduler.kt:249)
    at PremiumAutoPublishScheduler.tick(...)
```

## Диагностика (по AGENTS.md § «Диагностика через docker logs»)

1. `docker logs` показал **единичный** `VkApiClient: прямой доступ недоступен
   (Connection reset), переключение на прокси` в 06:17:37 → `useProxy=true`.
2. `vkProxyUrl` в `/sm-karaoke/system/Karaoke.properties` **отсутствует**
   (`telegramProxyUrl` есть, `vkProxyUrl` — нет) → `proxyClient()` вернул `null`.
3. `PremiumAutoPublishScheduler.tick` (каждые 30 с) повторял попытку; try/catch
   в `tick` только печатал stack trace → 12 одинаковых исключений за минуту.
4. Проверка изнутри контейнера: `curl https://api.vk.ru/method/wall.post` →
   HTTP 200 за 0.1 с, **8/8** попыток. То есть VK доступен напрямую; падение —
   следствие одиночного сетевого сбоя + необработанного исключения, а не
   устойчивой недоступности.

**Корневая причина**: для VK прокси **опционален**, но `send()` превращал его
отсутствие в фатальное `IllegalStateException` вместо `SEND_FAILED`-результата
(как в остальных VK-ветках).

## Изменения

### `VkApiClient.kt`

- Добавлены `VkNetworkException` (типизированная transient-ошибка, `cause` сохранён)
  и `VkSendMode` (enum).
- `decideSendMode(hasProxy, useProxy, modeSetAtMs, now, ttl)` — чистая функция:
  `hasProxy=false` → всегда `DIRECT`.
- `send()`: при пустом `vkProxyUrl` — сброс proxy-режима, повторный прямой запрос,
  затем `VkNetworkException`. Если `vkProxyUrl` пропал между тиками — тоже не
  фатально (повтор direct).
- KDoc обновлён: прокси опционален.

### Оркестраторы

- `VkAutoPublishService.publishTextOnly` / `publishFile`: `try/catch` вокруг
  `client.wallPost` / `client.sendPostWithVideo` → `writeFailure` →
  `VkAutoPublishResult(SEND_FAILED)`. Исключение не покидает метод.
- `VkAutoPublishScheduler.publishNewsWithoutVideo`: `try/catch`, пост **не**
  помечается опубликованным, ошибка логируется (повтор на следующем тике).
- `VkPhotoUploadClient`: `VkNetworkException` → `VkPhotoTransientException`
  (существующая transient-семантика: retry + fallback на `docs.*`).

### Тесты

`karaoke-app/src/test/.../VkApiClientSendModeTest.kt` (NEW, 8 тестов) —
без сети/VK/БД: `decideSendMode` (нет прокси / прокси / TTL-граница) +
`VkNetworkException`.

## Verification

| Проверка | Результат |
|---|---|
| `:karaoke-app:compileKotlin` / `compileTestKotlin` | OK |
| `:karaoke-app:test --tests VkApiClientSendModeTest` | 8/8 PASS |
| `:karaoke-app:ktlintCheck` | PASS |
| `tools/check-knowledge-structure.sh` | 9/9 |
| `tools/check-spec-issue-link.py` | OK (34/34 modern) |

## Knowledge References

- [external-api-clients.md](../../knowledge/domains/integration/components/external-api-clients.md) — proxy-fallback.
- [integration/domain.md](../../knowledge/domains/integration/domain.md) — Domain Invariant #4.
- [publishing-services.md](../../knowledge/domains/publishing/components/publishing-services.md) — resilience.
- [ADR local-0002](../../knowledge/adr/local-0002-save-exception-handling.md) — не глотать ошибку, а записывать.

## Follow-up (ops, вне scope)

- Выставить `vkProxyUrl=http://karaoke-telegram-proxy:1082` в
  `/sm-karaoke/system/Karaoke.properties` (прокси уже ходит в VK — проверено)
  как второй транспорт. С этим фиксом это уже не критично, но снижает
  вероятность единичных сбоев.

## Ограничения

- Рестарт `karaoke-app` — только владелец (AGENTS.md § Machine-Specific
  Exceptions, `nsa-i9`).
