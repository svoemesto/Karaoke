# How to: SSH-доступ к прод-хосту — новая машина, бэкап ключей, восстановление

Обновляется вместе с изменениями доступа. Связанные документы:
`knowledge/system/infra/deploy-overview.md` (контейнеры, окно обновлений),
`knowledge/guidelines/runbooks/prod-tunnel.md` (канал к прод-БД),
ADR `knowledge/adr/local-0018-*.md` (туннель вместо публичного allow-list).

## Контекст

Когда использовать: подключение с новой машины; ротация или потеря ключа;
снятие резервной копии доступа; полная блокировка SSH (только консоль
провайдера); переустановка прод-хоста. Если задача — выкладка кода или
диагностика прод-сервисов, это другой runbook (`prod-config-delivery.md`,
`log-correlation.md`).

## 1. Как устроен доступ

На прод-хосте `188.127.240.124` есть **один** пользователь с shell — `root`.
Попасть на него можно только по SSH. Пароль у `root` в системе задан, но после
отключения `PasswordAuthentication` (см. § 6) по сети он приниматься не будет:
вход — **только по ключу**.

Ключи, которые пускают на прод (состояние на 2026-11-19, проверяется командой
`ssh-keygen -lf /root/.ssh/authorized_keys` на проде):

| Ключ | Где приватная часть | Назначение |
|---|---|---|
| `nsa@nsa-i9` (RSA 4096) | админ-машина, `/home/nsa/.ssh/id_rsa` | ручная работа: `ssh`, `scp`, `rsync`, выкладка |
| `karaoke-tunnel-cert-renew@nsa-i9` (ed25519) | админ-машина, `/root/.ssh/id_ed25519_karaoke_tunnel` | автомат: таймер продления клиентского сертификата mTLS |

Оба ключа добавлены в `/root/.ssh/authorized_keys` на проде. Отпечатки:

```bash
ssh-keygen -lf ~/.ssh/id_rsa.pub                                    # ключ ручной работы
sudo ssh-keygen -lf /root/.ssh/id_ed25519_karaoke_tunnel.pub         # служебный (админ-машина)
ssh root@188.127.240.124 'ssh-keygen -lf /root/.ssh/authorized_keys' # что реально пускает
```

**Важно:** ключ `id_rsa` на админ-машине **без парольной фразы** (проверка:
`ssh-keygen -y -f ~/.ssh/id_rsa -P ""`). Это удобно для автоматов, но означает,
что копия файла = доступ к проду. Рекомендация — отдельный ключ с фразой для
ручной работы (§ 7).

## 2. Доступ с новой машины

### 2.1. Пока вход по паролю ещё разрешён (текущее состояние)

```bash
# на новой машине
ssh-keygen -t ed25519 -C "nsa@<имя-машины>" -f ~/.ssh/id_ed25519_prod
ssh-copy-id -i ~/.ssh/id_ed25519_prod.pub root@188.127.240.124   # спросит пароль root
ssh -i ~/.ssh/id_ed25519_prod root@188.127.240.124 'hostname'     # проверка
```

### 2.2. После отключения паролей (процедура на будущее)

`ssh-copy-id` перестанет работать — он использует пароль. Публичный ключ
переносится **с уже авторизованной машины**:

```bash
# на новой машине: создать ключ и забрать публичную часть
ssh-keygen -t ed25519 -C "nsa@<имя-машины>" -f ~/.ssh/id_ed25519_prod
cat ~/.ssh/id_ed25519_prod.pub          # скопировать строку

# на любой машине, которая уже пускается на прод:
ssh root@188.127.240.124 'cat >> /root/.ssh/authorized_keys' <<'KEY'
ssh-ed25519 AAAA... nsa@<имя-машины>
KEY

# проверка с новой машины
ssh -i ~/.ssh/id_ed25519_prod root@188.127.240.124 'hostname'
```

Если авторизованной машины нет вообще — только консоль провайдера (§ 5).

### 2.3. Чтобы не писать `-i` каждый раз

`~/.ssh/config` на рабочей машине:

```
Host prod
    HostName 188.127.240.124
    User root
    IdentityFile ~/.ssh/id_ed25519_prod
    IdentitiesOnly yes
    ServerAliveInterval 30
```

Дальше: `ssh prod`, `scp file prod:/tmp/`, `rsync -av dir/ prod:/path/`.

## 3. Ротация ключа (добавить новый, убрать старый)

Порядок обязателен: **сначала добавить новый и проверить, потом убирать старый**.
Обратный порядок = потеря доступа.

```bash
# 1. новый ключ
ssh-keygen -t ed25519 -C "nsa@nsa-i9-2027" -f ~/.ssh/id_ed25519_prod_2027

# 2. добавить на прод (с текущего рабочего ключа)
ssh root@188.127.240.124 'cat >> /root/.ssh/authorized_keys' < ~/.ssh/id_ed25519_prod_2027.pub

# 3. ПРОВЕРИТЬ новым ключом, не закрывая текущую сессию
ssh -i ~/.ssh/id_ed25519_prod_2027 root@188.127.240.124 'echo ok'

# 4. убрать старую строку из authorized_keys (по отпечатку, а не «на глаз»)
ssh root@188.127.240.124 'ssh-keygen -lf /root/.ssh/authorized_keys'   # найти строку
ssh root@188.127.240.124 'cp /root/.ssh/authorized_keys /root/authorized_keys.bak'
ssh root@188.127.240.124 'sed -i "/<фрагмент старого ключа>/d" /root/.ssh/authorized_keys'
ssh root@188.127.240.124 'ssh-keygen -lf /root/.ssh/authorized_keys'   # сверить остаток
```

Правило: в `authorized_keys` держим **минимальный набор** — рабочий ключ,
служебный ключ и, при необходимости, один резервный. Лишние строки — это
забытые доступы.

## 4. Бэкап ключей

### 4.1. Что и чем снимается

```bash
sudo bash deploy/ssh-key-backup.sh          # снять бэкап
sudo bash deploy/ssh-key-backup.sh --list   # только показать, что есть и что пускает
```

Скрипт (`/usr/local/sbin/ssh-key-backup.sh` на админ-машине) собирает:

* приватные и публичные ключи, которые реально пускают на прод;
* `/root/.ssh/authorized_keys` прод-хоста и его `sshd_config` + drop-in файлы;
* `INVENTORY.txt` — отпечатки и «что от чего», чтобы при восстановлении не путать;
* архив `tar.gz`.

Результат: `/root/ssh-backup-<дата>/` (права `700`) и
`/root/ssh-backup-<дата>.tar.gz` (`600`). Снимать — после каждой ротации ключей
и перед любым изменением `sshd`.

### 4.2. Внешняя копия — обязательна

Каталог на машине **не является** бэкапом: при потере машины он пропадёт вместе
с ключами. Копию архива владелец уносит **вне админ-машины** — менеджер паролей
или защищённый носитель. Хранить рядом с машиной (в том же каталоге, на том же
диске) смысла нет.

По желанию — зашифровать перед выносом:

```bash
tar -czf - -C /root/ssh-backup-<дата> . \
  | openssl enc -aes-256-cbc -pbkdf2 -out ssh-backup-<дата>.tar.gz.enc
# пароль — только в менеджере паролей, никогда рядом с архивом
```

Проверка, что архив читается и ключи целы:

```bash
tar -tzf /root/ssh-backup-<дата>.tar.gz | head
openssl enc -d -aes-256-cbc -pbkdf2 -in <файл>.enc | tar -tzf - | head   # для шифрованной копии
```

## 5. Восстановление доступа

Разбор по сценариям: чем раньше сценарий, тем он проще.

### Сценарий A. Ключ потерян, но есть другая авторизованная машина

Самый частый. Действуем как в § 2.2: с рабочей машины добавляем новый публичный
ключ, проверяем, старый — удаляем.

### Сценарий B. Потеряна админ-машина целиком

1. На новой машине создать ключ (§ 2.1/2.2).
2. Восстановить доступ: если есть вторая машина с ключом — § 2.2; если нет — § 5.4
   (консоль).
3. Восстановить служебный ключ для автопродления сертификата:
   ```bash
   # на новой админ-машине, от root
   install -d -m 700 /root/.ssh
   cp <из-бэкапа>/keys/root/id_ed25519_karaoke_tunnel /root/.ssh/
   chmod 600 /root/.ssh/id_ed25519_karaoke_tunnel
   install -m 755 deploy/ssh-key-backup.sh /usr/local/sbin/ssh-key-backup.sh
   systemctl status karaoke-tunnel-cert-renew.timer     # таймер продления
   ```

### Сценарий C. Ключ есть, но SSH не пускает (сломан конфиг/права)

Признаки и лечение по порядку:

| Симптом | Причина | Что делать |
|---|---|---|
| `Permission denied (publickey)` | ключа нет в `authorized_keys`; неверный файл ключа; `-i` не указан | сверить отпечатки: `ssh-keygen -lf` на клиенте и на проде; `ssh -v` покажет, какой ключ предлагается |
| `Permission denied` при верном ключе | права: `authorized_keys`/домашний каталог доступны группе или всем | на проде: `chmod 700 /root /root/.ssh; chmod 600 /root/.ssh/authorized_keys` |
| `Connection refused` | `sshd` не запущен | только консоль: `systemctl start ssh` |
| `Host key verification failed` | хост переустановлен/сменился ключ | `ssh-keygen -R 188.127.240.124`, сверить новый отпечаток, подключиться |
| Вход не идёт сразу после правки `sshd_config` | синтаксическая ошибка | консоль: `sshd -t` покажет строку; откатить drop-in |

### Сценарий D. Полная блокировка — доступна только консоль провайдера

Порядок (консоль даёт доступ к виртуальному экрану/серийной консоли хоста):

1. Открыть консоль в панели провайдера, войти как `root` (пароль root задан
   в системе — он для консоли и остаётся рабочим).
2. Убедиться, что `sshd` живой: `systemctl status ssh`, при необходимости
   `sshd -t` и `systemctl start ssh`.
3. Проверить права и файл ключей:
   ```bash
   chmod 700 /root /root/.ssh
   chmod 600 /root/.ssh/authorized_keys
   ssh-keygen -lf /root/.ssh/authorized_keys
   ```
4. Добавить актуальный публичный ключ (из бэкапа или новый):
   ```bash
   echo 'ssh-ed25519 AAAA... nsa@new-machine' >> /root/.ssh/authorized_keys
   ```
5. Если правился `sshd_config` — вернуть из бэкапа:
   ```bash
   cp /etc/ssh/sshd_config.bak-<дата> /etc/ssh/sshd_config
   sshd -t && systemctl reload ssh
   ```
6. Проверить вход с рабочей машины, только потом закрывать консоль.

### Сценарий E. Прод-хост переустановлен

Ключи и `authorized_keys` пропадут вместе с системой. Порядок:

1. Через консоль провайдера добавить публичные ключи из бэкапа (§ 4).
2. Восстановить `sshd_config` из бэкапа (`prod/sshd_config`, `prod/sshd_config.d.txt`).
3. Заново поднять окружение по `deploy/prod-single-host/` (compose, nginx, юниты).
4. Проверить, что автоматы работают: таймер продления сертификата, сторож
   контейнеров, fail2ban, окно обновлений.

## 6. Вход по паролю отключён (выполнено 2026-11-19, 13:10 МСК)

Состояние на проде: `passwordauthentication no`, `permitrootlogin prohibit-password`,
`kbdinteractiveauthentication no`. Пароль по SSH **не принимается** — сервер
предлагает только `publickey`.

Файлы на хосте: `/etc/ssh/sshd_config.d/99-karaoke-keyonly.conf` (канон в
репозитории — `deploy/prod-single-host/maintenance/99-karaoke-keyonly.conf`) и
`/etc/ssh/sshd_config.d/50-cloud-init.conf`, приведённый к `no`, чтобы при снятии drop-in пароль не
вернулся молча.

Проверено после применения: вход по ключу в новой сессии работает; попытка по
паролю даёт `Permission denied (publickey)`; служебный ключ (от которого зависит
таймер продления сертификата) пускает; таймер продления отработал 19.11 в 04:18
успешно; fail2ban продолжает видеть попытки перебора (фильтр ловит `Invalid user`
и `preauth`, которые sshd пишет и при отключённом пароле).

### Как это было сделано (процедура для повторения)

1. Проверить **оба** ключа независимо, до изменений.
2. Бэкап `/etc/ssh/sshd_config` и drop-in в `/root/ssh-config-backup-<дата>`.
3. Положить drop-in `/etc/ssh/sshd_config.d/99-karaoke-keyonly.conf`, привести `/etc/ssh/sshd_config.d/50-cloud-init.conf` к `no`.
4. `sshd -t`; при ошибке — откат, `sshd` не трогать.
5. **«Мёртвая рука»**: `systemd-run --unit=ssh-keyonly-rollback --on-active=5min ...`
   с восстановлением бэкапа и `reload`. Если через 5 минут успех не подтверждён —
   конфигурация откатывается сама, и доступ не теряется.
6. `systemctl reload ssh`.
7. Проверить в новой сессии: ключ пускает, пароль отбит. **Только после этого**
   гасить автооткат (`systemctl stop ssh-keyonly-rollback.timer`).

Прежняя формулировка (на случай повторного включения пароля):

```bash
# 0. бэкап
sudo bash deploy/ssh-key-backup.sh

# 1. проверить ОБА ключа, не закрывая текущую сессию
ssh -i ~/.ssh/id_rsa root@188.127.240.124 'echo id_rsa ok'
#    служебный ключ (от root на админ-машине):
sudo ssh -i /root/.ssh/id_ed25519_karaoke_tunnel root@188.127.240.124 'echo service ok'

# 2. drop-in: имя ПОСЛЕ 50-cloud-init.conf, иначе он перебьёт настройку
sshd_config.d/99-karaoke-keyonly.conf:
    PasswordAuthentication no
    PermitRootLogin prohibit-password

# 3. применить
sshd -t && systemctl reload ssh

# 4. проверить в НОВОЙ сессии
sshd -T | grep -E "^(passwordauthentication|permitrootlogin)"
ssh -o PreferredAuthentications=password -o PubkeyAuthentication=no root@188.127.240.124
#    ожидание: Permission denied (publickey)
```

Ловушка, найденная на проде: `PasswordAuthentication yes` задан **и** в
`/etc/ssh/sshd_config:78`, **и** в `/etc/ssh/sshd_config.d/50-cloud-init.conf`.
Drop-in файлы читаются по алфавиту — файл `10-...` будет перекрыт `50-...`.

## 7. Гигиена ключей (что стоит сделать)

1. Отдельный ключ для ручной работы **с парольной фразой** + `ssh-agent`
   (сейчас единственный рабочий `id_rsa` без фразы).
2. Служебный ключ (`id_ed25519_karaoke_tunnel`) — только для таймера, руками им
   не работать.
3. Резервный ключ — сгенерировать и положить публичную часть в
   `authorized_keys`, приватную — во внешний бэкап, не используя в работе.
4. Раз в квартал: `ssh-keygen -lf /root/.ssh/authorized_keys` на проде и сверка
   с ожидаемым списком; лишнее — удалять.

## 9. Проверка после любых изменений доступа

```bash
# 1. вход работает (в НОВОЙ сессии, старую не закрывать до этой проверки)
ssh root@188.127.240.124 'hostname; uptime'
# 2. что реально пускает и сколько ключей
ssh root@188.127.240.124 'ssh-keygen -lf /root/.ssh/authorized_keys'
# 3. права в порядке (иначе sshd молча откажет)
ssh root@188.127.240.124 'ls -ld /root /root/.ssh; ls -l /root/.ssh/authorized_keys'
# 4. автоматы после ротации ключей: продление сертификата, сторож контейнеров
systemctl status karaoke-tunnel-cert-renew.timer --no-pager | head -3
ssh root@188.127.240.124 'systemctl is-active karaoke-container-watchdog.timer fail2ban'
```

## 10. Откат

| Что меняли | Как вернуть |
|---|---|
| `authorized_keys` | `cp /root/authorized_keys.bak /root/.ssh/authorized_keys` (бэкап делается перед правкой в § 3) |
| `sshd_config` / drop-in | вернуть файл из `/root/ssh-backup-<дата>/prod/`, затем `sshd -t && systemctl reload ssh` |
| ключ (ротация не удалась) | старую строку в `authorized_keys` не удалять до проверки нового ключа — это и есть откат |
| полная потеря доступа | консоль провайдера, § 5 сценарий D |

## 11. Быстрая шпаргалка

```bash
ssh root@188.127.240.124                       # вход (ключ по умолчанию)
ssh -v root@188.127.240.124                    # диагностика: какой ключ предлагается
ssh-keygen -lf /root/.ssh/authorized_keys      # что пускает (на проде)
sudo bash deploy/ssh-key-backup.sh             # бэкап ключей
sudo bash deploy/ssh-key-backup.sh --list      # что есть сейчас
ssh -o PreferredAuthentications=password -o PubkeyAuthentication=no root@188.127.240.124
                                               # проверка, что пароль отключён
```
