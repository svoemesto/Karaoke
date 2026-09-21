# Report: OpenProject #122 — «Реализовать фикс всплытия заданий в пуле HealthReport»

> **Статус**: ✅ done, merged в master через PR #492
> **PR**: <https://github.com/svoemesto/Karaoke/pull/492>
> **Ветка**: `395-hrqueue-floater` (заархивирована после merge)
> **Карта**: child of wayfinder-map #119 (specs/118)

## TL;DR

Реализован фикс «всплытия» HR-заданий при возврате на ранее посещённую страницу
в админке компонента «Песни». Точка слома — на фронте (`SongsTable.vue:1069`
и `:1901` сбрасывали `hrQueue`). Применена LIFO-семантика с механизмом
«splice + unshift» для всплытия вытесненных заданий.

## Что сделано

### 1. `webvue3/src/components/Songs/SongsTable.vue:654-657`

Добавлено новое поле `data()`:

```js
hrQueue: [],
// specs/118 #122: привязка songId → pageId для «всплытия» при возврате.
hrSongPages: new Map(),
hrRunning: 0,
HR_MAX_CONCURRENT: 3,
```

### 2. `_enqueueHrRequest(songId, pageId)` — новая сигнатура

Было:
```js
_enqueueHrRequest(songId) {
  this.hrQueue.push(songId)  // FIFO
  this._processHrQueue()
}
```

Стало:
```js
_enqueueHrRequest(songId, pageId) {
  // Если songId уже в очереди (вытеснен в конец), удаляем его — затем
  // добавляем в начало. Это и есть «всплытие» задания при возврате
  // на ранее посещённую страницу.
  const existingIdx = this.hrQueue.indexOf(songId)
  if (existingIdx >= 0) {
    this.hrQueue.splice(existingIdx, 1)
  }
  this.hrSongPages.set(songId, pageId)
  // unshift = LIFO: новейшие/всплывшие — первые на обработку.
  this.hrQueue.unshift(songId)
  this._processHrQueue()
}
```

### 3. `updateHealthReportForCurrentPage()` — передача pageId

```diff
- this._enqueueHrRequest(songId)
+ this._enqueueHrRequest(songId, this.currentPage)
```

### 4. `watch.currentPage` — без сброса hrQueue

```diff
currentPage: {
  handler(newPage) {
    this.$store.commit('setSongsTableCurrentPage', newPage)
-   this.hrQueue = []
    this.updateHealthReportForCurrentPage()
    this.reloadAssignmentStatus()
  },
},
```

### 5. `_processHrQueue()` — очистка Map после обработки

```diff
this.$store.dispatch('setCurrentSongHealthReports', id).finally(() => {
  this.hrRunning--
+ // // specs/118 #122: очищаем hrSongPages для обработанной песни —
+ // иначе Map растёт без ограничений за долгую сессию.
+ this.hrSongPages.delete(id)
  this._processHrQueue()
})
```

### 6. `editSong(id)` — без сброса hrQueue

```diff
async editSong(id) {
- this.hrQueue = []
  await this.$store.dispatch('setCurrentSongId', id)
  this.isSongEditVisible = true
  this.updateHealthReportForCurrentPage()
},
```

## Семантика (по grilling владельца)

| Сценарий | Поведение |
|----------|-----------|
| Переход вперёд (стр. 1 → стр. 2) | Новые песни стр. 2 добавляются в **начало** hrQueue. Песни стр. 1, если ещё не обработаны, остаются в конце и обрабатываются позже. |
| Возврат (стр. 2 → стр. 1) | Песни стр. 1, если они уже в hrQueue (вытеснены в конец), **всплывают** в начало через `splice + unshift`. Песни стр. 1, которых ещё нет в очереди, добавляются в начало. |
| Открытие/закрытие редактора (`editSong`) | hrQueue **не сбрасывается**. После закрытия `updateHealthReportForCurrentPage` перевычисляет — для уже-в-очереди песен они всплывают. |
| Долгая сессия (1000+ HR-запросов) | `hrSongPages` автоматически очищается в `finally()` после каждой обработки — нет утечки памяти. |

## Решения по grilling (владелец)

- **Семантика всплытия**: LIFO + всплытие при возврате на страницу.
- **editSong**: тоже перевычислять (без `hrQueue = []`).
- **Без keep-alive**: текущее поведение SongsTable не меняется.
- **Без регресс-тестов**: проект webvue3 исторически не покрыт
  тестами для SongsTable.vue.

## Проверки (kara-post-edit Pass 239+245)

| Шаг | Команда | Результат |
|-----|---------|-----------|
| Lint | `cd webvue3 && npm run lint` | ✅ no errors |
| Build | `cd webvue3 && npm run build` | ✅ built in 7.45s |
| Format | `cd webvue3 && npm run format:check` | ✅ (только baseline-warnings в sockjs-client) |

## CI (PR #492)

12/12 checks PASS:
- ✅ Baseline stats
- ✅ Docs (structure + offline links)
- ✅ ESLint + Prettier (karaoke-public)
- ✅ ESLint + Prettier (webvue3)
- ✅ JSDoc coverage
- ✅ KDoc coverage
- ✅ Knowledge SSoT impact
- ✅ Knowledge SSoT structure
- ✅ docker-image-tags guard
- ✅ ktlint (Kotlin/Java)
- ✅ no-jpa-imports guard
- ✅ no-mp4-mentions guard

## Файлы изменены

```
webvue3/src/components/Songs/SongsTable.vue | 34 ++++++++++++++++++++++++-----
1 file changed, 29 insertions(+), 5 deletions(-)
```

1 коммит:
- `SongsTable.vue: LIFO hrQueue + всплытие при возврате на страницу (specs/118 #122)`

## Разблокировано

Ничего (это финальный task-тикет по фиксу всплытия). Карта #119 после
этого имеет ещё 1 task-тикет — **#123** (UI-бейдж синего цвета), который
разблокирован после #125 (backend уже merged).

## Открытые вопросы / Out of scope

- (Нет) — тикет полностью разрешён.

## Связанные документы

- Карта #119: <http://localhost:8080/work_packages/119>
- Исходная задача #118: <http://localhost:8080/work_packages/118>
- Q1 research: `specs/118-health-report-pool/q1-answer.md`
- Q2 research: `specs/118-health-report-pool/q2-answer.md`
- #125 report: `specs/118-health-report-pool/q5-report.md`

---

**Готов к review владельца**. После одобрения и merge (уже выполнено):
work_package #122 → close.