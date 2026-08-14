# Quickstart: LiveDocs Validation

**Phase**: 1 (design)
**Date**: 2026-08-14
**Branch**: `189-live-documentation`

## Назначение

Этот документ — **runnable validation guide** для фичи 189-live-documentation.
Описывает 8 сценариев, которые доказывают, что LiveDocs работают end-to-end
после первого merge. Каждый сценарий — это ручная или полуавтоматическая
проверка, которую можно выполнить за 1-3 минуты.

Сценарии построены по принципу **bottom-up**: сначала проверяем структуру
(папки, файлы), потом frontmatter, потом семантику (связи, размер), и наконец
интеграцию (CI, AI-агент).

## Prerequisites

- Репозиторий Karaoke склонирован и находится в ветке `189-live-documentation`
  (или в master после merge этой ветки).
- Установлен bash ≥ 4.0 (POSIX).
- AI-агент с доступом к репозиторию (opencode / Claude Code / Cursor).

## Setup commands

```bash
# Перейти в корень репозитория
cd /path/to/Karaoke

# Проверить текущую ветку (должна быть 189-live-documentation или master с merge)
git branch --show-current

# Убедиться, что директория livedocs/ существует
ls livedocs/
# Ожидаемый результат:
# README.md  INDEX.md  templates/  features/  domain/  architecture/
```

## Validation scenarios

### Сценарий 1: Структура каталога

**Цель**: убедиться, что все обязательные директории и файлы-манифесты существуют.

**Команды**:
```bash
# Все обязательные директории
ls -d livedocs/{features,domain,architecture,templates}/
echo "---"
# Все обязательные файлы-манифесты
for f in livedocs/README.md livedocs/INDEX.md \
         livedocs/features/README.md livedocs/domain/README.md \
         livedocs/architecture/README.md livedocs/templates/README.md; do
  test -f "$f" && echo "OK: $f" || echo "MISSING: $f"
done
```

**Expected outcome**: 6 строк `OK: ...`, 0 строк `MISSING: ...`.

**Соответствует**: FR-001, FR-002, FR-003, FR-007 (манифесты).

---

### Сценарий 2: Минимум 5 фич мигрировано

**Цель**: убедиться, что в `livedocs/features/` есть ≥ 5 сводок (SC-006).

**Команды**:
```bash
ls -1 livedocs/features/*.md | grep -v README.md | wc -l
echo "---"
ls -1 livedocs/features/*.md | grep -v README.md
```

**Expected outcome**: первая команда возвращает `≥ 5`. Вторая команда выводит
список из 5+ файлов: `182-...md`, `184-...md`, `185-...md`, `186-...md`,
`187-...md` (или других, если пользователь выбрал иные).

**Соответствует**: SC-006 (≥ 5 фич), US4 (миграция существующих спек).

---

### Сценарий 3: Минимум 5 bounded context'ов описаны

**Цель**: убедиться, что в `livedocs/domain/` есть ≥ 5 контекстов (SC-007).

**Команды**:
```bash
ls -1 livedocs/domain/*.md | grep -v README.md | wc -l
echo "---"
ls -1 livedocs/domain/*.md | grep -v README.md
echo "---"
# Каждый файл содержит секцию Aggregate Roots
for f in livedocs/domain/*.md; do
  if [ "$(basename $f)" != "README.md" ]; then
    grep -q '^## Aggregate Roots' "$f" && echo "OK: $f" || echo "MISSING AR: $f"
  fi
done
```

**Expected outcome**: первая команда возвращает `≥ 5`. Список — 5+ файлов
(`catalog.md`, `processing.md`, `publishing.md`, `identity.md`, `editorial.md`).
Все 5 файлов содержат `## Aggregate Roots` (DDD-требование).

**Соответствует**: SC-007 (≥ 5 bounded contexts), US6 (DDD ubiquitous language).

---

### Сценарий 4: Все 3 уровня C4 реализованы

**Цель**: убедиться, что в `livedocs/architecture/` есть L1, L2, L3 (SC-008).

**Команды**:
```bash
for level in L1-system-context.md L2-containers.md L3-components.md; do
  f="livedocs/architecture/$level"
  test -f "$f" && echo "OK: $f" || echo "MISSING: $f"
done
echo "---"
# Каждый файл содержит Mermaid-блок
for level in L1-system-context.md L2-containers.md L3-components.md; do
  f="livedocs/architecture/$level"
  if [ -f "$f" ]; then
    grep -q '^```mermaid' "$f" && echo "OK mermaid: $f" || echo "NO mermaid: $f"
  fi
done
```

**Expected outcome**: 3 строки `OK: ...`, 3 строки `OK mermaid: ...`.

**Соответствует**: SC-008 (все 3 уровня C4), US7 (C4 диаграммы).

---

### Сценарий 5: Frontmatter валиден во всех LiveDocs

**Цель**: убедиться, что каждый файл (кроме README и templates) имеет валидный
frontmatter (`status`, `slug`).

**Команды**:
```bash
FAIL=0
for f in $(find livedocs -name '*.md' -not -name 'README.md' -not -path '*/templates/*'); do
  # Проверка 1: начинается с ---
  if ! head -1 "$f" | grep -q '^---$'; then
    echo "FAIL (no frontmatter): $f"
    FAIL=$((FAIL+1))
    continue
  fi
  # Проверка 2: содержит status в первых 10 строках
  if ! head -10 "$f" | grep -q '^status:'; then
    echo "FAIL (no status): $f"
    FAIL=$((FAIL+1))
    continue
  fi
  # Проверка 3: содержит slug в первых 15 строках
  if ! head -15 "$f" | grep -q '^slug:'; then
    echo "FAIL (no slug): $f"
    FAIL=$((FAIL+1))
    continue
  fi
done
echo "Total failures: $FAIL"
```

**Expected outcome**: `Total failures: 0`. Каждый файл имеет frontmatter
с `status` и `slug`.

**Соответствует**: FR-018 (Markdown + YAML frontmatter), frontmatter-schema.md
(контракт frontmatter).

---

### Сценарий 6: AGENTS.md сокращён до ≤ 100 строк

**Цель**: убедиться, что AGENTS.md сократился до ≤ 100 строк (SC-002).

**Команды**:
```bash
wc -l AGENTS.md
```

**Expected outcome**: выводит число ≤ 100 (было ~230, см. AGENTS.md v1.7.1).

**Соответствует**: SC-002 (AGENTS.md ≤ 100 строк), D-7 (стратегия миграции).

---

### Сценарий 7: CI запускает валидацию LiveDocs

**Цель**: убедиться, что в GitHub Actions `lint.yml` добавлен шаг проверки.

**Команды**:
```bash
# Проверить, что в .github/workflows/lint.yml есть шаг check-livedocs
grep -l 'check-livedocs-structure' .github/workflows/lint.yml && echo "OK" || echo "MISSING"
echo "---"
# Проверить, что скрипт существует
test -x tools/check-livedocs-structure.sh && echo "OK executable" || echo "NOT executable"
```

**Expected outcome**: `OK` и `OK executable`.

Дополнительная проверка — на GitHub: открыть PR с изменением, увидеть зелёный
шаг `check-livedocs` в CI (если PR уже создан).

**Соответствует**: FR-011 (скрипт валидации), FR-015 (CI запуск), D-6 (bash-скрипт),
D-8 (один шаг в lint.yml).

---

### Сценарий 8: AI-агент читает LiveDocs первым при старте

**Цель**: убедиться, что правило «читать LiveDocs первым» зафиксировано в
`AGENTS.md` и AI-агент следует ему.

**Шаги**:
1. Открыть свежую сессию AI-агента (opencode / Claude Code / Cursor).
2. Дать задачу: «Опиши модуль Song».
4. **Ожидаемое поведение**: агент в первую очередь читает
   `livedocs/README.md` + `livedocs/INDEX.md`, затем переходит к
   `livedocs/domain/catalog.md`, **а не** к `docs/features/*.md` или
   `specs/NNN-*/spec.md`.

**Как проверить**:
- Открыть лог сессии (если агент логирует tool calls).
- Найти обращения к файлам — первые 2-3 должны быть в `livedocs/`.
- Если агент сразу лезет в `AGENTS.md` или `docs/` — правило не сработало.

**Соответствует**: SC-001 (≤ 5K токенов на онбординг), SC-009 (агент НЕ
обращается к устаревшим спекам первым), US1 (AI-агент при старте читает
LiveDocs).

**Примечание**: этот сценарий — качественный, не автоматический. Лучшая
проверка — открыть AI-агента с этой задачей и понаблюдать за tool calls.

---

## Run-all скрипт (для CI)

Все автоматические сценарии (1-7) можно объединить в один скрипт
`tools/check-livedocs-structure.sh`:

```bash
#!/usr/bin/env bash
# tools/check-livedocs-structure.sh
# CI-валидация структуры LiveDocs (см. specs/189-live-documentation/quickstart.md).
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

FAIL=0

check_file() {
  test -f "$1" || { echo "MISSING: $1"; FAIL=$((FAIL+1)); }
}

check_dir() {
  test -d "$1" || { echo "MISSING DIR: $1"; FAIL=$((FAIL+1)); }
}

echo "[1/7] Проверка структуры..."
for d in livedocs/{features,domain,architecture,templates}; do check_dir "$d"; done
for f in livedocs/README.md livedocs/INDEX.md \
         livedocs/features/README.md livedocs/domain/README.md \
         livedocs/architecture/README.md livedocs/templates/README.md; do
  check_file "$f"
done

echo "[2/7] Проверка features/ (≥ 5)..."
features_count=$(ls -1 livedocs/features/*.md 2>/dev/null | grep -v README.md | wc -l)
test "$features_count" -ge 5 || { echo "NEED ≥ 5 features, found $features_count"; FAIL=$((FAIL+1)); }

echo "[3/7] Проверка domain/ (≥ 5)..."
domain_count=$(ls -1 livedocs/domain/*.md 2>/dev/null | grep -v README.md | wc -l)
test "$domain_count" -ge 5 || { echo "NEED ≥ 5 domains, found $domain_count"; FAIL=$((FAIL+1)); }

echo "[4/7] Проверка C4 L1/L2/L3..."
for level in L1-system-context.md L2-containers.md L3-components.md; do
  f="livedocs/architecture/$level"
  test -f "$f" || { echo "MISSING: $f"; FAIL=$((FAIL+1)); }
  if [ -f "$f" ]; then
    grep -q '^```mermaid' "$f" || { echo "NO MERMAID: $f"; FAIL=$((FAIL+1)); }
  fi
done

echo "[5/7] Проверка frontmatter..."
for f in $(find livedocs -name '*.md' -not -name 'README.md' -not -path '*/templates/*'); do
  head -1 "$f" | grep -q '^---$' || { echo "NO FRONTMATTER: $f"; FAIL=$((FAIL+1)); }
  head -10 "$f" | grep -q '^status:' || { echo "NO STATUS: $f"; FAIL=$((FAIL+1)); }
  head -15 "$f" | grep -q '^slug:' || { echo "NO SLUG: $f"; FAIL=$((FAIL+1)); }
done

echo "[6/7] Проверка AGENTS.md (≤ 100 строк)..."
agents_lines=$(wc -l < AGENTS.md)
test "$agents_lines" -le 100 || { echo "AGENTS.md = $agents_lines lines, need ≤ 100"; FAIL=$((FAIL+1)); }

echo "[7/7] Проверка CI integration..."
grep -q 'check-livedocs-structure' .github/workflows/lint.yml || { echo "CI NOT CONFIGURED"; FAIL=$((FAIL+1)); }

echo "---"
if [ "$FAIL" -eq 0 ]; then
  echo "OK: LiveDocs structure valid"
  exit 0
else
  echo "FAILED: $FAIL checks"
  exit 1
fi
```

Сохранить этот скрипт в `tools/check-livedocs-structure.sh`, сделать
`chmod +x tools/check-livedocs-structure.sh`, добавить шаг в `.github/workflows/lint.yml`:

```yaml
- name: Check LiveDocs structure
  run: bash tools/check-livedocs-structure.sh
```

---

## Done when

Этот quickstart готов, когда:
- [x] Все 8 сценариев описаны.
- [x] Все сценарии ссылаются на конкретные FR / SC из `spec.md`.
- [x] Есть готовый bash-скрипт, объединяющий сценарии 1-7.
- [x] Сценарий 8 (AI-агент) описан как качественный (не автоматический).

## Следующая фаза

После успешной валидации всех сценариев — переход к `/speckit.tasks` для
декомпозиции в конкретные задачи имплементации (T01: создать `livedocs/README.md`,
T02: создать `livedocs/INDEX.md`, T03: создать `livedocs/templates/`, и т.д.).