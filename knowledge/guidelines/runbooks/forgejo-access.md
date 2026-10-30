# Доступ к Forgejo для агентов (nsa-i9)

> Единое место, откуда **любой** агент на этой машине берёт креды Forgejo.
> Не зависит от конкретного checkout'а, worktree или каталога `.scratch`.

## Где лежит

| Что | Путь | Права |
|---|---|---|
| Токен + параметры | `/home/nsa/.config/karaoke/forgejo.env` | `600`, владелец `nsa` |
| Приватный SSH-ключ | `/home/nsa/.config/karaoke/id_ed25519` | `600` |
| SSH-конфиг (порт 2222) | `/home/nsa/.ssh/config`, блок `Host git.sm-karaoke.ru` | `600` |
| Git credential helper | `~/.gitconfig`: `credential.https://git.sm-karaoke.ru.helper` | — |

Содержимое `forgejo.env`:

```
FJ_URL=https://git.sm-karaoke.ru
FJ_USER=owner
FJ_TOKEN=<токен>
FJ_SSH_KEY=/home/nsa/.config/karaoke/id_ed25519
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

## Перезапуск CI-прогона

Отдельного API «перезапустить только упавшие джобы» в Forgejo 15 нет: в
`/swagger.v1.json` из rerun/dispatch-эндпоинтов есть ровно один, а кнопка
«Re-run failed jobs» в веб-интерфейсе — веб-маршрут без REST-эндпоинта.

Рабочий путь — диспетчеризация workflow: в `.forgejo/workflows/lint.yml`
объявлен триггер `workflow_dispatch`, поэтому прогон запускается без пуша:

```bash
. /home/nsa/.config/karaoke/forgejo.env
curl -s -o /dev/null -w "HTTP %{http_code}\n" -X POST \
  -H "Authorization: token $FJ_TOKEN" -H "Content-Type: application/json" \
  -d '{"ref":"master"}' \
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

## Почему так

Раньше креды лежали только в `.scratch/karaoke/infra-dub2000/.adm` этого
checkout'а. Агент в другом worktree (`../Karaoke-NNN-slug`) или в свежем клоне
видел «нет учётных данных» — файла просто не было рядом. Общий путь в
`~/.config/karaoke/` решает это для всех агентов и сессий на `nsa-i9`.
