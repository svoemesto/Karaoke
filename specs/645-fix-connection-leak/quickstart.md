# Quickstart: проверка фичи «Устранение утечки соединений с БД (BR-627)»

**Дата**: 2026-10-26 | **Спека**: [spec.md](spec.md) | **План**: [plan.md](plan.md)

Документ — инструкция по проверке, без кода реализации. Детали контракта —
[contracts/connection-lifecycle.md](contracts/connection-lifecycle.md),
инварианты проверки — [data-model.md](data-model.md).

## Предусловия

- JDK 17+, сеть до репозитория Gradle (кэш — `<корень репозитория>/.gradle`);
- локальная БД PostgreSQL доступна (тесты модуля `karaoke-app` идут против неё);
- backend-тесты запускаются с `env -u DISPLAY` (иначе падают 6 тестов).

Переменные путей — переносимые, от корня репозитория (AGENTS.md, Pass 372/373):

```bash
cd /home/nsa/Karaoke/.worktrees/br627
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")/.gradle}"
export DOCKER_CONFIG="${DOCKER_CONFIG:-$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")/.docker}"
```

## Сценарий 1. Проверка «до/после»: в файле с обращением к БД появляется сырой поток

**Ожидание**: проверка падает и указывает файл и строку.

1. Внести в любой файл прикладного кода, обращающийся к БД (например,
   `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/StorageCacheWarm.kt`),
   сырой поток:

   ```kotlin
   thread {
       val c = WORKING_DATABASE.getConnection()
       println(c)
   }
   ```

2. Прогнать проверку:

   ```bash
   env -u DISPLAY ./gradlew :karaoke-app:test --tests "com.svoemesto.karaokeapp.services.OneShotThreadWiringTest" \
     --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true
   ```

3. Ожидаемый результат: тест **красный**, в сообщении — путь файла и номер
   строки с внедрённым `thread {`.
4. Откатить правку (`git checkout -- <файл>`) и убедиться, что тест снова зелёный.

> То же самое делает встроенная негативная фикстура теста: она берёт **копию**
> реального DB-файла, внедряет сырой поток во временный каталог и требует, чтобы
> детектор её нашёл. Фикстура не зависит от состояния рабочего дерева.

## Сценарий 2. Различение: сырой поток в файле БЕЗ обращений к БД

**Ожидание**: нарушением не считается.

Встроенная фикстура: синтетический файл без маркеров обращения к БД, но с
сырым `thread {` → детектор возвращает **пустой** список нарушений.

Ручная проверка (необязательно): создать временный файл без обращений к БД
вне `src/main` (например, в `/tmp`) — он не сканируется; проверка работает по
дереву `src/main` двух модулей.

## Сценарий 3. Поведение закрытия соединения

**Ожидание**: соединение одноразового потока закрывается и при успехе, и при
исключении (FR-005).

```bash
env -u DISPLAY ./gradlew :karaoke-app:test \
  --tests "com.svoemesto.karaokeapp.services.OneShotThreadTest" \
  --tests "com.svoemesto.karaokeapp.services.OneShotThreadWiringTest" \
  --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true
```

Ожидаемый результат: все тесты зелёные; в отчёте видно, что `closeThreadConnection()`
вызван **ровно один раз** в обоих сценариях.

## Сценарий 4. Покрытие: сколько файлов прикладного кода проверяется

**Ожидание**: охват — 100% файлов, обращающихся к БД, а не фиксированный
перечень.

В отчёте теста `OneShotThreadWiringTest` печатается число найденных DB-файлов.
Ориентир на текущий код: **≈160 файлов** из ~420 `.kt` в `karaoke-app` +
`karaoke-web`. Список из 4 ранее неохваченных файлов, которые теперь под
проверкой:

- `karaoke-app/.../ExportAlignmentDataset.kt`
- `karaoke-app/.../StorageCacheWarm.kt`
- `karaoke-app/.../CacheEtagSizeBackfill.kt`
- `karaoke-app/.../controllers/SongEditorController.kt`

## Сценарий 5. Число соединений на БД не растёт (SC-001, частично)

**Ожидание**: после серии фоновых операций число занятых соединений вернулось
к исходному.

1. Зафиксировать исходное число соединений:

   ```sql
   SELECT count(*) FROM pg_stat_activity;
   ```

2. Выполнить N (20) фоновых операций, ходящих в БД (экспорт датасета,
   прогрев кеша хранилища, бэкфилл etag/size) — через API админки.
3. Снова выполнить `SELECT count(*) FROM pg_stat_activity;`.

Ожидание: разница — не более 1 соединения на операцию (сейчас — плюс одна на
каждую).

> На тестовой машине этот сценарий выполняется вручную владельцем: он требует
> живой локальной БД и запуска фоновых операций админки. Автоматическая часть
> проверки — сценарии 1–4.

## Сценарий 6. Регламент прогона (FR-009)

Перед длинным прогоном backend-тестов:

```sql
SELECT count(*) FROM pg_stat_activity;   -- сравнить с max_connections
```

Требуемый запас по лимиту соединений — `max_connections=300`
(`DB_MAX_CONNECTIONS`). Тестовые потоки оставляют соединения так же, как
фоновые операции: при нехватке запаса прогон падает с
`sorry, too many clients already` — не по своей причине.

## Полный контрактный прогон (R-47)

Перед финалом — **все** `run:`-команды из `.github/workflows/lint.yml`
(список извлекается из воркфлоу, а не по памяти):

```bash
python3 - <<'PY' > /tmp/ci_runs.txt
import re, pathlib
s = pathlib.Path('.github/workflows/lint.yml').read_text(encoding='utf-8')
for m in re.finditer(r'^\s*-?\s*run:\s*(.+)$', s, re.M):
    cmd = m.group(1).strip()
    if cmd.startswith(('bash tools/','python3 tools/','python tools/','./tools/')):
        print(cmd)
PY
while read -r cmd; do cmd="${cmd//\$\{\{ matrix.spa \}\}/webvue3}"; \
  eval "$cmd" >/tmp/g.out 2>&1 || echo "НЕ-НУЛЕВОЙ: $cmd"; done < /tmp/ci_runs.txt
```

Плюс гейты знаний:

```bash
bash tools/check-knowledge-structure.sh
bash tools/check-knowledge-cross-links.sh
python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt
python3 tools/check-doc-references.py
python3 tools/check-ssot-impact.py
```

## Ссылки

- Контракт: [contracts/connection-lifecycle.md](contracts/connection-lifecycle.md)
- Исследование: [research.md](research.md)
- Спека: [spec.md](spec.md)
