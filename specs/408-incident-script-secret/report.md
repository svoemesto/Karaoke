# Отчёт: пароль БД не в argv и не печатается (OpenProject #408, аудит #336 BR-626)

## Дефект

`tools/analyze-prod-incident.sh` (карточка P-75-01 аудита #336) — тот же корень
`secret-in-argv`, что у BR-551/BR-552, закрытых в PR #671. Дефект не был заведён
в реестр отдельной записью (BR не присвоен) и жил в baseline guard'а.

**Было:**

```bash
PSQL_REMOTE="docker exec karaoke-db psql \"host=${PROD_HOST} port=${PROD_PORT} user=${PROD_USER} password=${DB_PASS} dbname=${PROD_DB} sslmode=disable\" -t -A"
...
PG_STATE=$(eval "$PSQL_REMOTE -c \"SELECT ... FROM pg_stat_activity;\"" 2>/dev/null)
...
echo "  eval \"$PSQL_REMOTE -c 'SELECT pid, state, ...'\""
```

Два следствия:
1. пароль попадал в **argv** процесса `psql`/`docker exec` → виден в
   `ps -eo args` любому пользователю машины;
2. та же строка **печаталась в stdout** в блоке «Дополнительные команды» → секрет
   попадал в вывод терминала и в текст отчёта об инциденте (то есть в скриншоты
   и в переписку).

Плюс путь к `deploy/.env` был зашит как `/home/nsa/Karaoke/deploy/.env` — на
машине `dev-pc` такого каталога нет (BR-581, уже закрыт в AGENTS.md 3.5.0, но
в этом скрипте остался).

## Что стало

- пароль уходит в процесс **по stdin**, `psql` читает его из `PGPASSWORD`
  (переменная окружения), в argv остаются только хост, порт, пользователь и БД;
- строка-подсказка в выводе больше не содержит пароля — вместо неё печатается
  безопасная форма команды;
- путь к `.env` — от корня репозитория (`REPO_ROOT`).

## До/после (проверка заглушкой docker)

```
$ PATH="$PWD/.scratch/deploy/stub:$PATH" DB_PASS=TEST_DB_SECRET bash -c '... psql_prod "SELECT 1;"'
STUB_ARGV: exec -i karaoke-db sh -c PGPASSWORD="$(cat)" psql "host=1.2.3.4 port=5433 user=u dbname=karaoke sslmode=disable" -t -A -c "$1" sh SELECT 1;
STUB_STDIN_PASSWORD: TEST_DB_SECRET
PG_STATE_OK
```

Заглушка `docker` проверяет каждый аргумент на наличие секрета и падает с кодом 9
при утечке — падения не было: в argv пароля нет, он пришёл по stdin.

## Guard

Запись `tools/analyze-prod-incident.sh` удалена из
`tools/check-secret-in-argv.baseline` — guard больше не считает это известным
нарушением:

```
$ bash tools/check-secret-in-argv.sh
✅ OK: все 5 совпадений — в baseline (legacy, вне пакета #382)
$ bash tools/check-secret-in-argv.sh --self-test   → exit 0
```

## Проверки

- `bash -n tools/analyze-prod-incident.sh` → OK;
- guard + self-test → PASS;
- pre-commit — все применимые хуки Passed.

## Что осталось за владельцем

- Запуск скрипта на боевом хосте не выполнялся (нужен реальный `deploy/.env` и
  прод-доступ); проверка выполнена заглушкой `docker`.
