# Contract: Baseline-файлы линтеров

**Branch**: `001-code-standards-docs` | **Phase**: 1 | **Date**: 2026-07-20

Контракт формата baseline-файлов для `ktlint`, `detekt`, `eslint`.
Baseline-файл фиксирует известные нарушения, чтобы CI не валился на старом
коде; baseline уменьшается по мере рефакторинга (целевой темп: ≥10%/мес,
SC-002).

## Файлы

| Линтер | Файл baseline | Формат |
|--------|---------------|--------|
| `ktlint` | `config/ktlint/baseline.xml` | XML (встроенный в ktlint) |
| `detekt` | `config/detekt/baseline.xml` | XML (встроенный в detekt) |
| `eslint` (webvue3) | `webvue3/.eslint-baseline.json` | JSON-массив |
| `eslint` (karaoke-public) | `karaoke-public/.eslint-baseline.json` | JSON-массив |

## Формат ktlint baseline (`config/ktlint/baseline.xml`)

```xml
<?xml version="1.0" ?>
<baseline version="1.0">
    <file name="src/main/kotlin/com/example/Foo.kt">
        <error line="42" column="1" source="standard:no-wildcard-imports"
               message="Wildcard import" />
    </file>
    ...
</baseline>
```

**Генерация**:
```bash
./gradlew ktlintGenerateBaseline
# Создаёт/обновляет config/ktlint/baseline.xml
```

**Использование** (через `build.gradle.kts`):
```kotlin
ktlint {
    baseline = file("config/ktlint/baseline.xml")
    filter { exclude("**/build/**") }
}
```

## Формат detekt baseline (`config/detekt/baseline.xml`)

```xml
<?xml version="1.0" ?>
<SmellBaseline>
    <Manually>
        <ID>LongMethod:Foo.kt$Bar$baz</ID>
        <Signature>com.example.Bar#baz</Signature>
    </Manually>
    <Current>
        <ID>...</ID>
        <Signature>...</Signature>
    </Current>
</SmellBaseline>
```

**Генерация**:
```bash
./gradlew detektBaseline
```

**Использование**:
```kotlin
detekt {
    baseline = file("config/detekt/baseline.xml")
    input = files("src/main/kotlin", "src/test/kotlin")
}
```

## Формат ESLint baseline (JSON)

`webvue3/.eslint-baseline.json` и `karaoke-public/.eslint-baseline.json`:

```json
[
  {
    "ruleId": "vue/no-unused-vars",
    "file": "src/components/Songs/SongsTable.vue",
    "line": 42,
    "column": 7,
    "message": "'unusedProp' is defined but never used.",
    "severity": 2
  },
  ...
]
```

**Генерация** (через кастомный скрипт `tools/generate-eslint-baseline.sh`):

```bash
#!/bin/bash
# tools/generate-eslint-baseline.sh
cd webvue3
npx eslint --max-warnings 0 --format json . \
  | jq '[.[] | .messages[] | select(.ruleId != null) | {
      ruleId, file, line, column, message, severity
    }]' > .eslint-baseline.json
```

**Использование** (через кастомный скрипт `tools/check-eslint-baseline.sh`):

```bash
#!/bin/bash
# tools/check-eslint-baseline.sh
cd webvue3
npx eslint --max-warnings 0 --format json . | jq '[.[] | .messages[]]' > /tmp/current.json
# diff /tmp/current.json с .eslint-baseline.json
# Fail если в current.json есть записи, которых нет в baseline
# Warn если baseline содержит записи, которых нет в current.json (можно уменьшать)
```

## Инварианты

1. **Каждое нарушение в baseline подавлено** одним из способов:
   - `// ktlint-disable <ruleId> // intentional: <reason>` в коде, **или**
   - `// detekt-disable <ruleId> // intentional: <reason>`, **или**
   - комментарий в самом baseline-файле (XML-комментарий рядом с записью),
     **или**
   - для ESLint — `/* eslint-disable-next-line <ruleId> */` в коде
     с комментарием «intentional: <reason>».

2. **Размер baseline уменьшается**: diff между двумя последовательными
   baseline-файлами в git history показывает, сколько нарушений исправлено.
   Скрипт `tools/baseline-stats.sh` парсит baseline и возвращает count;
   CI сравнивает с предыдущим релизом (через git tag).

3. **Рост baseline > 0%** (новых нарушений больше, чем исправлено) →
   CI блокирует merge (нарушение SC-002).

4. **Линтеры НЕ в горячем пути `bootJar`**: baseline-файлы подключаются
   через отдельные Gradle-tasks (`ktlintCheck`, `detekt`) и npm-scripts
   (`lint`), которые запускаются в pre-commit и опционально в CI, но НЕ
   в `build`/`bootJar` (см. plan.md, раздел Constraints).

5. **Пути в baseline — относительные** (от корня репозитория или модуля).
   Абсолютные пути ломаются при переносе проекта.

## Скрипты

| Скрипт | Назначение |
|--------|------------|
| `tools/generate-eslint-baseline.sh` | Генерация baseline для ESLint (нет встроенного) |
| `tools/check-eslint-baseline.sh` | Проверка, что новых нарушений сверх baseline нет |
| `tools/baseline-stats.sh` | Считает count нарушений, печатает diff с прошлым релизом |
| `tools/check-enforcement.sh` | Парсит `CONTRIBUTING.md`, проверяет, что MUST-правила с `enforcedBy ≠ code-review-only` покрыты baseline или дают 0 нарушений |

## Эволюция контракта

- Изменение baseline-файлов — через PR (одна фича / один рефакторинг =
  одно уменьшение).
- Удаление baseline-файла — когда count = 0; тогда baseline не нужен.
- Изменение формата (миграция, например, на ESLint flat-config) — через
  governance, semver MINOR для `constitution.md` (если меняется API линтера).
