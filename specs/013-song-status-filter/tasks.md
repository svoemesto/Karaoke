---

description: "Task list for feature 013-song-status-filter"
---

# Tasks: Показ на проде только песен со статусом готовности >= 3

**Input**: Design documents from `/specs/013-song-status-filter/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/public-song-visibility.md, quickstart.md

**Tests**: Не запрошены явно и не предусмотрены Конституцией для этого слоя
(CI автотестов нет — см. plan.md → Technical Context → Testing). Вместо
автотестов — ручные сценарии `quickstart.md`, включённые в задачи ниже.

**Organization**: Задачи сгруппированы по user story из `spec.md` (US1 =
закрома/спецзаказ, US2 = поиск, US3 = regression guard для админки).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Можно выполнять параллельно (разные файлы, нет зависимостей)
- **[Story]**: US1 / US2 / US3 из spec.md
- Указаны точные пути к файлам

## ⚠️ Важное предупреждение о конфликтах файлов между историями

`PublicApiController.kt` и `MainController.kt` (`karaoke-web`) каждый
редактируются **и** в US1 (метод `zakroma()`), **и** в US2 (методы `songs()`
и `filter()` соответственно). Это разные методы в одном файле — параллельная
правка двумя разными агентами/разработчиками рискует конфликтом при мёрдже.
**Рекомендация**: выполнять US1 и US2 последовательно (не параллельно
разными исполнителями), даже несмотря на то что они логически независимы и
независимо тестируемы.

---

## Phase 1: Setup (Shared Infrastructure)

**Не применимо.** Фича не добавляет ни нового модуля, ни новых зависимостей —
все изменения в уже существующих Kotlin-файлах `karaoke-app`/`karaoke-web`.
Задач нет.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Не применимо.** Механизм, на который опирается вся фича — generic-фильтр
`id_status` с поддержкой операторов (`>=` и др.) в `Song.getWhereList`
(`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:7130-7148`)
— уже существует и подтверждён в `research.md` (Decision 2). Блокирующих
общих задач перед историями нет.

---

## Phase 3: User Story 1 - Закрома автора и спецзаказная плашка (Priority: P1) 🎯 MVP (часть 1/2)

**Goal**: Публичные закрома автора и виртуальная плашка «Отдельные песни
разных авторов» показывают только песни со статусом готовности >= 3; авторы
и альбомы, у которых после фильтрации не осталось ни одной песни, не
отображаются.

**Independent Test**: Открыть закрома автора с песнями разных статусов (см.
`quickstart.md` → Сценарий 1) и спецзаказную плашку (Сценарий 2); убедиться,
что видны только песни со статусом >= 3, а полностью «пустые» после
фильтрации авторы/альбомы не отображаются.

### Implementation for User Story 1

- [X] T001 [US1] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Zakroma.kt` добавить в `Zakroma.getZakroma(...)` опциональный параметр `onlyPublished: Boolean = false`; при `true` добавлять `"id_status" to ">=3"` в `args`, передаваемые в `Song.loadListFromDb`. Обновить KDoc функции с `@see docs/features/special-orders.md`.
- [X] T002 [US1] В том же файле `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Zakroma.kt` добавить аналогичный параметр `onlyPublished: Boolean = false` в `Zakroma.getZakromaBySpecialOrder(...)`, с той же логикой добавления `"id_status" to ">=3"` в `args`. Выполнять после T001 (один файл, чтобы избежать конфликта правок).
- [X] T003 [P] [US1] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt`, метод `zakroma()` (обе ветки — `specialBucket=true` → `getZakromaBySpecialOrder`, и обычная → `getZakroma`) передать `onlyPublished = true`. Зависит от T001, T002.
- [X] T004 [P] [US1] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt`, legacy-метод `zakroma()` (`GET /zakroma`) передать `onlyPublished = true` в вызов `Zakroma.getZakroma`. Зависит от T001, T002.
- [X] T005 [US1] Обновить `docs/features/special-orders.md` (FR-009 Конституции): зафиксировать, что `getZakromaBySpecialOrder` (и обычные закрома автора) теперь фильтруют песни по `id_status >= 3`, со ссылкой на эту фичу. Зависит от T002.
- [X] T006 [US1] Вручную выполнить Сценарии 1 и 2 из `quickstart.md` (закрома автора со смешанными статусами + спецзаказная плашка) в локальном sandbox-стеке. Зависит от T003, T004.

**Checkpoint**: Закрома и спецзаказная плашка на публичном сайте показывают
только готовые песни; поиск (US2) ещё не тронут.

---

## Phase 4: User Story 2 - Поиск (Priority: P1) 🎯 MVP (часть 2/2)

**Goal**: Публичный поиск (по названию/автору/альбому/тексту) не возвращает
песни со статусом готовности < 3.

**Independent Test**: Выполнить поиск по критерию, совпадающему с песней
статуса < 3 (см. `quickstart.md` → Сценарий 3 и Сценарий 4 для legacy
страницы); убедиться, что песня не появляется в выдаче, а песня со статусом
>= 3 по тому же критерию — появляется.

### Implementation for User Story 2

- [X] T007 [US2] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt`, метод `songs()`, добавить `attr["id_status"] = ">=3"` перед вызовом `Song.loadListFromDb` (та же условная ветка, что уже используется для `song_name`/`author`/`text`/`album` — добавляется безусловно, не по флагу пользователя). Выполнять после завершения T003 (общий файл с US1 — см. предупреждение выше).
- [X] T008 [P] [US2] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt`, legacy-метод `filter()` (`GET /filter`), добавить `attr["id_status"] = ">=3"` перед вызовом `Song.loadListFromDb`. Выполнять после завершения T004 (общий файл с US1).
- [X] T009 [US2] Вручную выполнить Сценарии 3 и 4 из `quickstart.md` (поиск через `karaoke-public` + legacy `GET /filter`) в локальном sandbox-стеке. Зависит от T007, T008.

**Checkpoint**: Оба P1-сценария (закрома и поиск) реализованы и проверены —
это полноценный MVP фичи.

---

## Phase 5: User Story 3 - Админка не теряет видимость (Priority: P2)

**Goal**: Редактор в админке (`webvue3`, и admin-only `karaoke-app`
`MainController.zakroma`) продолжает видеть песни всех статусов — фильтр их
не затрагивает.

**Independent Test**: Открыть в `webvue3` список/карточку песни со статусом
< 3 у автора из предусловий `quickstart.md` и убедиться, что она видна и
редактируема как раньше (Сценарий 5).

### Implementation for User Story 3

- [X] T010 [US3] Код-ревью guard: убедиться, что `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt` (admin-only `zakroma()`, единственный оставшийся вызыватель `Zakroma.getZakroma` без `onlyPublished`) НЕ изменён и по-прежнему вызывает `Zakroma.getZakroma` без параметра `onlyPublished` (использует default `false`). Зависит от T001, T002.
- [X] T011 [US3] Вручную выполнить Сценарий 5 из `quickstart.md` (`webvue3` + admin-only `/zakroma` из `karaoke-app`) — обе песни (статус < 3 и >= 3) видны и редактируемы. Зависит от T003, T004, T010.

**Checkpoint**: Все три user story проверены независимо; регрессий в
админке нет.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Финальные штрихи, затрагивающие несколько историй сразу.

- [X] T012 [P] Обновить `docs/features/stats.md` (FR-009): зафиксировать, что публичные листинги (закрома, поиск) теперь согласованы с определением «коллекция» (`id_status>=3`), устраняя ранее существовавшее расхождение (см. `spec.md` SC-003).
- [X] T013 [P] Проверить/добавить KDoc `@see`-ссылки на изменённые публичные функции (`PublicApiController.zakroma`, `PublicApiController.songs`, `MainController.zakroma`, `MainController.filter` в `karaoke-web`) на `docs/features/special-orders.md`/`docs/features/stats.md` (Constitution Principle VI / FR-006).
- [X] T014 Прогнать `./gradlew ktlintCheck` для затронутых модулей (`karaoke-app`, `karaoke-web`) и поправить нарушения перед коммитом (см. CLAUDE.md → обязательные проверки перед `git commit`).
- [X] T015 Вручную выполнить Сценарий 6 из `quickstart.md` (смена статуса песни на лету — песня появляется/исчезает из закромов и поиска без рестарта сервисов).
- [X] T016 Финальный полный прогон всех 6 сценариев `quickstart.md` подряд как sign-off перед PR.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup / Foundational**: отсутствуют, ничем не блокируют начало работы.
- **User Story 1 (Phase 3)**: может стартовать сразу.
- **User Story 2 (Phase 4)**: логически независима от US1, но T007/T008 стоит
  выполнять **после** T003/T004 соответственно — общие файлы
  (`PublicApiController.kt`, `MainController.kt`), см. предупреждение в
  начале документа.
- **User Story 3 (Phase 5)**: по сути verification-only; T010 может
  выполняться сразу после T001/T002 (не ждёт US2), T011 ждёт видимых
  изменений US1 (T003, T004), чтобы было с чем сравнивать регрессию.
- **Polish (Phase 6)**: после завершения всех трёх историй.

### Within Each User Story

- US1: T001 → T002 (один файл) → {T003, T004 параллельно} → T005 → T006.
- US2: {T007 после T003} и {T008 после T004} → T009.
- US3: T010 (после T001/T002) → T011 (после T003/T004).

### Parallel Opportunities

- T003 и T004 — разные файлы, можно параллельно (после T001+T002).
- T007 и T008 — разные файлы, можно параллельно (каждый после своего
  предшественника из US1).
- T012 и T013 — разные файлы (документация), можно параллельно.
- US1 и US2 в целом **не рекомендуется** распараллеливать между разными
  исполнителями из-за общих файлов (см. предупреждение выше), даже если
  формально они независимо тестируемы.

---

## Parallel Example: после завершения T001+T002

```bash
# Публичные call-site'ы закромов (US1) — разные файлы:
Task: "PublicApiController.kt zakroma(): onlyPublished=true в обеих ветках"
Task: "MainController.kt (karaoke-web) legacy zakroma(): onlyPublished=true"
```

```bash
# После завершения соответствующих задач US1 — публичные call-site'ы поиска (US2):
Task: "PublicApiController.kt songs(): id_status>=3 в attr"
Task: "MainController.kt (karaoke-web) legacy filter(): id_status>=3 в attr"
```

---

## Implementation Strategy

### MVP First (обе P1-истории — US1 и US2)

Обе истории спецификации имеют приоритет P1 (закрома и поиск одинаково
критичны для доверия пользователя — см. `spec.md`), поэтому полноценный MVP
этой фичи включает **обе**:

1. Phase 3 (US1) — закрома и спецзаказная плашка.
2. Phase 4 (US2) — поиск.
3. **STOP and VALIDATE**: прогнать Сценарии 1-4 `quickstart.md`.
4. Phase 5 (US3) — regression guard для админки (быстрый, в основном
   verification-only) — рекомендуется делать перед PR, а не пропускать, т.к.
   именно он защищает ключевой Constitution-gate (Principle V).

### Incremental Delivery

1. US1 (закрома) → проверить независимо → можно задеплоить отдельно, если
   нужно быстрее закрыть самый заметный кейс.
2. US2 (поиск) → проверить независимо → деплой вместе с US1 или следом.
3. US3 (guard) → не добавляет пользовательской ценности сама по себе, но
   обязателен перед мёрджем как проверка отсутствия регрессии в админке.
4. Phase 6 (Polish) — документация (FR-009), линтеры, финальный quickstart.

---

## Notes

- [P] задачи = разные файлы, нет зависимостей друг от друга.
- Метка [Story] связывает задачу с конкретной user story для трассируемости.
- Автотестов в CI для этого слоя нет — вместо "тесты падают → проходят"
  используются ручные сценарии `quickstart.md`, включённые как отдельные
  задачи в каждой истории.
- Коммитить после каждой задачи или логической группы (согласно
  `CLAUDE.md` — ветка `013-song-status-filter`, не `master`).
- Останавливаться на любом чекпоинте для независимой проверки истории.
