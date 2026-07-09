#!/usr/bin/env bash
# Общая функция голосовых уведомлений — подключается через `source` из do.sh
# (репо и рантайм-копия /sm-karaoke/system/deploy/do.sh) и из deploy_web.sh/deploy_public.sh.
# Абсолютные пути ниже намеренные: этот файл должен работать одинаково независимо
# от того, из какой директории его подключают.

SILERO_PY="/home/nsa/.venvs/karaoke-tts/bin/python"
SILERO_SCRIPT="/home/nsa/Karaoke/deploy/tts/silero_say.py"

function announce() {
  local msg="$1"
  local speaker="$2"
  local rate="$3"
  command -v notify-send &> /dev/null && notify-send -u normal "Karaoke" "$msg"
  if [ -x "$SILERO_PY" ] && [ -f "$SILERO_SCRIPT" ]; then
    ("$SILERO_PY" "$SILERO_SCRIPT" "$msg" "$speaker" "$rate" &> /dev/null &)
  elif command -v spd-say &> /dev/null; then
    spd-say -o rhvoice -l ru "$msg"
  elif command -v paplay &> /dev/null; then
    paplay /usr/share/sounds/freedesktop/stereo/complete.oga
  fi
}
