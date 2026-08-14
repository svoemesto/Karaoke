---
status: Active
slug: 184-approve-status-choice
related:
  - ../domain/catalog.md
  - ../domain/processing.md
  - ../architecture/L3-components.md
  - ../../specs/184-approve-status-choice/spec.md
---

# 184 — Approve Status Choice (выбор idStatus 5/6) (LiveDoc)

> Drill-down — [specs/184-approve-status-choice/spec.md](../../specs/184-approve-status-choice/spec.md).

## Что делает

При апруве задания редактором (см. фичу 182) админ выбирает целевой `idStatus`:
**5 (Render)** или **6 (Demo)**. В зависимости от выбора запускается разный
авто-конвейер (рендер MP4, push в PROD, новости).

**Главный паттерн (см. Q&A в `AGENTS.md`)**: гейтить конвейер нужно по
**ФАКТИЧЕСКОМУ** состоянию записи ПОСЛЕ применения, а **НЕ** по запрошенному
значению параметра. Push самой сущности (не её derived-эффектов) — НЕ гейтится.

## User Stories (краткий список)

- **US1**: Админ открывает задание редактора в `webvue3` → видит radio «Render» / «Demo» → апрувит → выбранный конвейер запускается.
- **US2**: Песня уже в idStatus=6, админ выбирает «Render» → downgrade-ignore (`if (song.idStatus < targetIdStatus)` сохраняет высший статус), гейт ВСЕ РАВНО срабатывает (по факту), конвейер для финальной песни запускается.
- **US3**: UI правильно показывает radio (через `watch: a()` в переиспользуемой модалке).

## Functional Requirements (указатель)

- **FR-001**: Radio «Render» / «Demo» в `ReviewModal` (default = currentIdStatus).
- **FR-007**: `POST /api/songeditor/byId` возвращает `a.idStatus` (для UI-gate).
- **FR-008**: Конвейер гейтится по `song.idStatus >= 6L` (факт), не по запросу.
- **FR-009**: Push сущности в SSE/LOCAL→SERVER — НЕ гейтится.

## Acceptance Criteria

- [ ] **AC1**: Админ выбирает «Render» для песни в idStatus=5 → рендер MP4 запускается.
- [ ] **AC2**: Админ выбирает «Demo» для той же песни → DEMO-версия рендерится (1280×720@30fps).
- [ ] **AC3**: Админ выбирает «Render» для песни в idStatus=6 → гейт PASS (факт), конвейер запускается.
- [ ] **AC4**: Push записи в LOCAL→SERVER происходит всегда (не зависит от выбора).
- [ ] **AC5**: `watch: a()` сбрасывает radio на default при смене задания в модалке.

## Связанные LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (idStatus как VO) | [processing.md](../processing.md) (рендер-конвейер)
- Architecture: [L3-components.md](../architecture/L3-components.md) | [queue-lanes.md](../architecture/queue-lanes.md) (HEAVY_RENDER lane)

## Код

- Контроллер: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/PublicSongEditorController.kt`
- Frontend: `webvue3/src/components/Songs/ReviewModal.vue` (radio + watch)
- Pipeline: `Utils.kt:executeRenderMp4()` — диспетчеризация LYRICS/KARAOKE/DEMO
- Демо-параметры: `ApiController.kt` (1280×720@30fps defaults)

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14