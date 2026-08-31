# Research: 283-admin-find-parent

**Branch**: `283-admin-find-parent`
**Date**: 2026-08-31
**Spec**: `../spec.md`

## Цель

Зафиксировать технические решения для новой кнопки «Поиск родителя» в админке `webvue3`, обслуживающей единственного автора. Закрыть все «NEEDS CLARIFICATION» из Technical Context и зафиксировать выбор с альтернативами.

## Решения

### R-001. Имя нового HTTP-эндпоинта

**Decision**: `POST /api/utils/findparentforauthor`

**Rationale**:
- Префикс `/api/utils/` уже используется для одноразовых/фоновых админских операций (`customfunction`, `rescanallcensorednames`, `exportalignmentdataset`, `backfillpublishflags`, `backfillalbumsfromsongs`). Семантически это «utils» — и наша кнопка попадает туда же.
- Суффикс `findparentforauthor` однозначно говорит, что это **только фаза 1** текстового поиска родителя и **для одного автора** (в отличие от `/utils/customfunction`, которая глобальна и двухфазна).
- Параметры запроса: `author: String` (обязательно, trimmed), `crossAuthor: Boolean = false`.

**Alternatives considered**:
- `POST /api/songs/findparent` — отвергнуто, потому что `/api/songs/...` исторически про операции над одной песней (`/songs/autoassignoriginalall` — единственное исключение, и то с суффиксом `all`). Наша операция явно «утилитная», а не per-song.
- Расширить существующий `/utils/customfunction` параметрами `author` + `crossAuthor` — отвергнуто: (1) ломает обратную совместимость поведения глобальной кнопки; (2) смешивает две разные UX-фичи в одном endpoint'е; (3) требует доп-флагов и в `Utils.customFunction`, и во Vuex-action `customFunctionPromise`.

---

### R-002. Точка расширения `findParentCandidateId` — новый параметр vs helper

**Decision** (обновлено 2026-08-31 по замечанию пользователя): добавить опциональный параметр `crossAuthor: Boolean = true` И убрать фильтр по наличию `source_text` (раньше приоритизировались кандидаты с текстом — это ошибочно отсеивало «сирот»-родителей без текста).

**Rationale**:
- `findParentCandidateId` — приватная функция в `Utils.kt`, вызывается из `customFunction` (фаза 1) и `findParentForAuthor` (новая кнопка). Если поменять сигнатуру с дефолтом — оба существующих вызова остаются работоспособными без правок (binary-compatible).
- Новая логика (псевдокод):
  ```kotlin
  // candidates — все песни с тем же нормализованным названием (без фильтра по source_text)
  val sameAuthor = candidates.filter { it.author.equals(song.author, ignoreCase = true) }
  // crossAuthor=false → только sameAuthor; crossAuthor=true → fallback на candidates
  val finalPool = if (crossAuthor) sameAuthor.ifEmpty { candidates } else sameAuthor
  return finalPool.minByOrNull { it.id }?.id
  ```
- Изменение **затрагивает обе функции**: `customFunction` тоже теперь ищет родителя среди всех песен автора, не только среди имеющих текст. По решению пользователя — это правильное поведение (замечание 2026-08-31).

**Alternatives considered**:
- Новый helper `findParentCandidateIdSameAuthorOnly` — отвергнуто: дублирование логики нормализации/выборки SQL, расхождение поведения при будущих правках.
- Сохранить приоритет по тексту для `customFunction` (доп. параметр `withTextOnly: Boolean = true`) — отвергнуто: пользователь явно подтвердил, что текущее поведение `customFunction` тоже было неправильным.
- Фильтровать `candidates` сразу на уровне SQL (`AND LOWER(song_author) = LOWER(?)`) — отвергнуто: ломает семантику «сначала того же автора, потом любого» (при `crossAuthor=true` кандидаты других авторов должны быть видны как fallback).

---

### R-003. Защита от гонок

**Decision**: `@Volatile private var isFindParentInProgress: Boolean = false` в `Utils.kt` (single-instance JVM, по образцу `isCensoredRescanInProgress`, см. Utils.kt:236).

**Rationale**:
- `karaoke-app` разворачивается в одном контейнере на admin-машине (Constitution § «Технологический стек»). Single-instance — in-memory `@Volatile` достаточно.
- Возвращаемое значение: `"OK"` (если запущено) или `"ALREADY_RUNNING"` (если занято). Фронт по нему решает, какой тост показать (`alertType: 'warning'` для уже запущено).
- `try { … } finally { isFindParentInProgress = false }` чтобы флаг снимался даже при исключении.

**Alternatives considered**:
- Синхронизация через DB (lock-таблица) — overkill, добавляет I/O ради ситуации, которая у админа случается раз в несколько минут.
- Никакой защиты — отвергнуто: пользователь может случайно дабл-кликнуть или открыть две вкладки админки.

---

### R-004. Vuex-action: `findParentForAuthorPromise`

**Decision**: Action `findParentForAuthorPromise(ctx, payload)` в `webvue3/src/components/Songs/store.js` рядом с `autoAssignOriginalAllPromise` (та же тематика — операции над автором).

**Rationale**:
- Все «утилитные» actions по автору (`autoAssignOriginalAllPromise`, `customFunctionPromise`, `markDublicatesPromise`) живут в одном модуле `Songs` (не `Home` и не `Utils`).
- Сигнатура: `payload = { author: string, crossAuthor: boolean }`. URL `/api/utils/findparentforauthor`, метод `POST`, query-params `author` + `crossAuthor`.

**Alternatives considered**:
- Отдельный Vuex-модуль `utils/` — отвергнуто: для одной новой кнопки это преждевременное обобщение, плюс придётся регистрировать в корневом `index.js`.

---

### R-005. Положение кнопки и `disabled`-логика

**Decision**: новый `<button class="button-action" :disabled="!author" @click="findParentForAuthor">` — **первой строкой** в существующем блоке `<div class="field-and-buttons-wrapper">` с полем «Автор», **перед** кнопкой «Автопривязать оригинал по аудио (статус 1 → 2)».

**Rationale**:
- Пользователь явно указал «над кнопкой Автопривязать оригинал по аудио…» — это первый элемент ряда.
- Тот же `:disabled="!author"` (Vue будет считать пустую строку и строку из одних пробелов как falsy **только** если не делать `.trim()` автоматически). В `HomeView.vue` уже есть шаблон с `:disabled="!author"` для соседней кнопки — менять семантику trimmed-обработки не нужно: пусть админ сам тримит, иначе опечатка с пробелом в конце уйдёт в бэкенд.
- Бэкенд всё равно делает `author.trim()` (см. `autoAssignOriginalAll`, ApiController.kt:5010) — защита двойная.

**Alternatives considered**:
- Вынести кнопку в отдельный блок — отвергнуто: пользователь явно сказал «над Автопривязать…», значит в **том же** wrapper'е; так логически связные операции (поиск родителя → автопривязка аудио) визуально сгруппированы.

---

### R-006. SSE-уведомление — заголовок и формат тела

**Decision**: `head = "Поиск родителя (автор «<author>»)"`, `body = "Обработано N, родитель назначен M (найдено, но пропущено из-за текста: K)"`.

**Rationale**:
- У существующей `customFunction` (Utils.kt:222) заголовок `"Поиск родителей и аудио-родителей"` — чтобы не путать с глобальной кнопкой, добавляем уточнение `(автор «...»)` и явно убираем из body упоминание аудио-фазы.
- Это позволяет админу в ленте SSE-уведомлений с одного взгляда отличить «глобальный» запуск от «авторского».

**Alternatives considered**:
- Использовать тот же заголовок, что у `customFunction` — отвергнуто: админ не сможет отличить источник уведомления.

---

### R-007. Способ передачи `crossAuthor` в `Utils.findParentForAuthor`

**Decision**: новая публичная top-level функция `findParentForAuthor(author: String, crossAuthor: Boolean, storageService, storageApiClient): String` в `Utils.kt`, которая:
1. Сериализует выборку `SELECT id FROM tbl_songs WHERE root_id = 0 AND LOWER(song_author) = LOWER(?) ORDER BY id`.
2. В цикле — `Song.loadFromDbById` + `findParentCandidateId(song, WORKING_DATABASE, crossAuthor = crossAuthor)`.
3. Логика применения — копия фазы 1 из `customFunction` (Utils.kt:118-186), но с `crossAuthor`.
4. Фоновая (`thread { … }`), `@Volatile`-флаг `isFindParentInProgress`, SSE-тост с заголовком по R-006.

**Rationale**:
- Дублирование с фазой 1 `customFunction` минимально: те же 60 строк по образцу. Извлекать в общий helper преждевременно (пока нет 3+ потребителей).
- Все нужные сервисы (`storageService`, `storageApiClient`) уже инжектятся в `ApiController` — повторно передавать из контроллера в функцию `Utils.findParentForAuthor` — тот же паттерн, что и `customFunction`/`autoAssignOriginalAll`.

**Alternatives considered**:
- Сделать общий `runParentSearchPhase1(ids, crossAuthor)` и дёргать его и из `customFunction`, и из `findParentForAuthor` — принято как **follow-up**, **не** в скоупе этой фичи (см. tasks.md — будет помечено как tech-debt / refactor).

---

### R-008. Обновление `LiveDocs` (Constitution § VI FR-009)

**Decision**: в этом PR не трогать `docs/features/` — наша фича затрагивает существующий документ про поиск родителей (если он есть), но для одного нового endpoint'а и одной кнопки отдельный per-feature документ создавать нерационально. Помечаем это как **out of scope** для spec.md и tasks.md.

**Rationale**:
- Конкретные per-feature документы для уже существующих сущностей (`docs/features/songs.md`, `docs/features/parent-search.md`) нужно проверить в `/speckit.tasks`/`/speckit.implement` — если они есть и описывают текстовый поиск родителей, в том же PR добавить секцию про новый endpoint.

**Alternatives considered**:
- Создать `docs/features/283-admin-find-parent.md` — отвергнуто: feature per-doc пишется, когда появляется новая подсистема. Наша фича — расширение существующей (текстовый поиск родителей) + UI-кнопка; достаточно отразить в существующих документах, если они есть.

---

### R-009. Документирование новых публичных API (Constitution § VI FR-006)

**Decision**:
- `KDoc` для нового `Utils.findParentForAuthor` со ссылкой на `specs/283-admin-find-parent/spec.md` через `@see`.
- `JSDoc` для нового Vuex-action `findParentForAuthorPromise` со ссылкой на ту же спеку.
- KDoc/JSDoc без `@see`-ссылки на per-feature документ — допустимо (см. R-008: per-feature документ не создаём).

**Rationale**: прямое требование Constitution § VI FR-006 (KDoc/JSDoc с `@see` или ссылкой на per-feature документ). Поскольку per-feature документа нет — ссылка идёт на spec.md.

**Alternatives considered**: пропустить KDoc — отвергнуто, нарушает FR-006.

---

### R-010. Ktlint / ESLint / prettier / Docker-image

**Decision**: после правок кода выполнить полный обязательный цикл из `AGENTS.md` (порядок — non-negotiable):
1. `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`
2. `./gradlew :karaoke-web:ktlintCheck`, `cd webvue3 && npm run lint`, `cd karaoke-public && npm run lint` (baseline не должен расти).
3. На `nsa-i9`/`nsa` (текущая машина) дополнительно: `./gradlew :karaoke-app:bootJar` (см. AGENTS.md, машинно-специфичное исключение).
4. `cd webvue3 && npm run build && npm run format:check`.
5. `cd deploy && bash do.sh build_webvue3` — Docker-образ (Vite-build ≠ multi-stage Dockerfile).

**Rationale**: прямое требование AGENTS.md «Обязательная проверка после ЛЮБОГО изменения кода».

**Alternatives considered**: только `npm run build` (без Docker-образа) — отвергнуто: после Pass 245 выяснилось, что Vite-build ≠ Docker-build (multi-stage `COPY ./webvue3/` отрезает кросс-импорты).

---

### R-011. Идемпотентность `findAudioParentForAuthor` по `audio_parent_id`

**Decision**: SQL-фильтр `AND audio_parent_id = 0` в начальной выборке (помимо `root_id <> 0`).

**Rationale**:
- По замечанию пользователя от 2026-08-31: песни, для которых `findAudioParentByWaveform` уже отработал ранее и записал `audio_parent_id <> 0`, не должны повторно обрабатываться — тяжёлая `WaveformCompare.compareWaveforms` для них уже не нужна.
- В `findAudioParentByWaveform` есть встроенный кэш `audio_compare_history` (не сверяет повторно уже сверенные пары), но он не избавляет от полной загрузки песни и обхода `findFamilySongIds` для каждой. SQL-фильтр дешевле.
- Идемпотентность обеспечивается **дешевле** на уровне SQL, чем в `findAudioParentForAuthor` (после загрузки).

**Alternatives considered**:
- Фильтр «после загрузки» в Kotlin (`if (song.audioParentId != 0L) return …`) — отвергнуто: загрузка `Song.loadFromDbById` уже произошла, FK-джойны сделаны, хотя можно было сэкономить.
- Положиться только на кэш `audio_compare_history` внутри `findAudioParentByWaveform` — отвергнуто: на повторном запуске функция всё равно выполнит `findFamilySongIds` + загрузку всех кандидатов + `compareWaveforms` для новых пар. SQL-фильтр радикально сокращает объём работы.

---

## Открытые вопросы

Нет. Все NEEDS CLARIFICATION из Technical Context были закрыты на этапе спекования (Assumptions A-001…A-010). В этом research.md только зафиксированы технические решения с обоснованием альтернатив.
