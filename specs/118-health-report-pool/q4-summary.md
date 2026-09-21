## Wayfinder-цикл по задаче #118 — завершён

**Карта**: <http://localhost:8080/work_packages/119> (closed)
**Исходная задача**: <http://localhost:8080/work_packages/118>

### Effort

Создана карта `wayfinder:map` (#119), 5 child-тикетов:
- **#120** (research) → subagent 2c533823 → пул HealthReport
- **#121** (research) → subagent 91e8d217 → UI бейдж KaraokeProcess
- **#125** (task, backend) → PR #491 — `countwaiting?threadId=1`
- **#122** (task, фронт) → PR #492 — LIFO hrQueue + всплытие при возврате
- **#123** (task, UI-бейдж) → PR #493 — синий бейдж в правом верхнем углу + live SSE `PROCESS_LANE_COUNT_WAITING`

Все 5 child-тикетов closed. Карта #119 closed.

### 3 коммита merged в master

| PR | Scope | Files |
|----|-------|-------|
| #491 | Backend: `getCountWaiting(threadId)` фильтр | 3 |
| #492 | Frontend: LIFO + всплытие в `SongsTable.vue` | 1 |
| #493 | Backend SSE + Frontend бейдж | 7 |

CI 12/12 PASS на каждом PR.

### Что решалось

1. **Фикс «всплытия»** — на фронте (`SongsTable.vue` сбрасывал `hrQueue` при
   смене страницы / открытии редактора). Решено: убран сброс, добавлен
   `hrSongPages: Map<songId, pageId>`, механизм `splice + unshift` для
   всплытия вытесненных заданий. Backend-cascade HR не трогался.

2. **UI-бейдж размера пула HR** — синий (`var(--bs-primary, #0d6efd)`),
   правый верхний угол кнопки Старт/Стоп, рядом с серым бейджем
   KaraokeProcess (правый нижний). Live SSE через новое событие
   `PROCESS_LANE_COUNT_WAITING` с дедупликацией per-lane.

### Решения по grilling (владелец)

- Семантика всплытия: LIFO + всплытие при возврате на посещённую страницу.
- `editSong` тоже перевычисляет (без `hrQueue = []`).
- Без keep-alive: текущее поведение SongsTable сохраняется.
- Без регресс-тестов: webvue3 исторически без тестов для SongsTable.
- Бейдж — в `ProcessWorker.vue` (не в SongsTable) с live SSE (не initial poll).

### Документы

Все артефакты в `specs/118-health-report-pool/`:
- `map.md` — каноническая карта
- `q1-answer.md`, `q2-answer.md` — research findings (36 KB + 27 KB)
- `q3-report.md`, `q4-report.md`, `q5-report.md` — отчёты по тикетам

Подробные отчёты по каждому тикету опубликованы как комментарии
в OpenProject #122, #123, #125.

### Что осталось владельцу

**Закрыть #118** через `tools/tracker.sh close-issue 118` после ручного ревью.
Все технические тикеты (#122, #123, #125) уже закрыты.