# Отчёт по пакету PKG-S2-11 (OpenProject #374, аудит #336)

- **Ветка**: `596-pkg-s2-11` от `origin/master` `822bb3fa`.
- **Worktree**: `/home/nsa/Karaoke/.worktrees/waveD-s2-11` (главная копия не
  изменялась).
- **Объём**: 15 записей S2 — BR-110, BR-111, BR-112, BR-113, BR-128, BR-132,
  BR-133, BR-139, BR-140, BR-141, BR-142, BR-143, BR-148, BR-162, BR-176.
- **Коммиты**:
  - `7caec60c` — BR-110..113 (P-08);
  - `3c913e62` — BR-128/132/133 (P-19/P-21);
  - `0f930cd2` — BR-139..143 (P-23);
  - `a77a69b5` — BR-148/162/176 (P-24/P-22/P-16);
  - отдельный коммит — этот отчёт + knowledge/ + per-feature doc.

## 1. Что было / что стало

| BR | Было | Стало | Регрессионный тест |
|---|---|---|---|
| BR-110 | `ConcurrentHashMap` огибающих без вытеснения; старые ключи по mtime жили вечно | LRU `LinkedHashMap(accessOrder)` на 64 записи; при новом mtime прежние ключи того же пути удаляются | `WaveformCompareCacheTest` |
| BR-111 | `plans`/`runs` — `ConcurrentHashMap` без удаления, полный список путей на каждый `plan` | LRU 32/128 + TTL 30 мин; отчёт удаляется после терминального `apply` | `CleanupPlanRetentionTest` |
| BR-112 | Читалась вся `tbl_listening_history` (loadList без LIMIT), все песни, затем сортировка/страница в Kotlin | SQL: `EXISTS` по `tbl_songs`, `ORDER BY`, `LIMIT/OFFSET`, отдельный `COUNT(*)`; песни — только страницы | `ListeningHistoryQueryTest` (SQL-контракт; БД-integration не запускалась — см. §5) |
| BR-113 | `authLatch.await()` без таймаута; глобальный latch переприсваивался после CAS | `AuthFlowGate`: локальный latch, `await(10 мин)`, контекст закрывается в `finally` | `AuthFlowGateTest` |
| BR-128 | `associateBy { songId }` — произвольная строка из нескольких назначений | `Map<songId, List<...>>`, порядок «open → max id»; публичная карточка ищет назначение текущего редактора | `SongAssignmentPreferenceTest` |
| BR-132 | Каждый `enqueue` → `submit { workerLoop() }` (бесконечный цикл) → неограниченная очередь executor'а | Воркеры стартуют один раз; побудка через `Semaphore` (не больше числа воркеров) | `HealthReportBatchPoolWakeupTest` |
| BR-133 | `key{SEP}value` без экранирования, `writeText`, destructuring без проверки | Base64-префикс `b64:`, атомарная запись под lock, битая строка пропускается с WARN | `PropertiesFileDictionaryTest` |
| BR-139 | `saveToDbLocked()` (Boolean) игнорировался во всех 4 местах | `saveWithRetry` (3 попытки); потеря `message_id` → `SEND_FAILED` с явной ошибкой | `TelegramSaveRetryTest` |
| BR-140 | Retry не ловил исключения `trySendVideo`; сеть/таймаут/не-JSON падали с первой попытки | Каждая попытка в `try/catch`, классификация retryable, backoff, `SEND_FAILED` на исчерпании; `cause` сохранён | `TelegramApiClientRetryTest` |
| BR-141 | `uploadFile(path)` при ошибке печатал и возвращал `null`; вызывающий ставил `DONE` | Бросает `IllegalArgumentException`/`RuntimeException`; «нет файла» и «сбой загрузки» различимы | `StorageUploadFailClosedTest` |
| BR-142 | Новый `HttpClient` на каждый proxy-запрос (selector-поток/сокеты) | Клиент кэшируется по `telegramProxyUrl`, пересоздаётся только при смене URL | `TelegramProxyClientCacheTest` |
| BR-143 | `ResponseEntity.ok(dto as Map)` → `ClassCastException` на каждом вызове | `ResponseEntity<StatsDebugDto>`, каст удалён | `StatsDebugResponseTest` |
| BR-148 | Настройки VK фиксировались в конструкторах синглтонов | `VkPreviewWarmupClient` читает флаг на каждом `warmup`; `VkPhotoUploadClient` — токены/флаги на каждом вызове | `VkPropertiesRefreshTest` |
| BR-162 | Уборка всех бакетов инстанса из `init` без `try/catch` | Только `karaoke`/`stemjobs`; сбой уборки → WARN, контекст поднимается | `CleanupStartupGuardTest` |
| BR-176 | `null` → `setString("null")`: ошибка timestamp/boolean, терялся весь `SET`; ошибка UPDATE только логировалась | `setNull(index, sqlTypeForNull(oldValue))`; ошибка `executeUpdate()` пробрасывается | `KaraokeDbTableNullBindingTest` |

## 2. «До/после» — показательные прогоны

Тесты запускались на **старом коде** в отдельном worktree
`/home/nsa/Karaoke/.worktrees/before-s211` (HEAD `822bb3fa`) и на новом — в
рабочем worktree. Старый worktree после проверки удалён.

### 2.1 BR-132 — старый код

```
$ cd /home/nsa/Karaoke/.worktrees/before-s211
$ GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 \
    -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" \
    -Djava.awt.headless=true :karaoke-app:test \
    --tests '...HealthReportBatchPoolWakeupTest'
HealthReportBatchPoolWakeupTest > enqueue does not submit new worker loops() FAILED
    org.opentest4j.AssertionFailedError at HealthReportBatchPoolWakeupTest.kt:83
HealthReportBatchPoolWakeupTest > enqueueWaiting does not submit new waiting worker loops() FAILED
    org.opentest4j.AssertionFailedError at HealthReportBatchPoolWakeupTest.kt:104
BUILD FAILED
```

Причина: 20 `enqueue` × `POOL_SIZE=10` = 200 submit'ов (ожидалось 0).

### 2.2 BR-133 — старый код

```
PropertiesFileDictionaryTest > values with newline survive save and load(Path) FAILED
    java.lang.IndexOutOfBoundsException at PropertiesFileDictionaryTest.kt:38
PropertiesFileDictionaryTest > broken legacy line is skipped and other settings survive(Path) FAILED
    java.lang.IndexOutOfBoundsException at PropertiesFileDictionaryTest.kt:51
BUILD FAILED
```

Причина: `val (key, value) = line.split(separator())` на строке без разделителя.

### 2.3 BR-141 — старый код

```
StorageUploadFailClosedTest > missing local file throws instead of returning null() FAILED
    org.opentest4j.AssertionFailedError at StorageUploadFailClosedTest.kt:30
BUILD FAILED
```

Причина: `uploadFile(path)` возвращал `null` вместо исключения.

### 2.4 После правки

```
$ GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 \
    -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" \
    -Djava.awt.headless=true :karaoke-app:test :karaoke-web:test
BUILD SUCCESSFUL
# 688 тестов, 0 failures (test-results/test/*.xml)
```

Для BR-139/143/162/113/128/112/176 тест использует новый внутренний шов
(`saveWithRetry`, `debugResponse`, `cleanupEmptyBucketsOnStartup`,
`AuthFlowGate`, `preferredAssignment`, `buildListeningHistoryQuery`,
`bindRecordDiffValue`), поэтому на старом коде он **не компилируется** —
это тоже красный прогон; отдельного runtime-«до» у этих записей нет.

## 3. Проверки (CI-набор)

| Проверка | Команда | Результат |
|---|---|---|
| Backend tests | `./gradlew :karaoke-app:test` | PASS (515 тестов, 0 fail) |
| Web tests | `./gradlew :karaoke-web:test` | PASS |
| ktlint | `./gradlew ktlintCheck` | PASS |
| Knowledge structure | `bash tools/check-knowledge-structure.sh` | PASS |
| Knowledge lint | `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt` | PASS |
| Doc references | `python3 tools/check-doc-references.py` | PASS |
| No MP4 mentions | `bash tools/check-no-mp4-mentions.sh` | PASS |
| Feature docs | `bash tools/check-feature-doc.sh docs/features/*.md` | PASS |
| Subagent isolation | `bash tools/check-subagent-isolation.sh` | PASS |

**Окружение.** На этой машине `DISPLAY=:0` указывает на несуществующий X11:
без сброса `DISPLAY` тесты `VkPreviewWarmupClientTest` падают с `AWTError`
(это не связано с правкой; `-Djava.awt.headless=true` в test-JVM не
пробрасывается). Полный прогон выполнен с `DISPLAY=` — 688 тестов, 0 failures.

## 4. Knowledge SSoT и per-feature документ

Обновлены:

- `knowledge/domains/health/components/health-report-batch-pool.md` — BR-132;
- `knowledge/domains/processing/components/playwright-rendering.md` — BR-113;
- `knowledge/domains/processing/components/karaoke-properties.md` — BR-133/148;
- `knowledge/domains/publishing/components/publishing-services.md` —
  BR-139/140/142/148;
- `knowledge/domains/storage/domain.md` — BR-111/141/162;
- `knowledge/domains/persistence/domain.md` — BR-176;
- `knowledge/domains/editorial/components/assignment-lifecycle.md` — BR-128;
- `knowledge/system/utilities.md` — BR-110/112.

Per-feature документ: `docs/features/app-resilience-pkg-s2-11.md`
(6 обязательных секций, включая `## Известные ловушки`).

## 5. Ограничения и решения за владельцем

1. **BR-112.** Модульного теста «до/после» с реальной БД нет: в проекте нет
   embedded-БД, а `Connection.local()` не инжектируется. Тест фиксирует
   SQL-контракт (LIMIT/OFFSET, EXISTS, bind-параметры); интеграционный прогон —
   на живой БД за владельцем.
2. **BR-113.** Auth-flow остаётся синхронным в HTTP-потоке, но с конечным
   таймаутом 10 минут. Полный вынос в фоновую задачу + статус — отдельная
   задача, за владельцем.
3. **BR-141.** Контракт `StorageApiClient.uploadFile(path)` изменён с
   `null`-при-ошибке на исключение. Внутренние вызывающие обёрнуты в
   `try/catch`; если есть внешние потребители, ожидающие `null`, — за
   владельцем.
4. **BR-162.** Allow-list бакетов — `karaoke`, `stemjobs`
   (`KARAOKE_STORAGE_BUCKETS`). Новый бакет Karaoke нужно добавить туда явно.
5. **BR-176.** `save()` теперь пробрасывает ошибку `executeUpdate()`
   (`IllegalStateException`) — это меняет контракт на fail-closed (5xx в
   админке вместо ложного 200). Согласовано с ADR local-0002; подтверждение
   владельца — за ним.
6. **BR-110/111.** Числовые лимиты (64 записи ≈ 6 МБ; 32 отчёта / TTL 30 мин;
   128 прогонов) — консервативные значения; при необходимости меняются в
   константах.
7. **BR-128.** Критерий «активное = `admin_status='open'`, иначе максимальный
   `id`» — за владельцем (при иной продуктовой трактовке меняется одна
   comparator-функция).

## 6. Замечание по рабочему дереву

Во время работы в рабочем дереве появилось **не относящееся к пакету**
изменение `karaoke-app/.../KaraokeProperties.kt` (BR-244/P-05-01, чужой пакет):
оно пришло из общего для всех worktree стека `git stash` при `stash pop`.
В коммиты пакета это изменение **не включено** и оставлено в рабочем дереве
как есть; владельцу/координатору следует учесть, что глобальный `git stash`
в этом репозитории не изолирован по worktree.
