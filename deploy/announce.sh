#!/usr/bin/env bash
# Общая функция голосовых уведомлений — подключается через `source` из do.sh
# (репо и рантайм-копия /sm-karaoke/system/deploy/do.sh) и из deploy_web.sh/deploy_public.sh.
# Абсолютные пути ниже намеренные: этот файл должен работать одинаково независимо
# от того, из какой директории его подключают.

SILERO_PY="~/.venvs/karaoke-tts/bin/python"
SILERO_SCRIPT="~/Karaoke/deploy/tts/silero_say.py"

function announce() {
  local notify_text="$1"
  local speak_text="$2"
  local speaker="$3"
  local rate="$4"
  [ -z "$speak_text" ] && speak_text="$notify_text"
  command -v notify-send &> /dev/null && notify-send -u normal "Karaoke" "$notify_text"
  [ "$speak_text" = "-" ] && return 0
  # Синхронно (без фонового &): иначе при нескольких announce подряд фразы
  # накладываются друг на друга — сперва должна договорить одна, потом следующая.
  if [ -x "$SILERO_PY" ] && [ -f "$SILERO_SCRIPT" ]; then
    "$SILERO_PY" "$SILERO_SCRIPT" "$speak_text" "$speaker" "$rate" &> /dev/null
  elif command -v spd-say &> /dev/null; then
    spd-say -w -o rhvoice -l ru "$speak_text"
  fi
}
