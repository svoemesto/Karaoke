# Feature Specification: Слияние слоя `docs/` в `knowledge/` и запрет параллельной документации

**Feature Branch**: `675-merge-docs-into-knowledge`

**Created**: 2026-10-29

**Status**: Draft

**Input**: User description: "Исполнить wayfinder-карту KARAOKE-323 «Слияние docs/ в knowledge/ и запрет параллельной документации»: перенести слой `docs/` (73 файла) в `knowledge/`, развести архитектурную и продуктовую ветки SSoT, сохранить реестр аудита #336, переписать все ссылки на перемещаемые файлы, принять ADR-0011 и поставить guard, который не даст каталогу `docs/` появиться снова. Источник решения — KARAOKE-320; карта решений — KARAOKE-323 (10 тикетов-решений); execution issue — KARAOKE-334."

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

> Трекер проекта — Plane (`AGENTS.md` v3.7.1). Номера Plane не совпадают с номерами OpenProject; OpenProject остаётся локальным архивом. Секция сохранена в шаблоне спеки, поля заполнены актуальными для Plane.

### Идентификация

- **Issue ID**: `KARAOKE-334`
- **Title**: `Слияние docs/ в knowledge/ по карте KARAOKE-323`
- **Created in OpenProject**: `2026-10-29` (задача создана в Plane; в OpenProject не переносилась)

### Workflow (NON-NEGOTIABLE при наличии Issue ID)

| Шаг | Команда | Когда | Кто |
|---|---|---|---|
| 1. **Claim** | `source .env.local-tracker && bash tools/tracker.sh claim-issue 334` | ПЕРЕД первой строкой кода спеки. Переводит `New` → `In progress`, assignee=ai-agent. | Agent |
| 2. **Pre-flight Knowledge** | `spec.md § Knowledge References` | Согласно Constitution Principle IX. | Agent |
| 3. **Work** | перенос, ссылки, инструменты, правила, guard | `/speckit.implement` | Agent |
| 4. **Add comment с отчётом** | `bash tools/tracker.sh add-comment 334 --file specs/675-merge-docs-into-knowledge/report.md` | После merge. Файл `report.md` — REQUIRED. | Agent |
| 5. **Mark review** | `bash tools/tracker.sh mark-review 334` | После публикации комментария. Переводит `In progress` → `In review`. | Agent |
| 6. **Close** | `bash tools/tracker.sh close-issue 334` | После ревью владельцем. | Agent или Owner |

### Проверки (validation)

- `tools/check-spec-issue-link.py` — наличие секции `## OpenProject Tracking`, полей `Issue ID`, `Title`, `Workflow` и команд `tracker.sh claim-issue` / `add-comment` / `mark-review` в таблице Workflow.
- Идентификаторы в коммитах: `[tracker-claim-334]` / `[tracker-review-334]`.

### Прецедент

2026-09-09, issue #69 (Pass 344/345): отчёт опубликован задним числом после merge — governance failure, исправлен в спеке #349 (Pass 349). Настоящая спека выполняет workflow с claim до первой строки работы.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-10-29
- **Grep-запросы** (по релевантным ключевым словам задачи):
  1. `grep -rn "docs/" knowledge/ --include=*.md` → 142 упоминания в 54 файлах `knowledge/` (концентрация: `adr/0007-adopt-knowledge-as-ssot.md` — 14, `system/infra/governance-docs.md` — 15, `system/infra/ci-tools.md` — 12, `system/infra/tracker.md` — 6)
  2. `grep -rn "playbook\|runbook\|рецепт" knowledge/ --include=*.md` → 19 совпадений; найден существующий каталог `knowledge/guidelines/runbooks/README.md` («индекс операционных playbook'ов», пуст)
  3. `grep -rn "per-feature-docs" --include='*.md' --include='*.py' --include='*.sh' --include='*.yml' .` → `knowledge/domains/README.md:115`, `knowledge/system/infra/pre-commit-config.md:183`, `knowledge/system/infra/ci-tools.md:414`, `specs/379-pkg-s2-16/report.md` — файл объявляет 7 документов при фактических 65
  4. `grep -rnE "\bBR-\d+\b" docs/features/*.md` (пересчёт) → 530 упоминаний, 510 уникальных, 267 не встречаются в `knowledge/`; 32 файла держат 507 кодов
  5. `grep -rn "docs/" --include='*.md' --include='*.yml' --include='*.sh' --include='*.py' --include='*.kt'` (по типам файлов, без `alignment-ml/`, `deploy/`) → 2373 actionable-строки в 8 группах; 30 строк с путями, зашитыми в 9 инструментов
- **Research-артефакты усилия**: `.scratch/karaoke/wayfinding/docs-merge/research/inventory-docs.md` (инвентарь 73 файлов), `references-to-docs.md` (карта ссылок), `skill-managing-living-docs.md` (скиллы и рантайм-копии).

### Knowledge files consulted

- [`knowledge/README.md`](../../knowledge/README.md) — SSoT-карта проекта; здесь же Karaoke-override #5 («`docs/` не трогаем»), который заменяется; § «Структура» и § «Для AI-агентов» — точки правки.
- [`knowledge/domains/README.md`](../../knowledge/domains/README.md) — реестр 16 доменов; продуктовый слой в него **не** вносится (решение KARAOKE-331); строка 115 — ссылка на удаляемый `per-feature-docs.md`.
- [`knowledge/adr/0007-adopt-knowledge-as-ssot.md`](../../knowledge/adr/0007-adopt-knowledge-as-ssot.md) — ADR, заменяемый **в части** (п. 2, п. 4, следствие 5) новым ADR-0011.
- [`knowledge/adr/README.md`](../../knowledge/adr/README.md) — правила нумерации (`0001…` — глобальные решения, `local-` — контейнерные) и append-only; следующий свободный ID — 0011.
- [`knowledge/adr/0008-tracker-openproject-migration.md`](../../knowledge/adr/0008-tracker-openproject-migration.md) и [`knowledge/adr/local-0012-plane-tracker-migration.md`](../../knowledge/adr/local-0012-plane-tracker-migration.md) — прецедент ADR о миграции трекера; [`knowledge/adr/0010-lm-studio-instead-of-ollama.md`](../../knowledge/adr/0010-lm-studio-instead-of-ollama.md) — прецедент **частичной** замены ADR.
- [`knowledge/templates/adr.md`](../../knowledge/templates/adr.md) — формат нового ADR; [`knowledge/templates/component.md`](../../knowledge/templates/component.md) — профиль A; [`knowledge/templates/README.md`](../../knowledge/templates/README.md) — правила шаблонов и lint-policy.
- [`knowledge/guidelines/runbooks/README.md`](../../knowledge/guidelines/runbooks/README.md) — формат runbook'а («How to: …», Контекст / Шаги / Проверка / Rollback); целевой каталог для `docs/ops/`.
- [`knowledge/guidelines/README.md`](../../knowledge/guidelines/README.md) — индекс guidelines; уже запланирован перенос гайда по KDoc/JSDoc из `docs/api/README.md`.
- [`knowledge/system/infra/per-feature-docs.md`](../../knowledge/system/infra/per-feature-docs.md) — устаревший каталог per-feature документов (7 вместо 65) — удаляется.
- [`knowledge/system/infra/governance-docs.md`](../../knowledge/system/infra/governance-docs.md) — каталог governance-документов с 10 несуществующими ссылками — переписывается.
- [`knowledge/system/infra/tracker.md`](../../knowledge/system/infra/tracker.md) — источник деталей по трекеру; `docs/tracker-setup.md` становится runbook'ом.
- [`knowledge/domains/catalog/components/audio-descendant-sync.md`](../../knowledge/domains/catalog/components/audio-descendant-sync.md), [`knowledge/system/frontend/target-request-guard.md`](../../knowledge/system/frontend/target-request-guard.md) — зеркала мост-файлов, которые удаляются.
- [`knowledge/public/glossary.md`](../../knowledge/public/glossary.md) — публичная проекция Универсального языка; место термина «продуктовый слой».

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Один слой проектной документации (Priority: P1)

Работающий над задачей агент (или разработчик) ищет, где описана фича, рецепт операции или принцип проекта, и находит это в одном месте — в `knowledge/`. Параллельного второго слоя документации в репозитории нет.

**Why this priority**: это корень задачи — пока два слоя живы, любой агент и любой разработчик рискует править не тот файл; это же причина, по которой `docs/` возвращался после уборок.

**Independent Test**: открыть `master`, убедиться, что каталога `docs/` нет, а перенесённые материалы доступны по новым путям (индекс `knowledge/product/README.md` + `knowledge/README.md`).

**Acceptance Scenarios**:

1. **Given** репозиторий в `master`, **When** проверяем содержимое корня, **Then** каталога `docs/` нет, а все 73 прежних материала доступны по путям из `knowledge/` или `archive/`.
2. **Given** агент ищет per-feature документ, **When** он открывает `knowledge/product/features/`, **Then** находит 26 документов, каждый с полным набором секций FR-009.
3. **Given** читатель ищет рецепт операции (доступ к Forgejo, логи прода, доставка конфигов, ротация ключей, ведение трекера), **When** он открывает `knowledge/guidelines/runbooks/`, **Then** находит 5 рецептов в формате runbook.

---

### User Story 2 - Параллельный слой не возвращается (Priority: P1)

Любой, кто попытается создать каталог `docs/` в корне репозитория, получает отказ: проверка падает на локальном коммите и в CI, объясняя причину.

**Why this priority**: без автоматической защиты `docs/` вернётся при первой же новой документации — именно этот риск закрывает исходная задача.

**Independent Test**: создать каталог `docs/` с одним файлом, убедиться, что локальная проверка и CI-джоб падают; убрать каталог — проверки снова зелёные.

**Acceptance Scenarios**:

1. **Given** в рабочем дереве появился каталог `docs/`, **When** выполняется локальная проверка перед коммитом, **Then** она завершается с ненулевым кодом и понятным сообщением.
2. **Given** каталог `docs/` создан в ветке, **When** выполняется CI, **Then** джоб падает, а зелёная сборка без `docs/` проходит.
3. **Given** проверка синхронности двух копий CI-конфигурации, **When** они расходятся, **Then** проверка это замечает.

---

### User Story 3 - Правка кода обновляет продуктовый документ (Priority: P2)

Разработчик, изменивший поведение продукта, обновляет соответствующий per-feature документ в той же ветке — путь к нему указан в правилах, а проверка структуры требует наличия документа.

**Why this priority**: FR-009 — действующее правило Constitution; после переезда оно должно указывать на живой путь, иначе правило станет пустым (проверка на пустом каталоге зелёная).

**Independent Test**: изменить per-feature документ, добавить недостающую секцию, запустить проверку — она должна указать на конкретный нарушенный файл.

**Acceptance Scenarios**:

1. **Given** per-feature документ без одной из шести обязательных секций, **When** выполняется проверка структуры, **Then** она указывает на этот файл и отсутствующую секцию.
2. **Given** правила проекта, **When** агент ищет, где обновлять per-feature документ, **Then** он находит единый актуальный путь, а не два конкурирующих.

---

### User Story 4 - Ничего не потеряно (Priority: P2)

Читатель отчёта об аудите #336 находит BR-код дефекта и по нему — компонент `knowledge/`, где описано исправление.

**Why this priority**: 267 уникальных BR-кодов существуют только в `docs/`; при наивном переносе они исчезли бы вместе с каталогом.

**Independent Test**: взять 10 BR-кодов из реестра, найти каждый в `knowledge/` по указанной ссылке.

**Acceptance Scenarios**:

1. **Given** реестр аудита, **When** ищем BR-код, **Then** получаем строку «дефект → компонент» и ссылку на компонент.
2. **Given** проверка соответствия BR-кодов, **When** код упомянут в `knowledge/`, но отсутствует в реестре, **Then** проверка падает.

---

### User Story 5 - Старые ссылки остаются объяснимыми (Priority: P3)

Читатель старого отчёта, письма или агентской памяти видит ссылку на `docs/...` и понимает, куда материал переехал.

**Why this priority**: внешние потребители ссылаются на старые пути (письма, отчёты, память агентов); без справочника они получают тупик, но основной ценности это не несёт.

**Independent Test**: открыть справочник «бывших путей» и убедиться, что каждый старый путь из `docs/` присутствует.

**Acceptance Scenarios**:

1. **Given** старый путь `docs/features/<slug>.md`, **When** ищем его в справочнике, **Then** находим новый путь.
2. **Given** ссылка на несуществующий файл, **When** проверка целостности ссылок проходит, **Then** она не падает: ссылка либо переписана, либо помечена как осознанная отсылка.

---

### Edge Cases

- **Относительные ссылки внутри перенесённых файлов**: глубина меняется (`docs/features/x.md` → `knowledge/product/features/x.md`), сотни ссылок вида `../../knowledge/…` требуют пересчёта; внутри `knowledge/` адресуется кратчайшая относительная форма.
- **Генерируемый API-вывод**: `docs/api/dokka/`, `docs/api/typedoc-*` — в `.gitignore`; после переезда генератор пишет в `build/api-docs/`, иначе каталог `docs/` не исчезнет с диска.
- **Рантайм-копия скилла перекрывает git-версию**: DSH грузит `.dsh/skills/` (ранг 100) выше `.agents/` (ранг 200) — правка git-версии не дойдёт до агента без синхронизации рантайм-копии.
- **Две копии CI-конфигурации расходятся**: контрактный тест покрывает только одну — рассинхрон не ловится.
- **Архивные per-feature документы** (`archive/docs/features/`) вне скоупа, но проверяются тем же guard'ом — путь в проверке сохраняется.
- **BR-коды без зеркала в `knowledge/`**: для них строка реестра должна быть самодостаточной.
- **Уже битые ссылки** (20+ ссылок на файлы, уехавшие в `archive/docs/`; 10 несуществующих в governance-каталоге; ссылки в `adr/0001…0004`): починка входит в усилие, иначе критерий «0 битых ссылок» недостижим.
- **`specs/`**: переписываются только markdown-ссылки; текстовые упоминания остаются (политика проекта).
- **`livedocs/`**: каталога нет в репозитории, ссылки на него уже битые — не чиним.
- **Сравнительные таблицы generic ↔ Karaoke в скиллах**: не трогаем.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: В `master` не должно быть ни одного отслеживаемого файла в каталоге `docs/` в корне репозитория.
- **FR-002**: Каждый из 73 файлов слоя `docs/` должен иметь однозначно определённое место: перенос, слияние с существующим документом, сворачивание или архив — в соответствии с картой решений.
- **FR-003**: Каталог `knowledge/product/features/` должен содержать 26 per-feature документов (25 продуктовых + `tonality-bpm-songs-table`), каждый — с полным набором шести секций FR-009.
- **FR-004**: `knowledge/product/README.md` должен служить индексом продуктовой ветки и содержать правило границы «что пишется в продуктовую ветку, а что — в компонент домена».
- **FR-005**: Должен существовать шаблон `knowledge/templates/product-feature.md`, а `knowledge/templates/README.md` — описывать его как шаблон второго профиля.
- **FR-006**: `knowledge/README.md` должен содержать: строку `product/` в таблице «Структура», таблицу двух профилей формата, обновлённый Karaoke-override #5, раздел «Бывшие пути», расширенный шаг Initialize протокола агента.
- **FR-007**: `knowledge/guidelines/runbooks/` должен содержать 5 runbook'ов: 4 операционных (из `docs/ops/`) и «How to: вести Plane-трекер» (из `docs/tracker-setup.md`).
- **FR-008**: Гайд по KDoc/JSDoc должен находиться в `knowledge/guidelines/kdoc-jsdoc.md`; генератор документации должен писать вывод в `build/api-docs/`, и этот путь должен быть исключён из системы контроля версий.
- **FR-009**: Enforcement-памятка MUST #0 должна находиться в `knowledge/guidelines/knowledge-first.md` и быть отражена в индексе guidelines.
- **FR-010**: `knowledge/system/infra/governance-docs.md` не должен содержать ссылок на несуществующие файлы и должен отражать новые места документации.
- **FR-011**: `knowledge/system/infra/audit-336-registry.md` должен содержать все 510 уникальных BR-кодов слоя `docs/features/`, карточку каждого пакета аудита и явный список кодов, не имеющих зеркала в `knowledge/`.
- **FR-012**: Проверка «каталог `docs/` в корне отсутствует» должна выполняться в CI **обоих** конфигурациях сборки и в локальной проверке перед коммитом; должна иметь фикстуры, ловящие появление `docs/`, и пропускать чистое дерево.
- **FR-013**: Должна существовать проверка синхронности двух копий CI-конфигурации, иначе рассинхрон гейтов не обнаруживается.
- **FR-014**: Ни одна ссылка на перемещаемый файл не должна остаться устаревшей в `knowledge/`, `specs/`, коде (KDoc/JSDoc), корневых правилах, инструментах и CI-конфигурации; уже сломанные ссылки должны быть починены в этом же усилии.
- **FR-015**: Должна существовать проверка соответствия BR-кодов реестру: код, упомянутый в `knowledge/`, обязан присутствовать в реестре, а код из реестра — иметь зеркало или быть помеченным как не имеющий зеркала.
- **FR-016**: Реестр доменов `knowledge/domains/README.md` не должен изменяться: продуктовый слой — структурная категория, а не bounded context; Linking Protocol к `knowledge/product/**` не применяется.
- **FR-017**: Должен быть принят ADR `knowledge/adr/0011-merge-docs-into-knowledge.md`, фиксирующий границу двух веток SSoT, запрет каталога `docs/`, второй профиль формата, реестр аудита и инвариант рантайм-копии скилла; в ADR-0007 должно быть проставлено только метаданное `superseded_by`, тело ADR-0007 не изменяется; реестр `knowledge/adr/README.md` должен отражать частичную замену.
- **FR-018**: Уникальные BR-коды из сворачиваемых мост-файлов (BR-258, BR-541, BR-601) должны попасть в реестр аудита, а не потеряться.
- **FR-019**: История перенесённых файлов должна сохраняться (перенос через `git mv`), чтобы просмотр истории файла показывал исходную историю.
- **FR-020**: Проектная копия скилла `managing-living-docs` и ссылка в `audit-living-docs` должны быть приведены в соответствие с новой структурой; рантайм-копия скилла должна быть синхронизирована с проектной.
- **FR-021**: `AGENTS.md`, `CLAUDE.md`, `.specify/memory/constitution.md`, `.github/PULL_REQUEST_TEMPLATE.md`, `tools/README.md`, `PR_DESCRIPTION.md` не должны содержать ссылок на `docs/`, кроме справочника «бывших путей» и таблицы соответствия старых путей.
- **FR-022**: Слой `knowledge/product/` должен быть включён в проверку структуры Knowledge, а `knowledge/templates/product-feature.md` — в проверку наличия шаблонов.
- **FR-023**: Миграция должна быть выполнена одной атомарной волной: перенос, ссылки, инструменты, правила, guard и ADR приземляются вместе; отдельная последующая волна выполняет только дочистку.
- **FR-024**: Справочник «бывших путей» должен покрывать все старые пути слоя `docs/`, включая пути удалённых и свёрнутых файлов.

### Key Entities

- **Продуктовый слой**: ветвь SSoT `knowledge/product/` — решения о поведении продукта, в отличие от устройства системы, которое описано в доменах и системном слое.
- **Per-feature документ**: документ FR-009 в `knowledge/product/features/<slug>.md` — что делает, зачем, как работает, инварианты, известные ловушки, ссылки.
- **Runbook**: операционный рецепт в `knowledge/guidelines/runbooks/` — контекст, шаги, проверка, rollback.
- **Реестр аудита #336**: сводный перечень BR-кодов и карточек пакетов аудита с обратной ссылкой на компонент.
- **Профиль формата**: правило проверки документов определённого вида; в проекте их два — компонент домена и per-feature документ.
- **Бывшие пути**: справочник соответствия старых путей `docs/` новым путям `knowledge/` для внешних потребителей.
- **Guard**: автоматическая проверка, падающая при появлении запрещённого состояния (каталог `docs/`, рассинхрон CI-конфигураций, BR-код без записи в реестре).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 0 отслеживаемых файлов в каталоге `docs/` в `master`.
- **SC-002**: 510 из 510 уникальных BR-кодов слоя `docs/features/` присутствуют в реестре аудита (100%).
- **SC-003**: 0 битых ссылок на перемещённые файлы в `knowledge/`, `specs/`, коде, корневых правилах, инструментах и CI-конфигурации (проверка: 0 замечаний при прогоне проверок ссылок и полного CI).
- **SC-004**: 26 из 26 per-feature документов проходят проверку структуры (100%).
- **SC-005**: Проверка запрета каталога `docs/` падает на фикстуре «каталог `docs/` появился» и проходит на чистом дереве (2 из 2 сценариев).
- **SC-006**: 0 новых нарушений при полном прогоне линтера и структурных проверок Knowledge (0 вместо 0 на старте).
- **SC-007**: 0 упоминаний `docs/` в корневых правилах и инструментах, кроме справочника «бывших путей» и таблицы соответствия (исключения перечислены явно).
- **SC-008**: CI зелёный, включая джобы структуры Knowledge, линтера документации и проверки ссылок; обе копии CI-конфигурации содержат одинаковый набор новых проверок.
- **SC-009**: Сверка «до/после» по всем 73 файлам слоя `docs/` показывает 73 из 73 учтённых (перенос / слияние / сворачивание / архив) и 0 неучтённых.
- **SC-010**: Для каждого из 10 решённых тикетов карты KARAOKE-323 найдено конкретное место в итоговом состоянии репозитория (10 из 10).
- **SC-011**: Просмотр истории любого перенесённого файла показывает исходные коммиты (перенос через `git mv`, 100% файлов).

## Assumptions

- Решения владельца от 2026-10-29, зафиксированные в карте KARAOKE-323 (§ Notes), являются контрактом; спека их не переоткрывает. Изменение FR/SC возможно только по слову владельца.
- Слой `archive/docs/**` (43 файла) остаётся вне скоупа; guard проверяет только корень репозитория.
- `livedocs/**` в репозитории отсутствует; его упоминания не чинятся.
- Трекер проекта — Plane; номера Plane не совпадают с номерами OpenProject, соответствие — в таблице соответствия. Спека ссылается на Plane-идентификатор `KARAOKE-334`.
- Перенос выполняется в feature-ветке `675-merge-docs-into-knowledge`, созданной из `master`; прямое изменение `master` запрещено.
- Синхронизация рантайм-копий скиллов в `.dsh/skills/` — операционная задача вне системы контроля версий, выполняется на машине агента после merge.
- Вторая волна (дочистка) не входит в критерии приёмки этой спеки: это отдельная задача, порождённая остатками после основной волны.

## Приложение: карта переноса (сводка по группам)

| Источник (`docs/`) | Файлов | Куда | Решение |
|---|---|---|---|
| `docs/features/` — продуктовые | 25 | `knowledge/product/features/<slug>.md` | перенос без изменений |
| `docs/features/` — мост-файлы | 10 | 1 перенос (`tonality-bpm-songs-table`), 7 свёртка в компоненты, 2 удаление | KARAOKE-325 |
| `docs/features/` — пакеты аудита #336 | 30 | `knowledge/system/infra/audit-336-registry.md` (BR-коды и карточки пакетов) | KARAOKE-327 |
| `docs/ops/` | 4 | `knowledge/guidelines/runbooks/` | KARAOKE-324 |
| `docs/tracker-setup.md` | 1 | `knowledge/guidelines/runbooks/tracker-setup.md` | KARAOKE-324 |
| `docs/api/README.md` | 1 | `knowledge/guidelines/kdoc-jsdoc.md` | KARAOKE-324 |
| `docs/governance/knowledge-first.md` | 1 | `knowledge/guidelines/knowledge-first.md` | KARAOKE-326 |
| `docs/architecture-notes.md` | 1 | `archive/docs/architecture-notes.md` | карта (Notes) |
| Итого | **73** | | |

Полные пофайловые решения — в тикетах карты KARAOKE-323 (KARAOKE-324…KARAOKE-332) и в артефактах `.scratch/karaoke/wayfinding/docs-merge/`.
