#!/usr/bin/env bash
# =====================================================================
# tools/check-allowlist-guardrails.sh — guard правил allow-list прод-хоста
# =====================================================================
#
# BR-631 (спека 677 / KARAOKE-337, FR-005…FR-020): репозиторная копия
# конфигурации allow-list и автоматизация, которая её правит, обязаны
# оставаться согласованными. Если из конфигурации выкинут `deny all;` или
# в allow-list попадёт приватный/дублирующийся адрес — прод перестанет
# отражать канон, а правка станет непредсказуемой.
#
# Guard проверяет ТОЛЬКО правила BR-631:
#   1. структура allow-list в deploy/prod-single-host/nginx.conf: у каждого
#      stream-блока (listen 5433 / listen 9000) есть `deny all;` и непустой
#      allow-list;
#   2. адреса allow-list уникальны и валидны (публичный IPv4 — те же правила,
#      что FR-004);
#   3. ограничение частоты задано (не более одной правки за 30 минут);
#   4. файл состояния и журнал — вне репозитория;
#   5. скрипт и юниты не содержат секретов (BR-551/552/605: секрет в argv и
#      в репозитории — одинаково плохо);
#   6. таймер настроен на 10 минут (Д-3) и юниты на месте;
#   7. прокси уведомлений: глобального прокси нет, ALLOWLIST_TELEGRAM_PROXY
#      применяется ТОЛЬКО к вызову Telegram Bot API и передаётся конфиг-файлом
#      curl (не через argv), а прямые вызовы (определение адреса и пробы)
#      идут с `--noproxy '*'`.
#
# Использование:
#   bash tools/check-allowlist-guardrails.sh              # проверка с отчётом
#   bash tools/check-allowlist-guardrails.sh --quiet      # для pre-commit
#   bash tools/check-allowlist-guardrails.sh --self-test  # проверка самих правил
#
# Коды возврата: 0 — нарушений нет; 1 — нарушения; 2 — self-test не прошёл.

set -uo pipefail

MODE="report"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

for arg in "$@"; do
  case "$arg" in
    --quiet) MODE="quiet" ;;
    --self-test) MODE="self-test" ;;
    *) echo "Неизвестный аргумент: $arg" >&2; exit 2 ;;
  esac
done

pass=0
fail=0

ok() {
  pass=$((pass + 1))
  [ "$MODE" != "quiet" ] && echo "  ✅ $1"
  return 0
}

bad() {
  fail=$((fail + 1))
  echo "  ❌ $1" >&2
  return 0
}

# --- правила ---

# 1-2. Структура и адреса allow-list в репозиторной копии.
check_allowlist() {
  local conf="$ROOT/deploy/prod-single-host/nginx.conf"
  if [ ! -f "$conf" ]; then
    bad "нет репозиторной копии конфигурации: deploy/prod-single-host/nginx.conf"
    return 0
  fi

  local ports
  ports="$(grep -oE '^[[:space:]]*listen[[:space:]]+[0-9]+' "$conf" | grep -oE '[0-9]+$' | sort -u | tr '\n' ' ')"
  local missing=0
  for port in 5433 9000; do
    if ! printf '%s' "$ports" | grep -qw "${port}"; then
      bad "в конфигурации нет stream-блока с listen ${port}"
      missing=1
    fi
  done
  [ "${missing}" = 0 ] && ok "stream-блоки 5433 и 9000 на месте"

  # В каждом блоке с listen 5433/9000 должен быть `deny all;` и allow-строки.
  local block port has_deny allows
  for port in 5433 9000; do
    block="$(awk -v p="${port}" '
      /listen[[:space:]]+'"${port}"'/ {inb=1}
      inb && /deny[[:space:]]+all[[:space:]]*;/ {print "deny"; exit}
    ' "$conf")"
    has_deny="$(printf '%s' "$block" | grep -c 'deny' || true)"
    if [ "${has_deny}" -ge 1 ]; then
      ok "в блоке listen ${port} есть deny all;"
    else
      bad "в блоке listen ${port} нет deny all; — allow-list не ограничен"
    fi
    allows="$(awk -v p="${port}" '
      /listen[[:space:]]+'"${port}"'/ {inb=1; next}
      inb && /^[[:space:]]*\}/ {inb=0}
      inb && /^[[:space:]]*allow[[:space:]]/ {c++}
      END {print c+0}
    ' "$conf")"
    if [ "${allows}" -ge 1 ]; then
      ok "в блоке listen ${port} непустой allow-list (${allows} адрес(ов))"
    else
      bad "в блоке listen ${port} пустой allow-list"
    fi
  done

  # Уникальность адресов ВНУТРИ каждого блока: один и тот же адрес в обоих
  # блоках — норма (оба блока разрешают доступ админ-машине), дубль внутри
  # блока — ошибка.
  local port block_addrs cnt distinct
  for port in 5433 9000; do
    block_addrs="$(awk -v p="${port}" '
      /listen[[:space:]]+'"${port}"'/ {inb=1; next}
      inb && /^[[:space:]]*\}/ {inb=0}
      inb && /^[[:space:]]*allow[[:space:]]/ {print $2}
    ' "$conf" | tr -d ';' | sort)"
    cnt="$(printf '%s\n' "${block_addrs}" | grep -c . || true)"
    distinct="$(printf '%s\n' "${block_addrs}" | sort -u | grep -c . || true)"
    if [ "${cnt}" = "${distinct}" ]; then
      ok "адреса в блоке listen ${port} уникальны (${distinct})"
    else
      bad "в блоке listen ${port} есть дубликаты адресов (строк ${cnt}, уникальных ${distinct})"
    fi
  done

  # Все адреса конфигурации — для проверки валидности.
  local addrs
  addrs="$(grep -oE '^[[:space:]]*allow[[:space:]]+[0-9.]+;' "$conf" | grep -oE '[0-9.]+' | sort -u)"

  local ip a b c d octet
  while read -r ip; do
    [ -z "${ip}" ] && continue
    if ! [[ "${ip}" =~ ^([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})$ ]]; then
      bad "адрес ${ip} не является IPv4"
      continue
    fi
    IFS='.' read -r a b c d <<<"${ip}"
    local bad_octet=0
    for octet in "${a}" "${b}" "${c}" "${d}"; do
      [ "${octet}" -le 255 ] 2>/dev/null || bad_octet=1
    done
    if [ "${bad_octet}" = 1 ]; then
      bad "адрес ${ip} содержит октет больше 255"
      continue
    fi
    case "${ip}" in
      127.* | 10.* | 192.168.* | 169.254.* | 224.* | 225.* | 226.* | 227.* | 228.* | 229.* | 230.* | 231.* | 232.* | 233.* | 234.* | 235.* | 236.* | 237.* | 238.* | 239.* | 255.255.255.255 | 0.0.0.0)
        bad "адрес ${ip} не является публичным (FR-004)"
        ;;
      *)
        if [ "${a}" = "172" ] && [ "${b}" -ge 16 ] && [ "${b}" -le 31 ] 2>/dev/null; then
          bad "адрес ${ip} — приватный 172.16.0.0/12 (FR-004)"
        fi
        ;;
    esac
  done <<<"${addrs}"
  ok "адреса allow-list проверены на валидность и публичность (FR-004)"
}

# 3-4. Ограничение частоты и пути состояния/журнала.
check_limits_and_paths() {
  local script="$ROOT/deploy/admin-ip-allowlist.sh"
  if [ ! -f "$script" ]; then
    bad "нет скрипта автоматизации: deploy/admin-ip-allowlist.sh"
    return 0
  fi

  local window
  window="$(grep -oE 'ALLOWLIST_MIN_INTERVAL_MINUTES:-[0-9]+' "$script" | grep -oE '[0-9]+$' | head -1)"
  if [ -z "${window}" ]; then
    bad "в скрипте не задан ALLOWLIST_MIN_INTERVAL_MINUTES (FR-010)"
  elif [ "${window}" -lt 30 ]; then
    bad "окно ограничения частоты ${window} мин меньше требуемых 30 (FR-010)"
  else
    ok "ограничение частоты задано: ${window} мин (FR-010)"
  fi

  local var value
  for var in ALLOWLIST_LOG_FILE ALLOWLIST_STATE_FILE; do
    value="$(grep -oE "${var}:-[^}]*}" "$script" | head -1 | sed -E 's/^[^-]*-//; s/}$//')"
    if [ -z "${value}" ]; then
      bad "в скрипте не задан путь ${var}"
      continue
    fi
    case "${value}" in
      "${ROOT}"/* | *"/Karaoke/"*)
        bad "${var} указывает внутрь репозитория (${value}) — состояние не должно быть в git"
        ;;
      /*)
        ok "${var} вне репозитория (${value})"
        ;;
      *)
        bad "${var} — не абсолютный путь (${value})"
        ;;
    esac
  done
}

# 5. Секреты в скрипте и юнитах.
check_no_secrets() {
  local targets=(
    "$ROOT/deploy/admin-ip-allowlist.sh"
    "$ROOT/deploy/admin-ip-allowlist.service"
    "$ROOT/deploy/admin-ip-allowlist.timer"
  )
  local f found=0
  for f in "${targets[@]}"; do
    [ -f "$f" ] || continue
    if grep -qE 'bot[0-9]{4,}:[A-Za-z0-9_-]{30,}' "$f"; then
      bad "в $(basename "$f") похоже на зашитый токен Telegram-бота"
      found=1
    fi
    if grep -qE 'Authorization: token [A-Za-z0-9]{20,}' "$f"; then
      bad "в $(basename "$f") зашит токен Forgejo"
      found=1
    fi
    if grep -qE '(password|passwd|secret|token)[A-Za-z_]*=[[:space:]]*["'"'"']?[A-Za-z0-9+/=_-]{16,}' "$f"; then
      bad "в $(basename "$f") присвоено значение секрета"
      found=1
    fi
  done
  [ "${found}" = 0 ] && ok "секретов в скрипте и юнитах нет (FR-016)"
}

# 6. Юниты и период таймера.
check_units() {
  local timer="$ROOT/deploy/admin-ip-allowlist.timer"
  local service="$ROOT/deploy/admin-ip-allowlist.service"
  if [ ! -f "$timer" ]; then
    bad "нет юнита deploy/admin-ip-allowlist.timer"
  else
    ok "юнит таймера на месте"
    if grep -qE '^OnUnitActiveSec=10min' "$timer"; then
      ok "таймер настроен на 10 минут (Д-3)"
    else
      bad "таймер не настроен на 10 минут (OnUnitActiveSec=10min) — проверь deploy/admin-ip-allowlist.timer"
    fi
  fi
  if [ ! -f "$service" ]; then
    bad "нет юнита deploy/admin-ip-allowlist.service"
  else
    if grep -qE '^ExecStart=/.*admin-ip-allowlist\.sh' "$service"; then
      ok "ExecStart в сервисе — абсолютный путь к скрипту"
    else
      bad "ExecStart в сервисе не указывает абсолютный путь к скрипту"
    fi
    if grep -qE '^StandardOutput=journal' "$service"; then
      ok "журнал сервиса идёт в journald (FR-012)"
    else
      bad "в сервисе нет StandardOutput=journal (FR-012)"
    fi
  fi
}

# 7. Прокси уведомлений: только для Telegram, никакого глобального прокси.
# Тело функции из shell-файла: от строки `name() {` до закрывающей `}`.
extract_func() {
  local file="$1" fn="$2"
  [ -f "${file}" ] || return 0
  awk -v fn="${fn}" '
    $0 ~ "^"fn"\\(\\)[[:space:]]*\\{" {inb=1}
    inb {print}
    inb && /^}/ {exit}
  ' "${file}"
}

check_proxy_rules() {
  local script="$ROOT/deploy/admin-ip-allowlist.sh"
  local contract="$ROOT/specs/677-admin-ip-allowlist/contracts/automation-cli.md"
  if [ ! -f "$script" ]; then
    bad "нет скрипта автоматизации: deploy/admin-ip-allowlist.sh"
    return 0
  fi

  # 7a. Глобальные переменные прокси окружения не используются: иначе
  # определение адреса пошло бы через прокси, и в allow-list прод-хоста попал бы
  # адрес выхода прокси вместо адреса админ-машины.
  local hits
  hits="$(grep -inE 'all_proxy|https_proxy|http_proxy' "${script}" | head -5 || true)"
  if [ -n "${hits}" ]; then
    bad "в скрипте использованы переменные прокси окружения — глобальный прокси запрещён:"
    printf '%s\n' "${hits}" | sed 's/^/      /' >&2
  else
    ok "в скрипте нет глобальных переменных прокси окружения"
  fi

  # 7b. Прямые вызовы идут напрямую: --noproxy '*' отключает переменные
  # прокси окружения для этих запросов.
  local fn body
  for fn in detect_address probe_https; do
    body="$(extract_func "${script}" "${fn}")"
    if [ -z "${body}" ]; then
      bad "в скрипте не найдена функция ${fn}"
      continue
    fi
    if printf '%s' "${body}" | grep -q -- '--noproxy'; then
      ok "${fn} — прямой вызов (--noproxy): адрес определяется без прокси"
    else
      bad "${fn} вызывается без --noproxy — через прокси окружения определился бы не адрес админ-машины"
    fi
  done

  # 7c. Прокси Telegram: из переменной окружения, в curl — конфиг-файлом.
  local tg
  tg="$(extract_func "${script}" send_telegram)"
  if [ -z "${tg}" ]; then
    bad "в скрипте не найдена функция send_telegram"
  else
    if grep -q 'ALLOWLIST_TELEGRAM_PROXY' "${script}"; then
      ok "скрипт читает ALLOWLIST_TELEGRAM_PROXY"
    else
      bad "скрипт не читает ALLOWLIST_TELEGRAM_PROXY"
    fi
    if printf '%s' "${tg}" | grep -qF 'proxy = "%s"'; then
      ok "прокси Telegram передаётся конфиг-файлом curl (proxy = ...)"
    else
      bad "в send_telegram нет строки proxy = — прокси не передаётся в curl"
    fi
    if printf '%s' "${tg}" | grep -q -- '--proxy'; then
      bad "в send_telegram прокси передаётся через argv (--proxy) — виден в ps"
    else
      ok "прокси Telegram не попадает в argv"
    fi
  fi

  # 7d. Контракт описывает переменную.
  if [ ! -f "${contract}" ]; then
    bad "нет контракта: specs/677-admin-ip-allowlist/contracts/automation-cli.md"
  elif grep -q 'ALLOWLIST_TELEGRAM_PROXY' "${contract}"; then
    ok "контракт automation-cli.md описывает ALLOWLIST_TELEGRAM_PROXY"
  else
    bad "в контракте automation-cli.md нет переменной ALLOWLIST_TELEGRAM_PROXY"
  fi
}

check_rules() {
  check_allowlist
  check_limits_and_paths
  check_no_secrets
  check_units
  check_proxy_rules
}

if [ "$MODE" = "self-test" ]; then
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  mkdir -p "$tmp/deploy/prod-single-host" "$tmp/tools" \
    "$tmp/specs/677-admin-ip-allowlist/contracts"
  cp "$ROOT/tools/check-allowlist-guardrails.sh" "$tmp/tools/" 2>/dev/null || true

  # Ожидание: нарушения должны быть пойманы.
  expect_fail() {
    local label="$1"
    pass=0
    fail=0
    ROOT="$tmp" check_rules >/dev/null 2>&1
    if [ "${fail}" -eq 0 ]; then
      echo "❌ self-test: ${label} — нарушение не поймано" >&2
      exit 2
    fi
  }

  # Ожидание: корректное состояние нарушением не считается.
  expect_ok() {
    local label="$1"
    pass=0
    fail=0
    ROOT="$tmp" check_rules >/dev/null 2>&1
    if [ "${fail}" -ne 0 ]; then
      echo "❌ self-test: ${label} — корректная конфигурация принята как нарушение" >&2
      exit 2
    fi
  }

  # «Битый» пример: deny all; убран, адрес приватный и продублирован,
  # глобальный прокси в скрипте, переменная не описана в контракте.
  cat >"$tmp/deploy/prod-single-host/nginx.conf" <<'NGX'
stream {
    server {
        listen 5433;
        allow 185.26.28.109;
        allow 10.0.0.5;
        allow 185.26.28.109;
    }
    server {
        listen 9000;
        allow 185.26.28.109;
        deny all;
    }
}
NGX
  cat >"$tmp/deploy/admin-ip-allowlist.sh" <<'SH'
MIN_INTERVAL_MINUTES="${ALLOWLIST_MIN_INTERVAL_MINUTES:-5}"
LOG_FILE="${ALLOWLIST_LOG_FILE:-/home/nsa/Karaoke/deploy/admin-ip-allowlist.log}"
STATE_FILE="${ALLOWLIST_STATE_FILE:-/home/nsa/Karaoke/deploy/admin-ip-allowlist.state}"
ALL_PROXY="http://127.0.0.1:3128"
SH
  cat >"$tmp/deploy/admin-ip-allowlist.timer" <<'UNIT'
[Timer]
OnUnitActiveSec=30min
UNIT
  cat >"$tmp/deploy/admin-ip-allowlist.service" <<'UNIT'
[Service]
ExecStart=deploy/admin-ip-allowlist.sh
UNIT
  cat >"$tmp/specs/677-admin-ip-allowlist/contracts/automation-cli.md" <<'MD'
# Contract: CLI автоматизации allow-list

## 4. Переменные окружения

| `ALLOWLIST_TELEGRAM_BOT_TOKEN` | (нет) | Токен бота проекта |
MD

  expect_fail "битое состояние"

  # «Хороший» пример: все правила выполняются.
  cat >"$tmp/deploy/prod-single-host/nginx.conf" <<'NGX'
stream {
    server {
        listen 5433;
        allow 185.26.28.109;
        allow 83.137.50.231;
        deny all;
    }
    server {
        listen 9000;
        allow 185.26.28.109;
        allow 83.137.50.231;
        deny all;
    }
}
NGX
  cat >"$tmp/good-script.sh" <<'SH'
MIN_INTERVAL_MINUTES="${ALLOWLIST_MIN_INTERVAL_MINUTES:-30}"
LOG_FILE="${ALLOWLIST_LOG_FILE:-/var/log/admin-ip-allowlist.log}"
STATE_FILE="${ALLOWLIST_STATE_FILE:-/var/log/admin-ip-allowlist.state}"
TELEGRAM_PROXY="${ALLOWLIST_TELEGRAM_PROXY:-}"

detect_address() {
  local url="$1"
  curl -fsS --noproxy '*' --max-time 8 "${url}"
}

probe_https() {
  local url="$1" timeout_s="$2"
  curl -fsS --noproxy '*' -o /dev/null -w '%{http_code}' --max-time "${timeout_s}" "${url}"
}

send_telegram() {
  local cfg="$1"
  printf 'proxy = "%s"\n' "${TELEGRAM_PROXY}" >>"${cfg}"
  curl -sS -K "${cfg}"
}
SH
  cp "$tmp/good-script.sh" "$tmp/deploy/admin-ip-allowlist.sh"
  cat >"$tmp/deploy/admin-ip-allowlist.timer" <<'UNIT'
[Timer]
OnUnitActiveSec=10min
UNIT
  cat >"$tmp/deploy/admin-ip-allowlist.service" <<'UNIT'
[Service]
ExecStart=/home/nsa/Karaoke/deploy/admin-ip-allowlist.sh
StandardOutput=journal
UNIT
  cat >"$tmp/specs/677-admin-ip-allowlist/contracts/automation-cli.md" <<'MD'
# Contract: CLI автоматизации allow-list

## 4. Переменные окружения

| `ALLOWLIST_TELEGRAM_BOT_TOKEN` | (нет) | Токен бота проекта |
| `ALLOWLIST_TELEGRAM_PROXY` | (нет) | Прокси только для вызова Telegram Bot API |
MD

  expect_ok "корректная конфигурация"

  # Правило 7a: глобальный прокси окружения в скрипте запрещён.
  cp "$tmp/good-script.sh" "$tmp/deploy/admin-ip-allowlist.sh"
  printf 'ALL_PROXY="http://127.0.0.1:3128"\n' >>"$tmp/deploy/admin-ip-allowlist.sh"
  expect_fail "глобальный прокси в скрипте"

  # Правило 7b: определение адреса обязано идти напрямую.
  cp "$tmp/good-script.sh" "$tmp/deploy/admin-ip-allowlist.sh"
  sed -i "s|curl -fsS --noproxy '\*' --max-time 8|curl -fsS --max-time 8|" \
    "$tmp/deploy/admin-ip-allowlist.sh"
  expect_fail "определение адреса без --noproxy"

  # Правило 7b: проба доступности обязана идти напрямую.
  cp "$tmp/good-script.sh" "$tmp/deploy/admin-ip-allowlist.sh"
  sed -i "s|curl -fsS --noproxy '\*' -o /dev/null|curl -fsS -o /dev/null|" \
    "$tmp/deploy/admin-ip-allowlist.sh"
  expect_fail "проба доступности без --noproxy"

  # Правило 7c: прокси Telegram — конфиг-файлом, не через argv.
  cp "$tmp/good-script.sh" "$tmp/deploy/admin-ip-allowlist.sh"
  sed -i "s|printf 'proxy = \"%s\"\\\\n' \"\${TELEGRAM_PROXY}\" >>\"\${cfg}\"|curl -sS --proxy \"\${TELEGRAM_PROXY}\" \"\${cfg}\"|" \
    "$tmp/deploy/admin-ip-allowlist.sh"
  expect_fail "прокси Telegram через argv"

  # Правило 7d: контракт описывает переменную.
  cp "$tmp/good-script.sh" "$tmp/deploy/admin-ip-allowlist.sh"
  cat >"$tmp/specs/677-admin-ip-allowlist/contracts/automation-cli.md" <<'MD'
# Contract: CLI автоматизации allow-list

## 4. Переменные окружения

| `ALLOWLIST_TELEGRAM_BOT_TOKEN` | (нет) | Токен бота проекта |
MD
  expect_fail "переменная не описана в контракте"

  # Возврат к корректному состоянию.
  cp "$tmp/good-script.sh" "$tmp/deploy/admin-ip-allowlist.sh"
  cat >"$tmp/specs/677-admin-ip-allowlist/contracts/automation-cli.md" <<'MD'
# Contract: CLI автоматизации allow-list

## 4. Переменные окружения

| `ALLOWLIST_TELEGRAM_BOT_TOKEN` | (нет) | Токен бота проекта |
| `ALLOWLIST_TELEGRAM_PROXY` | (нет) | Прокси только для вызова Telegram Bot API |
MD
  expect_ok "корректная конфигурация после проверок"

  echo "✅ self-test: правила ловят битое состояние и пропускают корректное"
  exit 0
fi

check_rules

if [ "$MODE" != "quiet" ]; then
  echo
  echo "--- итог: PASS=$pass FAIL=$fail ---"
fi

if [ "${fail}" -ne 0 ]; then
  exit 1
fi
exit 0
