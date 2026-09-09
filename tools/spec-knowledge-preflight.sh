#!/usr/bin/env bash
# tools/spec-knowledge-preflight.sh
#
# Knowledge-first pre-flight скрипт для /speckit.specify.
# Вызывается ПЕРЕД tools/specify-bootstrap.sh.
#
# Зачем (см. AGENTS.md MUST #0, Constitution Principle IX, прецедент
# 2026-09-09 — spec #339):
#   Без Knowledge-first агент рискует изобрести форму того, что уже
#   задокументировано в knowledge/, или нарушить принятое решение
#   (например, local-0003-shared-minio-image-cache.md уже зафиксировал
#   MinIO+TTL для image-cache, а агент spec #339 предложил БД-таблицу).
#
# Использование:
#   bash tools/spec-knowledge-preflight.sh <<'KNOWLEDGE'
#   knowledge/domains/caching/domain.md — bounded context кеширования
#   knowledge/domains/caching/components/caching-patterns.md — паттерны
#   knowledge/adr/local-0003-shared-minio-image-cache.md — MinIO+TTL прецедент
#   KNOWLEDGE
#
#   → exit 0 если pre-flight валиден (≥1 ссылка, все файлы существуют,
#     не менее 3 grep-запросов зафиксировано).
#
#   bash tools/spec-knowledge-preflight.sh
#   → без stdin: usage в stderr, exit 2.
#
# Env:
#   SPECIFY_STRICT — если задан и != "0", pre-flight становится
#     failure-stop'ом для tools/specify-bootstrap.sh (через exit 3,
#     bootstrap должен проверить).

set -euo pipefail

REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
cd "$REPO_ROOT"

# Читаем stdin в массив (если есть).
# Используем `read -r FIRST` для определения наличия данных — `[ -t0 ]`
# не работает, потому что в DSH fd0 всегда «терминал», а `[ -s /dev/stdin ]`
# возвращает false для pipe. Реальная проверка — попытаться прочесть.
read -r FIRST_LINE || FIRST_LINE=""
if [ -z "$FIRST_LINE" ]; then
  cat >&2 <<'EOF'
Usage: bash tools/spec-knowledge-preflight.sh <<'KNOWLEDGE'
knowledge/domains/<X>/domain.md — зачем прочитан
knowledge/domains/<X>/components/<Y>.md — зачем прочитан
knowledge/adr/local-NNNN-<slug>.md — принятое решение, влияющее на фичу
KNOWLEDGE

Knowledge-first pre-flight ОБЯЗАТЕЛЕН перед tools/specify-bootstrap.sh
(см. AGENTS.md MUST #0, Constitution Principle IX).

EOF
  exit 2
fi

# Собираем массив: первая строка + то, что осталось в stdin.
LINES=("$FIRST_LINE")
while IFS= read -r LINE; do
  LINES+=("$LINE")
done

# Проверка 1: ≥ 1 ссылка на knowledge/.
COUNT=$(printf '%s\n' "${LINES[@]}" | grep -cE '^knowledge/' || true)
if [ "$COUNT" -lt 1 ]; then
  echo "ERROR: pre-flight не содержит ни одной ссылки на knowledge/." >&2
  echo "  Минимум 1 ссылка на knowledge/<путь>.md обязательна." >&2
  exit 3
fi

# Проверка 2: все указанные файлы существуют.
MISSING=0
for LINE in "${LINES[@]}"; do
  [[ "$LINE" =~ ^knowledge/ ]] || continue
  # Извлекаем первый токен — путь.
  REL_PATH="${LINE%% *}"
  # Если файл не существует И это явный путь с расширением .md — ошибка.
  if [ ! -e "$REL_PATH" ]; then
    echo "ERROR: указанный файл '$REL_PATH' не существует." >&2
    MISSING=$((MISSING + 1))
  fi
done

if [ "$MISSING" -gt 0 ]; then
  echo "ERROR: $MISSING несуществующих путей в pre-flight." >&2
  echo "  Проверьте, что все knowledge/<...>.md файлы реально есть в репо." >&2
  exit 3
fi

# Проверка 3: рекомендуем (но не enforce'им) ≥ 3 grep-запросов. Сейчас
# grep-запросы не передаются через stdin — этот шаг остаётся за чек-листом
# спеки (Knowledge Compliance, см. .specify/templates/checklist-template.md).

echo "OK: Knowledge-first pre-flight пройден — $COUNT ссылок на knowledge/." >&2