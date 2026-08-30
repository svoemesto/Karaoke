---
description: "Task list для 249-public-settings-cache — TTL-кеш для getProperty (Tier-2, FR-006)"
---

# Tasks: TTL-кеш для PublicSettingsWebController.getProperty

**Input**: Design documents from `/specs/249-public-settings-cache/`
- plan.md (required)
- spec.md (required for user stories)

## Phase 1: Setup

- [x] T001 Создать спеку (spec.md) с FR-001..FR-009 и Success Criteria
- [x] T002 Создать plan.md с Implementation Steps + Risks
- [x] T003 Создать checklists/requirements.md
- [x] T004 Создать tasks.md (этот файл)

## Phase 2: Foundational

- [ ] T005 Создать feature-ветку `249-public-settings-cache` от master
- [ ] T006 Создать LiveDoc `livedocs/features/249-public-settings-cache.md`
- [ ] T007 Обновить `PublicSettingsWebController.kt`:
  - [ ] T007a Добавить `companion object` (cache, dirty, CACHE_TTL_MS, NOT_FOUND_SENTINEL, data class CachedProperty)
  - [ ] T007b Добавить `getCachedProperty(key, loadFn)` helper с KDoc
  - [ ] T007c Добавить `markDirty()` / `consumeDirty()` методы с KDoc
  - [ ] T007d Добавить `isCacheEnabled()` helper с KDoc
  - [ ] T007e Изменить `getProperty` — обернуть в `getCachedProperty`
  - [ ] T007f Изменить `setProperty` — вызвать `markDirty()` при успехе

## Phase 3: Polish

- [ ] T008 Проверить все 7 CI gates:
  - [ ] `./gradlew :karaoke-web:compileKotlin --parallel`
  - [ ] `./gradlew :karaoke-web:ktlintCheck`
  - [ ] `./gradlew :karaoke-web:bootJar --parallel`
  - [ ] `bash tools/check-kdoc-coverage.sh`
  - [ ] `cd webvue3 && npm run lint:check` (нетронут — sanity check)
  - [ ] `pre-commit run --all-files`
- [ ] T009 Создать PR через `gh pr create --base master`
- [ ] T010 Дождаться `gh pr checks` (CI 7/7 PASS)
- [ ] T011 Merge в master
- [ ] T012 Обновить parent спеку 241:
  - [ ] `specs/241-db-storage-perf-audit/tasks.md` — T012.2 → `[x]`
  - [ ] `livedocs/architecture-notes.md` §Pass 241 — отметить FR-006 как сделанный

## Definition of Done

- [ ] spec.md содержит FR-001..FR-009 + Success Criteria + Clarifications
- [ ] plan.md содержит Implementation Steps + Risks + Constitution Check
- [ ] PublicSettingsWebController.kt обновлён (companion object + helper'ы + KDoc 100%)
- [ ] setProperty вызывает markDirty() при успехе
- [ ] getProperty обёрнут в getCachedProperty
- [ ] LiveDoc создан в `livedocs/features/249-public-settings-cache.md`
- [ ] Все 7 CI gates PASS
- [ ] PR создан и замержен в master
- [ ] Parent спека 241 обновлена (T012.2 → done)

## Notes

- Эта фича — Tier-2 P1 из parent спеки 241, FR-006 (см. spec.md Clarifications Session 2026-08-26).
- Паттерн повторяет проверенный подход из sister-фичи 248-authors-tiles-cache (PR #370 MERGED).
- На проде (`karaoke-web` без admin-UI) endpoint не используется — эффект только на админ-машине.
- См. plan.md Risks & Mitigations для деталей про `NOT_FOUND_SENTINEL`, race conditions и TTL.