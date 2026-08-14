# tools/ — Index of operational scripts

> Скрипты для CI, LiveDocs валидации, поиска, и операционного обслуживания.
> Все скрипты — POSIX bash + стандартные Unix-утилиты, без новых зависимостей.

## LiveDocs валидация и поиск

| Скрипт | Назначение |
|--------|------------|
| [`check-livedocs-structure.sh`](check-livedocs-structure.sh) | CI gate: 7 проверок структуры (директории, ≥5 фич, ≥5 BC, L1+L2+L3, frontmatter, AGENTS.md ≤ 100 строк, CI integration). **Запускается в GitHub Actions + pre-commit.** |
| [`check-livedocs-cross-links.sh`](check-livedocs-cross-links.sh) | Проверяет 814+ cross-links (`../X.md` и `related:`). **Запускается в GitHub Actions + pre-commit.** |
| [`check-livedocs-external-links.sh`](check-livedocs-external-links.sh) | Проверяет ВНЕШНИЕ ссылки (https://) в LiveDocs через lychee (advisory) + curl (strict). **Запускается в GitHub Actions.** |
| [`search-livedocs.sh`](search-livedocs.sh) | grep wrapper для AI-агентов и людей. Поиск query по LiveDocs с фильтром по типу/пути. **Интерактивный** (не CI). |
| [`test-livedocs.sh`](test-livedocs.sh) | Self-test для всех LiveDocs-check скриптов. 12 тестовых сценариев: реальный LiveDocs, временный каталог, негативные сценарии, syntax-check. Запускать перед commit. |

## Код и CI (прочие проекты)

| Скрипт | Назначение |
|--------|------------|
| `check-kdoc-coverage.sh` | KDoc ≥ 50% (Constitution Principle VI / FR-006). |
| `check-jsdoc-coverage.sh` | JSDoc ≥ 50%. |
| `check-eslint-baseline.sh` | ESLint baseline-aware для `webvue3` / `karaoke-public`. |
| `check-feature-doc.sh` | Per-feature документация (структура + slug == имя файла). |
| `baseline-stats.sh` | Baseline counters (informational). |
| `generate-docs.sh` | Генерация Dokka + typedoc. |
| `check-censored-public.sh` | Smoke-test цензурирования на `karaoke-web` без `karaoke-app`. |
| `check-audit-coverage.sh` | 100% audit coverage. |

## Резерв (build / deploy)

| Скрипт | Назначение |
|--------|------------|
| `deploy_web.sh`, `deploy_public.sh` | Деплой на прод (rsync + nginx). |
| `do.sh` | docker-compose build/start. |
| `build-lock.sh` | Сериализация параллельных Gradle-сборок (flock). |
| `check-stats-connection-leak.sh` | Smoke-test для поиска connection leak (см. [runbooks/how-to-debug-connection-leak.md](../livedocs/runbooks/how-to-debug-connection-leak.md)). |
| `lint-*.sh` | Прочие линтеры/проверки (не-LiveDocs). |

## Утилиты для новых скриптов

Примерный шаблон POSIX bash:

```bash
#!/usr/bin/env bash
set -uo pipefail  # НЕ set -e (для CI-сборщиков failures нужен накопительный эффект)

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

FAIL=0
# ... проверки ...

if [ "$FAIL" -eq 0 ]; then
  echo "OK"
  exit 0
else
  echo "FAILED: $FAIL check(s)"
  exit 1
fi
```

`chmod +x <script>` обязателен.

## Conventions

- **Имя**: kebab-case (`check-foo-bar.sh`, НЕ `CheckFooBar.sh`).
- **Shebang**: `#!/usr/bin/env bash` (на alpine-bash тоже работает).
- **Output**: наглядный + номера failure (`$FAIL=$(($FAIL+1))`).
- **Exit code**: 0 если OK, 1 если FAIL, 2 если ошибка использования.
- **README-style help**: `--help` flag.
- **Robust parse**: кавычки, "set -uo pipefail", комментарии для non-trivial.

## Когда добавлять новый скрипт

1. Задача повторяется ≥ 2 раз — выделить в скрипт.
2. Скрипт проверяет что-то — должен иметь exit code, понятный output.
3. Документируйте здесь (таблица выше) — короткое описание.

См. также `livedocs/runbooks/how-to-update-livedocs.md` — как sync кода
с LiveDocs при изменениях.