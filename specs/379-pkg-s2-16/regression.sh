#!/usr/bin/env bash
# =====================================================================
# specs/379-pkg-s2-16/regression.sh — регрессионные проверки «до/после»
# для OpenProject #379 (PKG-S2-16, волна C, S2).
#
# Записи пакета: BR-571, BR-572, BR-593, BR-594, BR-595, BR-601.
#
# Как работает:
#   * «ДО»  — версия инструмента из базового коммита (BASE_REF, по умолчанию
#             534766da — master на момент старта пакета);
#   * «ПОСЛЕ» — версия из рабочего дерева;
#   * сценарии гоняются на ЗАГЛУШКАХ: локальный HTTP-сервер вместо registry,
#     подставные `docker`/`curl`/`npx`/`tracker.sh`. Никаких реальных
#     секретов, контейнеров, registry и трекера — только локальные процессы.
#
# Использование:
#   bash specs/379-pkg-s2-16/regression.sh
#
# Exit: 0 — все проверки прошли, 1 — есть расхождения.
# =====================================================================
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BASE_REF="${BASE_REF:-534766da}"
PKG_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

PASS=0
FAIL=0
ok()  { printf '  \033[0;32mPASS\033[0m  %s\n' "$*"; PASS=$((PASS + 1)); }
bad() { printf '  \033[0;31mFAIL\033[0m  %s\n' "$*"; FAIL=$((FAIL + 1)); }
check() { # описание, ожидание, факт
  if [ "$2" = "$3" ]; then ok "$1 = $3"; else bad "$1 — ожидали «$2», получили «$3»"; fi
}
note() { printf '        %s\n' "$*"; }

# Версия инструмента из базового коммита (путь относительно корня репо).
old_tool() { # $1=путь внутри tools/
  local rel="$1"
  local out="$WORK/old-$(basename "$rel")"
  git -C "$REPO_ROOT" show "${BASE_REF}:${rel}" > "$out" 2>/dev/null || return 1
  printf '%s' "$out"
}

banner() { printf '\n=== %s ===\n' "$*"; }

# =====================================================================
# BR-571 — check-do-sh-guards.sh не должен переписывать отслеживаемый Dockerfile
# =====================================================================
br571() {
  banner "BR-571 — отслеживаемый Dockerfile не переписывается (tools/check-do-sh-guards.sh)"

  # Фейковое дерево репозитория: deploy/do.sh «спит» (имитация долгой сборки),
  # чтобы прерывание пришлось ровно в момент сборки.
  local fake="$WORK/fake-repo"
  mkdir -p "$fake/tools" "$fake/deploy/karaoke-public"
  printf '#!/usr/bin/env bash\nsleep 60\n' > "$fake/deploy/do.sh"
  : > "$fake/deploy/do.env"
  cp "$REPO_ROOT/deploy/karaoke-public/Dockerfile" "$fake/deploy/karaoke-public/Dockerfile"

  local old new
  old="$(old_tool tools/check-do-sh-guards.sh)" || { bad "не найден $BASE_REF:tools/check-do-sh-guards.sh"; return; }
  cp "$old" "$fake/tools/check-do-sh-guards.sh"
  local orig_sum
  orig_sum="$(sha256sum "$fake/deploy/karaoke-public/Dockerfile" | cut -d' ' -f1)"

  # ДО: прерывание (SIGTERM) во время сборки.
  ( cd "$fake" && bash tools/check-do-sh-guards.sh >/dev/null 2>&1 ) &
  local pid=$!
  sleep 2
  kill -TERM "$pid" 2>/dev/null
  wait "$pid" 2>/dev/null
  local after_old
  after_old="$(sha256sum "$fake/deploy/karaoke-public/Dockerfile" | cut -d' ' -f1)"
  if [ "$after_old" != "$orig_sum" ]; then
    ok "ДО: дефект воспроизведён — после прерывания отслеживаемый Dockerfile изменён"
    note "$(head -1 "$fake/deploy/karaoke-public/Dockerfile")"
  else
    bad "ДО: дефект НЕ воспроизведён (старый скрипт устоял?) — проверка недействительна"
  fi

  # ПОСЛЕ: тот же сценарий с исправленным скриптом.
  cp "$REPO_ROOT/deploy/karaoke-public/Dockerfile" "$fake/deploy/karaoke-public/Dockerfile"
  rm -f "$fake/deploy/karaoke-public/Dockerfile.guardtest.bak"
  cp "$REPO_ROOT/tools/check-do-sh-guards.sh" "$fake/tools/check-do-sh-guards.sh"
  ( cd "$fake" && bash tools/check-do-sh-guards.sh >/dev/null 2>&1 ) &
  pid=$!
  sleep 2
  kill -TERM "$pid" 2>/dev/null
  wait "$pid" 2>/dev/null
  local after_new
  after_new="$(sha256sum "$fake/deploy/karaoke-public/Dockerfile" | cut -d' ' -f1)"
  check "ПОСЛЕ: отслеживаемый Dockerfile не изменён после прерывания" "$orig_sum" "$after_new"
  if [ -e "$fake/deploy/karaoke-public/Dockerfile.guardtest.bak" ]; then
    bad "ПОСЛЕ: остался мусорный .guardtest.bak"
  else
    ok "ПОСЛЕ: мусорный .guardtest.bak не остался"
  fi
}

# =====================================================================
# BR-572 — check-feature-doc.sh: отсутствующий документ = ошибка (exit 1)
# =====================================================================
br572() {
  banner "BR-572 — отсутствующий per-feature документ = ошибка (tools/check-feature-doc.sh)"

  local missing="$WORK/definitely-missing-doc.md"
  local old new rc
  old="$(old_tool tools/check-feature-doc.sh)" || { bad "нет старой версии"; return; }

  bash "$old" "$missing" >/dev/null 2>&1; rc=$?
  check "ДО: код возврата на несуществующем файле" "0" "$rc"

  bash "$REPO_ROOT/tools/check-feature-doc.sh" "$missing" >/dev/null 2>&1; rc=$?
  check "ПОСЛЕ: код возврата на несуществующем файле" "1" "$rc"

  # Контроль: реальные документы по-прежнему проходят.
  bash "$REPO_ROOT/tools/check-feature-doc.sh" "$REPO_ROOT"/docs/features/*.md >/dev/null 2>&1; rc=$?
  check "ПОСЛЕ: контроль — реальные документы валидны" "0" "$rc"
}

# =====================================================================
# BR-593 — registry-smoke.sh: проверка «неверный пароль» реально шлёт креды
# =====================================================================
br593() {
  banner "BR-593 — «неверный пароль → 401» реально отправляет креды (tools/registry-smoke.sh)"

  local port=15511 old rc
  old="$(old_tool tools/registry-smoke.sh)" || { bad "нет старой версии"; return; }

  # Режим weak: аноним → 401, но ЛЮБЫЕ креды → 200 (сломанный htpasswd).
  STUB_MODE=weak python3 "$PKG_DIR/stub-registry.py" "$port" 2>"$WORK/stub593.log" &
  local stub_pid=$!
  sleep 1.5

  REGISTRY_USER=smokeuser REGISTRY_PASSWORD=smokepass \
    bash "$old" "127.0.0.1:$port" --scheme http --no-upstream >"$WORK/smoke-old.out" 2>&1
  rc=$?
  check "ДО: exit на registry со сломанным htpasswd (ложный PASS)" "0" "$rc"

  : > "$WORK/stub593.log"
  REGISTRY_USER=smokeuser REGISTRY_PASSWORD=smokepass \
    bash "$REPO_ROOT/tools/registry-smoke.sh" "127.0.0.1:$port" --scheme http --no-upstream \
    >"$WORK/smoke-new.out" 2>&1
  rc=$?
  check "ПОСЛЕ: exit на registry со сломанным htpasswd (дефект пойман)" "1" "$rc"

  # В логе заглушки: у запроса проверки [3] должен быть Authorization-заголовок.
  if grep -q '^GET /v2/ Basic ' "$WORK/stub593.log"; then
    ok "ПОСЛЕ: запрос проверки [3] ушёл с Authorization-заголовком (креды отправлены)"
  else
    bad "ПОСЛЕ: запрос проверки [3] ушёл БЕЗ кредов — проверка по-прежнему дублирует [1]"
  fi

  kill "$stub_pid" 2>/dev/null
  wait "$stub_pid" 2>/dev/null
}

# =====================================================================
# BR-594 — registry-vendor.sh: PENDING не затирает пин в lock-файле
# =====================================================================
br594() {
  banner "BR-594 — дайджест-пин не затирается значением PENDING (tools/registry-vendor.sh)"

  local old lock digest
  old="$(old_tool tools/registry-vendor.sh)" || { bad "нет старой версии"; return; }

  # Заглушки: docker проходит, curl не отдаёт Docker-Content-Digest.
  mkdir -p "$WORK/stubbin"
  printf '#!/usr/bin/env bash\ncase "$1" in\n  image) echo "docker.io/library/nginx@sha256:1111"; exit 0;;\nesac\nexit 0\n' \
    > "$WORK/stubbin/docker"
  printf '#!/usr/bin/env bash\nexit 0\n' > "$WORK/stubbin/curl"
  chmod +x "$WORK/stubbin/docker" "$WORK/stubbin/curl"

  make_lock() { # $1=файл lock
    cat > "$1" <<'EOF'
| upstream_ref | our_ref | digest | checked_at |
|---|---|---|---|
| docker.io/library/nginx:stable | localhost:5500/mirror/library/nginx:stable | sha256:aaaa1111bbbb2222 | 2026-10-11 |
EOF
  }

  lock="$WORK/old.lock"; make_lock "$lock"
  PATH="$WORK/stubbin:$PATH" REGISTRY_USER=u REGISTRY_PASSWORD=p \
    bash "$old" --lock "$lock" --host localhost:5500 --update-lock >/dev/null 2>&1
  digest="$(awk -F'|' '/nginx:stable/ {gsub(/ /,"",$4); print $4}' "$lock")"
  check "ДО: что записано в колонку digest" "PENDING" "$digest"

  lock="$WORK/new.lock"; make_lock "$lock"
  PATH="$WORK/stubbin:$PATH" REGISTRY_USER=u REGISTRY_PASSWORD=p \
    bash "$REPO_ROOT/tools/registry-vendor.sh" --lock "$lock" --host localhost:5500 --update-lock >/dev/null 2>&1
  digest="$(awk -F'|' '/nginx:stable/ {gsub(/ /,"",$4); print $4}' "$lock")"
  check "ПОСЛЕ: пин в колонке digest сохранён" "sha256:aaaa1111bbbb2222" "$digest"

  # --verify при digest=PENDING: раньше skip (exit 0), теперь ошибка (exit 1).
  cat > "$WORK/pending.lock" <<'EOF'
| upstream_ref | our_ref | digest | checked_at |
|---|---|---|---|
| docker.io/library/nginx:stable | localhost:5500/mirror/library/nginx:stable | PENDING | 2026-10-24 |
EOF
  local rc
  PATH="$WORK/stubbin:$PATH" REGISTRY_USER=u REGISTRY_PASSWORD=p \
    bash "$old" --lock "$WORK/pending.lock" --host localhost:5500 --verify >/dev/null 2>&1; rc=$?
  check "ДО: --verify при digest=PENDING (пин молча не проверяется)" "0" "$rc"
  PATH="$WORK/stubbin:$PATH" REGISTRY_USER=u REGISTRY_PASSWORD=p \
    bash "$REPO_ROOT/tools/registry-vendor.sh" --lock "$WORK/pending.lock" --host localhost:5500 --verify \
    >/dev/null 2>&1; rc=$?
  check "ПОСЛЕ: --verify при digest=PENDING (ошибка проверки)" "1" "$rc"
}

# =====================================================================
# BR-593/BR-594 — доказательство guard'ом tools/check-secret-in-argv.sh
#   ДО  (tools из BASE_REF + baseline без 4 записей) → guard ВИДИТ нарушения, exit 1;
#   ПОСЛЕ (tools из рабочего дерева + тот же baseline) → нарушений нет, exit 0.
# =====================================================================
br593_594_guard() {
  banner "BR-593/BR-594 — guard check-secret-in-argv: ДО видит нарушения, ПОСЛЕ — нет"

  local tree="$WORK/guard-tree" out rc n
  mkdir -p "$tree/tools"
  git -C "$REPO_ROOT" show "$BASE_REF:tools/registry-smoke.sh" > "$tree/tools/registry-smoke.sh" || {
    bad "не удалось извлечь $BASE_REF:tools/registry-smoke.sh"; return; }
  git -C "$REPO_ROOT" show "$BASE_REF:tools/registry-vendor.sh" > "$tree/tools/registry-vendor.sh" || {
    bad "не удалось извлечь $BASE_REF:tools/registry-vendor.sh"; return; }
  cp "$REPO_ROOT/tools/check-secret-in-argv.sh" "$tree/tools/"
  # Baseline берём из рабочего дерева — в нём 4 registry-записи уже убраны.
  cp "$REPO_ROOT/tools/check-secret-in-argv.baseline" "$tree/tools/"
  ( cd "$tree" && git init -q . && git add -A \
    && git -c user.email=guard@test -c user.name=guard commit -qm stubs ) >/dev/null 2>&1

  out="$(bash "$tree/tools/check-secret-in-argv.sh" 2>&1)"; rc=$?
  n="$(printf '%s\n' "$out" | grep -c 'tools/registry-')"
  check "ДО (старые tools + новый baseline): exit guard" "1" "$rc"
  check "ДО: guard перечислил записей registry-smoke/registry-vendor" "5" "$n"
  note "$(printf '%s\n' "$out" | grep 'tools/registry-' | sed 's/^ *//' | cut -c1-90 | head -4)"

  cp "$REPO_ROOT/tools/registry-smoke.sh" "$REPO_ROOT/tools/registry-vendor.sh" "$tree/tools/"
  ( cd "$tree" && git add -A \
    && git -c user.email=guard@test -c user.name=guard commit -qm fix ) >/dev/null 2>&1
  out="$(bash "$tree/tools/check-secret-in-argv.sh" 2>&1)"; rc=$?
  check "ПОСЛЕ (исправленные tools + тот же baseline): exit guard" "0" "$rc"
  if printf '%s\n' "$out" | grep -q 'секреты не передаются через argv'; then
    ok "ПОСЛЕ: guard не видит ни одного нарушения в registry-smoke/registry-vendor"
  else
    bad "ПОСЛЕ: guard всё ещё что-то сообщает: $(printf '%s\n' "$out" | head -3 | tr '\n' ' ')"
  fi

  bash "$REPO_ROOT/tools/check-secret-in-argv.sh" --self-test >/dev/null 2>&1; rc=$?
  check "self-test guard'а (все правила ловят плохое и не ловят хорошее)" "0" "$rc"
}

# =====================================================================
# BR-595 — generate-eslint-baseline.sh не обнуляет отслеживаемый baseline
# =====================================================================
br595() {
  banner "BR-595 — baseline ESLint не обнуляется при падении eslint (tools/generate-eslint-baseline.sh)"

  local old mod rc size_before size_after
  old="$(old_tool tools/generate-eslint-baseline.sh)" || { bad "нет старой версии"; return; }

  mod="$WORK/mod"; mkdir -p "$mod/node_modules" "$WORK/stubnpx"
  printf '#!/usr/bin/env bash\necho "eslint: Cannot read config file" >&2\nexit 2\n' > "$WORK/stubnpx/npx"
  chmod +x "$WORK/stubnpx/npx"
  local baseline='[{"ruleId":"x","file":"a.js","line":1,"column":1,"message":"m","severity":1}]'

  printf '%s' "$baseline" > "$mod/.eslint-baseline.json"
  ( cd "$WORK" && PATH="$WORK/stubnpx:$PATH" bash "$old" mod >/dev/null 2>&1 ); rc=$?
  size_before="$(stat -c%s "$mod/.eslint-baseline.json")"
  check "ДО: размер baseline после падения eslint" "0" "$size_before"
  note "ДО: код возврата скрипта = $rc"

  printf '%s' "$baseline" > "$mod/.eslint-baseline.json"
  ( cd "$WORK" && PATH="$WORK/stubnpx:$PATH" bash "$REPO_ROOT/tools/generate-eslint-baseline.sh" mod >/dev/null 2>&1 ); rc=$?
  size_after="$(stat -c%s "$mod/.eslint-baseline.json")"
  check "ПОСЛЕ: baseline не тронут (размер)" "${#baseline}" "$size_after"
  check "ПОСЛЕ: код возврата скрипта" "1" "$rc"

  # Контроль: успешный путь не сломан — eslint отработал (код 1 = «есть нарушения»),
  # baseline перезаписывается целиком из его вывода.
  mkdir -p "$WORK/stubnpx-ok"
  cat > "$WORK/stubnpx-ok/npx" <<EOF
#!/usr/bin/env bash
# Формат ESLint: массив результатов, у каждого — messages[].
printf '[{"filePath":"$mod/a.js","messages":[{"ruleId":"no-unused-vars","line":1,"column":2,"message":"m","severity":1}]}]'
exit 1
EOF
  chmod +x "$WORK/stubnpx-ok/npx"
  printf '%s' "$baseline" > "$mod/.eslint-baseline.json"
  ( cd "$WORK" && PATH="$WORK/stubnpx-ok:$PATH" bash "$REPO_ROOT/tools/generate-eslint-baseline.sh" mod >/dev/null 2>&1 ); rc=$?
  check "ПОСЛЕ: успешный путь — код возврата скрипта" "0" "$rc"
  check "ПОСЛЕ: успешный путь — в baseline записан 1 нарушитель" "1" \
    "$(jq 'length' "$mod/.eslint-baseline.json" 2>/dev/null)"
}

# =====================================================================
# BR-601 — tracker-poll.sh: ошибка трекера не выглядит как «задач нет»
# =====================================================================
br601() {
  banner "BR-601 — ошибка трекера возвращается кодом, а не «задач нет» (tools/tracker-poll.sh)"

  local fake="$WORK/fake-tracker"
  mkdir -p "$fake/tools"
  cp "$REPO_ROOT/tools/tracker-lib.sh" "$fake/tools/tracker-lib.sh"
  cp "$REPO_ROOT/tools/tracker-poll.sh" "$fake/tools/tracker-poll.sh"
  cat > "$fake/.env.local-tracker" <<'EOF'
TRACKER_URL=http://127.0.0.1:9/openproject
TRACKER_USER=ai-agent
TRACKER_API_TOKEN=dummy-token-not-a-secret
EOF
  local old rc
  old="$(old_tool tools/tracker-poll.sh)" || { bad "нет старой версии"; return; }
  cp "$old" "$fake/tools/tracker-poll-old.sh"

  cat > "$fake/tools/tracker.sh" <<'EOF'
#!/usr/bin/env bash
echo "ERROR: OpenProject недоступен" >&2
exit 2
EOF
  chmod +x "$fake/tools/tracker.sh"

  ( cd "$fake" && bash tools/tracker-poll-old.sh >"$WORK/poll-old.out" 2>&1 ); rc=$?
  check "ДО: код возврата при недоступном трекере" "1" "$rc"
  if grep -q 'Все задачи закрыты' "$WORK/poll-old.out"; then
    ok "ДО: дефект воспроизведён — вывод утверждает «Все задачи закрыты»"
  else
    bad "ДО: дефект не воспроизведён (нет строки «Все задачи закрыты»)"
  fi

  ( cd "$fake" && bash tools/tracker-poll.sh >"$WORK/poll-new.out" 2>&1 ); rc=$?
  check "ПОСЛЕ: код возврата при недоступном трекере" "2" "$rc"
  if grep -q 'Все задачи закрыты' "$WORK/poll-new.out"; then
    bad "ПОСЛЕ: вывод всё ещё утверждает «Все задачи закрыты»"
  else
    ok "ПОСЛЕ: вывод не утверждает «Все задачи закрыты»"
  fi

  # Токен истёк → код 3.
  cat > "$fake/tools/tracker.sh" <<'EOF'
#!/usr/bin/env bash
echo "ERROR: TRACKER_API_TOKEN истёк" >&2
exit 3
EOF
  ( cd "$fake" && bash tools/tracker-poll.sh >/dev/null 2>&1 ); rc=$?
  check "ПОСЛЕ: код возврата при истёкшем токене" "3" "$rc"

  # Контроль: рабочий путь не сломан — трекер доступен, открытых задач нет.
  cat > "$fake/tools/tracker.sh" <<'EOF'
#!/usr/bin/env bash
printf 'ID\tSubject\tStatus\tAssignee\tType\n'
EOF
  ( cd "$fake" && bash tools/tracker-poll.sh >"$WORK/poll-ok.out" 2>&1 ); rc=$?
  # Семантика кода 1 («открытых задач нет») — прежняя, пакет её не меняет:
  # BR-601 добавляет только коды 2/3 для ошибок трекера.
  check "ПОСЛЕ: код возврата при доступном трекере и пустом списке" "1" "$rc"
  if grep -q 'Все задачи закрыты' "$WORK/poll-ok.out"; then
    ok "ПОСЛЕ: пустой список по-прежнему означает «Все задачи закрыты»"
  else
    bad "ПОСЛЕ: рабочий путь сломан — нет строки «Все задачи закрыты»"
  fi
}

br571
br572
br593
br594
br593_594_guard
br595
br601

printf '\n--- итог: PASS=%d FAIL=%d ---\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ] || exit 1
exit 0
