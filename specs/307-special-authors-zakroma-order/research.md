# Research: Порядок плашек в Закромах и явная сортировка спец-авторов

**Date**: 2026-09-06
**Branch**: `307-special-authors-zakroma-order`
**Spec**: [spec.md](./spec.md)

## Цель

Зафиксировать принятые решения (Decision), обоснование (Rationale) и рассмотренные альтернативы (Alternatives considered) для ключевых контрактных точек фичи. Все вопросы были решены в секции Clarifications спеки; здесь — структурированное резюме для исполнителя.

---

## R1. Контракт синхронизации схемы LOCAL↔PROD

### Decision

Миграция `46_author_sort_order.sql` применяется **вручную на LOCAL и на PROD в одном PR**. PR-чеклист явно фиксирует пункт «миграция применена на обеих сторонах». Если рассинхрон схемы случился — естественный canary: ошибка `column "sort_order" does not exist` при первом sync `tbl_authors` (push или pull). Восстановление — докатить миграцию на отстающую сторону (`ADD COLUMN IF NOT EXISTS` идемпотентен).

### Rationale

- Соответствует **текущей практике проекта**: миграция `27_author_special_order.sql` — единый файл, руками применяется на обе стороны. См. существующий `deploy/karaoke-db/`.
- Sync-механизм (Constitution III) оперирует **записями, не DDL**: колонки так не разъезжаются. Поэтому рассинхрон схемы не может «произойти сам» — он может только случиться по забывчивости.
- Автоматизация миграций (Flyway/Liquibase-style) — **вне скоупа** задачи №56 (задача про порядок, не про инфраструктуру БД).
- 60-секундное окно рассинхрона не страшно: миграция DDL + бэкенд идут в одном PR, обе стороны мигрируются **до деплоя** нового бэкенда.
- Естественный canary (SQL-ошибка при sync) надёжнее, чем специальная проверка в PR-чеклисте.

### Alternatives considered

- **Автоматическое применение миграций при старте `karaoke-web`** (Spring Flyway-style или кастомный runner). Отклонено: требует новой инфраструктуры, не в скоупе задачи №56. Прецедент для одной фичи — overkill.
- **Только на PROD, LOCAL подтягивает через sync** — отклонено: sync не работает с DDL.
- **Ничего не фиксируем в PR-чеклисте** — отклонено: риск «забыли на одной стороне» не снижается.

### Связанные артефакты

- Спека: FR-013, Edge Case «рассинхрон схемы», Clarification Q4.
- Миграция: `deploy/karaoke-db/46_author_sort_order.sql` (создаётся в Phase 2).

---

## R2. Поведение кеша `authorsTilesCache` при изменении `sort_order`

### Decision

TTL кеша ≤60 секунд достаточно; **новых механизмов инвалидации НЕ вводим**. Полагаемся только на TTL.

### Rationale

- **Текущая практика** для других полей `tbl_authors` (`is_special_order`, `aliases`, `warning`) — те же 60 секунд TTL. Введение отдельного hook'а инвалидации для одного нового поля создало бы прецедент.
- Редактор правит `sort_order` **редко** (1-2 раза в день). 60-секундное окно несогласованности приемлемо: посетители видят изменения в течение минуты.
- `StatBySong.consumeDirty()` (существующий механизм dirty-инвалидации) связан с **изменениями счётчиков песен** (`tbl_songs`), а не напрямую с `tbl_authors`. Изменение `sort_order` **не триггерит** `consumeDirty()` автоматически — это нормально, мы рассчитываем на TTL.
- **Sync LOCAL→SERVER** при изменении `sort_order` тоже не триггерит `consumeDirty()`.
- Явная инвалидация потребовала бы нового hook'а в `StatBySong` или отдельного endpoint'а — **расширение скоупа**, не оправданное одной фичей.

### Alternatives considered

- **Новый hook в `StatBySong`** (отдельный dirty-флаг для `tbl_authors`). Отклонено: расширение скоупа, новый код, который надо поддерживать; не оправдано одной фичей.
- **Явный HTTP-вызов `POST /api/public/authors-tiles/invalidate`** из админки при изменении `sort_order`. Отклонено: требует нового endpoint'а, не спасёт от push'а из sync LOCAL→SERVER.
- **Включение `sort_order` в cache key** (хэш от всех `sort_order`-значений). Отклонено: неэффективно — cache hit rate упадёт до 0, фактически отключает кеш.

### Связанные артефакты

- Спека: FR-010 (без изменений), SC-003 (явная ссылка на это решение), Edge Case «кеш старый», Clarification Q5.
- Код: `karaoke-web/src/main/kotlin/.../PublicApiController.kt::getCachedAuthorsTiles` — НЕ трогаем.

---

## R3. Визуальный/UX-эффект для спец-плашки в новой позиции «первый тайл»

### Decision

**Никаких новых визуальных эффектов не вводим**. Текущего отличия через иконку 📁 (вместо `<img>` автора) достаточно для категоризации. Состояние `at-selected` для обычных тайлов работает как раньше — спец-плашка в нём не участвует.

### Rationale

- Плашка **уже** визуально отличается от обычных тайлов: вместо `<img>` автора — `<span class="km-special-tile-icon">📁</span>`. Этого достаточно для семантического разделения «это не обычный автор».
- Скоуп задачи №56 — **порядок**, не дизайн. Любые дополнительные рамки/анимации — это изменение дизайна, требующее отдельного UX-ревью.
- Состояние `at-selected` в `AuthorTiles.vue` устанавливается через `selected === t.author` — селектор по **имени** автора. Спец-плашка в этот селектор не входит (она рендерится через слот/отдельный `<button>` в `ZakromaView.vue`), поэтому не конфликтует.
- Спец-плашка имеет свой класс `at-selected` (через `:class="{ 'at-selected': isSpecialBucketSelected }"`), но он активируется только при переходе в табличный режим `/zakroma/special-bucket` (см. существующий CSS `.km-special-tile.at-selected`).

### Alternatives considered

- **Добавить рамку `var(--km-accent)` и badge «📁 Категория»**. Отклонено: расширение скоупа, требует UX-ревью.
- **Smooth-scroll/focus на спец-плашку при первом открытии `/zakroma`**. Отклонено: не нужно для production-rollout.
- **Убрать визуальное отличие полностью** (только текст «Отдельные песни разных авторов»). Отклонено: регрессия UX (теряем иконку 📁, которая уже работает).

### Связанные артефакты

- Спека: FR-005 (без изменений), Edge Case «at-selected», Clarification Q6.
- Код: `karaoke-public/src/views/ZakromaView.vue` — перенос рендеринга плашки; CSS-классы `.km-special-tile*` — НЕ трогаем.

---

## R4. Структура и расположение файлов

### Decision

Тронем **6 файлов** (точно по спеку). Никаких новых директорий или модулей:

1. `deploy/karaoke-db/46_author_sort_order.sql` — **новый** (миграция).
2. `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt` — +1 поле, +ORDER BY.
3. `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/AuthorTilePublicDto.kt` — +1 поле.
4. `karaoke-public/src/views/ZakromaView.vue` — перенос плашки.
5. `karaoke-public/src/components/AuthorTiles.vue` — **опционально**: очистка слота `#trailing`.
6. `webvue3/src/components/Authors/AuthorsTable.vue` — +1 колонка.

Плюс:

7. `docs/features/zakroma-tiles-sort-order.md` — **новый** (per-feature документ).
8. `.github/PULL_REQUEST_TEMPLATE.md` (или эквивалент) — пункт чек-листа о миграции (FR-013).

### Rationale

- Локализация изменений: каждое изменение — в том файле, который логически отвечает за эту контрактную точку.
- Не вводим новых модулей — проект уже имеет чёткую структуру (karaoke-app / karaoke-web / webvue3 / karaoke-public), и фича аккуратно ложится в существующие.
- Миграция БД — в стандартный каталог `deploy/karaoke-db/`, следующий свободный номер 46 (предыдущая — 45).

### Alternatives considered

- **Создать новый модуль** для сортировки тайлов. Отклонено: overkill, разрастание кодовой базы для одной фичи.
- **Вынести логику сортировки в отдельный helper** (`AuthorSortUtil.kt`). Отклонено: логика — один `ORDER BY`, дублирование не требуется.

---

## R5. ORM-механика: аннотация `@KaraokeDbTableField`

### Decision

Поле `sortOrder: Int = 0` в `Author.kt` с аннотацией `@KaraokeDbTableField(name = "sort_order")`. Чтение и запись через стандартный `KaraokeDbTable.loadList` (reflection-based).

### Rationale

- Существующая ORM-инфраструктура (`KaraokeDbTable.kt`) автоматически читает и пишет поля с аннотацией `@KaraokeDbTableField` через reflection — никаких изменений в самой инфраструктуре не требуется.
- Соответствует **паттерну существующего поля `isSpecialOrder`** в том же `Author.kt` (FR-004 явно ссылается).
- `Int` в Kotlin ↔ `INTEGER` в Postgres — нативный маппинг, никаких конвертеров.

### Alternatives considered

- **Ручной SQL + ручной read/write** (без аннотации). Отклонено: расходится с паттерном существующих полей; усложняет поддержку.
- **Использовать JPA/Hibernate**. Отклонено: **NON-NEGOTIABLE** по Constitution II — никакого JPA в проекте.

---

## R6. API-контракт `/api/public/authors-tiles`

### Decision

- Поле `sortOrder: Int = 0` добавляется в `AuthorTilePublicDto`.
- Имя JSON: `sortOrder` (camelCase), как у `isSpecialOrder`.
- Никаких изменений URL, query-параметров, статусов ответа. **Backward compatible**.

### Rationale

- Поле не критично для публичного UI (он уже получает `authorTiles` в правильном порядке от бэкенда), но полезно для дебага в DevTools без обращения к БД.
- Дефолт `= 0` в DTO означает, что старые клиенты без этого поля получают безопасный дефолт — **нет breaking change**.
- Соответствует паттерну `isSpecialOrder` (с аннотацией `@get:JsonProperty("isSpecialOrder")` — здесь для `sortOrder` это не нужно, потому что Jackson по умолчанию сохраняет camelCase).

### Alternatives considered

- **Не отдавать `sortOrder` в публичном API** (только в админском). Отклонено: добавлять поле «только для дебага» — лишняя сущность; лучше отдавать всегда для консистентности.
- **Отдавать `sortOrder` под другим именем** (например, `order` или `displayOrder`). Отклонено: расходится с именем в БД (`sort_order`) и Kotlin (`sortOrder`).

---

## R7. PR-чеклист

### Decision

Добавить **один явный пункт** в `.github/PULL_REQUEST_TEMPLATE.md`:

```markdown
- [ ] Миграция `46_author_sort_order.sql` применена на LOCAL (`psql -f deploy/karaoke-db/46_author_sort_order.sql`)
      и на PROD (через стандартный deploy-процесс).
      Проверка: `\\d tbl_authors` показывает колонку `sort_order INTEGER NOT NULL DEFAULT 0` на обеих сторонах.
```

### Rationale

- Соответствует практике проекта (см. существующие пункты чек-листа в PR-шаблоне).
- Даёт конкретный verification step (можно прогнать `\\d tbl_authors` руками).
- Не заменяет существующих проверок (FR-013 — дополнение).

### Alternatives considered

- **Не включать в PR-чеклист** — отклонено: риск «забыли на одной стороне» не снижается.
- **Сделать автоматическую проверку** (CI скрипт проверяет наличие колонки на обеих БД через SSH). Отклонено: расширение скоупа, требует доступа CI к БД (которого сейчас нет).

---

## Сводка решений

| # | Тема | Решение | Связь с FR/SC/Clarification |
|---|---|---|---|
| R1 | Синхронизация схемы LOCAL↔PROD | Ручные миграции в одном PR + пункт в чеклисте | FR-013, Clarification Q4 |
| R2 | Кеш `authorsTilesCache` | TTL ≤60с, без новых hook'ов | FR-010, SC-003, Clarification Q5 |
| R3 | Визуал спец-плашки | Без изменений (иконка 📁 достаточно) | FR-005, Clarification Q6 |
| R4 | Структура файлов | 6 файлов + 1 миграция + 1 per-feature doc | Спека §Source Code |
| R5 | ORM `@KaraokeDbTableField` | По аналогии с `isSpecialOrder` | FR-004 |
| R6 | API `/authors-tiles` | `+sortOrder` в DTO, backward compatible | FR-006, FR-007, SC-006 |
| R7 | PR-чеклист | Один пункт с verification step | FR-013 |

Все NEEDS CLARIFICATION решены. Никаких открытых вопросов для следующей фазы.
