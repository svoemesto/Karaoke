# Доступ к Forgejo для агентов (nsa-i9)

> Единое место, откуда **любой** агент на этой машине берёт креды Forgejo.
> Не зависит от конкретного checkout'а, worktree или каталога `.scratch`.

## Где лежит

| Что | Путь | Права |
|---|---|---|
| Токен + параметры | `/home/nsa/.config/karaoke/forgejo.env` | `600`, владелец `nsa` |
| Токен аккаунта-согласователя | там же, ключ `FJ_REVIEWER_TOKEN` | `600`, владелец `nsa` |
| Приватный SSH-ключ | `/home/nsa/.config/karaoke/id_ed25519` | `600` |
| SSH-конфиг (порт 2222) | `/home/nsa/.ssh/config`, блок `Host git.sm-karaoke.ru` | `600` |
| Git credential helper | `~/.gitconfig`: `credential.https://git.sm-karaoke.ru.helper` | — |

Содержимое `forgejo.env`:

```
FJ_URL=https://git.sm-karaoke.ru
FJ_USER=owner
FJ_TOKEN=<токен>
FJ_SSH_KEY=/home/nsa/.config/karaoke/id_ed25519
FJ_REVIEWER_TOKEN=<токен аккаунта karaoke-reviewer>
```

## Как пользоваться

HTTPS (обычный путь, токен подставляется автоматически):

```bash
cd /home/nsa/Karaoke
git remote -v            # origin = https://git.sm-karaoke.ru/svoemesto/Karaoke.git
git fetch origin         # пароль не спрашивает: helper читает forgejo.env
git push origin <branch>
```

Если helper не сработал (другой пользователь/окружение):

```bash
. /home/nsa/.config/karaoke/forgejo.env
git -c credential.helper='!f(){ echo username=$FJ_USER; echo "password=$FJ_TOKEN"; }; f' push origin <branch>
```

SSH (альтернатива):

```bash
git clone ssh://git@git.sm-karaoke.ru:2222/svoemesto/Karaoke.git
ssh -T git@git.sm-karaoke.ru    # Hi there, owner!
```

API (curl):

```bash
. /home/nsa/.config/karaoke/forgejo.env
curl -s -H "Authorization: token $FJ_TOKEN" \
  "$FJ_URL/api/v1/repos/svoemesto/Karaoke/actions/runs?limit=5"
```

## Правила

1. **Не печатать токен** в чат, логи, коммиты; не копировать в репозиторий.
2. **Не полагаться на `.scratch/karaoke/infra-dub2000/.adm`** — это артефакт
   одного checkout'а; в worktree и клонах его нет.
3. Ротация: Forgejo → Settings → Applications → удалить старый токен, выпустить
   новый, обновить `forgejo.env`. SSH-ключ ротируется в Settings → SSH Keys
   (ключ зарегистрирован как `nsa-i9 agents (shared key)`).
4. Токен — владельческий (полные права). Для одноразовых задач лучше выпускать
   отдельный токен с меньшими правами.
5. `FJ_REVIEWER_TOKEN` — токен служебного аккаунта-согласователя. Только для
   одобрения PR; никогда не использовать его для push, merge или правки
   настроек. Отзыв — см. § «Одобрение PR».

## Перезапуск CI-прогона

Отдельного API «перезапустить только упавшие джобы» нет (проверено и на 15.0.10,
и на 17.0.1): в `/swagger.v1.json` из rerun/dispatch-эндпоинтов есть ровно один,
а кнопка «Re-run failed jobs» в веб-интерфейсе — веб-маршрут без REST-эндпоинта.

Рабочий путь — диспетчеризация workflow: в `.forgejo/workflows/lint.yml`
объявлен триггер `workflow_dispatch`, поэтому прогон запускается без пуша:

```bash
. /home/nsa/.config/karaoke/forgejo.env
# Токен — через stdin-конфиг curl, иначе он виден в `ps -eo args`.
printf 'header = "Authorization: token %s"\n' "$FJ_TOKEN" \
  | curl -sS --config - -o /dev/null -w "HTTP %{http_code}\n" -X POST \
      -H 'Content-Type: application/json' -d '{"ref":"master"}' \
      "$FJ_URL/api/v1/repos/svoemesto/Karaoke/actions/workflows/lint.yml/dispatches"
```

Ответ `204` — запрос принят; прогон виден в списке запусков. Перезапускается
**весь** workflow, а не только упавшие джобы.

Перед перезапуском стоит убедиться, что красное вызвано сбоем сети, а не
кодом: те же проверки локально на том же коммите
(`./gradlew :karaoke-app:test :karaoke-web:test`, `tools/check-doc-references.py`,
`tools/check-knowledge-structure.sh`). Джобы, которым нужен интернет, падают
при сетевых сбоях машины — подробности в
[ci-tools.md](../../system/infra/ci-tools.md) § «Известные ловушки».

## Одобрение PR

Forgejo не даёт автору одобрить свой PR: API отвечает `approve your own pull is
not allowed`. Это политика API, а не настройка защиты ветки — сам эндпоинт
ревью (`POST /repos/{owner}/{repo}/pulls/{index}/reviews`) работает, нужен
другой identity.

Для этого на инстансе заведён служебный аккаунт **`karaoke-reviewer`** (не
админ, без доступа к контейнерам и прод-хостам). Его токен лежит в
`forgejo.env` под ключом `FJ_REVIEWER_TOKEN`; учётка — collaborator репозитория
с правом **write** (см. ниже, почему именно write).

Полный цикл работы с PR без участия человека (вручную; рабочий путь —
`tools/merge-pr.sh`, см. ниже):

```bash
. /home/nsa/.config/karaoke/forgejo.env

# 1. Создать PR токеном owner — автором будет owner.
# 2. Дождаться CI (при красном из-за сети — перезапуск прогонов, см. выше).
# 3. Одобрить токеном согласователя. Токен нельзя передавать через -H:
#    он попадёт в `ps -eo args`. Заголовок уходит в stdin-конфиг curl.
printf 'header = "Authorization: token %s"\n' "$FJ_REVIEWER_TOKEN" \
  | curl -sS --config - -X POST -H 'Content-Type: application/json' \
      -d '{"event":"APPROVED","body":"Проверено: ..."}' \
      "$FJ_URL/api/v1/repos/svoemesto/Karaoke/pulls/<N>/reviews"

# 4. Смержить токеном owner — тем же приёмом.
printf 'header = "Authorization: token %s"\n' "$FJ_TOKEN" \
  | curl -sS --config - -X POST -H 'Content-Type: application/json' \
      -d '{"Do":"merge"}' \
      "$FJ_URL/api/v1/repos/svoemesto/Karaoke/pulls/<N>/merge"
```

Эндпоинт merge всегда отвечает `405` — успех проверяется по состоянию PR
(`state: closed`, `merged: true`).

**Тонкость: одобрение должно быть от пользователя с правом write.** С
read-доступом ревью создаётся и возвращает `state: APPROVED`, но **не
засчитывается**: merge падает с `not allowed to merge [reason: Does not have
enough approvals]`. Право выдаётся так:

```bash
curl -s -o /dev/null -w "HTTP %{http_code}\n" -X PUT \
  -H "Authorization: token $FJ_TOKEN" -H "Content-Type: application/json" \
  -d '{"permission":"write"}' \
  "$FJ_URL/api/v1/repos/svoemesto/Karaoke/collaborators/karaoke-reviewer"
```

Ревью, созданное **до** выдачи write, не засчитывается — после выдачи прав
одобрение надо создать заново.

**Защита ветки при этом не ослаблена**: `required_approvals = 1` остаётся
как было, одобрение по-прежнему обязательно. Статус-чеки в гейт не входят
(`enable_status_check = false`) — это осознанное решение: раннер живёт на
`nsa-i9`, и его остановка не должна блокировать работу.

Как отозвать доступ: удалить токен учётки (Forgejo → User Settings →
Applications) и убрать collaborator'а
(`DELETE /repos/svoemesto/Karaoke/collaborators/karaoke-reviewer`).

### Скрипт `tools/merge-pr.sh`

Ручная последовательность из четырёх шагов — источник ошибок (забыли дождаться
CI, забыли про одобрение). Поэтому в репозитории лежит скрипт, который делает
всё это за один вызов и **отказывается** мержить незелёное:

```bash
bash tools/merge-pr.sh <N>                    # одобрить и смержить
bash tools/merge-pr.sh <N> --dry-run          # только проверки
bash tools/merge-pr.sh <N> --delete-branch    # удалить remote-ветку после merge
```

Что внутри:

1. PR должен быть в состоянии `open`.
2. Все статус-чеки head-коммита — `success`. Ноль статус-чеков — тоже отказ:
   «CI не отработал» — не «CI зелёный». Ни одного `pending`.
3. Одобрение аккаунтом-согласователем (если его ещё нет).
4. Merge и проверка результата: эндпоинт merge всегда отвечает `405`, поэтому
   успех определяется по состоянию PR (`merged: true`).

Скрипт — **единственная** разрешённая точка входа в master (AGENTS.md v3.8.0,
Tier-1 «Hard Gate — Git — CI-gate для master»). Токены он читает из
`forgejo.env` и передаёт в curl через `--config -`, чтобы секрет не попадал в
`ps -eo args`.

## Логи джобов и прогонов (с 2026-11-01, Forgejo 17.0.1)

В версии 15.0.10 логи заданий через API были недоступны (`404`), и причину
красного джоба приходилось восстанавливать по косвенным признакам. В 17.0.1
появились эндпоинты:

| Эндпоинт | Ответ |
|---|---|
| `GET /repos/{owner}/{repo}/actions/jobs/{job_id}/logs` | `text/plain` — лог одного задания |
| `GET /repos/{owner}/{repo}/actions/runs/{run_id}/logs` | ZIP со всеми логами прогона (файлы `{джоб}-{id}-attempt-1.log`) |
| `GET /repos/{owner}/{repo}/actions/runs/{run_id}/jobs` | список джобов прогона с их id |

Токен — через stdin-конфиг curl (иначе он виден в `ps -eo args`):

```bash
. /home/nsa/.config/karaoke/forgejo.env

# список джобов прогона (run_id — из GET /actions/runs)
printf 'header = "Authorization: token %s"\n' "$FJ_TOKEN" \
  | curl -sS --config - \
      "$FJ_URL/api/v1/repos/svoemesto/Karaoke/actions/runs/<run_id>/jobs"

# лог одного джоба — text/plain
printf 'header = "Authorization: token %s"\n' "$FJ_TOKEN" \
  | curl -sS --config - \
      "$FJ_URL/api/v1/repos/svoemesto/Karaoke/actions/jobs/<job_id>/logs"

# все логи прогона — ZIP (распаковать и читать {джоб}-{id}-attempt-1.log)
printf 'header = "Authorization: token %s"\n' "$FJ_TOKEN" \
  | curl -sS --config - -o run-logs.zip \
      "$FJ_URL/api/v1/repos/svoemesto/Karaoke/actions/runs/<run_id>/logs"
```

### Как прочитать лог СТАРОГО прогона

Логи живут в хранилище Forgejo (`data/actions_log/<owner>/<repo>/<run-id>/<job-id>.log.zst`,
сжатие zstd) и переживают обновление платформы, но не доступны через API для
прогонов, чьи файлы уже вытеснены. Тогда — из архива бэкапа:

```bash
# архивы бэкапов на сервере: /home/sm/backup-out/forgejo-YYYY-MM-DD.tar.gz
ssh -p 7487 sm@185.71.198.17 \
  'tar -tzf /home/sm/backup-out/forgejo-YYYY-MM-DD.tar.gz | grep actions_log | head'
# вытащить нужный каталог прогона и распаковать логи (zstd)
```

Полный путь к архивам и что доступно агенту `sm` без root —
`knowledge/system/infra/external-infra.md`.

## Обновление Forgejo

Платформа обновляет владелец; агент помогает с проверками. Процедура и откат —
`knowledge/adr/local-0011-forgejo-git-hosting-and-ci.md`; здесь чек-лист.

**Перед обновлением**

1. Сверить текущую версию: `version` в ответе API (`17.0.1+gitea-1.22.0`).
2. **Бэкап** (обязателен: откат возможен только с `forgejo restore` из дампа) —
   дождаться таймера `infra-backup.timer` (03:30 UTC) или снять `forgejo dump`
   вручную; убедиться, что архив создан и не пуст.
3. Проверить, что образ прежней версии есть в кэше — без него откат невозможен.

**Обновление**

4. `forgejo manager flush-queues` — остановить обработку очередей.
5. Сменить тег образа в `/srv/forgejo/docker-compose.yml`.
6. `docker compose up -d` в каталоге стека.
7. `forgejo doctor check --all` — все проверки OK (на 17.0.1 — 27 проверок).

**Смоук после обновления**

8. Открыть `https://git.sm-karaoke.ru`: список PR и статус-чеки на месте.
9. Убедиться, что раннер подключился и выполняет задания (на 17.0.1 act_runner
   v13.0.0 подключается сразу).
10. Проверить, что API логов отдаёт `text/plain` (а не 404).

**Откат**

11. Вернуть прежний тег образа в compose и поднять стек заново.
12. **Обязательно** `forgejo restore` из дампа: версия 15 не стартует с базой,
    обновлённой до 17.

## Почему так

Раньше креды лежали только в `.scratch/karaoke/infra-dub2000/.adm` этого
checkout'а. Агент в другом worktree (`../Karaoke-NNN-slug`) или в свежем клоне
видел «нет учётных данных» — файла просто не было рядом. Общий путь в
`~/.config/karaoke/` решает это для всех агентов и сессий на `nsa-i9`.
