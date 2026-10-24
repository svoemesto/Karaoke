#!/usr/bin/env bash
# Guard: в Kotlin-аннотациях @Value не должно быть потерянного '$'.
#
# Дефект (2026-10-24, волна A): `@Value($$"{свойство}")` — без '$' внутри кавычек —
# в Kotlin компилируется в ЛИТЕРАЛ `{свойство}`. Spring не находит плейсхолдер и
# внедряет сам текст: сравнение секретов всегда ложно, проверка капчи отклоняет всё.
# Юнит-тесты этого не ловили: контроллеры создаются напрямую, минуя Spring-контекст.
#
# Корректные формы в проекте:
#   @Value("\${свойство}")        — Kotlin-экранирование (основная)
#   @Value($$"\${свойство}")      — устаревшая форма, тоже корректна
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

status=0
for module in karaoke-web karaoke-app; do
  dir="$module/src"
  [ -d "$dir" ] || continue
  # @Value($$"..."), внутри кавычек нет '${' → плейсхолдер потерян
  # аннотация должна начинаться со строки (комментарии с примером — не считаются)
  offenders="$(grep -rn '^[[:space:]]*@Value(\$\$"' --include='*.kt' "$dir" | grep -v '\${' || true)"
  if [ -n "$offenders" ]; then
    echo "FAIL: в $module есть @Value без плейсхолдера (значение внедрится литералом):" >&2
    echo "$offenders" >&2
    status=1
  fi
done

if [ "$status" -eq 0 ]; then
  echo "PASS: все @Value содержат настоящий Spring-плейсхолдер"
fi
exit "$status"
