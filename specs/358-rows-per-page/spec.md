# Feature Specification: Настраиваемое количество строк на странице таблиц в админке

**Feature Branch**: `359-rows-per-page`
**Created**: 2026-09-10
**Status**: Draft
**Input**: User description: "Работа над задачей #74 в трекере OpenProject"

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

### Идентификация

- **Issue ID**: `#74`
- **Title**: Количество строк на страницу таблицы
- **Created in OpenProject**: 2026-09-10
- **Status (на момент старта)**: claimed → In progress

### Workflow (NON-NEGOTIABLE)

| Шаг | Команда | Когда | Кто |
|---|---|---|---|
| 1. **Claim** | `bash tools/tracker.sh claim-issue 74` | ПЕРЕД первой строкой кода спеки | Agent ✅ (выполнено) |
| 2. **Pre-flight Knowledge** | `spec.md § Knowledge References` | Перед написанием спеки | Agent ✅ (выполнено) |
| 3. **Work** | код, tests, knowledge updates | `/speckit.implement` | Agent |
| 4. **Add comment с отчётом** | `bash tools/tracker.sh add-comment 74 --file specs/358-rows-per-page/report.md` | После merge | Agent |
| 5. **Mark review** | `bash tools/tracker.sh mark-review 74` | После публикации комментария | Agent |
| 6. **Close** | `bash tools/tracker.sh close-issue 74` | После ревью владельцем | Agent/Owner |

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-10
- **Grep-запросы** (минимум 3, по релевантным ключевым словам задачи):
  1. `KaraokeProperties` → `knowledge/domains/processing/components/karaoke-properties.md`, `knowledge/adr/local-0001-karaoke-properties-defaults.md`, `knowledge/system/frontend/store-properties.md` и др. (16 файлов)
  2. `perPage` → `webvue3/src/components/**/Table.vue` (12 таблиц), `webvue3/src/views/StatsView.vue`, `knowledge/adr/local-0004-lazy-eager-load-webvue3-pagination.md`
  3. `rowsPerPage` / `b-pagination` / `pagination` → 0 файлов в `knowledge/`, но паттерн в `local-0004-…`

### Knowledge files consulted

- [`knowledge/adr/local-0004-lazy-eager-load-webvue3-pagination.md`](../../knowledge/adr/local-0004-lazy-eager-load-webvue3-pagination.md) — **ПРИНИМАЕМОЕ РЕШЕНИЕ**: серверная пагинация, watch на `currentPage/perPage → loadData()`, нет client-side pagination, нет SSE full reload. В шаблоне явно есть поле `perPage: 25`. Эта фича — расширение ADR-0004: `perPage` должен быть изменяемым из UI, но конвенция остаётся.
- [`knowledge/adr/local-0001-karaoke-properties-defaults.md`](../../knowledge/adr/local-0001-karaoke-properties-defaults.md) — **ПРИНИМАЕМОЕ РЕШЕНИЕ**: дефолты через `System.getenv(...) ?: DEFAULT`. Конвенция используется для согласованности с render-параметрами (см. Clarifications Q1).
- [`knowledge/domains/processing/components/karaoke-properties.md`](../../knowledge/domains/processing/components/karaoke-properties.md) — описывает backend `KaraokeProperties` (Spring `@ConfigurationProperties`, ~150 параметров рендера + UI-настройки). Используется для хранения per-table perPage (см. Clarifications Q1).
- [`knowledge/system/frontend/store-properties.md`](../../knowledge/system/frontend/store-properties.md) — Vuex store для Properties: API `/api/properties/getproperty`, `/api/properties/setproperty`, `/api/properties/digest`. Это backend API для хранения настроек в `Karaoke.properties`. **Использовать этот же backend API** для хранения per-table perPage.
- [`knowledge/adr/0004-karaoke-app-admin-only.md`](../../knowledge/adr/0004-karaoke-app-admin-only.md) — admin-фичи живут в `karaoke-web` (backend) + `webvue3` (frontend). Эта фича относится ТОЛЬКО к admin, не к публичному сайту.

### Searched but not found

- `knowledge/domains/*/components/*.md` — нет документации про webvue3 pagination settings UI; паттерн извлечён из ADR-0004.
- `docs/features/*` — нет per-feature документа про pagination settings.
- `docs/strategy/*` — не относится к фиче.

## Clarifications

### Session 2026-09-10

- Q: Где хранить настройки «строк на странице»? → A: **Custom** — хранить в `KaraokeProperties` (тот же backend, что используется для других параметров компонента «Настройки»), для каждой таблицы свой параметр в `Karaoke.properties`. **Следствие**: настройки **глобальные per-table** (не per-user) — все админы видят одно и то же значение. Это согласуется с тем, как `KaraokeProperties` сам по себе устроен (файл `/sm-karaoke/system/Karaoke.properties`, один на весь кластер).
- Q: Каким должен быть UI-инпут? → A: **Free input 1..1000** (Recommended) — пользователь принял рекомендацию.
- Q: Оптимистичное обновление? → A: **B: Только после подтверждения backend** (пользователь отверг рекомендацию A). UI не обновляется до ответа `/api/properties/setproperty`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Songs: регулировка количества строк (Priority: P1)

**As an** администратор караоке-сайта,
**I want to** видеть в верхней панели пагинации таблицы «Песни» поле «Строк на странице» и менять это значение без перезагрузки,
**so that** я могу быстро просматривать большой каталог (10 000+ песен) либо, наоборот, видеть меньше строк и не скроллить.

**Why this priority**: Песни — самая большая таблица (10 000+ записей), дефолт 50 строк → 200 страниц. Возможность увеличить до 100/200/500 — критично для оператора.

**Independent Test**: Зайти в админку → Songs → изменить значение «Строк на странице» с 50 на 100 → страница должна сразу показать 100 строк (или первую страницу из 100) без перезагрузки.

**Acceptance Scenarios**:

1. **Given** открыта таблица «Песни» с дефолтом 50, **When** я меняю «Строк на странице» на 100 и нажимаю Enter (или кликаю вне поля), **Then** таблица отображает 100 строк, нумерация страниц пересчитывается, total страниц уменьшается в 2 раза.
2. **Given** я установил 100 строк для «Песни», **When** я перезагружу страницу / закрою-открою вкладку, **Then** таблица снова откроется с 100 строк (настройка сохранилась per-user).
3. **Given** у меня стоит 100 строк, **When** я меняю фильтр (поиск по названию), **Then** запрос уходит с perPage=100, страница сбрасывается на 1.
4. **Given** у меня стоит 100 строк, **When** я переключаюсь на таблицу «Авторы» (другая таблица), **Then** в «Авторах» — её собственное значение (например, 30), а не 100.

---

### User Story 2 — Остальные таблицы (Priority: P1, выполняется параллельно с US1)

**As an** администратор,
**I want to** то же самое поле для всех табличных компонентов админки (Authors, Albums, Pictures, SiteUsers, Subscriptions, ShareLinks, Dictionaries, Properties, SitePlaylists, ListeningHistory, Processes, News),
**so that** я могу настроить рабочее пространство под себя.

**Why this priority**: Задача #74 прямо перечисляет эти таблицы — это P1 по тексту issue.

**Independent Test**: На каждой таблице должно появиться то же поле. Перезагрузка вкладки сохраняет настройку.

**Acceptance Scenarios**:

1. **Given** таблица «Авторы» с дефолтом 30, **When** я меняю на 50, **Then** таблица отображает 50 строк.
2. **Given** я установил perPage=500 для «ListeningHistory», **When** я перезагружу страницу, **Then** сохраняется 500.
3. **Given** у меня установлены разные значения для разных таблиц, **When** я возвращаюсь к каждой, **Then** каждая использует СВОЁ значение.

---

### User Story 3 — Persistence per-table (Priority: P1)

**As an** администратор,
**I want to** мои настройки perPage сохранялись per-table глобально (для всех пользователей),
**so that** я не настраиваю каждую таблицу каждый раз и настройки видны всем админам.

**Why this priority**: без persistence фича бесполезна.

**Independent Test**: Задать разные значения в 3 таблицах → перезагрузить вкладку → значения сохранились. Другой админ логинится → видит те же значения.

**Acceptance Scenarios**:

1. **Given** admin1 задал Songs=100, Authors=50, **When** admin2 логинится и открывает те же таблицы, **Then** admin2 видит Songs=100, Authors=50 (глобальные настройки, см. Clarifications Q1).
2. **Given** admin1 задал Songs=200, **When** admin1 закрывает вкладку и возвращается через час, **Then** Songs=200 восстановлено.
3. **Given** ни один пользователь не настраивал таблицу, **When** таблица открывается, **Then** используется hardcoded дефолт (25/30/50/500).

---

### Edge Cases

- **Невалидное значение (например, 0, отрицательное, > 1000)**: серверная валидация → 400 + UI показывает ошибку, поле возвращается к последнему валидному значению.
- **Конкурентное обновление** (два admin'а меняют одну настройку): last-writer-wins; UI не блокирует.
- **Backend недоступен (POST /api/properties/setproperty не отвечает)**: UI показывает toast/alert с описанием ошибки, текущее значение в инпуте НЕ применяется к таблице (остаётся предыдущее).
- **Настройка не найдена в `Karaoke.properties`** (например, после deploy на чистую БД): используется hardcoded дефолт (25/30/50/500).
- **Что считать «строкой на странице»**: 0 — нет, 1 — допустимо (но бессмысленно), N>10000 — отклоняем серверной валидацией.
- **Pagination в Stats**: некоторые таблицы используют client-side aggregation (см. ADR-0004, server-side pagination). Если таблица рендерит агрегированный stats (например, TopUsersTable) — настройка perPage всё равно применима, т.к. это лишь размер страницы UI.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Система MUST предоставить UI-поле «Строк на странице» в верхнем блоке пагинации (слева) для следующих таблиц webvue3: `Songs`, `Authors`, `Albums`, `Pictures`, `SiteUsers`, `Subscriptions`, `ShareLinks`, `Dictionaries`, `Properties`, `SitePlaylists`, `ListeningHistory`, `Processes`, `News`. **ИСКЛЮЧЕНО**: `Stats/TopUsersTable.vue` и `Stats/TopListenedSongsTable.vue` — это chart-cards с собственным `pageSize` через `$emit('page-size')`, другой паттерн (вне scope этой фичи).

- **FR-001a (реализация для Processes и ListeningHistory)**: Для таблиц **`Processes`** и **`ListeningHistory`** параметр передаётся под другим именем:
  - **Processes**: backend `/api/admin/processes` принимает `limit` (= perPage) и `offset` (= (page - 1) * perPage). Преобразование page → offset в `ProcessesTable.onPerPageChange`.
  - **ListeningHistory**: backend `/api/listeninghistory/digest` принимает `pageSize` (= perPage). Передаётся напрямую в action `loadListeningHistoryDigest`.
- **FR-002**: UI-поле MUST позволять ввести целое число (input type=number) из разрешённого набора (минимум: 10, 25, 50, 100, 200, 500 — допустимо больше, если позволяет backend).
- **FR-003**: При изменении значения (Enter / blur) UI MUST отправить `POST /api/properties/setproperty` и **только после успешного ответа** применить новое значение. До ответа UI показывает loading-индикатор на поле. Это согласуется с Clarifications Q3 (без оптимистичного обновления).
- **FR-004**: Настройки MUST храниться **per-table глобально** (НЕ per-user — см. FR-009 и Clarifications Q1) в backend через `/api/properties/setproperty` (файл `Karaoke.properties`) — тот же backend, что используется для других параметров компонента «Настройки». Для каждой таблицы — свой ключ параметра (например, `ui.songs.rows_per_page`, `ui.authors.rows_per_page`, …).
- **FR-005**: При загрузке страницы таблицы MUST использоваться сохранённое значение (если есть); если нет — hardcoded дефолт.
- **FR-006**: Backend MUST валидировать значение: целое число, `>= 1` и `<= 1000`. Невалидное → HTTP 400 с описанием ошибки.
- **FR-007**: REST API MUST использовать существующий `/api/properties/setproperty` для записи и `/api/propertiesdigests` для чтения всех параметров разом (фильтр по `ui.*.rows_per_page` на стороне клиента). Endpoint `/api/properties/getproperty` НЕ использовать — он ходит в `tbl_public_settings`, а не в `Karaoke.properties`. Никаких новых эндпоинтов не требуется.
- **FR-008**: При смене значения MUST сбрасываться `currentPage` на 1 (паттерн из ADR-0004: filter/page change → reset).
- **FR-009**: Настройка MUST быть **глобальная** per-table (НЕ per-user): все админы видят одно и то же значение для таблицы (см. Clarifications Q1 — пользователь выбрал хранение в `KaraokeProperties`, который сам по себе глобальный).
- **FR-010**: Дефолтные значения MUST быть задокументированы в коде (константа в JS-модуле) и совпадать с текущим hardcoded поведением: `Songs=50, Authors=30, Albums=30, Pictures=30, SiteUsers=30, Subscriptions=25, ShareLinks=25, Dictionaries=30, Properties=50, SitePlaylists=30, ListeningHistory=500, Processes=50, News=30` (см. `FR-011`). Stats таблицы исключены (см. FR-001).
- **FR-011**: Существующее поведение пагинации (текущий hardcoded perPage, server-side pagination, SSE partial reload) MUST быть сохранено (см. ADR-0004).

### Non-Functional Requirements

- **NFR-001**: Сохранение настройки MUST укладываться в `≤ 200 мс` p95 (включая сетевой round-trip).
- **NFR-002**: Изменение значения в UI MUST реагировать **после успешного ответа backend** (без оптимистичного обновления, согласно Clarifications Q3). Loading-индикатор на время round-trip.
- **NFR-003**: Никаких регрессий в существующих таблицах: дефолты идентичны текущим.

### Key Entities

- **`KaraokeProperties`** (existing, см. [karaoke-properties.md](../../knowledge/domains/processing/components/karaoke-properties.md)): Spring `@ConfigurationProperties`, привязан к `/sm-karaoke/system/Karaoke.properties`. Хранит ~150 параметров рендера + UI-настройки. Дополняется 14 новыми полями `ui.<table>.rows_per_page` (или per-user ключами).
- **`TableKey` enum / константа**: строковый ключ для каждой таблицы (`'songs'`, `'authors'`, `'albums'`, …). Используется для построения имени параметра.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: В каждой из 14 перечисленных таблиц в верхней панели пагинации слева отображается поле «Строк на странице» с текущим значением (дефолт или сохранённое).
- **SC-002**: Изменение значения в UI приводит к изменению количества строк в таблице **после успешного ответа backend** (нет оптимистичного обновления, см. Clarifications Q3).
- **SC-003**: После перезагрузки страницы / закрытия-открытия вкладки сохранённое значение восстанавливается (persistence работает).
- **SC-004**: Разные пользователи видят **одни и те же** значения (глобально, см. Clarifications Q1 и FR-009).
- **SC-005**: Backend валидирует диапазон `1..1000`; невалидное значение отклоняется с HTTP 400.
- **SC-006**: Существующее поведение пагинации (server-side, SSE partial reload) сохранено — никаких регрессий в функциональности.
- **SC-007**: Изменение значения не ломает SSE subscriptions — `unsubscribeFromTable` в `beforeDestroy` всё ещё работает (см. ADR-0004).

## Assumptions

- **Хранение**: задача #74 говорит «хранились в KaraokeProperties», и владелец явно подтвердил (Clarification Q1): хранить в `Karaoke.properties` через тот же backend `/api/properties`, что используется для других параметров компонента «Настройки». **Это отменяет** изначальную интерпретацию ADR-0001 (разделение render-config vs UI-config) для этого кейса — пользователь принял решение хранить UI-настройки там же. См. Clarifications § Session 2026-09-10.
- **Авторизация**: используется существующий механизм сессий `karaoke-web` (анонимный доступ запрещён к admin-API). Никаких новых auth-механизмов не вводится.
- **UI-компонент**: используется существующий `<b-pagination>` из `bootstrap-vue-next` + новый `<b-form-input type="number">` рядом с ним. Никаких новых UI-библиотек.
- **Дефолты**: hardcoded дефолты совпадают с текущим поведением (никаких регрессий при первом открытии).
- **Per-table, не global**: разные таблицы — разные настройки (Songs=50 ≠ Authors=30). Это явно требуется в issue.
- **Global, не per-user**: настройки одни для всех админов (см. Clarifications Q1 — пользователь выбрал `KaraokeProperties`, который сам по себе глобальный).
- **Никаких новых REST-эндпоинтов**: используется существующее `/api/properties/*` API (`/api/properties/setproperty`, `/api/propertiesdigests`).

## Out of Scope

- Глобальная настройка для всех таблиц одним значением (явно не требуется в issue).
- Сохранение фильтров / сортировки / выбранных колонок (отдельный эпик).
- Массовое применение настройки ко всем таблицам сразу.
- Аналитика / отчёты по использованию perPage.

## NEEDS CLARIFICATION (max 3)

- **Q1**: ✅ RESOLVED 2026-09-10 — Custom: хранить в `KaraokeProperties` через `/api/properties` (см. Clarifications § Session 2026-09-10). Снимает вопрос о новой таблице.

- **Q2**: ✅ RESOLVED 2026-09-10 — A: Free input (1..1000), бэкенд валидирует.

- **Q3**: ✅ RESOLVED 2026-09-10 — **B**: Только после подтверждения backend. UI НЕ обновляется до успешного ответа `/api/properties/setproperty`. Это упрощает реализацию и устраняет необходимость rollback-логики. NFR-002 скорректирован (см. ниже).
