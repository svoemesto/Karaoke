# Tasks: 305-replace-systemerr-with-logger

**Workflow-тест нового multi-agent цикла speckit.**

## Phase 0: Спека (оркестратор — Мирон)

- [ ] T0.1 Создать `specs/305-replace-systemerr-with-logger/spec.md` ✅
- [ ] T0.2 Создать `specs/305-replace-systemerr-with-logger/tasks.md` ✅
- [ ] T0.3 Отправить Софье письмо «305: implement» с приложенным `spec.md` (path + sha256)

## Phase 1: Имплементация (Софья)

- [ ] T1.1 Прочитать `spec.md`, ознакомиться с FR-1..FR-8.
- [ ] T1.2 Создать ветку `305-replace-systemerr-with-logger` от master.
- [ ] T1.3 Внести правки в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsPlaywright.kt`:
  - Добавить `import org.slf4j.LoggerFactory`
  - Добавить top-level `private val log = LoggerFactory.getLogger("UtilsPlaywright")`
  - Заменить `System.err.println("Ошибка при работе с браузером: ${e.message}")` + `e.printStackTrace()` на `log.error("Ошибка при работе с браузером: ${e.message}", e)`
- [ ] T1.4 Запустить из-под DSH:
      `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin`
      Ожидаемо: exit 0.
- [ ] T1.5 Запустить:
      `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:ktlintCheck`
      Ожидаемо: exit 0. Если нарушения — поправить или записать в baseline (через ktlint baseline format).
- [ ] T1.6 (опционально):
      `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:bootJar`
      Ожидаемо: exit 0.
- [ ] T1.7 `git add` + `git commit` с сообщением `fix(playwright): replace System.err.println with SLF4J logger.error (spec 305)`.
- [ ] T1.8 `git push -u origin 305-replace-systemerr-with-logger`.
- [ ] T1.9 Собрать артефакт-отчёт для ревьюера (см. шаблон в `spec.md` → «Артефакт для ревьюера»).
- [ ] T1.10 Отправить Мирону письмо «305: implemented» с приложенным артефактом (path + sha256).

## Phase 2: Review (ревьюер)

- [ ] T2.1 Прочитать `spec.md` и письмо от Мирона.
- [ ] T2.2 Прочитать `UtilsPlaywright.kt`, посчитать sha256, сравнить с заявленным имплементатором.
- [ ] T2.3 Проверить каждый FR-1..FR-8:
  - T2.3.1 FR-1: `grep -rn "System\.err\.print" /home/nsa/Karaoke/karaoke-app/src/main` → пусто.
  - T2.3.2 FR-2: логгер объявлен в стиле проекта.
  - T2.3.3 FR-3: замена корректна (есть message, есть объект `e` вторым аргументом).
  - T2.3.4 FR-4: импорт `org.slf4j.LoggerFactory` есть.
  - T2.3.5 FR-5: `e.printStackTrace()` удалён.
  - T2.3.6 FR-6: compileKotlin exit 0 в build evidence.
  - T2.3.7 FR-7: ktlintCheck exit 0 + нет новых нарушений baseline.
  - T2.3.8 FR-8: bootJar (если запускался) exit 0.
- [ ] T2.4 Регрессионная проверка: `grep -n "runAuthFlow" /home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsPlaywright.kt` — посмотреть, нет ли других мест в файле со схожим паттерном `System.err.println` или `e.printStackTrace()`, которые тоже надо поправить (даже если за пределами FR).
- [ ] T2.5 Сохранить отчёт в `/home/nsa/Agents/Reviewer/reviews/305-replace-systemerr-with-logger-review.md` по формату из persona.
- [ ] T2.6 Отправить Мирону письмо «305: review — <VERDICT>» с path+sha256 отчёта.

## Phase 3: Закрытие (оркестратор)

- [ ] T3.1 Если APPROVE — записать в `journal` (или в `~/.dsh/agent-mail/journal-YYYY-MM.jsonl` через личное сообщение самому себе) «305 closed».
- [ ] T3.2 Если REQUEST CHANGES — отправить Софье письмо с цитатой из ревью и пойти на Phase 1 заново.
- [ ] T3.3 Если REJECT — обсудить с владельцем, нужна ли спека в принципе.
- [ ] T3.4 Записать тайминги цикла (получено → имплементировано → отревьюировано → закрыто) в `livedocs/decisions/` или в отдельный `workflow-metrics.md` — для будущей оптимизации команды.
