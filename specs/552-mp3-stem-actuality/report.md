# Report: Ложная неактуальность mp3-стемов после рендера демо

**Issue**: OpenProject #335 (источник — #328) · **Branch**: `552-mp3-stem-actuality` · **Дата**: 2026-10-23

## Что сделано

HealthReport для пяти storage-only mp3-типов больше не считает файл на диске эталоном актуальности. Если объекты есть в обоих хранилищах, сравниваются только их размеры (`LOCAL`↔`REMOTE`); при равенстве — OK, при расхождении — WARNING с текстом о расхождении хранилищ и без автоматических действий. Для типов с `LOCAL_FILESYSTEM` поведение не изменилось.

Файлы:

- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt` — helper'ы `diskCopyIsAuthoritative`, `storageActuality`, константы текстов; две ветки `actionsLocalStorage`/`actionsRemoteStorage`.
- `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/HealthReportMp3ActualityTest.kt` — 6 регрессионных тестов.
- `knowledge/domains/health/components/health-report.md` — правило актуальности для storage-only типов.

## Причина (напоминание)

1. specs/544 удалил локальную mp3-копию и удаляет её после загрузки в оба хранилища.
2. Рендер демо открывает headless-плеер; тот грузит `fileminus.mp3`/`filevoice.mp3`, `convertFlacToMp3()` создаёт копию заново (VBR q2 вместо 320k CBR).
3. `pushMp3ToStorage()` не перезаливает существующий объект.
4. HealthReport сравнивал диск с LOCAL и REMOTE → 4 ERROR. После фикса сравниваются хранилища, диск не читается.

Живые данные: песни 8243 и 18517, объекты 3 618 525 Б, дисковая копия 1 724 357 Б (ctime 2026-10-23 11:00:41). При равных размерах хранилищ новый helper возвращает OK — 4 записи исчезают.

## Проверки

```text
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin :karaoke-app:compileTestKotlin   → BUILD SUCCESSFUL
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test --tests '*HealthReport*'                   → BUILD SUCCESSFUL
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:ktlintCheck :karaoke-web:compileKotlin          → BUILD SUCCESSFUL
python3 tools/lint-knowledge.py                                                                                   → All documentation passes
python3 tools/check-spec-issue-link.py                                                                             → OK 47/47
```

Тесты: `HealthReportMp3ActualityTest` — 6/6, включая контрольные типы с `LOCAL_FILESYSTEM` (поведение не должно меняться).

## Что не проверено живьём

Контейнер `karaoke-app` не перезапускался: на admin-машине это требует согласия и остановки очереди `KaraokeProcess` (AGENTS.md Pass 380), а в очереди были активные задания. Проверка после деплоя — в `quickstart.md` (сценарий «плеер создал копию → HR OK» и негативный сценарий расхождения).

## Риски и Non-Goals

- Ленивая генерация mp3 остаётся в пути стема (#332). Существующий риск: переименование песни может залить VBR-копию в хранилища. Вне скоупа, зафиксировано в карте #329.
- Сравнение только по размеру (#333): файл того же размера с другим содержимым не будет замечен — как и до фикса.
- Исторические записи не лечатся; после деплоя исчезнут при первом пересчёте HR.
