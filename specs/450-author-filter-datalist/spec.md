# Feature Specification: Список авторов в фильтрах

**Feature Branch**: `450-author-filter-datalist`

**Created**: 2026-09-24

**Status**: Draft

**Input**: User description: "183"

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

> Эта секция REQUIRED для каждой спецификации. Без неё спека НЕ ДОЛЖНА
> переходить в `/speckit.plan`. CI gate `tools/check-spec-issue-link.py`
> валидирует наличие всех полей. Workflow `claim → report → mark-review →
> close` — обязателен (см. tools/tracker.sh и AGENTS.md).

### Идентификация

- **Issue ID**: `#183`
- **Title**: `Список авторов в фильтрах`
- **Created in OpenProject**: `2026-09-24`

### Workflow (NON-NEGOTIABLE при наличии Issue ID)

| Шаг | Команда | Когда | Кто |
|---|---|---|---|
| 1. **Claim** | `source .env.local-tracker && bash tools/tracker.sh claim-issue 183` | ПЕРЕД первой строкой кода спеки. Переводит `New` → `In progress`, assignee=ai-agent. | Agent |
| 2. **Pre-flight Knowledge** | `spec.md § Knowledge References` | Согласно Constitution Principle IX (см. ниже). | Agent |
| 3. **Work** | код, tests, knowledge updates | `/speckit.implement` | Agent |
| 4. **Add comment с отчётом** | `bash tools/tracker.sh add-comment 183 --file specs/450-author-filter-datalist/report.md` | После merge. Файл `report.md` — REQUIRED. | Agent |
| 5. **Mark review** | `bash tools/tracker.sh mark-review 183` | После публикации комментария. Переводит `In progress` → `In review`. | Agent |
| 6. **Close** | `bash tools/tracker.sh close-issue 183` | После ревью владельцем. (Может пропустить, если владелец закрывает сам.) | Agent или Owner |

### Проверки (validation)

- `tools/check-spec-issue-link.py` (Pass 349) проверяет в CI:
  - Наличие секции `## OpenProject Tracking` в `spec.md`.
  - Наличие полей `Issue ID`, `Title`, `Workflow` (с пунктами claim/report/mark-review).

### Прецедент

2026-09-09, issue #69 (Pass 344/345): work выполнен через `/speckit-full 69` БЕЗ
claim. Отчёт опубликован задним числом после merge PR #448.
Governance failure, исправлен в спеке #349 (Pass 349).

> **Примечание (2026-09-24)**: `tracker.sh claim-issue 183` завершился
> `HTTP 422 — The chosen user is not allowed to be 'Assignee'`. Issue #183
> создана в OpenProject без назначенного типа/исполнителя, и API отклонил
> смену assignee. Ветка `450-author-filter-datalist` создана и активна,
> работа ведётся; claim нужно повторить/уточнить по проекту, к которому
> относится #183 (см. `report.md`).

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

> **Прецедент**: 2026-09-09, spec #339 — агент пропустил Knowledge-first
> pre-flight и изобрёл форму кеша вместо паттернов из
> `knowledge/domains/caching/components/caching-patterns.md`.
> Без заполненной секции спека **НЕ ДОЛЖНА** переходить в
> `/speckit.plan`. См. `AGENTS.md` MUST #0, Constitution Principle IX.

### Pre-flight log

- **Дата pre-flight**: 2026-09-24
- **Grep-запросы** (минимум 3, по релевантным ключевым словам задачи):
  1. `Автор` → файлы: `knowledge/domains/catalog/*`, `knowledge/system/frontend/*`, `knowledge/adr/local-0007..0008`
  2. `author` → файлы: `knowledge/domains/catalog/components/author-entity.md`, `entities-catalog.md`, `store-authors.md`, `store-albums.md`, `filter-stores.md`
  3. `filter|фильтр` → файлы: `knowledge/system/frontend/filter-stores.md`, `knowledge/domains/catalog/domain.md`, `knowledge/system/frontend/store-songs.md`
  4. `webvue3|store-songs|views` → файлы: `knowledge/system/frontend/filter-stores.md`, `store-songs.md`, `store-authors.md`, `store-albums.md`, `webvue3-views.md`

### Knowledge files consulted

- [`knowledge/README.md`](../../knowledge/README.md) — карта Knowledge, протокол SSoT.
- [`knowledge/domains/README.md`](../../knowledge/domains/README.md) — реестр доменов, определён `catalog` как целевой.
- [`knowledge/domains/catalog/domain.md`](../../knowledge/domains/catalog/domain.md) — Ubiquitous Language: Author — музыкальный исполнитель (НЕ автор текста); Album — коллекция песен.
- [`knowledge/domains/catalog/components/author-entity.md`](../../knowledge/domains/catalog/components/author-entity.md) — сущность Author и endpoint `/api/songs/authors` (список имён).
- [`knowledge/domains/catalog/components/album-entity.md`](../../knowledge/domains/catalog/components/album-entity.md) — Album, `filterAuthorName` (точное имя) в фильтре альбомов.
- [`knowledge/system/frontend/filter-stores.md`](../../knowledge/system/frontend/filter-stores.md) — паттерн filter stores, 22 store'а, persistence через server-side key/value; Albums filter (6 полей).
- [`knowledge/system/frontend/store-songs.md`](../../knowledge/system/frontend/store-songs.md) — эталон: `songAuthorsPromise` / `dictAuthors` как источник `<datalist>` в Songs-фильтре.
- [`knowledge/system/frontend/store-authors.md`](../../knowledge/system/frontend/store-authors.md) — store авторов, дайджест.
- [`knowledge/system/frontend/store-albums.md`](../../knowledge/system/frontend/store-albums.md) — store альбомов.
- [`knowledge/adr/local-0004-lazy-eager-load-webvue3-pagination.md`](../../knowledge/adr/local-0004-lazy-eager-load-webvue3-pagination.md) — конвенция lazy-load/persistence фильтров webvue3; фильтры сохраняются через `setWebvueProp`/`getWebvueProp`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Подсказки авторов в фильтре «Авторы» (Priority: P1)

Администратор открывает модальное окно фильтра компонента «Авторы» и
начинает вводить имя автора в поле «Автор:». Поле показывает список
существующих авторов (нативный выпадающий список подсказок), как это
уже работает в фильтре компонента «Песни».

**Why this priority**: Это основная и минимальная ценность — избавить
администратора от ручного ввода имени автора «по памяти» и опечаток,
приводящих к пустому результату фильтрации.

**Independent Test**: Открыть «Авторы» → фильтр, начать вводить любые
2–3 буквы известного автора — появляется выпадающий список; выбор
подставляет точное имя и фильтр возвращает записи.

**Acceptance Scenarios**:

1. **Given** открыт фильтр компонента «Авторы», **When** пользователь
   фокусирует/начинает вводить поле «Автор:», **Then** появляется список
   подсказок с именами авторов.
2. **Given** в списке подсказок выбран автор, **When** пользователь
   применяет фильтр, **Then** таблица показывает записи только этого
   автора (текущее поведение точного совпадения сохраняется).
3. **Given** поле «Автор:» пустое, **When** фильтр применяется,
   **Then** показываются все записи (поведение не меняется).

---

### User Story 2 - Подсказки авторов в фильтре «Альбомы» (Priority: P2)

Администратор открывает модальное окно фильтра компонента «Альбомы» и
начинает вводить автора в поле «Автор:» — появляется такой же список
подсказок с именами авторов, как в фильтре «Песни».

**Why this priority**: Вторая поверхность той же проблемы; закрывает
симметрию с US1 и полностью удовлетворяет формулировку issue («компоненты
Авторы и Альбомы»).

**Independent Test**: Открыть «Альбомы» → фильтр, начать вводить имя
автора — появляется выпадающий список; выбор подставляет точное имя и
фильтр возвращает альбомы этого автора.

**Acceptance Scenarios**:

1. **Given** открыт фильтр компонента «Альбомы», **When** пользователь
   начинает вводить в поле «Автор:», **Then** появляется список подсказок
   с именами авторов.
2. **Given** выбран автор из списка, **When** применяется фильтр,
   **Then** показываются альбомы этого автора.
3. **Given** введён автор, которого нет в списке, **When** применяется
   фильтр, **Then** поведение не меняется (как и сейчас — пустой/
   отфильтрованный результат по точному имени).

---

### User Story 3 - Единообразие подсказок во всех трёх фильтрах (Priority: P3)

Источник и поведение подсказок совпадают в фильтрах «Песни», «Авторы»,
«Альбомы» — один и тот же список авторов и одинаковый способ показа
(нативный datalist).

**Why this priority**: Не отдельная ценность для пользователя, а гарантия
от расхождений (issue явно требует «вести себя так же, как в фильтре
компонента Песни»). Низкий приоритет, т.к. следует автоматически из US1/US2
при единой реализации.

**Independent Test**: Сравнить список подсказок в трёх фильтрах — он
идентичен; поведение при пустом поле/опечатке одинаково.

**Acceptance Scenarios**:

1. **Given** три фильтра открыты по очереди, **When** начинается ввод
   автора, **Then** набор подсказок одинаков во всех трёх.

---

### Edge Cases

- **Пустой список авторов** (свежая БД / все авторы `skip=true`): поле
  работает как обычный текстовый ввод, без подсказок; ошибок нет.
- **Большой список** (сотни авторов, 18k+ песен): подсказки не тормозят
  ввод; нативный datalist фильтрует по подстроке силами браузера.
- **Автор с похожим именем**: datalist показывает все совпадения,
  пользователь выбирает нужное; фильтр по-прежнему применяет точное
  совпадение имени (существующая семантика).
- **Сеть недоступна**: список подсказок остаётся пустым, поле работает
  как обычный ввод; применение фильтра не падает.
- **Сохранённый ранее фильтр** (переживает F5 через server-side key/value):
  подстановка подсказок не должна сбрасывать/портить сохранённое значение.
- **Совпадение с уже введённым текстом**: если текст в поле уже введён,
  datalist показывает подходящие варианты, не перезаписывая значение
  самопроизвольно.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Поле «Автор:» в фильтре компонента «Авторы» MUST показывать
  выпадающий список подсказок с именами авторов при вводе/фокусе.
- **FR-002**: Поле «Автор:» в фильтре компонента «Альбомы» MUST показывать
  такой же выпадающий список подсказок с именами авторов.
- **FR-003**: Список подсказок MUST использовать тот же источник данных,
  что и фильтр компонента «Песни» (список имён авторов из
  `/api/songs/authors`), чтобы набор авторов был единообразным.
- **FR-004**: Выбор подсказки MUST подставлять точное имя автора в поле,
  не изменяя текущую семантику фильтрации (точное совпадение имени).
- **FR-005**: Пустое поле MUST сохранять текущее поведение (фильтр по
  автору не применяется — показываются все записи).
- **FR-006**: Подсказки MUST быть необязательными — если список авторов
  не загрузился/пуст, поле продолжает работать как обычный текстовый ввод.
- **FR-007**: Поведение подсказок MUST быть одинаковым во всех трёх
  фильтрах (Песни, Авторы, Альбомы) — один и тот же механизм показа.
- **FR-008**: Загрузка списка авторов MUST NOT блокировать открытие
  модального окна фильтра и NOT сбрасывать сохранённые значения фильтров.
- **FR-009**: Изменение MUST быть только в админском приложении
  (`webvue3`); публичный `karaoke-public` не затрагивается (Constitution
  Principle V — двух-фронтенд).

### Key Entities *(include if feature involves data)*

- **Author (Исполнитель)**: имя автора-исполнителя из `tbl_authors`
  (и/или `tbl_songs.song_author`); здесь используется только как плоский
  список строк для подсказок. Не является источником изменений.
- **AlbumsFilterAuthorName**: значение поля «Автор:» в фильтре альбомов
  (строка, точное имя); persistence через server-side key/value.
- **AuthorsFilterAuthor**: значение поля «Автор:» в фильтре авторов
  (строка, точное имя); persistence через server-side key/value.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: В 100% открытий фильтров «Авторы» и «Альбомы» при
  непустом справочнике авторов поле «Автор:» предлагает список подсказок.
- **SC-002**: Администратор выбирает автора из подсказок не более чем за
  2 действия (фокус/ввод + клик), вместо ручного набора полного имени.
- **SC-003**: Набор подсказок идентичен во всех трёх фильтрах (0
  расхождений при сравнении).
- **SC-004**: Появление подсказок не увеличивает время открытия модалки
  фильтра более чем на неощутимую для пользователя величину (ввод не
  блокируется, список догружается асинхронно).
- **SC-005**: Существующее поведение фильтрации (точное совпадение,
  пустое поле = все записи, сохранение между сессиями) сохраняется на 100%.

## Assumptions

- Источник списка авторов — существующий endpoint `/api/songs/authors`
  (используется фильтром «Песни»); новых серверных изменений не требуется.
- Подсказки реализуются нативным механизмом подсказок ввода
  (как в фильтре «Песни»), без сторонних UI-библиотек.
- Изменение только в `webvue3` (админка); `karaoke-public` вне scope.
- Список авторов может быть большим (сотни) — приемлемо, т.к. механизм
  уже используется в фильтре «Песни» и работает.
- Точная семантика фильтрации по имени автора (совпадение) не меняется —
  issue просит только показ списка.
