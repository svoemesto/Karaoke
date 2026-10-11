# Contract: guard тегов контейнерных образов (R-04 / R-05)

**Спека**: [spec.md](../spec.md) (FR-014, FR-017, SC-009) |
**План**: [plan.md](../plan.md) (D10) | **Именование**: [image-naming.md](./image-naming.md) § 4

**Файл под контрактом**: `tools/check-docker-image-tags.sh`.
**Правила-источники**: R-04 (`nginx:stable`, не `nginx:alpine`) и R-05 (`node:22-alpine`,
не `node:latest`) — `knowledge/guidelines/architecture-conventions.md:21,39`.
**Вызов из CI**: `.github/workflows/lint.yml:337` — `bash tools/check-docker-image-tags.sh --quiet`.

---

## 1. Проблема, которую закрывает контракт

Сегодня guard ищет строки ровно по шаблону `^FROM[[:space:]]+(nginx|node):`
(`check-docker-image-tags.sh:128`). После FR-017 строка станет
`FROM sm-karaoke.ru:5443/library/node:22-alpine AS build-stage` — шаблон не совпадёт.

Наблюдаемое следствие (проверено запуском на текущем репозитории): guard видит
**4 строки в 5 Dockerfile** и печатает `OK: all 4 FROM nginx:/node: line(s) are in
whitelist.` После смены имён он увидит **0** строк и напечатает
`No 'FROM nginx:' or 'FROM node:' found in tracked Dockerfile* (R-04/R-05 N/A).`,
вернув **0**. То есть правило R-04/R-05 перестанет работать, оставаясь зелёным:
регресс защиты, а не косметика (FR-014, SC-009).

---

## 2. Вход

| Что | Как определяется |
|---|---|
| Область проверки | `git ls-files` → `(^|/)Dockerfile[^/]*$`, исключая `DockerfileBackup`, `DockerfileOld`, `DockerfileFull`, `DockerfileDemucs` (как сейчас, `check-docker-image-tags.sh:99-102`) |
| Строки | `grep -Hn '^FROM '` по каждому файлу |
| Релевантность | нормализованное имя начинается с `nginx` или `node` |

Файлы, не отслеживаемые git, вне области (CI работает по индексу).

---

## 3. Нормализация (новое)

```
parse(FROM-строка):
  1. отрезать "FROM "
  2. отрезать " AS <stage>" (если есть)
  3. взять первое слово
  4. отрезать "@sha256:<digest>" (если есть)
  5. если перед первым "/" есть "." или ":" → это registry-хост: отрезать его
  6. результат := <последний компонент пути> ":" <тег>
```

| Шаг | Пример |
|---|---|
| 0 | `FROM sm-karaoke.ru:5443/mirror/library/nginx:1.27-alpine@sha256:abc… AS prod` |
| 1-2 | `sm-karaoke.ru:5443/mirror/library/nginx:1.27-alpine@sha256:abc…` |
| 3-4 | `sm-karaoke.ru:5443/mirror/library/nginx:1.27-alpine` |
| 5 | `mirror/library/nginx:1.27-alpine` |
| 6 | `nginx:1.27-alpine` → **OK** |

**Правило сохранения чувствительности.** Шаги 5-6 не должны «спасать» нарушителя:
`…/library/nginx:alpine` → `nginx:alpine` → нарушение; `…/library/node:latest` →
`node:latest` → нарушение. Смена registry-хоста и порта не влияет на вердикт.

---

## 4. Whitelist

| Образ | Разрешённые теги |
|---|---|
| `nginx` | `stable`, `stable-alpine`, `1.27-alpine` |
| `node` | `22-alpine`, `20-alpine`, `20-bookworm-slim` |

Whitelist не расширяется в рамках этой фичи: FR-017 требует, чтобы правила
**продолжали** работать, а не чтобы они стали мягче. Добавление нового тега — отдельное
изменение `knowledge/guidelines/architecture-conventions.md` + этого файла.

---

## 5. Режимы и коды возврата

| Режим | Поведение | Коды |
|---|---|---|
| без аргументов | pretty-print каждой найденной строки (`✅`/`❌`) + итог | 0 — все в whitelist; 1 — есть нарушения; 2 — usage |
| `--quiet` | только код возврата (для pre-commit и CI) | те же |
| `--selftest` (**новый**) | прогоняет матчер на встроенном наборе строк, ничего не читая с диска | 0 — матчер исправен; 1 — матчер сломан; 2 — usage |

### 5.1 Набор `--selftest`

| Вход | Ожидание |
|---|---|
| `FROM nginx:stable AS production-stage` | допущено |
| `FROM node:22-alpine` | допущено |
| `FROM nginx:alpine` | нарушение |
| `FROM node:latest` | нарушение |
| `FROM sm-karaoke.ru:5443/library/nginx:alpine` | нарушение |
| `FROM sm-karaoke.ru:5443/library/node:latest` | нарушение |
| `FROM sm-karaoke.ru:5443/library/nginx:stable` | допущено |
| `FROM sm-karaoke.ru:5443/mirror/library/node:20-bookworm-slim AS build` | допущено |
| `FROM mirror/library/node:22-alpine@sha256:deadbeef AS build` | допущено |

Смысл: SC-009 («guard ловит подставной запрещённый тег **и после** смены имён»)
перестаёт быть разовым ручным упражнением и становится постоянной проверкой —
если кто-то снова сузит шаблон, `--selftest` упадёт раньше, чем guard ослепнет.

### 5.2 Что делать при «0 найденных строк»

Печатать предупреждение (не «N/A»): если среди `FROM` есть строка, чей
**нормализованный** последний компонент равен `nginx` или `node`, а матчер её не
увидел — это `FAIL`, а не «правило неприменимо». Условие «нет таких строк вообще»
остаётся законным случаем (exit 0) и встречается, только если в репозитории нет
Dockerfile'ов с nginx/node.

---

## 6. Критерии приёмки (SC-009)

1. `bash tools/check-docker-image-tags.sh` — зелёный на новых именах, число
   проверенных строк **≥ 4** (не 0).
2. `bash tools/check-docker-image-tags.sh --selftest` — зелёный.
3. **Намеренная поломка** (обязательна, SC-009): временно заменить в
   `deploy/karaoke-public/Dockerfile` строку на
   `FROM sm-karaoke.ru:5443/library/nginx:alpine AS production-stage` →
   `bash tools/check-docker-image-tags.sh` возвращает **1** и печатает файл:строку;
   вернуть строку обратно (`git checkout -- deploy/karaoke-public/Dockerfile`).
4. `bash tools/check-docker-image-tags.sh --quiet` на чистом дереве — 0
   (CI-совместимость).
5. Файл остаётся без зависимостей от внешних утилит (`bash`, `grep`, `git`), запуск —
   `bash tools/check-docker-image-tags.sh`, без `chmod +x` (как сейчас в CI).
