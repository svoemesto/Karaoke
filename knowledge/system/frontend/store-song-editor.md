# Vuex store: SongEditor

> **Домен**: system (frontend)
> **Компонента**: `webvue3/src/components/SongEditor/store.js` —
> задания редактора караоке-разметки.

## Файл

`webvue3/src/components/SongEditor/store.js`

## Назначение

Задания онлайн-редактора караоке-разметки (tbl_song_assignments +
tbl_song_assignment_drafts).

**`target`** (`'local'|'remote'`) — в запросах:
- **view** (digest/byId) — какую БД читать.
- **assign** — где создать (обычно **сервер**, если цикл
  назначение→работа→апрув идёт на проде).
- **approve** — читает draft оттуда, где реально идёт работа.
  `Song` пишет ВСЕГДА в LOCAL.
- **reject/delete** — обязательно с target (запись статуса).

## State

```javascript
state: {
    assignmentsDigest: [],                // дайджест заданий
    assignmentsIsLoading: false,          // флаг загрузки
    assignmentsTarget: 'local',           // текущий target
    editorSiteUsers: [],                 // пользователи-редакторы
    assignmentCurrent: undefined,        // текущее задание + draft
    defaultTarget: 'remote',             // default target (KaraokeProperty)
    assignmentStatusBySongId: {},        // songId -> {id, status, assigneeName}
    submittedAssignmentsCount: 0,       // бейдж в App.vue меню
}
```

## Hot paths

- **`/api/songassignment/list`** — каждое открытие вкладки.
- **`/api/songassignment/digest`** — дайджест (лёгкий запрос).
- **`/api/songassignment/{id}`** — детали задания.
- **`/api/songassignment/assign`** — назначить редактора.
- **`/api/songassignment/{id}/approve`** — одобрить.
- **`/api/songassignment/{id}/reject`** — отклонить.

## Известные TODO

- [ ] **Все actions/mutations** — детальный contract.
- [ ] **`submittedAssignmentsCount`** — где обновляется (cron? poll?).
- [ ] **Default target** — как определяется из `KaraokeProperty`.

## Связь

- **Editorial** ([editorial domain](../../domains/editorial/domain.md)) —
  `SongAssignment`/`SongAssignmentDraft`.
- **Monitor** — `SubmittedAssignmentsCheck` (см.
  [monitor-checks.md](../../domains/monitoring/components/monitor-checks.md)).

## Changelog

- **Pass 371** (2026-09-09): Initial. Автор: agent (Karaoke).