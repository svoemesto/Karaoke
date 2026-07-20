# Quickstart: Валидация фичи «Приведение кода к стандартам и документирование»

**Branch**: `001-code-standards-docs` | **Phase**: 1 | **Date**: 2026-07-20

Этот документ — сценарий проверки «end-to-end», что фича внедрена правильно
и работает. Не реализация, а **галочки**, которые нужно пройти для подтверждения
готовности.

## Предусловия

- Ветка: `001-code-standards-docs`.
- Репозиторий склонирован, `cd` в корень.
- Java 17, Node 22, Python 3.10+ (для pre-commit), Rust (для `lychee` —
  или заранее установленный бинарь).
- Установлен `pre-commit` (`pip install pre-commit`).
- Установлен `lychee` (https://github.com/lycheeverse/lychee/releases).

## 1. Установка инструментов (одноразовая)

```bash
# pre-commit (Python)
pip install pre-commit

# lychee (Rust binary)
# На Linux: скачать с https://github.com/lycheeverse/lychee/releases
# и положить в /usr/local/bin/lychee
# Альтернатива: cargo install lychee

# Активация pre-commit в репозитории
cd /home/nsa/Karaoke
pre-commit install
```

Ожидаемый результат: pre-commit-хук зарегистрирован в `.git/hooks/`.

## 2. Проверка: линтеры на «чистом» прогоне

**Kotlin (бэкенд)**:

```bash
./gradlew ktlintCheck detekt
```

Ожидаемый результат: `BUILD SUCCESSFUL`. Код завершается с кодом 0, в выводе
— только SUCCESS (или предупреждения, входящие в baseline).

Проверка соответствует: **SC-002** (baseline-чек), **FR-002** (линтеры
запускаются).

**Vue (webvue3)**:

```bash
cd webvue3
npm install  # один раз
npm run lint
```

Ожидаемый результат: `0 errors, X warnings` где X ≤ количества в baseline
`webvue3/.eslint-baseline.json`.

**Vue (karaoke-public)**:

```bash
cd karaoke-public
npm install  # один раз
npm run lint
```

Ожидаемый результат: `0 errors, X warnings` где X ≤ количества в baseline
`karaoke-public/.eslint-baseline.json`.

## 3. Проверка: per-feature документы (FR-004, FR-005, SC-001)

```bash
ls docs/features/
```

Ожидаемый результат: 9 файлов, по одному на каждую ключевую подсистему:
`mlt-generator.md`, `async-process-queue.md`, `dual-db-sync.md`,
`mp4-render.md`, `sse-notifications.md`, `premium-stems.md`,
`llm-lyrics-search.md`, `telegram-auto-publish.md`, `monitoring.md`.

```bash
# Проверка, что все 6 секций присутствуют в каждом документе
for f in docs/features/*.md; do
  echo "=== $f ==="
  rg '^## (Что делает|Зачем|Как работает|Инварианты|Известные ловушки|Ссылки)' "$f" | wc -l
done
```

Ожидаемый результат: `6` для каждого файла (все 6 секций найдены).

Проверка соответствует: **FR-004** (9 фич), **FR-005** (6 разделов),
**SC-001** (100% покрытие).

## 4. Проверка: валидность ссылок (FR-008, SC-005)

```bash
lychee --offline docs/features/ CONTRIBUTING.md
```

Ожидаемый результат: `0 errors` после прохода. Все внутренние ссылки
(на файлы `.kt`, `.vue`, `.md`) валидны, все относительные пути
разрешаются.

Если есть ошибки — они указывают на конкретный файл/строку/якорь.

Проверка соответствует: **SC-005** (0 битых ссылок).

## 5. Проверка: KDoc-генерация (FR-006, SC-004)

```bash
./gradlew :karaoke-app:dokkaHtml
```

Ожидаемый результат: задача завершается успешно. В выводе — `0 warnings`
(или только baseline-известные, не касающиеся «unresolved @see»).

Открыть `karaoke-app/build/dokka/html/index.html` в браузере → найти
любой публичный класс → убедиться, что у него есть KDoc с `@see`-ссылкой
на соответствующий per-feature документ (например,
`@see docs/features/async-process-queue.md`).

Проверка соответствует: **SC-004** (≥90% публичных API документированы,
Dokka без warning'ов).

## 6. Проверка: pre-commit-хук ловит нарушения

```bash
# Создать временный файл с нарушением (например, wildcard import)
echo "package com.example

import kotlinx.serialization.*

class Foo" > /tmp/test-violation.kt
cp /tmp/test-violation.kt karaoke-app/src/main/kotlin/com/example/Foo.kt

# Попытаться сделать коммит
git add karaoke-app/src/main/kotlin/com/example/Foo.kt
git commit -m "test: pre-commit should block this"
```

Ожидаемый результат: pre-commit-хук валится с сообщением
`ktlint: standard:no-wildcard-imports`. Коммит НЕ создан.

Откатить:

```bash
git restore --staged karaoke-app/src/main/kotlin/com/example/Foo.kt
rm karaoke-app/src/main/kotlin/com/example/Foo.kt
```

Проверка соответствует: **FR-002** (pre-commit блокирует), **SC-002**
(линтеры автоматические).

## 7. Проверка: baseline-метрики (SC-002, ≥10%/мес)

```bash
# Посчитать количество нарушений в baseline
./tools/baseline-stats.sh
```

Ожидаемый результат: число уменьшается по сравнению с предыдущим релизом
(если релиз первый — выводит текущее количество, цель на следующий релиз —
−10%).

Проверка соответствует: **SC-002** (≥10%/мес сокращение).

## 8. Проверка: Constitution Check (FR-010)

Прочитать `.specify/memory/constitution.md` → раздел «Constitution Check»
в `plan.md` → убедиться, что **5/5 принципов** в колонке «Соответствие»
имеют ✅ PASS.

Если хотя бы один принцип нарушен — фича не готова, нужны обоснования
в «Complexity Tracking» плана.

## 9. Проверка: правило для PR (FR-009)

Найти в git history любой PR, который менял код одной из 9 фич из FR-004
→ убедиться, что в том же PR менялся (или создавался) соответствующий
`docs/features/<slug>.md`.

Если есть PR, меняющий код фичи без обновления документа — нарушение FR-009.

## 10. Проверка: очистка (cleanup)

```bash
# Убедиться, что нет лишних файлов
git status
```

Ожидаемый результат: `nothing to commit, working tree clean` (после отката
из шага 6).

## Сводный чек-лист

- [ ] pre-commit установлен и активирован.
- [ ] `./gradlew ktlintCheck detekt` → SUCCESS, 0 новых нарушений.
- [ ] `npm run lint` в `webvue3` и `karaoke-public` → 0 новых нарушений.
- [ ] `docs/features/` содержит ровно 9 файлов с 6 секциями каждый.
- [ ] `lychee --offline` → 0 errors.
- [ ] `:karaoke-app:dokkaHtml` → SUCCESS, 0 warnings.
- [ ] pre-commit-хук ловит тестовое нарушение.
- [ ] baseline-stats показывает тенденцию к уменьшению.
- [ ] Constitution Check 5/5 PASS.
- [ ] Примеры PR за последний месяц: код фичи + документ = один PR.
- [ ] `git status` чистый.

Если все 11 пунктов ✅ — фича внедрена правильно и готова к использованию.

## Если что-то не работает

1. **Линтер не запускается** → проверить, что Gradle-плагин добавлен в
   `build.gradle.kts` (см. plan.md Project Structure), `npm run lint` есть
   в `package.json`.

2. **Baseline не сходится** → `tools/baseline-stats.sh` покажет, какие
   именно правила добавились; новые нарушения — блокер (FR-003).

3. **lychee находит битую ссылку** → найти документ, обновить ссылку
   (или удалить, если объект удалён).

4. **Dokka падает с warning** → найти warning, добавить KDoc, пересобрать.

5. **pre-commit не блокирует** → проверить `.pre-commit-config.yaml`,
   что `fail_fast: true` и хуки указаны верно.
