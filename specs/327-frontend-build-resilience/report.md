# Отчёт по OpenProject #327 — устойчивость сборки фронтендов

> **Issue**: OpenProject #327 «Сборка фронтендов: устойчивость к таймаутам npm и registry»
> **Branch**: `551-frontend-build-resilience`
> **Дата**: 2026-10-22
> **Статус**: реализовано, ожидает ревью владельца

## Проблема

`bash do.sh build_webvue3` дважды упал не из-за кода:

1. `npm install` внутри Docker — `ETIMEDOUT` на `registry.npmjs.org`
   (сработал пятиминутный дефолтный таймаут npm).
2. Docker не получил манифест `sm-karaoke.ru:5443/library/nginx:stable` —
   registry отвечал с таймаутом.

Ручной обход в тот день: локальные `nginx:stable`/`node:22-alpine` были
протегированы как registry-образы. Это не должно быть штатным способом.

## Что изменено

### Dockerfile'ы (`webvue3`, `karaoke-public`)

- `ARG NODE_IMAGE` / `ARG NGINX_IMAGE` — базовые образы параметризованы,
  дефолты остались registry-образами `sm-karaoke.ru:5443/library/...`.
- `npm install` заменён на `npm ci --prefer-offline --no-audit --no-fund`
  с cache-mount `/root/.npm` и параметрами:
  `--fetch-retries=5`, `--fetch-retry-mintimeout=20000`,
  `--fetch-retry-maxtimeout=120000`, `--fetch-timeout=600000`.

### `deploy/do.sh`

- Новый helper `do_build_frontend_image`:
  - пробрасывает `NODE_IMAGE`/`NGINX_IMAGE` в `--build-arg`, если заданы;
  - делает до `FRONTEND_BUILD_ATTEMPTS` попыток (по умолчанию 3);
  - повторяет **только** при сетевой ошибке (`ETIMEDOUT`,
    `timeout awaiting response headers`, `failed to do request`,
    `Could not resolve host`, `npm error network` и т.п.);
  - при ошибке Dockerfile/кода сразу возвращает ошибку без повторов;
  - некорректное значение `FRONTEND_BUILD_ATTEMPTS` откатывается к 3.
- `do_build_webvue3` и `do_build_public` переведены на helper.

### Документация

- `DEVELOPMENT.md` — как переопределить базовые образы и сколько попыток.
- `knowledge/system/infra/do-sh.md` — helper, переменные, поведение ретраев,
  changelog.

## Проверки

| Проверка | Результат |
|---|---|
| `bash -n deploy/do.sh` | OK |
| `do.sh build_webvue3` (дефолтные registry-образы) | rc=0 |
| `do.sh build_public` (дефолтные registry-образы) | rc=0 |
| `NODE_IMAGE=node:22-alpine NGINX_IMAGE=nginx:stable do.sh build_webvue3` | rc=0 |
| То же для `build_public` | rc=0 |
| Несетевая ошибка (`NODE_IMAGE=does-not-exist:1`) | rc=1, ровно 1 попытка, «повтор не выполняется» |
| Симуляция сетевой ошибки (fake docker, `ETIMEDOUT`) | 2 попытки с backoff, rc=1 |
| Содержимое `dist` до/после | тот же `index-gs3hKWy8.js`, sha256 `6d906b7a…` |
| `tools/check-doc-references.py` | 0 проблем |
| `tools/check-knowledge-structure.sh` | 9/9 PASS |
| `tools/lint-knowledge.py` | 0 нарушений |

## Что не менялось

- Прикладной код и содержимое фронтенд-бандла.
- Запущенные контейнеры: тег `:1` пересобран, но содержимое идентично;
  перезапуск не требуется.
- Сборки `karaoke-app` / `karaoke-web`: у них нет npm-шага, их базовые
  образы не затрагивались.

## Ссылки

- [Dockerfile webvue3](../../deploy/karaoke-webvue3/Dockerfile)
- [Dockerfile karaoke-public](../../deploy/karaoke-public/Dockerfile)
- [deploy/do.sh](../../deploy/do.sh)
- [DEVELOPMENT.md](../../DEVELOPMENT.md)
- [knowledge/system/infra/do-sh.md](../../knowledge/system/infra/do-sh.md)
