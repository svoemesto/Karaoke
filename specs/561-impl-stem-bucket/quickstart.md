# Quickstart: 561 — приватный бакет `stemjobs` и закрытый канал отдачи стемов (BR-258)

Документ — руководство по проверке и по операционным шагам на проде. Код описан в
`tasks.md`, контракт — в `contracts/stemjob-private-files-contract.md`.

## Предпосылки

- Репозиторий: worktree `/home/nsa/Karaoke/.worktrees/impl361`, ветка `561-impl-stem-bucket`.
- Gradle с `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle` (Hard Gate R-372).
- Стенд не требует БД и MinIO: проверка идёт через unit-тесты (fake-клиенты хранилища).
- Прод-деплой выполняет владелец (запрещено агенту: Constitution, «Ограничения и доступы»).

## Проверка 1. Раскладка приватных объектов (регрессия «до/после»)

```bash
cd /home/nsa/Karaoke/.worktrees/impl361
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test --tests '*StemJobStorageLayout*'
```

Ожидаемо:

- «приватные объекты кладутся в бакет `stemjobs`, а не в публичный `karaoke`» — **зелёный**;
- «ключ объекта — `<jobId>/<stem>.<ext>`» — **зелёный**;
- «legacy-ключ в публичном бакете известен уборке» — **зелёный**.

Обратная проверка (до правки): первый тест падает — бакет был `karaoke`. Это и есть «до/после».

## Проверка 2. Инвариант закрытости при старте `karaoke-app`

```bash
cd /home/nsa/Karaoke/.worktrees/impl361
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test --tests '*StemJobStoragePolicy*'
```

Ожидаемо:

- «бакет `stemjobs` выставляется в private» — **зелёный**;
- «бакет `karaoke` остаётся public» — **зелёный**;
- «ошибка связи с хранилищем не роняет запуск» — **зелёный**.

## Проверка 3. Fail-safe секрета во внутреннем канале (`karaoke-web`)

```bash
cd /home/nsa/Karaoke/.worktrees/impl361
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:test --tests '*StemJob*'
```

Ожидаемо:

- «без секрета → 403, файл не сохранён» — **зелёный**;
- «неверный секрет → 403» — **зелёный**;
- «секрет не настроен (пусто) + пустой заголовок → 403» — **зелёный**;
- «с корректным секретом → файл сохранён в приватный каталог» — **зелёный**;
- «недопустимое имя файла → 400» — **зелёный**;
- «скачивание отдаёт файл владельцу» / «чужое задание → 404» — **зелёные**.

Обратная проверка (до правки): эндпоинтов `PUT .../files/...` и `POST .../purge` не
существует, а скачивание идёт анонимным чтением `/minio/karaoke/...` — тесты не компилируются/
падают.

## Проверка 4. Полный набор проверок проекта

```bash
cd /home/nsa/Karaoke/.worktrees/impl361
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:compileKotlin :karaoke-app:compileKotlin --parallel
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:test :karaoke-app:test
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew ktlintCheck
bash tools/check-subagent-isolation.sh
bash tools/check-knowledge-structure.sh
python3 tools/check-doc-references.py
bash tools/check-feature-doc.sh docs/features/*.md
```

Фронтенд не трогается — `npm run lint` в `webvue3`/`karaoke-public` не требуется.

## Проверка 5. Отсутствие секретов в изменениях

```bash
cd /home/nsa/Karaoke/.worktrees/impl361
git diff master...HEAD | grep -inE 'secret\s*=\s*["'"'"'][0-9a-zA-Z]{16,}' || echo "OK: значение секрета не попало в diff"
```

Ожидаемо: `OK: значение секрета не попало в diff` — в изменениях только имена параметров
(`stemjobs.internal-secret`, `stemJobsInternalSecret`).

---

# Что сделать на проде (за владельцем)

## 1. Секрет (обязательно)

Одно и то же значение в двух местах (в репозиторий не попадает!):

| Сторона | Где | Как задать |
|---|---|---|
| `karaoke-app` | `Karaoke.properties` (свойство `stemJobsInternalSecret`) | через UI/API свойств на admin-машине |
| `karaoke-web` | env `STEMJOBS_INTERNAL_SECRET` | `deploy/prod-single-host/do.env` → `docker-compose-web.yml` (переменная уже прокидывается) |

Проверка после деплоя: `docker exec karaoke-web env | grep STEMJOBS_INTERNAL_SECRET` — значение
есть и **непустое**. Пустое значение = канал закрыт (fail-safe): стемы не скачаются.

## 2. Каталог результатов (рекомендуется)

`STEMJOBS_RESULT_DIR` — каталог, куда `karaoke-web` складывает принятые приватные объекты.

- Значение по умолчанию: `${STEMJOBS_TEMP_DIR}/results` = `/tmp/stemjobs/results` — внутри
  контейнера, **не переживает перезапуск контейнера**.
- Рекомендация: указать путь на постоянном томе `karaoke-web`
  (`WEB_FOLDER_HOST` монтируется в контейнер), например
  `${WEB_FOLDER_HOST}/stemjobs-results`.

Переменная в `deploy/prod-single-host/docker-compose-web.yml` (рядом с `STEMJOBS_INTERNAL_SECRET`)
и в `application.yml` (`stemjobs.result-dir`, env `STEMJOBS_RESULT_DIR`).

## 3. Порядок развёртывания (важно)

1. **`karaoke-app` первым.** При старте он создаёт приватный бакет `stemjobs` и выставляет
   политики (`stemjobs` → private, `karaoke` → public). После этого старые объекты в
   публичном бакете остаются анонимно читаемыми ещё до 24 ч, а новые уже не появятся.
   Секрет на `karaoke-app` должен быть задан **до** старта.
2. **`karaoke-web` вторым.** Появляются эндпоинты `PUT /api/internal/stemjobs/{id}/files/...`
   и `POST /api/internal/stemjobs/{id}/purge`, а скачивание начинает читать приватный
   каталог результатов. Секрет на `karaoke-web` должен совпадать с секретом на `karaoke-app`
   **до** деплоя — иначе финализация задания будет падать с ошибкой передачи.
3. Обратный порядок (сначала `karaoke-web`) приведёт к тому, что `karaoke-app` начнёт
   передавать файлы в ещё не готовый канал → задания будут уходить в `ERROR`.

## 4. Проверка после развёртывания

| Сценарий | Ожидание |
|---|---|
| `GET https://sm-karaoke.ru/minio/stemjobs/<id>/vocals.mp3` (аноним) | **403** от MinIO (бакет приватный) — содержимого нет |
| `GET https://sm-karaoke.ru/minio/karaoke/<публичный файл>` (аноним) | **200** — публичная часть не сломалась (FR-002) |
| `GET https://sm-karaoke.ru/minio/karaoke/stemjobs/<id>/vocals.mp3` (legacy-объект) | 200, пока объект не вычищен уборкой (≤24 ч после деплоя) |
| Скачивание своего стема владельцем через ЛК | **200**, файл отдаётся, `Content-Disposition` прежний |
| Скачивание чужого задания | **404**, факт существования не утекает |
| В журнале `karaoke-app`: `infra.stemjobs.storage` | строка о применении политики бакетов |
| В журнале `karaoke-web`: `infra.stemjobs.security` | отказы (если были) — без содержимого объекта |

Наблюдение после деплоя: 7 суток (SC-001…SC-004).

## 5. Уборка legacy-объектов (автоматически)

`karaoke-app` раз в 5 минут удаляет объекты просроченных/удалённых заданий — **и** из
приватного бакета, **и** из публичного `karaoke` по старой раскладке
(`stemjobs/<jobId>/...`). Отдельно ничего делать не нужно; достаточно дождаться, пока
задание старше 24 часов попадёт в уборку.
