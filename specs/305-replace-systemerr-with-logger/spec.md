# Feature Specification: Замена `System.err.println` на структурное логирование

**Feature Branch**: `305-replace-systemerr-with-logger`

**Created**: 2026-09-04

**Status**: Draft

**Input**: Workflow-тест для нового multi-agent цикла speckit (Мирон-оркестратор + Софья-имплементатор + ревьюер). Спека выбрана как самая узкая в Karaoke, чтобы отработать передачу артефакта между коллегами без риска большого diff'а.

## Контекст

В `karaoke-app` остался **ровно один** вызов `System.err.println` в production-коде:

```
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsPlaywright.kt:77
  System.err.println("Ошибка при работе с браузером: ${e.message}")
  e.printStackTrace()
```

Весь остальной код либо использует SLF4J `log.error/warn/info/debug` (27 файлов), либо `kotlin.io.println` для информационного вывода (около 200 вхождений, **вне scope** этой спеки). `System.err.println` — это единственный «голый» stderr в `src/main`, и спека закрывает именно его.

## Корневая причина

Замена нужна для:

1. **Структурности логов.** `System.err.println` пишет plain-text в stderr без уровня/logger-name/MDC. На проде это смешивается с настоящими ошибками и теряется в общем потоке (`docs/ops/log-correlation.md`).
2. **Корректной маршрутизации.** В `KaraokeAppService.kt:57-58` сделан `System.setErr(DualStream(System.err))` — обёртка для одновременного вывода в консоль и в файл. Замена на `logger.error` сохраняет тот же маршрут через logback.
3. **KDoc/JSDoc coverage.** Это top-level функция в Kotlin — KDoc не требуется, но logger-declaration документируется через имя логгера.

## Acceptance Criteria

**FR-1.** В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsPlaywright.kt` отсутствует `System.err.println` (проверяется `grep -rn "System\.err\.print" karaoke-app/src/main` → пусто).

**FR-2.** В том же файле есть top-level объявление логгера в стиле проекта:

```kotlin
private val log = LoggerFactory.getLogger("UtilsPlaywright")
```

(либо эквивалент `UtilsPlaywrightKt::class.java` — на усмотрение имплементатора, важно соответствие стилю 27 других файлов в `karaoke-app/src/main`).

**FR-3.** Замена в строке ~77:

```kotlin
} catch (e: Exception) {
    log.error("Ошибка при работе с браузером: ${e.message}", e)
}
```

Сохранены: `e.message` (или `${e.message}`), и сам объект исключения `e` передаётся вторым аргументом в `log.error` (важно для stack-trace в логах).

**FR-4.** Импорт `org.slf4j.LoggerFactory` добавлен в шапку файла (если его там нет).

**FR-5.** Удалена отдельная строка `e.printStackTrace()` (теперь stack-trace попадает в log через второй аргумент `log.error(...)`).

**FR-6.** Gradle compile проходит: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin` — exit 0.

**FR-7.** ktlintCheck проходит: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:ktlintCheck` — exit 0, новых нарушений в baseline нет.

**FR-8.** (опционально) `karaoke-app:bootJar` собирается без ошибок: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:bootJar` — exit 0. Если не запускается — в отчёте указать причину.

## Out of scope

- Миграция `kotlin.io.println` (~200 вхождений) — отдельная большая задача.
- Изменение `DualStream` или `KaraokeAppService` — не нужно для замены одного вызова.
- Полный аудит `karaoke-web/src/main` — там `System.err.println` не нашлось, scope karaoke-app.
- Любые правки livedocs (FR-014): эта спека не меняет BC и не поднимает C4 уровень.

## Артефакт для ревьюера

После имплементации имплементатор (Софья) обязан приложить к письму оркестратору:

```
spec: 305-replace-systemerr-with-logger
files:
  - path: /home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsPlaywright.kt
    sha256: <после правки>
  - path: /home/nsa/Karaoke/.gradle/baseline-diff.txt  # ktlint diff vs baseline
    sha256: <после gradle>
build evidence:
  - compileKotlin: <exit_code> (<output tail>)
  - ktlintCheck: <exit_code> (<output tail>)
  - bootJar: <exit_code or "not run — reason">
```

Ревьюер читает файлы по путям, считает sha256 сам, проверяет FR-1..FR-8 и пишет отчёт в `/home/nsa/Agents/Reviewer/reviews/305-replace-systemerr-with-logger-review.md`.

## Протокол передачи (для оркестратора)

1. Мирон шлёт Софье спеку (`spec.md` + `tasks.md`) через `send_letter` с темой «305: implement».
2. Софья имплементирует, запускает gradle/ktlint, собирает артефакт-отчёт.
3. Софья шлёт Мирону письмо с темой «305: implemented», прикладывая path+checksum (как в шаблоне выше).
4. Мирон шлёт ревьюеру письмо с темой «305: review», прикладывая spec+артефакт (тоже path+checksum).
5. Ревьюер делает ревью, пишет отчёт в `reviews/305-...-review.md`.
6. Ревьюер шлёт Мирону письмо с вердиктом (APPROVE / REQUEST CHANGES / REJECT).
7. Если REQUEST CHANGES — Мирон возвращает Софье с цитатой из ревью.
8. Если APPROVE — Мирон пишет в `journal` о закрытии спеки 305.

## Definition of Done

- [ ] FR-1..FR-8 — все ✓ в отчёте ревьюера.
- [ ] Review-отчёт сохранён в `reviews/305-...-review.md`.
- [ ] Мирон записал в журнал «305 closed».
- [ ] Никаких новых ktlint-нарушений в baseline.
- [ ] Workflow-тайминги задокументированы (для будущей оптимизации).
