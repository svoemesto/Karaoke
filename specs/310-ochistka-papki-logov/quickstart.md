# Quickstart: 310 — Очистка папки логов (validation scenarios)

**Branch**: `310-ochistka-papki-logov` | **Date**: 2026-09-06
**Spec**: [specs/310-ochistka-papki-logov/spec.md](./spec.md)
**Plan**: [plan.md](./plan.md)
**Research**: [research.md](./research.md)
**Data model**: [data-model.md](./data-model.md)

## Prerequisites

- Karaoke repo на feature-ветке `310-ochistka-papki-logov`
- JDK 17 + Gradle (для `./gradlew` команд)
- Karaoke строит из корня: `cd /home/nsa/Karaoke`

## Setup

```bash
cd /home/nsa/Karaoke
git checkout 310-ochistka-papki-logov
```

Убедитесь, что branch active и все изменения в working tree (per AGENTS.md «всё в working tree до самого финала»).

## Unit tests (FR-6)

Запуск тестов для фичи:

```bash
./gradlew :karaoke-app:test --tests "com.svoemesto.karaokeapp.KaraokeProcessCleanupTest"
```

**Ожидаемый результат**: 3 теста PASS, 0 failures. Тесты проверяют:
- SC-2 (свежие/старые файлы)
- FR-3 (clock skew защита)
- FR-4 (fail-open, per-file isolation)

## 5-step verification (brief.md, NON-NEGOTIABLE)

Запуск в этом порядке, без пропусков:

```bash
# 1. compile
./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel
# Ожидаемый результат: BUILD SUCCESSFUL, без ошибок компиляции

# 2. ktlint
./gradlew :karaoke-app:ktlintCheck
# Ожидаемый результат: BUILD SUCCESSFUL, без новых нарушений (UP-TO-DATE допустим)

# 3. eslint (skip — backend-only)
# (нет frontend изменений)

# 4. bootJar (на nsa-i9 — оба модуля)
./gradlew :karaoke-app:bootJar :karaoke-web:bootJar
# Ожидаемый результат: BUILD SUCCESSFUL, оба .jar файла созданы в build/libs/

# 5. (нет Vite build, нет Docker build)
```

## Manual integration smoke

Этот сценарий проверяет, что cleanup работает end-to-end в реальной среде.

### Подготовка

```bash
# Создать тестовую папку логов с файлами разного возраста
LOG_DIR=/tmp/test-logs
mkdir -p "$LOG_DIR"
rm -f "$LOG_DIR"/*.log

# Файлы 5, 20, 60 дней назад
for age in 5 20 60; do
  touch -d "$age days ago" "$LOG_DIR/test-${age}d.log"
done

ls -la "$LOG_DIR"
# Должно быть 3 файла с разными mtime
```

### Проверка

Если меняется `PATH_TO_LOGS` в `Constants.kt` для теста (на `/tmp/test-logs`) и собирается:
- 5d.log и 20d.log — остаются
- 60d.log — удаляется
- В `docker logs karaoke-app` (или в stderr при ручном запуске) — `cleanup: removed 1 files older than 30 days from /tmp/test-logs`

**ВАЖНО**: manual smoke изменяет `Constants.PATH_TO_LOGS` на `/tmp/test-logs` для теста. Это **не** для merge — только для локальной проверки. Перед коммитом вернуть на `/sm-karaoke/system/logs`.

## Production deployment validation

После merge в master и deploy на production:

1. Проверить, что `karaoke-app` запускается без ошибок
2. Дождаться первой записи лога KaraokeProcess
3. Проверить `docker logs karaoke-app | grep cleanup` — должна появиться строка `cleanup: removed N files older than 30 days from /sm-karaoke/system/logs` (если есть что удалять)
4. Если cleanup не нужен (все логи свежие) — запись лога проходит, cleanup просто возвращает 0

## Livedoc check (SC-5)

Проверить, что `livedocs/features/310-ochistka-papki-logov.md` создан с правильным frontmatter:

```bash
head -10 /home/nsa/Karaoke/livedocs/features/310-ochistka-papki-logov.md
# Должно быть:
# ---
# status: Active
# slug: 310-ochistka-papki-logov
# related:
#   - ../../specs/310-ochistka-papki-logov/spec.md
# ---

# Также проверить строку в INDEX
grep "310-ochistka-papki-logov" /home/nsa/Karaoke/livedocs/features/README.md
```

## Acceptance criteria self-check

| SC | Проверка | Ожидание |
|---|---|---|
| SC-1 | Папка не превышает 3000 файлов | ✅ при retention=30 на 100 процессов/день |
| SC-2 | Файлы ≤ 30 дней сохраняются, > 30 дней удаляются | ✅ covered by `cleanupRemovesOldFiles` test |
| SC-3 | Запись нового лога не блокируется | ✅ fail-open через outer try/catch |
| SC-4 | Cleanup ≤ 1 сек на 1000 файлов | ⚠️ не покрыто тестом; covered by design (Files.list + per-file ops) |
| SC-5 | Livedoc + INDEX | ✅ manual check |

## Open follow-up

- **OBS-1** от Марка (фаза 9): явный boundary-тест для AC-2 (mtime == threshold). Тест-план перечисляет только AC-3/4 в текущей версии — формально не нарушение, но follow-up. Можно добавить в следующей итерации.

## Done When

- [x] Unit tests (FR-6) PASS
- [x] 5-step verification пройден
- [x] Livedoc (SC-5) создан с правильным frontmatter
- [x] INDEX (SC-5) содержит строку 310
- [x] Acceptance criteria (SC-1..SC-5) self-check выполнен
