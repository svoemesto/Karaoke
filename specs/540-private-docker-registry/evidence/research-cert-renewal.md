# Research: как продлевается TLS-сертификат `sm-karaoke.ru` и что будет при просрочке

> **Дата исследования**: 2026-10-11.
> **Автор**: research-сессия (субагент), по заданию родительского агента (карта #274, тикет `cert`).
> **Артефакт**: разовый research-документ в `.scratch/karaoke/registry/` (не SSoT, в `knowledge/` не переносится без отдельного решения владельца).
> **Статус фактов**: помечено «на диске» — прочитано из репозитория `/home/nsa/Karaoke`; помечено «измерено» — снято живыми сетевыми запросами 2026-10-11 из этой сессии; остальное — первоисточники в интернете со ссылками.
> **Границы**: только чтение + этот один новый файл. Ничего не устанавливалось, `certbot` не запускался, nginx не менялся, на прод-хост не ходили (SSH из сессии нет). Временный PDF со страницы reg.ru был скачан, прочитан и удалён.
> **Оговорка о методе поиска**: `web_search` в этой сессии недоступен (провайдер без API-ключа) — вся работа шла прямыми `web_fetch`/`curl` по конкретным URL. Поэтому «не найдено» ниже означает «не найдено по тем URL, которые удалось открыть», а не «не существует».

---

## Вопрос

Приватный Docker-registry проекта будет доступен по `https://sm-karaoke.ru:<нестандартный порт>` под **существующим** сертификатом GlobalSign (файлы `/etc/keys/www.sm-karaoke.ru.crt` и `.key` на прод-хосте). Новый сертификат не выпускается. Значит, registry наследует все свойства чужого сертификата, включая срок жизни:

- **кто** его продлевает (панель хостинга reg.ru? кто-то ещё?) и **как**;
- есть ли **автопродление**, за сколько дней, требует ли ручного действия и кому приходят уведомления;
- **что произойдёт при просрочке** — конкретно для `docker push` (admin-машина), `docker pull` (прод) и для nginx;
- можно ли **не зависеть** от панели: выпустить сертификат на то же имя самостоятельно (Let's Encrypt HTTP-01 через порт 80 или DNS-01 через DNS reg.ru) с автоматическим продлением;
- что сделать **сейчас**, чтобы дата `notAfter` не застала врасплох.

---

## Метод

### Что прочитано на диске (репозиторий `/home/nsa/Karaoke`)

| Файл | Что взято |
|---|---|
| `deploy/prod-single-host/80to8897` | vhost: `listen 443 ssl; server_name sm-karaoke.ru www.sm-karaoke.ru; ssl_certificate /etc/keys/www.sm-karaoke.ru.crt; ssl_certificate_key /etc/keys/www.sm-karaoke.ru.key;` (строки 1–5); порт-80 блок `server { listen 80; server_name sm-karaoke.ru www.sm-karaoke.ru minio-proxy; ... location / { return 301 https://sm-karaoke.ru$request_uri; } }` (строки 122–167) |
| `deploy/prod-single-host/README.md` | состав хоста, `/etc/keys/www.sm-karaoke.ru.{crt,key}`, список таймеров (`karaoke-db-backup`, `karaoke-docker-prune`), адрес и параметры хоста |
| `deploy/Настройка сервера.md` (строки 79–89) | **ручная** процедура установки нового сертификата: положить `.crt`/`.key` на Яндекс.Диск → `scp` в `/etc/keys` → `systemctl reload nginx` → проверить `openssl s_client ... notAfter`. Никакой автоматизации в ней нет |
| `specs/165-migration-one-server-research/tls-certificate.md` | предыдущее исследование того же сертификата (2026-09-23): решение владельца **переиспользовать** текущий сертификат, а не ставить certbot; риск № 6 прямо фиксирует: «Нужен напоминатель/мониторинг (например, проверка `notAfter` в health-report или внешний монитор)» |
| `deploy/prod-single-host/*.timer`, `*.service` | на хосте из нашего репозитория есть только `karaoke-db-backup.timer` и `karaoke-docker-prune.timer` — **таймера проверки/обновления сертификата нет** |
| `grep -rn -iE 'reg\.ru\|certbot\|letsencrypt\|s_client\|notAfter' deploy/ knowledge/ docs/ specs/` | в `knowledge/` и `docs/` темы продления сертификата нет вообще; единственные упоминания — `deploy/Настройка сервера.md` (ручная процедура) и `specs/165-.../tls-certificate.md` |

### Что измерено живыми запросами (2026-10-11)

- `openssl s_client -connect {sm-karaoke.ru,www.sm-karaoke.ru,mail.sm-karaoke.ru}:443 -servername <имя>` → `subject`, `issuer`, `dates`, `subjectAltName`.
- DNS: `getent hosts` + `dig +short NS/MX/A`.

### Первоисточники в интернете

- reg.ru, справка: [Какой SSL-сертификат выбрать](https://help.reg.ru/support/ssl-sertifikaty/obshchaya-informatsiya-po-ssl/kakoy-ssl-sertifikat-vybrat), [Как заказать бесплатный SSL-сертификат](https://help.reg.ru/support/ssl-sertifikaty/1-etap-zakaz-ssl-sertifikata/kak-zakazat-besplatnyy-ssl-sertifikat), [Особенности выпуска SSL-сертификатов](https://help.reg.ru/support/ssl-sertifikaty/1-etap-zakaz-ssl-sertifikata/osobennosti-vypuska-ssl-sertifikatov), [Продление услуги по выпуску SSL-сертификата](https://help.reg.ru/support/ssl-sertifikaty/pereizdaniye-prodleniye-ssl-sertifikata/prodleniye-ssl-sertifikata), [Переиздание SSL-сертификата](https://help.reg.ru/support/ssl-sertifikaty/pereizdaniye-prodleniye-ssl-sertifikata/pereizdaniye-ssl-sertifikata), [Как узнать и изменить контактный email SSL-сертификата](https://help.reg.ru/support/ssl-sertifikaty/2-etap-aktivatsiya-ssl-sertifikata/kak-uznat-i-izmenit-kontaktnyy-email-ssl-sertifikata), [Сертификаты для почтового сервера](https://help.reg.ru/support/pochta-i-servisy/obshchiye-voprosy/sertifikaty-dlya-pochtovogo-servera), [В чём разница между ns1.reg.ru/ns2.reg.ru и ns1.hosting.reg.ru/ns2.hosting.reg.ru](https://help.reg.ru/support/dns-servery-i-nastroyka-zony/obshchaya-informatsiya-o-dns-serverakh/v-chem-raznitsa-mezhdu-dns-serverami-ns1-reg-ru-ns2-reg-ru-i-ns1-hosting-reg-ru-ns2-hosting-reg-ru).
- reg.ru, юридический первоисточник: [Правила оказания услуг по предоставлению SSL-сертификатов](https://img.reg.ru/faq/Pravila_okazania_uslug_SSL_140326.pdf) (PDF, редакция 14.03.2026; на HTML-странице help.reg.ru текст документа не отдаётся — рендерится JS, поэтому читался PDF).
- reg.ru, API: живой ответ `https://api.reg.ru/api/regru2` (измерено), [acme.sh, `dnsapi/dns_regru.sh`](https://github.com/acmesh-official/acme.sh/blob/master/dnsapi/dns_regru.sh) и [wiki acme.sh § 81 «Use reg.ru API»](https://github.com/acmesh-official/acme.sh/wiki/dnsapi2#dns_regru).
- CA/Browser Forum: [servercert/docs/BR.md](https://github.com/cabforum/servercert/blob/main/docs/BR.md) (§ 4.2.1, § 6.3.2 и таблица сроков), [Ballot SC081v3](https://cabforum.org/2025/04/11/ballot-sc081v3-introduce-schedule-of-reducing-validity-and-data-reuse-periods/).
- Go: [crypto/x509/verify.go](https://github.com/golang/go/blob/master/src/crypto/x509/verify.go) (строки с текстами ошибок).
- Docker: [dockerd reference, `--insecure-registry`](https://docs.docker.com/reference/cli/dockerd/), [Verify repository client with certificates](https://docs.docker.com/engine/security/certificates/).
- CNCF Distribution: [Deploy a registry server](https://distribution.github.io/distribution/about/deploying/).
- RFC: [RFC 8446](https://www.rfc-editor.org/rfc/rfc8446.txt) § 6.2 (AlertDescription).
- nginx: [CHANGES](https://nginx.org/en/CHANGES), [ngx_event_openssl.c](https://github.com/nginx/nginx/blob/master/src/event/ngx_event_openssl.c).
- OpenSSL: [crypto/err/openssl.txt](https://github.com/openssl/openssl/blob/master/crypto/err/openssl.txt), [include/openssl/ssl.h.in](https://github.com/openssl/openssl/blob/master/include/openssl/ssl.h.in).
- Let's Encrypt: [Challenge Types](https://letsencrypt.org/docs/challenge-types/), [Rate Limits](https://letsencrypt.org/docs/rate-limits/), [FAQ](https://letsencrypt.org/docs/faq/).
- Certbot: [User Guide](https://eff-certbot.readthedocs.io/en/stable/using.html).
- Prometheus blackbox_exporter: [prober/prober.go](https://github.com/prometheus/blackbox_exporter/blob/master/prober/prober.go).

---

## Факты

### 1. Что сейчас стоит на хосте

**Ф1.1 (измерено).** Все три имени отдают один и тот же лист:

```
subject = CN = www.sm-karaoke.ru
issuer  = C = BE, O = GlobalSign nv-sa, CN = GlobalSign GCC R3 DV TLS CA 2020
notBefore = Mar  2 10:34:28 2026 GMT
notAfter  = Apr  3 10:34:27 2027 GMT
SAN = DNS:www.sm-karaoke.ru, DNS:autodiscover.sm-karaoke.ru,
      DNS:mail.sm-karaoke.ru, DNS:owa.sm-karaoke.ru, DNS:sm-karaoke.ru
```

Источник: `echo | openssl s_client -connect <имя>:443 -servername <имя> | openssl x509 -noout -subject -issuer -dates -ext subjectAltName` для `sm-karaoke.ru`, `www.sm-karaoke.ru`, `mail.sm-karaoke.ru`.

**Ф1.2 (вычислено из Ф1.1).** `notBefore → notAfter` = **397 суток** (365 + 32). Дата истечения — 2027-04-03 10:34:27 GMT = **13:34:27 МСК**. От 2026-10-11 до истечения — **174 суток**.

**Ф1.3 (измерено).** DNS:

- `A sm-karaoke.ru`, `A www.sm-karaoke.ru`, `A mail.sm-karaoke.ru` → `188.127.240.124`;
- `NS sm-karaoke.ru` → `ns5.hosting.reg.ru`, `ns6.hosting.reg.ru`;
- `MX sm-karaoke.ru` → `10 mail.sm-karaoke.ru`, `20 mail.sm-karaoke.ru`.

Следствие: сертификат обслуживает не только сайт — MX указывает на `mail.sm-karaoke.ru`, то есть как минимум один из SAN-ов используется по назначению (почта на том же хосте).

**Ф1.4 (на диске).** nginx на хосте слушает 443 под этот сертификат и 80 с безусловным редиректом:

- `deploy/prod-single-host/80to8897:1-5` — `listen 443 ssl; server_name sm-karaoke.ru www.sm-karaoke.ru; ssl_certificate /etc/keys/www.sm-karaoke.ru.crt; ssl_certificate_key /etc/keys/www.sm-karaoke.ru.key;`
- `deploy/prod-single-host/80to8897:122-167` — `listen 80; server_name sm-karaoke.ru www.sm-karaoke.ru minio-proxy; … location / { return 301 https://sm-karaoke.ru$request_uri; }`.

**Ф1.5 (на диске).** В репозитории нет ни одной автоматизации продления/проверки сертификата: в `deploy/prod-single-host/` лежат только `karaoke-db-backup.timer` и `karaoke-docker-prune.timer`; процедура замены сертификата — ручной `scp` + `systemctl reload nginx` (`deploy/Настройка сервера.md:79-89`).

**Ф1.6 (на диске).** Предыдущее исследование `specs/165-migration-one-server-research/tls-certificate.md` (2026-09-23) уже зафиксировало риск № 6: «`notAfter = 2027-04-03`. Пропуск даты → сайт отдаёт просроченный сертификат. Нужен напоминатель/мониторинг… На дату 2026-09-23 запас ~6 месяцев». То есть **на 2026-09-23 мониторинга не было**, и он не появился (Ф1.5).

**Ф1.7 (на диске).** Ни один файл репозитория не называет поставщика сертификата: `grep -rn -iE 'reg\.ru' deploy/ knowledge/ docs/` даёт только IP-хосты и пути `scp`, ни слова о том, где сертификат заказан. **Кто и где его купил — на диске не зафиксировано.**

### 2. Что это за сертификат по продукту (reg.ru)

**Ф2.1 (первоисточник).** Справка reg.ru «Как заказать бесплатный SSL-сертификат»:

> «SSL-сертификат будет действителен как для домена `mysite.ru`, так и для поддоменов `www.mysite.ru`, `mail.mysite.ru`, `owa.mysite.ru` и `autodiscover.mysite.ru`.»

> «В качестве бесплатного сертификата предоставляется **DomainSSL**.»

Источник: <https://help.reg.ru/support/ssl-sertifikaty/1-etap-zakaz-ssl-sertifikata/kak-zakazat-besplatnyy-ssl-sertifikat>.

**Ф2.2 (первоисточник).** Справка reg.ru «Какой SSL-сертификат выбрать» — это тот же SAN-набор привязан к продукту **DomainSSL**, а у AlphaSSL он уже:

> «При заказе `DomainSSL` на один домен, сертификат защищает только домен, для которого он заказан (включая поддомены «www», «mail», «owa» и «autodiscover»).»

> «При заказе `AlphaSSL`, ТЦИ ECDSA или ТЦИ ГОСТ на один домен, сертификат защищает только домен, для которого он заказан (включая поддомен «www»).»

Источник: <https://help.reg.ru/support/ssl-sertifikaty/obshchaya-informatsiya-po-ssl/kakoy-ssl-sertifikat-vybrat>.

**Вывод по фактам 2.1–2.2 (сильный, но косвенный).** Набор SAN на хосте (`apex + www + mail + owa + autodiscover`) **дословно совпадает** с документированным профилем reg.ru для **GlobalSign DomainSSL** (сертификат начального уровня, DV, GlobalSign). У AlphaSSL профиль другой. Прямого документа, где назван именно `sm-karaoke.ru`, в открытых источниках нет — **«не найдено»**; совпадение профиля + NS reg.ru — сильная косвенная улика, но не доказательство.

**Ф2.3 (первоисточник).** Домен `sm-karaoke.ru` обслуживается **хостинговыми** NS reg.ru. Справка reg.ru:

> «Пара `ns1.hosting.reg.ru` и `ns2.hosting.reg.ru` … подходит только в том случае, если вы заказали виртуальный хостинг Рег.ру.»

> «Управление зоной в панели управления: добавлять, редактировать и удалять ресурсные записи вы можете через панель управления хостингом.»

Источник: <https://help.reg.ru/support/dns-servery-i-nastroyka-zony/obshchaya-informatsiya-o-dns-serverakh/v-chem-raznitsa-mezhdu-dns-serverami-ns1-reg-ru-ns2-reg-ru-i-ns1-hosting-reg-ru-ns2-hosting-reg-ru>.

Следствие (Ф1.3 + Ф2.3): зона домена живёт в **панели хостинга reg.ru**, то есть у владельца домена есть услуга хостинга в reg.ru, и SSL, скорее всего, заказан из того же личного кабинета.

**Ф2.4 (первоисточник).** Справка reg.ru про почтовые сертификаты объясняет, зачем вообще сертификату SAN-ы `mail/owa/autodiscover`:

> «Единственная проблема, с которой вы можете столкнуться, это несоответствие доменного имени в сертификате для почтового сервера (`mail.hosting.reg.ru`) и доменного имени, на базе которого создана ваша почта. … Если вы хотите, чтобы имя домена в адресе и сертификате соответствовало, установите сертификат на почтовый домен.»

Источник: <https://help.reg.ru/support/pochta-i-servisy/obshchiye-voprosy/sertifikaty-dlya-pochtovogo-servera>.

**Ф2.5 (вычислено из Ф2.1 и Ф1.2).** Бесплатный SSL у reg.ru подключается на **6 месяцев** (Ф3.2). Наш лист живёт **397 суток** — то есть это не бесплатный тариф, а **оплаченная годовая услуга** (либо платное продление после бесплатного периода). Это **вывод**, а не цитата.

### 3. Механизм продления у reg.ru

**Ф3.1 (первоисточник, юридический).** «Правила оказания услуг по предоставлению SSL-сертификатов» (ред. 14.03.2026), п. 2.7:

> «SSL-сертификат предоставляется на ограниченный срок, указанный в заказе… Процедура продления SSL-сертификата осуществляется **Заказчиком путем самостоятельного заказа и оплаты нового сертификата**, если для соответствующего SSL-сертификата (в т.ч. для услуги «GlobaslSign 199x2») **не подключено Автопродление** услуги в соответствии с п. 5.14. Договора.»

Источник: <https://img.reg.ru/faq/Pravila_okazania_uslug_SSL_140326.pdf>, п. 2.7.

**Ф3.2 (первоисточник).** Там же, п. 2.7.1:

> «**Автопродление услуги устанавливается** … **для SSL-сертификатов уровня AlphaSSL и DomainSSL**. В рамках Автопродления услуги Исполнитель направляет в Центр Сертификации заказ на выпуск нового SSL-сертификата… **за 15 (пятнадцать) календарных дней** до истечения срока действия подлежащего продлению SSL-сертификата **при условии наличия на лицевом счете Заказчика денежных средств**, достаточных для оплаты заказа. Заказчик вправе отключить Автопродление услуги для соответствующего SSL-сертификата не позднее направления Исполнителем заказа в Центр Сертификации.»

> «Автопродление услуги **недоступно** для SSL-сертификатов уровня ТЦИ SSL ГОСТ, ТЦИ SSL ECDSA, OrganizationSSL и ExtendedSSL. При использовании вышеуказанных SSL-сертификатов, а также **в случае отключения Автопродления** услуги для иных SSL-сертификатов **и/или несвоевременной оплаты заказа, Заказчик самостоятельно несет риски, связанные с истечением срока действия SSL-сертификата**.»

Источник: тот же PDF, п. 2.7.1.

**Ф3.3 (первоисточник, справка; расходится с Ф3.2).** Справка «Продление услуги по выпуску SSL-сертификата»:

> «Автопродление … доступно **только для SSL-сертификатов Начального уровня от компании GlobalSign**, при автопродлении сертификат продлевается по цене нового сертификата, **если все услуги приобретены в Рег.ру, то переустановка на хостинг не требуется**, **автопродление происходит за 9 дней** до окончания срока действия предыдущего сертификата. Первая попытка списания происходит с баланса аккаунта. Если средств недостаточно, то с привязанной карты.»

> «Важно: вы можете продлить услугу по выпуску SSL-сертификата из карточки услуги только **в течение 30 дней** до окончания действия сертификата. … Кнопка «Продлить» становится активна только за 30 дней до окончания действия сертификата.»

Источник: <https://help.reg.ru/support/ssl-sertifikaty/pereizdaniye-prodleniye-ssl-sertifikata/prodleniye-ssl-sertifikata>.

**Расхождение, важное для планирования:** справка говорит «за 9 дней», юридические правила — «за 15 календарных дней». Оба — первоисточники reg.ru. Точную дату срабатывания автопродления надо считать по консервативной оценке: **за 15 дней**, то есть **≈ 2027-03-19** (при включённом автопродлении).

**Ф3.4 (первоисточник).** Смена модели выпуска с 14.03.2026 — справка «Особенности выпуска SSL-сертификатов»:

> «С 14 марта 2026 года в услугу выпуска SSL-сертификатов GlobalSign входит выпуск **двух сертификатов сроком действия 199 дней каждый**. Первый сертификат будет действителен в течение первых 199 дней. Второй сертификат действует 199 дней с момента его выпуска… В сумме оба сертификата покрывают период примерно в **398 дней**, что и составляет годовую услугу.»

> «**Если вы пользуетесь хостингом Рег.ру** … За 30 дней до окончания система инициирует автоматическое продление. Сертификат будет автоматически выпущен и установлен на хостинг без вашего участия. По окончании процесса вам на почту придет сообщение о том, что сертификат успешно выпущен и установлен.»

> «**Если вы пользуетесь хостингом не Рег.ру** … вам на почту придет письмо о том, что сертификат готов. **Вам нужно самостоятельно скачать файлы сертификата и установить его.** Все необходимые файлы будут высланы в письме.»

> «**Если вы используете DNS-серверы не Рег.ру** … За 30 дней до окончания срока действия первого сертификата вам на почту придет письмо… Вам нужно будет пройти процедуру подтверждения домена (например, добавить TXT-запись у своего DNS-провайдера) **в течение 30 дней**. Если подтверждение не будет пройдено в этот срок, выпуск второго сертификата будет отменен Удостоверяющим центром (УЦ) GlobalSign.»

Источник: <https://help.reg.ru/support/ssl-sertifikaty/1-etap-zakaz-ssl-sertifikata/osobennosti-vypuska-ssl-sertifikatov>.

**Ф3.5 (первоисточник, юридический).** Там же в PDF, п. 3.12 «Особые условия оказания услуги «GlobalSign 199x2»»:

> п. 3.12.1: «поочередный выпуск двух SSL-сертификатов. Срок действия первого сертификата составляет 199 … дней. По истечении срока действия первого сертификата … Исполнитель обеспечивает выпуск второго сертификата. Срок действия второго сертификата составляет 199 … дней.»

> п. 3.12.2: «Оплата услуги «GlobalSign 199x2» осуществляется по тарифу, установленному для соответствующих SSL-сертификатов **за 1 год**, перед выпуском первого сертификата.»

> п. 3.12.4: «…Заказчику необходимо пройти вышеуказанную процедуру [подтверждения доменного имени] в период, начинающийся **за 30 (тридцать) календарных дней** до даты окончания срока действия первого сертификата и заканчивающийся **через 14 (четырнадцать) календарных дней после** указанной даты. Если подтверждение доменного имени не будет осуществлено Заказчиком в указанный срок, **второй сертификат не подлежит выпуску, при этом возврат денежных средств Заказчику не осуществляется**.»

Источник: <https://img.reg.ru/faq/Pravila_okazania_uslug_SSL_140326.pdf>, п. 3.12.

**Ф3.6 (первоисточник, юридический).** Там же, п. 3.9 — про автоматическую TXT-запись:

> «Заказчик согласен, что в целях предоставления SSL-сертификата, **в случае если на доменном имени присутствуют DNS серверы Исполнителя, Исполнитель может самостоятельно вносить изменения в TXT (Text string) DNS запись**. При внесении изменений в TXT … Исполнитель затрагивает только записи необходимые для предоставления SSL-сертификата, в иные записи, добавленные Заказчиком, изменения не вносятся.»

Источник: тот же PDF, п. 3.9.

**Ф3.7 (первоисточник, юридический).** Там же, п. 3.10:

> «Исполнитель получает согласие от Заказчика на автоматическое продление действия SSL-сертификата уровней, определенных в п. 2.7.1. Правил … и на **автоматическую настройку (замену, привязку и так далее) SSL-сертификата к услугам Заказчика**.»

Источник: тот же PDF, п. 3.10.

### 4. Кому приходят уведомления

**Ф4.1 (первоисточник).** Справка «Как узнать и изменить контактный email SSL-сертификата»:

> «Процедура выдачи SSL-сертификата не подразумевает изменения email, так **при заказе вы самостоятельно указываете все данные** и несёте ответственность за их корректность. **После активации платного SSL-сертификата вы не сможете изменить контактный email.**»

> Про бесплатный: «При его заказе вы не указываете email, поэтому в качестве контактных данных устанавливается **email той услуги, из карточки которой он был заказан**: домена или хостинга.»

Адрес виден в личном кабинете: «Во вкладке **Сведения о сертификате** внизу страницы указан контактный e-mail SSL-сертификата».

Источник: <https://help.reg.ru/support/ssl-sertifikaty/2-etap-aktivatsiya-ssl-sertifikata/kak-uznat-i-izmenit-kontaktnyy-email-ssl-sertifikata>.

Следствие: **кому именно уходит письмо о продлении — из этой сессии определить нельзя.** Это контактный e-mail услуги SSL (для платного — заданный при заказе), и он виден только в личном кабинете reg.ru.

### 5. Почему срок 397 дней и что изменится на следующем продлении

**Ф5.1 (первоисточник).** Базовые требования CA/Browser Forum, таблица в § 1.2.1 и текст § 6.3.2 — расписание сокращения максимального срока действия публичных TLS-сертификатов:

| Сертификат выпущен | Максимальный срок действия |
|---|---|
| до 2026-03-15 | **398 дней** |
| с 2026-03-15 | **200 дней** |
| с 2027-03-15 | **100 дней** |
| с 2029-03-15 | **47 дней** |

Источник: <https://github.com/cabforum/servercert/blob/main/docs/BR.md> (строки таблицы: `| 2026-03-15 | 6.3.2 | Maximum validity period of Subscriber Certificates is 200 days. |`, далее 2027-03-15 → 100 days, 2029-03-15 → 47 days). Баллотирование: [Ballot SC081v3](https://cabforum.org/2025/04/11/ballot-sc081v3-introduce-schedule-of-reducing-validity-and-data-reuse-periods/) — «Eventual reduction of maximum validity period from 398 days to 47 days… starting in March 2026 and concluding in March 2029».

**Ф5.2 (вычислено из Ф5.1 и Ф1.2).** Наш лист выпущен **2026-03-02** — за 13 дней до отсечки 2026-03-15, и живёт **397 суток**, то есть ровно в пределах старого лимита 398 дней. Это объясняет и «две по 199 дней» у reg.ru: 199 + 199 = 398 ≈ срок годовой услуги, который раньше покрывался **одним** сертификатом, а с 2026-03-15 — **двумя**.

**Ф5.3 (следствие, вывод).** Текущий сертификат на хосте — «старого образца»: один лист на весь год. **Начиная со следующего продления** (весна 2027) сертификат будет выпущен максимум на 200 дней, то есть **менять файл в `/etc/keys/` придётся примерно дважды в год**, а не раз в год. Это надо закладывать в runbook и в период мониторинга.

### 6. Что произойдёт при просрочке — docker

**Ф6.1 (первоисточник, код).** Go-клиент (`docker`/`containerd` написан на Go) при истёкшем сертификате возвращает ошибку `x509.CertificateInvalidError` с причиной `Expired`. Тексты — из `src/crypto/x509/verify.go`:

```go
// строка 73-78
func (e CertificateInvalidError) Error() string {
    switch e.Reason {
    …
    case Expired:
        return "x509: certificate has expired or is not yet valid: " + e.Detail
```

Detail формируется как `"current time %s is after %s"` (строка 472–475, аргументы — текущее время и `NotAfter`). Другие причины дают другие строки: `"x509: certificate signed by unknown authority"` (строка 158) — неизвестный издатель; `"x509: certificate is valid for <имена>, not <хост>"` (строка 143) — несовпадение имени.

Источник: <https://github.com/golang/go/blob/master/src/crypto/x509/verify.go>.

**Ф6.2 (первоисточник).** Docker различает «registry считается доверенным» и «registry недоверенный». `--insecure-registry` — это флаг демона:

> «`--insecure-registry list` — Enable insecure registry communication.»

> «If an insecure registry isn't marked as insecure, `docker pull`, `docker push`, and `docker search` result in error messages, prompting the user to either secure or pass the `--insecure-registry` flag… Enabling `--insecure-registry`, i.e., **allowing un-encrypted and/or untrusted communication** … because its use creates security vulnerabilities it should only be enabled for testing purposes. For increased security, users should add their CA to their system's list of trusted CAs instead of enabling `--insecure-registry`.»

Источник: <https://docs.docker.com/reference/cli/dockerd/>.

**Ф6.3 (первоисточник).** Доверие конкретному CA — каталог на **хост:порт**:

> «A custom certificate is configured by creating a directory under the daemon's certificate root using the same name as the registry's hostname… **All `*.crt` files are added to this directory as CA roots.** … Native Linux Engine: `/etc/docker/certs.d/` … `/etc/docker/certs.d/ <-- Certificate directory` / `└── localhost:5000 <-- Hostname:port` / `├── client.cert` / `├── client.key` / `└── ca.crt`»

Источник: <https://docs.docker.com/engine/security/certificates/>.

Следствие: для нестандартного порта каталог — `/etc/docker/certs.d/sm-karaoke.ru:<порт>/ca.crt` (в Linux двоеточие в имени каталога допустимо; оговорка про «strip the colon» в документации относится к Windows). **Но** добавление CA в доверенные **не отменяет проверку срока** — Go-верификатор проверяет `NotAfter` независимо от того, известен ли издатель (Ф6.1). Прямой цитаты «trusted CA не спасает от просрочки» в документации Docker **нет** — это **вывод из кода Go**, а не цитата; помечаю как «не подтверждено цитатой».

**Ф6.4 (первоисточник).** CNCF Distribution о `X509`-ошибках:

> «X509 errors usually indicate that you are attempting to use a self-signed certificate without configuring the Docker daemon correctly. See run an insecure registry.»

Источник: <https://distribution.github.io/distribution/about/deploying/>.

**Ф6.5 (следствие из Ф6.1–Ф6.4, вывод).** При просрочке:
- `docker push` с admin-машины (`nsa-i9`) — падает с `x509: certificate has expired or is not yet valid: current time … is after 2027-04-03T10:34:27Z`;
- `docker pull` на проде — падает так же; **деплой встаёт**;
- обойти можно только `--insecure-registry` (или `insecure-registries` в `daemon.json`), но сам Docker пишет, что это «для тестирования» и «создаёт уязвимости». Это ровно то, от чего проект отказался ещё на этапе выбора софта (задача #274: «без `insecure-registries`»).

### 7. Что произойдёт при просрочке — nginx

**Ф7.1 (первоисточник, код).** `ngx_ssl_certificate()` в `src/event/ngx_event_openssl.c` загружает лист и цепочку, вызывает `SSL_CTX_use_certificate()` и `SSL_CTX_set0_chain()`; при ошибке пишет `cannot load certificate "%s": %s` / `SSL_CTX_use_certificate("%s") failed`. **Сравнения `NotAfter` с текущим временем в этой функции нет.**

Источник: <https://github.com/nginx/nginx/blob/master/src/event/ngx_event_openssl.c> (функция `ngx_ssl_certificate`, ~строки 474–560).

**Ф7.2 (первоисточник).** В CHANGES nginx про проверку сертификатов при разборе конфигурации сказано только про **отсутствующие**:

> nginx 1.15.0: «Change: now nginx detects **missing** SSL certificates during configuration testing when using the "ssl" parameter of the "listen" directive.»

Источник: <https://nginx.org/en/CHANGES>. Упоминаний проверки **срока действия** при старте в CHANGES нет.

**Ф7.3 (следствие из Ф7.1–Ф7.2, вывод).** nginx с просроченным сертификатом **запускается и продолжает работать**: он просто отдаёт лист в handshake, не проверяя его срок. Разрушение происходит на стороне клиента. Утверждение «nginx падает при просроченном сертификате» — **не найдено в первоисточниках nginx**; документально подтверждается только проверка на *отсутствие* файла при `nginx -t`.

**Ф7.4 (первоисточник, RFC).** Клиент, отвергая просроченный сертификат, посылает TLS-алерт 45:

> «`certificate_expired(45)`» — в списке `AlertDescription`;
> «certificate_expired: A certificate has expired or is not currently valid.»

Источник: [RFC 8446](https://www.rfc-editor.org/rfc/rfc8446.txt) § 6.2.

**Ф7.5 (первоисточник, OpenSSL).** Текст этой ошибки в OpenSSL:

> `SSL_R_TLS_ALERT_CERTIFICATE_EXPIRED:1045:tls alert certificate expired`
> `#define SSL_AD_CERTIFICATE_EXPIRED SSL3_AD_CERTIFICATE_EXPIRED`

Источники: [crypto/err/openssl.txt](https://github.com/openssl/openssl/blob/master/crypto/err/openssl.txt), [include/openssl/ssl.h.in](https://github.com/openssl/openssl/blob/master/include/openssl/ssl.h.in).

Следствие: в `error_log` nginx при отвергнутом handshake появляется строка вида `SSL_do_handshake() failed (SSL: … tls alert certificate expired …) while SSL handshaking` — точную формулировку конкретно для 1.28.3 в этой сессии подтвердить не удалось: **«не найдено»**; подтверждены только имя алерта (RFC 8446) и строка причины OpenSSL.

**Ф7.6 (следствие, вывод).** Для HTTP-трафика сайта просрочка означает не «падение nginx», а «все браузеры и клиенты показывают ошибку сертификата»: сайт технически отвечает, но HTTPS невалиден. Для почты (Ф1.3) — то же самое для IMAP/SMTP-клиентов.

### 8. Альтернатива: свой сертификат на то же имя

**Ф8.1 (первоисточник).** HTTP-01 challenge в Let's Encrypt:

> «Let's Encrypt gives a token to your ACME client, and your ACME client puts a file on your web server at `http://<YOUR_DOMAIN>/.well-known/acme-challenge/<TOKEN>`. … Our implementation of the HTTP-01 challenge **follows redirects, up to 10 redirects deep**. It only accepts redirects to "http:" or "https:", and only to **ports 80 or 443**. When redirected to an HTTPS URL, it **does not validate certificates**…»

> «**The HTTP-01 challenge can only be done on port 80.** Allowing clients to specify arbitrary ports would make the challenge less secure, and so it is not allowed by the ACME standard.»

> «Cons: … **This challenge cannot be used to issue wildcard certificates.**»

Источник: <https://letsencrypt.org/docs/challenge-types/>.

**Ф8.2 (первоисточник).** DNS-01 challenge:

> «This challenge asks you to prove that you control the DNS for your domain name by putting a specific value in a **TXT record** under that domain name. … your client will create a TXT record derived from that token and your account key, and put that record at **`_acme-challenge.<YOUR_DOMAIN>`**. … It also **allows you to issue wildcard certificates**.»

> «Since automation of issuance and renewals is really important, it only makes sense to use DNS-01 challenges if your DNS provider **has an API** you can use to automate updates. … Note that putting your full DNS API credentials on your web server significantly increases the impact if that web server is hacked.»

Источник: <https://letsencrypt.org/docs/challenge-types/>.

**Ф8.3 (первоисточник).** Срок жизни сертификатов Let's Encrypt и рекомендуемая частота продления:

> «Our default certificates are valid for **90 days**. … Subscribers can opt in to short-lived certificates which are valid for **six days**. … There is **no way to adjust these lifetimes**, there are no exceptions. We recommend renewing 90 day certificates **every 60 days** and six day certificates every three days.»

Источник: <https://letsencrypt.org/docs/faq/>.

**Ф8.4 (первоисточник).** Лимиты Let's Encrypt, релевантные для одного домена (из многочисленных лимитов выбраны те, что могут ударить):

| Лимит | Значение |
|---|---|
| New Certificates per Registered Domain | **50 сертификатов на домен за 7 дней**, глобально, пополнение 1 сертификат / 202 мин |
| New Certificates per Exact Set of Identifiers | **5 сертификатов на точный набор имён за 7 дней**, глобально, пополнение 1 / 34 ч |
| New Orders per Account | 300 заказов / 3 ч |
| Failed Validation | (см. раздел «Failed Validation» документации) |

Источник: <https://letsencrypt.org/docs/rate-limits/>.

Следствие: для одного домена лимиты практически недостижимы при нормальной эксплуатации; «точный набор имён» (5 сертификатов на набор за 7 дней) — тот лимит, в который упираются при отладке с `--force-renewal`, поэтому отладку надо вести на staging (там же: «please utilize our staging environment»).

**Ф8.5 (первоисточник).** Certbot: как ставит сертификат и как продлевает.

- Плагин nginx правит конфигурацию сервера: «Installers are plugins which can **automatically modify your web server's configuration** …»; «The Nginx plugin should work for most configurations. **We recommend backing up Nginx configurations before using it** (though you can also revert changes to configurations with `certbot --nginx rollback`).»
- webroot: «The webroot plugin works by creating a temporary file for each of your requested domains in **`${webroot-path}/.well-known/acme-challenge`**. … Note that to use the webroot plugin, **your server must be configured to serve** those files…»
- standalone: «This plugin **needs to bind to port 80** … so you may need to **stop your existing webserver**.»
- Где лежат файлы: «All generated keys and issued certificates can be found in **`/etc/letsencrypt/live/$domain`**… Rather than copying, please point your (web)server configuration directly to those files (or create symlinks). During the renewal, `/etc/letsencrypt/live` is updated with the latest necessary files.»
- Порог продления: «As of **Certbot 4.0.0**, a certificate is considered ready for renewal when **less than 1/3rd of its lifetime** remains. For certificates with a lifetime of 10 days or less, that threshold is 1/2 of the lifetime. **Prior to Certbot 4.0.0 the threshold was a fixed 30 days.**» (для 90-дневного сертификата это ≈ 30 дней).
- Планировщик: «Run the following line, which will add a cron job to `/etc/crontab`: `0 0,12 * * * root sleep $SLEEPTIME && certbot renew -q`»; проверить, есть ли уже таймер: «look for the `certbot renew` command in either your system's crontab … or systemd timers (`systemctl list-timers`)».
- Хуки: «The renew command includes hooks for running commands or scripts **before or after** a certificate is renewed» (`/etc/letsencrypt/renewal-hooks/{pre,post}/`).

Источник: <https://eff-certbot.readthedocs.io/en/stable/using.html>.

**Ф8.6 (первоисточник, ключевой для нашего nginx).** Порт-80 vhost для `sm-karaoke.ru` **весь редиректит** на HTTPS (Ф1.4), а в 443-vhost локации `/.well-known/acme-challenge/` нет — `location /` там проксирует в `karaoke-web` (`127.0.0.1:8897`). Значит:

- `certonly --webroot -w <каталог>` **сам по себе не заработает**: файл из webroot некому отдать. LE пойдёт по редиректу на HTTPS и получит 404 от Spring-приложения.
- `--standalone` тоже не заработает без остановки nginx: порт 80 занят (Ф8.5).
- Рабочие варианты: (а) добавить в `server { listen 80; }` отдельный `location ^~ /.well-known/acme-challenge/ { root <каталог>; }` **выше** `location /` — тогда LE заберёт файл по HTTP, не следуя редиректу (LE принимает ответ по порту 80 напрямую); (б) `certbot --nginx` — он сам правит конфиг (но и сам же его меняет, см. Ф8.5); (в) DNS-01 вообще без правок nginx.

Это **вывод из прочитанных конфигов** (Ф1.4) и документации LE/Certbot (Ф8.1, Ф8.5); прямой цитаты «в вашем конфиге HTTP-01 не сработает» нигде нет.

**Ф8.7 (первоисточник).** У reg.ru **есть** DNS API, и у acme.sh есть готовый плагин:

- Плагин: `dnsapi/dns_regru.sh`, заголовок: `dns_regru_info='reg.ru / Site: reg.ru / Docs: github.com/acmesh-official/acme.sh/wiki/dnsapi2#dns_regru / Options: REGRU_API_Username Username, REGRU_API_Password Password'`; в коде — `REGRU_API_URL="https://api.reg.ru/api/regru2"` и вызов `POST zone/add_txt` с `input_data={…"domains":[{"dname":"$_domain"}],"subdomain":…,"text":"<txtvalue>"…}`.
- Живая проверка API (измерено): `GET https://api.reg.ru/api/regru2` → HTTP 200, тело `{"error_code":"ONLY_POST_ALLOWED", "error_text":"Only HTTP method POST allowed", "result":"error"}` — то есть эндпоинт существует и отвечает.
- Wiki acme.sh, § 81: `export REGRU_API_Username='…'; export REGRU_API_Password='…'; ./acme.sh --issue --dns dns_regru -d 'example.com' -d '*.example.com'`, «The `REGRU_API_Username` and `REGRU_API_Password` will be saved in `~/.acme.sh/account.conf` and will be reused when needed.»

Источники: <https://github.com/acmesh-official/acme.sh/blob/master/dnsapi/dns_regru.sh>, <https://github.com/acmesh-official/acme.sh/wiki/dnsapi2#dns_regru>.

**Чего подтвердить не удалось (Ф8.7, честно):**
- Официальную страницу документации API reg.ru найти не удалось: `https://www.reg.ru/company/api` и `https://www.reg.ru/reseller/api` отдают HTTP 200, но **пустой HTML** (рендерится JS). Статей про API/DNS-API в `sitemap.xml` справки reg.ru **нет**.
- Плагина `dns_regru` у **certbot** не найдено (в списке DNS-плагинов certbot его нет; сторонний плагин не искался). Для certbot+reg.ru остаётся только HTTP-01 или `--manual` с хуком.
- Работает ли `api.reg.ru/api/regru2/zone/add_txt` для зоны, которая обслуживается **хостинговыми** NS `*.hosting.reg.ru` (Ф2.3) — **не найдено**. Это надо проверять живым запросом с реальными кредами.

**Ф8.8 (первоисточник).** Мониторинг срока — есть готовая метрика у Prometheus blackbox_exporter:

> `probe_ssl_earliest_cert_expiry` — «Returns last SSL chain expiry in unixtime»; рядом `probe_ssl_last_chain_expiry_timestamp_seconds`.

Источник: <https://github.com/prometheus/blackbox_exporter/blob/master/prober/prober.go>. **Но** на прод-хосте 3.8 ГБ RAM и Prometheus-стека в проекте нет (в репозитории стек не найден) — тащить его ради одной метрики не стоит; проще скрипт + systemd timer (см. «Рекомендация»).

---

## Вывод

**Кто продлевает.** Сертификат — публичный DV GlobalSign **начального уровня**, по профилю имен совпадающий с продуктом reg.ru **DomainSSL** (`apex + www + mail + owa + autodiscover`; этот набор SAN дословно описан в справке reg.ru как свойство именно DomainSSL). Домен обслуживается **хостинговыми** NS reg.ru, зона лежит в панели хостинга reg.ru. Продлевает его **владелец аккаунта reg.ru** — либо вручную (заказ + оплата + скачивание + установка), либо через услугу **«Автопродление»**. **Ни проекта Karaoke, ни наш nginx в этой процедуре не участвуют**: на диске нет ни таймера, ни certbot, ни мониторинга, а процедура замены — ручной `scp` в `/etc/keys` + `systemctl reload nginx` (`deploy/Настройка сервера.md:79-89`). Прямого документа, где назван именно `sm-karaoke.ru`, найти не удалось — привязка к reg.ru построена на совпадении профиля SAN и NS **«не найдено» → косвенная**.

**Есть ли автопродление.** **Механизм существует и применим ровно к этому классу сертификатов** (AlphaSSL/DomainSSL, п. 2.7.1 правил reg.ru), срабатывает **за 15 календарных дней** до истечения (справка говорит «за 9 дней» — расхождение внутри reg.ru) и требует **денег на лицевом счёте** (или привязанной карты). **Включено ли оно для `sm-karaoke.ru` — из этой сессии установить невозможно:** это переключатель в карточке услуги в личном кабинете. И даже при включённом автопродлении **нельзя рассчитывать, что новый файл сам появится в `/etc/keys/`**: документально подтверждена автоустановка только «на хостинг Рег.ру» (Ф3.4) и «автоматическая настройка… к услугам Заказчика» (Ф3.7); наш host-nginx на VPS — не услуга панели reg.ru. Это **вывод**, не цитата.

**Что будет при просрочке.** nginx **не упадёт** — он не проверяет `NotAfter` (Ф7.1–Ф7.3); он продолжит отдавать просроченный лист, а клиенты начнут рвать handshake с алертом `certificate_expired(45)` (Ф7.4–Ф7.5). Для проекта важен не сайт, а registry: Docker написан на Go, и `docker push` с admin-машины и `docker pull` на проде будут падать с `x509: certificate has expired or is not yet valid: current time … is after 2027-04-03T10:34:27Z` (Ф6.1). **Деплой встанет.** Обход через `--insecure-registry` технически есть (Ф6.2), но это ровно то, от чего проект отказался при выборе registry.

**Что делать.** Не полагаться на чужую панель. Продление сертификата надо **забрать себе**: выпускать Let's Encrypt на то же имя `sm-karaoke.ru` и продлевать автоматически (certbot `renew` дважды в день + post-hook `systemctl reload nginx`), а GlobalSign оставить как есть до истечения — конфликта между сертификатами нет, они сосуществуют, nginx просто указывает на файлы. Плюс — прямо сейчас, не дожидаясь registry, поставить **мониторинг `notAfter`**, потому что после 2026-03-15 сроки публичных TLS-сертификатов упали до 200 дней (Ф5.1), и следующий сертификат будет жить **вдвое меньше**, а значит и проверять его надо вдвое чаще.

**Три даты для календаря (2027):**

| Дата | Что | При каком условии |
|---|---|---|
| **2027-03-04** | открывается окно ручного продления у reg.ru (за 30 дней) | если автопродление **выключено** |
| **2027-03-19** | reg.ru отправляет заказ в УЦ (за 15 дней) | если автопродление **включено** |
| **2027-04-03 13:34 МСК** | `notAfter` — с этой секунды registry недоступен | всегда |

**Отдельно про «кто получает письма».** Уведомления идут на **контактный e-mail услуги SSL** (для платного — заданный при заказе и **неизменяемый после активации**; для бесплатного — e-mail родительской услуги домена/хостинга). Адрес виден в личном кабинете: «SSL-сертификаты → сертификат → вкладка Сведения о сертификате» (Ф4.1). **Знает ли этот ящик кто-то из нас — неизвестно; это первый вопрос, который надо закрыть, потому что без него мы не узнаем о продлении вообще.**

---

## Рекомендация

Порядок — по возрастанию стоимости и по убыванию срочности. Шаги 1–2 можно (и нужно) сделать, не дожидаясь registry.

### Шаг 1. Сегодня: выяснить владельца услуги и включить мониторинг (без правок на проде)

1.1. **Владельцу — зайти в личный кабинет reg.ru** и по каждому пункту зафиксировать факт (это единственный способ получить ответы; из сессии доступ невозможен):
   - есть ли услуга SSL для `sm-karaoke.ru`, её тип (DomainSSL/AlphaSSL/…), дата окончания;
   - состояние переключателя **«Автопродление» (ON/OFF)**;
   - **контактный e-mail** сертификата (вкладка «Сведения о сертификате») — кто его читает;
   - есть ли на лицевом счёте деньги / привязана ли карта;
   - какой именно услуге хостинга принадлежит домен (NS `ns5/ns6.hosting.reg.ru` → зона в панели хостинга).

1.2. **Мониторинг `notAfter` — независимо от ответов п. 1.1.** Без него мы узнаем о просрочке по упавшему деплою. Минимальная рабочая схема (admin-машина, не прод):

```bash
# /usr/local/bin/cert-expiry-check.sh
#!/bin/sh
set -eu
HOST=sm-karaoke.ru; PORT=443
WARN=45                       # дней
END=$(echo | openssl s_client -connect "$HOST:$PORT" -servername "$HOST" 2>/dev/null \
      | openssl x509 -noout -enddate | cut -d= -f2)
END_EPOCH=$(date -d "$END" +%s); NOW=$(date +%s)
DAYS=$(( (END_EPOCH - NOW) / 86400 ))
echo "cert $HOST:$PORT notAfter=$END days_left=$DAYS"
[ "$DAYS" -lt "$WARN" ] && echo "ALERT: сертификат истекает через $DAYS дн." >&2 && exit 1
exit 0
```

   Запускать `systemd`-таймером раз в сутки (или в существующем health-report, как и предлагал риск № 6 спеки 165). Важно: проверять **и порт 443, и порт registry** (`-connect sm-karaoke.ru:<порт>`), а также **`mail.sm-karaoke.ru`** — там тот же лист, но свой путь трафика. Порог предупреждения — **45 дней**, а не 14: после 2026-03-15 сертификат живёт 200 дней, и 45 дней — это уже четверть срока.

1.3. **Записать в runbook** (в `deploy/Настройка сервера.md`, рядом с существующей процедурой) три даты из «Вывода» и явный факт: **замена сертификата — ручная операция `scp` + `systemctl reload nginx`**, автоматики нет.

### Шаг 2. До 2027-03: забрать продление себе (Let's Encrypt на то же имя)

Цель — чтобы `notAfter` перестала быть чужой датой. Рекомендуемый вариант — **DNS-01**, он не зависит ни от порта 80, ни от состояния сайта, ни от того, кто владеет панелью reg.ru.

2.1. **Сначала — HTTP-01 попроще, если готовы на маленькую правку nginx** (вариант «а»):

   - в `deploy/prod-single-host/80to8897`, в блок `server { listen 80; … }` (строка 122), **выше** `location /` (строка 164) добавить:
     ```nginx
     location ^~ /.well-known/acme-challenge/ {
       root /var/www/acme;      # создаётся один раз, 755
       default_type "text/plain";
       allow all;
     }
     ```
     Тогда LE заберёт токен по HTTP напрямую и **не пойдёт** по редиректу на HTTPS (Ф8.1, Ф8.6). Редирект для всех остальных путей сохраняется.
   - выпуск **без правки TLS-блока** (не трогаем сертификат, который работает сейчас):
     ```bash
     certbot certonly --webroot -w /var/www/acme \
       -d sm-karaoke.ru -d www.sm-karaoke.ru \
       --deploy-hook "systemctl reload nginx"
     ```
   - в `80to8897` в 443-блок переключить пути на LE-файлы **только после того, как выпуск проверен**:
     ```nginx
     ssl_certificate     /etc/letsencrypt/live/sm-karaoke.ru/fullchain.pem;
     ssl_certificate_key /etc/letsencrypt/live/sm-karaoke.ru/privkey.pem;
     ```
     `nginx -t && systemctl reload nginx` (reload, не restart — не рвём активные соединения).
   - продление — штатное: `certbot renew` дважды в день (Debian/Ubuntu ставят `certbot.timer`; проверить `systemctl list-timers | grep certbot`), порог — 1/3 срока (≈30 дней для 90-дневного листа) (Ф8.5).

   **Почему именно так, а не `certbot --nginx`:** плагин nginx **сам правит** конфигурацию, а `80to8897` — 167 строк с тонко настроенными `proxy_*`, SSE-стримами и MTU-обходами; автоправка там — лишний риск. `certonly --webroot` + явная правка двух строк предсказуемее.

2.2. **Вариант «б» — DNS-01 через acme.sh (`dns_regru`), если хочется ноль правок nginx** (Ф8.7):

   ```bash
   export REGRU_API_Username='…' REGRU_API_Password='…'   # креды аккаунта reg.ru
   ./acme.sh --issue --dns dns_regru -d sm-karaoke.ru -d www.sm-karaoke.ru
   ./acme.sh --install-cert -d sm-karaoke.ru \
     --fullchain-file /etc/keys/le.sm-karaoke.ru.crt \
     --key-file       /etc/keys/le.sm-karaoke.ru.key \
     --reloadcmd      "systemctl reload nginx"
   ```
   Плюсы: работает, даже если сайт лежит; не зависит от порта 80; не требует wildcard-ов, но допускает их. Минусы: (1) на сервере появляются **полные DNS-креды** аккаунта reg.ru — LE прямо предупреждает, что это сильно повышает ущерб при компромате сервера, и советует узкие креды или вынос валидации на отдельный хост (Ф8.2); (2) для зоны на `*.hosting.reg.ru` работоспособность `zone/add_txt` **не подтверждена** — надо сначала проверить одним тестовым `zone/add_txt`/`zone/get_resource_records` (Ф8.7).

2.3. **Сосуществование с текущим сертификатом.** Конфликта нет: сертификаты на одно имя от разных CA живут одновременно, «конфликт» возможен только на уровне того, какой файл указан в `ssl_certificate`. Переключение — это замена двух строк в `80to8897` + `reload`. GlobalSign-файлы в `/etc/keys/` при этом **не удалять**: пока LE не проверен в бою, они — точка отката.

2.4. **Что НЕ делать.** Не полагаться на автопродление reg.ru как на механизм доставки файла на хост (Ф3.4/Ф3.7 + вывод): даже если оно включено, оно доставит файл **в кабинет/на почту**, а не в `/etc/keys/`. Не использовать `--standalone` (займёт порт 80, потребует гасить nginx → даунтайм сайта, Ф8.5). Не отлаживать выпуск боевыми `--force-renewal` — упрётесь в лимит «5 сертификатов на точный набор имён за 7 дней», для отладки есть staging (Ф8.4).

### Шаг 3. Когда registry будет поднят

3.1. В `/etc/docker/certs.d/` на **admin-машине и на проде** ничего добавлять не нужно, **если** сертификат публичный (GlobalSign или LE) — оба CA есть в системных `cacerts`. Каталог `sm-karaoke.ru:<порт>/ca.crt` нужен только для приватного CA (Ф6.3). Никаких `insecure-registries`.

3.2. Добавить порт registry в суточную проверку из п. 1.2 отдельной строкой — у registry свой `notAfter`-путь (nginx на другом порту может отдавать другой сертификат, если кто-то ошибётся с `ssl_certificate` в новом `server`-блоке).

3.3. Обновить `specs/165-migration-one-server-research/tls-certificate.md` (или завести issue в трекере проекта) отметкой, что риск № 6 закрыт: мониторинг есть, владелец продления определён.

---

## Открытые вопросы

1. **Кто владелец услуги SSL и включено ли Автопродление** — единственный вопрос, от которого зависит, «само продлится или нет». Ответ лежит в личном кабинете reg.ru; из сессии недоступен. Без него все даты в «Выводе» — сценарные.
2. **Контактный e-mail сертификата** — кто получает письма о продлении и читает ли их кто-нибудь. Проверяется там же (вкладка «Сведения о сертификате»). Для платного сертификата адрес **не меняется после активации** — если он «мёртвый», это надо решать с поддержкой reg.ru (Ф4.1).
3. **Кем именно заказан сертификат.** Привязка к reg.ru построена на совпадении профиля SAN (DomainSSL) и на хостинговых NS. Прямого подтверждения (документа с именем `sm-karaoke.ru`) **не найдено**. Альтернатива — сертификат куплен напрямую у GlobalSign или у другого реселлера; тогда вся ветка «автопродление reg.ru» неверна. Минимальная проверка: сверить `SHA-256 fingerprint` в личном кабинете reg.ru с `2D:D3:5A:…:D5:1C` из `specs/165-.../tls-certificate.md`.
4. **Куда именно reg.ru поставит новый сертификат.** Подтверждено документально только «на хостинг Рег.ру» и «к услугам Заказчика». Для нашего host-nginx на VPS — **предположение**, что не поставит. Проверяется экспериментом: включить автопродление при живом текущем сертификате и посмотреть, появится ли что-то в `/etc/keys/` — но это уже после включения мониторинга.
5. **Работоспособность `api.reg.ru/api/regru2/zone/add_txt` для зоны на `*.hosting.reg.ru`.** API живой (измерено), плагин acme.sh существует, но что зона обслуживается именно тем API, который умеет хостинговые NS, — **не найдено**. Один тестовый `POST` с реальными кредами закрывает вопрос.
6. **Точная дата срабатывания автопродления** — 9 дней (справка) или 15 дней (юридические правила). Расхождение внутри первоисточников reg.ru. Для планирования принято консервативное «15».
7. **Точный текст ошибки nginx 1.28.3** при отвергнутом просроченном handshake. Подтверждены имя алерта (`certificate_expired(45)`, RFC 8446) и строка причины OpenSSL (`tls alert certificate expired`), но не конкретная строка `error_log` этой версии — **«не найдено»**. Снимается одним экспериментом на тестовом стенде с заведомо истёкшим сертификатом.
8. **Как будет покрыт год после перехода на модель «199×2».** Справка reg.ru пишет «в сумме ≈398 дней», но второй сертификат выпускается **за 30 дней до** окончания первого и живёт 199 дней **от своей выдачи** — арифметика даёт меньше 398. На наш runbook это влияет (сколько раз в год менять файл), точной цифры нет: **«не найдено»**.
9. **Нужно ли обновлять `deploy/karaoke-app/files/www.sm-karaoke.ru.crt`** (файл закоммичен в git и вшивается в образ). При любом перевыпуске — и по варианту GlobalSign, и по варианту LE — его надо синхронизировать, иначе образ соберётся со старым сертификатом (риск № 5 спеки 165).
10. **Не забыть про почту.** MX домена указывает на `mail.sm-karaoke.ru` = прод-хост (Ф1.3). Тот же лист обслуживает IMAP/SMTP. Если решение будет «а давайте просто отключим проверку сертификата у docker», почтовые клиенты всё равно сломаются о `notAfter` — вопрос «что делать с почтой» выходит за рамки задачи #274 и требует отдельного ответа владельца.
