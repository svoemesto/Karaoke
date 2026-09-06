# How to: добавить новое ORM-поле в Karaoke

> **Lesson source**: урок из спеки 307 зафиксирован в per-feature документе
> [docs/features/zakroma-tiles-sort-order.md](../../../docs/features/zakroma-tiles-sort-order.md)
> и в LiveDoc [features/307-special-authors-zakroma-order.md](../features/307-special-authors-zakroma-order.md).
>
> Добавление `@KaraokeDbTableField` в Kotlin-модели **недостаточно** — есть
> ещё 4 пути записи, которые надо проверить (см. секцию «4 пути записи
> ORM-поля» ниже).

## Prerequisites

- Спека (specs/NNN-*/spec.md) одобрена.
- Миграция БД (`deploy/karaoke-db/NN_*.sql`) спроектирована и готова к применению.
- Понимание, через какие эндпоинты пишется изменяемая таблица (sync? webvue3?

## Steps

### 1. Миграция БД (`deploy/karaoke-db/NN_*.sql`)

- `ALTER TABLE public.tbl_<name> ADD COLUMN IF NOT EXISTS ... NOT NULL DEFAULT ...`
- **Пересоздай** функцию `update_tbl_<name>_recordhash()` с новым полем в md5.
- **Backfill** recordhash для существующих строк (`UPDATE tbl_<name> SET recordhash = md5(...)`).
- Проверь идемпотентность: `ADD COLUMN IF NOT EXISTS` + `CREATE OR REPLACE FUNCTION` — повторный запуск не должен падать.

### 2. Kotlin ORM (`karaoke-app/.../model/<Entity>.kt`)

- Добавь поле с аннотацией:
  ```kotlin
  /**
   * KDoc с описанием.
   * @see specs/NNN-*/spec.md
   */
  @KaraokeDbTableField(name = "snake_case_in_db")
  var camelCaseInKotlin: Type = defaultValue
  ```
- **Reflection-write в `KaraokeDbTable.toSqlToInsert()` подхватит поле автоматически** — это покрывает `entity.save()`.

### 3. DTO (`karaoke-app/.../model/<Entity>DTO.kt`)

- Добавь поле в data class с `@JsonProperty(...)` если имя нестандартное (как `isSpecialOrder`).
- Пробрось в `toDTO()` и `fromDto()`.

### 4. SELECT-запросы (если поле нужно в payload API)

- Найди все `*loadList*`, `*loadById*`, `*load*` функции в `Entity.kt`.
- Добавь поле в `SELECT <existing>, new_field FROM ...`.
- Обнови маппинг `rs.getXxx("snake_case")` → DTO.

### 5. UPDATE через REST-эндпоинты с `@RequestParam` (самый частый blind spot!)

- **Поиск всех эндпоинтов**, через которые пишется эта сущность:
  ```bash
  grep -rn "RequestParam" karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/
  grep -rn "RequestParam" karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/
  ```
- Для каждого найденного эндпоинта:
  - Добавь `@RequestParam(required = false) newField: Type?` в сигнатуру.
  - В теле метода: `newField?.let { v -> entity.newField = v }`.
  - `required = false` — чтобы старые клиенты не сломались (обратная совместимость).
- **Этот шаг — самый частый источник бага «поле сохраняется только при определённом сценарии» или «поле не сохраняется вообще»**.

### 6. Sync (recordhash)

- Уже сделано в шаге 1 (миграция пересоздаёт триггер).
- Sanity-check: после деплоя измени значение поля в LOCAL и в SERVER через UI →
  sync должен увидеть расхождение и предложить push.

### 7. Frontend (admin)

- Если поле редактируется через админку (`webvue3/.../<Entity>/<Entity>Table.vue`):
  - Добавь колонку в массив `fields` / конфиг колонок.
  - Добавь `<template #cell(newField)="data">` для отображения.
  - Реализуй inline-редактирование с `onNewFieldChange(item, event)` методом.
  - Метод шлёт `setAuthorValuePromise` (или аналог) с `{fldName: 'newField', fldValue: value, id: item.id}`.

### 8. Frontend (public)

- Если поле влияет на публичный UI (например, сортировка тайлов):
  - Обнови `*store/modules/<entity>.js` если нужно обработать payload.
  - Или просто положись на то, что бэкенд уже отсортировал данные правильно (часто достаточно).

## Verification

- [ ] Backend compile: `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` — `BUILD SUCCESSFUL`.
- [ ] Backend ktlint: `./gradlew :karaoke-web:ktlintCheck :karaoke-app:ktlintCheck` — `BUILD SUCCESSFUL`.
- [ ] Frontend lint: `cd webvue3 && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}"` — 0 errors.
- [ ] Backend bootJar: `./gradlew :karaoke-web:bootJar :karaoke-app:bootJar` — `BUILD SUCCESSFUL`.
- [ ] Frontend Vite build: `cd webvue3 && npm run build && npm run format:check` — `built in ...`.
- [ ] Миграция применена на LOCAL: `docker exec karaoke-db psql -U postgres -d karaoke -c "\d tbl_<name>"` — колонка присутствует.
- [ ] Идемпотентность миграции: повторный запуск SQL — нет ошибки (только NOTICE).
- [ ] End-to-end: изменить значение через UI → сохранить → `SELECT * FROM tbl_<name> WHERE id=?` → значение записано.
- [ ] **Особенно**: end-to-end через **разные точки входа** (admin inline-edit, admin modal-edit, public API write если есть) — каждая из них проходит через свой эндпоинт с whitelist'ом.

## Rollback

- Код: `git revert <commit-sha>`.
- Миграция: `ALTER TABLE tbl_<name> DROP COLUMN IF EXISTS new_field` (не идемпотентно, но безопасно) + восстановление recordhash-триггера без поля.
- Sync: если recordhash-md5 содержит поле, которое drop'нули в БД — sync сломается. Нужно **сначала** откатить код (новые UPDATE не пишут поле), **потом** drop'нуть колонку и восстановить триггер.

## Когда добавлять ORM-поле НЕ стоит

- Если можно решить задачу без новой колонки (например, через вычисляемое поле или существующий флаг).
- Если миграция не идемпотентна (Constitution Principle III).
- Если добавление ломает backward compatibility публичного API без явной версии API.

## Связанные документы

- [livedocs/features/307-special-authors-zakroma-order.md](../features/307-special-authors-zakroma-order.md) — LiveDoc сводка по фиче 307.
- [docs/features/zakroma-tiles-sort-order.md](../../../docs/features/zakroma-tiles-sort-order.md) — per-feature документ с уроком и 4+1 путями записи.
- [livedocs/runbooks/how-to-migrate-db.md](how-to-migrate-db.md) — детали по миграциям БД.
- [livedocs/runbooks/how-to-add-new-feature.md](how-to-add-new-feature.md) — общий процесс добавления фичи.
- Constitution Principle II/III — sync через recordhash, NON-NEGOTIABLE.
