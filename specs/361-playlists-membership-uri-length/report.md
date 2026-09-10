# Report: Spec #361 / OpenProject #77 — 414 Request-URI Too Large на /playlists/membership

> **Branch**: `361-playlists-membership-uri-length`
> **Issue**: OpenProject #77 (репорт о 414 в консоли при открытии песен автора «Машина Времени» — ≈2500 песен, URL > 8 КБ)
> **Status**: Implementation complete (T001..T012, T020-T025, T029 done; T013-T019 + T026, T028, T030 — DEFERRED до deploy)
> **Date**: 2026-09-10

## Что сделано

Реализован переход с `GET /api/public/account/playlists/membership?ids=<csv>`
на `POST /api/public/account/playlists/membership` с JSON-телом
`{"ids": number[]}`. Это решает баг #77 — у крупных авторов (≈2500+ песен)
URL превышает 8 КБ nginx `large_client_header_buffers` и браузер получает
HTTP 414 вместо membership-карты.

## Изменённые файлы

| Файл | Что сделано |
|---|---|
| `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/MembershipRequest.kt` | **Новый** DTO `MembershipRequest(val ids: List<Long>)` с KDoc + `@see` на docs/features/playlist-membership.md. |
| `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlaylistController.kt` | Добавлен `@PostMapping("/playlists/membership") membershipPost(@RequestBody, HttpServletRequest)`. Общая логика вынесена в private `buildMembershipResponse(user, songIds)`. Старый `@GetMapping` сохранён для backward-compat (DEPRECATED в JSDoc). |
| `karaoke-public/src/services/authApi.js` | Добавлена новая функция `authPostJson(path, jsonBody, token)` с JSDoc. Существующая `authPost` (form-encoded) не тронута — используется в 10+ местах. |
| `karaoke-public/src/services/playlistApi.js` | `fetchMembership(ids)` переписан с `authGet(...?ids=csv)` на `authPostJson('/playlists/membership', { ids }, token())`. Dead code `qs()` удалён. |

## Новые / обновлённые документы

| Файл | Что сделано |
|---|---|
| `docs/features/playlist-membership.md` | **Новый** per-feature документ (Constitution FR-009). Контракт endpoint'ов, curl-примеры, changelog. |
| `knowledge/adr/0009-get-vs-post-large-payload.md` | **Новый** ADR — обоснование выбора POST (vs увеличить nginx лимит / chunked / GraphQL / form-encoded / multipart). |
| `knowledge/domains/karaoke-web/components/public-controllers-6.md` | Добавлено упоминание POST + GET (DEPRECATED) для `/playlists/membership`. |
| `knowledge/system/frontend/composable-playlist-membership.md` | Обновлены Hot paths (POST теперь основной, GET — DEPRECATED). Changelog + Pass 361. |

## Артефакты спеки

Все Phase-1 артефакты на месте:
- `specs/361-playlists-membership-uri-length/spec.md` (425 строк, Knowledge-first pre-flight, FR-001..FR-018, SC-001..SC-006, US1-US3, Clarifications § Session 2026-09-10 — JWT/CSRF).
- `specs/361-playlists-membership-uri-length/plan.md` (Constitution Check по всем принципам).
- `specs/361-playlists-membership-uri-length/research.md` (Decision 1 + Alternatives + nginx limits).
- `specs/361-playlists-membership-uri-length/data-model.md` (MembershipRequest DTO).
- `specs/361-playlists-membership-uri-length/contracts/api-public-account-playlists-membership.md` (OpenAPI-style контракт обоих методов).
- `specs/361-playlists-membership-uri-length/quickstart.md` (ручная проверка по шагам).
- `specs/361-playlists-membership-uri-length/checklists/requirements.md` (Quality Checklist — все `[x]`).
- `specs/361-playlists-membership-uri-length/tasks.md` (30 tasks: 4 Setup + 3 Foundational + 7 US1 + 2 US2 + 3 US3 + 11 Polish).

## Локальные проверки (DONE)

- ✅ Backend compile: `gradle :karaoke-web:compileKotlin` — BUILD SUCCESSFUL in 31s.
- ✅ Backend ktlint: `gradle :karaoke-web:ktlintCheck` — SUCCESSFUL (после фикса missing newline в DTO).
- ✅ Frontend build: `npm run build` в `karaoke-public/` — ✓ built in 3.65s, 295 modules transformed.
- ✅ Frontend lint: `npm run lint:check` — clean (max-warnings 0).
- ✅ Prettier: `npx prettier --check "src/**/*.{vue,js,ts,json}"` — All matched files use Prettier code style.
- ✅ KDoc coverage: `tools/check-kdoc-coverage.sh --strict` — 96.1% (>50% threshold).
- ✅ JSDoc coverage: `tools/check-jsdoc-coverage.sh karaoke-public --strict` — 94.0% (>50% threshold).
- ✅ nginx `client_max_body_size` ≥ 20M (см. `deploy/web-server-deploy/deploy/nginx.conf:88`).

## Отложено до deploy (BLOCKED на sandbox restriction)

- ⚠️ Docker build: sandbox не позволяет записать в `/home/nsa/.docker/buildx/activity/`.
  Пользователь должен вручную:
  ```bash
  cd /home/nsa/Karaoke/deploy
  GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle FORCE=1 bash do.sh build_web
  docker restart karaoke-web
  cd /home/nsa/Karaoke
  FORCE=1 GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle bash do.sh build_public
  docker restart karaoke-public
  ```
- ⚠️ T013 (curl POST) — DEFERRED до развертывания.
- ⚠️ T014 (browser test) — DEFERRED.
- ⚠️ T015-T019 (US2/US3 negative tests) — DEFERRED.
- ⚠️ T028 (tracker.sh add-comment + mark-review) — выполню после deploy.

## Известные проблемы / Caveats

1. **Sandbox restriction**: `/home/nsa/.docker/buildx/activity/` read-only — Docker build не может стартовать из этого процесса. Пользователь должен выполнить `bash do.sh build_web` вручную.
2. **nginx limits на проде**: проверены dev-машины (≥ 20M), но прод-сервер должен быть проверен владельцем. См. quickstart.md § 2.
3. **Backward-compat период**: GET остаётся работать. После стабилизации POST (1-2 месяца) — пометить GET `@Deprecated` в Kotlin, добавить WARN-лог. После ещё 1-2 месяцев — решить об удалении GET по метрике `membership_get_calls_total`.

## Чек-лист для владельца

- [ ] Merge PR #361
- [ ] Запустить `bash do.sh build_web` + `docker restart karaoke-web` (см. AGENTS.md § DIAGNOSTICS).
- [ ] Запустить `bash do.sh build_public` + `docker restart karaoke-public`.
- [ ] Залогиниться, открыть `/zakroma?author=Машина Времени`, проверить иконки избранного/плейлистов.
- [ ] В DevTools Network найти `playlists/membership` — должен быть POST, status 200, JSON body.
- [ ] Проверить nginx limits на проде (`nginx -T | grep client_max_body_size`).
- [ ] После проверки — закрыть Issue #77 в OpenProject (`tracker.sh close-issue 77`).
- [ ] Удалить ветку после merge (или оставить — по policy проекта).