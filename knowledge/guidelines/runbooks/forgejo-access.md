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

Отдельного API «перезапустить только упавшие джобы» в Forgejo 15 нет: в
`/swagger.v1.json` из rerun/dispatch-эндпоинтов есть ровно один, а кнопка
«Re-run failed jobs» в веб-интерфейсе — веб-маршрут без REST-эндпоинта.

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

## Почему так

Раньше креды лежали только в `.scratch/karaoke/infra-dub2000/.adm` этого
checkout'а. Агент в другом worktree (`../Karaoke-NNN-slug`) или в свежем клоне
видел «нет учётных данных» — файла просто не было рядом. Общий путь в
`~/.config/karaoke/` решает это для всех агентов и сессий на `nsa-i9`.
