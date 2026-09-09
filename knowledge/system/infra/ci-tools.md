# Component: ci-tools (tools/)

> **Домен**: system (infrastructure)
> **Компонента**: каталог tools/ — CI/dev утилит проекта.

## Назначение

`tools/` содержит 38 скриптов для CI, dev workflow и quality gates.
Запускаются как pre-commit, в CI pipeline, или вручную.

## Каталог scripts (38 файлов)

### Quality gates (CI)

| Скрипт | Что | Когда |
|---|---|---|
| `check-kdoc-coverage.sh` | Проверяет KDoc покрытие public API | pre-commit, CI |
| `check-jsdoc-coverage.sh` | Проверяет JSDoc покрытие JS/Vue | pre-commit, CI |
| `check-ssot-impact.py` | SSoT impact gate: код → knowledge/ | pre-commit, CI |
| `check-knowledge-structure.sh` | Структура knowledge/ (9 проверок) | pre-commit, CI |
| `check-knowledge-cross-links.sh` | Cross-links между knowledge/ (Pass 340+ уже 329 ссылок) | pre-commit, CI |
| `check-enforcement.sh` | Constitution rules enforcement | CI |
| `check-eslint-baseline.sh` | ESLint baseline | CI |
| `baseline-stats.sh` | Baseline metrics | CI (informational) |
| `check-feature-doc.sh` | Per-feature документ (6 sections) | pre-commit |
| `check-audit-coverage.sh` | Audit coverage | pre-commit |
| `check-endpoint-field-coverage.sh` | Endpoint ↔ UI field coverage | CI |
| `check-songedit-field-coverage.sh` | SongEdit ↔ backend field coverage | CI |
| `lint-knowledge.py` | Lint knowledge/ (no emoji, etc.) | pre-commit |

### Documentation generation

| Скрипт | Что |
|---|---|
| `auto-kdoc.py` | Auto-generate KDoc для public API |
| `auto-jsdoc.py` | Auto-generate JSDoc для JS/Vue |
| `auto-kdoc-quality.py` | Quality-check KDoc |
| `generate-docs.sh` | Generate API docs через Dokka |
| `generate-eslint-baseline.sh` | Generate ESLint baseline |

### Spec Kit (Pass 340)

| Скрипт | Что |
|---|---|
| `specify-bootstrap.sh` | Резервирует NNN, создаёт feature-ветку |
| `spec-knowledge-preflight.sh` | Knowledge-first pre-flight gate (Pass 340) |
| `spec-kit/` | Подмодуль Spec Kit |

### Utilities

| Скрипт | Что |
|---|---|
| `install-dsh-integration.sh` | Install DSH integration |
| `install-tracker.sh` | Install OpenProject tracker |
| `reserve-branch-number.sh` | Reserve branch NNN |
| `analyze-prod-incident.sh` | Analyze prod incident (logs) |
| `cleanup-test-songs.sql` | SQL cleanup для тестовых песен |

### Configs

| Файл | Что |
|---|---|
| `endpoint-pairs.yml` | UI ↔ backend endpoint pairs |
| `check-endpoint-field-coverage.whitelist.yml` | Whitelist |
| `check-songedit-field-coverage.whitelist.yml` | Whitelist |
| `spec-kit/` | Spec Kit submodule |

## Конвенции

### `check-ssot-impact.py` — SSoT gate

**Алгоритм** (по KDoc):

1. Получает изменённые файлы (`git diff master...HEAD --name-only`).
2. Фильтрует значимые (исключает тесты, документацию, спецификации).
3. Для каждого значимого файла ищет правило в `.ssot-map.yml`.
4. Если правило есть — проверяет, что соответствующий файл в
   `knowledge/` тоже изменён.
5. Если правила нет — файл пропускается (advisory).

**Exit code**: 0 если OK, 1 если есть нарушения.

**NB**: Pass 340 добавил governance-knowledge-first. Этот gate
обеспечивает, что любое изменение в коде сопровождается обновлением
Knowledge.

### `.ssot-map.yml` — карта обязательных обновлений

```yaml
- code: "karaoke-app/**/model/SiteUser.kt"
  requires: "knowledge/domains/identity/components/dictionaries.md"
  reason: "SiteUser AR — UserRole, canSelfAssign и другие поля..."
```

Каждое правило: code (glob) → requires (knowledge path) → reason
(зачем). Используется в `check-ssot-impact.py`.

## Известные TODO

- [ ] **Какие из 38 скриптов в CI** — полный список.
- [ ] **`pre-commit` config** — какие хуки активны.
- [ ] **Coverage thresholds** — для kdoc/jsdoc (≥ 50%?).
- [ ] **`endpoint-pairs.yml`** — что в нём сейчас.

## Связь с другими компонентами

- **AGENTS.md** — упоминает pre-commit hooks и CI 7/7.
- **Constitution.md** — define what CI enforces.

## Changelog

- **Pass 353** (2026-09-09): Initial. Автор: agent (Karaoke).