# TLS-сертификат sm-karaoke.ru при переезде на новый сервер

> Research-артефакт. Секреты (приватный ключ, полный PEM) НЕ приводятся — только пути, даты, issuer, SAN,
> отпечатки. Дата исследования: 2026-09-23.

## Обзор

Домен `sm-karaoke.ru` / `www.sm-karaoke.ru` обслуживается валидным **публичным** DV-сертификатом
GlobalSign. Решение владельца — **переиспользовать текущий сертификат** (не certbot) при переезде
на новый хост `188.127.240.124` (hostname `sm-karaoke`).

Ключевые выводы:

- Файлы `.crt`/`.key` **найдены** на admin-машине (Яндекс.Диск) и на старом проде.
- Сертификат покрывает оба нужных имени (`sm-karaoke.ru`, `www.sm-karaoke.ru`) через SAN.
- Сертификаты не привязаны к IP → **текущий сертификат подходит** для нового хоста без перевыпуска.
- Срок действия — до **2027-04-03**, перевыпуск сейчас **не требуется**.
- `keytool`/Java truststore для `karaoke-web` **не обязателен**: сертификат выпущен публичным CA
  (GlobalSign), корень которого уже есть в стандартных `cacerts`.
- ВАЖНО: рабочий файл на проде — это **fullchain** (leaf + intermediate, 4426 b), а файл на
  Яндекс.Диске — только **leaf** (2738 b). Для nginx нужен fullchain.

## Где лежит сертификат

### Admin-машина (Яндекс.Диск) — источник

Путь из `deploy/Настройка сервера.md` существует:

```
/disks/HDD_16Tb_Clouds/Yandex.Disk/_MAIN/karaoke_files/
├── www.sm-karaoke.ru.crt      2738 b   leaf, 1 сертификат
├── www.sm-karaoke.ru.key      3243 b   приватный ключ (НЕ секрет в отчёте)
├── www.sm-karaoke.ru.csr      1613 b
├── www.sm-karaoke.ru.der      1950 b
├── www.sm-karaoke.ru.p7b      5635 b
├── certificate_ca.crt         2967 b   intermediate + root GlobalSign
├── certificate.crt / .csr / .p7b       (прошлый сертификат, янв 2024)
└── do.env, deploy_new_comp/, rename_script.sh
```

Дубликаты leaf-сертификата (все 2738 b, идентичны):
- `/home/nsa/Downloads/sm-karaoke-cert-2026/www.sm-karaoke.ru.crt` (+ `.key`, `.csr`, `.der`, `.p7b`)
- `/home/nsa/Karaoke/deploy/karaoke-app/files/www.sm-karaoke.ru.crt` — **tracked в git**
- `/home/nsa/Karaoke/karaoke-app/www.sm-karaoke.ru.crt`
- `/home/nsa/Karaoke/karaoke-app/build/libs/www.sm-karaoke.ru.crt` (артефакт сборки)
- `/home/nsa/Agents/Reviewer/karaoke-306-review/karaoke-app/www.sm-karaoke.ru.crt`

### Старый прод `188.119.64.111`

```
/etc/keys/
├── www.sm-karaoke.ru.crt                4426 b  fullchain: leaf + intermediate (2 сертификата)
├── www.sm-karaoke.ru.crt.bak-20260708   2738 b  leaf-only (старая версия)
├── www.sm-karaoke.ru.key                3243 b  root:root, chmod 600
├── www.sm-karaoke.ru.key.save           3255 b
└── certificate_ca.crt                   2967 b
```

Конфиг nginx: `/etc/nginx/sites-enabled/80to8897` (копия в репо
`deploy/web-server-deploy/deploy/80to8897`), строки 4-5:
`ssl_certificate /etc/keys/www.sm-karaoke.ru.crt;` / `ssl_certificate_key /etc/keys/www.sm-karaoke.ru.key;`.

### Новый хост `188.127.240.124`

Чистая Ubuntu 26.04: **нет** `/etc/keys`, **нет** `/etc/nginx`, **нет** docker, **нет** java/keytool.
Сертификат нужно установить с нуля.

## Данные сертификата

Идентичны для всех копий (проверено на Яндекс.Диске, старом проде, репо):

| Параметр | Значение |
|---|---|
| Subject | `CN = www.sm-karaoke.ru` |
| Issuer | `C=BE, O=GlobalSign nv-sa, CN=GlobalSign GCC R3 DV TLS CA 2020` |
| notBefore | `Mar  2 10:34:28 2026 GMT` |
| **notAfter** | **`Apr  3 10:34:27 2027 GMT`** (3 апреля 2027, ~13:34 МСК) |
| SAN | `www.sm-karaoke.ru`, `autodiscover.sm-karaoke.ru`, `mail.sm-karaoke.ru`, `owa.sm-karaoke.ru`, `sm-karaoke.ru` |
| SHA-256 fingerprint | `2D:D3:5A:E0:2E:6A:F1:0A:0A:A3:96:1A:03:8A:15:58:B8:C9:0E:33:1E:58:87:60:11:CD:D5:CE:19:59:D5:1C` |

Цепочка (из `certificate_ca.crt`):
- Intermediate: `C=BE, O=GlobalSign nv-sa, CN=GlobalSign GCC R3 DV TLS CA 2020`
  (issuer `GlobalSign Root CA - R3`, срок до 2029-03-18).
- Root: `GlobalSign Root CA - R3` (самоподписан, до 2029-03-18) — стандартный корень, есть в
  браузерах и в Java `cacerts`.

Проверка соответствия ключа сертификату: SHA-256 публичного ключа из `.crt` и из `.key` совпадают
(`93b7554cf496ac636e3ab708d1ff1d236acb4e4aef095e457a7317a1ba4f71c6`) — пара валидна.

Примечания:
- Живой `openssl s_client` с admin-машины до `sm-karaoke.ru:443` **зависает** (TCP открыт, TLS
  handshake не завершается) — известный MTU black-hole на стороне admin-машины (описан в комментариях
  `80to8897`). Данные корректно сняты через `openssl s_client` к `127.0.0.1:443` на самом проде:
  nginx отдаёт 2 сертификата (fullchain), leaf с указанными выше полями.
- DNS: `sm-karaoke.ru` → `188.119.64.111` (старый прод), `www.sm-karaoke.ru` — тот же A (согласно
  конфигу; при переезде потребуется смена DNS).

## Подходит ли для нового хоста

**Да, подходит.** X.509-сертификаты привязываются к именам (CN/SAN), а не к IP-адресу. SAN
включает оба критичных имени (`sm-karaoke.ru`, `www.sm-karaoke.ru`), поэтому на `188.127.240.124`
достаточно положить те же `.crt`/`.key` — ничего перевыпускать не нужно, пока DNS указывает на новый
хост.

Ограничение: сертификат выдан на конкретный набор имён. Если после переезда появятся новые домены —
потребуется новый/расширенный сертификат. Для текущих `sm-karaoke.ru`/`www.sm-karaoke.ru` — ОК.

## Нужен ли keytool / Java truststore

Разбор по компонентам:

- **`karaoke-web`** (`deploy/karaoke-web/Dockerfile`, `FROM eclipse-temurin:22-jre-jammy`):
  сертификат в `cacerts` **не импортируется**. В коде есть `WebClientConfig.smKaraokeWebClient` с
  `baseUrl = "https://sm-karaoke.ru/api/storage"` — потенциальный self-HTTPS-вызов. Однако эндпоинт
  `/api/storage` в `karaoke-web` отсутствует (контроллер `StorageController` живёт только в
  `karaoke-app`; в `karaoke-web` бин `WebKaraokeStorageServiceImpl` — заглушка, бросающая
  `UnsupportedOperationException`). Т.е. это legacy/dead-путь. Даже если вызов случится — публичный
  GlobalSign-сертификат доверяется стандартным `cacerts` JDK, **keytool не нужен**.

- **`karaoke-app`** (`deploy/karaoke-app/Dockerfile`): на этапе сборки образа импортирует
  `www.sm-karaoke.ru.crt` в `cacerts`:
  `keytool -import -alias www.sm-karaoke.ru -keystore ... -storepass changeit -noprompt`.
  Источник — `deploy/karaoke-app/files/www.sm-karaoke.ru.crt` (tracked в git), `do.sh` копирует его
  в `karaoke-app/build/libs/` (строки 140/161) перед `docker build`. Это **легаси от прежнего
  самоподписанного** сертификата. Для текущего публичного CA импорт не обязателен (корень GlobalSign
  уже в `cacerts`), но и не вредит.

- **`deploy/Настройка сервера.md`** (строка 79) описывает импорт в системный
  `/lib/ssl/certs/java/cacerts` на сервере — тоже легаси-инструкция.

**Вывод**: для `karaoke-web` keytool/Java truststore **не требуется**. Для переезда достаточно
установить сертификат в nginx. Импорт в образ `karaoke-app` — опционален; при сохранении важно, что
файл `deploy/karaoke-app/files/www.sm-karaoke.ru.crt` **закоммичен** и его надо обновлять при
перевыпуске.

## План перевыпуска (если нужен)

**Сейчас перевыпуск не нужен**: до истечения `2027-04-03` ~6 месяцев (на дату 2026-09-23). Решение
владельца — переиспользовать текущий сертификат — согласуется с запасом срока.

Когда срок подойдёт (рекомендуется начать за 1-1.5 месяца, т.е. с ~середины февраля 2027):

- **Вариант A — Let's Encrypt + certbot**: бесплатно, авто-renew через systemd timer, но меняет CA
  и тип валидации; не требует действий владельца после настройки.
- **Вариант B — перевыпуск у GlobalSign**: сохраняет текущий CA/цепочку (полезно, если есть внешние
  системы, придирчивые к issuer). Платный, требует ручной процедуры у CA/регистратора.
- **Вариант C — другой CA**: не рекомендуется без причины.

После перевыпуска любым вариантом: обновить `/etc/keys/www.sm-karaoke.ru.{crt,key}` на хосте,
обновить `deploy/karaoke-app/files/www.sm-karaoke.ru.crt` в репо (если оставляем импорт в образ),
`nginx -t && systemctl reload nginx`.

Текущий issuer `GlobalSign GCC R3 DV TLS CA 2020` — вероятно, требование одного из OAuth-сценариев
(VK/VK ID) или исторический выбор; при перевыпуске стоит подтвердить у владельца, нет ли требования
именно на GlobalSign.

## Чеклист переноса

Подготовка файлов (на admin-машине):

1. Собрать **fullchain** (leaf + intermediate). Нельзя класть только leaf (2738 b) — часть клиентов
   (Java/Android) не построит цепочку. Варианты:
   - взять готовый fullchain со старого прода: `scp root@188.119.64.111:/etc/keys/www.sm-karaoke.ru.crt ./fullchain.crt` (4426 b); либо
   - склеить leaf + intermediate: intermediate — первый блок из `certificate_ca.crt`.
2. Приватный ключ — `www.sm-karaoke.ru.key` (3243 b, совпадает с сертификатом по pubkey).

Установка на новый хост `188.127.240.124`:

3. `mkdir -p /etc/keys` (root:root, 755).
4. Скопировать по scp (не через git!):
   `scp fullchain.crt root@188.127.240.124:/etc/keys/www.sm-karaoke.ru.crt`
   `scp www.sm-karaoke.ru.key root@188.127.240.124:/etc/keys/www.sm-karaoke.ru.key`
5. Права: `chown root:root /etc/keys/www.sm-karaoke.ru.{crt,key}`;
   `chmod 644` на `.crt`, `chmod 600` на `.key`. Каталог `/etc/keys` — 755.

Nginx:

6. Развернуть nginx + конфиг `80to8897` (из `deploy/web-server-deploy/deploy/`), в блоке
   `server { listen 443 ssl; ... }` уже прописаны нужные пути `/etc/keys/www.sm-karaoke.ru.{crt,key}`.
7. При необходимости добавить `client_max_body_size` в `http`-блок `nginx.conf`
   (`deploy/web-server-deploy/deploy/nginx.conf` уже содержит 20M; для загрузок 200M — `/api/` в
   `80to8897` задаёт `client_max_body_size 200m`).
8. Проверка конфига: `nginx -t`.
9. Применить: `systemctl reload nginx` (**reload**, не restart — не рвёт активные соединения).
   Reload требуется только при смене/добавлении сертификата или конфига; сам факт копирования файлов
   без reload ничего не меняет.

Проверка:

10. Локально на новом хосте:
    `echo | openssl s_client -connect 127.0.0.1:443 -servername sm-karaoke.ru -showcerts | grep -c 'BEGIN CERTIFICATE'` → ожидаем `2`.
    `openssl x509 -noout -dates -subject -issuer -ext subjectAltName` → `notAfter=Apr 3 2027`, issuer GlobalSign, SAN с обоими именами.
11. Отпечаток должен совпасть с эталоном:
    `2D:D3:5A:E0:2E:6A:F1:0A:0A:A3:96:1A:03:8A:15:58:B8:C9:0E:33:1E:58:87:60:11:CD:D5:CE:19:59:D5:1C`.
12. Снаружи (после переключения DNS на `188.127.240.124`):
    `openssl s_client -connect sm-karaoke.ru:443 -servername sm-karaoke.ru` и/или браузер — цепочка валидна, без предупреждений.
13. (Опционально) если сохраняем импорт в образ `karaoke-app` — пересобрать образ; проверить, что
    `deploy/karaoke-app/files/www.sm-karaoke.ru.crt` актуален.

## Риски

1. **Leaf-only вместо fullchain.** На Яндекс.Диске лежит только leaf (2738 b), на проде —
   fullchain (4426 b). Если на новый хост скопировать файл из Яндекс.Диска как есть, Java/Android-клиенты
   могут не построить цепочку. Обязательно класть leaf + intermediate.
2. **DNS-переключение.** `sm-karaoke.ru` сейчас → `188.119.64.111`. Без смены A-записей на
   `188.127.240.124` новый nginx не получит трафик; учесть TTL и порядок (сначала поднять хост, потом
   менять DNS), иначе даунтайм.
3. **MTU black-hole.** Прямой TLS к `sm-karaoke.ru:443` с admin-машины зависает — нельзя проверять
   сертификат оттуда. Проверять с самого нового хоста (localhost) и/или внешним клиентом.
4. **Секреты.** Приватный ключ и `do.env` (Docker/ЮKassa/VK-секреты) не должны попадать в git.
   `.gitignore` уже покрывает `*.key`, `*.pem`, `deploy/**/do.env`; сертификат `.crt` — публичный, его
   коммит допустим. Переносить ключ только по scp/ssh.
5. **Устаревание копии в репо.** `deploy/karaoke-app/files/www.sm-karaoke.ru.crt` закоммичен и
   вшивается в образ. При любом перевыпуске (2027) его нужно синхронно обновить, иначе образ
   соберётся со старым сертификатом.
6. **Срок действия.** `notAfter = 2027-04-03`. Пропуск даты → сайт отдаёт просроченный сертификат.
   Нужен напоминатель/мониторинг (например, проверка `notAfter` в health-report или внешний
   монитор). На дату 2026-09-23 запас ~6 месяцев.
7. **Импорт leaf в cacerts образа karaoke-app.** Если перевыпустить сертификат, старый leaf останется
   в `cacerts` — не ломает доверие (новый тоже от публичного CA), но накапливает мусор. Рассмотреть
   удаление импорта как ненужного.
8. **Отсутствие окружения на новом хосте.** Нет docker/nginx/java, `/etc/keys` не создан — переезд
   nginx-слоя делается «с нуля», не копированием состояния.
