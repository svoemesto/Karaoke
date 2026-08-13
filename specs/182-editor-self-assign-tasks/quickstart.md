# Quickstart: Self-Assign Tasks — Validation Guide

**Phase 1 output** — `/speckit.plan`  
**Created**: 2026-08-13

Этот документ — **runnable validation guide**: шаги ручной проверки, что фича работает end-to-end. Implementation details — в `tasks.md` (создаётся через `/speckit.tasks`).

## Prerequisites

- Локальная БД `karaoke` поднята (`docker compose up -d karaoke-db`).
- `karaoke-app` собран (`./gradlew karaoke-app:bootJar`).
- `karaoke-web` собран (`./gradlew karaoke-web:bootJar`).
- `webvue3` собран (`cd webvue3 && npm run build`).
- `karaoke-public` собран (`cd deploy && bash do.sh build_start_public`).
- Миграция применена: `docker exec -i karaoke-db psql -U postgres -d karaoke < deploy/karaoke-db/XX_add_can_self_assign_tasks.sql`.
- Залогинены 2 редактора (test1@test.test / test2@test.test) и 1 админ (`admin@admin.local`).
- Тестовый автор с ≥3 свободными песнями (например, "Аквариум" — любой, у которого есть песни без `tbl_song_assignments`).

## Setup

1. **Включить флаг** в `SiteUserEdit` для обоих тестовых редакторов:
   - Залогиниться в `webvue3` под `admin@admin.local`.
   - Открыть `SiteUsers`, выбрать `test1@test.test`, открыть карточку.
   - Поставить галочку `Является редактором` (если не стоит), затем ниже — **«Может сам назначать себе задания»**.
   - Сохранить. Перезагрузить. Убедиться, что галочка осталась.
   - Повторить для `test2@test.test`.

2. **Проверить, что БД обновилась**:
   ```bash
   docker exec -i karaoke-db psql -U postgres -d karaoke \
     -c "SELECT id, email, is_editor, can_self_assign_tasks FROM tbl_site_users WHERE email IN ('test1@test.test', 'test2@test.test');"
   ```
   Ожидается `is_editor=true`, `can_self_assign_tasks=true` для обоих.

## Scenario 1: Счастливый путь — взять свободную песню

**Цель**: SC-001, US2, Acceptance Scenario 1.

1. Залогиниться в `karaoke-public` (`https://localhost:8897`) под `test1@test.test`.
2. Перейти на страницу автора (например, `/zakroma?author=Аквариум`).
3. **Ожидается**: рядом с каждой **свободной** песней (по которой нет `tbl_song_assignments`) отображается кнопка «Взять в работу». Для занятых — кнопки нет.
4. Кликнуть «Взять в работу» на любой свободной песне.
5. **Ожидается через ≤2 сек**:
   - Toast «Задание взято в работу».
   - Кнопка в этой карточке заменяется на «Открыть задание» (или исчезает — допустимо).
   - В БД:
     ```bash
     docker exec -i karaoke-db psql -U postgres -d karaoke \
       -c "SELECT id, song_id, assignee_id, admin_status FROM tbl_song_assignments WHERE assignee_id = (SELECT id FROM tbl_site_users WHERE email='test1@test.test') ORDER BY id DESC LIMIT 1;"
     ```
   - Возвращает 1 строку, `assignee_id = user.id`, `admin_status = 'open'`.

## Scenario 2: Self-assign повторный клик (идемпотентность)

**Цель**: FR-005 (идемпотентность).

1. Не разлогиниваясь, перезагрузить страницу автора.
2. **Ожидается**: ранее взятая песня по-прежнему в списке, но с кнопкой «Открыть задание» (НЕ «Взять в работу»).
3. **Опционально**: через DevTools / curl вызвать `POST /api/public/songeditor/assign-self` с тем же `songId` повторно.
4. **Ожидается**: HTTP 200 OK с `{"ok":true, "idempotent":true, "id": <тот же id>}`.

## Scenario 3: Гонка — два редактора одновременно

**Цель**: FR-006 (атомарность), US3.

1. Подготовить: у тестового автора есть ≥2 свободных песни.
2. Открыть **два браузера** (или `incognito` + обычный).
3. Залогиниться в одном под `test1@test.test`, в другом под `test2@test.test`.
4. В обоих браузерах открыть `/zakroma?author=Аквариум`.
5. **Одновременно** (в одну секунду) кликнуть «Взять в работу» на **одной и той же** песне.
   - Подсказка: сначала оба закрывают глаза, считают до 3, открывают и сразу кликают.
6. **Ожидается**:
   - Один из браузеров получает 200 OK (или 200 с toast «Задание взято»).
   - Второй получает 409 `song_already_taken` + UI показывает «Эта песня уже занята другим редактором».
   - В `tbl_song_assignments` — **ровно 1 запись** по этой `song_id` (не две).
   - Проверка:
     ```bash
     docker exec -i karaoke-db psql -U postgres -d karaoke \
       -c "SELECT COUNT(*) FROM tbl_song_assignments WHERE song_id = <song_id>;"
     ```
   - Возвращает `1`.

## Scenario 4: Редактор без флага — кнопки нет

**Цель**: US2 Acceptance Scenario 2, SC-006.

1. Создать третьего тестового редактора `test3@test.test` с `is_editor=true`, но `can_self_assign_tasks=false` (или НЕ ставить галочку).
2. Залогиниться в `karaoke-public` под `test3@test.test`.
3. Открыть `/zakroma?author=Аквариум`.
4. **Ожидается**: ни на одной песне НЕТ кнопки «Взять в работу».

## Scenario 5: Анонимный посетитель — кнопки нет

**Цель**: US2 Acceptance Scenario 3.

1. Выйти из аккаунта (logout).
2. Открыть `/zakroma?author=Аквариум`.
3. **Ожидается**: ни на одной песне НЕТ кнопки «Взять в работу».
4. **Дополнительно**: открыть DevTools → Network → `zakroma/stream` → response.
5. **Ожидается**: в `song.assignment` поле отсутствует (или `null`) — НЕ должно быть JOIN к `tbl_song_assignments`.

## Scenario 6: Флаг в админке — синхронизация

**Цель**: SC-005, US1.

1. Залогиниться в `webvue3` под `admin@admin.local`.
2. Открыть `test1@test.test`, снять галочку «Может сам назначать себе задания», сохранить.
3. Проверить БД:
   ```bash
   docker exec -i karaoke-db psql -U postgres -d karaoke \
     -c "SELECT can_self_assign_tasks FROM tbl_site_users WHERE email='test1@test.test';"
   ```
4. **Ожидается**: `false`.
5. Перезагрузить в `karaoke-public` страницу `/zakroma?author=Аквариум` (залогиненным как `test1@test.test`).
6. **Ожидается**: через ≤2 сек кнопки «Взять в работу» исчезли со всех карточек (где были).
7. **Дополнительно**: вернуть флаг, перезагрузить, убедиться что кнопки вернулись.

## Scenario 7: Алмин видит self-assign задание

**Цель**: US4.

1. После того как `test1@test.test` взял 1+ песни через Scenario 1.
2. Залогиниться в `webvue3` под `admin@admin.local`.
3. Открыть `SongsTable`, отфильтровать по автору (или без фильтра).
4. **Ожидается**: в колонке «Статус задания» взятая песня показывает «Назначено» / «В работе», в assignee — `test1@test.test`.

## Scenario 8: Refuse — снова свободная

**Цель**: Edge case «recall».

1. Залогиниться в `karaoke-public` под `test1@test.test`.
2. Перейти в `/editor/tasks` (Мои задания).
3. На ранее взятой песне нажать «Отказаться» (refuse).
4. **Ожидается**: задание удалено, задача пропала из «Моих заданий».
5. Перейти на `/zakroma?author=Аквариум`.
6. **Ожидается**: эта песня снова с кнопкой «Взять в работу» (больше нет ни одного задания в `tbl_song_assignments` по ней).

## Scenario 9: Submit → Approve — функциональная идентичность

**Цель**: SC-004.

1. После Scenario 1 (test1 взял песню).
2. В `karaoke-public` открыть задание (через «Открыть задание»).
3. Подправить текст/маркеры, нажать «Отправить на проверку» (submit).
4. Залогиниться в `webvue3` под админом, открыть `SongsTable`, найти песню, нажать «Review».
5. **Ожидается**: видны правленные маркеры из черновика публичного редактора.
6. Нажать «Одобрить» (approve).
7. **Ожидается**: разметка применена к `tbl_songs` (как при админском assign).

## Scenario 10: Прямой вызов API без флага

**Цель**: Edge case «прямой вызов API».

1. Не залогиненным (или залогиненным НЕ-редактором) curl-ом:
   ```bash
   curl -X POST 'http://localhost:8897/api/public/songeditor/assign-self' \
     -H 'Content-Type: application/x-ndjson' \
     -d '{"songId": 12345}'
   ```
2. **Ожидается**:
   - Если не залогинен → 401 (Spring `SiteAuthInterceptor`)/ `not_found` (конвенция).
   - Если залогинен НЕ-редактор → 403 `forbidden_not_editor`.
   - Если залогинен редактор без флага → 403 `forbidden_not_self_assign_editor`.

## Cleanup

После прохождения сценариев:
1. Снять флаги у тестовых редакторов (`webvue3` → `SiteUserEdit`).
2. Удалить тестовые назначения:
   ```bash
   docker exec -i karaoke-db psql -U postgres -d karaoke \
     -c "DELETE FROM tbl_song_assignment_drafts WHERE assignee_id IN (SELECT id FROM tbl_site_users WHERE email LIKE 'test%@test.test');"
   docker exec -i karaoke-db psql -U postgres -d karaoke \
     -c "DELETE FROM tbl_song_assignments WHERE assignee_id IN (SELECT id FROM tbl_site_users WHERE email LIKE 'test%@test.test');"
   ```
3. Опционально: удалить `can_self_assign_tasks` колонку (если фича откатывается):
   ```sql
   ALTER TABLE tbl_site_users DROP COLUMN can_self_assign_tasks;
   -- + пересоздать recordhash-триггер без неё
   ```

## Что НЕ покрывает quickstart

- Регрессия админского `assign` (должна работать как раньше — это вне scope, но не должна сломаться).
- Sync LOCAL↔SERVER (проверяется через `git status` в `Karaoke.properties` и `docker exec karaoke-app ...` после пары self-assign).
- Производительность стрима с включённым JOIN (на 18k+ записей — отдельный бенчмарк, не в скоупе ручных тестов).

## Debugging

- Логи `karaoke-app` / `karaoke-web` содержат `[self-assign]` и `[editor-tasks/...]` — `grep` в `do.sh logs` / `docker logs`.
- SQL `SELECT * FROM tbl_song_assignments WHERE song_id = ?` — для проверки состояния.
- DevTools → Network → `zakroma/stream` → посмотреть payload NDJSON в EventStream.
- POST `/api/public/songeditor/assign-self` через curl с заданными cookies — для проверки endpoint изолированно.
