# Quickstart: проверка приватного registry

**Спека**: [spec.md](./spec.md) | **План**: [plan.md](./plan.md) | **Дата**: 2026-10-11

Набор **запускаемых** проверок: каждая отвечает на конкретное требование спеки и
имеет наблюдаемый результат. Полные реализации скриптов здесь не приводятся — это
руководство по приёмке, а не код; детали — в `contracts/`.

Обозначения: **A** — выполняется на admin-машине (`nsa-i9`), **P** — на прод-хосте
`sm-karaoke.ru` (только по слову владельца), **\*** — проверка, которая **меняет**
состояние (её надо вернуть назад).

Перед любой работой с docker на admin:

```bash
export DOCKER_CONFIG=/home/nsa/Karaoke/.docker      # Pass 373
```

---

## Q1. Конфигурация registry валидна; retention проверен на стенде

**Покрывает**: FR-006, FR-010, I-4. **Где**: A (стенд).

```bash
# 1) Синтаксис конфига — той же версией Zot, что поедет на прод.
docker run --rm -v "$PWD/deploy/prod-single-host/registry:/c:ro" \
  ghcr.io/project-zot/zot:v2.1.22 verify /c/config.json

# 2) Что именно удалит retention. ВАЖНО: запускать НЕ на живом registry.
#    Команда обращается к метаданным хранилища, и документация Zot требует
#    остановить сервер: «the zot server must be stopped before running this command».
#    Поэтому — на стенде, контейнер остановлен, в КОПИИ конфига dryRun = true,
#    каталог данных примонтирован.
docker stop zot-rehearsal
docker run --rm \
  -v "$PWD/deploy/prod-single-host/registry-dryrun.json:/c/config.json:ro" \
  -v /tmp/registry-rehearsal/data:/var/lib/registry \
  ghcr.io/project-zot/zot:v2.1.22 verify-feature retention /c/config.json
```

**Ожидание**: `verify` — конфиг принят; `verify-feature retention` (стенд, dryRun) —
в списке на удаление **нет** тега, на который ссылается прод-compose (тег `1`), и нет
ни одного тега из `mirror/**`. Правило «ИЛИ» внутри политики
(`pkg/retention/retention.go:219`) и отключённый tag-retention для `mirror/**`
(`pkg/storage/gc/gc.go:786`) означают, что оба условия обязаны выполняться.

**Следствие для эксплуатации (записать в `RUNBOOK.md`)**: на **живом** проде
`verify-feature retention` запускать нельзя — требуется остановка Zot, то есть
недоступность registry. Проверка политики делается на стенде, а на проде — только
наблюдением за логами GC.

---

## Q2. Полный цикл push/pull **через nginx-прокси** (стенд)

**Покрывает**: FR-005, FR-006, FR-009, SC-002, SC-001 (частично). **Где**: A (стенд).

Стенд поднимается в одноразовом каталоге (`/tmp/registry-rehearsal`), тем же
`config.json`, с тем же серверным блоком nginx на локальном порту — прод не
затрагивается.

```bash
# 1) push большого образа (≥ 5 ГБ) при ВКЛЮЧЁННОМ sync — это проверка ловушки #275.
docker tag <локальный-большой-образ> localhost:<стенд>/karaoke/huge:1
docker push localhost:<стенд>/karaoke/huge:1

# 2) pull того же образа обратно
docker pull localhost:<стенд>/karaoke/huge:1

# 3) pull-through библиотечного образа (allow-list работает)
docker pull localhost:<стенд>/library/postgres:16
```

**Ожидание**:
1. push завершается успехом (не `503 Service Unavailable` из ловушки `onDemand` без
   `content`); в логе Zot нет `failed to get remote manifest … karaoke/huge`;
2. ответ на `POST /v2/…/blobs/uploads/` содержит **относительный** `Location`
   (начинается с `/v2/`), а не `http://127.0.0.1:5000/…` — прямое следствие
   `getBlobUploadSessionLocation` (`pkg/api/routes.go:3199`); проверяется
   `curl -v -X POST …/blobs/uploads/` с кредами;
3. pull-through кладёт образ ровно как `library/postgres` (upstream-путь сохранён);
4. SC-002: повтор push-а 5.4 ГБ — не менее 9 успехов из 10 попыток (первые 10 прогнать
   на стенде, где это дёшево; на проде — по факту деплоев).

---

## Q3. Авторизация, изоляция и отзыв

**Покрывает**: FR-002, FR-003, FR-004, SC-004, SC-006, I-5. **Где**: A (стенд), затем
P (приёмка прода, read-only).

```bash
bash tools/registry-smoke.sh <host:port>          # после реализации
# внутри: матрица из contracts/registry-transport.md § 5
```

**Ожидание**:

| Проверка | Результат |
|---|---|
| `/v2/`, `/v2/_catalog`, `/v2/<name>/manifests/<tag>` без кредов | 401 на каждом |
| `/v2/` с неверным паролем | 401 |
| `/v2/` с верным паролем | 200 |
| клиент проекта A тянет репозиторий проекта B | отказ |
| отзыв: удалить строку в `htpasswd`, повторить запрос с теми же кредами | 401 **без перезапуска контейнера** (SC-004) |

Отдельно — **внешняя** анонимная попытка (SC-006 требует проверки снаружи, а не
рассуждением): `curl -s -o /dev/null -w '%{http_code}' https://sm-karaoke.ru:5443/v2/`
с машины, не находящейся в доверенной сети.

---

## Q4. Guard тегов: смена имён не ослепила защиту

**Покрывает**: FR-014, FR-017, SC-009. **Где**: A.

```bash
# 1) на новых именах: строк должно быть найдено не ноль
bash tools/check-docker-image-tags.sh

# 2) постоянная самопроверка матчера
bash tools/check-docker-image-tags.sh --selftest

# 3) * намеренная поломка (SC-009) — обязательна
sed -i 's|^FROM .*nginx:stable.*|FROM sm-karaoke.ru:5443/library/nginx:alpine AS production-stage|' \
  deploy/karaoke-public/Dockerfile
bash tools/check-docker-image-tags.sh; echo "exit=$?"     # ожидание: exit=1, напечатан файл:строка
git checkout -- deploy/karaoke-public/Dockerfile
```

**Ожидание**: (1) `Checked N …` где `N ≥ 4` (сейчас guard видит ровно 4 — при смене
имён он бы увидел 0 и остался зелёным); (2) зелёный; (3) `exit=1` с указанием
нарушителя.

---

## Q5. Ни одного внешнего реестра в пути прода

**Покрывает**: FR-001, FR-007, FR-017, SC-001, SC-008, I-1. **Где**: A + P.

```bash
# A: статические утверждения по репозиторию
grep -rn '^FROM ' $(git ls-files | grep -E '(^|/)Dockerfile[^/]*$')
grep -rn 'image:' deploy/*.yml deploy/prod-single-host/*.yml
```

**Ожидание**: каждый `FROM` боевого Dockerfile начинается с
`sm-karaoke.ru:5443/`; каждое `image:` в путях прода и сборки — либо
`${DOCKER_REGISTRY}/karaoke/…`, либо `sm-karaoke.ru:5443/mirror/…`. Ни одного
`postgres:16`, `nginx:stable`, `quay.io/…`, `ghcr.io/…` без нашего префикса.

```bash
# P (по слову владельца): контейнер прод-образа действительно из нашего registry
docker inspect --format '{{.Config.Image}}' karaoke-web karaoke-public karaoke-db karaoke-storage
# логи Zot: за окно наблюдения нет обращений к docker.io за образами проекта
docker logs karaoke-registry 2>&1 | grep -c 'registry-1.docker.io/.*karaoke/'
```

**Ожидание**: все четыре образа адресуются нашим registry; счётчик обращений к
docker.io за `karaoke/*` равен 0 (SC-008 после закрытия окна).

---

## Q6. Версии сторонних образов зафиксированы

**Покрывает**: FR-008, FR-018, I-3. **Где**: A.

```bash
bash tools/registry-vendor.sh --verify
```

**Ожидание**: для каждой записи `deploy/prod-single-host/registry/images.lock`
дайджест в нашем registry совпадает с записанным; имя содержит `mirror/`; каждое
`image:` с `mirror/` в compose содержит `@sha256:`; ни один образ под
`latest`/`cuda`/`16` не остался без дайджеста.

---

## Q7. Retention действительно удаляет старое и не трогает нужное

**Покрывает**: FR-010, I-4. **Где**: A (стенд; контейнер остановлен — см. Q1).

1. Запушить подряд 12 тегов одного репозитория `karaoke/<test>` → после срабатывания
   retention (или принудительного GC-цикла на стенде) остаются 10 последних.
2. Убедиться, что тег действующего прод-образа (`karaoke-web:1`) на месте.
3. Убедиться, что теги в `mirror/**` не удалены.
4. `du -sh <каталог данных>` — размер не растёт монотонно при повторных пуш-ах.

---

## Q8. Восстановление registry (репетиция, не документ)

**Покрывает**: FR-011, SC-005. **Где**: A (стенд).

1. Удалить каталог данных стенда целиком.
2. Поднять контейнер заново из `docker-compose-registry.yml` + `config.json` +
   `htpasswd` — по шагам `registry/RUNBOOK.md`, ничего не додумывая.
3. Восстановить свои образы: `bash deploy/do.sh push_*`.
4. Восстановить сторонние: `bash tools/registry-vendor.sh` (по `images.lock`).
5. `bash tools/registry-smoke.sh <стенд>` → PASS.

**Ожидание**: процедура выполняется без обращения к прод-хосту; суммарное время
укладывается в целевое «не более одного рабочего дня» (SC-005) с запасом — на стенде
основное время уходит на перезаливку измеренных шести сторонних образов (≈11.8 ГБ);
объём `minio` и `nvidia/cuda` добавляется к этому и не измерен.

---

## Q9. Напоминание о сроке сертификата

**Покрывает**: FR-016, SC-007. **Где**: A.

```bash
bash tools/check-cert-expiry.sh sm-karaoke.ru:443 sm-karaoke.ru:5443          # штатный порог 45 дней
bash tools/check-cert-expiry.sh sm-karaoke.ru:443 sm-karaoke.ru:5443 --warn-days 400  # * намеренная проверка
```

**Ожидание**: первая команда — exit 0 и строка с `notAfter` (сейчас
`2027-04-03T10:34:27Z`, запас ~174 дня); вторая — exit 1 **и** видимое/слышимое
предупреждение (через `announce`), то есть канал напоминания проверен, а не
предполагается. Порт `:5443` проверяется отдельной строкой: ошибка в
`ssl_certificate` нового server-блока не видна на 443.

---

## Q10. Внедрение не остановило прод

**Покрывает**: FR-012, SC-003. **Где**: P (по слову владельца).

1. До развёртывания: `docker ps --format '{{.Names}} {{.Status}}'`.
2. Развернуть registry (`up -d` **нового** контейнера) и подключить серверный блок
   (`nginx -t && systemctl reload nginx`).
3. Сразу после: тот же `docker ps` — четыре существующих контейнера имеют тот же
   `Status`/`Up …` без перезапуска; сайт отвечает (`curl -sI https://sm-karaoke.ru/`).
4. **Память registry — сравнивать `anon`, а не цифру из `docker stats`.**

   ```bash
   PID=$(docker inspect --format '{{.State.Pid}}' karaoke-registry)
   CG=$(cat /proc/$PID/cgroup | grep -o '0::.*' | cut -d: -f3)
   awk '/^anon /{printf "anon: %.1f МиБ\n", $2/1048576}' /sys/fs/cgroup$CG/memory.stat
   awk '/VmRSS/{printf "VmRSS: %.1f МиБ\n", $2/1024}' /proc/$PID/status
   ```

   **Порог (план): ≤ 150 МиБ в покое.** Сравниваем с ним **`anon`** — собственную
   память процесса.

   **Почему `docker stats` для этого не годится.** Он показывает `memory.current`
   cgroup, а туда попадает **page cache** от чтения и записи блобов. Этот кэш
   вытесняется ядром по требованию, то есть памятью в смысле «сколько нужно
   registry» он не является — но растёт пропорционально трафику и создаёт ложное
   впечатление, что порог нарушен. Замер на проде 2026-10-11 после серии публикаций:

   | Метрика | Значение | Что это |
   |---|---|---|
   | `anon` | **44 МиБ** | собственная память процесса — **это и сравниваем с порогом** |
   | `VmRSS` | 71 МиБ | резидентная память процесса (включая разделяемые страницы) |
   | `file` | 444 МиБ | page cache от блобов — вытесняется ядром по требованию |
   | `memory.current` (то, что видит `docker stats`) | 320–500 МиБ | `anon` + `file` + slab |

   Исторические замеры стенда (покой ≈ 50 МиБ, пик при push 5.4 ГБ ≈ 114 МиБ) сняты
   как раз `docker stats`, поэтому они включают кэш и являются оценкой **сверху**;
   на проде цифра больше просто потому, что через registry прошли куда большие
   публикации. Никакой утечки здесь нет.

---

## Q11. Знание и per-feature документ обновлены (SSoT)

**Покрывает**: FR-013, R-32/FR-009 (Constitution §VI). **Где**: A.

```bash
python3 tools/lint-knowledge.py
bash tools/check-knowledge-structure.sh
grep -n 'registry' knowledge/system/infra/deploy-overview.md knowledge/system/infra/do-sh.md
ls knowledge/system/infra/registry.md
bash tools/check-feature-doc.sh docs/features/private-docker-registry.md
```

**Ожидание**: линтер — «All documentation passes…», структура — `9/9 checks passed`;
registry упомянут и в `deploy-overview.md`, и в `do-sh.md` (сегодня — нигде, хотя
`do.sh` им пользуется); новый `registry.md` содержит раскладку, модель доступа,
retention, процедуру восстановления и зависимость от срока сертификата;
per-feature документ `docs/features/private-docker-registry.md` проходит
`check-feature-doc.sh` (6 обязательных секций, слаг = имя файла, статус `active`) и
упомянут в оглавлении `docs/features/README.md`.

---

## Q12. Окно dual-push: возврат к прежнему источнику всё ещё возможен

**Покрывает**: FR-015 (решение #280: окно — до двух успешных прод-деплоев, максимум
месяц). **Где**: A + P (по слову владельца).

```bash
# A: пока DUAL_PUSH=1, образ существует в обоих источниках
docker manifest inspect "${DOCKER_REGISTRY}/karaoke-web:${BUILD_VERSION}" >/dev/null \
  && echo "our registry: OK"
docker manifest inspect "${DOCKER_HUB_USER}/karaoke-web:${BUILD_VERSION}" >/dev/null \
  && echo "hub (архив): OK"
```

**Ожидание**: оба вызова успешны — значит откат возможен без пересборки: на проде
вернуть `DOCKER_REGISTRY` на прежнее значение Hub и выполнить `do.sh pull_web`.
После переключения `DUAL_PUSH=0` (состояние S3) проверка Hub-ветки **обязана**
перестать быть частью штатного деплоя: `grep -c 'docker push' <лог>` содержит только
наш registry (SC-008), но namespace `svoemestodev` в Hub остаётся архивом —
его не удаляем (решение #280).

Состояния и переходы окна — [data-model.md](./data-model.md) § E6.

---

## Сводка покрытия

| Сценарий | FR | SC |
|---|---|---|
| Q1 | FR-006, FR-010 | — |
| Q2 | FR-005, FR-006, FR-009 | SC-001 (частично), SC-002 |
| Q3 | FR-002, FR-003, FR-004 | SC-004, SC-006 |
| Q4 | FR-014, FR-017 | SC-009 |
| Q5 | FR-001, FR-007, FR-017 | SC-001, SC-008 |
| Q6 | FR-008, FR-018 | — |
| Q7 | FR-010 | — |
| Q8 | FR-011 | SC-005 |
| Q9 | FR-016 | SC-007 |
| Q10 | FR-012 | SC-003 |
| Q11 | FR-013, R-32/FR-009 | — |
| Q12 | FR-015 | — |
