# Tasks: VK proxy-fallback resilience (Pass 437, #161)

**Spec**: [`spec.md`](./spec.md) | **Branch**: `437-vk-proxy-fallback-resilience`

## Phase 1 — Fix

- [X] T001 `VkApiClient.kt`: добавить `VkNetworkException` + `VkSendMode` + `decideSendMode`.
- [X] T002 `VkApiClient.send()`: при `vkProxyUrl==""` не бросать `IllegalStateException`
      — повторный прямой запрос, затем `VkNetworkException`. (FR-001, FR-002)
- [X] T003 `VkAutoPublishService.publishTextOnly`: catch → `writeFailure` → `SEND_FAILED`. (FR-003)
- [X] T004 `VkAutoPublishService.publishFile`: catch → `writeFailure` → `SEND_FAILED`. (FR-003)
- [X] T005 `VkAutoPublishScheduler.publishNewsWithoutVideo`: catch + лог, без пометки опубликованным. (FR-004)
- [X] T006 `VkPhotoUploadClient`: `VkNetworkException` → `VkPhotoTransientException` (photos + docs). (FR-005)
- [X] T007 Обновить KDoc `VkApiClient` (proxy опционален, `VkNetworkException`).

## Phase 2 — Tests

- [X] T008 `VkApiClientSendModeTest.kt` (NEW, 8 тестов): `decideSendMode`, `VkNetworkException`. (FR-007, NFR-003)
- [X] T009 `:karaoke-app:test --tests VkApiClientSendModeTest` PASS.

## Phase 3 — Knowledge (SSoT)

- [X] T010 `knowledge/domains/integration/components/external-api-clients.md` — proxy-fallback + changelog.
- [X] T011 `knowledge/domains/integration/domain.md` — Domain Invariant #4 + changelog.
- [X] T012 `knowledge/domains/publishing/components/publishing-services.md` — resilience + changelog.

## Phase 4 — Verification

- [X] T013 `:karaoke-app:compileKotlin` / `compileTestKotlin` OK.
- [X] T014 `:karaoke-app:ktlintCheck` OK.
- [X] T015 `tools/check-knowledge-structure.sh` 9/9.
- [X] T016 `tools/check-spec-issue-link.py` OK.
- [X] T017 `gh pr checks` all PASS (после push).
