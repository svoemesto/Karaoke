# Phase 0 — Research: выбор статуса песни при апруве задания (5 или 6)

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-08-13

Все «NEEDS CLARIFICATION» из Technical Context закрыты сверкой с живым кодом
(codegraph + чтение исходников). Ниже — решения (D-1..D-8), каждое с ссылкой
на конкретное место в коде, которое его подтверждает.

---

## D-1. Как передавать выбранный статус: query-параметр `idStatus` в существующий `/approve`

**Decision**: расширить существующий `POST /api/songeditor/approve` необязательным
`@RequestParam(required = false) idStatus: Int?`. Отдельный эндпоинт (`/approve-as-5`)
не вводить.

**Rationale**:
- Все остальные ручки контроллера (`reject`, `delete`, `revoke`, `delete-approved`)
  используют ровно этот паттерн — `@RequestParam` + `required = false`
  (`SongEditorController.kt:494-523`, `558-565`). Никаких `@RequestBody`/DTO в этом
  контроллере нет вообще, вводить их ради одного Int — против стиля модуля.
- `required = false` + `Int?` даёт бесплатную обратную совместимость: старый клиент
  (не обновлённый `webvue3`) не передаёт параметр → `null` → дефолт 6 → поведение
  идентично сегодняшнему (SC-003).
- Spring сам парсит `?idStatus=5` из `application/x-www-form-urlencoded`, которым
  уже ходит `promisedXMLHttpRequest` (`webvue3/src/components/SongEditor/store.js:201-205`).
  Изменений в SecurityConfig/CSRF не требуется — `webvue3` работает в `permitAll()`
  (constitution V).

**Alternatives considered**:
- *Отдельный эндпоинт `/approve-as-5`* — дублирование ~170 строк логики апрува или
  вынос в общий private-метод. Дороже, чем один параметр, и фронт получает две
  ветки вызова вместо одной.
- *`@RequestBody ApproveRequest(id, target, idStatus)`* — ломает существующих
  потребителей (`id`/`target` сейчас form-параметры) и вводит DTO там, где их нет.

---

## D-2. Гейт рендера/sync — по ФАКТИЧЕСКОМУ статусу песни, а не по запрошенному

**Decision**: `triggerRenderMp4DemoIfNeeded` и fire-and-forget `thread { sync related }`
вызывать при условии `song.idStatus >= 6L`, вычисленном ПОСЛЕ применения статуса,
а не при `requested == 6`.

**Rationale** (ключевое место — `SongEditorController.kt:376-383` и `422-456`):
сегодня оба вызова стоят **вне** `if (song.idStatus < 6)`. Значит для песни,
которая УЖЕ была в 6 до апрува, рендер и sync вызываются. Если гейтить по
запрошенному значению, появится регрессия в единственном неочевидном кейсе
(`requested = 5`, `current = 6`): песня по факту финальна, а конвейер не
запустится. Гейт по фактическому значению даёт полную таблицу без исключений:

| requested | current | итог `id_status` | render + sync related | song push |
|---|---|---|---|---|
| — (null) | 4 | 6 | ✅ (как сегодня) | ✅ |
| 6 | 4 | 6 | ✅ | ✅ |
| 5 | 4 | 5 | ⛔ (цель фичи) | ✅ |
| 5 | 6 | 6 (downgrade игнорируется) | ✅ (как сегодня) | ✅ |
| — (null) | 6 | 6 | ✅ (как сегодня) | ✅ |

**Alternatives considered**: гейт по `requested` — проще на вид, но вводит
регрессию в 4-й строке таблицы и требует отдельной оговорки в контракте.

---

## D-3. Push самой песни (`updateRemoteSongFromLocalDatabase`) НЕ гейтится

**Decision**: блок `if (Karaoke.allowUpdateRemote) { updateRemoteSongFromLocalDatabase(song.id) }`
(`SongEditorController.kt:395-420`) выполняется всегда, при любом выбранном статусе.

**Rationale**:
- Смысл апрува — «принять работу редактора». Разметка (маркеры/текст/`.srt`) должна
  попасть на PROD, иначе редактор сдал работу «в никуда», и админу придётся
  вручную нажимать «Обновить на сервере» в `SongEdit`.
- Пушить `id_status = 5` на PROD безопасно, это проверено тремя независимыми
  гейтами в коде:
  1. `PublicPlayerController.stemsReady` / `Song.isContentReady` требуют
     `idStatus >= 6` (`Song.kt:1132-1139`) — песня не станет доступна в плеере.
  2. `Song.markNewsAvailableIfReady` требует `idStatus == 6L`
     (`Song.kt:5126-5136`) → флаг `newsAvailableAnnounced` не выставится.
  3. На сервере `SongReleaseAnnouncementService.detectAndAnnounceAvailability`
     первым делом проверяет `if (!song.newsAvailableAnnounced) return false`
     (`SongReleaseAnnouncementService.kt:98`) → новость «в коллекции» не создастся.

**Alternatives considered**: гейтить push вместе с рендером — оставляет одобренную
разметку только в LOCAL, что противоречит назначению апрува и создаёт «висящую»
работу редактора.

---

## D-4. Новости при статусе 5 — дополнительной защиты НЕ требуется

**Decision**: код анонсов не трогаем; в лог добавляем только явную строку
`news SKIPPED ... reason=idStatus=5` для observability (FR-005).

**Rationale**: обе ветки анонса уже гейтятся по 6:
- «доступна / в коллекции» — цепочка `markNewsAvailableIfReady` (`idStatus == 6L`)
  → `newsAvailableAnnounced` → `detectAndAnnounceAvailability`.
- «в эфире» — `SongReleaseAnnouncementScheduler` работает по
  `Song.isPubliclyWatchable` = `isContentReady && onAir`, а `isContentReady`
  требует `idStatus >= 6` (`Song.kt:1132-1145`).

Побочно подтверждено ещё одно полезное свойство: `crossedReadyThreshold` в
`Song.saveToDb()` (`Song.kt:5327`) — `savedSong.idStatus < 6L && this.idStatus >= 6L`
— при переходе 4→5 не срабатывает, т.е. пересчёт persistent player-readiness
флагов тоже корректно не запускается. Ничего доделывать не нужно.

**Alternatives considered**: добавить явный `if (idStatus == 5) skipNews()` —
мёртвый код, дублирующий уже существующий инвариант.

---

## D-5. Источник текущего статуса песни для UI — новое поле в ответе `/byId`

**Decision**: добавить `"idStatus" to (s?.idStatus ?: 0L)` в map ответа
`SongEditorController.byId` (`SongEditorController.kt:278-296`).

**Rationale**: сверка кода показала, что в ответе `/byId` статуса ПЕСНИ нет вообще
— есть `songId`, `songName`, `author`, `album`, `year` и `status`, где `status` —
это статус ЗАДАНИЯ (`SongAssignmentStatus.resolve`, строка 288), а не `id_status`
песни. Песня в этом методе уже загружена (`val s = Song.loadFromDbById(...)`,
строка 276), поэтому поле бесплатное — ни одного дополнительного SQL-запроса.
Изменение аддитивное: `ReviewModal` — единственный потребитель этих полей, а
`store.js:117-121` просто `JSON.parse` и кладёт объект в Vuex как есть.

**Alternatives considered**:
- *Отдельный запрос `/api/songs/{id}` из модалки* — лишний round-trip и N+1 при
  открытии модалки, при том что песня уже в руках у `byId`.
- *Взять статус из строки таблицы (`digest`)* — `ReviewModal` открывается из трёх
  разных мест (`SongEditorTable`, `SongsTable`, `SongEdit`), у которых разные
  источники данных; поле в `/byId` покрывает все три одинаково.

---

## D-6. Vuex-action `approveAssignment` — dual-format payload (Number | Object)

**Decision**: `approveAssignment(ctx, payload)` принимает и голый `id` (Number),
и `{ id, idStatus }`. Ровно тот же приём уже применён в этом же файле для
`loadAssignmentById` (`store.js:106-122`: `const isObj = payload !== null && typeof payload === 'object'`).

**Rationale**: следуем существующему паттерну модуля (constitution V — стиль
`webvue3`); гарантируем, что любой не найденный/будущий вызов с голым `id`
продолжит работать. Единственный существующий вызов — `ReviewModal.vue:285`.

**Alternatives considered**: жёстко перейти на объект — сломает любой вызов,
который мы не нашли (в т.ч. в ветках/локальных правках других разработчиков).

---

## D-7. UI-контрол — radio-group внутри `se-meta`-блока модалки, без новых зависимостей

**Decision**: нативные `<input type="radio">` + существующие CSS-классы модалки
(`se-field`, `se-meta`, `se-badge`, `se-remote-note` для баннера-подсказки).
Bootstrap-vue-next компоненты (`BFormRadioGroup`) не вводить.

**Rationale**: `ReviewModal.vue` — самодостаточная модалка со `<style scoped>` и
собственной дизайн-системой `se-*` (599 строк, ни одного импорта из
`bootstrap-vue-next`, в отличие от `SongEditorTable.vue`, который импортирует
`BTable`/`BSpinner`). Вставлять сюда `BFormRadioGroup` — смешение двух стилей в
одном файле. Для информационного бейджа статуса в `.se-meta` (Pass 51-3.1: бейдж виден ВСЕГДА, не блокирует radio) переиспользуем `se-badge` — он уже
используется для статуса задания (строка 19).

**Ловушка, учтённая в плане**: модалка живёт под `v-if` у вызывающих компонентов,
но `a` — это Vuex-getter (`getAssignmentCurrent`), а не prop. Если модалку
переоткрыть для ДРУГОГО задания без размонтирования (возможно в `SongsTable`),
`selectedIdStatus` останется от предыдущей песни. Поэтому нужен `watch: { a() {...} }`,
сбрасывающий выбор в 6.

**Alternatives considered**: `<select>` вместо radio — по стилю проекта `<select>`
требует `form-select` (constitution V), но в `se-*`-дизайне модалки Bootstrap-классов
нет; два radio наглядны и не требуют клика для просмотра вариантов.

---

## D-8. Никаких изменений схемы БД, миграций и sync-регистрации

**Decision**: фича полностью укладывается в существующую схему.

**Rationale**:
- Новых колонок нет → `recordhash`-триггеры пересоздавать не нужно
  (constitution III применяется только при изменении колонок).
- `tbl_songs.id_status` уже участвует в sync и уже покрыт `recordhash`.
- `tbl_song_assignments` не меняется вообще (в т.ч. `admin_status` — только
  `approved`, как сейчас).
- Никаких новых boolean-полей в DTO → ловушка Jackson `is`-префикса
  (AGENTS.md Q&A) не применима; `idStatus` — Int/Long, имя не начинается с `is`.

**Alternatives considered**: хранить «выбранный при апруве статус» в отдельной
колонке `tbl_song_assignments.approved_id_status` для истории — за пределами
спеки (Assumptions: «никаких дополнительных таблиц аудита»), лог достаточен.

---

## Проверенные инварианты (сводка)

| Инвариант | Где подтверждён | Значение для фичи |
|---|---|---|
| Хардкод `"6"` при апруве | `SongEditorController.kt:380-383` | единственное место правки статуса |
| `render` + `sync related` стоят ВНЕ `if (idStatus < 6)` | `SongEditorController.kt:427`, `435` | обязателен гейт по факту (D-2) |
| `/byId` не отдаёт `id_status` песни | `SongEditorController.kt:278-296` | нужен FR-011 (D-5) |
| Идемпотентность апрува | `SongEditorController.kt:333-335` | срабатывает ДО логики статуса, не ломается |
| Ответ уже содержит `idStatus` | `SongEditorController.kt:477` | FR-012 — фиксация инварианта, не новое поле |
| Новость «в коллекции» требует `idStatus == 6` | `Song.kt:5126-5136` + `SongReleaseAnnouncementService.kt:98` | FR-005 без кода |
| Новость «в эфире» требует `idStatus >= 6` | `Song.kt:1132-1145` | FR-005 без кода |
| Пересчёт player-readiness требует перехода `<6 → >=6` | `Song.kt:5327` | 4→5 корректно не триггерит |
| Единственный вызов `approveAssignment` | `ReviewModal.vue:285` | dual-format — страховка (D-6) |
| Единственный вызов `/byId` | `store.js:115` | аддитивное поле безопасно (D-5) |
