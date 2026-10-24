# Отчёт по #376 — PKG-S2-13 «karaoke-app: DATA» (BR-278, BR-279)

> **Пакет**: PKG-S2-13 бэклога аудита #336, волна D, severity S2, модуль `karaoke-app`
> **Ветка**: `594-pkg-s2-13` (worktree `/home/nsa/Karaoke/.worktrees/waveD-s2-13`),
> база `origin/master` @ `f5da6c18` (HEAD ветки = база; origin/master уже ушёл вперёд
> на `0c54672b` — ветка не ребейзилась, чужие коммиты не затрагивались).
> **Аудит**: порция **P-02** (`specs/336-code-audit/findings/P-02.md`), записи
> P-02-01 → BR-278 и P-02-02 → BR-279, реестр `specs/336-code-audit/registry.csv`.
> **Дата**: 2026-10-24.
> **Объём**: 1 production-файл (`HealthReport.kt`), 1 новый тест, SSoT-документ
> компонента. Схема БД, конфиги, деплой и контейнеры не менялись.

## 1. BR-278 — check-then-act при сравнении размера файла

### Было

`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:767–822`
(и зеркало для REMOTE — `:1153–1251`).

Существование файла на диске проверяется в `getHealthReportList` заранее
(`existsInLocalFileSystem = File(pathToFile).exists()`, например `:2265`), до
походов в `StorageMetadataCache` (SELECT в `tbl_storage_metadata_cache`),
`KaraokeProcess.loadList` (SQL) и `cachedFileInfo` (stat в MinIO). Сравнение
размера идёт уже после них — через десятки–сотни миллисекунд:

```kotlin
val fileIsActual = localInfo == null || File(pathToFile).length() == localInfo.size
if (!fileIsActual) {
    // ERROR + actions: deleteFile(...) + UPLOAD_* с pathToFile
}
```

Если файл за это время переименован каскадом `SongArtifactRenamer.renameLocal`
(specs/124) или удалён вручную, `File.length()` возвращает `0`, старая формула
даёт `false` → «неактуально» → repair удаляет **валидный** объект из
LOCAL/REMOTE-хранилища и ставит загрузку из уже несуществующего пути. Откат
возможен только при живой копии во втором хранилище.

### Стало

`HealthReport.kt:2901–2919` — новый top-level helper:

```kotlin
internal fun diskFileMatchesStoredSize(pathToFile: String, storedSize: Long): Boolean? {
    if (pathToFile.isEmpty()) return null
    val file = File(pathToFile)
    if (!file.exists()) return null
    return file.length() == storedSize
}
```

- существование перепроверяется **непосредственно перед сравнением** — и в
  LOCAL-ветке (`:785–850`), и в зеркальной REMOTE-ветке (`:1188–1258`);
- `null` — отдельная ветка «диск исчез»: `WARNING`, `canResolve = false`,
  `DISK_FILE_VANISHED_PROBLEM_TEXT` / `DISK_FILE_VANISHED_SOLUTION_TEXT`;
  объект хранилища **не удаляется** и повторная загрузка не ставится;
- `false` (файл есть, размер другой) — только здесь остаются delete + upload,
  как и раньше;
- `localInfo == null` (размер неизвестен из-за кеша/брейкера) по-прежнему
  трактуется как «актуально», поведение не менялось.

## 2. BR-279 — флаги готовности плеера при недоступном хранилище

### Было

`HealthReport.kt:2555–2591` (`reconcilePlayerReadinessFlags`).

При открытом circuit breaker'е `actionsLocalStorage` файл **не проверяет**:
возвращает `FATAL_ERROR` «Локальное хранилище недоступно» и выходит
(`:690–703`). Старый helper считал любую запись, отличную от `OK`, отсутствием
файла:

```kotlin
fun isOkInLocalStorage(karaokeFileType: KaraokeFileType): Boolean =
    reports.any {
        it.description == "${type}/${LOCAL_STORAGE.name}" && it.healthReportStatus == OK
    }
```

«Неизвестно из-за недоступности хранилища» превращалось в `false`, и 4
персистентных флага (`stemAccompanimentReady`, `stemVocalReady`,
`pictureAlbumReady`, `pictureAuthorReady`) сбрасывались с записью в БД
(`song.saveToDbLocked()`). Кратковременный сбой локального MinIO (cooldown
30 с) обнулял готовность уже готовых песен — следствие видно на публичном
плеере/в шаринге, лечится только повторным пересчётом.

### Стало

- `HealthReport` получил поле `storageStateUnknown: Boolean = false`
  (`:59–67`): проверка файла не выполнялась, состояние хранилища неизвестно;
- оба circuit-breaker-отчёта (LOCAL `:710`, REMOTE `:1090`) выставляют
  `storageStateUnknown = true`;
- новый helper `localStorageReadinessVerdict` (`:2921–2929`) возвращает
  `Boolean?`: `null` — проверка не выполнялась (`storageStateUnknown` или
  `WAITING`), `true`/`false` — проверка выполнена;
- `reconcilePlayerReadinessFlags` (`:2602–2640`) применяет вердикт через
  `?.let { ... }`: при `null` флаг сохраняет прежнее значение и запись в БД
  не выполняется. Отсутствие отчёта по типу и настоящий `ERROR` (проверка
  выполнена, файла нет) по-прежнему дают `false`.

## 3. Регрессионный тест «до/после»

Новый файл `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/HealthReportDataIntegrityTest.kt`,
2 теста:

- `BR-278 исчезнувший с диска файл не даёт вердикт неактуально` — фиксирует
  старую формулу (`File.length() == 0` → `false`) и новую семантику `null` для
  исчезнувшего файла; реальные вердикты (`true`/`false`) не изменились;
- `BR-279 отчёт из недоступного хранилища не сбрасывает флаги готовности` —
  circuit-breaker-отчёты по 4 типам дают `null` (флаги не трогаем), `OK` →
  `true`, `ERROR`/отсутствие отчёта → `false`, `WAITING` → `null`.

### Команда

```bash
cd /home/nsa/Karaoke/.worktrees/waveD-s2-13
DISPLAY= GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true \
  :karaoke-app:test --tests "com.svoemesto.karaokeapp.HealthReportDataIntegrityTest"
```

### После (текущий код)

```
> Task :karaoke-app:test
BUILD SUCCESSFUL in 1m 12s
```

```
TEST-com.svoemesto.karaokeapp.HealthReportDataIntegrityTest.xml:
tests="2" skipped="0" failures="0" errors="0"
```

### До (production-файл временно возвращён к базе `f5da6c18`, затем восстановлен)

```
> Task :karaoke-app:compileTestKotlin FAILED
e: HealthReportDataIntegrityTest.kt:46:13: error: no parameter with name 'storageStateUnknown' found.
e: HealthReportDataIntegrityTest.kt:62:20: error: unresolved reference 'diskFileMatchesStoredSize'.
e: HealthReportDataIntegrityTest.kt:87:32: error: unresolved reference 'localStorageReadinessVerdict'.
BUILD FAILED in 18s
```

Старый код не знает ни повторной проверки диска, ни состояния «неизвестно» —
регрессия ловится на уровне контракта, а не только ассерта.

## 4. Проверки

| Проверка | Результат |
|---|---|
| `:karaoke-app:test` (полный набор) | **BUILD SUCCESSFUL**, 492 тестов, 0 failures |
| `:karaoke-web:test` | **BUILD SUCCESSFUL**, 173 теста, 0 failures |
| `ktlintCheck` | **BUILD SUCCESSFUL** |
| `bash tools/check-knowledge-structure.sh` | `OK: knowledge/ structure valid (9/9 checks passed)` |
| `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt` | `All documentation passes architectural style and structural checks.` |
| `python3 tools/check-doc-references.py` | `OK: битых ссылок, несуществующих путей и эндпоинтов нет.` |
| `bash tools/check-no-mp4-mentions.sh` | `OK: all 139 MP4 mentions are in baseline (grandfathered)` |
| `bash tools/check-feature-doc.sh docs/features/*.md` | `Все документы валидны` |
| `bash tools/check-subagent-isolation.sh` | exit 0 (ветка без новых коммитов на момент запуска — проверка после коммита ожидаемо зелёная) |
| `pre-commit run --all-files` | запускается без `SKIP=` при коммите; результат — в журнале коммита |

## 5. Что осталось за владельцем

- **Остаточное окно BR-278** между построением отчёта и выполнением
  `solutionActions` (repair-loop может выполнять их позже, минуты): если файл
  исчезнет **после** сравнения, лямбды delete+upload всё ещё могут сработать.
  В ТЗ BR-278 поправка ограничена повторной проверкой перед сравнением;
  харденить сами лямбды (перепроверка диска внутри действия) — отдельное
  решение владельца, оно меняет контракт `solutionActions` и поведение repair.
- **Семантика «диск исчез»** выбрана как `WARNING` + `canResolve = false`, без
  удаления объекта — по формулировке карточки. Если владелец захочет вместо
  этого `IN_PROGRESS`/пропуск отчёта, это правка одной ветки.
- **REMOTE-зеркало** исправлено вместе с LOCAL, хотя `registry.csv` указывает
  диапазон `767–822`: карточка прямо называет зеркало (`:1160`, удаление
  `:1176–1186`) доказательством того же дефекта. Разделять на отдельный BR не
  стал — это тот же root cause.
- Схема БД, конфиги, деплой, контейнеры и прод не затрагивались; `SKIP=` не
  использовался.

## 6. Файлы и хэши

| Файл | Изменение | sha256 |
|---|---|---|
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt` | BR-278 (helper + 2 ветки), BR-279 (`storageStateUnknown`, helper, reconcile) | `934d223d4a4195ed4e356b15a0a448b3ce0e38d9202bf443188fe4ce0493eb19` |
| `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/HealthReportDataIntegrityTest.kt` | **новый**, 2 регрессионных теста | `466c60fcbd449b6b034466ef94cba8d4045f101e702a7795ccf8b5d1557a12cf` |
| `knowledge/domains/health/components/health-report.md` | SSoT: поле `storageStateUnknown`, правила BR-278/BR-279 | `49f51b71037d752b839edef3fb8a157eea02d778a6ba2ad2cb5e2da5666f0892` |

База: `f5da6c187660d9e1b5b61bb5cc651e2401fddaa8` (origin/master на момент
создания ветки). Хэш коммита этой ветки здесь не приводится: отчёт лежит
внутри него самого; актуальный — `git log -1` по ветке `594-pkg-s2-13`.
