# Plan: 305-replace-systemerr-with-logger

**Spec**: [spec.md](./spec.md)
**OpenProject issue**: workflow-тест multi-agent команды speckit
**Branch**: `305-replace-systemerr-with-logger`
**Status**: Implemented (post-mortem plan)

> **Примечание**: спека 305 — узкая (1 файл, 1 замена) и была выбрана
> для отработки workflow multi-agent команды. План написан после выполнения,
> как retrospective + чеклист для будущих узких спек.

## Контекст

В `karaoke-app/src/main` оставался **ровно один** «голый» `System.err.println`
в production-коде. Все остальные 200+ логирований используют `kotlin.io.println`
(не структурные). Полная миграция println → logger — отдельная большая задача.
Спека 305 закрывает именно этот последний «голый» stderr.

## Scope (по spec.md)

- **FR-1**: убрать `System.err.println` из production.
- **FR-2**: добавить top-level `private val log = LoggerFactory.getLogger("UtilsPlaywright")`
  в стиле StatsResponseUtils/ProdContainerCheck.
- **FR-3**: замена на `log.error("...", e)` (сохранить message и объект исключения).
- **FR-4**: добавить `import org.slf4j.LoggerFactory`.
- **FR-5**: удалить `e.printStackTrace()`.
- **FR-6..FR-8**: build evidence — compileKotlin / ktlintCheck / bootJar — exit 0.

## Подход

### 1. Изменение кода
Минимальный diff в одном файле: добавление импорта, top-level logger, замена 2 строк в catch.

### 2. Build pipeline
Из DSH-sandbox обязательно:
- `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle` (workspace-write режим блокирует `~/.gradle/`)
- `DOCKER_CONFIG=/home/nsa/Karaoke/.docker` (если docker)

Команды:
```bash
cd /home/nsa/Karaoke
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:ktlintCheck
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:bootJar
```

### 3. Git workflow
- Ветка от master: `305-replace-systemerr-with-logger`
- Один commit, без WIP, без `--no-verify`
- Push → PR #414 → merge --merge (БЕЗ --delete-branch)

### 4. Multi-agent workflow
- Спека: Мирон (оркестратор)
- Имплементация: Софья (peer-mail name `sofya`)
- Ревью: Лука (peer-mail name `reviewer`)
- Протокол артефактов: `/home/nsa/Agents/shared/team-protocol.md`

## Что НЕ входит (Out of scope)

- `kotlin.io.println` (~200 вхождений в karaoke-app) — отдельная спека.
- Изменение `DualStream` или `KaraokeAppService` — не нужно для замены одного вызова.
- Полный аудит `karaoke-web/src/main` — там `System.err.println` не нашлось.
- LiveDocs (FR-014): спека не меняет BC и не поднимает C4 уровень.

## Риски

| Риск | Митигация |
|------|-----------|
| ktlint baseline-файл отсутствует в Karaoke → ktlintCheck может ломать на ровном месте | Запустить ktlintCheck ДО commit; если новые нарушения — записать в baseline (через format baseline) или поправить |
| `GRADLE_USER_HOME` не выставлен → read-only FS ошибка из DSH-sandbox | Обязательно прописывать в каждой gradle-команде |
| DSH-sessions с workspace-write не имеют rw в Karaoke (для агентов с cwd=Programmer/Boss/Reviewer) | Workaround: git clone в свой workspace, ИЛИ full-access от владельца |

## Тайминги (факт)

- Спека создана: ~08:25
- Письмо «305: implement» → Софья: 08:36
- Full-access выдан владельцем: ~09:30
- Имплементация + push: 12:36
- Ревью APPROVE: 12:43
- PR #414 открыт: 12:45
- PR #414 merged: ~12:53
- **Полный цикл**: ~4 ч 20 мин (включая rw-блокер и рестарт DSH)

## Lessons learned (для будущих узких спек)

1. **Перед стартом спеки** проверить rw у имплементатора в Karaoke (touch-test).
   Сэкономит ~1 ч на ожидании full-access.
2. **Ревьюер** — зарегистрировать в roster сразу после старта сессии (peer-mail
   не подхватывает автоматически для нового workspace).
3. **Naming**: ревьюер сам выбирает имя — это правильная практика, не
   дёргать DSH ради переименования.
4. **Self-referential sha256** в Sign-off отчёта создаёт drift — убрали из
   `reviews/000-template.md` (v0.2). sha256 живёт только в verdict-письме.
5. **Файлы спеки** должны быть в git-tracking — workflow требует коммитить
   spec.md/tasks.md в той же ветке (или отдельно), иначе спека остаётся
   только в working tree одного агента.

## Связанные артефакты

- Spec: [spec.md](./spec.md)
- Tasks: [tasks.md](./tasks.md)
- Report: [report.md](./report.md)
- Checklist: [checklists/requirements.md](./checklists/requirements.md)
- Review: `/home/nsa/Agents/Reviewer/reviews/305-replace-systemerr-with-logger-review.md`
- PR: https://github.com/svoemesto/Karaoke/pull/414
- Metrics: `~/.dsh/agent-mail/workflow-metrics.md`
