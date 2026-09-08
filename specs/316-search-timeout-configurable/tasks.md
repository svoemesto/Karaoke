# Tasks: Настраиваемый таймаут между поисковыми запросами (iter #316)

**Input**: Design documents from `/specs/316-search-timeout-configurable/`
- plan.md (rev 3: UI fix + авто-подбор)
- spec.md (rev 3, 9 FR, 8 SC, 10 Assumption)
- research.md (rev 3, 10 решений R-001..R-010)
- data-model.md (rev 3, MassSearchTimeoutSetting + MassSearchSummary runtime)
- quickstart.md (rev 3, 7 scenarios)
- design.md (rev 3, Алина переделывается под UI fix + авто-подбор)

**Tests**: НЕ включены (CI тестов нет, все `@Disabled` per Karaoke/AGENTS.md). Verify через quickstart.md manual scenarios владельцем.

**Маленькая фича**: rev 3 — UI fix + авто-подбор. **Batch mode** (Lesson #15 iter #3 — MVP-checkpoint не нужен, единый батч до финала).

**Governance (Lessons #11-#14 iter #3, NON-NEGOTIABLE)**:
- Агенты НЕ перезапускают контейнер `karaoke-app`. Smoke-test только владелец.
- Boss self-verify через реальный код/grep ДО отправки задач Алине (Lesson #16 iter #4).
- 5-step verification канон `brief.md:59-63` после ЛЮБОГО изменения кода.
- 0 коммитов/push'ей — вся работа в working tree до явного указания владельца (правило 2026-09-05).
- **Не трогать**: `karaoke-public`, `SongEdit.vue` (массового поиска нет, grep `searchTextForAll=0`).
- **Не создавать**: `SearchTimeoutDialog.vue` (rev 1-2 отклонён владельцем, принцип «сделай так же, как рядом»).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallelizable.
- **[Story]**: только для US1.
- File paths exact.

## Path Conventions

- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`.
- Frontend: `webvue3/src/components/Songs/` + `webvue3/src/views/`.

---

## Phase 1: Setup (Подготовка)

- [ ] T001 [P] Verify что **KaraokeProperties** файл существует: `docker exec karaoke-app bash -c 'grep -a lyricsSearchTimeoutSeconds /sm-karaoke/system/Karaoke.properties || echo "нет ключа"'`. Reference `KaraokeProperties.kt:25` (class, base64-encoded, НЕ БД-таблица) + `:18` (`PATH_TO_KARAOKE_PROPERTIES_FILE = "/sm-karaoke/system/Karaoke.properties"`) + `:40-42` (decode) + `:70` (encode). ⚠️ **Constants.kt:149 не существует** — это rev 1 `WEBVUE_PROPERTIES_FILE_PATH` (Кирилл Р-4).
- [ ] T002 [P] Read reference components:
  - Backend: `karaoke-app/.../Karaoke.kt:156-162` (Karaoke companion + checkSearchAsync прецедент).
  - Backend: `karaoke-app/.../KaraokeProperties.kt:92` (getInt 1-арг), `:148-157` (set с no-op guard).
  - Backend: `karaoke-app/.../controllers/ApiController.kt:4771-4809` (getSearchSongTextAll), `:5204-5343` (doCreateFromFolder), `:5302` (submit), `:209` (pool 4 потока), `:5874` (Properties API).
  - Backend: `karaoke-app/.../services/SseNotificationService.kt:89`, `addressedTypes`.
  - Frontend: `webvue3/src/components/Songs/SongsTable.vue:423` (кнопка), `:1264-1281` (searchTextForAll), `:1289-1314` (custom-confirm «Подтвердите поиск текста»).
  - Frontend: `webvue3/src/views/HomeView.vue:32` (кнопка), `:225-246` (addFilesFromFolder), `:255-268` (custom-confirm «Добавление файлов из папки»).
  - Frontend: `webvue3/src/components/Common/CustomConfirm.vue` (поддержка `params.fields` массива с boolean/select/textarea/text).

---

## Phase 2: Backend (karaoke-app)

- [ ] T003 [P] Modify `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Karaoke.kt`: добавить новое поле `lyricsSearchTimeoutSeconds` рядом с `checkSearchAsync` (стр. ~160). Паттерн (Алина rev 2, дизайн §1.1):
  ```kotlin
  var lyricsSearchTimeoutSeconds: Int
      get() = KaraokeProperties.getInt("lyricsSearchTimeoutSeconds").takeIf { it >= 1 } ?: 10
      set(value) { KaraokeProperties.set("lyricsSearchTimeoutSeconds", value.coerceAtLeast(1)) }
  ```
  ⚠️ **В `KaraokeProperties.kt:92` только 1-арг `getInt(key)`**, двух-аргументный не существует — fallback через `.takeIf { it >= 1 } ?: 10`. KDoc с описанием и ссылкой на spec.md FR-007.

- [ ] T004 Modify `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:4771-4809` (`getSearchSongTextAll` — Путь A): добавить `@RequestParam(required = false) timeout: Int? = null` в сигнатуру; в начале тела (после `val resolvedEngine = ...`):
  ```kotlin
  val effectiveTimeout =
      timeout?.coerceAtLeast(1)
          ?: KaraokeProperties.getInt("lyricsSearchTimeoutSeconds").takeIf { it >= 1 } ?: 10
  ```
  + добавить замеры `cycleStart/lastSuccessTime/minIntervalMs/successfulCount` (FR-009, см. design §2.1).
  Заменить `ids.forEach { id -> ... }` (стр. 4786) на `ids.forEachIndexed { index, id -> ... }`. Внутри цикла — обновить замер `minIntervalMs` при успехе + добавить `Thread.sleep(effectiveTimeout * 1000L)` после `getLyricsSearch` (кроме последней итерации — условие `index < ids.size - 1 && effectiveTimeout > 0`). **НЕ использовать coroutine delay** — метод не suspend, `Thread.sleep` консистентен с `MainController.kt:1626`. **Reference**: `design.md §2.1`.

- [ ] T005 [P] Modify `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt:1602-1640` (`getSearchSongTextAll`, deprecated): добавить `@RequestParam(required = false) timeout: Int? = null`; в начале:
  ```kotlin
  val effectiveTimeout =
      timeout?.coerceAtLeast(1)
          ?: KaraokeProperties.getInt("lyricsSearchTimeoutSeconds").takeIf { it >= 1 } ?: 10
  ```
  Заменить хардкод `Thread.sleep(2000)` (стр. 1626) на `Thread.sleep(effectiveTimeout * 1000L)`. Симметрично ApiController.

- [ ] T006 Modify `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:5302` (`doCreateFromFolder` — Путь B): в **сигнатуру** добавить `@RequestParam(required = false) timeout: Int? = null` (Кирилл Р-3). В начале выполнения:
  ```kotlin
  val searchTimeout = timeout?.coerceAtLeast(1)
      ?: KaraokeProperties.getInt("lyricsSearchTimeoutSeconds").takeIf { it >= 1 } ?: 10
  ```
  + добавить замеры `cycleStart/lastSuccessTime/minIntervalMs/successfulCount` (FR-009).
  **Заменить** `createdList.forEach { newSong -> ... }` (стр. 5217) на `createdList.forEachIndexed { songIndex, newSong -> ... }` (Кирилл Р-2 — иначе `songIndex` не существует). Перед блоком `lyricsSearchExecutor.submit {` (стр. 5302), **внутри** `if (!textResolved) { ... }`, добавить паузу **перед** submit:
  ```kotlin
  if (songIndex < createdList.size - 1 && searchTimeout > 0) {
      Thread.sleep(searchTimeout * 1000L)
  }
  ```
  + обновить `minIntervalMs` при успехе (внутри `submit { try {...} finally {...} }`, FR-009). Это rate-limit между submit'ами в пуле `Executors.newFixedThreadPool(4)`. **НЕ менять** сам пул. **Reference**: `design.md §2.2`.

- [ ] T007 [P] Modify `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`: добавить новый endpoint для UI диалога (Алина rev 2, design §2.3):
  ```kotlin
  @GetMapping("/lyrics-search-timeout")
  @ResponseBody
  fun getLyricsSearchTimeout(): Map<String, Int> =
      mapOf("value" to KaraokeProperties.getInt("lyricsSearchTimeoutSeconds").takeIf { it >= 1 } ?: 10)

  @PostMapping("/lyrics-search-timeout")
  @ResponseBody
  fun setLyricsSearchTimeout(@RequestParam("value") value: Int): Map<String, Any> {
      val validated = if (value >= 1) value else 10
      KaraokeProperties.set("lyricsSearchTimeoutSeconds", validated)
      return mapOf("status" to "ok", "value" to validated)
  }
  ```
  ⚠️ **В `KaraokeProperties.kt:92` только 1-арг `getInt`** — fallback через `.takeIf { it >= 1 } ?: 10`. KDoc + reference spec FR-002/FR-008.

---

## Phase 3: Backend — авто-подбор (FR-009/FR-010, rev 3 NEW)

- [ ] T018 [P] ~~Modify `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SseNotificationService.kt`: добавить `"massSearchSummary"` в `addressedTypes`~~ — **ОТМЕНЕНО** (Кирилл Р-1 cycle 2). Тип `MASS_SEARCH_SUMMARY` — **enum** в `model/SseNotificationType.kt` (УЖЕ добавлен, broadcast по умолчанию — НЕ в `addressedTypes`). `SseNotificationService.kt` НЕ модифицируется.

- [ ] T019 Modify `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`: после **обоих** циклов (T004 Path A + T006 Path B) добавить backend-лог + SSE notification (FR-010, design §2.1-2.2):
  ```kotlin
  val totalDurationMs = System.currentTimeMillis() - cycleStart
  println("[lyrics-search-summary] path=${"A" or "B"} count=$count successful=$successfulCount minIntervalMs=${minIntervalMs ?: "null"} totalDurationMs=$totalDurationMs")
  sseNotificationService.send(SseNotification(
      SseNotificationType.MASS_SEARCH_SUMMARY,
      payload = mapOf(
          "path" to ("A" or "B"),
          "count" to $count,
          "successfulCount" to $successfulCount,
          "minIntervalMs" to minIntervalMs,
          "totalDurationMs" to $totalDurationMs
      )
  ))
  ```
  ⚠️ **Path A** vs **Path B** — `println` строка отличается префиксом `path=A` или `path=B`. SSE payload аналогично.
  ⚠️ Если `successfulCount < 2` → `minIntervalMs = null` (нет пары успешных). В JSON → `null`. В println → `"null"`.

---

## Phase 4: Frontend (webvue3)

- [ ] T008 [P] ~~[US1]~~ **УДАЛЁН в rev 3** ~~Создать `SearchTimeoutDialog.vue`.~~ Вместо этого — timeout поле в существующих `<custom-confirm>` (см. T012, T013). Принцип «сделай так же, как рядом» (Кирилл Р-1 владельца).

- [ ] T009 [P] Modify `webvue3/src/components/Songs/store.js`: добавить actions (Алина rev 2, design §3.1):
  ```javascript
  getLyricsSearchTimeout(ctx) {
    return promisedXMLHttpRequest({ method: 'GET', url: '/api/lyrics-search-timeout' })
      .then((r) => {
        const parsed = JSON.parse(r) // promisedXMLHttpRequest возвращает raw string (utils.js:27)
        const n = parseInt(parsed.value, 10)
        return Number.isInteger(n) && n >= 1 ? n : 10
      })
      .catch(() => 10) // fallback на 10 при ошибке (FR-002)
  },
  setLyricsSearchTimeout(ctx, value) {
    return promisedXMLHttpRequest({ method: 'POST', url: '/api/lyrics-search-timeout', params: { value } })
  },
  ```
  ⚠️ **`promisedXMLHttpRequest` резолвит raw string, НЕ объект** (Кирилл Р-1 rev 2). `JSON.parse(r)` обязателен перед `.value`. Default 10 через `.catch(() => 10)`.

- [ ] T010 [P] Modify `webvue3/src/components/Songs/store.js:2619-2630` (`searchTextForAll`): добавить `if (payload && payload.timeout) params.timeout = payload.timeout`. **НЕ** менять `searchTextForSong` (одна песня → нет паузы).

- [ ] T011 [P] Modify `webvue3/src/components/Songs/store.js:2325` (`createFromFolderPromise`): добавить `if (payload && payload.timeout) params.timeout = payload.timeout`.

- [ ] T012 [US1] Modify `webvue3/src/components/Songs/SongsTable.vue` метод `searchTextForAll` (стр. ~1264): **добавить поле `timeout`** в `customConfirmParams.fields` (рядом с `engine`). **НЕ создавать** `SearchTimeoutDialog.vue` (rev 3 — отменено). **Reference**: `design.md §4.2`. Код:
  ```javascript
  async searchTextForAll() {
    const defaultEngine = await this.$store.getters.getPropValue('lyricsSearchEngine')
    const defaultTimeout = await this.$store.dispatch('getLyricsSearchTimeout')
    this.customConfirmParams = {
      header: 'Подтвердите поиск текста',
      body: `Выбрано песен: <strong>${this.countRows}.</strong><br>Найти в Интернете тексты для всех песен, для которых ещё нет текстов? Ранее сохранённые результаты поиска (если есть) будут удалены.`,
      fields: [
        { fldName: 'engine', fldLabel: 'Движок поиска', fldIsSelect: true, fldOptions: ['YANDEX_SYNC', 'YANDEX_ASYNC', 'SEARXNG', 'FOURGET'], fldValue: defaultEngine || 'FOURGET' },
        { fldName: 'timeout', fldLabel: 'Таймаут (сек)', fldValue: defaultTimeout || 10 },
      ],
      callback: (ret) => {
        const timeout = Number(ret.timeout)
        if (!Number.isInteger(timeout) || timeout < 1) {
          this.customConfirmParams = { isAlert: true, alertType: 'warning', header: 'Ошибка ввода', body: 'Таймаут должен быть положительным целым числом ≥ 1.', timeout: 10 }
          this.isCustomConfirmVisible = true
          return
        }
        this.$store.dispatch('setLyricsSearchTimeout', timeout)
        this.doSearchTextForAll(ret.engine, timeout)
      },
    }
    this.isCustomConfirmVisible = true
  }
  ```
  **Кнопка `@click="searchTextForAll"` остаётся как есть** (стр. 423). JSDoc обновлён (rev 3: timeout поле).

- [ ] T013 [US1] Modify `webvue3/src/views/HomeView.vue` метод `addFilesFromFolder` (стр. ~246): **добавить поле `timeout`** в `customConfirmParams.fields`. **НЕ создавать** `SearchTimeoutDialog.vue`. **Reference**: `design.md §4.3`. Код:
  ```javascript
  async addFilesFromFolder() {
    const defaultTimeout = await this.$store.dispatch('getLyricsSearchTimeout')
    this.customConfirmParams = {
      header: 'Добавление файлов из папки',
      body: `Добавить файлы из папки?<br>
             Файлы будут добавлены, если их ещё нет в базе данных<br>
             И имеют формат: <strong>YYYY (NN) [Автор] - Песня.flac</strong>`,
      fields: [
        { fldName: 'timeout', fldLabel: 'Таймаут (сек)', fldValue: defaultTimeout || 10 },
      ],
      timeout: 10,
      callback: (ret) => {
        const timeout = Number(ret.timeout)
        if (!Number.isInteger(timeout) || timeout < 1) {
          this.customConfirmParams = { isAlert: true, alertType: 'warning', header: 'Ошибка ввода', body: 'Таймаут должен быть положительным целым числом ≥ 1.', timeout: 10 }
          this.isCustomConfirmVisible = true
          return
        }
        this.$store.dispatch('setLyricsSearchTimeout', timeout)
        this.doAddFilesFromFolder(timeout)
      },
    }
    this.isCustomConfirmVisible = true
  }
  ```
  **Кнопка `@click="addFilesFromFolder"` остаётся как есть** (стр. 32). JSDoc обновлён (rev 3: timeout поле).

---

## Phase 5: Frontend — SSE нотификация (FR-010, rev 3 NEW, опционально)

- [ ] T020 [P] Modify `webvue3/src/App.vue` switch в обработчике SSE (опционально): добавить `case SseNotificationType.MASS_SEARCH_SUMMARY` — может быть просто `console.log(payload)` или показать toast. **Reference**: `design.md §9` (опциональная строка).

---

## Phase 6: Polish

- [ ] T014 [P] Boss self-verify (Lesson #16 iter #4): `grep -rn "lyricsSearchTimeoutSeconds" karaoke-app/src/main/kotlin/` — должно быть ≥5 вхождений: (1) `Karaoke.kt` getter + (2) `Karaoke.kt` setter, (3) `ApiController.kt:4771` `getSearchSongTextAll` (effectiveTimeout), (4) `ApiController.kt:5302` `doCreateFromFolder` (searchTimeout), (5) новый endpoint `getLyricsSearchTimeout`/`setLyricsSearchTimeout` + (6) лог + (7) SSE.
- [ ] T015 [P] Verify `SongEdit.vue` НЕ содержит `searchTextForAll` (grep = 0) — НЕ трогать (Кирилл Р-5).
- [ ] T016 5-step verification (канон `brief.md:59-63`) после ВСЕХ изменений:
  1. Backend compile: `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`
  2. Линтеры: `./gradlew :karaoke-web:ktlintCheck` + `cd webvue3 && npm run lint && cd ..`
  3. Backend bootJar: `./gradlew :karaoke-web:bootJar --parallel` (на nsa-i9 также `:karaoke-app:bootJar`)
  4. Frontend Vite: `cd webvue3 && npm run build && npm run format:check && cd ..`
  5. Docker: `cd deploy && bash do.sh build_webvue3` (+ `bash do.sh build_public` если менялся)
  - На nsa-i9 под nsa допустим 3-step subset (compile + lint + bootJar) для правок только в `karaoke-app`/`webvue3`. Шаги 2/4/5 для `karaoke-public` — no-op.
- [ ] T017 [P] Create `livedocs/features/316-search-timeout-configurable.md` (формат `NNN-name`, прецеденты 304/305/307/310/315, **НЕ** `docs/features/`). Добавить ссылку в `livedocs/INDEX.md`. Описать ОБЕ точки входа (searchsongtextall + createfromfolder) + KaraokeProperties + авто-подбор (FR-009/FR-010).

---

## Dependencies & Execution Order

- **Phase 1 (T001..T002)**: read references, no dependencies.
- **Phase 2 (T003..T007)**: backend modify. T003 (Karaoke.kt) — первый, требуется всем остальным. T004, T005, T006, T007 — параллельно после T003 (разные файлы).
- **Phase 3 (T018, T019)**: backend авто-подбор. T018 — параллельно. T019 — после T004/T006.
- **Phase 4 (T009..T013)**: frontend. T009 (store.js) — параллельно. T010, T011 — параллельно после T009. T012, T013 — параллельно после T009.
- **Phase 5 (T020)**: frontend SSE — опционально, после Phase 4.
- **Phase 6 (T014..T017)**: verifications + livedoc.

## MVP scope

Маленькая фича — единый US1 + FR-009/FR-010 (авто-подбор, rev 3). Batch mode (Lesson #15 iter #3 — MVP-checkpoint не нужен).

## Implementation Strategy

- Phase 1 + 2 + 3 + 4 параллельно (backend не зависит от frontend до Phase 6).
- Phase 6 — verifications после изменений.
- 0 коммитов до явного указания владельца.

## Notes

- **[P]** tasks = different files, no dependencies → parallelize.
- **[Story]** label только для US1 (одна US).
- Агенты НЕ перезапускают контейнер `karaoke-app`. Smoke-test только владелец.
- Verify quickstart.md 7 scenarios владелец после implement.
- 17 уроков iter #1-#4 + Lesson #16 iter #4 применены.

— Илья (boss)
