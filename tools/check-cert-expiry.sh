#!/usr/bin/env bash
# =====================================================================
# tools/check-cert-expiry.sh — напоминание о сроке TLS-сертификата
# (спека 540 / issue #286; FR-016, SC-007).
#
# Зачем: доступность приватного registry держится на сертификате домена
# sm-karaoke.ru (используется на порту 5443). Продление — ручное, владельцем;
# автоматики на проде нет. При просрочке nginx продолжит работать, но Docker
# (он на Go) упадёт с `x509: certificate has expired` — встанут push, pull и деплой.
#
# Использование:
#   bash tools/check-cert-expiry.sh [--warn-days N] [--quiet] <host:port> [...]
#
# Примеры:
#   bash tools/check-cert-expiry.sh sm-karaoke.ru:443 sm-karaoke.ru:5443
#   bash tools/check-cert-expiry.sh --warn-days 400 sm-karaoke.ru:443   # намеренная проверка алерта
#
# Порог по умолчанию — 45 суток (запас к требуемым 30: после 2026-03-15 срок
# публичных TLS-сертификатов упал с 398 до 200 дней).
#
# Порт registry проверяется ОТДЕЛЬНОЙ строкой: у него свой путь трафика, и ошибка
# в `ssl_certificate` нового server-блока на 443 не видна.
#
# При срабатывании порога вызывается существующий канал уведомлений проекта —
# `announce` из deploy/announce.sh (Silero TTS + notify-send), если он доступен.
#
# Exit: 0 — все сертификаты свежее порога; 1 — хотя бы один скоро истекает
#       (или не читается); 2 — usage.
# =====================================================================
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WARN_DAYS=45
QUIET=0
ENDPOINTS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --warn-days) WARN_DAYS="$2"; shift 2 ;;
    --quiet)     QUIET=1; shift ;;
    -h|--help)   sed -n '3,20p' "$0"; exit 0 ;;
    -*)          echo "unknown option: $1" >&2; exit 2 ;;
    *)           ENDPOINTS+=("$1"); shift ;;
  esac
done

if [[ ${#ENDPOINTS[@]} -eq 0 ]]; then
  echo "usage: $0 [--warn-days N] [--quiet] <host:port> [...]" >&2
  exit 2
fi
[[ "$WARN_DAYS" =~ ^[0-9]+$ ]] || { echo "ERROR: --warn-days должен быть числом" >&2; exit 2; }

ALERTS=0
for ep in "${ENDPOINTS[@]}"; do
  host="${ep%%:*}"
  end="$(echo | timeout 25 openssl s_client -connect "$ep" -servername "$host" 2>/dev/null \
         | openssl x509 -noout -enddate 2>/dev/null | cut -d= -f2)"
  if [[ -z "$end" ]]; then
    [[ "$QUIET" != "1" ]] && printf '  \033[0;31mОШИБКА\033[0m %s — сертификат не прочитан (порт закрыт? nginx не слушает?)\n' "$ep"
    ALERTS=$((ALERTS + 1))
    continue
  fi
  end_epoch="$(date -d "$end" +%s 2>/dev/null || echo 0)"
  now_epoch="$(date +%s)"
  days=$(( (end_epoch - now_epoch) / 86400 ))
  if [[ "$days" -lt "$WARN_DAYS" ]]; then
    [[ "$QUIET" != "1" ]] && printf '  \033[0;31mТРЕВОГА\033[0m %s — notAfter=%s, осталось %s дн. (порог %s)\n' \
      "$ep" "$end" "$days" "$WARN_DAYS"
    ALERTS=$((ALERTS + 1))
  else
    [[ "$QUIET" != "1" ]] && printf '  \033[0;32mOK\033[0m      %s — notAfter=%s, осталось %s дн.\n' \
      "$ep" "$end" "$days"
  fi
done

if [[ "$ALERTS" -gt 0 ]]; then
  msg="ВНИМАНИЕ: TLS-сертификат sm-karaoke.ru истекает менее чем через ${WARN_DAYS} дней — деплой встанет"
  # Канал уведомлений — тот же, что у деплоя (admin-машина). Нет канала — не молчим,
  # а пишем в stderr: код возврата 1 увидят и cron/systemd.
  if [[ -f "$REPO_ROOT/deploy/announce.sh" ]]; then
    # shellcheck disable=SC1091
    source "$REPO_ROOT/deploy/announce.sh" 2>/dev/null || true
    # В подshell с `set +u`: announce.sh читает необязательные позиционные
    # параметры, а этот скрипт работает под `set -u`.
    ( set +u
      command -v announce >/dev/null 2>&1 && announce \
        "Сертификат sm-karaoke.ru истекает" \
        "Вним+ание: с+ертификат см-кара+оке истек+ает м+енее чем ч+ерез ${WARN_DAYS} дней" \
        "1" ) || true
  fi
  echo "$msg" >&2
  exit 1
fi
exit 0
