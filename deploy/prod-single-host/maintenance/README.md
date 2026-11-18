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
| `fail2ban-karaoke.local` | `/etc/fail2ban/jail.d/karaoke.local` |

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

# fail2ban (задача KARAOKE-432): ставится пакетом, jail — отдельным файлом
sudo apt-get install -y fail2ban
sudo install -m 644 fail2ban-karaoke.local /etc/fail2ban/jail.d/karaoke.local
sudo fail2ban-client -t && sudo systemctl enable --now fail2ban
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

```bash
# 6. fail2ban: jail активен и реально банит
systemctl is-active fail2ban
fail2ban-client status sshd              # Currently banned / Total banned
fail2ban-client set sshd banip 203.0.113.7   # тестовый бан
nft list ruleset | grep -c 203.0.113.7       # должно быть 1
fail2ban-client set sshd unbanip 203.0.113.7
nft list ruleset | grep -c 203.0.113.7       # должно быть 0
```

Ожидания: `containerd` не перезапускается; `Automatic-Reboot "false"`;
следующий запуск `apt-daily-upgrade.timer` — 03:30 ± 30 мин; в юните виден
`ExecStartPost` со сторожем; fail2ban активен, тестовый адрес банится и
разбанивается, а наши адреса в списке забаненных не появляются.

## Откат

```bash
sudo rm -f /etc/needrestart/conf.d/99-karaoke-prod.conf
sudo rm -f /etc/apt/apt.conf.d/51-karaoke-unattended.conf
sudo rm -rf /etc/systemd/system/apt-daily-upgrade.timer.d
sudo rm -rf /etc/systemd/system/apt-daily-upgrade.service.d
sudo systemctl daemon-reload
sudo systemctl revert apt-daily-upgrade.timer 2>/dev/null || true

# fail2ban
sudo rm -f /etc/fail2ban/jail.d/karaoke.local
sudo systemctl restart fail2ban   # или: sudo systemctl disable --now fail2ban
```

Автоматические обновления при откате **не отключаются** — возвращается прежний
режим и время.

## Связанное

- `deploy/karaoke-container-watchdog.sh` — сторож «зомби»-контейнеров (PR #136/#137).
- `knowledge/system/infra/deploy-overview.md` — разбор инцидента 2026-11-18.
- Задача `KARAOKE-431` — окно автообновлений.
- Задача `KARAOKE-432` — fail2ban против SSH-перебора (замер 18.11.2026:
  9 747 попыток за сутки, четыре источника по ~2 000).
  Наша специфика: публичный адрес админ-машины меняется, поэтому `ignoreip`
  включает подсеть туннеля `10.77.0.0/24`, бан короткий (1 час), а вход по
  ключам продолжает работать даже при бане по IP.
