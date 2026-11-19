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
| `99-karaoke-keyonly.conf` | `/etc/ssh/sshd_config.d/99-karaoke-keyonly.conf` (вход только по ключу) |
| `99-karaoke-blocklist-sctp.conf` | `/etc/modprobe.d/99-karaoke-blocklist-sctp.conf` |
| `../nginx.conf` (в репозитории) | `/etc/nginx/nginx.conf` — добавлена директива `http2 off;` |

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

## Защита от nginx2root (2026-11-18)

Серия уязвимостей nginx (`CVE-2026-123007` и ещё четыре) плюс zero-day в SCTP ядра
Linux: RCE в nginx, затем выход из Docker-контейнера в root (контейнеры делят ядро).
Исправление nginx — **1.30.7/1.31.9** (18.11.2026), патча на ядро нет.

Наша экспозиция проверена фактами, а не номерами версий:

| Вектор | Нужно | У нас |
|---|---|---|
| `CVE-2026-123007` / `-123633` (HTTP/2) | активный HTTP/2 | **выключен**: `curl --http2` → `HTTP/1.1`; в конфиге теперь и явное `http2 off;` |
| `CVE-2026-121810` (HTTP/3/QUIC) | HTTP/3 | не используется |
| `CVE-2026-123634` (DNS-резолвер) | `resolver` + переменная в `proxy_pass` + `mirror` | в конфигах отсутствуют |
| `CVE-2026-113425` (почтовый прокси) | блок `mail {}` | не используется |
| SCTP zero-day (выход из контейнера) | загруженный модуль `sctp` | **заблокирован** файлом modprobe.d |

Что сделано:

1. `http2 off;` в `http {}` — раньше HTTP/2 был выключен «по недосмотру»
   (`listen 443 ssl;` без параметра), и одна правка могла это изменить.
2. Заблокирован модуль ядра `sctp`: `modprobe sctp` возвращает ошибку,
   `lsmod` пуст. SCTP не нужен ни приложению, ни Docker, ни nginx.

Проверка:

```bash
nginx -t && systemctl is-active nginx
curl -s -o /dev/null -w "%{http_version}\n" --http2 https://sm-karaoke.ru/   # ждём 1.1
modprobe sctp; echo "rc=$?"        # ждём ненулевой код
lsmod | grep -c sctp               # ждём 0
```

Откат: удалить `/etc/modprobe.d/99-karaoke-blocklist-sctp.conf` (перезагрузка не
нужна, пока модуль не загружен) и снять строку `http2 off;` из `nginx.conf`.

**Что осталось** (оформлено задачами): поставить патч nginx, когда Ubuntu его
выпустит (сейчас `Candidate` = `Installed`, 1.28.3-2ubuntu1.12), и зафиксировать
версию nginx в контейнерном образе вместо плавающего `nginx:stable` (там 1.30.6).

## Связанное

- `deploy/karaoke-container-watchdog.sh` — сторож «зомби»-контейнеров (PR #136/#137).
- `knowledge/system/infra/deploy-overview.md` — разбор инцидента 2026-11-18.
- Задача `KARAOKE-431` — окно автообновлений.
- Задача `KARAOKE-432` — fail2ban против SSH-перебора (замер 18.11.2026:
  9 747 попыток за сутки, четыре источника по ~2 000).
  Наша специфика: публичный адрес админ-машины меняется, поэтому `ignoreip`
  включает подсеть туннеля `10.77.0.0/24`, бан короткий (1 час), а вход по
  ключам продолжает работать даже при бане по IP.
