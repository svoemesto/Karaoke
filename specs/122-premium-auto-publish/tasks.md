# Tasks: Премиум-автопубликация в Telegram и ВК при появлении песни в коллекции

**Input**: Design documents from `/specs/122-premium-auto-publish/`

**Prerequisites**: [plan.md](./plan.md) (required), [spec.md](./spec.md) (required for user stories), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/internal-api.md), [quickstart.md](./quickstart.md)

**Tests**: Тесты в CI не предусмотрены (см. `constitution.md` «Тесты»: существующие интеграционные тесты `@Disabled`, требуют сеть/credentials). Проверка — вручную через сценарии [quickstart.md](./quickstart.md). Тест-задачи НЕ генерируются.

**Organization**: Задачи сгруппированы по user story (из spec.md), каждая story — независимо реализуема и тестируема.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Можно параллелить (разные файлы, нет зависимости от незавершённых задач)
- **[Story]**: Принадлежность к user story (US1, US2, US3)
- Все пути — абсолютные от корня репозитория

## Path Conventions

- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`
- Frontend: `webvue3/src/components/Songs/`
- Per-feature документ: `docs/features/premium-auto-publish.md`

## Технический контекст (из plan.md)

- **Не создаём новых классов/сервисов** — изменяются существующие
  `PremiumAutoPublishScheduler.kt`, `Song.kt`, `SongEdit.vue`, `Songs/store.js`.
  `TelegramAutoPublishService.kt`/`VkAutoPublishService.kt`/`ApiController.kt`
  — БЕЗ изменений (их публичный API уже поддерживает `PublicationType.PREMIUM`
  и `persistMessageId=false`/`persistPostId=false`).
- **Без новых таблиц/колонок/recordhash-триггеров** — новые поля
  (`premiumAttemptCountTelegram`/`premiumAttemptCountVk`) живут в уже
  существующем JSON-блобе `tbl_songs.player_readiness_flags`.
- Root cause (research.md R1): завершение отложенного Telegram/ВК-премиум
  рендера сегодня зависит от AIR-планировщиков
  (`TelegramAutoPublishScheduler`/`VkAutoPublishScheduler`), гейтящихся
  чужими флагами (`telegramAutoPublishEnabled`/`vkAutoPublishEnabled`) —
  фикс перемещает эту логику в сам `PremiumAutoPublishScheduler`.

---

## Phase 1: Setup

**Purpose**: Backfill-документация фичи, которая уже смёржена в код, но
никогда не имела ни спеки, ни per-feature документа.

- [X] T001 Создать `docs/features/premium-auto-publish.md` по структуре
  `docs/features/telegram-auto-publish.md`/`docs/features/vk-news-auto-publish.md`
  (секции: «Что делает», «Зачем», «Как работает» — триггер
  `newsAvailableAnnounced` false→true в `Song.markNewsAvailableIfReady()`
  (`karaoke-app/.../model/Song.kt:5088`), тик `PremiumAutoPublishScheduler`
  каждые 30 сек, идемпотентность по `newsPremiumTelegramSent`/`newsPremiumVkSent`
  (без сохранения id — слот `idTelegramDemo`/`idVk` зарезервирован для
  будущей AIR-публикации), «Инварианты / правила» (ВК-премиум сегодня
  текстовый — community-токен без прав `video.save`; Telegram-премиум
  всегда с видео), «Известные ловушки» (общий счётчик попыток на два
  канала — до фикса этой фичи, см. FR-010; зависимость завершения
  рендера от чужого scheduler'а — до фикса FR-003), «Ссылки» на
  `specs/122-premium-auto-publish`, `specs/113-telegram-demo-publish`,
  `specs/121-vk-news-auto-publish`. Добавить строку в таблицу
  `docs/features/README.md`.

**Checkpoint**: Per-feature документ существует (ссылки `@see
docs/features/premium-auto-publish.md` в коде наконец указывают на
реальный файл).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Новые поля на `Song`, от которых зависят и US1 (корректность
`FAILED`), и US2 (отображение статуса), и US3 (когда показывать
«Повторить»).

**⚠️ CRITICAL**: US1/US2/US3 читают/пишут эти поля.

- [X] T002 [P] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`
  рядом с существующим `premiumAttemptCount` (~строка 1067-1069) добавить
  два новых `var`-свойства по тому же паттерну
  (`readinessStringFlag`/`setReadinessStringFlag`, int-as-string):
  ```kotlin
  var premiumAttemptCountTelegram: Int
      get() = readinessStringFlag("premiumAttemptCountTelegram").toIntOrNull() ?: 0
      set(value) = setReadinessStringFlag("premiumAttemptCountTelegram", value.toString())

  var premiumAttemptCountVk: Int
      get() = readinessStringFlag("premiumAttemptCountVk").toIntOrNull() ?: 0
      set(value) = setReadinessStringFlag("premiumAttemptCountVk", value.toString())
  ```
  KDoc: пояснить, что эти поля заменяют общий `premiumAttemptCount` для
  новой (раздельной по каналам) логики `PremiumAutoPublishScheduler`
  (FR-010 spec.md, data-model.md); старое поле `premiumAttemptCount`
  помечается в своём существующем KDoc как deprecated для новой логики
  (не удалять — старые записи/логи могут его читать). `@see
  docs/features/premium-auto-publish.md`.

**Checkpoint**: Новые поля доступны через `Song`-модель (тот же JSON-блоб,
без миграции) — US1/US2/US3 могут начинаться.

---

## Phase 3: User Story 1 - Telegram-премиум публикуется автоматически и надёжно (Priority: P1) 🎯 MVP

**Goal**: Отложенная Telegram-премиум-публикация (когда демо-MP4 нужно
отрендерить) надёжно завершается независимо от `telegramAutoPublishEnabled`;
раздельные счётчики попыток не позволяют одному каналу преждевременно
блокировать общий статус задачи.

**Independent Test**: quickstart.md Сценарий 1 (`telegramAutoPublishEnabled=false`,
`premiumAutoPublishEnabled=true`, песня без демо-MP4 → пост в Telegram
появляется после рендера) и Сценарий 4 (раздельные счётчики).

### Implementation for User Story 1

- [X] T003 [US1] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/PremiumAutoPublishScheduler.kt`
  изменить сигнатуру `private fun handleFailure(song: Song, error: String)`
  на `private fun handleFailure(song: Song, channel: String, error: String)`
  (`channel` — `"telegram"` или `"vk"`). Логика: инкрементировать
  `song.premiumAttemptCountTelegram` (если `channel=="telegram"`) или
  `song.premiumAttemptCountVk` (если `channel=="vk"") вместо общего
  `song.premiumAttemptCount`. `song.premiumAutoPublishLastError = error`
  остаётся общим (последняя ошибка любого канала, FR-007 spec.md).
  Обновить оба вызова в `processSong()` (`handleFailure(song, "telegram:
  ${tgResult.error}")` → `handleFailure(song, "telegram", tgResult.error ?:
  "")`; аналогично для `"vk: ${vkResult.error}"` → `handleFailure(reloaded,
  "vk", vkResult.error ?: "")`), убрав префикс канала из текста ошибки
  (он больше не нужен — канал теперь явный параметр).
- [X] T004 [US1] В том же файле переписать проверку «оба канала закрыты» —
  и в `handleFailure`, и в `closeIfBothChannelsDone(song: Song)`, и в
  финальной проверке `processSong()` — на канал-специфичное понятие
  «closed» (data-model.md): `tgClosed = song.newsPremiumTelegramSent ||
  song.idTelegramDemo.isNotEmpty() || song.premiumAttemptCountTelegram >=
  maxAttempts`, аналогично `vkClosed` для ВК (`song.premiumAttemptCountVk`).
  `maxAttempts` — тот же `KaraokeProperties.getLong("premiumAutoPublishMaxAttempts").let
  { if (it <= 0) 3L else it }`, уже используемый в `handleFailure`. Если
  `tgClosed && vkClosed`: `song.newsPremiumPublishPending = false`;
  `song.premiumAutoPublishState = if (!tgClosedBySuccess || !vkClosedBySuccess)
  "FAILED" else "COMPLETE"` (где `tgClosedBySuccess = newsPremiumTelegramSent
  || idTelegramDemo.isNotEmpty()`) — то есть `FAILED`, если хотя бы один
  канал закрылся через исчерпание попыток, а не через успех. Обновить
  `handleFailure`, чтобы он вызывал эту общую проверку после инкремента
  счётчика конкретного канала (сегодня `handleFailure` сам решает
  `newsPremiumPublishPending=false`+`state=FAILED` только по общему
  счётчику — заменить на вызов новой общей функции `closeIfBothChannelsDone`,
  которая теперь и решает FAILED/COMPLETE по обоим каналам).
- [X] T005 [US1] В том же файле добавить новую приватную фазу
  `resumeRenderingSongs()` (по образцу
  `TelegramAutoPublishScheduler.resumeRenderingSongs()`/`VkAutoPublishScheduler`-аналога),
  вызываемую БЕЗУСЛОВНО в начале `tick()` — **до** `publishPendingSongs()**
  и **не** гейтящуюся `telegramAutoPublishEnabled`/`vkAutoPublishEnabled`
  (только общим `premiumAutoPublishEnabled`, уже проверенным в начале
  `tick()`). Логика:
  ```kotlin
  private fun resumeRenderingSongs() {
      val ids = loadPremiumRenderingCandidateIds() // cheap SELECT, см. ниже
      for (songId in ids) {
          val song = Song.loadFromDbById(songId, WORKING_DATABASE, KSS_APP, SAC_APP) ?: continue
          if (!song.newsPremiumPublishPending) continue
          if (song.telegramAutoPublishState == "rendering" && !song.newsPremiumTelegramSent &&
              song.idTelegramDemo.isEmpty()) {
              val proc = findRenderDemoProcess(songId) // тот же паттерн, что TelegramAutoPublishScheduler
              if (proc != null && (proc.status == "DONE" || proc.status == "ERROR")) {
                  val result = TelegramAutoPublishService.onRenderCompleted(
                      songId, PublicationType.PREMIUM, persistMessageId = false,
                      success = proc.status == "DONE",
                      error = if (proc.status == "ERROR") "RENDER_MP4_DEMO failed" else null,
                  )
                  if (result?.state?.code == "send_failed") {
                      val reloaded = Song.loadFromDbById(songId, WORKING_DATABASE, KSS_APP, SAC_APP) ?: continue
                      handleFailure(reloaded, "telegram", result.error ?: "render/send failed")
                  }
              }
          }
          val reloadedForVk = Song.loadFromDbById(songId, WORKING_DATABASE, KSS_APP, SAC_APP) ?: continue
          if (!reloadedForVk.newsPremiumPublishPending) continue
          if (reloadedForVk.vkAutoPublishState == "rendering" && !reloadedForVk.newsPremiumVkSent &&
              reloadedForVk.idVk.isEmpty()) {
              val proc = findRenderDemoProcess(songId)
              if (proc != null && (proc.status == "DONE" || proc.status == "ERROR")) {
                  val result = VkAutoPublishService.onRenderCompleted(
                      songId, PublicationType.PREMIUM, persistPostId = false,
                      success = proc.status == "DONE",
                      error = if (proc.status == "ERROR") "RENDER_MP4_DEMO failed" else null,
                  )
                  if (result?.state?.code == "send_failed") {
                      val reloaded = Song.loadFromDbById(songId, WORKING_DATABASE, KSS_APP, SAC_APP) ?: continue
                      handleFailure(reloaded, "vk", result.error ?: "render/send failed")
                  }
              }
          }
      }
  }
  ```
  Добавить приватный helper `loadPremiumRenderingCandidateIds(): List<Long>`
  — cheap SELECT (без загрузки полного `Song`, по образцу
  `loadPendingIds()` уже в этом файле): `SELECT id, player_readiness_flags
  FROM tbl_songs WHERE player_readiness_flags LIKE
  '%newsPremiumPublishPending%' AND (player_readiness_flags LIKE
  '%"telegramAutoPublishState":"rendering"%' OR player_readiness_flags LIKE
  '%"vkAutoPublishState":"rendering"%')`, фильтровать в Kotlin по
  `"newsPremiumPublishPending":true`, аналогично уже существующей
  `loadPendingIds()`. Добавить приватный helper `findRenderDemoProcess(songId:
  Long): RenderProcessInfo?` — тот же SQL/паттерн, что
  `TelegramAutoPublishScheduler.findRenderDemoProcess` (SELECT
  `process_status, id` из `tbl_processes WHERE song_id=? AND
  process_type='RENDER_MP4_DEMO' ORDER BY id DESC LIMIT 1`), с локальным
  `data class RenderProcessInfo(val status: String, val id: Long)`.
- [X] T006 [US1] В `tick()` того же файла добавить вызов `resumeRenderingSongs()`
  **перед** `publishPendingSongs()`, внутри существующего `try { ... }
  catch (e: Exception) { e.printStackTrace() }` — так, чтобы сбой в одной
  фазе не блокировал другую. Функция вызывается только если
  `premiumAutoPublishEnabled=true` (проверка уже есть в начале `tick()`);
  **никаких** дополнительных проверок `telegramAutoPublishEnabled`/
  `vkAutoPublishEnabled` не добавлять (это и есть сам фикс FR-003
  spec.md).
- [X] T007 [US1] Обновить KDoc-комментарий в начале
  `PremiumAutoPublishScheduler.kt` (класс-документация, ~строки 14-33):
  описать новую фазу `resumeRenderingSongs()`, явно указать, что
  завершение отложенного рендера премиум-публикации в любом канале
  **не зависит** от `telegramAutoPublishEnabled`/`vkAutoPublishEnabled`
  (FR-003 spec.md), обновить описание идемпотентности с учётом раздельных
  счётчиков (`premiumAttemptCountTelegram`/`premiumAttemptCountVk` вместо
  общего `premiumAttemptCount`). Ссылка `@see
  docs/features/premium-auto-publish.md`.

**Checkpoint**: Telegram-премиум надёжно завершается независимо от
`telegramAutoPublishEnabled` (SC-004 spec.md); независимый сбой одного
канала не блокирует другой (SC-... / Edge Cases spec.md).

---

## Phase 4: User Story 2 - Администратор видит статус премиум-публикации в карточке песни (Priority: P1)

**Goal**: В `SongEdit.vue` видны два независимых статус-бейджа
(Telegram-премиум / ВК-премиум): Ожидает / Рендерится / Публикуется /
Опубликовано / Ошибка отправки.

**Independent Test**: quickstart.md Сценарий 2 — открыть карточку песни
во время рендера, увидеть «Рендерится», после завершения — «Опубликовано».

### Implementation for User Story 2

- [X] T008 [P] [US2] В `webvue3/src/components/Songs/edit/SongEdit.vue`,
  в секции `computed` рядом с существующим `telegramPublishState()`
  (~строка 2598), добавить `premiumTelegramPublishState()` — по тому же
  образцу (`{label, class, title}` или `null`), но на основе премиум-полей:
  ```js
  premiumTelegramPublishState() {
    if (!this.song) return null
    if (!this.song.newsPremiumPublishPending && !this.song.newsPremiumTelegramSent) return null
    if (this.song.newsPremiumTelegramSent) {
      return { label: 'Опубликовано', class: 'tg-publish-state-published', title: 'Премиум-пост отправлен' }
    }
    if (['rendering', 'publishing'].includes(this.song.telegramAutoPublishState)) {
      return {
        label: this.song.telegramAutoPublishState === 'rendering' ? 'Рендерится' : 'Публикуется',
        class: 'tg-publish-state-scheduled',
        title: 'Ожидает завершения рендера/отправки',
      }
    }
    const maxAttempts = this.premiumAutoPublishMaxAttempts || 3
    if ((this.song.premiumAttemptCountTelegram || 0) >= maxAttempts) {
      return {
        label: 'Ошибка отправки',
        class: 'tg-publish-state-late',
        title: this.song.premiumAutoPublishLastError || 'Все попытки исчерпаны',
      }
    }
    return { label: 'Ожидает', class: 'tg-publish-state-scheduled', title: 'В очереди премиум-тика' }
  }
  ```
  JSDoc-комментарий по образцу `telegramPublishState` (FR-006/FR-009
  spec.md), `@see docs/features/premium-auto-publish.md`.
- [X] T009 [P] [US2] Там же добавить симметричный `premiumVkPublishState()`
  для ВК (поля `newsPremiumVkSent`/`vkAutoPublishState`/
  `premiumAttemptCountVk`), тот же паттерн, что T008.
- [X] T010 [US2] В `<template>` того же файла, рядом с существующим
  блоком `v-if="!song.idTelegramDemo || (song.date && song.time)"`
  (badge `telegramPublishState`, ~строка 1571-1597) и рядом с
  аналогичным блоком для ВК (`idVk`/`vkPublishState`, если существует —
  проверить по образцу), добавить два новых `<span>`-бейджа (по образцу
  существующего `tg-publish-state-badge`, ~строка 1575-1581):
  `v-if="premiumTelegramPublishState"` и `v-if="premiumVkPublishState"`,
  каждый с `:class`/`:title`/`{{ ... .label }}`, подписанные «Премиум:»
  (например, `Премиум: {{ premiumTelegramPublishState.label }}`), чтобы
  визуально не путались с AIR-бейджами.
- [X] T011 [US2] Проверить, что CSS-классы `tg-publish-state-published`/
  `tg-publish-state-scheduled`/`tg-publish-state-late` (уже используемые
  `telegramPublishState`) визуально подходят и для премиум-бейджей; если
  для премиум нужен отдельный визуальный акцент (например, другой
  префикс/иконка) — добавить минимальные новые классы в `<style scoped>`
  того же файла, не дублируя существующие правила.
- [X] T012 [US2] Убедиться, что `premiumAutoPublishMaxAttempts` доступен
  в `computed`/`data` `SongEdit.vue` для порога в T008/T009 (проверить,
  загружены ли `KaraokeProperties` в `$store` для другого экрана —
  Vuex-модуль `Properties`; если да — прочитать оттуда через
  `mapGetters`/`this.$store.state...`; если нет — не грузить весь список
  Properties ради одного значения, захардкодить дефолт `3` с комментарием
  `// TODO: пробросить из KaraokeProperties, если появится способ дешевле полного списка`).

**Checkpoint**: Оба премиум-статуса видны в карточке песни, независимо от
US3 (кнопка «Повторить» появляется отдельной задачей).

---

## Phase 5: User Story 3 - Администратор может вручную перезапустить упавшую премиум-публикацию (Priority: P2)

**Goal**: Кнопка «Повторить» в карточке песни для канала со статусом
«Ошибка отправки», вызывающая уже существующие
`/api/song/publishPremiumTelegram`/`/api/song/publishPremiumVk`.

**Independent Test**: quickstart.md Сценарий 3 — довести канал до
`FAILED`, нажать «Повторить», убедиться в успехе.

### Implementation for User Story 3

- [X] T013 [P] [US3] В `webvue3/src/components/Songs/store.js`, рядом с
  существующим `publishToTelegramNowPromise` (~строка 2365), добавить
  два новых действия по тому же паттерну:
  ```js
  publishPremiumTelegramPromise(ctx) {
    let params = { id: ctx.state.currentSongId }
    let request = { method: 'POST', url: '/api/song/publishPremiumTelegram', params: params }
    return promisedXMLHttpRequest(request)
  },
  publishPremiumVkPromise(ctx) {
    let params = { id: ctx.state.currentSongId }
    let request = { method: 'POST', url: '/api/song/publishPremiumVk', params: params }
    return promisedXMLHttpRequest(request)
  },
  ```
  JSDoc со ссылкой на `contracts/internal-api.md` и `@see
  docs/features/premium-auto-publish.md`.
- [X] T014 [US3] В `webvue3/src/components/Songs/edit/SongEdit.vue`
  добавить `data()`-флаги `isPublishingPremiumTelegram: false`,
  `isPublishingPremiumVk: false` (рядом с `isPublishingTelegram`, ~строка
  2576) и два метода `publishPremiumTelegramNow()`/`publishPremiumVkNow()`
  в `methods`, по образцу `publishToTelegramNow()` (~строка 4057-4107):
  диспатчат `publishPremiumTelegramPromise`/`publishPremiumVkPromise`
  (T013), парсят JSON-ответ (`success`/`state`/`error`), показывают toast
  (переиспользовать `showTelegramToast`/аналог для ВК, если существует —
  проверить по образцу существующей кнопки `publishToVkNow`, иначе
  использовать тот же `showTelegramToast`).
- [X] T015 [US3] В `<template>`, внутри бейджей `premiumTelegramPublishState`/
  `premiumVkPublishState` (T010), добавить кнопку «Повторить» —
  `v-if="premiumTelegramPublishState && premiumTelegramPublishState.class
  === 'tg-publish-state-late'"`, `:disabled="isPublishingPremiumTelegram"`,
  `@click="publishPremiumTelegramNow"` (по образцу кнопки
  `publishToTelegramNow`, ~строка 1582-1596); симметрично для ВК.

**Checkpoint**: Все 3 user story независимо функциональны — фича
полностью закрывает баг-репорт спеки.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Финализация документации и проверка регрессий.

- [X] T016 [P] Дополнить `docs/features/premium-auto-publish.md` (T001)
  итоговым описанием после реализации: раздельные счётчики попыток,
  независимость завершения рендера от AIR-флагов, UI-статус в
  `SongEdit.vue`.
- [X] T017 Прогнать вручную все сценарии `quickstart.md` (1-5 +
  регрессия AIR) на admin-машине с тестовым Telegram-каналом/ВК-группой.
- [X] T018 [P] Прогнать чек-лист перед коммитом из `CLAUDE.md`:
  `./gradlew ktlintCheck`, `bash tools/check-kdoc-coverage.sh`,
  `cd webvue3 && npm run lint:check && npx prettier --check
  "src/**/*.{vue,js,ts,json}"`, `bash tools/check-jsdoc-coverage.sh webvue3`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей — можно начинать сразу
- **Foundational (Phase 2)**: без зависимостей от Setup, но БЛОКИРУЕТ
  все user stories (T003-T015 читают/пишут поля из T002)
- **User Stories (Phase 3-5)**: все зависят от завершения Phase 2
  - US1 (Phase 3) не зависит от US2/US3
  - US2 (Phase 4) не зависит от US1 по коду, но по смыслу бессмысленна
    без US1 (нечего показывать, если премиум не работает) — тем не менее
    независимо тестируема (можно смоделировать состояния вручную через БД)
  - US3 (Phase 5) зависит от UI-разметки US2 (T010) для размещения кнопки
    «Повторить» — по коду это единственная межстрочная зависимость
- **Polish (Phase 6)**: после всех желаемых user stories

### Parallel Opportunities

- T002 (Foundational) — единственная задача, самодостаточна
- T003 → T004 → T005 → T006 → T007 — последовательны (один файл,
  накапливающиеся изменения одного класса)
- T008 и T009 (US2, разные computed-функции в одном файле, но независимые
  друг от друга по содержанию) можно писать параллельно, затем — T010
  (объединяет оба в template)
- T013 (US3, `store.js`) параллелен с T008/T009 (US2, `SongEdit.vue` computed) —
  разные файлы
- T016 и T018 (Polish) — параллельны друг другу

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 (Setup) + Phase 2 (Foundational)
2. Phase 3 (US1) — Telegram-премиум надёжно завершается
3. **STOP and VALIDATE**: quickstart.md Сценарий 1 + Сценарий 4
4. Это уже закрывает баг-репорт спеки (видимость — отдельная ценность,
   но не блокирует сам факт публикации)

### Incremental Delivery

1. Setup + Foundational → готова база
2. US1 → надёжная публикация (MVP, закрывает баг-репорт)
3. US2 → администратор видит статус (устраняет «непрозрачность»,
   из-за которой баг-репорт вообще было трудно диагностировать)
4. US3 → ручное восстановление после сбоя
5. Polish → документация + регрессия
