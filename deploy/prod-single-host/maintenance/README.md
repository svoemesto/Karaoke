# Окно автообновлений прод-хоста: установка и проверка (задача KARAOKE-431)

Комплект конфигов, которые не дают автообновлениям ронять прод. Причина — инцидент
2026-11-18: установка пакета `needrestart` привела к автоматическому перезапуску
служб, включая `containerd`; задачи контейнеров были потеряны, прод-БД не
принимала соединения 2,5 часа.

## Что где

| Файл | Куда на прод-хосте |
|---|---|
| `99-karaoke-prod.needrestart.conf` | `/etc/needrestart/conf.d/99-karaoke-prod.conf` |
| `51-karaoke-unattended.conf` | `/etc/apt/apt.conf.d/51-karaoke-unattended.conf` |
| `apt-daily-upgrade.timer.override.conf` | `/etc/systemd/system/apt-daily-upgrade.timer.d/override.conf` |
| `apt-daily-upgrade.service.override.conf` | `/etc/systemd/system/apt-daily-upgrade.service.d/override.conf` |

## Установка

```bash
sudo install -m 644 99-karaoke-prod.needrestart.conf  /etc/needrestart/conf.d/99-karaoke-prod.conf
sudo install -m 644 51-karaoke-unattended.conf        /etc/apt/apt.conf.d/51-karaoke-unattended.conf
sudo install -d -m 755 /etc/systemd/system/apt-daily-upgrade.timer.d
sudo install -m 644 apt-daily-upgrade.timer.override.conf   /etc/systemd/system/apt-daily-upgrade.timer.d/override.conf
sudo install -d -m 755 /etc/systemd/system/apt-daily-upgrade.service.d
sudo install -m 644 apt-daily-upgrade.service.override.conf /etc/systemd/system/apt-daily-upgrade.service.d/override.conf
sudo systemctl daemon-reload
sudo systemctl restart apt-daily-upgrade.timer
```

## Проверка (обязательно после установки)

```bash
# 1. Режим needrestart: только список, без действий
grep -E "restart" /etc/needrestart/conf.d/99-karaoke-prod.conf

# 2. needrestart не перезапускает службы: PID containerd до и после прогона
PID_BEFORE=$(systemctl show containerd -p MainPID --value)
sudo needrestart -b            # в режиме 'l' только перечисляет
PID_AFTER=$(systemctl show containerd -p MainPID --value)
[ "$PID_BEFORE" = "$PID_AFTER" ] && echo "containerd НЕ перезапущен — верно"

# 3. Автоперезагрузка выключена
apt-config dump | grep -i "automatic-reboot"

# 4. Окно обновлений — ночное
systemctl list-timers apt-daily-upgrade.timer

# 5. Проверка после обновления подключена
systemctl cat apt-daily-upgrade.service | grep -A1 ExecStartPost
```

Ожидания: `containerd` не перезапускается; `Automatic-Reboot "false"`;
следующий запуск `apt-daily-upgrade.timer` — 03:30 ± 30 мин; в юните виден
`ExecStartPost` со сторожем.

## Откат

```bash
sudo rm -f /etc/needrestart/conf.d/99-karaoke-prod.conf
sudo rm -f /etc/apt/apt.conf.d/51-karaoke-unattended.conf
sudo rm -rf /etc/systemd/system/apt-daily-upgrade.timer.d
sudo rm -rf /etc/systemd/system/apt-daily-upgrade.service.d
sudo systemctl daemon-reload
sudo systemctl revert apt-daily-upgrade.timer 2>/dev/null || true
```

Автоматические обновления при откате **не отключаются** — возвращается прежний
режим и время.

## Связанное

- `deploy/karaoke-container-watchdog.sh` — сторож «зомби»-контейнеров (PR #136/#137).
- `knowledge/system/infra/deploy-overview.md` — разбор инцидента 2026-11-18.
- Задача `KARAOKE-431`.
