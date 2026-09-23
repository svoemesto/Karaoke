# Feature Specification: VkApiClient — отсутствие vkProxyUrl не должно валить публикацию (Pass 437, #161)

**Feature Branch**: `437-vk-proxy-fallback-resilience`
**Created**: 2026-09-23
**Status**: Draft
**Input**: Логи `karaoke-app` (2026-09-23 06:17-06:18 MSK):
`java.lang.IllegalStateException: VK недоступен напрямую, а vkProxyUrl не задан`
(`VkApiClient.kt:462`) × 12 подряд, из `VkAutoPublishService.publishTextOnly`
(`VkAutoPublishService.kt:474`), `PremiumAutoPublishScheduler.tick` (каждые 30 с).
Первопричина — единичный `Connection reset` при прямом запросе к `api.vk.ru`;
`vkProxyUrl` не задан (прокси для VK опционален).

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

- **Issue ID**: `#161` («VK недоступен напрямую + vkProxyUrl не задан → IllegalStateException валит публикацию»).
- **Title**: «VK недоступен напрямую + vkProxyUrl не задан → IllegalStateException валит публикацию».
- **Created in OpenProject**: 2026-09-23.
- **Workflow**:
  1. **Claim**: `bash tools/tracker.sh claim-issue 161` — выполнено 2026-09-23
     (статус `In progress`; PATCH вручную, т.к. `tracker.sh claim-issue`
     падает на смене assignee для Task в проекте Karaoke — прецедент #152).
  2. **Add comment с отчётом** (после merge):
     `bash tools/tracker.sh add-comment 161 --file specs/437-vk-proxy-fallback-resilience/report.md`.
  3. **Mark review** (после add-comment): `bash tools/tracker.sh mark-review 161`.
  4. **Close** (owner, после merge + согласия на рестарт `karaoke-app`): `bash tools/tracker.sh close-issue 161`.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-23
- **Grep-запросы**:
  1. `grep -rln "vkProxyUrl|прокси" knowledge/` →
     `integration/components/external-api-clients.md`, `integration/domain.md`,
     `storage/*`, `karaoke-web/domain.md`, `sse/domain.md`.
  2. `grep -rln "VkApiClient|wall.post" knowledge/` →
     `integration/components/external-api-clients.md`, `integration/domain.md`,
     `processing/components/schedulers.md`, `publishing/components/publishing-services.md`.
  3. `grep -rln "SEND_FAILED|send_failed|VkAutoPublishState" knowledge/` →
     `publishing/components/publishing-services.md`.
  4. `grep -rln "retry|backoff|FR-009" knowledge/` →
     `adr/local-0002-save-exception-handling.md`, `guidelines/architecture-conventions.md`,
     `processing/components/schedulers.md`, `integration/*`.
  5. `docker exec karaoke-app curl -sS https://api.vk.ru/method/wall.post` →
     HTTP 200 (0.1 с) × 8/8 — прямой доступ к VK из контейнера **есть**;
     сбой был единичным, а разросся из-за необработанного исключения.

### Knowledge files consulted

- [`knowledge/domains/integration/components/external-api-clients.md`](../../knowledge/domains/integration/components/external-api-clients.md)
  — паттерн `VkApiClient` / proxy-fallback (по образцу `TelegramApiClient`).
- [`knowledge/domains/integration/domain.md`](../../knowledge/domains/integration/domain.md)
  — Domain Invariants (внешние вызовы, rate-limits, retry policy).
- [`knowledge/domains/publishing/components/publishing-services.md`](../../knowledge/domains/publishing/components/publishing-services.md)
  — `VkAutoPublishService`, failure modes, error handling.
- [`knowledge/domains/processing/components/schedulers.md`](../../knowledge/domains/processing/components/schedulers.md)
  — `VkAutoPublishScheduler`, `PremiumAutoPublishScheduler`, ловушки scheduler'ов.
- [`knowledge/adr/local-0002-save-exception-handling.md`](../../knowledge/adr/local-0002-save-exception-handling.md)
  — «НЕТ silent fallback'ов в слое service» → ошибку **записываем**, а не глотаем.
- [`knowledge/adr/local-0005-structured-logging-karaoke-app.md`](../../knowledge/adr/local-0005-structured-logging-karaoke-app.md)
  — конвенция диагностического логирования.

### Прецедент

2026-09-23 06:17:37 MSK: `VkApiClient: прямой доступ недоступен (Connection reset),
переключение на прокси` → на следующем вызове `proxyClient()` вернул `null`
(`vkProxyUrl` пуст) → `IllegalStateException` (12×). До этого единичного сбоя
`wallPost: success` (05:24) — т.е. VK доступен, деградация не имела оснований
оставаться фатальной. Паттерн полностью совпадает с `TelegramApiClient.send()`
(`TelegramApiClient.kt:168-171`), но для Telegram прокси **обязателен** по
инфраструктуре, а для VK — опционален (КД `VkApiClient.kt:261`: «Если `vkProxyUrl`
не задан — ошибка пробрасывается»). Проброс должен быть **типизированной сетевой
ошибкой**, которую оркестратор конвертирует в `SEND_FAILED`, а не «сырым»
`IllegalStateException`, валящим тик scheduler'а.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Единичный сетевой сбой не валит публикацию (Priority: P1)

**Описание**: Прямой запрос к `api.vk.ru` падает (transient), `vkProxyUrl` не
задан. Бот записывает `SEND_FAILED` + `vkAutoPublishLastError`, песня остаётся
в очереди на следующий тик; исключение НЕ пролетает в scheduler.

**Independent Test**: Unit-тест `decideSendMode` (нет прокси → всегда `DIRECT`,
даже после падения) + code review `publishTextOnly`/`publishFile` (catch → `writeFailure`).

**Acceptance Scenarios**:

1. **Given** `vkProxyUrl=""` и `useProxy=true` (был сбой), **When** `send(request)`,
   **Then** попытка `DIRECT` (а не `IllegalStateException`); при повторном сбое —
   `VkNetworkException` с исходной причиной.
2. **Given** `wallPost` бросил `VkNetworkException`, **When** `publishTextOnly`,
   **Then** `vkAutoPublishLastError` заполнен, `state=SEND_FAILED`, исключение не
   покидает метод.
3. **Given** `VkAutoPublishScheduler.publishNewsWithoutVideo`, **When** `wallPost`
   падает, **Then** пост не считается опубликованным (не добавляется во внутренний
   `Set`), ошибка логируется, тик продолжается.
4. **Given** `VkPhotoUploadClient.uploadCover` и сбой `send`, **When** `photos.*`,
   **Then** ошибка классифицируется как transient (retry/fallback), не покидает метод.

### User Story 2 — Прокси-fallback сохраняется, если `vkProxyUrl` задан (Priority: P2)

**Описание**: При заданном `vkProxyUrl` поведение не меняется: прямой → прокси на
TTL-окно, авто-возврат на прямой при восстановлении.

**Acceptance Scenarios**:

1. **Given** `vkProxyUrl` задан, `useProxy=true`, TTL не истёк, **When** `decideSendMode`,
   **Then** `PROXY`.
2. **Given** `vkProxyUrl` задан, TTL истёк, **When** `decideSendMode`, **Then** `DIRECT`.

## Requirements *(mandatory)*

### Functional

- **FR-001**: `VkApiClient.send()` MUST NOT бросать `IllegalStateException` при
  пустом `vkProxyUrl`. Если `vkProxyUrl` не задан, proxy-режим MUST быть сброшен
  (`useProxy=false`) и MUST быть выполнена попытка прямого запроса.
- **FR-002**: При повторном сбое прямого запроса без прокси `send()` MUST бросать
  типизированную `VkNetworkException` (с исходной причиной в `cause`).
- **FR-003**: `VkAutoPublishService.publishTextOnly` и `publishFile` MUST ловить
  исключения от `client.wallPost` / `client.sendPostWithVideo`, вызывать
  `writeFailure` и возвращать `VkAutoPublishResult(SEND_FAILED)`.
- **FR-004**: `VkAutoPublishScheduler.publishNewsWithoutVideo` MUST NOT считать
  пост опубликованным при исключении; ошибка MUST логироваться.
- **FR-005**: `VkPhotoUploadClient` MUST конвертировать `VkNetworkException` в
  `VkPhotoTransientException` (существующая transient-семантика: retry + fallback).
- **FR-006**: При заданном `vkProxyUrl` поведение proxy-fallback (direct → proxy
  на TTL, авто-возврат) MUST сохраниться без изменений.
- **FR-007**: Решение «direct vs proxy» MUST быть выделено в чистую функцию
  (companion `decideSendMode`) и покрыто unit-тестами.

### Non-Functional

- **NFR-001**: Не менять публичные сигнатуры `VkApiClient` (`wallPost`,
  `sendPostWithVideo`, `getWallUploadServer`, …) и `VkAutoPublishResult`.
- **NFR-002**: `VkAutoPublishScheduler.tick` MUST оставаться «непрерывным» —
  один сбой не должен останавливать обработку остальных кандидатов.
- **NFR-003**: Unit-тесты без сети/БД/VK (по образцу `SyncRemoteClientTest`).
- **NFR-004**: Запись ошибки — через существующий `writeFailure` (не глотать,
  см. ADR local-0002).

### Key Entities

- **`VkApiClient`** (MODIFY) — `send()`, `decideSendMode`, `VkSendMode`.
- **`VkNetworkException`** (NEW) — типизированная сетевая ошибка VK.
- **`VkAutoPublishService`** (MODIFY) — catch в `publishTextOnly` / `publishFile`.
- **`VkAutoPublishScheduler`** (MODIFY) — catch в `publishNewsWithoutVideo`.
- **`VkPhotoUploadClient`** (MODIFY) — `VkNetworkException` → transient.

## Success Criteria *(mandatory)*

- **SC-001**: Сценарий из логов воспроизводится без `IllegalStateException`:
  `vkProxyUrl` пуст + сбой direct → `SEND_FAILED`, запись в `vkAutoPublishLastError`.
- **SC-002**: `:karaoke-app:test` PASS (новые тесты `decideSendMode`), `ktlintCheck` 0,
  `:karaoke-app:compileKotlin` OK.
- **SC-003**: `check-knowledge-structure.sh` 9/9; knowledge-документы обновлены
  (external-api-clients, publishing-services, integration domain changelog).
- **SC-004**: `gh pr checks` all PASS.

## Assumptions

1. `vkProxyUrl` пуст — легитимная конфигурация (прокси опционален для VK).
2. Единичные сетевые сбои direct — transient; retry — на следующем тике scheduler'а
   (30 с для premium, 60 с для air). Отдельный backoff внутри `send()` не вводим.
3. `VkNetworkException` — `RuntimeException`, чтобы не менять сигнатуры.

## Out of Scope

- Настройка `vkProxyUrl` на проде (ops, отдельно).
- Введение backoff/retry внутри `send()` (есть на уровне scheduler'ов).
- Унификация `println` → SLF4J в VK-сервисах (TODO в knowledge).

## Migration Path

### Что нужно изменить

- `VkApiClient.kt` — `send()`, `decideSendMode`, `VkNetworkException`.
- `VkAutoPublishService.kt` — catch в `publishTextOnly` / `publishFile`.
- `VkAutoPublishScheduler.kt` — catch в `publishNewsWithoutVideo`.
- `VkPhotoUploadClient.kt` — transient-конверсия.
- `karaoke-app/src/test/.../VkApiClientSendModeTest.kt` (NEW) — unit-тесты.
- `knowledge/domains/integration/components/external-api-clients.md`,
  `knowledge/domains/integration/domain.md`,
  `knowledge/domains/publishing/components/publishing-services.md` (MODIFY).

### Что НЕ нужно менять

- Схема БД, `VkAutoPublishResult`, публичные сигнатуры, `TelegramApiClient`.

## Validation

| Проверка | Ожидаемо |
|---|---|
| `decideSendMode(hasProxy=false)` | `DIRECT` при любом `useProxy` |
| `publishTextOnly` при `VkNetworkException` | `SEND_FAILED` + `vkAutoPublishLastError` |
| `:karaoke-app:test` | PASS |
| `:karaoke-app:ktlintCheck` | OK |
| `gh pr checks` | all PASS |

## Rollback

`git revert <merge-commit>` — поведение возвращается к пробросу
`IllegalStateException`; данные не теряются.
